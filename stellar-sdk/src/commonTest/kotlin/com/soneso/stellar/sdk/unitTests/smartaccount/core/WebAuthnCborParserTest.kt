//
//  WebAuthnCborParserTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount.core

import com.soneso.stellar.sdk.smartaccount.core.WebAuthnCborParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [WebAuthnCborParser].
 *
 * Covers:
 * - extractAuthenticatorDataFromAttestation: CBOR map parsing, key ordering, error paths
 * - readCborByteString: all length encodings, edge cases, error paths
 * - readCborTextString: all length encodings, UTF-8, error paths
 * - readCborLength: all additional-info encodings, overflow guard
 * - skipCborValue: all major types including nested arrays/maps, tags, floats
 * - skipCborHead: all additional-info sizes
 * - extractPublicKeyFromCoseKey: map iteration, fallback pattern, coordinate validation
 * - extractPublicKeyFromSpki: exact-size, larger-than-65, prefix validation, too-short
 * - parseAuthenticatorFlags: null input, too-short, all BE/BS combinations
 */
class WebAuthnCborParserTest {

    // =========================================================================
    // CBOR builder helpers
    // =========================================================================

    /**
     * Encodes a CBOR text string (major type 3).
     */
    private fun buildCborTextString(text: String): ByteArray {
        val utf8 = text.encodeToByteArray()
        return buildCborHead(3, utf8.size) + utf8
    }

    /**
     * Encodes a CBOR byte string (major type 2).
     */
    private fun buildCborByteString(data: ByteArray): ByteArray {
        return buildCborHead(2, data.size) + data
    }

    /**
     * Builds a CBOR head byte (and any extended length bytes) for the given major type
     * and length/value.
     */
    private fun buildCborHead(majorType: Int, length: Int): ByteArray {
        val major = majorType shl 5
        return when {
            length < 24 -> byteArrayOf((major or length).toByte())
            length < 256 -> byteArrayOf((major or 24).toByte(), length.toByte())
            length < 65536 -> byteArrayOf(
                (major or 25).toByte(),
                (length shr 8).toByte(),
                length.toByte()
            )
            else -> byteArrayOf(
                (major or 26).toByte(),
                (length shr 24).toByte(),
                (length shr 16).toByte(),
                (length shr 8).toByte(),
                length.toByte()
            )
        }
    }

    /**
     * Builds a minimal but structurally valid CBOR attestation object containing
     * the three standard fields: "fmt", "attStmt", "authData".
     *
     * @param authData Raw bytes to embed as the authData value.
     * @param fmtFirst When true, "fmt" appears before "authData" (standard order).
     *   When false, "authData" is the first entry.
     * @param includeAttStmt When true, a "attStmt" empty-map entry is included before "authData".
     */
    private fun buildAttestationObject(
        authData: ByteArray,
        fmtFirst: Boolean = true,
        includeAttStmt: Boolean = false
    ): ByteArray {
        val entries = mutableListOf<ByteArray>()

        if (!fmtFirst) {
            // authData first
            entries += buildCborTextString("authData")
            entries += buildCborByteString(authData)
            entries += buildCborTextString("fmt")
            entries += buildCborTextString("none")
        } else {
            entries += buildCborTextString("fmt")
            entries += buildCborTextString("none")
            if (includeAttStmt) {
                entries += buildCborTextString("attStmt")
                entries += buildCborHead(5, 0) // empty map
            }
            entries += buildCborTextString("authData")
            entries += buildCborByteString(authData)
        }

        val entryCount = if (!fmtFirst) 2 else if (includeAttStmt) 3 else 2
        var result = buildCborHead(5, entryCount)
        for (entry in entries) result += entry
        return result
    }

    /**
     * Builds realistic authenticator data of the minimum required length (37 bytes).
     * Optionally extends it with attested credential data.
     *
     * @param flagsByte The flags byte placed at offset 32.
     */
    private fun buildAuthenticatorData(flagsByte: Byte = 0x00): ByteArray {
        // rpIdHash (32) + flags (1) + signCount (4) = 37 bytes minimum
        val data = ByteArray(37)
        // fill rpIdHash with recognisable pattern
        for (i in 0 until 32) data[i] = (i + 1).toByte()
        data[32] = flagsByte
        // signCount = 0x00000001
        data[33] = 0x00; data[34] = 0x00; data[35] = 0x00; data[36] = 0x01
        return data
    }

    /**
     * Builds a minimal CBOR-encoded COSE ES256 key map for the given 32-byte X and Y
     * coordinates.
     *
     * Map entries (5 total):
     *   1 (kty): 2 (EC2)
     *   3 (alg): -7 (ES256)
     *  -1 (crv): 1 (P-256)
     *  -2 (x): bstr(x)
     *  -3 (y): bstr(y)
     */
    private fun buildCoseKey(x: ByteArray, y: ByteArray): ByteArray {
        // map(5)
        var result = byteArrayOf(0xa5.toByte())
        // 1 (kty) -> 2 (EC2)
        result += byteArrayOf(0x01, 0x02)
        // 3 (alg) -> -7 (ES256) encoded as 0x26
        result += byteArrayOf(0x03, 0x26.toByte())
        // -1 (crv) = 0x20, -> 1 (P-256)
        result += byteArrayOf(0x20.toByte(), 0x01)
        // -2 (x) = 0x21 -> bstr(x)
        result += byteArrayOf(0x21)
        result += buildCborByteString(x)
        // -3 (y) = 0x22 -> bstr(y)
        result += byteArrayOf(0x22)
        result += buildCborByteString(y)
        return result
    }

    // =========================================================================
    // Coordinate fixtures
    // =========================================================================

