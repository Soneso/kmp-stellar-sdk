package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XdrPaddingTest {
    // Distinct bytes form a recognizable big-endian sentinel word after the padded value.
    private val sentinel = byteArrayOf(1, 2, 3, 4)

    private val readString: (XdrReader, Int) -> ByteArray = { reader, _ -> reader.readString().encodeToByteArray() }
    private val readOpaque: (XdrReader, Int) -> ByteArray = { reader, _ -> reader.readVariableOpaque() }
    private val readFixed: (XdrReader, Int) -> ByteArray = { reader, size -> reader.readFixedOpaque(size) }

    // Small lengths fit in the low byte of the four-byte big-endian count; the high bytes are zero.
    // ASCII 'A' is the payload; XDR pads it with zeros to a four-byte word boundary.
    private fun input(size: Int, variable: Boolean): ByteArray =
        (if (variable) byteArrayOf(0, 0, 0, size.toByte()) else byteArrayOf()) +
            ByteArray(size) { 65 } + ByteArray((4 - size % 4) % 4)

    // Variable opaque has a four-byte length prefix; fixed opaque starts at offset zero.
    private fun paddingStart(size: Int, variable: Boolean) = (if (variable) 4 else 0) + size

    private fun accepts(read: (XdrReader, Int) -> ByteArray, variable: Boolean = true) {
        // Lengths one through four exercise every padding width, including no padding.
        for (size in 1..4) {
            val reader = XdrReader(input(size, variable) + sentinel)
            assertContentEquals(ByteArray(size) { 65 }, read(reader, size))
            assertEquals(0x01020304, reader.readInt())
            assertContentEquals(ByteArray(size) { 65 }, read(XdrReader(input(size, variable)), size))
        }
    }

    private fun rejectsNonzero(read: (XdrReader, Int) -> ByteArray, variable: Boolean = true) {
        // Lengths one through three all require padding.
        for (size in 1..3) {
            val bytes = input(size, variable)
            val start = paddingStart(size, variable)
            for (offset in start until bytes.size) {
                // Corrupt padding with the smallest nonzero byte, the high bit, and all bits set.
                for (value in listOf(1, 0x80, 0xFF)) {
                    val bad = bytes.copyOf().also { it[offset] = value.toByte() }
                    val error = assertFailsWith<IllegalArgumentException> { read(XdrReader(bad), size) }
                    assertEquals("XDR padding at offset $offset must be zero, got $value", error.message)
                }
            }
        }
    }

    private fun rejectsMissing(read: (XdrReader, Int) -> ByteArray, variable: Boolean = true) {
        // Lengths one through three all require padding.
        for (size in 1..3) {
            val bytes = input(size, variable)
            val start = paddingStart(size, variable)
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
        // The NUL character is one zero-valued UTF-8 data byte, distinct from an empty string.
        for (value in listOf("", "\u0000")) {
            val writer = XdrWriter()
            writer.writeString(value)
            // Empty is a zero length word; NUL is a length-one word, a zero data byte, and three padding zeros.
            val expected = if (value.isEmpty()) ByteArray(4) else byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0)
            assertContentEquals(expected, writer.toByteArray())
            assertEquals(value, XdrReader(writer.toByteArray()).readString())
        }
    }

    @Test fun emptyOpaqueRoundTrips() {
        val writer = XdrWriter()
        writer.writeVariableOpaque(byteArrayOf())
        // Empty opaque consists only of its four-byte zero length word.
        assertContentEquals(ByteArray(4), writer.toByteArray())
        assertContentEquals(byteArrayOf(), XdrReader(writer.toByteArray()).readVariableOpaque())
    }
}
