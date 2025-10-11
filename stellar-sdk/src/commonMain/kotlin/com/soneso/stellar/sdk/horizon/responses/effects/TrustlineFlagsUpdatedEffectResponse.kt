package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents trustline_flags_updated effect response.
 *
 * This effect occurs when trustline flags are updated.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("trustline_flags_updated")
data class TrustlineFlagsUpdatedEffectResponse(
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
     * The trustor account
     */
    @SerialName("trustor")
    val trustor: String,

    /**
     * The asset type
     */
    @SerialName("asset_type")
    val assetType: String,

    /**
     * The asset code
     */
    @SerialName("asset_code")
    val assetCode: String? = null,

    /**
     * The asset issuer
     */
    @SerialName("asset_issuer")
    val assetIssuer: String? = null,

    /**
     * Whether the trustline is authorized
     */
    @SerialName("authorized_flag")
    val authorizedFlag: Boolean? = null,

    /**
     * Whether the trustline is authorized to maintain liabilities
     * Note: The JSON field name has a typo from the Go implementation
     */
    @SerialName("authorized_to_maintain_liabilites_flag")
    val authorizedToMaintainLiabilitiesFlag: Boolean? = null,

    /**
     * Whether clawback is enabled for the trustline
     */
    @SerialName("clawback_enabled_flag")
    val clawbackEnabledFlag: Boolean? = null
) : EffectResponse()
