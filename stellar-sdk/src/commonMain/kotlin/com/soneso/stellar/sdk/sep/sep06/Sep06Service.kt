// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep06

import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep06.exceptions.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * SEP-6 Programmatic Deposit and Withdrawal service client.
 *
 * Provides non-interactive deposit and withdrawal flows with Stellar anchors.
 * SEP-6 enables wallets to offer on/off ramp functionality through a standardized
 * protocol where all required information is provided programmatically in API requests.
 *
 * This service handles all communication with SEP-6 compliant anchors, including:
 * - Discovering anchor capabilities and supported assets via /info
 * - Initiating programmatic deposit flows via /deposit and /deposit-exchange
 * - Initiating programmatic withdrawal flows via /withdraw and /withdraw-exchange
 * - Querying fee information via /fee (deprecated, use SEP-38)
 * - Querying transaction status and history
 * - Updating transaction information via PATCH
 *
 * Authentication:
 * - The /info endpoint may not require authentication depending on anchor configuration
 * - All other endpoints require a valid SEP-10 JWT token
 *
 * Typical workflow:
 * 1. Create service instance via [fromDomain] or [fromUrl]
 * 2. Call [info] to discover supported assets and limits
 * 3. Authenticate via SEP-10 WebAuth to obtain a JWT token
 * 4. Call [deposit] or [withdraw] to initiate a programmatic flow
 * 5. Follow deposit instructions or send withdrawal payment
 * 6. Poll [transaction] to monitor completion
 *
 * Example - Complete deposit flow:
 * ```kotlin
 * // 1. Initialize from domain
 * val sep06 = Sep06Service.fromDomain("anchor.example.com")
 *
 * // 2. Check supported assets
 * val info = sep06.info()
 * val usdcEnabled = info.deposit?.get("USDC")?.enabled == true
 *
 * // 3. Authenticate via SEP-10
 * val webAuth = WebAuth.fromDomain("anchor.example.com", network)
 * val jwt = webAuth.jwtToken(accountId, listOf(keyPair)).token
 *
 * // 4. Initiate deposit
 * val response = sep06.deposit(Sep06DepositRequest(
 *     assetCode = "USDC",
 *     account = keyPair.getAccountId(),
 *     jwt = jwt,
 *     amount = "100"
 * ))
 *
 * // 5. Follow deposit instructions
 * response.instructions?.forEach { (field, instruction) ->
 *     println("$field: ${instruction.value} - ${instruction.description}")
 * }
 *
 * // 6. Poll for completion
 * val tx = sep06.transaction(Sep06TransactionRequest(id = response.id, jwt = jwt))
 * println("Status: ${tx.transaction.status}")
 * ```
 *
 * Example - Error handling:
 * ```kotlin
 * try {
 *     val response = sep06.deposit(request)
 * } catch (e: Sep06AuthenticationRequiredException) {
 *     // JWT expired, re-authenticate via SEP-10
 * } catch (e: Sep06CustomerInformationNeededException) {
 *     // Additional KYC fields required, submit via SEP-12
 *     println("Required fields: ${e.fields}")
 * } catch (e: Sep06CustomerInformationStatusException) {
 *     // KYC status prevents transaction
 *     println("KYC status: ${e.status}")
 * } catch (e: Sep06InvalidRequestException) {
 *     // Invalid parameters, check request
 * } catch (e: Sep06ServerErrorException) {
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
 *         context = "sep6",
 *         sellAsset = "iso4217:USD",
 *         buyAsset = "stellar:USDC:GA...",
 *         sellAmount = "100"
 *     ),
 *     jwt
 * )
 *
 * // Use quote in deposit-exchange
 * val response = sep06.depositExchange(Sep06DepositExchangeRequest(
 *     destinationAsset = "USDC",
 *     sourceAsset = "iso4217:USD",
 *     amount = quote.sellAmount,
 *     account = keyPair.getAccountId(),
 *     jwt = jwt,
 *     quoteId = quote.id
 * ))
 * ```
 *
 * See also:
 * - [fromDomain] for automatic configuration from stellar.toml
 * - [Sep06DepositRequest] for deposit parameters
 * - [Sep06WithdrawRequest] for withdrawal parameters
 * - [Sep06Transaction] for transaction status and details
 * - [SEP-0006 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0006.md)
 *
 * @property serviceAddress The base URL of the anchor's SEP-6 transfer server
 * @property httpClient Optional custom HTTP client for testing
 * @property httpRequestHeaders Optional custom headers for all requests
 */
