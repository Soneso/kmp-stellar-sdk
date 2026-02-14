// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep10.exceptions.ChallengeRequestException
import com.soneso.stellar.sdk.sep.sep10.exceptions.NoMemoForMuxedAccountsException
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Unit tests for WebAuth.getChallenge() method.
 * Tests HTTP request handling, parameter validation, and error cases.
 */
class WebAuthChallengeTest {

    private val testServerKey = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
    private val testHomeDomain = "testanchor.stellar.org"
    private val testAuthEndpoint = "https://testanchor.stellar.org/auth"

    // Valid test account IDs (from StrKeyTest.kt)
    private val validAccountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
    private val validMuxedAccountId = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLK"

    // Sample challenge transaction XDR (base64)
    private val sampleChallengeXdr = "AAAAAgAAAADR5OSmtwp5KUzH0LjPuNiW3P5VNKRXvhT7jBMlZqOqAwAAAGQAAAAAAAAAAQAAAAEAAAAAZk0TCQAAAABWTRMMAAAAAAAAAA=="

    private fun createMockClient(responseContent: String, statusCode: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = responseContent,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
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
    fun testGetChallengeSuccess() = runTest {
        val responseJson = """
            {
                "transaction": "$sampleChallengeXdr",
                "network_passphrase": "Test SDF Network ; September 2015"
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

        val response = webAuth.getChallenge(clientAccountId = validAccountId)

        assertEquals(sampleChallengeXdr, response.transaction)
        assertEquals("Test SDF Network ; September 2015", response.networkPassphrase)
    }

    @Test
    fun testGetChallengeWithMemo() = runTest {
        val responseJson = """
            {
                "transaction": "$sampleChallengeXdr"
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

        val response = webAuth.getChallenge(
            clientAccountId = validAccountId,
            memo = 12345
        )

        assertEquals(sampleChallengeXdr, response.transaction)
        assertNull(response.networkPassphrase)
    }

    @Test
    fun testGetChallengeWithClientDomain() = runTest {
        val responseJson = """
            {
                "transaction": "$sampleChallengeXdr"
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

        val response = webAuth.getChallenge(
            clientAccountId = validAccountId,
            clientDomain = "wallet.example.com"
        )

        assertEquals(sampleChallengeXdr, response.transaction)
    }

    @Test
    fun testGetChallengeWithHomeDomain() = runTest {
        val responseJson = """
            {
                "transaction": "$sampleChallengeXdr"
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

        val response = webAuth.getChallenge(
            clientAccountId = validAccountId,
            homeDomain = "custom.stellar.org"
        )

        assertEquals(sampleChallengeXdr, response.transaction)
    }

    @Test
    fun testGetChallengeMuxedAccount() = runTest {
        val responseJson = """
            {
                "transaction": "$sampleChallengeXdr"
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

        // Muxed account without memo should work
        val response = webAuth.getChallenge(clientAccountId = validMuxedAccountId)

        assertEquals(sampleChallengeXdr, response.transaction)
    }

    @Test
    fun testGetChallengeMemoWithMuxedAccountThrows() = runTest {
        val responseJson = """
            {
                "transaction": "$sampleChallengeXdr"
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

        // Muxed account with memo should throw NoMemoForMuxedAccountsException
        assertFailsWith<NoMemoForMuxedAccountsException> {
            webAuth.getChallenge(
                clientAccountId = validMuxedAccountId,
                memo = 12345
            )
        }
    }

    @Test
    fun testGetChallengeInvalidAccountId() = runTest {
        val responseJson = """
            {
                "transaction": "$sampleChallengeXdr"
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

        // Invalid account format should throw IllegalArgumentException
        assertFailsWith<IllegalArgumentException> {
            webAuth.getChallenge(clientAccountId = "INVALID_ACCOUNT_ID")
        }
    }

    @Test
    fun testGetChallengeInvalidAccountIdNotGOrM() = runTest {
        val responseJson = """
            {
                "transaction": "$sampleChallengeXdr"
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

        // Account starting with S (secret seed) should fail
        assertFailsWith<IllegalArgumentException> {
            webAuth.getChallenge(clientAccountId = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        }
    }

    @Test
    fun testGetChallenge401Unauthorized() = runTest {
        val responseJson = """{"error": "Unauthorized"}"""

        val mockClient = createMockClient(responseJson, HttpStatusCode.Unauthorized)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.getChallenge(clientAccountId = validAccountId)
        }

        assertEquals(401, exception.statusCode)
        assertTrue(exception.errorMessage?.contains("Unauthorized") == true || exception.errorMessage == """{"error": "Unauthorized"}""")
    }

    @Test
    fun testGetChallenge403Forbidden() = runTest {
        val responseJson = """{"error": "Account not allowed"}"""

        val mockClient = createMockClient(responseJson, HttpStatusCode.Forbidden)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.getChallenge(clientAccountId = validAccountId)
        }

        assertEquals(403, exception.statusCode)
        assertTrue(exception.errorMessage?.contains("Account not allowed") == true || exception.errorMessage == """{"error": "Account not allowed"}""")
    }

    @Test
    fun testGetChallenge400BadRequest() = runTest {
        val responseJson = """{"error": "Invalid account parameter"}"""

        val mockClient = createMockClient(responseJson, HttpStatusCode.BadRequest)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.getChallenge(clientAccountId = validAccountId)
        }

        assertEquals(400, exception.statusCode)
        assertNotNull(exception.errorMessage)
    }

    @Test
    fun testGetChallenge404NotFound() = runTest {
        val responseJson = ""

        val mockClient = createMockClient(responseJson, HttpStatusCode.NotFound)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.getChallenge(clientAccountId = validAccountId)
        }

        assertEquals(404, exception.statusCode)
        assertTrue(exception.errorMessage?.contains("not found") == true)
    }

    @Test
    fun testGetChallenge500ServerError() = runTest {
        val responseJson = """{"error": "Internal server error"}"""

        val mockClient = createMockClient(responseJson, HttpStatusCode.InternalServerError)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.getChallenge(clientAccountId = validAccountId)
        }

        assertEquals(500, exception.statusCode)
        assertNotNull(exception.errorMessage)
    }

    @Test
    fun testGetChallengeMissingTransactionField() = runTest {
        // Response missing transaction field
        val responseJson = """
            {
                "network_passphrase": "Test SDF Network ; September 2015"
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

        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.getChallenge(clientAccountId = validAccountId)
        }

        assertEquals(200, exception.statusCode)
        assertTrue(exception.errorMessage?.contains("transaction") == true)
    }

    @Test
    fun testGetChallengeEmptyTransactionField() = runTest {
        // Response with empty transaction field
        val responseJson = """
            {
                "transaction": "",
                "network_passphrase": "Test SDF Network ; September 2015"
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

        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.getChallenge(clientAccountId = validAccountId)
        }

        assertEquals(200, exception.statusCode)
        assertTrue(exception.errorMessage?.contains("transaction") == true)
    }

    @Test
    fun testGetChallengeInvalidJsonResponse() = runTest {
        // Invalid JSON
        val responseJson = """{"transaction": "incomplete"""

        val mockClient = createMockClient(responseJson)
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.getChallenge(clientAccountId = validAccountId)
        }

        assertEquals(200, exception.statusCode)
        assertTrue(exception.errorMessage?.contains("parse") == true || exception.errorMessage?.contains("JSON") == true)
    }

