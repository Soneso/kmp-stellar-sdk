//
//  OZPolicyManager.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrWriter

// MARK: - Policy Type Definitions

/**
 * Policy type definitions for smart account context rules.
 *
 * Policies define authorization rules that must be satisfied for transactions
 * to execute. Multiple policy types are supported:
 *
 * - [SimpleThreshold]: M-of-N authorization (threshold of signers must sign)
 * - [WeightedThreshold]: Weighted voting with configurable threshold
 * - [SpendingLimit]: Maximum amount per time period (in ledgers)
 *
 * Policies are installed on specific context rules and evaluated during
 * transaction authorization.
 *
 * Example usage:
 * ```kotlin
 * // Create a 2-of-3 simple threshold policy
 * val simplePolicy = PolicyInstallParams.SimpleThreshold(threshold = 2u)
 *
 * // Create a weighted threshold policy (100 points required)
 * val weightedPolicy = PolicyInstallParams.WeightedThreshold(
 *     signerWeights = mapOf(
 *         delegatedSigner1 to 50u,
 *         delegatedSigner2 to 30u,
 *         externalSigner to 20u
 *     ),
 *     threshold = 100u
 * )
 *
 * // Create a spending limit (1000 tokens per day, in the token's base units)
 * val spendingPolicy = PolicyInstallParams.SpendingLimit(
 *     spendingLimit = OZTransactionOperations.amountToBaseUnits("1000", decimals = 7),
 *     periodLedgers = Util.LEDGERS_PER_DAY.toUInt()
 * )
 * ```
 *
 * Note: For most use cases, prefer the convenience methods on [OZPolicyManager]
 * (`addSimpleThreshold`, `addWeightedThreshold`, `addSpendingLimit`) which handle
 * parameter encoding internally. Use these sealed class variants directly only when
 * calling [OZPolicyManager.addPolicy] with custom parameters.
 */
sealed class PolicyInstallParams {

    /**
     * Encodes these policy parameters as the SCVal map expected by the policy
     * contract's install entry point.
     *
     * Map keys are emitted in the order the contract requires. Use the result as the
     * `installParams` argument of [OZPolicyManager.addPolicy] or as a value in
     * [OZContextRuleManager.addContextRule]'s policies map.
     *
     * @return SCVal map with installation parameters
     * @throws ValidationException if the parameters are invalid
     */
    abstract fun toScVal(): SCValXdr

    /**
     * Simple threshold policy requiring at least M-of-N signers to authorize.
     *
     * All signers in the context rule have equal weight (1 vote each).
     * The threshold specifies the minimum number of signers that must approve.
     *
     * @property threshold Number of signers required to authorize (must be > 0)
     */
    data class SimpleThreshold(
        val threshold: UInt
    ) : PolicyInstallParams() {
        /**
         * Converts the simple threshold policy parameters to an ScVal map.
         *
         * Map structure: { "threshold": U32(threshold) }
         * Keys are in alphabetical order (required for contract compatibility).
         *
         * @return SCVal map with installation parameters
         * @throws ValidationException if threshold is zero
         */
        override fun toScVal(): SCValXdr {
            if (threshold == 0u) {
                throw ValidationException.invalidInput("threshold", "Threshold must be greater than zero")
            }
            // Map with alphabetically sorted keys: ["threshold"]
            val map = linkedMapOf(
                Scv.toSymbol("threshold") to Scv.toUint32(threshold)
            )
            return Scv.toMap(map)
        }
    }

