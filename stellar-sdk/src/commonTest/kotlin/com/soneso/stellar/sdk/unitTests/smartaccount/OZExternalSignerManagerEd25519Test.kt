//
//  OZExternalSignerManagerEd25519Test.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.core.SignerException
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.ConnectedWallet
import com.soneso.stellar.sdk.smartaccount.oz.ExternalSignerType
import com.soneso.stellar.sdk.smartaccount.oz.ExternalWalletAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalEd25519SignerAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalSignerManager
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryOptions
import com.soneso.stellar.sdk.smartaccount.oz.SignAuthEntryResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Shared constants
// ---------------------------------------------------------------------------

private const val TEST_NETWORK_PASSPHRASE = "Test SDF Network ; September 2015"

/**
 * Well-formed C-strkey for the Ed25519 verifier contract used in unit tests.
 * Uses only the base32 alphabet (A-Z + 2-7).
 */
internal const val VERIFIER_A = "CAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD2KM"

/**
 * A second distinct verifier address for tests that exercise tuple-key semantics.
 * Uses only the base32 alphabet (A-Z + 2-7).
 */
internal const val VERIFIER_B = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"

// ---------------------------------------------------------------------------
// Mock adapters
// ---------------------------------------------------------------------------

/**
 * Adapter that reports it can sign for every (verifierAddress, publicKey) pair
 * and delegates signing to the provided keypair.
 */
private class AlwaysSignAdapter(private val keypair: KeyPair) : OZExternalEd25519SignerAdapter {
    override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean = true

    override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray =
        keypair.sign(authDigest)
}

/**
 * Adapter that always reports it cannot sign for any (verifierAddress, publicKey) pair.
 * Forces the fallback to the in-memory keypair registry.
 */
private class NeverSignAdapter : OZExternalEd25519SignerAdapter {
    override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean = false

    override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray =
        throw UnsupportedOperationException("NeverSignAdapter.signAuthDigest must never be called")
}

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

private fun createManager(): OZExternalSignerManager =
    OZExternalSignerManager(networkPassphrase = TEST_NETWORK_PASSPHRASE)

/**
 * Unit tests for the Ed25519 signer surface on [OZExternalSignerManager].
 *
 * Covers registration, lookup, signing, removal, and adapter precedence rules for
 * the Ed25519 external-signer path.
 */
class OZExternalSignerManagerEd25519Test {

    // ========================================================================
    // addEd25519FromRawKey
    // ========================================================================

    @Test
    fun test_addEd25519FromRawKey_validBytes_storesKeypairAndReturnsPublicKey() = runTest {
        val manager = createManager()
        val rawSeed = ByteArray(32) { it.toByte() }

        val publicKey = manager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        assertEquals(32, publicKey.size)
        assertTrue(
            manager.canSignEd25519For(VERIFIER_A, publicKey),
            "canSignEd25519For must return true for the registered (verifier, publicKey) pair"
        )
    }

    @Test
    fun test_addEd25519FromRawKey_wrongLength_throwsValidation() = runTest {
        val manager = createManager()

        assertFailsWith<ValidationException.InvalidInput> {
            manager.addEd25519FromRawKey(ByteArray(16) { it.toByte() }, VERIFIER_A)
        }

        assertFailsWith<ValidationException.InvalidInput> {
            manager.addEd25519FromRawKey(ByteArray(33) { it.toByte() }, VERIFIER_A)
        }
    }

    @Test
    fun test_addEd25519FromRawKey_sameKeyTwoVerifiers_storedAsDistinctEntries() = runTest {
        val manager = createManager()
        val rawSeed = ByteArray(32) { it.toByte() }

        val pk1 = manager.addEd25519FromRawKey(rawSeed, VERIFIER_A)
        val pk2 = manager.addEd25519FromRawKey(rawSeed, VERIFIER_B)

        // Both derived public keys are equal (same seed), but stored under distinct tuple keys.
        assertContentEquals(pk1, pk2)
        assertTrue(
            manager.canSignEd25519For(VERIFIER_A, pk1),
            "Entry under VERIFIER_A must be reachable"
        )
        assertTrue(
            manager.canSignEd25519For(VERIFIER_B, pk2),
            "Entry under VERIFIER_B must be reachable"
        )
    }

    // ========================================================================
    // canSignEd25519For
    // ========================================================================

