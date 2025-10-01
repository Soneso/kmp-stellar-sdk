package com.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a trade response from the Horizon API.
 *
 * A trade represents an exchange of assets between two parties on the Stellar network.
 * Trades can occur between two offers (orderbook trades) or between an offer and a liquidity pool.
 *
 * @property id Unique identifier for this trade
 * @property pagingToken A cursor value for use in pagination
 * @property ledgerCloseTime An ISO 8601 formatted string of when the ledger with this trade was closed
 * @property tradeType The type of trade (orderbook, liquidity_pool)
 * @property offerId The offer ID (for backwards compatibility, deprecated - use baseOfferId or counterOfferId)
 * @property liquidityPoolFeeBP The liquidity pool fee in basis points (for liquidity pool trades)
 * @property baseLiquidityPoolId The liquidity pool ID for the base side (for liquidity pool trades)
 * @property baseOfferId The offer ID of the base account
 * @property baseAccount The account ID of the base party in this trade
 * @property baseAmount The amount of the base asset that was moved from base to counter
 * @property baseAssetType The type of asset on the base side (native, credit_alphanum4, credit_alphanum12)
 * @property baseAssetCode The code of the base asset (null for native)
 * @property baseAssetIssuer The issuer of the base asset (null for native)
 * @property counterLiquidityPoolId The liquidity pool ID for the counter side (for liquidity pool trades)
 * @property counterOfferId The offer ID of the counter account
 * @property counterAccount The account ID of the counter party in this trade
 * @property counterAmount The amount of the counter asset that was moved from counter to base
 * @property counterAssetType The type of asset on the counter side (native, credit_alphanum4, credit_alphanum12)
 * @property counterAssetCode The code of the counter asset (null for native)
 * @property counterAssetIssuer The issuer of the counter asset (null for native)
 * @property baseIsSeller Indicates which party is the seller (true if base is seller)
 * @property price The original offer price (counter / base)
 * @property links HAL links related to this trade
 *
 * @see <a href="https://developers.stellar.org/api/resources/trades/">Trade documentation</a>
 */
@Serializable
data class TradeResponse(
    @SerialName("id")
    val id: String,

    @SerialName("paging_token")
    override val pagingToken: String,

    @SerialName("ledger_close_time")
    val ledgerCloseTime: String,

    @SerialName("trade_type")
    val tradeType: String,

    @SerialName("offer_id")
    val offerId: Long? = null,

    @SerialName("liquidity_pool_fee_bp")
    val liquidityPoolFeeBP: Int? = null,

    @SerialName("base_liquidity_pool_id")
    val baseLiquidityPoolId: String? = null,

    @SerialName("base_offer_id")
    val baseOfferId: Long? = null,

    @SerialName("base_account")
    val baseAccount: String? = null,

    @SerialName("base_amount")
    val baseAmount: String,

    @SerialName("base_asset_type")
    val baseAssetType: String,

    @SerialName("base_asset_code")
    val baseAssetCode: String? = null,

    @SerialName("base_asset_issuer")
    val baseAssetIssuer: String? = null,

    @SerialName("counter_liquidity_pool_id")
    val counterLiquidityPoolId: String? = null,

    @SerialName("counter_offer_id")
    val counterOfferId: Long? = null,

    @SerialName("counter_account")
    val counterAccount: String? = null,

    @SerialName("counter_amount")
    val counterAmount: String,

    @SerialName("counter_asset_type")
    val counterAssetType: String,

    @SerialName("counter_asset_code")
    val counterAssetCode: String? = null,

    @SerialName("counter_asset_issuer")
    val counterAssetIssuer: String? = null,

    @SerialName("base_is_seller")
    val baseIsSeller: Boolean? = null,

    @SerialName("price")
    val price: Price? = null,

    @SerialName("_links")
    val links: Links
) : Response(), Pageable {

    /**
     * HAL links connected to this trade.
     *
     * @property base Link to the base account or liquidity pool
     * @property counter Link to the counter account or liquidity pool
     * @property operation Link to the operation that created this trade
     */
    @Serializable
    data class Links(
        @SerialName("base")
        val base: Link,

        @SerialName("counter")
        val counter: Link,

        @SerialName("operation")
        val operation: Link
    )
}
