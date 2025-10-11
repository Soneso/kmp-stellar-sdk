package com.soneso.stellar.sdk.horizon.responses

/**
 * Interface for responses that support pagination.
 * Responses implementing this interface contain a paging token that can be used
 * to fetch the next page of results or resume streaming from a specific point.
 */
interface Pageable {
    /**
     * Returns the paging token (cursor) for this response.
     * This token can be used to fetch the next page of results or resume streaming.
     *
     * @return The paging token string
     */
    val pagingToken: String
}
