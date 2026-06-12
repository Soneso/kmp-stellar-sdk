//
//  SignerPickerSheet.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

/// Constants mirroring the values defined in the Kotlin SDK's SmartAccountConstants object.
enum SignerConstants {
    /// Length of a Stellar secret key in StrKey encoding (S...), matching
    /// SmartAccountConstants.ED25519_SECRET_KEY_STRKEY_LENGTH.
    static let strkeySecretLength = 56
    /// Size of a raw Ed25519 seed in bytes, matching SmartAccountConstants.ED25519_SECRET_KEY_SIZE.
    static let ed25519SeedSizeBytes = 32
    /// Hex character count for a 32-byte Ed25519 seed, matching
    /// SmartAccountConstants.ED25519_SECRET_KEY_SIZE * 2.
    static let ed25519SeedHexLength = 64
}

/// Mutable state for one secret-entry form in the signer picker.
///
/// The picker holds two independent instances — one per `Kind` — so the
/// delegated-key form and the Ed25519-seed form keep separate visibility:
/// opening one does not close the other.
struct KeyEntryDraft {

    /// Discriminates the two secret-entry flows and supplies their display strings.
    enum Kind {
        /// Stellar secret key (S...) entry for a delegated signer.
        case secretKey
        /// Raw 32-byte seed (hex) entry for an Ed25519 signer.
        case ed25519Seed

        /// Header label for the entry form. The delegated form shows the same
        /// symmetric address truncation as the signer row; the Ed25519 form shows
        /// the 8-character public-key prefix.
        func headerLabel(for identifier: String) -> String {
            switch self {
            case .secretKey:
                return "Secret key for \(KotlinInterop.truncateAddress(identifier, prefixLength: 6, suffixLength: 6))"
            case .ed25519Seed:
                return "Ed25519 secret for \(KotlinInterop.truncatePrefix(identifier, length: 8))"
            }
        }

        /// Placeholder text for the secure input field.
        var placeholder: String {
            switch self {
            case .secretKey:   return "S..."
            case .ed25519Seed: return "\(SignerConstants.ed25519SeedHexLength) hex characters"
            }
        }

        /// In-memory-only warning shown below the input field.
        var warningText: String {
            switch self {
            case .secretKey:
                return "Your secret key is stored in memory only and cleared when this sheet closes."
            case .ed25519Seed:
                return "Your seed is stored in memory only and cleared when this sheet closes."
            }
        }
    }

    let kind: Kind
    /// Identifier of the signer currently awaiting key entry; nil when the form is closed.
    var target: String? = nil
    /// Current (unverified) secret input.
    var draft: String = ""
    /// True while the secret is shown as plain text.
    var isVisible: Bool = false
    /// Validation error for the current draft, cleared on every edit.
    var error: String? = nil
    /// True while the secret is being verified against the bridge.
    var isValidating: Bool = false

    /// Opens the form for a signer, clearing any previous draft state.
    mutating func begin(target identifier: String) {
        target = identifier
        draft = ""
        error = nil
        isVisible = false
    }

    /// Closes the form and clears the draft state (cancel, successful verify,
    /// and sheet re-presentation all route through here).
    mutating func reset() {
        target = nil
        draft = ""
        error = nil
        isVisible = false
    }
}

