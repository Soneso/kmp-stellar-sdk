//
//  KotlinInterop.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import Foundation
import shared

/// Utilities for converting between Kotlin/Objective-C types and Swift types.
///
/// The Kotlin Multiplatform framework exposes Kotlin collections and primitive types via
/// Objective-C bridging, which sometimes requires explicit casting that would otherwise be
/// scattered across view files. Centralizing those conversions here keeps view code clean and
/// makes the bridging semantics explicit.
///
/// All of these functions are synchronous and side-effect free: they only convert or format
/// values already in memory (including accessors on Kotlin-bridged objects such as
/// `KotlinByteArray`) and never invoke suspend functions, network calls, or business logic.
enum KotlinInterop {

    // MARK: - Collection Conversion

    /// Converts a Kotlin `List<T>` to a typed Swift array.
    ///
    /// The Kotlin framework bridges Kotlin lists as `NSArray` in Swift. This helper casts each
    /// element to the requested Swift type and silently drops elements that fail the cast.
    ///
    /// ```swift
    /// let entries = KotlinInterop.toArray(bridge.getActivityLogEntries(), as: ActivityLogEntryBridge.self)
    /// ```
    ///
    /// - Parameters:
    ///   - kotlinList: The value returned from a Kotlin bridge method (may be `nil` or any type).
    ///   - type: The Swift type each element should be cast to.
    /// - Returns: A Swift array containing only the successfully cast elements.
    static func toArray<T>(_ kotlinList: Any?, as type: T.Type) -> [T] {
        guard let nsArray = kotlinList as? NSArray else { return [] }
        return (0..<nsArray.count).compactMap { nsArray[$0] as? T }
    }

    // MARK: - Byte Array Conversion

    /// Converts a Kotlin `ByteArray` to a lowercase hex string.
    ///
    /// Used when displaying raw cryptographic key material (e.g. Ed25519 public keys or
    /// WebAuthn credential key data) that the bridge returns as `KotlinByteArray`.
    ///
    /// - Parameter bytes: A `KotlinByteArray` instance from the Kotlin framework.
    /// - Returns: Lowercase hex-encoded string with no prefix or separators.
    static func hexString(from bytes: KotlinByteArray) -> String {
        var result = ""
        result.reserveCapacity(Int(bytes.size) * 2)
        for i in 0..<bytes.size {
            result += String(format: "%02x", UInt8(bitPattern: bytes.get(index: i)))
        }
        return result
    }

    // MARK: - Address Formatting

    /// Truncates a Stellar address to a short form for display in compact UI elements.
    ///
    /// Returns the first `prefixLength` characters, an ellipsis, and the last `suffixLength`
    /// characters. If the address is shorter than `prefixLength + suffixLength`, it is returned
    /// unchanged.
    ///
    /// ```swift
    /// KotlinInterop.truncateAddress("GABC...XYZ", prefixLength: 6, suffixLength: 4)
    /// // "GABC...XYZ" -> "GABC...XYZ" when already short
    /// // "GABCDEFGHIJKLMNOPQRSTUVWXYZ234567890GABCDEFGHIJKLMNOPQRSTUV" -> "GABCDE...STUV"
    /// ```
    ///
    /// - Parameters:
    ///   - address: The full Stellar address string.
    ///   - prefixLength: Number of leading characters to retain. Defaults to 6.
    ///   - suffixLength: Number of trailing characters to retain. Defaults to 4.
    /// - Returns: The truncated address string, or the original if no truncation is needed.
    static func truncateAddress(_ address: String, prefixLength: Int = 6, suffixLength: Int = 4) -> String {
        guard address.count > prefixLength + suffixLength else { return address }
        let prefix = address.prefix(prefixLength)
        let suffix = address.suffix(suffixLength)
        return "\(prefix)...\(suffix)"
    }

    /// Truncates a value to its first `length` characters followed by an ellipsis.
    ///
    /// Used for identifiers where only the leading characters are meaningful for
    /// display (e.g. credential IDs in the signer picker). Values that are not
    /// longer than `length` are returned unchanged.
    ///
    /// - Parameters:
    ///   - value: The full string.
    ///   - length: Number of leading characters to retain.
    /// - Returns: The truncated string, or the original if no truncation is needed.
    static func truncatePrefix(_ value: String, length: Int) -> String {
        guard value.count > length else { return value }
        return String(value.prefix(length)) + "..."
    }
}

extension KotlinInterop {

    // MARK: - Int Array Conversion

    /// Converts a Kotlin `List<Int>` to a Swift `[Int32]` array.
    ///
    /// The Kotlin framework bridges `Int` values as `KotlinInt` (NSNumber subclass) in Swift.
    /// This helper extracts the `.int32Value` from each element.
    ///
    /// - Parameter kotlinList: The value returned from a Kotlin bridge method.
    /// - Returns: A Swift array of `Int32` values.
    static func toIntArray(_ kotlinList: Any?) -> [Int32] {
        guard let nsArray = kotlinList as? NSArray else { return [] }
        return (0..<nsArray.count).compactMap { (nsArray[$0] as? NSNumber)?.int32Value }
    }
}
