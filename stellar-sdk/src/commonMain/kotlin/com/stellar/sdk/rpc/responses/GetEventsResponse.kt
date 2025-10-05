package com.stellar.sdk.rpc.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method getEvents.
 *
 * Returns a list of events matching the specified filter criteria.
 * Events can be contract events, system events, or diagnostic events.
 *
 * @property events List of event information objects
 * @property cursor Cursor value to be used for subsequent requests for more events
 * @property latestLedger The latest ledger known to Soroban RPC at the time it handled the request
 * @property oldestLedger The oldest ledger retained by Soroban RPC (may be null)
 * @property latestLedgerCloseTime Unix timestamp of when the latest ledger was closed (may be null)
 * @property oldestLedgerCloseTime Unix timestamp of when the oldest ledger was closed (may be null)
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getEvents">getEvents documentation</a>
 */
@Serializable
data class GetEventsResponse(
    val events: List<EventInfo>,
    val cursor: String? = null,
    val latestLedger: Long,
    val oldestLedger: Long? = null,
    val latestLedgerCloseTime: Long? = null,
    val oldestLedgerCloseTime: Long? = null
) {
    /**
     * Information about a single event in the response.
     *
     * @property type The type of event (CONTRACT, SYSTEM, or DIAGNOSTIC)
     * @property ledger The ledger sequence number in which the event was emitted
     * @property ledgerClosedAt ISO 8601 timestamp of when the ledger was closed
     * @property contractId The contract ID that emitted the event (for contract events)
     * @property id Unique identifier for the event
     * @property operationIndex Index of the operation that emitted the event within the transaction
     * @property transactionIndex Index of the transaction within the ledger
     * @property transactionHash Hash of the transaction that emitted the event (hex-encoded)
     * @property topic List of base64-encoded SCVal XDR objects representing the event topics
     * @property value Base64-encoded SCVal XDR object representing the event value
     * @property inSuccessfulContractCall Deprecated - Whether the event was emitted in a successful contract call
     */
    @Serializable
    data class EventInfo(
        val type: EventFilterType,
        val ledger: Long,
        val ledgerClosedAt: String,
        val contractId: String,
        val id: String,
        val operationIndex: Long,
        val transactionIndex: Long,
        @SerialName("txHash")
        val transactionHash: String,
        val topic: List<String>,
        val value: String,
        @Deprecated("This field will be removed in a future version")
        val inSuccessfulContractCall: Boolean? = null
    ) {
        /**
         * Parses the [topic] field from a list of base64-encoded strings to a list of SCVal XDR objects.
         *
         * Note: This is a placeholder for XDR parsing functionality.
         * The actual implementation will be added when XDR parsing utilities are available.
         *
         * @return list of parsed SCVal objects
         * @throws IllegalStateException if XDR parsing is not yet implemented
         */
        fun parseTopic(): List<Any> {
            // TODO: Implement XDR parsing when XDR utilities are available
            // return topic.map { SCVal.fromXdrBase64(it) }
            throw IllegalStateException("XDR parsing not yet implemented")
        }

        /**
         * Parses the [value] field from a base64-encoded string to an SCVal XDR object.
         *
         * Note: This is a placeholder for XDR parsing functionality.
         * The actual implementation will be added when XDR parsing utilities are available.
         *
         * @return the parsed SCVal object
         * @throws IllegalStateException if XDR parsing is not yet implemented
         */
        fun parseValue(): Any {
            // TODO: Implement XDR parsing when XDR utilities are available
            // return SCVal.fromXdrBase64(value)
            throw IllegalStateException("XDR parsing not yet implemented")
        }
    }
}

/**
 * Type of event filter for getEvents requests.
 *
 * @property CONTRACT Events emitted by smart contracts
 * @property SYSTEM System events emitted by the Stellar network
 * @property DIAGNOSTIC Diagnostic events for debugging (not part of transaction results)
 */
@Serializable
enum class EventFilterType {
    @SerialName("contract")
    CONTRACT,

    @SerialName("system")
    SYSTEM,

    @SerialName("diagnostic")
    DIAGNOSTIC
}
