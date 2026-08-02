// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep08

import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.sep.sep08.*
import com.soneso.stellar.sdk.sep.sep08.exceptions.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class Sep08ServiceTest {

    // ========== Mock TOML Responses ==========

    private val regulatedToml = """
        NETWORK_PASSPHRASE="Test SDF Network ; September 2015"
        HORIZON_URL="https://horizon-testnet.stellar.org"

        [[CURRENCIES]]
        code="GOAT"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        regulated=true
        approval_server="https://goat.io/tx_approve"
        approval_criteria="The goat approval server will ensure compliance"

        [[CURRENCIES]]
        code="USD"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        regulated=false
    """.trimIndent()

    private val multiRegulatedToml = """
        NETWORK_PASSPHRASE="Test SDF Network ; September 2015"
        HORIZON_URL="https://horizon-testnet.stellar.org"

        [[CURRENCIES]]
        code="GOAT"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        regulated=true
        approval_server="https://goat.io/tx_approve"
        approval_criteria="The goat approval server will ensure compliance"

        [[CURRENCIES]]
        code="SHEEP"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        regulated=true
        approval_server="https://sheep.io/tx_approve"

        [[CURRENCIES]]
        code="USD"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        regulated=false

        [[CURRENCIES]]
        code="EUR"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
    """.trimIndent()

    private val noRegulatedToml = """
        NETWORK_PASSPHRASE="Test SDF Network ; September 2015"
        HORIZON_URL="https://horizon-testnet.stellar.org"

        [[CURRENCIES]]
        code="USD"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        regulated=false

        [[CURRENCIES]]
        code="EUR"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
    """.trimIndent()

    private val noNetworkToml = """
        HORIZON_URL="https://horizon-testnet.stellar.org"

        [[CURRENCIES]]
        code="GOAT"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        regulated=true
        approval_server="https://goat.io/tx_approve"
    """.trimIndent()

    private val publicNetworkToml = """
        NETWORK_PASSPHRASE="Public Global Stellar Network ; September 2015"

        [[CURRENCIES]]
        code="GOAT"
        issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        regulated=true
        approval_server="https://goat.io/tx_approve"
    """.trimIndent()

    // ========== Mock JSON Responses ==========

    private val successJson = """
        {"status":"success","tx":"AAAAAgAAAAA...","message":"Approved"}
    """.trimIndent()

    private val successNoMessageJson = """
        {"status":"success","tx":"AAAAAgAAAAA..."}
    """.trimIndent()

    private val revisedJson = """
        {"status":"revised","tx":"AAAAAgBBBB...","message":"Added authorization ops"}
    """.trimIndent()

    private val pendingJson = """
        {"status":"pending","timeout":5000,"message":"Checking..."}
    """.trimIndent()

    private val pendingMinimalJson = """
        {"status":"pending"}
    """.trimIndent()

    private val actionRequiredJson = """
        {"status":"action_required","message":"KYC needed","action_url":"https://kyc.io/verify","action_method":"POST","action_fields":["email_address","mobile_number"]}
    """.trimIndent()

    private val actionRequiredGetJson = """
        {"status":"action_required","message":"Please verify","action_url":"https://kyc.io/verify"}
    """.trimIndent()

    private val rejectedJson = """
        {"status":"rejected","error":"Destination blocked"}
    """.trimIndent()

    private val actionDoneJson = """
        {"result":"no_further_action_required"}
    """.trimIndent()

    private val actionNextUrlJson = """
        {"result":"follow_next_url","next_url":"https://kyc.io/step2","message":"Complete step 2"}
    """.trimIndent()

    private val actionNextUrlNoMessageJson = """
        {"result":"follow_next_url","next_url":"https://kyc.io/step2"}
    """.trimIndent()

    // ========== Helper Methods ==========

    private fun createTomlMockClient(
        tomlContent: String
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
                    content = "Not found",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }
        }

        return HttpClient(mockEngine)
    }

    private fun createPostMockClient(
        responseContent: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK,
        expectedPath: String? = null
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            if (expectedPath == null || request.url.toString().contains(expectedPath)) {
                respond(
                    content = responseContent,
                    status = statusCode,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"error": "Not found: ${request.url.encodedPath}"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
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

    private fun createSep08ServiceWithMockClient(
        mockClient: HttpClient,
        regulatedAssets: List<RegulatedAsset> = emptyList(),
        httpRequestHeaders: Map<String, String>? = null
    ): Sep08Service {
        val toml = com.soneso.stellar.sdk.sep.sep01.StellarToml.parse(regulatedToml)
        return Sep08Service(
            tomlData = toml,
            regulatedAssets = regulatedAssets,
            network = Network.TESTNET,
            horizonServer = com.soneso.stellar.sdk.horizon.HorizonServer(
                "https://horizon-testnet.stellar.org"
            ),
            httpClient = mockClient,
            httpRequestHeaders = httpRequestHeaders
        )
    }

    // ========== fromDomain() Tests ==========

    @Test
    fun testFromDomainSuccess() = runTest {
        val mockClient = createTomlMockClient(regulatedToml)

        val service = Sep08Service.fromDomain(
            domain = "goat.io",
            httpClient = mockClient
        )

        assertNotNull(service.tomlData)
        assertEquals(1, service.regulatedAssets.size)

        val asset = service.regulatedAssets[0]
        assertEquals("GOAT", asset.code)
        assertEquals("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP", asset.issuer)
        assertEquals("https://goat.io/tx_approve", asset.approvalServer)
        assertEquals("The goat approval server will ensure compliance", asset.approvalCriteria)
    }

    @Test
    fun testFromDomainFiltersNonRegulatedAssets() = runTest {
        val mockClient = createTomlMockClient(multiRegulatedToml)

        val service = Sep08Service.fromDomain(
            domain = "example.com",
            httpClient = mockClient
        )

        assertEquals(2, service.regulatedAssets.size)

        val codes = service.regulatedAssets.map { it.code }
        assertTrue(codes.contains("GOAT"))
        assertTrue(codes.contains("SHEEP"))
        assertFalse(codes.contains("USD"))
        assertFalse(codes.contains("EUR"))
    }

    @Test
    fun testFromDomainNoRegulatedAssets() = runTest {
        val mockClient = createTomlMockClient(noRegulatedToml)

        val service = Sep08Service.fromDomain(
            domain = "example.com",
            httpClient = mockClient
        )

        assertTrue(service.regulatedAssets.isEmpty())
    }

    @Test
    fun testFromDomainWithExplicitNetwork() = runTest {
        val mockClient = createTomlMockClient(regulatedToml)

        val service = Sep08Service.fromDomain(
            domain = "goat.io",
            network = Network.PUBLIC,
            httpClient = mockClient
        )

        assertNotNull(service)
        assertEquals(1, service.regulatedAssets.size)
    }

    @Test
    fun testFromDomainWithExplicitHorizonUrl() = runTest {
        val mockClient = createTomlMockClient(regulatedToml)

        val service = Sep08Service.fromDomain(
            domain = "goat.io",
            horizonUrl = "https://my-custom-horizon.example.com",
            httpClient = mockClient
        )

        assertNotNull(service)
        assertEquals(1, service.regulatedAssets.size)
    }

    @Test
    fun testFromDomainMissingNetworkPassphrase() = runTest {
        val mockClient = createTomlMockClient(noNetworkToml)

        assertFailsWith<Sep08IncompleteInitDataException> {
            Sep08Service.fromDomain(
                domain = "goat.io",
                httpClient = mockClient
            )
        }
    }

    @Test
    fun testFromDomainWithCustomHeaders() = runTest {
        var customHeaderVerified = false
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/.well-known/stellar.toml")) {
                customHeaderVerified = request.headers["X-Custom-Header"] == "test-value"
                respond(
                    content = regulatedToml,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            } else {
                respond(
                    content = "Not found",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
            }
        }
        val mockClient = HttpClient(mockEngine)

        Sep08Service.fromDomain(
            domain = "goat.io",
            httpClient = mockClient,
            httpRequestHeaders = mapOf("X-Custom-Header" to "test-value")
        )

        assertTrue(customHeaderVerified, "Custom header should be forwarded to TOML fetch")
    }

    @Test
    fun testFromDomainPublicNetworkResolvesDefaultHorizon() = runTest {
        val mockClient = createTomlMockClient(publicNetworkToml)

        val service = Sep08Service.fromDomain(
            domain = "example.com",
            httpClient = mockClient
        )

        assertNotNull(service)
        assertEquals(1, service.regulatedAssets.size)
    }

    @Test
    fun testFromDomainTestnetPassphraseResolvesDefaultHorizon() = runTest {
        val toml = """
            NETWORK_PASSPHRASE="Test SDF Network ; September 2015"

            [[CURRENCIES]]
            code="GOAT"
            issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
            regulated=true
            approval_server="https://goat.io/tx_approve"
        """.trimIndent()

        val service = Sep08Service.fromDomain(
            domain = "goat.io",
            httpClient = createTomlMockClient(toml)
        )

        assertEquals(1, service.regulatedAssets.size)
        assertEquals(
            "Test SDF Network ; September 2015",
            service.tomlData.generalInformation.networkPassphrase
        )
    }

    @Test
    fun testFromDomainFuturenetPassphraseResolvesDefaultHorizon() = runTest {
        val toml = """
            NETWORK_PASSPHRASE="Test SDF Future Network ; October 2022"

            [[CURRENCIES]]
            code="GOAT"
            issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
            regulated=true
            approval_server="https://goat.io/tx_approve"
        """.trimIndent()

        val service = Sep08Service.fromDomain(
            domain = "goat.io",
            httpClient = createTomlMockClient(toml)
        )

        assertEquals(1, service.regulatedAssets.size)
        assertEquals(
            "Test SDF Future Network ; October 2022",
            service.tomlData.generalInformation.networkPassphrase
        )
    }

    @Test
    fun testFromDomainStandaloneNetworkWithoutHorizonUrlFails() = runTest {
        // The standalone network has no publicly known Horizon instance, so the URL
        // cannot be derived and must be supplied by the caller or the toml.
        val toml = """
            NETWORK_PASSPHRASE="Standalone Network ; February 2017"

            [[CURRENCIES]]
            code="GOAT"
            issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
            regulated=true
            approval_server="https://goat.io/tx_approve"
        """.trimIndent()

        val exception = assertFailsWith<Sep08IncompleteInitDataException> {
            Sep08Service.fromDomain(
                domain = "goat.io",
                httpClient = createTomlMockClient(toml)
            )
        }
        assertTrue(
            exception.message!!.contains("Horizon URL could not be determined for domain: goat.io"),
            "Exception should name the failing domain, was: ${exception.message}"
        )
    }

    @Test
    fun testFromDomainCustomNetworkWithoutHorizonUrlFails() = runTest {
        val toml = """
            NETWORK_PASSPHRASE="My Private Stellar Network ; January 2026"

            [[CURRENCIES]]
            code="GOAT"
            issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
            regulated=true
            approval_server="https://goat.io/tx_approve"
        """.trimIndent()

        val exception = assertFailsWith<Sep08IncompleteInitDataException> {
            Sep08Service.fromDomain(
                domain = "private.example.com",
                httpClient = createTomlMockClient(toml)
            )
        }
        assertTrue(
            exception.message!!.contains("Horizon URL could not be determined"),
            "Exception should report the missing Horizon URL, was: ${exception.message}"
        )
    }

    @Test
    fun testFromDomainSandboxNetworkWithHorizonUrlFromToml() = runTest {
        val toml = """
            NETWORK_PASSPHRASE="Local Sandbox Stellar Network ; September 2022"
            HORIZON_URL="http://localhost:8000"

            [[CURRENCIES]]
            code="GOAT"
            issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
            regulated=true
            approval_server="https://goat.io/tx_approve"
        """.trimIndent()

        val service = Sep08Service.fromDomain(
            domain = "goat.io",
            httpClient = createTomlMockClient(toml)
        )

        assertEquals(1, service.regulatedAssets.size)
        assertEquals("http://localhost:8000", service.tomlData.generalInformation.horizonUrl)
    }

    @Test
    fun testFromDomainCustomNetworkWithExplicitHorizonUrl() = runTest {
        val toml = """
            NETWORK_PASSPHRASE="My Private Stellar Network ; January 2026"

            [[CURRENCIES]]
            code="GOAT"
            issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
            regulated=true
            approval_server="https://goat.io/tx_approve"
        """.trimIndent()

        val service = Sep08Service.fromDomain(
            domain = "private.example.com",
            horizonUrl = "https://horizon.private.example.com",
            httpClient = createTomlMockClient(toml)
        )

        assertEquals(1, service.regulatedAssets.size)
        assertEquals(
            "My Private Stellar Network ; January 2026",
            service.tomlData.generalInformation.networkPassphrase
        )
    }

    @Test
    fun testFromDomainTomlWithoutCurrencies() = runTest {
        val toml = """
            NETWORK_PASSPHRASE="Test SDF Network ; September 2015"
            HORIZON_URL="https://horizon-testnet.stellar.org"
        """.trimIndent()

        val service = Sep08Service.fromDomain(
            domain = "example.com",
            httpClient = createTomlMockClient(toml)
        )

        assertTrue(service.regulatedAssets.isEmpty())
    }

    @Test
    fun testFromDomainSkipsRegulatedCurrenciesWithIncompleteData() = runTest {
        // A currency only qualifies as a regulated asset when code, issuer and
        // approval_server are all present.
        val toml = """
            NETWORK_PASSPHRASE="Test SDF Network ; September 2015"
            HORIZON_URL="https://horizon-testnet.stellar.org"

            [[CURRENCIES]]
            issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
            regulated=true
            approval_server="https://nocode.io/tx_approve"

            [[CURRENCIES]]
            code="NOISSUER"
            regulated=true
            approval_server="https://noissuer.io/tx_approve"

            [[CURRENCIES]]
            code="NOSERVER"
            issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
            regulated=true

            [[CURRENCIES]]
            code="GOAT"
            issuer="GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
            regulated=true
            approval_server="https://goat.io/tx_approve"
        """.trimIndent()

        val service = Sep08Service.fromDomain(
            domain = "example.com",
            httpClient = createTomlMockClient(toml)
        )

        assertEquals(1, service.regulatedAssets.size)
        assertEquals("GOAT", service.regulatedAssets[0].code)
    }

    @Test
    fun testFromDomainRegulatedAssetWithoutCriteria() = runTest {
        val mockClient = createTomlMockClient(multiRegulatedToml)

        val service = Sep08Service.fromDomain(
            domain = "example.com",
            httpClient = mockClient
        )

        val sheep = service.regulatedAssets.first { it.code == "SHEEP" }
        assertNull(sheep.approvalCriteria)
        assertEquals("https://sheep.io/tx_approve", sheep.approvalServer)
    }

    // ========== postTransaction() Tests ==========

    @Test
    fun testPostTransactionSuccess() = runTest {
        val mockClient = createPostMockClient(
            responseContent = successJson,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(response is Sep08PostTransactionResponse.Success)
        assertEquals("AAAAAgAAAAA...", response.tx)
        assertEquals("Approved", response.message)
    }

    @Test
    fun testPostTransactionSuccessNoMessage() = runTest {
        val mockClient = createPostMockClient(
            responseContent = successNoMessageJson,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(response is Sep08PostTransactionResponse.Success)
        assertEquals("AAAAAgAAAAA...", response.tx)
        assertNull(response.message)
    }

    @Test
    fun testPostTransactionRevised() = runTest {
        val mockClient = createPostMockClient(
            responseContent = revisedJson,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(response is Sep08PostTransactionResponse.Revised)
        assertEquals("AAAAAgBBBB...", response.tx)
        assertEquals("Added authorization ops", response.message)
    }

    @Test
    fun testPostTransactionPending() = runTest {
        val mockClient = createPostMockClient(
            responseContent = pendingJson,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(response is Sep08PostTransactionResponse.Pending)
        assertEquals(5000, response.timeout)
        assertEquals("Checking...", response.message)
    }

    @Test
    fun testPostTransactionPendingMinimal() = runTest {
        val mockClient = createPostMockClient(
            responseContent = pendingMinimalJson,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(response is Sep08PostTransactionResponse.Pending)
        assertEquals(0, response.timeout)
        assertNull(response.message)
    }

    @Test
    fun testPostTransactionActionRequired() = runTest {
        val mockClient = createPostMockClient(
            responseContent = actionRequiredJson,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(response is Sep08PostTransactionResponse.ActionRequired)
        assertEquals("KYC needed", response.message)
        assertEquals("https://kyc.io/verify", response.actionUrl)
        assertEquals("POST", response.actionMethod)
        assertNotNull(response.actionFields)
        assertEquals(2, response.actionFields!!.size)
        assertTrue(response.actionFields!!.contains("email_address"))
        assertTrue(response.actionFields!!.contains("mobile_number"))
    }

    @Test
    fun testPostTransactionActionRequiredGetDefault() = runTest {
        val mockClient = createPostMockClient(
            responseContent = actionRequiredGetJson,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(response is Sep08PostTransactionResponse.ActionRequired)
        assertEquals("Please verify", response.message)
        assertEquals("https://kyc.io/verify", response.actionUrl)
        assertEquals("GET", response.actionMethod)
        assertNull(response.actionFields)
    }

    @Test
    fun testPostTransactionRejected() = runTest {
        val mockClient = createPostMockClient(
            responseContent = rejectedJson,
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(response is Sep08PostTransactionResponse.Rejected)
        assertEquals("Destination blocked", response.error)
    }

    @Test
    fun testPostTransactionUnknownStatus() = runTest {
        val unknownStatusJson = """{"status":"unknown_status","tx":"AAAA..."}"""
        val mockClient = createPostMockClient(
            responseContent = unknownStatusJson,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        assertFailsWith<Sep08InvalidTransactionResponseException> {
            service.postTransaction(
                tx = "AAAAAgAAAAA...",
                approvalServer = "https://goat.io/tx_approve"
            )
        }
    }

    @Test
    fun testPostTransactionServerError() = runTest {
        val mockClient = createPostMockClient(
            responseContent = """{"error":"Internal server error"}""",
            statusCode = HttpStatusCode.InternalServerError,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        assertFailsWith<Sep08InvalidTransactionResponseException> {
            service.postTransaction(
                tx = "AAAAAgAAAAA...",
                approvalServer = "https://goat.io/tx_approve"
            )
        }
    }

    @Test
    fun testPostTransactionWithCustomHeaders() = runTest {
        var customHeaderVerified = false
        val mockEngine = MockEngine { request ->
            customHeaderVerified = request.headers["X-Api-Key"] == "my-api-key"
            respond(
                content = successJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }

        val service = createSep08ServiceWithMockClient(
            mockClient = mockClient,
            httpRequestHeaders = mapOf("X-Api-Key" to "my-api-key")
        )

        service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(customHeaderVerified, "Custom header should be sent with request")
    }

    @Test
    fun testPostTransactionVerifiesRequestBody() = runTest {
        var methodVerified = false
        var contentTypeVerified = false
        var urlVerified = false
        val mockEngine = MockEngine { request ->
            methodVerified = request.method == HttpMethod.Post
            contentTypeVerified = request.body.contentType?.match(ContentType.Application.Json) == true
            urlVerified = request.url.toString().contains("tx_approve")
            respond(
                content = successJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = createSep08ServiceWithMockClient(mockClient)

        service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(methodVerified, "Request method should be POST")
        assertTrue(contentTypeVerified, "Content-Type should be application/json")
        assertTrue(urlVerified, "Request URL should contain the approval server path")
    }

    @Test
    fun testPostTransactionVerifiesPostMethod() = runTest {
        var methodVerified = false
        val mockEngine = MockEngine { request ->
            methodVerified = request.method == HttpMethod.Post
            respond(
                content = successJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = createSep08ServiceWithMockClient(mockClient)

        service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(methodVerified, "Request method should be POST")
    }

    @Test
    fun testPostTransactionVerifiesContentType() = runTest {
        var contentTypeVerified = false
        val mockEngine = MockEngine { request ->
            contentTypeVerified = request.body.contentType?.match(ContentType.Application.Json) == true
            respond(
                content = successJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = createSep08ServiceWithMockClient(mockClient)

        service.postTransaction(
            tx = "AAAAAgAAAAA...",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(contentTypeVerified, "Content-Type should be application/json")
    }

    @Test
    fun testPostTransactionBadRequestNonRejected() = runTest {
        val badRequestJson = """{"status":"success","tx":"AAAA..."}"""
        val mockClient = createPostMockClient(
            responseContent = badRequestJson,
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        assertFailsWith<Sep08InvalidTransactionResponseException> {
            service.postTransaction(
                tx = "AAAAAgAAAAA...",
                approvalServer = "https://goat.io/tx_approve"
            )
        }
    }

    @Test
    fun testPostTransactionBadRequestMalformedJson() = runTest {
        val mockClient = createPostMockClient(
            responseContent = "this is not json",
            statusCode = HttpStatusCode.BadRequest,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        assertFailsWith<Sep08InvalidTransactionResponseException> {
            service.postTransaction(
                tx = "AAAAAgAAAAA...",
                approvalServer = "https://goat.io/tx_approve"
            )
        }
    }

    @Test
    fun testPostTransactionHttp403() = runTest {
        val mockClient = createPostMockClient(
            responseContent = """{"error":"Forbidden"}""",
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "tx_approve"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        assertFailsWith<Sep08InvalidTransactionResponseException> {
            service.postTransaction(
                tx = "AAAAAgAAAAA...",
                approvalServer = "https://goat.io/tx_approve"
            )
        }
    }

    // ========== postAction() Tests ==========

    @Test
    fun testPostActionDone() = runTest {
        val mockClient = createPostMockClient(
            responseContent = actionDoneJson,
            expectedPath = "kyc.io"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postAction(
            url = "https://kyc.io/verify",
            actionFields = mapOf("email_address" to "user@example.com")
        )

        assertTrue(response is Sep08PostActionResponse.Done)
    }

    @Test
    fun testPostActionNextUrl() = runTest {
        val mockClient = createPostMockClient(
            responseContent = actionNextUrlJson,
            expectedPath = "kyc.io"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postAction(
            url = "https://kyc.io/verify",
            actionFields = mapOf("email_address" to "user@example.com")
        )

        assertTrue(response is Sep08PostActionResponse.NextUrl)
        assertEquals("https://kyc.io/step2", response.nextUrl)
        assertEquals("Complete step 2", response.message)
    }

    @Test
    fun testPostActionNextUrlNoMessage() = runTest {
        val mockClient = createPostMockClient(
            responseContent = actionNextUrlNoMessageJson,
            expectedPath = "kyc.io"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        val response = service.postAction(
            url = "https://kyc.io/verify",
            actionFields = mapOf("email_address" to "user@example.com")
        )

        assertTrue(response is Sep08PostActionResponse.NextUrl)
        assertEquals("https://kyc.io/step2", response.nextUrl)
        assertNull(response.message)
    }

    @Test
    fun testPostActionUnknownResult() = runTest {
        val unknownResultJson = """{"result":"unknown_result"}"""
        val mockClient = createPostMockClient(
            responseContent = unknownResultJson,
            expectedPath = "kyc.io"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        assertFailsWith<Sep08InvalidActionResponseException> {
            service.postAction(
                url = "https://kyc.io/verify",
                actionFields = mapOf("email_address" to "user@example.com")
            )
        }
    }

    @Test
    fun testPostActionServerError() = runTest {
        val mockClient = createPostMockClient(
            responseContent = """{"error":"Internal server error"}""",
            statusCode = HttpStatusCode.InternalServerError,
            expectedPath = "kyc.io"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        assertFailsWith<Sep08InvalidActionResponseException> {
            service.postAction(
                url = "https://kyc.io/verify",
                actionFields = mapOf("email_address" to "user@example.com")
            )
        }
    }

    @Test
    fun testPostActionWithCustomHeaders() = runTest {
        var customHeaderVerified = false
        val mockEngine = MockEngine { request ->
            customHeaderVerified = request.headers["X-Api-Key"] == "my-api-key"
            respond(
                content = actionDoneJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }

        val service = createSep08ServiceWithMockClient(
            mockClient = mockClient,
            httpRequestHeaders = mapOf("X-Api-Key" to "my-api-key")
        )

        service.postAction(
            url = "https://kyc.io/verify",
            actionFields = mapOf("email_address" to "user@example.com")
        )

        assertTrue(customHeaderVerified, "Custom header should be sent with request")
    }

    @Test
    fun testPostActionVerifiesRequestBody() = runTest {
        var methodVerified = false
        var contentTypeVerified = false
        var urlVerified = false
        val mockEngine = MockEngine { request ->
            methodVerified = request.method == HttpMethod.Post
            contentTypeVerified = request.body.contentType?.match(ContentType.Application.Json) == true
            urlVerified = request.url.toString().contains("kyc.io/verify")
            respond(
                content = actionDoneJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = createSep08ServiceWithMockClient(mockClient)

        service.postAction(
            url = "https://kyc.io/verify",
            actionFields = mapOf("email_address" to "user@example.com")
        )

        assertTrue(methodVerified, "Request method should be POST")
        assertTrue(contentTypeVerified, "Content-Type should be application/json")
        assertTrue(urlVerified, "Request URL should contain the action URL path")
    }

    @Test
    fun testPostActionVerifiesPostMethod() = runTest {
        var methodVerified = false
        val mockEngine = MockEngine { request ->
            methodVerified = request.method == HttpMethod.Post
            respond(
                content = actionDoneJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val service = createSep08ServiceWithMockClient(mockClient)

        service.postAction(
            url = "https://kyc.io/verify",
            actionFields = mapOf("email_address" to "user@example.com")
        )

        assertTrue(methodVerified, "Request method should be POST")
    }

    @Test
    fun testPostActionHttp403() = runTest {
        val mockClient = createPostMockClient(
            responseContent = """{"error":"Forbidden"}""",
            statusCode = HttpStatusCode.Forbidden,
            expectedPath = "kyc.io"
        )
        val service = createSep08ServiceWithMockClient(mockClient)

        assertFailsWith<Sep08InvalidActionResponseException> {
            service.postAction(
                url = "https://kyc.io/verify",
                actionFields = mapOf("email_address" to "user@example.com")
            )
        }
    }

    // ========== authorizationRequired() Tests ==========

    private val issuerAccountId = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"

    private fun issuerAccountJson(authRequired: Boolean, authRevocable: Boolean): String = """
        {
            "id": "$issuerAccountId",
            "account_id": "$issuerAccountId",
            "sequence": "3298702387052545",
            "subentry_count": 0,
            "last_modified_ledger": 7654321,
            "paging_token": "$issuerAccountId",
            "thresholds": {
                "low_threshold": 0,
                "med_threshold": 0,
                "high_threshold": 0
            },
            "flags": {
                "auth_required": $authRequired,
                "auth_revocable": $authRevocable,
                "auth_immutable": false,
                "auth_clawback_enabled": false
            },
            "balances": [
                {
                    "asset_type": "native",
                    "balance": "10.0000000",
                    "buying_liabilities": "0.0000000",
                    "selling_liabilities": "0.0000000"
                }
            ],
            "signers": [
                {
                    "weight": 1,
                    "key": "$issuerAccountId",
                    "type": "ed25519_public_key"
                }
            ],
            "data": {},
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/accounts/$issuerAccountId"},
                "transactions": {"href": "https://horizon-testnet.stellar.org/accounts/$issuerAccountId/transactions"},
                "operations": {"href": "https://horizon-testnet.stellar.org/accounts/$issuerAccountId/operations"},
                "payments": {"href": "https://horizon-testnet.stellar.org/accounts/$issuerAccountId/payments"},
                "effects": {"href": "https://horizon-testnet.stellar.org/accounts/$issuerAccountId/effects"},
                "offers": {"href": "https://horizon-testnet.stellar.org/accounts/$issuerAccountId/offers"},
                "trades": {"href": "https://horizon-testnet.stellar.org/accounts/$issuerAccountId/trades"},
                "data": {"href": "https://horizon-testnet.stellar.org/accounts/$issuerAccountId/data/{key}", "templated": true}
            }
        }
    """.trimIndent()

    private fun createHorizonMockClient(
        accountJson: String,
        onRequest: ((HttpRequestData) -> Unit)? = null
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            onRequest?.invoke(request)
            respond(
                content = accountJson,
                status = HttpStatusCode.OK,
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

    private fun createSep08ServiceWithHorizon(horizonClient: HttpClient): Sep08Service {
        return Sep08Service(
            tomlData = com.soneso.stellar.sdk.sep.sep01.StellarToml.parse(regulatedToml),
            regulatedAssets = emptyList(),
            network = Network.TESTNET,
            horizonServer = com.soneso.stellar.sdk.horizon.HorizonServer(
                "https://horizon-testnet.stellar.org",
                httpClient = horizonClient
            )
        )
    }

    @Test
    fun testAuthorizationRequiredWithBothIssuerFlagsSet() = runTest {
        var requestedPath: String? = null
        val service = createSep08ServiceWithHorizon(
            createHorizonMockClient(
                issuerAccountJson(authRequired = true, authRevocable = true),
                onRequest = { requestedPath = it.url.encodedPath }
            )
        )
        val asset = RegulatedAsset(
            code = "GOAT",
            issuer = issuerAccountId,
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(service.authorizationRequired(asset))
        assertEquals("/accounts/$issuerAccountId", requestedPath)
    }

    @Test
    fun testAuthorizationRequiredWithoutRevocableFlag() = runTest {
        val service = createSep08ServiceWithHorizon(
            createHorizonMockClient(issuerAccountJson(authRequired = true, authRevocable = false))
        )
        val asset = RegulatedAsset(
            code = "GOAT",
            issuer = issuerAccountId,
            approvalServer = "https://goat.io/tx_approve"
        )

        assertFalse(service.authorizationRequired(asset))
    }

    @Test
    fun testAuthorizationRequiredWithoutAuthRequiredFlag() = runTest {
        val service = createSep08ServiceWithHorizon(
            createHorizonMockClient(issuerAccountJson(authRequired = false, authRevocable = true))
        )
        val asset = RegulatedAsset(
            code = "GOAT",
            issuer = issuerAccountId,
            approvalServer = "https://goat.io/tx_approve"
        )

        assertFalse(service.authorizationRequired(asset))
    }

    // ========== RegulatedAsset Tests ==========

    @Test
    fun testRegulatedAssetProperties() = runTest {
        val asset = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve",
            approvalCriteria = "The goat approval server will ensure compliance"
        )

        assertEquals("GOAT", asset.code)
        assertEquals("GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP", asset.issuer)
        assertEquals("https://goat.io/tx_approve", asset.approvalServer)
        assertEquals("The goat approval server will ensure compliance", asset.approvalCriteria)
    }

    @Test
    fun testRegulatedAssetToString() = runTest {
        val asset = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertEquals(
            "GOAT:GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            asset.toString()
        )
    }

    @Test
    fun testRegulatedAssetToXdr() = runTest {
        val asset = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )

        val xdr = asset.toXdr()
        assertNotNull(xdr)
    }

    @Test
    fun testRegulatedAssetToAsset() = runTest {
        val regulatedAsset = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )

        val asset = regulatedAsset.toAsset()
        assertNotNull(asset)
        assertEquals(
            "GOAT:GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            asset.toString()
        )
    }

    @Test
    fun testRegulatedAssetEquality() = runTest {
        val asset1 = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve",
            approvalCriteria = "criteria"
        )
        val asset2 = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve",
            approvalCriteria = "criteria"
        )
        val asset3 = RegulatedAsset(
            code = "SHEEP",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://sheep.io/tx_approve"
        )

        assertEquals(asset1, asset2)
        assertEquals(asset1.hashCode(), asset2.hashCode())
        assertNotEquals(asset1, asset3)
    }

    @Test
    fun testRegulatedAssetWithoutCriteria() = runTest {
        val asset = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertNull(asset.approvalCriteria)
    }

    @Test
    fun testRegulatedAssetComparable() = runTest {
        val assetA = RegulatedAsset(
            code = "AAA",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://example.com/approve"
        )
        val assetB = RegulatedAsset(
            code = "BBB",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://example.com/approve"
        )

        assertTrue(assetA < assetB)
    }

    @Test
    fun testRegulatedAssetAlphaNum12() = runTest {
        val asset = RegulatedAsset(
            code = "LONGASSETCD",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://example.com/approve"
        )

        assertNotNull(asset.toXdr())
        assertEquals(
            "LONGASSETCD:GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            asset.toString()
        )
    }

    @Test
    fun testRegulatedAssetNotEqualToNull() = runTest {
        val asset = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertFalse(asset.equals(null))
    }

    @Test
    fun testRegulatedAssetNotEqualToDifferentType() = runTest {
        val asset = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertFalse(asset.equals("not an asset"))
    }

    @Test
    fun testRegulatedAssetEqualToSelf() = runTest {
        val asset = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertTrue(asset.equals(asset))
    }

    @Test
    fun testRegulatedAssetType() = runTest {
        val alphaNum4 = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )
        val alphaNum12 = RegulatedAsset(
            code = "LONGASSETCD",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertEquals(
            com.soneso.stellar.sdk.xdr.AssetTypeXdr.ASSET_TYPE_CREDIT_ALPHANUM4,
            alphaNum4.type
        )
        assertEquals(
            com.soneso.stellar.sdk.xdr.AssetTypeXdr.ASSET_TYPE_CREDIT_ALPHANUM12,
            alphaNum12.type
        )
    }

    @Test
    fun testRegulatedAssetInequalityPerField() = runTest {
        val base = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve",
            approvalCriteria = "criteria"
        )
        val differentIssuer = RegulatedAsset(
            code = "GOAT",
            issuer = "GD5T6IPRNCKFOHQWT264YPKOZAWUMMZOLZBJ6BNQMUGPWGRLBK3U7ZNP",
            approvalServer = "https://goat.io/tx_approve",
            approvalCriteria = "criteria"
        )
        val differentServer = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://other.io/tx_approve",
            approvalCriteria = "criteria"
        )
        val differentCriteria = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve",
            approvalCriteria = "other criteria"
        )

        assertNotEquals(base, differentIssuer)
        assertNotEquals(base, differentServer)
        assertNotEquals(base, differentCriteria)
        assertNotEquals(base.hashCode(), differentServer.hashCode())
    }

    @Test
    fun testRegulatedAssetHashCodeWithAndWithoutCriteria() = runTest {
        val withCriteria = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve",
            approvalCriteria = "criteria"
        )
        val withoutCriteria = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )
        val alsoWithoutCriteria = RegulatedAsset(
            code = "GOAT",
            issuer = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            approvalServer = "https://goat.io/tx_approve"
        )

        assertNotEquals(withCriteria, withoutCriteria)
        assertNotEquals(withCriteria.hashCode(), withoutCriteria.hashCode())
        assertEquals(withoutCriteria, alsoWithoutCriteria)
        assertEquals(withoutCriteria.hashCode(), alsoWithoutCriteria.hashCode())
    }
}
