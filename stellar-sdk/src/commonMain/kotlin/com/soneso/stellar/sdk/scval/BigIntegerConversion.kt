package com.soneso.stellar.sdk.scval

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Convert bytes in two's complement format to BigInteger.
 *
 * This is platform-specific because different BigInteger implementations
 * handle byte array interpretation differently.
 */
internal expect fun bytesToBigIntegerSigned(bytes: ByteArray): BigInteger

/**
 * Convert BigInteger to bytes in two's complement format.
 *
 * This is platform-specific to ensure consistent behavior across platforms.
 */
internal expect fun bigIntegerToBytesSigned(value: BigInteger, byteCount: Int): ByteArray
