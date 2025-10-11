package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.LiquidityPoolResponse
import com.soneso.stellar.sdk.horizon.responses.Page

/**
 * Builds requests connected to liquidity pools.
 *
 * Liquidity pools enable automated market makers (AMMs) on the Stellar network,
 * allowing users to deposit assets and earn fees from trades.
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Get a specific liquidity pool by ID
 * val pool = server.liquidityPools()
 *     .liquidityPool("67260c4c1807b262ff851b0a3fe141194936bb0215b2f77447f1df11998eabb9")
 *
 * // Get liquidity pools for specific reserves
 * val pools = server.liquidityPools()
 *     .forReserves(
 *         "EURT:GAP5LETOV6YIE62YAM56STDANPRDO7ZFDBGSNHJQIYGGKSMOZAHOOS2S",
 *         "native"
 *     )
 *     .limit(20)
 *     .execute()
 *
 * // Get liquidity pools an account is participating in
 * val accountPools = server.liquidityPools()
 *     .forAccount("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .execute()
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/resources/liquiditypools/">Liquidity Pools documentation</a>
 */
class LiquidityPoolsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "liquidity_pools") {

    companion object {
        private const val RESERVES_PARAMETER_NAME = "reserves"
        private const val ACCOUNT_PARAMETER_NAME = "account"
    }

    /**
     * Requests a specific liquidity pool by ID.
     *
     * @param liquidityPoolId The liquidity pool ID to fetch
     * @return The liquidity pool response
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/resources/liquiditypools/single/">Liquidity Pool Details</a>
     */
    suspend fun liquidityPool(liquidityPoolId: String): LiquidityPoolResponse {
        setSegments("liquidity_pools", liquidityPoolId)
        return executeGetRequest(buildUrl())
    }

    /**
     * Returns all liquidity pools that contain reserves in all specified assets.
     *
     * You can specify up to 2 reserve assets to filter by. The assets should be in canonical
     * string format (e.g., "native" for XLM or "USD:GCDNJUBQSX7..." for issued assets).
     *
     * @param reserves Reserve assets to filter liquidity pools (up to 2 assets)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/liquiditypools/list/">List All Liquidity Pools</a>
     */
    fun forReserves(vararg reserves: String): LiquidityPoolsRequestBuilder {
        require(reserves.isNotEmpty()) { "At least one reserve asset must be specified" }
        require(reserves.size <= 2) { "Maximum of 2 reserve assets can be specified" }
        uriBuilder.parameters[RESERVES_PARAMETER_NAME] = reserves.joinToString(",")
        return this
    }

    /**
     * Returns all liquidity pools the specified account is participating in.
     *
     * An account participates in a liquidity pool by holding a trustline to the pool's
     * liquidity pool shares asset.
     *
     * @param account Account ID to filter liquidity pools
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/liquiditypools/list/">List All Liquidity Pools</a>
     */
    fun forAccount(account: String): LiquidityPoolsRequestBuilder {
        uriBuilder.parameters[ACCOUNT_PARAMETER_NAME] = account
        return this
    }

    /**
     * Build and execute request to get a page of liquidity pools.
     *
     * @return Page of liquidity pool responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/resources/liquiditypools/list/">List All Liquidity Pools</a>
     */
    suspend fun execute(): Page<LiquidityPoolResponse> {
        return executeGetRequest(buildUrl())
    }

    /**
     * Sets the cursor parameter for pagination.
     *
     * A cursor is a value that points to a specific location in a collection of resources.
     * Use this to retrieve results starting from a specific liquidity pool.
     *
     * @param cursor A paging token from a previous response
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/introduction/pagination/">Pagination documentation</a>
     */
    override fun cursor(cursor: String): LiquidityPoolsRequestBuilder {
        super.cursor(cursor)
        return this
    }

    /**
     * Sets the limit parameter defining maximum number of liquidity pools to return.
     *
     * The maximum limit is 200. If not specified, Horizon will use a default limit (typically 10).
     *
     * @param number Maximum number of liquidity pools to return (max 200)
     * @return This request builder instance
     */
    override fun limit(number: Int): LiquidityPoolsRequestBuilder {
        super.limit(number)
        return this
    }

    /**
     * Sets the order parameter defining the order in which to return liquidity pools.
     *
     * @param direction The order direction (ASC for ascending, DESC for descending)
     * @return This request builder instance
     */
    override fun order(direction: Order): LiquidityPoolsRequestBuilder {
        super.order(direction)
        return this
    }
}
