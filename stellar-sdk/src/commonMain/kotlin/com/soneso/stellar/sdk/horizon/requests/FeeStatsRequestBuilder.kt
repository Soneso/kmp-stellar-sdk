package com.soneso.stellar.sdk.horizon.requests

import io.ktor.client.*
import io.ktor.http.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.horizon.responses.FeeStatsResponse

/**
 * Builds requests connected to fee statistics.
 *
 * The fee stats endpoint provides information about transaction fees network validators
 * are currently accepting. This data is crucial for setting appropriate transaction fees
 * to ensure timely transaction acceptance by the network.
 *
 * This endpoint has no parameters - it simply returns the current fee statistics.
 *
 * Example usage:
 * ```kotlin
 * val server = HorizonServer("https://horizon.stellar.org")
 *
 * // Get current fee statistics
 * val feeStats = server.feeStats().execute()
 *
 * println("Last ledger: ${feeStats.lastLedger}")
 * println("Base fee: ${feeStats.lastLedgerBaseFee} stroops")
 * println("Ledger capacity usage: ${feeStats.ledgerCapacityUsage}")
 *
 * // Get recommended fee (e.g., use p90 for high priority)
 * val recommendedFee = feeStats.feeCharged.p90
 * println("Recommended fee (90th percentile): $recommendedFee stroops")
 *
 * // Get median fee
 * val medianFee = feeStats.feeCharged.p50
 * println("Median fee: $medianFee stroops")
 * ```
 *
 * @see <a href="https://developers.stellar.org/api/aggregations/fee-stats/">Fee Stats documentation</a>
 */
class FeeStatsRequestBuilder(
    httpClient: HttpClient,
    serverUri: Url
) : RequestBuilder(httpClient, serverUri, "fee_stats") {

    /**
     * Build and execute request to get fee statistics.
     *
     * @return FeeStatsResponse containing current fee statistics
     * @throws NetworkException All the exceptions below are subclasses of NetworkException
     * @throws BadRequestException If the request fails due to a bad request (4xx)
     * @throws BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws UnknownResponseException If the server returns an unknown status code
     * @throws ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     *
     * @see <a href="https://developers.stellar.org/api/aggregations/fee-stats/retrieve/">Retrieve Fee Stats</a>
     */
    suspend fun execute(): FeeStatsResponse {
        return executeGetRequest(buildUrl())
    }

    /**
     * Fee stats endpoint doesn't support cursor pagination.
     * This method throws UnsupportedOperationException.
     */
    override fun cursor(cursor: String): FeeStatsRequestBuilder {
        throw UnsupportedOperationException("cursor() is not supported on fee_stats endpoint")
    }

    /**
     * Fee stats endpoint doesn't support limit parameter.
     * This method throws UnsupportedOperationException.
     */
    override fun limit(number: Int): FeeStatsRequestBuilder {
        throw UnsupportedOperationException("limit() is not supported on fee_stats endpoint")
    }

    /**
     * Fee stats endpoint doesn't support order parameter.
     * This method throws UnsupportedOperationException.
     */
    override fun order(direction: Order): FeeStatsRequestBuilder {
        throw UnsupportedOperationException("order() is not supported on fee_stats endpoint")
    }
}
