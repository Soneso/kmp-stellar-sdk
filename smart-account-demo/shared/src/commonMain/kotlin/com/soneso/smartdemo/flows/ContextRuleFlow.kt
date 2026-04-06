package com.soneso.smartdemo.flows

/**
 * Business logic for managing context rules on a smart account.
 *
 * Functions provided by this file:
 * - Loading rules: [loadContextRules], [loadParsedContextRule]
 * - Modifying rules: [addContextRule], [removeContextRule], [updateContextRuleName], [updateContextRuleValidUntil]
 * - Signer operations: [addDelegatedSignerToRule], [addEd25519SignerToRule], [addPasskeySignerToRule],
 *   [addNewPasskeySignerToRule], [removeSignerFromRule]
 * - Policy operations: [addPolicyToRule], [removePolicyFromRule]
 * - Policy reading: [readPolicyParams], [readPolicyParamsWithServer]
 * - Signer construction: [registerPasskeySigner], [buildDelegatedSigner], [buildEd25519Signer]
 * - Helpers: [resolveAbsoluteLedger], [loadAvailablePasskeySigners]
 *
 * Context rules define on-chain authorization: each rule specifies which signers can
 * authorize which operations (Default, CallContract, or CreateContract) and which
 * policy contracts are enforced. All modifying operations require passkey authentication
 * or multi-signer authorization when [selectedSigners] is provided.
 *
 * Rule data is fetched via [OZContextRuleManager.listContextRules] which returns fully
 * parsed [ParsedContextRule] objects including the [ParsedContextRule.signerIds] and
 * [ParsedContextRule.policyIds] fields introduced in v0.7.0.
 */

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.config.KNOWN_POLICIES
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.fetchAllContextRules
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.AddPasskeySignerResult
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
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
 * The calling screen builds these from its form state (policy address + SCVal params).
 * The flow converts them into the [Map<String, SCValXdr>] format the SDK expects.
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
 * Delegates to [fetchAllContextRules] which calls [OZContextRuleManager.listContextRules].
 * The returned rules include [ParsedContextRule.signerIds] and [ParsedContextRule.policyIds]
 * populated by the SDK.
 *
 * @return List of [ParsedContextRule], sorted by ID.
 * @throws IllegalStateException if the kit is not initialized.
 */
suspend fun loadContextRules(): List<ParsedContextRule> {
    val rules = fetchAllContextRules()
    ActivityLogState.info("Fetched ${rules.size} context rule(s)")
    return rules
}

/**
 * Loads a single context rule by ID as a fully parsed [ParsedContextRule].
 *
 * Fetches all context rules via [OZContextRuleManager.listContextRules] and returns the
 * rule matching [ruleId]. This ensures the result includes [ParsedContextRule.signerIds]
 * and [ParsedContextRule.policyIds] as populated by the SDK parser.
 *
 * @param ruleId The rule ID to load.
 * @return The parsed [ParsedContextRule].
 * @throws IllegalStateException if the kit is not initialized.
 * @throws NoSuchElementException if no rule with the given ID exists.
 */
suspend fun loadParsedContextRule(ruleId: UInt): ParsedContextRule {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")
    return kit.contextRuleManager.listContextRules()
        .firstOrNull { it.id == ruleId }
        ?: throw NoSuchElementException("Context rule #$ruleId not found")
}

/**
 * Removes a context rule from the smart account.
 *
 * SDK workflow:
 * - Calls [OZSmartAccountKit.contextRuleManager.removeContextRule] which:
 *   1. Simulates the transaction to compute the Soroban auth entry.
 *   2. Triggers a WebAuthn authentication ceremony to sign with the passkey,
 *      or collects signatures from all [selectedSigners] when non-empty.
 *   3. Submits the transaction to the network.
 *
 * Note: the UI prevents removing the last remaining rule to keep the account operable.
 *
 * @param ruleId The ID of the rule to remove.
 * @param selectedSigners Optional signers for multi-signer authorization. When empty
 *   (default), uses single-signer auth with the connected passkey.
 * @return [ContextRuleResult] with success status, hash, and optional error message.
 */
