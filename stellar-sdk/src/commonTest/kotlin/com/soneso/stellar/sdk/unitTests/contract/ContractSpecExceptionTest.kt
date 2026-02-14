package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.contract.exception.ContractSpecException
import com.soneso.stellar.sdk.contract.exception.ContractException
import kotlin.test.*

/**
 * Comprehensive unit tests for ContractSpecException and its companion object factory methods.
 *
 * Tests cover:
 * - Constructor parameter handling
 * - Inheritance from ContractException
 * - Custom toString() implementation
 * - All companion object factory methods
 * - Property preservation across factory methods
 * - Exception type verification and casting
 * - Message formatting consistency
 */
class ContractSpecExceptionTest {

    companion object {
        const val TEST_MESSAGE = "Test exception message"
        const val TEST_FUNCTION_NAME = "testFunction"
        const val TEST_ARGUMENT_NAME = "amount"
        const val TEST_ENTRY_NAME = "TestEntry"
        const val ROOT_CAUSE_MESSAGE = "Root cause error"
    }

    private lateinit var rootCause: RuntimeException

    @BeforeTest
    fun setup() {
        rootCause = RuntimeException(ROOT_CAUSE_MESSAGE)
    }

    // ==================== Constructor Tests ====================

    @Test
    fun testContractSpecExceptionConstructorWithAllParameters() {
        val exception = ContractSpecException(
            message = TEST_MESSAGE,
            functionName = TEST_FUNCTION_NAME,
            argumentName = TEST_ARGUMENT_NAME,
            entryName = TEST_ENTRY_NAME,
            cause = rootCause
        )

        assertEquals(TEST_MESSAGE, exception.message)
        assertEquals(TEST_FUNCTION_NAME, exception.functionName)
        assertEquals(TEST_ARGUMENT_NAME, exception.argumentName)
        assertEquals(TEST_ENTRY_NAME, exception.entryName)
        assertSame(rootCause, exception.cause)
        assertNull(exception.assembledTransaction)
    }

    @Test
    fun testContractSpecExceptionConstructorWithMessageOnly() {
        val exception = ContractSpecException(message = TEST_MESSAGE)

        assertEquals(TEST_MESSAGE, exception.message)
        assertNull(exception.functionName)
        assertNull(exception.argumentName)
        assertNull(exception.entryName)
        assertNull(exception.cause)
        assertNull(exception.assembledTransaction)
    }

    @Test
    fun testContractSpecExceptionConstructorWithFunctionName() {
        val exception = ContractSpecException(
            message = TEST_MESSAGE,
            functionName = TEST_FUNCTION_NAME
        )

        assertEquals(TEST_MESSAGE, exception.message)
        assertEquals(TEST_FUNCTION_NAME, exception.functionName)
        assertNull(exception.argumentName)
        assertNull(exception.entryName)
        assertNull(exception.cause)
    }

    @Test
    fun testContractSpecExceptionConstructorWithArgumentName() {
        val exception = ContractSpecException(
            message = TEST_MESSAGE,
            argumentName = TEST_ARGUMENT_NAME
        )

        assertEquals(TEST_MESSAGE, exception.message)
        assertNull(exception.functionName)
        assertEquals(TEST_ARGUMENT_NAME, exception.argumentName)
        assertNull(exception.entryName)
        assertNull(exception.cause)
    }

    @Test
    fun testContractSpecExceptionConstructorWithEntryName() {
        val exception = ContractSpecException(
            message = TEST_MESSAGE,
            entryName = TEST_ENTRY_NAME
        )

        assertEquals(TEST_MESSAGE, exception.message)
        assertNull(exception.functionName)
        assertNull(exception.argumentName)
        assertEquals(TEST_ENTRY_NAME, exception.entryName)
        assertNull(exception.cause)
    }

    @Test
    fun testContractSpecExceptionConstructorWithCause() {
        val exception = ContractSpecException(
            message = TEST_MESSAGE,
            cause = rootCause
        )

        assertEquals(TEST_MESSAGE, exception.message)
        assertNull(exception.functionName)
        assertNull(exception.argumentName)
        assertNull(exception.entryName)
        assertSame(rootCause, exception.cause)
    }

    // ==================== Inheritance Tests ====================

    @Test
    fun testContractSpecExceptionInheritsFromContractException() {
        val exception = ContractSpecException(TEST_MESSAGE)

        assertTrue(exception is ContractException)
        assertTrue(exception is Exception)
        assertTrue(exception is Throwable)
    }

