package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.*
import kotlin.test.*

/**
 * Tests for request builder methods not covered by existing tests.
 * Focuses on URL construction and parameter validation.
 */
class RequestBuilderBoostTest {

    private val testUrl = "https://horizon-testnet.stellar.org"

    // ===== RequestBuilder base class - Order enum =====

    @Test
    fun testOrderAscValue() {
        assertEquals("asc", RequestBuilder.Order.ASC.value)
    }

    @Test
    fun testOrderDescValue() {
        assertEquals("desc", RequestBuilder.Order.DESC.value)
    }

    // ===== TradesRequestBuilder =====

    @Test
    fun testTradesForAccount() {
        val server = HorizonServer(testUrl)
        val builder = server.trades()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/trades"))
        server.close()
    }

    @Test
    fun testTradesForLiquidityPool() {
        val server = HorizonServer(testUrl)
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val builder = server.trades()
            .forLiquidityPool(poolId)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("liquidity_pools/$poolId/trades"))
        server.close()
    }

    @Test
    fun testTradesForOfferId() {
        val server = HorizonServer(testUrl)
        val builder = server.trades()
            .forOfferId(12345L)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("offer_id=12345"))
        server.close()
    }

    @Test
    fun testTradesForOfferIdNull() {
        val server = HorizonServer(testUrl)
        val builder = server.trades()
            .forOfferId(12345L)
            .forOfferId(null) // Remove the filter
        val url = builder.buildUrl().toString()
        assertFalse(url.contains("offer_id"))
        server.close()
    }

    @Test
    fun testTradesForTradeType() {
        val server = HorizonServer(testUrl)
        val builder = server.trades()
            .forTradeType("orderbook")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("trade_type=orderbook"))
        server.close()
    }

    @Test
    fun testTradesForTradeTypeLiquidityPool() {
        val server = HorizonServer(testUrl)
        val builder = server.trades()
            .forTradeType("liquidity_pool")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("trade_type=liquidity_pool"))
        server.close()
    }

    @Test
    fun testTradesForBaseAsset() {
        val server = HorizonServer(testUrl)
        val builder = server.trades()
            .forBaseAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("base_asset_type=native"))
        server.close()
    }

    @Test
    fun testTradesForBaseAssetCredit() {
        val server = HorizonServer(testUrl)
        val issuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val builder = server.trades()
            .forBaseAsset("credit_alphanum4", "USD", issuer)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("base_asset_type=credit_alphanum4"))
        assertTrue(url.contains("base_asset_code=USD"))
        assertTrue(url.contains("base_asset_issuer=$issuer"))
        server.close()
    }

    @Test
    fun testTradesForCounterAsset() {
        val server = HorizonServer(testUrl)
        val builder = server.trades()
            .forCounterAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("counter_asset_type=native"))
        server.close()
    }

    @Test
    fun testTradesForCounterAssetCredit() {
        val server = HorizonServer(testUrl)
        val issuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val builder = server.trades()
            .forCounterAsset("credit_alphanum4", "EUR", issuer)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("counter_asset_type=credit_alphanum4"))
        assertTrue(url.contains("counter_asset_code=EUR"))
        assertTrue(url.contains("counter_asset_issuer=$issuer"))
        server.close()
    }

    @Test
    fun testTradesChainedPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.trades()
            .cursor("abc123")
            .limit(50)
            .order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=abc123"))
        assertTrue(url.contains("limit=50"))
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    // ===== OffersRequestBuilder =====

    @Test
    fun testOffersForSponsor() {
        val server = HorizonServer(testUrl)
        val builder = server.offers()
            .forSponsor("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("sponsor=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testOffersForSeller() {
        val server = HorizonServer(testUrl)
        val builder = server.offers()
            .forSeller("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("seller=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testOffersForAccount() {
        val server = HorizonServer(testUrl)
        val builder = server.offers()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        // forAccount calls forSeller internally
        assertTrue(url.contains("seller=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testOffersForBuyingAssetNative() {
        val server = HorizonServer(testUrl)
        val builder = server.offers()
            .forBuyingAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("buying=native"))
        server.close()
    }

    @Test
    fun testOffersForBuyingAssetCredit() {
        val server = HorizonServer(testUrl)
        val issuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val builder = server.offers()
            .forBuyingAsset("credit_alphanum4", "USD", issuer)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("buying=USD"))
        server.close()
    }

    @Test
    fun testOffersForSellingAssetNative() {
        val server = HorizonServer(testUrl)
        val builder = server.offers()
            .forSellingAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("selling=native"))
        server.close()
    }

    @Test
    fun testOffersForSellingAssetCredit() {
        val server = HorizonServer(testUrl)
        val issuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val builder = server.offers()
            .forSellingAsset("credit_alphanum4", "EUR", issuer)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("selling=EUR"))
        server.close()
    }

    @Test
    fun testOffersForBuyingAssetObject_Native() {
        val server = HorizonServer(testUrl)
        val asset = com.soneso.stellar.sdk.AssetTypeNative
        val builder = server.offers()
            .forBuyingAsset(asset)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("buying=native"))
        server.close()
    }

    @Test
    fun testOffersForBuyingAssetObject_CreditAlphaNum4() {
        val server = HorizonServer(testUrl)
        val issuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val asset = com.soneso.stellar.sdk.AssetTypeCreditAlphaNum4("USD", issuer)
        val builder = server.offers()
            .forBuyingAsset(asset)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("buying=USD"))
        server.close()
    }

    @Test
    fun testOffersForBuyingAssetObject_CreditAlphaNum12() {
        val server = HorizonServer(testUrl)
        val issuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val asset = com.soneso.stellar.sdk.AssetTypeCreditAlphaNum12("LONGASSETCOD", issuer)
        val builder = server.offers()
            .forBuyingAsset(asset)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("buying=LONGASSETCOD"))
        server.close()
    }

    @Test
    fun testOffersForSellingAssetObject_Native() {
        val server = HorizonServer(testUrl)
        val asset = com.soneso.stellar.sdk.AssetTypeNative
        val builder = server.offers()
            .forSellingAsset(asset)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("selling=native"))
        server.close()
    }

    @Test
    fun testOffersForSellingAssetObject_CreditAlphaNum4() {
        val server = HorizonServer(testUrl)
        val issuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val asset = com.soneso.stellar.sdk.AssetTypeCreditAlphaNum4("USD", issuer)
        val builder = server.offers()
            .forSellingAsset(asset)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("selling=USD"))
        server.close()
    }

    @Test
    fun testOffersForSellingAssetObject_CreditAlphaNum12() {
        val server = HorizonServer(testUrl)
        val issuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val asset = com.soneso.stellar.sdk.AssetTypeCreditAlphaNum12("LONGASSETCOD", issuer)
        val builder = server.offers()
            .forSellingAsset(asset)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("selling=LONGASSETCOD"))
        server.close()
    }

    @Test
    fun testOffersPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.offers()
            .cursor("test")
            .limit(25)
            .order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=test"))
        assertTrue(url.contains("limit=25"))
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    // ===== TransactionsRequestBuilder =====

    @Test
    fun testTransactionsForAccount() {
        val server = HorizonServer(testUrl)
        val builder = server.transactions()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/transactions"))
        server.close()
    }

    @Test
    fun testTransactionsForClaimableBalance() {
        val server = HorizonServer(testUrl)
        val cbId = "00000000da0d57da7d4850e7fc10d2a9d0ebc731f7afb40574c03395b17d49149b91f5be"
        val builder = server.transactions()
            .forClaimableBalance(cbId)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("claimable_balances/$cbId/transactions"))
        server.close()
    }

    @Test
    fun testTransactionsForLedger() {
        val server = HorizonServer(testUrl)
        val builder = server.transactions()
            .forLedger(12345L)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("ledgers/12345/transactions"))
        server.close()
    }

    @Test
    fun testTransactionsForLiquidityPool() {
        val server = HorizonServer(testUrl)
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val builder = server.transactions()
            .forLiquidityPool(poolId)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("liquidity_pools/$poolId/transactions"))
        server.close()
    }

    @Test
    fun testTransactionsIncludeFailed() {
        val server = HorizonServer(testUrl)
        val builder = server.transactions()
            .includeFailed(true)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("include_failed=true"))
        server.close()
    }

    @Test
    fun testTransactionsIncludeFailedFalse() {
        val server = HorizonServer(testUrl)
        val builder = server.transactions()
            .includeFailed(false)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("include_failed=false"))
        server.close()
    }

    @Test
    fun testTransactionsPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.transactions()
            .cursor("cur123")
            .limit(100)
            .order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=cur123"))
        assertTrue(url.contains("limit=100"))
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    // ===== OperationsRequestBuilder =====

    @Test
    fun testOperationsForAccount() {
        val server = HorizonServer(testUrl)
        val builder = server.operations()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/operations"))
        server.close()
    }

    @Test
    fun testOperationsForClaimableBalance() {
        val server = HorizonServer(testUrl)
        val cbId = "00000000da0d57da7d4850e7fc10d2a9d0ebc731f7afb40574c03395b17d49149b91f5be"
        val builder = server.operations()
            .forClaimableBalance(cbId)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("claimable_balances/$cbId/operations"))
        server.close()
    }

    @Test
    fun testOperationsForLedger() {
        val server = HorizonServer(testUrl)
        val builder = server.operations()
            .forLedger(12345L)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("ledgers/12345/operations"))
        server.close()
    }

    @Test
    fun testOperationsForTransaction() {
        val server = HorizonServer(testUrl)
        val builder = server.operations()
            .forTransaction("abc123hash")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("transactions/abc123hash/operations"))
        server.close()
    }

    @Test
    fun testOperationsForLiquidityPool() {
        val server = HorizonServer(testUrl)
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val builder = server.operations()
            .forLiquidityPool(poolId)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("liquidity_pools/$poolId/operations"))
        server.close()
    }

    @Test
    fun testOperationsIncludeFailed() {
        val server = HorizonServer(testUrl)
        val builder = server.operations()
            .includeFailed(true)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("include_failed=true"))
        server.close()
    }

    @Test
    fun testOperationsIncludeTransactionsTrue() {
        val server = HorizonServer(testUrl)
        val builder = server.operations()
            .includeTransactions(true)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("join=transactions"))
        server.close()
    }

    @Test
    fun testOperationsIncludeTransactionsFalse() {
        val server = HorizonServer(testUrl)
        val builder = server.operations()
            .includeTransactions(true)
            .includeTransactions(false)
        val url = builder.buildUrl().toString()
        assertFalse(url.contains("join="))
        server.close()
    }

    @Test
    fun testOperationsPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.operations()
            .cursor("op123")
            .limit(200)
            .order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=op123"))
        assertTrue(url.contains("limit=200"))
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    // ===== PaymentsRequestBuilder =====

    @Test
    fun testPaymentsForAccount() {
        val server = HorizonServer(testUrl)
        val builder = server.payments()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/payments"))
        server.close()
    }

    @Test
    fun testPaymentsForLedger() {
        val server = HorizonServer(testUrl)
        val builder = server.payments()
            .forLedger(99999L)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("ledgers/99999/payments"))
        server.close()
    }

    @Test
    fun testPaymentsForTransaction() {
        val server = HorizonServer(testUrl)
        val builder = server.payments()
            .forTransaction("txhash123")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("transactions/txhash123/payments"))
        server.close()
    }

    @Test
    fun testPaymentsIncludeTransactionsTrue() {
        val server = HorizonServer(testUrl)
        val builder = server.payments()
            .includeTransactions(true)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("join=transactions"))
        server.close()
    }

    @Test
    fun testPaymentsIncludeTransactionsFalse() {
        val server = HorizonServer(testUrl)
        val builder = server.payments()
            .includeTransactions(true)
            .includeTransactions(false)
        val url = builder.buildUrl().toString()
        assertFalse(url.contains("join="))
        server.close()
    }

    @Test
    fun testPaymentsIncludeFailed() {
        val server = HorizonServer(testUrl)
        val builder = server.payments()
            .includeFailed(true)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("include_failed=true"))
        server.close()
    }

    @Test
    fun testPaymentsPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.payments()
            .cursor("pay123")
            .limit(50)
            .order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=pay123"))
        assertTrue(url.contains("limit=50"))
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    // ===== EffectsRequestBuilder =====

    @Test
    fun testEffectsForAccount() {
        val server = HorizonServer(testUrl)
        val builder = server.effects()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/effects"))
        server.close()
    }

    @Test
    fun testEffectsForLedger() {
        val server = HorizonServer(testUrl)
        val builder = server.effects()
            .forLedger(12345L)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("ledgers/12345/effects"))
        server.close()
    }

    @Test
    fun testEffectsForTransaction() {
        val server = HorizonServer(testUrl)
        val builder = server.effects()
            .forTransaction("txhash")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("transactions/txhash/effects"))
        server.close()
    }

    @Test
    fun testEffectsForOperation() {
        val server = HorizonServer(testUrl)
        val builder = server.effects()
            .forOperation(99887766L)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("operations/99887766/effects"))
        server.close()
    }

    @Test
    fun testEffectsForLiquidityPool() {
        val server = HorizonServer(testUrl)
        val poolId = "poolid123"
        val builder = server.effects()
            .forLiquidityPool(poolId)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("liquidity_pools/$poolId/effects"))
        server.close()
    }

    @Test
    fun testEffectsForClaimableBalance() {
        val server = HorizonServer(testUrl)
        val cbId = "cbid123"
        val builder = server.effects()
            .forClaimableBalance(cbId)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("claimable_balances/$cbId/effects"))
        server.close()
    }

    @Test
    fun testEffectsPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.effects()
            .cursor("fx123")
            .limit(10)
            .order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=fx123"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    // ===== LedgersRequestBuilder =====

    @Test
    fun testLedgersPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.ledgers()
            .cursor("led123")
            .limit(15)
            .order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=led123"))
        assertTrue(url.contains("limit=15"))
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    // ===== LiquidityPoolsRequestBuilder =====

    @Test
    fun testLiquidityPoolsForReserves() {
        val server = HorizonServer(testUrl)
        val builder = server.liquidityPools()
            .forReserves("native", "USD:GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("reserves="))
        server.close()
    }

    @Test
    fun testLiquidityPoolsForReservesSingle() {
        val server = HorizonServer(testUrl)
        val builder = server.liquidityPools()
            .forReserves("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("reserves=native"))
        server.close()
    }

    @Test
    fun testLiquidityPoolsForReservesEmpty() {
        val server = HorizonServer(testUrl)
        assertFailsWith<IllegalArgumentException> {
            server.liquidityPools().forReserves()
        }
        server.close()
    }

    @Test
    fun testLiquidityPoolsForReservesTooMany() {
        val server = HorizonServer(testUrl)
        assertFailsWith<IllegalArgumentException> {
            server.liquidityPools().forReserves("a", "b", "c")
        }
        server.close()
    }

    @Test
    fun testLiquidityPoolsForAccount() {
        val server = HorizonServer(testUrl)
        val builder = server.liquidityPools()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("account=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testLiquidityPoolsPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.liquidityPools()
            .cursor("lp123")
            .limit(30)
            .order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=lp123"))
        assertTrue(url.contains("limit=30"))
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    // ===== ClaimableBalancesRequestBuilder =====

    @Test
    fun testClaimableBalancesForSponsor() {
        val server = HorizonServer(testUrl)
        val builder = server.claimableBalances()
            .forSponsor("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("sponsor=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testClaimableBalancesForAsset() {
        val server = HorizonServer(testUrl)
        val builder = server.claimableBalances()
            .forAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset=native"))
        server.close()
    }

    @Test
    fun testClaimableBalancesForAssetCredit() {
        val server = HorizonServer(testUrl)
        val issuer = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"
        val builder = server.claimableBalances()
            .forAsset("credit_alphanum4", "USD", issuer)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset=USD"))
        server.close()
    }

    @Test
    fun testClaimableBalancesForClaimant() {
        val server = HorizonServer(testUrl)
        val builder = server.claimableBalances()
            .forClaimant("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("claimant=GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU"))
        server.close()
    }

    @Test
    fun testClaimableBalancesPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.claimableBalances()
            .cursor("cb123")
            .limit(20)
            .order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=cb123"))
        assertTrue(url.contains("limit=20"))
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    // ===== AssetsRequestBuilder =====

    @Test
    fun testAssetsForAssetCode() {
        val server = HorizonServer(testUrl)
        val builder = server.assets()
            .forAssetCode("USD")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset_code=USD"))
        server.close()
    }

    @Test
    fun testAssetsForAssetIssuer() {
        val server = HorizonServer(testUrl)
        val builder = server.assets()
            .forAssetIssuer("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset_issuer=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testAssetsForBoth() {
        val server = HorizonServer(testUrl)
        val builder = server.assets()
            .forAssetCode("USD")
            .forAssetIssuer("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset_code=USD"))
        assertTrue(url.contains("asset_issuer=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testAssetsPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.assets()
            .cursor("asset123")
            .limit(5)
            .order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=asset123"))
        assertTrue(url.contains("limit=5"))
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    // ===== StrictReceivePathsRequestBuilder =====

    @Test
    fun testStrictReceivePathsDestinationAccount() {
        val server = HorizonServer(testUrl)
        val builder = server.strictReceivePaths()
            .destinationAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_account=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testStrictReceivePathsSourceAssets() {
        val server = HorizonServer(testUrl)
        val builder = server.strictReceivePaths()
            .sourceAssets(listOf(
                Triple("native", null, null),
                Triple("credit_alphanum4", "USD", "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
            ))
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("source_assets="))
        server.close()
    }

    @Test
    fun testStrictReceivePathsCannotSetBothSourceAccountAndAssets() {
        val server = HorizonServer(testUrl)
        assertFailsWith<IllegalArgumentException> {
            server.strictReceivePaths()
                .sourceAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
                .sourceAssets(listOf(Triple("native", null, null)))
        }
        server.close()
    }

    @Test
    fun testStrictReceivePathsCannotSetBothSourceAssetsAndAccount() {
        val server = HorizonServer(testUrl)
        assertFailsWith<IllegalArgumentException> {
            server.strictReceivePaths()
                .sourceAssets(listOf(Triple("native", null, null)))
                .sourceAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        }
        server.close()
    }

    @Test
    fun testStrictReceivePathsDestinationAmount() {
        val server = HorizonServer(testUrl)
        val builder = server.strictReceivePaths()
            .destinationAmount("50.0")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_amount=50.0"))
        server.close()
    }

    @Test
    fun testStrictReceivePathsDestinationAsset() {
        val server = HorizonServer(testUrl)
        val builder = server.strictReceivePaths()
            .destinationAsset("credit_alphanum4", "USD", "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_asset_type=credit_alphanum4"))
        assertTrue(url.contains("destination_asset_code=USD"))
        server.close()
    }

    @Test
    fun testStrictReceivePathsDestinationAssetNative() {
        val server = HorizonServer(testUrl)
        val builder = server.strictReceivePaths()
            .destinationAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_asset_type=native"))
        server.close()
    }

    @Test
    fun testStrictReceivePathsPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.strictReceivePaths()
            .cursor("p123")
            .limit(5)
            .order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=p123"))
        assertTrue(url.contains("limit=5"))
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    // ===== StrictSendPathsRequestBuilder =====

    @Test
    fun testStrictSendPathsDestinationAssets() {
        val server = HorizonServer(testUrl)
        val builder = server.strictSendPaths()
            .destinationAssets(listOf(
                Triple("native", null, null),
                Triple("credit_alphanum4", "EUR", "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
            ))
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_assets="))
        server.close()
    }

    @Test
    fun testStrictSendPathsCannotSetBothDestAccountAndAssets() {
        val server = HorizonServer(testUrl)
        assertFailsWith<IllegalArgumentException> {
            server.strictSendPaths()
                .destinationAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
                .destinationAssets(listOf(Triple("native", null, null)))
        }
        server.close()
    }

    @Test
    fun testStrictSendPathsCannotSetBothDestAssetsAndAccount() {
        val server = HorizonServer(testUrl)
        assertFailsWith<IllegalArgumentException> {
            server.strictSendPaths()
                .destinationAssets(listOf(Triple("native", null, null)))
                .destinationAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        }
        server.close()
    }

    @Test
    fun testStrictSendPathsSourceAsset() {
        val server = HorizonServer(testUrl)
        val builder = server.strictSendPaths()
            .sourceAsset("credit_alphanum4", "USD", "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("source_asset_type=credit_alphanum4"))
        assertTrue(url.contains("source_asset_code=USD"))
        server.close()
    }

    @Test
    fun testStrictSendPathsSourceAmount() {
        val server = HorizonServer(testUrl)
        val builder = server.strictSendPaths()
            .sourceAmount("100.0")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("source_amount=100.0"))
        server.close()
    }

    @Test
    fun testStrictSendPathsPagination() {
        val server = HorizonServer(testUrl)
        val builder = server.strictSendPaths()
            .cursor("ss123")
            .limit(10)
            .order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=ss123"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    // ===== RequestBuilder.setAssetParameter edge cases =====

    @Test
    fun testSetAssetParameterUnsupportedType() {
        val server = HorizonServer(testUrl)
        assertFailsWith<IllegalArgumentException> {
            // This triggers the else branch in setAssetParameter
            server.claimableBalances().forAsset("invalid_type", "CODE", "ISSUER")
        }
        server.close()
    }

    @Test
    fun testSetAssetParameterCreditWithoutCode() {
        val server = HorizonServer(testUrl)
        assertFailsWith<IllegalArgumentException> {
            server.claimableBalances().forAsset("credit_alphanum4", null, null)
        }
        server.close()
    }

    // ===== RequestBuilder segment validation =====

    @Test
    fun testCannotSetSegmentsTwice() {
        val server = HorizonServer(testUrl)
        // First call to forAccount sets segments
        val builder = server.operations().forAccount("TEST")
        // Second call should fail
        assertFailsWith<IllegalArgumentException> {
            builder.forLedger(123L)
        }
        server.close()
    }
}
