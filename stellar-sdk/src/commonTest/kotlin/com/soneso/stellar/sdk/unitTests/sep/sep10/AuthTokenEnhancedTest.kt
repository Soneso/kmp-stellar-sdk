// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Enhanced tests for new AuthToken features:
 * - jti claim extraction
 * - account property
 * - memo property
 * - toString() override
 * - Edge cases
 */
class AuthTokenEnhancedTest {

    @OptIn(ExperimentalEncodingApi::class)
    private fun createJwtWithClaims(
        iss: String? = null,
        sub: String? = null,
        iat: Long? = null,
        exp: Long? = null,
        jti: String? = null,
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
            jti?.let { put("jti", it) }
            clientDomain?.let { put("client_domain", it) }
        }

        val headerEncoded = Base64.UrlSafe.encode(header.toString().encodeToByteArray()).trimEnd('=')
        val payloadEncoded = Base64.UrlSafe.encode(payload.toString().encodeToByteArray()).trimEnd('=')

        return "$headerEncoded.$payloadEncoded.signature"
    }

    @Test
    fun testJtiClaimExtraction() {
        val jwtToken = createJwtWithClaims(
            iss = "https://example.com",
            sub = "GACCOUNT...",
            jti = "550e8400-e29b-41d4-a716-446655440000"
        )

        val authToken = AuthToken.parse(jwtToken)

        assertEquals("550e8400-e29b-41d4-a716-446655440000", authToken.jti)
        assertEquals("https://example.com", authToken.iss)
        assertEquals("GACCOUNT...", authToken.sub)
    }

    @Test
    fun testJtiClaimAbsent() {
        val jwtToken = createJwtWithClaims(
            iss = "https://example.com",
            sub = "GACCOUNT..."
        )

        val authToken = AuthToken.parse(jwtToken)

        assertNull(authToken.jti)
        assertEquals("https://example.com", authToken.iss)
    }

    @Test
    fun testAccountPropertyBasic() {
        val authToken = AuthToken(
            token = "token",
            sub = "GACCOUNT..."
        )

        assertEquals("GACCOUNT...", authToken.account)
    }

    @Test
    fun testAccountPropertyWithMemo() {
        val authToken = AuthToken(
            token = "token",
            sub = "GACCOUNT...:12345"
        )

        assertEquals("GACCOUNT...", authToken.account)
    }

    @Test
    fun testAccountPropertyWithMuxedAccount() {
        val authToken = AuthToken(
            token = "token",
            sub = "MACCOUNT..."
        )

        assertEquals("MACCOUNT...", authToken.account)
    }

    @Test
    fun testAccountPropertyWithMuxedAccountAndMemo() {
        val authToken = AuthToken(
            token = "token",
            sub = "MACCOUNT...:67890"
        )

        assertEquals("MACCOUNT...", authToken.account)
    }

    @Test
    fun testAccountPropertyWhenSubIsNull() {
        val authToken = AuthToken(
            token = "token",
            sub = null
        )

        assertNull(authToken.account)
    }

    @Test
    fun testMemoPropertyWithMemo() {
        val authToken = AuthToken(
            token = "token",
            sub = "GACCOUNT...:12345"
        )

        assertEquals("12345", authToken.memo)
    }

    @Test
    fun testMemoPropertyWithoutMemo() {
        val authToken = AuthToken(
            token = "token",
            sub = "GACCOUNT..."
        )

        assertNull(authToken.memo)
    }

    @Test
    fun testMemoPropertyWithEmptyMemo() {
        val authToken = AuthToken(
            token = "token",
            sub = "GACCOUNT...:"
        )

        assertEquals("", authToken.memo)
    }

    @Test
    fun testMemoPropertyWithNumericMemo() {
        val authToken = AuthToken(
            token = "token",
            sub = "GACCOUNT...:9876543210"
        )

        assertEquals("9876543210", authToken.memo)
    }

    @Test
    fun testMemoPropertyWithStringMemo() {
        val authToken = AuthToken(
            token = "token",
            sub = "GACCOUNT...:session-abc123"
        )

        assertEquals("session-abc123", authToken.memo)
    }

    @Test
    fun testMemoPropertyWhenSubIsNull() {
        val authToken = AuthToken(
            token = "token",
            sub = null
        )

        assertNull(authToken.memo)
    }

    @Test
    fun testToString() {
        val tokenString = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ0ZXN0In0.test"
        val authToken = AuthToken(token = tokenString)

        // toString() should return the raw token
        assertEquals(tokenString, authToken.toString())

        // Verify it works in string interpolation
        val authHeader = "Bearer $authToken"
        assertEquals("Bearer $tokenString", authHeader)
    }

    @Test
    fun testToStringInHttpContext() {
        val authToken = AuthToken(
            token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ0ZXN0In0.test",
            iss = "https://anchor.example.com"
        )

        // Simulates how it would be used with SEP-24/31/6
        val authHeader = "Bearer $authToken"
        assertTrue(authHeader.startsWith("Bearer eyJ"))
        assertEquals("Bearer ${authToken.token}", authHeader)
    }

    @Test
    fun testComprehensiveTokenWithAllProperties() {
        val jwtToken = createJwtWithClaims(
            iss = "https://auth.stellar.org",
            sub = "GACCOUNT...:9999",
            iat = 1700000000L,
            exp = 1700003600L,
            jti = "unique-token-id-12345",
            clientDomain = "wallet.example.com"
        )

        val authToken = AuthToken.parse(jwtToken)

        assertEquals(jwtToken, authToken.token)
        assertEquals("https://auth.stellar.org", authToken.iss)
        assertEquals("GACCOUNT...:9999", authToken.sub)
        assertEquals(1700000000L, authToken.iat)
        assertEquals(1700003600L, authToken.exp)
        assertEquals("unique-token-id-12345", authToken.jti)
        assertEquals("wallet.example.com", authToken.clientDomain)
        assertEquals("GACCOUNT...", authToken.account)
        assertEquals("9999", authToken.memo)
    }

    @Test
    fun testAccountAndMemoWithMultipleColons() {
        // Edge case: Subject with multiple colons
        val authToken = AuthToken(
            token = "token",
            sub = "GACCOUNT...:12345:extra"
        )

        // account should be the first part
        assertEquals("GACCOUNT...", authToken.account)
        // memo should be the second part
        assertEquals("12345", authToken.memo)
    }

    @Test
    fun testSubjectWithOnlyColon() {
        val authToken = AuthToken(
            token = "token",
            sub = ":"
        )

        assertEquals("", authToken.account)
        assertEquals("", authToken.memo)
    }

    @Test
    fun testParseRealWorldJWTWithJti() {
        // Real JWT with jti claim
        val jwtToken = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9." +
                "eyJpc3MiOiAiaHR0cHM6Ly9hdXRoLnN0ZWxsYXIub3JnIiwgInN1YiI6ICJHQUNDRU5ULi4uOjEyMzQ1IiwgImlhdCI6IDE3MDAwMDAwMDAsICJleHAiOiAxNzAwMDAzNjAwLCAianRpIjogInVuaXF1ZS1pZCIsICJjbGllbnRfZG9tYWluIjogIndhbGxldC5jb20ifQ." +
                "signature"

        val authToken = AuthToken.parse(jwtToken)

        assertEquals("https://auth.stellar.org", authToken.iss)
        assertEquals("GACCENT...:12345", authToken.sub)
        assertEquals("unique-id", authToken.jti)
        assertEquals("wallet.com", authToken.clientDomain)
        assertEquals("GACCENT...", authToken.account)
        assertEquals("12345", authToken.memo)
    }
}