    @Test
    fun testGetChallengeNetworkError() = runTest {
        // Create a mock client that throws a network exception
        val mockEngine = MockEngine { request ->
            throw Exception("Network connection failed")
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

        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.getChallenge(clientAccountId = validAccountId)
        }

        assertEquals(0, exception.statusCode)
        assertTrue(exception.errorMessage?.contains("Network error") == true)
    }

    @Test
    fun testGetChallengeWithAllParameters() = runTest {
        val responseJson = """
            {
                "transaction": "$sampleChallengeXdr",
                "network_passphrase": "Test SDF Network ; September 2015"
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

        val response = webAuth.getChallenge(
            clientAccountId = validAccountId,
            memo = 99999,
            homeDomain = "custom.stellar.org",
            clientDomain = "wallet.example.com"
        )

        assertEquals(sampleChallengeXdr, response.transaction)
        assertEquals("Test SDF Network ; September 2015", response.networkPassphrase)
    }

    @Test
    fun testGetChallengeUrlBuilding() = runTest {
        // Test that URL parameters are properly encoded
        var capturedUrl = ""

        val mockEngine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """{"transaction": "$sampleChallengeXdr"}""",
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

        webAuth.getChallenge(
            clientAccountId = validAccountId,
            memo = 12345
        )

        assertTrue(capturedUrl.contains("account=$validAccountId"))
        assertTrue(capturedUrl.contains("memo=12345"))
    }

    @Test
    fun testGetChallengeCustomHeaders() = runTest {
        // Test that custom headers are included in request
        var capturedCustomHeader: String? = null

        val mockEngine = MockEngine { request ->
            capturedCustomHeader = request.headers["X-Custom-Header"]
            respond(
                content = """{"transaction": "$sampleChallengeXdr"}""",
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

        val customHeaders = mapOf("X-Custom-Header" to "CustomValue")

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = Network.TESTNET,
            serverSigningKey = testServerKey,
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient,
            httpRequestHeaders = customHeaders
        )

        webAuth.getChallenge(clientAccountId = validAccountId)

        assertEquals("CustomValue", capturedCustomHeader)
    }
}
