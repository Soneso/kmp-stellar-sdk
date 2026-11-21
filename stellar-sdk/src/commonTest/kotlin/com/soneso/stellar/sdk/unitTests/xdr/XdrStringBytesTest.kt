//
//  XdrStringBytesTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.xdr

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.MemoXdr
import com.soneso.stellar.sdk.xdr.SCMetaV0Xdr
import com.soneso.stellar.sdk.xdr.SCSpecFunctionV0Xdr
import com.soneso.stellar.sdk.xdr.SCStringXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.String64Xdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * The string positions that carry bytes (memo text, `SCVal` string and symbol, and every other
 * `SCString` and `SCSymbol` position) keep the bytes the XDR holds, also bytes that are not valid
 * UTF-8 and leading byte order marks; their `value` is a strict UTF-8 view. Every other string
 * position decodes strictly, in the binary and the XDR-JSON form, and keeps leading byte order marks.
 */
class XdrStringBytesTest {

    private val notUtf8 = Util.hexToBytes("fffe20616263")
    private val bom = Util.hexToBytes("efbbbf61")

    private fun bytesOf(encode: (XdrWriter) -> Unit): ByteArray = XdrWriter().also(encode).toByteArray()

    /** The XDR form of a string or variable opaque: length, bytes, zero padding. */
    private fun xdrString(bytes: ByteArray): ByteArray =
        bytesOf { it.writeInt(bytes.size) } + bytes + ByteArray((4 - bytes.size % 4) % 4)

    /** A spec function with the given doc bytes, name `f` and no inputs or outputs. */
    private fun specFunction(doc: ByteArray): ByteArray =
        xdrString(doc) + xdrString("f".encodeToByteArray()) + ByteArray(8)

    @Test
    fun standaloneStringAndSymbolValuesRoundTripTheirBytes() {
        val vectors = mapOf(
            "AAAADgAAAAb//iBhYmMAAA==" to "fffe20616263",
            "AAAADwAAAAb//iBhYmMAAA==" to "fffe20616263",
            "AAAADgAAAATvu79h" to "efbbbf61",
            "AAAADwAAAATvu79h" to "efbbbf61",
            "AAAADgAAAAPDqTEA" to "c3a931",
            "AAAADwAAAAPDqTEA" to "c3a931"
        )
        for ((base64, valueHex) in vectors) {
            val value = SCValXdr.decode(XdrReader(Base64.decode(base64)))
            assertEquals(base64, Base64.encode(bytesOf { value.encode(it) }))
            val bytes = when (value) {
                is SCValXdr.Str -> value.value.bytes
                is SCValXdr.Sym -> value.value.bytes
                else -> error("unexpected arm $value")
            }
            assertEquals(valueHex, Util.bytesToHex(bytes))
        }
    }

    @Test
    fun xdrJsonCarriesTheBytesInBothDirections() {
        val vectors = mapOf(
            "AAAADgAAAAb//iBhYmMAAA==" to """{"string":"\\xff\\xfe abc"}""",
            "AAAADwAAAAb//iBhYmMAAA==" to """{"symbol":"\\xff\\xfe abc"}""",
            "AAAADgAAAATvu79h" to """{"string":"\\xef\\xbb\\xbfa"}""",
            "AAAADwAAAATvu79h" to """{"symbol":"\\xef\\xbb\\xbfa"}"""
        )
        for ((base64, json) in vectors) {
            assertEquals(json, SCValXdr.decode(XdrReader(Base64.decode(base64))).toXdrJson())
            assertEquals(base64, Base64.encode(bytesOf { SCValXdr.fromXdrJson(json).encode(it) }))
        }
        val memo = MemoXdr.fromXdrJson("""{"text":"\\xff\\xfe abc"}""")
        assertContentEquals(notUtf8, assertIs<MemoXdr.Text>(memo).bytes)
        assertEquals("""{"text":"\\xff\\xfe abc"}""", memo.toXdrJson())
    }

