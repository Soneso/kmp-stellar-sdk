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
        // The last character carries checksum bits, so changing it leaves a string of the length
        // and the version byte an account id has whose checksum no longer matches.
        val failure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5X")
        }
        assertEquals("Checksum invalid", failure.message)
    }

    @Test
    fun testDecodeInvalidVersion() {
        // A secret seed read as a public key. The version byte it carries names a type, and not
        // the one the caller asked for.
        val failure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
        }
        assertEquals("Version byte mismatch", failure.message)
    }

    @Test
    fun testEncodeInvalidLength() {
        val tooShort = byteArrayOf(1, 2, 3)
        val failure = assertFailsWith<IllegalArgumentException> {
            StrKey.encodeEd25519PublicKey(tooShort)
        }
        assertEquals("Public key must be 32 bytes, got 3", failure.message)

        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeEd25519SecretSeed(tooShort)
        }
    }

    @Test
    fun testDecodeTooShort() {
        val failure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("GAAA")
        }
        assertEquals("Invalid encoded length, expected 56 characters, got 4", failure.message)
    }

    // Contract address tests

    @Test
    fun testEncodeDecodeContract() {
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
        // The last character carries checksum bits, so changing it leaves a string of the length
        // and the version byte a contract address has whose checksum no longer matches.
        val failure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeContract("CA2LVQXQLGPWHV2QO5ENVAGWM2TYICRMWXW4UXBPVKV26WLKU2V3UTH4")
        }
        assertEquals("Checksum invalid", failure.message)
    }

    @Test
    fun testDecodeContractInvalidVersion() {
        // An account id and a secret seed read as a contract address. Each carries a version byte
        // that names a type, and neither names the one the caller asked for.
        val others = listOf(
            "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D",
            "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        )
        for (other in others) {
            val failure = assertFailsWith<IllegalArgumentException> {
                StrKey.decodeContract(other)
            }
            assertEquals("Version byte mismatch", failure.message, "rejecting $other")
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
        val failure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeContract("CAAA")
        }
        assertEquals("Invalid encoded length, expected 56 characters, got 4", failure.message)
    }

    @Test
    fun testDecodeContractInvalidBase32() {
        // '0' is not a base32 character. The string is as long as a contract address, so it gets
        // past the encoded-length check and the alphabet is what stops it.
        val candidate = "C" + "0".repeat(55)
        assertEquals(56, candidate.length)
        val failure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeContract(candidate)
        }
        assertEquals("Invalid base32 encoded string", failure.message)
    }

    // Test vectors for valid public keys
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

    /**
     * A strkey that is not one, together with the message the check that rejects it reports.
     * Naming the check keeps a vector from standing for a defect it never reaches: a string
     * whose length no strkey of its type has is stopped before anything is decoded, whatever
     * else is wrong with it.
     */
    private class RejectedVector(val strKey: String, val rejection: String)

    private fun assertRejected(
        vector: RejectedVector,
        decode: (String) -> ByteArray,
        isValid: (String) -> Boolean
    ) {
        assertFalse(isValid(vector.strKey), "Expected ${vector.strKey} to be invalid")
        val failure = assertFailsWith<IllegalArgumentException>(
            "Expected ${vector.strKey} to be rejected"
        ) {
            decode(vector.strKey)
        }
        assertEquals(
            vector.rejection, failure.message,
            "${vector.strKey}: the rejection must name the check that reports it"
        )
    }

    /**
     * Asserts that [candidate] is rejected with [rejection] by both [decode] and [isValid].
     * [label] names the rule the candidate breaks; the strkeys here are too long to read.
     */
    private fun assertRejectedByRule(
        label: String,
        candidate: String,
        rejection: String,
        decode: (String) -> ByteArray,
        isValid: (String) -> Boolean
    ) {
        val failure = assertFailsWith<IllegalArgumentException>("$label: must be rejected") {
            decode(candidate)
        }
        assertEquals(rejection, failure.message, "$label: the rejection must name the rule broken")
        assertFalse(isValid(candidate), "$label: isValid must agree with decode")
    }

    // Test vectors for invalid public keys
    @Test
    fun testInvalidPublicKeys() {
        val invalidKeys = listOf(
            // '0' is no base32 character.
            RejectedVector(
                "GBPXX0A5N4JYPESHAADMQKBPWZWQDQ64ZV6ZL2S3LAGW4SY7NTCMWIVL",
                "Invalid base32 encoded string"
            ),
            // Two characters too long, and the two are not base32 characters either.
            RejectedVector(
                "GCFZB6L25D26RQFDWSSBDEYQ32JHLRMTT44ZYE3DZQUTYOL7WY43PLBG++",
                "Invalid encoded length, expected 56 characters, got 58"
            ),
            // One character too long.
            RejectedVector(
                "GADE5QJ2TY7S5ZB65Q43DFGWYWCPHIYDJ2326KZGAGBN7AE5UY6JVDRRA",
                "Invalid encoded length, expected 56 characters, got 57"
            ),
            RejectedVector(
                "GB6OWYST45X57HCJY5XWOHDEBULB6XUROWPIKW77L5DSNANBEQGUPADT2",
                "Invalid encoded length, expected 56 characters, got 57"
            ),
            // Two characters too long.
            RejectedVector(
                "GB6OWYST45X57HCJY5XWOHDEBULB6XUROWPIKW77L5DSNANBEQGUPADT2T",
                "Invalid encoded length, expected 56 characters, got 58"
            ),
            // The length and the version byte an account id has, and a checksum that does not
            // match the bytes it covers.
            RejectedVector(
                "GDXIIZTKTLVYCBHURXL2UPMTYXOVNI7BRAEFQCP6EZCY4JLKY4VKFNLT",
                "Checksum invalid"
            ),
            // A secret seed: the version byte names a type, and not this one.
            RejectedVector(
                "SAB5556L5AN5KSR5WF7UOEFDCIODEWEO7H2UR4S5R62DFTQOGLKOVZDY",
                "Version byte mismatch"
            ),
            // Strings of other lengths entirely: none of them is written the way a strkey is.
            RejectedVector(
                "gWRYUerEKuz53tstxEuR3NCkiQDcV4wzFHmvLnZmj7PUqxW2wt",
                "Invalid encoded length, expected 56 characters, got 50"
            ),
            RejectedVector("test", "Invalid encoded length, expected 56 characters, got 4"),
            RejectedVector(
                "g4VPBPrHZkfE8CsjuG2S4yBQNd455UWmk",
                "Invalid encoded length, expected 56 characters, got 33"
            )
        )

        for (key in invalidKeys) {
            assertRejected(
                key,
                { StrKey.decodeEd25519PublicKey(it) },
                { StrKey.isValidEd25519PublicKey(it) }
            )
        }
    }

    // Test vectors for valid secret seeds
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

    // Test vectors for invalid secret seeds
    @Test
    fun testInvalidSecretSeeds() {
        val invalidSeeds = listOf(
            // An account id: the version byte names a type, and not this one.
            RejectedVector(
                "GBBM6BKZPEHWYO3E3YKREDPQXMS4VK35YLNU7NFBRI26RAN7GI5POFBB",
                "Version byte mismatch"
            ),
            // One character too long, and two characters too short.
            RejectedVector(
                "SAB5556L5AN5KSR5WF7UOEFDCIODEWEO7H2UR4S5R62DFTQOGLKOVZDYT",
                "Invalid encoded length, expected 56 characters, got 57"
            ),
            RejectedVector(
                "SAFGAMN5Z6IHVI3IVEPIILS7ITZDYSCEPLN4FN5Z3IY63DRH4CIYEV",
                "Invalid encoded length, expected 56 characters, got 54"
            ),
            // The length and the version byte a secret seed has, and a checksum that does not
            // match the bytes it covers.
            RejectedVector(
                "SAFGAMN5Z6IHVI3IVEPIILS7ITZDYSCEPLN4FN5Z3IY63DRH4CIYEVIT",
                "Checksum invalid"
            ),
            RejectedVector("test", "Invalid encoded length, expected 56 characters, got 4")
        )

        for (seed in invalidSeeds) {
            assertRejected(
                seed,
                { StrKey.decodeEd25519SecretSeed(it.toCharArray()) },
                { StrKey.isValidEd25519SecretSeed(it.toCharArray()) }
            )
        }
    }

    // Test decode with wrong version byte
    @Test
    fun testDecodeWithWrongVersionByte() {
        // A secret seed read as an account id, and an account id read as a secret seed. Each
        // carries a version byte that names a type, and neither names the one asked for.
        val seedFailure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519SecretSeed("GBPXXOA5N4JYPESHAADMQKBPWZWQDQ64ZV6ZL2S3LAGW4SY7NTCMWIVL".toCharArray())
        }
        assertEquals("Version byte mismatch", seedFailure.message)

        val publicKeyFailure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("SBGWKM3CD4IL47QN6X54N6Y33T3JDNVI6AIJ6CD5IM47HG3IG4O36XCU")
        }
        assertEquals("Version byte mismatch", publicKeyFailure.message)
    }

    // Test decode with invalid encoded strings
    @Test
    fun testDecodeInvalidEncodedStrings() {
        val invalidAccountIds = listOf(
            // '0' is no base32 character.
            RejectedVector(
                "GBPXX0A5N4JYPESHAADMQKBPWZWQDQ64ZV6ZL2S3LAGW4SY7NTCMWIVL",
                "Invalid base32 encoded string"
            ),
            // Two characters too long, once with characters no strkey is written in and once
            // without.
            RejectedVector(
                "GCFZB6L25D26RQFDWSSBDEYQ32JHLRMTT44ZYE3DZQUTYOL7WY43PLBG++",
                "Invalid encoded length, expected 56 characters, got 58"
            ),
            RejectedVector(
                "GB6OWYST45X57HCJY5XWOHDEBULB6XUROWPIKW77L5DSNANBEQGUPADT2T",
                "Invalid encoded length, expected 56 characters, got 58"
            )
        )
        for (accountId in invalidAccountIds) {
            assertRejected(
                accountId,
                { StrKey.decodeEd25519PublicKey(it) },
                { StrKey.isValidEd25519PublicKey(it) }
            )
        }

        val invalidSeeds = listOf(
            // Six characters too short, one too long and two too long.
            RejectedVector(
                "SB7OJNF5727F3RJUG5ASQJ3LUM44ELLNKW35ZZQDHMVUUQNGYW",
                "Invalid encoded length, expected 56 characters, got 50"
            ),
            RejectedVector(
                "SB7OJNF5727F3RJUG5ASQJ3LUM44ELLNKW35ZZQDHMVUUQNGYWMEGB2W2",
                "Invalid encoded length, expected 56 characters, got 57"
            ),
            RejectedVector(
                "SB7OJNF5727F3RJUG5ASQJ3LUM44ELLNKW35ZZQDHMVUUQNGYWMEGB2W2T",
                "Invalid encoded length, expected 56 characters, got 58"
            ),
            // '0' is no base32 character.
            RejectedVector(
                "SCMB30FQCIQAWZ4WQTS6SVK37LGMAFJGXOZIHTH2PY6EXLP37G46H6DT",
                "Invalid base32 encoded string"
            ),
            // Two characters too long, and the two are not base32 characters either.
            RejectedVector(
                "SAYC2LQ322EEHZYWNSKBEW6N66IRTDREEBUXXU5HPVZGMAXKLIZNM45H++",
                "Invalid encoded length, expected 56 characters, got 58"
            )
        )
        for (seed in invalidSeeds) {
            assertRejected(
                seed,
                { StrKey.decodeEd25519SecretSeed(it.toCharArray()) },
                { StrKey.isValidEd25519SecretSeed(it.toCharArray()) }
            )
        }
    }

    // Test decode with wrong checksum
    @Test
    fun testDecodeWrongChecksum() {
        // An account id and a secret seed, each of the length and with the version byte its type
        // has, and each carrying a checksum that does not match the bytes it covers.
        val accountIdFailure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519PublicKey("GBPXXOA5N4JYPESHAADMQKBPWZWQDQ64ZV6ZL2S3LAGW4SY7NTCMWIVT")
        }
        assertEquals("Checksum invalid", accountIdFailure.message)

        val seedFailure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeEd25519SecretSeed("SBGWKM3CD4IL47QN6X54N6Y33T3JDNVI6AIJ6CD5IM47HG3IG4O36XCX".toCharArray())
        }
        assertEquals("Checksum invalid", seedFailure.message)
    }

    // Test encode with proper prefix
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

    // Test muxed accounts
    @Test
    fun testMuxedAccounts() {
        // The SEP-0023 valid vector whose id exceeds the largest signed 64-bit integer;
        // sep23ValidVectors() carries the id-0 vector.
        val muxedAddress = sep23MaxIdMuxedAccountId
        val rawMuxedKey = hexToBytes(sep23Hash + "8000000000000000")

        // Encodes & decodes M... addresses correctly
        assertEquals(muxedAddress, StrKey.encodeMed25519PublicKey(rawMuxedKey))
        assertTrue(rawMuxedKey.contentEquals(StrKey.decodeMed25519PublicKey(muxedAddress)))

        // Validation
        assertTrue(StrKey.isValidMed25519PublicKey(muxedAddress))
    }

    // Test contracts
    @Test
    fun testContractIdDecodesToItsHash() {
        val contractId = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"
        val asHex = "363eaa3867841fbad0f4ed88c779e4fe66e56a2470dc98c0ec9c073d05c7b103"

        val decoded = StrKey.decodeContract(contractId)
        assertEquals(asHex, bytesToHex(decoded))
        assertEquals(contractId, StrKey.encodeContract(hexToBytes(asHex)))

        assertTrue(StrKey.isValidContract(contractId))
        assertFalse(StrKey.isValidContract("GA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"))
    }

    // Test liquidity pools
    @Test
    fun testLiquidityPools() {
        val liquidityPoolId = sep23LiquidityPoolId
        val asHex = sep23Hash

        val decoded = StrKey.decodeLiquidityPool(liquidityPoolId)
        assertEquals(asHex, bytesToHex(decoded))
        assertEquals(liquidityPoolId, StrKey.encodeLiquidityPool(hexToBytes(asHex)))

        assertTrue(StrKey.isValidLiquidityPool(liquidityPoolId))
        assertFalse(StrKey.isValidLiquidityPool("LB7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUPJN"))
    }

    // Test claimable balances
    @Test
    fun testClaimableBalances() {
        val claimableBalanceId = sep23ClaimableBalanceId
        val asHex = "00" + sep23Hash

        val decoded = StrKey.decodeClaimableBalance(claimableBalanceId)
        assertEquals(asHex, bytesToHex(decoded))
        assertEquals(claimableBalanceId, StrKey.encodeClaimableBalance(hexToBytes(asHex)))

        assertTrue(StrKey.isValidClaimableBalance(claimableBalanceId))
        assertFalse(StrKey.isValidClaimableBalance("BBAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU"))

        // A 32-byte input is the hash on its own, which is written under the discriminant the
        // XDR union declares.
        val hashOnly = sep23Hash
        val encoded32Byte = StrKey.encodeClaimableBalance(hexToBytes(hashOnly))
        assertEquals(claimableBalanceId, encoded32Byte)

        // A 33-byte input carries that discriminant itself, followed by the hash.
        val fullBytes = "00" + hashOnly
        val encoded33Byte = StrKey.encodeClaimableBalance(hexToBytes(fullBytes))
        assertEquals(claimableBalanceId, encoded33Byte)

        // A 36-byte input is the XDR wire form: the discriminant across four big-endian bytes,
        // followed by the hash. It is the shape Horizon reports.
        val xdrBytes = "00000000" + hashOnly
        val encoded36Byte = StrKey.encodeClaimableBalance(hexToBytes(xdrBytes))
        assertEquals(claimableBalanceId, encoded36Byte)

        // All three widths name the same balance, so they spell one strkey
        assertEquals(encoded32Byte, encoded33Byte)
        assertEquals(encoded32Byte, encoded36Byte)
    }

    @Test
    fun testEncodeClaimableBalanceInvalidLength() {
        // Test with invalid input sizes (not 32, 33 or 36 bytes)
        val tooShort = byteArrayOf(1, 2, 3)
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeClaimableBalance(tooShort)
        }

        // The widths between the strkey body and the XDR form, and the one past it: accepting
        // any of them would mean a width is being padded or truncated to a shape it is not.
        for (size in listOf(34, 35, 37)) {
            val failure = assertFailsWith<IllegalArgumentException>("a $size byte id must be rejected") {
                StrKey.encodeClaimableBalance(ByteArray(size) { it.toByte() })
            }
            assertEquals(
                "Claimable balance ID must be 32 bytes (hash only), 33 bytes (type + hash) or " +
                    "36 bytes (XDR form), got $size bytes",
                failure.message
            )
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

    // ========== SEP-0023 test vectors ==========
    //
    // The strkey vectors SEP-0023 publishes, transcribed from the specification. Every invalid
    // one is listed with the reason the specification gives and with the message the check that
    // rejects it reports. The two need not describe the same check: a string whose length no
    // strkey of its type has is stopped before anything is decoded, whatever else is wrong with
    // it, and a vector that stood only for the check its reason names would prove nothing about
    // that check.

    /** The 32-byte hash the SEP-0023 vectors are built around. */
    private val sep23Hash = ClaimableBalanceVectors.hashHex

    private val sep23AccountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    /** The SEP-0023 valid multiplexed account vector, whose id is 0. */
    private val sep23MuxedAccountId =
        "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUQ"

    /**
     * The SEP-0023 valid multiplexed account vector whose id exceeds the largest signed
     * 64-bit integer.
     */
    private val sep23MaxIdMuxedAccountId =
        "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLK"

    /** The SEP-0023 valid signed payload vector, whose payload is 32 bytes and needs no padding. */
    private val sep23SignedPayload =
        "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAQACAQDAQ" +
            "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DUPB6IBZGM"

    /** The SEP-0023 valid signed payload vector whose 29-byte payload is zero padded. */
    private val sep23PaddedSignedPayload =
        "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAOQCAQDAQ" +
            "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DUAAAAFGBU"

    private val sep23ContractId = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"

    private val sep23LiquidityPoolId = "LA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUPJN"

    private val sep23ClaimableBalanceId = ClaimableBalanceVectors.strKey

    private class Sep23ValidVector(
        val description: String,
        val strKey: String,
        val dataHex: String,
        val decode: (String) -> ByteArray,
        val isValid: (String) -> Boolean
    )

    /**
     * The SEP-0023 valid vectors whose key no other test in this file reads back. The multiplexed
     * account whose id exceeds the largest signed 64-bit integer, the liquidity pool and the
     * claimable balance are read back by [testMuxedAccounts], [testLiquidityPools] and
     * [testClaimableBalances].
     */
    private fun sep23ValidVectors(): List<Sep23ValidVector> = listOf(
        Sep23ValidVector(
            "non-multiplexed account", sep23AccountId, sep23Hash,
            { StrKey.decodeEd25519PublicKey(it) }, { StrKey.isValidEd25519PublicKey(it) }
        ),
        Sep23ValidVector(
            // A multiplexed account holds the ed25519 public key followed by the id, and this
            // vector's id is 0.
            "multiplexed account", sep23MuxedAccountId, sep23Hash + "0000000000000000",
            { StrKey.decodeMed25519PublicKey(it) }, { StrKey.isValidMed25519PublicKey(it) }
        ),
        Sep23ValidVector(
            // A signed payload holds the ed25519 public key, the declared payload length as a
            // big-endian four-byte value, and the payload. 32 bytes need no padding.
            "signed payload with a 32-byte payload", sep23SignedPayload,
            sep23Hash + "00000020" + "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20",
            { StrKey.decodeSignedPayload(it) }, { StrKey.isValidSignedPayload(it) }
        ),
        Sep23ValidVector(
            // 29 payload bytes are padded with three zero bytes to the four-byte boundary.
            "signed payload with a 29-byte payload", sep23PaddedSignedPayload,
            sep23Hash + "0000001d" + "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d" +
                "000000",
            { StrKey.decodeSignedPayload(it) }, { StrKey.isValidSignedPayload(it) }
        ),
        Sep23ValidVector(
            "contract", sep23ContractId, sep23Hash,
            { StrKey.decodeContract(it) }, { StrKey.isValidContract(it) }
        )
    )

    @Test
    fun testSep23ValidVectorsDecodeToTheKeysTheySpell() {
        for (vector in sep23ValidVectors()) {
            assertTrue(vector.isValid(vector.strKey), "${vector.description}: must be valid")
            assertEquals(
                vector.dataHex, bytesToHex(vector.decode(vector.strKey)),
                "${vector.description}: must decode to the key it spells"
            )
        }
    }

    private class Sep23InvalidVector(
        /** The reason SEP-0023 gives for listing the vector as invalid. */
        val reason: String,
        val strKey: String,
        /** The message the check that rejects the vector reports. */
        val rejection: String,
        val decode: (String) -> ByteArray,
        val isValid: (String) -> Boolean,
        /** What the vector carries, where that is what makes its reason provable. */
        val evidence: (() -> Unit)? = null
    )

    /**
     * The fifteen invalid vectors SEP-0023 publishes, in the order the specification lists them.
     */
    private fun sep23InvalidVectors(): List<Sep23InvalidVector> {
        val g: (String) -> ByteArray = { StrKey.decodeEd25519PublicKey(it) }
        val gValid: (String) -> Boolean = { StrKey.isValidEd25519PublicKey(it) }
        val m: (String) -> ByteArray = { StrKey.decodeMed25519PublicKey(it) }
        val mValid: (String) -> Boolean = { StrKey.isValidMed25519PublicKey(it) }
        val p: (String) -> ByteArray = { StrKey.decodeSignedPayload(it) }
        val pValid: (String) -> Boolean = { StrKey.isValidSignedPayload(it) }
        val b: (String) -> ByteArray = { StrKey.decodeClaimableBalance(it) }
        val bValid: (String) -> Boolean = { StrKey.isValidClaimableBalance(it) }

        val trailingBitSet = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUR"
        val padded = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUK==="
        val declaredShorterThanPayload =
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAQACAQDAQ" +
                "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DUPB6IAAAAAAAAPM"
        val declaredLongerThanPayload =
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAOQCAQDAQ" +
                "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4Z2PQ"
        val withoutZeroPadding =
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAOQCAQDAQ" +
                "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DXFH6"
        val undeclaredClaimableBalanceType = "BAAT6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGXACA"

        return listOf(
            Sep23InvalidVector(
                "invalid length: an ed25519 public key is 32 bytes, not 5",
                "GAAAAAAAACGC6",
                "Invalid encoded length, expected 56 characters, got 13",
                g, gValid
            ),
            Sep23InvalidVector(
                "the unused trailing bit of the last character must be zero",
                trailingBitSet,
                unusedBitsRejection,
                m, mValid,
                evidence = {
                    // Padding is a second way to write the same characters, and it must not turn
                    // a rejected encoding into an accepted one.
                    assertFalse(
                        StrKey.isValidMed25519PublicKey("$trailingBitSet==="),
                        "padding must not rescue an encoding that leaves a trailing bit set"
                    )
                }
            ),
            Sep23InvalidVector(
                "invalid length: congruent to 1 mod 8",
                "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZA",
                "Invalid encoded length, expected 56 characters, got 57",
                g, gValid
            ),
            Sep23InvalidVector(
                "invalid length: base-32 decoding yields 36 bytes, not 35",
                "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUACUSI",
                "Invalid encoded length, expected 56 characters, got 58",
                g, gValid
            ),
            Sep23InvalidVector(
                "invalid algorithm: the low three bits of the version byte are 7",
                "G47QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVP2I",
                "Version byte is invalid",
                g, gValid
            ),
            Sep23InvalidVector(
                "invalid length: congruent to 6 mod 8",
                "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLKA",
                "Invalid encoded length, expected 69 characters, got 70",
                m, mValid
            ),
            Sep23InvalidVector(
                "invalid length: base-32 decoding yields 44 bytes, not 43",
                "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAAV75I",
                "Invalid encoded length, expected 69 characters, got 71",
                m, mValid
            ),
            Sep23InvalidVector(
                "invalid algorithm: the low three bits of the version byte are 7",
                "M47QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUQ",
                "Version byte is invalid",
                m, mValid
            ),
            Sep23InvalidVector(
                "padding characters are not allowed",
                padded,
                "Invalid encoded length, expected 69 characters, got 72",
                m, mValid,
                evidence = {
                    // With the padding taken off the string is the length a multiplexed account
                    // has and its checksum does not match, so the checksum is never what rejects
                    // the padded form.
                    val stripped = padded.removeSuffix("===")
                    assertEquals(69, stripped.length)
                    val failure = assertFailsWith<IllegalArgumentException> {
                        StrKey.decodeMed25519PublicKey(stripped)
                    }
                    assertEquals("Checksum invalid", failure.message)
                }
            ),
            Sep23InvalidVector(
                "invalid checksum",
                "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUO",
                "Checksum invalid",
                m, mValid
            ),
            Sep23InvalidVector(
                "the declared length is shorter than the payload present",
                declaredShorterThanPayload,
                "$signedPayloadSizeRejection, a declared length of 32 requires 68 bytes, got 72",
                p, pValid,
                evidence = { assertSignedPayloadFraming(declaredShorterThanPayload, 32L, 72) }
            ),
            Sep23InvalidVector(
                "the declared length is longer than the payload present",
                declaredLongerThanPayload,
                "$signedPayloadSizeRejection, a declared length of 29 requires 68 bytes, got 64",
                p, pValid,
                evidence = { assertSignedPayloadFraming(declaredLongerThanPayload, 29L, 64) }
            ),
            Sep23InvalidVector(
                // The declared length names 29 payload bytes inside 65 data bytes, a size no
                // padding of a 29-byte payload produces, so the exact-fit rule is what reports it
                // and the padding bytes are never read.
                "no zero padding in the signed payload",
                withoutZeroPadding,
                "$signedPayloadSizeRejection, a declared length of 29 requires 68 bytes, got 65",
                p, pValid,
                evidence = { assertSignedPayloadFraming(withoutZeroPadding, 29L, 65) }
            ),
            Sep23InvalidVector(
                "the unused trailing two bits of the last character must be zero",
                "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TV",
                unusedBitsRejection,
                b, bValid
            ),
            Sep23InvalidVector(
                "invalid claimable balance type: the first byte of the binary key is not 0",
                undeclaredClaimableBalanceType,
                "$claimableBalanceDiscriminantRejection, expected 0, got 1",
                b, bValid,
                evidence = {
                    val data = checkedClaimableBalanceData(undeclaredClaimableBalanceType)
                    assertEquals(58, undeclaredClaimableBalanceType.length, "encoded length")
                    assertEquals(33, data.size, "the vector must carry the payload the type admits")
                    assertEquals(1, data[0].toInt(), "the discriminant the vector is written for")
                }
            )
        )
    }

    /**
     * Reads the framing of the signed payload [strKey] carries and checks that it declares
     * [declaredLength] inside [dataSize] data bytes. A vector whose checksum or version byte did
     * not hold would be rejected for that rather than for the framing it is listed under.
     */
    private fun assertSignedPayloadFraming(strKey: String, declaredLength: Long, dataSize: Int) {
        val data = checkedSignedPayloadData(strKey)
        assertEquals(dataSize, data.size, "data size")
        assertEquals(declaredLength, declaredPayloadLength(data), "declared payload length")
    }

    @Test
    fun testSep23InvalidVectorsAreRejected() {
        val vectors = sep23InvalidVectors()
        assertEquals(15, vectors.size, "SEP-0023 publishes fifteen invalid vectors")

        for (vector in vectors) {
            val label = "${vector.strKey} (${vector.reason})"
            assertFalse(vector.isValid(vector.strKey), "$label: must be invalid")
            val failure = assertFailsWith<IllegalArgumentException>("$label: must be rejected") {
                vector.decode(vector.strKey)
            }
            assertEquals(
                vector.rejection, failure.message,
                "$label: the rejection must name the check that reports it"
            )
            vector.evidence?.invoke()
        }
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
        // Test with minimum size (40 bytes: 32 + 4 + a single payload byte padded to 4)
        val minPayload = signedPayloadData(1)
        assertEquals(40, minPayload.size)
        val encodedMin = StrKey.encodeSignedPayload(minPayload)
        assertTrue(encodedMin.startsWith("P"))
        val decodedMin = StrKey.decodeSignedPayload(encodedMin)
        assertTrue(minPayload.contentEquals(decodedMin))

        // Test with maximum size (100 bytes: 32 + 4 + 64 payload bytes)
        val maxPayload = signedPayloadData(64)
        assertEquals(100, maxPayload.size)
        val encodedMax = StrKey.encodeSignedPayload(maxPayload)
        assertTrue(encodedMax.startsWith("P"))
        val decodedMax = StrKey.decodeSignedPayload(encodedMax)
        assertTrue(maxPayload.contentEquals(decodedMax))

        // Test validation
        assertTrue(StrKey.isValidSignedPayload(encodedMin))
        assertTrue(StrKey.isValidSignedPayload(encodedMax))

        // Test invalid size (too small)
        val tooSmall = assertFailsWith<IllegalArgumentException> {
            StrKey.encodeSignedPayload(ByteArray(39))
        }
        assertEquals("Signed payload must be between 40 and 100 bytes, got 39", tooSmall.message)

        // Test invalid size (too large)
        val tooLarge = assertFailsWith<IllegalArgumentException> {
            StrKey.encodeSignedPayload(ByteArray(101))
        }
        assertEquals("Signed payload must be between 40 and 100 bytes, got 101", tooLarge.message)
    }

    // Test round-trip for all key types
    @Test
    fun testRoundTripAllTypes() {
        val data32 = ByteArray(32) { i -> (i * 3).toByte() }
        val data33 = ByteArray(33) { i -> (i * 3).toByte() }
        val data40 = ByteArray(40) { i -> (i * 3).toByte() }
        // A signed payload frames its bytes, so this one carries a 14-byte payload padded to 16.
        val signedPayloadRaw = signedPayloadData(14)

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
        val signedPayload = StrKey.encodeSignedPayload(signedPayloadRaw)
        assertTrue(signedPayloadRaw.contentEquals(StrKey.decodeSignedPayload(signedPayload)))

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

    // ========== Encode: raw payload length validation ==========

    @Test
    fun testEncodeMed25519PublicKeyInvalidLengthThrows() {
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeMed25519PublicKey(ByteArray(39))
        }
    }

    @Test
    fun testEncodePreAuthTxInvalidLengthThrows() {
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodePreAuthTx(ByteArray(31))
        }
    }

    @Test
    fun testEncodeSha256HashInvalidLengthThrows() {
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeSha256Hash(ByteArray(33))
        }
    }

    @Test
    fun testEncodeLiquidityPoolInvalidLengthThrows() {
        assertFailsWith<IllegalArgumentException> {
            StrKey.encodeLiquidityPool(ByteArray(31))
        }
    }

    // ========== Decode: payload-length validation ==========
    //
    // Two checks bound the payload, and they read the type from different places. The encoded
    // length is fixed by the type the caller asked for, so a string that could not hold that type's
    // payload is turned away before it is decoded. The decoded payload is then held against the
    // type the version byte inside it names, which need not be the type the caller asked for: a
    // string can have the length the requested type has and still carry a payload size the type it
    // names does not admit, and that is what the decoded-length check catches. The strkeys below
    // are hand-crafted with a correct CRC16-XModem checksum, standing in for a malicious or
    // corrupted strkey.

    // The version byte values a strkey is written under are protocol constants (see SEP-0023 /
    // CAP-0027). StrKey keeps its own enum of them private, so they are spelled out here.
    private val accountIdVersionByte: Byte = (6 shl 3).toByte()
    private val claimableBalanceVersionByte: Byte = (1 shl 3).toByte()
    private val signedPayloadVersionByte: Byte = (15 shl 3).toByte()

    private fun crc16XModem(bytes: ByteArray): ByteArray {
        var crc = 0x0000
        for (byte in bytes) {
            var code = (crc ushr 8) and 0xFF
            code = code xor (byte.toInt() and 0xFF)
            code = code xor (code ushr 4)
            crc = (crc shl 8) and 0xFFFF
            crc = crc xor code
            code = (code shl 5) and 0xFFFF
            crc = crc xor code
            code = (code shl 7) and 0xFFFF
            crc = crc xor code
        }
        return byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
    }

    private fun craftStrKey(versionByte: Byte, data: ByteArray): String {
        val payload = byteArrayOf(versionByte) + data
        val checksum = crc16XModem(payload)
        val unencoded = payload + checksum
        val encoded = Base32Codec.encode(unencoded)
        val unpaddedLength = encoded.indexOfFirst { it == '='.code.toByte() }.let {
            if (it == -1) encoded.size else it
        }
        return encoded.copyOfRange(0, unpaddedLength).map { it.toInt().toChar() }.joinToString("")
    }

    /**
     * The data bytes [strKey] carries, having checked that its checksum matches and that it is
     * written under [versionByte], the version byte of [type]. A vector that failed either check
     * would be rejected for that rather than for what it is written to exercise.
     */
    private fun checkedData(strKey: String, versionByte: Byte, type: String): ByteArray {
        val decoded = Base32Codec.decode(strKey.map { it.code.toByte() }.toByteArray())
        val payload = decoded.copyOfRange(0, decoded.size - 2)
        val checksum = decoded.copyOfRange(decoded.size - 2, decoded.size)
        assertTrue(
            crc16XModem(payload).contentEquals(checksum),
            "the vector must carry a valid checksum"
        )
        assertEquals(versionByte, payload[0], "the vector must be $type")
        return payload.copyOfRange(1, payload.size)
    }

    @Test
    fun testDecodeClaimableBalanceRejectsAPayloadSizeItsEncodedLengthCannotHold() {
        // A validly checksummed B... strkey carrying 20 payload bytes. 20 bytes are written in 37
        // characters, and no claimable balance id is 37 characters long.
        val badKey = craftStrKey(claimableBalanceVersionByte, ByteArray(20))
        assertEquals(37, badKey.length)
        val failure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeClaimableBalance(badKey)
        }
        assertEquals(
            "$encodedLengthRejection, expected 58 characters, got 37", failure.message,
            "the rejection must name the check that reports it"
        )
        assertFalse(StrKey.isValidClaimableBalance(badKey))
    }

    @Test
    fun testDecodeSignedPayloadRejectsAPayloadSizeItsEncodedLengthCannotHold() {
        // A validly checksummed P... strkey carrying 30 payload bytes, which is below the 40 the
        // type admits. 30 bytes are written in 53 characters, and the shortest signed payload is
        // 69 characters long.
        val badKey = craftStrKey(signedPayloadVersionByte, ByteArray(30))
        assertEquals(53, badKey.length)
        val failure = assertFailsWith<IllegalArgumentException> {
            StrKey.decodeSignedPayload(badKey)
        }
        assertEquals(
            "$encodedLengthRejection, expected between 69 and 165 characters, got 53",
            failure.message,
            "the rejection must name the check that reports it"
        )
        assertFalse(StrKey.isValidSignedPayload(badKey))
    }

    private class DecodedLengthCase(
        val description: String,
        val strKey: String,
        val rejection: String,
        val decode: (String) -> ByteArray,
        val isValid: (String) -> Boolean
    )

    @Test
    fun testDecodeRejectsPayloadSizeTheVersionByteInsideItDoesNotAdmit() {
        // Each strkey below has the encoded length the caller's type requires, so the
        // encoded-length check passes it, and carries a version byte naming a different type whose
        // payload sizes it does not fit. The decoded-length check reads that version byte, so it is
        // the check that reports these, and it runs ahead of the version-byte comparison so that
        // the framing a type defines is only ever read from a payload of a size that type admits.
        val cases = listOf(
            DecodedLengthCase(
                "a 58-character strkey naming the signed payload type",
                craftStrKey(signedPayloadVersionByte, ByteArray(33)),
                "Invalid data length, expected between 40 and 100 bytes, got 33",
                { StrKey.decodeClaimableBalance(it) },
                { StrKey.isValidClaimableBalance(it) }
            ),
            DecodedLengthCase(
                "a 58-character strkey naming the ed25519 public key type",
                craftStrKey(accountIdVersionByte, ByteArray(33)),
                "Invalid data length, expected 32 bytes, got 33",
                { StrKey.decodeClaimableBalance(it) },
                { StrKey.isValidClaimableBalance(it) }
            ),
            DecodedLengthCase(
                "a 69-character strkey naming the ed25519 public key type, read as a muxed account",
                craftStrKey(accountIdVersionByte, ByteArray(40)),
                "Invalid data length, expected 32 bytes, got 40",
                { StrKey.decodeMed25519PublicKey(it) },
                { StrKey.isValidMed25519PublicKey(it) }
            ),
            DecodedLengthCase(
                // 69 characters is the shortest a signed payload can be, so the range the
                // encoded-length check holds this type to admits the string as well.
                "a 69-character strkey naming the ed25519 public key type, read as a signed payload",
                craftStrKey(accountIdVersionByte, ByteArray(40)),
                "Invalid data length, expected 32 bytes, got 40",
                { StrKey.decodeSignedPayload(it) },
                { StrKey.isValidSignedPayload(it) }
            )
        )

        for (case in cases) {
            val failure = assertFailsWith<IllegalArgumentException>(
                "${case.description}: must be rejected"
            ) {
                case.decode(case.strKey)
            }
            assertEquals(
                case.rejection, failure.message,
                "${case.description}: the rejection must name the check that reports it"
            )
            assertFalse(
                case.isValid(case.strKey),
                "${case.description}: isValid must agree with decode"
            )
        }
    }

    // ========== Decode: encoded-string length ==========
    //
    // A strkey type fixes the number of characters its encoded form has, so a string of any
    // other length is not a strkey of that type and is rejected before anything is decoded.

    /**
     * The checks a string passes before its bytes stand for a key. Naming them lets a test state
     * which one it exercises instead of settling for a rejection.
     *
     * [nonAlphabetCharacterRejection] is reported by two of them: the guard that admits only
     * characters inside the ASCII range, and the alphabet check. Both answer the same question -
     * whether the string is written in the characters a strkey is written in - so they say so in
     * the same words, and a message alone does not tell them apart.
     */
    private val encodedLengthRejection = "Invalid encoded length"
    private val leftoverCharacterRejection = "Encoded char array has leftover character"
    private val unusedBitsRejection = "Unused bits should be set to 0"
    private val nonAlphabetCharacterRejection = "Invalid base32 encoded string"

    /**
     * A strkey type, the entry points that read it and one valid strkey of that type. Every test
     * that needs a genuinely valid strkey per type takes it from here, so the types a check is
     * exercised against cannot drift apart between tests.
     */
    private class StrKeyType(
        val name: String,
        val valid: String,
        val dataLength: Int,
        /**
         * Characters an encoded strkey of this type has, or null for a type whose encoded lengths
         * form a range. A signed payload carries a variable payload, so its length follows from
         * the payload rather than from the type.
         */
        val fixedEncodedLength: Int?,
        val decode: (String) -> ByteArray,
        val isValid: (String) -> Boolean
    )

    private fun strKeyTypes(): List<StrKeyType> {
        val sample32 = ByteArray(32) { i -> (i * 5 + 1).toByte() }
        return listOf(
            StrKeyType(
                "ed25519 public key", sep23AccountId, 32, 56,
                { StrKey.decodeEd25519PublicKey(it) },
                { StrKey.isValidEd25519PublicKey(it) }
            ),
            StrKeyType(
                "ed25519 secret seed",
                "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE", 32, 56,
                { StrKey.decodeEd25519SecretSeed(it.toCharArray()) },
                { StrKey.isValidEd25519SecretSeed(it.toCharArray()) }
            ),
            StrKeyType(
                "pre-auth transaction", StrKey.encodePreAuthTx(sample32), 32, 56,
                { StrKey.decodePreAuthTx(it) },
                { StrKey.isValidPreAuthTx(it) }
            ),
            StrKeyType(
                "sha256 hash", StrKey.encodeSha256Hash(sample32), 32, 56,
                { StrKey.decodeSha256Hash(it) },
                { StrKey.isValidSha256Hash(it) }
            ),
            StrKeyType(
                "contract", sep23ContractId, 32, 56,
                { StrKey.decodeContract(it) },
                { StrKey.isValidContract(it) }
            ),
            StrKeyType(
                "liquidity pool", sep23LiquidityPoolId, 32, 56,
                { StrKey.decodeLiquidityPool(it) },
                { StrKey.isValidLiquidityPool(it) }
            ),
            StrKeyType(
                "claimable balance", sep23ClaimableBalanceId, 33, 58,
                { StrKey.decodeClaimableBalance(it) },
                { StrKey.isValidClaimableBalance(it) }
            ),
            StrKeyType(
                "muxed ed25519 public key", sep23MuxedAccountId, 40, 69,
                { StrKey.decodeMed25519PublicKey(it) },
                { StrKey.isValidMed25519PublicKey(it) }
            ),
            StrKeyType(
                "signed payload", sep23SignedPayload, 68, null,
                { StrKey.decodeSignedPayload(it) },
                { StrKey.isValidSignedPayload(it) }
            )
        )
    }

    private fun assertRejectedForEncodedLength(
        label: String,
        candidate: String,
        expectation: String,
        decode: (String) -> ByteArray,
        isValid: (String) -> Boolean
    ) {
        val failure = assertFailsWith<IllegalArgumentException>(
            "$label: a ${candidate.length} character string must be rejected"
        ) {
            decode(candidate)
        }
        val message = failure.message.orEmpty()
        assertTrue(
            message.contains(encodedLengthRejection),
            "$label: rejected for a reason other than its length: $message"
        )
        assertTrue(
            message.contains(expectation) && message.contains(candidate.length.toString()),
            "$label: message must name the expected and the actual length: $message"
        )
        assertFalse(isValid(candidate), "$label: isValid must agree with decode")
    }

    @Test
    fun testDecodeRejectsEncodedLengthThatDoesNotMatchType() {
        // The signed payload is left out: its legal encoded lengths form a range, so a string one
        // character longer than a valid one can still be inside it. Its bounds are checked by
        // testSignedPayloadEncodedLengthBounds.
        val fixedLengthTypes = strKeyTypes().filter { it.fixedEncodedLength != null }
        assertEquals(8, fixedLengthTypes.size, "every type but the signed payload has one length")

        for (type in fixedLengthTypes) {
            val encodedLength = type.fixedEncodedLength!!
            assertEquals(encodedLength, type.valid.length, "${type.name}: test vector length")
            assertTrue(type.isValid(type.valid), "${type.name}: a valid strkey must be accepted")
            assertEquals(
                type.dataLength, type.decode(type.valid).size,
                "${type.name}: a valid strkey must decode to its payload"
            )

            assertRejectedForEncodedLength(
                type.name,
                type.valid.substring(0, type.valid.length - 1),
                encodedLength.toString(),
                type.decode,
                type.isValid
            )
            assertRejectedForEncodedLength(
                type.name,
                type.valid + "A",
                encodedLength.toString(),
                type.decode,
                type.isValid
            )
        }
    }

    /**
     * Builds well-formed signed payload data: a 32-byte ed25519 public key, the declared
     * payload length as a big-endian four-byte value, and the payload zero-padded to a
     * four-byte boundary.
     */
    private fun signedPayloadData(payloadLength: Int): ByteArray {
        val paddedPayloadLength = (payloadLength + 3) / 4 * 4
        val data = ByteArray(32 + 4 + paddedPayloadLength)
        for (i in 0 until 32) {
            data[i] = (i + 1).toByte()
        }
        data[32] = ((payloadLength ushr 24) and 0xFF).toByte()
        data[33] = ((payloadLength ushr 16) and 0xFF).toByte()
        data[34] = ((payloadLength ushr 8) and 0xFF).toByte()
        data[35] = (payloadLength and 0xFF).toByte()
        for (i in 0 until payloadLength) {
            data[36 + i] = (i + 1).toByte()
        }
        return data
    }

    @Test
    fun testSignedPayloadEncodedLengthBounds() {
        // The smallest and the largest signed payload a P... strkey can carry pin the range
        // of encoded lengths the type admits.
        val shortest = StrKey.encodeSignedPayload(signedPayloadData(1))
        assertEquals(69, shortest.length)
        assertTrue(StrKey.isValidSignedPayload(shortest))

        val longest = StrKey.encodeSignedPayload(signedPayloadData(64))
        assertEquals(165, longest.length)
        assertTrue(StrKey.isValidSignedPayload(longest))

        val tooShort = shortest.substring(0, shortest.length - 1)
        assertEquals(68, tooShort.length)
        assertRejectedForEncodedLength(
            "signed payload", tooShort, "69",
            { StrKey.decodeSignedPayload(it) }, { StrKey.isValidSignedPayload(it) }
        )

        val tooLong = longest + "A"
        assertEquals(166, tooLong.length)
        assertRejectedForEncodedLength(
            "signed payload", tooLong, "165",
            { StrKey.decodeSignedPayload(it) }, { StrKey.isValidSignedPayload(it) }
        )
    }

    @Test
    fun testDecodeRejectsMultiMegabyteInput() {
        // (length * 5) % 8 is below 5 for this length, so the leftover-character check accepts
        // it: the rejection can only come from the encoded-length check, and it happens before
        // the string is decoded.
        val length = 2_000_000
        assertTrue((length * 5) % 8 < 5, "the length must survive the leftover-character check")
        val huge = "A".repeat(length)

        assertRejectedForEncodedLength(
            "ed25519 public key", huge, "56",
            { StrKey.decodeEd25519PublicKey(it) }, { StrKey.isValidEd25519PublicKey(it) }
        )
        assertRejectedForEncodedLength(
            "claimable balance", huge, "58",
            { StrKey.decodeClaimableBalance(it) }, { StrKey.isValidClaimableBalance(it) }
        )
        assertRejectedForEncodedLength(
            "signed payload", huge, "165",
            { StrKey.decodeSignedPayload(it) }, { StrKey.isValidSignedPayload(it) }
        )
    }

    private class DecodeEntryPoint(val name: String, val decode: (String) -> ByteArray)

    private fun decodeEntryPoints(): List<DecodeEntryPoint> = listOf(
        DecodeEntryPoint("decodeEd25519PublicKey") { StrKey.decodeEd25519PublicKey(it) },
        DecodeEntryPoint("decodeEd25519SecretSeed") { StrKey.decodeEd25519SecretSeed(it.toCharArray()) },
        DecodeEntryPoint("decodeMed25519PublicKey") { StrKey.decodeMed25519PublicKey(it) },
        DecodeEntryPoint("decodePreAuthTx") { StrKey.decodePreAuthTx(it) },
        DecodeEntryPoint("decodeSha256Hash") { StrKey.decodeSha256Hash(it) },
        DecodeEntryPoint("decodeSignedPayload") { StrKey.decodeSignedPayload(it) },
        DecodeEntryPoint("decodeContract") { StrKey.decodeContract(it) },
        DecodeEntryPoint("decodeLiquidityPool") { StrKey.decodeLiquidityPool(it) },
        DecodeEntryPoint("decodeClaimableBalance") { StrKey.decodeClaimableBalance(it) }
    )

    @Test
    fun testDecodeRejectsDegenerateInputsWithIllegalArgumentException() {
        // Short and degenerate inputs are invalid arguments on every entry point. Nothing from
        // the IndexOutOfBoundsException family may escape, on any target: assertFailsWith
        // accepts only IllegalArgumentException and its subtypes.
        val degenerateInputs = listOf(
            "",
            "=",
            "A",
            "G",
            "GA",
            "GAAA",
            "========",
            "GAAAAAAAACGC6",
            "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJ"
        )
        for (entryPoint in decodeEntryPoints()) {
            for (input in degenerateInputs) {
                assertFailsWith<IllegalArgumentException>(
                    "${entryPoint.name} must reject \"$input\" with IllegalArgumentException"
                ) {
                    entryPoint.decode(input)
                }
            }
        }
    }

    // ========== Decode: base32 alphabet ==========
    //
    // A strkey is written in the 32 characters of the base32 alphabet and in nothing else. The
    // alphabet check rejects the pad character, whitespace and every other byte, and reports the
    // same verdict on every platform even though the codec behind it is platform-specific.

    /** Asserts that [candidate], every character of which is ASCII, fails the alphabet check. */
    private fun assertRejectedByAlphabet(
        label: String,
        candidate: String,
        decode: (String) -> ByteArray,
        isValid: (String) -> Boolean
    ) {
        assertRejectedByRule(label, candidate, nonAlphabetCharacterRejection, decode, isValid)
    }

    /**
     * Asserts that [candidate], which carries a character above the ASCII range, fails the guard
     * on that range.
     */
    private fun assertRejectedByTheAsciiRangeGuard(
        label: String,
        candidate: String,
        decode: (String) -> ByteArray,
        isValid: (String) -> Boolean
    ) {
        assertRejectedByRule(label, candidate, nonAlphabetCharacterRejection, decode, isValid)
    }

    @Test
    fun testDecodeRejectsCharacterOutsideTheBase32Alphabet() {
        // Substituting rather than appending keeps the string at the length its type requires,
        // so it passes the encoded-length check and reaches the alphabet check. The substituted
        // position is in the middle, which leaves the last character - the only one the
        // unused-trailing-bits check reads - as the valid vector had it.
        val substitutes = listOf("a pad character" to '=', "a space" to ' ', "a tab" to '\t')
        for (type in strKeyTypes()) {
            assertTrue(type.isValid(type.valid), "${type.name}: the vector must be valid")
            for ((description, replacement) in substitutes) {
                val middle = type.valid.length / 2
                val candidate =
                    type.valid.substring(0, middle) + replacement + type.valid.substring(middle + 1)
                assertEquals(
                    type.valid.length, candidate.length,
                    "${type.name}: the substitution must not change the length"
                )
                assertRejectedByAlphabet(
                    "${type.name} carrying $description", candidate, type.decode, type.isValid
                )
            }
        }
    }

    @Test
    fun testDecodeRejectsInputOfWhitespaceOrPaddingAlone() {
        // Both strings are as long as an ed25519 public key strkey, so they pass the
        // encoded-length check and reach the alphabet check. Neither carries a version byte, so
        // a decoder that let them through would read past the end of an empty decode result;
        // assertFailsWith accepts IllegalArgumentException alone, and nothing from the
        // IndexOutOfBoundsException family is one.
        val candidates = listOf(
            "spaces alone" to " ".repeat(56),
            "pad characters alone" to "=".repeat(56)
        )
        for ((description, candidate) in candidates) {
            assertRejectedByAlphabet(
                "ed25519 public key of $description", candidate,
                { StrKey.decodeEd25519PublicKey(it) }, { StrKey.isValidEd25519PublicKey(it) }
            )
        }
    }

    @Test
    fun testDecodeRejectsLowercaseAlphabet() {
        // The base32 alphabet is uppercase. Lowercasing keeps the length, so the candidate gets
        // past the encoded-length check. Every character is lowercased, the last one included:
        // the alphabet check runs ahead of the unused-trailing-bits check, so a string written
        // in characters no strkey is written in is reported as that, whatever five-bit value a
        // decoder would otherwise read from its last character.
        for (type in strKeyTypes()) {
            val lowercased = type.valid.lowercase()
            assertEquals(type.valid.length, lowercased.length)
            assertNotEquals(type.valid, lowercased)
            if ((type.valid.length * 5) % 8 > 0) {
                assertNotEquals(
                    type.valid.last(), lowercased.last(),
                    "${type.name}: the vector must end in a letter, so that lowercasing leaves " +
                        "the unused-trailing-bits check a character it cannot read"
                )
            }
            assertRejectedByAlphabet(
                "${type.name} in lowercase", lowercased, type.decode, type.isValid
            )
        }
    }

    /**
     * The character whose low byte spells [spelled], which is the form a decoder narrowing
     * characters to bytes would mistake for [spelled] itself.
     */
    private fun aliasing(spelled: Char): Char = (spelled.code + 0x100).toChar()

    private fun labelFor(char: Char): String =
        "U+" + char.code.toString(16).uppercase().padStart(4, '0')

    @Test
    fun testDecodeRejectsCharacterOutsideTheAsciiRange() {
        // A character above the ASCII range whose low byte spells an alphabet character is the
        // one input a byte-level alphabet check cannot see for itself. Each candidate replaces
        // exactly the character its low byte spells, so a decoder that narrowed characters to
        // their low bytes would read the candidate as the vector and hand back one key for two
        // different strings.
        val vector = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
        assertTrue(StrKey.isValidEd25519PublicKey(vector))

        for (spelled in listOf('A', '2', '5')) {
            val at = vector.indexOf(spelled)
            assertTrue(at >= 0, "the vector must contain '$spelled' for this candidate to alias onto it")
            val char = aliasing(spelled)
            assertEquals(spelled, (char.code and 0xFF).toChar(), "${labelFor(char)} must alias onto '$spelled'")

            val candidate = vector.substring(0, at) + char + vector.substring(at + 1)
            assertEquals(vector.length, candidate.length)
            assertNotEquals(vector, candidate)
            assertRejectedByTheAsciiRangeGuard(
                "ed25519 public key carrying ${labelFor(char)}", candidate,
                { StrKey.decodeEd25519PublicKey(it) }, { StrKey.isValidEd25519PublicKey(it) }
            )
        }

        // A character in 128..255 keeps its value when narrowed and is no alphabet character
        // in either form, so unlike the aliasing cases above this one cannot tell the guard
        // from the alphabet check; it documents the rejection either way.
        val at = vector.indexOf('A')
        val latin1 = vector.substring(0, at) + 'Á' + vector.substring(at + 1)
        assertEquals(vector.length, latin1.length)
        assertRejectedByTheAsciiRangeGuard(
            "ed25519 public key carrying U+00C1", latin1,
            { StrKey.decodeEd25519PublicKey(it) }, { StrKey.isValidEd25519PublicKey(it) }
        )
    }

    @Test
    fun testDecodeRejectsAliasingCharacterInTheLastPosition() {
        // The last character is the one the unused-trailing-bits check reads, and it reads the
        // narrowed byte. An aliasing character there has to be rejected before the narrowing,
        // or that check judges a character the string does not contain.
        val vector = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUQ"
        assertTrue(StrKey.isValidMed25519PublicKey(vector))
        assertEquals(1, (vector.length * 5) % 8, "the type must leave unused bits in its last character")

        val char = aliasing(vector.last())
        assertEquals(vector.last(), (char.code and 0xFF).toChar())
        val candidate = vector.substring(0, vector.length - 1) + char
        assertEquals(vector.length, candidate.length)
        assertRejectedByTheAsciiRangeGuard(
            "muxed ed25519 public key ending in ${labelFor(char)}", candidate,
            { StrKey.decodeMed25519PublicKey(it) }, { StrKey.isValidMed25519PublicKey(it) }
        )
    }

    // ========== Decode: canonical encoding ==========
    //
    // A key has exactly one strkey. Padding, characters written after padding and whitespace all
    // produce a string no encoder emits, and every type rejects every one of them. These cases run
    // from common code, so each target answers for the codec it carries and all of them have to
    // give the same answer.
    //
    // Every variant below lengthens the string, so for a type of one fixed encoded length the
    // check that reports it is the encoded-length check rather than the alphabet. For the signed
    // payload, whose legal lengths form a range, the variant stays inside the range and is
    // reported by the first check it fails.

    private class NonCanonicalVariant(
        val description: String,
        val build: (String) -> String,
        /** The check that reports the variant for a type whose encoded lengths form a range. */
        val rangeTypeRejection: String
    )

    private fun nonCanonicalVariants(): List<NonCanonicalVariant> = listOf(
        NonCanonicalVariant("one pad character appended", { "$it=" }, leftoverCharacterRejection),
        NonCanonicalVariant(
            "three pad characters appended", { "$it===" }, nonAlphabetCharacterRejection
        ),
        NonCanonicalVariant(
            "a full pad group appended", { "$it========" }, nonAlphabetCharacterRejection
        ),
        NonCanonicalVariant(
            "characters written after a pad character", { "$it=ZZZZZZZ" },
            nonAlphabetCharacterRejection
        ),
        NonCanonicalVariant(
            "eight trailing spaces", { "$it        " }, nonAlphabetCharacterRejection
        ),
        NonCanonicalVariant(
            "a space inserted mid-string",
            { it.substring(0, 10) + " " + it.substring(10) },
            leftoverCharacterRejection
        ),
        NonCanonicalVariant(
            "a tab inserted mid-string",
            { it.substring(0, 10) + "\t" + it.substring(10) },
            leftoverCharacterRejection
        )
    )

    @Test
    fun testDecodeRejectsNonCanonicalVariantsOfAValidStrKey() {
        for (type in strKeyTypes()) {
            assertTrue(type.isValid(type.valid), "${type.name}: the vector must be valid")
            for (variant in nonCanonicalVariants()) {
                val candidate = variant.build(type.valid)
                val label = "${type.name} with ${variant.description}"
                assertTrue(candidate.length > type.valid.length, "$label: must lengthen the string")

                val failure = assertFailsWith<IllegalArgumentException>("$label: must be rejected") {
                    type.decode(candidate)
                }
                val expected = if (type.fixedEncodedLength != null) {
                    "$encodedLengthRejection, expected ${type.fixedEncodedLength} characters, " +
                        "got ${candidate.length}"
                } else {
                    variant.rangeTypeRejection
                }
                assertEquals(
                    expected, failure.message,
                    "$label: the rejection must name the check that reports it"
                )
                assertFalse(type.isValid(candidate), "$label: isValid must agree with decode")
            }
        }
    }

    // ========== Decode and encode: signed payload framing ==========
    //
    // A signed payload frames its bytes the way the XDR wire form does: a 32-byte ed25519 public
    // key, the declared payload length written as a big-endian four-byte value, and the payload
    // padded with zeros to a four-byte boundary. The declared length has to name a payload the
    // type can carry, the data size has to fit that length exactly, and the padding after the
    // payload has to be zero. Each rejection below names the rule it exercises, so no case can
    // pass on the strength of a different check.

    private val signedPayloadDeclaredLengthRejection = "Invalid signed payload declared length"
    private val signedPayloadSizeRejection = "Invalid signed payload size"
    private val signedPayloadPaddingRejection = "Invalid signed payload padding"

    /**
     * The declared payload length a signed payload's data carries, read big-endian from the four
     * bytes that follow the ed25519 public key.
     */
    private fun declaredPayloadLength(data: ByteArray): Long {
        var length = 0L
        for (index in 32 until 36) {
            length = (length shl 8) or (data[index].toLong() and 0xFF)
        }
        return length
    }

    private fun checkedSignedPayloadData(strKey: String): ByteArray =
        checkedData(strKey, signedPayloadVersionByte, "a signed payload")

    @Test
    fun testDecodeRejectsSignedPayloadDeclaringALengthItCannotCarry() {
        // A 32-byte ed25519 public key, a declared length of 0 and four zero bytes. A signed
        // payload names at least one payload byte, so 0 is not a length it can declare.
        val declaresZero = "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAABO6A"
        val zeroData = checkedSignedPayloadData(declaresZero)
        assertEquals(40, zeroData.size)
        assertEquals(0L, declaredPayloadLength(zeroData))
        assertRejectedByRule(
            "a declared length of 0", declaresZero,
            "$signedPayloadDeclaredLengthRejection, expected between 1 and 64 bytes, got 0",
            { StrKey.decodeSignedPayload(it) }, { StrKey.isValidSignedPayload(it) }
        )

        // A 32-byte ed25519 public key, a declared length of 65 and 64 payload bytes: the largest
        // data a P... strkey can carry, declaring one byte more than the type admits.
        val declaresSixtyFive =
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAABAQCAQDAQCQMBYIBEFAWD" +
                "ANBYHRAEISCMKBKFQXDAMRUGY4DUPB6IBBEIRSIJJGE4UCSKRLFQWS4LZQGEZDGNBVGY3TQOJ2H" +
                "M6D2PR7ICJ4E"
        val sixtyFiveData = checkedSignedPayloadData(declaresSixtyFive)
        assertEquals(100, sixtyFiveData.size)
        assertEquals(65L, declaredPayloadLength(sixtyFiveData))
        assertRejectedByRule(
            "a declared length of 65", declaresSixtyFive,
            "$signedPayloadDeclaredLengthRejection, expected between 1 and 64 bytes, got 65",
            { StrKey.decodeSignedPayload(it) }, { StrKey.isValidSignedPayload(it) }
        )
    }

    @Test
    fun testDecodeRejectsSignedPayloadDeclaringMoreThanThePayloadPresent() {
        // A 32-byte ed25519 public key, a declared length of 64 and four payload bytes. The
        // declared length is one a signed payload can carry, but not one these 40 bytes hold.
        val declaresMoreThanItHolds =
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAABAACAQDAQ3PY"
        val data = checkedSignedPayloadData(declaresMoreThanItHolds)
        assertEquals(40, data.size)
        assertEquals(64L, declaredPayloadLength(data))
        assertRejectedByRule(
            "a declared length of 64 inside 40 bytes",
            declaresMoreThanItHolds,
            "$signedPayloadSizeRejection, a declared length of 64 requires 100 bytes, got 40",
            { StrKey.decodeSignedPayload(it) }, { StrKey.isValidSignedPayload(it) }
        )
    }

    @Test
    fun testDecodeRejectsSignedPayloadWithNonZeroPadding() {
        // A 32-byte ed25519 public key, a declared length of 29, 29 payload bytes and three
        // padding bytes of 0xff. The data size fits the declared length exactly, so the padding
        // is the only thing wrong with it and the only rule that can reject it.
        val nonZeroPadding =
            "PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAOQCAQDAQ" +
                "CQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DX77776K34"
        val data = checkedSignedPayloadData(nonZeroPadding)
        assertEquals(68, data.size)
        assertEquals(29L, declaredPayloadLength(data))
        assertEquals(
            data.size, 36 + (29 + 3) / 4 * 4,
            "the data size must fit the declared length exactly"
        )
        assertTrue(
            data.copyOfRange(36 + 29, data.size).all { it != 0.toByte() },
            "every padding byte must be non-zero"
        )
        assertRejectedByRule(
            "non-zero padding", nonZeroPadding,
            "$signedPayloadPaddingRejection, expected zero at index 65 after a declared length " +
                "of 29, got 255",
            { StrKey.decodeSignedPayload(it) }, { StrKey.isValidSignedPayload(it) }
        )
    }

    @Test
    fun testSignedPayloadAcceptsEveryLegalPayloadLength() {
        for (payloadLength in 1..64) {
            val data = signedPayloadData(payloadLength)
            assertEquals(
                36 + (payloadLength + 3) / 4 * 4, data.size,
                "payload length $payloadLength: framed size"
            )

            val encoded = StrKey.encodeSignedPayload(data)
            assertTrue(encoded.startsWith("P"), "payload length $payloadLength: must encode to a P... strkey")
            assertTrue(
                StrKey.isValidSignedPayload(encoded),
                "payload length $payloadLength: must be valid"
            )
            assertTrue(
                data.contentEquals(StrKey.decodeSignedPayload(encoded)),
                "payload length $payloadLength: must round-trip"
            )
        }
    }

    /**
     * Signed payload data of [dataSize] bytes whose declared length field holds [declaredLength],
     * whether or not the two agree.
     */
    private fun signedPayloadDataDeclaring(declaredLength: Int, dataSize: Int): ByteArray {
        val data = ByteArray(dataSize)
        for (i in 0 until 32) {
            data[i] = (i + 1).toByte()
        }
        data[32] = ((declaredLength ushr 24) and 0xFF).toByte()
        data[33] = ((declaredLength ushr 16) and 0xFF).toByte()
        data[34] = ((declaredLength ushr 8) and 0xFF).toByte()
        data[35] = (declaredLength and 0xFF).toByte()
        return data
    }

    @Test
    fun testEncodeSignedPayloadRejectsFramingItsDecoderRejects() {
        val wellFramed = signedPayloadData(29)
        assertTrue(
            StrKey.isValidSignedPayload(StrKey.encodeSignedPayload(wellFramed)),
            "what the encoder emits must be what the decoder accepts"
        )

        val nonZeroPadding = signedPayloadData(29)
        nonZeroPadding[nonZeroPadding.size - 1] = 0xFF.toByte()

        val cases = listOf(
            Triple(
                "a declared length of 0",
                signedPayloadDataDeclaring(0, 40),
                "$signedPayloadDeclaredLengthRejection, expected between 1 and 64 bytes, got 0"
            ),
            Triple(
                "a declared length of 65",
                signedPayloadDataDeclaring(65, 100),
                "$signedPayloadDeclaredLengthRejection, expected between 1 and 64 bytes, got 65"
            ),
            Triple(
                "a declared length of 64 inside 40 bytes",
                signedPayloadDataDeclaring(64, 40),
                "$signedPayloadSizeRejection, a declared length of 64 requires 100 bytes, got 40"
            ),
            Triple(
                "non-zero padding",
                nonZeroPadding,
                "$signedPayloadPaddingRejection, expected zero at index 67 after a declared " +
                    "length of 29, got 255"
            )
        )

        for ((description, data, rejection) in cases) {
            val failure = assertFailsWith<IllegalArgumentException>("$description: must be rejected") {
                StrKey.encodeSignedPayload(data)
            }
            assertEquals(
                rejection, failure.message,
                "$description: the rejection must name the rule broken"
            )
        }
    }

    // ========== Decode and encode: claimable balance discriminant ==========
    //
    // A claimable balance id is the one case its XDR union declares: a type discriminant of 0
    // followed by a 32-byte hash. Any other discriminant names a type the union does not
    // declare, so neither end of the codec handles it. Each rejection below names the rule it
    // exercises, so no case can pass on the strength of a different check.

    private val claimableBalanceDiscriminantRejection = "Invalid claimable balance discriminant"

    private val claimableBalanceHash = ByteArray(32) { index -> (index * 5 + 1).toByte() }

    private fun checkedClaimableBalanceData(strKey: String): ByteArray =
        checkedData(strKey, claimableBalanceVersionByte, "a claimable balance id")

    @Test
    fun testDecodeRejectsClaimableBalanceDiscriminantOtherThanTheOneDeclared() {
        // Both ends of the values a discriminant byte can hold beyond the one the union
        // declares. Each strkey is built with a valid checksum and the length its type requires,
        // so the discriminant is the only check left that can reject it.
        for (discriminant in listOf(1, 255)) {
            val candidate = craftStrKey(
                claimableBalanceVersionByte,
                byteArrayOf(discriminant.toByte()) + claimableBalanceHash
            )
            assertEquals(58, candidate.length, "discriminant $discriminant: encoded length")
            assertEquals(
                discriminant, checkedClaimableBalanceData(candidate)[0].toInt() and 0xFF,
                "discriminant $discriminant: the vector must carry it"
            )
            assertRejectedByRule(
                "a discriminant of $discriminant", candidate,
                "$claimableBalanceDiscriminantRejection, expected 0, got $discriminant",
                { StrKey.decodeClaimableBalance(it) }, { StrKey.isValidClaimableBalance(it) }
            )
        }
    }

    @Test
    fun testClaimableBalanceRoundTripsUnderTheDiscriminantTheUnionDeclares() {
        val data = byteArrayOf(0) + claimableBalanceHash

        val encoded = StrKey.encodeClaimableBalance(data)
        assertTrue(encoded.startsWith("B"), "must encode to a B... strkey")
        assertEquals(58, encoded.length, "encoded length")
        assertTrue(StrKey.isValidClaimableBalance(encoded), "must be valid")
        assertTrue(data.contentEquals(StrKey.decodeClaimableBalance(encoded)), "must round-trip")

        // The hash on its own names no type and is written under the same discriminant, so the
        // two forms of the same claimable balance id spell one strkey.
        assertEquals(encoded, StrKey.encodeClaimableBalance(claimableBalanceHash))
    }

    @Test
    fun testEncodeClaimableBalanceRejectsADiscriminantItsDecoderRejects() {
        for (discriminant in listOf(1, 255)) {
            val failure = assertFailsWith<IllegalArgumentException>(
                "a discriminant of $discriminant: must be rejected"
            ) {
                StrKey.encodeClaimableBalance(
                    byteArrayOf(discriminant.toByte()) + claimableBalanceHash
                )
            }
            assertEquals(
                "$claimableBalanceDiscriminantRejection, expected 0, got $discriminant",
                failure.message,
                "a discriminant of $discriminant: the rejection must name the rule broken"
            )
        }
    }

    @Test
    fun testEncodeClaimableBalanceReadsTheXdrFormUnderTheDiscriminantTheUnionDeclares() {
        val xdrForm = ByteArray(4) + claimableBalanceHash

        val encoded = StrKey.encodeClaimableBalance(xdrForm)
        assertEquals(
            StrKey.encodeClaimableBalance(claimableBalanceHash), encoded,
            "the XDR form and the bare hash name one balance"
        )
        assertTrue(
            (byteArrayOf(0) + claimableBalanceHash)
                .contentEquals(StrKey.decodeClaimableBalance(encoded)),
            "the XDR form must reach the strkey body the decoder hands back"
        )
    }

    @Test
    fun testEncodeClaimableBalanceRejectsAnXdrFormDiscriminantTheUnionDoesNotDeclare() {
        // Every byte of the wider discriminant is read. The first three carry a value only
        // above the last byte, which a check reading that byte alone would let through.
        val carriedByPrefix = mapOf(
            "01000000" to "0x1000000",
            "00010000" to "0x10000",
            "00000100" to "0x100",
            "00000001" to "0x1",
            "ffffffff" to "0xffffffff"
        )
        for ((prefix, carried) in carriedByPrefix) {
            val failure = assertFailsWith<IllegalArgumentException>(
                "the XDR form prefixed $prefix: must be rejected"
            ) {
                StrKey.encodeClaimableBalance(hexToBytes(prefix) + claimableBalanceHash)
            }
            assertEquals(
                "$claimableBalanceDiscriminantRejection, expected 0, got $carried",
                failure.message,
                "the XDR form prefixed $prefix: the rejection must name the rule broken"
            )
        }
    }
}
