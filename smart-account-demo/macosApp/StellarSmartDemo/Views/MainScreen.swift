//
//  MainScreen.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

// MARK: - MainScreen

/// Root screen of the Smart Account Demo.
///
/// Mirrors the Compose `MainScreen` exactly. Shows either a disconnected wallet state
/// (with Create/Connect actions) or a connected wallet state (with contract address,
/// credential ID, balances, and navigation buttons). An activity log section is always
/// visible below the wallet status card.
///
/// When a wallet is connected but not yet deployed on-chain, a warning card is shown
/// with a "Deploy Now" button. Navigation buttons for features that require an on-chain
/// contract (Transfer, Approve, Context Rules, Account Signers) are disabled until deployment.
///
/// SDK initialization runs once via `.task` on first appear. After every bridge operation
/// `appState.sync(from:)` is called on the `MainActor` to propagate Kotlin state changes
/// into the observable SwiftUI state.
struct MainScreen: View {

    // MARK: - Environment

    @EnvironmentObject var bridgeWrapper: MacOSBridgeWrapper
    @EnvironmentObject var appState: AppState

    // MARK: - Properties

    @ObservedObject var toastManager: ToastManager

    // MARK: - Local state

    @State private var isRefreshingBalance = false
    @State private var hasInitialized = false
    @State private var isInitializingKit = false
    @State private var initErrorMessage: String? = nil
    @State private var isDeploying = false
    @State private var deployErrorMessage: String? = nil

    // MARK: - Init

    init(toastManager: ToastManager) {
        self.toastManager = toastManager
    }

