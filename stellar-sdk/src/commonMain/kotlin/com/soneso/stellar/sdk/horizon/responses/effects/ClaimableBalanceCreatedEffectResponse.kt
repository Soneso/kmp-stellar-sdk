package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents claimable_balance_created effect response.
 *
 * This effect occurs when a claimable balance is created.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("claimable_balance_created")
data class ClaimableBalanceCreatedEffectResponse(
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
     * The asset (simplified as string for now)
     */
    @SerialName("asset")
    val asset: String,

    /**
     * The amount of the asset
     */
    @SerialName("amount")
    val amount: String,

    /**
     * The claimable balance ID
     */
    @SerialName("balance_id")
    val balanceId: String
) : EffectResponse()
