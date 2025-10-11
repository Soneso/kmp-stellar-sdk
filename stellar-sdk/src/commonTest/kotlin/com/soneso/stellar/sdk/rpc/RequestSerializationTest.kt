package com.soneso.stellar.sdk.rpc

import com.soneso.stellar.sdk.rpc.requests.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Comprehensive tests for request model serialization.
 *
 * Tests all request models to ensure proper JSON serialization with correct
 * field names (@SerialName annotations), validation, and structure.
 */
class RequestSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = false
    }

    // JSON with encodeDefaults=true for testing required fields
    private val jsonWithDefaults = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = true
    }

    // ========== SorobanRpcRequest Tests ==========

    @Test
    fun testSorobanRpcRequest_serializesWithCorrectFieldNames() {
        // Given: RPC request with parameters
        val request = SorobanRpcRequest(
            id = "test-123",
            method = "getHealth",
            params = null
        )

        // When: Serializing to JSON (use jsonWithDefaults to encode the jsonrpc field)
        val jsonString = jsonWithDefaults.encodeToString(request)

        // Then: Field names are correct (jsonrpc not jsonRpc)
        assertTrue(jsonString.contains("\"jsonrpc\""), "Missing jsonrpc field. JSON: $jsonString")
        assertTrue(jsonString.contains("\"2.0\""), "Missing version 2.0. JSON: $jsonString")
        assertTrue(jsonString.contains("\"id\""), "Missing id field. JSON: $jsonString")
        assertTrue(jsonString.contains("\"test-123\""), "Missing id value. JSON: $jsonString")
        assertTrue(jsonString.contains("\"method\""), "Missing method field. JSON: $jsonString")
        assertTrue(jsonString.contains("\"getHealth\""), "Missing method value. JSON: $jsonString")
        assertFalse(jsonString.contains("jsonRpc"), "Should use lowercase jsonrpc. JSON: $jsonString")
    }

    @Test
    fun testSorobanRpcRequest_withParameters_includesParams() {
        // Given: RPC request with string parameter
        val request = SorobanRpcRequest(
            id = "test-456",
            method = "getTransaction",
            params = "transaction-hash"
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Params are included
        assertTrue(jsonString.contains("\"params\":\"transaction-hash\""))
    }

    @Test
    fun testSorobanRpcRequest_withNullParams_omitsField() {
        // Given: RPC request with null params (encodeDefaults = false)
        val request = SorobanRpcRequest<String?>(
            id = "test-789",
            method = "getHealth",
            params = null
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Params field is omitted (encodeDefaults = false)
        assertFalse(jsonString.contains("\"params\""))
    }

    // ========== SimulateTransactionRequest Tests ==========

    @Test
    fun testSimulateTransactionRequest_basicSerialization() {
        // Given: Simulate transaction request
        val request = SimulateTransactionRequest(
            transaction = "AAAA...base64...="
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Transaction is included
        assertTrue(jsonString.contains("\"transaction\":\"AAAA...base64...=\""))
    }

    @Test
    fun testSimulateTransactionRequest_withResourceConfig() {
        // Given: Request with resource config
        val request = SimulateTransactionRequest(
            transaction = "AAAA...base64...=",
            resourceConfig = SimulateTransactionRequest.ResourceConfig(
                instructionLeeway = 1000000
            )
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Resource config is included
        assertTrue(jsonString.contains("\"resourceConfig\""))
        assertTrue(jsonString.contains("\"instructionLeeway\":1000000"))
    }

    @Test
    fun testSimulateTransactionRequest_authModeEnforceSerialName() {
        // Given: Request with ENFORCE auth mode
        val request = SimulateTransactionRequest(
            transaction = "AAAA...base64...=",
            authMode = SimulateTransactionRequest.AuthMode.ENFORCE
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Auth mode uses lowercase @SerialName
        assertTrue(jsonString.contains("\"authMode\":\"enforce\""))
    }

    @Test
    fun testSimulateTransactionRequest_authModeRecordSerialName() {
        // Given: Request with RECORD auth mode
        val request = SimulateTransactionRequest(
            transaction = "AAAA...base64...=",
            authMode = SimulateTransactionRequest.AuthMode.RECORD
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Auth mode uses lowercase @SerialName
        assertTrue(jsonString.contains("\"authMode\":\"record\""))
    }

    @Test
    fun testSimulateTransactionRequest_authModeRecordAllowNonrootSerialName() {
        // Given: Request with RECORD_ALLOW_NONROOT auth mode
        val request = SimulateTransactionRequest(
            transaction = "AAAA...base64...=",
            authMode = SimulateTransactionRequest.AuthMode.RECORD_ALLOW_NONROOT
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Auth mode uses snake_case @SerialName
        assertTrue(jsonString.contains("\"authMode\":\"record_allow_nonroot\""))
    }

    @Test
    fun testSimulateTransactionRequest_blankTransaction_throwsException() {
        // When/Then: Blank transaction should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            SimulateTransactionRequest(transaction = "")
        }

        assertTrue(exception.message?.contains("transaction") ?: false)
        assertTrue(exception.message?.contains("not be blank") ?: false)
    }

    @Test
    fun testSimulateTransactionRequest_negativeInstructionLeeway_throwsException() {
        // When/Then: Negative instruction leeway should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            SimulateTransactionRequest(
                transaction = "AAAA...base64...=",
                resourceConfig = SimulateTransactionRequest.ResourceConfig(
                    instructionLeeway = -1000
                )
            )
        }

        assertTrue(exception.message?.contains("instructionLeeway") ?: false)
        assertTrue(exception.message?.contains("non-negative") ?: false)
    }

    // ========== GetEventsRequest Tests ==========

    @Test
    fun testGetEventsRequest_basicSerialization() {
        // Given: Get events request
        val request = GetEventsRequest(
            startLedger = 1000,
            filters = listOf(
                GetEventsRequest.EventFilter(
                    type = GetEventsRequest.EventFilterType.CONTRACT,
                    contractIds = listOf("CCJZ5D...")
                )
            )
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: All fields are included
        assertTrue(jsonString.contains("\"startLedger\":1000"))
        assertTrue(jsonString.contains("\"filters\""))
        assertTrue(jsonString.contains("\"type\":\"contract\""))
        assertTrue(jsonString.contains("\"contractIds\""))
    }

    @Test
    fun testGetEventsRequest_eventFilterTypeSerialNames() {
        // Given: Requests with different event types
        val contractFilter = GetEventsRequest.EventFilter(
            type = GetEventsRequest.EventFilterType.CONTRACT
        )
        val systemFilter = GetEventsRequest.EventFilter(
            type = GetEventsRequest.EventFilterType.SYSTEM
        )
        val diagnosticFilter = GetEventsRequest.EventFilter(
            type = GetEventsRequest.EventFilterType.DIAGNOSTIC
        )

        // When: Serializing to JSON
        val contractJson = json.encodeToString(contractFilter)
        val systemJson = json.encodeToString(systemFilter)
        val diagnosticJson = json.encodeToString(diagnosticFilter)

        // Then: Types use lowercase @SerialName
        assertTrue(contractJson.contains("\"type\":\"contract\""))
        assertTrue(systemJson.contains("\"type\":\"system\""))
        assertTrue(diagnosticJson.contains("\"type\":\"diagnostic\""))
    }

    @Test
    fun testGetEventsRequest_withPagination() {
        // Given: Request with pagination
        val request = GetEventsRequest(
            startLedger = 1000,
            filters = listOf(
                GetEventsRequest.EventFilter(
                    type = GetEventsRequest.EventFilterType.CONTRACT
                )
            ),
            pagination = GetEventsRequest.Pagination(
                cursor = "cursor-123",
                limit = 100
            )
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Pagination is included
        assertTrue(jsonString.contains("\"pagination\""))
        assertTrue(jsonString.contains("\"cursor\":\"cursor-123\""))
        assertTrue(jsonString.contains("\"limit\":100"))
    }

    @Test
    fun testGetEventsRequest_zeroStartLedger_throwsException() {
        // When/Then: Zero start ledger should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest(
                startLedger = 0,
                filters = listOf(
                    GetEventsRequest.EventFilter(
                        type = GetEventsRequest.EventFilterType.CONTRACT
                    )
                )
            )
        }

        assertTrue(exception.message?.contains("startLedger") ?: false)
        assertTrue(exception.message?.contains("positive") ?: false)
    }

    @Test
    fun testGetEventsRequest_emptyFilters_throwsException() {
        // When/Then: Empty filters should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest(
                startLedger = 1000,
                filters = emptyList()
            )
        }

        assertTrue(exception.message?.contains("filters") ?: false)
        assertTrue(exception.message?.contains("not be empty") ?: false)
    }

    @Test
    fun testGetEventsRequest_tooManyFilters_throwsException() {
        // When/Then: More than 5 filters should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest(
                startLedger = 1000,
                filters = List(6) {
                    GetEventsRequest.EventFilter(
                        type = GetEventsRequest.EventFilterType.CONTRACT
                    )
                }
            )
        }

        assertTrue(exception.message?.contains("filters") ?: false)
        assertTrue(exception.message?.contains("exceed 5") ?: false)
    }

    @Test
    fun testGetEventsRequest_paginationLimitTooHigh_throwsException() {
        // When/Then: Limit exceeding 10000 should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest(
                startLedger = 1000,
                filters = listOf(
                    GetEventsRequest.EventFilter(
                        type = GetEventsRequest.EventFilterType.CONTRACT
                    )
                ),
                pagination = GetEventsRequest.Pagination(limit = 10001)
            )
        }

        assertTrue(exception.message?.contains("limit") ?: false)
        assertTrue(exception.message?.contains("exceed 10000") ?: false)
    }

    @Test
    fun testEventFilter_tooManyTopics_throwsException() {
        // When/Then: More than 4 topics should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest.EventFilter(
                type = GetEventsRequest.EventFilterType.CONTRACT,
                topics = List(5) { listOf("topic") }
            )
        }

        assertTrue(exception.message?.contains("topics") ?: false)
        assertTrue(exception.message?.contains("exceed 4") ?: false)
    }

    @Test
    fun testEventFilter_emptyTopicList_throwsException() {
        // When/Then: Empty topic filter list should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest.EventFilter(
                type = GetEventsRequest.EventFilterType.CONTRACT,
                topics = listOf(emptyList())
            )
        }

        assertTrue(exception.message?.contains("topic filter") ?: false)
        assertTrue(exception.message?.contains("not be empty") ?: false)
    }

    @Test
    fun testEventFilter_blankContractId_throwsException() {
        // When/Then: Blank contract ID should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest.EventFilter(
                type = GetEventsRequest.EventFilterType.CONTRACT,
                contractIds = listOf("")
            )
        }

        assertTrue(exception.message?.contains("contractIds") ?: false)
        assertTrue(exception.message?.contains("blank") ?: false)
    }

    // ========== GetLedgerEntriesRequest Tests ==========

    @Test
    fun testGetLedgerEntriesRequest_basicSerialization() {
        // Given: Get ledger entries request
        val request = GetLedgerEntriesRequest(
            keys = listOf("AAA...base64...=", "BBB...base64...=")
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Keys array is included
        assertTrue(jsonString.contains("\"keys\""))
        assertTrue(jsonString.contains("AAA...base64...="))
        assertTrue(jsonString.contains("BBB...base64...="))
    }

    @Test
    fun testGetLedgerEntriesRequest_emptyKeys_throwsException() {
        // When/Then: Empty keys should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetLedgerEntriesRequest(keys = emptyList())
        }

        assertTrue(exception.message?.contains("keys") ?: false)
        assertTrue(exception.message?.contains("not be empty") ?: false)
    }

    @Test
    fun testGetLedgerEntriesRequest_blankKey_throwsException() {
        // When/Then: Blank key should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetLedgerEntriesRequest(keys = listOf(""))
        }

        assertTrue(exception.message?.contains("keys") ?: false)
        assertTrue(exception.message?.contains("blank") ?: false)
    }

    // ========== SendTransactionRequest Tests ==========

    @Test
    fun testSendTransactionRequest_basicSerialization() {
        // Given: Send transaction request
        val request = SendTransactionRequest(
            transaction = "AAAA...base64...="
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Transaction is included
        assertTrue(jsonString.contains("\"transaction\":\"AAAA...base64...=\""))
    }

    @Test
    fun testSendTransactionRequest_blankTransaction_throwsException() {
        // When/Then: Blank transaction should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            SendTransactionRequest(transaction = "")
        }

        assertTrue(exception.message?.contains("transaction") ?: false)
        assertTrue(exception.message?.contains("not be blank") ?: false)
    }
}
