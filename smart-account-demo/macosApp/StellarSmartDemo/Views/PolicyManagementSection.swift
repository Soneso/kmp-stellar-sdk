//
//  PolicyManagementSection.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

/// Form section for managing policies on a context rule.
///
/// Shown in both create and edit mode. Supports threshold, spending limit, and weighted
/// threshold policies. Policy types already added to the rule are excluded from the picker.
/// In edit mode, existing on-chain policies additionally render inline parameter-editing
/// forms pre-populated from their on-chain values.
struct PolicyManagementSection: View {

    @ObservedObject var viewModel: ContextRuleBuilderViewModel

    var body: some View {
        VStack(spacing: 12) {
            sectionHeader
            policyList
            if viewModel.isEditing {
                editPolicyParamsForms
            }
            if viewModel.policies.count < viewModel.maxPolicies {
                addPolicyCard
            }
        }
        .disabled(viewModel.isSubmitting)
    }

    // MARK: - Section Header

    private var sectionHeader: some View {
        InfoCard(color: .variant) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Policies")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                Text(
                    "Attach policies to constrain how operations are authorized. " +
                    "Policies are optional. Maximum \(viewModel.maxPolicies) per rule."
                )
                .font(.callout)
                .foregroundColor(Material3Colors.onSurfaceVariant)
                if viewModel.isEditing {
                    Text("Each policy change requires a separate passkey authentication.")
                        .font(.caption)
                        .foregroundColor(Material3Colors.primary)
                }

