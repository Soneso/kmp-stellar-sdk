package com.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.stellar.sdk.horizon.exceptions.*
import com.stellar.sdk.horizon.responses.OfferResponse
import com.stellar.sdk.horizon.responses.Page

/**
 * Builds requests connected to offers.
 *
 * Offers represent an intent to trade one asset for another at a pre-determined exchange rate.
 * This builder allows you to query offers based on various criteria such as seller, sponsor,
 * or the assets being bought and sold.
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Get a specific offer by ID
 * val offer = server.offers().offer(12345)
 *
 * // Get offers by seller
 * val sellerOffers = server.offers()
 *     .forSeller("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .limit(20)
 *     .execute()
 *
 * // Get offers selling a specific asset
 * val assetOffers = server.offers()
 *     .forSellingAsset("credit_alphanum4", "USD", "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .execute()
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/resources/offers/">Offers documentation</a>
 */
class OffersRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "offers") {

    /**
     * Requests a specific offer by ID.
     *
     * @param offerId The offer ID to fetch
     * @return The offer response
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/resources/offers/single/">Offer Details</a>
     */
    suspend fun offer(offerId: Long): OfferResponse {
        setSegments("offers", offerId.toString())
        return executeGetRequest(buildUrl())
    }

    /**
     * Returns all offers sponsored by a given account.
     *
     * @param sponsor Account ID of the sponsor
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/offers/list/">List All Offers</a>
     */
    fun forSponsor(sponsor: String): OffersRequestBuilder {
        uriBuilder.parameters["sponsor"] = sponsor
        return this
    }

    /**
     * Returns all offers where the given account is the seller.
     *
     * @param seller Account ID of the offer creator
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/offers/list/">List All Offers</a>
     */
    fun forSeller(seller: String): OffersRequestBuilder {
        uriBuilder.parameters["seller"] = seller
        return this
    }

    /**
     * Returns all offers buying an asset.
     *
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer account ID (null for native)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/offers/list/">List All Offers</a>
     */
    fun forBuyingAsset(
        assetType: String,
        assetCode: String? = null,
        assetIssuer: String? = null
    ): OffersRequestBuilder {
        setAssetParameter("buying", assetType, assetCode, assetIssuer)
        return this
    }

    /**
     * Returns all offers selling an asset.
     *
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer account ID (null for native)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/offers/list/">List All Offers</a>
     */
    fun forSellingAsset(
        assetType: String,
        assetCode: String? = null,
        assetIssuer: String? = null
    ): OffersRequestBuilder {
        setAssetParameter("selling", assetType, assetCode, assetIssuer)
        return this
    }

    /**
     * Build and execute request to get a page of offers.
     *
     * @return Page of offer responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/resources/offers/list/">List All Offers</a>
     */
    suspend fun execute(): Page<OfferResponse> {
        return executeGetRequest(buildUrl())
    }

    /**
     * Sets the cursor parameter for pagination.
     *
     * A cursor is a value that points to a specific location in a collection of resources.
     * Use this to retrieve results starting from a specific offer.
     *
     * @param cursor A paging token from a previous response
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/introduction/pagination/">Pagination documentation</a>
     */
    override fun cursor(cursor: String): OffersRequestBuilder {
        super.cursor(cursor)
        return this
    }

    /**
     * Sets the limit parameter defining maximum number of offers to return.
     *
     * The maximum limit is 200. If not specified, Horizon will use a default limit (typically 10).
     *
     * @param number Maximum number of offers to return (max 200)
     * @return This request builder instance
     */
    override fun limit(number: Int): OffersRequestBuilder {
        super.limit(number)
        return this
    }

    /**
     * Sets the order parameter defining the order in which to return offers.
     *
     * @param direction The order direction (ASC for ascending, DESC for descending)
     * @return This request builder instance
     */
    override fun order(direction: Order): OffersRequestBuilder {
        super.order(direction)
        return this
    }
}
