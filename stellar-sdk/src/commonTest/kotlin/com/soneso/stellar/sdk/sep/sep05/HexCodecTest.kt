/*
 * Copyright 2025 Soneso
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soneso.stellar.sdk.sep.sep05

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for [HexCodec] encoding and decoding utilities.
 *
 * Tests cover:
 * - Empty input handling
 * - Single byte encoding/decoding
 * - Multi-byte encoding/decoding
 * - Case insensitivity for decoding
 * - Whitespace handling
 * - Invalid character detection
 * - Odd length string rejection
 */
class HexCodecTest {

    // ========== Encode Tests ==========

    @Test
    fun testEncodeEmpty() {
        val result = HexCodec.encode(ByteArray(0))
        assertEquals("", result)
    }

    @Test
    fun testEncodeSingleByteZero() {
        val result = HexCodec.encode(byteArrayOf(0x00))
        assertEquals("00", result)
    }

    @Test
    fun testEncodeSingleByteMax() {
        val result = HexCodec.encode(byteArrayOf(0xFF.toByte()))
        assertEquals("ff", result)
    }

    @Test
    fun testEncodeSingleByteMidRange() {
        val result = HexCodec.encode(byteArrayOf(0x7F))
        assertEquals("7f", result)
    }

    @Test
    fun testEncodeMultipleBytes() {
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val result = HexCodec.encode(bytes)
        assertEquals("deadbeef", result)
    }

    @Test
    fun testEncodeAllZeros() {
        val bytes = ByteArray(4) { 0x00 }
        val result = HexCodec.encode(bytes)
        assertEquals("00000000", result)
    }

    @Test
    fun testEncodeAllOnes() {
        val bytes = ByteArray(4) { 0xFF.toByte() }
        val result = HexCodec.encode(bytes)
        assertEquals("ffffffff", result)
    }

    @Test
    fun testEncodeOutputIsLowercase() {
        val bytes = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val result = HexCodec.encode(bytes)
        assertEquals("abcd", result)
        assertEquals(result, result.lowercase())
    }

    @Test
    fun testEncodeLargeArray() {
        val bytes = ByteArray(100) { it.toByte() }
        val result = HexCodec.encode(bytes)
        assertEquals(200, result.length)
        // Verify first few bytes
        assertEquals("00", result.substring(0, 2))
        assertEquals("01", result.substring(2, 4))
        assertEquals("0a", result.substring(20, 22)) // byte 10
    }

    // ========== Decode Tests ==========

    @Test
    fun testDecodeEmpty() {
        val result = HexCodec.decode("")
        assertContentEquals(ByteArray(0), result)
    }

    @Test
    fun testDecodeSingleByteZero() {
        val result = HexCodec.decode("00")
        assertContentEquals(byteArrayOf(0x00), result)
    }

    @Test
    fun testDecodeSingleByteMax() {
        val result = HexCodec.decode("ff")
        assertContentEquals(byteArrayOf(0xFF.toByte()), result)
    }

    @Test
    fun testDecodeMultipleBytes() {
        val result = HexCodec.decode("deadbeef")
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertContentEquals(expected, result)
    }

    @Test
    fun testDecodeUppercase() {
        val result = HexCodec.decode("DEADBEEF")
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertContentEquals(expected, result)
    }

    @Test
    fun testDecodeMixedCase() {
        val result = HexCodec.decode("DeAdBeEf")
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertContentEquals(expected, result)
    }

    @Test
    fun testDecodeWithSpaces() {
        val result = HexCodec.decode("de ad be ef")
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertContentEquals(expected, result)
    }

    @Test
    fun testDecodeWithTabs() {
        val result = HexCodec.decode("de\tad\tbe\tef")
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertContentEquals(expected, result)
    }

    @Test
    fun testDecodeWithNewlines() {
        val result = HexCodec.decode("de\nad\nbe\nef")
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertContentEquals(expected, result)
    }

    @Test
    fun testDecodeWithMixedWhitespace() {
        val result = HexCodec.decode("  de ad\t\nbe\tef  ")
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertContentEquals(expected, result)
    }

    @Test
    fun testDecodeWithLeadingTrailingWhitespace() {
        val result = HexCodec.decode("   deadbeef   ")
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertContentEquals(expected, result)
    }

    // ========== Invalid Input Tests ==========

    @Test
    fun testDecodeOddLengthThrows() {
        val exception = assertFailsWith<IllegalArgumentException> {
            HexCodec.decode("abc")
        }
        assertEquals("Hex string must have even length after removing whitespace, got 3", exception.message)
    }

    @Test
    fun testDecodeOddLengthWithWhitespaceThrows() {
        // "a bc" normalizes to "abc" which is odd length
        val exception = assertFailsWith<IllegalArgumentException> {
            HexCodec.decode("a bc")
        }
        assertEquals("Hex string must have even length after removing whitespace, got 3", exception.message)
    }

    @Test
    fun testDecodeInvalidCharacterG() {
        val exception = assertFailsWith<IllegalArgumentException> {
            HexCodec.decode("gg")
        }
        assertEquals("Invalid hex character: 'g' (code: 103)", exception.message)
    }

    @Test
    fun testDecodeInvalidCharacterZ() {
        val exception = assertFailsWith<IllegalArgumentException> {
            HexCodec.decode("zz")
        }
        assertEquals("Invalid hex character: 'z' (code: 122)", exception.message)
    }

    @Test
    fun testDecodeInvalidCharacterSymbol() {
        assertFailsWith<IllegalArgumentException> {
            HexCodec.decode("!@")
        }
    }

    @Test
    fun testDecodeInvalidCharacterMixedWithValid() {
        assertFailsWith<IllegalArgumentException> {
            HexCodec.decode("abcdefgh")
        }
    }

    // ========== Round-Trip Tests ==========

    @Test
    fun testRoundTripEmpty() {
        val original = ByteArray(0)
        val encoded = HexCodec.encode(original)
        val decoded = HexCodec.decode(encoded)
        assertContentEquals(original, decoded)
    }

    @Test
    fun testRoundTripSingleByte() {
        for (i in 0..255) {
            val original = byteArrayOf(i.toByte())
            val encoded = HexCodec.encode(original)
            val decoded = HexCodec.decode(encoded)
            assertContentEquals(original, decoded, "Round-trip failed for byte $i")
        }
    }

    @Test
    fun testRoundTripMultipleBytes() {
        val original = byteArrayOf(
            0x00, 0x01, 0x7F, 0x80.toByte(), 0xFE.toByte(), 0xFF.toByte()
        )
        val encoded = HexCodec.encode(original)
        val decoded = HexCodec.decode(encoded)
        assertContentEquals(original, decoded)
    }

    @Test
    fun testRoundTripKnownSeed() {
        // SEP-5 test vector seed
        val seedHex = "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497ee4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186"
        val decoded = HexCodec.decode(seedHex)
        assertEquals(64, decoded.size)
        val reencoded = HexCodec.encode(decoded)
        assertEquals(seedHex, reencoded)
    }

    // ========== Edge Cases ==========

    @Test
    fun testDecodeOnlyWhitespace() {
        val result = HexCodec.decode("   \t\n   ")
        assertContentEquals(ByteArray(0), result)
    }

    @Test
    fun testEncodeOutputLength() {
        // Output should always be exactly 2x input length
        for (size in listOf(1, 2, 10, 32, 64, 100)) {
            val bytes = ByteArray(size) { it.toByte() }
            val encoded = HexCodec.encode(bytes)
            assertEquals(size * 2, encoded.length, "Encoded length wrong for size $size")
        }
    }
}
