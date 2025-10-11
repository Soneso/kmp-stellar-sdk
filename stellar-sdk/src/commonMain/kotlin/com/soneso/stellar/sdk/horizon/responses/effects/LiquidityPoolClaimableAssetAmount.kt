package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents liquidity pool claimable asset amount used in effect responses.
 *
 * @property asset The asset (simplified as string for now)
 * @property amount The amount of the asset
 * @property claimableBalanceId The claimable balance ID
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
data class LiquidityPoolClaimableAssetAmount(
    @SerialName("asset")
    val asset: String,

    @SerialName("amount")
    val amount: String,

    @SerialName("claimable_balance_id")
    val claimableBalanceId: String
)
