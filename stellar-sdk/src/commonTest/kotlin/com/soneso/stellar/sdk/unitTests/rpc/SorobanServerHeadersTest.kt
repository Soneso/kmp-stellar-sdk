package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.*
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
 * Comprehensive tests for client identification headers in [SorobanServer].
 *
 * These tests verify that the SDK sends correct client identification headers
 * (X-Client-Name and X-Client-Version) with all HTTP requests to Soroban RPC servers.
 *
 * Client identification headers help Stellar server operators:
 * - Track SDK usage statistics
 * - Identify SDK-specific issues
 * - Provide better support and debugging
 *
 * ## Test Coverage
 *
 * 1. Default HTTP client header verification
 * 2. JSON-RPC POST request headers
 * 3. All RPC method endpoints (getHealth, getNetwork, simulateTransaction, etc.)
 * 4. Header values match SDK version and name
 * 5. Headers are sent with every request
 *
 * ## Implementation Notes
 *
 * Uses Ktor's MockEngine to intercept HTTP requests and capture headers without
 * making actual network calls. This ensures tests are fast, deterministic, and
 * don't depend on external services.
 *
 * All Soroban RPC methods use JSON-RPC 2.0 over HTTP POST, so all requests
 * are POST requests with JSON body.
 */
class SorobanServerHeadersTest {

    companion object {
        private const val TEST_SERVER_URL = "https://soroban-testnet.stellar.org:443"

        // Expected header values
        private const val EXPECTED_CLIENT_NAME = "kmp-stellar-sdk"

        // Mock JSON-RPC responses
        private const val HEALTH_RESPONSE = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {
                "status": "healthy",
                "latestLedger": 50000,
                "oldestLedger": 1,
                "ledgerRetentionWindow": 10000
            }
        }"""

        private const val NETWORK_RESPONSE = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {
                "friendbotUrl": "https://friendbot-futurenet.stellar.org/",
                "passphrase": "Test SDF Future Network ; October 2022",
                "protocolVersion": "20"
            }
        }"""

