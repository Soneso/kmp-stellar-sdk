// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep30

import com.soneso.stellar.sdk.sep.sep30.*
import com.soneso.stellar.sdk.sep.sep30.exceptions.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for [Sep30Service] using MockEngine.
 *
 * Tests cover all service methods (registerAccount, updateIdentitiesForAccount,
 * signTransaction, accountDetails, deleteAccount, accounts), error handling for
 * all HTTP status codes, request serialization, custom headers forwarding,
 * a full recovery workflow, and multi-server signing.
 *
 * The Sep30Service handles JSON serialization internally via TextContent, so
 * the mock clients do not need any serialization plugins.
 */
class Sep30ServiceTest {

    // ========== Test Constants ==========

    private val serviceUrl = "https://recovery.example.com"
    private val testJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJHQUJDIn0.test_signature"
    private val testAddress = "GDQNY3PBOBD7WLWZGZQS6WNST7VJHPKXSQFWBHQFHV3XMUC5DHAST4"
    private val testSigningAddress = "GCXE4HKPNGT4FOBMGUH3RGSTFLHPECGQO3RLCSPY3MHCM5NHQWMIH5E"
    private val testSigningAddress2 = "GBQB2BTHLUAHZWCAYXZPPQPCJ7JFQWJB5M3YAINOQJKAB7LLF7MS6FG"
    private val testAddress2 = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
    private val testTransactionXdr =
        "AAAAAgAAAABi/B0L0JGythwN1lY0aypo19NHxvLCyO5tBEcCVvwF9wAAAGQAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAACgAAAA"
    private val testNetworkPassphrase = "Test SDF Network ; September 2015"

    // ========== Mock JSON Responses ==========

    private val accountResponseJson = """
        {
            "address": "$testAddress",
            "identities": [
                {"role": "owner", "authenticated": true}
            ],
            "signers": [
                {"key": "$testSigningAddress"}
            ]
        }
    """.trimIndent()

    private val accountResponseMultiIdentityJson = """
        {
            "address": "$testAddress",
            "identities": [
                {"role": "owner", "authenticated": true},
                {"role": "sender", "authenticated": false}
            ],
            "signers": [
                {"key": "$testSigningAddress"},
                {"key": "$testSigningAddress2"}
            ]
        }
    """.trimIndent()

    private val accountResponseNoAuthenticatedJson = """
        {
            "address": "$testAddress",
            "identities": [
                {"role": "owner"}
            ],
            "signers": [
                {"key": "$testSigningAddress"}
            ]
        }
    """.trimIndent()

    private val signatureResponseJson = """
        {
            "signature": "YpVelqPAKsYTPppgraph3MZLG5L7tmCkE3G52X2deHNuwWGlOGzw9Cs4DF0sNkBnBD1G5qvbXHqz0EQ",
            "network_passphrase": "$testNetworkPassphrase"
        }
    """.trimIndent()

    private val accountsResponseJson = """
        {
            "accounts": [
                {
                    "address": "$testAddress",
                    "identities": [
                        {"role": "owner", "authenticated": true}
                    ],
                    "signers": [
                        {"key": "$testSigningAddress"}
                    ]
                },
                {
                    "address": "$testAddress2",
                    "identities": [
                        {"role": "sender", "authenticated": true},
                        {"role": "receiver"}
                    ],
                    "signers": [
                        {"key": "$testSigningAddress"}
                    ]
                }
            ]
        }
    """.trimIndent()

    private val accountsEmptyResponseJson = """
        {
            "accounts": []
        }
    """.trimIndent()

    // ========== Helper Methods ==========

