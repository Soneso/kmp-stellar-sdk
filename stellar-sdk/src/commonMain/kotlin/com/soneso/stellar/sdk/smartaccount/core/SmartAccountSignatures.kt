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
import com.soneso.stellar.sdk.xdr.XdrWriter

/**
 * Base sealed class for smart account signature types.
 *
 * Smart accounts support multiple signature types for transaction authorization:
 * - [WebAuthnSignature]: Signatures from passkeys (biometric authentication)
 * - [Ed25519Signature]: Signatures from traditional Ed25519 keypairs
 * - [PolicySignature]: Policy-based authorization (empty map)
 *
 * Each signature type can be converted to a Soroban SCVal representation that
 * the smart account contract can verify, and to a [ByteArray] that is embedded
 * directly in the on-wire auth-payload signers map via [toAuthPayloadBytes].
 *
 * Example usage:
 * ```kotlin
 * val signature = WebAuthnSignature(
 *     authenticatorData = authenticatorDataBytes,
 *     clientData = clientDataBytes,
 *     signature = signatureBytes
 * )
 * val scVal = signature.toScVal()
 * val payloadBytes = signature.toAuthPayloadBytes()
 * ```
 */
sealed class SmartAccountSignature {
    /**
     * Converts this signature to its ScVal representation.
     *
     * The exact shape is variant-dependent: [WebAuthnSignature] and [PolicySignature]
     * return an SCValXdr.Map; [Ed25519Signature] returns SCValXdr.Bytes holding the raw
     * 64-byte signature.
     *
     * @return The signature encoded as an [SCValXdr].
     */
    abstract fun toScVal(): SCValXdr

    /**
     * Returns the raw bytes to embed in the on-wire signers map of the auth payload.
     *
     * The content is verifier-dependent:
     *
     * | Signature type       | Content                                       |
     * |----------------------|-----------------------------------------------|
     * | [WebAuthnSignature]  | XDR-encoded SCValXdr (Map with 3 fields)      |
     * | [Ed25519Signature]   | Raw 64-byte signature (no XDR wrapper)        |
     * | [PolicySignature]    | XDR-encoded SCValXdr (empty Map)              |
     *
     * For [Ed25519Signature] the Ed25519 verifier contract expects `BytesN<64>` —
     * exactly 64 raw bytes. XDR-wrapping inflates the payload beyond 64 bytes and
     * causes the verifier to reject it.
     *
     * @return The bytes for the auth-payload signers map value.
     * @throws TransactionException.SigningFailed when XDR encoding fails
     *   (WebAuthn and Policy variants only; Ed25519 never throws).
     */
    abstract fun toAuthPayloadBytes(): ByteArray

    companion object {
        /**
         * XDR-encodes [scVal] and returns the resulting bytes.
         *
         * Used by [WebAuthnSignature] and [PolicySignature]; not used by
         * [Ed25519Signature] (which returns raw bytes without XDR wrapping).
         *
         * @throws TransactionException.SigningFailed when XDR encoding fails.
         */
        internal fun encodeScValToBytes(scVal: SCValXdr, contextLabel: String): ByteArray {
            return try {
                val writer = XdrWriter()
                scVal.encode(writer)
                writer.toByteArray()
            } catch (e: Exception) {
                throw TransactionException.signingFailed(
                    "Failed to XDR encode $contextLabel signature ScVal",
                    e
                )
            }
        }
    }
}

/**
 * WebAuthn signature from a passkey authentication ceremony.
 *
 * WebAuthn signatures contain the complete attestation data required to verify
 * biometric or security key authentication. The signature must be in compact format
 * (64 bytes) with normalized S value to prevent signature malleability.
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
        if (signature.size != SmartAccountConstants.ED25519_SIGNATURE_SIZE) {
            throw ValidationException.invalidInput(
                "signature",
                "WebAuthn signature must be exactly ${SmartAccountConstants.ED25519_SIGNATURE_SIZE} bytes, got ${signature.size}"
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

    /** XDR-encoded ScVal Map with `authenticator_data`, `client_data`, `signature` byte fields. */
    override fun toAuthPayloadBytes(): ByteArray =
        encodeScValToBytes(toScVal(), "WebAuthn")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WebAuthnSignature

        val a = Util.constantTimeEquals(authenticatorData, other.authenticatorData)
        val b = Util.constantTimeEquals(clientData, other.clientData)
        val c = Util.constantTimeEquals(signature, other.signature)
        return a and b and c
    }

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
 * Ed25519 signatures are 64 bytes and provide deterministic signing with strong
 * side-channel resistance.
 *
 * [toScVal] returns the raw 64-byte signature as `SCValXdr.Bytes`. The Ed25519
 * verifier contract expects `BytesN<64>` directly as `sig_data`. The corresponding
 * public key is supplied separately from the smart account's on-chain
 * `External(verifier, key_data)` storage and is NOT transmitted in the auth payload.
 *
 * The [publicKey] field is retained on the data class for local Ed25519 signature
 * verification before submission and for content-based equality.
 *
 * Example:
 * ```kotlin
 * val ed25519Sig = Ed25519Signature(
 *     publicKey = byteArrayOf(...),   // 32-byte Ed25519 public key
 *     signature = byteArrayOf(...)    // 64-byte Ed25519 signature
 * )
 * val scVal = ed25519Sig.toScVal()    // SCValXdr.Bytes holding the raw 64 bytes
 * ```
 *
 * @property publicKey Ed25519 public key (32 bytes). Used for local signature
 *   verification before submission; not transmitted on-chain.
 * @property signature Ed25519 signature (64 bytes).
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
        if (signature.size != SmartAccountConstants.ED25519_SIGNATURE_SIZE) {
            throw ValidationException.invalidInput(
                "signature",
                "Ed25519 signature must be exactly ${SmartAccountConstants.ED25519_SIGNATURE_SIZE} bytes, got ${signature.size}"
            )
        }
    }

    /**
     * Returns the raw 64-byte Ed25519 signature as `SCValXdr.Bytes`.
     *
     * The Ed25519 verifier contract expects `BytesN<64>` directly as `sig_data`.
     * The public key is supplied separately from the smart account's on-chain
     * `External(verifier, key_data)` storage and is NOT transmitted here.
     *
     * @return SCValXdr.Bytes holding the raw 64-byte signature.
     */
    override fun toScVal(): SCValXdr {
        return Scv.toBytes(signature)
    }

    /** Raw 64-byte Ed25519 signature; the public key is supplied by the on-chain `External(verifier, key_data)` signer slot. */
    override fun toAuthPayloadBytes(): ByteArray = signature.copyOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Ed25519Signature

        val a = Util.constantTimeEquals(publicKey, other.publicKey)
        val b = Util.constantTimeEquals(signature, other.signature)
        return a and b
    }

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

    /** XDR-encoded empty ScVal Map signaling policy-based authorization. */
    override fun toAuthPayloadBytes(): ByteArray =
        encodeScValToBytes(toScVal(), "Policy")
}
