//
//  KnownSignersScreen.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

// MARK: - View Models

/// Swift value type representing a single unique signer and its rule memberships.
///
/// Created by converting a Kotlin `SignerEntry` so that the view works with native Swift
/// types and does not retain references to the Kotlin object graph after loading.
private struct SignerViewModel: Identifiable {
    /// Unique identifier for SwiftUI diffing — derived from the signer identifier.
    let id: String
    /// Signer type tag: "passkey", "delegated", or "ed25519".
    let signerType: String
    /// Display identifier (credential ID, G-address, or hex public key).
    let identifier: String
    /// The context rules this signer belongs to.
    let rules: [RuleMembership]
}

/// A single context rule that a signer belongs to.
private struct RuleMembership {
    /// Numeric rule ID.
    let ruleId: UInt32
    /// Human-readable rule name, or empty string when unnamed.
    let ruleName: String
    /// Formatted context type description (e.g. "Default (Any Operation)").
    let ruleTypeDescription: String
}

// MARK: - KnownSignersScreen

/// Displays all signers registered on the connected smart account, deduplicated across
/// all on-chain context rules.
///
/// Mirrors the Compose `KnownSignersScreen` exactly. Loads via
/// `MacOSBridge.loadAccountSigners()` and converts the Kotlin `SignerEntry` objects to
/// native Swift view models before rendering.
struct KnownSignersScreen: View {

    // MARK: - Environment

    @EnvironmentObject var bridgeWrapper: MacOSBridgeWrapper
    @EnvironmentObject var appState: AppState
    @ObservedObject var toastManager: ToastManager
    @Environment(\.dismiss) private var dismiss

    // MARK: - Local state

    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var signerViewModels: [SignerViewModel] = []

    // MARK: - Init

    init(toastManager: ToastManager) {
        self.toastManager = toastManager
    }

    // MARK: - Body

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                descriptionCard
                if !appState.isConnected {
                    notConnectedCard
                } else {
                    refreshButton
                    if let error = errorMessage {
                        errorCard(message: error)
                    }
                    if isLoading && signerViewModels.isEmpty {
                        loadingView
                    }
                    if !isLoading && signerViewModels.isEmpty && errorMessage == nil {
                        emptyStateCard
                    }
                    if !signerViewModels.isEmpty {
                        signersListCard
                    }
                }
                goBackButton
                Spacer().frame(height: 16)
            }
            .padding(16)
        }
        .background(Material3Colors.background)
        .navigationToolbar(title: "Account Signers")
        .task {
            guard appState.isConnected else { return }
            await loadSigners()
        }
    }

    // MARK: - Description card

    private var descriptionCard: some View {
        InfoCard(title: "Account Signers", color: .variant) {
            Text("View all unique signers registered across all context rules on this smart account.")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
    }

    // MARK: - Not connected card

    private var notConnectedCard: some View {
        InfoCard(color: .variant) {
            Text("Connect a wallet to view account signers")
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.vertical, 8)
        }
    }

    // MARK: - Refresh button

    private var refreshButton: some View {
        LoadingButton(
            action: {
                Task { await loadSigners() }
            },
            isLoading: isLoading,
            isEnabled: !isLoading,
            text: "Refresh",
            loadingText: "Loading...",
            style: .outlined
        )
    }

    // MARK: - Error card

    @ViewBuilder
    private func errorCard(message: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(message)
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onErrorContainer)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.errorContainer)
        .cornerRadius(8)
    }

    // MARK: - Loading view

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
                .controlSize(.large)
                .frame(width: 32, height: 32)
            Text("Loading signers...")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
    }

    // MARK: - Empty state card

    private var emptyStateCard: some View {
        InfoCard(color: .variant) {
            Text("No signers found on this account")
                .font(.system(size: 14))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.vertical, 8)
        }
    }

    // MARK: - Signers list card

    private var signersListCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(signerViewModels.count) signer\(signerViewModels.count == 1 ? "" : "s")")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Material3Colors.onSurface)

            ForEach(Array(signerViewModels.enumerated()), id: \.element.id) { index, vm in
                if index > 0 {
                    Divider()
                        .background(Material3Colors.outline.opacity(0.3))
                        .padding(.vertical, 0)
                }
                SignerEntryRow(viewModel: vm)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.surface)
        .cornerRadius(8)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }

    // MARK: - Go Back button

    private var goBackButton: some View {
        LoadingButton(
            action: { dismiss() },
            isLoading: false,
            isEnabled: true,
            text: "Go Back",
            loadingText: "",
            style: .outlined
        )
    }

    // MARK: - Actions

    private func loadSigners() async {
        guard appState.isConnected else { return }
        await MainActor.run {
            isLoading = true
            errorMessage = nil
            signerViewModels = []
        }
        do {
            let rawResult = try await bridgeWrapper.bridge.loadAccountSigners()
            let entries = KotlinInterop.toArray(rawResult, as: SignerEntry.self)
            let viewModels = entries.map { entry in
                buildSignerViewModel(entry: entry)
            }
            await MainActor.run {
                signerViewModels = viewModels
                appState.syncActivityLog(from: bridgeWrapper.bridge)
                isLoading = false
            }
        } catch {
            await MainActor.run {
                errorMessage = "Failed to load signers: \(error.localizedDescription)"
                appState.syncActivityLog(from: bridgeWrapper.bridge)
                isLoading = false
            }
        }
    }

    // MARK: - Kotlin interop helpers

    /// Converts a Kotlin `SignerEntry` to a Swift `SignerViewModel`.
    ///
    /// Inspects the concrete Kotlin signer type (`DelegatedSigner` or `ExternalSigner`) to
    /// determine the display type tag and identifier. Converts the Kotlin `contextRules` list
    /// to `RuleMembership` values using the ObjC-bridged `ParsedContextRule` properties.
    private func buildSignerViewModel(entry: SignerEntry) -> SignerViewModel {
        let signer = entry.signer
        let signerType: String
        let identifier: String

        if let delegated = signer as? DelegatedSigner {
            signerType = "delegated"
            identifier = delegated.address
        } else if let external = signer as? ExternalSigner {
            let keyData = external.keyData
            // Determine passkey vs Ed25519 by key length:
            // WebAuthn compressed P-256 keys are 65 bytes; Ed25519 keys are 32 bytes.
            if keyData.size == 32 {
                signerType = "ed25519"
                identifier = KotlinInterop.hexString(from: keyData)
            } else {
                signerType = "passkey"
                identifier = KotlinInterop.hexString(from: keyData)
            }
        } else {
            signerType = "ed25519"
            identifier = "(unknown)"
        }

        let ruleMemberships = entry.contextRules.map { rule in
            buildRuleMembership(rule: rule)
        }

        return SignerViewModel(
            id: "\(signerType):\(identifier)",
            signerType: signerType,
            identifier: identifier,
            rules: ruleMemberships
        )
    }

    /// Converts a Kotlin `ParsedContextRule` to a Swift `RuleMembership`.
    ///
    /// The `contextType` sealed class is introspected via ObjC dynamic cast. The Kotlin
    /// sealed subclasses are exposed with nested Swift names:
    /// `ContextRuleType.Default`, `ContextRuleType.CallContract`, `ContextRuleType.CreateContract`.
    private func buildRuleMembership(rule: ParsedContextRule) -> RuleMembership {
        let ruleId = rule.id
        let ruleName = rule.name
        let typeDescription = formatContextType(rule.contextType)
        return RuleMembership(
            ruleId: ruleId,
            ruleName: ruleName,
            ruleTypeDescription: typeDescription
        )
    }

    /// Formats a Kotlin `ContextRuleType` sealed class value to a display string.
    ///
    /// Mirrors the Kotlin `formatContextType()` utility in `FormatUtils.kt`.
    private func formatContextType(_ contextType: ContextRuleType) -> String {
        if contextType is ContextRuleType.Default {
            return "Default (Any Operation)"
        } else if let callContract = contextType as? ContextRuleType.CallContract {
            let addr = callContract.contractAddress
            let truncated = KotlinInterop.truncateAddress(addr)
            return "Call Contract: \(truncated)"
        } else if let createContract = contextType as? ContextRuleType.CreateContract {
            let hashBytes = createContract.wasmHash
            let hex = KotlinInterop.hexString(from: hashBytes)
            let prefix = String(hex.prefix(8))
            return "Create Contract: \(prefix)..."
        }
        return "Unknown"
    }
}

