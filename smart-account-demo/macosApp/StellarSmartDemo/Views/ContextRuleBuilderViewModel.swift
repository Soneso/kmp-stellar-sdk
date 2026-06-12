//
//  ContextRuleBuilderViewModel.swift
//  StellarSmartDemo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

// MARK: - Supporting Types

/// Context rule type options for the form selector.
enum ContextTypeOption: String, CaseIterable {
    case defaultType    = "default"
    case callContract   = "call_contract"
    case createContract = "create_contract"

    var displayName: String {
        switch self {
        case .defaultType:    return "Default (Any Operation)"
        case .callContract:   return "Call Contract"
        case .createContract: return "Create Contract"
        }
    }

    var description: String {
        switch self {
        case .defaultType:    return "Matches any operation that does not match a more specific rule"
        case .callContract:   return "Matches invocations to a specific contract address"
        case .createContract: return "Matches contract deployments using a specific WASM hash"
        }
    }
}

/// Signer add mode for the tabbed signer-addition section.
enum SignerAddMode: String, CaseIterable {
    case delegated = "delegated"
    case ed25519   = "ed25519"
    case passkey   = "passkey"

    var displayName: String {
        switch self {
        case .delegated: return "Delegated"
        case .ed25519:   return "Ed25519"
        case .passkey:   return "Passkey"
        }
    }

    var description: String {
        switch self {
        case .delegated: return "A Stellar account (G-address) that can sign via its secret key"
        case .ed25519:   return "A raw Ed25519 public key (64 hex characters)"
        case .passkey:   return "A WebAuthn passkey signer (Touch ID / Face ID)"
        }
    }
}

/// A policy entry ready to submit.
struct PolicyEntry: Identifiable {
    let id = UUID()
    /// "threshold", "spending_limit", or "weighted_threshold".
    let policyType: String
    /// Human-readable display name.
    let policyName: String
    /// On-chain contract address (C-address).
    let policyAddress: String
    /// Display label summarizing the configuration.
    let label: String
    /// Encoded policy parameters for `PolicyDescriptor.params`.
    let params: [String: String]
    /// On-chain policy ID if loaded from an existing rule, nil for newly added.
    var onChainId: Int32? = nil
    /// True if this policy was loaded from the existing on-chain rule.
    var isOriginal: Bool = false
    /// True if the user changed parameters on an existing policy.
    var modified: Bool = false
    /// On-chain parameters loaded at edit start, for display and comparison.
    var originalParams: PolicyParamsBridge? = nil
}

/// A signer item in the pending signers list.
struct SignerItem: Identifiable {
    let id = UUID()
    /// "delegated", "ed25519", or "passkey".
    let type: String
    /// Display-ready label (truncated address, hex prefix, or passkey name).
    let displayName: String
    /// Stable bridge value: G-address, hex key (64 chars), or Base64URL credential ID.
    let identifier: String
    /// On-chain signer ID if loaded from an existing rule, nil for newly added.
    var onChainId: Int32? = nil
    /// True if this signer was loaded from the existing on-chain rule.
    var isOriginal: Bool = false
    /// True only for passkeys registered via the WebAuthn ceremony in this session,
    /// i.e. a local pending credential exists. Reused passkeys stay false.
    var isPending: Bool = false

    var descriptor: SignerDescriptor {
        SignerDescriptor(type: type, value: identifier, isPending: isPending)
    }
}

/// A known contract available for pre-selection in the Call Contract dropdown.
struct ContractOption: Identifiable {
    let id = UUID()
    let label: String
    let address: String
}

/// Tracks progress of a multi-operation edit submission.
struct SubmissionProgress {
    var isActive: Bool
    var currentStep: String
    var completedSteps: Int
    var totalSteps: Int
    var infoMessages: [String]
}

// MARK: - ContextRuleBuilderViewModel

/// Holds all form state, validation, and bridge calls for the context rule builder screen.
///
/// Sub-views bind to this object via `@ObservedObject`. Bridge calls run in `Task` blocks
/// and results are published back on the `@MainActor`.
@MainActor
final class ContextRuleBuilderViewModel: ObservableObject {

    // MARK: - Rule config

    @Published var ruleName: String = ""
    @Published var contextTypeOption: ContextTypeOption = .defaultType
    @Published var contractAddress: String = ""
    @Published var wasmHashHex: String = ""
    @Published var hasExpiry: Bool = false
    /// Selected ledger offset from the duration dropdown (stored as string).
    @Published var expiryLedger: String = ""
    /// Absolute ledger loaded from chain in edit mode -- read-only display only.
    @Published var existingExpiryLedger: Int64? = nil
    /// True once the user has touched the expiry section after form load in edit mode.
    @Published var expiryModified: Bool = false

    // MARK: - Signer management

    @Published var signers: [SignerItem] = []
    @Published var signerAddMode: SignerAddMode = .delegated
    @Published var delegatedAddress: String = ""
    @Published var ed25519PubKeyHex: String = ""
    @Published var availablePasskeys: [SignerInfoBridge] = []
    @Published var passkeysLoaded: Bool = false
    @Published var isLoadingPasskeys: Bool = false
    @Published var newPasskeyName: String = ""
    @Published var isRegistering: Bool = false

    // MARK: - Policy management

    @Published var policies: [PolicyEntry] = []
    @Published var selectedPolicyIndex: Int = -1
    @Published var thresholdValue: String = ""
    @Published var spendingLimitAmount: String = ""
    @Published var spendingLimitPeriodDays: String = ""
    @Published var weightedThresholdValue: String = ""
    /// Maps signer `identifier` -> weight string for the weighted threshold builder.
    @Published var signerWeights: [String: String] = [:]

    // MARK: - Submission state

    @Published var isSubmitting: Bool = false
    @Published var submissionTxHash: String? = nil
    @Published var submissionSuccess: Bool = false
    @Published var submissionError: String? = nil
    @Published var hasSubmitted: Bool = false

    // MARK: - Edit mode submission state

    @Published var submissionProgress: SubmissionProgress? = nil
    @Published var editResult: EditResult? = nil
    @Published var showSignerPicker: Bool = false

    // MARK: - Create mode multi-signer state

    @Published var showCreateSignerPicker: Bool = false

    // MARK: - Loading / error state

    @Published var isLoadingRule: Bool = false
    @Published var errorMessage: String? = nil
    @Published var fieldErrors: [String: String] = [:]

    // MARK: - Config values

    /// Per-rule signer cap enforced by the smart account contract.
    var maxSigners: Int { Int(OZConstants.shared.MAX_SIGNERS) }
    /// Per-rule policy cap enforced by the smart account contract.
    var maxPolicies: Int { Int(OZConstants.shared.MAX_POLICIES) }
    @Published var ledgersPerHour: Int = 720
    @Published var ledgersPerDay: Int = 17280
    @Published var knownPolicies: [PolicyInfoBridge] = []
    @Published var nativeTokenContract: String = ""
    @Published var demoTokenContractId: String? = nil
    @Published var ed25519VerifierAddress: String = ""
    @Published var webauthnVerifierAddress: String = ""

