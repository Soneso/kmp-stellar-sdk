package com.soneso.smartdemo.util

import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.smartaccount.oz.SmartAccountSharedUtils
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Fetches all context rules from the connected smart account.
 *
 * Queries the total rule count and fetches each rule individually by ID. Falls back
 * to fetching Default context rules directly if the count returns zero. Rules are
 * returned sorted by ID with duplicates removed.
 *
 * @param kit The initialized [OZSmartAccountKit] instance.
 * @throws IllegalStateException if kit is not initialized.
 */
suspend fun fetchAllContextRules(kit: OZSmartAccountKit): List<ParsedContextRule> {
    val contextMgr = kit.contextRuleManager

    val allRules = mutableListOf<ParsedContextRule>()
    val seenIds = mutableSetOf<UInt>()

    val totalCount = try {
        contextMgr.getContextRulesCount()
    } catch (e: Exception) {
        ActivityLogState.error("Failed to get rule count: ${e.message}")
        0u
    }

    if (totalCount == 0u) {
        try {
            val defaultScVal = contextMgr.getContextRules(ContextRuleType.Default)
            val defaultRules = parseContextRulesFromScVal(defaultScVal)
            for (rule in defaultRules) {
                if (seenIds.add(rule.id)) allRules.add(rule)
            }
        } catch (_: Exception) {
            // No default rules or contract doesn't support this call
        }
        return allRules.sortedBy { it.id }
    }

    for (id in 0u until totalCount) {
        try {
            val ruleScVal = contextMgr.getContextRule(id)
            val parsed = parseSingleContextRuleFromScVal(ruleScVal, id)
            if (parsed != null && seenIds.add(parsed.id)) {
                allRules.add(parsed)
            }
        } catch (e: Exception) {
            ActivityLogState.info("Rule #$id not found or could not be parsed")
        }
    }

    return allRules.sortedBy { it.id }
}

/**
 * Convenience overload that reads the kit from [DemoState].
 *
 * @throws IllegalStateException if DemoState.kit is null.
 */
suspend fun fetchAllContextRules(): List<ParsedContextRule> {
    val kit = DemoState.kit ?: throw IllegalStateException("Kit not initialized")
    return fetchAllContextRules(kit)
}

/**
 * Parses a Vec of context rules returned by get_context_rules.
 *
 * The returned SCVal is a Vec where each element is a Map (struct) with fields:
 * context_type, id, name, policies, signers, valid_until.
 */
fun parseContextRulesFromScVal(scVal: SCValXdr): List<ParsedContextRule> {
    val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return emptyList()
    return vec.mapNotNull { element ->
        parseSingleContextRuleFromScVal(element, null)
    }
}

/**
 * Parses a single context rule from its ScVal map representation.
 *
 * Soroban structs are encoded as ScMap with Symbol keys in alphabetical order:
 * { context_type, id, name, policies, signers, valid_until }
 *
 * @param scVal The ScVal to parse.
 * @param fallbackId Used as the rule ID if the map does not contain an "id" field.
 * @return A [ParsedContextRule] or null if the ScVal is not a valid map.
 */
fun parseSingleContextRuleFromScVal(
    scVal: SCValXdr,
    fallbackId: UInt?
): ParsedContextRule? {
    val map = (scVal as? SCValXdr.Map)?.value?.value ?: return null

    var id: UInt = fallbackId ?: 0u
    var contextType: ContextRuleType = ContextRuleType.Default
    var name = ""
    var signers = listOf<SmartAccountSigner>()
    var policies = listOf<String>()
    var validUntil: UInt? = null

    for (entry in map) {
        val fieldName = (entry.key as? SCValXdr.Sym)?.value?.value ?: continue
        val fieldValue = entry.`val`

        when (fieldName) {
            "id" -> {
                id = (fieldValue as? SCValXdr.U32)?.value?.value ?: (fallbackId ?: 0u)
            }
            "context_type" -> {
                contextType = parseContextType(fieldValue)
            }
            "name" -> {
                name = when (fieldValue) {
                    is SCValXdr.Str -> fieldValue.value.value
                    is SCValXdr.Sym -> fieldValue.value.value
                    else -> ""
                }
            }
            "signers" -> {
                signers = parseSigners(fieldValue)
            }
            "policies" -> {
                policies = parsePolicies(fieldValue)
            }
            "valid_until" -> {
                validUntil = when (fieldValue) {
                    is SCValXdr.U32 -> fieldValue.value.value
                    is SCValXdr.Void -> null
                    else -> null
                }
            }
        }
    }

    return ParsedContextRule(
        id = id,
        contextType = contextType,
        name = name,
        signers = signers,
        policies = policies,
        validUntil = validUntil
    )
}

