//
//  FormValidation.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import Foundation

/// Validation utilities for Stellar address formats and numeric amounts.
///
/// All validation is performed in pure Swift with no dependency on the Kotlin SDK. Functions
/// return an error message string when validation fails and `nil` when the input is valid.
/// This matches the convention used in SwiftUI form fields where a `nil` result means "no error".
///
/// ## Stellar Address Formats
///
/// Stellar uses StrKey encoding (a Base32 variant with a CRC16 checksum) for addresses:
///
/// - **Account IDs (G...)**: 56-character Ed25519 public keys.
/// - **Secret Seeds (S...)**: 56-character Ed25519 private keys. Never share or log these.
/// - **Contract IDs (C...)**: 56-character Soroban smart contract addresses.
///
/// ## Transaction Hashes
///
/// SHA-256 hashes of transaction envelope XDR, encoded as 64-character lowercase hex strings.
///
/// ## Amounts
///
/// Stellar amounts are positive decimal numbers with at most 7 decimal places.
/// Internally amounts are stored in stroops (1 XLM = 10,000,000 stroops).
struct FormValidation {

    // MARK: - Field-Level Helpers (empty-aware)

    /// Validates an account ID for use in a form field.
    ///
    /// Returns a prompt when the field is empty and a format error for non-empty invalid values,
    /// making it suitable for `TextField` `supportingText` / inline error display.
    ///
    /// - Parameter value: The account ID string entered by the user.
    /// - Returns: Error message if invalid, `nil` if valid.
    static func validateAccountIdField(_ value: String) -> String? {
        guard !value.isEmpty else { return "Enter an account ID (G...)" }
        return validateAccountId(value)
    }

    /// Validates a secret seed for use in a form field.
    ///
    /// - Parameter value: The secret seed string entered by the user.
    /// - Returns: Error message if invalid, `nil` if valid.
    static func validateSecretSeedField(_ value: String) -> String? {
        guard !value.isEmpty else { return "Enter a secret seed (S...)" }
        return validateSecretSeed(value)
    }

    /// Validates a contract ID for use in a form field.
    ///
    /// - Parameter value: The contract ID string entered by the user.
    /// - Returns: Error message if invalid, `nil` if valid.
    static func validateContractIdField(_ value: String) -> String? {
        guard !value.isEmpty else { return "Enter a contract ID (C...)" }
        return validateContractId(value)
    }

    /// Validates a transaction hash for use in a form field.
    ///
    /// - Parameter value: The transaction hash string entered by the user.
    /// - Returns: Error message if invalid, `nil` if valid.
    static func validateTransactionHashField(_ value: String) -> String? {
        guard !value.isEmpty else { return "Enter a transaction hash" }
        return validateTransactionHash(value)
    }

    /// Validates a transfer amount for use in a form field.
    ///
    /// - Parameter value: The amount string entered by the user.
    /// - Returns: Error message if invalid, `nil` if valid.
    static func validateAmountField(_ value: String) -> String? {
        guard !value.isEmpty else { return "Enter an amount" }
        return validateAmount(value)
    }

    // MARK: - Core Validation

