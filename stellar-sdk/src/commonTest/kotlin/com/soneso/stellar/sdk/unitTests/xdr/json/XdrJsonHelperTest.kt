package com.soneso.stellar.sdk.unitTests.xdr.json

import com.soneso.stellar.sdk.xdr.XdrJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Verifies the XDR-JSON (SEP-0051) runtime rules: the value mappings, the string escape
 * ladder, the strict input contract, and the limits that guard decoding.
 *
 * Golden values are the renderings SEP-0051 specifies; every documented rejection path has a
 * negative case asserting [IllegalArgumentException].
 */
class XdrJsonHelperTest {

    private val type = "SampleXdr"
    private val key = "field"

    private fun text(element: JsonElement): String = (element as JsonPrimitive).content

    private fun rejects(block: () -> Unit): IllegalArgumentException =
        assertFailsWith<IllegalArgumentException>(block = block)

    /**
     * Parses a bare JSON number so its literal text survives verbatim. Building a
     * [JsonPrimitive] from a Kotlin `Double` is not portable: on the JavaScript target 1.0
     * renders as "1", so a test written that way would assert nothing there.
     */
    private fun number(literal: String): JsonElement = XdrJson.parse(literal, type)

    // -------------------------------------------------------------------------------------
    // 32-bit integers
    // -------------------------------------------------------------------------------------

    @Test
    fun int32EncodesAsJsonNumber() {
        assertEquals("0", XdrJson.encodeToString(XdrJson.int32(0)))
        assertEquals("-1", XdrJson.encodeToString(XdrJson.int32(-1)))
        assertEquals("2147483647", XdrJson.encodeToString(XdrJson.int32(Int.MAX_VALUE)))
        assertEquals("-2147483648", XdrJson.encodeToString(XdrJson.int32(Int.MIN_VALUE)))
    }

    @Test
    fun int32DecodesAtBothExtremes() {
        assertEquals(Int.MAX_VALUE, XdrJson.int32(JsonPrimitive(Int.MAX_VALUE), type, key))
        assertEquals(Int.MIN_VALUE, XdrJson.int32(JsonPrimitive(Int.MIN_VALUE), type, key))
        assertEquals(0, XdrJson.int32(JsonPrimitive(0), type, key))
    }

    @Test
    fun int32RoundTripsThroughEncoding() {
        for (value in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) {
            assertEquals(value, XdrJson.int32(XdrJson.int32(value), type, key))
        }
    }

    @Test
    fun int32RejectsStringForm() {
        val error = rejects { XdrJson.int32(JsonPrimitive("1"), type, key) }
        assertTrue(error.message!!.contains(type))
        assertTrue(error.message!!.contains(key))
    }

    @Test
    fun int32RejectsValueBeyondRange() {
        rejects { XdrJson.int32(JsonPrimitive(2147483648L), type, key) }
        rejects { XdrJson.int32(JsonPrimitive(-2147483649L), type, key) }
    }

    @Test
    fun int32RejectsNonIntegerLiterals() {
        rejects { XdrJson.int32(number("1.0"), type, key) }
        rejects { XdrJson.int32(number("1.5"), type, key) }
        rejects { XdrJson.int32(number("-0.0"), type, key) }
    }

    @Test
    fun int32RejectsNull() {
        rejects { XdrJson.int32(JsonNull, type, key) }
    }

    @Test
    fun int32RejectsContainers() {
        rejects { XdrJson.int32(buildJsonObject { }, type, key) }
        rejects { XdrJson.int32(buildJsonArray { }, type, key) }
    }

    @Test
    fun uint32EncodesFullRangeAsJsonNumber() {
        assertEquals("0", XdrJson.encodeToString(XdrJson.uint32(0u)))
        assertEquals("4294967295", XdrJson.encodeToString(XdrJson.uint32(UInt.MAX_VALUE)))
    }

    @Test
    fun uint32RoundTripsAtMaximum() {
        assertEquals(UInt.MAX_VALUE, XdrJson.uint32(XdrJson.uint32(UInt.MAX_VALUE), type, key))
        assertEquals(0u, XdrJson.uint32(XdrJson.uint32(0u), type, key))
    }

    @Test
    fun uint32RejectsNegativeValue() {
        rejects { XdrJson.uint32(JsonPrimitive(-1), type, key) }
    }

    @Test
    fun uint32RejectsValueBeyondRange() {
        rejects { XdrJson.uint32(JsonPrimitive(4294967296L), type, key) }
    }

    @Test
    fun uint32RejectsStringForm() {
        rejects { XdrJson.uint32(JsonPrimitive("1"), type, key) }
    }

    // -------------------------------------------------------------------------------------
    // 64-bit integers
    // -------------------------------------------------------------------------------------

    @Test
    fun int64EncodesAsBaseTenString() {
        assertEquals("\"0\"", XdrJson.encodeToString(XdrJson.int64(0)))
        assertEquals("\"-1\"", XdrJson.encodeToString(XdrJson.int64(-1)))
        assertEquals(
            "\"9223372036854775807\"",
            XdrJson.encodeToString(XdrJson.int64(Long.MAX_VALUE))
        )
        assertEquals(
            "\"-9223372036854775808\"",
            XdrJson.encodeToString(XdrJson.int64(Long.MIN_VALUE))
        )
    }

    @Test
    fun int64DecodesFromStringForm() {
        assertEquals(Long.MAX_VALUE, XdrJson.int64(JsonPrimitive("9223372036854775807"), type, key))
        assertEquals(Long.MIN_VALUE, XdrJson.int64(JsonPrimitive("-9223372036854775808"), type, key))
    }

    @Test
    fun int64AlsoAcceptsNumberForm() {
        assertEquals(1L, XdrJson.int64(JsonPrimitive(1), type, key))
        assertEquals(-42L, XdrJson.int64(JsonPrimitive(-42), type, key))
    }

    @Test
    fun int64RejectsValueBeyondRange() {
        rejects { XdrJson.int64(JsonPrimitive("9223372036854775808"), type, key) }
        rejects { XdrJson.int64(JsonPrimitive("-9223372036854775809"), type, key) }
    }

