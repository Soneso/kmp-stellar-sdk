package com.stellar.sdk.horizon.responses.effects

import com.stellar.sdk.horizon.responses.AssetAmount
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents liquidity_pool_deposited effect response.
 *
 * This effect occurs when assets are deposited into a liquidity pool.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("liquidity_pool_deposited")
data class LiquidityPoolDepositedEffectResponse(
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
     * The reserves deposited
     */
    @SerialName("reserves_deposited")
    val reservesDeposited: List<AssetAmount>,

    /**
     * The shares received
     */
    @SerialName("shares_received")
    val sharesReceived: String
) : EffectResponse()
