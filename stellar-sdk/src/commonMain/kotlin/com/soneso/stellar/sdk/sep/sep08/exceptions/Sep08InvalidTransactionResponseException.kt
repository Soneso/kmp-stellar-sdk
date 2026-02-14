// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep08.exceptions

/**
 * Exception thrown when the approval server returns a malformed or unexpected response
 * to a transaction approval request (postTransaction).
 *
 * Indicates that the response from the approval server could not be parsed or does not
 * conform to the SEP-8 specification. This typically occurs when:
 * - The response JSON is malformed or missing required fields
 * - The response status field contains an unrecognized value
 * - A "revised" response is missing the required tx field
 * - A "rejected" response is missing the required error field
 * - An "action_required" response is missing the required action_url field
 * - The response HTTP status code is unexpected
 *
 * Recovery actions:
 * - Verify the approval server URL is correct
 * - Check the approval server logs for errors
 * - Ensure the transaction XDR is well-formed before submission
 * - Contact the asset issuer if the problem persists
 *
 * Example - Handle invalid response:
 * ```kotlin
 * try {
 *     val response = sep08Service.postTransaction(signedTxXdr)
 * } catch (e: Sep08InvalidTransactionResponseException) {
 *     println("Approval server returned invalid response: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep08Exception] base class
 * - [SEP-0008 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md)
 *
 * @param message Detailed error message describing the response parsing failure
 */
class Sep08InvalidTransactionResponseException(
    message: String
) : Sep08Exception(message) {
    override fun toString(): String {
        return "SEP-08 invalid transaction response: $message"
    }
}
