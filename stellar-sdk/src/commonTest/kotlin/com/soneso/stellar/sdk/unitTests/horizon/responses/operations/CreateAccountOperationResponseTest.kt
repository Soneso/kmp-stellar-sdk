package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.CreateAccountOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class CreateAccountOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(
        funderMuxed: String? = null,
        funderMuxedId: String? = null,
        sourceAccountMuxed: String? = null,
        sourceAccountMuxedId: String? = null
    ) = CreateAccountOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        sourceAccountMuxed = sourceAccountMuxed,
        sourceAccountMuxedId = sourceAccountMuxedId,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "create_account",
        links = OperationTestHelpers.testLinks(),
        account = OperationTestHelpers.TEST_ACCOUNT,
        funder = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        funderMuxed = funderMuxed,
        funderMuxedId = funderMuxedId,
        startingBalance = "100.0000000"
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(OperationTestHelpers.TEST_ID, response.id)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.sourceAccount)
        assertNull(response.sourceAccountMuxed)
        assertNull(response.sourceAccountMuxedId)
        assertEquals(OperationTestHelpers.TEST_PAGING_TOKEN, response.pagingToken)
        assertEquals(OperationTestHelpers.TEST_CREATED_AT, response.createdAt)
        assertEquals(OperationTestHelpers.TEST_TX_HASH, response.transactionHash)
        assertTrue(response.transactionSuccessful)
        assertEquals("create_account", response.type)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.account)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.funder)
        assertNull(response.funderMuxed)
        assertNull(response.funderMuxedId)
        assertEquals("100.0000000", response.startingBalance)
        assertNull(response.transaction)
    }

    @Test
    fun testConstructionWithMuxedFields() {
        val response = createResponse(
            funderMuxed = OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED,
            funderMuxedId = OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID,
            sourceAccountMuxed = OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED,
            sourceAccountMuxedId = OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID
        )
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, response.funderMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, response.funderMuxedId)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, response.sourceAccountMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, response.sourceAccountMuxedId)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<CreateAccountOperationResponse>(response)
        assertIs<OperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("create_account")},
            "account": "${OperationTestHelpers.TEST_ACCOUNT}",
            "funder": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "starting_balance": "100.0000000"
        }
        """
        val response = json.decodeFromString<CreateAccountOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.account)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT, response.funder)
        assertEquals("100.0000000", response.startingBalance)
        assertEquals("create_account", response.type)
    }

    @Test
    fun testJsonDeserializationWithMuxedFields() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("create_account")},
            "source_account_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "source_account_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "account": "${OperationTestHelpers.TEST_ACCOUNT}",
            "funder": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "funder_muxed": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED}",
            "funder_muxed_id": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID}",
            "starting_balance": "50.0000000"
        }
        """
        val response = json.decodeFromString<CreateAccountOperationResponse>(jsonString)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, response.sourceAccountMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, response.sourceAccountMuxedId)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED, response.funderMuxed)
        assertEquals(OperationTestHelpers.TEST_SOURCE_ACCOUNT_MUXED_ID, response.funderMuxedId)
    }

    @Test
    fun testEqualsAndHashCode() {
        val r1 = createResponse()
        val r2 = createResponse()
        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())

        val r3 = createResponse(funderMuxed = "different")
        assertNotEquals(r1, r3)
    }

    @Test
    fun testPolymorphicDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("create_account")},
            "account": "${OperationTestHelpers.TEST_ACCOUNT}",
            "funder": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
            "starting_balance": "100.0000000"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<CreateAccountOperationResponse>(response)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, (response as CreateAccountOperationResponse).account)
    }
}
