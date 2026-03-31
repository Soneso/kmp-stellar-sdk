//
//  WebAuthnProvider.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Claude on 27.01.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.oz
import com.soneso.stellar.sdk.smartaccount.core.*

import com.soneso.stellar.sdk.Util

/**
 * WebAuthn authentication result from a passkey ceremony.
 *
 * Contains the complete attestation data required to verify biometric or
 * security key authentication.
 *
 * @property credentialId The WebAuthn credential identifier (raw bytes)
 * @property authenticatorData Raw authenticator data from the WebAuthn ceremony
 * @property clientDataJSON Client data JSON from the WebAuthn ceremony
 * @property signature ECDSA signature in DER format (will be normalized to compact format)
 */
data class WebAuthnAuthenticationResult(
    val credentialId: ByteArray,
    val authenticatorData: ByteArray,
    val clientDataJSON: ByteArray,
    val signature: ByteArray
) {
    /**
     * Custom equals implementation that properly compares ByteArray fields.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WebAuthnAuthenticationResult

        val a = Util.constantTimeEquals(credentialId, other.credentialId)
        val b = Util.constantTimeEquals(authenticatorData, other.authenticatorData)
        val c = Util.constantTimeEquals(clientDataJSON, other.clientDataJSON)
        val d = Util.constantTimeEquals(signature, other.signature)
        return a and b and c and d
    }

    /**
     * Custom hashCode implementation that properly handles ByteArray fields.
     */
    override fun hashCode(): Int {
        var result = credentialId.contentHashCode()
        result = 31 * result + authenticatorData.contentHashCode()
        result = 31 * result + clientDataJSON.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}

/**
 * WebAuthn registration result from a passkey creation ceremony.
 *
 * Contains the public key and credential information needed to deploy
 * a smart account contract, along with optional metadata about the
 * authenticator and passkey characteristics.
 *
 * **Primary path**: Providers should populate [publicKey] directly with the 65-byte
 * uncompressed secp256r1 key (0x04 prefix + X + Y). Most WebAuthn APIs expose the
 * public key via `response.getPublicKey()` or equivalent.
 *
 * **Fallback**: If the provider cannot extract the public key directly, it can pass
 * the raw bytes from the WebAuthn API in [publicKey] and supply [attestationObject].
 * Callers can then use [SmartAccountUtils.extractPublicKeyFromRegistration] which
 * supports three extraction strategies: direct validation, authenticator data parsing,
 * and attestation object pattern matching.
 *
 * @property credentialId The WebAuthn credential identifier (raw bytes)
 * @property publicKey Uncompressed secp256r1 public key (65 bytes, starting with 0x04).
 *           This is the primary extraction path. If the platform WebAuthn API wraps
 *           the key in COSE/SPKI encoding, pass the raw bytes and use
 *           [SmartAccountUtils.extractPublicKeyFromRegistration] for extraction with
 *           fallback strategies.
 * @property attestationObject Raw attestation object from WebAuthn registration. Used as
 *           a fallback source for public key extraction when [publicKey] is not directly
 *           available as a 65-byte uncompressed key.
 * @property transports Authenticator transport hints indicating how the browser can
 *   communicate with the authenticator (e.g., "usb", "nfc", "ble", "internal").
 *   Used when constructing allowCredentials for future authentication ceremonies.
 * @property deviceType Authenticator device type: "singleDevice" for hardware security keys
 *   or "multiDevice" for synced/cloud-backed passkeys. Corresponds to the
 *   credentialDeviceType field in the WebAuthn authenticator data flags.
 * @property backedUp Whether the passkey is backed up or synced to a cloud provider.
 *   When true, the credential is available across the user's devices.
 *   Corresponds to the credentialBackedUp flag in the WebAuthn authenticator data.
 */
data class WebAuthnRegistrationResult(
    val credentialId: ByteArray,
    val publicKey: ByteArray,
    val attestationObject: ByteArray,
    val transports: List<String>? = null,
    val deviceType: String? = null,
    val backedUp: Boolean? = null
) {
    /**
     * Custom equals implementation that properly compares ByteArray fields.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WebAuthnRegistrationResult

        val a = Util.constantTimeEquals(credentialId, other.credentialId)
        val b = Util.constantTimeEquals(publicKey, other.publicKey)
        val c = Util.constantTimeEquals(attestationObject, other.attestationObject)
        val bytesMatch = a and b and c
        return bytesMatch
            && transports == other.transports
            && deviceType == other.deviceType
            && backedUp == other.backedUp
    }

    /**
     * Custom hashCode implementation that properly handles ByteArray fields.
     */
    override fun hashCode(): Int {
        var result = credentialId.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + attestationObject.contentHashCode()
        result = 31 * result + (transports?.hashCode() ?: 0)
        result = 31 * result + (deviceType?.hashCode() ?: 0)
        result = 31 * result + (backedUp?.hashCode() ?: 0)
        return result
    }
}

/**
 * Platform-specific WebAuthn provider interface.
 *
 * This interface defines the contract for WebAuthn operations across different
 * platforms (JVM, JS browser, iOS, Android). Each platform provides its own
 * implementation using expect/actual declarations.
 *
 * Implementations must:
 * - Trigger platform-specific biometric/security key prompts
 * - Handle WebAuthn credential creation and assertion
 * - Return properly formatted results with raw byte arrays
 *
 * Example implementation pattern (expect/actual):
 * ```kotlin
 * // commonMain
 * expect class WebAuthnProviderImpl : WebAuthnProvider
 *
 * // jsMain (browser)
 * actual class WebAuthnProviderImpl : WebAuthnProvider {
 *     actual suspend fun register(...) { ... navigator.credentials.create ... }
 *     actual suspend fun authenticate(...) { ... navigator.credentials.get ... }
 * }
 *
 * // jvmMain
 * actual class WebAuthnProviderImpl : WebAuthnProvider {
 *     // Placeholder implementation or integration with Java WebAuthn library
 * }
 * ```
 *
 * Usage:
 * ```kotlin
 * val config = OZSmartAccountConfig(
 *     ...
 *     webauthnProvider = WebAuthnProviderImpl()
 * )
 * ```
 */
interface WebAuthnProvider {
    /**
     * Registers a new WebAuthn credential (passkey creation).
     *
     * Triggers the platform's credential creation flow, prompting the user
     * to create a new passkey using biometric authentication or a security key.
     *
     * Flow:
     * 1. Platform shows biometric/security key prompt
     * 2. User authenticates with fingerprint, face, or security key
     * 3. Platform generates a secp256r1 keypair and credential ID
     * 4. Returns public key and attestation data
     *
     * IMPORTANT: The challenge parameter MUST be used as-is in the WebAuthn
     * registration request. It is a cryptographic hash that binds the credential
     * to the smart account deployment.
     *
     * @param challenge The challenge bytes to sign (typically 32 bytes)
     * @param userId User identifier bytes (typically random, used for discoverable credentials)
     * @param userName User-friendly name for the credential
     * @return WebAuthnRegistrationResult with credential ID, public key, and attestation data
     * @throws WebAuthnException if registration fails or user cancels
     */
    suspend fun register(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String
    ): WebAuthnRegistrationResult

    /**
     * Authenticates with an existing WebAuthn credential (passkey assertion).
     *
     * Triggers the platform's credential assertion flow, prompting the user
     * to authenticate with their passkey using biometric authentication or
     * a security key.
     *
     * Flow:
     * 1. Platform shows biometric/security key prompt
     * 2. User authenticates with fingerprint, face, or security key
     * 3. Platform signs the challenge with the private key
     * 4. Returns signature and authenticator data
     *
     * IMPORTANT: The challenge parameter MUST be used as-is in the WebAuthn
     * authentication request. It is the authorization payload hash that must
     * be signed to authorize the transaction.
     *
     * @param challenge The challenge bytes to sign (authorization payload hash, 32 bytes)
     * @param allowCredentialIds Optional list of raw credential ID byte arrays to set
     *   as allowCredentials in the WebAuthn request. Constrains which passkey the
     *   authenticator uses. Required on web where the browser may otherwise pick
     *   a different passkey than intended.
     * @return WebAuthnAuthenticationResult with signature and attestation data
     * @throws WebAuthnException if authentication fails or user cancels
     */
    suspend fun authenticate(
        challenge: ByteArray,
        allowCredentialIds: List<ByteArray>? = null
    ): WebAuthnAuthenticationResult
}
