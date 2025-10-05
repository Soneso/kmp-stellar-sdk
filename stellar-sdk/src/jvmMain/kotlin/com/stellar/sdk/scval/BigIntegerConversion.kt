package com.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger as KotlinBigInteger
import java.math.BigInteger as JavaBigInteger

/**
 * JVM implementation using Java's BigInteger which natively supports
 * two's complement byte array interpretation.
 */
internal actual fun bytesToBigIntegerSigned(bytes: ByteArray): KotlinBigInteger {
    // Use Java BigInteger constructor that interprets bytes as two's complement
    val javaBigInt = JavaBigInteger(bytes)

    // Convert to Kotlin BigInteger
    return KotlinBigInteger.parseString(javaBigInt.toString())
}

/**
 * JVM implementation using Java's BigInteger for two's complement conversion.
 */
internal actual fun bigIntegerToBytesSigned(
    value: KotlinBigInteger,
    byteCount: Int
): ByteArray {
    // Convert Kotlin BigInteger to Java BigInteger
    val javaBigInt = JavaBigInteger(value.toString())

    // Get two's complement byte array
    val bytes = javaBigInt.toByteArray()

    // Pad or trim to exact size
    return when {
        bytes.size == byteCount -> bytes
        bytes.size < byteCount -> {
            // Pad with sign extension
            val padded = ByteArray(byteCount)
            val fillByte: Byte = if (javaBigInt.signum() < 0) 0xFF.toByte() else 0x00
            padded.fill(fillByte, 0, byteCount - bytes.size)
            bytes.copyInto(padded, byteCount - bytes.size)
            padded
        }
        else -> {
            // Trim excess sign bytes
            bytes.copyOfRange(bytes.size - byteCount, bytes.size)
        }
    }
}
