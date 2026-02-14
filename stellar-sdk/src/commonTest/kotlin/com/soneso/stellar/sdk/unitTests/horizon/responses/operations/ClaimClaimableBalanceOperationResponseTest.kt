package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.ClaimClaimableBalanceOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class ClaimClaimableBalanceOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testBalanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072"

    private fun createResponse() = ClaimClaimableBalanceOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "claim_claimable_balance",
        links = OperationTestHelpers.testLinks(),
        balanceId = testBalanceId,
        claimant = OperationTestHelpers.TEST_ACCOUNT
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(testBalanceId, response.balanceId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.claimant)
        assertNull(response.claimantMuxed)
        assertNull(response.claimantMuxedId)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<ClaimClaimableBalanceOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("claim_claimable_balance")},
            "balance_id": "$testBalanceId",
            "claimant": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<ClaimClaimableBalanceOperationResponse>(jsonString)
        assertEquals(testBalanceId, response.balanceId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.claimant)
    }

    @Test
    fun testJsonWithMuxed() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("claim_claimable_balance")},
            "balance_id": "$testBalanceId",
            "claimant": "${OperationTestHelpers.TEST_ACCOUNT}",
            "claimant_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "claimant_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}"
        }
        """
        val response = json.decodeFromString<ClaimClaimableBalanceOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, response.claimantMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, response.claimantMuxedId)
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
            ${OperationTestHelpers.baseFieldsJson("claim_claimable_balance")},
            "balance_id": "$testBalanceId",
            "claimant": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<ClaimClaimableBalanceOperationResponse>(response)
    }
}
