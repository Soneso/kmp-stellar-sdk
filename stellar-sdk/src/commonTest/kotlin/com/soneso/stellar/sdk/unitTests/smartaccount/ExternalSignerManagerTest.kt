//
//  ExternalSignerManagerTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [OZExternalSignerManager].
 *
 * Tests cover the external signer lifecycle that is not already covered
 * in SmartAccountKitTest:
 * - addFromSecret: adding keypair signers from Stellar secret keys
 * - canSignFor: checking signing capability
 * - get / getAll / hasSigners: querying signer state
 * - remove / removeAll: cleaning up signers
 * - Error paths: invalid secret key, signer not found for signing
 * - hasWalletAdapter property
 * - InMemoryWalletConnectionStorage basic operations
 *
 * All tests use in-memory state to avoid network dependencies.
 */
class ExternalSignerManagerTest {

    // MARK: - Test Fixtures

    private fun createManager(
        walletAdapter: ExternalWalletAdapter? = null,
        walletConnectionStorage: WalletConnectionStorage? = null
    ): OZExternalSignerManager {
        return OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase,
            walletAdapter = walletAdapter,
            walletConnectionStorage = walletConnectionStorage
        )
    }

    // MARK: - addFromSecret Tests

    @Test
    fun testAddFromSecret_validSecretKey() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()
        val secretSeed = keypair.getSecretSeed()!!.concatToString()

        val address = manager.addFromSecret(secretSeed)

        assertEquals(keypair.getAccountId(), address)
        assertTrue(address.startsWith("G"))
        assertEquals(56, address.length)
    }

    @Test
    fun testAddFromSecret_signerIsAccessibleViaGet() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()
        val secretSeed = keypair.getSecretSeed()!!.concatToString()

        val address = manager.addFromSecret(secretSeed)

        val info = manager.get(address)
        assertNotNull(info)
        assertEquals(address, info.address)
        assertEquals(ExternalSignerType.KEYPAIR, info.type)
        assertNull(info.walletName)
        assertNull(info.walletId)
    }

    @Test
    fun testAddFromSecret_invalidSecretKeyThrows() = runTest {
        val manager = createManager()

        assertFailsWith<SignerException.Invalid> {
            manager.addFromSecret("INVALID_SECRET_KEY")
        }
    }

    @Test
    fun testAddFromSecret_emptySecretKeyThrows() = runTest {
        val manager = createManager()

        assertFailsWith<SignerException.Invalid> {
            manager.addFromSecret("")
        }
    }

    @Test
    fun testAddFromSecret_publicKeyInsteadOfSecretThrows() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()

        // A G-address is not a valid secret key
        assertFailsWith<SignerException.Invalid> {
            manager.addFromSecret(keypair.getAccountId())
        }
    }

    @Test
    fun testAddFromSecret_sameSecretOverwritesSilently() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()
        val secretSeed = keypair.getSecretSeed()!!.concatToString()

        val address1 = manager.addFromSecret(secretSeed)
        val address2 = manager.addFromSecret(secretSeed)

        assertEquals(address1, address2)

        // Should still have exactly one signer
        val all = manager.getAll()
        assertEquals(1, all.size)
    }

    @Test
    fun testAddFromSecret_multipleDistinctSigners() = runTest {
        val manager = createManager()

        val kp1 = KeyPair.random()
        val kp2 = KeyPair.random()
        val kp3 = KeyPair.random()

        manager.addFromSecret(kp1.getSecretSeed()!!.concatToString())
        manager.addFromSecret(kp2.getSecretSeed()!!.concatToString())
        manager.addFromSecret(kp3.getSecretSeed()!!.concatToString())

        val all = manager.getAll()
        assertEquals(3, all.size)

        val addresses = all.map { it.address }.toSet()
        assertTrue(addresses.contains(kp1.getAccountId()))
        assertTrue(addresses.contains(kp2.getAccountId()))
        assertTrue(addresses.contains(kp3.getAccountId()))
    }

    // MARK: - canSignFor Tests

    @Test
    fun testCanSignFor_registeredKeypair() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()

        val address = manager.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        assertTrue(manager.canSignFor(address))
    }

    @Test
    fun testCanSignFor_unregisteredAddress() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()

        assertFalse(manager.canSignFor(keypair.getAccountId()))
    }

    @Test
    fun testCanSignFor_afterRemoval() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()
        val address = manager.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        assertTrue(manager.canSignFor(address))

        manager.remove(address)

        assertFalse(manager.canSignFor(address))
    }

    // MARK: - get Tests

    @Test
    fun testGet_existingKeypairSigner() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()
        val address = manager.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        val info = manager.get(address)
        assertNotNull(info)
        assertEquals(address, info.address)
        assertEquals(ExternalSignerType.KEYPAIR, info.type)
    }

    @Test
    fun testGet_nonExistentReturnsNull() = runTest {
        val manager = createManager()

        val info = manager.get("GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF")
        assertNull(info)
    }

    // MARK: - getAll Tests

    @Test
    fun testGetAll_emptyManagerReturnsEmptyList() = runTest {
        val manager = createManager()

        val all = manager.getAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun testGetAll_returnsAllKeypairSigners() = runTest {
        val manager = createManager()

        val kp1 = KeyPair.random()
        val kp2 = KeyPair.random()

        manager.addFromSecret(kp1.getSecretSeed()!!.concatToString())
        manager.addFromSecret(kp2.getSecretSeed()!!.concatToString())

        val all = manager.getAll()
        assertEquals(2, all.size)
        assertTrue(all.all { it.type == ExternalSignerType.KEYPAIR })
    }

    // MARK: - hasSigners Tests

    @Test
    fun testHasSigners_emptyManagerReturnsFalse() = runTest {
        val manager = createManager()

        assertFalse(manager.hasSigners())
    }

    @Test
    fun testHasSigners_withKeypairSignerReturnsTrue() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()

        manager.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        assertTrue(manager.hasSigners())
    }

    @Test
    fun testHasSigners_afterRemoveAllReturnsFalse() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()

        manager.addFromSecret(keypair.getSecretSeed()!!.concatToString())
        assertTrue(manager.hasSigners())

        manager.removeAll()
        assertFalse(manager.hasSigners())
    }

    // MARK: - remove Tests

    @Test
    fun testRemove_existingSigner() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()
        val address = manager.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        manager.remove(address)

        assertNull(manager.get(address))
        assertFalse(manager.canSignFor(address))
        assertTrue(manager.getAll().isEmpty())
    }

    @Test
    fun testRemove_nonExistentDoesNotThrow() = runTest {
        val manager = createManager()

        // Should not throw
        manager.remove("GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF")
    }

    @Test
    fun testRemove_onlyRemovesTargetSigner() = runTest {
        val manager = createManager()
        val kp1 = KeyPair.random()
        val kp2 = KeyPair.random()

        val addr1 = manager.addFromSecret(kp1.getSecretSeed()!!.concatToString())
        val addr2 = manager.addFromSecret(kp2.getSecretSeed()!!.concatToString())

        manager.remove(addr1)

        assertNull(manager.get(addr1))
        assertNotNull(manager.get(addr2))
        assertEquals(1, manager.getAll().size)
    }

    // MARK: - removeAll Tests

    @Test
    fun testRemoveAll_clearsAllSigners() = runTest {
        val manager = createManager()

        val kp1 = KeyPair.random()
        val kp2 = KeyPair.random()
        val kp3 = KeyPair.random()

        manager.addFromSecret(kp1.getSecretSeed()!!.concatToString())
        manager.addFromSecret(kp2.getSecretSeed()!!.concatToString())
        manager.addFromSecret(kp3.getSecretSeed()!!.concatToString())

        assertEquals(3, manager.getAll().size)

        manager.removeAll()

        assertTrue(manager.getAll().isEmpty())
        assertFalse(manager.hasSigners())
    }

    @Test
    fun testRemoveAll_emptyManagerDoesNotThrow() = runTest {
        val manager = createManager()

        // Should not throw
        manager.removeAll()
    }

    // MARK: - signAuthEntry Tests

    @Test
    fun testSignAuthEntry_noSignerThrows() = runTest {
        val manager = createManager()

        assertFailsWith<SignerException.NotFound> {
            manager.signAuthEntry(
                address = "GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF",
                authEntry = "AAAA" // dummy base64
            )
        }
    }

    // MARK: - hasWalletAdapter Tests

    @Test
    fun testHasWalletAdapter_noAdapterReturnsFalse() {
        val manager = createManager(walletAdapter = null)
        assertFalse(manager.hasWalletAdapter)
    }

    // MARK: - addFromWallet without adapter Tests

    @Test
    fun testAddFromWallet_noAdapterThrows() = runTest {
        val manager = createManager(walletAdapter = null)

        assertFailsWith<ConfigurationException.MissingConfig> {
            manager.addFromWallet()
        }
    }

    // MARK: - InMemoryWalletConnectionStorage Tests

    @Test
    fun testInMemoryWalletConnectionStorage_basicOperations() = runTest {
        val storage = InMemoryWalletConnectionStorage()

        // Initially empty
        assertNull(storage.getItem("key1"))

        // Set and get
        storage.setItem("key1", "value1")
        assertEquals("value1", storage.getItem("key1"))

        // Overwrite
        storage.setItem("key1", "value2")
        assertEquals("value2", storage.getItem("key1"))

        // Remove
        storage.removeItem("key1")
        assertNull(storage.getItem("key1"))
    }

    @Test
    fun testInMemoryWalletConnectionStorage_removeNonExistentNoOp() = runTest {
        val storage = InMemoryWalletConnectionStorage()

        // Should not throw
        storage.removeItem("nonexistent")
    }

    @Test
    fun testInMemoryWalletConnectionStorage_multipleKeys() = runTest {
        val storage = InMemoryWalletConnectionStorage()

        storage.setItem("key1", "value1")
        storage.setItem("key2", "value2")
        storage.setItem("key3", "value3")

        assertEquals("value1", storage.getItem("key1"))
        assertEquals("value2", storage.getItem("key2"))
        assertEquals("value3", storage.getItem("key3"))

        storage.removeItem("key2")
        assertNull(storage.getItem("key2"))
        assertEquals("value1", storage.getItem("key1"))
        assertEquals("value3", storage.getItem("key3"))
    }

    // MARK: - ExternalSignerInfo Data Class Tests

    @Test
    fun testExternalSignerInfo_keypairType() {
        val info = ExternalSignerInfo(
            address = "GABC1234",
            type = ExternalSignerType.KEYPAIR
        )

        assertEquals("GABC1234", info.address)
        assertEquals(ExternalSignerType.KEYPAIR, info.type)
        assertNull(info.walletName)
        assertNull(info.walletId)
    }

    @Test
    fun testExternalSignerInfo_walletType() {
        val info = ExternalSignerInfo(
            address = "GDEF5678",
            type = ExternalSignerType.WALLET,
            walletName = "Freighter",
            walletId = "freighter"
        )

        assertEquals("GDEF5678", info.address)
        assertEquals(ExternalSignerType.WALLET, info.type)
        assertEquals("Freighter", info.walletName)
        assertEquals("freighter", info.walletId)
    }

    // MARK: - restoreConnections Tests

    @Test
    fun testRestoreConnections_noStorageReturnsEmpty() = runTest {
        val manager = createManager(walletConnectionStorage = null)

        val restored = manager.restoreConnections()
        assertTrue(restored.isEmpty())
    }

    @Test
    fun testRestoreConnections_noAdapterReturnsEmpty() = runTest {
        val storage = InMemoryWalletConnectionStorage()
        val manager = createManager(walletAdapter = null, walletConnectionStorage = storage)

        val restored = manager.restoreConnections()
        assertTrue(restored.isEmpty())
    }
}