suspend fun removeContextRule(
    ruleId: UInt,
    selectedSigners: List<SelectedSigner> = emptyList()
): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Removing context rule #$ruleId...")

    return try {
        // removeContextRule requires smart account authorization (passkey signature).
        val result = kit.contextRuleManager.removeContextRule(ruleId, selectedSigners)

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
 *   3. Triggers a WebAuthn authentication ceremony for passkey signing,
 *      or collects signatures from all [selectedSigners] when non-empty.
 *   4. Submits the transaction.
 *
 * The [policies] parameter is converted from [FlowPolicyEntry] to [Map<String, SCValXdr>]
 * as required by the SDK. Entries with a null SCVal are skipped (already installed on-chain).
 *
 * @param contextType The context type for this rule (Default, CallContract, or CreateContract).
 * @param name A human-readable name stored on-chain for the rule.
 * @param validUntil Optional expiry as an absolute ledger number. Null means no expiry.
 * @param signers The list of signers who can authorize operations under this rule.
 * @param policies Policy entries to enforce, each with an address and optional SCVal params.
 * @param selectedSigners Optional signers for multi-signer authorization. When empty
 *   (default), uses single-signer auth with the connected passkey.
 * @return [ContextRuleResult] with success status, hash, and optional error message.
 */
suspend fun addContextRule(
    contextType: ContextRuleType,
    name: String,
    validUntil: UInt?,
    signers: List<SmartAccountSigner>,
    policies: List<FlowPolicyEntry>,
    selectedSigners: List<SelectedSigner> = emptyList()
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
        } else {
            ActivityLogState.info(
                "Policy ${policy.address} has no SCVal params and will be skipped (params already on-chain)"
            )
        }
    }

    return try {
        // addContextRule encodes all parameters as Soroban SCVal, simulates, signs, and submits.
        val result = kit.contextRuleManager.addContextRule(
            contextType = contextType,
            name = name,
            validUntil = validUntil,
            signers = signers,
            policies = policiesMap,
            selectedSigners = selectedSigners
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
 *   (WebAuthn or multi-signer), and submits a transaction to update the rule's name on-chain.
 *
 * In edit mode, the screen calls [updateContextRuleName] followed by [updateContextRuleValidUntil]
 * to apply both changes.
 *
 * @param ruleId The ID of the rule to update.
 * @param name The new name to store on-chain.
 * @param selectedSigners Optional list of [SelectedSigner] for multi-signer authorization.
 *   When empty (default), uses single-signer auth with the connected passkey.
 * @return [ContextRuleResult] with success status and transaction hash.
 */
suspend fun updateContextRuleName(
    ruleId: UInt,
    name: String,
    selectedSigners: List<SelectedSigner> = emptyList()
): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Updating rule #$ruleId name...")

    return try {
        val result = kit.contextRuleManager.updateName(
            id = ruleId,
            name = name,
            selectedSigners = selectedSigners
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
 *   (WebAuthn or multi-signer), and submits a transaction to update the rule's expiry on-chain.
 *
 * Pass null for [validUntil] to clear the expiry and make the rule permanent.
 *
 * @param ruleId The ID of the rule to update.
 * @param validUntil The new expiry as an absolute ledger number, or null to remove expiry.
 * @param selectedSigners Optional list of [SelectedSigner] for multi-signer authorization.
 *   When empty (default), uses single-signer auth with the connected passkey.
 * @return [ContextRuleResult] with success status and transaction hash.
 */
suspend fun updateContextRuleValidUntil(
    ruleId: UInt,
    validUntil: UInt?,
    selectedSigners: List<SelectedSigner> = emptyList()
): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Updating rule #$ruleId expiry...")

    return try {
        val result = kit.contextRuleManager.updateValidUntil(
            id = ruleId,
            validUntil = validUntil,
            selectedSigners = selectedSigners
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

// MARK: - Signer Operations

/**
 * Adds a delegated signer to an existing context rule.
 *
 * Delegates to [OZSignerManager.addDelegated] which builds and submits an `add_signer`
 * transaction with the delegated signer's G-address or C-address.
 *
 * @param ruleId The context rule ID to add the signer to.
 * @param address The Stellar address (G-address for accounts, C-address for contracts).
 * @param selectedSigners Optional list of [SelectedSigner] for multi-signer authorization.
 *   When empty (default), uses single-signer auth with the connected passkey.
 * @return [ContextRuleResult] with success status, hash, and optional error message.
 */
suspend fun addDelegatedSignerToRule(
    ruleId: UInt,
    address: String,
    selectedSigners: List<SelectedSigner> = emptyList()
): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Adding delegated signer to rule #$ruleId...")

    return try {
        val result = kit.signerManager.addDelegated(
            contextRuleId = ruleId,
            address = address,
            selectedSigners = selectedSigners
        )

        if (result.success) {
            ActivityLogState.success("Delegated signer added to rule #$ruleId. Hash: ${result.hash ?: "N/A"}")
        } else {
            ActivityLogState.error("Failed to add delegated signer: ${result.error ?: "Unknown error"}")
        }

        ContextRuleResult(
            success = result.success,
            hash = result.hash,
            error = result.error
        )
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        ActivityLogState.error("Failed to add delegated signer: $msg")
        ContextRuleResult(success = false, hash = null, error = msg)
    }
}

/**
 * Adds an Ed25519 signer to an existing context rule.
 *
 * Delegates to [OZSignerManager.addEd25519] which builds and submits an `add_signer`
 * transaction with the Ed25519 verifier contract and public key.
 *
 * @param ruleId The context rule ID to add the signer to.
 * @param publicKey 32-byte Ed25519 public key.
 * @param selectedSigners Optional list of [SelectedSigner] for multi-signer authorization.
 *   When empty (default), uses single-signer auth with the connected passkey.
 * @return [ContextRuleResult] with success status, hash, and optional error message.
 */
suspend fun addEd25519SignerToRule(
    ruleId: UInt,
    publicKey: ByteArray,
    selectedSigners: List<SelectedSigner> = emptyList()
): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Adding Ed25519 signer to rule #$ruleId...")

    return try {
        val result = kit.signerManager.addEd25519(
            contextRuleId = ruleId,
            verifierAddress = DemoConfig.ED25519_VERIFIER_ADDRESS,
            publicKey = publicKey,
            selectedSigners = selectedSigners
        )

        if (result.success) {
            ActivityLogState.success("Ed25519 signer added to rule #$ruleId. Hash: ${result.hash ?: "N/A"}")
        } else {
            ActivityLogState.error("Failed to add Ed25519 signer: ${result.error ?: "Unknown error"}")
        }

        ContextRuleResult(
            success = result.success,
            hash = result.hash,
            error = result.error
        )
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        ActivityLogState.error("Failed to add Ed25519 signer: $msg")
        ContextRuleResult(success = false, hash = null, error = msg)
    }
}

/**
 * Adds a passkey signer to an existing context rule using an already-registered passkey.
 *
 * Delegates to [OZSignerManager.addPasskey] which builds and submits an `add_signer`
 * transaction with the WebAuthn verifier contract, public key, and credential ID.
 *
 * @param ruleId The context rule ID to add the signer to.
 * @param publicKey 65-byte uncompressed secp256r1 public key (0x04 prefix + X + Y).
 * @param credentialId Raw WebAuthn credential identifier bytes.
 * @param selectedSigners Optional list of [SelectedSigner] for multi-signer authorization.
 *   When empty (default), uses single-signer auth with the connected passkey.
 * @return [ContextRuleResult] with success status, hash, and optional error message.
 */
suspend fun addPasskeySignerToRule(
    ruleId: UInt,
    publicKey: ByteArray,
    credentialId: ByteArray,
    selectedSigners: List<SelectedSigner> = emptyList()
): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Adding passkey signer to rule #$ruleId...")

    return try {
        val result = kit.signerManager.addPasskey(
            contextRuleId = ruleId,
            publicKey = publicKey,
            credentialId = credentialId,
            selectedSigners = selectedSigners
        )

        if (result.success) {
            ActivityLogState.success("Passkey signer added to rule #$ruleId. Hash: ${result.hash ?: "N/A"}")
        } else {
            ActivityLogState.error("Failed to add passkey signer: ${result.error ?: "Unknown error"}")
        }

        ContextRuleResult(
            success = result.success,
            hash = result.hash,
            error = result.error
        )
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        ActivityLogState.error("Failed to add passkey signer: $msg")
        ContextRuleResult(success = false, hash = null, error = msg)
    }
}

/**
 * Registers a new passkey via WebAuthn ceremony and adds it as a signer to a context rule.
 *
 * Delegates to [OZSignerManager.addNewPasskeySigner] which:
 * 1. Triggers the platform WebAuthn registration ceremony.
 * 2. Saves the credential locally as pending.
 * 3. Adds the passkey signer on-chain via `add_signer`.
 *
 * Returns the full [AddPasskeySignerResult] including credential ID and public key,
 * which the caller needs for pending credential cleanup.
 *
 * @param ruleId The context rule ID to add the signer to.
 * @param userName Display name for the passkey registration prompt.
 * @param selectedSigners Optional list of [SelectedSigner] for multi-signer authorization.
 *   When empty (default), uses single-signer auth with the connected passkey.
 * @return [AddPasskeySignerResult] with credential info and transaction result.
 */
suspend fun addNewPasskeySignerToRule(
    ruleId: UInt,
    userName: String,
    selectedSigners: List<SelectedSigner> = emptyList()
): AddPasskeySignerResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Registering new passkey and adding to rule #$ruleId...")

    val result = kit.signerManager.addNewPasskeySigner(
        contextRuleId = ruleId,
        userName = userName,
        selectedSigners = selectedSigners
    )

    if (result.transactionResult.success) {
        ActivityLogState.success(
            "New passkey signer added to rule #$ruleId. Hash: ${result.transactionResult.hash ?: "N/A"}"
        )
    } else {
        ActivityLogState.error(
            "Failed to add new passkey signer: ${result.transactionResult.error ?: "Unknown error"}"
        )
    }

    return result
}

/**
 * Removes a signer from a context rule by its on-chain ID.
 *
 * Delegates to [OZSignerManager.removeSigner] which builds and submits a `remove_signer`
 * transaction targeting the specified signer ID.
 *
 * @param ruleId The context rule ID to remove the signer from.
 * @param signerId The on-chain signer ID (from [ParsedContextRule.signerIds]).
 * @param selectedSigners Optional list of [SelectedSigner] for multi-signer authorization.
 *   When empty (default), uses single-signer auth with the connected passkey.
 * @return [ContextRuleResult] with success status, hash, and optional error message.
 */
suspend fun removeSignerFromRule(
    ruleId: UInt,
    signerId: UInt,
    selectedSigners: List<SelectedSigner> = emptyList()
): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Removing signer #$signerId from rule #$ruleId...")

    return try {
        val result = kit.signerManager.removeSigner(
            contextRuleId = ruleId,
            signerId = signerId,
            selectedSigners = selectedSigners
        )

        if (result.success) {
            ActivityLogState.success("Signer #$signerId removed from rule #$ruleId. Hash: ${result.hash ?: "N/A"}")
        } else {
            ActivityLogState.error("Failed to remove signer: ${result.error ?: "Unknown error"}")
        }

        ContextRuleResult(
            success = result.success,
            hash = result.hash,
            error = result.error
        )
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        ActivityLogState.error("Failed to remove signer: $msg")
        ContextRuleResult(success = false, hash = null, error = msg)
    }
}

// MARK: - Policy Operations

/**
 * Adds a policy to an existing context rule with the given install parameters.
 *
 * Delegates to [OZPolicyManager.addPolicy] which builds and submits an `add_policy`
 * transaction with the policy contract address and installation parameters.
 *
 * @param ruleId The context rule ID to add the policy to.
 * @param policyAddress The policy contract's C-address.
 * @param installParams Encoded policy installation parameters as SCVal.
 * @param selectedSigners Optional list of [SelectedSigner] for multi-signer authorization.
 *   When empty (default), uses single-signer auth with the connected passkey.
 * @return [ContextRuleResult] with success status, hash, and optional error message.
 */
suspend fun addPolicyToRule(
    ruleId: UInt,
    policyAddress: String,
    installParams: SCValXdr,
    selectedSigners: List<SelectedSigner> = emptyList()
): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Adding policy to rule #$ruleId...")

    return try {
        val result = kit.policyManager.addPolicy(
            contextRuleId = ruleId,
            policyAddress = policyAddress,
            installParams = installParams,
            selectedSigners = selectedSigners
        )

        if (result.success) {
            ActivityLogState.success("Policy added to rule #$ruleId. Hash: ${result.hash ?: "N/A"}")
        } else {
            ActivityLogState.error("Failed to add policy: ${result.error ?: "Unknown error"}")
        }

        ContextRuleResult(
            success = result.success,
            hash = result.hash,
            error = result.error
        )
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        ActivityLogState.error("Failed to add policy: $msg")
        ContextRuleResult(success = false, hash = null, error = msg)
    }
}

