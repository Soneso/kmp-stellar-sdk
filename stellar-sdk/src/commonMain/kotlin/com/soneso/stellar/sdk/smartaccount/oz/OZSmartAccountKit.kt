//
//  OZSmartAccountKit.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.rpc.SorobanServer
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.withLock

/**
 * Main orchestrator for OpenZeppelin Smart Account operations on Stellar/Soroban.
 *
 * OZSmartAccountKit is the primary entry point for creating and managing smart accounts
 * with WebAuthn/passkey authentication. It provides a high-level interface for:
 *
 * - Creating new smart account wallets with biometric authentication
 * - Connecting to existing wallets via credential discovery
 * - Managing wallet sessions and credentials
 * - Submitting transactions with WebAuthn signatures
 * - Interacting with signers, policies, and context rules
 *
 * The kit orchestrates multiple components:
 * - Storage adapter for credential persistence
 * - Soroban RPC server for blockchain interaction
 * - Relayer client for fee-sponsored transactions (optional)
 * - Indexer client for credential-to-contract discovery (optional)
 *
 * Example usage:
 * ```kotlin
 * // Initialize the kit
 * val config = OZSmartAccountConfig(
 *     rpcUrl = "https://soroban-testnet.stellar.org",
 *     networkPassphrase = "Test SDF Network ; September 2015",
 *     accountWasmHash = "abc123...",
 *     webauthnVerifierAddress = "CBCD1234...",
 *     relayerUrl = "https://relayer.example.com",
 *     indexerUrl = "https://indexer.example.com"
 * )
 * val kit = OZSmartAccountKit.create(config)
 *
 * // Create a new wallet (prompts for biometric authentication)
 * val wallet = kit.walletOperations.createWallet(userName = "My Wallet")
 * println("Created wallet: ${wallet.contractId}")
 *
 * // Silent session check (returns null if no valid session)
 * val existing = kit.walletOperations.connectWallet()
 * if (existing != null) {
 *     println("Connected to: ${existing.contractId}")
 * }
 *
 * // Session check with WebAuthn fallback if no session
 * val connected = kit.walletOperations.connectWallet(
 *     OZWalletOperations.ConnectWalletOptions(prompt = true)
 * )
 * println("Connected to: ${connected?.contractId}")
 *
 * // Explicit connect with fresh WebAuthn prompt (skips session restore)
 * val fresh = kit.walletOperations.connectWallet(
 *     OZWalletOperations.ConnectWalletOptions(fresh = true)
 * )
 * println("Authenticated and connected to: ${fresh?.contractId}")
 *
 * // Disconnect
 * kit.disconnect()
 * ```
 *
 * Thread Safety:
 * This class is thread-safe. All internal state is protected by a Mutex.
 * Public methods can be safely called from any coroutine context.
 */
