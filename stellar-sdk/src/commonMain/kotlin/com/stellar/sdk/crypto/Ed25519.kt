package com.stellar.sdk.crypto

/**
 * Platform-specific Ed25519 cryptographic operations.
 *
 * ## Production-Ready Implementations
 *
 * All implementations use **audited, battle-tested cryptographic libraries**:
 *
 * ### JVM
 * - **Library**: BouncyCastle (org.bouncycastle:bcprov-jdk18on)
 * - **Algorithm**: Ed25519 via `Ed25519Signer`, `Ed25519PrivateKeyParameters`
 * - **Security**: RFC 8032 compliant, constant-time operations
 * - **Provider**: Registered as JCA security provider
 *
 * ### iOS/macOS (Native)
 * - **Library**: libsodium (via C interop)
 * - **Algorithm**: Ed25519 via `crypto_sign_*` functions
 * - **Security**: Audited, constant-time, memory-safe
 * - **Random**: `randombytes_buf()` using system CSPRNG
 * - **Why libsodium over CryptoKit**:
 *   - Direct C interop (no Swift bridge needed)
 *   - Matches Stellar's exact key format (raw 32-byte seeds)
 *   - Cross-platform (same code for iOS, macOS, Linux)
 *   - Used by stellar-core reference implementation
 *   - Industry standard (Signal, Tor, etc.)
 *
 * ### JavaScript (Browser & Node.js)
 * - **Primary**: Web Crypto API (Chrome 113+, Firefox 120+, Safari 17+)
 * - **Fallback**: libsodium-wrappers (WebAssembly, universal compatibility)
 * - **Strategy**: Automatic detection and graceful fallback
 * - **Security**: libsodium.js is the same audited C library compiled to WASM
 * - **Compatibility**: Works in all modern browsers and Node.js
 * - **Why libsodium.js over TweetNaCl**:
 *   - More features (crypto_sign_seed_keypair for key derivation)
 *   - Better performance (WebAssembly vs pure JS)
 *   - Same library as native implementation
 *   - Actively maintained
 *
 * @see <a href="https://tools.ietf.org/html/rfc8032">RFC 8032 - EdDSA</a>
 * @see <a href="https://libsodium.gitbook.io/doc/">libsodium documentation</a>
 * @see <a href="https://www.bouncycastle.org/">BouncyCastle</a>
 */
interface Ed25519Crypto {
    /**
     * Returns the name of the cryptographic library being used.
     */
    val libraryName: String

    /**
     * Generates a new random Ed25519 private key (32 bytes).
     */
    fun generatePrivateKey(): ByteArray

    /**
     * Derives the public key from a private key.
     *
     * @param privateKey The 32-byte Ed25519 private key
     * @return The 32-byte Ed25519 public key
     */
    fun derivePublicKey(privateKey: ByteArray): ByteArray

    /**
     * Signs data using Ed25519.
     *
     * @param data The data to sign
     * @param privateKey The 32-byte Ed25519 private key
     * @return The 64-byte signature
     */
    fun sign(data: ByteArray, privateKey: ByteArray): ByteArray

    /**
     * Verifies an Ed25519 signature.
     *
     * @param data The data that was signed
     * @param signature The 64-byte signature
     * @param publicKey The 32-byte Ed25519 public key
     * @return true if the signature is valid, false otherwise
     */
    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
}

/**
 * Get the platform-specific Ed25519 crypto implementation.
 */
expect fun getEd25519Crypto(): Ed25519Crypto