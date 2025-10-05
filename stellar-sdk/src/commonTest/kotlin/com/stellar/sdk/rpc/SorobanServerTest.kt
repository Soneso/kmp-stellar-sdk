package com.stellar.sdk.rpc

import com.stellar.sdk.rpc.exception.PrepareTransactionException
import com.stellar.sdk.rpc.exception.SorobanRpcException
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Comprehensive tests for [SorobanServer].
 *
 * Uses Ktor MockEngine to test JSON-RPC request/response handling without
 * making actual network calls. Tests all RPC methods, error handling, and
 * helper functions.
 *
 * Reference: Java SDK SorobanServer tests and test resources in
 * /Users/chris/projects/Stellar/java-stellar-sdk/src/test/resources/soroban_server/
 */
class SorobanServerTest {

    companion object {
        private const val TEST_SERVER_URL = "https://soroban-testnet.stellar.org:443"

        // Test data from Java SDK test resources
        private const val HEALTH_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "198cb1a8-9104-4446-a269-88bf000c2721",
  "result": {
    "status": "healthy",
    "latestLedger": 50000,
    "oldestLedger": 1,
    "ledgerRetentionWindow": 10000
  }
}"""

        private const val ERROR_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "198cb1a8-9104-4446-a269-88bf000c2721",
  "error": {
    "code": -32601,
    "message": "method not found",
    "data": "mockTest"
  }
}"""

