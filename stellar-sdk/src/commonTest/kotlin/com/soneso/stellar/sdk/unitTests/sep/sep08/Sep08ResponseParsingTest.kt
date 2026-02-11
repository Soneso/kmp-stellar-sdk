// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep08

import com.soneso.stellar.sdk.sep.sep08.*
import com.soneso.stellar.sdk.sep.sep08.exceptions.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

/**
 * Tests for JSON parsing of SEP-8 response types.
 *
 * Verifies that [Sep08PostTransactionResponse.fromJson] and [Sep08PostActionResponse.fromJson]
 * correctly deserialize from JSON, including edge cases with missing fields and unknown values.
 */
class Sep08ResponseParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ========== Sep08PostTransactionResponse - Success ==========

    @Test
    fun testParseSuccessResponse() = runTest {
        val jsonStr = """
            {"status":"success","tx":"AAAAAgAAAAA...","message":"Transaction approved"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.Success)
        assertEquals("AAAAAgAAAAA...", response.tx)
        assertEquals("Transaction approved", response.message)
    }

    @Test
    fun testParseSuccessNoMessage() = runTest {
        val jsonStr = """
            {"status":"success","tx":"AAAAAgAAAAA..."}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.Success)
        assertEquals("AAAAAgAAAAA...", response.tx)
        assertNull(response.message)
    }

    // ========== Sep08PostTransactionResponse - Revised ==========

    @Test
    fun testParseRevisedResponse() = runTest {
        val jsonStr = """
            {"status":"revised","tx":"AAAAAgBBBB...","message":"Added compliance operations"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.Revised)
        assertEquals("AAAAAgBBBB...", response.tx)
        assertEquals("Added compliance operations", response.message)
    }

    // ========== Sep08PostTransactionResponse - Pending ==========

    @Test
    fun testParsePendingResponse() = runTest {
        val jsonStr = """
            {"status":"pending","timeout":3000,"message":"Waiting for compliance check"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.Pending)
        assertEquals(3000, response.timeout)
        assertEquals("Waiting for compliance check", response.message)
    }

    @Test
    fun testParsePendingMinimal() = runTest {
        val jsonStr = """
            {"status":"pending"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.Pending)
        assertEquals(0, response.timeout)
        assertNull(response.message)
    }

    // ========== Sep08PostTransactionResponse - ActionRequired ==========

    @Test
    fun testParseActionRequiredResponse() = runTest {
        val jsonStr = """
            {
                "status":"action_required",
                "message":"KYC verification needed",
                "action_url":"https://kyc.io/verify",
                "action_method":"POST",
                "action_fields":["email_address","mobile_number"]
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.ActionRequired)
        assertEquals("KYC verification needed", response.message)
        assertEquals("https://kyc.io/verify", response.actionUrl)
        assertEquals("POST", response.actionMethod)
        assertNotNull(response.actionFields)
        assertEquals(2, response.actionFields!!.size)
        assertEquals("email_address", response.actionFields!![0])
        assertEquals("mobile_number", response.actionFields!![1])
    }

    @Test
    fun testParseActionRequiredMinimal() = runTest {
        val jsonStr = """
            {
                "status":"action_required",
                "message":"Please verify your identity",
                "action_url":"https://kyc.io/verify"
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.ActionRequired)
        assertEquals("Please verify your identity", response.message)
        assertEquals("https://kyc.io/verify", response.actionUrl)
        assertEquals("GET", response.actionMethod)
        assertNull(response.actionFields)
    }

    // ========== Sep08PostTransactionResponse - Rejected ==========

    @Test
    fun testParseRejectedResponse() = runTest {
        val jsonStr = """
            {"status":"rejected","error":"Destination account is blocked"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.Rejected)
        assertEquals("Destination account is blocked", response.error)
    }

    // ========== Sep08PostTransactionResponse - Error Cases ==========

    @Test
    fun testParseMissingStatus() = runTest {
        val jsonStr = """
            {"tx":"AAAAAgAAAAA...","message":"No status field"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        assertFailsWith<Sep08InvalidTransactionResponseException> {
            Sep08PostTransactionResponse.fromJson(jsonObject)
        }
    }

    @Test
    fun testParseUnknownStatus() = runTest {
        val jsonStr = """
            {"status":"unknown_value","tx":"AAAAAgAAAAA..."}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep08InvalidTransactionResponseException> {
            Sep08PostTransactionResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("Unknown status"))
        assertTrue(exception.message!!.contains("unknown_value"))
    }

    @Test
    fun testParseSuccessMissingTx() = runTest {
        val jsonStr = """
            {"status":"success","message":"Approved but no tx"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep08InvalidTransactionResponseException> {
            Sep08PostTransactionResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("tx"))
    }

    @Test
    fun testParseRevisedMissingTx() = runTest {
        val jsonStr = """
            {"status":"revised","message":"Revised but no tx"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        assertFailsWith<Sep08InvalidTransactionResponseException> {
            Sep08PostTransactionResponse.fromJson(jsonObject)
        }
    }

    @Test
    fun testParseRevisedMissingMessage() = runTest {
        val jsonStr = """
            {"status":"revised","tx":"AAAAAgBBBB..."}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep08InvalidTransactionResponseException> {
            Sep08PostTransactionResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("message"))
    }

    @Test
    fun testParseActionRequiredMissingUrl() = runTest {
        val jsonStr = """
            {"status":"action_required","message":"KYC needed"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep08InvalidTransactionResponseException> {
            Sep08PostTransactionResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("action_url"))
    }

    @Test
    fun testParseActionRequiredMissingMessage() = runTest {
        val jsonStr = """
            {"status":"action_required","action_url":"https://kyc.io/verify"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep08InvalidTransactionResponseException> {
            Sep08PostTransactionResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("message"))
    }

    @Test
    fun testParseRejectedMissingError() = runTest {
        val jsonStr = """
            {"status":"rejected"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep08InvalidTransactionResponseException> {
            Sep08PostTransactionResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("error"))
    }

    @Test
    fun testParseEmptyJsonObject() = runTest {
        val jsonStr = """{}"""

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        assertFailsWith<Sep08InvalidTransactionResponseException> {
            Sep08PostTransactionResponse.fromJson(jsonObject)
        }
    }

    @Test
    fun testParseNullStatusValue() = runTest {
        val jsonStr = """{"status":null}"""

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        assertFailsWith<Sep08InvalidTransactionResponseException> {
            Sep08PostTransactionResponse.fromJson(jsonObject)
        }
    }

    // ========== Sep08PostActionResponse - Done ==========

    @Test
    fun testParseActionDone() = runTest {
        val jsonStr = """
            {"result":"no_further_action_required"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostActionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostActionResponse.Done)
    }

    // ========== Sep08PostActionResponse - NextUrl ==========

    @Test
    fun testParseActionNextUrl() = runTest {
        val jsonStr = """
            {"result":"follow_next_url","next_url":"https://kyc.io/step2","message":"Complete step 2"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostActionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostActionResponse.NextUrl)
        assertEquals("https://kyc.io/step2", response.nextUrl)
        assertEquals("Complete step 2", response.message)
    }

    @Test
    fun testParseActionNextUrlNoMessage() = runTest {
        val jsonStr = """
            {"result":"follow_next_url","next_url":"https://kyc.io/step2"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostActionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostActionResponse.NextUrl)
        assertEquals("https://kyc.io/step2", response.nextUrl)
        assertNull(response.message)
    }

    // ========== Sep08PostActionResponse - Error Cases ==========

    @Test
    fun testParseActionMissingResult() = runTest {
        val jsonStr = """
            {"next_url":"https://kyc.io/step2"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        assertFailsWith<Sep08InvalidActionResponseException> {
            Sep08PostActionResponse.fromJson(jsonObject)
        }
    }

    @Test
    fun testParseActionUnknownResult() = runTest {
        val jsonStr = """
            {"result":"unknown_result_value"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep08InvalidActionResponseException> {
            Sep08PostActionResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("Unknown result"))
        assertTrue(exception.message!!.contains("unknown_result_value"))
    }

    @Test
    fun testParseActionNextUrlMissingUrl() = runTest {
        val jsonStr = """
            {"result":"follow_next_url","message":"Go to next step"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        val exception = assertFailsWith<Sep08InvalidActionResponseException> {
            Sep08PostActionResponse.fromJson(jsonObject)
        }
        assertTrue(exception.message!!.contains("next_url"))
    }

    @Test
    fun testParseActionEmptyJsonObject() = runTest {
        val jsonStr = """{}"""

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        assertFailsWith<Sep08InvalidActionResponseException> {
            Sep08PostActionResponse.fromJson(jsonObject)
        }
    }

    @Test
    fun testParseActionNullResultValue() = runTest {
        val jsonStr = """{"result":null}"""

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)

        assertFailsWith<Sep08InvalidActionResponseException> {
            Sep08PostActionResponse.fromJson(jsonObject)
        }
    }

    // ========== Edge Cases ==========

    @Test
    fun testParseSuccessWithUnknownFields() = runTest {
        val jsonStr = """
            {"status":"success","tx":"AAAA...","message":"OK","extra_field":"ignored","number":42}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.Success)
        assertEquals("AAAA...", response.tx)
        assertEquals("OK", response.message)
    }

    @Test
    fun testParsePendingWithZeroTimeout() = runTest {
        val jsonStr = """
            {"status":"pending","timeout":0,"message":"Processing"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.Pending)
        assertEquals(0, response.timeout)
        assertEquals("Processing", response.message)
    }

    @Test
    fun testParseActionRequiredWithEmptyActionFields() = runTest {
        val jsonStr = """
            {
                "status":"action_required",
                "message":"Verify identity",
                "action_url":"https://kyc.io/verify",
                "action_method":"POST",
                "action_fields":[]
            }
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostTransactionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostTransactionResponse.ActionRequired)
        assertNotNull(response.actionFields)
        assertTrue(response.actionFields!!.isEmpty())
    }

    @Test
    fun testParseActionDoneWithExtraFields() = runTest {
        val jsonStr = """
            {"result":"no_further_action_required","extra":"ignored"}
        """.trimIndent()

        val jsonObject = json.decodeFromString<JsonObject>(jsonStr)
        val response = Sep08PostActionResponse.fromJson(jsonObject)

        assertTrue(response is Sep08PostActionResponse.Done)
    }
}
