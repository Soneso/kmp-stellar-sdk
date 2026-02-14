// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.exceptions.TokenSubmissionException
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Unit tests for WebAuth.sendSignedChallenge() method.
 * Tests HTTP request handling, JSON parsing, and error cases for token submission.
 */
class WebAuthTokenSubmissionTest {

    private val testServerKey = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
    private val testHomeDomain = "testanchor.stellar.org"
    private val testAuthEndpoint = "https://testanchor.stellar.org/auth"

    // Sample signed challenge XDR (base64)
    private val signedChallengeXdr = "AAAAAgAAAADR5OSmtwp5KUzH0LjPuNiW3P5VNKRXvhT7jBMlZqOqAwAAAGQAAAAAAAAAAQAAAAEAAAAAZk0TCQAAAABWTRMMAAAAAAAAAA=="

    // Sample JWT token (properly formatted with payload containing sub, iss, iat, exp)
    // Payload: {"sub": "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D", "iss": "testanchor.stellar.org", "iat": 1709598600, "exp": 1709602200}
    private val sampleJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiAiR0NaSFhMNUhYUVg1QUJETTI2TEhZUkNRWjVPSkZITE9QTFpYNDdXRUJQM1YyUEY1QVZGSzJBNUQiLCAiaXNzIjogInRlc3RhbmNob3Iuc3RlbGxhci5vcmciLCAiaWF0IjogMTcwOTU5ODYwMCwgImV4cCI6IDE3MDk2MDIyMDB9.test_signature"