                if let error = viewModel.fieldErrors["policies"] {
                    Text(error)
                        .font(.footnote)
                        .foregroundColor(Material3Colors.error)
                }
            }
        }
    }

    // MARK: - Edit Mode: Inline Policy Parameter Forms

    /// Inline editing forms for existing on-chain policies with loaded parameters.
    @ViewBuilder
    private var editPolicyParamsForms: some View {
        let editablePolicies = viewModel.policies.filter {
            $0.isOriginal && $0.originalParams != nil && $0.onChainId != nil
        }
        ForEach(editablePolicies, id: \.onChainId) { policy in
            EditPolicyParamsForm(
                viewModel: viewModel,
                onChainId: policy.onChainId!,
                params: policy.originalParams!
            )
        }
    }

    // MARK: - Policy List

    @ViewBuilder
    private var policyList: some View {
        if viewModel.policies.isEmpty {
            InfoCard(color: .surface) {
                Text("No policies attached. Policies are optional.")
                    .font(.callout)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity)
            }
        } else {
            VStack(spacing: 8) {
                ForEach(Array(viewModel.policies.enumerated()), id: \.element.id) { index, policy in
                    policyRow(policy: policy, index: index)
                }
                Text(
                    "\(viewModel.policies.count) \(viewModel.policies.count == 1 ? "policy" : "policies") attached"
                )
                .font(.caption)
                .foregroundColor(Material3Colors.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)
                .id(ContextRuleBuilderViewModel.scrollAnchorStagedPolicies)
            }
        }
    }

    private func policyRow(policy: PolicyEntry, index: Int) -> some View {
        let chipColor = ContextRuleBuilderViewModel.policyColor(for: policy.policyType)
        return VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                // Type badge
                TagPill(text: policy.policyName, background: chipColor)

                if policy.isOriginal {
                    TagPill(
                        text: "on-chain",
                        font: .system(size: 9, weight: .medium),
                        background: Material3Colors.primary.opacity(0.7),
                        horizontalPadding: 5,
                        verticalPadding: 1,
                        cornerRadius: 3
                    )
                }

                if policy.modified {
                    TagPill(
                        text: "modified",
                        font: .system(size: 9, weight: .medium),
                        background: Color.orange.opacity(0.8),
                        horizontalPadding: 5,
                        verticalPadding: 1,
                        cornerRadius: 3
                    )
                }

                Text(policy.label)
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurface)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Button(action: { viewModel.removePolicy(at: index) }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundColor(Material3Colors.error)
                }
                .buttonStyle(.plain)
                .help("Remove policy")
            }
            // Policy address (truncated)
            Text(KotlinInterop.truncateAddress(policy.policyAddress, prefixLength: 8, suffixLength: 8))
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(Material3Colors.onSurfaceVariant)
                .padding(.leading, 4)

            // On-chain params display for existing policies.
            if let params = policy.originalParams {
                policyParamsView(params: params)
            }
        }
        .padding(12)
        .background(chipColor.opacity(0.08))
        .cornerRadius(6)
    }

    @ViewBuilder
    private func policyParamsView(params: PolicyParamsBridge) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            switch params.type {
            case "threshold":
                if let threshold = params.threshold {
                    Text("On-chain threshold: \(threshold)")
                        .font(.system(size: 11))
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
            case "spending_limit":
                if let limit = params.spendingLimit {
                    Text("On-chain limit: \(limit) XLM")
                        .font(.system(size: 11))
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
                if let days = params.periodDays {
                    Text("On-chain period: \(days) day(s)")
                        .font(.system(size: 11))
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
            case "weighted_threshold":
                if let threshold = params.threshold {
                    Text("On-chain threshold: \(threshold)")
                        .font(.system(size: 11))
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
                if let weights = params.signerWeights {
                    ForEach(Array(weights.keys.sorted()), id: \.self) { key in
                        let truncKey = KotlinInterop.truncateAddress(key, prefixLength: 8, suffixLength: 4)
                        Text("  \(truncKey) = \(weights[key] ?? 0)")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(Material3Colors.onSurfaceVariant)
                    }
                }
            default:
                EmptyView()
            }
        }
        .padding(.leading, 4)
    }

    // MARK: - Add Policy Card

    private var addPolicyCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Add Policy")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(Material3Colors.onSurface)

            policyTypePicker

            if let error = viewModel.fieldErrors["policy"] {
                Text(error)
                    .font(.footnote)
                    .foregroundColor(Material3Colors.error)
            }

            if let selected = viewModel.selectedPolicyInfo {
                // Contract address hint
                HStack(spacing: 4) {
                    Text("Contract:")
                        .font(.caption)
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                    Text(KotlinInterop.truncateAddress(selected.address, prefixLength: 8, suffixLength: 8))
                        .font(.system(.caption, design: .monospaced))
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }

                switch selected.type {
                case "threshold":          thresholdFields
                case "spending_limit":     spendingLimitFields
                case "weighted_threshold": weightedThresholdFields
                default:
                    Text("Select a policy type above to configure parameters.")
                        .font(.caption)
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
            } else {
                Text("Select a policy type above to configure parameters.")
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.surface)
        .cornerRadius(8)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }

    // MARK: - Policy Type Picker

    private var policyTypePicker: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Policy Type")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Material3Colors.onSurfaceVariant)

            let available = viewModel.availablePolicies
            if available.isEmpty {
                Text("All policy types already added.")
                    .font(.callout)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Material3Colors.surface)
                    .cornerRadius(6)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(Material3Colors.outline, lineWidth: 1)
                    )
            } else {
                Picker("Policy Type", selection: Binding(
                    get: { viewModel.selectedPolicyIndex },
                    set: { idx in
                        viewModel.selectedPolicyIndex = idx
                        viewModel.resetPolicyFields()
                    }
                )) {
                    Text("Select policy type...").tag(-1)
                    ForEach(available.indices, id: \.self) { idx in
                        VStack(alignment: .leading) {
                            Text(available[idx].name)
                            Text(available[idx].description)
                                .font(.caption)
                                .foregroundColor(Material3Colors.onSurfaceVariant)
                        }
                        .tag(idx)
                    }
                }
                .labelsHidden()
                .pickerStyle(.menu)
            }
        }
    }

    // MARK: - Threshold Fields

    private var thresholdFields: some View {
        VStack(spacing: 8) {
            ValidationTextField(
                label: "Threshold (required signers)",
                text: Binding(
                    get: { viewModel.thresholdValue },
                    set: { value in
                        viewModel.thresholdValue = value.filter { $0.isNumber }
                        viewModel.fieldErrors.removeValue(forKey: "threshold")
                    }
                ),
                error: viewModel.fieldErrors["threshold"],
                placeholder: "e.g., 2"
            )

            if viewModel.fieldErrors["threshold"] == nil {
                let signerCount = viewModel.signers.count
                Text("Number of signers required to authorize (1 to \(max(signerCount, 1)))")
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }

            LoadingButton(
                action: { viewModel.addThresholdPolicy() },
                isLoading: false,
                isEnabled: !viewModel.thresholdValue.isEmpty,
                text: "Add Threshold Policy",
                loadingText: "Adding..."
            )
        }
    }

    // MARK: - Spending Limit Fields

    private var spendingLimitFields: some View {
        VStack(spacing: 8) {
            ValidationTextField(
                label: "Amount",
                text: Binding(
                    get: { viewModel.spendingLimitAmount },
                    set: { value in
                        let filtered = value.filter { $0.isNumber || $0 == "." }
                        if filtered.components(separatedBy: ".").count <= 2 {
                            viewModel.spendingLimitAmount = filtered
                        }
                        viewModel.fieldErrors.removeValue(forKey: "spendingAmount")
                    }
                ),
                error: viewModel.fieldErrors["spendingAmount"],
                placeholder: "e.g., 100.0"
            )

            if viewModel.fieldErrors["spendingAmount"] == nil {
                Text("Maximum amount allowed per period")
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }

            ValidationTextField(
                label: "Period (days)",
                text: Binding(
                    get: { viewModel.spendingLimitPeriodDays },
                    set: { value in
                        viewModel.spendingLimitPeriodDays = value.filter { $0.isNumber }
                        viewModel.fieldErrors.removeValue(forKey: "spendingPeriod")
                    }
                ),
                error: viewModel.fieldErrors["spendingPeriod"],
                placeholder: "e.g., 1"
            )

            if viewModel.fieldErrors["spendingPeriod"] == nil {
                let days = Int(viewModel.spendingLimitPeriodDays) ?? 0
                Text(viewModel.periodLedgerHint(days: days))
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }

            LoadingButton(
                action: { viewModel.addSpendingLimitPolicy() },
                isLoading: false,
                isEnabled: !viewModel.spendingLimitAmount.isEmpty &&
                           !viewModel.spendingLimitPeriodDays.isEmpty,
                text: "Add Spending Limit Policy",
                loadingText: "Adding..."
            )
        }
    }

    // MARK: - Weighted Threshold Fields

    private var weightedThresholdFields: some View {
        VStack(spacing: 8) {
            ValidationTextField(
                label: "Weight Threshold",
                text: Binding(
                    get: { viewModel.weightedThresholdValue },
                    set: { value in
                        viewModel.weightedThresholdValue = value.filter { $0.isNumber }
                        viewModel.fieldErrors.removeValue(forKey: "weightedThreshold")
                    }
                ),
                error: viewModel.fieldErrors["weightedThreshold"],
                placeholder: "e.g., 100"
            )

            if viewModel.fieldErrors["weightedThreshold"] == nil {
                Text("Minimum total weight required for authorization")
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }

            // Per-signer weight fields
            if viewModel.signers.isEmpty {
                Text("Add signers above to configure per-signer weights.")
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Per-Signer Weights")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Material3Colors.onSurface)

                    ForEach(viewModel.signers) { signer in
                        signerWeightRow(signer: signer)
                    }

                    if let error = viewModel.fieldErrors["signerWeights"] {
                        Text(error)
                            .font(.footnote)
                            .foregroundColor(Material3Colors.error)
                    }
                }
            }

            LoadingButton(
                action: { viewModel.addWeightedThresholdPolicy() },
                isLoading: false,
                isEnabled: !viewModel.weightedThresholdValue.isEmpty && !viewModel.signers.isEmpty,
                text: "Add Weighted Threshold Policy",
                loadingText: "Adding..."
            )
        }
    }

    private func signerWeightRow(signer: SignerItem) -> some View {
        HStack(spacing: 8) {
            SignerBadge(signerType: signer.type)
            Text(signer.displayName)
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(Material3Colors.onSurface)
                .lineLimit(1)
                .truncationMode(.middle)
                .frame(maxWidth: .infinity, alignment: .leading)

            TextField("Wt.", text: Binding(
                get: { viewModel.signerWeights[signer.identifier] ?? "" },
                set: { value in
                    viewModel.signerWeights[signer.identifier] = value.filter { $0.isNumber }
                    viewModel.fieldErrors.removeValue(forKey: "signerWeights")
                }
            ))
            .textFieldStyle(.plain)
            .font(.callout)
            .multilineTextAlignment(.center)
            .padding(6)
            .frame(width: 60)
            .background(Material3Colors.surface)
            .cornerRadius(4)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(Material3Colors.outline, lineWidth: 1)
            )
        }
    }
}

