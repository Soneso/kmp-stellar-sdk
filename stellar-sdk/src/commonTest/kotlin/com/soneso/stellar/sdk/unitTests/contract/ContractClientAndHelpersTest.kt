package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.*
import com.soneso.stellar.sdk.contract.exception.ContractSpecException
import com.soneso.stellar.sdk.contract.exception.SendTransactionFailedException
import com.soneso.stellar.sdk.rpc.responses.SendTransactionResponse
import com.soneso.stellar.sdk.rpc.responses.SendTransactionStatus
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for ContractClient helpers, NativeUnionVal, and SimulateHostFunctionResult.
 */
class ContractClientAndHelpersTest {

    // ==================== NativeUnionVal ====================

    @Test
    fun testNativeUnionValVoidCase() {
        val vc = NativeUnionVal.VoidCase("Success")
        assertEquals("Success", vc.tag)
        assertTrue(vc.isVoidCase)
        assertFalse(vc.isTupleCase)
    }

    @Test
    fun testNativeUnionValTupleCase() {
        val tc = NativeUnionVal.TupleCase("Data", listOf("field1", 42))
        assertEquals("Data", tc.tag)
        assertFalse(tc.isVoidCase)
        assertTrue(tc.isTupleCase)
        assertEquals(2, tc.values.size)
        assertEquals("field1", tc.values[0])
        assertEquals(42, tc.values[1])
    }

    @Test
    fun testNativeUnionValVoidCaseEquality() {
        val vc1 = NativeUnionVal.VoidCase("Success")
        val vc2 = NativeUnionVal.VoidCase("Success")
        val vc3 = NativeUnionVal.VoidCase("Error")
        assertEquals(vc1, vc2)
        assertEquals(vc1.hashCode(), vc2.hashCode())
        assertNotEquals(vc1, vc3)
    }

    @Test
    fun testNativeUnionValTupleCaseEquality() {
        val tc1 = NativeUnionVal.TupleCase("Data", listOf("a"))
        val tc2 = NativeUnionVal.TupleCase("Data", listOf("a"))
        val tc3 = NativeUnionVal.TupleCase("Data", listOf("b"))
        assertEquals(tc1, tc2)
        assertNotEquals(tc1, tc3)
    }

    @Test
    fun testNativeUnionValTupleCaseEmptyValues() {
        val tc = NativeUnionVal.TupleCase("Empty", emptyList())
        assertEquals("Empty", tc.tag)
        assertTrue(tc.values.isEmpty())
    }

    @Test
    fun testNativeUnionValTupleCaseNullValues() {
        val tc = NativeUnionVal.TupleCase("Nullable", listOf(null, "data", null))
        assertEquals(3, tc.values.size)
        assertNull(tc.values[0])
        assertEquals("data", tc.values[1])
    }

    @Test
    fun testNativeUnionValVoidAndTupleDifferentTypes() {
        val vc: NativeUnionVal = NativeUnionVal.VoidCase("Tag")
        val tc: NativeUnionVal = NativeUnionVal.TupleCase("Tag", listOf("val"))
        assertNotEquals(vc, tc)
    }

    // ==================== SimulateHostFunctionResult ====================

