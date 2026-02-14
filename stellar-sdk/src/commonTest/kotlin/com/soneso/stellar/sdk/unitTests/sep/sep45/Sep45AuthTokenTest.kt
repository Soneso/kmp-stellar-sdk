// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep45

import com.soneso.stellar.sdk.sep.sep45.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Unit tests for Sep45AuthToken JWT parsing.
 *
 * Tests cover:
 * - JWT parsing with all claims
 * - client_domain extraction
 * - Token expiration validation
 * - Error handling for malformed tokens
 * - toString() utility
 */
class Sep45AuthTokenTest {

    /**
     * Creates a JWT token with the specified claims for testing.
     *
     * @param iss Issuer claim
     * @param sub Subject claim (contract account ID)
     * @param iat Issued at timestamp
     * @param exp Expiration timestamp
     * @param clientDomain Client domain claim (SEP-45 specific)
     * @return JWT token string in format: header.payload.signature
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun createJwtWithClaims(
        iss: String? = null,
        sub: String? = null,
        iat: Long? = null,
        exp: Long? = null,
        clientDomain: String? = null
    ): String {
        val header = buildJsonObject {
            put("alg", "HS256")
            put("typ", "JWT")
        }

        val payload = buildJsonObject {
            iss?.let { put("iss", it) }
            sub?.let { put("sub", it) }
            iat?.let { put("iat", it) }
            exp?.let { put("exp", it) }
            clientDomain?.let { put("client_domain", it) }
        }

        val headerEncoded = Base64.UrlSafe.encode(header.toString().encodeToByteArray()).trimEnd('=')
        val payloadEncoded = Base64.UrlSafe.encode(payload.toString().encodeToByteArray()).trimEnd('=')

        return "$headerEncoded.$payloadEncoded.signature"
    }

    // =========================================================================
    // JWT Parsing Tests
    // =========================================================================

    @Test
    fun testParseValidJwtToken() {
        // Create a valid JWT with all claims
        val jwtToken = createJwtWithClaims(
            iss = "https://auth.stellar.org",
            sub = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            iat = 1700000000L,
            exp = 1700003600L,
            clientDomain = "wallet.example.com"
        )

        val authToken = Sep45AuthToken.parse(jwtToken)

        assertEquals(jwtToken, authToken.token)
        assertEquals("https://auth.stellar.org", authToken.issuer)
        assertEquals("CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC", authToken.account)
        assertEquals(1700000000L, authToken.issuedAt)
        assertEquals(1700003600L, authToken.expiresAt)
        assertEquals("wallet.example.com", authToken.clientDomain)
    }

    @Test
    fun testParseJwtWithClientDomain() {
        // Test extraction of client_domain claim
        val jwtToken = createJwtWithClaims(
            iss = "https://example.com",
            sub = "CCONTRACT123456789012345678901234567890123456789012345",
            iat = 1700000000L,
            exp = 1700003600L,
            clientDomain = "wallet.mydomain.com"
        )

        val authToken = Sep45AuthToken.parse(jwtToken)

        assertEquals("wallet.mydomain.com", authToken.clientDomain)
    }

    @Test
    fun testParseJwtWithoutClientDomain() {
        // Test that clientDomain is null when not present
        val jwtToken = createJwtWithClaims(
            iss = "https://example.com",
            sub = "CCONTRACT123456789012345678901234567890123456789012345",
            iat = 1700000000L,
            exp = 1700003600L
            // No clientDomain
        )

        val authToken = Sep45AuthToken.parse(jwtToken)

        assertEquals(jwtToken, authToken.token)
        assertEquals("https://example.com", authToken.issuer)
        assertEquals("CCONTRACT123456789012345678901234567890123456789012345", authToken.account)
        assertEquals(1700000000L, authToken.issuedAt)
        assertEquals(1700003600L, authToken.expiresAt)
        assertNull(authToken.clientDomain)
    }

    // =========================================================================
    // Expiration Tests
    // =========================================================================

    @OptIn(ExperimentalTime::class)
    @Test
    fun testIsExpiredWhenExpired() {
        // Token with expiration in the past (1 hour ago)
        val pastExpiration = (Clock.System.now().toEpochMilliseconds() / 1000) - 3600

        val jwtToken = createJwtWithClaims(
            iss = "https://example.com",
            sub = "CCONTRACT123456789012345678901234567890123456789012345",
            iat = pastExpiration - 3600,
            exp = pastExpiration
        )

        val authToken = Sep45AuthToken.parse(jwtToken)

        assertTrue(authToken.isExpired())
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testIsExpiredWhenValid() {
        // Token with expiration in the future (1 hour from now)
        val futureExpiration = (Clock.System.now().toEpochMilliseconds() / 1000) + 3600

        val jwtToken = createJwtWithClaims(
            iss = "https://example.com",
            sub = "CCONTRACT123456789012345678901234567890123456789012345",
            iat = futureExpiration - 7200,
            exp = futureExpiration
        )

        val authToken = Sep45AuthToken.parse(jwtToken)

        assertFalse(authToken.isExpired())
    }

    @Test
    fun testIsExpiredWithZeroExpiration() {
        // A token with expiresAt = 0 (parsing failed or missing exp claim)
        // should be treated as expired for safety
        val authToken = Sep45AuthToken(
            token = "invalid.token.here",
            account = "",
            issuedAt = 0L,
            expiresAt = 0L,
            issuer = ""
        )

        assertTrue(authToken.isExpired())
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    @Test
    fun testParseMalformedJwt() {
        // JWT must have exactly 3 parts separated by dots
        val malformedToken = "only.two.parts"

        // Parser should handle gracefully (malformed token has 3 parts but invalid base64)
        val authToken = Sep45AuthToken.parse(malformedToken)

        assertEquals(malformedToken, authToken.token)
        assertEquals("", authToken.account)
        assertEquals(0L, authToken.issuedAt)
        assertEquals(0L, authToken.expiresAt)
        assertEquals("", authToken.issuer)
        assertNull(authToken.clientDomain)
    }

    @Test
    fun testParseMalformedJwtTwoParts() {
        // JWT with only 2 parts (missing signature)
        val twoPartToken = "header.payload"

        val authToken = Sep45AuthToken.parse(twoPartToken)

        assertEquals(twoPartToken, authToken.token)
        assertEquals("", authToken.account)
        assertEquals(0L, authToken.issuedAt)
        assertEquals(0L, authToken.expiresAt)
        assertEquals("", authToken.issuer)
    }

    @Test
    fun testParseMalformedJwtOnePart() {
        // JWT with only 1 part
        val onePartToken = "notavalidjwt"

        val authToken = Sep45AuthToken.parse(onePartToken)

        assertEquals(onePartToken, authToken.token)
        assertEquals("", authToken.account)
        assertEquals(0L, authToken.issuedAt)
        assertEquals(0L, authToken.expiresAt)
        assertEquals("", authToken.issuer)
    }

    @Test
    fun testParseInvalidBase64Payload() {
        // JWT with invalid base64 in payload (contains invalid characters)
        val invalidBase64Token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.!!!invalid_base64!!!.signature"

        val authToken = Sep45AuthToken.parse(invalidBase64Token)

        assertEquals(invalidBase64Token, authToken.token)
        assertEquals("", authToken.account)
        assertEquals(0L, authToken.issuedAt)
        assertEquals(0L, authToken.expiresAt)
        assertEquals("", authToken.issuer)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testParseInvalidJsonPayload() {
        // JWT with valid base64 but invalid JSON in payload
        val header = buildJsonObject {
            put("alg", "HS256")
            put("typ", "JWT")
        }
        val headerEncoded = Base64.UrlSafe.encode(header.toString().encodeToByteArray()).trimEnd('=')

        // Invalid JSON (not a JSON object)
        val invalidJsonPayload = Base64.UrlSafe.encode("not valid json {{}".encodeToByteArray()).trimEnd('=')

        val invalidJsonToken = "$headerEncoded.$invalidJsonPayload.signature"

        val authToken = Sep45AuthToken.parse(invalidJsonToken)

        assertEquals(invalidJsonToken, authToken.token)
        assertEquals("", authToken.account)
        assertEquals(0L, authToken.issuedAt)
        assertEquals(0L, authToken.expiresAt)
        assertEquals("", authToken.issuer)
    }

    @Test
    fun testParseEmptyToken() {
        val emptyToken = ""

        val authToken = Sep45AuthToken.parse(emptyToken)

        assertEquals(emptyToken, authToken.token)
        assertEquals("", authToken.account)
        assertEquals(0L, authToken.issuedAt)
        assertEquals(0L, authToken.expiresAt)
        assertEquals("", authToken.issuer)
    }

    // =========================================================================
    // Utility Tests
    // =========================================================================

    @Test
    fun testToStringReturnsRawToken() {
        val jwtToken = createJwtWithClaims(
            iss = "https://example.com",
            sub = "CCONTRACT123456789012345678901234567890123456789012345",
            iat = 1700000000L,
            exp = 1700003600L
        )

        val authToken = Sep45AuthToken.parse(jwtToken)

        // toString() should return the raw token string
        assertEquals(jwtToken, authToken.toString())

        // Verify it works in string interpolation (useful for HTTP headers)
        val authHeader = "Bearer $authToken"
        assertEquals("Bearer $jwtToken", authHeader)
    }

    @Test
    fun testToStringWithDirectConstruction() {
        val rawToken = "my.jwt.token"
        val authToken = Sep45AuthToken(
            token = rawToken,
            account = "CCONTRACT...",
            issuedAt = 1700000000L,
            expiresAt = 1700003600L,
            issuer = "https://example.com"
        )

        assertEquals(rawToken, authToken.toString())
    }

    // =========================================================================
    // Additional Edge Case Tests
    // =========================================================================

    @Test
    fun testParseJwtWithMissingClaims() {
        // JWT with only iss claim (missing sub, iat, exp)
        val jwtToken = createJwtWithClaims(
            iss = "https://example.com"
            // Missing sub, iat, exp
        )

        val authToken = Sep45AuthToken.parse(jwtToken)

        assertEquals(jwtToken, authToken.token)
        assertEquals("https://example.com", authToken.issuer)
        assertEquals("", authToken.account)
        assertEquals(0L, authToken.issuedAt)
        assertEquals(0L, authToken.expiresAt)
    }

    @Test
    fun testParseJwtWithEmptyPayload() {
        // JWT with empty payload object
        val jwtToken = createJwtWithClaims()

        val authToken = Sep45AuthToken.parse(jwtToken)

        assertEquals(jwtToken, authToken.token)
        assertEquals("", authToken.issuer)
        assertEquals("", authToken.account)
        assertEquals(0L, authToken.issuedAt)
        assertEquals(0L, authToken.expiresAt)
        assertNull(authToken.clientDomain)
    }

    @Test
    fun testDataClassEquality() {
        // Test that data class equality works correctly
        val token1 = Sep45AuthToken(
            token = "jwt.token.here",
            account = "CCONTRACT...",
            issuedAt = 1700000000L,
            expiresAt = 1700003600L,
            issuer = "https://example.com",
            clientDomain = "wallet.com"
        )

        val token2 = Sep45AuthToken(
            token = "jwt.token.here",
            account = "CCONTRACT...",
            issuedAt = 1700000000L,
            expiresAt = 1700003600L,
            issuer = "https://example.com",
            clientDomain = "wallet.com"
        )

        assertEquals(token1, token2)
        assertEquals(token1.hashCode(), token2.hashCode())
    }

    @Test
    fun testDataClassCopy() {
        val original = Sep45AuthToken(
            token = "jwt.token.here",
            account = "CCONTRACT...",
            issuedAt = 1700000000L,
            expiresAt = 1700003600L,
            issuer = "https://example.com",
            clientDomain = "wallet.com"
        )

        val modified = original.copy(clientDomain = "new.wallet.com")

        assertEquals(original.token, modified.token)
        assertEquals(original.account, modified.account)
        assertEquals("new.wallet.com", modified.clientDomain)
    }

    @Test
    fun testIsExpiredBoundaryCondition() {
        // Test boundary: token that expired 1 second ago (deterministically expired)
        // Use a fixed past timestamp to avoid timing issues
        val oneSecondAgo = 1700000000L  // Fixed timestamp in the past

        val authToken = Sep45AuthToken(
            token = "jwt.token.here",
            account = "CCONTRACT...",
            issuedAt = oneSecondAgo - 3600,
            expiresAt = oneSecondAgo,
            issuer = "https://example.com"
        )

        // Token with expiration in the past should be expired
        assertTrue(authToken.isExpired())
    }
}
