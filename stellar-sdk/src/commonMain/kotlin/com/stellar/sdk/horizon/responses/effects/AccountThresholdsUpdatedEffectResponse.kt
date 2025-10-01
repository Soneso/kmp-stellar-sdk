package com.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents account_thresholds_updated effect response.
 *
 * This effect occurs when an account's thresholds are updated.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("account_thresholds_updated")
data class AccountThresholdsUpdatedEffectResponse(
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
     * The low threshold for operations
     */
    @SerialName("low_threshold")
    val lowThreshold: Int,

    /**
     * The medium threshold for operations
     */
    @SerialName("med_threshold")
    val medThreshold: Int,

    /**
     * The high threshold for operations
     */
    @SerialName("high_threshold")
    val highThreshold: Int
) : EffectResponse()
