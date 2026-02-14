package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.*
import kotlinx.coroutines.test.runTest
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AccountsRequestBuilderTest {

    @Test
    fun testAccountsRequestBuilderCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val accountsBuilder = server.accounts()

        // Verify builder is created successfully
        assertTrue(accountsBuilder is com.soneso.stellar.sdk.horizon.requests.AccountsRequestBuilder)

        server.close()
    }

    @Test
    fun testForSigner() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val accountsBuilder = server.accounts()
            .forSigner("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")

        val url = accountsBuilder.buildUrl().toString()
        assertTrue(url.contains("signer=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))

        server.close()
    }

    @Test
    fun testForAsset() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val accountsBuilder = server.accounts()
            .forAsset("USD", "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")

        val url = accountsBuilder.buildUrl().toString()
        assertTrue(url.contains("asset=USD%3AGAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7") ||
                   url.contains("asset=USD:GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))

        server.close()
    }

    @Test
    fun testForLiquidityPool() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val accountsBuilder = server.accounts()
            .forLiquidityPool(poolId)

        val url = accountsBuilder.buildUrl().toString()
        assertTrue(url.contains("liquidity_pool=$poolId"))

        server.close()
    }

    @Test
    fun testForSponsor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val accountsBuilder = server.accounts()
            .forSponsor("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")

        val url = accountsBuilder.buildUrl().toString()
        assertTrue(url.contains("sponsor=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))

        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val accountsBuilder = server.accounts()
            .cursor("13537736921088")

        val url = accountsBuilder.buildUrl().toString()
        assertTrue(url.contains("cursor=13537736921088"))

        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val accountsBuilder = server.accounts()
            .limit(200)

        val url = accountsBuilder.buildUrl().toString()
        assertTrue(url.contains("limit=200"))

        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val accountsBuilder = server.accounts()
            .order(RequestBuilder.Order.DESC)

        val url = accountsBuilder.buildUrl().toString()
        assertTrue(url.contains("order=desc"))

        server.close()
    }

    @Test
    fun testCannotSetAssetAndSigner() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")

        assertFailsWith<IllegalArgumentException> {
            server.accounts()
                .forAsset("USD", "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
                .forSigner("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        }

        server.close()
    }

    @Test
    fun testCannotSetAssetAndLiquidityPool() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"

        assertFailsWith<IllegalArgumentException> {
            server.accounts()
                .forAsset("USD", "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
                .forLiquidityPool(poolId)
        }

        server.close()
    }

    @Test
    fun testCannotSetAssetAndSponsor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")

        assertFailsWith<IllegalArgumentException> {
            server.accounts()
                .forAsset("USD", "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
                .forSponsor("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        }

        server.close()
    }

    @Test
    fun testCannotSetSignerAndLiquidityPool() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"

        assertFailsWith<IllegalArgumentException> {
            server.accounts()
                .forSigner("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
                .forLiquidityPool(poolId)
        }

        server.close()
    }

    @Test
    fun testCannotSetSignerAndSponsor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")

        assertFailsWith<IllegalArgumentException> {
            server.accounts()
                .forSigner("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
                .forSponsor("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        }

        server.close()
    }

    @Test
    fun testCannotSetLiquidityPoolAndSponsor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"

        assertFailsWith<IllegalArgumentException> {
            server.accounts()
                .forLiquidityPool(poolId)
                .forSponsor("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        }

        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val accountsBuilder = server.accounts()
            .forSigner("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
            .cursor("13537736921088")
            .limit(10)
            .order(RequestBuilder.Order.ASC)

        val url = accountsBuilder.buildUrl().toString()
        assertTrue(url.contains("signer=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        assertTrue(url.contains("cursor=13537736921088"))
        assertTrue(url.contains("limit=10"))
        assertTrue(url.contains("order=asc"))

        server.close()
    }
}
