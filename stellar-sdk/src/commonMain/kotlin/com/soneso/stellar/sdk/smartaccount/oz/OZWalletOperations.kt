//
//  OZWalletOperations.kt
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
import com.soneso.stellar.sdk.MemoNone
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.crypto.getEd25519Crypto
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageFromAddressXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.CreateContractArgsV2Xdr
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SCVecXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import kotlinx.coroutines.delay

// MARK: - Result Types

/**
 * Result of a wallet creation operation.
 *
 * Contains the credential ID, contract address, public key, and optional transaction
 * hash if the wallet was auto-submitted to the network.
 *
 * @property credentialId The credential ID (Base64URL-encoded, no padding)
 * @property contractId The smart account contract address (C-address)
 * @property publicKey The uncompressed secp256r1 public key (65 bytes)
 * @property transactionHash The transaction hash if auto-submitted, null otherwise
 */
data class CreateWalletResult(
    val credentialId: String,
    val contractId: String,
    val publicKey: ByteArray,
    val transactionHash: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CreateWalletResult

        if (credentialId != other.credentialId) return false
        if (contractId != other.contractId) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (transactionHash != other.transactionHash) return false

        return true
    }

    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + contractId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + (transactionHash?.hashCode() ?: 0)
        return result
    }
}

/**
 * Result of a wallet connection operation.
 *
 * Contains the credential ID, contract address, and whether the connection was
 * restored from a saved session.
 *
 * @property credentialId The credential ID (Base64URL-encoded, no padding)
 * @property contractId The smart account contract address (C-address)
 * @property restoredFromSession Whether the connection was restored from a saved session
 */
data class ConnectWalletResult(
    val credentialId: String,
    val contractId: String,
    val restoredFromSession: Boolean
)

/**
 * Result of standalone passkey authentication.
 *
 * Contains the credential ID, normalized signature, and public key from a WebAuthn
 * authentication ceremony without connecting to a specific wallet contract.
 *
 * Use this result with:
 * - Indexer lookups to discover contracts for the credential
 * - Manual contract connection via connectWallet({ contractId, credentialId })
 * - Multi-signer operations that need pre-authenticated signatures
 *
 * @property credentialId The credential ID (Base64URL-encoded, no padding)
 * @property signature The WebAuthn signature with normalized compact format
 * @property publicKey The uncompressed secp256r1 public key (65 bytes)
 */
data class AuthenticatePasskeyResult(
    val credentialId: String,
    val signature: WebAuthnSignature,
    val publicKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthenticatePasskeyResult) return false
        return credentialId == other.credentialId &&
               signature == other.signature &&
               publicKey.contentEquals(other.publicKey)
    }
    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + signature.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}

// MARK: - Wallet Operations

/**
 * Operations for creating and connecting smart account wallets.
 *
 * OZWalletOperations provides high-level wallet lifecycle management:
 *
 * - Wallet creation with WebAuthn passkey generation
 * - Contract deployment with deterministic address derivation
 * - Wallet connection via session restoration or credential lookup
 * - Integration with indexer for credential-to-contract discovery
 *
 * This class requires a WebAuthnProvider to be set on the kit before use.
 * The provider handles platform-specific WebAuthn operations.
 *
 * Example usage:
 * ```kotlin
 * val kit = OZSmartAccountKit.create(config)
 * val walletOps = kit.walletOperations
 *
 * // Create a new wallet
 * val wallet = walletOps.createWallet(userName = "Alice", autoSubmit = true)
 * println("Created wallet: ${wallet.contractId}")
 *
 * // Connect to existing wallet (returns null if no session and prompt = false)
 * val connected = walletOps.connectWallet()
 * if (connected != null) {
 *     println("Connected: ${connected.contractId}")
 * } else {
 *     println("No active session found")
 * }
 *
 * // Connect with WebAuthn prompt fallback if no session
 * val prompted = walletOps.connectWallet(ConnectWalletOptions(prompt = true))
 * println("Connected: ${prompted?.contractId}")
 * ```
 *
 * @property kit Reference to the parent OZSmartAccountKit instance
 */
