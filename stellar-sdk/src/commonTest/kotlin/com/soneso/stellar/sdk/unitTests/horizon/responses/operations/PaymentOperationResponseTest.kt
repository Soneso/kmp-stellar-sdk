package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.PaymentOperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class PaymentOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(
        assetCode: String? = "USD",
        assetIssuer: String? = OperationTestHelpers.TEST_ACCOUNT_2,
        fromMuxed: String? = null,
        fromMuxedId: String? = null,
        toMuxed: String? = null,
        toMuxedId: String? = null
    ) = PaymentOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "payment",
        links = OperationTestHelpers.testLinks(),
        amount = "50.0000000",
        assetType = "credit_alphanum4",
        assetCode = assetCode,
        assetIssuer = assetIssuer,
        from = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        fromMuxed = fromMuxed,
        fromMuxedId = fromMuxedId,
        to = OperationTestHelpers.TEST_ACCOUNT,
        toMuxed = toMuxed,
        toMuxedId = toMuxedId
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals("50.0000000", response.amount)
        assertEquals("credit_alphanum4", response.assetType)
        assertEquals("USD", response.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.assetIssuer)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.from)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.to)
        assertNull(response.fromMuxed)
        assertNull(response.fromMuxedId)
        assertNull(response.toMuxed)
        assertNull(response.toMuxedId)
        assertNull(response.transaction)
    }

    @Test
    fun testNativeAsset() {
        val response = createResponse(assetCode = null, assetIssuer = null)
        assertNull(response.assetCode)
        assertNull(response.assetIssuer)
    }

    @Test
    fun testWithMuxedFields() {
        val response = createResponse(
            fromMuxed = "MFROM",
            fromMuxedId = "111",
            toMuxed = "MTO",
            toMuxedId = "222"
        )
        assertEquals("MFROM", response.fromMuxed)
        assertEquals("111", response.fromMuxedId)
        assertEquals("MTO", response.toMuxed)
        assertEquals("222", response.toMuxedId)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<PaymentOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("payment")},
            "amount": "50.0000000",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<PaymentOperationResponse>(jsonString)
        assertEquals("50.0000000", response.amount)
        assertEquals("credit_alphanum4", response.assetType)
        assertEquals("USD", response.assetCode)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.from)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.to)
    }

    @Test
    fun testJsonNativeAssetDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("payment")},
            "amount": "10.0000000",
            "asset_type": "native",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<PaymentOperationResponse>(jsonString)
        assertEquals("native", response.assetType)
        assertNull(response.assetCode)
        assertNull(response.assetIssuer)
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
            ${OperationTestHelpers.baseFieldsJson("payment")},
            "amount": "50.0000000",
            "asset_type": "native",
            "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "to": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<PaymentOperationResponse>(response)
    }
}
