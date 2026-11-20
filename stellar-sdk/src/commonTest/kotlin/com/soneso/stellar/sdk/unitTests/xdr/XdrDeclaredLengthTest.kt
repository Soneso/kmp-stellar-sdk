package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import com.soneso.stellar.sdk.xdr.readArrayLength
import com.soneso.stellar.sdk.xdr.readString
import com.soneso.stellar.sdk.xdr.readVariableOpaque
import com.soneso.stellar.sdk.xdr.writeString
import com.soneso.stellar.sdk.xdr.writeVariableOpaque
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class XdrDeclaredLengthTest {
    private val two = byteArrayOf(0, 0, 0, 2, 0xc3.toByte(), 0xa9.toByte(), 0, 0)
    private val three = byteArrayOf(0, 0, 0, 3, 65, 66, 67, 0)

    @Test fun readStringAtMaximum() = assertEquals("é", XdrReader(two).readString(2))
    @Test fun readOpaqueAtMaximum() =
        assertContentEquals(byteArrayOf(0xc3.toByte(), 0xa9.toByte()), XdrReader(two).readVariableOpaque(2))
    @Test fun readArrayAtMaximum() =
        assertEquals(2, XdrReader(byteArrayOf(0, 0, 0, 2) + ByteArray(8)).readArrayLength(2))

    @Test fun readStringAboveMaximum() = rejectsRead("string") { it.readString(2) }
    @Test fun readOpaqueAboveMaximum() = rejectsRead("opaque") { it.readVariableOpaque(2) }
    @Test fun readArrayAboveMaximum() = rejectsRead("array") { it.readArrayLength(2) }

    private fun rejectsRead(kind: String, read: (XdrReader) -> Unit) {
        val reader = XdrReader(three + ByteArray(8))
        val error = assertFailsWith<IllegalArgumentException> { read(reader) }
        val noun = if (kind == "array") "count" else "length"
        assertEquals("XDR $kind $noun 3 exceeds maximum 2", error.message)
        assertEquals(0x41424300, reader.readInt())
    }

    @Test fun boundedArrayRetainsAvailableBytesCheck() {
        val error = assertFailsWith<IllegalArgumentException> {
            XdrReader(byteArrayOf(0, 0, 0, 2) + ByteArray(4)).readArrayLength(2)
        }
        assertTrue(error.message.orEmpty().contains("4 byte(s) remaining"))
        assertFailsWith<IllegalArgumentException> { XdrReader(ByteArray(4) { -1 }).readArrayLength(2) }
    }

    @Test fun noArgumentArrayRemainsUnbounded() {
        assertEquals(101, XdrReader(byteArrayOf(0, 0, 0, 101) + ByteArray(404)).readArrayLength())
    }

    @Test fun writeStringAtMaximum() {
        val writer = XdrWriter()
        writer.writeString("é", 2)
        assertContentEquals(two, writer.toByteArray())
    }

    @Test fun writeOpaqueAtMaximum() {
        val writer = XdrWriter()
        writer.writeVariableOpaque(byteArrayOf(0xc3.toByte(), 0xa9.toByte()), 2)
        assertContentEquals(two, writer.toByteArray())
    }

    @Test fun writeStringAboveMaximum() = rejectsWrite("string") { it.writeString("éA", 2) }
    @Test fun writeOpaqueAboveMaximum() = rejectsWrite("opaque") { it.writeVariableOpaque(byteArrayOf(1, 2, 3), 2) }

    private fun rejectsWrite(kind: String, write: (XdrWriter) -> Unit) {
        val writer = XdrWriter()
        writer.writeInt(7)
        val error = assertFailsWith<IllegalArgumentException> { write(writer) }
        assertEquals("XDR $kind length 3 exceeds maximum 2", error.message)
        assertContentEquals(byteArrayOf(0, 0, 0, 7), writer.toByteArray())
    }

    @Test fun zeroMaximaAcceptEmptyValues() {
        assertEquals("", XdrReader(ByteArray(4)).readString(0))
        assertContentEquals(byteArrayOf(), XdrReader(ByteArray(4)).readVariableOpaque(0))
        assertEquals(0, XdrReader(ByteArray(4)).readArrayLength(0))
        val writer = XdrWriter()
        writer.writeString("", 0)
        writer.writeVariableOpaque(byteArrayOf(), 0)
        assertContentEquals(ByteArray(8), writer.toByteArray())
    }
}
