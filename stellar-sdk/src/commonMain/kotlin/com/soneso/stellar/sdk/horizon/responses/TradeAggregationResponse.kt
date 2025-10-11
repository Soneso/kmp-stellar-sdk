package com.soneso.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a trade aggregation response from the Horizon API.
 *
 * Trade aggregations represent aggregated statistics about trading activity for a given asset pair
 * over a specified time period. They are similar to candlestick charts used in traditional trading
 * platforms, showing open, high, low, and close (OHLC) prices, along with trading volume.
 *
 * Trade aggregations are useful for:
 * - Displaying price charts
 * - Analyzing trading patterns
 * - Computing trading statistics
 * - Building trading bots and algorithms
 *
 * @property timestamp The start time for this aggregation bucket (UNIX timestamp in milliseconds)
 * @property tradeCount The number of trades in this time period
 * @property baseVolume The volume of the base asset traded
 * @property counterVolume The volume of the counter asset traded
 * @property avg The average price in this time period
 * @property high The highest price in this time period
 * @property highR The highest price as a ratio (numerator/denominator)
 * @property low The lowest price in this time period
 * @property lowR The lowest price as a ratio (numerator/denominator)
 * @property open The price of the first trade in this time period
 * @property openR The opening price as a ratio (numerator/denominator)
 * @property close The price of the last trade in this time period
 * @property closeR The closing price as a ratio (numerator/denominator)
 *
 * @see <a href="https://developers.stellar.org/api/aggregations/trade-aggregations/">Trade Aggregations documentation</a>
 */
@Serializable
data class TradeAggregationResponse(
    @SerialName("timestamp")
    val timestamp: Long,

    @SerialName("trade_count")
    val tradeCount: Int,

    @SerialName("base_volume")
    val baseVolume: String,

    @SerialName("counter_volume")
    val counterVolume: String,

    @SerialName("avg")
    val avg: String,

    @SerialName("high")
    val high: String,

    @SerialName("high_r")
    val highR: Price,

    @SerialName("low")
    val low: String,

    @SerialName("low_r")
    val lowR: Price,

    @SerialName("open")
    val open: String,

    @SerialName("open_r")
    val openR: Price,

    @SerialName("close")
    val close: String,

    @SerialName("close_r")
    val closeR: Price
) : Response()
