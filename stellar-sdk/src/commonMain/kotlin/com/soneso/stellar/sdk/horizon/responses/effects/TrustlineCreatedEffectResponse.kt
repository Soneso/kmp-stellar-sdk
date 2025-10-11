package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents trustline_created effect response.
 *
 * This effect occurs when a new trustline is created.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("trustline_created")
data class TrustlineCreatedEffectResponse(
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

    @SerialName("limit")
    override val limit: String,

    @SerialName("asset_type")
    override val assetType: String,

    @SerialName("asset_code")
    override val assetCode: String? = null,

    @SerialName("asset_issuer")
    override val assetIssuer: String? = null,

    @SerialName("liquidity_pool_id")
    override val liquidityPoolId: String? = null
) : TrustlineCUDResponse()