    // MARK: - Edit mode

    let isEditing: Bool
    let editRuleId: Int32?

    // MARK: - Edit mode original state (snapshot at load time)

    @Published var originalSigners: [SignerItem] = []
    @Published var originalPolicies: [PolicyEntry] = []
    @Published var originalName: String = ""
    /// All signers from all on-chain rules for the "Reuse Signer" picker.
    @Published var allOnChainSigners: [SignerInfoBridge] = []
    /// Available signers for the multi-signer picker.
    @Published var availableSignersForPicker: [SignerInfoBridge] = []
    @Published var signersForPickerLoaded: Bool = false
    /// True when the picker signer fetch failed; distinguishes load-failed from
    /// loaded-empty so the UI can warn that multi-signer routing is unavailable.
    @Published var signersForPickerLoadFailed: Bool = false

    // MARK: - Init

    init(editRuleId: Int32? = nil) {
        self.editRuleId = editRuleId
        self.isEditing = editRuleId != nil
    }

    // MARK: - Computed Properties

    var contractOptions: [ContractOption] {
        var options: [ContractOption] = []
        if !nativeTokenContract.isEmpty {
            options.append(ContractOption(label: "XLM Native Contract", address: nativeTokenContract))
        }
        if let demo = demoTokenContractId, !demo.isEmpty {
            options.append(ContractOption(label: "Demo Token Contract", address: demo))
        }
        return options
    }

    /// Policies not yet attached (available for selection).
    var availablePolicies: [PolicyInfoBridge] {
        knownPolicies.filter { known in
            !policies.contains(where: { $0.policyAddress == known.address })
        }
    }

    /// Currently selected policy info, or nil if none selected.
    var selectedPolicyInfo: PolicyInfoBridge? {
        guard selectedPolicyIndex >= 0, selectedPolicyIndex < availablePolicies.count else {
            return nil
        }
        return availablePolicies[selectedPolicyIndex]
    }

    // MARK: - Expiry Helpers

    func expiryOptions() -> [(String, Int)] {
        [
            ("5 min",   ledgersPerHour / 12),
            ("30 min",  ledgersPerHour / 2),
            ("1 hour",  ledgersPerHour),
            ("1 day",   ledgersPerDay),
            ("10 days", ledgersPerDay * 10)
        ]
    }

    func periodLedgerHint(days: Int) -> String {
        guard days > 0 else { return "The spending limit resets after this period." }
        let ledgers = days * ledgersPerDay
        return "\(days) day(s) = \(ledgers) ledgers"
    }

    // MARK: - Config Loading

    func loadConfig(bridge: MacOSBridge) {
        ledgersPerHour = Int(bridge.getLedgersPerHour())
        ledgersPerDay = Int(bridge.getLedgersPerDay())
        nativeTokenContract = bridge.getNativeTokenContract()
        knownPolicies = KotlinInterop.toArray(bridge.getKnownPolicies(), as: PolicyInfoBridge.self)
        ed25519VerifierAddress = bridge.getEd25519VerifierAddress()
        webauthnVerifierAddress = bridge.getWebauthnVerifierAddress()
    }

    func updateDemoTokenContractId(_ contractId: String?) {
        demoTokenContractId = contractId
    }

    // MARK: - Rule Loading (Edit Mode)

    func loadRuleForEdit(bridge: MacOSBridge) async {
        guard let ruleId = editRuleId else { return }
        isLoadingRule = true
        errorMessage = nil

        do {
            let parsed = try await bridge.loadContextRuleForEdit(ruleId: ruleId)

            switch parsed.contextType {
            case "call_contract":
                contextTypeOption = .callContract
                contractAddress = parsed.contextTypeParam ?? ""
            case "create_contract":
                contextTypeOption = .createContract
                wasmHashHex = parsed.contextTypeParam ?? ""
            default:
                contextTypeOption = .defaultType
            }

            if let validUntil = parsed.validUntil {
                hasExpiry = true
                existingExpiryLedger = validUntil.int64Value
            }

            await populateSignersAndPolicies(from: parsed, ruleId: ruleId, bridge: bridge)

            // Load all signers from all on-chain rules for the "Reuse Signer" picker.
            do {
                let onChainSigners = try await bridge.loadAllOnChainSigners()
                allOnChainSigners = KotlinInterop.toArray(onChainSigners, as: SignerInfoBridge.self)
            } catch {
                // Non-fatal: reuse picker will be empty.
            }

            // Load available signers for multi-signer picker. On failure, single-signer
            // editing stays usable; the load-failed flag drives the warning.
            let pickerLoad = await SignerSelectionSupport.loadAvailableSigners(bridge: bridge)
            if !pickerLoad.loadFailed {
                availableSignersForPicker = pickerLoad.signers
            }
            signersForPickerLoaded = true
            signersForPickerLoadFailed = pickerLoad.loadFailed

        } catch {
            errorMessage = "Failed to load rule #\(ruleId): \(error.localizedDescription)"
        }

        isLoadingRule = false
    }

    /// Reloads the rule from chain after a partial edit failure.
    func reloadRuleFromChain(bridge: MacOSBridge) async {
        guard let ruleId = editRuleId else { return }
        do {
            let parsed = try await bridge.loadContextRuleForEdit(ruleId: ruleId)

            await populateSignersAndPolicies(from: parsed, ruleId: ruleId, bridge: bridge)

            // Refresh the cross-rule signer reuse list so it reflects the post-edit
            // on-chain state (a partially applied edit may have added signers).
            do {
                let onChainSigners = try await bridge.loadAllOnChainSigners()
                allOnChainSigners = KotlinInterop.toArray(onChainSigners, as: SignerInfoBridge.self)
            } catch {
                // Non-fatal: reuse list keeps its previous contents.
            }

            if let validUntil = parsed.validUntil {
                hasExpiry = true
                existingExpiryLedger = validUntil.int64Value
            } else {
                hasExpiry = false
                existingExpiryLedger = nil
            }
            expiryLedger = ""
            expiryModified = false

        } catch {
            errorMessage = "Failed to reload rule: \(error.localizedDescription)"
        }
    }

