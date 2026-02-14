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
}
