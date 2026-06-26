//
//  OZWalletOperations.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.AbstractTransaction
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.currentTimeMillis
import com.soneso.stellar.sdk.InvokeHostFunctionOperation
import com.soneso.stellar.sdk.MemoNone
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.TransactionBuilder
import com.soneso.stellar.sdk.secureRandomBytes
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.assembleTransaction
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageFromAddressXdr
import com.soneso.stellar.sdk.xdr.ContractIDPreimageXdr
import com.soneso.stellar.sdk.xdr.CreateContractArgsV2Xdr
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.Uint256Xdr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

// MARK: - Result Types

/**
 * Result of a wallet creation operation.
 *
 * Contains the credential ID, contract address, public key, the signed deploy transaction
 * XDR (always present), and an optional transaction hash if the wallet was auto-submitted.
 *
 * The [signedTransactionXdr] field is always populated — the deploy transaction is built
 * and signed regardless of `autoSubmit`. When `autoSubmit` is false, the caller can use
 * this XDR to submit the transaction externally or store it for later submission via
 * [OZWalletOperations.deployPendingCredential].
 *
 * @property credentialId The credential ID (Base64URL-encoded, no padding)
 * @property contractId The smart account contract address (C-address)
 * @property publicKey The uncompressed secp256r1 public key (65 bytes)
 * @property signedTransactionXdr The base64-encoded signed deploy transaction envelope
 * @property transactionHash The transaction hash if auto-submitted, null otherwise
 * @property nickname The user display name provided during wallet creation
 */
data class CreateWalletResult(
    val credentialId: String,
    val contractId: String,
    val publicKey: ByteArray,
    val signedTransactionXdr: String,
    val transactionHash: String? = null,
    val nickname: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CreateWalletResult

        if (credentialId != other.credentialId) return false
        if (contractId != other.contractId) return false
        if (!Util.constantTimeEquals(publicKey, other.publicKey)) return false
        if (signedTransactionXdr != other.signedTransactionXdr) return false
        if (transactionHash != other.transactionHash) return false
        if (nickname != other.nickname) return false

        return true
    }

    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + contractId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + signedTransactionXdr.hashCode()
        result = 31 * result + (transactionHash?.hashCode() ?: 0)
        result = 31 * result + (nickname?.hashCode() ?: 0)
        return result
    }
}

/**
 * Result of deploying a pending credential.
 *
 * Returned by [OZWalletOperations.deployPendingCredential] when retrying a failed or
 * deferred wallet deployment.
 *
 * @property contractId The smart account contract address (C-address)
 * @property signedTransactionXdr The base64-encoded signed deploy transaction envelope
 * @property transactionHash The transaction hash if auto-submitted, null when autoSubmit is false
 */
data class DeployPendingResult(
    val contractId: String,
    val signedTransactionXdr: String,
    val transactionHash: String? = null
)

/**
 * Result of a wallet connection operation.
 *
 * `connectWallet` and `connectWithCredentials` return one of two arms:
 *
 * - [Connected]: a single contract was resolved for the credential. The
 *   connection state has been set on the kit and the session has been saved.
 * - [Ambiguous]: the indexer reported multiple contracts for the credential
 *   (the passkey is registered as a signer on more than one contract). The
 *   connection state has NOT been set; the caller must let the user pick a
 *   contract and re-call `connectWallet` (or `connectWithAddress` in the demo)
 *   with the chosen contractId.
 *
 * `Ambiguous` is by-construction unreachable when an explicit `contractId` is
 * supplied (the cascade is bypassed). The session-restore path inside
 * `connectWallet` therefore always sees `Connected`.
 */
sealed class ConnectWalletResult {

    /**
     * The credential ID (Base64URL-encoded, no padding).
     */
    abstract val credentialId: String

    /**
     * A single contract was resolved for the credential.
     *
     * The kit's connection state is set and the session has been saved.
     *
     * @property credentialId The credential ID (Base64URL-encoded, no padding)
     * @property contractId The smart account contract address (C-address)
     * @property restoredFromSession Whether the connection was restored from a saved session
     */
    data class Connected(
        override val credentialId: String,
        val contractId: String,
        val restoredFromSession: Boolean
    ) : ConnectWalletResult()

    /**
     * The indexer returned more than one contract for the credential.
     *
     * The kit's connection state is NOT set. The caller is expected to let
     * the user select a contract from [candidates] and re-connect with the
     * chosen `contractId`.
     *
     * @property credentialId The credential ID (Base64URL-encoded, no padding)
     * @property candidates Contract addresses (C-addresses) returned by the indexer
     */
    data class Ambiguous(
        override val credentialId: String,
        val candidates: List<String>
    ) : ConnectWalletResult()
}

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
 * @property publicKey The uncompressed secp256r1 public key (65 bytes) if found in local storage,
 *   or an empty ByteArray if the credential is not stored locally.
 */
