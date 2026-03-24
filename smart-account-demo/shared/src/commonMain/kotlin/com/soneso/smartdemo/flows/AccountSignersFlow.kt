package com.soneso.smartdemo.flows

/**
 * Business logic for loading all signers registered on the connected smart account.
 *
 * Demonstrates how to aggregate signer information across all context rules:
 * 1. Fetch all context rules using [fetchAllContextRules] (which calls [contextRuleManager]).
 * 2. Deduplicate signers across rules using [SmartAccountBuilders.getSignerKey].
 * 3. For each unique signer, collect the list of rules it belongs to.
 *
 * A single signer (identified by a unique key) may appear in multiple context rules.
 * For example, a passkey signer may be authorized for both Default and CallContract rules.
 * This flow consolidates all appearances into one [SignerEntry] per unique signer.
 *
 * Uses [SmartAccountBuilders.getSignerKey] to compare signers independent of their type
 * (DelegatedSigner, ExternalSigner, etc.).
 */

import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.util.fetchAllContextRules
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule

/**
 * A unique signer and the context rules it is registered in.
 *
 * @property signer The signer object (DelegatedSigner or ExternalSigner).
 * @property contextRules All [ParsedContextRule] objects this signer appears in.
 *   A signer may appear in multiple rules if it was added to more than one context.
 */
data class SignerEntry(
    val signer: SmartAccountSigner,
    val contextRules: List<ParsedContextRule>
)

/**
 * Loads all unique signers from the connected smart account, grouped by rule membership.
 *
 * SDK workflow:
 * 1. Call [fetchAllContextRules] to load all context rules from the smart account contract.
 *    This uses [OZSmartAccountKit.contextRuleManager.getContextRulesCount] and
 *    [getContextRule] internally.
 * 2. Iterate all rules and their signers. Use [SmartAccountBuilders.getSignerKey] to
 *    compute a stable string key for each signer (independent of signer type).
 * 3. Use a linked map to deduplicate: for each key, keep the first signer instance
 *    and accumulate all rules that contain it.
 * 4. Return the aggregated list as [SignerEntry] objects.
 *
 * The insertion order of the linked map preserves the order signers were first encountered
 * across the sorted rule list, so the display is deterministic.
 *
 * @return List of [SignerEntry], each with a unique signer and its rule memberships.
 * @throws IllegalStateException if the kit is not initialized.
 */
suspend fun loadAccountSigners(): List<SignerEntry> {
    // fetchAllContextRules reads the kit from DemoState and returns all parsed rules.
    val rules = fetchAllContextRules()

    // Deduplicate signers across all rules using their stable string key.
    // LinkedHashMap preserves insertion order so the list is deterministic.
    val signerMap = linkedMapOf<String, Pair<SmartAccountSigner, MutableList<ParsedContextRule>>>()

    for (rule in rules) {
        for (signer in rule.signers) {
            // getSignerKey returns a stable string that uniquely identifies the signer
            // regardless of its concrete type (DelegatedSigner, ExternalSigner, etc.).
            val key = SmartAccountBuilders.getSignerKey(signer)
            val existing = signerMap[key]
            if (existing == null) {
                // First time we see this signer: record it with the current rule.
                signerMap[key] = Pair(signer, mutableListOf(rule))
            } else {
                // Signer already seen in a previous rule: just add this rule to its list.
                existing.second.add(rule)
            }
        }
    }

    val entries = signerMap.values.map { (signer, ruleList) ->
        SignerEntry(signer = signer, contextRules = ruleList)
    }

    ActivityLogState.info("Loaded ${entries.size} unique signer(s) from ${rules.size} context rule(s)")

    return entries
}
