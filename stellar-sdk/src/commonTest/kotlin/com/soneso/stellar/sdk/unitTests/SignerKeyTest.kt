package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlin.test.*

class SignerKeyTest {

    private val testPublicKey = ByteArray(32) { it.toByte() }
    private val testHash = ByteArray(32) { (it + 1).toByte() }
    private val testPayload = "test payload".encodeToByteArray()

    // Valid test account from KeyPairTest
    private val validAccountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"

    @Test
    fun testEd25519PublicKeySigner() {
        val signer = SignerKey.ed25519PublicKey(testPublicKey)

        assertTrue(signer is SignerKey.Ed25519PublicKey)
        assertTrue(signer.publicKey.contentEquals(testPublicKey))

        val encoded = signer.encodeSignerKey()
        assertTrue(encoded.startsWith("G"))

        // Test round-trip
        val decoded = SignerKey.fromEncodedSignerKey(encoded)
        assertTrue(decoded is SignerKey.Ed25519PublicKey)
        assertTrue(decoded.publicKey.contentEquals(testPublicKey))
    }

    @Test
    fun testEd25519PublicKeyFromEncodedString() {
        val signer = SignerKey.ed25519PublicKey(validAccountId)

        assertTrue(signer is SignerKey.Ed25519PublicKey)
        assertEquals(validAccountId, signer.encodeSignerKey())
    }

    @Test
    fun testEd25519PublicKeyInvalidSize() {
        assertFails {
            SignerKey.ed25519PublicKey(ByteArray(31))
        }

        assertFails {
            SignerKey.ed25519PublicKey(ByteArray(33))
        }
    }

    @Test
    fun testPreAuthTxSigner() {
        val signer = SignerKey.preAuthTx(testHash)

        assertTrue(signer is SignerKey.PreAuthTx)
        assertTrue(signer.hash.contentEquals(testHash))

        val encoded = signer.encodeSignerKey()
        assertTrue(encoded.startsWith("T"))

        // Test round-trip
        val decoded = SignerKey.fromEncodedSignerKey(encoded)
        assertTrue(decoded is SignerKey.PreAuthTx)
        assertTrue(decoded.hash.contentEquals(testHash))
    }

    @Test
    fun testPreAuthTxFromEncodedString() {
        // Generate a valid encoded preauth tx by first creating one from bytes
        val signer = SignerKey.preAuthTx(testHash)
        val encoded = signer.encodeSignerKey()

        // Now decode it
        val decoded = SignerKey.preAuthTx(encoded)

        assertTrue(decoded is SignerKey.PreAuthTx)
        assertEquals(encoded, decoded.encodeSignerKey())
    }

    @Test
    fun testPreAuthTxInvalidSize() {
        assertFails {
            SignerKey.preAuthTx(ByteArray(31))
        }

        assertFails {
            SignerKey.preAuthTx(ByteArray(33))
        }
    }

    @Test
    fun testHashXSigner() {
        val signer = SignerKey.hashX(testHash)

        assertTrue(signer is SignerKey.HashX)
        assertTrue(signer.hash.contentEquals(testHash))

        val encoded = signer.encodeSignerKey()
        assertTrue(encoded.startsWith("X"))

        // Test round-trip
        val decoded = SignerKey.fromEncodedSignerKey(encoded)
        assertTrue(decoded is SignerKey.HashX)
        assertTrue(decoded.hash.contentEquals(testHash))
    }

    @Test
    fun testHashXFromEncodedString() {
        // Generate a valid encoded hash-x by first creating one from bytes
        val signer = SignerKey.hashX(testHash)
        val encoded = signer.encodeSignerKey()

        // Now decode it
        val decoded = SignerKey.hashX(encoded)

        assertTrue(decoded is SignerKey.HashX)
        assertEquals(encoded, decoded.encodeSignerKey())
    }

    @Test
    fun testHashXInvalidSize() {
        assertFails {
            SignerKey.hashX(ByteArray(31))
        }

        assertFails {
            SignerKey.hashX(ByteArray(33))
        }
    }

    @Test
    fun testEd25519SignedPayload() {
        val signer = SignerKey.ed25519SignedPayload(testPublicKey, testPayload)

        assertTrue(signer is SignerKey.Ed25519SignedPayload)
        assertTrue(signer.ed25519PublicKey.contentEquals(testPublicKey))
        assertTrue(signer.payload.contentEquals(testPayload))

        val encoded = signer.encodeSignerKey()
        assertTrue(encoded.startsWith("P"))

        // Test round-trip
        val decoded = SignerKey.fromEncodedSignerKey(encoded)
        assertTrue(decoded is SignerKey.Ed25519SignedPayload)
        assertTrue(decoded.ed25519PublicKey.contentEquals(testPublicKey))
        assertTrue(decoded.payload.contentEquals(testPayload))
    }

