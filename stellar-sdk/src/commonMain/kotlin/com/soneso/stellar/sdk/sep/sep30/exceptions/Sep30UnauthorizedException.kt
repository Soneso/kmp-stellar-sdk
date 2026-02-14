// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30.exceptions

/**
 * Exception thrown when the recovery server returns HTTP 401 Unauthorized.
 *
 * Indicates that the request lacks valid authentication credentials or the provided
 * credentials are insufficient. This typically occurs when:
 * - The SEP-10 authentication token is missing
 * - The SEP-10 authentication token has expired
 * - The authenticated account does not have permission for the requested operation
 *
 * Recovery actions:
 * - Obtain a valid SEP-10 authentication token before retrying
 * - Re-authenticate if the token has expired
 * - Verify the authenticated account has the required permissions
 *
 * Example - Handle unauthorized:
 * ```kotlin
 * try {
 *     val account = sep30Service.registerAccount(address, identities, signers)
 * } catch (e: Sep30UnauthorizedException) {
 *     println("Authentication required: ${e.message}")
 *     // Re-authenticate via SEP-10 and retry
 * }
 * ```
 *
 * See also:
 * - [Sep30Exception] base class
 * - [SEP-0030 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md)
 *
 * @param message Detailed error message from the recovery server
 */
class Sep30UnauthorizedException(
    message: String
) : Sep30Exception(message) {
    override fun toString(): String {
        return "SEP-30 unauthorized: $message"
    }
}
