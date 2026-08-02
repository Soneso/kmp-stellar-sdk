//
//  ExternalSignerManagerTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.oz

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
 *
 * All tests use in-memory state to avoid network dependencies.
 */
class ExternalSignerManagerTest {

    // MARK: - Test Fixtures

    private fun createManager(
        walletAdapter: ExternalWalletAdapter? = null
    ): OZExternalSignerManager {
        return OZExternalSignerManager(
            networkPassphrase = Network.TESTNET.networkPassphrase,
            walletAdapter = walletAdapter
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

    // MARK: - clearInMemorySigners Tests

    @Test
    fun testClearInMemorySigners_clearsKeypairAndEd25519Signers() = runTest {
        val manager = createManager()

        val kp = KeyPair.random()
        val address = manager.addFromSecret(kp.getSecretSeed()!!.concatToString())
        val rawSeed = ByteArray(32) { (it + 1).toByte() }
        val publicKey = manager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        assertTrue(manager.canSignFor(address))
        assertTrue(manager.canSignEd25519For(VERIFIER_A, publicKey))

        manager.clearInMemorySigners()

        assertFalse(manager.canSignFor(address))
        assertFalse(manager.canSignEd25519For(VERIFIER_A, publicKey))
        assertTrue(manager.getAll().isEmpty())
        assertFalse(manager.hasSigners())
    }

    @Test
    fun testClearInMemorySigners_keepsWalletConnections() = runTest {
        val walletAddress = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val wallet = ConnectedWallet(
            address = walletAddress,
            walletId = "freighter",
            walletName = "Freighter"
        )
        var disconnected = false
        val adapter = object : ExternalWalletAdapter {
            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun disconnect() {
                disconnected = true
            }
            override fun canSignFor(address: String): Boolean = address == walletAddress
            override fun getConnectedWallets(): List<ConnectedWallet> = listOf(wallet)
            override fun getWalletForAddress(address: String): ConnectedWallet? =
                if (address == walletAddress) wallet else null
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }
        val manager = createManager(walletAdapter = adapter)

        val kp = KeyPair.random()
        val keypairAddress = manager.addFromSecret(kp.getSecretSeed()!!.concatToString())

        manager.clearInMemorySigners()

        // In-memory keypair is gone; the wallet connection is untouched
        assertNull(manager.get(keypairAddress))
        assertFalse(disconnected)
        assertTrue(manager.canSignFor(walletAddress))
        assertEquals(1, manager.getAll().size)
        assertEquals(ExternalSignerType.WALLET, manager.getAll().single().type)
    }

    @Test
    fun testClearInMemorySigners_emptyManagerDoesNotThrow() {
        val manager = createManager()

        // Should not throw, and is callable from non-suspend contexts
        manager.clearInMemorySigners()
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

    @Test
    fun testHasWalletAdapter_adapterConfiguredReturnsTrue() {
        val manager = createManager(walletAdapter = RecordingWalletAdapter())
        assertTrue(manager.hasWalletAdapter)
    }

    // MARK: - Wallet Adapter Query Tests

    @Test
    fun testGetAll_addressRegisteredAsKeypairAndWallet_reportedOnceAsKeypair() = runTest {
        val keypair = KeyPair.random()
        val sharedAddress = keypair.getAccountId()
        val adapter = RecordingWalletAdapter(
            wallets = listOf(
                ConnectedWallet(sharedAddress, walletId = "freighter", walletName = "Freighter"),
                ConnectedWallet(OTHER_WALLET_ADDRESS, walletId = "lobstr", walletName = "LOBSTR")
            )
        )
        val manager = createManager(walletAdapter = adapter)
        manager.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        val all = manager.getAll()

        assertEquals(2, all.size, "The shared address must not be listed twice")
        val shared = all.single { it.address == sharedAddress }
        assertEquals(
            ExternalSignerType.KEYPAIR,
            shared.type,
            "The keypair registration takes precedence over the wallet registration"
        )
        assertNull(shared.walletName, "The keypair entry carries no wallet metadata")
        val walletOnly = all.single { it.address == OTHER_WALLET_ADDRESS }
        assertEquals(ExternalSignerType.WALLET, walletOnly.type)
        assertEquals("LOBSTR", walletOnly.walletName)
        assertEquals("lobstr", walletOnly.walletId)
    }

    @Test
    fun testHasSigners_walletAdapterWithNoConnectedWallets_returnsFalse() = runTest {
        val manager = createManager(walletAdapter = RecordingWalletAdapter(wallets = emptyList()))

        assertFalse(
            manager.hasSigners(),
            "A configured adapter with no live connections is not a signer"
        )
    }

    @Test
    fun testHasSigners_walletAdapterWithConnectedWallet_returnsTrue() = runTest {
        val adapter = RecordingWalletAdapter(
            wallets = listOf(
                ConnectedWallet(OTHER_WALLET_ADDRESS, walletId = "lobstr", walletName = "LOBSTR")
            )
        )
        val manager = createManager(walletAdapter = adapter)

        assertTrue(manager.hasSigners())
    }

    // MARK: - Wallet Adapter Signing Tests

    @Test
    fun testSignAuthEntry_adapterCannotSignForAddress_throwsNotFound() = runTest {
        val adapter = RecordingWalletAdapter(
            wallets = listOf(
                ConnectedWallet(OTHER_WALLET_ADDRESS, walletId = "lobstr", walletName = "LOBSTR")
            )
        )
        val manager = createManager(walletAdapter = adapter)

        val unknownAddress = KeyPair.random().getAccountId()
        assertFailsWith<SignerException.NotFound> {
            manager.signAuthEntry(address = unknownAddress, authEntry = "AAAA")
        }
        assertEquals(
            0,
            adapter.signAuthEntryCallCount,
            "The adapter must not be asked to sign for an address it cannot sign for"
        )
    }

    @Test
    fun testSignAuthEntry_adapterThrows_wrappedInSigningFailed() = runTest {
        val adapter = RecordingWalletAdapter(
            wallets = listOf(
                ConnectedWallet(OTHER_WALLET_ADDRESS, walletId = "lobstr", walletName = "LOBSTR")
            ),
            signFailure = IllegalStateException("user rejected the signing request")
        )
        val manager = createManager(walletAdapter = adapter)

        val ex = assertFailsWith<TransactionException.SigningFailed> {
            manager.signAuthEntry(address = OTHER_WALLET_ADDRESS, authEntry = "AAAA")
        }
        assertTrue(
            ex.message.contains(OTHER_WALLET_ADDRESS),
            "The failure must name the address that could not be signed for; got: ${ex.message}"
        )
        assertTrue(
            ex.message.contains("user rejected the signing request"),
            "The failure must relay the adapter's own message; got: ${ex.message}"
        )
    }

    @Test
    fun testSignAuthEntry_adapterOmitsSignerAddress_fallsBackToRequestedAddress() = runTest {
        val adapter = RecordingWalletAdapter(
            wallets = listOf(
                ConnectedWallet(OTHER_WALLET_ADDRESS, walletId = "lobstr", walletName = "LOBSTR")
            ),
            reportSignerAddress = false
        )
        val manager = createManager(walletAdapter = adapter)

        val result = manager.signAuthEntry(address = OTHER_WALLET_ADDRESS, authEntry = "AAAA")

        assertEquals("c2lnbmF0dXJl", result.signedAuthEntry)
        assertEquals(
            OTHER_WALLET_ADDRESS,
            result.signerAddress,
            "When the wallet does not report a signer address, the requested address is used"
        )
        assertEquals(1, adapter.signAuthEntryCallCount)
        assertEquals(
            Network.TESTNET.networkPassphrase,
            adapter.lastSignOptions?.networkPassphrase,
            "The manager's network passphrase must be passed to the adapter"
        )
        assertEquals(OTHER_WALLET_ADDRESS, adapter.lastSignOptions?.address)
    }

    @Test
    fun testSignAuthEntry_adapterReportsSignerAddress_isPreserved() = runTest {
        val adapter = RecordingWalletAdapter(
            wallets = listOf(
                ConnectedWallet(OTHER_WALLET_ADDRESS, walletId = "lobstr", walletName = "LOBSTR")
            )
        )
        val manager = createManager(walletAdapter = adapter)

        val result = manager.signAuthEntry(address = OTHER_WALLET_ADDRESS, authEntry = "AAAA")

        assertEquals(OTHER_WALLET_ADDRESS, result.signerAddress)
    }

    @Test
    fun testSignAuthEntry_keypairSignerWithMalformedBase64Preimage_throwsSigningFailed() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()
        val address = manager.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        val ex = assertFailsWith<TransactionException.SigningFailed> {
            manager.signAuthEntry(address = address, authEntry = "not valid base64 !!!")
        }
        assertTrue(
            ex.message.contains("base64", ignoreCase = true),
            "The failure must identify base64 decoding as the cause; got: ${ex.message}"
        )
    }

    // MARK: - Wallet Adapter Removal Tests

    @Test
    fun testRemove_disconnectsTheAddressFromTheWalletAdapter() = runTest {
        val adapter = RecordingWalletAdapter(
            wallets = listOf(
                ConnectedWallet(OTHER_WALLET_ADDRESS, walletId = "lobstr", walletName = "LOBSTR")
            )
        )
        val manager = createManager(walletAdapter = adapter)

        manager.remove(OTHER_WALLET_ADDRESS)

        assertEquals(listOf(OTHER_WALLET_ADDRESS), adapter.disconnectedAddresses)
        assertFalse(adapter.disconnectAllCalled, "remove must not disconnect every wallet")
    }

    @Test
    fun testRemoveAll_disconnectsEveryWallet() = runTest {
        val adapter = RecordingWalletAdapter(
            wallets = listOf(
                ConnectedWallet(OTHER_WALLET_ADDRESS, walletId = "lobstr", walletName = "LOBSTR")
            )
        )
        val manager = createManager(walletAdapter = adapter)
        val keypair = KeyPair.random()
        val address = manager.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        manager.removeAll()

        assertTrue(adapter.disconnectAllCalled, "removeAll must disconnect every wallet")
        assertNull(manager.get(address), "removeAll must also clear in-memory keypairs")
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

}

// MARK: - Test Doubles

/** A G-address distinct from any randomly generated keypair used in these tests. */
private const val OTHER_WALLET_ADDRESS = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"

/**
 * An [ExternalWalletAdapter] whose connected wallets, signing outcome, and reported
 * signer address are configurable, and which records how the manager drove it.
 */
private class RecordingWalletAdapter(
    private val wallets: List<ConnectedWallet> = emptyList(),
    private val signFailure: Throwable? = null,
    private val reportSignerAddress: Boolean = true
) : ExternalWalletAdapter {

    var signAuthEntryCallCount = 0
        private set
    var lastSignOptions: SignAuthEntryOptions? = null
        private set
    var disconnectAllCalled = false
        private set
    val disconnectedAddresses = mutableListOf<String>()

    override suspend fun connect(): ConnectedWallet? = wallets.firstOrNull()

    override suspend fun disconnect() {
        disconnectAllCalled = true
    }

    override suspend fun disconnectByAddress(address: String) {
        disconnectedAddresses.add(address)
    }

    override fun canSignFor(address: String): Boolean = wallets.any { it.address == address }

    override fun getConnectedWallets(): List<ConnectedWallet> = wallets

    override fun getWalletForAddress(address: String): ConnectedWallet? =
        wallets.firstOrNull { it.address == address }

    override suspend fun signAuthEntry(
        preimageXdr: String,
        options: SignAuthEntryOptions?
    ): SignAuthEntryResult {
        signAuthEntryCallCount++
        lastSignOptions = options
        signFailure?.let { throw it }
        return SignAuthEntryResult(
            // base64 of "signature"
            signedAuthEntry = "c2lnbmF0dXJl",
            signerAddress = if (reportSignerAddress) options?.address else null
        )
    }
}