    @Test
    fun testEd25519SignedPayloadFromEncodedPublicKey() {
        val signer = SignerKey.ed25519SignedPayload(validAccountId, testPayload)

        assertTrue(signer is SignerKey.Ed25519SignedPayload)
        assertEquals(validAccountId, signer.encodedEd25519PublicKey())
        assertTrue(signer.payload.contentEquals(testPayload))
    }

    @Test
    fun testEd25519SignedPayloadInvalidPublicKeySize() {
        assertFails {
            SignerKey.ed25519SignedPayload(ByteArray(31), testPayload)
        }

        assertFails {
            SignerKey.ed25519SignedPayload(ByteArray(33), testPayload)
        }
    }

    @Test
    fun testEd25519SignedPayloadInvalidPayloadSize() {
        // Empty payload should fail
        assertFails {
            SignerKey.ed25519SignedPayload(testPublicKey, ByteArray(0))
        }

        // Payload too large should fail (max is 64 bytes)
        assertFails {
            SignerKey.ed25519SignedPayload(testPublicKey, ByteArray(65))
        }

        // Max size should work
        SignerKey.ed25519SignedPayload(testPublicKey, ByteArray(64))
    }

    @Test
    fun testEd25519SignedPayloadMaxPayloadLength() {
        assertEquals(64, SignerKey.SIGNED_PAYLOAD_MAX_PAYLOAD_LENGTH)
    }

    @Test
    fun testXdrRoundTripEd25519() {
        val original = SignerKey.ed25519PublicKey(testPublicKey)
        val xdr = original.toXdr()
        val restored = SignerKey.fromXdr(xdr)

        assertTrue(restored is SignerKey.Ed25519PublicKey)
        assertTrue(restored.publicKey.contentEquals(testPublicKey))
    }

    @Test
    fun testXdrRoundTripPreAuthTx() {
        val original = SignerKey.preAuthTx(testHash)
        val xdr = original.toXdr()
        val restored = SignerKey.fromXdr(xdr)

        assertTrue(restored is SignerKey.PreAuthTx)
        assertTrue(restored.hash.contentEquals(testHash))
    }

    @Test
    fun testXdrRoundTripHashX() {
        val original = SignerKey.hashX(testHash)
        val xdr = original.toXdr()
        val restored = SignerKey.fromXdr(xdr)

        assertTrue(restored is SignerKey.HashX)
        assertTrue(restored.hash.contentEquals(testHash))
    }

    @Test
    fun testXdrRoundTripEd25519SignedPayload() {
        val original = SignerKey.ed25519SignedPayload(testPublicKey, testPayload)
        val xdr = original.toXdr()
        val restored = SignerKey.fromXdr(xdr)

        assertTrue(restored is SignerKey.Ed25519SignedPayload)
        assertTrue(restored.ed25519PublicKey.contentEquals(testPublicKey))
        assertTrue(restored.payload.contentEquals(testPayload))
    }

    @Test
    fun testEquality() {
        val signer1 = SignerKey.ed25519PublicKey(testPublicKey)
        val signer2 = SignerKey.ed25519PublicKey(testPublicKey)
        val signer3 = SignerKey.ed25519PublicKey(ByteArray(32) { 99.toByte() })

        assertEquals(signer1, signer2)
        assertNotEquals(signer1, signer3)
        assertEquals(signer1.hashCode(), signer2.hashCode())
    }

    @Test
    fun testToString() {
        val signer = SignerKey.ed25519PublicKey(testPublicKey)
        val str = signer.toString()

        assertTrue(str.contains("SignerKey"))
        assertTrue(str.contains("Ed25519"))
    }

    @Test
    fun testFromEncodedSignerKeyInvalidInput() {
        assertFails {
            SignerKey.fromEncodedSignerKey("INVALID")
        }

        assertFails {
            SignerKey.fromEncodedSignerKey("")
        }
    }

    @Test
    fun testDifferentSignerTypesNotEqual() {
        val ed25519: SignerKey = SignerKey.ed25519PublicKey(testPublicKey)
        val preAuthTx: SignerKey = SignerKey.preAuthTx(testPublicKey)
        val hashX: SignerKey = SignerKey.hashX(testPublicKey)

        assertNotEquals<SignerKey>(ed25519, preAuthTx)
        assertNotEquals<SignerKey>(ed25519, hashX)
        assertNotEquals<SignerKey>(preAuthTx, hashX)
    }

    // ========== Ed25519PublicKey equals(null) ==========

    @Test
    fun testEd25519PublicKeyEqualsNull() {
        val signer = SignerKey.ed25519PublicKey(testPublicKey)

        @Suppress("ReplaceCallWithBinaryOperator", "EqualsNullCall")
        assertFalse(signer.equals(null))
    }

    // ========== PreAuthTx equals/hashCode/toString ==========

