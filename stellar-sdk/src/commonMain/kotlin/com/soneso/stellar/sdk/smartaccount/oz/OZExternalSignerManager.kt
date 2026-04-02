//
//  OZExternalSignerManager.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz

import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.currentTimeMillis
import com.soneso.stellar.sdk.crypto.getSha256Crypto
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// MARK: - External Signer Type

/**
 * The type of an external signer managed by [OZExternalSignerManager].
 */
enum class ExternalSignerType {
    /**
     * Ed25519 keypair-based signer. Stored in memory only, never persisted.
     */
    KEYPAIR,

    /**
     * External wallet signer (e.g., Freighter, LOBSTR). Connection info
     * can be persisted to storage for session restoration.
     */
    WALLET
}

// MARK: - External Signer Info

/**
 * Information about a managed external signer.
 *
 * Represents either a keypair-based signer (in-memory Ed25519 key) or a wallet-based
 * signer (external wallet connection). Used by [OZExternalSignerManager.getAll] and
 * [OZExternalSignerManager.get] to report signer details.
 *
 * Example:
 * ```kotlin
 * val signers = externalSignerManager.getAll()
 * for (signer in signers) {
 *     println("${signer.address} (${signer.type})")
 *     if (signer.type == ExternalSignerType.WALLET) {
 *         println("  Wallet: ${signer.walletName}")
 *     }
 * }
 * ```
 *
 * @property address The Stellar G-address of the signer
 * @property type Whether this signer is a keypair or wallet
 * @property walletName Human-readable wallet name (only for WALLET type)
 * @property walletId Wallet identifier for reconnection (only for WALLET type)
 */
data class ExternalSignerInfo(
    val address: String,
    val type: ExternalSignerType,
    val walletName: String? = null,
    val walletId: String? = null
)

// MARK: - Wallet Connection Storage

/**
 * Simple key-value storage interface for persisting external wallet connections.
 *
 * Implementations must be thread-safe. Platform-specific implementations can use
 * SharedPreferences (Android), UserDefaults (iOS), localStorage (Web), or any
 * other persistent key-value store.
 *
 * Example implementation:
 * ```kotlin
 * class LocalStorageWalletConnectionStorage : WalletConnectionStorage {
 *     override suspend fun getItem(key: String): String? = localStorage.getItem(key)
 *     override suspend fun setItem(key: String, value: String) = localStorage.setItem(key, value)
 *     override suspend fun removeItem(key: String) = localStorage.removeItem(key)
 * }
 * ```
 */
interface WalletConnectionStorage {
    /**
     * Retrieves a value by key.
     *
     * @param key The storage key
     * @return The stored value, or null if the key does not exist
     */
    suspend fun getItem(key: String): String?

    /**
     * Stores a value for a key. Overwrites any existing value.
     *
     * @param key The storage key
     * @param value The value to store
     */
    suspend fun setItem(key: String, value: String)

    /**
     * Removes a value by key. No-op if the key does not exist.
     *
     * @param key The storage key to remove
     */
    suspend fun removeItem(key: String)
}

/**
 * In-memory implementation of [WalletConnectionStorage].
 *
 * Data is not persisted across application restarts. Use platform-specific
 * implementations for persistent storage.
 */
class InMemoryWalletConnectionStorage : WalletConnectionStorage {
    private val data = mutableMapOf<String, String>()
    private val mutex = Mutex()

    override suspend fun getItem(key: String): String? = mutex.withLock {
        data[key]
    }

    override suspend fun setItem(key: String, value: String): Unit = mutex.withLock {
        data[key] = value
    }

    override suspend fun removeItem(key: String): Unit = mutex.withLock {
        data.remove(key)
        Unit
    }
}

// MARK: - External Signer Manager

/**
 * Storage key for persisted wallet connections in [WalletConnectionStorage].
 */
private const val WALLET_STORAGE_KEY = "external_wallets"