    @Test
    fun theTextViewKeepsEveryLeadingByteOrderMark() {
        val views = mapOf(
            "efbbbf61" to "\uFEFFa",
            "efbbbfefbbbf61" to "\uFEFF\uFEFFa",
            "61efbbbf" to "a\uFEFF",
            "c3a931" to "é1"
        )
        for ((hex, text) in views) {
            val bytes = Util.hexToBytes(hex)
            assertEquals(text, SCStringXdr(bytes).value)
            assertEquals(text, SCSymbolXdr(bytes).value)
            assertEquals(text, MemoXdr.Text(bytes).value)
        }
    }

    @Test
    fun theTextViewRefusesBytesThatAreNotUtf8() {
        for (hex in listOf("fffe20616263", "61c3", "61e282", "c0af")) {
            val bytes = Util.hexToBytes(hex)
            assertFailsWith<CharacterCodingException>(hex) { SCStringXdr(bytes).value }
            assertFailsWith<CharacterCodingException>(hex) { SCSymbolXdr(bytes).value }
            assertFailsWith<CharacterCodingException>(hex) { MemoXdr.Text(bytes).value }
        }
    }

    @Test
    fun boundedStringPositionsRefuseBytesThatAreNotUtf8AndKeepAByteOrderMark() {
        assertFailsWith<CharacterCodingException> { SCSpecFunctionV0Xdr.decode(XdrReader(specFunction(notUtf8))) }
        assertFailsWith<CharacterCodingException> { String64Xdr.decode(XdrReader(xdrString(notUtf8))) }

        assertEquals("\uFEFFa", SCSpecFunctionV0Xdr.decode(XdrReader(specFunction(bom))).doc)
        assertEquals("\uFEFFa", String64Xdr.decode(XdrReader(xdrString(bom))).value)
    }

    @Test
    fun unboundedStringPositionsRefuseBytesThatAreNotUtf8AndKeepAByteOrderMark() {
        val key = xdrString("k".encodeToByteArray())
        assertFailsWith<CharacterCodingException> { SCMetaV0Xdr.decode(XdrReader(xdrString(notUtf8) + key)) }
        assertFailsWith<CharacterCodingException> { SCMetaV0Xdr.decode(XdrReader(key + xdrString(notUtf8))) }

        val meta = SCMetaV0Xdr.decode(XdrReader(xdrString(bom) + xdrString(bom)))
        assertEquals("\uFEFFa", meta.key)
        assertEquals("\uFEFFa", meta.`val`)
    }

