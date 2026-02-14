// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep02.exceptions

/**
 * Exception thrown when a Stellar address has an invalid format.
 *
 * Indicates that the Stellar address does not conform to the federation protocol format.
 * This typically occurs when:
 * - The address is missing the * separator (e.g., "userexample.com")
 * - The username part (before *) is empty (e.g., "*example.com")
 * - The domain part (after *) is empty (e.g., "user*")
 * - The address contains multiple * characters (e.g., "user*example*com")
 *
 * A valid Stellar address follows the format: username*domain
 * - Username: Non-empty string identifying the user
 * - Domain: Non-empty domain name of the federation server
 * - Separator: Single * character
 *
 * Recovery actions:
 * - Verify the address contains exactly one * separator
 * - Ensure both username and domain parts are non-empty
 * - Check for typos in the address format
 *
 * Example - Handle invalid address:
 * ```kotlin
 * try {
 *     val response = federationService.resolveStellarAddress("user*example.com")
 * } catch (e: Sep02InvalidAddressException) {
 *     println("Invalid address format: ${e.message}")
 *     // Prompt user to correct the address
 * }
 * ```
 *
 * See also:
 * - [Sep02Exception] base class
 * - [SEP-0002 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md)
 *
 * @param message Detailed error message describing the address format issue
 */
class Sep02InvalidAddressException(
    message: String
) : Sep02Exception(message) {
    override fun toString(): String {
        return "SEP-02 invalid address: $message"
    }
}
