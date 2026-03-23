//
//  IndexedDBStorageAdapter.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 05.10.25.
//  Copyright (c) 2025 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.CredentialException
import com.soneso.stellar.sdk.smartaccount.core.StorageException
import com.soneso.stellar.sdk.smartaccount.oz.CredentialDeploymentStatus
import com.soneso.stellar.sdk.smartaccount.oz.StorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredentialUpdate
import com.soneso.stellar.sdk.smartaccount.oz.StoredSession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Storage adapter backed by the browser's IndexedDB API.
 *
 * Recommended for production web applications. Provides structured storage
 * with indexing support, larger storage limits than localStorage, and an
 * async API that does not block the main thread.
 *
 * Features:
 * - Upsert semantics for credential saves (uses IndexedDB `put`)
 * - Efficient contract ID lookups via an IndexedDB index
 * - Transactional data integrity for all operations
 * - Automatic database schema migration via version management
 * - Binary data support without serialization overhead
 *
 * Schema:
 * - Object store `credentials`: keyPath `credentialId`, index on `contractId`
 * - Object store `sessions`: keyPath `key`
 *
 * Example:
 * ```kotlin
 * val storage = IndexedDBStorageAdapter()
 * storage.save(credential)
 * val all = storage.getAll()
 * val byContract = storage.getByContract("CABC...")
 * storage.close()
 * ```
 *
 * @param dbName Database name. Defaults to `stellar_smart_account`.
 */
