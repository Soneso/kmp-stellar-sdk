//
//  SmartAccountBuilders.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

import com.soneso.stellar.sdk.smartaccount.oz.SmartAccountSharedUtils
import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Builder utilities for Smart Account Kit.
 *
 * Type-safe constructors for creating signers, context rule types, and policy parameters.
 * These helpers ensure correct data structures are created for smart account operations.
 *
 * Includes:
 * - Signer builder functions for delegated, external, WebAuthn, and Ed25519 signers
 * - Context rule type builder functions
 * - Policy parameter data classes
 * - Display and formatting utilities
 * - Signer comparison and deduplication utilities
 */
object SmartAccountBuilders {

    // ========================================================================
    // Signer Builders
    // ========================================================================

    /**
     * Creates a delegated signer (native Stellar account).
     *
     * Delegated signers use Stellar's native `require_auth()` mechanism.
     * No external verifier contract is needed.
     *
     * @param publicKey Stellar account public key (G-address) or contract address (C-address)
     * @return A [DelegatedSigner] for use in context rules
     * @throws ValidationException.InvalidAddress if the address format is invalid
     *
     * Example:
     * ```kotlin
     * val signer = SmartAccountBuilders.createDelegatedSigner("GA7Q...")
     * ```
     */
    fun createDelegatedSigner(publicKey: String): DelegatedSigner {
        return DelegatedSigner(publicKey)
    }

    /**
     * Creates an external signer (custom verifier contract).
     *
     * External signers use a verifier contract to validate signatures.
     * Used for WebAuthn passkeys, Ed25519 with custom logic, etc.
     *
     * @param verifierAddress Verifier contract address (C-address)
     * @param keyData Key data for the verifier (format depends on verifier type)
     * @return An [ExternalSigner] for use in context rules
     * @throws ValidationException.InvalidAddress if the verifier address format is invalid
     * @throws ValidationException.InvalidInput if key data is empty
     *
     * Example:
     * ```kotlin
     * val signer = SmartAccountBuilders.createExternalSigner(
     *     verifierAddress = "CBCD1234...",
     *     keyData = publicKeyBytes
     * )
     * ```
     */
    fun createExternalSigner(verifierAddress: String, keyData: ByteArray): ExternalSigner {
        return ExternalSigner(verifierAddress, keyData)
    }

    /**
     * Creates a WebAuthn passkey signer.
     *
     * Convenience wrapper around [createExternalSigner] that handles
     * the key_data format for WebAuthn (pubkey + credentialId).
     *
     * @param webauthnVerifierAddress WebAuthn verifier contract address (C-address)
     * @param publicKey 65-byte secp256r1 uncompressed public key (0x04 prefix + X + Y)
     * @param credentialId WebAuthn credential identifier bytes
     * @return An [ExternalSigner] configured for WebAuthn verification
     * @throws ValidationException.InvalidAddress if the verifier address format is invalid
     * @throws ValidationException.InvalidInput if the public key size or format is invalid,
     *         or if the credential ID is empty
     *
     * Example:
     * ```kotlin
     * val signer = SmartAccountBuilders.createWebAuthnSigner(
     *     webauthnVerifierAddress = "CBCD1234...",
     *     publicKey = secp256r1PublicKey,
     *     credentialId = credentialIdBytes
     * )
     * ```
     */
    fun createWebAuthnSigner(
        webauthnVerifierAddress: String,
        publicKey: ByteArray,
        credentialId: ByteArray
    ): ExternalSigner {
        return ExternalSigner.webAuthn(webauthnVerifierAddress, publicKey, credentialId)
    }

    /**
     * Creates an Ed25519 signer (with external verifier).
     *
     * For Ed25519 keys that need custom verification logic via a verifier contract.
     * The key data is the 32-byte Ed25519 public key.
     *
     * @param ed25519VerifierAddress Ed25519 verifier contract address (C-address)
     * @param publicKey 32-byte Ed25519 public key
     * @return An [ExternalSigner] configured for Ed25519 verification
     * @throws ValidationException.InvalidAddress if the verifier address format is invalid
     * @throws ValidationException.InvalidInput if the public key is not 32 bytes
     *
     * Example:
     * ```kotlin
     * val signer = SmartAccountBuilders.createEd25519Signer(
     *     ed25519VerifierAddress = "CDEF5678...",
     *     publicKey = ed25519PublicKey
     * )
     * ```
     */
    fun createEd25519Signer(
        ed25519VerifierAddress: String,
        publicKey: ByteArray
    ): ExternalSigner {
        return ExternalSigner.ed25519(ed25519VerifierAddress, publicKey)
    }

    // ========================================================================
    // Policy Parameter Builders
    // ========================================================================

