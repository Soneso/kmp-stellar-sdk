package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.OrderBookRequestBuilder
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderBookRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.orderBook()
        assertTrue(builder is OrderBookRequestBuilder)
        server.close()
    }

    @Test
    fun testBuyingAssetNative() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.orderBook()
            .buyingAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("buying_asset_type=native"))
        server.close()
    }

    @Test
    fun testBuyingAssetCredit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.orderBook()
            .buyingAsset("credit_alphanum4", "USD", "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("buying_asset_type=credit_alphanum4"))
        assertTrue(url.contains("buying_asset_code=USD"))
        assertTrue(url.contains("buying_asset_issuer=GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"))
        server.close()
    }

    @Test
    fun testSellingAssetNative() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.orderBook()
            .sellingAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("selling_asset_type=native"))
        server.close()
    }

    @Test
    fun testSellingAssetCredit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.orderBook()
            .sellingAsset("credit_alphanum12", "LONGASSET", "GTEST")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("selling_asset_type=credit_alphanum12"))
        assertTrue(url.contains("selling_asset_code=LONGASSET"))
        assertTrue(url.contains("selling_asset_issuer=GTEST"))
        server.close()
    }

    @Test
    fun testBuyingAndSellingAssets() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.orderBook()
            .buyingAsset("native")
            .sellingAsset("credit_alphanum4", "USD", "GISSUER")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("buying_asset_type=native"))
        assertTrue(url.contains("selling_asset_type=credit_alphanum4"))
        assertTrue(url.contains("selling_asset_code=USD"))
        assertTrue(url.contains("selling_asset_issuer=GISSUER"))
        server.close()
    }

    @Test
    fun testCursorNotSupported() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<UnsupportedOperationException> {
            server.orderBook().cursor("test")
        }
        server.close()
    }

    @Test
    fun testLimitNotSupported() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<UnsupportedOperationException> {
            server.orderBook().limit(10)
        }
        server.close()
    }

    @Test
    fun testOrderNotSupported() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<UnsupportedOperationException> {
            server.orderBook().order(RequestBuilder.Order.ASC)
        }
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.orderBook()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/order_book"))
        server.close()
    }
}