class IndexedDBStorageAdapter(
    private val dbName: String = DEFAULT_DB_NAME
) : StorageAdapter {

    companion object {
        /** Default IndexedDB database name. */
        const val DEFAULT_DB_NAME = "stellar_smart_account"

        /** Current database schema version. */
        const val DB_VERSION = 1

        internal const val STORE_CREDENTIALS = "credentials"
        internal const val STORE_SESSIONS = "sessions"
        internal const val INDEX_CONTRACT_ID = "contractId"
        internal const val INDEX_CREATED_AT = "createdAt"
        internal const val INDEX_IS_PRIMARY = "isPrimary"
        internal const val SESSION_KEY = "current"
    }

    /**
     * Cached database connection. Lazily opened on first access.
     * Typed as Any? to avoid dynamic null-safety issues with `?.let`.
     */
    private var cachedDb: Any? = null

    private val mutex = Mutex()

    // MARK: - Database Lifecycle

    /**
     * Opens or returns the cached IndexedDB database connection.
     *
     * On first call, opens the database and creates object stores and indexes
     * if needed (via the `onupgradeneeded` event). Subsequent calls return
     * the cached connection.
     *
     * @return The IDBDatabase instance
     * @throws StorageException.ReadFailed if IndexedDB is unavailable or the open fails
     */
    private suspend fun getDb(): dynamic {
        val existing = cachedDb
        if (existing != null) {
            return existing
        }

        val database = openDatabase()
        cachedDb = database
        return database
    }

    private suspend fun openDatabase(): dynamic = suspendCancellableCoroutine { cont ->
        val idb = js("(typeof indexedDB !== 'undefined') ? indexedDB : null")
        if (idb == null) {
            cont.resumeWithException(
                StorageException.readFailed(
                    "indexedDB",
                    Exception(
                        "IndexedDB is not available in this environment. " +
                            "IndexedDBStorageAdapter requires a browser environment."
                    )
                )
            )
            return@suspendCancellableCoroutine
        }

        val request = idb.open(dbName, DB_VERSION)

        request.onerror = { _: dynamic ->
            val errorMsg = request.error?.message?.toString() ?: "Unknown error"
            cont.resumeWithException(
                StorageException.readFailed(
                    "indexedDB:open",
                    Exception("Failed to open IndexedDB '$dbName': $errorMsg")
                )
            )
            Unit
        }

        request.onsuccess = { _: dynamic ->
            cont.resume(request.result)
            Unit
        }

        request.onupgradeneeded = { event: dynamic ->
            val database = event.target.result
            createSchema(database)
            Unit
        }
    }

    /**
     * Creates object stores and indexes for a new or upgraded database.
     *
     * Called during the `onupgradeneeded` event when the database is first
     * created or when the version number increases.
     */
    private fun createSchema(database: dynamic) {
        // Create credentials object store if it does not exist
        val objectStoreNames = database.objectStoreNames
        if (!containsStoreName(objectStoreNames, STORE_CREDENTIALS)) {
            val credStore = database.createObjectStore(
                STORE_CREDENTIALS,
                js("({keyPath: 'credentialId'})")
            )
            credStore.createIndex(INDEX_CONTRACT_ID, INDEX_CONTRACT_ID, js("({unique: false})"))
            credStore.createIndex(INDEX_CREATED_AT, INDEX_CREATED_AT, js("({unique: false})"))
            credStore.createIndex(INDEX_IS_PRIMARY, INDEX_IS_PRIMARY, js("({unique: false})"))
        }

        // Create sessions object store if it does not exist
        if (!containsStoreName(objectStoreNames, STORE_SESSIONS)) {
            database.createObjectStore(STORE_SESSIONS, js("({keyPath: 'key'})"))
        }
    }

    /**
     * Checks whether a DOMStringList contains a given store name.
     */
    private fun containsStoreName(storeNames: dynamic, name: String): Boolean {
        return storeNames.contains(name) as Boolean
    }

    /**
     * Closes the database connection and releases resources.
     *
     * After calling close, the adapter can be used again and will
     * reopen the database on the next operation.
     */
    fun close() {
        val existing = cachedDb
        if (existing != null) {
            existing.asDynamic().close()
        }
        cachedDb = null
    }

    // MARK: - Credential Operations

    override suspend fun save(credential: StoredCredential): Unit = mutex.withLock {
        val database = getDb()
        val obj = credentialToJs(credential)
        try {
            withObjectStore(database, STORE_CREDENTIALS, "readwrite") { store ->
                store.put(obj)
            }
        } catch (e: Throwable) {
            throw StorageException.writeFailed(
                "credential:${credential.credentialId}",
                e
            )
        }
    }

    override suspend fun get(credentialId: String): StoredCredential? = mutex.withLock {
        val database = getDb()
        val result = try {
            withObjectStore(database, STORE_CREDENTIALS, "readonly") { store ->
                store.get(credentialId)
            }
        } catch (e: Throwable) {
            throw StorageException.readFailed("credential:$credentialId", e)
        }
        if (result == null || result == undefined) return@withLock null
        jsToCredential(result)
    }

    override suspend fun getByContract(contractId: String): List<StoredCredential> = mutex.withLock {
        val database = getDb()
        val results = try {
            withIndex(database, STORE_CREDENTIALS, INDEX_CONTRACT_ID, "readonly") { index ->
                index.getAll(contractId)
            }
        } catch (e: Throwable) {
            throw StorageException.readFailed("credentials:contractId=$contractId", e)
        }
        jsArrayToCredentials(results)
    }

    override suspend fun getAll(): List<StoredCredential> = mutex.withLock {
        val database = getDb()
        val results = try {
            withObjectStore(database, STORE_CREDENTIALS, "readonly") { store ->
                store.getAll()
            }
        } catch (e: Throwable) {
            throw StorageException.readFailed("credentials:all", e)
        }
        jsArrayToCredentials(results)
    }

    override suspend fun delete(credentialId: String): Unit = mutex.withLock {
        val database = getDb()
        try {
            withObjectStore(database, STORE_CREDENTIALS, "readwrite") { store ->
                store.delete(credentialId)
            }
        } catch (e: Throwable) {
            throw StorageException.writeFailed("credential:$credentialId", e)
        }
    }

    override suspend fun update(credentialId: String, updates: StoredCredentialUpdate): Unit = mutex.withLock {
        val database = getDb()
        val result = try {
            withObjectStore(database, STORE_CREDENTIALS, "readonly") { store ->
                store.get(credentialId)
            }
        } catch (e: Throwable) {
            throw StorageException.readFailed("credential:$credentialId", e)
        }
        if (result == null || result == undefined) {
            throw CredentialException.notFound(credentialId)
        }
        val existing = jsToCredential(result)

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

        val obj = credentialToJs(updated)
        try {
            withObjectStore(database, STORE_CREDENTIALS, "readwrite") { store ->
                store.put(obj)
            }
        } catch (e: Throwable) {
            throw StorageException.writeFailed("credential:$credentialId", e)
        }
    }

    override suspend fun clear(): Unit = mutex.withLock {
        val database = getDb()
        try {
            withObjectStore(database, STORE_CREDENTIALS, "readwrite") { store ->
                store.clear()
            }
        } catch (e: Throwable) {
            throw StorageException.writeFailed("credentials:clear", e)
        }
        clearSessionUnlocked(database)
    }

    // MARK: - Session Operations

    override suspend fun saveSession(session: StoredSession): Unit = mutex.withLock {
        val database = getDb()
        val obj = sessionToJs(session)
        try {
            withObjectStore(database, STORE_SESSIONS, "readwrite") { store ->
                store.put(obj)
            }
        } catch (e: Throwable) {
            throw StorageException.writeFailed("session", e)
        }
    }

    override suspend fun getSession(): StoredSession? = mutex.withLock {
        val database = getDb()
        val result = try {
            withObjectStore(database, STORE_SESSIONS, "readonly") { store ->
                store.get(SESSION_KEY)
            }
        } catch (e: Throwable) {
            throw StorageException.readFailed("session", e)
        }
        if (result == null || result == undefined) return@withLock null

        val session = jsToSession(result)
        if (session.isExpired) {
            clearSessionUnlocked(database)
            null
        } else {
            session
        }
    }

    override suspend fun clearSession(): Unit = mutex.withLock {
        val database = getDb()
        clearSessionUnlocked(database)
    }

    /**
     * Deletes the current session from IndexedDB without acquiring the mutex.
     *
     * Must only be called from within a [mutex]-locked context to avoid
     * deadlocks when composed with other locked operations (e.g., [clear], [getSession]).
     */
    private suspend fun clearSessionUnlocked(database: dynamic) {
        try {
            withObjectStore(database, STORE_SESSIONS, "readwrite") { store ->
                store.delete(SESSION_KEY)
            }
        } catch (e: Throwable) {
            throw StorageException.writeFailed("session:clear", e)
        }
    }

    // MARK: - Database Deletion

    /**
     * Deletes the entire IndexedDB database.
     *
     * This is a destructive operation that removes all stored credentials
     * and sessions permanently.
     *
     * @param name Database name to delete. Defaults to [DEFAULT_DB_NAME].
     * @throws StorageException.WriteFailed if deletion fails
     */
    suspend fun deleteDatabase(name: String = DEFAULT_DB_NAME) {
        close()
        suspendCancellableCoroutine { cont ->
            val idb = js("(typeof indexedDB !== 'undefined') ? indexedDB : null")
            if (idb == null) {
                cont.resumeWithException(
                    StorageException.writeFailed(
                        "indexedDB:delete",
                        Exception("IndexedDB is not available")
                    )
                )
                return@suspendCancellableCoroutine
            }
            val request = idb.deleteDatabase(name)
            request.onsuccess = { _: dynamic ->
                cont.resume(Unit)
                Unit
            }
            request.onerror = { _: dynamic ->
                val errorMsg = request.error?.message?.toString() ?: "Unknown error"
                cont.resumeWithException(
                    StorageException.writeFailed(
                        "indexedDB:delete",
                        Exception("Failed to delete database '$name': $errorMsg")
                    )
                )
                Unit
            }
        }
    }

    // MARK: - IndexedDB Transaction Helpers

    /**
     * Executes a callback on an object store within a transaction and
     * waits for the resulting IDBRequest to complete.
     *
     * @param database The IDBDatabase instance
     * @param storeName Object store name
     * @param mode Transaction mode ("readonly" or "readwrite")
     * @param block Lambda that receives the object store and returns an IDBRequest
     * @return The result of the IDBRequest
     */
    private suspend fun withObjectStore(
        database: dynamic,
        storeName: String,
        mode: String,
        block: (store: dynamic) -> dynamic
    ): dynamic = suspendCancellableCoroutine { cont ->
        try {
            val transaction = database.transaction(storeName, mode)
            val store = transaction.objectStore(storeName)
            val request = block(store)

            request.onsuccess = { _: dynamic ->
                cont.resume(request.result)
                Unit
            }
            request.onerror = { _: dynamic ->
                val errorMsg = request.error?.message?.toString() ?: "IndexedDB operation failed"
                cont.resumeWithException(Exception(errorMsg))
                Unit
            }
        } catch (e: Throwable) {
            cont.resumeWithException(e)
        }
    }

    /**
     * Executes a callback on an index within an object store and waits
     * for the resulting IDBRequest to complete.
     *
     * @param database The IDBDatabase instance
     * @param storeName Object store name
     * @param indexName Index name
     * @param mode Transaction mode
     * @param block Lambda that receives the index and returns an IDBRequest
     * @return The result of the IDBRequest
     */
    private suspend fun withIndex(
        database: dynamic,
        storeName: String,
        indexName: String,
        mode: String,
        block: (index: dynamic) -> dynamic
    ): dynamic = suspendCancellableCoroutine { cont ->
        try {
            val transaction = database.transaction(storeName, mode)
            val store = transaction.objectStore(storeName)
            val index = store.index(indexName)
            val request = block(index)

            request.onsuccess = { _: dynamic ->
                cont.resume(request.result)
                Unit
            }
            request.onerror = { _: dynamic ->
                val errorMsg = request.error?.message?.toString() ?: "IndexedDB index query failed"
                cont.resumeWithException(Exception(errorMsg))
                Unit
            }
        } catch (e: Throwable) {
            cont.resumeWithException(e)
        }
    }

    // MARK: - JS Object Conversion

    /**
     * Converts a [StoredCredential] to a plain JS object for IndexedDB storage.
     *
     * ByteArray fields are stored as regular JS arrays (number[]) since IndexedDB
     * supports structured cloning of arrays natively.
     */
    private fun credentialToJs(credential: StoredCredential): dynamic {
        val obj = js("{}")
        obj.credentialId = credential.credentialId
        obj.publicKey = credential.publicKey.toTypedArray()
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
        return obj
    }

    /**
     * Converts a plain JS object from IndexedDB back to a [StoredCredential].
     */
    private fun jsToCredential(obj: dynamic): StoredCredential {
        val publicKeyArray = obj.publicKey
        val publicKey = jsArrayToByteArray(publicKeyArray)

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
     * Converts a [StoredSession] to a plain JS object for IndexedDB storage.
     *
     * Adds a `key` field as the keyPath for the sessions object store.
     */
    private fun sessionToJs(session: StoredSession): dynamic {
        val obj = js("{}")
        obj.key = SESSION_KEY
        obj.credentialId = session.credentialId
        obj.contractId = session.contractId
        obj.connectedAt = session.connectedAt.toDouble()
        obj.expiresAt = session.expiresAt.toDouble()
        return obj
    }

    /**
     * Converts a plain JS object from IndexedDB back to a [StoredSession].
     */
    private fun jsToSession(obj: dynamic): StoredSession {
        return StoredSession(
            credentialId = obj.credentialId as String,
            contractId = obj.contractId as String,
            connectedAt = (obj.connectedAt as Double).toLong(),
            expiresAt = (obj.expiresAt as Double).toLong()
        )
    }

    /**
     * Converts a JS number array to a Kotlin ByteArray.
     *
     * IndexedDB stores byte data as regular JS arrays of numbers via
     * structured cloning. This function converts them back to ByteArray.
     */
    private fun jsArrayToByteArray(jsArray: dynamic): ByteArray {
        val length = jsArray.length as Int
        return ByteArray(length) { i ->
            (jsArray[i] as Number).toByte()
        }
    }

    /**
     * Converts a JS array of credential objects to a list of [StoredCredential].
     */
    private fun jsArrayToCredentials(jsArray: dynamic): List<StoredCredential> {
        if (jsArray == null || jsArray == undefined) return emptyList()
        val length = jsArray.length as Int
        return List(length) { i ->
            jsToCredential(jsArray[i])
        }
    }
}
