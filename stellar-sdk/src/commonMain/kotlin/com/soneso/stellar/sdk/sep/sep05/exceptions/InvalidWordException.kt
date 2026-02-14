// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep05.exceptions

import com.soneso.stellar.sdk.sep.sep05.MnemonicLanguage

/**
 * Exception thrown when a word is not found in the specified word list.
 *
 * BIP-39 defines standardized word lists for multiple languages. Each list contains
 * exactly 2048 unique words. This exception is thrown when a mnemonic contains a
 * word that does not exist in the expected word list.
 *
 * Common causes:
 * - Misspelled word in the mnemonic
 * - Using a mnemonic from one language with a different language's word list
 * - Word truncated or corrupted
 * - Non-BIP-39 word list used during mnemonic generation
 *
 * Recovery actions:
 * - Check spelling of the reported word
 * - Try a different language word list if the mnemonic was generated elsewhere
 * - Verify the complete word was entered (some words are similar prefixes)
 *
 * Example:
 * ```kotlin
 * try {
 *     val m = Mnemonic.from(
 *         mnemonic = "abandonn abandon abandon abandon abandon abandon " +
 *             "abandon abandon abandon abandon abandon about",
 *         language = MnemonicLanguage.ENGLISH
 *     )
 * } catch (e: InvalidWordException) {
 *     println("Unknown word: '${e.word}'")
 *     println("Language: ${e.language.name}")
 *     println("Please check spelling or try a different language")
 * }
 * ```
 *
 * See also:
 * - [MnemonicLanguage] for supported languages
 * - [InvalidChecksumException] for valid words with wrong checksum
 * - [InvalidMnemonicException] for general mnemonic errors
 * - [Sep05Exception] base class
 *
 * @property word The word that was not found in the word list
 * @property language The language of the word list that was searched
 */
class InvalidWordException(
    val word: String,
    val language: MnemonicLanguage
) : Sep05Exception("Word '$word' not found in ${language.name} word list") {
    override fun toString(): String {
        return "SEP-05 invalid word: '$word' not in ${language.name} word list"
    }
}
