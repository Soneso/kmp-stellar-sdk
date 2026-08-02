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
import com.soneso.stellar.sdk.addressCredentials
import com.soneso.stellar.sdk.withUpdatedAddressCredentials
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.MemoNone
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
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
     * @property transports Optional transport hints for this credential (e.g., "internal",
     *   "hybrid"). Enables cross-device authentication flows such as QR code scanning.
     */
    data class Passkey(
        val credentialId: String? = null,
        val credentialIdBytes: ByteArray? = null,
        val keyData: ByteArray? = null,
        val transports: List<String>? = null
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
            if (transports != other.transports) return false
            return true
        }

        override fun hashCode(): Int {
            var result = credentialId?.hashCode() ?: 0
            result = 31 * result + (credentialIdBytes?.contentHashCode() ?: 0)
            result = 31 * result + (keyData?.contentHashCode() ?: 0)
            result = 31 * result + (transports?.hashCode() ?: 0)
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

    /**
     * An Ed25519 external signer identified by the verifier contract address and
     * 32-byte public key.
     *
     * The `(verifierAddress, publicKey)` pair identifies the on-chain
     * `External(verifierAddress, publicKey)` signer slot. Signing capability for this
     * signer must be registered separately via
     * [OZExternalSignerManager.addEd25519FromRawKey] (or by supplying an
     * [OZExternalEd25519SignerAdapter] through [OZSmartAccountConfig.externalEd25519Adapter]
     * when constructing the kit) before including this selector in a multi-signer operation.
     *
     * Unlike passkey selectors, this type carries no signing material — it is a pure
     * identifier.
     *
     * @property verifierAddress C-strkey of the Ed25519 verifier contract registered as
     *   part of the on-chain `External(verifierAddress, publicKey)` signer entry.
     * @property publicKey 32-byte Ed25519 public key identifying the signer slot on the
     *   smart account.
     */
    data class Ed25519(
        val verifierAddress: String,
        val publicKey: ByteArray,
    ) : SelectedSigner() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Ed25519
            if (verifierAddress != other.verifierAddress) return false
            return publicKey.contentEquals(other.publicKey)
        }

        override fun hashCode(): Int {
            var result = verifierAddress.hashCode()
            result = 31 * result + publicKey.contentHashCode()
            return result
        }
    }
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
     * @param decimals The token's decimal scale used to convert [amount] to base units.
     *   When null (default), the token's on-chain `decimals()` is fetched via
     *   [OZTransactionOperations.fetchTokenDecimals]. Supply it to skip the extra
     *   simulation round-trip (XLM and SAC-wrapped classic assets use 7).
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
        decimals: Int? = null,
        selectedSigners: List<SelectedSigner>,
        forceMethod: SubmissionMethod? = null,
        resolveContextRuleIds: ResolveContextRuleIds? = null
    ): TransactionResult {
        val (_, contractId) = kit.requireConnected()

        requireContractAddress(tokenContract, "tokenContract")
        requireStellarAddress(recipient, "recipient")

        if (recipient == contractId) {
            throw ValidationException.invalidInput(
                "recipient",
                "Cannot transfer to self"
            )
        }

        // Signer validation mirrors validateContractCallArgs and runs here so that a caller
        // error is reported before the decimals resolution round-trip.
        if (selectedSigners.isEmpty()) {
            throw ValidationException.invalidInput(
                "selectedSigners",
                "At least one signer must be provided"
            )
        }

        val resolvedDecimals = decimals
            ?: kit.transactionOperations.fetchTokenDecimals(tokenContract)
        val baseUnits = OZTransactionOperations.amountToBaseUnits(amount, resolvedDecimals)

        val targetArgs = listOf(
            Scv.toAddress(Address(contractId).toSCAddress()),
            Scv.toAddress(Address(recipient).toSCAddress()),
            OZTransactionOperations.baseUnitsToI128ScVal(baseUnits, amount)
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

        val walletSigners = selectedSigners.filterIsInstance<SelectedSigner.Wallet>()
        validateSelectedSigners(selectedSigners, walletSigners)

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

        val latestLedger = kit.sorobanServer.getLatestLedger()
        val expirationLedger = latestLedger.sequence.toUInt() + kit.config.signatureExpirationLedgers.toUInt()

        val contextRules = kit.contextRuleManager.listContextRules()
        val smartAccountSigners = mapToSmartAccountSigners(selectedSigners)

        val signedAuthEntries = mutableListOf<SorobanAuthorizationEntryXdr>()

        for ((entryIndex, entry) in authEntries.withIndex()) {
            // Extract inner address credentials across all address arms; null for Void.
            val credentials = entry.credentials.addressCredentials()
            if (credentials == null) {
                signedAuthEntries.add(entry)
                continue
            }

            val entryAddress = try { Address.fromSCAddress(credentials.address).toString() } catch (_: Exception) { null }
            if (entryAddress != contractId) {
                val matchingWalletSigner = walletSigners.firstOrNull { it.address == entryAddress }
                if (matchingWalletSigner != null) {
                    signedAuthEntries.add(signWalletAddressAuthEntry(entry, matchingWalletSigner, expirationLedger))
                } else {
                    throw TransactionException.signingFailed(
                        "Unsupported auth entry for $entryAddress. " +
                            "Add an external signer for that address or remove it from the transaction."
                    )
                }
                continue
            }

            var signedEntry = cloneEntryWithExpiration(entry, expirationLedger)

            val resolvedContextRuleIds = if (resolveContextRuleIds != null) {
                resolveContextRuleIds(signedEntry, entryIndex)
            } else {
                kit.contextRuleManager.resolveContextRuleIdsForEntry(signedEntry, smartAccountSigners, contextRules)
            }

            val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
                entry = signedEntry,
                expirationLedger = expirationLedger,
                networkPassphrase = kit.config.networkPassphrase
            )
            val authDigest = SmartAccountAuth.buildAuthDigest(payloadHash, resolvedContextRuleIds)

            for ((signerIndex, selectedSigner) in selectedSigners.withIndex()) {
                when (selectedSigner) {
                    is SelectedSigner.Passkey -> {
                        signedEntry = appendPasskeySignature(
                            signedEntry, selectedSigner, signerIndex, selectedSigners.size,
                            authDigest, expirationLedger, resolvedContextRuleIds
                        )
                    }
                    is SelectedSigner.Wallet -> { /* handled in delegated loop below */ }
                    is SelectedSigner.Ed25519 -> {
                        signedEntry = appendEd25519Signature(
                            signedEntry, selectedSigner, authDigest, expirationLedger, resolvedContextRuleIds
                        )
                    }
                }
            }

            val (updatedEntry, delegatedEntries) = appendDelegatedSignerEntries(
                signedEntry, contractId, selectedSigners, expirationLedger, authDigest, resolvedContextRuleIds
            )
            signedAuthEntries.addAll(delegatedEntries)
            signedAuthEntries.add(updatedEntry)
        }

        updatePasskeyLastUsed(selectedSigners)

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
     * Validates all selected signers: per-wallet signing-source reachability via
     * [OZExternalSignerManager.canSignFor], and Ed25519 key size, verifier address format, and
     * signing-source availability via [OZExternalSignerManager.canSignEd25519For].
     */
    private suspend fun validateSelectedSigners(
        selectedSigners: List<SelectedSigner>,
        walletSigners: List<SelectedSigner.Wallet>
    ) {
        for (walletSigner in walletSigners) {
            val canSign = try {
                kit.externalSigners.canSignFor(walletSigner.address)
            } catch (e: Exception) {
                false
            }
            if (!canSign) {
                throw ValidationException.invalidInput(
                    "selectedSigners",
                    "No signer available for address: ${walletSigner.address}. " +
                        "Register an in-memory keypair via kit.externalSigners.addFromSecret(<S-strkey>), " +
                        "or supply a wallet adapter that can sign for this address via config.externalWallet " +
                        "when constructing the kit."
                )
            }
        }

        for (ed25519Signer in selectedSigners.filterIsInstance<SelectedSigner.Ed25519>()) {
            if (ed25519Signer.publicKey.size != SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE) {
                throw ValidationException.invalidInput(
                    "selectedSigners",
                    "Ed25519 signer publicKey must be exactly ${SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE} bytes, " +
                        "got ${ed25519Signer.publicKey.size}"
                )
            }
            if (!StrKey.isValidContract(ed25519Signer.verifierAddress)) {
                throw ValidationException.invalidInput(
                    "selectedSigners",
                    "Ed25519 signer verifierAddress must be a valid contract address (C...), " +
                        "got: ${ed25519Signer.verifierAddress}"
                )
            }
            if (!kit.externalSigners.canSignEd25519For(ed25519Signer.verifierAddress, ed25519Signer.publicKey)) {
                throw ValidationException.invalidInput(
                    "selectedSigners",
                    "No signing source available for Ed25519 signer " +
                        "(verifier=${ed25519Signer.verifierAddress.take(SmartAccountConstants.ADDRESS_PREFIX_LENGTH)}...). " +
                        "Register an in-memory key via kit.externalSigners.addEd25519FromRawKey(...), " +
                        "or supply config.externalEd25519Adapter when constructing the kit."
                )
            }
        }
    }

    /**
     * Converts a list of [SelectedSigner] values to the corresponding [SmartAccountSigner] objects
     * used for context rule resolution.
     */
    private fun mapToSmartAccountSigners(selectedSigners: List<SelectedSigner>): List<SmartAccountSigner> {
        return selectedSigners.map { selectedSigner ->
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
                is SelectedSigner.Wallet -> DelegatedSigner(address = selectedSigner.address)
                is SelectedSigner.Ed25519 -> ExternalSigner.ed25519(
                    verifierAddress = selectedSigner.verifierAddress,
                    publicKey = selectedSigner.publicKey
                )
            }
        }
    }

    /**
     * Appends a passkey (WebAuthn) signature to the auth entry and returns the updated entry.
     */
    private suspend fun appendPasskeySignature(
        entry: SorobanAuthorizationEntryXdr,
        signer: SelectedSigner.Passkey,
        signerIndex: Int,
        totalSigners: Int,
        authDigest: ByteArray,
        expirationLedger: UInt,
        contextRuleIds: List<UInt>
    ): SorobanAuthorizationEntryXdr {
        val webauthnProvider = kit.config.webauthnProvider
            ?: throw ValidationException.invalidInput(
                "webauthnProvider",
                "WebAuthn provider is required for passkey signers but is not configured"
            )

        val allowCredentials = signer.credentialIdBytes?.let { idBytes ->
            listOf(AllowCredential(id = idBytes, transports = signer.transports))
        }

        val authResult = try {
            webauthnProvider.authenticate(authDigest, allowCredentials)
        } catch (e: Exception) {
            throw WebAuthnException.authenticationFailed(
                "WebAuthn authentication failed for passkey signer " +
                    "${signerIndex + 1}/$totalSigners: ${e.message}",
                e
            )
        }

        val compactSig = SmartAccountUtils.normalizeSignature(authResult.signature)
        val webAuthnSig = WebAuthnSignature(
            authenticatorData = authResult.authenticatorData,
            clientData = authResult.clientDataJSON,
            signature = compactSig
        )

        // keyData is guaranteed non-null — validated during mapToSmartAccountSigners.
        val passkeySigner = ExternalSigner(
            verifierAddress = kit.config.webauthnVerifierAddress,
            keyData = signer.keyData!!
        )

        return SmartAccountAuth.signAuthEntry(
            entry = entry,
            signer = passkeySigner,
            signature = webAuthnSig,
            expirationLedger = expirationLedger,
            contextRuleIds = contextRuleIds
        )
    }

    /**
     * Appends an Ed25519 signature to the auth entry after local verification and returns the
     * updated entry.
     */
    private suspend fun appendEd25519Signature(
        entry: SorobanAuthorizationEntryXdr,
        signer: SelectedSigner.Ed25519,
        authDigest: ByteArray,
        expirationLedger: UInt,
        contextRuleIds: List<UInt>
    ): SorobanAuthorizationEntryXdr {
        val rawSignature = kit.externalSigners.signEd25519AuthDigest(
            verifierAddress = signer.verifierAddress,
            publicKey = signer.publicKey,
            authDigest = authDigest
        )

        val isValid = try {
            KeyPair.fromPublicKey(signer.publicKey).verify(authDigest, rawSignature)
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Ed25519 local signature verification failed for verifier " +
                    "${signer.verifierAddress}: ${e.message}",
                e
            )
        }

        if (!isValid) {
            throw TransactionException.signingFailed(
                "Ed25519 signature produced by signing source does not verify for " +
                    "verifier ${signer.verifierAddress} — signing source returned an invalid signature"
            )
        }

        val ed25519Sig = Ed25519Signature(publicKey = signer.publicKey, signature = rawSignature)
        val ed25519ExternalSigner = ExternalSigner.ed25519(
            verifierAddress = signer.verifierAddress,
            publicKey = signer.publicKey
        )

        return SmartAccountAuth.signAuthEntry(
            entry = entry,
            signer = ed25519ExternalSigner,
            signature = ed25519Sig,
            expirationLedger = expirationLedger,
            contextRuleIds = contextRuleIds
        )
    }

    /**
     * Processes each [SelectedSigner.Wallet] in [selectedSigners]: builds a signed delegated auth
     * entry via [Auth.authorizeInvocation] and adds an empty-bytes placeholder to the smart
     * account's signature map on [entry].
     *
     * Returns the updated entry (with all wallet placeholders applied) and the list of signed
     * delegated auth entries that must be appended before the smart account entry.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private suspend fun appendDelegatedSignerEntries(
        entry: SorobanAuthorizationEntryXdr,
        contractId: String,
        selectedSigners: List<SelectedSigner>,
        expirationLedger: UInt,
        authDigest: ByteArray,
        contextRuleIds: List<UInt>
    ): Pair<SorobanAuthorizationEntryXdr, List<SorobanAuthorizationEntryXdr>> {
        var signedEntry = entry
        val delegatedEntries = mutableListOf<SorobanAuthorizationEntryXdr>()

        for (selectedSigner in selectedSigners) {
            if (selectedSigner !is SelectedSigner.Wallet) continue

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

            val authSigner = Auth.Signer { preimage ->
                val writer = XdrWriter()
                preimage.encode(writer)
                val preimageXdr = kotlin.io.encoding.Base64.encode(writer.toByteArray())
                val result = kit.externalSigners.signAuthEntry(selectedSigner.address, preimageXdr)
                val signatureBytes = kotlin.io.encoding.Base64.decode(result.signedAuthEntry)
                Auth.Signature(
                    publicKey = result.signerAddress ?: selectedSigner.address,
                    signature = signatureBytes
                )
            }

            val signedDelegatedEntry = Auth.authorizeInvocation(
                signer = authSigner,
                publicKey = selectedSigner.address,
                validUntilLedgerSeq = expirationLedger.toLong(),
                invocation = checkAuthInvocation,
                network = Network(kit.config.networkPassphrase)
            )
            delegatedEntries.add(signedDelegatedEntry)

            val delegatedSigner = DelegatedSigner(address = selectedSigner.address)
            signedEntry = SmartAccountAuth.addRawSignatureMapEntry(
                entry = signedEntry,
                signerKey = delegatedSigner.toScVal(),
                signatureValue = Scv.toBytes(byteArrayOf()),
                contextRuleIds = contextRuleIds
            )
        }

        return Pair(signedEntry, delegatedEntries)
    }

    /**
     * Updates the last-used timestamp for each passkey signer that participated in the transaction.
     * Best-effort: failures are silently ignored.
     */
    private suspend fun updatePasskeyLastUsed(selectedSigners: List<SelectedSigner>) {
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
        // Clone and set the expiration ledger
        val signedEntry = cloneEntryWithExpiration(entry, expirationLedger)

        val credentials = signedEntry.credentials.addressCredentials()
            ?: throw TransactionException.signingFailed(
                "Expected address-bearing credentials on wallet auth entry for ${walletSigner.address}"
            )

        // Build the preimage from the cloned entry's credentials. The arm selects the
        // preimage type (legacy vs address-bound); construction is delegated to
        // Auth.buildHashIDPreimage so the host-reconstructed hash always matches.
        val networkId = getSha256ForNetworkPassphrase(kit.config.networkPassphrase)
        val preimage = Auth.buildHashIDPreimage(
            credentials = signedEntry.credentials,
            networkId = networkId,
            invocation = signedEntry.rootInvocation
        )

        val writer = XdrWriter()
        preimage.encode(writer)
        val preimageXdr = kotlin.io.encoding.Base64.encode(writer.toByteArray())

        // Request signature from the external signer
        val signResult = kit.externalSigners.signAuthEntry(walletSigner.address, preimageXdr)

        val signatureBytes = kotlin.io.encoding.Base64.decode(signResult.signedAuthEntry)

        // Derive the raw 32-byte Ed25519 public key from the G-address
        val publicKeyBytes = KeyPair.fromAccountId(walletSigner.address).getPublicKey()

        // Build Vec([Map({public_key: Bytes, signature: Bytes})]) — standard Ed25519 auth format
        val sigMap = linkedMapOf(
            Scv.toSymbol("public_key") to Scv.toBytes(publicKeyBytes),
            Scv.toSymbol("signature") to Scv.toBytes(signatureBytes)
        )
        val signatureScVal = Scv.toVec(listOf(Scv.toMap(sigMap)))

        // Set the signature on the cloned entry's credentials, preserving the arm.
        val updatedCredentials = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = credentials.signatureExpirationLedger,
            signature = signatureScVal
        )

        return SorobanAuthorizationEntryXdr(
            credentials = signedEntry.credentials.withUpdatedAddressCredentials(updatedCredentials),
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

        // Set expiration on the cloned entry, preserving the credential arm.
        // Source-account (Void) entries carry no expiration and are returned as-is.
        val credentials = cloned.credentials.addressCredentials()
            ?: return cloned

        val updated = SorobanAddressCredentialsXdr(
            address = credentials.address,
            nonce = credentials.nonce,
            signatureExpirationLedger = Uint32Xdr(expirationLedger),
            signature = credentials.signature
        )

        return SorobanAuthorizationEntryXdr(
            credentials = cloned.credentials.withUpdatedAddressCredentials(updated),
            rootInvocation = cloned.rootInvocation
        )
    }
}
