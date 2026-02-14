package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.ClaimableBalancesRequestBuilder
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class ClaimableBalancesRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.claimableBalances()
        assertTrue(builder is ClaimableBalancesRequestBuilder)
        server.close()
    }

    @Test
    fun testForSponsor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.claimableBalances()
            .forSponsor("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("sponsor=GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"))
        server.close()
    }

    @Test
    fun testForAssetNative() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.claimableBalances()
            .forAsset("native")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset=native"))
        server.close()
    }

    @Test
    fun testForAssetCredit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.claimableBalances()
            .forAsset("credit_alphanum4", "USD", "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("asset=USD") || url.contains("asset=USD%3A"))
        server.close()
    }

    @Test
    fun testForClaimant() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.claimableBalances()
            .forClaimant("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("claimant=GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU"))
        server.close()
    }

    @Test
    fun testCursor() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.claimableBalances().cursor("abc123")
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("cursor=abc123"))
        server.close()
    }

    @Test
    fun testLimit() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.claimableBalances().limit(100)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("limit=100"))
        server.close()
    }

    @Test
    fun testOrder() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.claimableBalances().order(RequestBuilder.Order.ASC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("order=asc"))
        server.close()
    }

    @Test
    fun testChainedMethodCalls() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.claimableBalances()
            .forSponsor("GTEST")
            .cursor("cursor1")
            .limit(25)
            .order(RequestBuilder.Order.DESC)
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("sponsor=GTEST"))
        assertTrue(url.contains("cursor=cursor1"))
        assertTrue(url.contains("limit=25"))
        assertTrue(url.contains("order=desc"))
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.claimableBalances()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/claimable_balances"))
        server.close()
    }
}
