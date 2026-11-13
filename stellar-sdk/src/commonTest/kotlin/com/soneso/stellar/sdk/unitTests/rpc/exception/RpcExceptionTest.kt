package com.soneso.stellar.sdk.unitTests.rpc.exception

import com.soneso.stellar.sdk.horizon.exceptions.NetworkException
import com.soneso.stellar.sdk.rpc.exception.*
import com.soneso.stellar.sdk.rpc.responses.ReportedContractError
import kotlin.test.*

/**
 * Unit tests for RPC exception classes.
 *
 * Tests cover:
 * - AccountNotFoundException: construction, message, properties
 * - PrepareTransactionException: construction, message, simulationError
 * - SorobanRpcException: construction, message, errorCode, data, inheritance
 * - FailedSimulationResponseException and the simulation data of both simulation exceptions
 */
class RpcExceptionTest {

    // ========== AccountNotFoundException ==========

    @Test
    fun testAccountNotFoundException_message() {
        val accountId = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        val exception = AccountNotFoundException(accountId)

        assertEquals("Account not found: $accountId", exception.message)
    }

    @Test
    fun testAccountNotFoundException_accountIdProperty() {
        val accountId = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        val exception = AccountNotFoundException(accountId)

        assertEquals(accountId, exception.accountId)
    }

