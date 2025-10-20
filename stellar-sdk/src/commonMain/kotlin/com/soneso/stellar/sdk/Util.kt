package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.crypto.getSha256Crypto
import kotlin.math.pow

/**
 * Utility functions for the Stellar SDK.
 *
 * Provides public utility functions for SDK version information and internal utilities
 * for common operations used throughout the SDK.
 */
object Util {
    /**
     * Returns the version of the Stellar SDK.
     *
     * This version string is used for client identification in HTTP headers
     * (X-Client-Version) to help Stellar server operators track SDK usage
     * and identify SDK-specific issues.
     *
     * Note: Currently hardcoded to match the version in build.gradle.kts.
     * In future versions, this could be generated at build time from gradle.properties.
     *
     * @return The SDK version string (e.g., "0.1.0-SNAPSHOT")
     */
    fun getSdkVersion(): String {
        return "0.1.0-SNAPSHOT"
    }

    /**
     * Pads a byte array to the specified length with null bytes (0x00).
     * If the input array is already longer than or equal to the specified length,
     * only the first [length] bytes are returned.
     *
     * Note: This is an internal utility function and should not be used directly by
     * SDK consumers. The API may change without notice.
     *
     * @param bytes The input byte array
     * @param length The desired length
     * @return A byte array of exactly [length] bytes
     */
    internal fun paddedByteArray(bytes: ByteArray, length: Int): ByteArray {
        require(length >= 0) { "Length must be non-negative" }
        val result = ByteArray(length) { 0 }
        val copyLength = minOf(bytes.size, length)
        bytes.copyInto(result, 0, 0, copyLength)
        return result
    }

    /**
     * Pads a string to the specified length with null bytes (0x00).
     * The string is first converted to a byte array using UTF-8 encoding.
     *
     * Note: This is an internal utility function and should not be used directly by
     * SDK consumers. The API may change without notice.
     *
     * @param string The input string
     * @param length The desired length
     * @return A byte array of exactly [length] bytes
     */
    internal fun paddedByteArray(string: String, length: Int): ByteArray {
        return paddedByteArray(string.encodeToByteArray(), length)
    }

    /**
     * Converts a null-padded byte array to a string, removing trailing null bytes.
     * The conversion uses UTF-8 encoding.
     *
     * Note: This is an internal utility function and should not be used directly by
     * SDK consumers. The API may change without notice.
     *
     * @param bytes The null-padded byte array
     * @return The string with trailing null bytes removed
     */
    internal fun paddedByteArrayToString(bytes: ByteArray): String {
        // Find the first null byte
        val nullIndex = bytes.indexOfFirst { it == 0.toByte() }
        val trimmedBytes = if (nullIndex >= 0) {
            bytes.copyOfRange(0, nullIndex)
        } else {
            bytes
        }
        return trimmedBytes.decodeToString()
    }

    /**
     * Converts a byte array to a hexadecimal string.
     *
     * Note: This is an internal utility function and should not be used directly by
     * SDK consumers. The API may change without notice.
     *
     * @param bytes The byte array to convert
     * @return A lowercase hexadecimal string representation
     */
    internal fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    /**
     * Converts a hexadecimal string to a byte array.
     *
     * Note: This is an internal utility function and should not be used directly by
     * SDK consumers. The API may change without notice.
     *
     * @param hex The hexadecimal string (case-insensitive)
     * @return A byte array
     * @throws IllegalArgumentException if the hex string has odd length or contains invalid characters
     */
    internal fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.lowercase()
        require(cleanHex.length % 2 == 0) { "Hex string must have even length" }