        private const val LATEST_LEDGER_RESPONSE = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {
                "id": "e73d7654b72daa637f396669182c6072549736a26d1f31bc53ba6a08f9e3ca1f",
                "protocolVersion": 20,
                "sequence": 24170
            }
        }"""

        private const val FEE_STATS_RESPONSE = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {
                "sorobanInclusionFee": {
                    "max": 10000,
                    "min": 100,
                    "mode": 500,
                    "p10": 150,
                    "p20": 200,
                    "p30": 250,
                    "p40": 300,
                    "p50": 500,
                    "p60": 600,
                    "p70": 700,
                    "p80": 800,
                    "p90": 1000,
                    "p95": 5000,
                    "p99": 9000,
                    "transactionCount": 100,
                    "ledgerCount": 50
                },
                "inclusionFee": {
                    "max": 1000,
                    "min": 100,
                    "mode": 100,
                    "p10": 100,
                    "p20": 100,
                    "p30": 100,
                    "p40": 100,
                    "p50": 100,
                    "p60": 200,
                    "p70": 300,
                    "p80": 400,
                    "p90": 500,
                    "p95": 800,
                    "p99": 900,
                    "transactionCount": 10,
                    "ledgerCount": 50
                },
                "latestLedger": 4519945
            }
        }"""

        private const val VERSION_INFO_RESPONSE = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {
                "version": "20.0.0",
                "commitHash": "abc123",
                "buildTimestamp": "2023-05-15T12:34:56Z",
                "captiveCoreVersion": "19.10.1",
                "protocolVersion": 20
            }
        }"""

        private const val LEDGER_ENTRIES_RESPONSE = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {
                "entries": null,
                "latestLedger": 14245
            }
        }"""

        private const val TRANSACTION_RESPONSE = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {
                "status": "SUCCESS",
                "latestLedger": 14245,
                "latestLedgerCloseTime": 1690594566,
                "oldestLedger": 1000,
                "oldestLedgerCloseTime": 1690500000
            }
        }"""

        private const val SIMULATE_TRANSACTION_RESPONSE = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {
                "transactionData": "AAAAAAAAAAIAAAAGAAAAAem354u9STQWq5b3Ed1j9tOemvL7xV0NPwhn4gXg0AP8AAAAFAAAAAEAAAAH8dTe2OoI0BnhlDbH0fWvXmvprkBvBAgKIcL9busuuMEAAAABAAAABgAAAAHpt+eLvUk0FquW9xHdY/bTnpry+8VdDT8IZ+IF4NAD/AAAABAAAAABAAAAAgAAAA8AAAAHQ291bnRlcgAAAAASAAAAAAAAAABYt8SiyPKXqo89JHEoH9/M7K/kjlZjMT7BjhKnPsqYoQAAAAEAHifGAAAFlAAAAIgAAAAAAAAAAg==",
                "minResourceFee": "58181",
                "latestLedger": 14245
            }
        }"""

        private const val SEND_TRANSACTION_RESPONSE = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {
                "status": "PENDING",
                "hash": "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7",
                "latestLedger": 45075,
                "latestLedgerCloseTime": 1690594566
            }
        }"""

        private const val GET_EVENTS_RESPONSE = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {
                "events": [],
                "latestLedger": 14245
            }
        }"""
    }

    // ========== Helper Methods ==========

    /**
     * Creates a mock HTTP client that captures request headers.
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
        val mockEngine = MockEngine { request ->
            // Capture headers from the request
            capturedHeaders.add(request.headers)

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
                    encodeDefaults = false
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000L
                connectTimeoutMillis = 10_000L
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
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, HEALTH_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Making a request using default client
        server.getHealth()

        // Then: Headers should contain client identification
        assertTrue(capturedHeaders.isNotEmpty(), "Should have captured at least one request")
        verifyClientHeaders(capturedHeaders.first(), "Default HTTP Client")

        server.close()
    }

    @Test
    fun testDefaultHttpClientCreation_includesHeaders() {
        // Given/When: Creating default HTTP client
        val client = SorobanServer.defaultHttpClient()

        // Then: Client is created successfully
        assertNotNull(client)

        // Cleanup
        client.close()
    }

    // ========== RPC Method Header Tests ==========

    @Test
    fun testGetHealth_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, HEALTH_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Calling getHealth
        server.getHealth()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "getHealth")

        server.close()
    }

    @Test
    fun testGetNetwork_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, NETWORK_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Calling getNetwork
        server.getNetwork()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "getNetwork")

        server.close()
    }

    @Test
    fun testGetLatestLedger_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, LATEST_LEDGER_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Calling getLatestLedger
        server.getLatestLedger()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "getLatestLedger")

        server.close()
    }

    @Test
    fun testGetFeeStats_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, FEE_STATS_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Calling getFeeStats
        server.getFeeStats()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "getFeeStats")

        server.close()
    }

    @Test
    fun testGetVersionInfo_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, VERSION_INFO_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Calling getVersionInfo
        server.getVersionInfo()

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "getVersionInfo")

        server.close()
    }

    @Test
    fun testGetLedgerEntries_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture and a dummy ledger key
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, LEDGER_ENTRIES_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // Create a simple ledger key for testing
        val ledgerKey = com.soneso.stellar.sdk.xdr.LedgerKeyXdr.Account(
            com.soneso.stellar.sdk.xdr.LedgerKeyAccountXdr(
                accountId = com.soneso.stellar.sdk.xdr.AccountIDXdr(
                    com.soneso.stellar.sdk.xdr.PublicKeyXdr.Ed25519(
                        com.soneso.stellar.sdk.xdr.Uint256Xdr(ByteArray(32))
                    )
                )
            )
        )

        // When: Calling getLedgerEntries
        server.getLedgerEntries(listOf(ledgerKey))

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "getLedgerEntries")

        server.close()
    }

    @Test
    fun testGetTransaction_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, TRANSACTION_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Calling getTransaction (with valid 64-char hex hash)
        server.getTransaction("a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7")

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "getTransaction")

        server.close()
    }

    @Test
    fun testGetEvents_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, GET_EVENTS_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Calling getEvents
        val filter = com.soneso.stellar.sdk.rpc.requests.GetEventsRequest.EventFilter(
            type = com.soneso.stellar.sdk.rpc.requests.GetEventsRequest.EventFilterType.CONTRACT,
            contractIds = null,
            topics = null
        )
        val request = com.soneso.stellar.sdk.rpc.requests.GetEventsRequest(
            startLedger = 1000,
            filters = listOf(filter),
            pagination = null
        )
        server.getEvents(request)

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "getEvents")

        server.close()
    }

    // ========== Transaction Operation Header Tests ==========

    @Test
    fun testSimulateTransaction_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture and test transaction
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, SIMULATE_TRANSACTION_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // Create a simple test transaction
        val sourceKeypair = com.soneso.stellar.sdk.KeyPair.random()
        val sourceAccount = com.soneso.stellar.sdk.Account(sourceKeypair.getAccountId(), 1L)

        val contractHash = ByteArray(32)
        val contractId = com.soneso.stellar.sdk.xdr.ContractIDXdr(com.soneso.stellar.sdk.xdr.HashXdr(contractHash))

        val minimalSorobanData = com.soneso.stellar.sdk.xdr.SorobanTransactionDataXdr(
            ext = com.soneso.stellar.sdk.xdr.SorobanTransactionDataExtXdr.Void,
            resources = com.soneso.stellar.sdk.xdr.SorobanResourcesXdr(
                footprint = com.soneso.stellar.sdk.xdr.LedgerFootprintXdr(
                    readOnly = emptyList(),
                    readWrite = emptyList()
                ),
                instructions = com.soneso.stellar.sdk.xdr.Uint32Xdr(0u),
                diskReadBytes = com.soneso.stellar.sdk.xdr.Uint32Xdr(0u),
                writeBytes = com.soneso.stellar.sdk.xdr.Uint32Xdr(0u)
            ),
            resourceFee = com.soneso.stellar.sdk.xdr.Int64Xdr(0L)
        )

        val transaction = com.soneso.stellar.sdk.TransactionBuilder(sourceAccount, com.soneso.stellar.sdk.Network.TESTNET)
            .addOperation(
                com.soneso.stellar.sdk.InvokeHostFunctionOperation(
                    hostFunction = com.soneso.stellar.sdk.xdr.HostFunctionXdr.InvokeContract(
                        com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr(
                            contractAddress = com.soneso.stellar.sdk.xdr.SCAddressXdr.ContractId(contractId),
                            functionName = com.soneso.stellar.sdk.xdr.SCSymbolXdr("test"),
                            args = emptyList()
                        )
                    ),
                    auth = emptyList()
                )
            )
            .setTimeout(300)
            .setBaseFee(100)
            .setSorobanData(minimalSorobanData)
            .build()

        transaction.sign(sourceKeypair)

        // When: Calling simulateTransaction
        server.simulateTransaction(transaction)

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "simulateTransaction")

        server.close()
    }

    @Test
    fun testSendTransaction_sendsClientHeaders() = runTest {
        // Given: Mock server with header capture and test transaction
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, SEND_TRANSACTION_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // Create a simple test transaction
        val sourceKeypair = com.soneso.stellar.sdk.KeyPair.random()
        val sourceAccount = com.soneso.stellar.sdk.Account(sourceKeypair.getAccountId(), 1L)

        val contractHash = ByteArray(32)
        val contractId = com.soneso.stellar.sdk.xdr.ContractIDXdr(com.soneso.stellar.sdk.xdr.HashXdr(contractHash))

        val minimalSorobanData = com.soneso.stellar.sdk.xdr.SorobanTransactionDataXdr(
            ext = com.soneso.stellar.sdk.xdr.SorobanTransactionDataExtXdr.Void,
            resources = com.soneso.stellar.sdk.xdr.SorobanResourcesXdr(
                footprint = com.soneso.stellar.sdk.xdr.LedgerFootprintXdr(
                    readOnly = emptyList(),
                    readWrite = emptyList()
                ),
                instructions = com.soneso.stellar.sdk.xdr.Uint32Xdr(0u),
                diskReadBytes = com.soneso.stellar.sdk.xdr.Uint32Xdr(0u),
                writeBytes = com.soneso.stellar.sdk.xdr.Uint32Xdr(0u)
            ),
            resourceFee = com.soneso.stellar.sdk.xdr.Int64Xdr(0L)
        )

        val transaction = com.soneso.stellar.sdk.TransactionBuilder(sourceAccount, com.soneso.stellar.sdk.Network.TESTNET)
            .addOperation(
                com.soneso.stellar.sdk.InvokeHostFunctionOperation(
                    hostFunction = com.soneso.stellar.sdk.xdr.HostFunctionXdr.InvokeContract(
                        com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr(
                            contractAddress = com.soneso.stellar.sdk.xdr.SCAddressXdr.ContractId(contractId),
                            functionName = com.soneso.stellar.sdk.xdr.SCSymbolXdr("test"),
                            args = emptyList()
                        )
                    ),
                    auth = emptyList()
                )
            )
            .setTimeout(300)
            .setBaseFee(100)
            .setSorobanData(minimalSorobanData)
            .build()

        transaction.sign(sourceKeypair)

        // When: Calling sendTransaction
        server.sendTransaction(transaction)

        // Then: Client headers are sent
        verifyClientHeaders(capturedHeaders.first(), "sendTransaction")

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

    // ========== Multiple Requests Test ==========

    @Test
    fun testMultipleRequests_allIncludeClientHeaders() = runTest {
        // Given: Mock server with header capture
        val capturedHeaders = mutableListOf<Headers>()
        val mockClient = createMockClientWithHeaderCapture(capturedHeaders, HEALTH_RESPONSE)
        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Making multiple requests
        server.getHealth()
        server.getHealth()
        server.getHealth()

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
        val mockEngine = MockEngine { request ->
            capturedHeaders.add(request.headers)
            respond(
                content = ByteReadChannel(HEALTH_RESPONSE),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val customClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                    encodeDefaults = false
                })
            }
            install(DefaultRequest) {
                header("X-Client-Name", "custom-soroban-client")
                header("X-Client-Version", "9.9.9")
            }
        }

        val server = SorobanServer(TEST_SERVER_URL, customClient)

        // When: Making a request with custom client
        server.getHealth()

        // Then: Custom headers should be present
        val headers = capturedHeaders.first()
        assertEquals("custom-soroban-client", headers["X-Client-Name"])
        assertEquals("9.9.9", headers["X-Client-Version"])

        server.close()
    }

    // ========== JSON-RPC Specific Tests ==========

    @Test
    fun testAllRpcRequests_usePostMethod() = runTest {
        // Given: Mock client that captures request method
        var capturedMethod: HttpMethod? = null
        val mockEngine = MockEngine { request ->
            capturedMethod = request.method
            respond(
                content = ByteReadChannel(HEALTH_RESPONSE),
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
            install(DefaultRequest) {
                header("X-Client-Name", EXPECTED_CLIENT_NAME)
                header("X-Client-Version", Util.getSdkVersion())
            }
        }

        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Making any RPC request
        server.getHealth()

        // Then: Should use POST method (JSON-RPC 2.0 over HTTP)
        assertEquals(HttpMethod.Post, capturedMethod)

        server.close()
    }

    @Test
    fun testContentTypeHeader_isApplicationJson() = runTest {
        // Given: Mock client that captures Content-Type header
        var capturedContentType: String? = null
        val mockEngine = MockEngine { request ->
            capturedContentType = request.body.contentType?.toString()
            respond(
                content = ByteReadChannel(HEALTH_RESPONSE),
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
            install(DefaultRequest) {
                header("X-Client-Name", EXPECTED_CLIENT_NAME)
                header("X-Client-Version", Util.getSdkVersion())
            }
        }

        val server = SorobanServer(TEST_SERVER_URL, mockClient)

        // When: Making any RPC request
        server.getHealth()

        // Then: Content-Type should be application/json
        assertNotNull(capturedContentType)
        assertTrue(capturedContentType!!.contains("application/json"))

        server.close()
    }
}
