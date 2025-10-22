package com.soneso.stellar.sdk

import kotlin.experimental.and

/**
 * Platform-specific Base32 codec interface.
 */
internal expect object Base32Codec {
    fun encode(data: ByteArray): ByteArray
    fun decode(data: ByteArray): ByteArray
    fun isInAlphabet(data: ByteArray): Boolean
}

/**
 * StrKey is a helper class for encoding and decoding Stellar keys to/from strings.
 * Stellar uses a base32 encoding with checksums called "strkey" for human-readable keys.
 */
object StrKey {

    private enum class VersionByte(val value: Byte) {
        ACCOUNT_ID((6 shl 3).toByte()),           // G
        MED25519_PUBLIC_KEY((12 shl 3).toByte()), // M
        SEED((18 shl 3).toByte()),                // S
        PRE_AUTH_TX((19 shl 3).toByte()),         // T
        SHA256_HASH((23 shl 3).toByte()),         // X
        SIGNED_PAYLOAD((15 shl 3).toByte()),      // P
        CONTRACT((2 shl 3).toByte()),             // C
        LIQUIDITY_POOL((11 shl 3).toByte()),      // L
        CLAIMABLE_BALANCE((1 shl 3).toByte());    // B

        companion object {
            fun fromValue(value: Byte): VersionByte? = entries.find { it.value == value }
        }
    }

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    // Decoding table for base32
    private val decodingTable: ByteArray = ByteArray(256) { 0xff.toByte() }.apply {
        BASE32_ALPHABET.forEachIndexed { index, char ->
            this[char.code] = index.toByte()
        }
    }

    /**
     * Encodes raw bytes to strkey ed25519 public key (G...)
     */
    fun encodeEd25519PublicKey(data: ByteArray): String {
        require(data.size == 32) { "Public key must be 32 bytes" }
        return encodeCheck(VersionByte.ACCOUNT_ID, data).concatToString()
    }

    /**
     * Decodes strkey ed25519 public key (G...) to raw bytes
     */
    fun decodeEd25519PublicKey(data: String): ByteArray {
        return decodeCheck(VersionByte.ACCOUNT_ID, data.toCharArray())
    }

