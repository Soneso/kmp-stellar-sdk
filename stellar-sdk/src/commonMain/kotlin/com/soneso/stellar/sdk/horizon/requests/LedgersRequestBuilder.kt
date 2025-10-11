package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.LedgerResponse
import com.soneso.stellar.sdk.horizon.responses.Page

/**
 * Builds requests connected to ledgers.
 *
 * Ledgers are the foundation of the Stellar network. They represent the state of the network
 * at a particular point in time and contain all the changes (transactions) that have occurred
 * since the previous ledger.
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Get a specific ledger by sequence
 * val ledger = server.ledgers().ledger(12345)
 *
 * // Get a page of ledgers
 * val ledgers = server.ledgers()
 *     .limit(10)
 *     .order(Order.DESC)
 *     .execute()
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/resources/ledgers/">Ledgers documentation</a>
 */
class LedgersRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "ledgers") {

    /**
     * Requests a specific ledger by sequence number.
     *
     * @param sequence The ledger sequence number to fetch
     * @return The ledger response
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/resources/ledgers/single/">Ledger Details</a>
     */
    suspend fun ledger(sequence: Long): LedgerResponse {
        setSegments("ledgers", sequence.toString())
        return executeGetRequest(buildUrl())
    }

    /**
     * Build and execute request to get a page of ledgers.
     *
     * @return Page of ledger responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/resources/ledgers/list/">List All Ledgers</a>
     */
    suspend fun execute(): Page<LedgerResponse> {
        return executeGetRequest(buildUrl())
    }

    /**
     * Sets the cursor parameter for pagination.
     *
     * A cursor is a value that points to a specific location in a collection of resources.
     * Use this to retrieve results starting from a specific ledger.
     *
     * @param cursor A paging token from a previous response
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/introduction/pagination/">Pagination documentation</a>
     */
    override fun cursor(cursor: String): LedgersRequestBuilder {
        super.cursor(cursor)
        return this
    }

    /**
     * Sets the limit parameter defining maximum number of ledgers to return.
     *
     * The maximum limit is 200. If not specified, Horizon will use a default limit (typically 10).
     *
     * @param number Maximum number of ledgers to return (max 200)
     * @return This request builder instance
     */
    override fun limit(number: Int): LedgersRequestBuilder {
        super.limit(number)
        return this
    }

    /**
     * Sets the order parameter defining the order in which to return ledgers.
     *
     * @param direction The order direction (ASC for ascending by sequence, DESC for descending)
     * @return This request builder instance
     */
    override fun order(direction: Order): LedgersRequestBuilder {
        super.order(direction)
        return this
    }
}
