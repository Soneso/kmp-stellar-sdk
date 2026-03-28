// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep45

import com.soneso.stellar.sdk.Util
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Represents a parsed SEP-45 authentication token (JWT).
 *
 * This class parses JWT tokens returned from Stellar SEP-45 authentication endpoints
 * and exposes standard JWT claims and SEP-45 specific claims. It performs lenient parsing:
 * if the JWT is malformed, it returns a Sep45AuthToken with only the raw token string populated,
 * allowing graceful degradation in applications.
 *
 * ## Standard JWT Claims (RFC 7519)
 * - [issuer]: Issuer (iss) - the authentication server's domain
 * - [account]: Subject (sub) - the authenticated contract account ID (C... address)
 * - [issuedAt]: Issued At (iat) - Unix timestamp when token was created
 * - [expiresAt]: Expiration Time (exp) - Unix timestamp when token expires
 *
 * ## SEP-45 Specific Claims
 * - [clientDomain]: Client domain for domain-signed authentication
 *
 * ## Differences from SEP-10 AuthToken
 * - [account] is always a contract address (C...) instead of G.../M... addresses
 * - No memo support (contract accounts don't use memos)
 * - No jti claim (not required by SEP-45)
 *
 * ## Security Considerations
 * - This parser does NOT verify JWT signatures (per SEP-45 spec)
 * - SEP-45 clients receive signed tokens over HTTPS and use them as bearer tokens
 * - Signature verification is the server's responsibility
 * - Always validate token expiry using [isExpired] before use
 *
 * ## Example Usage
 * ```kotlin
 * // Parse token
 * val authToken = Sep45AuthToken.parse(jwtString)
 *
 * // Check expiry
 * if (authToken.isExpired()) {
 *     println("Token expired at epoch ${authToken.expiresAt}")
 *     return
 * }
 *
 * // Access claims
 * val contractId = authToken.account  // "CCONTRACT..."
 * println("Authenticated by: ${authToken.issuer}")
 *
 * // Use token in API calls (SEP-24, SEP-31, SEP-6, etc.)
 * val apiResponse = httpClient.get(endpoint) {
 *     headers {
 *         append("Authorization", "Bearer $authToken")  // Uses toString()
 *     }
 * }
 * ```
 *
 * ## Graceful Error Handling
 * If parsing fails (malformed JWT), a Sep45AuthToken is returned with:
 * - [token]: The original JWT string (preserved)
 * - All other properties: defaults (empty strings, 0 for timestamps)
 *
 * This allows applications to decide how to handle invalid tokens.
 *
 * @property token The raw JWT token string (always present)
 * @property account Subject claim (sub) - the authenticated contract account ID (C... address)
 * @property issuedAt Issued at timestamp (iat) - Unix epoch seconds
 * @property expiresAt Expiration timestamp (exp) - Unix epoch seconds
 * @property issuer Issuer claim (iss) - authentication server domain
 * @property clientDomain Client domain claim for domain-signed auth (optional)
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0045.md">SEP-45 Specification</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC 7519 - JSON Web Token (JWT)</a>
 */
data class Sep45AuthToken(
    val token: String,
    val account: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val issuer: String,
    val clientDomain: String? = null
) {
    /**
     * Checks whether the token has expired.
     *
     * Compares the [expiresAt] claim against the current system time.
     * If [expiresAt] is 0 (parsing failed or no expiration set), returns true
     * as a safety measure (treat invalid tokens as expired).
     *
     * @return true if the current time is after the expiration time, false otherwise
     */
    @OptIn(ExperimentalTime::class)
    fun isExpired(): Boolean {
        if (expiresAt == 0L) return true
        val currentTimeSeconds = Clock.System.now().toEpochMilliseconds() / 1000
        return currentTimeSeconds > expiresAt
    }

    /**
     * Returns the raw JWT token string.
     *
     * This allows the token to be used directly in string contexts,
     * such as HTTP Authorization headers for SEP-24, SEP-31, SEP-6, and other protocols:
     *
     * ```kotlin
     * // Example: Using token with SEP-24
     * val response = httpClient.get("https://anchor.com/sep24/transactions") {
     *     headers {
     *         append("Authorization", "Bearer $authToken")  // Uses toString()
     *     }
     * }
     * ```
     *
     * @return The raw JWT token string
     */
    override fun toString(): String = token

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parses a JWT token string into a Sep45AuthToken.
         *
         * This method performs lenient parsing: if the JWT is malformed or claims cannot be parsed,
         * it returns a Sep45AuthToken with default values. This allows graceful degradation in
         * client applications.
         *
         * ## Parsing Behavior
         * - Validates JWT has 3 parts (header.payload.signature)
         * - Handles Base64 URL-safe decoding with automatic padding
         * - Extracts standard JWT claims (iss, sub, iat, exp)
         * - Extracts SEP-45 specific claims (client_domain)
         * - Returns token with defaults on any parsing error
         *
         * ## Security Note
         * This parser does NOT verify JWT signatures. SEP-45 clients receive signed tokens
         * from auth servers over HTTPS and use them as bearer tokens. Signature verification
         * is the server's responsibility.
         *
         * @param jwt The JWT token string (format: "header.payload.signature")
         * @return Sep45AuthToken with parsed claims, or Sep45AuthToken with defaults on error
         *
         * @see isExpired
         */
        fun parse(jwt: String): Sep45AuthToken {
            try {
                val parts = jwt.split(".")
                if (parts.size != 3) {
                    return Sep45AuthToken(
                        token = jwt,
                        account = "",
                        issuedAt = 0L,
                        expiresAt = 0L,
                        issuer = ""
                    )
                }

                // Decode JWT payload (Base64 URL-safe encoding)
                val payload = parts[1]
                val decodedBytes = Util.base64urlDecode(payload)
                val payloadJson = decodedBytes.decodeToString()

                val jsonObject = json.parseToJsonElement(payloadJson).jsonObject

                return Sep45AuthToken(
                    token = jwt,
                    account = jsonObject["sub"]?.jsonPrimitive?.content ?: "",
                    issuedAt = jsonObject["iat"]?.jsonPrimitive?.longOrNull ?: 0L,
                    expiresAt = jsonObject["exp"]?.jsonPrimitive?.longOrNull ?: 0L,
                    issuer = jsonObject["iss"]?.jsonPrimitive?.content ?: "",
                    clientDomain = jsonObject["client_domain"]?.jsonPrimitive?.content
                )
            } catch (e: Exception) {
                return Sep45AuthToken(
                    token = jwt,
                    account = "",
                    issuedAt = 0L,
                    expiresAt = 0L,
                    issuer = ""
                )
            }
        }

    }
}
