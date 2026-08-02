//
//  Base64UrlTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.Util
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Base64URL encode/decode utilities in [Util].
 *
 * Verifies RFC 4648 Section 5 compliance:
 * - URL-safe alphabet: `-` instead of `+`, `_` instead of `/`
 * - No padding `=` characters in output
 * - Correct round-trip for all input lengths (padding edge cases)
 */
class Base64UrlTest {

    // MARK: - Encoding Tests

    @Test
    fun testEncodeEmptyByteArray() {
        val result = Util.base64urlEncode(ByteArray(0))
        assertEquals("", result, "Empty byte array should encode to empty string")
    }

    @Test
    fun testEncodeSingleByte() {
        // 0x00 -> standard base64 "AA==" -> base64url "AA"
        val result = Util.base64urlEncode(byteArrayOf(0x00))
        assertEquals("AA", result)
    }

    @Test
    fun testEncodeTwoBytes() {
        // 2 bytes -> 3 base64url chars (would need 1 padding in standard base64)
        val result = Util.base64urlEncode(byteArrayOf(0x00, 0x01))
        assertEquals("AAE", result)
    }

    @Test
    fun testEncodeThreeBytes() {
        // 3 bytes -> 4 base64url chars (no padding needed)
        val result = Util.base64urlEncode(byteArrayOf(0x00, 0x01, 0x02))
        assertEquals("AAEC", result)
    }

    @Test
    fun testEncodeNoPaddingInOutput() {
        // Test multiple lengths to verify padding is never present
        for (len in 0..20) {
            val data = ByteArray(len) { it.toByte() }
            val encoded = Util.base64urlEncode(data)
            assertFalse(
                encoded.contains("="),
                "Base64URL output must not contain padding for length $len"
            )
        }
    }

    @Test
    fun testEncodeNoStandardBase64Chars() {
        // Test that + and / never appear in output
        for (len in 1..50) {
            val data = ByteArray(len) { (it * 7 + 0xAB).toByte() }
            val encoded = Util.base64urlEncode(data)
            assertFalse(encoded.contains("+"), "Base64URL must not contain '+'")
            assertFalse(encoded.contains("/"), "Base64URL must not contain '/'")
        }
    }

    @Test
    fun testEncodeUrlSafeCharacterSubstitution_dash() {
        // 0xfb, 0xef, 0xbe -> binary: 111110 111110 111110 111110
        // Standard base64 indices all 62 -> "++++" -> base64url "----"
        val data = byteArrayOf(0xfb.toByte(), 0xef.toByte(), 0xbe.toByte())
        val result = Util.base64urlEncode(data)
        assertEquals("----", result, "Index 62 should map to '-' in base64url")
    }

    @Test
    fun testEncodeUrlSafeCharacterSubstitution_underscore() {
        // 0xff, 0xff, 0xff -> binary: 111111 111111 111111 111111
        // Standard base64 indices all 63 -> "////" -> base64url "____"
        val data = byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        val result = Util.base64urlEncode(data)
        assertEquals("____", result, "Index 63 should map to '_' in base64url")
    }

