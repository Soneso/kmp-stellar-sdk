// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep51

import com.soneso.stellar.sdk.xdr.AssetCode12Xdr
import com.soneso.stellar.sdk.xdr.AssetCode4Xdr
import com.soneso.stellar.sdk.xdr.AssetXdr
import com.soneso.stellar.sdk.xdr.Int32Xdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.SCStringXdr
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.SorobanResourcesExtV0Xdr
import com.soneso.stellar.sdk.xdr.SorobanTransactionMetaExtXdr
import com.soneso.stellar.sdk.xdr.SponsorshipDescriptorXdr
import com.soneso.stellar.sdk.xdr.TTLEntryXdr
import com.soneso.stellar.sdk.xdr.ThresholdsXdr
import com.soneso.stellar.sdk.xdr.TimeBoundsXdr
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.TransactionResultXdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import com.soneso.stellar.sdk.xdr.toXdrBase64
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Runs every code example in `docs/sep/sep-51.md` and asserts the value each one prints, so a
 * change to the XDR-JSON surface that would make the documentation wrong fails the build instead.
 *
 * Each test carries the name of the example it mirrors.
 */
class Sep51DocSnippetsCompileTest {

    @Test
    fun quickStart() {
        val value = SCValXdr.fromXdrBase64("AAAADwAAAAh0cmFuc2Zlcg==")
        assertEquals("{\"symbol\":\"transfer\"}", value.toXdrJson())

        val restored = SCValXdr.fromXdrJson("{\"symbol\":\"transfer\"}")
        assertEquals("AAAADwAAAAh0cmFuc2Zlcg==", restored.toXdrBase64())
    }

    @Test
    fun theFourMembers() {
        val bounds = TimeBoundsXdr.fromXdrJson("{\"min_time\":\"0\",\"max_time\":\"1700000000\"}")

        val text: String = bounds.toXdrJson()
        val fromText: TimeBoundsXdr = TimeBoundsXdr.fromXdrJson(text)

        val tree: JsonElement = bounds.toXdrJsonElement()
        val fromTree: TimeBoundsXdr = TimeBoundsXdr.fromXdrJsonElement(tree)

        assertTrue(fromText == fromTree)
    }

    @Test
    fun readEnvelope() {
        val base64 = "AAAAAgAAAADmmSZkwY3163TMouB2TY8MljqXw2IxVYTGyvDrR6YtAAAqmmQAABpuAAAAAQAAAAAA" +
            "AAAAAAAAAQAAAAAAAAAYAAAAAQAAAAEAAAAAAAAAAQAAAAAAAAABAAAAAAAAAAAAAAABAAAABgAA" +
            "AAHXkotywnA8z+r365/0701QSlWouXn8m0UOoshCtNHOYQAAABQAAAABAAI9fQAAAAAAAAD4AAAA" +
            "AAAqmgAAAAABR6YtAAAAAEArDtxbqUI+CsdkRmV0lFhVt0wyB7fyrmmkM6Fr35wpPcK8WKcXeKTl" +
            "4BQ+akE14MZtpaea9LMdhXopaW3pJA0E"

        val envelope = TransactionEnvelopeXdr.fromXdrBase64(base64)

        // The output is one compact line, which is what makes the indented block in the doc an
        // abridgement rather than the literal output.
        val json = envelope.toXdrJson()
        assertTrue(!json.contains("\n"), "output is a single line")

        // The abridged block is walked rather than string-matched, so the nesting it shows is
        // what gets asserted: the envelope arm, the transaction inside it, and its signatures.
        val root = envelope.toXdrJsonElement().jsonObject
        val v1 = root["tx"]!!.jsonObject
        val tx = v1["tx"]!!.jsonObject

        assertEquals(
            "GDTJSJTEYGG7L23UZSROA5SNR4GJMOUXYNRDCVMEY3FPB22HUYWQBZIA",
            tx["source_account"]!!.jsonPrimitive.content
        )
        // A 32-bit unsigned is a JSON number; a 64-bit one is a string.
        assertEquals(2792036, tx["fee"]!!.jsonPrimitive.int)
        assertEquals("29059748724737", tx["seq_num"]!!.jsonPrimitive.content)
        assertEquals("none", tx["cond"]!!.jsonPrimitive.content)
        assertEquals("none", tx["memo"]!!.jsonPrimitive.content)

        val operation = tx["operations"]!!.jsonArray.single().jsonObject
        assertEquals(JsonNull, operation["source_account"])
        val invoke = operation["body"]!!.jsonObject["invoke_host_function"]!!.jsonObject
        assertTrue(invoke["auth"]!!.jsonArray.isEmpty(), "auth renders as an empty array")
        val create = invoke["host_function"]!!.jsonObject["create_contract"]!!.jsonObject
        assertEquals(
            "native",
            create["contract_id_preimage"]!!.jsonObject["asset"]!!.jsonPrimitive.content
        )
        assertEquals("stellar_asset", create["executable"]!!.jsonPrimitive.content)

        val soroban = tx["ext"]!!.jsonObject["v1"]!!.jsonObject
        assertEquals("v0", soroban["ext"]!!.jsonPrimitive.content)
        assertEquals("2791936", soroban["resource_fee"]!!.jsonPrimitive.content)

        val signature = v1["signatures"]!!.jsonArray.single().jsonObject
        assertEquals("47a62d00", signature["hint"]!!.jsonPrimitive.content)
        assertTrue(
            signature["signature"]!!.jsonPrimitive.content
                .startsWith("2b0edc5ba9423e0ac764466574945855b74c3207b7f2ae69a433a16bdf9c293d"),
            "signature renders as hexadecimal"
        )
    }