    /**
     * Weighted threshold policy with configurable signer weights.
     *
     * Each signer has a weight (vote power). The sum of approving signers'
     * weights must meet or exceed the threshold.
     *
     * @property signerWeights Map of signers to their weights defining vote power
     * @property threshold Minimum total weight required for authorization
     */
    data class WeightedThreshold(
        val signerWeights: Map<SmartAccountSigner, UInt>,
        val threshold: UInt
    ) : PolicyInstallParams() {
        /**
         * Converts the weighted threshold policy parameters to an ScVal map.
         *
         * Map structure: {
         *   "signer_weights": Map[Signer => U32],
         *   "threshold": U32(threshold)
         * }
         * Keys are in alphabetical order (required for contract compatibility).
         *
         * @return SCVal map with installation parameters
         * @throws ValidationException if threshold is zero or signer weights map is empty
         */
        override fun toScVal(): SCValXdr {
            if (threshold == 0u) {
                throw ValidationException.invalidInput("threshold", "Threshold must be greater than zero")
            }
            if (signerWeights.isEmpty()) {
                throw ValidationException.invalidInput(
                    "signerWeights",
                    "Weighted threshold policy requires at least one signer with weight"
                )
            }

            // Build signer weights map
            val weightsMap = linkedMapOf<SCValXdr, SCValXdr>()
            for ((signer, weight) in signerWeights) {
                val signerScVal = signer.toScVal()
                weightsMap[signerScVal] = Scv.toUint32(weight)
            }

            // Sort signer weights map keys by XDR bytes (Soroban requirement)
            val sortedWeightsMap = OZPolicyManager.sortMapByKeyXdr(weightsMap)

            // Map with alphabetically sorted keys: ["signer_weights", "threshold"]
            val map = linkedMapOf(
                Scv.toSymbol("signer_weights") to Scv.toMap(sortedWeightsMap),
                Scv.toSymbol("threshold") to Scv.toUint32(threshold)
            )
            return Scv.toMap(map)
        }
    }

    /**
     * Spending limit policy restricting total amount per time period.
     *
     * Limits the total amount that can be spent within a rolling time window.
     * The period is specified in ledgers (approximately 5 seconds per ledger).
     *
     * @property spendingLimit Maximum amount per period in the token's base units (as BigInteger)
     * @property periodLedgers Time window in ledgers (e.g., 17,280 for one day)
     */
    data class SpendingLimit(
        val spendingLimit: BigInteger,
        val periodLedgers: UInt
    ) : PolicyInstallParams() {
        /**
         * Converts the spending limit policy parameters to an ScVal map.
         *
         * Map structure: {
         *   "period_ledgers": U32(periodLedgers),
         *   "spending_limit": I128(spendingLimit)
         * }
         * Keys are in alphabetical order (required for contract compatibility).
         *
         * @return SCVal map with installation parameters
         * @throws ValidationException if spending limit or period is invalid
         */
        override fun toScVal(): SCValXdr {
            // Validate inputs
            if (spendingLimit <= BigInteger.ZERO) {
                throw ValidationException.invalidInput(
                    "spendingLimit",
                    "Spending limit must be greater than zero, got: $spendingLimit"
                )
            }

            if (periodLedgers == 0u) {
                throw ValidationException.invalidInput(
                    "periodLedgers",
                    "Period ledgers must be greater than zero, got: $periodLedgers"
                )
            }

            // Convert limit to I128 ScVal
            val limitI128 = Scv.toInt128(spendingLimit)

            // Map with alphabetically sorted keys: ["period_ledgers", "spending_limit"]
            val map = linkedMapOf(
                Scv.toSymbol("period_ledgers") to Scv.toUint32(periodLedgers),
                Scv.toSymbol("spending_limit") to limitI128
            )
            return Scv.toMap(map)
        }
    }
}

// MARK: - Policy Manager

