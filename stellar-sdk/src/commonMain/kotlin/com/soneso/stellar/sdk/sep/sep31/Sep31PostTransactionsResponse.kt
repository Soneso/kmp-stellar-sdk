// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Response payload returned by SEP-31 `POST /transactions`.
 *
 * Carries the persistent transaction [id] and — when the anchor has already
 * determined the on-chain payment instructions — the Stellar account and memo
 * the Sending Anchor must use. The Stellar fields are optional: per spec
 * "Success (201 Created)", absent values indicate the anchor is still processing
 * the request. The transaction is in `pending_receiver` status; the anchor will
 * advance it to `pending_sender` once payment fields are populated, or to `error`
 * if the transaction cannot proceed. The Sending Anchor should poll or register a
 * callback until one of those two transitions occurs.
 *
 * Both HTTP 200 and HTTP 201 responses are accepted as success by the SDK.
 *
 * ## Usage
 *
 * ```kotlin
 * val response = sep31Service.postTransactions(request, jwt)
 * if (response.stellarAccountId != null) {
 *     println("Send payment to ${response.stellarAccountId} with memo ${response.stellarMemo}")
 * } else {
 *     println("Anchor is still processing; poll GET /transactions/${response.id}")
 * }
 * ```
 *
 * ## Example JSON
 *
 * ```json
 * {
 *   "id": "11111111-1111-1111-1111-111111111111",
 *   "stellar_account_id": "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H",
 *   "stellar_memo_type": "hash",
 *   "stellar_memo": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
 * }
 * ```
 *
 * @property id Persistent transaction identifier to use for `GET /transactions/:id` and `PATCH /transactions/:id`.
 * @property stellarAccountId Receiving Anchor's Stellar account to send the on-chain payment to. `null` while the anchor is still processing.
 * @property stellarMemoType Type of [stellarMemo] (`text`, `hash`, or `id`). Verbatim string; the SDK does not validate against an allow-list.
 * @property stellarMemo Memo to attach to the on-chain payment. `null` while the anchor is still processing.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#post-transactions">SEP-0031 POST Transactions</a>
 */
public data class Sep31PostTransactionsResponse(
    val id: String,
    val stellarAccountId: String? = null,
    val stellarMemoType: String? = null,
    val stellarMemo: String? = null
) {
    public companion object {
        /**
         * Parses the `POST /transactions` JSON response into a [Sep31PostTransactionsResponse].
         *
         * @param json The JSON object returned by the Receiving Anchor.
         * @return The parsed response.
         * @throws Sep31InvalidResponseException if the `id` field is missing or the body is malformed.
         */
        public fun fromJson(json: JsonObject): Sep31PostTransactionsResponse =
            sep31Rewrap("Malformed SEP-31 post transactions response") {
                val id = json["id"]?.jsonPrimitive?.contentOrNull
                    ?: throw Sep31InvalidResponseException(
                        "missing required 'id' field in SEP-31 post transactions response"
                    )
                Sep31PostTransactionsResponse(
                    id = id,
                    stellarAccountId = json["stellar_account_id"]?.jsonPrimitive?.contentOrNull,
                    stellarMemoType = json["stellar_memo_type"]?.jsonPrimitive?.contentOrNull,
                    stellarMemo = json["stellar_memo"]?.jsonPrimitive?.contentOrNull
                )
            }
    }
}
