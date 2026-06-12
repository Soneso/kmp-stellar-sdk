//
//  FormValidation.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import Foundation
import shared

/// Validation utilities for Stellar address formats and numeric amounts.
///
/// Account and contract IDs are checked structurally (prefix, length) and then verified with the
/// SDK's `StrKey` decoder, which validates the Base32 alphabet and CRC16 checksum. All other
/// checks are pure Swift. Functions return an error message string when validation fails and
/// `nil` when the input is valid. This matches the convention used in SwiftUI form fields where
/// a `nil` result means "no error".
///
/// ## Stellar Address Formats
///
/// Stellar uses StrKey encoding (a Base32 variant with a CRC16 checksum) for addresses:
///
/// - **Account IDs (G...)**: 56-character Ed25519 public keys.
/// - **Contract IDs (C...)**: 56-character Soroban smart contract addresses.
///
/// ## Amounts
///
/// Token amounts are positive decimal numbers with at most 7 decimal places,
/// matching the 7-decimal base-unit precision of Stellar tokens.
struct FormValidation {

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
        guard StrKey.shared.isValidEd25519PublicKey(accountId: trimmed) else {
            return "Must be a valid Stellar account (G...) address"
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
        guard StrKey.shared.isValidContract(address: trimmed) else {
            return "Must be a valid Stellar contract (C...) address"
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

        // Verify at most 7 decimal places (token base-unit precision).
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
}
