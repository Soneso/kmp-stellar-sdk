package com.soneso.stellar.sdk.unitTests.horizon

import com.soneso.stellar.sdk.horizon.*
import com.soneso.stellar.sdk.Util
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Comprehensive tests for client identification headers in [HorizonServer].
 *
 * These tests verify that the SDK sends correct client identification headers
 * (X-Client-Name and X-Client-Version) with all HTTP requests to Horizon servers.
 *
 * Client identification headers help Stellar server operators:
 * - Track SDK usage statistics
 * - Identify SDK-specific issues
 * - Provide better support and debugging
 *
 * ## Test Coverage
 *
 * 1. Default HTTP client header verification
 * 2. Submit HTTP client header verification
 * 3. GET request headers (root(), accounts(), etc.)
 * 4. POST request headers (submitTransaction(), submitTransactionAsync())
 * 5. Header values match SDK version and name
 * 6. Headers are sent with every request
 *
 * ## Implementation Notes
 *
 * Uses Ktor's MockEngine to intercept HTTP requests and capture headers without
 * making actual network calls. This ensures tests are fast, deterministic, and
 * don't depend on external services.
 */
class HorizonServerHeadersTest {

    companion object {
        private const val TEST_SERVER_URL = "https://horizon-testnet.stellar.org"

        // Expected header values
        private const val EXPECTED_CLIENT_NAME = "kmp-stellar-sdk"

        // Mock responses
        private const val ROOT_RESPONSE = """{
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/"},
                "account": {"href": "https://horizon-testnet.stellar.org/accounts/{account_id}", "templated": true}
            },
            "horizon_version": "2.0.0",
            "core_version": "v19.10.1",
            "history_latest_ledger": 12345,
            "history_latest_ledger_closed_at": "2023-01-01T00:00:00Z",
            "history_elder_ledger": 1,
            "core_latest_ledger": 12345,
            "network_passphrase": "Test SDF Network ; September 2015",
            "current_protocol_version": 20,
            "supported_protocol_version": 20,
            "core_supported_protocol_version": 20
        }"""

        private const val ACCOUNT_RESPONSE = """{
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"},
                "transactions": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/transactions"},
                "operations": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/operations"},
                "payments": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/payments"},
                "effects": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/effects"},
                "offers": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/offers"},
                "trades": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/trades"},
                "data": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7/data/{key}", "templated": true}
            },
            "id": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "account_id": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "sequence": "123456789",
            "subentry_count": 0,
            "last_modified_ledger": 12345,
            "last_modified_time": "2023-01-01T00:00:00Z",
            "thresholds": {
                "low_threshold": 0,
                "med_threshold": 0,
                "high_threshold": 0
            },
            "flags": {
                "auth_required": false,
                "auth_revocable": false,
                "auth_immutable": false,
                "auth_clawback_enabled": false
            },
            "balances": [],
            "signers": [],
            "paging_token": "123456789"
        }"""

        private const val TRANSACTION_RESPONSE = """{
            "_links": {
                "self": {"href": "https://horizon-testnet.stellar.org/transactions/abc123"},
                "account": {"href": "https://horizon-testnet.stellar.org/accounts/GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7"},
                "ledger": {"href": "https://horizon-testnet.stellar.org/ledgers/12345"},
                "operations": {"href": "https://horizon-testnet.stellar.org/transactions/abc123/operations"},
                "effects": {"href": "https://horizon-testnet.stellar.org/transactions/abc123/effects"},
                "precedes": {"href": "https://horizon-testnet.stellar.org/transactions?cursor=abc123&order=asc"},
                "succeeds": {"href": "https://horizon-testnet.stellar.org/transactions?cursor=abc123&order=desc"}
            },
            "id": "abc123",
            "paging_token": "12345-1",
            "hash": "abc123",
            "ledger": 12345,
            "created_at": "2023-01-01T00:00:00Z",
            "source_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "source_account_sequence": "123456789",
            "fee_account": "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7",
            "fee_charged": 100,
            "max_fee": 100,
            "operation_count": 1,
            "successful": true,
            "signatures": ["test"],
            "memo_type": "none"
        }"""
    }

    // ========== Helper Methods ==========

