//
//  OZSignerManager.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.crypto.getEd25519Crypto
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr

/**
 * Result of the [OZSignerManager.addNewPasskeySigner] operation.
 *
 * Contains the WebAuthn credential information from the registration ceremony
 * and the on-chain transaction result from adding the passkey signer to the
 * smart account contract.
 *
 * @property credentialId Base64URL-encoded credential ID (no padding)
 * @property publicKey 65-byte uncompressed secp256r1 public key (0x04 prefix + X + Y)
 * @property transactionResult Result from the on-chain signer addition transaction
 */
data class AddPasskeySignerResult(
    val credentialId: String,
    val publicKey: ByteArray,
    val transactionResult: TransactionResult
) {
    /**
     * Custom equals implementation that properly compares ByteArray fields.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AddPasskeySignerResult

        if (credentialId != other.credentialId) return false
        if (!Util.constantTimeEquals(publicKey, other.publicKey)) return false
        if (transactionResult != other.transactionResult) return false

        return true
    }

    /**
     * Custom hashCode implementation that properly handles ByteArray fields.
     */
    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + transactionResult.hashCode()
        return result
    }
}

/**
 * Manager for smart account signer operations.
 *
 * OZSignerManager provides high-level operations for managing signers on a smart account.
 * It handles adding and removing different types of signers (passkeys, delegated accounts,
 * Ed25519 keys) to context rules, with automatic validation and transaction building.
 *
 * Signer types supported:
 * - WebAuthn passkeys: secp256r1 signature verification via WebAuthn verifier contract
 * - Delegated signers: Stellar accounts or contracts using built-in require_auth verification
 * - Ed25519 signers: Traditional Ed25519 keys via Ed25519 verifier contract
 *
 * Each context rule can have up to 15 signers. Signers are identified by their on-chain
 * representation (address for delegated, verifier+key for external).
 *
 * All state-changing methods accept an optional [selectedSigners] parameter for multi-signer
 * authorization. When empty (default), the operation uses single-signer auth with the
 * connected passkey. When non-empty, signatures are collected from all listed signers
 * via [OZMultiSignerManager.submitWithMultipleSigners].
 *
 * Example usage:
 * ```kotlin
 * val kit = OZSmartAccountKit.create(config)
 * val signerManager = kit.signerManager
 *
 * // Add a passkey signer to the Default context rule (single-signer)
 * val result = signerManager.addPasskey(
 *     contextRuleId = 0u,
 *     publicKey = secp256r1PublicKey,       // ByteArray, 65 bytes
 *     credentialId = credentialIdBytes      // ByteArray, raw WebAuthn credential ID
 * )
 * println("Signer added: ${result.success}")
 *
 * // Add a delegated account signer (multi-signer)
 * val delegatedResult = signerManager.addDelegated(
 *     contextRuleId = 0u,
 *     address = "GA7QYNF7...",
 *     selectedSigners = listOf(
 *         SelectedSigner.Passkey(credentialId = cred1, credentialIdBytes = cred1Bytes, keyData = key1),
 *         SelectedSigner.Wallet("GA7Q...")
 *     )
 * )
 * ```
 *
 * Thread Safety:
 * This class holds no mutable state. Thread safety of operations depends on the
 * parent [OZSmartAccountKit] instance which synchronizes connection state via Mutex.
 *
 * @property kit Reference to the parent OZSmartAccountKit instance
 */
