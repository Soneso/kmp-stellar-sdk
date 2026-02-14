package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.AssetAmount
import com.soneso.stellar.sdk.horizon.responses.operations.LiquidityPoolWithdrawOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class LiquidityPoolWithdrawOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testPoolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"

    private fun createResponse() = LiquidityPoolWithdrawOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "liquidity_pool_withdraw",
        links = OperationTestHelpers.testLinks(),
        liquidityPoolId = testPoolId,
        reservesMin = listOf(
            AssetAmount(asset = "native", amount = "100.0000000"),
            AssetAmount(asset = "USD:${OperationTestHelpers.TEST_ACCOUNT_2}", amount = "50.0000000")
        ),
        reservesReceived = listOf(
            AssetAmount(asset = "native", amount = "120.0000000"),
            AssetAmount(asset = "USD:${OperationTestHelpers.TEST_ACCOUNT_2}", amount = "60.0000000")
        ),
        shares = "200.0000000"
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(testPoolId, response.liquidityPoolId)
        assertEquals(2, response.reservesMin.size)
        assertEquals("native", response.reservesMin[0].asset)
        assertEquals("100.0000000", response.reservesMin[0].amount)
        assertEquals(2, response.reservesReceived.size)
        assertEquals("120.0000000", response.reservesReceived[0].amount)
        assertEquals("200.0000000", response.shares)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<LiquidityPoolWithdrawOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("liquidity_pool_withdraw")},
            "liquidity_pool_id": "$testPoolId",
            "reserves_min": [
                {"asset": "native", "amount": "100.0000000"},
                {"asset": "USD:${OperationTestHelpers.TEST_ACCOUNT_2}", "amount": "50.0000000"}
            ],
            "reserves_received": [
                {"asset": "native", "amount": "120.0000000"},
                {"asset": "USD:${OperationTestHelpers.TEST_ACCOUNT_2}", "amount": "60.0000000"}
            ],
            "shares": "200.0000000"
        }
        """
        val response = json.decodeFromString<LiquidityPoolWithdrawOperationResponse>(jsonString)
        assertEquals(testPoolId, response.liquidityPoolId)
        assertEquals("200.0000000", response.shares)
        assertEquals(2, response.reservesMin.size)
        assertEquals(2, response.reservesReceived.size)
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
            ${OperationTestHelpers.baseFieldsJson("liquidity_pool_withdraw")},
            "liquidity_pool_id": "$testPoolId",
            "reserves_min": [
                {"asset": "native", "amount": "100.0000000"}
            ],
            "reserves_received": [
                {"asset": "native", "amount": "120.0000000"}
            ],
            "shares": "200.0000000"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<LiquidityPoolWithdrawOperationResponse>(response)
    }
}
