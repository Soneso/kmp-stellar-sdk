//
//  WalletConnectionScreen.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

// MARK: - Connection section identifier

/// Identifies which connection section is actively loading.
///
/// Only one section may be active at a time. While a section is active, all buttons in
/// other sections are disabled to prevent concurrent connection attempts.
private enum ConnectionSection {
    case autoConnect
    case indexer
    case address
    case pending(String)
}

/// Captures the data needed to finalize an ambiguous-result connect: the
/// credential ID from the authentication that produced the Ambiguous result,
/// and the candidate contracts the user must pick from.
private struct AmbiguousState {
    let credentialId: String
    let candidates: [String]
}

// MARK: - WalletConnectionScreen

/// Screen that provides four paths for connecting a smart account wallet.
///
/// Mirrors the Compose `WalletConnectionScreen` exactly:
/// - **Auto Connect** — restores the last session or authenticates with a passkey
///   and resolves the contract address via the indexer.
/// - **Connect via Indexer** — authenticates with a passkey and resolves the
///   associated contract via the indexer.
/// - **Connect with Address** — connects directly to a known contract address.
/// - **Pending Deployments** — visible only when there are locally stored credentials
///   whose contract deployment may not have completed.
///
/// All sections are always expanded with descriptions visible.
///
/// All async operations call the corresponding `MacOSBridge` suspend functions via `Task`.
/// A `activeSection` state variable guards against concurrent connection attempts.
struct WalletConnectionScreen: View {

    // MARK: - Environment

    @EnvironmentObject private var bridgeWrapper: MacOSBridgeWrapper
    @EnvironmentObject private var appState: AppState
    @ObservedObject var toastManager: ToastManager
    @Environment(\.dismiss) private var dismiss

    // MARK: - State

    /// Identifies which section is currently executing a network operation. `nil` when idle.
    @State private var activeSection: ConnectionSection? = nil

    /// Inline error for the Auto Connect section.
    @State private var autoConnectError: String? = nil

    /// Inline error for the Connect via Indexer section.
    @State private var indexerError: String? = nil

    /// Inline error for the Connect with Address section.
    @State private var addressError: String? = nil

    /// User-entered contract address for the Connect with Address section.
    @State private var contractAddressInput: String = ""

    /// Locally stored credentials with a pending deployment status.
    @State private var pendingCredentials: [StoredCredential] = []

    /// State surfaced when a connect flow returns an `Ambiguous` bridge
    /// result. While non-nil, the picker sheet is shown. The user's
    /// selection is routed through `performFinalizeConnect`, which uses
    /// `credentialId` from the original authentication so a second WebAuthn
    /// prompt is not required.
    @State private var ambiguousState: AmbiguousState? = nil

    // All sections are always expanded (no collapse/expand toggle).

    // MARK: - Derived state

    /// `true` when no section is currently loading and the kit is fully initialized.
    private var isIdle: Bool {
        activeSection == nil && bridgeWrapper.isKitInitialized
    }

    /// `true` when the contract address input is a valid Stellar contract address.
    private var isAddressValid: Bool {
        let trimmed = contractAddressInput.trimmingCharacters(in: .whitespaces)
        return trimmed.hasPrefix("C") && trimmed.count == 56
    }

