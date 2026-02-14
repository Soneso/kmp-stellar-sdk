package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.ChangeTrustOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class ChangeTrustOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(
        trustee: String? = OperationTestHelpers.TEST_ACCOUNT_2,
        assetCode: String? = "USD",
        assetIssuer: String? = OperationTestHelpers.TEST_ACCOUNT_2,
        liquidityPoolId: String? = null,
        trustorMuxed: String? = null,
        trustorMuxedId: String? = null
    ) = ChangeTrustOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "change_trust",
        links = OperationTestHelpers.testLinks(),
        trustor = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        trustorMuxed = trustorMuxed,
        trustorMuxedId = trustorMuxedId,
        trustee = trustee,
        assetType = "credit_alphanum4",
        assetCode = assetCode,
        assetIssuer = assetIssuer,
        limit = "922337203685.4775807",
        liquidityPoolId = liquidityPoolId
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.trustor)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.trustee)
        assertEquals("credit_alphanum4", response.assetType)
        assertEquals("USD", response.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.assetIssuer)
        assertEquals("922337203685.4775807", response.limit)
        assertNull(response.liquidityPoolId)
        assertNull(response.trustorMuxed)
        assertNull(response.trustorMuxedId)
        assertNull(response.transaction)
    }

    @Test
    fun testWithLiquidityPool() {
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val response = createResponse(
            trustee = null,
            assetCode = null,
            assetIssuer = null,
            liquidityPoolId = poolId
        )
        assertNull(response.trustee)
        assertNull(response.assetCode)
        assertEquals(poolId, response.liquidityPoolId)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<ChangeTrustOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("change_trust")},
            "trustor": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "trustee": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "limit": "922337203685.4775807"
        }
        """
        val response = json.decodeFromString<ChangeTrustOperationResponse>(jsonString)
        assertEquals("922337203685.4775807", response.limit)
        assertEquals("USD", response.assetCode)
    }

    @Test
    fun testJsonWithLiquidityPoolId() {
        val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7"
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("change_trust")},
            "trustor": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "asset_type": "liquidity_pool_shares",
            "limit": "922337203685.4775807",
            "liquidity_pool_id": "$poolId"
        }
        """
        val response = json.decodeFromString<ChangeTrustOperationResponse>(jsonString)
        assertEquals(poolId, response.liquidityPoolId)
        assertNull(response.trustee)
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
            ${OperationTestHelpers.baseFieldsJson("change_trust")},
            "trustor": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "trustee": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "limit": "922337203685.4775807"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<ChangeTrustOperationResponse>(response)
    }
}