    /// Populates the name and the signer and policy state (including the original-state
    /// snapshots) from a freshly loaded on-chain rule, reading the on-chain parameters
    /// for each known policy.
    private func populateSignersAndPolicies(
        from parsed: ParsedRuleBridge,
        ruleId: Int32,
        bridge: MacOSBridge
    ) async {
        ruleName = parsed.name
        originalName = parsed.name

        // Populate signer entries with on-chain IDs.
        let loadedSigners = KotlinInterop.toArray(parsed.signers, as: SignerDescriptor.self)
        let loadedSignerIds = KotlinInterop.toIntArray(parsed.signerIds)
        signers = loadedSigners.enumerated().map { (index, desc) in
            SignerItem(
                type: desc.type,
                displayName: displayNameForDescriptor(desc),
                identifier: desc.value,
                onChainId: index < loadedSignerIds.count ? loadedSignerIds[index] : nil,
                isOriginal: true
            )
        }
        originalSigners = signers

        // Populate policy entries with on-chain IDs and read params.
        let loadedPolicies = KotlinInterop.toArray(parsed.policies, as: PolicyDescriptor.self)
        let loadedPolicyIds = KotlinInterop.toIntArray(parsed.policyIds)
        var policyEntries: [PolicyEntry] = []
        for (index, desc) in loadedPolicies.enumerated() {
            let known = knownPolicies.first(where: { $0.address == desc.policyAddress })
            let policyId: Int32? = index < loadedPolicyIds.count ? loadedPolicyIds[index] : nil
            let policyType = known?.type ?? desc.policyType

            // Read on-chain params for known policies.
            var params: PolicyParamsBridge? = nil
            if let knownType = known?.type, policyId != nil {
                params = try? await bridge.readPolicyParams(
                    policyAddress: desc.policyAddress,
                    policyType: knownType,
                    contextRuleId: ruleId
                )
            }

            policyEntries.append(PolicyEntry(
                policyType: policyType,
                policyName: known?.name ?? "Unknown Policy",
                policyAddress: desc.policyAddress,
                label: known?.name ?? "Unknown Policy",
                params: [:],
                onChainId: policyId,
                isOriginal: true,
                modified: false,
                originalParams: params
            ))
        }
        policies = policyEntries
        originalPolicies = policyEntries
    }

    // MARK: - Edit Mode Diff Computation

    /// Computes the diff between original and current form state.
    func computeEditDiff() -> EditDiff? {
        guard isEditing, let ruleId = editRuleId else { return nil }

        let nameChanged = ruleName.trimmingCharacters(in: .whitespaces) != originalName

        // Only entries that are themselves originals can cancel out an original in the
        // removal diff. A staged entry that equals a dropped original (same signer
        // re-added, or a same-type policy whose contract address matches) must NOT
        // suppress the removal — the removal and the add are both submitted, in that
        // order, otherwise the contract rejects the add as a duplicate (3007/3009).

        // Removed signers: in original but not in current.
        let removedSignerIds = originalSigners.filter { orig in
            !signers.contains(where: { $0.isOriginal && $0.identifier == orig.identifier })
        }.compactMap { $0.onChainId }

        // New signers: in current but not original.
        let newSignerDescs = signers.filter { !$0.isOriginal }.map { $0.descriptor }

        // Removed policies: in original but not in current.
        let removedPolicyIds = originalPolicies.filter { orig in
            !policies.contains(where: { $0.isOriginal && $0.policyAddress == orig.policyAddress })
        }.compactMap { $0.onChainId }

        // New policies: in current but not original.
        let newPolicyDescs = policies.filter { !$0.isOriginal }.map { policy in
            PolicyDescriptor(
                policyAddress: policy.policyAddress,
                policyType: policy.policyType,
                params: policy.params
            )
        }

        // Modified policies: original, flagged as modified.
        let modifiedPolicies = policies.filter { $0.isOriginal && $0.modified }
        let modifiedPolicyDescs = modifiedPolicies.map { policy in
            PolicyDescriptor(
                policyAddress: policy.policyAddress,
                policyType: policy.policyType,
                params: policy.params
            )
        }
        let modifiedPolicyIds = modifiedPolicies.compactMap { $0.onChainId }

        return EditDiff(
            ruleId: ruleId,
            nameChanged: nameChanged,
            newName: nameChanged ? ruleName.trimmingCharacters(in: .whitespaces) : nil,
            newSignerDescriptors: newSignerDescs,
            removedSignerIds: removedSignerIds,
            newPolicyDescriptors: newPolicyDescs,
            removedPolicyIds: removedPolicyIds,
            modifiedPolicyDescriptors: modifiedPolicyDescs,
            modifiedPolicyIds: modifiedPolicyIds,
            expiryChanged: expiryModified,
            newExpiry: nil // Resolved at submission time.
        )
    }

    /// Builds an operation summary string from a diff.
    func buildOperationSummary(_ diff: EditDiff) -> String {
        if diff.isEmpty { return "No changes to apply" }
        var parts: [String] = []
        if diff.nameChanged { parts.append("name update") }
        if !diff.newSignerDescriptors.isEmpty {
            parts.append("\(diff.newSignerDescriptors.count) signer(s) to add")
        }
        if !diff.removedSignerIds.isEmpty {
            parts.append("\(diff.removedSignerIds.count) signer(s) to remove")
        }
        if !diff.newPolicyDescriptors.isEmpty {
            parts.append("\(diff.newPolicyDescriptors.count) policy(ies) to add")
        }
        if !diff.removedPolicyIds.isEmpty {
            parts.append("\(diff.removedPolicyIds.count) policy(ies) to remove")
        }
        if !diff.modifiedPolicyDescriptors.isEmpty {
            parts.append("\(diff.modifiedPolicyDescriptors.count) policy(ies) to update")
        }
        if diff.expiryChanged { parts.append("expiry update") }
        return "Pending changes: \(parts.joined(separator: ", ")) " +
            "(\(diff.totalOperations) passkey prompt(s) required)"
    }

    // MARK: - Signer Management

    func addDelegatedSigner() {
        let addr = delegatedAddress.trimmingCharacters(in: .whitespaces)
        if let error = FormValidation.validateAccountId(addr) {
            fieldErrors["delegatedAddress"] = error
            return
        }
        if let dupError = validateNewSigner(identifier: addr) {
            fieldErrors["delegatedAddress"] = dupError
            return
        }
        fieldErrors.removeValue(forKey: "delegatedAddress")
        signers.append(SignerItem(
            type: "delegated",
            displayName: KotlinInterop.truncateAddress(addr, prefixLength: 8, suffixLength: 8),
            identifier: addr
        ))
        delegatedAddress = ""
        signerWeights.removeAll()
    }

    func addEd25519Signer() {
        let hex = ed25519PubKeyHex.trimmingCharacters(in: .whitespaces).lowercased()
        if let error = FormValidation.validateHexString(hex, expectedLength: 32) {
            fieldErrors["ed25519PublicKey"] = error
            return
        }
        if let dupError = validateNewSigner(identifier: hex) {
            fieldErrors["ed25519PublicKey"] = dupError
            return
        }
        fieldErrors.removeValue(forKey: "ed25519PublicKey")
        signers.append(SignerItem(
            type: "ed25519",
            displayName: "\(hex.prefix(8))...",
            identifier: hex
        ))
        ed25519PubKeyHex = ""
        signerWeights.removeAll()
    }

