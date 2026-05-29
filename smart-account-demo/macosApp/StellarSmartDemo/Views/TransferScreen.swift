//
//  TransferScreen.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

/// Token transfer screen: select a token, enter a recipient address and amount, then submit.
///
/// When the connected smart account has multiple registered signers a signer picker sheet
/// is presented so the user can choose which signers co-authorize the transaction. The
/// transfer logic mirrors the Compose `TransferScreen` exactly:
/// - Single passkey path: `MacOSBridge.transfer(tokenContract:recipient:amount:)`.
/// - Multi-signer path: `MacOSBridge.multiSignerTransfer(...)` after the picker confirms.
struct TransferScreen: View {

    // MARK: - Environment

    @EnvironmentObject var bridgeWrapper: MacOSBridgeWrapper
    @EnvironmentObject var appState: AppState
    @ObservedObject var toastManager: ToastManager
    @Environment(\.dismiss) private var dismiss

    // MARK: - Token constants

    private static let tokenXLM = "xlm"
    private static let tokenDemo = "demo"

    // MARK: - Form state

    @State private var selectedToken = tokenXLM
    @State private var recipient = ""
    @State private var amount = ""

    // MARK: - Operation state

    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var txHash: String? = nil
    /// Snapshot of the token label captured at the moment the transfer was submitted,
    /// so the success card can display it even if selectedToken changes later.
    @State private var submittedTokenLabel = ""
    /// Snapshot of the recipient captured at submit time for the success card.
    @State private var submittedRecipient = ""
    /// Snapshot of the amount captured at submit time for the success card.
    @State private var submittedAmount = ""

    // MARK: - Signer state

    @State private var availableSigners: [SignerInfoBridge] = []
    @State private var signersLoaded = false

    // MARK: - Signer picker

    @State private var showSignerPicker = false
    @State private var selectedSignerDescriptors: [SignerDescriptor] = []
    @State private var delegatedSecretKeys: [String: String] = [:]

    // MARK: - Init

    init(toastManager: ToastManager) {
        self.toastManager = toastManager
    }

    // MARK: - Derived state

    private var tokenLabel: String {
        selectedToken == TransferScreen.tokenXLM ? "XLM" : "DEMO"
    }

    private var recipientError: String? {
        guard !recipient.isEmpty else { return nil }
        let error = FormValidation.validateRecipient(recipient)
        if let e = error { return e }
        // Prevent transferring to own account
        if let contractId = appState.contractId, recipient == contractId {
            return "Cannot transfer to your own account"
        }
        return nil
    }

    private var amountError: String? {
        guard !amount.isEmpty else { return nil }
        return FormValidation.validateAmount(amount)
    }

    private var isDemoAvailable: Bool {
        appState.demoTokenContractId != nil
    }

    private var isFormValid: Bool {
        !recipient.isEmpty &&
        !amount.isEmpty &&
        recipientError == nil &&
        amountError == nil &&
        (selectedToken == TransferScreen.tokenXLM || isDemoAvailable)
    }

