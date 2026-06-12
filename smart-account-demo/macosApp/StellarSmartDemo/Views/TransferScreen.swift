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
    /// True when the signer fetch failed; distinguishes load-failed from loaded-empty
    /// so the UI can warn that multi-signer routing is unavailable.
    @State private var signerLoadFailed = false

    // MARK: - Signer picker

    @State private var showSignerPicker = false

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
                    NotConnectedCard(
                        message: "No wallet connected. Please connect a wallet first.",
                        onGoBack: { dismiss() }
                    )
                } else {
                    infoCard
                    balanceCard
                    if signerLoadFailed {
                        signerLoadWarning
                    }
                    tokenPicker
                    recipientField
                    amountField
                    if let error = errorMessage {
                        ErrorCard(message: error)
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
                description: "Choose which signers co-authorize this transfer. " +
                    "For Stellar account signers, enter the secret key to enable signing.",
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
        BalanceRows(
            label: "Balance",
            labelFont: .system(size: 12, weight: .semibold),
            labelColor: Material3Colors.onPrimaryContainer,
            values: balanceValues,
            valueFont: .system(size: 15, weight: .bold),
            valueColor: Material3Colors.onPrimaryContainer,
            horizontal: true
        )
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.primaryContainer)
        .cornerRadius(8)
    }

    /// Formatted balance lines: XLM always, DEMO only when its balance is known.
    private var balanceValues: [String] {
        var values = ["\(appState.xlmBalance ?? "0.0") XLM"]
        if let demoBalance = appState.demoTokenBalance {
            values.append("\(demoBalance) DEMO")
        }
        return values
    }

    // MARK: - Token picker

    private var tokenPicker: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Token")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Picker("", selection: $selectedToken) {
                Text("XLM (Native)").tag(TransferScreen.tokenXLM)
                demoSegmentLabel.tag(TransferScreen.tokenDemo)
            }
            .pickerStyle(.segmented)
            .disabled(isLoading || txHash != nil)
            .onChange(of: selectedToken) { newValue in
                // The DEMO segment is selectable only when the DEMO token contract is
                // resolved; on macOS 13 (no per-segment disabling) revert the selection.
                if newValue == TransferScreen.tokenDemo && !isDemoAvailable {
                    selectedToken = TransferScreen.tokenXLM
                    return
                }
                // Reset error when token selection changes
                errorMessage = nil
            }
            .opacity((isLoading || txHash != nil) ? 0.5 : 1.0)
        }
    }

    /// Label for the DEMO token segment, disabled for selection when the DEMO token
    /// contract is not resolved (macOS 14+; older versions rely on the onChange revert).
    @ViewBuilder
    private var demoSegmentLabel: some View {
        let label = Text(isDemoAvailable ? "Demo Token (DEMO)" : "Demo Token (DEMO) — unavailable")
        if #available(macOS 14.0, *) {
            label.selectionDisabled(!isDemoAvailable)
        } else {
            label
        }
    }

    // MARK: - Recipient field

    private var recipientField: some View {
        ValidationTextField(
            label: "Recipient Address",
            text: $recipient,
            error: recipientError,
            placeholder: "G... or C... address",
            helperText: "Stellar account (G...) or contract (C...) address",
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
            placeholder: "e.g. 10.0",
            helperText: "Amount to transfer",
            isEnabled: !isLoading && txHash == nil
        )
        .onChange(of: amount) { _ in errorMessage = nil }
    }

    // MARK: - Signer load warning

    /// Non-blocking notice that the signer list could not be loaded. Single-signer
    /// transfers keep working; only the multi-signer picker is unavailable.
    private var signerLoadWarning: some View {
        Text("Could not load signers — multi-signer operations unavailable")
            .font(.footnote)
            .foregroundColor(Material3Colors.badgeExpiryText)
            .frame(maxWidth: .infinity, alignment: .leading)
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
                    .lineLimit(1)
                    .truncationMode(.tail)
            }

            BalanceRows(
                label: "Updated Balance",
                labelColor: Material3Colors.onPrimaryContainer,
                values: balanceValues,
                valueFont: .system(size: 14, weight: .bold),
                valueColor: Material3Colors.onPrimaryContainer,
                horizontal: true
            )
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

    /// Loads the available signers. On failure, single-signer transfers stay usable;
    /// the load-failed flag drives the multi-signer-unavailable warning.
    private func loadSigners() async {
        guard appState.isConnected else { return }
        let result = await SignerSelectionSupport.loadAvailableSigners(bridge: bridgeWrapper.bridge)
        await MainActor.run {
            if !result.loadFailed {
                availableSigners = result.signers
            }
            signersLoaded = true
            signerLoadFailed = result.loadFailed
        }
    }

    /// Called when the Transfer button is tapped.
    ///
    /// Routes to the single-signer path when there is at most one signer (or signers have not
    /// yet been loaded), otherwise opens the signer picker sheet.
    private func handleTransferTap() {
        guard !isLoading, isFormValid else { return }

        if SignerSelectionSupport.usesSingleSignerPath(
            signersLoaded: signersLoaded,
            availableSigners: availableSigners
        ) {
            performSingleSignerTransfer()
        } else {
            showSignerPicker = true
        }
    }

    /// Executes a simple passkey-authenticated transfer.
    private func performSingleSignerTransfer() {
        let tokenContract = resolveTokenContract()
        // Validators accept surrounding whitespace by trimming; capture the same
        // trimmed values so the bridge never sees padded input.
        let capturedRecipient = recipient.trimmingCharacters(in: .whitespaces)
        let capturedAmount = amount.trimmingCharacters(in: .whitespaces)
        let capturedLabel = tokenLabel

        isLoading = true
        errorMessage = nil
        ActivityLogState.shared.info(
            message: "Transferring \(capturedAmount) \(capturedLabel) to \(capturedRecipient.prefix(8))..."
        )
        appState.syncActivityLog(from: bridgeWrapper.bridge)

        Task {
            do {
                let result = try await bridgeWrapper.bridge.transfer(
                    tokenContract: tokenContract,
                    recipient: capturedRecipient,
                    amount: capturedAmount
                )

                await MainActor.run {
                    applyTransferResult(
                        result,
                        tokenLabel: capturedLabel,
                        recipient: capturedRecipient,
                        amount: capturedAmount
                    )
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
        // Validators accept surrounding whitespace by trimming; capture the same
        // trimmed values so the bridge never sees padded input.
        let capturedRecipient = recipient.trimmingCharacters(in: .whitespaces)
        let capturedAmount = amount.trimmingCharacters(in: .whitespaces)
        let capturedLabel = tokenLabel

        isLoading = true
        errorMessage = nil

        // Build SignerDescriptor list for the bridge. Auth signers are existing
        // on-chain signers, so isPending is always false here.
        let descriptors = selected.map { signer in
            SignerDescriptor(type: signer.type, value: signer.identifier, isPending: false)
        }

        // Determine if only the connected passkey was selected — use the simple path.
        let isSingleOwnPasskey = SignerSelectionSupport.isSingleOwnPasskey(
            selected: selected,
            connectedCredentialId: appState.credentialId
        )

        if isSingleOwnPasskey {
            ActivityLogState.shared.info(
                message: "Transferring \(capturedAmount) \(capturedLabel) to \(capturedRecipient.prefix(8))..."
            )
        } else {
            ActivityLogState.shared.info(
                message: "Multi-signer transfer: \(capturedAmount) \(capturedLabel) " +
                    "to \(capturedRecipient.prefix(8))... (\(descriptors.count) signer(s))"
            )
        }
        appState.syncActivityLog(from: bridgeWrapper.bridge)

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
                    applyTransferResult(
                        result,
                        tokenLabel: capturedLabel,
                        recipient: capturedRecipient,
                        amount: capturedAmount
                    )
                }
            } catch {
                await MainActor.run {
                    handleTransferError(error.localizedDescription)
                    isLoading = false
                }
            }
        }
    }

    /// Applies a completed transfer result on the main actor: records the submitted
    /// values for the success card on success, or routes the error message.
    private func applyTransferResult(
        _ result: TransferResult,
        tokenLabel: String,
        recipient: String,
        amount: String
    ) {
        if result.success {
            submittedTokenLabel = tokenLabel
            submittedRecipient = recipient
            submittedAmount = amount
            txHash = result.hash
            appState.sync(from: bridgeWrapper.bridge)
        } else {
            handleTransferError(result.error ?? "Transfer failed")
        }
        isLoading = false
    }

    /// Interprets the error message, sets `errorMessage`, and records the outcome in the
    /// activity log.
    ///
    /// User cancellation (Touch ID dismissed) is shown as a distinct informational message
    /// rather than a generic failure string.
    private func handleTransferError(_ message: String) {
        errorMessage = SignerSelectionSupport.interpretOperationError(
            message,
            failurePrefix: "Transfer failed:",
            bridge: bridgeWrapper.bridge,
            appState: appState
        )
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