    func registerPasskey(bridge: MacOSBridge) async {
        let name = newPasskeyName.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else {
            fieldErrors["passkeyName"] = "Passkey name is required"
            return
        }
        isRegistering = true
        fieldErrors.removeValue(forKey: "passkeyName")
        fieldErrors.removeValue(forKey: "signers")

        do {
            let signer: ExternalSigner = try await bridge.registerPasskeySigner(name: name)
            let info = convertExternalSignerToInfo(signer, bridge: bridge)
            let credId = info.identifier

            if let dupError = validateNewSigner(identifier: credId) {
                fieldErrors["signers"] = dupError
            } else {
                signers.append(SignerItem(
                    type: "passkey",
                    displayName: info.displayName.isEmpty
                        ? KotlinInterop.truncateAddress(credId, prefixLength: 8, suffixLength: 8)
                        : info.displayName,
                    identifier: credId,
                    isPending: true
                ))
                let newBridge = SignerInfoBridge(
                    type: "passkey",
                    displayName: info.displayName,
                    identifier: credId,
                    canSign: false,
                    keyData: info.keyData,
                    verifierAddress: info.verifierAddress
                )
                availablePasskeys.append(newBridge)
                passkeysLoaded = true
                newPasskeyName = ""
                signerWeights.removeAll()
            }
        } catch {
            let msg = error.localizedDescription
            if bridge.isUserCancellation(message: msg) {
                // Silent cancellation.
            } else {
                fieldErrors["signers"] = "Failed to register passkey: \(msg)"
            }
        }

        isRegistering = false
    }

    func loadAvailablePasskeys(bridge: MacOSBridge) async {
        isLoadingPasskeys = true
        fieldErrors.removeValue(forKey: "signers")
        do {
            let credentialId = bridge.getCredentialId()
            let result = try await bridge.loadAvailablePasskeySigners(excludeCredentialId: credentialId)
            availablePasskeys = KotlinInterop.toArray(result, as: SignerInfoBridge.self)
            passkeysLoaded = true
        } catch {
            fieldErrors["signers"] = "Failed to load passkeys: \(error.localizedDescription)"
        }
        isLoadingPasskeys = false
    }

    func addPasskeyFromList(_ signer: SignerInfoBridge) {
        let credId = signer.identifier
        if let dupError = validateNewSigner(identifier: credId) {
            fieldErrors["signers"] = dupError
            return
        }
        fieldErrors.removeValue(forKey: "signers")
        let display = signer.displayName.isEmpty
            ? KotlinInterop.truncateAddress(credId, prefixLength: 8, suffixLength: 8)
            : signer.displayName
        signers.append(SignerItem(type: "passkey", displayName: display, identifier: credId))
        signerWeights.removeAll()
    }

    /// Adds a signer chosen from the cross-rule reuse list (edit mode).
    ///
    /// Applies the same validation as the other add paths (duplicate detection, signer
    /// cap). The entry is a reused on-chain signer, so it is added with `isPending`
    /// false — no local pending credential exists for it.
    ///
    /// - Returns: `true` when the signer was added, `false` when validation rejected it.
    @discardableResult
    func addSignerFromOnChainList(_ signer: SignerInfoBridge) -> Bool {
        if let dupError = validateNewSigner(identifier: signer.identifier) {
            fieldErrors["signers"] = dupError
            return false
        }
        fieldErrors.removeValue(forKey: "signers")
        signers.append(SignerItem(
            type: signer.type,
            displayName: onChainSignerDisplay(signer),
            identifier: signer.identifier,
            isPending: false
        ))
        signerWeights.removeAll()
        return true
    }

    /// Display text for a cross-rule reuse list entry: the full credential ID for
    /// passkeys, a truncated G-address for delegated signers, and a shortened hex
    /// prefix for Ed25519 keys.
    func onChainSignerDisplay(_ signer: SignerInfoBridge) -> String {
        switch signer.type.lowercased() {
        case "delegated":
            return KotlinInterop.truncateAddress(signer.identifier, prefixLength: 6, suffixLength: 6)
        case "ed25519":
            return "\(signer.identifier.prefix(8))..."
        default:
            return signer.identifier
        }
    }

    func removeSigner(at index: Int) {
        guard index < signers.count else { return }
        let removed = signers.remove(at: index)
        signerWeights.removeValue(forKey: removed.identifier)
    }

    /// Returns true if the given signer is the connected wallet's passkey (cannot be removed).
    func isConnectedWalletSigner(_ signer: SignerItem, credentialId: String?) -> Bool {
        guard signer.isOriginal, signer.type == "passkey" else { return false }
        return credentialId != nil && signer.identifier == credentialId
    }

    func isSignerAlreadyAdded(_ signer: SignerInfoBridge) -> Bool {
        signers.contains(where: { $0.identifier == signer.identifier })
    }

    // MARK: - Policy Management

    func resetPolicyFields() {
        thresholdValue = ""
        spendingLimitAmount = ""
        spendingLimitPeriodDays = ""
        weightedThresholdValue = ""
        signerWeights = [:]
        for key in ["threshold", "spendingAmount", "spendingPeriod",
                    "weightedThreshold", "signerWeights", "policy"] {
            fieldErrors.removeValue(forKey: key)
        }
    }

    func addThresholdPolicy() {
        guard let info = selectedPolicyInfo else {
            fieldErrors["policy"] = "Select a policy type"
            return
        }
        var errors: [String: String] = [:]
        let t = UInt(thresholdValue) ?? 0
        if t == 0 || t > 15 {
            errors["threshold"] = "Must be between 1 and 15"
        } else if !signers.isEmpty && t > UInt(signers.count) {
            errors["threshold"] = "Cannot exceed signer count (\(signers.count))"
        }
        guard errors.isEmpty else {
            errors.forEach { fieldErrors[$0.key] = $0.value }
            return
        }
        policies.append(PolicyEntry(
            policyType: info.type,
            policyName: info.name,
            policyAddress: info.address,
            label: "Threshold: \(t)-of-N",
            params: ["threshold": "\(t)"]
        ))
        thresholdValue = ""
        selectedPolicyIndex = -1
        fieldErrors.removeValue(forKey: "threshold")
    }

