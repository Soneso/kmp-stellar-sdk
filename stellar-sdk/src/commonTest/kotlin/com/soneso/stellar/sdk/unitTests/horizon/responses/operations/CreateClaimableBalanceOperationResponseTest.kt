package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.Claimant
import com.soneso.stellar.sdk.horizon.responses.Predicate
import com.soneso.stellar.sdk.horizon.responses.operations.CreateClaimableBalanceOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class CreateClaimableBalanceOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(sponsor: String? = null) = CreateClaimableBalanceOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "create_claimable_balance",
        links = OperationTestHelpers.testLinks(),
        sponsor = sponsor,
        asset = "native",
        amount = "100.0000000",
        claimants = listOf(
            Claimant(
                destination = OperationTestHelpers.TEST_ACCOUNT,
                predicate = Predicate(unconditional = true)
            )
        )
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertNull(response.sponsor)
        assertEquals("native", response.asset)
        assertEquals("100.0000000", response.amount)
        assertEquals(1, response.claimants.size)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.claimants[0].destination)
        assertEquals(true, response.claimants[0].predicate.unconditional)
        assertNull(response.transaction)
    }

    @Test
    fun testWithSponsor() {
        val response = createResponse(sponsor = OperationTestHelpers.TEST_ACCOUNT_2)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.sponsor)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<CreateClaimableBalanceOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("create_claimable_balance")},
            "asset": "native",
            "amount": "100.0000000",
            "claimants": [
                {
                    "destination": "${OperationTestHelpers.TEST_ACCOUNT}",
                    "predicate": {
                        "unconditional": true
                    }
                }
            ]
        }
        """
        val response = json.decodeFromString<CreateClaimableBalanceOperationResponse>(jsonString)
        assertEquals("native", response.asset)
        assertEquals("100.0000000", response.amount)
        assertEquals(1, response.claimants.size)
        assertEquals(true, response.claimants[0].predicate.unconditional)
    }

    @Test
    fun testJsonWithSponsor() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("create_claimable_balance")},
            "sponsor": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "asset": "USD:${OperationTestHelpers.TEST_ACCOUNT_2}",
            "amount": "50.0000000",
            "claimants": [
                {
                    "destination": "${OperationTestHelpers.TEST_ACCOUNT}",
                    "predicate": {
                        "abs_before": "2025-01-01T00:00:00Z"
                    }
                }
            ]
        }
        """
        val response = json.decodeFromString<CreateClaimableBalanceOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.sponsor)
        assertEquals("2025-01-01T00:00:00Z", response.claimants[0].predicate.absBefore)
    }

    @Test
    fun testJsonMultipleClaimants() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("create_claimable_balance")},
            "asset": "native",
            "amount": "100.0000000",
            "claimants": [
                {
                    "destination": "${OperationTestHelpers.TEST_ACCOUNT}",
                    "predicate": { "unconditional": true }
                },
                {
                    "destination": "${OperationTestHelpers.TEST_ACCOUNT_2}",
                    "predicate": { "rel_before": "86400" }
                }
            ]
        }
        """
        val response = json.decodeFromString<CreateClaimableBalanceOperationResponse>(jsonString)
        assertEquals(2, response.claimants.size)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.claimants[1].destination)
        assertEquals("86400", response.claimants[1].predicate.relBefore)
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
            ${OperationTestHelpers.baseFieldsJson("create_claimable_balance")},
            "asset": "native",
            "amount": "100.0000000",
            "claimants": [
                {
                    "destination": "${OperationTestHelpers.TEST_ACCOUNT}",
                    "predicate": { "unconditional": true }
                }
            ]
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<CreateClaimableBalanceOperationResponse>(response)
    }
}
