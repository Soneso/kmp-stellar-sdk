package com.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents signer_sponsorship_removed effect response.
 *
 * This effect occurs when sponsorship for a signer is removed.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("signer_sponsorship_removed")
data class SignerSponsorshipRemovedEffectResponse(
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
     * The account ID of the former sponsor
     */
    @SerialName("former_sponsor")
    val formerSponsor: String,

    /**
     * The signer that was sponsored
     */
    @SerialName("signer")
    val signer: String
) : EffectResponse()
