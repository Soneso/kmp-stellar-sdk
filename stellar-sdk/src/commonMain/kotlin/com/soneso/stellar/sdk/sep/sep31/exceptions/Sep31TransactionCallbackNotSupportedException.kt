// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Exception thrown when the Receiving Anchor returns HTTP 404 for callback registration.
 *
 * Raised exclusively from `PUT /transactions/:id/callback` when the anchor signals
 * that it does not support callback-based status notifications. This is distinct
 * from [Sep31TransactionNotFoundException], which signals that the transaction id
 * itself is unknown — here the anchor recognises the transaction but refuses the
 * callback registration.
 *
 * Recovery actions:
 * - Switch to polling `GET /transactions/:id` to track transaction status
 * - Confirm callback support with the Receiving Anchor operator before retrying
 *
 * Example - Handle callback not supported:
 * ```kotlin
 * try {
 *     sep31Service.putTransactionCallback(
 *         id = "11111111-1111-1111-1111-111111111111",
 *         callbackUrl = "https://example.org/sep31-callback",
 *         jwt = jwt
 *     )
 * } catch (e: Sep31TransactionCallbackNotSupportedException) {
 *     println("Callback not supported (HTTP ${e.statusCode}): ${e.message}")
 *     // Fall back to polling.
 * }
 * ```
 *
 * See also:
 * - [Sep31Exception] base class
 * - [Sep31TransactionNotFoundException] for the transaction-id 404 path
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
 * @param message Sanitized error message describing the callback rejection.
 */
public class Sep31TransactionCallbackNotSupportedException(
    message: String,
    public val statusCode: Int = 404,
    public val rawResponseBody: String? = null,
) : Sep31Exception(message) {
    override fun toString(): String {
        return "SEP-31 transaction callback not supported (HTTP $statusCode): $message"
    }
}
