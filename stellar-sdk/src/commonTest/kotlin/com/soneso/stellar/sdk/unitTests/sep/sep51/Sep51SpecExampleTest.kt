package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.xdr.AssetCode12Xdr
import com.soneso.stellar.sdk.xdr.AssetCode4Xdr
import com.soneso.stellar.sdk.xdr.AssetXdr
import com.soneso.stellar.sdk.xdr.EvictionIteratorXdr
import com.soneso.stellar.sdk.xdr.Int32Xdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.LedgerHeaderXdr
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.SCStringXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SorobanResourcesExtV0Xdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionMetaExtXdr
import com.soneso.stellar.sdk.xdr.SponsorshipDescriptorXdr
import com.soneso.stellar.sdk.xdr.TTLEntryXdr
import com.soneso.stellar.sdk.xdr.ThresholdsXdr
import com.soneso.stellar.sdk.xdr.Uint32Xdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Holds the SDK to the renderings SEP-0051 states in its Specification and Stellar-Specific
 * Types sections, one example per behaviour and each asserted in both directions: the binary
 * form must render as the stated JSON, and that JSON must encode back to the same binary form.
 *
 * Where SEP-0051 states a rule with an abstract XDR declaration, the rule is exercised through
 * the Stellar type that carries it, so a change of XDR definitions cannot silently drop the
 * behaviour from the test.
 */
class Sep51SpecExampleTest {

    @OptIn(ExperimentalEncodingApi::class)
    private fun reader(base64: String): XdrReader = XdrReader(Base64.decode(base64))

    /** The base64 of whatever [encode] writes, so a decoded value can be checked back to XDR. */
    @OptIn(ExperimentalEncodingApi::class)
    private fun base64(encode: (XdrWriter) -> Unit): String {
        val writer = XdrWriter()
        encode(writer)
        return Base64.encode(writer.toByteArray())
    }

    /** The text of a JSON string value, before the JSON encoder escapes it a second time. */
    private fun text(element: JsonElement): String = (element as JsonPrimitive).content

    // -------------------------------------------------------------------------------------
    // Integers
    // -------------------------------------------------------------------------------------

    @Test
    fun signedIntegerRendersAsAJsonNumberAtItsMaximum() {
        val xdr = "f////w=="
        assertEquals("2147483647", Int32Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { Int32Xdr.fromXdrJson("2147483647").encode(it) })
    }

    @Test
    fun signedIntegerRendersAsAJsonNumberAtItsMinimum() {
        val xdr = "gAAAAA=="
        assertEquals("-2147483648", Int32Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { Int32Xdr.fromXdrJson("-2147483648").encode(it) })
    }

    @Test
    fun unsignedIntegerRendersAsAJsonNumberAtItsMaximum() {
        val xdr = "/////w=="
        assertEquals("4294967295", Uint32Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { Uint32Xdr.fromXdrJson("4294967295").encode(it) })
    }

    @Test
    fun hyperIntegerRendersAsABaseTenStringAtItsMaximum() {
        val xdr = "f/////////8="
        assertEquals("\"9223372036854775807\"", Int64Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { Int64Xdr.fromXdrJson("\"9223372036854775807\"").encode(it) })
    }

    @Test
    fun hyperIntegerRendersAsABaseTenStringAtItsMinimum() {
        val xdr = "gAAAAAAAAAA="
        assertEquals("\"-9223372036854775808\"", Int64Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { Int64Xdr.fromXdrJson("\"-9223372036854775808\"").encode(it) })
    }

    /**
     * SEP-0051 asks implementations to keep reading the JSON number form a hyper had before the
     * string form was introduced. Output stays the string form.
     */
    @Test
    fun hyperIntegerAlsoReadsTheJsonNumberForm() {
        val decoded = Int64Xdr.fromXdrJson("1")
        assertEquals(1L, decoded.value)
        assertEquals("\"1\"", decoded.toXdrJson())
    }

    @Test
    fun unsignedHyperIntegerRendersAsABaseTenStringAtItsMaximum() {
        val xdr = "//////////8="
        assertEquals("\"18446744073709551615\"", Uint64Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { Uint64Xdr.fromXdrJson("\"18446744073709551615\"").encode(it) })
    }

    @Test
    fun unsignedHyperIntegerRendersAsABaseTenStringAtZero() {
        val xdr = "AAAAAAAAAAA="
        assertEquals("\"0\"", Uint64Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { Uint64Xdr.fromXdrJson("\"0\"").encode(it) })
    }

