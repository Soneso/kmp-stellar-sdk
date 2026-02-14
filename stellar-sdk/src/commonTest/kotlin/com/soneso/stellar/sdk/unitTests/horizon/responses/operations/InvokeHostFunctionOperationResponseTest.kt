package com.soneso.stellar.sdk.unitTests.horizon.responses.operations

import com.soneso.stellar.sdk.horizon.responses.operations.InvokeHostFunctionOperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.InvokeHostFunctionOperationResponse.AssetContractBalanceChange
import com.soneso.stellar.sdk.horizon.responses.operations.InvokeHostFunctionOperationResponse.HostFunctionParameter
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import kotlinx.serialization.json.Json
import kotlin.test.*

class InvokeHostFunctionOperationResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun createResponse(
        parameters: List<HostFunctionParameter>? = listOf(
            HostFunctionParameter(type = "Address", value = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN7")
        ),
        address: String? = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
        salt: String? = "abc123",
        assetBalanceChanges: List<AssetContractBalanceChange>? = null
    ) = InvokeHostFunctionOperationResponse(
        id = OperationTestHelpers.TEST_ID,
        sourceAccount = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
        pagingToken = OperationTestHelpers.TEST_PAGING_TOKEN,
        createdAt = OperationTestHelpers.TEST_CREATED_AT,
        transactionHash = OperationTestHelpers.TEST_TX_HASH,
        transactionSuccessful = true,
        type = "invoke_host_function",
        links = OperationTestHelpers.testLinks(),
        function = "HostFunctionTypeInvokeContract",
        parameters = parameters,
        address = address,
        salt = salt,
        assetBalanceChanges = assetBalanceChanges
    )

    @Test
    fun testConstruction() {
        val response = createResponse()
        assertEquals("HostFunctionTypeInvokeContract", response.function)
        assertNotNull(response.parameters)
        assertEquals(1, response.parameters!!.size)
        assertEquals("Address", response.parameters!![0].type)
        assertEquals("CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC", response.address)
        assertEquals("abc123", response.salt)
        assertNull(response.assetBalanceChanges)
        assertNull(response.transaction)
    }

    @Test
    fun testWithAssetBalanceChanges() {
        val changes = listOf(
            AssetContractBalanceChange(
                assetType = "credit_alphanum4",
                assetCode = "USD",
                assetIssuer = OperationTestHelpers.TEST_ACCOUNT_2,
                type = "transfer",
                from = OperationTestHelpers.TEST_SOURCE_ACCOUNT,
                to = OperationTestHelpers.TEST_ACCOUNT,
                amount = "100.0000000"
            )
        )
        val response = createResponse(assetBalanceChanges = changes)
        assertNotNull(response.assetBalanceChanges)
        assertEquals(1, response.assetBalanceChanges!!.size)
        assertEquals("transfer", response.assetBalanceChanges!![0].type)
        assertEquals("100.0000000", response.assetBalanceChanges!![0].amount)
        assertNull(response.assetBalanceChanges!![0].destinationMuxedId)
    }

    @Test
    fun testAllNullOptionals() {
        val response = createResponse(
            parameters = null,
            address = null,
            salt = null,
            assetBalanceChanges = null
        )
        assertNull(response.parameters)
        assertNull(response.address)
        assertNull(response.salt)
        assertNull(response.assetBalanceChanges)
    }

    @Test
    fun testTypeHierarchy() {
        val response: OperationResponse = createResponse()
        assertIs<InvokeHostFunctionOperationResponse>(response)
    }

    @Test
    fun testJsonDeserialization() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("invoke_host_function")},
            "function": "HostFunctionTypeInvokeContract",
            "parameters": [
                {"type": "Address", "value": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}"}
            ],
            "address": "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            "salt": "abc123"
        }
        """
        val response = json.decodeFromString<InvokeHostFunctionOperationResponse>(jsonString)
        assertEquals("HostFunctionTypeInvokeContract", response.function)
        assertNotNull(response.parameters)
        assertEquals(1, response.parameters!!.size)
        assertEquals("CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC", response.address)
    }

    @Test
    fun testJsonWithAssetBalanceChanges() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("invoke_host_function")},
            "function": "HostFunctionTypeInvokeContract",
            "asset_balance_changes": [
                {
                    "asset_type": "credit_alphanum4",
                    "asset_code": "USD",
                    "asset_issuer": "${OperationTestHelpers.TEST_ACCOUNT_2}",
                    "type": "transfer",
                    "from": "${OperationTestHelpers.TEST_SOURCE_ACCOUNT}",
                    "to": "${OperationTestHelpers.TEST_ACCOUNT}",
                    "amount": "50.0000000"
                }
            ]
        }
        """
        val response = json.decodeFromString<InvokeHostFunctionOperationResponse>(jsonString)
        assertNotNull(response.assetBalanceChanges)
        assertEquals(1, response.assetBalanceChanges!!.size)
        assertEquals("transfer", response.assetBalanceChanges!![0].type)
        assertEquals("50.0000000", response.assetBalanceChanges!![0].amount)
    }

    @Test
    fun testJsonMinimal() {
        val jsonString = """
        {
            ${OperationTestHelpers.baseFieldsJson("invoke_host_function")},
            "function": "HostFunctionTypeInvokeContract"
        }
        """
        val response = json.decodeFromString<InvokeHostFunctionOperationResponse>(jsonString)
        assertEquals("HostFunctionTypeInvokeContract", response.function)
        assertNull(response.parameters)
        assertNull(response.address)
        assertNull(response.salt)
        assertNull(response.assetBalanceChanges)
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
            ${OperationTestHelpers.baseFieldsJson("invoke_host_function")},
            "function": "HostFunctionTypeInvokeContract"
        }
        """
        val response = json.decodeFromString<OperationResponse>(jsonString)
        assertIs<InvokeHostFunctionOperationResponse>(response)
    }
}
