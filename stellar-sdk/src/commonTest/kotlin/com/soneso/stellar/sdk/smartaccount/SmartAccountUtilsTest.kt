//
//  SmartAccountUtilsTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartAccountUtilsTest {

    // MARK: - Test Vectors

    /**
     * Test Vector 1: High-S to Low-S normalization
     *
     * Input: DER signature with s > halfOrder
     * Expected: Normalized signature with s' = n - s
     */
    @Test
    fun testNormalizeSignature_highSToLowS() {
        // DER encoded signature (71 bytes)
        // r = 0x0102030405060708091011121314151617181920212223242526272829303132
        // s = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550 (high-S, exactly n - 1)
        val derSignature = hexToBytes(
            "3045022001020304050607080910111213141516171819202122232425262728293031320221" +
            "00ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550"
        )

        val expected = hexToBytes(
            "0102030405060708091011121314151617181920212223242526272829303132" +
            "0000000000000000000000000000000000000000000000000000000000000001"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size, "Normalized signature should be 64 bytes")
        assertEquals(expected.toHex(), result.toHex(), "Signature normalization failed")
    }

    /**
     * Test Vector 1b: Already low-S (no normalization needed)
     *
     * Input: DER signature with s < halfOrder
     * Expected: Same signature in compact format
     */
    @Test
    fun testNormalizeSignature_alreadyLowS() {
        // DER encoded signature (70 bytes)
        // r = 0x0102030405060708091011121314151617181920212223242526272829303132
        // s = 0x0000000000000000000000000000000000000000000000000000000000000005 (low-S)
        val derSignature = hexToBytes(
            "30440220010203040506070809101112131415161718192021222324252627282930313202" +
            "200000000000000000000000000000000000000000000000000000000000000005"
        )

        val expected = hexToBytes(
            "0102030405060708091011121314151617181920212223242526272829303132" +
            "0000000000000000000000000000000000000000000000000000000000000005"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size, "Normalized signature should be 64 bytes")
        assertEquals(expected.toHex(), result.toHex(), "Signature should remain unchanged")
    }

    /**
     * Test Vector 2: DER with leading zeros (33-byte r and s)
     */
    @Test
    fun testNormalizeSignature_withLeadingZeros() {
        // DER with 33-byte r and s (leading 0x00 for positive representation)
        // r = 0x00ff02030405060708091011121314151617181920212223242526272829303132
        // s = 0x0000000000000000000000000000000000000000000000000000000000000010
        val derSignature = hexToBytes(
            "3046022100ff02030405060708091011121314151617181920212223242526272829303132022100" +
            "0000000000000000000000000000000000000000000000000000000000000010"
        )

        val expected = hexToBytes(
            "ff02030405060708091011121314151617181920212223242526272829303132" +
            "0000000000000000000000000000000000000000000000000000000000000010"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size, "Normalized signature should be 64 bytes")
        assertEquals(expected.toHex(), result.toHex(), "Leading zeros should be stripped")
    }

    /**
     * Test Vector 3: Short r and s (less than 32 bytes)
     */
    @Test
    fun testNormalizeSignature_shortComponents() {
        // DER with short r (4 bytes) and s (5 bytes)
        // r = 0x01020304
        // s = 0x0506070809
        val derSignature = hexToBytes(
            "300d02040102030402050506070809"
        )

        val expected = hexToBytes(
            "0000000000000000000000000000000000000000000000000000000001020304" +
            "0000000000000000000000000000000000000000000000000000000506070809"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size, "Normalized signature should be 64 bytes")
        assertEquals(expected.toHex(), result.toHex(), "Short components should be left-padded")
    }

    /**
     * Test Vector 4: Signature with s at exactly half order
     *
     * This tests the boundary: s = halfOrder should remain unchanged
     */
    @Test
    fun testNormalizeSignature_exactHalfOrder() {
        // s = halfOrder = 0x7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8
        // This needs 0x00 prefix in DER because high bit is set
        val derSignature = hexToBytes(
            "3045022001020304050607080910111213141516171819202122232425262728293031320221" +
            "007fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8"
        )

        val expected = hexToBytes(
            "0102030405060708091011121314151617181920212223242526272829303132" +
            "7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size, "Normalized signature should be 64 bytes")
        assertEquals(expected.toHex(), result.toHex(), "Half order should not be normalized")
    }

    /**
     * Test Vector 5: Half order + 1 (should be normalized)
     *
     * s = halfOrder + 1 should be normalized to n - (halfOrder + 1)
     * Since n is odd: n = 2*halfOrder + 1, so n - (halfOrder + 1) = halfOrder
     */
    @Test
    fun testNormalizeSignature_halfOrderPlusOne() {
        // s = halfOrder + 1 = 0x7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a9
        // n = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551 (odd)
        // Normalized: n - s = halfOrder = 0x7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8
        // DER needs 0x00 prefix because high bit is set
        val derSignature = hexToBytes(
            "3045022001020304050607080910111213141516171819202122232425262728293031320221" +
            "007fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a9"
        )

        val expected = hexToBytes(
            "0102030405060708091011121314151617181920212223242526272829303132" +
            "7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size, "Normalized signature should be 64 bytes")
        assertEquals(expected.toHex(), result.toHex(), "s > halfOrder should be normalized")
    }

    // MARK: - Verification Test Vectors

    /**
     * Verification Test Vector: Realistic WebAuthn DER signature with high-S.
     *
     * This DER signature has structure typical of a real WebAuthn authenticator response:
     * - 33-byte r (leading 0x00 because high bit of r is set)
     * - 33-byte s (leading 0x00 because high bit of s is set, and s > n/2)
     *
     * DER breakdown:
     *   30 46       -- SEQUENCE, 70 bytes total
     *   02 21       -- INTEGER, 33 bytes (r with 0x00 padding)
     *   00 b23694f0367f3e621a8458fc24d1dce654be3e2e2c1bacea40cd7a5e7a134540
     *   02 21       -- INTEGER, 33 bytes (s with 0x00 padding)
     *   00 d7fbd22ba32e17ce0f862e83e9c43e768eb3cc7a4ce050f6f71f33f27ce97ba2
     *
     * r = 0xb23694f0367f3e621a8458fc24d1dce654be3e2e2c1bacea40cd7a5e7a134540
     * s = 0xd7fbd22ba32e17ce0f862e83e9c43e768eb3cc7a4ce050f6f71f33f27ce97ba2
     *
     * n =   0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551
     * n/2 = 0x7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8
     *
     * s (0xd7fb...) > n/2 (0x7fff...) => high-S, normalize to n - s
     * s' = n - s = 0x28042dd35cd1e832f079d17c163bc1892e332e335a374d8dfc9a96d07f79a9af
     */
    @Test
    fun testNormalizeSignature_verification_realisticWebAuthnHighS() {
        val derSignature = hexToBytes(
            "3046" +
            "022100b23694f0367f3e621a8458fc24d1dce654be3e2e2c1bacea40cd7a5e7a134540" +
            "022100d7fbd22ba32e17ce0f862e83e9c43e768eb3cc7a4ce050f6f71f33f27ce97ba2"
        )

        val expected = hexToBytes(
            "b23694f0367f3e621a8458fc24d1dce654be3e2e2c1bacea40cd7a5e7a134540" +
            "28042dd35cd1e832f079d17c163bc1892e332e335a374d8dfc9a96d07f79a9af"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size, "Compact signature must be exactly 64 bytes")
        assertEquals(
            expected.toHex(), result.toHex(),
            "High-S WebAuthn signature must be normalized to low-S compact form"
        )
    }

    /**
     * Verification Test Vector: Realistic WebAuthn DER signature with low-S (no normalization).
     *
     * DER breakdown:
     *   30 45       -- SEQUENCE, 69 bytes
     *   02 21       -- INTEGER, 33 bytes (r with 0x00 padding)
     *   00 e47b78d0e44411cf2c94d2e4f14dfc2b91cc8c18ae3d9141a2798cadc9c5c8aa
     *   02 20       -- INTEGER, 32 bytes (s, no padding needed - high bit is 0)
     *   1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521
     *
     * r = 0xe47b78d0e44411cf2c94d2e4f14dfc2b91cc8c18ae3d9141a2798cadc9c5c8aa
     * s = 0x1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521
     *
     * s (0x1c7a...) < n/2 (0x7fff...) => already low-S, no normalization
     */
    @Test
    fun testNormalizeSignature_verification_realisticWebAuthnLowS() {
        val derSignature = hexToBytes(
            "3045" +
            "022100e47b78d0e44411cf2c94d2e4f14dfc2b91cc8c18ae3d9141a2798cadc9c5c8aa" +
            "02201c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521"
        )

        val expected = hexToBytes(
            "e47b78d0e44411cf2c94d2e4f14dfc2b91cc8c18ae3d9141a2798cadc9c5c8aa" +
            "1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size, "Compact signature must be exactly 64 bytes")
        assertEquals(
            expected.toHex(), result.toHex(),
            "Low-S WebAuthn signature must pass through unchanged"
        )
    }

    /**
     * Verification Test Vector: s = exactly n - 1 (maximum high-S).
     *
     * When s = n - 1, normalized s = n - (n - 1) = 1.
     * This tests the extreme high-S case.
     *
     * s = n - 1 = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550
     * normalized s = n - s = 1
     */
    @Test
    fun testNormalizeSignature_verification_maxHighS() {
        // s = n - 1 (maximum possible s value in valid signature)
        val derSignature = hexToBytes(
            "3045" +
            "02207a2b3c4d5e6f708192a3b4c5d6e7f80112233445566778899aabbccddeeff00a" +
            "022100ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550"
        )

        // r unchanged, s normalized to 1
        val expected = hexToBytes(
            "7a2b3c4d5e6f708192a3b4c5d6e7f80112233445566778899aabbccddeeff00a" +
            "0000000000000000000000000000000000000000000000000000000000000001"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size)
        assertEquals(
            expected.toHex(), result.toHex(),
            "s = n-1 must normalize to s = 1"
        )
    }

    /**
     * Verification Test Vector: s = exactly n/2 + 1 (minimum high-S).
     *
     * When s = n/2 + 1 = halfOrder + 1, normalized s = n - (halfOrder + 1) = halfOrder.
     * This tests the boundary just above the normalization threshold.
     *
     * n   = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551
     * n/2 = 0x7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8
     * s   = 0x7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a9
     * s'  = n - s = 0x7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8
     */
    @Test
    fun testNormalizeSignature_verification_minHighS() {
        val derSignature = hexToBytes(
            "3045" +
            "02207a2b3c4d5e6f708192a3b4c5d6e7f80112233445566778899aabbccddeeff00a" +
            "0221007fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a9"
        )

        val expected = hexToBytes(
            "7a2b3c4d5e6f708192a3b4c5d6e7f80112233445566778899aabbccddeeff00a" +
            "7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size)
        assertEquals(
            expected.toHex(), result.toHex(),
            "s = halfOrder + 1 must normalize to halfOrder"
        )
    }

    /**
     * Verification Test Vector: s = exactly n/2 (maximum low-S, boundary case).
     *
     * When s = n/2 (halfOrder), it should NOT be normalized (s <= n/2 is fine).
     *
     * n/2 = 0x7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8
     */
    @Test
    fun testNormalizeSignature_verification_exactHalfOrder() {
        val derSignature = hexToBytes(
            "3045" +
            "02207a2b3c4d5e6f708192a3b4c5d6e7f80112233445566778899aabbccddeeff00a" +
            "0221007fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8"
        )

        val expected = hexToBytes(
            "7a2b3c4d5e6f708192a3b4c5d6e7f80112233445566778899aabbccddeeff00a" +
            "7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)

        assertEquals(64, result.size)
        assertEquals(
            expected.toHex(), result.toHex(),
            "s = halfOrder (boundary) must NOT be normalized"
        )
    }

    /**
     * Verifies the secp256r1 curve order constant used in normalizeSignature.
     *
     * The curve order n for secp256r1 (P-256/prime256v1) is defined in SEC 2:
     * n = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551
     *
     * This test verifies the constant is used correctly by testing that
     * s = n - 1 normalizes to 1, which can only happen if the constant is exact.
     */
    @Test
    fun testNormalizeSignature_curveOrderConstantVerification() {
        // If the curve order n is correct, then normalizing s = n - 1 yields s' = 1.
        // If the constant were off by even 1 bit, this test would fail.
        val derSignature = hexToBytes(
            "3045" +
            "02200000000000000000000000000000000000000000000000000000000000000001" +
            "022100ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550"
        )

        val result = SmartAccountUtils.normalizeSignature(derSignature)
        val sHex = result.copyOfRange(32, 64).toHex()

        assertEquals(
            "0000000000000000000000000000000000000000000000000000000000000001",
            sHex,
            "Curve order verification: n - (n-1) must equal 1"
        )
    }

    // MARK: - Error Cases

    /**
     * Test invalid DER format: wrong header
     */
    @Test
    fun testNormalizeSignature_invalidHeader() {
        val invalidDer = hexToBytes("31450220010203040506070809101112131415161718192021222324252627282930313202200000000000000000000000000000000000000000000000000000000000000005")

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.normalizeSignature(invalidDer)
        }
    }

    /**
     * Test invalid DER format: missing r marker
     */
    @Test
    fun testNormalizeSignature_missingRMarker() {
        val invalidDer = hexToBytes("30450320010203040506070809101112131415161718192021222324252627282930313202200000000000000000000000000000000000000000000000000000000000000005")

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.normalizeSignature(invalidDer)
        }
    }

    /**
     * Test invalid DER format: missing s marker
     */
    @Test
    fun testNormalizeSignature_missingSMarker() {
        val invalidDer = hexToBytes("30450220010203040506070809101112131415161718192021222324252627282930313203200000000000000000000000000000000000000000000000000000000000000005")

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.normalizeSignature(invalidDer)
        }
    }

    /**
     * Test invalid DER format: truncated signature
     */
    @Test
    fun testNormalizeSignature_truncated() {
        val truncatedDer = hexToBytes("3045022001020304050607080910111213141516")

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.normalizeSignature(truncatedDer)
        }
    }

    /**
     * Test invalid DER format: too short
     */
    @Test
    fun testNormalizeSignature_tooShort() {
        val tooShort = hexToBytes("300102")

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.normalizeSignature(tooShort)
        }
    }

    // ========================================================================
    // MARK: - Public Key Extraction Tests
    // ========================================================================

    // -- Strategy 1: Direct public key --

    /**
     * Strategy 1: Valid 65-byte uncompressed secp256r1 key passed directly.
     */
    @Test
    fun testExtractPublicKeyFromRegistration_directPublicKey() {
        val xCoord = ByteArray(32) { 0xAA.toByte() }
        val yCoord = ByteArray(32) { 0xBB.toByte() }
        val directKey = byteArrayOf(0x04) + xCoord + yCoord

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = directKey
        )

        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    /**
     * Strategy 1: Public key wrapped in COSE/SPKI encoding (longer than 65 bytes).
     *
     * The last 65 bytes contain the uncompressed public key (0x04 prefix + X + Y).
     * This simulates a COSE-wrapped key with extra header bytes.
     */
    @Test
    fun testExtractPublicKeyFromRegistration_wrappedPublicKey() {
        val xCoord = ByteArray(32) { 0xCC.toByte() }
        val yCoord = ByteArray(32) { 0xDD.toByte() }
        val rawKey = byteArrayOf(0x04) + xCoord + yCoord

        // Prepend COSE/SPKI header bytes (simulated)
        val wrappedKey = byteArrayOf(
            0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86.toByte(),
            0x48, 0xce.toByte(), 0x3d, 0x02, 0x01, 0x06, 0x08, 0x2a,
            0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07
        ) + rawKey

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = wrappedKey
        )

        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    /**
     * Strategy 1: Public key with invalid prefix falls through to other strategies.
     * If no other data is provided, an exception is thrown.
     */
    @Test
    fun testExtractPublicKeyFromRegistration_invalidDirectKeyNoFallback() {
        // Wrong prefix byte (0x02 = compressed, not 0x04 = uncompressed)
        val invalidKey = byteArrayOf(0x02) + ByteArray(64) { 0x11 }

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(
                publicKey = invalidKey
            )
        }
    }

    /**
     * Strategy 1 fallthrough to Strategy 3: Invalid direct key falls through to
     * attestation object extraction.
     */
    @Test
    fun testExtractPublicKeyFromRegistration_invalidDirectKeyFallsToAttestationObject() {
        val xCoord = ByteArray(32) { 0xEE.toByte() }
        val yCoord = ByteArray(32) { 0xFF.toByte() }

        // Invalid direct key (wrong prefix)
        val invalidKey = byteArrayOf(0x02) + ByteArray(64) { 0x11 }

        // Valid attestation object with COSE key
        val attestationObject = buildAttestationObject(xCoord, yCoord)

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = invalidKey,
            attestationObject = attestationObject
        )

        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    // -- Strategy 2: Authenticator data parsing --

    /**
     * Strategy 2: Extract public key from authenticator data.
     *
     * Authenticator data structure:
     *   [0..31]   rpIdHash       (32 bytes)
     *   [32]      flags          (1 byte, bit 6 = AT flag)
     *   [33..36]  signCount      (4 bytes)
     *   [37..52]  aaguid         (16 bytes)
     *   [53..54]  credIdLen      (2 bytes, big-endian)
     *   [55..55+N-1] credId     (N bytes)
     *   [55+N..]  COSE key
     */
    @Test
    fun testExtractPublicKeyFromRegistration_fromAuthenticatorData() {
        val xCoord = ByteArray(32) { (it + 1).toByte() }
        val yCoord = ByteArray(32) { (it + 33).toByte() }

        val authData = buildAuthenticatorData(
            credentialId = ByteArray(16) { 0x42 },
            xCoord = xCoord,
            yCoord = yCoord
        )

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            authenticatorData = authData
        )

        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    /**
     * Strategy 2: Authenticator data with a longer credential ID (32 bytes).
     * Verifies that the credential ID length field is correctly parsed.
     */
    @Test
    fun testExtractPublicKeyFromAuthenticatorData_longCredentialId() {
        val xCoord = ByteArray(32) { 0xAA.toByte() }
        val yCoord = ByteArray(32) { 0xBB.toByte() }

        val authData = buildAuthenticatorData(
            credentialId = ByteArray(32) { 0x55 },
            xCoord = xCoord,
            yCoord = yCoord
        )

        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)

        assertNotNull(result)
        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    /**
     * Strategy 2: Authenticator data with a very long credential ID (128 bytes).
     * Tests that the 16-bit big-endian length field handles larger values.
     */
    @Test
    fun testExtractPublicKeyFromAuthenticatorData_veryLongCredentialId() {
        val xCoord = ByteArray(32) { 0x11 }
        val yCoord = ByteArray(32) { 0x22 }

        val authData = buildAuthenticatorData(
            credentialId = ByteArray(128) { 0x77 },
            xCoord = xCoord,
            yCoord = yCoord
        )

        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)

        assertNotNull(result)
        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    /**
     * Strategy 2: Returns null when AT flag is not set (no attested credential data).
     */
    @Test
    fun testExtractPublicKeyFromAuthenticatorData_noATFlag() {
        // Build authenticator data without AT flag (bit 6 not set)
        val rpIdHash = ByteArray(32) { 0x00 }
        val flags = byteArrayOf(0x01) // UP flag only, no AT flag
        val signCount = ByteArray(4) { 0x00 }

        val authData = rpIdHash + flags + signCount

        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)
        assertNull(result)
    }

    /**
     * Strategy 2: Returns null when authenticator data is too short.
     */
    @Test
    fun testExtractPublicKeyFromAuthenticatorData_tooShort() {
        val shortData = ByteArray(30) { 0x00 }

        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(shortData)
        assertNull(result)
    }

    /**
     * Strategy 2: Returns null when authenticator data has AT flag but is truncated
     * before the COSE key data ends.
     */
    @Test
    fun testExtractPublicKeyFromAuthenticatorData_truncatedCOSEKey() {
        val rpIdHash = ByteArray(32) { 0x00 }
        val flags = byteArrayOf(0x41) // UP + AT flags
        val signCount = ByteArray(4) { 0x00 }
        val aaguid = ByteArray(16) { 0x00 }
        val credIdLen = byteArrayOf(0x00, 0x10) // 16 bytes
        val credId = ByteArray(16) { 0x42 }
        // Only partial COSE key (not enough data for X + separator + Y)
        val partialCose = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte()
        )

        val authData = rpIdHash + flags + signCount + aaguid + credIdLen + credId + partialCose

        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)
        assertNull(result)
    }

    // -- Strategy 3: Attestation object pattern matching --

    /**
     * Strategy 3: Extract public key from attestation object by pattern matching
     * the COSE key prefix.
     */
    @Test
    fun testExtractPublicKeyFromAttestationObject_validCOSEPrefix() {
        val xCoord = ByteArray(32) { 0xAA.toByte() }
        val yCoord = ByteArray(32) { 0xBB.toByte() }

        val attestationObject = buildAttestationObject(xCoord, yCoord)

        val result = SmartAccountUtils.extractPublicKeyFromAttestationObject(attestationObject)

        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    /**
     * Strategy 3: COSE prefix not found in attestation object.
     */
    @Test
    fun testExtractPublicKeyFromAttestationObject_missingCOSEPrefix() {
        val attestationData = ByteArray(100) { 0xFF.toByte() }

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(attestationData)
        }
    }

    /**
     * Strategy 3: COSE prefix found but data is truncated.
     */
    @Test
    fun testExtractPublicKeyFromAttestationObject_truncatedAfterPrefix() {
        val cosePrefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
            0x01, 0x21, 0x58, 0x20.toByte()
        )
        val attestationData = cosePrefix + ByteArray(10) // Not enough data

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(attestationData)
        }
    }

    // -- extractPublicKeyFromRegistration with attestation object only --

    /**
     * extractPublicKeyFromRegistration with only attestationObject delegates to
     * extractPublicKeyFromAttestationObject and returns the same result.
     */
    @Test
    fun testExtractPublicKeyFromRegistration_attestationObjectOnly() {
        val xCoord = ByteArray(32) { 0xAA.toByte() }
        val yCoord = ByteArray(32) { 0xBB.toByte() }

        val attestationObject = buildAttestationObject(xCoord, yCoord)

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(attestationObject = attestationObject)

        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    // -- extractPublicKeyFromRegistration fallback chain --

    /**
     * Full fallback chain: invalid direct key -> authenticator data -> success.
     * Strategy 1 fails, Strategy 2 succeeds.
     */
    @Test
    fun testExtractPublicKeyFromRegistration_fallbackToAuthenticatorData() {
        val xCoord = ByteArray(32) { 0x11 }
        val yCoord = ByteArray(32) { 0x22 }

        // Invalid direct key (wrong size)
        val invalidKey = ByteArray(32) { 0x00 }

        val authData = buildAuthenticatorData(
            credentialId = ByteArray(20) { 0x33 },
            xCoord = xCoord,
            yCoord = yCoord
        )

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = invalidKey,
            authenticatorData = authData
        )

        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    /**
     * Full fallback chain: invalid direct key -> invalid authenticator data -> attestation object -> success.
     * Strategy 1 and 2 fail, Strategy 3 succeeds.
     */
    @Test
    fun testExtractPublicKeyFromRegistration_fallbackToAttestationObject() {
        val xCoord = ByteArray(32) { 0x44 }
        val yCoord = ByteArray(32) { 0x55 }

        // Invalid direct key
        val invalidKey = ByteArray(10) { 0x00 }

        // Authenticator data without AT flag
        val invalidAuthData = ByteArray(37) { 0x00 } // flags = 0x00, no AT

        val attestationObject = buildAttestationObject(xCoord, yCoord)

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = invalidKey,
            authenticatorData = invalidAuthData,
            attestationObject = attestationObject
        )

        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    /**
     * All strategies fail: no valid data in any source.
     */
    @Test
    fun testExtractPublicKeyFromRegistration_allStrategiesFail() {
        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(
                publicKey = null,
                authenticatorData = null,
                attestationObject = null
            )
        }
    }

    /**
     * Empty public key array should not be treated as a valid key.
     */
    @Test
    fun testExtractPublicKeyFromRegistration_emptyPublicKey() {
        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(
                publicKey = ByteArray(0)
            )
        }
    }

    /**
     * Strategy 2 via extractPublicKeyFromRegistration: authenticator data with
     * credential ID length encoded in big-endian across both bytes.
     * credentialIdLength = 0x01 << 8 | 0x00 = 256 bytes
     */
    @Test
    fun testExtractPublicKeyFromAuthenticatorData_bigEndianCredIdLength() {
        val xCoord = ByteArray(32) { 0xDE.toByte() }
        val yCoord = ByteArray(32) { 0xAD.toByte() }

        val authData = buildAuthenticatorData(
            credentialId = ByteArray(256) { 0x88.toByte() },
            xCoord = xCoord,
            yCoord = yCoord
        )

        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)

        assertNotNull(result)
        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertTrue(xCoord.contentEquals(result.copyOfRange(1, 33)))
        assertTrue(yCoord.contentEquals(result.copyOfRange(33, 65)))
    }

    // ========================================================================
    // MARK: - Test Helper Functions
    // ========================================================================

    /**
     * Builds a synthetic authenticator data blob for testing.
     *
     * Layout:
     *   [0..31]   rpIdHash       (32 bytes, zeros)
     *   [32]      flags          (1 byte, 0x41 = UP + AT)
     *   [33..36]  signCount      (4 bytes, zeros)
     *   [37..52]  aaguid         (16 bytes, zeros)
     *   [53..54]  credIdLen      (2 bytes, big-endian)
     *   [55..55+N-1] credId     (N bytes)
     *   [55+N..]  COSE public key (10 prefix + 32 X + 3 separator + 32 Y)
     */
    private fun buildAuthenticatorData(
        credentialId: ByteArray,
        xCoord: ByteArray,
        yCoord: ByteArray
    ): ByteArray {
        val rpIdHash = ByteArray(32) { 0x00 }
        val flags = byteArrayOf(0x41) // UP (bit 0) + AT (bit 6) = 0x01 | 0x40
        val signCount = ByteArray(4) { 0x00 }
        val aaguid = ByteArray(16) { 0x00 }
        val credIdLen = byteArrayOf(
            ((credentialId.size shr 8) and 0xFF).toByte(),
            (credentialId.size and 0xFF).toByte()
        )

        // COSE key: ES256 (P-256) prefix + X + separator + Y
        val cosePrefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
            0x01, 0x21, 0x58, 0x20.toByte()
        )
        val separator = byteArrayOf(0x22, 0x58, 0x20.toByte())

        return rpIdHash + flags + signCount + aaguid + credIdLen +
            credentialId + cosePrefix + xCoord + separator + yCoord
    }

    /**
     * Builds a synthetic attestation object containing a COSE key.
     *
     * The attestation object is a minimal blob with some padding before
     * and after the COSE key structure. This mimics CBOR-encoded attestation
     * without requiring a full CBOR encoder.
     */
    private fun buildAttestationObject(
        xCoord: ByteArray,
        yCoord: ByteArray
    ): ByteArray {
        val cosePrefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
            0x01, 0x21, 0x58, 0x20.toByte()
        )
        val separator = byteArrayOf(0x22, 0x58, 0x20.toByte())

        // Padding before and after to simulate real attestation structure
        return ByteArray(20) + cosePrefix + xCoord + separator + yCoord + ByteArray(10)
    }

    // MARK: - Hex Helper Functions

    /**
     * Parses a lowercase hex string to a BigInteger (unsigned, base 16).
     */
    private fun bigIntFromHex(hex: String): BigInteger = BigInteger.parseString(hex, 16)

    /**
     * Converts hex string to byte array.
     */
    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("\n", "")
        require(cleanHex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * Converts byte array to hex string.
     */
    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }

    // ========================================================================
    // MARK: - parseDerSignature Tests
    // ========================================================================

    /**
     * Valid DER signature: standard 32-byte R and S (no leading zero padding).
     * Verifies that R and S are returned verbatim when no stripping is needed.
     */
    @Test
    fun testParseDerSignature_validStandard() {
        // r = 32 bytes, s = 32 bytes — no 0x00 prefix needed (high bits are 0)
        val rBytes = hexToBytes("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val sBytes = hexToBytes("2a3b4c5d6e7f8091a2b3c4d5e6f70819283746556677889900aabbccddeeff01")

        val der = buildDer(rBytes, sBytes)
        val (r, s) = SmartAccountUtils.parseDerSignature(der)

        val expectedR = bigIntFromHex("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val expectedS = bigIntFromHex("2a3b4c5d6e7f8091a2b3c4d5e6f70819283746556677889900aabbccddeeff01")
        assertEquals(expectedR, r, "R should match input exactly")
        assertEquals(expectedS, s, "S should match input exactly")
    }

    /**
     * Valid DER signature: R has a leading 0x00 padding byte.
     * The padding must be stripped, leaving a 32-byte R.
     */
    @Test
    fun testParseDerSignature_leadingZeroStrippedFromR() {
        // DER encodes r with 0x00 prefix when high bit is set (to keep it positive in ASN.1)
        val rBytesRaw = hexToBytes("b23694f0367f3e621a8458fc24d1dce654be3e2e2c1bacea40cd7a5e7a134540")
        val sBytes = hexToBytes("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")

        // Build DER with 0x00-padded r (33 bytes)
        val rDerBytes = byteArrayOf(0x00) + rBytesRaw
        val der = buildDer(rDerBytes, sBytes)
        val (r, s) = SmartAccountUtils.parseDerSignature(der)

        val expectedR = bigIntFromHex("b23694f0367f3e621a8458fc24d1dce654be3e2e2c1bacea40cd7a5e7a134540")
        val expectedS = bigIntFromHex("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        assertEquals(expectedR, r, "Leading 0x00 must be stripped from R")
        assertEquals(expectedS, s, "S should be unchanged")
    }

    /**
     * Valid DER signature: S has a leading 0x00 padding byte.
     * The padding must be stripped, leaving a 32-byte S.
     */
    @Test
    fun testParseDerSignature_leadingZeroStrippedFromS() {
        val rBytes = hexToBytes("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val sBytesRaw = hexToBytes("d7fbd22ba32e17ce0f862e83e9c43e768eb3cc7a4ce050f6f71f33f27ce97ba2")

        // Build DER with 0x00-padded s (33 bytes)
        val sDerBytes = byteArrayOf(0x00) + sBytesRaw
        val der = buildDer(rBytes, sDerBytes)
        val (r, s) = SmartAccountUtils.parseDerSignature(der)

        val expectedR = bigIntFromHex("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val expectedS = bigIntFromHex("d7fbd22ba32e17ce0f862e83e9c43e768eb3cc7a4ce050f6f71f33f27ce97ba2")
        assertEquals(expectedR, r, "R should be unchanged")
        assertEquals(expectedS, s, "Leading 0x00 must be stripped from S")
    }

    /**
     * Minimum valid DER signature: 1-byte R and 1-byte S.
     * Both components are small positive integers with no padding needed.
     */
    @Test
    fun testParseDerSignature_minimumValid() {
        val rBytes = byteArrayOf(0x01)
        val sBytes = byteArrayOf(0x02)

        val der = buildDer(rBytes, sBytes)
        val (r, s) = SmartAccountUtils.parseDerSignature(der)

        assertEquals(BigInteger.ONE, r, "1-byte R should be parsed correctly")
        assertEquals(BigInteger.TWO, s, "1-byte S should be parsed correctly")
    }

    /**
     * Invalid header: first byte is not 0x30.
     */
    @Test
    fun testParseDerSignature_invalidHeader() {
        val rBytes = hexToBytes("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val sBytes = hexToBytes("2a3b4c5d6e7f8091a2b3c4d5e6f70819283746556677889900aabbccddeeff01")
        val validDer = buildDer(rBytes, sBytes)

        // Replace header byte 0x30 with 0x31
        val invalidDer = validDer.copyOf()
        invalidDer[0] = 0x31

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(invalidDer)
        }
    }

    /**
     * Wrong total length: declared length is one less than actual content.
     */
    @Test
    fun testParseDerSignature_wrongTotalLength() {
        val rBytes = hexToBytes("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val sBytes = hexToBytes("2a3b4c5d6e7f8091a2b3c4d5e6f70819283746556677889900aabbccddeeff01")
        val validDer = buildDer(rBytes, sBytes)

        // Decrement the length byte so it no longer matches the actual data
        val invalidDer = validDer.copyOf()
        invalidDer[1] = (invalidDer[1] - 1).toByte()

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(invalidDer)
        }
    }

    /**
     * Missing R marker: byte at offset 2 is 0x03 instead of 0x02.
     */
    @Test
    fun testParseDerSignature_missingRMarker() {
        val rBytes = hexToBytes("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val sBytes = hexToBytes("2a3b4c5d6e7f8091a2b3c4d5e6f70819283746556677889900aabbccddeeff01")
        val validDer = buildDer(rBytes, sBytes)

        val invalidDer = validDer.copyOf()
        invalidDer[2] = 0x03 // wrong tag for R

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(invalidDer)
        }
    }

    /**
     * Missing S marker: byte at the start of the S component is 0x03 instead of 0x02.
     */
    @Test
    fun testParseDerSignature_missingSMarker() {
        val rBytes = hexToBytes("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val sBytes = hexToBytes("2a3b4c5d6e7f8091a2b3c4d5e6f70819283746556677889900aabbccddeeff01")
        val validDer = buildDer(rBytes, sBytes)

        // Offset of S marker: 2 (header+len) + 2 (R tag+len) + rBytes.size
        val sMarkerOffset = 2 + 2 + rBytes.size
        val invalidDer = validDer.copyOf()
        invalidDer[sMarkerOffset] = 0x03 // wrong tag for S

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(invalidDer)
        }
    }

    /**
     * Truncated R: R length field claims more bytes than are present.
     */
    @Test
    fun testParseDerSignature_truncatedR() {
        val rBytes = hexToBytes("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val sBytes = hexToBytes("2a3b4c5d6e7f8091a2b3c4d5e6f70819283746556677889900aabbccddeeff01")
        val validDer = buildDer(rBytes, sBytes)

        // Inflate the R length byte to exceed the buffer
        val invalidDer = validDer.copyOf()
        invalidDer[3] = (rBytes.size + 10).toByte() // R length now exceeds available bytes

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(invalidDer)
        }
    }

    /**
     * Truncated S: S length field claims more bytes than are present.
     */
    @Test
    fun testParseDerSignature_truncatedS() {
        val rBytes = hexToBytes("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val sBytes = hexToBytes("2a3b4c5d6e7f8091a2b3c4d5e6f70819283746556677889900aabbccddeeff01")
        val validDer = buildDer(rBytes, sBytes)

        // S length byte is at offset: 2 (header+len) + 2 (R tag+len) + rBytes.size + 1 (S tag)
        val sLenOffset = 2 + 2 + rBytes.size + 1
        val invalidDer = validDer.copyOf()
        invalidDer[sLenOffset] = (sBytes.size + 10).toByte() // S length now exceeds available bytes

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(invalidDer)
        }
    }

    /**
     * Trailing bytes: extra bytes after the S component.
     * The total length byte is patched to match, but the DER structure has trailing garbage.
     */
    @Test
    fun testParseDerSignature_trailingBytes() {
        val rBytes = hexToBytes("1c7ab8e46f91d3f9dbff8c50a37a0d13bbf835ac31c5d0da2dbf1e8a91c10521")
        val sBytes = hexToBytes("2a3b4c5d6e7f8091a2b3c4d5e6f70819283746556677889900aabbccddeeff01")
        val validDer = buildDer(rBytes, sBytes)

        // Append 2 extra bytes and update the total length field
        val withTrailing = validDer + byteArrayOf(0xDE.toByte(), 0xAD.toByte())
        withTrailing[1] = (withTrailing[1].toInt() and 0xFF + 2).toByte()

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(withTrailing)
        }
    }

    /**
     * R too short: the DER header is present but R length is 0 (empty R component).
     */
    @Test
    fun testParseDerSignature_emptyR() {
        // Manually build a DER with 0-length R: 30 04 02 00 02 01 05
        val der = byteArrayOf(
            0x30, 0x05,       // SEQUENCE, 5 bytes
            0x02, 0x00,       // INTEGER R, length 0  -- invalid
            0x02, 0x01, 0x05  // INTEGER S = 5
        )

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
    }

    /**
     * R all zeros after stripping: R = 0x00 is an invalid ECDSA value.
     */
    @Test
    fun testParseDerSignature_rAllZeros() {
        // Manually build: R = 0x00, S = 0x01
        val der = byteArrayOf(
            0x30, 0x06,       // SEQUENCE, 6 bytes
            0x02, 0x01, 0x00, // INTEGER R = 0
            0x02, 0x01, 0x01  // INTEGER S = 1
        )

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(
            ex.message?.contains("r component is zero") == true,
            "Exception message should mention r component is zero"
        )
    }

    /**
     * S all zeros after stripping: S = 0x00 is an invalid ECDSA value.
     */
    @Test
    fun testParseDerSignature_sAllZeros() {
        // Manually build: R = 0x01, S = 0x00
        val der = byteArrayOf(
            0x30, 0x06,       // SEQUENCE, 6 bytes
            0x02, 0x01, 0x01, // INTEGER R = 1
            0x02, 0x01, 0x00  // INTEGER S = 0
        )

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(
            ex.message?.contains("s component is zero") == true,
            "Exception message should mention s component is zero"
        )
    }

    /**
     * R exceeds 32 bytes after stripping: secp256r1 R must fit in 32 bytes.
     *
     * We build a DER where R encodes 33 significant bytes (no leading zero),
     * which exceeds the 256-bit curve field size.
     */
    @Test
    fun testParseDerSignature_rExceeds32Bytes() {
        // 33 non-zero bytes for R (no leading 0x00, so all 33 are significant)
        val rBytes = ByteArray(33) { (it + 1).toByte() }
        val sBytes = byteArrayOf(0x01)

        val der = buildDer(rBytes, sBytes)

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
    }

    /**
     * S exceeds 32 bytes after stripping: secp256r1 S must fit in 32 bytes.
     */
    @Test
    fun testParseDerSignature_sExceeds32Bytes() {
        val rBytes = byteArrayOf(0x01)
        // 33 non-zero bytes for S (no leading 0x00)
        val sBytes = ByteArray(33) { (it + 1).toByte() }

        val der = buildDer(rBytes, sBytes)

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
    }

    // secp256r1 curve order n used in range validation tests:
    // n = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551

    /**
     * R equals the curve order n: parseDerSignature must reject it.
     *
     * Valid R values are in [1, n-1]. R = n is out of range.
     */
    @Test
    fun testParseDerSignature_rEqualsCurveOrder() {
        // n = ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551
        // High bit is set, so DER requires a 0x00 prefix to keep the integer positive (33 bytes).
        val nWithPrefix = hexToBytes("00ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551")
        val sBytes = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001")

        val der = buildDer(nWithPrefix, sBytes)

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(
            ex.message?.contains("r component exceeds curve order") == true,
            "Exception message should mention r component exceeds curve order"
        )
    }

    /**
     * S equals the curve order n: parseDerSignature must reject it.
     *
     * Valid S values are in [1, n-1]. S = n is out of range.
     */
    @Test
    fun testParseDerSignature_sEqualsCurveOrder() {
        val rBytes = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001")
        // n with 0x00 prefix (33 bytes in DER)
        val nWithPrefix = hexToBytes("00ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551")

        val der = buildDer(rBytes, nWithPrefix)

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(
            ex.message?.contains("s component exceeds curve order") == true,
            "Exception message should mention s component exceeds curve order"
        )
    }

    /**
     * R = n + 1: parseDerSignature must reject it.
     *
     * n + 1 = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632552
     * This value fits in 32 bytes (high bit is set, so DER needs a 0x00 prefix — 33 bytes total).
     */
    @Test
    fun testParseDerSignature_rExceedsCurveOrder() {
        // n + 1 = ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632552
        val nPlusOneWithPrefix = hexToBytes("00ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632552")
        val sBytes = hexToBytes("0000000000000000000000000000000000000000000000000000000000000001")

        val der = buildDer(nPlusOneWithPrefix, sBytes)

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(
            ex.message?.contains("r component exceeds curve order") == true,
            "Exception message should mention r component exceeds curve order"
        )
    }

    /**
     * R = n - 1: parseDerSignature must accept it (valid upper boundary).
     *
     * n - 1 = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550
     * High bit is set, so DER encoding requires a 0x00 prefix (33 bytes).
     * After stripping the prefix, the 32-byte value is returned.
     * S is encoded as a small integer; parseDerSignature strips its leading zeros.
     */
    @Test
    fun testParseDerSignature_rJustBelowCurveOrder() {
        // n - 1 = ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550
        val nMinusOneRaw = hexToBytes("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550")
        val nMinusOneWithPrefix = byteArrayOf(0x00) + nMinusOneRaw
        val sBytes = byteArrayOf(0x01)

        val der = buildDer(nMinusOneWithPrefix, sBytes)
        val (r, s) = SmartAccountUtils.parseDerSignature(der)

        val expectedR = bigIntFromHex("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550")
        assertEquals(expectedR, r, "R = n-1 should be accepted and returned as BigInteger")
        assertEquals(BigInteger.ONE, s, "S should be returned as-is")
    }

    /**
     * S = n - 1: parseDerSignature must accept it (valid upper boundary).
     *
     * Although normalizeSignature would then fold this high-S value down to 1,
     * parseDerSignature itself only enforces the range constraint [1, n-1].
     * R is encoded as a small integer; parseDerSignature strips its leading zeros.
     */
    @Test
    fun testParseDerSignature_sJustBelowCurveOrder() {
        val rBytes = byteArrayOf(0x01)
        // n - 1 = ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550
        val nMinusOneRaw = hexToBytes("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550")
        val nMinusOneWithPrefix = byteArrayOf(0x00) + nMinusOneRaw

        val der = buildDer(rBytes, nMinusOneWithPrefix)
        val (r, s) = SmartAccountUtils.parseDerSignature(der)

        val expectedS = bigIntFromHex("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550")
        assertEquals(BigInteger.ONE, r, "R should be returned as-is")
        assertEquals(expectedS, s, "S = n-1 should be accepted and returned as BigInteger")
    }

    /**
     * Builds a minimal DER-encoded signature from raw R and S byte arrays.
     *
     * Format: `0x30 [total_len] 0x02 [r_len] [r_bytes] 0x02 [s_len] [s_bytes]`
     *
     * Note: this helper does NOT add a leading 0x00 byte even when the high bit of
     * the first byte of R or S is set. That is intentional — tests that need the
     * 0x00 padding pass in the pre-padded bytes explicitly.
     */
    private fun buildDer(rBytes: ByteArray, sBytes: ByteArray): ByteArray {
        val totalLen = 2 + rBytes.size + 2 + sBytes.size
        return byteArrayOf(
            0x30, totalLen.toByte(),
            0x02, rBytes.size.toByte()
        ) + rBytes + byteArrayOf(
            0x02, sBytes.size.toByte()
        ) + sBytes
    }
}
