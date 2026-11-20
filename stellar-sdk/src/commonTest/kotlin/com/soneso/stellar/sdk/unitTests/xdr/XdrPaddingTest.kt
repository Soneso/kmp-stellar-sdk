package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XdrPaddingTest {
    private val sentinel = byteArrayOf(1, 2, 3, 4)

    private val readString: (XdrReader, Int) -> ByteArray = { reader, _ -> reader.readString().encodeToByteArray() }
    private val readOpaque: (XdrReader, Int) -> ByteArray = { reader, _ -> reader.readVariableOpaque() }
    private val readFixed: (XdrReader, Int) -> ByteArray = { reader, size -> reader.readFixedOpaque(size) }

    private fun input(size: Int, variable: Boolean): ByteArray =
        (if (variable) byteArrayOf(0, 0, 0, size.toByte()) else byteArrayOf()) +
            ByteArray(size) { 65 } + ByteArray((4 - size % 4) % 4)

    private fun accepts(read: (XdrReader, Int) -> ByteArray, variable: Boolean = true) {
        for (size in 1..4) {
            val reader = XdrReader(input(size, variable) + sentinel)
            assertContentEquals(ByteArray(size) { 65 }, read(reader, size))
            assertEquals(0x01020304, reader.readInt())
            assertContentEquals(ByteArray(size) { 65 }, read(XdrReader(input(size, variable)), size))
        }
    }

    private fun rejectsNonzero(read: (XdrReader, Int) -> ByteArray, variable: Boolean = true) {
        for (size in 1..3) {
            val bytes = input(size, variable)
            val start = (if (variable) 4 else 0) + size
            for (offset in start until bytes.size) {
                for (value in listOf(1, 0x80, 0xFF)) {
                    val bad = bytes.copyOf().also { it[offset] = value.toByte() }
                    val error = assertFailsWith<IllegalArgumentException> { read(XdrReader(bad), size) }
                    assertEquals("XDR padding at offset $offset must be zero, got $value", error.message)
                }
            }
        }
    }

    private fun rejectsMissing(read: (XdrReader, Int) -> ByteArray, variable: Boolean = true) {
        for (size in 1..3) {
            val bytes = input(size, variable)
            val start = (if (variable) 4 else 0) + size
            for (end in start until bytes.size) {
                val error = assertFailsWith<IllegalArgumentException> {
                    read(XdrReader(bytes.copyOf(end)), size)
                }
                assertTrue(error.message.orEmpty().startsWith("XDR decode requires"))
            }
        }
    }

    @Test fun stringZeroPadding() = accepts(readString)
    @Test fun opaqueZeroPadding() = accepts(readOpaque)
    @Test fun fixedZeroPadding() = accepts(readFixed, false)
    @Test fun stringNonzeroPadding() = rejectsNonzero(readString)
    @Test fun opaqueNonzeroPadding() = rejectsNonzero(readOpaque)
    @Test fun fixedNonzeroPadding() = rejectsNonzero(readFixed, false)
    @Test fun stringMissingPadding() = rejectsMissing(readString)
    @Test fun opaqueMissingPadding() = rejectsMissing(readOpaque)
    @Test fun fixedMissingPadding() = rejectsMissing(readFixed, false)

    @Test fun emptyAndNulStringsRoundTrip() {
        for (value in listOf("", "\u0000")) {
            val writer = XdrWriter()
            writer.writeString(value)
            val expected = if (value.isEmpty()) ByteArray(4) else byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0)
            assertContentEquals(expected, writer.toByteArray())
            assertEquals(value, XdrReader(writer.toByteArray()).readString())
        }
    }

    @Test fun emptyOpaqueRoundTrips() {
        val writer = XdrWriter()
        writer.writeVariableOpaque(byteArrayOf())
        assertContentEquals(ByteArray(4), writer.toByteArray())
        assertContentEquals(byteArrayOf(), XdrReader(writer.toByteArray()).readVariableOpaque())
    }
}
