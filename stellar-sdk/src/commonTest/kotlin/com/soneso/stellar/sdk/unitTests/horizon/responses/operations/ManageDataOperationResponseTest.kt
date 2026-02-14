package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.ManageDataOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class ManageDataOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(value: String? = "dGVzdHZhbHVl") = ManageDataOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "manage_data",
        links = OperationTestHelpers.testLinks(),
        name = "testkey",
        value = value
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals("testkey", response.name)
        assertEquals("dGVzdHZhbHVl", response.value)
        assertNull(response.transaction)
    }

    @Test
    fun testDeleteDataEntry() {
        val response = createResponse(value = null)
        assertEquals("testkey", response.name)
        assertNull(response.value)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<ManageDataOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("manage_data")},
            "name": "testkey",
            "value": "dGVzdHZhbHVl"
        }
        """
        val response = json.decodeFromString<ManageDataOperationResponse>(jsonString)
        assertEquals("testkey", response.name)
        assertEquals("dGVzdHZhbHVl", response.value)
    }

    @Test
    fun testJsonDeserializationNullValue() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("manage_data")},
            "name": "testkey",
            "value": null
        }
        """
        val response = json.decodeFromString<ManageDataOperationResponse>(jsonString)
        assertNull(response.value)
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
            ${OperationTestHelpers.baseFieldsJson("manage_data")},
            "name": "testkey",
            "value": "dGVzdHZhbHVl"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<ManageDataOperationResponse>(response)
    }
}
