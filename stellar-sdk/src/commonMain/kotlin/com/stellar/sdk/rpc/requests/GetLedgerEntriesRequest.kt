package com.stellar.sdk.rpc.requests

import kotlinx.serialization.Serializable

/**
 * Request for JSON-RPC method getLedgerEntries.
 *
 * Allows reading the current value of ledger entries directly.
 *
 * @property keys List of base64-encoded XDR LedgerKey objects representing the ledger entries to retrieve.
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getLedgerEntries">getLedgerEntries documentation</a>
 */
@Serializable
data class GetLedgerEntriesRequest(
    val keys: List<String>
) {
    init {
        require(keys.isNotEmpty()) { "keys must not be empty" }
        keys.forEach { key ->
            require(key.isNotBlank()) { "keys must not contain blank entries" }
        }
    }
}
