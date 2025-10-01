package com.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.stellar.sdk.horizon.exceptions.*
import com.stellar.sdk.horizon.responses.TradeAggregationResponse
import com.stellar.sdk.horizon.responses.Page

/**
 * Builds requests connected to trade aggregations.
 *
 * Trade aggregations divide a given time range into segments and aggregate statistics about
 * trading activity for each segment. They are similar to candlestick charts used in traditional
 * trading platforms, showing open, high, low, and close (OHLC) prices along with trading volume.
 *
 * All parameters are required for this endpoint:
 * - Base asset (the asset being bought)
 * - Counter asset (the asset being sold)
 * - Start time (beginning of time range, in milliseconds since epoch)
 * - End time (end of time range, in milliseconds since epoch)
 * - Resolution (size of each time bucket, in milliseconds)
 * - Offset (offset from start time for bucketing, in milliseconds, defaults to 0)
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Get hourly trade aggregations for XLM/USD over the last 24 hours
 * val startTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
 * val endTime = System.currentTimeMillis()
 * val oneHour = 60 * 60 * 1000L // 1 hour in milliseconds
 *
 * val aggregations = server.tradeAggregations(
 *     baseAssetType = "native",
 *     baseAssetCode = null,
 *     baseAssetIssuer = null,
 *     counterAssetType = "credit_alphanum4",
 *     counterAssetCode = "USD",
 *     counterAssetIssuer = "ISSUER_ID",
 *     startTime = startTime,
 *     endTime = endTime,
 *     resolution = oneHour,
 *     offset = 0
 * ).execute()
 *
 * for (agg in aggregations.records) {
 *     println("Time: ${agg.timestamp}, Open: ${agg.open}, High: ${agg.high}, Low: ${agg.low}, Close: ${agg.close}")
 * }
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/aggregations/trade-aggregations/">Trade Aggregations documentation</a>
 */
class TradeAggregationsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url,
    baseAssetType: String,
    baseAssetCode: String?,
    baseAssetIssuer: String?,
    counterAssetType: String,
    counterAssetCode: String?,
    counterAssetIssuer: String?,
    startTime: Long,
    endTime: Long,
    resolution: Long,
    offset: Long
) : RequestBuilder(httpClient, serverUri, "trade_aggregations") {

    init {
        // Set base asset parameters
        setAssetTypeParameters("base", baseAssetType, baseAssetCode, baseAssetIssuer)

        // Set counter asset parameters
        setAssetTypeParameters("counter", counterAssetType, counterAssetCode, counterAssetIssuer)

        // Set time and resolution parameters
        uriBuilder.parameters["start_time"] = startTime.toString()
        uriBuilder.parameters["end_time"] = endTime.toString()
        uriBuilder.parameters["resolution"] = resolution.toString()
        uriBuilder.parameters["offset"] = offset.toString()
    }

    /**
     * Build and execute request to get a page of trade aggregations.
     *
     * @return Page of trade aggregation responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/aggregations/trade-aggregations/list/">List Trade Aggregations</a>
     */
    suspend fun execute(): Page<TradeAggregationResponse> {
        return executeGetRequest(buildUrl())
    }

    /**
     * Sets the cursor parameter for pagination.
     *
     * A cursor is a value that points to a specific location in a collection of resources.
     * Use this to retrieve results starting from a specific trade aggregation.
     *
     * @param cursor A paging token from a previous response
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/introduction/pagination/">Pagination documentation</a>
     */
    override fun cursor(cursor: String): TradeAggregationsRequestBuilder {
        super.cursor(cursor)
        return this
    }

    /**
     * Sets the limit parameter defining maximum number of trade aggregations to return.
     *
     * The maximum limit is 200. If not specified, Horizon will use a default limit (typically 10).
     *
     * @param number Maximum number of trade aggregations to return (max 200)
     * @return This request builder instance
     */
    override fun limit(number: Int): TradeAggregationsRequestBuilder {
        super.limit(number)
        return this
    }

    /**
     * Sets the order parameter defining the order in which to return trade aggregations.
     *
     * @param direction The order direction (ASC for ascending, DESC for descending)
     * @return This request builder instance
     */
    override fun order(direction: Order): TradeAggregationsRequestBuilder {
        super.order(direction)
        return this
    }
}
