package com.soneso.stellar.sdk.unitTests.contract.exception

import com.soneso.stellar.sdk.contract.exception.*
import com.soneso.stellar.sdk.contract.*
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.AssembledTransaction
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Basic unit tests for all contract exception classes.
 *
 * Tests focus on exception class construction and properties:
 * - Constructor parameter handling
 * - Message accessibility
 * - AssembledTransaction reference accessibility
 * - Cause chain accessibility
 * - Inheritance hierarchy validation
 * - Exception type verification
 *
 * These are API contract tests, not integration tests.
 */
class ContractExceptionTest {

    companion object {
        const val TEST_MESSAGE = "Test exception message"
        const val ACCOUNT_ID = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        const val CONTRACT_ID = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
        val NETWORK = Network.TESTNET
        const val RPC_URL = "https://soroban-testnet.stellar.org"
    }

    private lateinit var server: SorobanServer
    private lateinit var assembledTransaction: AssembledTransaction<Unit>
    private lateinit var rootCause: IllegalArgumentException

    @BeforeTest
    fun setup() {
        server = SorobanServer(RPC_URL)
        rootCause = IllegalArgumentException("Root cause error")

        // Create a minimal AssembledTransaction for testing
        val builder = TransactionBuilder(
            sourceAccount = Account(ACCOUNT_ID, 100L),
            network = NETWORK
        )
            .addOperation(
                InvokeHostFunctionOperation.invokeContractFunction(
                    contractAddress = CONTRACT_ID,
                    functionName = "test",
                    parameters = emptyList()
                )
            )
            .setTimeout(300L)
            .setBaseFee(100L)

        assembledTransaction = AssembledTransaction(
            server = server,
            submitTimeout = 30,
            transactionSigner = null,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )
    }

    @AfterTest
    fun tearDown() {
        server.close()
    }

    // ==================== ContractException (Base Class) ====================

    @Test
    fun testContractExceptionConstructorWithAllParameters() {
        val exception = ContractException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction,
            cause = rootCause
        )

        assertEquals(TEST_MESSAGE, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertSame(rootCause, exception.cause)
    }