    private fun createMockClient(
        expectedMethod: HttpMethod,
        expectedPath: String,
        responseBody: String,
        responseStatus: HttpStatusCode = HttpStatusCode.OK,
        requestValidator: ((HttpRequestData) -> Unit)? = null
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestValidator?.invoke(request)
                    assertEquals(expectedMethod, request.method)
                    assertTrue(
                        request.url.encodedPath.endsWith(expectedPath),
                        "Expected path to end with '$expectedPath' but was '${request.url.encodedPath}'"
                    )
                    respond(
                        content = responseBody,
                        status = responseStatus,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
    }

    private fun createRoutingMockClient(
        router: (HttpRequestData) -> Pair<String, HttpStatusCode>
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val (body, status) = router(request)
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
    }

    private fun createInlineMockClient(mockEngine: MockEngine): HttpClient {
        return HttpClient(mockEngine)
    }

    private fun buildSingleIdentityRequest(): Sep30Request {
        val emailAuth = Sep30AuthMethod(type = "email", value = "user@example.com")
        val ownerIdentity = Sep30RequestIdentity(role = "owner", authMethods = listOf(emailAuth))
        return Sep30Request(identities = listOf(ownerIdentity))
    }

    private fun buildMultiIdentityRequest(): Sep30Request {
        val emailAuth = Sep30AuthMethod(type = "email", value = "user@example.com")
        val phoneAuth = Sep30AuthMethod(type = "phone_number", value = "+14155551234")
        val stellarAuth = Sep30AuthMethod(type = "stellar_address", value = "user*example.com")

        val ownerIdentity = Sep30RequestIdentity(
            role = "owner",
            authMethods = listOf(emailAuth, phoneAuth)
        )
        val senderIdentity = Sep30RequestIdentity(
            role = "sender",
            authMethods = listOf(stellarAuth)
        )
        return Sep30Request(identities = listOf(ownerIdentity, senderIdentity))
    }

    // ========== 1. registerAccount Tests ==========

    @Test
    fun testRegisterAccountSuccess() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Post,
            expectedPath = "/accounts/$testAddress",
            responseBody = accountResponseJson
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val request = buildSingleIdentityRequest()

        val response = service.registerAccount(testAddress, request, testJwt)

        assertEquals(testAddress, response.address)
        assertEquals(1, response.identities.size)
        assertEquals("owner", response.identities[0].role)
        assertEquals(true, response.identities[0].authenticated)
        assertEquals(1, response.signers.size)
        assertEquals(testSigningAddress, response.signers[0].key)
    }

    @Test
    fun testRegisterAccountMultipleIdentities() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Post,
            expectedPath = "/accounts/$testAddress",
            responseBody = accountResponseMultiIdentityJson
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val request = buildMultiIdentityRequest()

        val response = service.registerAccount(testAddress, request, testJwt)