// MARK: - Edit Policy Params Form

/// Inline form for editing parameters of an existing on-chain policy.
///
/// Fields are pre-populated from the policy's on-chain parameters. Changing a value marks
/// the entry as modified and stages the new parameters for submission; reverting to the
/// original values clears the modified flag.
private struct EditPolicyParamsForm: View {

    @ObservedObject var viewModel: ContextRuleBuilderViewModel
    let onChainId: Int32
    let params: PolicyParamsBridge

    @State private var editThreshold: String = ""
    @State private var editAmount: String = ""
    @State private var editPeriodDays: String = ""
    @State private var editWeightedThreshold: String = ""
    @State private var prefilled: Bool = false

    /// The current entry in the view model, looked up by on-chain ID.
    private var entry: PolicyEntry? {
        viewModel.policies.first(where: { $0.isOriginal && $0.onChainId == onChainId })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Edit \(entry?.policyName ?? "Policy") Parameters")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(Material3Colors.primary)

            if entry?.modified == true {
                Text("Parameters modified (will be updated on submit)")
                    .font(.caption)
                    .foregroundColor(Color.orange)
            }

            switch params.type {
            case "threshold":          thresholdEditFields
            case "spending_limit":     spendingLimitEditFields
            case "weighted_threshold": weightedThresholdEditFields
            default:                   EmptyView()
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Material3Colors.logInfo.opacity(0.06))
        .cornerRadius(8)
        .onAppear { prefillFromParams() }
    }

