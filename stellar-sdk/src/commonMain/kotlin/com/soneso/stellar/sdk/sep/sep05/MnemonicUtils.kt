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

import com.soneso.stellar.sdk.sep.sep05.crypto.constantTimeEquals
import com.soneso.stellar.sdk.sep.sep05.crypto.normalizeNfkd
import com.soneso.stellar.sdk.sep.sep05.crypto.pbkdf2HmacSha512
import com.soneso.stellar.sdk.sep.sep05.crypto.secureRandomBytes
import com.soneso.stellar.sdk.sep.sep05.crypto.sha256
import com.soneso.stellar.sdk.sep.sep05.crypto.zeroOut
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidChecksumException
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidEntropyException
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidMnemonicException
import com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidWordException
import com.soneso.stellar.sdk.sep.sep05.exceptions.Sep05Exception

/**
 * BIP-39 mnemonic utility functions for generating, validating, and converting mnemonic phrases.
 *
 * This object provides the core operations for BIP-39 mnemonic handling as specified by
 * the [BIP-39 specification](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki)
 * and [SEP-5](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0005.md).
 *
 * ## Security Considerations
 *
 * - **CSPRNG**: Uses cryptographically secure random number generation for entropy
 * - **O(1) Word Lookup**: Word list lookups use HashMap for constant-time access to mitigate timing attacks
 * - **Constant-Time Comparison**: Checksum validation uses constant-time comparison to prevent timing attacks
 * - **NFKD Normalization**: Both mnemonic and passphrase are NFKD-normalized before use per BIP-39 specification
 * - **Memory Cleanup**: Generated entropy is zeroed after use for defense in depth
 *
 * ## Usage Example
 *
 * ```kotlin
 * // Generate a 24-word mnemonic (256 bits of entropy)
 * val mnemonic = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_256)
 *
 * // Validate a mnemonic
 * if (MnemonicUtils.validateMnemonic(mnemonic)) {
 *     println("Mnemonic is valid")
 * }
 *
 * // Convert mnemonic to BIP-39 seed
 * val seed = MnemonicUtils.mnemonicToSeed(mnemonic, passphrase = "optional passphrase")
 * ```
 *
 * @see MnemonicStrength for entropy strength options
 * @see MnemonicLanguage for supported word list languages
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP-39 Specification</a>
 */
public object MnemonicUtils {

    /**
     * Generates a BIP-39 mnemonic phrase with the specified strength.
     *
     * Uses cryptographically secure random number generation to produce entropy,
     * which is then converted to a mnemonic phrase using the specified word list.
     *
     * @param strength Entropy strength determining word count (default: 256 bits / 24 words)
     * @param language Word list language (default: English)
     * @return Space-separated mnemonic phrase
     *
     * Example:
     * ```kotlin
     * // Generate a 24-word mnemonic (recommended)
     * val mnemonic = MnemonicUtils.generateMnemonic()
     *
     * // Generate a 12-word mnemonic
     * val shortMnemonic = MnemonicUtils.generateMnemonic(MnemonicStrength.BITS_128)
     *
     * // Generate a French mnemonic
     * val frenchMnemonic = MnemonicUtils.generateMnemonic(
     *     strength = MnemonicStrength.BITS_256,
     *     language = MnemonicLanguage.FRENCH
     * )
     * ```
     */
    public suspend fun generateMnemonic(
        strength: MnemonicStrength = MnemonicStrength.BITS_256,
        language: MnemonicLanguage = MnemonicLanguage.ENGLISH
    ): String {
        val entropyBytes = strength.entropyBits / 8
        val entropy = secureRandomBytes(entropyBytes)
        return try {
            entropyToMnemonic(entropy, language)
        } finally {
            // Zero out entropy for defense in depth
            zeroOut(entropy)
        }
    }