    @Test
    fun test_canSignEd25519For_registered_returnsTrue() = runTest {
        val manager = createManager()
        val rawSeed = ByteArray(32) { (it + 1).toByte() }
        val publicKey = manager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        assertTrue(manager.canSignEd25519For(VERIFIER_A, publicKey))
    }

    @Test
    fun test_canSignEd25519For_unregistered_returnsFalse() {
        val manager = createManager()
        val randomKey = ByteArray(32) { (it + 99).toByte() }

        assertFalse(manager.canSignEd25519For(VERIFIER_A, randomKey))
    }

    // ========================================================================
    // signEd25519AuthDigest
    // ========================================================================

    @Test
    fun test_signEd25519AuthDigest_registered_returnsValidSignature() = runTest {
        val manager = createManager()
        val rawSeed = ByteArray(32) { (it + 2).toByte() }
        val publicKey = manager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        val authDigest = ByteArray(32) { ((it * 7) and 0xFF).toByte() }
        val signature = manager.signEd25519AuthDigest(VERIFIER_A, publicKey, authDigest)

        assertEquals(64, signature.size)

        // Verify the signature locally using the public key only.
        val verifier = KeyPair.fromPublicKey(publicKey)
        assertTrue(
            verifier.verify(authDigest, signature),
            "Signature produced by signEd25519AuthDigest must verify under the registered public key"
        )
    }

    @Test
    fun test_signEd25519AuthDigest_unregistered_throwsValidation() = runTest {
        val manager = createManager()
        val randomKey = ByteArray(32) { (it + 77).toByte() }
        val authDigest = ByteArray(32)

        assertFailsWith<ValidationException.InvalidInput> {
            manager.signEd25519AuthDigest(VERIFIER_A, randomKey, authDigest)
        }
    }

    // ========================================================================
    // removeEd25519
    // ========================================================================

    @Test
    fun test_removeEd25519_clearsRegistration() = runTest {
        val manager = createManager()
        val rawSeed = ByteArray(32) { (it + 3).toByte() }
        val publicKey = manager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        assertTrue(manager.canSignEd25519For(VERIFIER_A, publicKey))

        manager.removeEd25519(VERIFIER_A, publicKey)

        assertFalse(
            manager.canSignEd25519For(VERIFIER_A, publicKey),
            "canSignEd25519For must return false after removeEd25519"
        )
    }

    // ========================================================================
    // Adapter precedence
    // ========================================================================

    @Test
    fun test_ed25519Adapter_takesPrecedenceForCanSignForTrue() = runTest {
        val manager = createManager()
        val keypair = KeyPair.random()
        val publicKey = keypair.getPublicKey()

        // Adapter always claims it can sign — no in-memory keypair registered.
        manager.ed25519Adapter = AlwaysSignAdapter(keypair)

        assertTrue(
            manager.canSignEd25519For(VERIFIER_A, publicKey),
            "canSignEd25519For must return true when the adapter claims it can sign"
        )

        val authDigest = ByteArray(32) { (it and 0xFF).toByte() }
        val signature = manager.signEd25519AuthDigest(VERIFIER_A, publicKey, authDigest)

        assertEquals(64, signature.size)
        val verifier = KeyPair.fromPublicKey(publicKey)
        assertTrue(
            verifier.verify(authDigest, signature),
            "Signature from adapter must verify under the known public key"
        )
    }

    @Test
    fun test_ed25519Adapter_falsyAdapterFallsBackToInProcessKeypair() = runTest {
        val manager = createManager()
        val rawSeed = ByteArray(32) { (it + 4).toByte() }
        val publicKey = manager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        // Adapter reports it cannot sign for any key.
        manager.ed25519Adapter = NeverSignAdapter()

        // canSignEd25519For still returns true via the in-memory fallback.
        assertTrue(
            manager.canSignEd25519For(VERIFIER_A, publicKey),
            "canSignEd25519For must return true via in-memory fallback when adapter returns false"
        )

        val authDigest = ByteArray(32) { ((it + 3) and 0xFF).toByte() }
        val signature = manager.signEd25519AuthDigest(VERIFIER_A, publicKey, authDigest)

        val verifier = KeyPair.fromPublicKey(publicKey)
        assertTrue(
            verifier.verify(authDigest, signature),
            "Signature produced by in-memory keypair fallback must verify"
        )
    }

