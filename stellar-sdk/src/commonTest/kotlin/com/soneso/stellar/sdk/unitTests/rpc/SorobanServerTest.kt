package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.*
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.SorobanContractParserException
import com.soneso.stellar.sdk.horizon.exceptions.ConnectionErrorException
import com.soneso.stellar.sdk.rpc.exception.PrepareTransactionException
import com.soneso.stellar.sdk.rpc.exception.SorobanRpcException
import com.soneso.stellar.sdk.rpc.requests.GetLedgersRequest
import com.soneso.stellar.sdk.rpc.requests.GetTransactionsRequest
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.rpc.responses.SendTransactionStatus
import com.soneso.stellar.sdk.rpc.responses.SimulateTransactionResponse
import com.soneso.stellar.sdk.rpc.responses.TransactionStatus
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import io.ktor.serialization.ContentConvertException
import kotlin.coroutines.cancellation.CancellationException
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
    "ledgerRetentionWindow": 10000,
    "latestLedgerCloseTime": "1783951566",
    "oldestLedgerCloseTime": "1783345758"
  }
}"""

        // Pre-v27.1.0 shape: the ledger close-time fields are absent.
        private const val HEALTH_RESPONSE_NO_CLOSE_TIMES = """{
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
    "sequence": 24170,
    "closeTime": 1700000000,
    "headerXdr": "AAAAIKN0fRh...",
    "metadataXdr": "AAAAAwAAAA..."
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

        /** A valid contract strkey used as the subject of contract-scoped calls. */
        private const val TEST_CONTRACT_ID = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"

        /** A valid account strkey used as the subject of account-scoped calls. */
        private const val TEST_ACCOUNT_ID = "GCEZWKCA5VLDNRLN3RPRJMRZOX3Z6G5CHCGSNFHEYVXM3XOJMDS674JZ"

        /** A valid asset issuer distinct from [TEST_ACCOUNT_ID]. */
        private const val TEST_ISSUER_ID = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

        /** Hex-encoded 32-byte WASM hash. */
        private const val TEST_WASM_ID =
            "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"

        /**
         * Builds WASM bytecode that [com.soneso.stellar.sdk.contract.SorobanContractParser]
         * accepts: a contract spec section, an environment meta section and a contract meta
         * section, each tight against the next marker.
         */
        private fun parseableContractByteCode(
            functionName: String = "hello",
            metaKey: String = "rsdkver",
            metaValue: String = "22.0.0"
        ): ByteArray {
            fun encoded(block: (XdrWriter) -> Unit): ByteArray {
                val writer = XdrWriter()
                block(writer)
                return writer.toByteArray()
            }

            val specBytes = encoded { writer ->
                SCSpecEntryXdr.FunctionV0(
                    SCSpecFunctionV0Xdr(
                        doc = "",
                        name = SCSymbolXdr(functionName),
                        inputs = emptyList(),
                        outputs = emptyList()
                    )
                ).encode(writer)
            }
            val envMetaBytes = encoded { writer ->
                SCEnvMetaEntryXdr.InterfaceVersion(
                    SCEnvMetaEntryInterfaceVersionXdr(
                        protocol = Uint32Xdr(22u),
                        preRelease = Uint32Xdr(0u)
                    )
                ).encode(writer)
            }
            val metaBytes = encoded { writer ->
                SCMetaEntryXdr.V0(SCMetaV0Xdr(key = metaKey, `val` = metaValue)).encode(writer)
            }

            return "contractenvmetav0".encodeToByteArray() + envMetaBytes +
                "contractmetav0".encodeToByteArray() + metaBytes +
                "contractspecv0".encodeToByteArray() + specBytes
        }

        /** Base64 XDR of an account ledger entry carrying [sequence]. */
        private fun accountEntryXdr(accountId: String, sequence: Long): String {
            val entry = AccountEntryXdr(
                accountId = KeyPair.fromAccountId(accountId).getXdrAccountId(),
                balance = Int64Xdr(100_000_000L),
                seqNum = SequenceNumberXdr(Int64Xdr(sequence)),
                numSubEntries = Uint32Xdr(0u),
                inflationDest = null,
                flags = Uint32Xdr(0u),
                homeDomain = String32Xdr(""),
                thresholds = ThresholdsXdr(byteArrayOf(1, 0, 0, 0)),
                signers = emptyList(),
                ext = AccountEntryExtXdr.Void
            )
            return LedgerEntryDataXdr.Account(entry).toXdrBase64()
        }

        /** Base64 XDR of a contract data ledger entry holding [value]. */
        private fun contractDataEntryXdr(
            contractAddress: SCAddressXdr,
            key: SCValXdr,
            value: SCValXdr,
            durability: ContractDataDurabilityXdr = ContractDataDurabilityXdr.PERSISTENT
        ): String {
            val entry = ContractDataEntryXdr(
                ext = ExtensionPointXdr.Void,
                contract = contractAddress,
                key = key,
                durability = durability,
                `val` = value
            )
            return LedgerEntryDataXdr.ContractData(entry).toXdrBase64()
        }

        /** Base64 XDR of a contract code ledger entry holding [code]. */
        private fun contractCodeEntryXdr(code: ByteArray, wasmIdHex: String = TEST_WASM_ID): String {
            val entry = ContractCodeEntryXdr(
                ext = ContractCodeEntryExtXdr.Void,
                hash = HashXdr(Util.hexToBytes(wasmIdHex)),
                code = code
            )
            return LedgerEntryDataXdr.ContractCode(entry).toXdrBase64()
        }

        /** SCVal map shaped like a Stellar Asset Contract balance entry. */
        private fun sacBalanceValue(
            amount: BigInteger,
            authorized: Boolean,
            clawback: Boolean
        ): SCValXdr = Scv.toMap(
            linkedMapOf(
                Scv.toSymbol("amount") to Scv.toInt128(amount),
                Scv.toSymbol("authorized") to Scv.toBoolean(authorized),
                Scv.toSymbol("clawback") to Scv.toBoolean(clawback)
            )
        )

        /**
         * Builds a JSON-RPC getLedgerEntries response body carrying a single entry.
         */
        private fun ledgerEntriesResult(
            keyXdr: String,
            entryXdr: String,
            lastModifiedLedger: Long = 1234L,
            liveUntilLedger: Long? = 5678L,
            latestLedger: Long = 14245L
        ): String {
            val liveUntil = if (liveUntilLedger == null) {
                ""
            } else {
                ",\"liveUntilLedgerSeq\":$liveUntilLedger"
            }
            return """{
  "jsonrpc": "2.0",
  "id": "entries-id",
  "result": {
    "entries": [
      {
        "key": "$keyXdr",
        "xdr": "$entryXdr",
        "lastModifiedLedgerSeq": $lastModifiedLedger$liveUntil
      }
    ],
    "latestLedger": $latestLedger
  }
}"""
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

    /**
     * Creates a mock server that answers consecutive requests with consecutive
     * [responses]. Once the list is exhausted the last entry is repeated.
     *
     * The request bodies seen by the engine are appended to [capturedRequests].
     */
    private fun createSequencedMockServer(
        vararg responses: String,
        capturedRequests: MutableList<String> = mutableListOf()
    ): SorobanServer {
        var index = 0
        val mockEngine = MockEngine { request ->
            capturedRequests.add(request.body.toByteArray().decodeToString())
            val body = responses[minOf(index, responses.size - 1)]
            index++
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                    encodeDefaults = false
                })
            }
        }
        return SorobanServer(TEST_SERVER_URL, client)
    }

    /**
     * Creates a mock HTTP client whose engine throws the given failure.
     */
    private fun createThrowingMockClient(failure: Throwable): HttpClient {
        val mockEngine = MockEngine { throw failure }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    // ========== Constructor and Basic Tests ==========

    @Test
    fun testConstructor_createsServerWithUrl() = runTest {
        // Given: A client that records where the request was sent
        var requestedUrl: Url? = null
        val client = HttpClient(MockEngine { request ->
            requestedUrl = request.url
            respond(
                content = ByteReadChannel(HEALTH_RESPONSE),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }

        // When: Making a call through a server built with the URL
        val server = SorobanServer(TEST_SERVER_URL, client)
        server.getHealth()

        // Then: The constructor URL is the one actually used. Compared component-wise because
        // Url.toString() elides a port that is the protocol default, as :443 is for https.
        val url = assertNotNull(requestedUrl)
        assertEquals("https", url.protocol.name)
        assertEquals("soroban-testnet.stellar.org", url.host)
        assertEquals(443, url.port)

        server.close()
    }

    @Test
    fun testClose_closesHttpClient() {
        // Given: Server built over a client whose lifecycle is observable
        val client = createThrowingMockClient(Exception("unused"))
        val server = SorobanServer(TEST_SERVER_URL, client)

        // When: Closing the server
        server.close()

        // Then: The underlying client is closed, so its scope is no longer active
        assertFalse(
            client.coroutineContext[kotlinx.coroutines.Job]!!.isActive,
            "close() must close the underlying HTTP client"
        )
    }

    @Test
    fun testDefaultHttpClient_hasCorrectConfiguration() {
        // When: Creating default HTTP client
        val client = SorobanServer.defaultHttpClient()

        // Then: The plugins the JSON-RPC calls depend on are installed
        assertNotNull(
            client.pluginOrNull(ContentNegotiation),
            "ContentNegotiation is required to (de)serialize JSON-RPC bodies"
        )
        assertNotNull(
            client.pluginOrNull(HttpTimeout),
            "HttpTimeout is required so a stalled RPC call cannot hang indefinitely"
        )

        // Cleanup
        client.close()
    }

    // ========== RPC Method Tests ==========

    @Test
    fun testGetHealth_successfulResponse_returnsHealthData() = runTest {
        // Given: Server with a full v27.1.0+ health response (the ledger close times
        // are int64 values serialized as JSON strings).
        createMockServer(HEALTH_RESPONSE).use { server ->
            // When: Getting health
            val health = server.getHealth()

            // Then: Response is properly deserialized
            assertEquals("healthy", health.status)
            assertEquals(50000L, health.latestLedger)
            assertEquals(1L, health.oldestLedger)
            assertEquals(10000L, health.ledgerRetentionWindow)
            assertEquals(1783951566L, health.latestLedgerCloseTime)
            assertEquals(1783345758L, health.oldestLedgerCloseTime)
        }
    }

    @Test
    fun testGetHealth_closeTimesAbsent_returnsNull() = runTest {
        // Given: Server with a pre-v27.1.0 health response that omits the close-time fields
        createMockServer(HEALTH_RESPONSE_NO_CLOSE_TIMES).use { server ->
            // When: Getting health
            val health = server.getHealth()

            // Then: The close-time fields deserialize as null
            assertEquals("healthy", health.status)
            assertNull(health.latestLedgerCloseTime)
            assertNull(health.oldestLedgerCloseTime)
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
            assertEquals(1700000000L, ledger.closeTime)
            assertEquals("AAAAIKN0fRh...", ledger.headerXdr)
            assertEquals("AAAAAwAAAA...", ledger.metadataXdr)
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
    fun testResponseMissingRequiredFields_throwsIllegalArgumentException() = runTest {
        // Given: Server answering with JSON that does not match the JSON-RPC response shape
        val errorClient = createMockClient("""{"invalid": "json"}""", HttpStatusCode.OK)
        SorobanServer(TEST_SERVER_URL, errorClient).use { server ->
            // When: Making a request
            val exception = assertFailsWith<IllegalArgumentException> {
                server.getHealth()
            }

            // Then: The documented IllegalArgumentException names the method and keeps the cause
            assertTrue(
                exception.message?.contains("getHealth") == true,
                "The failing method is named, got: ${exception.message}"
            )
            assertIs<ContentConvertException>(exception.cause)
        }
    }

    @Test
    fun testResponseThatIsNotJson_throwsIllegalArgumentException() = runTest {
        // Given: Server answering with a body that is not JSON at all
        val errorClient = createMockClient("<html><body>502 Bad Gateway</body></html>", HttpStatusCode.OK)
        SorobanServer(TEST_SERVER_URL, errorClient).use { server ->
            // When/Then: The deserialization failure surfaces as IllegalArgumentException
            val exception = assertFailsWith<IllegalArgumentException> {
                server.getLatestLedger()
            }
            assertTrue(
                exception.message?.contains("getLatestLedger") == true,
                "The failing method is named, got: ${exception.message}"
            )
            assertIs<ContentConvertException>(exception.cause)
        }
    }

    @Test
    fun testEngineThrowable_wrappedAsConnectionErrorException() = runTest {
        // Given: Client whose engine reports a connectivity failure as a
        // non-Exception Throwable (Kotlin/JS HTTP engine behavior)
        val failure = Error("Fail to fetch")
        SorobanServer(TEST_SERVER_URL, createThrowingMockClient(failure)).use { server ->
            // When/Then: The failure surfaces as an Exception-typed connection error.
            // Type and message are asserted instead of instance identity because the
            // JVM coroutine machinery copies exceptions for stack-trace recovery.
            val exception = assertFailsWith<ConnectionErrorException> {
                server.getHealth()
            }
            val cause = assertIs<Error>(exception.cause)
            assertEquals("Fail to fetch", cause.message)
        }
    }

    @Test
    fun testEngineException_propagatesUnwrapped() = runTest {
        // Given: Client whose engine throws a plain Exception
        val failure = Exception("Connection refused")
        SorobanServer(TEST_SERVER_URL, createThrowingMockClient(failure)).use { server ->
            // When/Then: The failure propagates without wrapping. Class and message
            // are asserted instead of instance identity because the JVM coroutine
            // machinery copies exceptions for stack-trace recovery.
            val exception = assertFailsWith<Exception> {
                server.getHealth()
            }
            assertEquals(Exception::class, exception::class)
            assertEquals("Connection refused", exception.message)
        }
    }

    @Test
    fun testEngineCancellation_propagates() = runTest {
        // Given: Client whose engine throws a cancellation
        val client = createThrowingMockClient(CancellationException("cancelled"))
        SorobanServer(TEST_SERVER_URL, client).use { server ->
            // When/Then: Cancellation propagates instead of being wrapped
            assertFailsWith<CancellationException> {
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
    fun testPollTransaction_engineThrowable_keepsPolling() = runTest {
        // Given: First attempt fails with a non-Exception Throwable (Kotlin/JS
        // connectivity glitch), second attempt succeeds
        var attempts = 0
        val mockEngine = MockEngine {
            attempts++
            if (attempts == 1) throw Error("Fail to fetch")
            respond(
                content = ByteReadChannel(GET_TRANSACTION_RESPONSE),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        SorobanServer(TEST_SERVER_URL, client).use { server ->
            val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

            // When: Polling across the transient failure
            val response = server.pollTransaction(hash = txHash, maxAttempts = 3, sleepStrategy = { 10L })

            // Then: The glitch is retried, not surfaced
            assertEquals(GetTransactionStatus.SUCCESS, response.status)
            assertEquals(2, attempts)
        }
    }

    @Test
    fun testPollTransaction_engineCancellation_propagates() = runTest {
        // Given: a poll attempt is cancelled rather than failing with a transient glitch
        val client = createThrowingMockClient(CancellationException("cancelled"))
        SorobanServer(TEST_SERVER_URL, client).use { server ->
            val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

            // When/Then: cancellation propagates instead of being swallowed and retried
            assertFailsWith<CancellationException> {
                server.pollTransaction(hash = txHash, maxAttempts = 3, sleepStrategy = { 1L })
            }
        }
    }

    @Test
    fun testPollTransaction_allAttemptsFail_throwsLastFailure() = runTest {
        // Given: Every attempt fails with a network error
        val client = createThrowingMockClient(Exception("Connection refused"))
        SorobanServer(TEST_SERVER_URL, client).use { server ->
            val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

            // When/Then: The last per-attempt failure surfaces instead of a
            // null-response error
            val exception = assertFailsWith<Exception> {
                server.pollTransaction(hash = txHash, maxAttempts = 3, sleepStrategy = { 1L })
            }
            assertEquals(Exception::class, exception::class)
            assertEquals("Connection refused", exception.message)
        }
    }

    @Test
    fun testPollTransaction_rpcErrorEveryAttempt_throwsSorobanRpcException() = runTest {
        // Given: The server returns a JSON-RPC error on every attempt
        createMockServer(ERROR_RESPONSE).use { server ->
            val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

            // When/Then: The typed RPC exception surfaces after the attempts run out
            assertFailsWith<SorobanRpcException> {
                server.pollTransaction(hash = txHash, maxAttempts = 2, sleepStrategy = { 1L })
            }
        }
    }

    @Test
    fun testPollTransaction_responseThenFailures_returnsLastResponse() = runTest {
        // Given: First attempt returns NOT_FOUND, remaining attempts fail
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
        var attempts = 0
        val mockEngine = MockEngine {
            attempts++
            if (attempts == 1) {
                respond(
                    content = ByteReadChannel(notFoundResponse),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                throw Exception("Connection refused")
            }
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        SorobanServer(TEST_SERVER_URL, client).use { server ->
            val txHash = "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"

            // When: Polling across the later failures
            val response = server.pollTransaction(hash = txHash, maxAttempts = 3, sleepStrategy = { 1L })

            // Then: The received response is preferred over the later failures
            assertEquals(GetTransactionStatus.NOT_FOUND, response.status)
            assertEquals(3, attempts)
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

    @Test
    fun testGetAccount_accountEntry_returnsSequenceNumber() = runTest {
        // Given: A ledger entry holding an account with a known sequence number
        val ledgerKey = LedgerKeyXdr.Account(
            LedgerKeyAccountXdr(KeyPair.fromAccountId(TEST_ACCOUNT_ID).getXdrAccountId())
        )
        val response = ledgerEntriesResult(
            keyXdr = ledgerKey.toXdrBase64(),
            entryXdr = accountEntryXdr(TEST_ACCOUNT_ID, 4_294_967_296L),
            liveUntilLedger = null
        )
        createSequencedMockServer(response).use { server ->
            // When: Loading the account
            val account = server.getAccount(TEST_ACCOUNT_ID)

            // Then: The sequence number comes from the decoded account entry
            assertEquals(TEST_ACCOUNT_ID, account.accountId)
            assertEquals(4_294_967_296L, account.sequenceNumber)
            assertEquals(4_294_967_297L, account.getIncrementedSequenceNumber())
        }
    }

    @Test
    fun testGetAccount_nonAccountEntry_throwsIllegalStateException() = runTest {
        // Given: The requested account key resolves to a contract data entry
        val entryXdr = contractDataEntryXdr(
            contractAddress = Address(TEST_CONTRACT_ID).toSCAddress(),
            key = Scv.toSymbol("balance"),
            value = Scv.toInt32(1)
        )
        createSequencedMockServer(
            ledgerEntriesResult(keyXdr = "AAAAAA==", entryXdr = entryXdr)
        ).use { server ->
            // When/Then: The mismatch is reported instead of silently mis-parsed
            val exception = assertFailsWith<IllegalStateException> {
                server.getAccount(TEST_ACCOUNT_ID)
            }
            assertTrue(
                exception.message?.contains("Expected Account entry") ?: false,
                "Unexpected message: ${exception.message}"
            )
            assertTrue(
                exception.message?.contains("CONTRACT_DATA") ?: false,
                "Message should name the actual entry type: ${exception.message}"
            )
        }
    }

    @Test
    fun testGetContractData_temporaryDurability_requestsTemporaryKey() = runTest {
        // Given: A temporary contract data entry
        val contractAddress = Address(TEST_CONTRACT_ID).toSCAddress()
        val key = Scv.toSymbol("session")
        val entryXdr = contractDataEntryXdr(
            contractAddress = contractAddress,
            key = key,
            value = Scv.toInt32(7),
            durability = ContractDataDurabilityXdr.TEMPORARY
        )
        val requests = mutableListOf<String>()
        createSequencedMockServer(
            ledgerEntriesResult(keyXdr = "AAAAAA==", entryXdr = entryXdr),
            capturedRequests = requests
        ).use { server ->
            // When: Reading with TEMPORARY durability
            val result = server.getContractData(
                TEST_CONTRACT_ID,
                key,
                SorobanServer.Durability.TEMPORARY
            )

            // Then: The entry is returned and the request asked for a TEMPORARY key
            assertNotNull(result)
            assertEquals(entryXdr, result.xdr)
            assertEquals(1234L, result.lastModifiedLedger)
            assertEquals(5678L, result.liveUntilLedger)

            val expectedKey = LedgerKeyXdr.ContractData(
                LedgerKeyContractDataXdr(
                    contract = contractAddress,
                    key = key,
                    durability = ContractDataDurabilityXdr.TEMPORARY
                )
            ).toXdrBase64()
            assertEquals(1, requests.size)
            assertTrue(
                requests[0].contains(expectedKey),
                "Request should carry the TEMPORARY ledger key. Body: ${requests[0]}"
            )
        }
    }

    @Test
    fun testGetTransactions_returnsPaginatedTransactions() = runTest {
        // Given: A getTransactions response with a single transaction
        val response = """{
  "jsonrpc": "2.0",
  "id": "tx-list-id",
  "result": {
    "transactions": [
      {
        "status": "SUCCESS",
        "txHash": "aabbcc",
        "applicationOrder": 2,
        "feeBump": false,
        "envelopeXdr": "AAAA",
        "resultXdr": "BBBB",
        "resultMetaXdr": "CCCC",
        "ledger": 1500,
        "createdAt": 1690594566
      }
    ],
    "latestLedger": 1600,
    "latestLedgerCloseTimestamp": 1690595000,
    "oldestLedger": 1000,
    "oldestLedgerCloseTimestamp": 1690500000,
    "cursor": "1500-2"
  }
}"""
        val requests = mutableListOf<String>()
        createSequencedMockServer(response, capturedRequests = requests).use { server ->
            // When: Listing transactions from a start ledger
            val result = server.getTransactions(
                GetTransactionsRequest(
                    startLedger = 1500,
                    pagination = GetTransactionsRequest.Pagination(limit = 10)
                )
            )

            // Then: The paginated payload is deserialized
            assertEquals(1, result.transactions.size)
            assertEquals(TransactionStatus.SUCCESS, result.transactions[0].status)
            assertEquals("aabbcc", result.transactions[0].txHash)
            assertEquals(2, result.transactions[0].applicationOrder)
            assertEquals(1500L, result.transactions[0].ledger)
            assertEquals("1500-2", result.cursor)
            assertEquals(1600L, result.latestLedger)
            assertEquals(1000L, result.oldestLedger)

            // And: The request carried the getTransactions method and parameters
            assertTrue(requests[0].contains("\"method\":\"getTransactions\""), requests[0])
            assertTrue(requests[0].contains("\"startLedger\":1500"), requests[0])
            assertTrue(requests[0].contains("\"limit\":10"), requests[0])
        }
    }

    @Test
    fun testGetLedgers_returnsPaginatedLedgers() = runTest {
        // Given: A getLedgers response with a single ledger
        val response = """{
  "jsonrpc": "2.0",
  "id": "ledger-list-id",
  "result": {
    "ledgers": [
      {
        "hash": "ddeeff",
        "sequence": 2000,
        "ledgerCloseTime": 1690594566,
        "headerXdr": "AAAA",
        "metadataXdr": "BBBB"
      }
    ],
    "latestLedger": 2100,
    "latestLedgerCloseTime": 1690595000,
    "oldestLedger": 1000,
    "oldestLedgerCloseTime": 1690500000,
    "cursor": "2000"
  }
}"""
        val requests = mutableListOf<String>()
        createSequencedMockServer(response, capturedRequests = requests).use { server ->
            // When: Listing ledgers with cursor-based pagination
            val result = server.getLedgers(
                GetLedgersRequest(
                    pagination = GetLedgersRequest.Pagination(cursor = "1999", limit = 5)
                )
            )

            // Then: The paginated payload is deserialized
            assertEquals(1, result.ledgers.size)
            assertEquals("ddeeff", result.ledgers[0].hash)
            assertEquals(2000L, result.ledgers[0].sequence)
            assertEquals("2000", result.cursor)
            assertEquals(2100L, result.latestLedger)

            // And: The request carried the getLedgers method and cursor
            assertTrue(requests[0].contains("\"method\":\"getLedgers\""), requests[0])
            assertTrue(requests[0].contains("\"cursor\":\"1999\""), requests[0])
            assertFalse(requests[0].contains("\"startLedger\""), requests[0])
        }
    }

    @Test
    fun testRpcResponse_withoutResultAndError_throwsIllegalStateException() = runTest {
        // Given: A JSON-RPC envelope carrying neither result nor error
        createMockServer("""{"jsonrpc":"2.0","id":"no-result-id"}""").use { server ->
            // When/Then: The protocol violation is reported with the method name
            val exception = assertFailsWith<IllegalStateException> {
                server.getHealth()
            }
            assertTrue(
                exception.message?.contains("Response missing result field") ?: false,
                "Unexpected message: ${exception.message}"
            )
            assertTrue(
                exception.message?.contains("getHealth") ?: false,
                "Message should name the method: ${exception.message}"
            )
        }
    }

    // ========== getSACBalance Tests ==========

    @Test
    fun testGetSACBalance_invalidContractId_throwsIllegalArgumentException() = runTest {
        createMockServer(GET_LEDGER_ENTRIES_RESPONSE).use { server ->
            val exception = assertFailsWith<IllegalArgumentException> {
                server.getSACBalance(TEST_ACCOUNT_ID, Asset.createNativeAsset(), Network.TESTNET)
            }
            assertTrue(
                exception.message?.contains("Invalid contract ID") ?: false,
                "Unexpected message: ${exception.message}"
            )
        }
    }

    @Test
    fun testGetSACBalance_noEntries_returnsNullBalanceEntry() = runTest {
        // Given: The balance ledger key resolves to nothing
        createMockServer(GET_LEDGER_ENTRIES_RESPONSE).use { server ->
            // When: Reading the balance
            val response = server.getSACBalance(
                TEST_CONTRACT_ID,
                Asset.createNativeAsset(),
                Network.TESTNET
            )

            // Then: The latest ledger is still reported and the balance is absent
            assertEquals(14245L, response.latestLedger)
            assertNull(response.balanceEntry)
        }
    }

    @Test
    fun testGetSACBalance_balanceEntry_returnsParsedFields() = runTest {
        // Given: A contract data entry shaped like a SAC balance
        val asset = Asset.createNonNativeAsset("USDC", TEST_ISSUER_ID)
        val assetContractAddress = Address(asset.getContractId(Network.TESTNET)).toSCAddress()
        val balanceKey = Scv.toVec(
            listOf(Scv.toSymbol("Balance"), Address(TEST_CONTRACT_ID).toSCVal())
        )
        val entryXdr = contractDataEntryXdr(
            contractAddress = assetContractAddress,
            key = balanceKey,
            value = sacBalanceValue(
                amount = BigInteger.parseString("170141183460469231731687303715884105727"),
                authorized = true,
                clawback = false
            )
        )
        val requests = mutableListOf<String>()
        createSequencedMockServer(
            ledgerEntriesResult(
                keyXdr = "AAAAAA==",
                entryXdr = entryXdr,
                lastModifiedLedger = 900L,
                liveUntilLedger = 12_000L
            ),
            capturedRequests = requests
        ).use { server ->
            // When: Reading the balance
            val response = server.getSACBalance(TEST_CONTRACT_ID, asset, Network.TESTNET)

            // Then: Amount, flags and TTL metadata come from the decoded entry
            val balance = assertNotNull(response.balanceEntry)
            assertEquals("170141183460469231731687303715884105727", balance.amount)
            assertTrue(balance.authorized)
            assertFalse(balance.clawback)
            assertEquals(900L, balance.lastModifiedLedgerSeq)
            assertEquals(12_000L, balance.liveUntilLedgerSeq)
            assertEquals(14245L, response.latestLedger)

            // And: The request asked for the asset contract's Balance key
            val expectedKey = LedgerKeyXdr.ContractData(
                LedgerKeyContractDataXdr(
                    contract = assetContractAddress,
                    key = balanceKey,
                    durability = ContractDataDurabilityXdr.PERSISTENT
                )
            ).toXdrBase64()
            assertTrue(
                requests[0].contains(expectedKey),
                "Request should carry the SAC balance key. Body: ${requests[0]}"
            )
        }
    }

    @Test
    fun testGetSACBalance_nonContractDataEntry_throwsIllegalStateException() = runTest {
        // Given: The balance key resolves to an account entry
        createSequencedMockServer(
            ledgerEntriesResult(
                keyXdr = "AAAAAA==",
                entryXdr = accountEntryXdr(TEST_ACCOUNT_ID, 1L)
            )
        ).use { server ->
            // When/Then: The mismatch is reported
            val exception = assertFailsWith<IllegalStateException> {
                server.getSACBalance(TEST_CONTRACT_ID, Asset.createNativeAsset(), Network.TESTNET)
            }
            assertTrue(
                exception.message?.contains("Expected ContractData entry") ?: false,
                "Unexpected message: ${exception.message}"
            )
            assertTrue(
                exception.message?.contains("ACCOUNT") ?: false,
                "Message should name the actual entry type: ${exception.message}"
            )
        }
    }

    // ========== Contract Code Loading Tests ==========

    @Test
    fun testLoadContractCodeForWasmId_returnsCodeEntry() = runTest {
        // Given: A contract code entry for the requested WASM hash
        val code = byteArrayOf(0, 97, 115, 109, 1, 0, 0, 0)
        val requests = mutableListOf<String>()
        createSequencedMockServer(
            ledgerEntriesResult(keyXdr = "AAAAAA==", entryXdr = contractCodeEntryXdr(code)),
            capturedRequests = requests
        ).use { server ->
            // When: Loading the code
            val entry = server.loadContractCodeForWasmId(TEST_WASM_ID)

            // Then: The decoded entry carries the bytecode and hash
            val codeEntry = assertNotNull(entry)
            assertEquals(code.toList(), codeEntry.code.toList())
            assertEquals(Util.hexToBytes(TEST_WASM_ID).toList(), codeEntry.hash.value.toList())

            // And: The request asked for the matching contract code key
            val expectedKey = LedgerKeyXdr.ContractCode(
                LedgerKeyContractCodeXdr(hash = HashXdr(Util.hexToBytes(TEST_WASM_ID)))
            ).toXdrBase64()
            assertTrue(
                requests[0].contains(expectedKey),
                "Request should carry the contract code key. Body: ${requests[0]}"
            )
        }
    }

    @Test
    fun testLoadContractCodeForWasmId_wrongLength_rejectsBeforeRequest() = runTest {
        // Given: A WASM ID that is not a 32-byte hash. Without the length check the failure
        // still happens, but only once the fixed-opaque writer rejects the short hash, so the
        // message must name the WASM ID rather than the XDR field.
        val requests = mutableListOf<String>()
        createSequencedMockServer(GET_LEDGER_ENTRIES_RESPONSE, capturedRequests = requests).use { server ->
            // When: Loading the code
            val exception = assertFailsWith<IllegalArgumentException> {
                server.loadContractCodeForWasmId("abc123")
            }

            // Then: The length is named and no request went out
            assertTrue(
                exception.message?.contains("WASM ID must be 64 hex characters, got 6") ?: false,
                "Unexpected message: ${exception.message}"
            )
            assertTrue(requests.isEmpty(), "No request should be issued for an invalid WASM ID")
        }
    }

    @Test
    fun testLoadContractCodeForWasmId_nonHexId_rejectsBeforeRequest() = runTest {
        // Given: A 64-character WASM ID made of sign pairs, which a radix parse would
        // silently decode to a 32-byte hash of 0xff bytes and query the wrong ledger key
        val requests = mutableListOf<String>()
        createSequencedMockServer(GET_LEDGER_ENTRIES_RESPONSE, capturedRequests = requests).use { server ->
            val exception = assertFailsWith<IllegalArgumentException> {
                server.loadContractCodeForWasmId("-1".repeat(32))
            }

            assertTrue(
                exception.message?.contains("0-9 and a-f") ?: false,
                "Unexpected message: ${exception.message}"
            )
            assertTrue(requests.isEmpty(), "No request should be issued for an invalid WASM ID")
        }
    }

    @Test
    fun testLoadContractInfoForWasmId_wrongLength_rejectsBeforeRequest() = runTest {
        // The info path delegates to the code path, so it inherits the same check.
        val requests = mutableListOf<String>()
        createSequencedMockServer(GET_LEDGER_ENTRIES_RESPONSE, capturedRequests = requests).use { server ->
            val exception = assertFailsWith<IllegalArgumentException> {
                server.loadContractInfoForWasmId("abc123")
            }
            assertTrue(
                exception.message?.contains("WASM ID must be 64 hex characters, got 6") ?: false,
                "Unexpected message: ${exception.message}"
            )
            assertTrue(requests.isEmpty(), "No request should be issued for an invalid WASM ID")
        }
    }

    @Test
    fun testLoadContractCodeForWasmId_noEntries_returnsNull() = runTest {
        createMockServer(GET_LEDGER_ENTRIES_RESPONSE).use { server ->
            assertNull(server.loadContractCodeForWasmId(TEST_WASM_ID))
        }
    }

    @Test
    fun testLoadContractCodeForWasmId_nonContractCodeEntry_throwsIllegalStateException() = runTest {
        // Given: The contract code key resolves to an account entry
        createSequencedMockServer(
            ledgerEntriesResult(
                keyXdr = "AAAAAA==",
                entryXdr = accountEntryXdr(TEST_ACCOUNT_ID, 1L)
            )
        ).use { server ->
            val exception = assertFailsWith<IllegalStateException> {
                server.loadContractCodeForWasmId(TEST_WASM_ID)
            }
            assertTrue(
                exception.message?.contains("Expected ContractCode entry") ?: false,
                "Unexpected message: ${exception.message}"
            )
        }
    }

    @Test
    fun testLoadContractCodeForContractId_followsInstanceToCode() = runTest {
        // Given: The contract instance points at a WASM hash whose code entry exists
        val code = byteArrayOf(0, 97, 115, 109, 1, 0, 0, 0)
        val instanceValue = SCValXdr.Instance(
            SCContractInstanceXdr(
                executable = ContractExecutableXdr.WasmHash(HashXdr(Util.hexToBytes(TEST_WASM_ID))),
                storage = null
            )
        )
        val instanceEntry = contractDataEntryXdr(
            contractAddress = Address(TEST_CONTRACT_ID).toSCAddress(),
            key = Scv.toLedgerKeyContractInstance(),
            value = instanceValue
        )
        val requests = mutableListOf<String>()
        createSequencedMockServer(
            ledgerEntriesResult(keyXdr = "AAAAAA==", entryXdr = instanceEntry),
            ledgerEntriesResult(keyXdr = "AAAAAA==", entryXdr = contractCodeEntryXdr(code)),
            capturedRequests = requests
        ).use { server ->
            // When: Loading the code by contract ID
            val entry = server.loadContractCodeForContractId(TEST_CONTRACT_ID)

            // Then: The bytecode from the second lookup is returned
            val codeEntry = assertNotNull(entry)
            assertEquals(code.toList(), codeEntry.code.toList())

            // And: Two ledger entry lookups were made, the second keyed by the WASM hash
            assertEquals(2, requests.size)
            val expectedCodeKey = LedgerKeyXdr.ContractCode(
                LedgerKeyContractCodeXdr(hash = HashXdr(Util.hexToBytes(TEST_WASM_ID)))
            ).toXdrBase64()
            assertTrue(
                requests[1].contains(expectedCodeKey),
                "Second request should key on the WASM hash. Body: ${requests[1]}"
            )
        }
    }

    @Test
    fun testLoadContractCodeForContractId_noInstance_returnsNull() = runTest {
        createMockServer(GET_LEDGER_ENTRIES_RESPONSE).use { server ->
            assertNull(server.loadContractCodeForContractId(TEST_CONTRACT_ID))
        }
    }

    @Test
    fun testLoadContractCodeForContractId_nonContractDataEntry_throwsIllegalStateException() =
        runTest {
            createSequencedMockServer(
                ledgerEntriesResult(
                    keyXdr = "AAAAAA==",
                    entryXdr = accountEntryXdr(TEST_ACCOUNT_ID, 1L)
                )
            ).use { server ->
                val exception = assertFailsWith<IllegalStateException> {
                    server.loadContractCodeForContractId(TEST_CONTRACT_ID)
                }
                assertTrue(
                    exception.message?.contains("Expected ContractData entry") ?: false,
                    "Unexpected message: ${exception.message}"
                )
            }
        }

    @Test
    fun testLoadContractCodeForContractId_valueNotInstance_throwsIllegalStateException() = runTest {
        // Given: The contract instance key resolves to a non-instance SCVal
        val entry = contractDataEntryXdr(
            contractAddress = Address(TEST_CONTRACT_ID).toSCAddress(),
            key = Scv.toLedgerKeyContractInstance(),
            value = Scv.toInt32(5)
        )
        createSequencedMockServer(
            ledgerEntriesResult(keyXdr = "AAAAAA==", entryXdr = entry)
        ).use { server ->
            val exception = assertFailsWith<IllegalStateException> {
                server.loadContractCodeForContractId(TEST_CONTRACT_ID)
            }
            assertTrue(
                exception.message?.contains("Expected Instance SCVal") ?: false,
                "Unexpected message: ${exception.message}"
            )
        }
    }

    @Test
    fun testLoadContractCodeForContractId_stellarAssetExecutable_returnsNull() = runTest {
        // Given: The contract instance is a Stellar Asset Contract, which has no WASM
        val instanceValue = SCValXdr.Instance(
            SCContractInstanceXdr(
                executable = ContractExecutableXdr.Void,
                storage = null
            )
        )
        val entry = contractDataEntryXdr(
            contractAddress = Address(TEST_CONTRACT_ID).toSCAddress(),
            key = Scv.toLedgerKeyContractInstance(),
            value = instanceValue
        )
        val requests = mutableListOf<String>()
        createSequencedMockServer(
            ledgerEntriesResult(keyXdr = "AAAAAA==", entryXdr = entry),
            capturedRequests = requests
        ).use { server ->
            // When/Then: No code is returned and no second lookup is attempted
            assertNull(server.loadContractCodeForContractId(TEST_CONTRACT_ID))
            assertEquals(1, requests.size)
        }
    }

    // ========== Contract Info Loading Tests ==========

    @Test
    fun testLoadContractInfoForWasmId_parsesContractMetadata() = runTest {
        createSequencedMockServer(
            ledgerEntriesResult(
                keyXdr = "AAAAAA==",
                entryXdr = contractCodeEntryXdr(parseableContractByteCode(functionName = "increment"))
            )
        ).use { server ->
            // When: Loading contract info by WASM ID
            val info = assertNotNull(server.loadContractInfoForWasmId(TEST_WASM_ID))

            // Then: Env meta, spec entries and meta entries come from the bytecode
            assertEquals(22UL, info.envInterfaceVersion)
            assertEquals(1, info.funcs.size)
            assertEquals("increment", info.funcs[0].name.value)
            assertEquals("22.0.0", info.metaEntries["rsdkver"])
        }
    }

    @Test
    fun testLoadContractInfoForWasmId_missingCode_returnsNull() = runTest {
        createMockServer(GET_LEDGER_ENTRIES_RESPONSE).use { server ->
            assertNull(server.loadContractInfoForWasmId(TEST_WASM_ID))
        }
    }

    @Test
    fun testLoadContractInfoForWasmId_unparseableCode_throwsParserException() = runTest {
        createSequencedMockServer(
            ledgerEntriesResult(
                keyXdr = "AAAAAA==",
                entryXdr = contractCodeEntryXdr(ByteArray(64) { it.toByte() })
            )
        ).use { server ->
            assertFailsWith<SorobanContractParserException> {
                server.loadContractInfoForWasmId(TEST_WASM_ID)
            }
        }
    }

    @Test
    fun testLoadContractInfoForContractId_parsesContractMetadata() = runTest {
        val instanceValue = SCValXdr.Instance(
            SCContractInstanceXdr(
                executable = ContractExecutableXdr.WasmHash(HashXdr(Util.hexToBytes(TEST_WASM_ID))),
                storage = null
            )
        )
        val instanceEntry = contractDataEntryXdr(
            contractAddress = Address(TEST_CONTRACT_ID).toSCAddress(),
            key = Scv.toLedgerKeyContractInstance(),
            value = instanceValue
        )
        createSequencedMockServer(
            ledgerEntriesResult(keyXdr = "AAAAAA==", entryXdr = instanceEntry),
            ledgerEntriesResult(
                keyXdr = "AAAAAA==",
                entryXdr = contractCodeEntryXdr(parseableContractByteCode(functionName = "transfer"))
            )
        ).use { server ->
            // When: Loading contract info by contract ID
            val info = assertNotNull(server.loadContractInfoForContractId(TEST_CONTRACT_ID))

            // Then: The spec parsed from the referenced WASM is returned
            assertEquals(22UL, info.envInterfaceVersion)
            assertEquals(1, info.funcs.size)
            assertEquals("transfer", info.funcs[0].name.value)
            assertEquals("22.0.0", info.metaEntries["rsdkver"])
        }
    }

    @Test
    fun testLoadContractInfoForContractId_missingContract_returnsNull() = runTest {
        createMockServer(GET_LEDGER_ENTRIES_RESPONSE).use { server ->
            assertNull(server.loadContractInfoForContractId(TEST_CONTRACT_ID))
        }
    }

    // ========== assembleTransaction Validation Tests ==========

    @Test
    fun testAssembleTransaction_nonSorobanTransaction_throwsIllegalArgumentException() = runTest {
        // Given: A classic transaction with no Soroban operation or data
        val sourceKeypair = KeyPair.random()
        val transaction = TransactionBuilder(
            Account(sourceKeypair.getAccountId(), 1L),
            Network.TESTNET
        )
            .addOperation(BumpSequenceOperation(12345678L))
            .setTimeout(300)
            .setBaseFee(100)
            .build()

        // When/Then: Assembly is refused
        val exception = assertFailsWith<IllegalArgumentException> {
            assembleTransaction(transaction, SimulateTransactionResponse(minResourceFee = 100L))
        }
        assertTrue(
            exception.message?.contains("unsupported transaction") ?: false,
            "Unexpected message: ${exception.message}"
        )
    }

    @Test
    fun testAssembleTransaction_missingResults_throwsIllegalArgumentException() = runTest {
        // Given: An InvokeHostFunction transaction and a simulation with no results
        val transaction = createTestTransaction()

        // When/Then: Assembly is refused because auth entries cannot be resolved
        val exception = assertFailsWith<IllegalArgumentException> {
            assembleTransaction(transaction, SimulateTransactionResponse(minResourceFee = 100L))
        }
        assertTrue(
            exception.message?.contains("must contain exactly one element") ?: false,
            "Unexpected message: ${exception.message}"
        )
    }

    @Test
    fun testAssembleTransaction_multipleResults_throwsIllegalArgumentException() = runTest {
        // Given: A simulation carrying two host function results for one operation
        val transaction = createTestTransaction()
        val simulation = SimulateTransactionResponse(
            minResourceFee = 100L,
            results = listOf(
                SimulateTransactionResponse.SimulateHostFunctionResult(xdr = "AAAAAwAAABQ="),
                SimulateTransactionResponse.SimulateHostFunctionResult(xdr = "AAAAAwAAABQ=")
            )
        )

        // When/Then: Assembly is refused
        val exception = assertFailsWith<IllegalArgumentException> {
            assembleTransaction(transaction, simulation)
        }
        assertTrue(
            exception.message?.contains("must contain exactly one element") ?: false,
            "Unexpected message: ${exception.message}"
        )
    }

    @Test
    fun testPollTransaction_defaultAttempts_returnsFinalState() = runTest {
        // Given: The first poll already sees a final state
        val requests = mutableListOf<String>()
        createSequencedMockServer(
            GET_TRANSACTION_RESPONSE,
            capturedRequests = requests
        ).use { server ->
            // When: Polling with the default attempt count and sleep strategy
            val response = server.pollTransaction(
                "a4721e2a61e9a6b3c6c2e5c0d4c0a5f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7"
            )

            // Then: The final state is returned after a single request, without sleeping
            assertEquals(GetTransactionStatus.SUCCESS, response.status)
            assertEquals(1, requests.size)
        }
    }
}
