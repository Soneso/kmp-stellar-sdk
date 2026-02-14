/*
 * Copyright 2025 Soneso
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soneso.stellar.sdk.sep.sep05

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.sep.sep05.crypto.hmacSha512
import com.soneso.stellar.sdk.sep.sep05.crypto.zeroOut
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidMnemonicException
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidPathException
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Implements SEP-0005 Key Derivation Methods for Stellar Keys.
 *
 * Provides hierarchical deterministic key derivation from BIP-39 mnemonic phrases
 * using the Stellar-specific derivation path `m/44'/148'/x'` where:
 * - 44' is the BIP-44 purpose (hardened)
 * - 148' is the Stellar coin type from SLIP-0044 (hardened)
 * - x' is the account index (hardened)
 *
 * The mnemonic supports:
 * - BIP-39 mnemonic generation in 9 languages (English, Chinese Simplified, Chinese Traditional,
 *   French, Italian, Japanese, Korean, Spanish, Malay)
 * - All BIP-39 word lengths: 12, 15, 18, 21, and 24 words
 * - Mnemonic validation with checksum verification
 * - SLIP-0010 hierarchical deterministic key derivation for Ed25519
 * - Optional BIP-39 passphrase for additional security
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Generate a 24-word mnemonic (recommended)
 * val mnemonic = Mnemonic.generate24WordsMnemonic()
 *
 * // Create Mnemonic instance from phrase
 * val mnemonic = Mnemonic.from(mnemonic)
 *
 * // Derive keypairs for multiple accounts
 * val account0 = mnemonic.getKeyPair(index = 0)
 * val account1 = mnemonic.getKeyPair(index = 1)
 *
 * // Get account ID only (more efficient for display)
 * val accountId = mnemonic.getAccountId(index = 0)
 *
 * // With passphrase for additional security
 * val secureMnemonic = Mnemonic.from(mnemonic, passphrase = "my secret passphrase")
 *
 * // Clean up when done
 * mnemonic.close()
 * ```
 *
 * ## Security Considerations
 *
 * - **Store mnemonics securely**: Never expose mnemonics in logs, error messages, or insecure storage
 * - **Never log sensitive data**: Mnemonics, BIP-39 seeds, and private keys must never be logged
 * - **Use close()**: Call [close] when the mnemonic instance is no longer needed to zero internal seed data
 * - **Passphrase handling**: Lost passphrases cannot be recovered; the mnemonic alone cannot
 *   restore access to funds if a passphrase was used
 * - **Validate mnemonics**: Always validate mnemonics before use with [validate]
 *
 * ## Implementation Details
 *
 * Key derivation follows SLIP-0010 for Ed25519:
 * 1. Master key: `HMAC-SHA512(key = "ed25519 seed", data = BIP39_seed)`
 * 2. Child derivation: `HMAC-SHA512(key = parent_chain_code, data = 0x00 || parent_key || index + 2^31)`
 *
 * All derivation is hardened (indicated by apostrophe in path) as required by Ed25519.
 *
 * @see <a href="https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0005.md">SEP-5 Specification</a>
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP-39 Specification</a>
 * @see <a href="https://github.com/satoshilabs/slips/blob/master/slip-0010.md">SLIP-0010 Specification</a>
 */
public class Mnemonic private constructor(private var seed: ByteArray) : AutoCloseable {

    public companion object {
        // ========== Mnemonic Generation ==========

        /**
         * Generates a 12-word BIP-39 mnemonic phrase (128 bits of entropy).
         *
         * Provides adequate security for most use cases.
         *
         * @param language The language for the word list (default: English)
         * @return Space-separated 12-word mnemonic phrase
         */
        @JvmStatic
        public suspend fun generate12WordsMnemonic(
            language: MnemonicLanguage = MnemonicLanguage.ENGLISH
        ): String = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_128, language)

