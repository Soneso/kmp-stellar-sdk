package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.ClawbackOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class ClawbackOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = ClawbackOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "clawback",
        links = OperationTestHelpers.testLinks(),
        assetType = "credit_alphanum4",
        assetCode = "USD",
        assetIssuer = OperationTestHelpers.TEST_ACCOUNT_2,
        amount = "100.0000000",
        from = OperationTestHelpers.TEST_ACCOUNT
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals("credit_alphanum4", response.assetType)
        assertEquals("USD", response.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.assetIssuer)
        assertEquals("100.0000000", response.amount)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.from)
        assertNull(response.fromMuxed)
        assertNull(response.fromMuxedId)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<ClawbackOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("clawback")},
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "amount": "100.0000000",
            "from": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<ClawbackOperationResponse>(jsonString)
        assertEquals("USD", response.assetCode)
        assertEquals("100.0000000", response.amount)
    }

    @Test
    fun testJsonWithMuxed() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("clawback")},
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "amount": "100.0000000",
            "from": "${OperationTestHelpers.TEST_ACCOUNT}",
            "from_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "from_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}"
        }
        """
        val response = json.decodeFromString<ClawbackOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, response.fromMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, response.fromMuxedId)
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
            ${OperationTestHelpers.baseFieldsJson("clawback")},
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "amount": "100.0000000",
            "from": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<ClawbackOperationResponse>(response)
    }
}
