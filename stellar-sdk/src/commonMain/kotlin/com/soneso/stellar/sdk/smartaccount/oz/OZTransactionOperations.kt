//
//  OZTransactionOperations.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.crypto.getEd25519Crypto
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MemoNone
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.rpc.responses.SendTransactionStatus
import com.soneso.stellar.sdk.rpc.responses.SimulateTransactionResponse
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import com.soneso.stellar.sdk.scval.Scv
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay

/**
 * Result of a transaction submission and polling operation.
 *
 * Contains the outcome of a transaction after it has been submitted to the network
 * and potentially confirmed on-chain. Use this to determine if a transaction succeeded
 * and retrieve its hash and ledger number.
 *
 * Example:
 * ```kotlin
 * val result = txOps.transfer(
 *     tokenContract = "CBCD...",
 *     recipient = "GA7Q...",
 *     amount = 10.0
 * )
 *
 * if (result.success) {
 *     println("Transaction succeeded! Hash: ${result.hash ?: "unknown"}")
 *     println("Confirmed in ledger: ${result.ledger ?: 0}")
 * } else {
 *     println("Transaction failed: ${result.error ?: "unknown error"}")
 * }
 * ```
 *
 * @property success Whether the transaction was successful
 * @property hash The transaction hash if submission succeeded
 * @property ledger The ledger number where the transaction was confirmed
 * @property error Error message if the transaction failed
 */
data class TransactionResult(
    val success: Boolean,
    val hash: String? = null,
    val ledger: UInt? = null,
    val error: String? = null
)

/**
 * Callback for resolving context rule IDs per auth entry.
 *
 * Called during the signing flow for each authorization entry that matches the connected
 * smart account. The callback receives the entry and its index in the auth entries list,
 * and returns the context rule IDs to use for that entry's invocation tree.
 *
 * When not provided, the SDK resolves rule IDs automatically from the connected signer
 * and available context rules. Provide a callback when automatic resolution fails due
 * to ambiguity (selected signers match multiple rules) or to bypass auto-resolution.
 *
 * Example:
 * ```kotlin
 * // Simple case: same rule for all entries
 * val resolver: ResolveContextRuleIds = { _, _ -> listOf(ruleId) }
 *
 * // Advanced: inspect entry to decide
 * val resolver: ResolveContextRuleIds = { entry, index ->
 *     if (index == 0) listOf(rule1Id) else listOf(rule2Id)
 * }
 * ```
 */
typealias ResolveContextRuleIds = suspend (
    entry: SorobanAuthorizationEntryXdr,
    index: Int
) -> List<UInt>

/**
 * Transaction operations for OpenZeppelin Smart Accounts.
 *
 * Provides high-level transaction building, signing, and submission capabilities
 * for smart account operations. Handles:
 *
 * - Token transfers with automatic stroops conversion
 * - Transaction simulation and fee estimation
 * - Authorization entry signing with WebAuthn
 * - Relayer submission for fee sponsoring
 * - Transaction polling and confirmation
 * - Testnet wallet funding via Friendbot
 *
 * ## Fee Sponsoring
 *
 * When a relayer URL is configured via `config.relayerUrl`, transactions can be fee-sponsored by the relayer.
 * Two modes are used depending on authorization entry types:
 *
 * - **Mode 1 (Host Function + Auth)**: Used when no source_account auth exists.
 *   Sends host function and signed auth entries. Transaction is not signed by source account.
 *
 * - **Mode 2 (Signed Transaction XDR)**: Used when source_account auth exists.
 *   Sends fully signed transaction XDR. Required for operations that need source account signature.
 *
 * The mode is automatically selected based on the presence of source_account (Void) credentials
 * in the authorization entries.
 *
 * This class works in tandem with OZSmartAccountKit and should be accessed via
 * the kit instance rather than instantiated directly.
 *
 * Example usage:
 * ```kotlin
 * val kit = OZSmartAccountKit.create(config)
 * val txOps = OZTransactionOperations(kit)
 *
 * // Transfer tokens
 * val result = txOps.transfer(
 *     tokenContract = nativeTokenAddress,
 *     recipient = "GA7Q...",
 *     amount = "100"
 * )
 * println("Transfer ${if (result.success) "succeeded" else "failed"}")
 *
 * // Fund testnet wallet
 * val fundedAmount = txOps.fundWallet(nativeTokenContract = nativeTokenAddress)
 * println("Funded with $fundedAmount XLM")
 * ```
 *
 * @see submit for fee sponsoring mode selection logic
 * @see fundWallet for testnet funding with relayer support
 */
