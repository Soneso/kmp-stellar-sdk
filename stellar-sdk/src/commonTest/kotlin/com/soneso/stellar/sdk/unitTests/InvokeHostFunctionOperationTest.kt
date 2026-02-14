package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.*

/**
 * Comprehensive unit tests for InvokeHostFunctionOperation.invokeContractFunction() builder.
 *
 * Tests the builder method that simplifies contract function invocation by constructing
 * the proper XDR structures from high-level parameters.
 */
class InvokeHostFunctionOperationTest {
    companion object {
        const val CONTRACT_ID = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"
        const val CONTRACT_ID_2 = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"
        const val ACCOUNT_ID = "GADBBY4WFXKKFJ7CMTG3J5YAUXMQDBILRQ6W3U5IWN5TQFZU4MWZ5T4K"
    }

    // ============================================================================
    // Basic Construction
    // ============================================================================

    @Test
    fun testBuilderCreatesOperationWithCorrectContractAddress() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "transfer",
            parameters = emptyList()
        )

        // Verify operation was created
        assertNotNull(operation)

        // Extract contract address from host function
        val hostFunction = operation.hostFunction
        assertTrue(hostFunction is HostFunctionXdr.InvokeContract)

        val invokeArgs = hostFunction.value
        val address = Address.fromSCAddress(invokeArgs.contractAddress)
        assertEquals(CONTRACT_ID, address.toString())
    }

    @Test
    fun testBuilderCreatesOperationWithCorrectFunctionName() {
        val functionName = "transfer"
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = functionName,
            parameters = emptyList()
        )

        // Extract function name from host function
        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(functionName, invokeArgs.functionName.value)
    }

    @Test
    fun testBuilderCreatesOperationWithCorrectParameters() {
        val params = listOf(
            Scv.toInt32(42),
            Scv.toString("hello"),
            Scv.toBoolean(true)
        )

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = params
        )

        // Extract parameters from host function
        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(params.size, invokeArgs.args.size)
        assertEquals(params, invokeArgs.args)
    }

    @Test
    fun testBuilderCreatesOperationWithEmptyAuthList() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = emptyList()
        )

        assertTrue(operation.auth.isEmpty())
    }

    // ============================================================================
    // Parameter Variations
    // ============================================================================

    @Test
    fun testEmptyParametersList() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "no_args",
            parameters = emptyList()
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertTrue(invokeArgs.args.isEmpty())
    }

    @Test
    fun testSingleParameterInt32() {
        val param = Scv.toInt32(12345)
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "set_value",
            parameters = listOf(param)
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(1, invokeArgs.args.size)
        assertEquals(param, invokeArgs.args[0])
        assertEquals(12345, Scv.fromInt32(invokeArgs.args[0]))
    }

    @Test
    fun testSingleParameterInt64() {
        val param = Scv.toInt64(9876543210L)
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "set_timestamp",
            parameters = listOf(param)
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(1, invokeArgs.args.size)
        assertEquals(9876543210L, Scv.fromInt64(invokeArgs.args[0]))
    }

    @Test
    fun testSingleParameterInt128() {
        val bigValue = BigInteger.parseString("123456789012345678901234567890")
        val param = Scv.toInt128(bigValue)
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "set_big_value",
            parameters = listOf(param)
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(1, invokeArgs.args.size)
        assertEquals(bigValue, Scv.fromInt128(invokeArgs.args[0]))
    }

    @Test
    fun testSingleParameterString() {
        val param = Scv.toString("Hello, Stellar!")
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "set_message",
            parameters = listOf(param)
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(1, invokeArgs.args.size)
        assertEquals("Hello, Stellar!", Scv.fromString(invokeArgs.args[0]))
    }

    @Test
    fun testSingleParameterSymbol() {
        val param = Scv.toSymbol("token_id")
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "get_info",
            parameters = listOf(param)
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(1, invokeArgs.args.size)
        assertEquals("token_id", Scv.fromSymbol(invokeArgs.args[0]))
    }

    @Test
    fun testSingleParameterBoolean() {
        val param = Scv.toBoolean(true)
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "set_flag",
            parameters = listOf(param)
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(1, invokeArgs.args.size)
        assertEquals(true, Scv.fromBoolean(invokeArgs.args[0]))
    }

    @Test
    fun testSingleParameterAddress() {
        val address = Address(ACCOUNT_ID)
        val param = Scv.toAddress(address.toSCAddress())
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "set_admin",
            parameters = listOf(param)
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(1, invokeArgs.args.size)
        val resultAddress = Address.fromSCAddress(Scv.fromAddress(invokeArgs.args[0]))
        assertEquals(ACCOUNT_ID, resultAddress.toString())
    }

    @Test
    fun testMultipleParametersMixedTypes() {
        val params = listOf(
            Scv.toAddress(Address(ACCOUNT_ID).toSCAddress()),
            Scv.toInt128(BigInteger.parseString("1000000000")),
            Scv.toSymbol("USD"),
            Scv.toBoolean(true)
        )

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "transfer",
            parameters = params
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(4, invokeArgs.args.size)

        // Verify each parameter
        val resultAddress = Address.fromSCAddress(Scv.fromAddress(invokeArgs.args[0]))
        assertEquals(ACCOUNT_ID, resultAddress.toString())
        assertEquals(BigInteger.parseString("1000000000"), Scv.fromInt128(invokeArgs.args[1]))
        assertEquals("USD", Scv.fromSymbol(invokeArgs.args[2]))
        assertEquals(true, Scv.fromBoolean(invokeArgs.args[3]))
    }

    @Test
    fun testLargeParameterList() {
        // Create 15 parameters
        val params = (1..15).map { Scv.toInt32(it) }

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "many_args",
            parameters = params
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(15, invokeArgs.args.size)

        // Verify all parameters
        for (i in 1..15) {
            assertEquals(i, Scv.fromInt32(invokeArgs.args[i - 1]))
        }
    }

    @Test
    fun testComplexNestedParametersVec() {
        val innerVec = Scv.toVec(listOf(
            Scv.toInt32(1),
            Scv.toInt32(2),
            Scv.toInt32(3)
        ))

        val params = listOf(innerVec)

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "process_vec",
            parameters = params
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(1, invokeArgs.args.size)

        val resultVec = Scv.fromVec(invokeArgs.args[0])
        assertEquals(3, resultVec.size)
        assertEquals(1, Scv.fromInt32(resultVec[0]))
        assertEquals(2, Scv.fromInt32(resultVec[1]))
        assertEquals(3, Scv.fromInt32(resultVec[2]))
    }

    @Test
    fun testComplexNestedParametersMap() {
        val mapEntries = linkedMapOf(
            Scv.toSymbol("key1") to Scv.toInt32(100),
            Scv.toSymbol("key2") to Scv.toInt32(200)
        )
        val mapParam = Scv.toMap(mapEntries)

        val params = listOf(mapParam)

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "process_map",
            parameters = params
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(1, invokeArgs.args.size)

        val resultMap = Scv.fromMap(invokeArgs.args[0])
        assertEquals(2, resultMap.size)
    }

    // ============================================================================
    // Contract Address Validation
    // ============================================================================

    @Test
    fun testValidContractAddress() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = emptyList()
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        // Verify address type is CONTRACT
        val scAddress = invokeArgs.contractAddress
        assertTrue(scAddress is SCAddressXdr.ContractId)
    }

    @Test
    fun testContractAddressToSCAddressConversion() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = emptyList()
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        // Verify round-trip conversion
        val address = Address.fromSCAddress(invokeArgs.contractAddress)
        assertEquals(CONTRACT_ID, address.toString())
    }

    @Test
    fun testDifferentContractAddressesProduceDifferentOperations() {
        val operation1 = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = emptyList()
        )

        val operation2 = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID_2,
            functionName = "test",
            parameters = emptyList()
        )

        val hostFunction1 = operation1.hostFunction as HostFunctionXdr.InvokeContract
        val hostFunction2 = operation2.hostFunction as HostFunctionXdr.InvokeContract

        val address1 = Address.fromSCAddress(hostFunction1.value.contractAddress)
        val address2 = Address.fromSCAddress(hostFunction2.value.contractAddress)

        assertNotEquals(address1.toString(), address2.toString())
    }

    @Test
    fun testInvalidContractAddressThrowsException() {
        val exception = assertFailsWith<IllegalArgumentException> {
            InvokeHostFunctionOperation.invokeContractFunction(
                contractAddress = "INVALID_ADDRESS",
                functionName = "test",
                parameters = emptyList()
            )
        }

        assertEquals("Unsupported address type", exception.message)
    }

    @Test
    fun testAccountAddressAcceptedButDifferentType() {
        // Account addresses (G...) can be used, but they create AccountId SCAddress type
        // This is technically allowed by the XDR but semantically you'd normally use contract addresses
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = ACCOUNT_ID,
            functionName = "test",
            parameters = emptyList()
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        // The Address constructor accepts account IDs and converts to SCAddress.AccountId
        assertTrue(invokeArgs.contractAddress is SCAddressXdr.AccountId)
    }

    // ============================================================================
    // Function Name Validation
    // ============================================================================

    @Test
    fun testShortFunctionName() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "f",
            parameters = emptyList()
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals("f", invokeArgs.functionName.value)
    }

    @Test
    fun testLongFunctionName() {
        // Stellar symbols can be up to 32 characters (not 63 as initially thought)
        // Using a reasonable 32-character function name
        val longName = "a".repeat(32)
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = longName,
            parameters = emptyList()
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(longName, invokeArgs.functionName.value)
    }

    @Test
    fun testCommonFunctionNames() {
        val commonNames = listOf(
            "balance",
            "transfer",
            "approve",
            "allowance",
            "mint",
            "burn",
            "initialize",
            "upgrade"
        )

        for (name in commonNames) {
            val operation = InvokeHostFunctionOperation.invokeContractFunction(
                contractAddress = CONTRACT_ID,
                functionName = name,
                parameters = emptyList()
            )

            val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
            val invokeArgs = hostFunction.value

            assertEquals(name, invokeArgs.functionName.value)
        }
    }

    @Test
    fun testFunctionNameConvertedToSCSymbol() {
        val functionName = "my_function"
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = functionName,
            parameters = emptyList()
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        // Verify it's an SCSymbolXdr
        assertTrue(invokeArgs.functionName is SCSymbolXdr)
        assertEquals(functionName, invokeArgs.functionName.value)
    }

    // ============================================================================
    // Operation Properties
    // ============================================================================

    @Test
    fun testOperationTypeIsCorrect() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = emptyList()
        )

        assertTrue(operation is InvokeHostFunctionOperation)
    }

    @Test
    fun testHostFunctionTypeIsInvokeContract() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = emptyList()
        )

        assertTrue(operation.hostFunction is HostFunctionXdr.InvokeContract)
        assertEquals(HostFunctionTypeXdr.HOST_FUNCTION_TYPE_INVOKE_CONTRACT, operation.hostFunction.discriminant)
    }

    @Test
    fun testAuthListIsEmpty() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = listOf(Scv.toInt32(42))
        )

        assertTrue(operation.auth.isEmpty())
    }

    @Test
    fun testSourceAccountHandlingNotSet() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = emptyList()
        )

        // Source account should be null by default (inherited from Operation base)
        assertNull(operation.sourceAccount)
    }

    @Test
    fun testSourceAccountHandlingCanBeSet() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = emptyList()
        )

        // Source account can be set after creation
        val sourceAccount = ACCOUNT_ID
        operation.sourceAccount = sourceAccount

        assertEquals(sourceAccount, operation.sourceAccount)
    }

    // ============================================================================
    // XDR Structure
    // ============================================================================

    @Test
    fun testCorrectInvokeContractArgsXdrStructure() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "transfer",
            parameters = listOf(Scv.toInt32(100))
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        // Verify InvokeContractArgsXdr structure
        assertNotNull(invokeArgs.contractAddress)
        assertNotNull(invokeArgs.functionName)
        assertNotNull(invokeArgs.args)

        assertTrue(invokeArgs.contractAddress is SCAddressXdr)
        assertTrue(invokeArgs.functionName is SCSymbolXdr)
        assertEquals(1, invokeArgs.args.size)
    }

    @Test
    fun testCorrectHostFunctionXdrStructure() {
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = emptyList()
        )

        val hostFunction = operation.hostFunction

        // Verify HostFunctionXdr structure
        assertTrue(hostFunction is HostFunctionXdr.InvokeContract)
        assertEquals(HostFunctionTypeXdr.HOST_FUNCTION_TYPE_INVOKE_CONTRACT, hostFunction.discriminant)

        val invokeArgs = hostFunction.value
        assertTrue(invokeArgs is InvokeContractArgsXdr)
    }

    @Test
    fun testParametersCorrectlyStoredInArgs() {
        val params = listOf(
            Scv.toInt32(1),
            Scv.toInt64(2L),
            Scv.toString("test")
        )

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "multi_param",
            parameters = params
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        // Verify all parameters are in args
        assertEquals(params.size, invokeArgs.args.size)
        for (i in params.indices) {
            assertEquals(params[i], invokeArgs.args[i])
        }
    }

    @Test
    fun testSerializationDeserializationRoundtrip() {
        val params = listOf(
            Scv.toInt32(42),
            Scv.toString("hello")
        )

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = params
        )

        // Convert to XDR
        val operationBody = operation.toOperationBody()

        // Verify it's the correct operation type
        assertTrue(operationBody is OperationBodyXdr.InvokeHostFunctionOp)

        val invokeOp = operationBody.value

        // Deserialize back
        val deserialized = InvokeHostFunctionOperation.fromXdr(invokeOp)

        // Verify deserialized operation matches original
        assertEquals(operation.auth.size, deserialized.auth.size)

        val originalHostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val deserializedHostFunction = deserialized.hostFunction as HostFunctionXdr.InvokeContract

        val originalArgs = originalHostFunction.value
        val deserializedArgs = deserializedHostFunction.value

        // Verify contract address
        val originalAddress = Address.fromSCAddress(originalArgs.contractAddress)
        val deserializedAddress = Address.fromSCAddress(deserializedArgs.contractAddress)
        assertEquals(originalAddress.toString(), deserializedAddress.toString())

        // Verify function name
        assertEquals(originalArgs.functionName.value, deserializedArgs.functionName.value)

        // Verify parameters
        assertEquals(originalArgs.args.size, deserializedArgs.args.size)
        for (i in originalArgs.args.indices) {
            assertEquals(originalArgs.args[i], deserializedArgs.args[i])
        }
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    fun testVeryLongParameterList() {
        // Create 50 parameters
        val params = (1..50).map { Scv.toInt32(it) }

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "many_params",
            parameters = params
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(50, invokeArgs.args.size)

        // Spot check some parameters
        assertEquals(1, Scv.fromInt32(invokeArgs.args[0]))
        assertEquals(25, Scv.fromInt32(invokeArgs.args[24]))
        assertEquals(50, Scv.fromInt32(invokeArgs.args[49]))
    }

    @Test
    fun testEmptyStringParameter() {
        val params = listOf(Scv.toString(""))

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = params
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals("", Scv.fromString(invokeArgs.args[0]))
    }

    @Test
    fun testZeroValueParameters() {
        val params = listOf(
            Scv.toInt32(0),
            Scv.toInt64(0L),
            Scv.toInt128(BigInteger.ZERO)
        )

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test_zeros",
            parameters = params
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(0, Scv.fromInt32(invokeArgs.args[0]))
        assertEquals(0L, Scv.fromInt64(invokeArgs.args[1]))
        assertEquals(BigInteger.ZERO, Scv.fromInt128(invokeArgs.args[2]))
    }

    @Test
    fun testNegativeValueParameters() {
        val params = listOf(
            Scv.toInt32(-42),
            Scv.toInt64(-9876543210L),
            Scv.toInt128(BigInteger.parseString("-123456789"))
        )

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test_negatives",
            parameters = params
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(-42, Scv.fromInt32(invokeArgs.args[0]))
        assertEquals(-9876543210L, Scv.fromInt64(invokeArgs.args[1]))
        assertEquals(BigInteger.parseString("-123456789"), Scv.fromInt128(invokeArgs.args[2]))
    }

    @Test
    fun testDeeplyNestedParameters() {
        // Create a Vec of Vecs
        val innerVec1 = Scv.toVec(listOf(Scv.toInt32(1), Scv.toInt32(2)))
        val innerVec2 = Scv.toVec(listOf(Scv.toInt32(3), Scv.toInt32(4)))
        val outerVec = Scv.toVec(listOf(innerVec1, innerVec2))

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "nested_test",
            parameters = listOf(outerVec)
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(1, invokeArgs.args.size)

        // Verify nested structure
        val resultOuterVec = Scv.fromVec(invokeArgs.args[0])
        assertEquals(2, resultOuterVec.size)

        val resultInnerVec1 = Scv.fromVec(resultOuterVec[0])
        assertEquals(2, resultInnerVec1.size)
        assertEquals(1, Scv.fromInt32(resultInnerVec1[0]))
        assertEquals(2, Scv.fromInt32(resultInnerVec1[1]))

        val resultInnerVec2 = Scv.fromVec(resultOuterVec[1])
        assertEquals(2, resultInnerVec2.size)
        assertEquals(3, Scv.fromInt32(resultInnerVec2[0]))
        assertEquals(4, Scv.fromInt32(resultInnerVec2[1]))
    }

    @Test
    fun testSpecialCharactersInStringParameter() {
        val specialString = "Hello\nWorld\t!\r\n"
        val params = listOf(Scv.toString(specialString))

        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "test",
            parameters = params
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(specialString, Scv.fromString(invokeArgs.args[0]))
    }

    @Test
    fun testFunctionNameWithUnderscores() {
        val functionName = "my_super_long_function_name"
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = functionName,
            parameters = emptyList()
        )

        val hostFunction = operation.hostFunction as HostFunctionXdr.InvokeContract
        val invokeArgs = hostFunction.value

        assertEquals(functionName, invokeArgs.functionName.value)
    }

    @Test
    fun testMultipleOperationsAreIndependent() {
        val operation1 = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "func1",
            parameters = listOf(Scv.toInt32(1))
        )

        val operation2 = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = CONTRACT_ID,
            functionName = "func2",
            parameters = listOf(Scv.toInt32(2))
        )

        // Verify operations don't interfere with each other
        val hostFunction1 = operation1.hostFunction as HostFunctionXdr.InvokeContract
        val hostFunction2 = operation2.hostFunction as HostFunctionXdr.InvokeContract

        assertEquals("func1", hostFunction1.value.functionName.value)
        assertEquals("func2", hostFunction2.value.functionName.value)

        assertEquals(1, Scv.fromInt32(hostFunction1.value.args[0]))
        assertEquals(2, Scv.fromInt32(hostFunction2.value.args[0]))
    }
}
