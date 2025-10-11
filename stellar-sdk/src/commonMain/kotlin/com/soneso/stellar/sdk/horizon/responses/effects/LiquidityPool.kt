package com.soneso.stellar.sdk.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.AssetAmount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents liquidity pool information used in effect responses.
 *
 * @property id The liquidity pool ID
 * @property feeBP The fee in basis points
 * @property type The type of liquidity pool
 * @property totalTrustlines Total number of trustlines
 * @property totalShares Total shares in the pool
 * @property reserves The reserves in the pool
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
data class LiquidityPool(
    @SerialName("id")
    val id: String,

    @SerialName("fee_bp")
    val feeBP: Int,

    @SerialName("type")
    val type: String,

    @SerialName("total_trustlines")
    val totalTrustlines: Long,

    @SerialName("total_shares")
    val totalShares: String,

    @SerialName("reserves")
    val reserves: List<AssetAmount>
)
