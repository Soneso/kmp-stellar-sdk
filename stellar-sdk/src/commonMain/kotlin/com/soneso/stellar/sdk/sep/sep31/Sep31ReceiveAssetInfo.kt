// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

import com.soneso.stellar.sdk.sep.common.jsonElementToAny
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Per-asset configuration advertised by a Receiving Anchor in `GET /info`.
 *
 * Bundles transaction limits, fee configuration, SEP-12 customer type requirements,
 * SEP-38 quote availability, and supported funding methods for one Stellar asset
 * the anchor accepts for cross-border payments.
 *
 * Numeric configuration fields ([minAmount], [maxAmount], [feeFixed], [feePercent])
 * are parsed via `doubleOrNull` so the SDK tolerates anchors that emit either JSON
 * numbers (`100`) or quoted strings (`"100"`). These are anchor configuration values
 * (limits and percentages), not transaction amounts; floating-point representation
 * is acceptable for them. Transaction amount fields elsewhere in the SDK remain
 * `String?` to preserve decimal precision.
 *
 * Memo-type and funding-method strings are kept verbatim. The SDK does not validate
 * them against any allow-list because the SEP-31 spec is expected to introduce new
 * values over time.
 *
 * ## Usage
 *
 * ```kotlin
 * val info = sep31Service.info(jwt = jwt)
 * val usdc = info.receiveAssets["USDC"] ?: error("Anchor does not accept USDC")
 * println("Limits: ${usdc.minAmount} - ${usdc.maxAmount}")
 * println("Fee: fixed=${usdc.feeFixed}, percent=${usdc.feePercent}")
 * println("Quotes required: ${usdc.quotesRequired}")
 * println("Funding methods: ${usdc.fundingMethods}")
 * ```
 *
 * @property sep12Info SEP-12 customer type requirements for sender and receiver. Defaults
 *   to empty sender/receiver maps when the anchor omits the per-asset `sep12` object —
 *   anchors that require no KYC may leave it out entirely. Note: SEP-31 v3.1.0 marks
 *   the wire `sep12` field "(Deprecated, optional)", but the spec prose still recommends
 *   `sep12.sender.types` / `sep12.receiver.types` over the older flat
 *   [senderSep12Type] / [receiverSep12Type] fields, and SEP-12 defers customer-type
 *   discovery back to the calling protocol with no alternative. The SDK therefore
 *   treats this field as canonical (not `@Deprecated`) despite the schema marker.
 * @property minAmount Minimum amount the anchor accepts; `null` when unlimited.
 * @property maxAmount Maximum amount the anchor accepts; `null` when unlimited.
 * @property feeFixed Fixed fee component charged by the anchor; `null` when the fee model does not decompose into fixed + percent.
 * @property feePercent Percentage fee component charged by the anchor (in percentage points); `null` when the fee model does not decompose into fixed + percent.
 * @property senderSep12Type Deprecated single sender SEP-12 type identifier. Use [Sep31Sep12TypesInfo.senderTypes] instead.
 * @property receiverSep12Type Deprecated single receiver SEP-12 type identifier. Use [Sep31Sep12TypesInfo.receiverTypes] instead.
 * @property fields Deprecated per-transaction field requirements. Migrate to SEP-12 `PUT /customer` instead.
 * @property quotesSupported `true` if the anchor optionally accepts a SEP-38 `quote_id` for off-chain delivery.
 * @property quotesRequired `true` if the anchor requires a SEP-38 `quote_id` for off-chain delivery.
 * @property fundingMethods Methods the anchor supports for delivering the off-chain asset (for example `SEPA`, `SWIFT`). `null` when the anchor does not advertise any.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#get-info">SEP-0031 GET Info</a>
 */
