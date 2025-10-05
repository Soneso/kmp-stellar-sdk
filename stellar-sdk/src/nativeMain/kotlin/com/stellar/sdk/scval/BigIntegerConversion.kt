package com.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign

/**
 * Native (iOS/macOS) implementation using manual two's complement conversion.
 *
 * Same approach as JS since native platforms don't have Java's BigInteger.
 */
internal actual fun bytesToBigIntegerSigned(bytes: ByteArray): BigInteger {
    // Same implementation as JS
    val isNegative = (bytes[0].toInt() and 0x80) != 0

    return if (isNegative) {
        val inverted = bytes.map { (it.toInt().inv() and 0xFF).toByte() }.toByteArray()
        val magnitude = BigInteger.fromByteArray(inverted, Sign.POSITIVE) + BigInteger.ONE
        -magnitude
    } else {
        BigInteger.fromByteArray(bytes, Sign.POSITIVE)
    }
}

/**
 * Native implementation of BigInteger to two's complement bytes.
 */
internal actual fun bigIntegerToBytesSigned(
    value: BigInteger,
    byteCount: Int
): ByteArray {
    // Same implementation as JS
    val bytes = value.toByteArray()
    val paddedBytes = ByteArray(byteCount)

    if (value.signum() >= 0) {
        val numBytesToCopy = minOf(bytes.size, byteCount)
        val copyStartIndex = bytes.size - numBytesToCopy
        bytes.copyInto(paddedBytes, byteCount - numBytesToCopy, copyStartIndex, bytes.size)
    } else {
        paddedBytes.fill(0xFF.toByte(), 0, byteCount - bytes.size)
        bytes.copyInto(paddedBytes, byteCount - bytes.size, 0, bytes.size)
    }

    return paddedBytes
}
