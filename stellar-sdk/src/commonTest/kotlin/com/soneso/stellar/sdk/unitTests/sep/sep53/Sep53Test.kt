// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep53

import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for SEP-53 (Message Signing) implementation.
 *
 * Test vectors are taken from the SEP-53 specification:
 * https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0053.md
 *
 * Secret seed: SAKICEVQLYWGSOJS4WW7HZJWAHZVEEBS527LHK5V4MLJALYKICQCJXMW
 * Account ID:  GBXFXNDLV4LSWA4VB7YIL5GBD7BVNR22SGBTDKMO2SBZZHDXSKZYCP7L
 */
@OptIn(ExperimentalEncodingApi::class)
class Sep53Test {

    companion object {
        private const val SECRET_SEED = "SAKICEVQLYWGSOJS4WW7HZJWAHZVEEBS527LHK5V4MLJALYKICQCJXMW"
        private const val ACCOUNT_ID = "GBXFXNDLV4LSWA4VB7YIL5GBD7BVNR22SGBTDKMO2SBZZHDXSKZYCP7L"

        private const val ASCII_MESSAGE = "Hello, World!"
        private const val ASCII_SIGNATURE_HEX =
            "7cee5d6d885752104c85eea421dfdcb95abf01f1271d11c4bec3fcbd7874dccd" +
            "6e2e98b97b8eb23b643cac4073bb77de5d07b0710139180ae9f3cbba78f2ba04"

        private const val JAPANESE_MESSAGE = "\u3053\u3093\u306b\u3061\u306f\u3001\u4e16\u754c\uff01"
        private const val JAPANESE_SIGNATURE_HEX =
            "083536eb95ecf32dce59b07fe7a1fd8cf814b2ce46f40d2a16e4ea1f6cecd980" +
            "e04e6fbef9d21f98011c785a81edb85f3776a6e7d942b435eb0adc07da4d4604"

        private const val BINARY_MESSAGE_BASE64 = "2zZDP1sa1BVBfLP7TeeMk3sUbaxAkUhBhDiNdrksaFo="
        private const val BINARY_SIGNATURE_HEX =
            "540d7eee179f370bf634a49c1fa9fe4a58e3d7990b0207be336c04edfcc539ff" +
            "8bd0c31bb2c0359b07c9651cb2ae104e4504657b5d17d43c69c7e50e23811b0d"

        /** Decodes a lowercase hex string to a byte array. */
        private fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Hex string must have even length" }
            return ByteArray(hex.length / 2) { i ->
                val high = hexCharToInt(hex[i * 2])
                val low = hexCharToInt(hex[i * 2 + 1])
                ((high shl 4) or low).toByte()
            }
        }

        /** Encodes a byte array to a lowercase hex string. */
        private fun bytesToHex(bytes: ByteArray): String {
            val chars = "0123456789abcdef".toCharArray()
            val result = StringBuilder(bytes.size * 2)
            for (byte in bytes) {
                val v = byte.toInt() and 0xFF
                result.append(chars[v shr 4])
                result.append(chars[v and 0x0F])
            }
            return result.toString()
        }

