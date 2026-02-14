package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.responses.*
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Extended tests for RPC response deserialization to boost line coverage.
 * Exercises all properties, nullable paths, edge cases, enums, and helper methods.
 */
class RpcResponseExtendedTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ========== GetEventsResponse Extended Tests ==========

    @Test
    fun testGetEventsResponseFull() {
        val eventsJson = """
        {
            "events": [
                {
                    "type": "contract",
                    "ledger": 1000,
                    "ledgerClosedAt": "2021-01-01T00:00:00Z",
                    "contractId": "contractId123",
                    "id": "eventId1",
                    "operationIndex": 0,
                    "transactionIndex": 5,
                    "txHash": "txhash123abc",
                    "topic": [],
                    "value": "AAAAAQ==",
                    "inSuccessfulContractCall": true
                }
            ],
            "cursor": "cursor123",
            "latestLedger": 2000,
            "oldestLedger": 500,
            "latestLedgerCloseTime": 1609459200,
            "oldestLedgerCloseTime": 1609000000
        }
        """.trimIndent()
        val response = json.decodeFromString<GetEventsResponse>(eventsJson)
        assertEquals(1, response.events.size)
        assertEquals("cursor123", response.cursor)
        assertEquals(2000L, response.latestLedger)
        assertEquals(500L, response.oldestLedger)
        assertEquals(1609459200L, response.latestLedgerCloseTime)
        assertEquals(1609000000L, response.oldestLedgerCloseTime)

        val event = response.events[0]
        assertEquals(EventFilterType.CONTRACT, event.type)
        assertEquals(1000L, event.ledger)
        assertEquals("2021-01-01T00:00:00Z", event.ledgerClosedAt)
        assertEquals("contractId123", event.contractId)
        assertEquals("eventId1", event.id)
        assertEquals(0L, event.operationIndex)
        assertEquals(5L, event.transactionIndex)
        assertEquals("txhash123abc", event.transactionHash)
        assertEquals(0, event.topic.size)
        assertEquals("AAAAAQ==", event.value)
        @Suppress("DEPRECATION")
        assertEquals(true, event.inSuccessfulContractCall)
    }

    @Test
    fun testGetEventsResponseMinimal() {
        val eventsJson = """
        {
            "events": [],
            "latestLedger": 100
        }
        """.trimIndent()
        val response = json.decodeFromString<GetEventsResponse>(eventsJson)
        assertTrue(response.events.isEmpty())
        assertEquals(100L, response.latestLedger)
        assertNull(response.cursor)
        assertNull(response.oldestLedger)
        assertNull(response.latestLedgerCloseTime)
        assertNull(response.oldestLedgerCloseTime)
    }

    @Test
    fun testEventFilterTypeSystem() {
        val eventsJson = """
        {
            "events": [
                {
                    "type": "system",
                    "ledger": 100,
                    "ledgerClosedAt": "2021-01-01T00:00:00Z",
                    "contractId": "",
                    "id": "ev1",
                    "operationIndex": 0,
                    "transactionIndex": 0,
                    "txHash": "hash1",
                    "topic": [],
                    "value": "AAAAAQ=="
                }
            ],
            "latestLedger": 100
        }
        """.trimIndent()
        val response = json.decodeFromString<GetEventsResponse>(eventsJson)
        assertEquals(EventFilterType.SYSTEM, response.events[0].type)
    }

    @Test
    fun testEventFilterTypeDiagnostic() {
        val eventsJson = """
        {
            "events": [
                {
                    "type": "diagnostic",
                    "ledger": 100,
                    "ledgerClosedAt": "2021-01-01T00:00:00Z",
                    "contractId": "",
                    "id": "ev1",
                    "operationIndex": 0,
                    "transactionIndex": 0,
                    "txHash": "hash1",
                    "topic": [],
                    "value": "AAAAAQ=="
                }
            ],
            "latestLedger": 100
        }
        """.trimIndent()
        val response = json.decodeFromString<GetEventsResponse>(eventsJson)
        assertEquals(EventFilterType.DIAGNOSTIC, response.events[0].type)
    }

    @Test
    fun testEventInfoWithoutInSuccessfulContractCall() {
        val eventsJson = """
        {
            "events": [
                {
                    "type": "contract",
                    "ledger": 100,
                    "ledgerClosedAt": "2021-01-01T00:00:00Z",
                    "contractId": "cid",
                    "id": "ev1",
                    "operationIndex": 1,
                    "transactionIndex": 2,
                    "txHash": "hash1",
                    "topic": ["AAAAAQ=="],
                    "value": "AAAAAQ=="
                }
            ],
            "latestLedger": 100
        }
        """.trimIndent()
        val response = json.decodeFromString<GetEventsResponse>(eventsJson)
        val event = response.events[0]
        @Suppress("DEPRECATION")
        assertNull(event.inSuccessfulContractCall)
        assertEquals(1, event.topic.size)
    }

    // ========== GetLedgersResponse Extended Tests ==========

    @Test
    fun testGetLedgersResponseFull() {
        val ledgersJson = """
        {
            "ledgers": [
                {
                    "hash": "abc123",
                    "sequence": 5000,
                    "ledgerCloseTime": 1609459200,
                    "headerXdr": "AAAAAQ==",
                    "metadataXdr": "AAAAAQ=="
                },
                {
                    "hash": "def456",
                    "sequence": 5001,
                    "ledgerCloseTime": 1609459205,
                    "headerXdr": "AAAAAg==",
                    "metadataXdr": "AAAAAg=="
                }
            ],
            "latestLedger": 6000,
            "latestLedgerCloseTime": 1609500000,
            "oldestLedger": 1000,
            "oldestLedgerCloseTime": 1609000000,
            "cursor": "cursor456"
        }
        """.trimIndent()
        val response = json.decodeFromString<GetLedgersResponse>(ledgersJson)
        assertEquals(2, response.ledgers.size)
        assertEquals(6000L, response.latestLedger)
        assertEquals(1609500000L, response.latestLedgerCloseTime)
        assertEquals(1000L, response.oldestLedger)
        assertEquals(1609000000L, response.oldestLedgerCloseTime)
        assertEquals("cursor456", response.cursor)

        val ledger = response.ledgers[0]
        assertEquals("abc123", ledger.hash)
        assertEquals(5000L, ledger.sequence)
        assertEquals(1609459200L, ledger.ledgerCloseTime)
        assertEquals("AAAAAQ==", ledger.headerXdr)
        assertEquals("AAAAAQ==", ledger.metadataXdr)
    }

    // ========== GetTransactionsResponse Extended Tests ==========

    @Test
    fun testGetTransactionsResponseFull() {
        val txsJson = """
        {
            "transactions": [
                {
                    "status": "SUCCESS",
                    "txHash": "txhash1",
                    "applicationOrder": 1,
                    "feeBump": false,
                    "envelopeXdr": "AAAAAQ==",
                    "resultXdr": "AAAAAQ==",
                    "resultMetaXdr": "AAAAAQ==",
                    "ledger": 5000,
                    "createdAt": 1609459200,
                    "diagnosticEventsXdr": ["AAAAAQ=="],
                    "events": {
                        "diagnosticEventsXdr": ["AAAAAQ=="],
                        "transactionEventsXdr": ["AAAAAQ=="],
                        "contractEventsXdr": [["AAAAAQ=="]]
                    }
                },
                {
                    "status": "FAILED",
                    "txHash": "txhash2",
                    "applicationOrder": 2,
                    "feeBump": true,
                    "envelopeXdr": "AAAAAg==",
                    "resultXdr": "AAAAAg==",
                    "resultMetaXdr": "AAAAAg==",
                    "ledger": 5000,
                    "createdAt": 1609459200
                }
            ],
            "latestLedger": 6000,
            "latestLedgerCloseTimestamp": 1609500000,
            "oldestLedger": 1000,
            "oldestLedgerCloseTimestamp": 1609000000,
            "cursor": "txcursor"
        }
        """.trimIndent()
        val response = json.decodeFromString<GetTransactionsResponse>(txsJson)
        assertEquals(2, response.transactions.size)
        assertEquals(6000L, response.latestLedger)
        assertEquals(1609500000L, response.latestLedgerCloseTimestamp)
        assertEquals(1000L, response.oldestLedger)
        assertEquals(1609000000L, response.oldestLedgerCloseTimestamp)
        assertEquals("txcursor", response.cursor)

        val tx1 = response.transactions[0]
        assertEquals(TransactionStatus.SUCCESS, tx1.status)
        assertEquals("txhash1", tx1.txHash)
        assertEquals(1, tx1.applicationOrder)
        assertFalse(tx1.feeBump)
        assertEquals("AAAAAQ==", tx1.envelopeXdr)
        assertEquals("AAAAAQ==", tx1.resultXdr)
        assertEquals("AAAAAQ==", tx1.resultMetaXdr)
        assertEquals(5000L, tx1.ledger)
        assertEquals(1609459200L, tx1.createdAt)
        @Suppress("DEPRECATION")
        assertNotNull(tx1.diagnosticEventsXdr)
        assertNotNull(tx1.events)
        assertNotNull(tx1.events!!.diagnosticEventsXdr)
        assertNotNull(tx1.events!!.transactionEventsXdr)
        assertNotNull(tx1.events!!.contractEventsXdr)

        val tx2 = response.transactions[1]
        assertEquals(TransactionStatus.FAILED, tx2.status)
        assertTrue(tx2.feeBump)
        @Suppress("DEPRECATION")
        assertNull(tx2.diagnosticEventsXdr)
        assertNull(tx2.events)
    }

    @Test
    fun testTransactionStatusEnum() {
        assertEquals("SUCCESS", TransactionStatus.SUCCESS.name)
        assertEquals("FAILED", TransactionStatus.FAILED.name)
    }

    // ========== GetLedgerEntriesResponse Extended Tests ==========

    @Test
    fun testGetLedgerEntriesResponseFull() {
        val leJson = """
        {
            "entries": [
                {
                    "key": "AAAAAQ==",
                    "xdr": "AAAAAg==",
                    "lastModifiedLedgerSeq": 5000,
                    "liveUntilLedgerSeq": 10000
                },
                {
                    "key": "AAAAAw==",
                    "xdr": "AAAAAQ==",
                    "lastModifiedLedgerSeq": 4000
                }
            ],
            "latestLedger": 6000
        }
        """.trimIndent()
        val response = json.decodeFromString<GetLedgerEntriesResponse>(leJson)
        assertNotNull(response.entries)
        assertEquals(2, response.entries!!.size)
        assertEquals(6000L, response.latestLedger)

        val entry1 = response.entries!![0]
        assertEquals("AAAAAQ==", entry1.key)
        assertEquals("AAAAAg==", entry1.xdr)
        assertEquals(5000L, entry1.lastModifiedLedger)
        assertEquals(10000L, entry1.liveUntilLedger)

        val entry2 = response.entries!![1]
        assertEquals(4000L, entry2.lastModifiedLedger)
        assertNull(entry2.liveUntilLedger)
    }

    @Test
    fun testGetLedgerEntriesResponseNoEntries() {
        val leJson = """
        {
            "latestLedger": 1000
        }
        """.trimIndent()
        val response = json.decodeFromString<GetLedgerEntriesResponse>(leJson)
        assertNull(response.entries)
        assertEquals(1000L, response.latestLedger)
    }

    @Test
    fun testGetLedgerEntriesResponseEmptyEntries() {
        val leJson = """
        {
            "entries": [],
            "latestLedger": 1000
        }
        """.trimIndent()
        val response = json.decodeFromString<GetLedgerEntriesResponse>(leJson)
        assertNotNull(response.entries)
        assertTrue(response.entries!!.isEmpty())
    }

    // ========== GetTransactionResponse Extended Tests ==========

    @Test
    fun testGetTransactionResponseSuccess() {
        val txJson = """
        {
            "status": "SUCCESS",
            "txHash": "successhash",
            "latestLedger": 6000,
            "latestLedgerCloseTime": 1609500000,
            "oldestLedger": 1000,
            "oldestLedgerCloseTime": 1609000000,
            "applicationOrder": 3,
            "feeBump": false,
            "envelopeXdr": "AAAAAQ==",
            "resultXdr": "AAAAAQ==",
            "resultMetaXdr": "AAAAAQ==",
            "ledger": 5000,
            "createdAt": 1609459200,
            "events": {
                "diagnosticEventsXdr": ["AAAAAQ=="]
            }
        }
        """.trimIndent()
        val response = json.decodeFromString<GetTransactionResponse>(txJson)
        assertEquals(GetTransactionStatus.SUCCESS, response.status)
        assertEquals("successhash", response.txHash)
        assertEquals(6000L, response.latestLedger)
        assertEquals(1609500000L, response.latestLedgerCloseTime)
        assertEquals(1000L, response.oldestLedger)
        assertEquals(1609000000L, response.oldestLedgerCloseTime)
        assertEquals(3, response.applicationOrder)
        assertEquals(false, response.feeBump)
        assertEquals("AAAAAQ==", response.envelopeXdr)
        assertEquals("AAAAAQ==", response.resultXdr)
        assertEquals("AAAAAQ==", response.resultMetaXdr)
        assertEquals(5000L, response.ledger)
        assertEquals(1609459200L, response.createdAt)
        assertNotNull(response.events)
    }

    @Test
    fun testGetTransactionResponseNotFound() {
        val txJson = """
        {
            "status": "NOT_FOUND",
            "latestLedger": 6000,
            "latestLedgerCloseTime": 1609500000,
            "oldestLedger": 1000,
            "oldestLedgerCloseTime": 1609000000
        }
        """.trimIndent()
        val response = json.decodeFromString<GetTransactionResponse>(txJson)
        assertEquals(GetTransactionStatus.NOT_FOUND, response.status)
        assertNull(response.txHash)
        assertNull(response.applicationOrder)
        assertNull(response.feeBump)
        assertNull(response.envelopeXdr)
        assertNull(response.resultXdr)
        assertNull(response.resultMetaXdr)
        assertNull(response.ledger)
        assertNull(response.createdAt)
        assertNull(response.events)
    }

    @Test
    fun testGetTransactionResponseFailed() {
        val txJson = """
        {
            "status": "FAILED",
            "txHash": "failedhash",
            "latestLedger": 6000,
            "applicationOrder": 1,
            "feeBump": true,
            "envelopeXdr": "AAAAAQ==",
            "resultXdr": "AAAAAQ==",
            "resultMetaXdr": "AAAAAQ==",
            "ledger": 5000,
            "createdAt": 1609459200
        }
        """.trimIndent()
        val response = json.decodeFromString<GetTransactionResponse>(txJson)
        assertEquals(GetTransactionStatus.FAILED, response.status)
        assertTrue(response.feeBump!!)
    }

    @Test
    fun testGetTransactionStatusEnum() {
        assertEquals("NOT_FOUND", GetTransactionStatus.NOT_FOUND.name)
        assertEquals("SUCCESS", GetTransactionStatus.SUCCESS.name)
        assertEquals("FAILED", GetTransactionStatus.FAILED.name)
    }

    @Test
    fun testGetTransactionResponseParseNullXdr() {
        val txJson = """
        {
            "status": "NOT_FOUND"
        }
        """.trimIndent()
        val response = json.decodeFromString<GetTransactionResponse>(txJson)
        assertNull(response.parseEnvelopeXdr())
        assertNull(response.parseResultXdr())
        assertNull(response.parseResultMetaXdr())
        assertNull(response.getResultValue())
        assertNull(response.getWasmId())
        assertNull(response.getCreatedContractId())
    }

    // ========== SendTransactionResponse Extended Tests ==========

    @Test
    fun testSendTransactionResponsePending() {
        val stJson = """
        {
            "status": "PENDING",
            "hash": "pendinghash",
            "latestLedger": 6000,
            "latestLedgerCloseTime": 1609500000
        }
        """.trimIndent()
        val response = json.decodeFromString<SendTransactionResponse>(stJson)
        assertEquals(SendTransactionStatus.PENDING, response.status)
        assertEquals("pendinghash", response.hash)
        assertEquals(6000L, response.latestLedger)
        assertEquals(1609500000L, response.latestLedgerCloseTime)
        assertNull(response.errorResultXdr)
        assertNull(response.diagnosticEventsXdr)
    }

    @Test
    fun testSendTransactionResponseDuplicate() {
        val stJson = """
        {
            "status": "DUPLICATE",
            "hash": "duphash"
        }
        """.trimIndent()
        val response = json.decodeFromString<SendTransactionResponse>(stJson)
        assertEquals(SendTransactionStatus.DUPLICATE, response.status)
    }

    @Test
    fun testSendTransactionResponseTryAgainLater() {
        val stJson = """
        {
            "status": "TRY_AGAIN_LATER"
        }
        """.trimIndent()
        val response = json.decodeFromString<SendTransactionResponse>(stJson)
        assertEquals(SendTransactionStatus.TRY_AGAIN_LATER, response.status)
        assertNull(response.hash)
        assertNull(response.latestLedger)
        assertNull(response.latestLedgerCloseTime)
    }

    @Test
    fun testSendTransactionResponseError() {
        val stJson = """
        {
            "status": "ERROR",
            "errorResultXdr": "AAAAAQ==",
            "diagnosticEventsXdr": ["AAAAAQ==", "AAAAAg=="],
            "latestLedger": 6000,
            "latestLedgerCloseTime": 1609500000
        }
        """.trimIndent()
        val response = json.decodeFromString<SendTransactionResponse>(stJson)
        assertEquals(SendTransactionStatus.ERROR, response.status)
        assertNotNull(response.errorResultXdr)
        assertNotNull(response.diagnosticEventsXdr)
        assertEquals(2, response.diagnosticEventsXdr!!.size)
    }

    @Test
    fun testSendTransactionStatusEnum() {
        assertEquals("PENDING", SendTransactionStatus.PENDING.name)
        assertEquals("DUPLICATE", SendTransactionStatus.DUPLICATE.name)
        assertEquals("TRY_AGAIN_LATER", SendTransactionStatus.TRY_AGAIN_LATER.name)
        assertEquals("ERROR", SendTransactionStatus.ERROR.name)
    }

    @Test
    fun testSendTransactionResponseParseNulls() {
        val stJson = """{"status": "PENDING"}"""
        val response = json.decodeFromString<SendTransactionResponse>(stJson)
        assertNull(response.parseErrorResultXdr())
        assertNull(response.parseDiagnosticEventsXdr())
    }

    // ========== SimulateTransactionResponse Extended Tests ==========

    @Test
    fun testSimulateTransactionResponseSuccess() {
        val simJson = """
        {
            "transactionData": "AAAAAQ==",
            "events": ["AAAAAQ=="],
            "minResourceFee": 100000,
            "results": [
                {
                    "auth": ["AAAAAQ=="],
                    "xdr": "AAAAAQ=="
                }
            ],
            "latestLedger": 6000
        }
        """.trimIndent()
        val response = json.decodeFromString<SimulateTransactionResponse>(simJson)
        assertNull(response.error)
        assertNotNull(response.transactionData)
        assertNotNull(response.events)
        assertEquals(1, response.events!!.size)
        assertEquals(100000L, response.minResourceFee)
        assertNotNull(response.results)
        assertEquals(1, response.results!!.size)
        assertNotNull(response.results!![0].auth)
        assertNotNull(response.results!![0].xdr)
        assertNull(response.restorePreamble)
        assertNull(response.stateChanges)
        assertEquals(6000L, response.latestLedger)
    }

    @Test
    fun testSimulateTransactionResponseError() {
        val simJson = """
        {
            "error": "Transaction simulation failed",
            "latestLedger": 6000
        }
        """.trimIndent()
        val response = json.decodeFromString<SimulateTransactionResponse>(simJson)
        assertEquals("Transaction simulation failed", response.error)
        assertNull(response.transactionData)
        assertNull(response.events)
        assertNull(response.minResourceFee)
        assertNull(response.results)
        assertNull(response.restorePreamble)
        assertNull(response.stateChanges)
    }

    @Test
    fun testSimulateTransactionResponseRestorePreamble() {
        val simJson = """
        {
            "restorePreamble": {
                "transactionData": "AAAAAQ==",
                "minResourceFee": 50000
            },
            "latestLedger": 6000
        }
        """.trimIndent()
        val response = json.decodeFromString<SimulateTransactionResponse>(simJson)
        assertNotNull(response.restorePreamble)
        assertEquals("AAAAAQ==", response.restorePreamble!!.transactionData)
        assertEquals(50000L, response.restorePreamble!!.minResourceFee)
    }

    @Test
    fun testSimulateTransactionResponseStateChanges() {
        val simJson = """
        {
            "stateChanges": [
                {
                    "type": "created",
                    "key": "AAAAAQ==",
                    "after": "AAAAAg=="
                },
                {
                    "type": "updated",
                    "key": "AAAAAw==",
                    "before": "AAAAAQ==",
                    "after": "AAAAAg=="
                },
                {
                    "type": "deleted",
                    "key": "AAAAAQ==",
                    "before": "AAAAAg=="
                }
            ],
            "latestLedger": 6000
        }
        """.trimIndent()
        val response = json.decodeFromString<SimulateTransactionResponse>(simJson)
        assertNotNull(response.stateChanges)
        assertEquals(3, response.stateChanges!!.size)

        val created = response.stateChanges!![0]
        assertEquals("created", created.type)
        assertEquals("AAAAAQ==", created.key)
        assertNull(created.before)
        assertEquals("AAAAAg==", created.after)

        val updated = response.stateChanges!![1]
        assertEquals("updated", updated.type)
        assertNotNull(updated.before)
        assertNotNull(updated.after)

        val deleted = response.stateChanges!![2]
        assertEquals("deleted", deleted.type)
        assertNotNull(deleted.before)
        assertNull(deleted.after)
    }

    @Test
    fun testSimulateHostFunctionResultMinimal() {
        val simJson = """
        {
            "results": [
                {}
            ],
            "latestLedger": 6000
        }
        """.trimIndent()
        val response = json.decodeFromString<SimulateTransactionResponse>(simJson)
        val result = response.results!![0]
        assertNull(result.auth)
        assertNull(result.xdr)
        assertNull(result.parseAuth())
        assertNull(result.parseXdr())
    }

    @Test
    fun testSimulateTransactionResponseParseNulls() {
        val simJson = """{"latestLedger": 100}"""
        val response = json.decodeFromString<SimulateTransactionResponse>(simJson)
        assertNull(response.parseTransactionData())
        assertNull(response.parseEvents())
    }

    @Test
    fun testLedgerEntryChangeParseBefore_null() {
        val simJson = """
        {
            "stateChanges": [
                {
                    "type": "created",
                    "key": "AAAAAQ==",
                    "after": "AAAAAg=="
                }
            ],
            "latestLedger": 100
        }
        """.trimIndent()
        val response = json.decodeFromString<SimulateTransactionResponse>(simJson)
        val change = response.stateChanges!![0]
        assertNull(change.parseBefore())
    }

    @Test
    fun testLedgerEntryChangeParseAfter_null() {
        val simJson = """
        {
            "stateChanges": [
                {
                    "type": "deleted",
                    "key": "AAAAAQ==",
                    "before": "AAAAAg=="
                }
            ],
            "latestLedger": 100
        }
        """.trimIndent()
        val response = json.decodeFromString<SimulateTransactionResponse>(simJson)
        val change = response.stateChanges!![0]
        assertNull(change.parseAfter())
    }

    // ========== SorobanRpcResponse Extended Tests ==========

    @Test
    fun testSorobanRpcResponseToString_success() {
        val rpcJson = """
        {
            "jsonrpc": "2.0",
            "id": "test-1",
            "result": {
                "status": "healthy"
            }
        }
        """.trimIndent()
        val response: SorobanRpcResponse<GetHealthResponse> = json.decodeFromString(rpcJson)
        val str = response.toString()
        assertTrue(str.contains("jsonRpc='2.0'"))
        assertTrue(str.contains("id='test-1'"))
        assertTrue(str.contains("result="))
        assertFalse(str.contains("error="))
    }

    @Test
    fun testSorobanRpcResponseToString_error() {
        val rpcJson = """
        {
            "jsonrpc": "2.0",
            "id": "test-2",
            "error": {
                "code": -32601,
                "message": "method not found"
            }
        }
        """.trimIndent()
        val response: SorobanRpcResponse<GetHealthResponse> = json.decodeFromString(rpcJson)
        val str = response.toString()
        assertTrue(str.contains("error="))
        assertFalse(str.contains("result="))
    }

    @Test
    fun testSorobanRpcResponseWithErrorData() {
        val rpcJson = """
        {
            "jsonrpc": "2.0",
            "id": "test-3",
            "error": {
                "code": -32000,
                "message": "Server error",
                "data": "additional details"
            }
        }
        """.trimIndent()
        val response: SorobanRpcResponse<GetHealthResponse> = json.decodeFromString(rpcJson)
        assertTrue(response.isError())
        assertFalse(response.isSuccess())
        assertEquals(-32000, response.error!!.code)
        assertEquals("Server error", response.error!!.message)
        assertEquals("additional details", response.error!!.data)
    }

    @Test
    fun testSorobanRpcResponseWithoutErrorData() {
        val rpcJson = """
        {
            "jsonrpc": "2.0",
            "id": "test-4",
            "error": {
                "code": -32600,
                "message": "Invalid Request"
            }
        }
        """.trimIndent()
        val response: SorobanRpcResponse<GetHealthResponse> = json.decodeFromString(rpcJson)
        assertNull(response.error!!.data)
    }

    // ========== Events Extended Tests ==========

    @Test
    fun testEventsFull() {
        val eventsJson = """
        {
            "diagnosticEventsXdr": ["AAAAAQ==", "AAAAAg=="],
            "transactionEventsXdr": ["AAAAAQ=="],
            "contractEventsXdr": [["AAAAAQ==", "AAAAAg=="], ["AAAAAw=="]]
        }
        """.trimIndent()
        val events = json.decodeFromString<Events>(eventsJson)
        assertNotNull(events.diagnosticEventsXdr)
        assertEquals(2, events.diagnosticEventsXdr!!.size)
        assertNotNull(events.transactionEventsXdr)
        assertEquals(1, events.transactionEventsXdr!!.size)
        assertNotNull(events.contractEventsXdr)
        assertEquals(2, events.contractEventsXdr!!.size)
        assertEquals(2, events.contractEventsXdr!![0].size)
        assertEquals(1, events.contractEventsXdr!![1].size)
    }

    @Test
    fun testEventsMinimal() {
        val eventsJson = """{}"""
        val events = json.decodeFromString<Events>(eventsJson)
        assertNull(events.diagnosticEventsXdr)
        assertNull(events.transactionEventsXdr)
        assertNull(events.contractEventsXdr)
        assertNull(events.parseDiagnosticEventsXdr())
        assertNull(events.parseTransactionEventsXdr())
        assertNull(events.parseContractEventsXdr())
    }

    // ========== GetFeeStatsResponse Extended Tests ==========

    @Test
    fun testGetFeeStatsResponseFull() {
        val feeJson = """
        {
            "sorobanInclusionFee": {
                "max": 10000, "min": 100, "mode": 200,
                "p10": 100, "p20": 100, "p30": 150, "p40": 200,
                "p50": 200, "p60": 300, "p70": 500, "p80": 1000,
                "p90": 5000, "p95": 8000, "p99": 10000,
                "transactionCount": 500, "ledgerCount": 50
            },
            "inclusionFee": {
                "max": 5000, "min": 100, "mode": 100,
                "p10": 100, "p20": 100, "p30": 100, "p40": 100,
                "p50": 100, "p60": 200, "p70": 300, "p80": 500,
                "p90": 1000, "p95": 3000, "p99": 5000,
                "transactionCount": 1000, "ledgerCount": 100
            },
            "latestLedger": 6000
        }
        """.trimIndent()
        val response = json.decodeFromString<GetFeeStatsResponse>(feeJson)
        assertEquals(6000L, response.latestLedger)
        
        // Soroban inclusion fee
        assertEquals(10000L, response.sorobanInclusionFee.max)
        assertEquals(100L, response.sorobanInclusionFee.min)
        assertEquals(200L, response.sorobanInclusionFee.mode)
        assertEquals(100L, response.sorobanInclusionFee.p10)
        assertEquals(100L, response.sorobanInclusionFee.p20)
        assertEquals(150L, response.sorobanInclusionFee.p30)
        assertEquals(200L, response.sorobanInclusionFee.p40)
        assertEquals(200L, response.sorobanInclusionFee.p50)
        assertEquals(300L, response.sorobanInclusionFee.p60)
        assertEquals(500L, response.sorobanInclusionFee.p70)
        assertEquals(1000L, response.sorobanInclusionFee.p80)
        assertEquals(5000L, response.sorobanInclusionFee.p90)
        assertEquals(8000L, response.sorobanInclusionFee.p95)
        assertEquals(10000L, response.sorobanInclusionFee.p99)
        assertEquals(500L, response.sorobanInclusionFee.transactionCount)
        assertEquals(50L, response.sorobanInclusionFee.ledgerCount)
        
        // Standard inclusion fee
        assertEquals(5000L, response.inclusionFee.max)
        assertEquals(100L, response.inclusionFee.min)
        assertEquals(100L, response.inclusionFee.mode)
        assertEquals(1000L, response.inclusionFee.transactionCount)
        assertEquals(100L, response.inclusionFee.ledgerCount)
    }

    // ========== GetHealthResponse Extended Tests ==========

    @Test
    fun testGetHealthResponseFull() {
        val healthJson = """
        {
            "status": "healthy",
            "latestLedger": 6000,
            "oldestLedger": 1000,
            "ledgerRetentionWindow": 17280
        }
        """.trimIndent()
        val response = json.decodeFromString<GetHealthResponse>(healthJson)
        assertEquals("healthy", response.status)
        assertEquals(6000L, response.latestLedger)
        assertEquals(1000L, response.oldestLedger)
        assertEquals(17280L, response.ledgerRetentionWindow)
    }

    @Test
    fun testGetHealthResponseMinimal() {
        val healthJson = """{"status": "healthy"}"""
        val response = json.decodeFromString<GetHealthResponse>(healthJson)
        assertEquals("healthy", response.status)
        assertNull(response.latestLedger)
        assertNull(response.oldestLedger)
        assertNull(response.ledgerRetentionWindow)
    }

    // ========== GetLatestLedgerResponse Extended Tests ==========

    @Test
    fun testGetLatestLedgerResponse() {
        val llJson = """
        {
            "id": "abc123hash",
            "protocolVersion": 21,
            "sequence": 50000
        }
        """.trimIndent()
        val response = json.decodeFromString<GetLatestLedgerResponse>(llJson)
        assertEquals("abc123hash", response.id)
        assertEquals(21, response.protocolVersion)
        assertEquals(50000L, response.sequence)
    }

    // ========== GetNetworkResponse Extended Tests ==========

    @Test
    fun testGetNetworkResponseFull() {
        val netJson = """
        {
            "friendbotUrl": "https://friendbot.stellar.org",
            "passphrase": "Test SDF Network ; September 2015",
            "protocolVersion": 21
        }
        """.trimIndent()
        val response = json.decodeFromString<GetNetworkResponse>(netJson)
        assertEquals("https://friendbot.stellar.org", response.friendbotUrl)
        assertEquals("Test SDF Network ; September 2015", response.passphrase)
        assertEquals(21, response.protocolVersion)
    }

    @Test
    fun testGetNetworkResponseNoFriendbot() {
        val netJson = """
        {
            "passphrase": "Public Global Stellar Network ; September 2015",
            "protocolVersion": 21
        }
        """.trimIndent()
        val response = json.decodeFromString<GetNetworkResponse>(netJson)
        assertNull(response.friendbotUrl)
        assertEquals("Public Global Stellar Network ; September 2015", response.passphrase)
    }

    // ========== GetVersionInfoResponse Extended Tests ==========

    @Test
    fun testGetVersionInfoResponse() {
        val viJson = """
        {
            "version": "21.0.0",
            "commitHash": "abc123def456",
            "buildTimestamp": "2024-01-15T10:30:00Z",
            "captiveCoreVersion": "v19.14.0",
            "protocolVersion": 21
        }
        """.trimIndent()
        val response = json.decodeFromString<GetVersionInfoResponse>(viJson)
        assertEquals("21.0.0", response.version)
        assertEquals("abc123def456", response.commitHash)
        assertEquals("2024-01-15T10:30:00Z", response.buildTimestamp)
        assertEquals("v19.14.0", response.captiveCoreVersion)
        assertEquals(21, response.protocolVersion)
    }

    // ========== GetSACBalanceResponse Extended Tests ==========

    @Test
    fun testGetSACBalanceResponseFull() {
        val sacJson = """
        {
            "balanceEntry": {
                "amount": "10000000",
                "authorized": true,
                "clawback": false,
                "lastModifiedLedgerSeq": 5000,
                "liveUntilLedgerSeq": 10000
            },
            "latestLedger": 6000
        }
        """.trimIndent()
        val response = json.decodeFromString<GetSACBalanceResponse>(sacJson)
        assertNotNull(response.balanceEntry)
        assertEquals("10000000", response.balanceEntry!!.amount)
        assertTrue(response.balanceEntry!!.authorized)
        assertFalse(response.balanceEntry!!.clawback)
        assertEquals(5000L, response.balanceEntry!!.lastModifiedLedgerSeq)
        assertEquals(10000L, response.balanceEntry!!.liveUntilLedgerSeq)
        assertEquals(6000L, response.latestLedger)
        
        // Helper methods
        assertEquals(10000000L, response.balanceEntry!!.getAmountAsLong())
        assertTrue(response.balanceEntry!!.isTemporary())
        assertTrue(response.balanceEntry!!.willBeArchivedBy(10000L))
        assertTrue(response.balanceEntry!!.willBeArchivedBy(20000L))
        assertFalse(response.balanceEntry!!.willBeArchivedBy(5000L))
    }

    @Test
    fun testGetSACBalanceResponseNoBalance() {
        val sacJson = """
        {
            "latestLedger": 6000
        }
        """.trimIndent()
        val response = json.decodeFromString<GetSACBalanceResponse>(sacJson)
        assertNull(response.balanceEntry)
        assertEquals(6000L, response.latestLedger)
    }

    @Test
    fun testGetSACBalanceEntryPersistent() {
        val sacJson = """
        {
            "balanceEntry": {
                "amount": "5000000",
                "authorized": false,
                "clawback": true,
                "lastModifiedLedgerSeq": 3000
            },
            "latestLedger": 6000
        }
        """.trimIndent()
        val response = json.decodeFromString<GetSACBalanceResponse>(sacJson)
        val entry = response.balanceEntry!!
        assertFalse(entry.authorized)
        assertTrue(entry.clawback)
        assertNull(entry.liveUntilLedgerSeq)
        assertFalse(entry.isTemporary())
        assertFalse(entry.willBeArchivedBy(999999L))
    }

    // ========== EventFilterType enum tests ==========

    @Test
    fun testEventFilterTypeValues() {
        assertEquals(3, EventFilterType.entries.size)
        assertTrue(EventFilterType.entries.contains(EventFilterType.CONTRACT))
        assertTrue(EventFilterType.entries.contains(EventFilterType.SYSTEM))
        assertTrue(EventFilterType.entries.contains(EventFilterType.DIAGNOSTIC))
    }
}
