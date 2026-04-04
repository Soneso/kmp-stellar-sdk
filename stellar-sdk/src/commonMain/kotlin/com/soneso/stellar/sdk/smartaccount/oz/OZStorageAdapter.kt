//
//  OZStorageAdapter.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.currentTimeMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// MARK: - Credential Deployment Status

/**
 * The deployment status of a smart account credential.
 */
enum class CredentialDeploymentStatus {
    /**
     * The credential has been created but the smart account contract has not been deployed yet.
     */
    PENDING,

    /**
     * The deployment transaction failed.
     */
    FAILED
    // Note: No SUCCESS status - credential is deleted from storage on successful deployment
}

// MARK: - Stored Credential

/**
 * A stored smart account credential with deployment and usage metadata.
 *
 * Represents a WebAuthn credential (passkey) associated with a smart account.
 * Tracks the credential's deployment status, contract address, and usage history.
 *
 * Example:
 * ```kotlin
 * val credential = StoredCredential(
 *     credentialId = "base64url-encoded-id",
 *     publicKey = secp256r1PublicKeyData,
 *     contractId = "CBCD1234...",
 *     deploymentStatus = CredentialDeploymentStatus.PENDING,
 *     isPrimary = true
 * )
 * ```
 */
data class StoredCredential(
    /**
     * The WebAuthn credential ID (Base64URL encoded).
     *
     * This is the unique identifier returned by the browser during WebAuthn registration.
     */
    val credentialId: String,

    /**
     * The uncompressed secp256r1 public key (65 bytes starting with 0x04).
     *
     * This public key is used for signature verification in the WebAuthn verifier contract.
     */
    val publicKey: ByteArray,

    /**
     * The smart account contract address (C-address).
     *
     * Set during wallet creation via deriveContractAddress. Null if the contract
     * address has not been derived yet.
     */
    val contractId: String? = null,

    /**
     * The current deployment status of the smart account contract.
     */
    val deploymentStatus: CredentialDeploymentStatus = CredentialDeploymentStatus.PENDING,

    /**
     * Error message if deployment failed.
     *
     * Contains details about why the deployment transaction failed.
     */
    val deploymentError: String? = null,

    /**
     * Timestamp of when this credential was created.
     */
    val createdAt: Long = currentTimeMillis(),

    /**
     * Timestamp of when this credential was last used for signing.
     *
     * Updated after successful transaction signatures.
     */
    val lastUsedAt: Long? = null,

    /**
     * Optional user-friendly nickname for this credential.
     *
     * Example: "MacBook Pro Touch ID", "YubiKey 5"
     */
    val nickname: String? = null,

    /**
     * Whether this is the primary credential for this smart account.
     *
     * The primary credential is used as the default for signing operations.
     */
    val isPrimary: Boolean = false,

    /**
     * Authenticator transport hints indicating how the browser can communicate
     * with the authenticator (e.g., "usb", "nfc", "ble", "internal").
     *
     * Used when constructing allowCredentials for future authentication ceremonies,
     * which helps the browser select the correct authenticator more efficiently.
     */
    val transports: List<String>? = null,

    /**
     * Authenticator device type.
     *
     * - "singleDevice": Hardware security key (not synced)
     * - "multiDevice": Synced/cloud-backed passkey (available across devices)
     *
     * Corresponds to the credentialDeviceType field in WebAuthn authenticator data flags.
     */
    val deviceType: String? = null,

    /**
     * Whether the passkey is backed up or synced to a cloud provider.
     *
     * When true, the credential is available across the user's devices via
     * iCloud Keychain, Google Password Manager, or similar sync services.
     * Corresponds to the credentialBackedUp flag in WebAuthn authenticator data.
     */
    val backedUp: Boolean? = null
) {
    /**
     * Custom equals implementation that properly compares ByteArray.
     *
     * Standard data class equals would not correctly compare the ByteArray field
     * by content, so this override ensures proper content-based comparison using
     * contentEquals().
     *
     * @param other The object to compare with
     * @return true if the objects are equal, false otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as StoredCredential

        if (credentialId != other.credentialId) return false
        if (!Util.constantTimeEquals(publicKey, other.publicKey)) return false
        if (contractId != other.contractId) return false
        if (deploymentStatus != other.deploymentStatus) return false
        if (deploymentError != other.deploymentError) return false
        if (createdAt != other.createdAt) return false
        if (lastUsedAt != other.lastUsedAt) return false
        if (nickname != other.nickname) return false
        if (isPrimary != other.isPrimary) return false
        if (transports != other.transports) return false
        if (deviceType != other.deviceType) return false
        if (backedUp != other.backedUp) return false

        return true
    }

    /**
     * Custom hashCode implementation that properly handles ByteArray.
     *
     * Standard data class hashCode would not correctly hash the ByteArray field
     * by content, so this override ensures proper content-based hashing using
     * contentHashCode().
     *
     * @return Hash code for this stored credential
     */
    override fun hashCode(): Int {
        var result = credentialId.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + (contractId?.hashCode() ?: 0)
        result = 31 * result + deploymentStatus.hashCode()
        result = 31 * result + (deploymentError?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (lastUsedAt?.hashCode() ?: 0)
        result = 31 * result + (nickname?.hashCode() ?: 0)
        result = 31 * result + isPrimary.hashCode()
        result = 31 * result + (transports?.hashCode() ?: 0)
        result = 31 * result + (deviceType?.hashCode() ?: 0)
        result = 31 * result + (backedUp?.hashCode() ?: 0)
        return result
    }
}

// MARK: - Stored Session

/**
 * A stored user session for silent reconnection.
 *
 * Sessions enable users to reconnect to their smart account wallet without
 * re-authentication, as long as the session has not expired.
 *
 * Example:
 * ```kotlin
 * val session = StoredSession(
 *     credentialId = "base64url-encoded-id",
 *     contractId = "CBCD1234...",
 *     connectedAt = currentTimeMillis(),
 *     expiresAt = currentTimeMillis() + (7 * 24 * 60 * 60 * 1000) // 7 days
 * )
 *
 * if (!session.isExpired) {
 *     // Silently reconnect
 * }
 * ```
 */
data class StoredSession(
    /**
     * The credential ID associated with this session.
     */
    val credentialId: String,

    /**
     * The smart account contract address.
     */
    val contractId: String,

    /**
     * When the session was established (milliseconds since epoch).
     */
    val connectedAt: Long,

    /**
     * When the session expires (milliseconds since epoch).
     */
    val expiresAt: Long
) {
    /**
     * Whether the session has expired.
     */
    val isExpired: Boolean
        get() = currentTimeMillis() >= expiresAt
}

// MARK: - Stored Credential Update

/**
 * Partial updates for a stored credential.
 *
 * Only non-null fields are applied during an update operation. A `null` value means
 * "no change" -- it does **not** clear the field to null. This is a deliberate design
 * choice: there is no way to set a previously non-null field back to null via
 * [StorageAdapter.update]. To reset a field, call [StorageAdapter.save] with a full
 * [StoredCredential] replacement.
 *
 * Example:
 * ```kotlin
 * val update = StoredCredentialUpdate(
 *     deploymentStatus = CredentialDeploymentStatus.FAILED,
 *     deploymentError = "Transaction failed: insufficient balance"
 * )
 * storage.update(credentialId = "abc123", updates = update)
 * ```
 */
data class StoredCredentialUpdate(
    /**
     * New deployment status.
     */
    val deploymentStatus: CredentialDeploymentStatus? = null,

    /**
     * New deployment error message.
     */
    val deploymentError: String? = null,

    /**
     * New contract ID.
     */
    val contractId: String? = null,

    /**
     * New last used timestamp.
     */
    val lastUsedAt: Long? = null,

    /**
     * New nickname.
     */
    val nickname: String? = null,

    /**
     * New primary flag.
     */
    val isPrimary: Boolean? = null,

    /**
     * New authenticator transport hints.
     */
    val transports: List<String>? = null,

    /**
     * New device type ("singleDevice" or "multiDevice").
     */
    val deviceType: String? = null,

    /**
     * New backed up flag.
     */
    val backedUp: Boolean? = null
)

// MARK: - Storage Adapter Interface

/**
 * Protocol for persisting smart account credentials and sessions.
 *
 * Storage adapters provide a pluggable persistence layer for credentials and sessions.
 * Implementations must be thread-safe and support concurrent access.
 *
 * The default implementation is InMemoryStorageAdapter, which stores data in memory
 * only. Platform-specific implementations can provide persistent storage.
 */
interface StorageAdapter {
    /**
     * Saves a credential to storage using upsert semantics.
     *
     * If a credential with the same ID already exists, it is overwritten.
     *
     * @param credential The credential to save
     * @throws StorageException.WriteFailed if saving fails
     */
    suspend fun save(credential: StoredCredential)

    /**
     * Retrieves a credential by its ID.
     *
     * @param credentialId The credential ID
     * @return The credential, or null if not found
     * @throws StorageException.ReadFailed if reading fails
     */
    suspend fun get(credentialId: String): StoredCredential?

    /**
     * Retrieves all credentials associated with a contract address.
     *
     * @param contractId The contract address
     * @return List of credentials (empty if none found)
     * @throws StorageException.ReadFailed if reading fails
     */
    suspend fun getByContract(contractId: String): List<StoredCredential>

    /**
     * Retrieves all stored credentials.
     *
     * @return List of all credentials
     * @throws StorageException.ReadFailed if reading fails
     */
    suspend fun getAll(): List<StoredCredential>

    /**
     * Deletes a credential by its ID.
     *
     * @param credentialId The credential ID to delete
     * @throws StorageException.WriteFailed if deletion fails
     */
    suspend fun delete(credentialId: String)

    /**
     * Updates a credential with partial changes.
     *
     * Only non-null fields in the update are applied.
     *
     * @param credentialId The credential ID to update
     * @param updates The partial updates to apply
     * @throws CredentialException.NotFound if the credential is not found
     * @throws StorageException.WriteFailed if updating fails
     */
    suspend fun update(credentialId: String, updates: StoredCredentialUpdate)

    /**
     * Clears all credentials from storage.
     *
     * @throws StorageException.WriteFailed if clearing fails
     */
    suspend fun clear()

    /**
     * Saves a session to storage.
     *
     * @param session The session to save
     * @throws StorageException.WriteFailed if saving fails
     */
    suspend fun saveSession(session: StoredSession)

    /**
     * Retrieves the current session.
     *
     * @return The session, or null if no session exists or if the session is expired
     * @throws StorageException.ReadFailed if reading fails
     */
    suspend fun getSession(): StoredSession?

    /**
     * Clears the current session.
     *
     * @throws StorageException.WriteFailed if clearing fails
     */
    suspend fun clearSession()
}

// MARK: - In-Memory Storage Adapter

/**
 * In-memory storage adapter for credentials and sessions.
 *
 * This implementation stores all data in memory and does not persist across application
 * restarts. It is thread-safe using mutex protection.
 *
 * Use platform-specific implementations for persistent storage (e.g., SharedPreferences
 * on Android, UserDefaults on iOS, localStorage on Web).
 *
 * All [InMemoryStorageAdapter] instances are considered equal since they are
 * interchangeable when freshly created (both start empty). This enables correct
 * data class equality for configs that use [InMemoryStorageAdapter] as a default field.
 *
 * Example:
 * ```kotlin
 * val storage = InMemoryStorageAdapter()
 * val credential = StoredCredential(...)
 * storage.save(credential)
 * ```
 */
class InMemoryStorageAdapter : StorageAdapter {
    private val credentials = mutableMapOf<String, StoredCredential>()
    private var session: StoredSession? = null
    private val mutex = Mutex()

    override suspend fun save(credential: StoredCredential): Unit = mutex.withLock {
        credentials[credential.credentialId] = credential
    }

    override suspend fun get(credentialId: String): StoredCredential? = mutex.withLock {
        credentials[credentialId]
    }

    override suspend fun getByContract(contractId: String): List<StoredCredential> = mutex.withLock {
        credentials.values.filter { it.contractId == contractId }
    }

    override suspend fun getAll(): List<StoredCredential> = mutex.withLock {
        credentials.values.toList()
    }

    override suspend fun delete(credentialId: String): Unit = mutex.withLock {
        credentials.remove(credentialId)
        Unit
    }

    override suspend fun update(credentialId: String, updates: StoredCredentialUpdate): Unit = mutex.withLock {
        val credential = credentials[credentialId]
            ?: throw CredentialException.notFound(credentialId)

        val updated = credential.copy(
            deploymentStatus = updates.deploymentStatus ?: credential.deploymentStatus,
            deploymentError = updates.deploymentError ?: credential.deploymentError,
            contractId = updates.contractId ?: credential.contractId,
            lastUsedAt = updates.lastUsedAt ?: credential.lastUsedAt,
            nickname = updates.nickname ?: credential.nickname,
            isPrimary = updates.isPrimary ?: credential.isPrimary,
            transports = updates.transports ?: credential.transports,
            deviceType = updates.deviceType ?: credential.deviceType,
            backedUp = updates.backedUp ?: credential.backedUp
        )

        credentials[credentialId] = updated
    }

    override suspend fun clear(): Unit = mutex.withLock {
        credentials.clear()
    }

    override suspend fun saveSession(session: StoredSession): Unit = mutex.withLock {
        this.session = session
    }

    override suspend fun getSession(): StoredSession? = mutex.withLock {
        val currentSession = session
        if (currentSession != null && currentSession.isExpired) {
            // Clear expired session
            session = null
            return@withLock null
        }
        currentSession
    }

    override suspend fun clearSession(): Unit = mutex.withLock {
        session = null
    }

    /**
     * All [InMemoryStorageAdapter] instances are considered equal.
     *
     * Two freshly created instances are functionally identical (both empty),
     * so they are interchangeable as default values in data class fields.
     */
    override fun equals(other: Any?): Boolean = other is InMemoryStorageAdapter

    /**
     * Consistent hash code for all [InMemoryStorageAdapter] instances.
     */
    override fun hashCode(): Int = InMemoryStorageAdapter::class.hashCode()
}

// MARK: - Connected Wallet

/**
 * Information about an externally connected wallet.
 *
 * Returned by [ExternalWalletAdapter.connect] and [ExternalWalletAdapter.getConnectedWallets]
 * to identify which wallet is connected and its signing address.
 *
 * Example:
 * ```kotlin
 * val wallet = ConnectedWallet(
 *     address = "GABC123...",
 *     walletId = "freighter",
 *     walletName = "Freighter"
 * )
 * ```
 */
data class ConnectedWallet(
    /**
     * The Stellar G-address of the connected wallet.
     */
    val address: String,

    /**
     * Unique wallet identifier (e.g., "freighter", "lobstr").
     *
     * Used for reconnection via [ExternalWalletAdapter.reconnect].
     */
    val walletId: String,

    /**
     * Human-readable display name for the wallet (e.g., "Freighter", "LOBSTR").
     */
    val walletName: String
)

// MARK: - Sign Auth Entry Types

/**
 * Options for signing an authorization entry with an external wallet.
 *
 * Allows specifying a network passphrase and a particular address when
 * multiple wallets are connected.
 */
data class SignAuthEntryOptions(
    /**
     * Network passphrase for signing context.
     */
    val networkPassphrase: String? = null,

    /**
     * Specific address to sign with, if multiple wallets are connected.
     */
    val address: String? = null
)

/**
 * Result of signing an authorization preimage with an external wallet.
 *
 * Contains the raw Ed25519 signature and optionally the signer address, which
 * may differ from the requested address in some wallet implementations.
 */
data class SignAuthEntryResult(
    /**
     * The base64-encoded raw Ed25519 signature (64 bytes).
     *
     * The wallet hashes the preimage with SHA-256 and signs the resulting
     * 32-byte payload with Ed25519. This field contains the 64-byte signature.
     */
    val signedAuthEntry: String,

    /**
     * The Stellar G-address that produced the signature.
     *
     * May be null if the wallet does not report the signer address.
     * When null, callers should assume the signature came from the requested address.
     */
    val signerAddress: String? = null
)

// MARK: - External Wallet Adapter Interface

/**
 * Protocol for integrating external wallet adapters for multi-signer support.
 *
 * External wallet adapters enable signing with external wallets like Freighter or Albedo
 * for multi-signature smart accounts. They handle wallet connection, signature collection,
 * and wallet reconnection.
 *
 * Example implementation:
 * ```kotlin
 * class FreighterAdapter : ExternalWalletAdapter {
 *     override suspend fun connect(): ConnectedWallet? {
 *         // Show wallet selection modal
 *         return ConnectedWallet(address = "G...", walletId = "freighter", walletName = "Freighter")
 *     }
 *
 *     override suspend fun signAuthEntry(
 *         preimageXdr: String,
 *         options: SignAuthEntryOptions?
 *     ): SignAuthEntryResult {
 *         // Decode preimage, hash with SHA-256, sign with Ed25519
 *         val preimageBytes = Base64.decode(preimageXdr)
 *         val hash = sha256(preimageBytes)
 *         val signature = freighterSign(hash, options?.address)
 *         return SignAuthEntryResult(
 *             signedAuthEntry = Base64.encode(signature),
 *             signerAddress = options?.address
 *         )
 *     }
 * }
 * ```
 */
interface ExternalWalletAdapter {
    /**
     * Connects to the external wallet.
     *
     * Prompts the user to authorize the connection via the wallet's UI
     * (e.g., showing a wallet selection modal).
     *
     * @return The connected wallet info, or null if the user cancelled
     * @throws WalletException if connection fails
     */
    suspend fun connect(): ConnectedWallet?

    /**
     * Disconnects all external wallets.
     *
     * @throws WalletException if disconnection fails
     */
    suspend fun disconnect()

    /**
     * Signs an authorization preimage with the external wallet.
     *
     * The SDK sends a base64-encoded HashIDPreimage XDR. The wallet should:
     * 1. Base64-decode the preimage bytes
     * 2. SHA-256 hash the preimage bytes
     * 3. Ed25519-sign the 32-byte hash
     * 4. Return the 64-byte raw signature as base64
     *
     * The SDK handles auth entry construction and signature format — the wallet
     * only needs to produce the raw Ed25519 signature.
     *
     * @param preimageXdr The base64-encoded HashIDPreimage XDR to sign
     * @param options Optional signing options (network passphrase, specific address)
     * @return The signing result with base64-encoded raw Ed25519 signature (64 bytes)
     * @throws TransactionException.SigningFailed if signing fails or is rejected
     */
    suspend fun signAuthEntry(
        preimageXdr: String,
        options: SignAuthEntryOptions? = null
    ): SignAuthEntryResult

    /**
     * Gets all currently connected wallets.
     *
     * @return List of connected wallets with their addresses and identifiers
     */
    fun getConnectedWallets(): List<ConnectedWallet>

    /**
     * Checks if a specific address has a connected wallet that can sign for it.
     *
     * @param address The Stellar G-address to check
     * @return True if a connected wallet can sign for this address
     */
    fun canSignFor(address: String): Boolean

    /**
     * Gets wallet info for a specific address.
     *
     * Optional method for looking up a connected wallet by its Stellar address.
     * Default implementation returns null.
     *
     * @param address The Stellar G-address to look up
     * @return The connected wallet info, or null if not found
     */
    fun getWalletForAddress(address: String): ConnectedWallet? {
        return null
    }

    /**
     * Reconnects to a previously connected wallet by its wallet ID.
     *
     * Used for restoring wallet connections after page reloads or app restarts.
     * Default implementation returns null (reconnection not supported).
     *
     * @param walletId The wallet identifier (e.g., "freighter", "lobstr")
     * @return The reconnected wallet info, or null if reconnection failed
     */
    suspend fun reconnect(walletId: String): ConnectedWallet? {
        return null
    }
}
