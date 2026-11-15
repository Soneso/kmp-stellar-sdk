//
//  OZSmartAccountKit.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.addressCredentials
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.ContractDataDurabilityXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyContractDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
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
     * A connection is defined by the smart-account contract; the credential is optional and is
     * absent for a headless [walletOperations].connectToContract() connection. This property
     * reflects in-memory state only. After an app restart, call
     * [walletOperations].connectWallet() to restore a saved session.
     */
    val isConnected: Boolean
        get() = _contractId != null

    /**
     * Indicates whether the current connection is headless: bound to a smart-account contract
     * with no passkey credential.
     *
     * `true` only for a connection established through
     * [walletOperations].connectToContract(). Headless connections are operable only through
     * the multi-signer / external-signer pipeline; the single-passkey paths reject them.
     */
    val isHeadless: Boolean
        get() = _contractId != null && _credentialId == null

    /**
     * The credential ID of the currently connected wallet, when one is present.
     *
     * Returns null when no wallet is connected, and also for a headless
     * [walletOperations].connectToContract() connection, which binds a contract without a
     * passkey credential. Use [isHeadless] to tell a headless connection apart from no
     * connection. When present, the credential ID is Base64URL-encoded without padding,
     * matching the WebAuthn specification.
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
     * @param credentialId The Base64URL-encoded credential ID, or null for a headless
     *   connection bound to the contract alone.
     * @param contractId The smart account contract address (C-address)
     */
    internal suspend fun setConnectedState(credentialId: String?, contractId: String) {
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
     * Closes this kit and releases all held resources: the Soroban RPC, indexer, and
     * relayer HTTP clients, plus in-memory state (event listeners and registered
     * signing keys).
     *
     * This method does not clear the connection state or stored session. Call [disconnect]
     * before [close] if you also want to end the session. The kit must not be used after
     * calling this method.
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
     * It provides a consistent error message and atomic access to the credential ID
     * and contract ID. A connection is defined by the contract alone, so the returned
     * credential ID is null for a headless connection.
     *
     * @return A pair containing the (nullable) credential ID and the non-null contract ID
     * @throws WalletException.NotConnected if no wallet is connected
     *
     * Example usage in operation modules:
     * ```kotlin
     * val (credentialId, contractId) = kit.requireConnected()
     * // Proceed with operation using credentialId and contractId
     * ```
     */
    internal suspend fun requireConnected(): Pair<String?, String> {
        return stateLock.withLock {
            val cId = _credentialId
            val ctId = _contractId
            if (ctId == null) {
                throw WalletException.notConnected(
                    "No wallet connected. Call createWallet() or connectWallet() first."
                )
            }
            Pair(cId, ctId)
        }
    }

    /**
     * Prepares one signing operation over the simulated [authEntries] for the smart account
     * [contractId]. Both signing pipelines call it once, before they ask any signer.
     *
     * For each auth entry of the smart account, validates the signers its invocation tree adds
     * to the wallet ([requireValidAddedSigners]). With at least one such entry, reads the
     * wallet's instance once ([resolveAuthDigestScheme]) and takes the signature expiration
     * from the latest ledger of that response; otherwise reads the latest ledger alone and
     * makes no instance read.
     *
     * @param contractId The connected smart account (C-address)
     * @param authEntries The auth entries the simulation returned
     * @return The digest scheme of the wallet (null without a smart account entry) and the
     *   ledger at which the signatures expire
     * @throws ValidationException if an added signer is refused
     * @throws TransactionException.SigningFailed if an added signer is malformed or the
     *   instance entry cannot be read as a contract instance
     */
    internal suspend fun prepareSigningOperation(
        contractId: String,
        authEntries: List<SorobanAuthorizationEntryXdr>
    ): SigningOperationState {
        val walletEntries = authEntries.filter { entry ->
            val credentials = entry.credentials.addressCredentials() ?: return@filter false
            val entryAddress = try { Address.fromSCAddress(credentials.address).toString() } catch (_: Exception) { null }
            entryAddress == contractId
        }
        for (entry in walletEntries) {
            requireValidAddedSigners(entry, contractId)
        }

        val (scheme, latestLedger) = if (walletEntries.isNotEmpty()) {
            val read = resolveAuthDigestScheme(contractId)
            read.scheme to read.latestLedger
        } else {
            null to sorobanServer.getLatestLedger().sequence
        }
        return SigningOperationState(
            authDigestScheme = scheme,
            expirationLedger = latestLedger.toUInt() + config.signatureExpirationLedgers.toUInt()
        )
    }

    /**
     * Reads the WASM hash the smart account [contractId] runs and maps it to its digest scheme
     * with [OZSmartAccountConfig.authDigestSchemeFor].
     *
     * One `getLedgerEntries` request for the persistent contract instance entry. A WASM
     * executable maps through the configured and built-in tables; an external reference or a
     * missing entry signs with [AuthDigestScheme.LEGACY_CONCAT]. RPC errors propagate.
     *
     * @param contractId The smart account (C-address)
     * @return The scheme and the latest ledger of the response
     * @throws TransactionException.SigningFailed if the returned entry is not a contract instance
     */
    internal suspend fun resolveAuthDigestScheme(contractId: String): AuthDigestSchemeRead {
        val instanceKey = LedgerKeyXdr.ContractData(
            LedgerKeyContractDataXdr(
                contract = Address(contractId).toSCAddress(),
                key = Scv.toLedgerKeyContractInstance(),
                durability = ContractDataDurabilityXdr.PERSISTENT
            )
        )
        val response = sorobanServer.getLedgerEntries(listOf(instanceKey))
        val entry = response.entries?.firstOrNull()
            ?: return AuthDigestSchemeRead(AuthDigestScheme.LEGACY_CONCAT, response.latestLedger)

        val data = try {
            entry.parseXdr()
        } catch (e: IllegalArgumentException) {
            throw TransactionException.signingFailed(
                "Failed to decode the instance entry of $contractId to read its code: ${e.message}",
                e
            )
        }
        val instance = when (data) {
            is LedgerEntryDataXdr.ContractData -> (data.value.`val` as? SCValXdr.Instance)?.value
            else -> null
        } ?: throw TransactionException.signingFailed(
            "The instance entry of $contractId is not a contract instance (${data.discriminant})"
        )
        val scheme = when (val executable = instance.executable) {
            is ContractExecutableXdr.WasmHash -> config.authDigestSchemeFor(Util.bytesToHex(executable.value.value))
            is ContractExecutableXdr.ExternalRef, ContractExecutableXdr.Void -> AuthDigestScheme.LEGACY_CONCAT
        }
        return AuthDigestSchemeRead(scheme, response.latestLedger)
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
     * The deployer's G-address is needed to fund it externally on networks without Friendbot.
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
         * Creates a kit with a pre-built [SorobanServer] and optional pre-built relayer and
         * indexer clients, used in tests to inject mock HTTP engines. Not intended for
         * production use.
         */
        internal fun createWithServer(
            config: OZSmartAccountConfig,
            sorobanServer: SorobanServer,
            relayerClient: OZRelayerClient? = null,
            indexerClient: OZIndexerClient? = null
        ): OZSmartAccountKit = OZSmartAccountKit(
            config = config,
            storage = config.storage,
            relayerClient = relayerClient,
            indexerClient = indexerClient,
            externalSigners = OZExternalSignerManager(
                networkPassphrase = config.networkPassphrase,
                walletAdapter = config.externalWallet,
                ed25519Adapter = config.externalEd25519Adapter,
            ),
            sorobanServer = sorobanServer
        )
    }
}

/**
 * The state one signing operation signs against, read before any signer is asked.
 *
 * @property authDigestScheme The digest scheme of the wallet's code, or null when no auth entry
 *   belongs to the wallet and no instance read was made
 * @property expirationLedger The ledger at which the signatures expire
 */
internal data class SigningOperationState(
    val authDigestScheme: AuthDigestScheme?,
    val expirationLedger: UInt
) {
    /**
     * The digest scheme an auth entry of the smart account [contractId] signs with.
     *
     * @throws IllegalStateException if the state was prepared without an entry of [contractId],
     *   so no scheme was read
     */
    fun schemeForWalletEntry(contractId: String): AuthDigestScheme = checkNotNull(authDigestScheme) {
        "No digest scheme was resolved for the auth entry of $contractId"
    }
}

/**
 * The result of one instance read of a smart account.
 *
 * @property scheme The digest scheme the wallet's code checks
 * @property latestLedger The latest ledger the RPC reported with the read
 */
internal data class AuthDigestSchemeRead(
    val scheme: AuthDigestScheme,
    val latestLedger: Long
)
