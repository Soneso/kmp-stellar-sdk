// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

/**
 * Request body for the SEP-31 `POST /transactions` endpoint.
 *
 * Initiates a cross-border payment by describing the amount, asset, optional SEP-38
 * quote, optional SEP-12 customer ids, optional refund memo, and optional funding
 * method. The Receiving Anchor responds with a [Sep31PostTransactionsResponse]
 * containing the on-chain payment instructions.
 *
 * Field naming follows the spec: every JSON key is snake_case, every Kotlin property
 * is camelCase. The [toJson] helper omits null entries and preserves insertion order
 * so test fixtures and on-the-wire bodies remain byte-stable.
 *
 * ## Usage
 *
 * ```kotlin
 * val request = Sep31PostTransactionsRequest(
 *     amount = 100.0,
 *     assetCode = "USDC",
 *     fundingMethod = "SWIFT",
 *     assetIssuer = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
 *     destinationAsset = "iso4217:BRL",
 *     quoteId = "11111111-1111-1111-1111-111111111111",
 *     senderId = "22222222-2222-2222-2222-222222222222",
 *     receiverId = "33333333-3333-3333-3333-333333333333",
 * )
 * val response = sep31Service.postTransactions(request, jwt)
 * ```
 *
 * @property amount Amount of the Stellar asset to send to the Receiving Anchor.
 * @property assetCode Code of the Stellar asset; must match a key in the `/info` response.
 * @property fundingMethod Funding method the Receiving Anchor will use to deliver the asset
 *   (for example `SWIFT`, `SEPA`). Must match a value advertised in `/info`. Required by
 *   SEP-31 v3.1.0 so the anchor can determine the KYC fields to collect.
 * @property assetIssuer Issuer of the Stellar asset; omit when the Receiving Anchor itself issues the asset.
 * @property destinationAsset SEP-38 asset identification string for the off-chain delivery asset. Omit when not using SEP-38.
 * @property quoteId SEP-38 firm quote id returned by `POST /quote`. Required when [destinationAsset] is set with an off-chain delivery rate.
 * @property senderId SEP-12 customer id of the Sending Client. Required when the Receiving Anchor requires SEP-12 KYC on senders.
 * @property receiverId SEP-12 customer id of the Receiving Client. Required when the Receiving Anchor requires SEP-12 KYC on receivers.
 * @property fields Deprecated per-transaction field map; migrate to SEP-12 `PUT /customer` instead.
 * @property lang ISO 639-1 language code for human-readable error and field descriptions.
 * @property refundMemo Memo the Receiving Anchor must attach when issuing refunds. If specified, [refundMemoType] must also be specified.
 * @property refundMemoType Type of [refundMemo] (`id`, `text`, or `hash`). If specified, [refundMemo] must also be specified.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#post-transactions">SEP-0031 POST Transactions</a>
 */
data class Sep31PostTransactionsRequest(
    val amount: Double,
    val assetCode: String,
    val fundingMethod: String,
    val assetIssuer: String? = null,
    val destinationAsset: String? = null,
    val quoteId: String? = null,
    val senderId: String? = null,
    val receiverId: String? = null,
    @Deprecated(
        message = "Deprecated in SEP-31 v2.5.0. Pass KYC via SEP-12 PUT /customer and reference sender_id/receiver_id instead.",
        level = DeprecationLevel.WARNING
    )
    val fields: Map<String, Any?>? = null,
    val lang: String? = null,
    val refundMemo: String? = null,
    val refundMemoType: String? = null,
) {
    /**
     * Returns a snake_case `Map<String, Any?>` ready for JSON encoding.
     *
     * The returned map is a [LinkedHashMap] so insertion order is preserved across
     * invocations; this gives deterministic byte-equal output for fixtures and
     * regression tests. Null-valued optional fields are omitted entirely rather
     * than serialized as JSON `null`, matching the spec's optional-field semantics.
     *
     * @return The ordered key-value map suitable for passing to `JsonElement` conversion helpers.
     */
    @Suppress("DEPRECATION")
    fun toJson(): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        result["amount"] = amount
        result["asset_code"] = assetCode
        result["funding_method"] = fundingMethod
        if (assetIssuer != null) result["asset_issuer"] = assetIssuer
        if (destinationAsset != null) result["destination_asset"] = destinationAsset
        if (quoteId != null) result["quote_id"] = quoteId
        if (senderId != null) result["sender_id"] = senderId
        if (receiverId != null) result["receiver_id"] = receiverId
        if (fields != null) result["fields"] = fields
        if (lang != null) result["lang"] = lang
        if (refundMemo != null) result["refund_memo"] = refundMemo
        if (refundMemoType != null) result["refund_memo_type"] = refundMemoType
        return result
    }
}
