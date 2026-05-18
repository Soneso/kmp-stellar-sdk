// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Exception thrown when the Receiving Anchor returns a malformed response.
 *
 * Raised when the anchor responded with an otherwise-successful HTTP status (200 or
 * 201) but the response body could not be parsed or was missing fields the SEP-31
 * spec marks as required. Common triggers:
 * - The response body is not syntactically valid JSON
 * - The response Content-Type is neither `application/json` nor `application/problem+json`
 * - A required field is absent (for example, `id` on `Sep31PostTransactionsResponse`)
 * - The `GET /transactions/:id` response is not wrapped in the spec-mandated `{"transaction": {...}}` envelope
 * - The response exceeds the SDK's size cap (2 MB for `/info`, 256 KB for transaction responses)
 *
 * The [statusCode] defaults to 200 because this exception only fires on a syntactically
 * bad response inside an otherwise-successful HTTP status.
 *
 * Recovery actions:
 * - Verify the configured `DIRECT_PAYMENT_SERVER` URL points to a SEP-31 compliant anchor
 * - Contact the Receiving Anchor operator if the failure persists
 *
 * Example - Handle invalid response:
 * ```kotlin
 * try {
 *     val info = sep31Service.info(jwt = jwt)
 * } catch (e: Sep31InvalidResponseException) {
 *     println("Malformed anchor response (HTTP ${e.statusCode}): ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep31Exception] base class
 * - [SEP-0031 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md)
 *
 * @property statusCode The HTTP status code that produced the malformed body (defaults to 200).
 * @property rawResponseBody Anchor response body preserved for local debugging only.
 *   Identical to the sanitized form used in [message] except that JWT-shaped substrings
 *   are NOT replaced with `<redacted-jwt>`. May therefore contain bearer tokens.
 *   Truncated to 1024 chars and stripped of control characters, so this field is safe
 *   against log-injection — but it is NOT safe to ship to shared log aggregators
 *   (Sentry, Datadog, Splunk) in production. Use to debug anchors that echo tokens or
 *   other sensitive context in error responses. For production logging, use [message]
 *   instead. `null` when the SDK had no response body to capture for this error path
 *   (for example, content-type rejection before any body was read).
 * @param message Sanitized error message describing the parsing failure.
 */
class Sep31InvalidResponseException(
    message: String,
    val statusCode: Int = 200,
    val rawResponseBody: String? = null,
) : Sep31Exception(message) {
    override fun toString(): String {
        return "SEP-31 invalid response (HTTP $statusCode): $message"
    }
}
