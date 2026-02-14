package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.AccountMergeOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class AccountMergeOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(
        accountMuxed: String? = null,
        accountMuxedId: String? = null,
        intoMuxed: String? = null,
        intoMuxedId: String? = null
    ) = AccountMergeOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "account_merge",
        links = OperationTestHelpers.testLinks(),
        account = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        accountMuxed = accountMuxed,
        accountMuxedId = accountMuxedId,
        into = OperationTestHelpers.TEST_ACCOUNT,
        intoMuxed = intoMuxed,
        intoMuxedId = intoMuxedId
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.account)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.into)
        assertNull(response.accountMuxed)
        assertNull(response.accountMuxedId)
        assertNull(response.intoMuxed)
        assertNull(response.intoMuxedId)
        assertNull(response.transaction)
    }

    @Test
    fun testConstructionWithMuxed() {
        val response = createResponse(
            accountMuxed = OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED,
            accountMuxedId = OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID,
            intoMuxed = "MINTO_MUXED",
            intoMuxedId = "999"
        )
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, response.accountMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, response.accountMuxedId)
        assertEquals("MINTO_MUXED", response.intoMuxed)
        assertEquals("999", response.intoMuxedId)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<AccountMergeOperationResponse>(response)
        assertIs<OperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("account_merge")},
            "account": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "into": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<AccountMergeOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.account)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.into)
        assertNull(response.accountMuxed)
        assertNull(response.intoMuxed)
    }

    @Test
    fun testJsonDeserializationWithMuxed() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("account_merge")},
            "account": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "into": "${OperationTestHelpers.TEST_ACCOUNT}",
            "into_muxed": "MINTO",
            "into_muxed_id": "456"
        }
        """
        val response = json.decodeFromString<AccountMergeOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, response.accountMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, response.accountMuxedId)
        assertEquals("MINTO", response.intoMuxed)
        assertEquals("456", response.intoMuxedId)
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
            ${OperationTestHelpers.baseFieldsJson("account_merge")},
            "account": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "into": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<AccountMergeOperationResponse>(response)
    }
}
