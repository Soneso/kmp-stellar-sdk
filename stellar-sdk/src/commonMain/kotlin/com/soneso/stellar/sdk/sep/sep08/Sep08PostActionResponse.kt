// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep08

import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08InvalidActionResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents the response from a SEP-8 action URL after the user completes a required action
 * (e.g., KYC verification) via the postAction endpoint.
 *
 * When the approval server returns an [Sep08PostTransactionResponse.ActionRequired] response,
 * the client directs the user to the action URL. After the action is completed, the action URL
 * returns one of two possible outcomes, each modeled as a subclass of this sealed class. The
 * response type is determined by the `result` field in the JSON response.
 *
 * ## Response Types
 *
 * - [Done]: No further action is required. The client should resubmit the original transaction
 *   to the approval server.
 * - [NextUrl]: The user must visit another URL to continue the process (e.g., a multi-step
 *   verification flow).
 *
 * ## Usage
 *
 * ```kotlin
 * val json = Json.parseToJsonElement(responseBody).jsonObject
 * val response = Sep08PostActionResponse.fromJson(json)
 *
 * when (response) {
 *     is Sep08PostActionResponse.Done -> {
 *         // Resubmit the original transaction to the approval server
 *     }
 *     is Sep08PostActionResponse.NextUrl -> {
 *         // Direct the user to response.nextUrl
 *     }
 * }
 * ```
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md">SEP-0008 Specification</a>
 */
sealed class Sep08PostActionResponse {

    /**
     * No further action is required from the user.
     *
     * The client should resubmit the original transaction to the approval server for
     * re-evaluation. The approval server may now approve the transaction since the
     * required action has been completed.
     */
    data object Done : Sep08PostActionResponse()

    /**
     * The user must visit another URL to continue the action flow.
     *
     * This occurs in multi-step verification flows where the action URL redirects the
     * user through additional steps. The client should direct the user to [nextUrl]
     * to continue.
     *
     * @property nextUrl The URL the user should visit next to continue the action flow
     * @property message Optional human-readable message providing context about the next step
     */
    data class NextUrl(
        val nextUrl: String,
        val message: String? = null
    ) : Sep08PostActionResponse()

    companion object {

        private const val RESULT_NO_FURTHER_ACTION = "no_further_action_required"
        private const val RESULT_FOLLOW_NEXT_URL = "follow_next_url"

        /**
         * Parses a JSON response from a SEP-8 action URL into the appropriate
         * [Sep08PostActionResponse] variant.
         *
         * The JSON must contain a `result` field with one of the recognized values:
         * `"no_further_action_required"` or `"follow_next_url"`. Required fields are
         * validated based on the result type.
         *
         * @param json The JSON object returned by the action URL
         * @return The parsed response as the appropriate sealed class variant
         * @throws Sep08InvalidActionResponseException if the result field is missing
         *   or unrecognized, or if required fields for the given result are missing
         */
        fun fromJson(json: JsonObject): Sep08PostActionResponse {
            val result = json["result"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep08InvalidActionResponseException(
                    "Missing 'result' field in action URL response"
                )

            return when (result) {
                RESULT_NO_FURTHER_ACTION -> Done
                RESULT_FOLLOW_NEXT_URL -> parseNextUrl(json)
                else -> throw Sep08InvalidActionResponseException(
                    "Unknown result '$result' in action URL response"
                )
            }
        }

        private fun parseNextUrl(json: JsonObject): NextUrl {
            val nextUrl = json["next_url"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep08InvalidActionResponseException(
                    "Missing required 'next_url' field in 'follow_next_url' response"
                )
            val message = json["message"]?.jsonPrimitive?.contentOrNull
            return NextUrl(nextUrl = nextUrl, message = message)
        }
    }
}
