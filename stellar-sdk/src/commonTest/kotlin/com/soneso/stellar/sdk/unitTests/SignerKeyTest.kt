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

    /**
     * Checks that [encoded] is refused as a signed payload for [rejection], the framing rule the
     * case is written to exercise, both by the codec and by [SignerKey.fromEncodedSignerKey],
     * which reports the rule rather than the refusal it gives a string it cannot place at all.
     */
    private fun assertSignedPayloadFramingRejection(encoded: String, rejection: String) {
        assertFalse(StrKey.isValidSignedPayload(encoded))
        val failure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeSignedPayload(encoded)
        }
        assertEquals(rejection, failure.message, "the rejection must name the rule broken")
        assertEquals(
            rejection, signerKeyRejection(encoded).message,
            "the signer key rejection must name the rule broken"
        )
    }

    @Test
    fun testFromEncodedSignerKeyInvalidPayloadLengthThrows() {
        // A P... strkey carrying a 32-byte ed25519 public key, a declared payload length of 0 and
        // four zero bytes. A signed payload declares at least one payload byte, so this string
        // describes no signer key. It is written out in full because the encoder does not produce
        // framing its own decoder rejects.
        assertSignedPayloadFramingRejection(
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAABO6A",
            "Invalid signed payload declared length, expected between 1 and 64 bytes, got 0"
        )
    }

    @Test
    fun testFromEncodedSignerKeyPayloadLengthExceedsMaxThrows() {
        // The same framing declaring 1000 payload bytes, which exceeds the 64 a signed payload
        // can carry and the 40 bytes this strkey holds.
        assertSignedPayloadFramingRejection(
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAPUAAAAAACOBO",
            "Invalid signed payload declared length, expected between 1 and 64 bytes, got 1000"
        )
    }

    @Test
    fun testFromEncodedSignerKeyDeclaredLengthLongerThanThePayloadPresentThrows() {
        // A 32-byte ed25519 public key, a declared payload length of 64 and four payload bytes.
        // The declared length is one a signed payload can carry, but these 40 bytes do not carry
        // it, so the payload the length names ends past the end of the decoded bytes.
        assertSignedPayloadFramingRejection(
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAABAACAQDAQ3PY",
            "Invalid signed payload size, a declared length of 64 requires 100 bytes, got 40"
        )
    }

    @Test
    fun testFromEncodedSignerKeyReEmitsEveryPayloadLengthUnchanged() {
        for (payloadLength in 1..SignerKey.SIGNED_PAYLOAD_MAX_PAYLOAD_LENGTH) {
            val payload = ByteArray(payloadLength) { (it + 1).toByte() }
            val encoded = SignerKey.ed25519SignedPayload(testPublicKey, payload).encodeSignerKey()

            val decoded = assertIs<SignerKey.Ed25519SignedPayload>(
                SignerKey.fromEncodedSignerKey(encoded),
                "payload length $payloadLength: must decode to a signed payload signer"
            )
            assertTrue(
                testPublicKey.contentEquals(decoded.ed25519PublicKey),
                "payload length $payloadLength: public key"
            )
            assertTrue(
                payload.contentEquals(decoded.payload),
                "payload length $payloadLength: payload"
            )
            assertEquals(
                encoded, decoded.encodeSignerKey(),
                "payload length $payloadLength: the re-emitted strkey must be the one decoded"
            )
        }
    }

    @Test
    fun testFromEncodedSignerKeyNamesTheInputItCannotPlace() {
        val unplaceable = listOf(
            // Nothing to decode, and a single character that names a type but carries no key.
            "",
            "P",
            // A signed payload strkey cut short: fewer characters than the shortest signed
            // payload has, so it describes no signer key rather than a malformed one.
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAABAACAQDAQ",
            // A signed payload strkey run past its end: more characters than the longest signed
            // payload has, so it describes no signer key rather than a malformed one.
            "P" + "A".repeat(200),
            // A valid account id with pad characters appended, and a string of pad characters
            // alone: a strkey is written without padding, so neither is one.
            "$validAccountId========",
            "========"
        )

        for (candidate in unplaceable) {
            val failure = signerKeyRejection(candidate)
            assertEquals(
                "Invalid encoded signer key: $candidate", failure.message,
                "the rejection must name the input it refused"
            )
        }
    }

    @Test
    fun testFromEncodedSignerKeyNamesTheRuleAMalformedSignedPayloadBroke() {
        val rejections = mapOf(
            // The three SEP-0023 invalid signed payload vectors: a declared length shorter than
            // the payload present, one longer than the payload present, and a payload written
            // without the padding that fills it to a four-byte boundary.
            ("PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAQACAQDAQ" +
                "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DUPB6IAAAAAAAAPM") to
                "Invalid signed payload size, a declared length of 32 requires 68 bytes, got 72",
            ("PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAOQCAQDAQ" +
                "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4Z2PQ") to
                "Invalid signed payload size, a declared length of 29 requires 68 bytes, got 64",
            ("PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAOQCAQDAQ" +
                "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DXFH6") to
                "Invalid signed payload size, a declared length of 29 requires 68 bytes, got 65",
            // 29 payload bytes followed by padding of 0xff.
            ("PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAOQCAQDAQ" +
                "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DX77776K34") to
                "Invalid signed payload padding, expected zero at index 65 after a declared " +
                "length of 29, got 255",
            // A signed payload strkey carrying a character the base32 alphabet does not hold.
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAABAACAQDAq3PY" to
                "Invalid base32 encoded string"
        )

        for ((candidate, rejection) in rejections) {
            val failure = signerKeyRejection(candidate)
            assertEquals(
                rejection, failure.message,
                "the rejection must name the rule broken for \"$candidate\""
            )
        }
    }

    /**
     * The failure [SignerKey.fromEncodedSignerKey] raises for [encoded], having checked that it is
     * the [IllegalArgumentException] the contract documents. A declared payload length the decoded
     * bytes do not carry is the input that could instead surface as an out-of-bounds read, so that
     * case is named on its own and a regression in it reports what broke.
     */
    private fun signerKeyRejection(encoded: String): IllegalArgumentException {
        val failure = assertFails { SignerKey.fromEncodedSignerKey(encoded) }
        assertFalse(
            failure is IndexOutOfBoundsException,
            "reading past the end of the decoded bytes escaped for \"$encoded\""
        )
        return assertIs<IllegalArgumentException>(
            failure,
            "the rejection must be the exception type the contract documents"
        )
    }
}
