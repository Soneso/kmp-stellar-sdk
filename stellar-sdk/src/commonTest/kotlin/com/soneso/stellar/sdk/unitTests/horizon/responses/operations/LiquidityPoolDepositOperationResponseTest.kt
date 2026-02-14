package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.AssetAmount
import com.soneso.stellar.sdk.horizon.responses.Price
import com.soneso.stellar.sdk.horizon.responses.operations.LiquidityPoolDepositOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class LiquidityPoolDepositOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testPoolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"

    private fun createResponse() = LiquidityPoolDepositOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "liquidity_pool_deposit",
        links = OperationTestHelpers.testLinks(),
        liquidityPoolId = testPoolId,
        reservesMax = listOf(
            AssetAmount(asset = "native", amount = "1000.0000000"),
            AssetAmount(asset = "USD:${OperationTestHelpers.TEST_ACCOUNT_2}", amount = "500.0000000")
        ),
        minPrice = "0.5000000",
        minPriceR = Price(numerator = 1, denominator = 2),
        maxPrice = "2.0000000",
        maxPriceR = Price(numerator = 2, denominator = 1),
        reservesDeposited = listOf(
            AssetAmount(asset = "native", amount = "800.0000000"),
            AssetAmount(asset = "USD:${OperationTestHelpers.TEST_ACCOUNT_2}", amount = "400.0000000")
        ),
        sharesReceived = "632.4555320"
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(testPoolId, response.liquidityPoolId)
        assertEquals(2, response.reservesMax.size)
        assertEquals("native", response.reservesMax[0].asset)
        assertEquals("1000.0000000", response.reservesMax[0].amount)
        assertEquals("0.5000000", response.minPrice)
        assertEquals(1L, response.minPriceR.numerator)
        assertEquals(2L, response.minPriceR.denominator)
        assertEquals("2.0000000", response.maxPrice)
        assertEquals(2L, response.maxPriceR.numerator)
        assertEquals(1L, response.maxPriceR.denominator)
        assertEquals(2, response.reservesDeposited.size)
        assertEquals("632.4555320", response.sharesReceived)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<LiquidityPoolDepositOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("liquidity_pool_deposit")},
            "liquidity_pool_id": "$testPoolId",
            "reserves_max": [
                {"asset": "native", "amount": "1000.0000000"},
                {"asset": "USD:${OperationTestHelpers.TEST_ACCOUNT_2}", "amount": "500.0000000"}
            ],
            "min_price": "0.5000000",
            "min_price_r": {"n": 1, "d": 2},
            "max_price": "2.0000000",
            "max_price_r": {"n": 2, "d": 1},
            "reserves_deposited": [
                {"asset": "native", "amount": "800.0000000"},
                {"asset": "USD:${OperationTestHelpers.TEST_ACCOUNT_2}", "amount": "400.0000000"}
            ],
            "shares_received": "632.4555320"
        }
        """
        val response = json.decodeFromString<LiquidityPoolDepositOperationResponse>(jsonString)
        assertEquals(testPoolId, response.liquidityPoolId)
        assertEquals(2, response.reservesMax.size)
        assertEquals("632.4555320", response.sharesReceived)
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
            ${OperationTestHelpers.baseFieldsJson("liquidity_pool_deposit")},
            "liquidity_pool_id": "$testPoolId",
            "reserves_max": [
                {"asset": "native", "amount": "1000.0000000"}
            ],
            "min_price": "0.5000000",
            "min_price_r": {"n": 1, "d": 2},
            "max_price": "2.0000000",
            "max_price_r": {"n": 2, "d": 1},
            "reserves_deposited": [
                {"asset": "native", "amount": "800.0000000"}
            ],
            "shares_received": "632.4555320"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<LiquidityPoolDepositOperationResponse>(response)
    }
}