    @Test
    fun testContractSpecExceptionCanBeCaughtAsContractException() {
        val exception = ContractSpecException(TEST_MESSAGE, TEST_FUNCTION_NAME)

        val caught = assertFailsWith<ContractException> {
            throw exception
        }

        assertTrue(caught is ContractSpecException)
        assertEquals(TEST_MESSAGE, caught.message)
    }

    @Test
    fun testContractSpecExceptionCanBeCaughtAsSpecificType() {
        val exception = ContractSpecException(
            message = TEST_MESSAGE,
            functionName = TEST_FUNCTION_NAME,
            argumentName = TEST_ARGUMENT_NAME
        )

        val caught = assertFailsWith<ContractSpecException> {
            throw exception
        }

        assertEquals(TEST_MESSAGE, caught.message)
        assertEquals(TEST_FUNCTION_NAME, caught.functionName)
        assertEquals(TEST_ARGUMENT_NAME, caught.argumentName)
    }

    // ==================== toString() Implementation Tests ====================

    @Test
    fun testToStringWithMessageOnly() {
        val exception = ContractSpecException(TEST_MESSAGE)
        val result = exception.toString()

        assertTrue(result.contains("ContractSpecException:"))
        assertTrue(result.contains(TEST_MESSAGE))
        assertFalse(result.contains("function:"))
        assertFalse(result.contains("argument:"))
        assertFalse(result.contains("entry:"))
    }

    @Test
    fun testToStringWithFunctionName() {
        val exception = ContractSpecException(
            message = TEST_MESSAGE,
            functionName = TEST_FUNCTION_NAME
        )
        val result = exception.toString()

        assertTrue(result.contains("ContractSpecException:"))
        assertTrue(result.contains(TEST_MESSAGE))
        assertTrue(result.contains("(function: $TEST_FUNCTION_NAME)"))
    }

    @Test
    fun testToStringWithArgumentName() {
        val exception = ContractSpecException(
            message = TEST_MESSAGE,
            argumentName = TEST_ARGUMENT_NAME
        )
        val result = exception.toString()

        assertTrue(result.contains("ContractSpecException:"))
        assertTrue(result.contains(TEST_MESSAGE))
        assertTrue(result.contains("(argument: $TEST_ARGUMENT_NAME)"))
    }

    @Test
    fun testToStringWithEntryName() {
        val exception = ContractSpecException(
            message = TEST_MESSAGE,
            entryName = TEST_ENTRY_NAME
        )
        val result = exception.toString()

        assertTrue(result.contains("ContractSpecException:"))
        assertTrue(result.contains(TEST_MESSAGE))
        assertTrue(result.contains("(entry: $TEST_ENTRY_NAME)"))
    }

    @Test
    fun testToStringWithAllNames() {
        val exception = ContractSpecException(
            message = TEST_MESSAGE,
            functionName = TEST_FUNCTION_NAME,
            argumentName = TEST_ARGUMENT_NAME,
            entryName = TEST_ENTRY_NAME
        )
        val result = exception.toString()

        assertTrue(result.contains("ContractSpecException:"))
        assertTrue(result.contains(TEST_MESSAGE))
        assertTrue(result.contains("(function: $TEST_FUNCTION_NAME)"))
        assertTrue(result.contains("(argument: $TEST_ARGUMENT_NAME)"))
        assertTrue(result.contains("(entry: $TEST_ENTRY_NAME)"))
    }

    // ==================== Companion Object Factory Methods ====================

    @Test
    fun testFunctionNotFoundFactory() {
        val functionName = "transfer"
        val exception = ContractSpecException.functionNotFound(functionName)

        assertEquals("Function not found: $functionName", exception.message)
        assertEquals(functionName, exception.functionName)
        assertNull(exception.argumentName)
        assertNull(exception.entryName)
        assertNull(exception.cause)
    }

    @Test
    fun testArgumentNotFoundFactoryWithoutFunctionName() {
        val argumentName = "amount"
        val exception = ContractSpecException.argumentNotFound(argumentName)

        assertEquals("Required argument not found: $argumentName", exception.message)
        assertNull(exception.functionName)
        assertEquals(argumentName, exception.argumentName)
        assertNull(exception.entryName)
        assertNull(exception.cause)
    }

    @Test
    fun testArgumentNotFoundFactoryWithFunctionName() {
        val argumentName = "amount"
        val functionName = "transfer"
        val exception = ContractSpecException.argumentNotFound(argumentName, functionName)

        assertEquals("Required argument not found: $argumentName", exception.message)
        assertEquals(functionName, exception.functionName)
        assertEquals(argumentName, exception.argumentName)
        assertNull(exception.entryName)
        assertNull(exception.cause)
    }