/**
 * Manager for external (non-passkey) signers for multi-signature smart account operations.
 *
 * OZExternalSignerManager provides a unified interface for managing Stellar account signers
 * that originate from Ed25519 secret keys or external wallet connections (e.g., Freighter,
 * LOBSTR). It supports two methods of adding signers:
 *
 * 1. **Keypair signers** (via [addFromSecret]): Created from a raw Ed25519 secret key.
 *    These are held in memory only and are never persisted to storage. The secret key
 *    material is accessible only through the in-memory KeyPair instance.
 *
 * 2. **Wallet signers** (via [addFromWallet]): Connected through an [ExternalWalletAdapter].
 *    Wallet connection metadata (address, wallet ID, wallet name) is persisted to
 *    [WalletConnectionStorage] so that connections can be restored across app launches
 *    via [restoreConnections].
 *
 * Thread Safety:
 * All mutable state is protected by a [Mutex]. Public methods can be safely called from
 * any coroutine context.
 *
 * Example usage:
 * ```kotlin
 * val manager = OZExternalSignerManager(
 *     networkPassphrase = "Test SDF Network ; September 2015",
 *     walletAdapter = myWalletAdapter,
 *     walletConnectionStorage = myStorage
 * )
 *
 * // Add from secret key (memory-only)
 * val address = manager.addFromSecret("SCZANGBA5YHT...")
 * println("Added keypair signer: $address")
 *
 * // Add from external wallet
 * val wallet = manager.addFromWallet()
 * if (wallet != null) {
 *     println("Connected wallet: ${wallet.walletName} (${wallet.address})")
 * }
 *
 * // Check signing capability
 * if (manager.canSignFor("GABC...")) {
 *     val result = manager.signAuthEntry("GABC...", preimageXdr)
 *     println("Signed by: ${result.signerAddress}")
 * }
 *
 * // List all signers
 * val signers = manager.getAll()
 * ```
 */
