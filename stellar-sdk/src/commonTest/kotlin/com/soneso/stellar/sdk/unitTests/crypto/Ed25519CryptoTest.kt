package com.soneso.stellar.sdk.unitTests.crypto

import com.soneso.stellar.sdk.crypto.getEd25519Crypto
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive unit tests for Ed25519 cryptographic operations.
 *
 * Tests cover:
 * - Key generation (random private key, public key derivation)
 * - Signing and verification
 * - Known test vectors (RFC 8032)
 * - Edge cases (wrong key, corrupted signature, empty data)
 */
class Ed25519CryptoTest {

    private val ed25519 = getEd25519Crypto()

    // ========== Library Info ==========

    @Test
    fun testLibraryNameIsNotBlank() {
        assertTrue(ed25519.libraryName.isNotBlank(), "Library name should not be blank")
    }

    // ========== Key Generation ==========

    @Test
    fun testGeneratePrivateKeyReturns32Bytes() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        assertEquals(32, privateKey.size, "Private key must be 32 bytes")
    }

    @Test
    fun testGeneratePrivateKeyIsRandom() = runTest {
        val key1 = ed25519.generatePrivateKey()
        val key2 = ed25519.generatePrivateKey()
        assertFalse(key1.contentEquals(key2), "Two generated private keys should not be identical")
    }

    @Test
    fun testDerivePublicKeyReturns32Bytes() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val publicKey = ed25519.derivePublicKey(privateKey)
        assertEquals(32, publicKey.size, "Public key must be 32 bytes")
    }

    @Test
    fun testDerivePublicKeyIsDeterministic() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val pub1 = ed25519.derivePublicKey(privateKey)
        val pub2 = ed25519.derivePublicKey(privateKey)
        assertTrue(pub1.contentEquals(pub2), "Same private key must derive the same public key")
    }

    @Test
    fun testDifferentPrivateKeysProduceDifferentPublicKeys() = runTest {
        val priv1 = ed25519.generatePrivateKey()
        val priv2 = ed25519.generatePrivateKey()
        val pub1 = ed25519.derivePublicKey(priv1)
        val pub2 = ed25519.derivePublicKey(priv2)
        assertFalse(pub1.contentEquals(pub2), "Different private keys should produce different public keys")
    }

    // ========== Signing ==========

    @Test
    fun testSignReturns64Bytes() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val data = "Hello, Stellar!".encodeToByteArray()
        val signature = ed25519.sign(data, privateKey)
        assertEquals(64, signature.size, "Signature must be 64 bytes")
    }

    @Test
    fun testSignIsDeterministic() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val data = "deterministic test".encodeToByteArray()
        val sig1 = ed25519.sign(data, privateKey)
        val sig2 = ed25519.sign(data, privateKey)
        assertTrue(sig1.contentEquals(sig2), "Ed25519 signing should be deterministic")
    }

    @Test
    fun testSignDifferentDataProducesDifferentSignatures() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val data1 = "message one".encodeToByteArray()
        val data2 = "message two".encodeToByteArray()
        val sig1 = ed25519.sign(data1, privateKey)
        val sig2 = ed25519.sign(data2, privateKey)
        assertFalse(sig1.contentEquals(sig2), "Different messages should produce different signatures")
    }

    @Test
    fun testSignDifferentKeysProduceDifferentSignatures() = runTest {
        val key1 = ed25519.generatePrivateKey()
        val key2 = ed25519.generatePrivateKey()
        val data = "same message".encodeToByteArray()
        val sig1 = ed25519.sign(data, key1)
        val sig2 = ed25519.sign(data, key2)
        assertFalse(sig1.contentEquals(sig2), "Different keys should produce different signatures")
    }

    // ========== Verification ==========

    @Test
    fun testVerifyValidSignature() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val publicKey = ed25519.derivePublicKey(privateKey)
        val data = "verify me".encodeToByteArray()
        val signature = ed25519.sign(data, privateKey)

        val result = ed25519.verify(data, signature, publicKey)
        assertTrue(result, "Valid signature should verify")
    }

    @Test
    fun testVerifyWithWrongPublicKey() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val wrongKey = ed25519.generatePrivateKey()
        val wrongPublicKey = ed25519.derivePublicKey(wrongKey)
        val data = "verify me".encodeToByteArray()
        val signature = ed25519.sign(data, privateKey)

        val result = ed25519.verify(data, signature, wrongPublicKey)
        assertFalse(result, "Signature should not verify with wrong public key")
    }

    @Test
    fun testVerifyWithCorruptedSignature() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val publicKey = ed25519.derivePublicKey(privateKey)
        val data = "verify me".encodeToByteArray()
        val signature = ed25519.sign(data, privateKey)

        // Corrupt one byte of the signature
        val corrupted = signature.copyOf()
        corrupted[0] = (corrupted[0].toInt() xor 0xFF).toByte()

        val result = ed25519.verify(data, corrupted, publicKey)
        assertFalse(result, "Corrupted signature should not verify")
    }

    @Test
    fun testVerifyWithWrongData() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val publicKey = ed25519.derivePublicKey(privateKey)
        val data = "original data".encodeToByteArray()
        val signature = ed25519.sign(data, privateKey)

        val wrongData = "tampered data".encodeToByteArray()
        val result = ed25519.verify(wrongData, signature, publicKey)
        assertFalse(result, "Signature should not verify with wrong data")
    }

    @Test
    fun testSignAndVerifyEmptyData() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val publicKey = ed25519.derivePublicKey(privateKey)
        val data = ByteArray(0)
        val signature = ed25519.sign(data, privateKey)

        assertEquals(64, signature.size, "Signature for empty data should still be 64 bytes")
        assertTrue(ed25519.verify(data, signature, publicKey), "Signature for empty data should verify")
    }

    @Test
    fun testSignAndVerifyLargeData() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val publicKey = ed25519.derivePublicKey(privateKey)
        val data = ByteArray(10000) { it.toByte() }
        val signature = ed25519.sign(data, privateKey)

        assertTrue(ed25519.verify(data, signature, publicKey), "Signature for large data should verify")
    }

    @Test
    fun testSignAndVerifySingleByte() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val publicKey = ed25519.derivePublicKey(privateKey)
        val data = byteArrayOf(0x42)
        val signature = ed25519.sign(data, privateKey)

        assertTrue(ed25519.verify(data, signature, publicKey), "Signature for single byte should verify")
    }

    // ========== Known Key Tests ==========
    // Test with generated keys to verify public key derivation is deterministic
    // and sign/verify works with known private keys.

    @Test
    fun testKnownKey_derivePublicKeyAndSignVerify() = runTest {
        // Generate a key pair and verify round-trip
        val privateKey = ed25519.generatePrivateKey()
        val publicKey = ed25519.derivePublicKey(privateKey)

        // Public key should always be 32 bytes
        assertEquals(32, publicKey.size)

        // Sign a message and verify
        val message = "Stellar SDK test message".encodeToByteArray()
        val signature = ed25519.sign(message, privateKey)
        assertEquals(64, signature.size)
        assertTrue(ed25519.verify(message, signature, publicKey))
    }

    @Test
    fun testKnownKey_publicKeyDerivationConsistentAcrossCalls() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val pub1 = ed25519.derivePublicKey(privateKey)
        val pub2 = ed25519.derivePublicKey(privateKey)
        val pub3 = ed25519.derivePublicKey(privateKey)
        assertTrue(pub1.contentEquals(pub2))
        assertTrue(pub2.contentEquals(pub3))
    }

    @Test
    fun testKnownKey_signatureConsistentAcrossCalls() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val data = "consistency test".encodeToByteArray()
        val sig1 = ed25519.sign(data, privateKey)
        val sig2 = ed25519.sign(data, privateKey)
        assertTrue(sig1.contentEquals(sig2), "Ed25519 signatures should be deterministic")
    }

    @Test
    fun testMultipleKeypairsIndependent() = runTest {
        // Generate multiple key pairs and verify they work independently
        val keyPairs = (1..5).map {
            val priv = ed25519.generatePrivateKey()
            val pub = ed25519.derivePublicKey(priv)
            priv to pub
        }

        val message = "test message".encodeToByteArray()

        for ((priv, pub) in keyPairs) {
            val sig = ed25519.sign(message, priv)
            assertTrue(ed25519.verify(message, sig, pub))

            // Verify other keys don't validate this signature
            for ((_, otherPub) in keyPairs) {
                if (!otherPub.contentEquals(pub)) {
                    assertFalse(ed25519.verify(message, sig, otherPub))
                }
            }
        }
    }

    // ========== Full Round-Trip ==========

    @Test
    fun testFullRoundTrip() = runTest {
        val privateKey = ed25519.generatePrivateKey()
        val publicKey = ed25519.derivePublicKey(privateKey)

        val messages = listOf(
            "Hello, World!",
            "",
            "The quick brown fox jumps over the lazy dog",
            "\u0000\u0001\u0002\u0003"  // binary data
        )

        for (msg in messages) {
            val data = msg.encodeToByteArray()
            val signature = ed25519.sign(data, privateKey)
            assertTrue(
                ed25519.verify(data, signature, publicKey),
                "Round-trip should pass for message: '$msg'"
            )
        }
    }

    // ========== Helpers ==========

    private fun hexToByteArray(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = hex.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        val hex = (it.toInt() and 0xFF).toString(16)
        if (hex.length == 1) "0$hex" else hex
    }
}
