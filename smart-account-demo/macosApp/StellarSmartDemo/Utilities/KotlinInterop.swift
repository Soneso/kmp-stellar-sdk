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
/// None of these functions call into the Kotlin runtime or perform network operations; they are
/// synchronous pure-Swift helpers.
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

    // MARK: - Date Formatting

    /// Cached short timestamp formatter (`HH:mm:ss`).
    ///
    /// `DateFormatter` initialization is expensive; caching as a static property avoids
    /// creating a new instance on every log row render.
    private static let shortFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    /// Cached full timestamp formatter (`yyyy-MM-dd HH:mm:ss`).
    private static let fullFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return f
    }()

    /// Formats a `Date` as a short `HH:mm:ss` timestamp string.
    ///
    /// Used to render activity log entry timestamps in list rows.
    ///
    /// - Parameter date: The date to format.
    /// - Returns: A string in `HH:mm:ss` format using the current locale's time zone.
    static func formatTimestamp(_ date: Date) -> String {
        shortFormatter.string(from: date)
    }

    /// Formats a `Date` as a full `yyyy-MM-dd HH:mm:ss` timestamp string.
    ///
    /// Used when detailed log timestamps are needed (e.g. in expandable detail rows).
    ///
    /// - Parameter date: The date to format.
    /// - Returns: A string in `yyyy-MM-dd HH:mm:ss` format.
    static func formatFullTimestamp(_ date: Date) -> String {
        fullFormatter.string(from: date)
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