    // MARK: - Body

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                autoConnectSection
                indexerSection
                addressSection
                if !pendingCredentials.isEmpty {
                    pendingDeploymentsSection
                }
            }
            .padding(16)
        }
        .background(Material3Colors.background)
        .navigationToolbar(title: "Connect Wallet")
        .task {
            await loadPending()
        }
        .sheet(isPresented: Binding(
            get: { ambiguousState != nil },
            set: { if !$0 { ambiguousState = nil } }
        )) {
            ContractPickerSheet(
                candidates: ambiguousState?.candidates ?? [],
                onCancel: { ambiguousState = nil },
                onSelected: { chosen in
                    let credentialId = ambiguousState?.credentialId
                    ambiguousState = nil
                    if let credentialId = credentialId {
                        performFinalizeConnect(
                            credentialId: credentialId,
                            contractAddress: chosen
                        )
                    }
                }
            )
        }
    }

    // MARK: - Auto Connect section

    private var autoConnectSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Auto Connect")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Material3Colors.onPrimaryContainer)

                Text(
                    "Restores the last connected session if available. " +
                    "If no session exists, triggers passkey authentication " +
                    "and tries to resolve the contract address automatically via indexer."
                )
                .font(.system(size: 13))
                .foregroundColor(Material3Colors.onPrimaryContainer)

                Spacer().frame(height: 4)

                LoadingButton(
                    action: performAutoConnect,
                    isLoading: isAutoConnectLoading,
                    isEnabled: isIdle,
                    text: "Auto Connect",
                    loadingText: "Connecting...",
                    style: .filled
                )

                if let error = autoConnectError {
                    inlineError(error)
                }
            }
            .padding(16)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.primaryContainer)
        .cornerRadius(8)
    }

    private var isAutoConnectLoading: Bool {
        if case .autoConnect = activeSection { return true }
        return false
    }

    // MARK: - Connect via Indexer section

    private var indexerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Connect via Indexer")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Material3Colors.onSurface)

            Text(
                "Authenticates with a passkey, then uses the indexer service " +
                "to look up the smart account contract associated with that credential."
            )
            .font(.system(size: 13))
            .foregroundColor(Material3Colors.onSurfaceVariant)

            Spacer().frame(height: 4)

            LoadingButton(
                action: performIndexerConnect,
                isLoading: isIndexerLoading,
                isEnabled: isIdle,
                text: "Connect via Indexer",
                loadingText: "Connecting...",
                style: .filled
            )

            if let error = indexerError {
                inlineError(error)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(8)
    }

    private var isIndexerLoading: Bool {
        if case .indexer = activeSection { return true }
        return false
    }

    // MARK: - Connect with Address section

    private var addressSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Connect with Address")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Material3Colors.onSurface)

            Text(
                "Connect to a smart account using a known contract address. " +
                "Authenticates with a passkey that is registered as a signer on the contract. " +
                "Use this to reconnect with a recovery signer."
            )
            .font(.system(size: 13))
            .foregroundColor(Material3Colors.onSurfaceVariant)

            Spacer().frame(height: 4)

            ValidationTextField(
                label: "Contract Address",
                text: $contractAddressInput,
                error: contractAddressInput.trimmingCharacters(in: .whitespaces).isEmpty
                    ? nil
                    : (isAddressValid ? nil : "Must be a C-address (56 characters starting with C)"),
                placeholder: "C...",
                isMonospace: true,
                isEnabled: activeSection == nil
            )
            .onChange(of: contractAddressInput) { _ in
                addressError = nil
            }

            Spacer().frame(height: 4)

            LoadingButton(
                action: { performAddressConnect() },
                isLoading: isAddressLoading,
                isEnabled: isIdle && isAddressValid,
                text: "Connect",
                loadingText: "Connecting...",
                style: .filled
            )

            if let error = addressError {
                inlineError(error)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(8)
    }

    private var isAddressLoading: Bool {
        if case .address = activeSection { return true }
        return false
    }

    // MARK: - Pending Deployments section

    private var pendingDeploymentsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pending Deployments (\(pendingCredentials.count))")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Material3Colors.onErrorContainer)

            Text(
                "These credentials were registered but contract deployment " +
                "may not have completed. Retry the deployment or delete the credential."
            )
            .font(.system(size: 13))
            .foregroundColor(Material3Colors.onErrorContainer)

            Spacer().frame(height: 4)

            ForEach(pendingCredentials, id: \.credentialId) { credential in
                pendingCredentialCard(credential)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.errorContainer)
        .cornerRadius(8)
    }

    private func pendingCredentialCard(_ credential: StoredCredential) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            // Credential ID row
            VStack(alignment: .leading, spacing: 2) {
                Text("Credential ID:")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Material3Colors.onSurfaceVariant)

                let credIdNickname = credential.nickname.map { " (\($0))" } ?? ""
                let credIdDisplay = KotlinInterop.truncateAddress(
                    credential.credentialId,
                    prefixLength: 12,
                    suffixLength: 8
                ) + credIdNickname
                Text(credIdDisplay)
                    .font(.system(.footnote, design: .monospaced))
                    .foregroundColor(Material3Colors.onSurface)
            }

            // Contract ID row
            VStack(alignment: .leading, spacing: 2) {
                Text("Contract ID:")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Material3Colors.onSurfaceVariant)

                let contractDisplay: String = {
                    if let cid = credential.contractId {
                        return KotlinInterop.truncateAddress(cid, prefixLength: 12, suffixLength: 12)
                    }
                    return "Unknown"
                }()
                Text(contractDisplay)
                    .font(.system(.footnote, design: .monospaced))
                    .foregroundColor(Material3Colors.onSurface)
            }

            // Action buttons
            HStack(spacing: 8) {
                LoadingButton(
                    action: { performRetryDeploy(credential: credential) },
                    isLoading: isPendingLoading(credentialId: credential.credentialId),
                    isEnabled: activeSection == nil,
                    text: "Retry Deploy",
                    loadingText: "Deploying...",
                    style: .filled
                )

                LoadingButton(
                    action: { performDeleteCredential(credential: credential) },
                    isLoading: false,
                    isEnabled: activeSection == nil,
                    text: "Delete",
                    loadingText: "Deleting...",
                    style: .outlined
                )
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.surface)
        .cornerRadius(6)
    }

    private func isPendingLoading(credentialId: String) -> Bool {
        if case .pending(let id) = activeSection { return id == credentialId }
        return false
    }

    // MARK: - Inline error helper

    private func inlineError(_ message: String) -> some View {
        Text(message)
            .font(.footnote)
            .foregroundColor(Material3Colors.error)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Actions

    private func clearAllErrors() {
        autoConnectError = nil
        indexerError = nil
        addressError = nil
    }

    private func performAutoConnect() {
        clearAllErrors()
        activeSection = .autoConnect
        Task {
            do {
                let result = try await bridgeWrapper.bridge.quickConnect()
                await MainActor.run {
                    activeSection = nil
                    handleBridgeResult(
                        result,
                        nullErrorSetter: { autoConnectError = $0 },
                        nullErrorMessage: "No wallet found for this passkey."
                    )
                }
            } catch {
                await MainActor.run {
                    activeSection = nil
                    let msg = error.localizedDescription
                    if bridgeWrapper.bridge.isUserCancellation(message: msg) {
                        autoConnectError = "Connection cancelled."
                    } else {
                        autoConnectError = msg
                    }
                }
            }
        }
    }

    private func performIndexerConnect() {
        clearAllErrors()
        activeSection = .indexer
        Task {
            do {
                let result = try await bridgeWrapper.bridge.manualConnect()
                await MainActor.run {
                    activeSection = nil
                    handleBridgeResult(
                        result,
                        nullErrorSetter: { indexerError = $0 },
                        nullErrorMessage: "No contract found for this credential."
                    )
                }
            } catch {
                await MainActor.run {
                    activeSection = nil
                    let msg = error.localizedDescription
                    if bridgeWrapper.bridge.isUserCancellation(message: msg) {
                        indexerError = "Connection cancelled."
                    } else {
                        indexerError = msg
                    }
                }
            }
        }
    }

    /// Attempts to connect to a known contract address.
    ///
    /// - Parameter prefilledAddress: When provided, used directly instead of
    ///   reading the user-entered field. The picker flow uses this to finalize
    ///   an Ambiguous result without requiring the user to retype an address.
    private func performAddressConnect(prefilledAddress: String? = nil) {
        clearAllErrors()
        let address = prefilledAddress
            ?? contractAddressInput.trimmingCharacters(in: .whitespaces)
        activeSection = .address
        Task {
            do {
                let result = try await bridgeWrapper.bridge.connectWithAddress(contractAddress: address)
                await MainActor.run {
                    activeSection = nil
                    handleBridgeResult(
                        result,
                        nullErrorSetter: { addressError = $0 },
                        nullErrorMessage: "Could not connect to the provided contract address."
                    )
                }
            } catch {
                await MainActor.run {
                    activeSection = nil
                    let msg = error.localizedDescription
                    if bridgeWrapper.bridge.isUserCancellation(message: msg) {
                        addressError = "Connection cancelled."
                    } else {
                        addressError = msg
                    }
                }
            }
        }
    }

    /// Handles a bridge connect result by routing to the right state update.
    ///
    /// - `nil`: invokes `nullErrorSetter` with `nullErrorMessage`.
    /// - `result.connected != nil`: syncs app state, shows a toast, dismisses.
    /// - `result.ambiguous != nil`: sets `ambiguousCandidates`, which triggers
    ///   the picker sheet. Does NOT dismiss the screen.
    private func handleBridgeResult(
        _ result: WalletConnectionBridgeResult?,
        nullErrorSetter: (String) -> Void,
        nullErrorMessage: String
    ) {
        guard let result = result else {
            nullErrorSetter(nullErrorMessage)
            return
        }
        if result.connected != nil {
            appState.sync(from: bridgeWrapper.bridge)
            toastManager.show("Connected successfully")
            dismiss()
        } else if let ambiguous = result.ambiguous {
            // Surface the candidates; the .sheet binding renders the picker.
            // The credentialId is captured so the picker callback can finalize
            // without a second WebAuthn prompt.
            ambiguousState = AmbiguousState(
                credentialId: ambiguous.credentialId,
                candidates: ambiguous.candidates
            )
        } else {
            // Bridge returned a non-null result with neither arm populated.
            // This violates the bridge invariant; surface as a generic error.
            nullErrorSetter(nullErrorMessage)
        }
    }

    /// Finalizes a connect after the user picks a contract from the
    /// ambiguous-result picker. Reuses the credential ID from the
    /// authentication that produced the Ambiguous result, skipping a second
    /// WebAuthn prompt.
    private func performFinalizeConnect(credentialId: String, contractAddress: String) {
        clearAllErrors()
        activeSection = .address
        Task {
            do {
                let result = try await bridgeWrapper.bridge.finalizeConnect(
                    credentialId: credentialId,
                    contractAddress: contractAddress
                )
                await MainActor.run {
                    activeSection = nil
                    handleBridgeResult(
                        result,
                        nullErrorSetter: { addressError = $0 },
                        nullErrorMessage: "Could not connect to the selected wallet."
                    )
                }
            } catch {
                await MainActor.run {
                    activeSection = nil
                    addressError = error.localizedDescription
                }
            }
        }
    }

    private func performRetryDeploy(credential: StoredCredential) {
        activeSection = .pending(credential.credentialId)
        Task {
            do {
                let result = try await bridgeWrapper.bridge.retryPendingDeploy(
                    credentialId: credential.credentialId
                )
                await MainActor.run {
                    activeSection = nil
                    appState.sync(from: bridgeWrapper.bridge)
                    toastManager.show("Connected successfully")
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    activeSection = nil
                    toastManager.show("Retry failed: \(error.localizedDescription)")
                }
            }
        }
    }

    private func performDeleteCredential(credential: StoredCredential) {
        activeSection = .pending(credential.credentialId)
        Task {
            do {
                _ = try await bridgeWrapper.bridge.deletePendingCredential(credentialId: credential.credentialId)
                await MainActor.run { activeSection = nil }
                await loadPending()
            } catch {
                await MainActor.run {
                    activeSection = nil
                    toastManager.show("Delete failed: \(error.localizedDescription)")
                }
            }
        }
    }

    // MARK: - Load pending credentials

    @MainActor
    private func loadPending() async {
        do {
            let list = try await bridgeWrapper.bridge.loadPendingCredentials()
            pendingCredentials = KotlinInterop.toArray(list, as: StoredCredential.self)
        } catch {
            // Non-fatal — the section simply stays hidden
        }
    }
}

