package com.soneso.demo.stellar

import com.soneso.stellar.sdk.FriendBot

/**
 * Result type for account funding operations.
 */
sealed class AccountFundingResult {
    /**
     * Successful account funding.
     *
     * @property accountId The funded account ID
     * @property message Success message
     */
    data class Success(
        val accountId: String,
        val message: String = "Account funded successfully with 10,000 XLM"
    ) : AccountFundingResult()

    /**
     * Failed account funding with error details.
     *
     * @property message Human-readable error message
     * @property exception The underlying exception, if any
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : AccountFundingResult()
}

/**
 * Funds a Stellar testnet account using the SDK's built-in FriendBot service.
 *
 * FriendBot creates and funds test accounts with 10,000 XLM on Stellar's testnet.
 * This is essential for development and testing without requiring real funds.
 *
 * ## Usage
 *
 * ```kotlin
 * val result = fundTestnetAccount("GABC...")
 * when (result) {
 *     is AccountFundingResult.Success -> {
 *         println("Account funded: ${result.accountId}")
 *         println(result.message)
 *     }
 *     is AccountFundingResult.Error -> {
 *         println("Funding failed: ${result.message}")
 *     }
 * }
 * ```
 *
 * @param accountId The Stellar account ID to fund (must start with 'G')
 * @return AccountFundingResult.Success if funding succeeded, AccountFundingResult.Error if it failed
 *
 * @see <a href="https://developers.stellar.org/docs/fundamentals-and-concepts/testnet-and-pubnet#friendbot">FriendBot Documentation</a>
 */
suspend fun fundTestnetAccount(accountId: String): AccountFundingResult {
    return try {
        // Use SDK's FriendBot to fund the account
        val success = FriendBot.fundTestnetAccount(accountId)

        if (success) {
            AccountFundingResult.Success(
                accountId = accountId,
                message = "Account funded successfully with 10,000 XLM"
            )
        } else {
            AccountFundingResult.Error(
                message = "FriendBot returned unsuccessful status for account $accountId"
            )
        }
    } catch (e: Exception) {
        AccountFundingResult.Error(
            message = "Failed to fund account: ${e.message}",
            exception = e
        )
    }
}