    @Test
    fun readResult() {
        val resultXdr = "AAAAAAAAAGT/////AAAAAQAAAAAAAAAB/////wAAAAA="
        assertEquals(
            "{\"fee_charged\":\"100\",\"result\":{\"tx_failed\":[{\"op_inner\":" +
                "{\"payment\":\"malformed\"}}]},\"ext\":\"v0\"}",
            TransactionResultXdr.fromXdrBase64(resultXdr).toXdrJson()
        )
    }

    @Test
    fun inspectTree() {
        val result = TransactionResultXdr.fromXdrBase64("AAAAAAAAAGT/////AAAAAQAAAAAAAAAB/////wAAAAA=")
        val tree = result.toXdrJsonElement().jsonObject

        assertEquals("100", tree["fee_charged"]!!.jsonPrimitive.content)
        assertEquals("tx_failed", tree["result"]!!.jsonObject.keys.single())
    }

    @Test
    fun buildTree() {
        val bounds = TimeBoundsXdr.fromXdrJsonElement(
            buildJsonObject {
                put("min_time", "0")
                put("max_time", "1700000000")
            }
        )
        assertEquals("1700000000", bounds.maxTime.value.value.toString())
    }

    @Test
    fun integers() {
        assertEquals("2147483647", Int32Xdr.fromXdrJson("2147483647").toXdrJson())
        assertEquals(
            "\"-9223372036854775808\"",
            Int64Xdr.fromXdrJson("\"-9223372036854775808\"").toXdrJson()
        )
        assertEquals(
            "\"18446744073709551615\"",
            Uint64Xdr.fromXdrJson("\"18446744073709551615\"").toXdrJson()
        )
        assertEquals("\"1\"", Int64Xdr.fromXdrJson("1").toXdrJson())
        assertFailsWith<IllegalArgumentException> { Int32Xdr.fromXdrJson("\"1\"") }
    }

    @Test
    fun opaque() {
        assertEquals("\"61626364\"", ThresholdsXdr.fromXdrJson("\"61626364\"").toXdrJson())
        assertEquals("\"\"", SCBytesXdr.fromXdrJson("\"\"").toXdrJson())
        assertFailsWith<IllegalArgumentException> { ThresholdsXdr.fromXdrJson("\"616263\"") }
        assertFailsWith<IllegalArgumentException> { ThresholdsXdr.fromXdrJson("\"6162636465\"") }
    }

    @Test
    fun strings() {
        assertEquals("\"" + "\\".repeat(4) + "\"", SCStringXdr("\\").toXdrJson())
        assertEquals("\"caf\\\\xc3\\\\xa9\"", SCStringXdr("café").toXdrJson())
    }

