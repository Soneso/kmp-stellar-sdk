//
//  SmartAccountTypes.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCValXdr

// MARK: - Signer Types

/**
 * Represents a signer that can authorize smart account transactions.
 *
 * Smart account signers define who can authorize transactions on a smart account.
 * Two types exist:
 * - [DelegatedSigner]: A Soroban address (G or C) using built-in require_auth verification
 * - [ExternalSigner]: A verifier contract + public key bytes for custom signature validation
 *
 * Example usage:
 * ```kotlin
 * // Create a delegated signer
 * val delegatedSigner = DelegatedSigner(address = "GA7Q...")
 *
 * // Create a WebAuthn signer
 * val webAuthnSigner = ExternalSigner.webAuthn(
 *     verifierAddress = "CBCD...",
 *     publicKey = publicKeyData,
 *     credentialId = credentialIdData
 * )
 *
 * // Convert to on-chain representation
 * val scVal = delegatedSigner.toScVal()
 * ```
 */
sealed class SmartAccountSigner {
    /**
     * Converts this signer to its ScVal representation for contract calls.
     *
     * @return The SCVal representation of this signer
     * @throws ValidationException if conversion fails
     */
    abstract fun toScVal(): SCValXdr

    /**
     * Unique identifier for deduplication.
     *
     * This key is used to prevent duplicate signers in context rules and to identify
     * signers when managing authorization. The format varies by signer type:
     * - Delegated: "delegated:[address]"
     * - External: "external:[verifierAddress]:[keyDataHex]"
     *
     * @return A unique key identifying this signer
     */
    abstract val uniqueKey: String
}

/**
 * A delegated signer using a Soroban address with built-in require_auth verification.
 *
 * Delegated signers are Stellar accounts (G-address) or smart contracts (C-address)
 * that use the native Soroban authorization mechanism. The smart account will call
 * `require_auth_for_args()` on the address to verify authorization.
 *
 * Example:
 * ```kotlin
 * // Account signer
 * val accountSigner = DelegatedSigner(address = "GA7QYNF7SOWQ...")
 *
 * // Contract signer
 * val contractSigner = DelegatedSigner(address = "CBCD1234...")
 * ```
 *
 * @property address The Stellar address of the signer (G-address for accounts, C-address for contracts)
 * @throws ValidationException.InvalidAddress if the address format is invalid
 */
data class DelegatedSigner(
    val address: String
) : SmartAccountSigner() {

    init {
        // Validate address format
        if (!address.startsWith("G") && !address.startsWith("C")) {
            throw ValidationException.invalidAddress("Address must start with 'G' (account) or 'C' (contract), got: $address")
        }
        if (address.length != 56) {
            throw ValidationException.InvalidAddress("Address must be 56 characters long, got: ${address.length}")
        }

        // Validate it's a valid StrKey address
        try {
            Address(address)
        } catch (e: Exception) {
            throw ValidationException.invalidAddress("Invalid address format: $address", e)
        }
    }

    /**
     * Converts the delegated signer to its on-chain representation.
     *
     * Returns: `ScVal::Vec([Symbol("Delegated"), Address(address)])`
     *
     * @return The SCVal representation
     * @throws ValidationException if conversion fails
     */
    override fun toScVal(): SCValXdr {
        try {
            val scAddress = Address(address).toSCAddress()
            val elements = listOf(
                Scv.toSymbol("Delegated"),
                Scv.toAddress(scAddress)
            )
            return Scv.toVec(elements)
        } catch (e: Exception) {
            throw ValidationException.InvalidInput("Failed to convert DelegatedSigner to ScVal: ${e.message}", e)
        }
    }

    /**
     * Unique identifier for deduplication.
     *
     * Format: "delegated:[address]"
     *
     * @return Unique key for this delegated signer
     */
    override val uniqueKey: String
        get() = "delegated:$address"
}

