package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.*
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class LedgersRequestBuilderTest {

    @Test
    fun testLedgersRequestBuilderCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val ledgersBuilder = server.ledgers()

        assertTrue(ledgersBuilder is com.soneso.stellar.sdk.horizon.requests.LedgersRequestBuilder)

        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val ledgersBuilder = server.ledgers()
            .cursor("13537736921088")

        val url = ledgersBuilder.buildUrl().toString()
        assertTrue(url.contains("cursor=13537736921088"))

        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val ledgersBuilder = server.ledgers()
            .limit(200)

        val url = ledgersBuilder.buildUrl().toString()
        assertTrue(url.contains("limit=200"))

        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val ledgersBuilder = server.ledgers()
            .order(RequestBuilder.Order.DESC)

        val url = ledgersBuilder.buildUrl().toString()
        assertTrue(url.contains("order=desc"))

        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val ledgersBuilder = server.ledgers()
            .cursor("13537736921088")
            .limit(10)
            .order(RequestBuilder.Order.ASC)

        val url = ledgersBuilder.buildUrl().toString()
        assertTrue(url.contains("cursor=13537736921088"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=asc"))

        server.close()
    }
}