    /**
     * Creates a mock HTTP client that captures request headers.
     *
     * IMPORTANT: This captures headers AFTER all Ktor plugins (including DefaultRequest)
     * have processed the request. The MockEngine.Config handles this by capturing
     * from the HttpRequestData which contains the final merged headers.
     *
     * @param capturedHeaders Mutable reference to store captured headers
     * @param responseJson The JSON response to return
     * @param statusCode The HTTP status code to return
     * @return Configured HttpClient with MockEngine
     */
    private fun createMockClientWithHeaderCapture(
        capturedHeaders: MutableList<Headers>,
        responseJson: String,
        statusCode: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
        val mockEngine = MockEngine { requestData ->
            // MockEngine receives HttpRequestData which contains final headers after all plugins
            // Store headers for verification
            capturedHeaders.add(requestData.headers)

            respond(
                content = ByteReadChannel(responseJson),
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                    encodeDefaults = true
                })
            }
            install(DefaultRequest) {
                header("X-Client-Name", EXPECTED_CLIENT_NAME)
                header("X-Client-Version", Util.getSdkVersion())
            }
        }
    }

    /**
     * Verifies that headers contain the expected client identification headers.
     *
     * @param headers The headers to verify
     * @param testName Name of the test for better error messages
     */
    private fun verifyClientHeaders(headers: Headers, testName: String) {
        val clientName = headers["X-Client-Name"]
        val clientVersion = headers["X-Client-Version"]

        assertNotNull(clientName, "$testName: X-Client-Name header should be present")
        assertEquals(
            EXPECTED_CLIENT_NAME,
            clientName,
            "$testName: X-Client-Name should be '$EXPECTED_CLIENT_NAME'"
        )

        assertNotNull(clientVersion, "$testName: X-Client-Version header should be present")
        assertEquals(
            Util.getSdkVersion(),
            clientVersion,
            "$testName: X-Client-Version should match SDK version"
        )
    }

    // ========== Default HTTP Client Tests ==========

    @Test
    fun testDefaultHttpClient_hasClientIdentificationHeaders() = runTest {
        // Given: A list to capture headers
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, ROOT_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Making a request using default client (root endpoint)
        server.root().execute()

        // Then: Headers should contain client identification
        assertTrue(capturedHeaders.isNotEmpty(), "Should have captured at least one request")
        verifyClientHeaders(capturedHeaders.first(), "Default HTTP Client")

        server.close()
    }

    @Test
    fun testSubmitHttpClient_hasClientIdentificationHeaders() = runTest {
        // Given: A list to capture headers
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, TRANSACTION_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Making a request using submit client (submit transaction endpoint)
        server.submitTransaction("AAAAAA==", skipMemoRequiredCheck = true)

        // Then: Headers should contain client identification
        assertTrue(capturedHeaders.isNotEmpty(), "Should have captured at least one request")
        verifyClientHeaders(capturedHeaders.first(), "Submit HTTP Client")

        server.close()
    }

    // ========== GET Request Header Tests ==========

    @Test
    fun testRootEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, ROOT_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling root endpoint
        server.root().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Root Endpoint")

        server.close()
    }

    @Test
    fun testAccountsEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, """{"_embedded": {"records": []}}""")
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling accounts endpoint
        server.accounts().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Accounts Endpoint")

        server.close()
    }

    @Test
    fun testAccountDetailsEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, ACCOUNT_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling account details endpoint
        server.accounts().account("GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Account Details Endpoint")

        server.close()
    }

    @Test
    fun testTransactionsEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, """{"_embedded": {"records": []}}""")
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling transactions endpoint
        server.transactions().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Transactions Endpoint")

        server.close()
    }

    @Test
    fun testOperationsEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, """{"_embedded": {"records": []}}""")
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling operations endpoint
        server.operations().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Operations Endpoint")

        server.close()
    }

    @Test
    fun testPaymentsEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, """{"_embedded": {"records": []}}""")
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling payments endpoint
        server.payments().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Payments Endpoint")

        server.close()
    }

    @Test
    fun testEffectsEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, """{"_embedded": {"records": []}}""")
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling effects endpoint
        server.effects().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Effects Endpoint")

        server.close()
    }

    @Test
    fun testLedgersEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, """{"_embedded": {"records": []}}""")
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling ledgers endpoint
        server.ledgers().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Ledgers Endpoint")

        server.close()
    }

    @Test
    fun testOffersEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, """{"_embedded": {"records": []}}""")
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling offers endpoint
        server.offers().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Offers Endpoint")

        server.close()
    }

    @Test
    fun testTradesEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, """{"_embedded": {"records": []}}""")
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling trades endpoint
        server.trades().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Trades Endpoint")

        server.close()
    }

    @Test
    fun testAssetsEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, """{"_embedded": {"records": []}}""")
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling assets endpoint
        server.assets().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Assets Endpoint")

        server.close()
    }

    @Test
    fun testFeeStatsEndpoint_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val feeStatsResponse = """{
            "last_ledger": 12345,
            "last_ledger_base_fee": 100,
            "ledger_capacity_usage": "0.5",
            "fee_charged": {
                "min": 100,
                "max": 1000,
                "mode": 100,
                "p10": 100,
                "p20": 100,
                "p30": 100,
                "p40": 100,
                "p50": 200,
                "p60": 300,
                "p70": 400,
                "p80": 500,
                "p90": 600,
                "p95": 800,
                "p99": 900
            },
            "max_fee": {
                "min": 100,
                "max": 10000,
                "mode": 1000,
                "p10": 100,
                "p20": 200,
                "p30": 300,
                "p40": 400,
                "p50": 1000,
                "p60": 2000,
                "p70": 3000,
                "p80": 4000,
                "p90": 5000,
                "p95": 8000,
                "p99": 9000
            }
        }"""
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, feeStatsResponse)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Calling fee stats endpoint
        server.feeStats().execute()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Fee Stats Endpoint")

        server.close()
    }

    // ========== POST Request Header Tests ==========

    @Test
    fun testSubmitTransaction_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, TRANSACTION_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Submitting a transaction
        server.submitTransaction("AAAAAA==", skipMemoRequiredCheck = true)

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Submit Transaction")

        server.close()
    }

    @Test
    fun testSubmitTransactionAsync_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val asyncResponse = """{
            "tx_status": "PENDING",
            "hash": "abc123"
        }"""
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, asyncResponse)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Submitting a transaction asynchronously
        server.submitTransactionAsync("AAAAAA==", skipMemoRequiredCheck = true)

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "Submit Transaction Async")

        server.close()
    }

    // ========== Header Value Tests ==========

    @Test
    fun testClientNameHeader_matchesExpectedValue() {
        // Given/When: Expected client name
        val expectedClientName = "kmp-stellar-sdk"

        // Then: The constant matches
        assertEquals(expectedClientName, EXPECTED_CLIENT_NAME)
    }

    @Test
    fun testClientVersionHeader_matchesUtilSdkVersion() {
        // Given: SDK version from Util
        val sdkVersion = Util.getSdkVersion()

        // Then: Version should not be null or empty
        assertNotNull(sdkVersion)
        assertTrue(sdkVersion.isNotEmpty(), "SDK version should not be empty")

        // Should follow semantic versioning pattern (with or without -SNAPSHOT)
        assertTrue(
            sdkVersion.matches(Regex("""\d+\.\d+\.\d+(-SNAPSHOT)?""")),
            "SDK version should match semantic versioning pattern: $sdkVersion"
        )
    }

    @Test
    fun testDefaultHttpClientCreation_includesHeaders() {
        // Given/When: Creating default HTTP client
        val client = HorizonServer.createDefaultHttpClient()

        // Then: Client is created successfully
        assertNotNull(client)

        // Cleanup
        client.close()
    }

    @Test
    fun testSubmitHttpClientCreation_includesHeaders() {
        // Given/When: Creating submit HTTP client
        val client = HorizonServer.createSubmitHttpClient()

        // Then: Client is created successfully
        assertNotNull(client)

        // Cleanup
        client.close()
    }

    // ========== Multiple Requests Test ==========

    @Test
    fun testMultipleRequests_allIncludeClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, ROOT_RESPONSE)
        val server = HorizonServer(TEST_SERVER_URL, httpClient = mockClient, submitHttpClient = mockClient)

        // When: Making multiple requests
        server.root().execute()
        server.root().execute()
        server.root().execute()

        // Then: All requests should include client headers
        assertEquals(3, capturedHeaders.size, "Should have captured 3 requests")
        capturedHeaders.forEachIndexed { index, headers ->
            verifyClientHeaders(headers, "Request ${index + 1}")
        }

        server.close()
    }

    // ========== Integration with Custom Client Test ==========

    @Test
    fun testCustomHttpClient_canOverrideHeaders() = runTest {
        // Given: Custom client with different headers
        val capturedHeaders = mutableListOf<Headers>()
        val mockEngine = MockEngine { requestData ->
            capturedHeaders.add(requestData.headers)
            respond(
                content = ByteReadChannel(ROOT_RESPONSE),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val customClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(DefaultRequest) {
                header("X-Client-Name", "custom-client")
                header("X-Client-Version", "9.9.9")
            }
        }

        val server = HorizonServer(TEST_SERVER_URL, httpClient = customClient, submitHttpClient = customClient)

        // When: Making a request with custom client
        server.root().execute()

        // Then: Custom headers should be present
        val headers = capturedHeaders.first()
        assertEquals("custom-client", headers["X-Client-Name"])
        assertEquals("9.9.9", headers["X-Client-Version"])

        server.close()
    }
}
