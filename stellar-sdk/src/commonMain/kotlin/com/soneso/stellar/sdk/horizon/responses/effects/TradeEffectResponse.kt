package com.soneso.stellar.sdk.horizon.responses.effects

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents trade effect response.
 *
 * This effect occurs when a trade is executed.
 *
 * @see [Effect Documentation](https://developers.stellar.org/docs/data/horizon/api-reference/resources/effects)
 */
@Serializable
@SerialName("trade")
data class TradeEffectResponse(
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
     * The account address of the seller
     */
    val seller: String,

    /**
     * The muxed account address of the seller
     */
    @SerialName("seller_muxed")
    val sellerMuxed: String? = null,

    /**
     * The muxed account ID of the seller
     */
    @SerialName("seller_muxed_id")
    val sellerMuxedId: String? = null,

    /**
     * The ID of the offer that was executed
     */
    @SerialName("offer_id")
    val offerId: Long,

    /**
     * The amount of the asset that was sold
     */
    @SerialName("sold_amount")
    val soldAmount: String,

    /**
     * The asset type that was sold
     */
    @SerialName("sold_asset_type")
    val soldAssetType: String,

    /**
     * The asset code that was sold (for non-native assets)
     */
    @SerialName("sold_asset_code")
    val soldAssetCode: String? = null,

    /**
     * The asset issuer that was sold (for non-native assets)
     */
    @SerialName("sold_asset_issuer")
    val soldAssetIssuer: String? = null,

    /**
     * The amount of the asset that was bought
     */
    @SerialName("bought_amount")
    val boughtAmount: String,

    /**
     * The asset type that was bought
     */
    @SerialName("bought_asset_type")
    val boughtAssetType: String,

    /**
     * The asset code that was bought (for non-native assets)
     */
    @SerialName("bought_asset_code")
    val boughtAssetCode: String? = null,

    /**
     * The asset issuer that was bought (for non-native assets)
     */
    @SerialName("bought_asset_issuer")
    val boughtAssetIssuer: String? = null
) : EffectResponse()
