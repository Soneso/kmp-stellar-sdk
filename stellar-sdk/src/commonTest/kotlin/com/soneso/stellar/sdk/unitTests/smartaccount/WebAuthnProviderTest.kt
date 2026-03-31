//
//  WebAuthnProviderTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2025 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountUtils
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnSignature
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnRegistrationResult
import com.soneso.stellar.sdk.xdr.SCMapXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for WebAuthn provider utilities and data structures.
 *
 * Since WebAuthn requires actual device interaction (biometrics, security keys),
 * this test file focuses on the shared utility functions and data structures used
 * by all platform-specific WebAuthn providers:
 *
 * 1. Public key extraction from attestation objects (COSE key pattern matching)
 * 2. Public key extraction from authenticator data (structured CBOR parsing)
 * 3. Multi-strategy public key extraction from registration results
 * 4. DER-to-compact signature normalization with low-S enforcement
 * 5. Authenticator data flag parsing (deviceType, backedUp)
 * 6. Data class validation and equality
 * 7. Error handling for malformed inputs
 *
 * Test fixtures use realistic WebAuthn data structures with known secp256r1
 * public keys, constructed according to the WebAuthn specification (W3C).
 */
class WebAuthnProviderTest {

    // =========================================================================
    // Test Constants - Known secp256r1 Public Key Coordinates
    // =========================================================================

    /**
     * Known X coordinate for test public key (32 bytes).
     */
    private val testXCoordinate = ByteArray(32) { (it + 0x10).toByte() }

    /**
     * Known Y coordinate for test public key (32 bytes).
     */
    private val testYCoordinate = ByteArray(32) { (it + 0x30).toByte() }

    /**
     * Expected uncompressed public key: 0x04 || X || Y (65 bytes).
     */
    private val expectedPublicKey: ByteArray
        get() {
            val key = ByteArray(65)
            key[0] = 0x04
            testXCoordinate.copyInto(key, 1)
            testYCoordinate.copyInto(key, 33)
            return key
        }

    /**
     * COSE key prefix for ES256 (secp256r1) used in attestation objects.
     *
     * Structure:
     * a5 = CBOR map with 5 entries
     * 01 02 = kty: 2 (EC2)
     * 03 26 = alg: -7 (ES256) -- CBOR negative int: 0x26 = 1*32+6, value = -1-6 = -7
     * 20 01 = crv: 1 (P-256) -- CBOR negative int: 0x20 = 1*32+0, value = -1-0 = -1; then 01
     * 21 58 20 = -2 (X coord): byte string of 32 bytes
     */
    private val coseKeyPrefix = byteArrayOf(
        0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
        0x01, 0x21, 0x58, 0x20.toByte()
    )

    /**
     * COSE key separator between X and Y coordinates: label -3, byte string of 32 bytes.
     */
    private val coseKeySeparator = byteArrayOf(0x22, 0x58, 0x20.toByte())

    // =========================================================================
    // Helper: Build Test Data Structures
    // =========================================================================

    /**
     * Builds a minimal COSE key structure containing the test X/Y coordinates.
     *
     * Structure:
     * [prefix (10 bytes)] [X (32 bytes)] [separator (3 bytes)] [Y (32 bytes)]
     * Total: 77 bytes
     */
    private fun buildCoseKey(): ByteArray {
        return coseKeyPrefix + testXCoordinate + coseKeySeparator + testYCoordinate
    }

    /**
     * Builds a minimal attestation object containing a COSE key.
     *
     * The attestation object is a CBOR map with the COSE key embedded.
     * This simulates the pattern that extractPublicKeyFromAttestationObject()
     * searches for: the COSE key prefix followed by X/Y coordinates.
     *
     * @param preamble Optional bytes before the COSE key (simulates CBOR map overhead)
     */
    private fun buildAttestationObject(preamble: ByteArray = ByteArray(0)): ByteArray {
        return preamble + buildCoseKey()
    }

    /**
     * Builds a minimal authenticator data structure with attested credential data.
     *
     * Layout (per WebAuthn spec):
     * [0..31]   rpIdHash         (32 bytes, filled with 0xAA)
     * [32]      flags            (1 byte)
     * [33..36]  signCount        (4 bytes, big-endian, set to 0)
     * [37..52]  aaguid           (16 bytes, filled with 0x00)
     * [53..54]  credentialIdLen  (2 bytes, big-endian)
     * [55..55+N-1] credentialId (N bytes)
     * [55+N..]  COSE public key  (variable)
     *
     * @param flags The flags byte. Bit 6 (0x40) = AT flag (attested credential data present).
     * @param credentialIdLength Length of the credential ID
     */
    private fun buildAuthenticatorData(
        flags: Int = 0x41, // UP + AT flags set
        credentialIdLength: Int = 16
    ): ByteArray {
        val rpIdHash = ByteArray(32) { 0xAA.toByte() }
        val flagsByte = byteArrayOf(flags.toByte())
        val signCount = ByteArray(4) // 0x00000000
        val aaguid = ByteArray(16)
        val credIdLen = byteArrayOf(
            ((credentialIdLength shr 8) and 0xFF).toByte(),
            (credentialIdLength and 0xFF).toByte()
        )
        val credentialId = ByteArray(credentialIdLength) { 0xBB.toByte() }
        val coseKey = buildCoseKey()

        return rpIdHash + flagsByte + signCount + aaguid + credIdLen + credentialId + coseKey
    }

