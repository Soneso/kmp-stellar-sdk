// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep10

import com.soneso.stellar.sdk.Util
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Represents a parsed SEP-10 authentication token (JWT).
 *
 * This class parses JWT tokens returned from Stellar SEP-10 authentication endpoints
 * and exposes standard JWT claims and SEP-10 specific claims. It performs lenient parsing:
 * if the JWT is malformed, it returns an AuthToken with only the raw token string populated,
 * allowing graceful degradation in applications.
 *
 * ## Standard JWT Claims (RFC 7519)
 * - [iss]: Issuer - the authentication server's domain
 * - [sub]: Subject - the authenticated account ID (may include memo)
 * - [iat]: Issued At - Unix timestamp when token was created
 * - [exp]: Expiration Time - Unix timestamp when token expires
 * - [jti]: JWT ID - unique identifier for this token
 *
 * ## SEP-10 Specific Claims
 * - [clientDomain]: Client domain for domain-signed authentication
 *
 * ## Computed Properties
 * - [account]: Account ID extracted from [sub] (handles memos and muxed accounts)
 * - [memo]: Memo extracted from [sub] (format: "ACCOUNT:MEMO")
 *
 * ## Security Considerations
 * - This parser does NOT verify JWT signatures (per SEP-10 spec)
 * - SEP-10 clients receive signed tokens over HTTPS and use them as bearer tokens
 * - Signature verification is the server's responsibility
 * - Always validate token expiry using [isExpired] before use
 *
 * ## Example Usage
 * ```kotlin
 * // Parse token
 * val authToken = AuthToken.parse(jwtString)
 *
 * // Check expiry
 * if (authToken.isExpired()) {
 *     println("Token expired at epoch ${authToken.exp}")
 *     return
 * }
 *
 * // Extract account and memo
 * val accountId = authToken.account  // "GACCOUNT..."
 * val memo = authToken.memo          // "12345" or null
 *
 * // Access issuer
 * println("Authenticated by: ${authToken.iss}")
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
 * If parsing fails (malformed JWT), an AuthToken is returned with:
 * - [token]: The original JWT string (preserved)
 * - All other properties: null
 *
 * This allows applications to decide how to handle invalid tokens.
 *
 * @property token The raw JWT token string (always present)
 * @property iss Issuer claim - authentication server domain
 * @property sub Subject claim - authenticated account ID (may include memo)
 * @property iat Issued at timestamp - Unix epoch seconds
 * @property exp Expiration timestamp - Unix epoch seconds
 * @property jti JWT ID - unique token identifier
 * @property clientDomain Client domain claim for domain-signed auth
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md">SEP-10 Specification</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC 7519 - JSON Web Token (JWT)</a>
 */
data class AuthToken(
    val token: String,
    val iss: String? = null,
    val sub: String? = null,
    val iat: Long? = null,
    val exp: Long? = null,
    val jti: String? = null,
    val clientDomain: String? = null
) {
    /**
     * The Stellar account ID extracted from the [sub] claim.
     *
     * Handles both standard accounts (G...) and muxed accounts (M...).
     * If the subject contains a memo (format: "ACCOUNT:MEMO"), only the account part is returned.
     *
     * Example:
     * - `sub = "GACCOUNT..."` → `account = "GACCOUNT..."`
     * - `sub = "GACCOUNT...:12345"` → `account = "GACCOUNT..."`
     * - `sub = "MACCOUNT..."` → `account = "MACCOUNT..."`
     *
     * @return The account ID, or null if [sub] is not present
     */
    val account: String?
        get() = sub?.split(":")?.firstOrNull()

    /**
     * The memo ID extracted from the [sub] claim.
     *
     * SEP-10 supports memo IDs in the subject claim using the format "ACCOUNT:MEMO".
     * This is commonly used by wallet applications to associate tokens with specific
     * sub-accounts or sessions.
     *
     * Example:
     * - `sub = "GACCOUNT..."` → `memo = null`
     * - `sub = "GACCOUNT...:12345"` → `memo = "12345"`
     *
     * @return The memo string, or null if no memo is present in the subject
     */
    val memo: String?
        get() = sub?.split(":")?.getOrNull(1)

    /**
     * Checks whether the token has expired.
     *
     * Compares the [exp] claim against the current system time.
     * If [exp] is null (no expiration set), returns false (token never expires).
     *
     * @return true if the current time is after the expiration time, false otherwise
     */
    @OptIn(ExperimentalTime::class)
    fun isExpired(): Boolean {
        val expirationTime = exp ?: return false
        val currentTimeSeconds = Clock.System.now().toEpochMilliseconds() / 1000
        return currentTimeSeconds > expirationTime
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
        /**
         * Parses a JWT token string into an AuthToken.
         *
         * This method performs lenient parsing: if the JWT is malformed or claims cannot be parsed,
         * it returns an AuthToken with only the raw token string populated. This allows graceful
         * degradation in client applications.
         *
         * ## Parsing Behavior
         * - Validates JWT has 3 parts (header.payload.signature)
         * - Handles Base64 URL-safe decoding with automatic padding
         * - Extracts all standard JWT claims (iss, sub, iat, exp, jti)
         * - Extracts SEP-10 specific claims (client_domain)
         * - Returns partial token on any parsing error
         *
         * ## Security Note
         * This parser does NOT verify JWT signatures. SEP-10 clients receive signed tokens
         * from auth servers over HTTPS and use them as bearer tokens. Signature verification
         * is the server's responsibility.
         *
         * @param jwtToken The JWT token string (format: "header.payload.signature")
         * @return AuthToken with parsed claims, or minimal AuthToken with only token string on error
         *
         * @see isExpired
         */
        fun parse(jwtToken: String): AuthToken {
            try {
                val parts = jwtToken.split(".")
                if (parts.size != 3) {
                    return AuthToken(token = jwtToken)
                }

                // Decode JWT payload (Base64 URL-safe encoding)
                val payload = parts[1]
                val decodedBytes = Util.base64urlDecode(payload)
                val payloadJson = decodedBytes.decodeToString()

                val json = Json { ignoreUnknownKeys = true }
                val jsonObject = json.parseToJsonElement(payloadJson).jsonObject

                return AuthToken(
                    token = jwtToken,
                    iss = jsonObject["iss"]?.jsonPrimitive?.content,
                    sub = jsonObject["sub"]?.jsonPrimitive?.content,
                    iat = jsonObject["iat"]?.jsonPrimitive?.longOrNull,
                    exp = jsonObject["exp"]?.jsonPrimitive?.longOrNull,
                    jti = jsonObject["jti"]?.jsonPrimitive?.content,
                    clientDomain = jsonObject["client_domain"]?.jsonPrimitive?.content
                )
            } catch (e: Exception) {
                return AuthToken(token = jwtToken)
            }
        }

    }
}
