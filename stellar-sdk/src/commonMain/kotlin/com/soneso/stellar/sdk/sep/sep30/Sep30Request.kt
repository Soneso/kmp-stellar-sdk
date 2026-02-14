// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30

/**
 * Represents a SEP-30 request for registering or updating account recovery identities.
 *
 * Contains a list of [identities] with their roles and authentication methods that
 * a recovery server uses for account recovery operations.
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
 *
 * val request = Sep30Request(identities = listOf(ownerIdentity))
 * ```
 *
 * @property identities The list of identities for account recovery configuration
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md">SEP-0030 Specification</a>
 */
data class Sep30Request(
    val identities: List<Sep30RequestIdentity>
) {

    /**
     * Converts this request to a JSON-compatible map.
     *
     * The returned map is suitable for serialization when sending requests to a recovery
     * server via HTTP POST or PUT. Each identity is converted to its JSON representation
     * using [Sep30RequestIdentity.toJson].
     *
     * @return A map with an `"identities"` entry containing the serialized identity list
     */
    fun toJson(): Map<String, Any?> = mapOf(
        "identities" to identities.map { it.toJson() }
    )
}
