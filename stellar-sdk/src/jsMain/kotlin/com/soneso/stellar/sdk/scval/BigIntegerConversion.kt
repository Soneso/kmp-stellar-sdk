package com.soneso.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.util.fromTwosComplementByteArray
import com.ionspin.kotlin.bignum.integer.util.toTwosComplementByteArray

/**
 * JavaScript implementation using ionspin's two's complement conversion utilities.
 *
 * JS doesn't have Java's BigInteger, so we use ionspin's built-in
 * [toTwosComplementByteArray] / [fromTwosComplementByteArray] which correctly
 * handle sign encoding in the byte representation.
 */
internal actual fun bytesToBigIntegerSigned(bytes: ByteArray): BigInteger {
    return BigInteger.fromTwosComplementByteArray(bytes)
}

/**
 * JavaScript implementation of BigInteger to two's complement bytes.
 */
internal actual fun bigIntegerToBytesSigned(
    value: BigInteger,
    byteCount: Int
): ByteArray {
    val twosComplementBytes = value.toTwosComplementByteArray()
    val paddedBytes = ByteArray(byteCount)
    val fillByte: Byte = if (value.signum() < 0) 0xFF.toByte() else 0x00

    if (twosComplementBytes.size <= byteCount) {
        // Pad with sign extension bytes on the left
        paddedBytes.fill(fillByte, 0, byteCount - twosComplementBytes.size)
        twosComplementBytes.copyInto(paddedBytes, byteCount - twosComplementBytes.size)
    } else {
        // Trim excess sign extension bytes from the left
        twosComplementBytes.copyInto(
            paddedBytes, 0,
            twosComplementBytes.size - byteCount, twosComplementBytes.size
        )
    }

    return paddedBytes
}
