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
/// - **Edit mode** (`editRuleId != nil`): pre-populated form; only name and expiry
///   can be updated (signer and policy changes require separate SDK calls).
///
/// Business logic lives in `ContextRuleBuilderViewModel`. The screen composes three
/// sub-views — `RuleConfigSection`, `SignerManagementSection`, `PolicyManagementSection` —
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

                if viewModel.hasSubmitted {
                    submissionResultCard
                }

                if appState.isConnected && !viewModel.isLoadingRule &&
                   viewModel.submissionSuccess == false {
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
            }
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

    // MARK: - Submission Result Card

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

            // Signers & Policies only in create mode.
            if !viewModel.isEditing {
                SignerManagementSection(viewModel: viewModel, bridge: bridgeWrapper.bridge)

                Divider()
                    .background(Material3Colors.outline.opacity(0.3))
                    .padding(.vertical, 4)

                PolicyManagementSection(viewModel: viewModel)

                Divider()
                    .background(Material3Colors.outline.opacity(0.3))
                    .padding(.vertical, 4)
            }

            submissionSection
        }
    }

    // MARK: - Submission Section

    @ViewBuilder
    private var submissionSection: some View {
        VStack(spacing: 8) {
            if viewModel.isSubmitting {
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
                    Task { await viewModel.submit(bridge: bridgeWrapper.bridge) }
                },
                isLoading: viewModel.isSubmitting,
                isEnabled: appState.isConnected && !viewModel.isSubmitting &&
                           !viewModel.ruleName.trimmingCharacters(in: .whitespaces).isEmpty &&
                           (viewModel.isEditing || !viewModel.signers.isEmpty),
                icon: viewModel.isEditing ? "pencil" : "plus.circle",
                text: viewModel.isEditing ? "Update Context Rule" : "Create Context Rule",
                loadingText: "Submitting...",
                style: .filled
            )
        }
    }
}
