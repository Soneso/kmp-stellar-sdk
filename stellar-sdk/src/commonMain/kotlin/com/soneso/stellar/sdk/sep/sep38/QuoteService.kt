// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep38

import com.soneso.stellar.sdk.HttpTimeouts
import com.soneso.stellar.sdk.sep.sep01.StellarToml
import com.soneso.stellar.sdk.sep.sep38.exceptions.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * SEP-38 Anchor RFQ (Request for Quote) API client.
 *
 * Implements the client side of SEP-38 for requesting price quotes for asset exchanges
 * between on-chain and off-chain assets. This service allows wallets and applications to:
 * - Discover supported assets and delivery methods
 * - Request indicative prices for asset pairs (non-binding)
 * - Request firm quotes (binding commitments)
 * - Retrieve previously created quotes
 *
 * SEP-38 enables anchors to accept any Stellar asset in exchange for off-chain assets
 * (fiat currencies, commodities, etc.) without requiring one-for-one reserve-backed
 * Stellar assets. This reduces liquidity fragmentation on the DEX and provides greater
 * flexibility for deposit/withdrawal operations.
 *
 * Quote types:
 * - Indicative prices: Non-binding price information via GET /prices and GET /price
 * - Firm quotes: Binding commitments via POST /quote that reserve liquidity until expiration
 *
 * Typical workflow:
 * 1. Call [info] to discover available assets and delivery methods
 * 2. Call [prices] to get indicative prices for multiple asset pairs (optional)
 * 3. Call [price] to get indicative price for a specific amount (optional)
 * 4. Call [postQuote] to request a firm quote with binding commitment
 * 5. Execute trade via SEP-6, SEP-24, or SEP-31 using the quote ID
 * 6. Call [getQuote] to check quote status (optional)
 *
 * Authentication:
 * - Optional for [info], [prices], and [price] endpoints (may provide personalized results)
 * - Required for [postQuote] and [getQuote] endpoints (must use SEP-10 JWT token)
 *
 * Asset format:
 * - Stellar assets: "stellar:CODE:ISSUER" (e.g., "stellar:USDC:GA5...ZVN")
 * - Fiat currencies: "iso4217:USD" (ISO 4217 three-character code)
 *
 * Example - Complete quote workflow:
 * ```kotlin
 * // 1. Initialize from domain
 * val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
 *
 * // 2. Discover available assets
 * val info = quoteService.info()
 * info.assets.forEach { asset ->
 *     println("Asset: ${asset.asset}")
 *     asset.sellDeliveryMethods?.forEach { method ->
 *         println("  Sell via: ${method.name}")
 *     }
 * }
 *
 * // 3. Get indicative prices (optional)
 * val pricesResponse = quoteService.prices(
 *     sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
 *     sellAmount = "100"
 * )
 * pricesResponse.buyAssets?.forEach { buyAsset ->
 *     println("Can buy ${buyAsset.asset} at price ${buyAsset.price}")
 * }
 *
 * // 4. Get specific indicative price (optional)
 * val priceResponse = quoteService.price(
 *     context = "sep6",
 *     sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
 *     buyAsset = "iso4217:BRL",
 *     sellAmount = "100",
 *     buyDeliveryMethod = "PIX"
 * )
 * println("Indicative price: ${priceResponse.price}")
 * println("Total with fees: ${priceResponse.totalPrice}")
 *
 * // 5. Request firm quote (requires authentication)
 * val jwtToken = obtainSep10Token() // Get JWT via SEP-10 WebAuth
 *
 * val quoteRequest = Sep38QuoteRequest(
 *     context = "sep6",
 *     sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
 *     buyAsset = "iso4217:BRL",
 *     sellAmount = "100",
 *     buyDeliveryMethod = "PIX",
 *     countryCode = "BR"
 * )
 *
 * val quote = quoteService.postQuote(quoteRequest, jwtToken)
 * println("Quote ID: ${quote.id}")
 * println("Expires at: ${quote.expiresAt}")
 * println("Guaranteed rate: ${quote.price}")
 *
 * // 6. Retrieve quote later (optional)
 * val retrievedQuote = quoteService.getQuote(quote.id, jwtToken)
 * println("Quote status: ${retrievedQuote.expiresAt}")
 * ```
 *
 * Example - Error handling:
 * ```kotlin
 * try {
 *     val quote = quoteService.postQuote(request, jwtToken)
 * } catch (e: Sep38BadRequestException) {
 *     println("Invalid request: ${e.error}")
 * } catch (e: Sep38PermissionDeniedException) {
 *     println("Authentication required or expired: ${e.error}")
 * } catch (e: Sep38NotFoundException) {
 *     println("Quote not found: ${e.error}")
 * } catch (e: Sep38UnknownResponseException) {
 *     println("Unexpected error (${e.statusCode}): ${e.responseBody}")
 * }
 * ```
 *
 * See also:
 * - [fromDomain] for automatic configuration from stellar.toml
 * - [Sep38QuoteRequest] for firm quote request parameters
 * - [Sep38QuoteResponse] for firm quote response structure
 * - [Sep38PriceResponse] for indicative price structure
 * - [SEP-0038 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0038.md)
 *
 * @property serviceAddress The base URL of the anchor's quote server
 * @property httpClient Optional custom HTTP client for testing
 * @property httpRequestHeaders Optional custom headers for all requests
 */
