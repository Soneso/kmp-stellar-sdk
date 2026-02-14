@file:Suppress("DEPRECATION")
package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.AllowTrustOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class AllowTrustOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(
        authorize: Boolean? = true,
        authorizeToMaintainLiabilities: Boolean? = null,
        assetCode: String? = "USD",
        assetIssuer: String? = OperationTestHelpers.TEST_ACCOUNT_2
    ) = AllowTrustOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "allow_trust",
        links = OperationTestHelpers.testLinks(),
        trustor = OperationTestHelpers.TEST_ACCOUNT,
        trustee = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        assetType = "credit_alphanum4",
        assetCode = assetCode,
        assetIssuer = assetIssuer,
        authorize = authorize,
        authorizeToMaintainLiabilities = authorizeToMaintainLiabilities
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.trustor)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.trustee)
        assertEquals("credit_alphanum4", response.assetType)
        assertEquals("USD", response.assetCode)
        assertEquals(true, response.authorize)
        assertNull(response.authorizeToMaintainLiabilities)
        assertNull(response.trusteeMuxed)
        assertNull(response.trusteeMuxedId)
        assertNull(response.transaction)
    }

    @Test
    fun testWithMaintainLiabilities() {
        val response = createResponse(
            authorize = false,
            authorizeToMaintainLiabilities = true
        )
        assertEquals(false, response.authorize)
        assertEquals(true, response.authorizeToMaintainLiabilities)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<AllowTrustOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("allow_trust")},
            "trustor": "${OperationTestHelpers.TEST_ACCOUNT}",
            "trustee": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "authorize": true,
            "authorize_to_maintain_liabilities": false
        }
        """
        val response = json.decodeFromString<AllowTrustOperationResponse>(jsonString)
        assertEquals(true, response.authorize)
        assertEquals(false, response.authorizeToMaintainLiabilities)
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
            ${OperationTestHelpers.baseFieldsJson("allow_trust")},
            "trustor": "${OperationTestHelpers.TEST_ACCOUNT}",
            "trustee": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "authorize": true
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<AllowTrustOperationResponse>(response)
    }
}