    // MARK: - Threshold

    private var thresholdEditFields: some View {
        VStack(alignment: .leading, spacing: 8) {
            ValidationTextField(
                label: "Threshold (required signers)",
                text: Binding(
                    get: { editThreshold },
                    set: { value in
                        let filtered = value.filter { $0.isNumber }
                        editThreshold = filtered
                        viewModel.updateThresholdPolicyEntry(onChainId: onChainId, thresholdStr: filtered)
                    }
                ),
                isEnabled: !viewModel.isSubmitting
            )
            Text("Current on-chain value: \(params.threshold.map { "\($0)" } ?? "unknown")")
                .font(.caption)
                .foregroundColor(Material3Colors.onSurfaceVariant)
        }
    }

    // MARK: - Spending Limit

    private var spendingLimitEditFields: some View {
        VStack(alignment: .leading, spacing: 8) {
            ValidationTextField(
                label: "Amount",
                text: Binding(
                    get: { editAmount },
                    set: { value in
                        let filtered = value.filter { $0.isNumber || $0 == "." }
                        if filtered.components(separatedBy: ".").count <= 2 {
                            editAmount = filtered
                            viewModel.updateSpendingLimitPolicyEntry(
                                onChainId: onChainId,
                                amountStr: filtered,
                                periodDaysStr: editPeriodDays
                            )
                        }
                    }
                ),
                isEnabled: !viewModel.isSubmitting
            )
            Text("Current on-chain value: \(params.spendingLimit ?? "unknown")")
                .font(.caption)
                .foregroundColor(Material3Colors.onSurfaceVariant)

            ValidationTextField(
                label: "Period (days)",
                text: Binding(
                    get: { editPeriodDays },
                    set: { value in
                        let filtered = value.filter { $0.isNumber }
                        editPeriodDays = filtered
                        viewModel.updateSpendingLimitPolicyEntry(
                            onChainId: onChainId,
                            amountStr: editAmount,
                            periodDaysStr: filtered
                        )
                    }
                ),
                isEnabled: !viewModel.isSubmitting
            )
            Text("Current on-chain value: \(params.periodDays.map { "\($0)" } ?? "unknown") day(s)")
                .font(.caption)
                .foregroundColor(Material3Colors.onSurfaceVariant)
        }
    }

