// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.sep.sep05.exceptions

/**
 * Exception thrown when a BIP-32 derivation path is invalid.
 *
 * SEP-5 uses BIP-32 hierarchical deterministic key derivation with SLIP-10.
 * The standard Stellar path is "m/44'/148'/0'" where:
 * - m: master node
 * - 44': BIP-44 purpose (hardened)
 * - 148': Stellar coin type (hardened)
 * - 0': account index (hardened)
 *
 * Valid path format:
 * - Must start with "m/"
 * - Path components separated by "/"
 * - Each component is a 31-bit unsigned integer (0 to 2147483647)
 * - Hardened derivation indicated by "'" or "h" suffix
 * - For Stellar, all derivation should be hardened
 *
 * Common causes:
 * - Missing "m/" prefix
 * - Invalid characters in path
 * - Index out of valid range
 * - Empty path components
 *
 * Example:
 * ```kotlin
 * try {
 *     val keypair = wallet.deriveKeyPair("invalid/path")
 * } catch (e: InvalidPathException) {
 *     println("Invalid path: ${e.path}")
 *     println("Error: ${e.message}")
 *     println("Use format: m/44'/148'/0'")
 * }
 * ```
 *
 * See also:
 * - [Sep05Exception] base class
 * - [BIP-32 Specification](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki)
 * - [SLIP-10 Specification](https://github.com/satoshilabs/slips/blob/master/slip-0010.md)
 *
 * @property path The invalid derivation path
 * @param message Description of the path validation failure
 */
class InvalidPathException(
    val path: String,
    message: String = "Invalid BIP-32 derivation path: $path"
) : Sep05Exception(message) {
    override fun toString(): String {
        return "SEP-05 invalid path: $path"
    }
}
