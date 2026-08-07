package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.xdr.AccountEntryExtensionV2ExtXdr
import com.soneso.stellar.sdk.xdr.AccountEntryExtensionV2Xdr
import com.soneso.stellar.sdk.xdr.AssetTypeXdr
import com.soneso.stellar.sdk.xdr.AssetXdr
import com.soneso.stellar.sdk.xdr.Curve25519PublicXdr
import com.soneso.stellar.sdk.xdr.HashXdr
import com.soneso.stellar.sdk.xdr.Int32Xdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.SCSpecUDTStructFieldV0Xdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.ShortHashSeedXdr
import com.soneso.stellar.sdk.xdr.SignatureXdr
import com.soneso.stellar.sdk.xdr.TTLEntryXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins what the XDR-JSON (SEP-0051) decoder refuses, asserted through the generated types rather
 * than through the shared runtime, so each refusal is reached the way a document reaches it.
 *
 * Decoding accepts only the exact spelling encoding produces: hexadecimal is lowercase and of
 * even length, the escape ladder uses lowercase `\xNN` and no other escape, an integer is a plain
 * base-10 literal within the range of its bit size, a struct carries every key it declares and no
 * key it does not, and a union carries exactly one arm it declares. Every refusal raises
 * [IllegalArgumentException] naming the type, and the offending key where the value sits under
 * one.
 */
class Sep51NegativeInputTest {

    private fun rejects(block: () -> Unit): IllegalArgumentException =
        assertFailsWith<IllegalArgumentException>(block = block)

    /** A hexadecimal string of [bytes] bytes, all zero. */
    private fun hex(bytes: Int): String = "00".repeat(bytes)

    // -------------------------------------------------------------------------------------
    // Hexadecimal
    // -------------------------------------------------------------------------------------

    @Test
    fun hexadecimalRejectsUppercaseDigits() {
        rejects { HashXdr.fromXdrJsonElement(JsonPrimitive("AB" + hex(31))) }
        rejects { HashXdr.fromXdrJsonElement(JsonPrimitive("aB" + hex(31))) }
    }

    @Test
    fun hexadecimalRejectsAnOddNumberOfDigits() {
        val error = rejects { SignatureXdr.fromXdrJsonElement(JsonPrimitive("abc")) }
        assertTrue(error.message!!.contains("even length"), error.message!!)
    }

    @Test
    fun hexadecimalRejectsACharacterThatIsNotAHexadecimalDigit() {
        rejects { SignatureXdr.fromXdrJsonElement(JsonPrimitive("zz")) }
        rejects { SignatureXdr.fromXdrJsonElement(JsonPrimitive("0g")) }
        rejects { SignatureXdr.fromXdrJsonElement(JsonPrimitive("  ")) }
    }

    @Test
    fun hexadecimalNamesTheTypeAndTheOffendingKey() {
        val error = rejects { ShortHashSeedXdr.fromXdrJson("{\"seed\":\"ZZ\"}") }
        assertTrue(error.message!!.startsWith("ShortHashSeedXdr: "), error.message!!)
        assertTrue(error.message!!.contains("\"seed\""), error.message!!)
    }

    /**
     * SEP-0051 §Fixed-Length Opaque Data renders such a field as a hexadecimal string whether
     * the `.x` declares the opaque inline or through a named typedef, and the inline case is the
     * one the specification shows. The array-of-byte-values spelling some tooling emits for it
     * is not a second accepted form.
     */
    @Test
    fun anInlineFixedLengthOpaqueRejectsAnArrayOfByteValues() {
        val seedError = rejects {
            ShortHashSeedXdr.fromXdrJsonElement(
                buildJsonObject {
                    put("seed", buildJsonArray { repeat(16) { add(JsonPrimitive(0)) } })
                }
            )
        }
        assertTrue(seedError.message!!.startsWith("ShortHashSeedXdr: "), seedError.message!!)
        assertTrue(seedError.message!!.contains("\"seed\""), seedError.message!!)

        val keyError = rejects {
            Curve25519PublicXdr.fromXdrJsonElement(
                buildJsonObject {
                    put("key", buildJsonArray { repeat(32) { add(JsonPrimitive(0)) } })
                }
            )
        }
        assertTrue(keyError.message!!.startsWith("Curve25519PublicXdr: "), keyError.message!!)
        assertTrue(keyError.message!!.contains("\"key\""), keyError.message!!)
    }

