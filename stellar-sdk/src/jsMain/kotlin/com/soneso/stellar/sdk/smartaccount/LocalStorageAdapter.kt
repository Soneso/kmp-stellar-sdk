//
//  LocalStorageAdapter.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 05.10.25.
//  Copyright (c) 2025 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.currentTimeMillis
import com.soneso.stellar.sdk.smartaccount.core.CredentialException
import com.soneso.stellar.sdk.smartaccount.core.StorageException
import com.soneso.stellar.sdk.smartaccount.oz.CredentialDeploymentStatus
import com.soneso.stellar.sdk.smartaccount.oz.StorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredentialUpdate
import com.soneso.stellar.sdk.smartaccount.oz.StoredSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Storage adapter backed by the browser's `localStorage` API.
 *
 * Provides persistent credential and session storage that survives page reloads
 * and browser restarts. Data is serialized as JSON using hex encoding for byte
 * arrays.
 *
 * Limitations:
 * - Storage is limited to approximately 5 MB per origin.
 * - Data is not encrypted and is accessible to any script on the same origin.
 * - Only available in browser environments (throws on Node.js).
 *
 * For production applications with larger storage needs, consider
 * [IndexedDBStorageAdapter].
 *
 * Key scheme:
 * - Credentials: `{prefix}cred_{credentialId_hex}`
 * - Credential index: `{prefix}cred_index`
 * - Session: `{prefix}session`
 *
 * Example:
 * ```kotlin
 * val storage = LocalStorageAdapter()
 * storage.save(credential)
 * val retrieved = storage.get(credential.credentialId)
 * ```
 *
 * @param keyPrefix Prefix for all localStorage keys. Defaults to `stellar_sa_`.
 */
