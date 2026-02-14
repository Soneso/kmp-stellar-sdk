package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubmitTransactionAsyncResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testPendingDeserialization() {
        val responseJson = """
        {
            "hash": "abc123def456",
            "tx_status": "PENDING"
        }
        """.trimIndent()
        val response = json.decodeFromString<SubmitTransactionAsyncResponse>(responseJson)
        assertEquals("abc123def456", response.hash)
        assertEquals(SubmitTransactionAsyncResponse.TransactionStatus.PENDING, response.txStatus)
        assertNull(response.errorResultXdr)
        assertEquals(0, response.httpResponseCode)
    }

    @Test
    fun testErrorDeserialization() {
        val responseJson = """
        {
            "hash": "abc123def456",
            "tx_status": "ERROR",
            "error_result_xdr": "AAAAAAAAAGT/////AAAAAQAAAAAAAAAB////+QAAAAA="
        }
        """.trimIndent()
        val response = json.decodeFromString<SubmitTransactionAsyncResponse>(responseJson)
        assertEquals("abc123def456", response.hash)
        assertEquals(SubmitTransactionAsyncResponse.TransactionStatus.ERROR, response.txStatus)
        assertEquals("AAAAAAAAAGT/////AAAAAQAAAAAAAAAB////+QAAAAA=", response.errorResultXdr)
    }

    @Test
    fun testDuplicateDeserialization() {
        val responseJson = """
        {
            "hash": "abc123",
            "tx_status": "DUPLICATE"
        }
        """.trimIndent()
        val response = json.decodeFromString<SubmitTransactionAsyncResponse>(responseJson)
        assertEquals(SubmitTransactionAsyncResponse.TransactionStatus.DUPLICATE, response.txStatus)
    }

    @Test
    fun testTryAgainLaterDeserialization() {
        val responseJson = """
        {
            "hash": "abc123",
            "tx_status": "TRY_AGAIN_LATER"
        }
        """.trimIndent()
        val response = json.decodeFromString<SubmitTransactionAsyncResponse>(responseJson)
        assertEquals(SubmitTransactionAsyncResponse.TransactionStatus.TRY_AGAIN_LATER, response.txStatus)
    }

    @Test
    fun testHttpResponseCodeDefault() {
        val response = SubmitTransactionAsyncResponse(
            hash = "abc",
            txStatus = SubmitTransactionAsyncResponse.TransactionStatus.PENDING
        )
        assertEquals(0, response.httpResponseCode)
    }

    @Test
    fun testHttpResponseCodeSet() {
        val response = SubmitTransactionAsyncResponse(
            hash = "abc",
            txStatus = SubmitTransactionAsyncResponse.TransactionStatus.PENDING
        )
        response.httpResponseCode = 201
        assertEquals(201, response.httpResponseCode)
    }

    @Test
    fun testAllTransactionStatuses() {
        val statuses = SubmitTransactionAsyncResponse.TransactionStatus.entries
        assertEquals(4, statuses.size)
        assertTrue(statuses.contains(SubmitTransactionAsyncResponse.TransactionStatus.ERROR))
        assertTrue(statuses.contains(SubmitTransactionAsyncResponse.TransactionStatus.PENDING))
        assertTrue(statuses.contains(SubmitTransactionAsyncResponse.TransactionStatus.DUPLICATE))
        assertTrue(statuses.contains(SubmitTransactionAsyncResponse.TransactionStatus.TRY_AGAIN_LATER))
    }

    private fun assertTrue(condition: Boolean) {
        kotlin.test.assertTrue(condition)
    }
}