    @Test
    fun aFixedLengthOpaqueRejectsTooFewBytes() {
        val error = rejects { HashXdr.fromXdrJsonElement(JsonPrimitive(hex(31))) }
        assertTrue(error.message!!.contains("exactly 32 bytes"), error.message!!)
    }

    @Test
    fun aFixedLengthOpaqueRejectsTooManyBytes() {
        rejects { HashXdr.fromXdrJsonElement(JsonPrimitive(hex(33))) }
    }

    @Test
    fun aFixedLengthOpaqueRejectsAnEmptyString() {
        rejects { HashXdr.fromXdrJsonElement(JsonPrimitive("")) }
    }

    @Test
    fun aVariableLengthOpaqueRejectsMoreBytesThanItsMaximum() {
        val error = rejects { SignatureXdr.fromXdrJsonElement(JsonPrimitive(hex(65))) }
        assertTrue(error.message!!.contains("at most 64 bytes"), error.message!!)
    }

    @Test
    fun aVariableLengthOpaqueAcceptsExactlyItsMaximum() {
        assertEquals(64, SignatureXdr.fromXdrJsonElement(JsonPrimitive(hex(64))).value.size)
    }

    @Test
    fun hexadecimalRejectsAValueThatIsNotAJsonString() {
        rejects { SignatureXdr.fromXdrJsonElement(JsonPrimitive(1)) }
        rejects { SignatureXdr.fromXdrJsonElement(JsonNull) }
        rejects { SignatureXdr.fromXdrJsonElement(buildJsonArray { }) }
    }

    // -------------------------------------------------------------------------------------
    // The string escape ladder
    // -------------------------------------------------------------------------------------

