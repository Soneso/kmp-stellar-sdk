// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep05.exceptions

/**
 * Exception thrown when a mnemonic phrase is invalid.
 *
 * This exception indicates general mnemonic validation failures that are not covered
 * by more specific exceptions like [InvalidChecksumException] or [InvalidWordException].
 *
 * Common causes:
 * - Empty mnemonic phrase
 * - Invalid word count (must be 12, 15, 18, 21, or 24 words)
 * - Null or blank input
 *
 * Example:
 * ```kotlin
 * try {
 *     val m = Mnemonic.from("")
 * } catch (e: InvalidMnemonicException) {
 *     println("Invalid mnemonic: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [InvalidChecksumException] for checksum validation failures
 * - [InvalidWordException] for words not in the word list
 * - [Sep05Exception] base class
 *
 * @param message Description of the validation failure
 */
class InvalidMnemonicException(
    message: String
) : Sep05Exception(message) {
    override fun toString(): String {
        return "SEP-05 invalid mnemonic: $message"
    }
}
