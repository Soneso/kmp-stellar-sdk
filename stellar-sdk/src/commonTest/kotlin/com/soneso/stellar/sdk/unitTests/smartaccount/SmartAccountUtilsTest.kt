//
//  SmartAccountUtilsTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountUtils
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [SmartAccountUtils].
 *
 * Covers:
 * - DER signature parsing and validation
 * - Low-S signature normalization
 * - Public key extraction (direct, authenticator data, attestation object)
 * - Contract salt computation
 * - Contract address derivation
 * - findSubarray helper
 */
class SmartAccountUtilsTest {

    // ========================================================================
    // Known secp256r1 test point used across public key extraction tests.
    // This is the NIST P-256 generator point G, which lies on the curve.
    // ========================================================================

    private val generatorX = hexToBytes(
        "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"
    )
    private val generatorY = hexToBytes(
        "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"
    )

    // Uncompressed form: 0x04 || X || Y (65 bytes)
    private val validUncompressedKey: ByteArray
        get() = byteArrayOf(0x04) + generatorX + generatorY

    // ========================================================================
    // parseDerSignature
    // ========================================================================

    @Test
    fun parseDerSignature_validMinimalSignature() {
        // Build a valid DER signature with small R and S values
        val r = byteArrayOf(0x01)
        val s = byteArrayOf(0x01)
        val der = buildDer(r, s)
        val (rBig, sBig) = SmartAccountUtils.parseDerSignature(der)
        assertEquals("1", rBig.toString(10))
        assertEquals("1", sBig.toString(10))
    }

    @Test
    fun parseDerSignature_valid32ByteComponents() {
        // 32-byte R and S, both well below curve order
        val r = ByteArray(32) { 0x01 }
        val s = ByteArray(32) { 0x02 }
        val (rBig, sBig) = SmartAccountUtils.parseDerSignature(buildDer(r, s))
        assertNotNull(rBig)
        assertNotNull(sBig)
    }

    @Test
    fun parseDerSignature_stripsLeadingZeroPadding() {
        // DER encodes a high-bit value with a leading 0x00 to keep it positive.
        // R = 0x00 || 32 bytes with high bit set; S = 0x00 || 1 byte
        val rInner = ByteArray(32) { if (it == 0) 0x80.toByte() else 0x01.toByte() }
        val rPadded = byteArrayOf(0x00) + rInner
        val s = byteArrayOf(0x00, 0x01)
        val der = buildDer(rPadded, s)
        val (rBig, sBig) = SmartAccountUtils.parseDerSignature(der)
        assertNotNull(rBig)
        assertEquals("1", sBig.toString(10))
    }

