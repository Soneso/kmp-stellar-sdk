// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep31

import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsRequest
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.Sep31TransactionResponse
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ConfigurationException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31CustomerInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ForbiddenException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionCallbackNotSupportedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionNotFoundException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnauthorizedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnknownResponseException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Sep31ServiceTest {

    // ==================== Test constants ====================

    private val serviceUrl = "https://anchor.example.org"
    private val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.testtoken"
    private val txId = "82fhs729f63dh0v4"
    private val callbackUrl = "https://wallet.example.org/sep31-callback"

    // ==================== Fixtures ====================

    // SEP-31 v3.1.0 GET /info - spec example (sep-0031.md L386-400) captured 2026-05-16
    private val infoResponseJson = """
        {
          "receive": {
            "USDC": {
              "quotes_supported": true,
              "quotes_required": false,
              "fee_fixed": 5,
              "fee_percent": 1,
              "min_amount": 0.1,
              "max_amount": 1000,
              "funding_methods": ["SEPA", "SWIFT"],
              "sep12": {
                "sender": { "types": {} },
                "receiver": { "types": {} }
              }
            }
          }
        }
    """.trimIndent()

    // SEP-31 v3.1.0 transaction object - SYNTHESIZED from spec sep-0031.md L786-802 with status="pending_sender" - captured 2026-05-16
    private val pendingSenderTransactionJson = """
        {
          "transaction": {
            "id": "82fhs729f63dh0v4",
            "status": "pending_sender",
            "status_eta": 3600,
            "status_message": "Awaiting Stellar payment from Sending Anchor.",
            "stellar_account_id": "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H",
            "stellar_memo": "123456789",
            "stellar_memo_type": "id",
            "amount_in": "18.34",
            "amount_out": "18.24",
            "amount_fee": "0.1",
            "started_at": "2017-03-20T17:05:32Z"
          }
        }
    """.trimIndent()

    private val postTransactionsResponseJson = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "stellar_account_id": "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H",
          "stellar_memo_type": "id",
          "stellar_memo": "987654321"
        }
    """.trimIndent()

    private val genericErrorJson = """{"error":"Something went wrong"}"""
    private val customerInfoNeededJson = """{"error":"customer_info_needed","type":"sep31-sender"}"""
    private val customerInfoNeededNoTypeJson = """{"error":"customer_info_needed"}"""
    private val transactionInfoNeededJson = """
        {
          "error": "transaction_info_needed",
          "fields": {
            "transaction": {
              "receiver_account_number": { "description": "The receiver account number" }
            }
          }
        }
    """.trimIndent()

    // Regex matching the canonical three-segment JWT shape: header.payload.signature where
    // every segment is one or more base64url characters and the header starts with 'eyJ'.
    // Mirrors the production-side regex in JsonElementUtil.sanitizeAnchorString.
    private val jwtPattern = Regex("eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+")

    // ==================== MockEngine helpers ====================

    /**
     * Builds a single-response mock [HttpClient] that captures all requests and
     * responds with the given [responseContent] and [statusCode]. The optional
     * [requestValidator] lambda receives the full [HttpRequestData] so tests can
     * assert on URL, method, headers, and body without a second round-trip.
     */
    private fun mockClient(
        responseContent: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
        requestValidator: ((HttpRequestData) -> Unit)? = null,
    ): HttpClient {
        val engine = MockEngine { request ->
            requestValidator?.invoke(request)
            respond(
                content = responseContent,
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, contentType),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    /**
     * Builds a mock [HttpClient] that returns 204 No Content with no body
     * (for `PUT /transactions/:id/callback` success path).
     */
    private fun mockNoContentClient(
        requestValidator: ((HttpRequestData) -> Unit)? = null,
    ): HttpClient {
        val engine = MockEngine { request ->
            requestValidator?.invoke(request)
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
                headers = headersOf(),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    }

    // ==================== Constructor / URL safety ====================

    @Test
    fun constructor_httpScheme_throwsIllegalArgumentException() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Sep31Service("http://anchor.example.org")
        }
    }

    @Test
    fun constructor_validHttpsUrl_succeeds() = runTest {
        val service = Sep31Service("https://anchor.example.org")
        assertEquals("https://anchor.example.org", service.serviceUrl)
    }

    @Test
    fun constructor_trailingSlashUrl_normalized() = runTest {
        var capturedPath = ""
        val client = mockClient(infoResponseJson) { request ->
            capturedPath = request.url.encodedPath
        }
        val service = Sep31Service("https://anchor.example.org/", client)
        service.info(jwt = jwt)
        // Must not produce a double-slash path like //info.
        assertEquals("/info", capturedPath)
    }

    @Test
    fun constructor_httpLocalhost_succeeds() = runTest {
        // Carve-out exists to support local Anchor Platform integration.
        val service = Sep31Service("http://localhost:8080/sep31")
        assertEquals("http://localhost:8080/sep31", service.serviceUrl)
    }

    @Test
    fun constructor_httpLocalhostNoPort_succeeds() = runTest {
        val service = Sep31Service("http://localhost/sep31")
        assertEquals("http://localhost/sep31", service.serviceUrl)
    }

    @Test
    fun constructor_httpLoopbackIpv4_succeeds() = runTest {
        val service = Sep31Service("http://127.0.0.1:8080")
        assertEquals("http://127.0.0.1:8080", service.serviceUrl)
    }

    @Test
    fun constructor_httpLoopbackIpv6_succeeds() = runTest {
        val service = Sep31Service("http://[::1]:8080/sep31")
        assertEquals("http://[::1]:8080/sep31", service.serviceUrl)
    }

    @Test
    fun constructor_httpUppercaseLocalhost_succeeds() = runTest {
        // Host comparison must be case-insensitive per RFC 3986.
        val service = Sep31Service("http://LOCALHOST:8080")
        assertEquals("http://LOCALHOST:8080", service.serviceUrl)
    }

    @Test
    fun constructor_httpLocalhostLookalikeHost_throwsIllegalArgumentException() = runTest {
        // The authority "localhost.evil.com" must not match the carve-out — only the
        // three exact loopback tokens are accepted.
        assertFailsWith<IllegalArgumentException> {
            Sep31Service("http://localhost.evil.com")
        }
    }

    @Test
    fun constructor_httpUserInfoAtLocalhost_throwsIllegalArgumentException() = runTest {
        // Userinfo in front of localhost (e.g., "user@localhost") would change the
        // authority parsed by some clients to "localhost" while keeping userinfo
        // attached. The strict string check rejects every authority not equal to one
        // of the three loopback tokens.
        assertFailsWith<IllegalArgumentException> {
            Sep31Service("http://user@localhost")
        }
    }

    @Test
    fun constructor_httpsLookalikeScheme_throwsIllegalArgumentException() = runTest {
        // Uppercase scheme must be rejected; comparison is case-sensitive on scheme.
        assertFailsWith<IllegalArgumentException> {
            Sep31Service("HTTP://localhost")
        }
    }

    @Test
    fun constructor_loopbackIpv4Lookalike_throwsIllegalArgumentException() = runTest {
        // "127.0.0.1.evil.com" must not match — the carve-out is exact-string only.
        assertFailsWith<IllegalArgumentException> {
            Sep31Service("http://127.0.0.1.evil.com")
        }
    }

    @Test
    fun fromDomain_missingDirectPaymentServer_throwsSep31ConfigurationException() = runTest {
        // Minimal stellar.toml without DIRECT_PAYMENT_SERVER.
        val tomlContent = "NETWORK_PASSPHRASE=\"Test SDF Network ; September 2015\"\n"
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains(".well-known/stellar.toml")) {
                respond(
                    content = tomlContent,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
            } else {
                respond(content = "{}", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val tomlClient = HttpClient(engine)

        val ex = assertFailsWith<Sep31ConfigurationException> {
            Sep31Service.fromDomain("anchor.example.org", tomlClient)
        }
        assertNotNull(ex.message)
    }

    @Test
    fun fromDomain_tomlAdvertisesHttpUrl_throwsSep31ConfigurationException() = runTest {
        val tomlContent = "DIRECT_PAYMENT_SERVER=\"http://payments.example.org\"\n"
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains(".well-known/stellar.toml")) {
                respond(
                    content = tomlContent,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
            } else {
                respond(content = "{}", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val tomlClient = HttpClient(engine)

        val ex = assertFailsWith<Sep31ConfigurationException> {
            Sep31Service.fromDomain("anchor.example.org", tomlClient)
        }
        assertNotNull(ex.message)
    }

    @Test
    fun fromDomain_tomlAdvertisesHttpLocalhost_succeeds() = runTest {
        // The loopback carve-out applies to URLs discovered via stellar.toml too,
        // so a local Anchor Platform with TOML pointing at http://localhost works
        // end-to-end without a TLS proxy.
        val tomlContent = "DIRECT_PAYMENT_SERVER=\"http://localhost:8080/sep31\"\n"
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains(".well-known/stellar.toml")) {
                respond(
                    content = tomlContent,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
            } else {
                respond(content = "{}", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val tomlClient = HttpClient(engine)

        val service = Sep31Service.fromDomain("anchor.example.org", tomlClient)
        assertEquals("http://localhost:8080/sep31", service.serviceUrl)
    }

    @Test
    fun fromDomain_localhostDomain_fetchesTomlOverHttp_andConstructsService() = runTest {
        // End-to-end loopback story: caller passes a loopback domain to fromDomain,
        // the TOML fetch goes over http://, and the resolved service URL also uses
        // http://. Mock captures the TOML fetch URL so the test asserts both the
        // scheme used for discovery and the final service URL.
        var tomlFetchScheme: io.ktor.http.URLProtocol? = null
        val tomlContent = "DIRECT_PAYMENT_SERVER=\"http://localhost:8080/sep31\"\n"
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains(".well-known/stellar.toml")) {
                tomlFetchScheme = request.url.protocol
                respond(
                    content = tomlContent,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
            } else {
                respond(content = "{}", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val tomlClient = HttpClient(engine)

        val service = Sep31Service.fromDomain("localhost:8080", tomlClient)
        assertEquals(io.ktor.http.URLProtocol.HTTP, tomlFetchScheme)
        assertEquals("http://localhost:8080/sep31", service.serviceUrl)
    }

    @Test
    fun fromDomain_invalidDomain_throwsSep31ConfigurationException() = runTest {
        val ex = assertFailsWith<Sep31ConfigurationException> {
            Sep31Service.fromDomain("anchor example.org/path?q=1")
        }
        assertNotNull(ex.message)
    }

    @Test
    fun fromDomain_validToml_returnsServiceWithDirectPaymentServer() = runTest {
        val tomlContent = "DIRECT_PAYMENT_SERVER=\"https://payments.example.org\"\n"
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains(".well-known/stellar.toml")) {
                respond(
                    content = tomlContent,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
            } else {
                respond(content = infoResponseJson, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val tomlClient = HttpClient(engine)

        val service = Sep31Service.fromDomain("anchor.example.org", tomlClient)
        assertEquals("https://payments.example.org", service.serviceUrl)
    }

    // ==================== Path-segment validation ====================

    @Test
    fun getTransaction_idWithSlash_throwsIllegalArgumentException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("{}", statusCode = HttpStatusCode.OK))
        assertFailsWith<IllegalArgumentException> {
            service.getTransaction("abc/def", jwt)
        }
    }

    @Test
    fun getTransaction_idWithPercentEncodedSlash_throwsIllegalArgumentException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("{}", statusCode = HttpStatusCode.OK))
        // %2F decodes to '/'; the decode-then-validate rule must catch this.
        assertFailsWith<IllegalArgumentException> {
            service.getTransaction("abc%2Fdef", jwt)
        }
    }

    @Test
    fun getTransaction_idWithDotDot_throwsIllegalArgumentException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("{}", statusCode = HttpStatusCode.OK))
        assertFailsWith<IllegalArgumentException> {
            service.getTransaction("..", jwt)
        }
    }

    @Test
    fun getTransaction_idWithControlChar_throwsIllegalArgumentException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("{}", statusCode = HttpStatusCode.OK))
        assertFailsWith<IllegalArgumentException> {
            service.getTransaction("abc def", jwt)
        }
    }

    @Test
    fun getTransaction_emptyId_throwsIllegalArgumentException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("{}", statusCode = HttpStatusCode.OK))
        assertFailsWith<IllegalArgumentException> {
            service.getTransaction("", jwt)
        }
    }

    @Test
    fun putTransactionCallback_httpCallbackUrl_throwsIllegalArgumentException() = runTest {
        val service = Sep31Service(serviceUrl, mockNoContentClient())
        assertFailsWith<IllegalArgumentException> {
            service.putTransactionCallback(txId, "http://wallet.example.org/cb", jwt)
        }
    }

    @Test
    fun putTransactionCallback_httpLoopbackCallbackUrl_succeeds() = runTest {
        // Local Anchor Platform integration: the callback URL points at the wallet
        // running on the same dev box. The loopback carve-out lets this work
        // without TLS termination on the dev wallet.
        val service = Sep31Service(serviceUrl, mockNoContentClient())
        service.putTransactionCallback(txId, "http://localhost:9000/cb", jwt)
    }

    @Test
    fun putTransactionCallback_httpLocalhostLookalikeCallbackUrl_throwsIllegalArgumentException() = runTest {
        // Same lookalike defense as the constructor: only the exact loopback hosts pass.
        val service = Sep31Service(serviceUrl, mockNoContentClient())
        assertFailsWith<IllegalArgumentException> {
            service.putTransactionCallback(txId, "http://localhost.evil.com/cb", jwt)
        }
    }

    // ==================== GET /info ====================

    @Test
    fun info_withJwt_attachesAuthorizationHeader() = runTest {
        var capturedAuth: String? = null
        val client = mockClient(infoResponseJson) { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
        }
        val service = Sep31Service(serviceUrl, client)
        service.info(jwt = jwt)

        assertEquals("Bearer $jwt", capturedAuth)
    }

    @Test
    fun info_withLang_appendsQueryParameter_assertsRequestUrl() = runTest {
        var capturedLang: String? = null
        val client = mockClient(infoResponseJson) { request ->
            capturedLang = request.url.parameters["lang"]
        }
        val service = Sep31Service(serviceUrl, client)
        service.info(jwt = jwt, lang = "fr")

        assertEquals("fr", capturedLang)
    }

    @Test
    fun info_200_parsesResponse() = runTest {
        val service = Sep31Service(serviceUrl, mockClient(infoResponseJson))
        val response = service.info(jwt = jwt)

        assertEquals(1, response.receiveAssets.size)
        val usdc = response.receiveAssets["USDC"]
        assertNotNull(usdc)
        assertEquals(0.1, usdc.minAmount)
        assertEquals(1000.0, usdc.maxAmount)
    }

    @Test
    fun info_400_throwsSep31BadRequestException_withStatusCode400() = runTest {
        val service = Sep31Service(serviceUrl, mockClient(genericErrorJson, statusCode = HttpStatusCode.BadRequest))
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.info(jwt = jwt)
        }
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun info_401_throwsSep31UnauthorizedException_withStatusCode401() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"unauthorized"}""", statusCode = HttpStatusCode.Unauthorized))
        val ex = assertFailsWith<Sep31UnauthorizedException> {
            service.info(jwt = jwt)
        }
        assertEquals(401, ex.statusCode)
    }

    @Test
    fun info_403_throwsSep31ForbiddenException_withStatusCode403() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"forbidden"}""", statusCode = HttpStatusCode.Forbidden))
        val ex = assertFailsWith<Sep31ForbiddenException> {
            service.info(jwt = jwt)
        }
        assertEquals(403, ex.statusCode)
    }

    @Test
    fun info_500_throwsSep31UnknownResponseException_withStatusCode500AndPopulatedBody() = runTest {
        val body = """{"error":"Internal server error"}"""
        val service = Sep31Service(serviceUrl, mockClient(body, statusCode = HttpStatusCode.InternalServerError))
        val ex = assertFailsWith<Sep31UnknownResponseException> {
            service.info(jwt = jwt)
        }
        assertEquals(500, ex.statusCode)
        assertTrue(ex.responseBody.isNotEmpty())
    }

    // ==================== POST /transactions ====================

    @Test
    fun postTransactions_200_parsesResponse_assertsIdPresentAndOptionalStellarFieldsCanBeNull() = runTest {
        val minimalResponse = """{"id":"11111111-1111-1111-1111-111111111111"}"""
        val service = Sep31Service(serviceUrl, mockClient(minimalResponse))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        val response = service.postTransactions(request, jwt)

        assertEquals("11111111-1111-1111-1111-111111111111", response.id)
        assertNull(response.stellarAccountId)
        assertNull(response.stellarMemo)
    }

    @Test
    fun postTransactions_201_parsesResponse_assertsIdPresentAndOptionalStellarFieldsCanBeNull() = runTest {
        val service = Sep31Service(serviceUrl, mockClient(postTransactionsResponseJson, statusCode = HttpStatusCode.Created))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        val response = service.postTransactions(request, jwt)

        assertEquals("11111111-1111-1111-1111-111111111111", response.id)
        assertNotNull(response.stellarAccountId)
    }

    @Test
    fun postTransactions_400CustomerInfoNeeded_throwsTypedException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient(customerInfoNeededJson, statusCode = HttpStatusCode.BadRequest))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        val ex = assertFailsWith<Sep31CustomerInfoNeededException> {
            service.postTransactions(request, jwt)
        }
        assertEquals("sep31-sender", ex.type)
    }

    @Test
    fun postTransactions_400CustomerInfoNeededNoType_throwsWithNullType() = runTest {
        val service = Sep31Service(serviceUrl, mockClient(customerInfoNeededNoTypeJson, statusCode = HttpStatusCode.BadRequest))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        val ex = assertFailsWith<Sep31CustomerInfoNeededException> {
            service.postTransactions(request, jwt)
        }
        assertNull(ex.type)
    }

    @Test
    fun postTransactions_400TransactionInfoNeeded_throwsTypedException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient(transactionInfoNeededJson, statusCode = HttpStatusCode.BadRequest))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        @Suppress("DEPRECATION")
        val ex = assertFailsWith<Sep31TransactionInfoNeededException> {
            service.postTransactions(request, jwt)
        }
        val fields = ex.fields
        assertNotNull(fields)
        assertNoJsonElementLeaves(fields)
    }

    @Test
    fun postTransactions_400GenericError_throwsSep31BadRequestException_withStatusCode400() = runTest {
        val service = Sep31Service(serviceUrl, mockClient(genericErrorJson, statusCode = HttpStatusCode.BadRequest))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.postTransactions(request, jwt)
        }
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun postTransactions_400ErrorWithControlChars_messageSanitized() = runTest {
        // Anchor returns an error string containing control chars that must be stripped.
        val errorBody = """{"error":"Bad inputdata"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.BadRequest))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.postTransactions(request, jwt)
        }
        val msg = ex.message ?: ""
        // Control chars 0x00 and 0x01 must not appear in the message.
        assertTrue(msg.none { it.code in 0x00..0x1F && it.code != 0x09 },
            "message must not contain control characters")
    }

    @Test
    fun postTransactions_400Error1500CharsLong_messageTruncatedTo1024PlusContext() = runTest {
        // Generate a 1500-char error field; after sanitization, the message content portion
        // must be at most 1024 chars.
        val longError = "x".repeat(1500)
        val errorBody = """{"error":"$longError"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.BadRequest))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.postTransactions(request, jwt)
        }
        // The sanitized anchor string alone must be <= 1024. The full exception message
        // may include SDK boilerplate so we only bound the total sensibly.
        val msg = ex.message ?: ""
        assertTrue(msg.length <= 1024 + 200,
            "message length ${msg.length} exceeds expected bound")
    }

    @Test
    fun postTransactions_401_throwsSep31UnauthorizedException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"unauthorized"}""", statusCode = HttpStatusCode.Unauthorized))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        val ex = assertFailsWith<Sep31UnauthorizedException> {
            service.postTransactions(request, jwt)
        }
        assertEquals(401, ex.statusCode)
    }

    @Test
    fun postTransactions_403_throwsSep31ForbiddenException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"forbidden"}""", statusCode = HttpStatusCode.Forbidden))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        val ex = assertFailsWith<Sep31ForbiddenException> {
            service.postTransactions(request, jwt)
        }
        assertEquals(403, ex.statusCode)
    }

    @Test
    fun postTransactions_500_throwsSep31UnknownResponseException_withStatusCodeAndBody() = runTest {
        val body = """{"error":"Internal server error"}"""
        val service = Sep31Service(serviceUrl, mockClient(body, statusCode = HttpStatusCode.InternalServerError))
        val request = Sep31PostTransactionsRequest(amount = 100.0, assetCode = "USDC", fundingMethod = "SWIFT")
        val ex = assertFailsWith<Sep31UnknownResponseException> {
            service.postTransactions(request, jwt)
        }
        assertEquals(500, ex.statusCode)
        assertTrue(ex.responseBody.isNotEmpty())
    }

    @Test
    fun postTransactions_sendsAuthorizationHeader() = runTest {
        var capturedAuth: String? = null
        val client = mockClient(postTransactionsResponseJson) { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
        }
        val service = Sep31Service(serviceUrl, client)
        service.postTransactions(Sep31PostTransactionsRequest(100.0, "USDC", "SWIFT"), jwt)

        assertEquals("Bearer $jwt", capturedAuth)
    }

    @Test
    fun postTransactions_sendsCustomHeaders() = runTest {
        var capturedCustomHeader: String? = null
        val client = mockClient(postTransactionsResponseJson) { request ->
            capturedCustomHeader = request.headers["X-Custom-Header"]
        }
        val service = Sep31Service(
            serviceUrl = serviceUrl,
            httpClient = client,
            httpRequestHeaders = mapOf("X-Custom-Header" to "custom-value"),
        )
        service.postTransactions(Sep31PostTransactionsRequest(100.0, "USDC", "SWIFT"), jwt)

        assertEquals("custom-value", capturedCustomHeader)
    }

    @Test
    fun postTransactions_bodyMatchesToJson() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = (request.body as? TextContent)?.text ?: ""
            respond(
                content = postTransactionsResponseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep31Service(serviceUrl, client)
        val request = Sep31PostTransactionsRequest(
            amount = 100.0,
            assetCode = "USDC",
            fundingMethod = "SWIFT",
            senderId = "11111111-1111-1111-1111-111111111111",
            receiverId = "22222222-2222-2222-2222-222222222222",
        )
        service.postTransactions(request, jwt)

        val body = capturedBody ?: ""
        assertTrue(body.contains("\"amount\""), "body must contain 'amount'")
        assertTrue(body.contains("\"asset_code\""), "body must contain 'asset_code'")
        assertTrue(body.contains("USDC"), "body must include asset code value")
        assertTrue(body.contains("\"funding_method\""), "body must contain required 'funding_method'")
    }

    @Test
    fun postTransactions_responseBodyContainsJwtPatternMidString_exceptionMessageRedactsJwt() = runTest {
        // Production defense (defense-in-depth): the SEP-31 SDK's sanitizeAnchorString
        // helper replaces any substring matching the JWT shape (eyJ<b64url>.<b64url>.<b64url>)
        // with the literal token "<redacted-jwt>". Anchors should never echo bearer tokens
        // back to clients, but if a buggy anchor does, the SDK MUST NOT propagate the JWT
        // into application logs via Sep31*Exception.message.
        val realJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.abcdef1234567890"
        val errorBody = """{"error":"invalid token: $realJwt is not valid"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.BadRequest))
        val request = Sep31PostTransactionsRequest(100.0, "USDC", "SWIFT")
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.postTransactions(request, jwt)
        }
        val msg = ex.message ?: ""
        assertTrue(
            !jwtPattern.containsMatchIn(msg),
            "exception message must not contain a JWT-shaped substring; message was: $msg",
        )
        // The literal raw JWT string must not survive verbatim either; the regex above
        // covers this, but assert the explicit literal for extra clarity.
        assertTrue(
            !msg.contains(realJwt),
            "exception message must not contain the verbatim JWT; message was: $msg",
        )
    }

    @Test
    fun postTransactions_responseBodyJwtAtStartOfErrorString_exceptionMessageRedactsJwt() = runTest {
        // Same defense-in-depth contract as the mid-string variant, but with the JWT placed
        // at the very start of the anchor error field. Ensures the regex replacement is not
        // anchored to a leading delimiter.
        val realJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1Njc4In0.signature_part_1234"
        val errorBody = """{"error":"$realJwt: token rejected"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.BadRequest))
        val request = Sep31PostTransactionsRequest(100.0, "USDC", "SWIFT")
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.postTransactions(request, jwt)
        }
        val msg = ex.message ?: ""
        assertTrue(
            !jwtPattern.containsMatchIn(msg),
            "exception message must not contain a JWT-shaped substring; message was: $msg",
        )
        assertTrue(
            !msg.contains(realJwt),
            "exception message must not contain the verbatim JWT; message was: $msg",
        )
    }

    @Test
    fun unknownResponse_responseBodyContainsJwtPattern_responseBodyFieldRedactsJwt() = runTest {
        // The public Sep31UnknownResponseException.responseBody field receives the sanitized
        // and truncated anchor body. JWT-shape redaction MUST apply there too: callers commonly
        // log the responseBody field, and an unredacted JWT in the response body is the same
        // log-poisoning vector as one in the message. Sep31UnknownResponseException is the only
        // exception that exposes the raw anchor body, so verifying redaction here closes the
        // remaining gap.
        val realJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5OTk5In0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        val errorBody = """{"error":"upstream failure with token $realJwt embedded"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.InternalServerError))
        val ex = assertFailsWith<Sep31UnknownResponseException> {
            service.info(jwt = jwt)
        }
        assertEquals(500, ex.statusCode)
        val responseBody = ex.responseBody
        assertTrue(
            !jwtPattern.containsMatchIn(responseBody),
            "responseBody must not contain a JWT-shaped substring; was: $responseBody",
        )
        assertTrue(
            !responseBody.contains(realJwt),
            "responseBody must not contain the verbatim JWT; was: $responseBody",
        )
    }

    // ==================== rawResponseBody (debug escape hatch) ====================

    @Test
    fun rawResponseBody_badRequest_preservesJwtForDebugging() = runTest {
        // The rawResponseBody field intentionally preserves JWT-shaped substrings so a
        // developer debugging an anchor that echoes tokens can see the actual token.
        // The message field is still redacted; only rawResponseBody preserves the JWT.
        val realJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.abcdef1234567890"
        val errorBody = """{"error":"invalid token: $realJwt is not valid"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.BadRequest))
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.postTransactions(Sep31PostTransactionsRequest(100.0, "USDC", "SWIFT"), jwt)
        }
        val raw = ex.rawResponseBody
        assertNotNull(raw, "rawResponseBody must be populated when a body was read")
        assertTrue(raw.contains(realJwt), "rawResponseBody must preserve the verbatim JWT; was: $raw")
        // Sanity check on the message-vs-raw split.
        assertTrue(!ex.message!!.contains(realJwt), "message must still be redacted")
    }

    @Test
    fun rawResponseBody_unauthorized_preservesJwt() = runTest {
        val realJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1Njc4In0.signature_part_1234"
        val errorBody = """{"error":"unauthorized: $realJwt has expired"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.Unauthorized))
        val ex = assertFailsWith<Sep31UnauthorizedException> { service.info(jwt = jwt) }
        val raw = ex.rawResponseBody
        assertNotNull(raw)
        assertTrue(raw.contains(realJwt), "rawResponseBody must preserve the verbatim JWT")
    }

    @Test
    fun rawResponseBody_forbidden_preservesJwt() = runTest {
        val realJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI5OTk5In0.signature9876"
        val errorBody = """{"error":"forbidden: $realJwt missing scope"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.Forbidden))
        val ex = assertFailsWith<Sep31ForbiddenException> { service.info(jwt = jwt) }
        val raw = ex.rawResponseBody
        assertNotNull(raw)
        assertTrue(raw.contains(realJwt), "rawResponseBody must preserve the verbatim JWT")
    }

    @Test
    fun rawResponseBody_unknownResponse_preservesJwt_andResponseBodyStillRedacts() = runTest {
        val realJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI3Nzc3In0.signature7777unique"
        val errorBody = """{"error":"upstream: token $realJwt rejected"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.InternalServerError))
        val ex = assertFailsWith<Sep31UnknownResponseException> { service.info(jwt = jwt) }
        // The existing responseBody field is sanitized (redacted) — existing contract.
        assertTrue(!ex.responseBody.contains(realJwt), "responseBody must remain redacted")
        assertTrue(!jwtPattern.containsMatchIn(ex.responseBody), "responseBody must remain redacted (regex check)")
        // The new rawResponseBody field preserves the JWT for debugging.
        val raw = ex.rawResponseBody
        assertNotNull(raw)
        assertTrue(raw.contains(realJwt), "rawResponseBody must preserve the verbatim JWT")
    }

    @Test
    fun rawResponseBody_customerInfoNeeded_preservesJwt() = runTest {
        val realJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMTExIn0.signature1111kyc"
        val errorBody = """{"error":"customer_info_needed","type":"sep31-sender","note":"$realJwt"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.BadRequest))
        val ex = assertFailsWith<Sep31CustomerInfoNeededException> {
            service.postTransactions(Sep31PostTransactionsRequest(100.0, "USDC", "SWIFT"), jwt)
        }
        val raw = ex.rawResponseBody
        assertNotNull(raw)
        assertTrue(raw.contains(realJwt), "rawResponseBody must preserve the verbatim JWT")
        // type field still surfaces sanitized through message — sanity check.
        assertEquals("sep31-sender", ex.type)
    }

    @Test
    fun rawResponseBody_transactionNotFound_populatedOn404() = runTest {
        // The 404 path now reads the body for rawResponseBody (it does not interpolate
        // anchor content into the message). Verify both the body capture and that
        // the message remains the SDK-supplied fixed string.
        val anchorBody = """{"error":"transaction was purged after 30 days"}"""
        val service = Sep31Service(serviceUrl, mockClient(anchorBody, statusCode = HttpStatusCode.NotFound))
        val ex = assertFailsWith<Sep31TransactionNotFoundException> {
            service.getTransaction("some-id", jwt)
        }
        assertEquals(404, ex.statusCode)
        assertTrue(
            ex.message!!.contains("transaction not found for id"),
            "message must remain the SDK-fixed string",
        )
        assertNotNull(ex.rawResponseBody, "rawResponseBody must be populated on 404")
        assertTrue(
            ex.rawResponseBody!!.contains("purged after 30 days"),
            "rawResponseBody must contain the anchor's 404 body",
        )
    }

    @Test
    fun rawResponseBody_callbackNotSupported_populatedOn404() = runTest {
        val anchorBody = """{"error":"callbacks disabled for this account"}"""
        val service = Sep31Service(serviceUrl, mockClient(anchorBody, statusCode = HttpStatusCode.NotFound))
        val ex = assertFailsWith<Sep31TransactionCallbackNotSupportedException> {
            service.putTransactionCallback("some-id", "https://wallet.example.org/cb", jwt)
        }
        assertEquals(404, ex.statusCode)
        assertNotNull(ex.rawResponseBody)
        assertTrue(ex.rawResponseBody!!.contains("callbacks disabled"))
    }

    @Test
    fun rawResponseBody_stillStripsControlCharacters() = runTest {
        // Defense in depth: even the "raw" body must have control characters stripped
        // so a malicious anchor cannot inject terminal escapes or NUL bytes into
        // debugger output via the field.
        val realJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature_for_control_char_test"
        val controlChar = "" // ESC — would inject ANSI escapes into a terminal
        val errorBody = """{"error":"token $realJwt$controlChar[31mmalicious[0m"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.BadRequest))
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.postTransactions(Sep31PostTransactionsRequest(100.0, "USDC", "SWIFT"), jwt)
        }
        val raw = ex.rawResponseBody!!
        assertTrue(raw.contains(realJwt), "rawResponseBody must preserve the JWT")
        assertTrue(!raw.contains(controlChar), "rawResponseBody must strip control chars even with redactJwts=false")
    }

    @Test
    fun rawResponseBody_stillCappedAt1024Chars() = runTest {
        // Defense in depth: even the "raw" body must be size-capped so a malicious
        // anchor cannot spam application debug logs by returning megabytes of text
        // when a developer is inspecting rawResponseBody.
        val padding = "x".repeat(2000)
        val errorBody = """{"error":"oversized: $padding"}"""
        val service = Sep31Service(serviceUrl, mockClient(errorBody, statusCode = HttpStatusCode.BadRequest))
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.postTransactions(Sep31PostTransactionsRequest(100.0, "USDC", "SWIFT"), jwt)
        }
        val raw = ex.rawResponseBody!!
        assertTrue(raw.length <= 1024, "rawResponseBody must be capped at 1024 chars; was ${raw.length}")
    }

    // ==================== GET /transactions/:id ====================

    @Test
    fun getTransaction_200_parsesResponse() = runTest {
        val service = Sep31Service(serviceUrl, mockClient(pendingSenderTransactionJson))
        val response = service.getTransaction(txId, jwt)

        assertEquals("82fhs729f63dh0v4", response.id)
        assertEquals("pending_sender", response.status)
    }

    @Test
    fun getTransaction_400_throwsSep31BadRequestException_withStatusCode400() = runTest {
        val service = Sep31Service(serviceUrl, mockClient(genericErrorJson, statusCode = HttpStatusCode.BadRequest))
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.getTransaction(txId, jwt)
        }
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun getTransaction_401_throwsSep31UnauthorizedException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"unauthorized"}""", statusCode = HttpStatusCode.Unauthorized))
        val ex = assertFailsWith<Sep31UnauthorizedException> {
            service.getTransaction(txId, jwt)
        }
        assertEquals(401, ex.statusCode)
    }

    @Test
    fun getTransaction_403_throwsSep31ForbiddenException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"forbidden"}""", statusCode = HttpStatusCode.Forbidden))
        val ex = assertFailsWith<Sep31ForbiddenException> {
            service.getTransaction(txId, jwt)
        }
        assertEquals(403, ex.statusCode)
    }

    @Test
    fun getTransaction_404_throwsSep31TransactionNotFoundException_withStatusCode404() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"not found"}""", statusCode = HttpStatusCode.NotFound))
        val ex = assertFailsWith<Sep31TransactionNotFoundException> {
            service.getTransaction(txId, jwt)
        }
        assertEquals(404, ex.statusCode)
    }

    @Test
    fun getTransaction_500_throwsSep31UnknownResponseException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"server error"}""", statusCode = HttpStatusCode.InternalServerError))
        val ex = assertFailsWith<Sep31UnknownResponseException> {
            service.getTransaction(txId, jwt)
        }
        assertEquals(500, ex.statusCode)
    }

    // ==================== PUT /transactions/:id/callback ====================

    @Test
    fun putTransactionCallback_204_succeeds() = runTest {
        val service = Sep31Service(serviceUrl, mockNoContentClient())
        // Must complete without throwing.
        service.putTransactionCallback(txId, callbackUrl, jwt)
    }

    @Test
    fun putTransactionCallback_sendsUrlInRequestBody() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = (request.body as? TextContent)?.text ?: ""
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
                headers = headersOf(),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep31Service(serviceUrl, client)
        service.putTransactionCallback(txId, callbackUrl, jwt)

        val body = capturedBody ?: ""
        assertTrue(body.contains("\"url\""), "body must have 'url' key")
        assertTrue(body.contains(callbackUrl), "body must include the callback URL")
    }

    @Test
    fun putTransactionCallback_400_throwsSep31BadRequestException_withStatusCode400() = runTest {
        val service = Sep31Service(serviceUrl, mockClient(genericErrorJson, statusCode = HttpStatusCode.BadRequest))
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.putTransactionCallback(txId, callbackUrl, jwt)
        }
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun putTransactionCallback_401_throwsSep31UnauthorizedException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"unauthorized"}""", statusCode = HttpStatusCode.Unauthorized))
        val ex = assertFailsWith<Sep31UnauthorizedException> {
            service.putTransactionCallback(txId, callbackUrl, jwt)
        }
        assertEquals(401, ex.statusCode)
    }

    @Test
    fun putTransactionCallback_403_throwsSep31ForbiddenException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"forbidden"}""", statusCode = HttpStatusCode.Forbidden))
        val ex = assertFailsWith<Sep31ForbiddenException> {
            service.putTransactionCallback(txId, callbackUrl, jwt)
        }
        assertEquals(403, ex.statusCode)
    }

    @Test
    fun putTransactionCallback_404_throwsSep31TransactionCallbackNotSupportedException_withStatusCode404() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"not supported"}""", statusCode = HttpStatusCode.NotFound))
        val ex = assertFailsWith<Sep31TransactionCallbackNotSupportedException> {
            service.putTransactionCallback(txId, callbackUrl, jwt)
        }
        assertEquals(404, ex.statusCode)
    }

    @Test
    fun putTransactionCallback_500_throwsSep31UnknownResponseException() = runTest {
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"server error"}""", statusCode = HttpStatusCode.InternalServerError))
        val ex = assertFailsWith<Sep31UnknownResponseException> {
            service.putTransactionCallback(txId, callbackUrl, jwt)
        }
        assertEquals(500, ex.statusCode)
    }

    // ==================== PATCH /transactions/:id ====================

    @Test
    fun patchTransaction_200_returnsSep31TransactionResponse() = runTest {
        @Suppress("DEPRECATION")
        val service = Sep31Service(serviceUrl, mockClient(pendingSenderTransactionJson))
        @Suppress("DEPRECATION")
        val result = service.patchTransaction(txId, mapOf("transaction" to mapOf("note" to "updated")), jwt)

        assertIs<Sep31TransactionResponse>(result)
        assertEquals("82fhs729f63dh0v4", result.id)
    }

    @Test
    fun patchTransaction_badFields_400_throwsSep31BadRequestException_withStatusCode400() = runTest {
        // A 400 from PATCH must NEVER route to Sep31CustomerInfoNeededException.
        @Suppress("DEPRECATION")
        val service = Sep31Service(serviceUrl, mockClient(genericErrorJson, statusCode = HttpStatusCode.BadRequest))
        @Suppress("DEPRECATION")
        val ex = assertFailsWith<Sep31BadRequestException> {
            service.patchTransaction(txId, emptyMap(), jwt)
        }
        assertEquals(400, ex.statusCode)
    }

    @Test
    fun patchTransaction_401_throwsSep31UnauthorizedException() = runTest {
        @Suppress("DEPRECATION")
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"unauthorized"}""", statusCode = HttpStatusCode.Unauthorized))
        @Suppress("DEPRECATION")
        val ex = assertFailsWith<Sep31UnauthorizedException> {
            service.patchTransaction(txId, emptyMap(), jwt)
        }
        assertEquals(401, ex.statusCode)
    }

    @Test
    fun patchTransaction_403_throwsSep31ForbiddenException() = runTest {
        @Suppress("DEPRECATION")
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"forbidden"}""", statusCode = HttpStatusCode.Forbidden))
        @Suppress("DEPRECATION")
        val ex = assertFailsWith<Sep31ForbiddenException> {
            service.patchTransaction(txId, emptyMap(), jwt)
        }
        assertEquals(403, ex.statusCode)
    }

    @Test
    fun patchTransaction_404_throwsSep31TransactionNotFoundException() = runTest {
        @Suppress("DEPRECATION")
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"not found"}""", statusCode = HttpStatusCode.NotFound))
        @Suppress("DEPRECATION")
        val ex = assertFailsWith<Sep31TransactionNotFoundException> {
            service.patchTransaction(txId, emptyMap(), jwt)
        }
        assertEquals(404, ex.statusCode)
    }

    @Test
    fun patchTransaction_500_throwsSep31UnknownResponseException() = runTest {
        @Suppress("DEPRECATION")
        val service = Sep31Service(serviceUrl, mockClient("""{"error":"server error"}""", statusCode = HttpStatusCode.InternalServerError))
        @Suppress("DEPRECATION")
        val ex = assertFailsWith<Sep31UnknownResponseException> {
            service.patchTransaction(txId, emptyMap(), jwt)
        }
        assertEquals(500, ex.statusCode)
    }

    @Test
    fun patchTransaction_sendsFieldsWrappedInFieldsKey() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = (request.body as? TextContent)?.text ?: ""
            respond(
                content = pendingSenderTransactionJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        @Suppress("DEPRECATION")
        val service = Sep31Service(serviceUrl, client)
        val fields = mapOf("transaction" to mapOf("receiver_account_number" to "12345"))
        @Suppress("DEPRECATION")
        service.patchTransaction(txId, fields, jwt)

        val body = capturedBody ?: ""
        assertTrue(body.contains("\"fields\""), "PATCH body must wrap payload in 'fields' key")
    }

    // ==================== Cross-cutting ====================

    @Test
    fun service_redirectIsTreatedAsUnknownResponse_dispatchValidation() = runTest {
        // SCOPE: This test validates that when the client passed into Sep31Service is configured
        // with followRedirects=false, a 3xx response from the anchor surfaces as
        // Sep31UnknownResponseException carrying the original 3xx status code. The
        // followRedirects=false setting matches what Sep31Service applies to its internally-
        // built client at the call site `withHttpClient` in Sep31Service.kt (the production
        // builder is `HttpClient(<engine>) { followRedirects = false; ... }`).
        //
        // LIMITATION: This unit test cannot directly observe the followRedirects=false setting
        // on the SDK's internally-built client because Ktor exposes no introspection API for
        // the live client config and the SDK does not surface the config object. The production
        // guarantee is enforced structurally by the source-level `followRedirects = false` line
        // in Sep31Service.kt's `withHttpClient` private helper. End-to-end verification of that
        // line is the responsibility of integration tests (Phase 4) against a live anchor that
        // serves a 3xx — those tests use no injected client.
        val engine = MockEngine { _ ->
            respond(
                content = "Moved",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            followRedirects = false
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep31Service(serviceUrl, client)
        val ex = assertFailsWith<Sep31UnknownResponseException> {
            service.info(jwt = jwt)
        }
        assertEquals(302, ex.statusCode)
    }

    @Test
    fun fromDomain_tomlRedirects_resolvesSuccessfully() = runTest {
        // SEP-31 plan §1.1 rule 14: the followRedirects=false rule applies to Sep31Service's
        // own HTTP calls (info, postTransactions, etc.), NOT to the StellarToml.fromDomain
        // fetch. The TOML client retains its default redirect behavior so that anchors which
        // serve their stellar.toml from a redirected canonical host (a common production setup
        // behind hostname migrations or CDN rewrites) remain reachable.
        //
        // This test models the production redirect chain explicitly:
        //   1. GET https://anchor.example.org/.well-known/stellar.toml -> 301 with Location
        //      header pointing at the canonical host
        //   2. GET <Location URL>                                       -> 200 with TOML content
        // Ktor's default HttpClient has the HttpRedirect plugin installed, which auto-follows
        // 301 responses by issuing the second request through the same MockEngine. The
        // requestCount captures both invocations and the test asserts both fired.
        val tomlContent = "DIRECT_PAYMENT_SERVER=\"https://payments.example.org\"\n"
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            if (requestCount == 1) {
                // First call: 301 redirect to the canonical host. Ktor's HttpRedirect plugin
                // consumes the Location header and issues a follow-up GET.
                respond(
                    content = "",
                    status = HttpStatusCode.MovedPermanently,
                    headers = io.ktor.http.Headers.build {
                        append(HttpHeaders.Location, "https://www.anchor.example.org/.well-known/stellar.toml")
                    },
                )
            } else {
                // Second call: 200 with the TOML payload. The MockEngine is shared for both
                // hops because we never registered a per-host engine.
                respond(
                    content = tomlContent,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain"),
                )
            }
        }
        val tomlClient = HttpClient(engine)
        val service = Sep31Service.fromDomain("anchor.example.org", tomlClient)

        // Both hops must have been observed by the engine; the second hop carries the TOML.
        assertEquals(2, requestCount, "MockEngine must have served the 301 and the 200 hops")
        assertEquals("https://payments.example.org", service.serviceUrl)
    }

    @Test
    fun service_oversizedResponseBodyByContentLength_throwsSep31InvalidResponseException() = runTest {
        // Transaction cap is 256 KB (262144 bytes). Send cap+1 bytes with a matching Content-Length
        // so the service's pre-read check fires before the body channel is consumed.
        val cap = 256 * 1024
        val oversizedBody = ByteArray(cap + 1) { 0x61.toByte() }.decodeToString()
        val engine = MockEngine { _ ->
            respond(
                content = oversizedBody,
                status = HttpStatusCode.OK,
                headers = io.ktor.http.Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                    append(HttpHeaders.ContentLength, (cap + 1).toString())
                },
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep31Service(serviceUrl, client)
        assertFailsWith<Sep31InvalidResponseException> {
            service.getTransaction(txId, jwt)
        }
    }

    @Test
    fun service_oversizedResponseBodyWithoutContentLength_throwsSep31InvalidResponseException() = runTest {
        // Generate a body larger than the transaction response cap (256 KB = 262144 bytes).
        // ByteArray instantiation kept in the test body, not as a compile-time constant.
        val cap = 256 * 1024
        val oversizedBody = ByteArray(cap + 1) { 0x61.toByte() }.decodeToString()
        val engine = MockEngine { _ ->
            respond(
                content = oversizedBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = Sep31Service(serviceUrl, client)
        assertFailsWith<Sep31InvalidResponseException> {
            service.getTransaction(txId, jwt)
        }
    }

    @Test
    fun service_responseContentTypeIsHtml_throwsSep31InvalidResponseException() = runTest {
        val service = Sep31Service(
            serviceUrl,
            mockClient(
                "<html><body>Error</body></html>",
                statusCode = HttpStatusCode.OK,
                contentType = "text/html; charset=utf-8",
            ),
        )
        assertFailsWith<Sep31InvalidResponseException> {
            service.info(jwt = jwt)
        }
    }
}

/**
 * Recursively asserts that no value within [map] (or within any nested map or list) is a
 * [JsonElement] instance. Fails the test at the first offending leaf.
 */
private fun assertNoJsonElementLeaves(map: Map<String, Any?>) {
    for ((key, value) in map) {
        assertNoJsonElementLeavesValue(key, value)
    }
}

private fun assertNoJsonElementLeavesValue(path: String, value: Any?) {
    if (value is kotlinx.serialization.json.JsonElement) {
        kotlin.test.fail("JsonElement found at path '$path': $value")
    }
    @Suppress("UNCHECKED_CAST")
    when (value) {
        is Map<*, *> -> (value as Map<String, Any?>).forEach { (k, v) ->
            assertNoJsonElementLeavesValue("$path.$k", v)
        }
        is List<*> -> value.forEachIndexed { i, v ->
            assertNoJsonElementLeavesValue("$path[$i]", v)
        }
    }
}
