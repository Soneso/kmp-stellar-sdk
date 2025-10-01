package com.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.stellar.sdk.horizon.exceptions.*
import com.stellar.sdk.horizon.responses.TradeResponse
import com.stellar.sdk.horizon.responses.Page

/**
 * Builds requests connected to trades.
 *
 * Trades represent exchanges of assets on the Stellar network. This builder allows you to query
 * trades based on various criteria such as trading accounts, liquidity pools, asset pairs, or offer IDs.
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Get trades for an account
 * val accountTrades = server.trades()
 *     .forAccount("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .limit(20)
 *     .execute()
 *
 * // Get trades for a specific asset pair
 * val pairTrades = server.trades()
 *     .forBaseAsset("native")
 *     .forCounterAsset("credit_alphanum4", "USD", "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .execute()
 *
 * // Get trades for a specific offer
 * val offerTrades = server.trades()
 *     .forOfferId(12345)
 *     .execute()
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/resources/trades/">Trades documentation</a>
 */
class TradesRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "trades") {

    /**
     * Returns trades for a specific account.
     *
     * @param accountId Account ID to filter trades by
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/accounts/trades/">Trades for Account</a>
     */
    fun forAccount(accountId: String): TradesRequestBuilder {
        setSegments("accounts", accountId, "trades")
        return this
    }

    /**
     * Returns trades for a specific liquidity pool.
     *
     * @param liquidityPoolId Liquidity pool ID to filter trades by
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/liquiditypools/trades/">Trades for Liquidity Pool</a>
     */
    fun forLiquidityPool(liquidityPoolId: String): TradesRequestBuilder {
        setSegments("liquidity_pools", liquidityPoolId, "trades")
        return this
    }

    /**
     * Returns trades for a specific offer.
     *
     * @param offerId The offer ID to filter trades by (null to clear the filter)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/trades/list/">List All Trades</a>
     */
    fun forOfferId(offerId: Long?): TradesRequestBuilder {
        if (offerId == null) {
            uriBuilder.parameters.remove("offer_id")
        } else {
            uriBuilder.parameters["offer_id"] = offerId.toString()
        }
        return this
    }

    /**
     * Returns trades of a specific type.
     *
     * @param tradeType The trade type (orderbook, liquidity_pool, or all)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/trades/list/">List All Trades</a>
     */
    fun forTradeType(tradeType: String): TradesRequestBuilder {
        uriBuilder.parameters["trade_type"] = tradeType
        return this
    }

    /**
     * Filters trades by base asset.
     *
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer account ID (null for native)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/trades/list/">List All Trades</a>
     */
    fun forBaseAsset(
        assetType: String,
        assetCode: String? = null,
        assetIssuer: String? = null
    ): TradesRequestBuilder {
        setAssetTypeParameters("base", assetType, assetCode, assetIssuer)
        return this
    }

    /**
     * Filters trades by counter asset.
     *
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer account ID (null for native)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/trades/list/">List All Trades</a>
     */
    fun forCounterAsset(
        assetType: String,
        assetCode: String? = null,
        assetIssuer: String? = null
    ): TradesRequestBuilder {
        setAssetTypeParameters("counter", assetType, assetCode, assetIssuer)
        return this
    }

    /**
     * Build and execute request to get a page of trades.
     *
     * @return Page of trade responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/resources/trades/list/">List All Trades</a>
     */
    suspend fun execute(): Page<TradeResponse> {
        return executeGetRequest(buildUrl())
    }

    /**
     * Sets the cursor parameter for pagination.
     *
     * A cursor is a value that points to a specific location in a collection of resources.
     * Use this to retrieve results starting from a specific trade.
     *
     * @param cursor A paging token from a previous response
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/introduction/pagination/">Pagination documentation</a>
     */
    override fun cursor(cursor: String): TradesRequestBuilder {
        super.cursor(cursor)
        return this
    }

    /**
     * Sets the limit parameter defining maximum number of trades to return.
     *
     * The maximum limit is 200. If not specified, Horizon will use a default limit (typically 10).
     *
     * @param number Maximum number of trades to return (max 200)
     * @return This request builder instance
     */
    override fun limit(number: Int): TradesRequestBuilder {
        super.limit(number)
        return this
    }
}