class LocalStorageAdapter(
    private val keyPrefix: String = DEFAULT_KEY_PREFIX
) : StorageAdapter {

    companion object {
        /** Default key prefix for localStorage entries. */
        const val DEFAULT_KEY_PREFIX = "stellar_sa_"

        private const val CRED_KEY_PREFIX = "cred_"
        private const val CRED_INDEX_KEY = "cred_index"
        private const val SESSION_KEY = "session"
    }

    private val mutex = Mutex()

    // MARK: - Credential Operations

    override suspend fun save(credential: StoredCredential): Unit = mutex.withLock {
        val storage = requireLocalStorage()
        try {
            val json = serializeCredential(credential)
            val key = credentialKey(credential.credentialId)
            storage.setItem(key, json)
            addToIndex(storage, credential.credentialId)
        } catch (e: dynamic) {
            val message = e?.message?.toString() ?: "Unknown error"
            if (message.contains("QuotaExceeded", ignoreCase = true) ||
                message.contains("quota", ignoreCase = true)
            ) {
                throw StorageException.writeFailed(
                    "credential:${credential.credentialId}",
                    Exception("localStorage quota exceeded: $message")
                )
            }
            throw StorageException.writeFailed(
                "credential:${credential.credentialId}",
                Exception(message)
            )
        }
    }

    override suspend fun get(credentialId: String): StoredCredential? = mutex.withLock {
        val storage = requireLocalStorage()
        val key = credentialKey(credentialId)
        val json = storage.getItem(key) ?: return@withLock null
        return@withLock try {
            deserializeCredential(json)
        } catch (e: dynamic) {
            throw StorageException.readFailed(
                "credential:$credentialId",
                Exception(e?.message?.toString() ?: "Failed to deserialize credential")
            )
        }
    }

    override suspend fun getByContract(contractId: String): List<StoredCredential> = mutex.withLock {
        val storage = requireLocalStorage()
        val index = readIndex(storage)
        val result = mutableListOf<StoredCredential>()
        for (id in index) {
            val key = credentialKey(id)
            val json = storage.getItem(key)
            if (json != null) {
                try {
                    val cred = deserializeCredential(json)
                    if (cred.contractId == contractId) result.add(cred)
                } catch (e: dynamic) {
                    val message = e?.message?.toString() ?: "unknown error"
                    consoleWarn("LocalStorageAdapter: corrupted credential data for '$id', skipping: $message")
                }
            }
        }
        result
    }

    override suspend fun getAll(): List<StoredCredential> = mutex.withLock {
        val storage = requireLocalStorage()
        val index = readIndex(storage)
        val result = mutableListOf<StoredCredential>()
        for (credentialId in index) {
            val key = credentialKey(credentialId)
            val json = storage.getItem(key)
            if (json != null) {
                try {
                    result.add(deserializeCredential(json))
                } catch (e: dynamic) {
                    val message = e?.message?.toString() ?: "unknown error"
                    val warning = "LocalStorageAdapter: corrupted credential data" +
                        " for '$credentialId', skipping: $message"
                    consoleWarn(warning)
                }
            }
        }
        result
    }

    override suspend fun delete(credentialId: String): Unit = mutex.withLock {
        val storage = requireLocalStorage()
        val key = credentialKey(credentialId)
        storage.removeItem(key)
        removeFromIndex(storage, credentialId)
    }

    override suspend fun update(credentialId: String, updates: StoredCredentialUpdate): Unit = mutex.withLock {
        val storage = requireLocalStorage()
        val key = credentialKey(credentialId)
        val json = storage.getItem(key)
            ?: throw CredentialException.notFound(credentialId)
        val existing = try {
            deserializeCredential(json)
        } catch (e: dynamic) {
            throw StorageException.readFailed(
                "credential:$credentialId",
                Exception(e?.message?.toString() ?: "Failed to deserialize credential")
            )
        }

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

        val updatedJson = serializeCredential(updated)
        try {
            storage.setItem(key, updatedJson)
        } catch (e: dynamic) {
            val message = e?.message?.toString() ?: "Unknown error"
            throw StorageException.writeFailed("credential:$credentialId", Exception(message))
        }
    }

    override suspend fun clear(): Unit = mutex.withLock {
        val storage = requireLocalStorage()
        val index = readIndex(storage)
        for (credentialId in index) {
            storage.removeItem(credentialKey(credentialId))
        }
        storage.removeItem(prefixedKey(CRED_INDEX_KEY))
        clearSessionUnlocked(storage)
    }

    // MARK: - Session Operations

    override suspend fun saveSession(session: StoredSession): Unit = mutex.withLock {
        val storage = requireLocalStorage()
        try {
            val json = serializeSession(session)
            storage.setItem(prefixedKey(SESSION_KEY), json)
        } catch (e: dynamic) {
            val message = e?.message?.toString() ?: "Unknown error"
            throw StorageException.writeFailed("session", Exception(message))
        }
    }

    override suspend fun getSession(): StoredSession? = mutex.withLock {
        val storage = requireLocalStorage()
        val json = storage.getItem(prefixedKey(SESSION_KEY)) ?: return@withLock null
        return@withLock try {
            val session = deserializeSession(json)
            if (session.isExpired) {
                clearSessionUnlocked(storage)
                null
            } else {
                session
            }
        } catch (e: dynamic) {
            throw StorageException.readFailed(
                "session",
                Exception(e?.message?.toString() ?: "Failed to deserialize session")
            )
        }
    }

    override suspend fun clearSession(): Unit = mutex.withLock {
        clearSessionUnlocked(requireLocalStorage())
    }

    /**
     * Removes the session entry from localStorage without acquiring the mutex.
     *
     * Must only be called from within a [mutex]-locked context to avoid
     * deadlocks when composed with other locked operations (e.g., [clear], [getSession]).
     */
    private fun clearSessionUnlocked(storage: dynamic) {
        storage.removeItem(prefixedKey(SESSION_KEY))
    }

    // MARK: - Key Helpers

    private fun prefixedKey(suffix: String): String = "$keyPrefix$suffix"

    private fun credentialKey(credentialId: String): String {
        val hexId = credentialId.encodeToByteArray().toHexString()
        return prefixedKey("${CRED_KEY_PREFIX}$hexId")
    }

    // MARK: - Index Management

    private fun readIndex(storage: dynamic): List<String> {
        val indexKey = prefixedKey(CRED_INDEX_KEY)
        val raw = storage.getItem(indexKey) as? String ?: return emptyList()
        return try {
            val parsed = JSON.parse<Array<String>>(raw)
            parsed.toList()
        } catch (_: dynamic) {
            emptyList()
        }
    }

    private fun writeIndex(storage: dynamic, index: List<String>) {
        val indexKey = prefixedKey(CRED_INDEX_KEY)
        storage.setItem(indexKey, JSON.stringify(index.toTypedArray()))
    }

    private fun addToIndex(storage: dynamic, credentialId: String) {
        val index = readIndex(storage).toMutableList()
        if (credentialId !in index) {
            index.add(credentialId)
        }
        writeIndex(storage, index)
    }

    private fun removeFromIndex(storage: dynamic, credentialId: String) {
        val index = readIndex(storage).toMutableList()
        index.remove(credentialId)
        writeIndex(storage, index)
    }

    // MARK: - Environment Check

    private fun requireLocalStorage(): dynamic {
        val storage = js("(typeof localStorage !== 'undefined') ? localStorage : null")
        if (storage == null) {
            throw StorageException.readFailed(
                "localStorage",
                Exception(
                    "localStorage is not available in this environment. " +
                        "LocalStorageAdapter requires a browser environment."
                )
            )
        }
        return storage
    }
}

// MARK: - JSON Serialization Helpers

