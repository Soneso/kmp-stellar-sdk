package com.soneso.stellar.sdk.rpc.responses

import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.TransactionMetaXdr
import com.soneso.stellar.sdk.xdr.TransactionResultXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
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

    /**
     * Extracts the WASM hash (ID) from an upload contract WASM transaction.
     *
     * This helper function parses the transaction metadata and extracts the WASM hash
     * from the return value of a successful WASM upload operation.
     *
     * @return The WASM hash as a hex string (lowercase), or null if:
     *         - The transaction is not a successful upload operation
     *         - The resultMetaXdr is null
     *         - The return value is not a bytes value
     * @throws IllegalArgumentException if the XDR cannot be parsed
     */
    fun getWasmId(): String? {
        val meta = parseResultMetaXdr() ?: return null

        // Check if this is a V3 transaction meta (Soroban)
        val v3Meta = when (meta) {
            is TransactionMetaXdr.V3 -> meta.value
            else -> return null
        }

        // Extract the return value from Soroban metadata
        val sorobanMeta = v3Meta.sorobanMeta ?: return null
        val returnValue = sorobanMeta.returnValue

        // The WASM hash is returned as bytes
        val scBytes = when (returnValue) {
            is com.soneso.stellar.sdk.xdr.SCValXdr.Bytes -> returnValue.value
            else -> return null
        }

        // Convert to hex string (lowercase to match Java SDK)
        return scBytes.value.joinToString("") { "%02x".format(it) }
    }

    /**
     * Extracts the created contract ID from a deploy contract transaction.
     *
     * This helper function parses the transaction metadata and extracts the contract ID
     * from the return value of a successful contract deployment operation.
     *
     * @return The contract ID as a strkey-encoded string (starting with "C"), or null if:
     *         - The transaction is not a successful deployment operation
     *         - The resultMetaXdr is null
     *         - The return value is not a contract address
     * @throws IllegalArgumentException if the XDR cannot be parsed or contract address is invalid
     */
    fun getCreatedContractId(): String? {
        val meta = parseResultMetaXdr() ?: return null

        // Check if this is a V3 transaction meta (Soroban)
        val v3Meta = when (meta) {
            is TransactionMetaXdr.V3 -> meta.value
            else -> return null
        }

        // Extract the return value from Soroban metadata
        val sorobanMeta = v3Meta.sorobanMeta ?: return null
        val returnValue = sorobanMeta.returnValue

        // The contract ID is returned as an Address (SCAddress)
        val address = when (returnValue) {
            is com.soneso.stellar.sdk.xdr.SCValXdr.Address -> returnValue.value
            else -> return null
        }

        // Extract the contract hash from the address
        val contractId = when (address) {
            is com.soneso.stellar.sdk.xdr.SCAddressXdr.ContractId -> address.value
            else -> return null
        }

        // Encode to strkey format (C...)
        return com.soneso.stellar.sdk.StrKey.encodeContract(contractId.value.value)
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
