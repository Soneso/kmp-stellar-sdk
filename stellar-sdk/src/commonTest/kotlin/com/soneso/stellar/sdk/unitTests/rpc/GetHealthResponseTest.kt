package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.responses.GetHealthResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetHealthResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testHealthyResponseDeserialization() {
        val responseJson = """
        {
            "status": "healthy",
            "latestLedger": 1000,
            "oldestLedger": 900,
            "ledgerRetentionWindow": 100
        }
        """.trimIndent()

        val response = json.decodeFromString<GetHealthResponse>(responseJson)
        assertEquals("healthy", response.status)
        assertEquals(1000L, response.latestLedger)
        assertEquals(900L, response.oldestLedger)
        assertEquals(100L, response.ledgerRetentionWindow)
    }

    @Test
    fun testMinimalHealthyResponse() {
        val responseJson = """
        {
            "status": "healthy"
        }
        """.trimIndent()

        val response = json.decodeFromString<GetHealthResponse>(responseJson)
        assertEquals("healthy", response.status)
        assertNull(response.latestLedger)
        assertNull(response.oldestLedger)
        assertNull(response.ledgerRetentionWindow)
    }

    @Test
    fun testPartialLedgerDataResponse() {
        val responseJson = """
        {
            "status": "syncing",
            "latestLedger": 500,
            "ledgerRetentionWindow": 200
        }
        """.trimIndent()

        val response = json.decodeFromString<GetHealthResponse>(responseJson)
        assertEquals("syncing", response.status)
        assertEquals(500L, response.latestLedger)
        assertNull(response.oldestLedger)
        assertEquals(200L, response.ledgerRetentionWindow)
    }

    @Test
    fun testUnhealthyStatusResponse() {
        val responseJson = """
        {
            "status": "unhealthy",
            "latestLedger": 750,
            "oldestLedger": 650,
            "ledgerRetentionWindow": 150
        }
        """.trimIndent()

        val response = json.decodeFromString<GetHealthResponse>(responseJson)
        assertEquals("unhealthy", response.status)
        assertEquals(750L, response.latestLedger)
        assertEquals(650L, response.oldestLedger)
        assertEquals(150L, response.ledgerRetentionWindow)
    }

    @Test
    fun testLargeLedgerNumbers() {
        val responseJson = """
        {
            "status": "healthy",
            "latestLedger": 9223372036854775807,
            "oldestLedger": 1,
            "ledgerRetentionWindow": 999999999
        }
        """.trimIndent()

        val response = json.decodeFromString<GetHealthResponse>(responseJson)
        assertEquals("healthy", response.status)
        assertEquals(9223372036854775807L, response.latestLedger)
        assertEquals(1L, response.oldestLedger)
        assertEquals(999999999L, response.ledgerRetentionWindow)
    }

    @Test
    fun testZeroLedgerNumbers() {
        val responseJson = """
        {
            "status": "starting",
            "latestLedger": 0,
            "oldestLedger": 0,
            "ledgerRetentionWindow": 0
        }
        """.trimIndent()

        val response = json.decodeFromString<GetHealthResponse>(responseJson)
        assertEquals("starting", response.status)
        assertEquals(0L, response.latestLedger)
        assertEquals(0L, response.oldestLedger)
        assertEquals(0L, response.ledgerRetentionWindow)
    }

    @Test
    fun testNodeNotSyncedYet() {
        val responseJson = """
        {
            "status": "starting"
        }
        """.trimIndent()

        val response = json.decodeFromString<GetHealthResponse>(responseJson)
        assertEquals("starting", response.status)
        assertNull(response.latestLedger)
        assertNull(response.oldestLedger)
        assertNull(response.ledgerRetentionWindow)
    }

    @Test
    fun testVariousStatusStrings() {
        val statuses = listOf("healthy", "unhealthy", "syncing", "starting", "maintenance", "degraded")

        for (status in statuses) {
            val responseJson = """{"status": "$status"}"""
            val response = json.decodeFromString<GetHealthResponse>(responseJson)
            assertEquals(status, response.status)
        }
    }

    @Test
    fun testSerialization() {
        val response = GetHealthResponse(
            status = "healthy",
            latestLedger = 1234L,
            oldestLedger = 1000L,
            ledgerRetentionWindow = 234L
        )

        val serialized = json.encodeToString(GetHealthResponse.serializer(), response)
        val deserialized = json.decodeFromString<GetHealthResponse>(serialized)

        assertEquals(response.status, deserialized.status)
        assertEquals(response.latestLedger, deserialized.latestLedger)
        assertEquals(response.oldestLedger, deserialized.oldestLedger)
        assertEquals(response.ledgerRetentionWindow, deserialized.ledgerRetentionWindow)
    }

    @Test
    fun testEquality() {
        val response1 = GetHealthResponse(
            status = "healthy",
            latestLedger = 100L,
            oldestLedger = 50L,
            ledgerRetentionWindow = 50L
        )

        val response2 = GetHealthResponse(
            status = "healthy",
            latestLedger = 100L,
            oldestLedger = 50L,
            ledgerRetentionWindow = 50L
        )

        val response3 = GetHealthResponse(
            status = "unhealthy",
            latestLedger = 100L,
            oldestLedger = 50L,
            ledgerRetentionWindow = 50L
        )

        assertEquals(response1, response2)
        assertEquals(response1.hashCode(), response2.hashCode())
        assertTrue(response1 != response3)
    }

    @Test
    fun testToString() {
        val response = GetHealthResponse(
            status = "healthy",
            latestLedger = 100L,
            oldestLedger = 50L,
            ledgerRetentionWindow = 50L
        )

        val toStringResult = response.toString()
        assertTrue(toStringResult.contains("healthy"))
        assertTrue(toStringResult.contains("100"))
        assertTrue(toStringResult.contains("50"))
    }
}