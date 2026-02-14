package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.EffectsRequestBuilder
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class EffectsRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.effects()
        assertTrue(builder is EffectsRequestBuilder)
        server.close()
    }

    @Test
    fun testForAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.effects()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/effects"))
        server.close()
    }

    @Test
    fun testForLedger() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.effects().forLedger(12345L)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("ledgers/12345/effects"))
        server.close()
    }

    @Test
    fun testForTransaction() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.effects().forTransaction("abc123def456")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("transactions/abc123def456/effects"))
        server.close()
    }

    @Test
    fun testForOperation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.effects().forOperation(107449584845914113L)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("operations/107449584845914113/effects"))
        server.close()
    }

    @Test
    fun testForLiquidityPool() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val poolId = "67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9"
        val builder = server.effects().forLiquidityPool(poolId)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("liquidity_pools/$poolId/effects"))
        server.close()
    }

    @Test
    fun testForClaimableBalance() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val balanceId = "00000000da0d57da7d4850e7fc10d2a9d0ebc731f7afb40574c03395b17d49149b91f5be"
        val builder = server.effects().forClaimableBalance(balanceId)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("claimable_balances/$balanceId/effects"))
        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.effects().cursor("cursor123")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=cursor123"))
        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.effects().limit(50)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("limit=50"))
        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.effects().order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.effects()
            .forAccount("GTEST")
            .cursor("c1")
            .limit(10)
            .order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("accounts/GTEST/effects"))
        assertTrue(url.contains("cursor=c1"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.effects()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/effects"))
        server.close()
    }
}
