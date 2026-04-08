//
//  UserDefaultsStorageAdapter.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.CredentialException
import com.soneso.stellar.sdk.smartaccount.core.StorageException
import com.soneso.stellar.sdk.smartaccount.oz.CredentialIndex
import com.soneso.stellar.sdk.smartaccount.oz.SerializableCredential
import com.soneso.stellar.sdk.smartaccount.oz.SerializableSession
import com.soneso.stellar.sdk.smartaccount.oz.toSerializable
import com.soneso.stellar.sdk.smartaccount.oz.toStoredCredential
import com.soneso.stellar.sdk.smartaccount.oz.toStoredSession
import com.soneso.stellar.sdk.smartaccount.oz.StorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredentialUpdate
import com.soneso.stellar.sdk.smartaccount.oz.StoredSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

// MARK: - UserDefaultsStorageAdapter

/**
 * Persistent storage adapter for smart account credentials and sessions using NSUserDefaults.
 *
 * This adapter stores credential and session data in an isolated NSUserDefaults suite,
 * persisting across application restarts. Data is serialized as JSON with ByteArray fields
 * encoded as lowercase hex strings.
 *
 * Since stored credentials contain public keys (not secret keys), NSUserDefaults provides
 * adequate security for most use cases. For applications requiring stronger isolation,
 * use [KeychainStorageAdapter] instead.
 *
 * Thread safety is ensured by a [Mutex], and all operations are suspend functions.
 *
 * Key scheme:
 * - `cred_{credentialId}` for individual credentials
 * - `credential_index` for the credential ID index
 * - `session_current` for the active session
 *
 * Example:
 * ```kotlin
 * val storage = UserDefaultsStorageAdapter()
 * storage.save(credential)
 * val loaded = storage.get(credential.credentialId)
 * ```
 *
 * @param suiteName The NSUserDefaults suite name for isolation.
 *   Defaults to `"com.soneso.stellar.smartaccount"`.
 */
