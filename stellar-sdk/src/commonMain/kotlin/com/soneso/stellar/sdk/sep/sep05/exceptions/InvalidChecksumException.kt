// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep05.exceptions

/**
 * Exception thrown when mnemonic checksum validation fails.
 *
 * BIP-39 mnemonics include a checksum derived from the SHA-256 hash of the entropy.
 * The checksum is appended to the entropy before encoding to words. When decoding,
 * the checksum is recalculated and compared. A mismatch indicates the mnemonic
 * was corrupted or contains a typo.
 *
 * Checksum sizes by mnemonic length:
 * - 12 words: 4-bit checksum
 * - 15 words: 5-bit checksum
 * - 18 words: 6-bit checksum
 * - 21 words: 7-bit checksum
 * - 24 words: 8-bit checksum
 *
 * Recovery actions:
 * - Double-check spelling of each word
 * - Verify word order is correct
 * - Ensure using the correct word list language
 *
 * Example:
 * ```kotlin
 * try {
 *     // Mnemonic with incorrect last word (checksum word)
 *     val m = Mnemonic.from(
 *         "abandon abandon abandon abandon abandon abandon " +
 *         "abandon abandon abandon abandon abandon wrong"
 *     )
 * } catch (e: InvalidChecksumException) {
 *     println("Checksum failed: ${e.message}")
 *     println("Please verify your mnemonic phrase for typos")
 * }
 * ```
 *
 * See also:
 * - [InvalidWordException] for words not in the word list
 * - [InvalidMnemonicException] for general mnemonic errors
 * - [Sep05Exception] base class
 *
 * @param message Description of the checksum failure (defaults to standard message)
 */
class InvalidChecksumException(
    message: String = "Mnemonic checksum validation failed"
) : Sep05Exception(message) {
    override fun toString(): String {
        return "SEP-05 checksum error: $message"
    }
}
