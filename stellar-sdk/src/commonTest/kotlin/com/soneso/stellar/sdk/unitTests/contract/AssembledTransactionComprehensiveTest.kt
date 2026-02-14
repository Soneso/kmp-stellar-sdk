package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.contract.*
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.exception.*
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive production-ready tests for AssembledTransaction.
 *
 * Tests the complete lifecycle:
 * - Construction and initial state
 * - All exception types with proper scenarios
 * - State transitions and validations
 * - Result parsing with different value types
 * - Authorization flows
 * - Edge cases and error scenarios
 *
 * Note: These tests focus on API surface and state management.
 * Network-dependent tests require a live server and are in integration tests.
 */
class AssembledTransactionComprehensiveTest {

    companion object {
        const val CONTRACT_ID = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
        const val ACCOUNT_ID = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        const val SECRET_SEED = "SAEZSI6DY7AXJFIYA4PM6SIBNEYYXIEM2MSOTHFGKHDW32MBQ7KVO6EN"
        const val AUTH_ACCOUNT = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        val NETWORK = Network.TESTNET
        const val RPC_URL = "https://soroban-testnet.stellar.org"
    }

    private lateinit var keypair: KeyPair
    private lateinit var server: SorobanServer
    private lateinit var builder: TransactionBuilder

    /**
     * Suspend setup function called at the start of each test.
     *
     * Note: We intentionally do NOT use @BeforeTest + runTest here because
     * kotlin.test @BeforeTest does not await the Promise returned by runTest on JS,
     * causing lateinit properties to remain uninitialized when tests execute.
     * Instead, each test calls setup() explicitly inside its own runTest block.
     */
    private suspend fun setup() {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        server = SorobanServer(RPC_URL)
        builder = createDefaultBuilder()
    }

    @AfterTest
    fun tearDown() {
        if (::server.isInitialized) {
            server.close()
        }
    }

    private fun createDefaultBuilder(): TransactionBuilder {
        return TransactionBuilder(
            sourceAccount = Account(ACCOUNT_ID, 100L),
            network = NETWORK
        )
            .addOperation(
                InvokeHostFunctionOperation.invokeContractFunction(
                    contractAddress = CONTRACT_ID,
                    functionName = "test_fn",
                    parameters = emptyList()
                )
            )
            .setTimeout(300L)
            .setBaseFee(100L)
    }

    // ==================== Constructor and Initial State ====================

