//
//  AndroidStorageAdapter.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.soneso.stellar.sdk.smartaccount.core.CredentialException
import com.soneso.stellar.sdk.smartaccount.core.StorageException
import com.soneso.stellar.sdk.smartaccount.oz.SerializableCredential
import com.soneso.stellar.sdk.smartaccount.oz.SerializableSession
import com.soneso.stellar.sdk.smartaccount.oz.StorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredentialUpdate
import com.soneso.stellar.sdk.smartaccount.oz.StoredSession
import com.soneso.stellar.sdk.smartaccount.oz.toSerializable
import com.soneso.stellar.sdk.smartaccount.oz.toStoredCredential
import com.soneso.stellar.sdk.smartaccount.oz.toStoredSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// MARK: - Android Storage Adapter

/**
 * Android storage adapter using [EncryptedSharedPreferences] for secure credential
 * and session persistence.
 *
 * This adapter encrypts all stored data at rest using AES-256-GCM for values
 * and AES-256-SIV for keys, backed by the Android Keystore. Data persists across
 * application restarts and is protected by the device's hardware-backed keystore
 * when available.
 *
 * Thread safety: All write operations ([save], [delete], [update]) use synchronous
 * [SharedPreferences.Editor.commit] to ensure data is persisted before returning.
 * A [Mutex] protects all operations to ensure coroutine-safe read-modify-write
 * sequences.
 *
 * Requirements:
 * - Android API 24+ (minSdk for this SDK)
 * - AndroidX Security Crypto library (`androidx.security:security-crypto`)
 *
 * Example:
 * ```kotlin
 * val storage = AndroidStorageAdapter(applicationContext)
 *
 * // Save a credential
 * val credential = StoredCredential(
 *     credentialId = "abc123",
 *     publicKey = publicKeyBytes,
 *     contractId = "CBCD1234..."
 * )
 * storage.save(credential)
 *
 * // Retrieve credentials
 * val retrieved = storage.get("abc123")
 * val allCreds = storage.getAll()
 * ```
 *
 * @param context The Android application context. Use `applicationContext` to avoid
 *   activity/fragment lifecycle issues.
 * @throws StorageException.WriteFailed if encrypted storage cannot be initialized
 *   (e.g., device does not support Android Keystore)
 */