    func addSpendingLimitPolicy() {
        guard let info = selectedPolicyInfo else {
            fieldErrors["policy"] = "Select a policy type"
            return
        }
        var errors: [String: String] = [:]
        let amount = spendingLimitAmount.trimmingCharacters(in: .whitespaces)
        if amount.isEmpty || FormValidation.validateAmount(amount) != nil {
            errors["spendingAmount"] = "Must be a positive number"
        }
        let days = Int(spendingLimitPeriodDays) ?? 0
        if days <= 0 {
            errors["spendingPeriod"] = "Must be at least 1 day"
        }
        guard errors.isEmpty else {
            errors.forEach { fieldErrors[$0.key] = $0.value }
            return
        }
        policies.append(PolicyEntry(
            policyType: info.type,
            policyName: info.name,
            policyAddress: info.address,
            label: "Limit: \(amount) / \(days) day(s)",
            params: ["amount": amount, "period_days": "\(days)"]
        ))
        spendingLimitAmount = ""
        spendingLimitPeriodDays = ""
        selectedPolicyIndex = -1
        fieldErrors.removeValue(forKey: "spendingAmount")
        fieldErrors.removeValue(forKey: "spendingPeriod")
    }

    func addWeightedThresholdPolicy() {
        guard let info = selectedPolicyInfo else {
            fieldErrors["policy"] = "Select a policy type"
            return
        }
        var errors: [String: String] = [:]
        let threshold = UInt(weightedThresholdValue) ?? 0
        if threshold == 0 {
            errors["weightedThreshold"] = "Must be at least 1"
        }
        if signers.isEmpty {
            errors["signerWeights"] = "Add signers before configuring weights"
        } else {
            var totalWeight: UInt = 0
            var allHaveWeights = true
            for signer in signers {
                let w = UInt(signerWeights[signer.identifier] ?? "") ?? 0
                if w == 0 { allHaveWeights = false; break }
                totalWeight += w
            }
            if !allHaveWeights {
                errors["signerWeights"] = "All signers must have a weight >= 1"
            } else if threshold > 0 && totalWeight < threshold {
                errors["signerWeights"] = "Total weight (\(totalWeight)) must be >= threshold (\(threshold))"
            }
        }
        guard errors.isEmpty else {
            errors.forEach { fieldErrors[$0.key] = $0.value }
            return
        }
        let weightsEncoded = signers.map { s in
            "\(s.identifier):\(signerWeights[s.identifier] ?? "0")"
        }.joined(separator: ",")
        let weightsDesc = signers.map { s in
            "\(Self.signerDisplayType(s.type))=\(signerWeights[s.identifier] ?? "0")"
        }.joined(separator: ", ")
        policies.append(PolicyEntry(
            policyType: info.type,
            policyName: info.name,
            policyAddress: info.address,
            label: "Weighted: threshold=\(threshold) (\(weightsDesc))",
            params: ["threshold": "\(threshold)", "weights": weightsEncoded]
        ))
        weightedThresholdValue = ""
        signerWeights = [:]
        selectedPolicyIndex = -1
        fieldErrors.removeValue(forKey: "weightedThreshold")
        fieldErrors.removeValue(forKey: "signerWeights")
    }

    func removePolicy(at index: Int) {
        guard index < policies.count else { return }
        policies.remove(at: index)
    }

    /// Updates an existing policy's parameters. The entry is marked modified only when the
    /// edited values differ from the on-chain parameters; reverting to the original values
    /// clears the modified flag and pending parameters.
    func updatePolicyParams(at index: Int, params: [String: String], label: String, modified: Bool) {
        guard index < policies.count, policies[index].isOriginal else { return }
        let updated = policies[index]
        policies[index] = PolicyEntry(
            policyType: updated.policyType,
            policyName: updated.policyName,
            policyAddress: updated.policyAddress,
            label: label,
            params: modified ? params : [:],
            onChainId: updated.onChainId,
            isOriginal: true,
            modified: modified,
            originalParams: updated.originalParams
        )
    }

    // MARK: - Edit Mode: Policy Parameter Editing

    /// Rebuilds a threshold policy entry when the edited value changes.
    /// Only updates the entry when the value is valid (1 to 15).
    func updateThresholdPolicyEntry(onChainId: Int32, thresholdStr: String) {
        guard let index = policies.firstIndex(where: { $0.isOriginal && $0.onChainId == onChainId }),
              let original = policies[index].originalParams else { return }
        guard let t = Int(thresholdStr), t >= 1, t <= 15 else { return }

        let changed = original.threshold?.intValue != t
        updatePolicyParams(
            at: index,
            params: ["threshold": "\(t)"],
            label: "Threshold: \(t)-of-N",
            modified: changed
        )
    }

    /// Rebuilds a spending limit policy entry when amount or period changes.
    /// Only updates the entry when both values are valid (positive amount, period >= 1 day).
    func updateSpendingLimitPolicyEntry(onChainId: Int32, amountStr: String, periodDaysStr: String) {
        guard let index = policies.firstIndex(where: { $0.isOriginal && $0.onChainId == onChainId }),
              let original = policies[index].originalParams else { return }
        let days = Int(periodDaysStr) ?? 0
        guard !amountStr.isEmpty, FormValidation.validateAmount(amountStr) == nil, days > 0 else { return }

        let amountChanged = amountStr != original.spendingLimit
        let periodChanged = days != original.periodDays?.intValue
        let changed = amountChanged || periodChanged
        updatePolicyParams(
            at: index,
            params: ["amount": amountStr, "period_days": "\(days)"],
            label: "Limit: \(amountStr) / \(days) day(s)",
            modified: changed
        )
    }

    /// Rebuilds a weighted threshold policy entry when the threshold or any signer weight
    /// changes. Only updates the entry when all values are valid (threshold > 0, all signers
    /// have weight > 0, total weight >= threshold). Marks the entry as modified only when
    /// values differ from the on-chain parameters.
    func updateWeightedThresholdPolicyEntry(onChainId: Int32, thresholdStr: String) {
        guard let index = policies.firstIndex(where: { $0.isOriginal && $0.onChainId == onChainId }),
              let original = policies[index].originalParams else { return }
        guard let threshold = Int(thresholdStr), threshold > 0 else { return }
        guard !signers.isEmpty else { return }

        // Validate all signers have valid weights and compute the total.
        var weights: [(signer: SignerItem, weight: Int)] = []
        var totalWeight = 0
        for signer in signers {
            guard let w = Int(signerWeights[signer.identifier] ?? ""), w > 0 else { return }
            weights.append((signer, w))
            totalWeight += w
        }
        guard totalWeight >= threshold else { return }

        // Determine if anything changed from the on-chain values.
        let thresholdChanged = original.threshold?.intValue != threshold
        let weightsChanged: Bool = original.signerWeights == nil
            || weights.contains { onChainWeight(for: $0.signer, in: original) != $0.weight }
        let changed = thresholdChanged || weightsChanged

        let weightsEncoded = weights.map { "\($0.signer.identifier):\($0.weight)" }
            .joined(separator: ",")
        let weightsDesc = weights.map { "\(Self.signerDisplayType($0.signer.type))=\($0.weight)" }
            .joined(separator: ", ")
        updatePolicyParams(
            at: index,
            params: ["threshold": "\(threshold)", "weights": weightsEncoded],
            label: "Weighted: threshold=\(threshold) (\(weightsDesc))",
            modified: changed
        )
    }