/**
 * Removes a policy from a context rule by its on-chain ID.
 *
 * Delegates to [OZPolicyManager.removePolicy] which builds and submits a `remove_policy`
 * transaction targeting the specified policy ID.
 *
 * @param ruleId The context rule ID to remove the policy from.
 * @param policyId The on-chain policy ID (from [ParsedContextRule.policyIds]).
 * @param selectedSigners Optional list of [SelectedSigner] for multi-signer authorization.
 *   When empty (default), uses single-signer auth with the connected passkey.
 * @return [ContextRuleResult] with success status, hash, and optional error message.
 */
suspend fun removePolicyFromRule(
    ruleId: UInt,
    policyId: UInt,
    selectedSigners: List<SelectedSigner> = emptyList()
): ContextRuleResult {
    val kit = DemoState.kit
        ?: throw IllegalStateException("Kit not initialized")

    ActivityLogState.info("Removing policy #$policyId from rule #$ruleId...")

    return try {
        val result = kit.policyManager.removePolicy(
            contextRuleId = ruleId,
            policyId = policyId,
            selectedSigners = selectedSigners
        )

        if (result.success) {
            ActivityLogState.success("Policy #$policyId removed from rule #$ruleId. Hash: ${result.hash ?: "N/A"}")
        } else {
            ActivityLogState.error("Failed to remove policy: ${result.error ?: "Unknown error"}")
        }

        ContextRuleResult(
            success = result.success,
            hash = result.hash,
            error = result.error
        )
    } catch (e: Exception) {
        val msg = e.message ?: "Unknown error"
        ActivityLogState.error("Failed to remove policy: $msg")
        ContextRuleResult(success = false, hash = null, error = msg)
    }
}

