package com.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents account_credited effect response.
 *
 * This effect occurs when an account receives a payment.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("account_credited")
data class AccountCreditedEffectResponse(
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
     * The amount credited
     */
    val amount: String,

    /**
     * The asset type (native, credit_alphanum4, credit_alphanum12, or liquidity_pool_shares)
     */
    @SerialName("asset_type")
    val assetType: String,

    /**
     * The asset code (for non-native assets)
     */
    @SerialName("asset_code")
    val assetCode: String? = null,

    /**
     * The asset issuer (for non-native assets)
     */
    @SerialName("asset_issuer")
    val assetIssuer: String? = null
) : EffectResponse()
