package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents data_sponsorship_updated effect response.
 *
 * This effect occurs when sponsorship for a data entry is updated.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("data_sponsorship_updated")
data class DataSponsorshipUpdatedEffectResponse(
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
     * The account ID of the new sponsor
     */
    @SerialName("new_sponsor")
    val newSponsor: String,

    /**
     * The name of the data entry
     */
    @SerialName("data_name")
    val dataName: String
) : EffectResponse()
