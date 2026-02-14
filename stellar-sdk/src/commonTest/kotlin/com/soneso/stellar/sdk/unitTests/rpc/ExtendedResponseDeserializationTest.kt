package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.responses.*
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Extended tests for RPC response model deserialization covering:
 * - GetLatestLedgerResponse
 * - GetVersionInfoResponse
 * - GetFeeStatsResponse
 * - GetTransactionResponse
 * - GetTransactionsResponse
 * - GetLedgersResponse
 * - GetLedgerEntriesResponse
 * - SendTransactionResponse
 * - GetEventsResponse
 * - GetSACBalanceResponse
 * - Events
 *
 * The base ResponseDeserializationTest covers SorobanRpcResponse, GetHealthResponse,
 * GetNetworkResponse, SimulateTransactionResponse, and XDR parsing helpers.
 */
class ExtendedResponseDeserializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = false
    }

    // ========== GetLatestLedgerResponse Tests ==========

    @Test
    fun testGetLatestLedgerResponse_deserializes() {
        val jsonString = """{
            "id": "b789e0caef47ac1f1ef6fd9f21a6bbf625e5cb575c9b08c5d4a11e2e0a688dac",
            "protocolVersion": 20,
            "sequence": 50000
        }"""

        val response: GetLatestLedgerResponse = json.decodeFromString(jsonString)

        assertEquals("b789e0caef47ac1f1ef6fd9f21a6bbf625e5cb575c9b08c5d4a11e2e0a688dac", response.id)
        assertEquals(20, response.protocolVersion)
        assertEquals(50000L, response.sequence)
    }

    // ========== GetVersionInfoResponse Tests ==========

    @Test
    fun testGetVersionInfoResponse_deserializes() {
        val jsonString = """{
            "version": "21.0.0",
            "commitHash": "abc123def456",
            "buildTimestamp": "2024-01-15T10:30:00Z",
            "captiveCoreVersion": "v21.0.0-rc.1",
            "protocolVersion": 21
        }"""

        val response: GetVersionInfoResponse = json.decodeFromString(jsonString)

        assertEquals("21.0.0", response.version)
        assertEquals("abc123def456", response.commitHash)
        assertEquals("2024-01-15T10:30:00Z", response.buildTimestamp)
        assertEquals("v21.0.0-rc.1", response.captiveCoreVersion)
        assertEquals(21, response.protocolVersion)
    }

    // ========== GetFeeStatsResponse Tests ==========

    @Test
    fun testGetFeeStatsResponse_deserializes() {
        val jsonString = """{
            "sorobanInclusionFee": {
                "max": "100",
                "min": "100",
                "mode": "100",
                "p10": "100",
                "p20": "100",
                "p30": "100",
                "p40": "100",
                "p50": "100",
                "p60": "100",
                "p70": "100",
                "p80": "100",
                "p90": "100",
                "p95": "100",
                "p99": "100",
                "transactionCount": "10",
                "ledgerCount": 50
            },
            "inclusionFee": {
                "max": "200",
                "min": "100",
                "mode": "100",
                "p10": "100",
                "p20": "100",
                "p30": "100",
                "p40": "100",
                "p50": "100",
                "p60": "100",
                "p70": "100",
                "p80": "100",
                "p90": "100",
                "p95": "100",
                "p99": "200",
                "transactionCount": "100",
                "ledgerCount": 50
            },
            "latestLedger": 12345
        }"""

        val response: GetFeeStatsResponse = json.decodeFromString(jsonString)

        assertNotNull(response.sorobanInclusionFee)
        assertNotNull(response.inclusionFee)
        assertEquals(12345L, response.latestLedger)
    }

    // ========== GetTransactionResponse Tests ==========

    @Test
    fun testGetTransactionResponse_notFound_deserializes() {
        val jsonString = """{
            "status": "NOT_FOUND",
            "latestLedger": 50000,
            "latestLedgerCloseTime": 1700000000,
            "oldestLedger": 40000,
            "oldestLedgerCloseTime": 1699000000
        }"""

        val response: GetTransactionResponse = json.decodeFromString(jsonString)

        assertEquals(GetTransactionStatus.NOT_FOUND, response.status)
        assertEquals(50000L, response.latestLedger)
        assertNull(response.envelopeXdr)
        assertNull(response.resultXdr)
        assertNull(response.resultMetaXdr)
        assertNull(response.ledger)
        assertNull(response.applicationOrder)
    }

    @Test
    fun testGetTransactionResponse_success_deserializes() {
        val jsonString = """{
            "status": "SUCCESS",
            "txHash": "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            "latestLedger": 50000,
            "latestLedgerCloseTime": 1700000000,
            "oldestLedger": 40000,
            "oldestLedgerCloseTime": 1699000000,
            "applicationOrder": 1,
            "feeBump": false,
            "envelopeXdr": "AAAA...envelope...",
            "resultXdr": "AAAA...result...",
            "resultMetaXdr": "AAAA...meta...",
            "ledger": 49999,
            "createdAt": 1699999999
        }"""

        val response: GetTransactionResponse = json.decodeFromString(jsonString)

        assertEquals(GetTransactionStatus.SUCCESS, response.status)
        assertEquals("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", response.txHash)
        assertEquals(1, response.applicationOrder)
        assertEquals(false, response.feeBump)
        assertEquals("AAAA...envelope...", response.envelopeXdr)
        assertEquals("AAAA...result...", response.resultXdr)
        assertEquals("AAAA...meta...", response.resultMetaXdr)
        assertEquals(49999L, response.ledger)
        assertEquals(1699999999L, response.createdAt)
    }

    @Test
    fun testGetTransactionResponse_failed_deserializes() {
        val jsonString = """{
            "status": "FAILED",
            "txHash": "deadbeef1234567890deadbeef1234567890deadbeef1234567890deadbeef12",
            "latestLedger": 50000,
            "ledger": 49998,
            "createdAt": 1699999900
        }"""

        val response: GetTransactionResponse = json.decodeFromString(jsonString)
        assertEquals(GetTransactionStatus.FAILED, response.status)
    }

    // ========== GetTransactionsResponse Tests ==========

    @Test
    fun testGetTransactionsResponse_emptyTransactions_deserializes() {
        val jsonString = """{
            "transactions": [],
            "latestLedger": 50000,
            "latestLedgerCloseTimestamp": 1700000000,
            "oldestLedger": 40000,
            "oldestLedgerCloseTimestamp": 1699000000,
            "cursor": "50000-0"
        }"""

        val response: GetTransactionsResponse = json.decodeFromString(jsonString)

        assertEquals(0, response.transactions.size)
        assertEquals(50000L, response.latestLedger)
        assertEquals(1700000000L, response.latestLedgerCloseTimestamp)
        assertEquals(40000L, response.oldestLedger)
        assertEquals(1699000000L, response.oldestLedgerCloseTimestamp)
        assertEquals("50000-0", response.cursor)
    }

    // ========== GetLedgersResponse Tests ==========

    @Test
    fun testGetLedgersResponse_emptyLedgers_deserializes() {
        val jsonString = """{
            "ledgers": [],
            "latestLedger": 60000,
            "latestLedgerCloseTime": 1700001000,
            "oldestLedger": 50000,
            "oldestLedgerCloseTime": 1699001000,
            "cursor": "60000"
        }"""

        val response: GetLedgersResponse = json.decodeFromString(jsonString)

        assertEquals(0, response.ledgers.size)
        assertEquals(60000L, response.latestLedger)
        assertEquals("60000", response.cursor)
    }

    // ========== GetLedgerEntriesResponse Tests ==========

    @Test
    fun testGetLedgerEntriesResponse_noEntries_deserializes() {
        val jsonString = """{
            "latestLedger": 70000
        }"""

        val response: GetLedgerEntriesResponse = json.decodeFromString(jsonString)

        assertNull(response.entries)
        assertEquals(70000L, response.latestLedger)
    }

    @Test
    fun testGetLedgerEntriesResponse_withEntries_deserializes() {
        val jsonString = """{
            "entries": [{
                "key": "AAAAAAAAAABuaCbVXZ2DlXWarV6UxwbW3GNJgpn3ASChIFp5bxSIWg==",
                "xdr": "AAAAAAAAAAAAAAAA...entry...=",
                "lastModifiedLedgerSeq": 69999,
                "liveUntilLedgerSeq": 100000
            }],
            "latestLedger": 70000
        }"""

        val response: GetLedgerEntriesResponse = json.decodeFromString(jsonString)

        assertNotNull(response.entries)
        assertEquals(1, response.entries!!.size)
        assertEquals("AAAAAAAAAABuaCbVXZ2DlXWarV6UxwbW3GNJgpn3ASChIFp5bxSIWg==", response.entries!![0].key)
        assertEquals(69999L, response.entries!![0].lastModifiedLedger)
        assertEquals(100000L, response.entries!![0].liveUntilLedger)
    }

    @Test
    fun testGetLedgerEntriesResponse_entryWithoutLiveUntilLedger_deserializes() {
        val jsonString = """{
            "entries": [{
                "key": "AAA...key...=",
                "xdr": "BBB...data...=",
                "lastModifiedLedgerSeq": 69999
            }],
            "latestLedger": 70000
        }"""

        val response: GetLedgerEntriesResponse = json.decodeFromString(jsonString)

        assertNotNull(response.entries)
        assertEquals(1, response.entries!!.size)
        assertNull(response.entries!![0].liveUntilLedger)
    }

    // ========== SendTransactionResponse Tests ==========

    @Test
    fun testSendTransactionResponse_pending_deserializes() {
        val jsonString = """{
            "status": "PENDING",
            "hash": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            "latestLedger": 80000,
            "latestLedgerCloseTime": 1700002000
        }"""

        val response: SendTransactionResponse = json.decodeFromString(jsonString)

        assertEquals(SendTransactionStatus.PENDING, response.status)
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", response.hash)
        assertEquals(80000L, response.latestLedger)
        assertNull(response.errorResultXdr)
    }

    @Test
    fun testSendTransactionResponse_error_deserializes() {
        val jsonString = """{
            "status": "ERROR",
            "errorResultXdr": "AAAA...errorResult...=",
            "latestLedger": 80000,
            "latestLedgerCloseTime": 1700002000
        }"""

        val response: SendTransactionResponse = json.decodeFromString(jsonString)

        assertEquals(SendTransactionStatus.ERROR, response.status)
        assertNull(response.hash)
        assertEquals("AAAA...errorResult...=", response.errorResultXdr)
    }

    @Test
    fun testSendTransactionResponse_duplicate_deserializes() {
        val jsonString = """{
            "status": "DUPLICATE",
            "hash": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
            "latestLedger": 80000,
            "latestLedgerCloseTime": 1700002000
        }"""

        val response: SendTransactionResponse = json.decodeFromString(jsonString)

        assertEquals(SendTransactionStatus.DUPLICATE, response.status)
        assertNotNull(response.hash)
    }

    @Test
    fun testSendTransactionResponse_tryAgainLater_deserializes() {
        val jsonString = """{
            "status": "TRY_AGAIN_LATER",
            "latestLedger": 80000,
            "latestLedgerCloseTime": 1700002000
        }"""

        val response: SendTransactionResponse = json.decodeFromString(jsonString)

        assertEquals(SendTransactionStatus.TRY_AGAIN_LATER, response.status)
    }

    // ========== GetEventsResponse Tests ==========

    @Test
    fun testGetEventsResponse_emptyEvents_deserializes() {
        val jsonString = """{
            "events": [],
            "latestLedger": 90000
        }"""

        val response: GetEventsResponse = json.decodeFromString(jsonString)

        assertEquals(0, response.events.size)
        assertEquals(90000L, response.latestLedger)
        assertNull(response.cursor)
    }

    @Test
    fun testGetEventsResponse_withCursor_deserializes() {
        val jsonString = """{
            "events": [],
            "cursor": "0000000386547056640-0000000001",
            "latestLedger": 90000
        }"""

        val response: GetEventsResponse = json.decodeFromString(jsonString)

        assertEquals("0000000386547056640-0000000001", response.cursor)
    }

    // ========== GetSACBalanceResponse Tests ==========

    @Test
    fun testGetSACBalanceResponse_withBalance_deserializes() {
        val jsonString = """{
            "balanceEntry": {
                "amount": "10000000",
                "authorized": true,
                "clawback": false,
                "lastModifiedLedgerSeq": 85000,
                "liveUntilLedgerSeq": 120000
            },
            "latestLedger": 90000
        }"""

        val response: GetSACBalanceResponse = json.decodeFromString(jsonString)

        assertNotNull(response.balanceEntry)
        assertEquals("10000000", response.balanceEntry!!.amount)
        assertTrue(response.balanceEntry!!.authorized)
        assertFalse(response.balanceEntry!!.clawback)
        assertEquals(85000L, response.balanceEntry!!.lastModifiedLedgerSeq)
        assertEquals(120000L, response.balanceEntry!!.liveUntilLedgerSeq)
        assertEquals(90000L, response.latestLedger)
    }

    @Test
    fun testGetSACBalanceResponse_noBalance_deserializes() {
        val jsonString = """{
            "latestLedger": 90000
        }"""

        val response: GetSACBalanceResponse = json.decodeFromString(jsonString)

        assertNull(response.balanceEntry)
        assertEquals(90000L, response.latestLedger)
    }

    @Test
    fun testGetSACBalanceResponse_noLiveUntilLedgerSeq_deserializes() {
        val jsonString = """{
            "balanceEntry": {
                "amount": "5000000",
                "authorized": true,
                "clawback": true,
                "lastModifiedLedgerSeq": 85000
            },
            "latestLedger": 90000
        }"""

        val response: GetSACBalanceResponse = json.decodeFromString(jsonString)

        assertNotNull(response.balanceEntry)
        assertNull(response.balanceEntry!!.liveUntilLedgerSeq)
        assertTrue(response.balanceEntry!!.clawback)
    }

    // ========== Events Tests ==========

    @Test
    fun testEvents_allNull_deserializes() {
        val jsonString = """{}"""

        val response: Events = json.decodeFromString(jsonString)

        assertNull(response.diagnosticEventsXdr)
        assertNull(response.transactionEventsXdr)
        assertNull(response.contractEventsXdr)
    }

    @Test
    fun testEvents_withDiagnosticEvents_deserializes() {
        val jsonString = """{
            "diagnosticEventsXdr": ["AAAA...diag1...=", "BBBB...diag2...="]
        }"""

        val response: Events = json.decodeFromString(jsonString)

        assertNotNull(response.diagnosticEventsXdr)
        assertEquals(2, response.diagnosticEventsXdr!!.size)
        assertEquals("AAAA...diag1...=", response.diagnosticEventsXdr!![0])
    }

    @Test
    fun testEvents_withContractEvents_deserializes() {
        val jsonString = """{
            "contractEventsXdr": [["AAAA...=", "BBBB...="], ["CCCC...="]]
        }"""

        val response: Events = json.decodeFromString(jsonString)

        assertNotNull(response.contractEventsXdr)
        assertEquals(2, response.contractEventsXdr!!.size)
        assertEquals(2, response.contractEventsXdr!![0].size)
        assertEquals(1, response.contractEventsXdr!![1].size)
    }

    // ========== Edge Cases ==========

    @Test
    fun testGetHealthResponse_unknownFieldsIgnored() {
        val jsonString = """{
            "status": "healthy",
            "unknownField": "ignored",
            "anotherUnknown": 42
        }"""

        val response: GetHealthResponse = json.decodeFromString(jsonString)
        assertEquals("healthy", response.status)
    }

    @Test
    fun testGetTransactionResponse_statusValues() {
        // Test all status enum values
        for (status in listOf("NOT_FOUND", "SUCCESS", "FAILED")) {
            val jsonString = """{"status": "$status"}"""
            val response: GetTransactionResponse = json.decodeFromString(jsonString)
            assertNotNull(response.status)
        }
    }

    @Test
    fun testSendTransactionResponse_statusValues() {
        // Test all status enum values
        for (status in listOf("PENDING", "DUPLICATE", "TRY_AGAIN_LATER", "ERROR")) {
            val jsonString = """{"status": "$status"}"""
            val response: SendTransactionResponse = json.decodeFromString(jsonString)
            assertNotNull(response.status)
        }
    }
}
