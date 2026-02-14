// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep05.exceptions

/**
 * Base exception class for SEP-5 key derivation errors.
 *
 * All SEP-5-specific exceptions extend this class to enable unified error handling
 * while providing specific error types for different failure scenarios.
 *
 * This exception hierarchy allows applications to handle key derivation errors at different levels:
 * - Catch Sep05Exception for general key derivation error handling
 * - Catch specific subclasses for precise error recovery
 *
 * Common error scenarios:
 * - [InvalidMnemonicException]: General mnemonic validation failure
 * - [InvalidEntropyException]: Entropy size or format error
 * - [InvalidChecksumException]: Mnemonic checksum validation failed
 * - [InvalidWordException]: Word not found in word list
 * - [InvalidPathException]: Invalid BIP-32 derivation path
 *
 * Example - General error handling:
 * ```kotlin
 * try {
 *     val m = Mnemonic.from(phrase)
 * } catch (e: InvalidWordException) {
 *     println("Unknown word '${e.word}' in ${e.language.name} word list")
 * } catch (e: InvalidChecksumException) {
 *     println("Mnemonic checksum failed, check for typos")
 * } catch (e: Sep05Exception) {
 *     println("Key derivation error: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [InvalidMnemonicException] for general mnemonic errors
 * - [InvalidEntropyException] for entropy errors
 * - [InvalidChecksumException] for checksum failures
 * - [InvalidWordException] for unknown words
 * - [InvalidPathException] for path format errors
 * - [SEP-0005 Specification](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0005.md)
 *
 * @property message Human-readable error description
 * @property cause Optional underlying cause of the error
 */
open class Sep05Exception(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    override fun toString(): String {
        return "SEP-05 error: $message"
    }
}
