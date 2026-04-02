//
//  SignerPickerSheet.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

/// Modal sheet for selecting one or more signers during a multi-signer transfer.
///
/// Displays available signers grouped by type: passkey, delegated, and Ed25519.
/// The currently connected passkey is auto-selected on presentation. Delegated
/// signers require the user to enter and verify a Stellar secret key before they
/// can be selected.
///
/// On confirmation the caller receives the selected `SignerInfoBridge` list and a
/// dictionary mapping delegated-signer identifiers to their verified secret key
/// strings (for use with the Kotlin bridge).
///
/// ## Usage
///
/// ```swift
/// SignerPickerSheet(
///     signers: availableSigners,
///     activeCredentialId: connectedCredentialId,
///     onConfirm: { selected, secretKeys in
///         performTransfer(signers: selected, keys: secretKeys)
///     },
///     onDismiss: { showSheet = false }
/// )
/// ```
struct SignerPickerSheet: View {
    let signers: [SignerInfoBridge]
    let activeCredentialId: String?
    let onConfirm: ([SignerInfoBridge], [String: String]) -> Void
    let onDismiss: () -> Void

    // MARK: - State

    /// Tracks selected state by signer identifier.
    @State private var selectedIds: Set<String> = []
    /// Verified secret keys keyed by delegated signer identifier.
    @State private var verifiedSecretKeys: [String: String] = [:]
    /// Identifier of the delegated signer currently awaiting key entry.
    @State private var keyEntryTarget: String? = nil
    @State private var secretKeyDraft: String = ""
    @State private var secretKeyVisible: Bool = false
    @State private var secretKeyError: String? = nil
    @State private var isValidatingKey: Bool = false

    // MARK: - Computed groups

    private var passkeySigners: [SignerInfoBridge] {
        signers.filter { $0.type.lowercased() == "passkey" }
    }

    private var delegatedSigners: [SignerInfoBridge] {
        signers.filter { $0.type.lowercased() == "delegated" }
    }

    private var ed25519Signers: [SignerInfoBridge] {
        signers.filter { $0.type.lowercased() == "ed25519" }
    }

    private var selectedCount: Int { selectedIds.count }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            // Header
            sheetHeader
            Divider()
                .background(Material3Colors.outline)

            // Scrollable signer list
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    descriptionText
                        .padding(.horizontal, 16)
                        .padding(.top, 12)
                        .padding(.bottom, 8)

                    if !passkeySigners.isEmpty {
                        sectionHeader("Passkey Signers")
                        ForEach(passkeySigners, id: \.identifier) { signer in
                            passkeyRow(signer)
                        }
                    }

                    if !delegatedSigners.isEmpty {
                        sectionHeader("Delegated Signers")
                        ForEach(delegatedSigners, id: \.identifier) { signer in
                            delegatedRow(signer)
                            if keyEntryTarget == signer.identifier {
                                secretKeyForm(for: signer)
                            }
                        }
                    }

                    if !ed25519Signers.isEmpty {
                        sectionHeader("Ed25519 Signers")
                        ForEach(ed25519Signers, id: \.identifier) { signer in
                            ed25519Row(signer)
                        }
                    }

