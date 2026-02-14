package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.OffersRequestBuilder
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class OffersRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers()
        assertTrue(builder is OffersRequestBuilder)
        server.close()
    }

    @Test
    fun testForSponsor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers()
            .forSponsor("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("sponsor=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testForSeller() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers()
            .forSeller("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("seller=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testForAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers()
            .forAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        // forAccount delegates to forSeller
        assertTrue(url.contains("seller=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testForBuyingAssetNative() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers()
            .forBuyingAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("buying=native"))
        server.close()
    }

    @Test
    fun testForBuyingAssetCredit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers()
            .forBuyingAsset("credit_alphanum4", "USD", "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("buying=USD"))
        server.close()
    }

    @Test
    fun testForSellingAssetNative() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers()
            .forSellingAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("selling=native"))
        server.close()
    }

    @Test
    fun testForSellingAssetCredit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers()
            .forSellingAsset("credit_alphanum4", "EUR", "GTEST")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("selling=EUR"))
        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers().cursor("offer_cursor")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=offer_cursor"))
        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers().limit(100)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("limit=100"))
        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers().order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers()
            .forSeller("GTEST")
            .forBuyingAsset("native")
            .forSellingAsset("credit_alphanum4", "USD", "GISSUER")
            .cursor("c1")
            .limit(20)
            .order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("seller=GTEST"))
        assertTrue(url.contains("buying=native"))
        assertTrue(url.contains("selling=USD"))
        assertTrue(url.contains("cursor=c1"))
        assertTrue(url.contains("limit=20"))
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.offers()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/offers"))
        server.close()
    }
}
