package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.*
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class TransactionsRequestBuilderTest {

    @Test
    fun testTransactionsRequestBuilderCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val transactionsBuilder = server.transactions()

        assertTrue(transactionsBuilder is com.soneso.stellar.sdk.horizon.requests.TransactionsRequestBuilder)

        server.close()
    }

    @Test
    fun testForAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val transactionsBuilder = server.transactions()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")

        val url = transactionsBuilder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/transactions"))

        server.close()
    }

    @Test
    fun testForClaimableBalance() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val balanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072"
        val transactionsBuilder = server.transactions()
            .forClaimableBalance(balanceId)

        val url = transactionsBuilder.buildUrl().toString()
        assertTrue(url.contains("claimable_balances/$balanceId/transactions"))

        server.close()
    }

    @Test
    fun testForLedger() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val transactionsBuilder = server.transactions()
            .forLedger(12345)

        val url = transactionsBuilder.buildUrl().toString()
        assertTrue(url.contains("ledgers/12345/transactions"))

        server.close()
    }

    @Test
    fun testForLiquidityPool() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val transactionsBuilder = server.transactions()
            .forLiquidityPool(poolId)

        val url = transactionsBuilder.buildUrl().toString()
        assertTrue(url.contains("liquidity_pools/$poolId/transactions"))

        server.close()
    }

    @Test
    fun testIncludeFailed() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val transactionsBuilder = server.transactions()
            .includeFailed(true)

        val url = transactionsBuilder.buildUrl().toString()
        assertTrue(url.contains("include_failed=true"))

        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val transactionsBuilder = server.transactions()
            .cursor("13537736921088")

        val url = transactionsBuilder.buildUrl().toString()
        assertTrue(url.contains("cursor=13537736921088"))

        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val transactionsBuilder = server.transactions()
            .limit(200)

        val url = transactionsBuilder.buildUrl().toString()
        assertTrue(url.contains("limit=200"))

        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val transactionsBuilder = server.transactions()
            .order(RequestBuilder.Order.DESC)

        val url = transactionsBuilder.buildUrl().toString()
        assertTrue(url.contains("order=desc"))

        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val transactionsBuilder = server.transactions()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
            .includeFailed(true)
            .cursor("13537736921088")
            .limit(10)
            .order(RequestBuilder.Order.ASC)

        val url = transactionsBuilder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/transactions"))
        assertTrue(url.contains("include_failed=true"))
        assertTrue(url.contains("cursor=13537736921088"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=asc"))

        server.close()
    }
}
