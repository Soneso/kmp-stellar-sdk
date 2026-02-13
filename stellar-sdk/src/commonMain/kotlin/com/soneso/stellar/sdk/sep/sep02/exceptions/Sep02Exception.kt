// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep02.exceptions

/**
 * Base exception class for SEP-2 Federation Protocol errors.
 *
 * All SEP-2-specific exceptions extend this class to enable unified error handling
 * while providing specific error types for different failure scenarios.
 *
 * This exception hierarchy allows applications to handle federation errors at different levels:
 * - Catch Sep02Exception for general SEP-2 error handling
 * - Catch specific subclasses for precise error recovery
 *
 * Common error scenarios:
 * - [Sep02InvalidAddressException]: Malformed Stellar address format (missing *, empty parts, multiple *)
 * - [Sep02FederationNotFoundException]: stellar.toml does not contain FEDERATION_SERVER field
 * - [Sep02InvalidResponseException]: Malformed federation server response, missing required fields, or HTTP errors
 *
 * Example - General error handling:
 * ```kotlin
 * try {
 *     val response = federationService.resolveStellarAddress("user*example.com")
 * } catch (e: Sep02InvalidAddressException) {
 *     println("Invalid address format: ${e.message}")
 * } catch (e: Sep02Exception) {
 *     println("SEP-2 error: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep02InvalidAddressException] for address format errors
 * - [Sep02FederationNotFoundException] for missing federation server configuration
 * - [Sep02InvalidResponseException] for malformed server responses
 * - [SEP-0002 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md)
 *
 * @property message Human-readable error description
 * @property cause Optional underlying cause of the error
 */
open class Sep02Exception(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    override fun toString(): String {
        return "SEP-02 error: $message"
    }
}