    // MARK: - Weighted Threshold

    private var weightedThresholdEditFields: some View {
        VStack(alignment: .leading, spacing: 8) {
            ValidationTextField(
                label: "Weight Threshold",
                text: Binding(
                    get: { editWeightedThreshold },
                    set: { value in
                        let filtered = value.filter { $0.isNumber }
                        editWeightedThreshold = filtered
                        viewModel.updateWeightedThresholdPolicyEntry(
                            onChainId: onChainId,
                            thresholdStr: filtered
                        )
                    }
                ),
                isEnabled: !viewModel.isSubmitting
            )
            Text("Current on-chain value: \(params.threshold.map { "\($0)" } ?? "unknown")")
                .font(.caption)
                .foregroundColor(Material3Colors.onSurfaceVariant)

            // Per-signer weight fields with original values
            if !viewModel.signers.isEmpty {
                Text("Per-Signer Weights")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Material3Colors.onSurface)

                ForEach(viewModel.signers) { signer in
                    editSignerWeightRow(signer: signer)
                }
            }
        }
    }

    private func editSignerWeightRow(signer: SignerItem) -> some View {
        HStack(spacing: 8) {
            SignerBadge(signerType: signer.type)
            VStack(alignment: .leading, spacing: 1) {
                Text(signer.displayName)
                    .font(.system(size: 11, design: .monospaced))
                    .foregroundColor(Material3Colors.onSurface)
                    .lineLimit(1)
                    .truncationMode(.middle)
                if let weight = viewModel.onChainWeight(for: signer, in: params) {
                    Text("On-chain: \(weight)")
                        .font(.system(size: 9))
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            TextField("Wt.", text: Binding(
                get: { viewModel.signerWeights[signer.identifier] ?? "" },
                set: { value in
                    viewModel.signerWeights[signer.identifier] = value.filter { $0.isNumber }
                    viewModel.updateWeightedThresholdPolicyEntry(
                        onChainId: onChainId,
                        thresholdStr: editWeightedThreshold
                    )
                }
            ))
            .textFieldStyle(.plain)
            .font(.callout)
            .multilineTextAlignment(.center)
            .padding(6)
            .frame(width: 60)
            .background(Material3Colors.surface)
            .cornerRadius(4)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(Material3Colors.outline, lineWidth: 1)
            )
            .disabled(viewModel.isSubmitting)
        }
    }

    // MARK: - Prefill

    /// Pre-populates the form fields from the on-chain parameters on first appearance.
    private func prefillFromParams() {
        guard !prefilled else { return }
        prefilled = true
        switch params.type {
        case "threshold":
            editThreshold = params.threshold.map { "\($0)" } ?? ""
        case "spending_limit":
            editAmount = params.spendingLimit ?? ""
            editPeriodDays = params.periodDays.map { "\($0)" } ?? ""
        case "weighted_threshold":
            editWeightedThreshold = params.threshold.map { "\($0)" } ?? ""
            for signer in viewModel.signers {
                if let weight = viewModel.onChainWeight(for: signer, in: params) {
                    viewModel.signerWeights[signer.identifier] = "\(weight)"
                }
            }
        default:
            break
        }
    }
}
