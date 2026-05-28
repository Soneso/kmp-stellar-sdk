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
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalEd25519SignerAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZExternalSignerManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
}
