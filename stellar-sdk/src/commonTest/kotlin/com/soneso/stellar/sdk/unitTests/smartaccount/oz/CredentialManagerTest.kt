//
//  CredentialManagerTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.unitTests.smartaccount.buildConstantResponseMockServer
import com.soneso.stellar.sdk.unitTests.smartaccount.buildNoRpcMockServer
import com.soneso.stellar.sdk.unitTests.smartaccount.contractInstanceEntriesResponseJson
import com.soneso.stellar.sdk.unitTests.smartaccount.emptyLedgerEntriesResponseJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [OZCredentialManager].
 *
 * The credential lifecycle operations under test:
 * - getAllCredentials / getPendingCredentials
 * - saveCredential (direct save without duplicate check)
 * - updateNickname / updateCredential
 * - deleteCredential
 * - createPendingCredential with duplicate ID
 * - clearAll
 * - setPrimary
 * - getCredentialsByContract
 * - getForConnectedWallet
 * - sync / syncAll against on-chain contract state
 * - the storage failure translation every public method performs
 *
 * Tests are hermetic: storage is either an [InMemoryStorageAdapter] or a
 * [FaultyStorageAdapter] that injects failures, and the Soroban RPC calls made by
 * [OZCredentialManager.sync] are served by a Ktor MockEngine.
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

    /**
     * Builds a kit over [storage] whose Soroban RPC traffic is served by [server].
     * The default server fails on any request, which proves a path never reaches the network.
     */
    private fun createKitWith(
        storage: StorageAdapter,
        server: SorobanServer = buildNoRpcMockServer()
    ): OZSmartAccountKit {
        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            storage = storage
        )
        return OZSmartAccountKit.createWithServer(config, server)
    }

    /**
     * A [SorobanServer] whose every call answers with a JSON-RPC error, standing in for an
     * RPC endpoint that is reachable but unable to serve the contract-instance lookup.
     */
    private fun rpcErrorMockServer(): SorobanServer {
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(
                    """{"jsonrpc":"2.0","id":"test-id","error":{"code":-32603,"message":"internal error"}}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = false
                })
            }
        }
        return SorobanServer("https://soroban-testnet.stellar.org", client)
    }

    private fun testPublicKey(): ByteArray {
        val key = ByteArray(65)
        key[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        for (i in 1 until 65) key[i] = (i % 256).toByte()
        return key
    }

    private fun storedCredential(
        id: String,
        contractId: String? = deployedContractId,
        isPrimary: Boolean = false,
        deploymentStatus: CredentialDeploymentStatus = CredentialDeploymentStatus.PENDING
    ): StoredCredential = StoredCredential(
        credentialId = id,
        publicKey = testPublicKey(),
        contractId = contractId,
        deploymentStatus = deploymentStatus,
        createdAt = 1700000000000L,
        isPrimary = isPrimary
    )

    private val testContractId = "CBCD1234" + "A".repeat(48)

    /** A well-formed contract address, required wherever an address is parsed for RPC. */
    private val deployedContractId = VERIFIER_B

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
    fun testCreatePendingCredential_setsIsPrimaryFalse() = runTest {
        val kit = createKit()

        val credential = kit.credentialManager.createPendingCredential(
            credentialId = "primary-cred",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        assertFalse(credential.isPrimary)
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

        // saveCredential does not check for duplicates, so we can use it for a second
        kit.credentialManager.saveCredential(
            credentialId = "cred-b",
            publicKey = testPublicKey(),
            contractId = testContractId
        )

        // Both credentials are stored non-primary, so cred-a has to be promoted first for the
        // handover to have something to unset.
        kit.credentialManager.setPrimary("cred-a")
        assertEquals(true, kit.credentialManager.getCredential("cred-a")?.isPrimary)

        kit.credentialManager.setPrimary("cred-b")

        val credA = kit.credentialManager.getCredential("cred-a")
        val credB = kit.credentialManager.getCredential("cred-b")

        assertNotNull(credA)
        assertNotNull(credB)
        assertEquals(false, credA.isPrimary, "Promoting cred-b must unset the previous primary")
        assertEquals(true, credB.isPrimary)
    }

    @Test
    fun testSetPrimary_nonExistentThrows() = runTest {
        val kit = createKit()

        assertFailsWith<CredentialException.NotFound> {
            kit.credentialManager.setPrimary("nonexistent")
        }
    }

    @Test
    fun testSetPrimary_sameContract_unsetsOnlyTheOtherPrimary() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-old-primary", isPrimary = true))
        storage.putDirectly(storedCredential("cred-secondary", isPrimary = false))
        storage.putDirectly(storedCredential("cred-target", isPrimary = true))
        val kit = createKitWith(storage)

        kit.credentialManager.setPrimary("cred-target")

        val oldPrimary = kit.credentialManager.getCredential("cred-old-primary")
        val secondary = kit.credentialManager.getCredential("cred-secondary")
        val target = kit.credentialManager.getCredential("cred-target")
        assertNotNull(oldPrimary)
        assertNotNull(secondary)
        assertNotNull(target)

        assertFalse(oldPrimary.isPrimary, "The previous primary for this contract must be unset")
        assertFalse(secondary.isPrimary, "A non-primary credential must stay non-primary")
        assertTrue(target.isPrimary, "The target credential must become primary")
    }

    @Test
    fun testSetPrimary_credentialWithoutContract_unsetsPrimariesAcrossAllCredentials() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-target", contractId = null, isPrimary = false))
        storage.putDirectly(storedCredential("cred-orphan-primary", contractId = null, isPrimary = true))
        // A primary belonging to a different contract is also unset: with no contract to scope
        // the search to, setPrimary falls back to every stored credential.
        storage.putDirectly(storedCredential("cred-other-contract-primary", isPrimary = true))
        val kit = createKitWith(storage)

        kit.credentialManager.setPrimary("cred-target")

        val target = kit.credentialManager.getCredential("cred-target")
        val orphanPrimary = kit.credentialManager.getCredential("cred-orphan-primary")
        val otherContractPrimary = kit.credentialManager.getCredential("cred-other-contract-primary")
        assertNotNull(target)
        assertNotNull(orphanPrimary)
        assertNotNull(otherContractPrimary)

        assertTrue(target.isPrimary)
        assertFalse(orphanPrimary.isPrimary)
        assertFalse(otherContractPrimary.isPrimary)
    }

    @Test
    fun testSetPrimary_unsettingPreviousPrimaryFails_newPrimaryStillSet() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-stuck-primary", isPrimary = true))
        storage.putDirectly(storedCredential("cred-new-primary", isPrimary = false))
        storage.updateFailures["cred-stuck-primary"] = IllegalStateException("write barrier")
        val kit = createKitWith(storage)

        kit.credentialManager.setPrimary("cred-new-primary")

        val newPrimary = kit.credentialManager.getCredential("cred-new-primary")
        val stuckPrimary = kit.credentialManager.getCredential("cred-stuck-primary")
        assertNotNull(newPrimary)
        assertNotNull(stuckPrimary)

        assertTrue(newPrimary.isPrimary, "The new primary must be set even if unsetting the old one failed")
        assertTrue(
            stuckPrimary.isPrimary,
            "A failed unset is non-fatal and leaves the stale primary flag in place"
        )
    }

    @Test
    fun testSetPrimary_storageUpdateFails_throwsStorageWriteFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-primary-fail"))
        storage.updateFailures["cred-primary-fail"] = IllegalStateException("write barrier")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.WriteFailed> {
            kit.credentialManager.setPrimary("cred-primary-fail")
        }
        assertTrue(ex.message.contains("cred-primary-fail"), "The error must name the key; got: ${ex.message}")
        assertEquals("write barrier", ex.cause?.message)
    }

    // MARK: - saveCredential Contract ID Defaulting

    @Test
    fun testSaveCredential_nullContractId_storedAsEmptyString() = runTest {
        val kit = createKitWith(FaultyStorageAdapter())

        val saved = kit.credentialManager.saveCredential(
            credentialId = "cred-no-contract",
            publicKey = testPublicKey()
        )
        assertEquals("", saved.contractId, "A null contract ID is normalized to an empty string")

        val retrieved = kit.credentialManager.getCredential("cred-no-contract")
        assertNotNull(retrieved)
        assertEquals("", retrieved.contractId)
    }

    // MARK: - sync

    @Test
    fun testSync_unknownCredential_throwsCredentialNotFound() = runTest {
        val kit = createKitWith(FaultyStorageAdapter())

        val ex = assertFailsWith<CredentialException.NotFound> {
            kit.credentialManager.sync("cred-unknown")
        }
        assertTrue(ex.message.contains("cred-unknown"), "The error must name the credential; got: ${ex.message}")
    }

    @Test
    fun testSync_storageReadFails_throwsStorageReadFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-read-fail"))
        storage.getFailure = IllegalStateException("keychain locked")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.ReadFailed> {
            kit.credentialManager.sync("cred-read-fail")
        }
        assertTrue(ex.message.contains("cred-read-fail"), "The error must name the key; got: ${ex.message}")
        assertEquals("keychain locked", ex.cause?.message)
    }

    @Test
    fun testSync_credentialWithoutContractAddress_returnsFalseWithoutRpc() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-null-contract", contractId = null))
        storage.putDirectly(storedCredential("cred-empty-contract", contractId = ""))
        // A server that fails on any request proves no on-chain lookup is attempted.
        val kit = createKitWith(storage, buildNoRpcMockServer())

        assertFalse(kit.credentialManager.sync("cred-null-contract"))
        assertFalse(kit.credentialManager.sync("cred-empty-contract"))

        assertNotNull(
            kit.credentialManager.getCredential("cred-null-contract"),
            "A credential without a contract address must survive the sync"
        )
        assertNotNull(kit.credentialManager.getCredential("cred-empty-contract"))
    }

    @Test
    fun testSync_contractNotOnChain_returnsFalseAndKeepsCredential() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-undeployed"))
        val kit = createKitWith(storage, buildConstantResponseMockServer(emptyLedgerEntriesResponseJson()))

        assertFalse(kit.credentialManager.sync("cred-undeployed"))

        assertNotNull(
            kit.credentialManager.getCredential("cred-undeployed"),
            "An undeployed credential must remain pending in storage"
        )
    }

    @Test
    fun testSync_contractOnChain_returnsTrueAndDeletesCredential() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-deployed"))
        val kit = createKitWith(
            storage,
            buildConstantResponseMockServer(contractInstanceEntriesResponseJson(deployedContractId))
        )

        assertTrue(kit.credentialManager.sync("cred-deployed"))

        assertNull(
            kit.credentialManager.getCredential("cred-deployed"),
            "A credential confirmed on-chain must be removed from storage"
        )
    }

    @Test
    fun testSync_rpcFailure_returnsFalseAndKeepsCredential() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-rpc-error"))
        val kit = createKitWith(storage, rpcErrorMockServer())

        assertFalse(
            kit.credentialManager.sync("cred-rpc-error"),
            "A failing RPC lookup must be treated as not-yet-deployed"
        )
        assertNotNull(kit.credentialManager.getCredential("cred-rpc-error"))
    }

    // MARK: - syncAll

    @Test
    fun testSyncAll_emptyStorage_returnsZeroCounts() = runTest {
        val kit = createKitWith(FaultyStorageAdapter())

        assertEquals(SyncResult(deployed = 0, pending = 0, failed = 0), kit.credentialManager.syncAll())
    }

    @Test
    fun testSyncAll_mixedStatuses_countsDeployedPendingAndFailed() = runTest {
        val storage = FaultyStorageAdapter()
        // Only the credential carrying a contract address reaches the RPC; the other two
        // short-circuit on their empty contract address.
        storage.putDirectly(storedCredential("cred-deployed"))
        storage.putDirectly(storedCredential("cred-pending", contractId = ""))
        storage.putDirectly(
            storedCredential(
                "cred-failed",
                contractId = "",
                deploymentStatus = CredentialDeploymentStatus.FAILED
            )
        )
        val kit = createKitWith(
            storage,
            buildConstantResponseMockServer(contractInstanceEntriesResponseJson(deployedContractId))
        )

        assertEquals(
            SyncResult(deployed = 1, pending = 1, failed = 1),
            kit.credentialManager.syncAll()
        )

        assertNull(kit.credentialManager.getCredential("cred-deployed"), "The deployed credential is removed")
        assertNotNull(kit.credentialManager.getCredential("cred-pending"))
        assertNotNull(kit.credentialManager.getCredential("cred-failed"))
    }

    @Test
    fun testSyncAll_storageReadFails_throwsStorageReadFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.getAllFailure = IllegalStateException("keychain locked")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.ReadFailed> { kit.credentialManager.syncAll() }
        assertTrue(ex.message.contains("all"), "The error must name the enumeration key; got: ${ex.message}")
        assertEquals("keychain locked", ex.cause?.message)
    }

    @Test
    fun testSyncAll_credentialRemovedMidRun_isCountedWithoutFailing() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-vanishing", contractId = ""))
        storage.putDirectly(storedCredential("cred-survivor", contractId = ""))
        val kit = createKitWith(storage)

        // Simulate a concurrent deletion between the enumeration and the per-credential read.
        storage.onGet = { id -> if (id == "cred-vanishing") storage.removeDirectly(id) }

        val result = kit.credentialManager.syncAll()
        storage.onGet = null

        assertEquals(
            SyncResult(deployed = 0, pending = 2, failed = 0),
            result,
            "A credential that disappears mid-run must not abort the sync"
        )
        assertNull(kit.credentialManager.getCredential("cred-vanishing"))
        assertNotNull(kit.credentialManager.getCredential("cred-survivor"))
    }

    // MARK: - deleteCredential

    @Test
    fun testDeleteCredential_storageDeleteFails_throwsStorageWriteFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-delete-fail", contractId = ""))
        storage.deleteFailure = IllegalStateException("keychain locked")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.WriteFailed> {
            kit.credentialManager.deleteCredential("cred-delete-fail")
        }
        assertTrue(ex.message.contains("cred-delete-fail"), "The error must name the key; got: ${ex.message}")
        assertEquals("keychain locked", ex.cause?.message)
    }

    @Test
    fun testDeleteCredential_deployedContract_throwsCredentialInvalid() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-deployed"))
        val kit = createKitWith(
            storage,
            buildConstantResponseMockServer(contractInstanceEntriesResponseJson(deployedContractId))
        )

        val ex = assertFailsWith<CredentialException.Invalid> {
            kit.credentialManager.deleteCredential("cred-deployed")
        }
        assertTrue(
            ex.message.contains("deployed credential"),
            "The error must explain the wallet exists on-chain; got: ${ex.message}"
        )
    }

    // MARK: - Storage Failure Translation

    @Test
    fun testCreatePendingCredential_storageSaveFails_throwsStorageWriteFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.saveFailure = IllegalStateException("keychain locked")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.WriteFailed> {
            kit.credentialManager.createPendingCredential(
                credentialId = "cred-save-fail",
                publicKey = testPublicKey(),
                contractId = deployedContractId
            )
        }
        assertTrue(ex.message.contains("cred-save-fail"), "The error must name the key; got: ${ex.message}")
        assertEquals("keychain locked", ex.cause?.message)
    }

    @Test
    fun testSaveCredential_storageSaveFails_throwsStorageWriteFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.saveFailure = IllegalStateException("keychain locked")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.WriteFailed> {
            kit.credentialManager.saveCredential(
                credentialId = "cred-save-fail",
                publicKey = testPublicKey()
            )
        }
        assertTrue(ex.message.contains("cred-save-fail"), "The error must name the key; got: ${ex.message}")
        assertEquals("keychain locked", ex.cause?.message)
    }

    @Test
    fun testMarkDeploymentFailed_storageUpdateFails_throwsStorageWriteFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-mark-fail"))
        storage.updateFailures["cred-mark-fail"] = IllegalStateException("write barrier")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.WriteFailed> {
            kit.credentialManager.markDeploymentFailed("cred-mark-fail", "Insufficient balance")
        }
        assertTrue(ex.message.contains("cred-mark-fail"), "The error must name the key; got: ${ex.message}")
        assertEquals("write barrier", ex.cause?.message)
    }

    @Test
    fun testUpdateCredential_storageUpdateFails_throwsStorageWriteFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.putDirectly(storedCredential("cred-update-fail"))
        storage.updateFailures["cred-update-fail"] = IllegalStateException("write barrier")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.WriteFailed> {
            kit.credentialManager.updateCredential(
                "cred-update-fail",
                StoredCredentialUpdate(nickname = "New Name")
            )
        }
        assertTrue(ex.message.contains("cred-update-fail"), "The error must name the key; got: ${ex.message}")
        assertEquals("write barrier", ex.cause?.message)
    }

    @Test
    fun testGetCredentialsByContract_storageReadFails_throwsStorageReadFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.getByContractFailure = IllegalStateException("keychain locked")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.ReadFailed> {
            kit.credentialManager.getCredentialsByContract(deployedContractId)
        }
        assertTrue(
            ex.message.contains("contract:$deployedContractId"),
            "The error must name the contract-scoped key; got: ${ex.message}"
        )
        assertEquals("keychain locked", ex.cause?.message)
    }

    @Test
    fun testGetAllCredentials_storageReadFails_throwsStorageReadFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.getAllFailure = IllegalStateException("keychain locked")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.ReadFailed> { kit.credentialManager.getAllCredentials() }
        assertTrue(ex.message.contains("all"), "The error must name the enumeration key; got: ${ex.message}")
        assertEquals("keychain locked", ex.cause?.message)
    }

    @Test
    fun testGetPendingCredentials_storageReadFails_throwsStorageReadFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.getAllFailure = IllegalStateException("keychain locked")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.ReadFailed> { kit.credentialManager.getPendingCredentials() }
        assertTrue(ex.message.contains("all"), "The error must name the enumeration key; got: ${ex.message}")
        assertEquals("keychain locked", ex.cause?.message)
    }

    @Test
    fun testClearAll_storageClearFails_throwsStorageWriteFailed() = runTest {
        val storage = FaultyStorageAdapter()
        storage.clearFailure = IllegalStateException("keychain locked")
        val kit = createKitWith(storage)

        val ex = assertFailsWith<StorageException.WriteFailed> { kit.credentialManager.clearAll() }
        assertTrue(ex.message.contains("all"), "The error must name the clear-all key; got: ${ex.message}")
        assertEquals("keychain locked", ex.cause?.message)
    }
}

/**
 * A [StorageAdapter] whose every operation can be made to raise an arbitrary failure,
 * used to drive the failure-translation branches of [OZCredentialManager].
 *
 * Failures are plain [IllegalStateException]s rather than [StorageException]s so the manager
 * has to wrap them itself.
 */
private class FaultyStorageAdapter : StorageAdapter {

    private val credentials = mutableMapOf<String, StoredCredential>()
    private var session: StoredSession? = null

    var saveFailure: Throwable? = null
    var getFailure: Throwable? = null
    var getAllFailure: Throwable? = null
    var getByContractFailure: Throwable? = null
    var deleteFailure: Throwable? = null
    var clearFailure: Throwable? = null

    /** Failures raised by [update], keyed by the credential ID whose update should fail. */
    val updateFailures = mutableMapOf<String, Throwable>()

    /** Invoked before every [get], letting a test mutate the store mid-operation. */
    var onGet: ((String) -> Unit)? = null

    override suspend fun save(credential: StoredCredential) {
        saveFailure?.let { throw it }
        credentials[credential.credentialId] = credential
    }

    override suspend fun get(credentialId: String): StoredCredential? {
        onGet?.invoke(credentialId)
        getFailure?.let { throw it }
        return credentials[credentialId]
    }

    override suspend fun getByContract(contractId: String): List<StoredCredential> {
        getByContractFailure?.let { throw it }
        return credentials.values.filter { it.contractId == contractId }
    }

    override suspend fun getAll(): List<StoredCredential> {
        getAllFailure?.let { throw it }
        return credentials.values.toList()
    }

    override suspend fun delete(credentialId: String) {
        deleteFailure?.let { throw it }
        credentials.remove(credentialId)
    }

    override suspend fun update(credentialId: String, updates: StoredCredentialUpdate) {
        updateFailures[credentialId]?.let { throw it }
        val credential = credentials[credentialId] ?: throw CredentialException.notFound(credentialId)
        credentials[credentialId] = credential.applyUpdate(updates)
    }

    override suspend fun clear() {
        clearFailure?.let { throw it }
        credentials.clear()
        session = null
    }

    override suspend fun saveSession(session: StoredSession) {
        this.session = session
    }

    override suspend fun getSession(): StoredSession? = session?.takeIf { !it.isExpired }

    override suspend fun clearSession() {
        session = null
    }

    /** Seeds a credential without going through the failure-injecting [save]. */
    fun putDirectly(credential: StoredCredential) {
        credentials[credential.credentialId] = credential
    }

    /** Removes a credential without going through the failure-injecting [delete]. */
    fun removeDirectly(credentialId: String) {
        credentials.remove(credentialId)
    }
}
