package com.soneso.stellar.sdk.unitTests.horizon.requests

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
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
    fun testAssetParameterRejectsUnsupportedType() {
        val server = HorizonServer(testUrl)

        assertFailsWith<IllegalArgumentException> {
            server.claimableBalances().forAsset("invalid_type", "CODE", "ISSUER")
        }

        server.close()
    }

    @Test
    fun testAssetParameterRejectsCreditAssetWithoutCodeAndIssuer() {
        val server = HorizonServer(testUrl)

        assertFailsWith<IllegalArgumentException> {
            server.claimableBalances().forAsset("credit_alphanum4", null, null)
        }

        server.close()
    }

    @Test
    fun testSegmentsCannotBeSetTwice() {
        val server = HorizonServer(testUrl)

        // The first filter sets the URL segments, a second one must be rejected
        val builder = server.operations().forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        assertFailsWith<IllegalArgumentException> {
            builder.forLedger(12345L)
        }

        server.close()
    }

    @Test
    fun testBuilderMethodReturnsSelf() {
        val server = HorizonServer(testUrl)

        // Pagination setters mutate and return the same builder, so chaining accumulates
        val builder = server.transactions()

        assertSame(builder, builder.cursor("test"))
        assertSame(builder, builder.limit(10))
        assertSame(builder, builder.order(RequestBuilder.Order.DESC))

        val url = builder.buildUrl()
        assertEquals("test", url.parameters["cursor"])
        assertEquals("10", url.parameters["limit"])
        assertEquals("desc", url.parameters["order"])

        server.close()
    }

    @Test
    fun testBuildUrlIsIdempotent() {
        val server = HorizonServer(testUrl)

        // SSEStream rebuilds the URL on every reconnect, so buildUrl() must not mutate the
        // builder: a second call has to yield the same endpoint, not an appended one.
        val builder = server.transactions().forLedger(12345L).limit(10)

        val first = builder.buildUrl()
        val second = builder.buildUrl()

        assertEquals("$testUrl/ledgers/12345/transactions?limit=10", first.toString())
        assertEquals(first.toString(), second.toString())
        assertEquals("/ledgers/12345/transactions", second.encodedPath)

        server.close()
    }

    @Test
    fun testBuildUrlIsIdempotentForADefaultSegmentEndpoint() {
        val server = HorizonServer(testUrl)

        // An endpoint whose path comes from the default segment must be equally stable
        val builder = server.accounts()

        assertEquals("$testUrl/accounts", builder.buildUrl().toString())
        assertEquals("$testUrl/accounts", builder.buildUrl().toString())
        assertEquals("$testUrl/accounts", builder.buildUrl().toString())

        server.close()
    }

    // ===== Base class path and parameter helpers =====

    private val issuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"

    /** Client for builders that are only used to construct URLs, never to send requests. */
    private fun offlineClient(): HttpClient = HttpClient(MockEngine { respond("") })

    @Test
    fun testSegmentsAreAppendedToServerPathAndCanOnlyBeSetOnce() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        assertFalse(builder.segmentsAlreadySet)

        builder.applySegments("accounts", issuer)
        assertTrue(builder.segmentsAlreadySet)
        assertEquals("$testUrl/accounts/$issuer", builder.buildUrl().toString())

        val error = assertFailsWith<IllegalArgumentException> { builder.applySegments("ledgers") }
        assertEquals("URL segments have been already added.", error.message)

        client.close()
    }

    @Test
    fun testDefaultSegmentCanBeReplacedByAFilterSegment() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl), defaultSegment = "accounts")

        // A default segment does not consume the one-shot segment assignment
        assertFalse(builder.segmentsAlreadySet)
        assertEquals("$testUrl/accounts", builder.buildUrl().toString())

        val filtered = ParameterAccessRequestBuilder(client, Url(testUrl), defaultSegment = "accounts")
        filtered.applySegments("accounts", issuer, "data", "config.memo_required")
        assertEquals("$testUrl/accounts/$issuer/data/config.memo_required", filtered.buildUrl().toString())

        client.close()
    }

    @Test
    fun testBuildUrlWithoutSegmentsKeepsServerPath() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        val url = builder.buildUrl()
        assertEquals("", url.encodedPath.trimEnd('/'))
        assertEquals("horizon-testnet.stellar.org", url.host)

        client.close()
    }

    @Test
    fun testBuildUrlAppendsSegmentsToServerSubPathWithoutDoubleSlash() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url("https://example.org/horizon/"))

        builder.applySegments("transactions", "abc123")

        assertEquals("https://example.org/horizon/transactions/abc123", builder.buildUrl().toString())

        client.close()
    }

    @Test
    fun testPaginationParametersAreSetOnTheBaseBuilder() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        builder.cursor("cursor-1").limit(37).order(RequestBuilder.Order.DESC)

        val url = builder.buildUrl()
        assertEquals("cursor-1", url.parameters["cursor"])
        assertEquals("37", url.parameters["limit"])
        assertEquals("desc", url.parameters["order"])
        assertEquals("", url.encodedPath.trimEnd('/'))

        client.close()
    }

    @Test
    fun testAssetParameterEncodesNativeAndCreditAssets() {
        val client = offlineClient()

        val native = ParameterAccessRequestBuilder(client, Url(testUrl))
        native.applyNativeAssetParameter("selling")
        assertEquals("native", native.buildUrl().parameters["selling"])

        val alphaNum4 = ParameterAccessRequestBuilder(client, Url(testUrl))
        alphaNum4.applyAssetParameter("selling", "credit_alphanum4", "USD", issuer)
        assertEquals("USD:$issuer", alphaNum4.buildUrl().parameters["selling"])

        val alphaNum12 = ParameterAccessRequestBuilder(client, Url(testUrl))
        alphaNum12.applyAssetParameter("buying", "credit_alphanum12", "LONGASSET", issuer)
        assertEquals("LONGASSET:$issuer", alphaNum12.buildUrl().parameters["buying"])

        client.close()
    }

    @Test
    fun testAssetParameterRejectsCreditAssetWithMissingIssuer() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        val error = assertFailsWith<IllegalArgumentException> {
            builder.applyAssetParameter("selling", "credit_alphanum4", "USD", null)
        }
        assertEquals("Asset code and issuer must be provided for credit assets", error.message)
        assertNull(builder.buildUrl().parameters["selling"])

        client.close()
    }

    @Test
    fun testAssetParameterRejectsUnknownAssetType() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        val error = assertFailsWith<IllegalArgumentException> {
            builder.applyAssetParameter("selling", "credit_alphanum8", "USDCOIN", issuer)
        }
        assertEquals("Unsupported asset type: credit_alphanum8", error.message)

        client.close()
    }

    @Test
    fun testAssetTypeParametersOmitAbsentCodeAndIssuer() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        builder.applyAssetTypeParameters("base", "native")

        val url = builder.buildUrl()
        assertEquals("native", url.parameters["base_asset_type"])
        assertNull(url.parameters["base_asset_code"])
        assertNull(url.parameters["base_asset_issuer"])

        client.close()
    }

    @Test
    fun testAssetTypeParametersIncludeCodeAndIssuer() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        builder.applyAssetTypeParameters("counter", "credit_alphanum4", "USD", issuer)

        val url = builder.buildUrl()
        assertEquals("credit_alphanum4", url.parameters["counter_asset_type"])
        assertEquals("USD", url.parameters["counter_asset_code"])
        assertEquals(issuer, url.parameters["counter_asset_issuer"])

        client.close()
    }

    @Test
    fun testAssetTypeParametersIncludeCodeWithoutIssuer() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        builder.applyAssetTypeParameters("base", "credit_alphanum4", "USD", null)

        val url = builder.buildUrl()
        assertEquals("credit_alphanum4", url.parameters["base_asset_type"])
        assertEquals("USD", url.parameters["base_asset_code"])
        assertNull(url.parameters["base_asset_issuer"])

        client.close()
    }

    @Test
    fun testEncodeAssetProducesCanonicalRepresentation() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        assertEquals("native", builder.encodeNativeAsset())
        assertEquals("USD:$issuer", builder.encode("credit_alphanum4", "USD", issuer))
        assertEquals("LONGASSET:$issuer", builder.encode("credit_alphanum12", "LONGASSET", issuer))

        client.close()
    }

    @Test
    fun testEncodeAssetRejectsCreditAssetWithoutCode() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        val error = assertFailsWith<IllegalArgumentException> {
            builder.encode("credit_alphanum12", null, issuer)
        }
        assertEquals("Asset code and issuer must be provided for credit assets", error.message)

        client.close()
    }

    @Test
    fun testEncodeAssetRejectsUnknownAssetType() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        val error = assertFailsWith<IllegalArgumentException> {
            builder.encode("liquidity_pool_shares", "POOL", issuer)
        }
        assertEquals("Unsupported asset type: liquidity_pool_shares", error.message)

        client.close()
    }

    @Test
    fun testAssetsParameterJoinsEncodedAssetsWithComma() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        builder.applyAssetsParameter(
            "destination_assets",
            listOf(
                Triple("native", null, null),
                Triple("credit_alphanum4", "USD", issuer),
                Triple("credit_alphanum12", "LONGASSET", issuer)
            )
        )

        assertEquals(
            "native,USD:$issuer,LONGASSET:$issuer",
            builder.buildUrl().parameters["destination_assets"]
        )

        client.close()
    }

    @Test
    fun testAssetsParameterWithEmptyListProducesEmptyValue() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        builder.applyAssetsParameter("destination_assets", emptyList())

        assertEquals("", builder.buildUrl().parameters["destination_assets"])

        client.close()
    }

    @Test
    fun testAssetsParameterRejectsUnknownAssetTypeInList() {
        val client = offlineClient()
        val builder = ParameterAccessRequestBuilder(client, Url(testUrl))

        val error = assertFailsWith<IllegalArgumentException> {
            builder.applyAssetsParameter(
                "destination_assets",
                listOf(Triple("native", null, null), Triple("pool_share", null, null))
            )
        }
        assertEquals("Unsupported asset type: pool_share", error.message)
        assertNull(builder.buildUrl().parameters["destination_assets"])

        client.close()
    }
}

