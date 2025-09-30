package com.stellar.sdk

import com.stellar.sdk.crypto.getEd25519Crypto
import kotlin.jvm.JvmStatic

/**
 * Holds a Stellar keypair consisting of a public key and optionally a private key (secret seed).
 *
 * ## Cryptographic Implementation
 *
 * This class uses production-ready, platform-specific cryptographic implementations:
 *
 * ### JVM/Android
 * - **Library**: BouncyCastle (org.bouncycastle:bcprov-jdk18on)
 * - **Algorithm**: Ed25519 (RFC 8032 compliant)
 * - **Security**: Mature, widely-audited implementation
 * - **Provider**: Registered as JCA security provider
 *
 * ### iOS/macOS
 * - **Library**: libsodium (via C interop)
 * - **Algorithm**: Ed25519 (crypto_sign_*)
 * - **Security**: Audited, constant-time operations, memory-safe
 * - **Distribution**: Requires libsodium via Swift Package Manager or static linking
 *
 * ### JavaScript/Web
 * - **Library**: libsodium-wrappers (WebAssembly)
 * - **Algorithm**: Ed25519 (crypto_sign_*)
 * - **Security**: Same audited libsodium implementation compiled to WebAssembly
 * - **Distribution**: Bundled via npm/webpack
 *
 * ### Security Considerations
 *
 * 1. **Memory Management**:
 *    - Secret seeds are stored as ByteArray and should be zeroed after use
 *    - Use [fromSecretSeed] with CharArray when possible for better memory control
 *    - All returned arrays are defensive copies to prevent external modification
 *
 * 2. **Side-Channel Protection**:
 *    - All implementations use constant-time operations for signing/verification
 *    - No timing-based attacks on key operations
 *
 * 3. **Validation**:
 *    - All inputs are validated for correct length and format
 *    - Invalid keys/seeds throw IllegalArgumentException immediately
 *
 * ## Thread Safety
 *
 * KeyPair instances are immutable and thread-safe. The underlying cryptographic
 * operations are also thread-safe.
 *
 * @see <a href="https://tools.ietf.org/html/rfc8032">RFC 8032 - Edwards-Curve Digital Signature Algorithm (EdDSA)</a>
 * @see <a href="https://libsodium.gitbook.io/doc/">libsodium documentation</a>
 * @see <a href="https://www.bouncycastle.org/">BouncyCastle</a>
 */