class AndroidStorageAdapter(context: Context) : StorageAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences

    private val mutex = Mutex()

    init {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            throw StorageException.WriteFailed(
                "Failed to initialize encrypted storage: ${e.message}",
                e
            )
        }
    }

    // MARK: - Credential Operations

    /**
     * Saves a credential to encrypted storage using upsert semantics.
     *
     * If a credential with the same ID already exists, it is overwritten.
     * The credential is serialized to JSON with the public key encoded as a hex string.
     *
     * @param credential The credential to save
     * @throws StorageException.WriteFailed if the write operation fails
     */
    override suspend fun save(credential: StoredCredential): Unit = mutex.withLock {
        try {
            val key = credentialKey(credential.credentialId)
            val jsonStr = json.encodeToString(credential.toSerializable())
            val success = prefs.edit().putString(key, jsonStr).commit()
            if (!success) {
                throw StorageException.writeFailed("credential:${credential.credentialId}")
            }
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                "credential:${credential.credentialId}",
                e
            )
        }
    }

    /**
     * Retrieves a credential by its ID from encrypted storage.
     *
     * Returns null if the credential is not found or if the stored data is corrupted.
     * Corrupted data is logged and treated as absent rather than throwing an exception.
     *
     * @param credentialId The credential ID to look up
     * @return The credential, or null if not found or data is corrupted
     * @throws StorageException.ReadFailed if the read operation fails due to a storage error
     */
    override suspend fun get(credentialId: String): StoredCredential? = mutex.withLock {
        return try {
            val key = credentialKey(credentialId)
            val jsonStr = prefs.getString(key, null) ?: return null
            deserializeCredential(jsonStr, credentialId)
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.readFailed(
                "credential:$credentialId",
                e
            )
        }
    }

    /**
     * Retrieves all credentials associated with a specific contract address.
     *
     * @param contractId The contract address (C-address) to filter by
     * @return List of credentials matching the contract ID (empty if none found)
     * @throws StorageException.ReadFailed if the read operation fails
     */
    override suspend fun getByContract(contractId: String): List<StoredCredential> {
        return getAll().filter { it.contractId == contractId }
    }

    /**
     * Retrieves all stored credentials from encrypted storage.
     *
     * Iterates over all keys with the credential prefix and deserializes each value.
     * Corrupted entries are logged and skipped rather than causing the entire
     * operation to fail.
     *
     * @return List of all stored credentials
     * @throws StorageException.ReadFailed if the read operation fails due to a storage error
     */
    override suspend fun getAll(): List<StoredCredential> = mutex.withLock {
        return try {
            val allKeys = prefs.all.keys
            val credentials = mutableListOf<StoredCredential>()
            for (key in allKeys) {
                if (!key.startsWith(CREDENTIAL_KEY_PREFIX)) continue
                val jsonStr = prefs.getString(key, null) ?: continue
                val credentialId = key.removePrefix(CREDENTIAL_KEY_PREFIX)
                val credential = deserializeCredential(jsonStr, credentialId)
                if (credential != null) {
                    credentials.add(credential)
                }
            }
            credentials
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.readFailed(
                "credentials:all",
                e
            )
        }
    }

    /**
     * Deletes a credential by its ID from encrypted storage.
     *
     * If the credential does not exist, this operation is a no-op.
     *
     * @param credentialId The credential ID to delete
     * @throws StorageException.WriteFailed if the deletion fails
     */
    override suspend fun delete(credentialId: String): Unit = mutex.withLock {
        try {
            val key = credentialKey(credentialId)
            val success = prefs.edit().remove(key).commit()
            if (!success) {
                throw StorageException.writeFailed("credential:$credentialId")
            }
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                "credential:$credentialId",
                e
            )
        }
    }

    /**
     * Updates a credential with partial changes using read-modify-write semantics.
     *
     * Only non-null fields in the [updates] object are applied to the existing credential.
     * Uses synchronous [SharedPreferences.Editor.commit] to ensure atomicity of the
     * read-modify-write operation. A [Mutex] guards the entire sequence to prevent
     * concurrent modifications.
     *
     * @param credentialId The credential ID to update
     * @param updates The partial updates to apply
     * @throws CredentialException.NotFound if the credential does not exist
     * @throws StorageException.WriteFailed if the update fails
     */
    override suspend fun update(credentialId: String, updates: StoredCredentialUpdate): Unit = mutex.withLock {
        try {
            val key = credentialKey(credentialId)
            val jsonStr = prefs.getString(key, null)
                ?: throw CredentialException.notFound(credentialId)

            val existing = deserializeCredentialOrThrow(jsonStr, credentialId)

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

            val updatedJson = json.encodeToString(updated.toSerializable())
            val success = prefs.edit().putString(key, updatedJson).commit()
            if (!success) {
                throw StorageException.writeFailed("credential:$credentialId")
            }
        } catch (e: StorageException) {
            throw e
        } catch (e: CredentialException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                "credential:$credentialId",
                e
            )
        }
    }

    /**
     * Removes all smart account credentials and sessions from encrypted storage.
     *
     * Only keys managed by this adapter (credential and session prefixes) are removed.
     * Other keys in the SharedPreferences file are not affected.
     *
     * @throws StorageException.WriteFailed if the clear operation fails
     */
    override suspend fun clear(): Unit = mutex.withLock {
        try {
            val editor = prefs.edit()
            val allKeys = prefs.all.keys
            for (key in allKeys) {
                if (key.startsWith(CREDENTIAL_KEY_PREFIX) || key.startsWith(SESSION_KEY_PREFIX)) {
                    editor.remove(key)
                }
            }
            val success = editor.commit()
            if (!success) {
                throw StorageException.writeFailed("clear:all")
            }
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                "clear:all",
                e
            )
        }
    }

    // MARK: - Session Operations

    /**
     * Saves a session to encrypted storage.
     *
     * Only one session is stored at a time. Saving a new session overwrites any
     * previously stored session.
     *
     * @param session The session to save
     * @throws StorageException.WriteFailed if the write operation fails
     */
    override suspend fun saveSession(session: StoredSession): Unit = mutex.withLock {
        try {
            val jsonStr = json.encodeToString(session.toSerializable())
            val success = prefs.edit().putString(SESSION_KEY, jsonStr).commit()
            if (!success) {
                throw StorageException.writeFailed("session")
            }
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                "session",
                e
            )
        }
    }

    /**
     * Retrieves the current session from encrypted storage.
     *
     * Returns null if no session exists, if the session data is corrupted, or if the
     * session has expired. Expired sessions are automatically cleared from storage.
     *
     * @return The current session, or null if not found, corrupted, or expired
     * @throws StorageException.ReadFailed if the read operation fails due to a storage error
     */
    override suspend fun getSession(): StoredSession? = mutex.withLock {
        return try {
            val jsonStr = prefs.getString(SESSION_KEY, null) ?: return null
            val session = deserializeSession(jsonStr)
            if (session == null) {
                return null
            }
            if (session.isExpired) {
                // Clear expired session from storage
                prefs.edit().remove(SESSION_KEY).commit()
                return null
            }
            session
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.readFailed(
                "session",
                e
            )
        }
    }

    /**
     * Clears the current session from encrypted storage.
     *
     * @throws StorageException.WriteFailed if the clear operation fails
     */
    override suspend fun clearSession(): Unit = mutex.withLock {
        try {
            val success = prefs.edit().remove(SESSION_KEY).commit()
            if (!success) {
                throw StorageException.writeFailed("session")
            }
        } catch (e: StorageException) {
            throw e
        } catch (e: Exception) {
            throw StorageException.writeFailed(
                "session",
                e
            )
        }
    }

    // MARK: - Private Helpers

    /**
     * Deserializes a JSON string into a [StoredCredential], returning null on failure.
     *
     * Corrupted or malformed data is logged at warning level and treated as absent.
     */
    private fun deserializeCredential(jsonStr: String, credentialId: String): StoredCredential? {
        return try {
            json.decodeFromString<SerializableCredential>(jsonStr).toStoredCredential()
        } catch (e: Exception) {
            Log.w(TAG, "Corrupted credential data for '$credentialId', skipping: ${e.message}")
            null
        }
    }

    /**
     * Deserializes a JSON string into a [StoredCredential], throwing on failure.
     *
     * Used in contexts (such as [update]) where corrupted data must be reported as an error.
     */
    private fun deserializeCredentialOrThrow(
        jsonStr: String,
        credentialId: String
    ): StoredCredential {
        return try {
            json.decodeFromString<SerializableCredential>(jsonStr).toStoredCredential()
        } catch (e: Exception) {
            throw StorageException.readFailed(
                "credential:$credentialId",
                e
            )
        }
    }

    /**
     * Deserializes a JSON string into a [StoredSession], returning null on failure.
     *
     * Corrupted or malformed data is logged at warning level and treated as absent.
     */
    private fun deserializeSession(jsonStr: String): StoredSession? {
        return try {
            json.decodeFromString<SerializableSession>(jsonStr).toStoredSession()
        } catch (e: Exception) {
            Log.w(TAG, "Corrupted session data, skipping: ${e.message}")
            null
        }
    }

    /**
     * Builds the SharedPreferences key for a credential ID.
     */
    private fun credentialKey(credentialId: String): String {
        return "$CREDENTIAL_KEY_PREFIX$credentialId"
    }

    companion object {
        private const val TAG = "AndroidStorageAdapter"
        private const val PREFS_FILE_NAME = "stellar_smart_account_prefs"
        private const val CREDENTIAL_KEY_PREFIX = "cred_"
        private const val SESSION_KEY_PREFIX = "session_"
        private const val SESSION_KEY = "${SESSION_KEY_PREFIX}current"
    }
}