    /// Validates a Stellar account ID (G-address).
    ///
    /// - Parameter accountId: The account ID string to validate.
    /// - Returns: Error message if validation fails, `nil` if valid.
    static func validateAccountId(_ accountId: String) -> String? {
        let trimmed = accountId.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return "Account ID cannot be empty" }
        guard trimmed.hasPrefix("G") else {
            let prefix = trimmed.isEmpty ? "" : String(trimmed.prefix(1))
            return "Account ID must start with 'G' (got: '\(prefix)')"
        }
        guard trimmed.count == 56 else {
            return "Account ID must be exactly 56 characters (got: \(trimmed.count))"
        }
        return nil
    }

    /// Validates a Stellar secret seed (S-address).
    ///
    /// - Parameter secretSeed: The secret seed string to validate.
    /// - Returns: Error message if validation fails, `nil` if valid.
    static func validateSecretSeed(_ secretSeed: String) -> String? {
        let trimmed = secretSeed.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return "Secret seed cannot be empty" }
        guard trimmed.hasPrefix("S") else { return "Secret seed must start with 'S'" }
        guard trimmed.count == 56 else {
            return "Secret seed must be exactly 56 characters (got: \(trimmed.count))"
        }
        return nil
    }

    /// Validates a Stellar contract ID (C-address).
    ///
    /// - Parameter contractId: The contract ID string to validate.
    /// - Returns: Error message if validation fails, `nil` if valid.
    static func validateContractId(_ contractId: String) -> String? {
        let trimmed = contractId.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return "Contract ID cannot be empty" }
        guard trimmed.hasPrefix("C") else {
            let prefix = trimmed.isEmpty ? "" : String(trimmed.prefix(1))
            return "Contract ID must start with 'C' (got: '\(prefix)')"
        }
        guard trimmed.count == 56 else {
            return "Contract ID must be exactly 56 characters (got: \(trimmed.count))"
        }
        return nil
    }

    /// Validates a Stellar transaction hash (64-character lowercase hex string).
    ///
    /// - Parameter hash: The transaction hash string to validate.
    /// - Returns: Error message if validation fails, `nil` if valid.
    static func validateTransactionHash(_ hash: String) -> String? {
        let trimmed = hash.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return "Transaction hash cannot be empty" }
        guard trimmed.count == 64 else {
            return "Transaction hash must be exactly 64 characters (got: \(trimmed.count))"
        }
        let hexSet = CharacterSet(charactersIn: "0123456789abcdefABCDEF")
        guard trimmed.rangeOfCharacter(from: hexSet.inverted) == nil else {
            return "Transaction hash must contain only hexadecimal characters (0-9, a-f)"
        }
        return nil
    }

    /// Validates a transfer amount.
    ///
    /// The amount must be a positive decimal number with at most 7 decimal places.
    /// Scientific notation and negative values are rejected.
    ///
    /// - Parameter value: The amount string to validate.
    /// - Returns: Error message if validation fails, `nil` if valid.
    static func validateAmount(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return "Amount cannot be empty" }

        // Reject scientific notation explicitly before Decimal parsing accepts it.
        let lowerTrimmed = trimmed.lowercased()
        guard !lowerTrimmed.contains("e") else {
            return "Amount must be a plain decimal number"
        }

        guard let decimal = Decimal(string: trimmed), decimal > 0 else {
            return "Amount must be a positive number"
        }

        // Verify at most 7 decimal places (Stellar stroop precision).
        if let dotIndex = trimmed.firstIndex(of: ".") {
            let fractionalPart = trimmed[trimmed.index(after: dotIndex)...]
            guard fractionalPart.count <= 7 else {
                return "Amount can have at most 7 decimal places"
            }
        }

        return nil
    }

    /// Validates a hex string of the expected byte length.
    ///
    /// Used for WASM hashes and raw public key values that must be a specific number of hex bytes.
    ///
    /// - Parameters:
    ///   - value: The hex string to validate.
    ///   - expectedLength: Expected number of **bytes** (the string must be `expectedLength * 2` chars).
    /// - Returns: Error message if validation fails, `nil` if valid.
    static func validateHexString(_ value: String, expectedLength: Int) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return "Value cannot be empty" }

        let expectedChars = expectedLength * 2
        guard trimmed.count == expectedChars else {
            return "Must be exactly \(expectedChars) hex characters (\(expectedLength) bytes), got \(trimmed.count)"
        }
        let hexSet = CharacterSet(charactersIn: "0123456789abcdefABCDEF")
        guard trimmed.rangeOfCharacter(from: hexSet.inverted) == nil else {
            return "Must contain only hexadecimal characters (0-9, a-f)"
        }
        return nil
    }

    /// Validates a recipient address that may be either a G-address or a C-address.
    ///
    /// Used in transfer screens where the recipient can be a regular account or a contract.
    ///
    /// - Parameter value: The recipient address string to validate.
    /// - Returns: Error message if validation fails, `nil` if valid.
    static func validateRecipient(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return "Enter a recipient address (G... or C...)" }

        if trimmed.hasPrefix("G") {
            return validateAccountId(trimmed)
        } else if trimmed.hasPrefix("C") {
            return validateContractId(trimmed)
        } else {
            let prefix = trimmed.isEmpty ? "" : String(trimmed.prefix(1))
            return "Recipient must be a G-address (account) or C-address (contract), got: '\(prefix)'"
        }
    }

    // MARK: - Boolean Helpers

    /// Returns `true` when `value` passes `validateContractId(_:)`.
    ///
    /// - Parameter value: The string to test.
    /// - Returns: `true` if the value is a valid contract address.
    static func isValidContractAddress(_ value: String) -> Bool {
        validateContractId(value) == nil
    }

    /// Returns `true` when `value` passes `validateAccountId(_:)`.
    ///
    /// - Parameter value: The string to test.
    /// - Returns: `true` if the value is a valid account ID.
    static func isValidAccountId(_ value: String) -> Bool {
        validateAccountId(value) == nil
    }

    /// Returns `true` when `value` passes `validateRecipient(_:)`.
    ///
    /// - Parameter value: The string to test.
    /// - Returns: `true` if the value is a valid G-address or C-address.
    static func isValidRecipient(_ value: String) -> Bool {
        validateRecipient(value) == nil
    }
}
