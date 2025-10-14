package com.soneso.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an offer response from the Horizon API.
 *
 * An offer is an intent to trade one asset for another at a pre-determined exchange rate.
 * Offers are created using the Manage Buy Offer or Manage Sell Offer operation.
 *
 * @property id Unique identifier for this offer
 * @property pagingToken A cursor value for use in pagination
 * @property seller Account ID of the account making this offer
 * @property sellingAsset The asset this offer wants to sell (nested JSON object)
 * @property buyingAsset The asset this offer wants to buy (nested JSON object)
 * @property amount The amount of selling being offered
 * @property priceR The precise representation of buy and sell price as a rational number
 * @property price The price to buy 1 unit of buying in terms of selling, as a string
 * @property lastModifiedLedger The sequence number of the last ledger in which this offer was modified
 * @property lastModifiedTime An ISO 8601 formatted string of the last time this offer was modified
 * @property sponsor The account ID of the sponsor who is paying the reserves for this offer (optional)
 * @property links HAL links related to this offer
 *
 * @see <a href="https://developers.stellar.org/api/resources/offers/">Offer documentation</a>
 */
@Serializable
data class OfferResponse(
    @SerialName("id")
    val id: String,

    @SerialName("paging_token")
    override val pagingToken: String,

    @SerialName("seller")
    val seller: String,

    @SerialName("selling")
    val sellingAsset: Asset,

    @SerialName("buying")
    val buyingAsset: Asset,

    @SerialName("amount")
    val amount: String,

    @SerialName("price_r")
    val priceR: Price,

    @SerialName("price")
    val price: String,

    @SerialName("last_modified_ledger")
    val lastModifiedLedger: Long? = null,

    @SerialName("last_modified_time")
    val lastModifiedTime: String? = null,

    @SerialName("sponsor")
    val sponsor: String? = null,

    @SerialName("_links")
    val links: Links
) : Response(), Pageable {

    /**
     * Returns the buying asset as an SDK Asset object.
     *
     * This converts the Horizon response Asset (which contains assetType, assetCode, and assetIssuer)
     * into a proper SDK Asset type (AssetTypeNative, AssetTypeCreditAlphaNum4, or AssetTypeCreditAlphaNum12).
     */
    val buying: com.soneso.stellar.sdk.Asset
        get() = buyingAsset.toSdkAsset()

    /**
     * Returns the selling asset as an SDK Asset object.
     *
     * This converts the Horizon response Asset (which contains assetType, assetCode, and assetIssuer)
     * into a proper SDK Asset type (AssetTypeNative, AssetTypeCreditAlphaNum4, or AssetTypeCreditAlphaNum12).
     */
    val selling: com.soneso.stellar.sdk.Asset
        get() = sellingAsset.toSdkAsset()

    /**
     * HAL links connected to this offer.
     *
     * @property self Link to this offer
     * @property offerMaker Link to the account that created this offer
     */
    @Serializable
    data class Links(
        @SerialName("self")
        val self: Link,

        @SerialName("offer_maker")
        val offerMaker: Link
    )
}
