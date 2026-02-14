// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30

import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30InvalidResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents a signer in a SEP-30 account recovery response.
 *
 * Each signer corresponds to a Stellar public key that the recovery server controls and can
 * use to sign recovery transactions after successful identity authentication. Signers are
 * ordered from most recently added to least recently added.
 *
 * ## Usage
 *
 * ```kotlin
 * val json = Json.parseToJsonElement(responseBody).jsonObject
 * val signer = Sep30ResponseSigner.fromJson(json)
 * println("Signer key: ${signer.key}")
 * ```
 *
 * ## Example JSON
 *
 * ```json
 * { "key": "GADF..." }
 * ```
 *
 * @property key The Stellar public key (G... address) of the recovery signer
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md">SEP-0030 Specification</a>
 */
data class Sep30ResponseSigner(
    val key: String
) {
    companion object {
        /**
         * Parses a JSON object into a [Sep30ResponseSigner].
         *
         * The JSON must contain a `key` field with the signer's Stellar public key.
         *
         * @param json The JSON object representing a signer
         * @return The parsed response signer
         * @throws Sep30InvalidResponseException if the required `key` field is missing
         */
        fun fromJson(json: JsonObject): Sep30ResponseSigner {
            val key = json["key"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep30InvalidResponseException(
                    "Missing required 'key' field in SEP-30 signer response"
                )

            return Sep30ResponseSigner(key = key)
        }
    }
}
