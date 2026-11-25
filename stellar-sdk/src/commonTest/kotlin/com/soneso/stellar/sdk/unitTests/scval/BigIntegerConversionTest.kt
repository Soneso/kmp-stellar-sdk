package com.soneso.stellar.sdk.unitTests.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.scval.bigIntegerToBytesSigned
import com.soneso.stellar.sdk.scval.bytesToBigIntegerSigned
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Two's complement conversion at the 16 and 32 bytes of the Soroban 128-bit and 256-bit
 * integers, checked against literal bytes on every target.
 */
class BigIntegerConversionTest {

    private class Vector(val decimal: String, val byteCount: Int, val hex: String)

    private val vectors = listOf(
        Vector("0", 16, "00000000000000000000000000000000"),
        Vector("1", 16, "00000000000000000000000000000001"),
        Vector("-1", 16, "ffffffffffffffffffffffffffffffff"),
        Vector("127", 16, "0000000000000000000000000000007f"),
        Vector("128", 16, "00000000000000000000000000000080"),
        Vector("-128", 16, "ffffffffffffffffffffffffffffff80"),
        Vector("-129", 16, "ffffffffffffffffffffffffffffff7f"),
        Vector("-170141183460469231731687303715884105728", 16, "80000000000000000000000000000000"),
        Vector("170141183460469231731687303715884105727", 16, "7fffffffffffffffffffffffffffffff"),
        Vector("0", 32, "0000000000000000000000000000000000000000000000000000000000000000"),
        Vector("1", 32, "0000000000000000000000000000000000000000000000000000000000000001"),
        Vector("-1", 32, "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
        Vector("127", 32, "000000000000000000000000000000000000000000000000000000000000007f"),
        Vector("128", 32, "0000000000000000000000000000000000000000000000000000000000000080"),
        Vector("-128", 32, "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff80"),
        Vector("-129", 32, "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
        Vector(
            "-170141183460469231731687303715884105728", 32,
            "ffffffffffffffffffffffffffffffff80000000000000000000000000000000"
        ),
        Vector(
            "170141183460469231731687303715884105727", 32,
            "000000000000000000000000000000007fffffffffffffffffffffffffffffff"
        ),
        Vector(
            "-57896044618658097711785492504343953926634992332820282019728792003956564819968", 32,
            "8000000000000000000000000000000000000000000000000000000000000000"
        ),
        Vector(
            "57896044618658097711785492504343953926634992332820282019728792003956564819967", 32,
            "7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        )
    )

    @Test
    fun testEmptyBytesAreRejected() {
        val error = assertFailsWith<IllegalArgumentException> { bytesToBigIntegerSigned(ByteArray(0)) }
        assertEquals("bytes must not be empty", error.message)
    }

    @Test
    fun testZeroByteCountIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> { bigIntegerToBytesSigned(BigInteger.ONE, 0) }
        assertEquals("byteCount must be positive, got 0", error.message)
    }

    @Test
    fun testNegativeByteCountIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> { bigIntegerToBytesSigned(BigInteger.ONE, -1) }
        assertEquals("byteCount must be positive, got -1", error.message)
    }

    @Test
    fun testValueWiderThanByteCountKeepsItsLowBytes() {
        // 257 is 0x0101 and -257 is 0xfeff in two's complement.
        assertEquals("01", Util.bytesToHex(bigIntegerToBytesSigned(BigInteger.parseString("257"), 1)))
        assertEquals("ff", Util.bytesToHex(bigIntegerToBytesSigned(BigInteger.parseString("-257"), 1)))
    }

    @Test
    fun testValuesEncodeToTheirLiteralBytes() {
        for (vector in vectors) {
            val bytes = bigIntegerToBytesSigned(BigInteger.parseString(vector.decimal), vector.byteCount)
            assertEquals(vector.hex, Util.bytesToHex(bytes), "${vector.decimal} in ${vector.byteCount} bytes")
        }
    }

    @Test
    fun testLiteralBytesDecodeToTheirValues() {
        for (vector in vectors) {
            val value = bytesToBigIntegerSigned(Util.hexToBytes(vector.hex))
            assertEquals(BigInteger.parseString(vector.decimal), value, vector.hex)
        }
    }
}
