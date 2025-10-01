package com.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.stellar.sdk.horizon.exceptions.*
import com.stellar.sdk.horizon.responses.PathResponse
import com.stellar.sdk.horizon.responses.Page

/**
 * Builds requests connected to strict send paths.
 *
 * The strict send path finding endpoint lists the possible payment paths that can be taken
 * starting with a specific source asset and amount. This is useful when you know how much
 * you want to send and want to find out what destinations are reachable.
 *
 * You must specify:
 * - Source asset (what you're sending)
 * - Source amount (how much you're sending)
 * - Either a destination account OR a list of destination assets
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Find paths sending 10 XLM to a specific account
 * val paths = server.strictSendPaths()
 *     .sourceAsset("native")
 *     .sourceAmount("10.0")
 *     .destinationAccount("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .execute()
 *
 * // Find paths sending 100 USD to accounts holding EUR or GBP
 * val paths2 = server.strictSendPaths()
 *     .sourceAsset("credit_alphanum4", "USD", "ISSUER_ID")
 *     .sourceAmount("100.0")
 *     .destinationAssets(listOf(
 *         Triple("credit_alphanum4", "EUR", "ISSUER_ID"),
 *         Triple("credit_alphanum4", "GBP", "ISSUER_ID")
 *     ))
 *     .execute()
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-send/">Strict Send Paths documentation</a>
 */
class StrictSendPathsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, null) {

    init {
        setSegments("paths", "strict-send")
    }

    /**
     * Sets the destination account for the path search.
     * Cannot be used together with destinationAssets().
     *
     * @param account The account ID of the destination
     * @return This request builder instance
     * @throws IllegalArgumentException if destinationAssets has already been set
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-send/">Strict Send Paths</a>
     */
    fun destinationAccount(account: String): StrictSendPathsRequestBuilder {
        require(uriBuilder.parameters["destination_assets"] == null) {
            "cannot set both destination_assets and destination_account"
        }
        uriBuilder.parameters["destination_account"] = account
        return this
    }

    /**
     * Sets the list of destination assets for the path search.
     * Cannot be used together with destinationAccount().
     *
     * @param assets List of assets, where each asset is a triple of (type, code, issuer)
     * @return This request builder instance
     * @throws IllegalArgumentException if destinationAccount has already been set
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-send/">Strict Send Paths</a>
     */
    fun destinationAssets(assets: List<Triple<String, String?, String?>>): StrictSendPathsRequestBuilder {
        require(uriBuilder.parameters["destination_account"] == null) {
            "cannot set both destination_assets and destination_account"
        }
        setAssetsParameter("destination_assets", assets)
        return this
    }

    /**
     * Sets the source amount for the path search.
     * This is the amount you want to send.
     *
     * @param amount The amount to send as a string (to preserve precision)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-send/">Strict Send Paths</a>
     */
    fun sourceAmount(amount: String): StrictSendPathsRequestBuilder {
        uriBuilder.parameters["source_amount"] = amount
        return this
    }

    /**
     * Sets the source asset for the path search.
     * This is the asset you want to send.
     *
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer account ID (null for native)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-send/">Strict Send Paths</a>
     */
    fun sourceAsset(assetType: String, assetCode: String? = null, assetIssuer: String? = null): StrictSendPathsRequestBuilder {
        setAssetTypeParameters("source", assetType, assetCode, assetIssuer)
        return this
    }

    /**
     * Build and execute request to get a page of paths.
     *
     * @return Page of path responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-send/">Strict Send Paths</a>
     */
    suspend fun execute(): Page<PathResponse> {
        return executeGetRequest(buildUrl())
    }

    /**
     * Sets the cursor parameter for pagination.
     *
     * A cursor is a value that points to a specific location in a collection of resources.
     * Use this to retrieve results starting from a specific path.
     *
     * @param cursor A paging token from a previous response
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/introduction/pagination/">Pagination documentation</a>
     */
    override fun cursor(cursor: String): StrictSendPathsRequestBuilder {
        super.cursor(cursor)
        return this
    }

    /**
     * Sets the limit parameter defining maximum number of paths to return.
     *
     * The maximum limit is 200. If not specified, Horizon will use a default limit (typically 10).
     *
     * @param number Maximum number of paths to return (max 200)
     * @return This request builder instance
     */
    override fun limit(number: Int): StrictSendPathsRequestBuilder {
        super.limit(number)
        return this
    }

    /**
     * Sets the order parameter defining the order in which to return paths.
     *
     * @param direction The order direction (ASC for ascending, DESC for descending)
     * @return This request builder instance
     */
    override fun order(direction: Order): StrictSendPathsRequestBuilder {
        super.order(direction)
        return this
    }
}
