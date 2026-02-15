//
//  OZPolicyManager.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCMapEntryXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr

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
 * // Create a spending limit (1000 XLM per day)
 * val spendingPolicy = PolicyInstallParams.SpendingLimit(
 *     spendingLimit = 1000L * 10_000_000L, // Convert XLM to stroops
 *     periodLedgers = SmartAccountConstants.LEDGERS_PER_DAY.toUInt()
 * )
 * ```
 */
sealed class PolicyInstallParams {
    /**
     * Simple threshold policy requiring exactly M-of-N signers to authorize.
     *
     * All signers in the context rule have equal weight (1 vote each).
     * The threshold specifies how many signers must approve.
     *
     * @property threshold Number of signers required to authorize (1 to signer count)
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
         */
        internal fun toScVal(): SCValXdr {
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
         * @throws ValidationException if signer weights map is empty
         */
        internal fun toScVal(): SCValXdr {
            // Validate signer weights array
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
            val sortedWeightsMap = SmartAccountSharedUtils.sortMapByKeyXdr(weightsMap)

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
     * @property spendingLimit Maximum amount in stroops for the period
     * @property periodLedgers Time window in ledgers (e.g., 17,280 for one day)
     */
    data class SpendingLimit(
        val spendingLimit: Long,
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
        internal fun toScVal(): SCValXdr {
            // Validate inputs
            if (spendingLimit <= 0) {
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
            val limitI128 = SmartAccountSharedUtils.stroopsToI128ScVal(spendingLimit)

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
 * A context rule can have multiple policies (up to [SmartAccountConstants.MAX_POLICIES]),
 * and all policies must be satisfied for a transaction to succeed.
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
 * // Add a simple threshold policy (2-of-3)
 * val result = policyManager.addSimpleThreshold(
 *     contextRuleId = 0u,
 *     policyAddress = "CBCD1234...",
 *     threshold = 2u
 * )
 *
 * // Add a custom policy with raw install parameters
 * val customResult = policyManager.addPolicy(
 *     contextRuleId = 0u,
 *     policyAddress = "CBCD5678...",
 *     installParams = myCustomPolicyParams
 * )
 *
 * if (result.success) {
 *     println("Policy added successfully")
 * }
 *
 * // Remove a policy
 * val removeResult = policyManager.removePolicy(
 *     contextRuleId = 0u,
 *     policyAddress = "CBCD1234..."
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
     * Flow:
     * 1. Validates inputs (connected wallet, policy address format, threshold)
     * 2. Encodes policy installation parameters
     * 3. Builds contract invocation for add_policy
     * 4. Submits transaction via transactionOps (handles simulation, signing, polling)
     *
     * IMPORTANT: This operation requires the connected wallet to have authorization
     * on the smart account. The user will be prompted for biometric authentication
     * to sign the transaction.
     *
     * Contract limits:
     * - Maximum [SmartAccountConstants.MAX_POLICIES] policies per context rule
     * - Policy address must be a valid C-address
     * - Threshold must be greater than zero
     *
     * @param contextRuleId The context rule ID to add the policy to (0 for Default rule)
     * @param policyAddress The policy contract address (C-address)
     * @param threshold Number of signers required to authorize (1 to signer count)
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
        threshold: UInt
    ): TransactionResult {
        val params = PolicyInstallParams.SimpleThreshold(threshold)
        return addPolicy(contextRuleId, policyAddress, params.toScVal())
    }

    // MARK: - Add Weighted Threshold Policy

    /**
     * Adds a weighted threshold policy to a context rule.
     *
     * A weighted threshold policy assigns different weights (vote power) to each signer.
     * The sum of weights from approving signers must meet or exceed the threshold.
     *
     * Flow:
     * 1. Validates inputs (connected wallet, policy address format, signer weights)
     * 2. Encodes policy installation parameters
     * 3. Builds contract invocation for add_policy
     * 4. Submits transaction via transactionOps (handles simulation, signing, polling)
     *
     * IMPORTANT: This operation requires the connected wallet to have authorization
     * on the smart account. The user will be prompted for biometric authentication
     * to sign the transaction.
     *
     * Contract limits:
     * - Maximum [SmartAccountConstants.MAX_POLICIES] policies per context rule
     * - Policy address must be a valid C-address
     * - At least one signer weight must be specified
     * - Threshold must be greater than zero
     *
     * @param contextRuleId The context rule ID to add the policy to (0 for Default rule)
     * @param policyAddress The policy contract address (C-address)
     * @param signerWeights Map of signers to their weights defining vote power
     * @param threshold Minimum total weight required for authorization
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
        threshold: UInt
    ): TransactionResult {
        val params = PolicyInstallParams.WeightedThreshold(signerWeights, threshold)
        return addPolicy(contextRuleId, policyAddress, params.toScVal())
    }

    // MARK: - Add Spending Limit Policy

    /**
     * Adds a spending limit policy to a context rule.
     *
     * A spending limit policy restricts the total amount that can be spent within
     * a rolling time window. The period is specified in ledgers (approximately
     * 5 seconds per ledger, 720 per hour, 17,280 per day).
     *
     * Flow:
     * 1. Validates inputs (connected wallet, policy address format, limits)
     * 2. Converts XLM amount to stroops
     * 3. Encodes policy installation parameters
     * 4. Builds contract invocation for add_policy
     * 5. Submits transaction via transactionOps (handles simulation, signing, polling)
     *
     * IMPORTANT: This operation requires the connected wallet to have authorization
     * on the smart account. The user will be prompted for biometric authentication
     * to sign the transaction.
     *
     * Contract limits:
     * - Maximum [SmartAccountConstants.MAX_POLICIES] policies per context rule
     * - Policy address must be a valid C-address
     * - Spending limit must be greater than zero
     * - Period ledgers must be greater than zero
     *
     * @param contextRuleId The context rule ID to add the policy to (0 for Default rule)
     * @param policyAddress The policy contract address (C-address)
     * @param spendingLimit Maximum amount per period in XLM (will be converted to stroops)
     * @param periodLedgers Period duration in ledgers (17,280 = approximately 1 day)
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
     *     spendingLimit = 1000.0,
     *     periodLedgers = SmartAccountConstants.LEDGERS_PER_DAY.toUInt()
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
        spendingLimit: Double,
        periodLedgers: UInt
    ): TransactionResult {
        val stroops = SmartAccountSharedUtils.amountToStroops(spendingLimit)
        val params = PolicyInstallParams.SpendingLimit(stroops, periodLedgers)
        return addPolicy(contextRuleId, policyAddress, params.toScVal())
    }

    // MARK: - Remove Policy

    /**
     * Removes a policy from a context rule.
     *
     * Uninstalls an existing policy from the specified context rule. The policy
     * contract remains deployed on the network but is no longer evaluated for
     * this context rule.
     *
     * Flow:
     * 1. Validates inputs (connected wallet, policy address format)
     * 2. Builds contract invocation for remove_policy
     * 3. Submits transaction via transactionOps (handles simulation, signing, polling)
     *
     * IMPORTANT: This operation requires the connected wallet to have authorization
     * on the smart account. The user will be prompted for biometric authentication
     * to sign the transaction.
     *
     * @param contextRuleId The context rule ID to remove the policy from (0 for Default rule)
     * @param policyAddress The policy contract address to remove (C-address)
     * @return [TransactionResult] indicating success or failure
     * @throws ValidationException if validation fails
     * @throws TransactionException if transaction submission fails
     *
     * Example:
     * ```kotlin
     * // Remove a policy from the default context rule
     * val result = policyManager.removePolicy(
     *     contextRuleId = 0u,
     *     policyAddress = "CBCD1234..."
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
        policyAddress: String
    ): TransactionResult {
        // Validate wallet is connected
        val (_, contractId) = kit.requireConnected()

        // Validate policy address (must be C-address)
        if (!policyAddress.startsWith("C") || policyAddress.length != 56) {
            throw ValidationException.invalidAddress(
                "Policy address must be a valid C-address, got: $policyAddress"
            )
        }

        // Build host function
        val hostFunction = buildRemovePolicyFunction(
            contractId = contractId,
            contextRuleId = contextRuleId,
            policyAddress = policyAddress
        )

        // Submit transaction
        return kit.transactionOperations.submit(hostFunction = hostFunction, auth = emptyList())
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
     * - Maximum [SmartAccountConstants.MAX_POLICIES] policies per context rule
     * - Policy address must be a valid C-address
     *
     * @param contextRuleId The context rule ID to add the policy to (0 for Default rule)
     * @param policyAddress The policy contract address (C-address)
     * @param installParams Policy-specific installation parameters encoded as [SCValXdr].
     *   The structure depends on the policy contract. For built-in policy types, use
     *   [PolicyInstallParams.SimpleThreshold.toScVal],
     *   [PolicyInstallParams.WeightedThreshold.toScVal], or
     *   [PolicyInstallParams.SpendingLimit.toScVal].
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
        installParams: SCValXdr
    ): TransactionResult {
        // Validate wallet is connected
        val (_, contractId) = kit.requireConnected()

        // Validate policy address (must be C-address)
        if (!policyAddress.startsWith("C") || policyAddress.length != 56) {
            throw ValidationException.invalidAddress(
                "Policy address must be a valid C-address, got: $policyAddress"
            )
        }

        // Build host function
        val hostFunction = buildAddPolicyFunction(
            contractId = contractId,
            contextRuleId = contextRuleId,
            policyAddress = policyAddress,
            installParams = installParams
        )

        // Submit transaction
        return kit.transactionOperations.submit(hostFunction = hostFunction, auth = emptyList())
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
     * @return HostFunctionXDR for the add_policy invocation
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
     * Contract method: remove_policy(context_rule_id: u32, policy: Address)
     *
     * @param contractId The smart account contract ID
     * @param contextRuleId The context rule ID
     * @param policyAddress The policy contract address
     * @return HostFunctionXDR for the remove_policy invocation
     */
    private fun buildRemovePolicyFunction(
        contractId: String,
        contextRuleId: UInt,
        policyAddress: String
    ): HostFunctionXdr {
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("remove_policy"),
            args = listOf(
                Scv.toUint32(contextRuleId),
                Scv.toAddress(Address(policyAddress).toSCAddress())
            )
        )
        return HostFunctionXdr.InvokeContract(invokeArgs)
    }
}
