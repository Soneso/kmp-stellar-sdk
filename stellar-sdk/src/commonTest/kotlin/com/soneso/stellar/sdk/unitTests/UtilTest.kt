package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
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

    // ========== hexToBytes: strict ASCII hex alphabet ==========

    @Test
    fun testHexToBytesRoundTrip() {
        val bytes = ByteArray(32) { it.toByte() }
        assertContentEquals(bytes, Util.hexToBytes(Util.bytesToHex(bytes)))
    }

    @Test
    fun testHexToBytesAcceptsMixedCase() {
        // The documented contract is case-insensitive: mixed-case valid hex must parse
        // to the same bytes as its lowercase form.
        val mixed = "0A1b2C3d4E5f6789AbCdEf0123456789abcdef0123456789ABCDEF0123456789"
        assertContentEquals(Util.hexToBytes(mixed.lowercase()), Util.hexToBytes(mixed))
        assertEquals(32, Util.hexToBytes(mixed).size)
    }

    @Test
    fun testHexToBytesEmptyStringYieldsEmptyArray() {
        assertContentEquals(ByteArray(0), Util.hexToBytes(""))
    }

    @Test
    fun testHexToBytesRejectsOddLength() {
        val exception = assertFailsWith<IllegalArgumentException> { Util.hexToBytes("abc") }
        assertTrue(
            exception.message?.contains("even length") ?: false,
            "Unexpected message: ${exception.message}"
        )
    }

    @Test
    fun testHexToBytesRejectsNegativeSignPairs() {
        // A per-pair radix parse reads "-1" as -1 and truncates it to 0xff, so a 64-character
        // string of sign pairs would decode to a full 32-byte id that the caller never wrote.
        val exception = assertFailsWith<IllegalArgumentException> {
            Util.hexToBytes("-1".repeat(32))
        }
        assertTrue(
            exception.message?.contains("0-9 and a-f") ?: false,
            "Unexpected message: ${exception.message}"
        )
    }

    @Test
    fun testHexToBytesRejectsPositiveSignPairs() {
        assertFailsWith<IllegalArgumentException> { Util.hexToBytes("+1".repeat(32)) }
    }

    @Test
    fun testHexToBytesRejectsArabicIndicDigits() {
        // U+0661 U+0662 are Unicode decimal digits that a radix parse accepts as 1 and 2.
        assertFailsWith<IllegalArgumentException> { Util.hexToBytes("١٢") }
    }

    @Test
    fun testHexToBytesRejectsFullwidthDigits() {
        // U+FF11 U+FF12 are the fullwidth forms of 1 and 2.
        assertFailsWith<IllegalArgumentException> { Util.hexToBytes("１２") }
    }

    @Test
    fun testHexToBytesRejectsFullwidthLetters() {
        // Fullwidth letters lowercase to fullwidth letters, so case folding alone does not
        // map U+FF21 U+FF22 onto the ASCII alphabet.
        assertFailsWith<IllegalArgumentException> { Util.hexToBytes("ＡＢ") }
    }

    @Test
    fun testHexToBytesRejectsNonHexLetters() {
        assertFailsWith<IllegalArgumentException> { Util.hexToBytes("0g") }
        assertFailsWith<IllegalArgumentException> { Util.hexToBytes("zz") }
    }

    @Test
    fun testHexToBytesRejectsWhitespaceAndPrefix() {
        assertFailsWith<IllegalArgumentException> { Util.hexToBytes("01 02") }
        assertFailsWith<IllegalArgumentException> { Util.hexToBytes("0x0102") }
    }

    @Test
    fun testHexToBytesErrorNamesTheOffendingCharacter() {
        val exception = assertFailsWith<IllegalArgumentException> { Util.hexToBytes("00zz") }
        assertTrue(
            exception.message?.contains("'z'") ?: false,
            "Message should name the offending character: ${exception.message}"
        )
    }

    @Test
    fun testIsFatal_cancellationException_isFatal() {
        assertTrue(isFatal(CancellationException("cancelled")))
    }

    @Test
    fun testIsFatal_plainThrowable_isNotFatal() {
        assertFalse(isFatal(Throwable("boom")))
    }

    @Test
    fun testIsFatal_error_isNotFatal() {
        // kotlin.Error is how the Kotlin/JS HTTP engine reports connectivity
        // failures; it must be handled, not rethrown
        assertFalse(isFatal(Error("Fail to fetch")))
    }

    @Test
    fun testIsFatal_exception_isNotFatal() {
        assertFalse(isFatal(Exception("boom")))
    }

    @Test
    fun testReadErrorBodyOrFallback_readSucceeds_returnsBodyText() = runTest {
        assertEquals("error detail", readErrorBodyOrFallback("fallback") { "error detail" })
    }

    @Test
    fun testReadErrorBodyOrFallback_readThrowsError_returnsFallback() = runTest {
        // kotlin.Error is how the Kotlin/JS HTTP engine reports connectivity failures:
        // non-fatal, so the read falls back instead of propagating.
        assertEquals("fallback", readErrorBodyOrFallback("fallback") { throw Error("Fail to fetch") })
    }

    @Test
    fun testReadErrorBodyOrFallback_readThrowsException_returnsFallback() = runTest {
        assertEquals("fallback", readErrorBodyOrFallback("fallback") { throw RuntimeException("boom") })
    }

    @Test
    fun testReadErrorBodyOrFallback_readCancelled_propagates() = runTest {
        assertFailsWith<CancellationException> {
            readErrorBodyOrFallback("fallback") { throw CancellationException("cancelled") }
        }
    }
}
