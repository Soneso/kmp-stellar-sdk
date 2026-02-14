package com.soneso.stellar.sdk.unitTests.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.effects.*
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.LINKS_JSON
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT_2
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_CREATED_AT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_PAGING_TOKEN
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.testLinks
import kotlinx.serialization.json.Json
import kotlin.test.*

class ContractEffectsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val testContractId = "CDCYWK73YTYFJZZSJ5V7EDFNHYBG4QN3VUNG2IGD27KJDDPNCZKBCBXK"

    // ==================== ContractCreditedEffectResponse ====================

    @Test
    fun testContractCreditedConstruction() {
        val effect = ContractCreditedEffectResponse(
            id = "80", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "contract_credited", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            assetType = "credit_alphanum4", assetCode = "USDC", assetIssuer = TEST_ACCOUNT_2,
            contract = testContractId, amount = "500.0000000"
        )
        assertEquals("credit_alphanum4", effect.assetType)
        assertEquals("USDC", effect.assetCode)
        assertEquals(TEST_ACCOUNT_2, effect.assetIssuer)
        assertEquals(testContractId, effect.contract)
        assertEquals("500.0000000", effect.amount)
    }

    @Test
    fun testContractCreditedNativeAsset() {
        val effect = ContractCreditedEffectResponse(
            id = "80", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "contract_credited", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            assetType = "native", contract = testContractId, amount = "100.0"
        )
        assertNull(effect.assetCode)
        assertNull(effect.assetIssuer)
        assertEquals("native", effect.assetType)
    }

    @Test
    fun testContractCreditedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "80",
            "account": "$TEST_ACCOUNT",
            "type": "contract_credited",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "asset_type": "credit_alphanum4",
            "asset_code": "USDC",
            "asset_issuer": "$TEST_ACCOUNT_2",
            "contract": "$testContractId",
            "amount": "500.0000000"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<ContractCreditedEffectResponse>(effect)
        assertEquals("USDC", effect.assetCode)
        assertEquals(testContractId, effect.contract)
        assertEquals("500.0000000", effect.amount)
    }

    @Test
    fun testContractCreditedEquality() {
        val e1 = ContractCreditedEffectResponse("80", TEST_ACCOUNT, null, null, "contract_credited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", null, null, testContractId, "100.0")
        val e2 = ContractCreditedEffectResponse("80", TEST_ACCOUNT, null, null, "contract_credited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", null, null, testContractId, "100.0")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== ContractDebitedEffectResponse ====================

    @Test
    fun testContractDebitedConstruction() {
        val effect = ContractDebitedEffectResponse(
            id = "81", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "contract_debited", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            assetType = "credit_alphanum4", assetCode = "USDC", assetIssuer = TEST_ACCOUNT_2,
            contract = testContractId, amount = "250.0000000"
        )
        assertEquals("credit_alphanum4", effect.assetType)
        assertEquals("USDC", effect.assetCode)
        assertEquals(TEST_ACCOUNT_2, effect.assetIssuer)
        assertEquals(testContractId, effect.contract)
        assertEquals("250.0000000", effect.amount)
    }

    @Test
    fun testContractDebitedNativeAsset() {
        val effect = ContractDebitedEffectResponse(
            id = "81", account = TEST_ACCOUNT, accountMuxed = null, accountMuxedId = null,
            type = "contract_debited", createdAt = TEST_CREATED_AT,
            pagingToken = TEST_PAGING_TOKEN, links = testLinks(),
            assetType = "native", contract = testContractId, amount = "50.0"
        )
        assertNull(effect.assetCode)
        assertNull(effect.assetIssuer)
    }

    @Test
    fun testContractDebitedJsonDeserialization() {
        val jsonStr = """
        {
            "id": "81",
            "account": "$TEST_ACCOUNT",
            "type": "contract_debited",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON,
            "asset_type": "credit_alphanum4",
            "asset_code": "USDC",
            "asset_issuer": "$TEST_ACCOUNT_2",
            "contract": "$testContractId",
            "amount": "250.0000000"
        }
        """
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertIs<ContractDebitedEffectResponse>(effect)
        assertEquals("USDC", effect.assetCode)
        assertEquals(testContractId, effect.contract)
        assertEquals("250.0000000", effect.amount)
    }

    @Test
    fun testContractDebitedEquality() {
        val e1 = ContractDebitedEffectResponse("81", TEST_ACCOUNT, null, null, "contract_debited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", null, null, testContractId, "50.0")
        val e2 = ContractDebitedEffectResponse("81", TEST_ACCOUNT, null, null, "contract_debited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", null, null, testContractId, "50.0")
        assertEquals(e1, e2)
        assertEquals(e1.hashCode(), e2.hashCode())
    }

    // ==================== Type hierarchy ====================

    @Test
    fun testContractEffectsAreEffectResponse() {
        val credited = ContractCreditedEffectResponse("80", TEST_ACCOUNT, null, null, "contract_credited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", null, null, testContractId, "100.0")
        val debited = ContractDebitedEffectResponse("81", TEST_ACCOUNT, null, null, "contract_debited", TEST_CREATED_AT, TEST_PAGING_TOKEN, testLinks(), "native", null, null, testContractId, "50.0")

        assertIs<EffectResponse>(credited)
        assertIs<EffectResponse>(debited)
    }
}