// MARK: - Policy Parameter Reading

/**
 * Reads on-chain policy parameters from contract storage.
 *
 * Creates a new [SorobanServer] instance. For batch reads (e.g., loading params for
 * multiple policies during rule edit initialization), prefer [readPolicyParamsWithServer]
 * with a shared server instance.
 *
 * @param smartAccountContractId The smart account contract C-address.
 * @param policyAddress The policy contract C-address to read from.
 * @param policyType The policy type identifier ("threshold", "spending_limit", "weighted_threshold").
 * @param contextRuleId The context rule ID the policy is installed on.
 * @return [PolicyParams] with parsed parameters, or null if the entry cannot be read or parsed.
 */
suspend fun readPolicyParams(
    smartAccountContractId: String,
    policyAddress: String,
    policyType: String,
    contextRuleId: UInt
): PolicyParams? {
    return try {
        SorobanServer(DemoConfig.RPC_URL).use { server ->
            readPolicyParamsWithServer(server, smartAccountContractId, policyAddress, policyType, contextRuleId)
        }
    } catch (e: Exception) {
        ActivityLogState.info("Could not read $policyType policy params: ${e.message}")
        null
    }
}

/**
 * Reads on-chain policy parameters from contract storage using an existing server instance.
 *
 * OZ policy contracts store per-account per-rule parameters using the storage key:
 * `Vec([Symbol("AccountContext"), Address(smartAccount), U32(contextRuleId)])`.
 *
 * The stored value format depends on the policy type:
 * - "threshold": bare U32 value (the threshold count).
 * - "spending_limit": struct map with "spending_limit" (I128, in stroops) and "period_ledgers" (U32).
 * - "weighted_threshold": struct map with "threshold" (U32) and "signer_weights" (map of signer -> U32).
 *
 * @param server An existing [SorobanServer] instance (not closed by this method).
 * @param smartAccountContractId The smart account contract C-address.
 * @param policyAddress The policy contract C-address to read from.
 * @param policyType The policy type identifier.
 * @param contextRuleId The context rule ID the policy is installed on.
 * @return [PolicyParams] with parsed parameters, or null if the entry cannot be read or parsed.
 */