    // ========================================================================
    // removeAll sweeps Ed25519 registrations
    // ========================================================================

    // ========================================================================
    // signEd25519AuthDigest — adapter throws exception → wrapped as SigningFailed
    // ========================================================================

    @Test
    fun test_signEd25519AuthDigest_adapterThrowsException_wrapsAsSigningFailed() = runTest {
        val manager = createManager()
        val rawSeed = ByteArray(32) { (it + 8).toByte() }
        val publicKey = manager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        // Replace the in-memory signer with an adapter that claims it can sign but throws.
        manager.ed25519Adapter = object : OZExternalEd25519SignerAdapter {
            override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean = true
            override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray =
                throw RuntimeException("Hardware wallet disconnected")
        }

        val authDigest = ByteArray(32) { it.toByte() }

        assertFailsWith<TransactionException.SigningFailed> {
            manager.signEd25519AuthDigest(VERIFIER_A, publicKey, authDigest)
        }
    }

    // ========================================================================
    // signEd25519AuthDigest — adapter returns false → in-memory fallback path
    // ========================================================================

    @Test
    fun test_signEd25519AuthDigest_adapterReturnsFalseForCanSignFor_fallsBackToInMemory() = runTest {
        val manager = createManager()
        val rawSeed = ByteArray(32) { (it + 9).toByte() }
        val publicKey = manager.addEd25519FromRawKey(rawSeed, VERIFIER_A)

        // NeverSignAdapter reports false → in-memory keypair must be used.
        manager.ed25519Adapter = NeverSignAdapter()

        val authDigest = ByteArray(32) { ((it + 5) and 0xFF).toByte() }
        val signature = manager.signEd25519AuthDigest(VERIFIER_A, publicKey, authDigest)

        assertEquals(64, signature.size)
        val verifier = KeyPair.fromPublicKey(publicKey)
        assertTrue(
            verifier.verify(authDigest, signature),
            "Signature produced via in-memory fallback must verify under the registered public key"
        )
    }

    // ========================================================================
    // signEd25519AuthDigest — no adapter, no in-memory keypair → ValidationException
    // ========================================================================

    @Test
    fun test_signEd25519AuthDigest_neitherRegistered_throwsValidation() = runTest {
        val manager = createManager()
        val unregisteredKey = ByteArray(32) { (it + 55).toByte() }
        val authDigest = ByteArray(32)

        assertFailsWith<ValidationException.InvalidInput> {
            manager.signEd25519AuthDigest(VERIFIER_A, unregisteredKey, authDigest)
        }
    }

    // ========================================================================
    // addEd25519FromRawKey — verifier address is stored per-tuple (no format check on verifier)
    // ========================================================================

    @Test
    fun test_addEd25519FromRawKey_anyStringVerifier_storesAndRetrieves() = runTest {
        // addEd25519FromRawKey stores the verifier address as-is in the composite key;
        // it does not validate the C-strkey format. Any non-null string is accepted.
        val manager = createManager()
        val rawSeed = ByteArray(32) { (it + 10).toByte() }

        val publicKey = manager.addEd25519FromRawKey(rawSeed, "arbitrary-verifier-string")

        assertEquals(32, publicKey.size)
        assertTrue(
            manager.canSignEd25519For("arbitrary-verifier-string", publicKey),
            "canSignEd25519For must return true for the tuple stored under the arbitrary verifier key"
        )
    }

    // ========================================================================
    // removeAll sweeps Ed25519 registrations
    // ========================================================================

    @Test
    fun test_removeAll_clearsEd25519RegistrationsAlongsideWalletSigners() = runTest {
        val manager = createManager()

        // Register a wallet signer.
        val walletKeypair = KeyPair.random()
        val walletAddress = manager.addFromSecret(walletKeypair.getSecretSeed()!!.concatToString())

        // Register an Ed25519 signer.
        val ed25519Seed = ByteArray(32) { (it + 5).toByte() }
        val ed25519PublicKey = manager.addEd25519FromRawKey(ed25519Seed, VERIFIER_A)

        assertTrue(manager.canSignFor(walletAddress))
        assertTrue(manager.canSignEd25519For(VERIFIER_A, ed25519PublicKey))

        manager.removeAll()

        assertFalse(manager.canSignFor(walletAddress))
        assertFalse(manager.canSignEd25519For(VERIFIER_A, ed25519PublicKey))
    }

