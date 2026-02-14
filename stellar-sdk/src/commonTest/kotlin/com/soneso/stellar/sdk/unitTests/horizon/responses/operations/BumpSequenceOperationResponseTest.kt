package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.BumpSequenceOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class BumpSequenceOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = BumpSequenceOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "bump_sequence",
        links = OperationTestHelpers.testLinks(),
        bumpTo = 1234567890L
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(1234567890L, response.bumpTo)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<BumpSequenceOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("bump_sequence")},
            "bump_to": 1234567890
        }
        """
        val response = json.decodeFromString<BumpSequenceOperationResponse>(jsonString)
        assertEquals(1234567890L, response.bumpTo)
    }

    @Test
    fun testEqualsAndHashCode() {
        val r1 = createResponse()
        val r2 = createResponse()
        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    @Test
    fun testPolymorphicDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("bump_sequence")},
            "bump_to": 1234567890
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<BumpSequenceOperationResponse>(response)
    }
}