    @Test
    fun testEntryNotFoundFactory() {
        val entryName = "MyEnum"
        val exception = ContractSpecException.entryNotFound(entryName)

        assertEquals("Entry not found: $entryName", exception.message)
        assertNull(exception.functionName)
        assertNull(exception.argumentName)
        assertEquals(entryName, exception.entryName)
        assertNull(exception.cause)
    }

    @Test
    fun testInvalidTypeFactory() {
        val typeMessage = "Expected Int, got String"
        val exception = ContractSpecException.invalidType(typeMessage)

        assertEquals("Invalid type: $typeMessage", exception.message)
        assertNull(exception.functionName)
        assertNull(exception.argumentName)
        assertNull(exception.entryName)
        assertNull(exception.cause)
    }

    @Test
    fun testConversionFailedFactory() {
        val conversionMessage = "Cannot convert String to U64"
        val exception = ContractSpecException.conversionFailed(conversionMessage)

        assertEquals("Conversion failed: $conversionMessage", exception.message)
        assertNull(exception.functionName)
        assertNull(exception.argumentName)
        assertNull(exception.entryName)
        assertNull(exception.cause)
    }

    @Test
    fun testInvalidEnumValueFactory() {
        val enumMessage = "Unknown variant: InvalidVariant"
        val exception = ContractSpecException.invalidEnumValue(enumMessage)

        assertEquals("Invalid enum value: $enumMessage", exception.message)
        assertNull(exception.functionName)
        assertNull(exception.argumentName)
        assertNull(exception.entryName)
        assertNull(exception.cause)
    }

    // ==================== Factory Method toString() Tests ====================

    @Test
    fun testFunctionNotFoundToString() {
        val exception = ContractSpecException.functionNotFound("transfer")
        val result = exception.toString()

        assertTrue(result.contains("ContractSpecException:"))
        assertTrue(result.contains("Function not found: transfer"))
        assertTrue(result.contains("(function: transfer)"))
    }

    @Test
    fun testArgumentNotFoundToString() {
        val exception = ContractSpecException.argumentNotFound("amount", "transfer")
        val result = exception.toString()

        assertTrue(result.contains("ContractSpecException:"))
        assertTrue(result.contains("Required argument not found: amount"))
        assertTrue(result.contains("(function: transfer)"))
        assertTrue(result.contains("(argument: amount)"))
    }

    @Test
    fun testEntryNotFoundToString() {
        val exception = ContractSpecException.entryNotFound("MyEnum")
        val result = exception.toString()

        assertTrue(result.contains("ContractSpecException:"))
        assertTrue(result.contains("Entry not found: MyEnum"))
        assertTrue(result.contains("(entry: MyEnum)"))
    }

    @Test
    fun testInvalidTypeToString() {
        val exception = ContractSpecException.invalidType("Expected Int, got String")
        val result = exception.toString()

        assertTrue(result.contains("ContractSpecException:"))
        assertTrue(result.contains("Invalid type: Expected Int, got String"))
        assertFalse(result.contains("(function:"))
        assertFalse(result.contains("(argument:"))
        assertFalse(result.contains("(entry:"))
    }

    // ==================== Exception Behavior Tests ====================

    @Test
    fun testContractSpecExceptionCanBeThrown() {
        val exception = ContractSpecException.functionNotFound("nonExistentFunction")

        assertFailsWith<ContractSpecException> {
            throw exception
        }
    }

    @Test
    fun testContractSpecExceptionPreservesStackTrace() {
        val exception = ContractSpecException.invalidType("Type error")
        val stackTrace = exception.stackTraceToString()

        assertTrue(stackTrace.isNotEmpty())
        assertTrue(stackTrace.contains("ContractSpecException"))
    }

    @Test
    fun testContractSpecExceptionWithCauseChain() {
        val rootException = IllegalArgumentException("Root error")
        val wrappedException = RuntimeException("Wrapped error", rootException)
        val contractSpecException = ContractSpecException(
            message = "Contract spec error",
            functionName = "test",
            cause = wrappedException
        )

        assertSame(wrappedException, contractSpecException.cause)
        assertSame(rootException, contractSpecException.cause?.cause)
        assertEquals("Contract spec error", contractSpecException.message)
        assertEquals("Wrapped error", contractSpecException.cause?.message)
        assertEquals("Root error", contractSpecException.cause?.cause?.message)
    }

    // ==================== Edge Cases ====================

