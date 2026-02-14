package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.PathPaymentBaseOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.PathPaymentStrictReceiveOperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class PathPaymentStrictReceiveOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = PathPaymentStrictReceiveOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "path_payment_strict_receive",
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
                assetType = "credit_alphanum4",
                assetCode = "EUR",
                assetIssuer = OperationTestHelpers.TEST_ACCOUNT_2
            )
        ),
        sourceMax = "55.0000000"
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals("100.0000000", response.amount)
        assertEquals("50.0000000", response.sourceAmount)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.from)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.to)
        assertEquals("credit_alphanum4", response.assetType)
        assertEquals("USD", response.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.assetIssuer)
        assertEquals("native", response.sourceAssetType)
        assertNull(response.sourceAssetCode)
        assertNull(response.sourceAssetIssuer)
        assertEquals(1, response.path.size)
        assertEquals("EUR", response.path[0].assetCode)
        assertEquals("55.0000000", response.sourceMax)
        assertNull(response.fromMuxed)
        assertNull(response.fromMuxedId)
        assertNull(response.toMuxed)
        assertNull(response.toMuxedId)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<PathPaymentStrictReceiveOperationResponse>(response)
        assertIs<PathPaymentBaseOperationResponse>(response)
        assertIs<OperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("path_payment_strict_receive")},
            "amount": "100.0000000",
            "source_amount": "50.0000000",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "source_asset_type": "native",
            "source_max": "55.0000000",
            "path": [
                {
                    "asset_type": "credit_alphanum4",
                    "asset_code": "EUR",
                    "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}"
                }
            ]
        }
        """
        val response = json.decodeFromString<PathPaymentStrictReceiveOperationResponse>(jsonString)
        assertEquals("100.0000000", response.amount)
        assertEquals("55.0000000", response.sourceMax)
        assertEquals(1, response.path.size)
        assertEquals("EUR", response.path[0].assetCode)
    }

    @Test
    fun testJsonDeserializationEmptyPath() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("path_payment_strict_receive")},
            "amount": "100.0000000",
            "source_amount": "50.0000000",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}",
            "asset_type": "native",
            "source_asset_type": "native",
            "source_max": "55.0000000",
            "path": []
        }
        """
        val response = json.decodeFromString<PathPaymentStrictReceiveOperationResponse>(jsonString)
        assertTrue(response.path.isEmpty())
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
            ${OperationTestHelpers.baseFieldsJson("path_payment_strict_receive")},
            "amount": "100.0000000",
            "source_amount": "50.0000000",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}",
            "asset_type": "native",
            "source_asset_type": "native",
            "source_max": "55.0000000",
            "path": []
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<PathPaymentStrictReceiveOperationResponse>(response)
    }
}
