//
//  SignerSelectionSupport.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import Foundation
import shared

/// Shared helpers for the screens that run signer-authorized operations
/// (Transfer, Approve, Context Rules, and the rule builder).
///
/// Each of those screens loads the available signer list, routes between the
/// single-signer and multi-signer submission paths, and interprets operation
/// errors the same way. These helpers are the single home for that logic; the
/// screens keep their own `@State` and assign from the returned values.
enum SignerSelectionSupport {

    // MARK: - Signer loading

    /// Result of a signer-list fetch.
    ///
    /// `loadFailed` distinguishes load-failed from loaded-empty so the UI can
    /// warn that multi-signer routing is unavailable while keeping
    /// single-signer operations usable. On failure `signers` is empty and the
    /// caller keeps its previous list.
    struct SignerLoadResult {
        let signers: [SignerInfoBridge]
        let loadFailed: Bool
    }

    /// Loads the available signers for the connected wallet.
    ///
    /// Never throws: a fetch failure is reported via `SignerLoadResult.loadFailed`.
    static func loadAvailableSigners(bridge: MacOSBridge) async -> SignerLoadResult {
        do {
            let result = try await bridge.loadAvailableSigners()
            return SignerLoadResult(
                signers: KotlinInterop.toArray(result, as: SignerInfoBridge.self),
                loadFailed: false
            )
        } catch {
            return SignerLoadResult(signers: [], loadFailed: true)
        }
    }

    // MARK: - Submission routing

    /// Returns true when an operation should use the single-signer path:
    /// the signer list has not loaded (or failed to load), or at most one
    /// signer is registered. Otherwise the multi-signer picker is shown.
    static func usesSingleSignerPath(
        signersLoaded: Bool,
        availableSigners: [SignerInfoBridge]
    ) -> Bool {
        !signersLoaded || availableSigners.count <= 1
    }

    /// Returns true when the picker selection is exactly the connected
    /// wallet's own passkey, in which case the simple single-passkey bridge
    /// call is used instead of the multi-signer call.
    static func isSingleOwnPasskey(
        selected: [SignerInfoBridge],
        connectedCredentialId: String?
    ) -> Bool {
        guard selected.count == 1,
              let only = selected.first,
              only.type.lowercased() == "passkey",
              let credId = connectedCredentialId,
              only.identifier == credId
        else { return false }
        return true
    }

    // MARK: - Operation error interpretation

    /// Interprets an operation error message, records the outcome in the
    /// activity log, and returns the message to display.
    ///
    /// User cancellation (Touch ID dismissed) maps to a distinct informational
    /// message rather than a generic failure string; any other failure is
    /// prefixed with `failurePrefix` (e.g. "Transfer failed:").
    @MainActor
    static func interpretOperationError(
        _ message: String,
        failurePrefix: String,
        bridge: MacOSBridge,
        appState: AppState
    ) -> String {
        let displayMessage: String
        if bridge.isUserCancellation(message: message) {
            displayMessage = "Passkey authentication cancelled"
            ActivityLogState.shared.info(message: "Passkey authentication cancelled")
        } else {
            displayMessage = "\(failurePrefix) \(message)"
            ActivityLogState.shared.error(message: message)
        }
        appState.syncActivityLog(from: bridge)
        return displayMessage
    }
}
