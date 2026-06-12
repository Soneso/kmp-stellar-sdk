//
//  WalletCreationScreen.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

/// Screen that collects a display name, triggers passkey registration, and optionally deploys a
/// smart account contract to the Stellar testnet.
///
/// The screen mirrors the Compose `WalletCreationScreen` and covers the full wallet creation
/// lifecycle:
/// 1. The user enters a passkey display name.
/// 2. Tapping "Create Wallet" calls `MacOSBridge.createWallet(username:autoSubmit:onProgress:)`.
/// 3. Progress messages are shown in a live card while the operation is in flight.
/// 4. On success:
///    - If autoSubmit=true: a green result card shows credentials, balances, and "Go to Main Screen".
///    - If autoSubmit=false: a blue info card shows the passkey was registered but not deployed,
///      with a "Deploy Now" button to retry deployment inline.
/// 5. User cancellation (Touch ID dismissed) is distinguished from genuine errors.
struct WalletCreationScreen: View {

    // MARK: - Environment

    @EnvironmentObject private var bridgeWrapper: MacOSBridgeWrapper
    @EnvironmentObject private var appState: AppState
    @ObservedObject var toastManager: ToastManager
    @Environment(\.dismiss) private var dismiss

    // MARK: - State

    @State private var username: String = ""
    @State private var autoSubmit: Bool = true
    @State private var isLoading: Bool = false
    @State private var progressMessage: String = ""
    @State private var errorMessage: String? = nil
    @State private var infoMessage: String? = nil
    @State private var createResult: WalletCreationResult? = nil

    // Deploy-from-creation-screen state (for autoSubmit=false results)
    @State private var isDeploying: Bool = false
    @State private var deployErrorMessage: String? = nil
    @State private var deployedSuccessfully: Bool = false

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
                if let error = errorMessage { ErrorCard(message: error) }
                if let info = infoMessage { infoCard(message: info) }
                if isLoading { progressCard }
                if createResult == nil && !isLoading { autoSubmitToggle }
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

    // MARK: - Auto-submit Toggle