    /**
     * Converts entropy bytes to a BIP-39 mnemonic phrase.
     *
     * The entropy is validated, SHA-256 checksum bits are appended, and the result
     * is encoded as a sequence of words from the specified word list.
     *
     * Algorithm:
     * 1. Validate entropy size (must be 16, 20, 24, 28, or 32 bytes)
     * 2. Convert entropy to binary string
     * 3. Calculate SHA-256 hash, take first N bits as checksum (N = entropy_bits / 32)
     * 4. Append checksum bits to entropy bits
     * 5. Split into 11-bit chunks
     * 6. Map each chunk to corresponding word in word list
     *
     * This function is suspend because JavaScript requires async initialization
     * of the cryptographic library for SHA-256. On JVM and Native platforms,
     * the suspend keyword has zero overhead.
     *
     * @param entropy Random entropy bytes (16, 20, 24, 28, or 32 bytes)
     * @param language Word list language (default: English)
     * @return Space-separated mnemonic phrase
     * @throws InvalidEntropyException if entropy size is invalid
     *
     * Example:
     * ```kotlin
     * // Convert 16 bytes of entropy to a 12-word mnemonic
     * val entropy = HexCodec.decode("00000000000000000000000000000000")
     * val mnemonic = MnemonicUtils.entropyToMnemonic(entropy)
     * // Result: "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
     * ```
     */
    public suspend fun entropyToMnemonic(
        entropy: ByteArray,
        language: MnemonicLanguage = MnemonicLanguage.ENGLISH
    ): String {
        validateEntropy(entropy)

        val wordList = WordList.getWordList(language)
        val entropyBits = bytesToBinaryString(entropy)
        val checksumBits = deriveChecksumBits(entropy)
        val bits = entropyBits + checksumBits

        val words = bits.chunked(MnemonicConstants.BITS_PER_WORD).map { chunk ->
            val index = chunk.toInt(2)
            wordList[index]
        }

        return words.joinToString(" ")
    }

    /**
     * Converts a BIP-39 mnemonic phrase back to its original entropy.
     *
     * This function validates the mnemonic by:
     * 1. Checking word count (must be 12, 15, 18, 21, or 24)
     * 2. Verifying all words exist in the word list
     * 3. Validating the checksum
     *
     * Algorithm:
     * 1. Split mnemonic into words
     * 2. Look up each word's index in word list (O(1) HashMap lookup)
     * 3. Convert indices to 11-bit binary strings
     * 4. Concatenate all bits
     * 5. Split into entropy bits and checksum bits
     * 6. Verify checksum using constant-time comparison
     * 7. Return entropy bytes
     *
     * This function is suspend because JavaScript requires async initialization
     * of the cryptographic library for SHA-256 checksum validation. On JVM and
     * Native platforms, the suspend keyword has zero overhead.
     *
     * @param mnemonic Space-separated mnemonic phrase
     * @param language Word list language (default: English)
     * @return Original entropy bytes
     * @throws InvalidMnemonicException if word count is invalid
     * @throws InvalidWordException if a word is not in the word list
     * @throws InvalidChecksumException if checksum validation fails
     *
     * Example:
     * ```kotlin
     * val mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
     * val entropy = MnemonicUtils.mnemonicToEntropy(mnemonic)
     * // entropy is 16 bytes of zeros
     * ```
     */
    public suspend fun mnemonicToEntropy(
        mnemonic: String,
        language: MnemonicLanguage = MnemonicLanguage.ENGLISH
    ): ByteArray {
        val words = normalizeWords(mnemonic)

        // Validate word count (must be 12, 15, 18, 21, or 24)
        if (words.size % 3 != 0 || words.size < 12 || words.size > 24) {
            throw InvalidMnemonicException(
                "Invalid word count: ${words.size}. Must be 12, 15, 18, 21, or 24."
            )
        }

        // Convert words to bits using O(1) HashMap lookup
        val bits = StringBuilder()
        for (word in words) {
            val normalizedWord = word.lowercase()
            val index = WordList.getWordIndex(normalizedWord, language)
                ?: throw InvalidWordException(word, language)
            bits.append(index.toString(2).padStart(MnemonicConstants.BITS_PER_WORD, '0'))
        }

        // Split entropy and checksum
        // Total bits = word_count * 11
        // Entropy bits = (total_bits * 32) / 33
        // Checksum bits = total_bits - entropy_bits
        val totalBits = bits.length
        val dividerIndex = (totalBits / MnemonicConstants.MNEMONIC_DIVIDER_RATIO) *
                MnemonicConstants.ENTROPY_MULTIPLE_BITS
        val entropyBits = bits.substring(0, dividerIndex)
        val checksumBits = bits.substring(dividerIndex)

        // Convert entropy bits to bytes
        val entropyBytes = entropyBits.chunked(8).map { it.toInt(2).toByte() }.toByteArray()

        // Validate entropy size
        validateEntropy(entropyBytes)

        // Verify checksum using constant-time comparison
        val expectedChecksum = deriveChecksumBits(entropyBytes)
        if (!constantTimeEquals(checksumBits.encodeToByteArray(), expectedChecksum.encodeToByteArray())) {
            throw InvalidChecksumException()
        }

        return entropyBytes
    }

