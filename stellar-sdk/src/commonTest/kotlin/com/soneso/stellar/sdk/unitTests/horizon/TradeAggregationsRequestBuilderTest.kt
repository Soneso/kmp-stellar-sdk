package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import com.soneso.stellar.sdk.horizon.requests.TradeAggregationsRequestBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class TradeAggregationsRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "credit_alphanum4",
            counterAssetCode = "USD",
            counterAssetIssuer = "GISSUER",
            startTime = 1609459200000,
            endTime = 1609545600000,
            resolution = 3600000,
            offset = 0
        )
        assertTrue(builder is TradeAggregationsRequestBuilder)
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "native",
            counterAssetCode = null,
            counterAssetIssuer = null,
            startTime = 1000,
            endTime = 2000,
            resolution = 60000,
            offset = 0
        )
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/trade_aggregations"))
        server.close()
    }

    @Test
    fun testBaseAssetParameters() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.tradeAggregations(
            baseAssetType = "credit_alphanum4",
            baseAssetCode = "EUR",
            baseAssetIssuer = "GBASE",
            counterAssetType = "native",
            counterAssetCode = null,
            counterAssetIssuer = null,
            startTime = 1000,
            endTime = 2000,
            resolution = 60000,
            offset = 0
        )
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("base_asset_type=credit_alphanum4"))
        assertTrue(url.contains("base_asset_code=EUR"))
        assertTrue(url.contains("base_asset_issuer=GBASE"))
        server.close()
    }

    @Test
    fun testCounterAssetParameters() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "credit_alphanum4",
            counterAssetCode = "USD",
            counterAssetIssuer = "GCOUNTER",
            startTime = 1000,
            endTime = 2000,
            resolution = 60000,
            offset = 0
        )
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("counter_asset_type=credit_alphanum4"))
        assertTrue(url.contains("counter_asset_code=USD"))
        assertTrue(url.contains("counter_asset_issuer=GCOUNTER"))
        server.close()
    }

    @Test
    fun testTimeAndResolutionParameters() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "native",
            counterAssetCode = null,
            counterAssetIssuer = null,
            startTime = 1609459200000,
            endTime = 1609545600000,
            resolution = 3600000,
            offset = 1800000
        )
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("start_time=1609459200000"))
        assertTrue(url.contains("end_time=1609545600000"))
        assertTrue(url.contains("resolution=3600000"))
        assertTrue(url.contains("offset=1800000"))
        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "native",
            counterAssetCode = null,
            counterAssetIssuer = null,
            startTime = 1000,
            endTime = 2000,
            resolution = 60000,
            offset = 0
        ).cursor("agg_cursor")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=agg_cursor"))
        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "native",
            counterAssetCode = null,
            counterAssetIssuer = null,
            startTime = 1000,
            endTime = 2000,
            resolution = 60000,
            offset = 0
        ).limit(50)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("limit=50"))
        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "native",
            counterAssetCode = null,
            counterAssetIssuer = null,
            startTime = 1000,
            endTime = 2000,
            resolution = 60000,
            offset = 0
        ).order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    @Test
    fun testNativeBaseAsset() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "native",
            counterAssetCode = null,
            counterAssetIssuer = null,
            startTime = 1000,
            endTime = 2000,
            resolution = 60000,
            offset = 0
        )
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("base_asset_type=native"))
        server.close()
    }
}