    @Test
    fun arrays() {
        assertEquals(
            "{\"archived_soroban_entries\":[1,2,3,4]}",
            SorobanResourcesExtV0Xdr.fromXdrJson("{\"archived_soroban_entries\":[1,2,3,4]}").toXdrJson()
        )
        assertEquals(
            "{\"archived_soroban_entries\":[]}",
            SorobanResourcesExtV0Xdr.fromXdrJson("{\"archived_soroban_entries\":[]}").toXdrJson()
        )
    }

    @Test
    fun enums() {
        assertEquals("\"u32\"", SCValTypeXdr.SCV_U32.toXdrJson())
        assertEquals("\"bool\"", SCValTypeXdr.SCV_BOOL.toXdrJson())
    }

    @Test
    fun structs() {
        val json = "{\"key_hash\":\"0102030405060708091011121314151617181920212223242526272829303132\"," +
            "\"live_until_ledger_seq\":1}"
        assertTrue(TTLEntryXdr.fromXdrJson(json).toXdrJson() == json)
    }

    @Test
    fun unions() {
        assertEquals("\"native\"", AssetXdr.Void.toXdrJson())

        val credit = "{\"credit_alphanum4\":{\"asset_code\":\"ABCD\"," +
            "\"issuer\":\"GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF\"}}"
        assertTrue(AssetXdr.fromXdrJson(credit).toXdrJson() == credit)
    }

    @Test
    fun intCasedUnion() {
        assertEquals("\"v0\"", SorobanTransactionMetaExtXdr.Void.toXdrJson())
    }

    @Test
    fun optionals() {
        assertEquals("null", SponsorshipDescriptorXdr(null).toXdrJson())
    }

    @Test
    fun stellarTypes() {
        assertEquals("\"ABC\"", AssetCode4Xdr.fromXdrJson("\"ABC\"").toXdrJson())
        assertEquals("\"ABCDE\"", AssetCode12Xdr.fromXdrJson("\"ABCDE\"").toXdrJson())
        assertEquals("{\"i128\":\"-42\"}", SCValXdr.fromXdrJson("{\"i128\":\"-42\"}").toXdrJson())
        assertEquals("AAAACv///////////////////9Y=", SCValXdr.fromXdrJson("{\"i128\":\"-42\"}").toXdrBase64())
    }

    @Test
    fun handleErrors() {
        val missingKey = assertFailsWith<IllegalArgumentException> {
            TTLEntryXdr.fromXdrJson("{\"key_hash\":\"" + "00".repeat(32) + "\"}")
        }
        assertEquals(
            "TTLEntryXdr: is missing the required key \"live_until_ledger_seq\"",
            missingKey.message
        )

        val unknownArm = assertFailsWith<IllegalArgumentException> {
            AssetXdr.fromXdrJson("{\"gold\":{}}")
        }
        assertEquals("AssetXdr: has no arm named \"gold\"", unknownArm.message)
    }

    @Test
    fun strictnessRules() {
        assertFailsWith<IllegalArgumentException> { ThresholdsXdr.fromXdrJson("\"6162636D\"") }
        assertFailsWith<IllegalArgumentException> { Int64Xdr.fromXdrJson("\"1.0\"") }
        assertFailsWith<IllegalArgumentException> { Int64Xdr.fromXdrJson("\"1e10\"") }
        assertFailsWith<IllegalArgumentException> { Int64Xdr.fromXdrJson("\"+1\"") }
        // The type_ alias is accepted and never emitted
        assertEquals(
            "{\"doc\":\"\",\"name\":\"\",\"type\":\"val\"}",
            com.soneso.stellar.sdk.xdr.SCSpecFunctionInputV0Xdr
                .fromXdrJson("{\"doc\":\"\",\"name\":\"\",\"type_\":\"val\"}")
                .toXdrJson()
        )
        // $schema is accepted and never emitted
        assertEquals(
            "{\"min_time\":\"0\",\"max_time\":\"1\"}",
            TimeBoundsXdr.fromXdrJson(
                "{\"\$schema\":\"https://example.com/s.json\",\"min_time\":\"0\",\"max_time\":\"1\"}"
            ).toXdrJson()
        )
    }
}
