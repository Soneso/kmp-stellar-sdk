import Foundation

/// Validation utilities for Stellar address and data formats.
///
/// This utility provides validation functions for various Stellar data types
/// including account IDs, secret seeds, contract IDs, transaction hashes, and asset codes.
/// These validations ensure that inputs conform to Stellar's encoding standards before
/// being passed to SDK methods.
///
/// ## Stellar Address Formats
///
/// Stellar uses StrKey encoding (a variant of Base32) for human-readable addresses:
///
/// - **Account IDs (G...)**: 56-character strings starting with 'G' (Ed25519 public keys)
///   Example: `GABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234`
///
/// - **Secret Seeds (S...)**: 56-character strings starting with 'S' (Ed25519 private keys)
///   Example: `SABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234`
///   WARNING: Never share secret seeds - they provide full control over accounts
///
/// - **Contract IDs (C...)**: 56-character strings starting with 'C' (Soroban contract addresses)
///   Example: `CABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234`
///
/// - **Muxed Accounts (M...)**: 69-character strings starting with 'M' (account ID with sub-account)
///   Example: `MABC...` (not validated by these utilities)
///
/// ## Transaction Hashes
///
/// Transaction hashes are 64-character hexadecimal strings representing the SHA-256 hash
/// of the transaction envelope XDR.
/// Example: `abc123def456789012345678901234567890123456789012345678901234`
///
/// ## Asset Codes
///
/// Asset codes can be 1-12 alphanumeric characters (a-z, A-Z, 0-9).
/// Common examples: `USD`, `USDC`, `BTC`, `yUSDC`, `yETH`.
struct FormValidation {

    /// Validates an account ID field for UI display.
    ///
    /// This is a UI-friendly validation that provides short error messages suitable for form fields.
    ///
    /// Account IDs are Ed25519 public keys encoded in StrKey format. They always:
    /// - Start with the character 'G'
    /// - Are exactly 56 characters long
    /// - Use Base32 encoding with a CRC16 checksum
    ///
    /// ## Usage
    ///
    /// ```swift
    /// let accountId = "GABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234"
    /// if let error = FormValidation.validateAccountIdField(accountId) {
    ///     print("Invalid account ID: \(error)")
    /// } else {
    ///     print("Account ID is valid")
    /// }
    /// ```
    ///
    /// - Parameter value: The account ID string to validate
    /// - Returns: Short error message if invalid, nil if valid
    static func validateAccountIdField(_ value: String) -> String? {
        if value.isEmpty {
            return "Enter an account ID (G...)"
        }
        return validateAccountId(value)
    }

    /// Validates a secret seed field for UI display.
    ///
    /// This is a UI-friendly validation that provides short error messages suitable for form fields.
    ///
    /// Secret seeds are Ed25519 private keys encoded in StrKey format. They always:
    /// - Start with the character 'S'
    /// - Are exactly 56 characters long
    /// - Use Base32 encoding with a CRC16 checksum
    ///
    /// **SECURITY WARNING**: Secret seeds provide full control over accounts. Never:
    /// - Share them with anyone
    /// - Store them in plaintext
    /// - Log them to console or files
    /// - Commit them to version control
    /// - Send them over insecure channels
    ///
    /// ## Usage
    ///
    /// ```swift
    /// let secretSeed = "SABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234"
    /// if let error = FormValidation.validateSecretSeedField(secretSeed) {
    ///     print("Invalid secret seed: \(error)")
    /// } else {
    ///     // Proceed with caution
    /// }
    /// ```
    ///
    /// - Parameter value: The secret seed string to validate
    /// - Returns: Short error message if invalid, nil if valid
    static func validateSecretSeedField(_ value: String) -> String? {
        if value.isEmpty {
            return "Enter a secret seed (S...)"
        }
        return validateSecretSeed(value)
    }

    /// Validates a contract ID field for UI display.
    ///
    /// This is a UI-friendly validation that provides short error messages suitable for form fields.
    ///
    /// Contract IDs are Soroban smart contract addresses encoded in StrKey format. They always:
    /// - Start with the character 'C'
    /// - Are exactly 56 characters long
    /// - Use Base32 encoding with a CRC16 checksum
    ///
    /// Contract IDs uniquely identify deployed smart contracts on the Stellar network.
    /// They are derived from the contract's WASM hash and deployment parameters.
    ///
    /// ## Usage
    ///
    /// ```swift
    /// let contractId = "CABC7IJKLMNOPQRSTUVWXYZ234567ABCDEFGHIJKLMNOPQRSTUVWXYZ234"
    /// if let error = FormValidation.validateContractIdField(contractId) {
    ///     print("Invalid contract ID: \(error)")
    /// } else {
    ///     print("Contract ID is valid")
    /// }
    /// ```
    ///
    /// - Parameter value: The contract ID string to validate
    /// - Returns: Short error message if invalid, nil if valid
    static func validateContractIdField(_ value: String) -> String? {
        if value.isEmpty {
            return "Enter a contract ID (C...)"
        }
        return validateContractId(value)
    }

    /// Validates a transaction hash field for UI display.
    ///
    /// This is a UI-friendly validation that provides short error messages suitable for form fields.
    ///
    /// Transaction hashes are SHA-256 hashes of transaction envelope XDR, represented as
    /// 64-character hexadecimal strings. They:
    /// - Are exactly 64 characters long
    /// - Contain only hexadecimal characters (0-9, a-f, A-F)
    /// - Uniquely identify transactions on the Stellar network
    ///
    /// Transaction hashes are used to query transaction status and details from Horizon
    /// and Soroban RPC servers.
    ///
    /// ## Usage
    ///
    /// ```swift
    /// let hash = "abc123def456789012345678901234567890123456789012345678901234"
    /// if let error = FormValidation.validateTransactionHashField(hash) {
    ///     print("Invalid transaction hash: \(error)")
    /// } else {
    ///     print("Transaction hash is valid")
    /// }
    /// ```
    ///
    /// - Parameter value: The transaction hash string to validate
    /// - Returns: Short error message if invalid, nil if valid
    static func validateTransactionHashField(_ value: String) -> String? {
        if value.isEmpty {
            return "Enter a transaction hash"
        }
        return validateTransactionHash(value)
    }

