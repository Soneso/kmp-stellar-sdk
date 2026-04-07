package com.soneso.smartdemo.flows

/**
 * Edit orchestrator for context rule modifications.
 *
 * [submitContextRuleEdits] executes a sequence of on-chain transactions to apply all
 * changes described in a [ContextRuleEditDiff]. The execution order matches the TS SDK
 * demo's edit-mode submission flow:
 *
 * 1. Update name (if changed)
 * 2. Add new signers
 * 3. Remove deleted signers
 * 4. Auth context guard (if new signers were added, skip policy/expiry changes)
 * 5. Remove policies marked for deletion
 * 6. Update modified policies (remove + re-add)
 * 7. Add new policies
 * 8. Update expiration (if changed)
 * 9. Cleanup pending passkey credentials
 *
 * All operations pass [selectedSigners] through to the SDK manager methods. When
 * [selectedSigners] is empty, the SDK uses single-signer auth with the connected
 * passkey. When non-empty, the SDK routes through the multi-signer pipeline.
 *
 * On any step failure, execution stops immediately and the result describes which step
 * failed and how many operations completed. The UI should reload the rule from chain
 * to refresh form state after a partial failure.
 */

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Executes the full edit-mode submission flow for a context rule.
 *
 * Each step that modifies on-chain state is a separate Stellar transaction. The
 * [selectedSigners] list is passed through to every SDK manager call, allowing the
 * SDK to transparently handle single-signer or multi-signer authorization.
 *
 * @param diff The computed diff from UI form state describing all changes to apply.
 * @param selectedSigners Signers chosen by the user for multi-signer auth.
 *   Empty list = single-signer mode (connected passkey signs directly).
 * @param onProgress Callback for per-operation progress messages (UI updates).
 * @return [ContextRuleEditResult] with completion details, partial success info, or error.
 */
