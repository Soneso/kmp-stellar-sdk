//
//  OZSmartAccountConfig.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.crypto.getSha256Crypto

/**
 * Configuration for OpenZeppelin Smart Account operations.
 *
 * This configuration data class defines all parameters required to interact with OpenZeppelin
 * smart accounts on Stellar/Soroban. It includes network connectivity settings, contract
 * addresses, and operational parameters.
 *
 * Example usage:
 * ```kotlin
 * val config = OZSmartAccountConfig(
 *     rpcUrl = "https://soroban-testnet.stellar.org",
 *     networkPassphrase = "Test SDF Network ; September 2015",
 *     accountWasmHash = "abc123...",
 *     webauthnVerifierAddress = "CBCD1234..."
 * )
 *
 * // With custom settings using builder
 * val customConfig = OZSmartAccountConfig.builder(
 *     rpcUrl = "https://soroban-testnet.stellar.org",
 *     networkPassphrase = "Test SDF Network ; September 2015",
 *     accountWasmHash = "abc123...",
 *     webauthnVerifierAddress = "CBCD1234..."
 * )
 *     .rpName("My Custom Wallet")
 *     .sessionExpiryMs(86400000L) // 1 day
 *     .relayerUrl("https://relayer.example.com")
 *     .storage(myPersistentStorage)
 *     .externalWallet(freighterAdapter)
 *     .build()
 * ```
 *
 * | Field | Required | Default |
 * |-------|----------|---------|
 * | rpcUrl | Yes | - |
 * | networkPassphrase | Yes | - |
 * | accountWasmHash | Yes | - |
 * | webauthnVerifierAddress | Yes | - |
 * | deployerKeypair | No | Deterministic deployer |
 * | rpId | No | Browser default |
 * | rpName | No | "Smart Account" |
 * | sessionExpiryMs | No | 604800000 (7 days) |
 * | signatureExpirationLedgers | No | 720 (~1 hour) |
 * | timeoutInSeconds | No | 30 |
 * | relayerUrl | No | null |
 * | indexerUrl | No | null |
 * | webauthnProvider | No | null |
 * | storage | No | InMemoryStorageAdapter |
 * | externalWallet | No | null |
 */