    @Test
    fun uint64EncodesFullRangeAsBaseTenString() {
        assertEquals("\"0\"", XdrJson.encodeToString(XdrJson.uint64(0uL)))
        assertEquals(
            "\"18446744073709551615\"",
            XdrJson.encodeToString(XdrJson.uint64(ULong.MAX_VALUE))
        )
    }

    @Test
    fun uint64RoundTripsAtMaximumWithExactDigits() {
        val encoded = XdrJson.uint64(ULong.MAX_VALUE)
        assertEquals("18446744073709551615", text(encoded))
        assertEquals(ULong.MAX_VALUE, XdrJson.uint64(encoded, type, key))
    }

    @Test
    fun int64RoundTripsAtMinimumWithExactDigits() {
        val encoded = XdrJson.int64(Long.MIN_VALUE)
        assertEquals("-9223372036854775808", text(encoded))
        assertEquals(Long.MIN_VALUE, XdrJson.int64(encoded, type, key))
    }

    @Test
    fun uint64RejectsNegativeValue() {
        rejects { XdrJson.uint64(JsonPrimitive("-1"), type, key) }
    }

    @Test
    fun uint64RejectsValueBeyondRange() {
        rejects { XdrJson.uint64(JsonPrimitive("18446744073709551616"), type, key) }
    }

    @Test
    fun integersRejectExponentNotation() {
        rejects { XdrJson.int64(JsonPrimitive("1e10"), type, key) }
        rejects { XdrJson.int64(number("1e10"), type, key) }
        rejects { XdrJson.int32(number("1E5"), type, key) }
        rejects { XdrJson.uint64(number("1e10"), type, key) }
    }

    @Test
    fun integersRejectTrailingDecimalPoint() {
        rejects { XdrJson.int64(JsonPrimitive("1.0"), type, key) }
    }

    @Test
    fun integersRejectHexadecimalNotation() {
        rejects { XdrJson.int64(JsonPrimitive("0x10"), type, key) }
    }

    @Test
    fun integersRejectLeadingPlus() {
        rejects { XdrJson.int64(JsonPrimitive("+1"), type, key) }
        rejects { XdrJson.uint64(JsonPrimitive("+1"), type, key) }
    }

    @Test
    fun integersRejectLeadingZeros() {
        rejects { XdrJson.int64(JsonPrimitive("007"), type, key) }
        rejects { XdrJson.uint64(JsonPrimitive("00"), type, key) }
    }

    @Test
    fun integersRejectNegativeZero() {
        rejects { XdrJson.int64(JsonPrimitive("-0"), type, key) }
    }

    @Test
    fun integersRejectEmptyText() {
        rejects { XdrJson.int64(JsonPrimitive(""), type, key) }
    }

    // -------------------------------------------------------------------------------------
    // Booleans
    // -------------------------------------------------------------------------------------

    @Test
    fun booleanEncodesAsJsonBoolean() {
        assertEquals("true", XdrJson.encodeToString(XdrJson.bool(true)))
        assertEquals("false", XdrJson.encodeToString(XdrJson.bool(false)))
    }

    @Test
    fun booleanRoundTrips() {
        assertEquals(true, XdrJson.bool(XdrJson.bool(true), type, key))
        assertEquals(false, XdrJson.bool(XdrJson.bool(false), type, key))
    }

    @Test
    fun booleanRejectsStringForm() {
        rejects { XdrJson.bool(JsonPrimitive("true"), type, key) }
    }

    @Test
    fun booleanRejectsNumberForm() {
        rejects { XdrJson.bool(JsonPrimitive(1), type, key) }
    }

    @Test
    fun booleanRejectsNull() {
        rejects { XdrJson.bool(JsonNull, type, key) }
    }

    // -------------------------------------------------------------------------------------
    // Opaque data as hexadecimal
    // -------------------------------------------------------------------------------------

    @Test
    fun hexEncodesLowercase() {
        val bytes = byteArrayOf(0x0A, 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte())
        assertEquals("0abcdef0", text(XdrJson.hex(bytes)))
    }

    @Test
    fun hexEncodesEmptyDataAsEmptyString() {
        assertEquals("", text(XdrJson.hex(ByteArray(0))))
        assertEquals("\"\"", XdrJson.encodeToString(XdrJson.hex(ByteArray(0))))
    }

    @Test
    fun hexEncodesAllByteValues() {
        val bytes = ByteArray(256) { it.toByte() }
        val encoded = text(XdrJson.hex(bytes))
        assertEquals(512, encoded.length)
        assertTrue(encoded.startsWith("000102"))
        assertTrue(encoded.endsWith("fdfeff"))
        assertTrue(encoded.none { it.isUpperCase() })
    }

    @Test
    fun hexRoundTripsAllByteValues() {
        val bytes = ByteArray(256) { it.toByte() }
        val decoded = XdrJson.hex(XdrJson.hex(bytes), type, key)
        assertTrue(bytes.contentEquals(decoded))
    }

    @Test
    fun hexDecodesEmptyStringToEmptyData() {
        assertEquals(0, XdrJson.hex(JsonPrimitive(""), type, key).size)
    }

    @Test
    fun hexRejectsUppercaseDigits() {
        rejects { XdrJson.hex(JsonPrimitive("AB"), type, key) }
        rejects { XdrJson.hex(JsonPrimitive("aB"), type, key) }
    }

    @Test
    fun hexRejectsOddLength() {
        rejects { XdrJson.hex(JsonPrimitive("abc"), type, key) }
    }

    @Test
    fun hexRejectsNonHexadecimalCharacters() {
        rejects { XdrJson.hex(JsonPrimitive("zz"), type, key) }
        rejects { XdrJson.hex(JsonPrimitive("0g"), type, key) }
    }

    @Test
    fun hexRejectsCharactersBelowTheDigitRange() {
        rejects { XdrJson.hex(JsonPrimitive("0/"), type, key) }
        rejects { XdrJson.hex(JsonPrimitive("  "), type, key) }
    }

    @Test
    fun hexRejectsWrongFixedLength() {
        rejects { XdrJson.hex(JsonPrimitive("abcd"), type, key, expectedLength = 4) }
        rejects { XdrJson.hex(JsonPrimitive("abcdef"), type, key, expectedLength = 2) }
    }

