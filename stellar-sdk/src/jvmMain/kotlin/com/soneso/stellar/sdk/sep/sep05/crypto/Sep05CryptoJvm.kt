package com.soneso.stellar.sdk.sep.sep05.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import java.security.Security
import java.text.Normalizer

/**
 * JVM implementation of SEP-5 cryptographic primitives using BouncyCastle.
 *
 * BouncyCastle provides production-ready, audited implementations of:
 * - SHA-256 and SHA-512 digests
 * - HMAC-SHA512
 * - PBKDF2 with HMAC-SHA512
 *
 * java.security.SecureRandom is used for cryptographically secure randomness,
 * and java.text.Normalizer handles Unicode normalization.
 */

// Register BouncyCastle provider on class load
private val providerRegistered: Boolean = run {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(BouncyCastleProvider())
    }
    true
}

/**
 * Generates cryptographically secure random bytes using java.security.SecureRandom.
 */
internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0) { "Size must be positive" }
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}

/**
 * Computes SHA-256 hash using BouncyCastle's SHA256Digest.
 */
internal actual suspend fun sha256(data: ByteArray): ByteArray {
    val digest = SHA256Digest()
    digest.update(data, 0, data.size)
    val result = ByteArray(digest.digestSize)
    digest.doFinal(result, 0)
    return result
}

/**
 * PBKDF2 key derivation using HMAC-SHA512 via BouncyCastle.
 *
 * Uses PKCS5S2ParametersGenerator with SHA-512 digest for BIP-39 compliant
 * seed derivation.
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

    val generator = PKCS5S2ParametersGenerator(SHA512Digest())
    generator.init(password, salt, iterations)

    // keyLength is in bytes, generateDerivedParameters takes bits
    val keyParams = generator.generateDerivedParameters(keyLength * 8) as KeyParameter
    return keyParams.key
}

/**
 * HMAC-SHA512 using BouncyCastle's HMac with SHA512Digest.
 *
 * Used for BIP-32/SLIP-10 hierarchical key derivation.
 *
 * @param key The HMAC key
 * @param data The data to authenticate
 * @return 64-byte HMAC-SHA512 result
 */
internal actual suspend fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
    val hmac = HMac(SHA512Digest())
    hmac.init(KeyParameter(key))
    hmac.update(data, 0, data.size)
    val result = ByteArray(hmac.macSize)
    hmac.doFinal(result, 0)
    return result
}

/**
 * Unicode NFKD normalization using java.text.Normalizer.
 *
 * NFKD (Compatibility Decomposition) is required by BIP-39 specification
 * to ensure consistent mnemonic processing across all platforms.
 */
internal actual fun normalizeNfkd(input: String): String {
    return Normalizer.normalize(input, Normalizer.Form.NFKD)
}