        private fun hexCharToInt(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw IllegalArgumentException("Invalid hex character: $c")
        }
    }

    // ---------------------------------------------------------------
    // A. Specification test vectors
    // ---------------------------------------------------------------

    @Test
    fun testSignAsciiMessage() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val signature = signer.signMessage(ASCII_MESSAGE.encodeToByteArray())
        assertEquals(ASCII_SIGNATURE_HEX, bytesToHex(signature))
    }

    @Test
    fun testVerifyAsciiMessage() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val signature = hexToBytes(ASCII_SIGNATURE_HEX)
        assertTrue(verifier.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), signature))
    }

    @Test
    fun testSignJapaneseMessage() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val signature = signer.signMessage(JAPANESE_MESSAGE.encodeToByteArray())
        assertEquals(JAPANESE_SIGNATURE_HEX, bytesToHex(signature))
    }

    @Test
    fun testVerifyJapaneseMessage() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val signature = hexToBytes(JAPANESE_SIGNATURE_HEX)
        assertTrue(verifier.verifyMessage(JAPANESE_MESSAGE.encodeToByteArray(), signature))
    }

    @Test
    fun testSignBinaryMessage() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val message = Base64.decode(BINARY_MESSAGE_BASE64)
        val signature = signer.signMessage(message)
        assertEquals(BINARY_SIGNATURE_HEX, bytesToHex(signature))
    }

    @Test
    fun testVerifyBinaryMessage() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val message = Base64.decode(BINARY_MESSAGE_BASE64)
        val signature = hexToBytes(BINARY_SIGNATURE_HEX)
        assertTrue(verifier.verifyMessage(message, signature))
    }

    // ---------------------------------------------------------------
    // B. String convenience method tests
    // ---------------------------------------------------------------

    @Test
    fun testSignMessageStringProducesSameSignatureAsByteArrayForAscii() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val fromString = signer.signMessage(ASCII_MESSAGE)
        val fromBytes = signer.signMessage(ASCII_MESSAGE.encodeToByteArray())
        assertTrue(fromString.contentEquals(fromBytes))
    }

    @Test
    fun testSignMessageStringProducesSameSignatureAsByteArrayForJapanese() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val fromString = signer.signMessage(JAPANESE_MESSAGE)
        val fromBytes = signer.signMessage(JAPANESE_MESSAGE.encodeToByteArray())
        assertTrue(fromString.contentEquals(fromBytes))
    }

    @Test
    fun testVerifyMessageStringProducesSameResultAsByteArrayForAscii() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val signature = hexToBytes(ASCII_SIGNATURE_HEX)
        val fromString = verifier.verifyMessage(ASCII_MESSAGE, signature)
        val fromBytes = verifier.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), signature)
        assertEquals(fromString, fromBytes)
    }

    // ---------------------------------------------------------------
    // C. Round-trip tests
    // ---------------------------------------------------------------

    @Test
    fun testSignAndVerifyWithRandomKeypair() = runTest {
        val keypair = KeyPair.random()
        val message = "Round-trip test with random keypair".encodeToByteArray()
        val signature = keypair.signMessage(message)
        assertTrue(keypair.verifyMessage(message, signature))
    }

    @Test
    fun testSignWithSeedKeypairVerifyWithPublicOnlyKeypair() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val message = "Cross-keypair verification test".encodeToByteArray()
        val signature = signer.signMessage(message)
        val verifier = KeyPair.fromAccountId(signer.getAccountId())
        assertTrue(verifier.verifyMessage(message, signature))
    }

    // ---------------------------------------------------------------
    // D. Failure tests
    // ---------------------------------------------------------------

    @Test
    fun testWrongMessageFailsVerification() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val signature = hexToBytes(ASCII_SIGNATURE_HEX)
        assertFalse(verifier.verifyMessage("Wrong message".encodeToByteArray(), signature))
    }

    @Test
    fun testWrongSignatureFailsVerification() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val wrongSignature = ByteArray(64) { 0x00 }
        assertFalse(verifier.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), wrongSignature))
    }

    @Test
    fun testWrongKeypairFailsVerification() = runTest {
        val otherKeypair = KeyPair.random()
        val signature = hexToBytes(ASCII_SIGNATURE_HEX)
        assertFalse(otherKeypair.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), signature))
    }

    @Test
    fun testTruncatedSignatureFailsVerification() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val truncated = hexToBytes(ASCII_SIGNATURE_HEX).copyOfRange(0, 32)
        assertFalse(verifier.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), truncated))
    }

    @Test
    fun testMinimalMalformedSignatureFailsVerification() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val singleByte = byteArrayOf(0x42)
        assertFalse(verifier.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), singleByte))
    }

    @Test
    fun testVerifyMessageStringWithWrongMessageReturnsFalse() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val signature = hexToBytes(ASCII_SIGNATURE_HEX)
        assertFalse(verifier.verifyMessage("Wrong message", signature))
    }

    @Test
    fun testBinarySignatureFailsForAsciiMessage() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val binarySignature = hexToBytes(BINARY_SIGNATURE_HEX)
        assertFalse(verifier.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), binarySignature))
    }

    @Test
    fun testEmptySignatureFailsVerification() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        assertFalse(verifier.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), ByteArray(0)))
    }

    @Test
    fun testSignMessageByteArrayWithoutPrivateKeyThrows() = runTest {
        val publicOnly = KeyPair.fromAccountId(ACCOUNT_ID)
        assertFailsWith<IllegalStateException> {
            publicOnly.signMessage("test".encodeToByteArray())
        }
    }

    @Test
    fun testSignMessageStringWithoutPrivateKeyThrows() = runTest {
        val publicOnly = KeyPair.fromAccountId(ACCOUNT_ID)
        assertFailsWith<IllegalStateException> {
            publicOnly.signMessage("test")
        }
    }

    // ---------------------------------------------------------------
    // E. Edge case tests
    // ---------------------------------------------------------------

    @Test
    fun testEmptyByteArraySignAndVerifyRoundTrip() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val emptyMessage = ByteArray(0)
        val signature = keypair.signMessage(emptyMessage)
        assertEquals(64, signature.size)
        assertTrue(keypair.verifyMessage(emptyMessage, signature))
    }

    @Test
    fun testEmptyStringSignAndVerifyRoundTrip() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val signature = keypair.signMessage("")
        assertEquals(64, signature.size)
        assertTrue(keypair.verifyMessage("", signature))
    }

    @Test
    fun testOversizedSignatureFailsVerification() = runTest {
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)
        val oversized = ByteArray(65) { 0x42 }
        assertFalse(verifier.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), oversized))
    }

    @Test
    fun testLargeMessageSignAndVerifyRoundTrip() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val largeMessage = ByteArray(100_000) { (it % 256).toByte() }
        val signature = keypair.signMessage(largeMessage)
        assertEquals(64, signature.size)
        assertTrue(keypair.verifyMessage(largeMessage, signature))
    }

    @Test
    fun testDeterministicSignature() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val sig1 = keypair.signMessage(ASCII_MESSAGE.encodeToByteArray())
        val sig2 = keypair.signMessage(ASCII_MESSAGE.encodeToByteArray())
        assertTrue(sig1.contentEquals(sig2))
    }

    @Test
    fun testBinaryWithNullBytesSignAndVerifyRoundTrip() = runTest {
        val keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val messageWithNulls = byteArrayOf(0x00, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00)
        val signature = keypair.signMessage(messageWithNulls)
        assertEquals(64, signature.size)
        assertTrue(keypair.verifyMessage(messageWithNulls, signature))
    }

    // ---------------------------------------------------------------
    // F. Encoding round-trip tests
    // ---------------------------------------------------------------

    @Test
    fun testSignAsciiMessageBase64EncodingRoundTrip() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)

        val signature = signer.signMessage(ASCII_MESSAGE.encodeToByteArray())

        val base64Encoded = Base64.encode(signature)
        assertEquals(
            "fO5dbYhXUhBMhe6kId/cuVq/AfEnHRHEvsP8vXh03M1uLpi5e46yO2Q8rEBzu3feXQewcQE5GArp88u6ePK6BA==",
            base64Encoded
        )

        val decoded = Base64.decode(base64Encoded)
        assertTrue(verifier.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), decoded))
    }

    @Test
    fun testSignAsciiMessageHexEncodingRoundTrip() = runTest {
        val signer = KeyPair.fromSecretSeed(SECRET_SEED)
        val verifier = KeyPair.fromAccountId(ACCOUNT_ID)

        val signature = signer.signMessage(ASCII_MESSAGE.encodeToByteArray())

        val hexEncoded = bytesToHex(signature)
        assertEquals(ASCII_SIGNATURE_HEX, hexEncoded)

        val decoded = hexToBytes(hexEncoded)
        assertTrue(verifier.verifyMessage(ASCII_MESSAGE.encodeToByteArray(), decoded))
    }
}
