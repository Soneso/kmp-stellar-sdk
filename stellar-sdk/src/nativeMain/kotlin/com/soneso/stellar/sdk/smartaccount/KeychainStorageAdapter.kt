//
//  KeychainStorageAdapter.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class
)

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
import com.soneso.stellar.sdk.smartaccount.oz.applyUpdate
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretObjCPointerOrNull
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.OSStatus

// MARK: - CFStringRef to NSString Helper

/**
 * Interprets a CFStringRef as NSString via toll-free bridging.
 *
 * CFStringRef and NSString are toll-free bridged in the Apple runtime,
 * meaning they share the same underlying memory layout. This function
 * reinterprets the C pointer as an Objective-C object without transferring
 * ownership (unlike CFBridgingRelease which would incorrectly release
 * global constants).
 */
private fun CFStringRef?.asNSString(): NSString? {
    if (this == null) return null
    return interpretObjCPointerOrNull<NSString>(this.rawValue)
}

// MARK: - KeychainStorageAdapter

/**
 * Persistent storage adapter for smart account credentials and sessions using the iOS/macOS Keychain.
 *
 * This adapter uses the Security framework's Keychain Services API (`SecItem*`) to store
 * credential and session data. Data is stored as generic password items with JSON payloads.
 *
 * Keychain storage provides:
 * - Encryption at rest by the OS
 * - Access control via `kSecAttrAccessibleAfterFirstUnlock`
 * - Persistence across app reinstalls (unless explicitly deleted)
 * - iCloud Keychain sync (if the user has it enabled)
 *
 * While [UserDefaultsStorageAdapter] is adequate for public key storage,
 * this adapter is appropriate for use cases requiring stronger data protection.
 *
 * Key scheme:
 * - Service: `com.soneso.stellar.smartaccount` (configurable)
 * - Account: `cred_{credentialId}` for credentials, `session_current` for the session,
 *   `credential_index` for the credential ID list
 *
 * Example:
 * ```kotlin
 * val storage = KeychainStorageAdapter()
 * storage.save(credential)
 * val loaded = storage.get(credential.credentialId)
 * ```
 *
 * @param serviceName The Keychain service name for grouping items.
 *   Defaults to `"com.soneso.stellar.smartaccount"`.
 */
