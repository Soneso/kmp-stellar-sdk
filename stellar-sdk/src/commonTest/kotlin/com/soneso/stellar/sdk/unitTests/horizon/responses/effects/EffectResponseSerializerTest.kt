@file:Suppress("DEPRECATION")

package com.soneso.stellar.sdk.unitTests.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.effects.*
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.LINKS_JSON
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT_2
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_ACCOUNT_3
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_CREATED_AT
import com.soneso.stellar.sdk.unitTests.horizon.responses.effects.EffectTestHelpers.TEST_PAGING_TOKEN
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Tests the EffectResponseSerializer polymorphic dispatch for all 55 effect types
 * plus error cases.
 */
class EffectResponseSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun buildJson(type: String, extraFields: String = ""): String {
        val extra = if (extraFields.isNotEmpty()) ", $extraFields" else ""
        return """
        {
            "id": "1",
            "account": "$TEST_ACCOUNT",
            "type": "$type",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON
            $extra
        }
        """
    }

    // ==================== Account effects ====================

    @Test
    fun testAccountCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_created", """"starting_balance": "10000.0""""))
        assertIs<AccountCreatedEffectResponse>(result)
    }

    @Test
    fun testAccountRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_removed"))
        assertIs<AccountRemovedEffectResponse>(result)
    }

    @Test
    fun testAccountCreditedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_credited", """"amount": "100.0", "asset_type": "native""""))
        assertIs<AccountCreditedEffectResponse>(result)
    }

    @Test
    fun testAccountDebitedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_debited", """"amount": "100.0", "asset_type": "native""""))
        assertIs<AccountDebitedEffectResponse>(result)
    }

    @Test
    fun testAccountThresholdsUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_thresholds_updated", """"low_threshold": 1, "med_threshold": 2, "high_threshold": 3"""))
        assertIs<AccountThresholdsUpdatedEffectResponse>(result)
    }

    @Test
    fun testAccountHomeDomainUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_home_domain_updated", """"home_domain": "stellar.org""""))
        assertIs<AccountHomeDomainUpdatedEffectResponse>(result)
    }

    @Test
    fun testAccountFlagsUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_flags_updated"))
        assertIs<AccountFlagsUpdatedEffectResponse>(result)
    }

    @Test
    fun testAccountInflationDestinationUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_inflation_destination_updated"))
        assertIs<AccountInflationDestinationUpdatedEffectResponse>(result)
    }

    // ==================== Signer effects ====================

    @Test
    fun testSignerCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("signer_created", """"weight": 1, "public_key": "$TEST_ACCOUNT_2""""))
        assertIs<SignerCreatedEffectResponse>(result)
    }

    @Test
    fun testSignerRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("signer_removed", """"weight": 0, "public_key": "$TEST_ACCOUNT_2""""))
        assertIs<SignerRemovedEffectResponse>(result)
    }

    @Test
    fun testSignerUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("signer_updated", """"weight": 3, "public_key": "$TEST_ACCOUNT_2""""))
        assertIs<SignerUpdatedEffectResponse>(result)
    }

    // ==================== Trustline effects ====================

    @Test
    fun testTrustlineCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("trustline_created", """"limit": "1000.0", "asset_type": "native""""))
        assertIs<TrustlineCreatedEffectResponse>(result)
    }

    @Test
    fun testTrustlineRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("trustline_removed", """"limit": "0.0", "asset_type": "native""""))
        assertIs<TrustlineRemovedEffectResponse>(result)
    }

    @Test
    fun testTrustlineUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("trustline_updated", """"limit": "5000.0", "asset_type": "native""""))
        assertIs<TrustlineUpdatedEffectResponse>(result)
    }

    @Test
    fun testTrustlineAuthorizedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("trustline_authorized", """"trustor": "$TEST_ACCOUNT_2", "asset_type": "credit_alphanum4""""))
        assertIs<TrustlineAuthorizedEffectResponse>(result)
    }

    @Test
    fun testTrustlineDeauthorizedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("trustline_deauthorized", """"trustor": "$TEST_ACCOUNT_2", "asset_type": "credit_alphanum4""""))
        assertIs<TrustlineDeauthorizedEffectResponse>(result)
    }

    @Test
    fun testTrustlineAuthorizedToMaintainLiabilitiesDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("trustline_authorized_to_maintain_liabilities", """"trustor": "$TEST_ACCOUNT_2", "asset_type": "credit_alphanum4""""))
        assertIs<TrustlineAuthorizedToMaintainLiabilitiesEffectResponse>(result)
    }

    @Test
    fun testTrustlineFlagsUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("trustline_flags_updated", """"trustor": "$TEST_ACCOUNT_2", "asset_type": "credit_alphanum4""""))
        assertIs<TrustlineFlagsUpdatedEffectResponse>(result)
    }

    // ==================== Trade effect ====================

    @Test
    fun testTradeDispatch() {
        val extra = """"seller": "$TEST_ACCOUNT_2", "offer_id": 123, "sold_amount": "10.0", "sold_asset_type": "native", "bought_amount": "5.0", "bought_asset_type": "native""""
        val result = json.decodeFromString<EffectResponse>(buildJson("trade", extra))
        assertIs<TradeEffectResponse>(result)
    }

    // ==================== Offer effects ====================

    @Test
    fun testOfferCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("offer_created"))
        assertIs<OfferCreatedEffectResponse>(result)
    }

    @Test
    fun testOfferRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("offer_removed"))
        assertIs<OfferRemovedEffectResponse>(result)
    }

    @Test
    fun testOfferUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("offer_updated"))
        assertIs<OfferUpdatedEffectResponse>(result)
    }

    // ==================== Data effects ====================

    @Test
    fun testDataCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("data_created", """"name": "k", "value": "v""""))
        assertIs<DataCreatedEffectResponse>(result)
    }

    @Test
    fun testDataRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("data_removed", """"name": "k""""))
        assertIs<DataRemovedEffectResponse>(result)
    }

    @Test
    fun testDataUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("data_updated", """"name": "k", "value": "v""""))
        assertIs<DataUpdatedEffectResponse>(result)
    }

    // ==================== Sequence effect ====================

    @Test
    fun testSequenceBumpedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("sequence_bumped", """"new_seq": 12345"""))
        assertIs<SequenceBumpedEffectResponse>(result)
    }

    // ==================== Account sponsorship effects ====================

    @Test
    fun testAccountSponsorshipCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_sponsorship_created", """"sponsor": "$TEST_ACCOUNT_2""""))
        assertIs<AccountSponsorshipCreatedEffectResponse>(result)
    }

    @Test
    fun testAccountSponsorshipUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_sponsorship_updated", """"former_sponsor": "$TEST_ACCOUNT_2", "new_sponsor": "$TEST_ACCOUNT_3""""))
        assertIs<AccountSponsorshipUpdatedEffectResponse>(result)
    }

    @Test
    fun testAccountSponsorshipRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("account_sponsorship_removed", """"former_sponsor": "$TEST_ACCOUNT_2""""))
        assertIs<AccountSponsorshipRemovedEffectResponse>(result)
    }

    // ==================== Trustline sponsorship effects ====================

    @Test
    fun testTrustlineSponsorshipCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("trustline_sponsorship_created", """"asset_type": "native", "sponsor": "$TEST_ACCOUNT_2""""))
        assertIs<TrustlineSponsorshipCreatedEffectResponse>(result)
    }

    @Test
    fun testTrustlineSponsorshipUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("trustline_sponsorship_updated", """"asset_type": "native", "former_sponsor": "$TEST_ACCOUNT_2", "new_sponsor": "$TEST_ACCOUNT_3""""))
        assertIs<TrustlineSponsorshipUpdatedEffectResponse>(result)
    }

    @Test
    fun testTrustlineSponsorshipRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("trustline_sponsorship_removed", """"asset_type": "native", "former_sponsor": "$TEST_ACCOUNT_2""""))
        assertIs<TrustlineSponsorshipRemovedEffectResponse>(result)
    }

    // ==================== Data sponsorship effects ====================

    @Test
    fun testDataSponsorshipCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("data_sponsorship_created", """"sponsor": "$TEST_ACCOUNT_2", "data_name": "k""""))
        assertIs<DataSponsorshipCreatedEffectResponse>(result)
    }

    @Test
    fun testDataSponsorshipUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("data_sponsorship_updated", """"former_sponsor": "$TEST_ACCOUNT_2", "new_sponsor": "$TEST_ACCOUNT_3", "data_name": "k""""))
        assertIs<DataSponsorshipUpdatedEffectResponse>(result)
    }

    @Test
    fun testDataSponsorshipRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("data_sponsorship_removed", """"former_sponsor": "$TEST_ACCOUNT_2", "data_name": "k""""))
        assertIs<DataSponsorshipRemovedEffectResponse>(result)
    }

    // ==================== Signer sponsorship effects ====================

    @Test
    fun testSignerSponsorshipCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("signer_sponsorship_created", """"sponsor": "$TEST_ACCOUNT_2", "signer": "$TEST_ACCOUNT_3""""))
        assertIs<SignerSponsorshipCreatedEffectResponse>(result)
    }

    @Test
    fun testSignerSponsorshipUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("signer_sponsorship_updated", """"former_sponsor": "$TEST_ACCOUNT_2", "new_sponsor": "$TEST_ACCOUNT_3", "signer": "$TEST_ACCOUNT""""))
        assertIs<SignerSponsorshipUpdatedEffectResponse>(result)
    }

    @Test
    fun testSignerSponsorshipRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("signer_sponsorship_removed", """"former_sponsor": "$TEST_ACCOUNT_2", "signer": "$TEST_ACCOUNT_3""""))
        assertIs<SignerSponsorshipRemovedEffectResponse>(result)
    }

    // ==================== Claimable balance effects ====================

    @Test
    fun testClaimableBalanceCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("claimable_balance_created", """"asset": "native", "amount": "100.0", "balance_id": "bid1""""))
        assertIs<ClaimableBalanceCreatedEffectResponse>(result)
    }

    @Test
    fun testClaimableBalanceClaimantCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("claimable_balance_claimant_created", """"asset": "native", "amount": "100.0", "balance_id": "bid1""""))
        assertIs<ClaimableBalanceClaimantCreatedEffectResponse>(result)
    }

    @Test
    fun testClaimableBalanceClaimedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("claimable_balance_claimed", """"asset": "native", "amount": "100.0", "balance_id": "bid1""""))
        assertIs<ClaimableBalanceClaimedEffectResponse>(result)
    }

    @Test
    fun testClaimableBalanceClawedBackDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("claimable_balance_clawed_back", """"balance_id": "bid1""""))
        assertIs<ClaimableBalanceClawedBackEffectResponse>(result)
    }

    // ==================== Claimable balance sponsorship effects ====================

    @Test
    fun testClaimableBalanceSponsorshipCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("claimable_balance_sponsorship_created", """"sponsor": "$TEST_ACCOUNT_2", "balance_id": "bid1""""))
        assertIs<ClaimableBalanceSponsorshipCreatedEffectResponse>(result)
    }

    @Test
    fun testClaimableBalanceSponsorshipUpdatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("claimable_balance_sponsorship_updated", """"former_sponsor": "$TEST_ACCOUNT_2", "new_sponsor": "$TEST_ACCOUNT_3", "balance_id": "bid1""""))
        assertIs<ClaimableBalanceSponsorshipUpdatedEffectResponse>(result)
    }

    @Test
    fun testClaimableBalanceSponsorshipRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("claimable_balance_sponsorship_removed", """"former_sponsor": "$TEST_ACCOUNT_2", "balance_id": "bid1""""))
        assertIs<ClaimableBalanceSponsorshipRemovedEffectResponse>(result)
    }

    // ==================== Liquidity pool effects ====================

    private val poolJson = """
        "liquidity_pool": {
            "id": "pool1",
            "fee_bp": 30,
            "type": "constant_product",
            "total_trustlines": 10,
            "total_shares": "100.0",
            "reserves": [
                { "asset": "native", "amount": "500.0" },
                { "asset": "USD:$TEST_ACCOUNT_2", "amount": "250.0" }
            ]
        }
    """

    @Test
    fun testLiquidityPoolDepositedDispatch() {
        val extra = """$poolJson, "reserves_deposited": [{ "asset": "native", "amount": "10.0" }], "shares_received": "7.0""""
        val result = json.decodeFromString<EffectResponse>(buildJson("liquidity_pool_deposited", extra))
        assertIs<LiquidityPoolDepositedEffectResponse>(result)
    }

    @Test
    fun testLiquidityPoolWithdrewDispatch() {
        val extra = """$poolJson, "reserves_received": [{ "asset": "native", "amount": "10.0" }], "shares_redeemed": "7.0""""
        val result = json.decodeFromString<EffectResponse>(buildJson("liquidity_pool_withdrew", extra))
        assertIs<LiquidityPoolWithdrewEffectResponse>(result)
    }

    @Test
    fun testLiquidityPoolTradeDispatch() {
        val extra = """$poolJson, "sold": { "asset": "native", "amount": "10.0" }, "bought": { "asset": "USD", "amount": "5.0" }"""
        val result = json.decodeFromString<EffectResponse>(buildJson("liquidity_pool_trade", extra))
        assertIs<LiquidityPoolTradeEffectResponse>(result)
    }

    @Test
    fun testLiquidityPoolCreatedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("liquidity_pool_created", poolJson))
        assertIs<LiquidityPoolCreatedEffectResponse>(result)
    }

    @Test
    fun testLiquidityPoolRemovedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("liquidity_pool_removed", """"liquidity_pool_id": "pool1""""))
        assertIs<LiquidityPoolRemovedEffectResponse>(result)
    }

    @Test
    fun testLiquidityPoolRevokedDispatch() {
        val extra = """$poolJson, "reserves_revoked": [{ "asset": "native", "amount": "10.0", "claimable_balance_id": "cb1" }], "shares_revoked": "7.0""""
        val result = json.decodeFromString<EffectResponse>(buildJson("liquidity_pool_revoked", extra))
        assertIs<LiquidityPoolRevokedEffectResponse>(result)
    }

    // ==================== Contract effects ====================

    @Test
    fun testContractCreditedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("contract_credited", """"asset_type": "native", "contract": "C123", "amount": "100.0""""))
        assertIs<ContractCreditedEffectResponse>(result)
    }

    @Test
    fun testContractDebitedDispatch() {
        val result = json.decodeFromString<EffectResponse>(buildJson("contract_debited", """"asset_type": "native", "contract": "C123", "amount": "100.0""""))
        assertIs<ContractDebitedEffectResponse>(result)
    }

    // ==================== Error cases ====================

    @Test
    fun testUnknownTypeThrows() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<EffectResponse>(buildJson("unknown_effect_type"))
        }
    }

    @Test
    fun testMissingTypeThrows() {
        val jsonStr = """
        {
            "id": "1",
            "account": "$TEST_ACCOUNT",
            "created_at": "$TEST_CREATED_AT",
            "paging_token": "$TEST_PAGING_TOKEN",
            $LINKS_JSON
        }
        """
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<EffectResponse>(jsonStr)
        }
    }

    // ==================== Pageable interface ====================

    @Test
    fun testEffectImplementsPageable() {
        val jsonStr = buildJson("account_created", """"starting_balance": "10000.0"""")
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertEquals(TEST_PAGING_TOKEN, effect.pagingToken)
    }

    // ==================== EffectLinks ====================

    @Test
    fun testEffectLinksDeserialization() {
        val jsonStr = buildJson("account_created", """"starting_balance": "10000.0"""")
        val effect = json.decodeFromString<EffectResponse>(jsonStr)
        assertEquals("https://horizon.stellar.org/operations/12345", effect.links.operation.href)
        assertEquals(false, effect.links.operation.templated)
        assertNotNull(effect.links.precedes)
        assertNotNull(effect.links.succeeds)
    }

    @Test
    fun testEffectLinksEquality() {
        val links1 = EffectTestHelpers.testLinks()
        val links2 = EffectTestHelpers.testLinks()
        assertEquals(links1, links2)
        assertEquals(links1.hashCode(), links2.hashCode())
    }
}