    // ========================================================================
    // addFromWallet — connect() returns null (user cancelled)
    // ========================================================================

    @Test
    fun test_addFromWallet_adapterConnectReturnsNull_returnsNull() = runTest {
        val nullConnectAdapter = object : ExternalWalletAdapter {
            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean = false
            override fun getConnectedWallets(): List<ConnectedWallet> = emptyList()
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        val manager = OZExternalSignerManager(
            networkPassphrase = TEST_NETWORK_PASSPHRASE,
            walletAdapter = nullConnectAdapter
        )

        val result = manager.addFromWallet()
        assertNull(result, "addFromWallet must return null when adapter.connect() returns null")
    }

    // ========================================================================
    // addFromWallet — connect() succeeds, no storage (no persistence attempt)
    // ========================================================================

    @Test
    fun test_addFromWallet_successfulConnect_noStorage_returnsWallet() = runTest {
        val expectedWallet = ConnectedWallet(
            address = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN",
            walletId = "freighter",
            walletName = "Freighter"
        )
        val connectAdapter = object : ExternalWalletAdapter {
            override suspend fun connect(): ConnectedWallet = expectedWallet
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean = address == expectedWallet.address
            override fun getConnectedWallets(): List<ConnectedWallet> = listOf(expectedWallet)
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        // No walletConnectionStorage — saves nothing, but must still return the wallet.
        val manager = OZExternalSignerManager(
            networkPassphrase = TEST_NETWORK_PASSPHRASE,
            walletAdapter = connectAdapter,
            walletConnectionStorage = null
        )

        val result = manager.addFromWallet()
        assertNotNull(result, "addFromWallet must return the connected wallet")
        assertEquals(expectedWallet.address, result.address)
        assertEquals(expectedWallet.walletId, result.walletId)
        assertEquals(expectedWallet.walletName, result.walletName)
    }

    // ========================================================================
    // get() — wallet adapter path (no keypair for address, adapter has it)
    // ========================================================================

    @Test
    fun test_get_walletAdapterAddress_returnsWalletInfo() = runTest {
        val walletAddress = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN"
        val wallet = ConnectedWallet(
            address = walletAddress,
            walletId = "lobstr",
            walletName = "LOBSTR"
        )
        val adapter = object : ExternalWalletAdapter {
            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean = address == walletAddress
            override fun getConnectedWallets(): List<ConnectedWallet> = listOf(wallet)
            override fun getWalletForAddress(address: String): ConnectedWallet? =
                if (address == walletAddress) wallet else null
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        val manager = OZExternalSignerManager(
            networkPassphrase = TEST_NETWORK_PASSPHRASE,
            walletAdapter = adapter
        )

        val info = manager.get(walletAddress)
        assertNotNull(info, "get() must return wallet info for a known wallet address")
        assertEquals(ExternalSignerType.WALLET, info.type)
        assertEquals(walletAddress, info.address)
        assertEquals("LOBSTR", info.walletName)
        assertEquals("lobstr", info.walletId)
    }

    // ========================================================================
    // getAll() — wallet adapter path returns entries not already in keypairSigners
    // ========================================================================

    @Test
    fun test_getAll_walletAdapterWithoutKeypairOverlap_includesWalletSigners() = runTest {
        val walletAddress = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN"
        val wallet = ConnectedWallet(
            address = walletAddress,
            walletId = "freighter",
            walletName = "Freighter"
        )
        val adapter = object : ExternalWalletAdapter {
            override suspend fun connect(): ConnectedWallet? = null
            override suspend fun disconnect() {}
            override fun canSignFor(address: String): Boolean = address == walletAddress
            override fun getConnectedWallets(): List<ConnectedWallet> = listOf(wallet)
            override suspend fun signAuthEntry(
                preimageXdr: String,
                options: SignAuthEntryOptions?
            ): SignAuthEntryResult = throw UnsupportedOperationException()
        }

        val manager = OZExternalSignerManager(
            networkPassphrase = TEST_NETWORK_PASSPHRASE,
            walletAdapter = adapter
        )

        val all = manager.getAll()
        assertEquals(1, all.size, "getAll() must include the wallet signer")
        assertEquals(ExternalSignerType.WALLET, all[0].type)
        assertEquals(walletAddress, all[0].address)
        assertEquals("Freighter", all[0].walletName)
    }
}