    private fun createMockClient(
        responseContent: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json"
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = responseContent,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, contentType)
            )
        }

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    @Test
    fun testSendSignedChallengeSuccess() = runTest {
        val responseJson = """
            {
                "token": "$sampleJwt"
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val authToken = webAuth.sendSignedChallenge(signedChallengeXdr)

        assertNotNull(authToken)
        assertEquals(sampleJwt, authToken.token)
        assertEquals("testanchor.stellar.org", authToken.iss)
        assertEquals(testServerKey, authToken.account)
        assertNotNull(authToken.exp)
        assertEquals(1709602200L, authToken.exp)
        assertNotNull(authToken.iat)
        assertEquals(1709598600L, authToken.iat)
    }

    @Test
    fun testSendSignedChallenge400BadRequest() = runTest {
        val errorResponse = """
            {
                "error": "Transaction validation failed: invalid sequence number"
            }
        """.trimIndent()

        val mockClient = createMockClient(errorResponse, HttpStatusCode.BadRequest)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("400") == true)
        assertTrue(exception.message?.contains("Bad request") == true)
    }

    @Test
    fun testSendSignedChallenge401Unauthorized() = runTest {
        val errorResponse = """
            {
                "error": "Signature verification failed"
            }
        """.trimIndent()

        val mockClient = createMockClient(errorResponse, HttpStatusCode.Unauthorized)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("401") == true)
        assertTrue(exception.message?.contains("Unauthorized") == true)
        assertTrue(exception.message?.contains("Signature verification failed") == true)
    }

    @Test
    fun testSendSignedChallenge403Forbidden() = runTest {
        val errorResponse = """
            {
                "error": "Account not authorized for this service"
            }
        """.trimIndent()

        val mockClient = createMockClient(errorResponse, HttpStatusCode.Forbidden)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("403") == true)
        assertTrue(exception.message?.contains("Forbidden") == true)
    }

    @Test
    fun testSendSignedChallenge404NotFound() = runTest {
        val mockClient = createMockClient("Not Found", HttpStatusCode.NotFound)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("404") == true)
        assertTrue(exception.message?.contains("Not Found") == true)
        assertTrue(exception.message?.contains(testAuthEndpoint) == true)
    }

    @Test
    fun testSendSignedChallenge500ServerError() = runTest {
        val errorResponse = """
            {
                "error": "Internal server error"
            }
        """.trimIndent()

        val mockClient = createMockClient(errorResponse, HttpStatusCode.InternalServerError)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("500") == true)
        assertTrue(exception.message?.contains("Server error") == true)
    }

    @Test
    fun testSendSignedChallenge503ServiceUnavailable() = runTest {
        val mockClient = createMockClient("Service Unavailable", HttpStatusCode.ServiceUnavailable)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("503") == true)
        assertTrue(exception.message?.contains("Server error") == true)
    }

    @Test
    fun testSendSignedChallengeEmptyXdr() = runTest {
        val mockClient = createMockClient("{}")
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            webAuth.sendSignedChallenge("")
        }

        assertTrue(exception.message?.contains("empty") == true)
    }

    @Test
    fun testSendSignedChallengeBlankXdr() = runTest {
        val mockClient = createMockClient("{}")
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            webAuth.sendSignedChallenge("   ")
        }

        assertTrue(exception.message?.contains("empty") == true)
    }

    @Test
    fun testSendSignedChallengeNetworkError() = runTest {
        val mockEngine = MockEngine { request ->
            throw Exception("Connection timeout")
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("Network error") == true)
        assertTrue(exception.message?.contains("Connection timeout") == true)
        assertNotNull(exception.cause)
    }

    @Test
    fun testSendSignedChallengeInvalidJsonResponse() = runTest {
        val invalidJson = "{ this is not valid json"

        val mockClient = createMockClient(invalidJson)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("Failed to parse JSON response") == true)
    }

    @Test
    fun testSendSignedChallengeMissingTokenField() = runTest {
        val responseWithoutToken = """
            {
                "status": "success"
            }
        """.trimIndent()

        val mockClient = createMockClient(responseWithoutToken)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("Failed to parse JSON response") == true)
    }

    @Test
    fun testSendSignedChallengeEmptyTokenField() = runTest {
        val responseWithEmptyToken = """
            {
                "token": ""
            }
        """.trimIndent()

        val mockClient = createMockClient(responseWithEmptyToken)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("missing required 'token' field") == true)
    }

    @Test
    fun testSendSignedChallengeUnexpectedStatusCode() = runTest {
        val mockClient = createMockClient("Teapot", HttpStatusCode(418, "I'm a teapot"))
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.sendSignedChallenge(signedChallengeXdr)
        }

        assertTrue(exception.message?.contains("418") == true)
        assertTrue(exception.message?.contains("Unexpected response") == true)
    }

    @Test
    fun testSendSignedChallengeWithCustomHeaders() = runTest {
        var receivedHeaders: Headers? = null

        val mockEngine = MockEngine { request ->
            receivedHeaders = request.headers
            respond(
                content = """{"token": "$sampleJwt"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val customHeaders = mapOf(
            "X-Custom-Header" to "test-value",
            "Authorization" to "Bearer custom-token"
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient,
            httpRequestHeaders = customHeaders
        )

        webAuth.sendSignedChallenge(signedChallengeXdr)

        assertNotNull(receivedHeaders)
        assertEquals("test-value", receivedHeaders?.get("X-Custom-Header"))
        assertEquals("Bearer custom-token", receivedHeaders?.get("Authorization"))
    }

    @Test
    fun testSendSignedChallengeContentTypeIsJson() = runTest {
        var receivedContentType: ContentType? = null

        val mockEngine = MockEngine { request ->
            receivedContentType = request.body.contentType
            respond(
                content = """{"token": "$sampleJwt"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        webAuth.sendSignedChallenge(signedChallengeXdr)

        assertNotNull(receivedContentType)
        assertEquals(ContentType.Application.Json, receivedContentType)
    }

    @Test
    fun testSendSignedChallengeRequestMethod() = runTest {
        var receivedMethod: HttpMethod? = null

        val mockEngine = MockEngine { request ->
            receivedMethod = request.method
            respond(
                content = """{"token": "$sampleJwt"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        webAuth.sendSignedChallenge(signedChallengeXdr)

        assertEquals(HttpMethod.Post, receivedMethod)
    }

    @Test
    fun testSendSignedChallengeMinimalJwt() = runTest {
        // Test with a minimal JWT (just token string, no parsed claims)
        val minimalJwt = "simple.token.here"
        val responseJson = """
            {
                "token": "$minimalJwt"
            }
        """.trimIndent()

        val mockClient = createMockClient(responseJson)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val authToken = webAuth.sendSignedChallenge(signedChallengeXdr)

        assertNotNull(authToken)
        assertEquals(minimalJwt, authToken.token)
        // Claims should be null for invalid JWT format
        assertNull(authToken.iss)
        assertNull(authToken.sub)
    }
}
