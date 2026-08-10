package com.soneso.stellar.sdk.contract.exception

import com.soneso.stellar.sdk.contract.AssembledTransaction
import com.soneso.stellar.sdk.rpc.responses.SendTransactionResponse
import com.soneso.stellar.sdk.rpc.responses.SendTransactionStatus

/**
 * Exception thrown when sending a transaction to the network fails.
 *
 * This indicates that the transaction was rejected by the network before being
 * queued for consensus. Common causes include invalid signatures, expired transaction,
 * an inclusion fee below the current network minimum, or a full transaction queue.
 *
 * @property message The error message
 * @property assembledTransaction The AssembledTransaction that failed to send. Null when
 * the failure comes from a [com.soneso.stellar.sdk.contract.ContractClient] deploy or
 * install call, which submits without an AssembledTransaction.
 */
class SendTransactionFailedException(
    message: String,
    assembledTransaction: AssembledTransaction<*>? = null
) : ContractException(message, assembledTransaction) {

    companion object {
        /**
         * Builds the failure report for a sendTransaction response the network did
         * not accept: the status, the error result XDR with its parsed form when the
         * network supplied one, and any diagnostic events. Every submission path
         * raising [SendTransactionFailedException] reports through this builder.
         */
        internal fun describe(sendResponse: SendTransactionResponse): String = buildString {
            append("Sending the transaction to the network failed! Status: ${sendResponse.status}")

            if (sendResponse.status == SendTransactionStatus.ERROR && sendResponse.errorResultXdr != null) {
                append("\nError Result XDR: ${sendResponse.errorResultXdr}")
                try {
                    val txResult = sendResponse.parseErrorResultXdr()
                    append("\nParsed Error: $txResult")
                } catch (e: Exception) {
                    append("\n(Could not parse error XDR: ${e.message})")
                }
            }

            if (sendResponse.diagnosticEventsXdr != null && sendResponse.diagnosticEventsXdr.isNotEmpty()) {
                append("\nDiagnostic Events: ${sendResponse.diagnosticEventsXdr.joinToString(", ")}")
            }
        }
    }
}
