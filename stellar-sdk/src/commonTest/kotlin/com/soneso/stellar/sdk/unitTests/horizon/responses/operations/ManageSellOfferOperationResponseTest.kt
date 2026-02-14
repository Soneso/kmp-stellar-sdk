package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.Price
import com.soneso.stellar.sdk.horizon.responses.operations.ManageSellOfferOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class ManageSellOfferOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = ManageSellOfferOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "manage_sell_offer",
        links = OperationTestHelpers.testLinks(),
        offerId = 12345L,
        amount = "100.0000000",
        price = "2.5000000",
        priceR = Price(numerator = 5, denominator = 2),
        buyingAssetType = "credit_alphanum4",
        buyingAssetCode = "USD",
        buyingAssetIssuer = OperationTestHelpers.TEST_ACCOUNT_2,
        sellingAssetType = "native"
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(12345L, response.offerId)
        assertEquals("100.0000000", response.amount)
        assertEquals("2.5000000", response.price)
        assertEquals(5L, response.priceR.numerator)
        assertEquals(2L, response.priceR.denominator)
        assertEquals("credit_alphanum4", response.buyingAssetType)
        assertEquals("USD", response.buyingAssetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.buyingAssetIssuer)
        assertEquals("native", response.sellingAssetType)
        assertNull(response.sellingAssetCode)
        assertNull(response.sellingAssetIssuer)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<ManageSellOfferOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("manage_sell_offer")},
            "offer_id": 12345,
            "amount": "100.0000000",
            "price": "2.5000000",
            "price_r": {"n": 5, "d": 2},
            "buying_asset_type": "credit_alphanum4",
            "buying_asset_code": "USD",
            "buying_asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "selling_asset_type": "native"
        }
        """
        val response = json.decodeFromString<ManageSellOfferOperationResponse>(jsonString)
        assertEquals(12345L, response.offerId)
        assertEquals(5L, response.priceR.numerator)
        assertEquals(2L, response.priceR.denominator)
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
            ${OperationTestHelpers.baseFieldsJson("manage_sell_offer")},
            "offer_id": 12345,
            "amount": "100.0000000",
            "price": "2.5000000",
            "price_r": {"n": 5, "d": 2},
            "buying_asset_type": "credit_alphanum4",
            "buying_asset_code": "USD",
            "buying_asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "selling_asset_type": "native"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<ManageSellOfferOperationResponse>(response)
    }
}
