package com.soneso.stellar.sdk.unitTests.rpc.requests

import com.soneso.stellar.sdk.rpc.requests.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Tests for request model serialization.
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

    // ========== GetTransactionRequest Tests ==========

    @Test
    fun testGetTransactionRequest_basicSerialization() {
        val hash = "a".repeat(64)
        val request = GetTransactionRequest(hash = hash)
        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"hash\":\"$hash\""))
    }

    @Test
    fun testGetTransactionRequest_validHexHash() {
        val hash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        val request = GetTransactionRequest(hash = hash)
        assertEquals(hash, request.hash)
    }

    @Test
    fun testGetTransactionRequest_uppercaseHexAllowed() {
        val hash = "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789"
        val request = GetTransactionRequest(hash = hash)
        assertEquals(hash, request.hash)
    }

    @Test
    fun testGetTransactionRequest_mixedCaseHexAllowed() {
        val hash = "AbCdEf0123456789abcdef0123456789ABCDEF0123456789abcdef0123456789"
        val request = GetTransactionRequest(hash = hash)
        assertEquals(hash, request.hash)
    }

    @Test
    fun testGetTransactionRequest_blankHash_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetTransactionRequest(hash = "")
        }
    }

    @Test
    fun testGetTransactionRequest_tooShortHash_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetTransactionRequest(hash = "abcdef")
        }
    }

    @Test
    fun testGetTransactionRequest_tooLongHash_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetTransactionRequest(hash = "a".repeat(65))
        }
    }

    @Test
    fun testGetTransactionRequest_nonHexHash_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetTransactionRequest(hash = "g".repeat(64))
        }
    }

    // ========== GetTransactionsRequest Tests ==========

    @Test
    fun testGetTransactionsRequest_basicSerialization() {
        val request = GetTransactionsRequest(startLedger = 1000)
        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"startLedger\":1000"))
    }

    @Test
    fun testGetTransactionsRequest_withPagination() {
        val request = GetTransactionsRequest(
            startLedger = 5000,
            pagination = GetTransactionsRequest.Pagination(limit = 50)
        )
        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"startLedger\":5000"))
        assertTrue(jsonString.contains("\"limit\":50"))
    }

    @Test
    fun testGetTransactionsRequest_withCursorOnly() {
        val request = GetTransactionsRequest(
            startLedger = null,
            pagination = GetTransactionsRequest.Pagination(cursor = "abc123")
        )
        val jsonString = json.encodeToString(request)

        assertFalse(jsonString.contains("\"startLedger\""))
        assertTrue(jsonString.contains("\"cursor\":\"abc123\""))
    }

    @Test
    fun testGetTransactionsRequest_nullStartLedgerNoPagination() {
        // startLedger can be null without a cursor (no validation against this)
        val request = GetTransactionsRequest(startLedger = null)
        val jsonString = json.encodeToString(request)
        assertFalse(jsonString.contains("\"startLedger\""))
    }

    @Test
    fun testGetTransactionsRequest_negativeStartLedger_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetTransactionsRequest(startLedger = -1)
        }
    }

    @Test
    fun testGetTransactionsRequest_zeroStartLedger_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetTransactionsRequest(startLedger = 0)
        }
    }

    @Test
    fun testGetTransactionsRequest_negativePaginationLimit_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetTransactionsRequest(
                startLedger = 1000,
                pagination = GetTransactionsRequest.Pagination(limit = -1)
            )
        }
    }

    @Test
    fun testGetTransactionsRequest_zeroPaginationLimit_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetTransactionsRequest(
                startLedger = 1000,
                pagination = GetTransactionsRequest.Pagination(limit = 0)
            )
        }
    }

    @Test
    fun testGetTransactionsRequest_startLedgerWithCursor_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetTransactionsRequest(
                startLedger = 1000,
                pagination = GetTransactionsRequest.Pagination(cursor = "cursor123")
            )
        }
    }

    // ========== GetLedgersRequest Tests ==========

    @Test
    fun testGetLedgersRequest_basicSerialization() {
        val request = GetLedgersRequest(startLedger = 2000)
        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"startLedger\":2000"))
    }

    @Test
    fun testGetLedgersRequest_withPagination() {
        val request = GetLedgersRequest(
            startLedger = 3000,
            pagination = GetLedgersRequest.Pagination(limit = 100)
        )
        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"startLedger\":3000"))
        assertTrue(jsonString.contains("\"limit\":100"))
    }

    @Test
    fun testGetLedgersRequest_cursorBasedPagination() {
        val request = GetLedgersRequest(
            startLedger = null,
            pagination = GetLedgersRequest.Pagination(cursor = "ledger-cursor-xyz")
        )
        val jsonString = json.encodeToString(request)

        assertFalse(jsonString.contains("\"startLedger\""))
        assertTrue(jsonString.contains("\"cursor\":\"ledger-cursor-xyz\""))
    }

    @Test
    fun testGetLedgersRequest_noStartLedgerNoCursor_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetLedgersRequest(startLedger = null)
        }
    }

    @Test
    fun testGetLedgersRequest_negativeStartLedger_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetLedgersRequest(startLedger = -5)
        }
    }

    @Test
    fun testGetLedgersRequest_zeroStartLedger_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetLedgersRequest(startLedger = 0)
        }
    }

    @Test
    fun testGetLedgersRequest_negativePaginationLimit_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetLedgersRequest(
                startLedger = 1000,
                pagination = GetLedgersRequest.Pagination(limit = -1)
            )
        }
    }

    @Test
    fun testGetLedgersRequest_zeroPaginationLimit_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            GetLedgersRequest(
                startLedger = 1000,
                pagination = GetLedgersRequest.Pagination(limit = 0)
            )
        }
    }

    // ========== Pagination Sub-Model Tests ==========

    @Test
    fun testGetTransactionsPagination_nullCursorAndLimit() {
        val pagination = GetTransactionsRequest.Pagination()
        assertNull(pagination.cursor)
        assertNull(pagination.limit)
    }

    @Test
    fun testGetTransactionsPagination_serialization() {
        val pagination = GetTransactionsRequest.Pagination(cursor = "next", limit = 25)
        val jsonString = json.encodeToString(pagination)
        assertTrue(jsonString.contains("\"cursor\":\"next\""))
        assertTrue(jsonString.contains("\"limit\":25"))
    }

    @Test
    fun testGetLedgersPagination_nullCursorAndLimit() {
        val pagination = GetLedgersRequest.Pagination()
        assertNull(pagination.cursor)
        assertNull(pagination.limit)
    }

    @Test
    fun testGetLedgersPagination_serialization() {
        val pagination = GetLedgersRequest.Pagination(cursor = "ledger-next", limit = 200)
        val jsonString = json.encodeToString(pagination)
        assertTrue(jsonString.contains("\"cursor\":\"ledger-next\""))
        assertTrue(jsonString.contains("\"limit\":200"))
    }

    // ========== Optional Field Presence and Absence ==========

    @Test
    fun testGetEventsRequest_allOptionalFieldsSet_emitsEveryField() {
        // Given: Every optional field carries a value
        val request = GetEventsRequest(
            startLedger = 1000,
            endLedger = 2000,
            filters = listOf(
                GetEventsRequest.EventFilter(
                    type = GetEventsRequest.EventFilterType.SYSTEM,
                    contractIds = listOf("CCJZ5D..."),
                    topics = listOf(listOf("AAAADwAAAAdDT1VOVEVSAA==", "**"))
                )
            ),
            pagination = GetEventsRequest.Pagination(limit = 250)
        )

        // When: Serializing with defaults omitted
        val jsonString = json.encodeToString(request)

        // Then: Each optional field appears with its value
        assertTrue(jsonString.contains("\"startLedger\":1000"), jsonString)
        assertTrue(jsonString.contains("\"endLedger\":2000"), jsonString)
        assertTrue(jsonString.contains("\"type\":\"system\""), jsonString)
        assertTrue(jsonString.contains("\"contractIds\":[\"CCJZ5D...\"]"), jsonString)
        assertTrue(jsonString.contains("\"topics\""), jsonString)
        assertTrue(jsonString.contains("\"limit\":250"), jsonString)
    }

    @Test
    fun testGetEventsRequest_onlyRequiredFields_omitsOptionalFields() {
        // Given: Only the required startLedger and filters are set
        val request = GetEventsRequest(
            startLedger = 1000,
            filters = listOf(GetEventsRequest.EventFilter())
        )

        // When: Serializing with defaults omitted
        val jsonString = json.encodeToString(request)

        // Then: Every optional field is absent from the payload
        assertEquals("""{"startLedger":1000,"filters":[{}]}""", jsonString)
    }

    @Test
    fun testGetEventsRequest_encodeDefaults_emitsNullOptionalFields() {
        // Given: A request whose optional fields are unset
        val request = GetEventsRequest(
            startLedger = 1000,
            filters = listOf(GetEventsRequest.EventFilter())
        )

        // When: Serializing with defaults encoded
        val jsonString = jsonWithDefaults.encodeToString(request)

        // Then: The unset fields are emitted explicitly as null
        assertTrue(jsonString.contains("\"endLedger\":null"), jsonString)
        assertTrue(jsonString.contains("\"pagination\":null"), jsonString)
        assertTrue(jsonString.contains("\"type\":null"), jsonString)
        assertTrue(jsonString.contains("\"contractIds\":null"), jsonString)
        assertTrue(jsonString.contains("\"topics\":null"), jsonString)
    }

    @Test
    fun testGetEventsRequest_deserializesFromJsonWithAllFields() {
        // Given: A payload carrying every field
        val payload = """{
            "startLedger": 1000,
            "endLedger": 2000,
            "filters": [
                {
                    "type": "contract",
                    "contractIds": ["CCJZ5D..."],
                    "topics": [["AAAADwAAAAdDT1VOVEVSAA==", "**"]]
                }
            ],
            "pagination": {"limit": 500}
        }"""

        // When: Deserializing
        val request = json.decodeFromString<GetEventsRequest>(payload)

        // Then: Every field is populated
        assertEquals(1000L, request.startLedger)
        assertEquals(2000L, request.endLedger)
        assertEquals(1, request.filters.size)
        assertEquals(GetEventsRequest.EventFilterType.CONTRACT, request.filters[0].type)
        assertEquals(listOf("CCJZ5D..."), request.filters[0].contractIds)
        assertEquals(listOf(listOf("AAAADwAAAAdDT1VOVEVSAA==", "**")), request.filters[0].topics)
        assertEquals(500L, request.pagination?.limit)
        assertNull(request.pagination?.cursor)
    }

    @Test
    fun testGetEventsRequest_deserializesFromJsonWithoutOptionalFields() {
        // Given: A payload carrying only the required fields
        val payload = """{"startLedger": 1000, "filters": [{}]}"""

        // When: Deserializing
        val request = json.decodeFromString<GetEventsRequest>(payload)

        // Then: The optional fields fall back to null
        assertEquals(1000L, request.startLedger)
        assertNull(request.endLedger)
        assertNull(request.pagination)
        assertNull(request.filters[0].type)
        assertNull(request.filters[0].contractIds)
        assertNull(request.filters[0].topics)
    }

    @Test
    fun testGetEventsRequest_deserializedCursorWithStartLedger_throwsException() {
        // Given: A payload that sets both a cursor and a start ledger
        val payload = """{
            "startLedger": 1000,
            "filters": [],
            "pagination": {"cursor": "cursor-123"}
        }"""

        // When/Then: Deserialization enforces the same rule as the constructor
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetEventsRequest>(payload)
        }
        assertTrue(exception.message?.contains("omitted when cursor") ?: false, "${exception.message}")
    }

    @Test
    fun testGetEventsRequest_deserializedCursorWithEndLedger_throwsException() {
        // Given: A payload that sets both a cursor and an end ledger
        val payload = """{
            "endLedger": 2000,
            "filters": [],
            "pagination": {"cursor": "cursor-123"}
        }"""

        // When/Then: endLedger is rejected alongside a cursor
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetEventsRequest>(payload)
        }
        assertTrue(exception.message?.contains("endLedger") ?: false, "${exception.message}")
        assertTrue(exception.message?.contains("omitted when cursor") ?: false, "${exception.message}")
    }

    @Test
    fun testGetEventsRequest_deserializedLimitOutOfRange_throwsException() {
        // Given: A payload whose pagination limit exceeds the documented maximum
        val payload = """{"startLedger": 1, "filters": [], "pagination": {"limit": 10001}}"""

        // When/Then: Deserialization enforces the limit
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetEventsRequest>(payload)
        }
    }

    @Test
    fun testGetEventsRequest_deserializedZeroLimit_throwsException() {
        // Given: A payload whose pagination limit is not positive
        val payload = """{"startLedger": 1, "filters": [], "pagination": {"limit": 0}}"""

        // When/Then: Deserialization enforces the positive limit
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetEventsRequest>(payload)
        }
        assertTrue(exception.message?.contains("must be positive") ?: false, "${exception.message}")
    }

    @Test
    fun testGetEventsRequest_deserializedEndLedgerBeforeStart_throwsException() {
        // Given: A payload whose end ledger precedes its start ledger
        val payload = """{"startLedger": 2000, "endLedger": 1000, "filters": []}"""

        // When/Then: Deserialization enforces the ordering
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetEventsRequest>(payload)
        }
    }

    @Test
    fun testGetEventsRequest_deserializedFilterWithMisplacedWildcard_throwsException() {
        // Given: A payload whose topic filter puts "**" before the last segment
        val payload = """{
            "startLedger": 1,
            "filters": [{"topics": [["**", "AAAA"]]}]
        }"""

        // When/Then: Deserialization enforces the wildcard placement rule
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetEventsRequest>(payload)
        }
        assertTrue(exception.message?.contains("last segment") ?: false, "${exception.message}")
    }

    @Test
    fun testGetEventsRequest_deserializedFilterWithBlankContractId_throwsException() {
        // Given: A payload whose filter carries a blank contract ID
        val payload = """{"startLedger": 1, "filters": [{"contractIds": [""]}]}"""

        // When/Then: Deserialization enforces the non-blank rule
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetEventsRequest>(payload)
        }
    }

    @Test
    fun testGetEventsRequest_equalityDistinguishesEveryField() {
        val base = GetEventsRequest(
            startLedger = 1000,
            endLedger = 2000,
            filters = listOf(GetEventsRequest.EventFilter(contractIds = listOf("CABC"))),
            pagination = GetEventsRequest.Pagination(limit = 10)
        )

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(startLedger = 1001))
        assertNotEquals(base, base.copy(endLedger = 2001))
        assertNotEquals(base, base.copy(filters = emptyList()))
        assertNotEquals(base, base.copy(pagination = GetEventsRequest.Pagination(limit = 11)))
        assertNotEquals<Any?>(base, null)
        assertNotEquals<Any?>(base, "not a request")
        assertTrue(base.toString().contains("startLedger=1000"), base.toString())
    }

    @Test
    fun testEventFilter_equalityDistinguishesEveryField() {
        val base = GetEventsRequest.EventFilter(
            type = GetEventsRequest.EventFilterType.CONTRACT,
            contractIds = listOf("CABC"),
            topics = listOf(listOf("AAAA"))
        )

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(type = GetEventsRequest.EventFilterType.SYSTEM))
        assertNotEquals(base, base.copy(type = null))
        assertNotEquals(base, base.copy(contractIds = null))
        assertNotEquals(base, base.copy(topics = null))
        assertNotEquals<Any?>(base, null)
        assertTrue(base.toString().contains("CONTRACT"), base.toString())
    }

    @Test
    fun testGetLedgersRequest_encodeDefaults_emitsNullPagination() {
        // Given: A ledger request without pagination
        val request = GetLedgersRequest(startLedger = 4000)

        // When: Serializing with and without defaults
        val omitted = json.encodeToString(request)
        val withDefaults = jsonWithDefaults.encodeToString(request)

        // Then: Only the encode-defaults form carries the null pagination
        assertEquals("""{"startLedger":4000}""", omitted)
        assertTrue(withDefaults.contains("\"pagination\":null"), withDefaults)
    }

    @Test
    fun testGetLedgersRequest_deserializesFromJsonWithPagination() {
        // Given: A payload with cursor-based pagination
        val payload = """{"pagination": {"cursor": "ledger-cursor", "limit": 20}}"""

        // When: Deserializing
        val request = json.decodeFromString<GetLedgersRequest>(payload)

        // Then: The cursor form is accepted with no start ledger
        assertNull(request.startLedger)
        assertEquals("ledger-cursor", request.pagination?.cursor)
        assertEquals(20L, request.pagination?.limit)
    }

    @Test
    fun testGetLedgersRequest_deserializedWithoutStartLedgerOrCursor_throwsException() {
        // Given: A payload with neither a start ledger nor a cursor
        val payload = """{"pagination": {"limit": 20}}"""

        // When/Then: Deserialization enforces the same rule as the constructor
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetLedgersRequest>(payload)
        }
        assertTrue(
            exception.message?.contains("startLedger must be provided") ?: false,
            "${exception.message}"
        )
    }

    @Test
    fun testGetLedgersRequest_deserializedNonPositiveStartLedger_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetLedgersRequest>("""{"startLedger": 0}""")
        }
    }

    @Test
    fun testGetLedgersRequest_deserializedNonPositiveLimit_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetLedgersRequest>(
                """{"startLedger": 1, "pagination": {"limit": 0}}"""
            )
        }
    }

    @Test
    fun testGetLedgersRequest_equalityDistinguishesEveryField() {
        val base = GetLedgersRequest(
            startLedger = 4000,
            pagination = GetLedgersRequest.Pagination(cursor = null, limit = 50)
        )

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(startLedger = 4001))
        assertNotEquals(
            base,
            base.copy(pagination = GetLedgersRequest.Pagination(cursor = null, limit = 51))
        )
        assertNotEquals<Any?>(base, null)
        assertTrue(base.toString().contains("startLedger=4000"), base.toString())
    }

    @Test
    fun testGetLedgersPagination_equalityDistinguishesEveryField() {
        val base = GetLedgersRequest.Pagination(cursor = "c", limit = 5)

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(cursor = "d"))
        assertNotEquals(base, base.copy(cursor = null))
        assertNotEquals(base, base.copy(limit = 6))
        assertNotEquals(base, base.copy(limit = null))
        assertNotEquals<Any?>(base, null)
    }

    @Test
    fun testGetTransactionsRequest_encodeDefaults_emitsNullPagination() {
        // Given: A transactions request without pagination
        val request = GetTransactionsRequest(startLedger = 7000)

        // When: Serializing with and without defaults
        val omitted = json.encodeToString(request)
        val withDefaults = jsonWithDefaults.encodeToString(request)

        // Then: Only the encode-defaults form carries the null pagination
        assertEquals("""{"startLedger":7000}""", omitted)
        assertTrue(withDefaults.contains("\"pagination\":null"), withDefaults)
    }

    @Test
    fun testGetTransactionsRequest_deserializesFromJsonWithPagination() {
        // Given: A payload with a start ledger and a limit
        val payload = """{"startLedger": 7000, "pagination": {"limit": 30}}"""

        // When: Deserializing
        val request = json.decodeFromString<GetTransactionsRequest>(payload)

        // Then: Both values survive the round trip
        assertEquals(7000L, request.startLedger)
        assertEquals(30L, request.pagination?.limit)
        assertNull(request.pagination?.cursor)
    }

    @Test
    fun testGetTransactionsRequest_deserializedStartLedgerWithCursor_throwsException() {
        // Given: A payload that sets both a start ledger and a cursor
        val payload = """{"startLedger": 7000, "pagination": {"cursor": "abc"}}"""

        // When/Then: Deserialization enforces the mutual exclusion
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetTransactionsRequest>(payload)
        }
        assertTrue(
            exception.message?.contains("cannot both be set") ?: false,
            "${exception.message}"
        )
    }

    @Test
    fun testGetTransactionsRequest_deserializedNonPositiveStartLedger_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetTransactionsRequest>("""{"startLedger": -5}""")
        }
    }

    @Test
    fun testGetTransactionsRequest_deserializedNonPositiveLimit_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetTransactionsRequest>(
                """{"startLedger": 1, "pagination": {"limit": -1}}"""
            )
        }
    }

    @Test
    fun testGetTransactionsRequest_equalityDistinguishesEveryField() {
        val base = GetTransactionsRequest(
            startLedger = 7000,
            pagination = GetTransactionsRequest.Pagination(limit = 15)
        )

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(startLedger = 7001))
        assertNotEquals(
            base,
            base.copy(pagination = GetTransactionsRequest.Pagination(limit = 16))
        )
        assertNotEquals<Any?>(base, null)
        assertTrue(base.toString().contains("startLedger=7000"), base.toString())
    }

    @Test
    fun testGetTransactionsPagination_equalityDistinguishesEveryField() {
        val base = GetTransactionsRequest.Pagination(cursor = "c", limit = 5)

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(cursor = "d"))
        assertNotEquals(base, base.copy(cursor = null))
        assertNotEquals(base, base.copy(limit = 6))
        assertNotEquals(base, base.copy(limit = null))
        assertNotEquals<Any?>(base, null)
    }

    @Test
    fun testSimulateTransactionRequest_allOptionalFieldsSet_emitsEveryField() {
        // Given: Every optional field carries a value
        val request = SimulateTransactionRequest(
            transaction = "AAAA...base64...=",
            resourceConfig = SimulateTransactionRequest.ResourceConfig(instructionLeeway = 0),
            authMode = SimulateTransactionRequest.AuthMode.RECORD,
            useUpgradedAuth = true
        )

        // When: Serializing with defaults omitted
        val jsonString = json.encodeToString(request)

        // Then: Each optional field appears with its value
        assertTrue(jsonString.contains("\"instructionLeeway\":0"), jsonString)
        assertTrue(jsonString.contains("\"authMode\":\"record\""), jsonString)
        assertTrue(jsonString.contains("\"useUpgradedAuth\":true"), jsonString)
    }

    @Test
    fun testSimulateTransactionRequest_onlyTransaction_alwaysSendsUseUpgradedAuth() {
        // Given: Only the required transaction is set
        val request = SimulateTransactionRequest(transaction = "AAAA...base64...=")

        // When: Serializing with defaults omitted
        val jsonString = json.encodeToString(request)

        // Then: The null optional fields are omitted, while useUpgradedAuth is
        // serialized with its default true even under encodeDefaults = false
        assertEquals("""{"transaction":"AAAA...base64...=","useUpgradedAuth":true}""", jsonString)
    }

    @Test
    fun testSimulateTransactionRequest_explicitFalse_serializedAsFalse() {
        // Given: The legacy opt-out
        val request = SimulateTransactionRequest(transaction = "AAAA...base64...=", useUpgradedAuth = false)

        // When: Serializing with defaults omitted
        val jsonString = json.encodeToString(request)

        // Then: The opt-out reaches the wire as an explicit false
        assertEquals("""{"transaction":"AAAA...base64...=","useUpgradedAuth":false}""", jsonString)
    }

    @Test
    fun testSimulateTransactionRequest_encodeDefaults_emitsNullOptionalFields() {
        // Given: A request without optional fields
        val request = SimulateTransactionRequest(transaction = "AAAA...base64...=")

        // When: Serializing with defaults encoded
        val jsonString = jsonWithDefaults.encodeToString(request)

        // Then: The unset nullable fields are emitted explicitly as null and
        // useUpgradedAuth carries its default true
        assertTrue(jsonString.contains("\"resourceConfig\":null"), jsonString)
        assertTrue(jsonString.contains("\"authMode\":null"), jsonString)
        assertTrue(jsonString.contains("\"useUpgradedAuth\":true"), jsonString)
    }

    @Test
    fun testSimulateTransactionRequest_deserializesFromJsonWithAllFields() {
        // Given: A payload carrying every field
        val payload = """{
            "transaction": "AAAA",
            "resourceConfig": {"instructionLeeway": 3000000},
            "authMode": "record_allow_nonroot",
            "useUpgradedAuth": false
        }"""

        // When: Deserializing
        val request = json.decodeFromString<SimulateTransactionRequest>(payload)

        // Then: Every field is populated
        assertEquals("AAAA", request.transaction)
        assertEquals(3000000L, request.resourceConfig?.instructionLeeway)
        assertEquals(SimulateTransactionRequest.AuthMode.RECORD_ALLOW_NONROOT, request.authMode)
        assertEquals(false, request.useUpgradedAuth)
    }

    @Test
    fun testSimulateTransactionRequest_deserializesFromJsonWithoutOptionalFields() {
        // Given: A payload with only the transaction
        val request = json.decodeFromString<SimulateTransactionRequest>("""{"transaction":"AAAA"}""")

        // Then: The nullable fields fall back to null and useUpgradedAuth to true
        assertNull(request.resourceConfig)
        assertNull(request.authMode)
        assertEquals(true, request.useUpgradedAuth)
    }

    @Test
    fun testSimulateTransactionRequest_deserializedBlankTransaction_throwsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<SimulateTransactionRequest>("""{"transaction":"   "}""")
        }
        assertTrue(exception.message?.contains("not be blank") ?: false, "${exception.message}")
    }

    @Test
    fun testSimulateTransactionRequest_deserializedNegativeLeeway_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<SimulateTransactionRequest>(
                """{"transaction":"AAAA","resourceConfig":{"instructionLeeway":-1}}"""
            )
        }
    }

    @Test
    fun testSimulateTransactionRequest_equalityDistinguishesEveryField() {
        val base = SimulateTransactionRequest(
            transaction = "AAAA",
            resourceConfig = SimulateTransactionRequest.ResourceConfig(instructionLeeway = 1),
            authMode = SimulateTransactionRequest.AuthMode.ENFORCE,
            useUpgradedAuth = true
        )

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(transaction = "BBBB"))
        assertNotEquals(base, base.copy(resourceConfig = null))
        assertNotEquals(base, base.copy(authMode = SimulateTransactionRequest.AuthMode.RECORD))
        assertNotEquals(base, base.copy(useUpgradedAuth = false))
        assertNotEquals<Any?>(base, null)
        assertTrue(base.toString().contains("transaction=AAAA"), base.toString())
    }

    @Test
    fun testResourceConfig_nullLeeway_omittedAndEncodedAsNull() {
        val config = SimulateTransactionRequest.ResourceConfig()

        assertNull(config.instructionLeeway)
        assertEquals("{}", json.encodeToString(config))
        assertEquals("""{"instructionLeeway":null}""", jsonWithDefaults.encodeToString(config))
        assertEquals(config, config.copy())
        assertNotEquals(config, config.copy(instructionLeeway = 0))
    }

    @Test
    fun testGetLedgerEntriesRequest_deserializesFromJson() {
        // Given: A payload with two keys
        val payload = """{"keys": ["AAA=", "BBB="]}"""

        // When: Deserializing
        val request = json.decodeFromString<GetLedgerEntriesRequest>(payload)

        // Then: Keys survive the round trip in order
        assertEquals(listOf("AAA=", "BBB="), request.keys)
        assertEquals(payload.replace(" ", ""), json.encodeToString(request))
    }

    @Test
    fun testGetLedgerEntriesRequest_deserializedEmptyKeys_throwsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetLedgerEntriesRequest>("""{"keys": []}""")
        }
        assertTrue(exception.message?.contains("not be empty") ?: false, "${exception.message}")
    }

    @Test
    fun testGetLedgerEntriesRequest_deserializedBlankKey_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetLedgerEntriesRequest>("""{"keys": ["AAA=", " "]}""")
        }
    }

    @Test
    fun testGetLedgerEntriesRequest_equalityDistinguishesKeys() {
        val base = GetLedgerEntriesRequest(keys = listOf("AAA="))

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(keys = listOf("BBB=")))
        assertNotEquals<Any?>(base, null)
        assertTrue(base.toString().contains("AAA="), base.toString())
    }

    @Test
    fun testGetTransactionRequest_deserializesFromJson() {
        val hash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
        val request = json.decodeFromString<GetTransactionRequest>("""{"hash":"$hash"}""")

        assertEquals(hash, request.hash)
        assertEquals("""{"hash":"$hash"}""", json.encodeToString(request))
    }

    @Test
    fun testGetTransactionRequest_deserializedInvalidHash_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<GetTransactionRequest>("""{"hash":"zz"}""")
        }
    }

    @Test
    fun testGetTransactionRequest_equalityDistinguishesHash() {
        val base = GetTransactionRequest(hash = "a".repeat(64))

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(hash = "b".repeat(64)))
        assertNotEquals<Any?>(base, null)
        assertTrue(base.toString().contains("a".repeat(64)), base.toString())
    }

    @Test
    fun testSendTransactionRequest_deserializesFromJson() {
        val request = json.decodeFromString<SendTransactionRequest>("""{"transaction":"AAAA"}""")

        assertEquals("AAAA", request.transaction)
        assertEquals("""{"transaction":"AAAA"}""", json.encodeToString(request))
    }

    @Test
    fun testSendTransactionRequest_deserializedBlankTransaction_throwsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<SendTransactionRequest>("""{"transaction":"  "}""")
        }
        assertTrue(exception.message?.contains("not be blank") ?: false, "${exception.message}")
    }

    @Test
    fun testSendTransactionRequest_equalityDistinguishesTransaction() {
        val base = SendTransactionRequest(transaction = "AAAA")

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(transaction = "BBBB"))
        assertNotEquals<Any?>(base, null)
        assertTrue(base.toString().contains("AAAA"), base.toString())
    }

    @Test
    fun testSorobanRpcRequest_deserializesFromJson() {
        // Given: A JSON-RPC envelope with string params
        val payload = """{"jsonrpc":"2.0","id":"req-1","method":"getTransaction","params":"hash"}"""

        // When: Deserializing
        val request = json.decodeFromString<SorobanRpcRequest<String>>(payload)

        // Then: Every field is populated, with the wire name mapped onto jsonRpc
        assertEquals("2.0", request.jsonRpc)
        assertEquals("req-1", request.id)
        assertEquals("getTransaction", request.method)
        assertEquals("hash", request.params)
    }

    @Test
    fun testSorobanRpcRequest_deserializesWithoutJsonRpcAndParams() {
        // Given: An envelope omitting the defaulted fields
        val payload = """{"id":"req-2","method":"getHealth"}"""

        // When: Deserializing
        val request = json.decodeFromString<SorobanRpcRequest<String>>(payload)

        // Then: The protocol version falls back to its default and params stays null
        assertEquals("2.0", request.jsonRpc)
        assertNull(request.params)
    }

    @Test
    fun testSorobanRpcRequest_equalityDistinguishesEveryField() {
        val base = SorobanRpcRequest(id = "req-1", method = "getHealth", params = "p")

        assertEquals(base, base.copy())
        assertEquals(base.hashCode(), base.copy().hashCode())
        assertNotEquals(base, base.copy(jsonRpc = "1.0"))
        assertNotEquals(base, base.copy(id = "req-2"))
        assertNotEquals(base, base.copy(method = "getNetwork"))
        assertNotEquals(base, base.copy(params = "q"))
        assertNotEquals(base, base.copy(params = null))
        assertNotEquals<Any?>(base, null)
        assertTrue(base.toString().contains("getHealth"), base.toString())
    }
}
