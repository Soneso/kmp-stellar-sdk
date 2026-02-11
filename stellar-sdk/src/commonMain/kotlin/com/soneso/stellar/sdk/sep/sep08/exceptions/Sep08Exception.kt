// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep08.exceptions

/**
 * Base exception class for SEP-8 Regulated Assets errors.
 *
 * All SEP-8-specific exceptions extend this class to enable unified error handling
 * while providing specific error types for different failure scenarios.
 *
 * This exception hierarchy allows applications to handle regulated asset errors at different levels:
 * - Catch Sep08Exception for general SEP-8 error handling
 * - Catch specific subclasses for precise error recovery
 *
 * Common error scenarios:
 * - [Sep08IncompleteInitDataException]: Network passphrase or Horizon URL missing during initialization
 * - [Sep08InvalidTransactionResponseException]: Malformed approval server response to postTransaction
 * - [Sep08InvalidActionResponseException]: Malformed action URL response to postAction
 *
 * Example - General error handling:
 * ```kotlin
 * try {
 *     val response = sep08Service.postTransaction(tx)
 * } catch (e: Sep08InvalidTransactionResponseException) {
 *     println("Invalid response from approval server: ${e.message}")
 * } catch (e: Sep08Exception) {
 *     println("SEP-8 error: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep08IncompleteInitDataException] for initialization data errors
 * - [Sep08InvalidTransactionResponseException] for malformed postTransaction responses
 * - [Sep08InvalidActionResponseException] for malformed postAction responses
 * - [SEP-0008 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md)
 *
 * @property message Human-readable error description
 * @property cause Optional underlying cause of the error
 */
open class Sep08Exception(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    override fun toString(): String {
        return "SEP-08 error: $message"
    }
}
