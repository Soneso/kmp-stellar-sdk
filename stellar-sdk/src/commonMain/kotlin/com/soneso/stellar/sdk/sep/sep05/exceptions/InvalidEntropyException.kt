// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep05.exceptions

/**
 * Exception thrown when entropy has invalid size or format.
 *
 * BIP-39 requires entropy to be a multiple of 32 bits (4 bytes) with a size
 * between 128 and 256 bits (16 to 32 bytes). Valid entropy sizes:
 * - 128 bits (16 bytes) -> 12 word mnemonic
 * - 160 bits (20 bytes) -> 15 word mnemonic
 * - 192 bits (24 bytes) -> 18 word mnemonic
 * - 224 bits (28 bytes) -> 21 word mnemonic
 * - 256 bits (32 bytes) -> 24 word mnemonic
 *
 * Common causes:
 * - Entropy size not a multiple of 4 bytes
 * - Entropy too small (less than 16 bytes)
 * - Entropy too large (more than 32 bytes)
 * - Entropy is null or empty
 *
 * Example:
 * ```kotlin
 * try {
 *     val mnemonic = Mnemonic.fromEntropy(byteArrayOf(1, 2, 3)) // Too small
 * } catch (e: InvalidEntropyException) {
 *     println("Invalid entropy: ${e.message}")
 * }
 * ```
 *
 * See also:
 * - [Sep05Exception] base class
 *
 * @param message Description of the entropy validation failure
 */
class InvalidEntropyException(
    message: String
) : Sep05Exception(message) {
    override fun toString(): String {
        return "SEP-05 invalid entropy: $message"
    }
}
