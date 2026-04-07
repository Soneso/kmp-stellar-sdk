//
//  ContextRuleBuilderScreen.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

// MARK: - ContextRuleBuilderScreen

/// Screen for creating or editing a context rule on a connected smart account.
///
/// Compose equivalent: `ContextRuleBuilderScreen.kt`.
///
/// Supports two modes:
/// - **Create mode** (`editRuleId == nil`): blank form with signers and policies.
/// - **Edit mode** (`editRuleId != nil`): pre-populated form; all sections (name, expiry,
///   signers, policies) can be modified. Uses the edit flow orchestrator for multi-operation
///   submission with progress tracking and auth guard handling.
///
/// Business logic lives in `ContextRuleBuilderViewModel`. The screen composes three
/// sub-views -- `RuleConfigSection`, `SignerManagementSection`, `PolicyManagementSection` --
/// and handles the submission result display.
struct ContextRuleBuilderScreen: View {

    // MARK: - Environment

    @EnvironmentObject var bridgeWrapper: MacOSBridgeWrapper
    @EnvironmentObject var appState: AppState
    @ObservedObject var toastManager: ToastManager
    @Environment(\.dismiss) private var dismiss

    // MARK: - ViewModel

    @StateObject private var viewModel: ContextRuleBuilderViewModel

    // MARK: - Init

    /// Called after a rule is successfully created or updated.
    var onRuleChanged: (() -> Void)?

    init(editRuleId: Int32? = nil, toastManager: ToastManager, onRuleChanged: (() -> Void)? = nil) {
        self._viewModel = StateObject(wrappedValue: ContextRuleBuilderViewModel(editRuleId: editRuleId))
        self.toastManager = toastManager
        self.onRuleChanged = onRuleChanged
    }

    // MARK: - Body

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if !appState.isConnected {
                    notConnectedCard
                }

                if viewModel.isLoadingRule {
                    loadingRuleIndicator
                }

                if let msg = viewModel.errorMessage {
                    errorCard(message: msg)
                }

                // Edit mode result card.
                if viewModel.isEditing, let result = viewModel.editResult {
                    editResultCard(result)
                }

                // Edit mode progress card.
                if let progress = viewModel.submissionProgress, progress.isActive {
                    editProgressCard(progress)
                }

                // Create mode result card.
                if !viewModel.isEditing && viewModel.hasSubmitted {
                    submissionResultCard
                }

                if appState.isConnected && !viewModel.isLoadingRule && !isFormHidden {
                    formContent
                }

