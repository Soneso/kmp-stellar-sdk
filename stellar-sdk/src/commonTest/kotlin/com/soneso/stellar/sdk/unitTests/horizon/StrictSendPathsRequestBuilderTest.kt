package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import com.soneso.stellar.sdk.horizon.requests.StrictSendPathsRequestBuilder
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StrictSendPathsRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths()
        assertTrue(builder is StrictSendPathsRequestBuilder)
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/paths/strict-send"))
        server.close()
    }

    @Test
    fun testDestinationAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths()
            .destinationAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_account=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testDestinationAssets() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths()
            .destinationAssets(listOf(
                Triple("native", null, null),
                Triple("credit_alphanum4", "EUR", "GISSUER")
            ))
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_assets="))
        assertTrue(url.contains("native"))
        server.close()
    }

    @Test
    fun testCannotSetDestinationAccountAndDestinationAssets() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<IllegalArgumentException> {
            server.strictSendPaths()
                .destinationAccount("GTEST")
                .destinationAssets(listOf(Triple("native", null, null)))
        }
        server.close()
    }

    @Test
    fun testCannotSetDestinationAssetsAndDestinationAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<IllegalArgumentException> {
            server.strictSendPaths()
                .destinationAssets(listOf(Triple("native", null, null)))
                .destinationAccount("GTEST")
        }
        server.close()
    }

    @Test
    fun testSourceAssetNative() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths()
            .sourceAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("source_asset_type=native"))
        server.close()
    }

    @Test
    fun testSourceAssetCredit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths()
            .sourceAsset("credit_alphanum4", "USD", "GISSUER")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("source_asset_type=credit_alphanum4"))
        assertTrue(url.contains("source_asset_code=USD"))
        assertTrue(url.contains("source_asset_issuer=GISSUER"))
        server.close()
    }

    @Test
    fun testSourceAmount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths()
            .sourceAmount("10.0")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("source_amount=10.0"))
        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths().cursor("c1")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=c1"))
        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths().limit(5)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("limit=5"))
        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths().order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictSendPaths()
            .sourceAsset("native")
            .sourceAmount("100.0")
            .destinationAccount("GDEST")
            .cursor("c1")
            .limit(10)
            .order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("source_asset_type=native"))
        assertTrue(url.contains("source_amount=100.0"))
        assertTrue(url.contains("destination_account=GDEST"))
        assertTrue(url.contains("cursor=c1"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=desc"))
        server.close()
    }
}
