// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep24

import com.soneso.stellar.sdk.HttpTimeouts
import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep24.exceptions.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * SEP-24 Hosted Deposit and Withdrawal service client.
 *
 * Provides interactive deposit and withdrawal flows with Stellar anchors.
 * SEP-24 enables wallets to offer on/off ramp functionality through a standardized
 * protocol where users complete the deposit or withdrawal process through an
 * anchor-hosted web interface.
 *
 * This service handles all communication with SEP-24 compliant anchors, including:
 * - Discovering anchor capabilities and supported assets via /info
 * - Initiating interactive deposit flows via /transactions/deposit/interactive
 * - Initiating interactive withdrawal flows via /transactions/withdraw/interactive
 * - Querying transaction status and history
 * - Polling for transaction completion
 *
 * Authentication:
 * - The /info endpoint does not require authentication
 * - All other endpoints require a valid SEP-10 JWT token
 *
 * Typical workflow:
 * 1. Create service instance via [fromDomain] or constructor
 * 2. Call [info] to discover supported assets and limits
 * 3. Authenticate via SEP-10 WebAuth to obtain a JWT token
 * 4. Call [deposit] or [withdraw] to initiate an interactive flow
 * 5. Display the returned URL in a webview for user interaction
 * 6. Poll [transaction] or use [pollTransaction] to monitor completion
 *
 * Example - Complete deposit flow:
 * ```kotlin
 * // 1. Initialize from domain
 * val sep24 = Sep24Service.fromDomain("anchor.example.com")
 *
 * // 2. Check supported assets
 * val info = sep24.info()
 * val usdcEnabled = info.depositAssets?.get("USDC")?.enabled == true
 *
 * // 3. Authenticate via SEP-10
 * val webAuth = WebAuth.fromDomain("anchor.example.com", network)
 * val jwt = webAuth.jwtToken(accountId, listOf(keyPair)).token
 *
 * // 4. Initiate deposit
 * val response = sep24.deposit(Sep24DepositRequest(
 *     assetCode = "USDC",
 *     jwt = jwt,
 *     account = keyPair.getAccountId(),
 *     amount = "100"
 * ))
 *
 * // 5. Display interactive URL in webview
 * displayWebView(response.url)
 *
 * // 6. Poll for completion
 * val tx = sep24.pollTransaction(
 *     Sep24TransactionRequest(jwt = jwt, id = response.id),
 *     onStatusChange = { println("Status: ${it.status}") }
 * )
 *
 * when (tx.getStatusEnum()) {
 *     Sep24TransactionStatus.COMPLETED -> println("Success! Received ${tx.amountOut}")
 *     Sep24TransactionStatus.ERROR -> println("Failed: ${tx.message}")
 *     else -> println("Final status: ${tx.status}")
 * }
 * ```
 *
 * Example - Error handling:
 * ```kotlin
 * try {
 *     val response = sep24.deposit(request)
 * } catch (e: Sep24AuthenticationRequiredException) {
 *     // JWT expired, re-authenticate via SEP-10
 * } catch (e: Sep24InvalidRequestException) {
 *     // Invalid parameters, check request
 * } catch (e: Sep24ServerErrorException) {
 *     // Anchor server error, retry with backoff
 * }
 * ```
 *
 * Example - SEP-38 quote integration:
 * ```kotlin
 * // Get firm quote first
 * val quoteService = QuoteService.fromDomain("anchor.example.com")
 * val quote = quoteService.postQuote(
 *     Sep38QuoteRequest(
 *         context = "sep24",
 *         sellAsset = "iso4217:USD",
 *         buyAsset = "stellar:USDC:GA...",
 *         sellAmount = "100"
 *     ),
 *     jwt
 * )
 *
 * // Use quote in deposit
 * val response = sep24.deposit(Sep24DepositRequest(
 *     assetCode = "USDC",
 *     jwt = jwt,
 *     quoteId = quote.id,
 *     amount = quote.sellAmount
 * ))
 * ```
 *
 * See also:
 * - [fromDomain] for automatic configuration from stellar.toml
 * - [Sep24DepositRequest] for deposit parameters
 * - [Sep24WithdrawRequest] for withdrawal parameters
 * - [Sep24Transaction] for transaction status and details
 * - [SEP-0024 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0024.md)
 *
 * @property serviceAddress The base URL of the anchor's SEP-24 transfer server
 * @property httpClient Optional custom HTTP client for testing
 * @property httpRequestHeaders Optional custom headers for all requests
 */
