package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.test.Test
import kotlin.test.assertEquals

/** Bytes [XdrWriter] emits, checked against literal big-endian encodings on every target. */
class XdrWriterTest {

    private fun written(write: XdrWriter.() -> Unit): String = Util.bytesToHex(XdrWriter().apply(write).toByteArray())

    @Test
    fun testIntsAreWrittenBigEndianInTwosComplement() {
        assertEquals("ffffffff", written { writeInt(-1) })
        assertEquals("80000000", written { writeInt(Int.MIN_VALUE) })
        assertEquals("b2d05e00", written { writeUnsignedInt(3_000_000_000u) })
    }

    @Test
    fun testLongIsWrittenBigEndianInTwosComplement() {
        assertEquals("8000000000000000", written { writeLong(Long.MIN_VALUE) })
    }

    @Test
    fun testFloatIsWrittenInIeee754SingleFormatWithCanonicalNaN() {
        assertEquals("3fc00000", written { writeFloat(1.5f) })
        assertEquals("80000000", written { writeFloat(-0.0f) })
        assertEquals("7fc00000", written { writeFloat(Float.NaN) })
    }

    @Test
    fun testDoubleIsWrittenInIeee754DoubleFormatWithCanonicalNaN() {
        assertEquals("3ff8000000000000", written { writeDouble(1.5) })
        assertEquals("8000000000000000", written { writeDouble(-0.0) })
        assertEquals("7ff8000000000000", written { writeDouble(Double.NaN) })
    }

    @Test
    fun testFiveByteOpaqueIsPaddedToEightBytes() {
        val value = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals("0102030405000000", written { writeFixedOpaque(value) })
        assertEquals("000000050102030405000000", written { writeVariableOpaque(value) })
    }
}