    /**
     * Creates simple threshold policy parameters.
     *
     * Simple threshold requires M-of-N signers where M = threshold
     * and N = total number of signers on the context rule.
     *
     * @param threshold Minimum number of signers required (must be >= 1)
     * @return Policy parameters for simple threshold
     * @throws ValidationException.InvalidInput if threshold is less than 1
     *
     * Example:
     * ```kotlin
     * // 2-of-3 multisig
     * val params = SmartAccountBuilders.createThresholdParams(2)
     * ```
     */
    fun createThresholdParams(threshold: Int): SimpleThresholdParams {
        if (threshold < 1) {
            throw ValidationException.invalidInput(
                "threshold",
                "Threshold must be at least 1, got: $threshold"
            )
        }
        return SimpleThresholdParams(threshold = threshold)
    }

    /**
     * Creates weighted threshold policy parameters.
     *
     * Weighted threshold assigns different weights to different signers.
     * Authorization succeeds when the sum of weights of authenticated
     * signers meets or exceeds the threshold.
     *
     * @param threshold Total weight required for authorization (must be >= 1)
     * @param signerWeights Map of signers to their weights (all weights must be >= 1)
     * @return Policy parameters for weighted threshold
     * @throws ValidationException.InvalidInput if threshold is less than 1, signer weights
     *         is empty, any weight is less than 1, or total weight is less than threshold
     *
     * Example:
     * ```kotlin
     * val weights = mapOf(adminSigner to 100, userSigner to 50)
     * val params = SmartAccountBuilders.createWeightedThresholdParams(100, weights)
     * ```
     */
    fun createWeightedThresholdParams(
        threshold: Int,
        signerWeights: Map<SmartAccountSigner, Int>
    ): WeightedThresholdParams {
        if (threshold < 1) {
            throw ValidationException.invalidInput(
                "threshold",
                "Threshold must be at least 1, got: $threshold"
            )
        }
        if (signerWeights.isEmpty()) {
            throw ValidationException.invalidInput(
                "signerWeights",
                "At least one signer weight must be provided"
            )
        }

        var totalWeight = 0
        for ((_, weight) in signerWeights) {
            if (weight < 1) {
                throw ValidationException.invalidInput(
                    "signerWeights",
                    "All weights must be positive integers, got: $weight"
                )
            }
            totalWeight += weight
        }

        if (totalWeight < threshold) {
            throw ValidationException.invalidInput(
                "signerWeights",
                "Sum of weights ($totalWeight) must be >= threshold ($threshold)"
            )
        }

        return WeightedThresholdParams(
            threshold = threshold,
            signerWeights = signerWeights
        )
    }

    /**
     * Creates spending limit policy parameters.
     *
     * Spending limit restricts how much can be transferred within a given time period.
     * Useful for rate limiting or daily limits.
     *
     * @param spendingLimit Maximum amount allowed in the period as a decimal XLM string
     *   (e.g. "100" or "10.5"). Converted to stroops internally.
     * @param periodLedgers Number of ledgers in the period (must be >= 1).
     *        Use [SmartAccountConstants.LEDGERS_PER_HOUR] or [SmartAccountConstants.LEDGERS_PER_DAY]
     *        for common periods.
     * @return Policy parameters for spending limit
     * @throws ValidationException.InvalidInput if spending limit is not a valid positive number
     *         or period is less than 1
     *
     * Example:
     * ```kotlin
     * // 100 XLM per day (~17280 ledgers at 5 seconds per ledger)
     * val params = SmartAccountBuilders.createSpendingLimitParams(
     *     spendingLimit = "100",
     *     periodLedgers = SmartAccountConstants.LEDGERS_PER_DAY
     * )
     * ```
     */
    fun createSpendingLimitParams(spendingLimit: String, periodLedgers: Int): SpendingLimitParams {
        // Delegate to amountToStroops which validates non-empty and positive
        val stroops = SmartAccountSharedUtils.amountToStroops(spendingLimit)
        if (periodLedgers < 1) {
            throw ValidationException.invalidInput(
                "periodLedgers",
                "Period must be at least 1 ledger, got: $periodLedgers"
            )
        }
        return SpendingLimitParams(
            spendingLimit = stroops,
            periodLedgers = periodLedgers
        )
    }

    // ========================================================================
    // Signer Parsing
    // ========================================================================

    /**
     * Parses a signer from a Stellar address string.
     *
     * Accepts a Stellar account address (G-address) and returns a [DelegatedSigner].
     * For more complex signer types, construct the appropriate [SmartAccountSigner]
     * subclass directly.
     *
     * @param address Stellar account address (G-address)
     * @return A [DelegatedSigner] for the given address
     * @throws ValidationException.InvalidAddress if the address format is invalid
     *
     * Example:
     * ```kotlin
     * val signer = SmartAccountBuilders.parseSigner("GA7Q...")
     * ```
     */
    fun parseSigner(address: String): DelegatedSigner {
        if (!address.startsWith("G") || address.length != 56) {
            throw ValidationException.invalidAddress(
                "Invalid Stellar account address. Must start with 'G' and be 56 characters, got: $address"
            )
        }
        return DelegatedSigner(address)
    }

