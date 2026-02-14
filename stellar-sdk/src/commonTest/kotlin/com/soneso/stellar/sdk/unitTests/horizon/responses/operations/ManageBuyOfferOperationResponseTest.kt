package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.Price
import com.soneso.stellar.sdk.horizon.responses.operations.ManageBuyOfferOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class ManageBuyOfferOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse() = ManageBuyOfferOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "manage_buy_offer",
        links = OperationTestHelpers.testLinks(),
        offerId = 67890L,
        amount = "200.0000000",
        price = "0.5000000",
        priceR = Price(numerator = 1, denominator = 2),
        buyingAssetType = "credit_alphanum12",
        buyingAssetCode = "LONGASSET",
        buyingAssetIssuer = OperationTestHelpers.TEST_ACCOUNT_2,
        sellingAssetType = "credit_alphanum4",
        sellingAssetCode = "USD",
        sellingAssetIssuer = OperationTestHelpers.TEST_ACCOUNT_2
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(67890L, response.offerId)
        assertEquals("200.0000000", response.amount)
        assertEquals("0.5000000", response.price)
        assertEquals(1L, response.priceR.numerator)
        assertEquals(2L, response.priceR.denominator)
        assertEquals("credit_alphanum12", response.buyingAssetType)
        assertEquals("LONGASSET", response.buyingAssetCode)
        assertEquals("credit_alphanum4", response.sellingAssetType)
        assertEquals("USD", response.sellingAssetCode)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<ManageBuyOfferOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("manage_buy_offer")},
            "offer_id": 67890,
            "amount": "200.0000000",
            "price": "0.5000000",
            "price_r": {"n": 1, "d": 2},
            "buying_asset_type": "credit_alphanum12",
            "buying_asset_code": "LONGASSET",
            "buying_asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "selling_asset_type": "credit_alphanum4",
            "selling_asset_code": "USD",
            "selling_asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}"
        }
        """
        val response = json.decodeFromString<ManageBuyOfferOperationResponse>(jsonString)
        assertEquals(67890L, response.offerId)
        assertEquals("LONGASSET", response.buyingAssetCode)
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
            ${OperationTestHelpers.baseFieldsJson("manage_buy_offer")},
            "offer_id": 67890,
            "amount": "200.0000000",
            "price": "0.5000000",
            "price_r": {"n": 1, "d": 2},
            "buying_asset_type": "native",
            "selling_asset_type": "native"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<ManageBuyOfferOperationResponse>(response)
    }
}
