package com.stellar.sdk.integrationTests

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Helper object for funding test accounts using Stellar's FriendBot service.
 *
 * FriendBot is a testnet-only service that creates and funds test accounts with 10,000 XLM.
 * This is useful for integration testing without requiring real funds.
 *
 * ## Usage
 *
 * ```kotlin
 * // Fund a testnet account
 * val keypair = KeyPair.random()
 * FriendBot.fundTestnetAccount(keypair.getAccountId())
 *
 * // Fund a futurenet account
 * FriendBot.fundFuturenetAccount(keypair.getAccountId())
 * ```
 *
 * @see <a href="https://developers.stellar.org/docs/fundamentals-and-concepts/testnet-and-pubnet#friendbot">FriendBot Documentation</a>
 */
object FriendBot {
    private const val TESTNET_FRIENDBOT_URL = "https://friendbot.stellar.org"
    private const val FUTURENET_FRIENDBOT_URL = "https://friendbot-futurenet.stellar.org"

    private val httpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
    }

    /**
     * Funds a testnet account using FriendBot.
     *
     * This will create the account if it doesn't exist and fund it with 10,000 XLM (testnet lumens).
     *
     * @param accountId The account ID (G... address) to fund
     * @return true if funding was successful
     * @throws Exception if the funding request fails
     */
    suspend fun fundTestnetAccount(accountId: String): Boolean {
        return fundAccount(TESTNET_FRIENDBOT_URL, accountId)
    }

    /**
     * Funds a futurenet account using FriendBot.
     *
     * This will create the account if it doesn't exist and fund it with 10,000 XLM (futurenet lumens).
     *
     * @param accountId The account ID (G... address) to fund
     * @return true if funding was successful
     * @throws Exception if the funding request fails
     */
    suspend fun fundFuturenetAccount(accountId: String): Boolean {
        return fundAccount(FUTURENET_FRIENDBOT_URL, accountId)
    }

    /**
     * Internal method to fund an account using a specific FriendBot endpoint.
     *
     * @param friendbotUrl The FriendBot base URL
     * @param accountId The account ID to fund
     * @return true if funding was successful
     * @throws Exception if the funding request fails
     */
    private suspend fun fundAccount(friendbotUrl: String, accountId: String): Boolean {
        return try {
            val response = httpClient.get("$friendbotUrl/") {
                parameter("addr", accountId)
            }

            response.status.isSuccess()
        } catch (e: Exception) {
            throw Exception("Failed to fund account $accountId via FriendBot: ${e.message}", e)
        }
    }

    /**
     * Closes the HTTP client and releases resources.
     * Call this when you're done using FriendBot (typically not needed in tests).
     */
    fun close() {
        httpClient.close()
    }
}