class QuoteService(
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
         * Creates a QuoteService by discovering the endpoint from stellar.toml.
         *
         * Fetches the stellar.toml file from the specified domain and extracts the
         * ANCHOR_QUOTE_SERVER address. This is the recommended method for initializing
         * the service as it automatically configures the correct endpoint.
         *
         * @param domain The domain name hosting the stellar.toml file
         * @param httpClient Optional custom HTTP client for testing
         * @param httpRequestHeaders Optional custom headers for requests
         * @return QuoteService configured with the domain's quote server endpoint
         * @throws IllegalStateException If ANCHOR_QUOTE_SERVER is not found in stellar.toml
         *
         * Example:
         * ```kotlin
         * val quoteService = QuoteService.fromDomain("testanchor.stellar.org")
         * ```
         */
        suspend fun fromDomain(
            domain: String,
            httpClient: HttpClient? = null,
            httpRequestHeaders: Map<String, String>? = null
        ): QuoteService {
            val stellarToml = StellarToml.fromDomain(
                domain = domain,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )

            val address = stellarToml.generalInformation.anchorQuoteServer
                ?: throw IllegalStateException(
                    "ANCHOR_QUOTE_SERVER not found in stellar.toml for domain: $domain"
                )

            return QuoteService(
                serviceAddress = address,
                httpClient = httpClient,
                httpRequestHeaders = httpRequestHeaders
            )
        }
    }

    /**
     * Retrieves supported assets and delivery methods available for trading.
     *
     * Returns comprehensive information about all assets supported by the anchor
     * for exchange operations, including available delivery methods and country
     * restrictions. This is typically the first call made to discover what exchanges
     * are possible.
     *
     * Note: Not all asset pairs may be supported. Use the [prices] endpoint to see
     * which specific pairs are available for trading.
     *
     * @param jwtToken Optional JWT token from SEP-10 authentication (may provide personalized results)
     * @return Sep38InfoResponse containing available assets and options
     * @throws Sep38BadRequestException If request parameters are invalid (400)
     * @throws Sep38UnknownResponseException For unexpected HTTP status codes
     *
     * Example:
     * ```kotlin
     * val infoResponse = quoteService.info()
     *
     * infoResponse.assets.forEach { asset ->
     *     println("Asset: ${asset.asset}")
     *
     *     asset.countryCodes?.let { codes ->
     *         println("  Available in: ${codes.joinToString(", ")}")
     *     }
     *
     *     asset.sellDeliveryMethods?.forEach { method ->
     *         println("  Sell via: ${method.name} - ${method.description}")
     *     }
     *
     *     asset.buyDeliveryMethods?.forEach { method ->
     *         println("  Buy via: ${method.name} - ${method.description}")
     *     }
     * }
     * ```
     */
    suspend fun info(jwtToken: String? = null): Sep38InfoResponse {
        return withHttpClient { client ->
            val url = "$serviceAddress/info"

            val response = client.get(url) {
                jwtToken?.let { header("Authorization", "Bearer $it") }
                applyCustomHeaders()
            }

            handleResponse(response) { body ->
                json.decodeFromString<Sep38InfoResponse>(body)
            }
        }
    }

    /**
     * Fetches indicative prices for available assets given a base asset and amount.
     *
     * Returns non-binding price information for multiple asset pairs. Either [sellAsset]
     * or [buyAsset] must be provided, but not both. The corresponding amount parameter
     * ([sellAmount] or [buyAmount]) is also required.
     *
     * When [sellAsset] is provided, the response contains [Sep38PricesResponse.buyAssets].
     * When [buyAsset] is provided, the response contains [Sep38PricesResponse.sellAssets].
     *
     * These prices are indicative and non-binding. For binding commitments, use [postQuote].
     *
     * @param sellAsset Asset to sell using Asset Identification Format (mutually exclusive with buyAsset)
     * @param buyAsset Asset to buy using Asset Identification Format (mutually exclusive with sellAsset)
     * @param sellAmount Amount of sellAsset to exchange (required when sellAsset is provided)
     * @param buyAmount Amount of buyAsset to receive (required when buyAsset is provided)
     * @param sellDeliveryMethod Optional delivery method name from info endpoint
     * @param buyDeliveryMethod Optional delivery method name from info endpoint
     * @param countryCode Optional ISO 3166-1 alpha-2 or ISO 3166-2 country code
     * @param jwtToken Optional JWT token from SEP-10 authentication
     * @return Sep38PricesResponse containing prices for available assets
     * @throws IllegalArgumentException If neither or both sellAsset and buyAsset are provided
     * @throws Sep38BadRequestException If request parameters are invalid (400)
     * @throws Sep38UnknownResponseException For unexpected HTTP status codes
     *
     * Example - Get buy options (selling USDC):
     * ```kotlin
     * val pricesResponse = quoteService.prices(
     *     sellAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
     *     sellAmount = "100",
     *     sellDeliveryMethod = null,
     *     buyDeliveryMethod = "PIX",
     *     countryCode = "BR"
     * )
     *
     * pricesResponse.buyAssets?.forEach { buyAsset ->
     *     println("Buy ${buyAsset.asset} at price ${buyAsset.price}")
     * }
     * ```
     *
     * Example - Get sell options (buying USDC):
     * ```kotlin
     * val pricesResponse = quoteService.prices(
     *     buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
     *     buyAmount = "100",
     *     sellDeliveryMethod = "PIX",
     *     countryCode = "BR"
     * )
     *
     * pricesResponse.sellAssets?.forEach { sellAsset ->
     *     println("Sell ${sellAsset.asset} at price ${sellAsset.price}")
     * }
     * ```
     */
    suspend fun prices(
        sellAsset: String? = null,
        buyAsset: String? = null,
        sellAmount: String? = null,
        buyAmount: String? = null,
        sellDeliveryMethod: String? = null,
        buyDeliveryMethod: String? = null,
        countryCode: String? = null,
        jwtToken: String? = null
    ): Sep38PricesResponse {
        // Validate exactly one of sellAsset or buyAsset
        if ((sellAsset != null && buyAsset != null) || (sellAsset == null && buyAsset == null)) {
            throw IllegalArgumentException(
                "Must provide either sellAsset or buyAsset, but not both"
            )
        }

        return withHttpClient { client ->
            val url = buildUrl("$serviceAddress/prices") {
                sellAsset?.let { parameters.append("sell_asset", it) }
                buyAsset?.let { parameters.append("buy_asset", it) }
                sellAmount?.let { parameters.append("sell_amount", it) }
                buyAmount?.let { parameters.append("buy_amount", it) }
                sellDeliveryMethod?.let { parameters.append("sell_delivery_method", it) }
                buyDeliveryMethod?.let { parameters.append("buy_delivery_method", it) }
                countryCode?.let { parameters.append("country_code", it) }
            }

            val response = client.get(url) {
                jwtToken?.let { header("Authorization", "Bearer $it") }
                applyCustomHeaders()
            }

            handleResponse(response) { body ->
                json.decodeFromString<Sep38PricesResponse>(body)
            }
        }
    }

    /**
     * Fetches an indicative price for a specific asset pair and amount.
     *
     * Returns a non-binding price quote for exchanging one asset for another.
     * Either [sellAmount] or [buyAmount] must be provided, but not both.
     *
     * This price is indicative and non-binding. The actual price will be calculated
     * at conversion time once the anchor receives funds. For binding commitments,
     * use [postQuote] to request a firm quote.
     *
     * @param context Context for quote usage ("sep6", "sep24", or "sep31")
     * @param sellAsset Asset to sell (e.g., "stellar:USDC:G...", "iso4217:USD")
     * @param buyAsset Asset to buy
     * @param sellAmount Optional amount of sellAsset to exchange (mutually exclusive with buyAmount)
     * @param buyAmount Optional amount of buyAsset to receive (mutually exclusive with sellAmount)
     * @param sellDeliveryMethod Optional delivery method name from info endpoint
     * @param buyDeliveryMethod Optional delivery method name from info endpoint
     * @param countryCode Optional ISO 3166-1 alpha-2 or ISO 3166-2 country code
     * @param jwtToken Optional JWT token from SEP-10 authentication
     * @return Sep38PriceResponse containing price details and fees
     * @throws IllegalArgumentException If both or neither amount parameters are provided
     * @throws Sep38BadRequestException If request parameters are invalid (400)
     * @throws Sep38UnknownResponseException For unexpected HTTP status codes
     *
     * Example:
     * ```kotlin
     * val priceResponse = quoteService.price(
     *     context = "sep6",
     *     sellAsset = "iso4217:BRL",
     *     buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
     *     sellAmount = "542",
     *     sellDeliveryMethod = "PIX",
     *     countryCode = "BR"
     * )
     *
     * println("Exchange rate: ${priceResponse.price} BRL per USDC")
     * println("Effective rate (with fees): ${priceResponse.totalPrice} BRL per USDC")
     * println("You'll receive: ${priceResponse.buyAmount} USDC")
     * println("Total cost: ${priceResponse.sellAmount} BRL")
     * println("Fee: ${priceResponse.fee.total} ${priceResponse.fee.asset}")
     * ```
     */
    suspend fun price(
        context: String,
        sellAsset: String,
        buyAsset: String,
        sellAmount: String? = null,
        buyAmount: String? = null,
        sellDeliveryMethod: String? = null,
        buyDeliveryMethod: String? = null,
        countryCode: String? = null,
        jwtToken: String? = null
    ): Sep38PriceResponse {
        // Validate exactly one of sellAmount or buyAmount
        if ((sellAmount != null && buyAmount != null) || (sellAmount == null && buyAmount == null)) {
            throw IllegalArgumentException(
                "Must provide either sellAmount or buyAmount, but not both"
            )
        }

        return withHttpClient { client ->
            val url = buildUrl("$serviceAddress/price") {
                parameters.append("context", context)
                parameters.append("sell_asset", sellAsset)
                parameters.append("buy_asset", buyAsset)
                sellAmount?.let { parameters.append("sell_amount", it) }
                buyAmount?.let { parameters.append("buy_amount", it) }
                sellDeliveryMethod?.let { parameters.append("sell_delivery_method", it) }
                buyDeliveryMethod?.let { parameters.append("buy_delivery_method", it) }
                countryCode?.let { parameters.append("country_code", it) }
            }

            val response = client.get(url) {
                jwtToken?.let { header("Authorization", "Bearer $it") }
                applyCustomHeaders()
            }

            handleResponse(response) { body ->
                json.decodeFromString<Sep38PriceResponse>(body)
            }
        }
    }

    /**
     * Requests a firm quote with binding commitment from the anchor.
     *
     * Creates a quote that the anchor is committed to honor until expiration.
     * Unlike indicative prices, firm quotes reserve liquidity at the quoted rate.
     * The returned quote ID can be used with SEP-6, SEP-24, or SEP-31 transactions
     * to execute the exchange at the guaranteed rate.
     *
     * The request must provide either [Sep38QuoteRequest.sellAmount] or
     * [Sep38QuoteRequest.buyAmount], but not both. This is validated before sending
     * the request.
     *
     * Authentication is required. The JWT token must be obtained via SEP-10 WebAuth
     * and the user must be properly KYC'ed (via SEP-12 or other mechanism) to prevent
     * abuse of the quote system.
     *
     * @param request Quote request parameters
     * @param jwtToken Required JWT token from SEP-10 authentication
     * @return Sep38QuoteResponse containing firm quote with unique ID
     * @throws IllegalArgumentException If both or neither amount parameters are provided in request
     * @throws Sep38BadRequestException If request parameters are invalid (400)
     * @throws Sep38PermissionDeniedException If authentication is missing or invalid (403)
     * @throws Sep38UnknownResponseException For unexpected HTTP status codes
     *
     * Example:
     * ```kotlin
     * val quoteRequest = Sep38QuoteRequest(
     *     context = "sep6",
     *     sellAsset = "iso4217:BRL",
     *     buyAsset = "stellar:USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
     *     sellAmount = "542",
     *     sellDeliveryMethod = "PIX",
     *     countryCode = "BR"
     * )
     *
     * val quote = quoteService.postQuote(quoteRequest, jwtToken)
     *
     * println("Quote ID: ${quote.id}")
     * println("Valid until: ${quote.expiresAt}")
     * println("Guaranteed exchange rate: ${quote.price}")
     * println("Total with fees: ${quote.totalPrice}")
     *
     * // Use quote.id in SEP-6/24/31 transaction
     * ```
     */
    suspend fun postQuote(request: Sep38QuoteRequest, jwtToken: String): Sep38QuoteResponse {
        // Validate exactly one of sellAmount or buyAmount
        if ((request.sellAmount != null && request.buyAmount != null) ||
            (request.sellAmount == null && request.buyAmount == null)) {
            throw IllegalArgumentException(
                "Must provide either sellAmount or buyAmount in request, but not both"
            )
        }

        return withHttpClient { client ->
            val url = "$serviceAddress/quote"

            val response = client.post(url) {
                header("Authorization", "Bearer $jwtToken")
                contentType(ContentType.Application.Json)
                applyCustomHeaders()
                setBody(request)
            }

            handleResponse(response) { body ->
                json.decodeFromString<Sep38QuoteResponse>(body)
            }
        }
    }

    /**
     * Retrieves a previously created firm quote by ID.
     *
     * Fetches the current state and details of a firm quote that was created
     * via [postQuote]. Quotes must remain available past their expiration time
     * to allow clients to verify quote details after execution.
     *
     * This endpoint is useful for:
     * - Checking quote expiration before initiating a transfer
     * - Verifying quote details during transaction flow
     * - Retrieving quote information for transaction reconciliation
     *
     * Authentication is required. The JWT token must be from the same user who
     * created the quote.
     *
     * @param id Unique identifier of the quote to retrieve
     * @param jwtToken Required JWT token from SEP-10 authentication
     * @return Sep38QuoteResponse containing quote details
     * @throws Sep38BadRequestException If request parameters are invalid (400)
     * @throws Sep38PermissionDeniedException If authentication is missing or invalid (403)
     * @throws Sep38NotFoundException If quote ID is not found (404)
     * @throws Sep38UnknownResponseException For unexpected HTTP status codes
     *
     * Example:
     * ```kotlin
     * val quoteId = "de762cda-a193-4961-861e-57b31fed6eb3"
     * val quote = quoteService.getQuote(quoteId, jwtToken)
     *
     * // Check if quote is still valid
     * val expiresAt = Instant.parse(quote.expiresAt)
     * val isExpired = Instant.now() > expiresAt
     *
     * if (isExpired) {
     *     println("Quote expired at ${quote.expiresAt}")
     *     // Request new quote
     * } else {
     *     println("Quote valid for ${Duration.between(Instant.now(), expiresAt).toMinutes()} more minutes")
     *     // Proceed with transaction
     * }
     * ```
     */
    suspend fun getQuote(id: String, jwtToken: String): Sep38QuoteResponse {
        return withHttpClient { client ->
            val url = "$serviceAddress/quote/$id"

            val response = client.get(url) {
                header("Authorization", "Bearer $jwtToken")
                applyCustomHeaders()
            }

            handleResponse(response) { body ->
                json.decodeFromString<Sep38QuoteResponse>(body)
            }
        }
    }

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
     * Builds a URL with query parameters.
     */
    private fun buildUrl(baseUrl: String, block: URLBuilder.() -> Unit = {}): String {
        return URLBuilder(baseUrl).apply(block).buildString()
    }

    /**
     * Applies custom HTTP headers to a request.
     */
    private fun HttpRequestBuilder.applyCustomHeaders() {
        httpRequestHeaders?.forEach { (key, value) ->
            header(key, value)
        }
    }

    /**
     * Handles HTTP response and maps errors to SEP-38 exceptions.
     */
    private suspend fun <T> handleResponse(
        response: HttpResponse,
        parser: suspend (String) -> T
    ): T {
        val statusCode = response.status.value
        val body = response.bodyAsText()

        return when (statusCode) {
            200, 201 -> parser(body)  // 200 OK or 201 Created
            400 -> {
                val errorMessage = extractErrorMessage(body)
                throw Sep38BadRequestException(errorMessage)
            }
            403 -> {
                val errorMessage = extractErrorMessage(body)
                throw Sep38PermissionDeniedException(errorMessage)
            }
            404 -> {
                val errorMessage = extractErrorMessage(body)
                throw Sep38NotFoundException(errorMessage)
            }
            else -> throw Sep38UnknownResponseException(
                statusCode = statusCode,
                responseBody = body
            )
        }
    }

    /**
     * Extracts error message from response body.
     */
    private fun extractErrorMessage(body: String): String {
        return try {
            val errorJson = json.decodeFromString<Map<String, String>>(body)
            errorJson["error"] ?: body
        } catch (e: Exception) {
            body
        }
    }
}