    @Test
    fun xdrJsonStringPositionsRefuseBytesThatAreNotUtf8AndKeepAByteOrderMark() {
        val failure = assertFailsWith<IllegalArgumentException> { String64Xdr.fromXdrJson(""""\\xff\\xfe abc"""") }
        assertEquals(
            """String64Xdr: key "value" resolves to bytes that are not valid UTF-8: "\\xff\\xfe abc"""",
            failure.message
        )
        assertIs<CharacterCodingException>(failure.cause)
        assertFailsWith<IllegalArgumentException> { SCMetaV0Xdr.fromXdrJson("""{"key":"k","val":"\\xff"}""") }

        assertEquals("\uFEFFa", String64Xdr.fromXdrJson(""""\\xef\\xbb\\xbfa"""").value)
        assertEquals("\uFEFFa", SCMetaV0Xdr.fromXdrJson("""{"key":"\\xef\\xbb\\xbfa","val":"v"}""").key)
    }

    @Test
    fun equalityAndHashingCompareTheBytes() {
        assertEquals(SCStringXdr(notUtf8.copyOf()), SCStringXdr(notUtf8.copyOf()))
        assertEquals(SCStringXdr(notUtf8).hashCode(), SCStringXdr(notUtf8.copyOf()).hashCode())
        assertEquals(SCSymbolXdr(notUtf8.copyOf()), SCSymbolXdr(notUtf8.copyOf()))
        assertEquals(SCSymbolXdr(notUtf8).hashCode(), SCSymbolXdr(notUtf8.copyOf()).hashCode())
        assertEquals(MemoXdr.Text(notUtf8.copyOf()), MemoXdr.Text(notUtf8.copyOf()))
        assertEquals(MemoXdr.Text(notUtf8).hashCode(), MemoXdr.Text(notUtf8.copyOf()).hashCode())

        assertEquals(SCStringXdr("abc"), SCStringXdr("abc".encodeToByteArray()))
        assertEquals(SCSymbolXdr("abc"), SCSymbolXdr("abc".encodeToByteArray()))
        assertEquals(MemoXdr.Text("abc"), MemoXdr.Text("abc".encodeToByteArray()))

        // Two byte sequences that are not UTF-8 and read as the same replacement text differ.
        val other = Util.hexToBytes("fefe20616263")
        assertNotEquals(SCStringXdr(notUtf8), SCStringXdr(other))
        assertNotEquals(SCSymbolXdr(notUtf8), SCSymbolXdr(other))
        assertNotEquals(MemoXdr.Text(notUtf8), MemoXdr.Text(other))
        assertNotEquals<Any>(SCStringXdr(notUtf8), SCSymbolXdr(notUtf8))
        assertNotEquals<Any>(SCSymbolXdr(notUtf8), SCStringXdr(notUtf8))
        assertNotEquals<MemoXdr>(MemoXdr.Text(ByteArray(0)), MemoXdr.Void)
    }

    @Test
    fun theValuesCopyTheBytesInAndOut() {
        val input = notUtf8.copyOf()
        val string = SCStringXdr(input)
        val symbol = SCSymbolXdr(input)
        val text = MemoXdr.Text(input)
        input[0] = 0
        string.bytes[1] = 0
        symbol.bytes[1] = 0
        text.bytes[1] = 0
        assertContentEquals(notUtf8, string.bytes)
        assertContentEquals(notUtf8, symbol.bytes)
        assertContentEquals(notUtf8, text.bytes)
    }

    @Test
    fun toStringShowsTheTextOrTheEscapedBytes() {
        assertEquals("SCStringXdr(value=café)", SCStringXdr("café").toString())
        assertEquals("SCSymbolXdr(value=transfer)", SCSymbolXdr("transfer").toString())
        assertEquals("Text(value=café)", MemoXdr.Text("café").toString())

        assertEquals("SCStringXdr(value=\\xff\\xfe abc)", SCStringXdr(notUtf8).toString())
        assertEquals("SCSymbolXdr(value=\\xff\\xfe abc)", SCSymbolXdr(notUtf8).toString())
        assertEquals("Text(value=\\xff\\xfe abc)", MemoXdr.Text(notUtf8).toString())
    }

    @Test
    fun theDeclaredMaximumHoldsForTheBytes() {
        val tooLong = ByteArray(33) { 0xff.toByte() }
        val decodeFailure = assertFailsWith<IllegalArgumentException> { SCSymbolXdr.decode(XdrReader(xdrString(tooLong))) }
        assertEquals("XDR string length 33 exceeds maximum 32", decodeFailure.message)
        val encodeFailure = assertFailsWith<IllegalArgumentException> { bytesOf { SCSymbolXdr(tooLong).encode(it) } }
        assertEquals("XDR string length 33 exceeds maximum 32", encodeFailure.message)

        val memoFailure = assertFailsWith<IllegalArgumentException> { bytesOf { MemoXdr.Text(ByteArray(29)).encode(it) } }
        assertEquals("XDR string length 29 exceeds maximum 28", memoFailure.message)
        val full = ByteArray(28) { 0xff.toByte() }
        assertContentEquals(full, assertIs<MemoXdr.Text>(MemoXdr.decode(XdrReader(bytesOf { MemoXdr.Text(full).encode(it) }))).bytes)
    }
}
