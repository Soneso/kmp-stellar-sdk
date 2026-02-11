// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep08

import com.soneso.stellar.sdk.sep.sep08.exceptions.Sep08InvalidTransactionResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents the response from a SEP-8 approval server when a transaction is submitted
 * for regulatory approval via the postTransaction endpoint.
 *
 * The approval server evaluates the submitted transaction and returns one of five possible
 * outcomes, each modeled as a subclass of this sealed class. The response type is determined
 * by the `status` field in the JSON response.
 *
 * ## Response Types
 *
 * - [Success]: The transaction was approved and is ready for submission to the network.
 * - [Revised]: The transaction was modified by the approval server (e.g., additional operations
 *   added) and the revised version should be submitted instead.
 * - [Pending]: The approval server needs more time to evaluate the transaction. The client
 *   should resubmit after the specified timeout.
 * - [ActionRequired]: The user must complete an additional action (e.g., KYC verification)
 *   before the transaction can be approved.
 * - [Rejected]: The transaction was rejected and cannot be approved.
 *
 * ## Usage
 *
 * ```kotlin
 * val json = Json.parseToJsonElement(responseBody).jsonObject
 * val response = Sep08PostTransactionResponse.fromJson(json)
 *
 * when (response) {
 *     is Sep08PostTransactionResponse.Success -> {
 *         // Submit response.tx to the Stellar network
 *     }
 *     is Sep08PostTransactionResponse.Revised -> {
 *         // Review response.tx and response.message, then submit
 *     }
 *     is Sep08PostTransactionResponse.Pending -> {
 *         // Wait response.timeout milliseconds, then resubmit
 *     }
 *     is Sep08PostTransactionResponse.ActionRequired -> {
 *         // Direct user to response.actionUrl
 *     }
 *     is Sep08PostTransactionResponse.Rejected -> {
 *         // Display response.error to the user
 *     }
 * }
 * ```
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0008.md">SEP-0008 Specification</a>
 */
sealed class Sep08PostTransactionResponse {

    /**
     * The transaction was approved by the approval server without modifications.
     *
     * The [tx] field contains the signed transaction XDR envelope that is ready to be
     * submitted to the Stellar network. The approval server may have added its signature
     * to the transaction.
     *
     * @property tx The approved transaction as a base64-encoded XDR transaction envelope
     * @property message Optional human-readable message from the approval server
     */
    data class Success(
        val tx: String,
        val message: String? = null
    ) : Sep08PostTransactionResponse()

    /**
     * The transaction was revised by the approval server.
     *
     * The approval server modified the original transaction (e.g., added compliance-related
     * operations) and returned a revised version. The [tx] field contains the modified
     * transaction XDR that the client should sign and submit instead of the original.
     *
     * The [message] field explains what changes were made and why.
     *
     * @property tx The revised transaction as a base64-encoded XDR transaction envelope
     * @property message Human-readable explanation of the revisions made
     */
    data class Revised(
        val tx: String,
        val message: String
    ) : Sep08PostTransactionResponse()

    /**
     * The approval server needs more time to process the transaction.
     *
     * The client should wait for [timeout] milliseconds and then resubmit the same transaction
     * to the approval server. This status may indicate that the server is performing
     * asynchronous checks (e.g., compliance verification).
     *
     * @property timeout Number of milliseconds the client should wait before resubmitting (default 0)
     * @property message Optional human-readable message explaining the delay
     */
    data class Pending(
        val timeout: Int = 0,
        val message: String? = null
    ) : Sep08PostTransactionResponse()

    /**
     * The user must complete an action before the transaction can be approved.
     *
     * The approval server requires additional information or verification from the user.
     * The client should direct the user to the [actionUrl] to complete the required action
     * (e.g., KYC verification, identity confirmation).
     *
     * If [actionMethod] is "POST", the client should submit the [actionFields] to the
     * [actionUrl] as form data. If "GET" (the default), the client should open the URL
     * in a browser or webview.
     *
     * @property message Human-readable message explaining what action is required
     * @property actionUrl URL where the user should complete the required action
     * @property actionMethod HTTP method to use when accessing the action URL (default "GET")
     * @property actionFields List of field names that should be submitted to the action URL
     *   when [actionMethod] is "POST"
     */
    data class ActionRequired(
        val message: String,
        val actionUrl: String,
        val actionMethod: String = "GET",
        val actionFields: List<String>? = null
    ) : Sep08PostTransactionResponse()