    // ========================================================================
    // Signer Inspection Utilities
    // ========================================================================

    /**
     * Extracts the credential ID from a WebAuthn signer's key data.
     *
     * WebAuthn signers store key data as: publicKey (65 bytes) + credentialId (variable).
     * This function extracts the credential ID portion from an [ExternalSigner].
     *
     * @param signer The signer to extract the credential ID from
     * @return The credential ID bytes, or null if the signer is not a WebAuthn signer
     *
     * Example:
     * ```kotlin
     * val credentialId = SmartAccountBuilders.getCredentialIdFromSigner(signer)
     * ```
     */
    fun getCredentialIdFromSigner(signer: SmartAccountSigner): ByteArray? {
        if (signer !is ExternalSigner) return null
        if (signer.keyData.size <= SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
            return null // Not a WebAuthn signer (no credential ID suffix)
        }
        return signer.keyData.copyOfRange(
            SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE,
            signer.keyData.size
        )
    }

    /**
     * Extracts the credential ID from a WebAuthn signer's key data as a Base64URL-encoded string.
     *
     * @param signer The signer to extract the credential ID from
     * @return Base64URL-encoded credential ID string, or null if the signer is not a WebAuthn signer
     *
     * Example:
     * ```kotlin
     * val credentialIdStr = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
     * // "abc123-def456_ghi789"
     * ```
     */
    fun getCredentialIdStringFromSigner(signer: SmartAccountSigner): String? {
        val credentialId = getCredentialIdFromSigner(signer) ?: return null
        return SmartAccountSharedUtils.base64urlEncode(credentialId)
    }

    /**
     * Checks if a signer is a delegated signer (native Stellar account).
     *
     * @param signer The signer to check
     * @return true if the signer is a [DelegatedSigner]
     */
    fun isDelegatedSigner(signer: SmartAccountSigner): Boolean {
        return signer is DelegatedSigner
    }

    /**
     * Checks if a signer is an external signer (verifier contract).
     *
     * @param signer The signer to check
     * @return true if the signer is an [ExternalSigner]
     */
    fun isExternalSigner(signer: SmartAccountSigner): Boolean {
        return signer is ExternalSigner
    }

    /**
     * Returns a human-readable description of a signer type.
     *
     * @param signer The signer to describe
     * @return Human-readable string such as "Stellar Account", "Passkey (WebAuthn)",
     *         "Ed25519", or "External Verifier"
     *
     * Example:
     * ```kotlin
     * val description = SmartAccountBuilders.describeSignerType(signer)
     * // "Passkey (WebAuthn)"
     * ```
     */
    fun describeSignerType(signer: SmartAccountSigner): String {
        if (signer is DelegatedSigner) {
            return "Stellar Account"
        }

        val external = signer as ExternalSigner

        // WebAuthn signers have 65-byte pubkey + credential ID
        if (external.keyData.size > SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
            return "Passkey (WebAuthn)"
        }

        // Ed25519 signers have 32-byte public key
        if (external.keyData.size == 32) {
            return "Ed25519"
        }

        return "External Verifier"
    }

    // ========================================================================
    // Signer Matching
    // ========================================================================

    /**
     * Checks if a signer matches a given credential ID.
     *
     * Useful for finding a specific passkey signer among a list of signers.
     *
     * @param signer The signer to check
     * @param credentialId The credential ID to match (raw bytes)
     * @return true if the signer is a WebAuthn signer with a matching credential ID
     *
     * Example:
     * ```kotlin
     * val matchingSigner = signers.find {
     *     SmartAccountBuilders.signerMatchesCredential(it, myCredentialId)
     * }
     * ```
     */
    fun signerMatchesCredential(signer: SmartAccountSigner, credentialId: ByteArray): Boolean {
        val signerCredId = getCredentialIdFromSigner(signer) ?: return false
        return signerCredId.contentEquals(credentialId)
    }

    /**
     * Checks if a signer matches a given credential ID string.
     *
     * Useful for finding a specific passkey signer among a list of signers
     * when working with Base64URL-encoded credential IDs.
     *
     * @param signer The signer to check
     * @param credentialId The credential ID to match (Base64URL-encoded)
     * @return true if the signer is a WebAuthn signer with a matching credential ID
     *
     * Example:
     * ```kotlin
     * val matchingSigner = signers.find {
     *     SmartAccountBuilders.signerMatchesCredentialId(it, "abc123-def456")
     * }
     * ```
     */
    fun signerMatchesCredentialId(signer: SmartAccountSigner, credentialId: String): Boolean {
        val signerCredId = getCredentialIdStringFromSigner(signer) ?: return false
        return signerCredId == credentialId
    }

