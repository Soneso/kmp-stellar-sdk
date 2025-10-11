package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents liquidity_pool_revoked effect response.
 *
 * This effect occurs when a liquidity pool is revoked.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("liquidity_pool_revoked")
data class LiquidityPoolRevokedEffectResponse(
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
     * The reserves revoked
     */
    @SerialName("reserves_revoked")
    val reservesRevoked: List<LiquidityPoolClaimableAssetAmount>,

    /**
     * The shares revoked
     */
    @SerialName("shares_revoked")
    val sharesRevoked: String
) : EffectResponse()
