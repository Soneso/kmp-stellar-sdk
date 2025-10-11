package com.soneso.stellar.sdk.horizon.responses.effects

import com.soneso.stellar.sdk.horizon.responses.AssetAmount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents liquidity_pool_withdrew effect response.
 *
 * This effect occurs when assets are withdrawn from a liquidity pool.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("liquidity_pool_withdrew")
data class LiquidityPoolWithdrewEffectResponse(
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
     * The reserves received
     */
    @SerialName("reserves_received")
    val reservesReceived: List<AssetAmount>,

    /**
     * The shares redeemed
     */
    @SerialName("shares_redeemed")
    val sharesRedeemed: String
) : EffectResponse()
