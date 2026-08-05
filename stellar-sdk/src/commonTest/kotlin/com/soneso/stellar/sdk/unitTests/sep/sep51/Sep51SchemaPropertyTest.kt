package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.xdr.AssetXdr
import com.soneso.stellar.sdk.xdr.TTLEntryXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins the `$schema` property of SEP-0051: every JSON object may carry it, no JSON object
 * requires it, and it names a schema document rather than any part of the value. Decoding must
 * therefore accept it and produce the value the rest of the object describes, and encoding must
 * never write it.
 *
 * A `$schema` property alone does not identify a value: a union still needs an arm.
 */
class Sep51SchemaPropertyTest {

    /** The base64 of whatever [encode] writes, so a decoded value can be checked back to XDR. */
    @OptIn(ExperimentalEncodingApi::class)
    private fun base64(encode: (XdrWriter) -> Unit): String {
        val writer = XdrWriter()
        encode(writer)
        return Base64.encode(writer.toByteArray())
    }

    /** The same document with a `$schema` property added ahead of its other members. */
    private fun withSchema(json: String): String =
        "{\"\$schema\":\"$SCHEMA_URL\"," + json.removePrefix("{")

    @Test
    fun schemaPropertyIsAcceptedAndDroppedOnAStruct() {
        val carried = withSchema(TTL_ENTRY_JSON)
        assertEquals(TTL_ENTRY_XDR, base64 { TTLEntryXdr.fromXdrJson(carried).encode(it) })
    }

    /** The object below is the one SEP-0051 prints to show `$schema` on an `Asset`. */
    @Test
    fun schemaPropertyIsAcceptedAndDroppedOnAUnion() {
        val carried = withSchema(ASSET_JSON)
        assertEquals(ASSET_XDR, base64 { AssetXdr.fromXdrJson(carried).encode(it) })
    }

    @Test
    fun schemaPropertyIsNeverEmitted() {
        assertEquals(TTL_ENTRY_JSON, TTLEntryXdr.fromXdrJson(withSchema(TTL_ENTRY_JSON)).toXdrJson())
        assertEquals(ASSET_JSON, AssetXdr.fromXdrJson(withSchema(ASSET_JSON)).toXdrJson())
    }

    @Test
    fun anObjectCarryingOnlyTheSchemaPropertyIsRejected() {
        val only = "{\"\$schema\":\"$SCHEMA_URL\"}"
        assertFailsWith<IllegalArgumentException> { AssetXdr.fromXdrJson(only) }
        assertFailsWith<IllegalArgumentException> { TTLEntryXdr.fromXdrJson(only) }
    }

    private companion object {
        const val SCHEMA_URL: String = "https://stellar.org/schema/xdr-json/main/Asset.json"

        const val TTL_ENTRY_JSON: String =
            "{\"key_hash\":\"01020304050607080910111213141516171819202122232425262728293031" +
                "32\",\"live_until_ledger_seq\":1}"

        const val TTL_ENTRY_XDR: String = "AQIDBAUGBwgJEBESExQVFhcYGSAhIiMkJSYnKCkwMTIAAAAB"

        const val ASSET_JSON: String =
            "{\"credit_alphanum4\":{\"asset_code\":\"ABCD\",\"issuer\":\"GAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF\"}}"

        const val ASSET_XDR: String =
            "AAAAAUFCQ0QAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }
}