    /**
     * Checks validity of Stellar account ID (G...)
     */
    fun isValidEd25519PublicKey(accountId: String): Boolean {
        return try {
            decodeEd25519PublicKey(accountId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey ed25519 secret seed (S...)
     */
    fun encodeEd25519SecretSeed(data: ByteArray): CharArray {
        require(data.size == 32) { "Secret seed must be 32 bytes" }
        return encodeCheck(VersionByte.SEED, data)
    }

    /**
     * Decodes strkey ed25519 secret seed (S...) to raw bytes
     */
    fun decodeEd25519SecretSeed(data: CharArray): ByteArray {
        return decodeCheck(VersionByte.SEED, data)
    }

    /**
     * Checks validity of seed (S...)
     */
    fun isValidEd25519SecretSeed(seed: CharArray): Boolean {
        return try {
            decodeEd25519SecretSeed(seed)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey muxed ed25519 public key (M...)
     */
    fun encodeMed25519PublicKey(data: ByteArray): String {
        require(data.size == 40) { "Muxed public key must be 40 bytes" }
        return encodeCheck(VersionByte.MED25519_PUBLIC_KEY, data).concatToString()
    }

    /**
     * Decodes strkey muxed ed25519 public key (M...) to raw bytes
     */
    fun decodeMed25519PublicKey(data: String): ByteArray {
        return decodeCheck(VersionByte.MED25519_PUBLIC_KEY, data.toCharArray())
    }

    /**
     * Checks validity of muxed ed25519 public key (M...)
     *
     * Muxed accounts (M...) are virtual accounts that share the same underlying ed25519 key
     * but have different IDs. They are used for memo-less payments as defined in SEP-0023.
     *
     * @param med25519PublicKey The muxed public key to check
     * @return true if the given muxed public key is valid, false otherwise
     * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0023.md">SEP-0023</a>
     */
    fun isValidMed25519PublicKey(med25519PublicKey: String): Boolean {
        return try {
            decodeMed25519PublicKey(med25519PublicKey)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey pre-authorized transaction hash (T...)
     */
    fun encodePreAuthTx(data: ByteArray): String {
        require(data.size == 32) { "Pre-auth transaction hash must be 32 bytes" }
        return encodeCheck(VersionByte.PRE_AUTH_TX, data).concatToString()
    }

    /**
     * Decodes strkey pre-authorized transaction hash (T...) to raw bytes
     */
    fun decodePreAuthTx(data: String): ByteArray {
        return decodeCheck(VersionByte.PRE_AUTH_TX, data.toCharArray())
    }

    /**
     * Checks validity of pre-authorized transaction hash (T...)
     */
    fun isValidPreAuthTx(preAuthTx: String): Boolean {
        return try {
            decodePreAuthTx(preAuthTx)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey SHA-256 hash (X...)
     */
    fun encodeSha256Hash(data: ByteArray): String {
        require(data.size == 32) { "SHA-256 hash must be 32 bytes" }
        return encodeCheck(VersionByte.SHA256_HASH, data).concatToString()
    }

    /**
     * Decodes strkey SHA-256 hash (X...) to raw bytes
     */
    fun decodeSha256Hash(data: String): ByteArray {
        return decodeCheck(VersionByte.SHA256_HASH, data.toCharArray())
    }

    /**
     * Checks validity of SHA-256 hash (X...)
     */
    fun isValidSha256Hash(sha256Hash: String): Boolean {
        return try {
            decodeSha256Hash(sha256Hash)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey signed payload (P...)
     */
    fun encodeSignedPayload(data: ByteArray): String {
        require(data.size in (32 + 4 + 4)..(32 + 4 + 64)) {
            "Signed payload must be between 40 and 100 bytes, got ${data.size}"
        }
        return encodeCheck(VersionByte.SIGNED_PAYLOAD, data).concatToString()
    }

    /**
     * Decodes strkey signed payload (P...) to raw bytes
     */
    fun decodeSignedPayload(data: String): ByteArray {
        return decodeCheck(VersionByte.SIGNED_PAYLOAD, data.toCharArray())
    }

    /**
     * Checks validity of signed payload (P...)
     */
    fun isValidSignedPayload(signedPayload: String): Boolean {
        return try {
            decodeSignedPayload(signedPayload)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey contract address (C...)
     */
    fun encodeContract(data: ByteArray): String {
        require(data.size == 32) { "Contract address must be 32 bytes" }
        return encodeCheck(VersionByte.CONTRACT, data).concatToString()
    }

    /**
     * Decodes strkey contract address (C...) to raw bytes
     */
    fun decodeContract(data: String): ByteArray {
        return decodeCheck(VersionByte.CONTRACT, data.toCharArray())
    }

    /**
     * Checks validity of contract address (C...)
     */
    fun isValidContract(address: String): Boolean {
        return try {
            decodeContract(address)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey liquidity pool ID (L...)
     */
    fun encodeLiquidityPool(data: ByteArray): String {
        require(data.size == 32) { "Liquidity pool ID must be 32 bytes" }
        return encodeCheck(VersionByte.LIQUIDITY_POOL, data).concatToString()
    }

    /**
     * Decodes strkey liquidity pool ID (L...) to raw bytes
     */
    fun decodeLiquidityPool(data: String): ByteArray {
        return decodeCheck(VersionByte.LIQUIDITY_POOL, data.toCharArray())
    }

    /**
     * Checks validity of liquidity pool ID (L...)
     */
    fun isValidLiquidityPool(liquidityPoolId: String): Boolean {
        return try {
            decodeLiquidityPool(liquidityPoolId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Encodes raw bytes to strkey claimable balance ID (B...)
     *
     * Accepts both 32-byte and 33-byte inputs:
     * - 32 bytes: Just the hash - automatically prepends V0 type discriminant (0x00)
     * - 33 bytes: Type (1 byte) + hash (32 bytes) - used as-is
     *
     * This handles RPC responses that may return only the 32-byte hash without the type byte.
     */
    fun encodeClaimableBalance(data: ByteArray): String {
        val fullData = when (data.size) {
            32 -> {
                // Type is missing, prepend V0 type discriminant (0x00)
                byteArrayOf(0x00) + data
            }
            33 -> {
                // Already has type byte, use as-is
                data
            }
            else -> {
                throw IllegalArgumentException(
                    "Claimable balance ID must be 32 bytes (hash only) or 33 bytes (type + hash), got ${data.size} bytes"
                )
            }
        }
        return encodeCheck(VersionByte.CLAIMABLE_BALANCE, fullData).concatToString()
    }

    /**
     * Decodes strkey claimable balance ID (B...) to raw bytes
     */
    fun decodeClaimableBalance(data: String): ByteArray {
        return decodeCheck(VersionByte.CLAIMABLE_BALANCE, data.toCharArray())
    }

    /**
     * Checks validity of claimable balance ID (B...)
     */
    fun isValidClaimableBalance(claimableBalanceId: String): Boolean {
        return try {
            decodeClaimableBalance(claimableBalanceId)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun encodeCheck(versionByte: VersionByte, data: ByteArray): CharArray {
        val payload = byteArrayOf(versionByte.value) + data
        val checksum = calculateChecksum(payload)
        val unencoded = payload + checksum
        val encoded = Base32Codec.encode(unencoded)

        // Remove padding
        val unpaddedLength = encoded.indexOfFirst { it == '='.code.toByte() }.let {
            if (it == -1) encoded.size else it
        }

        return encoded.take(unpaddedLength).map { it.toInt().toChar() }.toCharArray()
    }

    private fun decodeCheck(versionByte: VersionByte, encoded: CharArray): ByteArray {
        require(encoded.size >= 5) { "Encoded char array must have a length of at least 5" }

        val bytes = encoded.map { it.code.toByte() }.toByteArray()

        // Validate no leftover character
        val leftoverBits = (bytes.size * 5) % 8
        require(leftoverBits < 5) { "Encoded char array has leftover character" }

        // Validate unused bits are zero
        if (leftoverBits > 0) {
            val lastChar = bytes.last()
            val decodedLastChar = decodingTable[lastChar.toInt() and 0xFF]
            val leftoverBitsMask = (0x0f shr (4 - leftoverBits)).toByte()
            require((decodedLastChar and leftoverBitsMask) == 0.toByte()) { "Unused bits should be set to 0" }
        }

        val decoded = base32Decode(bytes)
        val decodedVersionByte = decoded[0]
        val decodedVersion = VersionByte.fromValue(decodedVersionByte)
            ?: throw IllegalArgumentException("Version byte is invalid")

        val payload = decoded.copyOfRange(0, decoded.size - 2)
        val data = payload.copyOfRange(1, payload.size)
        val checksum = decoded.copyOfRange(decoded.size - 2, decoded.size)

        // Validate data length
        when (decodedVersion) {
            VersionByte.SIGNED_PAYLOAD -> {
                require(data.size in (32 + 4 + 4)..(32 + 4 + 64)) {
                    "Invalid data length, the length should be between 40 and 100 bytes, got ${data.size}"
                }
            }
            VersionByte.MED25519_PUBLIC_KEY -> {
                require(data.size == 40) { "Invalid data length, expected 40 bytes, got ${data.size}" }
            }
            VersionByte.CLAIMABLE_BALANCE -> {
                require(data.size == 33) { "Invalid data length, expected 33 bytes, got ${data.size}" }
            }
            else -> {
                require(data.size == 32) { "Invalid data length, expected 32 bytes, got ${data.size}" }
            }
        }

        require(decodedVersion == versionByte) { "Version byte mismatch" }

        val expectedChecksum = calculateChecksum(payload)
        require(expectedChecksum.contentEquals(checksum)) { "Checksum invalid" }

        return data
    }

    /**
     * Calculates CRC16-XModem checksum
     */
    private fun calculateChecksum(bytes: ByteArray): ByteArray {
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

        // Return little-endian
        return byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
    }


    private fun base32Decode(data: ByteArray): ByteArray {
        // Validate all characters are in alphabet
        require(Base32Codec.isInAlphabet(data)) {
            "Invalid base32 encoded string"
        }

        return Base32Codec.decode(data)
    }
}
