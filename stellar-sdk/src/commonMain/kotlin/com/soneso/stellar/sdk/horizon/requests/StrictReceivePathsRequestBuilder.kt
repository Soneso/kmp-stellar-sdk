package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.PathResponse
import com.soneso.stellar.sdk.horizon.responses.Page

/**
 * Builds requests connected to strict receive paths.
 *
 * The strict receive path finding endpoint lists the possible payment paths that can be taken
 * to arrive at a specific destination asset and amount. This is useful when you know how much
 * you want to receive and want to find out what sources can provide it.
 *
 * You must specify:
 * - Either a source account OR a list of source assets (what you're sending from)
 * - Destination asset (what you want to receive)
 * - Destination amount (how much you want to receive)
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Find paths to receive 50 USD from a specific account
 * val paths = server.strictReceivePaths()
 *     .sourceAccount("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .destinationAsset("credit_alphanum4", "USD", "ISSUER_ID")
 *     .destinationAmount("50.0")
 *     .execute()
 *
 * // Find paths to receive 100 EUR from accounts holding XLM or USD
 * val paths2 = server.strictReceivePaths()
 *     .sourceAssets(listOf(
 *         Triple("native", null, null),
 *         Triple("credit_alphanum4", "USD", "ISSUER_ID")
 *     ))
 *     .destinationAsset("credit_alphanum4", "EUR", "ISSUER_ID")
 *     .destinationAmount("100.0")
 *     .execute()
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-receive/">Strict Receive Paths documentation</a>
 */
class StrictReceivePathsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, null) {

    init {
        setSegments("paths", "strict-receive")
    }

    /**
     * Sets the destination account for the path search.
     * This is optional - paths can be found without specifying a destination account.
     *
     * @param account The account ID of the destination
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-receive/">Strict Receive Paths</a>
     */
    fun destinationAccount(account: String): StrictReceivePathsRequestBuilder {
        uriBuilder.parameters["destination_account"] = account
        return this
    }

    /**
     * Sets the source account for the path search.
     * Cannot be used together with sourceAssets().
     *
     * @param account The account ID of the source
     * @return This request builder instance
     * @throws IllegalArgumentException if sourceAssets has already been set
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-receive/">Strict Receive Paths</a>
     */
    fun sourceAccount(account: String): StrictReceivePathsRequestBuilder {
        require(uriBuilder.parameters["source_assets"] == null) {
            "cannot set both source_assets and source_account"
        }
        uriBuilder.parameters["source_account"] = account
        return this
    }

    /**
     * Sets the list of source assets for the path search.
     * Cannot be used together with sourceAccount().
     *
     * @param assets List of assets, where each asset is a triple of (type, code, issuer)
     * @return This request builder instance
     * @throws IllegalArgumentException if sourceAccount has already been set
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-receive/">Strict Receive Paths</a>
     */
    fun sourceAssets(assets: List<Triple<String, String?, String?>>): StrictReceivePathsRequestBuilder {
        require(uriBuilder.parameters["source_account"] == null) {
            "cannot set both source_assets and source_account"
        }
        setAssetsParameter("source_assets", assets)
        return this
    }

    /**
     * Sets the destination amount for the path search.
     * This is the amount you want to receive.
     *
     * @param amount The amount to receive as a string (to preserve precision)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-receive/">Strict Receive Paths</a>
     */
    fun destinationAmount(amount: String): StrictReceivePathsRequestBuilder {
        uriBuilder.parameters["destination_amount"] = amount
        return this
    }

    /**
     * Sets the destination asset for the path search.
     * This is the asset you want to receive.
     *
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer account ID (null for native)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-receive/">Strict Receive Paths</a>
     */
    fun destinationAsset(assetType: String, assetCode: String? = null, assetIssuer: String? = null): StrictReceivePathsRequestBuilder {
        setAssetTypeParameters("destination", assetType, assetCode, assetIssuer)
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
     * @see <a href="https://developers.stellar.org/api/aggregations/paths/strict-receive/">Strict Receive Paths</a>
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
    override fun cursor(cursor: String): StrictReceivePathsRequestBuilder {
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
    override fun limit(number: Int): StrictReceivePathsRequestBuilder {
        super.limit(number)
        return this
    }

    /**
     * Sets the order parameter defining the order in which to return paths.
     *
     * @param direction The order direction (ASC for ascending, DESC for descending)
     * @return This request builder instance
     */
    override fun order(direction: Order): StrictReceivePathsRequestBuilder {
        super.order(direction)
        return this
    }
}
