package com.soneso.smartdemo.flows

/**
 * Shared data types used by the context rule edit flow orchestrator and the UI layer.
 *
 * Types defined here:
 * - [EditSignerEntry]: A signer in the edit form, tracking on-chain state.
 * - [EditPolicyEntry]: A policy in the edit form, tracking on-chain state and modifications.
 * - [PolicyParams]: On-chain policy parameters read from contract storage.
 * - [ContextRuleEditDiff]: Describes what changed between original and current form state.
 * - [ContextRuleEditResult]: Result of the edit submission, including per-operation details.
 *
 * Also re-exports [buildSelectedSigners] and [isSinglePasskeyTransfer] which are shared
 * between the transfer flow and the edit flow.
 */

import com.soneso.smartdemo.config.PolicyInfo
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * A signer in the edit form, tracking its on-chain state.
 *
 * Named `EditSignerEntry` to avoid conflict with [SignerEntry] in `AccountSignersFlow.kt`
 * which tracks signers grouped by rule membership.
 *
 * @property signer The signer object (DelegatedSigner or ExternalSigner).
 * @property onChainId The on-chain signer ID assigned by the contract, or null if newly added.
 * @property isOriginal True if this signer was loaded from the existing on-chain rule.
 * @property isPending True if this is a pending passkey credential (registered but not yet on-chain).
 */
data class EditSignerEntry(
    val signer: SmartAccountSigner,
    val onChainId: UInt?,
    val isOriginal: Boolean,
    val isPending: Boolean = false
)

/**
 * A policy in the edit form, tracking its on-chain state and modifications.
 *
 * Named `EditPolicyEntry` to avoid conflict with [PolicyEntry] in `PolicyManagementSection.kt`
 * which is a simpler UI-only data class without on-chain tracking fields.
 *
 * @property info Known policy metadata from [KNOWN_POLICIES], or null for unknown policy contracts.
 * @property label Display label for the policy.
 * @property address The policy contract's C-address.
 * @property scVal The encoded policy parameters as an SCVal, or null if loaded from an existing rule.
 * @property onChainId The on-chain policy ID assigned by the contract, or null if newly added.
 * @property isOriginal True if this policy was loaded from the existing on-chain rule.
 * @property modified True if the user changed parameters on an existing policy.
 * @property originalParams On-chain parameters loaded at edit start, for display and comparison.
 */
data class EditPolicyEntry(
    val info: PolicyInfo?,
    val label: String,
    val address: String,
    val scVal: SCValXdr? = null,
    val onChainId: UInt?,
    val isOriginal: Boolean,
    val modified: Boolean = false,
    val originalParams: PolicyParams? = null
)

/**
 * On-chain parameters for a policy, read by [readPolicyParams].
 *
 * Conversion constants: 17,280 ledgers per day (5 seconds per ledger).
 *
 * @property type The policy type identifier ("threshold", "spending_limit", "weighted_threshold").
 * @property threshold Threshold value for "threshold" and "weighted_threshold" policies.
 * @property spendingLimit Formatted XLM amount string for "spending_limit" policies (e.g. "1000").
 * @property periodDays Period in days for "spending_limit" policies (periodLedgers / 17280, min 1).
 * @property signerWeights Map of signer key string to weight for "weighted_threshold" policies.
 */
data class PolicyParams(
    val type: String,
    val threshold: UInt?,
    val spendingLimit: String?,
    val periodDays: Int?,
    val signerWeights: Map<String, UInt>?
)

/**
 * Describes what changed between the original and current form state.
 *
 * Computed by the UI from its form state, passed to [submitContextRuleEdits] for execution.
 *
 * @property ruleId The on-chain context rule ID being edited.
 * @property nameChanged True if the name was modified.
 * @property newName The new name value, or null if unchanged.
 * @property newSigners Signer entries that are newly added (not yet on-chain).
 * @property removedSigners Original signer entries that were removed by the user.
 * @property newPolicies Policy entries that are newly added (not yet on-chain).
 * @property removedPolicies Original policy entries that were removed by the user.
 * @property modifiedPolicies Original policy entries whose parameters were changed.
 * @property expiryChanged True if the expiration was modified.
 * @property newExpiry The new expiry ledger number, or null to remove expiry.
 */
