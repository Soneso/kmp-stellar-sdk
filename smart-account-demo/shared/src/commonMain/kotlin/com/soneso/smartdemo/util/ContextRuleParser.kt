package com.soneso.smartdemo.util

import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.ExternalWalletAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import com.soneso.stellar.sdk.smartaccount.oz.ParsedContextRule
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.SCAddressXdr
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Represents a signer extracted from context rules with its signing capability.
 *
 * @property signer The smart account signer (ExternalSigner or DelegatedSigner)
 * @property canSign Whether this signer can currently sign transactions
 */
data class SignerInfo(
    val signer: SmartAccountSigner,
    val canSign: Boolean
)

/**
 * Fetches all context rules from the connected smart account.
 *
 * Iterates rule IDs from 0 upward using [OZContextRuleManager.getContextRule], stopping
 * once all active rules (per [OZContextRuleManager.getContextRulesCount]) are found or
 * [OZSmartAccountConfig.maxContextRuleScanId] is reached. Gaps from removed rules are skipped.
 *
 * @param kit The initialized [OZSmartAccountKit] instance.
 * @throws IllegalStateException if kit is not initialized.
 */
suspend fun fetchAllContextRules(kit: OZSmartAccountKit): List<ParsedContextRule> {
    val allRuleScVals = try {
        kit.contextRuleManager.getAllContextRules()
    } catch (e: Exception) {
        ActivityLogState.error("Failed to fetch context rules: ${e.message}")
        emptyList()
    }

    val result = mutableListOf<ParsedContextRule>()
    val seenIds = mutableSetOf<UInt>()

    for ((index, ruleScVal) in allRuleScVals.withIndex()) {
        val parsed = parseSingleContextRuleFromScVal(ruleScVal, index.toUInt())
        if (parsed != null && seenIds.add(parsed.id)) {
            result.add(parsed)
        }
    }

    return result.sortedBy { it.id }
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
 * Extracts unique signers from a list of parsed context rules and determines
 * whether each signer can currently sign transactions.
 *
 * For [ExternalSigner] with WebAuthn key data (keyData > 65 bytes): canSign is true
 * when the signer's credential ID matches [connectedCredentialId].
 * For [DelegatedSigner]: canSign is true when [externalWallet]?.canSignFor(address) returns true.
 *
 * Signers are deduplicated across rules using [SmartAccountBuilders.collectUniqueSigners].
 *
 * @param rules Parsed context rules to extract signers from.
 * @param connectedCredentialId Base64URL-encoded credential ID of the connected passkey.
 * @param externalWallet Optional external wallet adapter for delegated signer check.
 * @return List of [SignerInfo] with canSign status for each unique signer.
 */
fun extractSignersFromRules(
    rules: List<ParsedContextRule>,
    connectedCredentialId: String?,
    externalWallet: ExternalWalletAdapter?
): List<SignerInfo> {
    val allSigners = rules.flatMap { it.signers }
    val unique = SmartAccountBuilders.collectUniqueSigners(allSigners)

    return unique.map { signer ->
        val canSign = when (signer) {
            is ExternalSigner -> {
                val keyData = signer.keyData
                if (keyData.size > SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
                    // WebAuthn signer: compare credential ID suffix against connected credential
                    val credIdBytes = keyData.copyOfRange(
                        SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE,
                        keyData.size
                    )
                    val credIdEncoded = Util.base64urlEncode(credIdBytes)
                    credIdEncoded == connectedCredentialId
                } else {
                    false
                }
            }
            is DelegatedSigner -> {
                try {
                    externalWallet?.canSignFor(signer.address) ?: false
                } catch (_: Exception) {
                    false
                }
            }
        }
        SignerInfo(signer = signer, canSign = canSign)
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
        val fieldName = try { Scv.fromSymbol(entry.key) } catch (_: Exception) { continue }
        val fieldValue = entry.`val`

        when (fieldName) {
            "id" -> {
                id = try { Scv.fromUint32(fieldValue) } catch (_: Exception) { fallbackId ?: 0u }
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
                    is SCValXdr.U32 -> try { Scv.fromUint32(fieldValue) } catch (_: Exception) { null }
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

    val tag = try { Scv.fromSymbol(vec[0]) } catch (_: Exception) { return ContextRuleType.Default }

    return when (tag) {
        "Default" -> ContextRuleType.Default

        "CallContract" -> {
            if (vec.size >= 2) {
                val address = (vec[1] as? SCValXdr.Address)?.value
                if (address != null) {
                    val addressStr = extractAddressString(address)
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
                val bytes = try { Scv.fromBytes(vec[1]) } catch (_: Exception) { null }
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

    val tag = try { Scv.fromSymbol(vec[0]) } catch (_: Exception) { return null }

    return when (tag) {
        "Delegated" -> {
            if (vec.size >= 2) {
                val address = (vec[1] as? SCValXdr.Address)?.value
                if (address != null) {
                    val addressStr = extractAddressString(address)
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
                val keyData = try { Scv.fromBytes(vec[2]) } catch (_: Exception) { null }
                if (verifier != null && keyData != null) {
                    val verifierStr = extractAddressString(verifier)
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
                if (address != null) extractAddressString(address) else null
            }
        }
        is SCValXdr.Map -> {
            val entries = scVal.value?.value ?: return emptyList()
            entries.mapNotNull { mapEntry ->
                val address = (mapEntry.key as? SCValXdr.Address)?.value
                if (address != null) extractAddressString(address) else null
            }
        }
        else -> emptyList()
    }
}

private fun extractAddressString(address: SCAddressXdr): String? {
    return try {
        Address.fromSCAddress(address).toString()
    } catch (_: Exception) {
        null
    }
}
