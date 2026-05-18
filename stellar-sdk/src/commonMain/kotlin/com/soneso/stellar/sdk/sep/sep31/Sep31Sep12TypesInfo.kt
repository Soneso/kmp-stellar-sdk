// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep31

import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * KYC type definitions for sender and receiver customers in a SEP-31 asset offering.
 *
 * Captures the parsed `sep12.sender.types` and `sep12.receiver.types` sub-maps from
 * the SEP-31 `GET /info` response. Each map associates a SEP-12 customer type
 * identifier (the value passed as the `type` parameter to SEP-12 `GET /customer`)
 * with a human-readable description supplied by the Receiving Anchor.
 *
 * The class is prefixed `Sep31` to keep all SEP-31 types under a single namespace
 * and to avoid naming collisions with the existing `com.soneso.stellar.sdk.sep.sep12`
 * package.
 *
 * ## Usage
 *
 * ```kotlin
 * val info = sep31Service.info(jwt = jwt)
 * val usdc = info.receiveAssets["USDC"] ?: return
 * for ((typeId, description) in usdc.sep12Info.senderTypes) {
 *     println("Sender type $typeId: $description")
 * }
 * ```
 *
 * ## Example JSON (excerpt from `GET /info`)
 *
 * ```json
 * {
 *   "sep12": {
 *     "sender": {
 *       "types": {
 *         "sep31-sender": { "description": "U.S. citizens limited to sending payments of less than $10,000 in value" }
 *       }
 *     },
 *     "receiver": {
 *       "types": {
 *         "sep31-receiver": { "description": "U.S. citizens receiving USD" }
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * @property senderTypes Map of SEP-12 sender type identifier to human-readable description. Empty when the anchor omits `sep12.sender.types`.
 * @property receiverTypes Map of SEP-12 receiver type identifier to human-readable description. Empty when the anchor omits `sep12.receiver.types`.
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md#get-info">SEP-0031 GET Info</a>
 */
public data class Sep31Sep12TypesInfo(
    val senderTypes: Map<String, String>,
    val receiverTypes: Map<String, String>
) {
    public companion object {
        /**
         * Parses a SEP-31 `sep12` JSON object into a [Sep31Sep12TypesInfo].
         *
         * The function tolerates missing `sender`, `receiver`, or `types` sub-objects
         * by treating them as empty maps — the anchor may legitimately omit either
         * side if no KYC is required for that role. Entries whose `description` is
         * missing or not a string are silently skipped.
         *
         * @param json The `sep12` JSON object from the SEP-31 `GET /info` response.
         * @return The parsed [Sep31Sep12TypesInfo].
         * @throws Sep31InvalidResponseException if the underlying JSON shape is malformed.
         */
        public fun fromJson(json: JsonObject): Sep31Sep12TypesInfo =
            sep31Rewrap("Malformed sep12 object in SEP-31 info response") {
                Sep31Sep12TypesInfo(
                    senderTypes = extractTypes(json["sender"]),
                    receiverTypes = extractTypes(json["receiver"])
                )
            }

        private fun extractTypes(roleElement: kotlinx.serialization.json.JsonElement?): Map<String, String> {
            val role = (roleElement as? JsonObject) ?: return emptyMap()
            val typesObject = (role["types"] as? JsonObject) ?: return emptyMap()
            val out = LinkedHashMap<String, String>(typesObject.size)
            for ((typeId, typeElement) in typesObject) {
                val typeObject = (typeElement as? JsonObject) ?: continue
                val description = typeObject["description"]?.jsonPrimitive?.contentOrNull
                if (description != null) {
                    out[typeId] = description
                }
            }
            return out
        }
    }
}
