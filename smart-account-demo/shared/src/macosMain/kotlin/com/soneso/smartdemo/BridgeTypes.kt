//
//  BridgeTypes.kt
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.smartdemo

/**
 * Swift-friendly activity log entry.
 *
 * Replaces [com.soneso.smartdemo.state.LogEntry] for Swift interop by avoiding Kotlin-specific
 * types that generate awkward Swift wrappers: [kotlinx.datetime.Instant] is replaced with epoch
 * millis ([Long]), and the [com.soneso.smartdemo.state.LogLevel] enum is replaced with a plain
 * [String] constant so Swift can compare values without importing the enum.
 *
 * @property message Human-readable log message.
 * @property level Severity level: one of "INFO", "SUCCESS", or "ERROR".
 * @property timestampMs UNIX epoch timestamp in milliseconds.
 */
data class ActivityLogEntryBridge(
    val message: String,
    val level: String,
    val timestampMs: Long
)

/**
 * Swift-friendly policy descriptor for passing known policy configurations from Swift.
 *
 * Mirrors [com.soneso.smartdemo.config.PolicyInfo] but is used as a bridge type so Swift
 * code does not need to import the config package directly.
 *
 * @property type Short policy type identifier (e.g., "threshold", "spending_limit", "weighted_threshold").
 * @property name Display name shown in the UI.
 * @property description Human-readable description of what the policy enforces.
 * @property address On-chain contract address (C-address) for this policy.
 */
data class PolicyInfoBridge(
    val type: String,
    val name: String,
    val description: String,
    val address: String
)

/**
 * Describes a signer to be constructed or looked up from Swift.
 *
 * Used in [MacOSBridge.addContextRule] and [MacOSBridge.multiSignerTransfer] to pass signer
 * configuration without exposing [com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner]
 * sealed class variants to Swift.
 *
 * @property type Signer type: "delegated", "ed25519", or "passkey".
 * @property value Type-specific value:
 *   - "delegated": the G-address of the Stellar account.
 *   - "ed25519": lowercase hex-encoded 32-byte public key.
 *   - "passkey": Base64URL-encoded credential ID (used to look up the full signer from context rules).
 */
data class SignerDescriptor(
    val type: String,
    val value: String
)

/**
 * Describes a policy entry to be built from Swift.
 *
 * Used in [MacOSBridge.addContextRule] so Swift can pass typed policy configurations
 * without needing to construct [com.soneso.stellar.sdk.xdr.SCValXdr] instances.
 *
 * @property policyAddress On-chain policy contract address (C-address).
 * @property policyType Short type identifier: "threshold", "spending_limit", or "weighted_threshold".
 * @property params Type-specific parameters encoded as String pairs, for example:
 *   - threshold: `{"threshold": "2"}`
 *   - spending_limit: `{"amount": "100.0", "period_days": "7"}`
 *   - weighted_threshold: `{"threshold": "5", "weights": "addr1:3,addr2:2"}`
 */
data class PolicyDescriptor(
    val policyAddress: String,
    val policyType: String,
    val params: Map<String, String>
)

/**
 * A fully parsed context rule suitable for pre-populating an edit form in Swift.
 *
 * Replaces [com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule] for Swift interop by
 * converting all Kotlin-specific types to primitives:
 * - [com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType] sealed class -> [contextType] + [contextTypeParam] strings.
 * - [UInt] rule ID and validUntil -> [Long] to avoid Objective-C unsigned integer boxing.
 * - [com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner] sealed class list -> [List<SignerDescriptor>].
 * - Policy addresses -> [List<PolicyDescriptor>] (no SCVal params; they are already on-chain).
 *
 * @property name The rule's human-readable name.
 * @property contextType Context type tag: "default", "call_contract", or "create_contract".
 * @property contextTypeParam For "call_contract": the contract C-address. For "create_contract":
 *   lowercase hex-encoded WASM hash. Null for "default".
 * @property validUntil Absolute ledger expiry, or null if the rule has no expiry.
 * @property signers Signers registered on the rule, as [SignerDescriptor] instances.
 * @property signerIds On-chain signer IDs, positionally aligned with [signers]. Each value is
 *   the on-chain signer ID as Int (converted from UInt).
 * @property policies Policy addresses registered on the rule (no SCVal params needed in edit mode).
 * @property policyIds On-chain policy IDs, positionally aligned with [policies]. Each value is
 *   the on-chain policy ID as Int (converted from UInt).
 */
data class ParsedRuleBridge(
    val name: String,
    val contextType: String,
    val contextTypeParam: String?,
    val validUntil: Long?,
    val signers: List<SignerDescriptor>,
    val signerIds: List<Int>,
    val policies: List<PolicyDescriptor>,
    val policyIds: List<Int>
)

