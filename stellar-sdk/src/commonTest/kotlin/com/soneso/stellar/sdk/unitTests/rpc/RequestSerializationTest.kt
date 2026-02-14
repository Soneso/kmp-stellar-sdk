package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.*
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

        // When: Serializing to JSON
        val contractJson = json.encodeToString(contractFilter)
        val systemJson = json.encodeToString(systemFilter)

        // Then: Types use lowercase @SerialName
        assertTrue(contractJson.contains("\"type\":\"contract\""))
        assertTrue(systemJson.contains("\"type\":\"system\""))
    }

    @Test
    fun testGetEventsRequest_omittedTypeForDiagnostics() {
        // Given: Filter without type (to include diagnostics)
        val filter = GetEventsRequest.EventFilter(
            type = null,  // Omitted type includes all events (including diagnostics)
            contractIds = listOf("CCJZ5D...")
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(filter)

        // Then: Type field is omitted when null (encodeDefaults = false)
        assertFalse(jsonString.contains("\"type\""))
        assertTrue(jsonString.contains("\"contractIds\""))
    }

    @Test
    fun testGetEventsRequest_withEndLedger() {
        // Given: Request with endLedger
        val request = GetEventsRequest(
            startLedger = 1000,
            endLedger = 2000,
            filters = listOf(
                GetEventsRequest.EventFilter(
                    type = GetEventsRequest.EventFilterType.CONTRACT
                )
            )
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Both ledgers are included
        assertTrue(jsonString.contains("\"startLedger\":1000"))
        assertTrue(jsonString.contains("\"endLedger\":2000"))
    }

    @Test
    fun testGetEventsRequest_withPaginationLimit() {
        // Given: Request with pagination limit (no cursor)
        val request = GetEventsRequest(
            startLedger = 1000,
            filters = listOf(
                GetEventsRequest.EventFilter(
                    type = GetEventsRequest.EventFilterType.CONTRACT
                )
            ),
            pagination = GetEventsRequest.Pagination(
                limit = 100
            )
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Pagination with limit is included
        assertTrue(jsonString.contains("\"pagination\""))
        assertTrue(jsonString.contains("\"limit\":100"))
        assertFalse(jsonString.contains("\"cursor\""))  // No cursor in this test
    }

    @Test
    fun testGetEventsRequest_withCursor_startLedgerOmitted() {
        // Given: Request with cursor (startLedger should be null)
        val request = GetEventsRequest(
            startLedger = null,
            filters = listOf(
                GetEventsRequest.EventFilter(
                    type = GetEventsRequest.EventFilterType.CONTRACT
                )
            ),
            pagination = GetEventsRequest.Pagination(cursor = "cursor-123")
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: startLedger is omitted
        assertFalse(jsonString.contains("\"startLedger\""))
        assertTrue(jsonString.contains("\"cursor\":\"cursor-123\""))
    }

    @Test
    fun testGetEventsRequest_noStartLedgerNoCursor_throwsException() {
        // When/Then: Missing startLedger without cursor should fail
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest(
                startLedger = null,
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
    fun testGetEventsRequest_startLedgerWithCursor_throwsException() {
        // When/Then: startLedger with cursor should fail
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest(
                startLedger = 1000,
                filters = listOf(
                    GetEventsRequest.EventFilter(
                        type = GetEventsRequest.EventFilterType.CONTRACT
                    )
                ),
                pagination = GetEventsRequest.Pagination(cursor = "cursor-123")
            )
        }

        assertTrue(exception.message?.contains("startLedger") ?: false)
        assertTrue(exception.message?.contains("omitted when cursor") ?: false)
    }

    @Test
    fun testGetEventsRequest_endLedgerLessThanStart_throwsException() {
        // When/Then: endLedger < startLedger should fail
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest(
                startLedger = 2000,
                endLedger = 1000,
                filters = listOf(
                    GetEventsRequest.EventFilter(
                        type = GetEventsRequest.EventFilterType.CONTRACT
                    )
                )
            )
        }

        assertTrue(exception.message?.contains("endLedger") ?: false)
        assertTrue(exception.message?.contains("greater than startLedger") ?: false)
    }

    @Test
    fun testGetEventsRequest_emptyFilters_allowed() {
        // Given: Request with empty filters (now allowed)
        val request = GetEventsRequest(
            startLedger = 1000,
            filters = emptyList()
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(request)

        // Then: Empty filters are allowed
        assertTrue(jsonString.contains("\"startLedger\":1000"))
        assertTrue(jsonString.contains("\"filters\":[]"))
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
    fun testEventFilter_tooManyContractIds_throwsException() {
        // When/Then: More than 5 contractIds should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest.EventFilter(
                type = GetEventsRequest.EventFilterType.CONTRACT,
                contractIds = List(6) { "CCJZ5D$it..." }
            )
        }

        assertTrue(exception.message?.contains("contractIds") ?: false)
        assertTrue(exception.message?.contains("exceed 5") ?: false)
    }

    @Test
    fun testEventFilter_tooManyTopicFilters_throwsException() {
        // When/Then: More than 5 topic filters should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest.EventFilter(
                type = GetEventsRequest.EventFilterType.CONTRACT,
                topics = List(6) { listOf("topic") }
            )
        }

        assertTrue(exception.message?.contains("topics") ?: false)
        assertTrue(exception.message?.contains("exceed 5 topic filters") ?: false)
    }

    @Test
    fun testEventFilter_tooManyTopicSegments_throwsException() {
        // When/Then: More than 4 segments in a topic filter should fail
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest.EventFilter(
                type = GetEventsRequest.EventFilterType.CONTRACT,
                topics = listOf(List(5) { "segment$it" })
            )
        }

        assertTrue(exception.message?.contains("topic filter") ?: false)
        assertTrue(exception.message?.contains("exceed 4 segments") ?: false)
    }

    @Test
    fun testEventFilter_doubleWildcardNotAtEnd_throwsException() {
        // When/Then: ** not at the end should fail validation
        val exception = assertFailsWith<IllegalArgumentException> {
            GetEventsRequest.EventFilter(
                type = GetEventsRequest.EventFilterType.CONTRACT,
                topics = listOf(listOf("topic1", "**", "topic3"))
            )
        }

        assertTrue(exception.message?.contains("**") ?: false)
        assertTrue(exception.message?.contains("last segment") ?: false)
    }

    @Test
    fun testEventFilter_doubleWildcardAtEnd_allowed() {
        // Given: Topic filter with ** at the end
        val filter = GetEventsRequest.EventFilter(
            type = GetEventsRequest.EventFilterType.CONTRACT,
            topics = listOf(listOf("topic1", "topic2", "**"))
        )

        // When: Serializing to JSON
        val jsonString = json.encodeToString(filter)

        // Then: Trailing ** is allowed
        assertTrue(jsonString.contains("\"topics\""))
        assertTrue(jsonString.contains("**"))
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
