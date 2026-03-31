//
//  OZContextRuleManager.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.HostFunctionXdr
import com.soneso.stellar.sdk.xdr.InvokeContractArgsXdr
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Type of context rule that determines which operations it applies to.
 *
 * Context rules use pattern matching to determine when signers and policies should be enforced.
 * Three types of context matching are supported:
 * - Default: Matches any operation (fallback rule)
 * - CallContract: Matches invocations to a specific contract address
 * - CreateContract: Matches contract deployments using a specific WASM hash
 *
 * Example usage:
 * ```kotlin
 * // Default rule applies to all operations
 * val defaultRule = ContextRuleType.Default
 *
 * // Rule for calling a specific token contract
 * val tokenRule = ContextRuleType.CallContract("CBCD1234...")
 *
 * // Rule for deploying contracts with a specific WASM hash
 * val deployRule = ContextRuleType.CreateContract(wasmHashData)
 * ```
 */
sealed class ContextRuleType {
    /**
     * Matches any operation (fallback/default rule).
     */
    object Default : ContextRuleType()

    /**
     * Matches invocations to a specific contract address.
     * @property contractAddress Contract address (C-address, 56 characters)
     */
    data class CallContract(val contractAddress: String) : ContextRuleType()

    /**
     * Matches contract deployments using a specific WASM hash.
     * @property wasmHash WASM hash (32 bytes)
     */
    data class CreateContract(val wasmHash: ByteArray) : ContextRuleType() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as CreateContract

            return wasmHash.contentEquals(other.wasmHash)
        }

        override fun hashCode(): Int {
            return wasmHash.contentHashCode()
        }
    }

    /**
     * Converts the context rule type to its on-chain ScVal representation.
     *
     * The on-chain representation is:
     * - Default: `ScVal::Vec([Symbol("Default")])`
     * - CallContract: `ScVal::Vec([Symbol("CallContract"), Address(contractAddress)])`
     * - CreateContract: `ScVal::Vec([Symbol("CreateContract"), Bytes(wasmHash)])`
     *
     * @return The SCVal representation of this context rule type
     * @throws ValidationException if address conversion fails
     *
     * Example:
     * ```kotlin
     * val ruleType = ContextRuleType.CallContract("CBCD1234...")
     * val scVal = ruleType.toScVal()
     * // ScVal::Vec([Symbol("CallContract"), Address("CBCD1234...")])
     * ```
     */
    fun toScVal(): SCValXdr {
        return when (this) {
            is Default -> Scv.toVec(listOf(Scv.toSymbol("Default")))

            is CallContract -> {
                try {
                    val scAddress = Address(contractAddress).toSCAddress()
                    Scv.toVec(listOf(
                        Scv.toSymbol("CallContract"),
                        Scv.toAddress(scAddress)
                    ))
                } catch (e: Exception) {
                    throw ValidationException.invalidAddress(contractAddress, e)
                }
            }

            is CreateContract -> {
                Scv.toVec(listOf(
                    Scv.toSymbol("CreateContract"),
                    Scv.toBytes(wasmHash)
                ))
            }
        }
    }
}

/**
 * Parsed representation of a context rule from on-chain data.
 *
 * Contains all the information about a context rule including its ID, type,
 * associated signers, policies, and optional expiration.
 *
 * @property id The unique identifier of this context rule
 * @property contextType The type of operations this rule applies to
 * @property name Human-readable name for the rule
 * @property signers List of signers who can authorize operations matching this context
 * @property policies List of policy contract addresses that constrain operations
 * @property validUntil Optional ledger number when this rule expires (null = no expiration)
 */
data class ParsedContextRule(
    val id: UInt,
    val contextType: ContextRuleType,
    val name: String,
    val signers: List<SmartAccountSigner>,
    val policies: List<String>,  // Policy contract addresses
    val validUntil: UInt?
)

