package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.AssetsRequestBuilder
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class AssetsRequestBuilderTest {

    @Test
    fun testAssetsRequestBuilderCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.assets()
        assertTrue(builder is AssetsRequestBuilder)
        server.close()
    }

    @Test
    fun testForAssetCode() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.assets().forAssetCode("USD")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset_code=USD"))
        server.close()
    }

    @Test
    fun testForAssetIssuer() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.assets()
            .forAssetIssuer("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset_issuer=GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"))
        server.close()
    }

    @Test
    fun testForAssetCodeAndIssuer() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.assets()
            .forAssetCode("USD")
            .forAssetIssuer("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset_code=USD"))
        assertTrue(url.contains("asset_issuer=GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B"))
        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.assets().cursor("token123")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=token123"))
        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.assets().limit(50)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("limit=50"))
        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.assets().order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.assets()
            .forAssetCode("USD")
            .forAssetIssuer("GTEST")
            .cursor("abc")
            .limit(20)
            .order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset_code=USD"))
        assertTrue(url.contains("asset_issuer=GTEST"))
        assertTrue(url.contains("cursor=abc"))
        assertTrue(url.contains("limit=20"))
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.assets()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/assets"))
        server.close()
    }
}