/**
 * An external signer using a verifier contract for custom signature validation.
 *
 * External signers delegate signature verification to a Soroban contract. The verifier
 * contract receives the public key data and signature, and returns whether the signature
 * is valid. This enables support for non-native signature schemes like WebAuthn (secp256r1)
 * and Ed25519.
 *
 * The verifier contract address must be a C-address, and the key data contains the public
 * key bytes plus any additional authentication data (like WebAuthn credential IDs).
 *
 * Example:
 * ```kotlin
 * // WebAuthn signer
 * val webAuthnSigner = ExternalSigner.webAuthn(
 *     verifierAddress = "CBCD1234...",
 *     publicKey = secp256r1PublicKey,
 *     credentialId = webAuthnCredentialId
 * )
 *
 * // Ed25519 signer
 * val ed25519Signer = ExternalSigner.ed25519(
 *     verifierAddress = "CDEF5678...",
 *     publicKey = ed25519PublicKey
 * )
 * ```
 *
 * @property verifierAddress The contract address of the signature verifier (C-address)
 * @property keyData The public key data and any additional authentication data
 * @throws ValidationException if validation fails
 */
data class ExternalSigner(
    val verifierAddress: String,
    val keyData: ByteArray
) : SmartAccountSigner() {

    init {
        // Validate verifier address
        if (!verifierAddress.startsWith("C")) {
            throw ValidationException.invalidAddress("Verifier address must start with 'C' (contract), got: $verifierAddress")
        }
        if (verifierAddress.length != 56) {
            throw ValidationException.InvalidAddress("Verifier address must be 56 characters long, got: ${verifierAddress.length}")
        }

        // Validate it's a valid contract address
        try {
            Address(verifierAddress)
        } catch (e: Exception) {
            throw ValidationException.invalidAddress("Invalid verifier address format: $verifierAddress", e)
        }

        // Validate key data
        if (keyData.isEmpty()) {
            throw ValidationException.invalidInput("keyData", "Key data cannot be empty")
        }
    }

    /**
     * Converts the external signer to its on-chain representation.
     *
     * Returns: `ScVal::Vec([Symbol("External"), Address(verifier), Bytes(keyData)])`
     *
     * @return The SCVal representation
     * @throws ValidationException if conversion fails
     */
    override fun toScVal(): SCValXdr {
        try {
            val scAddress = Address(verifierAddress).toSCAddress()
            val elements = listOf(
                Scv.toSymbol("External"),
                Scv.toAddress(scAddress),
                Scv.toBytes(keyData)
            )
            return Scv.toVec(elements)
        } catch (e: Exception) {
            throw ValidationException.InvalidInput("Failed to convert ExternalSigner to ScVal: ${e.message}", e)
        }
    }

    /**
     * Unique identifier for deduplication.
     *
     * Format: "external:[verifierAddress]:[keyDataHex]"
     *
     * @return Unique key for this external signer
     */
    override val uniqueKey: String
        get() = "external:$verifierAddress:${keyData.toHex()}"

    /**
     * Custom equals implementation that properly compares ByteArray.
     *
     * Standard data class equals would not correctly compare the ByteArray field
     * by content, so this override ensures proper content-based comparison using
     * contentEquals().
     *
     * @param other The object to compare with
     * @return true if the objects are equal, false otherwise
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ExternalSigner

        if (verifierAddress != other.verifierAddress) return false
        if (!keyData.contentEquals(other.keyData)) return false

        return true
    }

    /**
     * Custom hashCode implementation that properly handles ByteArray.
     *
     * Standard data class hashCode would not correctly hash the ByteArray field
     * by content, so this override ensures proper content-based hashing using
     * contentHashCode().
     *
     * @return Hash code for this external signer
     */
    override fun hashCode(): Int {
        var result = verifierAddress.hashCode()
        result = 31 * result + keyData.contentHashCode()
        return result
    }

    companion object {
        /**
         * Creates a WebAuthn external signer with secp256r1 signature verification.
         *
         * WebAuthn signers use an uncompressed secp256r1 public key (65 bytes starting with 0x04)
         * combined with a WebAuthn credential ID for authentication.
         *
         * @param verifierAddress The contract address of the WebAuthn verifier (C-address)
         * @param publicKey The uncompressed secp256r1 public key (65 bytes, starting with 0x04)
         * @param credentialId The WebAuthn credential identifier
         * @return An external signer configured for WebAuthn verification
         * @throws ValidationException if validation fails
         */
        fun webAuthn(
            verifierAddress: String,
            publicKey: ByteArray,
            credentialId: ByteArray
        ): ExternalSigner {
            // Validate public key size
            if (publicKey.size != SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
                throw ValidationException.invalidInput(
                    "publicKey",
                    "WebAuthn public key must be ${SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE} bytes (uncompressed secp256r1), got: ${publicKey.size}"
                )
            }

            // Validate uncompressed format
            if (publicKey[0] != SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX) {
                throw ValidationException.invalidInput(
                    "publicKey",
                    "WebAuthn public key must start with 0x04 (uncompressed format), got: 0x${publicKey[0].toHexString()}"
                )
            }

            // Validate credential ID
            if (credentialId.isEmpty()) {
                throw ValidationException.invalidInput("credentialId", "WebAuthn credential ID cannot be empty")
            }

            // Combine public key + credential ID
            val keyData = publicKey + credentialId

            return ExternalSigner(verifierAddress, keyData)
        }

        /**
         * Creates an Ed25519 external signer.
         *
         * Ed25519 signers use a 32-byte Ed25519 public key for signature verification.
         *
         * @param verifierAddress The contract address of the Ed25519 verifier (C-address)
         * @param publicKey The Ed25519 public key (32 bytes)
         * @return An external signer configured for Ed25519 verification
         * @throws ValidationException if validation fails
         */
        fun ed25519(
            verifierAddress: String,
            publicKey: ByteArray
        ): ExternalSigner {
            val expectedSize = 32
            if (publicKey.size != expectedSize) {
                throw ValidationException.invalidInput(
                    "publicKey",
                    "Ed25519 public key must be $expectedSize bytes, got: ${publicKey.size}"
                )
            }

            return ExternalSigner(verifierAddress, publicKey)
        }
    }
}

