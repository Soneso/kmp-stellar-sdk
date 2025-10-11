package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents offer_updated effect response.
 *
 * This effect occurs when an offer is updated on the Stellar network.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("offer_updated")
data class OfferUpdatedEffectResponse(
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
    override val links: EffectLinks
) : EffectResponse()
