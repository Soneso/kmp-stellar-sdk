// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthTokenTest {

    @Test
    fun testParseValidJWT() {
        val jwtToken = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9." +
                "eyJpc3MiOiAiaHR0cHM6Ly9leGFtcGxlLmNvbSIsICJzdWIiOiAiR0FDQ09VTlQuLi4iLCAiaWF0IjogMTcwMDAwMDAwMCwgImV4cCI6IDE3MDAwMDM2MDAsICJjbGllbnRfZG9tYWluIjogIndhbGxldC5leGFtcGxlLmNvbSJ9." +
                "signature"

        val authToken = AuthToken.parse(jwtToken)

        assertEquals(jwtToken, authToken.token)
        assertEquals("https://example.com", authToken.iss)
        assertEquals("GACCOUNT...", authToken.sub)
        assertEquals(1700000000L, authToken.iat)
        assertEquals(1700003600L, authToken.exp)
        assertEquals("wallet.example.com", authToken.clientDomain)
    }

    @Test
    fun testParseJWTWithoutOptionalClaims() {
        val jwtToken = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9." +
                "eyJpc3MiOiAiaHR0cHM6Ly9leGFtcGxlLmNvbSIsICJzdWIiOiAiR0FDQ09VTlQuLi4iLCAiaWF0IjogMTcwMDAwMDAwMCwgImV4cCI6IDE3MDAwMDM2MDB9." +
                "signature"

        val authToken = AuthToken.parse(jwtToken)

        assertEquals(jwtToken, authToken.token)
        assertEquals("https://example.com", authToken.iss)
        assertEquals("GACCOUNT...", authToken.sub)
        assertEquals(1700000000L, authToken.iat)
        assertEquals(1700003600L, authToken.exp)
        assertNull(authToken.clientDomain)
    }

    @Test
    fun testParseJWTWithMemoInSub() {
        val jwtToken = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9." +
                "eyJpc3MiOiAiaHR0cHM6Ly9leGFtcGxlLmNvbSIsICJzdWIiOiAiR0FDQ09VTlQuLi46MTIzNDUiLCAiaWF0IjogMTcwMDAwMDAwMCwgImV4cCI6IDE3MDAwMDM2MDB9." +
                "signature"

        val authToken = AuthToken.parse(jwtToken)

        assertEquals("GACCOUNT...:12345", authToken.sub)
        assertEquals("GACCOUNT...", authToken.account)
        assertEquals("12345", authToken.memo)
    }

    @Test
    fun testParseJWTWithMuxedAccount() {
        val jwtToken = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9." +
                "eyJpc3MiOiAiaHR0cHM6Ly9leGFtcGxlLmNvbSIsICJzdWIiOiAiTUFDQ09VTlQuLi4iLCAiaWF0IjogMTcwMDAwMDAwMCwgImV4cCI6IDE3MDAwMDM2MDB9." +
                "signature"

        val authToken = AuthToken.parse(jwtToken)

        assertEquals("MACCOUNT...", authToken.sub)
        assertEquals("MACCOUNT...", authToken.account)
        assertNull(authToken.memo)
    }

    @Test
    fun testParseInvalidJWT() {
        val invalidToken = "invalid.token"

        val authToken = AuthToken.parse(invalidToken)

        assertEquals(invalidToken, authToken.token)
        assertNull(authToken.iss)
        assertNull(authToken.sub)
        assertNull(authToken.iat)
        assertNull(authToken.exp)
        assertNull(authToken.clientDomain)
    }

    @Test
    fun testParseJWTWithMalformedPayload() {
        val malformedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid_base64!@#.signature"

        val authToken = AuthToken.parse(malformedToken)

        assertEquals(malformedToken, authToken.token)
        assertNull(authToken.iss)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testIsExpiredWhenExpired() {
        val pastExpiration = (Clock.System.now().toEpochMilliseconds() / 1000) - 3600
        val authToken = AuthToken(
            token = "token",
            exp = pastExpiration
        )

        assertTrue(authToken.isExpired())
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun testIsExpiredWhenNotExpired() {
        val futureExpiration = (Clock.System.now().toEpochMilliseconds() / 1000) + 3600
        val authToken = AuthToken(
            token = "token",
            exp = futureExpiration
        )

        assertFalse(authToken.isExpired())
    }

    @Test
    fun testIsExpiredWhenNoExpiration() {
        val authToken = AuthToken(token = "token")

        assertFalse(authToken.isExpired())
    }

    @Test
    fun testAccountPropertyWithRegularAccount() {
        val authToken = AuthToken(
            token = "token",
            sub = "GACCOUNT..."
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
    fun testAccountPropertyWithMemo() {
        val authToken = AuthToken(
            token = "token",
            sub = "GACCOUNT...:12345"
        )

        assertEquals("GACCOUNT...", authToken.account)
    }

    @Test
    fun testAccountPropertyWhenNoSub() {
        val authToken = AuthToken(token = "token")

        assertNull(authToken.account)
    }

    @Test
    fun testTokenIsAlwaysPreserved() {
        val tokenString = "my.jwt.token"
        val authToken = AuthToken.parse(tokenString)

        assertNotNull(authToken.token)
        assertEquals(tokenString, authToken.token)
    }
}
