// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

import com.soneso.stellar.sdk.sep.common.sanitizeAnchorString

/**
 * Exception thrown when the Receiving Anchor needs additional SEP-12 KYC information.
 *
 * Raised exclusively from `POST /transactions` when the anchor responds with HTTP 400
 * and `error == "customer_info_needed"`. The [type] field, when present, names the
 * specific SEP-12 customer type the Sending Anchor must collect (matching a key from
 * the `sep12.sender.types` or `sep12.receiver.types` map in the `GET /info` response).
 *
 * Resolution workflow:
 * 1. Inspect [type] to determine which SEP-12 customer type to collect.
 * 2. Call SEP-12 `PUT /customer` with the missing KYC fields.
 * 3. Use the returned `id` as `sender_id` or `receiver_id` in the SEP-31 request.
 * 4. Retry `POST /transactions` with the updated request body.
 *
 * The exception [message] interpolates the anchor-supplied [type] after passing it
 * through the project-wide sanitizer so log-injection and terminal-escape payloads
 * cannot reach application logs via the exception text. The [type] property itself
 * is exposed unsanitized for programmatic use by the caller.
 *
 * Example - Handle customer-info-needed:
 * ```kotlin
 * try {
 *     val response = sep31Service.postTransactions(request, jwt)
 * } catch (e: Sep31CustomerInfoNeededException) {
 *     println("SEP-12 KYC required; collect fields for type=${e.type}")
 *     // Submit SEP-12 PUT /customer with the missing fields, then retry.
 * }
 * ```
 *
 * See also:
 * - [Sep31Exception] base class
 * - [SEP-0031 Specification - POST Transactions](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#post-transactions)
 * - [SEP-0012 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0012.md)
 *
 * @property type The SEP-12 customer type the Sending Anchor must collect, or `null` if the anchor did not specify one.
 * @property error The literal error tag returned by the anchor; always `"customer_info_needed"`.
 * @property rawResponseBody Anchor response body preserved for local debugging only.
 *   Identical to the sanitized form used in [message] except that JWT-shaped substrings
 *   are NOT replaced with `<redacted-jwt>`. May therefore contain bearer tokens.
 *   Truncated and stripped of control characters, so this field is safe
 *   against log-injection — but it is NOT safe to ship to shared log aggregators
 *   (Sentry, Datadog, Splunk) in production. Use to debug anchors that echo tokens or
 *   other sensitive context in error responses. For production logging, use [message]
 *   instead. `null` when the SDK had no response body to capture for this error path.
 */
public class Sep31CustomerInfoNeededException(
    public val type: String?,
    public val rawResponseBody: String? = null,
) : Sep31Exception("Customer info needed; type=${sanitizeAnchorString(type)}") {
    /** The literal error tag returned by the Receiving Anchor for this condition. */
    public val error: String = "customer_info_needed"

    override fun toString(): String {
        return "SEP-31 customer info needed; type=${sanitizeAnchorString(type)}"
    }
}
