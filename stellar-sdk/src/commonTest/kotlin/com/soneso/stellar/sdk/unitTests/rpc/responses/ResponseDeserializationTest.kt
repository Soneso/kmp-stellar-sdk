package com.soneso.stellar.sdk.unitTests.rpc.responses

import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.rpc.responses.*
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.unitTests.xdr.XdrTestHelpers
import com.soneso.stellar.sdk.xdr.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Tests for response model deserialization.
 *
 * Covers every RPC response model to ensure proper JSON deserialization with correct
 * field names, enum handling, XDR parsing helpers, and null handling.
 */
class ResponseDeserializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = false
    }

    // ========== SorobanRpcResponse Tests ==========

    @Test
    fun testSorobanRpcResponse_successfulResult_deserializes() {
        // Given: JSON with successful result
        val jsonString = """{
            "jsonrpc": "2.0",
            "id": "test-123",
            "result": {
                "status": "healthy"
            }
        }"""

        // When: Deserializing
        val response: SorobanRpcResponse<GetHealthResponse> = json.decodeFromString(jsonString)

        // Then: Result is populated, error is null
        assertEquals("2.0", response.jsonRpc)
        assertEquals("test-123", response.id)
        assertNotNull(response.result)
        assertNull(response.error)
        assertTrue(response.isSuccess())
        assertFalse(response.isError())
        assertEquals("healthy", response.result?.status)
    }

    @Test
    fun testSorobanRpcResponse_errorResult_deserializes() {
        // Given: JSON with error
        val jsonString = """{
            "jsonrpc": "2.0",
            "id": "test-456",
            "error": {
                "code": -32601,
                "message": "method not found",
                "data": "additional info"
            }
        }"""

        // When: Deserializing
        val response: SorobanRpcResponse<GetHealthResponse> = json.decodeFromString(jsonString)

        // Then: Error is populated, result is null
        assertEquals("2.0", response.jsonRpc)
        assertEquals("test-456", response.id)
        assertNull(response.result)
        assertNotNull(response.error)
        assertFalse(response.isSuccess())
        assertTrue(response.isError())
        assertEquals(-32601, response.error?.code)
        assertEquals("method not found", response.error?.message)
        assertEquals("additional info", response.error?.data)
    }

    @Test
    fun testSorobanRpcResponse_errorWithoutData_deserializes() {
        // Given: JSON with error but no data field
        val jsonString = """{
            "jsonrpc": "2.0",
            "id": "test-789",
            "error": {
                "code": -32600,
                "message": "Invalid Request"
            }
        }"""

        // When: Deserializing
        val response: SorobanRpcResponse<GetHealthResponse> = json.decodeFromString(jsonString)

        // Then: Error data is null
        assertNotNull(response.error)
        assertEquals(-32600, response.error?.code)
        assertEquals("Invalid Request", response.error?.message)
        assertNull(response.error?.data)
    }

    // ========== GetHealthResponse Tests ==========

    @Test
    fun testGetHealthResponse_fullData_deserializes() {
        // Given: Full health response JSON
        val jsonString = """{
            "status": "healthy",
            "latestLedger": 50000,
            "oldestLedger": 1,
            "ledgerRetentionWindow": 10000
        }"""

        // When: Deserializing
        val response: GetHealthResponse = json.decodeFromString(jsonString)

        // Then: All fields are populated
        assertEquals("healthy", response.status)
        assertEquals(50000L, response.latestLedger)
        assertEquals(1L, response.oldestLedger)
        assertEquals(10000L, response.ledgerRetentionWindow)
    }

    @Test
    fun testGetHealthResponse_minimalData_deserializes() {
        // Given: Health response with only status
        val jsonString = """{"status": "healthy"}"""

        // When: Deserializing
        val response: GetHealthResponse = json.decodeFromString(jsonString)

        // Then: Optional fields are null
        assertEquals("healthy", response.status)
        assertNull(response.latestLedger)
        assertNull(response.oldestLedger)
        assertNull(response.ledgerRetentionWindow)
    }

    // ========== GetNetworkResponse Tests ==========

    @Test
    fun testGetNetworkResponse_withFriendbot_deserializes() {
        // Given: Network response with friendbot URL
        val jsonString = """{
            "friendbotUrl": "https://friendbot-futurenet.stellar.org/",
            "passphrase": "Test SDF Future Network ; October 2022",
            "protocolVersion": 20
        }"""

        // When: Deserializing
        val response: GetNetworkResponse = json.decodeFromString(jsonString)

        // Then: All fields are populated
        assertEquals("https://friendbot-futurenet.stellar.org/", response.friendbotUrl)
        assertEquals("Test SDF Future Network ; October 2022", response.passphrase)
        assertEquals(20, response.protocolVersion)
    }

    @Test
    fun testGetNetworkResponse_withoutFriendbot_deserializes() {
        // Given: Network response without friendbot (mainnet)
        val jsonString = """{
            "passphrase": "Public Global Stellar Network ; September 2015",
            "protocolVersion": 20
        }"""

        // When: Deserializing
        val response: GetNetworkResponse = json.decodeFromString(jsonString)

        // Then: Friendbot URL is null
        assertNull(response.friendbotUrl)
        assertEquals("Public Global Stellar Network ; September 2015", response.passphrase)
        assertEquals(20, response.protocolVersion)
    }

    // ========== SimulateTransactionResponse Tests ==========

    @Test
    fun testSimulateTransactionResponse_successfulSimulation_deserializes() {
        // Given: Successful simulation response
        val jsonString = """{
            "transactionData": "AAAAAAAAAAIAAAAGAAAAAem354u9STQWq5b3Ed1j9tOemvL7xV0NPwhn4gXg0AP8AAAAFAAAAAE=",
            "minResourceFee": "58181",
            "events": ["AAAAAQAAAAAAAAAAAAAAAgAAAAAAAAADAAAADw=="],
            "results": [{
                "auth": ["AAAAAAAAAAAAAAAB6bfni71JNBarlvcR3WP2056a8vvFXQ0="],
                "xdr": "AAAAAwAAABQ="
            }],
            "latestLedger": "14245"
        }"""

        // When: Deserializing
        val response: SimulateTransactionResponse = json.decodeFromString(jsonString)

        // Then: All success fields are populated, optional sections stay null
        assertNull(response.error)
        assertNotNull(response.transactionData)
        assertEquals(58181L, response.minResourceFee)
        assertEquals(1, response.events?.size)
        assertEquals(1, response.results?.size)
        assertEquals(14245L, response.latestLedger)
        assertEquals(1, response.results?.get(0)?.auth?.size)
        assertEquals("AAAAAwAAABQ=", response.results?.get(0)?.xdr)
        assertNull(response.restorePreamble)
        assertNull(response.stateChanges)
    }

    @Test
    fun testSimulateTransactionResponse_errorSimulation_deserializes() {
        // Given: Simulation with error
        val jsonString = """{
            "error": "HostError: Error(WasmVm, InvalidAction)",
            "latestLedger": "14245"
        }"""

        // When: Deserializing
        val response: SimulateTransactionResponse = json.decodeFromString(jsonString)

        // Then: Error is populated, other fields are null
        assertEquals("HostError: Error(WasmVm, InvalidAction)", response.error)
        assertEquals(14245L, response.latestLedger)
        assertNull(response.transactionData)
        assertNull(response.minResourceFee)
        assertNull(response.events)
        assertNull(response.results)
        assertNull(response.restorePreamble)
        assertNull(response.stateChanges)
    }

    @Test
    fun testSimulateTransactionResponse_withRestorePreamble_deserializes() {
        // Given: Simulation requiring restore operation
        val jsonString = """{
            "restorePreamble": {
                "transactionData": "AAAAAAAAAAIAAAAGAAAAAem354u9STQWq5b3Ed1j9tOemvL7xV0NPwhn4gXg0AP8=",
                "minResourceFee": "1000"
            },
            "latestLedger": "14245"
        }"""

        // When: Deserializing
        val response: SimulateTransactionResponse = json.decodeFromString(jsonString)

        // Then: Restore preamble is populated
        assertNotNull(response.restorePreamble)
        assertEquals("AAAAAAAAAAIAAAAGAAAAAem354u9STQWq5b3Ed1j9tOemvL7xV0NPwhn4gXg0AP8=",
                     response.restorePreamble?.transactionData)
        assertEquals(1000L, response.restorePreamble?.minResourceFee)
        assertEquals(14245L, response.latestLedger)
    }

    @Test
    fun testSimulateTransactionResponse_withStateChanges_deserializes() {
        // Given: Simulation reporting a created, an updated and a deleted ledger entry
        val jsonString = """{
            "stateChanges": [{
                "type": "created",
                "key": "AAAAAAAAAABuaCbVXZ2DlXWarV6UxwbW3GNJgpn3ASChIFp5bxSIWg==",
                "before": null,
                "after": "AAAAZAAAAAAAAAAAbmgm1V2dg5V1mq1elMcG1txjSYKZ9wEgoSBaeW8UiFo="
            }, {
                "type": "updated",
                "key": "AAAAAw==",
                "before": "AAAAAQ==",
                "after": "AAAAAg=="
            }, {
                "type": "deleted",
                "key": "AAAAAQ==",
                "before": "AAAAAg==",
                "after": null
            }],
            "latestLedger": "14245"
        }"""

        // When: Deserializing
        val response: SimulateTransactionResponse = json.decodeFromString(jsonString)

        // Then: Every change type maps its before/after states
        assertEquals(3, response.stateChanges?.size)

        val created = response.stateChanges?.get(0)
        assertEquals("created", created?.type)
        assertEquals("AAAAAAAAAABuaCbVXZ2DlXWarV6UxwbW3GNJgpn3ASChIFp5bxSIWg==", created?.key)
        assertNull(created?.before)
        assertNotNull(created?.after)

        val updated = response.stateChanges?.get(1)
        assertEquals("updated", updated?.type)
        assertEquals("AAAAAw==", updated?.key)
        assertEquals("AAAAAQ==", updated?.before)
        assertEquals("AAAAAg==", updated?.after)

        val deleted = response.stateChanges?.get(2)
        assertEquals("deleted", deleted?.type)
        assertEquals("AAAAAg==", deleted?.before)
        assertNull(deleted?.after)
    }

    @Test
    fun testSimulateHostFunctionResult_emptyAuth_deserializes() {
        // Given: Result with null auth
        val jsonString = """{"xdr": "AAAAAwAAABQ="}"""

        // When: Deserializing
        val result: SimulateTransactionResponse.SimulateHostFunctionResult = json.decodeFromString(jsonString)

        // Then: Auth is null
        assertNull(result.auth)
        assertEquals("AAAAAwAAABQ=", result.xdr)
    }

    @Test
    fun testSimulateHostFunctionResult_emptyObject_deserializes() {
        // Given: Simulation whose result object carries no fields
        val jsonString = """{
            "results": [{}],
            "latestLedger": "6000"
        }"""

        // When: Deserializing
        val response: SimulateTransactionResponse = json.decodeFromString(jsonString)

        // Then: Both fields and their parse helpers are null
        val result = response.results!![0]
        assertNull(result.auth)
        assertNull(result.xdr)
        assertNull(result.parseAuth())
        assertNull(result.parseXdr())
    }

    // ========== XDR Parsing Helper Tests ==========

    @Test
    fun testSimulateTransactionResponse_parseTransactionData_parsesXdr() {
        // Given: Response with transaction data
        val response = SimulateTransactionResponse(
            transactionData = "AAAAAAAAAAIAAAAGAAAAAem354u9STQWq5b3Ed1j9tOemvL7xV0NPwhn4gXg0AP8AAAAFAAAAAEAAAAH8dTe2OoI0BnhlDbH0fWvXmvprkBvBAgKIcL9busuuMEAAAABAAAABgAAAAHpt+eLvUk0FquW9xHdY/bTnpry+8VdDT8IZ+IF4NAD/AAAABAAAAABAAAAAgAAAA8AAAAHQ291bnRlcgAAAAASAAAAAAAAAABYt8SiyPKXqo89JHEoH9/M7K/kjlZjMT7BjhKnPsqYoQAAAAEAHifGAAAFlAAAAIgAAAAAAAAAAg=="
        )

        // When: Calling parse helper
        val parsed = response.parseTransactionData()

        // Then: XDR is successfully parsed
        assertNotNull(parsed)
    }

    @Test
    fun testSimulateTransactionResponse_parseEvents_parsesXdr() {
        // Given: Response with events
        val response = SimulateTransactionResponse(
            events = listOf("AAAAAQAAAAAAAAAAAAAAAgAAAAAAAAADAAAADwAAAAdmbl9jYWxsAAAAAA0AAAAg6bfni71JNBarlvcR3WP2056a8vvFXQ0/CGfiBeDQA/wAAAAPAAAACWluY3JlbWVudAAAAAAAABAAAAABAAAAAgAAABIAAAAAAAAAAFi3xKLI8peqjz0kcSgf38zsr+SOVmMxPsGOEqc+ypihAAAAAwAAAAo=")
        )

        // When: Calling parse helper
        val parsed = response.parseEvents()

        // Then: XDR is successfully parsed
        assertNotNull(parsed)
        assertEquals(1, parsed?.size)
    }

    @Test
    fun testSimulateHostFunctionResult_parseAuth_parsesXdr() {
        // Given: Result with auth
        val result = SimulateTransactionResponse.SimulateHostFunctionResult(
            auth = listOf("AAAAAAAAAAAAAAAB6bfni71JNBarlvcR3WP2056a8vvFXQ0/CGfiBeDQA/wAAAAJaW5jcmVtZW50AAAAAAAAAgAAABIAAAAAAAAAAFi3xKLI8peqjz0kcSgf38zsr+SOVmMxPsGOEqc+ypihAAAAAwAAAAoAAAAA")
        )

        // When: Calling parse helper
        val parsed = result.parseAuth()

        // Then: XDR is successfully parsed
        assertNotNull(parsed)
        assertEquals(1, parsed?.size)
    }

    @Test
    fun testSimulateHostFunctionResult_parseXdr_parsesXdr() {
        // Given: Result with xdr
        val result = SimulateTransactionResponse.SimulateHostFunctionResult(
            xdr = "AAAAAwAAABQ="
        )

        // When: Calling parse helper
        val parsed = result.parseXdr()

        // Then: XDR is successfully parsed
        assertNotNull(parsed)
    }

    @Test
    fun testLedgerEntryChange_parseKey_parsesXdr() {
        // Given: State change with key
        val change = SimulateTransactionResponse.LedgerEntryChange(
            type = "created",
            key = "AAAAAAAAAABuaCbVXZ2DlXWarV6UxwbW3GNJgpn3ASChIFp5bxSIWg=="
        )

        // When: Calling parse helper
        val parsed = change.parseKey()

        // Then: XDR is successfully parsed
        assertNotNull(parsed)
    }

    @Test
    fun testRestorePreamble_parseTransactionData_parsesXdr() {
        // Given: Restore preamble
        val preamble = SimulateTransactionResponse.RestorePreamble(
            transactionData = "AAAAAAAAAAIAAAAGAAAAAem354u9STQWq5b3Ed1j9tOemvL7xV0NPwhn4gXg0AP8AAAAFAAAAAEAAAAH8dTe2OoI0BnhlDbH0fWvXmvprkBvBAgKIcL9busuuMEAAAABAAAABgAAAAHpt+eLvUk0FquW9xHdY/bTnpry+8VdDT8IZ+IF4NAD/AAAABAAAAABAAAAAgAAAA8AAAAHQ291bnRlcgAAAAASAAAAAAAAAABYt8SiyPKXqo89JHEoH9/M7K/kjlZjMT7BjhKnPsqYoQAAAAEAHifGAAAFlAAAAIgAAAAAAAAAAg==",
            minResourceFee = 1000
        )

        // When: Calling parse helper
        val parsed = preamble.parseTransactionData()

        // Then: XDR is successfully parsed
        assertNotNull(parsed)
    }

    // ========== Null Handling Tests ==========

    @Test
    fun testSimulateTransactionResponse_parseTransactionData_nullReturnsNull() {
        // Given: Response without transaction data
        val response = SimulateTransactionResponse(
            transactionData = null
        )

        // When: Calling parse helper
        val result = response.parseTransactionData()

        // Then: Returns null (doesn't throw)
        assertNull(result)
    }

    @Test
    fun testSimulateTransactionResponse_parseEvents_nullReturnsNull() {
        // Given: Response without events
        val response = SimulateTransactionResponse(
            events = null
        )

        // When: Calling parse helper
        val result = response.parseEvents()

        // Then: Returns null (doesn't throw)
        assertNull(result)
    }

    @Test
    fun testSimulateHostFunctionResult_parseAuth_nullReturnsNull() {
        // Given: Result without auth
        val result = SimulateTransactionResponse.SimulateHostFunctionResult(
            auth = null
        )

        // When: Calling parse helper
        val authResult = result.parseAuth()

        // Then: Returns null (doesn't throw)
        assertNull(authResult)
    }

    @Test
    fun testSimulateHostFunctionResult_parseXdr_nullReturnsNull() {
        // Given: Result without xdr
        val result = SimulateTransactionResponse.SimulateHostFunctionResult(
            xdr = null
        )

        // When: Calling parse helper
        val xdrResult = result.parseXdr()

        // Then: Returns null (doesn't throw)
        assertNull(xdrResult)
    }

    @Test
    fun testLedgerEntryChange_parseBefore_nullReturnsNull() {
        // Given: Change without before state (creation)
        val change = SimulateTransactionResponse.LedgerEntryChange(
            type = "created",
            key = "AAA=",
            before = null,
            after = "BBB="
        )

        // When: Calling parse helper
        val beforeResult = change.parseBefore()

        // Then: Returns null (doesn't throw)
        assertNull(beforeResult)
    }

    @Test
    fun testLedgerEntryChange_parseAfter_nullReturnsNull() {
        // Given: Change without after state (deletion)
        val change = SimulateTransactionResponse.LedgerEntryChange(
            type = "deleted",
            key = "AAA=",
            before = "BBB=",
            after = null
        )

        // When: Calling parse helper
        val afterResult = change.parseAfter()

        // Then: Returns null (doesn't throw)
        assertNull(afterResult)
    }

    // ========== GetLatestLedgerResponse Tests ==========

    @Test
    fun testGetLatestLedgerResponse_deserializes() {
        val jsonString = """{
            "id": "b789e0caef47ac1f1ef6fd9f21a6bbf625e5cb575c9b08c5d4a11e2e0a688dac",
            "protocolVersion": 20,
            "sequence": 50000,
            "closeTime": 1700000000,
            "headerXdr": "AAAAIKN0fRh...",
            "metadataXdr": "AAAAAwAAAA..."
        }"""

        val response: GetLatestLedgerResponse = json.decodeFromString(jsonString)

        assertEquals("b789e0caef47ac1f1ef6fd9f21a6bbf625e5cb575c9b08c5d4a11e2e0a688dac", response.id)
        assertEquals(20, response.protocolVersion)
        assertEquals(50000L, response.sequence)
        assertEquals(1700000000L, response.closeTime)
        assertEquals("AAAAIKN0fRh...", response.headerXdr)
        assertEquals("AAAAAwAAAA...", response.metadataXdr)
    }

    @Test
    fun testGetLatestLedgerResponse_withoutOptionalFields() {
        val jsonString = """{
            "id": "abc123hash",
            "protocolVersion": 21,
            "sequence": 50000
        }"""

        val response: GetLatestLedgerResponse = json.decodeFromString(jsonString)

        assertEquals("abc123hash", response.id)
        assertEquals(21, response.protocolVersion)
        assertEquals(50000L, response.sequence)
        assertNull(response.closeTime)
        assertNull(response.headerXdr)
        assertNull(response.metadataXdr)
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
        // Fee values arrive as JSON strings and are decoded into Long fields
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

    @Test
    fun testGetFeeStatsResponse_allPercentiles_deserializes() {
        val jsonString = """{
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
        }"""

        val response: GetFeeStatsResponse = json.decodeFromString(jsonString)

        assertEquals(6000L, response.latestLedger)

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

        assertEquals(5000L, response.inclusionFee.max)
        assertEquals(100L, response.inclusionFee.min)
        assertEquals(100L, response.inclusionFee.mode)
        assertEquals(1000L, response.inclusionFee.transactionCount)
        assertEquals(100L, response.inclusionFee.ledgerCount)
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
        assertEquals(1700000000L, response.latestLedgerCloseTime)
        assertEquals(40000L, response.oldestLedger)
        assertEquals(1699000000L, response.oldestLedgerCloseTime)
        assertNull(response.txHash)
        assertNull(response.envelopeXdr)
        assertNull(response.resultXdr)
        assertNull(response.resultMetaXdr)
        assertNull(response.ledger)
        assertNull(response.createdAt)
        assertNull(response.applicationOrder)
        assertNull(response.feeBump)
        assertNull(response.events)
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
            "createdAt": 1699999999,
            "events": {
                "diagnosticEventsXdr": ["AAAAAQ=="]
            }
        }"""

        val response: GetTransactionResponse = json.decodeFromString(jsonString)

        assertEquals(GetTransactionStatus.SUCCESS, response.status)
        assertEquals("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", response.txHash)
        assertEquals(50000L, response.latestLedger)
        assertEquals(1700000000L, response.latestLedgerCloseTime)
        assertEquals(40000L, response.oldestLedger)
        assertEquals(1699000000L, response.oldestLedgerCloseTime)
        assertEquals(1, response.applicationOrder)
        assertEquals(false, response.feeBump)
        assertEquals("AAAA...envelope...", response.envelopeXdr)
        assertEquals("AAAA...result...", response.resultXdr)
        assertEquals("AAAA...meta...", response.resultMetaXdr)
        assertEquals(49999L, response.ledger)
        assertEquals(1699999999L, response.createdAt)
        assertNotNull(response.events)
        assertEquals(listOf("AAAAAQ=="), response.events?.diagnosticEventsXdr)
    }

    @Test
    fun testGetTransactionResponse_failed_deserializes() {
        val jsonString = """{
            "status": "FAILED",
            "txHash": "deadbeef1234567890deadbeef1234567890deadbeef1234567890deadbeef12",
            "latestLedger": 50000,
            "applicationOrder": 1,
            "feeBump": true,
            "ledger": 49998,
            "createdAt": 1699999900
        }"""

        val response: GetTransactionResponse = json.decodeFromString(jsonString)

        assertEquals(GetTransactionStatus.FAILED, response.status)
        assertEquals(1, response.applicationOrder)
        assertTrue(response.feeBump!!)
        assertEquals(49998L, response.ledger)
        assertEquals(1699999900L, response.createdAt)
    }

    @Test
    fun testGetTransactionResponse_statusValues() {
        // Every wire status maps to its enum constant
        val expected = mapOf(
            "NOT_FOUND" to GetTransactionStatus.NOT_FOUND,
            "SUCCESS" to GetTransactionStatus.SUCCESS,
            "FAILED" to GetTransactionStatus.FAILED
        )

        for ((wireValue, status) in expected) {
            val jsonString = """{"status": "$wireValue"}"""
            val response: GetTransactionResponse = json.decodeFromString(jsonString)
            assertEquals(status, response.status)
        }
    }

    @Test
    fun testGetTransactionStatus_enumConstants() {
        assertEquals(3, GetTransactionStatus.entries.size)
        assertEquals("NOT_FOUND", GetTransactionStatus.NOT_FOUND.name)
        assertEquals("SUCCESS", GetTransactionStatus.SUCCESS.name)
        assertEquals("FAILED", GetTransactionStatus.FAILED.name)
    }

    @Test
    fun testGetTransactionResponse_parseHelpers_nullReturnNull() {
        val jsonString = """{"status": "NOT_FOUND"}"""

        val response: GetTransactionResponse = json.decodeFromString(jsonString)

        assertNull(response.parseEnvelopeXdr())
        assertNull(response.parseResultXdr())
        assertNull(response.parseResultMetaXdr())
        assertNull(response.getResultValue())
        assertNull(response.getWasmId())
        assertNull(response.getCreatedContractId())
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

    @Test
    fun testGetTransactionsResponse_withTransactions_deserializes() {
        val jsonString = """{
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
        }"""

        val response: GetTransactionsResponse = json.decodeFromString(jsonString)

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
    fun testTransactionStatus_enumConstants() {
        assertEquals(2, TransactionStatus.entries.size)
        assertEquals("SUCCESS", TransactionStatus.SUCCESS.name)
        assertEquals("FAILED", TransactionStatus.FAILED.name)
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

    @Test
    fun testGetLedgersResponse_withLedgers_deserializes() {
        val jsonString = """{
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
        }"""

        val response: GetLedgersResponse = json.decodeFromString(jsonString)

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
    fun testGetLedgerEntriesResponse_emptyEntries_deserializes() {
        val jsonString = """{
            "entries": [],
            "latestLedger": 70000
        }"""

        val response: GetLedgerEntriesResponse = json.decodeFromString(jsonString)

        assertNotNull(response.entries)
        assertTrue(response.entries!!.isEmpty())
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
        assertEquals("AAAAAAAAAAAAAAAA...entry...=", response.entries!![0].xdr)
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
        assertEquals("BBB...data...=", response.entries!![0].xdr)
        assertEquals(69999L, response.entries!![0].lastModifiedLedger)
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
        assertEquals(1700002000L, response.latestLedgerCloseTime)
        assertNull(response.errorResultXdr)
        assertNull(response.diagnosticEventsXdr)
    }

    @Test
    fun testSendTransactionResponse_error_deserializes() {
        val jsonString = """{
            "status": "ERROR",
            "errorResultXdr": "AAAA...errorResult...=",
            "diagnosticEventsXdr": ["AAAAAQ==", "AAAAAg=="],
            "latestLedger": 80000,
            "latestLedgerCloseTime": 1700002000
        }"""

        val response: SendTransactionResponse = json.decodeFromString(jsonString)

        assertEquals(SendTransactionStatus.ERROR, response.status)
        assertNull(response.hash)
        assertEquals("AAAA...errorResult...=", response.errorResultXdr)
        assertEquals(listOf("AAAAAQ==", "AAAAAg=="), response.diagnosticEventsXdr)
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

    @Test
    fun testSendTransactionResponse_tryAgainLaterMinimal_deserializes() {
        val jsonString = """{
            "status": "TRY_AGAIN_LATER"
        }"""

        val response: SendTransactionResponse = json.decodeFromString(jsonString)

        assertEquals(SendTransactionStatus.TRY_AGAIN_LATER, response.status)
        assertNull(response.hash)
        assertNull(response.latestLedger)
        assertNull(response.latestLedgerCloseTime)
    }

    @Test
    fun testSendTransactionResponse_statusValues() {
        // Every wire status maps to its enum constant
        val expected = mapOf(
            "PENDING" to SendTransactionStatus.PENDING,
            "DUPLICATE" to SendTransactionStatus.DUPLICATE,
            "TRY_AGAIN_LATER" to SendTransactionStatus.TRY_AGAIN_LATER,
            "ERROR" to SendTransactionStatus.ERROR
        )

        for ((wireValue, status) in expected) {
            val jsonString = """{"status": "$wireValue"}"""
            val response: SendTransactionResponse = json.decodeFromString(jsonString)
            assertEquals(status, response.status)
        }
    }

    @Test
    fun testSendTransactionStatus_enumConstants() {
        assertEquals(4, SendTransactionStatus.entries.size)
        assertEquals("PENDING", SendTransactionStatus.PENDING.name)
        assertEquals("DUPLICATE", SendTransactionStatus.DUPLICATE.name)
        assertEquals("TRY_AGAIN_LATER", SendTransactionStatus.TRY_AGAIN_LATER.name)
        assertEquals("ERROR", SendTransactionStatus.ERROR.name)
    }

    @Test
    fun testSendTransactionResponse_parseHelpers_nullReturnNull() {
        val jsonString = """{"status": "PENDING"}"""

        val response: SendTransactionResponse = json.decodeFromString(jsonString)

        assertNull(response.parseErrorResultXdr())
        assertNull(response.parseDiagnosticEventsXdr())
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
        assertNull(response.oldestLedger)
        assertNull(response.latestLedgerCloseTime)
        assertNull(response.oldestLedgerCloseTime)
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

    @Test
    fun testGetEventsResponse_withEvents_deserializes() {
        val jsonString = """{
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
        }"""

        val response: GetEventsResponse = json.decodeFromString(jsonString)

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
    fun testGetEventsResponse_systemEventType_deserializes() {
        val jsonString = """{
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
        }"""

        val response: GetEventsResponse = json.decodeFromString(jsonString)

        assertEquals(EventFilterType.SYSTEM, response.events[0].type)
    }

    @Test
    fun testGetEventsResponse_diagnosticEventType_deserializes() {
        val jsonString = """{
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
        }"""

        val response: GetEventsResponse = json.decodeFromString(jsonString)

        assertEquals(EventFilterType.DIAGNOSTIC, response.events[0].type)
    }

    @Test
    fun testEventInfo_withoutInSuccessfulContractCall_deserializes() {
        val jsonString = """{
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
        }"""

        val response: GetEventsResponse = json.decodeFromString(jsonString)

        val event = response.events[0]
        @Suppress("DEPRECATION")
        assertNull(event.inSuccessfulContractCall)
        assertEquals(1, event.topic.size)
    }

    @Test
    fun testEventFilterType_enumConstants() {
        assertEquals(3, EventFilterType.entries.size)
        assertTrue(EventFilterType.entries.contains(EventFilterType.CONTRACT))
        assertTrue(EventFilterType.entries.contains(EventFilterType.SYSTEM))
        assertTrue(EventFilterType.entries.contains(EventFilterType.DIAGNOSTIC))
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

        assertEquals(10000000L, response.balanceEntry!!.getAmountAsLong())
        assertTrue(response.balanceEntry!!.isTemporary())
        assertTrue(response.balanceEntry!!.willBeArchivedBy(120000L))
        assertTrue(response.balanceEntry!!.willBeArchivedBy(200000L))
        assertFalse(response.balanceEntry!!.willBeArchivedBy(119999L))
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
                "authorized": false,
                "clawback": true,
                "lastModifiedLedgerSeq": 85000
            },
            "latestLedger": 90000
        }"""

        val response: GetSACBalanceResponse = json.decodeFromString(jsonString)

        assertNotNull(response.balanceEntry)
        val entry = response.balanceEntry!!
        assertNull(entry.liveUntilLedgerSeq)
        // An unauthorized balance is a distinct on-chain state from an authorized one
        assertFalse(entry.authorized)
        assertTrue(entry.clawback)

        // Persistent entries never expire
        assertEquals(5000000L, entry.getAmountAsLong())
        assertFalse(entry.isTemporary())
        assertFalse(entry.willBeArchivedBy(999999L))
    }

    // ========== Events Tests ==========

    @Test
    fun testEvents_allNull_deserializes() {
        val jsonString = """{}"""

        val response: Events = json.decodeFromString(jsonString)

        assertNull(response.diagnosticEventsXdr)
        assertNull(response.transactionEventsXdr)
        assertNull(response.contractEventsXdr)
        assertNull(response.parseDiagnosticEventsXdr())
        assertNull(response.parseTransactionEventsXdr())
        assertNull(response.parseContractEventsXdr())
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

    @Test
    fun testEvents_allEventTypes_deserializes() {
        val jsonString = """{
            "diagnosticEventsXdr": ["AAAAAQ==", "AAAAAg=="],
            "transactionEventsXdr": ["AAAAAQ=="],
            "contractEventsXdr": [["AAAAAQ==", "AAAAAg=="], ["AAAAAw=="]]
        }"""

        val events: Events = json.decodeFromString(jsonString)

        assertNotNull(events.diagnosticEventsXdr)
        assertEquals(2, events.diagnosticEventsXdr!!.size)
        assertNotNull(events.transactionEventsXdr)
        assertEquals(1, events.transactionEventsXdr!!.size)
        assertNotNull(events.contractEventsXdr)
        assertEquals(2, events.contractEventsXdr!!.size)
        assertEquals(2, events.contractEventsXdr!![0].size)
        assertEquals(1, events.contractEventsXdr!![1].size)
    }

    // ========== Edge Cases ==========

    @Test
    fun testSorobanRpcResponse_ignoresUnknownFields() {
        // Given: JSON with extra unknown fields
        val jsonString = """{
            "jsonrpc": "2.0",
            "id": "test",
            "result": {"status": "healthy"},
            "extraField": "ignored",
            "anotherField": 123
        }"""

        // When: Deserializing
        val response: SorobanRpcResponse<GetHealthResponse> = json.decodeFromString(jsonString)

        // Then: Unknown fields are ignored (ignoreUnknownKeys = true)
        assertNotNull(response.result)
        assertEquals("healthy", response.result?.status)
    }

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
    fun testSimulateTransactionResponse_emptyArrays_deserialize() {
        // Given: Response with empty arrays
        val jsonString = """{
            "events": [],
            "results": [],
            "stateChanges": [],
            "latestLedger": "100"
        }"""

        // When: Deserializing
        val response: SimulateTransactionResponse = json.decodeFromString(jsonString)

        // Then: Empty arrays are preserved
        assertEquals(0, response.events?.size)
        assertEquals(0, response.results?.size)
        assertEquals(0, response.stateChanges?.size)
    }

    // ========== XDR Fixtures ==========

    private fun contractEventXdr(topic: String = "transfer"): ContractEventXdr = ContractEventXdr(
        ext = ExtensionPointXdr.Void,
        contractId = XdrTestHelpers.contractId(),
        type = ContractEventTypeXdr.CONTRACT,
        body = ContractEventBodyXdr.V0(
            ContractEventV0Xdr(
                topics = listOf(Scv.toSymbol(topic)),
                data = Scv.toInt64(42L)
            )
        )
    )

    private fun diagnosticEventXdr(successful: Boolean = true): DiagnosticEventXdr =
        DiagnosticEventXdr(successful, contractEventXdr("diagnostic"))

    private fun transactionEventXdr(): TransactionEventXdr = TransactionEventXdr(
        TransactionEventStageXdr.TRANSACTION_EVENT_STAGE_AFTER_TX,
        contractEventXdr("fee")
    )

    private fun transactionEnvelopeXdr(fee: UInt = 200u): TransactionEnvelopeXdr =
        TransactionEnvelopeXdr.V1(
            TransactionV1EnvelopeXdr(
                tx = TransactionXdr(
                    sourceAccount = XdrTestHelpers.muxedAccountEd25519(),
                    fee = Uint32Xdr(fee),
                    seqNum = XdrTestHelpers.sequenceNumber(9L),
                    cond = PreconditionsXdr.Void,
                    memo = MemoXdr.Void,
                    operations = emptyList(),
                    ext = TransactionExtXdr.Void
                ),
                signatures = emptyList()
            )
        )

    private fun transactionResultXdr(feeCharged: Long = 123L): TransactionResultXdr =
        TransactionResultXdr(
            feeCharged = Int64Xdr(feeCharged),
            result = TransactionResultResultXdr.Void(TransactionResultCodeXdr.txTOO_EARLY),
            ext = TransactionResultExtXdr.Void
        )

    /** TransactionMeta V3 whose Soroban meta carries [returnValue], or no Soroban meta. */
    private fun metaV3(returnValue: SCValXdr?): TransactionMetaXdr = TransactionMetaXdr.V3(
        TransactionMetaV3Xdr(
            ext = ExtensionPointXdr.Void,
            txChangesBefore = LedgerEntryChangesXdr(emptyList()),
            operations = emptyList(),
            txChangesAfter = LedgerEntryChangesXdr(emptyList()),
            sorobanMeta = returnValue?.let {
                SorobanTransactionMetaXdr(
                    ext = SorobanTransactionMetaExtXdr.Void,
                    events = emptyList(),
                    returnValue = it,
                    diagnosticEvents = emptyList()
                )
            }
        )
    )

    /** TransactionMeta V4 whose Soroban meta carries [returnValue], or no Soroban meta. */
    private fun metaV4(returnValue: SCValXdr?, withSorobanMeta: Boolean = true): TransactionMetaXdr =
        TransactionMetaXdr.V4(
            TransactionMetaV4Xdr(
                ext = ExtensionPointXdr.Void,
                txChangesBefore = LedgerEntryChangesXdr(emptyList()),
                operations = emptyList(),
                txChangesAfter = LedgerEntryChangesXdr(emptyList()),
                sorobanMeta = if (withSorobanMeta) {
                    SorobanTransactionMetaV2Xdr(
                        ext = SorobanTransactionMetaExtXdr.Void,
                        returnValue = returnValue
                    )
                } else {
                    null
                },
                events = emptyList(),
                diagnosticEvents = emptyList()
            )
        )

    private fun ledgerHeaderHistoryEntryXdr(sequence: UInt = 4242u): LedgerHeaderHistoryEntryXdr =
        LedgerHeaderHistoryEntryXdr(
            hash = XdrTestHelpers.hashXdr(),
            header = LedgerHeaderXdr(
                ledgerVersion = Uint32Xdr(22u),
                previousLedgerHash = XdrTestHelpers.hashXdrAlt(),
                scpValue = StellarValueXdr(
                    txSetHash = XdrTestHelpers.hashXdr(),
                    closeTime = TimePointXdr(Uint64Xdr(1_700_000_000UL)),
                    upgrades = emptyList(),
                    ext = StellarValueExtXdr.Void
                ),
                txSetResultHash = XdrTestHelpers.hashXdr(),
                bucketListHash = XdrTestHelpers.hashXdr(),
                ledgerSeq = Uint32Xdr(sequence),
                totalCoins = Int64Xdr(1000L),
                feePool = Int64Xdr(100L),
                inflationSeq = Uint32Xdr(0u),
                idPool = Uint64Xdr(1UL),
                baseFee = Uint32Xdr(100u),
                baseReserve = Uint32Xdr(5_000_000u),
                maxTxSetSize = Uint32Xdr(100u),
                skipList = Array(4) { XdrTestHelpers.hashXdr() },
                ext = LedgerHeaderExtXdr.Void
            ),
            ext = LedgerHeaderHistoryEntryExtXdr.Void
        )

    private fun ledgerCloseMetaXdr(sequence: UInt = 4242u): LedgerCloseMetaXdr = LedgerCloseMetaXdr.V1(
        LedgerCloseMetaV1Xdr(
            ext = LedgerCloseMetaExtXdr.Void,
            ledgerHeader = ledgerHeaderHistoryEntryXdr(sequence),
            txSet = GeneralizedTransactionSetXdr.V1TxSet(
                TransactionSetV1Xdr(XdrTestHelpers.hashXdr(), emptyList())
            ),
            txProcessing = emptyList(),
            upgradesProcessing = emptyList(),
            scpInfo = emptyList(),
            totalByteSizeOfLiveSorobanState = Uint64Xdr(4096UL),
            evictedKeys = emptyList(),
            unused = emptyList()
        )
    )

    // ========== Events XDR Parsing ==========

    @Test
    fun testEvents_parseDiagnosticEventsXdr_returnsTypedEvents() {
        // Given: Two base64-encoded diagnostic events
        val successful = diagnosticEventXdr(successful = true)
        val failed = diagnosticEventXdr(successful = false)
        val events = Events(
            diagnosticEventsXdr = listOf(successful.toXdrBase64(), failed.toXdrBase64())
        )

        // When: Parsing
        val parsed = assertNotNull(events.parseDiagnosticEventsXdr())

        // Then: Both events decode back to their originals
        assertEquals(2, parsed.size)
        assertEquals(successful.toXdrBase64(), parsed[0].toXdrBase64())
        assertEquals(failed.toXdrBase64(), parsed[1].toXdrBase64())
        assertTrue(parsed[0].inSuccessfulContractCall)
        assertFalse(parsed[1].inSuccessfulContractCall)
        assertEquals(ContractEventTypeXdr.CONTRACT, parsed[0].event.type)
        val body = assertIs<ContractEventBodyXdr.V0>(parsed[0].event.body)
        assertEquals(Scv.toSymbol("diagnostic"), body.value.topics[0])
    }

    @Test
    fun testEvents_parseTransactionEventsXdr_returnsTypedEvents() {
        // Given: A base64-encoded transaction event
        val event = transactionEventXdr()
        val events = Events(transactionEventsXdr = listOf(event.toXdrBase64()))

        // When: Parsing
        val parsed = assertNotNull(events.parseTransactionEventsXdr())

        // Then: The event decodes back to its original
        assertEquals(1, parsed.size)
        assertEquals(event.toXdrBase64(), parsed[0].toXdrBase64())
        assertEquals(
            TransactionEventStageXdr.TRANSACTION_EVENT_STAGE_AFTER_TX,
            parsed[0].stage
        )
        val body = assertIs<ContractEventBodyXdr.V0>(parsed[0].event.body)
        assertEquals(Scv.toSymbol("fee"), body.value.topics[0])
    }

    @Test
    fun testEvents_parseContractEventsXdr_returnsTypedEventsPerOperation() {
        // Given: Contract events grouped by operation
        val first = contractEventXdr("mint")
        val second = contractEventXdr("burn")
        val events = Events(
            contractEventsXdr = listOf(
                listOf(first.toXdrBase64(), second.toXdrBase64()),
                emptyList()
            )
        )

        // When: Parsing
        val parsed = assertNotNull(events.parseContractEventsXdr())

        // Then: The nesting and order are preserved
        assertEquals(2, parsed.size)
        assertEquals(
            listOf(first.toXdrBase64(), second.toXdrBase64()),
            parsed[0].map { it.toXdrBase64() }
        )
        assertEquals(emptyList(), parsed[1].map { it.toXdrBase64() })
    }

    @Test
    fun testEvents_parseHelpers_malformedXdr_throwsException() {
        // Given: An event list holding a value that is not valid XDR
        val events = Events(diagnosticEventsXdr = listOf("not-base64-xdr"))

        // When/Then: Parsing reports the malformed input
        assertFailsWith<IllegalArgumentException> { events.parseDiagnosticEventsXdr() }
    }

    // ========== GetTransactionResponse XDR Parsing ==========

    @Test
    fun testGetTransactionResponse_parseXdrHelpers_returnTypedObjects() {
        // Given: A successful transaction carrying envelope, result and meta XDR
        val envelope = transactionEnvelopeXdr(fee = 555u)
        val result = transactionResultXdr(feeCharged = 777L)
        val meta = metaV3(Scv.toInt32(9))
        val response = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            envelopeXdr = envelope.toXdrBase64(),
            resultXdr = result.toXdrBase64(),
            resultMetaXdr = meta.toXdrBase64()
        )

        // When/Then: Each helper decodes back to the original object
        val parsedEnvelope = assertIs<TransactionEnvelopeXdr.V1>(response.parseEnvelopeXdr())
        assertEquals(envelope.toXdrBase64(), parsedEnvelope.toXdrBase64())
        assertEquals(555u, parsedEnvelope.value.tx.fee.value)

        val parsedResult = assertNotNull(response.parseResultXdr())
        assertEquals(result.toXdrBase64(), parsedResult.toXdrBase64())
        assertEquals(777L, parsedResult.feeCharged.value)

        val parsedMeta = assertIs<TransactionMetaXdr.V3>(response.parseResultMetaXdr())
        assertEquals(meta.toXdrBase64(), parsedMeta.toXdrBase64())
        assertEquals(Scv.toInt32(9), parsedMeta.value.sorobanMeta?.returnValue)
    }

    @Test
    fun testGetTransactionResponse_getResultValue_fromV3Meta() {
        // Given: V3 meta whose Soroban meta carries a return value
        val returnValue = Scv.toSymbol("ok")
        val response = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            resultMetaXdr = metaV3(returnValue).toXdrBase64()
        )

        // When/Then: The return value is extracted
        assertEquals(returnValue, response.getResultValue())
    }

    @Test
    fun testGetTransactionResponse_getResultValue_fromV4Meta() {
        // Given: V4 meta whose Soroban meta carries a return value
        val returnValue = Scv.toSymbol("ok-v4")
        val response = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            resultMetaXdr = metaV4(returnValue).toXdrBase64()
        )

        // When/Then: The return value is extracted
        assertEquals(returnValue, response.getResultValue())
    }

    @Test
    fun testGetTransactionResponse_getResultValue_noSorobanMeta_returnsNull() {
        // Given: V3 and V4 meta without Soroban meta
        val v3 = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            resultMetaXdr = metaV3(null).toXdrBase64()
        )
        val v4 = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            resultMetaXdr = metaV4(null, withSorobanMeta = false).toXdrBase64()
        )

        // When/Then: There is no return value to extract
        assertNull(v3.getResultValue())
        assertNull(v4.getResultValue())
    }

    @Test
    fun testGetTransactionResponse_getResultValue_classicMeta_returnsNull() {
        // Given: Classic (non-Soroban) transaction meta
        val response = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            resultMetaXdr = TransactionMetaXdr.Operations(emptyList()).toXdrBase64()
        )

        // When/Then: No Soroban return value exists
        assertNull(response.getResultValue())
    }

    @Test
    fun testGetTransactionResponse_getWasmId_fromBytesReturnValue() {
        // Given: An upload transaction whose return value is the WASM hash bytes
        val hashBytes = ByteArray(32) { (it * 7 + 3).toByte() }
        val response = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            resultMetaXdr = metaV3(SCValXdr.Bytes(SCBytesXdr(hashBytes))).toXdrBase64()
        )

        // When: Reading the WASM ID
        val wasmId = assertNotNull(response.getWasmId())

        // Then: It is the lowercase hex encoding of the returned bytes
        val expected = hashBytes.joinToString("") {
            it.toInt().and(0xFF).toString(16).padStart(2, '0')
        }
        assertEquals(expected, wasmId)
        assertEquals(64, wasmId.length)
    }

    @Test
    fun testGetTransactionResponse_getWasmId_nonBytesReturnValue_returnsNull() {
        // Given: A transaction whose return value is not bytes
        val response = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            resultMetaXdr = metaV3(Scv.toInt32(1)).toXdrBase64()
        )

        // When/Then: No WASM ID can be derived
        assertNull(response.getWasmId())
    }

    @Test
    fun testGetTransactionResponse_getCreatedContractId_fromContractAddress() {
        // Given: A deployment whose return value is the created contract address
        val contractHash = ByteArray(32) { (it + 1).toByte() }
        val address = SCAddressXdr.ContractId(ContractIDXdr(HashXdr(contractHash)))
        val response = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            resultMetaXdr = metaV3(SCValXdr.Address(address)).toXdrBase64()
        )

        // When: Reading the created contract ID
        val contractId = assertNotNull(response.getCreatedContractId())

        // Then: It is the strkey encoding of the returned contract hash
        assertEquals(StrKey.encodeContract(contractHash), contractId)
        assertTrue(contractId.startsWith("C"), contractId)
    }

    @Test
    fun testGetTransactionResponse_getCreatedContractId_nonAddressReturnValue_returnsNull() {
        val response = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            resultMetaXdr = metaV3(Scv.toSymbol("nope")).toXdrBase64()
        )

        assertNull(response.getCreatedContractId())
    }

    @Test
    fun testGetTransactionResponse_getCreatedContractId_accountAddress_returnsNull() {
        // Given: The return value is an account address rather than a contract address
        val address = SCAddressXdr.AccountId(XdrTestHelpers.accountId())
        val response = GetTransactionResponse(
            status = GetTransactionStatus.SUCCESS,
            resultMetaXdr = metaV3(SCValXdr.Address(address)).toXdrBase64()
        )

        // When/Then: No contract was created
        assertNull(response.getCreatedContractId())
    }

    @Test
    fun testGetTransactionResponse_derivedHelpers_noMeta_returnNull() {
        // Given: A NOT_FOUND response with no meta at all
        val response = GetTransactionResponse(status = GetTransactionStatus.NOT_FOUND)

        // When/Then: Every derived helper reports absence
        assertNull(response.getResultValue())
        assertNull(response.getWasmId())
        assertNull(response.getCreatedContractId())
    }

    // ========== GetTransactionsResponse XDR Parsing ==========

    @Suppress("DEPRECATION")
    @Test
    fun testTransactionInfo_parseXdrHelpers_returnTypedObjects() {
        // Given: A transaction info carrying envelope, result, meta and diagnostic XDR
        val envelope = transactionEnvelopeXdr(fee = 321u)
        val result = transactionResultXdr(feeCharged = 654L)
        val meta = metaV3(Scv.toInt32(3))
        val diagnostic = diagnosticEventXdr()
        val info = GetTransactionsResponse.TransactionInfo(
            status = TransactionStatus.SUCCESS,
            txHash = "aabb",
            applicationOrder = 1,
            feeBump = false,
            envelopeXdr = envelope.toXdrBase64(),
            resultXdr = result.toXdrBase64(),
            resultMetaXdr = meta.toXdrBase64(),
            ledger = 500L,
            createdAt = 1690594566L,
            diagnosticEventsXdr = listOf(diagnostic.toXdrBase64())
        )

        // When/Then: Each helper decodes back to the original object
        val parsedEnvelope = assertIs<TransactionEnvelopeXdr.V1>(info.parseEnvelopeXdr())
        assertEquals(envelope.toXdrBase64(), parsedEnvelope.toXdrBase64())
        assertEquals(321u, parsedEnvelope.value.tx.fee.value)

        assertEquals(result.toXdrBase64(), info.parseResultXdr().toXdrBase64())
        assertEquals(654L, info.parseResultXdr().feeCharged.value)

        val parsedMeta = assertIs<TransactionMetaXdr.V3>(info.parseResultMetaXdr())
        assertEquals(meta.toXdrBase64(), parsedMeta.toXdrBase64())
        assertEquals(Scv.toInt32(3), parsedMeta.value.sorobanMeta?.returnValue)

        val parsedDiagnostics = assertNotNull(info.parseDiagnosticEventsXdr())
        assertEquals(
            listOf(diagnostic.toXdrBase64()),
            parsedDiagnostics.map { it.toXdrBase64() }
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun testTransactionInfo_parseDiagnosticEventsXdr_absentField_returnsNull() {
        // Given: A transaction info without the deprecated diagnostic events field
        val info = GetTransactionsResponse.TransactionInfo(
            status = TransactionStatus.FAILED,
            txHash = "ccdd",
            applicationOrder = 2,
            feeBump = true,
            envelopeXdr = transactionEnvelopeXdr().toXdrBase64(),
            resultXdr = transactionResultXdr().toXdrBase64(),
            resultMetaXdr = metaV3(null).toXdrBase64(),
            ledger = 501L,
            createdAt = 1690594567L
        )

        // When/Then: The helper reports absence rather than an empty list
        assertNull(info.diagnosticEventsXdr)
        assertNull(info.parseDiagnosticEventsXdr())
        assertNull(info.events)
    }

    // ========== GetLedgerEntriesResponse XDR Parsing ==========

    @Test
    fun testLedgerEntryResult_parseKeyAndXdr_returnTypedObjects() {
        // Given: A ledger entry result carrying a contract data key and entry
        val contractAddress = SCAddressXdr.ContractId(XdrTestHelpers.contractId())
        val key = LedgerKeyXdr.ContractData(
            LedgerKeyContractDataXdr(
                contract = contractAddress,
                key = Scv.toSymbol("balance"),
                durability = ContractDataDurabilityXdr.PERSISTENT
            )
        )
        val entryData = LedgerEntryDataXdr.ContractData(
            ContractDataEntryXdr(
                ext = ExtensionPointXdr.Void,
                contract = contractAddress,
                key = Scv.toSymbol("balance"),
                durability = ContractDataDurabilityXdr.PERSISTENT,
                `val` = Scv.toInt64(1234L)
            )
        )
        val entry = GetLedgerEntriesResponse.LedgerEntryResult(
            key = key.toXdrBase64(),
            xdr = entryData.toXdrBase64(),
            lastModifiedLedger = 900L,
            liveUntilLedger = 1000L
        )

        // When/Then: Both helpers decode back to the original objects
        val parsedKey = assertIs<LedgerKeyXdr.ContractData>(entry.parseKey())
        assertEquals(key.toXdrBase64(), parsedKey.toXdrBase64())
        assertEquals(ContractDataDurabilityXdr.PERSISTENT, parsedKey.value.durability)
        assertEquals(Scv.toSymbol("balance"), parsedKey.value.key)

        val parsedData = assertIs<LedgerEntryDataXdr.ContractData>(entry.parseXdr())
        assertEquals(entryData.toXdrBase64(), parsedData.toXdrBase64())
        assertEquals(Scv.toInt64(1234L), parsedData.value.`val`)
    }

    @Test
    fun testLedgerEntryResult_parseHelpers_malformedXdr_throwException() {
        val entry = GetLedgerEntriesResponse.LedgerEntryResult(
            key = "not-xdr",
            xdr = "not-xdr",
            lastModifiedLedger = 1L
        )

        assertFailsWith<IllegalArgumentException> { entry.parseKey() }
        assertFailsWith<IllegalArgumentException> { entry.parseXdr() }
    }

    // ========== GetLedgersResponse XDR Parsing ==========

    @Test
    fun testLedgerInfo_parseHeaderAndMetadataXdr_returnTypedObjects() {
        // Given: A ledger info carrying header and close meta XDR
        val header = ledgerHeaderHistoryEntryXdr(sequence = 4242u)
        val closeMeta = ledgerCloseMetaXdr(sequence = 4242u)
        val info = GetLedgersResponse.LedgerInfo(
            hash = "abcd",
            sequence = 4242L,
            ledgerCloseTime = 1_700_000_000L,
            headerXdr = header.toXdrBase64(),
            metadataXdr = closeMeta.toXdrBase64()
        )

        // When/Then: Both helpers decode back to the original objects
        assertEquals(header.toXdrBase64(), info.parseHeaderXdr().toXdrBase64())
        assertEquals(4242u, info.parseHeaderXdr().header.ledgerSeq.value)

        val parsedMeta = assertIs<LedgerCloseMetaXdr.V1>(info.parseMetadataXdr())
        assertEquals(closeMeta.toXdrBase64(), parsedMeta.toXdrBase64())
        assertEquals(4242u, parsedMeta.value.ledgerHeader.header.ledgerSeq.value)
        assertEquals(4096UL, parsedMeta.value.totalByteSizeOfLiveSorobanState.value)
    }

    @Test
    fun testLedgerInfo_parseHelpers_malformedXdr_throwException() {
        val info = GetLedgersResponse.LedgerInfo(
            hash = "abcd",
            sequence = 1L,
            ledgerCloseTime = 1L,
            headerXdr = "not-xdr",
            metadataXdr = "not-xdr"
        )

        assertFailsWith<IllegalArgumentException> { info.parseHeaderXdr() }
        assertFailsWith<IllegalArgumentException> { info.parseMetadataXdr() }
    }

    // ========== GetEventsResponse XDR Parsing ==========

    @Test
    fun testEventInfo_parseTopicAndValue_returnTypedScVals() {
        // Given: An event whose topics and value are base64 SCVal XDR
        val first = Scv.toSymbol("transfer")
        val second = Scv.toInt32(11)
        val value = Scv.toInt64(9_000L)
        val info = GetEventsResponse.EventInfo(
            type = EventFilterType.CONTRACT,
            ledger = 100L,
            ledgerClosedAt = "2024-01-01T00:00:00Z",
            contractId = "CCJZ5D...",
            id = "0001-0000000000",
            operationIndex = 0L,
            transactionIndex = 1L,
            transactionHash = "aabb",
            topic = listOf(first.toXdrBase64(), second.toXdrBase64()),
            value = value.toXdrBase64()
        )

        // When/Then: Both helpers decode back to the original SCVals
        assertEquals(listOf(first, second), info.parseTopic())
        assertEquals(value, info.parseValue())
    }

    @Test
    fun testEventInfo_parseHelpers_malformedXdr_throwException() {
        val info = GetEventsResponse.EventInfo(
            type = EventFilterType.SYSTEM,
            ledger = 100L,
            ledgerClosedAt = "2024-01-01T00:00:00Z",
            contractId = "CCJZ5D...",
            id = "0001-0000000000",
            operationIndex = 0L,
            transactionIndex = 1L,
            transactionHash = "aabb",
            topic = listOf("not-xdr"),
            value = "not-xdr"
        )

        assertFailsWith<IllegalArgumentException> { info.parseTopic() }
        assertFailsWith<IllegalArgumentException> { info.parseValue() }
    }

    @Test
    fun testEventInfo_emptyTopic_parsesToEmptyList() {
        // Given: An event with no topics
        val info = GetEventsResponse.EventInfo(
            type = EventFilterType.DIAGNOSTIC,
            ledger = 100L,
            ledgerClosedAt = "2024-01-01T00:00:00Z",
            contractId = "CCJZ5D...",
            id = "0001-0000000000",
            operationIndex = 0L,
            transactionIndex = 1L,
            transactionHash = "aabb",
            topic = emptyList(),
            value = Scv.toVoid().toXdrBase64()
        )

        // When/Then: The topic list parses to an empty list
        assertEquals(emptyList(), info.parseTopic())
        assertEquals(Scv.toVoid(), info.parseValue())
    }
}
