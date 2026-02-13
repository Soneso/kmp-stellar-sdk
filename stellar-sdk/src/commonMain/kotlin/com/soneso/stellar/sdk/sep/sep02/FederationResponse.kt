// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep02

import com.soneso.stellar.sdk.sep.sep02.exceptions.Sep02InvalidResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents a successful response from a SEP-2 federation server.
 *
 * The federation server returns this response when resolving a Stellar address to account
 * information. This response maps user-friendly addresses (e.g., `bob*stellar.org`) to
 * Stellar account IDs and optional memo information needed for payments.
 *
 * ## Fields
 *
 * - [stellarAddress]: The Stellar address that was resolved (e.g., `bob*stellar.org`).
 *   This field is optional in the response but typically included for confirmation.
 * - [accountId]: **Required**. The Stellar account ID (public key) in G... format
 *   (e.g., `GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ`). This is the
 *   destination account for payments.
 * - [memoType]: Optional type of memo that should be attached to transactions sent to
 *   this account. Valid values are `text`, `id`, or `hash`. If absent, no memo is required.
 * - [memo]: Optional value of the memo to attach to transactions. The format depends on
 *   [memoType]:
 *   - For `text`: UTF-8 string (max 28 bytes)
 *   - For `id`: String representation of a 64-bit unsigned integer
 *   - For `hash`: Base64-encoded 32-byte hash
 *   **Important**: This field is always a string, even when [memoType] is `id`. This ensures
 *   compatibility with languages that don't support 64-bit integers without precision loss.
 *
 * ## Usage
 *
 * ```kotlin
 * val json = Json.parseToJsonElement(responseBody).jsonObject
 * val response = FederationResponse.fromJson(json)
 *
 * println("Account ID: ${response.accountId}")
 * if (response.memoType != null && response.memo != null) {
 *     println("Memo required: ${response.memoType} = ${response.memo}")
 * }
 * ```
 *
 * ## Example Response
 *
 * ```json
 * {
 *   "stellar_address": "bob*stellar.org",
 *   "account_id": "GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ",
 *   "memo_type": "id",
 *   "memo": "123"
 * }
 * ```
 *
 * @property stellarAddress The Stellar address that was resolved (optional)
 * @property accountId The Stellar account ID (G... address) - required
 * @property memoType The type of memo required: `text`, `id`, or `hash` (optional)
 * @property memo The memo value as a string (optional)
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0002.md">SEP-0002 Specification</a>
 */
data class FederationResponse(
    val stellarAddress: String? = null,
    val accountId: String,
    val memoType: String? = null,
    val memo: String? = null
) {
    companion object {
        /**
         * Parses a JSON response from a SEP-2 federation server into a [FederationResponse].
         *
         * The JSON must contain at minimum the `account_id` field. All other fields
         * (`stellar_address`, `memo_type`, `memo`) are optional.
         *
         * ## JSON Field Mapping
         *
         * - `stellar_address` → [stellarAddress]
         * - `account_id` → [accountId] (required)
         * - `memo_type` → [memoType]
         * - `memo` → [memo]
         *
         * @param json The JSON object returned by the federation server
         * @return The parsed federation response
         * @throws Sep02InvalidResponseException if the `account_id` field is missing
         *
         * Example:
         * ```kotlin
         * val json = Json.parseToJsonElement("""
         *     {
         *       "stellar_address": "bob*stellar.org",
         *       "account_id": "GCIBUCGPOHWMMMFPFTDWBSVHQRT4DIBJ7AD6BZJYDITBK2LCVBYW7HUQ",
         *       "memo_type": "id",
         *       "memo": "123"
         *     }
         * """.trimIndent()).jsonObject
         *
         * val response = FederationResponse.fromJson(json)
         * ```
         */
        fun fromJson(json: JsonObject): FederationResponse {
            val accountId = json["account_id"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep02InvalidResponseException(
                    "Missing required 'account_id' field in federation response"
                )

            return FederationResponse(
                stellarAddress = json["stellar_address"]?.jsonPrimitive?.contentOrNull,
                accountId = accountId,
                memoType = json["memo_type"]?.jsonPrimitive?.contentOrNull,
                memo = json["memo"]?.jsonPrimitive?.contentOrNull
            )
        }
    }
}
