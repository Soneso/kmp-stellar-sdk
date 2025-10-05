package com.stellar.sdk.rpc.requests

import kotlinx.serialization.Serializable

/**
 * Request for JSON-RPC method getTransaction.
 *
 * Fetches a single transaction's status, details, and history.
 *
 * @property hash Transaction hash as a hex-encoded string (64 characters).
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getTransaction">getTransaction documentation</a>
 */
@Serializable
data class GetTransactionRequest(
    val hash: String
) {
    init {
        require(hash.isNotBlank()) { "hash must not be blank" }
        require(hash.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            "hash must be a 64-character hexadecimal string"
        }
    }
}
