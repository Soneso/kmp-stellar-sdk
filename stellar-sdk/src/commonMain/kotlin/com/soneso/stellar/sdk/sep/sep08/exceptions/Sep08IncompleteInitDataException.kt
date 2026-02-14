// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep08.exceptions

/**
 * Exception thrown when required initialization data is missing for the SEP-8 service.
 *
 * Indicates that the network passphrase or Horizon server URL could not be determined
 * during service initialization. This typically occurs when:
 * - The stellar.toml file does not contain a NETWORK_PASSPHRASE entry
 * - The stellar.toml file does not contain a HORIZON_URL entry
 * - The stellar.toml file cannot be resolved from the asset issuer's domain
 *
 * Recovery actions:
 * - Verify the issuer's stellar.toml contains NETWORK_PASSPHRASE and HORIZON_URL
 * - Provide the network passphrase and Horizon URL explicitly during initialization
 * - Ensure the issuer's home domain is correctly configured
 *
 * Example - Handle missing init data:
 * ```kotlin
 * try {
 *     val service = Sep08Service.forDomain("example.com")
 * } catch (e: Sep08IncompleteInitDataException) {
 *     println("Missing configuration: ${e.message}")
 *     // Fall back to explicit configuration
 * }
 * ```
 *
 * See also:
 * - [Sep08Exception] base class
 * - [SEP-0008 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md)
 *
 * @param message Detailed error message describing the missing initialization data
 */
class Sep08IncompleteInitDataException(
    message: String
) : Sep08Exception(message) {
    override fun toString(): String {
        return "SEP-08 incomplete init data: $message"
    }
}
