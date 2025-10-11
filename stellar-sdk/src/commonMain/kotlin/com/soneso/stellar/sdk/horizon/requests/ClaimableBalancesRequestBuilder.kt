package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.ClaimableBalanceResponse
import com.soneso.stellar.sdk.horizon.responses.Page

/**
 * Builds requests connected to claimable balances.
 *
 * Claimable balances are used for trustless, non-interactive asset transfers.
 * They can be claimed by the intended destination account(s) when certain conditions are met.
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Get a specific claimable balance by ID
 * val balance = server.claimableBalances()
 *     .claimableBalance("00000000da0d57da7d4850e7fc10d2a9d0ebc731f7afb40574c03395b17d49149b91f5be")
 *
 * // Get claimable balances for a specific sponsor
 * val sponsoredBalances = server.claimableBalances()
 *     .forSponsor("GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .limit(20)
 *     .execute()
 *
 * // Get claimable balances for a specific asset
 * val assetBalances = server.claimableBalances()
 *     .forAsset("credit_alphanum4", "USD", "GCDNJUBQSX7AJWLJACMJ7I4BC3Z47BQUTMHEICZLE6MU4KQBRYG5JY6B")
 *     .execute()
 *
 * // Get claimable balances claimable by a specific account
 * val claimantBalances = server.claimableBalances()
 *     .forClaimant("GBVFTZL5HIPT4PFQVTZVIWR77V7LWYCXU4CLYWWHHOEXB64XPG5LDMTU")
 *     .execute()
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/resources/claimablebalances/">Claimable Balances documentation</a>
 */
class ClaimableBalancesRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "claimable_balances") {

    /**
     * Requests a specific claimable balance by ID.
     *
     * @param claimableBalanceId The claimable balance ID to fetch
     * @return The claimable balance response
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/resources/claimablebalances/single/">Claimable Balance Details</a>
     */
    suspend fun claimableBalance(claimableBalanceId: String): ClaimableBalanceResponse {
        setSegments("claimable_balances", claimableBalanceId)
        return executeGetRequest(buildUrl())
    }

    /**
     * Returns all claimable balances sponsored by a given account.
     *
     * @param sponsor Account ID of the sponsor
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/claimablebalances/list/">List All Claimable Balances</a>
     */
    fun forSponsor(sponsor: String): ClaimableBalancesRequestBuilder {
        uriBuilder.parameters["sponsor"] = sponsor
        return this
    }

    /**
     * Returns all claimable balances which hold a given asset.
     *
     * @param assetType The asset type (native, credit_alphanum4, credit_alphanum12)
     * @param assetCode The asset code (null for native)
     * @param assetIssuer The asset issuer account ID (null for native)
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/claimablebalances/list/">List All Claimable Balances</a>
     */
    fun forAsset(
        assetType: String,
        assetCode: String? = null,
        assetIssuer: String? = null
    ): ClaimableBalancesRequestBuilder {
        setAssetParameter("asset", assetType, assetCode, assetIssuer)
        return this
    }

    /**
     * Returns all claimable balances which can be claimed by a given account ID.
     *
     * @param claimant Account ID of the address which can claim the claimable balance
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/resources/claimablebalances/list/">List All Claimable Balances</a>
     */
    fun forClaimant(claimant: String): ClaimableBalancesRequestBuilder {
        uriBuilder.parameters["claimant"] = claimant
        return this
    }

    /**
     * Build and execute request to get a page of claimable balances.
     *
     * @return Page of claimable balance responses
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/resources/claimablebalances/list/">List All Claimable Balances</a>
     */
    suspend fun execute(): Page<ClaimableBalanceResponse> {
        return executeGetRequest(buildUrl())
    }

    /**
     * Sets the cursor parameter for pagination.
     *
     * A cursor is a value that points to a specific location in a collection of resources.
     * Use this to retrieve results starting from a specific claimable balance.
     *
     * @param cursor A paging token from a previous response
     * @return This request builder instance
     * @see <a href="https://developers.stellar.org/api/introduction/pagination/">Pagination documentation</a>
     */
    override fun cursor(cursor: String): ClaimableBalancesRequestBuilder {
        super.cursor(cursor)
        return this
    }

    /**
     * Sets the limit parameter defining maximum number of claimable balances to return.
     *
     * The maximum limit is 200. If not specified, Horizon will use a default limit (typically 10).
     *
     * @param number Maximum number of claimable balances to return (max 200)
     * @return This request builder instance
     */
    override fun limit(number: Int): ClaimableBalancesRequestBuilder {
        super.limit(number)
        return this
    }

    /**
     * Sets the order parameter defining the order in which to return claimable balances.
     *
     * @param direction The order direction (ASC for ascending, DESC for descending)
     * @return This request builder instance
     */
    override fun order(direction: Order): ClaimableBalancesRequestBuilder {
        super.order(direction)
        return this
    }
}