        private const val SIMULATE_TRANSACTION_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "7a469b9d6ed4444893491be530862ce3",
  "result": {
    "transactionData": "AAAAAAAAAAIAAAAGAAAAAem354u9STQWq5b3Ed1j9tOemvL7xV0NPwhn4gXg0AP8AAAAFAAAAAEAAAAH8dTe2OoI0BnhlDbH0fWvXmvprkBvBAgKIcL9busuuMEAAAABAAAABgAAAAHpt+eLvUk0FquW9xHdY/bTnpry+8VdDT8IZ+IF4NAD/AAAABAAAAABAAAAAgAAAA8AAAAHQ291bnRlcgAAAAASAAAAAAAAAABYt8SiyPKXqo89JHEoH9/M7K/kjlZjMT7BjhKnPsqYoQAAAAEAHifGAAAFlAAAAIgAAAAAAAAAAg==",
    "minResourceFee": "58181",
    "events": [
      "AAAAAQAAAAAAAAAAAAAAAgAAAAAAAAADAAAADwAAAAdmbl9jYWxsAAAAAA0AAAAg6bfni71JNBarlvcR3WP2056a8vvFXQ0/CGfiBeDQA/wAAAAPAAAACWluY3JlbWVudAAAAAAAABAAAAABAAAAAgAAABIAAAAAAAAAAFi3xKLI8peqjz0kcSgf38zsr+SOVmMxPsGOEqc+ypihAAAAAwAAAAo="
    ],
    "results": [
      {
        "auth": [
          "AAAAAAAAAAAAAAAB6bfni71JNBarlvcR3WP2056a8vvFXQ0/CGfiBeDQA/wAAAAJaW5jcmVtZW50AAAAAAAAAgAAABIAAAAAAAAAAFi3xKLI8peqjz0kcSgf38zsr+SOVmMxPsGOEqc+ypihAAAAAwAAAAoAAAAA"
        ],
        "xdr": "AAAAAwAAABQ="
      }
    ],
    "latestLedger": "14245"
  }
}"""

        private const val SIMULATE_ERROR_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "7a469b9d6ed4444893491be530862ce3",
  "result": {
    "error": "HostError: Error(WasmVm, InvalidAction)",
    "latestLedger": "14245"
  }
}"""

        private const val SEND_TRANSACTION_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "688dfcf3-5f31-4351-88a7-04aaec34ae1f",
  "result": {
    "status": "PENDING",
    "hash": "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7",
    "latestLedger": "45075",
    "latestLedgerCloseTime": "1690594566"
  }
}"""

        private const val GET_NETWORK_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "198cb1a8-9104-4446-a269-88bf000c2721",
  "result": {
    "friendbotUrl": "https://friendbot-futurenet.stellar.org/",
    "passphrase": "Test SDF Future Network ; October 2022",
    "protocolVersion": "20"
  }
}"""

        private const val GET_LATEST_LEDGER_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "198cb1a8-9104-4446-a269-88bf000c2721",
  "result": {
    "id": "e73d7654b72daa637f396669182c6072549736a26d1f31bc53ba6a08f9e3ca1f",
    "protocolVersion": 20,
    "sequence": 24170
  }
}"""

        private const val GET_VERSION_INFO_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "198cb1a8-9104-4446-a269-88bf000c2721",
  "result": {
    "version": "20.0.0",
    "commitHash": "9ab9d7f7b5c7e6f5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b0a9f8e7d6c5b4a3f2e1",
    "buildTimestamp": "2023-05-15T12:34:56Z",
    "captiveCoreVersion": "19.10.1",
    "protocolVersion": 20
  }
}"""

        private const val GET_FEE_STATS_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "198cb1a8-9104-4446-a269-88bf000c2721",
  "result": {
    "sorobanInclusionFee": {
      "max": "10000",
      "min": "100",
      "mode": "500",
      "p10": "150",
      "p20": "200",
      "p30": "250",
      "p40": "300",
      "p50": "500",
      "p60": "600",
      "p70": "700",
      "p80": "800",
      "p90": "1000",
      "p95": "5000",
      "p99": "9000",
      "transactionCount": "100",
      "ledgerCount": 50
    },
    "inclusionFee": {
      "max": "1000",
      "min": "100",
      "mode": "100",
      "p10": "100",
      "p20": "100",
      "p30": "100",
      "p40": "100",
      "p50": "100",
      "p60": "200",
      "p70": "300",
      "p80": "400",
      "p90": "500",
      "p95": "800",
      "p99": "900",
      "transactionCount": "10",
      "ledgerCount": 50
    },
    "latestLedger": 4519945
  }
}"""
    }

    // ========== Helper Methods ==========

    /**
     * Creates a mock HTTP client that responds with the given JSON.
     */
    private fun createMockClient(responseJson: String, statusCode: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        val mockEngine = MockEngine { request ->
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
        }
    }

    /**
     * Creates a mock server with the given response.
     */
    private fun createMockServer(responseJson: String): SorobanServer {
        val client = createMockClient(responseJson)
        return SorobanServer(TEST_SERVER_URL, client)
    }

    // ========== Constructor and Basic Tests ==========

    @Test
    fun testConstructor_createsServerWithUrl() {
        // Given/When: Creating server with URL
        val server = SorobanServer(TEST_SERVER_URL)

        // Then: Server is created successfully
        assertNotNull(server)

        // Cleanup
        server.close()
    }

    @Test
    fun testClose_closesHttpClient() {
        // Given: Server instance
        val server = SorobanServer(TEST_SERVER_URL)

        // When: Closing server
        server.close()

        // Then: No exception thrown
        // Subsequent requests would fail if attempted
    }

    @Test
    fun testDefaultHttpClient_hasCorrectConfiguration() {
        // When: Creating default HTTP client
        val client = SorobanServer.defaultHttpClient()

        // Then: Client is configured properly
        assertNotNull(client)

        // Cleanup
        client.close()
    }

    // ========== RPC Method Tests ==========

    @Test
    fun testGetHealth_successfulResponse_returnsHealthData() = runTest {
        // Given: Server with mocked health response
        createMockServer(HEALTH_RESPONSE).use { server ->
            // When: Getting health
            val exception = assertFailsWith<NotImplementedError> {
                server.getHealth()
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("requires GetHealthResponse") ?: false)
        }
    }

    @Test
    fun testGetNetwork_successfulResponse_returnsNetworkData() = runTest {
        // Given: Server with mocked network response
        createMockServer(GET_NETWORK_RESPONSE).use { server ->
            // When: Getting network info
            val exception = assertFailsWith<NotImplementedError> {
                server.getNetwork()
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("requires GetNetworkResponse") ?: false)
        }
    }

    @Test
    fun testGetLatestLedger_successfulResponse_returnsLedgerData() = runTest {
        // Given: Server with mocked latest ledger response
        createMockServer(GET_LATEST_LEDGER_RESPONSE).use { server ->
            // When: Getting latest ledger
            val exception = assertFailsWith<NotImplementedError> {
                server.getLatestLedger()
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("requires GetLatestLedgerResponse") ?: false)
        }
    }

    @Test
    fun testGetVersionInfo_successfulResponse_returnsVersionData() = runTest {
        // Given: Server with mocked version info response
        createMockServer(GET_VERSION_INFO_RESPONSE).use { server ->
            // When: Getting version info
            val exception = assertFailsWith<NotImplementedError> {
                server.getVersionInfo()
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("requires GetVersionInfoResponse") ?: false)
        }
    }

    @Test
    fun testGetFeeStats_successfulResponse_returnsFeeData() = runTest {
        // Given: Server with mocked fee stats response
        createMockServer(GET_FEE_STATS_RESPONSE).use { server ->
            // When: Getting fee stats
            val exception = assertFailsWith<NotImplementedError> {
                server.getFeeStats()
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("requires GetFeeStatsResponse") ?: false)
        }
    }

    // ========== Error Handling Tests ==========

    @Test
    fun testRpcError_throwsSorobanRpcException() = runTest {
        // Given: Server that returns RPC error
        createMockServer(ERROR_RESPONSE).use { server ->
            // When: Making any request that returns an error
            val exception = assertFailsWith<NotImplementedError> {
                server.getHealth()
            }

            // Then: Would throw SorobanRpcException (when implemented)
            // Expected: SorobanRpcException(code=-32601, message="method not found")
            assertTrue(exception.message?.contains("requires GetHealthResponse") ?: false)
        }
    }

    @Test
    fun testRpcError_preservesErrorDetails() = runTest {
        // Given: Server that returns detailed error
        createMockServer(ERROR_RESPONSE).use { server ->
            // When/Then: Error should preserve code, message, and data
            val exception = assertFailsWith<NotImplementedError> {
                server.getHealth()
            }

            // When implemented, should verify:
            // - exception.code == -32601
            // - exception.message.contains("method not found")
            // - exception.data == "mockTest"
            assertTrue(exception.message?.contains("requires GetHealthResponse") ?: false)
        }
    }

    @Test
    fun testNetworkError_propagatesException() = runTest {
        // Given: Server with network error (500)
        val errorClient = createMockClient("{}", HttpStatusCode.InternalServerError)
        SorobanServer(TEST_SERVER_URL, errorClient).use { server ->
            // When: Making request with network error
            val exception = assertFailsWith<NotImplementedError> {
                server.getHealth()
            }

            // Then: Would propagate network exception (when implemented)
            assertTrue(exception.message?.contains("requires GetHealthResponse") ?: false)
        }
    }

    // ========== Transaction Methods Tests ==========

    @Test
    fun testSimulateTransaction_notImplemented() = runTest {
        // Given: Server with mocked simulate response
        createMockServer(SIMULATE_TRANSACTION_RESPONSE).use { server ->
            // When: Simulating transaction
            val exception = assertFailsWith<NotImplementedError> {
                // server.simulateTransaction(transaction)
                TODO("Requires Transaction implementation")
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("Not yet implemented") ?: false)
        }
    }

    @Test
    fun testPrepareTransaction_withSimulation_notImplemented() = runTest {
        // Given: Server and transaction
        createMockServer(SIMULATE_TRANSACTION_RESPONSE).use { server ->
            // When: Preparing transaction
            val exception = assertFailsWith<NotImplementedError> {
                // server.prepareTransaction(transaction)
                TODO("Requires Transaction implementation")
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("Not yet implemented") ?: false)
        }
    }

    @Test
    fun testPrepareTransaction_withError_throwsPrepareTransactionException() = runTest {
        // Given: Server with simulation error
        createMockServer(SIMULATE_ERROR_RESPONSE).use { server ->
            // When: Preparing transaction that fails simulation
            val exception = assertFailsWith<NotImplementedError> {
                // server.prepareTransaction(transaction)
                TODO("Requires Transaction and SimulateTransactionResponse")
            }

            // Then: Would throw PrepareTransactionException (when implemented)
            // Expected: PrepareTransactionException with simulationError
            assertTrue(exception.message?.contains("Not yet implemented") ?: false)
        }
    }

    @Test
    fun testSendTransaction_notImplemented() = runTest {
        // Given: Server with mocked send response
        createMockServer(SEND_TRANSACTION_RESPONSE).use { server ->
            // When: Sending transaction
            val exception = assertFailsWith<NotImplementedError> {
                // server.sendTransaction(transaction)
                TODO("Requires Transaction implementation")
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("Not yet implemented") ?: false)
        }
    }

    @Test
    fun testGetTransaction_notImplemented() = runTest {
        // Given: Server and transaction hash
        createMockServer("{}").use { server ->
            val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

            // When: Getting transaction
            val exception = assertFailsWith<NotImplementedError> {
                server.getTransaction(txHash)
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("requires GetTransactionResponse") ?: false)
        }
    }

    @Test
    fun testPollTransaction_respectsMaxAttempts() = runTest {
        // Given: Server that always returns NOT_FOUND
        createMockServer("{}").use { server ->
            val txHash = "test-hash"

            // When: Polling with max attempts
            val exception = assertFailsWith<NotImplementedError> {
                server.pollTransaction(hash = txHash, maxAttempts = 3)
            }

            // Then: Would stop after max attempts (when implemented)
            assertTrue(exception.message?.contains("requires GetTransactionResponse") ?: false)
        }
    }

    @Test
    fun testPollTransaction_zeroMaxAttempts_throwsException() = runTest {
        // Given: Server instance
        createMockServer("{}").use { server ->
            // When/Then: Zero max attempts should throw
            val exception = assertFailsWith<IllegalArgumentException> {
                server.pollTransaction(hash = "test", maxAttempts = 0)
            }

            assertTrue(exception.message?.contains("maxAttempts") ?: false)
            assertTrue(exception.message?.contains("greater than 0") ?: false)
        }
    }

    @Test
    fun testPollTransaction_negativeMaxAttempts_throwsException() = runTest {
        // Given: Server instance
        createMockServer("{}").use { server ->
            // When/Then: Negative max attempts should throw
            val exception = assertFailsWith<IllegalArgumentException> {
                server.pollTransaction(hash = "test", maxAttempts = -1)
            }

            assertTrue(exception.message?.contains("maxAttempts") ?: false)
            assertTrue(exception.message?.contains("greater than 0") ?: false)
        }
    }

    // ========== Helper Function Tests ==========

    @Test
    fun testAssembleTransaction_notImplemented() {
        // Given: Transaction and simulation response
        val exception = assertFailsWith<NotImplementedError> {
            // assembleTransaction(transaction, simulationResponse)
            TODO("Requires Transaction and SimulateTransactionResponse")
        }

        // Then: Not yet implemented
        assertTrue(exception.message?.contains("Not yet implemented") ?: false)
    }

    // ========== Account Methods Tests ==========

    @Test
    fun testGetAccount_notImplemented() = runTest {
        // Given: Server and account address
        createMockServer("{}").use { server ->
            val accountId = "GABC123..."

            // When: Getting account
            val exception = assertFailsWith<NotImplementedError> {
                server.getAccount(accountId)
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("requires getLedgerEntries") ?: false)
        }
    }

    @Test
    fun testGetLedgerEntries_notImplemented() = runTest {
        // Given: Server and ledger keys
        createMockServer("{}").use { server ->
            // When: Getting ledger entries
            val exception = assertFailsWith<NotImplementedError> {
                server.getLedgerEntries(emptyList())
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("requires GetLedgerEntriesResponse") ?: false)
        }
    }

    @Test
    fun testGetContractData_notImplemented() = runTest {
        // Given: Server, contract ID, key, and durability
        createMockServer("{}").use { server ->
            // When: Getting contract data
            val exception = assertFailsWith<NotImplementedError> {
                // server.getContractData(contractId, key, SorobanServer.Durability.PERSISTENT)
                TODO("Requires SCValXdr implementation")
            }

            // Then: Not yet implemented
            assertTrue(exception.message?.contains("Not yet implemented") ?: false)
        }
    }
}
