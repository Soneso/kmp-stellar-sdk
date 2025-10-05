package com.stellar.sdk.rpc.responses

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
     * Note: This is a placeholder for XDR parsing functionality.
     * The actual implementation will be added when XDR parsing utilities are available.
     *
     * @return the parsed TransactionResult object, or null if errorResultXdr is null
     * @throws IllegalStateException if XDR parsing is not yet implemented
     */
    fun parseErrorResultXdr(): Any? {
        if (errorResultXdr == null) return null
        // TODO: Implement XDR parsing when XDR utilities are available
        // return TransactionResult.fromXdrBase64(errorResultXdr)
        throw IllegalStateException("XDR parsing not yet implemented")
    }

    /**
     * Parses the [diagnosticEventsXdr] field from a list of base64-encoded strings
     * to a list of DiagnosticEvent XDR objects.
     *
     * Diagnostic events can help debug why a transaction failed or understand its execution.
     *
     * Note: This is a placeholder for XDR parsing functionality.
     * The actual implementation will be added when XDR parsing utilities are available.
     *
     * @return list of parsed DiagnosticEvent objects, or null if diagnosticEventsXdr is null
     * @throws IllegalStateException if XDR parsing is not yet implemented
     */
    fun parseDiagnosticEventsXdr(): List<Any>? {
        if (diagnosticEventsXdr == null) return null
        // TODO: Implement XDR parsing when XDR utilities are available
        // return diagnosticEventsXdr.map { DiagnosticEvent.fromXdrBase64(it) }
        throw IllegalStateException("XDR parsing not yet implemented")
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
