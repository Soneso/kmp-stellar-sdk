package com.soneso.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.util.fromTwosComplementByteArray
import com.ionspin.kotlin.bignum.integer.util.toTwosComplementByteArray

/**
 * Converts big-endian two's complement [bytes] to a [BigInteger].
 *
 * @throws IllegalArgumentException if [bytes] is empty, because an empty array encodes no value.
 */
internal fun bytesToBigIntegerSigned(bytes: ByteArray): BigInteger {
    require(bytes.isNotEmpty()) { "bytes must not be empty" }
    return BigInteger.fromTwosComplementByteArray(bytes)
}

/**
 * Converts [value] to big-endian two's complement bytes of exactly [byteCount] bytes.
 *
 * A shorter encoding is sign-extended on the left; a longer one keeps its low [byteCount] bytes.
 *
 * @throws IllegalArgumentException if [byteCount] is zero or negative.
 */
internal fun bigIntegerToBytesSigned(value: BigInteger, byteCount: Int): ByteArray {
    require(byteCount > 0) { "byteCount must be positive, got $byteCount" }
    val twosComplementBytes = value.toTwosComplementByteArray()
    val paddedBytes = ByteArray(byteCount)
    val fillByte: Byte = if (value.signum() < 0) 0xFF.toByte() else 0x00

    if (twosComplementBytes.size <= byteCount) {
        // Pad with sign extension bytes on the left
        paddedBytes.fill(fillByte, 0, byteCount - twosComplementBytes.size)
        twosComplementBytes.copyInto(paddedBytes, byteCount - twosComplementBytes.size)
    } else {
        // Keep the low byteCount bytes
        twosComplementBytes.copyInto(
            paddedBytes, 0,
            twosComplementBytes.size - byteCount, twosComplementBytes.size
        )
    }

    return paddedBytes
}
