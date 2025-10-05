package com.stellar.sdk.rpc.exception

/**
 * Exception thrown when an account is not found on the network.
 *
 * This typically occurs when attempting to fetch account information for an account
 * that hasn't been funded yet or doesn't exist on the Stellar network.
 *
 * @property accountId The account ID that was not found
 */
class AccountNotFoundException(
    val accountId: String
) : Exception("Account not found: $accountId")
