package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.RestoreFootprintOperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class RestoreFootprintOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = RestoreFootprintOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "restore_footprint",
        links = OperationTestHelpers.testLinks()
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(OperationTestHelpers.TEST_ID, response.id)
        assertEquals("restore_footprint", response.type)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<RestoreFootprintOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("restore_footprint")}
        }
        """
        val response = json.decodeFromString<RestoreFootprintOperationResponse>(jsonString)
        assertEquals("restore_footprint", response.type)
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
            ${OperationTestHelpers.baseFieldsJson("restore_footprint")}
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<RestoreFootprintOperationResponse>(response)
    }
}
