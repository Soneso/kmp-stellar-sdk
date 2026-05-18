// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

import com.soneso.stellar.sdk.sep.common.jsonElementToAny
import com.soneso.stellar.sdk.sep.common.sanitizeAnchorString
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Full state of a SEP-31 cross-border payment transaction.
 *
 * Returned by `GET /transactions/:id` and `PATCH /transactions/:id`. Captures the
 * transaction's lifecycle status, on-chain payment instructions, amount and fee
 * breakdown, refund aggregate, and any required-info-update prompts.
 *
 * Amount fields ([amountIn], [amountInAsset], [amountOut], [amountOutAsset],
 * [amountFee], [amountFeeAsset]) are `String?` (not numeric) because SEP-31
 * amounts must preserve decimal precision. Callers convert to a domain-specific
 * numeric type at the application boundary.
 *
 * The [status] field is kept as raw `String`. Use [Sep31TransactionStatus.fromString]
 * for typed status handling. The SDK does not internally compare against the enum
 * so a new spec status does not require an SDK release.
 *
 * [statusEta] is `Long?` (not `Int?`) because anchors that emit multi-day ETAs in
 * seconds exceed `Int` range; the SEP-31 spec types this as `number`.
 *
 * The [requiredInfoUpdates] map is parsed with primitive-leaf semantics: every
 * value at every depth is either a `String`, `Boolean`, `Long`, `Double`, or
 * `null`, never a `kotlinx.serialization.json.JsonElement` instance. Callers can
 * walk the structure with simple `as Map<String, Any?>` and `as List<Any?>` casts.
 *
 * ## Usage
 *
 * ```kotlin
 * val response = sep31Service.getTransaction(id = "11111111-1111-1111-1111-111111111111", jwt = jwt)
 * println("Status: ${response.status}")
 * println("Amount in: ${response.amountIn} ${response.amountInAsset ?: "(stellar asset)"}")
 * response.feeDetails?.let { println("Fees: ${it.total} ${it.asset}") }
 * ```
 *
 * @property id Transaction identifier matching the `id` returned by `POST /transactions`.
 * @property status Lifecycle status as a raw string. Use [Sep31TransactionStatus.fromString] to map to the enum.
 * @property statusEta Estimated seconds until the next status change. `null` when not reported.
 * @property statusMessage Human-readable status description.
 * @property amountIn Amount of the Stellar asset received or to be received by the Receiving Anchor.
 * @property amountInAsset SEP-38 Asset Identification of the inbound asset.
 * @property amountOut Amount sent or to be sent by the Receiving Anchor to the Receiving Client.
 * @property amountOutAsset SEP-38 Asset Identification of the delivered asset.
 * @property amountFee Deprecated aggregate fee charged by the anchor. Use [feeDetails] instead.
 * @property amountFeeAsset Deprecated fee asset. Use [feeDetails] instead.
 * @property feeDetails Structured fee breakdown (replaces [amountFee] / [amountFeeAsset]).
 * @property quoteId SEP-38 quote id used to create this transaction.
 * @property stellarAccountId Receiving Anchor's Stellar account that the Sending Anchor pays to.
 * @property stellarMemoType Type of [stellarMemo] (`text`, `hash`, or `id`).
 * @property stellarMemo Memo attached to the on-chain payment.
 * @property startedAt UTC ISO 8601 timestamp when the transaction was created.
 * @property updatedAt UTC ISO 8601 timestamp of the last status transition.
 * @property completedAt UTC ISO 8601 completion timestamp.
 * @property stellarTransactionId Stellar transaction hash of the on-chain payment initiating the transfer.
 * @property externalTransactionId External (off-chain) transaction identifier for the final delivery.
 * @property refunded Deprecated boolean indicating full refund. Use [refunds] instead.
 * @property refunds Structured refund aggregate.
 * @property requiredInfoMessage Human-readable message accompanying [requiredInfoUpdates].
 * @property requiredInfoUpdates Fields requiring updates from the Sending Anchor. Leaves are primitive Kotlin types only.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#transaction-object">SEP-0031 Transaction Object</a>
 */
