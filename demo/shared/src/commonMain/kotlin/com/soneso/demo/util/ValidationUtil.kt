package com.soneso.demo.util

/**
 * Validation utilities for Stellar address and data formats.
 *
 * This utility object provides validation functions for various Stellar data types
 * including account IDs, secret seeds, contract IDs, transaction hashes, and asset codes.
 * These validations ensure that inputs conform to Stellar's encoding standards before
 * being passed to SDK methods.
 *
 * ## Stellar Address Formats
 *
 * Stellar uses StrKey encoding (a variant of Base32) for human-readable addresses:
 *
 * - **Account IDs (G...)**: 56-character strings starting with 'G' (Ed25519 public keys)
 *   Example: `GABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234`
 *
 * - **Secret Seeds (S...)**: 56-character strings starting with 'S' (Ed25519 private keys)
 *   Example: `SABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234`
 *   WARNING: Never share secret seeds - they provide full control over accounts
 *
 * - **Contract IDs (C...)**: 56-character strings starting with 'C' (Soroban contract addresses)
 *   Example: `CABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234`
 *
 * - **Muxed Accounts (M...)**: 69-character strings starting with 'M' (account ID with sub-account)
 *   Example: `MABC...` (not validated by these utilities)
 *
 * ## Transaction Hashes
 *
 * Transaction hashes are 64-character hexadecimal strings representing the SHA-256 hash
 * of the transaction envelope XDR.
 * Example: `abc123def456789012345678901234567890123456789012345678901234`
 *
 * ## Asset Codes
 *
 * Asset codes can be 1-12 characters long and must contain only uppercase letters (A-Z)
 * and digits (0-9). Common examples: `USD`, `USDC`, `BTC`, `EURT`, `MYTOKEN`.
 *
 * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/data-format/strkey">StrKey Encoding</a>
 * @see <a href="https://developers.stellar.org/docs/learn/fundamentals/stellar-data-structures/accounts">Stellar Accounts</a>
 * @see <a href="https://developers.stellar.org/docs/smart-contracts">Soroban Smart Contracts</a>
 */
object StellarValidation {

    /**
     * Validates a Stellar account ID (G... address).
     *
     * Account IDs are Ed25519 public keys encoded in StrKey format. They always:
     * - Start with the character 'G'
     * - Are exactly 56 characters long
     * - Use Base32 encoding with a CRC16 checksum
     *
     * ## Usage
     *
     * ```kotlin
     * val accountId = "GABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234"
     * val error = StellarValidation.validateAccountId(accountId)
     * if (error != null) {
     *     println("Invalid account ID: $error")
     * } else {
     *     println("Account ID is valid")
     * }
     * ```
     *
     * @param accountId The account ID string to validate
     * @return Error message if validation fails, null if valid
     */
    fun validateAccountId(accountId: String): String? {
        return when {
            accountId.isBlank() -> "Account ID cannot be empty"
            !accountId.startsWith('G') -> "Account ID must start with 'G' (got: ${accountId.take(1)})"
            accountId.length != 56 -> "Account ID must be exactly 56 characters long (got: ${accountId.length})"
            else -> null
        }
    }

    /**
     * Validates a Stellar secret seed (S... address).
     *
     * Secret seeds are Ed25519 private keys encoded in StrKey format. They always:
     * - Start with the character 'S'
     * - Are exactly 56 characters long
     * - Use Base32 encoding with a CRC16 checksum
     *
     * **SECURITY WARNING**: Secret seeds provide full control over accounts. Never:
     * - Share them with anyone
     * - Store them in plaintext
     * - Log them to console or files
     * - Commit them to version control
     * - Send them over insecure channels
     *
     * ## Usage
     *
     * ```kotlin
     * val secretSeed = "SABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234"
     * val error = StellarValidation.validateSecretSeed(secretSeed)
     * if (error != null) {
     *     println("Invalid secret seed: $error")
     * } else {
     *     // Proceed with caution
     * }
     * ```
     *
     * @param secretSeed The secret seed string to validate
     * @return Error message if validation fails, null if valid
     */
    fun validateSecretSeed(secretSeed: String): String? {
        return when {
            secretSeed.isBlank() -> "Secret seed cannot be empty"
            !secretSeed.startsWith('S') -> "Secret seed must start with 'S'"
            secretSeed.length != 56 -> "Secret seed must be exactly 56 characters long (got: ${secretSeed.length})"
            else -> null
        }
    }