data class AuthenticatePasskeyResult(
    val credentialId: String,
    val signature: WebAuthnSignature,
    val publicKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthenticatePasskeyResult) return false
        val keyMatch = Util.constantTimeEquals(publicKey, other.publicKey)
        return credentialId == other.credentialId
            && signature == other.signature
            && keyMatch
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
     * 1. Validate WebAuthn provider and autoFund requirements
     * 2. Call WebAuthn registration (user authenticates with biometric/security key)
     * 3. Extract and normalize secp256r1 public key from attestation
     * 4. Derive deterministic contract address from credential ID
     * 5. Save credential as pending in storage
     * 6. Set connected state and save session (before deployment)
     * 7. Build and sign the deploy transaction (always, regardless of autoSubmit)
     * 8. If autoSubmit: submit the transaction, fund wallet if autoFund, delete credential
     * 9. Return result
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
     * @param forceMethod Optional override to force relayer or RPC submission (default: auto-detect)
     * @return CreateWalletResult containing credential ID, contract address, signed transaction XDR, and optional transaction hash
     * @throws WebAuthnException if WebAuthn registration fails or no provider configured
     * @throws ValidationException if public key extraction fails or nativeTokenContract is missing when autoFund is true
     * @throws TransactionException if deployment or funding fails
     * @throws CredentialException if credential storage fails
     * @throws StorageException if storage write fails
     *
     * Example:
     * ```kotlin
     * // Create wallet without deploying — signedTransactionXdr always present for external submission
     * val wallet = walletOps.createWallet(userName = "Alice", autoSubmit = false)
     * println("Wallet address: ${wallet.contractId}")
     * println("Signed XDR: ${wallet.signedTransactionXdr}")
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
     * @see deployPendingCredential for retrying a failed or deferred deployment
     */
    suspend fun createWallet(
        userName: String = "Smart Account User",
        autoSubmit: Boolean = false,
        autoFund: Boolean = false,
        nativeTokenContract: String? = null,
        forceMethod: SubmissionMethod? = null
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
        val challengeData = secureRandomBytes(32)
        val userIdData = secureRandomBytes(32)

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

        // STEP 4: Normalize and validate the public key from registration.
        // Built-in providers return correct 65-byte uncompressed keys, but custom
        // providers (e.g., YubiKey) might return SPKI-wrapped or COSE-encoded keys.
        val publicKey = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = registrationResult.publicKey,
            attestationObject = registrationResult.attestationObject
        )

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
        val credentialIdBase64url = Util.base64urlEncode(registrationResult.credentialId)

        // STEP 7: Save credential as pending (with metadata from registration)
        val credential = try {
            credentialManager.createPendingCredential(
                credentialId = credentialIdBase64url,
                publicKey = publicKey,
                contractId = contractId,
                nickname = userName,
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

        // Mark as primary — this is the wallet creation passkey
        try {
            credentialManager.setPrimary(credentialIdBase64url)
        } catch (_: Exception) {
            // Non-critical — isPrimary is metadata only
        }

        // Emit credential created event
        kit.events.emit(SmartAccountEvent.CredentialCreated(credential = credential))

        // STEP 8: Set connected state and save session (before deployment)
        kit.setConnectedState(
            credentialId = credentialIdBase64url,
            contractId = contractId
        )

        kit.events.emit(
            SmartAccountEvent.WalletConnected(
                contractId = contractId,
                credentialId = credentialIdBase64url
            )
        )

        saveSession(credentialId = credentialIdBase64url, contractId = contractId)

        // STEP 9: Always build the deploy transaction (side-effect-free)
        val deployTransaction = try {
            buildDeployTransaction(
                publicKey = publicKey,
                credentialId = registrationResult.credentialId,
                forceMethod = forceMethod
            )
        } catch (e: Exception) {
            try {
                credentialManager.markDeploymentFailed(
                    credentialId = credentialIdBase64url,
                    error = e.message ?: "Build failed"
                )
            } catch (_: Exception) {}
            throw (e as? SmartAccountException) ?: TransactionException.submissionFailed(
                "Failed to build deploy transaction: ${e.message}",
                e
            )
        }
        val signedTxXdr = deployTransaction.toEnvelopeXdrBase64()

        // STEP 10: Optionally submit
        var transactionHash: String? = null

        if (autoSubmit) {
            transactionHash = submitDeployTransaction(
                transaction = deployTransaction,
                credentialIdBase64url = credentialIdBase64url,
                forceMethod = forceMethod
            )

            // Fund wallet if requested
            if (autoFund) {
                val tokenContract = nativeTokenContract
                    ?: throw ValidationException.invalidInput(
                        "nativeTokenContract",
                        "nativeTokenContract is required when autoFund is true"
                    )
                // Wait for the deployed contract instance to propagate to the Soroban RPC.
                // The deploy may be confirmed on Horizon but not yet visible to RPC
                // simulation until a later ledger close. fundWallet simulates against the
                // contract and fails if the instance is not yet applied to the RPC state,
                // so poll the RPC until the instance is visible rather than assuming a
                // fixed propagation delay.
                waitForContractVisibleToRpc(contractId)
                kit.transactionOperations.fundWallet(
                    nativeTokenContract = tokenContract,
                    forceMethod = forceMethod
                )
            }

            // Delete credential on successful deployment
            try {
                credentialManager.deleteCredential(credentialId = credentialIdBase64url)
            } catch (_: Exception) {
                // Non-critical - credential is transitional
            }
        }

        // STEP 11: Return result
        return CreateWalletResult(
            credentialId = credentialIdBase64url,
            contractId = contractId,
            publicKey = publicKey,
            signedTransactionXdr = signedTxXdr,
            transactionHash = transactionHash,
            nickname = userName
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
     * The non-null result is one of two sealed arms:
     * - [ConnectWalletResult.Connected]: a single contract was resolved, the
     *   kit's connected state has been set, and a session has been saved.
     * - [ConnectWalletResult.Ambiguous]: the indexer reported multiple
     *   contracts where the passkey is registered as a signer. The connected
     *   state has NOT been set; the caller must let the user pick a contract
     *   and re-call `connectWallet(ConnectWalletOptions(credentialId,
     *   contractId = chosen))` to finalize.
     *
     * Connection flow:
     * 1. If credentialId/contractId provided: direct connect via [connectWithCredentials].
     * 2. If fresh = true: skip session, trigger WebAuthn authentication.
     * 3. Otherwise, check storage for a valid (non-expired) session. If a
     *    valid session is found, verify the contract on-chain and reconnect.
     *    If verification returns NotFound (contract is not on-chain), clear
     *    the stale session and continue. Other exceptions during session
     *    verification propagate (the saved session is preserved so the user
     *    can retry once their network is healthy).
     * 4. If no valid session and prompt = false: return null.
     * 5. If no valid session and prompt = true: trigger WebAuthn authentication.
     * 6. Resolve the contract via the cascade: storage → derivation → indexer.
     *    Storage with FAILED deployment status throws with a recovery hint.
     *    Derivation under the configured deployer is checked on-chain; if
     *    the address has no contract, falls through to the indexer. Indexer
     *    returns 0 / 1 / N>1 contracts mapping to NotFound / Connected /
     *    Ambiguous respectively.
     * 7. On Connected: save session, set kit connected state, return.
     *    On Ambiguous: return early without saving a session.
     *
     * ## Connection Options
     *
     * **Silent Session Check (default)**
     * ```kotlin
     * val result = walletOps.connectWallet()
     * when (result) {
     *     null -> println("No active session - show login UI")
     *     is ConnectWalletResult.Connected -> println("Connected: ${result.contractId}")
     *     is ConnectWalletResult.Ambiguous -> { /* unreachable for the silent path */ }
     * }
     * ```
     *
     * **Session Check with WebAuthn Fallback**
     * ```kotlin
     * val result = walletOps.connectWallet(
     *     ConnectWalletOptions(prompt = true)
     * )
     * when (result) {
     *     null -> { /* unreachable when prompt = true */ }
     *     is ConnectWalletResult.Connected -> println("Connected: ${result.contractId}")
     *     is ConnectWalletResult.Ambiguous -> showPicker(result.candidates)
     * }
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
     * // Explicit contractId bypasses the cascade. The non-null result is
     * // always Connected; Ambiguous is by-construction unreachable.
     * ```
     *
     * **Force Fresh Authentication**
     * ```kotlin
     * val result = walletOps.connectWallet(
     *     ConnectWalletOptions(fresh = true)
     * )
     * // Always prompts WebAuthn, ignoring any saved session.
     * ```
     *
     * IMPORTANT: Requires a WebAuthnProvider to be configured in the kit config
     * when WebAuthn authentication is needed (prompt = true or fresh = true).
     *
     * @param options Connection options controlling the flow behavior.
     * @return [ConnectWalletResult] on success, or null if no session and
     *   prompt is false.
     * @throws WebAuthnException if WebAuthn authentication fails or no
     *   provider is configured.
     * @throws WalletException.NotFound if no contract can be resolved for
     *   the credential (e.g. a FAILED storage entry, no on-chain contract
     *   at the derived address, or the indexer reports zero contracts).
     * @throws ValidationException if options are invalid (e.g. contractId
     *   without credentialId), or if the configured deployer's public key
     *   is malformed.
     * @throws TransactionException if XDR encoding of the contract-id
     *   preimage fails (an internal SDK bug).
     * @throws com.soneso.stellar.sdk.rpc.SorobanRpcException if a Soroban
     *   RPC call (e.g. the on-chain `verifyContractExists` check) fails
     *   for transport / server reasons. RPC errors propagate as their
     *   original type rather than being laundered to NotFound, so callers
     *   can distinguish "contract is not on-chain" from "lookup was
     *   inconclusive."
     * @throws com.soneso.stellar.sdk.smartaccount.core.IndexerException
     *   if the indexer call fails for transport / server reasons.
     *
     * @see ConnectWalletOptions for detailed option descriptions and the decision matrix.
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
            } catch (_: Exception) {
                null
            }

            if (session != null && !session.isExpired) {
                // Valid session exists - verify contract still exists on-chain
                try {
                    val result = connectWithCredentials(
                        credentialId = session.credentialId,
                        contractId = session.contractId
                    )
                    // The session-restore path supplies an explicit contractId,
                    // which bypasses the cascade in connectWithCredentials. The
                    // result is therefore always Connected; Ambiguous is by
                    // construction unreachable here.
                    return when (result) {
                        is ConnectWalletResult.Connected -> result.copy(restoredFromSession = true)
                        is ConnectWalletResult.Ambiguous -> error(
                            "Unreachable: connectWithCredentials with explicit contractId never returns Ambiguous"
                        )
                    }
                } catch (_: WalletException.NotFound) {
                    // The stored contract is not on-chain (it was never
                    // deployed, or the saved contractId is malformed). Clear
                    // the stale session and fall through. Archived contracts
                    // do not trigger this branch -- the RPC's
                    // getLedgerEntries returns archived entries as real
                    // entries, so verifyContractExists passes for them.
                    //
                    // Other exceptions (network/RPC errors) propagate to the
                    // caller; the saved session is preserved so the user can
                    // retry once their network is healthy.
                    try {
                        kit.getStorage().clearSession()
                    } catch (_: Exception) {
                        // Non-critical
                    }
                    // Fall through to prompt or return null
                }
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
                } catch (_: Exception) {
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
        val challengeData = secureRandomBytes(32)

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
        val credentialIdBase64url = Util.base64urlEncode(authenticationResult.credentialId)

        // STEP 6: Look up contract ID via cascade: storage -> derivation -> indexer.
        //
        // Storage hit means deployment is PENDING or FAILED (successful deploys
        // delete the credential). FAILED entries throw with a recovery hint;
        // PENDING entries are trusted without an on-chain re-verify here.
        //
        // Derivation finds "the contract this passkey deployed under our
        // configured deployer." Exceptions from deriveContractAddress
        // (ValidationException.InvalidAddress for a malformed deployer
        // config, TransactionException.SigningFailed for an internal
        // encoding bug) propagate as their original types so the developer
        // sees the actual cause rather than a misleading "not found."
        //
        // If derivation succeeds and the address has no on-chain contract,
        // the WalletException.NotFound thrown by verifyContractExists is
        // caught and we fall through to the indexer. Other RPC / network
        // errors during verification propagate as their original types so
        // the caller knows the lookup was inconclusive.
        //
        // Indexer fallback: returns contracts where the passkey is registered
        // as a signer, sourced from on-chain signer_registered events
        // (emitted both at deploy time and on add_signer). It is reached
        // when derivation produces no on-chain contract -- typically because
        // the passkey was added as a signer to an existing wallet, or the
        // wallet was originally deployed under a different deployer than the
        // one configured here. N=0 means not found; N=1 is verified on-chain
        // for symmetry with the derivation arm; N>1 returns
        // ConnectWalletResult.Ambiguous so the caller can let the user pick.
        var contractId: String? = null

        // 6a. Storage
        val storedCredential = try {
            credentialManager.getCredential(credentialId = credentialIdBase64url)
        } catch (_: Exception) {
            null
        }
        if (storedCredential != null) {
            if (storedCredential.deploymentStatus == CredentialDeploymentStatus.FAILED) {
                throw WalletException.notFound(
                    "Smart account deployment previously failed for credential $credentialIdBase64url. " +
                    "Call deployPendingCredential() to retry, or deleteCredential() to start over."
                )
            }
            contractId = storedCredential.contractId
        }

        // 6b. Derivation: derive the deterministic address and check it on-chain.
        if (contractId == null) {
            val deployer = kit.getDeployer()
            val derivedContractId = SmartAccountUtils.deriveContractAddress(
                credentialId = authenticationResult.credentialId,
                deployerPublicKey = deployer.getAccountId(),
                networkPassphrase = kit.config.networkPassphrase
            )

            try {
                verifyContractExists(derivedContractId)
                contractId = derivedContractId
            } catch (_: WalletException.NotFound) {
                // Address is well-formed but no contract is deployed there.
                // Typically this means the passkey was added as a signer to
                // an existing wallet (rather than deploying its own under our
                // configured deployer). Fall through to the indexer for
                // discovery.
            }
            // Any other exception from verifyContractExists (network/RPC) propagates.
        }

        // 6c. Indexer fallback: discover contracts where the passkey is registered.
        if (contractId == null) {
            val indexer = kit.indexerClient
                ?: throw WalletException.notFound(
                    "Could not resolve contract for credential $credentialIdBase64url. " +
                    "No contract was found at the derived address and no indexer is configured."
                )

            val candidates = indexer.lookupByCredentialId(credentialIdBase64url).contracts
            when (candidates.size) {
                0 -> throw WalletException.notFound(
                    "No contract found for credential $credentialIdBase64url."
                )
                1 -> {
                    val candidate = candidates.first().contractId
                    verifyContractExists(candidate)
                    contractId = candidate
                }
                else -> {
                    // Multiple contracts list this passkey as a signer. We do
                    // not pick one for the user -- return Ambiguous and let
                    // the caller render a picker. Connection state is NOT set.
                    return ConnectWalletResult.Ambiguous(
                        credentialId = credentialIdBase64url,
                        candidates = candidates.map { it.contractId }
                    )
                }
            }
        }

        // The cascade above either set contractId to a non-null value or
        // exited via throw / early return; the compiler smart-casts contractId
        // to String here.
        val finalContractId: String = contractId

        // End-of-cascade on-chain verify. For the Stage B derivation arm and
        // the Stage C N=1 arm this is a redundant repeat of the per-stage
        // verify. For the Stage A storage-hit arm (PENDING credential) it is
        // the only verification, and catches the "credential is in storage
        // but the deploy never landed on-chain" case (otherwise the user
        // would silently connect to a non-existent contract and the next
        // operation would fail confusingly). Mirrors connectWithCredentials.
        verifyContractExists(finalContractId)

        // Delete transitional credential from storage after on-chain
        // verification. No-ops cleanly when storage didn't have an entry
        // (Stage B / Stage C paths).
        try {
            credentialManager.deleteCredential(credentialId = credentialIdBase64url)
        } catch (_: Exception) {
            // Non-critical - credential is transitional
        }

        // Set connected state
        kit.setConnectedState(
            credentialId = credentialIdBase64url,
            contractId = finalContractId
        )

        kit.events.emit(
            SmartAccountEvent.WalletConnected(
                contractId = finalContractId,
                credentialId = credentialIdBase64url
            )
        )

        saveSession(credentialId = credentialIdBase64url, contractId = finalContractId)

        return ConnectWalletResult.Connected(
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
     * val response = kit.indexerClient?.lookupByCredentialId(authResult.credentialId)
     *
     * // Step 3: Connect to the first contract found
     * val firstContract = response?.contracts?.firstOrNull()
     * if (firstContract != null) {
     *     val connected = walletOps.connectWallet(
     *         ConnectWalletOptions(
     *             credentialId = authResult.credentialId,
     *             contractId = firstContract.contractId
     *         )
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
        val challengeData = challenge ?: secureRandomBytes(32)

        // STEP 3: Call WebAuthn authentication
        // Decode base64url credential IDs and enrich with stored transport hints so the
        // OS/browser can route to the correct authenticator without an extra round-trip.
        // `stored` may be null for cross-device credentials not yet in local storage — in
        // that case null transports is the correct fallback (browser uses its defaults).
        val allowCredentials = credentialIds?.map { credIdStr ->
            val idBytes = Util.base64urlDecode(credIdStr)
            val stored = try { kit.getStorage().get(credIdStr) } catch (_: Exception) { null }
            AllowCredential(id = idBytes, transports = stored?.transports)
        }

        val authenticationResult = try {
            webauthnProvider.authenticate(
                challenge = challengeData,
                allowCredentials = allowCredentials
            )
        } catch (e: Exception) {
            throw WebAuthnException.authenticationFailed(
                "WebAuthn authentication failed: ${e.message}",
                e
            )
        }

        // STEP 4: Base64URL-encode credential ID
        val credentialIdBase64url = Util.base64urlEncode(authenticationResult.credentialId)

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
        } catch (_: Exception) {
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
     * Connects to a wallet using an explicit credential ID and / or contract ID.
     *
     * Helper used by [connectWallet] when [ConnectWalletOptions.credentialId]
     * or [ConnectWalletOptions.contractId] is provided, and by the
     * session-restore path inside [connectWallet] STEP 2. Skips WebAuthn
     * authentication and session restore (those are the caller's
     * responsibility).
     *
     * Behavior depends on which arguments are non-null:
     * - **Both provided** (`credentialId` and `contractId`): the cascade is
     *   bypassed entirely. The supplied `contractId` is used directly. The
     *   on-chain verification at the end of the function catches the case
     *   where the explicit contract address does not exist on-chain. The
     *   result is always [ConnectWalletResult.Connected];
     *   [ConnectWalletResult.Ambiguous] is by construction unreachable.
     * - **`credentialId` only** (no `contractId`): runs the
     *   storage → derivation → indexer cascade.
     *   - Stage A (storage): a credential with PENDING status is trusted;
     *     FAILED throws with a recovery hint pointing at
     *     [deployPendingCredential] or [deleteCredential].
     *   - Stage B (derivation): derives the deterministic address under the
     *     configured deployer and verifies it on-chain. NotFound from
     *     verification falls through to the indexer; other RPC errors
     *     propagate.
     *   - Stage C (indexer): looks up contracts where the passkey is
     *     registered. Returns [ConnectWalletResult.Connected] for N=1 (with
     *     on-chain verify) and [ConnectWalletResult.Ambiguous] for N>1.
     *
     * After a successful resolution, the function:
     * 1. Verifies the contract on-chain (catches the explicit-contractId case
     *    where the caller passed an address that does not exist on-chain).
     * 2. Deletes any transitional credential from storage (PENDING entries
     *    are removed once the contract is confirmed live).
     * 3. Sets the kit's connected state and saves a session.
     *
     * @param credentialId The credential ID (Base64URL-encoded), optional.
     * @param contractId The contract ID (C-address), optional. Must be
     *   accompanied by `credentialId` if provided.
     * @return [ConnectWalletResult.Connected] when a single contract is
     *   resolved, or [ConnectWalletResult.Ambiguous] when the indexer
     *   reports multiple contracts (cascade path only).
     * @throws ValidationException if `contractId` is provided without
     *   `credentialId`, or if the configured deployer's public key is
     *   malformed (the latter is from `deriveContractAddress`).
     * @throws WalletException.NotFound if the contract can't be resolved
     *   (FAILED storage entry, no on-chain contract at the derived address,
     *   indexer returns zero results, or the explicit `contractId` is not
     *   on-chain).
     * @throws TransactionException if XDR encoding of the contract-id
     *   preimage fails (an internal SDK bug).
     * @throws com.soneso.stellar.sdk.rpc.SorobanRpcException if a Soroban
     *   RPC call (e.g. the on-chain `verifyContractExists` check) fails
     *   for transport / server reasons. RPC errors propagate as their
     *   original type rather than being laundered to NotFound, so callers
     *   can distinguish "contract is not on-chain" from "lookup was
     *   inconclusive."
     * @throws com.soneso.stellar.sdk.smartaccount.core.IndexerException
     *   if the indexer call fails for transport / server reasons.
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

        // contractId is reassigned during the cascade so it needs a local var;
        // credentialId is read-only and used as the function parameter directly.
        var finalContractId = contractId

        // When credentialId is provided without contractId, run the cascade:
        // storage -> derivation -> indexer. When contractId is provided, the
        // cascade is bypassed entirely (caller-supplied contractId wins) and
        // Ambiguous is by-construction unreachable.
        if (credentialId != null && finalContractId == null) {

            // Stage A: Storage. A storage hit means deployment is PENDING or
            // FAILED (successful deploy deletes the credential). FAILED entries
            // throw with a recovery hint; PENDING entries are trusted without
            // an on-chain re-verify here.
            val storedCredential = try {
                credentialManager.getCredential(credentialId = credentialId)
            } catch (_: Exception) {
                null
            }
            if (storedCredential != null) {
                if (storedCredential.deploymentStatus == CredentialDeploymentStatus.FAILED) {
                    throw WalletException.notFound(
                        "Smart account deployment previously failed for credential $credentialId. " +
                        "Call deployPendingCredential() to retry, or deleteCredential() to start over."
                    )
                }
                finalContractId = storedCredential.contractId
            }

            // Stage B: Derivation. Derive the deterministic address and check
            // it on-chain. Exceptions from deriveContractAddress
            // (ValidationException.InvalidAddress for a malformed deployer
            // config, TransactionException.SigningFailed for an internal
            // encoding bug) propagate as their original types.
            //
            // NotFound from verifyContractExists falls through to the indexer
            // (the passkey was added as a signer to an existing wallet rather
            // than deploying its own under our configured deployer). Other
            // RPC errors during verification propagate as their original types
            // so the caller knows the lookup was inconclusive.
            if (finalContractId == null) {
                val deployer = kit.getDeployer()

                // Decode Base64URL credential ID to raw bytes
                val credentialIdBytes = try {
                    Util.base64urlDecode(credentialId)
                } catch (_: IllegalArgumentException) {
                    throw ValidationException.invalidInput(
                        "credentialId",
                        "Invalid Base64URL-encoded credential ID"
                    )
                }

                val derivedContractId = SmartAccountUtils.deriveContractAddress(
                    credentialId = credentialIdBytes,
                    deployerPublicKey = deployer.getAccountId(),
                    networkPassphrase = kit.config.networkPassphrase
                )

                try {
                    verifyContractExists(derivedContractId)
                    finalContractId = derivedContractId
                } catch (_: WalletException.NotFound) {
                    // Address is well-formed but no contract is deployed there.
                    // Typically the passkey was added as a signer to an
                    // existing wallet rather than deploying its own under our
                    // configured deployer. Fall through to the indexer.
                }
                // Any other exception from verifyContractExists propagates.
            }

            // Stage C: Indexer fallback.
            if (finalContractId == null) {
                val indexer = kit.indexerClient
                    ?: throw WalletException.notFound(
                        "Could not resolve contract for credential $credentialId. " +
                        "No contract was found at the derived address and no indexer is configured."
                    )

                val candidates = indexer.lookupByCredentialId(credentialId).contracts
                when (candidates.size) {
                    0 -> throw WalletException.notFound(
                        "No contract found for credential $credentialId."
                    )
                    1 -> {
                        val candidate = candidates.first().contractId
                        verifyContractExists(candidate)
                        finalContractId = candidate
                    }
                    else -> {
                        // Multiple contracts list this passkey as a signer.
                        // Return Ambiguous; do NOT set connected state, do
                        // NOT delete the transitional credential.
                        return ConnectWalletResult.Ambiguous(
                            credentialId = credentialId,
                            candidates = candidates.map { it.contractId }
                        )
                    }
                }
            }
        }

        // At this point, we must have both credentialId and contractId
        if (credentialId == null || finalContractId == null) {
            throw WalletException.notFound(
                "Could not determine credential ID or contract ID"
            )
        }

        // Verify contract exists on-chain by checking for its instance ledger entry.
        // For the storage-hit and explicit-contractId paths this is the only
        // verification; for the derivation and indexer-N=1 paths it is a no-op
        // repeat (already verified during the cascade) but kept for symmetry
        // and to catch the explicit-contractId case where the caller passes a
        // contract address that does not exist on-chain.
        verifyContractExists(finalContractId)

        // Delete transitional credential from storage after on-chain verification
        try {
            credentialManager.deleteCredential(credentialId = credentialId)
        } catch (_: Exception) {
            // Non-critical - credential is transitional
        }

        // Set connected state
        kit.setConnectedState(
            credentialId = credentialId,
            contractId = finalContractId
        )

        kit.events.emit(
            SmartAccountEvent.WalletConnected(
                contractId = finalContractId,
                credentialId = credentialId
            )
        )

        saveSession(credentialId = credentialId, contractId = finalContractId)

        return ConnectWalletResult.Connected(
            credentialId = credentialId,
            contractId = finalContractId,
            restoredFromSession = false
        )
    }

    // MARK: - Shared Helpers

    /**
     * Verifies a smart account contract exists on-chain by checking its instance ledger entry.
     *
     * Distinguishes three outcomes so callers can react correctly:
     * - **Entry returned (live or archived)**: returns normally. The Soroban
     *   RPC's `getLedgerEntries` returns archived entries as real entries
     *   (with a TTL marker indicating archived state); a state-archived
     *   contract therefore passes this check. The next operation against the
     *   contract auto-restores the entry on Protocol 23+.
     * - **Malformed address** (`IllegalArgumentException` from `Address(contractId)`)
     *   or **no entry on-chain** (`getContractData` returns `null` because
     *   the RPC reports the key as `NotFound`): throws [WalletException.NotFound].
     *   Both mean "no contract exists at this address" -- a malformed
     *   address by definition cannot have one.
     * - **Lookup failed** (network / RPC exception from `SorobanServer.getContractData`):
     *   the original exception propagates as its original type. The caller
     *   knows the lookup was inconclusive rather than authoritatively "not found."
     *
     * @param contractId The contract address to verify
     * @throws WalletException.NotFound if the contract does not exist on-chain
     */
    private suspend fun verifyContractExists(contractId: String) {
        val instanceEntry = try {
            kit.sorobanServer.getContractData(
                contractId = contractId,
                key = Scv.toLedgerKeyContractInstance(),
                durability = SorobanServer.Durability.PERSISTENT
            )
        } catch (e: IllegalArgumentException) {
            // Malformed C-address. By definition no contract exists at it.
            throw WalletException.notFound(
                "Invalid contract address: $contractId (${e.message})"
            )
        }

        if (instanceEntry == null) {
            throw WalletException.notFound(
                "Contract not found at address: $contractId"
            )
        }
    }

    /**
     * Waits until the freshly deployed smart-account contract instance is visible to the
     * Soroban RPC before funding.
     *
     * Polls the contract-instance ledger entry via [SorobanServer.getContractData] for
     * [contractId] on the [OZConstants.RPC_VISIBILITY_POLL_INTERVAL_MS] interval, up to an
     * overall [OZConstants.RPC_VISIBILITY_TIMEOUT_SECONDS] budget, and returns once the
     * instance entry is observed. A null result means the contract is not yet visible (the
     * expected pre-propagation state); transient RPC or transport errors are retried until
     * the deadline.
     *
     * The lookup mirrors [verifyContractExists]: it queries the persistent contract-instance
     * ledger key, so it observes the same structural signal the funding simulation depends on.
     *
     * @param contractId The deployed smart-account contract address (C-address) to wait for
     * @throws TransactionException.Timeout if the contract is not visible within the budget
     */
    private suspend fun waitForContractVisibleToRpc(contractId: String) {
        pollUntilContractVisibleToRpc(contractId) { id ->
            kit.sorobanServer.getContractData(
                contractId = id,
                key = Scv.toLedgerKeyContractInstance(),
                durability = SorobanServer.Durability.PERSISTENT
            ) != null
        }
    }

    /**
     * Saves a session to storage for reconnection.
     *
     * @param credentialId The Base64URL-encoded credential ID
     * @param contractId The smart account contract address
     */
    private suspend fun saveSession(credentialId: String, contractId: String) {
        val now = currentTimeMillis()
        val session = StoredSession(
            credentialId = credentialId,
            contractId = contractId,
            connectedAt = now,
            expiresAt = now + kit.config.sessionExpiryMs
        )
        kit.getStorage().saveSession(session = session)
    }

    // MARK: - Deploy Pending Credential

    /**
     * Deploys a wallet from a previously created pending credential.
     *
     * Use this method to retry a failed deployment or to submit a wallet that was
     * created with [createWallet] using `autoSubmit = false`. The credential must
     * exist in local storage with a valid `publicKey` and `contractId`.
     *
     * This method sets the connected state on the kit (same as [createWallet]) so
     * the kit is ready to use immediately after a successful deployment.
     *
     * Flow:
     * 1. Look up credential from storage (throws [CredentialException] if not found or invalid)
     * 2. Set connected state and emit [SmartAccountEvent.WalletConnected]
     * 3. Build and sign the deploy transaction
     * 4. If autoSubmit: submit via relayer or RPC, poll for confirmation
     * 5. If autoFund: fund wallet via native token contract
     * 6. Delete credential from storage on successful deployment
     * 7. Return [DeployPendingResult] with the contract address, signed XDR, and optional hash
     *
     * @param credentialId The credential ID (Base64URL-encoded, no padding) to deploy
     * @param autoSubmit Whether to automatically submit the deploy transaction (default: true)
     * @param autoFund Whether to automatically fund the wallet after deployment (default: false)
     * @param nativeTokenContract Contract address for the native token (required if autoFund is true)
     * @param forceMethod Optional override to force relayer or RPC submission (default: auto-detect)
     * @return DeployPendingResult with contractId, signedTransactionXdr, and optional transactionHash
     * @throws CredentialException if the credential is not found or missing required fields
     * @throws ValidationException if autoFund is true but nativeTokenContract is null
     * @throws TransactionException if building, simulating, or submitting the deploy transaction fails
     *
     * Example (retry a failed deployment with auto-submit):
     * ```kotlin
     * val result = walletOps.deployPendingCredential(
     *     credentialId = "abc123...",
     *     autoSubmit = true
     * )
     * println("Deployed at tx: ${result.transactionHash}")
     * ```
     *
     * Example (build the XDR without submitting, for external submission):
     * ```kotlin
     * val result = walletOps.deployPendingCredential(
     *     credentialId = "abc123...",
     *     autoSubmit = false
     * )
     * println("Signed XDR: ${result.signedTransactionXdr}")
     * // Submit externally or store for later
     * ```
     *
     * @see createWallet for initial wallet creation with deferred deployment
     */
    suspend fun deployPendingCredential(
        credentialId: String,
        autoSubmit: Boolean = true,
        autoFund: Boolean = false,
        nativeTokenContract: String? = null,
        forceMethod: SubmissionMethod? = null
    ): DeployPendingResult {
        // Validate autoFund requirements early (before any network calls)
        if (autoFund && nativeTokenContract == null) {
            throw ValidationException.InvalidInput(
                "nativeTokenContract is required when autoFund is true"
            )
        }

        // Look up credential from storage
        val credential = credentialManager.getCredential(credentialId = credentialId)
            ?: throw CredentialException.notFound(credentialId)

        // Validate credential has required fields
        val publicKey = credential.publicKey
        if (publicKey.isEmpty()) {
            throw CredentialException.invalid(
                "Credential '$credentialId' is missing publicKey"
            )
        }

        val contractId = credential.contractId
        if (contractId.isNullOrEmpty()) {
            throw CredentialException.invalid(
                "Credential '$credentialId' is missing contractId"
            )
        }

        // Decode credentialId bytes from base64url
        val credentialIdBytes = try {
            Util.base64urlDecode(credentialId)
        } catch (_: IllegalArgumentException) {
            throw CredentialException.invalid(
                "Invalid Base64URL-encoded credential ID: $credentialId"
            )
        }

        // Set connected state and save session (before deployment attempt)
        kit.setConnectedState(
            credentialId = credentialId,
            contractId = contractId
        )

        kit.events.emit(
            SmartAccountEvent.WalletConnected(
                contractId = contractId,
                credentialId = credentialId
            )
        )

        saveSession(credentialId = credentialId, contractId = contractId)

        // Build the deploy transaction (side-effect-free)
        val deployTransaction = try {
            buildDeployTransaction(
                publicKey = publicKey,
                credentialId = credentialIdBytes,
                forceMethod = forceMethod
            )
        } catch (e: Exception) {
            try {
                credentialManager.markDeploymentFailed(
                    credentialId = credentialId,
                    error = e.message ?: "Build failed"
                )
            } catch (_: Exception) {}
            throw (e as? SmartAccountException) ?: TransactionException.submissionFailed(
                "Failed to build deploy transaction: ${e.message}",
                e
            )
        }
        val signedTxXdr = deployTransaction.toEnvelopeXdrBase64()

        if (!autoSubmit) {
            return DeployPendingResult(
                contractId = contractId,
                signedTransactionXdr = signedTxXdr
            )
        }

        // Submit the transaction
        val hash = submitDeployTransaction(
            transaction = deployTransaction,
            credentialIdBase64url = credentialId,
            forceMethod = forceMethod
        )

        // Fund wallet if requested
        if (autoFund) {
            // Wait for the deployed contract instance to propagate to the Soroban RPC
            // before funding. fundWallet simulates against the contract and fails if the
            // instance is not yet applied to the RPC state, so poll the RPC until the
            // instance is visible rather than assuming a fixed propagation delay.
            waitForContractVisibleToRpc(contractId)
            kit.transactionOperations.fundWallet(
                nativeTokenContract = nativeTokenContract!!,
                forceMethod = forceMethod
            )
        }

        // Delete credential on successful deployment
        try {
            credentialManager.deleteCredential(credentialId = credentialId)
        } catch (_: Exception) {
            // Non-critical - credential is transitional
        }

        return DeployPendingResult(
            contractId = contractId,
            signedTransactionXdr = signedTxXdr,
            transactionHash = hash
        )
    }

    // MARK: - Private Helpers

    /**
     * Builds, simulates, assembles, and signs the deploy transaction.
     *
     * This method is side-effect-free — it does not write to storage or mark
     * credentials as failed. Callers are responsible for failure marking.
     *
     * When a relayer is configured (and [forceMethod] does not override this), the
     * timeout is set to 30 seconds and the fee is set to the resource fee only
     * (the relayer wraps it in a fee-bump with the outer fee).
     *
     * @param publicKey The uncompressed secp256r1 public key (65 bytes)
     * @param credentialId The WebAuthn credential ID (raw bytes)
     * @param forceMethod Optional override to force relayer or RPC submission. When null, auto-detects based on relayer configuration.
     * @return The signed [Transaction] ready for submission
     * @throws TransactionException if building or simulation fails
     */
    private suspend fun buildDeployTransaction(
        publicKey: ByteArray,
        credentialId: ByteArray,
        forceMethod: SubmissionMethod? = null
    ): Transaction {
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
            Scv.toVec(listOf(webauthnSigner.toScVal()))
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to convert signer to ScVal: ${e.message}",
                e
            )
        }

        // Empty policies map
        val policiesScVal = Scv.toMap(linkedMapOf())

        val constructorArgs = listOf(signersScVal, policiesScVal)

        val deployer = kit.getDeployer()
        val salt = SmartAccountUtils.getContractSalt(credentialId = credentialId)

        // Build contract ID preimage
        val deployerSCAddress = Address(deployer.getAccountId()).toSCAddress()
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
                .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
                .addOperation(operation)
                .addMemo(MemoNone)
                .setTimeout(kit.config.timeoutInSeconds.toLong())
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
            throw TransactionException.simulationFailed(
                "Failed to simulate deployment transaction: ${e.message}",
                e
            )
        }

        if (simulation.error != null) {
            throw TransactionException.simulationFailed(
                "Simulation error: ${simulation.error}"
            )
        }

        // Assemble transaction from simulation. assembleTransaction handles:
        // - Updating auth entries from simulation results
        // - Setting soroban data (footprint, resource limits)
        // - Calculating fee as classicFee + minResourceFee
        val assembled = try {
            assembleTransaction(transaction, simulation)
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to assemble transaction: ${e.message}",
                e
            )
        }

        // When using a relayer, the fee must equal exactly the resource fee
        // (relayer wraps it in a fee-bump with the outer fee).
        // assembleTransaction sets fee = classicFee + resourceFee, so reconstruct
        // with fee = resourceFee only for the relayer path.
        val useRelayer = resolveDeploySubmissionMethod(forceMethod) == SubmissionMethod.RELAYER
        val updatedTransaction = if (useRelayer) {
            val minResourceFee = simulation.minResourceFee
                ?: throw TransactionException.submissionFailed(
                    "Failed to get min resource fee from simulation"
                )
            Transaction(
                sourceAccount = assembled.sourceAccount,
                fee = minResourceFee,
                sequenceNumber = assembled.sequenceNumber,
                operations = assembled.operations,
                memo = assembled.memo,
                preconditions = assembled.preconditions,
                sorobanData = assembled.sorobanData,
                network = assembled.network
            )
        } else {
            assembled
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

        return updatedTransaction
    }

    /**
     * Submits a pre-built and signed deploy transaction via relayer or direct RPC.
     *
     * Respects [forceMethod]: when null, auto-detects (relayer if configured, otherwise RPC).
     * Marks the credential as failed on submission or confirmation errors.
     * Polls for on-chain confirmation and returns the transaction hash.
     *
     * @param transaction The signed deploy transaction
     * @param credentialIdBase64url The Base64URL-encoded credential ID (for failure marking)
     * @param forceMethod Optional override to force relayer or RPC submission
     * @return The confirmed transaction hash
     * @throws TransactionException if submission or confirmation fails
     */
    private suspend fun submitDeployTransaction(
        transaction: Transaction,
        credentialIdBase64url: String,
        forceMethod: SubmissionMethod? = null
    ): String {
        val useRelayer = resolveDeploySubmissionMethod(forceMethod) == SubmissionMethod.RELAYER

        // Submit transaction via relayer (fee-bump) or directly to Soroban RPC
        val transactionHash: String

        if (useRelayer) {
            val relayer = kit.relayerClient
                ?: throw TransactionException.submissionFailed(
                    "Relayer was selected but no relayer is configured"
                )
            // Use relayer XDR mode: relayer fee-bumps the signed transaction
            val txEnvelope = transaction.toEnvelopeXdr()
            val relayerResponse = try {
                relayer.sendXdr(txEnvelope)
            } catch (e: Exception) {
                try {
                    credentialManager.markDeploymentFailed(
                        credentialId = credentialIdBase64url,
                        error = "Relayer submission failed: ${e.message}"
                    )
                } catch (_: Exception) {}
                throw TransactionException.submissionFailed(
                    "Failed to submit deployment via relayer: ${e.message}",
                    e
                )
            }

            if (!relayerResponse.success) {
                val errorMsg = relayerResponse.error ?: "Relayer submission failed"
                try {
                    credentialManager.markDeploymentFailed(
                        credentialId = credentialIdBase64url,
                        error = "Relayer error: $errorMsg"
                    )
                } catch (_: Exception) {}
                throw TransactionException.submissionFailed(
                    "Deployment relayer error: $errorMsg"
                )
            }

            transactionHash = relayerResponse.hash
                ?: throw TransactionException.submissionFailed(
                    "No transaction hash returned from relayer"
                )
        } else {
            // No relayer configured (or forced RPC): submit directly to Soroban RPC.
            // NOTE: The deployer account must be funded with XLM to pay fees.
            val sendResult = try {
                kit.sorobanServer.sendTransaction(transaction = transaction)
            } catch (e: Exception) {
                try {
                    credentialManager.markDeploymentFailed(
                        credentialId = credentialIdBase64url,
                        error = "Failed to send transaction: ${e.message}"
                    )
                } catch (_: Exception) {}
                throw TransactionException.submissionFailed(
                    "Failed to send deployment transaction: ${e.message}",
                    e
                )
            }

            if (sendResult.errorResultXdr != null) {
                try {
                    credentialManager.markDeploymentFailed(
                        credentialId = credentialIdBase64url,
                        error = "Transaction error: ${sendResult.errorResultXdr}"
                    )
                } catch (_: Exception) {}
                throw TransactionException.submissionFailed(
                    "Deployment transaction error: ${sendResult.errorResultXdr}"
                )
            }

            transactionHash = sendResult.hash
                ?: throw TransactionException.submissionFailed(
                    "No transaction hash returned from submission"
                )
        }

        // Poll for confirmation
        var confirmed = false
        for (attempt in 1..10) {
            delay(2000) // Wait 2 seconds between polls

            val txStatus = try {
                kit.sorobanServer.getTransaction(hash = transactionHash)
            } catch (_: Exception) {
                // Network error, retry
                if (attempt < 10) {
                    continue
                }
                try {
                    credentialManager.markDeploymentFailed(
                        credentialId = credentialIdBase64url,
                        error = "Deployment confirmation timed out"
                    )
                } catch (_: Exception) {}
                throw TransactionException.timeout(
                    "Deployment confirmation timed out"
                )
            }

            when (txStatus.status) {
                GetTransactionStatus.SUCCESS -> {
                    confirmed = true
                }
                GetTransactionStatus.FAILED -> {
                    try {
                        credentialManager.markDeploymentFailed(
                            credentialId = credentialIdBase64url,
                            error = txStatus.resultXdr ?: "Deployment failed on-chain"
                        )
                    } catch (_: Exception) {}
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
            try {
                credentialManager.markDeploymentFailed(
                    credentialId = credentialIdBase64url,
                    error = "Deployment confirmation timed out"
                )
            } catch (_: Exception) {}
            throw TransactionException.timeout(
                "Deployment confirmation timed out"
            )
        }

        return transactionHash
    }

    /**
     * Determines the submission method for deploy transactions.
     *
     * Priority:
     * 1. If [forceMethod] is specified, use it directly
     * 2. If a relayer is configured, use relayer
     * 3. Otherwise, use RPC
     *
     * @param forceMethod Optional override to force a specific submission method
     * @return The submission method to use
     */
    private fun resolveDeploySubmissionMethod(forceMethod: SubmissionMethod?): SubmissionMethod {
        if (forceMethod != null) {
            return forceMethod
        }
        return if (kit.relayerClient != null) {
            SubmissionMethod.RELAYER
        } else {
            SubmissionMethod.RPC
        }
    }
}

/**
 * Polls [isVisible] until the deployed contract instance is visible to the Soroban RPC,
 * the timeout elapses, or the coroutine is cancelled.
 *
 * Used by [OZWalletOperations] after an auto-submitted deploy transaction is included in a
 * ledger, to bridge the gap between the deploy confirming and the Soroban RPC reflecting the
 * new contract-instance entry in its simulation state. The subsequent
 * [OZTransactionOperations.fundWallet] simulates against the contract and fails if the
 * instance is not yet applied to the RPC state. Polling avoids assuming a fixed propagation
 * delay, which fails when testnet propagation is slower than the assumed wait.
 *
 * [isVisible] performs a single structural lookup of the contract-instance ledger entry for
 * the supplied contract ID. It must:
 * - return true once the contract-instance entry is present in the RPC state;
 * - return false while the entry is absent (the expected pre-propagation state), which keeps
 *   polling without recording a cause;
 * - throw any exception for a transient RPC or transport error, which is retried until the
 *   deadline and surfaced as the timeout cause.
 *
 * The total wait is bounded by [timeoutSeconds] (covering both lookups and the interval
 * sleeps) and is cooperatively cancellable: [delay] and the enclosing timeout both observe
 * cancellation, and any [CancellationException] thrown by [isVisible] is rethrown rather than
 * treated as a transient error.
 *
 * @param contractId The deployed contract address (C-address) to wait for, used in the timeout message
 * @param pollIntervalMs Delay between polls in milliseconds
 * @param timeoutSeconds Overall budget in seconds before failing
 * @param isVisible Suspending visibility check with the present / absent / transient-error contract above
 * @throws TransactionException.Timeout if the contract is not visible within the budget
 */
internal suspend fun pollUntilContractVisibleToRpc(
    contractId: String,
    pollIntervalMs: Long = OZConstants.RPC_VISIBILITY_POLL_INTERVAL_MS,
    timeoutSeconds: Int = OZConstants.RPC_VISIBILITY_TIMEOUT_SECONDS,
    isVisible: suspend (String) -> Boolean
) {
    var lastTransientError: Throwable? = null

    val completed = withTimeoutOrNull(timeoutSeconds.toLong() * 1000L) {
        while (true) {
            ensureActive()
            try {
                if (isVisible(contractId)) {
                    return@withTimeoutOrNull
                }
                // Contract instance not yet visible to the RPC (the expected state while
                // the deploy transaction propagates). Keep polling without recording a cause.
            } catch (e: CancellationException) {
                // Cooperative cancellation (including the enclosing timeout): never swallow
                // it as a transient error.
                throw e
            } catch (e: Exception) {
                // Transient RPC/transport error: retry until the deadline and surface the
                // last failure as the timeout cause.
                lastTransientError = e
            }
            delay(pollIntervalMs)
        }
    }

    if (completed == null) {
        throw TransactionException.timeout(
            details = "Deployed contract $contractId not visible to the Soroban RPC within " +
                "${timeoutSeconds}s after deployment; testnet propagation may be delayed. " +
                "Retry shortly",
            cause = lastTransientError
        )
    }
}
