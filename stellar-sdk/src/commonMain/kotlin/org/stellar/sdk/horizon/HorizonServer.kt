package org.stellar.sdk.horizon

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.stellar.sdk.horizon.requests.AccountsRequestBuilder

/**
 * Main class used to connect to Horizon server.
 *
 * This class provides factory methods for creating request builders to interact with
 * various Horizon API endpoints.
 *
 * @property serverUri The URI of the Horizon server
 * @property httpClient The HTTP client used for general requests (30s timeout)
 * @property submitHttpClient The HTTP client used for submitting transactions (65s timeout)
 */
class HorizonServer(
    serverUri: String,
    httpClient: HttpClient? = null,
    submitHttpClient: HttpClient? = null
) {
    companion object {
        /**
         * HORIZON_SUBMIT_TIMEOUT is a time in seconds after Horizon sends a timeout response
         * after internal txsub timeout.
         */
        const val HORIZON_SUBMIT_TIMEOUT = 60

        /**
         * Default JSON configuration for serialization/deserialization
         */
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }

        /**
         * Creates a default HTTP client with standard timeout settings.
         */
        fun createDefaultHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(defaultJson)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()
            }
        }

        /**
         * Creates an HTTP client for submitting transactions with extended timeout.
         */
        fun createSubmitHttpClient(): HttpClient = HttpClient {
            install(ContentNegotiation) {
                json(defaultJson)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = (HORIZON_SUBMIT_TIMEOUT + 5) * 1_000L
                socketTimeoutMillis = (HORIZON_SUBMIT_TIMEOUT + 5) * 1_000L
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()
            }
        }
    }

    val serverUri: Url = Url(serverUri)
    val httpClient: HttpClient = httpClient ?: createDefaultHttpClient()
    val submitHttpClient: HttpClient = submitHttpClient ?: createSubmitHttpClient()

    /**
     * Returns an [AccountsRequestBuilder] instance for querying accounts.
     *
     * @return [AccountsRequestBuilder] instance
     */
    fun accounts(): AccountsRequestBuilder {
        return AccountsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns a [TransactionsRequestBuilder] instance for querying transactions.
     *
     * @return [TransactionsRequestBuilder] instance
     */
    fun transactions(): org.stellar.sdk.horizon.requests.TransactionsRequestBuilder {
        return org.stellar.sdk.horizon.requests.TransactionsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns an [OperationsRequestBuilder] instance for querying operations.
     *
     * @return [OperationsRequestBuilder] instance
     */
    fun operations(): org.stellar.sdk.horizon.requests.OperationsRequestBuilder {
        return org.stellar.sdk.horizon.requests.OperationsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Closes the HTTP clients and releases resources.
     * Call this when you're done using the HorizonServer instance.
     */
    fun close() {
        httpClient.close()
        submitHttpClient.close()
    }
}