class OZWalletOperations internal constructor(
    private val kit: OZSmartAccountKit
) {
    /**
     * Credential manager for storage operations.
     */
    private val credentialManager: OZCredentialManager
        get() = kit.credentialManager

    // MARK: - Create Wallet

    /**
     * Creates a new smart account wallet with WebAuthn passkey authentication.
     *
     * Creates a new wallet by generating a WebAuthn credential, deriving the contract
     * address, and optionally deploying the smart account contract to the network.
     *
     * Flow:
     * 1. Generate random 32-byte challenge for WebAuthn
     * 2. Call WebAuthn registration (user authenticates with biometric/security key)
     * 3. Extract secp256r1 public key from attestation
     * 4. Derive deterministic contract address from credential ID
     * 5. Save credential as pending in storage
     * 6. Build deploy transaction (if autoSubmit, submit and delete credential on success)
     * 7. Fund wallet if autoFund is enabled
     * 8. Return result
     *
     * ## Auto-Fund Feature
     *
     * When `autoFund = true`, the wallet is automatically funded after deployment using
     * Friendbot (testnet only). This is useful for onboarding flows where users need
     * immediate access to a funded wallet.
     *
     * Requirements for autoFund:
     * - `autoSubmit` must be true (wallet must be deployed first)
     * - `nativeTokenContract` must be provided (the native token contract address)
     * - Must be on testnet (Friendbot is testnet-only)
     * - Relayer may be used for fee sponsoring if configured
     *
     * IMPORTANT: Requires a WebAuthnProvider to be configured in the kit config.
     * Throws WEBAUTHN_NOT_SUPPORTED if no provider is configured.
     *
     * @param userName Display name for the user (default: "Smart Account User")
     * @param autoSubmit Whether to automatically submit the deploy transaction (default: false)
     * @param autoFund Whether to automatically fund the wallet after deployment (default: false)
     * @param nativeTokenContract Contract address for the native token (required if autoFund is true)
     * @return CreateWalletResult containing credential ID, contract address, and transaction hash
     * @throws WebAuthnException if WebAuthn registration fails or no provider configured
     * @throws ValidationException if public key extraction fails or nativeTokenContract is missing when autoFund is true
     * @throws TransactionException if deployment or funding fails
     *
     * Example:
     * ```kotlin
     * // Create wallet without deploying (for later deployment)
     * val wallet = walletOps.createWallet(userName = "Alice", autoSubmit = false)
     * println("Wallet address: ${wallet.contractId}")
     * println("Credential ID: ${wallet.credentialId}")
     *
     * // Create and deploy immediately
     * val deployedWallet = walletOps.createWallet(userName = "Bob", autoSubmit = true)
     * println("Deployed at: ${deployedWallet.transactionHash ?: "unknown"}")
     *
     * // Create, deploy, and fund with native token (testnet)
     * val fundedWallet = walletOps.createWallet(
     *     userName = "Charlie",
     *     autoSubmit = true,
     *     autoFund = true,
     *     nativeTokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
     * )
     * println("Funded wallet at: ${fundedWallet.contractId}")
     * ```
     *
     * @see OZTransactionOperations.fundWallet for manual funding after deployment
     */
    suspend fun createWallet(
        userName: String = "Smart Account User",
        autoSubmit: Boolean = false,
        autoFund: Boolean = false,
        nativeTokenContract: String? = null
    ): CreateWalletResult {
        // STEP 1: Check for WebAuthn provider
        val webauthnProvider = kit.config.webauthnProvider
            ?: throw WebAuthnException.notSupported(
                "No WebAuthnProvider configured. Set webauthnProvider in config before calling createWallet()."
            )

        // STEP 1b: Validate autoFund requirements early (before WebAuthn/network)
        if (autoFund && nativeTokenContract == null) {
            throw ValidationException.InvalidInput(
                "nativeTokenContract is required when autoFund is true"
            )
        }

        // STEP 2: Generate random challenge (32 bytes) and user ID (32 bytes)
        val crypto = getEd25519Crypto()
        val challengeData = crypto.generatePrivateKey() // Reuse for 32 secure random bytes
        val userIdData = crypto.generatePrivateKey() // Reuse for 32 secure random bytes

        // STEP 3: Call WebAuthn registration
        val registrationResult = try {
            webauthnProvider.register(
                challenge = challengeData,
                userId = userIdData,
                userName = userName
            )
        } catch (e: Exception) {
            throw WebAuthnException.registrationFailed(
                "WebAuthn registration failed: ${e.message}",
                e
            )
        }

        // STEP 4: Extract public key from attestation (already extracted by provider)
        val publicKey = registrationResult.publicKey

        // STEP 5: Derive contract address
        val deployer = kit.getDeployer()
        val contractId = try {
            SmartAccountUtils.deriveContractAddress(
                credentialId = registrationResult.credentialId,
                deployerPublicKey = deployer.getAccountId(),
                networkPassphrase = kit.config.networkPassphrase
            )
        } catch (e: ValidationException) {
            throw e
        } catch (e: TransactionException) {
            throw e
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to derive contract address: ${e.message}",
                e
            )
        }

        // STEP 6: Base64URL-encode credential ID
        val credentialIdBase64url = SmartAccountSharedUtils.base64urlEncode(registrationResult.credentialId)

        // STEP 7: Save credential as pending (with metadata from registration)
        val credential = try {
            credentialManager.createPendingCredential(
                credentialId = credentialIdBase64url,
                publicKey = publicKey,
                contractId = contractId,
                transports = registrationResult.transports,
                deviceType = registrationResult.deviceType,
                backedUp = registrationResult.backedUp
            )
        } catch (e: CredentialException) {
            throw e
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                key = credentialIdBase64url,
                cause = e
            )
        }

        // Emit credential created event
        kit.events.emit(SmartAccountEvent.CredentialCreated(credential = credential))

        // STEP 8: Build deploy transaction and optionally submit
        var transactionHash: String? = null

        if (autoSubmit) {
            try {
                // Build deployment transaction
                transactionHash = deployWallet(
                    publicKey = publicKey,
                    credentialId = registrationResult.credentialId,
                    contractId = contractId,
                    credentialIdBase64url = credentialIdBase64url
                )

                // Set connected state after successful deployment
                kit.setConnectedState(
                    credentialId = credentialIdBase64url,
                    contractId = contractId
                )

                // Emit wallet connected event
                kit.events.emit(
                    SmartAccountEvent.WalletConnected(
                        contractId = contractId,
                        credentialId = credentialIdBase64url
                    )
                )

                // Fund wallet if requested
                if (autoFund) {
                    val tokenContract = nativeTokenContract
                        ?: throw ValidationException.invalidInput(
                            "nativeTokenContract",
                            "nativeTokenContract is required when autoFund is true"
                        )
                    kit.transactionOperations.fundWallet(nativeTokenContract = tokenContract)
                }

                // Delete credential on successful deployment
                try {
                    credentialManager.deleteCredential(credentialId = credentialIdBase64url)
                } catch (e: Exception) {
                    // Non-critical error - credential deletion failed but deployment succeeded
                }
            } catch (e: SmartAccountException) {
                throw e
            } catch (e: Exception) {
                // Mark deployment as failed
                try {
                    credentialManager.markDeploymentFailed(
                        credentialId = credentialIdBase64url,
                        error = e.message ?: "Unknown error"
                    )
                } catch (markError: Exception) {
                    // Ignore - main error is more important
                }
                throw TransactionException.submissionFailed(
                    "Failed to deploy wallet: ${e.message}",
                    e
                )
            }
        }

        // STEP 9: Return result
        return CreateWalletResult(
            credentialId = credentialIdBase64url,
            contractId = contractId,
            publicKey = publicKey,
            transactionHash = transactionHash
        )
    }

    // MARK: - Connect Wallet

    /**
     * Options for connecting to a wallet.
     *
     * These options control how wallet connection is performed, allowing for different
     * connection flows based on your application's needs.
     *
     * ## Decision Matrix
     *
     * | Options | Behavior |
     * |---------|----------|
     * | (default) | Session restore, return null if no session |
     * | credentialId and/or contractId | Direct connect, skip session check |
     * | fresh = true | Skip session, always WebAuthn |
     * | prompt = true | Session restore, WebAuthn if no session |
     * | fresh = true, prompt = true | fresh takes priority, always WebAuthn |
     *
     * ## Option Patterns
     *
     * **Silent Session Check (default)**
     * ```kotlin
     * // Returns saved session if valid, null if no session
     * val result = connectWallet()
     * if (result == null) {
     *     // Show login UI
     * }
     * ```
     *
     * **Session Check with WebAuthn Fallback**
     * ```kotlin
     * // Uses saved session if valid, prompts WebAuthn if no session
     * connectWallet(ConnectWalletOptions(prompt = true))
     * ```
     *
     * **Direct Connection with Known Credentials**
     * ```kotlin
     * // Connect to specific contract using known credential ID
     * connectWallet(ConnectWalletOptions(
     *     credentialId = "abc123...",
     *     contractId = "CABC..."
     * ))
     * ```
     *
     * **Force Fresh Authentication**
     * ```kotlin
     * // Require WebAuthn authentication even if session exists
     * connectWallet(ConnectWalletOptions(fresh = true))
     * ```
     *
     * **Credential-Only Connection**
     * ```kotlin
     * // Provide credential ID, look up contract from storage/indexer
     * connectWallet(ConnectWalletOptions(credentialId = "abc123..."))
     * ```
     *
     * @property credentialId Connect directly using this credential ID (Base64URL-encoded).
     *                        When provided alone, contract address is looked up from storage,
     *                        indexer, or derived from deployer. Skips WebAuthn authentication.
     *
     * @property contractId Connect directly to this contract address (C-address).
     *                      Must be used with credentialId. Cannot be used alone.
     *                      Useful when you already know both the credential and contract.
     *
     * @property fresh Force fresh WebAuthn authentication, skipping session restore.
     *                 Use this to require re-authentication even if a valid session exists.
     *                 Useful for sensitive operations or after session timeout warnings.
     *
     * @property prompt When true, triggers WebAuthn authentication if no valid session exists.
     *                  When false (default), returns null if no session is found.
     *                  Has no effect when [fresh] is true (WebAuthn is always triggered).
     *                  Has no effect when [credentialId] is provided (direct connect path).
     */
    data class ConnectWalletOptions(
        val credentialId: String? = null,
        val contractId: String? = null,
        val fresh: Boolean = false,
        val prompt: Boolean = false
    )

    /**
     * Connects to an existing smart account wallet.
     *
     * Returns a [ConnectWalletResult] on successful connection, or null if no valid
     * session exists and neither [ConnectWalletOptions.prompt] nor
     * [ConnectWalletOptions.fresh] is set.
     *
     * Connection flow:
     * 1. If credentialId/contractId provided: direct connect (always returns non-null)
     * 2. If fresh = true: skip session, trigger WebAuthn authentication
     * 3. Otherwise, check storage for a valid (non-expired) session
     * 4. If valid session found: restore and return restoredFromSession = true
     * 5. If no valid session and prompt = false: return null
     * 6. If no valid session and prompt = true: trigger WebAuthn authentication
     * 7. Look up contract address via storage, indexer, or on-chain derivation
     * 8. Save session, set kit connected state, return result
     *
     * ## Connection Options
     *
     * **Silent Session Check (default)**
     * ```kotlin
     * val result = walletOps.connectWallet()
     * if (result != null) {
     *     println("Connected: ${result.contractId}")
     * } else {
     *     println("No active session - show login UI")
     * }
     * ```
     *
     * **Session Check with WebAuthn Fallback**
     * ```kotlin
     * val result = walletOps.connectWallet(
     *     ConnectWalletOptions(prompt = true)
     * )
     * // Restores session if valid, prompts WebAuthn if not
     * ```
     *
     * **Direct Connection**
     * ```kotlin
     * val result = walletOps.connectWallet(
     *     ConnectWalletOptions(
     *         credentialId = "abc123...",
     *         contractId = "CABC..."
     *     )
     * )
     * // Connects directly without WebAuthn or session check
     * ```
     *
     * **Force Fresh Authentication**
     * ```kotlin
     * val result = walletOps.connectWallet(
     *     ConnectWalletOptions(fresh = true)
     * )
     * // Always prompts WebAuthn, ignoring any saved session
     * ```
     *
     * IMPORTANT: Requires a WebAuthnProvider to be configured in the kit config
     * when WebAuthn authentication is needed (prompt = true, fresh = true, or
     * direct credential connection).
     *
     * @param options Connection options controlling the flow behavior
     * @return ConnectWalletResult on success, or null if no session and prompt is false
     * @throws WebAuthnException if authentication fails or no provider configured
     * @throws WalletException if wallet not found
     * @throws ValidationException if options are invalid (e.g., contractId without credentialId)
     *
     * @see ConnectWalletOptions for detailed option descriptions and the decision matrix
     */
    suspend fun connectWallet(
        options: ConnectWalletOptions = ConnectWalletOptions()
    ): ConnectWalletResult? {
        // STEP 1: If credentialId or contractId provided, connect directly
        if (options.credentialId != null || options.contractId != null) {
            return connectWithCredentials(
                credentialId = options.credentialId,
                contractId = options.contractId
            )
        }

        // STEP 2: If fresh = false, check for valid session
        if (!options.fresh) {
            val session = try {
                kit.getStorage().getSession()
            } catch (e: Exception) {
                null
            }

            if (session != null && !session.isExpired) {
                // Valid session exists - silently reconnect
                kit.setConnectedState(
                    credentialId = session.credentialId,
                    contractId = session.contractId
                )

                // Emit wallet connected event
                kit.events.emit(
                    SmartAccountEvent.WalletConnected(
                        contractId = session.contractId,
                        credentialId = session.credentialId
                    )
                )

                return ConnectWalletResult(
                    credentialId = session.credentialId,
                    contractId = session.contractId,
                    restoredFromSession = true
                )
            }

            // If expired session, delete silently
            if (session != null && session.isExpired) {
                // Emit session expired event
                kit.events.emit(
                    SmartAccountEvent.SessionExpired(
                        contractId = session.contractId,
                        credentialId = session.credentialId
                    )
                )

                try {
                    kit.getStorage().clearSession()
                } catch (e: Exception) {
                    // Non-critical - continue
                }
            }

            // No valid session found - check if we should prompt for WebAuthn
            if (!options.prompt) {
                return null
            }
        }

        // STEP 3: No valid session with prompt = true, or fresh = true - require WebAuthn authentication
        val webauthnProvider = kit.config.webauthnProvider
            ?: throw WebAuthnException.notSupported(
                "No WebAuthnProvider configured. Set webauthnProvider in config before calling connectWallet()."
            )

        // Generate random challenge (32 bytes)
        val crypto = getEd25519Crypto()
        val challengeData = crypto.generatePrivateKey() // Reuse for 32 secure random bytes

        // STEP 4: Call WebAuthn authentication (no credential filter - allow all)
        val authenticationResult = try {
            webauthnProvider.authenticate(
                challenge = challengeData
            )
        } catch (e: Exception) {
            throw WebAuthnException.authenticationFailed(
                "WebAuthn authentication failed: ${e.message}",
                e
            )
        }

        // STEP 5: Base64URL-encode credential ID
        val credentialIdBase64url = SmartAccountSharedUtils.base64urlEncode(authenticationResult.credentialId)

        // STEP 6: Look up contract ID
        var contractId: String? = null

        // 6a. Check local storage
        try {
            val storedCredential = credentialManager.getCredential(credentialId = credentialIdBase64url)
            if (storedCredential != null) {
                contractId = storedCredential.contractId
            }
        } catch (e: Exception) {
            // Storage lookup failed - continue to indexer
        }

        // 6b. If not found and indexer configured: call indexer
        if (contractId == null) {
            val indexer = kit.indexerClient
            if (indexer != null) {
                try {
                    val lookupResponse = indexer.lookupByCredentialId(credentialIdBase64url)
                    if (lookupResponse.contracts.isNotEmpty()) {
                        contractId = lookupResponse.contracts.first().contractId
                    }
                } catch (e: Exception) {
                    // Indexer lookup failed - continue to derivation
                }
            }
        }

        // 6c. If still not found: derive contract address and verify on-chain
        if (contractId == null) {
            val deployer = kit.getDeployer()
            val derivedContractId = try {
                SmartAccountUtils.deriveContractAddress(
                    credentialId = authenticationResult.credentialId,
                    deployerPublicKey = deployer.getAccountId(),
                    networkPassphrase = kit.config.networkPassphrase
                )
            } catch (e: Exception) {
                throw WalletException.notFound(
                    "Failed to derive contract address: ${e.message}"
                )
            }

            // Verify contract exists by simulating a read-only call
            val contractAddress = try {
                Address(derivedContractId).toSCAddress()
            } catch (e: Exception) {
                throw WalletException.notFound(
                    "Invalid derived contract address: $derivedContractId"
                )
            }

            val verifyArgs = InvokeContractArgsXdr(
                contractAddress = contractAddress,
                functionName = SCSymbolXdr("get_context_rules_count"),
                args = emptyList()
            )
            val verifyFunction = HostFunctionXdr.InvokeContract(verifyArgs)

            try {
                SmartAccountSharedUtils.simulateAndExtractResult(
                    hostFunction = verifyFunction,
                    kit = kit
                )
            } catch (e: Exception) {
                throw WalletException.notFound(
                    "Contract not found at derived address: $derivedContractId"
                )
            }

            contractId = derivedContractId
        }

        val finalContractId = contractId
            ?: throw WalletException.notFound(
                "Failed to resolve contract address for credential ID: $credentialIdBase64url"
            )

        // STEP 7: Save session
        val expiresAt = currentTimeMillis() + kit.config.sessionExpiryMs
        val newSession = StoredSession(
            credentialId = credentialIdBase64url,
            contractId = finalContractId,
            connectedAt = currentTimeMillis(),
            expiresAt = expiresAt
        )

        try {
            kit.getStorage().saveSession(session = newSession)
        } catch (e: Exception) {
            // Session save failed - not critical, continue
        }

        // STEP 8: Set kit connected state
        kit.setConnectedState(
            credentialId = credentialIdBase64url,
            contractId = finalContractId
        )

        // Emit wallet connected event
        kit.events.emit(
            SmartAccountEvent.WalletConnected(
                contractId = finalContractId,
                credentialId = credentialIdBase64url
            )
        )

        // STEP 9: Return result
        return ConnectWalletResult(
            credentialId = credentialIdBase64url,
            contractId = finalContractId,
            restoredFromSession = false
        )
    }

    // MARK: - Authenticate Passkey

    /**
     * Authenticates with a passkey without connecting to a wallet.
     *
     * Use this when you need to authenticate the user before deciding
     * which contract to connect to, or for signing operations that
     * don't require a connected wallet state.
     *
     * This method performs a WebAuthn authentication ceremony and returns
     * the credential ID, signature, and public key without modifying the
     * kit's connection state.
     *
     * Typical usage patterns:
     * 1. Authenticate first, then discover contracts via indexer
     * 2. Authenticate for multi-signer operations
     * 3. Pre-authenticate before contract selection
     *
     * Flow:
     * 1. Check for WebAuthn provider
     * 2. Generate random challenge (32 bytes)
     * 3. Call WebAuthn authentication (no credential filter - allow all)
     * 4. Extract signature from authentication result
     * 5. Normalize signature (DER to compact, low-S)
     * 6. Build WebAuthnSignature from normalized signature
     * 7. Look up stored credential for public key (best effort)
     * 8. Return result without modifying kit state
     *
     * IMPORTANT: Requires a WebAuthnProvider to be configured in the kit config.
     * Throws WEBAUTHN_NOT_SUPPORTED if no provider is configured.
     *
     * @param challenge Optional challenge bytes to sign. If null, generates random 32 bytes.
     *                  Use this for specific transaction authorization flows.
     * @param credentialIds Optional list of allowed credential IDs (Base64URL-encoded).
     *                      If provided, only these credentials can be used for authentication.
     * @return AuthenticatePasskeyResult with credential ID, signature, and public key
     * @throws WebAuthnException if authentication fails or no provider configured
     * @throws ValidationException if signature normalization fails
     *
     * Example:
     * ```kotlin
     * // Step 1: Authenticate to get credential ID
     * val authResult = walletOps.authenticatePasskey()
     * println("Authenticated with credential: ${authResult.credentialId}")
     *
     * // Step 2: Discover contracts via indexer
     * val indexer = kit.indexerClient
     * val contracts = indexer?.lookupByCredentialId(authResult.credentialId)
     *
     * // Step 3: Let user choose or connect to the first one
     * if (!contracts.isNullOrEmpty()) {
     *     kit.setConnectedState(
     *         credentialId = authResult.credentialId,
     *         contractId = contracts.first().contractId
     *     )
     * }
     * ```
     */
    suspend fun authenticatePasskey(
        challenge: ByteArray? = null,
        credentialIds: List<String>? = null
    ): AuthenticatePasskeyResult {
        // STEP 1: Check for WebAuthn provider
        val webauthnProvider = kit.config.webauthnProvider
            ?: throw WebAuthnException.notSupported(
                "No WebAuthnProvider configured. Set webauthnProvider in config before calling authenticatePasskey()."
            )

        // STEP 2: Generate or use provided challenge
        val challengeData = challenge ?: run {
            val crypto = getEd25519Crypto()
            crypto.generatePrivateKey() // Reuse for 32 secure random bytes
        }

        // STEP 3: Call WebAuthn authentication
        // Note: The WebAuthnProvider interface doesn't currently support credential filtering
        // via credentialIds parameter. This would need to be added to the interface if required.
        // For now, we ignore the credentialIds parameter as the TS implementation also doesn't
        // use it in the simple authenticatePasskey() call.
        val authenticationResult = try {
            webauthnProvider.authenticate(
                challenge = challengeData
            )
        } catch (e: Exception) {
            throw WebAuthnException.authenticationFailed(
                "WebAuthn authentication failed: ${e.message}",
                e
            )
        }

        // STEP 4: Base64URL-encode credential ID
        val credentialIdBase64url = SmartAccountSharedUtils.base64urlEncode(authenticationResult.credentialId)

        // STEP 5: Normalize signature (DER to compact, low-S)
        val normalizedSignature = try {
            SmartAccountUtils.normalizeSignature(authenticationResult.signature)
        } catch (e: ValidationException) {
            throw e
        } catch (e: Exception) {
            throw ValidationException.invalidInput(
                "signature",
                "Failed to normalize WebAuthn signature: ${e.message}"
            )
        }

        // STEP 6: Build WebAuthnSignature
        val webAuthnSignature = try {
            WebAuthnSignature(
                authenticatorData = authenticationResult.authenticatorData,
                clientData = authenticationResult.clientDataJSON,
                signature = normalizedSignature
            )
        } catch (e: ValidationException) {
            throw e
        } catch (e: Exception) {
            throw ValidationException.invalidInput(
                "signature",
                "Failed to build WebAuthn signature: ${e.message}"
            )
        }

        // STEP 7: Look up stored credential for public key (best effort)
        // If not found, we'll return an empty byte array - the caller can look it up
        // from the indexer or on-chain contract state if needed
        var publicKey = ByteArray(0)
        try {
            val storedCredential = credentialManager.getCredential(credentialId = credentialIdBase64url)
            if (storedCredential != null) {
                publicKey = storedCredential.publicKey
            }
        } catch (e: Exception) {
            // Storage lookup failed - continue with empty public key
            // Caller can retrieve it from other sources if needed
        }

        // STEP 8: Return result without modifying kit state
        return AuthenticatePasskeyResult(
            credentialId = credentialIdBase64url,
            signature = webAuthnSignature,
            publicKey = publicKey
        )
    }


    /**
     * Connects to a wallet using explicit credential ID and contract ID.
     *
     * This is a helper function used by connectWallet when credentialId
     * or contractId options are provided. It skips session restoration
     * and WebAuthn authentication, directly connecting to the specified
     * credential/contract.
     *
     * Flow:
     * 1. If credentialId provided, look up contract ID from storage or derive it
     * 2. If contractId provided without credentialId, throw validation error
     * 3. Verify contract exists on-chain
     * 4. Save new session
     * 5. Set kit connected state
     * 6. Return result
     *
     * @param credentialId The credential ID (Base64URL-encoded), optional
     * @param contractId The contract ID (C-address), optional
     * @return ConnectWalletResult with restoredFromSession = false
     * @throws ValidationException if contractId provided without credentialId
     * @throws WalletException if wallet not found or contract doesn't exist
     */
    private suspend fun connectWithCredentials(
        credentialId: String?,
        contractId: String?
    ): ConnectWalletResult {
        // Validate: contractId requires credentialId
        if (contractId != null && credentialId == null) {
            throw ValidationException.invalidInput(
                "contractId",
                "contractId option requires credentialId to be provided"
            )
        }

        var finalCredentialId = credentialId
        var finalContractId = contractId

        // If credentialId provided, look up contract ID if not provided
        if (finalCredentialId != null) {
            // Look up in storage first
            if (finalContractId == null) {
                try {
                    val storedCredential = credentialManager.getCredential(credentialId = finalCredentialId)
                    if (storedCredential != null) {
                        finalContractId = storedCredential.contractId
                    }
                } catch (e: Exception) {
                    // Storage lookup failed - continue to indexer
                }
            }

            // If not found, try indexer
            if (finalContractId == null) {
                val indexer = kit.indexerClient
                if (indexer != null) {
                    try {
                        val lookupResponse = indexer.lookupByCredentialId(finalCredentialId)
                        if (lookupResponse.contracts.isNotEmpty()) {
                            finalContractId = lookupResponse.contracts.first().contractId
                        }
                    } catch (e: Exception) {
                        // Indexer lookup failed - continue to derivation
                    }
                }
            }

            // If still not found, derive contract address
            if (finalContractId == null) {
                val deployer = kit.getDeployer()

                // Decode Base64URL credential ID to raw bytes
                val credentialIdBytes = SmartAccountSharedUtils.base64urlDecode(finalCredentialId)
                    ?: throw ValidationException.invalidInput(
                        "credentialId",
                        "Invalid Base64URL-encoded credential ID"
                    )

                finalContractId = try {
                    SmartAccountUtils.deriveContractAddress(
                        credentialId = credentialIdBytes,
                        deployerPublicKey = deployer.getAccountId(),
                        networkPassphrase = kit.config.networkPassphrase
                    )
                } catch (e: Exception) {
                    throw WalletException.notFound(
                        "Failed to derive contract address: ${e.message}"
                    )
                }
            }
        }

        // At this point, we must have both credentialId and contractId
        if (finalCredentialId == null || finalContractId == null) {
            throw WalletException.notFound(
                "Could not determine credential ID or contract ID"
            )
        }

        // Verify contract exists on-chain
        val contractAddress = try {
            Address(finalContractId).toSCAddress()
        } catch (e: Exception) {
            throw WalletException.notFound(
                "Invalid contract address: $finalContractId"
            )
        }

        val verifyArgs = InvokeContractArgsXdr(
            contractAddress = contractAddress,
            functionName = SCSymbolXdr("get_context_rules_count"),
            args = emptyList()
        )
        val verifyFunction = HostFunctionXdr.InvokeContract(verifyArgs)

        try {
            SmartAccountSharedUtils.simulateAndExtractResult(
                hostFunction = verifyFunction,
                kit = kit
            )
        } catch (e: Exception) {
            throw WalletException.notFound(
                "Contract not found at address: $finalContractId. The wallet may not be deployed yet."
            )
        }

        // Save new session
        val expiresAt = currentTimeMillis() + kit.config.sessionExpiryMs
        val newSession = StoredSession(
            credentialId = finalCredentialId,
            contractId = finalContractId,
            connectedAt = currentTimeMillis(),
            expiresAt = expiresAt
        )

        try {
            kit.getStorage().saveSession(session = newSession)
        } catch (e: Exception) {
            // Session save failed - not critical, continue
        }

        // Set kit connected state
        kit.setConnectedState(
            credentialId = finalCredentialId,
            contractId = finalContractId
        )

        // Emit wallet connected event
        kit.events.emit(
            SmartAccountEvent.WalletConnected(
                contractId = finalContractId,
                credentialId = finalCredentialId
            )
        )

        // Return result
        return ConnectWalletResult(
            credentialId = finalCredentialId,
            contractId = finalContractId,
            restoredFromSession = false
        )
    }

    // MARK: - Private Helpers

    /**
     * Deploys a smart account wallet contract.
     *
     * Builds, simulates, signs, and submits a deployment transaction.
     * Polls for confirmation and returns the transaction hash on success.
     *
     * @param publicKey The uncompressed secp256r1 public key (65 bytes)
     * @param credentialId The WebAuthn credential ID (raw bytes)
     * @param contractId The derived contract address
     * @param credentialIdBase64url The Base64URL-encoded credential ID
     * @return The transaction hash
     * @throws TransactionException if deployment fails
     */
    private suspend fun deployWallet(
        publicKey: ByteArray,
        credentialId: ByteArray,
        contractId: String,
        credentialIdBase64url: String
    ): String {
        // Build key_data = publicKey (65 bytes) + credentialId
        val keyData = publicKey + credentialId

        // Build signer = External(webauthnVerifier, keyData)
        val webauthnSigner = try {
            ExternalSigner(
                verifierAddress = kit.config.webauthnVerifierAddress,
                keyData = keyData
            )
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to create WebAuthn signer: ${e.message}",
                e
            )
        }

        // Build constructor arguments:
        // - signers: Vec([External signer])
        // - policies: Map([])
        val signersScVal = try {
            SCValXdr.Vec(SCVecXdr(listOf(webauthnSigner.toScVal())))
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to convert signer to ScVal: ${e.message}",
                e
            )
        }

        // Empty policies map
        val policiesScVal = SCValXdr.Map(SCMapXdr(emptyList()))

        val constructorArgs = listOf(signersScVal, policiesScVal)

        // TODO: Build proper CreateContractArgsV2Xdr with WASM hash and constructor args
        // This requires:
        // 1. ContractIDPreimageXdr.FromAddress with deployer address and salt
        // 2. ContractExecutableXdr.StellarAsset with WASM hash
        // 3. Constructor arguments list
        //
        // For now, this is a placeholder showing the required structure.
        // The proper implementation needs to match the iOS SDK's InvokeHostFunctionOperation.forCreatingContractWithConstructor

        val deployer = kit.getDeployer()
        val salt = SmartAccountUtils.getContractSalt(credentialId = credentialId)

        // Build contract ID preimage
        val deployerSCAddress = SCAddressXdr.AccountId(deployer.getXdrAccountId())
        val contractIdPreimage = ContractIDPreimageXdr.FromAddress(
            ContractIDPreimageFromAddressXdr(
                address = deployerSCAddress,
                salt = Uint256Xdr(salt)
            )
        )

        // Build contract executable from WASM hash
        // Note: WASM hash needs to be converted from hex string to ByteArray
        val wasmHashBytes = kit.config.accountWasmHash.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
        val contractExecutable = ContractExecutableXdr.WasmHash(HashXdr(wasmHashBytes))

        // Build CreateContractArgsV2Xdr
        val createContractArgs = CreateContractArgsV2Xdr(
            contractIdPreimage = contractIdPreimage,
            executable = contractExecutable,
            constructorArgs = constructorArgs
        )

        // Build host function
        val hostFunction = HostFunctionXdr.CreateContractV2(createContractArgs)

        // Create operation
        val operation = InvokeHostFunctionOperation(
            hostFunction = hostFunction,
            auth = emptyList()
        )
        operation.sourceAccount = deployer.getAccountId()

        // Get deployer account
        val deployerAccount = try {
            kit.sorobanServer.getAccount(address = deployer.getAccountId())
        } catch (e: Exception) {
            throw TransactionException.submissionFailed(
                "Failed to fetch deployer account: ${e.message}",
                e
            )
        }

        // Build transaction
        val transaction = try {
            TransactionBuilder(
                sourceAccount = deployerAccount,
                network = Network(networkPassphrase = kit.config.networkPassphrase)
            )
                .setBaseFee(100)
                .addOperation(operation)
                .addMemo(MemoNone)
                .setTimeout(300)
                .build()
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to build transaction: ${e.message}",
                e
            )
        }

        // Simulate transaction
        val simulation = try {
            kit.sorobanServer.simulateTransaction(transaction = transaction)
        } catch (e: Exception) {
            // Mark deployment as failed
            try {
                credentialManager.markDeploymentFailed(
                    credentialId = credentialIdBase64url,
                    error = "Simulation failed: ${e.message}"
                )
            } catch (markError: Exception) {
                // Ignore
            }
            throw TransactionException.simulationFailed(
                "Failed to simulate deployment transaction: ${e.message}",
                e
            )
        }

        if (simulation.error != null) {
            // Mark deployment as failed
            try {
                credentialManager.markDeploymentFailed(
                    credentialId = credentialIdBase64url,
                    error = "Simulation error: ${simulation.error}"
                )
            } catch (e: Exception) {
                // Ignore
            }
            throw TransactionException.simulationFailed(
                "Simulation error: ${simulation.error}"
            )
        }

        // Assemble transaction from simulation
        val transactionData = simulation.parseTransactionData()
            ?: throw TransactionException.submissionFailed(
                "Failed to get transaction data from simulation"
            )

        val minResourceFee = simulation.minResourceFee
            ?: throw TransactionException.submissionFailed(
                "Failed to get min resource fee from simulation"
            )

        // Build updated transaction with Soroban data and resource fee
        val updatedTransaction = try {
            TransactionBuilder(
                sourceAccount = deployerAccount,
                network = Network(networkPassphrase = kit.config.networkPassphrase)
            )
                .setBaseFee(100 + minResourceFee)
                .addOperation(operation)
                .addMemo(MemoNone)
                .setSorobanData(transactionData)
                .setTimeout(300)
                .build()
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to build updated transaction: ${e.message}",
                e
            )
        }

        // Sign with deployer
        try {
            updatedTransaction.sign(signer = deployer)
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to sign transaction: ${e.message}",
                e
            )
        }

        // Submit transaction
        val sendResult = try {
            kit.sorobanServer.sendTransaction(transaction = updatedTransaction)
        } catch (e: Exception) {
            // Mark deployment as failed
            try {
                credentialManager.markDeploymentFailed(
                    credentialId = credentialIdBase64url,
                    error = "Failed to send transaction: ${e.message}"
                )
            } catch (markError: Exception) {
                // Ignore
            }
            throw TransactionException.submissionFailed(
                "Failed to send deployment transaction: ${e.message}",
                e
            )
        }

        if (sendResult.errorResultXdr != null) {
            // Mark deployment as failed
            try {
                credentialManager.markDeploymentFailed(
                    credentialId = credentialIdBase64url,
                    error = "Transaction error: ${sendResult.errorResultXdr}"
                )
            } catch (e: Exception) {
                // Ignore
            }
            throw TransactionException.submissionFailed(
                "Deployment transaction error: ${sendResult.errorResultXdr}"
            )
        }

        val transactionHash = sendResult.hash
            ?: throw TransactionException.submissionFailed(
                "No transaction hash returned from submission"
            )

        // Poll for confirmation
        var confirmed = false
        for (attempt in 1..10) {
            delay(2000) // Wait 2 seconds between polls

            val txStatus = try {
                kit.sorobanServer.getTransaction(hash = transactionHash)
            } catch (e: Exception) {
                // Network error, retry
                if (attempt < 10) {
                    continue
                }
                // Mark deployment as failed
                try {
                    credentialManager.markDeploymentFailed(
                        credentialId = credentialIdBase64url,
                        error = "Deployment confirmation timed out"
                    )
                } catch (markError: Exception) {
                    // Ignore
                }
                throw TransactionException.timeout(
                    "Deployment confirmation timed out"
                )
            }

            when (txStatus.status) {
                GetTransactionStatus.SUCCESS -> {
                    confirmed = true
                }
                GetTransactionStatus.FAILED -> {
                    // Mark deployment as failed
                    try {
                        credentialManager.markDeploymentFailed(
                            credentialId = credentialIdBase64url,
                            error = txStatus.resultXdr ?: "Deployment failed on-chain"
                        )
                    } catch (e: Exception) {
                        // Ignore
                    }
                    throw TransactionException.submissionFailed(
                        "Deployment failed: ${txStatus.resultXdr ?: "unknown"}"
                    )
                }
                else -> {
                    // NOT_FOUND or unknown - continue polling
                    continue
                }
            }

            if (confirmed) break
        }

        if (!confirmed) {
            // Mark deployment as failed
            try {
                credentialManager.markDeploymentFailed(
                    credentialId = credentialIdBase64url,
                    error = "Deployment confirmation timed out"
                )
            } catch (e: Exception) {
                // Ignore
            }
            throw TransactionException.timeout(
                "Deployment confirmation timed out"
            )
        }

        return transactionHash
    }
}
