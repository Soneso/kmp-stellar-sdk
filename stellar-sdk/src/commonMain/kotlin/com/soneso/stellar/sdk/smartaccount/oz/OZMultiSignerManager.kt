//
//  OZMultiSignerManager.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.AbstractTransaction
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MemoNone
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.HashIDPreimageSorobanAuthorizationXdr
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter

/**
 * Specifies a signer to participate in a multi-signature operation.
 *
 * The caller explicitly lists every signer that should sign. There is no implicit
 * connected passkey — if the connected passkey should sign, include
 * [SelectedSigner.Passkey] in the list.
 */
sealed class SelectedSigner {
    /**
     * A passkey (WebAuthn) signer.
     *
     * Each instance triggers one OS WebAuthn authentication prompt.
     *
     * @property credentialId Base64URL-encoded credential ID for display/logging.
     * @property credentialIdBytes Raw credential ID bytes used for the WebAuthn
     *   allowCredentials constraint. Passed directly to avoid base64url
     *   encode/decode round-trip issues on JS.
     * @property keyData Full key data (secp256r1 public key + credentialId bytes) for this
     *   passkey signer. When provided, the SDK uses it directly without an on-chain lookup.
     *   Must be supplied by the caller from the signer data already available client-side.
     */
    data class Passkey(
        val credentialId: String? = null,
        val credentialIdBytes: ByteArray? = null,
        val keyData: ByteArray? = null
    ) : SelectedSigner() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Passkey
            if (credentialId != other.credentialId) return false
            if (credentialIdBytes != null) {
                if (other.credentialIdBytes == null || !credentialIdBytes.contentEquals(other.credentialIdBytes)) return false
            } else if (other.credentialIdBytes != null) return false
            if (keyData != null) {
                if (other.keyData == null || !keyData.contentEquals(other.keyData)) return false
            } else if (other.keyData != null) return false
            return true
        }

        override fun hashCode(): Int {
            var result = credentialId?.hashCode() ?: 0
            result = 31 * result + (credentialIdBytes?.contentHashCode() ?: 0)
            result = 31 * result + (keyData?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * A delegated wallet signer identified by its Stellar G-address.
     *
     * The address must have been registered as a `Delegated` signer on the smart account
     * contract and the external wallet adapter must be able to sign for it.
     *
     * @property address The Stellar G-address of the delegated signer.
     */
    data class Wallet(val address: String) : SelectedSigner()
}

// MARK: - Multi-Signer Manager

/**
 * Manager for multi-signature smart account operations.
 *
 * OZMultiSignerManager provides functionality for executing multi-signature operations —
 * including token transfers and arbitrary contract calls — collecting signatures from both
 * passkey and external wallet signers.
 *
 * Multi-signature transactions require collecting signatures from multiple signers
 * sequentially to enable fail-fast behavior on user cancellation.
 *
 * Signatures are collected in the order the caller supplies them via [SelectedSigner]:
 * - Each [SelectedSigner.Passkey] triggers one OS WebAuthn authentication prompt.
 * - Each [SelectedSigner.Wallet] signs via the configured external wallet adapter.
 *
 * The connected passkey is NOT added implicitly. If it should sign, include
 * [SelectedSigner.Passkey] with the credential ID and keyData populated from the
 * signer data obtained during context rule discovery.
 *
 * Delegated signers produce their own auth entries with Address credentials that
 * reference the smart account's __check_auth function. The smart account's signature
 * map includes a placeholder entry for each delegated signer.
 *
 * Example usage:
 * ```kotlin
 * val kit = OZSmartAccountKit.create(config)
 * val multiSigner = kit.multiSignerManager
 *
 * // Execute multi-signature transfer — caller lists ALL signers explicitly
 * val result = multiSigner.multiSignerTransfer(
 *     tokenContract = "CBCD...",
 *     recipient = "GA7Q...",
 *     amount = "100",
 *     selectedSigners = listOf(
 *         SelectedSigner.Passkey(
 *             credentialId = credIdStr,
 *             credentialIdBytes = credIdBytes,
 *             keyData = signer.keyData
 *         ),
 *         SelectedSigner.Wallet("GA7Q...")
 *     )
 * )
 * println("Transfer ${if (result.success) "succeeded" else "failed"}")
 * ```
 */
class OZMultiSignerManager internal constructor(
    private val kit: OZSmartAccountKit
) {
    // MARK: - Multi-Signer Transfer

    /**
     * Executes a token transfer signed by an explicit list of signers.
     *
     * The caller supplies every signer that must sign via [selectedSigners]. There is no
     * implicit connected passkey — include [SelectedSigner.Passkey] if the connected
     * passkey should sign. Signatures are collected in list order:
     * - [SelectedSigner.Passkey] — triggers one OS WebAuthn authentication prompt each.
     * - [SelectedSigner.Wallet] — requests a delegated auth entry from the external wallet.
     *
     * Delegated signers produce their own auth entries with Address credentials that
     * invoke the smart account's __check_auth function. The smart account's signature
     * map includes a placeholder entry for each delegated signer.
     *
     * @param tokenContract The token contract address (C-address)
     * @param recipient The recipient address (G-address or C-address)
     * @param amount Decimal amount to transfer (e.g., "100" or "10.5")
     * @param selectedSigners All signers that must sign, in collection order.
     *   The list must not be empty.
     * @param forceMethod Optional override for the submission method. When null (default),
     *   the SDK auto-detects whether to use the relayer or direct submission.
     * @param resolveContextRuleIds Optional callback to resolve context rule IDs per auth entry.
     *   When null (default), the SDK resolves the rule IDs automatically from the selected signers
     *   and available context rules. Provide a callback when automatic resolution fails due to
     *   ambiguity (selected signers match multiple rules) or to bypass auto-resolution.
     * @return TransactionResult indicating success or failure
     * @throws SmartAccountException if validation fails, signing fails, or submission fails
     *
     * Example:
     * ```kotlin
     * val result = multiSigner.multiSignerTransfer(
     *     tokenContract = nativeTokenAddress,
     *     recipient = "GBXYZ...",
     *     amount = "50",
     *     selectedSigners = listOf(
     *         SelectedSigner.Passkey(),             // connected passkey
     *         SelectedSigner.Passkey("credBase64"), // a second specific passkey
     *         SelectedSigner.Wallet("GA7Q...")      // delegated wallet signer
     *     )
     * )
     * if (result.success) {
     *     println("Multi-sig transfer succeeded: ${result.hash ?: ""}")
     * }
     * ```
     */
    suspend fun multiSignerTransfer(
        tokenContract: String,
        recipient: String,
        amount: String,
        selectedSigners: List<SelectedSigner>,
        forceMethod: SubmissionMethod? = null,
        resolveContextRuleIds: ResolveContextRuleIds? = null
    ): TransactionResult {
        val (_, contractId) = kit.requireConnected()

        requireStellarAddress(recipient, "recipient")

        if (recipient == contractId) {
            throw ValidationException.invalidInput(
                "recipient",
                "Cannot transfer to self"
            )
        }

        val stroops = Util.amountToStroops(amount)

        val targetArgs = listOf(
            Scv.toAddress(Address(contractId).toSCAddress()),
            Scv.toAddress(Address(recipient).toSCAddress()),
            Util.stroopsToI128ScVal(stroops)
        )

        return multiSignerContractCall(
            target = tokenContract,
            targetFn = "transfer",
            targetArgs = targetArgs,
            selectedSigners = selectedSigners,
            forceMethod = forceMethod,
            resolveContextRuleIds = resolveContextRuleIds
        )
    }

    // MARK: - Multi-Signer Direct Contract Call

    /**
     * Calls an arbitrary function on an external contract directly with multi-signer authorization.
     *
     * Builds a host function that invokes `target.targetFn(targetArgs)` directly (not through
     * the smart account's `execute()` entry point). Context rules of type `CallContract(target)`
     * are matched for authorization, allowing contract-specific multi-signer rules to apply.
     *
     * This is the multi-signer counterpart to [OZTransactionOperations.contractCall].
     *
     * @param target The contract address (C-address) to call.
     * @param targetFn The function name to invoke on the target contract.
     * @param targetArgs Pre-encoded SCVal arguments for the function.
     * @param selectedSigners All signers that must participate, in signing order.
     * @param forceMethod Optional override to force relayer or RPC submission.
     * @param resolveContextRuleIds Optional callback to resolve context rule IDs per auth entry.
     * @return TransactionResult indicating success or failure.
     * @throws SmartAccountException if validation fails, signing fails, or submission fails.
     */
    suspend fun multiSignerContractCall(
        target: String,
        targetFn: String,
        targetArgs: List<SCValXdr> = emptyList(),
        selectedSigners: List<SelectedSigner>,
        forceMethod: SubmissionMethod? = null,
        resolveContextRuleIds: ResolveContextRuleIds? = null
    ): TransactionResult {
        kit.requireConnected()
        validateContractCallArgs(target, targetFn, selectedSigners)

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(target).toSCAddress(),
            functionName = SCSymbolXdr(targetFn),
            args = targetArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        return submitWithMultipleSigners(hostFunction, selectedSigners, forceMethod, resolveContextRuleIds)
    }

    // MARK: - Multi-Signer Execute (Smart-Account Mediated Call)

    /**
     * Executes an arbitrary contract function through the smart account's `execute` entry
     * point with multi-signer authorization.
     *
     * This method is the multi-signer counterpart to [OZTransactionOperations.executeAndSubmit].
     * Use it when a contract call must be authorized by more than one signer — for example, a
     * governance vote, a multisig swap, or any operation gated by a multi-signer context rule.
     *
     * The method routes the call through the smart account contract's `execute(target, target_fn,
     * target_args)` entry point and collects signatures from all [selectedSigners] before submission.
     *
     * This method takes `target`, `targetFn`, and `targetArgs` directly rather than accepting an
     * `AssembledTransaction`. The KMP SDK constructs transactions from raw XDR without an
     * AssembledTransaction wrapper; `targetArgs` must therefore be pre-encoded using [Scv] helpers.
     *
     * @param target The target contract address (C-address) to invoke via `execute`.
     * @param targetFn The function name to call on the target contract.
     * @param targetArgs Pre-encoded arguments for the target function. Use [Scv] helpers
     *   (e.g., `Scv.toUint32`, `Scv.toBoolean`, `Scv.toAddress`) to encode each argument.
     *   Defaults to an empty list for functions that take no arguments.
     * @param selectedSigners All signers that must sign, in collection order.
     *   - [SelectedSigner.Passkey] triggers one OS WebAuthn authentication prompt per entry.
     *   - [SelectedSigner.Wallet] requests a delegated auth entry from the external wallet adapter.
     *   The list must not be empty.
     * @param forceMethod Optional override for the submission method. When null (default),
     *   the SDK auto-detects whether to use the relayer or direct submission.
     * @param resolveContextRuleIds Optional callback to resolve context rule IDs per auth entry.
     *   When null (default), the SDK resolves the rule IDs automatically from the selected signers
     *   and available context rules.
     * @return [TransactionResult] indicating success or failure with the transaction hash.
     * @throws SmartAccountException if validation fails, signing fails, or submission fails.
     *
     * Example — multi-signer governance vote:
     * ```kotlin
     * val result = multiSigner.multiSignerExecuteAndSubmit(
     *     target = "CDAO_CONTRACT_ADDRESS_HERE...",
     *     targetFn = "vote",
     *     targetArgs = listOf(
     *         Scv.toUint32(proposalId),
     *         Scv.toBoolean(true)
     *     ),
     *     selectedSigners = listOf(
     *         SelectedSigner.Passkey(
     *             credentialId = credIdStr,
     *             credentialIdBytes = credIdBytes,
     *             keyData = signer.keyData
     *         ),
     *         SelectedSigner.Wallet("GA7Q...")
     *     )
     * )
     * if (result.success) {
     *     println("Vote submitted: ${result.hash ?: ""}")
     * }
     * ```
     */
    suspend fun multiSignerExecuteAndSubmit(
        target: String,
        targetFn: String,
        targetArgs: List<SCValXdr> = emptyList(),
        selectedSigners: List<SelectedSigner>,
        forceMethod: SubmissionMethod? = null,
        resolveContextRuleIds: ResolveContextRuleIds? = null
    ): TransactionResult {
        val (_, contractId) = kit.requireConnected()
        validateContractCallArgs(target, targetFn, selectedSigners)

        // Build host function for execute(target, target_fn, target_args) on the smart account
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

        return submitWithMultipleSigners(hostFunction, selectedSigners, forceMethod, resolveContextRuleIds)
    }

    // MARK: - Internal Shared Signing Pipeline

    /**
     * Shared signing pipeline for multi-signer operations.
     *
     * Handles wallet signer validation, simulation, auth entry signing, re-simulation, and
     * submission. The caller is responsible for building the [hostFunction] before calling this.
     *
     * Used by [multiSignerTransfer], [multiSignerExecuteAndSubmit], and by other managers
     * (signer, policy, context rule) when they receive a non-empty `selectedSigners` list
     * for multi-signer authorization.
     *
     * @param hostFunction The host function to invoke.
     * @param selectedSigners All signers that must sign, in collection order.
     * @param forceMethod Optional override for the submission method.
     * @param resolveContextRuleIds Optional callback to resolve context rule IDs per auth entry.
     * @return [TransactionResult] indicating success or failure.
     * @throws SmartAccountException if validation fails, signing fails, or submission fails.
     */
    suspend fun submitWithMultipleSigners(
        hostFunction: HostFunctionXdr,
        selectedSigners: List<SelectedSigner>,
        forceMethod: SubmissionMethod? = null,
        resolveContextRuleIds: ResolveContextRuleIds? = null
    ): TransactionResult {
        val (_, contractId) = kit.requireConnected()

        // Validate: wallet signers require an external wallet adapter
        val walletSigners = selectedSigners.filterIsInstance<SelectedSigner.Wallet>()
        if (walletSigners.isNotEmpty() && kit.externalWallet == null) {
            throw ValidationException.invalidInput(
                "selectedSigners",
                "Wallet signers require an external wallet adapter to be configured"
            )
        }

        // Validate: each wallet signer must be reachable via the external wallet
        for (walletSigner in walletSigners) {
            val canSign = try {
                kit.externalWallet!!.canSignFor(walletSigner.address)
            } catch (e: Exception) {
                false
            }
            if (!canSign) {
                throw ValidationException.invalidInput(
                    "selectedSigners",
                    "No signer available for address: ${walletSigner.address}. " +
                        "Use externalWallet.addFromSecret() or externalWallet.addFromWallet() to add a signer."
                )
            }
        }

        // Step 1: Simulate to get auth entries
        val deployer = kit.getDeployer()
        val deployerAccount = kit.sorobanServer.getAccount(deployer.getAccountId())

        val operation = InvokeHostFunctionOperation(hostFunction, emptyList())
        val transaction = TransactionBuilder(deployerAccount, Network(kit.config.networkPassphrase))
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .addOperation(operation)
            .addMemo(MemoNone)
            .setTimeout(kit.config.timeoutInSeconds.toLong())
            .build()

        val simulation = kit.sorobanServer.simulateTransaction(transaction)

        if (simulation.error != null) {
            throw TransactionException.simulationFailed("Simulation error: ${simulation.error}")
        }

        val authEntries = simulation.results?.firstOrNull()?.parseAuth()
            ?: throw TransactionException.simulationFailed("No auth entries returned from simulation")

        // Step 2: Get current ledger sequence
        val latestLedger = kit.sorobanServer.getLatestLedger()

        // Step 3: Calculate expiration
        val expirationLedger = latestLedger.sequence.toUInt() + kit.config.signatureExpirationLedgers.toUInt()

        // Pre-fetch context rules ONCE for all auth entries (avoids N+1 RPC calls per entry)
        val contextRules = kit.contextRuleManager.listContextRules()

        // Build the list of SmartAccountSigner objects from selectedSigners for rule resolution.
        // Hoisted outside the auth entry loop since selectedSigners is invariant across entries.
        val smartAccountSigners = selectedSigners.map { selectedSigner ->
            when (selectedSigner) {
                is SelectedSigner.Passkey -> {
                    val keyData = selectedSigner.keyData
                        ?: throw ValidationException.invalidInput(
                            "selectedSigners",
                            "keyData is required for passkey signers for rule resolution"
                        )
                    ExternalSigner(
                        verifierAddress = kit.config.webauthnVerifierAddress,
                        keyData = keyData
                    )
                }
                is SelectedSigner.Wallet -> {
                    DelegatedSigner(address = selectedSigner.address)
                }
            }
        }

        // Step 4: Sign auth entries.
        // Uses the same SmartAccountAuth.signAuthEntry() as the single-signer flow.
        // signAuthEntry is called once per passkey and the entry accumulates signatures across calls.
        val signedAuthEntries = mutableListOf<SorobanAuthorizationEntryXdr>()

        for ((entryIndex, entry) in authEntries.withIndex()) {
            // Check if this entry's credentials match our contract
            val credentials = (entry.credentials as? SorobanCredentialsXdr.Address)?.value
            if (credentials == null) {
                signedAuthEntries.add(entry)
                continue
            }

            val entryAddress = try { Address.fromSCAddress(credentials.address).toString() } catch (_: Exception) { null }
            if (entryAddress != contractId) {
                // The entry address doesn't match the smart account contract.
                // Check whether it matches any SelectedSigner.Wallet address — if so, sign it
                // directly using the external wallet adapter.
                val matchingWalletSigner = walletSigners.firstOrNull { it.address == entryAddress }
                if (matchingWalletSigner != null) {
                    val signedWalletEntry = signWalletAddressAuthEntry(
                        entry = entry,
                        walletSigner = matchingWalletSigner,
                        expirationLedger = expirationLedger
                    )
                    signedAuthEntries.add(signedWalletEntry)
                } else {
                    throw TransactionException.signingFailed(
                        "Unsupported auth entry for $entryAddress. " +
                            "Add an external signer for that address or remove it from the transaction."
                    )
                }
                continue
            }

            // Clone the entry and set the expiration ledger before signing
            var signedEntry = cloneEntryWithExpiration(entry, expirationLedger)

            // Use caller-provided callback or resolve automatically.
            val resolvedContextRuleIds = if (resolveContextRuleIds != null) {
                resolveContextRuleIds(signedEntry, entryIndex)
            } else {
                kit.contextRuleManager.resolveContextRuleIdsForEntry(
                    signedEntry, smartAccountSigners, contextRules
                )
            }

            // Compute the payload hash once for this entry (used by both passkey and delegated paths).
            val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
                entry = signedEntry,
                expirationLedger = expirationLedger,
                networkPassphrase = kit.config.networkPassphrase
            )

            // Compute the auth digest binding the rule IDs. This is what all signers actually sign,
            // preventing rule-selection downgrade attacks.
            val authDigest = SmartAccountAuth.buildAuthDigest(payloadHash, resolvedContextRuleIds)

            // Step 4a: Sign with each passkey signer using SmartAccountAuth.signAuthEntry().
            // Each call triggers one WebAuthn prompt and appends to the signature map.
            for ((signerIndex, selectedSigner) in selectedSigners.withIndex()) {
                when (selectedSigner) {
                    is SelectedSigner.Passkey -> {
                        val webauthnProvider = kit.config.webauthnProvider
                            ?: throw ValidationException.invalidInput(
                                "webauthnProvider",
                                "WebAuthn provider is required for passkey signers but is not configured"
                            )

                        // Trigger WebAuthn authentication (one OS prompt per passkey signer).
                        // Pass credentialIdBytes as allowCredentials so the browser uses
                        // the correct passkey when multiple exist for this RP.
                        val allowCredentialIds = selectedSigner.credentialIdBytes?.let { listOf(it) }

                        val authResult = try {
                            webauthnProvider.authenticate(authDigest, allowCredentialIds)
                        } catch (e: Exception) {
                            throw WebAuthnException.authenticationFailed(
                                "WebAuthn authentication failed for passkey signer " +
                                    "${signerIndex + 1}/${selectedSigners.size}: ${e.message}",
                                e
                            )
                        }

                        // Normalize DER signature to compact format with low-S
                        val compactSig = SmartAccountUtils.normalizeSignature(authResult.signature)

                        val webAuthnSig = WebAuthnSignature(
                            authenticatorData = authResult.authenticatorData,
                            clientData = authResult.clientDataJSON,
                            signature = compactSig
                        )

                        // keyData is guaranteed non-null here — validated during
                        // smartAccountSigners construction above.
                        val passkeySigner = ExternalSigner(
                            verifierAddress = kit.config.webauthnVerifierAddress,
                            keyData = selectedSigner.keyData!!
                        )

                        // Attach the signature to the entry.
                        // This appends to the existing signature map if one exists.
                        signedEntry = SmartAccountAuth.signAuthEntry(
                            entry = signedEntry,
                            signer = passkeySigner,
                            signature = webAuthnSig,
                            expirationLedger = expirationLedger,
                            contextRuleIds = resolvedContextRuleIds
                        )
                    }

                    is SelectedSigner.Wallet -> {
                        // Delegated wallet signers are handled after all passkey signatures
                    }
                }
            }

            // Step 4b: Add delegated signer auth entries and placeholders.
            // Each delegated signer gets:
            // - Its own signed auth entry (built and signed via Auth.authorizeInvocation)
            // - An empty-bytes placeholder in the smart account's signature map
            for (selectedSigner in selectedSigners) {
                if (selectedSigner !is SelectedSigner.Wallet) continue

                // externalWallet is guaranteed non-null — validated at method entry.
                val externalWallet = kit.externalWallet!!

                // Build the invocation targeting the smart account's __check_auth.
                // The auth digest (payloadHash bound to contextRuleIds) is passed as argument,
                // matching what the verifier contract expects.
                val checkAuthInvocation = SorobanAuthorizedInvocationXdr(
                    function = SorobanAuthorizedFunctionXdr.ContractFn(
                        InvokeContractArgsXdr(
                            contractAddress = Address(contractId).toSCAddress(),
                            functionName = SCSymbolXdr("__check_auth"),
                            args = listOf(Scv.toBytes(authDigest))
                        )
                    ),
                    subInvocations = emptyList()
                )

                // Create an Auth.Signer that delegates signing to the ExternalWalletAdapter.
                // The adapter receives the base64-encoded HashIDPreimage XDR and returns
                // the raw Ed25519 signature bytes (base64-encoded).
                @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                val authSigner = Auth.Signer { preimage ->
                    val writer = XdrWriter()
                    preimage.encode(writer)
                    val preimageXdr = kotlin.io.encoding.Base64.encode(writer.toByteArray())
                    val result = try {
                        externalWallet.signAuthEntry(
                            preimageXdr,
                            SignAuthEntryOptions(
                                networkPassphrase = kit.config.networkPassphrase,
                                address = selectedSigner.address
                            )
                        )
                    } catch (e: Exception) {
                        throw TransactionException.signingFailed(
                            "External wallet signing failed for ${selectedSigner.address}: ${e.message}", e
                        )
                    }

                    val signatureBytes = kotlin.io.encoding.Base64.decode(result.signedAuthEntry)
                    Auth.Signature(
                        publicKey = result.signerAddress ?: selectedSigner.address,
                        signature = signatureBytes
                    )
                }

                // Build and sign the delegated auth entry using Auth.authorizeInvocation.
                // This handles nonce generation, preimage construction, signing, and
                // building the {public_key, signature} format.
                val signedDelegatedEntry = Auth.authorizeInvocation(
                    signer = authSigner,
                    publicKey = selectedSigner.address,
                    validUntilLedgerSeq = expirationLedger.toLong(),
                    invocation = checkAuthInvocation,
                    network = Network(kit.config.networkPassphrase)
                )
                signedAuthEntries.add(signedDelegatedEntry)

                // Add empty-bytes placeholder to the smart account's signature map.
                val delegatedSigner = DelegatedSigner(address = selectedSigner.address)
                signedEntry = SmartAccountAuth.addRawSignatureMapEntry(
                    entry = signedEntry,
                    signerKey = delegatedSigner.toScVal(),
                    signatureValue = Scv.toBytes(byteArrayOf()),
                    contextRuleIds = resolvedContextRuleIds
                )
            }

            signedAuthEntries.add(signedEntry)
        }

        // Update lastUsedAt for each passkey signer that participated (once per transaction)
        for (signer in selectedSigners) {
            if (signer is SelectedSigner.Passkey) {
                val credId = signer.credentialId ?: continue
                try {
                    kit.credentialManager.updateLastUsed(credId)
                } catch (_: Exception) {
                    // Non-critical — credential tracking is best-effort
                }
            }
        }

        // Step 5: Re-simulate with signed auth entries.
        // Use a fresh deployer account to avoid sequence number double-increment.
        val refetchedDeployerAccount = kit.sorobanServer.getAccount(deployer.getAccountId())

        val signedOperation = InvokeHostFunctionOperation(hostFunction, signedAuthEntries)
        val signedTransaction = TransactionBuilder(
            refetchedDeployerAccount,
            Network(kit.config.networkPassphrase)
        )
            .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
            .addOperation(signedOperation)
            .addMemo(MemoNone)
            .setTimeout(kit.config.timeoutInSeconds.toLong())
            .build()

        val resignedSimulation = kit.sorobanServer.simulateTransaction(signedTransaction)

        if (resignedSimulation.error != null) {
            throw TransactionException.simulationFailed("Re-simulation error: ${resignedSimulation.error}")
        }

        // Step 6: Assemble and submit via the same Mode 1 / Mode 2 routing as single-signer.
        // Mode 1 (default): relayer receives hostFunction + authEntries and builds the envelope.
        // Mode 2 (fallback): used only when source_account auth entries are present.
        // prepareTransaction applies resource fees, footprint, and soroban data from simulation.
        return kit.transactionOperations.submitMultiSignerTransaction(
            hostFunction = hostFunction,
            signedAuthEntries = signedAuthEntries,
            signedTransaction = signedTransaction,
            simulation = resignedSimulation,
            forceMethod = forceMethod
        )
    }

    // MARK: - Private Helpers

    /**
     * Validates shared parameters for contract call methods.
     */
    private fun validateContractCallArgs(
        target: String,
        targetFn: String,
        selectedSigners: List<SelectedSigner>
    ) {
        requireContractAddress(target, "target")

        if (targetFn.isBlank()) {
            throw ValidationException.invalidInput("targetFn", "Function name cannot be empty")
        }

        if (selectedSigners.isEmpty()) {
            throw ValidationException.invalidInput(
                "selectedSigners",
                "At least one signer must be provided"
            )
        }
    }

    /**
     * Signs an auth entry whose address matches a [SelectedSigner.Wallet] address directly,
     * without going through the smart account's __check_auth indirection.
     *
     * This handles auth entries produced by arbitrary contract calls (via `execute`) that
     * require direct authorization from the wallet signer's own address rather than from the
     * smart account contract.
     *
     * The signature is formatted as `Vec([Map({Symbol("public_key"): Bytes, Symbol("signature"): Bytes})])`,
     * matching the standard Ed25519 authorization format expected by Soroban contracts.
     *
     * @param entry The auth entry to sign.
     * @param walletSigner The wallet signer whose address matches the entry.
     * @param expirationLedger The ledger at which the signature expires.
     * @return The signed auth entry with expiration and signature set.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private suspend fun signWalletAddressAuthEntry(
        entry: SorobanAuthorizationEntryXdr,
        walletSigner: SelectedSigner.Wallet,
        expirationLedger: UInt
    ): SorobanAuthorizationEntryXdr {
        // externalWallet is guaranteed non-null — validated in submitWithMultipleSigners.
        val externalWallet = kit.externalWallet!!

        // Clone and set the expiration ledger
        val signedEntry = cloneEntryWithExpiration(entry, expirationLedger)

        val credentials = (signedEntry.credentials as? SorobanCredentialsXdr.Address)?.value
            ?: throw TransactionException.signingFailed(
                "Expected Address credentials on wallet auth entry for ${walletSigner.address}"
            )

        // Build HashIDPreimage::SorobanAuthorization from the cloned entry's credentials
        val networkId = getSha256ForNetworkPassphrase(kit.config.networkPassphrase)
        val authPreimage = HashIDPreimageSorobanAuthorizationXdr(
            networkId = HashXdr(networkId),
            nonce = credentials.nonce,
            signatureExpirationLedger = credentials.signatureExpirationLedger,
            invocation = signedEntry.rootInvocation
        )
        val preimage = HashIDPreimageXdr.SorobanAuthorization(authPreimage)

        val writer = XdrWriter()
        preimage.encode(writer)
        val preimageXdr = kotlin.io.encoding.Base64.encode(writer.toByteArray())

        // Request signature from the external wallet
        val signResult = try {
            externalWallet.signAuthEntry(
                preimageXdr,
                SignAuthEntryOptions(
                    networkPassphrase = kit.config.networkPassphrase,
                    address = walletSigner.address
                )
            )
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "External wallet signing failed for ${walletSigner.address}: ${e.message}", e
            )
        }

        val signatureBytes = kotlin.io.encoding.Base64.decode(signResult.signedAuthEntry)

        // Derive the raw 32-byte Ed25519 public key from the G-address
        val publicKeyBytes = KeyPair.fromAccountId(walletSigner.address).getPublicKey()

        // Build Vec([Map({public_key: Bytes, signature: Bytes})]) — standard Ed25519 auth format
        val sigMap = linkedMapOf(
            Scv.toSymbol("public_key") to Scv.toBytes(publicKeyBytes),
            Scv.toSymbol("signature") to Scv.toBytes(signatureBytes)
        )
        val signatureScVal = Scv.toVec(listOf(Scv.toMap(sigMap)))

        // Set the signature on the cloned entry's credentials
        val updatedCredentials = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = credentials.signatureExpirationLedger,
            signature = signatureScVal
        )

        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(updatedCredentials),
            rootInvocation = signedEntry.rootInvocation
        )
    }

    /**
     * Computes the SHA-256 hash of the network passphrase for use in preimage construction.
     */
    private suspend fun getSha256ForNetworkPassphrase(networkPassphrase: String): ByteArray {
        return getSha256Crypto().hash(networkPassphrase.encodeToByteArray())
    }

    /**
     * Clones an auth entry via XDR round-trip and sets the signatureExpirationLedger.
     */
    private fun cloneEntryWithExpiration(
        entry: SorobanAuthorizationEntryXdr,
        expirationLedger: UInt
    ): SorobanAuthorizationEntryXdr {
        // XDR round-trip clone
        val writer = XdrWriter()
        entry.encode(writer)
        val reader = XdrReader(writer.toByteArray())
        val cloned = SorobanAuthorizationEntryXdr.decode(reader)

        // Set expiration on the cloned entry
        val credentials = (cloned.credentials as? SorobanCredentialsXdr.Address)?.value
            ?: return cloned

        val updated = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = Uint32Xdr(expirationLedger),
            signature = credentials.signature
        )

        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(updated),
            rootInvocation = cloned.rootInvocation
        )
    }
}
