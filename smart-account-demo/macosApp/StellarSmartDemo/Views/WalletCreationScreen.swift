//
//  WalletCreationScreen.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

/// Screen that collects a display name, triggers passkey registration, and deploys a smart account
/// contract to the Stellar testnet.
///
/// The screen mirrors the Compose `WalletCreationScreen` and covers the full wallet creation
/// lifecycle:
/// 1. The user enters a passkey display name.
/// 2. Tapping "Create Wallet" calls `MacOSBridge.createWallet(username:onProgress:)`.
/// 3. Progress messages are shown in a live card while the operation is in flight.
/// 4. On success a result card shows the credential ID, contract address, initial balances,
///    and a "Go to Main Screen" button.
/// 5. User cancellation (Touch ID dismissed) is distinguished from genuine errors and shown
///    as an informational message rather than an error.
struct WalletCreationScreen: View {

    // MARK: - Environment

    @EnvironmentObject private var bridgeWrapper: MacOSBridgeWrapper
    @EnvironmentObject private var appState: AppState
    @ObservedObject var toastManager: ToastManager
    @Environment(\.dismiss) private var dismiss

    // MARK: - State

    @State private var username: String = ""
    @State private var isLoading: Bool = false
    @State private var progressMessage: String = ""
    @State private var errorMessage: String? = nil
    @State private var infoMessage: String? = nil
    @State private var createResult: WalletCreationResult? = nil

    // MARK: - Derived State

    private var isButtonEnabled: Bool {
        !username.trimmingCharacters(in: .whitespaces).isEmpty
            && bridgeWrapper.isKitInitialized
            && !isLoading
            && createResult == nil
    }

    // MARK: - Body

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                descriptionCard
                usernameField
                if let error = errorMessage { errorCard(message: error) }
                if let info = infoMessage { infoCard(message: info) }
                if isLoading { progressCard }
                if createResult == nil && !isLoading { createButton }
                if let result = createResult { resultSection(result: result) }
                Spacer().frame(height: 16)
            }
            .padding(16)
        }
        .background(Material3Colors.background)
        .navigationToolbar(title: "Create Wallet")
    }

    // MARK: - Description Card

    private var descriptionCard: some View {
        InfoCard(title: "Wallet Creation", color: .variant) {
            Text(
                "Creating a wallet will register a passkey with your device and deploy a smart " +
                "account contract to the Stellar network. The passkey is used to authenticate " +
                "and sign transactions."
            )
            .font(.system(size: 13))
            .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
    }

    // MARK: - Username Input

    private var usernameField: some View {
        ValidationTextField(
            label: "Passkey Name",
            text: $username,
            placeholder: "Enter a display name for your passkey",
            isEnabled: !isLoading && createResult == nil
        )
    }

    // MARK: - Error Card

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

    // MARK: - Info Card (user cancellation)

    @ViewBuilder
    private func infoCard(message: String) -> some View {
        Text(message)
            .font(.system(size: 13))
            .foregroundStyle(Material3Colors.onSecondaryContainer)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Material3Colors.secondaryContainer)
            .cornerRadius(8)
    }

    // MARK: - Progress Card

    private var progressCard: some View {
        VStack(spacing: 12) {
            ProgressView()
                .controlSize(.large)
                .scaleEffect(1.1)
                .frame(width: 32, height: 32)
            Text(progressMessage.isEmpty ? "Creating..." : progressMessage)
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(8)
    }

    // MARK: - Create Button

    private var createButton: some View {
        LoadingButton(
            action: startWalletCreation,
            isLoading: isLoading,
            isEnabled: isButtonEnabled,
            text: "Create Wallet",
            loadingText: "Creating wallet...",
            style: .filled
        )
    }

    // MARK: - Result Section

    @ViewBuilder
    private func resultSection(result: WalletCreationResult) -> some View {
        VStack(spacing: 16) {
            successCard(result: result)
            LoadingButton(
                action: { dismiss() },
                isLoading: false,
                isEnabled: true,
                text: "Go to Main Screen",
                loadingText: "",
                style: .filled
            )
        }
    }

    @ViewBuilder
    private func successCard(result: WalletCreationResult) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Wallet Created Successfully")
                .font(.headline)
                .fontWeight(.semibold)
                .foregroundStyle(Material3Colors.onPrimaryContainer)

            CopyableField(
                label: "Credential ID",
                value: result.credentialId,
                textColor: Material3Colors.onPrimaryContainer,
                labelColor: Material3Colors.onPrimaryContainer,
                monospace: true,
                onCopy: { toastManager.show("Credential ID copied to clipboard") }
            )

            CopyableField(
                label: "Contract Address",
                value: result.contractId,
                textColor: Material3Colors.onPrimaryContainer,
                labelColor: Material3Colors.onPrimaryContainer,
                monospace: true,
                onCopy: { toastManager.show("Contract address copied to clipboard") }
            )

            if let txHash = result.transactionHash {
                CopyableField(
                    label: "Transaction Hash",
                    value: txHash,
                    textColor: Material3Colors.onPrimaryContainer,
                    labelColor: Material3Colors.onPrimaryContainer,
                    monospace: true,
                    onCopy: { toastManager.show("Transaction hash copied to clipboard") }
                )
            }

            balanceSection(result: result)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.primaryContainer)
        .cornerRadius(8)
    }

    @ViewBuilder
    private func balanceSection(result: WalletCreationResult) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Balance")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(Material3Colors.primary)

            Text("\(result.xlmBalance) XLM")
                .font(.system(.callout, design: .monospaced))
                .foregroundStyle(Material3Colors.onPrimaryContainer)

            if let demoBalance = result.demoTokenBalance {
                Text("\(demoBalance) DEMO")
                    .font(.system(.callout, design: .monospaced))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)
            }
        }
    }

    // MARK: - Actions

    private func startWalletCreation() {
        isLoading = true
        errorMessage = nil
        infoMessage = nil

        Task {
            do {
                let result = try await bridgeWrapper.bridge.createWallet(
                    username: username,
                    onProgress: { progress in
                        // Kotlin (String) -> Unit callback runs on the coroutine dispatcher thread.
                        // DispatchQueue.main.async is used here because this is a synchronous callback,
                        // not a Swift async context where MainActor.run would be appropriate.
                        DispatchQueue.main.async {
                            self.progressMessage = progress
                        }
                    }
                )
                await MainActor.run {
                    self.createResult = result
                    self.isLoading = false
                    appState.sync(from: bridgeWrapper.bridge)
                    toastManager.show("Wallet created successfully")
                }
            } catch {
                await MainActor.run {
                    let message = error.localizedDescription
                    if bridgeWrapper.bridge.isUserCancellation(message: message) {
                        self.infoMessage = "Passkey registration cancelled by user."
                    } else {
                        self.errorMessage =
                            "Failed to create wallet: \(message)\n\n" +
                            "If a passkey was registered before the failure, " +
                            "go to Connect Wallet and check Pending Deployments " +
                            "to retry the deployment."
                    }
                    self.isLoading = false
                }
            }
        }
    }
}