// MARK: - Contract picker sheet

/// Sheet shown when a connect flow returns an `Ambiguous` bridge result.
///
/// The user picks one contract from the candidate list; the parent
/// `WalletConnectionScreen` then re-runs the connect via
/// `performAddressConnect(prefilledAddress:)` with the chosen address.
private struct ContractPickerSheet: View {
    let candidates: [String]
    let onCancel: () -> Void
    let onSelected: (String) -> Void

    @State private var selected: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Select Wallet")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Material3Colors.onSurface)

            Text(
                "This passkey is a signer on more than one wallet. " +
                "Pick the one to connect."
            )
            .font(.system(size: 13))
            .foregroundColor(Material3Colors.onSurfaceVariant)

            if candidates.isEmpty {
                Text("No candidates available.")
                    .font(.system(size: 13))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                    .padding(.vertical, 24)
            } else {
                ScrollView {
                    VStack(spacing: 8) {
                        ForEach(candidates, id: \.self) { address in
                            ContractCandidateRow(
                                address: address,
                                isSelected: selected == address,
                                onTap: { selected = address }
                            )
                        }
                    }
                }
                .frame(maxHeight: 280)
            }

            HStack {
                Spacer()
                Button("Cancel", action: onCancel)
                    .keyboardShortcut(.cancelAction)
                Button("Connect") {
                    if let selected = selected {
                        onSelected(selected)
                    }
                }
                .keyboardShortcut(.defaultAction)
                .disabled(selected == nil)
            }
        }
        .padding(20)
        .frame(width: 460)
        .onAppear {
            if selected == nil {
                selected = candidates.first
            }
        }
    }
}

private struct ContractCandidateRow: View {
    let address: String
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: isSelected
                ? "largecircle.fill.circle"
                : "circle"
            )
            .foregroundColor(isSelected
                ? Material3Colors.primary
                : Material3Colors.onSurfaceVariant
            )

            VStack(alignment: .leading, spacing: 2) {
                Text("Smart Account")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Material3Colors.onSurface)
                Text(truncate(address, prefixCount: 8, suffixCount: 8))
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }

            Spacer()
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(isSelected
                    ? Material3Colors.primaryContainer.opacity(0.5)
                    : Material3Colors.surfaceVariant.opacity(0.5)
                )
        )
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }

    private func truncate(_ value: String, prefixCount: Int, suffixCount: Int) -> String {
        guard value.count > prefixCount + suffixCount else { return value }
        let prefix = value.prefix(prefixCount)
        let suffix = value.suffix(suffixCount)
        return "\(prefix)...\(suffix)"
    }
}