class KeyPair private constructor(
    private val publicKey: ByteArray,
    private val privateKey: ByteArray?
) {
    init {
        require(publicKey.size == 32) { "Public key must be exactly 32 bytes" }
        privateKey?.let {
            require(it.size == 32) { "Private key must be exactly 32 bytes" }
        }
    }
    companion object {
        private val crypto = getEd25519Crypto()

        /**
         * Returns the name of the cryptographic library being used by the SDK.
         *
         * Examples:
         * - "BouncyCastle" on JVM
         * - "libsodium" on iOS/macOS/native platforms
         * - "libsodium.js / Web Crypto API" on JavaScript
         */
        @JvmStatic
        fun getCryptoLibraryName(): String = crypto.libraryName

        /**
         * Creates a new Stellar KeyPair from a strkey encoded Stellar secret seed.
         *
         * **Security Note**: This method accepts a CharArray which allows for secure memory
         * management. After use, the CharArray can be zeroed to remove the secret from memory.
         * This is preferred over String-based methods.
         *
         * @param seed Char array containing strkey encoded Stellar secret seed (S...)
         * @return KeyPair with both public and private keys
         * @throws IllegalArgumentException if the seed format is invalid, has incorrect checksum,
         *         wrong version byte, or invalid length
         */
        @JvmStatic
        fun fromSecretSeed(seed: CharArray): KeyPair {
            require(seed.isNotEmpty()) { "Secret seed cannot be empty" }
            val decoded = try {
                StrKey.decodeEd25519SecretSeed(seed)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid secret seed format: ${e.message}", e)
            }
            return fromSecretSeed(decoded)
        }

        /**
         * **Insecure** Creates a new Stellar KeyPair from a strkey encoded Stellar secret seed.
         *
         * ## Security Warning
         *
         * This method is **insecure** for the following reasons:
         * - String values in JVM/Kotlin are immutable and cannot be reliably cleared from memory
         * - Strings may be interned, duplicated, or remain in memory even after garbage collection
         * - Strings may appear in memory dumps, swap files, or hibernate images
         * - If the system is compromised, secret strings can be extracted from memory
         *
         * **Use [fromSecretSeed] with CharArray instead** for better security. CharArray can be
         * explicitly zeroed after use.
         *
         * Only use this method when:
         * - Reading from configuration files that cannot be changed
         * - Receiving secrets from external APIs that return strings
         * - Prototyping or testing (never in production with real secrets)
         *
         * @param seed The strkey encoded Stellar secret seed (S...)
         * @return KeyPair with both public and private keys
         * @throws IllegalArgumentException if the seed format is invalid
         * @see fromSecretSeed(CharArray) for secure alternative
         */
        @JvmStatic
        fun fromSecretSeed(seed: String): KeyPair {
            require(seed.isNotEmpty()) { "Secret seed cannot be empty" }
            val charSeed = seed.toCharArray()
            try {
                return fromSecretSeed(charSeed)
            } finally {
                // Attempt to zero the char array (though the original string remains in memory)
                charSeed.fill('\u0000')
            }
        }

        /**
         * Creates a new Stellar keypair from a raw 32 byte secret seed.
         *
         * **Security Note**: The provided seed array is copied internally. After creating the keypair,
         * you should zero the original seed array if it's no longer needed:
         * ```kotlin
         * val seed = getSeedFromSomewhere()
         * val keypair = KeyPair.fromSecretSeed(seed)
         * seed.fill(0) // Zero the original
         * ```
         *
         * @param seed The 32 byte secret seed
         * @return KeyPair with both public and private keys
         * @throws IllegalArgumentException if seed is not exactly 32 bytes
         * @throws IllegalStateException if key derivation fails (crypto implementation error)
         */
        @JvmStatic
        fun fromSecretSeed(seed: ByteArray): KeyPair {
            require(seed.size == 32) { "Secret seed must be exactly 32 bytes, got ${seed.size}" }

            val publicKey = try {
                crypto.derivePublicKey(seed)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to derive public key from seed: ${e.message}", e)
            }

            require(publicKey.size == 32) { "Derived public key must be 32 bytes, got ${publicKey.size}" }

            return KeyPair(publicKey, seed.copyOf())
        }

        /**
         * Creates a new Stellar KeyPair from a strkey encoded Stellar account ID.
         *
         * @param accountId The strkey encoded Stellar account ID (G...)
         * @return KeyPair with only public key (cannot sign)
         * @throws IllegalArgumentException if the provided account ID is invalid
         */
        @JvmStatic
        fun fromAccountId(accountId: String): KeyPair {
            val decoded = StrKey.decodeEd25519PublicKey(accountId)
            return fromPublicKey(decoded)
        }

        /**
         * Creates a new Stellar keypair from a 32 byte public key.
         *
         * @param publicKey The 32 byte public key
         * @return KeyPair with only public key (cannot sign)
         * @throws IllegalArgumentException if the provided public key is invalid
         */
        @JvmStatic
        fun fromPublicKey(publicKey: ByteArray): KeyPair {
            require(publicKey.size == 32) { "Public key must be 32 bytes" }
            return KeyPair(publicKey.copyOf(), null)
        }

        /**
         * Generates a random Stellar keypair using cryptographically secure random number generation.
         *
         * ## Implementation Details
         *
         * ### JVM
         * Uses `java.security.SecureRandom` via BouncyCastle's Ed25519PrivateKeyParameters
         *
         * ### Native (iOS/macOS)
         * Uses `randombytes_buf()` from libsodium, which uses:
         * - macOS: `arc4random_buf()` backed by the system's CSPRNG
         * - iOS: Same as macOS
         *
         * All implementations provide cryptographically secure randomness suitable for
         * generating private keys.
         *
         * @return a random Stellar keypair with both public and private keys
         * @throws IllegalStateException if random generation or key derivation fails
         */
        @JvmStatic
        fun random(): KeyPair {
            val privateKey = try {
                crypto.generatePrivateKey()
            } catch (e: Exception) {
                throw IllegalStateException("Failed to generate random private key: ${e.message}", e)
            }

            require(privateKey.size == 32) { "Generated private key must be 32 bytes, got ${privateKey.size}" }

            val publicKey = try {
                crypto.derivePublicKey(privateKey)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to derive public key: ${e.message}", e)
            }

            return KeyPair(publicKey, privateKey)
        }
    }

    /**
     * Returns true if this Keypair is capable of signing.
     */
    fun canSign(): Boolean = privateKey != null

    /**
     * Returns the human-readable account ID encoded in strkey (G...).
     */
    fun getAccountId(): String = StrKey.encodeEd25519PublicKey(publicKey)

    /**
     * Returns the human-readable secret seed encoded in strkey (S...).
     *
     * **WARNING**: This method returns the secret seed of the keypair. The secret seed should be
     * handled with care and not be exposed to anyone else. Exposing the secret seed can lead to
     * the theft of the account.
     *
     * @return CharArray The secret seed of the keypair. If the keypair was created without a secret
     *     seed, this method will return null.
     */
    fun getSecretSeed(): CharArray? {
        return privateKey?.let { StrKey.encodeEd25519SecretSeed(it) }
    }

    /**
     * Returns the raw 32 byte public key.
     */
    fun getPublicKey(): ByteArray = publicKey.copyOf()

    /**
     * Sign the provided data with the keypair's private key.
     *
     * @param data The data to sign
     * @return signed bytes (64 bytes)
     * @throws IllegalStateException if the private key for this keypair is null
     */
    fun sign(data: ByteArray): ByteArray {
        val key = privateKey ?: throw IllegalStateException(
            "KeyPair does not contain secret key. Use KeyPair.fromSecretSeed method to create a new KeyPair with a secret key."
        )
        return crypto.sign(data, key)
    }

    /**
     * Verify the provided data and signature match this keypair's public key.
     *
     * @param data The data that was signed
     * @param signature The signature (64 bytes)
     * @return True if they match, false otherwise
     */
    fun verify(data: ByteArray, signature: ByteArray): Boolean {
        return crypto.verify(data, signature, publicKey)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as KeyPair

        if (!publicKey.contentEquals(other.publicKey)) return false
        if (privateKey != null && other.privateKey != null) {
            if (!privateKey.contentEquals(other.privateKey)) return false
        } else if (privateKey != null || other.privateKey != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + (privateKey?.contentHashCode() ?: 0)
        return result
    }
}