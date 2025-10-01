package com.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents claimable_balance_sponsorship_created effect response.
 *
 * This effect occurs when sponsorship for a claimable balance is created.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("claimable_balance_sponsorship_created")
data class ClaimableBalanceSponsorshipCreatedEffectResponse(
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
     * The claimable balance ID
     */
    @SerialName("balance_id")
    val balanceId: String
) : EffectResponse()
