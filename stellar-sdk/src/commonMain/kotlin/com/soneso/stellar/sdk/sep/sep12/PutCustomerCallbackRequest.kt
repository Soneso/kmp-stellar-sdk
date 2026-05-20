// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep12

/**
 * Request for registering a callback URL to receive KYC status updates from a SEP-12 anchor.
 *
 * Allows clients to receive webhook notifications when customer KYC status changes.
 * The anchor will POST updates to the provided URL. This replaces any previously
 * registered callback URL for the account.
 *
 * Callback payload format:
 * - The anchor will POST JSON with customer status updates
 * - Callbacks include Signature and X-Stellar-Signature headers for verification
 * - Verify signatures using [com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier]
 *
 * Customer identification:
 * - Use [id] if you have a customer ID from previous registration
 * - Use JWT sub value for account identification (recommended)
 * - Use [account] and [memo] for backwards compatibility
 *
 * Example - Register callback for customer:
 * ```kotlin
 * val request = PutCustomerCallbackRequest(
 *     jwt = authToken,
 *     url = "https://myapp.com/webhooks/kyc-status",
 *     id = customerId
 * )
 *
 * kycService.putCustomerCallback(request)
 * println("Callback registered, will receive updates at ${request.url}")
 * ```
 *
 * Example - Callback for shared account:
 * ```kotlin
 * val request = PutCustomerCallbackRequest(
 *     jwt = authToken,
 *     url = "https://myapp.com/webhooks/kyc-status",
 *     account = sharedAccountId,
 *     memo = "user_12345"
 * )
 *
 * kycService.putCustomerCallback(request)
 * ```
 *
 * Example - Handle callback in server:
 * ```kotlin
 * // Your webhook endpoint
 * post("/webhooks/kyc-status") {
 *     val signatureHeader = call.request.header("Signature")
 *     val xStellarSignatureHeader = call.request.header("X-Stellar-Signature")
 *     val requestBody = call.receiveText()
 *
 *     val verifier = com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier(
 *         signingKey = anchorPublicKey,
 *         registeredCallbackUrl = "https://myapp.com/webhooks/kyc-status",
 *     )
 *
 *     val result = verifier.verify(signatureHeader, xStellarSignatureHeader, requestBody)
 *     if (result is com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier.Result.Valid) {
 *         val update = Json.decodeFromString<GetCustomerInfoResponse>(requestBody)
 *         handleStatusUpdate(update)
 *         call.respond(HttpStatusCode.OK)
 *     } else {
 *         call.respond(HttpStatusCode.Unauthorized, "Invalid signature")
 *     }
 * }
 * ```
 *
 * Security considerations:
 * - Always verify callback signatures using [com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier]
 * - Use HTTPS for callback URLs to protect customer data in transit
 * - Implement request authentication on your webhook endpoint
 * - Consider rate limiting to prevent abuse
 *
 * See also:
 * - [com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier] for verifying callback authenticity
 * - [GetCustomerInfoResponse] for callback payload format
 * - [SEP-0012 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0012.md#customer-callback-put)
 *
 * @property jwt JWT token from SEP-10 or SEP-45 authentication
 * @property url Callback URL that will receive status update POSTs
 * @property id Customer ID from previous PUT request (optional)
 * @property account Stellar account ID - deprecated, use JWT sub value instead (optional)
 * @property memo Memo for shared accounts (optional)
 */
data class PutCustomerCallbackRequest(
    val jwt: String,

    val url: String,

    val id: String? = null,

    @Deprecated("Use JWT sub value instead. Maintained for backwards compatibility only.")
    val account: String? = null,

    val memo: String? = null
)
