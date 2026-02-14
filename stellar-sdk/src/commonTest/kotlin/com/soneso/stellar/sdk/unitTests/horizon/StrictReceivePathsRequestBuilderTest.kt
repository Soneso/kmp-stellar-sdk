package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import com.soneso.stellar.sdk.horizon.requests.StrictReceivePathsRequestBuilder
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StrictReceivePathsRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths()
        assertTrue(builder is StrictReceivePathsRequestBuilder)
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/paths/strict-receive"))
        server.close()
    }

    @Test
    fun testSourceAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths()
            .sourceAccount("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("source_account=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testSourceAssets() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths()
            .sourceAssets(listOf(
                Triple("native", null, null),
                Triple("credit_alphanum4", "USD", "GISSUER")
            ))
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("source_assets="))
        assertTrue(url.contains("native"))
        server.close()
    }

    @Test
    fun testCannotSetSourceAccountAndSourceAssets() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<IllegalArgumentException> {
            server.strictReceivePaths()
                .sourceAccount("GTEST")
                .sourceAssets(listOf(Triple("native", null, null)))
        }
        server.close()
    }

    @Test
    fun testCannotSetSourceAssetsAndSourceAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<IllegalArgumentException> {
            server.strictReceivePaths()
                .sourceAssets(listOf(Triple("native", null, null)))
                .sourceAccount("GTEST")
        }
        server.close()
    }

    @Test
    fun testDestinationAccount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths()
            .destinationAccount("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_account=GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU"))
        server.close()
    }

    @Test
    fun testDestinationAmount() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths()
            .destinationAmount("50.0")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_amount=50.0"))
        server.close()
    }

    @Test
    fun testDestinationAssetNative() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths()
            .destinationAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_asset_type=native"))
        server.close()
    }

    @Test
    fun testDestinationAssetCredit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths()
            .destinationAsset("credit_alphanum4", "USD", "GISSUER")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("destination_asset_type=credit_alphanum4"))
        assertTrue(url.contains("destination_asset_code=USD"))
        assertTrue(url.contains("destination_asset_issuer=GISSUER"))
        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths().cursor("c1")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=c1"))
        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths().limit(5)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("limit=5"))
        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths().order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.strictReceivePaths()
            .sourceAccount("GTEST")
            .destinationAsset("native")
            .destinationAmount("100.0")
            .cursor("c1")
            .limit(10)
            .order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("source_account=GTEST"))
        assertTrue(url.contains("destination_asset_type=native"))
        assertTrue(url.contains("destination_amount=100.0"))
        assertTrue(url.contains("cursor=c1"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=asc"))
        server.close()
    }
}