/**
 * Manages context rules for OpenZeppelin Smart Accounts.
 *
 * Context rules define authorization requirements for different types of operations.
 * Each rule specifies:
 * - Context type: What operations does this rule apply to (default, call contract, create contract)
 * - Name: A human-readable identifier for the rule
 * - Signers: Who can authorize operations matching this context
 * - Policies: What constraints apply (spending limits, time locks, multi-sig thresholds, etc.)
 * - Valid until: Optional expiration ledger number
 *
 * The smart account evaluates transactions against context rules to determine:
 * 1. Which signers are required to authorize the transaction
 * 2. Which policies must be satisfied for the transaction to execute
 *
 * Contract limits:
 * - Maximum 15 context rules per smart account
 * - Maximum 15 signers per context rule
 * - Maximum 5 policies per context rule
 *
 * Example usage:
 * ```kotlin
 * val contextMgr = kit.contextRuleManager
 *
 * // Add a rule for token transfers requiring 2-of-3 multi-sig
 * val result = contextMgr.addContextRule(
 *     contextType = ContextRuleType.CallContract(tokenContractAddress),
 *     name = "TokenTransfers",
 *     validUntil = null,
 *     signers = listOf(signer1, signer2, signer3),
 *     policies = mapOf(
 *         thresholdPolicyAddress to thresholdScVal
 *     )
 * )
 *
 * // Get total count of context rules
 * val count = contextMgr.getContextRulesCount()
 *
 * // Remove a context rule
 * val removeResult = contextMgr.removeContextRule(id = ruleId)
 * ```
 */
