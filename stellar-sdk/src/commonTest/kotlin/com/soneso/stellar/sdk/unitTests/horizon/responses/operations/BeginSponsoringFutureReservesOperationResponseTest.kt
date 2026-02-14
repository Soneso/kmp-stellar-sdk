package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.BeginSponsoringFutureReservesOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class BeginSponsoringFutureReservesOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = BeginSponsoringFutureReservesOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "begin_sponsoring_future_reserves",
        links = OperationTestHelpers.testLinks(),
        sponsoredId = OperationTestHelpers.TEST_ACCOUNT
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.sponsoredId)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<BeginSponsoringFutureReservesOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("begin_sponsoring_future_reserves")},
            "sponsored_id": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<BeginSponsoringFutureReservesOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.sponsoredId)
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
            ${OperationTestHelpers.baseFieldsJson("begin_sponsoring_future_reserves")},
            "sponsored_id": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<BeginSponsoringFutureReservesOperationResponse>(response)
    }
}
