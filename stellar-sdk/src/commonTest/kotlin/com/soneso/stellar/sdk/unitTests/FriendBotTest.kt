//
//  FriendBotTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.FriendBot
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for FriendBot covering the fund and close paths against a mocked HTTP client.
 *
 * FriendBot is a singleton object shared with the integration test suite, so every test here
 * installs [FriendBot.httpClientOverride] before exercising the client and clears it again in
 * [tearDown], regardless of whether the test body succeeded or threw.
 */
class FriendBotTest {

    private val testAccountId = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"

    @AfterTest
    fun tearDown() {
        FriendBot.httpClientOverride = null
    }

    @Test
    fun testFundTestnetAccountSuccessReturnsTrue() = runTest {
        var capturedHost: String? = null
        var capturedAddr: String? = null

        FriendBot.httpClientOverride = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedHost = request.url.host
                    capturedAddr = request.url.parameters["addr"]
                    respond(content = """{"successful": true}""", status = HttpStatusCode.OK)
                }
            }
        }

        val result = FriendBot.fundTestnetAccount(testAccountId)

        assertTrue(result)
        assertEquals("friendbot.stellar.org", capturedHost)
        assertEquals(testAccountId, capturedAddr)
    }

    @Test
    fun testFundFuturenetAccountSuccessReturnsTrue() = runTest {
        var capturedHost: String? = null
        var capturedAddr: String? = null

        FriendBot.httpClientOverride = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    capturedHost = request.url.host
                    capturedAddr = request.url.parameters["addr"]
                    respond(content = """{"successful": true}""", status = HttpStatusCode.OK)
                }
            }
        }

        val result = FriendBot.fundFuturenetAccount(testAccountId)

        assertTrue(result)
        assertEquals("friendbot-futurenet.stellar.org", capturedHost)
        assertEquals(testAccountId, capturedAddr)
    }

    @Test
    fun testFundTestnetAccountBadRequestReturnsFalse() = runTest {
        FriendBot.httpClientOverride = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = """{"detail": "createAccountAlreadyExist"}""",
                        status = HttpStatusCode.BadRequest
                    )
                }
            }
        }

        val result = FriendBot.fundTestnetAccount(testAccountId)

        assertFalse(result)
    }

    @Test
    fun testFundTestnetAccountNotFoundReturnsFalse() = runTest {
        FriendBot.httpClientOverride = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(content = "not found", status = HttpStatusCode.NotFound)
                }
            }
        }

        val result = FriendBot.fundTestnetAccount(testAccountId)

        assertFalse(result)
    }

    @Test
    fun testFundTestnetAccountServerErrorRetriesAndReturnsFalse() = runTest {
        var requestCount = 0

        // Reproduce the production client's retry configuration so this test exercises the
        // same retry-on-5xx behavior FriendBot's real client is configured with.
        FriendBot.httpClientOverride = HttpClient(MockEngine) {
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()
            }
            engine {
                addHandler {
                    requestCount += 1
                    respond(content = "internal error", status = HttpStatusCode.InternalServerError)
                }
            }
        }

        val result = FriendBot.fundTestnetAccount(testAccountId)

        assertFalse(result)
        // One initial attempt plus three retries.
        assertEquals(4, requestCount)
    }

    @Test
    fun testFundTestnetAccountHandlerThrowsWrapsException() = runTest {
        val cause = RuntimeException("connection refused")
        FriendBot.httpClientOverride = HttpClient(MockEngine) {
            engine {
                addHandler { throw cause }
            }
        }

        val exception = assertFailsWith<Exception> {
            FriendBot.fundTestnetAccount(testAccountId)
        }

        assertTrue(exception.message!!.contains("Failed to fund account"))
        assertTrue(exception.message!!.contains(testAccountId))
        // kotlinx.coroutines may recreate the cause instance (with an identical message and type)
        // while recovering the stack trace across the suspend boundary, so the cause is compared
        // by type and message rather than by identity.
        assertEquals(cause.message, exception.cause?.message)
        assertEquals(cause::class, exception.cause?.let { it::class })
    }

    @Test
    fun testFundFuturenetAccountHandlerThrowsWrapsException() = runTest {
        val cause = IllegalStateException("network unreachable")
        FriendBot.httpClientOverride = HttpClient(MockEngine) {
            engine {
                addHandler { throw cause }
            }
        }

        val exception = assertFailsWith<Exception> {
            FriendBot.fundFuturenetAccount(testAccountId)
        }

        assertTrue(exception.message!!.contains("Failed to fund account"))
        assertTrue(exception.message!!.contains(testAccountId))
        assertEquals(cause.message, exception.cause?.message)
        assertEquals(cause::class, exception.cause?.let { it::class })
    }

    @Test
    fun testCloseClosesOverrideClient() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(content = """{"successful": true}""", status = HttpStatusCode.OK)
                }
            }
        }
        FriendBot.httpClientOverride = client

        // Confirm the override is usable before close().
        assertTrue(FriendBot.fundTestnetAccount(testAccountId))

        FriendBot.close()

        // The client's coroutine scope is cancelled once closed.
        assertFalse(client.coroutineContext[kotlinx.coroutines.Job]!!.isActive)
    }
}