/**
 * Manager for policy operations on OpenZeppelin Smart Accounts.
 *
 * Provides functionality to add and remove policies on context rules. Policies
 * define authorization rules that must be satisfied for transactions to execute.
 *
 * A context rule can have multiple policies (up to [OZConstants.MAX_POLICIES]),
 * and all policies must be satisfied for a transaction to succeed.
 *
 * All state-changing methods accept an optional [selectedSigners] parameter for multi-signer
 * authorization. When empty (default), the operation uses single-signer auth with the
 * connected passkey. When non-empty, signatures are collected from all listed signers
 * via [OZMultiSignerManager.submitWithMultipleSigners].
 *
 * Policy lifecycle:
 * 1. Deploy policy contract to network
 * 2. Add policy to context rule with installation parameters
 * 3. Policy is initialized on the smart account contract
 * 4. Policy is evaluated during transaction authorization
 * 5. Remove policy when no longer needed
 *
 * This manager is typically accessed via [OZSmartAccountKit] rather than
 * instantiated directly.
 *
 * Three convenience methods are provided for built-in policy types:
 * [addSimpleThreshold], [addWeightedThreshold], and [addSpendingLimit].
 * For custom policy contracts, use [addPolicy] directly with policy-specific
 * installation parameters encoded as [SCValXdr].
 *
 * Example usage:
 * ```kotlin
 * val kit = OZSmartAccountKit.create(config)
 * val policyManager = kit.policyManager
 *
 * // Add a simple threshold policy (2-of-3, single-signer)
 * val result = policyManager.addSimpleThreshold(
 *     contextRuleId = 0u,
 *     policyAddress = "CBCD1234...",
 *     threshold = 2u
 * )
 *
 * // Add a custom policy with multi-signer authorization
 * val customResult = policyManager.addPolicy(
 *     contextRuleId = 0u,
 *     policyAddress = "CBCD5678...",
 *     installParams = myCustomPolicyParams,
 *     selectedSigners = listOf(
 *         SelectedSigner.Passkey(credentialId = cred1, credentialIdBytes = cred1Bytes, keyData = key1),
 *         SelectedSigner.Wallet("GA7Q...")
 *     )
 * )
 *
 * if (result.success) {
 *     println("Policy added successfully")
 * }
 *
 * // Remove a policy by its ID (ID available from ParsedContextRule.policyIds)
 * val removeResult = policyManager.removePolicy(
 *     contextRuleId = 0u,
 *     policyId = 1u
 * )
 * ```
 *
 * Thread Safety:
 * This class is thread-safe. All operations are async and can be called from any coroutine context.
 *
 * @property kit Reference to the parent OZSmartAccountKit instance
 */
