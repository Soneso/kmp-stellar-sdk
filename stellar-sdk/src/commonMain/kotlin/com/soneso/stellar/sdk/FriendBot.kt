package com.soneso.stellar.sdk

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Helper object for funding test accounts using Stellar's FriendBot service.
 *
 * FriendBot is a testnet and futurenet service that creates and funds test accounts
 * with 10,000 XLM. This is useful for development and testing without requiring real funds.
 *
 * **Important**: FriendBot is only available on testnet and futurenet. It will not work
 * on the public Stellar network (mainnet).
 *
 * ## Usage
 *
 * ```kotlin
 * import com.soneso.stellar.sdk.FriendBot
 * import com.soneso.stellar.sdk.KeyPair
 *
 * suspend fun example() {
 *     // Generate a new keypair
 *     val keypair = KeyPair.random()
 *     val accountId = keypair.getAccountId()
 *
 *     // Fund the account on testnet
 *     FriendBot.fundTestnetAccount(accountId)
 *
 *     // Account is now ready to use with 10,000 XLM balance
 * }
 * ```
 *
 * ## Futurenet
 *
 * To use FriendBot with futurenet instead of testnet:
 *
 * ```kotlin
 * FriendBot.fundFuturenetAccount(accountId)
 * ```
 *
 * ## Error Handling
 *
 * ```kotlin
 * try {
 *     FriendBot.fundTestnetAccount(accountId)
 *     println("Account funded successfully")
 * } catch (e: Exception) {
 *     println("Failed to fund account: ${e.message}")
 * }
 * ```
 *
 * ## Resource Management
 *
 * FriendBot maintains a persistent HTTP client for performance. If you need to release
 * resources (typically not needed):
 *
 * ```kotlin
 * FriendBot.close()
 * ```
 *
 * @see <a href="https://developers.stellar.org/docs/fundamentals-and-concepts/testnet-and-pubnet#friendbot">FriendBot Documentation</a>
 */
object FriendBot {
    private const val TESTNET_FRIENDBOT_URL = "https://friendbot.stellar.org"
    private const val FUTURENET_FRIENDBOT_URL = "https://friendbot-futurenet.stellar.org"

    private val httpClient = createHttpClient()

    /**
     * Creates an HTTP client with the same configuration as HorizonServer.
     *
     * Uses ContentNegotiation, timeouts, and retry logic matching the main SDK's
     * HTTP client configuration. This ensures consistent behavior and SSL/TLS handling.
     */
    private fun createHttpClient(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
                encodeDefaults = true
            })
        }
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
     * The operation is idempotent - calling it multiple times on the same account will not fail,
     * but the account will only receive the initial 10,000 XLM funding once.
     *
     * **Network**: This only works on Stellar's testnet. For futurenet, use [fundFuturenetAccount].
     *
     * @param accountId The account ID (G... address) to fund
     * @return true if funding was successful, false otherwise
     * @throws Exception if the funding request fails with network errors
     *
     * ## Example
     *
     * ```kotlin
     * val keypair = KeyPair.random()
     * val success = FriendBot.fundTestnetAccount(keypair.getAccountId())
     * if (success) {
     *     println("Account funded successfully!")
     * }
     * ```
     */
    suspend fun fundTestnetAccount(accountId: String): Boolean {
        return fundAccount(TESTNET_FRIENDBOT_URL, accountId)
    }

    /**
     * Funds a futurenet account using FriendBot.
     *
     * This will create the account if it doesn't exist and fund it with 10,000 XLM (futurenet lumens).
     * The operation is idempotent - calling it multiple times on the same account will not fail,
     * but the account will only receive the initial 10,000 XLM funding once.
     *
     * **Network**: This only works on Stellar's futurenet. For testnet, use [fundTestnetAccount].
     *
     * @param accountId The account ID (G... address) to fund
     * @return true if funding was successful, false otherwise
     * @throws Exception if the funding request fails with network errors
     *
     * ## Example
     *
     * ```kotlin
     * val keypair = KeyPair.random()
     * val success = FriendBot.fundFuturenetAccount(keypair.getAccountId())
     * if (success) {
     *     println("Account funded successfully!")
     * }
     * ```
     */
    suspend fun fundFuturenetAccount(accountId: String): Boolean {
        return fundAccount(FUTURENET_FRIENDBOT_URL, accountId)
    }

    /**
     * Internal method to fund an account using a specific FriendBot endpoint.
     *
     * @param friendbotUrl The FriendBot base URL
     * @param accountId The account ID to fund
     * @return true if funding was successful, false otherwise
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
     *
     * **Note**: Typically not needed in application code. FriendBot maintains a persistent
     * HTTP client for better performance. Only call this if you need to explicitly release
     * resources (e.g., when shutting down your application).
     *
     * After calling [close], FriendBot cannot be used again until the application restarts.
     */
    fun close() {
        httpClient.close()
    }
}
