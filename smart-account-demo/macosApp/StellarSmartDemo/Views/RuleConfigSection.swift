//
//  RuleConfigSection.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI

/// Form section for rule name, context type, and expiry configuration.
///
/// In create mode all fields are editable. In edit mode, the context type is
/// shown as read-only text (the on-chain type cannot be changed); only the
/// rule name and expiry can be updated.
struct RuleConfigSection: View {

    @ObservedObject var viewModel: ContextRuleBuilderViewModel

    var body: some View {
        VStack(spacing: 12) {
            sectionHeader
            ruleNameField
            contextTypeRow
            expiryCard
        }
    }

    // MARK: - Section Header

    private var sectionHeader: some View {
        InfoCard(color: .variant) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Rule Configuration")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                Text("Define the context type and basic settings for this rule.")
                    .font(.callout)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }
        }
    }

    // MARK: - Rule Name

    private var ruleNameField: some View {
        ValidationTextField(
            label: "Rule Name",
            text: Binding(
                get: { viewModel.ruleName },
                set: { viewModel.ruleName = $0
                       viewModel.fieldErrors.removeValue(forKey: "ruleName") }
            ),
            error: viewModel.fieldErrors["ruleName"],
            placeholder: "e.g., DefaultRule, TokenTransfers",
            isEnabled: !viewModel.isSubmitting
        )
    }

    // MARK: - Context Type

    @ViewBuilder
    private var contextTypeRow: some View {
        if viewModel.isEditing {
            // In edit mode show the context type as read-only.
            VStack(alignment: .leading, spacing: 4) {
                Text("Context Type")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                Text(viewModel.contextTypeOption.displayName)
                    .font(.callout)
                    .foregroundColor(Material3Colors.onSurface)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Material3Colors.surface)
                    .cornerRadius(6)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(Material3Colors.outline, lineWidth: 1)
                    )
                    .opacity(0.6)
                Text("Context type cannot be changed after rule creation.")
                    .font(.caption2)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }
        } else {
            contextTypePicker
        }
    }

    private var contextTypePicker: some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Context Type")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                Picker("Context Type", selection: Binding(
                    get: { viewModel.contextTypeOption },
                    set: { option in
                        viewModel.contextTypeOption = option
                        if option == .callContract && viewModel.contractAddress.isEmpty {
                            viewModel.contractAddress = viewModel.contractOptions.first?.address ?? ""
                        }
                        viewModel.fieldErrors.removeValue(forKey: "contractAddress")
                        viewModel.fieldErrors.removeValue(forKey: "wasmHash")
                    }
                )) {
                    ForEach(ContextTypeOption.allCases, id: \.self) { option in
                        VStack(alignment: .leading) {
                            Text(option.displayName)
                            Text(option.description)
                                .font(.caption)
                                .foregroundColor(Material3Colors.onSurfaceVariant)
                        }
                        .tag(option)
                    }
                }
                .labelsHidden()
                .pickerStyle(.menu)
                .disabled(viewModel.isSubmitting)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // Contract picker for callContract
            if viewModel.contextTypeOption == .callContract {
                contractAddressPicker
            }

            // WASM hash input for createContract
            if viewModel.contextTypeOption == .createContract {
                ValidationTextField(
                    label: "WASM Hash (hex)",
                    text: Binding(
                        get: { viewModel.wasmHashHex },
                        set: { viewModel.wasmHashHex = $0
                               viewModel.fieldErrors.removeValue(forKey: "wasmHash") }
                    ),
                    error: viewModel.fieldErrors["wasmHash"],
                    placeholder: "64 hex characters",
                    isMonospace: true,
                    isEnabled: !viewModel.isSubmitting
                )
            }
        }
    }

    private var contractAddressPicker: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Contract")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Material3Colors.onSurfaceVariant)

            if viewModel.contractOptions.isEmpty {
                Text("No known contracts available.")
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
                let options = viewModel.contractOptions
                Picker("Contract", selection: Binding(
                    get: {
                        options.first(where: { $0.address == viewModel.contractAddress })?.address
                            ?? options.first?.address
                            ?? ""
                    },
                    set: { addr in
                        viewModel.contractAddress = addr
                        viewModel.fieldErrors.removeValue(forKey: "contractAddress")
                    }
                )) {
                    ForEach(options, id: \.address) { option in
                        VStack(alignment: .leading) {
                            Text(option.label)
                            Text(KotlinInterop.truncateAddress(option.address, prefixLength: 8, suffixLength: 8))
                                .font(.system(.caption, design: .monospaced))
                                .foregroundColor(Material3Colors.onSurfaceVariant)
                        }
                        .tag(option.address)
                    }
                }
                .labelsHidden()
                .pickerStyle(.menu)
                .disabled(viewModel.isSubmitting)

                if let error = viewModel.fieldErrors["contractAddress"] {
                    Text(error)
                        .font(.footnote)
                        .foregroundColor(Material3Colors.error)
                }
            }
        }
    }

    // MARK: - Expiry Card

    private var expiryCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Toggle row
            HStack(spacing: 8) {
                Toggle("", isOn: Binding(
                    get: { viewModel.hasExpiry },
                    set: { value in
                        viewModel.hasExpiry = value
                        viewModel.expiryModified = true
                        if !value {
                            viewModel.expiryLedger = ""
                            viewModel.fieldErrors.removeValue(forKey: "expiryLedger")
                        }
                    }
                ))
                .toggleStyle(.checkbox)
                .disabled(viewModel.isSubmitting)
                Text("Set Expiry")
                    .font(.callout)
                    .foregroundColor(Material3Colors.onSurface)
                Spacer()
            }
            .padding(.bottom, viewModel.hasExpiry ? 12 : 0)

            if viewModel.hasExpiry {
                expiryPickerContent
            }
        }
        .padding(16)
        .background(Material3Colors.surface)
        .cornerRadius(8)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }

    private var expiryPickerContent: some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Time from now")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Material3Colors.onSurfaceVariant)

                let options = viewModel.expiryOptions()
                Picker("Duration", selection: Binding(
                    get: { viewModel.expiryLedger },
                    set: { value in
                        viewModel.expiryLedger = value
                        viewModel.expiryModified = true
                        viewModel.fieldErrors.removeValue(forKey: "expiryLedger")
                    }
                )) {
                    Text("Select duration...").tag("")
                    ForEach(options, id: \.0) { label, ledgers in
                        Text(label).tag("\(ledgers)")
                    }
                }
                .labelsHidden()
                .pickerStyle(.menu)
                .disabled(viewModel.isSubmitting)

                if let error = viewModel.fieldErrors["expiryLedger"] {
                    Text(error)
                        .font(.footnote)
                        .foregroundColor(Material3Colors.error)
                }
            }

            Text("The rule will expire after the selected duration from the current ledger.")
                .font(.caption)
                .foregroundColor(Material3Colors.onSurfaceVariant)

            if viewModel.isEditing, let existing = viewModel.existingExpiryLedger {
                Text("Current on-chain expiry: ledger \(existing). Select a duration above to replace it.")
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }
        }
    }
}