class Sep24Service(
    private val serviceAddress: String,
    private val httpClient: HttpClient? = null,
    private val httpRequestHeaders: Map<String, String>? = null
) {
    /**
     * JSON configuration for parsing server responses.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        /**
         * Creates a Sep24Service by discovering the endpoint from stellar.toml.
         *
         * Fetches the stellar.toml file from the specified domain and extracts the
         * TRANSFER_SERVER_SEP0024 address. This is the recommended method for initializing
         * the service as it automatically configures the correct endpoint.
         *
         * @param domain The domain name (without protocol). E.g., "anchor.example.com"
         * @param httpClient Optional custom HTTP client for testing
         * @param httpRequestHeaders Optional custom headers for requests
         * @return Sep24Service configured with the domain's transfer server endpoint
         * @throws Sep24Exception If TRANSFER_SERVER_SEP0024 is not found in stellar.toml
         *
         * Example:
         * ```kotlin
         * val sep24 = Sep24Service.fromDomain("testanchor.stellar.org")
         * ```
         */
        suspend fun fromDomain(
            domain: String,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): Sep24Service {
            val stellarToml = StellarToml.fromDomain(
                domain = domain,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )

            val address = stellarToml.generalInformation.transferServerSep24
                ?: throw Sep24Exception(
                    "TRANSFER_SERVER_SEP0024 not found in stellar.toml for domain: $domain"
                )

            return Sep24Service(
                serviceAddress = address,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )
        }
    }

    /**
     * Gets anchor capabilities and supported assets.
     *
     * Returns information about what assets the anchor supports for deposit and withdrawal,
     * including limits, fees, and feature support. No authentication required.
     *
     * @param lang Optional language code (RFC 4646, e.g., "en", "es") for localized messages
     * @return Sep24InfoResponse containing anchor capabilities
     * @throws Sep24ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val info = sep24.info()
     *
     * // Check deposit assets
     * info.depositAssets?.forEach { (code, assetInfo) ->
     *     if (assetInfo.enabled) {
     *         println("$code: min=${assetInfo.minAmount}, max=${assetInfo.maxAmount}")
     *     }
     * }
     *
     * // Check feature support
     * if (info.features?.claimableBalances == true) {
     *     println("Anchor supports claimable balances")
     * }
     * ```
     */
    suspend fun info(lang: String? = null): Sep24InfoResponse {
        val queryParams = mutableMapOf<String, String?>()
        lang?.let { queryParams["lang"] = it }

        return httpGet(
            endpoint = "info",
            queryParams = queryParams,
            jwt = null
        )
    }

    /**
     * Gets the fee for a deposit or withdrawal operation.
     *
     * Note: This endpoint is deprecated in SEP-24. Anchors should use SEP-38 quotes
     * for fee calculation instead. This method is provided for compatibility with
     * anchors that still support the legacy fee endpoint.
     *
     * @param request Fee request parameters
     * @return Sep24FeeResponse containing the fee amount
     * @throws Sep24AuthenticationRequiredException If authentication is required but not provided
     * @throws Sep24InvalidRequestException On validation error
     * @throws Sep24ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val feeResponse = sep24.fee(Sep24FeeRequest(
     *     operation = "deposit",
     *     assetCode = "USDC",
     *     amount = "100"
     * ))
     * println("Fee: ${feeResponse.fee}")
     * ```
     */
    suspend fun fee(request: Sep24FeeRequest): Sep24FeeResponse {
        val queryParams = mutableMapOf<String, String?>(
            "operation" to request.operation,
            "asset_code" to request.assetCode,
            "amount" to request.amount,
            "type" to request.type
        )

        return httpGet(
            endpoint = "fee",
            queryParams = queryParams,
            jwt = request.jwt
        )
    }

    /**
     * Initiates an interactive deposit flow.
     *
     * Returns a URL to display in a webview where the user can complete the deposit
     * process. After the user finishes, the anchor will deposit funds to the specified
     * Stellar account.
     *
     * @param request Deposit request parameters
     * @return Sep24InteractiveResponse with URL to display and transaction ID
     * @throws Sep24AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep24InvalidRequestException On validation error
     * @throws Sep24ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep24.deposit(Sep24DepositRequest(
     *     assetCode = "USDC",
     *     jwt = jwtToken,
     *     account = keyPair.getAccountId(),
     *     amount = "100",
     *     claimableBalanceSupported = true
     * ))
     *
     * println("Transaction ID: ${response.id}")
     * println("Interactive URL: ${response.url}")
     * // Display response.url in a webview
     * ```
     */
    suspend fun deposit(request: Sep24DepositRequest): Sep24InteractiveResponse {
        val fields = mutableMapOf<String, String?>()
        fields["asset_code"] = request.assetCode
        fields["asset_issuer"] = request.assetIssuer
        fields["source_asset"] = request.sourceAsset
        fields["amount"] = request.amount
        fields["quote_id"] = request.quoteId
        fields["account"] = request.account
        fields["memo"] = request.memo
        fields["memo_type"] = request.memoType
        fields["wallet_name"] = request.walletName
        fields["wallet_url"] = request.walletUrl
        fields["lang"] = request.lang
        fields["claimable_balance_supported"] = request.claimableBalanceSupported?.toString()
        fields["customer_id"] = request.customerId

        // Add KYC fields
        request.kycFields?.forEach { (key, value) ->
            fields[key] = value
        }

        return httpPostMultipart(
            endpoint = "transactions/deposit/interactive",
            fields = fields,
            files = request.kycFiles,
            jwt = request.jwt
        )
    }

    /**
     * Initiates an interactive withdrawal flow.
     *
     * Returns a URL to display in a webview where the user can complete the withdrawal
     * process. After the user sends funds to the anchor's Stellar account, the anchor
     * will deliver the withdrawn assets through the specified off-chain channel.
     *
     * @param request Withdrawal request parameters
     * @return Sep24InteractiveResponse with URL to display and transaction ID
     * @throws Sep24AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep24InvalidRequestException On validation error
     * @throws Sep24ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep24.withdraw(Sep24WithdrawRequest(
     *     assetCode = "USDC",
     *     jwt = jwtToken,
     *     account = keyPair.getAccountId(),
     *     amount = "100"
     * ))
     *
     * println("Transaction ID: ${response.id}")
     * println("Interactive URL: ${response.url}")
     * // Display response.url in a webview
     * ```
     */
    suspend fun withdraw(request: Sep24WithdrawRequest): Sep24InteractiveResponse {
        val fields = mutableMapOf<String, String?>()
        fields["asset_code"] = request.assetCode
        fields["asset_issuer"] = request.assetIssuer
        fields["destination_asset"] = request.destinationAsset
        fields["amount"] = request.amount
        fields["quote_id"] = request.quoteId
        fields["account"] = request.account
        @Suppress("DEPRECATION")
        fields["memo"] = request.memo
        @Suppress("DEPRECATION")
        fields["memo_type"] = request.memoType
        fields["wallet_name"] = request.walletName
        fields["wallet_url"] = request.walletUrl
        fields["lang"] = request.lang
        fields["refund_memo"] = request.refundMemo
        fields["refund_memo_type"] = request.refundMemoType
        fields["customer_id"] = request.customerId

        // Add KYC fields
        request.kycFields?.forEach { (key, value) ->
            fields[key] = value
        }

        return httpPostMultipart(
            endpoint = "transactions/withdraw/interactive",
            fields = fields,
            files = request.kycFiles,
            jwt = request.jwt
        )
    }

    /**
     * Gets transaction history for the authenticated account.
     *
     * Returns a list of transactions for the specified asset, optionally filtered
     * by date, kind, and pagination parameters.
     *
     * @param request Transaction history request parameters
     * @return Sep24TransactionsResponse containing list of transactions
     * @throws Sep24AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep24ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep24.transactions(Sep24TransactionsRequest(
     *     assetCode = "USDC",
     *     jwt = jwtToken,
     *     kind = "deposit",
     *     limit = 10
     * ))
     *
     * response.transactions.forEach { tx ->
     *     println("${tx.id}: ${tx.status} - ${tx.amountIn} -> ${tx.amountOut}")
     * }
     * ```
     */
    suspend fun transactions(request: Sep24TransactionsRequest): Sep24TransactionsResponse {
        val queryParams = mutableMapOf<String, String?>(
            "asset_code" to request.assetCode,
            "no_older_than" to request.noOlderThan,
            "limit" to request.limit?.toString(),
            "kind" to request.kind,
            "paging_id" to request.pagingId,
            "lang" to request.lang
        )

        return httpGet(
            endpoint = "transactions",
            queryParams = queryParams,
            jwt = request.jwt
        )
    }

    /**
     * Gets a single transaction by its identifier.
     *
     * At least one of id, stellarTransactionId, or externalTransactionId must be
     * provided in the request to identify the transaction.
     *
     * @param request Transaction query parameters
     * @return Sep24TransactionResponse containing the transaction details
     * @throws Sep24AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep24TransactionNotFoundException If transaction not found
     * @throws Sep24ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep24.transaction(Sep24TransactionRequest(
     *     jwt = jwtToken,
     *     id = transactionId
     * ))
     *
     * val tx = response.transaction
     * println("Status: ${tx.status}")
     * println("Amount in: ${tx.amountIn}")
     * println("Amount out: ${tx.amountOut}")
     * ```
     */
    suspend fun transaction(request: Sep24TransactionRequest): Sep24TransactionResponse {
        val queryParams = mutableMapOf<String, String?>(
            "id" to request.id,
            "stellar_transaction_id" to request.stellarTransactionId,
            "external_transaction_id" to request.externalTransactionId,
            "lang" to request.lang
        )

        return httpGet(
            endpoint = "transaction",
            queryParams = queryParams,
            jwt = request.jwt
        )
    }

    /**
     * Polls a transaction until it reaches a terminal status.
     *
     * Continuously queries the transaction status at the specified interval until
     * the transaction reaches a terminal state (completed, refunded, expired, error,
     * no_market, too_small, or too_large).
     *
     * @param request Transaction request (must have id, stellarTransactionId, or externalTransactionId)
     * @param pollIntervalMs Interval between polls in milliseconds (default 5000ms)
     * @param maxAttempts Maximum poll attempts (default 60, which is 5 minutes at 5s intervals)
     * @param onStatusChange Optional callback invoked when transaction status changes
     * @return Transaction in terminal status
     * @throws Sep24Exception If max attempts exceeded without reaching terminal status
     * @throws Sep24AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep24TransactionNotFoundException If transaction not found
     * @throws Sep24ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val tx = sep24.pollTransaction(
     *     request = Sep24TransactionRequest(jwt = jwtToken, id = transactionId),
     *     pollIntervalMs = 3000,
     *     maxAttempts = 100,
     *     onStatusChange = { tx ->
     *         println("Status changed to: ${tx.status}")
     *         tx.statusEta?.let { eta ->
     *             println("Estimated time: ${eta}s")
     *         }
     *     }
     * )
     *
     * when (tx.getStatusEnum()) {
     *     Sep24TransactionStatus.COMPLETED -> {
     *         println("Deposit completed!")
     *         println("Received: ${tx.amountOut} ${tx.amountOutAsset}")
     *     }
     *     Sep24TransactionStatus.REFUNDED -> {
     *         println("Transaction refunded")
     *         tx.refunds?.let { refunds ->
     *             println("Refunded: ${refunds.amountRefunded}")
     *         }
     *     }
     *     Sep24TransactionStatus.ERROR -> {
     *         println("Transaction failed: ${tx.message}")
     *     }
     *     else -> println("Final status: ${tx.status}")
     * }
     * ```
     */
    suspend fun pollTransaction(
        request: Sep24TransactionRequest,
        pollIntervalMs: Long = 5000,
        maxAttempts: Int = 60,
        onStatusChange: ((Sep24Transaction) -> Unit)? = null
    ): Sep24Transaction {
        var lastStatus: String? = null

        repeat(maxAttempts) {
            val response = transaction(request)
            val tx = response.transaction

            if (tx.status != lastStatus) {
                lastStatus = tx.status
                onStatusChange?.invoke(tx)
            }

            if (tx.isTerminal()) {
                return tx
            }

            delay(pollIntervalMs)
        }

        throw Sep24Exception("Polling timeout after $maxAttempts attempts")
    }

    // ========== Private HTTP Utilities ==========

    /**
     * Executes a block with an HTTP client, managing lifecycle properly.
     */
    private suspend fun <T> withHttpClient(block: suspend (HttpClient) -> T): T {
        val client = httpClient ?: HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                connectTimeoutMillis = HttpTimeouts.CONNECT_TIMEOUT_MS
                requestTimeoutMillis = HttpTimeouts.REQUEST_TIMEOUT_MS
            }
        }

        return try {
            block(client)
        } finally {
            if (httpClient == null) {
                client.close()
            }
        }
    }

    /**
     * Builds request headers with optional JWT authentication.
     */
    private fun buildHeaders(jwt: String? = null): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        httpRequestHeaders?.let { headers.putAll(it) }
        jwt?.let { headers["Authorization"] = "Bearer $it" }
        return headers
    }

    /**
     * Builds a URL with query parameters.
     */
    private fun buildUrl(endpoint: String, queryParams: Map<String, String?> = emptyMap()): String {
        val base = serviceAddress.trimEnd('/')
        val path = endpoint.trimStart('/')
        val url = "$base/$path"

        val params = queryParams.filterValues { it != null }
        if (params.isEmpty()) return url

        val query = params.entries.joinToString("&") { (key, value) ->
            "${key.encodeURLParameter()}=${value!!.encodeURLParameter()}"
        }
        return "$url?$query"
    }

    /**
     * Performs a GET request and parses the response.
     */
    private suspend inline fun <reified T> httpGet(
        endpoint: String,
        queryParams: Map<String, String?>,
        jwt: String?
    ): T = withHttpClient { client ->
        val url = buildUrl(endpoint, queryParams)
        val headers = buildHeaders(jwt)

        val response = client.get(url) {
            headers.forEach { (key, value) -> header(key, value) }
        }

        handleResponse(response)
    }

    /**
     * Performs a multipart POST request and parses the response.
     */
    private suspend inline fun <reified T> httpPostMultipart(
        endpoint: String,
        fields: Map<String, String?>,
        files: Map<String, ByteArray>?,
        jwt: String
    ): T = withHttpClient { client ->
        val url = buildUrl(endpoint)

        val response = client.submitFormWithBinaryData(
            url = url,
            formData = formData {
                // Add text fields
                fields.forEach { (key, value) ->
                    value?.let { append(key, it) }
                }
                // Add file uploads
                files?.forEach { (key, bytes) ->
                    append(key, bytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=$key")
                    })
                }
            }
        ) {
            header("Authorization", "Bearer $jwt")
            httpRequestHeaders?.forEach { (key, value) -> header(key, value) }
        }

        handleResponse(response)
    }

    /**
     * Handles HTTP response and maps errors to appropriate exceptions.
     */
    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T {
        val body = response.bodyAsText()

        return when (response.status.value) {
            200, 201 -> {
                json.decodeFromString<T>(body)
            }
            400 -> {
                val error = parseErrorMessage(body)
                throw Sep24InvalidRequestException(error)
            }
            403 -> {
                val errorType = parseErrorType(body)
                if (errorType == "authentication_required") {
                    throw Sep24AuthenticationRequiredException()
                }
                throw Sep24ServerErrorException(parseErrorMessage(body), 403)
            }
            404 -> {
                throw Sep24TransactionNotFoundException()
            }
            else -> {
                throw Sep24ServerErrorException(
                    parseErrorMessage(body),
                    response.status.value
                )
            }
        }
    }

    /**
     * Extracts error message from response body.
     */
    private fun parseErrorMessage(body: String): String {
        return try {
            json.decodeFromString<JsonObject>(body)["error"]?.jsonPrimitive?.content
                ?: "Unknown error"
        } catch (e: Exception) {
            body.ifBlank { "Unknown error" }
        }
    }

    /**
     * Extracts error type from response body.
     */
    private fun parseErrorType(body: String): String? {
        return try {
            json.decodeFromString<JsonObject>(body)["type"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}
