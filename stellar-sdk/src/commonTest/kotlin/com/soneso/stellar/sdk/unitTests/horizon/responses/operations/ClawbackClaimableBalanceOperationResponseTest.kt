package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.ClawbackClaimableBalanceOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class ClawbackClaimableBalanceOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testBalanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072"

    private fun createResponse() = ClawbackClaimableBalanceOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "clawback_claimable_balance",
        links = OperationTestHelpers.testLinks(),
        balanceId = testBalanceId
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(testBalanceId, response.balanceId)
        assertNull(response.transaction)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<ClawbackClaimableBalanceOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("clawback_claimable_balance")},
            "balance_id": "$testBalanceId"
        }
        """
        val response = json.decodeFromString<ClawbackClaimableBalanceOperationResponse>(jsonString)
        assertEquals(testBalanceId, response.balanceId)
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
            ${OperationTestHelpers.baseFieldsJson("clawback_claimable_balance")},
            "balance_id": "$testBalanceId"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<ClawbackClaimableBalanceOperationResponse>(response)
    }
}
