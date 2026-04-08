//
//  SmartAccountSignatures.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCValXdr

/**
 * Base sealed class for smart account signature types.
 *
 * Smart accounts support multiple signature types for transaction authorization:
 * - [WebAuthnSignature]: Signatures from passkeys (biometric authentication)
 * - [Ed25519Signature]: Signatures from traditional Ed25519 keypairs
 * - [PolicySignature]: Policy-based authorization (empty map)
 *
 * Each signature type can be converted to a Soroban SCVal representation that
 * the smart account contract can verify.
 *
 * Example usage:
 * ```kotlin
 * val signature = WebAuthnSignature(
 *     authenticatorData = authenticatorDataBytes,
 *     clientData = clientDataBytes,
 *     signature = signatureBytes
 * )
 * val scVal = signature.toScVal()
 * ```
 */
sealed class SmartAccountSignature {
    /**
     * Converts this signature to its ScVal representation.
     *
     * The keys in the resulting map MUST be alphabetically sorted for
     * contract compatibility.
     *
     * @return The signature encoded as an SCValXdr.Map
     */
    abstract fun toScVal(): SCValXdr

}

/**
 * WebAuthn signature from a passkey authentication ceremony.
 *
 * WebAuthn signatures contain the complete attestation data required to verify
 * biometric or security key authentication. The signature must be in compact format
 * (64 bytes) with normalized S value to prevent signature malleability.
 *
 * Field ordering in the SCVal map is CRITICAL and must be alphabetical:
 * 1. authenticator_data
 * 2. client_data
 * 3. signature
 *
 * Note: The field name is "client_data", NOT "client_data_json".
 *
 * Example:
 * ```kotlin
 * val webauthnSig = WebAuthnSignature(
 *     authenticatorData = byteArrayOf(...),  // Raw authenticator data from WebAuthn ceremony
 *     clientData = byteArrayOf(...),         // Client data JSON from WebAuthn ceremony
 *     signature = byteArrayOf(...)           // 64-byte compact ECDSA signature (r || s)
 * )
 * val scVal = webauthnSig.toScVal()
 * ```
 *
 * @property authenticatorData Raw authenticator data from the WebAuthn authentication ceremony.
 *   Contains RP ID hash, flags, signature counter, and optional extensions.
 * @property clientData Client data JSON from the WebAuthn ceremony.
 *   Contains challenge, origin, type, and other client-side information.
 *   CRITICAL: This is stored as "client_data", NOT "client_data_json".
 * @property signature ECDSA signature in compact 64-byte format (r || s).
 *   The signature must already be normalized (S value in lower half of curve order)
 *   to prevent signature malleability attacks. This is typically handled by the
 *   WebAuthn browser API.
 */
