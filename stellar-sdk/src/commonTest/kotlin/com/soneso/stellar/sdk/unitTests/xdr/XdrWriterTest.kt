package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.test.Test
import kotlin.test.assertEquals

/** Bytes [XdrWriter] emits and [XdrReader] reads, checked against literal big-endian encodings on every target. */
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
    fun testFloatUsesIeee754SingleFormatWithCanonicalNaN() {
        assertEquals("3fc00000", written { writeFloat(1.5f) })
        assertEquals("80000000", written { writeFloat(-0.0f) })
        assertEquals("7fc00000", written { writeFloat(Float.NaN) })
        assertEquals(1.5f, XdrReader(Util.hexToBytes("3fc00000")).readFloat())
        assertEquals(Int.MIN_VALUE, XdrReader(Util.hexToBytes("80000000")).readFloat().toRawBits())
        assertEquals(0x7fc00000, XdrReader(Util.hexToBytes("7fc00000")).readFloat().toRawBits())
    }

    @Test
    fun testDoubleUsesIeee754DoubleFormatWithCanonicalNaN() {
        assertEquals("3ff8000000000000", written { writeDouble(1.5) })
        assertEquals("8000000000000000", written { writeDouble(-0.0) })
        assertEquals("7ff8000000000000", written { writeDouble(Double.NaN) })
        assertEquals(1.5, XdrReader(Util.hexToBytes("3ff8000000000000")).readDouble())
        assertEquals(Long.MIN_VALUE, XdrReader(Util.hexToBytes("8000000000000000")).readDouble().toRawBits())
        assertEquals(0x7ff8000000000000, XdrReader(Util.hexToBytes("7ff8000000000000")).readDouble().toRawBits())
    }

    @Test
    fun testFiveByteOpaqueIsPaddedToEightBytes() {
        val value = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals("0102030405000000", written { writeFixedOpaque(value) })
        assertEquals("000000050102030405000000", written { writeVariableOpaque(value) })
    }

    @Test
    fun testFlushKeepsTheWrittenBytesAndLaterWritesAppend() {
        val writer = XdrWriter().apply { writeInt(1) }
        assertEquals("00000001", Util.bytesToHex(writer.toByteArray()))
        writer.flush()
        assertEquals("00000001", Util.bytesToHex(writer.toByteArray()))
        writer.writeInt(2)
        assertEquals("0000000100000002", Util.bytesToHex(writer.toByteArray()))
    }
}