/**
 * Concrete [RequestBuilder] that exposes the protected path and asset helpers shared by all
 * endpoint builders, so they can be exercised independently of a specific endpoint.
 */
private class ParameterAccessRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url,
    defaultSegment: String? = null
) : RequestBuilder(httpClient, serverUri, defaultSegment) {

    val segmentsAlreadySet: Boolean
        get() = segmentsAdded

    fun applySegments(vararg values: String): RequestBuilder = setSegments(*values)

    fun applyNativeAssetParameter(parameterName: String) =
        setAssetParameter(parameterName, "native")

    fun applyAssetParameter(parameterName: String, assetType: String, assetCode: String?, assetIssuer: String?) =
        setAssetParameter(parameterName, assetType, assetCode, assetIssuer)

    fun applyAssetTypeParameters(prefix: String, assetType: String) =
        setAssetTypeParameters(prefix, assetType)

    fun applyAssetTypeParameters(prefix: String, assetType: String, assetCode: String?, assetIssuer: String?) =
        setAssetTypeParameters(prefix, assetType, assetCode, assetIssuer)

    fun encodeNativeAsset(): String = encodeAsset("native")

    fun encode(assetType: String, assetCode: String?, assetIssuer: String?): String =
        encodeAsset(assetType, assetCode, assetIssuer)

    fun applyAssetsParameter(parameterName: String, assets: List<Triple<String, String?, String?>>) =
        setAssetsParameter(parameterName, assets)
}