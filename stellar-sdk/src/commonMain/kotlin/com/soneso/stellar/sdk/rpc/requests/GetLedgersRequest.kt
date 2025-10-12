package com.soneso.stellar.sdk.rpc.requests

import kotlinx.serialization.Serializable

/**
 * Request for JSON-RPC method getLedgers.
 *
 * Fetches a list of ledgers starting from a specified ledger sequence or using cursor-based pagination.
 *
 * @property startLedger Ledger sequence number to start fetching ledgers from (inclusive). Optional when using cursor-based pagination.
 * @property pagination Optional pagination configuration for limiting and controlling result sets.
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getLedgers">getLedgers documentation</a>
 */
@Serializable
data class GetLedgersRequest(
    val startLedger: Long? = null,
    val pagination: Pagination? = null
) {
    init {
        // When using cursor-based pagination, startLedger should be omitted (null)
        // When not using cursor, startLedger is required
        if (pagination?.cursor == null && startLedger == null) {
            throw IllegalArgumentException("startLedger must be provided when not using cursor-based pagination")
        }

        startLedger?.let {
            require(it > 0) { "startLedger must be positive" }
        }

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
     * @property limit Maximum number of ledgers to return (default and max value is 200).
     */
    @Serializable
    data class Pagination(
        val cursor: String? = null,
        val limit: Long? = null
    )
}
