package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.OrderBookResponse

/**
 * Builds requests connected to order books.
 *
 * Order books show the current buy and sell offers for a given asset pair. This builder allows
 * you to specify the buying and selling assets to query the order book between them.
 *
 * Note: This endpoint returns a single OrderBookResponse, not a paginated result.
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Get order book for XLM/USD trading pair
 * val orderBook = server.orderBook()
 *     .buyingAsset("native")
 *     .sellingAsset("credit_alphanum4", "USD", "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .execute()
 *
 * // Access bids and asks
 * for (bid in orderBook.bids) {
 *     println("Bid: ${bid.amount} at ${bid.price}")
 * }
 * for (ask in orderBook.asks) {
 *     println("Ask: ${ask.amount} at ${ask.price}")
 * }
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/aggregations/order-books/">Order Books documentation</a>
 */
class OrderBookRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "order_book") {

    /**
     * Sets the buying asset for the order book query.
     *
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer account ID (null for native)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/aggregations/order-books/retrieve/">Retrieve an Order Book</a>
     */
    fun buyingAsset(assetType: String, assetCode: String? = null, assetIssuer: String? = null): OrderBookRequestBuilder {
        setAssetTypeParameters("buying", assetType, assetCode, assetIssuer)
        return this
    }

    /**
     * Sets the buying asset for the order book query.
     *
     * @param asset The asset being bought
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/aggregations/order-books/retrieve/">Retrieve an Order Book</a>
     */
    fun buyingAsset(asset: com.soneso.stellar.sdk.Asset): OrderBookRequestBuilder {
        return when (asset) {
            is com.soneso.stellar.sdk.AssetTypeNative -> buyingAsset("native")
            is com.soneso.stellar.sdk.AssetTypeCreditAlphaNum4 -> buyingAsset("credit_alphanum4", asset.code, asset.issuer)
            is com.soneso.stellar.sdk.AssetTypeCreditAlphaNum12 -> buyingAsset("credit_alphanum12", asset.code, asset.issuer)
        }
    }

    /**
     * Sets the selling asset for the order book query.
     *
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer account ID (null for native)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/aggregations/order-books/retrieve/">Retrieve an Order Book</a>
     */
    fun sellingAsset(assetType: String, assetCode: String? = null, assetIssuer: String? = null): OrderBookRequestBuilder {
        setAssetTypeParameters("selling", assetType, assetCode, assetIssuer)
        return this
    }

    /**
     * Sets the selling asset for the order book query.
     *
     * @param asset The asset being sold
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/aggregations/order-books/retrieve/">Retrieve an Order Book</a>
     */
    fun sellingAsset(asset: com.soneso.stellar.sdk.Asset): OrderBookRequestBuilder {
        return when (asset) {
            is com.soneso.stellar.sdk.AssetTypeNative -> sellingAsset("native")
            is com.soneso.stellar.sdk.AssetTypeCreditAlphaNum4 -> sellingAsset("credit_alphanum4", asset.code, asset.issuer)
            is com.soneso.stellar.sdk.AssetTypeCreditAlphaNum12 -> sellingAsset("credit_alphanum12", asset.code, asset.issuer)
        }
    }

    /**
     * Build and execute request to get the order book.
     *
     * Note: This endpoint returns a single OrderBookResponse, not a paginated result.
     *
     * @return OrderBookResponse containing bids and asks
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/aggregations/order-books/retrieve/">Retrieve an Order Book</a>
     */
    suspend fun execute(): OrderBookResponse {
        return executeGetRequest(buildUrl())
    }

    /**
     * Order book endpoint doesn't support cursor pagination.
     * This method throws UnsupportedOperationException.
     */
    override fun cursor(cursor: String): OrderBookRequestBuilder {
        throw UnsupportedOperationException("cursor() is not supported on order_book endpoint")
    }

    /**
     * Order book endpoint doesn't support limit parameter.
     * This method throws UnsupportedOperationException.
     */
    override fun limit(number: Int): OrderBookRequestBuilder {
        throw UnsupportedOperationException("limit() is not supported on order_book endpoint")
    }

    /**
     * Order book endpoint doesn't support order parameter.
     * This method throws UnsupportedOperationException.
     */
    override fun order(direction: Order): OrderBookRequestBuilder {
        throw UnsupportedOperationException("order() is not supported on order_book endpoint")
    }
}
