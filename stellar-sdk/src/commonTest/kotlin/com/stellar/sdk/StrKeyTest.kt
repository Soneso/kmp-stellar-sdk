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

    // Contract address tests

    @Test
    fun testEncodeDecodeContract() {
        // Test vector from Java SDK: CA2LVQXQLGPWHV2QO5ENVAGWM2TYICRMWXW4UXBPVKV26WLKU2V3UTH5
        val contractAddress = "CA2LVQXQLGPWHV2QO5ENVAGWM2TYICRMWXW4UXBPVKV26WLKU2V3UTH5"

        // Decode to get the actual bytes
        val contractHash = StrKey.decodeContract(contractAddress)
        assertEquals(32, contractHash.size)

        // Re-encode and verify it matches
        val encoded = StrKey.encodeContract(contractHash)
        assertEquals(contractAddress, encoded)

        // Decode again and verify bytes match
        val decoded = StrKey.decodeContract(encoded)
        assertTrue(contractHash.contentEquals(decoded))
    }

    @Test
    fun testEncodeDecodeContractAnotherVector() {
        // Another test vector from Java SDK: CADEDRPB3MIT2QWLK5DGAFR3JMCIZMTEFT6R4KUGW5ZZYCQKAMPR5WAJ
        val contractAddress = "CADEDRPB3MIT2QWLK5DGAFR3JMCIZMTEFT6R4KUGW5ZZYCQKAMPR5WAJ"

        // Decode to get the actual bytes
        val contractHash = StrKey.decodeContract(contractAddress)
        assertEquals(32, contractHash.size)

        // Re-encode and verify it matches
        val encoded = StrKey.encodeContract(contractHash)
        assertEquals(contractAddress, encoded)

        // Decode again and verify bytes match
        val decoded = StrKey.decodeContract(encoded)
        assertTrue(contractHash.contentEquals(decoded))
    }

    @Test
    fun testIsValidContract() {
        // Valid contract addresses
        assertTrue(StrKey.isValidContract("CA2LVQXQLGPWHV2QO5ENVAGWM2TYICRMWXW4UXBPVKV26WLKU2V3UTH5"))
        assertTrue(StrKey.isValidContract("CADEDRPB3MIT2QWLK5DGAFR3JMCIZMTEFT6R4KUGW5ZZYCQKAMPR5WAJ"))

        // Invalid inputs
        assertFalse(StrKey.isValidContract("INVALID"))
        assertFalse(StrKey.isValidContract(""))

        // Account ID should not be valid as contract
        assertFalse(StrKey.isValidContract("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"))

        // Seed should not be valid as contract
        assertFalse(StrKey.isValidContract("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"))
    }

    @Test
    fun testDecodeContractInvalidChecksum() {
        // Change last character to make checksum invalid
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeContract("CA2LVQXQLGPWHV2QO5ENVAGWM2TYICRMWXW4UXBPVKV26WLKU2V3UTH4")
        }
    }

    @Test
    fun testDecodeContractInvalidVersion() {
        // Try to decode an account ID as a contract
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeContract("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D")
        }

        // Try to decode a seed as a contract
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeContract("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        }
    }

    @Test
    fun testEncodeContractInvalidLength() {
        val tooShort = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeContract(tooShort)
        }

        val tooLong = ByteArray(33) { it.toByte() }
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeContract(tooLong)
        }

        val empty = byteArrayOf()
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeContract(empty)
        }
    }

    @Test
    fun testContractRoundTrip() {
        // Generate some test data (32 bytes)
        val testData = ByteArray(32) { i -> (i * 7).toByte() }

        // Encode to contract address
        val contractAddress = StrKey.encodeContract(testData)

        // Verify it starts with 'C'
        assertTrue(contractAddress.startsWith("C"))
        assertTrue(contractAddress.length > 10)

        // Decode back
        val decoded = StrKey.decodeContract(contractAddress)

        // Verify round-trip
        assertEquals(32, decoded.size)
        assertTrue(testData.contentEquals(decoded))
    }

    @Test
    fun testContractVsAccountIdDifferentEncoding() {
        // Same 32-byte data encoded as both contract and account ID should produce different results
        val testData = ByteArray(32) { 0x42 }

        val asContract = StrKey.encodeContract(testData)
        val asAccountId = StrKey.encodeEd25519PublicKey(testData)

        // They should be different
        assertNotEquals(asContract, asAccountId)

        // Contract starts with C, account with G
        assertTrue(asContract.startsWith("C"))
        assertTrue(asAccountId.startsWith("G"))

        // Each should decode only with its own method
        assertTrue(StrKey.isValidContract(asContract))
        assertFalse(StrKey.isValidContract(asAccountId))

        assertTrue(StrKey.isValidEd25519PublicKey(asAccountId))
        assertFalse(StrKey.isValidEd25519PublicKey(asContract))
    }

    @Test
    fun testDecodeContractTooShort() {
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeContract("CAAA")
        }
    }

    @Test
    fun testDecodeContractInvalidBase32() {
        // Invalid base32 character (0 and 1 are not in base32 alphabet)
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeContract("C000000000000000000000000000000000000000000000000000000")
        }
    }
}
