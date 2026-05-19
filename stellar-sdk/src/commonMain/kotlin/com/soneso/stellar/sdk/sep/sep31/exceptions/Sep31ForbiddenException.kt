// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Exception thrown when the Receiving Anchor returns HTTP 403 Forbidden.
 *
 * Indicates a SEP-10 authentication failure as defined by the SEP-31 specification
 * "Authentication" section: "Any API request that fails to meet proper authentication
 * should return a 403 Forbidden response." Catch [Sep31ForbiddenException] for the
 * spec-mandated path and [Sep31UnauthorizedException] for anchors that emit HTTP 401
 * for the same condition.
 *
 * Common causes:
 * - The SEP-10 JWT is valid but signed by a different anchor
 * - The JWT claims do not satisfy the Receiving Anchor's authorization policy
 * - The authenticated Sending Anchor has been suspended or rate-limited by the Receiving Anchor
 *
 * Recovery actions:
 * - Verify the SEP-10 JWT was issued by the same anchor advertised in stellar.toml
 * - Contact the Receiving Anchor operator if the failure persists with a fresh JWT
 *
 * Example - Handle forbidden:
 * ```kotlin
 * try {
 *     val info = sep31Service.info(jwt = jwt)
 * } catch (e: Sep31ForbiddenException) {
 *     println("Access denied (HTTP ${e.statusCode}): ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep31Exception] base class
 * - [Sep31UnauthorizedException] for the HTTP 401 variant
 * - [SEP-0031 Specification - Authentication](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#authentication)
 *
 * @property statusCode The HTTP status code returned by the Receiving Anchor (always 403 for this exception).
 * @property rawResponseBody Anchor response body for local debugging — see the
 *   rawResponseBody convention on [Sep31Exception]. `null` when no body was captured.
 * @param message Sanitized error message extracted from the anchor response.
 */
public class Sep31ForbiddenException(
    message: String,
    public val statusCode: Int = 403,
    public val rawResponseBody: String? = null,
) : Sep31Exception(message) {
    override fun toString(): String {
        return "SEP-31 forbidden (HTTP $statusCode): $message"
    }
}
