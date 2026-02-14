package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.SetTrustLineFlagsOperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class SetTrustLineFlagsOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(
        clearFlags: List<Int>? = listOf(1),
        clearFlagStrings: List<String>? = listOf("authorized"),
        setFlags: List<Int>? = listOf(2),
        setFlagStrings: List<String>? = listOf("authorized_to_maintain_liabilities")
    ) = SetTrustLineFlagsOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "set_trust_line_flags",
        links = OperationTestHelpers.testLinks(),
        assetType = "credit_alphanum4",
        assetCode = "USD",
        assetIssuer = OperationTestHelpers.TEST_ACCOUNT_2,
        clearFlags = clearFlags,
        clearFlagStrings = clearFlagStrings,
        setFlags = setFlags,
        setFlagStrings = setFlagStrings,
        trustor = OperationTestHelpers.TEST_ACCOUNT
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals("credit_alphanum4", response.assetType)
        assertEquals("USD", response.assetCode)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.assetIssuer)
        assertEquals(listOf(1), response.clearFlags)
        assertEquals(listOf("authorized"), response.clearFlagStrings)
        assertEquals(listOf(2), response.setFlags)
        assertEquals(listOf("authorized_to_maintain_liabilities"), response.setFlagStrings)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.trustor)
        assertNull(response.transaction)
    }

    @Test
    fun testNullableFlags() {
        val response = createResponse(
            clearFlags = null,
            clearFlagStrings = null,
            setFlags = null,
            setFlagStrings = null
        )
        assertNull(response.clearFlags)
        assertNull(response.clearFlagStrings)
        assertNull(response.setFlags)
        assertNull(response.setFlagStrings)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<SetTrustLineFlagsOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("set_trust_line_flags")},
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "trustor": "${OperationTestHelpers.TEST_ACCOUNT}",
            "clear_flags": [1],
            "clear_flags_s": ["authorized"],
            "set_flags": [2],
            "set_flags_s": ["authorized_to_maintain_liabilities"]
        }
        """
        val response = json.decodeFromString<SetTrustLineFlagsOperationResponse>(jsonString)
        assertEquals(listOf(1), response.clearFlags)
        assertEquals(listOf("authorized"), response.clearFlagStrings)
        assertEquals(listOf(2), response.setFlags)
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
            ${OperationTestHelpers.baseFieldsJson("set_trust_line_flags")},
            "asset_type": "credit_alphanum4",
            "asset_code": "USD",
            "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "trustor": "${OperationTestHelpers.TEST_ACCOUNT}"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<SetTrustLineFlagsOperationResponse>(response)
    }
}
