package com.soneso.smartdemo.flows

/**
 * Business logic for managing context rules on a smart account.
 *
 * Demonstrates the [OZSmartAccountKit.contextRuleManager] API:
 * - Loading rules: [getContextRulesCount], [getContextRule]
 * - Modifying rules: [addContextRule], [removeContextRule], [updateName], [updateValidUntil]
 *
 * Context rules define on-chain authorization: each rule specifies which signers can
 * authorize which operations (Default, CallContract, or CreateContract) and which
 * policy contracts are enforced. All modifying operations require passkey authentication.
 *
 * Rule data is stored on-chain as Soroban SCVal maps and parsed by [fetchAllContextRules]
 * and [parseSingleContextRuleFromScVal] from ContextRuleParser.kt.
 */

import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.fetchAllContextRules
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Result of a context rule modification (add, remove, update).
 *
 * @property success True if the on-chain transaction was submitted and confirmed.
 * @property hash The Stellar transaction hash, or null if the operation failed.
 * @property error Error message if [success] is false; null on success.
 */
data class ContextRuleResult(
    val success: Boolean,
    val hash: String?,
    val error: String?
)

/**
 * A policy entry for passing to [addContextRule].
 *
 * The [ContextRuleBuilderScreen] builds these from its form state (policy address + SCVal params).
 * The flow converts them into the [Map<String, SCValXdr?>] format the SDK expects.
 *
 * @property address The policy contract's C-address.
 * @property scVal The encoded policy parameters as an SCVal, or null if the policy was
 *   loaded from an existing rule (params are already installed on-chain).
 */
data class FlowPolicyEntry(
    val address: String,
    val scVal: SCValXdr?
)

/**
 * Loads all context rules from the connected smart account.
 *
 * SDK workflow:
 * 1. Call [contextRuleManager.getContextRulesCount] to get the total rule count.
 * 2. Iterate IDs from 0 upward, calling [contextRuleManager.getContextRule] for each.
 *    Gaps from removed rules are skipped.
 * 3. Parse each SCVal result using [parseSingleContextRuleFromScVal] from ContextRuleParser.
 *
 * @return List of [ParsedContextRule], sorted by ID with duplicates removed.
 * @throws IllegalStateException if the kit is not initialized.
 */
suspend fun loadContextRules(): List<ParsedContextRule> {
    // fetchAllContextRules reads from DemoState.kit internally and handles the
    // count-then-fetch pattern with a fallback for contracts without count support.
    val rules = fetchAllContextRules()
    ActivityLogState.info("Fetched ${rules.size} context rule(s)")
    return rules
}

/**
 * Loads a single context rule by ID for edit mode pre-population.
 *
 * SDK workflow:
 * - Calls [OZSmartAccountKit.contextRuleManager.getContextRule] which returns the
 *   rule's on-chain data as a raw SCVal map.
 * - The screen parses the result using [parseSingleContextRuleFromScVal] to populate
 *   its form fields (name, context type, signers, policies, expiry).
 *
 * @param ruleId The rule ID (0-indexed) to load.
 * @return The raw [SCValXdr] for parsing by the caller.
 * @throws Exception if the rule does not exist or the RPC call fails.
 */
suspend fun loadContextRule(ruleId: UInt): SCValXdr {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    // getContextRule returns the raw on-chain SCVal so the screen can parse
    // it into typed form fields using parseSingleContextRuleFromScVal.
    return kit.contextRuleManager.getContextRule(ruleId)
}

/**
 * Removes a context rule from the smart account.
 *
 * SDK workflow:
 * - Calls [OZSmartAccountKit.contextRuleManager.removeContextRule] which:
 *   1. Simulates the transaction to compute the Soroban auth entry.
 *   2. Triggers a WebAuthn authentication ceremony to sign with the passkey.
 *   3. Submits the transaction to the network.
 *
 * Note: the UI prevents removing the last remaining rule to keep the account operable.
 *
 * @param ruleId The ID of the rule to remove.
 * @return [ContextRuleResult] with success status, hash, and optional error message.
 */
suspend fun removeContextRule(ruleId: UInt): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Removing context rule #$ruleId...")

    return try {
        // removeContextRule requires smart account authorization (passkey signature).
        val result = kit.contextRuleManager.removeContextRule(ruleId)

        if (result.success) {
            ActivityLogState.success("Context rule #$ruleId removed. Hash: ${result.hash ?: "N/A"}")
        } else {
            val errMsg = result.error ?: "Unknown error"
            ActivityLogState.error("Failed to remove rule: $errMsg")
        }

        ContextRuleResult(
            success = result.success,
            hash = result.hash,
            error = result.error
        )
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        ActivityLogState.error("Failed to remove rule: $msg")
        ContextRuleResult(success = false, hash = null, error = msg)
    }
}

