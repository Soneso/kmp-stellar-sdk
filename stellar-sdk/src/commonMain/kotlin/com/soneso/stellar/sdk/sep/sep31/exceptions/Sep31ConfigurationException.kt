// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Exception thrown when SEP-31 service configuration is invalid before any HTTP call.
 *
 * Raised by `Sep31Service.fromDomain` when:
 * - The supplied `domain` string is empty, whitespace, or contains URL-unsafe characters
 * - The stellar.toml at the supplied domain does not advertise a `DIRECT_PAYMENT_SERVER`
 * - The advertised `DIRECT_PAYMENT_SERVER` is not an HTTPS URL
 * - The TOML fetch failed (the underlying cause is propagated via [cause])
 *
 * No HTTP status code is associated with this exception because no SEP-31 call
 * completed. Subclasses of [Sep31Exception] that carry `statusCode` are reserved
 * for failures that originated on the SEP-31 endpoint itself.
 *
 * Recovery actions:
 * - Verify the anchor's stellar.toml exposes `DIRECT_PAYMENT_SERVER` and that the URL is HTTPS
 * - Verify the domain string is well-formed (no slashes, query strings, or whitespace)
 *
 * Example - Handle configuration failure:
 * ```kotlin
 * try {
 *     val service = Sep31Service.fromDomain("anchor.example.org")
 * } catch (e: Sep31ConfigurationException) {
 *     println("Anchor configuration invalid: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep31Exception] base class
 * - [SEP-0031 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md)
 *
 * @param message Detailed error message describing the configuration failure.
 * @param cause Optional underlying cause (for example, a TOML fetch failure).
 */
class Sep31ConfigurationException(
    message: String,
    cause: Throwable? = null
) : Sep31Exception(message, cause) {
    override fun toString(): String {
        return "SEP-31 configuration error: $message"
    }
}