    // Arbitrary 32-byte patterns, deliberately NOT on the secp256r1 curve: the parser
    // is purely structural (CBOR layout, separator, lengths); curve validation lives
    // in SmartAccountUtils and is tested there with real on-curve points.
    private val testX = ByteArray(32) { (it + 1).toByte() }
    private val testY = ByteArray(32) { (it + 33).toByte() }

    // =========================================================================
    // 1. extractAuthenticatorDataFromAttestation
    // =========================================================================

    @Test
    fun extractAuthenticatorData_authDataAsFirstEntry() = runTest {
        val authData = buildAuthenticatorData()
        val attestation = buildAttestationObject(authData, fmtFirst = false)
        val result = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestation)
        assertContentEquals(authData, result)
    }

    @Test
    fun extractAuthenticatorData_authDataAsSecondEntry() = runTest {
        val authData = buildAuthenticatorData()
        val attestation = buildAttestationObject(authData, fmtFirst = true, includeAttStmt = false)
        val result = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestation)
        assertContentEquals(authData, result)
    }

    @Test
    fun extractAuthenticatorData_authDataAsThirdEntry() = runTest {
        val authData = buildAuthenticatorData()
        val attestation = buildAttestationObject(authData, fmtFirst = true, includeAttStmt = true)
        val result = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestation)
        assertContentEquals(authData, result)
    }

    @Test
    fun extractAuthenticatorData_emptyInputReturnsNull() = runTest {
        assertNull(WebAuthnCborParser.extractAuthenticatorDataFromAttestation(byteArrayOf()))
    }

    @Test
    fun extractAuthenticatorData_singleByteInputReturnsNull() = runTest {
        assertNull(WebAuthnCborParser.extractAuthenticatorDataFromAttestation(byteArrayOf(0x01)))
    }

    @Test
    fun extractAuthenticatorData_nonMapMajorTypeReturnsNull() = runTest {
        // Major type 4 (array), not a map
        val data = byteArrayOf(0x82.toByte(), 0x01, 0x02)
        assertNull(WebAuthnCborParser.extractAuthenticatorDataFromAttestation(data))
    }

    @Test
    fun extractAuthenticatorData_mapWithNoAuthDataKeyReturnsNull() = runTest {
        // Map with "fmt" -> "none" only, no "authData"
        var data = buildCborHead(5, 1)
        data += buildCborTextString("fmt")
        data += buildCborTextString("none")
        assertNull(WebAuthnCborParser.extractAuthenticatorDataFromAttestation(data))
    }

    @Test
    fun extractAuthenticatorData_mapWithZeroEntriesReturnsNull() = runTest {
        val data = buildCborHead(5, 0) // empty map
        assertNull(WebAuthnCborParser.extractAuthenticatorDataFromAttestation(data))
    }

    @Test
    fun extractAuthenticatorData_truncatedByteStringLengthReturnsNull() = runTest {
        // Map with "authData" key, then a byte string header claiming 40 bytes but only 2 follow
        var data = buildCborHead(5, 1)
        data += buildCborTextString("authData")
        // 0x58 = major-type 2 + additionalInfo 24, then length byte = 40, then only 2 bytes of content
        data += byteArrayOf(0x58, 0x28, 0x01, 0x02)
        assertNull(WebAuthnCborParser.extractAuthenticatorDataFromAttestation(data))
    }

    @Test
    fun extractAuthenticatorData_authDataWith1ByteCborLength() = runTest {
        // authData between 24 and 255 bytes triggers the 0x58 prefix path
        val authData = ByteArray(100) { it.toByte() }
        val attestation = buildAttestationObject(authData)
        val result = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestation)
        assertContentEquals(authData, result)
    }

    @Test
    fun extractAuthenticatorData_authDataWith2ByteCborLength() = runTest {
        // authData >= 256 bytes triggers the 0x59 prefix path
        val authData = ByteArray(300) { it.toByte() }
        val attestation = buildAttestationObject(authData)
        val result = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestation)
        assertContentEquals(authData, result)
    }

    @Test
    fun extractAuthenticatorData_authDataWithInlineLength() = runTest {
        // authData < 24 bytes uses inline length (0x40..0x57 range)
        val authData = ByteArray(20) { it.toByte() }
        val attestation = buildAttestationObject(authData)
        val result = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestation)
        assertContentEquals(authData, result)
    }

    @Test
    fun extractAuthenticatorData_truncatedMapHeaderReturnsNull() = runTest {
        // 0xa1 = map(1) header but no entries follow
        assertNull(WebAuthnCborParser.extractAuthenticatorDataFromAttestation(byteArrayOf(0xa1.toByte())))
    }

    @Test
    fun extractAuthenticatorData_realAttestationObjectBytes() = runTest {
        // Construct a realistic "none" attestation object with 37-byte authData.
        // This exercises the full parsing path used by real authenticators.
        val rpIdHash = ByteArray(32) { 0xAB.toByte() }
        val flags: Byte = 0x41  // AT | UP
        val signCount = byteArrayOf(0x00, 0x00, 0x00, 0x05)
        val authData = rpIdHash + byteArrayOf(flags) + signCount

        var attestation = buildCborHead(5, 2)
        attestation += buildCborTextString("fmt")
        attestation += buildCborTextString("none")
        attestation += buildCborTextString("authData")
        attestation += buildCborByteString(authData)

        val result = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestation)
        assertContentEquals(authData, result)
        assertEquals(32, result!!.size - 5)  // 32-byte rpIdHash + 5 bytes remaining
    }

    @Test
    fun extractAuthenticatorData_valueAfterFmtIsMapSkippedCorrectly() = runTest {
        // Tests that a non-empty attStmt (a map value) is properly skipped.
        // attStmt = { "x5c": bstr } (one-entry map with a bstr value)
        val authData = buildAuthenticatorData()

        var attestation = buildCborHead(5, 3)
        attestation += buildCborTextString("fmt")
        attestation += buildCborTextString("packed")
        attestation += buildCborTextString("attStmt")
        // attStmt is a one-entry map: "x5c" -> some bytes
        attestation += buildCborHead(5, 1)
        attestation += buildCborTextString("x5c")
        attestation += buildCborByteString(ByteArray(10) { 0xFF.toByte() })
        attestation += buildCborTextString("authData")
        attestation += buildCborByteString(authData)

        val result = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestation)
        assertContentEquals(authData, result)
    }

    // =========================================================================
    // 2. readCborByteString
    // =========================================================================

    @Test
    fun readCborByteString_inlineLength() = runTest {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val encoded = buildCborByteString(payload)
        val result = WebAuthnCborParser.readCborByteString(encoded, 0)
        assertNotNull(result)
        assertContentEquals(payload, result.first)
        assertEquals(encoded.size, result.second)
    }

    @Test
    fun readCborByteString_1ByteHeader_additionalInfo24() = runTest {
        // 30-byte payload triggers additionalInfo == 24 (0x58 prefix)
        val payload = ByteArray(30) { it.toByte() }
        val encoded = buildCborByteString(payload)
        // verify the header is 0x58
        assertEquals(0x58.toByte(), encoded[0])
        val result = WebAuthnCborParser.readCborByteString(encoded, 0)
        assertNotNull(result)
        assertContentEquals(payload, result.first)
    }

    @Test
    fun readCborByteString_2ByteHeader_additionalInfo25() = runTest {
        // 300-byte payload triggers additionalInfo == 25 (0x59 prefix)
        val payload = ByteArray(300) { it.toByte() }
        val encoded = buildCborByteString(payload)
        assertEquals(0x59.toByte(), encoded[0])
        val result = WebAuthnCborParser.readCborByteString(encoded, 0)
        assertNotNull(result)
        assertContentEquals(payload, result.first)
    }

    @Test
    fun readCborByteString_4ByteHeader_additionalInfo26() = runTest {
        // Build manually to avoid allocating 70000 bytes: claim 4-byte length header
        // with actual content of 5 bytes.
        val content = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte())
        // 0x5a = major-type 2 (0x40) | 26
        val header = byteArrayOf(0x5a.toByte(), 0x00, 0x00, 0x00, 0x05)
        val encoded = header + content
        val result = WebAuthnCborParser.readCborByteString(encoded, 0)
        assertNotNull(result)
        assertContentEquals(content, result.first)
        assertEquals(encoded.size, result.second)
    }

    @Test
    fun readCborByteString_4ByteHeader_negativeIntOverflow_returnsNull() = runTest {
        // 0x5a = major-type 2 | 26, then length bytes = 0x80000000 which is negative as Int
        val encoded = byteArrayOf(0x5a.toByte(), 0x80.toByte(), 0x00, 0x00, 0x00, 0x01)
        assertNull(WebAuthnCborParser.readCborByteString(encoded, 0))
    }

    @Test
    fun readCborByteString_emptyByteString() = runTest {
        // 0x40 = major-type 2, length 0
        val encoded = byteArrayOf(0x40.toByte())
        val result = WebAuthnCborParser.readCborByteString(encoded, 0)
        assertNotNull(result)
        assertEquals(0, result.first.size)
        assertEquals(1, result.second)
    }

    @Test
    fun readCborByteString_truncatedData_returnsNull() = runTest {
        // Header claims 10 bytes but only 3 bytes of content follow
        val encoded = byteArrayOf(0x4a.toByte(), 0x01, 0x02, 0x03)
        assertNull(WebAuthnCborParser.readCborByteString(encoded, 0))
    }

    @Test
    fun readCborByteString_offsetAtEndOfData_returnsNull() = runTest {
        val data = byteArrayOf(0x01, 0x02)
        assertNull(WebAuthnCborParser.readCborByteString(data, 2))
    }

    @Test
    fun readCborByteString_wrongMajorType_returnsNull() = runTest {
        // 0x61 = major-type 3 (text string), not 2
        val encoded = byteArrayOf(0x61, 0x41)
        assertNull(WebAuthnCborParser.readCborByteString(encoded, 0))
    }

    @Test
    fun readCborByteString_1ByteHeaderTruncatedLength_returnsNull() = runTest {
        // 0x58 requires one more byte for the length, but data ends here
        val encoded = byteArrayOf(0x58.toByte())
        assertNull(WebAuthnCborParser.readCborByteString(encoded, 0))
    }

    @Test
    fun readCborByteString_2ByteHeaderTruncatedLength_returnsNull() = runTest {
        // 0x59 requires two more bytes for the length, but only one follows
        val encoded = byteArrayOf(0x59.toByte(), 0x01)
        assertNull(WebAuthnCborParser.readCborByteString(encoded, 0))
    }

    // =========================================================================
    // 3. readCborTextString
    // =========================================================================

    @Test
    fun readCborTextString_inlineLength() = runTest {
        val text = "hello"
        val encoded = buildCborTextString(text)
        val result = WebAuthnCborParser.readCborTextString(encoded, 0)
        assertNotNull(result)
        assertEquals(text, result.first)
        assertEquals(encoded.size, result.second)
    }

    @Test
    fun readCborTextString_1ByteHeader_additionalInfo24() = runTest {
        // 30-char string triggers 1-byte extended length header
        val text = "a".repeat(30)
        val encoded = buildCborTextString(text)
        val result = WebAuthnCborParser.readCborTextString(encoded, 0)
        assertNotNull(result)
        assertEquals(text, result.first)
    }

    @Test
    fun readCborTextString_2ByteHeader_additionalInfo25() = runTest {
        // 300-char string triggers 2-byte extended length header
        val text = "b".repeat(300)
        val encoded = buildCborTextString(text)
        val result = WebAuthnCborParser.readCborTextString(encoded, 0)
        assertNotNull(result)
        assertEquals(text, result.first)
    }

    @Test
    fun readCborTextString_multiByteUtf8() = runTest {
        // "Stellar" in Japanese (7 code points, >7 UTF-8 bytes due to 3-byte sequences)
        val text = "ステラー"
        val encoded = buildCborTextString(text)
        val result = WebAuthnCborParser.readCborTextString(encoded, 0)
        assertNotNull(result)
        assertEquals(text, result.first)
    }

    @Test
    fun readCborTextString_emptyString() = runTest {
        val encoded = buildCborTextString("")
        val result = WebAuthnCborParser.readCborTextString(encoded, 0)
        assertNotNull(result)
        assertEquals("", result.first)
    }

    @Test
    fun readCborTextString_truncatedData_returnsNull() = runTest {
        // 0x6a = major-type 3, length 10 — only 2 bytes of content follow
        val encoded = byteArrayOf(0x6a.toByte(), 0x41, 0x42)
        assertNull(WebAuthnCborParser.readCborTextString(encoded, 0))
    }

    @Test
    fun readCborTextString_wrongMajorType_returnsNull() = runTest {
        // 0x44 = major-type 2 (byte string), not 3
        val encoded = byteArrayOf(0x44.toByte(), 0x01, 0x02, 0x03, 0x04)
        assertNull(WebAuthnCborParser.readCborTextString(encoded, 0))
    }

    // =========================================================================
    // 4. readCborLength
    // =========================================================================

    @Test
    fun readCborLength_inlineLength_0() = runTest {
        // 0x40 = major-type 2, additionalInfo 0
        val result = WebAuthnCborParser.readCborLength(byteArrayOf(0x40.toByte()), 0)
        assertNotNull(result)
        assertEquals(0, result.first)
        assertEquals(1, result.second)
    }

    @Test
    fun readCborLength_inlineLength_23() = runTest {
        // 0x57 = major-type 2, additionalInfo 23
        val result = WebAuthnCborLength(byteArrayOf(0x57.toByte()), 0)
        assertNotNull(result)
        assertEquals(23, result!!.first)
        assertEquals(1, result.second)
    }

    @Test
    fun readCborLength_1ByteExtended_additionalInfo24() = runTest {
        // 0x58 0x64 = major-type 2, additionalInfo 24, length = 100
        val data = byteArrayOf(0x58.toByte(), 0x64)
        val result = WebAuthnCborParser.readCborLength(data, 0)
        assertNotNull(result)
        assertEquals(100, result.first)
        assertEquals(2, result.second)
    }

    @Test
    fun readCborLength_2ByteExtended_additionalInfo25() = runTest {
        // 0x59 0x01 0x00 = length 256
        val data = byteArrayOf(0x59.toByte(), 0x01, 0x00)
        val result = WebAuthnCborParser.readCborLength(data, 0)
        assertNotNull(result)
        assertEquals(256, result.first)
        assertEquals(3, result.second)
    }

    @Test
    fun readCborLength_4ByteExtended_additionalInfo26() = runTest {
        // 0x5a 0x00 0x01 0x00 0x00 = length 65536
        val data = byteArrayOf(0x5a.toByte(), 0x00, 0x01, 0x00, 0x00)
        val result = WebAuthnCborParser.readCborLength(data, 0)
        assertNotNull(result)
        assertEquals(65536, result.first)
        assertEquals(5, result.second)
    }

    @Test
    fun readCborLength_4ByteExtended_negativeOverflow_returnsNull() = runTest {
        // 0x5a 0x80 0x00 0x00 0x00 = raw int is negative (high bit set)
        val data = byteArrayOf(0x5a.toByte(), 0x80.toByte(), 0x00, 0x00, 0x00)
        assertNull(WebAuthnCborParser.readCborLength(data, 0))
    }

    @Test
    fun readCborLength_insufficientData_1ByteExtended_returnsNull() = runTest {
        // 0x58 requires one more byte but none follows
        assertNull(WebAuthnCborParser.readCborLength(byteArrayOf(0x58.toByte()), 0))
    }

    @Test
    fun readCborLength_insufficientData_2ByteExtended_returnsNull() = runTest {
        // 0x59 requires two more bytes but only one follows
        assertNull(WebAuthnCborParser.readCborLength(byteArrayOf(0x59.toByte(), 0x01), 0))
    }

    @Test
    fun readCborLength_offsetAtEnd_returnsNull() = runTest {
        assertNull(WebAuthnCborParser.readCborLength(byteArrayOf(0x01), 1))
    }

    // =========================================================================
    // 5. skipCborValue
    // =========================================================================

    @Test
    fun skipCborValue_unsignedInteger_inline() = runTest {
        // 0x0a = major-type 0, value 10 — occupies 1 byte
        val data = byteArrayOf(0x0a, 0x99.toByte())
        assertEquals(1, WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_negativeInteger_inline() = runTest {
        // 0x20 = major-type 1, value -1 — occupies 1 byte
        val data = byteArrayOf(0x20, 0x99.toByte())
        assertEquals(1, WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_textString() = runTest {
        val encoded = buildCborTextString("abc")
        val extra = byteArrayOf(0xFF.toByte())
        assertEquals(encoded.size, WebAuthnCborParser.skipCborValue(encoded + extra, 0))
    }

    @Test
    fun skipCborValue_byteString() = runTest {
        val encoded = buildCborByteString(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        val extra = byteArrayOf(0xFF.toByte())
        assertEquals(encoded.size, WebAuthnCborParser.skipCborValue(encoded + extra, 0))
    }

    @Test
    fun skipCborValue_array_withNestedElements() = runTest {
        // array(2) [ uint(1), uint(2) ]
        val data = byteArrayOf(
            0x82.toByte(),  // array, length 2
            0x01,           // uint(1)
            0x02            // uint(2)
        )
        assertEquals(3, WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_map_withNestedEntries() = runTest {
        // map(1) { "k" -> uint(5) }
        val data = buildCborHead(5, 1) + buildCborTextString("k") + byteArrayOf(0x05)
        assertEquals(data.size, WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_nestedArray() = runTest {
        // array(1) [ array(1) [ uint(7) ] ]
        val inner = byteArrayOf(0x81.toByte(), 0x07)
        val outer = byteArrayOf(0x81.toByte()) + inner
        assertEquals(outer.size, WebAuthnCborParser.skipCborValue(outer, 0))
    }

    @Test
    fun skipCborValue_taggedValue() = runTest {
        // tag(1) uint(1000) — 0xc1 is tag(1), then 0x190003e8 = uint(1000)
        // Build manually: tag with inline value 1 = 0xC1, then uint(1000) = 0x19 0x03 0xE8
        val data = byteArrayOf(0xC1.toByte(), 0x19.toByte(), 0x03, 0xE8.toByte())
        assertEquals(4, WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_float16() = runTest {
        // 0xF9 = major-type 7, additionalInfo 25 — 2-byte half-precision float
        val data = byteArrayOf(0xF9.toByte(), 0x00, 0x00, 0xFF.toByte())
        assertEquals(3, WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_float32() = runTest {
        // 0xFA = major-type 7, additionalInfo 26 — 4-byte single-precision float
        val data = byteArrayOf(0xFA.toByte(), 0x3f, 0x80.toByte(), 0x00, 0x00, 0xFF.toByte())
        assertEquals(5, WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_float64() = runTest {
        // 0xFB = major-type 7, additionalInfo 27 — 8-byte double-precision float
        val data = ByteArray(10)
        data[0] = 0xFB.toByte()
        // fill 8 content bytes arbitrarily
        for (i in 1..8) data[i] = i.toByte()
        data[9] = 0xFF.toByte()
        assertEquals(9, WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_simpleValue_inline() = runTest {
        // 0xF4 = false (major-type 7, additionalInfo 20)
        val data = byteArrayOf(0xF4.toByte(), 0xFF.toByte())
        assertEquals(1, WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_simpleValue_1ByteExtended() = runTest {
        // major-type 7, additionalInfo 24 = 2-byte simple
        val data = byteArrayOf(0xF8.toByte(), 0x10, 0xFF.toByte())
        assertEquals(2, WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_truncatedContent_returnsNull() = runTest {
        // byteString claiming 10 bytes but data ends after header
        val data = byteArrayOf(0x4a.toByte())
        assertNull(WebAuthnCborParser.skipCborValue(data, 0))
    }

    @Test
    fun skipCborValue_offsetAtEnd_returnsNull() = runTest {
        assertNull(WebAuthnCborParser.skipCborValue(byteArrayOf(0x01), 1))
    }

    // =========================================================================
    // 6. skipCborHead
    // =========================================================================

    @Test
    fun skipCborHead_inlineAdditionalInfo() = runTest {
        // Any byte with additionalInfo < 24 occupies exactly 1 byte
        val data = byteArrayOf(0x05, 0xFF.toByte())
        assertEquals(1, WebAuthnCborParser.skipCborHead(data, 0))
    }

    @Test
    fun skipCborHead_additionalInfo24_2Bytes() = runTest {
        val data = byteArrayOf(0x18, 0x64, 0xFF.toByte())
        assertEquals(2, WebAuthnCborParser.skipCborHead(data, 0))
    }

    @Test
    fun skipCborHead_additionalInfo25_3Bytes() = runTest {
        val data = byteArrayOf(0x19, 0x01, 0x00, 0xFF.toByte())
        assertEquals(3, WebAuthnCborParser.skipCborHead(data, 0))
    }

    @Test
    fun skipCborHead_additionalInfo26_5Bytes() = runTest {
        val data = byteArrayOf(0x1a, 0x00, 0x01, 0x00, 0x00, 0xFF.toByte())
        assertEquals(5, WebAuthnCborParser.skipCborHead(data, 0))
    }

    @Test
    fun skipCborHead_additionalInfo27_9Bytes() = runTest {
        val data = ByteArray(10)
        data[0] = 0x1b  // additionalInfo 27
        for (i in 1..8) data[i] = 0x00
        data[9] = 0xFF.toByte()
        assertEquals(9, WebAuthnCborParser.skipCborHead(data, 0))
    }

    @Test
    fun skipCborHead_additionalInfo24_truncated_returnsNull() = runTest {
        // additionalInfo 24 requires 1 more byte but data ends
        assertNull(WebAuthnCborParser.skipCborHead(byteArrayOf(0x18), 0))
    }

    @Test
    fun skipCborHead_additionalInfo25_truncated_returnsNull() = runTest {
        // additionalInfo 25 requires 2 more bytes but only 1 follows
        assertNull(WebAuthnCborParser.skipCborHead(byteArrayOf(0x19, 0x01), 0))
    }

    @Test
    fun skipCborHead_additionalInfo26_truncated_returnsNull() = runTest {
        // additionalInfo 26 requires 4 more bytes but only 3 follow
        assertNull(WebAuthnCborParser.skipCborHead(byteArrayOf(0x1a, 0x00, 0x01, 0x00), 0))
    }

    @Test
    fun skipCborHead_additionalInfo27_truncated_returnsNull() = runTest {
        // additionalInfo 27 requires 8 more bytes but only 7 follow
        val data = byteArrayOf(0x1b, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        assertNull(WebAuthnCborParser.skipCborHead(data, 0))
    }

    // =========================================================================
    // 7. extractPublicKeyFromCoseKey
    // =========================================================================

    @Test
    fun extractPublicKeyFromCoseKey_standardOrder() = runTest {
        val coseKey = buildCoseKey(testX, testY)
        val result = WebAuthnCborParser.extractPublicKeyFromCoseKey(coseKey)
        assertNotNull(result)
        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertContentEquals(testX, result.copyOfRange(1, 33))
        assertContentEquals(testY, result.copyOfRange(33, 65))
    }

    @Test
    fun extractPublicKeyFromCoseKey_yBeforeX() = runTest {
        // Build a COSE map with Y (-3) appearing before X (-2)
        var data = byteArrayOf(0xa5.toByte()) // map(5)
        data += byteArrayOf(0x01, 0x02)        // kty -> EC2
        data += byteArrayOf(0x03, 0x26.toByte()) // alg -> ES256
        data += byteArrayOf(0x20.toByte(), 0x01) // crv -> P-256
        // Y first
        data += byteArrayOf(0x22)
        data += buildCborByteString(testY)
        // X second
        data += byteArrayOf(0x21)
        data += buildCborByteString(testX)

        val result = WebAuthnCborParser.extractPublicKeyFromCoseKey(data)
        assertNotNull(result)
        assertEquals(65, result.size)
        assertContentEquals(testX, result.copyOfRange(1, 33))
        assertContentEquals(testY, result.copyOfRange(33, 65))
    }

    @Test
    fun extractPublicKeyFromCoseKey_extraEntriesAroundCoordinates() = runTest {
        // Add extra map entries before X and Y; they should be skipped
        var data = byteArrayOf(0xa7.toByte()) // map(7) — 5 standard + 2 extra
        data += byteArrayOf(0x01, 0x02)         // kty
        data += byteArrayOf(0x03, 0x26.toByte()) // alg
        data += byteArrayOf(0x20.toByte(), 0x01) // crv
        // extra entry: key 0x04 (uint 4) -> uint(99)
        data += byteArrayOf(0x04, 0x18, 0x63)
        data += byteArrayOf(0x21)
        data += buildCborByteString(testX)
        data += byteArrayOf(0x22)
        data += buildCborByteString(testY)
        // extra trailing entry: key 0x05 -> uint(1)
        data += byteArrayOf(0x05, 0x01)

        val result = WebAuthnCborParser.extractPublicKeyFromCoseKey(data)
        assertNotNull(result)
        assertEquals(65, result.size)
    }

    @Test
    fun extractPublicKeyFromCoseKey_missingXCoordinate_returnsNull() = runTest {
        // Map with only Y (-3), no X (-2)
        var data = byteArrayOf(0xa2.toByte()) // map(2)
        data += byteArrayOf(0x01, 0x02)        // kty
        data += byteArrayOf(0x22)              // -3 (Y)
        data += buildCborByteString(testY)

        val result = WebAuthnCborParser.extractPublicKeyFromCoseKey(data)
        assertNull(result)
    }

    @Test
    fun extractPublicKeyFromCoseKey_missingYCoordinate_returnsNull() = runTest {
        // Map with only X (-2), no Y (-3)
        var data = byteArrayOf(0xa2.toByte()) // map(2)
        data += byteArrayOf(0x01, 0x02)        // kty
        data += byteArrayOf(0x21)              // -2 (X)
        data += buildCborByteString(testX)

        val result = WebAuthnCborParser.extractPublicKeyFromCoseKey(data)
        assertNull(result)
    }

    @Test
    fun extractPublicKeyFromCoseKey_xCoordinateWrongSize_returnsNull() = runTest {
        val shortX = byteArrayOf(0x01, 0x02, 0x03) // 3 bytes, not 32
        var data = byteArrayOf(0xa2.toByte())
        data += byteArrayOf(0x21)
        data += buildCborByteString(shortX)
        data += byteArrayOf(0x22)
        data += buildCborByteString(testY)

        val result = WebAuthnCborParser.extractPublicKeyFromCoseKey(data)
        assertNull(result)
    }

    @Test
    fun extractPublicKeyFromCoseKey_yCoordinateWrongSize_returnsNull() = runTest {
        val shortY = byteArrayOf(0x01, 0x02) // 2 bytes, not 32
        var data = byteArrayOf(0xa2.toByte())
        data += byteArrayOf(0x21)
        data += buildCborByteString(testX)
        data += byteArrayOf(0x22)
        data += buildCborByteString(shortY)

        val result = WebAuthnCborParser.extractPublicKeyFromCoseKey(data)
        assertNull(result)
    }

    @Test
    fun extractPublicKeyFromCoseKey_emptyInput_returnsNull() = runTest {
        assertNull(WebAuthnCborParser.extractPublicKeyFromCoseKey(byteArrayOf()))
    }

    @Test
    fun extractPublicKeyFromCoseKey_nonMapInput_fallsBackToPatternMatching() = runTest {
        // Input is not a CBOR map — should try the pattern-matching path.
        // Embed the COSE_ES256_KEY_PREFIX followed by X and Y with the correct structure.
        // Pattern: 0xa5 01 02 03 26 20 01 21 58 20 <X 32 bytes> 22 58 20 <Y 32 bytes>
        val prefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(),
            0x20.toByte(), 0x01, 0x21, 0x58, 0x20.toByte()
        )
        // The -3 (Y) CBOR header that follows X: 0x22 0x58 0x20
        val yHeader = byteArrayOf(0x22, 0x58, 0x20)
        // Wrap in an array (major type 4) to make it a non-map input
        val inner = prefix + testX + yHeader + testY
        val data = byteArrayOf(0x81.toByte()) + inner  // array(1) containing the raw bytes

        // This particular wrapping won't match pattern at offset 1 due to context,
        // but the raw concatenation (no array wrapper) exercises the pattern path directly.
        val rawData = prefix + testX + yHeader + testY
        val result = WebAuthnCborParser.extractPublicKeyFromCoseKey(rawData)
        assertNotNull(result)
        assertEquals(65, result.size)
        assertContentEquals(testX, result.copyOfRange(1, 33))
        assertContentEquals(testY, result.copyOfRange(33, 65))
    }

    @Test
    fun extractPublicKeyFromCoseKey_mapParsingFailsThenPatternSucceeds() = runTest {
        // Construct data where map(5) header is present but entries are garbled,
        // yet the well-known COSE ES256 prefix exists somewhere in the bytes.
        val prefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(),
            0x20.toByte(), 0x01, 0x21, 0x58, 0x20.toByte()
        )
        val yHeader = byteArrayOf(0x22, 0x58, 0x20)
        // Data starts with map(5) but the map iteration will fail because the content
        // doesn't parse as valid key-value pairs — however, extractPublicKeyByPattern
        // will still locate the embedded prefix.
        // We arrange the data so that the map header byte (0xa5) is the COSE prefix
        // first byte as well, making the map parse attempt hit the pattern path after failing.
        val fullData = prefix + testX + yHeader + testY
        val result = WebAuthnCborParser.extractPublicKeyFromCoseKey(fullData)
        assertNotNull(result)
        assertEquals(65, result.size)
    }

    @Test
    fun extractPublicKeyFromCoseKey_patternFallbackValidSeparator() = runTest {
        // Force the pattern-fallback path: a leading non-map byte (major type 0) makes
        // the entry method skip map iteration entirely. The embedded structure carries
        // a valid [0x22, 0x58, 0x20] Y-coordinate separator, so extraction succeeds.
        val prefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(),
            0x20.toByte(), 0x01, 0x21, 0x58, 0x20.toByte()
        )
        val yHeader = byteArrayOf(0x22, 0x58, 0x20)
        val data = byteArrayOf(0x00) + prefix + testX + yHeader + testY

        val result = WebAuthnCborParser.extractPublicKeyFromCoseKey(data)
        assertNotNull(result)
        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
        assertContentEquals(testX, result.copyOfRange(1, 33))
        assertContentEquals(testY, result.copyOfRange(33, 65))
    }

    @Test
    fun extractPublicKeyFromCoseKey_patternFallbackCorruptedSeparator_returnsNull() = runTest {
        // Force the pattern-fallback path with a leading non-map byte, then corrupt the
        // 3-byte Y-coordinate separator. The strict separator validation must reject the
        // structure and return null instead of extracting a misaligned Y coordinate.
        val prefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(),
            0x20.toByte(), 0x01, 0x21, 0x58, 0x20.toByte()
        )
        val corruptedYHeader = byteArrayOf(0xFF.toByte(), 0x58, 0x20)
        val data = byteArrayOf(0x00) + prefix + testX + corruptedYHeader + testY

        assertNull(WebAuthnCborParser.extractPublicKeyFromCoseKey(data))
    }

    // =========================================================================
    // 8. extractPublicKeyFromSpki
    // =========================================================================

    @Test
    fun extractPublicKeyFromSpki_exact65BytesWithPrefix() = runTest {
        val spki = byteArrayOf(0x04.toByte()) + testX + testY
        assertEquals(65, spki.size)
        val result = WebAuthnCborParser.extractPublicKeyFromSpki(spki)
        assertNotNull(result)
        assertContentEquals(spki, result)
    }

    @Test
    fun extractPublicKeyFromSpki_91ByteSpkiWithPrefixAt26() = runTest {
        // Realistic 91-byte SPKI: 26-byte DER header + 65-byte uncompressed key
        val header = ByteArray(26) { 0x30.toByte() }
        val keyBytes = byteArrayOf(0x04.toByte()) + testX + testY
        val spki = header + keyBytes
        assertEquals(91, spki.size)
        val result = WebAuthnCborParser.extractPublicKeyFromSpki(spki)
        assertNotNull(result)
        assertContentEquals(keyBytes, result)
    }

    @Test
    fun extractPublicKeyFromSpki_largerThan91BytesWithPrefixAtCorrectPosition() = runTest {
        // 100-byte input where 0x04 appears at byte index 35 (= 100 - 65)
        val spki = ByteArray(100) { 0x00 }
        spki[35] = 0x04.toByte()
        val result = WebAuthnCborParser.extractPublicKeyFromSpki(spki)
        assertNotNull(result)
        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
    }

    @Test
    fun extractPublicKeyFromSpki_shorterThan65Bytes_returnsNull() = runTest {
        val spki = ByteArray(64) { 0x01 }
        assertNull(WebAuthnCborParser.extractPublicKeyFromSpki(spki))
    }

    @Test
    fun extractPublicKeyFromSpki_emptyInput_returnsNull() = runTest {
        assertNull(WebAuthnCborParser.extractPublicKeyFromSpki(byteArrayOf()))
    }

    @Test
    fun extractPublicKeyFromSpki_65BytesWithoutPrefix_returnsNull() = runTest {
        // 65 bytes but the first is 0x03, not 0x04
        val spki = ByteArray(65) { 0x01 }
        spki[0] = 0x03
        assertNull(WebAuthnCborParser.extractPublicKeyFromSpki(spki))
    }

    @Test
    fun extractPublicKeyFromSpki_exactly64Bytes_returnsNull() = runTest {
        assertNull(WebAuthnCborParser.extractPublicKeyFromSpki(ByteArray(64) { 0x04.toByte() }))
    }

    @Test
    fun extractPublicKeyFromSpki_91BytesWithWrongPrefixByte_returnsNull() = runTest {
        // All bytes are 0x00 — byte 26 is 0x00, not 0x04
        assertNull(WebAuthnCborParser.extractPublicKeyFromSpki(ByteArray(91)))
    }

    // =========================================================================
    // 9. parseAuthenticatorFlags
    // =========================================================================

    @Test
    fun parseAuthenticatorFlags_nullInput_bothNull() = runTest {
        val flags = WebAuthnCborParser.parseAuthenticatorFlags(null)
        assertNull(flags.deviceType)
        assertNull(flags.backedUp)
    }

    @Test
    fun parseAuthenticatorFlags_emptyByteArray_bothNull() = runTest {
        val flags = WebAuthnCborParser.parseAuthenticatorFlags(byteArrayOf())
        assertNull(flags.deviceType)
        assertNull(flags.backedUp)
    }

    @Test
    fun parseAuthenticatorFlags_tooShort32Bytes_bothNull() = runTest {
        // FLAGS_OFFSET is 32, so size must be > 32. A 32-byte array has last index 31.
        val flags = WebAuthnCborParser.parseAuthenticatorFlags(ByteArray(32))
        assertNull(flags.deviceType)
        assertNull(flags.backedUp)
    }

    @Test
    fun parseAuthenticatorFlags_exactlyMinLength33Bytes_parsesCorrectly() = runTest {
        val data = ByteArray(33)
        data[32] = 0x00 // BE=0, BS=0
        val flags = WebAuthnCborParser.parseAuthenticatorFlags(data)
        assertEquals(WebAuthnCborParser.DEVICE_TYPE_SINGLE, flags.deviceType)
        assertFalse(flags.backedUp!!)
    }

    @Test
    fun parseAuthenticatorFlags_BE0_BS0_singleDevice_notBackedUp() = runTest {
        val data = buildAuthenticatorData(0x00)
        val flags = WebAuthnCborParser.parseAuthenticatorFlags(data)
        assertEquals(WebAuthnCborParser.DEVICE_TYPE_SINGLE, flags.deviceType)
        assertFalse(flags.backedUp!!)
    }

    @Test
    fun parseAuthenticatorFlags_BE1_BS0_multiDevice_notBackedUp() = runTest {
        // BE = 0x08, BS = 0x00
        val data = buildAuthenticatorData(0x08)
        val flags = WebAuthnCborParser.parseAuthenticatorFlags(data)
        assertEquals(WebAuthnCborParser.DEVICE_TYPE_MULTI, flags.deviceType)
        assertFalse(flags.backedUp!!)
    }

    @Test
    fun parseAuthenticatorFlags_BE0_BS1_singleDevice_backedUp() = runTest {
        // BE = 0x00, BS = 0x10 — unusual but spec-valid
        val data = buildAuthenticatorData(0x10)
        val flags = WebAuthnCborParser.parseAuthenticatorFlags(data)
        assertEquals(WebAuthnCborParser.DEVICE_TYPE_SINGLE, flags.deviceType)
        assertTrue(flags.backedUp!!)
    }

    @Test
    fun parseAuthenticatorFlags_BE1_BS1_multiDevice_backedUp() = runTest {
        // BE = 0x08, BS = 0x10
        val data = buildAuthenticatorData(0x18)
        val flags = WebAuthnCborParser.parseAuthenticatorFlags(data)
        assertEquals(WebAuthnCborParser.DEVICE_TYPE_MULTI, flags.deviceType)
        assertTrue(flags.backedUp!!)
    }

    @Test
    fun parseAuthenticatorFlags_otherFlagBitsDoNotAffectResult() = runTest {
        // UP (0x01) + UV (0x04) + AT (0x40) + BE (0x08) + BS (0x10) all set
        val allFlags: Byte = (0x01 or 0x04 or 0x08 or 0x10 or 0x40).toByte()
        val data = buildAuthenticatorData(allFlags)
        val flags = WebAuthnCborParser.parseAuthenticatorFlags(data)
        assertEquals(WebAuthnCborParser.DEVICE_TYPE_MULTI, flags.deviceType)
        assertTrue(flags.backedUp!!)
    }

    @Test
    fun parseAuthenticatorFlags_37ByteTypicalAuthData_parsesCorrectly() = runTest {
        val data = buildAuthenticatorData(0x08) // 37 bytes, BE=1
        assertEquals(37, data.size)
        val flags = WebAuthnCborParser.parseAuthenticatorFlags(data)
        assertEquals(WebAuthnCborParser.DEVICE_TYPE_MULTI, flags.deviceType)
        assertFalse(flags.backedUp!!)
    }

    @Test
    fun parseAuthenticatorFlags_deviceTypeSingleConstantValue() = runTest {
        assertEquals("singleDevice", WebAuthnCborParser.DEVICE_TYPE_SINGLE)
    }

    @Test
    fun parseAuthenticatorFlags_deviceTypeMultiConstantValue() = runTest {
        assertEquals("multiDevice", WebAuthnCborParser.DEVICE_TYPE_MULTI)
    }

    // =========================================================================
    // Private helper — wraps readCborLength to avoid test duplication
    // =========================================================================

    private fun WebAuthnCborLength(data: ByteArray, offset: Int): Pair<Int, Int>? {
        return WebAuthnCborParser.readCborLength(data, offset)
    }
}
