package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents trustline_deauthorized effect response.
 *
 * @deprecated As of release 0.24.0, replaced by [TrustlineFlagsUpdatedEffectResponse]
 *
 * This effect occurs when a trustline is deauthorized.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Deprecated("Replaced by TrustlineFlagsUpdatedEffectResponse")
@Serializable
@SerialName("trustline_deauthorized")
data class TrustlineDeauthorizedEffectResponse(
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

    @SerialName("trustor")
    override val trustor: String,

    @SerialName("asset_type")
    override val assetType: String,

    @SerialName("asset_code")
    override val assetCode: String? = null
) : TrustlineAuthorizationResponse()
