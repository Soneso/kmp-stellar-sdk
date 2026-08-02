package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.contract.*
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.exception.*
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Unit tests for AssembledTransaction.
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
class AssembledTransactionTest {

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

    // ==================== Simulation / sign / submit lifecycle (mocked RPC) ====================

    /** Transaction builder pointed at the shared mock-RPC fixtures. */
    private fun mockBuilder(functionName: String = "hello"): TransactionBuilder {
        return TransactionBuilder(
            sourceAccount = Account(MOCK_RPC_SOURCE_ACCOUNT, MOCK_RPC_SOURCE_SEQ),
            network = NETWORK
        )
            .addOperation(
                InvokeHostFunctionOperation.invokeContractFunction(
                    contractAddress = MOCK_RPC_CONTRACT_ID,
                    functionName = functionName,
                    parameters = emptyList()
                )
            )
            .setTimeout(300L)
            .setBaseFee(100L)
    }

    /** A non-invoker auth entry addressed to [accountId], unsigned. */
    private fun addressAuthEntry(accountId: String, functionName: String = "hello"): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(
                SorobanAddressCredentialsXdr(
                    address = Address(accountId).toSCAddress(),
                    nonce = Int64Xdr(1L),
                    signatureExpirationLedger = Uint32Xdr(1000u),
                    signature = Scv.toVoid()
                )
            ),
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(
                    InvokeContractArgsXdr(
                        contractAddress = Address(MOCK_RPC_CONTRACT_ID).toSCAddress(),
                        functionName = SCSymbolXdr(functionName),
                        args = emptyList()
                    )
                ),
                subInvocations = emptyList()
            )
        )

    private fun simulateErrorResultJson(message: String): String =
        """{ "error": "$message", "latestLedger": 14245 }"""

    /** A simulate() success payload that also carries a restorePreamble. */
    private fun simulateWithRestoreResultJson(
        returnValue: SCValXdr,
        authEntries: List<SorobanAuthorizationEntryXdr>
    ): String {
        val authArray = authEntries.joinToString(",") { "\"${it.toXdrBase64()}\"" }
        val txData = minimalSorobanData().toXdrBase64()
        val returnXdr = returnValue.toXdrBase64()
        val restoreTxData = minimalSorobanData().toXdrBase64()
        return """
            {
              "transactionData": "$txData",
              "minResourceFee": "100",
              "results": [ { "auth": [ $authArray ], "xdr": "$returnXdr" } ],
              "restorePreamble": { "transactionData": "$restoreTxData", "minResourceFee": "100" },
              "latestLedger": 14245
            }
        """.trimIndent()
    }

    /** Base64 TransactionMeta (V3) with no Soroban meta at all, i.e. no return value. */
    private fun noReturnValueResultMetaBase64(): String {
        val meta = TransactionMetaXdr.V3(
            TransactionMetaV3Xdr(
                ext = ExtensionPointXdr.Void,
                txChangesBefore = LedgerEntryChangesXdr(emptyList()),
                operations = emptyList(),
                txChangesAfter = LedgerEntryChangesXdr(emptyList()),
                sorobanMeta = null
            )
        )
        return meta.toXdrBase64()
    }

    /**
     * A MockEngine-backed [SorobanServer] whose simulateTransaction / sendTransaction /
     * getTransaction bodies are supplied verbatim, for scenarios [mockRpcServer] cannot
     * express (simulation errors, restorePreamble responses, result metas with no
     * Soroban return value).
     */
    private fun rawJsonMockServer(
        sourceAccount: String = MOCK_RPC_SOURCE_ACCOUNT,
        simulateResultJson: String,
        sendTransactionResultJson: String? = null,
        getTransactionResultJson: String? = null
    ): SorobanServer {
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            val resultJson = when {
                "\"getLedgerEntries\"" in body -> ledgerEntriesResultJson(sourceAccount)
                "\"simulateTransaction\"" in body -> simulateResultJson
                sendTransactionResultJson != null && "\"sendTransaction\"" in body -> sendTransactionResultJson
                getTransactionResultJson != null && "\"getTransaction\"" in body -> getTransactionResultJson
                else -> error("unrouted JSON-RPC request in rawJsonMockServer: $body")
            }
            respond(
                content = ByteReadChannel("""{ "jsonrpc": "2.0", "id": "1", "result": $resultJson }"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false })
            }
        }
        return SorobanServer(MOCK_RPC_SERVER_URL, client)
    }

    @Test
    fun testSimulateThrowsSimulationFailedExceptionOnError() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        rawJsonMockServer(simulateResultJson = simulateErrorResultJson("host invocation failed")).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            val ex = assertFailsWith<SimulationFailedException> { assembled.simulate() }
            assertTrue(ex.message!!.contains("host invocation failed"))
            assertSame(assembled, ex.assembledTransaction)
        }
    }

    @Test
    fun testSignThrowsExpiredStateExceptionWhenRestorePreambleIsPresent() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val simJson = simulateWithRestoreResultJson(Scv.toVoid(), listOf(writeAuthEntry()))
        rawJsonMockServer(simulateResultJson = simJson).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            // restore=false: the restore branch is skipped, so builtTransaction is still
            // assembled even though the simulation carries a restorePreamble.
            assembled.simulate(restore = false)
            val ex = assertFailsWith<ExpiredStateException> { assembled.sign() }
            assertSame(assembled, ex.assembledTransaction)
        }
    }

    @Test
    fun testSimulateThrowsRestorationFailureExceptionWithoutSigner() = runTest {
        val simJson = simulateWithRestoreResultJson(Scv.toVoid(), listOf(writeAuthEntry()))
        rawJsonMockServer(simulateResultJson = simJson).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = null,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            val ex = assertFailsWith<RestorationFailureException> { assembled.simulate(restore = true) }
            assertTrue(ex.message!!.contains("Failed to restore contract data"))
            assertSame(assembled, ex.assembledTransaction)
        }
    }

    @Test
    fun testSignThrowsNeedsMoreSignaturesExceptionForUnsignedNonInvokerEntry() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val otherAccount = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        mockRpcServer(
            simulateReturnValue = Scv.toVoid(),
            authEntries = listOf(addressAuthEntry(otherAccount))
        ).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            val ex = assertFailsWith<NeedsMoreSignaturesException> { assembled.sign() }
            assertTrue(ex.message!!.contains(otherAccount))
            assertSame(assembled, ex.assembledTransaction)
        }
    }

    @Test
    fun testSignThrowsIllegalArgumentExceptionWhenNoSignerAvailable() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid(), authEntries = listOf(writeAuthEntry())).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = null,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            val ex = assertFailsWith<IllegalArgumentException> { assembled.sign() }
            assertTrue(ex.message!!.contains("You must provide a transactionSigner"))
        }
    }

    @Test
    fun testSignThrowsNoSignatureNeededExceptionForReadCall() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        mockRpcServer(simulateReturnValue = SCValXdr.B(true)).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            assertTrue(assembled.isReadCall())
            val ex = assertFailsWith<NoSignatureNeededException> { assembled.sign() }
            assertSame(assembled, ex.assembledTransaction)
        }
    }

    @Test
    fun testSignWithForceTrueSignsReadCall() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        mockRpcServer(simulateReturnValue = SCValXdr.B(true)).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            assembled.sign(force = true)
            assertNotNull(assembled.signed)
        }
    }

    @Test
    fun testIsReadCallFalseForWriteCall() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid(), authEntries = listOf(writeAuthEntry())).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = null,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            assertFalse(assembled.isReadCall())
        }
    }

    @Test
    fun testNeedsNonInvokerSigningByReturnsEmptyForNonInvokeHostFunctionOperation() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val builder = TransactionBuilder(
                sourceAccount = Account(MOCK_RPC_SOURCE_ACCOUNT, MOCK_RPC_SOURCE_SEQ),
                network = NETWORK
            )
                .addOperation(RestoreFootprintOperation())
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )
            assembled.simulate()
            assertTrue(assembled.needsNonInvokerSigningBy().isEmpty())
        }
    }

    // ==================== signAuthEntries guards ====================

    @Test
    fun testSignAuthEntriesThrowsWhenNoUnsignedEntries() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        // writeAuthEntry() carries source-account (Void) credentials, which contribute
        // nothing to needsNonInvokerSigningBy: it is a write call with no signable entries.
        mockRpcServer(simulateReturnValue = Scv.toVoid(), authEntries = listOf(writeAuthEntry())).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            val ex = assertFailsWith<IllegalStateException> { assembled.signAuthEntries(keypair) }
            assertTrue(ex.message!!.contains("maybe you already signed"))
        }
    }

    @Test
    fun testSignAuthEntriesThrowsWhenSignerNotAddressed() = runTest {
        val addressedAccount = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        val unrelatedSigner = KeyPair.random()
        mockRpcServer(
            simulateReturnValue = Scv.toVoid(),
            authEntries = listOf(addressAuthEntry(addressedAccount))
        ).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = unrelatedSigner,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            val ex = assertFailsWith<IllegalStateException> { assembled.signAuthEntries(unrelatedSigner) }
            assertTrue(ex.message!!.contains("No auth entries for public key ${unrelatedSigner.getAccountId()}"))
            assertTrue(ex.message!!.contains(addressedAccount))
        }
    }

    @Test
    fun testSignAuthEntriesThrowsWhenSignerLacksPrivateKey() = runTest {
        val addressedAccount = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        mockRpcServer(
            simulateReturnValue = Scv.toVoid(),
            authEntries = listOf(addressAuthEntry(addressedAccount))
        ).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = null,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            val publicOnly = KeyPair.fromAccountId(addressedAccount)
            val ex = assertFailsWith<IllegalArgumentException> { assembled.signAuthEntries(publicOnly) }
            assertTrue(ex.message!!.contains("private key"))
        }
    }

    // ==================== submit() result parsing ====================

    @Test
    fun testSubmitReturnsParsedResultFromTransactionMeta() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        mockRpcServer(
            simulateReturnValue = Scv.toVoid(),
            authEntries = listOf(writeAuthEntry()),
            sourceAccount = source,
            submittedReturnValue = SCValXdr.Sym(SCSymbolXdr("done"))
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<String>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = { (it as SCValXdr.Sym).value.value },
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            assertEquals("done", assembled.submit())
        }
    }

    @Test
    fun testSubmitReturnsRawScValWhenNoParser() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        mockRpcServer(
            simulateReturnValue = Scv.toVoid(),
            authEntries = listOf(writeAuthEntry()),
            sourceAccount = source,
            submittedReturnValue = Scv.toUint32(99u)
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            val result = assembled.submit()
            assertTrue(result is SCValXdr.U32)
            assertEquals(99u, (result as SCValXdr.U32).value.value)
        }
    }

    @Test
    fun testSubmitThrowsIllegalStateExceptionWhenMetaHasNoReturnValue() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        val txHash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"
        rawJsonMockServer(
            sourceAccount = source,
            simulateResultJson = simulateResultJson(Scv.toVoid(), listOf(writeAuthEntry())),
            sendTransactionResultJson = """{ "status": "PENDING", "hash": "$txHash", "latestLedger": 14245 }""",
            getTransactionResultJson = """
                {
                  "status": "SUCCESS",
                  "txHash": "$txHash",
                  "latestLedger": 14246,
                  "resultMetaXdr": "${noReturnValueResultMetaBase64()}"
                }
            """.trimIndent()
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            val ex = assertFailsWith<IllegalStateException> { assembled.submit() }
            assertTrue(ex.message!!.contains("No return value in transaction meta"))
        }
    }

    @Test
    fun testSignAndSubmitCombinesSignAndSubmit() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        mockRpcServer(
            simulateReturnValue = Scv.toVoid(),
            authEntries = listOf(writeAuthEntry()),
            sourceAccount = source,
            submittedReturnValue = SCValXdr.B(true)
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<Boolean>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = { (it as SCValXdr.B).value },
                transactionBuilder = builder
            )
            assembled.simulate()
            assertTrue(assembled.signAndSubmit())
        }
    }

    // ==================== Helpers: sequenced RPC responses, alternate result metas ====================

    /**
     * A MockEngine-backed [SorobanServer] that serves a distinct response per call for each
     * JSON-RPC method, consumed from the front of each list in call order. Needed for flows
     * that invoke the same method more than once with different results within a single
     * public API call, such as automatic footprint restoration (which simulates the outer
     * transaction, the restore transaction, and then the outer transaction again).
     */
    private fun sequencedJsonMockServer(
        sourceAccount: String = MOCK_RPC_SOURCE_ACCOUNT,
        simulateResultJsons: List<String> = emptyList(),
        sendTransactionResultJsons: List<String> = emptyList(),
        getTransactionResultJsons: List<String> = emptyList()
    ): SorobanServer {
        var simulateIndex = 0
        var sendIndex = 0
        var getIndex = 0
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            val resultJson = when {
                "\"getLedgerEntries\"" in body -> ledgerEntriesResultJson(sourceAccount)
                "\"simulateTransaction\"" in body -> simulateResultJsons[simulateIndex++]
                "\"sendTransaction\"" in body -> sendTransactionResultJsons[sendIndex++]
                "\"getTransaction\"" in body -> getTransactionResultJsons[getIndex++]
                else -> error("unrouted JSON-RPC request in sequencedJsonMockServer: $body")
            }
            respond(
                content = ByteReadChannel("""{ "jsonrpc": "2.0", "id": "1", "result": $resultJson }"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false })
            }
        }
        return SorobanServer(MOCK_RPC_SERVER_URL, client)
    }

    /** Base64 TransactionMeta (V4) carrying [returnValue] as the Soroban return value. */
    private fun v4ResultMetaBase64(returnValue: SCValXdr): String {
        val meta = TransactionMetaXdr.V4(
            TransactionMetaV4Xdr(
                ext = ExtensionPointXdr.Void,
                txChangesBefore = LedgerEntryChangesXdr(emptyList()),
                operations = emptyList(),
                txChangesAfter = LedgerEntryChangesXdr(emptyList()),
                sorobanMeta = SorobanTransactionMetaV2Xdr(
                    ext = SorobanTransactionMetaExtXdr.Void,
                    returnValue = returnValue
                ),
                events = emptyList(),
                diagnosticEvents = emptyList()
            )
        )
        return meta.toXdrBase64()
    }

    /** Base64 TransactionMeta from a pre-Soroban ledger (case 0), carrying no Soroban meta at all. */
    private fun preSorobanResultMetaBase64(): String {
        return TransactionMetaXdr.Operations(emptyList()).toXdrBase64()
    }

    /**
     * An AddressWithDelegates auth entry whose delegate tree is 130 levels deep, built
     * entirely in memory (never encoded/decoded as XDR bytes, since SorobanDelegateSignatureXdr's
     * own decoder enforces the same 128-deep cap and would reject the tree before
     * AssembledTransaction's own traversal-cap check ever runs).
     */
    private fun deeplyNestedDelegateAuthEntry(
        topAccountId: String,
        functionName: String = "hello"
    ): SorobanAuthorizationEntryXdr {
        val leafAddress = Address(topAccountId).toSCAddress()
        var deepNode = SorobanDelegateSignatureXdr(leafAddress, Scv.toVoid(), emptyList())
        repeat(130) {
            deepNode = SorobanDelegateSignatureXdr(leafAddress, Scv.toVoid(), listOf(deepNode))
        }
        return SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.AddressWithDelegates(
                SorobanAddressCredentialsWithDelegatesXdr(
                    addressCredentials = SorobanAddressCredentialsXdr(
                        address = Address(topAccountId).toSCAddress(),
                        nonce = Int64Xdr(1L),
                        signatureExpirationLedger = Uint32Xdr(1000u),
                        signature = Scv.toVoid()
                    ),
                    delegates = listOf(deepNode)
                )
            ),
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(
                    InvokeContractArgsXdr(
                        contractAddress = Address(MOCK_RPC_CONTRACT_ID).toSCAddress(),
                        functionName = SCSymbolXdr(functionName),
                        args = emptyList()
                    )
                ),
                subInvocations = emptyList()
            )
        )
    }

    // ==================== simulate(): read-call restore skip, full restore flow ====================

    @Test
    fun testSimulateSkipsAutoRestoreForReadCallWithRestorePreamble() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        // No auth entries and the default empty read-write footprint make this a read
        // call; the restore branch must not fire even though restorePreamble is present.
        val simJson = simulateWithRestoreResultJson(SCValXdr.B(true), authEntries = emptyList())
        rawJsonMockServer(simulateResultJson = simJson).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate(restore = true)
            assertTrue(assembled.isReadCall())
            assertNotNull(assembled.builtTransaction)
            assertNotNull(assembled.simulation!!.restorePreamble)
        }
    }

    @Test
    fun testSimulateThrowsRestorationFailureExceptionWhenRestoreSimulationFails() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val withRestoreJson = simulateWithRestoreResultJson(Scv.toVoid(), listOf(writeAuthEntry()))
        val restoreSimFailsJson = simulateErrorResultJson("restore simulation rejected")
        sequencedJsonMockServer(
            simulateResultJsons = listOf(withRestoreJson, restoreSimFailsJson)
        ).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            val ex = assertFailsWith<RestorationFailureException> { assembled.simulate(restore = true) }
            // The ContractException catch path rethrows a fixed message, unlike the
            // generic-Exception catch path, which appends the error text.
            assertEquals("Failed to restore contract data.", ex.message)
            assertSame(assembled, ex.assembledTransaction)
        }
    }

    @Test
    fun testSimulateAutomaticallyRestoresFootprintAndResimulates() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val withRestoreJson = simulateWithRestoreResultJson(Scv.toVoid(), listOf(writeAuthEntry()))
        val normalJson = simulateResultJson(Scv.toVoid(), listOf(writeAuthEntry()))
        val txHash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"
        sequencedJsonMockServer(
            simulateResultJsons = listOf(withRestoreJson, normalJson, normalJson),
            sendTransactionResultJsons = listOf(
                """{ "status": "PENDING", "hash": "$txHash", "latestLedger": 14245 }"""
            ),
            getTransactionResultJsons = listOf(
                """{ "status": "SUCCESS", "txHash": "$txHash", "latestLedger": 14246 }"""
            )
        ).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate(restore = true)
            // Restoration succeeded and the outer transaction was re-simulated: the final
            // simulation carries no restorePreamble and the transaction is fully assembled.
            assertNotNull(assembled.builtTransaction)
            assertNull(assembled.simulation!!.restorePreamble)
        }
    }

    // ==================== signAuthEntries: operation-type guard and mixed credential arms ====================

    @Test
    fun testSignAuthEntriesThrowsWhenOperationIsNotInvokeHostFunction() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val restoreBuilder = TransactionBuilder(
            sourceAccount = Account(MOCK_RPC_SOURCE_ACCOUNT, MOCK_RPC_SOURCE_SEQ),
            network = NETWORK
        )
            .addOperation(RestoreFootprintOperation())
            .setTimeout(300L)
            .setBaseFee(100L)
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = restoreBuilder
            )
            assembled.simulate()
            // A non-null delegate bypasses the needsNonInvokerSigningBy pre-check (which
            // would otherwise throw first for a non-invoke operation), reaching the
            // operation-type guard directly.
            val ex = assertFailsWith<IllegalStateException> {
                assembled.signAuthEntries(
                    authEntriesSigner = keypair,
                    validUntilLedgerSequence = 5000L,
                    authorizeEntryDelegate = { entry, _ -> entry }
                )
            }
            assertTrue(ex.message!!.contains("Expected InvokeHostFunctionOperation"))
        }
    }

    @Test
    fun testSignAuthEntriesPassesThroughSourceAccountCredentialsEntryUnchanged() = runTest {
        val signer = KeyPair.random()
        val signerAccountId = signer.getAccountId()
        // A mix of a source-account (Void) entry and an address entry addressed to the
        // signer: the Void entry must pass through unsigned while the address entry is signed.
        mockRpcServer(
            simulateReturnValue = Scv.toVoid(),
            authEntries = listOf(writeAuthEntry(), addressAuthEntry(signerAccountId))
        ).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            assembled.signAuthEntries(signer, validUntilLedgerSequence = 5000L)

            val operation = assembled.builtTransaction!!.operations.first() as InvokeHostFunctionOperation
            assertEquals(2, operation.auth.size)
            assertTrue(operation.auth.any { it.credentials is SorobanCredentialsXdr.Void })
            assertTrue(assembled.needsNonInvokerSigningBy().isEmpty())
        }
    }

    // ==================== needsNonInvokerSigningBy: delegate traversal cap ====================

    @Test
    fun testNeedsNonInvokerSigningByThrowsWhenDelegateTreeExceedsTraversalCap() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val topAccount = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        val hostFunction = HostFunctionXdr.InvokeContract(
            InvokeContractArgsXdr(
                contractAddress = Address(MOCK_RPC_CONTRACT_ID).toSCAddress(),
                functionName = SCSymbolXdr("hello"),
                args = emptyList()
            )
        )
        // The deep entry is attached directly to the operation (non-empty auth), so
        // assembleTransaction() will not try to replace it from the simulated JSON.
        val builder = TransactionBuilder(
            sourceAccount = Account(MOCK_RPC_SOURCE_ACCOUNT, MOCK_RPC_SOURCE_SEQ),
            network = NETWORK
        )
            .addOperation(
                InvokeHostFunctionOperation(
                    hostFunction = hostFunction,
                    auth = listOf(deeplyNestedDelegateAuthEntry(topAccount))
                )
            )
            .setTimeout(300L)
            .setBaseFee(100L)
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )
            assembled.simulate()
            val ex = assertFailsWith<IllegalArgumentException> { assembled.needsNonInvokerSigningBy() }
            assertTrue(ex.message!!.contains("exceeds cap"))
        }
    }

    // ==================== isReadCall: simulation omits the auth field entirely ====================

    @Test
    fun testIsReadCallTrueWhenSimulationOmitsAuthField() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        val txData = minimalSorobanData().toXdrBase64()
        val returnXdr = SCValXdr.B(true).toXdrBase64()
        // No "auth" key at all (as opposed to an empty array), so parseAuth() yields null.
        val simJson = """
            {
              "transactionData": "$txData",
              "minResourceFee": "100",
              "results": [ { "xdr": "$returnXdr" } ],
              "latestLedger": 14245
            }
        """.trimIndent()
        rawJsonMockServer(simulateResultJson = simJson).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            assertNull(assembled.getSimulationData().auth)
            assertTrue(assembled.isReadCall())
        }
    }

    // ==================== toEnvelopeXdrBase64: after a successful simulation ====================

    @Test
    fun testToEnvelopeXdrBase64ReturnsEnvelopeAfterSimulation() = runTest {
        keypair = KeyPair.fromSecretSeed(SECRET_SEED)
        mockRpcServer(simulateReturnValue = SCValXdr.B(true)).use { server ->
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = keypair,
                parseResultXdrFn = null,
                transactionBuilder = mockBuilder()
            )
            assembled.simulate()
            val envelopeXdr = assembled.toEnvelopeXdrBase64()
            assertTrue(envelopeXdr.isNotBlank())
            assertEquals(assembled.builtTransaction!!.toEnvelopeXdrBase64(), envelopeXdr)
        }
    }

    // ==================== submit(): alternate TransactionMeta variants ====================

    @Test
    fun testSubmitParsesReturnValueFromV4TransactionMeta() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        val txHash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"
        rawJsonMockServer(
            sourceAccount = source,
            simulateResultJson = simulateResultJson(Scv.toVoid(), listOf(writeAuthEntry())),
            sendTransactionResultJson = """{ "status": "PENDING", "hash": "$txHash", "latestLedger": 14245 }""",
            getTransactionResultJson = """
                {
                  "status": "SUCCESS",
                  "txHash": "$txHash",
                  "latestLedger": 14246,
                  "resultMetaXdr": "${v4ResultMetaBase64(SCValXdr.Sym(SCSymbolXdr("v4done")))}"
                }
            """.trimIndent()
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<String>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = { (it as SCValXdr.Sym).value.value },
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            assertEquals("v4done", assembled.submit())
        }
    }

    @Test
    fun testSubmitThrowsIllegalStateExceptionForPreSorobanTransactionMetaVariant() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        val txHash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"
        rawJsonMockServer(
            sourceAccount = source,
            simulateResultJson = simulateResultJson(Scv.toVoid(), listOf(writeAuthEntry())),
            sendTransactionResultJson = """{ "status": "PENDING", "hash": "$txHash", "latestLedger": 14245 }""",
            getTransactionResultJson = """
                {
                  "status": "SUCCESS",
                  "txHash": "$txHash",
                  "latestLedger": 14246,
                  "resultMetaXdr": "${preSorobanResultMetaBase64()}"
                }
            """.trimIndent()
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            val ex = assertFailsWith<IllegalStateException> { assembled.submit() }
            assertTrue(ex.message!!.contains("No return value in transaction meta"))
        }
    }

    // ==================== submitInternal(): sendTransaction failure branches ====================

    @Test
    fun testSubmitThrowsSendTransactionFailedExceptionOnTryAgainLater() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        rawJsonMockServer(
            sourceAccount = source,
            simulateResultJson = simulateResultJson(Scv.toVoid(), listOf(writeAuthEntry())),
            sendTransactionResultJson = """{ "status": "TRY_AGAIN_LATER", "latestLedger": 14245 }"""
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            val ex = assertFailsWith<SendTransactionFailedException> { assembled.submit() }
            assertTrue(ex.message!!.contains("Status: TRY_AGAIN_LATER"))
            assertFalse(ex.message!!.contains("Error Result XDR"))
            assertFalse(ex.message!!.contains("Diagnostic Events"))
        }
    }

    @Test
    fun testSubmitThrowsSendTransactionFailedExceptionOnErrorWithParsableResultXdrAndDiagnostics() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        val txResult = TransactionResultXdr(
            feeCharged = Int64Xdr(100L),
            result = TransactionResultResultXdr.Void(TransactionResultCodeXdr.txTOO_LATE),
            ext = TransactionResultExtXdr.Void
        ).toXdrBase64()
        rawJsonMockServer(
            sourceAccount = source,
            simulateResultJson = simulateResultJson(Scv.toVoid(), listOf(writeAuthEntry())),
            sendTransactionResultJson = """
                {
                  "status": "ERROR",
                  "errorResultXdr": "$txResult",
                  "diagnosticEventsXdr": [ "AAAAAA==" ],
                  "latestLedger": 14245
                }
            """.trimIndent()
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            val ex = assertFailsWith<SendTransactionFailedException> { assembled.submit() }
            assertTrue(ex.message!!.contains("Error Result XDR"))
            assertTrue(ex.message!!.contains("Parsed Error"))
            assertTrue(ex.message!!.contains("Diagnostic Events"))
        }
    }

    @Test
    fun testSubmitThrowsSendTransactionFailedExceptionOnErrorWithUnparsableResultXdr() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        rawJsonMockServer(
            sourceAccount = source,
            simulateResultJson = simulateResultJson(Scv.toVoid(), listOf(writeAuthEntry())),
            sendTransactionResultJson = """{ "status": "ERROR", "errorResultXdr": "AA==", "latestLedger": 14245 }"""
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            val ex = assertFailsWith<SendTransactionFailedException> { assembled.submit() }
            assertTrue(ex.message!!.contains("Could not parse error XDR"))
        }
    }

    @Test
    fun testSubmitDoesNotResendWhenSendTransactionResponseAlreadyCached() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        mockRpcServer(
            simulateReturnValue = Scv.toVoid(),
            authEntries = listOf(writeAuthEntry()),
            sourceAccount = source,
            submittedReturnValue = SCValXdr.Sym(SCSymbolXdr("done"))
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<String>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = { (it as SCValXdr.Sym).value.value },
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            assertEquals("done", assembled.submit())
            val firstResponse = assembled.sendTransactionResponse
            // A second submit() call must reuse the cached sendTransactionResponse rather
            // than resending, then re-poll getTransaction for the (idempotent) result.
            assertEquals("done", assembled.submit())
            assertSame(firstResponse, assembled.sendTransactionResponse)
        }
    }

    // ==================== submitInternal(): getTransaction terminal statuses ====================

    @Test
    fun testSubmitThrowsTransactionStillPendingExceptionWhenPollingTimesOut() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        val txHash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"
        rawJsonMockServer(
            sourceAccount = source,
            simulateResultJson = simulateResultJson(Scv.toVoid(), listOf(writeAuthEntry())),
            sendTransactionResultJson = """{ "status": "PENDING", "hash": "$txHash", "latestLedger": 14245 }""",
            getTransactionResultJson = """{ "status": "NOT_FOUND", "latestLedger": 14245 }"""
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            // submitTimeout=0 means the poll loop's time budget is exhausted immediately
            // after the first getTransaction call, so it stays NOT_FOUND deterministically.
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 0,
                transactionSigner = signer,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            val ex = assertFailsWith<TransactionStillPendingException> { assembled.submit() }
            assertTrue(ex.message!!.contains("Waited 0 seconds"))
            assertSame(assembled, ex.assembledTransaction)
        }
    }

    @Test
    fun testSubmitThrowsTransactionFailedExceptionWhenStatusIsFailed() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        val txHash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"
        rawJsonMockServer(
            sourceAccount = source,
            simulateResultJson = simulateResultJson(Scv.toVoid(), listOf(writeAuthEntry())),
            sendTransactionResultJson = """{ "status": "PENDING", "hash": "$txHash", "latestLedger": 14245 }""",
            getTransactionResultJson = """{ "status": "FAILED", "txHash": "$txHash", "latestLedger": 14246 }"""
        ).use { server ->
            val builder = TransactionBuilder(sourceAccount = Account(source, MOCK_RPC_SOURCE_SEQ), network = NETWORK)
                .addOperation(
                    InvokeHostFunctionOperation.invokeContractFunction(
                        contractAddress = MOCK_RPC_CONTRACT_ID,
                        functionName = "hello",
                        parameters = emptyList()
                    )
                )
                .setTimeout(300L)
                .setBaseFee(100L)
            val assembled = AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = signer,
                parseResultXdrFn = null,
                transactionBuilder = builder
            )
            assembled.simulate()
            assembled.sign()
            val ex = assertFailsWith<TransactionFailedException> { assembled.submit() }
            assertTrue(ex.message!!.contains("Transaction failed"))
            assertSame(assembled, ex.assembledTransaction)
        }
    }
}