class OZPolicyManager internal constructor(
    private val kit: OZSmartAccountKit
) {
    // MARK: - Add Simple Threshold Policy

    /**
     * Adds a simple threshold policy to a context rule.
     *
     * A simple threshold policy requires a specific number of signers to authorize
     * a transaction. All signers have equal weight (1 vote each).
     *
     * Encodes the threshold parameter and delegates to [addPolicy].
     *
     * IMPORTANT: This operation requires the connected wallet to have authorization
     * on the smart account. The user will be prompted for biometric authentication
     * to sign the transaction.
     *
     * Contract limits:
     * - Maximum [OZConstants.MAX_POLICIES] policies per context rule
     * - Policy address must be a valid C-address
     * - Threshold must be greater than zero
     *
     * @param contextRuleId The context rule ID to add the policy to (0 for Default rule)
     * @param policyAddress The policy contract address (C-address)
     * @param threshold Number of signers required to authorize (1 to signer count)
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return [TransactionResult] indicating success or failure
     * @throws ValidationException if validation fails
     * @throws TransactionException if transaction submission fails
     *
     * Example:
     * ```kotlin
     * // Add a 2-of-3 simple threshold policy
     * val result = policyManager.addSimpleThreshold(
     *     contextRuleId = 0u,
     *     policyAddress = "CBCD1234...",
     *     threshold = 2u
     * )
     *
     * if (result.success) {
     *     println("Policy added: ${result.hash ?: ""}")
     * } else {
     *     println("Failed to add policy: ${result.error ?: ""}")
     * }
     * ```
     */
    suspend fun addSimpleThreshold(
        contextRuleId: UInt,
        policyAddress: String,
        threshold: UInt,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        val params = PolicyInstallParams.SimpleThreshold(threshold)
        return addPolicy(contextRuleId, policyAddress, params.toScVal(), selectedSigners, forceMethod)
    }

    // MARK: - Add Weighted Threshold Policy

    /**
     * Adds a weighted threshold policy to a context rule.
     *
     * A weighted threshold policy assigns different weights (vote power) to each signer.
     * The sum of weights from approving signers must meet or exceed the threshold.
     *
     * Encodes the signer weights and threshold parameters and delegates to [addPolicy].
     *
     * IMPORTANT: This operation requires the connected wallet to have authorization
     * on the smart account. The user will be prompted for biometric authentication
     * to sign the transaction.
     *
     * Contract limits:
     * - Maximum [OZConstants.MAX_POLICIES] policies per context rule
     * - Policy address must be a valid C-address
     * - At least one signer weight must be specified
     * - Threshold must be greater than zero
     *
     * @param contextRuleId The context rule ID to add the policy to (0 for Default rule)
     * @param policyAddress The policy contract address (C-address)
     * @param signerWeights Map of signers to their weights defining vote power
     * @param threshold Minimum total weight required for authorization
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return [TransactionResult] indicating success or failure
     * @throws ValidationException if validation fails
     * @throws TransactionException if transaction submission fails
     *
     * Example:
     * ```kotlin
     * // Create a weighted threshold policy (100 points required)
     * val result = policyManager.addWeightedThreshold(
     *     contextRuleId = 0u,
     *     policyAddress = "CBCD1234...",
     *     signerWeights = mapOf(
     *         delegatedSigner1 to 50u,
     *         delegatedSigner2 to 30u,
     *         externalSigner to 20u
     *     ),
     *     threshold = 100u
     * )
     *
     * if (result.success) {
     *     println("Weighted policy added: ${result.hash ?: ""}")
     * } else {
     *     println("Failed to add policy: ${result.error ?: ""}")
     * }
     * ```
     */
    suspend fun addWeightedThreshold(
        contextRuleId: UInt,
        policyAddress: String,
        signerWeights: Map<SmartAccountSigner, UInt>,
        threshold: UInt,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        val params = PolicyInstallParams.WeightedThreshold(signerWeights, threshold)
        return addPolicy(contextRuleId, policyAddress, params.toScVal(), selectedSigners, forceMethod)
    }

    // MARK: - Add Spending Limit Policy

    /**
     * Adds a spending limit policy to a context rule.
     *
     * A spending limit policy restricts the total amount that can be spent within
     * a rolling time window. The period is specified in ledgers (approximately
     * 5 seconds per ledger, 720 per hour, 17,280 per day).
     *
     * Converts the amount to the token's base units using [decimals] and delegates
     * to [addPolicy].
     *
     * IMPORTANT: This operation requires the connected wallet to have authorization
     * on the smart account. The user will be prompted for biometric authentication
     * to sign the transaction.
     *
     * Contract limits:
     * - Maximum [OZConstants.MAX_POLICIES] policies per context rule
     * - Policy address must be a valid C-address
     * - Spending limit must be greater than zero
     * - Period ledgers must be greater than zero
     *
     * @param contextRuleId The context rule ID to add the policy to (0 for Default rule)
     * @param policyAddress The policy contract address (C-address)
     * @param spendingLimit Maximum amount per period as a positive decimal string
     *   (e.g., "100" or "10.5") with up to [decimals] fractional digits. Converted to
     *   the token's base units internally using [decimals].
     * @param periodLedgers Period duration in ledgers (17,280 = approximately 1 day)
     * @param decimals The token's decimal scale used to convert [spendingLimit] to base
     *   units. Defaults to 7 (XLM and SAC-wrapped classic assets). Pass the token's
     *   `decimals()` value for tokens with a different scale (see
     *   [OZTransactionOperations.fetchTokenDecimals]).
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return [TransactionResult] indicating success or failure
     * @throws ValidationException if validation fails
     * @throws TransactionException if transaction submission fails
     *
     * Example:
     * ```kotlin
     * // Create a spending limit (1000 XLM per day)
     * val result = policyManager.addSpendingLimit(
     *     contextRuleId = 0u,
     *     policyAddress = "CBCD1234...",
     *     spendingLimit = "1000",
     *     periodLedgers = Util.LEDGERS_PER_DAY.toUInt()
     * )
     *
     * if (result.success) {
     *     println("Spending limit added: ${result.hash ?: ""}")
     * } else {
     *     println("Failed to add policy: ${result.error ?: ""}")
     * }
     * ```
     */
    suspend fun addSpendingLimit(
        contextRuleId: UInt,
        policyAddress: String,
        spendingLimit: String,
        periodLedgers: UInt,
        decimals: Int = 7,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        val baseUnits = try {
            OZTransactionOperations.amountToBaseUnits(spendingLimit, decimals)
        } catch (e: ValidationException.InvalidAmount) {
            // Re-surface as a field-tagged validation error so the message refers
            // to the policy parameter the caller supplied.
            throw ValidationException.invalidInput(
                "spendingLimit",
                "Invalid spending limit: ${e.message}",
                e
            )
        }
        val params = PolicyInstallParams.SpendingLimit(baseUnits, periodLedgers)
        return addPolicy(contextRuleId, policyAddress, params.toScVal(), selectedSigners, forceMethod)
    }

    // MARK: - Remove Policy

    /**
     * Removes a policy from a context rule by its ID.
     *
     * Uninstalls the policy with the given ID from the specified context rule. The policy
     * contract remains deployed on the network but is no longer evaluated for this context rule.
     * The policy ID is assigned by the contract when the policy is added and is available via
     * [ParsedContextRule.policyIds] after fetching the context rule.
     *
     * Flow:
     * 1. Validates that a wallet is connected
     * 2. Builds contract invocation for remove_policy
     * 3. Submits transaction via transactionOps (handles simulation, signing, polling)
     *
     * IMPORTANT: This operation requires the connected wallet to have authorization
     * on the smart account. The user will be prompted for biometric authentication
     * to sign the transaction.
     *
     * @param contextRuleId The context rule ID to remove the policy from (0 for Default rule)
     * @param policyId The on-chain policy ID assigned by the contract (available from [ParsedContextRule.policyIds])
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return [TransactionResult] indicating success or failure
     * @throws WalletException.NotConnected if no wallet is connected
     * @throws TransactionException if simulation, signing, or submission fails
     * @throws WebAuthnException if biometric authentication fails
     *
     * Example:
     * ```kotlin
     * // Fetch the parsed context rule to get policy IDs
     * val rules = kit.contextRuleManager.listContextRules()
     * val rule = rules.first { it.id == 0u }
     * val policyIdToRemove = rule.policyIds.first()
     *
     * val result = policyManager.removePolicy(
     *     contextRuleId = 0u,
     *     policyId = policyIdToRemove
     * )
     *
     * if (result.success) {
     *     println("Policy removed: ${result.hash ?: ""}")
     * } else {
     *     println("Failed to remove policy: ${result.error ?: ""}")
     * }
     * ```
     */
    suspend fun removePolicy(
        contextRuleId: UInt,
        policyId: UInt,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        // Validate wallet is connected
        val (_, contractId) = kit.requireConnected()

        // Build host function
        val hostFunction = buildRemovePolicyFunction(
            contractId = contractId,
            contextRuleId = contextRuleId,
            policyId = policyId
        )

        // Route to single-signer or multi-signer submission
        return if (selectedSigners.isEmpty()) {
            kit.transactionOperations.submit(hostFunction = hostFunction, auth = emptyList(), forceMethod = forceMethod)
        } else {
            kit.multiSignerManager.submitWithMultipleSigners(hostFunction, selectedSigners, forceMethod = forceMethod)
        }
    }

    /**
     * Removes a policy from a context rule by matching the policy contract address.
     *
     * Convenience overload that resolves the on-chain policy ID internally. Fetches the
     * specified context rule (single RPC call), finds the policy matching the given address,
     * and delegates to the ID-based [removePolicy].
     *
     * @param contextRuleId The context rule ID to remove the policy from
     * @param policyAddress The policy contract address (C-address) to remove
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return TransactionResult indicating success or failure
     * @throws WalletException.NotConnected if no wallet is connected
     * @throws ValidationException if the policy address is not found on the context rule
     * @throws TransactionException if simulation, signing, or submission fails
     * @throws WebAuthnException if biometric authentication fails
     *
     * Example:
     * ```kotlin
     * val result = kit.policyManager.removePolicy(
     *     contextRuleId = 1u,
     *     policyAddress = "CPOLICY..."
     * )
     * ```
     */
    suspend fun removePolicy(
        contextRuleId: UInt,
        policyAddress: String,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        requireContractAddress(policyAddress, "policyAddress")

        // Fetch only the target context rule (single RPC call) and parse it
        val ruleScVal = kit.contextRuleManager.getContextRule(contextRuleId)
        val rule = kit.contextRuleManager.parseContextRule(ruleScVal)

        // Find the matching policy index by address
        val policyIndex = rule.policies.indexOfFirst { it == policyAddress }
        if (policyIndex == -1) {
            throw ValidationException.invalidInput(
                "policyAddress",
                "Policy $policyAddress not found on context rule $contextRuleId"
            )
        }

        // Bounds check: policyIds must be aligned with policies
        if (policyIndex >= rule.policyIds.size) {
            throw ValidationException.invalidInput(
                "policyAddress",
                "Policy found at index $policyIndex but policyIds has only ${rule.policyIds.size} entries"
            )
        }

        val policyId = rule.policyIds[policyIndex]
        return removePolicy(contextRuleId, policyId, selectedSigners, forceMethod)
    }

    // MARK: - Add Generic Policy

    /**
     * Adds a policy to a context rule with custom installation parameters.
     *
     * This is the generic method that [addSimpleThreshold], [addWeightedThreshold],
     * and [addSpendingLimit] delegate to. Use this method directly when adding
     * custom policy contracts that are not covered by the convenience methods.
     *
     * Flow:
     * 1. Validates inputs (connected wallet, policy address format)
     * 2. Builds contract invocation for add_policy
     * 3. Submits transaction via transactionOps (handles simulation, signing, polling)
     *
     * IMPORTANT: This operation requires the connected wallet to have authorization
     * on the smart account. The user will be prompted for biometric authentication
     * to sign the transaction.
     *
     * Contract limits:
     * - Maximum [OZConstants.MAX_POLICIES] policies per context rule
     * - Policy address must be a valid C-address
     *
     * Note: The contract returns a u32 policy ID for the newly added policy. The ID is available
     * via [ParsedContextRule.policyIds] after fetching the context rule and can be used with
     * [OZPolicyManager.removePolicy] to remove the policy.
     *
     * @param contextRuleId The context rule ID to add the policy to (0 for Default rule)
     * @param policyAddress The policy contract address (C-address)
     * @param installParams Policy-specific installation parameters encoded as [SCValXdr].
     *   The structure depends on the policy contract. For built-in policy types, use
     *   [PolicyInstallParams.SimpleThreshold.toScVal],
     *   [PolicyInstallParams.WeightedThreshold.toScVal], or
     *   [PolicyInstallParams.SpendingLimit.toScVal].
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return [TransactionResult] indicating success or failure
     * @throws ValidationException if validation fails
     * @throws TransactionException if transaction submission fails
     *
     * Example:
     * ```kotlin
     * // Add a custom policy with manually constructed install parameters
     * val installParams = Scv.toMap(linkedMapOf(
     *     Scv.toSymbol("max_operations") to Scv.toUint32(5u),
     *     Scv.toSymbol("allowed_contracts") to Scv.toVec(listOf(
     *         Scv.toAddress(Address("CBCD1234...").toSCAddress())
     *     ))
     * ))
     *
     * val result = policyManager.addPolicy(
     *     contextRuleId = 0u,
     *     policyAddress = "CBCD5678...",
     *     installParams = installParams
     * )
     *
     * if (result.success) {
     *     println("Custom policy added: ${result.hash ?: ""}")
     * } else {
     *     println("Failed to add policy: ${result.error ?: ""}")
     * }
     * ```
     */
    suspend fun addPolicy(
        contextRuleId: UInt,
        policyAddress: String,
        installParams: SCValXdr,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        // Validate wallet is connected
        val (_, contractId) = kit.requireConnected()

        // Validate policy address (must be C-address)
        requireContractAddress(policyAddress, "policyAddress")

        // Build host function
        val hostFunction = buildAddPolicyFunction(
            contractId = contractId,
            contextRuleId = contextRuleId,
            policyAddress = policyAddress,
            installParams = installParams
        )

        // Route to single-signer or multi-signer submission
        return if (selectedSigners.isEmpty()) {
            kit.transactionOperations.submit(hostFunction = hostFunction, auth = emptyList(), forceMethod = forceMethod)
        } else {
            kit.multiSignerManager.submitWithMultipleSigners(hostFunction, selectedSigners, forceMethod = forceMethod)
        }
    }

    /**
     * Adds a policy to a context rule using typed installation parameters.
     *
     * Convenience overload that encodes [installParams] via [PolicyInstallParams.toScVal]
     * and delegates to the [SCValXdr]-based [addPolicy]. For policy contracts not modelled
     * by a [PolicyInstallParams] variant, use the [SCValXdr] overload with parameters built
     * via the [Scv] factories.
     *
     * @param contextRuleId The context rule ID to add the policy to (0 for Default rule)
     * @param policyAddress The policy contract address (C-address)
     * @param installParams Typed installation parameters for one of the built-in policy types
     * @param selectedSigners Optional list of signers for multi-signer authorization.
     *   When empty (default), uses single-signer auth with the connected passkey.
     *   When non-empty, coordinates signatures from all listed signers.
     * @param forceMethod Optional submission method override. When null (default), uses the
     *   configured submission method (relayer if available, RPC otherwise).
     * @return [TransactionResult] indicating success or failure
     * @throws ValidationException if validation fails or the parameters are invalid
     * @throws TransactionException if transaction submission fails
     *
     * Example:
     * ```kotlin
     * val result = policyManager.addPolicy(
     *     contextRuleId = 0u,
     *     policyAddress = "CBCD5678...",
     *     installParams = PolicyInstallParams.SimpleThreshold(threshold = 2u)
     * )
     * ```
     */
    suspend fun addPolicy(
        contextRuleId: UInt,
        policyAddress: String,
        installParams: PolicyInstallParams,
        selectedSigners: List<SelectedSigner> = emptyList(),
        forceMethod: SubmissionMethod? = null
    ): TransactionResult {
        return addPolicy(
            contextRuleId = contextRuleId,
            policyAddress = policyAddress,
            installParams = installParams.toScVal(),
            selectedSigners = selectedSigners,
            forceMethod = forceMethod
        )
    }

    // MARK: - Private Helpers

    /**
     * Builds the host function for adding a policy.
     *
     * Contract method: add_policy(context_rule_id: u32, policy: Address, install_param: Val)
     *
     * @param contractId The smart account contract ID
     * @param contextRuleId The context rule ID
     * @param policyAddress The policy contract address
     * @param installParams The installation parameters
     * @return [HostFunctionXdr] for the add_policy invocation
     */
    private fun buildAddPolicyFunction(
        contractId: String,
        contextRuleId: UInt,
        policyAddress: String,
        installParams: SCValXdr
    ): HostFunctionXdr {
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("add_policy"),
            args = listOf(
                Scv.toUint32(contextRuleId),
                Scv.toAddress(Address(policyAddress).toSCAddress()),
                installParams
            )
        )
        return HostFunctionXdr.InvokeContract(invokeArgs)
    }

    /**
     * Builds the host function for removing a policy.
     *
     * Contract method: remove_policy(context_rule_id: u32, policy_id: u32)
     *
     * @param contractId The smart account contract ID
     * @param contextRuleId The context rule ID
     * @param policyId The on-chain policy ID to remove
     * @return [HostFunctionXdr] for the remove_policy invocation
     */
    private fun buildRemovePolicyFunction(
        contractId: String,
        contextRuleId: UInt,
        policyId: UInt
    ): HostFunctionXdr {
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("remove_policy"),
            args = listOf(
                Scv.toUint32(contextRuleId),
                Scv.toUint32(policyId)
            )
        )
        return HostFunctionXdr.InvokeContract(invokeArgs)
    }

    companion object {
        /**
         * Sorts ScMap entries by lexicographic comparison of their keys' XDR byte representation.
         *
         * Soroban mandates that ScMap keys are sorted lexicographically by their XDR-encoded
         * bytes. This function takes a LinkedHashMap of ScVal entries and returns a new
         * LinkedHashMap with entries sorted by their key's XDR encoding.
         *
         * @param map The unsorted map of ScVal key-value pairs
         * @return A new LinkedHashMap with entries sorted by XDR-encoded key bytes
         */
        fun sortMapByKeyXdr(map: LinkedHashMap<SCValXdr, SCValXdr>): LinkedHashMap<SCValXdr, SCValXdr> {
            val sorted = LinkedHashMap<SCValXdr, SCValXdr>()
            map.entries
                .sortedWith(Comparator { a, b ->
                    val aBytes = scValToXdrBytes(a.key)
                    val bBytes = scValToXdrBytes(b.key)
                    compareByteArraysLexicographically(aBytes, bBytes)
                })
                .forEach { (key, value) ->
                    sorted[key] = value
                }
            return sorted
        }

        /**
         * Encodes an SCValXdr to its XDR byte representation.
         * Used for deterministic key comparison when sorting ScMap entries.
         */
        internal fun scValToXdrBytes(scVal: SCValXdr): ByteArray {
            val writer = XdrWriter()
            scVal.encode(writer)
            return writer.toByteArray()
        }

        private fun compareByteArraysLexicographically(a: ByteArray, b: ByteArray): Int {
            val minLength = minOf(a.size, b.size)
            for (i in 0 until minLength) {
                val aByte = a[i].toInt() and 0xFF
                val bByte = b[i].toInt() and 0xFF
                if (aByte != bByte) {
                    return aByte - bByte
                }
            }
            return a.size - b.size
        }
    }
}