    // MARK: - Body

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                initErrorSection
                walletStatusSection
                activityLogSection
            }
            .padding(16)
        }
        .background(Material3Colors.background)
        .frame(minWidth: 550, minHeight: 700)
        .navigationToolbar(
            title: "Stellar Smart Account Demo",
            subtitle: "Testnet",
            showBackButton: false
        )
        .task {
            guard !hasInitialized else { return }
            hasInitialized = true
            await initializeKitOnce()
        }
    }

    // MARK: - Init Error Section

    /// Shown when SDK kit initialization failed. All wallet actions depend on the kit,
    /// so the card offers a Retry that re-runs initialization.
    @ViewBuilder
    private var initErrorSection: some View {
        if let message = initErrorMessage {
            VStack(alignment: .leading, spacing: 8) {
                Text("Initialization Failed")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Material3Colors.onErrorContainer)

                Text(message)
                    .font(.system(size: 13))
                    .foregroundColor(Material3Colors.onErrorContainer)
                    .textSelection(.enabled)

                if isInitializingKit {
                    HStack(spacing: 6) {
                        ProgressView()
                            .controlSize(.small)
                            .tint(Material3Colors.error)
                        Text("Initializing...")
                            .font(.system(size: 13))
                            .foregroundColor(Material3Colors.onErrorContainer)
                    }
                }

                Button(action: {
                    Task { await initializeKitOnce() }
                }) {
                    Text(isInitializingKit ? "Retrying..." : "Retry")
                        .font(.system(size: 14, weight: .medium))
                        .frame(maxWidth: .infinity)
                        .frame(height: 36)
                        .foregroundColor(.white)
                        .background(isInitializingKit
                            ? Material3Colors.error.opacity(0.5)
                            : Material3Colors.error)
                        .cornerRadius(6)
                }
                .buttonStyle(.plain)
                .disabled(isInitializingKit)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Material3Colors.errorContainer)
            .cornerRadius(8)
        }
    }

    // MARK: - Wallet Status Section

    @ViewBuilder
    private var walletStatusSection: some View {
        InfoCard(title: "Wallet Status", color: .primary) {
            if appState.isConnected {
                connectedWalletContent
            } else {
                disconnectedWalletContent
            }
        }
    }

    // MARK: - Disconnected State

    private var disconnectedWalletContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Info description
            Text(
                "A smart account is a Soroban contract wallet controlled by passkey credentials. " +
                "Create a new wallet to register a passkey and deploy a contract, or connect an existing one."
            )
            .font(.callout)
            .foregroundColor(Material3Colors.onPrimaryContainer)

            // Status badge
            HStack(spacing: 8) {
                Image(systemName: "info.circle.fill")
                    .font(.system(size: 14))
                    .foregroundColor(Material3Colors.primary)
                Text("No wallet connected")
                    .font(.callout)
                    .foregroundColor(Material3Colors.onPrimaryContainer)
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Material3Colors.primaryContainer.opacity(0.6))
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(Material3Colors.primary.opacity(0.3), lineWidth: 1)
            )
            .cornerRadius(6)

            // Action buttons
            HStack(spacing: 8) {
                NavigationLink(destination: WalletCreationScreen(toastManager: toastManager)) {
                    PrimaryButtonLabel(
                        text: "Create Wallet",
                        style: .filled(foreground: .white, background: Material3Colors.primary)
                    )
                }
                .buttonStyle(.plain)

                NavigationLink(destination: WalletConnectionScreen(toastManager: toastManager)) {
                    PrimaryButtonLabel(
                        text: "Connect Wallet",
                        style: .filled(foreground: .white, background: Material3Colors.primary)
                    )
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Connected State

    private var connectedWalletContent: some View {
        VStack(alignment: .leading, spacing: 8) {

            // Contract Address
            if let contractId = appState.contractId {
                CopyableField(
                    label: "Contract Address",
                    value: KotlinInterop.truncateAddress(contractId, prefixLength: 12, suffixLength: 12),
                    textColor: Material3Colors.onPrimaryContainer,
                    labelColor: Material3Colors.onPrimaryContainer.opacity(0.7),
                    monospace: true,
                    onCopy: {
                        ClipboardHelper.copy(contractId)
                        toastManager.show("Contract address copied")
                    }
                )
            } else {
                unknownValueRow(label: "Contract Address")
            }

            Divider()
                .background(Material3Colors.onPrimaryContainer.opacity(0.15))

            // Credential ID
            if let credentialId = appState.credentialId {
                CopyableField(
                    label: "Credential ID",
                    value: credentialId,
                    textColor: Material3Colors.onPrimaryContainer,
                    labelColor: Material3Colors.onPrimaryContainer.opacity(0.7),
                    monospace: true,
                    onCopy: {
                        ClipboardHelper.copy(credentialId)
                        toastManager.show("Credential ID copied")
                    }
                )
            } else {
                unknownValueRow(label: "Credential ID")
            }

            Divider()
                .background(Material3Colors.onPrimaryContainer.opacity(0.15))

            // Undeployed warning card
            if !appState.isDeployed {
                undeployedWarningCard

                Divider()
                    .background(Material3Colors.onPrimaryContainer.opacity(0.15))
            }

            // Balances + Refresh (only when deployed)
            if appState.isDeployed {
                HStack(alignment: .top) {
                    BalanceRows(
                        label: "Balance",
                        labelColor: Material3Colors.onPrimaryContainer.opacity(0.7),
                        values: [
                            "\(appState.xlmBalance ?? "Loading...") XLM",
                            "\(appState.demoTokenBalance ?? "0.0") DEMO"
                        ],
                        valueFont: .callout.bold(),
                        valueColor: Material3Colors.onPrimaryContainer
                    )

                    Spacer()

                    Button(action: refreshBalance) {
                        HStack(spacing: 6) {
                            if isRefreshingBalance {
                                ProgressView()
                                    .controlSize(.small)
                                    .tint(Material3Colors.primary)
                                Text("Refreshing...")
                                    .font(.caption)
                                    .foregroundColor(Material3Colors.primary)
                            } else {
                                Text("Refresh")
                                    .font(.caption)
                                    .foregroundColor(Material3Colors.primary)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(Material3Colors.primary, lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(isRefreshingBalance)
                    .opacity(isRefreshingBalance ? 0.5 : 1.0)
                }

                Divider()
                    .background(Material3Colors.onPrimaryContainer.opacity(0.15))
                    .padding(.top, 4)
            }

            // Navigation buttons
            VStack(spacing: 8) {
                HStack(spacing: 8) {
                    navButton("Context Rules", destination: ContextRulesScreen(toastManager: toastManager))
                    navButton("Transfer", destination: TransferScreen(toastManager: toastManager))
                }

                HStack(spacing: 8) {
                    navButton("Approve", destination: ApproveScreen(toastManager: toastManager))
                    navButton("Account Signers", destination: KnownSignersScreen())
                }

                // Disconnect -- outlined with error-ish styling (disabled during deployment)
                LoadingButton(
                    action: disconnectWallet,
                    isLoading: false,
                    isEnabled: !isDeploying,
                    text: "Disconnect",
                    loadingText: "Disconnecting...",
                    style: .outlined
                )
            }
        }
    }

    /// Filled navigation button for the connected-wallet feature screens.
    /// Enabled only while the wallet contract is deployed on-chain.
    private func navButton<Destination: View>(_ title: String, destination: Destination) -> some View {
        NavigationLink(destination: destination) {
            PrimaryButtonLabel(
                text: title,
                style: .filled(
                    foreground: appState.isDeployed ? .white : .gray,
                    background: appState.isDeployed ? Material3Colors.primary : Color.gray.opacity(0.3)
                )
            )
        }
        .buttonStyle(.plain)
        .disabled(!appState.isDeployed)
    }

    /// Labeled row shown when a connected-wallet value is not available.
    private func unknownValueRow(label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(Material3Colors.onPrimaryContainer.opacity(0.7))
            Text("Unknown")
                .font(.system(.callout, design: .monospaced))
                .foregroundColor(Material3Colors.onPrimaryContainer)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Undeployed Warning Card

    private var undeployedWarningCard: some View {
        let warningColor = Material3Colors.warningText
        let warningBg = Material3Colors.warningBackground

        return VStack(alignment: .leading, spacing: 8) {
            Text("Wallet Not Deployed")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(warningColor)

            Text(
                "Your passkey is registered but the smart account contract has not been " +
                "deployed to the network. Deploy it to start using your wallet."
            )
            .font(.system(size: 13))
            .foregroundColor(warningColor)

            if let error = deployErrorMessage {
                Text(error)
                    .font(.system(size: 12))
                    .foregroundColor(Material3Colors.error)
            }

            if isDeploying {
                HStack(spacing: 6) {
                    ProgressView()
                        .controlSize(.small)
                        .tint(warningColor)
                    Text("Deploying contract...")
                        .font(.system(size: 13))
                        .foregroundColor(warningColor)
                }
            }

            Button(action: deployFromMainScreen) {
                Text(isDeploying ? "Deploying..." : "Deploy Now")
                    .font(.system(size: 14, weight: .medium))
                    .frame(maxWidth: .infinity)
                    .frame(height: 36)
                    .foregroundColor(.white)
                    .background(isDeploying ? warningColor.opacity(0.5) : warningColor)
                    .cornerRadius(6)
            }
            .buttonStyle(.plain)
            .disabled(isDeploying)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(warningBg)
        .cornerRadius(8)
    }

    // MARK: - Activity Log Section

    private var activityLogSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            ActivityLogView(
                entries: appState.activityLog,
                onClear: {
                    appState.clearActivityLog(bridge: bridgeWrapper.bridge)
                },
                onCopyEntry: { _ in
                    toastManager.show("Log message copied to clipboard")
                }
            )
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.surface)
        .cornerRadius(8)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }

    // MARK: - Actions

    private func initializeKitOnce() async {
        // Both call sites (the one-shot .task and the Retry button) run on the main
        // actor, so this synchronous guard prevents concurrent initialization runs.
        guard !isInitializingKit else { return }
        isInitializingKit = true
        let bridge = bridgeWrapper.bridge
        do {
            try await bridge.initializeKit()
            await MainActor.run {
                bridgeWrapper.isKitInitialized = true
                initErrorMessage = nil
            }
        } catch {
            // The bridge logs the failure to the activity log; the card adds a Retry path.
            await MainActor.run {
                initErrorMessage = "Failed to initialize: \(error.localizedDescription)"
            }
        }
        await MainActor.run {
            appState.sync(from: bridge)
            isInitializingKit = false
        }
    }

    private func refreshBalance() {
        let bridge = bridgeWrapper.bridge
        isRefreshingBalance = true
        Task {
            do {
                try await bridge.refreshBalances()
            } catch {
                await MainActor.run {
                    ActivityLogState.shared.error(
                        message: "Failed to refresh balance: \(error.localizedDescription)"
                    )
                    appState.syncActivityLog(from: bridge)
                }
            }
            await MainActor.run {
                appState.sync(from: bridge)
                isRefreshingBalance = false
            }
        }
    }

    private func disconnectWallet() {
        let bridge = bridgeWrapper.bridge
        Task {
            do {
                try await bridge.disconnect()
            } catch {
                await MainActor.run {
                    ActivityLogState.shared.error(
                        message: "Disconnect failed: \(error.localizedDescription)"
                    )
                    appState.syncActivityLog(from: bridge)
                }
            }
            await MainActor.run {
                appState.sync(from: bridge)
            }
        }
    }

    private func deployFromMainScreen() {
        guard let credentialId = appState.credentialId else { return }
        let bridge = bridgeWrapper.bridge
        isDeploying = true
        deployErrorMessage = nil

        Task {
            do {
                let result = try await bridge.deployPendingAndProvision(
                    credentialId: credentialId,
                    onProgress: { _ in }
                )
                await MainActor.run {
                    appState.sync(from: bridge)
                    if !result.success {
                        deployErrorMessage = "Deployment failed: \(result.error ?? "Unknown error")"
                    }
                    isDeploying = false
                    if result.success {
                        toastManager.show("Wallet deployed successfully")
                    }
                }
            } catch {
                await MainActor.run {
                    deployErrorMessage = "Deployment failed: \(error.localizedDescription)"
                    isDeploying = false
                    appState.syncActivityLog(from: bridge)
                }
            }
        }
    }
}