internal suspend fun readPolicyParamsWithServer(
    server: SorobanServer,
    smartAccountContractId: String,
    policyAddress: String,
    policyType: String,
    contextRuleId: UInt
): PolicyParams? {
    return try {
        // Build the storage key matching the OZ policy contract format:
        // EnumVariant("AccountContext", [Address, u32])
        val storageKey = Scv.toVec(
            listOf(
                Scv.toSymbol("AccountContext"),
                Address(smartAccountContractId).toSCVal(),
                Scv.toUint32(contextRuleId)
            )
        )

        val entry = server.getContractData(
            contractId = policyAddress,
            key = storageKey,
            durability = SorobanServer.Durability.PERSISTENT
        ) ?: return null

        // Parse the ledger entry data to get the stored SCVal
        val data = entry.parseXdr()
        if (data !is LedgerEntryDataXdr.ContractData) return null
        val scVal = data.value.`val`

        // Parse type-specific fields from the stored SCVal
        when (policyType) {
            "threshold" -> parseThresholdParams(scVal)
            "spending_limit" -> parseSpendingLimitParams(scVal)
            "weighted_threshold" -> parseWeightedThresholdParams(scVal)
            else -> null
        }
    } catch (e: Exception) {
        ActivityLogState.info("Could not read $policyType policy params from $policyAddress: ${e.message}")
        null
    }
}

