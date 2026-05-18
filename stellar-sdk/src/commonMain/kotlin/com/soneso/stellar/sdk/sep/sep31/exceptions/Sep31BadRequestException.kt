// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Exception thrown when the Receiving Anchor returns HTTP 400 Bad Request.
 *
 * Indicates that the request was malformed or contained invalid parameters.
 * The two domain-specific 400 subcases — `customer_info_needed` and
 * `transaction_info_needed` — are dispatched to [Sep31CustomerInfoNeededException]
 * and [Sep31TransactionInfoNeededException] respectively; this generic class covers
 * every other 400 response.
 *
 * Common causes:
 * - Required request fields are missing
 * - Request body JSON is malformed
 * - Amount falls outside the asset's min/max limits
 * - Unsupported asset or destination asset
 * - Invalid or expired SEP-38 `quote_id`
 * - `sender_id` or `receiver_id` references an unknown SEP-12 customer
 *
 * Recovery actions:
 * - Inspect [message] for the sanitized error returned by the anchor
 * - Verify all required request parameters per the SEP-31 spec
 * - Re-fetch the asset configuration via `GET /info` to confirm limits and supported funding methods
 *
 * Example - Handle bad request:
 * ```kotlin
 * try {
 *     val response = sep31Service.postTransactions(request, jwt)
 * } catch (e: Sep31BadRequestException) {
 *     println("Invalid request (HTTP ${e.statusCode}): ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep31Exception] base class
 * - [SEP-0031 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md)
 *
 * @property statusCode The HTTP status code returned by the Receiving Anchor (always 400 for this exception).
 * @property rawResponseBody Anchor response body preserved for local debugging only.
 *   Identical to the sanitized form used in [message] except that JWT-shaped substrings
 *   are NOT replaced with `<redacted-jwt>`. May therefore contain bearer tokens.
 *   Truncated and stripped of control characters, so this field is safe
 *   against log-injection — but it is NOT safe to ship to shared log aggregators
 *   (Sentry, Datadog, Splunk) in production. Use to debug anchors that echo tokens or
 *   other sensitive context in error responses. For production logging, use [message]
 *   instead. `null` when the SDK had no response body to capture for this error path.
 * @param message Sanitized error message extracted from the anchor response.
 */
public class Sep31BadRequestException(
    message: String,
    public val statusCode: Int = 400,
    public val rawResponseBody: String? = null,
) : Sep31Exception(message) {
    override fun toString(): String {
        return "SEP-31 bad request (HTTP $statusCode): $message"
    }
}
