// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30.exceptions

/**
 * Exception thrown when the recovery server returns HTTP 200 with a malformed response body.
 *
 * Indicates that the server returned a successful HTTP status but the response could not
 * be parsed or is missing required fields. This typically occurs when:
 * - The response JSON is malformed or unparseable
 * - Required fields (address, identities, signers) are missing from the response
 * - Field values have unexpected types or formats
 * - The response structure does not conform to the SEP-30 specification
 *
 * Recovery actions:
 * - Verify the recovery server URL is correct and points to a SEP-30 compliant server
 * - Check the recovery server version and SEP-30 compatibility
 * - Contact the recovery server operator if the problem persists
 *
 * Example - Handle invalid response:
 * ```kotlin
 * try {
 *     val details = sep30Service.getAccountDetails(address)
 * } catch (e: Sep30InvalidResponseException) {
 *     println("Malformed server response: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep30Exception] base class
 * - [SEP-0030 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md)
 *
 * @param message Detailed error message describing the response parsing failure
 */
class Sep30InvalidResponseException(
    message: String
) : Sep30Exception(message) {
    override fun toString(): String {
        return "SEP-30 invalid response: $message"
    }
}
