//
//  KeychainStorageAdapterTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.KeychainStorageAdapter
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
 * Tests for [KeychainStorageAdapter] exercising the [StorageAdapter] interface contract
 * using the real macOS Keychain (Security framework).
 *
 * Each test class instance uses a unique [serviceName] to provide full isolation between
 * tests and prevent Keychain collisions from stale data. The [AfterTest] method calls
 * [KeychainStorageAdapter.clear] to remove all Keychain items written during the test.
 *
 * The macOS Keychain is available in the test environment without additional entitlement
 * configuration when running under `macosArm64Test` or `macosX64Test`.
 */
class KeychainStorageAdapterTest {

    // Unique service name per test class instance to prevent Keychain collisions
    private val testServiceName =
        "com.soneso.stellar.test.keychain.${kotlin.random.Random.nextInt(100_000)}"

    /**
     * Checks whether the macOS Keychain is accessible in the current environment.
     * CI runners and unsigned test binaries may not have Keychain entitlements,
     * causing all SecItem operations to fail with errSecMissingEntitlement (-34018).
     * Returns true if a probe write+delete succeeds, false otherwise.
     */
    private fun isKeychainAvailable(): Boolean {
        return try {
            val probe = KeychainStorageAdapter(serviceName = "com.soneso.stellar.test.probe")
            kotlinx.coroutines.runBlocking {
                probe.save(
                    StoredCredential(
                        credentialId = "probe",
                        publicKey = ByteArray(65) { if (it == 0) 0x04.toByte() else 0x01.toByte() },
                        createdAt = 1L
                    )
                )
                probe.clear()
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private val keychainAvailable by lazy { isKeychainAvailable() }

    private fun newAdapter(): KeychainStorageAdapter =
        KeychainStorageAdapter(serviceName = testServiceName)

    // MARK: - Test Data Helpers

    /**
     * Creates a test public key (65 bytes, uncompressed secp256r1 format starting with 0x04).
     * The [seed] parameter shifts byte values so that multiple distinct keys can be generated.
     */
    private fun testPublicKey(seed: Int = 0): ByteArray =
        ByteArray(65) { i ->
            if (i == 0) 0x04.toByte()
            else ((i + seed) % 256).toByte()
        }

    /**
     * Creates a [StoredCredential] with all optional fields populated.
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
        createdAt = 1_700_000_000_000L,
        lastUsedAt = 1_700_001_000_000L,
        nickname = "MacBook Pro Touch ID",
        isPrimary = true,
        transports = listOf("internal", "usb"),
        deviceType = "multiDevice",
        backedUp = true
    )

    /**
     * Creates a [StoredCredential] with only required fields populated
     * and all optional fields left as their defaults (null / false).
     */
    private fun minimalCredential(
        id: String = "cred-minimal-001"
    ): StoredCredential = StoredCredential(
        credentialId = id,
        publicKey = testPublicKey(2),
        contractId = null,
        deploymentStatus = CredentialDeploymentStatus.PENDING,
        deploymentError = null,
        createdAt = 1_700_000_000_000L,
        lastUsedAt = null,
        nickname = null,
        isPrimary = false,
        transports = null,
        deviceType = null,
        backedUp = null
    )

    @AfterTest
    fun cleanup() = runTest {
        if (!keychainAvailable) return@runTest
        // Remove all Keychain items written by this test via the unique service name
        newAdapter().clear()
    }

    // MARK: - Credential: Save and Retrieve

    @Test
    fun testSaveAndRetrieveCredential() = runTest {
        if (!keychainAvailable) return@runTest
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
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())

        val retrieved = adapter.get("cred-full-001")

        assertNotNull(retrieved)
        assertEquals("cred-full-001", retrieved.credentialId)
        assertTrue(testPublicKey(1).contentEquals(retrieved.publicKey))
        assertEquals("CBCD1234EFGH5678IJKL9012MNOP3456QRST7890UVWX1234YZAB5678", retrieved.contractId)
        assertEquals(CredentialDeploymentStatus.PENDING, retrieved.deploymentStatus)
        assertNull(retrieved.deploymentError)
        assertEquals(1_700_000_000_000L, retrieved.createdAt)
        assertEquals(1_700_001_000_000L, retrieved.lastUsedAt)
        assertEquals("MacBook Pro Touch ID", retrieved.nickname)
        assertTrue(retrieved.isPrimary)
        assertEquals(listOf("internal", "usb"), retrieved.transports)
        assertEquals("multiDevice", retrieved.deviceType)
        assertEquals(true, retrieved.backedUp)
    }

    @Test
    fun testSaveCredentialWithMinimalFields() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(minimalCredential())

        val retrieved = adapter.get("cred-minimal-001")

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
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        val result = adapter.get("nonexistent-credential-id")
        assertNull(result)
    }

    // MARK: - Credential: Upsert Behavior

    @Test
    fun testSaveExistingCredentialOverwrites() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        val original = StoredCredential(
            credentialId = "cred-upsert",
            publicKey = testPublicKey(10),
            contractId = "CONTRACT_ORIGINAL",
            deploymentStatus = CredentialDeploymentStatus.PENDING,
            createdAt = 1_700_000_000_000L,
            nickname = "Original Name"
        )
        adapter.save(original)

        val replacement = StoredCredential(
            credentialId = "cred-upsert",
            publicKey = testPublicKey(20),
            contractId = "CONTRACT_REPLACED",
            deploymentStatus = CredentialDeploymentStatus.FAILED,
            deploymentError = "Insufficient balance",
            createdAt = 1_700_002_000_000L,
            nickname = "Replaced Name"
        )
        adapter.save(replacement)

        val retrieved = adapter.get("cred-upsert")
        assertNotNull(retrieved)
        assertTrue(testPublicKey(20).contentEquals(retrieved.publicKey))
        assertEquals("CONTRACT_REPLACED", retrieved.contractId)
        assertEquals(CredentialDeploymentStatus.FAILED, retrieved.deploymentStatus)
        assertEquals("Replaced Name", retrieved.nickname)
        assertEquals("Insufficient balance", retrieved.deploymentError)

        // Upsert must not duplicate entries in the index
        val all = adapter.getAll()
        assertEquals(1, all.size)
    }

