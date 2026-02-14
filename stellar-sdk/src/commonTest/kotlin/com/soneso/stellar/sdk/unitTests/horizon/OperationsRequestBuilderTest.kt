package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.*
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class OperationsRequestBuilderTest {

    @Test
    fun testOperationsRequestBuilderCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val operationsBuilder = server.operations()

        assertTrue(operationsBuilder is com.soneso.stellar.sdk.horizon.requests.OperationsRequestBuilder)

        server.close()
    }

    @Test
    fun testForAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val operationsBuilder = server.operations()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/operations"))

        server.close()
    }

    @Test
    fun testForClaimableBalance() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val balanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072"
        val operationsBuilder = server.operations()
            .forClaimableBalance(balanceId)

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("claimable_balances/$balanceId/operations"))

        server.close()
    }

    @Test
    fun testForLedger() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val operationsBuilder = server.operations()
            .forLedger(12345)

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("ledgers/12345/operations"))

        server.close()
    }

    @Test
    fun testForTransaction() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val txHash = "5ebd5c0af4385500b53dd63b0ef5f6e8feef1a7e1c2e9e2c7f5d5f5e5f5e5f5e"
        val operationsBuilder = server.operations()
            .forTransaction(txHash)

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("transactions/$txHash/operations"))

        server.close()
    }

    @Test
    fun testForLiquidityPool() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val operationsBuilder = server.operations()
            .forLiquidityPool(poolId)

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("liquidity_pools/$poolId/operations"))

        server.close()
    }

    @Test
    fun testIncludeFailed() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val operationsBuilder = server.operations()
            .includeFailed(true)

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("include_failed=true"))

        server.close()
    }

    @Test
    fun testIncludeTransactions() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val operationsBuilder = server.operations()
            .includeTransactions(true)

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("join=transactions"))

        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val operationsBuilder = server.operations()
            .cursor("13537736921088")

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("cursor=13537736921088"))

        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val operationsBuilder = server.operations()
            .limit(200)

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("limit=200"))

        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val operationsBuilder = server.operations()
            .order(RequestBuilder.Order.DESC)

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("order=desc"))

        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val operationsBuilder = server.operations()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
            .includeFailed(true)
            .includeTransactions(true)
            .cursor("13537736921088")
            .limit(10)
            .order(RequestBuilder.Order.ASC)

        val url = operationsBuilder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/operations"))
        assertTrue(url.contains("include_failed=true"))
        assertTrue(url.contains("join=transactions"))
        assertTrue(url.contains("cursor=13537736921088"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=asc"))

        server.close()
    }
}