    @Test
    fun hexAcceptsExactFixedLength() {
        val decoded = XdrJson.hex(JsonPrimitive("abcd"), type, key, expectedLength = 2)
        assertTrue(byteArrayOf(0xAB.toByte(), 0xCD.toByte()).contentEquals(decoded))
    }

    @Test
    fun hexRejectsOverDeclaredMaximum() {
        rejects { XdrJson.hex(JsonPrimitive("abcdef"), type, key, maxLength = 2) }
    }

    @Test
    fun hexAcceptsUpToDeclaredMaximum() {
        assertEquals(2, XdrJson.hex(JsonPrimitive("abcd"), type, key, maxLength = 2).size)
    }

    @Test
    fun hexRejectsNonStringInput() {
        rejects { XdrJson.hex(JsonPrimitive(1), type, key) }
        rejects { XdrJson.hex(JsonNull, type, key) }
    }

    // -------------------------------------------------------------------------------------
    // The string escape ladder
    // -------------------------------------------------------------------------------------

    @Test
    fun escapedStringKeepsPrintableAsciiVerbatim() {
        assertEquals("hello world", text(XdrJson.escapedString("hello world")))
    }

    @Test
    fun escapedStringAppliesShortEscapes() {
        val bytes = byteArrayOf(0x00, 0x09, 0x0A, 0x0D, 0x5C)
        assertEquals("\\0\\t\\n\\r\\\\", text(XdrJson.escapedString(bytes)))
    }

    @Test
    fun escapedStringUsesLowercaseHexForOtherBytes() {
        assertEquals("\\xc3", text(XdrJson.escapedString(byteArrayOf(0xC3.toByte()))))
        assertEquals("\\x7f", text(XdrJson.escapedString(byteArrayOf(0x7F))))
        assertEquals("\\x80", text(XdrJson.escapedString(byteArrayOf(0x80.toByte()))))
        assertEquals("\\x01", text(XdrJson.escapedString(byteArrayOf(0x01))))
    }

    @Test
    fun escapedStringKeepsPrintableBoundaryBytesVerbatim() {
        assertEquals(" ", text(XdrJson.escapedString(byteArrayOf(0x20))))
        assertEquals("~", text(XdrJson.escapedString(byteArrayOf(0x7E))))
    }

    @Test
    fun escapedStringMatchesSpecExample() {
        val bytes = "hello".encodeToByteArray() + byteArrayOf(0xC3.toByte()) +
            "world".encodeToByteArray()
        assertEquals("hello\\xc3world", text(XdrJson.escapedString(bytes)))
    }

    @Test
    fun escapedStringBackslashIsEscapedTwiceOnTheWire() {
        val encoded = XdrJson.encodeToString(XdrJson.escapedString(byteArrayOf(0x5C)))
        assertEquals("\"\\\\\\\\\"", encoded)
    }

    @Test
    fun escapedStringRendersMixedControlBytes() {
        val bytes = byteArrayOf('a'.code.toByte(), 0x5C, 0x00, 'b'.code.toByte(), 0x09, 0x0A, 0x0D)
        assertEquals("a\\\\\\0b\\t\\n\\r", text(XdrJson.escapedString(bytes)))
    }

    @Test
    fun escapedStringEncodesEmptyAsEmpty() {
        assertEquals("", text(XdrJson.escapedString(ByteArray(0))))
    }

    @Test
    fun escapedStringRoundTripsAllByteValues() {
        val bytes = ByteArray(256) { it.toByte() }
        val decoded = XdrJson.unescapeStringBytes(XdrJson.escapedString(bytes), type, key)
        assertTrue(bytes.contentEquals(decoded))
    }

    @Test
    fun unescapeReadsShortEscapes() {
        val decoded = XdrJson.unescapeStringBytes(JsonPrimitive("\\0\\t\\n\\r\\\\"), type, key)
        assertTrue(byteArrayOf(0x00, 0x09, 0x0A, 0x0D, 0x5C).contentEquals(decoded))
    }

    @Test
    fun unescapeReadsHexEscape() {
        val decoded = XdrJson.unescapeStringBytes(JsonPrimitive("\\xc3"), type, key)
        assertTrue(byteArrayOf(0xC3.toByte()).contentEquals(decoded))
    }