    @Test
    fun theEscapeLadderRejectsAnUppercaseHexEscape() {
        val error = rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("\\xC3")) }
        assertTrue(error.message!!.startsWith("SCSymbolXdr: "), error.message!!)
        rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("\\x3C")) }
    }

    @Test
    fun theEscapeLadderRejectsAnUnrecognisedEscape() {
        val error = rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("\\q")) }
        assertTrue(error.message!!.contains("unrecognised escape"), error.message!!)
        rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("\\u0041")) }
    }

    @Test
    fun theEscapeLadderRejectsATrailingBackslash() {
        val error = rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("abc\\")) }
        assertTrue(error.message!!.contains("trailing backslash"), error.message!!)
    }

    @Test
    fun theEscapeLadderRejectsATruncatedHexEscape() {
        rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("\\x")) }
        rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("\\xc")) }
    }

    @Test
    fun theEscapeLadderRejectsAnUnescapedByteOutsideThePrintableRange() {
        rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("a\u0000b")) }
        rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("a\u0009b")) }
        rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("café")) }
    }

    @Test
    fun theEscapeLadderRejectsTextLongerThanItsDeclaredMaximum() {
        val error = rejects { SCSymbolXdr.fromXdrJsonElement(JsonPrimitive("a".repeat(33))) }
        assertTrue(error.message!!.contains("at most 32 bytes"), error.message!!)
    }

    @Test
    fun theEscapeLadderNamesTheTypeAndTheOffendingKey() {
        val error = rejects { SCValXdr.fromXdrJson("{\"symbol\":\"\\\\q\"}") }
        assertTrue(error.message!!.startsWith("SCSymbolXdr: "), error.message!!)
        assertTrue(error.message!!.contains("\"value\""), error.message!!)
    }

    // -------------------------------------------------------------------------------------
    // 64-bit integers
    // -------------------------------------------------------------------------------------

    @Test
    fun aHyperRejectsExponentNotation() {
        rejects { Int64Xdr.fromXdrJson("1e10") }
        rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive("1e10")) }
    }

    @Test
    fun aHyperRejectsADecimalPoint() {
        rejects { Int64Xdr.fromXdrJson("1.0") }
        rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive("1.0")) }
    }

    @Test
    fun aHyperRejectsHexadecimalNotation() {
        rejects { Int64Xdr.fromXdrJson("0x10") }
        rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive("0x10")) }
    }

    @Test
    fun aHyperRejectsALeadingPlus() {
        rejects { Int64Xdr.fromXdrJson("+1") }
        rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive("+1")) }
    }

    @Test
    fun aHyperRejectsALeadingZero() {
        rejects { Int64Xdr.fromXdrJson("007") }
        rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive("007")) }
        rejects { Uint64Xdr.fromXdrJsonElement(JsonPrimitive("00")) }
    }

    @Test
    fun aHyperRejectsANegativeZero() {
        rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive("-0")) }
    }

    @Test
    fun aHyperRejectsAnEmptyString() {
        rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive("")) }
    }

    @Test
    fun aHyperRejectsAValueAboveItsMaximum() {
        val error = rejects {
            Int64Xdr.fromXdrJsonElement(JsonPrimitive("9223372036854775808"))
        }
        assertTrue(error.message!!.contains("out of range"), error.message!!)
    }

    @Test
    fun aHyperRejectsAValueBelowItsMinimum() {
        rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive("-9223372036854775809")) }
    }

    @Test
    fun anUnsignedHyperRejectsANegativeValue() {
        rejects { Uint64Xdr.fromXdrJsonElement(JsonPrimitive("-1")) }
    }

    @Test
    fun anUnsignedHyperRejectsAValueAboveItsMaximum() {
        rejects { Uint64Xdr.fromXdrJsonElement(JsonPrimitive("18446744073709551616")) }
    }

    @Test
    fun aHyperRejectsABooleanAndNull() {
        rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive(true)) }
        rejects { Int64Xdr.fromXdrJsonElement(JsonNull) }
    }

    @Test
    fun aHyperNamesTheTypeAndTheOffendingKey() {
        val error = rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive("1.0")) }
        assertTrue(error.message!!.startsWith("Int64Xdr: "), error.message!!)
        assertTrue(error.message!!.contains("\"value\""), error.message!!)
    }

    // -------------------------------------------------------------------------------------
    // 32-bit integers
    // -------------------------------------------------------------------------------------

    @Test
    fun anUnsignedIntegerRejectsTheStringForm() {
        val error = rejects { Uint32Xdr.fromXdrJsonElement(JsonPrimitive("1")) }
        assertTrue(error.message!!.contains("as a JSON number"), error.message!!)
    }

    @Test
    fun aSignedIntegerRejectsTheStringForm() {
        rejects { Int32Xdr.fromXdrJsonElement(JsonPrimitive("1")) }
    }

    @Test
    fun anUnsignedIntegerRejectsAValueAboveItsMaximum() {
        rejects { Uint32Xdr.fromXdrJson("4294967296") }
    }

    @Test
    fun anUnsignedIntegerRejectsANegativeValue() {
        rejects { Uint32Xdr.fromXdrJson("-1") }
    }

    @Test
    fun aSignedIntegerRejectsAValueOutsideItsRange() {
        rejects { Int32Xdr.fromXdrJson("2147483648") }
        rejects { Int32Xdr.fromXdrJson("-2147483649") }
    }

    @Test
    fun aSignedIntegerRejectsALiteralThatIsNotAWholeNumber() {
        rejects { Int32Xdr.fromXdrJson("1.0") }
        rejects { Int32Xdr.fromXdrJson("1e5") }
    }

    // -------------------------------------------------------------------------------------
    // Structs
    // -------------------------------------------------------------------------------------

    @Test
    fun aStructRejectsAMissingKey() {
        val error = rejects { TTLEntryXdr.fromXdrJson("{\"key_hash\":\"${hex(32)}\"}") }
        assertEquals(
            "TTLEntryXdr: is missing the required key \"live_until_ledger_seq\"",
            error.message
        )
    }

    @Test
    fun aStructRejectsAJsonArrayInPlaceOfItsObject() {
        val error = rejects { TTLEntryXdr.fromXdrJsonElement(buildJsonArray { }) }
        assertTrue(error.message!!.contains("expects a JSON object"), error.message!!)
    }

    @Test
    fun aStructRejectsABareStringInPlaceOfItsObject() {
        rejects { TTLEntryXdr.fromXdrJsonElement(JsonPrimitive("ttl_entry")) }
    }

    @Test
    fun aStructRejectsNullInPlaceOfItsObject() {
        rejects { TTLEntryXdr.fromXdrJsonElement(JsonNull) }
    }

    @Test
    fun aStructRejectsNullForAMemberThatIsNotOptional() {
        val document = "{\"key_hash\":null,\"live_until_ledger_seq\":1}"
        val error = rejects { TTLEntryXdr.fromXdrJson(document) }
        assertTrue(error.message!!.contains("got null"), error.message!!)
    }

    @Test
    fun aStructRejectsAnUnknownKeyStandingInForADeclaredOne() {
        val document = "{\"key_hash\":\"${hex(32)}\",\"live_until\":1}"
        val error = rejects { TTLEntryXdr.fromXdrJson(document) }
        assertEquals("TTLEntryXdr: has the unknown key \"live_until\"", error.message)
    }

    @Test
    fun aStructRejectsAKeyThatNamesNoField() {
        val document =
            "{\"key_hash\":\"${hex(32)}\",\"live_until_ledger_seq\":1,\"note\":\"anything\"}"
        val error = rejects { TTLEntryXdr.fromXdrJson(document) }
        assertEquals("TTLEntryXdr: has the unknown key \"note\"", error.message)
    }

    @Test
    fun aStructNamesEveryUnknownKeyItCarries() {
        val document = "{\"alpha\":1,\"key_hash\":\"${hex(32)}\"," +
            "\"live_until_ledger_seq\":1,\"omega\":2}"
        val error = rejects { TTLEntryXdr.fromXdrJson(document) }
        assertEquals("TTLEntryXdr: has the unknown keys \"alpha\", \"omega\"", error.message)
    }

    /**
     * A hostile document can carry more unknown keys than any message should repeat, so the
     * message names a bounded prefix and counts the rest.
     */
    @Test
    fun aStructCountsUnknownKeysBeyondTheOnesItNames() {
        val extras = (1..8).joinToString(",") { "\"k$it\":1" }
        val document = "{\"key_hash\":\"${hex(32)}\",\"live_until_ledger_seq\":1,$extras}"
        val error = rejects { TTLEntryXdr.fromXdrJson(document) }
        assertEquals(
            "TTLEntryXdr: has the unknown keys \"k1\", \"k2\", \"k3\", \"k4\", \"k5\" and 3 more",
            error.message
        )
    }

    /**
     * A key name reaches the message through the same rendering every other untrusted value
     * uses, so it cannot carry a line break or a terminal control sequence into a log. A newline
     * is already escaped by the JSON rendering; a delete character survives it and is escaped
     * after it.
     */
    @Test
    fun aStructEscapesAnUnknownKeyItNames() {
        fun messageFor(key: String): String? {
            val document = buildJsonObject {
                put("key_hash", JsonPrimitive(hex(32)))
                put("live_until_ledger_seq", JsonPrimitive(1))
                put(key, JsonPrimitive(1))
            }
            return rejects { TTLEntryXdr.fromXdrJsonElement(document) }.message
        }
        assertEquals("TTLEntryXdr: has the unknown key \"a\\nb\"", messageFor("a\nb"))
        assertEquals("TTLEntryXdr: has the unknown key \"a\\x7fb\"", messageFor("a\u007Fb"))
    }

    /**
     * `type` and `type_` are two spellings of one key, so a document supplying both states the
     * field twice rather than once, and neither spelling gets to win by position.
     */
    @Test
    fun aStructRejectsBothSpellingsOfAKeyThatHasAHistoricalOne() {
        val error = rejects {
            SCSpecUDTStructFieldV0Xdr.fromXdrJson(
                "{\"doc\":\"d\",\"name\":\"n\",\"type\":\"val\",\"type_\":\"val\"}"
            )
        }
        assertEquals(
            "SCSpecUDTStructFieldV0Xdr: has both \"type\" and \"type_\", " +
                "which are two spellings of one key",
            error.message
        )
    }

    /** Either spelling alone names the declared field, so neither is an unknown key. */
    @Test
    fun aStructAcceptsEitherSpellingOfAKeyThatHasAHistoricalOne() {
        val canonical = SCSpecUDTStructFieldV0Xdr
            .fromXdrJson("{\"doc\":\"d\",\"name\":\"n\",\"type\":\"val\"}")
        val historical = SCSpecUDTStructFieldV0Xdr
            .fromXdrJson("{\"doc\":\"d\",\"name\":\"n\",\"type_\":\"val\"}")
        assertEquals(canonical, historical)
        assertEquals("n", canonical.name)
    }

    // -------------------------------------------------------------------------------------
    // Arrays
    // -------------------------------------------------------------------------------------

    @Test
    fun anArrayRejectsMoreElementsThanItsDeclaredMaximum() {
        val nulls = List(21) { "null" }.joinToString(",")
        val document = "{\"num_sponsored\":0,\"num_sponsoring\":0," +
            "\"signer_sponsoring_i_ds\":[$nulls],\"ext\":\"v0\"}"
        val error = rejects { AccountEntryExtensionV2Xdr.fromXdrJson(document) }
        assertTrue(error.message!!.contains("at most 20 elements"), error.message!!)
    }

    @Test
    fun anArrayRejectsAnObjectInPlaceOfItsElements() {
        val document = "{\"num_sponsored\":0,\"num_sponsoring\":0," +
            "\"signer_sponsoring_i_ds\":{},\"ext\":\"v0\"}"
        val error = rejects { AccountEntryExtensionV2Xdr.fromXdrJson(document) }
        assertTrue(error.message!!.contains("expects a JSON array"), error.message!!)
    }

    @Test
    fun anArrayRejectsABareStringInPlaceOfItsElements() {
        val document = "{\"num_sponsored\":0,\"num_sponsoring\":0," +
            "\"signer_sponsoring_i_ds\":\"none\",\"ext\":\"v0\"}"
        rejects { AccountEntryExtensionV2Xdr.fromXdrJson(document) }
    }

    // -------------------------------------------------------------------------------------
    // Enums
    // -------------------------------------------------------------------------------------

    @Test
    fun anEnumRejectsAMemberNameItDoesNotDeclare() {
        val error = rejects { AssetTypeXdr.fromXdrJsonElement(JsonPrimitive("gold")) }
        assertEquals("AssetTypeXdr: has no member named \"gold\"", error.message)
    }

    @Test
    fun anEnumRejectsItsMemberNameInTheWrongCase() {
        rejects { AssetTypeXdr.fromXdrJsonElement(JsonPrimitive("NATIVE")) }
    }

    @Test
    fun anEnumRejectsAnObjectWhereABareStringIsRequired() {
        val error = rejects { AssetTypeXdr.fromXdrJsonElement(buildJsonObject { }) }
        assertTrue(error.message!!.contains("expects a JSON string"), error.message!!)
    }

    @Test
    fun anEnumRejectsANumberWhereABareStringIsRequired() {
        rejects { AssetTypeXdr.fromXdrJsonElement(JsonPrimitive(0)) }
        rejects { AssetTypeXdr.fromXdrJsonElement(JsonNull) }
    }

    // -------------------------------------------------------------------------------------
    // Unions
    // -------------------------------------------------------------------------------------

    @Test
    fun aUnionRejectsAnArmItDoesNotDeclare() {
        val error = rejects {
            AssetXdr.fromXdrJsonElement(
                buildJsonObject { put("gold", buildJsonObject { }) }
            )
        }
        assertEquals("AssetXdr: has no arm named \"gold\"", error.message)
    }

    @Test
    fun aUnionRejectsAVoidArmNameItDoesNotDeclare() {
        rejects { AssetXdr.fromXdrJsonElement(JsonPrimitive("gold")) }
    }

    @Test
    fun aUnionRejectsAnObjectCarryingTwoArms() {
        val document = "{\"credit_alphanum4\":{\"asset_code\":\"AB\",\"issuer\":\"$ISSUER\"}," +
            "\"credit_alphanum12\":{\"asset_code\":\"ABCDE\",\"issuer\":\"$ISSUER\"}}"
        val error = rejects { AssetXdr.fromXdrJson(document) }
        assertTrue(error.message!!.contains("exactly one key"), error.message!!)
    }

    @Test
    fun aUnionRejectsAnObjectCarryingNoArm() {
        rejects { AssetXdr.fromXdrJsonElement(buildJsonObject { }) }
    }

    @Test
    fun aUnionRejectsAValueArmSpelledAsABareString() {
        val error = rejects { AssetXdr.fromXdrJsonElement(JsonPrimitive("credit_alphanum4")) }
        assertEquals("AssetXdr: has no arm named \"credit_alphanum4\"", error.message)
    }

    @Test
    fun aUnionRejectsAVoidArmSpelledAsAnObject() {
        val error = rejects {
            AssetXdr.fromXdrJsonElement(buildJsonObject { put("native", buildJsonObject { }) })
        }
        assertEquals("AssetXdr: has no arm named \"native\"", error.message)
    }

    @Test
    fun aUnionRejectsAJsonArrayInPlaceOfItsArm() {
        rejects { AssetXdr.fromXdrJsonElement(buildJsonArray { }) }
        rejects { AssetXdr.fromXdrJsonElement(JsonNull) }
    }

    @Test
    fun anIntegerCasedUnionRejectsACaseNumberItDoesNotDeclare() {
        val error = rejects {
            AccountEntryExtensionV2ExtXdr.fromXdrJsonElement(
                buildJsonObject { put("v1", buildJsonObject { }) }
            )
        }
        assertEquals("AccountEntryExtensionV2ExtXdr: has no arm named \"v1\"", error.message)
        rejects { AccountEntryExtensionV2ExtXdr.fromXdrJsonElement(JsonPrimitive("v1")) }
    }

    @Test
    fun aBooleanArmRejectsTheStringForm() {
        val error = rejects {
            SCValXdr.fromXdrJsonElement(buildJsonObject { put("bool", JsonPrimitive("true")) })
        }
        assertTrue(error.message!!.startsWith("SCValXdr: "), error.message!!)
        assertTrue(error.message!!.contains("\"bool\""), error.message!!)
    }

    // -------------------------------------------------------------------------------------
    // The text form
    // -------------------------------------------------------------------------------------

    @Test
    fun textThatIsNotJsonIsRejected() {
        val error = rejects { TTLEntryXdr.fromXdrJson("not json") }
        assertTrue(error.message!!.startsWith("TTLEntryXdr: "), error.message!!)
        assertTrue(error.message!!.contains("not valid JSON"), error.message!!)
    }

    @Test
    fun anEmptyDocumentIsRejected() {
        rejects { TTLEntryXdr.fromXdrJson("") }
        rejects { TTLEntryXdr.fromXdrJson("   ") }
    }

    @Test
    fun anUnterminatedDocumentIsRejected() {
        rejects { TTLEntryXdr.fromXdrJson("{\"key_hash\":\"${hex(32)}\"") }
    }

    @Test
    fun trailingContentAfterACompleteDocumentIsRejected() {
        val error = rejects { TTLEntryXdr.fromXdrJson("$TTL_ENTRY_JSON{}") }
        assertTrue(error.message!!.contains("not valid JSON"), error.message!!)
    }

    @Test
    fun trailingContentAfterACompletePrimitiveIsRejected() {
        rejects { Uint32Xdr.fromXdrJson("1 2") }
    }

    @Test
    fun theSameDocumentWithoutTheTrailingContentIsAccepted() {
        assertEquals(1u, TTLEntryXdr.fromXdrJson(TTL_ENTRY_JSON).liveUntilLedgerSeq.value)
    }

    // -------------------------------------------------------------------------------------
    // Repeated keys
    // -------------------------------------------------------------------------------------

    /**
     * SEP-0051 gives a value one document, so an object naming a key twice describes two values
     * where the format admits one. Reading either occurrence would discard the other silently,
     * so the document is refused instead.
     *
     * The refusal belongs to the text form alone. A tree handed to `fromXdrJsonElement` reaches
     * the decoder as a map, which resolved any repetition before the decoder could see it.
     */
    @Test
    fun aRepeatedKeyIsRejected() {
        val document = "{\"key_hash\":\"${hex(32)}\",\"live_until_ledger_seq\":1," +
            "\"live_until_ledger_seq\":2}"
        val error = rejects { TTLEntryXdr.fromXdrJson(document) }
        assertEquals("TTLEntryXdr: repeats the key \"live_until_ledger_seq\"", error.message)
    }

    /** Two occurrences agreeing is still two occurrences; the count is what the format fixes. */
    @Test
    fun aRepeatedKeyIsRejectedEvenWhenBothOccurrencesAgree() {
        val document = "{\"key_hash\":\"${hex(32)}\",\"live_until_ledger_seq\":1," +
            "\"live_until_ledger_seq\":1}"
        rejects { TTLEntryXdr.fromXdrJson(document) }
    }

    /**
     * Keys compare after their escapes resolve, so a repetition cannot be smuggled past the check
     * by writing one occurrence as escapes. Both spellings name `live_until_ledger_seq`.
     */
    @Test
    fun aRepeatedKeySpelledWithEscapesIsRejected() {
        val escaped = "\\u006cive_until_ledger_seq"
        val document = "{\"key_hash\":\"${hex(32)}\",\"live_until_ledger_seq\":1,\"$escaped\":2}"
        val error = rejects { TTLEntryXdr.fromXdrJson(document) }
        assertEquals("TTLEntryXdr: repeats the key \"live_until_ledger_seq\"", error.message)
    }

    /** An escaped spelling that repeats nothing names its field and decodes. */
    @Test
    fun anEscapedKeySpellingIsAcceptedOnItsOwn() {
        val escaped = "\\u006cive_until_ledger_seq"
        val document = "{\"key_hash\":\"${hex(32)}\",\"$escaped\":7}"
        assertEquals(7u, TTLEntryXdr.fromXdrJson(document).liveUntilLedgerSeq.value)
    }

    @Test
    fun aRepeatedArmKeyOnAUnionIsRejected() {
        rejects { AssetXdr.fromXdrJson("{\"credit_alphanum4\":1,\"credit_alphanum4\":2}") }
    }

    @Test
    fun aRepeatedKeyInsideANestedObjectIsRejected() {
        val document = "{\"map\":[{\"key\":{\"u32\":1,\"u32\":2},\"val\":\"void\"}]}"
        val error = rejects { SCValXdr.fromXdrJson(document) }
        assertEquals("SCValXdr: repeats the key \"u32\"", error.message)
    }

    @Test
    fun aRepeatedKeyInsideAnArrayElementIsRejected() {
        val document = "{\"map\":[{\"key\":\"void\",\"key\":\"void\",\"val\":\"void\"}]}"
        rejects { SCValXdr.fromXdrJson(document) }
    }

    /**
     * The check is scoped to one object, so the shape every element of an array shares is not
     * mistaken for a repetition, and neither is a nested value reusing the key that carries it.
     */
    @Test
    fun theSameKeyInSeparateObjectsIsAccepted() {
        val document = "{\"map\":[{\"key\":\"void\",\"val\":\"void\"}," +
            "{\"key\":\"void\",\"val\":\"void\"}]}"
        val decoded = SCValXdr.fromXdrJson(document)
        assertEquals(2, (decoded as SCValXdr.Map).value!!.value.size)
    }

    @Test
    fun aNestedObjectRepeatingTheKeyOfItsParentIsAccepted() {
        val inner = "{\"map\":[{\"key\":\"void\",\"val\":\"void\"}]}"
        val document = "{\"map\":[{\"key\":$inner,\"val\":\"void\"}]}"
        val decoded = SCValXdr.fromXdrJson(document)
        assertEquals(1, (decoded as SCValXdr.Map).value!!.value.size)
    }

    // -------------------------------------------------------------------------------------
    // Message shape
    // -------------------------------------------------------------------------------------

    @Test
    fun aRejectionTruncatesAnOverlongValue() {
        val error = rejects { Int64Xdr.fromXdrJsonElement(JsonPrimitive("9".repeat(500))) }
        assertTrue(error.message!!.length < 200, error.message!!)
    }

    @Test
    fun aRejectionLeavesNoRawControlBytesInItsMessage() {
        val error = rejects { AssetTypeXdr.fromXdrJsonElement(JsonPrimitive("a\nb")) }
        assertTrue(error.message!!.none { it.code < 0x20 }, error.message!!)
    }

    private companion object {
        const val ISSUER: String =
            "GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF"

        const val TTL_ENTRY_JSON: String =
            "{\"key_hash\":\"0000000000000000000000000000000000000000000000000000000000000000\"," +
                "\"live_until_ledger_seq\":1}"
    }
}
