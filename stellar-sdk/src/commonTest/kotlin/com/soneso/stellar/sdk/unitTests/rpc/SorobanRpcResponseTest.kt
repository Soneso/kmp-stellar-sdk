package com.soneso.stellar.sdk.unitTests.rpc

import com.soneso.stellar.sdk.rpc.responses.SorobanRpcResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SorobanRpcResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TestResult(
        @SerialName("status")
        val status: String,
        @SerialName("value")
        val value: String
    )

    @Test
    fun testSuccessfulResponseDeserialization() {
        val responseJson = """
        {
            "jsonrpc": "2.0",
            "id": "test-123",
            "result": {
                "status": "healthy",
                "value": "test-data"
            }
        }
        """.trimIndent()

        val response = json.decodeFromString<SorobanRpcResponse<TestResult>>(responseJson)
        assertEquals("2.0", response.jsonRpc)
        assertEquals("test-123", response.id)
        assertNotNull(response.result)
        assertNull(response.error)
        assertEquals("healthy", response.result!!.status)
        assertEquals("test-data", response.result!!.value)
    }

    @Test
    fun testErrorResponseDeserialization() {
        val responseJson = """
        {
            "jsonrpc": "2.0",
            "id": "test-456",
            "error": {
                "code": -32600,
                "message": "Invalid Request",
                "data": "Additional error details"
            }
        }
        """.trimIndent()

        val response = json.decodeFromString<SorobanRpcResponse<TestResult>>(responseJson)
        assertEquals("2.0", response.jsonRpc)
        assertEquals("test-456", response.id)
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32600, response.error!!.code)
        assertEquals("Invalid Request", response.error!!.message)
        assertEquals("Additional error details", response.error!!.data)
    }

    @Test
    fun testErrorResponseWithoutData() {
        val responseJson = """
        {
            "jsonrpc": "2.0",
            "id": "test-789",
            "error": {
                "code": -32601,
                "message": "Method not found"
            }
        }
        """.trimIndent()

        val response = json.decodeFromString<SorobanRpcResponse<TestResult>>(responseJson)
        assertEquals("2.0", response.jsonRpc)
        assertEquals("test-789", response.id)
        assertNull(response.result)
        assertNotNull(response.error)
        assertEquals(-32601, response.error!!.code)
        assertEquals("Method not found", response.error!!.message)
        assertNull(response.error!!.data)
    }

    @Test
    fun testIsSuccessMethod() {
        val successResponse = SorobanRpcResponse<TestResult>(
            jsonRpc = "2.0",
            id = "test",
            result = TestResult("success", "data"),
            error = null
        )

        val errorResponse = SorobanRpcResponse<TestResult>(
            jsonRpc = "2.0",
            id = "test",
            result = null,
            error = SorobanRpcResponse.Error(-32600, "Invalid Request")
        )

        assertTrue(successResponse.isSuccess())
        assertFalse(errorResponse.isSuccess())
    }

    @Test
    fun testIsErrorMethod() {
        val successResponse = SorobanRpcResponse<TestResult>(
            jsonRpc = "2.0",
            id = "test",
            result = TestResult("success", "data"),
            error = null
        )

        val errorResponse = SorobanRpcResponse<TestResult>(
            jsonRpc = "2.0",
            id = "test",
            result = null,
            error = SorobanRpcResponse.Error(-32600, "Invalid Request")
        )

        assertFalse(successResponse.isError())
        assertTrue(errorResponse.isError())
    }

    @Test
    fun testStandardErrorCodes() {
        val errorCodes = mapOf(
            -32700 to "Parse error",
            -32600 to "Invalid Request",
            -32601 to "Method not found",
            -32602 to "Invalid params",
            -32603 to "Internal error",
            -32000 to "Server error"
        )

        for ((code, message) in errorCodes) {
            val error = SorobanRpcResponse.Error(code, message)
            assertEquals(code, error.code)
            assertEquals(message, error.message)
            assertNull(error.data)
        }
    }

    @Test
    fun testErrorWithAdditionalData() {
        val error = SorobanRpcResponse.Error(
            code = -32000,
            message = "Server error",
            data = "Stack trace or additional debugging information"
        )

        assertEquals(-32000, error.code)
        assertEquals("Server error", error.message)
        assertEquals("Stack trace or additional debugging information", error.data)
    }

    @Test
    fun testToStringForSuccessResponse() {
        val response = SorobanRpcResponse<TestResult>(
            jsonRpc = "2.0",
            id = "test-success",
            result = TestResult("healthy", "ok"),
            error = null
        )

        val toStringResult = response.toString()
        assertTrue(toStringResult.contains("jsonRpc='2.0'"))
        assertTrue(toStringResult.contains("id='test-success'"))
        assertTrue(toStringResult.contains("result="))
        assertFalse(toStringResult.contains("error="))
    }

    @Test
    fun testToStringForErrorResponse() {
        val response = SorobanRpcResponse<TestResult>(
            jsonRpc = "2.0",
            id = "test-error",
            result = null,
            error = SorobanRpcResponse.Error(-32600, "Invalid Request")
        )

        val toStringResult = response.toString()
        assertTrue(toStringResult.contains("jsonRpc='2.0'"))
        assertTrue(toStringResult.contains("id='test-error'"))
        assertTrue(toStringResult.contains("error="))
        assertFalse(toStringResult.contains("result="))
    }

    @Serializable
    data class ComplexResult(
        @SerialName("nested_object")
        val nestedObject: NestedData,
        @SerialName("array_field")
        val arrayField: List<String>
    )

    @Serializable
    data class NestedData(
        @SerialName("inner_value")
        val innerValue: Int
    )

    @Test
    fun testComplexResultTypeDeserialization() {

        val responseJson = """
        {
            "jsonrpc": "2.0",
            "id": "complex-test",
            "result": {
                "nested_object": {
                    "inner_value": 42
                },
                "array_field": ["item1", "item2", "item3"]
            }
        }
        """.trimIndent()

        val response = json.decodeFromString<SorobanRpcResponse<ComplexResult>>(responseJson)
        assertEquals("2.0", response.jsonRpc)
        assertEquals("complex-test", response.id)
        assertNotNull(response.result)
        assertEquals(42, response.result!!.nestedObject.innerValue)
        assertEquals(3, response.result!!.arrayField.size)
        assertEquals("item1", response.result!!.arrayField[0])
    }

    @Test
    fun testNullResultDeserialization() {
        val responseJson = """
        {
            "jsonrpc": "2.0",
            "id": "null-result",
            "result": null
        }
        """.trimIndent()

        val response = json.decodeFromString<SorobanRpcResponse<TestResult>>(responseJson)
        assertEquals("2.0", response.jsonRpc)
        assertEquals("null-result", response.id)
        assertNull(response.result)
        assertNull(response.error)
        assertTrue(response.isSuccess())
        assertFalse(response.isError())
    }

    @Test
    fun testStringResultType() {
        val responseJson = """
        {
            "jsonrpc": "2.0",
            "id": "string-test",
            "result": "simple string result"
        }
        """.trimIndent()

        val response = json.decodeFromString<SorobanRpcResponse<String>>(responseJson)
        assertEquals("2.0", response.jsonRpc)
        assertEquals("string-test", response.id)
        assertEquals("simple string result", response.result)
        assertTrue(response.isSuccess())
    }

    @Test
    fun testNumberResultType() {
        val responseJson = """
        {
            "jsonrpc": "2.0",
            "id": "number-test",
            "result": 12345
        }
        """.trimIndent()

        val response = json.decodeFromString<SorobanRpcResponse<Int>>(responseJson)
        assertEquals("2.0", response.jsonRpc)
        assertEquals("number-test", response.id)
        assertEquals(12345, response.result)
        assertTrue(response.isSuccess())
    }

    @Test
    fun testEquality() {
        val error1 = SorobanRpcResponse.Error(-32600, "Invalid Request", "data")
        val error2 = SorobanRpcResponse.Error(-32600, "Invalid Request", "data")
        val error3 = SorobanRpcResponse.Error(-32601, "Method not found", null)

        assertEquals(error1, error2)
        assertEquals(error1.hashCode(), error2.hashCode())
        assertTrue(error1 != error3)
    }
}