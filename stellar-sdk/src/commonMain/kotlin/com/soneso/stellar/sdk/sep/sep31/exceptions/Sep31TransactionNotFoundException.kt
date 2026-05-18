// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Exception thrown when the Receiving Anchor returns HTTP 404 for a transaction lookup.
 *
 * Raised from `GET /transactions/:id` and `PATCH /transactions/:id` when the anchor
 * cannot locate a transaction matching the supplied id. This is distinct from
 * [Sep31TransactionCallbackNotSupportedException], which signals that the anchor
 * does not advertise callback support — not that the transaction itself is missing.
 *
 * Common causes:
 * - The transaction id is incorrect or typo'd
 * - The transaction was archived by the anchor's retention policy
 * - The Sending Anchor and Receiving Anchor are configured with different transaction id namespaces
 *
 * Recovery actions:
 * - Verify the transaction id captured from the original `POST /transactions` response
 * - Contact the Receiving Anchor operator if the id is recent and known-good
 *
 * Example - Handle not found:
 * ```kotlin
 * try {
 *     val transaction = sep31Service.getTransaction(id = "11111111-1111-1111-1111-111111111111", jwt = jwt)
 * } catch (e: Sep31TransactionNotFoundException) {
 *     println("Transaction not found (HTTP ${e.statusCode}): ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep31Exception] base class
 * - [Sep31TransactionCallbackNotSupportedException] for the callback-specific 404 path
 * - [SEP-0031 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md)
 *
 * @property statusCode The HTTP status code returned by the Receiving Anchor (always 404 for this exception).
 * @property rawResponseBody Anchor response body preserved for local debugging only.
 *   Identical to the sanitized form used in [message] except that JWT-shaped substrings
 *   are NOT replaced with `<redacted-jwt>`. May therefore contain bearer tokens.
 *   Truncated and stripped of control characters, so this field is safe
 *   against log-injection — but it is NOT safe to ship to shared log aggregators
 *   (Sentry, Datadog, Splunk) in production. Use to debug anchors that echo tokens or
 *   other sensitive context in error responses. For production logging, use [message]
 *   instead. `null` when the SDK had no response body to capture for this error path.
 * @param message Sanitized error message describing the lookup failure.
 */
public class Sep31TransactionNotFoundException(
    message: String,
    public val statusCode: Int = 404,
    public val rawResponseBody: String? = null,
) : Sep31Exception(message) {
    override fun toString(): String {
        return "SEP-31 transaction not found (HTTP $statusCode): $message"
    }
}
