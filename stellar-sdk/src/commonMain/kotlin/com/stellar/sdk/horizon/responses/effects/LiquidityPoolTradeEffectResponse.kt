package com.stellar.sdk.horizon.responses.effects

import com.stellar.sdk.horizon.responses.AssetAmount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents liquidity_pool_trade effect response.
 *
 * This effect occurs when a trade is executed against a liquidity pool.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("liquidity_pool_trade")
data class LiquidityPoolTradeEffectResponse(
    override val id: String,
    override val account: String?,
    @SerialName("account_muxed")
    override val accountMuxed: String? = null,
    @SerialName("account_muxed_id")
    override val accountMuxedId: String? = null,
    override val type: String,
    @SerialName("created_at")
    override val createdAt: String,
    @SerialName("paging_token")
    override val pagingToken: String,
    @SerialName("_links")
    override val links: EffectLinks,

    /**
     * The liquidity pool information
     */
    @SerialName("liquidity_pool")
    val liquidityPool: LiquidityPool,

    /**
     * The asset sold
     */
    @SerialName("sold")
    val sold: AssetAmount,

    /**
     * The asset bought
     */
    @SerialName("bought")
    val bought: AssetAmount
) : EffectResponse()
