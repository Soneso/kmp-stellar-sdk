package com.soneso.stellar.sdk.rpc.responses

import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.LedgerKeyXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response for JSON-RPC method getLedgerEntries.
 *
 * Returns the current state of ledger entries specified by their keys. This is the primary
 * method for querying contract data, account balances, trustlines, and other ledger state.
 * Each entry is returned with its current value and metadata about when it was last modified
 * and when it will expire (for temporary/persistent contract storage).
 *
 * @property entries List of ledger entries matching the requested keys. May be null or empty if
 *                   no entries were found. The order matches the order of keys in the request.
 * @property latestLedger The sequence number of the latest ledger known to the server at the time
 *                        of the request. This indicates the ledger state at which these entries were read.
 *
 * @see [Stellar Soroban RPC getLedgerEntries documentation](https://developers.stellar.org/docs/data/rpc/api-reference/methods/getLedgerEntries)
 */
@Serializable
data class GetLedgerEntriesResponse(
    val entries: List<LedgerEntryResult>? = null,
    val latestLedger: Long
) {
    /**
     * Represents a single ledger entry with its current value and metadata.
     *
     * Ledger entries are stored in XDR format and include various types: accounts, trustlines,
     * offers, contract data, contract code, and more. The XDR-encoded values can be parsed
     * into typed objects for further processing.
     *
     * @property key Base64-encoded XDR of the LedgerKey. This uniquely identifies the type and
     *               location of this entry in the ledger. Can be parsed using [parseKey].
     * @property xdr Base64-encoded XDR of the LedgerEntry.LedgerEntryData. This contains the
     *               actual data stored in this entry. Can be parsed using [parseXdr].
     * @property lastModifiedLedger The ledger sequence number when this entry was last modified.
     *                              This is useful for tracking changes and implementing caching strategies.
     * @property liveUntilLedger The ledger sequence number until which this entry will exist.
     *                           Only applicable to temporary and persistent Soroban contract storage entries.
     *                           Null for permanent entries like accounts and trustlines.
     *                           When the network reaches this ledger number, the entry will be automatically
     *                           deleted unless its lifetime is extended via a BumpFootprintExpirationOp.
     */
    @Serializable
    data class LedgerEntryResult(
        val key: String,
        val xdr: String,
        @SerialName("lastModifiedLedgerSeq")
        val lastModifiedLedger: Long,
        @SerialName("liveUntilLedgerSeq")
        val liveUntilLedger: Long? = null
    ) {
        /**
         * Parses the [key] field from base64-encoded XDR string to a typed LedgerKey object.
         *
         * The LedgerKey identifies the type and location of this entry:
         * - ACCOUNT: Account entry key
         * - TRUSTLINE: Trustline entry key
         * - OFFER: Offer entry key
         * - DATA: Account data entry key
         * - CLAIMABLE_BALANCE: Claimable balance entry key
         * - LIQUIDITY_POOL: Liquidity pool entry key
         * - CONTRACT_DATA: Soroban contract data entry key
         * - CONTRACT_CODE: Soroban contract code entry key
         * - CONFIG_SETTING: Network configuration setting key
         * - TTL: Time-to-live entry key
         *
         * @return The parsed LedgerKey XDR object.
         * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded.
         */
        fun parseKey(): LedgerKeyXdr = LedgerKeyXdr.fromXdrBase64(key)

        /**
         * Parses the [xdr] field from base64-encoded XDR string to a typed LedgerEntry.LedgerEntryData object.
         *
         * The LedgerEntryData contains the actual stored data for this entry, with different structures
         * for each entry type (account data, contract data, etc.).
         *
         * @return The parsed LedgerEntry.LedgerEntryData XDR object.
         * @throws IllegalArgumentException if the XDR string is malformed or cannot be decoded.
         */
        fun parseXdr(): LedgerEntryDataXdr = LedgerEntryDataXdr.fromXdrBase64(xdr)
    }
}
