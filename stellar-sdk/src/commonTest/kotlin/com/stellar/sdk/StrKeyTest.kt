package com.stellar.sdk

import kotlin.test.*

class StrKeyTest {

    @Test
    fun testEncodeDecodeEd25519PublicKey() {
        val accountId = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"

        // Decode to get the actual bytes
        val publicKey = StrKey.decodeEd25519PublicKey(accountId)
        assertEquals(32, publicKey.size)

        // Re-encode and verify it matches
        val encoded = StrKey.encodeEd25519PublicKey(publicKey)
        assertEquals(accountId, encoded)

        // Decode again and verify bytes match
        val decoded = StrKey.decodeEd25519PublicKey(encoded)
        assertTrue(publicKey.contentEquals(decoded))
    }

    @Test
    fun testEncodeDecodeEd25519SecretSeed() {
        val secretSeed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"

        // Decode to get the actual bytes
        val seed = StrKey.decodeEd25519SecretSeed(secretSeed.toCharArray())
        assertEquals(32, seed.size)

        // Re-encode and verify it matches
        val encoded = StrKey.encodeEd25519SecretSeed(seed)
        assertEquals(secretSeed, encoded.concatToString())

        // Decode again and verify bytes match
        val decoded = StrKey.decodeEd25519SecretSeed(encoded)
        assertTrue(seed.contentEquals(decoded))
    }

    @Test
    fun testIsValidEd25519PublicKey() {
        assertTrue(StrKey.isValidEd25519PublicKey("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"))
        assertFalse(StrKey.isValidEd25519PublicKey("INVALID"))
        assertFalse(StrKey.isValidEd25519PublicKey(""))
        assertFalse(StrKey.isValidEd25519PublicKey("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"))
    }

    @Test
    fun testIsValidEd25519SecretSeed() {
        assertTrue(StrKey.isValidEd25519SecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE".toCharArray()))
        assertFalse(StrKey.isValidEd25519SecretSeed("INVALID".toCharArray()))
        assertFalse(StrKey.isValidEd25519SecretSeed("".toCharArray()))
        assertFalse(StrKey.isValidEd25519SecretSeed("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D".toCharArray()))
    }

    @Test
    fun testDecodeInvalidChecksum() {
        // Change last character to make checksum invalid
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5X")
        }
    }

    @Test
    fun testDecodeInvalidVersion() {
        // Try to decode a seed as a public key
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        }
    }

    @Test
    fun testEncodeInvalidLength() {
        val tooShort = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeEd25519PublicKey(tooShort)
        }

        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeEd25519SecretSeed(tooShort)
        }
    }

    @Test
    fun testDecodeTooShort() {
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("GAAA")
        }
    }
}