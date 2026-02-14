package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.Price
import com.soneso.stellar.sdk.horizon.responses.operations.CreatePassiveSellOfferOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class CreatePassiveSellOfferOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = CreatePassiveSellOfferOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "create_passive_sell_offer",
        links = OperationTestHelpers.testLinks(),
        offerId = 99999L,
        amount = "500.0000000",
        price = "10.0000000",
        priceR = Price(numerator = 10, denominator = 1),
        buyingAssetType = "credit_alphanum4",
        buyingAssetCode = "EUR",
        buyingAssetIssuer = OperationTestHelpers.TEST_ACCOUNT_2,
        sellingAssetType = "native"
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(99999L, response.offerId)
        assertEquals("500.0000000", response.amount)
        assertEquals("10.0000000", response.price)
        assertEquals(10L, response.priceR.numerator)
        assertEquals(1L, response.priceR.denominator)
        assertEquals("EUR", response.buyingAssetCode)
        assertEquals("native", response.sellingAssetType)
        assertNull(response.sellingAssetCode)
        assertNull(response.sellingAssetIssuer)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<CreatePassiveSellOfferOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("create_passive_sell_offer")},
            "offer_id": 99999,
            "amount": "500.0000000",
            "price": "10.0000000",
            "price_r": {"n": 10, "d": 1},
            "buying_asset_type": "credit_alphanum4",
            "buying_asset_code": "EUR",
            "buying_asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "selling_asset_type": "native"
        }
        """
        val response = json.decodeFromString<CreatePassiveSellOfferOperationResponse>(jsonString)
        assertEquals(99999L, response.offerId)
        assertEquals("EUR", response.buyingAssetCode)
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
            ${OperationTestHelpers.baseFieldsJson("create_passive_sell_offer")},
            "offer_id": 99999,
            "amount": "500.0000000",
            "price": "10.0000000",
            "price_r": {"n": 10, "d": 1},
            "buying_asset_type": "native",
            "selling_asset_type": "native"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<CreatePassiveSellOfferOperationResponse>(response)
    }
}