class OZExternalSignerManager(
    private val networkPassphrase: String,
    private val walletAdapter: ExternalWalletAdapter? = null,
    private val walletConnectionStorage: WalletConnectionStorage? = null
) {
    // MARK: - Internal State

    /**
     * Keypair-based signers keyed by G-address.
     * Memory-only, never persisted.
     */
    private val keypairSigners = mutableMapOf<String, KeyPair>()

    /**
     * Whether [restoreConnections] has been called.
     * Prevents duplicate restoration attempts.
     */
    private var restored = false

    /**
     * Mutex protecting all mutable state.
     */
    private val mutex = Mutex()

    // MARK: - Wallet Adapter Access

    /**
     * Whether an external wallet adapter is configured.
     *
     * Returns true if the manager was initialized with a non-null [ExternalWalletAdapter].
     * Wallet-related operations ([addFromWallet], [restoreConnections]) require this
     * to be true.
     */
    val hasWalletAdapter: Boolean
        get() = walletAdapter != null

    // MARK: - Add Signers

    /**
     * Adds an Ed25519 keypair signer from a raw secret key.
     *
     * Creates a [KeyPair] from the provided Stellar secret key (S...) and stores it
     * in memory. The keypair is never persisted to storage -- it is lost when the
     * application terminates or the manager is garbage collected.
     *
     * If a signer with the same G-address already exists (either keypair or wallet),
     * the keypair signer takes precedence and overwrites the existing entry.
     *
     * @param secretKey A valid Stellar secret key (S-address, 56 characters)
     * @return The derived G-address of the signer
     * @throws SignerException.Invalid if the secret key is invalid or keypair creation fails
     *
     * Example:
     * ```kotlin
     * val address = manager.addFromSecret("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34REYB6WBMG7CKKFJHYAEGQ")
     * println("Added signer: $address")
     * ```
     */
    suspend fun addFromSecret(secretKey: String): String {
        // Validate and create keypair
        val keypair: KeyPair = try {
            KeyPair.fromSecretSeed(secretKey)
        } catch (e: Exception) {
            throw SignerException.invalid(
                "Invalid secret key. Must be a valid Stellar secret key (S...): ${e.message}",
                e
            )
        }

        val address = keypair.getAccountId()

        mutex.withLock {
            keypairSigners[address] = keypair
        }

        return address
    }

    /**
     * Connects an external wallet and adds it as a signer.
     *
     * Delegates to the configured [ExternalWalletAdapter] to prompt the user for wallet
     * authorization (e.g., showing a wallet selection modal). If the connection succeeds
     * and [WalletConnectionStorage] is configured, the connection metadata is persisted
     * for later restoration via [restoreConnections].
     *
     * @return The connected wallet info, or null if the user cancelled or the operation
     *   was unavailable
     * @throws ConfigurationException.MissingConfig if no wallet adapter is configured
     * @throws SmartAccountException if the wallet connection fails
     *
     * Example:
     * ```kotlin
     * val wallet = manager.addFromWallet()
     * if (wallet != null) {
     *     println("Connected: ${wallet.walletName} (${wallet.address})")
     * }
     * ```
     */
    suspend fun addFromWallet(): ConnectedWallet? {
        val adapter = walletAdapter ?: throw ConfigurationException.missingConfig(
            "walletAdapter: No wallet adapter configured. Pass an ExternalWalletAdapter " +
                    "to OZExternalSignerManager to enable wallet connections."
        )

        val wallet = adapter.connect() ?: return null

        // Persist the connection if storage is configured
        if (walletConnectionStorage != null) {
            saveWalletToStorage(wallet)
        }

        return wallet
    }

    // MARK: - Query Signers

    /**
     * Checks if any managed signer can sign for the given address.
     *
     * Checks keypair signers first (O(1) map lookup), then delegates to the
     * wallet adapter's [ExternalWalletAdapter.canSignFor] if available.
     *
     * @param address The Stellar G-address to check
     * @return True if a keypair or connected wallet can sign for this address
     *
     * Example:
     * ```kotlin
     * if (manager.canSignFor("GABC123...")) {
     *     println("Can sign for this address")
     * }
     * ```
     */
    suspend fun canSignFor(address: String): Boolean {
        // Check keypair signers first
        val hasKeypair = mutex.withLock {
            keypairSigners.containsKey(address)
        }
        if (hasKeypair) return true

        // Check wallet adapter
        if (walletAdapter?.canSignFor(address) == true) {
            return true
        }

        return false
    }

    /**
     * Gets information about a specific signer by address.
     *
     * Checks keypair signers first (takes precedence), then wallet signers.
     *
     * @param address The Stellar G-address to look up
     * @return The signer info, or null if no signer exists for this address
     *
     * Example:
     * ```kotlin
     * val info = manager.get("GABC123...")
     * if (info != null) {
     *     println("Signer type: ${info.type}")
     * }
     * ```
     */
    suspend fun get(address: String): ExternalSignerInfo? {
        // Check keypair signers
        val hasKeypair = mutex.withLock {
            keypairSigners.containsKey(address)
        }
        if (hasKeypair) {
            return ExternalSignerInfo(
                address = address,
                type = ExternalSignerType.KEYPAIR
            )
        }

        // Check wallet adapter
        if (walletAdapter != null) {
            val wallet = walletAdapter.getWalletForAddress(address)
            if (wallet != null) {
                return ExternalSignerInfo(
                    address = wallet.address,
                    type = ExternalSignerType.WALLET,
                    walletName = wallet.walletName,
                    walletId = wallet.walletId
                )
            }
        }

        return null
    }

    /**
     * Lists all managed external signers (both keypair and wallet).
     *
     * Keypair signers are listed first. If a G-address exists as both a keypair signer
     * and a wallet signer, only the keypair entry is returned (keypair takes precedence).
     *
     * @return List of all external signer info objects
     *
     * Example:
     * ```kotlin
     * val signers = manager.getAll()
     * for (signer in signers) {
     *     println("${signer.address} - ${signer.type}")
     * }
     * ```
     */
    suspend fun getAll(): List<ExternalSignerInfo> {
        val signers = mutableListOf<ExternalSignerInfo>()
        val keypairAddresses: Set<String>

        // Add keypair signers
        mutex.withLock {
            keypairAddresses = keypairSigners.keys.toSet()
            for (address in keypairAddresses) {
                signers.add(
                    ExternalSignerInfo(
                        address = address,
                        type = ExternalSignerType.KEYPAIR
                    )
                )
            }
        }

        // Add wallet signers (skip addresses already present as keypair)
        if (walletAdapter != null) {
            val wallets = walletAdapter.getConnectedWallets()
            for (wallet in wallets) {
                if (!keypairAddresses.contains(wallet.address)) {
                    signers.add(
                        ExternalSignerInfo(
                            address = wallet.address,
                            type = ExternalSignerType.WALLET,
                            walletName = wallet.walletName,
                            walletId = wallet.walletId
                        )
                    )
                }
            }
        }

        return signers
    }

    /**
     * Checks if any external signers are registered (keypair or wallet).
     *
     * @return True if at least one signer is managed
     */
    suspend fun hasSigners(): Boolean {
        val hasKeypairs = mutex.withLock {
            keypairSigners.isNotEmpty()
        }
        if (hasKeypairs) return true

        val walletCount = walletAdapter?.getConnectedWallets()?.size ?: 0
        return walletCount > 0
    }

    // MARK: - Sign Auth Entry

    /**
     * Signs an authorization entry preimage with the appropriate signer for the given address.
     *
     * For keypair signers, the preimage XDR is decoded, hashed (SHA-256), and signed directly
     * with the in-memory Ed25519 keypair. For wallet signers, the signing is delegated to
     * the [ExternalWalletAdapter.signAuthEntry] method.
     *
     * Keypair signers take precedence over wallet signers when both exist for the same address.
     *
     * @param address The G-address identifying which signer to use
     * @param authEntry Base64-encoded HashIdPreimage XDR to sign
     * @return The signing result containing the base64-encoded signature and signer address
     * @throws SignerException.NotFound if no signer is available for the address
     * @throws TransactionException.SigningFailed if the signing operation fails
     *
     * Example:
     * ```kotlin
     * val result = manager.signAuthEntry(
     *     address = "GABC123...",
     *     authEntry = preimageXdrBase64
     * )
     * println("Signature: ${result.signedAuthEntry}")
     * println("Signer: ${result.signerAddress}")
     * ```
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    suspend fun signAuthEntry(
        address: String,
        authEntry: String
    ): SignAuthEntryResult {
        // Try keypair signer first
        val keypair = mutex.withLock {
            keypairSigners[address]
        }

        if (keypair != null) {
            return signWithKeypair(keypair, authEntry, address)
        }

        // Try wallet adapter
        if (walletAdapter != null && walletAdapter.canSignFor(address)) {
            val result = try {
                walletAdapter.signAuthEntry(
                    authEntry,
                    SignAuthEntryOptions(
                        networkPassphrase = networkPassphrase,
                        address = address
                    )
                )
            } catch (e: Exception) {
                throw TransactionException.signingFailed(
                    "External wallet signing failed for $address: ${e.message}",
                    e
                )
            }

            return SignAuthEntryResult(
                signedAuthEntry = result.signedAuthEntry,
                signerAddress = result.signerAddress ?: address
            )
        }

        throw SignerException.notFound(address)
    }

    // MARK: - Remove Signers

    /**
     * Removes a signer by address.
     *
     * For keypair signers, removes the keypair from memory. For wallet signers,
     * removes the connection from storage. Both types are cleaned up if present.
     *
     * @param address The G-address of the signer to remove
     *
     * Example:
     * ```kotlin
     * manager.remove("GABC123...")
     * println("Signer removed")
     * ```
     */
    suspend fun remove(address: String) {
        // Remove from keypair signers
        mutex.withLock {
            keypairSigners.remove(address)
        }

        // Remove from storage
        removeWalletFromStorage(address)
    }

    /**
     * Removes all signers.
     *
     * Clears all keypair signers from memory, disconnects all external wallets,
     * and removes all persisted wallet connections from storage.
     *
     * Example:
     * ```kotlin
     * manager.removeAll()
     * println("All signers removed")
     * ```
     */
    suspend fun removeAll() {
        mutex.withLock {
            keypairSigners.clear()
        }

        // Disconnect all wallets
        walletAdapter?.disconnect()

        // Clear storage
        walletConnectionStorage?.removeItem(WALLET_STORAGE_KEY)
    }

    // MARK: - Wallet Connection Persistence

    /**
     * Restores previously connected wallets from storage.
     *
     * Reads stored wallet connection metadata from [WalletConnectionStorage] and
     * attempts to reconnect each wallet via [ExternalWalletAdapter.reconnect].
     * Wallets that fail to reconnect are removed from storage.
     *
     * This method is idempotent: subsequent calls after the first successful restoration
     * return the currently connected wallets without re-reading storage.
     *
     * @return List of successfully restored wallet connections
     *
     * Example:
     * ```kotlin
     * val restored = manager.restoreConnections()
     * println("Restored ${restored.size} wallet connections")
     * ```
     */
    suspend fun restoreConnections(): List<ConnectedWallet> {
        val alreadyRestored = mutex.withLock {
            val current = restored
            restored = true
            current
        }

        if (alreadyRestored) {
            // Already restored, return current wallet connections
            return walletAdapter?.getConnectedWallets() ?: emptyList()
        }

        if (walletConnectionStorage == null || walletAdapter == null) {
            return emptyList()
        }

        val stored = getStoredWallets()
        val restoredWallets = mutableListOf<ConnectedWallet>()

        for (savedWallet in stored) {
            try {
                val wallet = walletAdapter.reconnect(savedWallet.walletId)
                if (wallet != null) {
                    restoredWallets.add(wallet)
                } else {
                    // Reconnection returned null -- remove stale entry
                    removeWalletFromStorage(savedWallet.address)
                }
            } catch (_: Exception) {
                // Reconnection failed -- remove stale entry
                removeWalletFromStorage(savedWallet.address)
            }
        }

        return restoredWallets
    }

    // MARK: - Private Signing Helpers

    /**
     * Signs an auth entry preimage with an Ed25519 keypair.
     *
     * Decodes the base64-encoded HashIdPreimage XDR, computes its SHA-256 hash,
     * signs the hash with the keypair, and returns the base64-encoded signature.
     */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private suspend fun signWithKeypair(
        keypair: KeyPair,
        preimageXdrBase64: String,
        address: String
    ): SignAuthEntryResult {
        // Decode the base64 preimage
        val preimageXdrBytes: ByteArray = try {
            kotlin.io.encoding.Base64.decode(preimageXdrBase64)
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to decode base64 auth entry preimage: ${e.message}",
                e
            )
        }

        // Hash the preimage XDR bytes
        val payload: ByteArray = try {
            getSha256Crypto().hash(preimageXdrBytes)
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Failed to hash auth entry preimage: ${e.message}",
                e
            )
        }

        // Sign with keypair
        val signature: ByteArray = try {
            keypair.sign(payload)
        } catch (e: Exception) {
            throw TransactionException.signingFailed(
                "Ed25519 signing failed for $address: ${e.message}",
                e
            )
        }

        // Encode signature to base64
        val signatureBase64 = kotlin.io.encoding.Base64.encode(signature)

        return SignAuthEntryResult(
            signedAuthEntry = signatureBase64,
            signerAddress = address
        )
    }

    // =========================================================================
    // Private Storage Helpers
    // =========================================================================

    /**
     * Stored wallet connection info for serialization.
     */
    @Serializable
    internal data class StoredWalletConnection(
        val address: String,
        val walletId: String,
        val walletName: String,
        val connectedAt: Long
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Retrieves stored wallet connections from storage.
     *
     * Parses the JSON array stored under [WALLET_STORAGE_KEY]. Returns an empty
     * list if storage is not configured, the key does not exist, or parsing fails.
     */
    private suspend fun getStoredWallets(): List<StoredWalletConnection> {
        if (walletConnectionStorage == null) return emptyList()

        return try {
            val data = walletConnectionStorage.getItem(WALLET_STORAGE_KEY) ?: return emptyList()
            parseStoredWallets(data)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Saves a wallet connection to storage.
     *
     * Performs upsert semantics: if a connection with the same address already exists,
     * it is replaced. The connection list is serialized as a JSON array.
     */
    private suspend fun saveWalletToStorage(wallet: ConnectedWallet) {
        if (walletConnectionStorage == null) return

        val stored = getStoredWallets().toMutableList()

        // Remove existing entry for this address (if any)
        stored.removeAll { it.address == wallet.address }

        // Add new entry
        stored.add(
            StoredWalletConnection(
                address = wallet.address,
                walletId = wallet.walletId,
                walletName = wallet.walletName,
                connectedAt = currentTimeMillis()
            )
        )

        walletConnectionStorage.setItem(WALLET_STORAGE_KEY, serializeWallets(stored))
    }

    /**
     * Removes a wallet connection from storage by address.
     *
     * If no connections remain after removal, the storage key is deleted entirely.
     */
    private suspend fun removeWalletFromStorage(address: String) {
        if (walletConnectionStorage == null) return

        val stored = getStoredWallets().toMutableList()
        stored.removeAll { it.address == address }

        if (stored.isEmpty()) {
            walletConnectionStorage.removeItem(WALLET_STORAGE_KEY)
        } else {
            walletConnectionStorage.setItem(WALLET_STORAGE_KEY, serializeWallets(stored))
        }
    }

    // =========================================================================
    // JSON Serialization
    // =========================================================================

    /**
     * Serializes wallet connections to a JSON array string using kotlinx.serialization.
     *
     * Produces the same format as the previous manual serializer:
     * ```json
     * [{"address":"G...","walletId":"freighter","walletName":"Freighter","connectedAt":12345}]
     * ```
     */
    private fun serializeWallets(wallets: List<StoredWalletConnection>): String {
        return json.encodeToString(wallets)
    }

    /**
     * Parses wallet connections from a JSON array string using kotlinx.serialization.
     *
     * Returns all entries or an empty list on any parse failure. Unlike the previous
     * manual parser which skipped individual malformed entries, kotlinx.serialization
     * parses atomically. This is acceptable because the serializer always produces valid
     * JSON — corruption would only come from external tampering or storage failure.
     */
    private fun parseStoredWallets(jsonString: String): List<StoredWalletConnection> {
        return try {
            json.decodeFromString<List<StoredWalletConnection>>(jsonString)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
