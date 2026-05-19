// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31.exceptions

/**
 * Exception thrown when the Receiving Anchor returns HTTP 401 Unauthorized.
 *
 * Indicates that the SEP-10 JWT used to authenticate the request was missing,
 * malformed, or expired. The SEP-31 specification mandates HTTP 403 for
 * authentication failures (see spec "Authentication" section), but real-world
 * anchors also emit HTTP 401; the SDK handles both. Catch [Sep31UnauthorizedException]
 * for the 401 path and [Sep31ForbiddenException] for the 403 path.
 *
 * Common causes:
 * - The SEP-10 JWT has expired
 * - The `Authorization: Bearer <jwt>` header is missing entirely
 * - The JWT signature did not validate against the anchor's signing key
 *
 * Recovery actions:
 * - Obtain a fresh SEP-10 JWT and retry the request
 * - Verify the JWT issuer matches the anchor's expected issuer
 *
 * Example - Handle unauthorized:
 * ```kotlin
 * try {
 *     val transaction = sep31Service.getTransaction(id = "11111111-1111-1111-1111-111111111111", jwt = jwt)
 * } catch (e: Sep31UnauthorizedException) {
 *     println("Authentication required (HTTP ${e.statusCode}): ${e.message}")
 *     // Re-authenticate via SEP-10 and retry
 * }
 * ```
 *
 * See also:
 * - [Sep31Exception] base class
 * - [Sep31ForbiddenException] for the spec-mandated HTTP 403 variant
 * - [SEP-0031 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md)
 *
 * @property statusCode The HTTP status code returned by the Receiving Anchor (always 401 for this exception).
 * @property rawResponseBody Anchor response body for local debugging — see the
 *   rawResponseBody convention on [Sep31Exception]. `null` when no body was captured.
 * @param message Sanitized error message extracted from the anchor response.
 */
public class Sep31UnauthorizedException(
    message: String,
    public val statusCode: Int = 401,
    public val rawResponseBody: String? = null,
) : Sep31Exception(message) {
    override fun toString(): String {
        return "SEP-31 unauthorized (HTTP $statusCode): $message"
    }
}
