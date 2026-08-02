package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Bounds checking of [XdrReader], asserted in common code so every target is held to the same
 * contract.
 *
 * XDR carries no stream-level length prefix, so a truncated or hostile buffer is only detectable
 * at the read that would run past the end. Every such read raises [IllegalArgumentException] on
 * all targets, and a length prefix is validated before any allocation.
 */
class XdrReaderBoundsTest {

    /** Big-endian encoding of a single XDR int, used to plant length prefixes. */
    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte()
    )

    @Test
    fun testReadIntOnEmptyBufferFails() {
        val exception = assertFailsWith<IllegalArgumentException> {
            XdrReader(ByteArray(0)).readInt()
        }
        assertTrue(
            exception.message?.contains("XDR decode requires") == true,
            "Message should name the shortfall, got: ${exception.message}"
        )
    }

    @Test
    fun testReadIntOnPartialWordFails() {
        assertFailsWith<IllegalArgumentException> {
            XdrReader(byteArrayOf(0, 0, 1)).readInt()
        }
    }

    @Test
    fun testReadIntPastEndOfBufferFails() {
        val reader = XdrReader(intBytes(7))
        assertEquals(7, reader.readInt())
        assertFailsWith<IllegalArgumentException> { reader.readInt() }
    }

    @Test
    fun testNegativeLengthPrefixIsRejectedForString() {
        val exception = assertFailsWith<IllegalArgumentException> {
            XdrReader(intBytes(-1)).readString()
        }
        assertTrue(
            exception.message?.contains("negative") == true,
            "Message should identify the negative length, got: ${exception.message}"
        )
    }

    @Test
    fun testNegativeLengthPrefixIsRejectedForVariableOpaque() {
        assertFailsWith<IllegalArgumentException> {
            XdrReader(intBytes(-1)).readVariableOpaque()
        }
    }

    @Test
    fun testHugeLengthPrefixIsRejectedWithoutAllocatingForString() {
        // A hostile prefix must fail the bounds check rather than attempt a 2GB allocation
        val exception = assertFailsWith<IllegalArgumentException> {
            XdrReader(intBytes(Int.MAX_VALUE)).readString()
        }
        assertTrue(
            exception.message?.contains("XDR decode requires") == true,
            "Message should name the shortfall, got: ${exception.message}"
        )
    }

    @Test
    fun testHugeLengthPrefixIsRejectedWithoutAllocatingForVariableOpaque() {
        assertFailsWith<IllegalArgumentException> {
            XdrReader(intBytes(Int.MAX_VALUE)).readVariableOpaque()
        }
    }

    @Test
    fun testReadFixedOpaquePastEndFails() {
        assertFailsWith<IllegalArgumentException> {
            XdrReader(byteArrayOf(1, 2, 3, 4)).readFixedOpaque(8)
        }
    }

    @Test
    fun testReadFixedOpaqueRejectsNegativeLength() {
        assertFailsWith<IllegalArgumentException> {
            XdrReader(byteArrayOf(1, 2, 3, 4)).readFixedOpaque(-4)
        }
    }

    @Test
    fun testTruncatedLongFails() {
        // readLong consumes two words; only one is present
        assertFailsWith<IllegalArgumentException> {
            XdrReader(intBytes(1)).readLong()
        }
    }

    @Test
    fun testWellFormedValuesRoundTrip() {
        val writer = XdrWriter()
        writer.writeInt(42)
        writer.writeLong(-9_007_199_254_740_993L)
        writer.writeString("stellar")
        writer.writeVariableOpaque(byteArrayOf(9, 8, 7))
        writer.writeBoolean(true)

        val reader = XdrReader(writer.toByteArray())
        assertEquals(42, reader.readInt())
        assertEquals(-9_007_199_254_740_993L, reader.readLong())
        assertEquals("stellar", reader.readString())
        assertContentEquals(byteArrayOf(9, 8, 7), reader.readVariableOpaque())
        assertEquals(true, reader.readBoolean())
    }

    @Test
    fun testValueEndingOnBufferEndWithoutPaddingIsRead() {
        // Trailing padding is skipped but is not required to be present: a 4-byte value that
        // ends exactly on the buffer end needs no padding and must read cleanly.
        val reader = XdrReader(intBytes(4) + byteArrayOf(1, 2, 3, 4))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), reader.readVariableOpaque())
    }

    @Test
    fun testUnpaddedShortValueAtBufferEndIsRead() {
        // A 3-byte value would be followed by one padding byte in a full stream; ending the
        // buffer immediately after the data must still yield the value.
        val reader = XdrReader(intBytes(3) + byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), reader.readVariableOpaque())
    }
}