public data class Sep31ReceiveAssetInfo(
    val sep12Info: Sep31Sep12TypesInfo,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val feeFixed: Double? = null,
    val feePercent: Double? = null,
    @Deprecated(
        message = "Deprecated in SEP-31 v1.2.0. Use sep12Info.senderTypes instead when any entries are present.",
        level = DeprecationLevel.WARNING
    )
    val senderSep12Type: String? = null,
    @Deprecated(
        message = "Deprecated in SEP-31 v1.2.0. Use sep12Info.receiverTypes instead when any entries are present.",
        level = DeprecationLevel.WARNING
    )
    val receiverSep12Type: String? = null,
    @Deprecated(
        message = "Deprecated in SEP-31 v2.5.0. Pass KYC via SEP-12 PUT /customer and reference sender_id/receiver_id instead.",
        level = DeprecationLevel.WARNING
    )
    val fields: Map<String, Any?>? = null,
    val quotesSupported: Boolean? = null,
    val quotesRequired: Boolean? = null,
    val fundingMethods: List<String>? = null
) {
    public companion object {
        /**
         * Parses a `receive[<code>]` JSON object from the SEP-31 `GET /info` response.
         *
         * Numeric fields tolerate either JSON numeric or string-numeric encodings.
         * The deprecated `fields` map is preserved verbatim as a `Map<String, Any?>`
         * with primitive leaves so legacy callers can inspect the requested per-transaction
         * fields without depending on `kotlinx.serialization.json.JsonElement` types.
         *
         * @param json The per-asset JSON object from the `receive` map.
         * @return The parsed [Sep31ReceiveAssetInfo].
         * @throws Sep31InvalidResponseException when required fields are missing or malformed.
         */
        @Suppress("DEPRECATION")
        public fun fromJson(json: JsonObject): Sep31ReceiveAssetInfo =
            sep31Rewrap("Malformed receive asset info in SEP-31 response") {
                // Per SEP-31 v3.1.0, the schema row marks the per-asset `sep12` object
                // "(Deprecated, optional)" — but the surrounding spec prose still
                // recommends `sep12.sender.types` / `sep12.receiver.types` for
                // customer-type discovery with no documented alternative, so we treat
                // it as canonical. The "optional" half is real: an anchor that requires
                // no KYC may legitimately omit the key, hence the empty-map default
                // below. Treating absence as a parse failure would reject valid responses.
                val sep12Object = json["sep12"] as? JsonObject
                val sep12Info = if (sep12Object != null) {
                    Sep31Sep12TypesInfo.fromJson(sep12Object)
                } else {
                    Sep31Sep12TypesInfo(senderTypes = emptyMap(), receiverTypes = emptyMap())
                }

                val fundingMethodsArray = json["funding_methods"] as? JsonArray
                val fundingMethods = fundingMethodsArray?.mapNotNull { element ->
                    (element as? JsonPrimitive)?.contentOrNull
                }

                val fieldsObject = json["fields"] as? JsonObject
                val fields = fieldsObject?.let { obj ->
                    @Suppress("UNCHECKED_CAST")
                    jsonElementToAny(obj) as Map<String, Any?>
                }

                Sep31ReceiveAssetInfo(
                    sep12Info = sep12Info,
                    minAmount = (json["min_amount"] as? JsonPrimitive)?.doubleOrNull,
                    maxAmount = (json["max_amount"] as? JsonPrimitive)?.doubleOrNull,
                    feeFixed = (json["fee_fixed"] as? JsonPrimitive)?.doubleOrNull,
                    feePercent = (json["fee_percent"] as? JsonPrimitive)?.doubleOrNull,
                    senderSep12Type = json["sender_sep12_type"]?.jsonPrimitive?.contentOrNull,
                    receiverSep12Type = json["receiver_sep12_type"]?.jsonPrimitive?.contentOrNull,
                    fields = fields,
                    quotesSupported = json["quotes_supported"]?.jsonPrimitive?.booleanOrNull,
                    quotesRequired = json["quotes_required"]?.jsonPrimitive?.booleanOrNull,
                    fundingMethods = fundingMethods
                )
            }
    }
}
