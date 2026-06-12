//
//  AppState.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

// MARK: - AppState

/// Reactive SwiftUI mirror of the Kotlin `DemoState` singleton.
///
/// Views observe this object and call `sync(from:)` after any bridge operation that may
/// change connection state or balances. The activity log is synced separately via
/// `syncActivityLog(from:)` to avoid rebuilding the entire log on every balance refresh.
///
/// Inject a single instance at the root of the view hierarchy using `@StateObject` and pass it
/// down with `@EnvironmentObject`.
///
/// Usage:
/// ```swift
/// // Root app
/// @StateObject private var appState = AppState()
///
/// // After a bridge call
/// appState.sync(from: bridgeWrapper.bridge)
///
/// // Observe in a view
/// @EnvironmentObject var appState: AppState
/// Text(appState.xlmBalance ?? "—")
/// ```
@MainActor
final class AppState: ObservableObject {

    // MARK: - Connection State

    /// Whether a smart account wallet is currently connected.
    @Published var isConnected: Bool = false

    /// Whether the connected wallet's contract has been deployed on-chain.
    @Published var isDeployed: Bool = false

    /// The connected wallet's contract address (C-address), or `nil` when disconnected.
    @Published var contractId: String? = nil

    /// The active passkey credential ID (Base64URL-encoded), or `nil` when disconnected.
    @Published var credentialId: String? = nil

    // MARK: - Balances

    /// Display string for the connected wallet's XLM balance, or `nil` if not yet loaded.
    @Published var xlmBalance: String? = nil

    /// Display string for the connected wallet's DEMO token balance, or `nil` if not yet loaded.
    @Published var demoTokenBalance: String? = nil

    /// The DEMO token contract address (C-address), or `nil` if not yet resolved.
    @Published var demoTokenContractId: String? = nil

    // MARK: - Activity Log

    /// A snapshot of the current activity log, newest entry first.
    @Published var activityLog: [ActivityLogEntry] = []

    // MARK: - Activity Log Entry

    /// A single parsed entry from the Kotlin `ActivityLogState`.
    struct ActivityLogEntry: Identifiable {
        /// Identifier derived from the entry data (timestamp, level, message, and a
        /// duplicate ordinal), so rebuilding the array in `syncActivityLog(from:)` keeps
        /// row identity stable across syncs and SwiftUI only re-renders new rows.
        let id: String
        /// Human-readable log message.
        let message: String
        /// Severity level: one of `"INFO"`, `"SUCCESS"`, or `"ERROR"`.
        let level: String
        /// The time the entry was recorded.
        let timestamp: Date
    }

    // MARK: - Sync

    /// Synchronizes all published properties from the current Kotlin `DemoState`.
    ///
    /// Call this after any bridge operation that may change connection state or balances.
    ///
    /// - Parameter bridge: The shared `MacOSBridge` instance.
    func sync(from bridge: MacOSBridge) {
        isConnected = bridge.isConnected()
        isDeployed = bridge.isWalletDeployed()
        contractId = bridge.getContractId()
        credentialId = bridge.getCredentialId()
        xlmBalance = bridge.getBalance()
        demoTokenBalance = bridge.getDemoTokenBalance()
        demoTokenContractId = bridge.getDemoTokenContractId()
        syncActivityLog(from: bridge)
    }

    /// Synchronizes only the activity log from the current Kotlin `ActivityLogState`.
    ///
    /// Call this when polling for new log entries without needing a full state refresh.
    ///
    /// - Parameter bridge: The shared `MacOSBridge` instance.
    func syncActivityLog(from bridge: MacOSBridge) {
        let bridgeEntries = KotlinInterop.toArray(
            bridge.getActivityLogEntries(), as: ActivityLogEntryBridge.self
        )
        // Entries arrive newest-first. Assign duplicate ordinals from the oldest end so
        // existing rows keep their identity when new entries are prepended.
        var occurrenceCounts: [String: Int] = [:]
        var oldestFirst: [ActivityLogEntry] = []
        oldestFirst.reserveCapacity(bridgeEntries.count)
        for entry in bridgeEntries.reversed() {
            let baseKey = "\(entry.timestampMs):\(entry.level):\(entry.message)"
            let ordinal = occurrenceCounts[baseKey, default: 0]
            occurrenceCounts[baseKey] = ordinal + 1
            oldestFirst.append(ActivityLogEntry(
                id: "\(baseKey)#\(ordinal)",
                message: entry.message,
                level: entry.level,
                timestamp: Date(timeIntervalSince1970: Double(entry.timestampMs) / 1_000.0)
            ))
        }
        activityLog = oldestFirst.reversed()
    }

    /// Clears the activity log both in Swift state and in the Kotlin `ActivityLogState`.
    ///
    /// - Parameter bridge: The shared `MacOSBridge` instance.
    func clearActivityLog(bridge: MacOSBridge) {
        bridge.clearActivityLog()
        activityLog = []
    }
}
