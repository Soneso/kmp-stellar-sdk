@file:OptIn(ExperimentalForeignApi::class)

package com.soneso.stellar.sdk.sep.sep05.crypto

import kotlinx.cinterop.*
import libsodium.*
import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCompatibilityMapping

/**
 * Native (iOS/macOS) implementation of SEP-5 cryptographic primitives.
 *
 * Uses libsodium for cryptographic operations and Foundation for Unicode normalization:
 *
 * - **Random**: libsodium randombytes_buf() using system CSPRNG (arc4random_buf)
 * - **SHA-256**: libsodium crypto_hash_sha256
 * - **HMAC-SHA512**: libsodium crypto_auth_hmacsha512
 * - **PBKDF2**: Custom implementation using HMAC-SHA512
 * - **NFKD**: Foundation NSString.decomposedStringWithCompatibilityMapping
 *
 * All cryptographic operations use audited, constant-time implementations.
 */

// Ensure libsodium is initialized
private val sodiumInitialized: Boolean = run {
    val result = sodium_init()
    if (result < 0) {
        throw IllegalStateException("Failed to initialize libsodium")
    }
    true
}

/**
 * Generates cryptographically secure random bytes using libsodium.
 *
 * Uses randombytes_buf() which is backed by the system CSPRNG (arc4random_buf on Apple platforms).
 */
internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "Size must be positive" }

    return UByteArray(size).apply {
        usePinned { pinned ->
            randombytes_buf(pinned.addressOf(0), size.toULong())
        }
    }.asByteArray()
}

/**
 * Computes SHA-256 hash using libsodium's crypto_hash_sha256.
 */
@OptIn(UnsafeNumber::class)
internal actual suspend fun sha256(data: ByteArray): ByteArray {
    return memScoped {
        val output = allocArray<UByteVar>(32)

        if (data.isEmpty()) {
            val result = crypto_hash_sha256(output, null, 0u)
            require(result == 0) { "SHA-256 hash failed" }
        } else {
            data.usePinned { pinnedData ->
                val result = crypto_hash_sha256(
                    output,
                    pinnedData.addressOf(0).reinterpret(),
                    data.size.toULong()
                )
                require(result == 0) { "SHA-256 hash failed" }
            }
        }

        ByteArray(32) { i -> output[i].toByte() }
    }
}

/**
 * PBKDF2 key derivation using HMAC-SHA512.
 *
 * Implements PBKDF2 as specified in RFC 8018 using libsodium's HMAC-SHA512.
 * This is used for BIP-39 seed generation from mnemonic phrases.
 *
 * @param password The password bytes (normalized mnemonic)
 * @param salt The salt bytes ("mnemonic" + passphrase)
 * @param iterations Number of PBKDF2 iterations (2048 for BIP-39)
 * @param keyLength Length of derived key in bytes (64 for BIP-39)
 * @return Derived key bytes
 */
internal actual suspend fun pbkdf2HmacSha512(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int
): ByteArray {
    require(iterations > 0) { "Iterations must be positive" }
    require(keyLength > 0) { "Key length must be positive" }

    // PBKDF2 implementation using HMAC-SHA512
    // RFC 8018: DK = T1 || T2 || ... || Tdklen/hlen
    // Ti = F(Password, Salt, c, i)
    // F(Password, Salt, c, i) = U1 ^ U2 ^ ... ^ Uc
    // U1 = PRF(Password, Salt || INT(i))
    // U2 = PRF(Password, U1)
    // ...

    val hLen = 64 // SHA-512 output length
    val dkLen = keyLength
    val numBlocks = (dkLen + hLen - 1) / hLen

    val dk = ByteArray(numBlocks * hLen)

    for (blockIndex in 1..numBlocks) {
        // U1 = HMAC-SHA512(password, salt || INT_32_BE(blockIndex))
        val blockData = salt + intToBytesBigEndian(blockIndex)
        var u = hmacSha512Internal(password, blockData)
        val t = u.copyOf()

        // U2...Uc
        for (j in 2..iterations) {
            u = hmacSha512Internal(password, u)
            // XOR into T
            for (k in t.indices) {
                t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
            }
        }

        // Copy T to output
        val offset = (blockIndex - 1) * hLen
        t.copyInto(dk, offset, 0, minOf(hLen, dkLen - offset))
    }

    return dk.copyOf(dkLen)
}

/**
 * Converts an integer to 4 bytes in big-endian order.
 */
private fun intToBytesBigEndian(value: Int): ByteArray {
    return byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}

/**
 * Internal HMAC-SHA512 implementation using libsodium.
 */
private fun hmacSha512Internal(key: ByteArray, data: ByteArray): ByteArray {
    return memScoped {
        val output = allocArray<UByteVar>(64)

        // libsodium's crypto_auth_hmacsha512 function
        // Note: This is the keyed HMAC, not the raw auth (which expects 32-byte key)
        val state = alloc<crypto_auth_hmacsha512_state>()

        key.asUByteArray().usePinned { keyPinned ->
            val initResult = crypto_auth_hmacsha512_init(
                state.ptr,
                keyPinned.addressOf(0),
                key.size.toULong()
            )
            require(initResult == 0) { "HMAC-SHA512 init failed" }
        }

        if (data.isNotEmpty()) {
            data.asUByteArray().usePinned { dataPinned ->
                val updateResult = crypto_auth_hmacsha512_update(
                    state.ptr,
                    dataPinned.addressOf(0),
                    data.size.toULong()
                )
                require(updateResult == 0) { "HMAC-SHA512 update failed" }
            }
        } else {
            val updateResult = crypto_auth_hmacsha512_update(state.ptr, null, 0u)
            require(updateResult == 0) { "HMAC-SHA512 update failed" }
        }

        val finalResult = crypto_auth_hmacsha512_final(state.ptr, output)
        require(finalResult == 0) { "HMAC-SHA512 final failed" }

        ByteArray(64) { i -> output[i].toByte() }
    }
}

/**
 * HMAC-SHA512 using libsodium.
 *
 * @param key The HMAC key (arbitrary length)
 * @param data The data to authenticate
 * @return 64-byte HMAC-SHA512 result
 */
internal actual suspend fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
    return hmacSha512Internal(key, data)
}

/**
 * Unicode NFKD normalization using Foundation.
 *
 * NFKD (Compatibility Decomposition) is required by BIP-39 specification
 * to ensure consistent mnemonic processing across all platforms.
 *
 * Uses NSString.decomposedStringWithCompatibilityMapping which performs NFKD normalization.
 */
internal actual fun normalizeNfkd(input: String): String {
    // Use Foundation's NSString for normalization
    // Kotlin/Native automatically bridges String to NSString
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nsString = input as NSString
    return nsString.decomposedStringWithCompatibilityMapping
}
