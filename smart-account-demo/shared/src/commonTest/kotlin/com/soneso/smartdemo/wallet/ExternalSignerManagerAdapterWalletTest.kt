//
//  ExternalSignerManagerAdapterWalletTest.kt
//  KMP Stellar SDK - Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.smartdemo.wallet

import com.soneso.smartdemo.util.ExternalSignerManagerAdapter
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the wallet connector integration in [ExternalSignerManagerAdapter].
 *
 * Covers:
 * - canSignFor routing for wallet-connected addresses and keypair addresses
 * - signAuthEntry delegation to WalletConnector for wallet-connected addresses
 * - signAuthEntry keypair path when wallet connector is present but not for that address
 * - Error propagation from WalletConnector
 * - disconnect lifecycle
 */
class ExternalSignerManagerAdapterWalletTest {

    // MARK: - Mock WalletConnector

    private class MockWalletConnector : WalletConnector {
        var connectedAddr: String? = null
        var signResult: String? = null
        var signError: Throwable? = null
        var connectResult: WalletConnection? = null
        var networkPassphrase: String? = null

        val signAuthEntryCalls = mutableListOf<Pair<String, String>>() // preimage to address
        val disconnectCalls = mutableListOf<String>()

        override suspend fun connect(): WalletConnection? = connectResult

        override suspend fun disconnect(address: String) {
            disconnectCalls.add(address)
            if (connectedAddr == address) {
                connectedAddr = null
            }
        }

        override suspend fun signAuthEntry(authEntryPreimageXdr: String, address: String): String {
            signAuthEntryCalls.add(authEntryPreimageXdr to address)
            signError?.let { throw it }
            return signResult ?: throw WalletSigningException("No mock result configured")
        }

        override fun isConnected(address: String): Boolean = connectedAddr == address

        override fun getConnectedAddress(): String? = connectedAddr

        override suspend fun getNetworkPassphrase(): String? = networkPassphrase
    }

    // MARK: - Helpers

