package com.soneso.stellar.sdk.sep.sep05.crypto

/**
 * Platform-specific cryptographic primitives for SEP-5 (Key Derivation Methods for Stellar Keys).
 *
 * This module provides the cryptographic building blocks needed for BIP-39 mnemonic
 * generation and BIP-32/SLIP-10 hierarchical key derivation.
 *
 * ## Production-Ready Implementations
 *
 * All implementations use audited, battle-tested cryptographic libraries:
 *
 * ### JVM
 * - **Library**: BouncyCastle (org.bouncycastle:bcprov-jdk18on)
 * - **Random**: java.security.SecureRandom
 * - **Hashing**: SHA256Digest, HMac with SHA512Digest
 * - **KDF**: PKCS5S2ParametersGenerator with SHA-512
 * - **Normalization**: java.text.Normalizer
 *
 * ### iOS/macOS (Native)
 * - **Library**: libsodium (via C interop) + Core Foundation
 * - **Random**: randombytes_buf() using system CSPRNG
 * - **Hashing**: crypto_hash_sha256, crypto_auth_hmacsha512
 * - **KDF**: Custom PBKDF2 using HMAC-SHA512
 * - **Normalization**: CFStringNormalize with kCFStringNormalizationFormKD
 *
 * ### JavaScript (Browser and Node.js)
 * - **Library**: libsodium-wrappers-sumo (WebAssembly)
 * - **Random**: crypto.getRandomValues()
 * - **Hashing**: crypto_hash_sha256, crypto_auth_hmacsha512
 * - **KDF**: Custom PBKDF2 using HMAC-SHA512
 * - **Normalization**: String.prototype.normalize("NFKD")
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0005.md">SEP-5</a>
 */

/**
 * Generates cryptographically secure random bytes.
 *
 * Uses platform-specific CSPRNG:
 * - JVM: java.security.SecureRandom
 * - Native: libsodium randombytes_buf() (backed by arc4random_buf)
 * - JS: crypto.getRandomValues()
 *
 * @param size Number of random bytes to generate
 * @return ByteArray of cryptographically secure random bytes
 */
internal expect fun secureRandomBytes(size: Int): ByteArray

/**
 * Computes SHA-256 hash of the input data.
 *
 * Used for BIP-39 checksum calculation.
 *
 * This function is suspend because JavaScript requires async initialization
 * of libsodium. On JVM and Native platforms, the suspend keyword has zero overhead.
 *
 * @param data Input data to hash
 * @return 32-byte SHA-256 hash
 */
internal expect suspend fun sha256(data: ByteArray): ByteArray

/**
 * PBKDF2 key derivation using HMAC-SHA512.
 *
 * Used for BIP-39 seed generation from mnemonic phrase.
 *
 * This function is suspend because JavaScript requires async initialization
 * of libsodium. On JVM and Native platforms, the suspend keyword has zero overhead.
 *
 * @param password The password (mnemonic phrase bytes)
 * @param salt The salt (typically "mnemonic" + passphrase)
 * @param iterations Number of iterations (2048 for BIP-39)
 * @param keyLength Length of derived key in bytes (64 for BIP-39)
 * @return Derived key bytes
 */
internal expect suspend fun pbkdf2HmacSha512(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    keyLength: Int
): ByteArray

/**
 * HMAC-SHA512 message authentication code.
 *
 * Used for BIP-32/SLIP-10 key derivation.
 *
 * This function is suspend because JavaScript requires async initialization
 * of libsodium. On JVM and Native platforms, the suspend keyword has zero overhead.
 *
 * @param key The HMAC key
 * @param data The data to authenticate
 * @return 64-byte HMAC-SHA512 result
 */
internal expect suspend fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray

/**
 * Unicode NFKD (Compatibility Decomposition) normalization.
 *
 * Required by BIP-39 for consistent mnemonic processing across platforms.
 * NFKD ensures that equivalent Unicode strings produce the same bytes.
 *
 * @param input String to normalize
 * @return NFKD-normalized string
 */
internal expect fun normalizeNfkd(input: String): String

/**
 * Constant-time byte array comparison.
 *
 * Compares two byte arrays in constant time to prevent timing attacks.
 * Returns true only if both arrays have the same length and contents.
 *
 * @param a First byte array
 * @param b Second byte array
 * @return true if arrays are equal, false otherwise
 */
internal fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var result = 0
    for (i in a.indices) {
        result = result or (a[i].toInt() xor b[i].toInt())
    }
    return result == 0
}

/**
 * Securely zeros out a byte array.
 *
 * Should be called on sensitive data (seeds, private keys) when no longer needed.
 *
 * @param bytes ByteArray to zero out
 */
internal fun zeroOut(bytes: ByteArray) {
    bytes.fill(0)
}