/**
 * Signer information for display in the transfer screen signer picker.
 *
 * Aggregates the data needed to render a signer row and determine whether it can
 * participate in a multi-signer transfer without exposing SDK signer types to Swift.
 *
 * @property type Signer type: "passkey", "delegated", or "ed25519".
 * @property displayName Human-readable label shown in the picker.
 * @property identifier Stable identifier: credential ID for passkey, G-address for delegated,
 *   hex public key prefix for Ed25519.
 * @property canSign Whether the app currently has the credentials to sign with this signer.
 * @property keyData Hex-encoded full key data for passkey signers (needed to reconstruct the
 *   [com.soneso.stellar.sdk.smartaccount.core.ExternalSigner] for the transfer call). Null for
 *   non-passkey signers.
 */
data class SignerInfoBridge(
    val type: String,
    val displayName: String,
    val identifier: String,
    val canSign: Boolean,
    val keyData: String?
)

/**
 * Describes the diff of changes between the original and current form state for a context rule edit.
 *
 * Swift cannot use UInt or sealed classes, so all IDs use Int and signer/policy objects are
 * represented as descriptors. The bridge converts these to the internal [ContextRuleEditDiff]
 * before calling the flow orchestrator.
 *
 * @property ruleId The on-chain context rule ID being edited.
 * @property nameChanged True if the name was modified.
 * @property newName The new name value, or null if unchanged.
 * @property newSignerDescriptors Signer descriptors that are newly added (not yet on-chain).
 * @property removedSignerIds On-chain IDs of signers that were removed by the user.
 * @property newPolicyDescriptors Policy descriptors that are newly added (not yet on-chain).
 * @property removedPolicyIds On-chain IDs of policies that were removed by the user.
 * @property modifiedPolicyDescriptors Policy descriptors with changed parameters.
 * @property modifiedPolicyIds On-chain IDs of modified policies (positionally aligned with
 *   [modifiedPolicyDescriptors]).
 * @property expiryChanged True if the expiration was modified.
 * @property newExpiry The new absolute expiry ledger number, or null to remove expiry.
 */
data class ContextRuleEditDiffBridge(
    val ruleId: Int,
    val nameChanged: Boolean,
    val newName: String?,
    val newSignerDescriptors: List<SignerDescriptor>,
    val removedSignerIds: List<Int>,
    val newPolicyDescriptors: List<PolicyDescriptor>,
    val removedPolicyIds: List<Int>,
    val modifiedPolicyDescriptors: List<PolicyDescriptor>,
    val modifiedPolicyIds: List<Int>,
    val expiryChanged: Boolean,
    val newExpiry: Int?
)

/**
 * Result of a context rule edit submission, suitable for Swift consumption.
 *
 * Mirrors [com.soneso.smartdemo.flows.ContextRuleEditResult] with all types safe for
 * Objective-C/Swift interop.
 *
 * @property success True if all operations completed successfully.
 * @property completedOperations Number of operations that succeeded.
 * @property totalOperations Total number of operations that were planned.
 * @property partialDueToAuthGuard True if policy/expiry updates were skipped due to auth context guard.
 * @property authGuardMessage Message explaining the auth context guard, or null if not triggered.
 * @property error Error message if [success] is false.
 * @property failedStep Description of the step that failed, or null on success.
 */
data class ContextRuleEditResultBridge(
    val success: Boolean,
    val completedOperations: Int,
    val totalOperations: Int,
    val partialDueToAuthGuard: Boolean,
    val authGuardMessage: String?,
    val error: String?,
    val failedStep: String?
)

/**
 * On-chain policy parameters read from contract storage, suitable for Swift consumption.
 *
 * Mirrors [com.soneso.smartdemo.flows.PolicyParams] with UInt converted to Int for
 * Objective-C/Swift interop.
 *
 * @property type The policy type identifier ("threshold", "spending_limit", "weighted_threshold").
 * @property threshold Threshold value for "threshold" and "weighted_threshold" policies.
 * @property spendingLimit Formatted XLM amount string for "spending_limit" policies (e.g. "1000").
 * @property periodDays Period in days for "spending_limit" policies.
 * @property signerWeights Map of signer key string to weight for "weighted_threshold" policies.
 *   Weights use Int instead of UInt for Swift compatibility.
 */
data class PolicyParamsBridge(
    val type: String,
    val threshold: Int?,
    val spendingLimit: String?,
    val periodDays: Int?,
    val signerWeights: Map<String, Int>?
)
