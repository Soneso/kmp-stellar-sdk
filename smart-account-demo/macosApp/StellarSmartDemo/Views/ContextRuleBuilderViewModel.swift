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

    var descriptor: SignerDescriptor {
        SignerDescriptor(type: type, value: identifier)
    }
}

/// A known contract available for pre-selection in the Call Contract dropdown.
struct ContractOption: Identifiable {
    let id = UUID()
    let label: String
    let address: String
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
    /// Absolute ledger loaded from chain in edit mode — read-only display only.
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

    // MARK: - Loading / error state

    @Published var isLoadingRule: Bool = false
    @Published var errorMessage: String? = nil
    @Published var fieldErrors: [String: String] = [:]

    // MARK: - Config values

    @Published var maxSigners: Int = 10
    @Published var maxPolicies: Int = 5
    @Published var ledgersPerHour: Int = 720
    @Published var ledgersPerDay: Int = 17280
    @Published var knownPolicies: [PolicyInfoBridge] = []
    @Published var nativeTokenContract: String = ""
    @Published var demoTokenContractId: String? = nil

    // MARK: - Edit mode

    let isEditing: Bool
    let editRuleId: Int32?

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

    var selectedExpiryLabel: String {
        guard let offset = Int(expiryLedger), offset > 0 else { return "Select duration..." }
        return expiryOptions().first(where: { $0.1 == offset })?.0 ?? "Custom"
    }

    func periodLedgerHint(days: Int) -> String {
        guard days > 0 else { return "The spending limit resets after this period." }
        let ledgers = days * ledgersPerDay
        return "\(days) day(s) = \(ledgers) ledgers"
    }

    // MARK: - Config Loading

    func loadConfig(bridge: MacOSBridge) {
        maxSigners = Int(bridge.getMaxSigners())
        maxPolicies = Int(bridge.getMaxPolicies())
        ledgersPerHour = Int(bridge.getLedgersPerHour())
        ledgersPerDay = Int(bridge.getLedgersPerDay())
        nativeTokenContract = bridge.getNativeTokenContract()
        knownPolicies = KotlinInterop.toArray(bridge.getKnownPolicies(), as: PolicyInfoBridge.self)
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
            ruleName = parsed.name

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

            let loadedSigners = KotlinInterop.toArray(parsed.signers, as: SignerDescriptor.self)
            signers = loadedSigners.map { desc in
                SignerItem(
                    type: desc.type,
                    displayName: displayNameForDescriptor(desc),
                    identifier: desc.value
                )
            }

            let loadedPolicies = KotlinInterop.toArray(parsed.policies, as: PolicyDescriptor.self)
            policies = loadedPolicies.map { desc in
                let known = knownPolicies.first(where: { $0.address == desc.policyAddress })
                return PolicyEntry(
                    policyType: known?.type ?? desc.policyType,
                    policyName: known?.name ?? "Unknown Policy",
                    policyAddress: desc.policyAddress,
                    label: known?.name ?? "Unknown Policy",
                    params: [:]
                )
            }
        } catch {
            errorMessage = "Failed to load rule #\(ruleId): \(error.localizedDescription)"
        }

        isLoadingRule = false
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
        // Reset signer weights when signer list changes.
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
            // Extract credential ID from keyData via the bridge helper.
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
                    identifier: credId
                ))
                // Add to available list so "already added" state is shown immediately.
                let newBridge = SignerInfoBridge(
                    type: "passkey",
                    displayName: info.displayName,
                    identifier: credId,
                    canSign: false,
                    keyData: info.keyData
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

    func removeSigner(at index: Int) {
        guard index < signers.count else { return }
        let removed = signers.remove(at: index)
        signerWeights.removeValue(forKey: removed.identifier)
    }

    func isPasskeyAlreadyAdded(_ signer: SignerInfoBridge) -> Bool {
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
            "\(s.type)=\(signerWeights[s.identifier] ?? "0")"
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

    // MARK: - Validation

    @discardableResult
    func validateForm() -> Bool {
        var errors: [String: String] = [:]

        let trimmedName = ruleName.trimmingCharacters(in: .whitespaces)
        if trimmedName.isEmpty {
            errors["ruleName"] = "Rule name is required"
        }

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

        // Skip expiry validation in edit mode when the user has not touched the expiry section.
        if hasExpiry && !(isEditing && !expiryModified) {
            if expiryLedger.isEmpty {
                errors["expiryLedger"] = "Please select an expiry duration"
            } else if (Int(expiryLedger) ?? 0) <= 0 {
                errors["expiryLedger"] = "Must be a positive integer"
            }
        }

        if !isEditing && signers.isEmpty {
            errors["signers"] = "At least one signer is required"
        }

        fieldErrors = errors
        if !errors.isEmpty {
            errorMessage = "Please fix the validation errors above."
        }
        return errors.isEmpty
    }

    // MARK: - Submission

    func submit(bridge: MacOSBridge) async {
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
            submissionSuccess = false
            submissionTxHash = nil
            submissionError = submissionErr.errorDescription
            hasSubmitted = true
        } catch {
            submissionSuccess = false
            submissionTxHash = nil
            submissionError = error.localizedDescription
            hasSubmitted = true
        }

        isSubmitting = false
    }

    // MARK: - Private Submission Helpers

    private func submitEdit(bridge: MacOSBridge) async throws {
        guard let ruleId = editRuleId else { return }
        let trimmedName = ruleName.trimmingCharacters(in: .whitespaces)

        let nameResult = try await bridge.updateContextRuleName(ruleId: ruleId, name: trimmedName)
        guard nameResult.success else {
            throw SubmissionError.operationFailed(
                "Failed to update name: \(nameResult.error ?? "Unknown error")"
            )
        }

        if expiryModified {
            let offsetInt32: KotlinInt?
            if hasExpiry, let offset = Int32(expiryLedger) {
                offsetInt32 = KotlinInt(value: offset)
            } else {
                offsetInt32 = nil
            }
            let validUntilResult = try await bridge.updateContextRuleValidUntil(
                ruleId: ruleId,
                validUntilOffset: offsetInt32
            )
            guard validUntilResult.success else {
                throw SubmissionError.operationFailed(
                    "Name updated but failed to update expiry: \(validUntilResult.error ?? "Unknown error")"
                )
            }
            submissionSuccess = true
            submissionTxHash = validUntilResult.hash
            submissionError = nil
        } else {
            submissionSuccess = true
            submissionTxHash = nameResult.hash
            submissionError = nil
        }
        hasSubmitted = true
    }

    private func submitCreate(bridge: MacOSBridge) async throws {
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
            policyDescriptors: policyDescriptors
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
    ///
    /// The bridge's `loadAvailablePasskeySigners` does this conversion server-side; for a freshly
    /// registered signer that is not yet on any context rule, we do it locally by reading keyData.
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
            keyData: keyDataHex
        )
    }

    // MARK: - Static Helpers

    static func policyColor(for type: String) -> Color {
        switch type {
        case "threshold":          return Material3Colors.policyThreshold
        case "spending_limit":     return Material3Colors.policySpendingLimit
        case "weighted_threshold": return Material3Colors.policyWeightedThreshold
        default:                   return Material3Colors.signerDefault
        }
    }
}

// MARK: - Errors

private enum SubmissionError: LocalizedError {
    case operationFailed(String)

    var errorDescription: String? {
        switch self { case .operationFailed(let msg): return msg }
    }
}
