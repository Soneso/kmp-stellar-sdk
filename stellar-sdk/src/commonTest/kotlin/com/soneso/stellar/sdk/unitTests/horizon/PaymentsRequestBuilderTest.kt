package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.*
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class PaymentsRequestBuilderTest {

    @Test
    fun testPaymentsRequestBuilderCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val paymentsBuilder = server.payments()

        assertTrue(paymentsBuilder is com.soneso.stellar.sdk.horizon.requests.PaymentsRequestBuilder)

        server.close()
    }

    @Test
    fun testForAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val paymentsBuilder = server.payments()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")

        val url = paymentsBuilder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/payments"))

        server.close()
    }

    @Test
    fun testForLedger() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val paymentsBuilder = server.payments()
            .forLedger(12345)

        val url = paymentsBuilder.buildUrl().toString()
        assertTrue(url.contains("ledgers/12345/payments"))

        server.close()
    }

    @Test
    fun testForTransaction() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val txHash = "5ebd5c0af4385500b53dd63b0ef5f6e8feef1a7e1c2e9e2c7f5d5f5e5f5e5f5e"
        val paymentsBuilder = server.payments()
            .forTransaction(txHash)

        val url = paymentsBuilder.buildUrl().toString()
        assertTrue(url.contains("transactions/$txHash/payments"))

        server.close()
    }

    @Test
    fun testIncludeTransactions() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val paymentsBuilder = server.payments()
            .includeTransactions(true)

        val url = paymentsBuilder.buildUrl().toString()
        assertTrue(url.contains("join=transactions"))

        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val paymentsBuilder = server.payments()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
            .includeTransactions(true)
            .cursor("13537736921088")
            .limit(10)
            .order(RequestBuilder.Order.ASC)

        val url = paymentsBuilder.buildUrl().toString()
        assertTrue(url.contains("accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/payments"))
        assertTrue(url.contains("join=transactions"))
        assertTrue(url.contains("cursor=13537736921088"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=asc"))

        server.close()
    }
}