    @Test
    fun testEncodeUrlSafeCharacterSubstitution_mixed() {
        // 0xfb, 0xff, 0xfe -> binary: 111110 111111 111111 111110
        // Standard base64 indices: 62, 63, 63, 62 -> "+//+" -> base64url "-__-"
        val data = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xfe.toByte())
        val result = Util.base64urlEncode(data)
        assertEquals("-__-", result, "Mixed + and / should be replaced with - and _")
    }

    // MARK: - Decoding Tests

    @Test
    fun testDecodeEmptyString() {
        val result = Util.base64urlDecode("")
        assertEquals(0, result.size, "Empty string should decode to empty byte array")
    }

    @Test
    fun testDecodeSingleChar() {
        // "AA" -> 0x00
        val result = Util.base64urlDecode("AA")
        assertEquals(1, result.size)
        assertEquals(0x00.toByte(), result[0])
    }

    @Test
    fun testDecodeWithDash() {
        // "----" -> indices 62,62,62,62 -> 0xfb, 0xef, 0xbe
        val result = Util.base64urlDecode("----")
        assertEquals(3, result.size)
        assertEquals(0xfb.toByte(), result[0])
        assertEquals(0xef.toByte(), result[1])
        assertEquals(0xbe.toByte(), result[2])
    }

    @Test
    fun testDecodeWithUnderscore() {
        // "____" -> indices 63,63,63,63 -> 0xff, 0xff, 0xff
        val result = Util.base64urlDecode("____")
        assertEquals(3, result.size)
        assertEquals(0xff.toByte(), result[0])
        assertEquals(0xff.toByte(), result[1])
        assertEquals(0xff.toByte(), result[2])
    }

    @Test
    fun testDecodeWithPaddingAccepted() {
        // base64url decoders should accept input with or without padding
        val withoutPadding = Util.base64urlDecode("AA")
        val withPadding = Util.base64urlDecode("AA==")
        assertTrue(
            withoutPadding.contentEquals(withPadding),
            "Decoding should work with or without padding"
        )
    }

    @Test
    fun testDecodeInvalidInputThrows() {
        // Invalid base64url characters should cause decode to throw
        assertFailsWith<IllegalArgumentException> { Util.base64urlDecode("!!!invalid!!!") }
    }

    // MARK: - Round-Trip Tests

    @Test
    fun testRoundTripEmpty() {
        val data = ByteArray(0)
        val encoded = Util.base64urlEncode(data)
        val decoded = Util.base64urlDecode(encoded)
        assertTrue(data.contentEquals(decoded), "Round-trip failed for empty array")
    }

    @Test
    fun testRoundTripSingleByte() {
        val data = byteArrayOf(0x42)
        val encoded = Util.base64urlEncode(data)
        val decoded = Util.base64urlDecode(encoded)
        assertTrue(data.contentEquals(decoded), "Round-trip failed for single byte")
    }

    @Test
    fun testRoundTripAllLengths() {
        // Test lengths 0..64 to cover all padding edge cases (mod 3 = 0, 1, 2)
        for (len in 0..64) {
            val data = ByteArray(len) { (it * 17 + 0x5A).toByte() }
            val encoded = Util.base64urlEncode(data)
            val decoded = Util.base64urlDecode(encoded)
            assertTrue(
                data.contentEquals(decoded),
                "Round-trip failed for length $len"
            )
        }
    }

    @Test
    fun testRoundTripAllByteValues() {
        // All 256 byte values in a single array
        val data = ByteArray(256) { it.toByte() }
        val encoded = Util.base64urlEncode(data)
        val decoded = Util.base64urlDecode(encoded)
        assertTrue(data.contentEquals(decoded), "Round-trip failed for all byte values")
    }

    @Test
    fun testRoundTripUrlSafeChars() {
        // Bytes that produce + and / in standard base64
        val data = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xfe.toByte())
        val encoded = Util.base64urlEncode(data)
        val decoded = Util.base64urlDecode(encoded)
        assertTrue(
            data.contentEquals(decoded),
            "Round-trip failed for URL-safe character data"
        )
    }

    // MARK: - Credential ID Compatibility Tests

    /**
     * Verifies that known credential ID bytes produce the expected base64url output.
     *
     * These test vectors verify cross-SDK compatibility for base64url encoding:
     * - [0xfb, 0xef, 0xbe] -> `"----"`
     * - [0xff, 0xff, 0xff] -> `"____"`
     * - [0xfb, 0xff, 0xfe] -> `"-__-"`
     */
    @Test
    fun testInteropWithTypeScriptSdk_dashChars() {
        val data = byteArrayOf(0xfb.toByte(), 0xef.toByte(), 0xbe.toByte())
        assertEquals("----", Util.base64urlEncode(data))
    }

    @Test
    fun testInteropWithTypeScriptSdk_underscoreChars() {
        val data = byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte())
        assertEquals("____", Util.base64urlEncode(data))
    }

    @Test
    fun testInteropWithTypeScriptSdk_mixedChars() {
        val data = byteArrayOf(0xfb.toByte(), 0xff.toByte(), 0xfe.toByte())
        assertEquals("-__-", Util.base64urlEncode(data))
    }

    /**
     * Verifies that base64url decode followed by hex conversion is lossless.
     *
     * Credential IDs are stored as base64url strings and converted to hex for
     * indexer lookups. This test verifies that encode/decode and hex conversion
     * round-trips without data loss.
     */
    @Test
    fun testCredentialIdBase64urlToHexInterop() {
        // A realistic 32-byte credential ID
        val credentialIdBytes = byteArrayOf(
            0x49.toByte(), 0xcc.toByte(), 0x7d.toByte(), 0x41.toByte(),
            0x9e.toByte(), 0x62.toByte(), 0x34.toByte(), 0xe3.toByte(),
            0xbf.toByte(), 0x9d.toByte(), 0x7a.toByte(), 0x6e.toByte(),
            0x8e.toByte(), 0x4b.toByte(), 0x7c.toByte(), 0x1f.toByte(),
            0x2a.toByte(), 0x3d.toByte(), 0x5e.toByte(), 0x6f.toByte(),
            0x8a.toByte(), 0x9b.toByte(), 0x0c.toByte(), 0x1d.toByte(),
            0x2e.toByte(), 0x3f.toByte(), 0x4a.toByte(), 0x5b.toByte(),
            0x6c.toByte(), 0x7d.toByte(), 0x8e.toByte(), 0x9f.toByte()
        )

        // Encode then decode should produce the same bytes
        val encoded = Util.base64urlEncode(credentialIdBytes)
        val decoded = Util.base64urlDecode(encoded)

        assertTrue(
            credentialIdBytes.contentEquals(decoded),
            "Credential ID round-trip must be lossless"
        )

        // The encoded form must not contain standard base64 characters
        assertFalse(encoded.contains("+"))
        assertFalse(encoded.contains("/"))
        assertFalse(encoded.contains("="))

        // Verify hex conversion is lossless
        val hexFromDecoded = decoded.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
        val expectedHex = "49cc7d419e6234e3bf9d7a6e8e4b7c1f2a3d5e6f8a9b0c1d2e3f4a5b6c7d8e9f"
        assertEquals(expectedHex, hexFromDecoded)
    }

    /**
     * Verifies a 20-byte credential ID (typical WebAuthn credential ID length).
     */
    @Test
    fun testCredentialId20Bytes() {
        val credentialIdBytes = byteArrayOf(
            0xa1.toByte(), 0xb2.toByte(), 0xc3.toByte(), 0xd4.toByte(),
            0xe5.toByte(), 0xf6.toByte(), 0xa7.toByte(), 0xb8.toByte(),
            0xc9.toByte(), 0xd0.toByte(), 0xe1.toByte(), 0xf2.toByte(),
            0xa3.toByte(), 0xb4.toByte(), 0xc5.toByte(), 0xd6.toByte(),
            0xe7.toByte(), 0xf8.toByte(), 0xa9.toByte(), 0xb0.toByte()
        )

        val encoded = Util.base64urlEncode(credentialIdBytes)
        val decoded = Util.base64urlDecode(encoded)

        assertTrue(
            credentialIdBytes.contentEquals(decoded),
            "20-byte credential ID round-trip must be lossless"
        )

        // Verify no padding, no standard base64 chars
        assertFalse(encoded.contains("="))
        assertFalse(encoded.contains("+"))
        assertFalse(encoded.contains("/"))
    }

    // MARK: - Padding Edge Case Tests

    /**
     * Tests all three padding scenarios (mod 3 = 0, 1, 2):
     * - 3 bytes: no padding needed
     * - 4 bytes: 2 padding chars would be needed in standard base64
     * - 5 bytes: 1 padding char would be needed in standard base64
     */
    @Test
    fun testPaddingEdgeCases() {
        // mod 3 = 0: 3 bytes -> 4 base64 chars, no padding
        val data3 = byteArrayOf(0x01, 0x02, 0x03)
        val enc3 = Util.base64urlEncode(data3)
        assertEquals(4, enc3.length, "3 bytes -> 4 base64url chars")
        assertFalse(enc3.contains("="))

        // mod 3 = 1: 4 bytes -> 6 base64 chars (would have == padding)
        val data4 = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val enc4 = Util.base64urlEncode(data4)
        assertEquals(6, enc4.length, "4 bytes -> 6 base64url chars (no padding)")
        assertFalse(enc4.contains("="))

        // mod 3 = 2: 5 bytes -> 7 base64 chars (would have = padding)
        val data5 = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        val enc5 = Util.base64urlEncode(data5)
        assertEquals(7, enc5.length, "5 bytes -> 7 base64url chars (no padding)")
        assertFalse(enc5.contains("="))

        // Verify all round-trip correctly
        assertTrue(data3.contentEquals(Util.base64urlDecode(enc3)))
        assertTrue(data4.contentEquals(Util.base64urlDecode(enc4)))
        assertTrue(data5.contentEquals(Util.base64urlDecode(enc5)))
    }
}