suspend fun submitContextRuleEdits(
    diff: ContextRuleEditDiff,
    selectedSigners: List<SelectedSigner>,
    onProgress: (String) -> Unit
): ContextRuleEditResult {
    if (diff.isEmpty) {
        return ContextRuleEditResult(
            success = true,
            completedOperations = 0,
            totalOperations = 0,
            partialDueToAuthGuard = false,
            authGuardMessage = null,
            error = null,
            failedStep = null
        )
    }

    val kit = DemoState.kit
        ?: return ContextRuleEditResult(
            success = false,
            completedOperations = 0,
            totalOperations = diff.totalOperations,
            partialDueToAuthGuard = false,
            authGuardMessage = null,
            error = "Smart Account Kit not initialized",
            failedStep = "Initialization"
        )

    val ruleId = diff.ruleId
    var completedOps = 0
    val txHashes = mutableListOf<String>()

    // Step 1: Update name (if changed)
    if (diff.nameChanged) {
        val stepName = "Updating rule name"
        onProgress("$stepName...")
        try {
            val newName = diff.newName ?: ""
            val result = updateContextRuleName(ruleId, newName, selectedSigners)
            if (!result.success) {
                return failureResult(completedOps, diff.totalOperations, stepName, result.error, txHashes)
            }
            completedOps++
            result.hash?.let { txHashes.add(it) }
            ActivityLogState.info("Rule name updated successfully")
        } catch (e: Exception) {
            return failureResult(completedOps, diff.totalOperations, stepName, e.message, txHashes)
        }
    }

    // Step 2: Add new signers (each is a separate transaction)
    for ((index, signerEntry) in diff.newSigners.withIndex()) {
        val stepName = "Adding signer ${index + 1} of ${diff.newSigners.size}"
        onProgress("$stepName...")
        try {
            val result = addSignerByType(ruleId, signerEntry, selectedSigners)
            if (!result.success) {
                return failureResult(completedOps, diff.totalOperations, stepName, result.error, txHashes)
            }
            completedOps++
            result.hash?.let { txHashes.add(it) }
        } catch (e: Exception) {
            return failureResult(completedOps, diff.totalOperations, stepName, e.message, txHashes)
        }
    }

    // Step 3: Remove deleted signers (each is a separate transaction)
    for ((index, signerEntry) in diff.removedSigners.withIndex()) {
        val stepName = "Removing signer ${index + 1} of ${diff.removedSigners.size}"
        onProgress("$stepName...")
        try {
            val signerId = signerEntry.onChainId
                ?: throw IllegalStateException("Cannot remove signer without on-chain ID")

            val result = removeSignerFromRule(ruleId, signerId, selectedSigners)
            if (!result.success) {
                return failureResult(completedOps, diff.totalOperations, stepName, result.error, txHashes)
            }
            completedOps++
            result.hash?.let { txHashes.add(it) }
        } catch (e: Exception) {
            return failureResult(completedOps, diff.totalOperations, stepName, e.message, txHashes)
        }
    }

    // Step 4: Auth context guard
    // If new signers were ADDED (not just removed), skip policy and expiration changes.
    // Adding signers changes the rule's authorization requirements, making subsequent
    // policy operations fail because the auth context no longer matches.
    val hasPendingPolicyOrExpiryChanges = diff.removedPolicies.isNotEmpty() ||
        diff.newPolicies.isNotEmpty() ||
        diff.modifiedPolicies.isNotEmpty() ||
        diff.expiryChanged

    if (diff.newSigners.isNotEmpty() && hasPendingPolicyOrExpiryChanges) {
        val authGuardMsg = "Signer changes were applied successfully. Policy and expiration " +
            "updates were skipped because adding signers changes the rule's authorization " +
            "requirements. Please edit the rule again to apply the remaining changes."
        ActivityLogState.info(authGuardMsg)
        return ContextRuleEditResult(
            success = true,
            completedOperations = completedOps,
            totalOperations = diff.totalOperations,
            partialDueToAuthGuard = true,
            authGuardMessage = authGuardMsg,
            error = null,
            failedStep = null,
            transactionHashes = txHashes
        )
    }

    // Step 5: Remove policies marked for deletion
    for ((index, policyEntry) in diff.removedPolicies.withIndex()) {
        val stepName = "Removing policy ${index + 1} of ${diff.removedPolicies.size}"
        onProgress("$stepName...")
        try {
            val policyId = policyEntry.onChainId
                ?: throw IllegalStateException("Cannot remove policy without on-chain ID")

            val result = removePolicyFromRule(ruleId, policyId, selectedSigners)
            if (!result.success) {
                return failureResult(completedOps, diff.totalOperations, stepName, result.error, txHashes)
            }
            completedOps++
            result.hash?.let { txHashes.add(it) }
        } catch (e: Exception) {
            return failureResult(completedOps, diff.totalOperations, stepName, e.message, txHashes)
        }
    }

    // Step 6: Update modified policies.
    // Simple threshold policies use executeAndSubmit to call set_threshold() directly
    // on the policy contract via the smart account's execute() entry point (1 transaction).
    // All other policy types use the remove + re-add path (2 transactions).
    for ((index, policyEntry) in diff.modifiedPolicies.withIndex()) {
        val stepName = "Updating policy ${index + 1} of ${diff.modifiedPolicies.size}"
        try {
            val isSimpleThreshold = policyEntry.info?.type == "threshold"

            if (isSimpleThreshold) {
                // Use execute() to call set_threshold() on the policy contract directly.
                // This is a single transaction instead of remove + re-add.
                onProgress("$stepName (set_threshold)...")

                // Extract the new threshold value from the encoded install params map.
                // The scVal is a Map { Symbol("threshold") -> U32(value) }.
                val scVal = policyEntry.scVal
                    ?: throw IllegalStateException("Cannot update threshold without parameter value")
                val paramsMap = Scv.fromMap(scVal)
                val thresholdScVal = paramsMap.entries.firstOrNull { (k, _) ->
                    try { Scv.fromSymbol(k) == "threshold" } catch (_: Exception) { false }
                }?.value ?: throw IllegalStateException("Threshold value not found in policy params")
                val newThreshold = Scv.fromUint32(thresholdScVal)

                // Get the full ContextRule SCVal from the contract (required by set_threshold)
                val contextRuleScVal = kit.contextRuleManager.getContextRule(ruleId)
                val smartAccountAddress = DemoState.contractId
                    ?: throw IllegalStateException("Smart account contract ID not set")

                val targetArgs = listOf(
                    Scv.toUint32(newThreshold),
                    contextRuleScVal,
                    Address(smartAccountAddress).toSCVal()
                )

                val result = if (selectedSigners.isEmpty()) {
                    kit.transactionOperations.executeAndSubmit(
                        target = policyEntry.address,
                        targetFn = "set_threshold",
                        targetArgs = targetArgs
                    )
                } else {
                    kit.multiSignerManager.multiSignerExecuteAndSubmit(
                        target = policyEntry.address,
                        targetFn = "set_threshold",
                        targetArgs = targetArgs,
                        selectedSigners = selectedSigners
                    )
                }

                if (!result.success) {
                    return failureResult(completedOps, diff.totalOperations, stepName, result.error, txHashes)
                }
                completedOps++
                result.hash?.let { txHashes.add(it) }
            } else {
                // All other policy types: remove + re-add (2 transactions)
                onProgress("$stepName (removing old)...")

                val policyId = policyEntry.onChainId
                    ?: throw IllegalStateException("Cannot update policy without on-chain ID")
                val installParams = policyEntry.scVal
                    ?: throw IllegalStateException("Cannot re-add policy without install parameters")

                val removeResult = removePolicyFromRule(ruleId, policyId, selectedSigners)
                if (!removeResult.success) {
                    return failureResult(completedOps, diff.totalOperations, "$stepName (remove)", removeResult.error, txHashes)
                }
                completedOps++
                removeResult.hash?.let { txHashes.add(it) }

                onProgress("$stepName (adding new)...")
                val addResult = addPolicyToRule(ruleId, policyEntry.address, installParams, selectedSigners)
                if (!addResult.success) {
                    return failureResult(completedOps, diff.totalOperations, "$stepName (re-add)", addResult.error, txHashes)
                }
                completedOps++
                addResult.hash?.let { txHashes.add(it) }
            }
        } catch (e: Exception) {
            return failureResult(completedOps, diff.totalOperations, stepName, e.message, txHashes)
        }
    }

    // Step 7: Add new policies (each is a separate transaction)
    for ((index, policyEntry) in diff.newPolicies.withIndex()) {
        val stepName = "Adding policy ${index + 1} of ${diff.newPolicies.size}"
        onProgress("$stepName...")
        try {
            val installParams = policyEntry.scVal
                ?: throw IllegalStateException("Cannot add policy without install parameters")

            val result = addPolicyToRule(ruleId, policyEntry.address, installParams, selectedSigners)
            if (!result.success) {
                return failureResult(completedOps, diff.totalOperations, stepName, result.error, txHashes)
            }
            completedOps++
            result.hash?.let { txHashes.add(it) }
        } catch (e: Exception) {
            return failureResult(completedOps, diff.totalOperations, stepName, e.message, txHashes)
        }
    }

    // Step 8: Update expiration (if changed)
    if (diff.expiryChanged) {
        val stepName = "Updating expiration"
        onProgress("$stepName...")
        try {
            val result = updateContextRuleValidUntil(ruleId, diff.newExpiry, selectedSigners)
            if (!result.success) {
                return failureResult(completedOps, diff.totalOperations, stepName, result.error, txHashes)
            }
            completedOps++
            result.hash?.let { txHashes.add(it) }
        } catch (e: Exception) {
            return failureResult(completedOps, diff.totalOperations, stepName, e.message, txHashes)
        }
    }

    // Step 9: Cleanup pending passkey credentials
    // For each newly added passkey signer that was successfully added on-chain and has
    // isPending == true, delete the pending credential to promote it to confirmed status.
    for (signerEntry in diff.newSigners) {
        if (!signerEntry.isPending) continue
        val signer = signerEntry.signer
        if (signer !is ExternalSigner) continue
        val credentialId = SmartAccountBuilders.getCredentialIdStringFromSigner(signer) ?: continue
        try {
            kit.credentialManager.deleteCredential(credentialId)
            ActivityLogState.info("Cleaned up pending credential: $credentialId")
        } catch (e: Exception) {
            // Credential cleanup is non-fatal; log and continue
            ActivityLogState.info("Could not clean up pending credential $credentialId: ${e.message}")
        }
    }

    ActivityLogState.success("All $completedOps edit operations completed successfully")

    return ContextRuleEditResult(
        success = true,
        completedOperations = completedOps,
        totalOperations = diff.totalOperations,
        partialDueToAuthGuard = false,
        authGuardMessage = null,
        error = null,
        failedStep = null,
        transactionHashes = txHashes
    )
}

