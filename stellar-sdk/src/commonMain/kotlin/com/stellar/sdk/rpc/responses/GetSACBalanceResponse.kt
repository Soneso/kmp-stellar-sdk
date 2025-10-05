package com.stellar.sdk.rpc.responses

import kotlinx.serialization.Serializable

/**
 * Response for the getSACBalance operation.
 *
 * Retrieves the balance of a Stellar Asset Contract (SAC) for a given account.
 * Stellar Asset Contracts are smart contracts that wrap classic Stellar assets,
 * allowing them to be used in Soroban smart contract interactions.
 *
 * @property balanceEntry The balance entry for the account, or null if no valid balance exists
 * @property latestLedger The latest ledger known to Soroban RPC at the time it handled the request
 *
 * @see <a href="https://developers.stellar.org/docs/tokens/stellar-asset-contract">Stellar Asset Contract documentation</a>
 */
@Serializable
data class GetSACBalanceResponse(
    val balanceEntry: BalanceEntry? = null,
    val latestLedger: Long
) {
    /**
     * Balance entry information for a Stellar Asset Contract account.
     *
     * Contains the balance amount and authorization state for an account holding
     * a Stellar Asset Contract token.
     *
     * @property amount The balance amount as a string (to preserve precision for large numbers)
     * @property authorized Whether the account is authorized to hold and transact with this asset.
     *                      For non-regulated assets, this is typically true.
     *                      For regulated assets (AUTH_REQUIRED flag), the issuer must authorize accounts.
     * @property clawback Whether the asset allows clawback. If true, the issuer can revoke
     *                    (claw back) the asset from this account.
     * @property lastModifiedLedgerSeq The ledger sequence number when this balance entry was last modified
     * @property liveUntilLedgerSeq The ledger sequence number until which this entry will live.
     *                              For temporary storage, entries must be extended before this ledger
     *                              or they will be archived. Null for persistent storage entries.
     */
    @Serializable
    data class BalanceEntry(
        val amount: String,
        val authorized: Boolean,
        val clawback: Boolean,
        val lastModifiedLedgerSeq: Long,
        val liveUntilLedgerSeq: Long? = null
    ) {
        /**
         * Parses the [amount] field as a Long value.
         *
         * SAC balances are 7-decimal precision (stroops for XLM, or asset-specific decimals).
         *
         * @return the balance as a Long
         * @throws NumberFormatException if the amount cannot be parsed as a Long
         */
        fun getAmountAsLong(): Long {
            return amount.toLong()
        }

        /**
         * Checks if this balance entry is using temporary storage.
         *
         * Temporary storage entries must be periodically extended or they will be archived.
         * Persistent entries do not have a liveUntilLedgerSeq and never expire.
         *
         * @return true if this is a temporary storage entry (has a liveUntilLedgerSeq), false otherwise
         */
        fun isTemporary(): Boolean {
            return liveUntilLedgerSeq != null
        }

        /**
         * Checks if this balance entry will be archived by the specified ledger.
         *
         * Only applicable for temporary storage entries.
         *
         * @param ledgerSeq The ledger sequence to check against
         * @return true if the entry will be archived by the specified ledger, false if it will still be live
         *         or if this is a persistent entry
         */
        fun willBeArchivedBy(ledgerSeq: Long): Boolean {
            return liveUntilLedgerSeq?.let { it <= ledgerSeq } ?: false
        }
    }
}
