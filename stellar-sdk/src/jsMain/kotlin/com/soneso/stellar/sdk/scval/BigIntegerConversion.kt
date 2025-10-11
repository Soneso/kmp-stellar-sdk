package com.soneso.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

/**
 * JavaScript implementation using manual two's complement conversion.
 *
 * JS doesn't have Java's BigInteger, so we implement the conversion manually.
 */
internal actual fun bytesToBigIntegerSigned(bytes: ByteArray): BigInteger {
    // Check if negative (high bit set)
    val isNegative = (bytes[0].toInt() and 0x80) != 0

    return if (isNegative) {
        // Two's complement: -(~bytes + 1)
        val inverted = bytes.map { (it.toInt().inv() and 0xFF).toByte() }.toByteArray()
        val magnitude = BigInteger.fromByteArray(inverted, Sign.POSITIVE) + BigInteger.ONE
        -magnitude
    } else {
        // Positive: direct conversion
        BigInteger.fromByteArray(bytes, Sign.POSITIVE)
    }
}

/**
 * JavaScript implementation of BigInteger to two's complement bytes.
 */
internal actual fun bigIntegerToBytesSigned(
    value: BigInteger,
    byteCount: Int
): ByteArray {
    val bytes = value.toByteArray()
    val paddedBytes = ByteArray(byteCount)

    if (value.signum() >= 0) {
        // Positive: pad with zeros on the left
        val numBytesToCopy = minOf(bytes.size, byteCount)
        val copyStartIndex = bytes.size - numBytesToCopy
        bytes.copyInto(paddedBytes, byteCount - numBytesToCopy, copyStartIndex, bytes.size)
    } else {
        // Negative: pad with 0xFF on the left
        paddedBytes.fill(0xFF.toByte(), 0, byteCount - bytes.size)
        bytes.copyInto(paddedBytes, byteCount - bytes.size, 0, bytes.size)
    }

    return paddedBytes
}