/**
 * Parses threshold policy parameters from on-chain storage.
 *
 * On-chain format: bare U32 value representing the threshold count.
 */
private fun parseThresholdParams(scVal: SCValXdr): PolicyParams {
    val threshold = Scv.fromUint32(scVal)
    return PolicyParams(
        type = "threshold",
        threshold = threshold,
        spendingLimit = null,
        periodDays = null,
        signerWeights = null
    )
}

/**
 * Parses spending limit policy parameters from on-chain storage.
 *
 * On-chain format: struct map with fields:
 * - "spending_limit": I128 value in stroops
 * - "period_ledgers": U32 value in ledgers
 *
 * The storage may contain additional fields (spending_history, cached_total_spent)
 * which are ignored for display purposes.
 */
private fun parseSpendingLimitParams(scVal: SCValXdr): PolicyParams? {
    val map = Scv.fromMap(scVal)

    var spendingLimitStroops: BigInteger? = null
    var periodLedgers: UInt? = null

    for ((key, value) in map) {
        val keySymbol = try { Scv.fromSymbol(key) } catch (_: Exception) { continue }
        when (keySymbol) {
            "spending_limit" -> {
                spendingLimitStroops = Scv.fromInt128(value)
            }
            "period_ledgers" -> {
                periodLedgers = Scv.fromUint32(value)
            }
        }
    }

    if (spendingLimitStroops == null || periodLedgers == null) return null

    // Convert stroops to XLM string: divide by 10^7
    val stroopsPerXlm = BigDecimal.fromLong(Util.STROOPS_PER_XLM)
    val xlmAmount = BigDecimal.fromBigInteger(spendingLimitStroops).divide(stroopsPerXlm)
    val spendingLimitXlm = xlmAmount.toPlainString()

    // Convert ledgers to days: 17,280 ledgers per day, minimum 1
    val days = (periodLedgers.toInt() / Util.LEDGERS_PER_DAY).coerceAtLeast(1)

    return PolicyParams(
        type = "spending_limit",
        threshold = null,
        spendingLimit = spendingLimitXlm,
        periodDays = days,
        signerWeights = null
    )
}

