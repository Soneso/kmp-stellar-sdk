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
/// contract (Transfer, Context Rules, Account Signers) are disabled until deployment.
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
                walletStatusSection
                activityLogSection
            }
            .padding(16)
        }
        .background(Material3Colors.background)
        .frame(minWidth: 550, minHeight: 700)
        .navigationToolbar(
            title: "Smart Account Demo",
            subtitle: "Testnet",
            showBackButton: false
        )
        .task {
            guard !hasInitialized else { return }
            hasInitialized = true
            await initializeKitOnce()
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
                    Text("Create Wallet")
                        .font(.system(size: 15, weight: .medium))
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .foregroundColor(.white)
                        .background(Material3Colors.primary)
                        .cornerRadius(8)
                }
                .buttonStyle(.plain)

                NavigationLink(destination: WalletConnectionScreen(toastManager: toastManager)) {
                    Text("Connect Wallet")
                        .font(.system(size: 15, weight: .medium))
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .foregroundColor(.white)
                        .background(Material3Colors.primary)
                        .cornerRadius(8)
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
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Balance")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(Material3Colors.onPrimaryContainer.opacity(0.7))

                        Text("\(appState.xlmBalance ?? "Loading...") XLM")
                            .font(.callout)
                            .fontWeight(.bold)
                            .foregroundColor(Material3Colors.onPrimaryContainer)

                        Text("\(appState.demoTokenBalance ?? "0.0") DEMO")
                            .font(.callout)
                            .fontWeight(.bold)
                            .foregroundColor(Material3Colors.onPrimaryContainer)
                    }

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
                    NavigationLink(destination: ContextRulesScreen(toastManager: toastManager)) {
                        Text("Context Rules")
                            .font(.system(size: 15, weight: .medium))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .foregroundColor(appState.isDeployed ? .white : .gray)
                            .background(appState.isDeployed ? Material3Colors.primary : Color.gray.opacity(0.3))
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                    .disabled(!appState.isDeployed)

                    NavigationLink(destination: TransferScreen(toastManager: toastManager)) {
                        Text("Transfer")
                            .font(.system(size: 15, weight: .medium))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .foregroundColor(appState.isDeployed ? .white : .gray)
                            .background(appState.isDeployed ? Material3Colors.primary : Color.gray.opacity(0.3))
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                    .disabled(!appState.isDeployed)
                }

                NavigationLink(destination: KnownSignersScreen(toastManager: toastManager)) {
                    Text("Account Signers")
                        .font(.system(size: 15, weight: .medium))
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .foregroundColor(appState.isDeployed ? .white : .gray)
                        .background(appState.isDeployed ? Material3Colors.primary : Color.gray.opacity(0.3))
                        .cornerRadius(8)
                }
                .buttonStyle(.plain)
                .disabled(!appState.isDeployed)

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

    // MARK: - Undeployed Warning Card

    private var undeployedWarningCard: some View {
        let warningColor = Color(red: 0.902, green: 0.318, blue: 0.000) // Orange 900
        let warningBg = Color(red: 1.0, green: 0.953, blue: 0.878) // Orange 50

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
                onCopyEntry: { message in
                    ClipboardHelper.copy(message)
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
        let bridge = bridgeWrapper.bridge
        do {
            try await bridge.initializeKit()
            await MainActor.run {
                bridgeWrapper.isKitInitialized = true
            }
        } catch {
            // Error is logged to the activity log by the bridge itself
        }
        await MainActor.run {
            appState.sync(from: bridge)
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
