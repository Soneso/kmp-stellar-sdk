package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.*
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.rpc.exception.PrepareTransactionException
import com.soneso.stellar.sdk.rpc.exception.SorobanRpcException
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.rpc.responses.SendTransactionStatus
import com.soneso.stellar.sdk.xdr.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import io.ktor.serialization.JsonConvertException
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
    "latestLedger": 14245
  }
}"""

        private const val SIMULATE_ERROR_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "7a469b9d6ed4444893491be530862ce3",
  "result": {
    "error": "HostError: Error(WasmVm, InvalidAction)",
    "latestLedger": 14245
  }
}"""

        private const val SEND_TRANSACTION_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "688dfcf3-5f31-4351-88a7-04aaec34ae1f",
  "result": {
    "status": "PENDING",
    "hash": "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7",
    "latestLedger": 45075,
    "latestLedgerCloseTime": 1690594566
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

        private const val GET_TRANSACTION_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "198cb1a8-9104-4446-a269-88bf000c2721",
  "result": {
    "status": "SUCCESS",
    "latestLedger": 14245,
    "latestLedgerCloseTime": 1690594566,
    "oldestLedger": 1000,
    "oldestLedgerCloseTime": 1690500000
  }
}"""

        private const val GET_LEDGER_ENTRIES_RESPONSE = """{
  "jsonrpc": "2.0",
  "id": "198cb1a8-9104-4446-a269-88bf000c2721",
  "result": {
    "entries": null,
    "latestLedger": 14245
  }
}"""

        /**
         * Creates a simple test transaction for Soroban testing.
         *
         * The transaction includes:
         * - An InvokeHostFunctionOperation
         * - Minimal sorobanData to make it a valid Soroban transaction
         * - Empty auth entries (to be filled by simulation)
         */
        private suspend fun createTestTransaction(): Transaction {
            val sourceKeypair = KeyPair.random()
            val sourceAccount = Account(sourceKeypair.getAccountId(), 1L)

            // Create a simple contract ID (32 zero bytes)
            val contractHash = ByteArray(32)
            val contractId = ContractIDXdr(HashXdr(contractHash))

            // Create minimal soroban data to make this a valid Soroban transaction
            val minimalSorobanData = SorobanTransactionDataXdr(
                ext = SorobanTransactionDataExtXdr.Void,
                resources = SorobanResourcesXdr(
                    footprint = LedgerFootprintXdr(
                        readOnly = emptyList(),
                        readWrite = emptyList()
                    ),
                    instructions = Uint32Xdr(0u),
                    diskReadBytes = Uint32Xdr(0u),
                    writeBytes = Uint32Xdr(0u)
                ),
                resourceFee = Int64Xdr(0L)
            )

            val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
                .addOperation(
                    InvokeHostFunctionOperation(
                        hostFunction = HostFunctionXdr.InvokeContract(
                            InvokeContractArgsXdr(
                                contractAddress = SCAddressXdr.ContractId(contractId),
                                functionName = SCSymbolXdr("test"),
                                args = emptyList()
                            )
                        ),
                        auth = emptyList() // Empty auth entries - to be filled by simulation
                    )
                )
                .setTimeout(300)
                .setBaseFee(100)
                .setSorobanData(minimalSorobanData)
                .build()

            transaction.sign(sourceKeypair)
            return transaction
        }
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
            val health = server.getHealth()

            // Then: Response is properly deserialized
            assertEquals("healthy", health.status)
            assertEquals(50000L, health.latestLedger)
            assertEquals(1L, health.oldestLedger)
            assertEquals(10000L, health.ledgerRetentionWindow)
        }
    }

    @Test
    fun testGetNetwork_successfulResponse_returnsNetworkData() = runTest {
        // Given: Server with mocked network response
        createMockServer(GET_NETWORK_RESPONSE).use { server ->
            // When: Getting network info
            val network = server.getNetwork()

            // Then: Response is properly deserialized
            assertEquals("https://friendbot-futurenet.stellar.org/", network.friendbotUrl)
            assertEquals("Test SDF Future Network ; October 2022", network.passphrase)
            assertEquals(20, network.protocolVersion)
        }
    }

    @Test
    fun testGetLatestLedger_successfulResponse_returnsLedgerData() = runTest {
        // Given: Server with mocked latest ledger response
        createMockServer(GET_LATEST_LEDGER_RESPONSE).use { server ->
            // When: Getting latest ledger
            val ledger = server.getLatestLedger()

            // Then: Response is properly deserialized
            assertEquals("e73d7654b72daa637f396669182c6072549736a26d1f31bc53ba6a08f9e3ca1f", ledger.id)
            assertEquals(20, ledger.protocolVersion)
            assertEquals(24170L, ledger.sequence)
        }
    }

    @Test
    fun testGetVersionInfo_successfulResponse_returnsVersionData() = runTest {
        // Given: Server with mocked version info response
        createMockServer(GET_VERSION_INFO_RESPONSE).use { server ->
            // When: Getting version info
            val version = server.getVersionInfo()

            // Then: Response is properly deserialized
            assertEquals("20.0.0", version.version)
            assertEquals("9ab9d7f7b5c7e6f5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b0a9f8e7d6c5b4a3f2e1", version.commitHash)
            assertEquals("2023-05-15T12:34:56Z", version.buildTimestamp)
            assertEquals("19.10.1", version.captiveCoreVersion)
            assertEquals(20, version.protocolVersion)
        }
    }

    @Test
    fun testGetFeeStats_successfulResponse_returnsFeeData() = runTest {
        // Given: Server with mocked fee stats response
        createMockServer(GET_FEE_STATS_RESPONSE).use { server ->
            // When: Getting fee stats
            val feeStats = server.getFeeStats()

            // Then: Response is properly deserialized
            assertEquals(4519945L, feeStats.latestLedger)

            // Soroban inclusion fee stats
            assertEquals(10000L, feeStats.sorobanInclusionFee.max)
            assertEquals(100L, feeStats.sorobanInclusionFee.min)
            assertEquals(500L, feeStats.sorobanInclusionFee.mode)
            assertEquals(500L, feeStats.sorobanInclusionFee.p50)
            assertEquals(100L, feeStats.sorobanInclusionFee.transactionCount)
            assertEquals(50L, feeStats.sorobanInclusionFee.ledgerCount)

            // Regular inclusion fee stats
            assertEquals(1000L, feeStats.inclusionFee.max)
            assertEquals(100L, feeStats.inclusionFee.min)
            assertEquals(100L, feeStats.inclusionFee.mode)
            assertEquals(100L, feeStats.inclusionFee.p50)
            assertEquals(10L, feeStats.inclusionFee.transactionCount)
            assertEquals(50L, feeStats.inclusionFee.ledgerCount)
        }
    }

    // ========== Error Handling Tests ==========

    @Test
    fun testRpcError_throwsSorobanRpcException() = runTest {
        // Given: Server that returns RPC error
        createMockServer(ERROR_RESPONSE).use { server ->
            // When: Making any request that returns an error
            val exception = assertFailsWith<SorobanRpcException> {
                server.getHealth()
            }

            // Then: Exception contains error details
            assertEquals(-32601, exception.code)
            assertTrue(exception.message?.contains("method not found") ?: false)
            assertEquals("mockTest", exception.data)
        }
    }

    @Test
    fun testRpcError_preservesErrorDetails() = runTest {
        // Given: Server that returns detailed error
        createMockServer(ERROR_RESPONSE).use { server ->
            // When/Then: Error should preserve code, message, and data
            val exception = assertFailsWith<SorobanRpcException> {
                server.getHealth()
            }

            assertEquals(-32601, exception.code)
            assertTrue(exception.message?.contains("method not found") ?: false)
            assertEquals("mockTest", exception.data)
        }
    }

    @Test
    fun testNetworkError_propagatesException() = runTest {
        // Given: Server with malformed response (missing required fields)
        val errorClient = createMockClient("""{"invalid": "json"}""", HttpStatusCode.OK)
        SorobanServer(TEST_SERVER_URL, errorClient).use { server ->
            // When: Making request with malformed response
            // Then: JsonConvertException is thrown for missing required fields
            assertFailsWith<JsonConvertException> {
                server.getHealth()
            }
        }
    }

    // ========== Transaction Methods Tests ==========

    @Test
    fun testSimulateTransaction_successfulResponse_returnsSimulationData() = runTest {
        // Given: Server with mocked simulate response and test transaction
        createMockServer(SIMULATE_TRANSACTION_RESPONSE).use { server ->
            val transaction = createTestTransaction()

            // When: Simulating transaction
            val simulation = server.simulateTransaction(transaction)

            // Then: Response is properly deserialized
            assertNotNull(simulation.transactionData)
            assertEquals(58181L, simulation.minResourceFee)
            assertEquals(1, simulation.events?.size)
            assertEquals(1, simulation.results?.size)
            assertEquals(14245L, simulation.latestLedger)
        }
    }

    @Test
    fun testPrepareTransaction_withSimulation_preparesTransaction() = runTest {
        // Given: Server and transaction
        createMockServer(SIMULATE_TRANSACTION_RESPONSE).use { server ->
            val transaction = createTestTransaction()
            val originalFee = transaction.fee

            // When: Preparing transaction
            val prepared = server.prepareTransaction(transaction)

            // Then: Transaction is prepared with updated fee and soroban data
            assertNotNull(prepared)
            assertNotNull(prepared.sorobanData)
            assertTrue(prepared.fee > originalFee) // Fee increased with resource fee
            assertEquals(1, prepared.operations.size) // Should have exactly 1 operation

            // Verify the operation has auth entries from simulation
            val operation = prepared.operations[0] as InvokeHostFunctionOperation
            assertEquals(1, operation.auth.size) // Auth entry added from simulation
        }
    }

    @Test
    fun testPrepareTransaction_withError_throwsPrepareTransactionException() = runTest {
        // Given: Server with simulation error
        createMockServer(SIMULATE_ERROR_RESPONSE).use { server ->
            val transaction = createTestTransaction()

            // When: Preparing transaction that fails simulation
            val exception = assertFailsWith<PrepareTransactionException> {
                server.prepareTransaction(transaction)
            }

            // Then: PrepareTransactionException with simulationError
            assertTrue(exception.message?.contains("Simulation failed") ?: false)
            assertEquals("HostError: Error(WasmVm, InvalidAction)", exception.simulationError)
        }
    }

    @Test
    fun testSendTransaction_successfulResponse_returnsSendData() = runTest {
        // Given: Server with mocked send response
        createMockServer(SEND_TRANSACTION_RESPONSE).use { server ->
            val transaction = createTestTransaction()

            // When: Sending transaction
            val response = server.sendTransaction(transaction)

            // Then: Response is properly deserialized
            assertEquals(SendTransactionStatus.PENDING, response.status)
            assertEquals("a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7", response.hash)
            assertEquals(45075L, response.latestLedger)
            assertEquals(1690594566L, response.latestLedgerCloseTime)
        }
    }

    @Test
    fun testGetTransaction_successfulResponse_returnsTransactionData() = runTest {
        // Given: Server and transaction hash
        createMockServer(GET_TRANSACTION_RESPONSE).use { server ->
            val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

            // When: Getting transaction
            val response = server.getTransaction(txHash)

            // Then: Response is properly deserialized
            assertEquals(GetTransactionStatus.SUCCESS, response.status)
            assertEquals(14245L, response.latestLedger)
            assertEquals(1690594566L, response.latestLedgerCloseTime)
            assertEquals(1000L, response.oldestLedger)
            assertEquals(1690500000L, response.oldestLedgerCloseTime)
        }
    }

    @Test
    fun testPollTransaction_respectsMaxAttempts() = runTest {
        // Given: Server that always returns NOT_FOUND
        val notFoundResponse = """{
  "jsonrpc": "2.0",
  "id": "test-id",
  "result": {
    "status": "NOT_FOUND",
    "latestLedger": 14245,
    "latestLedgerCloseTime": 1690594566,
    "oldestLedger": 1000,
    "oldestLedgerCloseTime": 1690500000
  }
}"""
        createMockServer(notFoundResponse).use { server ->
            val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

            // When: Polling with max attempts
            val response = server.pollTransaction(hash = txHash, maxAttempts = 3, sleepStrategy = { 10L })

            // Then: Stops after max attempts and returns NOT_FOUND
            assertEquals(GetTransactionStatus.NOT_FOUND, response.status)
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
    fun testAssembleTransaction_assemblesCorrectly() = runTest {
        // Given: Transaction and simulation response
        createMockServer(SIMULATE_TRANSACTION_RESPONSE).use { server ->
            val transaction = createTestTransaction()
            val simulation = server.simulateTransaction(transaction)

            // When: Assembling transaction
            val assembled = assembleTransaction(transaction, simulation)

            // Then: Transaction is assembled correctly
            assertNotNull(assembled)
            assertNotNull(assembled.sorobanData)
            assertTrue(assembled.fee > transaction.fee)
            assertEquals(1, assembled.operations.size) // Should have exactly 1 operation

            // Verify the operation has auth entries from simulation
            val operation = assembled.operations[0] as InvokeHostFunctionOperation
            assertEquals(1, operation.auth.size) // Auth entry added from simulation
        }
    }

    // ========== Account Methods Tests ==========

    @Test
    fun testGetAccount_notFound_throwsAccountNotFoundException() = runTest {
        // Given: Server and valid account address that doesn't exist
        createMockServer(GET_LEDGER_ENTRIES_RESPONSE).use { server ->
            // Use a valid Stellar address format (56 characters, valid checksum)
            val accountId = "GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGSNFHEYVXM3XOJMDS674JZ"

            // When: Getting account that doesn't exist (entries is null in mock response)
            val exception = assertFailsWith<com.soneso.stellar.sdk.rpc.exception.AccountNotFoundException> {
                server.getAccount(accountId)
            }

            // Then: AccountNotFoundException is thrown with account ID
            assertTrue(exception.message?.contains(accountId) ?: false)
        }
    }

    @Test
    fun testGetLedgerEntries_emptyKeys_throwsException() = runTest {
        // Given: Server and empty keys list
        createMockServer("{}").use { server ->
            // When: Getting ledger entries with empty keys
            val exception = assertFailsWith<IllegalArgumentException> {
                server.getLedgerEntries(emptyList())
            }

            // Then: Exception is thrown
            assertTrue(exception.message?.contains("At least one key must be provided") ?: false)
        }
    }

    @Test
    fun testGetContractData_withValidParams_callsGetLedgerEntries() = runTest {
        // Given: Server, contract ID, key, and durability
        createMockServer(GET_LEDGER_ENTRIES_RESPONSE).use { server ->
            val contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
            val key = SCValXdr.Sym(SCSymbolXdr("balance"))

            // When: Getting contract data
            val result = server.getContractData(contractId, key, SorobanServer.Durability.PERSISTENT)

            // Then: Returns null (no entries in mock response)
            assertNull(result)
        }
    }
}
