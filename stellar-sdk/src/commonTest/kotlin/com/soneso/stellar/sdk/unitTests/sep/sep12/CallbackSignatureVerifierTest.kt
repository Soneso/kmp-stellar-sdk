// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep12

import com.soneso.stellar.sdk.sep.sep12.*
import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class)
class CallbackSignatureVerifierTest {

    private val testSigningKey = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
    private val testHost = "myapp.com"
    private val testBody = """{"id":"123","status":"ACCEPTED"}"""

    @Test
    fun testValidSignatureVerification() = runTest {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val payload = "$timestamp.$testHost.$testBody"

        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$timestamp, s=$signatureBase64"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )

        assertTrue(isValid)
    }

    @Test
    fun testInvalidSignatureRejection() = runTest {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val invalidSignature = Base64.encode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val signatureHeader = "t=$timestamp, s=$invalidSignature"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = testSigningKey,
            maxAgeSeconds = 300
        )

        assertFalse(isValid)
    }

    @Test
    fun testExpiredTimestampRejection() = runTest {
        val expiredTimestamp = (kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000) - 400
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val payload = "$expiredTimestamp.$testHost.$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$expiredTimestamp, s=$signatureBase64"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )

        assertFalse(isValid)
    }

    @Test
    fun testParseSignatureHeaderValid() {
        val timestamp = 1600000000L
        val signature = "SGVsbG8gV29ybGQh"
        val header = "t=$timestamp, s=$signature"

        val (parsedTimestamp, parsedSignature) = CallbackSignatureVerifier.parseSignatureHeader(header)

        assertEquals(timestamp, parsedTimestamp)
        assertEquals(signature, parsedSignature)
    }

    @Test
    fun testParseSignatureHeaderMissingTimestamp() {
        val header = "s=SGVsbG8gV29ybGQh"

        val exception = assertFailsWith<IllegalArgumentException> {
            CallbackSignatureVerifier.parseSignatureHeader(header)
        }

        assertTrue(exception.message!!.contains("Invalid or missing timestamp"))
    }

    @Test
    fun testParseSignatureHeaderMissingSignature() {
        val header = "t=1600000000"

        val exception = assertFailsWith<IllegalArgumentException> {
            CallbackSignatureVerifier.parseSignatureHeader(header)
        }

        assertTrue(exception.message!!.contains("Missing signature"))
    }

    @Test
    fun testParseSignatureHeaderInvalidTimestampFormat() {
        val header = "t=invalid_timestamp, s=SGVsbG8gV29ybGQh"

        val exception = assertFailsWith<IllegalArgumentException> {
            CallbackSignatureVerifier.parseSignatureHeader(header)
        }

        assertTrue(exception.message!!.contains("Invalid or missing timestamp"))
    }

    @Test
    fun testTimestampAgeValidation() = runTest {
        val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val recentTimestamp = currentTime - 100
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val payload = "$recentTimestamp.$testHost.$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$recentTimestamp, s=$signatureBase64"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )

        assertTrue(isValid)
    }

    @Test
    fun testPayloadConstructionFormat() = runTest {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val host = "example.com"
        val body = """{"test":"data"}"""
        val expectedPayload = "$timestamp.$host.$body"

        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val signatureBytes = signerKeyPair.sign(expectedPayload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$timestamp, s=$signatureBase64"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = body,
            expectedHost = host,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )

        assertTrue(isValid)
    }

    @Test
    fun testCustomMaxAgeSeconds() = runTest {
        val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val timestamp = currentTime - 550
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val payload = "$timestamp.$testHost.$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$timestamp, s=$signatureBase64"

        val isValidShortWindow = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )
        assertFalse(isValidShortWindow)

        val isValidLongWindow = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 600
        )
        assertTrue(isValidLongWindow)
    }

    @Test
    fun testDifferentHostRejection() = runTest {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val payload = "$timestamp.wronghost.com.$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$timestamp, s=$signatureBase64"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )

        assertFalse(isValid)
    }

    @Test
    fun testDifferentBodyRejection() = runTest {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val payload = "$timestamp.$testHost.$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$timestamp, s=$signatureBase64"

        val differentBody = """{"id":"456","status":"REJECTED"}"""

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = differentBody,
            expectedHost = testHost,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )

        assertFalse(isValid)
    }

    @Test
    fun testParseSignatureHeaderWithWhitespace() {
        val timestamp = 1600000000L
        val signature = "SGVsbG8gV29ybGQh"
        val header = "  t=$timestamp  ,  s=$signature  "

        val (parsedTimestamp, parsedSignature) = CallbackSignatureVerifier.parseSignatureHeader(header)

        assertEquals(timestamp, parsedTimestamp)
        assertEquals(signature, parsedSignature)
    }

    @Test
    fun testInvalidAccountIdRejection() = runTest {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val payload = "$timestamp.$testHost.$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$timestamp, s=$signatureBase64"

        val differentAccountId = "GCZPCFRQXMUSYLZX7IJKLZR5LIWZIMYPXYZXMHKSMS3IX6PFFNBDYAVY"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = differentAccountId,
            maxAgeSeconds = 300
        )

        assertFalse(isValid)
    }

    // ==================== Shim regression coverage (32-36) ====================
    //
    // These cases lock in the bit-for-bit compatibility promise of the v0.6.0
    // CallbackSignatureVerifier object. They exercise behaviours that the new
    // shared class deliberately changes (port-included host, empty host,
    // one-sided freshness, swallowed construction errors) and must continue to
    // hold through the deprecation shim.

    @Test
    fun testShim_portIncludedHost_preserved() = runTest {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val hostWithPort = "myapp.com:8443"
        val payload = "$timestamp.$hostWithPort.$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$timestamp, s=$signatureBase64"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = hostWithPort,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )

        assertTrue(isValid)
    }

    @Test
    fun testShim_loopbackWithPort_preserved() = runTest {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val loopbackHost = "localhost:8080"
        val payload = "$timestamp.$loopbackHost.$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$timestamp, s=$signatureBase64"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = loopbackHost,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )

        assertTrue(isValid)
    }

    @Test
    fun testShim_emptyHost_preserved() = runTest {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        // v0.6.0 allowed `expectedHost = ""`, producing payload `"$t..$body"`. The
        // shim must continue to accept that degenerate-but-valid configuration.
        val payload = "$timestamp..$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$timestamp, s=$signatureBase64"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = "",
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )

        assertTrue(isValid)
    }

    @Test
    fun testShim_futureDatedTimestamp_preserved() = runTest {
        val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        // 200 seconds in the future. The v0.6.0 verifier's one-sided check
        // (`currentTime - timestamp > maxAgeSeconds`) accepts future-dated
        // timestamps because `currentTime - futureTimestamp` is negative.
        val futureTimestamp = currentTime + 200
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val payload = "$futureTimestamp.$testHost.$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$futureTimestamp, s=$signatureBase64"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = signerKeyPair.getAccountId(),
            maxAgeSeconds = 300
        )

        assertTrue(isValid)
    }

    @Test
    fun testShim_malformedSigningKey_swallowedAsFalse() = runTest {
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val signatureHeader = "t=$timestamp, s=${Base64.encode(ByteArray(64))}"

        // v0.6.0 wrapped its entire `verify` body in `try/catch(Exception) { false }`,
        // so a malformed `anchorSigningKey` (which makes `KeyPair.fromAccountId` throw)
        // collapsed to `false`. The shim must preserve that contract — the shared
        // class's `init {}` throws on malformed `signingKey` and the shim's wrapping
        // try/catch is what keeps the return value at `false`.
        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = "not-a-G-key",
            maxAgeSeconds = 300
        )

        assertFalse(isValid)
    }

    @Test
    fun testDefaultMaxAgeSeconds_omittedParameter_invokesWithFiveMinuteDefault() = runTest {
        // Calling the shim without the `maxAgeSeconds` argument exercises the
        // default-parameter binding (= 300 seconds, 5 minutes). The signature payload
        // is constructed with a current timestamp, so a 300-second freshness window
        // is comfortably wide and the verification must succeed.
        val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val signerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        val payload = "$timestamp.$testHost.$testBody"
        val signatureBytes = signerKeyPair.sign(payload.encodeToByteArray())
        val signatureBase64 = Base64.encode(signatureBytes)
        val signatureHeader = "t=$timestamp, s=$signatureBase64"

        val isValid = CallbackSignatureVerifier.verify(
            signatureHeader = signatureHeader,
            requestBody = testBody,
            expectedHost = testHost,
            anchorSigningKey = signerKeyPair.getAccountId(),
            // maxAgeSeconds intentionally omitted — exercises default 300-second value.
        )

        assertTrue(isValid)
    }
}
