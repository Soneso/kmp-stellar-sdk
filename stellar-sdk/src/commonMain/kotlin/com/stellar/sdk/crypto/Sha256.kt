package com.stellar.sdk.crypto

/**
 * Platform-specific SHA-256 cryptographic hash function.
 *
 * ## Production-Ready Implementations
 *
 * All implementations use **audited, battle-tested cryptographic libraries**:
 *
 * ### JVM
 * - **Library**: Java Security API (java.security.MessageDigest)
 * - **Algorithm**: SHA-256
 * - **Security**: FIPS 140-2 compliant, industry standard
 *
 * ### iOS/macOS (Native)
 * - **Library**: libsodium (via C interop)
 * - **Algorithm**: SHA-256 via `crypto_hash_sha256`
 * - **Security**: Audited, constant-time operations
 *
 * ### JavaScript (Browser & Node.js)
 * - **Primary**: Web Crypto API (SubtleCrypto.digest)
 * - **Fallback**: libsodium-wrappers (WebAssembly)
 * - **Security**: Hardware-accelerated where available
 *
 * @see <a href="https://csrc.nist.gov/publications/detail/fips/180/4/final">FIPS 180-4 - SHA-256</a>
 */
interface Sha256Crypto {
    /**
     * Returns the name of the cryptographic library being used.
     */
    val libraryName: String

    /**
     * Computes the SHA-256 hash of the input data.
     *
     * @param data The data to hash
     * @return The 32-byte SHA-256 hash
     */
    fun hash(data: ByteArray): ByteArray
}

/**
 * Get the platform-specific SHA-256 crypto implementation.
 */
expect fun getSha256Crypto(): Sha256Crypto
