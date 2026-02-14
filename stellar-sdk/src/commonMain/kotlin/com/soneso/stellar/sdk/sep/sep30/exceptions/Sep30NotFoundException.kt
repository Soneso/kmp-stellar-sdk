// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30.exceptions

/**
 * Exception thrown when the recovery server returns HTTP 404 Not Found.
 *
 * Indicates that the requested account or resource does not exist on the recovery
 * server. This typically occurs when:
 * - The account has not been registered with the recovery server
 * - The account was previously deleted
 * - The signing address used in a recovery request is not recognized
 *
 * Recovery actions:
 * - Verify the Stellar account address is correct
 * - Register the account with the recovery server before attempting recovery
 * - Check if the account was registered with a different recovery server
 *
 * Example - Handle not found:
 * ```kotlin
 * try {
 *     val details = sep30Service.getAccountDetails(address)
 * } catch (e: Sep30NotFoundException) {
 *     println("Account not registered: ${e.message}")
 *     // Register the account first
 * }
 * ```
 *
 * See also:
 * - [Sep30Exception] base class
 * - [SEP-0030 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md)
 *
 * @param message Detailed error message from the recovery server
 */
class Sep30NotFoundException(
    message: String
) : Sep30Exception(message) {
    override fun toString(): String {
        return "SEP-30 not found: $message"
    }
}
