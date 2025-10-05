package com.stellar.sdk.rpc.responses

import com.stellar.sdk.xdr.DiagnosticEventXdr
import com.stellar.sdk.xdr.LedgerEntryXdr
import com.stellar.sdk.xdr.LedgerKeyXdr
import com.stellar.sdk.xdr.SCValXdr
import com.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.stellar.sdk.xdr.SorobanTransactionDataXdr
import com.stellar.sdk.xdr.fromXdrBase64
import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method simulateTransaction.
 *
 * The simulation response will have different model representations with different members
 * present or absent depending on the type of response that it is conveying. For example,
 * the simulation response for invoke host function could be one of three types:
 * - Error: Contains an error message
 * - Success: Contains results, transaction data, and resource fees
 * - Restore operation needed: Contains restore preamble for state archival restoration
 *
 * @property error Error message if the simulation failed
 * @property transactionData Base64-encoded SorobanTransactionData XDR to be set in the transaction
 * @property events List of base64-encoded DiagnosticEvent XDR objects emitted during simulation
 * @property minResourceFee Minimum resource fee required for the transaction (in stroops)
 * @property results List of host function invocation results (typically contains a single element)
 * @property restorePreamble Information about required state restoration (if needed)
 * @property stateChanges List of ledger entry changes that would occur if the transaction is applied
 * @property latestLedger The latest ledger used for simulation
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/simulateTransaction">simulateTransaction documentation</a>
 */
@Serializable
data class SimulateTransactionResponse(
    val error: String? = null,
    val transactionData: String? = null,
    val events: List<String>? = null,
    val minResourceFee: Long? = null,
    val results: List<SimulateHostFunctionResult>? = null,
    val restorePreamble: RestorePreamble? = null,
    val stateChanges: List<LedgerEntryChange>? = null,
    val latestLedger: Long? = null
) {
    /**
     * Parses the [transactionData] field from a base64-encoded string to a SorobanTransactionData XDR object.
     *
     * @return the parsed SorobanTransactionData object, or null if transactionData is null
     * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
     */
    fun parseTransactionData(): SorobanTransactionDataXdr? {
        return transactionData?.let { SorobanTransactionDataXdr.fromXdrBase64(it) }
    }

    /**
     * Parses the [events] field from a list of base64-encoded strings to a list of DiagnosticEvent XDR objects.
     *
     * @return list of parsed DiagnosticEvent objects, or null if events is null
     * @throws IllegalArgumentException if any XDR string is malformed or cannot be decoded
     */
    fun parseEvents(): List<DiagnosticEventXdr>? {
        return events?.map { DiagnosticEventXdr.fromXdrBase64(it) }
    }

    /**
     * Result of simulating a host function invocation.
     *
     * @property auth List of base64-encoded SorobanAuthorizationEntry XDR objects required for authorization
     * @property xdr Base64-encoded SCVal XDR object representing the return value
     */
    @Serializable
    data class SimulateHostFunctionResult(
        val auth: List<String>? = null,
        val xdr: String? = null
    ) {
        /**
         * Parses the [auth] field from a list of base64-encoded strings to a list of
         * SorobanAuthorizationEntry XDR objects.
         *
         * @return list of parsed SorobanAuthorizationEntry objects, or null if auth is null
         * @throws IllegalArgumentException if any XDR string is malformed or cannot be decoded
         */
        fun parseAuth(): List<SorobanAuthorizationEntryXdr>? {
            return auth?.map { SorobanAuthorizationEntryXdr.fromXdrBase64(it) }
        }

        /**
         * Parses the [xdr] field from a base64-encoded string to an SCVal XDR object.
         *
         * @return the parsed SCVal object, or null if xdr is null
         * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
         */
        fun parseXdr(): SCValXdr? {
            return xdr?.let { SCValXdr.fromXdrBase64(it) }
        }
    }

    /**
     * Information about required state restoration before the transaction can be submitted.
     *
     * When archived ledger entries need to be restored before a transaction can succeed,
     * this object contains the data needed to build a RestoreFootprint operation.
     *
     * @property transactionData Base64-encoded SorobanTransactionData XDR for the restore operation
     * @property minResourceFee Minimum resource fee required for the restore operation (in stroops)
     */
    @Serializable
    data class RestorePreamble(
        val transactionData: String,
        val minResourceFee: Long
    ) {
        /**
         * Parses the [transactionData] field from a base64-encoded string to a SorobanTransactionData XDR object.
         *
         * @return the parsed SorobanTransactionData object
         * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
         */
        fun parseTransactionData(): SorobanTransactionDataXdr {
            return SorobanTransactionDataXdr.fromXdrBase64(transactionData)
        }
    }

    /**
     * Represents a change in a ledger entry during simulation.
     *
     * Before and After cannot be omitted at the same time:
     * - If Before is omitted, it constitutes a creation
     * - If After is omitted, it constitutes a deletion
     * - If both are present, it constitutes an update
     *
     * @property type Type of state change (typically "created", "updated", or "deleted")
     * @property key Base64-encoded LedgerKey XDR identifying the entry
     * @property before Base64-encoded LedgerEntry XDR of the state before the change (null for creation)
     * @property after Base64-encoded LedgerEntry XDR of the state after the change (null for deletion)
     */
    @Serializable
    data class LedgerEntryChange(
        val type: String,
        val key: String,
        val before: String? = null,
        val after: String? = null
    ) {
        /**
         * Parses the [key] field from a base64-encoded string to a LedgerKey XDR object.
         *
         * @return the parsed LedgerKey object
         * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
         */
        fun parseKey(): LedgerKeyXdr {
            return LedgerKeyXdr.fromXdrBase64(key)
        }

        /**
         * Parses the [before] field from a base64-encoded string to a LedgerEntry XDR object.
         *
         * @return the parsed LedgerEntry object, or null if before is null
         * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
         */
        fun parseBefore(): LedgerEntryXdr? {
            return before?.let { LedgerEntryXdr.fromXdrBase64(it) }
        }

        /**
         * Parses the [after] field from a base64-encoded string to a LedgerEntry XDR object.
         *
         * @return the parsed LedgerEntry object, or null if after is null
         * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded
         */
        fun parseAfter(): LedgerEntryXdr? {
            return after?.let { LedgerEntryXdr.fromXdrBase64(it) }
        }
    }
}
