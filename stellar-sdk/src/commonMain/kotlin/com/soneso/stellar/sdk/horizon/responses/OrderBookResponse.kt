package com.soneso.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an order book response from the Horizon API.
 *
 * The order book shows the current state of buy and sell offers for a given asset pair.
 * It contains lists of bids (buy offers) and asks (sell offers), each with their amounts
 * and prices.
 *
 * @property base The base asset of the trading pair
 * @property counter The counter asset of the trading pair
 * @property asks List of sell offers (asks) in the order book
 * @property bids List of buy offers (bids) in the order book
 *
 * @see <a href="https://developers.stellar.org/api/aggregations/order-books/">Order Book documentation</a>
 */
@Serializable
data class OrderBookResponse(
    @SerialName("base")
    val base: Asset,

    @SerialName("counter")
    val counter: Asset,

    @SerialName("asks")
    val asks: List<Row>,

    @SerialName("bids")
    val bids: List<Row>
) : Response() {

    /**
     * Represents a single price level in the order book.
     *
     * Each row contains the total amount available at a given price point,
     * the decimal price, and the price as a ratio.
     *
     * @property amount The amount of the asset available at this price level
     * @property price The price as a decimal string
     * @property priceR The price as a ratio (numerator/denominator)
     */
    @Serializable
    data class Row(
        @SerialName("amount")
        val amount: String,

        @SerialName("price")
        val price: String,

        @SerialName("price_r")
        val priceR: Price
    )
}
