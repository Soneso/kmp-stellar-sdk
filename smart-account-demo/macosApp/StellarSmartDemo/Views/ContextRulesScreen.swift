//
//  ContextRulesScreen.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

// MARK: - ContextRulesScreen

/// Lists all on-chain authorization rules for the connected smart account.
///
/// Mirrors the Compose `ContextRulesScreen` exactly. Each rule card shows the rule ID,
/// name, context type, and summary badges for signers, policies, and optional expiry.
/// Expanding a card reveals the full signer and policy lists plus edit and remove actions.
///
/// Rule loading and removal delegate to `MacOSBridge.loadContextRules()` and
/// `MacOSBridge.removeContextRule(ruleId:)`. Removing the last rule is blocked by
/// disabling the button and showing "Last Rule" text instead.
struct ContextRulesScreen: View {

    // MARK: - Environment

    @EnvironmentObject var bridgeWrapper: MacOSBridgeWrapper
    @EnvironmentObject var appState: AppState
    @ObservedObject var toastManager: ToastManager

    // MARK: - Rule list state

    @State private var rules: [ParsedContextRule] = []
    @State private var isLoading = false
    @State private var errorMessage: String? = nil

    // MARK: - UI state

    /// ID of the currently expanded rule card; `nil` if all cards are collapsed.
    @State private var expandedRuleId: UInt32? = nil
    /// Rule pending removal confirmation. Non-nil triggers the confirmation dialog.
    @State private var ruleToRemove: ParsedContextRule? = nil
    @State private var isRemoving = false

    // Multi-signer state for rule removal
    @State private var availableSigners: [SignerInfoBridge] = []
    @State private var signersLoaded = false
    /// True when the signer fetch failed; distinguishes load-failed from loaded-empty
    /// so the UI can warn that multi-signer routing is unavailable.
    @State private var signerLoadFailed = false
    @State private var showRemoveSignerPicker = false
    @State private var ruleToRemoveWithSigners: ParsedContextRule? = nil

    // MARK: - Init

    init(toastManager: ToastManager) {
        self.toastManager = toastManager
    }

