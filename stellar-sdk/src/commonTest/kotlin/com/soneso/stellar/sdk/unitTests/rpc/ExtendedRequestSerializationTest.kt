package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.requests.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Extended tests for RPC request model serialization covering
 * GetTransactionRequest, GetTransactionsRequest, and GetLedgersRequest.
 *
 * The base RequestSerializationTest covers SorobanRpcRequest, SimulateTransactionRequest,
 * GetEventsRequest, GetLedgerEntriesRequest, and SendTransactionRequest.
 */
class ExtendedRequestSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = false
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
}