    companion object {
        /** A valid 56-character Stellar G-address used as a mock wallet-connected address.
         *  This is the Stellar testnet Friendbot address — chosen because it is well-known
         *  and guaranteed to be a valid G-address format with correct checksum. */
        private const val MOCK_WALLET_ADDRESS = "GAIH3ULLFQ4DGSECF2AR555KZ4KNDGEKN4AFI4SU2M7B43MGK3QJZNSR"
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private val testPreimage: String
        get() = kotlin.io.encoding.Base64.encode("test-preimage-data".encodeToByteArray())

    private fun createAdapter(): ExternalSignerManagerAdapter = ExternalSignerManagerAdapter()

    // MARK: - canSignFor Tests

    @Test
    fun testCanSignFor_walletConnectedAddress() = runTest {
        val adapter = createAdapter()
        val mock = MockWalletConnector()
        mock.connectedAddr = MOCK_WALLET_ADDRESS
        adapter.walletConnector = mock

        assertTrue(adapter.canSignFor(MOCK_WALLET_ADDRESS))
    }

    @Test
    fun testCanSignFor_keypairAddress() = runTest {
        val adapter = createAdapter()
        val keypair = KeyPair.random()
        val address = adapter.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        assertTrue(adapter.canSignFor(address))
    }

    @Test
    fun testCanSignFor_unknownAddress_returnsFalse() = runTest {
        val adapter = createAdapter()
        val unknownAddress = KeyPair.random().getAccountId()

        assertFalse(adapter.canSignFor(unknownAddress))
    }

    @Test
    fun testCanSignFor_unknownAddress_withWalletConnectorSet_returnsFalse() = runTest {
        val adapter = createAdapter()
        val mock = MockWalletConnector()
        mock.connectedAddr = MOCK_WALLET_ADDRESS
        adapter.walletConnector = mock

        // A different address — not connected by the wallet
        val differentAddress = KeyPair.random().getAccountId()
        assertFalse(adapter.canSignFor(differentAddress))
    }

    @Test
    fun testCanSignFor_noWalletConnector_returnsFalse() = runTest {
        val adapter = createAdapter()
        adapter.walletConnector = null

        assertFalse(adapter.canSignFor(MOCK_WALLET_ADDRESS))
    }

    // MARK: - signAuthEntry delegation to wallet

    @Test
    fun testSignAuthEntry_delegatesToWallet_forWalletConnectedAddress() = runTest {
        val adapter = createAdapter()
        val mock = MockWalletConnector()
        val walletAddress = MOCK_WALLET_ADDRESS
        mock.connectedAddr = walletAddress
        mock.signResult = "mockSignatureBase64"
        adapter.walletConnector = mock

        val result = adapter.signAuthEntry(
            preimageXdr = testPreimage,
            options = SignAuthEntryOptions(address = walletAddress)
        )

        assertEquals("mockSignatureBase64", result.signedAuthEntry)
        assertEquals(walletAddress, result.signerAddress)
        assertEquals(1, mock.signAuthEntryCalls.size)
        assertEquals(testPreimage, mock.signAuthEntryCalls[0].first)
        assertEquals(walletAddress, mock.signAuthEntryCalls[0].second)
    }

    @Test
    fun testSignAuthEntry_usesKeypair_notWallet_forKeypairAddress() = runTest {
        val adapter = createAdapter()
        val keypair = KeyPair.random()
        val keypairAddress = adapter.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        // Wallet is connected but for a different address
        val mock = MockWalletConnector()
        mock.connectedAddr = MOCK_WALLET_ADDRESS
        mock.signResult = "walletSignature"
        adapter.walletConnector = mock

        val result = adapter.signAuthEntry(
            preimageXdr = testPreimage,
            options = SignAuthEntryOptions(address = keypairAddress)
        )

        // Wallet must not have been called
        assertEquals(0, mock.signAuthEntryCalls.size)

        // Result must be a valid non-empty base64 signature
        assertNotNull(result.signedAuthEntry)
        assertTrue(result.signedAuthEntry.isNotEmpty())
        assertEquals(keypairAddress, result.signerAddress)
    }

    @Test
    fun testSignAuthEntry_throwsForUnknownAddress() = runTest {
        val adapter = createAdapter()

        assertFailsWith<IllegalStateException> {
            adapter.signAuthEntry(
                preimageXdr = testPreimage,
                options = SignAuthEntryOptions(address = MOCK_WALLET_ADDRESS)
            )
        }
    }

    @Test
    fun testSignAuthEntry_throwsWhenOptionsAddressIsNull() = runTest {
        val adapter = createAdapter()

        assertFailsWith<IllegalArgumentException> {
            adapter.signAuthEntry(
                preimageXdr = testPreimage,
                options = null
            )
        }
    }

    @Test
    fun testSignAuthEntry_propagatesWalletSigningException() = runTest {
        val adapter = createAdapter()
        val mock = MockWalletConnector()
        val walletAddress = MOCK_WALLET_ADDRESS
        mock.connectedAddr = walletAddress
        mock.signError = WalletSigningException("User rejected the signing request")
        adapter.walletConnector = mock

        val exception = assertFailsWith<WalletSigningException> {
            adapter.signAuthEntry(
                preimageXdr = testPreimage,
                options = SignAuthEntryOptions(address = walletAddress)
            )
        }

        assertEquals("User rejected the signing request", exception.message)
    }

    @Test
    fun testSignAuthEntry_propagatesArbitraryWalletErrors() = runTest {
        val adapter = createAdapter()
        val mock = MockWalletConnector()
        val walletAddress = MOCK_WALLET_ADDRESS
        mock.connectedAddr = walletAddress
        mock.signError = RuntimeException("Network timeout")
        adapter.walletConnector = mock

        assertFailsWith<RuntimeException> {
            adapter.signAuthEntry(
                preimageXdr = testPreimage,
                options = SignAuthEntryOptions(address = walletAddress)
            )
        }
    }

    // MARK: - disconnect Tests

    @Test
    fun testDisconnect_clearsWalletSession() = runTest {
        val adapter = createAdapter()
        val mock = MockWalletConnector()
        val walletAddress = MOCK_WALLET_ADDRESS
        mock.connectedAddr = walletAddress
        adapter.walletConnector = mock

        assertTrue(adapter.canSignFor(walletAddress))

        adapter.disconnect()

        assertTrue(mock.disconnectCalls.contains(walletAddress))
        assertFalse(adapter.canSignFor(walletAddress))
    }

    @Test
    fun testDisconnect_withNoWalletConnector_doesNotThrow() = runTest {
        val adapter = createAdapter()
        adapter.walletConnector = null

        // Must not throw
        adapter.disconnect()
    }

    @Test
    fun testDisconnect_clearsKeypairSigners() = runTest {
        val adapter = createAdapter()
        val keypair = KeyPair.random()
        val address = adapter.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        assertTrue(adapter.canSignFor(address))

        adapter.disconnect()

        assertFalse(adapter.canSignFor(address))
    }

    @Test
    fun testDisconnect_doesNotCallWalletWhenNotConnected() = runTest {
        val adapter = createAdapter()
        val mock = MockWalletConnector()
        mock.connectedAddr = null // no active wallet session
        adapter.walletConnector = mock

        adapter.disconnect()

        assertEquals(0, mock.disconnectCalls.size)
    }

    // MARK: - Wallet takes precedence over keypair

    @Test
    fun testSignAuthEntry_walletTakesPrecedence_overKeypair_forSameAddress() = runTest {
        // Edge case: both wallet and keypair exist for the same address.
        // The adapter checks wallet first; the wallet path must be taken.
        val adapter = createAdapter()

        val keypair = KeyPair.random()
        val address = adapter.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        val mock = MockWalletConnector()
        mock.connectedAddr = address
        mock.signResult = "walletSignatureTakesPrecedence"
        adapter.walletConnector = mock

        val result = adapter.signAuthEntry(
            preimageXdr = testPreimage,
            options = SignAuthEntryOptions(address = address)
        )

        assertEquals("walletSignatureTakesPrecedence", result.signedAuthEntry)
        assertEquals(1, mock.signAuthEntryCalls.size)
    }

    // MARK: - addFromSecret / canSignFor interaction

    @Test
    fun testAddFromSecret_derivesCorrectAddress() = runTest {
        val adapter = createAdapter()
        val keypair = KeyPair.random()
        val expectedAddress = keypair.getAccountId()
        val secretSeed = keypair.getSecretSeed()!!.concatToString()

        val returnedAddress = adapter.addFromSecret(secretSeed)

        assertEquals(expectedAddress, returnedAddress)
        assertTrue(adapter.canSignFor(returnedAddress))
    }

    @Test
    fun testRemove_preventsCanSignFor() = runTest {
        val adapter = createAdapter()
        val keypair = KeyPair.random()
        val address = adapter.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        assertTrue(adapter.canSignFor(address))
        adapter.remove(address)
        assertFalse(adapter.canSignFor(address))
    }

    @Test
    fun testRemoveAll_preventsCanSignForAllKeypairs() = runTest {
        val adapter = createAdapter()
        val kp1 = KeyPair.random()
        val kp2 = KeyPair.random()
        val addr1 = adapter.addFromSecret(kp1.getSecretSeed()!!.concatToString())
        val addr2 = adapter.addFromSecret(kp2.getSecretSeed()!!.concatToString())

        assertTrue(adapter.canSignFor(addr1))
        assertTrue(adapter.canSignFor(addr2))

        adapter.removeAll()

        assertFalse(adapter.canSignFor(addr1))
        assertFalse(adapter.canSignFor(addr2))
    }

    // MARK: - getConnectedWallets

    @Test
    fun testGetConnectedWallets_returnsEmptyList() = runTest {
        val adapter = createAdapter()
        val keypair = KeyPair.random()
        adapter.addFromSecret(keypair.getSecretSeed()!!.concatToString())

        // Keypair signers are not surfaced as ConnectedWallet objects
        assertTrue(adapter.getConnectedWallets().isEmpty())
    }

    // MARK: - connect / reconnect

    @Test
    fun testConnect_returnsNull() = runTest {
        val adapter = createAdapter()
        // connect() is not supported — must return null without throwing
        val result = adapter.connect()
        assertEquals(null, result)
    }

    @Test
    fun testReconnect_returnsNull() = runTest {
        val adapter = createAdapter()
        // reconnect() is not supported — must return null without throwing
        val result = adapter.reconnect("freighter")
        assertEquals(null, result)
    }
}
