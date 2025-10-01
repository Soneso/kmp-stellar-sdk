package com.stellar.sdk.horizon

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.stellar.sdk.horizon.requests.AccountsRequestBuilder

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

    // SEP-29 checker for memo required validation
    private val sep29Checker = Sep29Checker(this.httpClient, this.serverUri)

    /**
     * Returns a [RootRequestBuilder] instance for querying server and network information.
     *
     * The root endpoint provides information about the Horizon server and the Stellar network
     * it's connected to, including version information, protocol versions, and network passphrase.
     *
     * @return [com.stellar.sdk.horizon.requests.RootRequestBuilder] instance
     */
    fun root(): com.stellar.sdk.horizon.requests.RootRequestBuilder {
        return com.stellar.sdk.horizon.requests.RootRequestBuilder(httpClient, serverUri)
    }

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
    fun transactions(): com.stellar.sdk.horizon.requests.TransactionsRequestBuilder {
        return com.stellar.sdk.horizon.requests.TransactionsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns an [OperationsRequestBuilder] instance for querying operations.
     *
     * @return [OperationsRequestBuilder] instance
     */
    fun operations(): com.stellar.sdk.horizon.requests.OperationsRequestBuilder {
        return com.stellar.sdk.horizon.requests.OperationsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns a [PaymentsRequestBuilder] instance for querying payment operations.
     *
     * @return [PaymentsRequestBuilder] instance
     */
    fun payments(): com.stellar.sdk.horizon.requests.PaymentsRequestBuilder {
        return com.stellar.sdk.horizon.requests.PaymentsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns an [EffectsRequestBuilder] instance for querying effects.
     *
     * @return [EffectsRequestBuilder] instance
     */
    fun effects(): com.stellar.sdk.horizon.requests.EffectsRequestBuilder {
        return com.stellar.sdk.horizon.requests.EffectsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns a [LedgersRequestBuilder] instance for querying ledgers.
     *
     * @return [LedgersRequestBuilder] instance
     */
    fun ledgers(): com.stellar.sdk.horizon.requests.LedgersRequestBuilder {
        return com.stellar.sdk.horizon.requests.LedgersRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns an [OffersRequestBuilder] instance for querying offers.
     *
     * @return [OffersRequestBuilder] instance
     */
    fun offers(): com.stellar.sdk.horizon.requests.OffersRequestBuilder {
        return com.stellar.sdk.horizon.requests.OffersRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns a [TradesRequestBuilder] instance for querying trades.
     *
     * @return [TradesRequestBuilder] instance
     */
    fun trades(): com.stellar.sdk.horizon.requests.TradesRequestBuilder {
        return com.stellar.sdk.horizon.requests.TradesRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns an [AssetsRequestBuilder] instance for querying assets.
     *
     * @return [AssetsRequestBuilder] instance
     */
    fun assets(): com.stellar.sdk.horizon.requests.AssetsRequestBuilder {
        return com.stellar.sdk.horizon.requests.AssetsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns a [ClaimableBalancesRequestBuilder] instance for querying claimable balances.
     *
     * @return [ClaimableBalancesRequestBuilder] instance
     */
    fun claimableBalances(): com.stellar.sdk.horizon.requests.ClaimableBalancesRequestBuilder {
        return com.stellar.sdk.horizon.requests.ClaimableBalancesRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns a [LiquidityPoolsRequestBuilder] instance for querying liquidity pools.
     *
     * @return [LiquidityPoolsRequestBuilder] instance
     */
    fun liquidityPools(): com.stellar.sdk.horizon.requests.LiquidityPoolsRequestBuilder {
        return com.stellar.sdk.horizon.requests.LiquidityPoolsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns an [OrderBookRequestBuilder] instance for querying order books.
     *
     * @return [OrderBookRequestBuilder] instance
     */
    fun orderBook(): com.stellar.sdk.horizon.requests.OrderBookRequestBuilder {
        return com.stellar.sdk.horizon.requests.OrderBookRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns a [StrictSendPathsRequestBuilder] instance for finding payment paths with strict send.
     *
     * Strict send paths find possible payment routes starting with a specific source asset and amount.
     * Use this when you know how much you want to send and want to find out what destinations are reachable.
     *
     * @return [StrictSendPathsRequestBuilder] instance
     */
    fun strictSendPaths(): com.stellar.sdk.horizon.requests.StrictSendPathsRequestBuilder {
        return com.stellar.sdk.horizon.requests.StrictSendPathsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns a [StrictReceivePathsRequestBuilder] instance for finding payment paths with strict receive.
     *
     * Strict receive paths find possible payment routes to arrive at a specific destination asset and amount.
     * Use this when you know how much you want to receive and want to find out what sources can provide it.
     *
     * @return [StrictReceivePathsRequestBuilder] instance
     */
    fun strictReceivePaths(): com.stellar.sdk.horizon.requests.StrictReceivePathsRequestBuilder {
        return com.stellar.sdk.horizon.requests.StrictReceivePathsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Returns a [TradeAggregationsRequestBuilder] instance for querying trade aggregations.
     *
     * Trade aggregations divide a given time range into segments and aggregate statistics about
     * trading activity for each segment. This is useful for displaying price charts and analyzing
     * trading patterns.
     *
     * All parameters are required:
     * @param baseAssetType The base asset type (native, credit_alphanum4, credit_alphanum12)
     * @param baseAssetCode The base asset code (null for native)
     * @param baseAssetIssuer The base asset issuer (null for native)
     * @param counterAssetType The counter asset type (native, credit_alphanum4, credit_alphanum12)
     * @param counterAssetCode The counter asset code (null for native)
     * @param counterAssetIssuer The counter asset issuer (null for native)
     * @param startTime Start of time range in milliseconds since epoch
     * @param endTime End of time range in milliseconds since epoch
     * @param resolution Size of each time bucket in milliseconds
     * @param offset Offset from start time for bucketing in milliseconds (default 0)
     * @return [TradeAggregationsRequestBuilder] instance
     */
    fun tradeAggregations(
        baseAssetType: String,
        baseAssetCode: String?,
        baseAssetIssuer: String?,
        counterAssetType: String,
        counterAssetCode: String?,
        counterAssetIssuer: String?,
        startTime: Long,
        endTime: Long,
        resolution: Long,
        offset: Long = 0
    ): com.stellar.sdk.horizon.requests.TradeAggregationsRequestBuilder {
        return com.stellar.sdk.horizon.requests.TradeAggregationsRequestBuilder(
            httpClient,
            serverUri,
            baseAssetType,
            baseAssetCode,
            baseAssetIssuer,
            counterAssetType,
            counterAssetCode,
            counterAssetIssuer,
            startTime,
            endTime,
            resolution,
            offset
        )
    }

    /**
     * Returns a [FeeStatsRequestBuilder] instance for querying fee statistics.
     *
     * Fee stats provide information about the transaction fees network validators are currently
     * accepting. This data helps users set appropriate transaction fees to ensure their transactions
     * are accepted by the network in a timely manner.
     *
     * @return [FeeStatsRequestBuilder] instance
     */
    fun feeStats(): com.stellar.sdk.horizon.requests.FeeStatsRequestBuilder {
        return com.stellar.sdk.horizon.requests.FeeStatsRequestBuilder(httpClient, serverUri)
    }

    /**
     * Submits a transaction to the Stellar network.
     *
     * This method submits a base64-encoded transaction envelope to Horizon and waits for it
     * to be ingested into a ledger. By default, this function checks if destination accounts
     * require a memo as defined in SEP-0029. Use the overloaded version with skipMemoRequiredCheck
     * to bypass this validation.
     *
     * @param transactionEnvelopeXdr Base64-encoded transaction envelope XDR
     * @return [com.stellar.sdk.horizon.responses.TransactionResponse] containing the result
     * @throws com.stellar.sdk.horizon.exceptions.AccountRequiresMemoException when a transaction is trying to submit an operation to an account which requires a memo
     * @throws com.stellar.sdk.horizon.exceptions.NetworkException All the exceptions below are subclasses of NetworkException
     * @throws com.stellar.sdk.horizon.exceptions.BadRequestException if the request fails due to a bad request (4xx)
     * @throws com.stellar.sdk.horizon.exceptions.BadResponseException if the request fails due to a bad response from the server (5xx)
     * @throws com.stellar.sdk.horizon.exceptions.TooManyRequestsException if the request fails due to too many requests sent to the server
     * @throws com.stellar.sdk.horizon.exceptions.RequestTimeoutException when Horizon returns a Timeout or connection timeout occurred
     * @throws com.stellar.sdk.horizon.exceptions.UnknownResponseException if the server returns an unknown status code
     * @throws com.stellar.sdk.horizon.exceptions.ConnectionErrorException when the request cannot be executed due to cancellation or connectivity problems
     * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0029.md">SEP-0029</a>
     */
    suspend fun submitTransaction(transactionEnvelopeXdr: String): com.stellar.sdk.horizon.responses.TransactionResponse {
        return submitTransaction(transactionEnvelopeXdr, skipMemoRequiredCheck = false)
    }

    /**
     * Submits a transaction to the Stellar network with optional memo required check.
     *
     * This method submits a base64-encoded transaction envelope to Horizon and waits for it
     * to be ingested into a ledger. The submission uses the submitHttpClient with extended
     * timeout (65 seconds) to account for transaction processing time.
     *
     * @param transactionEnvelopeXdr Base64-encoded transaction envelope XDR
     * @param skipMemoRequiredCheck Set to true to skip the SEP-0029 memo required check
     * @return [com.stellar.sdk.horizon.responses.TransactionResponse] containing the result
     * @throws com.stellar.sdk.horizon.exceptions.AccountRequiresMemoException when skipMemoRequiredCheck is false and a transaction is trying to submit an operation to an account which requires a memo
     * @throws com.stellar.sdk.horizon.exceptions.NetworkException All the exceptions below are subclasses of NetworkException
     * @throws com.stellar.sdk.horizon.exceptions.BadRequestException if the request fails due to a bad request (4xx)
     * @throws com.stellar.sdk.horizon.exceptions.BadResponseException if the request fails due to a bad response from the server (5xx)
     * @throws com.stellar.sdk.horizon.exceptions.TooManyRequestsException if the request fails due to too many requests sent to the server
     * @throws com.stellar.sdk.horizon.exceptions.RequestTimeoutException when Horizon returns a Timeout or connection timeout occurred
     * @throws com.stellar.sdk.horizon.exceptions.UnknownResponseException if the server returns an unknown status code
     * @throws com.stellar.sdk.horizon.exceptions.ConnectionErrorException when the request cannot be executed due to cancellation or connectivity problems
     * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0029.md">SEP-0029</a>
     */
    suspend fun submitTransaction(
        transactionEnvelopeXdr: String,
        skipMemoRequiredCheck: Boolean
    ): com.stellar.sdk.horizon.responses.TransactionResponse {
        if (!skipMemoRequiredCheck) {
            sep29Checker.checkMemoRequired(transactionEnvelopeXdr)
        }

        return executePostRequest(
            client = submitHttpClient,
            url = URLBuilder(serverUri).apply {
                appendPathSegments("transactions")
            }.build(),
            formParameters = io.ktor.http.parameters {
                append("tx", transactionEnvelopeXdr)
            }
        )
    }

    /**
     * Submits a transaction to the Stellar network asynchronously.
     *
     * Unlike the synchronous version ([submitTransaction]), which blocks and waits for the
     * transaction to be ingested in Horizon, this endpoint relays the response from Stellar Core
     * directly back to the user. By default, this function checks if destination accounts
     * require a memo as defined in SEP-0029.
     *
     * @param transactionEnvelopeXdr Base64-encoded transaction envelope XDR
     * @return [com.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse] containing the submission status
     * @throws com.stellar.sdk.horizon.exceptions.AccountRequiresMemoException when a transaction is trying to submit an operation to an account which requires a memo
     * @throws com.stellar.sdk.horizon.exceptions.NetworkException All the exceptions below are subclasses of NetworkException
     * @throws com.stellar.sdk.horizon.exceptions.BadRequestException if the request fails due to a bad request (4xx)
     * @throws com.stellar.sdk.horizon.exceptions.BadResponseException if the request fails due to a bad response from the server (5xx)
     * @throws com.stellar.sdk.horizon.exceptions.TooManyRequestsException if the request fails due to too many requests sent to the server
     * @throws com.stellar.sdk.horizon.exceptions.RequestTimeoutException when Horizon returns a Timeout or connection timeout occurred
     * @throws com.stellar.sdk.horizon.exceptions.UnknownResponseException if the server returns an unknown status code
     * @throws com.stellar.sdk.horizon.exceptions.ConnectionErrorException when the request cannot be executed due to cancellation or connectivity problems
     * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/submit-async-transaction">Submit a Transaction Asynchronously</a>
     * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0029.md">SEP-0029</a>
     */
    suspend fun submitTransactionAsync(transactionEnvelopeXdr: String): com.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse {
        return submitTransactionAsync(transactionEnvelopeXdr, skipMemoRequiredCheck = false)
    }

    /**
     * Submits a transaction to the Stellar network asynchronously with optional memo required check.
     *
     * Unlike the synchronous version, which blocks and waits for the transaction to be ingested
     * in Horizon, this endpoint relays the response from Stellar Core directly back to the user.
     * This is useful for fire-and-forget scenarios or when you want to poll for results separately.
     *
     * @param transactionEnvelopeXdr Base64-encoded transaction envelope XDR
     * @param skipMemoRequiredCheck Set to true to skip the SEP-0029 memo required check
     * @return [com.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse] containing the submission status
     * @throws com.stellar.sdk.horizon.exceptions.AccountRequiresMemoException when skipMemoRequiredCheck is false and a transaction is trying to submit an operation to an account which requires a memo
     * @throws com.stellar.sdk.horizon.exceptions.NetworkException All the exceptions below are subclasses of NetworkException
     * @throws com.stellar.sdk.horizon.exceptions.BadRequestException if the request fails due to a bad request (4xx)
     * @throws com.stellar.sdk.horizon.exceptions.BadResponseException if the request fails due to a bad response from the server (5xx)
     * @throws com.stellar.sdk.horizon.exceptions.TooManyRequestsException if the request fails due to too many requests sent to the server
     * @throws com.stellar.sdk.horizon.exceptions.RequestTimeoutException when Horizon returns a Timeout or connection timeout occurred
     * @throws com.stellar.sdk.horizon.exceptions.UnknownResponseException if the server returns an unknown status code
     * @throws com.stellar.sdk.horizon.exceptions.ConnectionErrorException when the request cannot be executed due to cancellation or connectivity problems
     * @see <a href="https://developers.stellar.org/docs/data/horizon/api-reference/submit-async-transaction">Submit a Transaction Asynchronously</a>
     * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0029.md">SEP-0029</a>
     */
    suspend fun submitTransactionAsync(
        transactionEnvelopeXdr: String,
        skipMemoRequiredCheck: Boolean
    ): com.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse {
        if (!skipMemoRequiredCheck) {
            sep29Checker.checkMemoRequired(transactionEnvelopeXdr)
        }

        return executePostRequest(
            client = submitHttpClient,
            url = URLBuilder(serverUri).apply {
                appendPathSegments("transactions_async")
            }.build(),
            formParameters = io.ktor.http.parameters {
                append("tx", transactionEnvelopeXdr)
            }
        )
    }

    /**
     * Executes a POST request with form parameters and handles the response.
     *
     * @param T The type of the response object
     * @param client The HTTP client to use for the request
     * @param url The URL to send the POST request to
     * @param formParameters The form parameters to include in the request body
     * @return The response object of type T
     * @throws com.stellar.sdk.horizon.exceptions.NetworkException All the exceptions below are subclasses of NetworkException
     * @throws com.stellar.sdk.horizon.exceptions.BadRequestException If the request fails due to a bad request (4xx)
     * @throws com.stellar.sdk.horizon.exceptions.BadResponseException If the request fails due to a bad response from the server (5xx)
     * @throws com.stellar.sdk.horizon.exceptions.TooManyRequestsException If the request fails due to too many requests sent to the server
     * @throws com.stellar.sdk.horizon.exceptions.RequestTimeoutException When Horizon returns a Timeout or connection timeout occurred
     * @throws com.stellar.sdk.horizon.exceptions.UnknownResponseException If the server returns an unknown status code
     * @throws com.stellar.sdk.horizon.exceptions.ConnectionErrorException When the request cannot be executed due to cancellation or connectivity problems
     */
    private suspend inline fun <reified T> executePostRequest(
        client: HttpClient,
        url: Url,
        formParameters: io.ktor.http.Parameters
    ): T {
        return try {
            val response = client.post(url) {
                setBody(io.ktor.client.request.forms.FormDataContent(formParameters))
            }

            when (response.status.value) {
                in 200..299 -> response.body<T>()
                in 400..499 -> {
                    val body = try {
                        response.body<String>()
                    } catch (e: Exception) {
                        ""
                    }
                    when (response.status.value) {
                        429 -> throw com.stellar.sdk.horizon.exceptions.TooManyRequestsException(
                            code = response.status.value,
                            body = body
                        )
                        504 -> throw com.stellar.sdk.horizon.exceptions.RequestTimeoutException(
                            code = response.status.value,
                            body = body
                        )
                        else -> throw com.stellar.sdk.horizon.exceptions.BadRequestException(
                            code = response.status.value,
                            body = body
                        )
                    }
                }
                in 500..599 -> {
                    val body = try {
                        response.body<String>()
                    } catch (e: Exception) {
                        ""
                    }
                    throw com.stellar.sdk.horizon.exceptions.BadResponseException(
                        code = response.status.value,
                        body = body
                    )
                }
                else -> {
                    val body = try {
                        response.body<String>()
                    } catch (e: Exception) {
                        ""
                    }
                    throw com.stellar.sdk.horizon.exceptions.UnknownResponseException(
                        code = response.status.value,
                        body = body
                    )
                }
            }
        } catch (e: com.stellar.sdk.horizon.exceptions.NetworkException) {
            throw e
        } catch (e: com.stellar.sdk.horizon.exceptions.SdkException) {
            throw e
        } catch (e: Exception) {
            throw com.stellar.sdk.horizon.exceptions.ConnectionErrorException(e)
        }
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
