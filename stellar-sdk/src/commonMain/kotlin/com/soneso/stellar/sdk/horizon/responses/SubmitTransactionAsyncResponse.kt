package com.soneso.stellar.sdk.horizon.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents the response from the "Submit a Transaction Asynchronously" endpoint of Horizon API.
 *
 * Unlike the synchronous submission, which blocks and waits for the transaction to be ingested
 * in Horizon, this endpoint relays the response from Stellar Core directly back to the user.
 *
 * @property hash The transaction hash
 * @property txStatus The status of the transaction submission
 * @property errorResultXdr The XDR-encoded TransactionResult (only present if status is ERROR)
 * @property httpResponseCode The HTTP status code of the response (201, 409, 400, etc.)
 *
 * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/submit-async-transaction">Submit Transaction Asynchronously</a>
 */
@Serializable
data class SubmitTransactionAsyncResponse(
    @SerialName("hash")
    val hash: String,

    @SerialName("tx_status")
    val txStatus: TransactionStatus,

    @SerialName("error_result_xdr")
    val errorResultXdr: String? = null,

    @Transient
    var httpResponseCode: Int = 0
) : Response() {

    /**
     * Transaction status values returned by async submission.
     */
    @Serializable
    enum class TransactionStatus {
        /**
         * The transaction failed and was not included in the ledger.
         */
        @SerialName("ERROR")
        ERROR,

        /**
         * The transaction is pending and may still be included in a ledger.
         */
        @SerialName("PENDING")
        PENDING,

        /**
         * The transaction was a duplicate of a previously submitted transaction.
         */
        @SerialName("DUPLICATE")
        DUPLICATE,

        /**
         * The transaction should be resubmitted later.
         */
        @SerialName("TRY_AGAIN_LATER")
        TRY_AGAIN_LATER
    }
}
