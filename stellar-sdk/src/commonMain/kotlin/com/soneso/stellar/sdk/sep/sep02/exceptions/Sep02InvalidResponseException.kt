// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep02.exceptions

/**
 * Exception thrown when the federation server returns a malformed or unexpected response.
 *
 * Indicates that the response from the federation server could not be parsed or does not
 * conform to the SEP-2 specification. This typically occurs when:
 * - The response JSON is malformed or missing required fields
 * - The response is missing the required account_id field
 * - The response contains an invalid account_id format
 * - The federation server returned a non-2xx HTTP status code
 * - The response HTTP status indicates an error (4xx, 5xx)
 * - The Content-Type is not application/json
 *
 * Valid SEP-2 responses must include at minimum an account_id field containing a valid
 * Stellar account ID (G... address). Responses may also include optional fields such as
 * memo_type, memo, and stellar_address.
 *
 * Recovery actions:
 * - Verify the federation server URL is correct
 * - Check that the requested Stellar address exists on the federation server
 * - Ensure the federation server is accessible and returning valid JSON
 * - Inspect the HTTP status code included in the error message
 * - Contact the federation server administrator if the problem persists
 *
 * Example - Handle invalid response:
 * ```kotlin
 * try {
 *     val response = federationService.resolveStellarAddress("user*example.com")
 * } catch (e: Sep02InvalidResponseException) {
 *     println("Invalid federation response: ${e.message}")
 *     // Check if HTTP status code indicates rate limiting or server error
 * }
 * ```
 *
 * See also:
 * - [Sep02Exception] base class
 * - [SEP-0002 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md)
 *
 * @param message Detailed error message describing the response parsing failure, including HTTP status for non-2xx responses
 */
class Sep02InvalidResponseException(
    message: String
) : Sep02Exception(message) {
    override fun toString(): String {
        return "SEP-02 invalid response: $message"
    }
}
