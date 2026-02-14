package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.*
import com.soneso.stellar.sdk.rpc.responses.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Comprehensive tests for response model deserialization.
 *
 * Tests all response models to ensure proper JSON deserialization with correct
 * field names, enum handling, XDR parsing helpers, and null handling.
 *
 * Uses real test data from Java SDK test resources in
 * /Users/chris/projects/Stellar/java-stellar-sdk/src/test/resources/soroban_server/
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

        // Then: All success fields are populated
        assertNull(response.error)
        assertNotNull(response.transactionData)
        assertEquals(58181L, response.minResourceFee)
        assertEquals(1, response.events?.size)
        assertEquals(1, response.results?.size)
        assertEquals(14245L, response.latestLedger)
        assertEquals(1, response.results?.get(0)?.auth?.size)
        assertEquals("AAAAAwAAABQ=", response.results?.get(0)?.xdr)
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
        assertNull(response.results)
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
        // Given: Simulation with state changes
        val jsonString = """{
            "stateChanges": [{
                "type": "created",
                "key": "AAAAAAAAAABuaCbVXZ2DlXWarV6UxwbW3GNJgpn3ASChIFp5bxSIWg==",
                "before": null,
                "after": "AAAAZAAAAAAAAAAAbmgm1V2dg5V1mq1elMcG1txjSYKZ9wEgoSBaeW8UiFo="
            }],
            "latestLedger": "14245"
        }"""

        // When: Deserializing
        val response: SimulateTransactionResponse = json.decodeFromString(jsonString)

        // Then: State changes are populated
        assertEquals(1, response.stateChanges?.size)
        val change = response.stateChanges?.get(0)
        assertEquals("created", change?.type)
        assertEquals("AAAAAAAAAABuaCbVXZ2DlXWarV6UxwbW3GNJgpn3ASChIFp5bxSIWg==", change?.key)
        assertNull(change?.before)
        assertNotNull(change?.after)
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
}
