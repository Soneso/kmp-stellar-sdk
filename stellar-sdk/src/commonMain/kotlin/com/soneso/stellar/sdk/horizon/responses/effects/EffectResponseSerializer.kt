package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom polymorphic serializer for EffectResponse that preserves the "type" field as a property.
 *
 * The Horizon API uses the "type" field for both:
 * 1. Type discrimination (which subclass to deserialize)
 * 2. A regular data property that applications need
 *
 * Standard kotlinx.serialization polymorphic mechanisms don't support this dual use.
 * This custom serializer reads the "type" field to determine the subclass while preserving
 * it in the deserialized object.
 *
 * @see EffectResponse
 */
object EffectResponseSerializer : JsonContentPolymorphicSerializer<EffectResponse>(EffectResponse::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<EffectResponse> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Effect response missing 'type' field")

        return when (type) {
            // Account effects
            "account_created" -> AccountCreatedEffectResponse.serializer()
            "account_removed" -> AccountRemovedEffectResponse.serializer()
            "account_credited" -> AccountCreditedEffectResponse.serializer()
            "account_debited" -> AccountDebitedEffectResponse.serializer()
            "account_thresholds_updated" -> AccountThresholdsUpdatedEffectResponse.serializer()
            "account_home_domain_updated" -> AccountHomeDomainUpdatedEffectResponse.serializer()
            "account_flags_updated" -> AccountFlagsUpdatedEffectResponse.serializer()
            "account_inflation_destination_updated" -> AccountInflationDestinationUpdatedEffectResponse.serializer()

            // Signer effects
            "signer_created" -> SignerCreatedEffectResponse.serializer()
            "signer_removed" -> SignerRemovedEffectResponse.serializer()
            "signer_updated" -> SignerUpdatedEffectResponse.serializer()

            // Trustline effects
            "trustline_created" -> TrustlineCreatedEffectResponse.serializer()
            "trustline_removed" -> TrustlineRemovedEffectResponse.serializer()
            "trustline_updated" -> TrustlineUpdatedEffectResponse.serializer()
            "trustline_authorized" -> TrustlineAuthorizedEffectResponse.serializer()
            "trustline_deauthorized" -> TrustlineDeauthorizedEffectResponse.serializer()
            "trustline_authorized_to_maintain_liabilities" -> TrustlineAuthorizedToMaintainLiabilitiesEffectResponse.serializer()
            "trustline_flags_updated" -> TrustlineFlagsUpdatedEffectResponse.serializer()

            // Trade effects
            "trade" -> TradeEffectResponse.serializer()

            // Offer effects
            "offer_created" -> OfferCreatedEffectResponse.serializer()
            "offer_removed" -> OfferRemovedEffectResponse.serializer()
            "offer_updated" -> OfferUpdatedEffectResponse.serializer()

            // Data effects
            "data_created" -> DataCreatedEffectResponse.serializer()
            "data_removed" -> DataRemovedEffectResponse.serializer()
            "data_updated" -> DataUpdatedEffectResponse.serializer()

            // Sequence effects
            "sequence_bumped" -> SequenceBumpedEffectResponse.serializer()

            // Sponsorship effects - Account
            "account_sponsorship_created" -> AccountSponsorshipCreatedEffectResponse.serializer()
            "account_sponsorship_updated" -> AccountSponsorshipUpdatedEffectResponse.serializer()
            "account_sponsorship_removed" -> AccountSponsorshipRemovedEffectResponse.serializer()

            // Sponsorship effects - Trustline
            "trustline_sponsorship_created" -> TrustlineSponsorshipCreatedEffectResponse.serializer()
            "trustline_sponsorship_updated" -> TrustlineSponsorshipUpdatedEffectResponse.serializer()
            "trustline_sponsorship_removed" -> TrustlineSponsorshipRemovedEffectResponse.serializer()

            // Sponsorship effects - Data
            "data_sponsorship_created" -> DataSponsorshipCreatedEffectResponse.serializer()
            "data_sponsorship_updated" -> DataSponsorshipUpdatedEffectResponse.serializer()
            "data_sponsorship_removed" -> DataSponsorshipRemovedEffectResponse.serializer()

            // Sponsorship effects - Signer
            "signer_sponsorship_created" -> SignerSponsorshipCreatedEffectResponse.serializer()
            "signer_sponsorship_updated" -> SignerSponsorshipUpdatedEffectResponse.serializer()
            "signer_sponsorship_removed" -> SignerSponsorshipRemovedEffectResponse.serializer()

            // Claimable balance effects
            "claimable_balance_created" -> ClaimableBalanceCreatedEffectResponse.serializer()
            "claimable_balance_claimant_created" -> ClaimableBalanceClaimantCreatedEffectResponse.serializer()
            "claimable_balance_claimed" -> ClaimableBalanceClaimedEffectResponse.serializer()
            "claimable_balance_clawed_back" -> ClaimableBalanceClawedBackEffectResponse.serializer()

            // Sponsorship effects - Claimable balance
            "claimable_balance_sponsorship_created" -> ClaimableBalanceSponsorshipCreatedEffectResponse.serializer()
            "claimable_balance_sponsorship_updated" -> ClaimableBalanceSponsorshipUpdatedEffectResponse.serializer()
            "claimable_balance_sponsorship_removed" -> ClaimableBalanceSponsorshipRemovedEffectResponse.serializer()

            // Liquidity pool effects
            "liquidity_pool_deposited" -> LiquidityPoolDepositedEffectResponse.serializer()
            "liquidity_pool_withdrew" -> LiquidityPoolWithdrewEffectResponse.serializer()
            "liquidity_pool_trade" -> LiquidityPoolTradeEffectResponse.serializer()
            "liquidity_pool_created" -> LiquidityPoolCreatedEffectResponse.serializer()
            "liquidity_pool_removed" -> LiquidityPoolRemovedEffectResponse.serializer()
            "liquidity_pool_revoked" -> LiquidityPoolRevokedEffectResponse.serializer()

            // Contract effects (Soroban)
            "contract_credited" -> ContractCreditedEffectResponse.serializer()
            "contract_debited" -> ContractDebitedEffectResponse.serializer()

            else -> throw IllegalArgumentException("Unknown effect type: $type")
        }
    }
}
