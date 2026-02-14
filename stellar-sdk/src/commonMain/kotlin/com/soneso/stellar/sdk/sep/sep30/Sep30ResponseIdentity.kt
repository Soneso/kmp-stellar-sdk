// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents an identity in a SEP-30 account recovery response.
 *
 * Contains the [role] and optional [authenticated] status of a registered recovery identity.
 * The recovery server returns this information when querying or modifying account recovery
 * configurations.
 *
 * ## Roles
 *
 * - `"owner"`: Can sign transactions and modify account identities on the recovery server.
 * - `"other"`: Can only sign transactions (additional signers).
 * - Custom roles are also supported (e.g., `"sender"`, `"receiver"`).
 *
 * The [role] field is nullable because the SEP-30 specification does not require it in all
 * response contexts. For example, the `GET /accounts` response may return identities without
 * a role when the identity is authenticated but the role was not set during registration.
 *
 * The [authenticated] field is optional. When present and `true`, it indicates that the
 * currently authenticated client has successfully verified this identity. When absent,
 * it is set to `null` (not `false`).
 *
 * ## Usage
 *
 * ```kotlin
 * val json = Json.parseToJsonElement(responseBody).jsonObject
 * val identity = Sep30ResponseIdentity.fromJson(json)
 *
 * println("Role: ${identity.role ?: "not specified"}")
 * if (identity.authenticated == true) {
 *     println("Identity is authenticated")
 * }
 * ```
 *
 * ## Example JSON (with role)
 *
 * ```json
 * { "role": "owner", "authenticated": true }
 * ```
 *
 * ## Example JSON (without role)
 *
 * ```json
 * { "authenticated": true }
 * ```
 *
 * @property role The role of the identity (e.g., `"owner"`, `"other"`), or `null` if not specified
 * @property authenticated Whether the identity has been authenticated, or `null` if not reported
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md">SEP-0030 Specification</a>
 */
data class Sep30ResponseIdentity(
    val role: String?,
    val authenticated: Boolean?
) {
    companion object {
        /**
         * Parses a JSON object into a [Sep30ResponseIdentity].
         *
         * Both `role` and `authenticated` are optional fields. If absent in the JSON,
         * they are set to `null`.
         *
         * @param json The JSON object representing an identity
         * @return The parsed response identity
         */
        fun fromJson(json: JsonObject): Sep30ResponseIdentity {
            val role = json["role"]?.jsonPrimitive?.contentOrNull
            val authenticated = json["authenticated"]?.jsonPrimitive?.booleanOrNull

            return Sep30ResponseIdentity(
                role = role,
                authenticated = authenticated
            )
        }
    }
}
