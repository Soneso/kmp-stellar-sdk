package com.soneso.stellar.sdk.rpc.responses

import com.soneso.stellar.sdk.xdr.DiagnosticEventXdr
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.TransactionMetaXdr
import com.soneso.stellar.sdk.xdr.TransactionResultXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method getTransactions.
 *
 * Returns a detailed list of transactions starting from the specified ledger.
 * This method allows for paginated retrieval of transaction data.
 *
 * @property transactions List of transaction information objects
 * @property latestLedger The latest ledger known to Soroban RPC at the time it handled the request
 * @property latestLedgerCloseTimestamp Unix timestamp of when the latest ledger was closed
 * @property oldestLedger The oldest ledger retained by Soroban RPC
 * @property oldestLedgerCloseTimestamp Unix timestamp of when the oldest ledger was closed
 * @property cursor Cursor value to be used for subsequent requests for more transactions
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getTransactions">getTransactions documentation</a>
 */
@Serializable
data class GetTransactionsResponse(
    val transactions: List<TransactionInfo>,
    val latestLedger: Long,
    val latestLedgerCloseTimestamp: Long,
    val oldestLedger: Long,
    val oldestLedgerCloseTimestamp: Long,
    val cursor: String
) {
    /**
     * Information about a single transaction in the response.
     *
     * @property status The status of the transaction (SUCCESS or FAILED)
     * @property txHash The transaction hash (hex-encoded)
     * @property applicationOrder The index of the transaction among all transactions included in the ledger
     * @property feeBump Whether this transaction is a fee bump transaction
     * @property envelopeXdr Base64-encoded TransactionEnvelope XDR
     * @property resultXdr Base64-encoded TransactionResult XDR
     * @property resultMetaXdr Base64-encoded TransactionMeta XDR
     * @property ledger The ledger sequence number that included this transaction
     * @property createdAt Unix timestamp of when the transaction was included in the ledger
     * @property diagnosticEventsXdr Deprecated - List of base64-encoded DiagnosticEvent XDR objects. Use [events] instead.
     * @property events Transaction events data
     */
    @Serializable
    data class TransactionInfo(
        val status: TransactionStatus,
        val txHash: String,
        val applicationOrder: Int,
        val feeBump: Boolean,
        val envelopeXdr: String,
        val resultXdr: String,
        val resultMetaXdr: String,
        val ledger: Long,
        val createdAt: Long,
        @Deprecated("Use events.diagnosticEventsXdr instead. This field will be removed in a future version.")
        val diagnosticEventsXdr: List<String>? = null,
        val events: Events? = null
    ) {
        /**
         * Parses the [envelopeXdr] field from a base64-encoded string to a TransactionEnvelope XDR object.
         *
         * @return the parsed TransactionEnvelope object
         * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
         */
        fun parseEnvelopeXdr(): TransactionEnvelopeXdr {
            return TransactionEnvelopeXdr.fromXdrBase64(envelopeXdr)
        }

        /**
         * Parses the [resultXdr] field from a base64-encoded string to a TransactionResult XDR object.
         *
         * @return the parsed TransactionResult object
         * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
         */
        fun parseResultXdr(): TransactionResultXdr {
            return TransactionResultXdr.fromXdrBase64(resultXdr)
        }

        /**
         * Parses the [resultMetaXdr] field from a base64-encoded string to a TransactionMeta XDR object.
         *
         * @return the parsed TransactionMeta object
         * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
         */
        fun parseResultMetaXdr(): TransactionMetaXdr {
            return TransactionMetaXdr.fromXdrBase64(resultMetaXdr)
        }

        /**
         * Parses the deprecated [diagnosticEventsXdr] field from a list of base64-encoded strings
         * to a list of DiagnosticEvent XDR objects.
         *
         * This method is deprecated and will be removed in a future version.
         * Use [events].[Events.parseDiagnosticEventsXdr] instead.
         *
         * @return list of parsed DiagnosticEvent objects, or null if diagnosticEventsXdr is null
         * @throws IllegalArgumentException if any XDR string is malformed or cannot be decoded
         */
        @Deprecated("Use events.parseDiagnosticEventsXdr() instead. This method will be removed in a future version.")
        fun parseDiagnosticEventsXdr(): List<DiagnosticEventXdr>? {
            return diagnosticEventsXdr?.map { DiagnosticEventXdr.fromXdrBase64(it) }
        }
    }
}

/**
 * Status of a transaction in the getTransactions response.
 *
 * Note: Unlike [GetTransactionStatus], this enum does not include NOT_FOUND
 * because getTransactions only returns transactions that exist.
 *
 * @property SUCCESS Transaction was successfully included in a ledger
 * @property FAILED Transaction was included in a ledger but failed
 */
@Serializable
enum class TransactionStatus {
    @SerialName("SUCCESS")
    SUCCESS,

    @SerialName("FAILED")
    FAILED
}