    /**
     * The transaction was rejected by the approval server.
     *
     * The transaction cannot be approved. The [error] field contains a human-readable
     * explanation of why the transaction was rejected.
     *
     * @property error Human-readable error message explaining the rejection reason
     */
    data class Rejected(
        val error: String
    ) : Sep08PostTransactionResponse()

    companion object {

        private const val STATUS_SUCCESS = "success"
        private const val STATUS_REVISED = "revised"
        private const val STATUS_PENDING = "pending"
        private const val STATUS_ACTION_REQUIRED = "action_required"
        private const val STATUS_REJECTED = "rejected"

        /**
         * Parses a JSON response from the SEP-8 approval server into the appropriate
         * [Sep08PostTransactionResponse] variant.
         *
         * The JSON must contain a `status` field with one of the recognized values:
         * `"success"`, `"revised"`, `"pending"`, `"action_required"`, or `"rejected"`.
         * Required fields are validated based on the status type.
         *
         * @param json The JSON object returned by the approval server
         * @return The parsed response as the appropriate sealed class variant
         * @throws Sep08InvalidTransactionResponseException if the status field is missing
         *   or unrecognized, or if required fields for the given status are missing
         */
        fun fromJson(json: JsonObject): Sep08PostTransactionResponse {
            val status = json["status"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep08InvalidTransactionResponseException(
                    "Missing 'status' field in approval server response"
                )

            return when (status) {
                STATUS_SUCCESS -> parseSuccess(json)
                STATUS_REVISED -> parseRevised(json)
                STATUS_PENDING -> parsePending(json)
                STATUS_ACTION_REQUIRED -> parseActionRequired(json)
                STATUS_REJECTED -> parseRejected(json)
                else -> throw Sep08InvalidTransactionResponseException(
                    "Unknown status '$status' in approval server response"
                )
            }
        }

        private fun parseSuccess(json: JsonObject): Success {
            val tx = json["tx"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep08InvalidTransactionResponseException(
                    "Missing required 'tx' field in 'success' response"
                )
            val message = json["message"]?.jsonPrimitive?.contentOrNull
            return Success(tx = tx, message = message)
        }

        private fun parseRevised(json: JsonObject): Revised {
            val tx = json["tx"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep08InvalidTransactionResponseException(
                    "Missing required 'tx' field in 'revised' response"
                )
            val message = json["message"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep08InvalidTransactionResponseException(
                    "Missing required 'message' field in 'revised' response"
                )
            return Revised(tx = tx, message = message)
        }

        private fun parsePending(json: JsonObject): Pending {
            val timeout = json["timeout"]?.jsonPrimitive?.intOrNull ?: 0
            val message = json["message"]?.jsonPrimitive?.contentOrNull
            return Pending(timeout = timeout, message = message)
        }

        private fun parseActionRequired(json: JsonObject): ActionRequired {
            val message = json["message"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep08InvalidTransactionResponseException(
                    "Missing required 'message' field in 'action_required' response"
                )
            val actionUrl = json["action_url"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep08InvalidTransactionResponseException(
                    "Missing required 'action_url' field in 'action_required' response"
                )
            val actionMethod = json["action_method"]?.jsonPrimitive?.contentOrNull ?: "GET"
            val actionFields = json["action_fields"]?.jsonArray?.map {
                it.jsonPrimitive.content
            }
            return ActionRequired(
                message = message,
                actionUrl = actionUrl,
                actionMethod = actionMethod,
                actionFields = actionFields
            )
        }

        private fun parseRejected(json: JsonObject): Rejected {
            val error = json["error"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep08InvalidTransactionResponseException(
                    "Missing required 'error' field in 'rejected' response"
                )
            return Rejected(error = error)
        }
    }
}
