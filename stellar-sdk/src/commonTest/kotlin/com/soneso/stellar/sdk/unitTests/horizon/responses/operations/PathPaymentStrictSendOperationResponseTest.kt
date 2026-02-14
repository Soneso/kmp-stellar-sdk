package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.PathPaymentBaseOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.PathPaymentStrictSendOperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class PathPaymentStrictSendOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = PathPaymentStrictSendOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "path_payment_strict_send",
        links = OperationTestHelpers.testLinks(),
        amount = "100.0000000",
        sourceAmount = "50.0000000",
        from = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        to = OperationTestHelpers.TEST_ACCOUNT,
        assetType = "credit_alphanum4",
        assetCode = "USD",
        assetIssuer = OperationTestHelpers.TEST_ACCOUNT_2,
        sourceAssetType = "native",
        path = listOf(
            PathPaymentBaseOperationResponse.PathAsset(
                assetType = "credit_alphanum12",
                assetCode = "LONGASSET",
                assetIssuer = OperationTestHelpers.TEST_ACCOUNT_2
            )
        ),
        destinationMin = "95.0000000"
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals("100.0000000", response.amount)
        assertEquals("50.0000000", response.sourceAmount)
        assertEquals("95.0000000", response.destinationMin)
        assertEquals(1, response.path.size)
        assertEquals("LONGASSET", response.path[0].assetCode)
        assertEquals("credit_alphanum12", response.path[0].assetType)
        assertNull(response.fromMuxed)
        assertNull(response.toMuxed)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<PathPaymentStrictSendOperationResponse>(response)
        assertIs<PathPaymentBaseOperationResponse>(response)
        assertIs<OperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("path_payment_strict_send")},
            "amount": "100.0000000",
            "source_amount": "50.0000000",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "source_asset_type": "native",
            "destination_min": "95.0000000",
            "path": [
                {
                    "asset_type": "credit_alphanum12",
                    "asset_code": "LONGASSET",
                    "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}"
                }
            ]
        }
        """
        val response = json.decodeFromString<PathPaymentStrictSendOperationResponse>(jsonString)
        assertEquals("95.0000000", response.destinationMin)
        assertEquals(1, response.path.size)
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
            ${OperationTestHelpers.baseFieldsJson("path_payment_strict_send")},
            "amount": "100.0000000",
            "source_amount": "50.0000000",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}",
            "asset_type": "native",
            "source_asset_type": "native",
            "destination_min": "95.0000000",
            "path": []
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<PathPaymentStrictSendOperationResponse>(response)
    }
}
