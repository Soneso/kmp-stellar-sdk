package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.SetOptionsOperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class SetOptionsOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(
        lowThreshold: Int? = 1,
        medThreshold: Int? = 2,
        highThreshold: Int? = 3,
        inflationDestination: String? = OperationTestHelpers.TEST_ACCOUNT_2,
        homeDomain: String? = "stellar.org",
        signerKey: String? = OperationTestHelpers.TEST_ACCOUNT,
        signerWeight: Int? = 1,
        masterKeyWeight: Int? = 10,
        clearFlags: List<Int>? = listOf(1),
        clearFlagStrings: List<String>? = listOf("auth_required"),
        setFlags: List<Int>? = listOf(2),
        setFlagStrings: List<String>? = listOf("auth_revocable")
    ) = SetOptionsOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "set_options",
        links = OperationTestHelpers.testLinks(),
        lowThreshold = lowThreshold,
        medThreshold = medThreshold,
        highThreshold = highThreshold,
        inflationDestination = inflationDestination,
        homeDomain = homeDomain,
        signerKey = signerKey,
        signerWeight = signerWeight,
        masterKeyWeight = masterKeyWeight,
        clearFlags = clearFlags,
        clearFlagStrings = clearFlagStrings,
        setFlags = setFlags,
        setFlagStrings = setFlagStrings
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals(1, response.lowThreshold)
        assertEquals(2, response.medThreshold)
        assertEquals(3, response.highThreshold)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT_2, response.inflationDestination)
        assertEquals("stellar.org", response.homeDomain)
        assertEquals(OperationTestHelpers.TEST_ACCOUNT, response.signerKey)
        assertEquals(1, response.signerWeight)
        assertEquals(10, response.masterKeyWeight)
        assertEquals(listOf(1), response.clearFlags)
        assertEquals(listOf("auth_required"), response.clearFlagStrings)
        assertEquals(listOf(2), response.setFlags)
        assertEquals(listOf("auth_revocable"), response.setFlagStrings)
        assertNull(response.transaction)
    }

    @Test
    fun testAllNullableFieldsNull() {
        val response = createResponse(
            lowThreshold = null,
            medThreshold = null,
            highThreshold = null,
            inflationDestination = null,
            homeDomain = null,
            signerKey = null,
            signerWeight = null,
            masterKeyWeight = null,
            clearFlags = null,
            clearFlagStrings = null,
            setFlags = null,
            setFlagStrings = null
        )
        assertNull(response.lowThreshold)
        assertNull(response.medThreshold)
        assertNull(response.highThreshold)
        assertNull(response.inflationDestination)
        assertNull(response.homeDomain)
        assertNull(response.signerKey)
        assertNull(response.signerWeight)
        assertNull(response.masterKeyWeight)
        assertNull(response.clearFlags)
        assertNull(response.clearFlagStrings)
        assertNull(response.setFlags)
        assertNull(response.setFlagStrings)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<SetOptionsOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("set_options")},
            "low_threshold": 1,
            "med_threshold": 2,
            "high_threshold": 3,
            "inflation_dest": "${OperationTestHelpers.TEST_ACCOUNT_2}",
            "home_domain": "stellar.org",
            "signer_key": "${OperationTestHelpers.TEST_ACCOUNT}",
            "signer_weight": 1,
            "master_key_weight": 10,
            "clear_flags": [1],
            "clear_flags_s": ["auth_required"],
            "set_flags": [2],
            "set_flags_s": ["auth_revocable"]
        }
        """
        val response = json.decodeFromString<SetOptionsOperationResponse>(jsonString)
        assertEquals(1, response.lowThreshold)
        assertEquals(2, response.medThreshold)
        assertEquals(3, response.highThreshold)
        assertEquals("stellar.org", response.homeDomain)
        assertEquals(10, response.masterKeyWeight)
    }

    @Test
    fun testJsonMinimalDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("set_options")}
        }
        """
        val response = json.decodeFromString<SetOptionsOperationResponse>(jsonString)
        assertNull(response.lowThreshold)
        assertNull(response.homeDomain)
        assertNull(response.signerKey)
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
            ${OperationTestHelpers.baseFieldsJson("set_options")},
            "home_domain": "stellar.org"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<SetOptionsOperationResponse>(response)
    }
}