    /**
     * Builds a CBOR-encoded attestation object with an "authData" field containing
     * the given authenticator data.
     *
     * Simplified CBOR structure:
     * a3                               -- map(3)
     *   63 666d74                      -- text(3) "fmt"
     *   64 6e6f6e65                    -- text(4) "none"
     *   67 61747453746d74              -- text(7) "attStmt"
     *   a0                            -- map(0) (empty)
     *   68 61757468 44617461           -- text(8) "authData"
     *   59 [2-byte length] [authData]  -- bytes(N)
     */
    private fun buildCborAttestationObject(authenticatorData: ByteArray): ByteArray {
        // CBOR map(3)
        val mapHeader = byteArrayOf(0xa3.toByte())

        // Key: "fmt" (text string of length 3)
        val fmtKey = byteArrayOf(0x63, 0x66, 0x6d, 0x74)
        // Value: "none" (text string of length 4)
        val fmtValue = byteArrayOf(0x64, 0x6e, 0x6f, 0x6e, 0x65)

        // Key: "attStmt" (text string of length 7)
        val attStmtKey = byteArrayOf(0x67, 0x61, 0x74, 0x74, 0x53, 0x74, 0x6d, 0x74)
        // Value: {} (empty map)
        val attStmtValue = byteArrayOf(0xa0.toByte())

        // Key: "authData" (text string of length 8)
        val authDataKey = byteArrayOf(
            0x68, 0x61, 0x75, 0x74, 0x68, 0x44, 0x61, 0x74, 0x61
        )

        // Value: byte string with the authenticator data
        val authDataLenHeader: ByteArray = if (authenticatorData.size <= 23) {
            // Inline length: 0x40 + length
            byteArrayOf((0x40 + authenticatorData.size).toByte())
        } else if (authenticatorData.size <= 255) {
            // 1-byte length: 0x58 [length]
            byteArrayOf(0x58, authenticatorData.size.toByte())
        } else {
            // 2-byte length: 0x59 [hi] [lo]
            byteArrayOf(
                0x59,
                ((authenticatorData.size shr 8) and 0xFF).toByte(),
                (authenticatorData.size and 0xFF).toByte()
            )
        }

        return mapHeader +
            fmtKey + fmtValue +
            attStmtKey + attStmtValue +
            authDataKey + authDataLenHeader + authenticatorData
    }

    // =========================================================================
    // extractPublicKeyFromAttestationObject Tests (Pattern Matching Strategy)
    // =========================================================================

    @Test
    fun testExtractPublicKey_validAttestationObject_extractsCorrectKey() {
        val attestationObject = buildAttestationObject()
        val extracted = SmartAccountUtils.extractPublicKeyFromAttestationObject(attestationObject)

        assertEquals(65, extracted.size, "Extracted public key must be 65 bytes")
        assertEquals(
            0x04.toByte(), extracted[0],
            "Public key must start with 0x04 (uncompressed format)"
        )
        assertContentEquals(
            expectedPublicKey, extracted,
            "Extracted public key must match expected X/Y coordinates"
        )
    }

    @Test
    fun testExtractPublicKey_withPreambleBeforeCoseKey_extractsCorrectKey() {
        // Simulate real attestation objects that have CBOR map overhead before the COSE key
        val preamble = ByteArray(50) { 0x00 }
        val attestationObject = buildAttestationObject(preamble)
        val extracted = SmartAccountUtils.extractPublicKeyFromAttestationObject(attestationObject)

        assertContentEquals(expectedPublicKey, extracted)
    }

    @Test
    fun testExtractPublicKey_convenienceAlias_matchesDirectCall() {
        val attestationObject = buildAttestationObject()

        val direct = SmartAccountUtils.extractPublicKeyFromAttestationObject(attestationObject)
        val alias = SmartAccountUtils.extractPublicKey(attestationObject)

        assertContentEquals(
            direct, alias,
            "extractPublicKey() alias must produce the same result as extractPublicKeyFromAttestationObject()"
        )
    }

    @Test
    fun testExtractPublicKey_noCosePrefix_throwsValidationException() {
        // An attestation object without the COSE key prefix should fail
        val garbage = ByteArray(200) { 0xFF.toByte() }

        assertFailsWith<ValidationException.InvalidInput>(
            "Must throw when COSE key prefix is not found"
        ) {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(garbage)
        }
    }

    @Test
    fun testExtractPublicKey_truncatedAfterPrefix_throwsValidationException() {
        // COSE key prefix present but not enough bytes for X + separator + Y
        val truncated = coseKeyPrefix + testXCoordinate + coseKeySeparator
        // Missing Y coordinate entirely

        assertFailsWith<ValidationException.InvalidInput>(
            "Must throw when there is insufficient data after COSE key prefix"
        ) {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(truncated)
        }
    }

