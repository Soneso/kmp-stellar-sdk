package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Response
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TestResponse(val id: String) : Response()

    @Test
    fun testBasicResponseSerialization() {
        val testResponse = TestResponse("test-id")
        val responseJson = """{"id":"test-id"}"""
        
        // Test deserialization
        val deserialized = json.decodeFromString<TestResponse>(responseJson)
        assertEquals("test-id", deserialized.id)
        assertTrue(deserialized is Response)
        
        // Test serialization
        val serialized = json.encodeToString(TestResponse.serializer(), testResponse)
        assertTrue(serialized.contains("test-id"))
    }

    @Test
    fun testResponseInheritance() {
        val testResponse = TestResponse("test-id")
        assertTrue(testResponse is Response)
    }

    @Test
    fun testResponseAbstractClass() {
        // Response is abstract and cannot be instantiated directly
        // This test verifies the class hierarchy
        val testResponse = TestResponse("test-id")
        val response: Response = testResponse
        assertEquals(testResponse, response)
    }
}