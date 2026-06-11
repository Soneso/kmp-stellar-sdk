//
//  StorageAdapterTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.CredentialException
import com.soneso.stellar.sdk.smartaccount.oz.CredentialDeploymentStatus
import com.soneso.stellar.sdk.smartaccount.oz.InMemoryStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.StorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredentialUpdate
import com.soneso.stellar.sdk.smartaccount.oz.StoredSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [InMemoryStorageAdapter] exercising the full [StorageAdapter] interface contract.
 *
 * Since platform-specific storage adapters (localStorage, IndexedDB, EncryptedSharedPreferences,
 * NSUserDefaults, Keychain) require their respective runtime environments, these tests validate
 * the interface contract using the in-memory implementation. All adapters must satisfy the same
 * behavioral expectations tested here.
 */
class StorageAdapterTest {

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

    private fun newAdapter(): InMemoryStorageAdapter = InMemoryStorageAdapter()

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
    fun testUpdateCredentialLastUsedAt() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential())

        val newTimestamp = 1700099000000L
        adapter.update("cred-full-001", StoredCredentialUpdate(
            lastUsedAt = newTimestamp
        ))

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals(newTimestamp, updated.lastUsedAt)
        // Other fields remain unchanged
        assertEquals("MacBook Pro Touch ID", updated.nickname)
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
    fun testUpdateCredentialPrimaryFlag() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.update("cred-full-001", StoredCredentialUpdate(
            isPrimary = false
        ))

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals(false, updated.isPrimary)
    }

    @Test
    fun testUpdateCredentialTransports() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.update("cred-full-001", StoredCredentialUpdate(
            transports = listOf("ble", "nfc")
        ))

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals(listOf("ble", "nfc"), updated.transports)
    }

    @Test
    fun testUpdateCredentialDeviceTypeAndBackedUp() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.update("cred-full-001", StoredCredentialUpdate(
            deviceType = "singleDevice",
            backedUp = false
        ))

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals("singleDevice", updated.deviceType)
        assertEquals(false, updated.backedUp)
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
    fun testUpdateOnlyNonNullFieldsAreApplied() = runTest {
        val adapter = newAdapter()
        val original = fullCredential()
        adapter.save(original)

        // Update with only nickname set, all other fields null
        adapter.update("cred-full-001", StoredCredentialUpdate(
            nickname = "Updated Name"
        ))

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        // Updated field
        assertEquals("Updated Name", updated.nickname)
        // All other fields remain unchanged
        assertEquals(original.contractId, updated.contractId)
        assertEquals(original.deploymentStatus, updated.deploymentStatus)
        assertEquals(original.deploymentError, updated.deploymentError)
        assertEquals(original.lastUsedAt, updated.lastUsedAt)
        assertEquals(original.isPrimary, updated.isPrimary)
        assertEquals(original.transports, updated.transports)
        assertEquals(original.deviceType, updated.deviceType)
        assertEquals(original.backedUp, updated.backedUp)
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
    fun testGetByContractIdExcludesNullContractIds() = runTest {
        val adapter = newAdapter()
        adapter.save(minimalCredential("cred-no-contract")) // contractId = null
        adapter.save(fullCredential("cred-with-contract"))

        val result = adapter.getByContract(fullCredential().contractId!!)
        assertEquals(1, result.size)
        assertEquals("cred-with-contract", result[0].credentialId)
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
        assertNull(adapter.getSession())
    }

    @Test
    fun testClearOnEmptyAdapterDoesNotThrow() = runTest {
        val adapter = newAdapter()
        // Should not throw
        adapter.clear()
        assertTrue(adapter.getAll().isEmpty())
    }

    // MARK: - Session: Save and Retrieve

    @Test
    fun testSaveAndRetrieveSession() = runTest {
        val adapter = newAdapter()
        val now = 1700000000000L
        val expiresAt = Long.MAX_VALUE // far future so session is never expired during test
        val session = StoredSession(
            credentialId = "cred-session-001",
            contractId = "CSESS1234CONT5678RACT9012ADDR3456GOES7890HERE1234ABCD5678",
            connectedAt = now,
            expiresAt = expiresAt
        )

        adapter.saveSession(session)

        val retrieved = adapter.getSession()
        assertNotNull(retrieved)
        assertEquals("cred-session-001", retrieved.credentialId)
        assertEquals("CSESS1234CONT5678RACT9012ADDR3456GOES7890HERE1234ABCD5678", retrieved.contractId)
        assertEquals(now, retrieved.connectedAt)
        assertEquals(expiresAt, retrieved.expiresAt)
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
        val now = 1700000000000L

        val session1 = StoredSession(
            credentialId = "cred-session-1",
            contractId = "CONTRACT_1",
            connectedAt = now,
            expiresAt = Long.MAX_VALUE
        )
        adapter.saveSession(session1)

        val session2 = StoredSession(
            credentialId = "cred-session-2",
            contractId = "CONTRACT_2",
            connectedAt = now + 1000,
            expiresAt = Long.MAX_VALUE
        )
        adapter.saveSession(session2)

        val retrieved = adapter.getSession()
        assertNotNull(retrieved)
        assertEquals("cred-session-2", retrieved.credentialId)
        assertEquals("CONTRACT_2", retrieved.contractId)
    }

    // MARK: - Session: Clear

    @Test
    fun testClearSession() = runTest {
        val adapter = newAdapter()
        val now = 1700000000000L
        adapter.saveSession(StoredSession(
            credentialId = "cred-session",
            contractId = "CONTRACT",
            connectedAt = now,
            expiresAt = now + 7 * 24 * 60 * 60 * 1000
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

    // MARK: - Session: Expiry Auto-Clear

    @Test
    fun testExpiredSessionAutoClearedOnGetSession() = runTest {
        val adapter = newAdapter()
        // Create a session that expired in the past
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
        // Create a session that expires far in the future
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

    // MARK: - Session and Credentials Independence

    @Test
    fun testClearRemovesCredentialsAndSession() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential())
        adapter.saveSession(StoredSession(
            credentialId = "cred-full-001",
            contractId = "CONTRACT",
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        ))

        adapter.clear() // hard reset: credentials AND session

        assertTrue(adapter.getAll().isEmpty(), "Credentials should be cleared")
        assertNull(adapter.getSession(), "Session must be cleared by clear()")
    }

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

    // MARK: - Edge Cases: Credential IDs with Special Characters

    @Test
    fun testCredentialIdWithSpecialCharacters() = runTest {
        val adapter = newAdapter()
        val specialIds = listOf(
            "cred-with-dashes",
            "cred_with_underscores",
            "cred.with.dots",
            "cred/with/slashes",
            "cred+with+plus",
            "cred=with=equals",
            "cred with spaces",
            "Scz0fXNlcjoxMjM0NTY3ODkw",         // base64url-like
            "-__-SomeCredId",                      // starts with special chars
            "cred@user:domain#fragment?query=1"    // URI-like characters
        )

        for (id in specialIds) {
            adapter.save(StoredCredential(
                credentialId = id,
                publicKey = testPublicKey(0),
                createdAt = 1700000000000L
            ))
        }

        for (id in specialIds) {
            val retrieved = adapter.get(id)
            assertNotNull(retrieved, "Should retrieve credential with ID: $id")
            assertEquals(id, retrieved.credentialId)
        }

        assertEquals(specialIds.size, adapter.getAll().size)
    }

    @Test
    fun testCredentialIdWithEmptyString() = runTest {
        val adapter = newAdapter()
        val credential = StoredCredential(
            credentialId = "",
            publicKey = testPublicKey(0),
            createdAt = 1700000000000L
        )

        adapter.save(credential)

        val retrieved = adapter.get("")
        assertNotNull(retrieved)
        assertEquals("", retrieved.credentialId)
    }

    // MARK: - Edge Cases: Large Credential Data

    @Test
    fun testLargePublicKey() = runTest {
        val adapter = newAdapter()
        // Standard uncompressed secp256r1 key is 65 bytes, but test storage handles any size
        val largeKey = ByteArray(1024) { (it % 256).toByte() }
        val credential = StoredCredential(
            credentialId = "cred-large-key",
            publicKey = largeKey,
            createdAt = 1700000000000L
        )

        adapter.save(credential)

        val retrieved = adapter.get("cred-large-key")
        assertNotNull(retrieved)
        assertTrue(largeKey.contentEquals(retrieved.publicKey))
    }

    @Test
    fun testLargeNickname() = runTest {
        val adapter = newAdapter()
        val longNickname = "A".repeat(10000)
        val credential = StoredCredential(
            credentialId = "cred-long-name",
            publicKey = testPublicKey(0),
            nickname = longNickname,
            createdAt = 1700000000000L
        )

        adapter.save(credential)

        val retrieved = adapter.get("cred-long-name")
        assertNotNull(retrieved)
        assertEquals(longNickname, retrieved.nickname)
    }

    @Test
    fun testLargeTransportsList() = runTest {
        val adapter = newAdapter()
        val manyTransports = (1..100).map { "transport-$it" }
        val credential = StoredCredential(
            credentialId = "cred-many-transports",
            publicKey = testPublicKey(0),
            transports = manyTransports,
            createdAt = 1700000000000L
        )

        adapter.save(credential)

        val retrieved = adapter.get("cred-many-transports")
        assertNotNull(retrieved)
        assertEquals(100, retrieved.transports?.size)
        assertEquals("transport-1", retrieved.transports?.first())
        assertEquals("transport-100", retrieved.transports?.last())
    }

    // MARK: - Edge Cases: Multiple Credentials for Same Contract

    @Test
    fun testMultipleCredentialsForSameContractId() = runTest {
        val adapter = newAdapter()
        val sharedContract = "CSHARED1234ABCD5678EFGH9012IJKL3456MNOP7890QRST1234UVWX"

        val cred1 = StoredCredential(
            credentialId = "cred-primary",
            publicKey = testPublicKey(1),
            contractId = sharedContract,
            isPrimary = true,
            nickname = "Primary Passkey",
            createdAt = 1700000000000L
        )

        val cred2 = StoredCredential(
            credentialId = "cred-backup",
            publicKey = testPublicKey(2),
            contractId = sharedContract,
            isPrimary = false,
            nickname = "Backup YubiKey",
            createdAt = 1700000001000L
        )

        val cred3 = StoredCredential(
            credentialId = "cred-recovery",
            publicKey = testPublicKey(3),
            contractId = sharedContract,
            isPrimary = false,
            nickname = "Recovery Key",
            createdAt = 1700000002000L
        )

        adapter.save(cred1)
        adapter.save(cred2)
        adapter.save(cred3)

        val byContract = adapter.getByContract(sharedContract)
        assertEquals(3, byContract.size)

        val ids = byContract.map { it.credentialId }.toSet()
        assertTrue(ids.contains("cred-primary"))
        assertTrue(ids.contains("cred-backup"))
        assertTrue(ids.contains("cred-recovery"))
    }

    // MARK: - Edge Cases: Concurrent-like Operations

    @Test
    fun testRapidSaveAndRetrieveCycle() = runTest {
        val adapter = newAdapter()

        // Simulate rapid save/retrieve cycles
        for (i in 1..50) {
            val id = "cred-rapid-$i"
            val credential = StoredCredential(
                credentialId = id,
                publicKey = testPublicKey(i),
                contractId = "CONTRACT_RAPID",
                createdAt = 1700000000000L + i
            )
            adapter.save(credential)

            val retrieved = adapter.get(id)
            assertNotNull(retrieved, "Should retrieve credential $id immediately after save")
            assertEquals(id, retrieved.credentialId)
        }

        assertEquals(50, adapter.getAll().size)
    }

    @Test
    fun testRapidUpdateCycle() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential())

        // Multiple sequential updates to the same credential
        for (i in 1..20) {
            adapter.update("cred-full-001", StoredCredentialUpdate(
                lastUsedAt = 1700000000000L + i * 1000,
                nickname = "Update #$i"
            ))
        }

        val final = adapter.get("cred-full-001")
        assertNotNull(final)
        assertEquals(1700000000000L + 20 * 1000, final.lastUsedAt)
        assertEquals("Update #20", final.nickname)
    }

    // MARK: - Edge Cases: Deployment Status Transitions

    @Test
    fun testDeploymentStatusTransition() = runTest {
        val adapter = newAdapter()
        val credential = StoredCredential(
            credentialId = "cred-deploy",
            publicKey = testPublicKey(0),
            deploymentStatus = CredentialDeploymentStatus.PENDING,
            createdAt = 1700000000000L
        )
        adapter.save(credential)

        // Transition: PENDING -> FAILED
        adapter.update("cred-deploy", StoredCredentialUpdate(
            deploymentStatus = CredentialDeploymentStatus.FAILED,
            deploymentError = "Transaction rejected"
        ))

        val failed = adapter.get("cred-deploy")
        assertNotNull(failed)
        assertEquals(CredentialDeploymentStatus.FAILED, failed.deploymentStatus)
        assertEquals("Transaction rejected", failed.deploymentError)

        // Re-save as PENDING (retry scenario)
        adapter.update("cred-deploy", StoredCredentialUpdate(
            deploymentStatus = CredentialDeploymentStatus.PENDING
        ))

        val retrying = adapter.get("cred-deploy")
        assertNotNull(retrying)
        assertEquals(CredentialDeploymentStatus.PENDING, retrying.deploymentStatus)
        // Note: deploymentError is not cleared because update only applies non-null fields
        assertEquals("Transaction rejected", retrying.deploymentError)
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

    // MARK: - Edge Cases: Clear Then Add

    @Test
    fun testClearThenAddNewCredentials() = runTest {
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-old-1"))
        adapter.save(fullCredential("cred-old-2"))

        adapter.clear()

        adapter.save(minimalCredential("cred-new-1"))
        assertEquals(1, adapter.getAll().size)
        assertNotNull(adapter.get("cred-new-1"))
        assertNull(adapter.get("cred-old-1"))
    }

    // MARK: - StoredSession.isExpired Property

    @Test
    fun testStoredSessionIsExpiredProperty() {
        // A session with expiresAt in the distant past should be expired
        val expired = StoredSession(
            credentialId = "cred",
            contractId = "CONTRACT",
            connectedAt = 1000L,
            expiresAt = 2000L
        )
        assertTrue(expired.isExpired, "Session expiring at epoch 2000ms should be expired")

        // A session with expiresAt far in the future should not be expired
        val valid = StoredSession(
            credentialId = "cred",
            contractId = "CONTRACT",
            connectedAt = 1700000000000L,
            expiresAt = Long.MAX_VALUE
        )
        assertTrue(!valid.isExpired, "Session expiring at Long.MAX_VALUE should not be expired")
    }

    // MARK: - StoredCredential Equality

    @Test
    fun testStoredCredentialEqualityWithSameData() {
        val key = testPublicKey(5)
        val cred1 = StoredCredential(
            credentialId = "cred-eq",
            publicKey = key.copyOf(),
            contractId = "CONTRACT",
            createdAt = 1700000000000L,
            nickname = "Test"
        )
        val cred2 = StoredCredential(
            credentialId = "cred-eq",
            publicKey = key.copyOf(),
            contractId = "CONTRACT",
            createdAt = 1700000000000L,
            nickname = "Test"
        )

        assertEquals(cred1, cred2, "Credentials with same content should be equal")
        assertEquals(cred1.hashCode(), cred2.hashCode(), "Equal credentials should have same hashCode")
    }

    @Test
    fun testStoredCredentialInequalityWithDifferentPublicKey() {
        val cred1 = StoredCredential(
            credentialId = "cred-neq",
            publicKey = testPublicKey(1),
            createdAt = 1700000000000L
        )
        val cred2 = StoredCredential(
            credentialId = "cred-neq",
            publicKey = testPublicKey(2),
            createdAt = 1700000000000L
        )

        assertTrue(cred1 != cred2, "Credentials with different public keys should not be equal")
    }

    // MARK: - Interface Conformance

    @Test
    fun testInMemoryStorageAdapterImplementsStorageAdapterInterface() {
        val adapter: StorageAdapter = InMemoryStorageAdapter()
        // Compilation check: InMemoryStorageAdapter can be assigned to StorageAdapter
        assertNotNull(adapter)
    }
}
