//
//  AppleWebAuthnProviderTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tests for [AppleWebAuthnProvider] constructor validation and
 * the internal [ByteArray.toNSData] / [NSData.toByteArray] extension round-trips.
 *
 * Round-trip tests verify that data is faithfully preserved through NSData conversion
 * and that copy semantics hold: mutating the source after conversion does not affect
 * the converted result.
 *
 * Note: register() and authenticate() are not tested here because they require
 * a live AuthenticationServices session (biometric prompt / system UI). Those
 * paths are covered by manual integration testing on device/simulator.
 */
class AppleWebAuthnProviderTest {

    // MARK: - Constructor Validation

    @Test
    fun testBlankRpIdThrows() {
        assertFailsWith<IllegalArgumentException> {
            AppleWebAuthnProvider(rpId = "", rpName = "My Wallet")
        }
    }

    @Test
    fun testBlankRpIdWhitespaceOnlyThrows() {
        assertFailsWith<IllegalArgumentException> {
            AppleWebAuthnProvider(rpId = "   ", rpName = "My Wallet")
        }
    }

    @Test
    fun testBlankRpNameThrows() {
        assertFailsWith<IllegalArgumentException> {
            AppleWebAuthnProvider(rpId = "example.com", rpName = "")
        }
    }

    @Test
    fun testBlankRpNameWhitespaceOnlyThrows() {
        assertFailsWith<IllegalArgumentException> {
            AppleWebAuthnProvider(rpId = "example.com", rpName = "   ")
        }
    }

    @Test
    fun testZeroTimeoutThrows() {
        assertFailsWith<IllegalArgumentException> {
            AppleWebAuthnProvider(rpId = "example.com", rpName = "My Wallet", timeout = 0L)
        }
    }

    @Test
    fun testNegativeTimeoutThrows() {
        assertFailsWith<IllegalArgumentException> {
            AppleWebAuthnProvider(rpId = "example.com", rpName = "My Wallet", timeout = -1L)
        }
    }

    @Test
    fun testValidConstructionWithDefaultTimeout() {
        val provider = AppleWebAuthnProvider(rpId = "example.com", rpName = "My Wallet")
        assertNotNull(provider)
    }

    @Test
    fun testValidConstructionWithExplicitTimeout() {
        val provider = AppleWebAuthnProvider(
            rpId = "stellar.example.com",
            rpName = "Stellar Smart Wallet",
            timeout = 30_000L
        )
        assertNotNull(provider)
    }

    @Test
    fun testValidConstructionViaFactoryMethod() {
        val provider = AppleWebAuthnProvider.create(
            rpId = "example.com",
            rpName = "My Wallet",
            timeout = OZConstants.WEBAUTHN_TIMEOUT_MS
        )
        assertNotNull(provider)
    }

    // MARK: - ByteArray <-> NSData Round-trips

    @Test
    fun testEmptyByteArrayRoundTrip() {
        val original = ByteArray(0)
        val nsData = original.toNSData()
        val result = nsData.toByteArray()
        assertEquals(0, result.size)
        assertContentEquals(original, result)
    }

    @Test
    fun testSingleByteRoundTrip() {
        val original = byteArrayOf(0x42)
        val result = original.toNSData().toByteArray()
        assertContentEquals(original, result)
    }

    @Test
    fun testThirtyTwoByteChallengeRoundTrip() {
        val original = ByteArray(32) { it.toByte() }
        val result = original.toNSData().toByteArray()
        assertContentEquals(original, result)
        assertEquals(32, result.size)
    }

    @Test
    fun testSixtyFiveBytePublicKeyRoundTrip() {
        // Uncompressed secp256r1 public key format: 0x04 || X (32 bytes) || Y (32 bytes)
        val original = ByteArray(65) { i ->
            if (i == 0) 0x04.toByte() else (i * 3 and 0xFF).toByte()
        }
        val result = original.toNSData().toByteArray()
        assertContentEquals(original, result)
        assertEquals(65, result.size)
        assertEquals(0x04.toByte(), result[0])
    }

    @Test
    fun testAllByteValuesRoundTrip() {
        // Ensure all possible byte values (0x00 through 0xFF) survive the conversion
        val original = ByteArray(256) { it.toByte() }
        val result = original.toNSData().toByteArray()
        assertContentEquals(original, result)
    }

    @Test
    fun testByteArrayToNSDataCopySemantics() {
        // Mutating the source byte array after conversion must not affect NSData
        val original = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val nsData = original.toNSData()
        original[0] = 0xFF.toByte()
        val result = nsData.toByteArray()
        // First byte should still be 0x01, not 0xFF
        assertEquals(0x01.toByte(), result[0])
    }

    @Test
    fun testNSDataToByteArrayCopySemantics() {
        // Mutating the returned ByteArray must not affect the source NSData
        val original = byteArrayOf(0x10, 0x20, 0x30)
        val nsData = original.toNSData()
        val result1 = nsData.toByteArray()
        result1[0] = 0xFF.toByte()
        // A second conversion from the same NSData must still yield the original value
        val result2 = nsData.toByteArray()
        assertEquals(0x10.toByte(), result2[0])
    }
}