    @Test
    fun testExtractPublicKey_emptyAttestationObject_throwsValidationException() {
        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(ByteArray(0))
        }
    }

    @Test
    fun testExtractPublicKey_tooShortForCoseKey_throwsValidationException() {
        // Only the prefix, no coordinates
        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(coseKeyPrefix)
        }
    }

    @Test
    fun testExtractPublicKey_partialCosePrefix_throwsValidationException() {
        // Only first 5 bytes of the 10-byte COSE prefix
        val partial = coseKeyPrefix.copyOfRange(0, 5)
        val padded = partial + ByteArray(200)

        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(padded)
        }
    }

    // =========================================================================
    // extractPublicKeyFromAuthenticatorData Tests
    // =========================================================================

    @Test
    fun testExtractFromAuthData_validData_extractsCorrectKey() {
        val authData = buildAuthenticatorData()
        val extracted = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)

        assertNotNull(extracted, "Should extract public key from valid authenticator data")
        assertEquals(65, extracted.size)
        assertEquals(0x04.toByte(), extracted[0])
        assertContentEquals(expectedPublicKey, extracted)
    }

    @Test
    fun testExtractFromAuthData_differentCredentialIdLengths() {
        // WebAuthn credential IDs can be 16 to 1023 bytes
        for (credIdLen in listOf(0, 1, 16, 32, 64, 128, 255)) {
            val authData = buildAuthenticatorData(credentialIdLength = credIdLen)
            val extracted = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)

            assertNotNull(extracted, "Should extract key with credentialId length $credIdLen")
            assertContentEquals(
                expectedPublicKey, extracted,
                "Public key must be correct for credentialId length $credIdLen"
            )
        }
    }

    @Test
    fun testExtractFromAuthData_noATFlag_returnsNull() {
        // Flags byte without AT flag (bit 6) set
        val authData = buildAuthenticatorData(flags = 0x01) // UP only, no AT
        val extracted = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)

        assertNull(extracted, "Should return null when AT flag is not set")
    }

    @Test
    fun testExtractFromAuthData_tooShort_returnsNull() {
        // Less than 55 bytes (minimum for attested credential data header)
        val shortData = ByteArray(37) // rpIdHash(32) + flags(1) + signCount(4) only
        val extracted = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(shortData)

        assertNull(extracted, "Should return null for authenticator data shorter than 55 bytes")
    }

    @Test
    fun testExtractFromAuthData_emptyInput_returnsNull() {
        val extracted = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(ByteArray(0))
        assertNull(extracted)
    }

    @Test
    fun testExtractFromAuthData_truncatedCoseKey_returnsNull() {
        // Valid header but truncated after credential ID (no COSE key data)
        val rpIdHash = ByteArray(32) { 0xAA.toByte() }
        val flagsByte = byteArrayOf(0x41.toByte()) // UP + AT
        val signCount = ByteArray(4)
        val aaguid = ByteArray(16)
        val credIdLen = byteArrayOf(0x00, 0x10) // 16 bytes
        val credentialId = ByteArray(16) { 0xBB.toByte() }
        // No COSE key after credential ID

        val authData = rpIdHash + flagsByte + signCount + aaguid + credIdLen + credentialId
        val extracted = SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)

        assertNull(
            extracted,
            "Should return null when authenticator data is truncated before COSE key"
        )
    }

    // =========================================================================
    // Authenticator Data Flag Parsing Tests
    // =========================================================================

    @Test
    fun testAuthenticatorDataFlags_userPresent() {
        // Bit 0 (0x01): UP (User Present)
        val flags = 0x01
        assertEquals(1, flags and 0x01, "UP bit should be set")
        assertEquals(0, flags and 0x04, "UV bit should not be set")
        assertEquals(0, flags and 0x08, "BE bit should not be set")
        assertEquals(0, flags and 0x10, "BS bit should not be set")
        assertEquals(0, flags and 0x40, "AT bit should not be set")
    }

    @Test
    fun testAuthenticatorDataFlags_userVerified() {
        // Bit 2 (0x04): UV (User Verified)
        val flags = 0x05 // UP + UV
        assertEquals(1, flags and 0x01, "UP bit should be set")
        assertEquals(0x04, flags and 0x04, "UV bit should be set")
    }

    @Test
    fun testAuthenticatorDataFlags_backupEligible_singleDevice() {
        // BE=0 -> "singleDevice"
        val flags = 0x01 // UP only, no BE, no BS
        val backupEligible = (flags and 0x08) != 0
        val backedUp = (flags and 0x10) != 0
        val deviceType = if (backupEligible) "multiDevice" else "singleDevice"

        assertFalse(backupEligible, "BE should not be set for singleDevice")
        assertFalse(backedUp, "BS should not be set when BE is not set")
        assertEquals("singleDevice", deviceType)
    }

    @Test
    fun testAuthenticatorDataFlags_backupEligible_multiDevice() {
        // BE=1 -> "multiDevice"
        val flags = 0x09 // UP + BE
        val backupEligible = (flags and 0x08) != 0
        val backedUp = (flags and 0x10) != 0
        val deviceType = if (backupEligible) "multiDevice" else "singleDevice"

        assertTrue(backupEligible, "BE should be set for multiDevice")
        assertFalse(backedUp, "BS should not be set (eligible but not yet backed up)")
        assertEquals("multiDevice", deviceType)
    }

    @Test
    fun testAuthenticatorDataFlags_backedUp() {
        // BE=1, BS=1 -> multiDevice, backed up
        val flags = 0x19 // UP + BE + BS
        val backupEligible = (flags and 0x08) != 0
        val backedUp = (flags and 0x10) != 0
        val deviceType = if (backupEligible) "multiDevice" else "singleDevice"

        assertTrue(backupEligible, "BE should be set")
        assertTrue(backedUp, "BS should be set (credential is backed up)")
        assertEquals("multiDevice", deviceType)
    }

    @Test
    fun testAuthenticatorDataFlags_attestedCredentialData() {
        // Bit 6 (0x40): AT (Attested Credential Data present)
        val flags = 0x41 // UP + AT
        val atPresent = (flags and 0x40) != 0
        assertTrue(atPresent, "AT flag should be set")
    }

    @Test
    fun testAuthenticatorDataFlags_allFlagsSet() {
        // UP=1, UV=1, BE=1, BS=1, AT=1, ED=1 (extension data)
        val flags = 0xDD // 11011101 = UP+UV+BE+BS+AT+ED
        assertEquals(0x01, flags and 0x01, "UP bit")
        assertEquals(0x04, flags and 0x04, "UV bit")
        assertEquals(0x08, flags and 0x08, "BE bit")
        assertEquals(0x10, flags and 0x10, "BS bit")
        assertEquals(0x40, flags and 0x40, "AT bit")
        assertEquals(0x80.toByte().toInt() and 0xFF, flags and 0x80, "ED bit")
    }

    @Test
    fun testDeviceType_fromRealAuthenticatorData_singleDevice() {
        // Build authenticator data with flags = 0x41 (UP + AT, no BE)
        val authData = buildAuthenticatorData(flags = 0x41)
        val flags = authData[32].toInt() and 0xFF
        val backupEligible = (flags and 0x08) != 0
        val deviceType = if (backupEligible) "multiDevice" else "singleDevice"

        assertEquals("singleDevice", deviceType)
    }

    @Test
    fun testDeviceType_fromRealAuthenticatorData_multiDevice() {
        // Build authenticator data with flags = 0x49 (UP + BE + AT)
        val authData = buildAuthenticatorData(flags = 0x49)
        val flags = authData[32].toInt() and 0xFF
        val backupEligible = (flags and 0x08) != 0
        val deviceType = if (backupEligible) "multiDevice" else "singleDevice"

        assertEquals("multiDevice", deviceType)
    }

    @Test
    fun testBackedUp_fromRealAuthenticatorData_notBackedUp() {
        // Flags = 0x49 (UP + BE + AT, no BS)
        val authData = buildAuthenticatorData(flags = 0x49)
        val flags = authData[32].toInt() and 0xFF
        val backedUp = (flags and 0x10) != 0

        assertFalse(backedUp, "Should not be backed up when BS flag is clear")
    }

    @Test
    fun testBackedUp_fromRealAuthenticatorData_backedUp() {
        // Flags = 0x59 (UP + BE + BS + AT)
        val authData = buildAuthenticatorData(flags = 0x59)
        val flags = authData[32].toInt() and 0xFF
        val backedUp = (flags and 0x10) != 0

        assertTrue(backedUp, "Should be backed up when BS flag is set")
    }

    // =========================================================================
    // extractPublicKeyFromRegistration Tests (Multi-Strategy)
    // =========================================================================

    @Test
    fun testExtractFromRegistration_strategy1_directPublicKey() {
        // Strategy 1: direct 65-byte uncompressed public key
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = expectedPublicKey
        )

        assertContentEquals(expectedPublicKey, result)
    }

    @Test
    fun testExtractFromRegistration_strategy1_publicKeyWrappedInSpki() {
        // SPKI-wrapped key: the last 65 bytes are the uncompressed point
        val spkiHeader = ByteArray(26) { 0x30.toByte() } // Simulated DER/SPKI header
        val spkiKey = spkiHeader + expectedPublicKey

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = spkiKey
        )

        assertContentEquals(
            expectedPublicKey, result,
            "Should extract last 65 bytes from SPKI-wrapped key"
        )
    }

    @Test
    fun testExtractFromRegistration_strategy2_authenticatorData() {
        val authData = buildAuthenticatorData()
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            authenticatorData = authData
        )

        assertContentEquals(expectedPublicKey, result)
    }

    @Test
    fun testExtractFromRegistration_strategy3_attestationObject() {
        val attestationObj = buildAttestationObject()
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            attestationObject = attestationObj
        )

        assertContentEquals(expectedPublicKey, result)
    }

    @Test
    fun testExtractFromRegistration_fallbackOrder_strategy1To2() {
        // Invalid public key (wrong prefix) should fall through to strategy 2
        val invalidPublicKey = ByteArray(65) { 0x00 } // Wrong prefix (0x00 instead of 0x04)
        val authData = buildAuthenticatorData()

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = invalidPublicKey,
            authenticatorData = authData
        )

        assertContentEquals(
            expectedPublicKey, result,
            "Should fall through to authenticator data when public key is invalid"
        )
    }

    @Test
    fun testExtractFromRegistration_fallbackOrder_strategy2To3() {
        // Invalid authenticator data (no AT flag) should fall through to strategy 3
        val invalidAuthData = ByteArray(37) // Too short for attested credential data
        val attestationObj = buildAttestationObject()

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            authenticatorData = invalidAuthData,
            attestationObject = attestationObj
        )

        assertContentEquals(
            expectedPublicKey, result,
            "Should fall through to attestation object when authenticator data extraction fails"
        )
    }

    @Test
    fun testExtractFromRegistration_allStrategiesProvided_prefersDirectKey() {
        // When all three sources are provided, strategy 1 (direct key) should be preferred
        val authData = buildAuthenticatorData()
        val attestationObj = buildAttestationObject()

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            publicKey = expectedPublicKey,
            authenticatorData = authData,
            attestationObject = attestationObj
        )

        assertContentEquals(expectedPublicKey, result)
    }

    @Test
    fun testExtractFromRegistration_noSourcesProvided_throwsValidationException() {
        assertFailsWith<ValidationException.InvalidInput>(
            "Must throw when no extraction source is provided"
        ) {
            SmartAccountUtils.extractPublicKeyFromRegistration()
        }
    }

    @Test
    fun testExtractFromRegistration_allNullSources_throwsValidationException() {
        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(
                publicKey = null,
                authenticatorData = null,
                attestationObject = null
            )
        }
    }

    @Test
    fun testExtractFromRegistration_emptyPublicKey_throwsWithNoFallback() {
        assertFailsWith<ValidationException.InvalidInput>(
            "Must throw when public key is empty and no fallback is provided"
        ) {
            SmartAccountUtils.extractPublicKeyFromRegistration(
                publicKey = ByteArray(0)
            )
        }
    }

    @Test
    fun testExtractFromRegistration_wrongSizePublicKey_throwsWithNoFallback() {
        // 32-byte key (too short, wrong prefix) - no fallback sources
        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.extractPublicKeyFromRegistration(
                publicKey = ByteArray(32) { 0x04 }
            )
        }
    }

    // =========================================================================
    // normalizeSignature Tests (DER to Compact with Low-S)
    // =========================================================================

    @Test
    fun testNormalizeSignature_validDer_producesCompactSignature() {
        // Build a simple DER signature with known r and s values
        val r = ByteArray(32) { (it + 1).toByte() }
        val s = ByteArray(32) { (it + 0x21).toByte() }
        val derSig = buildDerSignature(r, s)

        val compact = SmartAccountUtils.normalizeSignature(derSig)

        assertEquals(64, compact.size, "Compact signature must be exactly 64 bytes")

        // First 32 bytes are r, last 32 bytes are s (possibly normalized)
        val extractedR = compact.copyOfRange(0, 32)
        assertContentEquals(r, extractedR, "r component must be preserved")
    }

    @Test
    fun testNormalizeSignature_lowS_preservedAsIs() {
        // s value that is less than halfCurveOrder should be preserved
        val r = ByteArray(32) { (it + 1).toByte() }
        // Small s value (well below half curve order)
        val s = ByteArray(32)
        s[31] = 0x42 // s = 0x42 (very small)

        val derSig = buildDerSignature(r, s)
        val compact = SmartAccountUtils.normalizeSignature(derSig)

        val extractedS = compact.copyOfRange(32, 64)
        assertContentEquals(s, extractedS, "Low-S value should be preserved unchanged")
    }

    @Test
    fun testNormalizeSignature_highS_normalized() {
        val r = ByteArray(32) { (it + 1).toByte() }
        // High s value: set to curve order - 1 (definitely > halfCurveOrder)
        // Curve order hex: ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551
        val highS = hexToByteArray(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550"
        )

        val derSig = buildDerSignature(r, highS)
        val compact = SmartAccountUtils.normalizeSignature(derSig)

        assertEquals(64, compact.size)

        // After normalization, s should be n - s, which should be 1
        val extractedS = compact.copyOfRange(32, 64)
        val expectedNormalizedS = ByteArray(32)
        expectedNormalizedS[31] = 0x01 // n - (n-1) = 1

        assertContentEquals(
            expectedNormalizedS, extractedS,
            "High-S value should be normalized to n - s"
        )
    }

    @Test
    fun testNormalizeSignature_rWithLeadingZeroPadding() {
        // DER encoding adds a 0x00 prefix when the first byte has the high bit set.
        // Use a value with high bit set but below the curve order.
        val r = ByteArray(32) { 0x80.toByte() }
        r[31] = 0x01 // 0x808080...8001, well below curve order
        val s = ByteArray(32)
        s[31] = 0x01

        // Build DER with explicit leading zero on r
        val rWithPadding = byteArrayOf(0x00) + r
        val derSig = buildDerSignatureRaw(rWithPadding, s)

        val compact = SmartAccountUtils.normalizeSignature(derSig)
        assertEquals(64, compact.size)

        val extractedR = compact.copyOfRange(0, 32)
        assertContentEquals(r, extractedR, "Leading zero padding should be stripped from r")
    }

    @Test
    fun testNormalizeSignature_sWithLeadingZeroPadding() {
        val r = ByteArray(32)
        r[31] = 0x01
        val s = ByteArray(32) { 0x80.toByte() } // High bit set

        val sWithPadding = byteArrayOf(0x00) + s
        val derSig = buildDerSignatureRaw(r, sWithPadding)

        val compact = SmartAccountUtils.normalizeSignature(derSig)
        assertEquals(64, compact.size)
    }

    @Test
    fun testNormalizeSignature_shortRComponent_padded() {
        // r component shorter than 32 bytes should be left-padded with zeros
        val shortR = byteArrayOf(0x01, 0x02, 0x03)
        val s = ByteArray(32)
        s[31] = 0x01

        val derSig = buildDerSignatureRaw(shortR, s)
        val compact = SmartAccountUtils.normalizeSignature(derSig)

        val expectedR = ByteArray(32)
        expectedR[29] = 0x01
        expectedR[30] = 0x02
        expectedR[31] = 0x03

        val extractedR = compact.copyOfRange(0, 32)
        assertContentEquals(expectedR, extractedR, "Short r should be left-padded to 32 bytes")
    }

    @Test
    fun testNormalizeSignature_invalidDer_noSequenceTag_throwsValidationException() {
        val invalid = byteArrayOf(0x00, 0x44, 0x02, 0x20) + ByteArray(64)

        assertFailsWith<ValidationException.InvalidInput>(
            "Must throw for DER without 0x30 sequence tag"
        ) {
            SmartAccountUtils.normalizeSignature(invalid)
        }
    }

    @Test
    fun testNormalizeSignature_tooShort_throwsValidationException() {
        assertFailsWith<ValidationException.InvalidInput>(
            "Must throw for DER shorter than 8 bytes"
        ) {
            SmartAccountUtils.normalizeSignature(byteArrayOf(0x30, 0x06, 0x02, 0x01, 0x01))
        }
    }

    @Test
    fun testNormalizeSignature_emptyInput_throwsValidationException() {
        assertFailsWith<ValidationException.InvalidInput> {
            SmartAccountUtils.normalizeSignature(ByteArray(0))
        }
    }

    @Test
    fun testNormalizeSignature_missingRComponentMarker_throwsValidationException() {
        // 0x30 [len] then 0x03 instead of 0x02 for r component
        val invalid = byteArrayOf(
            0x30, 0x44, 0x03, 0x20
        ) + ByteArray(32) + byteArrayOf(0x02, 0x20) + ByteArray(32)

        assertFailsWith<ValidationException.InvalidInput>(
            "Must throw when r component marker is not 0x02"
        ) {
            SmartAccountUtils.normalizeSignature(invalid)
        }
    }

    @Test
    fun testNormalizeSignature_truncatedRComponent_throwsValidationException() {
        // Claims r is 32 bytes but only provides 16
        val invalid = byteArrayOf(
            0x30, 0x30, 0x02, 0x20
        ) + ByteArray(16)

        assertFailsWith<ValidationException.InvalidInput>(
            "Must throw when r component is truncated"
        ) {
            SmartAccountUtils.normalizeSignature(invalid)
        }
    }

    @Test
    fun testNormalizeSignature_missingSComponentMarker_throwsValidationException() {
        // Valid r but then 0x03 instead of 0x02 for s component
        val invalid = byteArrayOf(
            0x30, 0x44, 0x02, 0x20
        ) + ByteArray(32) + byteArrayOf(0x03, 0x20) + ByteArray(32)

        assertFailsWith<ValidationException.InvalidInput>(
            "Must throw when s component marker is not 0x02"
        ) {
            SmartAccountUtils.normalizeSignature(invalid)
        }
    }

    @Test
    fun testNormalizeSignature_realWorldDerSignature() {
        // A realistic DER-encoded ECDSA signature (70 bytes)
        val r = hexToByteArray(
            "b15c5fb4f64a2527aa0d48a1dfb1a7ed94b5f2c3a1b0e9d8c7f6e5d4c3b2a190"
        )
        val s = hexToByteArray(
            "1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b"
        )
        val derSig = buildDerSignature(r, s)

        val compact = SmartAccountUtils.normalizeSignature(derSig)
        assertEquals(64, compact.size)

        val extractedR = compact.copyOfRange(0, 32)
        assertContentEquals(r, extractedR)
    }

    // =========================================================================
    // WebAuthnRegistrationResult Data Class Tests
    // =========================================================================

    @Test
    fun testRegistrationResult_equality() {
        val credId = ByteArray(16) { it.toByte() }
        val pubKey = expectedPublicKey
        val attestObj = buildAttestationObject()

        val result1 = WebAuthnRegistrationResult(
            credentialId = credId,
            publicKey = pubKey,
            attestationObject = attestObj,
            transports = listOf("internal"),
            deviceType = "multiDevice",
            backedUp = true
        )

        val result2 = WebAuthnRegistrationResult(
            credentialId = credId.copyOf(),
            publicKey = pubKey.copyOf(),
            attestationObject = attestObj.copyOf(),
            transports = listOf("internal"),
            deviceType = "multiDevice",
            backedUp = true
        )

        assertEquals(result1, result2, "Registration results with same content must be equal")
        assertEquals(result1.hashCode(), result2.hashCode(), "Equal objects must have same hashCode")
    }

    @Test
    fun testRegistrationResult_inequality_differentCredentialId() {
        val attestObj = buildAttestationObject()

        val result1 = WebAuthnRegistrationResult(
            credentialId = ByteArray(16) { 0x01 },
            publicKey = expectedPublicKey,
            attestationObject = attestObj
        )

        val result2 = WebAuthnRegistrationResult(
            credentialId = ByteArray(16) { 0x02 },
            publicKey = expectedPublicKey,
            attestationObject = attestObj
        )

        assertFalse(
            result1 == result2,
            "Registration results with different credential IDs must not be equal"
        )
    }

    @Test
    fun testRegistrationResult_optionalFieldsDefaults() {
        val result = WebAuthnRegistrationResult(
            credentialId = ByteArray(16),
            publicKey = expectedPublicKey,
            attestationObject = buildAttestationObject()
        )

        assertNull(result.transports, "transports should default to null")
        assertNull(result.deviceType, "deviceType should default to null")
        assertNull(result.backedUp, "backedUp should default to null")
    }

    // =========================================================================
    // WebAuthnAuthenticationResult Data Class Tests
    // =========================================================================

    @Test
    fun testAuthenticationResult_equality() {
        val credId = ByteArray(16) { it.toByte() }
        val authData = ByteArray(37) { it.toByte() }
        val clientData = """{"type":"webauthn.get","challenge":"abc"}""".encodeToByteArray()
        val sig = ByteArray(64) { it.toByte() }

        val result1 = WebAuthnAuthenticationResult(
            credentialId = credId,
            authenticatorData = authData,
            clientDataJSON = clientData,
            signature = sig
        )

        val result2 = WebAuthnAuthenticationResult(
            credentialId = credId.copyOf(),
            authenticatorData = authData.copyOf(),
            clientDataJSON = clientData.copyOf(),
            signature = sig.copyOf()
        )

        assertEquals(result1, result2, "Authentication results with same content must be equal")
        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun testAuthenticationResult_inequality_differentSignature() {
        val base = WebAuthnAuthenticationResult(
            credentialId = ByteArray(16),
            authenticatorData = ByteArray(37),
            clientDataJSON = ByteArray(100),
            signature = ByteArray(64) { 0x01 }
        )

        val different = WebAuthnAuthenticationResult(
            credentialId = ByteArray(16),
            authenticatorData = ByteArray(37),
            clientDataJSON = ByteArray(100),
            signature = ByteArray(64) { 0x02 }
        )

        assertFalse(
            base == different,
            "Authentication results with different signatures must not be equal"
        )
    }

    // =========================================================================
    // WebAuthnSignature (SmartAccountSignatures) Tests
    // =========================================================================

    @Test
    fun testWebAuthnSignature_validConstruction() {
        val authData = ByteArray(37) { it.toByte() }
        val clientData = ByteArray(100) { it.toByte() }
        val sig = ByteArray(64) { it.toByte() }

        val webAuthnSig = WebAuthnSignature(
            authenticatorData = authData,
            clientData = clientData,
            signature = sig
        )

        assertContentEquals(authData, webAuthnSig.authenticatorData)
        assertContentEquals(clientData, webAuthnSig.clientData)
        assertContentEquals(sig, webAuthnSig.signature)
    }

    @Test
    fun testWebAuthnSignature_invalidSignatureSize_throwsValidationException() {
        assertFailsWith<ValidationException.InvalidInput>(
            "Must throw when signature is not 64 bytes"
        ) {
            WebAuthnSignature(
                authenticatorData = ByteArray(37),
                clientData = ByteArray(100),
                signature = ByteArray(32) // Wrong size (should be 64)
            )
        }
    }

    @Test
    fun testWebAuthnSignature_toScVal_alphabeticalFieldOrder() {
        val webAuthnSig = WebAuthnSignature(
            authenticatorData = ByteArray(37),
            clientData = ByteArray(100),
            signature = ByteArray(64)
        )

        val scVal = webAuthnSig.toScVal()
        assertTrue(scVal is SCValXdr.Map, "WebAuthnSignature ScVal must be a Map")

        val map = (scVal as SCValXdr.Map).value as SCMapXdr
        val entries = map.value
        assertEquals(3, entries.size, "Map must have 3 entries")

        // Verify alphabetical key ordering
        val key0 = (entries[0].key as SCValXdr.Sym).value.value
        val key1 = (entries[1].key as SCValXdr.Sym).value.value
        val key2 = (entries[2].key as SCValXdr.Sym).value.value

        assertEquals("authenticator_data", key0, "First key must be 'authenticator_data'")
        assertEquals("client_data", key1, "Second key must be 'client_data'")
        assertEquals("signature", key2, "Third key must be 'signature'")
    }

    @Test
    fun testWebAuthnSignature_toScVal_fieldNameIsClientData_notClientDataJson() {
        // The contract expects "client_data", NOT "client_data_json"
        val webAuthnSig = WebAuthnSignature(
            authenticatorData = ByteArray(37),
            clientData = ByteArray(100),
            signature = ByteArray(64)
        )

        val scVal = webAuthnSig.toScVal()
        val map = (scVal as SCValXdr.Map).value as SCMapXdr
        val keys = map.value.map { (it.key as SCValXdr.Sym).value.value }

        assertTrue(
            keys.contains("client_data"),
            "Must use field name 'client_data'"
        )
        assertFalse(
            keys.contains("client_data_json"),
            "Must NOT use field name 'client_data_json'"
        )
    }

    @Test
    fun testWebAuthnSignature_equality() {
        val sig1 = WebAuthnSignature(
            authenticatorData = ByteArray(37) { 0x01 },
            clientData = ByteArray(100) { 0x02 },
            signature = ByteArray(64) { 0x03 }
        )

        val sig2 = WebAuthnSignature(
            authenticatorData = ByteArray(37) { 0x01 },
            clientData = ByteArray(100) { 0x02 },
            signature = ByteArray(64) { 0x03 }
        )

        assertEquals(sig1, sig2, "WebAuthnSignatures with same content must be equal")
        assertEquals(sig1.hashCode(), sig2.hashCode())
    }

    // =========================================================================
    // CBOR Attestation Object with Authenticator Data Tests
    // =========================================================================

    @Test
    fun testCborAttestationObject_extractionViaStrategy2() {
        // Build a proper CBOR attestation object with embedded authenticator data
        val authData = buildAuthenticatorData(flags = 0x41) // UP + AT
        val cborAttestation = buildCborAttestationObject(authData)

        // Strategy 2 via extractPublicKeyFromRegistration with authenticator data
        // should work when extracting from the attestation object via strategy 3
        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            attestationObject = cborAttestation
        )

        // The COSE key pattern should still be found in the CBOR attestation object
        // because strategy 3 (pattern matching) searches the entire byte sequence
        assertContentEquals(expectedPublicKey, result)
    }

    @Test
    fun testCborAttestationObject_shortAuthData_fallsToPattern() {
        // Build authenticator data without AT flag (strategy 2 will fail)
        // But the COSE key is still in the attestation object bytes (strategy 3 succeeds)
        val authDataNoAT = ByteArray(37) { 0xAA.toByte() }
        authDataNoAT[32] = 0x01 // UP only, no AT

        // Append the COSE key pattern separately in the overall attestation object
        val cborAttestation = buildCborAttestationObject(authDataNoAT) + buildCoseKey()

        val result = SmartAccountUtils.extractPublicKeyFromRegistration(
            attestationObject = cborAttestation
        )

        assertContentEquals(expectedPublicKey, result)
    }

    // =========================================================================
    // Secp256r1 Public Key Constants Tests
    // =========================================================================

    @Test
    fun testSecp256r1PublicKeySize_is65() {
        assertEquals(65, SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
    }

    @Test
    fun testUncompressedPubkeyPrefix_is0x04() {
        assertEquals(0x04.toByte(), SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX)
    }

    @Test
    fun testWebAuthnTimeoutMs_is60000() {
        assertEquals(60_000L, SmartAccountConstants.WEBAUTHN_TIMEOUT_MS)
    }

    // =========================================================================
    // Private Test Helpers
    // =========================================================================

    /**
     * Builds a DER-encoded ECDSA signature from r and s components.
     * Automatically adds 0x00 padding when the high bit of r or s is set.
     *
     * DER format: 0x30 [total_len] 0x02 [r_len] [r_bytes] 0x02 [s_len] [s_bytes]
     */
    private fun buildDerSignature(r: ByteArray, s: ByteArray): ByteArray {
        // Add 0x00 prefix if high bit is set
        val rPadded = if (r[0].toInt() and 0x80 != 0) byteArrayOf(0x00) + r else r
        val sPadded = if (s[0].toInt() and 0x80 != 0) byteArrayOf(0x00) + s else s

        return buildDerSignatureRaw(rPadded, sPadded)
    }

    /**
     * Builds a DER-encoded ECDSA signature from raw r and s byte arrays (no automatic padding).
     */
    private fun buildDerSignatureRaw(r: ByteArray, s: ByteArray): ByteArray {
        val totalLength = 2 + r.size + 2 + s.size // 0x02 [len] r 0x02 [len] s
        return byteArrayOf(
            0x30.toByte(),
            totalLength.toByte(),
            0x02,
            r.size.toByte()
        ) + r + byteArrayOf(
            0x02,
            s.size.toByte()
        ) + s
    }

    /**
     * Converts a hex string to ByteArray.
     */
    private fun hexToByteArray(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            val index = i * 2
            hex.substring(index, index + 2).toInt(16).toByte()
        }
    }
}
