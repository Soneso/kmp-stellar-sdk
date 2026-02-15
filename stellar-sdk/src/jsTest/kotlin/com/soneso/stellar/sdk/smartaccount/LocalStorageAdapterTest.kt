//
//  LocalStorageAdapterTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.CredentialException
import com.soneso.stellar.sdk.smartaccount.oz.CredentialDeploymentStatus
import com.soneso.stellar.sdk.smartaccount.oz.StorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredentialUpdate
import com.soneso.stellar.sdk.smartaccount.oz.StoredSession
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [LocalStorageAdapter] exercising the [StorageAdapter] interface contract
 * using the browser's localStorage API.
 *
 * These tests run in the Karma browser test environment where localStorage is available.
 * Each test uses a unique key prefix to avoid interference between tests, and an
 * [AfterTest] method clears all test data.
 *
 * Note: JS browser tests require Chrome to be installed (configured via Karma in Gradle).
 */
class LocalStorageAdapterTest {

    companion object {
        /** Returns true if we are running in a browser environment with localStorage available. */
        private fun isBrowserEnvironment(): Boolean = js(
            "typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'"
        ) as Boolean
    }

    // Use a unique prefix per test run to avoid collisions with other test suites
    private val testPrefix = "test_ls_${kotlin.random.Random.nextInt(100000)}_"

    private fun newAdapter(): LocalStorageAdapter = LocalStorageAdapter(keyPrefix = testPrefix)

    // MARK: - Test Data Helpers

    /**
     * Creates a test public key (65 bytes, uncompressed secp256r1 format starting with 0x04).
     */
    private fun testPublicKey(seed: Int = 0): ByteArray {
        return ByteArray(65) { i ->
            if (i == 0) 0x04.toByte()
            else ((i + seed) % 256).toByte()
        }
    }

    /**
     * Creates a credential with all fields populated.
     */
    private fun fullCredential(
        id: String = "cred-full-001",
        contractId: String = "CBCD1234EFGH5678IJKL9012MNOP3456QRST7890UVWX1234YZAB5678"
    ): StoredCredential = StoredCredential(
        credentialId = id,
        publicKey = testPublicKey(1),
        contractId = contractId,
        deploymentStatus = CredentialDeploymentStatus.PENDING,
        deploymentError = null,
        createdAt = 1700000000000L,
        lastUsedAt = 1700001000000L,
        nickname = "MacBook Pro Touch ID",
        isPrimary = true,
        transports = listOf("internal", "usb"),
        deviceType = "multiDevice",
        backedUp = true
    )

    /**
     * Creates a credential with only required fields (all nullable fields as null).
     */
    private fun minimalCredential(
        id: String = "cred-minimal-001"
    ): StoredCredential = StoredCredential(
        credentialId = id,
        publicKey = testPublicKey(2),
        contractId = null,
        deploymentStatus = CredentialDeploymentStatus.PENDING,
        deploymentError = null,
        createdAt = 1700000000000L,
        lastUsedAt = null,
        nickname = null,
        isPrimary = false,
        transports = null,
        deviceType = null,
        backedUp = null
    )

    @AfterTest
    fun cleanup() {
        // Clear all localStorage keys with our test prefix
        val storage = js("(typeof localStorage !== 'undefined') ? localStorage : null")
        if (storage != null) {
            val keysToRemove = mutableListOf<String>()
            val length = storage.length as Int
            for (i in 0 until length) {
                val key = storage.key(i) as? String
                if (key != null && key.startsWith(testPrefix)) {
                    keysToRemove.add(key)
                }
            }
            for (key in keysToRemove) {
                storage.removeItem(key)
            }
        }
    }

    // MARK: - Credential: Save and Retrieve

    @Test
    fun testSaveAndRetrieveCredential() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        val credential = fullCredential()

        adapter.save(credential)

