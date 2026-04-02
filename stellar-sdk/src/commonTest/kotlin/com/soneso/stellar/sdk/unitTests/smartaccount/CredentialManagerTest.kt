//
//  CredentialManagerTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [OZCredentialManager].
 *
 * Tests cover the credential lifecycle operations that are not already tested
 * in SmartAccountKitTest:
 * - getAllCredentials / getPendingCredentials
 * - saveCredential (direct save without duplicate check)
 * - updateNickname / updateCredential
 * - markDeployed (sync workflow that deletes from storage)
 * - createPendingCredential with duplicate ID
 * - clearAll
 * - setPrimary
 * - getCredentialsByContract
 * - getForConnectedWallet
 *
 * All tests use InMemoryStorageAdapter to avoid network dependencies.
 */
class CredentialManagerTest {

    // MARK: - Test Fixtures

    private suspend fun createKit(): OZSmartAccountKit {
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        )
        return OZSmartAccountKit.create(config)
    }

    private fun testPublicKey(): ByteArray {
        val key = ByteArray(65)
        key[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        for (i in 1 until 65) key[i] = (i % 256).toByte()
        return key
    }

    private val testContractId = "CBCD1234" + "A".repeat(48)

    // MARK: - getAllCredentials Tests

    @Test
    fun testGetAllCredentials_emptyStorage() = runTest {
        val kit = createKit()
        val all = kit.credentialManager.getAllCredentials()
        assertTrue(all.isEmpty())
    }

    @Test
    fun testGetAllCredentials_multipleCredentials() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "cred-1",
            publicKey = testPublicKey(),
            contractId = testContractId
        )
        kit.credentialManager.createPendingCredential(
            credentialId = "cred-2",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        val all = kit.credentialManager.getAllCredentials()
        assertEquals(2, all.size)

        val ids = all.map { it.credentialId }.toSet()
        assertTrue(ids.contains("cred-1"))
        assertTrue(ids.contains("cred-2"))
    }

    // MARK: - getPendingCredentials Tests

    @Test
    fun testGetPendingCredentials_returnsPendingAndFailed() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "cred-pending",
            publicKey = testPublicKey(),
            contractId = testContractId
        )
        kit.credentialManager.createPendingCredential(
            credentialId = "cred-failed",
            publicKey = testPublicKey(),
            contractId = "CXYZ5678" + "B".repeat(48)
        )
        kit.credentialManager.markDeploymentFailed(
            credentialId = "cred-failed",
            error = "Insufficient balance"
        )

        val pending = kit.credentialManager.getPendingCredentials()
        assertEquals(2, pending.size)

        val pendingIds = pending.map { it.credentialId }.toSet()
        assertTrue(pendingIds.contains("cred-pending"))
        assertTrue(pendingIds.contains("cred-failed"))

        val failedCred = pending.first { it.credentialId == "cred-failed" }
        assertEquals(CredentialDeploymentStatus.FAILED, failedCred.deploymentStatus)
    }

    @Test
    fun testGetPendingCredentials_emptyWhenNoPending() = runTest {
        val kit = createKit()
        val pending = kit.credentialManager.getPendingCredentials()
        assertTrue(pending.isEmpty())
    }

    // MARK: - saveCredential Tests

    @Test
    fun testSaveCredential_persistsInStorage() = runTest {
        val kit = createKit()

        val saved = kit.credentialManager.saveCredential(
            credentialId = "saved-cred",
            publicKey = testPublicKey(),
            nickname = "My MacBook",
            contractId = testContractId
        )

        assertEquals("saved-cred", saved.credentialId)
        assertEquals("My MacBook", saved.nickname)
        assertEquals(CredentialDeploymentStatus.PENDING, saved.deploymentStatus)

        val retrieved = kit.credentialManager.getCredential("saved-cred")
        assertNotNull(retrieved)
        assertEquals("My MacBook", retrieved.nickname)
    }

    @Test
    fun testSaveCredential_emptyCredentialIdThrows() = runTest {
        val kit = createKit()

        assertFailsWith<ValidationException.InvalidInput> {
            kit.credentialManager.saveCredential(
                credentialId = "",
                publicKey = testPublicKey()
            )
        }
    }

    @Test
    fun testSaveCredential_invalidPublicKeySizeThrows() = runTest {
        val kit = createKit()

        assertFailsWith<ValidationException.InvalidInput> {
            kit.credentialManager.saveCredential(
                credentialId = "invalid-key-cred",
                publicKey = ByteArray(32) // Wrong size
            )
        }
    }

    // MARK: - markDeployed Tests (Sync Workflow)

    @Test
    fun testMarkDeployed_deletesCredentialFromStorage() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "deploy-cred",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        // Verify credential exists
        assertNotNull(kit.credentialManager.getCredential("deploy-cred"))

        // Mark as deployed (deletes from storage)
        kit.credentialManager.markDeployed("deploy-cred")

        // Verify credential is gone
        assertNull(kit.credentialManager.getCredential("deploy-cred"))
    }

    @Test
    fun testMarkDeployed_nonExistentCredentialDoesNotThrow() = runTest {
        val kit = createKit()

        // markDeployed calls storage.delete which is a silent no-op for non-existent IDs
        kit.credentialManager.markDeployed("nonexistent")
        // Should not throw
    }

    // MARK: - updateNickname Tests

    @Test
    fun testUpdateNickname_updatesSuccessfully() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "nick-cred",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        kit.credentialManager.updateNickname("nick-cred", "YubiKey 5")

        val updated = kit.credentialManager.getCredential("nick-cred")
        assertNotNull(updated)
        assertEquals("YubiKey 5", updated.nickname)
    }

    @Test
    fun testUpdateNickname_nonExistentThrows() = runTest {
        val kit = createKit()

        assertFailsWith<CredentialException.NotFound> {
            kit.credentialManager.updateNickname("nonexistent", "Name")
        }
    }

    // MARK: - updateCredential Tests

    @Test
    fun testUpdateCredential_partialUpdate() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "update-cred",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        kit.credentialManager.updateCredential(
            "update-cred",
            StoredCredentialUpdate(
                nickname = "Updated Name",
                isPrimary = false
            )
        )

        val updated = kit.credentialManager.getCredential("update-cred")
        assertNotNull(updated)
        assertEquals("Updated Name", updated.nickname)
        assertEquals(false, updated.isPrimary)
        // deploymentStatus should be unchanged
        assertEquals(CredentialDeploymentStatus.PENDING, updated.deploymentStatus)
    }

    @Test
    fun testUpdateCredential_nonExistentThrows() = runTest {
        val kit = createKit()

        assertFailsWith<CredentialException.NotFound> {
            kit.credentialManager.updateCredential(
                "nonexistent",
                StoredCredentialUpdate(nickname = "Fail")
            )
        }
    }

    // MARK: - createPendingCredential Duplicate ID Tests

    @Test
    fun testCreatePendingCredential_duplicateIdThrows() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "dup-cred",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        assertFailsWith<CredentialException.AlreadyExists> {
            kit.credentialManager.createPendingCredential(
                credentialId = "dup-cred",
                publicKey = testPublicKey(),
                contractId = "CXYZ5678" + "B".repeat(48)
            )
        }
    }

    @Test
    fun testCreatePendingCredential_setsIsPrimaryTrue() = runTest {
        val kit = createKit()

        val credential = kit.credentialManager.createPendingCredential(
            credentialId = "primary-cred",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        assertTrue(credential.isPrimary)
    }

    @Test
    fun testCreatePendingCredential_withTransportsAndDeviceType() = runTest {
        val kit = createKit()

        val credential = kit.credentialManager.createPendingCredential(
            credentialId = "full-cred",
            publicKey = testPublicKey(),
            contractId = testContractId,
            transports = listOf("internal", "usb"),
            deviceType = "multiDevice",
            backedUp = true
        )

        assertEquals(listOf("internal", "usb"), credential.transports)
        assertEquals("multiDevice", credential.deviceType)
        assertEquals(true, credential.backedUp)
    }

    // MARK: - clearAll Tests

    @Test
    fun testClearAll_removesAllCredentials() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "clear-1",
            publicKey = testPublicKey(),
            contractId = testContractId
        )
        kit.credentialManager.createPendingCredential(
            credentialId = "clear-2",
            publicKey = testPublicKey(),
            contractId = "CXYZ5678" + "B".repeat(48)
        )

        kit.credentialManager.clearAll()

        val all = kit.credentialManager.getAllCredentials()
        assertTrue(all.isEmpty())
    }

    @Test
    fun testClearAll_emptyStorageDoesNotThrow() = runTest {
        val kit = createKit()
        kit.credentialManager.clearAll()
        // Should not throw
    }

    // MARK: - getCredentialsByContract Tests

    @Test
    fun testGetCredentialsByContract_filtersCorrectly() = runTest {
        val kit = createKit()
        val contractA = "CAAA1234" + "A".repeat(48)
        val contractB = "CBBB1234" + "B".repeat(48)

        kit.credentialManager.createPendingCredential(
            credentialId = "cred-a1",
            publicKey = testPublicKey(),
            contractId = contractA
        )
        kit.credentialManager.createPendingCredential(
            credentialId = "cred-a2",
            publicKey = testPublicKey(),
            contractId = contractA
        )
        kit.credentialManager.createPendingCredential(
            credentialId = "cred-b1",
            publicKey = testPublicKey(),
            contractId = contractB
        )

        val contractACredentials = kit.credentialManager.getCredentialsByContract(contractA)
        assertEquals(2, contractACredentials.size)

        val contractBCredentials = kit.credentialManager.getCredentialsByContract(contractB)
        assertEquals(1, contractBCredentials.size)
        assertEquals("cred-b1", contractBCredentials[0].credentialId)
    }

    @Test
    fun testGetCredentialsByContract_noMatchReturnsEmpty() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "cred-1",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        val result = kit.credentialManager.getCredentialsByContract("NONEXISTENT_CONTRACT")
        assertTrue(result.isEmpty())
    }

    // MARK: - getForConnectedWallet Tests

    @Test
    fun testGetForConnectedWallet_notConnectedReturnsEmpty() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "cred-1",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        // Kit is not connected, so getForConnectedWallet should return empty
        val result = kit.credentialManager.getForConnectedWallet()
        assertTrue(result.isEmpty())
    }

    @Test
    fun testGetForConnectedWallet_connectedReturnsMatchingCredentials() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "cred-1",
            publicKey = testPublicKey(),
            contractId = testContractId
        )
        kit.credentialManager.createPendingCredential(
            credentialId = "cred-other",
            publicKey = testPublicKey(),
            contractId = "CXYZ5678" + "B".repeat(48)
        )

        kit.setConnectedState("cred-1", testContractId)

        val result = kit.credentialManager.getForConnectedWallet()
        assertEquals(1, result.size)
        assertEquals("cred-1", result[0].credentialId)
    }

    // MARK: - updateLastUsed Tests

    @Test
    fun testUpdateLastUsed_setsTimestamp() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "used-cred",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        kit.credentialManager.updateLastUsed("used-cred")

        val updated = kit.credentialManager.getCredential("used-cred")
        assertNotNull(updated)
        assertNotNull(updated.lastUsedAt)
        assertTrue(updated.lastUsedAt!! > 0)
    }

    // MARK: - setPrimary Tests

    @Test
    fun testSetPrimary_unsetsPreviousPrimary() = runTest {
        val kit = createKit()

        kit.credentialManager.createPendingCredential(
            credentialId = "cred-a",
            publicKey = testPublicKey(),
            contractId = testContractId
        )
        // First credential is always isPrimary=true

        // saveCredential does not check for duplicates, so we can use it for a second
        kit.credentialManager.saveCredential(
            credentialId = "cred-b",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        // Set cred-b as primary
        kit.credentialManager.setPrimary("cred-b")

        val credA = kit.credentialManager.getCredential("cred-a")
        val credB = kit.credentialManager.getCredential("cred-b")

        assertNotNull(credA)
        assertNotNull(credB)
        assertEquals(false, credA.isPrimary)
        assertEquals(true, credB.isPrimary)
    }

    @Test
    fun testSetPrimary_nonExistentThrows() = runTest {
        val kit = createKit()

        assertFailsWith<CredentialException.NotFound> {
            kit.credentialManager.setPrimary("nonexistent")
        }
    }
}
