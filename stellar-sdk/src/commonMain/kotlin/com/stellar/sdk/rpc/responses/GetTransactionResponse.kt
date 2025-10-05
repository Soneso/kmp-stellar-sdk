package com.stellar.sdk.rpc.responses

import com.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.stellar.sdk.xdr.TransactionMetaXdr
import com.stellar.sdk.xdr.TransactionResultXdr
import com.stellar.sdk.xdr.fromXdrBase64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method getTransaction.
 *
 * Contains information about a specific transaction, including its status, XDR data,
 * and ledger information.
 *
 * @property status The current status of the transaction
 * @property txHash The transaction hash (hex-encoded)
 * @property latestLedger The latest ledger known to Soroban RPC at the time it handled the request
 * @property latestLedgerCloseTime Unix timestamp of when the latest ledger was closed
 * @property oldestLedger The oldest ledger retained by Soroban RPC
 * @property oldestLedgerCloseTime Unix timestamp of when the oldest ledger was closed
 * @property applicationOrder The index of the transaction among all transactions included in the ledger (null if not found)
 * @property feeBump Whether this transaction is a fee bump transaction (null if not found)
 * @property envelopeXdr Base64-encoded TransactionEnvelope XDR (null if not found)
 * @property resultXdr Base64-encoded TransactionResult XDR (null if not found)
 * @property resultMetaXdr Base64-encoded TransactionMeta XDR (null if not found)
 * @property ledger The ledger sequence number that included this transaction (null if not found)
 * @property createdAt Unix timestamp of when the transaction was included in the ledger (null if not found)
 * @property events Transaction events data (null if not found or no events)
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getTransaction">getTransaction documentation</a>
 */
@Serializable
data class GetTransactionResponse(
    val status: GetTransactionStatus,
    val txHash: String? = null,
    val latestLedger: Long? = null,
    val latestLedgerCloseTime: Long? = null,
    val oldestLedger: Long? = null,
    val oldestLedgerCloseTime: Long? = null,
    val applicationOrder: Int? = null,
    val feeBump: Boolean? = null,
    val envelopeXdr: String? = null,
    val resultXdr: String? = null,
    val resultMetaXdr: String? = null,
    val ledger: Long? = null,
    val createdAt: Long? = null,
    val events: Events? = null
) {
    /**
     * Parses the [envelopeXdr] field from a base64-encoded string to a TransactionEnvelope XDR object.
     *
     * @return the parsed TransactionEnvelope object, or null if envelopeXdr is null
     * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
     */
    fun parseEnvelopeXdr(): TransactionEnvelopeXdr? {
        return envelopeXdr?.let { TransactionEnvelopeXdr.fromXdrBase64(it) }
    }

    /**
     * Parses the [resultXdr] field from a base64-encoded string to a TransactionResult XDR object.
     *
     * @return the parsed TransactionResult object, or null if resultXdr is null
     * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
     */
    fun parseResultXdr(): TransactionResultXdr? {
        return resultXdr?.let { TransactionResultXdr.fromXdrBase64(it) }
    }

    /**
     * Parses the [resultMetaXdr] field from a base64-encoded string to a TransactionMeta XDR object.
     *
     * @return the parsed TransactionMeta object, or null if resultMetaXdr is null
     * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
     */
    fun parseResultMetaXdr(): TransactionMetaXdr? {
        return resultMetaXdr?.let { TransactionMetaXdr.fromXdrBase64(it) }
    }
}

/**
 * Status of a transaction in the Soroban RPC response.
 *
 * @property NOT_FOUND Transaction was not found in the ledger range
 * @property SUCCESS Transaction was successfully included in a ledger
 * @property FAILED Transaction was included in a ledger but failed
 */
@Serializable
enum class GetTransactionStatus {
    @SerialName("NOT_FOUND")
    NOT_FOUND,

    @SerialName("SUCCESS")
    SUCCESS,

    @SerialName("FAILED")
    FAILED
}