    @Test
    fun testAccountNotFoundException_isException() {
        val exception = AccountNotFoundException("GTEST")
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testAccountNotFoundException_canBeCaught() {
        val caught = assertFailsWith<AccountNotFoundException> {
            throw AccountNotFoundException("GABC123")
        }
        assertEquals("GABC123", caught.accountId)
    }

    @Test
    fun testAccountNotFoundException_emptyAccountId() {
        val exception = AccountNotFoundException("")
        assertEquals("Account not found: ", exception.message)
        assertEquals("", exception.accountId)
    }

    // ========== PrepareTransactionException ==========

    @Test
    fun testPrepareTransactionException_messageOnly() {
        val exception = PrepareTransactionException("Preparation failed")

        assertEquals("Preparation failed", exception.message)
        assertNull(exception.simulationError)
    }

    @Test
    fun testPrepareTransactionException_withSimulationError() {
        val exception = PrepareTransactionException(
            message = "Transaction preparation failed",
            simulationError = "HostError: Error(WasmVm, InvalidAction)"
        )

        assertEquals("Transaction preparation failed", exception.message)
        assertEquals("HostError: Error(WasmVm, InvalidAction)", exception.simulationError)
    }

    @Test
    fun testPrepareTransactionException_nullSimulationError() {
        val exception = PrepareTransactionException(
            message = "Failed",
            simulationError = null
        )

        assertNull(exception.simulationError)
    }

    @Test
    fun testPrepareTransactionException_isException() {
        val exception = PrepareTransactionException("Failed")
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testPrepareTransactionException_canBeCaught() {
        val caught = assertFailsWith<PrepareTransactionException> {
            throw PrepareTransactionException("Simulation error", "Contract execution error")
        }
        assertEquals("Simulation error", caught.message)
        assertEquals("Contract execution error", caught.simulationError)
    }

    @Test
    fun testPrepareTransactionException_emptySimulationError() {
        val exception = PrepareTransactionException(
            message = "Failed",
            simulationError = ""
        )
        assertEquals("", exception.simulationError)
    }

    // ========== SorobanRpcException ==========

    @Test
    fun testSorobanRpcException_basicConstruction() {
        val exception = SorobanRpcException(
            errorCode = -32601,
            errorMessage = "method not found"
        )

        assertEquals(-32601, exception.errorCode)
        assertEquals("Soroban RPC error (-32601): method not found", exception.message)
        assertNull(exception.data)
    }

    @Test
    fun testSorobanRpcException_withData() {
        val exception = SorobanRpcException(
            errorCode = -32602,
            errorMessage = "Invalid params",
            data = "additional error details"
        )

        assertEquals(-32602, exception.errorCode)
        assertEquals("Soroban RPC error (-32602): Invalid params", exception.message)
        assertEquals("additional error details", exception.data)
    }

    @Test
    fun testSorobanRpcException_inheritsNetworkException() {
        val exception = SorobanRpcException(
            errorCode = -32600,
            errorMessage = "Invalid Request"
        )

        assertTrue(exception is NetworkException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testSorobanRpcException_networkExceptionProperties() {
        val exception = SorobanRpcException(
            errorCode = -32700,
            errorMessage = "Parse error",
            data = "body data"
        )

        // NetworkException properties
        assertEquals(-32700, exception.code)
        assertEquals("body data", exception.body)
    }

    @Test
    fun testSorobanRpcException_canBeCaughtAsNetworkException() {
        val caught = assertFailsWith<NetworkException> {
            throw SorobanRpcException(-32603, "Internal error")
        }

        assertTrue(caught is SorobanRpcException)
        assertEquals("Soroban RPC error (-32603): Internal error", caught.message)
    }

    @Test
    fun testSorobanRpcException_canBeCaughtAsSpecificType() {
        val caught = assertFailsWith<SorobanRpcException> {
            throw SorobanRpcException(-32001, "Transaction submission failed")
        }

        assertEquals(-32001, caught.errorCode)
        assertEquals("Soroban RPC error (-32001): Transaction submission failed", caught.message)
    }

    @Test
    fun testSorobanRpcException_standardErrorCodes() {
        // Test standard JSON-RPC error codes
        val parseError = SorobanRpcException(-32700, "Parse error")
        assertEquals(-32700, parseError.errorCode)

        val invalidRequest = SorobanRpcException(-32600, "Invalid Request")
        assertEquals(-32600, invalidRequest.errorCode)

        val methodNotFound = SorobanRpcException(-32601, "Method not found")
        assertEquals(-32601, methodNotFound.errorCode)

        val invalidParams = SorobanRpcException(-32602, "Invalid params")
        assertEquals(-32602, invalidParams.errorCode)

        val internalError = SorobanRpcException(-32603, "Internal error")
        assertEquals(-32603, internalError.errorCode)
    }

    @Test
    fun testSorobanRpcException_sorobanSpecificErrorCodes() {
        // Test Soroban-specific error codes
        val submissionFailed = SorobanRpcException(-32001, "Transaction submission failed")
        assertEquals(-32001, submissionFailed.errorCode)

        val simulationFailed = SorobanRpcException(-32002, "Transaction simulation failed")
        assertEquals(-32002, simulationFailed.errorCode)

        val entryNotFound = SorobanRpcException(-32003, "Ledger entry not found")
        assertEquals(-32003, entryNotFound.errorCode)

        val invocationFailed = SorobanRpcException(-32004, "Contract invocation failed")
        assertEquals(-32004, invocationFailed.errorCode)
    }

    @Test
    fun testSorobanRpcException_nullData() {
        val exception = SorobanRpcException(
            errorCode = -32601,
            errorMessage = "Method not found",
            data = null
        )
        assertNull(exception.data)
    }

    @Test
    fun testSorobanRpcException_emptyData() {
        val exception = SorobanRpcException(
            errorCode = -32601,
            errorMessage = "Method not found",
            data = ""
        )
        assertEquals("", exception.data)
    }

    // ========== Cross-Cutting ==========

    @Test
    fun testExceptionTypeDistinction() {
        val accNotFound = AccountNotFoundException("GTEST")
        val prepareFailed = PrepareTransactionException("Failed")
        val rpcError = SorobanRpcException(-32601, "Method not found")

        assertTrue(accNotFound is AccountNotFoundException)
        assertFalse(accNotFound is PrepareTransactionException)

        assertTrue(prepareFailed is PrepareTransactionException)
        assertFalse(prepareFailed is SorobanRpcException)

        assertTrue(rpcError is SorobanRpcException)
        assertFalse(rpcError is AccountNotFoundException)
    }

    // ========== Simulation data ==========

    private val reported = ReportedContractError(3016u, ReportedContractError.Provenance.DIAGNOSTIC_EVENTS, null)

    @Test
    fun testPrepareTransactionException_constructorsWithoutEvents_carryNoSimulationData() {
        val hostError = "HostError: Error(Contract, #3016)"
        val cases = listOf(
            PrepareTransactionException("m") to "m",
            PrepareTransactionException(hostError) to hostError,
            PrepareTransactionException("Simulation failed: $hostError", hostError) to "Simulation failed: $hostError"
        )
        for ((exception, message) in cases) {
            assertEquals(message, exception.message)
            assertEquals(emptyList(), exception.diagnosticEventsXdr)
            assertNull(exception.reportedContractError)
        }
        assertNull(PrepareTransactionException("m").simulationError)
        assertEquals(hostError, PrepareTransactionException("Simulation failed: $hostError", hostError).simulationError)
    }

    @Test
    fun testPrepareTransactionException_keepsAnImmutableCopyOfTheEvents() {
        val events = mutableListOf("AAAA", "BBBB")
        val exception = PrepareTransactionException("m", "e", events, reported)

        events.add("CCCC")
        events.clear()

        assertEquals(listOf("AAAA", "BBBB"), exception.diagnosticEventsXdr)
        assertFalse(exception.diagnosticEventsXdr is MutableList<*>)
        assertEquals(reported, exception.reportedContractError)
        assertEquals("m", exception.message)
        assertEquals("e", exception.simulationError)
    }

    @Test
    fun testFailedSimulationResponseException_isAnIllegalArgumentExceptionWithData() {
        val events = mutableListOf("AAAA")
        val exception: Exception = FailedSimulationResponseException("m", "e", events, reported)

        events.clear()

        val failure = assertIs<FailedSimulationResponseException>(assertIs<IllegalArgumentException>(exception))
        assertEquals("m", failure.message)
        assertEquals("e", failure.simulationError)
        assertEquals(listOf("AAAA"), failure.diagnosticEventsXdr)
        assertFalse(failure.diagnosticEventsXdr is MutableList<*>)
        assertEquals(reported, failure.reportedContractError)
    }

    @Test
    fun testSimulationFailureDetails_oneHandlerReadsTheData() {
        val failures: List<Exception> = listOf(
            PrepareTransactionException("m", "e", listOf("AAAA"), reported),
            FailedSimulationResponseException("m", "e", listOf("AAAA"), reported)
        )
        for (failure in failures) {
            assertEquals(reported, (failure as? SimulationFailureDetails)?.reportedContractError)
        }
        assertNull((SorobanRpcException(-32601, "Method not found") as Exception as? SimulationFailureDetails))
    }
}