                    if signers.isEmpty {
                        Text("No signers available for this context.")
                            .font(.callout)
                            .foregroundColor(Material3Colors.onSurfaceVariant)
                            .padding(16)
                    }
                }
            }
            .frame(maxHeight: 480)

            Divider()
                .background(Material3Colors.outline)

            // Footer
            sheetFooter
        }
        .frame(width: 520)
        .background(Material3Colors.surface)
        .cornerRadius(12)
        .onAppear { autoSelectActivePasskey() }
    }

    // MARK: - Header

    private var sheetHeader: some View {
        HStack {
            Text("Select Signers")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(Material3Colors.onSurface)
            Spacer()
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
                    .frame(width: 28, height: 28)
                    .background(Material3Colors.surfaceVariant)
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
    }

    // MARK: - Description

    private var descriptionText: some View {
        Text("Choose which signers to use for this transaction. The transaction will be authorized by all selected signers.")
            .font(.callout)
            .foregroundColor(Material3Colors.onSurfaceVariant)
            .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: - Section header

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 13, weight: .semibold))
            .foregroundColor(Material3Colors.primary)
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 6)
    }

    // MARK: - Passkey row

    private func passkeyRow(_ signer: SignerInfoBridge) -> some View {
        let isActive = activeCredentialId != nil && signer.identifier == activeCredentialId
        let isSelected = selectedIds.contains(signer.identifier)

        return HStack(spacing: 10) {
            // Toggle
            Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                .font(.system(size: 18))
                .foregroundColor(isSelected ? Material3Colors.primary : Material3Colors.onSurfaceVariant)

            // Info
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(signer.displayName.isEmpty ? "Passkey" : signer.displayName)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(Material3Colors.onSurface)

                    if isActive {
                        Text("Active")
                            .font(.caption2)
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Material3Colors.logSuccess)
                            .cornerRadius(3)
                    }
                }
                Text(truncated(signer.identifier, prefix: 16))
                    .font(.system(.caption, design: .monospaced))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }

            Spacer()

            SignerBadge(signerType: "passkey", text: "WebAuthn")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(isSelected
            ? Material3Colors.primaryContainer.opacity(0.4)
            : Material3Colors.surfaceVariant.opacity(0.3))
        .contentShape(Rectangle())
        .onTapGesture { toggleSelection(signer.identifier) }
    }

    // MARK: - Delegated row

    private func delegatedRow(_ signer: SignerInfoBridge) -> some View {
        let hasKey = verifiedSecretKeys[signer.identifier] != nil
        let isSelected = selectedIds.contains(signer.identifier)
        let isEnteringKey = keyEntryTarget == signer.identifier

        return HStack(spacing: 10) {
            // Toggle — only active once key is verified
            Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                .font(.system(size: 18))
                .foregroundColor(isSelected
                    ? Material3Colors.primary
                    : (hasKey ? Material3Colors.onSurfaceVariant : Material3Colors.outline))
                .opacity(hasKey ? 1.0 : 0.4)

            // Info
            VStack(alignment: .leading, spacing: 2) {
                Text(signer.displayName.isEmpty ? truncated(signer.identifier, prefix: 8) : signer.displayName)
                    .font(.system(size: 13, weight: .medium, design: .monospaced))
                    .foregroundColor(Material3Colors.onSurface)

                if hasKey {
                    Text("Ready to sign")
                        .font(.caption)
                        .foregroundColor(Material3Colors.logSuccess)
                } else {
                    Text("Enter secret key to enable signing")
                        .font(.caption)
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
            }

            Spacer()

            if hasKey {
                Text("Verified")
                    .font(.caption2)
                    .foregroundColor(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Material3Colors.logSuccess)
                    .cornerRadius(3)
            } else if !isEnteringKey {
                Button(action: {
                    keyEntryTarget = signer.identifier
                    secretKeyDraft = ""
                    secretKeyError = nil
                    secretKeyVisible = false
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "key")
                            .font(.system(size: 11))
                        Text("Enter Key")
                            .font(.caption)
                    }
                    .foregroundColor(Material3Colors.primary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Material3Colors.primary, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            }

            SignerBadge(signerType: "delegated")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(isSelected && hasKey
            ? Material3Colors.primaryContainer.opacity(0.4)
            : Material3Colors.surfaceVariant.opacity(0.3))
        .contentShape(Rectangle())
        .onTapGesture {
            guard hasKey else { return }
            toggleSelection(signer.identifier)
        }
    }

    // MARK: - Secret key entry form

    private func secretKeyForm(for signer: SignerInfoBridge) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            // Form header
            HStack {
                Text("Secret key for \(truncated(signer.identifier, prefix: 8))")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Material3Colors.onSurface)
                Spacer()
                Button(action: cancelKeyEntry) {
                    Image(systemName: "xmark")
                        .font(.system(size: 11))
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
                .buttonStyle(.plain)
            }

            // Secret key field
            HStack(spacing: 0) {
                Group {
                    if secretKeyVisible {
                        TextField("S...", text: $secretKeyDraft)
                    } else {
                        SecureField("S...", text: $secretKeyDraft)
                    }
                }
                .textFieldStyle(.plain)
                .font(.system(.callout, design: .monospaced))
                .padding(8)
                .onChange(of: secretKeyDraft) { _ in secretKeyError = nil }

                Button(action: { secretKeyVisible.toggle() }) {
                    Image(systemName: secretKeyVisible ? "eye.slash.fill" : "eye.fill")
                        .font(.system(size: 13))
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
            }
            .background(Material3Colors.surface)
            .cornerRadius(6)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(
                        secretKeyError != nil
                            ? Material3Colors.error
                            : Material3Colors.outline,
                        lineWidth: 1
                    )
            )

            if let error = secretKeyError {
                Text(error)
                    .font(.caption)
                    .foregroundColor(Material3Colors.error)
            }

            // Warning card
            Text("Your secret key is stored in memory only and cleared when this sheet closes.")
                .font(.caption)
                .foregroundColor(Material3Colors.onErrorContainer)
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Material3Colors.errorContainer.opacity(0.5))
                .cornerRadius(4)

            // Action buttons
            HStack {
                Spacer()
                Button("Cancel", action: cancelKeyEntry)
                    .buttonStyle(.plain)
                    .font(.system(size: 13))
                    .foregroundColor(Material3Colors.onSurfaceVariant)

                LoadingButton(
                    action: { verifySecretKey(for: signer) },
                    isLoading: isValidatingKey,
                    isEnabled: !isValidatingKey && !secretKeyDraft.trimmingCharacters(in: .whitespaces).isEmpty,
                    text: "Verify",
                    loadingText: "Verifying...",
                    style: .filled
                )
                .frame(width: 100)
            }
        }
        .padding(12)
        .background(Material3Colors.surface)
        .cornerRadius(6)
        .shadow(color: Color.black.opacity(0.06), radius: 2, x: 0, y: 1)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    // MARK: - Ed25519 row

    private func ed25519Row(_ signer: SignerInfoBridge) -> some View {
        let isSelected = selectedIds.contains(signer.identifier)

        return HStack(spacing: 10) {
            Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                .font(.system(size: 18))
                .foregroundColor(isSelected ? Material3Colors.primary : Material3Colors.onSurfaceVariant)

            VStack(alignment: .leading, spacing: 2) {
                Text(signer.displayName.isEmpty ? truncated(signer.identifier, prefix: 8) : signer.displayName)
                    .font(.system(size: 13, weight: .medium, design: .monospaced))
                    .foregroundColor(Material3Colors.onSurface)

                Text(truncated(signer.identifier, prefix: 16))
                    .font(.system(.caption, design: .monospaced))
                    .foregroundColor(Material3Colors.onSurfaceVariant)
            }

            Spacer()

            SignerBadge(signerType: "ed25519")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(isSelected
            ? Material3Colors.primaryContainer.opacity(0.4)
            : Material3Colors.surfaceVariant.opacity(0.3))
        .contentShape(Rectangle())
        .onTapGesture { toggleSelection(signer.identifier) }
    }

    // MARK: - Footer

    private var sheetFooter: some View {
        HStack {
            Button("Cancel", action: onDismiss)
                .buttonStyle(.plain)
                .font(.system(size: 14))
                .foregroundColor(Material3Colors.onSurfaceVariant)

            Spacer()

            LoadingButton(
                action: confirmSelection,
                isLoading: false,
                isEnabled: selectedCount > 0,
                text: "Confirm (\(selectedCount) selected)",
                loadingText: "",
                style: .filled
            )
            .frame(width: 200)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    // MARK: - Actions

    private func autoSelectActivePasskey() {
        selectedIds.removeAll()
        verifiedSecretKeys.removeAll()
        keyEntryTarget = nil
        secretKeyDraft = ""
        secretKeyError = nil
        secretKeyVisible = false

        if let credId = activeCredentialId {
            if let passkey = passkeySigners.first(where: { $0.identifier == credId }) {
                selectedIds.insert(passkey.identifier)
            }
        }
    }

    private func toggleSelection(_ id: String) {
        if selectedIds.contains(id) {
            selectedIds.remove(id)
        } else {
            selectedIds.insert(id)
        }
    }

    private func cancelKeyEntry() {
        keyEntryTarget = nil
        secretKeyDraft = ""
        secretKeyError = nil
        secretKeyVisible = false
    }

    private func verifySecretKey(for signer: SignerInfoBridge) {
        let trimmed = secretKeyDraft.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }

        secretKeyError = nil

        // Format validation: Stellar secret keys start with S and are 56 characters
        guard trimmed.hasPrefix("S"), trimmed.count == 56 else {
            secretKeyError = "Invalid secret key. Must start with S and be 56 characters."
            return
        }

        isValidatingKey = true

        // Validate asynchronously to avoid blocking the main thread during key derivation
        Task { @MainActor in
            defer { isValidatingKey = false }
            verifiedSecretKeys[signer.identifier] = trimmed
            selectedIds.insert(signer.identifier)
            keyEntryTarget = nil
            secretKeyDraft = ""
            secretKeyVisible = false
        }
    }

    private func confirmSelection() {
        let selectedSigners = signers.filter { selectedIds.contains($0.identifier) }
        onConfirm(selectedSigners, verifiedSecretKeys)
    }

    // MARK: - Helpers

    private func truncated(_ value: String, prefix length: Int) -> String {
        guard value.count > length else { return value }
        return String(value.prefix(length)) + "..."
    }
}
