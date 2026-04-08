//
//  UserDefaultsStorageAdapterTest.kt
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
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [UserDefaultsStorageAdapter] exercising the [StorageAdapter] interface contract
 * using NSUserDefaults on macOS.
 *
 * Each test uses a unique suite name to provide full isolation between tests and
 * prevent stale data from interfering. The [AfterTest] method removes the suite's
 * persistent domain to clean up after each test.
 *
 * NSUserDefaults is available in the macOS test environment without additional setup.
 */
class UserDefaultsStorageAdapterTest {

    // Use a unique suite name per test run to avoid collisions
    private val testSuiteName = "com.soneso.stellar.test.${kotlin.random.Random.nextInt(100000)}"

    private fun newAdapter(): UserDefaultsStorageAdapter =
        UserDefaultsStorageAdapter(suiteName = testSuiteName)

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
        // Remove the persistent domain for our test suite to clean up
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(testSuiteName)
        NSUserDefaults.standardUserDefaults.synchronize()
    }

    // MARK: - Credential: Save and Retrieve

    @Test
    fun testSaveAndRetrieveCredential() = runTest {
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
        val adapter = newAdapter()
        val result = adapter.get("nonexistent-id")
        assertNull(result)
    }

    // MARK: - Credential: Upsert Behavior

    @Test
    fun testSaveExistingCredentialOverwrites() = runTest {
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

    // MARK: - Credential: Update

    @Test
    fun testUpdateCredentialDeploymentStatus() = runTest {
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
    fun testUpdateCredentialContractId() = runTest {
        val adapter = newAdapter()
        adapter.save(minimalCredential())

        val newContractId = "CNEW1234CONT5678RACT9012ADDR3456GOES7890HERE1234ABCD5678"
        adapter.update("cred-minimal-001", StoredCredentialUpdate(
            contractId = newContractId
        ))

        val updated = adapter.get("cred-minimal-001")
        assertNotNull(updated)
        assertEquals(newContractId, updated.contractId)
    }

    @Test
    fun testUpdateNonexistentCredentialThrows() = runTest {
        val adapter = newAdapter()

        assertFailsWith<CredentialException.NotFound> {
            adapter.update("nonexistent-id", StoredCredentialUpdate(
                nickname = "Should fail"
            ))
        }
    }

    @Test
    fun testUpdateMultipleFieldsAtOnce() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.update("cred-full-001", StoredCredentialUpdate(
            deploymentStatus = CredentialDeploymentStatus.FAILED,
            deploymentError = "Network timeout",
            lastUsedAt = 1700099000000L,
            nickname = "Updated Device",
            isPrimary = false
        ))

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals(CredentialDeploymentStatus.FAILED, updated.deploymentStatus)
        assertEquals("Network timeout", updated.deploymentError)
        assertEquals(1700099000000L, updated.lastUsedAt)
        assertEquals("Updated Device", updated.nickname)
        assertEquals(false, updated.isPrimary)
        // Unchanged fields
        assertEquals(listOf("internal", "usb"), updated.transports)
        assertEquals("multiDevice", updated.deviceType)
        assertEquals(true, updated.backedUp)
    }

    // MARK: - Credential: Delete

    @Test
    fun testDeleteCredential() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.delete("cred-full-001")

        val result = adapter.get("cred-full-001")
        assertNull(result)
    }

    @Test
    fun testDeleteNonexistentCredentialDoesNotThrow() = runTest {
        val adapter = newAdapter()
        // Should not throw - silent no-op
        adapter.delete("nonexistent-id")
    }

    @Test
    fun testDeleteRemovesOnlyTargetCredential() = runTest {
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
        val adapter = newAdapter()
        val all = adapter.getAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun testGetAllWithMultipleCredentials() = runTest {
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
    fun testGetByContractExcludesNullContractId() = runTest {
        val adapter = newAdapter()
        // Save credentials with null contractId — they must not appear in getByContract results
        adapter.save(minimalCredential("cred-no-contract-1"))
        adapter.save(minimalCredential("cred-no-contract-2"))

        val result = adapter.getByContract("someContract")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testGetByContractIdReturnsMatchingCredentials() = runTest {
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
        val adapter = newAdapter()
        adapter.save(fullCredential())

        val result = adapter.getByContract("NONEXISTENT_CONTRACT_ID")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testUpdateIsPrimaryFalseToTrue() = runTest {
        val adapter = newAdapter()
        val credential = minimalCredential("cred-primary-upgrade")
        // minimalCredential sets isPrimary = false
        assertEquals(false, credential.isPrimary)
        adapter.save(credential)

        adapter.update("cred-primary-upgrade", StoredCredentialUpdate(isPrimary = true))

        val updated = adapter.get("cred-primary-upgrade")
        assertNotNull(updated)
        assertEquals(true, updated.isPrimary)
        // Other fields remain unchanged
        assertEquals("cred-primary-upgrade", updated.credentialId)
        assertNull(updated.contractId)
        assertEquals(CredentialDeploymentStatus.PENDING, updated.deploymentStatus)
    }

    // MARK: - Credential: Clear

    @Test
    fun testClearRemovesAllCredentials() = runTest {
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
        val adapter = newAdapter()
        // Should not throw
        adapter.clear()
        assertTrue(adapter.getAll().isEmpty())
    }

    @Test
    fun testClearAlsoRemovesSession() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-with-session"))
        adapter.saveSession(StoredSession(
            credentialId = "cred-with-session",
            contractId = "CBCD1234EFGH5678IJKL9012MNOP3456QRST7890UVWX1234YZAB5678",
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        ))

        adapter.clear()

        assertTrue(adapter.getAll().isEmpty(), "clear() must remove all credentials")
        assertNull(adapter.getSession(), "clear() must also remove the active session")
    }

    // MARK: - Session: Save and Retrieve

    @Test
    fun testSaveAndRetrieveSession() = runTest {
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
        val adapter = newAdapter()
        val result = adapter.getSession()
        assertNull(result)
    }

    @Test
    fun testSaveSessionOverwritesPrevious() = runTest {
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

        // Verify the session was actually cleared (second call also returns null)
        val secondResult = adapter.getSession()
        assertNull(secondResult, "Session should remain cleared after auto-eviction")
    }

    @Test
    fun testNonExpiredSessionIsReturned() = runTest {
        val adapter = newAdapter()
        val session = StoredSession(
            credentialId = "cred-valid",
            contractId = "CONTRACT_VALID",
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE // effectively never expires
        )
        adapter.saveSession(session)

        val result = adapter.getSession()
        assertNotNull(result)
        assertEquals("cred-valid", result.credentialId)
    }

    // MARK: - Session: Clear

    @Test
    fun testClearSession() = runTest {
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
        val adapter = newAdapter()
        // Should not throw
        adapter.clearSession()
        assertNull(adapter.getSession())
    }

    // MARK: - Custom Suite Name

    @Test
    fun testCustomSuiteNameIsolatesData() = runTest {
        val suiteA = "com.soneso.stellar.test.suiteA.${kotlin.random.Random.nextInt(100000)}"
        val suiteB = "com.soneso.stellar.test.suiteB.${kotlin.random.Random.nextInt(100000)}"
        val adapterA = UserDefaultsStorageAdapter(suiteName = suiteA)
        val adapterB = UserDefaultsStorageAdapter(suiteName = suiteB)

        try {
            adapterA.save(fullCredential("cred-A"))
            adapterB.save(fullCredential("cred-B"))

            // Each adapter should only see its own credentials
            val allA = adapterA.getAll()
            assertEquals(1, allA.size)
            assertEquals("cred-A", allA[0].credentialId)

            val allB = adapterB.getAll()
            assertEquals(1, allB.size)
            assertEquals("cred-B", allB[0].credentialId)

            // Clearing one should not affect the other
            adapterA.clear()
            assertTrue(adapterA.getAll().isEmpty())
            assertEquals(1, adapterB.getAll().size)
        } finally {
            // Clean up both suites
            NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suiteA)
            NSUserDefaults.standardUserDefaults.removePersistentDomainForName(suiteB)
            NSUserDefaults.standardUserDefaults.synchronize()
        }
    }

    @Test
    fun testDefaultSuiteNameIsUsed() {
        // Verify that the default suite name is used when no suite name is provided
        val adapter = UserDefaultsStorageAdapter()
        // This is a compile-time check: the adapter can be constructed with no arguments
        assertNotNull(adapter)
    }

    // MARK: - Session and Credentials Independence

    @Test
    fun testClearSessionDoesNotAffectCredentials() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential())
        adapter.saveSession(StoredSession(
            credentialId = "cred-full-001",
            contractId = "CONTRACT",
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        ))

        adapter.clearSession()

        assertNull(adapter.getSession(), "Session should be cleared")
        assertEquals(1, adapter.getAll().size, "Credentials should not be affected by clearSession()")
    }

    // MARK: - Edge Cases: Delete Then Re-Save

    @Test
    fun testDeleteThenReSave() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-lifecycle"))

        adapter.delete("cred-lifecycle")
        assertNull(adapter.get("cred-lifecycle"))

        // Re-save with same ID but different data
        val newCredential = StoredCredential(
            credentialId = "cred-lifecycle",
            publicKey = testPublicKey(99),
            contractId = "NEW_CONTRACT",
            createdAt = 1700099000000L,
            nickname = "Reborn"
        )
        adapter.save(newCredential)

        val retrieved = adapter.get("cred-lifecycle")
        assertNotNull(retrieved)
        assertEquals("NEW_CONTRACT", retrieved.contractId)
        assertEquals("Reborn", retrieved.nickname)
    }

    // MARK: - Edge Cases: Update After Delete Throws

    @Test
    fun testUpdateAfterDeleteThrows() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-deleted"))

        adapter.delete("cred-deleted")

        assertFailsWith<CredentialException.NotFound> {
            adapter.update("cred-deleted", StoredCredentialUpdate(
                nickname = "Should fail"
            ))
        }
    }

    // MARK: - Interface Conformance

    @Test
    fun testUserDefaultsStorageAdapterImplementsStorageAdapterInterface() {
        val adapter: StorageAdapter = UserDefaultsStorageAdapter(suiteName = testSuiteName)
        // Compilation check: UserDefaultsStorageAdapter can be assigned to StorageAdapter
        assertNotNull(adapter)
    }
}
