// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30.exceptions

/**
 * Exception thrown when the recovery server returns HTTP 400 Bad Request.
 *
 * Indicates that the request was malformed or contained invalid parameters.
 * This typically occurs when:
 * - Required request fields are missing
 * - Request body JSON is malformed
 * - Identity or signer parameters are invalid
 * - The Stellar address format is incorrect
 *
 * Recovery actions:
 * - Verify all required request parameters are provided
 * - Check that the Stellar account address is valid
 * - Ensure identities and signers conform to the SEP-30 specification
 *
 * Example - Handle bad request:
 * ```kotlin
 * try {
 *     val account = sep30Service.registerAccount(address, identities, signers)
 * } catch (e: Sep30BadRequestException) {
 *     println("Invalid request: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep30Exception] base class
 * - [SEP-0030 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md)
 *
 * @param message Detailed error message from the recovery server
 */
class Sep30BadRequestException(
    message: String
) : Sep30Exception(message) {
    override fun toString(): String {
        return "SEP-30 bad request: $message"
    }
}
