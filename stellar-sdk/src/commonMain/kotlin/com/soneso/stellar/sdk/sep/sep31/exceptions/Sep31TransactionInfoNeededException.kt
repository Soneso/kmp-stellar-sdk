// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Exception thrown when the Receiving Anchor reports missing per-transaction fields.
 *
 * Raised exclusively from `POST /transactions` when the anchor responds with HTTP 400
 * and `error == "transaction_info_needed"`. The [fields] map mirrors the deprecated
 * `fields` shape from `GET /info` and describes the per-transaction parameters the
 * Sending Anchor must supply on retry.
 *
 * The leaves of the [fields] map are always primitive Kotlin types (`String`,
 * `Boolean`, `Long`, `Double`, or `null`) — never `kotlinx.serialization.json.JsonElement`
 * instances. Callers can safely walk the nested `Map<String, Any?>` and `List<Any?>`
 * structure without performing `JsonElement` casts.
 *
 * Resolution workflow (legacy):
 * 1. Inspect [fields] for the missing per-transaction parameters.
 * 2. Populate the `fields` map on the next `Sep31PostTransactionsRequest` and retry.
 *
 * The per-transaction `fields` workflow is deprecated. New integrations should
 * register customer KYC via SEP-12 `PUT /customer` and pass `sender_id` / `receiver_id`
 * instead.
 *
 * Example - Handle transaction-info-needed:
 * ```kotlin
 * try {
 *     val response = sep31Service.postTransactions(request, jwt)
 * } catch (e: Sep31TransactionInfoNeededException) {
 *     @Suppress("DEPRECATION")
 *     println("Missing transaction fields: ${e.fields?.keys}")
 *     // Populate the legacy fields map and retry, or migrate to SEP-12.
 * }
 * ```
 *
 * See also:
 * - [Sep31Exception] base class
 * - [SEP-0031 Specification - POST Transactions](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#post-transactions)
 *
 * @property fields The per-transaction parameters the Sending Anchor must supply on retry.
 *   Leaves are primitive Kotlin types only; `null` when the anchor did not include a `fields` object.
 * @property error The literal error tag returned by the anchor; always `"transaction_info_needed"`.
 * @property rawResponseBody Anchor response body preserved for local debugging only.
 *   Identical to the sanitized form used in [message] except that JWT-shaped substrings
 *   are NOT replaced with `<redacted-jwt>`. May therefore contain bearer tokens.
 *   Truncated and stripped of control characters, so this field is safe
 *   against log-injection — but it is NOT safe to ship to shared log aggregators
 *   (Sentry, Datadog, Splunk) in production. Use to debug anchors that echo tokens or
 *   other sensitive context in error responses. For production logging, use [message]
 *   instead. `null` when the SDK had no response body to capture for this error path.
 */
@Deprecated(
    message = "SEP-31 v2.5.0 deprecated per-transaction fields. Use SEP-12 PUT /customer instead.",
    level = DeprecationLevel.WARNING
)
public class Sep31TransactionInfoNeededException(
    public val fields: Map<String, Any?>?,
    public val rawResponseBody: String? = null,
) : Sep31Exception("Transaction info needed; see fields") {
    /** The literal error tag returned by the Receiving Anchor for this condition. */
    public val error: String = "transaction_info_needed"

    override fun toString(): String {
        return "SEP-31 transaction info needed; see fields"
    }
}
