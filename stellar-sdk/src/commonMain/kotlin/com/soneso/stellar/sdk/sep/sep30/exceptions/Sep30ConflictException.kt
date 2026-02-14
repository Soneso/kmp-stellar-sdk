// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30.exceptions

/**
 * Exception thrown when the recovery server returns HTTP 409 Conflict.
 *
 * Indicates that the request conflicts with the current state of the recovery server.
 * This typically occurs when:
 * - An account is already registered and a new registration is attempted
 * - A signer is already registered for the account
 *
 * Recovery actions:
 * - Use the update endpoint instead of register if the account already exists
 * - Retrieve the current account details to understand the existing state
 *
 * Example - Handle conflict:
 * ```kotlin
 * try {
 *     val account = sep30Service.registerAccount(address, identities, signers)
 * } catch (e: Sep30ConflictException) {
 *     println("Account already registered: ${e.message}")
 *     // Update the existing registration instead
 * }
 * ```
 *
 * See also:
 * - [Sep30Exception] base class
 * - [SEP-0030 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md)
 *
 * @param message Detailed error message from the recovery server
 */
class Sep30ConflictException(
    message: String
) : Sep30Exception(message) {
    override fun toString(): String {
        return "SEP-30 conflict: $message"
    }
}
