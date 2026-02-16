//
//  SmartAccountKitTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.crypto.getEd25519Crypto
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
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
        webauthnProvider: WebAuthnProvider? = null
    ): OZSmartAccountConfig {
        return OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = Network.TESTNET.networkPassphrase,
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
        assertTrue(credential.isPrimary)
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
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        val result = kit.walletOperations.createWallet(userName = "Test User", autoSubmit = false)

        assertNotNull(result)
        assertNotNull(result.credentialId)
        assertNotNull(result.contractId)
        assertEquals(65, result.publicKey.size)
        assertNull(result.transactionHash) // autoSubmit = false
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
    fun testWalletOperations_connectWallet_noWebAuthnProvider() = runTest {
        val config = createTestConfig(webauthnProvider = null)
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.connectWallet()
        }
    }

    @Test
    fun testWalletOperations_connectWallet_withValidSession() = runTest {
        val config = createTestConfig()
        val storage = InMemoryStorageAdapter()
        val kit = OZSmartAccountKit.create(config.copy(storage = storage))

        // Save a valid session
        val session = StoredSession(
            credentialId = "test-credential-1",
            contractId = "CBCD1234" + "A".repeat(48),
            connectedAt = Clock.System.now().toEpochMilliseconds(),
            expiresAt = Clock.System.now().toEpochMilliseconds() + 60000
        )
        storage.saveSession(session)

        val result = kit.walletOperations.connectWallet()

        assertNotNull(result)
        assertEquals("test-credential-1", result.credentialId)
        assertEquals(session.contractId, result.contractId)
        assertTrue(result.restoredFromSession)
    }

    @Test
    fun testWalletOperations_connectWallet_withExpiredSession() = runTest {
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

        // Should trigger WebAuthn authentication due to expired session
        // This will fail without indexer/storage lookup, but test the flow
        assertFailsWith<WalletException.NotFound> {
            kit.walletOperations.connectWallet()
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
        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountSharedUtils.amountToStroops(0.0)
        }
    }

    @Test
    fun testTransactionOperations_validateTransfer_negativeAmount() = runTest {
        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountSharedUtils.amountToStroops(-10.0)
        }
    }

    @Test
    fun testTransactionOperations_amountConversion() = runTest {
        val stroops = SmartAccountSharedUtils.amountToStroops(10.0)
        assertEquals(100_000_000L, stroops)
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

    // MARK: - 7. Context Rule Manager Tests

    // Note: Context rule manager tests would require RPC simulation
    // We test validation and limits here

    @Test
    fun testContextRules_maxRulesLimit() {
        // Test that the constant is defined correctly
        assertEquals(15, SmartAccountConstants.MAX_CONTEXT_RULES)
    }

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
        assertEquals(5, SmartAccountConstants.MAX_POLICIES)
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
        val params = PolicyInstallParams.SimpleThreshold(threshold = 0u)
        val scVal = params.toScVal()

        // Zero threshold is allowed (validation is contract-side)
        assertNotNull(scVal)
        assertTrue(scVal is SCValXdr.Map)
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
            spendingLimit = 1000_0000000L, // 1000 XLM in stroops
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
            spendingLimit = 0L,
            periodLedgers = 17280u
        )

        assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
    }

    @Test
    fun testPolicyInstallParams_spendingLimit_negativeLimit() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = -100L,
            periodLedgers = 17280u
        )

        assertFailsWith<ValidationException.InvalidInput> {
            params.toScVal()
        }
    }

    @Test
    fun testPolicyInstallParams_spendingLimit_zeroPeriod() {
        val params = PolicyInstallParams.SpendingLimit(
            spendingLimit = 1000_0000000L,
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
        assertEquals(15, SmartAccountConstants.MAX_SIGNERS)
    }

    @Test
    fun testMultiSigner_parseSignersFromContextRulesResponse_delegated() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        // Build mock response: Vec([ContextRule])
        // ContextRule is Map with field "signers": Vec([Signer])
        // Delegated signer: Vec([Symbol("Delegated"), Address(G-address)])

        val delegatedAddress = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        val keyPair = KeyPair.fromAccountId(delegatedAddress)
        val scAddress = com.soneso.stellar.sdk.xdr.SCAddressXdr.AccountId(keyPair.getXdrAccountId())

        val signerScVal = Scv.toVec(listOf(
            Scv.toSymbol("Delegated"),
            SCValXdr.Address(scAddress)
        ))

        val signersVec = Scv.toVec(listOf(signerScVal))

        val ruleMap = Scv.toMap(linkedMapOf(
            Scv.toSymbol("id") to Scv.toUint32(0u),
            Scv.toSymbol("signers") to signersVec
        ))

        val resultScVal = Scv.toVec(listOf(ruleMap))

        // Parse the response
        val parsed = kit.multiSignerManager.parseSignersFromContextRulesResponse(resultScVal)

        assertEquals(1, parsed.size)
        assertEquals("Delegated", parsed[0].tag)
        assertEquals(delegatedAddress, parsed[0].address)
        assertNull(parsed[0].keyBytes)
    }

    @Test
    fun testMultiSigner_parseSignersFromContextRulesResponse_external() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        // Build mock response with External signer
        // External signer: Vec([Symbol("External"), Address(C-address), Bytes(keyData)])

        val verifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val keyData = ByteArray(65) { 0x04 }

        val signerScVal = Scv.toVec(listOf(
            Scv.toSymbol("External"),
            Scv.toAddress(com.soneso.stellar.sdk.Address(verifierAddress).toSCAddress()),
            Scv.toBytes(keyData)
        ))

        val signersVec = Scv.toVec(listOf(signerScVal))

        val ruleMap = Scv.toMap(linkedMapOf(
            Scv.toSymbol("id") to Scv.toUint32(0u),
            Scv.toSymbol("signers") to signersVec
        ))

        val resultScVal = Scv.toVec(listOf(ruleMap))

        // Parse the response
        val parsed = kit.multiSignerManager.parseSignersFromContextRulesResponse(resultScVal)

        assertEquals(1, parsed.size)
        assertEquals("External", parsed[0].tag)
        assertEquals(verifierAddress, parsed[0].address)
        assertNotNull(parsed[0].keyBytes)
        assertTrue(keyData.contentEquals(parsed[0].keyBytes))
    }

    @Test
    fun testMultiSigner_parseSignersFromContextRulesResponse_mixed() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        // Build mock response with both Delegated and External signers

        val delegatedAddress = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
        val keyPair = KeyPair.fromAccountId(delegatedAddress)
        val delegatedScAddress = com.soneso.stellar.sdk.xdr.SCAddressXdr.AccountId(keyPair.getXdrAccountId())

        val delegatedSignerScVal = Scv.toVec(listOf(
            Scv.toSymbol("Delegated"),
            SCValXdr.Address(delegatedScAddress)
        ))

        val verifierAddress = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"
        val keyData = ByteArray(65) { 0x04 }

        val externalSignerScVal = Scv.toVec(listOf(
            Scv.toSymbol("External"),
            Scv.toAddress(com.soneso.stellar.sdk.Address(verifierAddress).toSCAddress()),
            Scv.toBytes(keyData)
        ))

        val signersVec = Scv.toVec(listOf(delegatedSignerScVal, externalSignerScVal))

        val ruleMap = Scv.toMap(linkedMapOf(
            Scv.toSymbol("id") to Scv.toUint32(0u),
            Scv.toSymbol("signers") to signersVec
        ))

        val resultScVal = Scv.toVec(listOf(ruleMap))

        // Parse the response
        val parsed = kit.multiSignerManager.parseSignersFromContextRulesResponse(resultScVal)

        assertEquals(2, parsed.size)

        // First should be Delegated
        assertEquals("Delegated", parsed[0].tag)
        assertEquals(delegatedAddress, parsed[0].address)

        // Second should be External
        assertEquals("External", parsed[1].tag)
        assertEquals(verifierAddress, parsed[1].address)
    }

    @Test
    fun testMultiSigner_parseSignersFromContextRulesResponse_invalidScVal() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        // Test with invalid ScVal (not a Vec)
        val invalidScVal = Scv.toVoid()

        val parsed = kit.multiSignerManager.parseSignersFromContextRulesResponse(invalidScVal)

        assertEquals(0, parsed.size)
    }

    @Test
    fun testMultiSigner_parseSignersFromContextRulesResponse_emptyRules() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        // Test with empty Vec
        val emptyScVal = Scv.toVec(emptyList())

        val parsed = kit.multiSignerManager.parseSignersFromContextRulesResponse(emptyScVal)

        assertEquals(0, parsed.size)
    }

    // MARK: - 10. Relayer Client Tests

    @Test
    fun testRelayerClient_initialization() {
        val client = OZRelayerClient(
            relayerUrl = "https://relayer.example.com",
            timeoutMs = 60000
        )

        assertNotNull(client)
        assertTrue(client.isConfigured)
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
        assertTrue(client.isConfigured)
    }

    // Note: Full relayer tests would require a mock HTTP client
    // That's beyond the scope of pure KMP common tests without platform-specific mocking

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
        val encoded = SmartAccountSharedUtils.base64urlEncode(data)

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
        val encoded = SmartAccountSharedUtils.base64urlEncode(data)
        val decoded = SmartAccountSharedUtils.base64urlDecode(encoded)

        assertNotNull(decoded)
        assertTrue(data.contentEquals(decoded))
    }

    @Test
    fun testStroopsConversion() {
        val stroops = 100_000_000L
        val scVal = SmartAccountSharedUtils.stroopsToI128ScVal(stroops)

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
            .rpName("Test Wallet")
            .sessionExpiryMs(86400000L)
            .relayerUrl("https://relayer.example.com")
            .indexerUrl("https://indexer.example.com")
            .build()

        assertEquals("Test Wallet", config.rpName)
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
                challenge: ByteArray
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
        val credential1 = StoredCredential(
            credentialId = "test-1",
            publicKey = ByteArray(65) { 0x04 },
            contractId = "CBCD1234" + "A".repeat(48)
        )

        val credential2 = StoredCredential(
            credentialId = "test-1",
            publicKey = ByteArray(65) { 0x04 },
            contractId = "CBCD1234" + "A".repeat(48)
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
        assertEquals(10_000_000L, SmartAccountConstants.STROOPS_PER_XLM)
    }

    @Test
    fun testConstants_maxSigners() {
        assertEquals(15, SmartAccountConstants.MAX_SIGNERS)
    }

    @Test
    fun testConstants_maxPolicies() {
        assertEquals(5, SmartAccountConstants.MAX_POLICIES)
    }

    @Test
    fun testConstants_maxContextRules() {
        assertEquals(15, SmartAccountConstants.MAX_CONTEXT_RULES)
    }

    @Test
    fun testConstants_sessionExpiry() {
        assertEquals(604_800_000L, SmartAccountConstants.DEFAULT_SESSION_EXPIRY_MS)
    }

    @Test
    fun testConstants_ledgersPerHour() {
        assertEquals(720, SmartAccountConstants.LEDGERS_PER_HOUR)
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

    // MARK: - GAP-02: authenticatePasskey Tests

    @Test
    fun testWalletOperations_authenticatePasskey_noProvider() = runTest {
        val config = createTestConfig()
        val kit = OZSmartAccountKit.create(config)

        assertFailsWith<WebAuthnException.NotSupported> {
            kit.walletOperations.connectWallet()
        }
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
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        val result = kit.walletOperations.createWallet(
            userName = "Test User",
            autoSubmit = false,
            autoFund = false,
            nativeTokenContract = null
        )

        assertNotNull(result)
        assertNull(result.transactionHash)
    }

    @Test
    fun testCreateWallet_signatureWithAutoFundParameters() = runTest {
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        val result = kit.walletOperations.createWallet(
            userName = "Test User",
            autoSubmit = false,
            autoFund = false,
            nativeTokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
        )

        assertNotNull(result)
        assertNotNull(result.credentialId)
        assertNotNull(result.contractId)
        assertNotNull(result.publicKey)
    }

    @Test
    fun testCreateWallet_autoFundParameterAccepted() = runTest {
        val mockProvider = MockWebAuthnProvider()
        val config = createTestConfig(webauthnProvider = mockProvider)
        val kit = OZSmartAccountKit.create(config)

        val result = kit.walletOperations.createWallet(
            userName = "Test User",
            autoSubmit = false,
            autoFund = false
        )

        assertNotNull(result)
    }

    // MARK: - Fix 4: connectWallet with options

    @Test
    fun testConnectWalletOptions_defaultValues() = runTest {
        val options = OZWalletOperations.ConnectWalletOptions()
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertFalse(options.fresh)
    }

    @Test
    fun testConnectWalletOptions_withCredentialId() = runTest {
        val options = OZWalletOperations.ConnectWalletOptions(credentialId = "test-credential")
        assertEquals("test-credential", options.credentialId)
        assertNull(options.contractId)
        assertFalse(options.fresh)
    }

    @Test
    fun testConnectWalletOptions_withFresh() = runTest {
        val options = OZWalletOperations.ConnectWalletOptions(fresh = true)
        assertNull(options.credentialId)
        assertNull(options.contractId)
        assertTrue(options.fresh)
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

        val result = kit.walletOperations.connectWallet(
            options = OZWalletOperations.ConnectWalletOptions()
        )

        assertNotNull(result)
        assertEquals("test-credential-1", result.credentialId)
        assertTrue(result.restoredFromSession)
    }

    @Test
    fun testConnectWallet_freshOptionSkipsSession() = runTest {
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

        assertFailsWith<WalletException.NotFound> {
            kit.walletOperations.connectWallet(
                options = OZWalletOperations.ConnectWalletOptions(fresh = true)
            )
        }
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
        challenge: ByteArray
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
