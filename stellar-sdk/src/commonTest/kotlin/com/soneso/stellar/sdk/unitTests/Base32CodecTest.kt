package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.BASE32_ALPHABET
import com.soneso.stellar.sdk.Base32Codec
import com.soneso.stellar.sdk.INVALID_BASE32_MESSAGE
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [Base32Codec], the base32 codec every strkey is written with.
 *
 * The codec runs from common code, so each of these cases holds on every target.
 */
class Base32CodecTest {

    private fun ascii(text: String): ByteArray = text.encodeToByteArray()

    // The test vectors of RFC 4648, section 10, as input and padded encoding.
    private val rfc4648Vectors = listOf(
        "" to "",
        "f" to "MY======",
        "fo" to "MZXQ====",
        "foo" to "MZXW6===",
        "foob" to "MZXW6YQ=",
        "fooba" to "MZXW6YTB",
        "foobar" to "MZXW6YTBOI======"
    )

    @Test
    fun testEncodeWritesTheRfc4648Vectors() {
        for ((input, expected) in rfc4648Vectors) {
            assertContentEquals(
                ascii(expected), Base32Codec.encode(ascii(input)),
                "BASE32(\"$input\") must be \"$expected\""
            )
        }
    }

    @Test
    fun testDecodeReadsTheRfc4648VectorsWithoutTheirPadding() {
        // A strkey is written without padding, and the pad character is outside the alphabet the
        // decoder accepts, so each vector is decoded in its unpadded form. The forms are 0, 2, 4,
        // 5, 7, 8 and 10 characters long, which covers every length remainder a base32 encoder
        // produces.
        for ((expected, padded) in rfc4648Vectors) {
            val unpadded = padded.trimEnd('=')
            assertContentEquals(
                ascii(expected), Base32Codec.decode(ascii(unpadded)),
                "\"$unpadded\" must decode to \"$expected\""
            )
        }
    }

    @Test
    fun testEncodeOfAnEmptyArrayIsEmptyAndDecodeOfAnEmptyArrayIsEmpty() {
        assertEquals(0, Base32Codec.encode(ByteArray(0)).size)
        assertEquals(0, Base32Codec.decode(ByteArray(0)).size)
        assertTrue(Base32Codec.isInAlphabet(ByteArray(0)), "an empty array holds no foreign byte")
    }

    @Test
    fun testEncodePadsToEightCharactersAndDecodeRestoresEveryLength() {
        // Every length from 0 to 100 bytes, the 35 bytes an account id or seed strkey encodes
        // among them. The byte values run through the whole range, so a lost high bit shows as
        // a changed result.
        for (length in 0..100) {
            val data = ByteArray(length) { index -> (index * 151 + length * 29 + 0x80).toByte() }
            val encoded = Base32Codec.encode(data)
            assertEquals(
                (length + 4) / 5 * 8, encoded.size,
                "$length bytes must encode to whole groups of eight characters"
            )

            val unpadded = encoded.copyOfRange(0, (length * 8 + 4) / 5)
            assertTrue(
                encoded.copyOfRange(unpadded.size, encoded.size).all { it == '='.code.toByte() },
                "$length bytes: every character after the data characters must be the pad"
            )
            assertTrue(Base32Codec.isInAlphabet(unpadded), "$length bytes: data characters only")
            assertContentEquals(data, Base32Codec.decode(unpadded), "$length bytes must round-trip")
        }
    }

    @Test
    fun testIsInAlphabetAcceptsExactlyTheThirtyTwoAlphabetBytes() {
        for (value in 0..255) {
            val byte = value.toByte()
            val inAlphabet = value < 0x80 && value.toChar() in BASE32_ALPHABET
            assertEquals(
                inAlphabet, Base32Codec.isInAlphabet(byteArrayOf(byte)),
                "byte 0x${value.toString(16)}"
            )
        }
        assertEquals(32, (0..255).count { Base32Codec.isInAlphabet(byteArrayOf(it.toByte())) })
    }

    @Test
    fun testIsInAlphabetReadsEveryByte() {
        // A foreign byte is found at the start, in the middle and at the end of the input.
        val valid = ascii(BASE32_ALPHABET)
        assertTrue(Base32Codec.isInAlphabet(valid))
        for (at in listOf(0, valid.size / 2, valid.size - 1)) {
            val candidate = valid.copyOf()
            candidate[at] = 'a'.code.toByte()
            assertFalse(Base32Codec.isInAlphabet(candidate), "a lowercase byte at index $at")
        }
    }

    @Test
    fun testDecodeRefusesEveryByteOutsideTheAlphabet() {
        // The foreign byte follows two alphabet characters, so a decoder that skipped it would
        // still have a byte to return. Lowercase letters, the pad character, whitespace and every
        // byte above the ASCII range are among the 224 values.
        var refused = 0
        for (value in 0..255) {
            if (Base32Codec.isInAlphabet(byteArrayOf(value.toByte()))) continue
            val failure = assertFailsWith<IllegalArgumentException>("byte 0x${value.toString(16)}") {
                Base32Codec.decode(byteArrayOf('M'.code.toByte(), 'Y'.code.toByte(), value.toByte()))
            }
            assertEquals(INVALID_BASE32_MESSAGE, failure.message, "byte 0x${value.toString(16)}")
            refused++
        }
        assertEquals(224, refused)
    }

    @Test
    fun testDecodeRefusesThePaddedAndTheLowercaseForm() {
        for (candidate in listOf("MY======", "my", "MZXW6YTBOI======", "mzxw6ytboi")) {
            val failure = assertFailsWith<IllegalArgumentException>("\"$candidate\"") {
                Base32Codec.decode(ascii(candidate))
            }
            assertEquals(INVALID_BASE32_MESSAGE, failure.message, "\"$candidate\"")
            assertFalse(Base32Codec.isInAlphabet(ascii(candidate)), "\"$candidate\"")
        }
    }
}