    /**
     * Validates a Stellar contract ID (C... address).
     *
     * Contract IDs are Soroban smart contract addresses encoded in StrKey format. They always:
     * - Start with the character 'C'
     * - Are exactly 56 characters long
     * - Use Base32 encoding with a CRC16 checksum
     *
     * Contract IDs uniquely identify deployed smart contracts on the Stellar network.
     * They are derived from the contract's WASM hash and deployment parameters.
     *
     * ## Usage
     *
     * ```kotlin
     * val contractId = "CABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234"
     * val error = StellarValidation.validateContractId(contractId)
     * if (error != null) {
     *     println("Invalid contract ID: $error")
     * } else {
     *     println("Contract ID is valid")
     * }
     * ```
     *
     * @param contractId The contract ID string to validate
     * @return Error message if validation fails, null if valid
     */
    fun validateContractId(contractId: String): String? {
        return when {
            contractId.isBlank() -> "Contract ID cannot be empty"
            !contractId.startsWith('C') -> "Contract ID must start with 'C' (got: ${contractId.take(1)})"
            contractId.length != 56 -> "Contract ID must be exactly 56 characters long (got: ${contractId.length})"
            else -> null
        }
    }

    /**
     * Validates a Stellar transaction hash.
     *
     * Transaction hashes are SHA-256 hashes of transaction envelope XDR, represented as
     * 64-character hexadecimal strings. They:
     * - Are exactly 64 characters long
     * - Contain only hexadecimal characters (0-9, a-f, A-F)
     * - Uniquely identify transactions on the Stellar network
     *
     * Transaction hashes are used to query transaction status and details from Horizon
     * and Soroban RPC servers.
     *
     * ## Usage
     *
     * ```kotlin
     * val hash = "abc123def456789012345678901234567890123456789012345678901234"
     * val error = StellarValidation.validateTransactionHash(hash)
     * if (error != null) {
     *     println("Invalid transaction hash: $error")
     * } else {
     *     println("Transaction hash is valid")
     * }
     * ```
     *
     * @param hash The transaction hash string to validate
     * @return Error message if validation fails, null if valid
     */
    fun validateTransactionHash(hash: String): String? {
        return when {
            hash.isBlank() -> "Transaction hash cannot be empty"
            hash.length != 64 -> "Transaction hash must be exactly 64 characters long (got: ${hash.length})"
            !hash.matches(Regex("^[0-9a-fA-F]{64}$")) -> "Transaction hash must be a valid hexadecimal string (0-9, a-f, A-F)"
            else -> null
        }
    }

    /**
     * Validates a Stellar asset code.
     *
     * Asset codes identify issued assets (tokens) on the Stellar network. They must:
     * - Be between 1 and 12 characters long
     * - Contain only uppercase letters (A-Z) and digits (0-9)
     * - Not be empty or blank
     *
     * Common examples:
     * - Short codes: `USD`, `EUR`, `BTC`, `ETH`
     * - Medium codes: `USDC`, `EURT`, `USDT`
     * - Long codes: `MYTOKEN`, `PROJECTCOIN`
     *
     * ## Usage
     *
     * ```kotlin
     * val assetCode = "USDC"
     * val error = StellarValidation.validateAssetCode(assetCode)
     * if (error != null) {
     *     println("Invalid asset code: $error")
     * } else {
     *     println("Asset code is valid")
     * }
     * ```
     *
     * **Note**: This does NOT validate "native" or "XLM" - those should be handled
     * separately as they refer to Stellar's native asset (lumens).
     *
     * @param assetCode The asset code string to validate
     * @return Error message if validation fails, null if valid
     */
    fun validateAssetCode(assetCode: String): String? {
        return when {
            assetCode.isBlank() -> "Asset code cannot be empty"
            assetCode.length > 12 -> "Asset code cannot exceed 12 characters (got: ${assetCode.length})"
            else -> {
                // Validate asset code contains only alphanumeric characters
                val invalidChars = assetCode.filter { char ->
                    char !in 'A'..'Z' && char !in '0'..'9'
                }
                if (invalidChars.isNotEmpty()) {
                    "Asset code must contain only uppercase letters and digits. Invalid characters: '$invalidChars'"
                } else {
                    null
                }
            }
        }
    }
}
