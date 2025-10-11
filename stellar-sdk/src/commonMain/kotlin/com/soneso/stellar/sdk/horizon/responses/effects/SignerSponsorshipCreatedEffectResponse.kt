package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents signer_sponsorship_created effect response.
 *
 * This effect occurs when sponsorship for a signer is created.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("signer_sponsorship_created")
data class SignerSponsorshipCreatedEffectResponse(
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
     * The account ID of the sponsor
     */
    @SerialName("sponsor")
    val sponsor: String,

    /**
     * The signer being sponsored
     */
    @SerialName("signer")
    val signer: String
) : EffectResponse()