    /**
     * Validates a BIP-39 mnemonic phrase.
     *
     * Checks that all words are in the word list and the checksum is valid.
     * This is a convenience function that returns a boolean instead of throwing exceptions.
     *
     * This function is suspend because JavaScript requires async initialization
     * of the cryptographic library for checksum validation. On JVM and Native
     * platforms, the suspend keyword has zero overhead.
     *
     * @param mnemonic Space-separated mnemonic phrase
     * @param language Word list language (default: English)
     * @return true if the mnemonic is valid, false otherwise
     *
     * Example:
     * ```kotlin
     * val isValid = MnemonicUtils.validateMnemonic(
     *     "illness spike retreat truth genius clock brain pass fit cave bargain toe"
     * )
     * // isValid is true
     *
     * val isInvalid = MnemonicUtils.validateMnemonic("invalid mnemonic words")
     * // isInvalid is false
     * ```
     */
    public suspend fun validateMnemonic(
        mnemonic: String,
        language: MnemonicLanguage = MnemonicLanguage.ENGLISH
    ): Boolean {
        return try {
            mnemonicToEntropy(mnemonic, language)
            true
        } catch (_: Sep05Exception) {
            false
        }
    }

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
     *
     * Example:
     * ```kotlin
     * val language = MnemonicUtils.detectLanguage(
     *     "illness spike retreat truth genius clock brain pass fit cave bargain toe"
     * )
     * // language is MnemonicLanguage.ENGLISH
     * ```
     */
    public fun detectLanguage(mnemonic: String): MnemonicLanguage? {
        val words = normalizeWords(mnemonic).map { it.lowercase() }

        if (words.isEmpty()) return null

        for (language in MnemonicLanguage.entries) {
            val allFound = words.all { word ->
                WordList.getWordIndex(word, language) != null
            }
            if (allFound) {
                return language
            }
        }
        return null
    }

    /**
     * Converts a BIP-39 mnemonic phrase to a 64-byte seed.
     *
     * Uses PBKDF2-HMAC-SHA512 with the following parameters per BIP-39:
     * - Password: NFKD-normalized mnemonic phrase (UTF-8 encoded)
     * - Salt: "mnemonic" + NFKD-normalized passphrase (UTF-8 encoded)
     * - Iterations: 2048
     * - Output length: 64 bytes (512 bits)
     *
     * Both the mnemonic and passphrase are NFKD-normalized per BIP-39 specification
     * to ensure consistent seed derivation across different Unicode representations.
     *
     * The passphrase provides an additional security layer. Using different passphrases
     * with the same mnemonic produces completely different seeds and wallets.
     *
     * **Warning**: Lost passphrases cannot be recovered. The mnemonic alone cannot
     * restore access to funds if a passphrase was used.
     *
     * @param mnemonic Space-separated mnemonic phrase
     * @param passphrase Optional passphrase for additional security (default: empty string)
     * @return 64-byte BIP-39 seed
     *
     * Example:
     * ```kotlin
     * val mnemonic = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
     *
     * // Without passphrase
     * val seed = MnemonicUtils.mnemonicToSeed(mnemonic)
     *
     * // With passphrase (creates a different wallet)
     * val seedWithPassphrase = MnemonicUtils.mnemonicToSeed(mnemonic, "secret passphrase")
     * ```
     */
    public suspend fun mnemonicToSeed(
        mnemonic: String,
        passphrase: String = ""
    ): ByteArray {
        // NFKD normalize both mnemonic and passphrase per BIP-39 specification
        val normalizedMnemonic = normalizeNfkd(mnemonic)
        val normalizedPassphrase = normalizeNfkd(passphrase)
        val salt = (MnemonicConstants.PBKDF2_SALT_PREFIX + normalizedPassphrase).encodeToByteArray()
        val password = normalizedMnemonic.encodeToByteArray()

        return pbkdf2HmacSha512(
            password = password,
            salt = salt,
            iterations = MnemonicConstants.PBKDF2_ITERATIONS,
            keyLength = MnemonicConstants.PBKDF2_KEY_LENGTH
        )
    }

