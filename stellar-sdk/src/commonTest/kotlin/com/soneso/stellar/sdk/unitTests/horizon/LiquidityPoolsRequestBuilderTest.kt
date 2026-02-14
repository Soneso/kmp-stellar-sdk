package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.LiquidityPoolsRequestBuilder
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LiquidityPoolsRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.liquidityPools()
        assertTrue(builder is LiquidityPoolsRequestBuilder)
        server.close()
    }

    @Test
    fun testForReservesSingle() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.liquidityPools()
            .forReserves("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("reserves=native"))
        server.close()
    }

    @Test
    fun testForReservesMultiple() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.liquidityPools()
            .forReserves("native", "USD:GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("reserves="))
        assertTrue(url.contains("native"))
        server.close()
    }

    @Test
    fun testForReservesEmpty() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<IllegalArgumentException> {
            server.liquidityPools().forReserves()
        }
        server.close()
    }

    @Test
    fun testForReservesTooMany() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<IllegalArgumentException> {
            server.liquidityPools().forReserves("a", "b", "c")
        }
        server.close()
    }

    @Test
    fun testForAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.liquidityPools()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("account=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.liquidityPools().cursor("pool_cursor")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=pool_cursor"))
        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.liquidityPools().limit(50)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("limit=50"))
        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.liquidityPools().order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.liquidityPools()
            .forReserves("native")
            .cursor("c1")
            .limit(10)
            .order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("reserves=native"))
        assertTrue(url.contains("cursor=c1"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.liquidityPools()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/liquidity_pools"))
        server.close()
    }
}