        return ByteArray(cleanHex.length / 2) { i ->
            val index = i * 2
            val byteString = cleanHex.substring(index, index + 2)
            byteString.toInt(16).toByte()
        }
    }

    /**
     * Returns SHA-256 hash of the input data.
     *
     * Note: This is an internal utility function and should not be used directly by
     * SDK consumers. The API may change without notice.
     *
     * @param data The data to hash
     * @return The 32-byte SHA-256 hash
     */
    internal suspend fun hash(data: ByteArray): ByteArray {
        return getSha256Crypto().hash(data)
    }

    /**
     * One Stroop is the smallest unit of Stellar's native asset (Lumen).
     * One Lumen = 10^7 stroops.
     */
    private const val ONE = 10_000_000L // 10^7

    /**
     * Converts a stroop amount (Long) to a decimal amount string.
     *
     * Stroops are the smallest unit of Stellar's native asset. One Lumen = 10^7 stroops.
     * The resulting string will have up to 7 decimal places.
     *
     * Note: This is an internal utility function and should not be used directly by
     * SDK consumers. The API may change without notice.
     *
     * @param value The amount in stroops
     * @return The amount as a decimal string (e.g., "10.0000000" for 100000000 stroops)
     *
     * ## Example
     * ```kotlin
     * toAmountString(10_000_000L)  // "1.0000000"
     * toAmountString(15_000_000L)  // "1.5000000"
     * ```
     */
    internal fun toAmountString(value: Long): String {
        val wholePart = value / ONE
        val fractionalPart = value % ONE
        return "$wholePart.${fractionalPart.toString().padStart(7, '0')}"
    }

    /**
     * Converts a decimal amount string to stroops (Long).
     *
     * The amount must have at most 7 decimal places (stroop precision).
     * One Lumen = 10^7 stroops.
     *
     * Note: This is an internal utility function and should not be used directly by
     * SDK consumers. The API may change without notice.
     *
     * @param value The amount as a decimal string (e.g., "1.5", "10.0000000")
     * @return The amount in stroops
     * @throws IllegalArgumentException if the amount format is invalid or exceeds 7 decimal places
     *
     * ## Example
     * ```kotlin
     * toStroops("1.0000000")  // 10_000_000L
     * toStroops("1.5")        // 15_000_000L
     * ```
     */
    internal fun toStroops(value: String): Long {
        require(value.isNotBlank()) { "Amount cannot be blank" }

        // Parse the decimal value
        val parts = value.split(".")
        require(parts.size <= 2) { "Invalid amount format: '$value'" }

        val wholePart = try {
            if (parts[0].isEmpty()) 0L else parts[0].toLong()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("Invalid amount format: '$value'", e)
        }

        val fractionalPart = if (parts.size == 2) {
            val fraction = parts[1]
            require(fraction.length <= 7) {
                "Amount cannot have more than 7 decimal places, got ${fraction.length}"
            }
            // Pad to 7 digits and parse
            val paddedFraction = fraction.padEnd(7, '0')
            try {
                paddedFraction.toLong()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid amount format: '$value'", e)
            }
        } else {
            0L
        }

        return wholePart * ONE + fractionalPart
    }

    /**
     * Formats an amount string to have exactly 7 decimal places.
     *
     * Note: This is an internal utility function and should not be used directly by
     * SDK consumers. The API may change without notice.
     *
     * @param value The amount string
     * @return The amount string with exactly 7 decimal places
     * @throws IllegalArgumentException if the scale exceeds 7 decimal places
     */
    internal fun formatAmountScale(value: String): String {
        require(value.isNotBlank()) { "Amount cannot be blank" }

        val parts = value.split(".")
        require(parts.size <= 2) { "Invalid amount format: '$value'" }

        val wholePart = parts[0]
        val fractionalPart = if (parts.size == 2) {
            require(parts[1].length <= 7) {
                "The scale of the amount must be less than or equal to 7, got ${parts[1].length}"
            }
            parts[1].padEnd(7, '0')
        } else {
            "0000000"
        }

        return "$wholePart.$fractionalPart"
    }
}

/**
 * Returns the current time in milliseconds since the Unix epoch (January 1, 1970, 00:00:00 UTC).
 *
 * This is a platform-specific implementation used internally by the SDK for timeouts and
 * exponential backoff calculations.
 *
 * @return Current time in milliseconds
 */
internal expect fun currentTimeMillis(): Long
