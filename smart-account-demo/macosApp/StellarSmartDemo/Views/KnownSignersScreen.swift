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
/// Created from a `SignerEntryBridge` so that the view works with native Swift value
/// types and does not retain references to the Kotlin object graph after loading.
private struct SignerViewModel: Identifiable {
    /// Unique identifier for SwiftUI diffing — derived from the signer type and identifier.
    let id: String
    /// Signer type tag: "passkey", "delegated", "ed25519", or "external".
    let signerType: String
    /// Display identifier (credential ID, G-address, or hex key data).
    let identifier: String
    /// The context rules this signer belongs to.
    let rules: [RuleMembership]
}

/// A single context rule that a signer belongs to.
private struct RuleMembership {
    /// Numeric rule ID.
    let ruleId: Int64
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
/// `MacOSBridge.loadAccountSigners()`, which returns `SignerEntryBridge` values with
/// pre-classified signer info and pre-formatted rule type descriptions.
struct KnownSignersScreen: View {

    // MARK: - Environment

    @EnvironmentObject var bridgeWrapper: MacOSBridgeWrapper
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) private var dismiss

    // MARK: - Local state

    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var signerViewModels: [SignerViewModel] = []

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
                        ErrorCard(message: error)
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
            Text("All signers registered on this smart account across all context rules.")
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
            style: .filled
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
            let entries = KotlinInterop.toArray(rawResult, as: SignerEntryBridge.self)
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

    /// Converts a `SignerEntryBridge` to a Swift `SignerViewModel`.
    ///
    /// The bridge delivers the classified signer info and pre-formatted rule type
    /// descriptions, so this is a value-type copy with no Kotlin type inspection.
    private func buildSignerViewModel(entry: SignerEntryBridge) -> SignerViewModel {
        let info = entry.signerInfo
        let ruleMemberships = KotlinInterop.toArray(
            entry.ruleMemberships, as: RuleMembershipBridge.self
        ).map { membership in
            RuleMembership(
                ruleId: membership.ruleId,
                ruleName: membership.ruleName,
                ruleTypeDescription: membership.typeDescription
            )
        }

        return SignerViewModel(
            id: "\(info.type):\(info.identifier)",
            signerType: info.type,
            identifier: info.identifier,
            rules: ruleMemberships
        )
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
            SignerBadge(signerType: viewModel.signerType)

            Text(viewModel.identifier)
                .font(.system(.caption, design: .monospaced))
                .foregroundStyle(Material3Colors.onSurface)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .leading)
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
        TagPill(
            text: "#\(membership.ruleId)",
            font: .system(size: 11, weight: .medium),
            foreground: Material3Colors.primary,
            background: Material3Colors.primary.opacity(0.15),
            horizontalPadding: 6,
            verticalPadding: 2
        )
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
