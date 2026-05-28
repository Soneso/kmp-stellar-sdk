//
//  ApproveScreen.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

/// Token allowance approval screen: select token, enter spender address, amount, and
/// expiration, then submit the approval.
///
/// When the connected smart account has multiple registered signers a signer picker sheet
/// is presented so the user can choose which signers co-authorize the transaction. The
/// approval logic mirrors the Compose `ApproveScreen` exactly:
/// - Single passkey path: `MacOSBridge.approveAllowance(...)`.
/// - Multi-signer path: `MacOSBridge.multiSignerApproveAllowance(...)` after the picker confirms.
struct ApproveScreen: View {

    // MARK: - Environment

    @EnvironmentObject var bridgeWrapper: MacOSBridgeWrapper
    @EnvironmentObject var appState: AppState
    @ObservedObject var toastManager: ToastManager
    @Environment(\.dismiss) private var dismiss

    // MARK: - Expiration constants

    private static let expirationLabels = ["1 day", "10 days", "30 days"]
    private static let expirationOffsets: [Int32] = [17_280, 172_800, 518_400]

    // MARK: - Form state

    @State private var spender = ""
    @State private var amount = ""
    @State private var selectedExpirationIndex = 0

    // MARK: - Operation state

    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var txHash: String? = nil
    @State private var currentAllowance: String? = nil
    @State private var allowanceFetched = false
    /// Snapshot of the spender captured at submit time for the success card.
    @State private var submittedSpender = ""
    /// Snapshot of the amount captured at submit time for the success card.
    @State private var submittedAmount = ""

    // MARK: - Signer state

    @State private var availableSigners: [SignerInfoBridge] = []
    @State private var signersLoaded = false

    // MARK: - Signer picker

    @State private var showSignerPicker = false

    // MARK: - Init

    init(toastManager: ToastManager) {
        self.toastManager = toastManager
    }

    // MARK: - Derived state

    private var spenderError: String? {
        guard !spender.isEmpty else { return nil }
        return FormValidation.validateRecipient(spender)
    }

    private var amountError: String? {
        guard !amount.isEmpty else { return nil }
        return FormValidation.validateAmount(amount)
    }