    @Test
    fun testFactoryMethodsWithEmptyStrings() {
        val emptyFunctionException = ContractSpecException.functionNotFound("")
        val emptyArgumentException = ContractSpecException.argumentNotFound("")
        val emptyEntryException = ContractSpecException.entryNotFound("")
        val emptyTypeException = ContractSpecException.invalidType("")
        val emptyConversionException = ContractSpecException.conversionFailed("")
        val emptyEnumException = ContractSpecException.invalidEnumValue("")

        assertEquals("Function not found: ", emptyFunctionException.message)
        assertEquals("Required argument not found: ", emptyArgumentException.message)
        assertEquals("Entry not found: ", emptyEntryException.message)
        assertEquals("Invalid type: ", emptyTypeException.message)
        assertEquals("Conversion failed: ", emptyConversionException.message)
        assertEquals("Invalid enum value: ", emptyEnumException.message)
    }

    @Test
    fun testFactoryMethodsWithSpecialCharacters() {
        val specialChars = "function_name-123!@#$%"
        
        val exception = ContractSpecException.functionNotFound(specialChars)
        assertEquals("Function not found: $specialChars", exception.message)
        assertEquals(specialChars, exception.functionName)
        
        val result = exception.toString()
        assertTrue(result.contains(specialChars))
    }

    @Test
    fun testFactoryMethodsWithUnicodeCharacters() {
        val unicodeName = "転送_🚀_function"
        
        val exception = ContractSpecException.functionNotFound(unicodeName)
        assertEquals("Function not found: $unicodeName", exception.message)
        assertEquals(unicodeName, exception.functionName)
    }

    @Test
    fun testFactoryMethodsWithLongStrings() {
        val longName = "very_long_function_name_".repeat(100)
        
        val exception = ContractSpecException.functionNotFound(longName)
        assertEquals("Function not found: $longName", exception.message)
        assertEquals(longName, exception.functionName)
        assertTrue(exception.message!!.length > 2000)
    }

    @Test
    fun testFactoryMethodsWithMultilineStrings() {
        val multilineMessage = "Line 1\nLine 2\nLine 3"
        
        val exception = ContractSpecException.invalidType(multilineMessage)
        assertEquals("Invalid type: $multilineMessage", exception.message)
        assertTrue(exception.message!!.contains("\n"))
    }

    // ==================== Type Consistency Tests ====================

    @Test
    fun testAllFactoryMethodsReturnContractSpecException() {
        val exceptions = listOf(
            ContractSpecException.functionNotFound("test"),
            ContractSpecException.argumentNotFound("arg"),
            ContractSpecException.argumentNotFound("arg", "func"),
            ContractSpecException.entryNotFound("entry"),
            ContractSpecException.invalidType("type error"),
            ContractSpecException.conversionFailed("conversion error"),
            ContractSpecException.invalidEnumValue("enum error")
        )

        exceptions.forEach { exception ->
            assertTrue(
                exception is ContractSpecException,
                "Factory method should return ContractSpecException"
            )
            assertTrue(
                exception is ContractException,
                "Factory method result should inherit from ContractException"
            )
        }
    }

    @Test
    fun testFactoryMethodParameterTypes() {
        // Test that factory methods accept the correct parameter types
        val stringParam = "test"
        val nullStringParam: String? = null
        
        // These should compile without issues
        ContractSpecException.functionNotFound(stringParam)
        ContractSpecException.argumentNotFound(stringParam)
        ContractSpecException.argumentNotFound(stringParam, nullStringParam)
        ContractSpecException.argumentNotFound(stringParam, stringParam)
        ContractSpecException.entryNotFound(stringParam)
        ContractSpecException.invalidType(stringParam)
        ContractSpecException.conversionFailed(stringParam)
        ContractSpecException.invalidEnumValue(stringParam)
        
        // Verify null handling in argumentNotFound
        val exceptionWithNullFunction = ContractSpecException.argumentNotFound("arg", null)
        assertNull(exceptionWithNullFunction.functionName)
        assertEquals("arg", exceptionWithNullFunction.argumentName)
    }

    @Test
    fun testMessageConsistencyAcrossFactoryMethods() {
        val testInput = "testInput"
        
        val exceptions = mapOf(
            "functionNotFound" to ContractSpecException.functionNotFound(testInput),
            "argumentNotFound" to ContractSpecException.argumentNotFound(testInput),
            "entryNotFound" to ContractSpecException.entryNotFound(testInput),
            "invalidType" to ContractSpecException.invalidType(testInput),
            "conversionFailed" to ContractSpecException.conversionFailed(testInput),
            "invalidEnumValue" to ContractSpecException.invalidEnumValue(testInput)
        )
        
        exceptions.forEach { (methodName, exception) ->
            assertNotNull(exception.message, "$methodName should have non-null message")
            assertTrue(
                exception.message!!.contains(testInput),
                "$methodName message should contain input parameter"
            )
        }
    }
}