/// Modal sheet for selecting one or more signers during a multi-signer transfer.
///
/// Displays available signers grouped by type: passkey, delegated, and Ed25519.
/// The currently connected passkey is auto-selected on presentation. Delegated
/// signers require the user to enter and verify a Stellar secret key before they
/// can be selected. Ed25519 signers require the user to enter and verify a
/// hex-encoded 32-byte seed whose derived public key must match the signer's
/// registered public key.
///
/// On confirmation the caller receives:
/// - The selected `SignerInfoBridge` list.
/// - A dictionary mapping delegated-signer identifiers to their verified secret key
///   strings (for use with the Kotlin bridge).
/// - A dictionary mapping Ed25519 signer identity strings
///   (`"<verifierAddress>:<publicKeyHex>"`) to their verified hex-encoded 32-byte seeds.
///
/// ## Usage
///
/// ```swift
/// SignerPickerSheet(
///     signers: availableSigners,
///     activeCredentialId: connectedCredentialId,
///     description: "Choose which signers co-authorize this transfer. " +
///         "For Stellar account signers, enter the secret key to enable signing.",
///     onConfirm: { selected, secretKeys, ed25519Secrets in
///         performTransfer(signers: selected, keys: secretKeys, ed25519Keys: ed25519Secrets)
///     },
///     onDismiss: { showSheet = false },
///     bridge: bridgeWrapper.bridge
/// )
/// ```
struct SignerPickerSheet: View {
    let signers: [SignerInfoBridge]
    let activeCredentialId: String?
    /// Sheet title text.
    var title: String = "Select Signers"
    /// Explanatory text shown below the title, specific to the operation being authorized.
    var description: String = "Choose which signers to use for this transaction."
    let onConfirm: ([SignerInfoBridge], [String: String], [String: String]) -> Void
    let onDismiss: () -> Void

    // MARK: - State

    /// Tracks selected state by signer identifier.
    @State private var selectedIds: Set<String> = []
    /// Verified secret keys keyed by delegated signer identifier.
    @State private var verifiedSecretKeys: [String: String] = [:]
    /// Verified Ed25519 seeds keyed by identity string `"<verifierAddress>:<publicKeyHex>"`.
    @State private var verifiedEd25519Secrets: [String: String] = [:]

    // MARK: - Key entry state

    /// Secret-key entry form state for delegated signers (target is the G-address).
    @State private var secretKeyEntry = KeyEntryDraft(kind: .secretKey)