                Spacer(minLength: 24)
            }
            .padding(16)
        }
        .background(Material3Colors.background)
        .frame(minWidth: 580, minHeight: 700)
        .navigationToolbar(title: viewModel.isEditing ? "Edit Context Rule" : "Add Context Rule")
        .task {
            let bridge = bridgeWrapper.bridge
            viewModel.loadConfig(bridge: bridge)
            viewModel.updateDemoTokenContractId(appState.demoTokenContractId)
            if viewModel.isEditing {
                await viewModel.loadRuleForEdit(bridge: bridge)
            } else {
                // Load available signers for create mode multi-signer authorization
                do {
                    let signerInfos = try await bridge.loadAvailableSigners()
                    let loaded = KotlinInterop.toArray(signerInfos, as: SignerInfoBridge.self)
                    viewModel.availableSignersForPicker = loaded
                    viewModel.signersForPickerLoaded = true
                } catch {
                    viewModel.signersForPickerLoaded = true
                }
            }
        }
        .sheet(isPresented: $viewModel.showSignerPicker) {
            SignerPickerSheet(
                signers: viewModel.availableSignersForPicker,
                activeCredentialId: appState.credentialId,
                onConfirm: { selected, secretKeys in
                    viewModel.showSignerPicker = false
                    let descriptors = selected.map { SignerDescriptor(type: $0.type, value: $0.identifier) }
                    Task {
                        await viewModel.submitEditWithSigners(
                            bridge: bridgeWrapper.bridge,
                            selectedSigners: selected,
                            delegatedSecretKeys: secretKeys
                        )
                        appState.sync(from: bridgeWrapper.bridge)
                    }
                },
                onDismiss: {
                    viewModel.showSignerPicker = false
                }
            )
        }
        .sheet(isPresented: $viewModel.showCreateSignerPicker) {
            SignerPickerSheet(
                signers: viewModel.availableSignersForPicker,
                activeCredentialId: appState.credentialId,
                onConfirm: { selected, secretKeys in
                    viewModel.showCreateSignerPicker = false
                    Task {
                        await viewModel.submitCreateWithSigners(
                            bridge: bridgeWrapper.bridge,
                            selectedSigners: selected,
                            delegatedSecretKeys: secretKeys
                        )
                        appState.sync(from: bridgeWrapper.bridge)
                    }
                },
                onDismiss: {
                    viewModel.showCreateSignerPicker = false
                }
            )
        }
    }

    // MARK: - Computed

    /// True when the main form should be hidden (success state).
    private var isFormHidden: Bool {
        if viewModel.isEditing {
            if let result = viewModel.editResult,
               result.success && !result.partialDueToAuthGuard {
                return true
            }
            return false
        } else {
            return viewModel.submissionSuccess
        }
    }

    // MARK: - Not Connected Card

    private var notConnectedCard: some View {
        InfoCard(color: .variant) {
            VStack(spacing: 8) {
                Text("No wallet connected")
                    .font(.body)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                Text("Connect a wallet to create or edit context rules.")
                    .font(.callout)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
    }

    // MARK: - Loading Indicator (Edit Mode)

    private var loadingRuleIndicator: some View {
        VStack(spacing: 12) {
            ProgressView()
                .controlSize(.regular)
                .tint(Material3Colors.primary)
            Text("Loading rule #\(viewModel.editRuleId.map { "\($0)" } ?? "")...")
                .font(.callout)
                .foregroundColor(Material3Colors.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
    }

    // MARK: - Error Card

    private func errorCard(message: String) -> some View {
        InfoCard(color: .error) {
            Text(message)
                .font(.callout)
                .foregroundColor(Material3Colors.onErrorContainer)
                .textSelection(.enabled)
        }
    }

    // MARK: - Edit Mode Result Card

    @ViewBuilder
    private func editResultCard(_ result: EditResult) -> some View {
        let bgColor: Color = {
            if result.success && !result.partialDueToAuthGuard {
                return Color(red: 0.298, green: 0.686, blue: 0.314).opacity(0.12) // green
            } else if result.partialDueToAuthGuard {
                return Color(red: 0.129, green: 0.588, blue: 0.953).opacity(0.12) // blue
            } else {
                return Material3Colors.errorContainer
            }
        }()

        let titleColor: Color = {
            if result.success && !result.partialDueToAuthGuard {
                return Color(red: 0.180, green: 0.490, blue: 0.196)
            } else if result.partialDueToAuthGuard {
                return Color(red: 0.082, green: 0.396, blue: 0.753)
            } else {
                return Material3Colors.onErrorContainer
            }
        }()

        VStack(alignment: .leading, spacing: 8) {
            Text(result.success && !result.partialDueToAuthGuard
                 ? "All Changes Applied"
                 : result.partialDueToAuthGuard
                 ? "Partial Update"
                 : "Update Failed")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(titleColor)

            Text("\(result.completedOperations) of \(result.totalOperations) operation(s) completed")
                .font(.callout)
                .foregroundColor(Material3Colors.onSurfaceVariant)

            let hashes = result.transactionHashes
            if !hashes.isEmpty {
                Text("Transaction Hashes")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Material3Colors.onSurfaceVariant)

                ForEach(hashes, id: \.self) { hash in
                    HStack {
                        Text(hash)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundColor(Material3Colors.onSurfaceVariant)
                            .lineLimit(1)
                            .truncationMode(.middle)

                        Button {
                            ClipboardHelper.copy(hash)
                            toastManager.show("Hash copied")
                        } label: {
                            Text("Copy")
                                .font(.caption)
                                .foregroundColor(Material3Colors.primary)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            if let authMsg = result.authGuardMessage {
                Text(authMsg)
                    .font(.callout)
                    .foregroundColor(Color(red: 0.082, green: 0.396, blue: 0.753))
            }

            if let error = result.error {
                Text(error)
                    .font(.callout)
                    .foregroundColor(Material3Colors.onErrorContainer)
                    .textSelection(.enabled)
            }

            if let failedStep = result.failedStep {
                Text("Failed at: \(failedStep)")
                    .font(.callout)
                    .foregroundColor(Material3Colors.onErrorContainer)
            }

            if result.success && !result.partialDueToAuthGuard {
                LoadingButton(
                    action: {
                        appState.sync(from: bridgeWrapper.bridge)
                        onRuleChanged?()
                        dismiss()
                    },
                    isLoading: false,
                    isEnabled: true,
                    text: "Done",
                    loadingText: "Done",
                    style: .filled
                )
            }
        }
        .padding(16)
        .background(bgColor)
        .cornerRadius(8)
    }

    // MARK: - Edit Mode Progress Card

    private func editProgressCard(_ progress: SubmissionProgress) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(progress.currentStep)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(Material3Colors.onSurface)

            ProgressView(
                value: progress.totalSteps > 0
                    ? Double(progress.completedSteps) / Double(progress.totalSteps)
                    : 0
            )
            .tint(Material3Colors.primary)

            Text("Step \(progress.completedSteps + 1) of \(progress.totalSteps)")
                .font(.callout)
                .foregroundColor(Material3Colors.onSurfaceVariant)

            ForEach(progress.infoMessages, id: \.self) { msg in
                Text(msg)
                    .font(.callout)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }
        }
        .padding(16)
        .background(Color(red: 0.129, green: 0.588, blue: 0.953).opacity(0.08))
        .cornerRadius(8)
    }

    // MARK: - Create Mode Submission Result Card

    @ViewBuilder
    private var submissionResultCard: some View {
        if viewModel.submissionSuccess {
            successCard
        } else if viewModel.hasSubmitted {
            if let err = viewModel.submissionError {
                InfoCard(color: .error) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Transaction Failed")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Material3Colors.onErrorContainer)
                        Text(err)
                            .font(.callout)
                            .foregroundColor(Material3Colors.onErrorContainer)
                            .textSelection(.enabled)
                    }
                }
            }
        }
    }

    private var successCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Transaction Successful")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(Material3Colors.badgePoliciesText)

            if let hash = viewModel.submissionTxHash {
                CopyableField(
                    label: "Transaction Hash",
                    value: hash,
                    textColor: Material3Colors.badgePoliciesText,
                    labelColor: Material3Colors.badgePoliciesText.opacity(0.8),
                    monospace: true,
                    onCopy: { toastManager.show("Hash copied to clipboard") }
                )
            }

            LoadingButton(
                action: {
                    appState.sync(from: bridgeWrapper.bridge)
                    onRuleChanged?()
                    dismiss()
                },
                isLoading: false,
                isEnabled: true,
                text: "Done",
                loadingText: "Done",
                style: .filled
            )
        }
        .padding(16)
        .background(Material3Colors.logSuccess.opacity(0.12))
        .cornerRadius(8)
    }

    // MARK: - Main Form Content

    private var formContent: some View {
        VStack(spacing: 16) {
            // Rule Configuration
            RuleConfigSection(viewModel: viewModel)

            Divider()
                .background(Material3Colors.outline.opacity(0.3))
                .padding(.vertical, 4)

            // Signers -- shown in both create and edit mode.
            SignerManagementSection(viewModel: viewModel, bridge: bridgeWrapper.bridge)

            Divider()
                .background(Material3Colors.outline.opacity(0.3))
                .padding(.vertical, 4)

            // Policies -- shown in both create and edit mode.
            PolicyManagementSection(viewModel: viewModel)

            Divider()
                .background(Material3Colors.outline.opacity(0.3))
                .padding(.vertical, 4)

            // Edit mode: Operation summary.
            if viewModel.isEditing {
                editOperationSummary
            }

            submissionSection
        }
    }

    // MARK: - Edit Operation Summary

    @ViewBuilder
    private var editOperationSummary: some View {
        if let diff = viewModel.computeEditDiff() {
            let summaryText = viewModel.buildOperationSummary(diff)
            let isEmpty = diff.isEmpty

            VStack(alignment: .leading) {
                Text(summaryText)
                    .font(.callout)
                    .foregroundColor(isEmpty
                        ? Material3Colors.onSurfaceVariant
                        : Color(red: 0.082, green: 0.396, blue: 0.753))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isEmpty
                ? Material3Colors.surfaceVariant
                : Color(red: 0.129, green: 0.588, blue: 0.953).opacity(0.08))
            .cornerRadius(8)
        }
    }

    // MARK: - Submission Section

    @ViewBuilder
    private var submissionSection: some View {
        VStack(spacing: 8) {
            if viewModel.isSubmitting && !viewModel.isEditing {
                HStack(spacing: 10) {
                    ProgressView()
                        .controlSize(.small)
                        .tint(Material3Colors.primary)
                    Text("Transaction in progress...")
                        .font(.caption)
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            LoadingButton(
                action: {
                    if viewModel.isEditing {
                        // For edit mode, check if diff is empty first.
                        let diff = viewModel.computeEditDiff()
                        if diff == nil || diff!.isEmpty {
                            viewModel.errorMessage = "No changes to apply"
                            return
                        }
                    }
                    Task { await viewModel.submit(bridge: bridgeWrapper.bridge) }
                },
                isLoading: viewModel.isSubmitting,
                isEnabled: isSubmitEnabled,
                icon: viewModel.isEditing ? "pencil" : "plus.circle",
                text: viewModel.isEditing ? "Update Context Rule" : "Create Context Rule",
                loadingText: "Submitting...",
                style: .filled
            )
        }
    }

    /// Determines whether the submit button is enabled.
    private var isSubmitEnabled: Bool {
        guard appState.isConnected && !viewModel.isSubmitting &&
              !viewModel.ruleName.trimmingCharacters(in: .whitespaces).isEmpty else {
            return false
        }
        if viewModel.submissionProgress?.isActive == true {
            return false
        }
        if viewModel.isEditing {
            let diff = viewModel.computeEditDiff()
            let diffNotEmpty = diff != nil && !diff!.isEmpty
            let notFullSuccess = viewModel.editResult == nil ||
                !viewModel.editResult!.success ||
                viewModel.editResult!.partialDueToAuthGuard
            return diffNotEmpty && notFullSuccess
        } else {
            return !viewModel.signers.isEmpty && !viewModel.submissionSuccess
        }
    }
}