class OZTransactionOperations internal constructor(
    private val kit: OZSmartAccountKit
) {
    // MARK: - Token Transfer

    /**
     * Transfers tokens from the smart account to a recipient.
     *
     * Builds and submits a token transfer transaction from the connected smart account
     * to the specified recipient. The amount is automatically converted from XLM to stroops.
     *
     * Flow:
     * 1. Validates inputs (addresses, amount, not sending to self)
     * 2. Converts amount to stroops (1 XLM = 10,000,000 stroops)
     * 3. Builds contract invocation for token transfer
     * 4. Simulates transaction to get auth entries
     * 5. Signs auth entries with passkey (requires user interaction)
     * 6. Re-simulates with signed auth entries
     * 7. Submits via relayer (if configured) or RPC
     * 8. Polls for confirmation
     *
     * IMPORTANT: This method requires WebAuthn interaction to sign auth entries.
     * The user will be prompted for biometric authentication.
     *
     * @param tokenContract The token contract address (C-address)
     * @param recipient The recipient address (G-address for accounts, C-address for contracts)
     * @param amount The amount to transfer in XLM (will be converted to stroops)
     * @param forceMethod Optional override to force relayer or RPC submission (default: auto-detect)
     * @return TransactionResult indicating success or failure
     * @throws SmartAccountException if validation fails, simulation fails, or submission fails
     *
     * Example:
     * ```kotlin
     * val result = txOps.transfer(
     *     tokenContract = "CBCD1234...",
     *     recipient = "GA7QYNF7...",
     *     amount = "10.5"
     * )
     *
     * if (result.success) {
     *     println("Transferred 10.5 XLM. Hash: ${result.hash ?: ""}")
     * } else {
     *     println("Transfer failed: ${result.error ?: ""}")
     * }
     * ```
     */
    suspend fun transfer(
        tokenContract: String,
        recipient: String,
        amount: String,
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        // STEP 1: Validate inputs
        val (_, contractId) = kit.requireConnected()

        // Validate token contract address (must be C-address)
        requireContractAddress(tokenContract, "tokenContract")

        // Validate recipient address (G or C)
        requireStellarAddress(recipient, "recipient")

        // Prevent self-transfer
        if (recipient == contractId) {
            throw ValidationException.invalidInput(
                "recipient",
                "Cannot transfer to self"
            )
        }

        // STEP 2: Convert amount to stroops — validates non-empty and positive
        val stroops = Util.amountToStroops(amount)

        // STEP 3: Build host function for token transfer
        // Contract call: token.transfer(from: smartAccount, to: recipient, amount: stroops)
        val fromAddress = Address(contractId).toSCAddress()
        val toAddress = Address(recipient).toSCAddress()

        val amountScVal = Util.stroopsToI128ScVal(stroops)

        val functionArgs = listOf(
            Scv.toAddress(fromAddress),
            Scv.toAddress(toAddress),
            amountScVal
        )

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(tokenContract).toSCAddress(),
            functionName = SCSymbolXdr("transfer"),
            args = functionArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        // STEP 4-8: Submit the transaction (will handle simulation, signing, and polling)
        return submit(hostFunction = hostFunction, auth = emptyList(), forceMethod = forceMethod)
    }

    // MARK: - Execute Arbitrary Contract Function

    /**
     * Executes an arbitrary contract function call through the smart account's execute entry point.
     *
     * Builds an invocation of the smart account contract's `execute(target, target_fn, target_args)`
     * function, which calls the target contract on behalf of the smart account. The smart account's
     * authorization rules (context rules, signers, policies) apply to this call.
     *
     * This is the single-signer equivalent of arbitrary contract execution. For multi-signer
     * operations, use [OZMultiSignerManager].
     *
     * @param target The target contract address (C-address) to call
     * @param targetFn The function name to invoke on the target contract
     * @param targetArgs The arguments to pass to the target function as SCVal list
     * @param forceMethod Optional override to force relayer or RPC submission
     * @param resolveContextRuleIds Optional callback to resolve context rule IDs per auth entry.
     *   When null, rule IDs are resolved automatically from the connected signer and available
     *   context rules. Provide a callback when automatic resolution fails due to ambiguity or to
     *   bypass auto-resolution.
     * @return TransactionResult indicating success or failure
     * @throws SmartAccountException if submission fails
     */
    suspend fun executeAndSubmit(
        target: String,
        targetFn: String,
        targetArgs: List<SCValXdr> = emptyList(),
        forceMethod: SubmissionMethod? = null,
        resolveContextRuleIds: ResolveContextRuleIds? = null
    ): TransactionResult {
        val (_, contractId) = kit.requireConnected()

        // Validate target address (must be C-address)
        requireContractAddress(target, "target")

        // Build the execute invocation on the smart account contract
        val functionArgs = listOf(
            Scv.toAddress(Address(target).toSCAddress()),
            Scv.toSymbol(targetFn),
            Scv.toVec(targetArgs)
        )

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("execute"),
            args = functionArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        return submit(
            hostFunction = hostFunction,
            auth = emptyList(),
            forceMethod = forceMethod,
            resolveContextRuleIds = resolveContextRuleIds
        )
    }

    // MARK: - Transaction Submission

    /**
     * Submits a host function with full Soroban authorization flow.
     *
     * Performs the complete transaction lifecycle: simulation, auth entry extraction,
     * WebAuthn signing, re-simulation with signed auth, and submission. This method
     * handles the critical authorization step that allows state-changing operations
     * to succeed on-chain.
     *
     * Flow:
     * 1. Require connected wallet (credential ID + contract ID)
     * 2. Get deployer account from kit
     * 3. Build transaction with host function and provided auth (may be empty)
     * 4. Simulate transaction to discover required auth entries
     * 5. Extract auth entries from simulation result
     * 6. For each auth entry matching our contract:
     *    a. Build auth payload hash
     *    b. Require WebAuthn provider
     *    c. Decode credential ID bytes
     *    d. Resolve key data from storage or on-chain context rules
     *    e. Build ExternalSigner for context rule resolution
     *    f. Resolve context rule IDs via contextRuleManager
     *    g. Compute auth digest: SHA-256(payloadHash || contextRuleIds.toXDR())
     *    h. Authenticate with WebAuthn using auth digest as challenge
     *    i. Normalize signature to low-S compact format
     *    j. Build WebAuthn signature ScVal
     *    k. Attach signature and context rule IDs to the auth entry
     * 7. Update transaction with signed auth entries
     * 8. Re-simulate to get correct resource fees
     * 9. Assemble transaction from re-simulation
     * 10. Conditionally sign envelope with deployer keypair
     * 11. Determine submission mode (relayer vs RPC)
     * 12. Submit and poll for confirmation
     *
     * ## Fee Sponsoring and Deployer Signing
     *
     * The transaction is signed by the deployer keypair in these cases:
     * - When no relayer is configured (always sign for direct RPC submission)
     * - When using relayer Mode 2 (has source_account auth entries)
     *
     * The transaction is NOT signed when using relayer Mode 1 (no source_account auth).
     * This allows the relayer to wrap the host function with its own channel account
     * for fee sponsoring.
     *
     * ## Relayer Mode Selection
     *
     * - **Mode 1**: Used when auth entries contain only Address credentials.
     *   Submits host function + signed auth entries via `relayerClient.send()`.
     *
     * - **Mode 2**: Used when any auth entry has source_account (Void) credentials.
     *   Submits fully signed transaction XDR via `relayerClient.sendXdr()`.
     *
     * IMPORTANT: This method requires WebAuthn interaction to sign auth entries.
     * The user will be prompted for biometric authentication for each auth entry
     * that matches the connected smart account contract.
     *
     * @param hostFunction The host function to execute
     * @param auth Authorization entries for the transaction (typically empty; simulation provides them)
     * @param forceMethod Optional override to force relayer or RPC submission (default: auto-detect)
     * @param resolveContextRuleIds Optional callback to resolve context rule IDs per auth entry.
     *   When null (default), rule IDs are resolved automatically from the connected signer and
     *   available context rules. Provide a callback when automatic resolution is ambiguous or to
     *   bypass auto-resolution entirely.
     * @return TransactionResult indicating success or failure
     * @throws SmartAccountException if submission, simulation, signing, or polling fails
     *
     * @see shouldUseRelayerMode2 for mode selection logic
     */
    suspend fun submit(
        hostFunction: HostFunctionXdr,
        auth: List<SorobanAuthorizationEntryXdr>,
        forceMethod: SubmissionMethod? = null,
        resolveContextRuleIds: ResolveContextRuleIds? = null
    ): TransactionResult {
        // STEP 1: Require connected wallet
        val (credentialId, contractId) = kit.requireConnected()

        // STEP 2: Get deployer account
        val deployer = kit.getDeployer()

        val deployerAccount = kit.sorobanServer.getAccount(deployer.getAccountId())

        // STEP 3: Build transaction with host function and provided auth
        val operation = InvokeHostFunctionOperation(hostFunction, auth)

        val transaction = TransactionBuilder(deployerAccount, Network(kit.config.networkPassphrase))
            .setBaseFee(100)
            .addOperation(operation)
            .addMemo(MemoNone)
            .setTimeout(300)
            .build()

        // STEP 4: Simulate transaction
        val simulation = kit.sorobanServer.simulateTransaction(transaction)

        // STEP 5: Check for simulation errors
        if (simulation.error != null) {
            throw TransactionException.simulationFailed("Simulation error: ${simulation.error}")
        }

        // STEP 6: Extract auth entries from simulation
        val simulatedAuthEntries = simulation.results?.firstOrNull()?.parseAuth() ?: emptyList()

        // STEP 7-8: Sign auth entries matching our contract
        val signedAuthEntries = if (simulatedAuthEntries.isNotEmpty()) {
            // Get latest ledger ONCE before the signing loop
            val latestLedger = kit.sorobanServer.getLatestLedger()
            val expiration = latestLedger.sequence.toUInt() + kit.config.signatureExpirationLedgers.toUInt()

            // Pre-fetch context rules ONCE for all auth entries (avoids N+1 RPC calls per entry)
            val contextRules = kit.contextRuleManager.listContextRules()

            val signed = mutableListOf<SorobanAuthorizationEntryXdr>()

            for ((entryIndex, entry) in simulatedAuthEntries.withIndex()) {
                // Check if this entry has address credentials
                val addressCreds = (entry.credentials as? SorobanCredentialsXdr.Address)?.value
                if (addressCreds == null) {
                    // Not an address credential (e.g., sourceAccount), pass through unchanged
                    signed.add(entry)
                    continue
                }

                // Check if the address matches our contract
                val entryAddress = try { Address.fromSCAddress(addressCreds.address).toString() } catch (_: Exception) { null }
                if (entryAddress != contractId) {
                    // Not our contract's entry, pass through unchanged
                    signed.add(entry)
                    continue
                }

                // This entry matches our smart account contract -- sign it

                // (a) Build the auth payload hash for WebAuthn signing
                val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
                    entry = entry,
                    expirationLedger = expiration,
                    networkPassphrase = kit.config.networkPassphrase
                )

                // (b) Require WebAuthn provider
                val webauthnProvider = kit.config.webauthnProvider
                    ?: throw ValidationException.invalidInput(
                        "webauthnProvider",
                        "WebAuthn provider is required for signing auth entries but is not configured"
                    )

                // (c) Decode credential ID bytes for allowCredentials constraint
                val credIdBytes = try {
                    Util.base64urlDecode(credentialId)
                } catch (e: IllegalArgumentException) {
                    throw CredentialException.invalid(
                        "Failed to decode credentialId from Base64URL: $credentialId"
                    )
                }

                // (d) Resolve key data from storage or on-chain context rules.
                // Storage lookup is an optimization; fall through to on-chain lookup on any failure.
                val keyData: ByteArray
                val stored = try {
                    kit.getStorage().get(credentialId)
                } catch (_: Exception) {
                    null
                }
                if (stored != null) {
                    keyData = stored.publicKey + credIdBytes
                } else {
                    keyData = findKeyDataFromContextRules(credIdBytes)
                }

                // (e) Build the connected signer — required for context rule resolution
                val signer = ExternalSigner(
                    verifierAddress = kit.config.webauthnVerifierAddress,
                    keyData = keyData
                )

                // (f) Resolve context rule IDs for this auth entry (using pre-fetched rules)
                val resolvedContextRuleIds = if (resolveContextRuleIds != null) {
                    resolveContextRuleIds(entry, entryIndex)
                } else {
                    kit.contextRuleManager.resolveContextRuleIdsForEntry(
                        entry, listOf(signer), contextRules
                    )
                }

                // (g) Compute auth digest binding rule IDs to the payload hash
                val authDigest = SmartAccountAuth.buildAuthDigest(payloadHash, resolvedContextRuleIds)

                // (h) Authenticate with passkey using auth digest as challenge (triggers biometric prompt)
                val authResult = webauthnProvider.authenticate(
                    authDigest,
                    allowCredentialIds = listOf(credIdBytes)
                )

                // (i) Normalize DER signature to compact format with low-S
                val compactSig = SmartAccountUtils.normalizeSignature(authResult.signature)

                // (j) Build WebAuthn signature
                val webAuthnSig = WebAuthnSignature(
                    authenticatorData = authResult.authenticatorData,
                    clientData = authResult.clientDataJSON,
                    signature = compactSig
                )

                // (k) Attach the signature and context rule IDs to the auth entry
                val signedEntry = SmartAccountAuth.signAuthEntry(
                    entry = entry,
                    signer = signer,
                    signature = webAuthnSig,
                    expirationLedger = expiration,
                    contextRuleIds = resolvedContextRuleIds
                )

                signed.add(signedEntry)
            }

            signed
        } else {
            emptyList()
        }

        // Emit transaction signed event
        kit.events.emit(
            SmartAccountEvent.TransactionSigned(
                contractId = contractId,
                credentialId = if (signedAuthEntries.isNotEmpty()) credentialId else null
            )
        )

        // STEP 9: Rebuild transaction with signed auth entries.
        // Re-fetch deployer account to get the correct on-chain sequence number.
        // The initial fetch (step 2) was consumed by TransactionBuilder.build() in step 3.
        val refreshedDeployerAccount = kit.sorobanServer.getAccount(deployer.getAccountId())
        val signedOperation = InvokeHostFunctionOperation(hostFunction, signedAuthEntries)
        val signedTransaction = TransactionBuilder(refreshedDeployerAccount, Network(kit.config.networkPassphrase))
            .setBaseFee(100)
            .addOperation(signedOperation)
            .addMemo(MemoNone)
            .setTimeout(300)
            .build()

        // STEP 10: Re-simulate with signed auth entries to get correct resource fees
        val reSimulation = kit.sorobanServer.simulateTransaction(signedTransaction)

        if (reSimulation.error != null) {
            throw TransactionException.simulationFailed("Re-simulation error: ${reSimulation.error}")
        }

        // STEP 11: Assemble transaction from re-simulation.
        // prepareTransaction applies resource fees, footprint, and soroban data from simulation.
        val finalTransaction = kit.sorobanServer.prepareTransaction(signedTransaction, reSimulation)

        // STEP 12: Determine submission method and submit
        val submissionMethod = getSubmissionMethod(forceMethod)
        val useRelayer = submissionMethod == SubmissionMethod.RELAYER

        return submitOrRelay(
            transaction = finalTransaction,
            hostFunction = hostFunction,
            signedAuthEntries = signedAuthEntries,
            signer = deployer,
            useRelayer = useRelayer
        )
    }

    // MARK: - Multi-Signer Transaction Submission

    /**
     * Submits a multi-signer transaction using the same Mode 1 / Mode 2 routing
     * as single-signer [submit].
     *
     * Extracts the host function and signed auth entries from the assembled transaction
     * and submits them via the relayer's Mode 1 endpoint (relayer builds the envelope).
     * Mode 2 (signed XDR) is used only when source_account auth entries are present.
     *
     * Called by [OZMultiSignerManager.multiSignerTransfer] after collecting all
     * signatures and re-simulating.
     *
     * @param hostFunction The host function for the token transfer
     * @param signedAuthEntries Auth entries with all collected signatures
     * @param signedTransaction The transaction with signed auth entries (pre-assembly)
     * @param simulation The re-simulation response for transaction assembly
     * @param forceMethod Override the submission method. Null uses the default (relayer if configured, otherwise RPC).
     * @return TransactionResult with submission outcome
     * @throws SmartAccountException if submission fails
     */
    internal suspend fun submitMultiSignerTransaction(
        hostFunction: HostFunctionXdr,
        signedAuthEntries: List<SorobanAuthorizationEntryXdr>,
        signedTransaction: Transaction,
        simulation: SimulateTransactionResponse,
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        val deployer = kit.getDeployer()

        // Assemble the transaction using the SDK's prepareTransaction.
        // This correctly applies resource fees, footprint, and soroban data from simulation.
        val finalTransaction = kit.sorobanServer.prepareTransaction(signedTransaction, simulation)

        val useRelayer = getSubmissionMethod(forceMethod) == SubmissionMethod.RELAYER

        return submitOrRelay(
            transaction = finalTransaction,
            hostFunction = hostFunction,
            signedAuthEntries = signedAuthEntries,
            signer = deployer,
            useRelayer = useRelayer
        )
    }

    // MARK: - Testnet Wallet Funding

    /**
     * Funds the smart account wallet using Friendbot (testnet only).
     *
     * Creates a temporary keypair, funds it via Friendbot, then transfers the balance
     * (minus reserve) to the smart account contract. Supports relayer fee sponsoring
     * by converting source_account auth entries to Address credentials.
     *
     * Flow:
     * 1. Generate random temporary keypair
     * 2. Fund temp account via Friendbot HTTP GET
     * 3. Wait briefly for funding to confirm
     * 4. Query temp account balance via native token contract simulation
     * 5. Calculate transfer amount (balance - reserve)
     * 6. Build transfer from temp to smart account
     * 7. Simulate to get auth entries
     * 8. Convert source_account auth entries to Address credentials (for relayer)
     * 9. Sign auth entries with temp keypair
     * 10. Re-simulate with signed auth entries
     * 11. Decide fee sponsoring mode and submit
     * 12. Return funded amount in XLM
     *
     * ## Fee Sponsoring
     *
     * When a relayer URL is configured via `config.relayerUrl`:
     * - **Mode 1**: Used when no source_account auth exists after conversion.
     *   Sends host function + signed auth entries without signing the transaction envelope.
     *
     * - **Mode 2**: Used when source_account auth still exists (not converted).
     *   Sends fully signed transaction XDR with temp keypair signature.
     *
     * When no relayer is configured, submits directly via RPC with temp keypair signature.
     *
     * ## Source Account Auth Conversion
     *
     * The funding flow converts source_account (Void) credentials to Address credentials
     * with a generated nonce. This allows the relayer to substitute its own channel accounts
     * for fee sponsoring, enabling zero-balance smart accounts to receive their first funds.
     *
     * IMPORTANT: Only works on testnet. Do not use on mainnet.
     *
     * @param nativeTokenContract The native token (XLM) contract address (C-address)
     * @param forceMethod Optional override to force relayer or RPC submission (default: auto-detect)
     * @return The amount funded in XLM
     * @throws SmartAccountException if funding fails at any step
     *
     * Example:
     * ```kotlin
     * // Fund wallet (uses relayer if configured)
     * val fundedAmount = txOps.fundWallet(
     *     nativeTokenContract = "CBCD1234..."
     * )
     * println("Funded $fundedAmount XLM")
     *
     * // Fund wallet with direct RPC submission (no relayer)
     * val kit = OZSmartAccountKit.create(
     *     config = config.copy(relayerUrl = null)
     * )
     * val amount = kit.transactionOperations.fundWallet(nativeTokenContract)
     * println("Funded directly: $amount XLM")
     * ```
     *
     * @see convertAndSignAuthEntries for source_account to Address credential conversion
     */
    suspend fun fundWallet(
        nativeTokenContract: String,
        forceMethod: SubmissionMethod? = null
    ): String {
        val (_, contractId) = kit.requireConnected()

        // Validate native token contract address
        requireContractAddress(nativeTokenContract, "nativeTokenContract")

        // STEP 1: Create temporary keypair
        val tempKeypair = KeyPair.random()

        // STEP 2: Fund via Friendbot
        val funded = FriendBot.fundTestnetAccount(tempKeypair.getAccountId())
        if (!funded) {
            throw TransactionException.submissionFailed("Friendbot funding failed")
        }

        // STEP 3: Wait for Friendbot funding to propagate to Soroban RPC state.
        // Friendbot's HTTP response confirms the Horizon transaction, but the Soroban
        // RPC simulation endpoint may not reflect the new account entry until the
        // next ledger close (~5 seconds on testnet).
        delay(5000)

        // STEP 4: Get temp account
        val tempAccount = kit.sorobanServer.getAccount(tempKeypair.getAccountId())

        // STEP 5: Calculate transfer amount
        // Reserve for account minimum balance
        val reserveStroops = BigInteger.fromLong(OZConstants.FRIENDBOT_RESERVE_XLM.toLong() * Util.STROOPS_PER_XLM)

        // Query temp account balance via contract simulation
        val balanceArgs = listOf(
            Scv.toAddress(Address(tempKeypair.getAccountId()).toSCAddress())
        )
        val balanceInvokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(nativeTokenContract).toSCAddress(),
            functionName = SCSymbolXdr("balance"),
            args = balanceArgs
        )
        val balanceHostFunction = HostFunctionXdr.InvokeContract(balanceInvokeArgs)
        val balanceResult = simulateAndExtractResult(
            hostFunction = balanceHostFunction
        )

        // Parse I128 result to BigInteger stroops (handles full 128-bit range)
        val balanceStroops = try {
            Scv.fromInt128(balanceResult)
        } catch (_: Exception) {
            throw TransactionException.submissionFailed("Failed to query temp account balance")
        }

        if (balanceStroops <= reserveStroops) {
            throw TransactionException.submissionFailed("Insufficient balance after Friendbot funding")
        }

        val transferStroops: BigInteger = balanceStroops - reserveStroops

        // STEP 6: Build transfer from temp account to smart account
        val fromAddress = Address(tempKeypair.getAccountId()).toSCAddress()
        val toAddress = Address(contractId).toSCAddress()
        val amountScVal = Util.stroopsToI128ScVal(transferStroops)

        val functionArgs = listOf(
            Scv.toAddress(fromAddress),
            Scv.toAddress(toAddress),
            amountScVal
        )

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(nativeTokenContract).toSCAddress(),
            functionName = SCSymbolXdr("transfer"),
            args = functionArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)
        val operation = InvokeHostFunctionOperation(hostFunction, emptyList())

        // STEP 7: Simulate to get auth entries
        val transaction = TransactionBuilder(tempAccount, Network(kit.config.networkPassphrase))
            .setBaseFee(100)
            .addOperation(operation)
            .addMemo(MemoNone)
            .setTimeout(300)
            .build()

        val simulation = kit.sorobanServer.simulateTransaction(transaction)

        if (simulation.error != null) {
            throw TransactionException.simulationFailed("Failed to simulate funding transfer: ${simulation.error}")
        }

        // Extract auth entries from simulation
        val simulatedAuthEntries = simulation.results?.firstOrNull()?.parseAuth() ?: emptyList()

        // STEP 8: Convert source_account auth entries to Address credentials.
        // This allows the Relayer to use its own channel accounts for fee sponsoring.
        val latestLedger = kit.sorobanServer.getLatestLedger()
        val expirationLedger = latestLedger.sequence.toUInt() + Util.LEDGERS_PER_HOUR.toUInt()

        val signedAuthEntries = convertAndSignAuthEntries(
            authEntries = simulatedAuthEntries,
            tempKeypair = tempKeypair,
            expirationLedger = expirationLedger
        )

        // STEP 9: Rebuild transaction with signed auth entries and re-simulate.
        // Use a fresh account fetch to avoid sequence number issues.
        val tempAccountRefresh = kit.sorobanServer.getAccount(tempKeypair.getAccountId())

        val signedOperation = InvokeHostFunctionOperation(hostFunction, signedAuthEntries)
        val signedTransaction = TransactionBuilder(tempAccountRefresh, Network(kit.config.networkPassphrase))
            .setBaseFee(100)
            .addOperation(signedOperation)
            .addMemo(MemoNone)
            .setTimeout(300)
            .build()

        val reSimulation = kit.sorobanServer.simulateTransaction(signedTransaction)

        if (reSimulation.error != null) {
            throw TransactionException.simulationFailed("Re-simulation error: ${reSimulation.error}")
        }

        // STEP 10: Assemble the transaction using the SDK's prepareTransaction.
        // This correctly applies resource fees, footprint, and soroban data from simulation.
        val preparedTransaction = kit.sorobanServer.prepareTransaction(signedTransaction, reSimulation)

        // STEP 11: Determine submission method and submit
        val submissionMethod = getSubmissionMethod(forceMethod)
        val useRelayer = submissionMethod == SubmissionMethod.RELAYER

        val result = submitOrRelay(
            transaction = preparedTransaction,
            hostFunction = hostFunction,
            signedAuthEntries = signedAuthEntries,
            signer = tempKeypair,
            useRelayer = useRelayer,
            emitEvents = false
        )

        if (!result.success) {
            throw TransactionException.submissionFailed(
                "Funding transaction failed: ${result.error ?: "unknown error"}"
            )
        }

        // STEP 12: Return funded amount as XLM string
        val xlmWhole = transferStroops / BigInteger.fromLong(Util.STROOPS_PER_XLM)
        val xlmFraction = transferStroops % BigInteger.fromLong(Util.STROOPS_PER_XLM)
        return if (xlmFraction == BigInteger.ZERO) {
            xlmWhole.toString()
        } else {
            val fractionStr = xlmFraction.toString().padStart(7, '0').trimEnd('0')
            "$xlmWhole.$fractionStr"
        }
    }

    // MARK: - Simulation

    /**
     * Simulates a host function and extracts the return value.
     *
     * Builds a transaction with the given host function, simulates it via
     * Soroban RPC, and returns the result SCVal. Used for query operations
     * that don't require transaction submission.
     *
     * @param hostFunction The host function to simulate
     * @return The SCVal return value from the simulation
     * @throws TransactionException if simulation fails or result extraction fails
     */
    internal suspend fun simulateAndExtractResult(
        hostFunction: HostFunctionXdr
    ): SCValXdr {
        val deployer = kit.getDeployer()
        val deployerAccount = kit.sorobanServer.getAccount(deployer.getAccountId())

        val operation = InvokeHostFunctionOperation(hostFunction, emptyList())

        val transaction = TransactionBuilder(deployerAccount, Network(kit.config.networkPassphrase))
            .setBaseFee(100)
            .addOperation(operation)
            .addMemo(MemoNone)
            .setTimeout(300)
            .build()

        val simulation = kit.sorobanServer.simulateTransaction(transaction)

        if (simulation.error != null) {
            throw TransactionException.simulationFailed("Simulation error: ${simulation.error}")
        }

        val results = simulation.results
        if (results.isNullOrEmpty()) {
            throw TransactionException.simulationFailed("No results returned from simulation")
        }

        return results[0].parseXdr()
            ?: throw TransactionException.simulationFailed("No return value in simulation result")
    }

    // MARK: - Private Helpers

    /**
     * Converts source_account auth entries to Address credentials and signs them.
     *
     * For source_account credentials (Void type), this creates new Address credentials
     * with a nonce and signature. This allows the Relayer to use its own channel accounts
     * for fee sponsoring. For Address credentials, signs them with the provided keypair.
     *
     * @param authEntries The authorization entries to convert and sign
     * @param tempKeypair The keypair to use for signing
     * @param expirationLedger The ledger number at which signatures expire
     * @return List of signed authorization entries with Address credentials
     */
    private suspend fun convertAndSignAuthEntries(
        authEntries: List<SorobanAuthorizationEntryXdr>,
        tempKeypair: KeyPair,
        expirationLedger: UInt
    ): List<SorobanAuthorizationEntryXdr> {
        return authEntries.map { entry ->
            val credType = entry.credentials

            // For source_account credentials, convert to Address credentials
            if (credType is SorobanCredentialsXdr.Void) {
                // Generate a nonce for the new Address credential
                val nonce = Int64Xdr(generateNonce())

                // Build auth payload hash
                val payloadHash = SmartAccountAuth.buildSourceAccountAuthPayloadHash(
                    entry = entry,
                    nonce = nonce,
                    expirationLedger = expirationLedger,
                    networkPassphrase = kit.config.networkPassphrase
                )

                // Sign with temp keypair
                val signature = tempKeypair.sign(payloadHash)

                // Standard Ed25519 format: Vec([Map({public_key, signature})]).
                // This is for a classical Stellar account (temp keypair), NOT the smart
                // account's AuthPayload format. Do not merge with the AuthPayload path.
                val signatureMapScVal = Scv.toMap(linkedMapOf(
                    Scv.toSymbol("public_key") to Scv.toBytes(tempKeypair.getPublicKey()),
                    Scv.toSymbol("signature") to Scv.toBytes(signature)
                ))
                val signatureVecScVal = Scv.toVec(listOf(signatureMapScVal))

                // Create new Address credentials entry to replace source_account
                val addressCredentials = SorobanAddressCredentialsXdr(
                    address = Address(tempKeypair.getAccountId()).toSCAddress(),
                    nonce = nonce,
                    signatureExpirationLedger = Uint32Xdr(expirationLedger),
                    signature = signatureVecScVal
                )

                SorobanAuthorizationEntryXdr(
                    credentials = SorobanCredentialsXdr.Address(addressCredentials),
                    rootInvocation = entry.rootInvocation
                )
            } else if (credType is SorobanCredentialsXdr.Address) {
                // For Address credentials, sign them
                // Clone the entry to avoid mutating the original
                val entryBytes = XdrWriter().also { entry.encode(it) }.toByteArray()
                val entryCopy = SorobanAuthorizationEntryXdr.decode(XdrReader(entryBytes))

                val credentials = (entryCopy.credentials as SorobanCredentialsXdr.Address).value

                // Build auth payload hash
                val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
                    entry = entryCopy,
                    expirationLedger = expirationLedger,
                    networkPassphrase = kit.config.networkPassphrase
                )

                // Sign with temp keypair
                val signature = tempKeypair.sign(payloadHash)

                // Create signature map and vec
                val signatureMapScVal = Scv.toMap(linkedMapOf(
                    Scv.toSymbol("public_key") to Scv.toBytes(tempKeypair.getPublicKey()),
                    Scv.toSymbol("signature") to Scv.toBytes(signature)
                ))
                val signatureVecScVal = Scv.toVec(listOf(signatureMapScVal))

                // Create new credentials with updated signature
                val updatedCredentials = SorobanAddressCredentialsXdr(
                    address = credentials.address,
                    nonce = credentials.nonce,
                    signatureExpirationLedger = Uint32Xdr(expirationLedger),
                    signature = signatureVecScVal
                )

                SorobanAuthorizationEntryXdr(
                    credentials = SorobanCredentialsXdr.Address(updatedCredentials),
                    rootInvocation = entryCopy.rootInvocation
                )
            } else {
                // Unknown credential type - pass through unchanged
                entry
            }
        }
    }

    /**
     * Determines the submission method based on configuration and optional override.
     *
     * Priority:
     * 1. If `forceMethod` is specified, use it directly
     * 2. If a relayer is configured, use relayer
     * 3. Otherwise, use RPC
     *
     * @param forceMethod Optional override to force a specific submission method
     * @return The submission method to use
     */
    private fun getSubmissionMethod(forceMethod: SubmissionMethod?): SubmissionMethod {
        if (forceMethod != null) {
            return forceMethod
        }
        return if (kit.relayerClient != null) {
            SubmissionMethod.RELAYER
        } else {
            SubmissionMethod.RPC
        }
    }

    /**
     * Determines if relayer Mode 2 should be used based on auth entries.
     *
     * Mode 2 (signed transaction XDR) is required when any auth entry has
     * source_account credentials rather than address credentials.
     *
     * @param authEntries The authorization entries to check
     * @return True if Mode 2 should be used, false for Mode 1
     */
    private fun shouldUseRelayerMode2(authEntries: List<SorobanAuthorizationEntryXdr>): Boolean {
        return authEntries.any { entry ->
            // Check if credentials is SourceAccount type (Void in KMP SDK)
            entry.credentials is SorobanCredentialsXdr.Void
        }
    }

    /**
     * Looks up key data for a credential ID from on-chain context rules.
     *
     * Uses [OZContextRuleManager.getAllContextRules] to search all active rules
     * for a WebAuthn signer whose key data suffix matches the given credential ID.
     *
     * @param credentialIdBytes Raw credential ID bytes to search for
     * @return Full key data (publicKey + credentialId) for the matching signer
     * @throws CredentialException.NotFound if no matching signer is found
     */
    private suspend fun findKeyDataFromContextRules(credentialIdBytes: ByteArray): ByteArray {
        val allRules = kit.contextRuleManager.getAllContextRules()

        for (ruleScVal in allRules) {
            val fields = (ruleScVal as? SCValXdr.Map)?.value?.value ?: continue
            for (field in fields) {
                val key = (field.key as? SCValXdr.Sym)?.value?.value ?: continue
                if (key != "signers") continue
                val signerVec = (field.`val` as? SCValXdr.Vec)?.value?.value ?: break
                for (signerScVal in signerVec) {
                    val parts = (signerScVal as? SCValXdr.Vec)?.value?.value
                    if (parts.isNullOrEmpty()) continue
                    val tag = (parts[0] as? SCValXdr.Sym)?.value?.value ?: continue
                    if (tag != "External" || parts.size < 3) continue
                    val keyDataBytes = (parts[2] as? SCValXdr.Bytes)?.value?.value ?: continue
                    if (keyDataBytes.size > SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
                        val suffix = keyDataBytes.copyOfRange(
                            SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE,
                            keyDataBytes.size
                        )
                        if (suffix.contentEquals(credentialIdBytes)) {
                            return keyDataBytes
                        }
                    }
                }
                break
            }
        }

        throw CredentialException.notFound(
            "No signer found on-chain for credential ID: " +
                Util.base64urlEncode(credentialIdBytes)
        )
    }

    /**
     * Signs (when required), submits, and polls a built transaction via relayer or RPC.
     *
     * Handles Mode 1 (host function + auth entries) and Mode 2 (signed XDR) relayer paths,
     * and direct RPC submission. Signing is performed only when not using the relayer, or
     * when source_account auth entries are present (Mode 2).
     *
     * @param transaction The fully built transaction (not yet signed). Mutated by signing when required.
     * @param hostFunction The host function, passed to the relayer for Mode 1 submission.
     * @param signedAuthEntries Signed auth entries, used for Mode 1 and mode detection.
     * @param signer The keypair to sign with when signing is required.
     * @param useRelayer Whether to submit via relayer or RPC.
     * @param emitEvents Whether to emit [SmartAccountEvent.TransactionSubmitted] events (default true).
     * @return TransactionResult with the outcome after polling for confirmation.
     */
    private suspend fun submitOrRelay(
        transaction: Transaction,
        hostFunction: HostFunctionXdr,
        signedAuthEntries: List<SorobanAuthorizationEntryXdr>,
        signer: KeyPair,
        useRelayer: Boolean,
        emitEvents: Boolean = true
    ): TransactionResult {
        val hasSourceAuth = shouldUseRelayerMode2(signedAuthEntries)

        // Sign when not using the relayer (RPC always requires a signature), or when
        // using Mode 2 (source_account auth present — relayer needs a signed envelope).
        if (!useRelayer || hasSourceAuth) {
            transaction.sign(signer)
        }

        return if (useRelayer) {
            val relayer = kit.relayerClient
                ?: throw TransactionException.submissionFailed("Relayer is not configured")

            // Mode 2: source_account auth — submit signed transaction XDR
            // Mode 1: no source_account auth — relayer builds the envelope
            val relayerResponse = if (hasSourceAuth) {
                relayer.sendXdr(transaction.toEnvelopeXdr())
            } else {
                relayer.send(hostFunction, signedAuthEntries)
            }

            if (emitEvents && relayerResponse.hash != null) {
                kit.events.emit(
                    SmartAccountEvent.TransactionSubmitted(
                        hash = relayerResponse.hash,
                        success = relayerResponse.success
                    )
                )
            }

            if (relayerResponse.success && relayerResponse.hash != null) {
                pollForConfirmation(relayerResponse.hash)
            } else {
                TransactionResult(
                    success = false,
                    error = relayerResponse.error ?: "Relayer submission failed"
                )
            }
        } else {
            // Submit via RPC
            val sendResult = kit.sorobanServer.sendTransaction(transaction)

            when (sendResult.status) {
                SendTransactionStatus.ERROR -> {
                    return TransactionResult(
                        success = false,
                        hash = sendResult.hash ?: "",
                        error = sendResult.errorResultXdr ?: "Transaction rejected by network"
                    )
                }
                SendTransactionStatus.TRY_AGAIN_LATER -> {
                    return TransactionResult(
                        success = false,
                        hash = sendResult.hash ?: "",
                        error = "Network is congested. Try again later."
                    )
                }
                else -> {
                    // PENDING or DUPLICATE — poll for confirmation
                }
            }

            val hash = sendResult.hash
                ?: throw TransactionException.submissionFailed(
                    "No transaction hash returned from send result"
                )

            if (emitEvents) {
                kit.events.emit(
                    SmartAccountEvent.TransactionSubmitted(
                        hash = hash,
                        success = true
                    )
                )
            }

            pollForConfirmation(hash)
        }
    }

    /**
     * Polls for transaction confirmation using the SDK's [SorobanServer.pollTransaction].
     *
     * Uses 30 attempts with 3-second intervals (~90 seconds total) to account for
     * testnet ledger close times and potential congestion.
     *
     * @param hash The transaction hash to poll
     * @return TransactionResult indicating success or failure
     */
    private suspend fun pollForConfirmation(hash: String): TransactionResult {
        val txResponse = kit.sorobanServer.pollTransaction(
            hash = hash,
            maxAttempts = 30,
            sleepStrategy = { 3000L }
        )

        return when (txResponse.status) {
            GetTransactionStatus.SUCCESS -> TransactionResult(
                success = true,
                hash = hash,
                ledger = txResponse.ledger?.toUInt()
            )
            GetTransactionStatus.FAILED -> TransactionResult(
                success = false,
                hash = hash,
                ledger = txResponse.ledger?.toUInt(),
                error = txResponse.resultXdr ?: "Transaction failed on-chain"
            )
            GetTransactionStatus.NOT_FOUND -> TransactionResult(
                success = false,
                hash = hash,
                error = "Transaction not confirmed after 30 polling attempts"
            )
        }
    }

    private suspend fun generateNonce(): Long {
        val randomBytes = getEd25519Crypto().generatePrivateKey()
        var nonce = 0L
        for (i in 0..7) {
            nonce = (nonce shl 8) or (randomBytes[i].toLong() and 0xFF)
        }
        return nonce
    }
}