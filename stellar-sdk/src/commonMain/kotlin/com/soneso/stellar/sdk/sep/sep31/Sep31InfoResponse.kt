// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

import com.soneso.stellar.sdk.sep.common.sanitizeAnchorString
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import kotlinx.serialization.json.JsonObject

/**
 * Response payload returned by the SEP-31 `GET /info` endpoint.
 *
 * Lists every Stellar asset the Receiving Anchor supports for cross-border payments,
 * keyed by asset code. Each entry describes that asset's transaction limits, fee
 * configuration, SEP-12 customer requirements, SEP-38 quote availability, and the
 * funding methods the anchor can use to deliver the off-chain asset.
 *
 * Callers typically look up a specific asset by code, then inspect the per-asset
 * configuration before constructing a [Sep31PostTransactionsRequest].
 *
 * ## Usage
 *
 * ```kotlin
 * val info = sep31Service.info(jwt = jwt)
 * val usdc = info.receiveAssets["USDC"] ?: error("Anchor does not accept USDC")
 * println("Min: ${usdc.minAmount}  Max: ${usdc.maxAmount}")
 * println("Sender types: ${usdc.sep12Info.senderTypes}")
 * ```
 *
 * ## Example JSON
 *
 * ```json
 * {
 *   "receive": {
 *     "USDC": {
 *       "min_amount": 0.1,
 *       "max_amount": 1000,
 *       "sep12": { "sender": { "types": {} }, "receiver": { "types": {} } }
 *     }
 *   }
 * }
 * ```
 *
 * @property receiveAssets Map of asset code to per-asset configuration. Empty if the
 *   anchor does not currently accept any assets.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#get-info">SEP-0031 GET Info</a>
 */
public data class Sep31InfoResponse(
    val receiveAssets: Map<String, Sep31ReceiveAssetInfo>
) {
    public companion object {
        /**
         * Parses the `GET /info` JSON response into a [Sep31InfoResponse].
         *
         * The `receive` object is optional at the top level. A response with no
         * `receive` key, or with `receive` set to an empty object, is parsed as a
         * [Sep31InfoResponse] with an empty [receiveAssets] map (this is rare in
         * production but spec-conformant).
         *
         * @param json The JSON object returned by the Receiving Anchor.
         * @return The parsed info response.
         * @throws Sep31InvalidResponseException when a `receive[<code>]` entry is malformed.
         */
        public fun fromJson(json: JsonObject): Sep31InfoResponse = sep31Rewrap("Malformed SEP-31 info response") {
            val receiveObject = json["receive"] as? JsonObject
                ?: return@sep31Rewrap Sep31InfoResponse(receiveAssets = emptyMap())
            val out = LinkedHashMap<String, Sep31ReceiveAssetInfo>(receiveObject.size)
            for ((code, element) in receiveObject) {
                val assetObject = (element as? JsonObject)
                    ?: throw Sep31InvalidResponseException(
                        "Malformed entry for asset code '${sanitizeAnchorString(code)}' in SEP-31 info response"
                    )
                out[code] = Sep31ReceiveAssetInfo.fromJson(assetObject)
            }
            Sep31InfoResponse(receiveAssets = out)
        }
    }
}
