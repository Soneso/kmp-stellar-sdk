package com.stellar.sdk.horizon.responses

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
 * @property selling The asset this offer wants to sell
 * @property sellingAssetType The type of asset being sold (native, credit_alphanum4, credit_alphanum12)
 * @property sellingAssetCode The code of the asset being sold (null for native)
 * @property sellingAssetIssuer The issuer of the asset being sold (null for native)
 * @property buying The asset this offer wants to buy
 * @property buyingAssetType The type of asset being bought (native, credit_alphanum4, credit_alphanum12)
 * @property buyingAssetCode The code of the asset being bought (null for native)
 * @property buyingAssetIssuer The issuer of the asset being bought (null for native)
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
    val pagingToken: String,

    @SerialName("seller")
    val seller: String,

    @SerialName("selling_asset_type")
    val sellingAssetType: String,

    @SerialName("selling_asset_code")
    val sellingAssetCode: String? = null,

    @SerialName("selling_asset_issuer")
    val sellingAssetIssuer: String? = null,

    @SerialName("buying_asset_type")
    val buyingAssetType: String,

    @SerialName("buying_asset_code")
    val buyingAssetCode: String? = null,

    @SerialName("buying_asset_issuer")
    val buyingAssetIssuer: String? = null,

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
) : Response() {

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
