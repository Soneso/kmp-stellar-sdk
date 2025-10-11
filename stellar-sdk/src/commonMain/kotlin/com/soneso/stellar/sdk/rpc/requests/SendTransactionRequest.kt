package com.soneso.stellar.sdk.rpc.requests

import kotlinx.serialization.Serializable

/**
 * Request for JSON-RPC method sendTransaction.
 *
 * Submits a transaction to the Stellar network for inclusion in the ledger.
 *
 * @property transaction Base64-encoded XDR TransactionEnvelope to submit.
 *                       The transaction must be properly signed and have valid sequence numbers.
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/sendTransaction">sendTransaction documentation</a>
 */
@Serializable
data class SendTransactionRequest(
    val transaction: String
) {
    init {
        require(transaction.isNotBlank()) { "transaction must not be blank" }
    }
}