    private var autoSubmitToggle: some View {
        Toggle("Auto-deploy after passkey registration", isOn: $autoSubmit)
            .toggleStyle(.checkbox)
            .font(.system(size: 13))
            .foregroundStyle(Material3Colors.onSurface)
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
            if deployedSuccessfully {
                deployedCard(result: result)
            } else {
                undeployedCard(result: result)
            }

            LoadingButton(
                action: { dismiss() },
                isLoading: false,
                isEnabled: !isDeploying,
                text: "Go to Main Screen",
                loadingText: "",
                style: .filled
            )
        }
    }

    // MARK: - Deployed Success Card

    @ViewBuilder
    private func deployedCard(result: WalletCreationResult) -> some View {
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

    // MARK: - Undeployed Card

    @ViewBuilder
    private func undeployedCard(result: WalletCreationResult) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Passkey Registered")
                .font(.headline)
                .fontWeight(.semibold)
                .foregroundStyle(Material3Colors.onSecondaryContainer)

            CopyableField(
                label: "Credential ID",
                value: result.credentialId,
                textColor: Material3Colors.onSecondaryContainer,
                labelColor: Material3Colors.onSecondaryContainer,
                monospace: true,
                onCopy: { toastManager.show("Credential ID copied to clipboard") }
            )

            CopyableField(
                label: "Contract Address (derived)",
                value: result.contractId,
                textColor: Material3Colors.onSecondaryContainer,
                labelColor: Material3Colors.onSecondaryContainer,
                monospace: true,
                onCopy: { toastManager.show("Contract address copied to clipboard") }
            )

            // Warning message
            warningBanner(
                text: "The wallet contract has not been deployed to the network yet. " +
                      "Deploy it now or later from the Connect Wallet screen."
            )

            // Deploy error
            if let error = deployErrorMessage {
                Text(error)
                    .font(.system(size: 13))
                    .foregroundStyle(Material3Colors.onErrorContainer)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Material3Colors.errorContainer)
                    .cornerRadius(6)
            }

            // Deploy progress
            if isDeploying {
                HStack(spacing: 8) {
                    ProgressView()
                        .controlSize(.small)
                    Text("Deploying contract...")
                        .font(.system(size: 13))
                        .foregroundStyle(Material3Colors.onSecondaryContainer)
                }
                .frame(maxWidth: .infinity)
            }

            // Deploy Now button
            LoadingButton(
                action: deployFromCreationScreen,
                isLoading: isDeploying,
                isEnabled: !isDeploying,
                text: "Deploy Now",
                loadingText: "Deploying...",
                style: .filled
            )
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.secondaryContainer)
        .cornerRadius(8)
    }

    @ViewBuilder
    private func balanceSection(result: WalletCreationResult) -> some View {
        BalanceRows(
            label: "Balance",
            labelColor: Material3Colors.primary,
            values: balanceValues(result: result),
            valueFont: .system(.callout, design: .monospaced),
            valueColor: Material3Colors.onPrimaryContainer
        )
    }

    /// Formatted balance lines: XLM always, DEMO only when minted during provisioning.
    private func balanceValues(result: WalletCreationResult) -> [String] {
        var values = ["\(result.xlmBalance) XLM"]
        if let demoBalance = result.demoTokenBalance {
            values.append("\(demoBalance) DEMO")
        }
        return values
    }

    @ViewBuilder
    private func warningBanner(text: String) -> some View {
        Text(text)
            .font(.system(size: 13))
            .foregroundStyle(Material3Colors.warningText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Material3Colors.warningBackground)
            .cornerRadius(6)
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
                    autoSubmit: autoSubmit,
                    onProgress: { progress in
                        DispatchQueue.main.async {
                            self.progressMessage = progress
                        }
                    }
                )
                await MainActor.run {
                    self.createResult = result
                    self.isLoading = false
                    appState.sync(from: bridgeWrapper.bridge)
                    self.deployedSuccessfully = appState.isDeployed
                    if self.deployedSuccessfully {
                        toastManager.show("Wallet created successfully")
                    } else {
                        toastManager.show("Passkey registered (deployment pending)")
                    }
                }
            } catch {
                await MainActor.run {
                    let message = error.localizedDescription
                    if bridgeWrapper.bridge.isUserCancellation(message: message) {
                        self.infoMessage = "Passkey registration cancelled by user"
                        ActivityLogState.shared.info(message: "User cancelled passkey registration")
                    } else {
                        self.errorMessage =
                            "Failed to create wallet: \(message)\n\n" +
                            "If a passkey was registered before the failure, " +
                            "go to Connect Wallet and check Pending Deployments " +
                            "to retry the deployment."
                        ActivityLogState.shared.error(message: message)
                    }
                    appState.syncActivityLog(from: bridgeWrapper.bridge)
                    self.isLoading = false
                }
            }
        }
    }

    private func deployFromCreationScreen() {
        guard let result = createResult else { return }
        isDeploying = true
        deployErrorMessage = nil

        Task {
            do {
                let provisionResult = try await bridgeWrapper.bridge.deployPendingAndProvision(
                    credentialId: result.credentialId,
                    onProgress: { progress in
                        DispatchQueue.main.async {
                            self.progressMessage = progress
                        }
                    }
                )
                await MainActor.run {
                    appState.sync(from: bridgeWrapper.bridge)
                    if provisionResult.success {
                        self.deployedSuccessfully = true
                        // Update the result with balances from provisioning
                        self.createResult = WalletCreationResult(
                            credentialId: result.credentialId,
                            contractId: result.contractId,
                            transactionHash: result.transactionHash,
                            nickname: result.nickname,
                            xlmBalance: provisionResult.xlmBalance,
                            demoTokenBalance: provisionResult.demoTokenBalance
                        )
                        toastManager.show("Wallet deployed successfully")
                    } else {
                        self.deployErrorMessage = "Deployment failed: \(provisionResult.error ?? "Unknown error")"
                    }
                    self.isDeploying = false
                }
            } catch {
                await MainActor.run {
                    self.deployErrorMessage = "Deployment failed: \(error.localizedDescription)"
                    self.isDeploying = false
                    appState.syncActivityLog(from: bridgeWrapper.bridge)
                }
            }
        }
    }
}