    /// Resolves the on-chain weight for a signer item from weighted threshold params.
    ///
    /// On-chain weight keys use the signer-key format: "delegated:<G-address>" for delegated
    /// signers and "external:<verifierAddress>:<keyDataHex>" for external signers. Ed25519
    /// items are matched by their public key hex; passkey items by the credential ID bytes
    /// at the end of the key data.
    func onChainWeight(for signer: SignerItem, in params: PolicyParamsBridge) -> Int? {
        guard let weights = params.signerWeights else { return nil }
        switch signer.type {
        case "delegated":
            return weights["delegated:\(signer.identifier)"]?.intValue
        case "ed25519":
            return weights["external:\(ed25519VerifierAddress):\(signer.identifier.lowercased())"]?.intValue
        case "passkey":
            guard let credIdHex = Self.base64UrlToHex(signer.identifier) else { return nil }
            let prefix = "external:\(webauthnVerifierAddress):"
            return weights.first(where: { $0.key.hasPrefix(prefix) && $0.key.hasSuffix(credIdHex) })?
                .value.intValue
        default:
            return nil
        }
    }

    /// Decodes a Base64URL string and returns the lowercase hex encoding of its bytes.
    private static func base64UrlToHex(_ base64Url: String) -> String? {
        var base64 = base64Url
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder > 0 {
            base64.append(String(repeating: "=", count: 4 - remainder))
        }
        guard let data = Data(base64Encoded: base64) else { return nil }
        return data.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Validation

    @discardableResult
    func validateForm() -> Bool {
        var errors: [String: String] = [:]

        let trimmedName = ruleName.trimmingCharacters(in: .whitespaces)
        if trimmedName.isEmpty {
            errors["ruleName"] = "Rule name is required"
        }

        if isEditing {
            // Edit mode validates the resulting rule state: the context type is
            // read-only, but signer and policy edits must leave a rule the contract
            // accepts (at least one member, within the per-rule caps, no duplicates).
            if signers.isEmpty && policies.isEmpty {
                errors["signers"] = "At least one signer or policy must remain"
            }

            if signers.count > maxSigners {
                errors["signers"] = "Maximum \(maxSigners) signers allowed"
            }

            if policies.count > maxPolicies {
                errors["policies"] = "Maximum \(maxPolicies) policies allowed"
            }

            // Pairwise duplicate scan; signer identity is type + identifier.
            for i in signers.indices {
                for j in signers.indices where j > i {
                    if signers[i].type == signers[j].type
                        && signers[i].identifier == signers[j].identifier {
                        errors["signers"] = "Duplicate signers detected"
                    }
                }
            }

            // Expiry validation only when the user touched the expiry section.
            if expiryModified && hasExpiry {
                if expiryLedger.isEmpty {
                    errors["expiryLedger"] = "Please select an expiry duration"
                } else if (Int(expiryLedger) ?? 0) <= 0 {
                    errors["expiryLedger"] = "Must be a positive integer"
                }
            }
        } else {
            switch contextTypeOption {
            case .callContract:
                if contractAddress.isEmpty {
                    errors["contractAddress"] = "A contract must be selected"
                }
            case .createContract:
                let hex = wasmHashHex.trimmingCharacters(in: .whitespaces).lowercased()
                if let hexError = FormValidation.validateHexString(hex, expectedLength: 32) {
                    errors["wasmHash"] = hexError
                }
            case .defaultType:
                break
            }

            if hasExpiry {
                if expiryLedger.isEmpty {
                    errors["expiryLedger"] = "Please select an expiry duration"
                } else if (Int(expiryLedger) ?? 0) <= 0 {
                    errors["expiryLedger"] = "Must be a positive integer"
                }
            }

            if signers.isEmpty {
                errors["signers"] = "At least one signer is required"
            }
        }

        fieldErrors = errors
        if !errors.isEmpty {
            errorMessage = "Please fix the validation errors above."
        }
        return errors.isEmpty
    }

    // MARK: - Submission

    func submit(bridge: MacOSBridge) async {
        guard !isSubmitting else { return }
        guard validateForm() else { return }
        fieldErrors = [:]
        errorMessage = nil
        isSubmitting = true
        hasSubmitted = false

        do {
            if isEditing {
                try await submitEdit(bridge: bridge)
            } else {
                try await submitCreate(bridge: bridge)
            }
        } catch let submissionErr as SubmissionError {
            setSubmissionFailure(submissionErr.errorDescription)
        } catch {
            setSubmissionFailure(error.localizedDescription)
        }

        isSubmitting = false
    }

    /// Records a failed create-mode submission for the result card.
    private func setSubmissionFailure(_ message: String?) {
        submissionSuccess = false
        submissionTxHash = nil
        submissionError = message
        hasSubmitted = true
    }

    // MARK: - Edit Submission (multi-signer aware)

    /// Called from the submit button or after signer picker confirms.
    func submitEditWithSigners(
        bridge: MacOSBridge,
        selectedSigners: [SignerInfoBridge],
        delegatedSecretKeys: [String: String],
        ed25519SecretKeys: [String: String]
    ) async {
        guard let diff = computeEditDiff() else { return }
        guard !diff.isEmpty else { return }

        isSubmitting = true
        errorMessage = nil
        editResult = nil
        // Clear any previous attempt's progress so an error thrown before the first
        // operation reports zero completed operations.
        submissionProgress = nil

        do {
            // Resolve expiry if changed.
            var resolvedDiff = diff
            if diff.expiryChanged {
                if hasExpiry {
                    // This method is also reached from the picker confirmation without a
                    // revalidation pass. An unparsable duration must fail here: falling
                    // through would send a nil expiry, which the bridge interprets as
                    // "remove expiry" — the opposite of the user's intent.
                    guard let offset = Int32(expiryLedger), offset > 0 else {
                        editResult = EditResult(
                            success: false,
                            completedOperations: 0,
                            totalOperations: diff.totalOperations,
                            partialDueToAuthGuard: false,
                            authGuardMessage: nil,
                            error: "Please select an expiry duration",
                            failedStep: nil,
                            transactionHashes: []
                        )
                        isSubmitting = false
                        return
                    }
                    let absoluteLedgerKt = try await bridge.resolveAbsoluteLedger(offset: offset)
                    resolvedDiff = diff.withResolvedExpiry(absoluteLedgerKt.int32Value)
                } else {
                    resolvedDiff = diff.withResolvedExpiry(nil)
                }
            }

            let totalOps = resolvedDiff.totalOperations
            submissionProgress = SubmissionProgress(
                isActive: true,
                currentStep: "Starting...",
                completedSteps: 0,
                totalSteps: totalOps,
                infoMessages: []
            )

            // Build bridge diff.
            let bridgeDiff = ContextRuleEditDiffBridge(
                ruleId: resolvedDiff.ruleId,
                nameChanged: resolvedDiff.nameChanged,
                newName: resolvedDiff.newName,
                newSignerDescriptors: resolvedDiff.newSignerDescriptors,
                removedSignerIds: resolvedDiff.removedSignerIds.map { KotlinInt(value: $0) },
                newPolicyDescriptors: resolvedDiff.newPolicyDescriptors,
                removedPolicyIds: resolvedDiff.removedPolicyIds.map { KotlinInt(value: $0) },
                modifiedPolicyDescriptors: resolvedDiff.modifiedPolicyDescriptors,
                modifiedPolicyIds: resolvedDiff.modifiedPolicyIds.map { KotlinInt(value: $0) },
                expiryChanged: resolvedDiff.expiryChanged,
                newExpiry: resolvedDiff.newExpiry.map { KotlinInt(value: $0) }
            )

            // Build signer descriptors for multi-signer auth.
            // isPending only describes newly registered passkeys in the edit diff;
            // auth signers are existing on-chain signers, so it is always false here.
            let signerDescs = selectedSigners.map { info in
                SignerDescriptor(type: info.type, value: info.identifier, isPending: false)
            }

            var stepCount = 0
            let result = try await bridge.submitContextRuleEdits(
                editDiff: bridgeDiff,
                signerDescriptors: signerDescs,
                delegatedSecretKeys: delegatedSecretKeys,
                ed25519SecretKeys: ed25519SecretKeys,
                onProgress: { [weak self] msg in
                    Task { @MainActor in
                        stepCount += 1
                        self?.submissionProgress = SubmissionProgress(
                            isActive: true,
                            currentStep: msg,
                            completedSteps: stepCount - 1,
                            totalSteps: totalOps,
                            infoMessages: self?.submissionProgress?.infoMessages ?? []
                        )
                    }
                }
            )

            submissionProgress?.isActive = false

            editResult = EditResult(
                success: result.success,
                completedOperations: Int(result.completedOperations),
                totalOperations: Int(result.totalOperations),
                partialDueToAuthGuard: result.partialDueToAuthGuard,
                authGuardMessage: result.authGuardMessage,
                error: result.error,
                failedStep: result.failedStep,
                transactionHashes: (result.transactionHashes as? [String]) ?? []
            )

            if result.success && !result.partialDueToAuthGuard {
                // Full success -- view will dismiss.
            } else {
                // Partial or failure -- reload from chain.
                await reloadRuleFromChain(bridge: bridge)
            }

        } catch {
            // Thrown errors abort the sequence without a flow result. The rule is reloaded
            // only for flow-returned failures above, where the on-chain state may have
            // partially advanced.
            submissionProgress?.isActive = false
            let msg = error.localizedDescription
            if bridge.isUserCancellation(message: msg) {
                editResult = EditResult(
                    success: false,
                    completedOperations: submissionProgress?.completedSteps ?? 0,
                    totalOperations: diff.totalOperations,
                    partialDueToAuthGuard: false,
                    authGuardMessage: nil,
                    error: "Passkey authentication cancelled",
                    failedStep: nil,
                    transactionHashes: []
                )
                ActivityLogState.shared.info(message: "Edit cancelled by user")
            } else {
                errorMessage = msg
                ActivityLogState.shared.error(message: "Edit failed: \(msg)")
            }
        }

        isSubmitting = false
    }

    // MARK: - Private Submission Helpers

    private func submitEdit(bridge: MacOSBridge) async throws {
        guard let diff = computeEditDiff() else { return }
        if diff.isEmpty {
            throw SubmissionError.operationFailed("No changes to apply")
        }

        // Check if multi-signer auth is needed, based on the original (on-chain) signers.
        // Single-signer when exactly one on-chain signer AND it is the connected passkey.
        // The picker opens only when the rule additionally has more than one on-chain
        // signer: zero-signer (policy-only) rules and single-non-connected-signer rules
        // submit directly through the single-signer path with an empty selection.
        let connectedCredId = bridge.getCredentialId()
        let isSinglePasskey = originalSigners.count == 1
            && originalSigners[0].type == "passkey"
            && connectedCredId != nil
            && originalSigners[0].identifier == connectedCredId

        if !isSinglePasskey && originalSigners.count > 1 {
            // Multi-signer: show signer picker. Submission happens via submitEditWithSigners().
            // An empty picker pool after a failed signer load would present a sheet whose
            // Confirm can never be enabled; surface an error instead.
            if availableSignersForPicker.isEmpty {
                errorMessage = "Could not load signers for multi-signer authorization. " +
                    "Close and reopen this screen to retry."
                isSubmitting = false
                return
            }
            showSignerPicker = true
            isSubmitting = false
            return
        }

        // Single-signer path: submit directly with empty signers list.
        await submitEditWithSigners(
            bridge: bridge,
            selectedSigners: [],
            delegatedSecretKeys: [:],
            ed25519SecretKeys: [:]
        )
    }

    private func submitCreate(bridge: MacOSBridge) async throws {
        // Check if multi-signer authorization is needed.
        let isSinglePasskey = !(signersForPickerLoaded && availableSignersForPicker.count > 1)

        if !isSinglePasskey {
            // Multi-signer: show signer picker. Submission happens via submitCreateWithSigners().
            showCreateSignerPicker = true
            isSubmitting = false
            return
        }

        // Single-signer path: submit directly
        try await performCreateSubmission(bridge: bridge, signerDescs: [], secretKeys: [:], ed25519Secrets: [:])
    }

    /// Called from the create-mode signer picker after user confirms signer selection.
    func submitCreateWithSigners(
        bridge: MacOSBridge,
        selectedSigners: [SignerInfoBridge],
        delegatedSecretKeys: [String: String],
        ed25519SecretKeys: [String: String]
    ) async {
        isSubmitting = true
        errorMessage = nil

        do {
            // Auth signers are existing on-chain signers, so isPending is always false here.
            let signerDescs = selectedSigners.map { info in
                SignerDescriptor(type: info.type, value: info.identifier, isPending: false)
            }
            try await performCreateSubmission(
                bridge: bridge,
                signerDescs: signerDescs,
                secretKeys: delegatedSecretKeys,
                ed25519Secrets: ed25519SecretKeys
            )
        } catch let submissionErr as SubmissionError {
            setSubmissionFailure(submissionErr.errorDescription)
        } catch {
            let msg = error.localizedDescription
            if bridge.isUserCancellation(message: msg) {
                setSubmissionFailure("Passkey authentication cancelled")
            } else {
                setSubmissionFailure(msg)
            }
        }

        isSubmitting = false
    }

    /// Shared create-mode submission logic used by both single-signer and multi-signer paths.
    private func performCreateSubmission(
        bridge: MacOSBridge,
        signerDescs: [SignerDescriptor],
        secretKeys: [String: String],
        ed25519Secrets: [String: String]
    ) async throws {
        let contextTypeName: String
        let contextTypeParam: String?
        switch contextTypeOption {
        case .defaultType:
            contextTypeName = "default"
            contextTypeParam = nil
        case .callContract:
            contextTypeName = "call_contract"
            contextTypeParam = contractAddress.trimmingCharacters(in: .whitespaces)
        case .createContract:
            contextTypeName = "create_contract"
            contextTypeParam = wasmHashHex.trimmingCharacters(in: .whitespaces).lowercased()
        }

        let offsetInt32: KotlinInt?
        if hasExpiry, let offset = Int32(expiryLedger) {
            offsetInt32 = KotlinInt(value: offset)
        } else {
            offsetInt32 = nil
        }

        let signerDescriptors: [SignerDescriptor] = signers.map { $0.descriptor }
        let policyDescriptors: [PolicyDescriptor] = policies.map { policy in
            PolicyDescriptor(
                policyAddress: policy.policyAddress,
                policyType: policy.policyType,
                params: policy.params
            )
        }

        let result = try await bridge.addContextRule(
            contextTypeName: contextTypeName,
            contextTypeParam: contextTypeParam,
            name: ruleName.trimmingCharacters(in: .whitespaces),
            validUntilOffset: offsetInt32,
            signerDescriptors: signerDescriptors,
            policyDescriptors: policyDescriptors,
            signerDescriptorsForAuth: signerDescs,
            delegatedSecretKeysForAuth: secretKeys,
            ed25519SecretKeys: ed25519Secrets
        )

        submissionSuccess = result.success
        submissionTxHash = result.hash
        submissionError = result.error
        hasSubmitted = true

        if !result.success {
            throw SubmissionError.operationFailed(result.error ?? "Unknown error")
        }
    }

    // MARK: - Private Helpers

    private func validateNewSigner(identifier: String) -> String? {
        if signers.count >= maxSigners {
            return "Maximum \(maxSigners) signers allowed"
        }
        if signers.contains(where: { $0.identifier == identifier }) {
            return "This signer is already added"
        }
        return nil
    }

    private func displayNameForDescriptor(_ desc: SignerDescriptor) -> String {
        switch desc.type.lowercased() {
        case "delegated":
            return KotlinInterop.truncateAddress(desc.value, prefixLength: 8, suffixLength: 8)
        case "ed25519":
            return desc.value.count > 16 ? "\(desc.value.prefix(8))..." : desc.value
        case "passkey":
            return KotlinInterop.truncateAddress(desc.value, prefixLength: 8, suffixLength: 8)
        default:
            return KotlinInterop.truncateAddress(desc.value, prefixLength: 8, suffixLength: 8)
        }
    }

    /// Converts an `ExternalSigner` returned by `registerPasskeySigner` to a `SignerInfoBridge`.
    private func convertExternalSignerToInfo(
        _ signer: ExternalSigner,
        bridge: MacOSBridge
    ) -> SignerInfoBridge {
        let credentialId = bridge.getCredentialIdFromSigner(signer: signer) ?? ""
        let keyDataHex = KotlinInterop.hexString(from: signer.keyData)
        let displayText = newPasskeyName.trimmingCharacters(in: .whitespaces)
        return SignerInfoBridge(
            type: "passkey",
            displayName: displayText.isEmpty ? "Passkey (\(credentialId.prefix(8))...)" : displayText,
            identifier: credentialId,
            canSign: true,
            keyData: keyDataHex,
            verifierAddress: signer.verifierAddress
        )
    }

    // MARK: - Static Helpers

    /// Display type label for a signer type tag, used in weighted-threshold policy labels.
    static func signerDisplayType(_ type: String) -> String {
        switch type.lowercased() {
        case "delegated": return "G-Address"
        case "passkey":   return "Passkey"
        case "ed25519":   return "Ed25519"
        default:          return "External"
        }
    }

    static func policyColor(for type: String) -> Color {
        switch type {
        case "threshold":          return Material3Colors.policyThreshold
        case "spending_limit":     return Material3Colors.policySpendingLimit
        case "weighted_threshold": return Material3Colors.policyWeightedThreshold
        default:                   return Material3Colors.signerDefault
        }
    }
}

// MARK: - Edit Result Type

/// Mirrors `ContextRuleEditResult` for Swift display.
struct EditResult {
    let success: Bool
    let completedOperations: Int
    let totalOperations: Int
    let partialDueToAuthGuard: Bool
    let authGuardMessage: String?
    let error: String?
    let failedStep: String?
    let transactionHashes: [String]
}

// MARK: - Edit Diff Type

/// Swift-side representation of the edit diff, used for UI display and bridge conversion.
struct EditDiff {
    let ruleId: Int32
    let nameChanged: Bool
    let newName: String?
    let newSignerDescriptors: [SignerDescriptor]
    let removedSignerIds: [Int32]
    let newPolicyDescriptors: [PolicyDescriptor]
    let removedPolicyIds: [Int32]
    let modifiedPolicyDescriptors: [PolicyDescriptor]
    let modifiedPolicyIds: [Int32]
    let expiryChanged: Bool
    let newExpiry: Int32?