// MARK: - SignerEntryRow

/// A single row in the signers list showing the signer badge, identifier, and rule memberships.
private struct SignerEntryRow: View {

    let viewModel: SignerViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            signerTypeAndIdentifier
            ruleMembershipsSection
        }
    }

    // MARK: - Signer type badge and identifier

    private var signerTypeAndIdentifier: some View {
        HStack(alignment: .center, spacing: 8) {
            SignerBadge(signerType: viewModel.signerType, text: badgeLabel)

            Text(viewModel.identifier)
                .font(.system(.caption, design: .monospaced))
                .foregroundStyle(Material3Colors.onSurface)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    /// The badge label for this signer type, mirroring the Compose display logic.
    private var badgeLabel: String {
        switch viewModel.signerType.lowercased() {
        case "passkey":   return "Passkey"
        case "delegated": return "Stellar Account"
        case "ed25519":   return "Ed25519"
        default:          return viewModel.signerType.capitalized
        }
    }

    // MARK: - Rule memberships

    @ViewBuilder
    private var ruleMembershipsSection: some View {
        if !viewModel.rules.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(viewModel.rules, id: \.ruleId) { membership in
                    RuleMembershipRow(membership: membership)
                }
            }
        }
    }
}

// MARK: - RuleMembershipRow

/// One row inside a signer entry representing a single context rule the signer belongs to.
private struct RuleMembershipRow: View {

    let membership: RuleMembership

    var body: some View {
        HStack(alignment: .center, spacing: 6) {
            ruleIdBadge
            ruleNameLabel
            ruleTypeLabel
        }
    }

    // MARK: - Rule ID badge

    private var ruleIdBadge: some View {
        Text("#\(membership.ruleId)")
            .font(.system(size: 11, weight: .medium))
            .foregroundStyle(Material3Colors.primary)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Material3Colors.primary.opacity(0.15))
            .cornerRadius(4)
    }

    // MARK: - Rule name

    private var ruleNameLabel: some View {
        Text(membership.ruleName.isEmpty ? "Unnamed Rule" : membership.ruleName)
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(Material3Colors.onSurface)
    }

    // MARK: - Rule type

    private var ruleTypeLabel: some View {
        Text(membership.ruleTypeDescription)
            .font(.system(size: 12))
            .foregroundStyle(Material3Colors.onSurfaceVariant)
    }
}
