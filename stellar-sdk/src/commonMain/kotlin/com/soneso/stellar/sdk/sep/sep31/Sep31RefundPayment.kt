// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

import com.soneso.stellar.sdk.sep.common.sanitizeAnchorString
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Single on-chain refund payment within a SEP-31 [Sep31Refunds] aggregate.
 *
 * Each instance describes one Stellar transaction the Receiving Anchor used to
 * send funds back to the Sending Anchor. Multiple refund payments may exist for
 * a single SEP-31 transaction; the aggregate is captured in [Sep31Refunds].
 *
 * The [amount] and [fee] fields are `String` (not numeric) because SEP-31 amounts
 * must preserve decimal precision; callers convert to a domain-specific numeric
 * type at the application boundary.
 *
 * @property id Stellar transaction hash of the refund payment. Not guaranteed to be unique across refund payments.
 * @property amount Amount returned to the Sending Anchor in this payment, in units of `amount_in_asset`.
 * @property fee Fee charged for processing this refund payment, in units of `amount_in_asset`.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#refund-payment-object-schema">SEP-0031 Refund Payment Object Schema</a>
 */
data class Sep31RefundPayment(
    val id: String,
    val amount: String,
    val fee: String
) {
    companion object {
        /**
         * Parses a refund-payment JSON object into a [Sep31RefundPayment].
         *
         * @param json The JSON object describing one refund payment.
         * @return The parsed refund payment.
         * @throws Sep31InvalidResponseException if `id`, `amount`, or `fee` is missing or the body is malformed.
         */
        fun fromJson(json: JsonObject): Sep31RefundPayment {
            try {
                val id = json["id"]?.jsonPrimitive?.contentOrNull
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'id' field in SEP-31 refund payment"
                    )
                val amount = json["amount"]?.jsonPrimitive?.contentOrNull
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'amount' field in SEP-31 refund payment"
                    )
                val fee = json["fee"]?.jsonPrimitive?.contentOrNull
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'fee' field in SEP-31 refund payment"
                    )
                return Sep31RefundPayment(id = id, amount = amount, fee = fee)
            } catch (e: Sep31InvalidResponseException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw Sep31InvalidResponseException(
                    "Malformed SEP-31 refund payment: ${sanitizeAnchorString(e.message)}"
                )
            } catch (e: SerializationException) {
                throw Sep31InvalidResponseException(
                    "Malformed SEP-31 refund payment: ${sanitizeAnchorString(e.message)}"
                )
            }
        }
    }
}