class OZSignerManager internal constructor(
    private val kit: OZSmartAccountKit
) {
    // MARK: - Add Signers

    /**
     * Registers a new WebAuthn passkey and adds it as a signer to a context rule.
     *
     * Performs the full end-to-end flow of creating a new passkey via the platform's
     * WebAuthn API, persisting the credential locally, and adding it as a signer on
     * the smart account contract. This is the high-level method for adding passkey
     * signers; use [addPasskey] if you already have the public key and credential ID.
     *
     * Flow:
     * 1. Validates that a wallet is connected and a WebAuthnProvider is configured
     * 2. Generates cryptographically secure random challenge and user ID (32 bytes each)
     * 3. Triggers the platform WebAuthn registration ceremony (biometric prompt)
     * 4. Base64URL-encodes the credential ID for storage
     * 5. Saves the credential locally via [OZCredentialManager.createPendingCredential]
     * 6. Emits a [SmartAccountEvent.CredentialCreated] event
     * 7. Adds the passkey signer on-chain via [addPasskey]
     *
     * The on-chain addition requires authorization from an existing signer on the
     * specified context rule. The user will be prompted for biometric authentication
     * twice: once for the new passkey registration and once for the existing signer
     * to authorize the on-chain transaction.
     *
     * @param contextRuleId The context rule ID to add the signer to (e.g., 0 for Default)
     * @param userName User-friendly name for the new passkey (displayed by the authenticator)
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return [AddPasskeySignerResult] containing the credential ID, public key, and transaction result
     * @throws WebAuthnException.NotSupported if no WebAuthnProvider is configured
     * @throws WalletException.NotConnected if no wallet is connected
     * @throws WebAuthnException if the WebAuthn registration ceremony fails or the user cancels
     * @throws SmartAccountException if credential storage or on-chain signer addition fails
     *
     * Example:
     * ```kotlin
     * val result = signerManager.addNewPasskeySigner(
     *     contextRuleId = 0u,
     *     userName = "Recovery Passkey"
     * )
     *
     * println("Credential ID: ${result.credentialId}")
     * println("On-chain result: ${result.transactionResult.success}")
     * ```
     */
    suspend fun addNewPasskeySigner(
        contextRuleId: UInt,
        userName: String,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): AddPasskeySignerResult {
        // Step 1: Validate wallet is connected
        val (_, contractId) = kit.requireConnected()

        // Step 2: Get WebAuthn provider, throw if not configured
        val webauthnProvider = kit.config.webauthnProvider
            ?: throw WebAuthnException.notSupported(
                "No WebAuthnProvider configured. Set webauthnProvider in config before calling addNewPasskeySigner()."
            )

        // Step 3: Generate cryptographically secure random challenge and user ID (32 bytes each)
        val crypto = getEd25519Crypto()
        val challengeData = crypto.generatePrivateKey()
        val userIdData = crypto.generatePrivateKey()

        // Step 4: Trigger WebAuthn registration ceremony
        val registrationResult = try {
            webauthnProvider.register(
                challenge = challengeData,
                userId = userIdData,
                userName = userName
            )
        } catch (e: Exception) {
            throw WebAuthnException.registrationFailed(
                e.message ?: "Unknown error",
                e
            )
        }

        // Step 5: Base64URL-encode credential ID for storage
        val credentialIdBase64url = Util.base64urlEncode(registrationResult.credentialId)

        // Step 6: Save credential locally as pending (isPrimary defaults to false)
        val credential = kit.credentialManager.createPendingCredential(
            credentialId = credentialIdBase64url,
            publicKey = registrationResult.publicKey,
            contractId = contractId,
            transports = registrationResult.transports,
            deviceType = registrationResult.deviceType,
            backedUp = registrationResult.backedUp
        )

        // Step 7: Emit credential created event
        kit.events.emit(SmartAccountEvent.CredentialCreated(credential = credential))

        // Step 8: Add passkey signer on-chain (reuse existing low-level method)
        val transactionResult = addPasskey(
            contextRuleId = contextRuleId,
            publicKey = registrationResult.publicKey,
            credentialId = registrationResult.credentialId,
            selectedSigners = selectedSigners,
            forceMethod = forceMethod
        )

        // Step 9: Return combined result
        return AddPasskeySignerResult(
            credentialId = credentialIdBase64url,
            publicKey = registrationResult.publicKey,
            transactionResult = transactionResult
        )
    }

    /**
     * Adds a WebAuthn passkey signer to a context rule.
     *
     * Creates an external signer with WebAuthn verification and adds it to the specified
     * context rule on the smart account contract. The public key must be an uncompressed
     * secp256r1 key (65 bytes starting with 0x04), and the credential ID must be non-empty.
     *
     * The transaction requires authorization from an existing signer on the specified
     * context rule. The user will be prompted for biometric authentication if the current
     * passkey is the authorizing signer.
     *
     * Contract call: `smart_account.add_signer(context_rule_id, signer)` — returns a u32 signer ID.
     * The assigned ID is available via [ParsedContextRule.signerIds] after fetching the context rule.
     *
     * @param contextRuleId The context rule ID to add the signer to (e.g., 0 for Default)
     * @param publicKey The uncompressed secp256r1 public key (65 bytes, starting with 0x04)
     * @param credentialId The WebAuthn credential identifier
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return TransactionResult indicating success or failure
     * @throws SmartAccountException if validation fails or transaction fails
     *
     * Example:
     * ```kotlin
     * val result = signerManager.addPasskey(
     *     contextRuleId = 0u,
     *     publicKey = secp256r1PublicKey,
     *     credentialId = credentialIdData
     * )
     *
     * if (result.success) {
     *     println("Passkey signer added successfully")
     * } else {
     *     println("Failed to add signer: ${result.error ?: ""}")
     * }
     * ```
     */
    suspend fun addPasskey(
        contextRuleId: UInt,
        publicKey: ByteArray,
        credentialId: ByteArray,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        // Validate inputs
        kit.requireConnected()

        // Validate public key
        if (publicKey.size != SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
            throw ValidationException.invalidInput(
                "publicKey",
                "Public key must be ${SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE} bytes, got: ${publicKey.size}"
            )
        }

        if (publicKey[0] != SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX) {
            throw ValidationException.invalidInput(
                "publicKey",
                "Public key must start with 0x04 (uncompressed format), got: 0x${publicKey[0].toString(16).padStart(2, '0')}"
            )
        }

        if (credentialId.isEmpty()) {
            throw ValidationException.invalidInput("credentialId", "Credential ID cannot be empty")
        }

        // Build WebAuthn external signer
        val signer = ExternalSigner.webAuthn(
            verifierAddress = kit.config.webauthnVerifierAddress,
            publicKey = publicKey,
            credentialId = credentialId
        )

        // Add signer via contract invocation
        return addSigner(contextRuleId = contextRuleId, signer = signer, selectedSigners = selectedSigners, forceMethod = forceMethod)
    }

    /**
     * Adds a delegated signer to a context rule.
     *
     * Creates a delegated signer that uses built-in Soroban require_auth verification
     * and adds it to the specified context rule. The address can be either a Stellar
     * account (G-address) or a smart contract (C-address).
     *
     * Delegated signers authorize transactions using the native Soroban authorization
     * mechanism, which calls `require_auth_for_args()` on the signer's address.
     *
     * The transaction requires authorization from an existing signer on the specified
     * context rule.
     *
     * Contract call: `smart_account.add_signer(context_rule_id, signer)` — returns a u32 signer ID.
     * The assigned ID is available via [ParsedContextRule.signerIds] after fetching the context rule.
     *
     * @param contextRuleId The context rule ID to add the signer to (e.g., 0 for Default)
     * @param address The Stellar address (G-address for accounts, C-address for contracts)
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return TransactionResult indicating success or failure
     * @throws SmartAccountException if validation fails or transaction fails
     *
     * Example:
     * ```kotlin
     * // Add an account signer (single-signer)
     * val result = signerManager.addDelegated(
     *     contextRuleId = 0u,
     *     address = "GA7QYNF7SOWQ..."
     * )
     *
     * // Add a contract signer (multi-signer)
     * val contractResult = signerManager.addDelegated(
     *     contextRuleId = 1u,
     *     address = "CBCD1234...",
     *     selectedSigners = listOf(
     *         SelectedSigner.Passkey(credentialId = cred1, credentialIdBytes = cred1Bytes, keyData = key1),
     *         SelectedSigner.Wallet("GA7Q...")
     *     )
     * )
     * ```
     */
    suspend fun addDelegated(
        contextRuleId: UInt,
        address: String,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        // Validate inputs
        kit.requireConnected()

        // Build delegated signer (validation happens in initializer)
        val signer = DelegatedSigner(address = address)

        // Add signer via contract invocation
        return addSigner(contextRuleId = contextRuleId, signer = signer, selectedSigners = selectedSigners, forceMethod = forceMethod)
    }

    /**
     * Adds an Ed25519 signer to a context rule.
     *
     * Creates an external signer with Ed25519 signature verification and adds it to the
     * specified context rule on the smart account contract. The public key must be a
     * 32-byte Ed25519 public key.
     *
     * Ed25519 signers use the traditional Stellar signing algorithm. The verifier contract
     * validates signatures against the provided public key.
     *
     * The transaction requires authorization from an existing signer on the specified
     * context rule.
     *
     * Contract call: `smart_account.add_signer(context_rule_id, signer)` — returns a u32 signer ID.
     * The assigned ID is available via [ParsedContextRule.signerIds] after fetching the context rule.
     *
     * @param contextRuleId The context rule ID to add the signer to (e.g., 0 for Default)
     * @param verifierAddress The Ed25519 verifier contract address (C-address)
     * @param publicKey The Ed25519 public key (32 bytes)
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return TransactionResult indicating success or failure
     * @throws SmartAccountException if validation fails or transaction fails
     *
     * Example:
     * ```kotlin
     * val result = signerManager.addEd25519(
     *     contextRuleId = 0u,
     *     verifierAddress = "CDEF5678...",
     *     publicKey = ed25519PublicKey
     * )
     *
     * if (result.success) {
     *     println("Ed25519 signer added successfully")
     * }
     * ```
     */
    suspend fun addEd25519(
        contextRuleId: UInt,
        verifierAddress: String,
        publicKey: ByteArray,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        // Validate inputs
        kit.requireConnected()

        // Build Ed25519 external signer (validation happens in factory method)
        val signer = ExternalSigner.ed25519(
            verifierAddress = verifierAddress,
            publicKey = publicKey
        )

        // Add signer via contract invocation
        return addSigner(contextRuleId = contextRuleId, signer = signer, selectedSigners = selectedSigners, forceMethod = forceMethod)
    }

    // MARK: - Remove Signer

    /**
     * Removes a signer from a context rule by its ID.
     *
     * Removes the signer with the given ID from the specified context rule on the smart
     * account contract. The signer ID is assigned by the contract when the signer is added
     * and is available via [ParsedContextRule.signerIds] after fetching the context rule.
     *
     * The transaction requires authorization from an existing signer on the specified
     * context rule.
     *
     * IMPORTANT: You cannot remove the last signer from a context rule unless the rule
     * has policies that provide authorization. The contract will throw error 3004
     * if you attempt to remove the last signer with no policies configured.
     *
     * Contract call: `smart_account.remove_signer(context_rule_id, signer_id)`
     *
     * @param contextRuleId The context rule ID to remove the signer from
     * @param signerId The on-chain signer ID assigned by the contract (available from [ParsedContextRule.signerIds])
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return TransactionResult indicating success or failure
     * @throws WalletException.NotConnected if no wallet is connected
     * @throws TransactionException if simulation, signing, or submission fails
     * @throws WebAuthnException if biometric authentication fails
     *
     * Example:
     * ```kotlin
     * // Fetch the parsed context rule to get signer IDs
     * val rules = kit.contextRuleManager.listContextRules()
     * val rule = rules.first { it.id == 0u }
     * val signerIdToRemove = rule.signerIds.first()
     *
     * val result = signerManager.removeSigner(
     *     contextRuleId = 0u,
     *     signerId = signerIdToRemove
     * )
     * ```
     */
    suspend fun removeSigner(
        contextRuleId: UInt,
        signerId: UInt,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        // Validate inputs
        val (_, contractId) = kit.requireConnected()

        // Build contract invocation for remove_signer
        val functionArgs = listOf(
            Scv.toUint32(contextRuleId),
            Scv.toUint32(signerId)
        )

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("remove_signer"),
            args = functionArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        // Route to single-signer or multi-signer submission
        return if (selectedSigners.isEmpty()) {
            kit.transactionOperations.submit(hostFunction = hostFunction, auth = emptyList(), forceMethod = forceMethod)
        } else {
            kit.multiSignerManager.submitWithMultipleSigners(hostFunction, selectedSigners, forceMethod = forceMethod)
        }
    }

    /**
     * Removes a signer from a context rule by matching the signer value.
     *
     * Convenience overload that resolves the on-chain signer ID internally. Fetches the
     * specified context rule (single RPC call), finds the matching signer by equality,
     * and delegates to the ID-based [removeSigner]. Use this when you have the
     * [SmartAccountSigner] object but not the numeric signer ID.
     *
     * @param contextRuleId The context rule ID to remove the signer from
     * @param signer The signer to remove (matched by equality against the rule's signers)
     * @param selectedSigners Optional list of [SelectedSigner] for multi-signer authorization.
     * @param forceMethod Optional submission method override.
     * @return TransactionResult indicating success or failure
     * @throws WalletException.NotConnected if no wallet is connected
     * @throws ValidationException if the signer is not found on the context rule
     * @throws TransactionException if simulation, signing, or submission fails
     * @throws WebAuthnException if biometric authentication fails
     *
     * Example:
     * ```kotlin
     * val result = kit.signerManager.removeSigner(
     *     contextRuleId = 1u,
     *     signer = DelegatedSigner(address = "GA7Q...")
     * )
     * ```
     */
    suspend fun removeSigner(
        contextRuleId: UInt,
        signer: SmartAccountSigner,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        // Fetch only the target context rule (single RPC call) and parse it
        val ruleScVal = kit.contextRuleManager.getContextRule(contextRuleId)
        val rule = kit.contextRuleManager.parseContextRule(ruleScVal)

        // Find the matching signer index
        val signerIndex = rule.signers.indexOfFirst { SmartAccountBuilders.signersEqual(it, signer) }
        if (signerIndex == -1) {
            throw ValidationException.invalidInput(
                "signer",
                "Signer not found on context rule $contextRuleId"
            )
        }

        // Bounds check: signerIds must be aligned with signers
        if (signerIndex >= rule.signerIds.size) {
            throw ValidationException.invalidInput(
                "signer",
                "Signer found at index $signerIndex but signerIds has only ${rule.signerIds.size} entries"
            )
        }

        val signerId = rule.signerIds[signerIndex]
        return removeSigner(contextRuleId, signerId, selectedSigners, forceMethod)
    }

    // MARK: - Private Helpers

    /**
     * Internal helper to add a signer to a context rule.
     *
     * Builds the contract invocation for add_signer and submits it via the appropriate
     * submission path. When [selectedSigners] is empty, uses single-signer submission.
     * When non-empty, delegates to [OZMultiSignerManager.submitWithMultipleSigners].
     *
     * Note: The contract assigns a u32 signer ID to the newly added signer. This ID is not
     * included in the [TransactionResult]. To discover it, fetch the context rule via
     * [OZContextRuleManager.listContextRules] and read [ParsedContextRule.signerIds].
     *
     * @param contextRuleId The context rule ID
     * @param signer The signer to add
     * @param selectedSigners List of signers for multi-signer authorization (empty for single-signer)
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return TransactionResult indicating success or failure
     * @throws SmartAccountException if the operation fails
     */
    private suspend fun addSigner(
        contextRuleId: UInt,
        signer: SmartAccountSigner,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        val (_, contractId) = kit.requireConnected()

        // Build contract invocation for add_signer
        val signerScVal = signer.toScVal()

        val functionArgs = listOf(
            Scv.toUint32(contextRuleId),
            signerScVal
        )

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("add_signer"),
            args = functionArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        // Route to single-signer or multi-signer submission
        return if (selectedSigners.isEmpty()) {
            kit.transactionOperations.submit(hostFunction = hostFunction, auth = emptyList(), forceMethod = forceMethod)
        } else {
            kit.multiSignerManager.submitWithMultipleSigners(hostFunction, selectedSigners, forceMethod = forceMethod)
        }
    }
}
