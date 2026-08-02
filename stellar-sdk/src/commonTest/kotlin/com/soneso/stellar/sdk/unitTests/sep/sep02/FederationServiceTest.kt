// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep02

import com.soneso.stellar.sdk.sep.sep02.FederationResponse
import com.soneso.stellar.sdk.sep.sep02.FederationService
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02Exception
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02FederationNotFoundException
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02InvalidAddressException
import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02InvalidResponseException
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class FederationServiceTest {

    // ========== Mock TOML Responses ==========

    private val validToml = """
        FEDERATION_SERVER="https://federation.example.com/federation"
    """.trimIndent()

    private val noFederationServerToml = """
        HORIZON_URL="https://horizon-testnet.stellar.org"
    """.trimIndent()

    // ========== Mock Federation JSON Responses ==========

    private val basicResponse = """{"stellar_address":"bob*stellar.org","account_id":"GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"}"""

    private val memoTextResponse = """{"stellar_address":"bob*stellar.org","account_id":"GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ","memo_type":"text","memo":"hello"}"""

    private val memoIdResponse = """{"stellar_address":"bob*stellar.org","account_id":"GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ","memo_type":"id","memo":"123"}"""

    private val memoHashResponse = """{"stellar_address":"bob*stellar.org","account_id":"GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ","memo_type":"hash","memo":"dGVzdGhhc2g="}"""

    private val noMemoResponse = """{"account_id":"GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"}"""

    private val accountIdResponse = """{"stellar_address":"bob*stellar.org","account_id":"GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"}"""

    private val forwardResponse = """{"account_id":"GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ","memo_type":"hash","memo":"dGVzdGhhc2g="}"""

    // ========== Helper Methods ==========

    private fun createFederationMockClient(
        responseContent: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            respond(
                content = responseContent,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(mockEngine)
    }

    private fun createTomlAndFederationMockClient(
        tomlContent: String,
        federationResponse: String,
        federationStatusCode: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/.well-known/stellar.toml")) {
                respond(
                    content = tomlContent,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            } else {
                respond(
                    content = federationResponse,
                    status = federationStatusCode,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        return HttpClient(mockEngine)
    }

    // ========== A. Address resolution (name) — 8 tests ==========

    @Test
    fun testResolveStellarAddressBasic() = runTest {
        val mockClient = createFederationMockClient(basicResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveStellarAddress("bob*stellar.org")

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("bob*stellar.org", response.stellarAddress)
        assertNull(response.memoType)
        assertNull(response.memo)
    }

    @Test
    fun testResolveStellarAddressMemoText() = runTest {
        val mockClient = createFederationMockClient(memoTextResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveStellarAddress("bob*stellar.org")

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("text", response.memoType)
        assertEquals("hello", response.memo)
    }

    @Test
    fun testResolveStellarAddressMemoId() = runTest {
        val mockClient = createFederationMockClient(memoIdResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveStellarAddress("bob*stellar.org")

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("id", response.memoType)
        assertEquals("123", response.memo)
    }

    @Test
    fun testResolveStellarAddressMemoHash() = runTest {
        val mockClient = createFederationMockClient(memoHashResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveStellarAddress("bob*stellar.org")

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("hash", response.memoType)
        assertEquals("dGVzdGhhc2g=", response.memo)
    }

    @Test
    fun testResolveStellarAddressNoMemo() = runTest {
        val mockClient = createFederationMockClient(noMemoResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveStellarAddress("bob*stellar.org")

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertNull(response.memoType)
        assertNull(response.memo)
    }

    @Test
    fun testResolveStellarAddressInvalidFormat() = runTest {
        val mockClient = createFederationMockClient(basicResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        assertFailsWith<Sep02InvalidAddressException> {
            service.resolveStellarAddress("nope")
        }
    }

    @Test
    fun testResolveStellarAddressEmailStyle() = runTest {
        var requestUrlVerified = false
        val mockEngine = MockEngine { request ->
            requestUrlVerified = request.url.parameters["q"] == "maria@gmail.com*stellar.org"
            respond(
                content = basicResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = HttpClient(mockEngine)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveStellarAddress("maria@gmail.com*stellar.org")

        assertNotNull(response)
        assertTrue(requestUrlVerified, "Request URL should contain email-style address")
    }

    @Test
    fun testResolveStellarAddressSubdomain() = runTest {
        val mockClient = createFederationMockClient(basicResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveStellarAddress("user*subdomain.example.com")

        assertNotNull(response)
        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
    }

    // ========== B. Static resolveStellarAddress — 3 tests ==========

    @Test
    fun testStaticResolveStellarAddress() = runTest {
        val mockClient = createTomlAndFederationMockClient(validToml, memoTextResponse)

        val response = FederationService.resolveStellarAddress(
            address = "bob*stellar.org",
            httpClient = mockClient
        )

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("text", response.memoType)
        assertEquals("hello", response.memo)
    }

    @Test
    fun testStaticResolveMissingFederationServer() = runTest {
        val mockClient = createTomlAndFederationMockClient(noFederationServerToml, "{}")

        assertFailsWith<Sep02FederationNotFoundException> {
            FederationService.resolveStellarAddress(
                address = "bob*stellar.org",
                httpClient = mockClient
            )
        }
    }

    @Test
    fun testStaticResolveTomlFetchFails() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/.well-known/stellar.toml")) {
                respond(
                    content = "Not found",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            } else {
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val mockClient = HttpClient(mockEngine)

        assertFails {
            FederationService.resolveStellarAddress(
                address = "bob*stellar.org",
                httpClient = mockClient
            )
        }
    }

    // ========== C. fromDomain factory — 2 tests ==========

    @Test
    fun testFromDomainSuccess() = runTest {
        val mockClient = createTomlAndFederationMockClient(validToml, basicResponse)

        val service = FederationService.fromDomain(
            domain = "stellar.org",
            httpClient = mockClient
        )

        assertEquals("https://federation.example.com/federation", service.federationServerUrl)
    }

    @Test
    fun testFromDomainMissingFederationServer() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/.well-known/stellar.toml")) {
                respond(
                    content = noFederationServerToml,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            } else {
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val mockClient = HttpClient(mockEngine)

        assertFailsWith<Sep02FederationNotFoundException> {
            FederationService.fromDomain(
                domain = "stellar.org",
                httpClient = mockClient
            )
        }
    }

    // ========== D. Account ID resolution — 2 tests ==========

    @Test
    fun testResolveAccountIdSuccess() = runTest {
        val mockClient = createFederationMockClient(accountIdResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveAccountId("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ")

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("bob*stellar.org", response.stellarAddress)
    }

    @Test
    fun testResolveAccountId404() = runTest {
        val mockClient = createFederationMockClient(
            responseContent = """{"error":"not found"}""",
            statusCode = HttpStatusCode.NotFound
        )
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep02InvalidResponseException> {
            service.resolveAccountId("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ")
        }

        assertTrue(exception.message!!.contains("HTTP 404"))
    }

    // ========== E. Transaction ID resolution — 1 test ==========

    @Test
    fun testResolveTransactionIdSuccess() = runTest {
        val mockClient = createFederationMockClient(forwardResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveTransactionId("c1b368...")

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("hash", response.memoType)
        assertEquals("dGVzdGhhc2g=", response.memo)
    }

    // ========== F. Forward resolution — 3 tests ==========

    @Test
    fun testResolveForwardBankAccount() = runTest {
        val mockClient = createFederationMockClient(forwardResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveForward(
            mapOf(
                "forward_type" to "bank_account",
                "swift" to "BOPBPHMM",
                "acct" to "2382376"
            )
        )

        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
    }

    @Test
    fun testResolveForwardMultipleParams() = runTest {
        val mockClient = createFederationMockClient(forwardResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val response = service.resolveForward(
            mapOf(
                "forward_type" to "bank_account",
                "swift" to "BOPBPHMM",
                "acct" to "2382376",
                "custom_field" to "custom_value"
            )
        )

        assertNotNull(response)
        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
    }

    @Test
    fun testResolveForwardNoQParameter() = runTest {
        var requestVerified = false
        val mockEngine = MockEngine { request ->
            requestVerified = request.url.parameters["type"] == "forward" &&
                    request.url.parameters["q"] == null &&
                    request.url.parameters["swift"] == "BOPBPHMM" &&
                    request.url.parameters["acct"] == "2382376"
            respond(
                content = forwardResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = HttpClient(mockEngine)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        service.resolveForward(
            mapOf(
                "swift" to "BOPBPHMM",
                "acct" to "2382376"
            )
        )

        assertTrue(requestVerified, "Request should have type=forward, no q parameter, and custom params")
    }

    // ========== G. Error handling — 5 tests ==========

    @Test
    fun testHttp404WithErrorField() = runTest {
        val mockClient = createFederationMockClient(
            responseContent = """{"error":"record not found"}""",
            statusCode = HttpStatusCode.NotFound
        )
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep02InvalidResponseException> {
            service.resolveStellarAddress("bob*stellar.org")
        }

        assertTrue(exception.message!!.contains("HTTP 404"))
        assertTrue(exception.message!!.contains("record not found"))
    }

    @Test
    fun testHttp429() = runTest {
        val mockClient = createFederationMockClient(
            responseContent = """{"error":"rate limited"}""",
            statusCode = HttpStatusCode.TooManyRequests
        )
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep02InvalidResponseException> {
            service.resolveStellarAddress("bob*stellar.org")
        }

        assertTrue(exception.message!!.contains("HTTP 429"))
    }

    @Test
    fun testHttp500() = runTest {
        val mockClient = createFederationMockClient(
            responseContent = """{"error":"internal server error"}""",
            statusCode = HttpStatusCode.InternalServerError
        )
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep02InvalidResponseException> {
            service.resolveStellarAddress("bob*stellar.org")
        }

        assertTrue(exception.message!!.contains("HTTP 500"))
    }

    @Test
    fun testMalformedJsonResponse() = runTest {
        val mockClient = createFederationMockClient(
            responseContent = "not json",
            statusCode = HttpStatusCode.OK
        )
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        assertFailsWith<Sep02InvalidResponseException> {
            service.resolveStellarAddress("bob*stellar.org")
        }
    }

    @Test
    fun testErrorFieldParsedFromBody() = runTest {
        val mockClient = createFederationMockClient(
            responseContent = """{"error":"access denied"}""",
            statusCode = HttpStatusCode.Forbidden
        )
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep02InvalidResponseException> {
            service.resolveStellarAddress("bob*stellar.org")
        }

        assertTrue(exception.message!!.contains("HTTP 403"))
        assertTrue(exception.message!!.contains("access denied"))
    }

    @Test
    fun testHttpErrorWithNonJsonBody() = runTest {
        val mockClient = createFederationMockClient(
            responseContent = "<html><body>Bad Gateway</body></html>",
            statusCode = HttpStatusCode.BadGateway
        )
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep02InvalidResponseException> {
            service.resolveStellarAddress("bob*stellar.org")
        }

        // No parsable "error" field, so the HTTP status description is used instead.
        assertEquals(
            "Federation request failed (HTTP 502): ${HttpStatusCode.BadGateway.description}",
            exception.message
        )
    }

    @Test
    fun testHttpErrorWithJsonBodyWithoutErrorField() = runTest {
        val mockClient = createFederationMockClient(
            responseContent = """{"detail":"quota exceeded"}""",
            statusCode = HttpStatusCode.ServiceUnavailable
        )
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep02InvalidResponseException> {
            service.resolveStellarAddress("bob*stellar.org")
        }

        assertEquals(
            "Federation request failed (HTTP 503): ${HttpStatusCode.ServiceUnavailable.description}",
            exception.message
        )
    }

    @Test
    fun testInformationalStatusTreatedAsError() = runTest {
        // Only 2xx is a success. A 1xx status must surface as an invalid response.
        val mockClient = createFederationMockClient(
            responseContent = basicResponse,
            statusCode = HttpStatusCode(199, "Informational")
        )
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep02InvalidResponseException> {
            service.resolveStellarAddress("bob*stellar.org")
        }

        assertTrue(exception.message!!.contains("HTTP 199"))
    }

    @Test
    fun testCustomHeadersForwarded() = runTest {
        var apiKey: String? = null
        var trace: String? = null
        val mockEngine = MockEngine { request ->
            apiKey = request.headers["X-Api-Key"]
            trace = request.headers["X-Custom-Trace"]
            respond(
                content = basicResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = HttpClient(mockEngine),
            httpRequestHeaders = mapOf(
                "X-Api-Key" to "my-api-key-123",
                "X-Custom-Trace" to "trace-abc"
            )
        )

        service.resolveStellarAddress("bob*stellar.org")

        assertEquals("my-api-key-123", apiKey)
        assertEquals("trace-abc", trace)
    }

    // ========== H. FederationResponse parsing — 4 tests ==========

    @Test
    fun testResponseAllFieldsPresent() = runTest {
        val json = Json.parseToJsonElement(
            """{"stellar_address":"bob*stellar.org","account_id":"GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ","memo_type":"text","memo":"hello"}"""
        )
        val jsonObject = json as kotlinx.serialization.json.JsonObject

        val response = FederationResponse.fromJson(jsonObject)

        assertEquals("bob*stellar.org", response.stellarAddress)
        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
        assertEquals("text", response.memoType)
        assertEquals("hello", response.memo)
    }

    @Test
    fun testResponseNullMemo() = runTest {
        val json = Json.parseToJsonElement(
            """{"stellar_address":"bob*stellar.org","account_id":"GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"}"""
        )
        val jsonObject = json as kotlinx.serialization.json.JsonObject

        val response = FederationResponse.fromJson(jsonObject)

        assertNull(response.memoType)
        assertNull(response.memo)
    }

    @Test
    fun testResponseNullStellarAddress() = runTest {
        val json = Json.parseToJsonElement(
            """{"account_id":"GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ"}"""
        )
        val jsonObject = json as kotlinx.serialization.json.JsonObject

        val response = FederationResponse.fromJson(jsonObject)

        assertNull(response.stellarAddress)
        assertEquals("GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ", response.accountId)
    }

    @Test
    fun testResponseMissingAccountId() = runTest {
        val json = Json.parseToJsonElement(
            """{"stellar_address":"bob*stellar.org"}"""
        )
        val jsonObject = json as kotlinx.serialization.json.JsonObject

        val exception = assertFailsWith<Sep02InvalidResponseException> {
            FederationResponse.fromJson(jsonObject)
        }

        assertEquals("Missing required 'account_id' field in federation response", exception.message)
    }

    // ========== I. Input validation edge cases — 5 tests ==========

    @Test
    fun testEmptyStringAddress() = runTest {
        assertFailsWith<Sep02InvalidAddressException> {
            FederationService.parseAddress("")
        }
    }

    @Test
    fun testStarDomainOnly() = runTest {
        assertFailsWith<Sep02InvalidAddressException> {
            FederationService.parseAddress("*example.com")
        }
    }

    @Test
    fun testUserStarOnly() = runTest {
        assertFailsWith<Sep02InvalidAddressException> {
            FederationService.parseAddress("bob*")
        }
    }

    @Test
    fun testPhoneNumberAddress() = runTest {
        val (user, domain) = FederationService.parseAddress("+14155550100*example.com")

        assertEquals("+14155550100", user)
        assertEquals("example.com", domain)
    }

    @Test
    fun testMultipleAsterisks() = runTest {
        assertFailsWith<Sep02InvalidAddressException> {
            FederationService.parseAddress("user*extra*domain.com")
        }
    }

    // ========== J. parseAddress utility — 1 test ==========

    @Test
    fun testParseAddressBasic() = runTest {
        val (user, domain) = FederationService.parseAddress("bob*stellar.org")

        assertEquals("bob", user)
        assertEquals("stellar.org", domain)
    }

    // ========== K. Exception string representation — 4 tests ==========

    @Test
    fun testInvalidAddressExceptionToString() = runTest {
        val mockClient = createFederationMockClient(basicResponse)
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep02InvalidAddressException> {
            service.resolveStellarAddress("nope")
        }

        assertEquals(
            "SEP-02 invalid address: Invalid Stellar address: 'nope'. " +
                "Expected format: username*domain",
            exception.toString()
        )
    }

    @Test
    fun testFederationNotFoundExceptionToString() = runTest {
        val mockClient = createTomlAndFederationMockClient(noFederationServerToml, "{}")

        val exception = assertFailsWith<Sep02FederationNotFoundException> {
            FederationService.fromDomain(domain = "stellar.org", httpClient = mockClient)
        }

        assertEquals(
            "SEP-02 federation server not found: No federation server found in " +
                "stellar.toml for domain: stellar.org",
            exception.toString()
        )
    }

    @Test
    fun testInvalidResponseExceptionToString() = runTest {
        val mockClient = createFederationMockClient(
            responseContent = """{"error":"record not found"}""",
            statusCode = HttpStatusCode.NotFound
        )
        val service = FederationService(
            federationServerUrl = "https://federation.example.com/federation",
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep02InvalidResponseException> {
            service.resolveStellarAddress("bob*stellar.org")
        }

        assertEquals(
            "SEP-02 invalid response: Federation request failed (HTTP 404): record not found",
            exception.toString()
        )
    }

    @Test
    fun testBaseExceptionToString() = runTest {
        // The base type is never thrown by the SDK itself; it exists so callers can catch
        // every SEP-2 failure with a single clause.
        val exception = Sep02Exception("something went wrong")

        assertEquals("SEP-02 error: something went wrong", exception.toString())
        assertEquals("something went wrong", exception.message)
        assertNull(exception.cause)
    }
}
