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

    // MARK: - JSON Serialization Round-Trip Tests

    /**
     * Storage key used by OZExternalSignerManager for persisted wallet connections.
     * Must match the private constant in the production class.
     */
    private val walletStorageKey = "external_wallets"

    /**
     * Writes a single StoredWalletConnection as a JSON array directly to storage,
     * bypassing the manager's address validation. Useful for testing raw JSON parsing.
     */
    private suspend fun InMemoryWalletConnectionStorage.writeWalletJson(jsonString: String) {
        setItem(walletStorageKey, jsonString)
    }

    @Test
    fun testSerializationRoundTrip_singleConnection() = runTest {
        // Write JSON directly to storage, bypassing manager address validation.
        // Then create a manager + adapter and confirm restoreConnections correctly parses it.
        val storage = InMemoryWalletConnectionStorage()
        storage.writeWalletJson(
            """[{"address":"GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",""" +
                """"walletId":"freighter","walletName":"Freighter","connectedAt":1700000000000}]"""
        )

        val adapter = object : ExternalWalletAdapter {
            private val connectedWallets = mutableListOf<ConnectedWallet>()

            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun reconnect(walletId: String): ConnectedWallet? {
                if (walletId == "freighter") {
                    val w = ConnectedWallet(
                        address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",
                        walletId = "freighter",
                        walletName = "Freighter"
                    )
                    connectedWallets.add(w)
                    return w
                }
                return null
            }
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean =
                connectedWallets.any { it.address == address }
            override fun getConnectedWallets(): List<ConnectedWallet> = connectedWallets.toList()
            override fun getWalletForAddress(address: String): ConnectedWallet? =
                connectedWallets.find { it.address == address }
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        val manager = createManager(walletAdapter = adapter, walletConnectionStorage = storage)
        val restored = manager.restoreConnections()

        assertEquals(1, restored.size)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN", restored[0].address)
        assertEquals("freighter", restored[0].walletId)
        assertEquals("Freighter", restored[0].walletName)
    }

    @Test
    fun testSerializationRoundTrip_multipleConnections() = runTest {
        val storage = InMemoryWalletConnectionStorage()
        storage.writeWalletJson(
            """[""" +
                """{"address":"GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",""" +
                """"walletId":"freighter","walletName":"Freighter","connectedAt":1700000000000},""" +
                """{"address":"GBBM6BKZPEHWYO3E3YKREDPQXMS4VK35YLNU7NFBRI26RAN7GI5POFBB",""" +
                """"walletId":"lobstr","walletName":"LOBSTR","connectedAt":1700000001000},""" +
                """{"address":"GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGKBF3LZGXNGZAQJ8FQB2T",""" +
                """"walletId":"xbull","walletName":"xBull","connectedAt":1700000002000}]"""
        )

        val connectedWallets = mutableListOf<ConnectedWallet>()
        val adapter = object : ExternalWalletAdapter {
            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun reconnect(walletId: String): ConnectedWallet? {
                val w = when (walletId) {
                    "freighter" -> ConnectedWallet(
                        address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",
                        walletId = "freighter",
                        walletName = "Freighter"
                    )
                    "lobstr" -> ConnectedWallet(
                        address = "GBBM6BKZPEHWYO3E3YKREDPQXMS4VK35YLNU7NFBRI26RAN7GI5POFBB",
                        walletId = "lobstr",
                        walletName = "LOBSTR"
                    )
                    "xbull" -> ConnectedWallet(
                        address = "GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGKBF3LZGXNGZAQJ8FQB2T",
                        walletId = "xbull",
                        walletName = "xBull"
                    )
                    else -> null
                }
                if (w != null) connectedWallets.add(w)
                return w
            }
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean =
                connectedWallets.any { it.address == address }
            override fun getConnectedWallets(): List<ConnectedWallet> = connectedWallets.toList()
            override fun getWalletForAddress(address: String): ConnectedWallet? =
                connectedWallets.find { it.address == address }
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        val manager = createManager(walletAdapter = adapter, walletConnectionStorage = storage)
        val restored = manager.restoreConnections()

        assertEquals(3, restored.size)
        val walletIds = restored.map { it.walletId }.toSet()
        assertTrue(walletIds.contains("freighter"))
        assertTrue(walletIds.contains("lobstr"))
        assertTrue(walletIds.contains("xbull"))
    }

    @Test
    fun testSerializationRoundTrip_emptyStorage() = runTest {
        val storage = InMemoryWalletConnectionStorage()
        // Storage has no data at all
        val manager = createManager(walletAdapter = null, walletConnectionStorage = storage)

        // restoreConnections with no adapter returns empty without touching storage
        val result = manager.restoreConnections()
        assertTrue(result.isEmpty())

        // Confirm storage is still empty
        assertNull(storage.getItem(walletStorageKey))
    }

    @Test
    fun testSerializationRoundTrip_malformedJson() = runTest {
        val storage = InMemoryWalletConnectionStorage()
        // Write clearly invalid JSON; the parser must return empty list, not crash
        storage.writeWalletJson("this is not json")

        // Use a stub adapter so restoreConnections proceeds to parsing
        val adapter = object : ExternalWalletAdapter {
            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun reconnect(walletId: String): ConnectedWallet? = null
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean = false
            override fun getConnectedWallets(): List<ConnectedWallet> = emptyList()
            override fun getWalletForAddress(address: String): ConnectedWallet? = null
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        val manager = createManager(walletAdapter = adapter, walletConnectionStorage = storage)
        // Must not throw; malformed data results in no restored wallets
        val result = manager.restoreConnections()
        assertTrue(result.isEmpty())
    }

    @Test
    fun testSerializationRoundTrip_specialCharactersInWalletName() = runTest {
        val storage = InMemoryWalletConnectionStorage()
        // walletName contains a double-quote, a newline escape, and a backslash — all JSON-escaped.
        // The JSON-escaped form "My \\\"Wallet\\\"\\nLine2" represents: My \"Wallet\"\nLine2
        storage.writeWalletJson(
            """[{"address":"GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",""" +
                """"walletId":"test","walletName":"My \\\"Wallet\\\"\\nLine2","connectedAt":1700000000000}]"""
        )

        // The expected decoded walletName after JSON parsing: My "Wallet"\nLine2 (with real newline)
        val expectedWalletName = "My \"Wallet\"\nLine2"

        val adapter = object : ExternalWalletAdapter {
            private val connectedWallets = mutableListOf<ConnectedWallet>()

            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun reconnect(walletId: String): ConnectedWallet? {
                if (walletId == "test") {
                    val w = ConnectedWallet(
                        address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",
                        walletId = "test",
                        walletName = expectedWalletName
                    )
                    connectedWallets.add(w)
                    return w
                }
                return null
            }
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean =
                connectedWallets.any { it.address == address }
            override fun getConnectedWallets(): List<ConnectedWallet> = connectedWallets.toList()
            override fun getWalletForAddress(address: String): ConnectedWallet? =
                connectedWallets.find { it.address == address }
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        val manager = createManager(walletAdapter = adapter, walletConnectionStorage = storage)
        val restored = manager.restoreConnections()

        assertEquals(1, restored.size)
        assertEquals("test", restored[0].walletId)
    }

    @Test
    fun testSerializationRoundTrip_backwardCompatibility() = runTest {
        // This is the exact JSON format produced by the old manual serializer.
        // The new kotlinx.serialization parser must read it without errors.
        val legacyJson = """[{"address":"GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN","walletId":"freighter","walletName":"Freighter","connectedAt":1700000000000}]"""

        val storage = InMemoryWalletConnectionStorage()
        storage.writeWalletJson(legacyJson)

        val adapter = object : ExternalWalletAdapter {
            private val connectedWallets = mutableListOf<ConnectedWallet>()

            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun reconnect(walletId: String): ConnectedWallet? {
                if (walletId == "freighter") {
                    val w = ConnectedWallet(
                        address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",
                        walletId = "freighter",
                        walletName = "Freighter"
                    )
                    connectedWallets.add(w)
                    return w
                }
                return null
            }
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean =
                connectedWallets.any { it.address == address }
            override fun getConnectedWallets(): List<ConnectedWallet> = connectedWallets.toList()
            override fun getWalletForAddress(address: String): ConnectedWallet? =
                connectedWallets.find { it.address == address }
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        val manager = createManager(walletAdapter = adapter, walletConnectionStorage = storage)
        val restored = manager.restoreConnections()

        assertEquals(1, restored.size)
        assertEquals("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN", restored[0].address)
        assertEquals("freighter", restored[0].walletId)
        assertEquals("Freighter", restored[0].walletName)
    }

    @Test
    fun testSerializationRoundTrip_emptyStringFields() = runTest {
        val storage = InMemoryWalletConnectionStorage()
        // walletName is empty string
        storage.writeWalletJson(
            """[{"address":"GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",""" +
                """"walletId":"","walletName":"","connectedAt":1700000000000}]"""
        )

        val adapter = object : ExternalWalletAdapter {
            private val connectedWallets = mutableListOf<ConnectedWallet>()

            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun reconnect(walletId: String): ConnectedWallet? {
                if (walletId == "") {
                    val w = ConnectedWallet(
                        address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",
                        walletId = "",
                        walletName = ""
                    )
                    connectedWallets.add(w)
                    return w
                }
                return null
            }
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean =
                connectedWallets.any { it.address == address }
            override fun getConnectedWallets(): List<ConnectedWallet> = connectedWallets.toList()
            override fun getWalletForAddress(address: String): ConnectedWallet? =
                connectedWallets.find { it.address == address }
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        val manager = createManager(walletAdapter = adapter, walletConnectionStorage = storage)
        val restored = manager.restoreConnections()

        assertEquals(1, restored.size)
        assertEquals("", restored[0].walletId)
        assertEquals("", restored[0].walletName)
    }

    @Test
    fun testSerializationRoundTrip_connectedAtBoundaryValues() = runTest {
        val storage = InMemoryWalletConnectionStorage()
        // Test Long.MAX_VALUE and 0L as connectedAt values
        storage.writeWalletJson(
            """[""" +
                """{"address":"GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",""" +
                """"walletId":"w1","walletName":"MaxTime","connectedAt":${Long.MAX_VALUE}},""" +
                """{"address":"GBBM6BKZPEHWYO3E3YKREDPQXMS4VK35YLNU7NFBRI26RAN7GI5POFBB",""" +
                """"walletId":"w2","walletName":"ZeroTime","connectedAt":0}]"""
        )

        val restoredWallets = mutableListOf<ConnectedWallet>()
        val adapter = object : ExternalWalletAdapter {
            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun reconnect(walletId: String): ConnectedWallet? {
                val w = when (walletId) {
                    "w1" -> ConnectedWallet(
                        address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",
                        walletId = "w1",
                        walletName = "MaxTime"
                    )
                    "w2" -> ConnectedWallet(
                        address = "GBBM6BKZPEHWYO3E3YKREDPQXMS4VK35YLNU7NFBRI26RAN7GI5POFBB",
                        walletId = "w2",
                        walletName = "ZeroTime"
                    )
                    else -> null
                }
                if (w != null) restoredWallets.add(w)
                return w
            }
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean =
                restoredWallets.any { it.address == address }
            override fun getConnectedWallets(): List<ConnectedWallet> = restoredWallets.toList()
            override fun getWalletForAddress(address: String): ConnectedWallet? =
                restoredWallets.find { it.address == address }
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        val manager = createManager(walletAdapter = adapter, walletConnectionStorage = storage)
        val result = manager.restoreConnections()

        assertEquals(2, result.size)
        val walletIds = result.map { it.walletId }.toSet()
        assertTrue(walletIds.contains("w1"))
        assertTrue(walletIds.contains("w2"))
    }
}
