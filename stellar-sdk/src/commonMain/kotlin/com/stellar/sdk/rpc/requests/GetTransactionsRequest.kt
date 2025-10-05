package com.stellar.sdk.rpc.requests

import kotlinx.serialization.Serializable

/**
 * Request for JSON-RPC method getTransactions.
 *
 * Fetches a list of transactions starting from a specified ledger sequence.
 *
 * @property startLedger Ledger sequence number to start fetching transactions from (inclusive).
 * @property pagination Optional pagination configuration for limiting and controlling result sets.
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getTransactions">getTransactions documentation</a>
 */
@Serializable
data class GetTransactionsRequest(
    val startLedger: Long,
    val pagination: Pagination? = null
) {
    init {
        require(startLedger > 0) { "startLedger must be positive" }
        pagination?.let {
            it.limit?.let { limit ->
                require(limit > 0) { "pagination.limit must be positive" }
            }
        }
    }

    /**
     * Pagination options for controlling the number of results returned.
     *
     * @property cursor Continuation token from a previous response for fetching the next page.
     * @property limit Maximum number of transactions to return (default and max value is 200).
     */
    @Serializable
    data class Pagination(
        val cursor: String? = null,
        val limit: Long? = null
    )
}
