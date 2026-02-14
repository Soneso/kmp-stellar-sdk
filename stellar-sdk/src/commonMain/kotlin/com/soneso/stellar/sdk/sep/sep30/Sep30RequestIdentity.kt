// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30

/**
 * Represents an identity configuration for SEP-30 account recovery requests.
 *
 * Each identity defines a [role] and a list of [authMethods] that the recovery server
 * uses to verify the identity during account recovery operations.
 *
 * ## Roles
 *
 * - `"owner"`: Can sign transactions and modify account identities on the recovery server.
 * - `"other"`: Can only sign transactions (additional signers).
 *
 * ## Usage
 *
 * ```kotlin
 * val emailAuth = Sep30AuthMethod(type = "email", value = "user@example.com")
 * val phoneAuth = Sep30AuthMethod(type = "phone_number", value = "+14155551234")
 *
 * val ownerIdentity = Sep30RequestIdentity(
 *     role = "owner",
 *     authMethods = listOf(emailAuth, phoneAuth)
 * )
 * ```
 *
 * @property role The role of this identity (`"owner"` or `"other"`)
 * @property authMethods The list of authentication methods for verifying this identity
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md">SEP-0030 Specification</a>
 */
data class Sep30RequestIdentity(
    val role: String,
    val authMethods: List<Sep30AuthMethod>
) {

    /**
     * Converts this request identity to a JSON-compatible map.
     *
     * The returned map uses the SEP-30 specification field names. The [authMethods] property
     * is serialized under the `"auth_methods"` key as required by the specification.
     *
     * @return A map with `"role"` and `"auth_methods"` entries
     */
    fun toJson(): Map<String, Any?> = mapOf(
        "role" to role,
        "auth_methods" to authMethods.map { it.toJson() }
    )
}