    /**
     * Converts a BIP-39 mnemonic phrase to a hex-encoded 64-byte seed.
     *
     * This is a convenience function that combines [mnemonicToSeed] with hex encoding.
     * The resulting string is 128 hexadecimal characters (64 bytes).
     *
     * @param mnemonic Space-separated mnemonic phrase
     * @param passphrase Optional passphrase for additional security (default: empty string)
     * @return 128-character hex string representing the 64-byte seed
     *
     * Example:
     * ```kotlin
     * val seedHex = MnemonicUtils.mnemonicToSeedHex(
     *     "illness spike retreat truth genius clock brain pass fit cave bargain toe"
     * )
     * // seedHex is "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497ee4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186"
     * ```
     */
    public suspend fun mnemonicToSeedHex(
        mnemonic: String,
        passphrase: String = ""
    ): String {
        return HexCodec.encode(mnemonicToSeed(mnemonic, passphrase))
    }

    /**
     * Gets the strength level of a mnemonic phrase based on its word count.
     *
     * @param mnemonic Space-separated mnemonic phrase
     * @return Strength level, or null if the word count is not valid (12, 15, 18, 21, or 24)
     *
     * Example:
     * ```kotlin
     * val strength = MnemonicUtils.getMnemonicStrength(
     *     "illness spike retreat truth genius clock brain pass fit cave bargain toe"
     * )
     * // strength is MnemonicStrength.BITS_128 (12 words = 128 bits)
     * ```
     */
    public fun getMnemonicStrength(mnemonic: String): MnemonicStrength? {
        val wordCount = normalizeWords(mnemonic).size
        return MnemonicStrength.fromWordCount(wordCount)
    }

    // ========== Private Helper Functions ==========

    /**
     * Normalizes a mnemonic string to a list of words.
     *
     * - Trims leading/trailing whitespace
     * - Splits on any whitespace (handles multiple spaces, tabs, newlines)
     * - Filters out empty strings
     */
    private fun normalizeWords(mnemonic: String): List<String> {
        return mnemonic.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    /**
     * Validates entropy size according to BIP-39 requirements.
     *
     * Valid sizes: 16, 20, 24, 28, or 32 bytes (128, 160, 192, 224, or 256 bits)
     *
     * @throws InvalidEntropyException if entropy size is invalid
     */
    private fun validateEntropy(entropy: ByteArray) {
        if (entropy.size < MnemonicConstants.MIN_ENTROPY_BYTES ||
            entropy.size > MnemonicConstants.MAX_ENTROPY_BYTES ||
            entropy.size % MnemonicConstants.ENTROPY_MULTIPLE_BYTES != 0
        ) {
            throw InvalidEntropyException(
                "Invalid entropy size: ${entropy.size} bytes. " +
                        "Must be 16, 20, 24, 28, or 32 bytes."
            )
        }
    }

    /**
     * Derives checksum bits from entropy using SHA-256.
     *
     * Per BIP-39: "A checksum is generated by taking the first ENT / 32 bits
     * of its SHA256 hash."
     *
     * @return Binary string of checksum bits
     */
    private suspend fun deriveChecksumBits(entropy: ByteArray): String {
        val hash = sha256(entropy)
        val checksumBits = entropy.size * 8 / MnemonicConstants.CHECKSUM_BITS_PER_32_ENT
        return bytesToBinaryString(hash).take(checksumBits)
    }

    /**
     * Converts a byte array to a binary string.
     *
     * Each byte is converted to an 8-bit binary string with leading zeros.
     *
     * @return Binary string representation
     */
    private fun bytesToBinaryString(bytes: ByteArray): String {
        return bytes.joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(2).padStart(8, '0')
        }
    }
}