// MARK: - Submission Method

/**
 * Determines how a transaction is submitted to the network.
 *
 * By default, the SDK uses the relayer if configured, otherwise submits directly via RPC.
 * Use [SubmissionMethod] with the `forceMethod` parameter on transaction methods to
 * override this default behavior.
 *
 * Example:
 * ```kotlin
 * // Force direct RPC submission even when relayer is configured
 * val result = txOps.transfer(
 *     tokenContract = "CBCD...",
 *     recipient = "GA7Q...",
 *     amount = 10.0,
 *     forceMethod = SubmissionMethod.RPC
 * )
 * ```
 */
enum class SubmissionMethod {
    /**
     * Submit via the relayer proxy for fee-sponsored transactions.
     * Fails if no relayer is configured.
     */
    RELAYER,

    /**
     * Submit directly via Soroban RPC. Always available.
     */
    RPC
}

// MARK: - Extension Functions

/**
 * Converts a ByteArray to a hexadecimal string representation.
 *
 * Each byte is converted to a two-character lowercase hexadecimal string.
 * For example: [0x0A, 0xFF] becomes "0aff".
 *
 * @receiver The ByteArray to convert
 * @return Hexadecimal string representation of the byte array (lowercase, no prefix)
 */
private fun ByteArray.toHex(): String {
    return joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
}

/**
 * Converts a single byte to a hexadecimal string representation.
 *
 * The byte is converted to a two-character lowercase hexadecimal string.
 * For example: 0x0A becomes "0a", 0xFF becomes "ff".
 *
 * @receiver The Byte to convert
 * @return Two-character hexadecimal string (lowercase, zero-padded)
 */
private fun Byte.toHexString(): String {
    return (this.toInt() and 0xFF).toString(16).padStart(2, '0')
}
