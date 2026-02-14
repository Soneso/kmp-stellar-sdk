package com.soneso.stellar.sdk.sep.sep05.crypto

import com.soneso.stellar.sdk.crypto.LibsodiumInit
import org.khronos.webgl.Uint8Array

/**
 * JavaScript implementation of SEP-5 cryptographic primitives.
 *
 * Uses libsodium-wrappers-sumo for cryptographic operations and
 * native JavaScript APIs where appropriate:
 *
 * - **Random**: crypto.getRandomValues() (Web Crypto API)
 * - **SHA-256**: libsodium crypto_hash_sha256
 * - **HMAC-SHA512**: libsodium crypto_auth_hmacsha512
 * - **PBKDF2**: Custom implementation using HMAC-SHA512
 * - **NFKD**: String.prototype.normalize("NFKD")
 *
 * All cryptographic functions are suspend to properly handle libsodium
 * async initialization in JavaScript environments.
 */

/**
 * Generates cryptographically secure random bytes using Web Crypto API.
 *
 * Uses crypto.getRandomValues() which is available in all modern browsers
 * and Node.js. This is the recommended way to generate cryptographic randomness
 * in JavaScript environments.
 */
internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "Size must be positive" }

    return try {
        val array = Uint8Array(size)
        js("crypto.getRandomValues(array)")
        array.toByteArray()
    } catch (e: Throwable) {
        throw IllegalStateException("Failed to generate secure random bytes: ${e.message}", e)
    }
}

/**
 * Computes SHA-256 hash using libsodium's crypto_hash_sha256.
 *
 * This function is suspend to properly handle libsodium async initialization
 * in JavaScript environments.
 */
internal actual suspend fun sha256(data: ByteArray): ByteArray {
    // Ensure libsodium is initialized
    LibsodiumInit.ensureInitialized()
    val sodium = LibsodiumInit.getSodium()

    return try {
        val dataArray = data.toUint8Array()
        val result = js(
            """
            (function() {
                return sodium.crypto_hash_sha256(dataArray);
            })()
            """
        ).unsafeCast<Uint8Array>()
        result.toByteArray()
    } catch (e: Throwable) {
        throw IllegalStateException("SHA-256 computation failed: ${e.message}", e)
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

    // Ensure libsodium is initialized
    LibsodiumInit.ensureInitialized()

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
 * Internal HMAC-SHA512 without suspend (for use within pbkdf2).
 * Assumes libsodium is already initialized.
 *
 * Uses the multi-part HMAC API (init/update/final) to support arbitrary-length keys,
 * which is required for BIP-39 and BIP-32 key derivation.
 */
private fun hmacSha512Internal(key: ByteArray, data: ByteArray): ByteArray {
    val sodium = LibsodiumInit.getSodium()
    val keyArray = key.toUint8Array()
    val dataArray = data.toUint8Array()

    return try {
        // Use multi-part HMAC API for arbitrary-length keys
        // crypto_auth_hmacsha512 expects a 32-byte key, but we need arbitrary-length
        val result = js(
            """
            (function() {
                // Initialize HMAC state with arbitrary-length key
                var state = sodium.crypto_auth_hmacsha512_init(keyArray);
                // Update with message data
                sodium.crypto_auth_hmacsha512_update(state, dataArray);
                // Finalize and return the MAC
                return sodium.crypto_auth_hmacsha512_final(state);
            })()
            """
        ).unsafeCast<Uint8Array>()
        result.toByteArray()
    } catch (e: Throwable) {
        throw IllegalStateException("HMAC-SHA512 computation failed: ${e.message}", e)
    }
}

/**
 * HMAC-SHA512 using libsodium's crypto_auth_hmacsha512.
 *
 * Note: libsodium's crypto_auth_hmacsha512 expects a 32-byte key for the
 * standard crypto_auth function. For arbitrary-length keys (as needed for
 * BIP-32), we use the full HMAC construction.
 *
 * @param key The HMAC key (arbitrary length)
 * @param data The data to authenticate
 * @return 64-byte HMAC-SHA512 result
 */
internal actual suspend fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
    // Ensure libsodium is initialized
    LibsodiumInit.ensureInitialized()

    return hmacSha512Internal(key, data)
}

/**
 * Unicode NFKD normalization using JavaScript's String.normalize().
 *
 * NFKD (Compatibility Decomposition) is required by BIP-39 specification
 * to ensure consistent mnemonic processing across all platforms.
 *
 * All modern browsers and Node.js support String.prototype.normalize().
 */
internal actual fun normalizeNfkd(input: String): String {
    return js("input.normalize('NFKD')").unsafeCast<String>()
}

// Helper extension functions

private fun ByteArray.toUint8Array(): Uint8Array {
    val array = Uint8Array(this.size)
    this.forEachIndexed { index, byte ->
        array.asDynamic()[index] = byte
    }
    return array
}

private fun Uint8Array.toByteArray(): ByteArray {
    return ByteArray(this.length) { index ->
        this.asDynamic()[index] as Byte
    }
}