/**
 * Dispatches a signer add operation to the appropriate type-specific flow function.
 *
 * For passkey signers, the public key (first 65 bytes of keyData) and credential ID
 * (remaining bytes after the public key) are extracted from the [ExternalSigner.keyData].
 * For Ed25519 signers, keyData is the 32-byte public key directly.
 *
 * @param ruleId The context rule ID to add the signer to.
 * @param signerEntry The signer entry containing the signer object.
 * @param selectedSigners Signers for multi-signer authorization (empty for single-signer).
 * @return [ContextRuleResult] from the type-specific add operation.
 */
private suspend fun addSignerByType(
    ruleId: UInt,
    signerEntry: EditSignerEntry,
    selectedSigners: List<SelectedSigner>
): ContextRuleResult {
    return when (val signer = signerEntry.signer) {
        is DelegatedSigner -> {
            addDelegatedSignerToRule(ruleId, signer.address, selectedSigners)
        }
        is ExternalSigner -> {
            // Determine if this is a passkey or Ed25519 signer by checking the verifier address
            if (signer.verifierAddress == DemoConfig.WEBAUTHN_VERIFIER_ADDRESS) {
                // Passkey signer: keyData = publicKey (65 bytes) + credentialId (remaining bytes)
                if (signer.keyData.size <= SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
                    return ContextRuleResult(
                        success = false,
                        hash = null,
                        error = "Passkey signer keyData too short to contain public key and credential ID"
                    )
                }
                val publicKey = signer.keyData.copyOfRange(0, SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
                val credentialId = signer.keyData.copyOfRange(
                    SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE,
                    signer.keyData.size
                )
                addPasskeySignerToRule(ruleId, publicKey, credentialId, selectedSigners)
            } else if (signer.verifierAddress == DemoConfig.ED25519_VERIFIER_ADDRESS) {
                // Ed25519 signer: keyData IS the 32-byte public key
                addEd25519SignerToRule(ruleId, signer.keyData, selectedSigners)
            } else {
                ContextRuleResult(
                    success = false,
                    hash = null,
                    error = "Unknown verifier address: ${signer.verifierAddress}"
                )
            }
        }
    }
}

/**
 * Creates a failure result with the given step information.
 */
private fun failureResult(
    completedOps: Int,
    totalOps: Int,
    failedStep: String,
    error: String?,
    txHashes: List<String> = emptyList()
): ContextRuleEditResult {
    val msg = error ?: "Unknown error"
    ActivityLogState.error("Edit failed at step '$failedStep': $msg")
    return ContextRuleEditResult(
        success = false,
        completedOperations = completedOps,
        totalOperations = totalOps,
        partialDueToAuthGuard = false,
        authGuardMessage = null,
        error = msg,
        failedStep = failedStep,
        transactionHashes = txHashes
    )
}
