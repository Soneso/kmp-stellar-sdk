// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep08.exceptions

/**
 * Exception thrown when the action URL returns a malformed or unexpected response
 * to a post-action request (postAction).
 *
 * Indicates that the response from the action URL could not be parsed or does not
 * conform to the SEP-8 specification. This typically occurs when:
 * - The response JSON is malformed or missing required fields
 * - The response does not contain a valid result field
 * - The action URL returned an unexpected HTTP status code
 * - The next_url field is present but invalid
 *
 * Action URLs are provided by the approval server when a transaction requires
 * additional steps (e.g., KYC verification) before it can be approved. The client
 * submits required fields to the action URL and processes the response.
 *
 * Recovery actions:
 * - Verify all required fields were submitted to the action URL
 * - Check the action URL is accessible and returning valid JSON
 * - Ensure the request content type matches what the server expects
 * - Contact the asset issuer if the problem persists
 *
 * Example - Handle invalid action response:
 * ```kotlin
 * try {
 *     val response = sep08Service.postAction(actionUrl, actionFields)
 * } catch (e: Sep08InvalidActionResponseException) {
 *     println("Action URL returned invalid response: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep08Exception] base class
 * - [SEP-0008 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md)
 *
 * @param message Detailed error message describing the response parsing failure
 */
class Sep08InvalidActionResponseException(
    message: String
) : Sep08Exception(message) {
    override fun toString(): String {
        return "SEP-08 invalid action response: $message"
    }
}