    @Test
    fun unescapeRejectsUppercaseHexEscape() {
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("\\xC3"), type, key) }
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("\\x3C"), type, key) }
    }

    @Test
    fun unescapeRejectsUnrecognisedEscape() {
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("\\q"), type, key) }
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("\\u0041"), type, key) }
    }

    @Test
    fun unescapeRejectsTrailingBackslash() {
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("abc\\"), type, key) }
    }

    @Test
    fun unescapeRejectsHexEscapeBelowTheDigitRange() {
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("\\x/0"), type, key) }
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("\\x0/"), type, key) }
    }

    @Test
    fun unescapeRejectsHexEscapeAboveTheLetterRange() {
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("\\xg0"), type, key) }
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("\\x0z"), type, key) }
    }

    @Test
    fun unescapeRejectsTruncatedHexEscape() {
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("\\x"), type, key) }
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("\\xc"), type, key) }
    }

    @Test
    fun unescapeRejectsUnescapedControlCharacter() {
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("a\u0000b"), type, key) }
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("a\u0009b"), type, key) }
    }

    @Test
    fun unescapeRejectsUnescapedNonAsciiCharacter() {
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("café"), type, key) }
    }

    @Test
    fun unescapeRejectsOverDeclaredMaximum() {
        rejects { XdrJson.unescapeStringBytes(JsonPrimitive("abcd"), type, key, maxLength = 3) }
    }

    @Test
    fun unescapeAcceptsUpToDeclaredMaximum() {
        assertEquals(
            4,
            XdrJson.unescapeStringBytes(JsonPrimitive("abcd"), type, key, maxLength = 4).size
        )
    }

    @Test
    fun unescapeReturnsTextForStringFields() {
        assertEquals("hello", XdrJson.unescapeString(JsonPrimitive("hello"), type, key))
    }

    @Test
    fun escapedTextRoundTripsThroughStringForm() {
        val original = "line1\\nline2"
        val encoded = XdrJson.escapedString(original)
        assertEquals(original, XdrJson.unescapeString(encoded, type, key))
    }

    // -------------------------------------------------------------------------------------
    // Arrays and optionals
    // -------------------------------------------------------------------------------------

    @Test
    fun arrayEncodesEmptyAsEmptyArray() {
        assertEquals("[]", XdrJson.encodeToString(XdrJson.array(emptyList<Int>()) { XdrJson.int32(it) }))
    }

    @Test
    fun arrayEncodesElementsInOrder() {
        val encoded = XdrJson.array(listOf(1, 2, 3)) { XdrJson.int32(it) }
        assertEquals("[1,2,3]", XdrJson.encodeToString(encoded))
    }

    @Test
    fun arrayDecodesToJsonArray() {
        val decoded = XdrJson.array(buildJsonArray { add(JsonPrimitive(1)) }, type, key)
        assertEquals(1, decoded.size)
    }

    @Test
    fun arrayRejectsNonArray() {
        rejects { XdrJson.array(JsonPrimitive("x"), type, key) }
        rejects { XdrJson.array(buildJsonObject { }, type, key) }
        rejects { XdrJson.array(JsonNull, type, key) }
    }

    @Test
    fun arrayRejectsWrongFixedLength() {
        val value = buildJsonArray { add(JsonPrimitive(1)) }
        rejects { XdrJson.array(value, type, key, expectedLength = 2) }
    }

    @Test
    fun arrayAcceptsExactFixedLength() {
        val value = buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2)) }
        assertEquals(2, XdrJson.array(value, type, key, expectedLength = 2).size)
    }

    @Test
    fun arrayRejectsOverDeclaredMaximum() {
        val value = buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2)) }
        rejects { XdrJson.array(value, type, key, maxLength = 1) }
    }

    @Test
    fun arrayAcceptsUpToDeclaredMaximum() {
        val value = buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2)) }
        assertEquals(2, XdrJson.array(value, type, key, maxLength = 2).size)
    }

    @Test
    fun optionalEncodesNullAsJsonNull() {
        assertEquals("null", XdrJson.encodeToString(XdrJson.optional<Int>(null) { XdrJson.int32(it) }))
    }

    @Test
    fun optionalEncodesPresentValue() {
        assertEquals("7", XdrJson.encodeToString(XdrJson.optional(7) { XdrJson.int32(it) }))
    }

    @Test
    fun optionalDecodesJsonNullAsAbsent() {
        assertNull(XdrJson.optional(JsonNull))
    }

    @Test
    fun optionalDecodesValueAsPresent() {
        val element = JsonPrimitive(7)
        assertSame(element, XdrJson.optional(element))
    }

    // -------------------------------------------------------------------------------------
    // Objects, schema property and union shape
    // -------------------------------------------------------------------------------------

    @Test
    fun objectReadsJsonObject() {
        val value = buildJsonObject { put("a", JsonPrimitive(1)) }
        assertEquals(1, XdrJson.obj(value, type).size)
    }

    @Test
    fun objectRejectsNonObject() {
        rejects { XdrJson.obj(JsonPrimitive("x"), type) }
        rejects { XdrJson.obj(buildJsonArray { }, type) }
        rejects { XdrJson.obj(JsonNull, type) }
    }

    @Test
    fun schemaPropertyIsStrippedOnInput() {
        val value = buildJsonObject {
            put("\$schema", JsonPrimitive("https://example.test/schema.json"))
            put("a", JsonPrimitive(1))
        }
        val stripped = XdrJson.obj(value, type)
        assertEquals(setOf("a"), stripped.keys)
    }

    @Test
    fun schemaPropertyIsNeverEmitted() {
        val value = buildJsonObject { put("a", JsonPrimitive(1)) }
        assertEquals("{\"a\":1}", XdrJson.encodeToString(XdrJson.stripSchema(value)))
    }

    @Test
    fun stripSchemaLeavesObjectsWithoutItUnchanged() {
        val value = buildJsonObject { put("a", JsonPrimitive(1)) }
        assertSame(value, XdrJson.stripSchema(value))
    }

    @Test
    fun schemaOnlyObjectIsRejectedAsUnion() {
        val value = buildJsonObject { put("\$schema", JsonPrimitive("x")) }
        rejects { XdrJson.singleKeyObject(value, type) }
    }

    @Test
    fun singleKeyObjectReturnsArmAndValue() {
        val value = buildJsonObject { put("u32", JsonPrimitive(5)) }
        val (arm, payload) = XdrJson.singleKeyObject(value, type)
        assertEquals("u32", arm)
        assertEquals(5, XdrJson.int32(payload, type, arm))
    }

    @Test
    fun singleKeyObjectIgnoresSchemaProperty() {
        val value = buildJsonObject {
            put("\$schema", JsonPrimitive("x"))
            put("u32", JsonPrimitive(5))
        }
        assertEquals("u32", XdrJson.singleKeyObject(value, type).first)
    }

    @Test
    fun singleKeyObjectRejectsMultipleKeys() {
        val value = buildJsonObject {
            put("u32", JsonPrimitive(5))
            put("i64", JsonPrimitive("5"))
        }
        rejects { XdrJson.singleKeyObject(value, type) }
    }

    @Test
    fun singleKeyObjectRejectsEmptyObject() {
        rejects { XdrJson.singleKeyObject(buildJsonObject { }, type) }
    }

    @Test
    fun singleKeyObjectRejectsBareString() {
        rejects { XdrJson.singleKeyObject(JsonPrimitive("v0"), type) }
    }

    @Test
    fun fieldReadsPresentKey() {
        val value = buildJsonObject { put("a", JsonPrimitive(1)) }
        assertEquals(JsonPrimitive(1), XdrJson.field(value, "a", type))
    }

    @Test
    fun fieldReadsKeyHoldingNull() {
        val value = buildJsonObject { put("a", JsonNull) }
        assertEquals(JsonNull, XdrJson.field(value, "a", type))
    }

    @Test
    fun fieldRejectsMissingKey() {
        val error = rejects { XdrJson.field(buildJsonObject { }, "a", type) }
        assertTrue(error.message!!.contains("\"a\""))
    }

    @Test
    fun fieldPrefersCanonicalKeyOverAlias() {
        val value = buildJsonObject {
            put("type", JsonPrimitive("canonical"))
            put("type_", JsonPrimitive("alias"))
        }
        assertEquals(JsonPrimitive("canonical"), XdrJson.field(value, "type", "type_", type))
    }

    @Test
    fun fieldFallsBackToAlias() {
        val value = buildJsonObject { put("type_", JsonPrimitive("alias")) }
        assertEquals(JsonPrimitive("alias"), XdrJson.field(value, "type", "type_", type))
    }

    @Test
    fun fieldRejectsMissingKeyAndAlias() {
        rejects { XdrJson.field(buildJsonObject { }, "type", "type_", type) }
    }

    @Test
    fun objectKeysKeepInsertionOrder() {
        val value = buildJsonObject {
            put("z", JsonPrimitive(1))
            put("a", JsonPrimitive(2))
            put("m", JsonPrimitive(3))
        }
        assertEquals("{\"z\":1,\"a\":2,\"m\":3}", XdrJson.encodeToString(value))
    }

    // -------------------------------------------------------------------------------------
    // Parsing and depth limits
    // -------------------------------------------------------------------------------------

    @Test
    fun parseReadsValidDocument() {
        val element = XdrJson.parse("{\"a\":1}", type)
        assertEquals(1, XdrJson.int32(XdrJson.field(element as JsonObject, "a", type), type, "a"))
    }

    @Test
    fun parseRejectsMalformedDocument() {
        rejects { XdrJson.parse("{", type) }
        rejects { XdrJson.parse("not json", type) }
        rejects { XdrJson.parse("", type) }
    }

    @Test
    fun parseAcceptsDocumentAtDepthLimit() {
        val text = "[".repeat(XdrJson.RECURSION_CAP) + "]".repeat(XdrJson.RECURSION_CAP)
        XdrJson.parse(text, type)
    }

    @Test
    fun parseRejectsDocumentBeyondDepthLimit() {
        val depth = XdrJson.RECURSION_CAP + 1
        val text = "[".repeat(depth) + "]".repeat(depth)
        val error = rejects { XdrJson.parse(text, type) }
        assertTrue(error.message!!.contains(XdrJson.RECURSION_CAP.toString()))
    }

    @Test
    fun parseIgnoresBracketsInsideStrings() {
        val text = "{\"a\":\"[[[[[[\"}"
        assertEquals(1, (XdrJson.parse(text, type) as JsonObject).size)
    }

    @Test
    fun parseIgnoresEscapedQuotesInsideStrings() {
        val text = "{\"a\":\"say \\\"hi\\\" [\"}"
        assertEquals(1, (XdrJson.parse(text, type) as JsonObject).size)
    }

    /**
     * Depth counts containers, since a container is what costs the decoder a stack frame. The
     * innermost value is a primitive and adds no level of its own.
     */
    @Test
    fun checkDepthAcceptsTreeAtLimit() {
        var element: JsonElement = JsonPrimitive(1)
        repeat(XdrJson.RECURSION_CAP) { element = JsonArray(listOf(element)) }
        XdrJson.checkDepth(element, type)
    }

    @Test
    fun checkDepthRejectsTreeBeyondLimit() {
        var element: JsonElement = JsonPrimitive(1)
        repeat(XdrJson.RECURSION_CAP + 8) { element = JsonArray(listOf(element)) }
        rejects { XdrJson.checkDepth(element, type) }
    }

    /**
     * Pins the boundary itself: one container past the cap is the first depth rejected. Without
     * this an off-by-one in either direction would still satisfy the at-limit and beyond-limit
     * cases.
     */
    @Test
    fun checkDepthRejectsTreeAtFirstExcessiveDepth() {
        var element: JsonElement = JsonPrimitive(1)
        repeat(XdrJson.RECURSION_CAP + 1) { element = JsonArray(listOf(element)) }
        rejects { XdrJson.checkDepth(element, type) }
    }

    /**
     * The limit belongs to the document, not to the entry point it arrives through, so text and
     * tree must admit and refuse the same depths. The document carries a primitive at the bottom,
     * which is where the two measurements could differ: the text form counts brackets and never
     * sees a leaf at all.
     */
    @Test
    fun parsedTextAndTreeAgreeOnTheDepthBoundary() {
        for (depth in XdrJson.RECURSION_CAP - 1..XdrJson.RECURSION_CAP + 1) {
            val text = "[".repeat(depth) + "1" + "]".repeat(depth)
            val textAccepted = runCatching { XdrJson.parse(text, type) }.isSuccess
            val treeAccepted = runCatching {
                var element: JsonElement = JsonPrimitive(1)
                repeat(depth) { element = JsonArray(listOf(element)) }
                XdrJson.checkDepth(element, type)
            }.isSuccess
            assertEquals(textAccepted, treeAccepted, "disagreement at depth $depth")
        }
    }

    @Test
    fun checkDepthAcceptsShallowTree() {
        XdrJson.checkDepth(buildJsonObject { put("a", JsonPrimitive(1)) }, type)
        XdrJson.checkDepth(JsonPrimitive(1), type)
    }

    @Test
    fun checkDepthMeasuresNestedObjects() {
        var element: JsonElement = JsonPrimitive(1)
        repeat(XdrJson.RECURSION_CAP + 1) { element = buildJsonObject { put("a", element) } }
        rejects { XdrJson.checkDepth(element, type) }
    }

    // -------------------------------------------------------------------------------------
    // 128-bit and 256-bit integers
    // -------------------------------------------------------------------------------------

    @Test
    fun int128RendersReassembledValue() {
        assertEquals("-18446744073709551615", XdrJson.int128ToDecimalString(-1, 1uL))
        assertEquals("0", XdrJson.int128ToDecimalString(0, 0uL))
        assertEquals("1", XdrJson.int128ToDecimalString(0, 1uL))
        assertEquals("-1", XdrJson.int128ToDecimalString(-1, ULong.MAX_VALUE))
    }

    @Test
    fun int128RendersExtremes() {
        assertEquals(
            "170141183460469231731687303715884105727",
            XdrJson.int128ToDecimalString(Long.MAX_VALUE, ULong.MAX_VALUE)
        )
        assertEquals(
            "-170141183460469231731687303715884105728",
            XdrJson.int128ToDecimalString(Long.MIN_VALUE, 0uL)
        )
    }

    @Test
    fun int128RoundTripsExtremes() {
        for (limbs in listOf(
            Long.MIN_VALUE to 0uL,
            Long.MAX_VALUE to ULong.MAX_VALUE,
            -1L to 1uL,
            0L to 0uL
        )) {
            val rendered = XdrJson.int128ToDecimalString(limbs.first, limbs.second)
            val parsed = XdrJson.decimalStringToInt128(JsonPrimitive(rendered), type, key)
            assertEquals(limbs.first, parsed.hi)
            assertEquals(limbs.second, parsed.lo)
        }
    }

    @Test
    fun int128RejectsValueBeyondRange() {
        rejects {
            XdrJson.decimalStringToInt128(
                JsonPrimitive("170141183460469231731687303715884105728"), type, key
            )
        }
        rejects {
            XdrJson.decimalStringToInt128(
                JsonPrimitive("-170141183460469231731687303715884105729"), type, key
            )
        }
    }

    @Test
    fun uint128RendersExtremes() {
        assertEquals("0", XdrJson.uint128ToDecimalString(0uL, 0uL))
        assertEquals(
            "340282366920938463463374607431768211455",
            XdrJson.uint128ToDecimalString(ULong.MAX_VALUE, ULong.MAX_VALUE)
        )
    }

    @Test
    fun uint128RoundTripsExtremes() {
        for (limbs in listOf(0uL to 0uL, ULong.MAX_VALUE to ULong.MAX_VALUE, 1uL to 2uL)) {
            val rendered = XdrJson.uint128ToDecimalString(limbs.first, limbs.second)
            val parsed = XdrJson.decimalStringToUInt128(JsonPrimitive(rendered), type, key)
            assertEquals(limbs.first, parsed.hi)
            assertEquals(limbs.second, parsed.lo)
        }
    }

    @Test
    fun uint128RejectsNegativeAndOverRange() {
        rejects { XdrJson.decimalStringToUInt128(JsonPrimitive("-1"), type, key) }
        rejects {
            XdrJson.decimalStringToUInt128(
                JsonPrimitive("340282366920938463463374607431768211456"), type, key
            )
        }
    }

    @Test
    fun int256RendersExtremes() {
        assertEquals(
            "57896044618658097711785492504343953926634992332820282019728792003956564819967",
            XdrJson.int256ToDecimalString(
                Long.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE
            )
        )
        assertEquals(
            "-57896044618658097711785492504343953926634992332820282019728792003956564819968",
            XdrJson.int256ToDecimalString(Long.MIN_VALUE, 0uL, 0uL, 0uL)
        )
        assertEquals(
            "-1",
            XdrJson.int256ToDecimalString(-1, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE)
        )
    }

    private fun assertInt256RoundTrips(hiHi: Long, hiLo: ULong, loHi: ULong, loLo: ULong) {
        val rendered = XdrJson.int256ToDecimalString(hiHi, hiLo, loHi, loLo)
        val parsed = XdrJson.decimalStringToInt256(JsonPrimitive(rendered), type, key)
        assertEquals(hiHi, parsed.hiHi)
        assertEquals(hiLo, parsed.hiLo)
        assertEquals(loHi, parsed.loHi)
        assertEquals(loLo, parsed.loLo)
    }

    @Test
    fun int256RoundTripsExtremes() {
        assertInt256RoundTrips(Long.MIN_VALUE, 0uL, 0uL, 0uL)
        assertInt256RoundTrips(Long.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE)
        assertInt256RoundTrips(-1L, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE)
        assertInt256RoundTrips(0L, 0uL, 0uL, 1uL)
    }

    @Test
    fun int256RoundTripsDistinctLimbs() {
        assertInt256RoundTrips(1L, 2uL, 3uL, 4uL)
        assertInt256RoundTrips(-2L, 3uL, 4uL, 5uL)
    }

    @Test
    fun int256RejectsValueBeyondRange() {
        rejects {
            XdrJson.decimalStringToInt256(
                JsonPrimitive(
                    "57896044618658097711785492504343953926634992332820282019728792003956564819968"
                ),
                type,
                key
            )
        }
    }

    @Test
    fun uint256RendersMaximum() {
        assertEquals(
            "115792089237316195423570985008687907853269984665640564039457584007913129639935",
            XdrJson.uint256ToDecimalString(
                ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE
            )
        )
    }

    @Test
    fun uint256RoundTripsMaximum() {
        val rendered = XdrJson.uint256ToDecimalString(
            ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE, ULong.MAX_VALUE
        )
        val parsed = XdrJson.decimalStringToUInt256(JsonPrimitive(rendered), type, key)
        assertEquals(ULong.MAX_VALUE, parsed.hiHi)
        assertEquals(ULong.MAX_VALUE, parsed.hiLo)
        assertEquals(ULong.MAX_VALUE, parsed.loHi)
        assertEquals(ULong.MAX_VALUE, parsed.loLo)
    }

    @Test
    fun uint256RoundTripsLimbBoundaries() {
        val parsed = XdrJson.decimalStringToUInt256(
            JsonPrimitive(XdrJson.uint256ToDecimalString(1uL, 2uL, 3uL, 4uL)), type, key
        )
        assertEquals(1uL, parsed.hiHi)
        assertEquals(2uL, parsed.hiLo)
        assertEquals(3uL, parsed.loHi)
        assertEquals(4uL, parsed.loLo)
    }

    @Test
    fun uint256RejectsNegativeAndOverRange() {
        rejects { XdrJson.decimalStringToUInt256(JsonPrimitive("-1"), type, key) }
        rejects {
            XdrJson.decimalStringToUInt256(
                JsonPrimitive(
                    "115792089237316195423570985008687907853269984665640564039457584007913129639936"
                ),
                type,
                key
            )
        }
    }

    @Test
    fun wideIntegersRejectNonCanonicalLiterals() {
        rejects { XdrJson.decimalStringToInt128(JsonPrimitive("007"), type, key) }
        rejects { XdrJson.decimalStringToInt128(JsonPrimitive("1.0"), type, key) }
        rejects { XdrJson.decimalStringToInt128(JsonPrimitive("0x10"), type, key) }
        rejects { XdrJson.decimalStringToInt128(JsonPrimitive(""), type, key) }
    }

    @Test
    fun wideIntegersRejectOverLongLiteralWithoutParsingIt() {
        rejects { XdrJson.decimalStringToInt128(JsonPrimitive("9".repeat(5000)), type, key) }
        rejects { XdrJson.decimalStringToUInt256(JsonPrimitive("9".repeat(5000)), type, key) }
    }

    @Test
    fun wideIntegersRejectNumberForm() {
        rejects { XdrJson.decimalStringToInt128(JsonPrimitive(1), type, key) }
        rejects { XdrJson.decimalStringToUInt256(JsonPrimitive(1), type, key) }
    }

    // -------------------------------------------------------------------------------------
    // Whole-value names: enum members, void union arms, strkeys
    // -------------------------------------------------------------------------------------

    @Test
    fun nameEncodesVerbatimWithoutTheEscapeLadder() {
        assertEquals("\"op_inner\"", XdrJson.encodeToString(XdrJson.name("op_inner")))
        assertEquals("\"\"", XdrJson.encodeToString(XdrJson.name("")))
    }

    @Test
    fun nameDecodesAJsonString() {
        assertEquals("u32", XdrJson.name(JsonPrimitive("u32"), type))
    }

    @Test
    fun nameRejectsAnythingButAString() {
        for (element in listOf(JsonPrimitive(1), JsonPrimitive(true), JsonNull, JsonArray(emptyList()))) {
            val error = rejects { XdrJson.name(element, type) }
            assertTrue(error.message!!.startsWith("$type: expects a JSON string"))
        }
    }

    @Test
    fun unknownMemberNamesTheEnumAndTheOffendingString() {
        val error = rejects { XdrJson.unknownMember("SCValTypeXdr", "u33") }
        assertEquals("SCValTypeXdr: has no member named \"u33\"", error.message)
    }

    @Test
    fun unknownArmNamesTheUnionAndTheOffendingKey() {
        val error = rejects { XdrJson.unknownArm("AssetXdr", "gold") }
        assertEquals("AssetXdr: has no arm named \"gold\"", error.message)
    }

    @Test
    fun unknownNamesAreEscapedAndTruncatedInMessages() {
        val error = rejects { XdrJson.unknownArm(type, "a\nb") }
        assertTrue(error.message!!.none { it == '\n' })
        assertTrue(rejects { XdrJson.unknownMember(type, "x".repeat(500)) }.message!!.length < 200)
    }

    // -------------------------------------------------------------------------------------
    // Integer-discriminated union arm keys
    // -------------------------------------------------------------------------------------

    @Test
    fun intArmReadsTheDiscriminantBackOutOfTheKey() {
        assertEquals(0, XdrJson.intArm(type, "v0"))
        assertEquals(1, XdrJson.intArm(type, "v1"))
        assertEquals(2147483647, XdrJson.intArm(type, "v2147483647"))
    }

    @Test
    fun intArmRejectsAKeyThatIsNotVFollowedByABaseTenInteger() {
        for (arm in listOf("v", "v-1", "v01", "v1.0", "0", "x1", "v1x", "V1", "")) {
            val error = rejects { XdrJson.intArm(type, arm) }
            assertTrue(error.message!!.contains("has no arm named"), "accepted $arm")
        }
    }

    @Test
    fun intArmRejectsAValueTooLargeForAnInt() {
        rejects { XdrJson.intArm(type, "v2147483648") }
    }

    // -------------------------------------------------------------------------------------
    // Strkey adaptation
    // -------------------------------------------------------------------------------------

    @Test
    fun strkeyReturnsWhatTheCodecDecodes() {
        val decoded = XdrJson.strkey("GABC", type, "a G strkey") { byteArrayOf(1, 2, 3) }
        assertEquals(listOf<Byte>(1, 2, 3), decoded.toList())
    }

    @Test
    fun strkeyReportsACodecFailureUnderTheUniformContract() {
        val error = rejects {
            XdrJson.strkey("nonsense", type, "a G strkey") { throw IllegalStateException("boom") }
        }
        assertEquals("$type: expects a G strkey, got \"nonsense\"", error.message)
    }

    @Test
    fun strkeyEscapesAndTruncatesTheRejectedText() {
        val error = rejects {
            XdrJson.strkey("a\nb", type, "a G strkey") { throw IllegalArgumentException() }
        }
        assertTrue(error.message!!.none { it == '\n' })
    }

    // -------------------------------------------------------------------------------------
    // Asset codes
    // -------------------------------------------------------------------------------------

    @Test
    fun trimAssetCodeDropsTrailingPadding() {
        assertEquals("ABC", XdrJson.trimAssetCode(byteArrayOf(65, 66, 67, 0), 0).decodeToString())
        assertEquals("ABCD", XdrJson.trimAssetCode(byteArrayOf(65, 66, 67, 68), 0).decodeToString())
        assertEquals(0, XdrJson.trimAssetCode(ByteArray(4), 0).size)
    }

    @Test
    fun trimAssetCodeKeepsTheMinimumWidth() {
        val twelve = byteArrayOf(65, 66, 67) + ByteArray(9)
        assertEquals(5, XdrJson.trimAssetCode(twelve, 5).size)
        assertEquals(listOf<Byte>(65, 66, 67, 0, 0), XdrJson.trimAssetCode(twelve, 5).toList())
        assertEquals(5, XdrJson.trimAssetCode(ByteArray(12), 5).size)
        assertEquals(12, XdrJson.trimAssetCode(ByteArray(12) { 65 }, 5).size)
    }

    @Test
    fun padAssetCodeRestoresTheFixedWidth() {
        val padded = XdrJson.padAssetCode(byteArrayOf(65, 66, 67), type, 4)
        assertEquals(4, padded.size)
        assertEquals(0, padded[3])
        assertEquals(12, XdrJson.padAssetCode(byteArrayOf(65), type, 12).size)
    }

    @Test
    fun padAssetCodeRejectsACodeThatDoesNotFit() {
        val error = rejects { XdrJson.padAssetCode(ByteArray(5), type, 4) }
        assertTrue(error.message!!.contains("at most 4 bytes"))
    }

    // -------------------------------------------------------------------------------------
    // Strkey payload packing
    // -------------------------------------------------------------------------------------

    @Test
    fun muxedPayloadPutsTheKeyFirstAndTheIdLast() {
        val key = ByteArray(32) { 7 }
        val payload = XdrJson.muxedPayload(key, 1uL)
        assertEquals(40, payload.size)
        assertEquals(listOf<Byte>(7, 7), payload.toList().subList(30, 32))
        assertEquals(listOf<Byte>(0, 0, 0, 0, 0, 0, 0, 1), payload.toList().subList(32, 40))
    }

    @Test
    fun muxedIdReadsBackEveryValue() {
        for (id in listOf(0uL, 1uL, 255uL, 256uL, ULong.MAX_VALUE)) {
            assertEquals(id, XdrJson.muxedId(XdrJson.muxedPayload(ByteArray(32), id)))
        }
    }

    @Test
    fun muxedPayloadRequiresAThirtyTwoByteKey() {
        rejects { XdrJson.muxedPayload(ByteArray(31), 0uL) }
        rejects { XdrJson.muxedPayload(ByteArray(33), 0uL) }
    }

    @Test
    fun signedPayloadKeyDeclaresTheLengthAndPadsToAFourByteBoundary() {
        val key = XdrJson.signedPayloadKey(ByteArray(32), byteArrayOf(1, 2, 3), type)
        assertEquals(40, key.size)
        assertEquals(listOf<Byte>(0, 0, 0, 3), key.toList().subList(32, 36))
        assertEquals(0, key[39])
        assertEquals(100, XdrJson.signedPayloadKey(ByteArray(32), ByteArray(64), type).size)
    }

    /**
     * The strkey codec accepts a packed key of 40 to 100 bytes. Padding a payload to a four-byte
     * boundary puts every length XDR admits inside that window, so the packed size is the
     * property worth pinning at the ends of the range.
     */
    @Test
    fun signedPayloadKeyPacksEveryLengthIntoTheStrkeyRange() {
        for (length in 1..64) {
            val size = XdrJson.signedPayloadKey(ByteArray(32), ByteArray(length), type).size
            assertTrue(size in 40..100, "payload of $length bytes packed to $size")
        }
    }

    @Test
    fun signedPayloadKeyRejectsAnEmptyPayload() {
        val error = rejects { XdrJson.signedPayloadKey(ByteArray(32), ByteArray(0), type) }
        assertEquals("$type: has an empty signed payload, which has no strkey form", error.message)
    }

    @Test
    fun signedPayloadReadsBackEveryLength() {
        for (length in 1..64) {
            val payload = ByteArray(length) { (it and 0xFF).toByte() }
            val key = XdrJson.signedPayloadKey(ByteArray(32), payload, type)
            assertEquals(payload.toList(), XdrJson.signedPayload(key, type).toList())
        }
    }

    @Test
    fun signedPayloadRequiresAThirtyTwoByteKey() {
        rejects { XdrJson.signedPayloadKey(ByteArray(31), ByteArray(4), type) }
    }

    @Test
    fun signedPayloadRejectsADeclaredLengthThatDoesNotFit() {
        val key = XdrJson.signedPayloadKey(ByteArray(32), byteArrayOf(1, 2, 3, 4), type)
        key[35] = 9
        val error = rejects { XdrJson.signedPayload(key, type) }
        assertTrue(error.message!!.contains("does not fit"))
    }

    // -------------------------------------------------------------------------------------
    // Error reporting
    // -------------------------------------------------------------------------------------

    @Test
    fun failNamesTheTypeAndDetail() {
        val error = rejects { XdrJson.fail("SomeXdr", "detail text") }
        assertEquals("SomeXdr: detail text", error.message)
    }

    @Test
    fun previewRendersShortValuesInFull() {
        assertEquals("\"abc\"", XdrJson.preview(JsonPrimitive("abc")))
        assertEquals("12", XdrJson.preview(JsonPrimitive(12)))
    }

    @Test
    fun previewTruncatesLongValues() {
        val rendered = XdrJson.preview(JsonPrimitive("x".repeat(500)))
        assertTrue(rendered.length <= 83)
        assertTrue(rendered.endsWith("..."))
    }

    @Test
    fun previewLeavesNoRawControlBytes() {
        for (code in 0x00..0x1F) {
            val rendered = XdrJson.preview(JsonPrimitive("a${code.toChar()}b"))
            assertTrue(
                rendered.none { it.code < 0x20 },
                "control byte $code survived into a message preview"
            )
        }
    }

    @Test
    fun previewLeavesNoRawNewlineFromNestedValues() {
        val value = buildJsonObject { put("a", JsonPrimitive("line1\nline2")) }
        assertTrue(XdrJson.preview(value).none { it == '\n' })
    }

    @Test
    fun previewEscapesDeleteCharacter() {
        assertTrue(XdrJson.preview(JsonPrimitive("a\u007Fb")).contains("\\x7f"))
    }

    @Test
    fun errorMessagesQuoteTheOffendingKey() {
        val error = rejects { XdrJson.int32(JsonPrimitive("bad"), "TxXdr", "sequence") }
        assertTrue(error.message!!.startsWith("TxXdr: "))
        assertTrue(error.message!!.contains("sequence"))
    }

    @Test
    fun errorMessagesTruncateUntrustedValues() {
        val error = rejects { XdrJson.int64(JsonPrimitive("9".repeat(500)), type, key) }
        assertTrue(error.message!!.length < 200)
    }
}