    /**
     * SEP-0051 asks implementations to keep reading the JSON number form an unsigned hyper had
     * before the string form was introduced. Output stays the string form.
     */
    @Test
    fun unsignedHyperIntegerAlsoReadsTheJsonNumberForm() {
        val decoded = Uint64Xdr.fromXdrJson("1")
        assertEquals(1uL, decoded.value)
        assertEquals("\"1\"", decoded.toXdrJson())
    }

    // -------------------------------------------------------------------------------------
    // Booleans
    // -------------------------------------------------------------------------------------

    /** The rendering below is the reference XDR-JSON form for this type. */
    @Test
    fun booleanRendersAsATrueJsonBoolean() {
        val xdr = "AAAAAwAAAAEAAAAAAAAAQA=="
        val json = "{\"bucket_list_level\":3,\"is_curr_bucket\":true,\"bucket_file_offset\":\"64\"}"
        assertEquals(json, EvictionIteratorXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { EvictionIteratorXdr.fromXdrJson(json).encode(it) })
    }

    /** The rendering below is the reference XDR-JSON form for this type. */
    @Test
    fun booleanRendersAsAFalseJsonBoolean() {
        val xdr = "AAAAAwAAAAAAAAAAAAAAQA=="
        val json = "{\"bucket_list_level\":3,\"is_curr_bucket\":false,\"bucket_file_offset\":\"64\"}"
        assertEquals(json, EvictionIteratorXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { EvictionIteratorXdr.fromXdrJson(json).encode(it) })
    }

    // -------------------------------------------------------------------------------------
    // Opaque data
    // -------------------------------------------------------------------------------------

    @Test
    fun fixedLengthOpaqueRendersAsAHexadecimalString() {
        val xdr = "YWJjZA=="
        assertEquals("\"61626364\"", ThresholdsXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { ThresholdsXdr.fromXdrJson("\"61626364\"").encode(it) })
    }