    @Test
    fun parseDerSignature_tooShort() {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(ByteArray(7))
        }
        assertTrue(ex.message!!.contains("Invalid DER signature format"))
    }

    @Test
    fun parseDerSignature_wrongLeadingByte() {
        val der = byteArrayOf(0x31, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x01)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("Invalid DER signature format"))
    }

    @Test
    fun parseDerSignature_lengthMismatch() {
        // Declare total length as 7 but actual remaining is 6
        val der = byteArrayOf(0x30, 0x07, 0x02, 0x01, 0x01, 0x02, 0x01, 0x01)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("declared length does not match"))
    }

    @Test
    fun parseDerSignature_missingRMarker() {
        // 0x03 instead of 0x02 for R marker
        val der = byteArrayOf(0x30, 0x06, 0x03, 0x01, 0x01, 0x02, 0x01, 0x01)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("missing r component marker"))
    }

    @Test
    fun parseDerSignature_zeroLengthR() {
        // R length = 0. Structure: 0x30 0x06 [0x02 0x00] [0x02 0x02 0x01 0x01]
        // Total remaining = 2 (r envelope) + 4 (s envelope with 2-byte value) = 6.
        // Full size = 8 bytes, which passes the minimum-length check (>= 8).
        val der = byteArrayOf(0x30, 0x06, 0x02, 0x00, 0x02, 0x02, 0x01, 0x01)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("truncated r component"))
    }

    @Test
    fun parseDerSignature_truncatedRComponent() {
        // R length claims 10 bytes but only 1 follows
        val der = byteArrayOf(0x30, 0x06, 0x02, 0x0A, 0x01, 0x02, 0x01, 0x01)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("truncated r component"))
    }

    @Test
    fun parseDerSignature_missingSMarker() {
        // S marker byte is 0x03 instead of 0x02
        val der = byteArrayOf(0x30, 0x06, 0x02, 0x01, 0x01, 0x03, 0x01, 0x01)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("missing s component marker"))
    }

    @Test
    fun parseDerSignature_zeroLengthS() {
        // S length = 0. Structure: 0x30 0x06 [0x02 0x02 0x01 0x01] [0x02 0x00]
        // Total remaining = 4 (r envelope with 2-byte value) + 2 (s envelope) = 6.
        // Full size = 8 bytes, which passes the minimum-length check (>= 8).
        val der = byteArrayOf(0x30, 0x06, 0x02, 0x02, 0x01, 0x01, 0x02, 0x00)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("truncated s component"))
    }

    @Test
    fun parseDerSignature_truncatedSComponent() {
        val der = byteArrayOf(0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x0A, 0x01)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("truncated s component"))
    }

    @Test
    fun parseDerSignature_trailingBytesAfterS() {
        // Valid DER content followed by an extra byte.
        // buildDer sets the length correctly, so appending creates an outer-length mismatch.
        val validDer = buildDer(byteArrayOf(0x01), byteArrayOf(0x01))
        val withTrailing = validDer + byteArrayOf(0xFF.toByte())
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(withTrailing)
        }
        assertTrue(ex.message!!.contains("declared length does not match"))
    }

    @Test
    fun parseDerSignature_trailingBytesInsideEnvelope() {
        // Create a DER where the outer length includes trailing bytes after S
        val r = byteArrayOf(0x01)
        val s = byteArrayOf(0x01)
        // Manual construction: 0x30 [totalLen] 0x02 0x01 R 0x02 0x01 S 0xFF
        val totalLen = 3 + 3 + 1 // r_part + s_part + trailing
        val der = byteArrayOf(
            0x30, totalLen.toByte(),
            0x02, 0x01, r[0],
            0x02, 0x01, s[0],
            0xFF.toByte()
        )
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("trailing bytes after s component"))
    }

    @Test
    fun parseDerSignature_rExceeds32BytesAfterStripping() {
        // 33 non-zero bytes for R (exceeds 32 after stripping leading zeros)
        val r = ByteArray(33) { 0x01 }
        val s = byteArrayOf(0x01)
        val der = buildDer(r, s)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("r component exceeds 32 bytes"))
    }

    @Test
    fun parseDerSignature_sExceeds32BytesAfterStripping() {
        val r = byteArrayOf(0x01)
        val s = ByteArray(33) { 0x01 }
        val der = buildDer(r, s)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("s component exceeds 32 bytes"))
    }

    @Test
    fun parseDerSignature_rIsZero() {
        // R = 0x00 with no other bytes; after stripping, size == 1 and value == 0x00
        val r = byteArrayOf(0x00)
        val s = byteArrayOf(0x01)
        val der = buildDer(r, s)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("r component is zero"))
    }

    @Test
    fun parseDerSignature_sIsZero() {
        val r = byteArrayOf(0x01)
        val s = byteArrayOf(0x00)
        val der = buildDer(r, s)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("s component is zero"))
    }

    @Test
    fun parseDerSignature_rExceedsCurveOrder() {
        // secp256r1 curve order n = FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
        // Use n itself (which is >= n, so invalid)
        val rBytes = hexToBytes(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551"
        )
        val s = byteArrayOf(0x01)
        val der = buildDer(rBytes, s)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("r component exceeds curve order"))
    }

    @Test
    fun parseDerSignature_sExceedsCurveOrder() {
        val r = byteArrayOf(0x01)
        val sBytes = hexToBytes(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551"
        )
        val der = buildDer(r, sBytes)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.parseDerSignature(der)
        }
        assertTrue(ex.message!!.contains("s component exceeds curve order"))
    }

    @Test
    fun parseDerSignature_rJustBelowCurveOrder() {
        // n - 1 is valid
        val rBytes = hexToBytes(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550"
        )
        val s = byteArrayOf(0x01)
        // R has high bit set, so DER needs 0x00 prefix
        val rDer = byteArrayOf(0x00) + rBytes
        val der = buildDer(rDer, s)
        val (rBig, _) = SmartAccountUtils.parseDerSignature(der)
        assertNotNull(rBig)
    }

    // ========================================================================
    // normalizeSignature
    // ========================================================================

    @Test
    fun normalizeSignature_outputIs64Bytes() {
        val r = byteArrayOf(0x01)
        val s = byteArrayOf(0x01)
        val der = buildDer(r, s)
        val compact = SmartAccountUtils.normalizeSignature(der)
        assertEquals(64, compact.size)
    }

    @Test
    fun normalizeSignature_lowSUnchanged() {
        // S = 1, which is well below halfOrder; should remain 1
        val rMinimal = byteArrayOf(0x0A)
        val sMinimal = byteArrayOf(0x01)
        val der = buildDer(rMinimal, sMinimal)
        val compact = SmartAccountUtils.normalizeSignature(der)

        // Last byte of R portion (bytes 0..31) should be 0x0A
        assertEquals(0x0A.toByte(), compact[31])
        // Last byte of S portion (bytes 32..63) should be 0x01
        assertEquals(0x01.toByte(), compact[63])
    }

    @Test
    fun normalizeSignature_highSIsNormalized() {
        // Use S = curveOrder - 1 (which is > halfOrder), should become n - S = 1
        val sBytes = hexToBytes(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550"
        )
        // DER requires 0x00 prefix for high-bit values
        val sDer = byteArrayOf(0x00) + sBytes
        val rMinimal = byteArrayOf(0x01)
        val der = buildDer(rMinimal, sDer)
        val compact = SmartAccountUtils.normalizeSignature(der)

        assertEquals(64, compact.size)
        // Normalized S should be n - (n-1) = 1
        // So bytes 32..62 should be zero and byte 63 should be 0x01
        for (i in 32..62) {
            assertEquals(0x00.toByte(), compact[i], "Byte $i should be zero")
        }
        assertEquals(0x01.toByte(), compact[63])
    }

    @Test
    fun normalizeSignature_sEqualToHalfOrder() {
        // S = halfOrder (n/2 rounded down); this is NOT > halfOrder, so no normalization.
        // n  = FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
        // n/2 = 7FFFFFFF800000007FFFFFFFFFFFFFFFDE737D56D38BCF4279DCE5617E3192A8
        val halfOrder = hexToBytes(
            "7fffffff800000007fffffffffffffffde737d56d38bcf4279dce5617e3192a8"
        )
        // High bit (0x7f) is not set, so no 0x00 DER prefix needed
        val rMinimal = byteArrayOf(0x01)
        val der = buildDer(rMinimal, halfOrder)
        val compact = SmartAccountUtils.normalizeSignature(der)
        assertEquals(64, compact.size)
        // S portion should equal halfOrder
        assertContentEquals(halfOrder, compact.copyOfRange(32, 64))
    }

    @Test
    fun normalizeSignature_rIsPaddedTo32Bytes() {
        val rMinimal = byteArrayOf(0x01)
        val sMinimal = byteArrayOf(0x01)
        val der = buildDer(rMinimal, sMinimal)
        val compact = SmartAccountUtils.normalizeSignature(der)
        // First 31 bytes should be zero, byte 31 should be 0x01
        for (i in 0..30) {
            assertEquals(0x00.toByte(), compact[i], "R padding byte $i should be zero")
        }
        assertEquals(0x01.toByte(), compact[31])
    }

    @Test
    fun normalizeSignature_invalidDerThrows() {
        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.normalizeSignature(byteArrayOf(0x00, 0x01, 0x02))
        }
    }

    // ========================================================================
    // extractPublicKeyFromRegistration - Strategy 1 (direct public key)
    // ========================================================================

    @Test
    fun extractPublicKey_directUncompressedKey() {
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = validUncompressedKey
        )
        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertContentEquals(validUncompressedKey, result)
    }

    @Test
    fun extractPublicKey_directKeyWithSpkiWrapper() {
        // Simulate SPKI wrapping: prepend extra bytes before the 65-byte key
        val spkiPrefix = ByteArray(26) { 0x30 } // arbitrary SPKI header bytes
        val wrapped = spkiPrefix + validUncompressedKey
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = wrapped
        )
        assertEquals(65, result.size)
        assertContentEquals(validUncompressedKey, result)
    }

    @Test
    fun extractPublicKey_compressedKeyThrows() {
        // 0x02 prefix = compressed even-Y
        val compressed = byteArrayOf(0x02) + generatorX
        assertEquals(33, compressed.size)

        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(publicKey = compressed)
        }
        assertTrue(ex.message!!.contains("Compressed secp256r1 key format"))
        assertTrue(ex.message!!.contains("0x02"))
    }

    @Test
    fun extractPublicKey_compressedKeyOddYThrows() {
        // 0x03 prefix = compressed odd-Y
        val compressed = byteArrayOf(0x03) + generatorX
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(publicKey = compressed)
        }
        assertTrue(ex.message!!.contains("0x03"))
    }

    @Test
    fun extractPublicKey_directKeyOffCurveThrows() {
        // Valid format (0x04 prefix, 65 bytes) but invalid coordinates
        val offCurveKey = ByteArray(65)
        offCurveKey[0] = 0x04
        // X = 1, Y = 1 -- not on the curve
        offCurveKey[32] = 0x01
        offCurveKey[64] = 0x01
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(publicKey = offCurveKey)
        }
        assertTrue(ex.message!!.contains("not on the P-256 curve"))
    }

    @Test
    fun extractPublicKey_directKeyZeroCoordinatesThrows() {
        // X = 0, Y = 0 (point at infinity representation)
        val zeroKey = ByteArray(65)
        zeroKey[0] = 0x04
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(publicKey = zeroKey)
        }
        assertTrue(ex.message!!.contains("zero component"))
    }

    @Test
    fun extractPublicKey_directKeyCoordinatesExceedFieldPrime() {
        // X and Y both set to all 0xFF (larger than field prime p)
        val bigKey = ByteArray(65)
        bigKey[0] = 0x04
        for (i in 1..64) bigKey[i] = 0xFF.toByte()
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(publicKey = bigKey)
        }
        assertTrue(ex.message!!.contains("exceed the field prime"))
    }

    @Test
    fun extractPublicKey_emptyPublicKeyFallsThrough() {
        // Empty publicKey + no other data = error
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(
                publicKey = ByteArray(0)
            )
        }
        assertTrue(ex.message!!.contains("Could not extract public key"))
    }

    @Test
    fun extractPublicKey_noParametersThrows() {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration()
        }
        assertTrue(ex.message!!.contains("Could not extract public key"))
    }

    @Test
    fun extractPublicKey_nonKeyDataFallsThroughToError() {
        // Data that is not 65 bytes, not compressed -- falls through strategies
        val randomData = ByteArray(40) { 0x55 }
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(publicKey = randomData)
        }
        assertTrue(ex.message!!.contains("Could not extract public key"))
    }

    // ========================================================================
    // extractPublicKeyFromRegistration - Strategy 2 (authenticator data)
    // ========================================================================

    @Test
    fun extractPublicKey_fromAuthenticatorData() {
        val authData = buildAuthenticatorData(
            credentialIdLength = 32,
            x = generatorX,
            y = generatorY
        )
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            authenticatorData = authData
        )
        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertContentEquals(generatorX, result.copyOfRange(1, 33))
        assertContentEquals(generatorY, result.copyOfRange(33, 65))
    }

    @Test
    fun extractPublicKeyFromAuthData_tooShort() {
        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(ByteArray(54))
        assertNull(result)
    }

    @Test
    fun extractPublicKeyFromAuthData_noATFlag() {
        // AT flag (bit 6) not set
        val authData = ByteArray(132)
        authData[32] = 0x01 // flags byte, no AT flag
        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)
        assertNull(result)
    }

    @Test
    fun extractPublicKeyFromAuthData_wrongCosePrefix() {
        // AT flag set but COSE prefix doesn't match ES256
        val authData = ByteArray(132)
        authData[32] = 0x41 // flags with AT flag set (bit 6)
        // credentialIdLength = 0 at bytes 53-54 (already zero)
        // COSE key starts at offset 55; write wrong prefix
        authData[55] = 0xFF.toByte()
        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)
        assertNull(result)
    }

    @Test
    fun extractPublicKeyFromAuthData_invalidYMarkerThrows() {
        // Build valid authenticator data but corrupt the Y marker
        val authData = buildAuthenticatorData(
            credentialIdLength = 0,
            x = generatorX,
            y = generatorY
        )
        // Y marker is at offset 55 + 10 + 32 = 97
        authData[97] = 0xFF.toByte()
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)
        }
        assertTrue(ex.message!!.contains("COSE key structure is invalid"))
    }

    @Test
    fun extractPublicKeyFromAuthData_offCurveCoordinatesThrow() {
        // Valid structure but coordinates not on curve
        val fakeX = ByteArray(32) { 0x01 }
        val fakeY = ByteArray(32) { 0x02 }
        val authData = buildAuthenticatorData(
            credentialIdLength = 0,
            x = fakeX,
            y = fakeY
        )
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)
        }
        assertTrue(ex.message!!.contains("not on the P-256 curve"))
    }

    @Test
    fun extractPublicKeyFromAuthData_withLargeCredentialId() {
        // credentialIdLength = 256 (shifts COSE key further into the data)
        val authData = buildAuthenticatorData(
            credentialIdLength = 256,
            x = generatorX,
            y = generatorY
        )
        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)
        assertNotNull(result)
        assertEquals(65, result.size)
        assertContentEquals(generatorX, result.copyOfRange(1, 33))
    }

    @Test
    fun extractPublicKeyFromAuthData_dataTooShortForCoseKey() {
        // AT flag set, credentialIdLength = 0, but data truncated before full COSE key
        val authData = ByteArray(80)
        authData[32] = 0x41.toByte() // AT flag set
        // COSE prefix at offset 55
        val cosePrefix = byteArrayOf(
            0xA5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20,
            0x01, 0x21, 0x58, 0x20
        )
        cosePrefix.copyInto(authData, 55)
        val result = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)
        assertNull(result)
    }

    // ========================================================================
    // extractPublicKeyFromRegistration - Strategy 3 (attestation object)
    // ========================================================================

    @Test
    fun extractPublicKey_fromAttestationObject() {
        val attestation = buildAttestationObject(generatorX, generatorY)
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            attestationObject = attestation
        )
        assertEquals(65, result.size)
        assertContentEquals(generatorX, result.copyOfRange(1, 33))
        assertContentEquals(generatorY, result.copyOfRange(33, 65))
    }

    @Test
    fun extractPublicKeyFromAttestation_noPrefixThrows() {
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(ByteArray(100) { 0x00 })
        }
        assertTrue(ex.message!!.contains("COSE key prefix not found"))
    }

    @Test
    fun extractPublicKeyFromAttestation_truncatedAfterPrefixThrows() {
        val cosePrefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
            0x01, 0x21, 0x58, 0x20.toByte()
        )
        // Only 10 bytes of data after prefix (need 67: 32 X + 3 sep + 32 Y)
        val data = cosePrefix + ByteArray(10)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(data)
        }
        assertTrue(ex.message!!.contains("COSE key structure is malformed"))
    }

    @Test
    fun extractPublicKeyFromAttestation_invalidYMarkerThrows() {
        val attestation = buildAttestationObject(generatorX, generatorY)
        // COSE prefix is at offset 5 (arbitrary padding in buildAttestationObject)
        // Y marker is at 5 + 10 + 32 = 47
        attestation[47] = 0xFF.toByte()
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(attestation)
        }
        assertTrue(ex.message!!.contains("COSE key structure is malformed"))
    }

    @Test
    fun extractPublicKeyFromAttestation_offCurveThrows() {
        val fakeX = ByteArray(32) { 0x01 }
        val fakeY = ByteArray(32) { 0x02 }
        val attestation = buildAttestationObject(fakeX, fakeY)
        val ex = assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(attestation)
        }
        assertTrue(ex.message!!.contains("not on the P-256 curve"))
    }

    // ========================================================================
    // extractPublicKeyFromRegistration - Strategy fallthrough
    // ========================================================================

    @Test
    fun extractPublicKey_fallsThroughToAuthenticatorData() {
        // publicKey is non-key garbage, authenticatorData is valid
        val garbage = ByteArray(20) { 0x55 }
        val authData = buildAuthenticatorData(
            credentialIdLength = 0,
            x = generatorX,
            y = generatorY
        )
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = garbage,
            authenticatorData = authData
        )
        assertContentEquals(validUncompressedKey, result)
    }

    @Test
    fun extractPublicKey_fallsThroughToAttestationObject() {
        // publicKey is garbage, authenticatorData returns null, attestationObject works
        val garbage = ByteArray(20) { 0x55 }
        val shortAuthData = ByteArray(30) // too short
        val attestation = buildAttestationObject(generatorX, generatorY)
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = garbage,
            authenticatorData = shortAuthData,
            attestationObject = attestation
        )
        assertContentEquals(validUncompressedKey, result)
    }

    @Test
    fun extractPublicKey_nullPublicKeyUsesAuthData() {
        val authData = buildAuthenticatorData(
            credentialIdLength = 0,
            x = generatorX,
            y = generatorY
        )
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = null,
            authenticatorData = authData
        )
        assertContentEquals(validUncompressedKey, result)
    }

    // ========================================================================
    // getContractSalt
    // ========================================================================

    @Test
    fun getContractSalt_returnsSha256() = runTest {
        val credentialId = "test-credential-id".encodeToByteArray()
        val salt = SmartAccountUtils.getContractSalt(credentialId)
        assertEquals(32, salt.size)
    }

    @Test
    fun getContractSalt_deterministicForSameInput() = runTest {
        val credentialId = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val salt1 = SmartAccountUtils.getContractSalt(credentialId)
        val salt2 = SmartAccountUtils.getContractSalt(credentialId)
        assertContentEquals(salt1, salt2)
    }

    @Test
    fun getContractSalt_differentInputsDifferentSalts() = runTest {
        val salt1 = SmartAccountUtils.getContractSalt(byteArrayOf(0x01))
        val salt2 = SmartAccountUtils.getContractSalt(byteArrayOf(0x02))
        kotlin.test.assertFalse(salt1.contentEquals(salt2))
    }

    @Test
    fun getContractSalt_emptyInput() = runTest {
        val salt = SmartAccountUtils.getContractSalt(ByteArray(0))
        assertEquals(32, salt.size)
        // SHA-256 of empty input is the well-known hash:
        // e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val expectedHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val expected = hexToBytes(expectedHex)
        assertContentEquals(expected, salt)
    }

    // ========================================================================
    // deriveContractAddress
    // ========================================================================

    @Test
    fun deriveContractAddress_returnsValidCAddress() = runTest {
        val keyPair = KeyPair.random()
        val credentialId = "test-cred".encodeToByteArray()
        val address = SmartAccountUtils.deriveContractAddress(
            credentialId,
            keyPair.getAccountId(),
            Network.TESTNET.networkPassphrase
        )
        assertTrue(address.startsWith("C"), "Contract address should start with C")
        assertEquals(56, address.length, "Contract address should be 56 characters")
        // Should be decodable
        val decoded = StrKey.decodeContract(address)
        assertEquals(32, decoded.size)
    }

    @Test
    fun deriveContractAddress_deterministic() = runTest {
        val keyPair = KeyPair.random()
        val credentialId = byteArrayOf(0x01, 0x02, 0x03)
        val addr1 = SmartAccountUtils.deriveContractAddress(
            credentialId,
            keyPair.getAccountId(),
            Network.TESTNET.networkPassphrase
        )
        val addr2 = SmartAccountUtils.deriveContractAddress(
            credentialId,
            keyPair.getAccountId(),
            Network.TESTNET.networkPassphrase
        )
        assertEquals(addr1, addr2)
    }

    @Test
    fun deriveContractAddress_differentCredentialIdsDifferentAddresses() = runTest {
        val keyPair = KeyPair.random()
        val addr1 = SmartAccountUtils.deriveContractAddress(
            byteArrayOf(0x01),
            keyPair.getAccountId(),
            Network.TESTNET.networkPassphrase
        )
        val addr2 = SmartAccountUtils.deriveContractAddress(
            byteArrayOf(0x02),
            keyPair.getAccountId(),
            Network.TESTNET.networkPassphrase
        )
        assertTrue(addr1 != addr2)
    }

    @Test
    fun deriveContractAddress_differentNetworksDifferentAddresses() = runTest {
        val keyPair = KeyPair.random()
        val credentialId = byteArrayOf(0x01)
        val testnetAddr = SmartAccountUtils.deriveContractAddress(
            credentialId,
            keyPair.getAccountId(),
            Network.TESTNET.networkPassphrase
        )
        val pubnetAddr = SmartAccountUtils.deriveContractAddress(
            credentialId,
            keyPair.getAccountId(),
            Network.PUBLIC.networkPassphrase
        )
        assertTrue(testnetAddr != pubnetAddr)
    }

    @Test
    fun deriveContractAddress_invalidDeployerKeyThrows() = runTest {
        val ex = assertFailsWith<ValidationException.InvalidAddress> {
            SmartAccountUtils.deriveContractAddress(
                byteArrayOf(0x01),
                "INVALID_KEY",
                Network.TESTNET.networkPassphrase
            )
        }
        assertTrue(ex.message!!.contains("Invalid address"))
    }

    // ========================================================================
    // findSubarray
    // ========================================================================

    @Test
    fun findSubarray_findsAtBeginning() {
        val array = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val sub = byteArrayOf(0x01, 0x02)
        assertEquals(0, SmartAccountUtils.findSubarray(array, sub))
    }

    @Test
    fun findSubarray_findsInMiddle() {
        val array = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val sub = byteArrayOf(0x01, 0x02)
        assertEquals(1, SmartAccountUtils.findSubarray(array, sub))
    }

    @Test
    fun findSubarray_findsAtEnd() {
        val array = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val sub = byteArrayOf(0x02, 0x03)
        assertEquals(2, SmartAccountUtils.findSubarray(array, sub))
    }

    @Test
    fun findSubarray_notFound() {
        val array = byteArrayOf(0x01, 0x02, 0x03)
        val sub = byteArrayOf(0x04, 0x05)
        assertEquals(-1, SmartAccountUtils.findSubarray(array, sub))
    }

    @Test
    fun findSubarray_emptySubarray() {
        val array = byteArrayOf(0x01, 0x02)
        assertEquals(-1, SmartAccountUtils.findSubarray(array, ByteArray(0)))
    }

    @Test
    fun findSubarray_subarrayLargerThanArray() {
        val array = byteArrayOf(0x01)
        val sub = byteArrayOf(0x01, 0x02)
        assertEquals(-1, SmartAccountUtils.findSubarray(array, sub))
    }

    @Test
    fun findSubarray_exactMatch() {
        val array = byteArrayOf(0x01, 0x02, 0x03)
        assertEquals(0, SmartAccountUtils.findSubarray(array, array.copyOf()))
    }

    @Test
    fun findSubarray_singleByteMatch() {
        val array = byteArrayOf(0x00, 0x01, 0x02)
        val sub = byteArrayOf(0x01)
        assertEquals(1, SmartAccountUtils.findSubarray(array, sub))
    }

    @Test
    fun findSubarray_firstOccurrence() {
        // Pattern appears twice; should return first index
        val array = byteArrayOf(0x01, 0x02, 0x01, 0x02)
        val sub = byteArrayOf(0x01, 0x02)
        assertEquals(0, SmartAccountUtils.findSubarray(array, sub))
    }

    @Test
    fun findSubarray_bothEmpty() {
        assertEquals(-1, SmartAccountUtils.findSubarray(ByteArray(0), ByteArray(0)))
    }

    // ========================================================================
    // Helper functions
    // ========================================================================

    /**
     * Builds a valid DER-encoded ECDSA signature from raw R and S byte arrays.
     * R and S may already include DER padding (leading 0x00 for positive interpretation).
     */
    private fun buildDer(r: ByteArray, s: ByteArray): ByteArray {
        val rPart = byteArrayOf(0x02, r.size.toByte()) + r
        val sPart = byteArrayOf(0x02, s.size.toByte()) + s
        val totalLen = rPart.size + sPart.size
        return byteArrayOf(0x30, totalLen.toByte()) + rPart + sPart
    }

    /**
     * Builds a synthetic WebAuthn authenticator data blob with an ES256 COSE key.
     *
     * Layout:
     * [0..31]  rpIdHash (32 bytes, zeros)
     * [32]     flags (0x41 = UP + AT)
     * [33..36] signCount (4 bytes, zeros)
     * [37..52] aaguid (16 bytes, zeros)
     * [53..54] credentialIdLength (big-endian uint16)
     * [55..55+N-1] credentialId (N bytes, zeros)
     * [55+N..] COSE ES256 key: prefix(10) + X(32) + separator(3) + Y(32) = 77 bytes
     */
    private fun buildAuthenticatorData(
        credentialIdLength: Int,
        x: ByteArray,
        y: ByteArray
    ): ByteArray {
        val cosePrefix = byteArrayOf(
            0xA5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20,
            0x01, 0x21, 0x58, 0x20
        )
        val ySeparator = byteArrayOf(0x22, 0x58, 0x20)

        val totalSize = 55 + credentialIdLength + 10 + 32 + 3 + 32
        val data = ByteArray(totalSize)

        // flags: UP (bit 0) + AT (bit 6) = 0x41
        data[32] = 0x41.toByte()

        // credentialIdLength at bytes 53-54 (big-endian)
        data[53] = ((credentialIdLength shr 8) and 0xFF).toByte()
        data[54] = (credentialIdLength and 0xFF).toByte()

        val coseStart = 55 + credentialIdLength
        cosePrefix.copyInto(data, coseStart)
        x.copyInto(data, coseStart + 10)
        ySeparator.copyInto(data, coseStart + 10 + 32)
        y.copyInto(data, coseStart + 10 + 32 + 3)

        return data
    }

    /**
     * Builds a synthetic attestation object containing a COSE key.
     * Prefixed with 5 bytes of arbitrary padding to simulate real attestation structure.
     */
    private fun buildAttestationObject(x: ByteArray, y: ByteArray): ByteArray {
        val padding = ByteArray(5) { 0xAA.toByte() }
        val cosePrefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
            0x01, 0x21, 0x58, 0x20.toByte()
        )
        val ySeparator = byteArrayOf(0x22, 0x58, 0x20)
        return padding + cosePrefix + x + ySeparator + y
    }

    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "")
        return ByteArray(cleanHex.length / 2) { i ->
            val index = i * 2
            cleanHex.substring(index, index + 2).toInt(16).toByte()
        }
    }
}
