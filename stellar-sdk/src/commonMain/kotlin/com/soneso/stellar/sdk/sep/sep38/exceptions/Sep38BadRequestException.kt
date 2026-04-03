// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep38.exceptions

/**
 * Exception thrown when request parameters are invalid (HTTP 400).
 *
 * Indicates that the anchor rejected the request due to invalid, malformed, or missing
 * parameters. This error typically occurs when:
 * - Invalid asset format (must be "stellar:CODE:ISSUER" or "iso4217:USD")
 * - Both sellAmount and buyAmount provided (must provide only one)
 * - Missing required parameters (context, sellAsset, buyAsset)
 * - Invalid delivery method (not from info endpoint)
 * - Invalid country code (must be ISO 3166-1 alpha-2 or ISO 3166-2)
 * - Invalid amount format (must be positive decimal string)
 *
 * Recovery actions:
 * - Verify asset identifiers use correct format
 * - Ensure only one of sellAmount or buyAmount is provided
 * - Check delivery methods match those from info endpoint
 * - Validate country codes are ISO 3166 compliant
 * - Verify amounts are valid positive decimals
 *
 * Example - Handle invalid asset format:
 * ```kotlin
 * suspend fun getPriceWithValidation(
 *     quoteService: Sep38QuoteService,
 *     sellAsset: String,
 *     buyAsset: String,
 *     sellAmount: String
 * ): Sep38PriceResponse? {
 *     try {
 *         return quoteService.price(
 *             context = "sep6",
 *             sellAsset = sellAsset,
 *             buyAsset = buyAsset,
 *             sellAmount = sellAmount
 *         )
 *     } catch (e: Sep38BadRequestException) {
 *         println("Invalid request: ${e.message}")
 *
 *         // Validate asset format
 *         if (!sellAsset.startsWith("stellar:") && !sellAsset.startsWith("iso4217:")) {
 *             println("Invalid sell asset format. Use 'stellar:CODE:ISSUER' or 'iso4217:USD'")
 *         }
 *
 *         return null
 *     }
 * }
 * ```
 *
 * Example - Validate before request:
 * ```kotlin
 * fun validateQuoteRequest(request: Sep38PostQuoteRequest): String? {
 *     // Check asset format
 *     val assetPattern = Regex("^(stellar:[a-zA-Z0-9]{1,12}:[A-Z0-9]{56}|iso4217:[A-Z]{3})$")
 *     if (!request.sellAsset.matches(assetPattern)) {
 *         return "Invalid sellAsset format: ${request.sellAsset}"
 *     }
 *     if (!request.buyAsset.matches(assetPattern)) {
 *         return "Invalid buyAsset format: ${request.buyAsset}"
 *     }
 *
 *     // Check amount constraints
 *     if (request.sellAmount != null && request.buyAmount != null) {
 *         return "Provide either sellAmount or buyAmount, not both"
 *     }
 *     if (request.sellAmount == null && request.buyAmount == null) {
 *         return "Must provide either sellAmount or buyAmount"
 *     }
 *
 *     // Check amount is positive
 *     val amount = request.sellAmount ?: request.buyAmount
 *     if (amount != null) {
 *         val amountValue = amount.toDoubleOrNull()
 *         if (amountValue == null || amountValue <= 0) {
 *             return "Amount must be a positive decimal: $amount"
 *         }
 *     }
 *
 *     return null // Valid
 * }
 *
 * suspend fun createQuoteWithValidation(
 *     quoteService: Sep38QuoteService,
 *     request: Sep38PostQuoteRequest,
 *     jwt: String
 * ): Sep38QuoteResponse? {
 *     // Validate before sending
 *     val error = validateQuoteRequest(request)
 *     if (error != null) {
 *         println("Validation failed: $error")
 *         return null
 *     }
 *
 *     try {
 *         return quoteService.postQuote(request, jwt)
 *     } catch (e: Sep38BadRequestException) {
 *         println("Server rejected request: ${e.message}")
 *         return null
 *     }
 * }
 * ```
 *
 * Example - Handle delivery method errors:
 * ```kotlin
 * suspend fun getAvailableDeliveryMethods(
 *     quoteService: Sep38QuoteService,
 *     asset: String
 * ): List<String>? {
 *     try {
 *         val info = quoteService.info()
 *         val assetInfo = info.assets.find { it.asset == asset }
 *         return assetInfo?.sellDeliveryMethods?.map { it.name }
 *     } catch (e: Sep38BadRequestException) {
 *         println("Failed to get delivery methods: ${e.message}")
 *         return null
 *     }
 * }
 *
 * suspend fun getPriceWithDeliveryMethod(
 *     quoteService: Sep38QuoteService,
 *     sellAsset: String,
 *     buyAsset: String,
 *     sellAmount: String,
 *     deliveryMethod: String?
 * ): Sep38PriceResponse? {
 *     // Validate delivery method if provided
 *     if (deliveryMethod != null) {
 *         val availableMethods = getAvailableDeliveryMethods(quoteService, sellAsset)
 *         if (availableMethods != null && deliveryMethod !in availableMethods) {
 *             println("Invalid delivery method: $deliveryMethod")
 *             println("Available methods: ${availableMethods.joinToString()}")
 *             return null
 *         }
 *     }
 *
 *     try {
 *         return quoteService.price(
 *             context = "sep6",
 *             sellAsset = sellAsset,
 *             buyAsset = buyAsset,
 *             sellAmount = sellAmount,
 *             sellDeliveryMethod = deliveryMethod
 *         )
 *     } catch (e: Sep38BadRequestException) {
 *         println("Invalid request: ${e.message}")
 *         return null
 *     }
 * }
 * ```
 *
 * See also:
 * - [Sep38Exception] base class
 * - [Sep38QuoteService] for quote API operations
 * - [SEP-0038 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0038.md)
 *
 * @property error Error message from the anchor
 */
class Sep38BadRequestException(
    val error: String
) : Sep38Exception(
    message = "Bad request (400). $error"
) {
    override fun toString(): String {
        return "SEP-38 bad request - error: $error"
    }
}