    @Test
    fun testPreAuthTxEqualsAndHashCode() {
        val signer1 = SignerKey.preAuthTx(testHash)
        val signer2 = SignerKey.preAuthTx(testHash.copyOf())
        val signer3 = SignerKey.preAuthTx(ByteArray(32) { 99.toByte() })

        assertEquals(signer1, signer2)
        assertEquals(signer1.hashCode(), signer2.hashCode())
        assertNotEquals(signer1, signer3)

        @Suppress("ReplaceCallWithBinaryOperator")
        assertTrue(signer1.equals(signer1))

        @Suppress("ReplaceCallWithBinaryOperator", "EqualsNullCall")
        assertFalse(signer1.equals(null))
    }

    @Test
    fun testPreAuthTxToString() {
        val signer = SignerKey.preAuthTx(testHash)
        val str = signer.toString()

        assertTrue(str.contains("SignerKey.PreAuthTx"))
        assertTrue(str.contains(signer.encodeSignerKey()))
    }

    // ========== HashX equals/hashCode/toString ==========

    @Test
    fun testHashXEqualsAndHashCode() {
        val signer1 = SignerKey.hashX(testHash)
        val signer2 = SignerKey.hashX(testHash.copyOf())
        val signer3 = SignerKey.hashX(ByteArray(32) { 99.toByte() })

        assertEquals(signer1, signer2)
        assertEquals(signer1.hashCode(), signer2.hashCode())
        assertNotEquals(signer1, signer3)

        @Suppress("ReplaceCallWithBinaryOperator")
        assertTrue(signer1.equals(signer1))

        @Suppress("ReplaceCallWithBinaryOperator", "EqualsNullCall")
        assertFalse(signer1.equals(null))
    }

    @Test
    fun testHashXToString() {
        val signer = SignerKey.hashX(testHash)
        val str = signer.toString()

        assertTrue(str.contains("SignerKey.HashX"))
        assertTrue(str.contains(signer.encodeSignerKey()))
    }

    // ========== Ed25519SignedPayload equals/hashCode/toString ==========

    @Test
    fun testEd25519SignedPayloadEqualsAndHashCode() {
        val signer1 = SignerKey.ed25519SignedPayload(testPublicKey, testPayload)
        val signer2 = SignerKey.ed25519SignedPayload(testPublicKey.copyOf(), testPayload.copyOf())
        val signerDifferentKey = SignerKey.ed25519SignedPayload(ByteArray(32) { 99.toByte() }, testPayload)
        val signerDifferentPayload = SignerKey.ed25519SignedPayload(testPublicKey, "other payload".encodeToByteArray())

        assertEquals(signer1, signer2)
        assertEquals(signer1.hashCode(), signer2.hashCode())
        assertNotEquals(signer1, signerDifferentKey)
        assertNotEquals(signer1, signerDifferentPayload)

        @Suppress("ReplaceCallWithBinaryOperator")
        assertTrue(signer1.equals(signer1))

        @Suppress("ReplaceCallWithBinaryOperator", "EqualsNullCall")
        assertFalse(signer1.equals(null))

        @Suppress("EqualsBetweenInconvertibleTypes")
        assertFalse(signer1.equals(SignerKey.hashX(testHash)))
    }

    @Test
    fun testEd25519SignedPayloadToString() {
        val signer = SignerKey.ed25519SignedPayload(testPublicKey, testPayload)
        val str = signer.toString()

        assertTrue(str.contains("SignerKey.Ed25519SignedPayload"))
        assertTrue(str.contains(signer.encodeSignerKey()))
    }

    // ========== fromEncodedSignerKey: invalid embedded payload length ==========

    @Test
    fun testFromEncodedSignerKeyInvalidPayloadLengthThrows() {
        // Craft a signed-payload strkey whose embedded length field (bytes 32-35) does not
        // describe a valid payload size (must be 1..64), while the overall byte count still
        // satisfies StrKey's own 40-100 byte range so decoding succeeds up to that point.
        val raw = ByteArray(40)
        testPublicKey.copyInto(raw, 0)
        // Length field = 0, which is outside the valid 1..64 range.
        raw[32] = 0
        raw[33] = 0
        raw[34] = 0
        raw[35] = 0

        val encoded = StrKey.encodeSignedPayload(raw)

        val exception = assertFailsWith<IllegalArgumentException> {
            SignerKey.fromEncodedSignerKey(encoded)
        }
        assertTrue(exception.message!!.contains("Invalid payload length"))
    }

    @Test
    fun testFromEncodedSignerKeyPayloadLengthExceedsMaxThrows() {
        val raw = ByteArray(40)
        testPublicKey.copyInto(raw, 0)
        // Length field = 1000, which exceeds SIGNED_PAYLOAD_MAX_PAYLOAD_LENGTH (64).
        raw[32] = 0
        raw[33] = 0
        raw[34] = 0x03
        raw[35] = 0xE8.toByte()

        val encoded = StrKey.encodeSignedPayload(raw)

        val exception = assertFailsWith<IllegalArgumentException> {
            SignerKey.fromEncodedSignerKey(encoded)
        }
        assertTrue(exception.message!!.contains("Invalid payload length"))
    }
}