class UserDefaultsStorageAdapter(
    suiteName: String = DEFAULT_SUITE_NAME
) : StorageAdapter {

    private val defaults: NSUserDefaults = NSUserDefaults(suiteName = suiteName)
        ?: throw StorageException.WriteFailed("Failed to create NSUserDefaults with suite: $suiteName")

    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        /** Default NSUserDefaults suite name. */
        const val DEFAULT_SUITE_NAME = "com.soneso.stellar.smartaccount"

        // Storage keys
        private const val KEY_PREFIX_CREDENTIAL = "cred_"
        private const val KEY_CREDENTIAL_INDEX = "credential_index"
        private const val KEY_SESSION = "session_current"
    }

    // MARK: - Credential Operations

    override suspend fun save(credential: StoredCredential): Unit = mutex.withLock {
        try {
            val serializable = credential.toSerializable()
            val jsonString = json.encodeToString(serializable)
            val key = KEY_PREFIX_CREDENTIAL + credential.credentialId

            defaults.setObject(jsonString, forKey = key)

            // Update the credential index
            val index = readIndex()
            if (!index.ids.contains(credential.credentialId)) {
                val updated = CredentialIndex(ids = index.ids + credential.credentialId)
                writeIndex(updated)
            }


        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.WriteFailed(
                "Storage write failed for key: ${credential.credentialId}", e
            )
        }
    }

    override suspend fun get(credentialId: String): StoredCredential? = mutex.withLock {
        try {
            readCredential(credentialId)
        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.ReadFailed(
                "Storage read failed for key: $credentialId", e
            )
        }
    }

    override suspend fun getByContract(contractId: String): List<StoredCredential> = mutex.withLock {
        try {
            val index = readIndex()
            index.ids.mapNotNull { id ->
                readCredential(id)
            }.filter { it.contractId == contractId }
        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.ReadFailed(
                "Storage read failed for contract: $contractId", e
            )
        }
    }

    override suspend fun getAll(): List<StoredCredential> = mutex.withLock {
        try {
            val index = readIndex()
            index.ids.mapNotNull { id -> readCredential(id) }
        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.ReadFailed(
                "Storage read failed for key: all credentials", e
            )
        }
    }

    override suspend fun delete(credentialId: String): Unit = mutex.withLock {
        try {
            val key = KEY_PREFIX_CREDENTIAL + credentialId
            defaults.removeObjectForKey(key)

            // Update the credential index
            val index = readIndex()
            val updated = CredentialIndex(ids = index.ids.filter { it != credentialId })
            writeIndex(updated)


        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.WriteFailed(
                "Storage write failed for key: $credentialId", e
            )
        }
    }

    override suspend fun update(credentialId: String, updates: StoredCredentialUpdate): Unit = mutex.withLock {
        try {
            val existing = readCredential(credentialId)
                ?: throw CredentialException.notFound(credentialId)

            val updated = existing.copy(
                deploymentStatus = updates.deploymentStatus ?: existing.deploymentStatus,
                deploymentError = updates.deploymentError ?: existing.deploymentError,
                contractId = updates.contractId ?: existing.contractId,
                lastUsedAt = updates.lastUsedAt ?: existing.lastUsedAt,
                nickname = updates.nickname ?: existing.nickname,
                isPrimary = updates.isPrimary ?: existing.isPrimary,
                transports = updates.transports ?: existing.transports,
                deviceType = updates.deviceType ?: existing.deviceType,
                backedUp = updates.backedUp ?: existing.backedUp
            )

            val serializable = updated.toSerializable()
            val jsonString = json.encodeToString(serializable)
            val key = KEY_PREFIX_CREDENTIAL + credentialId

            defaults.setObject(jsonString, forKey = key)

        } catch (e: Exception) {
            if (e is CredentialException || e is StorageException) throw e
            throw StorageException.WriteFailed(
                "Storage write failed for key: $credentialId", e
            )
        }
    }

    override suspend fun clear(): Unit = mutex.withLock {
        try {
            // Remove all credential entries
            val index = readIndex()
            for (id in index.ids) {
                defaults.removeObjectForKey(KEY_PREFIX_CREDENTIAL + id)
            }

            // Remove the index itself
            defaults.removeObjectForKey(KEY_CREDENTIAL_INDEX)

            // Remove the session
            defaults.removeObjectForKey(KEY_SESSION)


        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.WriteFailed(
                "Storage write failed for key: clear all", e
            )
        }
    }

    // MARK: - Session Operations

    override suspend fun saveSession(session: StoredSession): Unit = mutex.withLock {
        try {
            val serializable = session.toSerializable()
            val jsonString = json.encodeToString(serializable)
            defaults.setObject(jsonString, forKey = KEY_SESSION)

        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.WriteFailed(
                "Storage write failed for key: session", e
            )
        }
    }

    override suspend fun getSession(): StoredSession? = mutex.withLock {
        try {
            val jsonString = defaults.stringForKey(KEY_SESSION) ?: return null
            val serializable = json.decodeFromString<SerializableSession>(jsonString)
            val session = serializable.toStoredSession()

            if (session.isExpired) {
                // Clear expired session
                defaults.removeObjectForKey(KEY_SESSION)
    
                return null
            }

            session
        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.ReadFailed(
                "Storage read failed for key: session", e
            )
        }
    }

    override suspend fun clearSession(): Unit = mutex.withLock {
        try {
            defaults.removeObjectForKey(KEY_SESSION)

        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.WriteFailed(
                "Storage write failed for key: session", e
            )
        }
    }

    // MARK: - Internal Helpers

    /**
     * Reads a single credential from NSUserDefaults by its ID.
     *
     * Must be called within the mutex lock.
     *
     * @return The credential, or null if not found.
     */
    private fun readCredential(credentialId: String): StoredCredential? {
        val key = KEY_PREFIX_CREDENTIAL + credentialId
        val jsonString = defaults.stringForKey(key) ?: return null
        val serializable = json.decodeFromString<SerializableCredential>(jsonString)
        return serializable.toStoredCredential()
    }

    /**
     * Reads the credential index from NSUserDefaults.
     *
     * Returns an empty index if none exists.
     * Must be called within the mutex lock.
     */
    private fun readIndex(): CredentialIndex {
        val jsonString = defaults.stringForKey(KEY_CREDENTIAL_INDEX)
            ?: return CredentialIndex(ids = emptyList())
        return json.decodeFromString(jsonString)
    }

    /**
     * Writes the credential index to NSUserDefaults.
     *
     * Must be called within the mutex lock.
     */
    private fun writeIndex(index: CredentialIndex) {
        val jsonString = json.encodeToString(index)
        defaults.setObject(jsonString, forKey = KEY_CREDENTIAL_INDEX)
    }
}
