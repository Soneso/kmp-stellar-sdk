//
//  OZMultiSignerManager.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.MemoNone
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
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
    ) : SelectedSigner()

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
 * OZMultiSignerManager provides functionality for executing multi-signature token transfers
 * and collecting signatures from both passkey and external wallet signers.
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
     * @param amount The amount to transfer in XLM units
     * @param selectedSigners All signers that must sign, in collection order.
     *   An empty list means no signatures are collected (only valid for read-only simulation).
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
        resolveContextRuleIds: ResolveContextRuleIds? = null
    ): TransactionResult {
        // STEP 1: Validate inputs (same as single-signer transfer)
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

        // At least one signer is required
        if (selectedSigners.isEmpty()) {
            throw ValidationException.invalidInput(
                "selectedSigners",
                "At least one signer must be provided"
            )
        }

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

        // STEP 2: Build host function for token transfer
        val stroops = Util.amountToStroops(amount)

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

        // STEP 3: Simulate to get auth entries
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

        val authEntries = simulation.results?.firstOrNull()?.parseAuth()
            ?: throw TransactionException.simulationFailed("No auth entries returned from simulation")

        // STEP 4: Get current ledger sequence
        val latestLedger = kit.sorobanServer.getLatestLedger()

        // STEP 5: Calculate expiration
        val expirationLedger = latestLedger.sequence.toUInt() +
                OZConstants.AUTH_ENTRY_EXPIRATION_BUFFER.toUInt()

        // Pre-fetch context rules ONCE for all auth entries (avoids N+1 RPC calls per entry)
        val contextRules = kit.contextRuleManager.listContextRules()

        // STEP 6: Sign auth entries.
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
                signedAuthEntries.add(entry)
                continue
            }

            // Clone the entry and set the expiration ledger before signing
            var signedEntry = cloneEntryWithExpiration(entry, expirationLedger)

            // Build the list of SmartAccountSigner objects from selectedSigners for rule resolution.
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
                        ) as SmartAccountSigner
                    }
                    is SelectedSigner.Wallet -> {
                        DelegatedSigner(address = selectedSigner.address) as SmartAccountSigner
                    }
                }
            }

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

            // STEP 6a: Sign with each passkey signer using SmartAccountAuth.signAuthEntry().
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

                        // Use the caller-provided keyData. The client already has this
                        // from its own rule discovery and passes it via SelectedSigner.Passkey.
                        val keyData = selectedSigner.keyData
                            ?: throw ValidationException.invalidInput(
                                "selectedSigners",
                                "keyData is required for passkey signers. " +
                                    "Populate SelectedSigner.Passkey.keyData from the signer data " +
                                    "obtained during context rule discovery."
                            )

                        val passkeySigner = ExternalSigner(
                            verifierAddress = kit.config.webauthnVerifierAddress,
                            keyData = keyData
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

            // STEP 6b: Add delegated signer auth entries and placeholders.
            // Each delegated signer gets:
            // - Its own signed auth entry (built and signed via Auth.authorizeInvocation)
            // - An empty-bytes placeholder in the smart account's signature map
            for (selectedSigner in selectedSigners) {
                if (selectedSigner !is SelectedSigner.Wallet) continue

                val externalWallet = kit.externalWallet
                    ?: throw ValidationException.invalidInput(
                        "externalWallet",
                        "External wallet adapter is required for wallet signers"
                    )

                // Build the invocation targeting the smart account's __check_auth.
                // The auth digest (payloadHash bound to contextRuleIds) is passed as argument,
                // matching what the verifier contract expects in v0.7.0+.
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

                    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
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

        // STEP 7: Re-simulate with signed auth entries.
        // Use a fresh deployer account to avoid sequence number double-increment.
        val refetchedDeployerAccount = kit.sorobanServer.getAccount(deployer.getAccountId())

        val signedOperation = InvokeHostFunctionOperation(hostFunction, signedAuthEntries)
        val signedTransaction = TransactionBuilder(
            refetchedDeployerAccount,
            Network(kit.config.networkPassphrase)
        )
            .setBaseFee(100)
            .addOperation(signedOperation)
            .addMemo(MemoNone)
            .setTimeout(300)
            .build()

        val resignedSimulation = kit.sorobanServer.simulateTransaction(signedTransaction)

        if (resignedSimulation.error != null) {
            throw TransactionException.simulationFailed("Re-simulation error: ${resignedSimulation.error}")
        }

        // STEP 8: Assemble and submit via the same Mode 1 / Mode 2 routing as single-signer.
        // Mode 1 (default): relayer receives hostFunction + authEntries and builds the envelope.
        // Mode 2 (fallback): used only when source_account auth entries are present.
        // prepareTransaction applies resource fees, footprint, and soroban data from simulation.
        return kit.transactionOperations.submitMultiSignerTransaction(
            hostFunction = hostFunction,
            signedAuthEntries = signedAuthEntries,
            signedTransaction = signedTransaction,
            simulation = resignedSimulation
        )
    }

    // MARK: - Private Helpers

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