class OZContextRuleManager internal constructor(
    private val kit: OZSmartAccountKit
) {
    // MARK: - Add Context Rule

    /**
     * Adds a new context rule to the smart account.
     *
     * Creates a context rule that defines authorization requirements for operations matching
     * the specified context type. The rule includes signers who can authorize matching operations
     * and policies that constrain how operations can be executed.
     *
     * Flow:
     * 1. Validates inputs (name, signers count, policies count)
     * 2. Checks that adding this rule won't exceed MAX_CONTEXT_RULES
     * 3. Builds contract invocation for add_context_rule
     * 4. Simulates to get auth entries
     * 5. Signs auth entries (requires user interaction)
     * 6. Submits transaction
     * 7. Polls for confirmation
     *
     * Contract limits enforced:
     * - Maximum 15 context rules per smart account (checked via getContextRulesCount)
     * - Maximum 15 signers per context rule
     * - Maximum 5 policies per context rule
     *
     * IMPORTANT: This is a state-changing operation requiring smart account authorization.
     * The user will be prompted for biometric authentication.
     *
     * @param contextType The type of context this rule applies to
     * @param name A human-readable name for the rule (e.g., "DefaultRule", "TokenTransfers")
     * @param validUntil Optional ledger number when this rule expires (null = no expiration)
     * @param signers List of signers who can authorize operations matching this context
     * @param policies Map of policy contract addresses to their installation parameters
     * @return TransactionResult indicating success or failure
     * @throws ValidationException if validation fails
     * @throws TransactionException if transaction submission fails
     *
     * Example:
     * ```kotlin
     * val result = contextMgr.addContextRule(
     *     contextType = ContextRuleType.CallContract("CBCD1234..."),
     *     name = "TokenOps",
     *     validUntil = 12345678u,
     *     signers = listOf(webAuthnSigner, delegatedSigner),
     *     policies = mapOf(
     *         thresholdPolicyAddress to Scv.toU32(2u),
     *         spendingLimitPolicyAddress to limitScVal
     *     )
     * )
     * if (result.success) {
     *     println("Context rule added. Hash: ${result.hash ?: ""}")
     * }
     * ```
     */
    suspend fun addContextRule(
        contextType: ContextRuleType,
        name: String,
        validUntil: UInt? = null,
        signers: List<SmartAccountSigner>,
        policies: Map<String, SCValXdr> = emptyMap()
    ): TransactionResult {
        val (_, contractId) = kit.requireConnected()

        // Validate inputs
        if (name.isEmpty()) {
            throw ValidationException.invalidInput("name", "Context rule name cannot be empty")
        }

        if (signers.isEmpty() || signers.size > OZConstants.MAX_SIGNERS) {
            throw ValidationException.invalidInput(
                "signers",
                "Context rule must have between 1 and ${OZConstants.MAX_SIGNERS} signers, got: ${signers.size}"
            )
        }

        if (policies.size > OZConstants.MAX_POLICIES) {
            throw ValidationException.invalidInput(
                "policies",
                "Context rule cannot have more than ${OZConstants.MAX_POLICIES} policies, got: ${policies.size}"
            )
        }

        // Validate policy addresses
        for ((address, _) in policies) {
            if (!address.startsWith("C") || address.length != 56) {
                throw ValidationException.invalidAddress("Policy address must be a valid C-address, got: $address")
            }
        }

        // Check MAX_CONTEXT_RULES limit
        val currentCount = getContextRulesCount()
        if (currentCount >= OZConstants.MAX_CONTEXT_RULES.toUInt()) {
            throw ValidationException.invalidInput(
                "contextRules",
                "Cannot add context rule: maximum of ${OZConstants.MAX_CONTEXT_RULES} rules already reached"
            )
        }

        // Build function arguments
        // arg 0: context_type (ScVal from contextType.toScVal())
        val contextTypeScVal = contextType.toScVal()

        // arg 1: name (String - Soroban String type, not Symbol)
        val nameScVal = Scv.toString(name)

        // arg 2: valid_until (Option<u32> - represented as void for None, u32 for Some)
        val validUntilScVal: SCValXdr = if (validUntil != null) {
            Scv.toUint32(validUntil)
        } else {
            Scv.toVoid()
        }

        // arg 3: signers (Vec<Signer>)
        val signersVec = signers.map { it.toScVal() }
        val signersScVal = Scv.toVec(signersVec)

        // arg 4: policies (Map<Address, ScVal> for policy address -> install param)
        // Keys must be sorted by XDR bytes (Soroban requirement for ScMap)
        val policiesMap = LinkedHashMap<SCValXdr, SCValXdr>()
        for ((address, installParam) in policies) {
            val policyAddress = Address(address).toSCAddress()
            policiesMap[Scv.toAddress(policyAddress)] = installParam
        }
        val sortedPoliciesMap = OZPolicyManager.sortMapByKeyXdr(policiesMap)
        val policiesScVal = Scv.toMap(sortedPoliciesMap)

        // Build invocation
        val functionArgs: List<SCValXdr> = listOf(
            contextTypeScVal,
            nameScVal,
            validUntilScVal,
            signersScVal,
            policiesScVal
        )

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("add_context_rule"),
            args = functionArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        // Submit transaction (will handle simulation, signing, and polling)
        return kit.transactionOperations.submit(hostFunction = hostFunction, auth = emptyList())
    }

    // MARK: - Get Context Rule

    /**
     * Retrieves a specific context rule by its ID.
     *
     * Queries the smart account contract for a context rule with the specified ID.
     * The raw SCVal response is returned, containing the rule details in encoded form.
     *
     * This is a query operation (read-only, no authorization required). It uses simulation
     * to extract the return value without submitting a transaction.
     *
     * The returned ScVal structure contains:
     * - id: u32
     * - context_type: Vec[Symbol, ...] (Default | CallContract | CreateContract)
     * - name: Symbol or String
     * - signers: Vec[signer ScVals]
     * - policies: Map[Address -> ScVal]
     * - valid_until: Option<u32> (void for None, u32 for Some)
     *
     * NOTE: Parsing the full context rule from ScVal is complex due to nested structures.
     * For initial implementation, this method returns the raw ScVal. Applications can
     * extract specific fields as needed.
     *
     * @param id The context rule ID to retrieve
     * @return The raw SCVal response containing the context rule details
     * @throws TransactionException if simulation fails or the rule doesn't exist
     *
     * Example:
     * ```kotlin
     * val ruleScVal = contextMgr.getContextRule(id = 1u)
     * // Parse ruleScVal to extract rule details
     * ```
     */
    suspend fun getContextRule(id: UInt): SCValXdr {
        val (_, contractId) = kit.requireConnected()

        // Build invocation
        val functionArgs: List<SCValXdr> = listOf(Scv.toUint32(id))

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("get_context_rule"),
            args = functionArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        // Query operation - simulate to get return value
        return kit.transactionOperations.simulateAndExtractResult(hostFunction = hostFunction)
    }

    // MARK: - Get Context Rules Count

    /**
     * Retrieves the total number of context rules in the smart account.
     *
     * Queries the smart account contract to determine how many context rules are
     * currently configured. This is useful for checking whether adding a new rule
     * would exceed the MAX_CONTEXT_RULES limit.
     *
     * This is a query operation (read-only, no authorization required). It uses simulation
     * to extract the return value without submitting a transaction.
     *
     * @return The number of context rules currently configured
     * @throws TransactionException if simulation fails
     * @throws ValidationException if parsing fails
     *
     * Example:
     * ```kotlin
     * val count = contextMgr.getContextRulesCount()
     * println("Smart account has $count context rules")
     *
     * if (count < OZConstants.MAX_CONTEXT_RULES.toUInt()) {
     *     // Can add more rules
     * }
     * ```
     */
    suspend fun getContextRulesCount(): UInt {
        val (_, contractId) = kit.requireConnected()

        // Build invocation (no arguments)
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("get_context_rules_count"),
            args = emptyList()
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        // Query operation - simulate to get return value
        val resultScVal = kit.transactionOperations.simulateAndExtractResult(hostFunction = hostFunction)

        // Parse U32 result
        return try {
            Scv.fromUint32(resultScVal)
        } catch (_: Exception) {
            throw ValidationException.invalidInput(
                "result",
                "Expected U32 result from get_context_rules_count, got: $resultScVal"
            )
        }
    }

    // MARK: - Get All Context Rules

    /**
     * Retrieves all active context rules as raw ScVal objects.
     *
     * The contract uses monotonically increasing IDs. When a rule is removed, its
     * ID slot becomes empty but is never reused, creating gaps. This method iterates
     * IDs from 0 upward until all active rules (per [getContextRulesCount]) have been
     * found, with [maxScanId] as a safety bound.
     *
     * @param maxScanId Upper bound on rule IDs to scan. Defaults to
     *   [OZSmartAccountConfig.maxContextRuleScanId].
     * @return List of raw ScVal objects, one per active context rule.
     */
    suspend fun getAllContextRules(maxScanId: UInt = kit.config.maxContextRuleScanId): List<SCValXdr> {
        val activeCount = try {
            getContextRulesCount()
        } catch (_: Exception) {
            0u
        }

        if (activeCount == 0u) return emptyList()

        val result = mutableListOf<SCValXdr>()

        for (id in 0u until maxScanId) {
            if (result.size.toUInt() >= activeCount) break
            try {
                val ruleScVal = getContextRule(id)
                result.add(ruleScVal)
            } catch (_: Exception) {
                // Gap from removed rule — skip
            }
        }

        return result
    }

    // MARK: - Update Context Rule Name

    /**
     * Updates the name of an existing context rule.
     *
     * Changes the human-readable name of the context rule with the specified ID.
     * The name is used for identification and has no effect on rule matching or enforcement.
     *
     * Flow:
     * 1. Validates inputs (name not empty)
     * 2. Builds contract invocation for update_context_rule_name
     * 3. Simulates to get auth entries
     * 4. Signs auth entries (requires user interaction)
     * 5. Submits transaction
     * 6. Polls for confirmation
     *
     * IMPORTANT: This is a state-changing operation requiring smart account authorization.
     * The user will be prompted for biometric authentication.
     *
     * @param id The ID of the context rule to update
     * @param name The new name for the context rule
     * @return TransactionResult indicating success or failure
     * @throws ValidationException if validation fails
     * @throws TransactionException if transaction submission fails
     *
     * Example:
     * ```kotlin
     * val result = contextMgr.updateName(
     *     id = 2u,
     *     name = "UpdatedTokenOps"
     * )
     * if (result.success) {
     *     println("Context rule name updated. Hash: ${result.hash ?: ""}")
     * }
     * ```
     */
    suspend fun updateName(id: UInt, name: String): TransactionResult {
        val (_, contractId) = kit.requireConnected()

        // Validate input
        if (name.isEmpty()) {
            throw ValidationException.invalidInput("name", "Context rule name cannot be empty")
        }

        // Build invocation (name is Soroban String type, not Symbol)
        val functionArgs: List<SCValXdr> = listOf(
            Scv.toUint32(id),
            Scv.toString(name)
        )

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("update_context_rule_name"),
            args = functionArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        // Submit transaction (will handle simulation, signing, and polling)
        return kit.transactionOperations.submit(hostFunction = hostFunction, auth = emptyList())
    }

    // MARK: - Update Context Rule Valid Until

    /**
     * Updates the expiration ledger of an existing context rule.
     *
     * Changes the ledger number at which the context rule expires. After expiration,
     * the rule will no longer apply to transactions. Pass null to remove the expiration.
     *
     * Flow:
     * 1. Builds contract invocation for update_context_rule_valid_until
     * 2. Simulates to get auth entries
     * 3. Signs auth entries (requires user interaction)
     * 4. Submits transaction
     * 5. Polls for confirmation
     *
     * IMPORTANT: This is a state-changing operation requiring smart account authorization.
     * The user will be prompted for biometric authentication.
     *
     * @param id The ID of the context rule to update
     * @param validUntil The new expiration ledger number, or null for no expiration
     * @return TransactionResult indicating success or failure
     * @throws TransactionException if transaction submission fails
     *
     * Example:
     * ```kotlin
     * // Set expiration to ledger 12345678
     * val result = contextMgr.updateValidUntil(
     *     id = 2u,
     *     validUntil = 12345678u
     * )
     *
     * // Remove expiration (rule never expires)
     * val result2 = contextMgr.updateValidUntil(
     *     id = 2u,
     *     validUntil = null
     * )
     * ```
     */
    suspend fun updateValidUntil(id: UInt, validUntil: UInt?): TransactionResult {
        val (_, contractId) = kit.requireConnected()

        // Build valid_until ScVal (Option<u32>)
        val validUntilScVal: SCValXdr = if (validUntil != null) {
            Scv.toUint32(validUntil)
        } else {
            Scv.toVoid()
        }

        // Build invocation
        val functionArgs: List<SCValXdr> = listOf(
            Scv.toUint32(id),
            validUntilScVal
        )

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("update_context_rule_valid_until"),
            args = functionArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        // Submit transaction (will handle simulation, signing, and polling)
        return kit.transactionOperations.submit(hostFunction = hostFunction, auth = emptyList())
    }

    // MARK: - Remove Context Rule

    /**
     * Removes a context rule from the smart account.
     *
     * Deletes the context rule with the specified ID from the smart account. Once removed,
     * the rule will no longer apply to future transactions.
     *
     * Flow:
     * 1. Validates that a wallet is connected
     * 2. Builds contract invocation for remove_context_rule
     * 3. Simulates to get auth entries
     * 4. Signs auth entries (requires user interaction)
     * 5. Submits transaction
     * 6. Polls for confirmation
     *
     * IMPORTANT: This is a state-changing operation requiring smart account authorization.
     * The user will be prompted for biometric authentication.
     *
     * @param id The ID of the context rule to remove
     * @return TransactionResult indicating success or failure
     * @throws TransactionException if the rule doesn't exist or transaction submission fails
     *
     * Example:
     * ```kotlin
     * val result = contextMgr.removeContextRule(id = 3u)
     * if (result.success) {
     *     println("Context rule removed. Hash: ${result.hash ?: ""}")
     * }
     * ```
     */
    suspend fun removeContextRule(id: UInt): TransactionResult {
        val (_, contractId) = kit.requireConnected()

        // Build invocation
        val functionArgs: List<SCValXdr> = listOf(Scv.toUint32(id))

        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = Address(contractId).toSCAddress(),
            functionName = SCSymbolXdr("remove_context_rule"),
            args = functionArgs
        )

        val hostFunction = HostFunctionXdr.InvokeContract(invokeArgs)

        // Submit transaction (will handle simulation, signing, and polling)
        return kit.transactionOperations.submit(hostFunction = hostFunction, auth = emptyList())
    }
}
