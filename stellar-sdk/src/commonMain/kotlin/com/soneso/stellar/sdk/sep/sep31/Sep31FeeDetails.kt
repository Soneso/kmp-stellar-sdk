// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fee breakdown applied to a SEP-31 transaction.
 *
 * Replaces the deprecated `amount_fee` / `amount_fee_asset` fields with a
 * structured object containing the aggregate fee ([total]), the fee asset
 * ([asset]), and an optional list of line items ([details]) that further
 * decompose the total.
 *
 * The [total] field is `String` (not numeric) because SEP-31 amounts must
 * preserve decimal precision; callers convert to a domain-specific numeric type
 * at the application boundary.
 *
 * ## Usage
 *
 * ```kotlin
 * val response = sep31Service.getTransaction(id = "tx-id", jwt = jwt)
 * val fees = response.feeDetails ?: return
 * println("Total fee: ${fees.total} ${fees.asset}")
 * for (line in fees.details.orEmpty()) {
 *     println("  ${line.name}: ${line.amount}")
 * }
 * ```
 *
 * @property total Aggregate fee charged in units of [asset]. Preserve as `String` to avoid floating-point precision loss.
 * @property asset SEP-38 Asset Identification Format string identifying the fee asset.
 * @property details Optional list of fee line items whose [Sep31FeeDetailsDetails.amount] values sum to [total]. `null` when the anchor does not decompose the fee.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#fee-details-object-schema">SEP-0031 Fee Details Object Schema</a>
 */
public data class Sep31FeeDetails(
    val total: String,
    val asset: String,
    val details: List<Sep31FeeDetailsDetails>? = null
) {
    public companion object {
        /**
         * Parses a SEP-31 `fee_details` JSON object into a [Sep31FeeDetails].
         *
         * @param json The `fee_details` JSON object from a transaction response.
         * @return The parsed fee breakdown.
         * @throws Sep31InvalidResponseException if `total` or `asset` is missing or the body is malformed.
         */
        public fun fromJson(json: JsonObject): Sep31FeeDetails = sep31Rewrap("Malformed SEP-31 fee details") {
            val total = json["total"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep31InvalidResponseException(
                    "missing required 'total' field in SEP-31 fee details"
                )
            val asset = json["asset"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep31InvalidResponseException(
                    "missing required 'asset' field in SEP-31 fee details"
                )
            val detailsArray = json["details"] as? JsonArray
            val details = detailsArray?.map { element ->
                val obj = (element as? JsonObject)
                    ?: throw Sep31InvalidResponseException(
                        "Malformed SEP-31 fee details line item: expected JSON object"
                    )
                Sep31FeeDetailsDetails.fromJson(obj)
            }
            Sep31FeeDetails(total = total, asset = asset, details = details)
        }
    }
}
