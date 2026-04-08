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
                action: performAddressConnect,
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
                    if result != nil {
                        appState.sync(from: bridgeWrapper.bridge)
                        toastManager.show("Connected successfully")
                        dismiss()
                    } else {
                        autoConnectError = "No wallet found for this passkey."
                    }
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
                    if result != nil {
                        appState.sync(from: bridgeWrapper.bridge)
                        toastManager.show("Connected successfully")
                        dismiss()
                    } else {
                        indexerError = "No contract found for this credential."
                    }
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

    private func performAddressConnect() {
        clearAllErrors()
        let address = contractAddressInput.trimmingCharacters(in: .whitespaces)
        activeSection = .address
        Task {
            do {
                let result = try await bridgeWrapper.bridge.connectWithAddress(contractAddress: address)
                await MainActor.run {
                    activeSection = nil
                    if result != nil {
                        appState.sync(from: bridgeWrapper.bridge)
                        toastManager.show("Connected successfully")
                        dismiss()
                    } else {
                        addressError = "Could not connect to the provided contract address."
                    }
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