    // MARK: - Credential: Update

    @Test
    fun testUpdateCredentialDeploymentStatus() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.update(
            "cred-full-001",
            StoredCredentialUpdate(
                deploymentStatus = CredentialDeploymentStatus.FAILED,
                deploymentError = "Transaction failed: insufficient balance"
            )
        )

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals(CredentialDeploymentStatus.FAILED, updated.deploymentStatus)
        assertEquals("Transaction failed: insufficient balance", updated.deploymentError)
        // Unchanged fields must remain intact
        assertEquals("MacBook Pro Touch ID", updated.nickname)
        assertTrue(updated.isPrimary)
    }

    @Test
    fun testUpdateCredentialNickname() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.update(
            "cred-full-001",
            StoredCredentialUpdate(nickname = "YubiKey 5")
        )

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals("YubiKey 5", updated.nickname)
        // Unchanged fields must remain intact
        assertEquals(1_700_001_000_000L, updated.lastUsedAt)
        assertTrue(updated.isPrimary)
    }

    @Test
    fun testUpdateCredentialContractId() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(minimalCredential())

        val newContractId = "CNEW1234CONT5678RACT9012ADDR3456GOES7890HERE1234ABCD5678"
        adapter.update(
            "cred-minimal-001",
            StoredCredentialUpdate(contractId = newContractId)
        )

        val updated = adapter.get("cred-minimal-001")
        assertNotNull(updated)
        assertEquals(newContractId, updated.contractId)
    }

    @Test
    fun testUpdateCredentialIsPrimary() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential()) // isPrimary = true

        adapter.update(
            "cred-full-001",
            StoredCredentialUpdate(isPrimary = false)
        )

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals(false, updated.isPrimary)
        // Other fields remain unchanged
        assertEquals("MacBook Pro Touch ID", updated.nickname)
    }

    @Test
    fun testUpdateMultipleFieldsAtOnce() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.update(
            "cred-full-001",
            StoredCredentialUpdate(
                deploymentStatus = CredentialDeploymentStatus.FAILED,
                deploymentError = "Network timeout",
                lastUsedAt = 1_700_099_000_000L,
                nickname = "Updated Device",
                isPrimary = false
            )
        )

        val updated = adapter.get("cred-full-001")
        assertNotNull(updated)
        assertEquals(CredentialDeploymentStatus.FAILED, updated.deploymentStatus)
        assertEquals("Network timeout", updated.deploymentError)
        assertEquals(1_700_099_000_000L, updated.lastUsedAt)
        assertEquals("Updated Device", updated.nickname)
        assertEquals(false, updated.isPrimary)
        // Fields not in the update must remain unchanged
        assertEquals(listOf("internal", "usb"), updated.transports)
        assertEquals("multiDevice", updated.deviceType)
        assertEquals(true, updated.backedUp)
    }

    @Test
    fun testUpdateNonexistentCredentialThrows() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()

        assertFailsWith<CredentialException.NotFound> {
            adapter.update(
                "nonexistent-id",
                StoredCredentialUpdate(nickname = "Should fail")
            )
        }
    }

    // MARK: - Credential: Delete

    @Test
    fun testDeleteCredential() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())

        adapter.delete("cred-full-001")

        assertNull(adapter.get("cred-full-001"))
    }

    @Test
    fun testDeleteNonexistentCredentialDoesNotThrow() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        // Must be a silent no-op; must not throw
        adapter.delete("nonexistent-credential-id")
    }

    @Test
    fun testDeleteRemovesOnlyTargetCredential() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-alpha"))
        adapter.save(fullCredential("cred-beta"))
        adapter.save(fullCredential("cred-gamma"))

        adapter.delete("cred-beta")

        assertNotNull(adapter.get("cred-alpha"))
        assertNull(adapter.get("cred-beta"))
        assertNotNull(adapter.get("cred-gamma"))
        assertEquals(2, adapter.getAll().size)
    }

    // MARK: - Credential: Get All

    @Test
    fun testGetAllEmptyReturnsEmptyList() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        val all = adapter.getAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun testGetAllWithMultipleCredentials() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-1"))
        adapter.save(fullCredential("cred-2"))
        adapter.save(minimalCredential("cred-3"))

        val all = adapter.getAll()
        assertEquals(3, all.size)

        val ids = all.map { it.credentialId }.toSet()
        assertTrue("cred-1" in ids)
        assertTrue("cred-2" in ids)
        assertTrue("cred-3" in ids)
    }

    // MARK: - Credential: Get by Contract ID

    @Test
    fun testGetByContractIdReturnsMatchingCredentials() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        val contractA = "CAAA1234AAAA5678AAAA9012AAAA3456AAAA7890AAAA1234AAAA5678"
        val contractB = "CBBB1234BBBB5678BBBB9012BBBB3456BBBB7890BBBB1234BBBB5678"

        adapter.save(fullCredential("cred-a1", contractA))
        adapter.save(fullCredential("cred-a2", contractA))
        adapter.save(fullCredential("cred-b1", contractB))

        val resultA = adapter.getByContract(contractA)
        assertEquals(2, resultA.size)
        val idsA = resultA.map { it.credentialId }.toSet()
        assertTrue("cred-a1" in idsA)
        assertTrue("cred-a2" in idsA)

        val resultB = adapter.getByContract(contractB)
        assertEquals(1, resultB.size)
        assertEquals("cred-b1", resultB[0].credentialId)
    }

    @Test
    fun testGetByContractIdNoMatchReturnsEmptyList() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())

        val result = adapter.getByContract("CNONEXISTENT_CONTRACT_ID_THAT_DOES_NOT_EXIST_IN")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testGetByContractIdExcludesNullContractId() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        val contractId = "CTGT1234TARG5678ETCO9012NTRA3456CTID7890HERE1234ABCD5678"

        adapter.save(fullCredential("cred-with-contract", contractId))
        adapter.save(minimalCredential("cred-without-contract")) // contractId = null

        val result = adapter.getByContract(contractId)
        assertEquals(1, result.size)
        assertEquals("cred-with-contract", result[0].credentialId)
    }

    // MARK: - Credential: Clear

    @Test
    fun testClearRemovesAllCredentials() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-1"))
        adapter.save(fullCredential("cred-2"))
        adapter.save(minimalCredential("cred-3"))

        adapter.clear()

        assertTrue(adapter.getAll().isEmpty())
        assertNull(adapter.get("cred-1"))
        assertNull(adapter.get("cred-2"))
        assertNull(adapter.get("cred-3"))
    }

    @Test
    fun testClearOnEmptyAdapterDoesNotThrow() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        // Must not throw on an empty Keychain namespace
        adapter.clear()
        assertTrue(adapter.getAll().isEmpty())
    }

    // MARK: - Session: Save and Retrieve

    @Test
    fun testSaveAndRetrieveSession() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        val session = StoredSession(
            credentialId = "cred-session-001",
            contractId = "CSESS1234CONT5678RACT9012ADDR3456GOES7890HERE1234ABCD5678",
            connectedAt = 1_700_000_000_000L,
            expiresAt = Long.MAX_VALUE
        )

        adapter.saveSession(session)

        val retrieved = adapter.getSession()
        assertNotNull(retrieved)
        assertEquals("cred-session-001", retrieved.credentialId)
        assertEquals("CSESS1234CONT5678RACT9012ADDR3456GOES7890HERE1234ABCD5678", retrieved.contractId)
        assertEquals(1_700_000_000_000L, retrieved.connectedAt)
        assertEquals(Long.MAX_VALUE, retrieved.expiresAt)
    }

    @Test
    fun testGetSessionWhenNoneExistsReturnsNull() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        val result = adapter.getSession()
        assertNull(result)
    }

    @Test
    fun testSaveSessionOverwritesPreviousSession() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()

        val session1 = StoredSession(
            credentialId = "cred-session-1",
            contractId = "CONTRACT_ONE",
            connectedAt = 1_700_000_000_000L,
            expiresAt = Long.MAX_VALUE
        )
        adapter.saveSession(session1)

        val session2 = StoredSession(
            credentialId = "cred-session-2",
            contractId = "CONTRACT_TWO",
            connectedAt = 1_700_001_000_000L,
            expiresAt = Long.MAX_VALUE
        )
        adapter.saveSession(session2)

        val retrieved = adapter.getSession()
        assertNotNull(retrieved)
        assertEquals("cred-session-2", retrieved.credentialId)
        assertEquals("CONTRACT_TWO", retrieved.contractId)
        assertEquals(1_700_001_000_000L, retrieved.connectedAt)
    }

    // MARK: - Session: Expiry Auto-Clear

    @Test
    fun testExpiredSessionAutoClearedOnGetSession() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        val session = StoredSession(
            credentialId = "cred-expired",
            contractId = "CONTRACT_EXPIRED",
            connectedAt = 1_000L,
            expiresAt = 2_000L // well in the past
        )
        adapter.saveSession(session)

        val result = adapter.getSession()
        assertNull(result, "Expired session must be auto-cleared and return null")

        // Second call must also return null - the item should be gone from Keychain
        val secondResult = adapter.getSession()
        assertNull(secondResult, "Session must remain cleared after auto-eviction")
    }

    @Test
    fun testNonExpiredSessionIsReturned() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        val session = StoredSession(
            credentialId = "cred-valid",
            contractId = "CONTRACT_VALID",
            connectedAt = 1_700_000_000_000L,
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
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.saveSession(
            StoredSession(
                credentialId = "cred-to-clear",
                contractId = "CONTRACT_CLEAR",
                connectedAt = 1_700_000_000_000L,
                expiresAt = Long.MAX_VALUE
            )
        )

        adapter.clearSession()

        assertNull(adapter.getSession())
    }

    @Test
    fun testClearSessionWhenNoneExistsDoesNotThrow() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        // Must be a silent no-op; must not throw
        adapter.clearSession()
        assertNull(adapter.getSession())
    }

    // MARK: - Cross-Domain Isolation

    @Test
    fun testClearSessionDoesNotAffectCredentials() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())
        adapter.saveSession(
            StoredSession(
                credentialId = "cred-full-001",
                contractId = "CBCD1234EFGH5678IJKL9012MNOP3456QRST7890UVWX1234YZAB5678",
                connectedAt = 1_700_000_000_000L,
                expiresAt = Long.MAX_VALUE
            )
        )

        adapter.clearSession()

        assertNull(adapter.getSession(), "Session must be cleared")
        assertEquals(1, adapter.getAll().size, "Credentials must not be affected by clearSession()")
        assertNotNull(adapter.get("cred-full-001"), "Individual credential must survive clearSession()")
    }

    @Test
    fun testClearRemovesSessionToo() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential())
        adapter.saveSession(
            StoredSession(
                credentialId = "cred-full-001",
                contractId = "CBCD1234EFGH5678IJKL9012MNOP3456QRST7890UVWX1234YZAB5678",
                connectedAt = 1_700_000_000_000L,
                expiresAt = Long.MAX_VALUE
            )
        )

        adapter.clear()

        assertTrue(adapter.getAll().isEmpty(), "All credentials must be cleared")
        assertNull(adapter.getSession(), "Session must be cleared by clear()")
    }

    // MARK: - Keychain-Specific: Service Name Isolation

    @Test
    fun testCustomServiceNameIsolatesKeychainData() = runTest {
        if (!keychainAvailable) return@runTest
        val suffix = kotlin.random.Random.nextInt(100_000)
        val serviceA = "com.soneso.stellar.test.keychain.isolation.a.$suffix"
        val serviceB = "com.soneso.stellar.test.keychain.isolation.b.$suffix"

        val adapterA = KeychainStorageAdapter(serviceName = serviceA)
        val adapterB = KeychainStorageAdapter(serviceName = serviceB)

        try {
            adapterA.save(fullCredential("cred-in-a"))
            adapterB.save(fullCredential("cred-in-b"))

            // Each adapter must only see its own Keychain items
            val allA = adapterA.getAll()
            assertEquals(1, allA.size)
            assertEquals("cred-in-a", allA[0].credentialId)

            val allB = adapterB.getAll()
            assertEquals(1, allB.size)
            assertEquals("cred-in-b", allB[0].credentialId)

            // Item from A must not be visible in B and vice versa
            assertNull(adapterA.get("cred-in-b"))
            assertNull(adapterB.get("cred-in-a"))

            // Clearing A must not touch B
            adapterA.clear()
            assertTrue(adapterA.getAll().isEmpty())
            assertEquals(1, adapterB.getAll().size)
        } finally {
            // Always clean up both namespaces regardless of test outcome
            adapterA.clear()
            adapterB.clear()
        }
    }

    @Test
    fun testDefaultServiceNameIsUsed() {
        if (!keychainAvailable) return
        // Compile-time check: adapter can be constructed without arguments using the default service name
        val adapter = KeychainStorageAdapter()
        assertNotNull(adapter)
    }

    // MARK: - Interface Conformance

    @Test
    fun testKeychainStorageAdapterImplementsStorageAdapterInterface() {
        if (!keychainAvailable) return
        val adapter: StorageAdapter = KeychainStorageAdapter(serviceName = testServiceName)
        // Compilation check: KeychainStorageAdapter can be assigned to StorageAdapter
        assertNotNull(adapter)
    }

    // MARK: - Edge Cases

    @Test
    fun testDeleteThenReSaveCredential() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-lifecycle"))

        adapter.delete("cred-lifecycle")
        assertNull(adapter.get("cred-lifecycle"))

        // Re-save with the same ID but different data
        val newCredential = StoredCredential(
            credentialId = "cred-lifecycle",
            publicKey = testPublicKey(99),
            contractId = "CNEW_CONTRACT_ID_AFTER_RESURRECT_1234567890",
            createdAt = 1_700_099_000_000L,
            nickname = "Reborn"
        )
        adapter.save(newCredential)

        val retrieved = adapter.get("cred-lifecycle")
        assertNotNull(retrieved)
        assertEquals("CNEW_CONTRACT_ID_AFTER_RESURRECT_1234567890", retrieved.contractId)
        assertEquals("Reborn", retrieved.nickname)
        assertTrue(testPublicKey(99).contentEquals(retrieved.publicKey))
        // Upsert after delete must not leave duplicate index entries
        assertEquals(1, adapter.getAll().size)
    }

    @Test
    fun testUpdateAfterDeleteThrows() = runTest {
        if (!keychainAvailable) return@runTest
        val adapter = newAdapter()
        adapter.save(fullCredential("cred-deleted"))

        adapter.delete("cred-deleted")

        assertFailsWith<CredentialException.NotFound> {
            adapter.update(
                "cred-deleted",
                StoredCredentialUpdate(nickname = "Should fail")
            )
        }
    }
}
