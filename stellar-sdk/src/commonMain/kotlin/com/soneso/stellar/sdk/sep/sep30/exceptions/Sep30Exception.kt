// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30.exceptions

/**
 * Base exception class for SEP-30 Account Recovery errors.
 *
 * All SEP-30-specific exceptions extend this class to enable unified error handling
 * while providing specific error types for different failure scenarios.
 *
 * This exception hierarchy maps directly to HTTP status codes returned by recovery
 * servers, allowing applications to handle errors at different levels:
 * - Catch Sep30Exception for general SEP-30 error handling
 * - Catch specific subclasses for precise error recovery
 *
 * Common error scenarios:
 * - [Sep30BadRequestException]: Recovery server returned HTTP 400 (invalid request parameters)
 * - [Sep30UnauthorizedException]: Recovery server returned HTTP 401 (missing or invalid authentication)
 * - [Sep30NotFoundException]: Recovery server returned HTTP 404 (account not found)
 * - [Sep30ConflictException]: Recovery server returned HTTP 409 (account already registered)
 * - [Sep30UnknownResponseException]: Recovery server returned an unexpected HTTP status code
 * - [Sep30InvalidResponseException]: Recovery server returned HTTP 200 with malformed body
 *
 * Example - General error handling:
 * ```kotlin
 * try {
 *     val account = sep30Service.registerAccount(address, identities, signers)
 * } catch (e: Sep30UnauthorizedException) {
 *     println("Authentication failed: ${e.message}")
 * } catch (e: Sep30Exception) {
 *     println("SEP-30 error: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [SEP-0030 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md)
 *
 * @property message Human-readable error description
 * @property cause Optional underlying cause of the error
 */
open class Sep30Exception(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    override fun toString(): String {
        return "SEP-30 error: $message"
    }
}
