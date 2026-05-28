//
//  MacOSBridgeWrapper.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

/// Centralized wrapper for `MacOSBridge` that enables injection via `@EnvironmentObject`.
///
/// The native macOS SwiftUI app uses this class to hold a single `MacOSBridge` instance shared
/// across the entire view hierarchy. It removes the need for every view to instantiate its own
/// bridge and ensures that all views call into the same Kotlin state.
///
/// Swift-observable state is limited to `isKitInitialized`, which mirrors the Kotlin
/// `DemoState.kit != null` flag. All other state lives in `AppState`. Views observe
/// `bridgeWrapper.isKitInitialized` to gate actions that require a fully initialized kit.
///
/// Usage:
/// ```swift
/// // Root app
/// @main
/// struct StellarSmartDemoApp: App {
///     @StateObject private var bridgeWrapper = MacOSBridgeWrapper()
///
///     var body: some Scene {
///         WindowGroup {
///             MainScreen()
///                 .environmentObject(bridgeWrapper)
///         }
///     }
/// }
///
/// // Any child view
/// struct WalletCreationScreen: View {
///     @EnvironmentObject var bridgeWrapper: MacOSBridgeWrapper
///
///     var canCreate: Bool { bridgeWrapper.isKitInitialized && !username.isEmpty }
/// }
/// ```
final class MacOSBridgeWrapper: ObservableObject {

    /// The shared Kotlin bridge instance.
    ///
    /// Exposes all smart account flows as Swift-callable async functions, including:
    /// - `initializeKit()` — initialize the Smart Account Kit on startup.
    /// - `createWallet(username:onProgress:)` — passkey registration + contract deployment.
    /// - `quickConnect()` / `manualConnect()` / `connectWithAddress(_:)` — session restore and connection.
    /// - `transfer(tokenContract:recipient:amount:)` — single-signer token transfer.
    /// - `multiSignerTransfer(tokenContract:recipient:amount:signerDescriptors:delegatedSecretKeys:ed25519SecretKeys:)` — multi-signer token transfer.
    /// - `multiSignerApproveAllowance(tokenContract:spenderAddress:amount:expirationLedgerOffset:signerDescriptors:delegatedSecretKeys:ed25519SecretKeys:)` — multi-signer allowance approval.
    /// - `validateEd25519Key(secretHex:expectedPublicKeyHex:)` — validate an Ed25519 seed hex against a known public key.
    /// - `loadContextRules()` / `addContextRule(...)` / `removeContextRule(_:)` — context rule management.
    /// - `loadAccountSigners()` — enumerate all signers registered on the account.
    /// - `getActivityLogEntries()` — fetch the current activity log snapshot.
    /// - `refreshBalances()` — refresh XLM and DEMO token balances.
    /// - `disconnect()` — disconnect the active session.
    ///
    /// All mutating operations are Kotlin `suspend` functions; call them from a `Task` block.
    let bridge = MacOSBridge()

    /// Whether `bridge.initializeKit()` has completed successfully.
    ///
    /// Mirrors the Kotlin `DemoState.kit != null` state. Set to `true` by `MainScreen` after
    /// `initializeKit()` returns without throwing. Views use this to gate actions that require
    /// a fully initialized Smart Account Kit (e.g., wallet creation and connection).
    @Published var isKitInitialized: Bool = false

    /// Creates the wrapper and the underlying `MacOSBridge`.
    ///
    /// No additional initialization is required here. Call `bridge.initializeKit()` from the
    /// root view's `task` modifier before any user interaction, then set `isKitInitialized = true`.
    init() {}
}
