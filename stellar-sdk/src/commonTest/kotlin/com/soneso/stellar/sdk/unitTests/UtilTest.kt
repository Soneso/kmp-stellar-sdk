package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlin.test.*

class UtilTest {

    @Test
    fun testPaddedByteArrayWithBytes() {
        // Test padding shorter array
        val input = byteArrayOf(1, 2, 3)
        val result = Util.paddedByteArray(input, 6)

        assertEquals(6, result.size)
        assertEquals(1, result[0])
        assertEquals(2, result[1])
        assertEquals(3, result[2])
        assertEquals(0, result[3])
        assertEquals(0, result[4])
        assertEquals(0, result[5])
    }

    @Test
    fun testPaddedByteArrayExactLength() {
        val input = byteArrayOf(1, 2, 3, 4)
        val result = Util.paddedByteArray(input, 4)

        assertEquals(4, result.size)
        assertContentEquals(input, result)
    }

    @Test
    fun testPaddedByteArrayTruncation() {
        // When input is longer than desired length, only first N bytes are kept
        val input = byteArrayOf(1, 2, 3, 4, 5, 6)
        val result = Util.paddedByteArray(input, 4)

        assertEquals(4, result.size)
        assertEquals(1, result[0])
        assertEquals(2, result[1])
        assertEquals(3, result[2])
        assertEquals(4, result[3])
    }

    @Test
    fun testPaddedByteArrayEmpty() {
        val input = byteArrayOf()
        val result = Util.paddedByteArray(input, 4)

        assertEquals(4, result.size)
        assertContentEquals(byteArrayOf(0, 0, 0, 0), result)
    }

    @Test
    fun testPaddedByteArrayZeroLength() {
        val input = byteArrayOf(1, 2, 3)
        val result = Util.paddedByteArray(input, 0)

        assertEquals(0, result.size)
    }

    @Test
    fun testPaddedByteArrayWithString() {
        val result = Util.paddedByteArray("USD", 4)

        assertEquals(4, result.size)
        assertEquals('U'.code.toByte(), result[0])
        assertEquals('S'.code.toByte(), result[1])
        assertEquals('D'.code.toByte(), result[2])
        assertEquals(0, result[3])
    }

    @Test
    fun testPaddedByteArrayWithLongerString() {
        val result = Util.paddedByteArray("TESTASSET", 12)

        assertEquals(12, result.size)
        assertEquals('T'.code.toByte(), result[0])
        assertEquals('E'.code.toByte(), result[1])
        assertEquals('S'.code.toByte(), result[2])
        assertEquals('T'.code.toByte(), result[3])
        assertEquals('A'.code.toByte(), result[4])
        assertEquals('S'.code.toByte(), result[5])
        assertEquals('S'.code.toByte(), result[6])
        assertEquals('E'.code.toByte(), result[7])
        assertEquals('T'.code.toByte(), result[8])
        assertEquals(0, result[9])
        assertEquals(0, result[10])
        assertEquals(0, result[11])
    }

    @Test
    fun testPaddedByteArrayToString() {
        val input = byteArrayOf(
            'U'.code.toByte(),
            'S'.code.toByte(),
            'D'.code.toByte(),
            0,
            0,
            0
        )
        val result = Util.paddedByteArrayToString(input)

        assertEquals("USD", result)
    }

    @Test
    fun testPaddedByteArrayToStringNoNulls() {
        val input = byteArrayOf(
            'U'.code.toByte(),
            'S'.code.toByte(),
            'D'.code.toByte()
        )
        val result = Util.paddedByteArrayToString(input)

        assertEquals("USD", result)
    }

    @Test
    fun testPaddedByteArrayToStringEmpty() {
        val input = byteArrayOf()
        val result = Util.paddedByteArrayToString(input)

        assertEquals("", result)
    }

    @Test
    fun testPaddedByteArrayToStringOnlyNulls() {
        val input = byteArrayOf(0, 0, 0, 0)
        val result = Util.paddedByteArrayToString(input)

        assertEquals("", result)
    }

    @Test
    fun testPaddedByteArrayToStringNullInMiddle() {
        val input = byteArrayOf(
            'A'.code.toByte(),
            0,
            'B'.code.toByte()
        )
        val result = Util.paddedByteArrayToString(input)

        // Should stop at first null
        assertEquals("A", result)
    }

    @Test
    fun testRoundTripStringPadding() {
        val codes = listOf("A", "AB", "USD", "USDC", "ABCDE", "TESTASSET", "ABCDEFGHIJKL")

        for (code in codes) {
            val length = if (code.length <= 4) 4 else 12
            val padded = Util.paddedByteArray(code, length)
            val restored = Util.paddedByteArrayToString(padded)
            assertEquals(code, restored, "Round trip failed for code: $code")
        }
    }

    @Test
    fun testAssetCodePaddingCompat() {
        // Test asset code padding for AlphaNum4 (matches Java SDK behavior)
        val code4 = "USD"
        val padded4 = Util.paddedByteArray(code4, 4)
        assertEquals(4, padded4.size)
        assertEquals("USD", Util.paddedByteArrayToString(padded4))

        // Test asset code padding for AlphaNum12 (matches Java SDK behavior)
        val code12 = "TESTASSET"
        val padded12 = Util.paddedByteArray(code12, 12)
        assertEquals(12, padded12.size)
        assertEquals("TESTASSET", Util.paddedByteArrayToString(padded12))
    }
}
