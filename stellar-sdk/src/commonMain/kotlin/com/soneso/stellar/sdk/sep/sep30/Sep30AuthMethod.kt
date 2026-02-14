// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30

/**
 * Represents an authentication method for identity verification in SEP-30 account recovery.
 *
 * Each authentication method specifies a [type] and a [value] that a recovery server uses
 * to verify the identity of a user during account recovery. Multiple authentication methods
 * can be associated with a single identity to provide multi-factor verification.
 *
 * ## Common Types
 *
 * - `stellar_address`: A Stellar federation address (e.g., `"user*example.com"`)
 * - `phone_number`: A phone number in E.164 format (e.g., `"+14155551234"`)
 * - `email`: An email address (e.g., `"user@example.com"`)
 *
 * ## Usage
 *
 * ```kotlin
 * val emailAuth = Sep30AuthMethod(type = "email", value = "user@example.com")
 * val phoneAuth = Sep30AuthMethod(type = "phone_number", value = "+14155551234")
 * val stellarAuth = Sep30AuthMethod(type = "stellar_address", value = "user*example.com")
 * ```
 *
 * @property type The type of authentication method (e.g., `"stellar_address"`, `"phone_number"`, `"email"`)
 * @property value The value for this authentication method
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md">SEP-0030 Specification</a>
 */
data class Sep30AuthMethod(
    val type: String,
    val value: String
) {

    /**
     * Converts this authentication method to a JSON-compatible map.
     *
     * The returned map uses the SEP-30 specification field names and is suitable
     * for serialization when sending requests to a recovery server.
     *
     * @return A map with `"type"` and `"value"` entries
     */
    fun toJson(): Map<String, Any?> = mapOf(
        "type" to type,
        "value" to value
    )
}
