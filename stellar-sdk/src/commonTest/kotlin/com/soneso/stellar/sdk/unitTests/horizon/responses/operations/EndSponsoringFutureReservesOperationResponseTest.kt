package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.EndSponsoringFutureReservesOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class EndSponsoringFutureReservesOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = EndSponsoringFutureReservesOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "end_sponsoring_future_reserves",
        links = OperationTestHelpers.testLinks(),
        beginSponsor = OperationTestHelpers.TEST_ACCOUNT
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.beginSponsor)
        assertNull(response.beginSponsorMuxed)
        assertNull(response.beginSponsorMuxedId)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<EndSponsoringFutureReservesOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("end_sponsoring_future_reserves")},
            "begin_sponsor": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<EndSponsoringFutureReservesOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.beginSponsor)
    }

    @Test
    fun testJsonWithMuxed() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("end_sponsoring_future_reserves")},
            "begin_sponsor": "${OperationTestHelpers.TEST_ACCOUNT}",
            "begin_sponsor_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "begin_sponsor_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}"
        }
        """
        val response = json.decodeFromString<EndSponsoringFutureReservesOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, response.beginSponsorMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, response.beginSponsorMuxedId)
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
            ${OperationTestHelpers.baseFieldsJson("end_sponsoring_future_reserves")},
            "begin_sponsor": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<EndSponsoringFutureReservesOperationResponse>(response)
    }
}