        assertEquals(testAddress, response.address)
        assertEquals(2, response.identities.size)
        assertEquals("owner", response.identities[0].role)
        assertEquals(true, response.identities[0].authenticated)
        assertEquals("sender", response.identities[1].role)
        assertEquals(false, response.identities[1].authenticated)
        assertEquals(2, response.signers.size)
        assertEquals(testSigningAddress, response.signers[0].key)
        assertEquals(testSigningAddress2, response.signers[1].key)
    }

    @Test
    fun testRegisterAccountVerifiesPostMethod() = runTest {
        var methodVerified = false
        val mockEngine = MockEngine { request ->
            methodVerified = request.method == HttpMethod.Post
            respond(
                content = accountResponseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.registerAccount(testAddress, buildSingleIdentityRequest(), testJwt)
        assertTrue(methodVerified, "Request method should be POST")
    }

    @Test
    fun testRegisterAccountVerifiesUrlPath() = runTest {
        var pathVerified = false
        val mockEngine = MockEngine { request ->
            pathVerified = request.url.encodedPath.endsWith("/accounts/$testAddress")
            respond(
                content = accountResponseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.registerAccount(testAddress, buildSingleIdentityRequest(), testJwt)
        assertTrue(pathVerified, "URL path should end with /accounts/$testAddress")
    }

    @Test
    fun testRegisterAccountVerifiesAuthHeader() = runTest {
        var authHeaderVerified = false
        val mockEngine = MockEngine { request ->
            authHeaderVerified = request.headers["Authorization"] == "Bearer $testJwt"
            respond(
                content = accountResponseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.registerAccount(testAddress, buildSingleIdentityRequest(), testJwt)
        assertTrue(authHeaderVerified, "Authorization header should be 'Bearer $testJwt'")
    }

    // ========== 2. updateIdentitiesForAccount Tests ==========

    @Test
    fun testUpdateIdentitiesSuccess() = runTest {
        val updatedResponseJson = """
            {
                "address": "$testAddress",
                "identities": [
                    {"role": "owner", "authenticated": false}
                ],
                "signers": [
                    {"key": "$testSigningAddress"}
                ]
            }
        """.trimIndent()

        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Put,
            expectedPath = "/accounts/$testAddress",
            responseBody = updatedResponseJson
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val phoneAuth = Sep30AuthMethod(type = "phone_number", value = "+14155559876")
        val updatedIdentity = Sep30RequestIdentity(role = "owner", authMethods = listOf(phoneAuth))
        val request = Sep30Request(identities = listOf(updatedIdentity))

        val response = service.updateIdentitiesForAccount(testAddress, request, testJwt)

        assertEquals(testAddress, response.address)
        assertEquals(1, response.identities.size)
        assertEquals("owner", response.identities[0].role)
        assertEquals(false, response.identities[0].authenticated)
        assertEquals(1, response.signers.size)
        assertEquals(testSigningAddress, response.signers[0].key)
    }

    @Test
    fun testUpdateIdentitiesVerifiesPutMethod() = runTest {
        var methodVerified = false
        val mockEngine = MockEngine { request ->
            methodVerified = request.method == HttpMethod.Put
            respond(content = accountResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.updateIdentitiesForAccount(testAddress, buildSingleIdentityRequest(), testJwt)
        assertTrue(methodVerified, "Request method should be PUT")
    }

    @Test
    fun testUpdateIdentitiesVerifiesBody() = runTest {
        var bodyContainsIdentities = false
        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as TextContent).text
            bodyContainsIdentities = bodyText.contains("\"identities\"") &&
                bodyText.contains("\"role\"") &&
                bodyText.contains("\"auth_methods\"")
            respond(content = accountResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.updateIdentitiesForAccount(testAddress, buildSingleIdentityRequest(), testJwt)
        assertTrue(bodyContainsIdentities, "Request body should contain full identity replacement data")
    }

    // ========== 3. signTransaction Tests ==========

    @Test
    fun testSignTransactionSuccess() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Post,
            expectedPath = "/accounts/$testAddress/sign/$testSigningAddress",
            responseBody = signatureResponseJson
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)

        val response = service.signTransaction(testAddress, testSigningAddress, testTransactionXdr, testJwt)

        assertEquals(
            "YpVelqPAKsYTPppgraph3MZLG5L7tmCkE3G52X2deHNuwWGlOGzw9Cs4DF0sNkBnBD1G5qvbXHqz0EQ",
            response.signature
        )
        assertEquals(testNetworkPassphrase, response.networkPassphrase)
    }

    @Test
    fun testSignTransactionVerifiesPostMethod() = runTest {
        var methodVerified = false
        val mockEngine = MockEngine { request ->
            methodVerified = request.method == HttpMethod.Post
            respond(content = signatureResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.signTransaction(testAddress, testSigningAddress, testTransactionXdr, testJwt)
        assertTrue(methodVerified, "Request method should be POST")
    }

    @Test
    fun testSignTransactionVerifiesUrlPath() = runTest {
        var pathVerified = false
        val mockEngine = MockEngine { request ->
            pathVerified = request.url.encodedPath.endsWith(
                "/accounts/$testAddress/sign/$testSigningAddress"
            )
            respond(content = signatureResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.signTransaction(testAddress, testSigningAddress, testTransactionXdr, testJwt)
        assertTrue(pathVerified, "URL path should end with /accounts/$testAddress/sign/$testSigningAddress")
    }

    @Test
    fun testSignTransactionVerifiesRequestBody() = runTest {
        var bodyCorrect = false
        val mockEngine = MockEngine { request ->
            val bodyText = (request.body as TextContent).text
            bodyCorrect = bodyText.contains("\"transaction\"") &&
                bodyText.contains(testTransactionXdr) &&
                !bodyText.contains("\"tx\"") &&
                !bodyText.contains("\"xdr\"")
            respond(content = signatureResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.signTransaction(testAddress, testSigningAddress, testTransactionXdr, testJwt)
        assertTrue(bodyCorrect, "Request body should use 'transaction' key with the base64 XDR value")
    }

    // ========== 4. accountDetails Tests ==========

    @Test
    fun testAccountDetailsSuccess() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Get,
            expectedPath = "/accounts/$testAddress",
            responseBody = accountResponseJson
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val response = service.accountDetails(testAddress, testJwt)

        assertEquals(testAddress, response.address)
        assertEquals(1, response.identities.size)
        assertEquals("owner", response.identities[0].role)
        assertEquals(true, response.identities[0].authenticated)
        assertEquals(1, response.signers.size)
        assertEquals(testSigningAddress, response.signers[0].key)
    }

    @Test
    fun testAccountDetailsWithoutAuthenticated() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Get,
            expectedPath = "/accounts/$testAddress",
            responseBody = accountResponseNoAuthenticatedJson
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val response = service.accountDetails(testAddress, testJwt)

        assertEquals(testAddress, response.address)
        assertEquals(1, response.identities.size)
        assertEquals("owner", response.identities[0].role)
        assertNull(response.identities[0].authenticated)
    }

    @Test
    fun testAccountDetailsVerifiesGetMethod() = runTest {
        var methodVerified = false
        val mockEngine = MockEngine { request ->
            methodVerified = request.method == HttpMethod.Get
            respond(content = accountResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.accountDetails(testAddress, testJwt)
        assertTrue(methodVerified, "Request method should be GET")
    }

    // ========== 5. deleteAccount Tests ==========

    @Test
    fun testDeleteAccountSuccess() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Delete,
            expectedPath = "/accounts/$testAddress",
            responseBody = accountResponseJson
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val response = service.deleteAccount(testAddress, testJwt)

        assertEquals(testAddress, response.address)
        assertEquals(1, response.identities.size)
        assertEquals("owner", response.identities[0].role)
        assertEquals(true, response.identities[0].authenticated)
        assertEquals(1, response.signers.size)
        assertEquals(testSigningAddress, response.signers[0].key)
    }

    @Test
    fun testDeleteAccountVerifiesDeleteMethod() = runTest {
        var methodVerified = false
        val mockEngine = MockEngine { request ->
            methodVerified = request.method == HttpMethod.Delete
            respond(content = accountResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.deleteAccount(testAddress, testJwt)
        assertTrue(methodVerified, "Request method should be DELETE")
    }

    @Test
    fun testDeleteAccountVerifiesNoRequestBody() = runTest {
        var bodyIsEmpty = false
        val mockEngine = MockEngine { request ->
            val body = request.body
            val hasNoContent = body.contentLength == null || body.contentLength == 0L ||
                (body is TextContent && (body.text.isEmpty() || body.text == "{}"))
            bodyIsEmpty = hasNoContent || body !is TextContent ||
                (!body.text.contains("\"identities\"") && !body.text.contains("\"transaction\""))
            respond(content = accountResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.deleteAccount(testAddress, testJwt)
        assertTrue(bodyIsEmpty, "DELETE request should not carry identities or transaction data in the body")
    }

    // ========== 6. accounts (list) Tests ==========

    @Test
    fun testAccountsListSuccess() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Get,
            expectedPath = "/accounts",
            responseBody = accountsResponseJson
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val response = service.accounts(jwt = testJwt)

        assertEquals(2, response.accounts.size)
        assertEquals(testAddress, response.accounts[0].address)
        assertEquals(1, response.accounts[0].identities.size)
        assertEquals("owner", response.accounts[0].identities[0].role)
        assertEquals(testAddress2, response.accounts[1].address)
        assertEquals(2, response.accounts[1].identities.size)
        assertEquals("sender", response.accounts[1].identities[0].role)
        assertEquals("receiver", response.accounts[1].identities[1].role)
        assertNull(response.accounts[1].identities[1].authenticated)
    }

    @Test
    fun testAccountsListWithPagination() = runTest {
        var afterParamPresent = false
        val mockEngine = MockEngine { request ->
            afterParamPresent = request.url.toString().contains("?after=$testAddress")
            respond(content = accountsResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.accounts(jwt = testJwt, after = testAddress)
        assertTrue(afterParamPresent, "Request URL should contain '?after=$testAddress'")
    }

    @Test
    fun testAccountsListEmptyResults() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Get,
            expectedPath = "/accounts",
            responseBody = accountsEmptyResponseJson
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val response = service.accounts(jwt = testJwt)
        assertTrue(response.accounts.isEmpty())
    }

    @Test
    fun testAccountsListVerifiesGetMethod() = runTest {
        var methodVerified = false
        val mockEngine = MockEngine { request ->
            methodVerified = request.method == HttpMethod.Get
            respond(content = accountsResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(serviceUrl, httpClient = createInlineMockClient(mockEngine))
        service.accounts(jwt = testJwt)
        assertTrue(methodVerified, "Request method should be GET")
    }

    // ========== 7. Error Handling Tests ==========

    @Test
    fun testRegisterAccountBadRequest() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Post,
            expectedPath = "/accounts/$testAddress",
            responseBody = """{"error": "missing required 'identities' field"}""",
            responseStatus = HttpStatusCode.BadRequest
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val exception = assertFailsWith<Sep30BadRequestException> {
            service.registerAccount(testAddress, buildSingleIdentityRequest(), testJwt)
        }
        assertTrue(exception.message!!.contains("missing required 'identities' field"),
            "Exception message should contain the error from the server response")
    }

    @Test
    fun testRegisterAccountUnauthorized() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Post,
            expectedPath = "/accounts/$testAddress",
            responseBody = """{"error": "SEP-10 token expired"}""",
            responseStatus = HttpStatusCode.Unauthorized
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        assertFailsWith<Sep30UnauthorizedException> {
            service.registerAccount(testAddress, buildSingleIdentityRequest(), testJwt)
        }
    }

    @Test
    fun testAccountDetailsNotFound() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Get,
            expectedPath = "/accounts/$testAddress",
            responseBody = """{"error": "account not registered"}""",
            responseStatus = HttpStatusCode.NotFound
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        assertFailsWith<Sep30NotFoundException> {
            service.accountDetails(testAddress, testJwt)
        }
    }

    @Test
    fun testRegisterAccountConflict() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Post,
            expectedPath = "/accounts/$testAddress",
            responseBody = """{"error": "account already registered"}""",
            responseStatus = HttpStatusCode.Conflict
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        assertFailsWith<Sep30ConflictException> {
            service.registerAccount(testAddress, buildSingleIdentityRequest(), testJwt)
        }
    }

    @Test
    fun testAccountDetailsUnknownError() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Get,
            expectedPath = "/accounts/$testAddress",
            responseBody = """{"error": "internal server error"}""",
            responseStatus = HttpStatusCode.InternalServerError
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val exception = assertFailsWith<Sep30UnknownResponseException> {
            service.accountDetails(testAddress, testJwt)
        }
        assertEquals(500, exception.statusCode)
    }

    @Test
    fun testErrorMessageExtracted() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Post,
            expectedPath = "/accounts/$testAddress",
            responseBody = """{"error": "identities must contain at least one entry"}""",
            responseStatus = HttpStatusCode.BadRequest
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        val exception = assertFailsWith<Sep30BadRequestException> {
            service.registerAccount(testAddress, buildSingleIdentityRequest(), testJwt)
        }
        assertEquals("identities must contain at least one entry", exception.message)
    }

    @Test
    fun testMalformedJsonResponse() = runTest {
        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Get,
            expectedPath = "/accounts/$testAddress",
            responseBody = "this is not valid json at all",
            responseStatus = HttpStatusCode.OK
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        assertFailsWith<Sep30InvalidResponseException> {
            service.accountDetails(testAddress, testJwt)
        }
    }

    @Test
    fun testMissingRequiredFieldsResponse() = runTest {
        val incompleteJson = """
            {
                "identities": [{"role": "owner"}],
                "signers": [{"key": "$testSigningAddress"}]
            }
        """.trimIndent()

        val mockClient = createMockClient(
            expectedMethod = HttpMethod.Get,
            expectedPath = "/accounts/$testAddress",
            responseBody = incompleteJson,
            responseStatus = HttpStatusCode.OK
        )
        val service = Sep30Service(serviceUrl, httpClient = mockClient)
        assertFailsWith<Sep30InvalidResponseException> {
            service.accountDetails(testAddress, testJwt)
        }
    }

    // ========== 8. Custom Headers Test ==========

    @Test
    fun testCustomHeadersForwarded() = runTest {
        var xApiKeyVerified = false
        var xCustomVerified = false
        val mockEngine = MockEngine { request ->
            xApiKeyVerified = request.headers["X-Api-Key"] == "my-api-key-123"
            xCustomVerified = request.headers["X-Custom-Trace"] == "trace-abc"
            respond(content = accountResponseJson, status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val service = Sep30Service(
            serviceUrl = serviceUrl,
            httpClient = createInlineMockClient(mockEngine),
            httpRequestHeaders = mapOf(
                "X-Api-Key" to "my-api-key-123",
                "X-Custom-Trace" to "trace-abc"
            )
        )
        service.accountDetails(testAddress, testJwt)
        assertTrue(xApiKeyVerified, "X-Api-Key header should be forwarded")
        assertTrue(xCustomVerified, "X-Custom-Trace header should be forwarded")
    }

    // ========== 9. Request Serialization Tests ==========

    @Test
    fun testSep30RequestToJson() = runTest {
        val emailAuth = Sep30AuthMethod(type = "email", value = "user@example.com")
        val ownerIdentity = Sep30RequestIdentity(role = "owner", authMethods = listOf(emailAuth))
        val request = Sep30Request(identities = listOf(ownerIdentity))

        val json = request.toJson()

        assertTrue(json.containsKey("identities"))
        @Suppress("UNCHECKED_CAST")
        val identitiesList = json["identities"] as List<Map<String, Any?>>
        assertEquals(1, identitiesList.size)
        assertEquals("owner", identitiesList[0]["role"])
        @Suppress("UNCHECKED_CAST")
        val authMethods = identitiesList[0]["auth_methods"] as List<Map<String, Any?>>
        assertEquals(1, authMethods.size)
        assertEquals("email", authMethods[0]["type"])
        assertEquals("user@example.com", authMethods[0]["value"])
    }

    @Test
    fun testSep30RequestIdentityToJson() = runTest {
        val emailAuth = Sep30AuthMethod(type = "email", value = "user@example.com")
        val phoneAuth = Sep30AuthMethod(type = "phone_number", value = "+14155551234")
        val identity = Sep30RequestIdentity(role = "owner", authMethods = listOf(emailAuth, phoneAuth))

        val json = identity.toJson()

        assertEquals("owner", json["role"])
        assertTrue(json.containsKey("auth_methods"), "Key should be 'auth_methods' (snake_case)")
        assertFalse(json.containsKey("authMethods"), "Should not use camelCase key")
        @Suppress("UNCHECKED_CAST")
        val methods = json["auth_methods"] as List<Map<String, Any?>>
        assertEquals(2, methods.size)
    }

    @Test
    fun testSep30AuthMethodToJson() = runTest {
        val authMethod = Sep30AuthMethod(type = "stellar_address", value = "user*example.com")
        val json = authMethod.toJson()

        assertEquals("stellar_address", json["type"])
        assertEquals("user*example.com", json["value"])
        assertEquals(2, json.size, "Should contain exactly 'type' and 'value'")
    }

    @Test
    fun testMultiIdentityRequestToJson() = runTest {
        val emailAuth = Sep30AuthMethod(type = "email", value = "admin@example.com")
        val phoneAuth = Sep30AuthMethod(type = "phone_number", value = "+14155551234")
        val stellarAuth = Sep30AuthMethod(type = "stellar_address", value = "admin*example.com")

        val ownerIdentity = Sep30RequestIdentity(
            role = "owner", authMethods = listOf(emailAuth, phoneAuth)
        )
        val otherIdentity = Sep30RequestIdentity(
            role = "other", authMethods = listOf(stellarAuth)
        )
        val request = Sep30Request(identities = listOf(ownerIdentity, otherIdentity))
        val json = request.toJson()

        @Suppress("UNCHECKED_CAST")
        val identitiesList = json["identities"] as List<Map<String, Any?>>
        assertEquals(2, identitiesList.size)

        assertEquals("owner", identitiesList[0]["role"])
        @Suppress("UNCHECKED_CAST")
        val ownerMethods = identitiesList[0]["auth_methods"] as List<Map<String, Any?>>
        assertEquals(2, ownerMethods.size)
        assertEquals("email", ownerMethods[0]["type"])
        assertEquals("admin@example.com", ownerMethods[0]["value"])
        assertEquals("phone_number", ownerMethods[1]["type"])
        assertEquals("+14155551234", ownerMethods[1]["value"])

        assertEquals("other", identitiesList[1]["role"])
        @Suppress("UNCHECKED_CAST")
        val otherMethods = identitiesList[1]["auth_methods"] as List<Map<String, Any?>>
        assertEquals(1, otherMethods.size)
        assertEquals("stellar_address", otherMethods[0]["type"])
        assertEquals("admin*example.com", otherMethods[0]["value"])
    }

    // ========== 10. Full Workflow Test ==========

    @Test
    fun testFullRecoveryWorkflow() = runTest {
        var requestCount = 0
        val mockClient = createRoutingMockClient { request ->
            requestCount++
            val path = request.url.encodedPath
            val method = request.method

            when {
                method == HttpMethod.Post && path.endsWith("/accounts/$testAddress")
                    && !path.contains("/sign/") ->
                    accountResponseJson to HttpStatusCode.OK

                method == HttpMethod.Get && path.endsWith("/accounts/$testAddress") ->
                    accountResponseJson to HttpStatusCode.OK

                method == HttpMethod.Put && path.endsWith("/accounts/$testAddress") -> {
                    val updatedJson = """
                        {
                            "address": "$testAddress",
                            "identities": [
                                {"role": "owner", "authenticated": false},
                                {"role": "sender"}
                            ],
                            "signers": [
                                {"key": "$testSigningAddress"},
                                {"key": "$testSigningAddress2"}
                            ]
                        }
                    """.trimIndent()
                    updatedJson to HttpStatusCode.OK
                }

                method == HttpMethod.Post && path.contains("/sign/") ->
                    signatureResponseJson to HttpStatusCode.OK

                method == HttpMethod.Delete && path.endsWith("/accounts/$testAddress") ->
                    accountResponseJson to HttpStatusCode.OK

                else ->
                    """{"error": "unexpected request: $method $path"}""" to HttpStatusCode.InternalServerError
            }
        }
        val service = Sep30Service(serviceUrl, httpClient = mockClient)

        // Step 1: Register
        val registerResponse = service.registerAccount(testAddress, buildSingleIdentityRequest(), testJwt)
        assertEquals(testAddress, registerResponse.address)
        assertEquals(1, registerResponse.signers.size)
        assertEquals(testSigningAddress, registerResponse.signers[0].key)

        // Step 2: Query details
        val detailsResponse = service.accountDetails(testAddress, testJwt)
        assertEquals(testAddress, detailsResponse.address)
        assertEquals(true, detailsResponse.identities[0].authenticated)

        // Step 3: Update identities
        val updateResponse = service.updateIdentitiesForAccount(testAddress, buildMultiIdentityRequest(), testJwt)
        assertEquals(2, updateResponse.identities.size)
        assertEquals(2, updateResponse.signers.size)

        // Step 4: Sign transaction
        val signResponse = service.signTransaction(testAddress, testSigningAddress, testTransactionXdr, testJwt)
        assertNotNull(signResponse.signature)
        assertEquals(testNetworkPassphrase, signResponse.networkPassphrase)

        // Step 5: Delete account
        val deleteResponse = service.deleteAccount(testAddress, testJwt)
        assertEquals(testAddress, deleteResponse.address)

        assertEquals(5, requestCount, "All 5 workflow steps should have been executed")
    }

    // ========== 11. Multi-Server Signing Test ==========

    @Test
    fun testMultiServerSigning() = runTest {
        val server1Url = "https://recovery-server-1.example.com"
        val server2Url = "https://recovery-server-2.example.com"
        val signature1 = "SIG1_YpVelqPAKsYTPppgraph3MZLG5L7tmCkE3G52X2deHNuwWGlOGzw"
        val signature2 = "SIG2_BpQelqPAKsYTPppgraph3MZLG5L7tmCkE3G52X2deHNuwWGlOGzw"

        val mockClient1 = createMockClient(
            expectedMethod = HttpMethod.Post,
            expectedPath = "/accounts/$testAddress/sign/$testSigningAddress",
            responseBody = """
                {
                    "signature": "$signature1",
                    "network_passphrase": "$testNetworkPassphrase"
                }
            """.trimIndent()
        )

        val mockClient2 = createMockClient(
            expectedMethod = HttpMethod.Post,
            expectedPath = "/accounts/$testAddress/sign/$testSigningAddress2",
            responseBody = """
                {
                    "signature": "$signature2",
                    "network_passphrase": "$testNetworkPassphrase"
                }
            """.trimIndent()
        )

        val service1 = Sep30Service(server1Url, httpClient = mockClient1)
        val service2 = Sep30Service(server2Url, httpClient = mockClient2)

        val response1 = service1.signTransaction(testAddress, testSigningAddress, testTransactionXdr, testJwt)
        val response2 = service2.signTransaction(testAddress, testSigningAddress2, testTransactionXdr, testJwt)

        assertEquals(signature1, response1.signature)
        assertEquals(signature2, response2.signature)
        assertEquals(testNetworkPassphrase, response1.networkPassphrase)
        assertEquals(testNetworkPassphrase, response2.networkPassphrase)
        assertNotEquals(response1.signature, response2.signature)
    }
}