    /**
     * Checks if a signer matches a given Stellar address.
     *
     * Useful for finding a specific delegated signer among a list of signers.
     *
     * @param signer The signer to check
     * @param address The Stellar address to match (G-address)
     * @return true if the signer is a [DelegatedSigner] with the matching address
     *
     * Example:
     * ```kotlin
     * val matchingSigner = signers.find {
     *     SmartAccountBuilders.signerMatchesAddress(it, walletAddress)
     * }
     * ```
     */
    fun signerMatchesAddress(signer: SmartAccountSigner, address: String): Boolean {
        if (signer !is DelegatedSigner) return false
        return signer.address == address
    }

    // ========================================================================
    // Signer Comparison and Deduplication
    // ========================================================================

    /**
     * Checks if two signers are equal.
     *
     * Compares signers by their tag and values. For delegated signers, compares the
     * address. For external signers, compares verifier address and key data bytes.
     *
     * @param a First signer
     * @param b Second signer
     * @return true if signers are equal
     *
     * Example:
     * ```kotlin
     * val isSame = SmartAccountBuilders.signersEqual(signer1, signer2)
     * ```
     */
    fun signersEqual(a: SmartAccountSigner, b: SmartAccountSigner): Boolean {
        if (a is DelegatedSigner && b is DelegatedSigner) {
            return a.address == b.address
        }
        if (a is ExternalSigner && b is ExternalSigner) {
            return a.verifierAddress == b.verifierAddress &&
                a.keyData.contentEquals(b.keyData)
        }
        return false
    }

    /**
     * Returns a unique string key for a signer, suitable for Map/Set operations.
     *
     * The key format varies by signer type:
     * - Delegated: "delegated:[address]"
     * - External: "external:[verifierAddress]:[keyDataHex]"
     *
     * This is equivalent to [SmartAccountSigner.uniqueKey].
     *
     * @param signer The signer to get a key for
     * @return A unique string key identifying this signer
     *
     * Example:
     * ```kotlin
     * val signerMap = mutableMapOf<String, SmartAccountSigner>()
     * for (signer in signers) {
     *     val key = SmartAccountBuilders.getSignerKey(signer)
     *     signerMap.putIfAbsent(key, signer)
     * }
     * ```
     */
    fun getSignerKey(signer: SmartAccountSigner): String {
        return signer.uniqueKey
    }

    /**
     * Collects unique signers from a list, removing duplicates.
     *
     * Uses [getSignerKey] to determine uniqueness. The first occurrence of each
     * signer is kept; subsequent duplicates are discarded.
     *
     * @param signers List of signers (may contain duplicates)
     * @return List of unique signers preserving insertion order
     *
     * Example:
     * ```kotlin
     * val allSigners = rules.flatMap { it.signers }
     * val uniqueSigners = SmartAccountBuilders.collectUniqueSigners(allSigners)
     * ```
     */
    fun collectUniqueSigners(signers: List<SmartAccountSigner>): List<SmartAccountSigner> {
        val signerMap = linkedMapOf<String, SmartAccountSigner>()
        for (signer in signers) {
            val key = getSignerKey(signer)
            if (!signerMap.containsKey(key)) {
                signerMap[key] = signer
            }
        }
        return signerMap.values.toList()
    }
}

// ============================================================================
// Policy Parameter Data Classes
// ============================================================================

/**
 * Parameters for simple threshold policy.
 *
 * Requires that at least [threshold] of the signers on the context rule
 * provide valid signatures for authorization to succeed.
 *
 * @property threshold Minimum number of signers required (>= 1)
 */
data class SimpleThresholdParams(
    val threshold: Int
)

/**
 * Parameters for weighted threshold policy.
 *
 * Each signer has a weight, and authorization succeeds when the sum of
 * weights of authenticated signers meets or exceeds the threshold.
 *
 * @property threshold Total weight required for authorization (>= 1)
 * @property signerWeights Map of signers to their weights (all >= 1)
 */
data class WeightedThresholdParams(
    val threshold: Int,
    val signerWeights: Map<SmartAccountSigner, Int>
)

/**
 * Parameters for spending limit policy.
 *
 * Restricts how much can be transferred within a given time period.
 *
 * @property spendingLimit Maximum amount allowed in the period (in stroops as BigInteger, >= 1)
 * @property periodLedgers Number of ledgers in the period (>= 1).
 *           Approximately 5 seconds per ledger on the Stellar network.
 */
data class SpendingLimitParams(
    val spendingLimit: BigInteger,
    val periodLedgers: Int
)

