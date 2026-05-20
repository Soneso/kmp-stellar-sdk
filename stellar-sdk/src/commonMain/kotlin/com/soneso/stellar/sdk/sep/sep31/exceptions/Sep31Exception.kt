// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Base exception class for SEP-31 Cross-Border Payments errors.
 *
 * All SEP-31-specific exceptions extend this class to enable unified error handling
 * while providing specific error types for different failure scenarios.
 *
 * This exception hierarchy maps directly to HTTP status codes returned by Receiving
 * Anchor direct payment servers, allowing applications to handle errors at different
 * levels:
 * - Catch [Sep31Exception] for general SEP-31 error handling
 * - Catch specific subclasses for precise error recovery
 *
 * Common error scenarios:
 * - [Sep31BadRequestException]: Receiving Anchor returned HTTP 400 (invalid request parameters)
 * - [Sep31UnauthorizedException]: Receiving Anchor returned HTTP 401 (missing or invalid JWT)
 * - [Sep31ForbiddenException]: Receiving Anchor returned HTTP 403 (authentication failure per spec)
 * - [Sep31CustomerInfoNeededException]: HTTP 400 with `error="customer_info_needed"` (SEP-12 KYC required)
 * - [Sep31TransactionInfoNeededException]: HTTP 400 with `error="transaction_info_needed"` (legacy fields path)
 * - [Sep31TransactionNotFoundException]: HTTP 404 when fetching or patching a transaction
 * - [Sep31TransactionCallbackNotSupportedException]: HTTP 404 on `PUT /transactions/:id/callback`
 * - [Sep31InvalidResponseException]: HTTP 2xx with malformed body
 * - [Sep31UnknownResponseException]: Receiving Anchor returned an unexpected HTTP status code
 * - [Sep31ConfigurationException]: `fromDomain` cannot resolve a valid `DIRECT_PAYMENT_SERVER`
 *
 * Example - General error handling:
 * ```kotlin
 * try {
 *     val transaction = sep31Service.postTransactions(request, jwt)
 * } catch (e: Sep31UnauthorizedException) {
 *     println("Authentication failed: ${e.message}")
 * } catch (e: Sep31Exception) {
 *     println("SEP-31 error: ${e.message}")
 * }
 * ```
 *
 * ## rawResponseBody convention
 *
 * Every subclass that surfaces anchor response content also exposes a `rawResponseBody:
 * String?` property. The convention is identical across subclasses and is documented
 * here once so individual subclass KDoc can stay short:
 *
 * - Same sanitization pipeline as [message] except JWT-shaped substrings are **not**
 *   redacted. May therefore contain bearer tokens.
 * - Truncated to 1024 chars and stripped of control characters, so the field is safe
 *   against log injection.
 * - **Not** safe to ship to shared log aggregators (Sentry, Datadog, Splunk) in
 *   production — use [message] for production logging.
 * - Intended for local debugging only — e.g. inspecting an anchor that echoes the
 *   rejected JWT in its error response.
 * - `null` when the SDK had no response body to capture for that error path
 *   (for example, a content-type rejection that fails before any body bytes are read).
 *
 * See also:
 * - [SEP-0031 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md)
 *
 * @property message Human-readable error description
 * @property cause Optional underlying cause of the error
 */
public open class Sep31Exception(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    override fun toString(): String {
        return "SEP-31 error: $message"
    }
}