    @Test
    fun testContractExceptionConstructorWithMessageOnly() {
        val exception = ContractException(message = TEST_MESSAGE)

        assertEquals(TEST_MESSAGE, exception.message)
        assertNull(exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testContractExceptionConstructorWithMessageAndTransaction() {
        val exception = ContractException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction
        )

        assertEquals(TEST_MESSAGE, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testContractExceptionConstructorWithMessageAndCause() {
        val exception = ContractException(
            message = TEST_MESSAGE,
            assembledTransaction = null,
            cause = rootCause
        )

        assertEquals(TEST_MESSAGE, exception.message)
        assertNull(exception.assembledTransaction)
        assertSame(rootCause, exception.cause)
    }

    @Test
    fun testContractExceptionIsThrowable() {
        val exception = ContractException(TEST_MESSAGE)
        assertTrue(exception is Throwable)
        assertTrue(exception is Exception)
    }

    @Test
    fun testContractExceptionCanBeCaught() {
        val exception = ContractException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertSame(assembledTransaction, caught.assembledTransaction)
    }

    @Test
    fun testContractExceptionPreservesStackTrace() {
        val exception = ContractException(TEST_MESSAGE)
        val stackTrace = exception.stackTraceToString()

        assertTrue(stackTrace.isNotEmpty())
        assertTrue(stackTrace.contains("ContractException"))
    }

    // ==================== NotYetSimulatedException ====================

    @Test
    fun testNotYetSimulatedExceptionConstructor() {
        val message = "Transaction must be simulated before signing"
        val exception = NotYetSimulatedException(
            message = message,
            assembledTransaction = assembledTransaction
        )

        assertEquals(message, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testNotYetSimulatedExceptionInheritsFromContractException() {
        val exception = NotYetSimulatedException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction
        )

        assertTrue(exception is ContractException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testNotYetSimulatedExceptionCanBeCaughtAsContractException() {
        val exception = NotYetSimulatedException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertTrue(caught is NotYetSimulatedException)
        assertEquals(TEST_MESSAGE, caught.message)
    }

    @Test
    fun testNotYetSimulatedExceptionCanBeCaughtAsSpecificType() {
        val exception = NotYetSimulatedException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<NotYetSimulatedException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertSame(assembledTransaction, caught.assembledTransaction)
    }

    // ==================== SimulationFailedException ====================

    @Test
    fun testSimulationFailedExceptionConstructor() {
        val message = "Simulation failed: Contract execution error"
        val exception = SimulationFailedException(
            message = message,
            assembledTransaction = assembledTransaction
        )

        assertEquals(message, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testSimulationFailedExceptionInheritsFromContractException() {
        val exception = SimulationFailedException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction
        )

        assertTrue(exception is ContractException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testSimulationFailedExceptionCanBeCaughtAsContractException() {
        val exception = SimulationFailedException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertTrue(caught is SimulationFailedException)
        assertEquals(TEST_MESSAGE, caught.message)
    }

    @Test
    fun testSimulationFailedExceptionCanBeCaughtAsSpecificType() {
        val exception = SimulationFailedException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<SimulationFailedException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertSame(assembledTransaction, caught.assembledTransaction)
    }

    // ==================== RestorationFailureException ====================

    @Test
    fun testRestorationFailureExceptionConstructor() {
        val message = "Failed to restore contract state"
        val exception = RestorationFailureException(
            message = message,
            assembledTransaction = assembledTransaction
        )

        assertEquals(message, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testRestorationFailureExceptionInheritsFromContractException() {
        val exception = RestorationFailureException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction
        )

        assertTrue(exception is ContractException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testRestorationFailureExceptionCanBeCaughtAsContractException() {
        val exception = RestorationFailureException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertTrue(caught is RestorationFailureException)
        assertEquals(TEST_MESSAGE, caught.message)
    }

    @Test
    fun testRestorationFailureExceptionCanBeCaughtAsSpecificType() {
        val exception = RestorationFailureException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<RestorationFailureException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertSame(assembledTransaction, caught.assembledTransaction)
    }

    // ==================== NoSignatureNeededException ====================

    @Test
    fun testNoSignatureNeededExceptionConstructor() {
        val message = "This is a read-only call; no need to sign or submit"
        val exception = NoSignatureNeededException(
            message = message,
            assembledTransaction = assembledTransaction
        )

        assertEquals(message, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testNoSignatureNeededExceptionInheritsFromContractException() {
        val exception = NoSignatureNeededException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction
        )

        assertTrue(exception is ContractException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testNoSignatureNeededExceptionCanBeCaughtAsContractException() {
        val exception = NoSignatureNeededException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertTrue(caught is NoSignatureNeededException)
        assertEquals(TEST_MESSAGE, caught.message)
    }

    @Test
    fun testNoSignatureNeededExceptionCanBeCaughtAsSpecificType() {
        val exception = NoSignatureNeededException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<NoSignatureNeededException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertSame(assembledTransaction, caught.assembledTransaction)
    }

    // ==================== NeedsMoreSignaturesException ====================

    @Test
    fun testNeedsMoreSignaturesExceptionConstructor() {
        val message = "Transaction requires signatures from: [GXXX..., GYYY...]"
        val exception = NeedsMoreSignaturesException(
            message = message,
            assembledTransaction = assembledTransaction
        )

        assertEquals(message, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testNeedsMoreSignaturesExceptionInheritsFromContractException() {
        val exception = NeedsMoreSignaturesException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction
        )

        assertTrue(exception is ContractException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testNeedsMoreSignaturesExceptionCanBeCaughtAsContractException() {
        val exception = NeedsMoreSignaturesException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertTrue(caught is NeedsMoreSignaturesException)
        assertEquals(TEST_MESSAGE, caught.message)
    }

    @Test
    fun testNeedsMoreSignaturesExceptionCanBeCaughtAsSpecificType() {
        val exception = NeedsMoreSignaturesException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<NeedsMoreSignaturesException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertSame(assembledTransaction, caught.assembledTransaction)
    }

    // ==================== ExpiredStateException ====================

    @Test
    fun testExpiredStateExceptionConstructor() {
        val message = "Contract state has expired and needs restoration"
        val exception = ExpiredStateException(
            message = message,
            assembledTransaction = assembledTransaction
        )

        assertEquals(message, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testExpiredStateExceptionInheritsFromContractException() {
        val exception = ExpiredStateException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction
        )

        assertTrue(exception is ContractException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testExpiredStateExceptionCanBeCaughtAsContractException() {
        val exception = ExpiredStateException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertTrue(caught is ExpiredStateException)
        assertEquals(TEST_MESSAGE, caught.message)
    }

    @Test
    fun testExpiredStateExceptionCanBeCaughtAsSpecificType() {
        val exception = ExpiredStateException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ExpiredStateException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertSame(assembledTransaction, caught.assembledTransaction)
    }

    // ==================== SendTransactionFailedException ====================

    @Test
    fun testSendTransactionFailedExceptionConstructor() {
        val message = "Failed to send transaction to network"
        val exception = SendTransactionFailedException(
            message = message,
            assembledTransaction = assembledTransaction
        )

        assertEquals(message, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testSendTransactionFailedExceptionInheritsFromContractException() {
        val exception = SendTransactionFailedException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction
        )

        assertTrue(exception is ContractException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testSendTransactionFailedExceptionCanBeCaughtAsContractException() {
        val exception = SendTransactionFailedException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertTrue(caught is SendTransactionFailedException)
        assertEquals(TEST_MESSAGE, caught.message)
    }

    @Test
    fun testSendTransactionFailedExceptionCanBeCaughtAsSpecificType() {
        val exception = SendTransactionFailedException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<SendTransactionFailedException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertSame(assembledTransaction, caught.assembledTransaction)
    }

    // ==================== TransactionStillPendingException ====================

    @Test
    fun testTransactionStillPendingExceptionConstructor() {
        val message = "Transaction still pending after 60 seconds"
        val exception = TransactionStillPendingException(
            message = message,
            assembledTransaction = assembledTransaction
        )

        assertEquals(message, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testTransactionStillPendingExceptionInheritsFromContractException() {
        val exception = TransactionStillPendingException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction
        )

        assertTrue(exception is ContractException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testTransactionStillPendingExceptionCanBeCaughtAsContractException() {
        val exception = TransactionStillPendingException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertTrue(caught is TransactionStillPendingException)
        assertEquals(TEST_MESSAGE, caught.message)
    }

    @Test
    fun testTransactionStillPendingExceptionCanBeCaughtAsSpecificType() {
        val exception = TransactionStillPendingException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<TransactionStillPendingException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertSame(assembledTransaction, caught.assembledTransaction)
    }

    // ==================== TransactionFailedException ====================

    @Test
    fun testTransactionFailedExceptionConstructor() {
        val message = "Transaction execution failed on network"
        val exception = TransactionFailedException(
            message = message,
            assembledTransaction = assembledTransaction
        )

        assertEquals(message, exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
        assertNull(exception.cause)
    }

    @Test
    fun testTransactionFailedExceptionInheritsFromContractException() {
        val exception = TransactionFailedException(
            message = TEST_MESSAGE,
            assembledTransaction = assembledTransaction
        )

        assertTrue(exception is ContractException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testTransactionFailedExceptionCanBeCaughtAsContractException() {
        val exception = TransactionFailedException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertTrue(caught is TransactionFailedException)
        assertEquals(TEST_MESSAGE, caught.message)
    }

    @Test
    fun testTransactionFailedExceptionCanBeCaughtAsSpecificType() {
        val exception = TransactionFailedException(TEST_MESSAGE, assembledTransaction)

        val caught = assertFailsWith<TransactionFailedException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertSame(assembledTransaction, caught.assembledTransaction)
    }

    // ==================== Cross-Cutting Tests ====================

    @Test
    fun testAllExceptionsInheritFromContractException() {
        val exceptions = listOf(
            NotYetSimulatedException(TEST_MESSAGE, assembledTransaction),
            SimulationFailedException(TEST_MESSAGE, assembledTransaction),
            RestorationFailureException(TEST_MESSAGE, assembledTransaction),
            NoSignatureNeededException(TEST_MESSAGE, assembledTransaction),
            NeedsMoreSignaturesException(TEST_MESSAGE, assembledTransaction),
            ExpiredStateException(TEST_MESSAGE, assembledTransaction),
            SendTransactionFailedException(TEST_MESSAGE, assembledTransaction),
            TransactionStillPendingException(TEST_MESSAGE, assembledTransaction),
            TransactionFailedException(TEST_MESSAGE, assembledTransaction)
        )

        exceptions.forEach { exception ->
            assertTrue(
                exception is ContractException,
                "${exception::class.simpleName} should inherit from ContractException"
            )
        }
    }

    @Test
    fun testAllExceptionsPreserveAssembledTransactionReference() {
        val exceptions = listOf(
            NotYetSimulatedException(TEST_MESSAGE, assembledTransaction),
            SimulationFailedException(TEST_MESSAGE, assembledTransaction),
            RestorationFailureException(TEST_MESSAGE, assembledTransaction),
            NoSignatureNeededException(TEST_MESSAGE, assembledTransaction),
            NeedsMoreSignaturesException(TEST_MESSAGE, assembledTransaction),
            ExpiredStateException(TEST_MESSAGE, assembledTransaction),
            SendTransactionFailedException(TEST_MESSAGE, assembledTransaction),
            TransactionStillPendingException(TEST_MESSAGE, assembledTransaction),
            TransactionFailedException(TEST_MESSAGE, assembledTransaction)
        )

        exceptions.forEach { exception ->
            assertSame(
                assembledTransaction,
                exception.assembledTransaction,
                "${exception::class.simpleName} should preserve assembledTransaction reference"
            )
        }
    }

    @Test
    fun testAllExceptionsPreserveMessage() {
        val customMessage = "Custom error message for testing"
        val exceptions = listOf(
            NotYetSimulatedException(customMessage, assembledTransaction),
            SimulationFailedException(customMessage, assembledTransaction),
            RestorationFailureException(customMessage, assembledTransaction),
            NoSignatureNeededException(customMessage, assembledTransaction),
            NeedsMoreSignaturesException(customMessage, assembledTransaction),
            ExpiredStateException(customMessage, assembledTransaction),
            SendTransactionFailedException(customMessage, assembledTransaction),
            TransactionStillPendingException(customMessage, assembledTransaction),
            TransactionFailedException(customMessage, assembledTransaction)
        )

        exceptions.forEach { exception ->
            assertEquals(
                customMessage,
                exception.message,
                "${exception::class.simpleName} should preserve custom message"
            )
        }
    }

    @Test
    fun testAllExceptionsHaveNullCauseByDefault() {
        val exceptions = listOf(
            NotYetSimulatedException(TEST_MESSAGE, assembledTransaction),
            SimulationFailedException(TEST_MESSAGE, assembledTransaction),
            RestorationFailureException(TEST_MESSAGE, assembledTransaction),
            NoSignatureNeededException(TEST_MESSAGE, assembledTransaction),
            NeedsMoreSignaturesException(TEST_MESSAGE, assembledTransaction),
            ExpiredStateException(TEST_MESSAGE, assembledTransaction),
            SendTransactionFailedException(TEST_MESSAGE, assembledTransaction),
            TransactionStillPendingException(TEST_MESSAGE, assembledTransaction),
            TransactionFailedException(TEST_MESSAGE, assembledTransaction)
        )

        exceptions.forEach { exception ->
            assertNull(
                exception.cause,
                "${exception::class.simpleName} should have null cause by default"
            )
        }
    }

    @Test
    fun testExceptionTypeDistinction() {
        // Verify that each exception type is distinct and can be differentiated
        val exception1 = NotYetSimulatedException(TEST_MESSAGE, assembledTransaction)
        val exception2 = SimulationFailedException(TEST_MESSAGE, assembledTransaction)
        val exception3 = RestorationFailureException(TEST_MESSAGE, assembledTransaction)
        val exception4 = NoSignatureNeededException(TEST_MESSAGE, assembledTransaction)
        val exception5 = NeedsMoreSignaturesException(TEST_MESSAGE, assembledTransaction)
        val exception6 = ExpiredStateException(TEST_MESSAGE, assembledTransaction)
        val exception7 = SendTransactionFailedException(TEST_MESSAGE, assembledTransaction)
        val exception8 = TransactionStillPendingException(TEST_MESSAGE, assembledTransaction)
        val exception9 = TransactionFailedException(TEST_MESSAGE, assembledTransaction)

        // Each exception should be identifiable by its specific type
        assertTrue(exception1 is NotYetSimulatedException)
        assertFalse(exception1 is SimulationFailedException)

        assertTrue(exception2 is SimulationFailedException)
        assertFalse(exception2 is NotYetSimulatedException)

        assertTrue(exception3 is RestorationFailureException)
        assertFalse(exception3 is SimulationFailedException)

        assertTrue(exception4 is NoSignatureNeededException)
        assertFalse(exception4 is NeedsMoreSignaturesException)

        assertTrue(exception5 is NeedsMoreSignaturesException)
        assertFalse(exception5 is NoSignatureNeededException)

        assertTrue(exception6 is ExpiredStateException)
        assertFalse(exception6 is RestorationFailureException)

        assertTrue(exception7 is SendTransactionFailedException)
        assertFalse(exception7 is TransactionStillPendingException)

        assertTrue(exception8 is TransactionStillPendingException)
        assertFalse(exception8 is SendTransactionFailedException)

        assertTrue(exception9 is TransactionFailedException)
        assertFalse(exception9 is TransactionStillPendingException)
    }

    @Test
    fun testExceptionCanBeUsedInWhenExpression() {
        val exception: ContractException = SimulationFailedException(TEST_MESSAGE, assembledTransaction)

        val result = when (exception) {
            is NotYetSimulatedException -> "not_simulated"
            is SimulationFailedException -> "simulation_failed"
            is RestorationFailureException -> "restoration_failed"
            is NoSignatureNeededException -> "no_signature_needed"
            is NeedsMoreSignaturesException -> "needs_more_signatures"
            is ExpiredStateException -> "expired_state"
            is SendTransactionFailedException -> "send_failed"
            is TransactionStillPendingException -> "still_pending"
            is TransactionFailedException -> "transaction_failed"
            else -> "unknown"
        }

        assertEquals("simulation_failed", result)
    }

    @Test
    fun testContractExceptionWithCauseChain() {
        val rootException = RuntimeException("Network error")
        val wrappedException = IllegalStateException("Wrapped error", rootException)
        val contractException = ContractException(
            message = "Contract error",
            assembledTransaction = assembledTransaction,
            cause = wrappedException
        )

        // Verify cause chain is preserved
        assertSame(wrappedException, contractException.cause)
        assertSame(rootException, contractException.cause?.cause)

        // Verify message chain
        assertEquals("Contract error", contractException.message)
        assertEquals("Wrapped error", contractException.cause?.message)
        assertEquals("Network error", contractException.cause?.cause?.message)
    }

    @Test
    fun testExceptionMessagesCanContainSpecialCharacters() {
        val specialMessages = listOf(
            "Error: Transaction failed (status=ERROR)",
            "Failed with code: 0x1234",
            "Message with\nnewlines\nand\ttabs",
            "Unicode: \u00A9\u00AE\u2122",
            "JSON: {\"error\": \"value\"}",
            "Path: /path/to/file.txt",
            "Percentage: 100%",
            "Ampersand: A & B"
        )

        specialMessages.forEach { message ->
            val exception = ContractException(message, assembledTransaction)
            assertEquals(message, exception.message)
        }
    }

    @Test
    fun testExceptionWithEmptyMessage() {
        val exception = ContractException(
            message = "",
            assembledTransaction = assembledTransaction
        )

        assertEquals("", exception.message)
        assertSame(assembledTransaction, exception.assembledTransaction)
    }

    @Test
    fun testExceptionWithVeryLongMessage() {
        val longMessage = "Error: " + "x".repeat(10000)
        val exception = ContractException(
            message = longMessage,
            assembledTransaction = assembledTransaction
        )

        assertEquals(longMessage, exception.message)
        assertTrue(exception.message!!.length > 10000)
    }

    @Test
    fun testMultipleExceptionsCanReferencesSameAssembledTransaction() {
        val exception1 = NotYetSimulatedException("Error 1", assembledTransaction)
        val exception2 = SimulationFailedException("Error 2", assembledTransaction)
        val exception3 = TransactionFailedException("Error 3", assembledTransaction)

        assertSame(assembledTransaction, exception1.assembledTransaction)
        assertSame(assembledTransaction, exception2.assembledTransaction)
        assertSame(assembledTransaction, exception3.assembledTransaction)
        assertSame(exception1.assembledTransaction, exception2.assembledTransaction)
        assertSame(exception2.assembledTransaction, exception3.assembledTransaction)
    }
}
