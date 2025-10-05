package com.stellar.sdk.rpc.responses

import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method getLedgers.
 *
 * Returns a detailed list of ledgers starting from the specified ledger sequence number.
 * This method allows for paginated retrieval of ledger data.
 *
 * @property ledgers List of ledger information objects
 * @property latestLedger The latest ledger known to Soroban RPC at the time it handled the request
 * @property latestLedgerCloseTime Unix timestamp of when the latest ledger was closed
 * @property oldestLedger The oldest ledger retained by Soroban RPC
 * @property oldestLedgerCloseTime Unix timestamp of when the oldest ledger was closed
 * @property cursor Cursor value to be used for subsequent requests for more ledgers
 *
 * @see <a href="https://developers.stellar.org/docs/data/rpc/api-reference/methods/getLedgers">getLedgers documentation</a>
 */
@Serializable
data class GetLedgersResponse(
    val ledgers: List<LedgerInfo>,
    val latestLedger: Long,
    val latestLedgerCloseTime: Long,
    val oldestLedger: Long,
    val oldestLedgerCloseTime: Long,
    val cursor: String
) {
    /**
     * Information about a single ledger in the response.
     *
     * @property hash Hex-encoded hash of the ledger header
     * @property sequence Ledger sequence number
     * @property ledgerCloseTime Unix timestamp of when the ledger was closed
     * @property headerXdr Base64-encoded LedgerHeaderHistoryEntry XDR
     * @property metadataXdr Base64-encoded LedgerCloseMeta XDR
     */
    @Serializable
    data class LedgerInfo(
        val hash: String,
        val sequence: Long,
        val ledgerCloseTime: Long,
        val headerXdr: String,
        val metadataXdr: String
    ) {
        /**
         * Parses the [headerXdr] field from a base64-encoded string to a LedgerHeaderHistoryEntry XDR object.
         *
         * Note: This is a placeholder for XDR parsing functionality.
         * The actual implementation will be added when XDR parsing utilities are available.
         *
         * @return the parsed LedgerHeaderHistoryEntry object
         * @throws IllegalStateException if XDR parsing is not yet implemented
         */
        fun parseHeaderXdr(): Any {
            // TODO: Implement XDR parsing when XDR utilities are available
            // return LedgerHeaderHistoryEntry.fromXdrBase64(headerXdr)
            throw IllegalStateException("XDR parsing not yet implemented")
        }

        /**
         * Parses the [metadataXdr] field from a base64-encoded string to a LedgerCloseMeta XDR object.
         *
         * Note: This is a placeholder for XDR parsing functionality.
         * The actual implementation will be added when XDR parsing utilities are available.
         *
         * @return the parsed LedgerCloseMeta object
         * @throws IllegalStateException if XDR parsing is not yet implemented
         */
        fun parseMetadataXdr(): Any {
            // TODO: Implement XDR parsing when XDR utilities are available
            // return LedgerCloseMeta.fromXdrBase64(metadataXdr)
            throw IllegalStateException("XDR parsing not yet implemented")
        }
    }
}