data class OZSmartAccountConfig(
    // Required Configuration

    /**
     * The Soroban RPC endpoint URL.
     *
     * Example: "https://soroban-testnet.stellar.org"
     */
    val rpcUrl: String,

    /**
     * The Stellar network passphrase.
     *
     * Examples:
     * - Testnet: "Test SDF Network ; September 2015"
     * - Mainnet: "Public Global Stellar Network ; September 2015"
     */
    val networkPassphrase: String,

    /**
     * The WASM hash of the smart account contract (hex string).
     *
     * This is the SHA-256 hash of the smart account contract WASM code,
     * used for deploying new smart account instances.
     */
    val accountWasmHash: String,

    /**
     * The contract address of the WebAuthn signature verifier (C-address).
     *
     * This verifier contract validates secp256r1 signatures from WebAuthn/passkeys.
     */
    val webauthnVerifierAddress: String,

    // Optional Configuration with Defaults

    /**
     * The keypair used for deploying smart account contracts.
     *
     * If null, a deterministic deployer is derived from SHA256("openzeppelin-smart-account-kit").
     * Production apps typically use a custom deployer for attribution and traceability.
     *
     * Note: The deployer only pays for deployment transactions. It does not control user wallets.
     */
    val deployerKeypair: KeyPair? = null,

    /**
     * The WebAuthn Relying Party ID (rpId).
     *
     * This should match the domain where WebAuthn credentials are created.
     * If null, the browser will use the current domain.
     *
     * Example: "example.com"
     */
    val rpId: String? = null,

    /**
     * The WebAuthn Relying Party name displayed to users during authentication.
     *
     * Default: "Smart Account"
     */
    val rpName: String = "Smart Account",

    /**
     * Session expiry time in milliseconds.
     *
     * Sessions enable silent reconnection without re-authentication.
     * Default: 604800000 (7 days)
     */
    val sessionExpiryMs: Long = OZConstants.DEFAULT_SESSION_EXPIRY_MS,

    /**
     * Signature expiration in ledgers for auth entries.
     *
     * Auth entries expire after this many ledgers to prevent replay attacks.
     * Default: 720 (approximately 1 hour, since approximately 5 seconds per ledger)
     */
    val signatureExpirationLedgers: Int = Util.LEDGERS_PER_HOUR,

    /**
     * Default timeout for operations in seconds.
     *
     * Used for network requests and transaction submission.
     * Default: 30 seconds
     */
    val timeoutInSeconds: Int = OZConstants.DEFAULT_TIMEOUT_SECONDS,

    /**
     * Optional relayer endpoint URL for fee sponsoring.
     *
     * When set, enables gasless transactions by submitting through a fee-bump relayer.
     * This allows users with empty wallets to transact.
     *
     * Example: "https://relayer.example.com"
     */
    val relayerUrl: String? = null,

    /**
     * Optional indexer endpoint URL for credential-to-contract mapping.
     *
     * The indexer maps WebAuthn credential IDs to deployed smart account contract addresses,
     * enabling "Connect Wallet" functionality where users can discover their wallets.
     *
     * Example: "https://indexer.example.com"
     */
    val indexerUrl: String? = null,

    /**
     * Optional WebAuthn provider for passkey authentication.
     *
     * Platform-specific implementation that handles WebAuthn registration and authentication.
     * This is required for signing transactions with passkeys.
     */
    val webauthnProvider: WebAuthnProvider? = null,

    /**
     * Storage adapter for persisting credentials and session data.
     *
     * Defaults to [InMemoryStorageAdapter] (non-persistent, suitable for testing).
     */
    val storage: StorageAdapter = InMemoryStorageAdapter(),

    /**
     * External wallet adapter for signing transactions with an external signer.
     *
     * When set, the kit delegates transaction signing to this adapter instead of
     * using WebAuthn credentials.
     */
    val externalWallet: ExternalWalletAdapter? = null,

    /**
     * Maximum rule ID to scan when iterating context rules.
     *
     * The contract assigns monotonically increasing IDs to context rules. When rules are
     * removed, their IDs leave gaps. [OZContextRuleManager.getAllContextRules] iterates
     * from ID 0 up to this value to find all active rules. Increase if the account has
     * had many add/remove cycles.
     */
    val maxContextRuleScanId: UInt = 50u
) {
    init {
        // Validate required parameters
        if (rpcUrl.isBlank()) throw ConfigurationException.missingConfig("rpcUrl")

        if (networkPassphrase.isBlank()) throw ConfigurationException.missingConfig("networkPassphrase")

        if (accountWasmHash.isBlank()) throw ConfigurationException.missingConfig("accountWasmHash")

        if (!webauthnVerifierAddress.startsWith("C")) throw ConfigurationException.invalidConfig(
            "webauthnVerifierAddress must start with 'C' (contract address), got: $webauthnVerifierAddress"
        )

        if (webauthnVerifierAddress.length != 56) throw ConfigurationException.invalidConfig(
            "webauthnVerifierAddress must be 56 characters long, got: ${webauthnVerifierAddress.length}"
        )
    }

    companion object {
        /**
         * Creates a deterministic deployer keypair for smart account deployment.
         *
         * Derives a keypair from SHA256("openzeppelin-smart-account-kit"). The same derivation
         * is used by the TypeScript Smart Account Kit, so identical inputs produce identical
         * results across implementations. This keypair only pays deployment fees and does not
         * control user wallets. Suitable for testing and simple deployments; production apps
         * typically use a custom deployer for attribution and traceability.
         *
         * @return A deterministic KeyPair for contract deployment
         * @throws ConfigurationException if seed generation fails
         */
        suspend fun createDefaultDeployer(): KeyPair {
            return try {
                val seedString = "openzeppelin-smart-account-kit"
                val seedHash = getSha256Crypto().hash(seedString.encodeToByteArray())
                KeyPair.fromSecretSeed(seedHash)
            } catch (e: Exception) {
                throw ConfigurationException.invalidConfig(
                    "Failed to create default deployer keypair: ${e.message}",
                    e
                )
            }
        }

        /**
         * Creates a builder for constructing OZSmartAccountConfig with a fluent API.
         *
         * Example:
         * ```kotlin
         * val config = OZSmartAccountConfig.builder(
         *     rpcUrl = "https://soroban-testnet.stellar.org",
         *     networkPassphrase = "Test SDF Network ; September 2015",
         *     accountWasmHash = "abc123...",
         *     webauthnVerifierAddress = "CBCD1234..."
         * )
         *     .rpName("My Wallet")
         *     .sessionExpiryMs(86400000L)
         *     .relayerUrl("https://relayer.example.com")
         *     .storage(myPersistentStorage)
         *     .externalWallet(freighterAdapter)
         *     .build()
         * ```
         *
         * @param rpcUrl The Soroban RPC endpoint URL
         * @param networkPassphrase The Stellar network passphrase
         * @param accountWasmHash The smart account contract WASM hash
         * @param webauthnVerifierAddress The WebAuthn verifier contract address
         * @return A new Builder instance
         */
        fun builder(
            rpcUrl: String,
            networkPassphrase: String,
            accountWasmHash: String,
            webauthnVerifierAddress: String
        ): Builder = Builder(rpcUrl, networkPassphrase, accountWasmHash, webauthnVerifierAddress)
    }

    /**
     * Returns the deployer keypair, creating the default if needed.
     *
     * @return The configured deployer or the default deterministic deployer
     * @throws ConfigurationException if default deployer creation fails
     */
    suspend fun getDeployer(): KeyPair {
        return deployerKeypair ?: createDefaultDeployer()
    }

    /**
     * Returns the effective indexer URL for this configuration.
     *
     * If an indexer URL is explicitly configured, it is returned.
     * Otherwise, returns the default indexer URL for the network passphrase.
     *
     * @return The indexer URL to use, or null if no URL is configured and no default exists
     */
    fun effectiveIndexerUrl(): String? {
        return indexerUrl ?: OZIndexerClient.getDefaultUrl(networkPassphrase)
    }

    /**
     * Creates an OZIndexerClient using the effective indexer URL.
     *
     * Uses the explicitly configured indexer URL if available, otherwise falls back
     * to the default URL for the network passphrase.
     *
     * @param timeoutMs Optional request timeout in milliseconds
     * @return An OZIndexerClient instance, or null if no indexer URL is available
     *
     * Example:
     * ```kotlin
     * val client = config.createIndexerClient()
     * if (client != null) {
     *     val contracts = client.lookupByCredentialId(credentialId)
     * }
     * ```
     */
    fun createIndexerClient(timeoutMs: Long = OZConstants.DEFAULT_INDEXER_TIMEOUT_MS): OZIndexerClient? {
        val url = effectiveIndexerUrl() ?: return null
        return OZIndexerClient(url, timeoutMs)
    }

    /**
     * Builder for creating OZSmartAccountConfig with a fluent API.
     *
     * Example:
     * ```kotlin
     * val config = OZSmartAccountConfig.builder(
     *     rpcUrl = "https://soroban-testnet.stellar.org",
     *     networkPassphrase = "Test SDF Network ; September 2015",
     *     accountWasmHash = "abc123...",
     *     webauthnVerifierAddress = "CBCD1234..."
     * )
     *     .rpName("My Wallet")
     *     .sessionExpiryMs(86400000L)
     *     .relayerUrl("https://relayer.example.com")
     *     .storage(myPersistentStorage)
     *     .externalWallet(freighterAdapter)
     *     .build()
     * ```
     */
    class Builder(
        private val rpcUrl: String,
        private val networkPassphrase: String,
        private val accountWasmHash: String,
        private val webauthnVerifierAddress: String
    ) {
        private var deployerKeypair: KeyPair? = null
        private var rpId: String? = null
        private var rpName: String = "Smart Account"
        private var sessionExpiryMs: Long = OZConstants.DEFAULT_SESSION_EXPIRY_MS
        private var signatureExpirationLedgers: Int = Util.LEDGERS_PER_HOUR
        private var timeoutInSeconds: Int = OZConstants.DEFAULT_TIMEOUT_SECONDS
        private var relayerUrl: String? = null
        private var indexerUrl: String? = null
        private var webauthnProvider: WebAuthnProvider? = null
        private var storage: StorageAdapter = InMemoryStorageAdapter()
        private var externalWallet: ExternalWalletAdapter? = null

        /**
         * Sets the deployer keypair.
         *
         * @param value The deployer keypair (null to use default)
         * @return This builder for chaining
         */
        fun deployerKeypair(value: KeyPair?): Builder {
            deployerKeypair = value
            return this
        }

        /**
         * Sets the WebAuthn Relying Party ID.
         *
         * @param value The rpId (null to use browser default)
         * @return This builder for chaining
         */
        fun rpId(value: String?): Builder {
            rpId = value
            return this
        }

        /**
         * Sets the WebAuthn Relying Party name.
         *
         * @param value The rpName
         * @return This builder for chaining
         */
        fun rpName(value: String): Builder {
            rpName = value
            return this
        }

        /**
         * Sets the session expiry in milliseconds.
         *
         * @param value The session expiry duration
         * @return This builder for chaining
         */
        fun sessionExpiryMs(value: Long): Builder {
            sessionExpiryMs = value
            return this
        }

        /**
         * Sets the signature expiration in ledgers.
         *
         * @param value The signature expiration ledgers
         * @return This builder for chaining
         */
        fun signatureExpirationLedgers(value: Int): Builder {
            signatureExpirationLedgers = value
            return this
        }

        /**
         * Sets the operation timeout in seconds.
         *
         * @param value The timeout in seconds
         * @return This builder for chaining
         */
        fun timeoutInSeconds(value: Int): Builder {
            timeoutInSeconds = value
            return this
        }

        /**
         * Sets the relayer URL.
         *
         * @param value The relayer endpoint URL (null to disable)
         * @return This builder for chaining
         */
        fun relayerUrl(value: String?): Builder {
            relayerUrl = value
            return this
        }

        /**
         * Sets the indexer URL.
         *
         * @param value The indexer endpoint URL (null to disable)
         * @return This builder for chaining
         */
        fun indexerUrl(value: String?): Builder {
            indexerUrl = value
            return this
        }

        /**
         * Sets the WebAuthn provider.
         *
         * @param webauthnProvider The WebAuthn provider (null to disable passkey support)
         * @return This builder for chaining
         */
        fun webauthnProvider(webauthnProvider: WebAuthnProvider?) = apply { this.webauthnProvider = webauthnProvider }

        /**
         * Sets the storage adapter.
         *
         * @param storage The storage adapter for persisting credentials and sessions
         * @return This builder for chaining
         */
        fun storage(storage: StorageAdapter) = apply { this.storage = storage }

        /**
         * Sets the external wallet adapter.
         *
         * @param externalWallet The external wallet adapter (null to disable external signing)
         * @return This builder for chaining
         */
        fun externalWallet(externalWallet: ExternalWalletAdapter?) = apply { this.externalWallet = externalWallet }

        /**
         * Builds the OZSmartAccountConfig.
         *
         * @return A new OZSmartAccountConfig instance
         * @throws ConfigurationException if validation fails
         */
        fun build(): OZSmartAccountConfig {
            return OZSmartAccountConfig(
                rpcUrl = rpcUrl,
                networkPassphrase = networkPassphrase,
                accountWasmHash = accountWasmHash,
                webauthnVerifierAddress = webauthnVerifierAddress,
                deployerKeypair = deployerKeypair,
                rpId = rpId,
                rpName = rpName,
                sessionExpiryMs = sessionExpiryMs,
                signatureExpirationLedgers = signatureExpirationLedgers,
                timeoutInSeconds = timeoutInSeconds,
                relayerUrl = relayerUrl,
                indexerUrl = indexerUrl,
                webauthnProvider = webauthnProvider,
                storage = storage,
                externalWallet = externalWallet
            )
        }
    }
}