class Sep06Service private constructor(
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
         * Creates a Sep06Service by discovering the endpoint from stellar.toml.
         *
         * Fetches the stellar.toml file from the specified domain and extracts the
         * TRANSFER_SERVER address. This is the recommended method for initializing
         * the service as it automatically configures the correct endpoint.
         *
         * @param domain The domain name (without protocol). E.g., "anchor.example.com"
         * @param httpClient Optional custom HTTP client for testing
         * @param httpRequestHeaders Optional custom headers for requests
         * @return Sep06Service configured with the domain's transfer server endpoint
         * @throws Sep06Exception If TRANSFER_SERVER is not found in stellar.toml
         *
         * Example:
         * ```kotlin
         * val sep06 = Sep06Service.fromDomain("testanchor.stellar.org")
         * ```
         */
        suspend fun fromDomain(
            domain: String,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): Sep06Service {
            val stellarToml = StellarToml.fromDomain(
                domain = domain,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )

            val address = stellarToml.generalInformation.transferServer
                ?: throw Sep06Exception(
                    "TRANSFER_SERVER not found in stellar.toml for domain: $domain"
                )

            return Sep06Service(
                serviceAddress = address,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )
        }

        /**
         * Creates a Sep06Service from a known transfer server URL.
         *
         * Use this method when you already know the transfer server URL and don't
         * need to discover it from stellar.toml.
         *
         * @param serviceAddress The base URL of the anchor's SEP-6 transfer server
         * @param httpClient Optional custom HTTP client for testing
         * @param httpRequestHeaders Optional custom headers for requests
         * @return Sep06Service configured with the specified transfer server endpoint
         *
         * Example:
         * ```kotlin
         * val sep06 = Sep06Service.fromUrl("https://api.anchor.example.com/sep6")
         * ```
         */
        fun fromUrl(
            serviceAddress: String,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): Sep06Service {
            return Sep06Service(
                serviceAddress = serviceAddress,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )
        }
    }

    /**
     * Gets anchor capabilities and supported assets.
     *
     * Returns information about what assets the anchor supports for deposit and withdrawal,
     * including limits, fees, and feature support.
     *
     * @param language Optional language code (RFC 4646, e.g., "en", "es") for localized messages
     * @param jwt Optional SEP-10 JWT token if the anchor requires authentication for /info
     * @return Sep06InfoResponse containing anchor capabilities
     * @throws Sep06ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val info = sep06.info()
     *
     * // Check deposit assets
     * info.deposit?.forEach { (code, assetInfo) ->
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
    suspend fun info(language: String? = null, jwt: String? = null): Sep06InfoResponse {
        val queryParams = mutableMapOf<String, String?>()
        language?.let { queryParams["lang"] = it }

        return httpGet(
            endpoint = "info",
            queryParams = queryParams,
            jwt = jwt
        )
    }

    /**
     * Initiates a programmatic deposit.
     *
     * A deposit occurs when a user sends an external asset (fiat via bank transfer, crypto
     * from another blockchain, etc.) to an anchor, and the anchor sends an equivalent amount
     * of the corresponding Stellar asset to the user's account.
     *
     * @param request Deposit request parameters
     * @return Sep06DepositResponse with deposit instructions and transaction ID
     * @throws Sep06AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep06CustomerInformationNeededException If additional KYC fields are required
     * @throws Sep06CustomerInformationStatusException If KYC status prevents the transaction
     * @throws Sep06InvalidRequestException On validation error
     * @throws Sep06ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep06.deposit(Sep06DepositRequest(
     *     assetCode = "USDC",
     *     account = keyPair.getAccountId(),
     *     jwt = jwtToken,
     *     amount = "100",
     *     claimableBalanceSupported = true
     * ))
     *
     * println("Transaction ID: ${response.id}")
     * response.instructions?.forEach { (field, instruction) ->
     *     println("$field: ${instruction.value}")
     * }
     * ```
     */
    suspend fun deposit(request: Sep06DepositRequest): Sep06DepositResponse {
        val queryParams = mutableMapOf<String, String?>(
            "asset_code" to request.assetCode,
            "account" to request.account,
            "asset_issuer" to request.assetIssuer,
            "memo_type" to request.memoType,
            "memo" to request.memo,
            "email_address" to request.emailAddress,
            "type" to request.type,
            "funding_method" to request.fundingMethod,
            "amount" to request.amount,
            "country_code" to request.countryCode,
            "claimable_balance_supported" to request.claimableBalanceSupported?.toString(),
            "customer_id" to request.customerId,
            "location_id" to request.locationId,
            "wallet_name" to request.walletName,
            "wallet_url" to request.walletUrl,
            "lang" to request.lang,
            "on_change_callback" to request.onChangeCallback
        )

        // Add extra fields
        request.extraFields?.forEach { (key, value) ->
            queryParams[key] = value
        }

        return httpGet(
            endpoint = "deposit",
            queryParams = queryParams,
            jwt = request.jwt
        )
    }

    /**
     * Initiates a programmatic deposit with asset conversion (SEP-38 exchange).
     *
     * A deposit exchange allows a user to send an off-chain asset to an anchor and receive
     * a different Stellar asset in return. For example, depositing EUR via bank transfer
     * and receiving USDC on Stellar.
     *
     * @param request Deposit exchange request parameters
     * @return Sep06DepositResponse with deposit instructions and transaction ID
     * @throws Sep06AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep06CustomerInformationNeededException If additional KYC fields are required
     * @throws Sep06CustomerInformationStatusException If KYC status prevents the transaction
     * @throws Sep06InvalidRequestException On validation error
     * @throws Sep06ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep06.depositExchange(Sep06DepositExchangeRequest(
     *     destinationAsset = "USDC",
     *     sourceAsset = "iso4217:EUR",
     *     amount = "100",
     *     account = keyPair.getAccountId(),
     *     jwt = jwtToken,
     *     quoteId = quote.id
     * ))
     *
     * println("Transaction ID: ${response.id}")
     * ```
     */
    suspend fun depositExchange(request: Sep06DepositExchangeRequest): Sep06DepositResponse {
        val queryParams = mutableMapOf<String, String?>(
            "destination_asset" to request.destinationAsset,
            "source_asset" to request.sourceAsset,
            "amount" to request.amount,
            "account" to request.account,
            "quote_id" to request.quoteId,
            "memo_type" to request.memoType,
            "memo" to request.memo,
            "email_address" to request.emailAddress,
            "type" to request.type,
            "funding_method" to request.fundingMethod,
            "country_code" to request.countryCode,
            "claimable_balance_supported" to request.claimableBalanceSupported?.toString(),
            "customer_id" to request.customerId,
            "location_id" to request.locationId,
            "wallet_name" to request.walletName,
            "wallet_url" to request.walletUrl,
            "lang" to request.lang,
            "on_change_callback" to request.onChangeCallback
        )

        // Add extra fields
        request.extraFields?.forEach { (key, value) ->
            queryParams[key] = value
        }

        return httpGet(
            endpoint = "deposit-exchange",
            queryParams = queryParams,
            jwt = request.jwt
        )
    }

    /**
     * Initiates a programmatic withdrawal.
     *
     * A withdrawal occurs when a user sends a Stellar asset to an anchor's account, and the
     * anchor delivers the equivalent amount in an off-chain asset (fiat to bank account,
     * crypto to external blockchain, cash pickup, etc.).
     *
     * @param request Withdrawal request parameters
     * @return Sep06WithdrawResponse with withdrawal instructions (anchor account, memo)
     * @throws Sep06AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep06CustomerInformationNeededException If additional KYC fields are required
     * @throws Sep06CustomerInformationStatusException If KYC status prevents the transaction
     * @throws Sep06InvalidRequestException On validation error
     * @throws Sep06ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep06.withdraw(Sep06WithdrawRequest(
     *     assetCode = "USDC",
     *     type = "bank_account",
     *     jwt = jwtToken,
     *     dest = "1234567890",
     *     destExtra = "021000021",
     *     amount = "100"
     * ))
     *
     * println("Send to: ${response.accountId}")
     * println("Memo: ${response.memo} (${response.memoType})")
     * ```
     */
    suspend fun withdraw(request: Sep06WithdrawRequest): Sep06WithdrawResponse {
        @Suppress("DEPRECATION")
        val queryParams = mutableMapOf<String, String?>(
            "asset_code" to request.assetCode,
            "type" to request.type,
            "funding_method" to request.fundingMethod,
            "dest" to request.dest,
            "dest_extra" to request.destExtra,
            "account" to request.account,
            "memo" to request.memo,
            "memo_type" to request.memoType,
            "amount" to request.amount,
            "country_code" to request.countryCode,
            "refund_memo" to request.refundMemo,
            "refund_memo_type" to request.refundMemoType,
            "customer_id" to request.customerId,
            "location_id" to request.locationId,
            "wallet_name" to request.walletName,
            "wallet_url" to request.walletUrl,
            "lang" to request.lang,
            "on_change_callback" to request.onChangeCallback
        )

        // Add extra fields
        request.extraFields?.forEach { (key, value) ->
            queryParams[key] = value
        }

        return httpGet(
            endpoint = "withdraw",
            queryParams = queryParams,
            jwt = request.jwt
        )
    }

    /**
     * Initiates a programmatic withdrawal with asset conversion (SEP-38 exchange).
     *
     * A withdrawal exchange allows a user to send a Stellar asset to an anchor and receive
     * a different off-chain asset in return. For example, sending USDC on Stellar and
     * receiving EUR in a bank account.
     *
     * @param request Withdrawal exchange request parameters
     * @return Sep06WithdrawResponse with withdrawal instructions (anchor account, memo)
     * @throws Sep06AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep06CustomerInformationNeededException If additional KYC fields are required
     * @throws Sep06CustomerInformationStatusException If KYC status prevents the transaction
     * @throws Sep06InvalidRequestException On validation error
     * @throws Sep06ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep06.withdrawExchange(Sep06WithdrawExchangeRequest(
     *     sourceAsset = "USDC",
     *     destinationAsset = "iso4217:EUR",
     *     amount = "100",
     *     type = "bank_account",
     *     jwt = jwtToken,
     *     quoteId = quote.id,
     *     dest = "DE89370400440532013000"
     * ))
     *
     * println("Send to: ${response.accountId}")
     * ```
     */
    suspend fun withdrawExchange(request: Sep06WithdrawExchangeRequest): Sep06WithdrawResponse {
        @Suppress("DEPRECATION")
        val queryParams = mutableMapOf<String, String?>(
            "source_asset" to request.sourceAsset,
            "destination_asset" to request.destinationAsset,
            "amount" to request.amount,
            "type" to request.type,
            "funding_method" to request.fundingMethod,
            "quote_id" to request.quoteId,
            "dest" to request.dest,
            "dest_extra" to request.destExtra,
            "account" to request.account,
            "memo" to request.memo,
            "memo_type" to request.memoType,
            "country_code" to request.countryCode,
            "refund_memo" to request.refundMemo,
            "refund_memo_type" to request.refundMemoType,
            "customer_id" to request.customerId,
            "location_id" to request.locationId,
            "wallet_name" to request.walletName,
            "wallet_url" to request.walletUrl,
            "lang" to request.lang,
            "on_change_callback" to request.onChangeCallback
        )

        // Add extra fields
        request.extraFields?.forEach { (key, value) ->
            queryParams[key] = value
        }

        return httpGet(
            endpoint = "withdraw-exchange",
            queryParams = queryParams,
            jwt = request.jwt
        )
    }

    /**
     * Gets the fee for a deposit or withdrawal operation.
     *
     * Note: This endpoint is deprecated in SEP-6. Anchors should use SEP-38 quotes
     * for fee calculation instead. This method is provided for compatibility with
     * anchors that still support the legacy fee endpoint.
     *
     * @param request Fee request parameters
     * @return Sep06FeeResponse containing the fee amount
     * @throws Sep06AuthenticationRequiredException If authentication is required but not provided
     * @throws Sep06InvalidRequestException On validation error
     * @throws Sep06ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val feeResponse = sep06.fee(Sep06FeeRequest(
     *     operation = "deposit",
     *     assetCode = "USDC",
     *     amount = "100"
     * ))
     * println("Fee: ${feeResponse.fee}")
     * ```
     */
    suspend fun fee(request: Sep06FeeRequest): Sep06FeeResponse {
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
     * Gets transaction history for the authenticated account.
     *
     * Returns a list of transactions for the specified asset, optionally filtered
     * by date, kind, and pagination parameters.
     *
     * @param request Transaction history request parameters
     * @return Sep06TransactionsResponse containing list of transactions
     * @throws Sep06AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep06ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep06.transactions(Sep06TransactionsRequest(
     *     assetCode = "USDC",
     *     account = keyPair.getAccountId(),
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
    suspend fun transactions(request: Sep06TransactionsRequest): Sep06TransactionsResponse {
        val queryParams = mutableMapOf<String, String?>(
            "asset_code" to request.assetCode,
            "account" to request.account,
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
     * @return Sep06TransactionResponse containing the transaction details
     * @throws Sep06AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep06TransactionNotFoundException If transaction not found
     * @throws Sep06ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep06.transaction(Sep06TransactionRequest(
     *     id = transactionId,
     *     jwt = jwtToken
     * ))
     *
     * val tx = response.transaction
     * println("Status: ${tx.status}")
     * println("Amount in: ${tx.amountIn}")
     * println("Amount out: ${tx.amountOut}")
     * ```
     */
    suspend fun transaction(request: Sep06TransactionRequest): Sep06TransactionResponse {
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
     * Updates a transaction with additional information requested by the anchor.
     *
     * This endpoint allows clients to provide additional information that the anchor has
     * requested after a transaction was initiated. The transaction must be in the
     * "pending_transaction_info_update" status for this request to succeed.
     *
     * The request targets `PATCH TRANSFER_SERVER/transactions/:id` and sends the supplied
     * fields wrapped in a `transaction` object, as SEP-6 specifies:
     * `{"transaction": {"dest": "...", "dest_extra": "..."}}`.
     *
     * @param request Patch transaction request with transaction ID and fields to update
     * @return HttpResponse indicating success or failure
     * @throws Sep06AuthenticationRequiredException If JWT is invalid or expired
     * @throws Sep06InvalidRequestException On validation error
     * @throws Sep06TransactionNotFoundException If transaction not found
     * @throws Sep06ServerErrorException On anchor server error
     *
     * Example:
     * ```kotlin
     * val response = sep06.patchTransaction(Sep06PatchTransactionRequest(
     *     id = transactionId,
     *     fields = mapOf(
     *         "dest" to "DE89370400440532013000",
     *         "dest_extra" to "COBADEFFXXX"
     *     ),
     *     jwt = jwtToken
     * ))
     *
     * if (response.status.value == 200) {
     *     println("Transaction updated successfully")
     * }
     * ```
     */
    suspend fun patchTransaction(request: Sep06PatchTransactionRequest): HttpResponse {
        return httpPatch(
            endpoint = "transactions/${request.id}",
            fields = request.fields,
            jwt = request.jwt
        )
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
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
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
     * Performs a PATCH request and returns the raw response.
     *
     * SEP-6 requires the updated fields to be nested under a `transaction` key, so [fields]
     * is wrapped rather than sent at the top level.
     */
    private suspend fun httpPatch(
        endpoint: String,
        fields: Map<String, String>,
        jwt: String
    ): HttpResponse = withHttpClient { client ->
        val url = buildUrl(endpoint)
        val headers = buildHeaders(jwt)
        val requestBody = JsonObject(
            mapOf("transaction" to JsonObject(fields.mapValues { (_, value) -> JsonPrimitive(value) }))
        )

        val response = client.patch(url) {
            headers.forEach { (key, value) -> header(key, value) }
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        // For PATCH, check for errors but return raw response
        val body = response.bodyAsText()
        when (response.status.value) {
            200, 201, 204 -> response
            400 -> throw Sep06InvalidRequestException(parseErrorMessage(body))
            403 -> {
                handleForbiddenResponse(body)
                throw Sep06ServerErrorException(response.status.value, parseErrorMessage(body))
            }
            404 -> throw Sep06TransactionNotFoundException()
            in 500..599 -> throw Sep06ServerErrorException(response.status.value, parseErrorMessage(body))
            else -> throw Sep06ServerErrorException(response.status.value, parseErrorMessage(body))
        }
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
                throw Sep06InvalidRequestException(parseErrorMessage(body))
            }
            403 -> {
                handleForbiddenResponse(body)
                // If handleForbiddenResponse doesn't throw, treat as generic 403
                throw Sep06ServerErrorException(403, parseErrorMessage(body))
            }
            404 -> {
                throw Sep06TransactionNotFoundException()
            }
            in 500..599 -> {
                throw Sep06ServerErrorException(response.status.value, parseErrorMessage(body))
            }
            else -> {
                throw Sep06ServerErrorException(response.status.value, parseErrorMessage(body))
            }
        }
    }

    /**
     * Handles HTTP 403 responses by parsing error type and throwing appropriate exception.
     *
     * SEP-6 defines three types of 403 responses:
     * - authentication_required: JWT token is missing or invalid
     * - non_interactive_customer_info_needed: Additional KYC fields are required
     * - customer_info_status: KYC status prevents the transaction (pending or denied)
     */
    private fun handleForbiddenResponse(body: String) {
        try {
            val jsonObj = json.decodeFromString<JsonObject>(body)
            val type = jsonObj["type"]?.jsonPrimitive?.content

            when (type) {
                "authentication_required" -> {
                    throw Sep06AuthenticationRequiredException()
                }
                "non_interactive_customer_info_needed" -> {
                    val fields = jsonObj["fields"]?.jsonArray?.map { it.jsonPrimitive.content }
                        ?: emptyList()
                    throw Sep06CustomerInformationNeededException(fields)
                }
                "customer_info_status" -> {
                    val status = jsonObj["status"]?.jsonPrimitive?.content ?: "unknown"
                    val moreInfoUrl = jsonObj["more_info_url"]?.jsonPrimitive?.content
                    val eta = jsonObj["eta"]?.jsonPrimitive?.longOrNull
                    throw Sep06CustomerInformationStatusException(status, moreInfoUrl, eta)
                }
            }
        } catch (e: Sep06Exception) {
            throw e
        } catch (e: Exception) {
            // If parsing fails, let the caller handle it as a generic 403
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
}
