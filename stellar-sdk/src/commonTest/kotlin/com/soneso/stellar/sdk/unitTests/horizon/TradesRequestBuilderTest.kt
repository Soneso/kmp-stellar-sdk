package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import com.soneso.stellar.sdk.horizon.requests.TradesRequestBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class TradesRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades()
        assertTrue(builder is TradesRequestBuilder)
        server.close()
    }

    @Test
    fun testForAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/trades"))
        server.close()
    }

    @Test
    fun testForLiquidityPool() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val poolId = "67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9"
        val builder = server.trades().forLiquidityPool(poolId)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("liquidity_pools/$poolId/trades"))
        server.close()
    }

    @Test
    fun testForOfferId() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades().forOfferId(12345L)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("offer_id=12345"))
        server.close()
    }

    @Test
    fun testForOfferIdNull() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades()
            .forOfferId(12345L)
            .forOfferId(null)
        val url = builder.buildUrl().toString()
        // After setting null, the parameter should be removed
        assertTrue(!url.contains("offer_id="))
        server.close()
    }

    @Test
    fun testForTradeType() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades().forTradeType("orderbook")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("trade_type=orderbook"))
        server.close()
    }

    @Test
    fun testForBaseAssetNative() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades().forBaseAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("base_asset_type=native"))
        server.close()
    }

    @Test
    fun testForBaseAssetCredit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades()
            .forBaseAsset("credit_alphanum4", "USD", "GISSUER")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("base_asset_type=credit_alphanum4"))
        assertTrue(url.contains("base_asset_code=USD"))
        assertTrue(url.contains("base_asset_issuer=GISSUER"))
        server.close()
    }

    @Test
    fun testForCounterAssetNative() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades().forCounterAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("counter_asset_type=native"))
        server.close()
    }

    @Test
    fun testForCounterAssetCredit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades()
            .forCounterAsset("credit_alphanum4", "EUR", "GISSUER")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("counter_asset_type=credit_alphanum4"))
        assertTrue(url.contains("counter_asset_code=EUR"))
        assertTrue(url.contains("counter_asset_issuer=GISSUER"))
        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades().cursor("trade_cursor")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=trade_cursor"))
        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades().limit(100)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("limit=100"))
        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades().order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades()
            .forBaseAsset("native")
            .forCounterAsset("credit_alphanum4", "USD", "GISSUER")
            .forTradeType("orderbook")
            .cursor("c1")
            .limit(20)
            .order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("base_asset_type=native"))
        assertTrue(url.contains("counter_asset_type=credit_alphanum4"))
        assertTrue(url.contains("counter_asset_code=USD"))
        assertTrue(url.contains("trade_type=orderbook"))
        assertTrue(url.contains("cursor=c1"))
        assertTrue(url.contains("limit=20"))
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.trades()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/trades"))
        server.close()
    }
}