data class Sep31TransactionResponse(
    val id: String,
    val status: String,
    val statusEta: Long? = null,
    val statusMessage: String? = null,
    val amountIn: String? = null,
    val amountInAsset: String? = null,
    val amountOut: String? = null,
    val amountOutAsset: String? = null,
    @Deprecated(
        message = "Deprecated in SEP-31 v2.6.0. Use feeDetails instead.",
        level = DeprecationLevel.WARNING
    )
    val amountFee: String? = null,
    @Deprecated(
        message = "Deprecated in SEP-31 v2.6.0. Use feeDetails instead.",
        level = DeprecationLevel.WARNING
    )
    val amountFeeAsset: String? = null,
    val feeDetails: Sep31FeeDetails? = null,
    val quoteId: String? = null,
    val stellarAccountId: String? = null,
    val stellarMemoType: String? = null,
    val stellarMemo: String? = null,
    val startedAt: String? = null,
    val updatedAt: String? = null,
    val completedAt: String? = null,
    val stellarTransactionId: String? = null,
    val externalTransactionId: String? = null,
    @Deprecated(
        message = "Deprecated in SEP-31 v2.5.0. Use refunds instead.",
        level = DeprecationLevel.WARNING
    )
    val refunded: Boolean? = null,
    val refunds: Sep31Refunds? = null,
    val requiredInfoMessage: String? = null,
    val requiredInfoUpdates: Map<String, Any?>? = null
) {
    companion object {
        /**
         * Parses a SEP-31 transaction-response JSON payload into a [Sep31TransactionResponse].
         *
         * The function requires the wrapped shape `{"transaction": {...}}` defined by
         * the SEP-31 spec "GET Transaction" section. A bare flat object without the
         * `transaction` wrapper is rejected with [Sep31InvalidResponseException]:
         * accepting the flat form would teach callers a non-spec contract that
         * masks anchor non-conformance.
         *
         * @param json The full JSON object returned by the anchor, including the `transaction` wrapper.
         * @return The parsed transaction response.
         * @throws Sep31InvalidResponseException if the `transaction` wrapper is missing, any required field is absent, or the body is malformed.
         */
        @Suppress("DEPRECATION")
        fun fromJson(json: JsonObject): Sep31TransactionResponse {
            try {
                val txObject = (json["transaction"] as? JsonObject)
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'transaction' key in SEP-31 transaction response"
                    )

                val id = txObject["id"]?.jsonPrimitive?.contentOrNull
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'id' field in SEP-31 transaction response"
                    )
                val status = txObject["status"]?.jsonPrimitive?.contentOrNull
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'status' field in SEP-31 transaction response"
                    )

                val feeDetailsObject = txObject["fee_details"] as? JsonObject
                val feeDetails = feeDetailsObject?.let { Sep31FeeDetails.fromJson(it) }

                val refundsObject = txObject["refunds"] as? JsonObject
                val refunds = refundsObject?.let { Sep31Refunds.fromJson(it) }

                val requiredInfoUpdatesObject = txObject["required_info_updates"] as? JsonObject
                val requiredInfoUpdates = requiredInfoUpdatesObject?.let { obj ->
                    @Suppress("UNCHECKED_CAST")
                    jsonElementToAny(obj) as Map<String, Any?>
                }

                return Sep31TransactionResponse(
                    id = id,
                    status = status,
                    statusEta = txObject["status_eta"]?.jsonPrimitive?.longOrNull,
                    statusMessage = txObject["status_message"]?.jsonPrimitive?.contentOrNull,
                    amountIn = txObject["amount_in"]?.jsonPrimitive?.contentOrNull,
                    amountInAsset = txObject["amount_in_asset"]?.jsonPrimitive?.contentOrNull,
                    amountOut = txObject["amount_out"]?.jsonPrimitive?.contentOrNull,
                    amountOutAsset = txObject["amount_out_asset"]?.jsonPrimitive?.contentOrNull,
                    amountFee = txObject["amount_fee"]?.jsonPrimitive?.contentOrNull,
                    amountFeeAsset = txObject["amount_fee_asset"]?.jsonPrimitive?.contentOrNull,
                    feeDetails = feeDetails,
                    quoteId = txObject["quote_id"]?.jsonPrimitive?.contentOrNull,
                    stellarAccountId = txObject["stellar_account_id"]?.jsonPrimitive?.contentOrNull,
                    stellarMemoType = txObject["stellar_memo_type"]?.jsonPrimitive?.contentOrNull,
                    stellarMemo = txObject["stellar_memo"]?.jsonPrimitive?.contentOrNull,
                    startedAt = txObject["started_at"]?.jsonPrimitive?.contentOrNull,
                    updatedAt = txObject["updated_at"]?.jsonPrimitive?.contentOrNull,
                    completedAt = txObject["completed_at"]?.jsonPrimitive?.contentOrNull,
                    stellarTransactionId = txObject["stellar_transaction_id"]?.jsonPrimitive?.contentOrNull,
                    externalTransactionId = txObject["external_transaction_id"]?.jsonPrimitive?.contentOrNull,
                    refunded = txObject["refunded"]?.jsonPrimitive?.booleanOrNull,
                    refunds = refunds,
                    requiredInfoMessage = txObject["required_info_message"]?.jsonPrimitive?.contentOrNull,
                    requiredInfoUpdates = requiredInfoUpdates
                )
            } catch (e: Sep31InvalidResponseException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw Sep31InvalidResponseException(
                    "Malformed SEP-31 transaction response: ${sanitizeAnchorString(e.message)}"
                )
            } catch (e: SerializationException) {
                throw Sep31InvalidResponseException(
                    "Malformed SEP-31 transaction response: ${sanitizeAnchorString(e.message)}"
                )
            }
        }
    }
}