        /**
         * Generates a 15-word BIP-39 mnemonic phrase (160 bits of entropy).
         *
         * Provides good security.
         *
         * @param language The language for the word list (default: English)
         * @return Space-separated 15-word mnemonic phrase
         */
        @JvmStatic
        public suspend fun generate15WordsMnemonic(
            language: MnemonicLanguage = MnemonicLanguage.ENGLISH
        ): String = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_160, language)

        /**
         * Generates an 18-word BIP-39 mnemonic phrase (192 bits of entropy).
         *
         * Provides strong security.
         *
         * @param language The language for the word list (default: English)
         * @return Space-separated 18-word mnemonic phrase
         */
        @JvmStatic
        public suspend fun generate18WordsMnemonic(
            language: MnemonicLanguage = MnemonicLanguage.ENGLISH
        ): String = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_192, language)

        /**
         * Generates a 21-word BIP-39 mnemonic phrase (224 bits of entropy).
         *
         * Provides very strong security.
         *
         * @param language The language for the word list (default: English)
         * @return Space-separated 21-word mnemonic phrase
         */
        @JvmStatic
        public suspend fun generate21WordsMnemonic(
            language: MnemonicLanguage = MnemonicLanguage.ENGLISH
        ): String = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_224, language)

        /**
         * Generates a 24-word BIP-39 mnemonic phrase (256 bits of entropy).
         *
         * Provides maximum security and is recommended for long-term storage and high-value accounts.
         *
         * @param language The language for the word list (default: English)
         * @return Space-separated 24-word mnemonic phrase
         */
        @JvmStatic
        public suspend fun generate24WordsMnemonic(
            language: MnemonicLanguage = MnemonicLanguage.ENGLISH
        ): String = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_256, language)

        // ========== Validation ==========

        /**
         * Validates a BIP-39 mnemonic phrase.
         *
         * Checks that all words are in the word list and the checksum is valid.
         *
         * This function is suspend because JavaScript requires async initialization
         * of the cryptographic library. On JVM and Native platforms, the suspend
         * keyword has zero overhead.
         *
         * @param mnemonic Space-separated mnemonic phrase
         * @param language Word list language (default: English)
         * @return true if the mnemonic is valid, false otherwise
         */
        @JvmStatic
        @JvmOverloads
        public suspend fun validate(
            mnemonic: String,
            language: MnemonicLanguage = MnemonicLanguage.ENGLISH
        ): Boolean = MnemonicUtils.validateMnemonic(mnemonic, language)

        /**
         * Detects the language of a mnemonic phrase.
         *
         * Iterates through all supported languages and returns the first one where
         * all words in the mnemonic exist in the word list.
         *
         * Note: Some words may exist in multiple languages. This function returns
         * the first match found, which may not be the intended language in edge cases.
         *
         * @param mnemonic Space-separated mnemonic phrase
         * @return Detected language, or null if no language matches all words
         */
        @JvmStatic
        public fun detectLanguage(mnemonic: String): MnemonicLanguage? =
            MnemonicUtils.detectLanguage(mnemonic)

        // ========== Mnemonic Creation ==========

        /**
         * Creates a Mnemonic instance from a BIP-39 mnemonic phrase.
         *
         * The mnemonic is validated before creating the mnemonic instance. An optional passphrase
         * can be provided for additional security (BIP-39 passphrase extension).
         *
         * **Warning**: Using a passphrase creates a completely different mnemonic. Lost passphrases
         * cannot be recovered, and the mnemonic alone cannot restore access to funds if a passphrase
         * was used.
         *
         * @param mnemonic Space-separated mnemonic phrase
         * @param language Word list language; if null, language is auto-detected
         * @param passphrase Optional passphrase for additional security (default: empty string)
         * @return Mnemonic instance for key derivation
         * @throws InvalidMnemonicException if the mnemonic is invalid or language cannot be detected
         *
         * Example:
         * ```kotlin
         * val mnemonic = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
         * val mnemonic = Mnemonic.from(mnemonic)
         *
         * // With passphrase
         * val secureMnemonic = Mnemonic.from(mnemonic, passphrase = "my secret")
         *
         * // With explicit language
         * val frenchMnemonic = Mnemonic.from(frenchMnemonic, language = MnemonicLanguage.FRENCH)
         * ```
         */
        @JvmStatic
        @JvmOverloads
        public suspend fun from(
            mnemonic: String,
            language: MnemonicLanguage? = null,
            passphrase: String = ""
        ): Mnemonic {
            val detectedLanguage = language ?: detectLanguage(mnemonic)
                ?: throw InvalidMnemonicException("Cannot detect mnemonic language")

            if (!validate(mnemonic, detectedLanguage)) {
                throw InvalidMnemonicException("Invalid mnemonic phrase")
            }

            val seed = MnemonicUtils.mnemonicToSeed(mnemonic, passphrase)
            return Mnemonic(seed)
        }

        /**
         * Creates a Mnemonic instance from raw entropy bytes.
         *
         * For advanced users who generate their own entropy. The entropy is converted
         * to a mnemonic phrase and then to a BIP-39 seed.
         *
         * Note: A defensive copy of the entropy is made and zeroed after use for security.
         * The original entropy array is not modified.
         *
         * @param entropy 16, 20, 24, 28, or 32 bytes of entropy
         * @param language Word list language for the generated mnemonic (default: English)
         * @param passphrase Optional passphrase for additional security (default: empty string)
         * @return Mnemonic instance for key derivation
         * @throws com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidEntropyException if entropy size is invalid
         *
         * Example:
         * ```kotlin
         * // Generate 32 bytes of entropy from a secure source
         * val entropy = secureRandomBytes(32)
         * val mnemonic = Mnemonic.fromEntropy(entropy)
         * ```
         */
        @JvmStatic
        @JvmOverloads
        public suspend fun fromEntropy(
            entropy: ByteArray,
            language: MnemonicLanguage = MnemonicLanguage.ENGLISH,
            passphrase: String = ""
        ): Mnemonic {
            // Make a defensive copy to avoid modifying user's array
            val entropyCopy = entropy.copyOf()
            return try {
                val mnemonic = MnemonicUtils.entropyToMnemonic(entropyCopy, language)
                val seed = MnemonicUtils.mnemonicToSeed(mnemonic, passphrase)
                Mnemonic(seed)
            } finally {
                // Zero the entropy copy for defense in depth
                zeroOut(entropyCopy)
            }
        }

        /**
         * Creates a Mnemonic instance from a BIP-39 seed in hexadecimal format.
         *
         * This is useful when you have a pre-computed BIP-39 seed from another source.
         * BIP-39 seeds are always 512 bits (64 bytes) regardless of mnemonic length.
         *
         * @param hexSeed 128-character hex string representing a 64-byte seed
         * @return Mnemonic instance for key derivation
         * @throws IllegalArgumentException if the seed is not exactly 64 bytes
         *
         * Example:
         * ```kotlin
         * val seedHex = "e4a5a632e70943ae7f07659df1332160..."  // 128 hex characters
         * val mnemonic = Mnemonic.fromBip39HexSeed(seedHex)
         * ```
         */
        @JvmStatic
        public suspend fun fromBip39HexSeed(hexSeed: String): Mnemonic {
            val seed = HexCodec.decode(hexSeed)
            require(seed.size == MnemonicConstants.PBKDF2_KEY_LENGTH) {
                "BIP-39 seed must be 64 bytes (128 hex characters), got ${seed.size} bytes"
            }
            return Mnemonic(seed)
        }

        /**
         * Creates a Mnemonic instance from a BIP-39 seed as bytes.
         *
         * BIP-39 seeds are always 512 bits (64 bytes) regardless of the original
         * mnemonic length (12, 15, 18, 21, or 24 words).
         *
         * @param seed 64-byte BIP-39 seed
         * @return Mnemonic instance for key derivation
         * @throws IllegalArgumentException if the seed is not exactly 64 bytes
         *
         * Example:
         * ```kotlin
         * val seed = MnemonicUtils.mnemonicToSeed(mnemonic, passphrase)
         * val mnemonic = Mnemonic.fromBip39Seed(seed)
         * ```
         */
        @JvmStatic
        public suspend fun fromBip39Seed(seed: ByteArray): Mnemonic {
            require(seed.size == MnemonicConstants.PBKDF2_KEY_LENGTH) {
                "BIP-39 seed must be 64 bytes, got ${seed.size} bytes"
            }
            return Mnemonic(seed.copyOf())
        }
    }

    // ========== Key Derivation ==========

    /**
     * Derives a Stellar keypair at the specified account index.
     *
     * Uses the Stellar derivation path: `m/44'/148'/index'` where:
     * - 44' is the BIP-44 purpose (hardened)
     * - 148' is the Stellar coin type (hardened)
     * - index' is the account index (hardened)
     *
     * @param index Account index (0 to 2^31-1, default: 0)
     * @return KeyPair for the derived account with signing capability
     * @throws IllegalArgumentException if index is negative
     *
     * Example:
     * ```kotlin
     * val mnemonic = Mnemonic.from(mnemonic)
     *
     * // Derive multiple accounts
     * val account0 = mnemonic.getKeyPair(index = 0)
     * val account1 = mnemonic.getKeyPair(index = 1)
     * val account2 = mnemonic.getKeyPair(index = 2)
     * ```
     */
    public suspend fun getKeyPair(index: Int = 0): KeyPair {
        require(index >= 0) { "Index must be non-negative, got $index" }
        val derivedKey = derivePath("m/44'/148'/$index'")
        return KeyPair.fromSecretSeed(derivedKey.copyOfRange(0, MnemonicConstants.DERIVED_KEY_BYTES))
    }

    /**
     * Gets the Stellar account ID at the specified index.
     *
     * This derives the full keypair internally. If you need both the account ID and
     * signing capability, use [getKeyPair] instead to avoid redundant derivation.
     *
     * @param index Account index (default: 0)
     * @return Stellar account ID (G... address)
     * @throws IllegalArgumentException if index is negative
     *
     * Example:
     * ```kotlin
     * val accountId = mnemonic.getAccountId(index = 0)
     * // Returns: "GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6"
     * ```
     */
    public suspend fun getAccountId(index: Int = 0): String {
        return getKeyPair(index).getAccountId()
    }

    /**
     * Gets the raw 32-byte Ed25519 private key at the specified index.
     *
     * **Security Warning**: This method returns sensitive key material. Handle with care:
     * - Never log the returned bytes
     * - Zero the array when no longer needed
     * - Never expose to untrusted code
     *
     * @param index Account index (default: 0)
     * @return 32-byte Ed25519 private key (seed)
     * @throws IllegalArgumentException if index is negative
     */
    public suspend fun getPrivateKey(index: Int = 0): ByteArray {
        require(index >= 0) { "Index must be non-negative, got $index" }
        val derivedKey = derivePath("m/44'/148'/$index'")
        return derivedKey.copyOfRange(0, MnemonicConstants.DERIVED_KEY_BYTES)
    }

    /**
     * Gets the raw 32-byte Ed25519 public key at the specified index.
     *
     * @param index Account index (default: 0)
     * @return 32-byte Ed25519 public key
     * @throws IllegalArgumentException if index is negative
     */
    public suspend fun getPublicKey(index: Int = 0): ByteArray {
        return getKeyPair(index).getPublicKey()
    }

    // ========== Seed Access ==========

    /**
     * Gets the 64-byte BIP-39 seed.
     *
     * **Security Warning**: This method returns sensitive key material. Handle with care:
     * - Never log the returned bytes
     * - Zero the array when no longer needed
     * - Never expose to untrusted code
     *
     * The returned array is a defensive copy.
     *
     * @return 64-byte BIP-39 seed
     */
    public fun getBip39Seed(): ByteArray = seed.copyOf()

    /**
     * Gets the BIP-39 seed as a hex-encoded string.
     *
     * **Security Warning**: This method returns sensitive key material. Handle with care.
     *
     * @return 128-character hex string representing the 64-byte seed
     */
    public fun getBip39SeedHex(): String = HexCodec.encode(seed)

    // ========== Cleanup ==========

    /**
     * Zeros out the internal seed for security.
     *
     * Call this method when the mnemonic instance is no longer needed to minimize the time
     * sensitive key material remains in memory.
     *
     * After calling close(), the mnemonic instance becomes unusable. Any attempt to derive
     * keys will produce invalid results.
     *
     * Note: Due to Kotlin/JVM garbage collection, this provides best-effort cleanup
     * but cannot guarantee immediate removal from memory.
     */
    override fun close() {
        zeroOut(seed)
        seed = ByteArray(0)
    }

    // ========== SLIP-0010 HD Key Derivation ==========

    /**
     * Derives a key using the SLIP-0010 algorithm for Ed25519.
     *
     * SLIP-0010 is a derivation scheme that adapts BIP-32 for Ed25519 curves.
     * Unlike secp256k1 (Bitcoin), Ed25519 only supports hardened derivation.
     *
     * Algorithm:
     * 1. Master key: `I = HMAC-SHA512(key = "ed25519 seed", data = BIP39_seed)`
     *    - Master private key = I[0:32]
     *    - Master chain code = I[32:64]
     * 2. Child key: `I = HMAC-SHA512(key = parent_chain_code, data = 0x00 || parent_key || index + 2^31)`
     *    - Child private key = I[0:32]
     *    - Child chain code = I[32:64]
     *
     * @param path BIP-32 derivation path (e.g., "m/44'/148'/0'")
     * @return 64-byte array: [0:32] is private key, [32:64] is chain code
     * @throws InvalidPathException if the path format is invalid
     */
    internal suspend fun derivePath(path: String): ByteArray {
        // Validate path format
        val pathRegex = Regex("""^(m/)?(\d+'?/)*\d+'?$""")
        if (!pathRegex.matches(path)) {
            throw InvalidPathException(path, "Invalid BIP-32 derivation path format: $path")
        }

        val segments = path.removePrefix("m/").split("/")

        // Master key generation: HMAC-SHA512(key = "ed25519 seed", data = BIP39_seed)
        var result = hmacSha512(
            MnemonicConstants.ED25519_SEED_KEY.encodeToByteArray(),
            seed
        )

        // Derive each path segment
        for (segment in segments) {
            val indexStr = segment.removeSuffix("'")
            val index = indexStr.toIntOrNull()
                ?: throw InvalidPathException(path, "Invalid index in path segment: $segment")

            if (index < 0) {
                throw InvalidPathException(path, "Index must be non-negative: $index")
            }

            result = deriveChild(
                parentKey = result.copyOfRange(0, MnemonicConstants.DERIVED_KEY_BYTES),
                parentChainCode = result.copyOfRange(
                    MnemonicConstants.DERIVED_KEY_BYTES,
                    MnemonicConstants.DERIVED_KEY_BYTES + MnemonicConstants.CHAIN_CODE_BYTES
                ),
                index = index
            )
        }

        return result
    }

    /**
     * Derives a child key using SLIP-0010 hardened derivation.
     *
     * For Ed25519, only hardened derivation is supported. The hardened index
     * is computed as: `index + 2^31`.
     *
     * Data format for HMAC: `0x00 || parent_key (32 bytes) || index (4 bytes big-endian)`
     * Total: 37 bytes
     *
     * @param parentKey 32-byte parent private key
     * @param parentChainCode 32-byte parent chain code
     * @param index Child index (will be hardened by adding 2^31)
     * @return 64-byte array: [0:32] is child private key, [32:64] is child chain code
     */
    private suspend fun deriveChild(
        parentKey: ByteArray,
        parentChainCode: ByteArray,
        index: Int
    ): ByteArray {
        // SLIP-0010 hardened child derivation for Ed25519
        // data = 0x00 || parent_key || (index + 2^31)
        val data = ByteArray(MnemonicConstants.HD_DERIVATION_DATA_LENGTH)

        // First byte is 0x00
        data[0] = 0x00

        // Copy parent key (bytes 1-32)
        parentKey.copyInto(data, 1)

        // Add hardened index as 4-byte big-endian (bytes 33-36)
        val hardenedIndex = index.toLong() + MnemonicConstants.BIP32_HARDENED_OFFSET
        data[33] = (hardenedIndex shr 24).toByte()
        data[34] = (hardenedIndex shr 16).toByte()
        data[35] = (hardenedIndex shr 8).toByte()
        data[36] = hardenedIndex.toByte()

        return hmacSha512(parentChainCode, data)
    }
}