/**
 * Parses a context type from its ScVal Vec representation.
 *
 * On-chain encoding:
 * - Default: Vec[Symbol("Default")]
 * - CallContract: Vec[Symbol("CallContract"), Address(contractAddress)]
 * - CreateContract: Vec[Symbol("CreateContract"), Bytes(wasmHash)]
 */
fun parseContextType(scVal: SCValXdr): ContextRuleType {
    val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return ContextRuleType.Default
    if (vec.isEmpty()) return ContextRuleType.Default

    val tag = (vec[0] as? SCValXdr.Sym)?.value?.value ?: return ContextRuleType.Default

    return when (tag) {
        "Default" -> ContextRuleType.Default

        "CallContract" -> {
            if (vec.size >= 2) {
                val address = (vec[1] as? SCValXdr.Address)?.value
                if (address != null) {
                    val addressStr = SmartAccountSharedUtils.extractAddressString(address)
                    if (addressStr != null) {
                        ContextRuleType.CallContract(addressStr)
                    } else {
                        ContextRuleType.Default
                    }
                } else {
                    ContextRuleType.Default
                }
            } else {
                ContextRuleType.Default
            }
        }

        "CreateContract" -> {
            if (vec.size >= 2) {
                val bytes = (vec[1] as? SCValXdr.Bytes)?.value?.value
                if (bytes != null) {
                    ContextRuleType.CreateContract(bytes)
                } else {
                    ContextRuleType.Default
                }
            } else {
                ContextRuleType.Default
            }
        }

        else -> ContextRuleType.Default
    }
}

/**
 * Parses signers from a ScVal Vec.
 *
 * Each signer element is a Vec:
 * - Delegated: Vec[Symbol("Delegated"), Address(address)]
 * - External: Vec[Symbol("External"), Address(verifier), Bytes(keyData)]
 */
fun parseSigners(scVal: SCValXdr): List<SmartAccountSigner> {
    val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return emptyList()
    return vec.mapNotNull { signerVal -> parseSingleSigner(signerVal) }
}

/**
 * Parses a single signer from its ScVal Vec representation.
 *
 * @return A [SmartAccountSigner] or null if the ScVal cannot be parsed.
 */
fun parseSingleSigner(scVal: SCValXdr): SmartAccountSigner? {
    val vec = (scVal as? SCValXdr.Vec)?.value?.value ?: return null
    if (vec.isEmpty()) return null

    val tag = (vec[0] as? SCValXdr.Sym)?.value?.value ?: return null

    return when (tag) {
        "Delegated" -> {
            if (vec.size >= 2) {
                val address = (vec[1] as? SCValXdr.Address)?.value
                if (address != null) {
                    val addressStr = SmartAccountSharedUtils.extractAddressString(address)
                    if (addressStr != null) {
                        try {
                            DelegatedSigner(addressStr)
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                } else null
            } else null
        }

        "External" -> {
            if (vec.size >= 3) {
                val verifier = (vec[1] as? SCValXdr.Address)?.value
                val keyData = (vec[2] as? SCValXdr.Bytes)?.value?.value
                if (verifier != null && keyData != null) {
                    val verifierStr = SmartAccountSharedUtils.extractAddressString(verifier)
                    if (verifierStr != null) {
                        try {
                            ExternalSigner(verifierStr, keyData)
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                } else null
            } else null
        }

        else -> null
    }
}

/**
 * Parses policy addresses from a ScVal.
 *
 * Policies can be encoded as:
 * - Vec[Address, ...]: list of policy contract addresses
 * - Map[Address -> ScVal, ...]: policy address to install params (keys extracted)
 */
fun parsePolicies(scVal: SCValXdr): List<String> {
    return when (scVal) {
        is SCValXdr.Vec -> {
            val vec = scVal.value?.value ?: return emptyList()
            vec.mapNotNull { element ->
                val address = (element as? SCValXdr.Address)?.value
                if (address != null) SmartAccountSharedUtils.extractAddressString(address) else null
            }
        }
        is SCValXdr.Map -> {
            val entries = scVal.value?.value ?: return emptyList()
            entries.mapNotNull { mapEntry ->
                val address = (mapEntry.key as? SCValXdr.Address)?.value
                if (address != null) SmartAccountSharedUtils.extractAddressString(address) else null
            }
        }
        else -> emptyList()
    }
}
