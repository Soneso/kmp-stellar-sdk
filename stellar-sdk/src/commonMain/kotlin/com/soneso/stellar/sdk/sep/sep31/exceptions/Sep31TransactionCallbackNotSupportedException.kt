// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Exception thrown when the Receiving Anchor returns HTTP 404 for callback registration.
 *
 * Raised from `PUT /transactions/:id/callback` when the anchor returns HTTP 404.
 * Per SEP-31 §"PUT Transaction Callback", a 404 on this endpoint indicates that
 * the Receiving Anchor does not support callback-based status notifications.
 * Note: the spec does not separately define a response code for an unknown
 * transaction id on this endpoint, so a 404 here could in principle also reflect
 * a transaction-not-found condition — the SDK maps every 404 on PUT /callback to
 * this exception. Lookups against `GET` / `PATCH /transactions/:id` continue to
 * surface unknown-transaction 404s as [Sep31TransactionNotFoundException].
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
 * @property rawResponseBody Anchor response body for local debugging — see the
 *   rawResponseBody convention on [Sep31Exception]. `null` when no body was captured.
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
