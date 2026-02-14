package com.soneso.stellar.sdk.sep.sep05

/**
 * Hex encoding and decoding utilities for entropy handling in BIP-39.
 *
 * This codec provides conversion between byte arrays and hexadecimal string
 * representations, which is required for entropy manipulation in mnemonic
 * generation and validation.
 *
 * Features:
 * - Encoding produces lowercase hex strings
 * - Decoding is case-insensitive
 * - Decoding ignores whitespace for convenience
 * - Strict validation of hex characters
 *
 * Example usage:
 * ```kotlin
 * // Encoding
 * val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
 * val hex = HexCodec.encode(bytes) // "deadbeef"
 *
 * // Decoding
 * val decoded = HexCodec.decode("DEAD BEEF") // [0xDE, 0xAD, 0xBE, 0xEF]
 * ```
 */
internal object HexCodec {
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /**
     * Encodes a byte array to a lowercase hexadecimal string.
     *
     * Each byte is converted to two hex characters. The output is always
     * lowercase and has exactly twice the length of the input array.
     *
     * @param bytes The bytes to encode. May be empty.
     * @return Lowercase hex string representation. Returns empty string for empty input.
     *
     * Example:
     * ```kotlin
     * HexCodec.encode(byteArrayOf()) // ""
     * HexCodec.encode(byteArrayOf(0x00)) // "00"
     * HexCodec.encode(byteArrayOf(0xFF.toByte())) // "ff"
     * HexCodec.encode(byteArrayOf(0x12, 0x34)) // "1234"
     * ```
     */
    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        val result = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val i = byte.toInt() and 0xFF
            result.append(HEX_CHARS[i shr 4])
            result.append(HEX_CHARS[i and 0x0F])
        }
        return result.toString()
    }

    /**
     * Decodes a hexadecimal string to a byte array.
     *
     * The input is normalized before decoding:
     * - Converted to lowercase
     * - All whitespace (spaces, tabs, newlines) is removed
     *
     * After normalization, the string must have even length and contain
     * only valid hex characters (0-9, a-f).
     *
     * @param hex The hex string to decode. May be empty. Case-insensitive.
     *            Whitespace is ignored.
     * @return Decoded byte array. Returns empty array for empty input.
     * @throws IllegalArgumentException if:
     *         - The normalized string has odd length
     *         - The string contains invalid hex characters
     *
     * Example:
     * ```kotlin
     * HexCodec.decode("") // byteArrayOf()
     * HexCodec.decode("00") // byteArrayOf(0x00)
     * HexCodec.decode("FF") // byteArrayOf(0xFF.toByte())
     * HexCodec.decode("dead beef") // byteArrayOf(0xDE, 0xAD, 0xBE, 0xEF)
     * HexCodec.decode("DEAD\nBEEF") // byteArrayOf(0xDE, 0xAD, 0xBE, 0xEF)
     * ```
     */
    fun decode(hex: String): ByteArray {
        // Normalize: lowercase and remove all whitespace
        val normalized = hex.lowercase().replace(Regex("\\s"), "")

        if (normalized.isEmpty()) return ByteArray(0)

        require(normalized.length % 2 == 0) {
            "Hex string must have even length after removing whitespace, got ${normalized.length}"
        }

        return ByteArray(normalized.length / 2) { i ->
            val high = hexCharToInt(normalized[i * 2])
            val low = hexCharToInt(normalized[i * 2 + 1])
            ((high shl 4) or low).toByte()
        }
    }

    /**
     * Converts a single hex character to its integer value (0-15).
     *
     * @param char The hex character (must be 0-9 or a-f, lowercase)
     * @return Integer value 0-15
     * @throws IllegalArgumentException if char is not a valid hex character
     */
    private fun hexCharToInt(char: Char): Int {
        return when (char) {
            in '0'..'9' -> char - '0'
            in 'a'..'f' -> char - 'a' + 10
            else -> throw IllegalArgumentException(
                "Invalid hex character: '$char' (code: ${char.code})"
            )
        }
    }
}