    private fun createTestTxData(): SorobanTransactionDataXdr {
        return SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(
                    readOnly = emptyList(),
                    readWrite = emptyList()
                ),
                instructions = Uint32Xdr(0u),
                diskReadBytes = Uint32Xdr(0u),
                writeBytes = Uint32Xdr(0u)
            ),
            resourceFee = Int64Xdr(0)
        )
    }

    @Test
    fun testSimulateHostFunctionResultConstruction() {
        val authEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = SorobanAuthorizedInvocationXdr(
                function = SorobanAuthorizedFunctionXdr.ContractFn(
                    InvokeContractArgsXdr(
                        contractAddress = SCAddressXdr.ContractId(ContractIDXdr(HashXdr(ByteArray(32)))),
                        functionName = SCSymbolXdr("test"),
                        args = emptyList()
                    )
                ),
                subInvocations = emptyList()
            )
        )

        val txData = createTestTxData()
        val returnValue = SCValXdr.B(true)

        val result = SimulateHostFunctionResult(
            auth = listOf(authEntry),
            transactionData = txData,
            returnedValue = returnValue
        )

        assertEquals(1, result.auth?.size)
        assertNotNull(result.transactionData)
        assertTrue(result.returnedValue is SCValXdr.B)
        assertEquals(true, (result.returnedValue as SCValXdr.B).value)
    }

    @Test
    fun testSimulateHostFunctionResultNullAuth() {
        val txData = createTestTxData()
        val voidVal = SCValXdr.Void(SCValTypeXdr.SCV_VOID)

        val result = SimulateHostFunctionResult(
            auth = null,
            transactionData = txData,
            returnedValue = voidVal
        )

        assertNull(result.auth)
        assertTrue(result.returnedValue is SCValXdr.Void)
    }

    @Test
    fun testSimulateHostFunctionResultEquality() {
        val txData = createTestTxData()
        val voidVal = SCValXdr.Void(SCValTypeXdr.SCV_VOID)

        val r1 = SimulateHostFunctionResult(null, txData, voidVal)
        val r2 = SimulateHostFunctionResult(null, txData, voidVal)
        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    // ==================== ClientOptions ====================

    @Test
    fun testClientOptionsDefaults() {
        val keypair = com.soneso.stellar.sdk.KeyPair.fromAccountId(
            "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        )
        val opts = ClientOptions(
            sourceAccountKeyPair = keypair,
            contractId = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK",
            network = com.soneso.stellar.sdk.Network.TESTNET,
            rpcUrl = "https://soroban-testnet.stellar.org"
        )
        assertEquals(300, opts.transactionTimeout)
        assertEquals(30, opts.submitTimeout)
        assertEquals(100, opts.baseFee)
        assertTrue(opts.simulate)
        assertTrue(opts.restore)
        assertTrue(opts.autoSubmit)
    }

    @Test
    fun testClientOptionsCustomValues() {
        val keypair = com.soneso.stellar.sdk.KeyPair.fromAccountId(
            "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
        )
        val opts = ClientOptions(
            sourceAccountKeyPair = keypair,
            contractId = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK",
            network = com.soneso.stellar.sdk.Network.TESTNET,
            rpcUrl = "https://soroban-testnet.stellar.org",
            transactionTimeout = 600,
            submitTimeout = 60,
            baseFee = 200,
            simulate = false,
            restore = false,
            autoSubmit = false
        )
        assertEquals(600, opts.transactionTimeout)
        assertEquals(60, opts.submitTimeout)
        assertEquals(200, opts.baseFee)
        assertFalse(opts.simulate)
        assertFalse(opts.restore)
        assertFalse(opts.autoSubmit)
    }

    // ==================== Spec-Required Methods: no spec loaded ====================

    @Test
    fun testFuncArgsToXdrSCValuesThrowsIllegalStateWithoutSpec() {
        val client = ContractClient.forContractWithoutSpec(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET)
        val ex = assertFailsWith<IllegalStateException> {
            client.funcArgsToXdrSCValues("hello", mapOf("to" to "Alice"))
        }
        assertTrue(ex.message!!.contains("funcArgsToXdrSCValues requires ContractSpec"))
        client.close()
    }

    @Test
    fun testNativeToXdrSCValThrowsIllegalStateWithoutSpec() {
        val client = ContractClient.forContractWithoutSpec(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET)
        val ex = assertFailsWith<IllegalStateException> {
            client.nativeToXdrSCVal("Alice", symbolTypeDef())
        }
        assertTrue(ex.message!!.contains("nativeToXdrSCVal requires ContractSpec"))
        client.close()
    }

    @Test
    fun testFuncResToNativeScValThrowsIllegalStateWithoutSpec() {
        val client = ContractClient.forContractWithoutSpec(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET)
        val ex = assertFailsWith<IllegalStateException> {
            client.funcResToNative("hello", SCValXdr.Sym(SCSymbolXdr("Alice")))
        }
        assertTrue(ex.message!!.contains("funcResToNative requires ContractSpec"))
        client.close()
    }

    @Test
    fun testFuncResToNativeBase64ThrowsIllegalStateWithoutSpec() {
        val client = ContractClient.forContractWithoutSpec(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET)
        val ex = assertFailsWith<IllegalStateException> {
            client.funcResToNative("hello", SCValXdr.Sym(SCSymbolXdr("Alice")).toXdrBase64())
        }
        assertTrue(ex.message!!.contains("funcResToNative requires ContractSpec"))
        client.close()
    }

    // ==================== Spec-Required Methods: spec loaded ====================
    //
    // Companions to the "without spec" guard tests above: these exercise the same
    // methods' success path, where the spec is present and the call delegates to it
    // rather than throwing. No network call is made by any of these methods.

    @Test
    fun testGetMethodNamesReturnsFunctionNamesWhenSpecLoaded() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            assertEquals(setOf("hello"), client.getMethodNames())
        }
    }

    @Test
    fun testFuncArgsToXdrSCValuesConvertsArgumentsWhenSpecLoaded() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val args = client.funcArgsToXdrSCValues("hello", mapOf("to" to "Alice"))
            assertEquals(1, args.size)
            assertTrue(args[0] is SCValXdr.Sym)
            assertEquals("Alice", (args[0] as SCValXdr.Sym).value.value)
        }
    }

    @Test
    fun testNativeToXdrSCValConvertsValueWhenSpecLoaded() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val scVal = client.nativeToXdrSCVal("Alice", symbolTypeDef())
            assertTrue(scVal is SCValXdr.Sym)
            assertEquals("Alice", (scVal as SCValXdr.Sym).value.value)
        }
    }

    @Test
    fun testFuncResToNativeScValConvertsResultWhenSpecLoaded() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val native = client.funcResToNative("hello", SCValXdr.Sym(SCSymbolXdr("Hello Alice")))
            assertEquals("Hello Alice", native)
        }
    }

    @Test
    fun testFuncResToNativeBase64ConvertsResultWhenSpecLoaded() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val native = client.funcResToNative("hello", SCValXdr.Sym(SCSymbolXdr("Hello Alice")).toXdrBase64())
            assertEquals("Hello Alice", native)
        }
    }

    // ==================== Map-based invoke/buildInvoke: method validation ====================

    @Test
    fun testInvokeMapUnknownMethodThrows() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val ex = assertFailsWith<IllegalArgumentException> {
                client.invoke<SCValXdr>(
                    functionName = "missing",
                    arguments = emptyMap(),
                    source = MOCK_RPC_SOURCE_ACCOUNT,
                    signer = null
                )
            }
            assertTrue(ex.message!!.contains("Method 'missing' not found in contract spec"))
            assertTrue(ex.message!!.contains("hello"))
        }
    }

    @Test
    fun testBuildInvokeMapUnknownMethodThrows() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val ex = assertFailsWith<IllegalArgumentException> {
                client.buildInvoke<SCValXdr>(
                    functionName = "missing",
                    arguments = emptyMap(),
                    source = MOCK_RPC_SOURCE_ACCOUNT,
                    signer = null
                )
            }
            assertTrue(ex.message!!.contains("Method 'missing' not found in contract spec"))
            assertTrue(ex.message!!.contains("hello"))
        }
    }

    // ==================== Map-based invoke/buildInvoke: argument conversion failure ====================

    @Test
    fun testInvokeMapArgumentConversionFailureWraps() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val ex = assertFailsWith<IllegalArgumentException> {
                client.invoke<SCValXdr>(
                    functionName = "hello",
                    arguments = emptyMap(), // missing required "to" argument
                    source = MOCK_RPC_SOURCE_ACCOUNT,
                    signer = null
                )
            }
            assertTrue(ex.message!!.contains("Failed to convert arguments for 'hello'"))
            assertNotNull(ex.cause)
            assertTrue(ex.cause is ContractSpecException)
        }
    }

    @Test
    fun testBuildInvokeMapArgumentConversionFailureWraps() = runTest {
        mockRpcServer(simulateReturnValue = Scv.toVoid()).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val ex = assertFailsWith<IllegalArgumentException> {
                client.buildInvoke<SCValXdr>(
                    functionName = "hello",
                    arguments = emptyMap(), // missing required "to" argument
                    source = MOCK_RPC_SOURCE_ACCOUNT,
                    signer = null
                )
            }
            assertTrue(ex.message!!.contains("Failed to convert arguments for 'hello'"))
            assertNotNull(ex.cause)
            assertTrue(ex.cause is ContractSpecException)
        }
    }

    // ==================== Map-based invoke/buildInvoke: successful delegation ====================

    @Test
    fun testInvokeMapDelegatesToPositionalOnSuccess() = runTest {
        mockRpcServer(simulateReturnValue = SCValXdr.Sym(SCSymbolXdr("Hello Alice"))).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val result: String = client.invoke(
                functionName = "hello",
                arguments = mapOf("to" to "Alice"),
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null,
                parseResultXdrFn = { (it as SCValXdr.Sym).value.value }
            )
            assertEquals("Hello Alice", result)
        }
    }

    @Test
    fun testBuildInvokeMapDelegatesToPositionalOnSuccess() = runTest {
        mockRpcServer(simulateReturnValue = SCValXdr.Sym(SCSymbolXdr("Hello Alice"))).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val tx = client.buildInvoke<String>(
                functionName = "hello",
                arguments = mapOf("to" to "Alice"),
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null,
                parseResultXdrFn = { (it as SCValXdr.Sym).value.value }
            )
            assertNotNull(tx.simulation)
            assertEquals("Hello Alice", tx.result())
        }
    }

    @Test
    fun testInvokeMapWriteCallSignsAndSubmits() = runTest {
        val signer = KeyPair.random()
        val source = signer.getAccountId()
        mockRpcServer(
            simulateReturnValue = Scv.toVoid(),
            authEntries = listOf(writeAuthEntry()),
            sourceAccount = source,
            submittedReturnValue = SCValXdr.Sym(SCSymbolXdr("Hello Bob"))
        ).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, helloSpec())
            val result: String = client.invoke(
                functionName = "hello",
                arguments = mapOf("to" to "Bob"),
                source = source,
                signer = signer,
                parseResultXdrFn = { (it as SCValXdr.Sym).value.value }
            )
            assertEquals("Hello Bob", result, "write call must return the value parsed from the submitted transaction meta")
        }
    }

    // ==================== Positional buildInvoke: default result parser ====================

    @Test
    fun testPositionalBuildInvokeDefaultParserReturnsRawScVal() = runTest {
        mockRpcServer(simulateReturnValue = SCValXdr.Sym(SCSymbolXdr("Hello Alice"))).use { server ->
            val client = ContractClient.forServer(MOCK_RPC_CONTRACT_ID, MOCK_RPC_SERVER_URL, Network.TESTNET, server, contractSpec = null)
            val tx = client.buildInvoke<SCValXdr>(
                functionName = "hello",
                parameters = listOf(Scv.toSymbol("Alice")),
                source = MOCK_RPC_SOURCE_ACCOUNT,
                signer = null
            )
            val result = tx.result()
            assertTrue(result is SCValXdr.Sym)
            assertEquals("Hello Alice", (result as SCValXdr.Sym).value.value)
        }
    }

    // ==================== Test fixtures ====================

    private fun symbolTypeDef(): SCSpecTypeDefXdr {
        val writer = XdrWriter()
        SCSpecTypeXdr.SC_SPEC_TYPE_SYMBOL.encode(writer)
        return SCSpecTypeDefXdr.decode(XdrReader(writer.toByteArray()))
    }

    /** A minimal contract spec with a single "hello(to: symbol) -> symbol" function. */
    private fun helloSpec(): ContractSpec {
        val entry = SCSpecEntryXdr.FunctionV0(
            SCSpecFunctionV0Xdr(
                doc = "",
                name = SCSymbolXdr("hello"),
                inputs = listOf(
                    SCSpecFunctionInputV0Xdr(doc = "", name = "to", type = symbolTypeDef())
                ),
                outputs = listOf(symbolTypeDef())
            )
        )
        return ContractSpec(listOf(entry))
    }

    // ==================== requirePollableSend ====================
    //
    // The deploy and install paths poll a transaction hash only after the network
    // accepted the submission. A response with any other status reports a submission
    // the network did not accept, so it must raise immediately instead of surfacing
    // as NOT_FOUND after the polling window.

    private fun insufficientFeeResultXdr(): String = TransactionResultXdr(
        feeCharged = Int64Xdr(100),
        result = TransactionResultResultXdr.Void(TransactionResultCodeXdr.txINSUFFICIENT_FEE),
        ext = TransactionResultExtXdr.Void
    ).toXdrBase64()

    @Test
    fun testRequirePollableSendAcceptsAPendingResponse() {
        val response = SendTransactionResponse(
            status = SendTransactionStatus.PENDING,
            hash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"
        )
        ContractClient.requirePollableSend(response)
    }

    @Test
    fun testRequirePollableSendRejectsAnErrorResponseNamingTheParsedResult() {
        val errorXdr = insufficientFeeResultXdr()
        val response = SendTransactionResponse(
            status = SendTransactionStatus.ERROR,
            hash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304",
            errorResultXdr = errorXdr,
            diagnosticEventsXdr = listOf("AAAA")
        )
        val exception = assertFailsWith<SendTransactionFailedException> {
            ContractClient.requirePollableSend(response)
        }
        val message = exception.message ?: fail("Exception should carry a message")
        assertTrue(message.contains("Status: ERROR"), "Message should name the status")
        assertTrue(message.contains("Error Result XDR: $errorXdr"), "Message should carry the error result XDR")
        assertTrue(message.contains("Parsed Error:"), "Message should carry the parsed result")
        assertTrue(message.contains("txINSUFFICIENT_FEE"), "Parsed result should name the failure code")
        assertTrue(message.contains("Diagnostic Events: AAAA"), "Message should carry the diagnostic events")
        assertNull(exception.assembledTransaction, "The deploy path submits without an AssembledTransaction")
    }

    @Test
    fun testRequirePollableSendRejectsATryAgainLaterResponse() {
        val response = SendTransactionResponse(
            status = SendTransactionStatus.TRY_AGAIN_LATER,
            hash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"
        )
        val exception = assertFailsWith<SendTransactionFailedException> {
            ContractClient.requirePollableSend(response)
        }
        val message = exception.message ?: fail("Exception should carry a message")
        assertTrue(message.contains("Status: TRY_AGAIN_LATER"), "Message should name the status")
        assertFalse(message.contains("Error Result XDR"), "A response without an error result carries no XDR section")
    }

    @Test
    fun testRequirePollableSendAcceptsADuplicateResponse() {
        // A DUPLICATE response names a transaction the network already knows, so its hash
        // polls to the true outcome; a resubmitted deployment that already succeeded must
        // report that success rather than fail.
        val response = SendTransactionResponse(
            status = SendTransactionStatus.DUPLICATE,
            hash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304"
        )
        ContractClient.requirePollableSend(response)
    }

    @Test
    fun testRequirePollableSendReportsAnUnparsableErrorResult() {
        val response = SendTransactionResponse(
            status = SendTransactionStatus.ERROR,
            hash = "3389e9f0f1a7c19f0e9b8a1e9d2b3c4d5e6f70819293a4b5c6d7e8f901020304",
            errorResultXdr = "not a base64 transaction result"
        )
        val exception = assertFailsWith<SendTransactionFailedException> {
            ContractClient.requirePollableSend(response)
        }
        val message = exception.message ?: fail("Exception should carry a message")
        assertTrue(message.contains("Status: ERROR"), "Message should name the status")
        assertTrue(message.contains("Could not parse error XDR"), "Message should report the unparsable XDR")
    }
}
