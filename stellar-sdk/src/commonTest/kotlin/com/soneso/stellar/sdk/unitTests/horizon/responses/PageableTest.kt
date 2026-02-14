package com.soneso.stellar.sdk.unitTests.horizon.responses

import com.soneso.stellar.sdk.horizon.responses.Pageable
import com.soneso.stellar.sdk.horizon.responses.Response
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageableTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TestPageableResponse(
        @SerialName("id")
        val id: String,
        @SerialName("paging_token")
        override val pagingToken: String
    ) : Response(), Pageable

    @Test
    fun testPageableInterface() {
        val testResponse = TestPageableResponse("test-id", "test-token")
        assertTrue(testResponse is Pageable)
        assertEquals("test-token", testResponse.pagingToken)
    }

    @Test
    fun testPageableDeserialization() {
        val responseJson = """
        {
            "id": "test-id",
            "paging_token": "token-123"
        }
        """.trimIndent()

        val response = json.decodeFromString<TestPageableResponse>(responseJson)
        assertEquals("test-id", response.id)
        assertEquals("token-123", response.pagingToken)
    }

    @Test
    fun testPageableWithComplexToken() {
        val complexToken = "PT12345_1609459200_abc123"
        val responseJson = """
        {
            "id": "complex-test",
            "paging_token": "$complexToken"
        }
        """.trimIndent()

        val response = json.decodeFromString<TestPageableResponse>(responseJson)
        assertEquals(complexToken, response.pagingToken)
    }

    @Test
    fun testPageableMultipleImplementations() {
        // Test that multiple classes can implement Pageable
        @Serializable
        data class AnotherPageableResponse(
            @SerialName("name")
            val name: String,
            @SerialName("paging_token")
            override val pagingToken: String
        ) : Response(), Pageable

        val response1 = TestPageableResponse("id1", "token1")
        val response2 = AnotherPageableResponse("name2", "token2")

        val pageables: List<Pageable> = listOf(response1, response2)
        assertEquals("token1", pageables[0].pagingToken)
        assertEquals("token2", pageables[1].pagingToken)
    }
}