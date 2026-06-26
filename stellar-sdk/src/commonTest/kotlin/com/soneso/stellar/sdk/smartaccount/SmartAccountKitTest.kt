//
//  SmartAccountKitTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Util
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.crypto.getEd25519Crypto
import com.soneso.stellar.sdk.rpc.exception.AccountNotFoundException
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Layer 2 Integration Tests for Smart Account SDK.
 *
 * Tests cover all OZ* components:
 * 1. Kit initialization
 * 2. Storage adapter (InMemoryStorageAdapter)
 * 3. Credential manager
 * 4. Wallet operations
 * 5. Transaction operations
 * 6. Signer manager
 * 7. Context rule manager
 * 8. Policy manager
 * 9. Multi-signer manager
 * 10. Relayer client
 * 11. Indexer client
 * 12. Error propagation
 *
 * Coverage target: 90%+
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class SmartAccountKitTest {

    // MARK: - Test Fixtures

    private suspend fun createTestConfig(
        deployer: KeyPair? = null,
        relayerUrl: String? = null,
        indexerUrl: String? = null,
        webauthnProvider: WebAuthnProvider? = null,
        networkPassphrase: String = Network.TESTNET.networkPassphrase
    ): OZSmartAccountConfig {
        return OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63), // 64 hex chars
            webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            deployerKeypair = deployer,
            relayerUrl = relayerUrl,
            indexerUrl = indexerUrl,
            webauthnProvider = webauthnProvider
        )
    }

    // MARK: - 1. Kit Initialization Tests

    @Test
    fun testKitInitialization_validConfig() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        assertNotNull(kit)
        assertEquals(config.rpcUrl, kit.config.rpcUrl)
        assertEquals(config.networkPassphrase, kit.config.networkPassphrase)
        assertFalse(kit.isConnected)
        assertNull(kit.credentialId)
        assertNull(kit.contractId)
    }

    @Test
    fun testKitInitialization_missingRpcUrl() = runTest {
        assertFailsWith<ConfigurationException.MissingConfig> {
            OZSmartAccountConfig(
                rpcUrl = "",
                networkPassphrase = Network.TESTNET.networkPassphrase,
                accountWasmHash = "a" + "0".repeat(63),
                webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
            )
        }
    }

    @Test
    fun testKitInitialization_missingNetworkPassphrase() = runTest {
        assertFailsWith<ConfigurationException.MissingConfig> {
            OZSmartAccountConfig(
                rpcUrl = "https://soroban-testnet.stellar.org",
                networkPassphrase = "",
                accountWasmHash = "a" + "0".repeat(63),
                webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
            )
        }
    }

    @Test
    fun testKitInitialization_missingAccountWasmHash() = runTest {
        assertFailsWith<ConfigurationException.MissingConfig> {
            OZSmartAccountConfig(
                rpcUrl = "https://soroban-testnet.stellar.org",
                networkPassphrase = Network.TESTNET.networkPassphrase,
                accountWasmHash = "",
                webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
            )
        }
    }

    @Test
    fun testKitInitialization_invalidWebauthnVerifierAddress_wrongPrefix() = runTest {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZSmartAccountConfig(
                rpcUrl = "https://soroban-testnet.stellar.org",
                networkPassphrase = Network.TESTNET.networkPassphrase,
                accountWasmHash = "a" + "0".repeat(63),
                webauthnVerifierAddress = "G" + "A".repeat(55)
            )
        }
    }

    @Test
    fun testKitInitialization_invalidWebauthnVerifierAddress_wrongLength() = runTest {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZSmartAccountConfig(
                rpcUrl = "https://soroban-testnet.stellar.org",
                networkPassphrase = Network.TESTNET.networkPassphrase,
                accountWasmHash = "a" + "0".repeat(63),
                webauthnVerifierAddress = "CABC"
            )
        }
    }

    @Test
    fun testKitInitialization_customStorageAdapter() = runTest {
        val config = createTestConfig()
        val customStorage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = customStorage))

        assertNotNull(kit)
        assertEquals(config.rpcUrl, kit.config.rpcUrl)
    }

    @Test
    fun testKitInitialization_withRelayer() = runTest {
        val config = createTestConfig(relayerUrl = "https://relayer.example.com")
        val kit = OZSmartAccountKit.create(config)

        assertNotNull(kit)
        assertNotNull(kit.relayerClient)
    }

    @Test
    fun testKitInitialization_withIndexer() = runTest {
        val config = createTestConfig(indexerUrl = "https://indexer.example.com")
        val kit = OZSmartAccountKit.create(config)

        assertNotNull(kit)
        assertNotNull(kit.indexerClient)
    }

    // MARK: - close() Tests

    @Test
    fun testClose_canBeCalledOnFreshKit() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)
        // sorobanServer has never been accessed — _sorobanServer is still null
        kit.close()
    }

    @Test
    fun testClose_canBeCalledTwice() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)
        kit.close()
        // Second call must not throw even though _sorobanServer is already null
        kit.close()
    }

    @Test
    fun testClose_withIndexerClient() = runTest {
        val config = createTestConfig(indexerUrl = "https://indexer.example.com")
        val kit = OZSmartAccountKit.create(config)
        assertNotNull(kit.indexerClient)
        kit.close()
    }

    @Test
    fun testClose_clearsInMemoryExternalSigners() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        val kp = KeyPair.random()
        val address = kit.externalSigners.addFromSecret(kp.getSecretSeed()!!.concatToString())
        assertTrue(kit.externalSigners.canSignFor(address))

        kit.close()

        // In-memory signing secrets must not outlive the kit
        assertFalse(kit.externalSigners.canSignFor(address))
    }

    @Test
    fun testClose_withRelayerClient() = runTest {
        val config = createTestConfig(relayerUrl = "https://relayer.example.com")
        val kit = OZSmartAccountKit.create(config)
        assertNotNull(kit.relayerClient)

        // close() shuts down the relayer's owned HTTP client; calling twice must not throw
        kit.close()
        kit.close()
    }

    @Test
    fun testClose_removesEventListeners() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        var received = false
        kit.events.addListener { received = true }

        kit.close()

        // Listener references are dropped on close; an emit afterwards reaches nobody
        kit.events.emit(SmartAccountEvent.WalletDisconnected(contractId = "CONTRACT"))
        assertFalse(received)
    }

    @Test
    fun testClose_withoutIndexerClient() = runTest {
        // Use a non-testnet passphrase so effectiveIndexerUrl() returns null
        val config = createTestConfig(networkPassphrase = "Custom Network ; No Default Indexer")
        val kit = OZSmartAccountKit.create(config)
        assertNull(kit.indexerClient)
        kit.close()
    }

    // MARK: - 2. Storage Adapter Tests

    @Test
    fun testStorageAdapter_saveAndGet() = runTest {
        val storage = InMemoryStorageAdapter()
        val credential = StoredCredential(
            credentialId = "test-credential-1",
            publicKey = ByteArray(65) { 0x04 },
            contractId = "CBCD1234" + "A".repeat(48),
            isPrimary = true
        )

        storage.save(credential)
        val retrieved = storage.get("test-credential-1")

        assertNotNull(retrieved)
        assertEquals(credential.credentialId, retrieved.credentialId)
        assertTrue(credential.publicKey.contentEquals(retrieved.publicKey))
        assertEquals(credential.contractId, retrieved.contractId)
        assertEquals(credential.isPrimary, retrieved.isPrimary)
    }

    @Test
    fun testStorageAdapter_saveUpsert() = runTest {
        val storage = InMemoryStorageAdapter()
        val credential1 = StoredCredential(
            credentialId = "test-credential-1",
            publicKey = ByteArray(65) { 0x04 },
            nickname = "original"
        )
        val credential2 = StoredCredential(
            credentialId = "test-credential-1",
            publicKey = ByteArray(65) { 0x04 },
            nickname = "updated"
        )

        storage.save(credential1)
        storage.save(credential2) // Should overwrite, not throw

        val retrieved = storage.get("test-credential-1")
        assertNotNull(retrieved)
        assertEquals("updated", retrieved.nickname)
    }

    @Test
    fun testStorageAdapter_getNonExistent() = runTest {
        val storage = InMemoryStorageAdapter()
        val retrieved = storage.get("non-existent")

        assertNull(retrieved)
    }

    @Test
    fun testStorageAdapter_getByContract() = runTest {
        val storage = InMemoryStorageAdapter()
        val contractId = "CBCD1234" + "A".repeat(48)

        val credential1 = StoredCredential(
            credentialId = "test-credential-1",
            publicKey = ByteArray(65) { 0x04 },
            contractId = contractId
        )
        val credential2 = StoredCredential(
            credentialId = "test-credential-2",
            publicKey = ByteArray(65) { 0x04 },
            contractId = contractId
        )
        val credential3 = StoredCredential(
            credentialId = "test-credential-3",
            publicKey = ByteArray(65) { 0x04 },
            contractId = "CXYZ5678" + "B".repeat(48)
        )

        storage.save(credential1)
        storage.save(credential2)
        storage.save(credential3)

        val retrieved = storage.getByContract(contractId)

        assertEquals(2, retrieved.size)
        assertTrue(retrieved.any { it.credentialId == "test-credential-1" })
        assertTrue(retrieved.any { it.credentialId == "test-credential-2" })
        assertFalse(retrieved.any { it.credentialId == "test-credential-3" })
    }

    @Test
    fun testStorageAdapter_getAll() = runTest {
        val storage = InMemoryStorageAdapter()

        val credential1 = StoredCredential(
            credentialId = "test-credential-1",
            publicKey = ByteArray(65) { 0x04 }
        )
        val credential2 = StoredCredential(
            credentialId = "test-credential-2",
            publicKey = ByteArray(65) { 0x04 }
        )

        storage.save(credential1)
        storage.save(credential2)

        val all = storage.getAll()

        assertEquals(2, all.size)
        assertTrue(all.any { it.credentialId == "test-credential-1" })
        assertTrue(all.any { it.credentialId == "test-credential-2" })
    }

    @Test
    fun testStorageAdapter_delete() = runTest {
        val storage = InMemoryStorageAdapter()
        val credential = StoredCredential(
            credentialId = "test-credential-1",
            publicKey = ByteArray(65) { 0x04 }
        )

        storage.save(credential)
        storage.delete("test-credential-1")

        val retrieved = storage.get("test-credential-1")
        assertNull(retrieved)
    }

    @Test
    fun testStorageAdapter_update() = runTest {
        val storage = InMemoryStorageAdapter()
        val credential = StoredCredential(
            credentialId = "test-credential-1",
            publicKey = ByteArray(65) { 0x04 },
            deploymentStatus = CredentialDeploymentStatus.PENDING
        )

        storage.save(credential)

        val update = StoredCredentialUpdate(
            deploymentStatus = CredentialDeploymentStatus.FAILED,
            deploymentError = "Test error"
        )
        storage.update("test-credential-1", update)

        val retrieved = storage.get("test-credential-1")
        assertNotNull(retrieved)
        assertEquals(CredentialDeploymentStatus.FAILED, retrieved.deploymentStatus)
        assertEquals("Test error", retrieved.deploymentError)
    }

    @Test
    fun testStorageAdapter_updateNonExistent() = runTest {
        val storage = InMemoryStorageAdapter()
        val update = StoredCredentialUpdate(deploymentError = "Test")

        assertFailsWith<CredentialException.NotFound> {
            storage.update("non-existent", update)
        }
    }

    @Test
    fun testStorageAdapter_clear() = runTest {
        val storage = InMemoryStorageAdapter()
        val credential1 = StoredCredential(
            credentialId = "test-credential-1",
            publicKey = ByteArray(65) { 0x04 }
        )
        val credential2 = StoredCredential(
            credentialId = "test-credential-2",
            publicKey = ByteArray(65) { 0x04 }
        )

        storage.save(credential1)
        storage.save(credential2)
        storage.clear()

        val all = storage.getAll()
        assertEquals(0, all.size)
    }

    @Test
    fun testStorageAdapter_concurrentAccess() = runTest {
        val storage = InMemoryStorageAdapter()
        val credential1 = StoredCredential(
            credentialId = "test-credential-1",
            publicKey = ByteArray(65) { 0x04 }
        )
        val credential2 = StoredCredential(
            credentialId = "test-credential-2",
            publicKey = ByteArray(65) { 0x04 }
        )

        // Launch concurrent saves
        val job1 = async { storage.save(credential1) }
        val job2 = async { storage.save(credential2) }

        job1.await()
        job2.await()

        val all = storage.getAll()
        assertEquals(2, all.size)
    }

    @Test
    fun testStorageAdapter_session_saveAndGet() = runTest {
        val storage = InMemoryStorageAdapter()
        val session = StoredSession(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48),
            connectedAt = Clock.System.now().toEpochMilliseconds(),
            expiresAt = Clock.System.now().toEpochMilliseconds() + 60000
        )

        storage.saveSession(session)
        val retrieved = storage.getSession()

        assertNotNull(retrieved)
        assertEquals(session.credentialId, retrieved.credentialId)
        assertEquals(session.contractId, retrieved.contractId)
    }

    @Test
    fun testStorageAdapter_session_expiry() = runTest {
        val storage = InMemoryStorageAdapter()
        val session = StoredSession(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48),
            connectedAt = Clock.System.now().toEpochMilliseconds(),
            expiresAt = Clock.System.now().toEpochMilliseconds() - 1000 // Expired 1 second ago
        )

        storage.saveSession(session)
        val retrieved = storage.getSession()

        // Expired session should be automatically cleared
        assertNull(retrieved)
    }

    @Test
    fun testStorageAdapter_session_clear() = runTest {
        val storage = InMemoryStorageAdapter()
        val session = StoredSession(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48),
            connectedAt = Clock.System.now().toEpochMilliseconds(),
            expiresAt = Clock.System.now().toEpochMilliseconds() + 60000
        )

        storage.saveSession(session)
        storage.clearSession()
        val retrieved = storage.getSession()

        assertNull(retrieved)
    }

    // MARK: - 3. Credential Manager Tests

    @Test
    fun testCredentialManager_createPending() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX

        val credential = kit.credentialManager.createPendingCredential(
            credentialId = "test-credential-1",
            publicKey = publicKey,
            contractId = "CBCD1234" + "A".repeat(48)
        )

        assertNotNull(credential)
        assertEquals("test-credential-1", credential.credentialId)
        assertEquals(CredentialDeploymentStatus.PENDING, credential.deploymentStatus)
        assertFalse(credential.isPrimary)
    }

    @Test
    fun testCredentialManager_createPending_invalidPublicKeySize() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<ValidationException.InvalidInput> {
            kit.credentialManager.createPendingCredential(
                credentialId = "test-credential-1",
                publicKey = ByteArray(32), // Wrong size
                contractId = "CBCD1234" + "A".repeat(48)
            )
        }
    }

    @Test
    fun testCredentialManager_createPending_emptyCredentialId() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX

        assertFailsWith<ValidationException.InvalidInput> {
            kit.credentialManager.createPendingCredential(
                credentialId = "",
                publicKey = publicKey,
                contractId = "CBCD1234" + "A".repeat(48)
            )
        }
    }

    @Test
    fun testCredentialManager_markDeploymentFailed() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX

        kit.credentialManager.createPendingCredential(
            credentialId = "test-credential-1",
            publicKey = publicKey,
            contractId = "CBCD1234" + "A".repeat(48)
        )

        kit.credentialManager.markDeploymentFailed(
            credentialId = "test-credential-1",
            error = "Test deployment error"
        )

        val credential = kit.credentialManager.getCredential("test-credential-1")
        assertNotNull(credential)
        assertEquals(CredentialDeploymentStatus.FAILED, credential.deploymentStatus)
        assertEquals("Test deployment error", credential.deploymentError)
    }

    @Test
    fun testCredentialManager_markDeploymentFailed_nonExistent() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<CredentialException.NotFound> {
            kit.credentialManager.markDeploymentFailed(
                credentialId = "non-existent",
                error = "Test error"
            )
        }
    }

    @Test
    fun testCredentialManager_delete() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX

        kit.credentialManager.createPendingCredential(
            credentialId = "test-credential-1",
            publicKey = publicKey,
            contractId = "CBCD1234" + "A".repeat(48)
        )

        kit.credentialManager.deleteCredential("test-credential-1")

        val credential = kit.credentialManager.getCredential("test-credential-1")
        assertNull(credential)
    }

    // MARK: - 4. Wallet Operations Tests

    @Test
    fun testWalletOperations_createWallet_noWebAuthnProvider() = runTest {
        val config = createTestConfig(webauthnProvider = null)
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.createWallet(userName = "Test User", autoSubmit = false)
        }
    }

    @Test
    fun testWalletOperations_createWallet_withMockProvider() = runTest {
        // createWallet always builds the deploy transaction now (even autoSubmit = false).
        // buildDeployTransaction makes a network call that fails with the test config's
        // mock/unfunded deployer and dummy verifier address.
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<SmartAccountException> {
            kit.walletOperations.createWallet(userName = "Test User", autoSubmit = false)
        }
    }

    @Test
    fun testWalletOperations_createWallet_userCancellation() = runTest {
        val mockProvider = MockWebAuthnProvider(shouldCancel = true)
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<WebAuthnException.RegistrationFailed> {
            kit.walletOperations.createWallet(userName = "Test User", autoSubmit = false)
        }
    }

    @Test
    fun testWalletOperations_connectWallet_defaultReturnsNullWithNoSession() = runTest {
        // Default options (prompt=false): no session -> return null
        val config = createTestConfig(webauthnProvider = null)
        val kit = OZSmartAccountKit.create(config)

        val result = kit.walletOperations.connectWallet()
        assertNull(result)
    }

    @Test
    fun testWalletOperations_connectWallet_promptWithNoProvider_throws() = runTest {
        // prompt=true but no WebAuthn provider -> throws NotSupported
        val config = createTestConfig(webauthnProvider = null)
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.connectWallet(
                options = OZWalletOperations.ConnectWalletOptions(prompt = true)
            )
        }
    }

    @Test
    fun testWalletOperations_connectWallet_withValidSession_noNetwork() = runTest {
        // Session restore verifies the stored contractId on-chain. The session
        // contractId here is malformed (it would also fail address parsing on
        // the way to the RPC), and there is no real network in this test
        // environment. The session-restore catch only handles
        // WalletException.NotFound (genuine "contract is not on-chain"); other
        // failures propagate so the calling app can show a real error and the
        // saved session is preserved for retry once the network is healthy.
        val config = createTestConfig()
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        val session = StoredSession(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48),
            connectedAt = Clock.System.now().toEpochMilliseconds(),
            expiresAt = Clock.System.now().toEpochMilliseconds() + 60000
        )
        storage.saveSession(session)

        // The malformed-address case actually does throw WalletException.NotFound
        // (verifyContractExists wraps IllegalArgumentException). It is therefore
        // caught and clears the session. For a transient RPC error path we'd
        // expect propagation; that path requires a fake RPC and is covered
        // separately by future test infrastructure.
        val result = kit.walletOperations.connectWallet()
        assertNull(result)
        assertNull(storage.getSession())
    }

    @Test
    fun testWalletOperations_connectWallet_expiredSessionReturnsNull() = runTest {
        // Default options (prompt=false) with expired session -> return null
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        // Save an expired session
        val session = StoredSession(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48),
            connectedAt = Clock.System.now().toEpochMilliseconds() - 120000,
            expiresAt = Clock.System.now().toEpochMilliseconds() - 60000 // Expired
        )
        storage.saveSession(session)

        val result = kit.walletOperations.connectWallet()
        assertNull(result)
    }

    @Test
    fun testWalletOperations_connectWallet_expiredSessionWithPrompt_triggersWebAuthn() = runTest {
        // prompt=true with expired session -> triggers WebAuthn, then runs the
        // discovery cascade against the mock credential. Under no real network /
        // no usable indexer the cascade surfaces an exception (either
        // WalletException.NotFound or a transport-level error from the
        // indexer/RPC). The connect must not return a Connected result and
        // must not silently succeed.
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        // Save an expired session
        val session = StoredSession(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48),
            connectedAt = Clock.System.now().toEpochMilliseconds() - 120000,
            expiresAt = Clock.System.now().toEpochMilliseconds() - 60000 // Expired
        )
        storage.saveSession(session)

        // Should trigger WebAuthn authentication, then fail on contract lookup.
        // We use the broad assertFails (rather than assertFailsWith<NotFound>)
        // because the exact exception depends on the test runner's network
        // state -- WalletException.NotFound when the testnet RPC reaches a
        // verdict, SorobanRpcException / IndexerException when transport
        // fails. The cascade must always fail; the type is environmental.
        assertFails {
            kit.walletOperations.connectWallet(
                options = OZWalletOperations.ConnectWalletOptions(prompt = true)
            )
        }
    }

    // MARK: - 5. Transaction Operations Tests

    @Test
    fun testTransactionOperations_validateTransfer_invalidAddress() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        // This would be tested via internal validation in sendPayment
        // For now, test that validation works through Address class
        assertFailsWith<ValidationException.InvalidAddress> {
            DelegatedSigner("INVALID_ADDRESS")
        }
    }

    @Test
    fun testTransactionOperations_validateTransfer_zeroAmount() = runTest {
        // Test amount validation
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("0")
        }
    }

    @Test
    fun testTransactionOperations_validateTransfer_negativeAmount() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Util.amountToStroops("-10")
        }
    }

    @Test
    fun testTransactionOperations_amountConversion() = runTest {
        val stroops = Util.amountToStroops("10")
        assertEquals(BigInteger.fromLong(100_000_000L), stroops)
    }

    // MARK: - 6. Signer Manager Tests

    // Note: Signer manager tests would require RPC simulation
    // For integration tests, we test the signer type creation and validation

    @Test
    fun testSignerTypes_delegatedSigner_valid() {
        val signer = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")
        assertNotNull(signer)
        assertEquals("delegated:GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ", signer.uniqueKey)
    }

    @Test
    fun testSignerTypes_delegatedSigner_invalidPrefix() {
        assertFailsWith<ValidationException.InvalidAddress> {
            DelegatedSigner("MABCD1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ234567890ABC")
        }
    }

    @Test
    fun testSignerTypes_externalSigner_webAuthn() {
        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        val credentialId = ByteArray(16) { 0xAA.toByte() }

        val signer = ExternalSigner.webAuthn(
            verifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            publicKey = publicKey,
            credentialId = credentialId
        )

        assertNotNull(signer)
        assertEquals(81, signer.keyData.size) // 65 + 16
    }

    @Test
    fun testSignerTypes_externalSigner_webAuthn_wrongKeySize() {
        val publicKey = ByteArray(33) // Wrong size

        assertFailsWith<ValidationException.InvalidInput> {
            ExternalSigner.webAuthn(
                verifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
                publicKey = publicKey,
                credentialId = ByteArray(16)
            )
        }
    }

    @Test
    fun testSignerTypes_externalSigner_webAuthn_emptyCredentialId() {
        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX

        assertFailsWith<ValidationException.InvalidInput> {
            ExternalSigner.webAuthn(
                verifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
                publicKey = publicKey,
                credentialId = ByteArray(0) // Empty credential ID
            )
        }
    }

    @Test
    fun testSignerTypes_externalSigner_ed25519() {
        val publicKey = ByteArray(32) { 0xBB.toByte() }

        val signer = ExternalSigner.ed25519(
            verifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            publicKey = publicKey
        )

        assertNotNull(signer)
        assertEquals(32, signer.keyData.size)
    }

    // MARK: - 6a. addNewPasskeySigner Tests

    @Test
    fun testAddNewPasskeySigner_throwsWhenNotConnected() = runTest {
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        // Kit is not connected -- requireConnected() should throw
        assertFailsWith<WalletException.NotConnected> {
            kit.signerManager.addNewPasskeySigner(
                contextRuleId = 0u,
                userName = "Recovery Passkey"
            )
        }
    }

    @Test
    fun testAddNewPasskeySigner_throwsWhenNoWebAuthnProvider() = runTest {
        val config = createTestConfig(webauthnProvider = null)
        val kit = OZSmartAccountKit.create(config)

        // Set connected state directly (session restore requires network for on-chain verification)
        kit.setConnectedState(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48)
        )

        assertTrue(kit.isConnected)

        // No WebAuthnProvider configured -- should throw NotSupported
        assertFailsWith<WebAuthnException.NotSupported> {
            kit.signerManager.addNewPasskeySigner(
                contextRuleId = 0u,
                userName = "Recovery Passkey"
            )
        }
    }

    @Test
    fun testAddNewPasskeySigner_registersCredentialAndEmitsEvent() = runTest {
        // This test verifies that addNewPasskeySigner:
        // 1. Performs WebAuthn registration
        // 2. Stores the credential locally
        // 3. Emits CredentialCreated event
        // The on-chain addPasskey step will fail (no live RPC), so we catch that
        // and verify the steps that completed before the failure.

        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        // Connect the kit
        kit.setConnectedState("existing-credential", "CBCD1234" + "A".repeat(48))
        assertTrue(kit.isConnected)

        // Track emitted events
        var credentialCreatedEvent: SmartAccountEvent.CredentialCreated? = null
        kit.events.on<SmartAccountEvent.CredentialCreated> { event ->
            credentialCreatedEvent = event
        }

        // The call will fail at the on-chain addPasskey step (submit to Soroban RPC),
        // but the WebAuthn registration, credential storage, and event emission
        // all happen before that step.
        try {
            kit.signerManager.addNewPasskeySigner(
                contextRuleId = 0u,
                userName = "Recovery Passkey"
            )
        } catch (e: Exception) {
            // Expected: the on-chain submission step fails without a live RPC
        }

        // Verify the CredentialCreated event was emitted
        assertNotNull(credentialCreatedEvent, "CredentialCreated event should have been emitted")
        val emittedCredential = credentialCreatedEvent!!.credential
        assertNotNull(emittedCredential.credentialId)
        assertTrue(emittedCredential.credentialId.isNotEmpty())
        assertEquals(65, emittedCredential.publicKey.size)
        assertEquals(SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX, emittedCredential.publicKey[0])
        assertEquals("CBCD1234" + "A".repeat(48), emittedCredential.contractId)
        assertEquals(CredentialDeploymentStatus.PENDING, emittedCredential.deploymentStatus)

        // Verify the credential was stored
        val storedCredentials = storage.getAll()
        // At least the newly created credential should be in storage
        val newCredential = storedCredentials.find { it.credentialId == emittedCredential.credentialId }
        assertNotNull(newCredential, "New credential should be stored in storage")
        assertEquals(65, newCredential.publicKey.size)
    }

    @Test
    fun testAddNewPasskeySigner_registrationFailurePropagatesAsWebAuthnException() = runTest {
        // When the WebAuthn provider throws during registration, addNewPasskeySigner
        // should wrap it in a WebAuthnException.RegistrationFailed
        val mockProvider = MockWebAuthnProvider(shouldCancel = true)
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        kit.setConnectedState("existing-credential", "CBCD1234" + "A".repeat(48))

        val exception = assertFailsWith<WebAuthnException.RegistrationFailed> {
            kit.signerManager.addNewPasskeySigner(
                contextRuleId = 0u,
                userName = "Recovery Passkey"
            )
        }
        assertTrue(exception.message.contains("WebAuthn registration failed"))
    }

    // MARK: - 7. Context Rule Manager Tests

    // Note: Context rule manager tests would require RPC simulation
    // We test validation and limits here

    @Test
    fun testContextRuleType_default_toScVal() {
        val contextType = ContextRuleType.Default
        val scVal = contextType.toScVal()

        assertNotNull(scVal)
        assertTrue(scVal is SCValXdr.Vec)

        // Verify structure: Vec([Symbol("Default")])
        val vec = (scVal as SCValXdr.Vec).value?.value ?: emptyList()
        assertEquals(1, vec.size)

        val symbol = vec[0] as? SCValXdr.Sym
        assertNotNull(symbol)
        assertEquals("Default", symbol.value?.value)
    }

    @Test
    fun testContextRuleType_callContract_toScVal() {
        val contractAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val contextType = ContextRuleType.CallContract(contractAddress)
        val scVal = contextType.toScVal()

        assertNotNull(scVal)
        assertTrue(scVal is SCValXdr.Vec)

        // Verify structure: Vec([Symbol("CallContract"), Address(contractAddress)])
        val vec = (scVal as SCValXdr.Vec).value?.value ?: emptyList()
        assertEquals(2, vec.size)

        val symbol = vec[0] as? SCValXdr.Sym
        assertNotNull(symbol)
        assertEquals("CallContract", symbol.value?.value)

        val address = vec[1] as? SCValXdr.Address
        assertNotNull(address)
    }

    @Test
    fun testContextRuleType_callContract_invalidAddress() {
        val contextType = ContextRuleType.CallContract("INVALID")

        assertFailsWith<ValidationException.InvalidAddress> {
            contextType.toScVal()
        }
    }

    @Test
    fun testContextRuleType_createContract_toScVal() {
        val wasmHash = ByteArray(32) { it.toByte() }
        val contextType = ContextRuleType.CreateContract(wasmHash)
        val scVal = contextType.toScVal()

        assertNotNull(scVal)
        assertTrue(scVal is SCValXdr.Vec)

        // Verify structure: Vec([Symbol("CreateContract"), Bytes(wasmHash)])
        val vec = (scVal as SCValXdr.Vec).value?.value ?: emptyList()
        assertEquals(2, vec.size)

        val symbol = vec[0] as? SCValXdr.Sym
        assertNotNull(symbol)
        assertEquals("CreateContract", symbol.value?.value)

        val bytes = vec[1] as? SCValXdr.Bytes
        assertNotNull(bytes)
        assertTrue(wasmHash.contentEquals(bytes.value?.value))
    }

    // MARK: - 8. Policy Manager Tests

    // Note: Policy manager tests would require RPC simulation
    // We test validation and limits here

    @Test
    fun testPolicies_maxPoliciesLimit() {
        // Test that the constant is defined correctly
        assertEquals(5, OZConstants.MAX_POLICIES)
    }

    @Test
    fun testPolicyInstallParams_simpleThreshold_valid() {
        val params = PolicyInstallParams.SimpleThreshold(threshold = 2u)
        val scVal = params.toScVal()

        assertNotNull(scVal)
        assertTrue(scVal is SCValXdr.Map)

        // Verify structure: { "threshold": U32(2) }
        val map = (scVal as SCValXdr.Map).value?.value ?: emptyList()
        assertEquals(1, map.size)

        val thresholdEntry = map.first { (key, _) ->
            (key as? SCValXdr.Sym)?.value?.value == "threshold"
        }
        assertNotNull(thresholdEntry)
        assertTrue(thresholdEntry.`val` is SCValXdr.U32)
        assertEquals(2u, (thresholdEntry.`val` as SCValXdr.U32).value?.value)
    }

    @Test
    fun testPolicyInstallParams_simpleThreshold_zeroThreshold() {
        // Zero threshold is rejected — must be > 0
        assertFailsWith<ValidationException.InvalidInput> {
            val params = PolicyInstallParams.SimpleThreshold(threshold = 0u)
            params.toScVal()
        }
    }

    @Test
    fun testPolicyInstallParams_weightedThreshold_valid() {
        val signer1 = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")
        val signer2 = DelegatedSigner("GBXGQJWVLWOYHFLVTKWV5FGHA3LNYY2JQKM7OAJAUEQFU6LPCSEFVXON")

        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(
                signer1 to 50u,
                signer2 to 30u
            ),
            threshold = 70u
        )

        val scVal = params.toScVal()
        assertNotNull(scVal)
        assertTrue(scVal is SCValXdr.Map)

        // Verify structure: { "signer_weights": Map, "threshold": U32 }
        val map = (scVal as SCValXdr.Map).value?.value ?: emptyList()
        assertEquals(2, map.size)

        // Verify signer_weights field exists
        val signerWeightsEntry = map.first { (key, _) ->
            (key as? SCValXdr.Sym)?.value?.value == "signer_weights"
        }
        assertNotNull(signerWeightsEntry)
        assertTrue(signerWeightsEntry.`val` is SCValXdr.Map)

        // Verify threshold field
        val thresholdEntry = map.first { (key, _) ->
            (key as? SCValXdr.Sym)?.value?.value == "threshold"
        }
        assertNotNull(thresholdEntry)
        assertEquals(70u, (thresholdEntry.`val` as SCValXdr.U32).value?.value)
    }

    @Test
    fun testPolicyInstallParams_weightedThreshold_emptySigners() {
        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = emptyMap(),
            threshold = 10u
        )

        assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
    }

    @Test
    fun testPolicyInstallParams_weightedThreshold_zeroTotalWeights() {
        val signer1 = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")

        val params = PolicyInstallParams.WeightedThreshold(
            signerWeights = mapOf(signer1 to 0u),
            threshold = 1u
        )

        // Zero weights are allowed (validation is contract-side)
        val scVal = params.toScVal()
        assertNotNull(scVal)
    }

    @Test
    fun testPolicyInstallParams_spendingLimit_valid() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(1000_0000000L), // 1000 XLM in stroops
            periodLedgers = 17280u // 1 day
        )

        val scVal = params.toScVal()
        assertNotNull(scVal)
        assertTrue(scVal is SCValXdr.Map)

        // Verify structure: { "period_ledgers": U32, "spending_limit": I128 }
        val map = (scVal as SCValXdr.Map).value?.value ?: emptyList()
        assertEquals(2, map.size)

        // Verify period_ledgers field
        val periodEntry = map.first { (key, _) ->
            (key as? SCValXdr.Sym)?.value?.value == "period_ledgers"
        }
        assertNotNull(periodEntry)
        assertEquals(17280u, (periodEntry.`val` as SCValXdr.U32).value?.value)

        // Verify spending_limit field
        val limitEntry = map.first { (key, _) ->
            (key as? SCValXdr.Sym)?.value?.value == "spending_limit"
        }
        assertNotNull(limitEntry)
        assertTrue(limitEntry.`val` is SCValXdr.I128)
    }

    @Test
    fun testPolicyInstallParams_spendingLimit_zeroLimit() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.ZERO,
            periodLedgers = 17280u
        )

        assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
    }

    @Test
    fun testPolicyInstallParams_spendingLimit_negativeLimit() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(-100L),
            periodLedgers = 17280u
        )

        assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
    }

    @Test
    fun testPolicyInstallParams_spendingLimit_zeroPeriod() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = BigInteger.fromLong(1000_0000000L),
            periodLedgers = 0u
        )

        assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
    }

    // MARK: - 9. Multi-Signer Manager Tests

    // Note: Multi-signer manager tests would require RPC simulation
    // We test the signer deduplication logic here

    @Test
    fun testMultiSigner_signerDeduplication() {
        val signer1 = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")
        val signer2 = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")

        // Same unique key means they're the same signer
        assertEquals(signer1.uniqueKey, signer2.uniqueKey)
    }

    @Test
    fun testMultiSigner_maxSignersLimit() {
        // Test that the constant is defined correctly
        assertEquals(15, OZConstants.MAX_SIGNERS)
    }

    // MARK: - 10. Relayer Client Tests

    @Test
    fun testRelayerClient_initialization() {
        val client = OZRelayerClient(
            relayerUrl = "https://relayer.example.com",
            timeoutMs = 60000
        )

        assertNotNull(client)
        assertNotNull(client)
    }

    @Test
    fun testRelayerClient_blankUrl_throwsConfigurationError() {
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "")
        }
        assertFailsWith<ConfigurationException.InvalidConfig> {
            OZRelayerClient(relayerUrl = "   ")
        }
    }

    @Test
    fun testRelayerClient_trailingSlashNormalization() {
        // Trailing slashes should be stripped; no exception thrown
        val client = OZRelayerClient(relayerUrl = "https://relayer.example.com///")
        assertNotNull(client)
        assertNotNull(client)
    }

    // MARK: - 11. Indexer Client Tests

    @Test
    fun testIndexerClient_initialization() {
        val client = OZIndexerClient(
            indexerUrl = "https://indexer.example.com",
            timeoutMs = 10000
        )

        assertNotNull(client)
    }

    @Test
    fun testIndexerClient_lookupByAddress_invalidPrefix() = runTest {
        val client = OZIndexerClient(
            indexerUrl = "https://indexer.example.com"
        )

        assertFailsWith<ValidationException.InvalidAddress> {
            client.lookupByAddress("INVALID_ADDRESS")
        }
    }

    @Test
    fun testIndexerClient_getContract_invalidPrefix() = runTest {
        val client = OZIndexerClient(
            indexerUrl = "https://indexer.example.com"
        )

        assertFailsWith<ValidationException.InvalidAddress> {
            client.getContract("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")
        }
    }

    // MARK: - 12. Error Propagation Tests

    @Test
    fun testErrorPropagation_configurationException() {
        val error = ConfigurationException.invalidConfig("test details")

        assertEquals(SmartAccountErrorCode.INVALID_CONFIG, error.code)
        assertTrue(error.message.contains("test details"))
    }

    @Test
    fun testErrorPropagation_walletException() {
        val error = WalletException.notConnected()

        assertEquals(SmartAccountErrorCode.WALLET_NOT_CONNECTED, error.code)
    }

    @Test
    fun testErrorPropagation_credentialException() {
        val error = CredentialException.notFound("cred123")

        assertEquals(SmartAccountErrorCode.CREDENTIAL_NOT_FOUND, error.code)
        assertTrue(error.message.contains("cred123"))
    }

    @Test
    fun testErrorPropagation_webAuthnException() {
        val error = WebAuthnException.cancelled()

        assertEquals(SmartAccountErrorCode.WEBAUTHN_CANCELLED, error.code)
    }

    @Test
    fun testErrorPropagation_transactionException() {
        val error = TransactionException.timeout()

        assertEquals(SmartAccountErrorCode.TRANSACTION_TIMEOUT, error.code)
    }

    @Test
    fun testErrorPropagation_signerException() {
        val error = SignerException.notFound("signer123")

        assertEquals(SmartAccountErrorCode.SIGNER_NOT_FOUND, error.code)
        assertTrue(error.message.contains("signer123"))
    }

    @Test
    fun testErrorPropagation_validationException() {
        val error = ValidationException.invalidAddress("GXYZ...")

        assertEquals(SmartAccountErrorCode.INVALID_ADDRESS, error.code)
        assertTrue(error.message.contains("GXYZ"))
    }

    @Test
    fun testErrorPropagation_storageException() {
        val error = StorageException.readFailed("key123")

        assertEquals(SmartAccountErrorCode.STORAGE_READ_FAILED, error.code)
        assertTrue(error.message.contains("key123"))
    }

    @Test
    fun testErrorPropagation_sessionException() {
        val error = SessionException.expired()

        assertEquals(SmartAccountErrorCode.SESSION_EXPIRED, error.code)
    }

    @Test
    fun testErrorPropagation_exceptionToString() {
        val cause = RuntimeException("Root cause")
        val error = TransactionException.signingFailed("Signing failed", cause)

        val string = error.toString()
        assertTrue(string.contains("SmartAccountException"))
        assertTrue(string.contains("5002"))
        assertTrue(string.contains("Signing failed"))
        assertTrue(string.contains("Root cause"))
    }

    // MARK: - Additional Integration Tests

    @Test
    fun testKitDisconnect() = runTest {
        val config = createTestConfig()
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        // Manually set connected state
        kit.setConnectedState("test-credential-1", "CBCD1234" + "A".repeat(48))
        assertTrue(kit.isConnected)

        // Save a session
        storage.saveSession(
            StoredSession(
                credentialId = "test-credential-1",
                contractId = "CBCD1234" + "A".repeat(48),
                connectedAt = Clock.System.now().toEpochMilliseconds(),
                expiresAt = Clock.System.now().toEpochMilliseconds() + 60000
            )
        )

        // Disconnect
        kit.disconnect()

        assertFalse(kit.isConnected)
        assertNull(kit.credentialId)
        assertNull(kit.contractId)

        // Session should be cleared
        val session = storage.getSession()
        assertNull(session)
    }

    @Test
    fun testKitRequireConnected_notConnected() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<WalletException.NotConnected> {
            kit.requireConnected()
        }
    }

    @Test
    fun testKitRequireConnected_connected() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        kit.setConnectedState("test-credential-1", "CBCD1234" + "A".repeat(48))

        val (credentialId, contractId) = kit.requireConnected()

        assertEquals("test-credential-1", credentialId)
        assertEquals("CBCD1234" + "A".repeat(48), contractId)
    }

    @Test
    fun testBase64UrlEncoding() {
        val data = ByteArray(16) { it.toByte() }
        val encoded = Util.base64urlEncode(data)

        // Should not contain + or / or =
        assertFalse(encoded.contains("+"))
        assertFalse(encoded.contains("/"))
        assertFalse(encoded.contains("="))

        // Should contain - or _
        assertTrue(encoded.contains("-") || encoded.contains("_") || encoded.matches(Regex("[A-Za-z0-9]*")))
    }

    @Test
    fun testBase64UrlDecoding() {
        val data = ByteArray(16) { it.toByte() }
        val encoded = Util.base64urlEncode(data)
        val decoded = Util.base64urlDecode(encoded)

        assertTrue(data.contentEquals(decoded))
    }

    @Test
    fun testStroopsConversion() {
        val stroops = BigInteger.fromLong(100_000_000L)
        val scVal = Util.stroopsToI128ScVal(stroops)

        assertNotNull(scVal)
    }

    @Test
    fun testDefaultDeployer() = runTest {
        val deployer = OZSmartAccountConfig.createDefaultDeployer()

        assertNotNull(deployer)
        assertNotNull(deployer.getAccountId())
    }

    @Test
    fun testConfigBuilder() = runTest {
        val config = OZSmartAccountConfig.builder(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        )
            .sessionExpiryMs(86400000L)
            .relayerUrl("https://relayer.example.com")
            .indexerUrl("https://indexer.example.com")
            .build()

        assertEquals(86400000L, config.sessionExpiryMs)
        assertEquals("https://relayer.example.com", config.relayerUrl)
        assertEquals("https://indexer.example.com", config.indexerUrl)
    }

    @Test
    fun testConfigBuilder_passesStorageAndProviders() = runTest {
        // Create custom instances to verify reference equality
        val customStorage = InMemoryStorageAdapter()

        val customWebauthnProvider = object : WebAuthnProvider {
            override suspend fun register(
                challenge: ByteArray,
                userId: ByteArray,
                userName: String
            ): WebAuthnRegistrationResult {
                throw NotImplementedError()
            }

            override suspend fun authenticate(
                challenge: ByteArray,
                allowCredentials: List<AllowCredential>?
            ): WebAuthnAuthenticationResult {
                throw NotImplementedError()
            }
        }

        val customExternalWallet = object : ExternalWalletAdapter {
            override suspend fun connect(): ConnectedWallet? {
                throw NotImplementedError()
            }

            override suspend fun disconnect() {
                throw NotImplementedError()
            }

            override suspend fun signAuthEntry(
                authEntryXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult {
                throw NotImplementedError()
            }

            override fun getConnectedWallets(): List<ConnectedWallet> {
                throw NotImplementedError()
            }

            override fun canSignFor(address: String): Boolean {
                throw NotImplementedError()
            }
        }

        // Build config with custom storage, webauthnProvider, and externalWallet
        val config = OZSmartAccountConfig.builder(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        )
            .storage(customStorage)
            .webauthnProvider(customWebauthnProvider)
            .externalWallet(customExternalWallet)
            .build()

        // Verify the builder passes all three references through correctly
        assertSame(customStorage, config.storage)
        assertSame(customWebauthnProvider, config.webauthnProvider)
        assertSame(customExternalWallet, config.externalWallet)

        // Verify defaults when not explicitly set
        val defaultConfig = OZSmartAccountConfig.builder(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
            accountWasmHash = "a" + "0".repeat(63),
            webauthnVerifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        ).build()

        assertNotNull(defaultConfig.storage)
        assertTrue(defaultConfig.storage is InMemoryStorageAdapter)
        assertNull(defaultConfig.webauthnProvider)
        assertNull(defaultConfig.externalWallet)
    }

    @Test
    fun testStoredCredential_equality() {
        // Pin createdAt explicitly. Default is currentTimeMillis() at
        // construction time, and StoredCredential.equals() compares
        // createdAt, so back-to-back constructions on a slow runner can
        // straddle a millisecond boundary and produce unequal credentials.
        val fixedCreatedAt = 1_700_000_000_000L
        val credential1 = StoredCredential(
            credentialId = "test-1",
            publicKey = ByteArray(65) { 0x04 },
            contractId = "CBCD1234" + "A".repeat(48),
            createdAt = fixedCreatedAt
        )

        val credential2 = StoredCredential(
            credentialId = "test-1",
            publicKey = ByteArray(65) { 0x04 },
            contractId = "CBCD1234" + "A".repeat(48),
            createdAt = fixedCreatedAt
        )

        assertEquals(credential1, credential2)
        assertEquals(credential1.hashCode(), credential2.hashCode())
    }

    @Test
    fun testExternalSigner_equality() {
        val keyData = ByteArray(65) { 0x04 }
        val signer1 = ExternalSigner(
            verifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            keyData = keyData
        )

        val signer2 = ExternalSigner(
            verifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            keyData = keyData.copyOf()
        )

        assertEquals(signer1, signer2)
        assertEquals(signer1.hashCode(), signer2.hashCode())
    }

    // MARK: - Constants Validation

    @Test
    fun testConstants_stroopsPerXlm() {
        assertEquals(10_000_000L, Util.STROOPS_PER_XLM)
    }

    @Test
    fun testConstants_maxSigners() {
        assertEquals(15, OZConstants.MAX_SIGNERS)
    }

    @Test
    fun testConstants_maxPolicies() {
        assertEquals(5, OZConstants.MAX_POLICIES)
    }

    @Test
    fun testConstants_sessionExpiry() {
        assertEquals(604_800_000L, OZConstants.DEFAULT_SESSION_EXPIRY_MS)
    }

    @Test
    fun testConstants_ledgersPerHour() {
        assertEquals(720, Util.LEDGERS_PER_HOUR)
    }

    // MARK: - GAP-01: Default Indexer URLs Tests

    @Test
    fun testIndexerClient_defaultUrls_testnet() = runTest {
        val url = OZIndexerClient.getDefaultUrl("Test SDF Network ; September 2015")
        assertNotNull(url)
        assertTrue(url.contains("indexer") || url.contains("testnet") || url.contains("workers.dev"))
    }

    @Test
    fun testIndexerClient_defaultUrls_unknown() = runTest {
        val url = OZIndexerClient.getDefaultUrl("Unknown Network")
        assertNull(url)
    }

    @Test
    fun testIndexerClient_forNetwork_testnet() = runTest {
        val client = OZIndexerClient.forNetwork("Test SDF Network ; September 2015")
        assertNotNull(client)
    }

    @Test
    fun testIndexerClient_forNetwork_unknown() = runTest {
        val client = OZIndexerClient.forNetwork("Unknown Network")
        assertNull(client)
    }

    // MARK: - GAP-04: IndexerClient getStats/isHealthy Tests

    @Test
    fun testIndexerClient_isHealthy_noNetwork() = runTest {
        // isHealthy should return false when network unavailable, not throw
        val client = OZIndexerClient("https://invalid-indexer.example.com")
        val healthy = client.isHealthy()
        assertFalse(healthy)
    }

    // MARK: - GAP-05: Events System Tests

    @Test
    fun testEvents_typeSafeSubscription() = runTest {
        val emitter = SmartAccountEventEmitter()
        var specificEventReceived = false

        emitter.on<SmartAccountEvent.WalletConnected> { event ->
            specificEventReceived = true
            assertEquals("test-contract", event.contractId)
        }

        // This should not trigger the listener
        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "test-contract"))
        assertFalse(specificEventReceived)

        // This should trigger the listener
        emitter.emit(SmartAccountEvent.WalletConnected("test-contract", "test-credential"))
        assertTrue(specificEventReceived)
    }

    @Test
    fun testEvents_onceListener() = runTest {
        val emitter = SmartAccountEventEmitter()
        var callCount = 0

        emitter.once<SmartAccountEvent.WalletDisconnected> {
            callCount++
        }

        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "test-contract"))
        emitter.emit(SmartAccountEvent.WalletDisconnected(contractId = "test-contract"))

        assertEquals(1, callCount) // Should only be called once
    }

    @Test
    fun testEvents_removeAllListeners() = runTest {
        val emitter = SmartAccountEventEmitter()

        // Add listeners
        emitter.on<SmartAccountEvent.WalletConnected> { }
        emitter.on<SmartAccountEvent.WalletDisconnected> { }

        val countConnected = emitter.listenerCount("WalletConnected")
        val countDisconnected = emitter.listenerCount("WalletDisconnected")
        assertEquals(1, countConnected)
        assertEquals(1, countDisconnected)

        emitter.removeAllListeners()

        val countConnectedAfter = emitter.listenerCount("WalletConnected")
        val countDisconnectedAfter = emitter.listenerCount("WalletDisconnected")
        assertEquals(0, countConnectedAfter)
        assertEquals(0, countDisconnectedAfter)
    }

    // MARK: - Fix 1: fundWallet with relayer support

    @Test
    fun testFundWallet_checkRelayerInKit() = runTest {
        val configWithRelayer = createTestConfig(relayerUrl = "https://relayer.example.com")
        val kitWithRelayer = OZSmartAccountKit.create(configWithRelayer)
        assertNotNull(kitWithRelayer.relayerClient)

        val configWithoutRelayer = createTestConfig(relayerUrl = null)
        val kitWithoutRelayer = OZSmartAccountKit.create(configWithoutRelayer)
        assertNull(kitWithoutRelayer.relayerClient)
    }

    @Test
    fun testFundWallet_requiresWalletConnected() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<WalletException.NotConnected> {
            kit.transactionOperations.fundWallet(nativeTokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC")
        }
    }

    @Test
    fun testFundWallet_validateNativeTokenContractFormat() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        kit.setConnectedState("test-credential", "CBCD1234" + "A".repeat(48))

        assertFailsWith<ValidationException.InvalidAddress> {
            kit.transactionOperations.fundWallet(nativeTokenContract = "")
        }

        assertFailsWith<ValidationException.InvalidAddress> {
            kit.transactionOperations.fundWallet(nativeTokenContract = "GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF")
        }
    }

    // MARK: - Fix 2: Conditional deployer signing

    @Test
    fun testSubmit_conditionalSigningLogicRelayerPresence() = runTest {
        val configWithoutRelayer = createTestConfig()
        val kitWithoutRelayer = OZSmartAccountKit.create(configWithoutRelayer)
        assertNull(kitWithoutRelayer.relayerClient)

        val configWithRelayer = createTestConfig(relayerUrl = "https://relayer.example.com")
        val kitWithRelayer = OZSmartAccountKit.create(configWithRelayer)
        assertNotNull(kitWithRelayer.relayerClient)
    }

    @Test
    fun testSubmit_withRelayerConfigured() = runTest {
        val config = createTestConfig(relayerUrl = "https://relayer.example.com")
        val kit = OZSmartAccountKit.create(config)

        assertNotNull(kit.relayerClient)
        assertEquals("https://relayer.example.com", config.relayerUrl)
    }

    @Test
    fun testSubmit_withoutRelayerConfigured() = runTest {
        val config = createTestConfig(relayerUrl = null)
        val kit = OZSmartAccountKit.create(config)

        assertNull(kit.relayerClient)
        assertNull(config.relayerUrl)
    }

    // MARK: - Fix 3: createWallet with autoFund

    @Test
    fun testCreateWallet_autoFundWithoutNativeTokenContract_throws() = runTest {
        val mockProvider = MockWebAuthnProvider()
        val deployer = KeyPair.random()
        val config = createTestConfig(webauthnProvider = mockProvider, deployer = deployer)
        val kit = OZSmartAccountKit.create(config)

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.createWallet(
                userName = "Test User",
                autoSubmit = true,
                autoFund = true,
                nativeTokenContract = null
            )
        }
        assertTrue(exception.message.contains("nativeTokenContract"),
            "Exception message should contain 'nativeTokenContract', but was: ${exception.message}")
    }

    @Test
    fun testCreateWallet_autoFundRequiresAutoSubmit() = runTest {
        // createWallet always builds the deploy transaction now (even autoSubmit = false).
        // buildDeployTransaction fails with the test config's dummy verifier address.
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<SmartAccountException> {
            kit.walletOperations.createWallet(
                userName = "Test User",
                autoSubmit = false,
                autoFund = false,
                nativeTokenContract = null
            )
        }
    }

    @Test
    fun testCreateWallet_signatureWithAutoFundParameters() = runTest {
        // createWallet always builds the deploy transaction now (even autoSubmit = false).
        // buildDeployTransaction fails with the test config's dummy verifier address.
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<SmartAccountException> {
            kit.walletOperations.createWallet(
                userName = "Test User",
                autoSubmit = false,
                autoFund = false,
                nativeTokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
            )
        }
    }

    @Test
    fun testCreateWallet_autoFundParameterAccepted() = runTest {
        // createWallet always builds the deploy transaction now (even autoSubmit = false).
        // buildDeployTransaction fails with the test config's dummy verifier address.
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<SmartAccountException> {
            kit.walletOperations.createWallet(
                userName = "Test User",
                autoSubmit = false,
                autoFund = false
            )
        }
    }

    // MARK: - Deployment-via-relayer behavior tests

    @Test
    fun testCreateWallet_autoSubmitRequiresRelayerOrFundedDeployer() = runTest {
        // When autoSubmit = true and no relayer is configured, the deploy path
        // submits directly to Soroban RPC. Since the deployer has no XLM,
        // this must fail gracefully with a TransactionException.
        val mockProvider = MockWebAuthnProvider()
        val deployer = KeyPair.random()
        val config = createTestConfig(webauthnProvider = mockProvider, deployer = deployer)
        val kit = OZSmartAccountKit.create(config)

        // No relayer configured
        assertNull(kit.relayerClient)

        // autoSubmit = true triggers buildDeployTransaction(), which calls
        // sorobanServer.getAccount() for the unfunded deployer. This network call fails
        // because the deployer has no account on testnet, resulting in an exception.
        // The exception may be a SmartAccountException or a lower-level network exception.
        assertFailsWith<Exception> {
            kit.walletOperations.createWallet(
                userName = "Test User",
                autoSubmit = true
            )
        }
    }

    @Test
    fun testCreateWallet_autoSubmitValidationStillWorks() = runTest {
        // Even with autoSubmit = true, the early validation for autoFund requiring
        // nativeTokenContract must fire before any deployment attempt is made.
        val mockProvider = MockWebAuthnProvider()
        val deployer = KeyPair.random()
        val config = createTestConfig(webauthnProvider = mockProvider, deployer = deployer)
        val kit = OZSmartAccountKit.create(config)

        // autoFund = true but nativeTokenContract = null should throw immediately
        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.createWallet(
                userName = "Test User",
                autoSubmit = true,
                autoFund = true,
                nativeTokenContract = null
            )
        }
        assertTrue(
            exception.message.contains("nativeTokenContract"),
            "Exception message should contain 'nativeTokenContract', but was: ${exception.message}"
        )
    }

    // MARK: - FP3: CreateWalletResult.signedTransactionXdr field

    @Test
    fun testCreateWalletResult_signedTransactionXdrField_exists() = runTest {
        // Compile-time test: verify the field exists and is accessible
        val fakePublicKey = ByteArray(65) { it.toByte() }
        val result = CreateWalletResult(
            credentialId = "test-cred",
            contractId = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            publicKey = fakePublicKey,
            signedTransactionXdr = "AAAAAA==",
            transactionHash = null,
            nickname = "Test"
        )
        assertEquals("AAAAAA==", result.signedTransactionXdr)
        assertNull(result.transactionHash)
    }

    @Test
    fun testCreateWalletResult_equalsIncludesSignedTransactionXdr() = runTest {
        val fakePublicKey = ByteArray(65) { it.toByte() }
        val result1 = CreateWalletResult(
            credentialId = "cred",
            contractId = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            publicKey = fakePublicKey,
            signedTransactionXdr = "XDR_A"
        )
        val result2 = CreateWalletResult(
            credentialId = "cred",
            contractId = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            publicKey = fakePublicKey,
            signedTransactionXdr = "XDR_B"
        )
        assertFalse(result1 == result2, "Results with different signedTransactionXdr must not be equal")
    }

    @Test
    fun testCreateWalletResult_hashCodeIncludesSignedTransactionXdr() = runTest {
        val fakePublicKey = ByteArray(65) { it.toByte() }
        val result1 = CreateWalletResult(
            credentialId = "cred",
            contractId = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            publicKey = fakePublicKey,
            signedTransactionXdr = "XDR_A"
        )
        val result2 = CreateWalletResult(
            credentialId = "cred",
            contractId = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            publicKey = fakePublicKey,
            signedTransactionXdr = "XDR_A"
        )
        assertEquals(result1.hashCode(), result2.hashCode(), "Equal results must have equal hashCode")
    }

    // MARK: - FP3: createWallet forceMethod parameter

    @Test
    fun testCreateWallet_forcedMethodRpcValidationFiringFirst() = runTest {
        // autoFund = true with no nativeTokenContract must throw before any network call,
        // regardless of forceMethod. This confirms forceMethod parameter is accepted
        // and validation still fires early.
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.createWallet(
                userName = "Test User",
                autoSubmit = true,
                autoFund = true,
                nativeTokenContract = null,
                forceMethod = SubmissionMethod.RPC
            )
        }
        assertTrue(exception.message.contains("nativeTokenContract"))
    }

    // MARK: - FP3: DeployPendingResult data class

    @Test
    fun testDeployPendingResult_construction() = runTest {
        val result = DeployPendingResult(
            contractId = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            signedTransactionXdr = "AAAAAA==",
            transactionHash = "abc123"
        )
        assertEquals("CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM", result.contractId)
        assertEquals("AAAAAA==", result.signedTransactionXdr)
        assertEquals("abc123", result.transactionHash)
    }

    @Test
    fun testDeployPendingResult_transactionHashDefaultsToNull() = runTest {
        val result = DeployPendingResult(
            contractId = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM",
            signedTransactionXdr = "AAAAAA=="
        )
        assertNull(result.transactionHash)
    }

    @Test
    fun testDeployPendingResult_equality() = runTest {
        val r1 = DeployPendingResult(
            contractId = "CABC",
            signedTransactionXdr = "XDR1",
            transactionHash = "hash1"
        )
        val r2 = DeployPendingResult(
            contractId = "CABC",
            signedTransactionXdr = "XDR1",
            transactionHash = "hash1"
        )
        val r3 = DeployPendingResult(
            contractId = "CABC",
            signedTransactionXdr = "XDR2",
            transactionHash = "hash1"
        )
        assertEquals(r1, r2)
        assertFalse(r1 == r3)
    }

    // MARK: - FP3: deployPendingCredential validation

    @Test
    fun testDeployPendingCredential_autoFundWithoutNativeTokenContract_throws() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "any-credential-id",
                autoSubmit = true,
                autoFund = true,
                nativeTokenContract = null
            )
        }
        assertTrue(exception.message.contains("nativeTokenContract"))
    }

    @Test
    fun testDeployPendingCredential_credentialNotFound_throws() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<CredentialException> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "nonexistent-credential"
            )
        }
    }

    @Test
    fun testDeployPendingCredential_forceMethodParameterAccepted() = runTest {
        // Confirm the parameter compiles and early validation fires before the lookup
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        // autoFund = true with no nativeTokenContract throws before the credential lookup
        val exception = assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.deployPendingCredential(
                credentialId = "any-cred",
                autoSubmit = true,
                autoFund = true,
                nativeTokenContract = null,
                forceMethod = SubmissionMethod.RELAYER
            )
        }
        assertTrue(exception.message.contains("nativeTokenContract"))
    }

    // MARK: - Fix 4: connectWallet with options

    @Test
    fun testConnectWalletOptions_defaultValues() = runTest {
        val options = OZWalletOperations.ConnectWalletOptions()
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertFalse(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withCredentialId() = runTest {
        val options = OZWalletOperations.ConnectWalletOptions(credentialId = "test-credential")
        assertEquals("test-credential", options.credentialId)
        assertNull(options.contractId)
        assertFalse(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withFresh() = runTest {
        val options = OZWalletOperations.ConnectWalletOptions(fresh = true)
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertTrue(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withPrompt() = runTest {
        val options = OZWalletOperations.ConnectWalletOptions(prompt = true)
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertFalse(options.fresh)
        assertTrue(options.prompt)
    }

    @Test
    fun testConnectWalletOptions_withContractId() = runTest {
        val options = OZWalletOperations.ConnectWalletOptions(
            credentialId = "test-credential",
            contractId = "CBCD1234" + "A".repeat(48)
        )
        assertEquals("test-credential", options.credentialId)
        assertEquals("CBCD1234" + "A".repeat(48), options.contractId)
        assertFalse(options.fresh)
        assertFalse(options.prompt)
    }

    @Test
    fun testConnectWallet_contractIdRequiresCredentialId() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<ValidationException.InvalidInput> {
            kit.walletOperations.connectWallet(
                options = OZWalletOperations.ConnectWalletOptions(
                    contractId = "CBCD1234" + "A".repeat(48)
                )
            )
        }
    }

    @Test
    fun testConnectWallet_withOptionsAcceptsParameter() = runTest {
        // ConnectWalletOptions() with defaults (prompt=false, fresh=false) should work.
        // Without network access, session verification fails and returns null.
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        val session = StoredSession(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48),
            connectedAt = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            expiresAt = kotlin.time.Clock.System.now().toEpochMilliseconds() + 60000
        )
        storage.saveSession(session)

        // On-chain verification fails -> session cleared -> returns null (prompt=false)
        val result = kit.walletOperations.connectWallet(
            options = OZWalletOperations.ConnectWalletOptions()
        )
        assertNull(result)
    }

    @Test
    fun testConnectWallet_freshOptionSkipsSession() = runTest {
        // fresh=true bypasses the saved session and triggers WebAuthn. The
        // resulting cascade (storage / derivation / indexer) cannot resolve a
        // contract for the mock credential and must surface an exception
        // (either WalletException.NotFound or an RPC/indexer transport error).
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        val session = StoredSession(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48),
            connectedAt = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            expiresAt = kotlin.time.Clock.System.now().toEpochMilliseconds() + 60000
        )
        storage.saveSession(session)

        assertFails {
            kit.walletOperations.connectWallet(
                options = OZWalletOperations.ConnectWalletOptions(fresh = true)
            )
        }
    }

    @Test
    fun testConnectWallet_noSessionDefaultReturnsNull() = runTest {
        // No session, default options (prompt=false) -> returns null
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        val result = kit.walletOperations.connectWallet()
        assertNull(result)
    }

    @Test
    fun testConnectWallet_noSessionWithPrompt_triggersWebAuthn() = runTest {
        // No session, prompt=true -> triggers WebAuthn. The cascade then runs
        // and cannot resolve a contract for the mock credential; an exception
        // is surfaced (either WalletException.NotFound or an RPC/indexer
        // transport error). The test's purpose is to verify the cascade is
        // entered, not the specific error type.
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        assertFails {
            kit.walletOperations.connectWallet(
                options = OZWalletOperations.ConnectWalletOptions(prompt = true)
            )
        }
    }

    @Test
    fun testConnectWallet_freshTakesPriorityOverPrompt() = runTest {
        // fresh=true, prompt=true -> fresh takes priority, skips the saved
        // session, triggers WebAuthn. The cascade fails to resolve the
        // mock credential and surfaces an exception.
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        // Save a valid session
        val session = StoredSession(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48),
            connectedAt = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            expiresAt = kotlin.time.Clock.System.now().toEpochMilliseconds() + 60000
        )
        storage.saveSession(session)

        assertFails {
            kit.walletOperations.connectWallet(
                options = OZWalletOperations.ConnectWalletOptions(fresh = true, prompt = true)
            )
        }
    }

    // MARK: - Friendbot funding: poll RPC until the funded account is visible

    @Test
    fun testRpcVisibility_constants() {
        // The RPC visibility poll budget is fixed and must not silently drift. Shared by
        // the funding-account wait and the deploy contract-instance wait.
        assertEquals(1_500L, OZConstants.RPC_VISIBILITY_POLL_INTERVAL_MS)
        assertEquals(45, OZConstants.RPC_VISIBILITY_TIMEOUT_SECONDS)
    }

    @Test
    fun testPollUntilAccountVisible_waitsForNotFoundThenProceeds() = runTest {
        // The funding account is not yet visible to the RPC for the first three polls,
        // then appears. The poll must keep waiting and return once the account is visible.
        val accountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        val notFoundPolls = 3
        var calls = 0

        pollUntilAccountVisibleToRpc(accountId) { id ->
            calls++
            if (calls <= notFoundPolls) {
                throw AccountNotFoundException(id)
            }
            // Visible from the fourth lookup onward — return normally.
        }

        // One lookup per attempt: three "not found" plus the successful one.
        assertEquals(notFoundPolls + 1, calls)
        // It waited exactly one interval between each retry before succeeding.
        assertEquals(
            notFoundPolls * OZConstants.RPC_VISIBILITY_POLL_INTERVAL_MS,
            testScheduler.currentTime
        )
    }

    @Test
    fun testPollUntilAccountVisible_returnsImmediatelyWhenAlreadyVisible() = runTest {
        // When the account is visible on the first lookup, the poll proceeds without delay.
        val accountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        var calls = 0

        pollUntilAccountVisibleToRpc(accountId) { _ ->
            calls++
            // Visible immediately.
        }

        assertEquals(1, calls)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun testPollUntilAccountVisible_throwsClearTimeoutWhenNeverVisible() = runTest {
        // The account never becomes visible. The poll must exhaust its budget and throw a
        // clear, dedicated timeout rather than letting the opaque balance simulation fail.
        val accountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        var calls = 0

        val error = assertFailsWith<TransactionException.Timeout> {
            pollUntilAccountVisibleToRpc(accountId) { id ->
                calls++
                throw AccountNotFoundException(id)
            }
        }

        assertTrue(error.message.contains(accountId), "Timeout message should name the account")
        assertTrue(
            error.message.contains("not visible to the Soroban RPC"),
            "Timeout message should explain the visibility failure, but was: ${error.message}"
        )
        // A pure "not found" timeout has no transient RPC error to surface as the cause.
        assertNull(error.cause)
        // Budget consumed at the configured interval: 45s / 1.5s = 30 polls.
        assertTrue(calls > 0)
        assertEquals(
            OZConstants.RPC_VISIBILITY_TIMEOUT_SECONDS.toLong() * 1000L,
            testScheduler.currentTime
        )
    }

    @Test
    fun testPollUntilAccountVisible_retriesTransientErrorsAndSurfacesLastAsCause() = runTest {
        // Transient RPC/transport errors (not "account not found") must be retried until the
        // deadline, and the last one surfaced as the timeout cause for diagnosability.
        val accountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        var calls = 0

        val error = assertFailsWith<TransactionException.Timeout> {
            pollUntilAccountVisibleToRpc(accountId) { _ ->
                calls++
                throw IllegalStateException("rpc unavailable #$calls")
            }
        }

        assertTrue(calls > 1, "Transient errors should be retried, not surfaced immediately")
        val cause = error.cause
        assertNotNull(cause, "The last transient error should be surfaced as the timeout cause")
        assertTrue(cause is IllegalStateException)
        assertTrue(
            cause.message?.contains("rpc unavailable") == true,
            "Cause should be the last transient error, but was: ${cause.message}"
        )
    }

    @Test
    fun testPollUntilAccountVisible_recoversAfterTransientErrors() = runTest {
        // A transient error followed by a successful lookup must complete normally,
        // proving transient failures do not abort the poll.
        val accountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        var calls = 0

        pollUntilAccountVisibleToRpc(accountId) { id ->
            calls++
            when (calls) {
                1 -> throw IllegalStateException("transient rpc error")
                2 -> throw AccountNotFoundException(id)
                else -> { /* visible on the third lookup */ }
            }
        }

        assertEquals(3, calls)
        assertEquals(
            2 * OZConstants.RPC_VISIBILITY_POLL_INTERVAL_MS,
            testScheduler.currentTime
        )
    }

    @Test
    fun testPollUntilAccountVisible_cancellationPropagatesAndIsNotConvertedToTimeout() = runTest {
        // A CancellationException surfaced by the lookup must propagate unchanged. It must not
        // be swallowed by the generic transient-error catch, recorded as a transient cause, or
        // converted into a TransactionException.Timeout. If the dedicated CancellationException
        // rethrow were removed or reordered after the generic catch, the cancellation would be
        // treated as a transient error, retried across the whole budget, and surfaced as a
        // timeout, which would fail this test.
        val accountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        var calls = 0
        var thrown: Throwable? = null

        val job = launch {
            try {
                pollUntilAccountVisibleToRpc(accountId) { _ ->
                    calls++
                    // The lookup's cancellable work is cancelled and surfaces a
                    // CancellationException from inside the poll's try block.
                    throw CancellationException("lookup cancelled")
                }
            } catch (e: Throwable) {
                // Capture the propagated outcome, then rethrow so the job reflects the real
                // completion state (cancelled vs failed).
                thrown = e
                throw e
            }
        }

        job.join()

        // The cancellation aborted the poll on the first lookup instead of being retried as a
        // transient error across the budget.
        assertEquals(1, calls, "Cancellation must abort the poll, not be retried as transient")
        assertEquals(0L, testScheduler.currentTime, "No poll interval should elapse after cancellation")
        // The child coroutine ended cancelled, not completed normally and not failed.
        assertTrue(job.isCancelled, "Cancellation must propagate so the job ends cancelled")
        assertTrue(
            thrown is CancellationException,
            "The CancellationException must propagate unchanged, but was: $thrown"
        )
        assertFalse(
            thrown is TransactionException.Timeout,
            "A CancellationException must not be converted into a timeout"
        )
    }

    // MARK: - Deploy funding: poll RPC until the deployed contract instance is visible

    @Test
    fun testPollUntilContractVisible_waitsForAbsentThenProceeds() = runTest {
        // The deployed contract instance is not yet visible to the RPC for the first three
        // polls (the instance ledger entry is absent), then appears. The poll must keep
        // waiting and return once the instance entry is present.
        val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
        val absentPolls = 3
        var calls = 0

        pollUntilContractVisibleToRpc(contractId) { _ ->
            calls++
            // Visible (instance entry present) from the fourth lookup onward.
            calls > absentPolls
        }

        // One lookup per attempt: three "absent" plus the successful one.
        assertEquals(absentPolls + 1, calls)
        // It waited exactly one interval between each retry before succeeding, and did not
        // sleep again after the successful lookup.
        assertEquals(
            absentPolls * OZConstants.RPC_VISIBILITY_POLL_INTERVAL_MS,
            testScheduler.currentTime
        )
    }

    @Test
    fun testPollUntilContractVisible_returnsImmediatelyWhenAlreadyVisible() = runTest {
        // When the contract instance is visible on the first lookup, the poll proceeds
        // without delay.
        val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
        var calls = 0

        pollUntilContractVisibleToRpc(contractId) { _ ->
            calls++
            true // Visible immediately.
        }

        assertEquals(1, calls)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun testPollUntilContractVisible_throwsClearTimeoutWhenNeverVisible() = runTest {
        // The contract instance never becomes visible. The poll must exhaust its budget and
        // throw a clear, dedicated timeout rather than letting the opaque funding simulation
        // fail with an unrelated error.
        val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
        var calls = 0

        val error = assertFailsWith<TransactionException.Timeout> {
            pollUntilContractVisibleToRpc(contractId) { _ ->
                calls++
                false // Never visible.
            }
        }

        assertTrue(error.message.contains(contractId), "Timeout message should name the contract")
        assertTrue(
            error.message.contains("not visible to the Soroban RPC"),
            "Timeout message should explain the visibility failure, but was: ${error.message}"
        )
        assertTrue(
            error.message.contains("Retry shortly"),
            "Timeout message should advise retrying, but was: ${error.message}"
        )
        // The message must be ASCII only: no em dash (U+2014) is permitted.
        assertFalse(
            error.message.contains('—'),
            "Timeout message must be ASCII (no em dash), but was: ${error.message}"
        )
        // A pure "not visible" timeout has no transient RPC error to surface as the cause.
        assertNull(error.cause)
        // Budget consumed at the configured interval: 45s / 1.5s = 30 polls.
        assertTrue(calls > 0)
        assertEquals(
            OZConstants.RPC_VISIBILITY_TIMEOUT_SECONDS.toLong() * 1000L,
            testScheduler.currentTime
        )
    }

    @Test
    fun testPollUntilContractVisible_retriesTransientErrorsAndSurfacesLastAsCause() = runTest {
        // Transient RPC/transport errors (anything other than a clean absent/present result)
        // must be retried until the deadline, and the last one surfaced as the timeout cause
        // for diagnosability.
        val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
        var calls = 0

        val error = assertFailsWith<TransactionException.Timeout> {
            pollUntilContractVisibleToRpc(contractId) { _ ->
                calls++
                throw IllegalStateException("rpc unavailable #$calls")
            }
        }

        assertTrue(calls > 1, "Transient errors should be retried, not surfaced immediately")
        val cause = error.cause
        assertNotNull(cause, "The last transient error should be surfaced as the timeout cause")
        assertTrue(cause is IllegalStateException)
        assertTrue(
            cause.message?.contains("rpc unavailable") == true,
            "Cause should be the last transient error, but was: ${cause.message}"
        )
    }

    @Test
    fun testPollUntilContractVisible_recoversAfterTransientErrors() = runTest {
        // A transient error, then an absent instance, then a present instance must complete
        // normally, proving transient failures and the absent state do not abort the poll.
        val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
        var calls = 0

        pollUntilContractVisibleToRpc(contractId) { _ ->
            calls++
            when (calls) {
                1 -> throw IllegalStateException("transient rpc error")
                2 -> false // Instance not yet visible.
                else -> true // Visible on the third lookup.
            }
        }

        assertEquals(3, calls)
        assertEquals(
            2 * OZConstants.RPC_VISIBILITY_POLL_INTERVAL_MS,
            testScheduler.currentTime
        )
    }

    @Test
    fun testPollUntilContractVisible_cancellationPropagatesAndIsNotConvertedToTimeout() = runTest {
        // A CancellationException surfaced by the lookup must propagate unchanged. It must not
        // be swallowed by the generic transient-error catch, recorded as a transient cause, or
        // converted into a TransactionException.Timeout. If the dedicated CancellationException
        // rethrow were removed or reordered after the generic catch, the cancellation would be
        // treated as a transient error, retried across the whole budget, and surfaced as a
        // timeout, which would fail this test.
        val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
        var calls = 0
        var thrown: Throwable? = null

        val job = launch {
            try {
                pollUntilContractVisibleToRpc(contractId) { _ ->
                    calls++
                    // The lookup's cancellable work is cancelled and surfaces a
                    // CancellationException from inside the poll's try block.
                    throw CancellationException("lookup cancelled")
                }
            } catch (e: Throwable) {
                // Capture the propagated outcome, then rethrow so the job reflects the real
                // completion state (cancelled vs failed).
                thrown = e
                throw e
            }
        }

        job.join()

        // The cancellation aborted the poll on the first lookup instead of being retried as a
        // transient error across the budget.
        assertEquals(1, calls, "Cancellation must abort the poll, not be retried as transient")
        assertEquals(0L, testScheduler.currentTime, "No poll interval should elapse after cancellation")
        // The child coroutine ended cancelled, not completed normally and not failed.
        assertTrue(job.isCancelled, "Cancellation must propagate so the job ends cancelled")
        assertTrue(
            thrown is CancellationException,
            "The CancellationException must propagate unchanged, but was: $thrown"
        )
        assertFalse(
            thrown is TransactionException.Timeout,
            "A CancellationException must not be converted into a timeout"
        )
    }

    @Test
    fun testPollUntilContractVisible_absentEntryNotVisiblePresentEntryVisible() = runTest {
        // Wiring contract: waitForContractVisibleToRpc passes
        // `getContractData(...) != null` as the visibility check, so an empty entries result
        // (getContractData returns null) must read as "not visible" and a present entry as
        // "visible". Emulate that exact mapping to pin the structural visibility signal the
        // deploy/fund flow depends on.
        val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
        // null = instance ledger entry absent (RPC reports NotFound); non-null = present.
        val getContractDataResults: List<Any?> = listOf(null, null, Any())
        var calls = 0

        pollUntilContractVisibleToRpc(contractId) { _ ->
            val result = getContractDataResults[calls]
            calls++
            // Exactly the production lambda's structural mapping.
            result != null
        }

        // Two absent (null) lookups kept polling; the present (non-null) entry returned.
        assertEquals(3, calls)
        assertEquals(
            2 * OZConstants.RPC_VISIBILITY_POLL_INTERVAL_MS,
            testScheduler.currentTime
        )
    }
}

// MARK: - Mock WebAuthn Provider

/**
 * Mock WebAuthn provider for testing.
 */
class MockWebAuthnProvider(
    private val shouldCancel: Boolean = false,
    private val shouldFail: Boolean = false
) : WebAuthnProvider {

    override suspend fun register(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String
    ): WebAuthnRegistrationResult {
        if (shouldCancel) {
            throw Exception("User cancelled")
        }
        if (shouldFail) {
            throw Exception("Registration failed")
        }

        // Generate mock data
        val crypto = getEd25519Crypto()
        val credentialId = crypto.generatePrivateKey() // 32 random bytes

        val publicKey = ByteArray(65)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        // Fill rest with deterministic data
        for (i in 1 until 65) {
            publicKey[i] = (i % 256).toByte()
        }

        val attestationObject = ByteArray(100) { 0x00 }

        return WebAuthnRegistrationResult(
            credentialId = credentialId,
            publicKey = publicKey,
            attestationObject = attestationObject
        )
    }

    override suspend fun authenticate(
        challenge: ByteArray,
        allowCredentials: List<AllowCredential>?
    ): WebAuthnAuthenticationResult {
        if (shouldCancel) {
            throw Exception("User cancelled")
        }
        if (shouldFail) {
            throw Exception("Authentication failed")
        }

        // Generate mock data
        val crypto = getEd25519Crypto()
        val credentialId = crypto.generatePrivateKey() // 32 random bytes
        val authenticatorData = ByteArray(37) { 0x11 }
        val clientDataJSON = ByteArray(80) { 0x22 }
        val signature = ByteArray(64) { 0x33 }

        return WebAuthnAuthenticationResult(
            credentialId = credentialId,
            authenticatorData = authenticatorData,
            clientDataJSON = clientDataJSON,
            signature = signature
        )
    }
}
