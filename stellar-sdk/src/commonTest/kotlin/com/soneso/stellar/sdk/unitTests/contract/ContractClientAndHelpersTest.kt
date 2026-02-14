package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.contract.*
import com.soneso.stellar.sdk.xdr.*
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
}
