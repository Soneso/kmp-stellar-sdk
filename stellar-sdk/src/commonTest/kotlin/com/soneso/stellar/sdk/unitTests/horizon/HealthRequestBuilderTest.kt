package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.HealthRequestBuilder
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HealthRequestBuilderTest {

    @Test
    fun testCreation() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.health()
        assertTrue(builder is HealthRequestBuilder)
        server.close()
    }

    @Test
    fun testDefaultSegment() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val builder = server.health()
        val url = builder.buildUrl().toString()
        assertTrue(url.contains("/health"))
        server.close()
    }

    @Test
    fun testCursorNotSupported() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<UnsupportedOperationException> {
            server.health().cursor("test")
        }
        server.close()
    }

    @Test
    fun testLimitNotSupported() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<UnsupportedOperationException> {
            server.health().limit(10)
        }
        server.close()
    }

    @Test
    fun testOrderNotSupported() {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        assertFailsWith<UnsupportedOperationException> {
            server.health().order(RequestBuilder.Order.ASC)
        }
        server.close()
    }
}
