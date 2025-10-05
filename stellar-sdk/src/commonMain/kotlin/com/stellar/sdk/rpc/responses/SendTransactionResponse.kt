package com.stellar.sdk.rpc.responses

import com.stellar.sdk.xdr.DiagnosticEventXdr
import com.stellar.sdk.xdr.TransactionResultXdr
import com.stellar.sdk.xdr.fromXdrBase64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method sendTransaction.
 *
 * Returns the status of a submitted transaction. The transaction may be pending,
 * duplicate, require retry, or have an error.
 *
 * @property status The submission status of the transaction
 * @property hash Transaction hash (hex-encoded), present for PENDING and DUPLICATE status
 * @property latestLedger The latest ledger known to Soroban RPC at the time it handled the request
 * @property latestLedgerCloseTime Unix timestamp of when the latest ledger was closed
 * @property errorResultXdr Base64-encoded TransactionResult XDR (only present for ERROR status)
 * @property diagnosticEventsXdr List of base64-encoded DiagnosticEvent XDR objects for debugging (may be null)
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/sendTransaction">sendTransaction documentation</a>
 */
@Serializable
data class SendTransactionResponse(
    val status: SendTransactionStatus,
    val hash: String? = null,
    val latestLedger: Long? = null,
    val latestLedgerCloseTime: Long? = null,
    val errorResultXdr: String? = null,
    val diagnosticEventsXdr: List<String>? = null
) {
    /**
     * Parses the [errorResultXdr] field from a base64-encoded string to a TransactionResult XDR object.
     *
     * This field is only present when status is ERROR and contains the detailed error information.
     *
     * @return the parsed TransactionResult object, or null if errorResultXdr is null
     * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
     */
    fun parseErrorResultXdr(): TransactionResultXdr? {
        return errorResultXdr?.let { TransactionResultXdr.fromXdrBase64(it) }
    }

    /**
     * Parses the [diagnosticEventsXdr] field from a list of base64-encoded strings
     * to a list of DiagnosticEvent XDR objects.
     *
     * Diagnostic events can help debug why a transaction failed or understand its execution.
     *
     * @return list of parsed DiagnosticEvent objects, or null if diagnosticEventsXdr is null
     * @throws IllegalArgumentException if any XDR string is malformed or cannot be decoded
     */
    fun parseDiagnosticEventsXdr(): List<DiagnosticEventXdr>? {
        return diagnosticEventsXdr?.map { DiagnosticEventXdr.fromXdrBase64(it) }
    }
}

/**
 * Status of a transaction submission.
 *
 * @property PENDING Transaction was accepted and is pending inclusion in a ledger
 * @property DUPLICATE Transaction was already submitted and is pending or has been processed
 * @property TRY_AGAIN_LATER Server is overloaded, client should retry the submission later
 * @property ERROR Transaction was rejected due to an error (see errorResultXdr for details)
 */
@Serializable
enum class SendTransactionStatus {
    @SerialName("PENDING")
    PENDING,

    @SerialName("DUPLICATE")
    DUPLICATE,

    @SerialName("TRY_AGAIN_LATER")
    TRY_AGAIN_LATER,

    @SerialName("ERROR")
    ERROR
}