data class ContextRuleEditDiff(
    val ruleId: UInt,
    val nameChanged: Boolean,
    val newName: String?,
    val newSigners: List<EditSignerEntry>,
    val removedSigners: List<EditSignerEntry>,
    val newPolicies: List<EditPolicyEntry>,
    val removedPolicies: List<EditPolicyEntry>,
    val modifiedPolicies: List<EditPolicyEntry>,
    val expiryChanged: Boolean,
    val newExpiry: UInt?
) {
    /** True if there are no changes at all. */
    val isEmpty: Boolean
        get() = !nameChanged && newSigners.isEmpty() &&
            removedSigners.isEmpty() && newPolicies.isEmpty() &&
            removedPolicies.isEmpty() && modifiedPolicies.isEmpty() && !expiryChanged

    /** Total number of on-chain operations that will be executed. */
    val totalOperations: Int
        get() {
            var count = 0
            if (nameChanged) count++
            count += newSigners.size
            count += removedSigners.size
            count += removedPolicies.size
            // Simple threshold uses execute() for set_threshold (1 op),
            // other policy types use remove + re-add (2 ops)
            count += modifiedPolicies.sumOf { policy ->
                if (policy.info?.type == "threshold") 1 else 2 as Int
            }
            count += newPolicies.size
            if (expiryChanged) count++
            return count
        }
}

/**
 * Result of the edit submission, including per-operation details.
 *
 * @property success True if all operations completed successfully.
 * @property completedOperations Number of operations that succeeded.
 * @property totalOperations Total number of operations that were planned.
 * @property partialDueToAuthGuard True if policy/expiry updates were skipped due to auth context guard.
 * @property authGuardMessage Message explaining the auth context guard, or null if not triggered.
 * @property error Error message if [success] is false.
 * @property failedStep Description of the step that failed, or null on success.
 */
data class ContextRuleEditResult(
    val success: Boolean,
    val completedOperations: Int,
    val totalOperations: Int,
    val partialDueToAuthGuard: Boolean,
    val authGuardMessage: String?,
    val error: String?,
    val failedStep: String?,
    val transactionHashes: List<String> = emptyList()
)

/**
 * Converts a list of selected [SmartAccountSigner] objects from the signer picker into
 * the [SelectedSigner] list required by multi-signer operations.
 *
 * - [ExternalSigner] with a WebAuthn credential ID -> [SelectedSigner.Passkey] with full keyData.
 * - [ExternalSigner] with a 32-byte public key (Ed25519) -> [SelectedSigner.Ed25519] carrying
 *   the verifier address and public key. Signing source must already be registered on the
 *   [OZExternalSignerManager] before the multi-signer call.
 * - [DelegatedSigner] -> [SelectedSigner.Wallet] identified by G-address.
 *
 * @param signers Selected signers from the signer picker dialog.
 * @return Mapped list of [SelectedSigner] ready for multi-signer operations.
 */
suspend fun buildSelectedSigners(signers: List<SmartAccountSigner>): List<SelectedSigner> {
    val storage = DemoState.storage
    val result = mutableListOf<SelectedSigner>()
    for (signer in signers) {
        when (signer) {
            is ExternalSigner -> {
                val credIdStr = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
                val credIdBytes = SmartAccountBuilders.getCredentialIdFromSigner(signer)
                when {
                    credIdStr != null -> {
                        // WebAuthn passkey signer — look up stored credential for transport hints
                        // (enables cross-device auth). Null transports is the correct fallback
                        // for credentials not in local storage.
                        val transports = try {
                            storage?.get(credIdStr)?.transports
                        } catch (_: Exception) { null }
                        result.add(
                            SelectedSigner.Passkey(
                                credentialId = credIdStr,
                                credentialIdBytes = credIdBytes,
                                keyData = signer.keyData,
                                transports = transports
                            )
                        )
                    }
                    signer.keyData.size == SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE -> {
                        // Ed25519 signer — the public key is stored directly as keyData.
                        // Signing source must be registered on the OZExternalSignerManager
                        // (either in-memory keypair or adapter) before the multi-signer call.
                        result.add(
                            SelectedSigner.Ed25519(
                                verifierAddress = signer.verifierAddress,
                                publicKey = signer.keyData
                            )
                        )
                    }
                    // Other external signer types without a recognisable credential are skipped.
                }
            }
            is DelegatedSigner -> {
                result.add(SelectedSigner.Wallet(address = signer.address))
            }
        }
    }
    return result
}

/**
 * Determines whether a single-passkey operation can be used instead of the multi-signer path.
 *
 * Returns true only when exactly one passkey is selected AND it is the currently connected
 * passkey. The simple single-signer path always signs with the connected passkey, so it cannot
 * be used for other passkeys from context rules or for any delegated signers.
 *
 * @param selectedSigners Signers chosen in the signer picker dialog.
 * @return True if the single-signer path should be used; false for multi-signer.
 */
fun isSinglePasskeyTransfer(selectedSigners: List<SmartAccountSigner>): Boolean {
    if (selectedSigners.size != 1) return false
    val signer = selectedSigners[0]
    if (signer !is ExternalSigner) return false
    val credIdStr = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
    return credIdStr != null && credIdStr == DemoState.credentialId
}
