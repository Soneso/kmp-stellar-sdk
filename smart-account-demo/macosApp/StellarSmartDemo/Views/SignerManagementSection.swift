//
//  SignerManagementSection.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

/// Form section for managing signers on a context rule being created.
///
/// Only visible in create mode — signers cannot be modified via this form in edit mode
/// (signer changes require separate SDK calls: `addSigner` / `removeSigner`).
///
/// Supports three add modes: Delegated (G-address), Ed25519 (hex key), and Passkey
/// (WebAuthn credential). Signers are displayed as removable chips.
struct SignerManagementSection: View {

    @ObservedObject var viewModel: ContextRuleBuilderViewModel
    let bridge: MacOSBridge

    var body: some View {
        VStack(spacing: 12) {
            sectionHeader
            signerList
            if !viewModel.isSubmitting {
                addSignerCard
            }
        }
    }

    // MARK: - Section Header

    private var sectionHeader: some View {
        InfoCard(color: .variant) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Signers")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                Text(
                    "Add signers who can authorize operations matching this context. " +
                    "At least one signer is required. Maximum \(viewModel.maxSigners)."
                )
                .font(.callout)
                .foregroundColor(Material3Colors.onSurfaceVariant)

                if let error = viewModel.fieldErrors["signers"] {
                    Text(error)
                        .font(.footnote)
                        .foregroundColor(Material3Colors.error)
                }
            }
        }
    }

    // MARK: - Signer List

    @ViewBuilder
    private var signerList: some View {
        if viewModel.signers.isEmpty {
            InfoCard(color: .surface) {
                Text("No signers added yet. Add at least one signer below.")
                    .font(.callout)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity)
            }
        } else {
            VStack(spacing: 8) {
                ForEach(Array(viewModel.signers.enumerated()), id: \.element.id) { index, signer in
                    signerRow(signer: signer, index: index)
                }
                Text("\(viewModel.signers.count) signer(s) added")
                    .font(.caption)
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func signerRow(signer: SignerItem, index: Int) -> some View {
        HStack(spacing: 8) {
            SignerBadge(signerType: signer.type)
            VStack(alignment: .leading, spacing: 2) {
                // Show credential ID / address / hex key as primary identifier
                Text(signer.identifier)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(Material3Colors.onSurface)
                    .lineLimit(1)
                    .truncationMode(.middle)
                // Show display name as secondary label if different from identifier
                if signer.displayName != signer.identifier &&
                   !signer.displayName.isEmpty {
                    Text(signer.displayName)
                        .font(.caption2)
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: { viewModel.removeSigner(at: index) }) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 16))
                    .foregroundColor(Material3Colors.error)
            }
            .buttonStyle(.plain)
            .help("Remove signer")
        }
        .padding(12)
        .background(signerBackground(type: signer.type))
        .cornerRadius(6)
    }

    private func signerBackground(type: String) -> Color {
        switch type.lowercased() {
        case "passkey":   return Material3Colors.signerPasskey.opacity(0.08)
        case "delegated": return Material3Colors.signerDelegated.opacity(0.08)
        case "ed25519":   return Material3Colors.signerEd25519.opacity(0.08)
        default:          return Material3Colors.signerDefault.opacity(0.08)
        }
    }

    // MARK: - Add Signer Card

    private var addSignerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Add Signer")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(Material3Colors.onSurface)

            signerModePicker

            switch viewModel.signerAddMode {
            case .delegated: delegatedTab
            case .ed25519:   ed25519Tab
            case .passkey:   passkeyTab
            }
        }
        .padding(16)
        .background(Material3Colors.surface)
        .cornerRadius(8)
        .shadow(color: Material3Colors.cardShadow, radius: 2, x: 0, y: 1)
    }

    // MARK: - Mode Picker

    private var signerModePicker: some View {
        Picker("Signer Type", selection: Binding(
            get: { viewModel.signerAddMode },
            set: { viewModel.signerAddMode = $0 }
        )) {
            ForEach(SignerAddMode.allCases, id: \.self) { mode in
                Text(mode.displayName).tag(mode)
            }
        }
        .pickerStyle(.segmented)
        .labelsHidden()
    }

    // MARK: - Delegated Tab

    private var delegatedTab: some View {
        VStack(spacing: 8) {
            ValidationTextField(
                label: "Stellar Address (G-address)",
                text: Binding(
                    get: { viewModel.delegatedAddress },
                    set: { viewModel.delegatedAddress = $0
                           viewModel.fieldErrors.removeValue(forKey: "delegatedAddress") }
                ),
                error: viewModel.fieldErrors["delegatedAddress"],
                placeholder: "GABC...",
                isMonospace: true
            )
            LoadingButton(
                action: { viewModel.addDelegatedSigner() },
                isLoading: false,
                isEnabled: !viewModel.delegatedAddress.trimmingCharacters(in: .whitespaces).isEmpty,
                text: "Add Delegated Signer",
                loadingText: "Adding..."
            )
        }
    }

    // MARK: - Ed25519 Tab

    private var ed25519Tab: some View {
        VStack(spacing: 8) {
            ValidationTextField(
                label: "Ed25519 Public Key (hex)",
                text: Binding(
                    get: { viewModel.ed25519PubKeyHex },
                    set: { viewModel.ed25519PubKeyHex = $0
                           viewModel.fieldErrors.removeValue(forKey: "ed25519PublicKey") }
                ),
                error: viewModel.fieldErrors["ed25519PublicKey"],
                placeholder: "64 hex characters",
                isMonospace: true
            )
            LoadingButton(
                action: { viewModel.addEd25519Signer() },
                isLoading: false,
                isEnabled: !viewModel.ed25519PubKeyHex.trimmingCharacters(in: .whitespaces).isEmpty,
                text: "Add Ed25519 Signer",
                loadingText: "Adding..."
            )
        }
    }

    // MARK: - Passkey Tab

    private var passkeyTab: some View {
        VStack(spacing: 12) {
            passkeyTabCard
        }
    }

    private var passkeyTabCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Passkey (WebAuthn) Signer")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(Material3Colors.signerPasskey)

            Text(
                "You can reuse a signer already registered on an existing context rule, " +
                "or register a new passkey signer for this context rule."
            )
            .font(.caption)
            .foregroundColor(Material3Colors.onSurfaceVariant)

            // Load existing passkeys button
            LoadingButton(
                action: { Task { await viewModel.loadAvailablePasskeys(bridge: bridge) } },
                isLoading: viewModel.isLoadingPasskeys,
                isEnabled: !viewModel.isLoadingPasskeys && !viewModel.isRegistering &&
                           !viewModel.isSubmitting && !viewModel.passkeysLoaded,
                text: "Reuse Signer",
                loadingText: "Loading..."
            )

            // Available passkeys list
            if viewModel.passkeysLoaded {
                if viewModel.availablePasskeys.isEmpty {
                    Text("No existing passkey signers found on this account.")
                        .font(.caption)
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                } else {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Available signers from existing context rules:")
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(Material3Colors.onSurface)

                        ForEach(viewModel.availablePasskeys, id: \.identifier) { passkey in
                            availablePasskeyRow(passkey)
                        }
                    }
                }
            }

            Divider()
                .background(Material3Colors.outline.opacity(0.3))

            // Register new passkey
            Text("Register a new passkey signer for this context rule:")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(Material3Colors.onSurface)

            ValidationTextField(
                label: "Passkey Name",
                text: Binding(
                    get: { viewModel.newPasskeyName },
                    set: { viewModel.newPasskeyName = $0
                           viewModel.fieldErrors.removeValue(forKey: "passkeyName") }
                ),
                error: viewModel.fieldErrors["passkeyName"],
                placeholder: "e.g., Recovery Key",
                isEnabled: !viewModel.isRegistering && !viewModel.isSubmitting
            )

            LoadingButton(
                action: { Task { await viewModel.registerPasskey(bridge: bridge) } },
                isLoading: viewModel.isRegistering,
                isEnabled: !viewModel.isRegistering && !viewModel.isLoadingPasskeys &&
                           !viewModel.isSubmitting &&
                           !viewModel.newPasskeyName.trimmingCharacters(in: .whitespaces).isEmpty,
                icon: "touchid",
                text: "Register New",
                loadingText: "Registering...",
                style: .filled
            )
        }
        .padding(12)
        .background(Material3Colors.signerPasskey.opacity(0.08))
        .cornerRadius(8)
    }

    private func availablePasskeyRow(_ passkey: SignerInfoBridge) -> some View {
        let alreadyAdded = viewModel.isPasskeyAlreadyAdded(passkey)
        let displayName = passkey.displayName.isEmpty
            ? KotlinInterop.truncateAddress(passkey.identifier, prefixLength: 8, suffixLength: 8)
            : passkey.displayName

        return Button(action: {
            if !alreadyAdded { viewModel.addPasskeyFromList(passkey) }
        }) {
            HStack(spacing: 8) {
                SignerBadge(signerType: "passkey")
                Text(alreadyAdded ? "\(displayName) (already added)" : "Add: \(displayName)")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(alreadyAdded
                        ? Material3Colors.onSurfaceVariant
                        : Material3Colors.primary)
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 10)
            .frame(maxWidth: .infinity)
            .background(
                alreadyAdded
                    ? Material3Colors.surface
                    : Material3Colors.primaryContainer
            )
            .cornerRadius(6)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(Material3Colors.outline, lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
        .disabled(alreadyAdded || viewModel.isSubmitting)
        .opacity(alreadyAdded ? 0.6 : 1.0)
    }
}
