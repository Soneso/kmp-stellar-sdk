package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.ExtendFootprintTTLOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class ExtendFootprintTTLOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = ExtendFootprintTTLOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "extend_footprint_ttl",
        links = OperationTestHelpers.testLinks(),
        extendTo = 1000000L
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(1000000L, response.extendTo)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<ExtendFootprintTTLOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("extend_footprint_ttl")},
            "extend_to": 1000000
        }
        """
        val response = json.decodeFromString<ExtendFootprintTTLOperationResponse>(jsonString)
        assertEquals(1000000L, response.extendTo)
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
            ${OperationTestHelpers.baseFieldsJson("extend_footprint_ttl")},
            "extend_to": 1000000
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<ExtendFootprintTTLOperationResponse>(response)
    }
}
