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
 * Single line item within a SEP-31 fee breakdown.
 *
 * Each instance describes one component of the total fee charged by the Receiving
 * Anchor — for example an ACH fee, a service fee, or a country-specific
 * conciliation fee. When [Sep31FeeDetails.details] is non-null the sum of every
 * line item's [amount] equals [Sep31FeeDetails.total].
 *
 * The [amount] field is `String` (not numeric) because SEP-31 amounts must
 * preserve decimal precision. Callers convert to a domain-specific numeric type
 * (for example `BigDecimal` on JVM) at the application boundary.
 *
 * ## Usage
 *
 * ```kotlin
 * val response = sep31Service.getTransaction(id = "tx-id", jwt = jwt)
 * for (line in response.feeDetails?.details.orEmpty()) {
 *     println("${line.name}: ${line.amount} - ${line.description ?: "no description"}")
 * }
 * ```
 *
 * @property name Human-readable name of the fee component (for example "ACH fee").
 * @property amount Amount of this fee component, in units of [Sep31FeeDetails.asset]. Preserve as `String` to avoid floating-point precision loss.
 * @property description Optional longer description of the fee component.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#fee-details-object-schema">SEP-0031 Fee Details Object Schema</a>
 */
data class Sep31FeeDetailsDetails(
    val name: String,
    val amount: String,
    val description: String? = null
) {
    companion object {
        /**
         * Parses a fee-details line item JSON object into a [Sep31FeeDetailsDetails].
         *
         * @param json The JSON object describing one fee line item.
         * @return The parsed line item.
         * @throws Sep31InvalidResponseException if `name` or `amount` is missing or the body is malformed.
         */
        fun fromJson(json: JsonObject): Sep31FeeDetailsDetails {
            try {
                val name = json["name"]?.jsonPrimitive?.contentOrNull
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'name' field in SEP-31 fee details line item"
                    )
                val amount = json["amount"]?.jsonPrimitive?.contentOrNull
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'amount' field in SEP-31 fee details line item"
                    )
                return Sep31FeeDetailsDetails(
                    name = name,
                    amount = amount,
                    description = json["description"]?.jsonPrimitive?.contentOrNull
                )
            } catch (e: Sep31InvalidResponseException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw Sep31InvalidResponseException(
                    "Malformed SEP-31 fee details line item: ${sanitizeAnchorString(e.message)}"
                )
            } catch (e: SerializationException) {
                throw Sep31InvalidResponseException(
                    "Malformed SEP-31 fee details line item: ${sanitizeAnchorString(e.message)}"
                )
            }
        }
    }
}
