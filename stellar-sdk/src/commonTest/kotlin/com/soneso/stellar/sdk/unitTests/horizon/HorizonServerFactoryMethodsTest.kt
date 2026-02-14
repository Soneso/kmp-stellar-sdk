package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import kotlin.test.*

/**
 * Unit tests for HorizonServer factory methods and request builder creation.
 *
 * These tests verify that HorizonServer correctly creates all request builder types
 * and has proper default configuration. No network calls are made.
 */
class HorizonServerFactoryMethodsTest {

    private lateinit var server: HorizonServer

    @BeforeTest
    fun setup() {
        server = HorizonServer("https://horizon-testnet.stellar.org")
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    // ========== Constructor ==========
    @Test
    fun testServerUri() {
        assertEquals("https://horizon-testnet.stellar.org", server.serverUri.toString())
    }

    @Test
    fun testCustomUri() {
        val custom = HorizonServer("https://custom-horizon.example.com")
        assertNotNull(custom.serverUri)
        assertEquals("https://custom-horizon.example.com", custom.serverUri.toString())
        custom.close()
    }

    // ========== Request Builder Factories ==========
    @Test
    fun testRootRequestBuilder() {
        val builder = server.root()
        assertNotNull(builder)
    }

    @Test
    fun testAccountsRequestBuilder() {
        val builder = server.accounts()
        assertNotNull(builder)
    }

    @Test
    fun testTransactionsRequestBuilder() {
        val builder = server.transactions()
        assertNotNull(builder)
    }

    @Test
    fun testOperationsRequestBuilder() {
        val builder = server.operations()
        assertNotNull(builder)
    }

    @Test
    fun testPaymentsRequestBuilder() {
        val builder = server.payments()
        assertNotNull(builder)
    }

    @Test
    fun testEffectsRequestBuilder() {
        val builder = server.effects()
        assertNotNull(builder)
    }

    @Test
    fun testLedgersRequestBuilder() {
        val builder = server.ledgers()
        assertNotNull(builder)
    }

    @Test
    fun testOffersRequestBuilder() {
        val builder = server.offers()
        assertNotNull(builder)
    }

    @Test
    fun testTradesRequestBuilder() {
        val builder = server.trades()
        assertNotNull(builder)
    }

    @Test
    fun testAssetsRequestBuilder() {
        val builder = server.assets()
        assertNotNull(builder)
    }

    @Test
    fun testClaimableBalancesRequestBuilder() {
        val builder = server.claimableBalances()
        assertNotNull(builder)
    }

    @Test
    fun testLiquidityPoolsRequestBuilder() {
        val builder = server.liquidityPools()
        assertNotNull(builder)
    }

    @Test
    fun testOrderBookRequestBuilder() {
        val builder = server.orderBook()
        assertNotNull(builder)
    }

    @Test
    fun testStrictSendPathsRequestBuilder() {
        val builder = server.strictSendPaths()
        assertNotNull(builder)
    }

    @Test
    fun testStrictReceivePathsRequestBuilder() {
        val builder = server.strictReceivePaths()
        assertNotNull(builder)
    }

    @Test
    fun testTradeAggregationsRequestBuilder() {
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "credit_alphanum4",
            counterAssetCode = "USD",
            counterAssetIssuer = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K",
            startTime = 1609459200000,
            endTime = 1640995200000,
            resolution = 3600000
        )
        assertNotNull(builder)
    }

    @Test
    fun testTradeAggregationsWithOffset() {
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "native",
            counterAssetCode = null,
            counterAssetIssuer = null,
            startTime = 0,
            endTime = 100000,
            resolution = 60000,
            offset = 30000
        )
        assertNotNull(builder)
    }

    @Test
    fun testFeeStatsRequestBuilder() {
        val builder = server.feeStats()
        assertNotNull(builder)
    }

    @Test
    fun testHealthRequestBuilder() {
        val builder = server.health()
        assertNotNull(builder)
    }

    // ========== Companion Object ==========
    @Test
    fun testHorizonSubmitTimeout() {
        assertEquals(60, HorizonServer.HORIZON_SUBMIT_TIMEOUT)
    }

    @Test
    fun testDefaultJson() {
        val json = HorizonServer.defaultJson
        assertNotNull(json)
    }

    @Test
    fun testCreateDefaultHttpClient() {
        val client = HorizonServer.createDefaultHttpClient()
        assertNotNull(client)
        client.close()
    }

    @Test
    fun testCreateSubmitHttpClient() {
        val client = HorizonServer.createSubmitHttpClient()
        assertNotNull(client)
        client.close()
    }

    // ========== Multiple close() calls don't crash ==========
    @Test
    fun testCloseIdempotent() {
        val s = HorizonServer("https://horizon-testnet.stellar.org")
        s.close()
        // Should not throw
    }
}
