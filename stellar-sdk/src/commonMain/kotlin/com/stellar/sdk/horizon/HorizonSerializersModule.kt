package com.stellar.sdk.horizon

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import com.stellar.sdk.horizon.responses.effects.*
import com.stellar.sdk.horizon.responses.operations.*

/**
 * SerializersModule for polymorphic serialization of Horizon API responses.
 *
 * This module registers all EffectResponse and OperationResponse subtypes
 * to enable automatic polymorphic deserialization based on the "type" field.
 *
 * @deprecated This module is no longer needed as of version X.X.X.
 * EffectResponse and OperationResponse now use custom JsonContentPolymorphicSerializer
 * implementations that preserve the "type" field as both a discriminator and a property.
 * This module is kept for backward compatibility but will be removed in a future release.
 *
 * @see com.stellar.sdk.horizon.responses.effects.EffectResponseSerializer
 * @see com.stellar.sdk.horizon.responses.operations.OperationResponseSerializer
 */
@Deprecated(
    message = "No longer needed. EffectResponse and OperationResponse use custom serializers.",
    level = DeprecationLevel.WARNING
)
val HorizonSerializersModule = SerializersModule {
    polymorphic(EffectResponse::class) {
        // Account effects
        subclass(AccountCreatedEffectResponse::class)
        subclass(AccountCreditedEffectResponse::class)
        subclass(AccountDebitedEffectResponse::class)
        subclass(AccountFlagsUpdatedEffectResponse::class)
        subclass(AccountHomeDomainUpdatedEffectResponse::class)
        subclass(AccountInflationDestinationUpdatedEffectResponse::class)
        subclass(AccountRemovedEffectResponse::class)
        subclass(AccountSponsorshipCreatedEffectResponse::class)
        subclass(AccountSponsorshipRemovedEffectResponse::class)
        subclass(AccountSponsorshipUpdatedEffectResponse::class)
        subclass(AccountThresholdsUpdatedEffectResponse::class)

        // Claimable balance effects
        subclass(ClaimableBalanceClaimantCreatedEffectResponse::class)
        subclass(ClaimableBalanceClaimedEffectResponse::class)
        subclass(ClaimableBalanceClawedBackEffectResponse::class)
        subclass(ClaimableBalanceCreatedEffectResponse::class)
        subclass(ClaimableBalanceSponsorshipCreatedEffectResponse::class)
        subclass(ClaimableBalanceSponsorshipRemovedEffectResponse::class)
        subclass(ClaimableBalanceSponsorshipUpdatedEffectResponse::class)

        // Contract effects
        subclass(ContractCreditedEffectResponse::class)
        subclass(ContractDebitedEffectResponse::class)

        // Data effects
        subclass(DataCreatedEffectResponse::class)
        subclass(DataRemovedEffectResponse::class)
        subclass(DataSponsorshipCreatedEffectResponse::class)
        subclass(DataSponsorshipRemovedEffectResponse::class)
        subclass(DataSponsorshipUpdatedEffectResponse::class)
        subclass(DataUpdatedEffectResponse::class)

        // Liquidity pool effects
        subclass(LiquidityPoolCreatedEffectResponse::class)
        subclass(LiquidityPoolDepositedEffectResponse::class)
        subclass(LiquidityPoolRemovedEffectResponse::class)
        subclass(LiquidityPoolRevokedEffectResponse::class)
        subclass(LiquidityPoolTradeEffectResponse::class)
        subclass(LiquidityPoolWithdrewEffectResponse::class)

        // Offer effects
        subclass(OfferCreatedEffectResponse::class)
        subclass(OfferRemovedEffectResponse::class)
        subclass(OfferUpdatedEffectResponse::class)

        // Sequence effects
        subclass(SequenceBumpedEffectResponse::class)

        // Signer effects
        subclass(SignerCreatedEffectResponse::class)
        subclass(SignerRemovedEffectResponse::class)
        subclass(SignerSponsorshipCreatedEffectResponse::class)
        subclass(SignerSponsorshipRemovedEffectResponse::class)
        subclass(SignerSponsorshipUpdatedEffectResponse::class)
        subclass(SignerUpdatedEffectResponse::class)

        // Trade effects
        subclass(TradeEffectResponse::class)

        // Trustline effects
        subclass(TrustlineAuthorizedEffectResponse::class)
        subclass(TrustlineAuthorizedToMaintainLiabilitiesEffectResponse::class)
        subclass(TrustlineCreatedEffectResponse::class)
        subclass(TrustlineDeauthorizedEffectResponse::class)
        subclass(TrustlineFlagsUpdatedEffectResponse::class)
        subclass(TrustlineRemovedEffectResponse::class)
        subclass(TrustlineSponsorshipCreatedEffectResponse::class)
        subclass(TrustlineSponsorshipRemovedEffectResponse::class)
        subclass(TrustlineSponsorshipUpdatedEffectResponse::class)
        subclass(TrustlineUpdatedEffectResponse::class)
    }

    polymorphic(OperationResponse::class) {
        subclass(AccountMergeOperationResponse::class)
        subclass(AllowTrustOperationResponse::class)
        subclass(BeginSponsoringFutureReservesOperationResponse::class)
        subclass(BumpSequenceOperationResponse::class)
        subclass(ChangeTrustOperationResponse::class)
        subclass(ClaimClaimableBalanceOperationResponse::class)
        subclass(ClawbackClaimableBalanceOperationResponse::class)
        subclass(ClawbackOperationResponse::class)
        subclass(CreateAccountOperationResponse::class)
        subclass(CreateClaimableBalanceOperationResponse::class)
        subclass(CreatePassiveSellOfferOperationResponse::class)
        subclass(EndSponsoringFutureReservesOperationResponse::class)
        subclass(ExtendFootprintTTLOperationResponse::class)
        subclass(InvokeHostFunctionOperationResponse::class)
        subclass(LiquidityPoolDepositOperationResponse::class)
        subclass(LiquidityPoolWithdrawOperationResponse::class)
        subclass(ManageBuyOfferOperationResponse::class)
        subclass(ManageDataOperationResponse::class)
        subclass(ManageSellOfferOperationResponse::class)
        subclass(PathPaymentStrictReceiveOperationResponse::class)
        subclass(PathPaymentStrictSendOperationResponse::class)
        subclass(PaymentOperationResponse::class)
        subclass(RestoreFootprintOperationResponse::class)
        subclass(RevokeSponsorshipOperationResponse::class)
        subclass(SetOptionsOperationResponse::class)
        subclass(SetTrustLineFlagsOperationResponse::class)
    }
}
