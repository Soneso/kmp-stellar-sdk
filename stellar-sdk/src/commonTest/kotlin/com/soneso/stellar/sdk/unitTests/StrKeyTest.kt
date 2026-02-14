package com.soneso.stellar.sdk.unitTests

import com.soneso.stellar.sdk.*
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

    // NEW TESTS FROM FLUTTER SDK

    // Test vectors for valid public keys (from Flutter SDK)
    @Test
    fun testValidPublicKeys() {
        val validKeys = listOf(
            "GBBM6BKZPEHWYO3E3YKREDPQXMS4VK35YLNU7NFBRI26RAN7GI5POFBB",
            "GB7KKHHVYLDIZEKYJPAJUOTBE5E3NJAXPSDZK7O6O44WR3EBRO5HRPVT",
            "GD6WVYRVID442Y4JVWFWKWCZKB45UGHJAABBJRS22TUSTWGJYXIUR7N2",
            "GBCG42WTVWPO4Q6OZCYI3D6ZSTFSJIXIS6INCIUF23L6VN3ADE4337AP",
            "GDFX463YPLCO2EY7NGFMI7SXWWDQAMASGYZXCG2LATOF3PP5NQIUKBPT",
            "GBXEODUMM3SJ3QSX2VYUWFU3NRP7BQRC2ERWS7E2LZXDJXL2N66ZQ5PT",
            "GAJHORKJKDDEPYCD6URDFODV7CVLJ5AAOJKR6PG2VQOLWFQOF3X7XLOG",
            "GACXQEAXYBEZLBMQ2XETOBRO4P66FZAJENDHOQRYPUIXZIIXLKMZEXBJ",
            "GDD3XRXU3G4DXHVRUDH7LJM4CD4PDZTVP4QHOO4Q6DELKXUATR657OZV",
            "GDTYVCTAUQVPKEDZIBWEJGKBQHB4UGGXI2SXXUEW7LXMD4B7MK37CWLJ"
        )

        for (key in validKeys) {
            assertTrue(StrKey.isValidEd25519PublicKey(key), "Expected $key to be valid")
        }
    }

    // Test vectors for invalid public keys (from Flutter SDK)
    @Test
    fun testInvalidPublicKeys() {
        val invalidKeys = listOf(
            "GBPXX0A5N4JYPESHAADMQKBPWZWQDQ64ZV6ZL2S3LAGW4SY7NTCMWIVL", // Invalid encoded
            "GCFZB6L25D26RQFDWSSBDEYQ32JHLRMTT44ZYE3DZQUTYOL7WY43PLBG++", // Invalid chars
            "GADE5QJ2TY7S5ZB65Q43DFGWYWCPHIYDJ2326KZGAGBN7AE5UY6JVDRRA", // Invalid encoded
            "GB6OWYST45X57HCJY5XWOHDEBULB6XUROWPIKW77L5DSNANBEQGUPADT2", // Invalid encoded
            "GB6OWYST45X57HCJY5XWOHDEBULB6XUROWPIKW77L5DSNANBEQGUPADT2T", // Too long
            "GDXIIZTKTLVYCBHURXL2UPMTYXOVNI7BRAEFQCP6EZCY4JLKY4VKFNLT", // Invalid checksum
            "SAB5556L5AN5KSR5WF7UOEFDCIODEWEO7H2UR4S5R62DFTQOGLKOVZDY", // Secret seed, not account
            "gWRYUerEKuz53tstxEuR3NCkiQDcV4wzFHmvLnZmj7PUqxW2wt", // Invalid format
            "test", // Invalid
            "g4VPBPrHZkfE8CsjuG2S4yBQNd455UWmk" // Old network key
        )

        for (key in invalidKeys) {
            assertFalse(StrKey.isValidEd25519PublicKey(key), "Expected $key to be invalid")
        }
    }

    // Test vectors for valid secret seeds (from Flutter SDK)
    @Test
    fun testValidSecretSeeds() {
        val validSeeds = listOf(
            "SAB5556L5AN5KSR5WF7UOEFDCIODEWEO7H2UR4S5R62DFTQOGLKOVZDY",
            "SCZTUEKSEH2VYZQC6VLOTOM4ZDLMAGV4LUMH4AASZ4ORF27V2X64F2S2",
            "SCGNLQKTZ4XCDUGVIADRVOD4DEVNYZ5A7PGLIIZQGH7QEHK6DYODTFEH",
            "SDH6R7PMU4WIUEXSM66LFE4JCUHGYRTLTOXVUV5GUEPITQEO3INRLHER",
            "SC2RDTRNSHXJNCWEUVO7VGUSPNRAWFCQDPP6BGN4JFMWDSEZBRAPANYW",
            "SCEMFYOSFZ5MUXDKTLZ2GC5RTOJO6FGTAJCF3CCPZXSLXA2GX6QUYOA7"
        )

        for (seed in validSeeds) {
            assertTrue(StrKey.isValidEd25519SecretSeed(seed.toCharArray()), "Expected $seed to be valid")
        }
    }

    // Test vectors for invalid secret seeds (from Flutter SDK)
    @Test
    fun testInvalidSecretSeeds() {
        val invalidSeeds = listOf(
            "GBBM6BKZPEHWYO3E3YKREDPQXMS4VK35YLNU7NFBRI26RAN7GI5POFBB", // Account ID, not seed
            "SAB5556L5AN5KSR5WF7UOEFDCIODEWEO7H2UR4S5R62DFTQOGLKOVZDYT", // Too long
            "SAFGAMN5Z6IHVI3IVEPIILS7ITZDYSCEPLN4FN5Z3IY63DRH4CIYEV", // Too short
            "SAFGAMN5Z6IHVI3IVEPIILS7ITZDYSCEPLN4FN5Z3IY63DRH4CIYEVIT", // Invalid checksum
            "test" // Invalid
        )

        for (seed in invalidSeeds) {
            assertFalse(StrKey.isValidEd25519SecretSeed(seed.toCharArray()), "Expected $seed to be invalid")
        }
    }

    // Test decode with wrong version byte (from Flutter SDK)
    @Test
    fun testDecodeWithWrongVersionByte() {
        // Throws an error when the version byte is wrong
        assertFailsWith<IllegalArgumentException> {
            // Try to decode a secret seed as account ID
            StrKey.decodeEd25519SecretSeed("GBPXXOA5N4JYPESHAADMQKBPWZWQDQ64ZV6ZL2S3LAGW4SY7NTCMWIVL".toCharArray())
        }

        assertFailsWith<IllegalArgumentException> {
            // Try to decode an account ID as secret seed
            StrKey.decodeEd25519PublicKey("SBGWKM3CD4IL47QN6X54N6Y33T3JDNVI6AIJ6CD5IM47HG3IG4O36XCU")
        }
    }

    // Test decode with invalid encoded strings (from Flutter SDK)
    @Test
    fun testDecodeInvalidEncodedStrings() {
        // Invalid account ID
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("GBPXX0A5N4JYPESHAADMQKBPWZWQDQ64ZV6ZL2S3LAGW4SY7NTCMWIVL")
        }

        // Invalid account ID with special chars
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("GCFZB6L25D26RQFDWSSBDEYQ32JHLRMTT44ZYE3DZQUTYOL7WY43PLBG++")
        }

        // Invalid account ID - too long
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("GB6OWYST45X57HCJY5XWOHDEBULB6XUROWPIKW77L5DSNANBEQGUPADT2T")
        }

        // Invalid secret seeds with various issues
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519SecretSeed("SB7OJNF5727F3RJUG5ASQJ3LUM44ELLNKW35ZZQDHMVUUQNGYW".toCharArray())
        }

        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519SecretSeed("SB7OJNF5727F3RJUG5ASQJ3LUM44ELLNKW35ZZQDHMVUUQNGYWMEGB2W2".toCharArray())
        }

        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519SecretSeed("SB7OJNF5727F3RJUG5ASQJ3LUM44ELLNKW35ZZQDHMVUUQNGYWMEGB2W2T".toCharArray())
        }

        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519SecretSeed("SCMB30FQCIQAWZ4WQTS6SVK37LGMAFJGXOZIHTH2PY6EXLP37G46H6DT".toCharArray())
        }

        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519SecretSeed("SAYC2LQ322EEHZYWNSKBEW6N66IRTDREEBUXXU5HPVZGMAXKLIZNM45H++".toCharArray())
        }
    }

    // Test decode with wrong checksum (from Flutter SDK)
    @Test
    fun testDecodeWrongChecksum() {
        // Invalid account ID checksum
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("GBPXXOA5N4JYPESHAADMQKBPWZWQDQ64ZV6ZL2S3LAGW4SY7NTCMWIVT")
        }

        // Invalid secret seed checksum
        assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519SecretSeed("SBGWKM3CD4IL47QN6X54N6Y33T3JDNVI6AIJ6CD5IM47HG3IG4O36XCX".toCharArray())
        }
    }

    // Test encode with proper prefix (from Flutter SDK)
    @Test
    fun testEncodePrefixes() {
        // Create test data
        val testData = ByteArray(32) { it.toByte() }

        // Account IDs should start with G
        val accountId = StrKey.encodeEd25519PublicKey(testData)
        assertTrue(accountId.startsWith("G"))

        // Secret seeds should start with S
        val secretSeed = StrKey.encodeEd25519SecretSeed(testData)
        assertTrue(secretSeed.concatToString().startsWith("S"))

        // Pre-auth TX should start with T
        val preAuthTx = StrKey.encodePreAuthTx(testData)
        assertTrue(preAuthTx.startsWith("T"))

        // SHA256 hash should start with X
        val sha256Hash = StrKey.encodeSha256Hash(testData)
        assertTrue(sha256Hash.startsWith("X"))
    }

    // Test muxed accounts (from Flutter SDK)
    @Test
    fun testMuxedAccounts() {
        val muxedAddress = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLK"
        val rawMuxedKey = hexToBytes("3f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a8000000000000000")

        // Encodes & decodes M... addresses correctly
        assertEquals(muxedAddress, StrKey.encodeMed25519PublicKey(rawMuxedKey))
        assertTrue(rawMuxedKey.contentEquals(StrKey.decodeMed25519PublicKey(muxedAddress)))

        // Validation
        assertTrue(StrKey.isValidMed25519PublicKey(muxedAddress))
    }

    // Test contracts (from Flutter SDK)
    @Test
    fun testContractsFromFlutterSDK() {
        val contractId = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"
        val asHex = "363eaa3867841fbad0f4ed88c779e4fe66e56a2470dc98c0ec9c073d05c7b103"

        val decoded = StrKey.decodeContract(contractId)
        assertEquals(asHex, bytesToHex(decoded))
        assertEquals(contractId, StrKey.encodeContract(hexToBytes(asHex)))

        assertTrue(StrKey.isValidContract(contractId))
        assertFalse(StrKey.isValidContract("GA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"))
    }

    // Test liquidity pools (from Flutter SDK)
    @Test
    fun testLiquidityPools() {
        val liquidityPoolId = "LA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUPJN"
        val asHex = "3f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a"

        val decoded = StrKey.decodeLiquidityPool(liquidityPoolId)
        assertEquals(asHex, bytesToHex(decoded))
        assertEquals(liquidityPoolId, StrKey.encodeLiquidityPool(hexToBytes(asHex)))

        assertTrue(StrKey.isValidLiquidityPool(liquidityPoolId))
        assertFalse(StrKey.isValidLiquidityPool("LB7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUPJN"))
    }

    // Test claimable balances (from Flutter SDK)
    @Test
    fun testClaimableBalances() {
        val claimableBalanceId = "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU"
        val asHex = "003f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a"

        val decoded = StrKey.decodeClaimableBalance(claimableBalanceId)
        assertEquals(asHex, bytesToHex(decoded))
        assertEquals(claimableBalanceId, StrKey.encodeClaimableBalance(hexToBytes(asHex)))

        assertTrue(StrKey.isValidClaimableBalance(claimableBalanceId))
        assertFalse(StrKey.isValidClaimableBalance("BBAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU"))

        // Test with 32-byte input (hash only) - SDK should automatically prepend V0 discriminant (0x00)
        val hashOnly = "3f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a"
        val encoded32Byte = StrKey.encodeClaimableBalance(hexToBytes(hashOnly))
        assertEquals(claimableBalanceId, encoded32Byte)

        // Test with 33-byte input (type + hash) - SDK should use as-is
        val fullBytes = "00" + hashOnly
        val encoded33Byte = StrKey.encodeClaimableBalance(hexToBytes(fullBytes))
        assertEquals(claimableBalanceId, encoded33Byte)

        // Both 32-byte and 33-byte inputs should produce the same result
        assertEquals(encoded32Byte, encoded33Byte)
    }

    @Test
    fun testEncodeClaimableBalanceInvalidLength() {
        // Test with invalid input sizes (not 32 or 33 bytes)
        val tooShort = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeClaimableBalance(tooShort)
        }

        val tooLong = ByteArray(34) { it.toByte() }
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeClaimableBalance(tooLong)
        }

        val empty = byteArrayOf()
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeClaimableBalance(empty)
        }

        val wrongSize = ByteArray(31) { it.toByte() }
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeClaimableBalance(wrongSize)
        }
    }

    // Test invalid str keys (from Flutter SDK - comprehensive edge cases)
    @Test
    fun testInvalidStrKeys() {
        // The unused trailing bit must be zero in the encoding of the last three
        // bytes (24 bits) as five base-32 symbols (25 bits)
        var strKey = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUR"
        assertFalse(StrKey.isValidMed25519PublicKey(strKey))

        // Invalid length (congruent to 1 mod 8)
        strKey = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZA"
        assertFalse(StrKey.isValidEd25519PublicKey(strKey))

        // Invalid algorithm (low 3 bits of version byte are 7)
        strKey = "G47QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVP2I"
        assertFalse(StrKey.isValidEd25519PublicKey(strKey))

        // Invalid length (congruent to 6 mod 8)
        strKey = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLKA"
        assertFalse(StrKey.isValidMed25519PublicKey(strKey))

        // Invalid algorithm (low 3 bits of version byte are 7)
        strKey = "M47QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUQ"
        assertFalse(StrKey.isValidMed25519PublicKey(strKey))

        // Padding bytes are not allowed
        strKey = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUK==="
        assertFalse(StrKey.isValidMed25519PublicKey(strKey))

        // Invalid checksum
        strKey = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUO"
        assertFalse(StrKey.isValidMed25519PublicKey(strKey))

        // Trailing bits should be zeroes
        strKey = "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TV"
        assertFalse(StrKey.isValidClaimableBalance(strKey))

        // Invalid length (Ed25519 should be 32 bytes, not 5)
        strKey = "GAAAAAAAACGC6"
        assertFalse(StrKey.isValidEd25519PublicKey(strKey))

        // Invalid length (base-32 decoding should yield 35 bytes, not 36)
        strKey = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUACUSI"
        assertFalse(StrKey.isValidEd25519PublicKey(strKey))

        // Invalid length (base-32 decoding should yield 43 bytes, not 44)
        strKey = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAAV75I"
        assertFalse(StrKey.isValidMed25519PublicKey(strKey))
    }

    // Test pre-auth transaction hashes
    @Test
    fun testPreAuthTxEncodeDecode() {
        val testData = ByteArray(32) { it.toByte() }

        val encoded = StrKey.encodePreAuthTx(testData)
        assertTrue(encoded.startsWith("T"))

        val decoded = StrKey.decodePreAuthTx(encoded)
        assertTrue(testData.contentEquals(decoded))

        assertTrue(StrKey.isValidPreAuthTx(encoded))
    }

    // Test SHA256 hash encoding/decoding
    @Test
    fun testSha256HashEncodeDecode() {
        val testData = ByteArray(32) { it.toByte() }

        val encoded = StrKey.encodeSha256Hash(testData)
        assertTrue(encoded.startsWith("X"))

        val decoded = StrKey.decodeSha256Hash(encoded)
        assertTrue(testData.contentEquals(decoded))

        assertTrue(StrKey.isValidSha256Hash(encoded))
    }

    // Test signed payload encoding/decoding
    @Test
    fun testSignedPayloadEncodeDecode() {
        // Test with minimum size (40 bytes: 32 + 4 + 4)
        val minPayload = ByteArray(40) { it.toByte() }
        val encodedMin = StrKey.encodeSignedPayload(minPayload)
        assertTrue(encodedMin.startsWith("P"))
        val decodedMin = StrKey.decodeSignedPayload(encodedMin)
        assertTrue(minPayload.contentEquals(decodedMin))

        // Test with maximum size (100 bytes: 32 + 4 + 64)
        val maxPayload = ByteArray(100) { it.toByte() }
        val encodedMax = StrKey.encodeSignedPayload(maxPayload)
        assertTrue(encodedMax.startsWith("P"))
        val decodedMax = StrKey.decodeSignedPayload(encodedMax)
        assertTrue(maxPayload.contentEquals(decodedMax))

        // Test validation
        assertTrue(StrKey.isValidSignedPayload(encodedMin))
        assertTrue(StrKey.isValidSignedPayload(encodedMax))

        // Test invalid size (too small)
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeSignedPayload(ByteArray(39))
        }

        // Test invalid size (too large)
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeSignedPayload(ByteArray(101))
        }
    }

    // Test round-trip for all key types
    @Test
    fun testRoundTripAllTypes() {
        val data32 = ByteArray(32) { i -> (i * 3).toByte() }
        val data33 = ByteArray(33) { i -> (i * 3).toByte() }
        val data40 = ByteArray(40) { i -> (i * 3).toByte() }
        val data50 = ByteArray(50) { i -> (i * 3).toByte() }

        // Ed25519 Public Key (G)
        val publicKey = StrKey.encodeEd25519PublicKey(data32)
        assertTrue(data32.contentEquals(StrKey.decodeEd25519PublicKey(publicKey)))

        // Ed25519 Secret Seed (S)
        val secretSeed = StrKey.encodeEd25519SecretSeed(data32)
        assertTrue(data32.contentEquals(StrKey.decodeEd25519SecretSeed(secretSeed)))

        // Muxed Account (M)
        val muxedAccount = StrKey.encodeMed25519PublicKey(data40)
        assertTrue(data40.contentEquals(StrKey.decodeMed25519PublicKey(muxedAccount)))

        // Pre-auth TX (T)
        val preAuthTx = StrKey.encodePreAuthTx(data32)
        assertTrue(data32.contentEquals(StrKey.decodePreAuthTx(preAuthTx)))

        // SHA256 Hash (X)
        val sha256Hash = StrKey.encodeSha256Hash(data32)
        assertTrue(data32.contentEquals(StrKey.decodeSha256Hash(sha256Hash)))

        // Signed Payload (P)
        val signedPayload = StrKey.encodeSignedPayload(data50)
        assertTrue(data50.contentEquals(StrKey.decodeSignedPayload(signedPayload)))

        // Contract (C)
        val contract = StrKey.encodeContract(data32)
        assertTrue(data32.contentEquals(StrKey.decodeContract(contract)))

        // Liquidity Pool (L)
        val liquidityPool = StrKey.encodeLiquidityPool(data32)
        assertTrue(data32.contentEquals(StrKey.decodeLiquidityPool(liquidityPool)))

        // Claimable Balance (B)
        val claimableBalance = StrKey.encodeClaimableBalance(data33)
        assertTrue(data33.contentEquals(StrKey.decodeClaimableBalance(claimableBalance)))
    }

    // Helper functions for hex conversion
    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = hex.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { byte ->
            byte.toInt().and(0xFF).toString(16).padStart(2, '0')
        }
    }
}
