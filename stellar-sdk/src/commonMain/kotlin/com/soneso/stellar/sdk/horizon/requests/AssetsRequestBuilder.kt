package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.AssetResponse
import com.soneso.stellar.sdk.horizon.responses.Page

/**
 * Builds requests connected to assets.
 *
 * Assets represent different types of value issued on the Stellar network. This builder allows
 * you to query assets based on their code, issuer, or retrieve all assets on the network.
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Get all assets
 * val allAssets = server.assets()
 *     .limit(20)
 *     .execute()
 *
 * // Get assets by code
 * val usdAssets = server.assets()
 *     .forAssetCode("USD")
 *     .execute()
 *
 * // Get assets by issuer
 * val issuerAssets = server.assets()
 *     .forAssetIssuer("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .execute()
 *
 * // Get a specific asset
 * val asset = server.assets()
 *     .forAssetCode("USD")
 *     .forAssetIssuer("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .execute()
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/resources/assets/">Assets documentation</a>
 */
class AssetsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "assets") {

    /**
     * Filters assets by asset code.
     *
     * @param assetCode The asset code to filter by
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/assets/list/">List All Assets</a>
     */
    fun forAssetCode(assetCode: String): AssetsRequestBuilder {
        uriBuilder.parameters["asset_code"] = assetCode
        return this
    }

    /**
     * Filters assets by asset issuer.
     *
     * @param assetIssuer The account ID of the asset issuer
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/assets/list/">List All Assets</a>
     */
    fun forAssetIssuer(assetIssuer: String): AssetsRequestBuilder {
        uriBuilder.parameters["asset_issuer"] = assetIssuer
        return this
    }

    /**
     * Build and execute request to get a page of assets.
     *
     * @return Page of asset responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/resources/assets/list/">List All Assets</a>
     */
    suspend fun execute(): Page<AssetResponse> {
        return executeGetRequest(buildUrl())
    }

    /**
     * Sets the cursor parameter for pagination.
     *
     * A cursor is a value that points to a specific location in a collection of resources.
     * Use this to retrieve results starting from a specific asset.
     *
     * @param cursor A paging token from a previous response
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/introduction/pagination/">Pagination documentation</a>
     */
    override fun cursor(cursor: String): AssetsRequestBuilder {
        super.cursor(cursor)
        return this
    }

    /**
     * Sets the limit parameter defining maximum number of assets to return.
     *
     * The maximum limit is 200. If not specified, Horizon will use a default limit (typically 10).
     *
     * @param number Maximum number of assets to return (max 200)
     * @return This request builder instance
     */
    override fun limit(number: Int): AssetsRequestBuilder {
        super.limit(number)
        return this
    }

    /**
     * Sets the order parameter defining the order in which to return assets.
     *
     * @param direction The order direction (ASC for ascending, DESC for descending)
     * @return This request builder instance
     */
    override fun order(direction: Order): AssetsRequestBuilder {
        super.order(direction)
        return this
    }
}