    /// Seed entry form state for Ed25519 signers (target is the publicKeyHex identifier).
    @State private var ed25519SeedEntry = KeyEntryDraft(kind: .ed25519Seed)

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
                        sectionHeader("Stellar Account Signers")
                        ForEach(delegatedSigners, id: \.identifier) { signer in
                            delegatedRow(signer)
                            if secretKeyEntry.target == signer.identifier {
                                keyEntryForm(
                                    for: signer,
                                    entry: $secretKeyEntry,
                                    onVerify: { verifySecretKey(for: signer) }
                                )
                            }
                        }
                    }

                    if !ed25519Signers.isEmpty {
                        sectionHeader("Ed25519 Signers")
                        ForEach(ed25519Signers, id: \.identifier) { signer in
                            ed25519Row(signer)
                            if ed25519SeedEntry.target == signer.identifier {
                                keyEntryForm(
                                    for: signer,
                                    entry: $ed25519SeedEntry,
                                    onVerify: { verifyEd25519Seed(for: signer) }
                                )
                            }
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
            Text(title)
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
        Text(description)
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
                        TagPill(
                            text: "Active",
                            background: Material3Colors.logSuccess,
                            horizontalPadding: 6,
                            verticalPadding: 2,
                            cornerRadius: 3
                        )
                    }
                }
                Text(KotlinInterop.truncatePrefix(signer.identifier, length: 16))
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

    // MARK: - Shared signer row scaffold

    /// Renders a signer row for types that require key entry before selection (delegated and
    /// Ed25519). Both row types share the same visual scaffold: a toggle icon (disabled until
    /// the key is verified), an info column with a display name and status text, a trailing
    /// verified badge or enter-key button, and a type badge. Differences between delegated and
    /// Ed25519 rows are supplied as parameters so there is a single implementation to maintain.
    ///
    /// - Parameters:
    ///   - signer: The signer to display.
    ///   - isAuthorized: True when the signing material has been verified and stored locally.
    ///   - isEnteringKey: True when this signer's key entry form is currently expanded.
    ///   - primaryText: The main display name for the signer.
    ///   - verifiedStatusText: Caption shown below the name when `isAuthorized` is true.
    ///   - pendingStatusText: Caption shown below the name when `isAuthorized` is false.
    ///   - badgeType: Signer type string passed to `SignerBadge`.
    ///   - onToggle: Called when the user taps the row while authorized.
    ///   - onEnterKeyTap: Called when the user taps the "Enter Key" button.
    @ViewBuilder
    private func signerRow(
        signer: SignerInfoBridge,
        isAuthorized: Bool,
        isEnteringKey: Bool,
        primaryText: String,
        verifiedStatusText: String,
        pendingStatusText: String,
        badgeType: String,
        onToggle: @escaping () -> Void,
        onEnterKeyTap: @escaping () -> Void
    ) -> some View {
        let isSelected = selectedIds.contains(signer.identifier)

        HStack(spacing: 10) {
            // Toggle — only active once signing material is verified.
            Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                .font(.system(size: 18))
                .foregroundColor(isSelected
                    ? Material3Colors.primary
                    : (isAuthorized ? Material3Colors.onSurfaceVariant : Material3Colors.outline))
                .opacity(isAuthorized ? 1.0 : 0.4)

            // Info column.
            VStack(alignment: .leading, spacing: 2) {
                Text(primaryText)
                    .font(.system(size: 13, weight: .medium, design: .monospaced))
                    .foregroundColor(Material3Colors.onSurface)

                if isAuthorized {
                    Text(verifiedStatusText)
                        .font(.caption)
                        .foregroundColor(Material3Colors.logSuccess)
                } else {
                    Text(pendingStatusText)
                        .font(.caption)
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
            }

            Spacer()

            if isAuthorized {
                TagPill(
                    text: "Verified",
                    background: Material3Colors.logSuccess,
                    horizontalPadding: 6,
                    verticalPadding: 2,
                    cornerRadius: 3
                )
            } else if !isEnteringKey {
                Button(action: onEnterKeyTap) {
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

            SignerBadge(signerType: badgeType)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(isSelected && isAuthorized
            ? Material3Colors.primaryContainer.opacity(0.4)
            : Material3Colors.surfaceVariant.opacity(0.3))
        .contentShape(Rectangle())
        .onTapGesture {
            guard isAuthorized else { return }
            onToggle()
        }
    }

    // MARK: - Delegated row

    private func delegatedRow(_ signer: SignerInfoBridge) -> some View {
        let hasKey = verifiedSecretKeys[signer.identifier] != nil
        let isEnteringKey = secretKeyEntry.target == signer.identifier
        // The truncated G-address identifies the account; the signer type is already
        // conveyed by the section header and the badge.
        let displayName = KotlinInterop.truncateAddress(
            signer.identifier, prefixLength: 6, suffixLength: 6
        )

        return signerRow(
            signer: signer,
            isAuthorized: hasKey,
            isEnteringKey: isEnteringKey,
            primaryText: displayName,
            verifiedStatusText: "Ready to sign",
            pendingStatusText: "Enter secret key to enable signing",
            badgeType: "delegated",
            onToggle: { toggleSelection(signer.identifier) },
            onEnterKeyTap: { secretKeyEntry.begin(target: signer.identifier) }
        )
    }

    // MARK: - Ed25519 row

    private func ed25519Row(_ signer: SignerInfoBridge) -> some View {
        let identityKey = ed25519IdentityKey(for: signer)
        let hasSeed = verifiedEd25519Secrets[identityKey] != nil
        let isEnteringSeed = ed25519SeedEntry.target == signer.identifier
        let displayName = signer.displayName.isEmpty
            ? KotlinInterop.truncatePrefix(signer.identifier, length: 8)
            : signer.displayName

        return signerRow(
            signer: signer,
            isAuthorized: hasSeed,
            isEnteringKey: isEnteringSeed,
            primaryText: displayName,
            verifiedStatusText: "Ready to sign",
            pendingStatusText: "Enter seed to enable signing",
            badgeType: "ed25519",
            onToggle: { toggleSelection(signer.identifier) },
            onEnterKeyTap: { ed25519SeedEntry.begin(target: signer.identifier) }
        )
    }

    // MARK: - Key entry form

    /// Secret-entry form for the signer currently targeted by `entry`. The form
    /// kind supplies the header, placeholder, and warning strings; verification
    /// stays signer-type-specific via `onVerify`.
    private func keyEntryForm(
        for signer: SignerInfoBridge,
        entry: Binding<KeyEntryDraft>,
        onVerify: @escaping () -> Void
    ) -> some View {
        let kind = entry.wrappedValue.kind
        return signerSecretInputCard(
            headerLabel: kind.headerLabel(for: signer.identifier),
            placeholder: kind.placeholder,
            warningText: kind.warningText,
            draft: entry.draft,
            isVisible: entry.isVisible,
            validationError: entry.wrappedValue.error,
            onDraftChange: { entry.wrappedValue.error = nil },
            isValidating: entry.wrappedValue.isValidating,
            onVerify: onVerify,
            onCancel: { entry.wrappedValue.reset() }
        )
    }

    // MARK: - Shared secret input card

    /// Renders a secure text-entry card used by both the delegated-signer key form and the
    /// Ed25519 seed form. All differences between the two forms are supplied as parameters.
    @ViewBuilder
    private func signerSecretInputCard(
        headerLabel: String,
        placeholder: String,
        warningText: String,
        draft: Binding<String>,
        isVisible: Binding<Bool>,
        validationError: String?,
        onDraftChange: @escaping () -> Void,
        isValidating: Bool,
        onVerify: @escaping () -> Void,
        onCancel: @escaping () -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            // Form header
            HStack {
                Text(headerLabel)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Material3Colors.onSurface)
                Spacer()
                Button(action: onCancel) {
                    Image(systemName: "xmark")
                        .font(.system(size: 11))
                        .foregroundColor(Material3Colors.onSurfaceVariant)
                }
                .buttonStyle(.plain)
            }

            // Secure text field with show/hide toggle
            HStack(spacing: 0) {
                Group {
                    if isVisible.wrappedValue {
                        TextField(placeholder, text: draft)
                    } else {
                        SecureField(placeholder, text: draft)
                    }
                }
                .textFieldStyle(.plain)
                .font(.system(.callout, design: .monospaced))
                .padding(8)
                .onChange(of: draft.wrappedValue) { _ in onDraftChange() }

                Button(action: { isVisible.wrappedValue.toggle() }) {
                    Image(systemName: isVisible.wrappedValue ? "eye.slash.fill" : "eye.fill")
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
                        validationError != nil
                            ? Material3Colors.error
                            : Material3Colors.outline,
                        lineWidth: 1
                    )
            )

            if let error = validationError {
                Text(error)
                    .font(.caption)
                    .foregroundColor(Material3Colors.error)
            }

            // Warning card
            Text(warningText)
                .font(.caption)
                .foregroundColor(Material3Colors.onErrorContainer)
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Material3Colors.errorContainer.opacity(0.5))
                .cornerRadius(4)

            // Action buttons
            HStack {
                Spacer()
                Button("Cancel", action: onCancel)
                    .buttonStyle(.plain)
                    .font(.system(size: 13))
                    .foregroundColor(Material3Colors.onSurfaceVariant)

                LoadingButton(
                    action: onVerify,
                    isLoading: isValidating,
                    isEnabled: !isValidating && !draft.wrappedValue.trimmingCharacters(in: .whitespaces).isEmpty,
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
        verifiedEd25519Secrets.removeAll()
        secretKeyEntry.reset()
        ed25519SeedEntry.reset()

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

    // MARK: - Delegated key entry actions

    private func verifySecretKey(for signer: SignerInfoBridge) {
        let trimmed = secretKeyEntry.draft.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }

        secretKeyEntry.error = nil

        // Format validation: Stellar secret keys start with S and are strkeySecretLength characters.
        guard trimmed.hasPrefix("S"), trimmed.count == SignerConstants.strkeySecretLength else {
            secretKeyEntry.error = "Invalid secret key. Must start with S and be \(SignerConstants.strkeySecretLength) characters."
            return
        }

        secretKeyEntry.isValidating = true

        Task { @MainActor in
            defer { secretKeyEntry.isValidating = false }
            do {
                let matchesObj = try await bridge.validateDelegatedKey(
                    secretSeed: trimmed,
                    expectedAddress: signer.identifier
                )
                if matchesObj.boolValue {
                    verifiedSecretKeys[signer.identifier] = trimmed
                    selectedIds.insert(signer.identifier)
                    secretKeyEntry.reset()
                } else {
                    secretKeyEntry.error = "Secret key does not derive the expected account ID for this signer."
                }
            } catch {
                secretKeyEntry.error = "Validation failed: \(error.localizedDescription)"
            }
        }
    }

    // MARK: - Ed25519 seed entry actions

    private func verifyEd25519Seed(for signer: SignerInfoBridge) {
        let trimmed = ed25519SeedEntry.draft.trimmingCharacters(in: .whitespaces).lowercased()
        guard !trimmed.isEmpty else { return }

        ed25519SeedEntry.error = nil

        // Format validation: ed25519SeedSizeBytes-byte raw seed = ed25519SeedHexLength hex characters.
        guard trimmed.count == SignerConstants.ed25519SeedHexLength,
              trimmed.allSatisfy({ $0.isHexDigit }) else {
            ed25519SeedEntry.error = "Invalid seed. Must be exactly \(SignerConstants.ed25519SeedHexLength) hex characters (\(SignerConstants.ed25519SeedSizeBytes) bytes)."
            return
        }

        // signer.identifier for Ed25519 signers is the hex-encoded public key.
        let expectedPubKeyHex = signer.identifier
        let identityKey = ed25519IdentityKey(for: signer)

        ed25519SeedEntry.isValidating = true

        Task { @MainActor in
            defer { ed25519SeedEntry.isValidating = false }
            do {
                let matchesObj = try await bridge.validateEd25519Key(
                    secretHex: trimmed,
                    expectedPublicKeyHex: expectedPubKeyHex
                )
                if matchesObj.boolValue {
                    verifiedEd25519Secrets[identityKey] = trimmed
                    selectedIds.insert(signer.identifier)
                    ed25519SeedEntry.reset()
                } else {
                    ed25519SeedEntry.error = "Seed does not match this signer's public key. Verify you entered the correct seed."
                }
            } catch {
                ed25519SeedEntry.error = "Validation failed: \(error.localizedDescription)"
            }
        }
    }

    private func confirmSelection() {
        let selectedSigners = signers.filter { selectedIds.contains($0.identifier) }
        onConfirm(selectedSigners, verifiedSecretKeys, verifiedEd25519Secrets)
    }

    // MARK: - Helpers

    /// Builds the bridge identity key `"<verifierAddress>:<publicKeyHex>"` for an Ed25519 signer.
    ///
    /// The signer's `identifier` is the hex-encoded public key. The verifier address is the
    /// signer's own `verifierAddress`, populated by the bridge for every external signer.
    private func ed25519IdentityKey(for signer: SignerInfoBridge) -> String {
        "\(signer.verifierAddress ?? ""):\(signer.identifier)"
    }

    // MARK: - Bridge reference for key validation

    /// Reference to the Kotlin bridge used for delegated and Ed25519 key validation.
    ///
    /// Set by the parent view via the `bridge` parameter when constructing this sheet.
    let bridge: MacOSBridge
}
