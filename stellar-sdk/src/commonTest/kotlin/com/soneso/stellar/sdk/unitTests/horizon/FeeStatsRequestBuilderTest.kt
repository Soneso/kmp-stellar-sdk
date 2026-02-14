package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.FeeStatsRequestBuilder
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FeeStatsRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.feeStats()
        assertTrue(builder is FeeStatsRequestBuilder)
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.feeStats()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/fee_stats"))
        server.close()
    }

    @Test
    fun testCursorNotSupported() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<UnsupportedOperationException> {
            server.feeStats().cursor("test")
        }
        server.close()
    }

    @Test
    fun testLimitNotSupported() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<UnsupportedOperationException> {
            server.feeStats().limit(10)
        }
        server.close()
    }

    @Test
    fun testOrderNotSupported() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<UnsupportedOperationException> {
            server.feeStats().order(RequestBuilder.Order.ASC)
        }
        server.close()
    }
}