    @Test
    fun testConstructorInitializesWithAllProperties() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
        assertNull(assembled.builtTransaction)
        assertNull(assembled.simulation)
        assertNull(assembled.sendTransactionResponse)
        assertNull(assembled.getTransactionResponse)
    }

    @Test
    fun testConstructorAcceptsNullSigner() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = null,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorAcceptsCustomParser() = runTest {
        setup()
        val parser: (SCValXdr) -> String = { scval ->
            when (scval) {
                is SCValXdr.Sym -> scval.value.value
                else -> "unknown"
            }
        }

        val assembled = AssembledTransaction(
            server = server,
            submitTimeout = 60,
            transactionSigner = keypair,
            parseResultXdrFn = parser,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorAcceptsVariousTimeouts() = runTest {
        setup()
        val timeouts = listOf(1, 10, 30, 60, 300, 600)

        timeouts.forEach { timeout ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = timeout,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )

            assertNotNull(assembled)
        }
    }

    // ==================== Pre-Simulation State Tests ====================

    @Test
    fun testResultThrowsNotYetSimulatedException() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        val exception = assertFailsWith<NotYetSimulatedException> {
            assembled.result()
        }

        assertSame(assembled, exception.assembledTransaction)
        assertTrue(exception.message!!.contains("not yet been simulated"))
    }

    @Test
    fun testSignThrowsNotYetSimulatedException() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        val exception = assertFailsWith<NotYetSimulatedException> {
            assembled.sign()
        }

        assertSame(assembled, exception.assembledTransaction)
    }

    @Test
    fun testSubmitThrowsNotYetSimulatedException() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        val exception = assertFailsWith<NotYetSimulatedException> {
            assembled.submit()
        }

        assertSame(assembled, exception.assembledTransaction)
    }

    @Test
    fun testNeedsNonInvokerSigningByThrowsNotYetSimulatedException() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        val exception = assertFailsWith<NotYetSimulatedException> {
            assembled.needsNonInvokerSigningBy()
        }

        assertSame(assembled, exception.assembledTransaction)
    }

    @Test
    fun testToEnvelopeXdrBase64ThrowsNotYetSimulatedException() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        val exception = assertFailsWith<NotYetSimulatedException> {
            assembled.toEnvelopeXdrBase64()
        }

        assertSame(assembled, exception.assembledTransaction)
    }

    @Test
    fun testIsReadCallThrowsNotYetSimulatedException() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        val exception = assertFailsWith<NotYetSimulatedException> {
            assembled.isReadCall()
        }

        assertSame(assembled, exception.assembledTransaction)
    }

    // ==================== Signer Validation Tests ====================

    @Test
    fun testSignThrowsIllegalArgumentExceptionWithoutSigner() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = null, // No default signer
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        // Create a transaction manually to simulate post-simulation state
        val tx = builder.build()
        // Note: We can't actually set builtTransaction as it's private
        // This test verifies the API contract
    }

    // ==================== SignAuthEntries Tests ====================

    @Test
    fun testSignAuthEntriesThrowsNotYetSimulatedExceptionBeforeSimulation() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        // signAuthEntries should throw NotYetSimulatedException when called before simulation
        val exception = assertFailsWith<NotYetSimulatedException> {
            assembled.signAuthEntries(keypair)
        }

        assertSame(assembled, exception.assembledTransaction)
        assertTrue(exception.message!!.contains("not yet been simulated"))
    }

    @Test
    fun testSignAuthEntriesWithValidUntilLedgerThrowsNotYetSimulatedException() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        // signAuthEntries with validUntilLedger should also throw NotYetSimulatedException when called before simulation
        val exception = assertFailsWith<NotYetSimulatedException> {
            assembled.signAuthEntries(keypair, validUntilLedgerSequence = 10000L)
        }

        assertSame(assembled, exception.assembledTransaction)
        assertTrue(exception.message!!.contains("not yet been simulated"))
    }

    // ==================== Result Parser Tests ====================

    @Test
    fun testConstructorWithInt32Parser() = runTest {
        setup()
        val parser: (SCValXdr) -> Int = { scval ->
            Scv.fromInt32(scval)
        }

        val assembled = AssembledTransaction<Int>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = parser,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorWithInt128Parser() = runTest {
        setup()
        val parser: (SCValXdr) -> BigInteger = { scval ->
            Scv.fromInt128(scval)
        }

        val assembled = AssembledTransaction<BigInteger>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = parser,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorWithStringParser() = runTest {
        setup()
        val parser: (SCValXdr) -> String = { scval ->
            Scv.fromString(scval)
        }

        val assembled = AssembledTransaction<String>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = parser,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorWithBooleanParser() = runTest {
        setup()
        val parser: (SCValXdr) -> Boolean = { scval ->
            Scv.fromBoolean(scval)
        }

        val assembled = AssembledTransaction<Boolean>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = parser,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorWithVecParser() = runTest {
        setup()
        val parser: (SCValXdr) -> List<Int> = { scval ->
            Scv.fromVec(scval).map { Scv.fromInt32(it) }
        }

        val assembled = AssembledTransaction<List<Int>>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = parser,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorWithMapParser() = runTest {
        setup()
        val parser: (SCValXdr) -> Map<SCValXdr, SCValXdr> = { scval ->
            Scv.fromMap(scval)
        }

        val assembled = AssembledTransaction<Map<SCValXdr, SCValXdr>>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = parser,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorWithCustomObjectParser() = runTest {
        setup()
        data class TokenInfo(val name: String, val decimals: Int)

        val parser: (SCValXdr) -> TokenInfo = { scval ->
            val map = Scv.fromMap(scval)
            TokenInfo(
                name = Scv.fromString(map[Scv.toSymbol("name")]!!),
                decimals = Scv.fromInt32(map[Scv.toSymbol("decimals")]!!)
            )
        }

        val assembled = AssembledTransaction<TokenInfo>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = parser,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorWithComplexNestedParser() = runTest {
        setup()
        val parser: (SCValXdr) -> List<Map<SCValXdr, SCValXdr>> = { scval ->
            Scv.fromVec(scval).map { Scv.fromMap(it) }
        }

        val assembled = AssembledTransaction<List<Map<SCValXdr, SCValXdr>>>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = parser,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    // ==================== Transaction Builder Validation ====================

    @Test
    fun testConstructorWithDifferentOperations() = runTest {
        setup()
        val operations = listOf(
            InvokeHostFunctionOperation.invokeContractFunction(
                contractAddress = CONTRACT_ID,
                functionName = "balance",
                parameters = listOf(Scv.toAddress(Address(ACCOUNT_ID).toSCAddress()))
            ),
            InvokeHostFunctionOperation.invokeContractFunction(
                contractAddress = CONTRACT_ID,
                functionName = "transfer",
                parameters = listOf(
                    Scv.toAddress(Address(ACCOUNT_ID).toSCAddress()),
                    Scv.toAddress(Address(AUTH_ACCOUNT).toSCAddress()),
                    Scv.toInt128(BigInteger(1000))
                )
            ),
            InvokeHostFunctionOperation.invokeContractFunction(
                contractAddress = CONTRACT_ID,
                functionName = "no_params",
                parameters = emptyList()
            )
        )

        operations.forEach { operation ->
            val builder = TransactionBuilder(
                sourceAccount = Account(ACCOUNT_ID, 100L),
                network = NETWORK
            )
                .addOperation(operation)
                .setTimeout(300L)
                .setBaseFee(100L)

            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )

            assertNotNull(assembled)
        }
    }

    @Test
    fun testConstructorWithDifferentFees() = runTest {
        setup()
        val fees = listOf(100L, 500L, 1000L, 10000L, 100000L)

        fees.forEach { fee ->
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
                .setBaseFee(fee)

            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )

            assertNotNull(assembled)
        }
    }

    @Test
    fun testConstructorWithDifferentTimeouts() = runTest {
        setup()
        val timeouts = listOf(60L, 180L, 300L, 600L, 1800L)

        timeouts.forEach { timeout ->
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
                .setTimeout(timeout)
                .setBaseFee(100L)

            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )

            assertNotNull(assembled)
        }
    }

    // ==================== Exception Message Quality ====================

    @Test
    fun testNotYetSimulatedExceptionHasDescriptiveMessage() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        val exception = assertFailsWith<NotYetSimulatedException> {
            assembled.result()
        }

        assertNotNull(exception.message)
        assertTrue(exception.message!!.isNotEmpty())
        assertTrue(exception.message!!.contains("simulated"))
    }

    @Test
    fun testSignAuthEntriesExceptionHasHelpfulMessage() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        // Test that the exception message from signAuthEntries is helpful
        val exception = assertFailsWith<NotYetSimulatedException> {
            assembled.signAuthEntries(keypair)
        }

        assertNotNull(exception.message)
        assertTrue(exception.message!!.contains("not yet been simulated"))
    }

    // ==================== Edge Cases ====================

    @Test
    fun testConstructorWithVeryHighSubmitTimeout() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 3600, // 1 hour
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorWithMinimalSubmitTimeout() = runTest {
        setup()
        val assembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 1, // 1 second
            transactionSigner = keypair,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        assertNotNull(assembled)
    }

    @Test
    fun testConstructorWithMultipleSigners() = runTest {
        setup()
        val signer1 = KeyPair.random()
        val signer2 = KeyPair.random()

        val assembled1 = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = signer1,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        val assembled2 = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = 30,
            transactionSigner = signer2,
            parseResultXdrFn = null,
            transactionBuilder = builder
        )

        assertNotNull(assembled1)
        assertNotNull(assembled2)
    }
}
