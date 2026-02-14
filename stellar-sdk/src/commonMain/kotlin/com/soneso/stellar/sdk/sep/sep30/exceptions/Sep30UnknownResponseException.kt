// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30.exceptions

/**
 * Exception thrown when the recovery server returns an unexpected HTTP status code.
 *
 * Indicates that the server responded with an HTTP status code not covered by the
 * SEP-30 specification (i.e., not 200, 400, 401, 404, or 409). This may occur when:
 * - The server encounters an internal error (HTTP 500)
 * - The server is temporarily unavailable (HTTP 503)
 * - A proxy or gateway returns an error (HTTP 502, 504)
 * - The server returns any other unexpected status code
 *
 * Recovery actions:
 * - Inspect [statusCode] and [responseBody] for diagnostic information
 * - Retry the request if the error appears transient (5xx)
 * - Contact the recovery server operator if the problem persists
 *
 * Example - Handle unknown response:
 * ```kotlin
 * try {
 *     val account = sep30Service.registerAccount(address, identities, signers)
 * } catch (e: Sep30UnknownResponseException) {
 *     println("Unexpected HTTP ${e.statusCode}: ${e.responseBody}")
 * }
 * ```
 *
 * See also:
 * - [Sep30Exception] base class
 * - [SEP-0030 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md)
 *
 * @property statusCode The HTTP status code returned by the recovery server
 * @property responseBody The raw response body returned by the recovery server
 * @param message Detailed error message describing the unexpected response
 */
class Sep30UnknownResponseException(
    val statusCode: Int,
    val responseBody: String,
    message: String
) : Sep30Exception(message) {
    override fun toString(): String {
        return "SEP-30 unknown response (HTTP $statusCode): $message"
    }
}