    /// Validates an asset code field for UI display.
    ///
    /// This is a UI-friendly validation that provides short error messages suitable for form fields.
    ///
    /// Asset codes identify issued assets (tokens) on the Stellar network. They must:
    /// - Be between 1 and 12 characters long
    /// - Contain only alphanumeric characters (a-z, A-Z, 0-9)
    /// - Not be empty or blank
    ///
    /// Common examples:
    /// - Short codes: `USD`, `EUR`, `BTC`, `ETH`
    /// - Medium codes: `USDC`, `EURT`, `USDT`
    /// - Long codes: `MYTOKEN`, `PROJECTCOIN`
    ///
    /// ## Usage
    ///
    /// ```swift
    /// let assetCode = "USDC"
    /// if let error = FormValidation.validateAssetCodeField(assetCode) {
    ///     print("Invalid asset code: \(error)")
    /// } else {
    ///     print("Asset code is valid")
    /// }
    /// ```
    ///
    /// **Note**: This does NOT validate "native" or "XLM" - those should be handled
    /// separately as they refer to Stellar's native asset (lumens).
    ///
    /// - Parameter value: The asset code string to validate
    /// - Returns: Short error message if invalid, nil if valid
    static func validateAssetCodeField(_ value: String) -> String? {
        if value.isEmpty {
            return "Enter an asset code"
        }
        return validateAssetCode(value)
    }

    // MARK: - Core Validation Logic

    /// Validates a Stellar account ID (G... address).
    ///
    /// - Parameter accountId: The account ID string to validate
    /// - Returns: Error message if validation fails, nil if valid
    private static func validateAccountId(_ accountId: String) -> String? {
        if accountId.trimmingCharacters(in: .whitespaces).isEmpty {
            return "Account ID cannot be empty"
        }
        if !accountId.hasPrefix("G") {
            let prefix = accountId.isEmpty ? "" : String(accountId.prefix(1))
            return "Account ID must start with 'G' (got: \(prefix))"
        }
        if accountId.count != 56 {
            return "Account ID must be exactly 56 characters long (got: \(accountId.count))"
        }
        return nil
    }

    /// Validates a Stellar secret seed (S... address).
    ///
    /// - Parameter secretSeed: The secret seed string to validate
    /// - Returns: Error message if validation fails, nil if valid
    private static func validateSecretSeed(_ secretSeed: String) -> String? {
        if secretSeed.trimmingCharacters(in: .whitespaces).isEmpty {
            return "Secret seed cannot be empty"
        }
        if !secretSeed.hasPrefix("S") {
            return "Secret seed must start with 'S'"
        }
        if secretSeed.count != 56 {
            return "Secret seed must be exactly 56 characters long (got: \(secretSeed.count))"
        }
        return nil
    }

    /// Validates a Stellar contract ID (C... address).
    ///
    /// - Parameter contractId: The contract ID string to validate
    /// - Returns: Error message if validation fails, nil if valid
    private static func validateContractId(_ contractId: String) -> String? {
        if contractId.trimmingCharacters(in: .whitespaces).isEmpty {
            return "Contract ID cannot be empty"
        }
        if !contractId.hasPrefix("C") {
            let prefix = contractId.isEmpty ? "" : String(contractId.prefix(1))
            return "Contract ID must start with 'C' (got: \(prefix))"
        }
        if contractId.count != 56 {
            return "Contract ID must be exactly 56 characters long (got: \(contractId.count))"
        }
        return nil
    }

    /// Validates a Stellar transaction hash.
    ///
    /// - Parameter hash: The transaction hash string to validate
    /// - Returns: Error message if validation fails, nil if valid
    private static func validateTransactionHash(_ hash: String) -> String? {
        if hash.trimmingCharacters(in: .whitespaces).isEmpty {
            return "Transaction hash cannot be empty"
        }
        if hash.count != 64 {
            return "Transaction hash must be exactly 64 characters long (got: \(hash.count))"
        }
        // Validate hexadecimal characters
        let hexCharacterSet = CharacterSet(charactersIn: "0123456789abcdefABCDEF")
        if hash.rangeOfCharacter(from: hexCharacterSet.inverted) != nil {
            return "Transaction hash must be a valid hexadecimal string (0-9, a-f, A-F)"
        }
        return nil
    }

    /// Validates a Stellar asset code.
    ///
    /// - Parameter assetCode: The asset code string to validate
    /// - Returns: Error message if validation fails, nil if valid
    private static func validateAssetCode(_ assetCode: String) -> String? {
        if assetCode.trimmingCharacters(in: .whitespaces).isEmpty {
            return "Asset code cannot be empty"
        }
        if assetCode.count > 12 {
            return "Asset code cannot exceed 12 characters (got: \(assetCode.count))"
        }
        // Validate asset code contains only alphanumeric characters (a-z, A-Z, 0-9)
        let validCharacterSet = CharacterSet.alphanumerics
        if assetCode.rangeOfCharacter(from: validCharacterSet.inverted) != nil {
            let invalidChars = assetCode.filter { char in
                !validCharacterSet.contains(char.unicodeScalars.first!)
            }
            return "Asset code must contain only alphanumeric characters (a-z, A-Z, 0-9). Invalid characters: '\(invalidChars)'"
        }
        return nil
    }
}
