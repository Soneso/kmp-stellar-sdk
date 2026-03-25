//
//  OZMultiSignerManager.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.currentTimeMillis
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Memo
import com.soneso.stellar.sdk.MemoNone
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SCVecXdr
import com.soneso.stellar.sdk.xdr.SorobanAddressCredentialsXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.XdrWriter

// MARK: - Available Signer Types

/**
 * Represents a signer available for multi-signature operations.
 *
 * Contains information about whether the signer can currently sign and where
 * the signer originates from (passkey or external wallet).
 *
 * @property signer The smart account signer
 * @property canSign Whether this signer can currently sign transactions
 * @property source The source of this signer
 */
data class AvailableSigner(
    val signer: SmartAccountSigner,
    val canSign: Boolean,
    val source: SignerSource
)

/**
 * The source of a signer for multi-signature operations.
 */
enum class SignerSource {
    /**
     * Passkey signer (WebAuthn credential).
     */
    PASSKEY,

    /**
     * External wallet signer (e.g., Freighter, Albedo).
     */
    EXTERNAL_WALLET
}

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
     * @property credentialId Base64URL-encoded credential ID to request from the
     *   authenticator. If null, the OS shows the passkey picker without pre-selecting
     *   a specific credential.
     */
    data class Passkey(val credentialId: String? = null) : SelectedSigner()

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
 * OZMultiSignerManager provides functionality for:
 * - Querying available signers from context rules
 * - Executing multi-signature token transfers
 * - Collecting signatures from both passkey and external wallets
 *
 * Multi-signature transactions require collecting signatures from multiple signers
 * sequentially to enable fail-fast behavior on user cancellation.
 *
 * Signatures are collected in the order the caller supplies them via [SelectedSigner]:
 * - Each [SelectedSigner.Passkey] triggers one OS WebAuthn authentication prompt.
 * - Each [SelectedSigner.Wallet] signs via the configured external wallet adapter.
 *
 * The connected passkey is NOT added implicitly. If it should sign, include
 * [SelectedSigner.Passkey] (optionally with its credential ID) in the list.
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
 * // Get available signers
 * val signers = multiSigner.getAvailableSigners()
 * println("Available signers: ${signers.size}")
 *
 * // Execute multi-signature transfer — caller lists ALL signers explicitly
 * val result = multiSigner.multiSignerTransfer(
 *     tokenContract = "CBCD...",
 *     recipient = "GA7Q...",
 *     amount = 100.0,
 *     selectedSigners = listOf(
 *         SelectedSigner.Passkey(),           // connected passkey (OS picker)
 *         SelectedSigner.Wallet("GA7Q...")    // delegated wallet signer
 *     )
 * )
 * println("Transfer ${if (result.success) "succeeded" else "failed"}")
 * ```
 */
class OZMultiSignerManager internal constructor(
    private val kit: OZSmartAccountKit
) {
    // MARK: - Get Available Signers

    /**
     * Retrieves the list of available signers for the connected smart account.
     *
     * Queries the smart account contract's context rules and determines which signers
     * can currently sign transactions. A signer is marked as "can sign" if:
     *
     * - External signer with WebAuthn verifier: canSign = true if credential ID matches connected wallet
     * - Delegated signer: canSign = true if externalWallet.canSignFor(address) returns true
     * - External signer with other verifier: canSign = false (not yet supported)
     *
     * @return List of available signers with their signing capabilities
     * @throws SmartAccountException if not connected or if contract query fails
     *
     * Example:
     * ```kotlin
     * val signers = multiSigner.getAvailableSigners()
     * for (availableSigner in signers) {
     *     if (availableSigner.canSign) {
     *         println("Can sign with: ${availableSigner.source}")
     *     }
     * }
     * ```
     */
    suspend fun getAvailableSigners(): List<AvailableSigner> {
        // STEP 1: Require connected state
        val (credentialId, contractId) = kit.requireConnected()

        // STEP 2: Query default context rules from contract
        val contextTypeScVal = Scv.toVec(listOf(Scv.toSymbol("Default")))

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("get_context_rules"),
            args = listOf(contextTypeScVal)
        )
        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        val resultScVal: SCValXdr
        try {
            resultScVal = SmartAccountSharedUtils.simulateAndExtractResult(
                hostFunction = hostFunction,
                kit = kit
            )
        } catch (e: Exception) {
            // If query fails (e.g., no rules configured), return empty list
            return emptyList()
        }

        // STEP 3: Parse signers from context rules response
        val parsedSigners = parseSignersFromContextRulesResponse(resultScVal)

        // STEP 4: Determine canSign for each signer
        val availableSigners = mutableListOf<AvailableSigner>()

        for (parsed in parsedSigners) {
            when (parsed.tag) {
                "Delegated" -> {
                    val canSign: Boolean = if (kit.externalWallet != null) {
                        try {
                            kit.externalWallet.canSignFor(parsed.address)
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        false
                    }

                    val signer = DelegatedSigner(address = parsed.address)
                    availableSigners.add(
                        AvailableSigner(
                            signer = signer,
                            canSign = canSign,
                            source = SignerSource.EXTERNAL_WALLET
                        )
                    )
                }

                "External" -> {
                    val keyBytes = parsed.keyBytes ?: continue
                    val signer = ExternalSigner(
                        verifierAddress = parsed.address,
                        keyData = keyBytes
                    )

                    // Determine if this is a WebAuthn signer we can sign with
                    val canSign: Boolean = if (parsed.address == kit.config.webauthnVerifierAddress) {
                        val signerCredentialId = parsed.credentialId
                        if (signerCredentialId != null) {
                            val signerCredentialIdEncoded = SmartAccountSharedUtils.base64urlEncode(signerCredentialId)
                            signerCredentialIdEncoded == credentialId
                        } else {
                            false
                        }
                    } else {
                        false
                    }

                    availableSigners.add(
                        AvailableSigner(
                            signer = signer,
                            canSign = canSign,
                            source = SignerSource.PASSKEY
                        )
                    )
                }

                else -> continue
            }
        }

        return availableSigners
    }

    // MARK: - Signer Parsing (Internal for testing)

    /**
     * Parsed signer extracted from on-chain context rules.
     *
     * @property tag "Delegated" or "External"
     * @property address G-address for Delegated, C-address (verifier) for External
     * @property keyBytes key_data for External signers, null for Delegated
     */
    internal data class ParsedContractSigner(
        val tag: String,
        val address: String,
        val keyBytes: ByteArray?
    ) {
        /**
         * For WebAuthn External signers, extracts the credential ID from keyBytes.
         * Key data format: publicKey (65 bytes) + credentialId (variable).
         * Returns null if not a WebAuthn signer (keyBytes <= 65 bytes).
         */
        val credentialId: ByteArray?
            get() {
                val keyData = keyBytes ?: return null
                return if (keyData.size > SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
                    keyData.copyOfRange(
                        SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE,
                        keyData.size
                    )
                } else {
                    null
                }
            }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as ParsedContractSigner

            if (tag != other.tag) return false
            if (address != other.address) return false
            if (keyBytes != null) {
                if (other.keyBytes == null) return false
                if (!keyBytes.contentEquals(other.keyBytes)) return false
            } else if (other.keyBytes != null) return false

            return true
        }

        override fun hashCode(): Int {
            var result = tag.hashCode()
            result = 31 * result + address.hashCode()
            result = 31 * result + (keyBytes?.contentHashCode() ?: 0)
            return result
        }
    }

    /**
     * Parses unique signers from a `get_context_rules` contract response.
     *
     * The response ScVal is expected to be `Vec<ContextRule>` where each ContextRule
     * is a Map (Soroban struct) with alphabetically-sorted Symbol keys.
     * The "signers" field contains `Vec<Signer>` where each Signer is:
     * - `Vec([Symbol("Delegated"), Address(addr)])`
     * - `Vec([Symbol("External"), Address(verifier), Bytes(keyData)])`
     *
     * @param resultScVal The raw ScVal response from `get_context_rules`
     * @return List of unique parsed signers, deduplicated by composite key
     */
    internal fun parseSignersFromContextRulesResponse(
        resultScVal: SCValXdr
    ): List<ParsedContractSigner> {
        // Result should be Vec<ContextRule>
        val rules = (resultScVal as? SCValXdr.Vec)?.value?.value ?: return emptyList()

        val signerKeys = mutableSetOf<String>()
        val uniqueSigners = mutableListOf<ParsedContractSigner>()

        for (ruleScVal in rules) {
            // Each rule is a Map (struct)
            val fields = (ruleScVal as? SCValXdr.Map)?.value?.value ?: continue

            // Find the "signers" field
            for (field in fields) {
                val key = (field.key as? SCValXdr.Sym)?.value?.value ?: continue
                if (key != "signers") continue

                // signers field value is Vec<Signer>
                val signerVec = (field.`val` as? SCValXdr.Vec)?.value?.value ?: break

                for (signerScVal in signerVec) {
                    // Each signer is Vec([Symbol(tag), ...])
                    val signerParts = (signerScVal as? SCValXdr.Vec)?.value?.value
                    if (signerParts.isNullOrEmpty()) continue

                    val tag = (signerParts[0] as? SCValXdr.Sym)?.value?.value ?: continue

                    val parsed: ParsedContractSigner
                    val signerKey: String

                    when (tag) {
                        "Delegated" -> {
                            // Vec([Symbol("Delegated"), Address(addr)])
                            if (signerParts.size < 2) continue
                            val addr = (signerParts[1] as? SCValXdr.Address)?.value ?: continue
                            val address = SmartAccountSharedUtils.extractAddressString(addr) ?: continue

                            signerKey = "delegated:$address"
                            parsed = ParsedContractSigner(
                                tag = "Delegated",
                                address = address,
                                keyBytes = null
                            )
                        }

                        "External" -> {
                            // Vec([Symbol("External"), Address(verifier), Bytes(keyData)])
                            if (signerParts.size < 3) continue
                            val addr = (signerParts[1] as? SCValXdr.Address)?.value ?: continue
                            val address = SmartAccountSharedUtils.extractAddressString(addr) ?: continue

                            val bytes = (signerParts[2] as? SCValXdr.Bytes)?.value?.value ?: continue

                            signerKey = "external:$address:${bytes.toHexString()}"
                            parsed = ParsedContractSigner(
                                tag = "External",
                                address = address,
                                keyBytes = bytes
                            )
                        }

                        else -> continue
                    }

                    if (!signerKeys.contains(signerKey)) {
                        signerKeys.add(signerKey)
                        uniqueSigners.add(parsed)
                    }
                }
                break
            }
        }

        return uniqueSigners
    }

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
     * @return TransactionResult indicating success or failure
     * @throws SmartAccountException if validation fails, signing fails, or submission fails
     *
     * Example:
     * ```kotlin
     * val result = multiSigner.multiSignerTransfer(
     *     tokenContract = nativeTokenAddress,
     *     recipient = "GBXYZ...",
     *     amount = 50.0,
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
        amount: Double,
        selectedSigners: List<SelectedSigner>
    ): TransactionResult {
        // STEP 1: Validate inputs (same as single-signer transfer)
        val (credentialId, contractId) = kit.requireConnected()

        // Validate token contract address (must be C-address)
        if (!tokenContract.startsWith("C") || tokenContract.length != 56) {
            throw ValidationException.invalidAddress(
                "Token contract must be a valid C-address, got: $tokenContract"
            )
        }

        // Validate recipient address (G or C)
        if ((!recipient.startsWith("G") && !recipient.startsWith("C")) || recipient.length != 56) {
            throw ValidationException.invalidAddress(
                "Recipient must be a valid G-address or C-address, got: $recipient"
            )
        }

        // Validate amount
        if (amount <= 0) {
            throw ValidationException.invalidInput(
                "amount",
                "Amount must be greater than zero, got: $amount"
            )
        }

        // Prevent self-transfer
        if (recipient == contractId) {
            throw ValidationException.invalidInput(
                "recipient",
                "Cannot transfer to self"
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
        val stroops = SmartAccountSharedUtils.amountToStroops(amount)

        val fromAddress = Address(contractId).toSCAddress()
        val toAddress: SCAddressXdr = if (recipient.startsWith("G")) {
            val keyPair = KeyPair.fromAccountId(recipient)
            SCAddressXdr.AccountId(keyPair.getXdrAccountId())
        } else {
            Address(recipient).toSCAddress()
        }

        val amountScVal = SmartAccountSharedUtils.stroopsToI128ScVal(stroops)

        val functionArgs = listOf(
            SCValXdr.Address(fromAddress),
            SCValXdr.Address(toAddress),
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
                SmartAccountConstants.AUTH_ENTRY_EXPIRATION_BUFFER.toUInt()

        // STEP 6: Decode auth entries and collect signatures
        val signedAuthEntries = mutableListOf<SorobanAuthorizationEntryXdr>()

        for (entry in authEntries) {
            // Check if this entry's credentials match our contract
            val credentials = (entry.credentials as? SorobanCredentialsXdr.Address)?.value
            if (credentials == null) {
                // Not an address credential, pass through unchanged
                signedAuthEntries.add(entry)
                continue
            }

            val entryAddress = SmartAccountSharedUtils.extractAddressString(credentials.address)
            if (entryAddress != contractId) {
                // Not our entry, pass through unchanged
                signedAuthEntries.add(entry)
                continue
            }

            // STEP 6a: Build payload hash
            val payloadHash = SmartAccountAuth.buildAuthPayloadHash(
                entry = entry,
                expirationLedger = expirationLedger,
                networkPassphrase = kit.config.networkPassphrase
            )

            // STEP 6b: Collect signatures from all selected signers in order
            val collectedSignatures = mutableListOf<Triple<SmartAccountSigner, SCValXdr, Boolean>>()

            // Lazily fetch available signers only when needed for passkey key-data lookup
            var cachedAvailableSigners: List<AvailableSigner>? = null
            suspend fun getAvailableSignersCached(): List<AvailableSigner> {
                if (cachedAvailableSigners == null) {
                    cachedAvailableSigners = try {
                        getAvailableSigners()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
                return cachedAvailableSigners!!
            }

            for ((signerIndex, selectedSigner) in selectedSigners.withIndex()) {
                when (selectedSigner) {
                    is SelectedSigner.Passkey -> {
                        val webauthnProvider = kit.config.webauthnProvider
                            ?: throw ValidationException.invalidInput(
                                "webauthnProvider",
                                "WebAuthn provider is required for passkey signers but is not configured"
                            )

                        // Trigger WebAuthn authentication (one OS prompt per passkey signer)
                        val authResult = try {
                            webauthnProvider.authenticate(payloadHash)
                        } catch (e: Exception) {
                            throw WebAuthnException.authenticationFailed(
                                "WebAuthn authentication failed for passkey signer ${signerIndex + 1}/${selectedSigners.size}: ${e.message}",
                                e
                            )
                        }

                        // Normalize signature (DER to compact, low-S)
                        val normalizedSignature = SmartAccountUtils.normalizeSignature(authResult.signature)

                        val webAuthnSignature = WebAuthnSignature(
                            authenticatorData = authResult.authenticatorData,
                            clientData = authResult.clientDataJSON,
                            signature = normalizedSignature
                        )
                        val webAuthnSignatureScVal = webAuthnSignature.toScVal()

                        // Resolve the credential ID to use for key-data lookup.
                        // If the caller specified a credentialId, use it; otherwise fall back
                        // to the connected credential ID stored in kit state.
                        val resolvedCredentialId = selectedSigner.credentialId ?: credentialId

                        val credentialIdBytes = SmartAccountSharedUtils.base64urlDecode(resolvedCredentialId)
                            ?: throw ValidationException.invalidInput(
                                "selectedSigners[$signerIndex].credentialId",
                                "Failed to decode credential ID"
                            )

                        // Resolve key data: local storage first, then on-chain context rules
                        val keyData: ByteArray
                        val storage = kit.getStorage()
                        val storedCredential = storage.get(resolvedCredentialId)
                        if (storedCredential != null) {
                            keyData = storedCredential.publicKey + credentialIdBytes
                        } else {
                            val availableSigners = getAvailableSignersCached()
                            val matchingSigner = availableSigners.firstOrNull { signer ->
                                signer.source == SignerSource.PASSKEY &&
                                signer.signer is ExternalSigner &&
                                (signer.signer as ExternalSigner).keyData.size > SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE &&
                                (signer.signer as ExternalSigner).keyData.copyOfRange(
                                    SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE,
                                    (signer.signer as ExternalSigner).keyData.size
                                ).contentEquals(credentialIdBytes)
                            }
                            keyData = (matchingSigner?.signer as? ExternalSigner)?.keyData
                                ?: throw CredentialException.notFound(
                                    "No signer found for credential ID at index $signerIndex: $resolvedCredentialId " +
                                        "(not in local storage or on-chain context rules)"
                                )
                        }

                        val passkeySigner = ExternalSigner(
                            verifierAddress = kit.config.webauthnVerifierAddress,
                            keyData = keyData
                        )

                        collectedSignatures.add(Triple(passkeySigner, webAuthnSignatureScVal, false))
                    }

                    is SelectedSigner.Wallet -> {
                        val externalWallet = kit.externalWallet
                            ?: throw ValidationException.invalidInput(
                                "externalWallet",
                                "External wallet adapter is required for wallet signers"
                            )

                        // Build delegated signer auth entry
                        val delegatedAuthEntry = buildDelegatedSignerAuthEntry(
                            payloadHash = payloadHash,
                            delegatedAddress = selectedSigner.address,
                            expirationLedger = expirationLedger
                        )

                        // XDR encode the auth entry for signing
                        val authEntryXdr = try {
                            val writer = XdrWriter()
                            delegatedAuthEntry.encode(writer)
                            writer.toByteArray()
                        } catch (e: Exception) {
                            throw TransactionException.signingFailed(
                                "Failed to XDR encode delegated auth entry",
                                e
                            )
                        }

                        // Convert to base64 for external wallet interface
                        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                        val authEntryXdrBase64 = kotlin.io.encoding.Base64.encode(authEntryXdr)

                        // Request signature from external wallet
                        val signResult: SignAuthEntryResult = try {
                            externalWallet.signAuthEntry(
                                authEntryXdrBase64,
                                SignAuthEntryOptions(
                                    networkPassphrase = kit.config.networkPassphrase,
                                    address = selectedSigner.address
                                )
                            )
                        } catch (e: Exception) {
                            throw TransactionException.signingFailed(
                                "External wallet signing failed: ${e.message}",
                                e
                            )
                        }

                        // Decode from base64
                        @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
                        val signedAuthEntryXdr = try {
                            kotlin.io.encoding.Base64.decode(signResult.signedAuthEntry)
                        } catch (e: Exception) {
                            throw TransactionException.signingFailed(
                                "Failed to decode base64 from external wallet",
                                e
                            )
                        }

                        // Decode the signed auth entry
                        val signedDelegatedAuthEntry = try {
                            val reader = com.soneso.stellar.sdk.xdr.XdrReader(signedAuthEntryXdr)
                            SorobanAuthorizationEntryXdr.decode(reader)
                        } catch (e: Exception) {
                            throw TransactionException.signingFailed(
                                "Failed to decode signed auth entry from external wallet",
                                e
                            )
                        }

                        // Add the signed delegated auth entry to our list
                        signedAuthEntries.add(signedDelegatedAuthEntry)

                        // Add placeholder to the smart account's signature map
                        val delegatedSigner = DelegatedSigner(address = selectedSigner.address)
                        val placeholderSignature = SCValXdr.Bytes(com.soneso.stellar.sdk.xdr.SCBytesXdr(byteArrayOf()))
                        collectedSignatures.add(Triple(delegatedSigner, placeholderSignature, true))
                    }
                }
            }

            // STEP 6d: Build signature map with ALL collected signatures
            val mapEntries = mutableListOf<SCMapEntryXdr>()

            for ((signer, signatureScVal, isPlaceholder) in collectedSignatures) {
                val signerKey = signer.toScVal()

                val signatureValue: SCValXdr = if (isPlaceholder) {
                    // Delegated signer placeholder: use the ScVal directly (no double-encoding)
                    signatureScVal
                } else {
                    // Real signature (e.g., WebAuthn): XDR-encode the signature ScVal
                    // and wrap it in bytes
                    val sigXdrBytes = try {
                        val writer = XdrWriter()
                        signatureScVal.encode(writer)
                        writer.toByteArray()
                    } catch (e: Exception) {
                        throw TransactionException.signingFailed(
                            "Failed to XDR encode signature ScVal",
                            e
                        )
                    }
                    SCValXdr.Bytes(com.soneso.stellar.sdk.xdr.SCBytesXdr(sigXdrBytes))
                }

                val mapEntry = SCMapEntryXdr(key = signerKey, `val` = signatureValue)
                mapEntries.add(mapEntry)
            }

            // STEP 6d: Sort map entries by ascending lowercase hex of XDR-encoded keys
            mapEntries.sortBy { entry ->
                try {
                    val writer = XdrWriter()
                    entry.key.encode(writer)
                    val keyBytes = writer.toByteArray()
                    keyBytes.toHexString()
                } catch (e: Exception) {
                    ""
                }
            }

            // STEP 6e: Set credentials.signature
            var updatedCredentials = SorobanAddressCredentialsXdr(
                address = credentials.address,
                nonce = credentials.nonce,
                signatureExpirationLedger = Uint32Xdr(expirationLedger),
                signature = credentials.signature
            )

            val signatureMap = SCValXdr.Map(SCMapXdr(mapEntries))
            updatedCredentials = SorobanAddressCredentialsXdr(
                address = updatedCredentials.address,
                nonce = updatedCredentials.nonce,
                signatureExpirationLedger = updatedCredentials.signatureExpirationLedger,
                signature = SCValXdr.Vec(SCVecXdr(listOf(signatureMap)))
            )

            val signedEntry = SorobanAuthorizationEntryXdr(
                credentials = SorobanCredentialsXdr.Address(updatedCredentials),
                rootInvocation = entry.rootInvocation
            )

            signedAuthEntries.add(signedEntry)
        }

        // STEP 7: Re-simulate with signed auth entries
        // Refetch deployer account to avoid sequence number double-increment
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

        val transactionData = resignedSimulation.parseTransactionData()
            ?: throw TransactionException.submissionFailed(
                "Failed to get transaction data from re-simulation"
            )

        val minResourceFee = resignedSimulation.minResourceFee
            ?: throw TransactionException.submissionFailed(
                "Failed to get min resource fee from re-simulation"
            )

        // Rebuild final transaction
        val finalTransaction = TransactionBuilder(
            refetchedDeployerAccount,
            Network(kit.config.networkPassphrase)
        )
            .setBaseFee(100 + minResourceFee)
            .addOperation(signedOperation)
            .addMemo(MemoNone)
            .setTimeout(300)
            .setSorobanData(transactionData)
            .build()

        // STEP 8: Submit the assembled transaction
        return kit.transactionOperations.submitAssembledTransaction(finalTransaction)
    }

    // MARK: - Private Helpers

    /**
     * Builds a delegated signer auth entry for external wallet signing.
     *
     * Delegated signers produce their own auth entries with Address credentials
     * that invoke the smart account's __check_auth function.
     *
     * @param payloadHash The payload hash to authorize
     * @param delegatedAddress The delegated signer's Stellar address
     * @param expirationLedger The ledger number at which the signature expires
     * @return The auth entry for the delegated signer
     * @throws SmartAccountException if construction fails
     */
    private suspend fun buildDelegatedSignerAuthEntry(
        payloadHash: ByteArray,
        delegatedAddress: String,
        expirationLedger: UInt
    ): SorobanAuthorizationEntryXdr {
        val (_, contractId) = kit.requireConnected()

        // Build the delegated signer's Address credentials
        val delegatedScAddress = try {
            val keyPair = KeyPair.fromAccountId(delegatedAddress)
            SCAddressXdr.AccountId(keyPair.getXdrAccountId())
        } catch (e: Exception) {
            throw ValidationException.invalidAddress("Invalid delegated address: $delegatedAddress", e)
        }

        // Use timestamp-based nonce
        val nonce = (currentTimeMillis())

        val addressCredentials = SorobanAddressCredentialsXdr(
            address = delegatedScAddress,
            nonce = Int64Xdr(nonce),
            signatureExpirationLedger = Uint32Xdr(expirationLedger),
            signature = SCValXdr.Vec(SCVecXdr(emptyList())) // Will be filled by external wallet
        )

        // Build the root invocation targeting smart account's __check_auth
        val smartAccountAddress = Address(contractId).toSCAddress()

        val contractFn = SorobanAuthorizedFunctionXdr.ContractFn(
            InvokeContractArgsXdr(
                contractAddress = smartAccountAddress,
                functionName = SCSymbolXdr("__check_auth"),
                args = listOf(SCValXdr.Bytes(com.soneso.stellar.sdk.xdr.SCBytesXdr(payloadHash)))
            )
        )

        val rootInvocation = SorobanAuthorizedInvocationXdr(
            function = contractFn,
            subInvocations = emptyList()
        )

        // Build the auth entry
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(addressCredentials),
            rootInvocation = rootInvocation
        )
    }
}

