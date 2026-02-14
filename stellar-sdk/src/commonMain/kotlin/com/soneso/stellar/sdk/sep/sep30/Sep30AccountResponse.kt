// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30

import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30InvalidResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents a SEP-30 account recovery response.
 *
 * Returned when registering, updating, querying, or deleting an account on a recovery server.
 * Contains the account's Stellar address, registered identities with their authentication
 * status, and the signing addresses that the recovery server controls.
 *
 * The [signers] correspond to the registered [identities] and represent the Stellar addresses
 * that the recovery service can use to sign recovery transactions on behalf of authenticated
 * identities.
 *
 * ## Usage
 *
 * ```kotlin
 * val json = Json.parseToJsonElement(responseBody).jsonObject
 * val response = Sep30AccountResponse.fromJson(json)
 *
 * println("Account: ${response.address}")
 * for (identity in response.identities) {
 *     println("Role: ${identity.role ?: "not specified"}, Authenticated: ${identity.authenticated}")
 * }
 * for (signer in response.signers) {
 *     println("Signer: ${signer.key}")
 * }
 * ```
 *
 * ## Example JSON
 *
 * ```json
 * {
 *   "address": "GFDD...",
 *   "identities": [
 *     { "role": "owner", "authenticated": true }
 *   ],
 *   "signers": [
 *     { "key": "GADF..." }
 *   ]
 * }
 * ```
 *
 * @property address The Stellar account ID or address of the account
 * @property identities The list of recovery identities with roles and authentication status
 * @property signers The list of signing keys the recovery server can use for this account
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md">SEP-0030 Specification</a>
 */
data class Sep30AccountResponse(
    val address: String,
    val identities: List<Sep30ResponseIdentity>,
    val signers: List<Sep30ResponseSigner>
) {
    companion object {
        /**
         * Parses a JSON object into a [Sep30AccountResponse].
         *
         * The JSON must contain `address`, `identities`, and `signers` fields.
         *
         * ## JSON Field Mapping
         *
         * - `address` -> [address] (required)
         * - `identities` -> [identities] (required, array of identity objects)
         * - `signers` -> [signers] (required, array of signer objects)
         *
         * @param json The JSON object returned by the recovery server
         * @return The parsed account response
         * @throws Sep30InvalidResponseException if any required field is missing
         */
        fun fromJson(json: JsonObject): Sep30AccountResponse {
            val address = json["address"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep30InvalidResponseException(
                    "Missing required 'address' field in SEP-30 account response"
                )

            val identitiesArray = json["identities"]?.jsonArray
                ?: throw Sep30InvalidResponseException(
                    "Missing required 'identities' field in SEP-30 account response"
                )
            val identities = identitiesArray.map {
                Sep30ResponseIdentity.fromJson(it.jsonObject)
            }

            val signersArray = json["signers"]?.jsonArray
                ?: throw Sep30InvalidResponseException(
                    "Missing required 'signers' field in SEP-30 account response"
                )
            val signers = signersArray.map {
                Sep30ResponseSigner.fromJson(it.jsonObject)
            }

            return Sep30AccountResponse(
                address = address,
                identities = identities,
                signers = signers
            )
        }
    }
}