    // MARK: - Body

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                descriptionCard
                actionButtonsRow
                if signerLoadFailed {
                    signerLoadWarning
                }
                if let message = errorMessage {
                    ErrorCard(message: message, font: .callout)
                }
                if !appState.isConnected {
                    notConnectedCard
                }
                if isLoading && rules.isEmpty {
                    loadingIndicator
                }
                if isRemoving {
                    removingIndicator
                }
                if !isLoading && rules.isEmpty && appState.isConnected && errorMessage == nil {
                    emptyStateCard
                }
                ForEach(Array(rules.enumerated()), id: \.offset) { _, rule in
                    contextRuleCard(rule: rule)
                }
                if !rules.isEmpty {
                    Text("\(rules.count) context rule(s) loaded")
                        .font(.caption)
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                Spacer(minLength: 16)
            }
            .padding(16)
        }
        .background(Material3Colors.background)
        .frame(minWidth: 550, minHeight: 700)
        .navigationToolbar(title: "Context Rules")
        .task {
            await loadRules()
            await loadSigners()
        }
        .confirmationDialog(
            removeDialogTitle,
            isPresented: Binding(
                get: { ruleToRemove != nil },
                set: { if !$0 { ruleToRemove = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Remove", role: .destructive) {
                if let rule = ruleToRemove {
                    ruleToRemove = nil
                    Task { await performRemove(rule: rule) }
                }
            }
            Button("Cancel", role: .cancel) {
                ruleToRemove = nil
            }
        } message: {
            if let rule = ruleToRemove {
                Text(
                    "Remove rule #\(rule.id) \"\(rule.name)\"? " +
                    "This action requires smart account authorization and cannot be undone."
                )
            }
        }
        .sheet(isPresented: $showRemoveSignerPicker) {
            if let rule = ruleToRemoveWithSigners {
                SignerPickerSheet(
                    signers: availableSigners,
                    activeCredentialId: appState.credentialId,
                    description: "Choose which signers co-authorize removing this context rule. " +
                        "For Stellar account signers, enter the secret key to enable signing.",
                    onConfirm: { selected, secretKeys, ed25519Secrets in
                        showRemoveSignerPicker = false
                        // Auth signers are existing on-chain signers, so isPending is always false here.
                        let signerDescs = selected.map { SignerDescriptor(type: $0.type, value: $0.identifier, isPending: false) }
                        let capturedRule = rule
                        ruleToRemoveWithSigners = nil
                        Task {
                            await performRemoveWithSigners(
                                rule: capturedRule,
                                signerDescs: signerDescs,
                                secretKeys: secretKeys,
                                ed25519Secrets: ed25519Secrets
                            )
                        }
                    },
                    onDismiss: {
                        showRemoveSignerPicker = false
                        ruleToRemoveWithSigners = nil
                    },
                    bridge: bridgeWrapper.bridge
                )
            }
        }
    }

    // MARK: - Description card

    private var descriptionCard: some View {
        InfoCard(color: .variant) {
            VStack(alignment: .leading, spacing: 8) {
                Text("On-Chain Authorization Rules")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                Text(
                    "Context rules define who can authorize what operations on this smart account. " +
                    "Each rule specifies signers and policies that control access for a given context type."
                )
                .font(.callout)
                .foregroundColor(Material3Colors.onSurfaceVariant)
            }
        }
    }

    // MARK: - Action buttons row

    private var actionButtonsRow: some View {
        HStack(spacing: 8) {
            // Refresh — outlined
            Button(action: { Task { await loadRules() } }) {
                PrimaryButtonLabel(
                    text: isLoading ? "Loading..." : "Refresh",
                    style: .outlined(color: Material3Colors.primary)
                )
            }
            .buttonStyle(.plain)
            .disabled(isLoading || isRemoving || !appState.isConnected)
            .opacity((isLoading || isRemoving || !appState.isConnected) ? 0.5 : 1.0)

            // Add Rule — filled
            NavigationLink(
                destination: ContextRuleBuilderScreen(
                    toastManager: toastManager,
                    onRuleChanged: { Task { await loadRules() } }
                )
            ) {
                PrimaryButtonLabel(
                    text: "+ Add Rule",
                    style: .filled(
                        foreground: .white,
                        background: (isLoading || isRemoving || !appState.isConnected)
                            ? Material3Colors.primary.opacity(0.5)
                            : Material3Colors.primary
                    )
                )
            }
            .buttonStyle(.plain)
            .disabled(isLoading || isRemoving || !appState.isConnected)
        }
    }

    // MARK: - Signer load warning

    /// Non-blocking notice that the signer list could not be loaded. Single-signer
    /// rule removal keeps working; only the multi-signer picker is unavailable.
    private var signerLoadWarning: some View {
        Text("Could not load signers — multi-signer operations unavailable")
            .font(.footnote)
            .foregroundColor(Material3Colors.badgeExpiryText)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Not connected card

    private var notConnectedCard: some View {
        InfoCard(color: .variant) {
            VStack(spacing: 8) {
                Text("No wallet connected")
                    .font(.body)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                Text("Connect a wallet to view context rules.")
                    .font(.callout)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
    }

    // MARK: - Loading indicator

    private var loadingIndicator: some View {
        VStack(spacing: 12) {
            ProgressView()
                .controlSize(.regular)
                .tint(Material3Colors.primary)
            Text("Loading context rules...")
                .font(.callout)
                .foregroundColor(Material3Colors.onSurfaceVariant)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
    }

    // MARK: - Removing indicator

    private var removingIndicator: some View {
        InfoCard(color: .secondary) {
            HStack(spacing: 12) {
                ProgressView()
                    .controlSize(.small)
                    .tint(Material3Colors.secondary)
                Text("Removing context rule (requires authorization)...")
                    .font(.callout)
                    .foregroundColor(Material3Colors.onSecondaryContainer)
            }
        }
    }

    // MARK: - Empty state card

    private var emptyStateCard: some View {
        InfoCard(color: .variant) {
            VStack(spacing: 8) {
                Text("No context rules found")
                    .font(.body)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                Text(
                    "This wallet may use a default configuration, or rules may not have been created yet."
                )
                .font(.callout)
                .foregroundColor(Material3Colors.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
    }

    // MARK: - Rule card

    @ViewBuilder
    private func contextRuleCard(rule: ParsedContextRule) -> some View {
        let ruleId: UInt32 = rule.id
        let isExpanded = expandedRuleId == ruleId
        let canRemove = rules.count > 1
        let signers = KotlinInterop.toArray(rule.signers, as: SmartAccountSigner.self)
        let policies = KotlinInterop.toArray(rule.policies, as: NSString.self).map { $0 as String }
        let contextTypeStr = contextTypeDisplayString(for: rule)
        let validUntil: KotlinUInt? = rule.validUntil

        VStack(spacing: 0) {
            // Header — always visible, tappable to expand/collapse
            Button(action: {
                withAnimation(.easeInOut(duration: 0.2)) {
                    expandedRuleId = isExpanded ? nil : ruleId
                }
            }) {
                VStack(alignment: .leading, spacing: 8) {
                    // Top row: ID badge + name + chevron
                    HStack(spacing: 8) {
                        // Rule ID badge
                        TagPill(
                            text: "#\(ruleId)",
                            font: .caption2.bold(),
                            background: Material3Colors.primary
                        )

                        // Rule name
                        Text(rule.name.isEmpty ? "Unnamed Rule" : rule.name)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(Material3Colors.onSurface)
                            .lineLimit(1)
                            .truncationMode(.tail)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        // Expand / collapse chevron
                        Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Material3Colors.onSurfaceVariant)
                    }

                    // Context type
                    Text(contextTypeStr)
                        .font(.caption)
                        .foregroundColor(Material3Colors.onSurfaceVariant)

                    // Summary badges
                    HStack(spacing: 8) {
                        // Signers badge
                        let signerCount = signers.count
                        TagPill(
                            text: "\(signerCount) signer\(signerCount != 1 ? "s" : "")",
                            foreground: Material3Colors.badgeSignersText,
                            background: Material3Colors.badgeSignersBackground
                        )

                        // Policies badge
                        let policyCount = policies.count
                        TagPill(
                            text: "\(policyCount) polic\(policyCount != 1 ? "ies" : "y")",
                            foreground: Material3Colors.badgePoliciesText,
                            background: Material3Colors.badgePoliciesBackground
                        )

                        // Expiry badge (only when validUntil is set)
                        if let expiry = validUntil {
                            TagPill(
                                text: "Expires: ledger \(expiry.uint32Value)",
                                foreground: Material3Colors.badgeExpiryText,
                                background: Material3Colors.badgeExpiryBackground
                            )
                        }
                    }
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            // Expanded details
            if isExpanded {
                VStack(spacing: 0) {
                    Divider()
                        .background(Material3Colors.outline.opacity(0.3))

                    VStack(alignment: .leading, spacing: 16) {
                        // Signers section
                        signersSection(signers: signers)

                        // Policies section
                        policiesSection(policies: policies)

                        // Action buttons
                        HStack(spacing: 8) {
                            // Edit Rule — outlined
                            NavigationLink(
                                destination: ContextRuleBuilderScreen(
                                    editRuleId: Int32(bitPattern: rule.id),
                                    toastManager: toastManager,
                                    onRuleChanged: { Task { await loadRules() } }
                                )
                            ) {
                                PrimaryButtonLabel(
                                    text: "Edit Rule",
                                    style: .outlined(
                                        color: isRemoving
                                            ? Material3Colors.primary.opacity(0.5)
                                            : Material3Colors.primary
                                    )
                                )
                            }
                            .buttonStyle(.plain)
                            .disabled(isRemoving)

                            // Remove Rule — error filled (or disabled "Last Rule")
                            Button(action: {
                                if canRemove && !isRemoving {
                                    ruleToRemove = rule
                                }
                            }) {
                                PrimaryButtonLabel(
                                    text: canRemove ? "Remove Rule" : "Last Rule",
                                    style: .filled(
                                        foreground: .white,
                                        background: (!canRemove || isRemoving)
                                            ? Material3Colors.error.opacity(0.5)
                                            : Material3Colors.error
                                    )
                                )
                            }
                            .buttonStyle(.plain)
                            .disabled(!canRemove || isRemoving)
                        }
                    }
                    .padding(16)
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .frame(maxWidth: .infinity)
        .background(Material3Colors.surface)
        .cornerRadius(8)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }

    // MARK: - Signers section

    @ViewBuilder
    private func signersSection(signers: [SmartAccountSigner]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Signers")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(Material3Colors.onSurface)

            if signers.isEmpty {
                Text("No signers (policy-only rule)")
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            } else {
                ForEach(Array(signers.enumerated()), id: \.offset) { _, signer in
                    signerChip(signer: signer)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Signer chip

    @ViewBuilder
    private func signerChip(signer: SmartAccountSigner) -> some View {
        let typeInfo = signerTypeInfo(signer: signer)

        HStack(spacing: 8) {
            SignerBadge(signerType: typeInfo.type)

            Text(typeInfo.identifier)
                .font(.system(size: 12, design: .monospaced))
                .foregroundColor(Material3Colors.onSurface)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(signerChipBackground(type: typeInfo.type))
        .cornerRadius(6)
    }

    // MARK: - Policies section

    @ViewBuilder
    private func policiesSection(policies: [String]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Policies")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(Material3Colors.onSurface)

            if policies.isEmpty {
                Text("No policies (signer-only rule)")
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            } else {
                ForEach(Array(policies.enumerated()), id: \.offset) { _, policyAddress in
                    policyChip(address: policyAddress)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Policy chip

    private func policyChip(address: String) -> some View {
        let truncated = KotlinInterop.truncateAddress(address, prefixLength: 8, suffixLength: 8)

        return HStack(spacing: 8) {
            TagPill(
                text: "P",
                font: .caption2.bold(),
                background: Material3Colors.logSuccess
            )

            Text(truncated)
                .font(.system(size: 12, design: .monospaced))
                .foregroundColor(Material3Colors.onSurface)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(Material3Colors.logSuccess.opacity(0.08))
        .cornerRadius(6)
    }

    // MARK: - Helpers

    /// Extracts a signer type tag and display identifier from a `SmartAccountSigner`.
    ///
    /// - For `ExternalSigner` with a credential ID: type = "passkey", identifier = credential ID.
    /// - For `ExternalSigner` with 32-byte key data: type = "ed25519", identifier = hex prefix.
    /// - For any other `ExternalSigner`: type = "external", identifier = truncated verifier address.
    /// - For `DelegatedSigner`: type = "delegated", identifier = truncated address.
    private func signerTypeInfo(signer: SmartAccountSigner) -> (type: String, identifier: String) {
        if let external = signer as? ExternalSigner {
            if let credentialId = bridgeWrapper.bridge.getCredentialIdFromSigner(signer: external) {
                return ("passkey", credentialId)
            }
            if external.keyData.size == 32 {
                let hexStr = KotlinInterop.hexString(from: external.keyData)
                return ("ed25519", "\(hexStr.prefix(8))...")
            }
            let display = KotlinInterop.truncateAddress(
                external.verifierAddress, prefixLength: 4, suffixLength: 4
            )
            return ("external", display)
        } else if let delegated = signer as? DelegatedSigner {
            let display = KotlinInterop.truncateAddress(
                delegated.address, prefixLength: 8, suffixLength: 8
            )
            return ("delegated", display)
        } else {
            return ("unknown", "(unknown signer)")
        }
    }

    /// Returns the background tint color for a signer chip based on its type tag.
    private func signerChipBackground(type: String) -> Color {
        switch type.lowercased() {
        case "passkey":   return Material3Colors.signerPasskey.opacity(0.08)
        case "delegated": return Material3Colors.signerDelegated.opacity(0.08)
        case "ed25519":   return Material3Colors.signerEd25519.opacity(0.08)
        default:          return Material3Colors.signerDefault.opacity(0.08)
        }
    }

    /// Formats the context type of a rule for display using pattern-matched casting.
    ///
    /// Falls back to "Default (Any Operation)" for unrecognized types.
    private func contextTypeDisplayString(for rule: ParsedContextRule) -> String {
        let contextType = rule.contextType
        if let callContract = contextType as? ContextRuleType.CallContract {
            let truncated = KotlinInterop.truncateAddress(
                callContract.contractAddress, prefixLength: 8, suffixLength: 8
            )
            return "Call Contract: \(truncated)"
        } else if let createContract = contextType as? ContextRuleType.CreateContract {
            let hex = KotlinInterop.hexString(from: createContract.wasmHash)
            return "Create Contract: \(hex.prefix(8))..."
        } else {
            return "Default (Any Operation)"
        }
    }

    /// Title string for the remove confirmation dialog.
    private var removeDialogTitle: String {
        if let rule = ruleToRemove {
            return "Remove Context Rule #\(rule.id)"
        }
        return "Remove Context Rule"
    }

    // MARK: - Actions

    /// Loads the rule list. `failureMessage` prefixes the error shown when the
    /// fetch fails (the post-removal refresh uses its own wording).
    private func loadRules(failureMessage: String = "Failed to fetch context rules") async {
        guard appState.isConnected else { return }
        let bridge = bridgeWrapper.bridge
        await MainActor.run {
            isLoading = true
            errorMessage = nil
        }
        do {
            let result = try await bridge.loadContextRules()
            let loaded = KotlinInterop.toArray(result, as: ParsedContextRule.self)
            await MainActor.run {
                rules = loaded
                isLoading = false
            }
        } catch {
            await MainActor.run {
                errorMessage = "\(failureMessage): \(error.localizedDescription)"
                isLoading = false
            }
        }
    }

    private func performRemove(rule: ParsedContextRule) async {
        // Check if multi-signer authorization is needed
        let isSinglePasskey = !(signersLoaded && availableSigners.count > 1)

        if !isSinglePasskey {
            await MainActor.run {
                ruleToRemoveWithSigners = rule
                showRemoveSignerPicker = true
            }
            return
        }

        // Single-signer path
        await performRemoveWithSigners(rule: rule, signerDescs: [], secretKeys: [:], ed25519Secrets: [:])
    }

    /// Performs rule removal with optional multi-signer authorization.
    private func performRemoveWithSigners(
        rule: ParsedContextRule,
        signerDescs: [SignerDescriptor],
        secretKeys: [String: String],
        ed25519Secrets: [String: String]
    ) async {
        let bridge = bridgeWrapper.bridge
        let ruleIdForBridge = Int32(bitPattern: rule.id)
        await MainActor.run {
            isRemoving = true
        }
        do {
            let result = try await bridge.removeContextRule(
                ruleId: ruleIdForBridge,
                signerDescriptors: signerDescs,
                delegatedSecretKeys: secretKeys,
                ed25519SecretKeys: ed25519Secrets
            )
            if result.success {
                await loadRules(failureMessage: "Failed to refresh rules")
                await MainActor.run {
                    isRemoving = false
                }
            } else {
                let errMsg = result.error ?? "Unknown error"
                await MainActor.run {
                    errorMessage = "Failed to remove rule: \(errMsg)"
                    isRemoving = false
                }
            }
        } catch {
            let msg = error.localizedDescription
            await MainActor.run {
                if bridge.isUserCancellation(message: msg) {
                    errorMessage = "Passkey authentication cancelled"
                } else {
                    errorMessage = "Failed to remove rule: \(msg)"
                }
                isRemoving = false
            }
        }
    }

    /// Loads the available signers. On failure, single-signer rule removal stays
    /// usable; the load-failed flag drives the multi-signer-unavailable warning.
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
}