    private var isFormValid: Bool {
        !spender.isEmpty &&
        !amount.isEmpty &&
        spenderError == nil &&
        amountError == nil &&
        appState.demoTokenContractId != nil
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
                    tokenContractCard
                    spenderField
                    amountField
                    expirationPicker
                    if let error = errorMessage {
                        errorCard(message: error)
                    }
                    if txHash == nil {
                        approveButton
                    }
                    if txHash != nil {
                        successCard
                        newApproveButton
                        goToMainButton
                    }
                    Spacer().frame(height: 16)
                }
            }
            .padding(16)
        }
        .background(Material3Colors.background)
        .navigationToolbar(title: "Approve")
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
                    performMultiSignerApprove(
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
        InfoCard(title: "Token Allowance", color: .variant) {
            Text(
                "Approve a token spending allowance for another address. " +
                "The spender can transfer up to the approved amount from your " +
                "smart account until the allowance expires."
            )
            .font(.system(size: 13))
            .foregroundStyle(Material3Colors.onSurfaceVariant)
        }
    }

    // MARK: - Balance card

    private var balanceCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("DEMO Balance")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Material3Colors.onPrimaryContainer)

            Text("\(appState.demoTokenBalance ?? "0.0") DEMO")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Material3Colors.onPrimaryContainer)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.primaryContainer)
        .cornerRadius(8)
    }

    // MARK: - Token contract card

    private var tokenContractCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Token Contract")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Text(appState.demoTokenContractId != nil
                ? "DEMO (\(appState.demoTokenContractId!))"
                : "DEMO token not deployed"
            )
            .font(.system(.callout, design: .monospaced))
            .foregroundStyle(Material3Colors.onSurfaceVariant)
            .lineLimit(2)
            .truncationMode(.middle)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.surfaceVariant)
        .cornerRadius(8)
    }

    // MARK: - Spender field

    private var spenderField: some View {
        ValidationTextField(
            label: "Spender Address",
            text: $spender,
            error: spenderError,
            placeholder: "G... or C...",
            isMonospace: true,
            isEnabled: !isLoading && txHash == nil
        )
        .onChange(of: spender) { _ in errorMessage = nil }
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

    // MARK: - Expiration picker

    private var expirationPicker: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Expiration")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Material3Colors.onSurfaceVariant)

            Picker("", selection: $selectedExpirationIndex) {
                ForEach(0..<ApproveScreen.expirationLabels.count, id: \.self) { index in
                    Text(ApproveScreen.expirationLabels[index]).tag(index)
                }
            }
            .pickerStyle(.segmented)
            .disabled(isLoading || txHash != nil)
            .opacity((isLoading || txHash != nil) ? 0.5 : 1.0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
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

    // MARK: - Approve button

    private var approveButton: some View {
        LoadingButton(
            action: handleApproveTap,
            isLoading: isLoading,
            isEnabled: isFormValid && !isLoading && bridgeWrapper.isKitInitialized,
            icon: "checkmark.seal",
            text: "Approve",
            loadingText: "Approving...",
            style: .filled
        )
    }

    // MARK: - Success card

    private var successCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Approve Successful")
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
                Text("Amount Approved")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundStyle(Material3Colors.onPrimaryContainer)

                Text("\(submittedAmount) DEMO")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("Spender")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundStyle(Material3Colors.onPrimaryContainer)

                Text(submittedSpender)
                    .font(.system(.callout, design: .monospaced))
                    .foregroundStyle(Material3Colors.onPrimaryContainer)
                    .lineLimit(2)
                    .truncationMode(.middle)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("Current Allowance")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundStyle(Material3Colors.onPrimaryContainer)

                Text({
                    if let allowance = currentAllowance {
                        return "\(allowance) DEMO"
                    } else if allowanceFetched {
                        return "Unable to fetch"
                    } else {
                        return "Loading..."
                    }
                }())
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(Material3Colors.onPrimaryContainer)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.primaryContainer)
        .cornerRadius(8)
    }

    // MARK: - Post-success buttons

    private var newApproveButton: some View {
        LoadingButton(
            action: resetForm,
            isLoading: false,
            isEnabled: true,
            text: "New Approve",
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

    /// Called when the Approve button is tapped.
    ///
    /// Routes to the single-signer path when there is at most one signer (or signers have not
    /// yet been loaded), otherwise opens the signer picker sheet.
    private func handleApproveTap() {
        guard !isLoading, isFormValid else { return }

        if !signersLoaded || availableSigners.count <= 1 {
            performSingleSignerApprove()
        } else {
            showSignerPicker = true
        }
    }

    /// Executes a simple passkey-authenticated approve.
    private func performSingleSignerApprove() {
        let tokenContract = resolveTokenContract()
        let capturedSpender = spender
        let capturedAmount = amount
        let capturedOffset = ApproveScreen.expirationOffsets[selectedExpirationIndex]

        isLoading = true
        errorMessage = nil

        Task {
            do {
                let result = try await bridgeWrapper.bridge.approveAllowance(
                    tokenContract: tokenContract,
                    spenderAddress: capturedSpender,
                    amount: capturedAmount,
                    expirationLedgerOffset: capturedOffset
                )

                await MainActor.run {
                    if result.success {
                        submittedSpender = capturedSpender
                        submittedAmount = capturedAmount
                        txHash = result.hash
                        appState.sync(from: bridgeWrapper.bridge)
                        fetchCurrentAllowance(
                            tokenContract: tokenContract,
                            spenderAddress: capturedSpender
                        )
                    } else {
                        handleApproveError(result.error ?? "Approve failed")
                    }
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    handleApproveError(error.localizedDescription)
                    isLoading = false
                }
            }
        }
    }

    /// Called by the signer picker sheet on confirmation.
    ///
    /// Determines whether to use the single-passkey path (only the connected passkey was
    /// chosen) or the multi-signer path.
    private func performMultiSignerApprove(
        selected: [SignerInfoBridge],
        secretKeys: [String: String],
        ed25519Secrets: [String: String] = [:]
    ) {
        let tokenContract = resolveTokenContract()
        let capturedSpender = spender
        let capturedAmount = amount
        let capturedOffset = ApproveScreen.expirationOffsets[selectedExpirationIndex]

        isLoading = true
        errorMessage = nil

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
                let result: ApproveResult

                if isSingleOwnPasskey {
                    result = try await bridgeWrapper.bridge.approveAllowance(
                        tokenContract: tokenContract,
                        spenderAddress: capturedSpender,
                        amount: capturedAmount,
                        expirationLedgerOffset: capturedOffset
                    )
                } else {
                    result = try await bridgeWrapper.bridge.multiSignerApproveAllowance(
                        tokenContract: tokenContract,
                        spenderAddress: capturedSpender,
                        amount: capturedAmount,
                        expirationLedgerOffset: capturedOffset,
                        signerDescriptors: descriptors,
                        delegatedSecretKeys: secretKeys,
                        ed25519SecretKeys: ed25519Secrets
                    )
                }

                await MainActor.run {
                    if result.success {
                        submittedSpender = capturedSpender
                        submittedAmount = capturedAmount
                        txHash = result.hash
                        appState.sync(from: bridgeWrapper.bridge)
                        fetchCurrentAllowance(
                            tokenContract: tokenContract,
                            spenderAddress: capturedSpender
                        )
                    } else {
                        handleApproveError(result.error ?? "Approve failed")
                    }
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    handleApproveError(error.localizedDescription)
                    isLoading = false
                }
            }
        }
    }

    /// Fetches the current allowance after a successful approve and updates the UI.
    private func fetchCurrentAllowance(tokenContract: String, spenderAddress: String) {
        Task {
            let allowance = try? await bridgeWrapper.bridge.fetchAllowance(
                tokenContract: tokenContract,
                spenderAddress: spenderAddress
            )
            await MainActor.run {
                currentAllowance = allowance
                allowanceFetched = true
            }
        }
    }

    /// Interprets the error message and sets `errorMessage` accordingly.
    ///
    /// User cancellation (Touch ID dismissed) is shown as a distinct informational message
    /// rather than a generic failure string.
    private func handleApproveError(_ message: String) {
        if bridgeWrapper.bridge.isUserCancellation(message: message) {
            errorMessage = "Passkey authentication cancelled"
        } else {
            errorMessage = "Approve failed: \(message)"
        }
    }

    /// Resets all form and result state for a new approval.
    private func resetForm() {
        spender = ""
        amount = ""
        txHash = nil
        errorMessage = nil
        currentAllowance = nil
        allowanceFetched = false
        selectedExpirationIndex = 0
        submittedSpender = ""
        submittedAmount = ""
    }

    /// Returns the DEMO token contract address.
    private func resolveTokenContract() -> String {
        return appState.demoTokenContractId ?? ""
    }
}