    // MARK: - Body

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if !appState.isConnected {
                    notConnectedSection
                } else {
                    infoCard
                    balanceCard
                    tokenPicker
                    recipientField
                    amountField
                    if let error = errorMessage {
                        errorCard(message: error)
                    }
                    if txHash == nil {
                        transferButton
                    }
                    if txHash != nil {
                        successCard
                        newTransferButton
                        goToMainButton
                    }
                    Spacer().frame(height: 16)
                }
            }
            .padding(16)
        }
        .background(Material3Colors.background)
        .navigationToolbar(title: "Transfer")
        .task {
            await loadSigners()
        }
        .sheet(isPresented: $showSignerPicker) {
            SignerPickerSheet(
                signers: availableSigners,
                activeCredentialId: appState.credentialId,
                ed25519VerifierAddress: bridgeWrapper.bridge.getEd25519VerifierAddress(),
                onConfirm: { selected, secretKeys, ed25519Secrets in
                    showSignerPicker = false
                    performMultiSignerTransfer(
                        selected: selected,
                        secretKeys: secretKeys,
                        ed25519Secrets: ed25519Secrets
                    )
                },
                onDismiss: {
                    showSignerPicker = false
                },
                bridge: bridgeWrapper.bridge
            )
        }
    }

    // MARK: - Not-connected section

    @ViewBuilder
    private var notConnectedSection: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("No wallet connected. Please connect a wallet first.")
                .font(.system(size: 13))
                .foregroundStyle(Material3Colors.onErrorContainer)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.errorContainer)
        .cornerRadius(8)

        LoadingButton(
            action: { dismiss() },
            isLoading: false,
            isEnabled: true,
            text: "Go Back",
            loadingText: "",
            style: .filled
        )
    }

    // MARK: - Info card

    private var infoCard: some View {
        InfoCard(title: "Token Transfer", color: .variant) {
            Text(
                "Send tokens from your smart account to another Stellar address. " +
                "This requires passkey authentication to sign the transaction."
            )
            .font(.system(size: 13))
            .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
    }

    // MARK: - Balance card

    private var balanceCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Balance")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Material3Colors.onPrimaryContainer)

            HStack(spacing: 16) {
                Text("\(appState.xlmBalance ?? "0.0") XLM")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)

                if let demoBalance = appState.demoTokenBalance {
                    Text("\(demoBalance) DEMO")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Material3Colors.onPrimaryContainer)
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.primaryContainer)
        .cornerRadius(8)
    }

    // MARK: - Token picker

    private var tokenPicker: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Token")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Picker("", selection: $selectedToken) {
                Text("XLM (Native)").tag(TransferScreen.tokenXLM)
                Text(isDemoAvailable ? "Demo Token (DEMO)" : "Demo Token (DEMO) — unavailable")
                    .tag(TransferScreen.tokenDemo)
            }
            .pickerStyle(.segmented)
            .disabled(isLoading || txHash != nil)
            .onChange(of: selectedToken) { _ in
                // Reset error when token selection changes
                errorMessage = nil
            }
            .opacity((isLoading || txHash != nil) ? 0.5 : 1.0)
        }
    }

    // MARK: - Recipient field

    private var recipientField: some View {
        ValidationTextField(
            label: "Recipient Address",
            text: $recipient,
            error: recipientError,
            placeholder: "G... or C...",
            isMonospace: true,
            isEnabled: !isLoading && txHash == nil
        )
        .onChange(of: recipient) { _ in errorMessage = nil }
    }

    // MARK: - Amount field

    private var amountField: some View {
        ValidationTextField(
            label: "Amount",
            text: $amount,
            error: amountError,
            placeholder: "0.0",
            isEnabled: !isLoading && txHash == nil
        )
        .onChange(of: amount) { _ in errorMessage = nil }
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

    // MARK: - Transfer button

    private var transferButton: some View {
        LoadingButton(
            action: handleTransferTap,
            isLoading: isLoading,
            isEnabled: isFormValid && !isLoading && bridgeWrapper.isKitInitialized,
            icon: "paperplane",
            text: "Transfer",
            loadingText: "Transferring...",
            style: .filled
        )
    }

    // MARK: - Success card

    private var successCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Transfer Successful")
                .font(.headline)
                .fontWeight(.bold)
                .foregroundStyle(Material3Colors.onPrimaryContainer)

            if let hash = txHash {
                CopyableField(
                    label: "Transaction Hash",
                    value: hash,
                    textColor: Material3Colors.onPrimaryContainer,
                    labelColor: Material3Colors.onPrimaryContainer,
                    monospace: true,
                    onCopy: { toastManager.show("Transaction hash copied to clipboard") }
                )
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("Amount Sent")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundStyle(Material3Colors.onPrimaryContainer)

                Text("\(submittedAmount) \(submittedTokenLabel)")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("Recipient")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundStyle(Material3Colors.onPrimaryContainer)

                Text(submittedRecipient)
                    .font(.system(.callout, design: .monospaced))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)
                    .lineLimit(2)
                    .truncationMode(.middle)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Updated Balance")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundStyle(Material3Colors.onPrimaryContainer)

                HStack(spacing: 16) {
                    Text("\(appState.xlmBalance ?? "0.0") XLM")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Material3Colors.onPrimaryContainer)

                    if let demoBalance = appState.demoTokenBalance {
                        Text("\(demoBalance) DEMO")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Material3Colors.onPrimaryContainer)
                    }
                }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.primaryContainer)
        .cornerRadius(8)
    }

    // MARK: - Post-success buttons

    private var newTransferButton: some View {
        LoadingButton(
            action: resetForm,
            isLoading: false,
            isEnabled: true,
            text: "New Transfer",
            loadingText: "",
            style: .filled
        )
    }

    private var goToMainButton: some View {
        LoadingButton(
            action: { dismiss() },
            isLoading: false,
            isEnabled: true,
            text: "Go to Main Screen",
            loadingText: "",
            style: .outlined
        )
    }

    // MARK: - Actions

    private func loadSigners() async {
        guard appState.isConnected else { return }
        do {
            let result = try await bridgeWrapper.bridge.loadAvailableSigners()
            let signers = KotlinInterop.toArray(result, as: SignerInfoBridge.self)
            await MainActor.run {
                availableSigners = signers
                signersLoaded = true
            }
        } catch {
            await MainActor.run {
                signersLoaded = true
            }
        }
    }

    /// Called when the Transfer button is tapped.
    ///
    /// Routes to the single-signer path when there is at most one signer (or signers have not
    /// yet been loaded), otherwise opens the signer picker sheet.
    private func handleTransferTap() {
        guard !isLoading, isFormValid else { return }

        if !signersLoaded || availableSigners.count <= 1 {
            performSingleSignerTransfer()
        } else {
            showSignerPicker = true
        }
    }

    /// Executes a simple passkey-authenticated transfer.
    private func performSingleSignerTransfer() {
        let tokenContract = resolveTokenContract()
        let capturedRecipient = recipient
        let capturedAmount = amount
        let capturedLabel = tokenLabel

        isLoading = true
        errorMessage = nil

        Task {
            do {
                let result = try await bridgeWrapper.bridge.transfer(
                    tokenContract: tokenContract,
                    recipient: capturedRecipient,
                    amount: capturedAmount
                )

                await MainActor.run {
                    if result.success {
                        submittedTokenLabel = capturedLabel
                        submittedRecipient = capturedRecipient
                        submittedAmount = capturedAmount
                        txHash = result.hash
                        appState.sync(from: bridgeWrapper.bridge)
                    } else {
                        handleTransferError(result.error ?? "Transfer failed")
                    }
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    handleTransferError(error.localizedDescription)
                    isLoading = false
                }
            }
        }
    }

    /// Called by the signer picker sheet on confirmation.
    ///
    /// Determines whether to use the single-passkey path (only the connected passkey was
    /// chosen) or the multi-signer path.
    private func performMultiSignerTransfer(
        selected: [SignerInfoBridge],
        secretKeys: [String: String],
        ed25519Secrets: [String: String] = [:]
    ) {
        let tokenContract = resolveTokenContract()
        let capturedRecipient = recipient
        let capturedAmount = amount
        let capturedLabel = tokenLabel

        isLoading = true
        errorMessage = nil

        // Build SignerDescriptor list for the bridge.
        let descriptors = selected.map { signer in
            SignerDescriptor(type: signer.type, value: signer.identifier)
        }

        // Determine if only the connected passkey was selected — use the simple path.
        let isSingleOwnPasskey: Bool = {
            guard selected.count == 1,
                  let only = selected.first,
                  only.type.lowercased() == "passkey",
                  let credId = appState.credentialId,
                  only.identifier == credId
            else { return false }
            return true
        }()

        Task {
            do {
                let result: TransferResult

                if isSingleOwnPasskey {
                    result = try await bridgeWrapper.bridge.transfer(
                        tokenContract: tokenContract,
                        recipient: capturedRecipient,
                        amount: capturedAmount
                    )
                } else {
                    result = try await bridgeWrapper.bridge.multiSignerTransfer(
                        tokenContract: tokenContract,
                        recipient: capturedRecipient,
                        amount: capturedAmount,
                        signerDescriptors: descriptors,
                        delegatedSecretKeys: secretKeys,
                        ed25519SecretKeys: ed25519Secrets
                    )
                }

                await MainActor.run {
                    if result.success {
                        submittedTokenLabel = capturedLabel
                        submittedRecipient = capturedRecipient
                        submittedAmount = capturedAmount
                        txHash = result.hash
                        appState.sync(from: bridgeWrapper.bridge)
                    } else {
                        handleTransferError(result.error ?? "Transfer failed")
                    }
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    handleTransferError(error.localizedDescription)
                    isLoading = false
                }
            }
        }
    }

    /// Interprets the error message and sets `errorMessage` accordingly.
    ///
    /// User cancellation (Touch ID dismissed) is shown as a distinct informational message
    /// rather than a generic failure string.
    private func handleTransferError(_ message: String) {
        if bridgeWrapper.bridge.isUserCancellation(message: message) {
            errorMessage = "Passkey authentication cancelled"
        } else {
            errorMessage = "Transfer failed: \(message)"
        }
    }

    /// Resets all form and result state for a new transfer.
    private func resetForm() {
        recipient = ""
        amount = ""
        txHash = nil
        errorMessage = nil
        selectedToken = TransferScreen.tokenXLM
        submittedTokenLabel = ""
        submittedRecipient = ""
        submittedAmount = ""
    }

    /// Returns the token contract address for the currently selected token.
    private func resolveTokenContract() -> String {
        if selectedToken == TransferScreen.tokenXLM {
            return bridgeWrapper.bridge.getNativeTokenContract()
        }
        return appState.demoTokenContractId ?? ""
    }
}
