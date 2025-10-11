package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents sequence_bumped effect response.
 *
 * This effect occurs when an account's sequence number is bumped.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("sequence_bumped")
data class SequenceBumpedEffectResponse(
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
     * The new sequence number
     */
    @SerialName("new_seq")
    val newSequence: Long
) : EffectResponse()