        val retrieved = adapter.get(credential.credentialId)
        assertNotNull(retrieved)
        assertEquals(credential.credentialId, retrieved.credentialId)
        assertTrue(credential.publicKey.contentEquals(retrieved.publicKey))
        assertEquals(credential.contractId, retrieved.contractId)
        assertEquals(credential.deploymentStatus, retrieved.deploymentStatus)
        assertEquals(credential.nickname, retrieved.nickname)
        assertEquals(credential.isPrimary, retrieved.isPrimary)
    }

    @Test
    fun testSaveCredentialWithAllFieldsPopulated() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        val credential = fullCredential()

        adapter.save(credential)
        val retrieved = adapter.get(credential.credentialId)

        assertNotNull(retrieved)
        assertEquals("cred-full-001", retrieved.credentialId)
        assertTrue(testPublicKey(1).contentEquals(retrieved.publicKey))
        assertEquals("CBCD1234EFGH5678IJKL9012MNOP3456QRST7890UVWX1234YZAB5678", retrieved.contractId)
        assertEquals(CredentialDeploymentStatus.PENDING, retrieved.deploymentStatus)
        assertNull(retrieved.deploymentError)
        assertEquals(1700000000000L, retrieved.createdAt)
        assertEquals(1700001000000L, retrieved.lastUsedAt)
        assertEquals("MacBook Pro Touch ID", retrieved.nickname)
        assertTrue(retrieved.isPrimary)
        assertEquals(listOf("internal", "usb"), retrieved.transports)
        assertEquals("multiDevice", retrieved.deviceType)
        assertEquals(true, retrieved.backedUp)
    }

    @Test
    fun testSaveCredentialWithMinimalFields() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        val credential = minimalCredential()

        adapter.save(credential)
        val retrieved = adapter.get(credential.credentialId)

        assertNotNull(retrieved)
        assertEquals("cred-minimal-001", retrieved.credentialId)
        assertTrue(testPublicKey(2).contentEquals(retrieved.publicKey))
        assertNull(retrieved.contractId)
        assertEquals(CredentialDeploymentStatus.PENDING, retrieved.deploymentStatus)
        assertNull(retrieved.deploymentError)
        assertNull(retrieved.lastUsedAt)
        assertNull(retrieved.nickname)
        assertEquals(false, retrieved.isPrimary)
        assertNull(retrieved.transports)
        assertNull(retrieved.deviceType)
        assertNull(retrieved.backedUp)
    }

    @Test
    fun testGetNonexistentCredentialReturnsNull() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        val result = adapter.get("nonexistent-id")
        assertNull(result)
    }

    // MARK: - Credential: Upsert Behavior

    @Test
    fun testSaveExistingCredentialOverwrites() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        val original = StoredCredential(
            credentialId = "cred-upsert",
            publicKey = testPublicKey(10),
            contractId = "CONTRACT_A",
            deploymentStatus = CredentialDeploymentStatus.PENDING,
            createdAt = 1700000000000L,
            nickname = "Original Name"
        )
        adapter.save(original)

        val replacement = StoredCredential(
            credentialId = "cred-upsert",
            publicKey = testPublicKey(20),
            contractId = "CONTRACT_B",
            deploymentStatus = CredentialDeploymentStatus.FAILED,
            createdAt = 1700002000000L,
            nickname = "Replaced Name",
            deploymentError = "Insufficient balance"
        )
        adapter.save(replacement)

        val retrieved = adapter.get("cred-upsert")
        assertNotNull(retrieved)
        assertTrue(testPublicKey(20).contentEquals(retrieved.publicKey))
        assertEquals("CONTRACT_B", retrieved.contractId)
        assertEquals(CredentialDeploymentStatus.FAILED, retrieved.deploymentStatus)
        assertEquals("Replaced Name", retrieved.nickname)
        assertEquals("Insufficient balance", retrieved.deploymentError)

        // Upsert should not duplicate entries
        val all = adapter.getAll()
        assertEquals(1, all.size)
    }

    // MARK: - Credential: Delete

    @Test
    fun testDeleteCredential() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.delete("cred-full-001")

        val result = adapter.get("cred-full-001")
        assertNull(result)
    }

    @Test
    fun testDeleteNonexistentCredentialDoesNotThrow() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        // Should not throw - silent no-op
        adapter.delete("nonexistent-id")
    }

    @Test
    fun testDeleteRemovesOnlyTargetCredential() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-a"))
        adapter.save(fullCredential("cred-b"))
        adapter.save(fullCredential("cred-c"))

        adapter.delete("cred-b")

        assertNotNull(adapter.get("cred-a"))
        assertNull(adapter.get("cred-b"))
        assertNotNull(adapter.get("cred-c"))
        assertEquals(2, adapter.getAll().size)
    }

    // MARK: - Credential: Get All

    @Test
    fun testGetAllEmptyReturnsEmptyList() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        val all = adapter.getAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun testGetAllWithMultipleCredentials() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-1"))
        adapter.save(fullCredential("cred-2"))
        adapter.save(minimalCredential("cred-3"))

        val all = adapter.getAll()
        assertEquals(3, all.size)

        val ids = all.map { it.credentialId }.toSet()
        assertTrue(ids.contains("cred-1"))
        assertTrue(ids.contains("cred-2"))
        assertTrue(ids.contains("cred-3"))
    }

    // MARK: - Credential: Get by Contract ID

    @Test
    fun testGetByContractIdReturnsMatchingCredentials() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        val contractA = "CAAA1234AAAA5678AAAA9012AAAA3456AAAA7890AAAA1234AAAA5678"
        val contractB = "CBBB1234BBBB5678BBBB9012BBBB3456BBBB7890BBBB1234BBBB5678"

        adapter.save(fullCredential("cred-a1", contractA))
        adapter.save(fullCredential("cred-a2", contractA))
        adapter.save(fullCredential("cred-b1", contractB))

        val resultA = adapter.getByContract(contractA)
        assertEquals(2, resultA.size)
        val idsA = resultA.map { it.credentialId }.toSet()
        assertTrue(idsA.contains("cred-a1"))
        assertTrue(idsA.contains("cred-a2"))

        val resultB = adapter.getByContract(contractB)
        assertEquals(1, resultB.size)
        assertEquals("cred-b1", resultB[0].credentialId)
    }

    @Test
    fun testGetByContractIdNoMatchReturnsEmptyList() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())

        val result = adapter.getByContract("NONEXISTENT_CONTRACT_ID")
        assertTrue(result.isEmpty())
    }

    // MARK: - Credential: Update

    @Test
    fun testUpdateCredentialDeploymentStatus() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.update("cred-full-001", StoredCredentialUpdate(
            deploymentStatus = CredentialDeploymentStatus.FAILED,
            deploymentError = "Transaction failed: insufficient balance"
        ))

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals(CredentialDeploymentStatus.FAILED, updated.deploymentStatus)
        assertEquals("Transaction failed: insufficient balance", updated.deploymentError)
        // Other fields remain unchanged
        assertEquals("MacBook Pro Touch ID", updated.nickname)
        assertTrue(updated.isPrimary)
    }

    @Test
    fun testUpdateCredentialNickname() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.update("cred-full-001", StoredCredentialUpdate(
            nickname = "YubiKey 5"
        ))

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals("YubiKey 5", updated.nickname)
        // Other fields remain unchanged
        assertEquals(1700001000000L, updated.lastUsedAt)
        assertTrue(updated.isPrimary)
    }

    @Test
    fun testUpdateNonexistentCredentialThrows() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()

        assertFailsWith<CredentialException.NotFound> {
            adapter.update("nonexistent-id", StoredCredentialUpdate(
                nickname = "Should fail"
            ))
        }
    }

    // MARK: - Credential: Clear

    @Test
    fun testClearRemovesAllCredentials() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-1"))
        adapter.save(fullCredential("cred-2"))
        adapter.save(minimalCredential("cred-3"))

        adapter.clear()

        val all = adapter.getAll()
        assertTrue(all.isEmpty())
        assertNull(adapter.get("cred-1"))
        assertNull(adapter.get("cred-2"))
        assertNull(adapter.get("cred-3"))
    }

    @Test
    fun testClearOnEmptyAdapterDoesNotThrow() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        // Should not throw
        adapter.clear()
        assertTrue(adapter.getAll().isEmpty())
    }

    // MARK: - Session: Save and Retrieve

    @Test
    fun testSaveAndRetrieveSession() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        val session = StoredSession(
            credentialId = "cred-session-001",
            contractId = "CSESS1234CONT5678RACT9012ADDR3456GOES7890HERE1234ABCD5678",
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        )

        adapter.saveSession(session)

        val retrieved = adapter.getSession()
        assertNotNull(retrieved)
        assertEquals("cred-session-001", retrieved.credentialId)
        assertEquals("CSESS1234CONT5678RACT9012ADDR3456GOES7890HERE1234ABCD5678", retrieved.contractId)
        assertEquals(1700000000000L, retrieved.connectedAt)
    }

    @Test
    fun testGetSessionWhenNoneExistsReturnsNull() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        val result = adapter.getSession()
        assertNull(result)
    }

    @Test
    fun testSaveSessionOverwritesPrevious() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()

        val session1 = StoredSession(
            credentialId = "cred-session-1",
            contractId = "CONTRACT_1",
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        )
        adapter.saveSession(session1)

        val session2 = StoredSession(
            credentialId = "cred-session-2",
            contractId = "CONTRACT_2",
            connectedAt = 1700001000000L,
            expiresAt = Long.MAX_VALUE
        )
        adapter.saveSession(session2)

        val retrieved = adapter.getSession()
        assertNotNull(retrieved)
        assertEquals("cred-session-2", retrieved.credentialId)
        assertEquals("CONTRACT_2", retrieved.contractId)
    }

    // MARK: - Session: Expiry Auto-Clear

    @Test
    fun testExpiredSessionAutoClearedOnGetSession() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        val session = StoredSession(
            credentialId = "cred-expired",
            contractId = "CONTRACT_EXPIRED",
            connectedAt = 1000L,
            expiresAt = 2000L // well in the past
        )
        adapter.saveSession(session)

        // getSession should detect expiry and return null
        val result = adapter.getSession()
        assertNull(result, "Expired session should be auto-cleared and return null")
    }

    // MARK: - Session: Clear

    @Test
    fun testClearSession() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        adapter.saveSession(StoredSession(
            credentialId = "cred-session",
            contractId = "CONTRACT",
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        ))

        adapter.clearSession()

        val result = adapter.getSession()
        assertNull(result)
    }

    @Test
    fun testClearSessionWhenNoneExistsDoesNotThrow() = runTest {
        if (!isBrowserEnvironment()) return@runTest
        val adapter = newAdapter()
        // Should not throw
        adapter.clearSession()
        assertNull(adapter.getSession())
    }

    // MARK: - Interface Conformance

    @Test
    fun testLocalStorageAdapterImplementsStorageAdapterInterface() {
        if (!isBrowserEnvironment()) return
        val adapter: StorageAdapter = LocalStorageAdapter(keyPrefix = testPrefix)
        // Compilation check: LocalStorageAdapter can be assigned to StorageAdapter
        assertNotNull(adapter)
    }
}