    @Test
    fun variableLengthOpaqueRendersAsAHexadecimalString() {
        val xdr = "AAAABGFiY2Q="
        assertEquals("\"61626364\"", SCBytesXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SCBytesXdr.fromXdrJson("\"61626364\"").encode(it) })
    }

    @Test
    fun variableLengthOpaqueRendersEmptyDataAsAnEmptyString() {
        val xdr = "AAAAAA=="
        assertEquals("\"\"", SCBytesXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SCBytesXdr.fromXdrJson("\"\"").encode(it) })
    }

    // -------------------------------------------------------------------------------------
    // The string escape ladder
    // -------------------------------------------------------------------------------------

    /** The rendering below is the reference XDR-JSON form for these bytes. */
    @Test
    fun stringEscapesNulTabLineFeedCarriageReturnAndBackslash() {
        val xdr = "AAAABQAJCg1cAAAA"
        val expected = JsonPrimitive("\\0\\t\\n\\r\\\\")
        assertEquals(expected, SCStringXdr.decode(reader(xdr)).toXdrJsonElement())
        assertEquals(xdr, base64 { SCStringXdr.fromXdrJsonElement(expected).encode(it) })
    }

    /** The rendering below is the reference XDR-JSON form for these bytes. */
    @Test
    fun stringKeepsThePrintableAsciiRangeVerbatimAndEscapesItsNeighbours() {
        val xdr = "AAAABCAffn8="
        val expected = JsonPrimitive(" \\x1f~\\x7f")
        assertEquals(expected, SCStringXdr.decode(reader(xdr)).toXdrJsonElement())
        assertEquals(xdr, base64 { SCStringXdr.fromXdrJsonElement(expected).encode(it) })
    }

    /** The rendering below is the reference XDR-JSON form for these bytes. */
    @Test
    fun stringHexEscapesBytesOutsideThePrintableRangeInLowercase() {
        val xdr = "AAAABWNhZsOpAAAA"
        val expected = JsonPrimitive("caf\\xc3\\xa9")
        assertEquals(expected, SCStringXdr.decode(reader(xdr)).toXdrJsonElement())
        assertEquals(xdr, base64 { SCStringXdr.fromXdrJsonElement(expected).encode(it) })
    }

    /**
     * A single backslash byte becomes two characters through the ladder, and the JSON encoder
     * escapes each of those again, so the document carries four.
     */
    @Test
    fun backslashIsEscapedASecondTimeByTheJsonEncoder() {
        val xdr = "AAAAAVwAAAA="
        val document = "\"" + "\\".repeat(4) + "\""
        assertEquals("\\\\", text(SCStringXdr.decode(reader(xdr)).toXdrJsonElement()))
        assertEquals(document, SCStringXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SCStringXdr.fromXdrJson(document).encode(it) })
    }

    /**
     * The conformance example of the ladder, `hello\xc3world`, is not valid UTF-8, so it is
     * carried by a bytes-typed member: an asset code holds arbitrary bytes and renders them
     * through the same ladder.
     */
    @Test
    fun bytesMemberRendersTheLadderConformanceExample() {
        val xdr = "aGVsbG/Dd29ybGQA"
        val document = "\"hello\\\\xc3world\""
        assertEquals(document, AssetCode12Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { AssetCode12Xdr.fromXdrJson(document).encode(it) })
    }

    // -------------------------------------------------------------------------------------
    // Arrays
    // -------------------------------------------------------------------------------------

    /** The rendering below is the reference XDR-JSON form for this type. */
    @Test
    fun fixedLengthArrayRendersEveryElementInOrder() {
        val xdr = "AAAAARERERERERERERERERERERERERERERERERERERERERERIiIiIiIiIiIiIiIiIiIiIiIiIiIi" +
            "IiIiIiIiIiIiIiIAAAAAAAAAAAAAAAAAAAAAMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMz" +
            "MzNERERERERERERERERERERERERERERERERERERERERERAAAAAIAAAAAAAAAAwAAAAAAAAAEAAAA" +
            "BQAAAAAAAAAGAAAAZAAAAAcAAAAIoaGhoaGhoaGhoaGhoaGhoaGhoaGhoaGhoaGhoaGhoaGioqKi" +
            "oqKioqKioqKioqKioqKioqKioqKioqKioqKioqOjo6Ojo6Ojo6Ojo6Ojo6Ojo6Ojo6Ojo6Ojo6Oj" +
            "o6OjpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKSkpKQAAAAA"
        assertEquals(LEDGER_HEADER_JSON, LedgerHeaderXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { LedgerHeaderXdr.fromXdrJson(LEDGER_HEADER_JSON).encode(it) })
    }

    /** The rendering below is the reference XDR-JSON form for this type. */
    @Test
    fun variableLengthArrayRendersEveryElementInOrder() {
        val xdr = "AAAABAAAAAEAAAACAAAAAwAAAAQ="
        val json = "{\"archived_soroban_entries\":[1,2,3,4]}"
        assertEquals(json, SorobanResourcesExtV0Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SorobanResourcesExtV0Xdr.fromXdrJson(json).encode(it) })
    }

    /** The rendering below is the reference XDR-JSON form for this type. */
    @Test
    fun variableLengthArrayRendersNoElementsAsAnEmptyArray() {
        val xdr = "AAAAAA=="
        val json = "{\"archived_soroban_entries\":[]}"
        assertEquals(json, SorobanResourcesExtV0Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SorobanResourcesExtV0Xdr.fromXdrJson(json).encode(it) })
    }

    // -------------------------------------------------------------------------------------
    // Enums, structs and unions
    // -------------------------------------------------------------------------------------

    @Test
    fun enumRendersItsMemberNameWithoutTheSharedPrefix() {
        val xdr = "AAAAAw=="
        assertEquals("\"u32\"", SCValTypeXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SCValTypeXdr.fromXdrJson("\"u32\"").encode(it) })
    }

    /** The rendering below is the reference XDR-JSON form for this member. */
    @Test
    fun enumRendersTheMemberAtValueZero() {
        val xdr = "AAAAAA=="
        assertEquals("\"bool\"", SCValTypeXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SCValTypeXdr.fromXdrJson("\"bool\"").encode(it) })
    }

    @Test
    fun structRendersEachMemberUnderItsSnakeCaseName() {
        val xdr = "AQIDBAUGBwgJEBESExQVFhcYGSAhIiMkJSYnKCkwMTIAAAAB"
        val json = "{\"key_hash\":\"010203040506070809101112131415161718192021222324252627" +
            "2829303132\",\"live_until_ledger_seq\":1}"
        assertEquals(json, TTLEntryXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { TTLEntryXdr.fromXdrJson(json).encode(it) })
    }

    @Test
    fun unionWithAVoidArmRendersAsTheDiscriminantString() {
        val xdr = "AAAAAA=="
        assertEquals("\"native\"", AssetXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { AssetXdr.fromXdrJson("\"native\"").encode(it) })
    }

    @Test
    fun unionWithAValueArmRendersAsASingleKeyObject() {
        val xdr = "AAAAAUFCQ0QAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val json = "{\"credit_alphanum4\":{\"asset_code\":\"ABCD\",\"issuer\":" +
            "\"GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF\"}}"
        assertEquals(json, AssetXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { AssetXdr.fromXdrJson(json).encode(it) })
    }

    @Test
    fun intCasedUnionRendersTheDiscriminantNameSuffixedWithItsInteger() {
        val xdr = "AAAAAA=="
        assertEquals("\"v0\"", SorobanTransactionMetaExtXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SorobanTransactionMetaExtXdr.fromXdrJson("\"v0\"").encode(it) })
    }

    // -------------------------------------------------------------------------------------
    // Optionals
    // -------------------------------------------------------------------------------------

    @Test
    fun optionalRendersAsNullWhenNotSet() {
        val xdr = "AAAAAA=="
        assertEquals("null", SponsorshipDescriptorXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SponsorshipDescriptorXdr.fromXdrJson("null").encode(it) })
    }

    @Test
    fun optionalRendersTheValueWhenSet() {
        val xdr = "AAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
        val json = "\"GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF\""
        assertEquals(json, SponsorshipDescriptorXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SponsorshipDescriptorXdr.fromXdrJson(json).encode(it) })
    }

    // -------------------------------------------------------------------------------------
    // Stellar-specific types
    // -------------------------------------------------------------------------------------

    @Test
    fun addressRendersAMuxedAccountAsAnMStrkey() {
        val xdr = "AAAAAgAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        val json = "\"MAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAFNZG\""
        assertEquals(json, SCAddressXdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { SCAddressXdr.fromXdrJson(json).encode(it) })
    }

    @Test
    fun assetCodeOfFourDropsItsTrailingPadding() {
        val xdr = "QUJDAA=="
        assertEquals("\"ABC\"", AssetCode4Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { AssetCode4Xdr.fromXdrJson("\"ABC\"").encode(it) })
    }

    /** The rendering below is the reference XDR-JSON form for an asset code filling four bytes. */
    @Test
    fun assetCodeOfFourKeepsItsFullWidth() {
        val xdr = "QUJDRA=="
        assertEquals("\"ABCD\"", AssetCode4Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { AssetCode4Xdr.fromXdrJson("\"ABCD\"").encode(it) })
    }

    @Test
    fun assetCodeOfTwelveDropsPaddingDownToFiveCharacters() {
        val xdr = "QUJDREUAAAAAAAAA"
        assertEquals("\"ABCDE\"", AssetCode12Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { AssetCode12Xdr.fromXdrJson("\"ABCDE\"").encode(it) })
    }

    /**
     * Five characters is the floor: a shorter code keeps enough padding to stay distinguishable
     * from anything an asset code of four can render.
     */
    @Test
    fun assetCodeOfTwelveKeepsPaddingUpToFiveCharacters() {
        val xdr = "QUJDAAAAAAAAAAAA"
        val document = "\"ABC\\\\0\\\\0\""
        assertEquals("ABC\\0\\0", text(AssetCode12Xdr.decode(reader(xdr)).toXdrJsonElement()))
        assertEquals(document, AssetCode12Xdr.decode(reader(xdr)).toXdrJson())
        assertEquals(xdr, base64 { AssetCode12Xdr.fromXdrJson(document).encode(it) })
    }

    private companion object {

        /** The reference XDR-JSON form of a ledger header, whose skip list is a fixed array. */
        const val LEDGER_HEADER_JSON: String =
            "{\"ledger_version\":1,\"previous_ledger_hash\":\"1111111111111111111111111" +
                "111111111111111111111111111111111111111\",\"scp_value\":{\"tx_set_hash\":" +
                "\"2222222222222222222222222222222222222222222222222222222222222222\",\"clo" +
                "se_time\":\"0\",\"upgrades\":[],\"ext\":\"basic\"},\"tx_set_result_hash\":" +
                "\"3333333333333333333333333333333333333333333333333333333333333333\",\"buc" +
                "ket_list_hash\":\"44444444444444444444444444444444444444444444444444444444" +
                "44444444\",\"ledger_seq\":2,\"total_coins\":\"3\",\"fee_pool\":\"4\",\"inf" +
                "lation_seq\":5,\"id_pool\":\"6\",\"base_fee\":100,\"base_reserve\":7,\"max" +
                "_tx_set_size\":8,\"skip_list\":[\"a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1" +
                "a1a1a1a1a1a1a1a1a1a1a1a1\",\"a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a" +
                "2a2a2a2a2a2a2a2a2a2\",\"a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3" +
                "a3a3a3a3a3a3a3\",\"a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a4a" +
                "4a4a4a4a4\"],\"ext\":\"v0\"}"
    }
}