data class WebAuthnSignature(
    val authenticatorData: ByteArray,
    val clientData: ByteArray,
    val signature: ByteArray
) : SmartAccountSignature() {

    init {
        if (signature.size != 64) {
            throw ValidationException.invalidInput(
                "signature",
                "WebAuthn signature must be exactly 64 bytes, got ${signature.size}"
            )
        }
    }

    /**
     * Converts the WebAuthn signature to a Soroban SCVal map.
     *
     * The resulting map has keys in alphabetical order (CRITICAL for contract compatibility):
     * ```
     * ScVal::Map([
     *   { Symbol("authenticator_data"), Bytes(authenticatorData) },
     *   { Symbol("client_data"), Bytes(clientData) },
     *   { Symbol("signature"), Bytes(signature) },
     * ])
     * ```
     *
     * @return SCValXdr.Map with signature components
     */
    override fun toScVal(): SCValXdr {
        // Keys must be in alphabetical order for contract compatibility
        return Scv.toMap(linkedMapOf(
            Scv.toSymbol("authenticator_data") to Scv.toBytes(authenticatorData),
            Scv.toSymbol("client_data") to Scv.toBytes(clientData),
            Scv.toSymbol("signature") to Scv.toBytes(signature)
        ))
    }

    /**
     * Constant-time equals to prevent timing side-channel attacks on signature data.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WebAuthnSignature

        val a = Util.constantTimeEquals(authenticatorData, other.authenticatorData)
        val b = Util.constantTimeEquals(clientData, other.clientData)
        val c = Util.constantTimeEquals(signature, other.signature)
        return a and b and c
    }

    /**
     * Custom hashCode implementation that properly handles ByteArray fields.
     *
     * Standard data class hashCode would not correctly hash ByteArray fields
     * by content, so this override ensures proper content-based hashing.
     *
     * @return Hash code for this WebAuthn signature
     */
    override fun hashCode(): Int {
        var result = authenticatorData.contentHashCode()
        result = 31 * result + clientData.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}

/**
 * Ed25519 signature from a traditional keypair.
 *
 * Ed25519 signatures are 64 bytes and provide strong security guarantees with
 * deterministic signing and built-in resistance to side-channel attacks.
 *
 * Field ordering in the SCVal map is CRITICAL and must be alphabetical:
 * 1. public_key
 * 2. signature
 *
 * Example:
 * ```kotlin
 * val ed25519Sig = Ed25519Signature(
 *     publicKey = byteArrayOf(...),   // 32-byte Ed25519 public key
 *     signature = byteArrayOf(...)    // 64-byte Ed25519 signature
 * )
 * val scVal = ed25519Sig.toScVal()
 * ```
 *
 * @property publicKey Ed25519 public key (32 bytes).
 * @property signature Ed25519 signature (64 bytes).
 *   Generated by signing a message hash with an Ed25519 private key.
 */
data class Ed25519Signature(
    val publicKey: ByteArray,
    val signature: ByteArray
) : SmartAccountSignature() {

    init {
        if (publicKey.size != SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE) {
            throw ValidationException.invalidInput(
                "publicKey",
                "Ed25519 public key must be exactly ${SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE} bytes, got ${publicKey.size}"
            )
        }
        if (signature.size != 64) {
            throw ValidationException.invalidInput(
                "signature",
                "Ed25519 signature must be exactly 64 bytes, got ${signature.size}"
            )
        }
    }

    /**
     * Converts the Ed25519 signature to a Soroban SCVal map.
     *
     * The resulting map has keys in alphabetical order:
     * ```
     * ScVal::Map([
     *   { Symbol("public_key"), Bytes(publicKey) },
     *   { Symbol("signature"), Bytes(signature) },
     * ])
     * ```
     *
     * @return SCValXdr.Map with public key and signature bytes
     */
    override fun toScVal(): SCValXdr {
        // Keys must be in alphabetical order for contract compatibility
        return Scv.toMap(linkedMapOf(
            Scv.toSymbol("public_key") to Scv.toBytes(publicKey),
            Scv.toSymbol("signature") to Scv.toBytes(signature)
        ))
    }

    /**
     * Constant-time equals to prevent timing side-channel attacks on signature data.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Ed25519Signature

        val a = Util.constantTimeEquals(publicKey, other.publicKey)
        val b = Util.constantTimeEquals(signature, other.signature)
        return a and b
    }

    /**
     * Custom hashCode implementation that properly handles ByteArray fields.
     *
     * Standard data class hashCode would not correctly hash ByteArray fields
     * by content, so this override ensures proper content-based hashing.
     *
     * @return Hash code for this Ed25519 signature
     */
    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}

/**
 * Policy signature representing policy-based authorization.
 *
 * Policy signatures are empty maps that indicate authorization should be
 * determined by the smart account's policy evaluation (e.g., spending limits,
 * threshold signatures, time-based restrictions).
 *
 * The policy itself is responsible for validating the authorization context
 * and returning success or failure. This signature type is a marker indicating
 * that no explicit cryptographic signature is required.
 *
 * Example:
 * ```kotlin
 * val policySig = PolicySignature
 * val scVal = policySig.toScVal()  // Returns empty map
 * ```
 */
object PolicySignature : SmartAccountSignature() {

    /**
     * Converts the policy signature to a Soroban SCVal map.
     *
     * Policy signatures are represented as empty maps:
     * ```
     * ScVal::Map([])
     * ```
     *
     * @return Empty SCValXdr.Map
     */
    override fun toScVal(): SCValXdr {
        return Scv.toMap(linkedMapOf())
    }
}