/**
 * Adds a new context rule to the smart account.
 *
 * SDK workflow:
 * - Calls [OZSmartAccountKit.contextRuleManager.addContextRule] with the rule configuration.
 *   The SDK:
 *   1. Encodes the context type, signers, and policies into Soroban SCVal format.
 *   2. Simulates the transaction.
 *   3. Triggers a WebAuthn authentication ceremony for passkey signing.
 *   4. Submits the transaction.
 *
 * The [policies] parameter is converted from [FlowPolicyEntry] to [Map<String, SCValXdr?>]
 * as required by the SDK. Entries with a null SCVal are skipped (already installed on-chain).
 *
 * @param contextType The context type for this rule (Default, CallContract, or CreateContract).
 * @param name A human-readable name stored on-chain for the rule.
 * @param validUntil Optional expiry as an absolute ledger number. Null means no expiry.
 * @param signers The list of signers who can authorize operations under this rule.
 * @param policies Policy entries to enforce, each with an address and optional SCVal params.
 * @return [ContextRuleResult] with success status, hash, and optional error message.
 */
suspend fun addContextRule(
    contextType: ContextRuleType,
    name: String,
    validUntil: UInt?,
    signers: List<SmartAccountSigner>,
    policies: List<FlowPolicyEntry>
): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Submitting new context rule...")

    // Convert FlowPolicyEntry list to the Map<String, SCValXdr> format the SDK expects.
    // Policies without an scVal are skipped — they were loaded from an existing rule
    // and their parameters are already installed on-chain.
    val policiesMap = mutableMapOf<String, SCValXdr>()
    for (policy in policies) {
        if (policy.scVal != null) {
            policiesMap[policy.address] = policy.scVal
        }
    }

    return try {
        // addContextRule encodes all parameters as Soroban SCVal, simulates, signs, and submits.
        val result = kit.contextRuleManager.addContextRule(
            contextType = contextType,
            name = name,
            validUntil = validUntil,
            signers = signers,
            policies = policiesMap
        )

        if (result.success) {
            ActivityLogState.info("Context rule created successfully. Hash: ${result.hash ?: "N/A"}")
        } else {
            ActivityLogState.error("Failed to create context rule: ${result.error ?: "Unknown error"}")
        }

        ContextRuleResult(
            success = result.success,
            hash = result.hash,
            error = result.error
        )
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        ActivityLogState.error("Transaction failed: $msg")
        ContextRuleResult(success = false, hash = null, error = msg)
    }
}

/**
 * Updates the name of an existing context rule.
 *
 * SDK workflow:
 * - Calls [OZSmartAccountKit.contextRuleManager.updateName] which simulates, signs
 *   (WebAuthn), and submits a transaction to update the rule's name on-chain.
 *
 * In edit mode, the screen calls [updateContextRuleName] followed by [updateContextRuleValidUntil]
 * to apply both changes.
 *
 * @param ruleId The ID of the rule to update.
 * @param name The new name to store on-chain.
 * @return [ContextRuleResult] with success status and transaction hash.
 */
suspend fun updateContextRuleName(ruleId: UInt, name: String): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Updating rule #$ruleId name...")

    return try {
        // updateName requires smart account authorization (passkey signature).
        val result = kit.contextRuleManager.updateName(
            id = ruleId,
            name = name
        )

        if (!result.success) {
            ActivityLogState.error("Failed to update rule name: ${result.error}")
        }

        ContextRuleResult(
            success = result.success,
            hash = result.hash,
            error = result.error
        )
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        ActivityLogState.error("Transaction failed: $msg")
        ContextRuleResult(success = false, hash = null, error = msg)
    }
}

/**
 * Updates the expiry (valid_until ledger) of an existing context rule.
 *
 * SDK workflow:
 * - Calls [OZSmartAccountKit.contextRuleManager.updateValidUntil] which simulates, signs
 *   (WebAuthn), and submits a transaction to update the rule's expiry on-chain.
 *
 * Pass null for [validUntil] to clear the expiry and make the rule permanent.
 *
 * @param ruleId The ID of the rule to update.
 * @param validUntil The new expiry as an absolute ledger number, or null to remove expiry.
 * @return [ContextRuleResult] with success status and transaction hash.
 */
suspend fun updateContextRuleValidUntil(ruleId: UInt, validUntil: UInt?): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Updating rule #$ruleId expiry...")

    return try {
        // updateValidUntil requires smart account authorization (passkey signature).
        val result = kit.contextRuleManager.updateValidUntil(
            id = ruleId,
            validUntil = validUntil
        )

        if (!result.success) {
            ActivityLogState.error("Failed to update validUntil: ${result.error}")
        }

        ContextRuleResult(
            success = result.success,
            hash = result.hash,
            error = result.error
        )
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        ActivityLogState.error("Transaction failed: $msg")
        ContextRuleResult(success = false, hash = null, error = msg)
    }
}
