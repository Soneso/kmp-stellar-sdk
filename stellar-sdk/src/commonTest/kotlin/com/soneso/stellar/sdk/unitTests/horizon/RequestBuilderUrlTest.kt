package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.*
import kotlin.test.*

/**
 * Unit tests for request builder URL construction.
 * Tests parameter handling and URL building without network calls.
 */
class RequestBuilderUrlTest {

    private val testUrl = "https://horizon-testnet.stellar.org"

    @Test
    fun testAccountsRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test basic accounts URL
        val builder1 = server.accounts()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("accounts"))
        
        // Test forSigner filter
        val builder2 = server.accounts().forSigner(accountId)
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("signer=$accountId"))
        
        // Test forAsset filter  
        val builder3 = server.accounts().forAsset("USD", accountId)
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("asset="))
        
        // Test forLiquidityPool filter
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val builder4 = server.accounts().forLiquidityPool(poolId)
        val url4 = builder4.buildUrl().toString()
        assertTrue(url4.contains("liquidity_pool=$poolId"))
        
        // Test forSponsor filter
        val builder5 = server.accounts().forSponsor(accountId)
        val url5 = builder5.buildUrl().toString()
        assertTrue(url5.contains("sponsor=$accountId"))
        
        server.close()
    }

    @Test
    fun testTransactionsRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test basic transactions URL
        val builder1 = server.transactions()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("transactions"))
        
        // Test forAccount
        val builder2 = server.transactions().forAccount(accountId)
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("accounts/$accountId/transactions"))
        
        // Test forLedger
        val builder3 = server.transactions().forLedger(12345L)
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("ledgers/12345/transactions"))
        
        // Test forClaimableBalance
        val cbId = "00000000da0d57da7d4850e7fc10d2a9d0ebc731f7afb40574c03395b17d49149b91f5be"
        val builder4 = server.transactions().forClaimableBalance(cbId)
        val url4 = builder4.buildUrl().toString()
        assertTrue(url4.contains("claimable_balances/$cbId/transactions"))
        
        // Test forLiquidityPool
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val builder5 = server.transactions().forLiquidityPool(poolId)
        val url5 = builder5.buildUrl().toString()
        assertTrue(url5.contains("liquidity_pools/$poolId/transactions"))
        
        server.close()
    }

    @Test
    fun testTransactionsRequestBuilderParameters() {
        val server = HorizonServer(testUrl)
        
        // Test includeFailed parameter
        val builder1 = server.transactions().includeFailed(true)
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("include_failed=true"))
        
        val builder2 = server.transactions().includeFailed(false)
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("include_failed=false"))
        
        server.close()
    }

    @Test
    fun testOperationsRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test basic operations URL
        val builder1 = server.operations()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("operations"))
        
        // Test forAccount
        val builder2 = server.operations().forAccount(accountId)
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("accounts/$accountId/operations"))
        
        // Test forTransaction
        val hash = "abc123def456"
        val builder3 = server.operations().forTransaction(hash)
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("transactions/$hash/operations"))
        
        server.close()
    }

    @Test
    fun testOperationsRequestBuilderParameters() {
        val server = HorizonServer(testUrl)
        
        // Test includeFailed parameter
        val builder1 = server.operations().includeFailed(true)
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("include_failed=true"))
        
        server.close()
    }

    @Test
    fun testPaymentsRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test basic payments URL
        val builder1 = server.payments()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("payments"))
        
        // Test forAccount
        val builder2 = server.payments().forAccount(accountId)
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("accounts/$accountId/payments"))
        
        // Test forTransaction
        val hash = "abc123def456"
        val builder3 = server.payments().forTransaction(hash)
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("transactions/$hash/payments"))
        
        server.close()
    }

    @Test
    fun testEffectsRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test basic effects URL
        val builder1 = server.effects()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("effects"))
        
        // Test forAccount
        val builder2 = server.effects().forAccount(accountId)
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("accounts/$accountId/effects"))
        
        // Test forTransaction
        val hash = "abc123def456"
        val builder3 = server.effects().forTransaction(hash)
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("transactions/$hash/effects"))
        
        // Test forOperation
        val builder4 = server.effects().forOperation(12345L)
        val url4 = builder4.buildUrl().toString()
        assertTrue(url4.contains("operations/12345/effects"))
        
        server.close()
    }

    @Test
    fun testLedgersRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        
        // Test basic ledgers URL
        val builder1 = server.ledgers()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("ledgers"))
        
        server.close()
    }

    @Test
    fun testOffersRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test basic offers URL
        val builder1 = server.offers()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("offers"))
        
        // Test forAccount (delegates to forSeller)
        val builder2 = server.offers().forAccount(accountId)
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("seller=$accountId"))
        
        // Test forSponsor
        val builder3 = server.offers().forSponsor(accountId)
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("sponsor=$accountId"))
        
        // Test forSeller
        val builder4 = server.offers().forSeller(accountId)
        val url4 = builder4.buildUrl().toString()
        assertTrue(url4.contains("seller=$accountId"))
        
        server.close()
    }

    @Test
    fun testOffersRequestBuilderAssetFilters() {
        val server = HorizonServer(testUrl)
        val issuer = "GABC123DEF456GHI789JKL012MNO345PQR678STU901VWX234YZA567BC"
        
        // Test forBuyingAsset
        val builder1 = server.offers().forBuyingAsset("credit_alphanum4", "USD", issuer)
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("buying="))
        
        // Test forSellingAsset native
        val builder2 = server.offers().forSellingAsset("native")
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("selling=native"))
        
        server.close()
    }

    @Test
    fun testTradesRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test basic trades URL
        val builder1 = server.trades()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("trades"))
        
        // Test forAccount
        val builder2 = server.trades().forAccount(accountId)
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("accounts/$accountId/trades"))
        
        // Test forLiquidityPool
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val builder3 = server.trades().forLiquidityPool(poolId)
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("liquidity_pools/$poolId/trades"))
        
        server.close()
    }

    @Test
    fun testTradesRequestBuilderParameters() {
        val server = HorizonServer(testUrl)
        
        // Test forOfferId
        val builder1 = server.trades().forOfferId(12345L)
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("offer_id=12345"))
        
        // Test forTradeType
        val builder2 = server.trades().forTradeType("orderbook")
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("trade_type=orderbook"))
        
        // Test forBaseAsset
        val builder3 = server.trades().forBaseAsset("native")
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("base_asset_type=native"))
        
        server.close()
    }

    @Test
    fun testAssetsRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test basic assets URL
        val builder1 = server.assets()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("assets"))
        
        // Test forAssetCode
        val builder2 = server.assets().forAssetCode("USD")
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("asset_code=USD"))
        
        // Test forAssetIssuer
        val builder3 = server.assets().forAssetIssuer(accountId)
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("asset_issuer=$accountId"))
        
        server.close()
    }

    @Test
    fun testClaimableBalancesRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test basic claimable_balances URL
        val builder1 = server.claimableBalances()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("claimable_balances"))
        
        // Test forSponsor
        val builder2 = server.claimableBalances().forSponsor(accountId)
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("sponsor=$accountId"))
        
        // Test forAsset
        val builder3 = server.claimableBalances().forAsset("native")
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("asset=native"))
        
        // Test forClaimant
        val builder4 = server.claimableBalances().forClaimant(accountId)
        val url4 = builder4.buildUrl().toString()
        assertTrue(url4.contains("claimant=$accountId"))
        
        server.close()
    }

    @Test
    fun testLiquidityPoolsRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test basic liquidity_pools URL
        val builder1 = server.liquidityPools()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("liquidity_pools"))
        
        // Test forReserves
        val builder2 = server.liquidityPools().forReserves("native", "USD:$accountId")
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("reserves="))
        
        // Test forAccount
        val builder3 = server.liquidityPools().forAccount(accountId)
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("account=$accountId"))
        
        server.close()
    }

    @Test
    fun testOrderBookRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val issuer = "GABC123DEF456GHI789JKL012MNO345PQR678STU901VWX234YZA567BC"
        
        // Test basic order_book URL
        val builder1 = server.orderBook()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("order_book"))
        
        // Test buyingAsset
        val builder2 = server.orderBook().buyingAsset("USD:$issuer")
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("buying_asset_type="))
        
        // Test sellingAsset
        val builder3 = server.orderBook().sellingAsset("native")
        val url3 = builder3.buildUrl().toString()
        assertTrue(url3.contains("selling_asset_type=native"))
        
        server.close()
    }

    @Test
    fun testStrictPathsRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        
        // Test strict send paths
        val builder1 = server.strictSendPaths()
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("paths/strict-send"))
        
        // Test strict receive paths
        val builder2 = server.strictReceivePaths()
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("paths/strict-receive"))
        
        server.close()
    }

    @Test
    fun testTradeAggregationsRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        val issuer = "GABC123DEF456GHI789JKL012MNO345PQR678STU901VWX234YZA567BC"
        
        // Test trade aggregations with all parameters
        val builder = server.tradeAggregations(
            baseAssetType = "native",
            baseAssetCode = null,
            baseAssetIssuer = null,
            counterAssetType = "credit_alphanum4",
            counterAssetCode = "USD",
            counterAssetIssuer = issuer,
            startTime = 1609459200000,
            endTime = 1640995200000,
            resolution = 3600000
        )
        
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("trade_aggregations"))
        assertTrue(url.contains("base_asset_type=native"))
        assertTrue(url.contains("counter_asset_type=credit_alphanum4"))
        assertTrue(url.contains("counter_asset_code=USD"))
        assertTrue(url.contains("counter_asset_issuer=$issuer"))
        assertTrue(url.contains("start_time=1609459200000"))
        assertTrue(url.contains("end_time=1640995200000"))
        assertTrue(url.contains("resolution=3600000"))
        
        server.close()
    }

    @Test
    fun testTradeAggregationsRequestBuilderWithOffset() {
        val server = HorizonServer(testUrl)
        
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
        
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("offset=30000"))
        
        server.close()
    }

    @Test
    fun testFeeStatsRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        
        val builder = server.feeStats()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("fee_stats"))
        
        server.close()
    }

    @Test
    fun testHealthRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        
        val builder = server.health()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("health"))
        
        server.close()
    }

    @Test
    fun testRootRequestBuilderUrls() {
        val server = HorizonServer(testUrl)
        
        val builder = server.root()
        val url = builder.buildUrl().toString()
        assertEquals("$testUrl/", url)
        
        server.close()
    }

    @Test
    fun testRequestBuilderParameterChaining() {
        val server = HorizonServer(testUrl)
        
        // Test chaining cursor, limit, and order parameters
        val builder = server.transactions()
            .cursor("123456789")
            .limit(50)
            .order(RequestBuilder.Order.DESC)
        
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=123456789"))
        assertTrue(url.contains("limit=50"))
        assertTrue(url.contains("order=desc"))
        
        server.close()
    }

    @Test
    fun testRequestBuilderOrderValues() {
        assertEquals("asc", RequestBuilder.Order.ASC.value)
        assertEquals("desc", RequestBuilder.Order.DESC.value)
    }

    @Test
    fun testUrlEncodingSpecialCharacters() {
        val server = HorizonServer(testUrl)
        val issuer = "GABC123DEF456GHI789JKL012MNO345PQR678STU901VWX234YZA567BC"
        
        // Test asset parameter with colon (should be URL encoded or handled properly)
        val builder = server.accounts().forAsset("USD", issuer)
        val url = builder.buildUrl().toString()
        
        // URL should contain the asset parameter
        assertTrue(url.contains("asset="))
        // Should contain USD and issuer in some form
        assertTrue(url.contains("USD") && url.contains(issuer))
        
        server.close()
    }

    @Test
    fun testLimitParameterBoundaries() {
        val server = HorizonServer(testUrl)
        
        // Test various limit values
        val builder1 = server.transactions().limit(1)
        val url1 = builder1.buildUrl().toString()
        assertTrue(url1.contains("limit=1"))
        
        val builder2 = server.transactions().limit(200)
        val url2 = builder2.buildUrl().toString()
        assertTrue(url2.contains("limit=200"))
        
        server.close()
    }

    @Test
    fun testParameterOverwriting() {
        val server = HorizonServer(testUrl)
        
        // Test that setting the same parameter multiple times uses the last value
        val builder = server.transactions()
            .cursor("first_cursor")
            .cursor("second_cursor")
            .cursor("final_cursor")
        
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=final_cursor"))
        assertFalse(url.contains("cursor=first_cursor"))
        assertFalse(url.contains("cursor=second_cursor"))
        
        server.close()
    }

    @Test
    fun testMultipleFiltersAndParameters() {
        val server = HorizonServer(testUrl)
        val accountId = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        
        // Test combining multiple filters with pagination parameters
        val builder = server.accounts()
            .forSponsor(accountId)
            .cursor("123456789")
            .limit(25)
            .order(RequestBuilder.Order.ASC)
        
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("sponsor=$accountId"))
        assertTrue(url.contains("cursor=123456789"))
        assertTrue(url.contains("limit=25"))
        assertTrue(url.contains("order=asc"))
        
        server.close()
    }

    @Test
    fun testBuilderMethodReturnsSelf() {
        val server = HorizonServer(testUrl)
        
        // Test that builder methods return the builder for chaining
        val builder = server.transactions()
        
        val withCursor = builder.cursor("test")
        val withLimit = withCursor.limit(10)
        val withOrder = withLimit.order(RequestBuilder.Order.DESC)
        
        // All should be non-null and allow chaining
        assertNotNull(withCursor)
        assertNotNull(withLimit)
        assertNotNull(withOrder)
        
        server.close()
    }
}