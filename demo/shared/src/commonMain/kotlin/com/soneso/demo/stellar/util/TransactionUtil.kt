package com.soneso.demo.stellar.util

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.RequestBuilder

/**
 * Utility functions for working with Stellar transactions.
 *
 * This object provides helper functions for common transaction-related operations
 * such as fetching transaction details from Horizon.
 */
object TransactionUtil {
    /**
     * Fetches the most recent transaction hash for a given account from Horizon.
     *
     * This function queries the Horizon API to retrieve the latest transaction
     * associated with the specified account.
     *
     * ## Usage Example
     *
     * ```kotlin
     * val txHash = TransactionUtil.getLatestTransactionHash(
     *     accountId = "GXYZ...",
     *     useTestnet = true
     * )
     * println("Latest transaction: $txHash")
     * ```
     *
     * ## Error Handling
     *
     * This function throws exceptions in the following cases:
     * - No transactions found for the account (IllegalStateException)
     * - Network communication errors (various Horizon exceptions)
     * - Invalid account ID format (propagated from Horizon)
     *
     * @param accountId The account ID to query transactions for (G... format, 56 characters)
     * @param useTestnet Whether to use testnet (true) or mainnet (false)
     * @return The transaction hash of the most recent transaction
     * @throws IllegalStateException if no transactions found for the account
     * @throws com.soneso.stellar.sdk.horizon.exception.HorizonException for Horizon API errors
     *
     * @see HorizonServer.transactions
     * @see <a href="https://developers.stellar.org/api/horizon/resources/transactions">Horizon Transactions API</a>
     */
    suspend fun getLatestTransactionHash(accountId: String, useTestnet: Boolean): String {
        val horizonUrl = if (useTestnet) {
            "https://horizon-testnet.stellar.org"
        } else {
            "https://horizon.stellar.org"
        }

        val horizon = HorizonServer(horizonUrl)

        return try {
            // Query for the most recent transaction from the account
            // Order by descending (most recent first) and limit to 1 result
            val transactionsPage = horizon.transactions()
                .forAccount(accountId)
                .order(RequestBuilder.Order.DESC)
                .limit(1)
                .execute()

            // Extract the first (most recent) transaction
            val transaction = transactionsPage.records.firstOrNull()
                ?: throw IllegalStateException("No transactions found for account $accountId")

            transaction.hash
        } finally {
            // Always close the HTTP client to release resources
            horizon.close()
        }
    }
}
