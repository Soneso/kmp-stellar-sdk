//
//  OZBuilders.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.ValidationException

/**
 * Builder utilities for OpenZeppelin smart account context rules.
 *
 * Provides type-safe constructors and display utilities for [ContextRuleType] and
 * related OZ-specific operations. These functions are separated from [SmartAccountBuilders]
 * to avoid a circular package dependency between `core` and `oz`.
 */
object OZBuilders {

    // ========================================================================
    // Context Rule Type Builders
    // ========================================================================

    /**
     * Creates a Default context rule type.
     *
     * Default rules apply to any operation that does not match a more specific
     * CallContract or CreateContract rule.
     *
     * @return A [ContextRuleType.Default] for default authorization
     *
     * Example:
     * ```kotlin
     * val contextType = OZBuilders.createDefaultContext()
     * ```
     */
    fun createDefaultContext(): ContextRuleType {
        return ContextRuleType.Default
    }

    /**
     * Creates a CallContract context rule type.
     *
     * CallContract rules apply only when calling a specific contract.
     * Useful for restricting signers to specific dApps or operations.
     *
     * @param contractAddress The contract address this rule applies to (C-address)
     * @return A [ContextRuleType.CallContract] for contract-specific authorization
     * @throws ValidationException.InvalidAddress if the contract address format is invalid
     *
     * Example:
     * ```kotlin
     * val contextType = OZBuilders.createCallContractContext("CBCD1234...")
     * ```
     */
    fun createCallContractContext(contractAddress: String): ContextRuleType {
        requireContractAddress(contractAddress, "contractAddress")
        return ContextRuleType.CallContract(contractAddress)
    }

    /**
     * Creates a CreateContract context rule type from a hex-encoded WASM hash.
     *
     * CreateContract rules apply only when deploying contracts with a specific WASM hash.
     *
     * @param wasmHashHex The WASM hash as a hex string (64 characters, optionally prefixed with "0x")
     * @return A [ContextRuleType.CreateContract] for contract creation authorization
     * @throws ValidationException.InvalidInput if the hex string (after removing optional "0x" prefix) is not 64 characters
     *
     * Example:
     * ```kotlin
     * val contextType = OZBuilders.createCreateContractContext("abc123...")
     * ```
     */
    fun createCreateContractContext(wasmHashHex: String): ContextRuleType {
        val cleanHash = if (wasmHashHex.startsWith("0x")) wasmHashHex.substring(2) else wasmHashHex
        if (cleanHash.length != 64) {
            throw ValidationException.invalidInput(
                "wasmHash",
                "WASM hash must be 32 bytes (64 hex characters), got: ${cleanHash.length} characters"
            )
        }
        val hashBytes = Util.hexToBytes(cleanHash)
        return ContextRuleType.CreateContract(hashBytes)
    }

    /**
     * Creates a CreateContract context rule type from raw WASM hash bytes.
     *
     * CreateContract rules apply only when deploying contracts with a specific WASM hash.
     *
     * @param wasmHash The WASM hash (32 bytes)
     * @return A [ContextRuleType.CreateContract] for contract creation authorization
     * @throws ValidationException.InvalidInput if the byte array is not 32 bytes
     *
     * Example:
     * ```kotlin
     * val contextType = OZBuilders.createCreateContractContext(wasmHashBytes)
     * ```
     */
    fun createCreateContractContext(wasmHash: ByteArray): ContextRuleType {
        if (wasmHash.size != 32) {
            throw ValidationException.invalidInput(
                "wasmHash",
                "WASM hash must be 32 bytes, got: ${wasmHash.size}"
            )
        }
        return ContextRuleType.CreateContract(wasmHash)
    }

    // ========================================================================
    // Signer Inspection Utilities
    // ========================================================================

    /**
     * Collects unique signers from all context rules, removing duplicates across rules.
     *
     * Iterates through all context rules, collects their signers, and returns
     * a deduplicated list.
     *
     * @param rules List of parsed context rules
     * @return List of unique signers across all rules
     *
     * Example:
     * ```kotlin
     * val allUniqueSigners = OZBuilders.collectUniqueSignersFromRules(rules)
     * ```
     */
    fun collectUniqueSignersFromRules(rules: List<ParsedContextRule>): List<SmartAccountSigner> {
        val allSigners = rules.flatMap { it.signers }
        return SmartAccountBuilders.collectUniqueSigners(allSigners)
    }

}
