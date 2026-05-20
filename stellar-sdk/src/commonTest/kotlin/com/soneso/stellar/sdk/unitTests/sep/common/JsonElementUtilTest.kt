// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.common

import com.soneso.stellar.sdk.sep.common.anyToJsonElement
import com.soneso.stellar.sdk.sep.common.jsonElementToAny
import com.soneso.stellar.sdk.sep.common.mapToJsonString
import com.soneso.stellar.sdk.sep.common.sanitizeAnchorString
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class JsonElementUtilTest {

    private val testJson = Json { encodeDefaults = false }

    // ==================== mapToJsonString ====================

    @Test
    fun mapToJsonString_primitiveValues_producesSnakeCaseJson() = runTest {
        val map = linkedMapOf<String, Any?>(
            "amount" to 100.0,
            "asset_code" to "USDC",
            "is_active" to true,
        )
        val result = mapToJsonString(map, testJson)
        // Verify all three keys appear in the output and basic shape is correct.
        val parsed = Json.decodeFromString(JsonObject.serializer(), result)
        assertEquals("USDC", (parsed["asset_code"] as? JsonPrimitive)?.content)
        assertEquals(true, (parsed["is_active"] as? JsonPrimitive)?.boolean)
        // Double serialization format is platform-dependent (100 vs 100.0), so compare numerically.
        assertEquals(100.0, (parsed["amount"] as? JsonPrimitive)?.doubleOrNull)
    }

    @Test
    fun mapToJsonString_nestedMap_producesNestedJson() = runTest {
        val map = mapOf<String, Any?>(
            "fields" to mapOf(
                "transaction" to mapOf(
                    "sender_first_name" to "Alice",
                ),
            ),
        )
        val result = mapToJsonString(map, testJson)
        val outer = Json.decodeFromString(JsonObject.serializer(), result)
        val fields = outer["fields"] as? JsonObject
        val transaction = fields?.get("transaction") as? JsonObject
        assertEquals("Alice", (transaction?.get("sender_first_name") as? JsonPrimitive)?.content)
    }

    @Test
    fun mapToJsonString_nullValue_producesJsonNull() = runTest {
        val map = mapOf<String, Any?>("key" to null)
        val result = mapToJsonString(map, testJson)
        val parsed = Json.decodeFromString(JsonObject.serializer(), result)
        assertEquals(JsonNull, parsed["key"])
    }

    // ==================== anyToJsonElement ====================

    @Test
    fun anyToJsonElement_supportedPrimitives_returnsCorrectJsonElementType() = runTest {
        val stringEl = anyToJsonElement("hello")
        assertIs<JsonPrimitive>(stringEl)
        assertEquals("hello", stringEl.content)

        val intEl = anyToJsonElement(42)
        assertIs<JsonPrimitive>(intEl)
        assertEquals("42", intEl.content)

        val longEl = anyToJsonElement(9_000_000_000L)
        assertIs<JsonPrimitive>(longEl)
        assertEquals("9000000000", longEl.content)

        val doubleEl = anyToJsonElement(3.14)
        assertIs<JsonPrimitive>(doubleEl)

        val boolEl = anyToJsonElement(true)
        assertIs<JsonPrimitive>(boolEl)
        assertEquals(true, boolEl.boolean)

        val nullEl = anyToJsonElement(null)
        assertEquals(JsonNull, nullEl)
    }

    @Test
    fun anyToJsonElement_unsupportedType_throwsIllegalArgumentException() = runTest {
        // A data class is not one of the supported types; must throw.
        data class UnsupportedValue(val x: Int)
        assertFailsWith<IllegalArgumentException> {
            anyToJsonElement(UnsupportedValue(1))
        }
    }

    // ==================== jsonElementToAny ====================

    @Test
    fun jsonElementToAny_primitive_returnsPrimitive() = runTest {
        val stringResult = jsonElementToAny(JsonPrimitive("hello"))
        assertIs<String>(stringResult)
        assertEquals("hello", stringResult)

        val boolResult = jsonElementToAny(JsonPrimitive(true))
        assertIs<Boolean>(boolResult)
        assertEquals(true, boolResult)

        val longResult = jsonElementToAny(JsonPrimitive(42L))
        assertIs<Long>(longResult)
        assertEquals(42L, longResult)

        val doubleResult = jsonElementToAny(JsonPrimitive(1.5))
        assertIs<Double>(doubleResult)
        assertEquals(1.5, doubleResult)
    }

    @Test
    fun jsonElementToAny_jsonObject_returnsMapWithPrimitiveLeaves() = runTest {
        val obj = buildJsonObject {
            put("name", "Alice")
            put("age", 30)
            put("active", true)
        }
        val result = jsonElementToAny(obj)
        assertIs<Map<*, *>>(result)
        assertNoJsonElementLeaves(result.entries.associate { (k, v) -> k.toString() to v })
        assertEquals("Alice", result["name"])
        assertEquals(30L, result["age"])
        assertEquals(true, result["active"])
    }

    @Test
    fun jsonElementToAny_jsonArray_returnsListWithPrimitiveLeaves() = runTest {
        val arr = buildJsonArray {
            add(JsonPrimitive("SEPA"))
            add(JsonPrimitive("SWIFT"))
        }
        val result = jsonElementToAny(arr)
        assertIs<List<*>>(result)
        assertNoJsonElementLeaves(mapOf("list" to result))
        assertEquals(listOf("SEPA", "SWIFT"), result)
    }

    @Test
    fun jsonElementToAny_deeplyNested_returnsCorrectStructure() = runTest {
        // Three levels of nesting.
        val obj = buildJsonObject {
            put("level1", buildJsonObject {
                put("level2", buildJsonObject {
                    put("value", "deep")
                })
            })
        }
        val result = jsonElementToAny(obj)
        assertIs<Map<*, *>>(result)
        @Suppress("UNCHECKED_CAST")
        val l1 = result["level1"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val l2 = l1["level2"] as Map<String, Any?>
        assertEquals("deep", l2["value"])
        assertNoJsonElementLeaves(result.entries.associate { (k, v) -> k.toString() to v })
    }

    @Test
    fun jsonElementToAny_jsonNull_returnsNull() = runTest {
        val result = jsonElementToAny(JsonNull)
        assertNull(result)
    }

    @Test
    fun jsonElementToAny_numericPrimitiveAsString_remainsAsString() = runTest {
        // A quoted JSON primitive like "100" has isString == true and must stay as String.
        val quotedNumeric = JsonPrimitive("100")
        val result = jsonElementToAny(quotedNumeric)
        assertIs<String>(result)
        assertEquals("100", result)
    }

    @Test
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    fun jsonElementToAny_unquotedLiteralNonNumericNonBoolean_returnsRawContentString() = runTest {
        // The fallback in jsonElementToAny returns `element.content` (String) when an
        // unquoted JSON primitive can be parsed as neither Boolean, Long, nor Double.
        // JsonUnquotedLiteral creates exactly this shape: isString=false with content
        // that is not a parseable JSON number or boolean. This locks in the defensive
        // string fallback at the end of the branch chain.
        val literal = kotlinx.serialization.json.JsonUnquotedLiteral("not-a-number")
        val result = jsonElementToAny(literal)
        assertIs<String>(result)
        assertEquals("not-a-number", result)
    }

    // ==================== sanitizeAnchorString ====================

    private val jwtFixture = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig123"

    @Test
    fun sanitizeAnchorString_defaultRedactJwts_replacesJwtWithRedactedToken() = runTest {
        val input = "token $jwtFixture is invalid"
        val result = sanitizeAnchorString(input)
        kotlin.test.assertTrue(!result.contains(jwtFixture), "JWT must be redacted by default")
        kotlin.test.assertTrue(result.contains("<redacted-jwt>"), "literal token must appear")
    }

    @Test
    fun sanitizeAnchorString_redactJwtsFalse_preservesJwt() = runTest {
        val input = "token $jwtFixture is invalid"
        val result = sanitizeAnchorString(input, redactJwts = false)
        kotlin.test.assertTrue(
            result.contains(jwtFixture),
            "JWT must survive verbatim when redactJwts=false; was: $result",
        )
        kotlin.test.assertTrue(
            !result.contains("<redacted-jwt>"),
            "redacted-jwt sentinel must not appear when redactJwts=false",
        )
    }

    @Test
    fun sanitizeAnchorString_redactJwtsFalse_stillStripsControlCharacters() = runTest {
        // Control-char strip applies even with JWT redaction disabled — defense in depth
        // against terminal escape injection.
        val controlChar = '' // ESC
        val input = "token $jwtFixture${controlChar}[31m"
        val result = sanitizeAnchorString(input, redactJwts = false)
        kotlin.test.assertTrue(result.contains(jwtFixture), "JWT must survive")
        kotlin.test.assertTrue(!result.contains(controlChar), "control char must still be scrubbed")
    }

    @Test
    fun sanitizeAnchorString_redactJwtsFalse_stillCapsAt1024Chars() = runTest {
        // 1024-char cap applies even with JWT redaction disabled — defense in depth
        // against log-spam from oversized anchor responses.
        val padding = "x".repeat(2000)
        val result = sanitizeAnchorString(padding, redactJwts = false)
        kotlin.test.assertTrue(result.length <= 1024, "must cap at 1024 even when not redacting; was ${result.length}")
    }
}

/**
 * Recursively asserts that no value within [map] (or within any nested map or list) is a
 * [JsonElement] instance. Fails the test at the first offending leaf.
 */
private fun assertNoJsonElementLeaves(map: Map<String, Any?>) {
    for ((key, value) in map) {
        assertNoJsonElementLeavesValue(key, value)
    }
}

private fun assertNoJsonElementLeavesValue(path: String, value: Any?) {
    if (value is JsonElement) {
        kotlin.test.fail("JsonElement found at path '$path': $value")
    }
    @Suppress("UNCHECKED_CAST")
    when (value) {
        is Map<*, *> -> (value as Map<String, Any?>).forEach { (k, v) ->
            assertNoJsonElementLeavesValue("$path.$k", v)
        }
        is List<*> -> value.forEachIndexed { i, v ->
            assertNoJsonElementLeavesValue("$path[$i]", v)
        }
    }
}
