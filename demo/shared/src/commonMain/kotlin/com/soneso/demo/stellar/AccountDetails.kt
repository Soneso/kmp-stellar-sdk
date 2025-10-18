package com.soneso.demo.stellar

import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.responses.AccountResponse

/**
 * Result type for account details fetching operations.
 */
sealed class AccountDetailsResult {
    /**
     * Successful account fetch with full details.
     *
     * @property accountResponse The complete account response from Horizon
     */
    data class Success(
        val accountResponse: AccountResponse
    ) : AccountDetailsResult()

    /**
     * Failed account fetch with error details.
     *
     * @property message Human-readable error message
     * @property exception The underlying exception, if any
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : AccountDetailsResult()
}

/**
 * Fetches detailed account information from the Stellar network using Horizon API.
 *
 * This function retrieves comprehensive account data including:
 * - Account ID and sequence number
 * - All balances (native XLM and trustlines)
 * - Signers and their weights
 * - Thresholds (low, medium, high)
 * - Flags (authorization settings)
 * - Data entries (key-value pairs)
 * - Sponsorship information
 * - Home domain
 * - Last modification details
 *
 * ## Usage
 *
 * ```kotlin
 * val result = fetchAccountDetails("GABC...")
 * when (result) {
 *     is AccountDetailsResult.Success -> {
 *         val account = result.accountResponse
 *         println("Balance: ${account.balances[0].balance} XLM")
 *         println("Sequence: ${account.sequenceNumber}")
 *     }
 *     is AccountDetailsResult.Error -> {
 *         println("Failed to fetch account: ${result.message}")
 *     }
 * }
 * ```
 *
 * @param accountId The Stellar account ID to fetch (must start with 'G')
 * @param useTestnet If true, connects to testnet; otherwise connects to public network (default: true)
 * @return AccountDetailsResult.Success with full account data if fetch succeeded, AccountDetailsResult.Error if it failed
 *
 * @see <a href="https://developers.stellar.org/docs/fundamentals-and-concepts/stellar-data-structures/accounts">Stellar Account Documentation</a>
 * @see <a href="https://developers.stellar.org/api/resources/accounts/object">Horizon Account Response</a>
 */
suspend fun fetchAccountDetails(
    accountId: String,
    useTestnet: Boolean = true
): AccountDetailsResult {
    return try {
        // Validate account ID format
        if (accountId.isBlank()) {
            return AccountDetailsResult.Error(
                message = "Account ID cannot be empty"
            )
        }

        if (!accountId.startsWith('G')) {
            return AccountDetailsResult.Error(
                message = "Account ID must start with 'G' (got: ${accountId.take(1)})"
            )
        }

        if (accountId.length != 56) {
            return AccountDetailsResult.Error(
                message = "Account ID must be exactly 56 characters long (got: ${accountId.length})"
            )
        }

        // Connect to Horizon server
        val horizonUrl = if (useTestnet) {
            "https://horizon-testnet.stellar.org"
        } else {
            "https://horizon.stellar.org"
        }

        val server = HorizonServer(horizonUrl)

        try {
            // Fetch account details from Horizon
            val accountResponse = server.accounts().account(accountId)

            AccountDetailsResult.Success(
                accountResponse = accountResponse
            )
        } finally {
            // Clean up HTTP client resources
            server.close()
        }
    } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
        // Account not found or invalid request
        if (e.code == 404) {
            AccountDetailsResult.Error(
                message = "Account not found. The account may not exist or hasn't been funded yet.",
                exception = e
            )
        } else {
            AccountDetailsResult.Error(
                message = "Invalid request: ${e.message}",
                exception = e
            )
        }
    } catch (e: com.soneso.stellar.sdk.horizon.exceptions.NetworkException) {
        // Network-related errors (timeout, too many requests, server errors)
        AccountDetailsResult.Error(
            message = "Network error: ${e.message ?: "Failed to connect to Horizon"}",
            exception = e
        )
    } catch (e: Exception) {
        // Unexpected errors
        AccountDetailsResult.Error(
            message = "Unexpected error: ${e.message ?: "Unknown error occurred"}",
            exception = e
        )
    }
}
