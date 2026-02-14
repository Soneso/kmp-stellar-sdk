// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30

import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30InvalidResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Represents a SEP-30 response containing a list of accounts.
 *
 * Returned by the `GET /accounts` endpoint. Contains all accounts that the authenticated
 * user has permission to access. Supports cursor-based pagination via the `after` query
 * parameter in the request.
 *
 * ## Usage
 *
 * ```kotlin
 * val json = Json.parseToJsonElement(responseBody).jsonObject
 * val response = Sep30AccountsResponse.fromJson(json)
 *
 * for (account in response.accounts) {
 *     println("Account: ${account.address}")
 * }
 * ```
 *
 * ## Example JSON
 *
 * ```json
 * {
 *   "accounts": [
 *     {
 *       "address": "GEAC...",
 *       "identities": [{ "authenticated": true }],
 *       "signers": [{ "key": "GADF..." }]
 *     },
 *     {
 *       "address": "GFDD...",
 *       "identities": [
 *         { "role": "sender", "authenticated": true },
 *         { "role": "receiver" }
 *       ],
 *       "signers": [{ "key": "GADF..." }]
 *     }
 *   ]
 * }
 * ```
 *
 * @property accounts The list of accounts accessible by the authenticated client
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md">SEP-0030 Specification</a>
 */
data class Sep30AccountsResponse(
    val accounts: List<Sep30AccountResponse>
) {
    companion object {
        /**
         * Parses a JSON object into a [Sep30AccountsResponse].
         *
         * The JSON must contain an `accounts` field with an array of account objects.
         *
         * @param json The JSON object returned by the recovery server
         * @return The parsed accounts response
         * @throws Sep30InvalidResponseException if the required `accounts` field is missing
         */
        fun fromJson(json: JsonObject): Sep30AccountsResponse {
            val accountsArray = json["accounts"]?.jsonArray
                ?: throw Sep30InvalidResponseException(
                    "Missing required 'accounts' field in SEP-30 accounts response"
                )
            val accounts = accountsArray.map {
                Sep30AccountResponse.fromJson(it.jsonObject)
            }

            return Sep30AccountsResponse(accounts = accounts)
        }
    }
}