class OZSmartAccountKit private constructor(
    // MARK: - Configuration

    /**
     * The configuration defining network endpoints, contract addresses, and operational parameters.
     */
    val config: OZSmartAccountConfig,

    // MARK: - Internal Components

    /**
     * Storage adapter for persisting credentials and sessions.
     */
    private val storage: StorageAdapter,

    /**
     * Optional relayer client for fee-sponsored transaction submission.
     * Null when no relayer URL is configured.
     */
    val relayerClient: OZRelayerClient?,

    /**
     * Optional indexer client for credential-to-contract discovery.
     * Null when no indexer URL is configured.
     */
    val indexerClient: OZIndexerClient?,

    /**
     * The kit-owned [OZExternalSignerManager] — the single front door for all external
     * (non-passkey) signers.
     *
     * The manager is constructed by the factories from [config], injecting the wallet adapter
     * ([OZSmartAccountConfig.externalWallet]) and the Ed25519 adapter
     * ([OZSmartAccountConfig.externalEd25519Adapter]). Use it to register in-memory keypairs and to
     * query signing capability before submitting multi-signer operations.
     *
     * Example:
     * ```kotlin
     * val config = OZSmartAccountConfig.builder(...)
     *     .externalEd25519Adapter(myHardwareAdapter)
     *     .build()
     * val kit = OZSmartAccountKit.create(config)
     *
     * // Register an in-memory Ed25519 signer at runtime
     * kit.externalSigners.addEd25519FromRawKey(secretKeyBytes, verifierAddress)
     *
     * // Register an in-memory wallet (G-address) signer at runtime
     * kit.externalSigners.addFromSecret("S...")
     * ```
     */
    val externalSigners: OZExternalSignerManager,

    /**
     * Soroban RPC server shared by all operation managers. Closed by [close].
     */
    internal val sorobanServer: SorobanServer
) {
    // MARK: - Connection State

    /**
     * Currently connected credential ID (Base64URL-encoded).
     * Volatile for thread-safe reads from non-suspend contexts (e.g., isConnected).
     */
    @Volatile
    private var _credentialId: String? = null

    /**
     * Currently connected smart account contract address (C-address).
     * Volatile for thread-safe reads from non-suspend contexts (e.g., isConnected).
     */
    @Volatile
    private var _contractId: String? = null

    /**
     * Mutex for thread-safe state access.
     */
    private val stateLock = Mutex()

    // MARK: - Events

    /**
     * Event emitter for wallet lifecycle events.
     *
     * Subscribe to events to monitor wallet creation, connection, transactions,
     * and credential lifecycle operations.
     *
     * Example:
     * ```kotlin
     * kit.events.on<SmartAccountEvent.WalletConnected> { event ->
     *     println("Connected to ${event.contractId}")
     * }
     * ```
     */
    val events: SmartAccountEventEmitter = SmartAccountEventEmitter()

    // MARK: - Sub-Managers

    /**
     * Manages wallet operations (creation, connection, disconnection).
     */
    val walletOperations: OZWalletOperations by lazy { OZWalletOperations(this) }

    /**
     * Manages transaction operations (building, signing, submitting).
     */
    val transactionOperations: OZTransactionOperations by lazy { OZTransactionOperations(this) }

    /**
     * Manages signer operations (adding, removing, querying signers).
     */
    val signerManager: OZSignerManager by lazy { OZSignerManager(this) }

    /**
     * Manages context rule operations (creating, updating, removing rules).
     */
    val contextRuleManager: OZContextRuleManager by lazy { OZContextRuleManager(this) }

    /**
     * Manages policy operations (installing, removing, querying policies).
     */
    val policyManager: OZPolicyManager by lazy { OZPolicyManager(this) }

    /**
     * Manages multi-signer operations (coordinating multiple signers).
     */
    val multiSignerManager: OZMultiSignerManager by lazy { OZMultiSignerManager(this) }

    /**
     * Manages credential operations (storing, retrieving, updating credentials).
     */
    val credentialManager: OZCredentialManager by lazy { OZCredentialManager(this) }

    // MARK: - Public State Accessors

    /**
     * Indicates whether a wallet is currently connected.
     *
     * A wallet is connected when both the credential ID and contract ID are set.
     * This property reflects in-memory state only. After an app restart, call
     * [walletOperations].connectWallet() to restore a saved session.
     */
    val isConnected: Boolean
        get() = _credentialId != null && _contractId != null

    /**
     * The credential ID of the currently connected wallet.
     *
     * Returns null if no wallet is connected. The credential ID is Base64URL-encoded
     * without padding, matching the WebAuthn specification.
     */
    val credentialId: String?
        get() = _credentialId

    /**
     * The contract address of the currently connected wallet.
     *
     * Returns null if no wallet is connected. The contract ID is a Stellar C-address
     * (56 characters, starting with 'C').
     */
    val contractId: String?
        get() = _contractId

    // MARK: - Connection Management

    /**
     * Sets the connected wallet state.
     *
     * This method is called by wallet operation modules after successful wallet
     * creation or connection. It updates the in-memory state with the provided
     * credential ID and contract ID.
     *
     * Thread-safe: This method can be called from any coroutine.
     *
     * @param credentialId The Base64URL-encoded credential ID
     * @param contractId The smart account contract address (C-address)
     */
    internal suspend fun setConnectedState(credentialId: String, contractId: String) {
        stateLock.withLock {
            _credentialId = credentialId
            _contractId = contractId
        }
    }

    /**
     * Disconnects the currently connected wallet.
     *
     * Clears the in-memory connection state (credential ID and contract ID) and
     * removes the stored session. The stored credentials remain in storage and
     * can be reconnected later. Emits a [SmartAccountEvent.WalletDisconnected]
     * event if a wallet was connected at the time of the call.
     *
     * This method is safe to call even if no wallet is connected.
     *
     * Example:
     * ```kotlin
     * kit.disconnect()
     * println("Disconnected. isConnected: ${kit.isConnected}") // false
     * ```
     */
    suspend fun disconnect() {
        val contractIdToEmit = stateLock.withLock {
            val currentContractId = _contractId
            _credentialId = null
            _contractId = null
            currentContractId
        }
        storage.clearSession()

        // Emit wallet disconnected event (if a wallet was connected)
        contractIdToEmit?.let { cId ->
            events.emit(SmartAccountEvent.WalletDisconnected(contractId = cId))
        }
    }

    // MARK: - Resource Management

    /**
     * Closes this kit, releases all held HTTP client resources, removes all event
     * listeners, and drops in-memory signing secrets.
     *
     * Closes the Soroban RPC server connection and the indexer and relayer HTTP clients
     * if present. All listeners registered on [events] are removed so that subscriber
     * objects do not stay reachable through the kit. In-memory external signers (keypairs
     * registered via [OZExternalSignerManager.addFromSecret] and Ed25519 keys registered
     * via [OZExternalSignerManager.addEd25519FromRawKey]) are cleared so that raw key
     * material does not outlive the kit; the external wallet adapter is left intact.
     *
     * This method does not clear the connection state or stored session. Call [disconnect]
     * before [close] if you also want to end the session. The kit must not be used after
     * calling this method: the manager properties remain accessible, but any operation on
     * them fails because the underlying HTTP clients are closed.
     *
     * Example:
     * ```kotlin
     * val kit = OZSmartAccountKit.create(config)
     * try {
     *     // use kit
     * } finally {
     *     kit.close()
     * }
     * ```
     */
    fun close() {
        sorobanServer.close()
        indexerClient?.close()
        relayerClient?.close()

        // Drop subscriber references so listener closures do not keep consumer
        // objects reachable through the kit.
        events.removeAllListeners()

        // Drop in-memory signing secrets (registered keypairs / Ed25519 keys). The
        // external-wallet adapter is intentionally left intact.
        externalSigners.clearInMemorySigners()
    }

    // MARK: - Internal Helpers

    /**
     * Requires that a wallet is currently connected, throwing an error if not.
     *
     * This helper method is used by operations that require an active connection.
     * It provides a consistent error message and atomic access to both credential ID
     * and contract ID.
     *
     * @return A pair containing the credential ID and contract ID
     * @throws WalletException.NotConnected if no wallet is connected
     *
     * Example usage in operation modules:
     * ```kotlin
     * val (credentialId, contractId) = kit.requireConnected()
     * // Proceed with operation using credentialId and contractId
     * ```
     */
    internal suspend fun requireConnected(): Pair<String, String> {
        return stateLock.withLock {
            val cId = _credentialId
            val ctId = _contractId
            if (cId == null || ctId == null) {
                throw WalletException.notConnected(
                    "No wallet connected. Call createWallet() or connectWallet() first."
                )
            }
            Pair(cId, ctId)
        }
    }

    /** Cached deployer keypair to avoid repeated creation. See [getDeployer]. */
    private var cachedDeployer: KeyPair? = null

    /**
     * Returns the deployer keypair, resolving to the default if not explicitly configured.
     * The result is cached after the first call to avoid repeated keypair creation.
     * The cache is not synchronized; concurrent callers may redundantly create the
     * deployer, but the result is deterministic and idempotent.
     *
     * The deployer keypair is used for deploying smart account contracts. If no deployer
     * was provided in the configuration, a deterministic deployer is derived from
     * SHA256("openzeppelin-smart-account-kit") for deterministic address derivation.
     *
     * Note: The deployer only pays for deployment transactions. It does not control user wallets.
     * Use this accessor to obtain the deployer's G-address when funding it externally on
     * networks without Friendbot.
     *
     * @return The configured or default deployer keypair
     * @throws ConfigurationException if default deployer creation fails
     */
    suspend fun getDeployer(): KeyPair {
        return cachedDeployer ?: config.effectiveDeployer().also { cachedDeployer = it }
    }

    /**
     * Provides access to the storage adapter for credential persistence.
     *
     * Used by operation modules to save, retrieve, update, and delete credentials
     * and sessions.
     */
    internal fun getStorage(): StorageAdapter = storage

    companion object {
        /**
         * Creates a new OZSmartAccountKit instance.
         *
         * Initializes all required components including:
         * - Soroban RPC server connection
         * - Storage adapter (from config, defaults to in-memory)
         * - Relayer client (if relayerUrl is configured)
         * - Indexer client (if indexerUrl is configured)
         *
         * This factory method does not perform any network requests or load saved sessions.
         * Call the wallet operations' `connectWallet()` separately if you want to restore
         * a previous connection.
         *
         * @param config The configuration for smart account operations
         * @return A new OZSmartAccountKit instance
         * @throws ConfigurationException if the configuration is invalid or required fields are missing
         *
         * Example:
         * ```kotlin
         * val config = OZSmartAccountConfig(
         *     rpcUrl = "https://soroban-testnet.stellar.org",
         *     networkPassphrase = "Test SDF Network ; September 2015",
         *     accountWasmHash = "abc123...",
         *     webauthnVerifierAddress = "CBCD1234..."
         * )
         * val kit = OZSmartAccountKit.create(config)
         * ```
         */
        fun create(config: OZSmartAccountConfig): OZSmartAccountKit {
            // Initialize relayer client if configured
            val relayerClient = config.relayerUrl?.let { url ->
                OZRelayerClient(
                    relayerUrl = url,
                    timeoutMs = OZConstants.DEFAULT_RELAYER_TIMEOUT_MS
                )
            }

            // Initialize indexer client if configured or if a default URL exists for the network
            val indexerClient = config.effectiveIndexerUrl()?.let { url ->
                OZIndexerClient(
                    indexerUrl = url,
                    timeoutMs = OZConstants.DEFAULT_INDEXER_TIMEOUT_MS
                )
            }

            return OZSmartAccountKit(
                config = config,
                storage = config.storage,
                relayerClient = relayerClient,
                indexerClient = indexerClient,
                externalSigners = OZExternalSignerManager(
                    networkPassphrase = config.networkPassphrase,
                    walletAdapter = config.externalWallet,
                    ed25519Adapter = config.externalEd25519Adapter,
                ),
                sorobanServer = SorobanServer(config.rpcUrl)
            )
        }

        /**
         * Creates a kit with a pre-built [SorobanServer], used in tests to inject a
         * mock HTTP engine. Not intended for production use.
         */
        internal fun createWithServer(
            config: OZSmartAccountConfig,
            sorobanServer: SorobanServer
        ): OZSmartAccountKit = OZSmartAccountKit(
            config = config,
            storage = config.storage,
            relayerClient = null,
            indexerClient = null,
            externalSigners = OZExternalSignerManager(
                networkPassphrase = config.networkPassphrase,
                walletAdapter = config.externalWallet,
                ed25519Adapter = config.externalEd25519Adapter,
            ),
            sorobanServer = sorobanServer
        )
    }
}