/**
 * Hex encoding/decoding utilities for byte array serialization in storage.
 *
 * Uses hex encoding (rather than Base64) for byte arrays because hex provides
 * unambiguous round-trip fidelity and is simpler to debug in browser devtools.
 */
private object StorageHex {
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /**
     * Encodes a byte array to a lowercase hex string.
     */
    fun encode(bytes: ByteArray): String {
        val result = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val i = byte.toInt() and 0xFF
            result.append(HEX_CHARS[i shr 4])
            result.append(HEX_CHARS[i and 0x0F])
        }
        return result.toString()
    }

    /**
     * Decodes a lowercase hex string to a byte array.
     */
    fun decode(hex: String): ByteArray {
        if (hex.isEmpty()) return ByteArray(0)
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            val high = hexCharToInt(hex[i * 2])
            val low = hexCharToInt(hex[i * 2 + 1])
            ((high shl 4) or low).toByte()
        }
    }

    private fun hexCharToInt(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> throw IllegalArgumentException("Invalid hex character: '$char'")
    }
}

/**
 * Extension to convert a ByteArray to a hex string for storage keys.
 */
private fun ByteArray.toHexString(): String = StorageHex.encode(this)

/**
 * Serializes a [StoredCredential] to a JSON string.
 *
 * ByteArray fields (publicKey) are encoded as hex strings.
 */
private fun serializeCredential(credential: StoredCredential): String {
    val obj = js("{}")
    obj.credentialId = credential.credentialId
    obj.publicKey = StorageHex.encode(credential.publicKey)
    obj.contractId = credential.contractId
    obj.deploymentStatus = credential.deploymentStatus.name
    obj.deploymentError = credential.deploymentError
    obj.createdAt = credential.createdAt.toDouble()
    obj.lastUsedAt = credential.lastUsedAt?.toDouble()
    obj.nickname = credential.nickname
    obj.isPrimary = credential.isPrimary
    obj.transports = credential.transports?.toTypedArray()
    obj.deviceType = credential.deviceType
    obj.backedUp = credential.backedUp
    return JSON.stringify(obj)
}

/**
 * Deserializes a [StoredCredential] from a JSON string.
 *
 * Hex-encoded byte array fields are decoded back to ByteArray.
 */
private fun deserializeCredential(json: String): StoredCredential {
    val obj = JSON.parse<dynamic>(json)

    val publicKeyHex = obj.publicKey as String
    val publicKey = StorageHex.decode(publicKeyHex)

    val statusName = obj.deploymentStatus as String
    val deploymentStatus = CredentialDeploymentStatus.valueOf(statusName)

    val transportsRaw = obj.transports
    val transports: List<String>? = if (transportsRaw != null && transportsRaw != undefined) {
        (transportsRaw as Array<String>).toList()
    } else {
        null
    }

    val lastUsedAtRaw = obj.lastUsedAt
    val lastUsedAt: Long? = if (lastUsedAtRaw != null && lastUsedAtRaw != undefined) {
        (lastUsedAtRaw as Double).toLong()
    } else {
        null
    }

    val backedUpRaw = obj.backedUp
    val backedUp: Boolean? = if (backedUpRaw != null && backedUpRaw != undefined) {
        backedUpRaw as Boolean
    } else {
        null
    }

    return StoredCredential(
        credentialId = obj.credentialId as String,
        publicKey = publicKey,
        contractId = obj.contractId as? String,
        deploymentStatus = deploymentStatus,
        deploymentError = obj.deploymentError as? String,
        createdAt = (obj.createdAt as Double).toLong(),
        lastUsedAt = lastUsedAt,
        nickname = obj.nickname as? String,
        isPrimary = obj.isPrimary as Boolean,
        transports = transports,
        deviceType = obj.deviceType as? String,
        backedUp = backedUp
    )
}

/**
 * Serializes a [StoredSession] to a JSON string.
 */
private fun serializeSession(session: StoredSession): String {
    val obj = js("{}")
    obj.credentialId = session.credentialId
    obj.contractId = session.contractId
    obj.connectedAt = session.connectedAt.toDouble()
    obj.expiresAt = session.expiresAt.toDouble()
    return JSON.stringify(obj)
}

/**
 * Deserializes a [StoredSession] from a JSON string.
 */
private fun deserializeSession(json: String): StoredSession {
    val obj = JSON.parse<dynamic>(json)
    return StoredSession(
        credentialId = obj.credentialId as String,
        contractId = obj.contractId as String,
        connectedAt = (obj.connectedAt as Double).toLong(),
        expiresAt = (obj.expiresAt as Double).toLong()
    )
}

/**
 * Emits a warning to the browser/Node.js console.
 *
 * Equivalent to `console.warn(message)` in JavaScript.
 */
private fun consoleWarn(message: String) {
    js("console.warn(message)")
}