/**
 * Parses weighted threshold policy parameters from on-chain storage.
 *
 * On-chain format: struct map with fields:
 * - "threshold": U32 value
 * - "signer_weights": map of signer SCVal -> U32 weight
 *
 * Signer keys in the weights map are converted to their string representation
 * using [SmartAccountBuilders.getSignerKey] or a hex fallback.
 */
private fun parseWeightedThresholdParams(scVal: SCValXdr): PolicyParams? {
    val map = Scv.fromMap(scVal)

    var threshold: UInt? = null
    var signerWeightsMap: Map<String, UInt>? = null

    for ((key, value) in map) {
        val keySymbol = try { Scv.fromSymbol(key) } catch (_: Exception) { continue }
        when (keySymbol) {
            "threshold" -> {
                threshold = Scv.fromUint32(value)
            }
            "signer_weights" -> {
                val weightsScMap = Scv.fromMap(value)
                val weights = mutableMapOf<String, UInt>()
                for ((signerKey, weightVal) in weightsScMap) {
                    // The signer key is the full signer SCVal (Vec with type + address + keyData).
                    // Convert to a readable string key for display.
                    val signerKeyStr = signerScValToString(signerKey)
                    val weight = Scv.fromUint32(weightVal)
                    weights[signerKeyStr] = weight
                }
                signerWeightsMap = weights
            }
        }
    }

    if (threshold == null) return null

    return PolicyParams(
        type = "weighted_threshold",
        threshold = threshold,
        spendingLimit = null,
        periodDays = null,
        signerWeights = signerWeightsMap
    )
}

/**
 * Converts a signer SCVal to a display string.
 *
 * Attempts to parse the signer SCVal as a SmartAccountSigner and extract a readable key.
 * Falls back to the SCVal discriminant name if parsing fails.
 */
private fun signerScValToString(scVal: SCValXdr): String {
    return try {
        val vec = Scv.fromVec(scVal)
        if (vec.size >= 2) {
            val typeSymbol = Scv.fromSymbol(vec[0])
            when (typeSymbol) {
                "Delegated" -> {
                    // Vec([Symbol("Delegated"), Address(addr)])
                    val address = Address.fromSCVal(vec[1])
                    address.toString()
                }
                "External" -> {
                    // Vec([Symbol("External"), Address(verifier), Bytes(keyData)])
                    val verifier = Address.fromSCVal(vec[1]).toString()
                    if (vec.size >= 3) {
                        val keyData = Scv.fromBytes(vec[2])
                        // Try to use the signer key utility
                        val signer = ExternalSigner(verifierAddress = verifier, keyData = keyData)
                        SmartAccountBuilders.getSignerKey(signer)
                    } else {
                        "external:$verifier"
                    }
                }
                else -> scVal.discriminant.name
            }
        } else {
            scVal.discriminant.name
        }
    } catch (_: Exception) {
        scVal.discriminant.name
    }
}

// MARK: - Signer Construction Helpers

