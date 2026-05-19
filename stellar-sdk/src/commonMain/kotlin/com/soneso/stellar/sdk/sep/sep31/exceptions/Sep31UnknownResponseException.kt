// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Exception thrown when the Receiving Anchor returns an unexpected HTTP status code.
 *
 * Raised when the anchor responds with a status code not covered by the SEP-31
 * specification or the SDK's per-endpoint dispatch matrix. Typical scenarios:
 * - 5xx server errors (500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable, 504 Gateway Timeout)
 * - 3xx redirects (the SDK configures `followRedirects = false`, so a 302 surfaces here rather than being followed)
 * - 4xx codes outside the documented 400/401/403/404 set (e.g., 429 Too Many Requests)
 *
 * The raw [responseBody] is captured so callers can implement custom diagnostics or
 * retry policies. The body is already truncated to the SDK's response-size cap; no
 * additional truncation is necessary.
 *
 * Recovery actions:
 * - Inspect [statusCode] and [responseBody] for diagnostic information
 * - Retry the request with exponential backoff if the error appears transient (5xx)
 * - Contact the Receiving Anchor operator if the problem persists
 *
 * Example - Handle unknown response:
 * ```kotlin
 * try {
 *     val response = sep31Service.postTransactions(request, jwt)
 * } catch (e: Sep31UnknownResponseException) {
 *     println("Unexpected HTTP ${e.statusCode}: ${e.message}")
 *     println("Body: ${e.responseBody}")
 * }
 * ```
 *
 * See also:
 * - [Sep31Exception] base class
 * - [SEP-0031 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md)
 *
 * @property statusCode The HTTP status code returned by the Receiving Anchor.
 * @property responseBody The sanitized response body — size-capped, control characters
 *   stripped, and JWT-shaped substrings replaced with `<redacted-jwt>`. Safe to log.
 * @property rawResponseBody Anchor response body for local debugging — see the
 *   rawResponseBody convention on [Sep31Exception]. Identical to [responseBody]
 *   except JWT-shaped substrings are not redacted. `null` when no body was captured.
 * @param message Sanitized error message describing the unexpected response.
 */
public class Sep31UnknownResponseException(
    message: String,
    public val statusCode: Int,
    public val responseBody: String,
    public val rawResponseBody: String? = null,
) : Sep31Exception(message) {
    override fun toString(): String {
        return "SEP-31 unknown response (HTTP $statusCode): $message"
    }
}
