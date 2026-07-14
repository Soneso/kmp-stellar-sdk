package com.soneso.stellar.sdk

import com.soneso.stellar.sdk.crypto.getSha256Crypto
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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
     * @return The SDK version string (e.g., "1.0.0")
     */
    fun getSdkVersion(): String {
        return "1.9.0"
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
     * Encodes a byte array to Base64URL format (RFC 4648 Section 5, no padding).
     *
     * Uses URL-safe alphabet: `-` instead of `+`, `_` instead of `/`.
     * Padding `=` characters are stripped.
     *
     * @param data The data to encode
     * @return Base64URL-encoded string without padding
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun base64urlEncode(data: ByteArray): String {
        return Base64.UrlSafe.encode(data).trimEnd('=')
    }

    /**
     * Decodes a Base64URL-encoded string to a byte array.
     *
     * Accepts input with or without padding. Uses URL-safe alphabet:
     * `-` instead of `+`, `_` instead of `/`.
     *
     * @param encoded The Base64URL-encoded string (with or without padding)
     * @return Decoded byte array
     * @throws IllegalArgumentException if the input is not valid Base64URL
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun base64urlDecode(encoded: String): ByteArray {
        val padded = when (encoded.length % 4) {
            2 -> encoded + "=="
            3 -> encoded + "="
            else -> encoded
        }
        return Base64.UrlSafe.decode(padded)
    }

    /**
     * Constant-time byte array comparison to prevent timing side-channel attacks.
     *
     * Unlike [ByteArray.contentEquals], this always compares every byte regardless
     * of where the first mismatch occurs, preventing an attacker from inferring
     * how many leading bytes match by measuring comparison time.
     *
     * @param a First byte array
     * @param b Second byte array
     * @return true if both arrays have identical length and contents, false otherwise
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    /** Number of stroops in one XLM (1 XLM = 10,000,000 stroops). */
    const val STROOPS_PER_XLM = 10_000_000L

    /** Average number of ledgers closed per hour on the Stellar network (~5s per ledger). */
    const val LEDGERS_PER_HOUR = 720

    /** Average number of ledgers closed per day on the Stellar network (~5s per ledger). */
    const val LEDGERS_PER_DAY = 17_280

    /**
     * Converts a decimal XLM amount string to stroops using BigDecimal arithmetic.
     *
     * Parses the string as a decimal number, multiplies by 10,000,000 (stroops per XLM),
     * and rounds to the nearest integer. Supports up to 7 decimal places.
     *
     * @param amount The amount in XLM as a decimal string (must be non-empty and positive)
     * @return The amount in stroops as a BigInteger (1 XLM = 10,000,000 stroops)
     * @throws IllegalArgumentException if the string is empty, not a valid number,
     *         or the resulting value is not positive
     */
    fun amountToStroops(amount: String): BigInteger {
        require(amount.isNotBlank()) { "Amount must not be empty" }
        require(!amount.contains('e', ignoreCase = true)) {
            "Scientific notation is not supported, got: $amount"
        }

        val decimal = try {
            BigDecimal.parseString(amount)
        } catch (e: Exception) {
            throw IllegalArgumentException("Amount is not a valid number, got: $amount", e)
        }

        require(decimal > BigDecimal.ZERO) {
            "Amount must be greater than zero, got: $amount"
        }

        val stroopsDecimal = decimal.multiply(BigDecimal.fromLong(STROOPS_PER_XLM))
        val stroops = stroopsDecimal.roundToDigitPositionAfterDecimalPoint(
            0,
            RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
        ).toBigInteger()

        require(stroops > BigInteger.ZERO) {
            "Amount too small; minimum is 0.0000001 (1 stroop), got: $amount"
        }

        return stroops
    }

    /**
     * Converts stroops (BigInteger) to I128 ScVal.
     *
     * Delegates to [Scv.toInt128] which handles the full 128-bit range correctly,
     * including values larger than Long.MAX_VALUE.
     *
     * @param stroops The amount in stroops (must be within I128 range)
     * @return SCValXdr I128 representation
     * @throws IllegalArgumentException if the value is outside the I128 range
     */
    fun stroopsToI128ScVal(stroops: BigInteger): SCValXdr {
        return Scv.toInt128(stroops)
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

/**
 * Suspends for the specified time using a real wall-clock delay.
 *
 * On JVM, this uses [Thread.sleep] via [Dispatchers.IO] to bypass coroutine virtual time.
 * On JS, this uses `setTimeout` via a [Promise] to bypass the coroutine test scheduler.
 * On Native, this uses [platform.posix.usleep] to ensure real-time delay.
 *
 * This is necessary because [kotlinx.coroutines.delay] can be skipped by
 * [kotlinx.coroutines.test.runTest]'s virtual time scheduler, which breaks
 * polling loops that need to wait for real network responses.
 *
 * @param timeMillis The delay duration in milliseconds
 */
internal expect suspend fun platformDelay(timeMillis: Long)

/**
 * Provides mutual exclusion for concurrent access to shared mutable state.
 *
 * On JVM, this delegates to [kotlin.synchronized] for proper thread synchronization.
 * On JS, this simply executes the block directly since JavaScript is single-threaded.
 * On Native, this uses a single process-global, NON-REENTRANT busy-wait spinlock that
 * ignores the [lock] argument — every caller in the SDK contends on the same lock.
 *
 * CONTRACT (load-bearing on Native):
 * - [block] must NEVER call [platformSynchronized] again, directly or transitively
 *   (e.g. by emitting an event or touching another synchronized registry). On Native
 *   this deadlocks the calling thread with no diagnostic; on JVM (reentrant monitor)
 *   and JS (no-op) the same code works, so the deadlock surfaces only on iOS/macOS.
 * - [block] must be short and must not suspend or block: waiters busy-spin on Native,
 *   so a slow block burns CPU on every waiting thread.
 *
 * @param lock The monitor object on JVM; ignored by the Native implementation
 * @param block The block to execute under mutual exclusion
 * @return The result of executing [block]
 */
internal expect fun <T> platformSynchronized(lock: Any, block: () -> T): T