/**
 * Resolves a ledger offset to an absolute ledger number by fetching the current ledger
 * from the Soroban RPC.
 *
 * @param offset Number of ledgers from now (e.g., 720 for approximately 1 hour).
 * @return Absolute ledger number = current ledger sequence + offset.
 * @throws Exception if the RPC call fails.
 */
suspend fun resolveAbsoluteLedger(offset: UInt): UInt {
    return SorobanServer(DemoConfig.RPC_URL).use { server ->
        val currentLedger = server.getLatestLedger().sequence.toUInt()
        currentLedger + offset
    }
}

/**
 * Registers a new passkey signer via the WebAuthn ceremony and returns the constructed
 * [ExternalSigner].
 *
 * Workflow:
 * 1. Generates a random 32-byte challenge and 16-byte user ID (WebAuthn protocol only --
 *    not stored on-chain).
 * 2. Calls [WebAuthnProvider.register] to trigger the platform passkey registration UI.
 * 3. Constructs an [ExternalSigner] from the registration result using the configured
 *    WebAuthn verifier contract address.
 *
 * @param name Display name shown to the user during the passkey registration prompt.
 * @return The constructed [ExternalSigner] ready to be added to a context rule.
 * @throws IllegalStateException if the WebAuthn provider is not available.
 * @throws Exception if the registration ceremony fails or is cancelled.
 */
suspend fun registerPasskeySigner(name: String): ExternalSigner {
    val provider = DemoState.webauthnProvider
        ?: throw IllegalStateException("WebAuthn provider not available")

    val challenge = kotlin.random.Random.nextBytes(32)
    val userId = kotlin.random.Random.nextBytes(16)

    ActivityLogState.info("Starting passkey registration...")
    val result = provider.register(
        challenge = challenge,
        userId = userId,
        userName = name
    )

    return ExternalSigner.webAuthn(
        verifierAddress = DemoConfig.WEBAUTHN_VERIFIER_ADDRESS,
        publicKey = result.publicKey,
        credentialId = result.credentialId
    )
}

/**
 * Constructs a [DelegatedSigner] from a Stellar G-address.
 *
 * @param address Full G-address of the delegated signer.
 * @return The constructed [DelegatedSigner].
 * @throws Exception if the address is invalid.
 */
fun buildDelegatedSigner(address: String): DelegatedSigner {
    return DelegatedSigner(address)
}

/**
 * Constructs an Ed25519 [ExternalSigner] from a raw public key byte array.
 *
 * Uses the configured Ed25519 verifier contract address from [DemoConfig].
 *
 * @param publicKey 32-byte Ed25519 public key.
 * @return The constructed [ExternalSigner].
 * @throws Exception if the key is invalid.
 */
fun buildEd25519Signer(publicKey: ByteArray): ExternalSigner {
    return ExternalSigner.ed25519(
        verifierAddress = DemoConfig.ED25519_VERIFIER_ADDRESS,
        publicKey = publicKey
    )
}

/**
 * Loads passkey signers from all on-chain context rules, excluding the currently
 * connected wallet's own passkey.
 *
 * Passkey signers are [ExternalSigner] instances whose verifier address matches the
 * configured WebAuthn verifier contract. They are deduplicated by signer key.
 *
 * @param excludeCredentialId Base64URL credential ID of the passkey to exclude (the
 *   connected wallet owner). Pass null to include all passkeys.
 * @return Deduplicated list of available [ExternalSigner] instances.
 * @throws IllegalStateException if the kit is not initialized.
 */
suspend fun loadAvailablePasskeySigners(excludeCredentialId: String?): List<ExternalSigner> {
    val rules = loadContextRules()
    return rules
        .flatMap { it.signers }
        .filterIsInstance<ExternalSigner>()
        .filter { it.verifierAddress == DemoConfig.WEBAUTHN_VERIFIER_ADDRESS }
        .distinctBy { SmartAccountBuilders.getSignerKey(it) }
        .filter { signer ->
            val signerCredId = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
            signerCredId != excludeCredentialId
        }
}