class KeychainStorageAdapter(
    private val serviceName: String = DEFAULT_SERVICE_NAME
) : StorageAdapter {

    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        /** Default Keychain service name. */
        const val DEFAULT_SERVICE_NAME = "com.soneso.stellar.smartaccount"

        // Key prefixes and names
        private const val KEY_PREFIX_CREDENTIAL = "cred_"
        private const val KEY_CREDENTIAL_INDEX = "credential_index"
        private const val KEY_SESSION = "session_current"
    }

    // MARK: - Credential Operations

    override suspend fun save(credential: StoredCredential): Unit = mutex.withLock {
        try {
            val serializable = credential.toSerializable()
            val jsonString = json.encodeToString(serializable)
            val account = KEY_PREFIX_CREDENTIAL + credential.credentialId

            keychainUpsert(account = account, data = jsonString)

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
            val account = KEY_PREFIX_CREDENTIAL + credentialId
            keychainDelete(account = account)

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

            val updated = existing.applyUpdate(updates)

            val serializable = updated.toSerializable()
            val jsonString = json.encodeToString(serializable)
            val account = KEY_PREFIX_CREDENTIAL + credentialId

            keychainUpsert(account = account, data = jsonString)
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
                keychainDelete(account = KEY_PREFIX_CREDENTIAL + id)
            }

            // Remove the index itself
            keychainDelete(account = KEY_CREDENTIAL_INDEX)

            // Remove the session
            keychainDelete(account = KEY_SESSION)
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
            keychainUpsert(account = KEY_SESSION, data = jsonString)
        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.WriteFailed(
                "Storage write failed for key: session", e
            )
        }
    }

    override suspend fun getSession(): StoredSession? = mutex.withLock {
        try {
            val jsonString = keychainRead(account = KEY_SESSION) ?: return@withLock null
            val serializable = json.decodeFromString<SerializableSession>(jsonString)
            val session = serializable.toStoredSession()

            if (session.isExpired) {
                // Clear expired session
                keychainDelete(account = KEY_SESSION)
                return@withLock null
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
            keychainDelete(account = KEY_SESSION)
        } catch (e: Exception) {
            if (e is StorageException) throw e
            throw StorageException.WriteFailed(
                "Storage write failed for key: session", e
            )
        }
    }

    // MARK: - Internal Helpers

    /**
     * Reads a single credential from the Keychain by its ID.
     *
     * Must be called within the mutex lock.
     *
     * @return The credential, or null if not found.
     */
    private fun readCredential(credentialId: String): StoredCredential? {
        val account = KEY_PREFIX_CREDENTIAL + credentialId
        val jsonString = keychainRead(account = account) ?: return null
        val serializable = json.decodeFromString<SerializableCredential>(jsonString)
        return serializable.toStoredCredential()
    }

    /**
     * Reads the credential index from the Keychain.
     *
     * Returns an empty index if none exists.
     * Must be called within the mutex lock.
     */
    private fun readIndex(): CredentialIndex {
        val jsonString = keychainRead(account = KEY_CREDENTIAL_INDEX)
            ?: return CredentialIndex(ids = emptyList())
        return json.decodeFromString(jsonString)
    }

    /**
     * Writes the credential index to the Keychain.
     *
     * Must be called within the mutex lock.
     */
    private fun writeIndex(index: CredentialIndex) {
        val jsonString = json.encodeToString(index)
        keychainUpsert(account = KEY_CREDENTIAL_INDEX, data = jsonString)
    }

    // MARK: - Keychain Primitives

    /**
     * Creates a Keychain query dictionary for the given account.
     *
     * Security framework constants (kSecClass, kSecAttrService, etc.) are CFStringRef
     * values. They are reinterpreted as NSString via toll-free bridging for use
     * as NSDictionary keys.
     *
     * @param account The Keychain account identifier.
     * @return A mutable dictionary with the base query parameters.
     */
    @Suppress("UNCHECKED_CAST")
    private fun baseQuery(account: String): NSMutableDictionary {
        val dict = NSMutableDictionary()
        dict.setObject(kSecClassGenericPassword.asNSString(), forKey = kSecClass.asNSString()!!)
        dict.setObject(serviceName, forKey = kSecAttrService.asNSString()!!)
        dict.setObject(account, forKey = kSecAttrAccount.asNSString()!!)
        return dict
    }

    /**
     * Reads a string value from the Keychain.
     *
     * @param account The Keychain account (key) to read.
     * @return The stored string value, or null if not found.
     * @throws StorageException.ReadFailed if the Keychain query fails with an unexpected error.
     */
    @Suppress("UNCHECKED_CAST")
    private fun keychainRead(account: String): String? = memScoped {
        val query = baseQuery(account)
        query.setObject(true, forKey = kSecReturnData.asNSString()!!)
        query.setObject(
            kSecMatchLimitOne.asNSString()!!,
            forKey = kSecMatchLimit.asNSString()!!
        )

        val result = alloc<ObjCObjectVar<Any?>>()

        val status: OSStatus = SecItemCopyMatching(
            query as CFDictionaryRef,
            result.ptr as CPointer<CFTypeRefVar>
        )

        when (status) {
            errSecSuccess -> {
                val data = result.value as? NSData ?: return null
                NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
            }
            errSecItemNotFound -> null
            else -> throw StorageException.ReadFailed(
                "Keychain read failed for account '$account' with OSStatus: $status"
            )
        }
    }

    /**
     * Inserts or updates a string value in the Keychain (upsert semantics).
     *
     * If the item already exists, it is updated. Otherwise, a new item is created
     * with `kSecAttrAccessibleAfterFirstUnlock` accessibility.
     *
     * @param account The Keychain account (key) to write.
     * @param data The string value to store.
     * @throws StorageException.WriteFailed if the Keychain operation fails.
     */
    @Suppress("UNCHECKED_CAST")
    private fun keychainUpsert(account: String, data: String) {
        val nsData = (data as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw StorageException.WriteFailed(
                "Failed to encode string data for account '$account'"
            )

        // Try to add first
        val addQuery = baseQuery(account)
        addQuery.setObject(nsData, forKey = kSecValueData.asNSString()!!)
        addQuery.setObject(
            kSecAttrAccessibleAfterFirstUnlock.asNSString()!!,
            forKey = kSecAttrAccessible.asNSString()!!
        )

        val addStatus: OSStatus = SecItemAdd(addQuery as CFDictionaryRef, null)

        when (addStatus) {
            errSecSuccess -> {
                // Item added successfully
            }
            errSecDuplicateItem -> {
                // Item exists, update it
                val searchQuery = baseQuery(account)

                val updateDict = NSMutableDictionary()
                updateDict.setObject(nsData, forKey = kSecValueData.asNSString()!!)
                updateDict.setObject(
                    kSecAttrAccessibleAfterFirstUnlock.asNSString()!!,
                    forKey = kSecAttrAccessible.asNSString()!!
                )

                val updateStatus: OSStatus = SecItemUpdate(
                    searchQuery as CFDictionaryRef,
                    updateDict as CFDictionaryRef
                )

                if (updateStatus != errSecSuccess) {
                    throw StorageException.WriteFailed(
                        "Keychain update failed for account '$account' with OSStatus: $updateStatus"
                    )
                }
            }
            else -> {
                throw StorageException.WriteFailed(
                    "Keychain add failed for account '$account' with OSStatus: $addStatus"
                )
            }
        }
    }

    /**
     * Deletes an item from the Keychain.
     *
     * Does not throw if the item does not exist (idempotent delete).
     *
     * @param account The Keychain account (key) to delete.
     * @throws StorageException.WriteFailed if the Keychain delete fails with an unexpected error.
     */
    @Suppress("UNCHECKED_CAST")
    private fun keychainDelete(account: String) {
        val query = baseQuery(account)

        val status: OSStatus = SecItemDelete(query as CFDictionaryRef)

        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw StorageException.WriteFailed(
                "Keychain delete failed for account '$account' with OSStatus: $status"
            )
        }
    }
}
