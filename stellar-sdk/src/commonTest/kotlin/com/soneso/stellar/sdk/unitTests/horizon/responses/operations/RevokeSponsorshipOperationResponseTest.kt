package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.RevokeSponsorshipOperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class RevokeSponsorshipOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(
        accountId: String? = OperationTestHelpers.TEST_ACCOUNT,
        claimableBalanceId: String? = null,
        dataAccountId: String? = null,
        dataName: String? = null,
        offerId: Long? = null,
        trustlineAccountId: String? = null,
        trustlineAsset: String? = null,
        signerAccountId: String? = null,
        signerKey: String? = null
    ) = RevokeSponsorshipOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "revoke_sponsorship",
        links = OperationTestHelpers.testLinks(),
        accountId = accountId,
        claimableBalanceId = claimableBalanceId,
        dataAccountId = dataAccountId,
        dataName = dataName,
        offerId = offerId,
        trustlineAccountId = trustlineAccountId,
        trustlineAsset = trustlineAsset,
        signerAccountId = signerAccountId,
        signerKey = signerKey
    )

    @Test
    fun testConstructionAccountRevoke() {
        val response = createResponse()
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.accountId)
        assertNull(response.claimableBalanceId)
        assertNull(response.dataAccountId)
        assertNull(response.dataName)
        assertNull(response.offerId)
        assertNull(response.trustlineAccountId)
        assertNull(response.trustlineAsset)
        assertNull(response.signerAccountId)
        assertNull(response.signerKey)
        assertNull(response.transaction)
    }

    @Test
    fun testConstructionDataRevoke() {
        val response = createResponse(
            accountId = null,
            dataAccountId = OperationTestHelpers.TEST_ACCOUNT,
            dataName = "mykey"
        )
        assertNull(response.accountId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.dataAccountId)
        assertEquals("mykey", response.dataName)
    }

    @Test
    fun testConstructionOfferRevoke() {
        val response = createResponse(accountId = null, offerId = 12345L)
        assertEquals(12345L, response.offerId)
    }

    @Test
    fun testConstructionTrustlineRevoke() {
        val response = createResponse(
            accountId = null,
            trustlineAccountId = OperationTestHelpers.TEST_ACCOUNT,
            trustlineAsset = "USD:${OperationTestHelpers.TEST_ACCOUNT_2}"
        )
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.trustlineAccountId)
        assertEquals("USD:${OperationTestHelpers.TEST_ACCOUNT_2}", response.trustlineAsset)
    }

    @Test
    fun testConstructionSignerRevoke() {
        val response = createResponse(
            accountId = null,
            signerAccountId = OperationTestHelpers.TEST_ACCOUNT,
            signerKey = OperationTestHelpers.TEST_ACCOUNT_2
        )
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.signerAccountId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.signerKey)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<RevokeSponsorshipOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("revoke_sponsorship")},
            "account_id": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<RevokeSponsorshipOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.accountId)
    }

    @Test
    fun testJsonDeserializationTrustline() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("revoke_sponsorship")},
            "trustline_account_id": "${OperationTestHelpers.TEST_ACCOUNT}",
            "trustline_asset": "USD:${OperationTestHelpers.TEST_ACCOUNT_2}"
        }
        """
        val response = json.decodeFromString<RevokeSponsorshipOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.trustlineAccountId)
        assertEquals("USD:${OperationTestHelpers.TEST_ACCOUNT_2}", response.trustlineAsset)
    }

    @Test
    fun testJsonDeserializationSigner() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("revoke_sponsorship")},
            "signer_account_id": "${OperationTestHelpers.TEST_ACCOUNT}",
            "signer_key": "${OperationTestHelpers.TEST_ACCOUNT_2}"
        }
        """
        val response = json.decodeFromString<RevokeSponsorshipOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.signerAccountId)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.signerKey)
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
            ${OperationTestHelpers.baseFieldsJson("revoke_sponsorship")},
            "account_id": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<RevokeSponsorshipOperationResponse>(response)
    }
}