    var isEmpty: Bool {
        !nameChanged && newSignerDescriptors.isEmpty &&
        removedSignerIds.isEmpty && newPolicyDescriptors.isEmpty &&
        removedPolicyIds.isEmpty && modifiedPolicyDescriptors.isEmpty && !expiryChanged
    }

    var totalOperations: Int {
        var count = 0
        if nameChanged { count += 1 }
        count += newSignerDescriptors.count
        count += removedSignerIds.count
        count += removedPolicyIds.count
        // Simple threshold uses execute() for set_threshold (1 op),
        // other policy types use remove + re-add (2 ops).
        count += modifiedPolicyDescriptors.reduce(0) { total, desc in
            total + (desc.policyType == "threshold" ? 1 : 2)
        }
        count += newPolicyDescriptors.count
        if expiryChanged { count += 1 }
        return count
    }

    func withResolvedExpiry(_ absoluteLedger: Int32?) -> EditDiff {
        EditDiff(
            ruleId: ruleId,
            nameChanged: nameChanged,
            newName: newName,
            newSignerDescriptors: newSignerDescriptors,
            removedSignerIds: removedSignerIds,
            newPolicyDescriptors: newPolicyDescriptors,
            removedPolicyIds: removedPolicyIds,
            modifiedPolicyDescriptors: modifiedPolicyDescriptors,
            modifiedPolicyIds: modifiedPolicyIds,
            expiryChanged: expiryChanged,
            newExpiry: absoluteLedger
        )
    }
}

// MARK: - Errors

private enum SubmissionError: LocalizedError {
    case operationFailed(String)

    var errorDescription: String? {
        switch self { case .operationFailed(let msg): return msg }
    }
}
