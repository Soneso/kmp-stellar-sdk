// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.common

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class)
class CallbackSignatureVerifierTest {

    private val registeredCallbackUrl = "https://wallet.example.org/sep31-callback"
    private val registeredHost = Url(registeredCallbackUrl).host
    private val testBody = """{"transaction":{"id":"abc","status":"completed"}}"""

    /** Fixed-time clock used to make freshness boundaries deterministic. */
    private class TestClock(private val nowSeconds: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(nowSeconds)
    }

    /**
     * Build a `Signature` header value for the given parameters. Tests can mutate
     * individual components (timestamp, host, body, signing key) to construct any
     * negative-case input.
     */
    private suspend fun signCallback(
        signer: KeyPair,
        timestamp: Long,
        host: String,
        body: String,
        urlSafe: Boolean = false,
    ): String {
        val payload = "$timestamp.$host.$body".encodeToByteArray()
        val signature = signer.sign(payload)
        val encoded = if (urlSafe) Base64.UrlSafe.encode(signature) else Base64.encode(signature)
        return "t=$timestamp, s=$encoded"
    }

    // ==================== Positive cases (1-8) ====================

    @Test
    fun test01_validSignatureHeader_returnsValid() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        val header = signCallback(signer, now, registeredHost, testBody)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        val result = verifier.verify(header, null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.Valid, result)
    }

    @Test
    fun test02_xStellarSignatureHeaderOnly_returnsValid() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        val header = signCallback(signer, now, registeredHost, testBody)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        val result = verifier.verify(null, header, testBody)
        assertEquals(CallbackSignatureVerifier.Result.Valid, result)
    }

    @Test
    fun test03_bothHeadersPresent_primaryValid_returnsValid() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        val validHeader = signCallback(signer, now, registeredHost, testBody)
        val invalidLegacy = "t=$now, s=AAAA"

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        val result = verifier.verify(validHeader, invalidLegacy, testBody)
        assertEquals(CallbackSignatureVerifier.Result.Valid, result)
    }

    @Test
    fun test04_bothHeadersPresent_primaryMalformed_returnsMalformed() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        val validLegacy = signCallback(signer, now, registeredHost, testBody)
        val malformedPrimary = "not-a-valid-signature-header"

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        val result = verifier.verify(malformedPrimary, validLegacy, testBody)
        assertEquals(CallbackSignatureVerifier.Result.MalformedHeader, result)
    }

    @Test
    fun test05_freshnessBoundary_atLimit_returnsValid() = runTest {
        val signer = KeyPair.random()
        val signedAt = 1_700_000_000L
        val now = signedAt + 120 // abs(now - t) == freshnessSeconds, boundary inclusive
        val header = signCallback(signer, signedAt, registeredHost, testBody)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            freshnessSeconds = 120,
            clock = TestClock(now),
        )

        val result = verifier.verify(header, null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.Valid, result)
    }

    @Test
    fun test06_freshnessBoundary_oneOver_returnsStale() = runTest {
        val signer = KeyPair.random()
        val signedAt = 1_700_000_000L
        val now = signedAt + 121
        val header = signCallback(signer, signedAt, registeredHost, testBody)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            freshnessSeconds = 120,
            clock = TestClock(now),
        )

        val result = verifier.verify(header, null, testBody)
        val stale = assertIs<CallbackSignatureVerifier.Result.Stale>(result)
        assertEquals(121L, stale.ageSeconds)
    }

    @Test
    fun test07_urlSafeBase64Signature_returnsValid() = runTest {
        // Engineer a payload whose Ed25519 signature contains a `/` or `+` byte
        // pattern by trying random keys until we land on a URL-unsafe encoding.
        // In practice every signature ends up with mixed alphabets, so we
        // assert the URL-safe branch separately by feeding a URL-safe encoded
        // signature explicitly.
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        val header = signCallback(signer, now, registeredHost, testBody, urlSafe = true)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        // Standard base64 decode may succeed for many signature bytes; we accept
        // either path as long as verification ultimately succeeds.
        val result = verifier.verify(header, null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.Valid, result)
    }

    @Test
    fun test08_standardBase64WithPadding_returnsValid() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        // 64-byte Ed25519 signatures base64-encode to 88 characters with `=` padding.
        val header = signCallback(signer, now, registeredHost, testBody)
        // Sanity-check the header carries the expected padded form.
        val sValue = header.substringAfter("s=")
        assertTrue(sValue.endsWith("=") || sValue.endsWith("=="), "expected base64 padding in header: $header")

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        val result = verifier.verify(header, null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.Valid, result)
    }

    // ==================== Negative header-form cases (9-16) ====================

    @Test
    fun test09_bothHeadersNull_returnsMissing() = runTest {
        val verifier = CallbackSignatureVerifier(
            signingKey = KeyPair.random().getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
        )
        val result = verifier.verify(null, null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.MissingHeader, result)
    }

    @Test
    fun test10_bothHeadersEmpty_returnsMissing() = runTest {
        val verifier = CallbackSignatureVerifier(
            signingKey = KeyPair.random().getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
        )
        val result = verifier.verify("", "", testBody)
        assertEquals(CallbackSignatureVerifier.Result.MissingHeader, result)
    }

    @Test
    fun test11_bothHeadersWhitespace_returnsMissing() = runTest {
        val verifier = CallbackSignatureVerifier(
            signingKey = KeyPair.random().getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
        )
        val result = verifier.verify("   ", "\t\n ", testBody)
        assertEquals(CallbackSignatureVerifier.Result.MissingHeader, result)
    }

    @Test
    fun test12_malformedTimestampNotDigits_returnsMalformed() = runTest {
        val verifier = CallbackSignatureVerifier(
            signingKey = KeyPair.random().getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
        )
        val result = verifier.verify("t=foo, s=abc", null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.MalformedHeader, result)
    }

    @Test
    fun test13_malformedMissingSignaturePart_returnsMalformed() = runTest {
        val verifier = CallbackSignatureVerifier(
            signingKey = KeyPair.random().getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
        )
        val result = verifier.verify("t=1700000000", null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.MalformedHeader, result)
    }

    @Test
    fun test14_malformedExtraPrefixJunk_returnsMalformed() = runTest {
        val verifier = CallbackSignatureVerifier(
            signingKey = KeyPair.random().getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
        )
        val result = verifier.verify("junk t=1700000000, s=AAAA", null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.MalformedHeader, result)
    }

    @Test
    fun test15_malformedNonBase64CharsInSignature_returnsMalformed() = runTest {
        val verifier = CallbackSignatureVerifier(
            signingKey = KeyPair.random().getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
        )
        // `@` is outside the base64 alphabet (and outside the URL-safe alphabet)
        // so the regex itself refuses to match.
        val result = verifier.verify("t=1700000000, s=A@BC", null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.MalformedHeader, result)
    }

    @Test
    fun test16_malformedTrailingHeaderParameter_returnsMalformed() = runTest {
        val verifier = CallbackSignatureVerifier(
            signingKey = KeyPair.random().getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
        )
        // Trailing `; v=1` is outside the tightened regex's permitted character set.
        val result = verifier.verify("t=1700000000, s=AAAA; v=1", null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.MalformedHeader, result)
    }

    // ==================== Negative crypto / payload cases (17-22) ====================

    @Test
    fun test17_wrongSigningKey_returnsSignatureMismatch() = runTest {
        val realSigner = KeyPair.random()
        val unrelatedKey = KeyPair.random()
        val now = 1_700_000_000L
        val header = signCallback(realSigner, now, registeredHost, testBody)

        val verifier = CallbackSignatureVerifier(
            signingKey = unrelatedKey.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        val result = verifier.verify(header, null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.SignatureMismatch, result)
    }

    @Test
    fun test18_tamperedBody_returnsSignatureMismatch() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        val header = signCallback(signer, now, registeredHost, testBody)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        val tamperedBody = testBody.replace("completed", "refunded")
        val result = verifier.verify(header, null, tamperedBody)
        assertEquals(CallbackSignatureVerifier.Result.SignatureMismatch, result)
    }

    @Test
    fun test19_hostPinning_differentRegisteredUrl_returnsSignatureMismatch() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        // Signer signs against "wallet.example.org" but the verifier is constructed
        // for a different registered URL.
        val header = signCallback(signer, now, "wallet.example.org", testBody)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = "https://different.example.org/sep31-callback",
            clock = TestClock(now),
        )

        val result = verifier.verify(header, null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.SignatureMismatch, result)
    }

    @Test
    fun test20_portStripping_signedWithPort_returnsSignatureMismatch() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        // Signer includes the port. Verifier strips it, so the canonical payloads diverge.
        val header = signCallback(signer, now, "wallet.example.org:8443", testBody)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = "https://wallet.example.org:8443/sep31-callback",
            clock = TestClock(now),
        )

        val result = verifier.verify(header, null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.SignatureMismatch, result)
    }

    @Test
    fun test21_staleReplayPositiveAge_returnsStale() = runTest {
        val signer = KeyPair.random()
        val signedAt = 1_700_000_000L
        val now = signedAt + 500
        val header = signCallback(signer, signedAt, registeredHost, testBody)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            freshnessSeconds = 120,
            clock = TestClock(now),
        )

        val result = verifier.verify(header, null, testBody)
        val stale = assertIs<CallbackSignatureVerifier.Result.Stale>(result)
        assertEquals(500L, stale.ageSeconds)
    }

    @Test
    fun test22_futureDatedNegativeAge_returnsStale() = runTest {
        val signer = KeyPair.random()
        val signedAt = 1_700_000_500L
        val now = 1_700_000_000L // signedAt is in the future
        val header = signCallback(signer, signedAt, registeredHost, testBody)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            freshnessSeconds = 120,
            clock = TestClock(now),
        )

        val result = verifier.verify(header, null, testBody)
        val stale = assertIs<CallbackSignatureVerifier.Result.Stale>(result)
        assertEquals(-500L, stale.ageSeconds)
    }

    // ==================== Constructor validation (23-28) ====================

    @Test
    fun test23_malformedSigningKey_throwsOnConstruction() = runTest {
        assertFailsWith<IllegalArgumentException> {
            CallbackSignatureVerifier(
                signingKey = "not-a-G-key",
                registeredCallbackUrl = registeredCallbackUrl,
            )
        }
    }

    @Test
    fun test24_malformedCallbackUrl_throwsOnConstruction() = runTest {
        val accountId = KeyPair.random().getAccountId()
        // A URL string that the underlying parser rejects (illegal characters in
        // the scheme portion) surfaces as an IllegalArgumentException at
        // construction. The wrapper catch in the verifier normalizes the
        // platform-specific parse exception type to IllegalArgumentException.
        assertFailsWith<IllegalArgumentException> {
            CallbackSignatureVerifier(
                signingKey = accountId,
                registeredCallbackUrl = "ht!tp://[example",
            )
        }
    }

    @Test
    fun test25_httpNonLoopback_throwsOnConstruction() = runTest {
        val accountId = KeyPair.random().getAccountId()
        assertFailsWith<IllegalArgumentException> {
            CallbackSignatureVerifier(
                signingKey = accountId,
                registeredCallbackUrl = "http://attacker.example.com/cb",
            )
        }
    }

    @Test
    fun test26_httpLoopback_constructsSuccessfully() = runTest {
        val verifier = CallbackSignatureVerifier(
            signingKey = KeyPair.random().getAccountId(),
            registeredCallbackUrl = "http://localhost:8080/cb",
        )
        // No exception means success; sanity-check that verify still operates.
        val result = verifier.verify(null, null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.MissingHeader, result)
    }

    @Test
    fun test27_freshnessSecondsZero_throwsOnConstruction() = runTest {
        val accountId = KeyPair.random().getAccountId()
        assertFailsWith<IllegalArgumentException> {
            CallbackSignatureVerifier(
                signingKey = accountId,
                registeredCallbackUrl = registeredCallbackUrl,
                freshnessSeconds = 0,
            )
        }
    }

    @Test
    fun test28_freshnessSecondsAboveCap_throwsOnConstruction() = runTest {
        val accountId = KeyPair.random().getAccountId()
        assertFailsWith<IllegalArgumentException> {
            CallbackSignatureVerifier(
                signingKey = accountId,
                registeredCallbackUrl = registeredCallbackUrl,
                freshnessSeconds = 601,
            )
        }
    }

    // ==================== Crypto-layer normalisation (29) ====================

    @Test
    fun test29_shortSignatureBytes_returnsMalformed() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        // 63 bytes — one short of the Ed25519 64-byte signature length.
        val shortSignature = ByteArray(63)
        val header = "t=$now, s=${Base64.encode(shortSignature)}"

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        // The `KeyPair.verify` wrapper throws IllegalArgumentException on size mismatch;
        // the shared verifier maps that to MalformedHeader.
        val result = verifier.verify(header, null, testBody)
        assertEquals(CallbackSignatureVerifier.Result.MalformedHeader, result)
    }

    // ==================== Spec-conformance regression coverage (30-31) ====================

    @Test
    fun test30_bodyContainsPayloadSeparators_returnsValid() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        val bodyWithDots = ".foo.bar.$registeredHost.payload"
        val header = signCallback(signer, now, registeredHost, bodyWithDots)

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        // The canonical-payload assembly is unambiguous because both verifier and
        // signer agree on component order ("<t>.<host>.<body>"). No escaping needed.
        val result = verifier.verify(header, null, bodyWithDots)
        assertEquals(CallbackSignatureVerifier.Result.Valid, result)
    }

    @Test
    fun test31_whitespaceToleranceOnCommaSeparator_returnsValid() = runTest {
        val signer = KeyPair.random()
        val now = 1_700_000_000L
        val payload = "$now.$registeredHost.$testBody".encodeToByteArray()
        val signature = Base64.encode(signer.sign(payload))

        // No space after comma.
        val noSpace = "t=$now,s=$signature"
        // Multiple spaces.
        val multiSpace = "t=$now,   s=$signature"

        val verifier = CallbackSignatureVerifier(
            signingKey = signer.getAccountId(),
            registeredCallbackUrl = registeredCallbackUrl,
            clock = TestClock(now),
        )

        assertEquals(CallbackSignatureVerifier.Result.Valid, verifier.verify(noSpace, null, testBody))
        assertEquals(CallbackSignatureVerifier.Result.Valid, verifier.verify(multiSpace, null, testBody))
    }
}
