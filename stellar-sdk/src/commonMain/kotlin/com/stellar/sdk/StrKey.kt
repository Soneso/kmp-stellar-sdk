package com.stellar.sdk

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