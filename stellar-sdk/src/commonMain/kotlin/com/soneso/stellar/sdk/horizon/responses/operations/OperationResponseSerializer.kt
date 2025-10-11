package com.soneso.stellar.sdk.horizon.responses.operations

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom polymorphic serializer for OperationResponse that preserves the "type" field as a property.
 *
 * The Horizon API uses the "type" field for both:
 * 1. Type discrimination (which subclass to deserialize)
 * 2. A regular data property that applications need
 *
 * Standard kotlinx.serialization polymorphic mechanisms don't support this dual use.
 * This custom serializer reads the "type" field to determine the subclass while preserving
 * it in the deserialized object.
 *
 * @see OperationResponse
 */
object OperationResponseSerializer : JsonContentPolymorphicSerializer<OperationResponse>(OperationResponse::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<OperationResponse> {
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Operation response missing 'type' field")

        return when (type) {
            // Account operations
            "create_account" -> CreateAccountOperationResponse.serializer()
            "account_merge" -> AccountMergeOperationResponse.serializer()

            // Payment operations
            "payment" -> PaymentOperationResponse.serializer()
            "path_payment_strict_receive" -> PathPaymentStrictReceiveOperationResponse.serializer()
            "path_payment_strict_send" -> PathPaymentStrictSendOperationResponse.serializer()

            // Trustline operations
            "change_trust" -> ChangeTrustOperationResponse.serializer()
            "allow_trust" -> AllowTrustOperationResponse.serializer()
            "set_trust_line_flags" -> SetTrustLineFlagsOperationResponse.serializer()

            // Offer operations
            "manage_sell_offer" -> ManageSellOfferOperationResponse.serializer()
            "manage_buy_offer" -> ManageBuyOfferOperationResponse.serializer()
            "create_passive_sell_offer" -> CreatePassiveSellOfferOperationResponse.serializer()

            // Account configuration operations
            "set_options" -> SetOptionsOperationResponse.serializer()
            "manage_data" -> ManageDataOperationResponse.serializer()
            "bump_sequence" -> BumpSequenceOperationResponse.serializer()

            // Claimable balance operations
            "create_claimable_balance" -> CreateClaimableBalanceOperationResponse.serializer()
            "claim_claimable_balance" -> ClaimClaimableBalanceOperationResponse.serializer()
            "clawback_claimable_balance" -> ClawbackClaimableBalanceOperationResponse.serializer()

            // Clawback operations
            "clawback" -> ClawbackOperationResponse.serializer()

            // Sponsorship operations
            "begin_sponsoring_future_reserves" -> BeginSponsoringFutureReservesOperationResponse.serializer()
            "end_sponsoring_future_reserves" -> EndSponsoringFutureReservesOperationResponse.serializer()
            "revoke_sponsorship" -> RevokeSponsorshipOperationResponse.serializer()

            // Liquidity pool operations
            "liquidity_pool_deposit" -> LiquidityPoolDepositOperationResponse.serializer()
            "liquidity_pool_withdraw" -> LiquidityPoolWithdrawOperationResponse.serializer()

            // Soroban operations
            "invoke_host_function" -> InvokeHostFunctionOperationResponse.serializer()
            "extend_footprint_ttl" -> ExtendFootprintTTLOperationResponse.serializer()
            "restore_footprint" -> RestoreFootprintOperationResponse.serializer()

            // Deprecated operations
            "inflation" -> InflationOperationResponse.serializer()

            else -> throw IllegalArgumentException("Unknown operation type: $type")
        }
    }
}
