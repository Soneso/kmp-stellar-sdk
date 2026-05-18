// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

import com.soneso.stellar.sdk.sep.common.sanitizeAnchorString
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Aggregate refund details for a SEP-31 transaction.
 *
 * Captures the total amount refunded to the Sending Anchor, the total fee charged
 * for processing those refunds, and the list of individual on-chain payments that
 * compose the refund. The [amountRefunded] and [amountFee] aggregates must equal
 * the sums of the corresponding fields across [payments].
 *
 * The aggregate amount fields are `String` (not numeric) because SEP-31 amounts
 * must preserve decimal precision.
 *
 * ## Usage
 *
 * ```kotlin
 * val response = sep31Service.getTransaction(id = "tx-id", jwt = jwt)
 * val refunds = response.refunds ?: return
 * println("Total refunded: ${refunds.amountRefunded} (fees: ${refunds.amountFee})")
 * for (payment in refunds.payments) {
 *     println("  txn ${payment.id}: ${payment.amount} (fee ${payment.fee})")
 * }
 * ```
 *
 * @property amountRefunded Total amount refunded across all [payments], in units of `amount_in_asset`.
 * @property amountFee Total fee charged for processing all refund [payments], in units of `amount_in_asset`.
 * @property payments Individual refund payments composing the aggregate.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#refunds-object-schema">SEP-0031 Refunds Object Schema</a>
 */
data class Sep31Refunds(
    val amountRefunded: String,
    val amountFee: String,
    val payments: List<Sep31RefundPayment>
) {
    companion object {
        /**
         * Parses a SEP-31 `refunds` JSON object into a [Sep31Refunds].
         *
         * @param json The `refunds` JSON object from a transaction response.
         * @return The parsed refund aggregate.
         * @throws Sep31InvalidResponseException if `amount_refunded`, `amount_fee`, or `payments` is missing or the body is malformed.
         */
        fun fromJson(json: JsonObject): Sep31Refunds {
            try {
                val amountRefunded = json["amount_refunded"]?.jsonPrimitive?.contentOrNull
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'amount_refunded' field in SEP-31 refunds"
                    )
                val amountFee = json["amount_fee"]?.jsonPrimitive?.contentOrNull
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'amount_fee' field in SEP-31 refunds"
                    )
                val paymentsArray = json["payments"] as? JsonArray
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'payments' field in SEP-31 refunds"
                    )
                val payments = paymentsArray.map { element ->
                    val obj = (element as? JsonObject)
                        ?: throw Sep31InvalidResponseException(
                            "Malformed SEP-31 refund payment: expected JSON object"
                        )
                    Sep31RefundPayment.fromJson(obj)
                }
                return Sep31Refunds(
                    amountRefunded = amountRefunded,
                    amountFee = amountFee,
                    payments = payments
                )
            } catch (e: Sep31InvalidResponseException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw Sep31InvalidResponseException(
                    "Malformed SEP-31 refunds: ${sanitizeAnchorString(e.message)}"
                )
            } catch (e: SerializationException) {
                throw Sep31InvalidResponseException(
                    "Malformed SEP-31 refunds: ${sanitizeAnchorString(e.message)}"
                )
            }
        }
    }
}
