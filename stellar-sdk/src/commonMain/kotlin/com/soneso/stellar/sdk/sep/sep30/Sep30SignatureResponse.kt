// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep30

import com.soneso.stellar.sdk.sep.sep30.exceptions.Sep30InvalidResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents a SEP-30 transaction signature response.
 *
 * Returned when requesting the recovery server to sign a transaction via the
 * `POST /accounts/<address>/sign/<signing-address>` endpoint. Contains the base64-encoded
 * signature and the network passphrase confirming which Stellar network the signature
 * is valid for.
 *
 * ## Usage
 *
 * ```kotlin
 * val json = Json.parseToJsonElement(responseBody).jsonObject
 * val response = Sep30SignatureResponse.fromJson(json)
 *
 * println("Signature: ${response.signature}")
 * println("Network: ${response.networkPassphrase}")
 * ```
 *
 * ## Example JSON
 *
 * ```json
 * {
 *   "signature": "YpVelqPAKsYTP...",
 *   "network_passphrase": "Test SDF Network ; September 2015"
 * }
 * ```
 *
 * @property signature The base64-encoded signature for the transaction
 * @property networkPassphrase The network passphrase the signature is valid for
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0030.md">SEP-0030 Specification</a>
 */
data class Sep30SignatureResponse(
    val signature: String,
    val networkPassphrase: String
) {
    companion object {
        /**
         * Parses a JSON object into a [Sep30SignatureResponse].
         *
         * The JSON must contain `signature` and `network_passphrase` fields.
         *
         * ## JSON Field Mapping
         *
         * - `signature` -> [signature] (required)
         * - `network_passphrase` -> [networkPassphrase] (required)
         *
         * @param json The JSON object returned by the recovery server
         * @return The parsed signature response
         * @throws Sep30InvalidResponseException if any required field is missing
         */
        fun fromJson(json: JsonObject): Sep30SignatureResponse {
            val signature = json["signature"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep30InvalidResponseException(
                    "Missing required 'signature' field in SEP-30 signature response"
                )

            val networkPassphrase = json["network_passphrase"]?.jsonPrimitive?.contentOrNull
                ?: throw Sep30InvalidResponseException(
                    "Missing required 'network_passphrase' field in SEP-30 signature response"
                )

            return Sep30SignatureResponse(
                signature = signature,
                networkPassphrase = networkPassphrase
            )
        }
    }
}
