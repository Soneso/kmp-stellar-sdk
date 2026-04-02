//
//  OZContextRuleManager.kt
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
import com.soneso.stellar.sdk.xdr.SCSymbolXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizationEntryXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedFunctionXdr
import com.soneso.stellar.sdk.xdr.SorobanAuthorizedInvocationXdr

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

            return Util.constantTimeEquals(wasmHash, other.wasmHash)
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
 * @property signerIds Positionally-aligned signer IDs corresponding to [signers]
 * @property policies List of policy contract addresses that constrain operations
 * @property policyIds Positionally-aligned policy IDs corresponding to [policies]
 * @property validUntil Optional ledger number when this rule expires (null = no expiration)
 */
data class ParsedContextRule(
    val id: UInt,
    val contextType: ContextRuleType,
    val name: String,
    val signers: List<SmartAccountSigner>,
    val signerIds: List<UInt>,
    val policies: List<String>,
    val policyIds: List<UInt>,
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
     * 2. Builds contract invocation for add_context_rule
     * 3. Simulates to get auth entries
     * 4. Signs auth entries (requires user interaction)
     * 5. Submits transaction
     * 6. Polls for confirmation
     *
     * Contract limits enforced:
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

        if (signers.isEmpty() && policies.isEmpty()) {
            throw ValidationException.invalidInput(
                "signers",
                "Context rule must have at least one signer or one policy"
            )
        }

        if (signers.size > OZConstants.MAX_SIGNERS) {
            throw ValidationException.invalidInput(
                "signers",
                "Context rule cannot have more than ${OZConstants.MAX_SIGNERS} signers, got: ${signers.size}"
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
            requireContractAddress(address, "contractAddress")
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
     * currently configured.
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
        val activeCount = getContextRulesCount()

        if (activeCount == 0u) return emptyList()

        val result = mutableListOf<SCValXdr>()

        for (id in 0u until maxScanId) {
            if (result.size.toUInt() >= activeCount) break
            try {
                val ruleScVal = getContextRule(id)
                result.add(ruleScVal)
            } catch (_: TransactionException.SimulationFailed) {
                // Gap from removed rule — skip
            }
        }

        return result
    }

    // MARK: - List Context Rules

    /**
     * Lists all active context rules as parsed objects.
     *
     * Fetches all raw ScVal rules via [getAllContextRules] and parses each one.
     * This is the primary method for rule discovery in v0.7.0.
     *
     * @param maxScanId Upper bound on rule IDs to scan
     * @return List of parsed context rules
     * @throws ValidationException if a rule ScVal cannot be parsed
     * @throws TransactionException if simulation fails
     */
    suspend fun listContextRules(maxScanId: UInt = kit.config.maxContextRuleScanId): List<ParsedContextRule> {
        return getAllContextRules(maxScanId).map { scVal -> parseContextRule(scVal) }
    }

    /**
     * Parses a context rule from its on-chain ScVal representation.
     *
     * The Soroban named struct serializes as ScVal::Map with Symbol-keyed entries
     * sorted lexicographically. Fields are looked up by key name, not by position.
     * Expected keys (alphabetical): context_type, id, name, policies, policy_ids,
     * signer_ids, signers, valid_until.
     *
     * @param scVal The raw ScVal containing the context rule data
     * @return The parsed context rule
     * @throws ValidationException if the ScVal structure is invalid or required fields are missing
     */
    private fun parseContextRule(scVal: SCValXdr): ParsedContextRule {
        val mapEntries = (scVal as? SCValXdr.Map)?.value?.value
            ?: throw ValidationException.invalidInput("contextRule", "Expected Map ScVal for context rule")

        // Build a lookup by symbol key
        val fields = mutableMapOf<String, SCValXdr>()
        for (entry in mapEntries) {
            val key = (entry.key as? SCValXdr.Sym)?.value?.value ?: continue
            fields[key] = entry.`val`
        }

        // Parse id (U32)
        val id = fields["id"]?.let {
            try { Scv.fromUint32(it) } catch (e: Exception) {
                throw ValidationException.invalidInput("id", "Expected U32 for id: ${e.message}")
            }
        } ?: throw ValidationException.invalidInput("contextRule", "Missing required field: id")

        // Parse name (String)
        val name = fields["name"]?.let {
            try { Scv.fromString(it) } catch (e: Exception) {
                throw ValidationException.invalidInput("name", "Expected String for name: ${e.message}")
            }
        } ?: throw ValidationException.invalidInput("contextRule", "Missing required field: name")

        // Parse context_type (Vec)
        val contextType = fields["context_type"]?.let { parseContextRuleType(it) }
            ?: throw ValidationException.invalidInput("contextRule", "Missing required field: context_type")

        // Parse signers (Vec of signer ScVals)
        val signers = fields["signers"]?.let { signerVecScVal ->
            val signerVec = try { Scv.fromVec(signerVecScVal) } catch (e: Exception) {
                throw ValidationException.invalidInput("signers", "Expected Vec for signers: ${e.message}")
            }
            signerVec.map { parseSigner(it) }
        } ?: emptyList()

        // Parse signer_ids (Vec of U32)
        val signerIds = fields["signer_ids"]?.let { signerIdsScVal ->
            val vec = try { Scv.fromVec(signerIdsScVal) } catch (e: Exception) {
                throw ValidationException.invalidInput("signer_ids", "Expected Vec for signer_ids: ${e.message}")
            }
            vec.map { entry ->
                try { Scv.fromUint32(entry) } catch (e: Exception) {
                    throw ValidationException.invalidInput("signer_ids", "Expected U32 entries in signer_ids: ${e.message}")
                }
            }
        } ?: emptyList()

        // Parse policies (Vec of Address)
        val policies = fields["policies"]?.let { policiesScVal ->
            val vec = try { Scv.fromVec(policiesScVal) } catch (e: Exception) {
                throw ValidationException.invalidInput("policies", "Expected Vec for policies: ${e.message}")
            }
            vec.map { entry ->
                try { Address.fromSCVal(entry).toString() } catch (e: Exception) {
                    throw ValidationException.invalidInput("policies", "Expected Address entries in policies: ${e.message}")
                }
            }
        } ?: emptyList()

        // Parse policy_ids (Vec of U32)
        val policyIds = fields["policy_ids"]?.let { policyIdsScVal ->
            val vec = try { Scv.fromVec(policyIdsScVal) } catch (e: Exception) {
                throw ValidationException.invalidInput("policy_ids", "Expected Vec for policy_ids: ${e.message}")
            }
            vec.map { entry ->
                try { Scv.fromUint32(entry) } catch (e: Exception) {
                    throw ValidationException.invalidInput("policy_ids", "Expected U32 entries in policy_ids: ${e.message}")
                }
            }
        } ?: emptyList()

        // Parse valid_until (Option<U32>: void = null, U32 = value)
        val validUntil = fields["valid_until"]?.let { validUntilScVal ->
            when (validUntilScVal) {
                is SCValXdr.Void -> null
                else -> try { Scv.fromUint32(validUntilScVal) } catch (e: Exception) {
                    throw ValidationException.invalidInput("valid_until", "Expected U32 or Void for valid_until: ${e.message}")
                }
            }
        }

        return ParsedContextRule(
            id = id,
            contextType = contextType,
            name = name,
            signers = signers,
            signerIds = signerIds,
            policies = policies,
            policyIds = policyIds,
            validUntil = validUntil
        )
    }

    /**
     * Parses the context_type field from its Vec ScVal representation.
     *
     * Format:
     * - Default: Vec([Symbol("Default")])
     * - CallContract: Vec([Symbol("CallContract"), Address(contractAddress)])
     * - CreateContract: Vec([Symbol("CreateContract"), Bytes(wasmHash)])
     */
    private fun parseContextRuleType(scVal: SCValXdr): ContextRuleType {
        val vec = try { Scv.fromVec(scVal) } catch (e: Exception) {
            throw ValidationException.invalidInput("context_type", "Expected Vec for context_type: ${e.message}")
        }
        if (vec.isEmpty()) {
            throw ValidationException.invalidInput("context_type", "context_type Vec is empty")
        }
        val discriminant = try { Scv.fromSymbol(vec[0]) } catch (e: Exception) {
            throw ValidationException.invalidInput("context_type", "Expected Symbol discriminant in context_type Vec: ${e.message}")
        }
        return when (discriminant) {
            "Default" -> ContextRuleType.Default
            "CallContract" -> {
                if (vec.size < 2) {
                    throw ValidationException.invalidInput("context_type", "CallContract context_type missing address element")
                }
                val address = try { Address.fromSCVal(vec[1]).toString() } catch (e: Exception) {
                    throw ValidationException.invalidInput("context_type", "Expected Address for CallContract context_type: ${e.message}")
                }
                ContextRuleType.CallContract(address)
            }
            "CreateContract" -> {
                if (vec.size < 2) {
                    throw ValidationException.invalidInput("context_type", "CreateContract context_type missing wasm hash element")
                }
                val wasmHash = try { Scv.fromBytes(vec[1]) } catch (e: Exception) {
                    throw ValidationException.invalidInput("context_type", "Expected Bytes for CreateContract context_type: ${e.message}")
                }
                ContextRuleType.CreateContract(wasmHash)
            }
            else -> throw ValidationException.invalidInput("context_type", "Unknown context_type discriminant: $discriminant")
        }
    }

    /**
     * Parses a signer from its Vec ScVal representation.
     *
     * Format:
     * - Delegated: Vec([Symbol("Delegated"), Address])
     * - External: Vec([Symbol("External"), Address, Bytes])
     */
    private fun parseSigner(scVal: SCValXdr): SmartAccountSigner {
        val vec = try { Scv.fromVec(scVal) } catch (e: Exception) {
            throw ValidationException.invalidInput("signer", "Expected Vec for signer: ${e.message}")
        }
        if (vec.isEmpty()) {
            throw ValidationException.invalidInput("signer", "Signer Vec is empty")
        }
        val discriminant = try { Scv.fromSymbol(vec[0]) } catch (e: Exception) {
            throw ValidationException.invalidInput("signer", "Expected Symbol discriminant in signer Vec: ${e.message}")
        }
        return when (discriminant) {
            "Delegated" -> {
                if (vec.size < 2) {
                    throw ValidationException.invalidInput("signer", "Delegated signer missing address element")
                }
                val address = try { Address.fromSCVal(vec[1]).toString() } catch (e: Exception) {
                    throw ValidationException.invalidInput("signer", "Expected Address for Delegated signer: ${e.message}")
                }
                DelegatedSigner(address)
            }
            "External" -> {
                if (vec.size < 3) {
                    throw ValidationException.invalidInput("signer", "External signer missing address or keyData element")
                }
                val verifierAddress = try { Address.fromSCVal(vec[1]).toString() } catch (e: Exception) {
                    throw ValidationException.invalidInput("signer", "Expected Address for External signer verifier: ${e.message}")
                }
                val keyData = try { Scv.fromBytes(vec[2]) } catch (e: Exception) {
                    throw ValidationException.invalidInput("signer", "Expected Bytes for External signer keyData: ${e.message}")
                }
                ExternalSigner(verifierAddress, keyData)
            }
            else -> throw ValidationException.invalidInput("signer", "Unknown signer discriminant: $discriminant")
        }
    }

    // MARK: - Resolve Context Rule IDs

    /**
     * Resolves context rule IDs for a given auth entry.
     *
     * Analyzes the auth entry's invocations to determine which context types are needed,
     * then matches those against the account's active rules. The resolution algorithm:
     * 1. Filter rules by context type match
     * 2. If 1 candidate: use it
     * 3. If multiple: filter by exact signer match (same signers, same count)
     * 4. If 1 match: use it
     * 5. If multiple: filter by signer subset (rule signers subset of selected, no policies)
     * 6. If 1 match: use it
     * 7. Otherwise: throw error
     *
     * @param entry The auth entry to resolve rules for
     * @param selectedSigners The signers that will sign this entry
     * @return Ordered list of context rule IDs, one per invocation context
     * @throws ValidationException if no unique context rule can be resolved for any context
     * @throws TransactionException if simulation fails while fetching rules
     */
    suspend fun resolveContextRuleIdsForEntry(
        entry: SorobanAuthorizationEntryXdr,
        selectedSigners: List<SmartAccountSigner>
    ): List<UInt> {
        val rules = listContextRules()
        return resolveContextRuleIdsForEntry(entry, selectedSigners, rules)
    }

    /**
     * Resolves context rule IDs using a pre-fetched rules list.
     *
     * Use this overload to avoid redundant RPC calls when resolving multiple auth entries
     * in the same transaction. Fetch rules once via [listContextRules] and pass them here.
     *
     * @param entry The auth entry to resolve rules for
     * @param selectedSigners The signers that will sign this entry
     * @param rules Pre-fetched list of active context rules
     * @return Ordered list of context rule IDs, one per invocation context
     * @throws ValidationException if no unique context rule can be resolved for any context
     */
    fun resolveContextRuleIdsForEntry(
        entry: SorobanAuthorizationEntryXdr,
        selectedSigners: List<SmartAccountSigner>,
        rules: List<ParsedContextRule>
    ): List<UInt> {
        val contexts = buildInvocationContextTypes(entry)

        return contexts.map { contextType ->
            val candidates = rules.filter { rule ->
                contextRuleTypeMatches(rule.contextType, contextType)
            }

            if (candidates.size == 1) {
                return@map candidates[0].id
            }

            // Tier 1: Exact signer match — same number of signers and all match bidirectionally
            val exactSignerMatches = candidates.filter { rule ->
                if (rule.signers.size != selectedSigners.size) return@filter false

                selectedSigners.all { selected ->
                    rule.signers.any { ruleSigner ->
                        SmartAccountBuilders.signersEqual(ruleSigner, selected)
                    }
                } && rule.signers.all { ruleSigner ->
                    selectedSigners.any { selected ->
                        SmartAccountBuilders.signersEqual(ruleSigner, selected)
                    }
                }
            }

            if (exactSignerMatches.size == 1) {
                return@map exactSignerMatches[0].id
            }

            // Tier 2: Rule signers subset of selected (all rule signers in selectedSigners, no policies)
            val signerSubsetMatches = candidates.filter { rule ->
                if (rule.policies.isNotEmpty()) return@filter false

                rule.signers.all { ruleSigner ->
                    selectedSigners.any { selected ->
                        SmartAccountBuilders.signersEqual(ruleSigner, selected)
                    }
                }
            }

            if (signerSubsetMatches.size == 1) {
                return@map signerSubsetMatches[0].id
            }

            // Tier 3: Selected signers subset of rule (all selected signers exist in the rule).
            // Handles threshold scenarios where the user picks fewer signers than the rule has.
            val selectedSubsetMatches = candidates.filter { rule ->
                selectedSigners.all { selected ->
                    rule.signers.any { ruleSigner ->
                        SmartAccountBuilders.signersEqual(ruleSigner, selected)
                    }
                }
            }

            if (selectedSubsetMatches.size == 1) {
                return@map selectedSubsetMatches[0].id
            }

            if (candidates.isEmpty()) {
                throw ValidationException.invalidInput(
                    "contextRuleIds",
                    "No context rule matches $contextType. Add a rule for this context type or a Default rule."
                )
            }

            if (selectedSubsetMatches.size > 1) {
                val ids = selectedSubsetMatches.joinToString(", ") { it.id.toString() }
                throw ValidationException.invalidInput(
                    "contextRuleIds",
                    "Selected signers match multiple context rules: $ids."
                )
            }

            throw ValidationException.invalidInput(
                "contextRuleIds",
                "No context rule contains all selected signers."
            )
        }
    }

    // MARK: - Build Invocation Context Types

    /**
     * Extracts context types from an auth entry's invocation tree.
     *
     * Walks the invocation tree recursively and extracts the context type for each invocation:
     * - ContractFn invocations produce [ContextRuleType.CallContract] with the contract address
     * - Create contract invocations produce [ContextRuleType.CreateContract] with the WASM hash
     *
     * @param entry The auth entry whose invocation tree to traverse
     * @return Ordered list of context types, one per invocation node (depth-first)
     */
    fun buildInvocationContextTypes(entry: SorobanAuthorizationEntryXdr): List<ContextRuleType> {
        val result = mutableListOf<ContextRuleType>()
        collectInvocationContextTypes(entry.rootInvocation.function, result)
        collectSubInvocationContextTypes(entry.rootInvocation.subInvocations, result)
        return result
    }

    private fun collectInvocationContextTypes(
        function: SorobanAuthorizedFunctionXdr,
        result: MutableList<ContextRuleType>
    ) {
        when (function) {
            is SorobanAuthorizedFunctionXdr.ContractFn -> {
                val contractAddress = try {
                    Address.fromSCAddress(function.value.contractAddress).toString()
                } catch (e: Exception) {
                    throw ValidationException.invalidInput(
                        "contractAddress",
                        "Failed to parse contract address from ContractFn invocation: ${e.message}"
                    )
                }
                result.add(ContextRuleType.CallContract(contractAddress))
            }
            is SorobanAuthorizedFunctionXdr.CreateContractHostFn -> {
                val wasmHash = extractWasmHash(function.value.executable)
                result.add(ContextRuleType.CreateContract(wasmHash))
            }
            is SorobanAuthorizedFunctionXdr.CreateContractV2HostFn -> {
                val wasmHash = extractWasmHash(function.value.executable)
                result.add(ContextRuleType.CreateContract(wasmHash))
            }
        }
    }

    private fun collectSubInvocationContextTypes(
        subInvocations: List<SorobanAuthorizedInvocationXdr>,
        result: MutableList<ContextRuleType>
    ) {
        for (subInvocation in subInvocations) {
            collectInvocationContextTypes(subInvocation.function, result)
            collectSubInvocationContextTypes(subInvocation.subInvocations, result)
        }
    }

    private fun extractWasmHash(executable: ContractExecutableXdr): ByteArray {
        return when (executable) {
            is ContractExecutableXdr.WasmHash -> executable.value.value
            is ContractExecutableXdr.Void -> throw ValidationException.invalidInput(
                "executable",
                "CreateContract invocation references a Stellar Asset Contract, not a WASM contract"
            )
        }
    }

    // MARK: - Context Rule Type Matching

    /**
     * Checks if a rule's context type matches a required context type.
     *
     * [ContextRuleType.Default] matches any required context type, acting as a fallback.
     * Other types require an exact equality match.
     *
     * @param ruleType The context type defined in the rule
     * @param requiredType The context type required by the invocation
     * @return true if the rule's context type matches the required type
     */
    fun contextRuleTypeMatches(ruleType: ContextRuleType, requiredType: ContextRuleType): Boolean {
        if (ruleType is ContextRuleType.Default) return true
        return ruleType == requiredType
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
