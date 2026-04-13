//
//  JsWebAuthnProvider.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.SmartAccountUtils
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnCborParser
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
import com.soneso.stellar.sdk.smartaccount.oz.AllowCredential
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnRegistrationResult
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import kotlin.js.Promise

// ---------------------------------------------------------------------------
// External declarations for the Web Authentication API
// ---------------------------------------------------------------------------

/**
 * The CredentialsContainer interface exposed via `navigator.credentials`.
 * Provides access to WebAuthn credential creation and assertion.
 */
private external interface CredentialsContainer {
    fun create(options: dynamic): Promise<dynamic>
    fun get(options: dynamic): Promise<dynamic>
}

/**
 * Helper to access the navigator global.
 * Returns null in Node.js or other non-browser environments where
 * `navigator.credentials` is not available.
 */
private fun getNavigatorCredentials(): CredentialsContainer? {
    return js(
        """
        (function() {
            if (typeof navigator !== 'undefined' && navigator.credentials) {
                return navigator.credentials;
            }
            return null;
        })()
        """
    ).unsafeCast<CredentialsContainer?>()
}

// ---------------------------------------------------------------------------
// ArrayBuffer / ByteArray conversion helpers
// ---------------------------------------------------------------------------

/**
 * Converts a Kotlin [ByteArray] to a JavaScript [ArrayBuffer].
 *
 * Uses [Int8Array] as the intermediary since Kotlin bytes are signed.
 */
private fun ByteArray.toArrayBuffer(): ArrayBuffer {
    val int8 = Int8Array(this.size)
    for (i in this.indices) {
        int8.asDynamic()[i] = this[i]
    }
    return int8.buffer
}

/**
 * Converts a JavaScript [ArrayBuffer] to a Kotlin [ByteArray].
 *
 * Uses [Int8Array] view to correctly handle signed byte values.
 */
private fun ArrayBuffer.toByteArray(): ByteArray {
    val int8 = Int8Array(this)
    return ByteArray(int8.length) { index ->
        int8[index]
    }
}

// ---------------------------------------------------------------------------
// JsWebAuthnProvider
// ---------------------------------------------------------------------------

/**
 * JavaScript/Browser implementation of [WebAuthnProvider] using the Web Authentication API.
 *
 * This provider uses `navigator.credentials` to create and assert WebAuthn credentials
 * in a browser environment. It requests ES256 (secp256r1, algorithm -7) keys and returns
 * the public key as an uncompressed 65-byte secp256r1 point (0x04 prefix + X + Y).
 *
 * For public key extraction during registration, three strategies are used in order
 * of preference:
 * 1. `response.getPublicKey()` -- returns SubjectPublicKeyInfo (SPKI); the last 65 bytes
 *    are the uncompressed secp256r1 point. Preferred because it is the most direct path.
 * 2. Parse `authenticatorData` from the CBOR-encoded attestation object to locate the
 *    COSE key structure and extract the X/Y coordinates.
 * 3. Pattern-match the raw `attestationObject` bytes for the COSE ES256 key prefix
 *    (`a5 01 02 03 26 20 01 21 58 20`) and extract X/Y coordinates.
 *
 * This class is only usable in browser environments. Attempting to use it in Node.js
 * (where `navigator.credentials` is not available) will throw [WebAuthnException.NotSupported].
 *
 * @param rpId Relying party identifier (typically the origin domain, e.g. "example.com")
 * @param rpName Human-readable relying party name displayed to the user during ceremonies
 * @param timeout Timeout in milliseconds for WebAuthn operations (default: 60000ms)
 *
 * Example usage:
 * ```kotlin
 * val provider = JsWebAuthnProvider(
 *     rpId = "example.com",
 *     rpName = "My Stellar App"
 * )
 *
 * // Register a new passkey
 * val registration = provider.register(
 *     challenge = challengeBytes,
 *     userId = userIdBytes,
 *     userName = "alice@example.com"
 * )
 * println("Credential ID: ${registration.credentialId.size} bytes")
 * println("Public key: ${registration.publicKey.size} bytes")
 *
 * // Authenticate with the passkey
 * val authentication = provider.authenticate(challenge = payloadHash)
 * println("Signature: ${authentication.signature.size} bytes")
 * ```
 */
class JsWebAuthnProvider(
    private val rpId: String,
    private val rpName: String,
    private val timeout: Long = OZConstants.WEBAUTHN_TIMEOUT_MS
) : WebAuthnProvider {

    /**
     * Registers a new WebAuthn credential (passkey creation).
     *
     * Calls `navigator.credentials.create()` with [PublicKeyCredentialCreationOptions]
     * configured for ES256 (algorithm -7, secp256r1). The resulting credential's public
     * key is extracted using three fallback strategies and returned as an uncompressed
     * 65-byte secp256r1 key with 0x04 prefix.
     *
     * The authenticator flags in the attestation data are parsed to determine the device
     * type ("singleDevice" or "multiDevice") and backup status.
     *
     * @param challenge The challenge bytes to bind into the credential (typically 32 bytes)
     * @param userId User identifier bytes for discoverable credentials
     * @param userName User-friendly display name for the credential
     * @return [WebAuthnRegistrationResult] containing credential ID, public key, attestation data,
     *         transport hints, device type, and backup status
     * @throws WebAuthnException.NotSupported if WebAuthn is not available (e.g. Node.js)
     * @throws WebAuthnException.Cancelled if the user dismissed the registration prompt
     * @throws WebAuthnException.RegistrationFailed for any other registration error
     */
    override suspend fun register(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String
    ): WebAuthnRegistrationResult {
        val credentials = getNavigatorCredentials()
            ?: throw WebAuthnException.notSupported(
                "WebAuthn is not supported in this environment. " +
                    "navigator.credentials is not available (Node.js or non-browser context)."
            )

        // Build PublicKeyCredentialCreationOptions via dynamic JS object construction
        val challengeBuffer = challenge.toArrayBuffer()
        val userIdBuffer = userId.toArrayBuffer()

        val options = js("{}")
        val publicKey = js("{}")

        publicKey.challenge = challengeBuffer
        publicKey.rp = js("{}")
        publicKey.rp.id = rpId
        publicKey.rp.name = rpName

        publicKey.user = js("{}")
        publicKey.user.id = userIdBuffer
        publicKey.user.name = userName
        publicKey.user.displayName = userName

        // Request ES256 (secp256r1, COSE algorithm ID -7)
        publicKey.pubKeyCredParams = js("[{type: 'public-key', alg: -7}]")

        publicKey.authenticatorSelection = js("{}")
        publicKey.authenticatorSelection.residentKey = "preferred"
        // The OZ WebAuthn verifier contract requires the User Verified (UV) flag to be set
        // in the authenticator data. "required" ensures the browser always verifies the user
        // (biometric/PIN), which is needed on localhost where "preferred" may skip verification.
        publicKey.authenticatorSelection.userVerification = "required"

        publicKey.timeout = timeout.toInt()

        // Request direct attestation to receive the full attestation object
        publicKey.attestation = "direct"

        options.publicKey = publicKey

        // Invoke navigator.credentials.create()
        val credential: dynamic = try {
            credentials.create(options).await()
        } catch (e: Throwable) {
            throw mapWebAuthnError(e, isRegistration = true)
        }

        if (credential == null) {
            throw WebAuthnException.registrationFailed(
                "navigator.credentials.create() returned null"
            )
        }

        // Extract credential ID (raw bytes)
        val credentialIdBuffer = credential.rawId.unsafeCast<ArrayBuffer>()
        val credentialIdBytes = credentialIdBuffer.toByteArray()

        // Extract attestation object (CBOR-encoded)
        val response = credential.response
        val attestationObjectBuffer = response.attestationObject.unsafeCast<ArrayBuffer>()
        val attestationObjectBytes = attestationObjectBuffer.toByteArray()

        // Extract public key with three fallback strategies
        val extractedPublicKey = extractPublicKey(response, attestationObjectBytes)

        // Extract transport hints (may not be available in all browsers)
        val transports = extractTransports(response)

        // Parse authenticator flags from attestation object for device type and backup status
        val flagsInfo = parseAuthenticatorFlags(attestationObjectBytes)

        return WebAuthnRegistrationResult(
            credentialId = credentialIdBytes,
            publicKey = extractedPublicKey,
            attestationObject = attestationObjectBytes,
            transports = transports,
            deviceType = flagsInfo.deviceType,
            backedUp = flagsInfo.backedUp
        )
    }

    /**
     * Authenticates with an existing WebAuthn credential (passkey assertion).
     *
     * Calls `navigator.credentials.get()` with [PublicKeyCredentialRequestOptions].
     * The returned signature is in DER-encoded ECDSA format. Callers should use
     * [SmartAccountUtils.normalizeSignature] to convert to compact format with low-S
     * normalization before submitting to the Stellar network.
     *
     * @param challenge The challenge bytes to sign (authorization payload hash, typically 32 bytes)
     * @param allowCredentials Optional list of credential descriptors with optional transport hints
     *        to restrict authentication to specific passkeys. When [AllowCredential.transports] is
     *        non-null, the transport hints are forwarded to the browser to enable cross-device
     *        flows (e.g. QR code scanning via "hybrid"). If null, all registered passkeys for
     *        this RP are eligible.
     * @return [WebAuthnAuthenticationResult] with credential ID, authenticator data,
     *         client data JSON, and DER-encoded signature
     * @throws WebAuthnException.NotSupported if WebAuthn is not available (e.g. Node.js)
     * @throws WebAuthnException.Cancelled if the user dismissed the authentication prompt
     * @throws WebAuthnException.AuthenticationFailed for any other authentication error
     */
    override suspend fun authenticate(
        challenge: ByteArray,
        allowCredentials: List<AllowCredential>?
    ): WebAuthnAuthenticationResult {
        val credentials = getNavigatorCredentials()
            ?: throw WebAuthnException.notSupported(
                "WebAuthn is not supported in this environment. " +
                    "navigator.credentials is not available (Node.js or non-browser context)."
            )

        // Build PublicKeyCredentialRequestOptions
        val challengeBuffer = challenge.toArrayBuffer()

        val options = js("{}")
        val publicKey = js("{}")

        publicKey.challenge = challengeBuffer
        publicKey.rpId = rpId
        // The OZ WebAuthn verifier contract requires the User Verified (UV) flag to be set.
        // "required" ensures the browser prompts for biometric/PIN verification on every assertion.
        publicKey.userVerification = "required"
        publicKey.timeout = timeout.toInt()

        // Constrain which passkey the authenticator uses. Without this, the browser
        // may pick a different passkey than intended when multiple exist for this RP.
        // When transport hints are present they are forwarded to enable cross-device
        // flows (e.g. "hybrid" for QR-code scanning via a phone).
        if (allowCredentials != null && allowCredentials.isNotEmpty()) {
            val idBuffers = allowCredentials.map { it.id.toArrayBuffer() }.toTypedArray()
            // Build a parallel array of transport arrays (JS Array<String> or null).
            // Using js("null") for entries without hints avoids emitting a transports
            // field entirely, which is required by the WebAuthn spec — omitting the field
            // is different from passing an empty array.
            val transportArrays: Array<dynamic> = allowCredentials.map { cred ->
                if (cred.transports != null && cred.transports.isNotEmpty()) {
                    cred.transports.toTypedArray().asDynamic()
                } else {
                    null.asDynamic()
                }
            }.toTypedArray()

            val jsAllowCreds = js(
                """
                (function(buffers, transportsPerCred) {
                    return buffers.map(function(buf, i) {
                        var descriptor = { type: 'public-key', id: buf };
                        var t = transportsPerCred[i];
                        if (t !== null && t !== undefined && t.length > 0) {
                            descriptor.transports = t;
                        }
                        return descriptor;
                    });
                })
                """
            )
            publicKey.allowCredentials = jsAllowCreds(idBuffers, transportArrays)
        }

        options.publicKey = publicKey

        // Invoke navigator.credentials.get()
        val credential: dynamic = try {
            credentials.get(options).await()
        } catch (e: Throwable) {
            throw mapWebAuthnError(e, isRegistration = false)
        }

        if (credential == null) {
            throw WebAuthnException.authenticationFailed(
                "navigator.credentials.get() returned null"
            )
        }

        // Extract response data
        val credentialIdBuffer = credential.rawId.unsafeCast<ArrayBuffer>()
        val credentialIdBytes = credentialIdBuffer.toByteArray()

        val response = credential.response
        val authenticatorDataBuffer = response.authenticatorData.unsafeCast<ArrayBuffer>()
        val authenticatorDataBytes = authenticatorDataBuffer.toByteArray()

        val clientDataJSONBuffer = response.clientDataJSON.unsafeCast<ArrayBuffer>()
        val clientDataJSONBytes = clientDataJSONBuffer.toByteArray()

        val signatureBuffer = response.signature.unsafeCast<ArrayBuffer>()
        val signatureBytes = signatureBuffer.toByteArray()

        return WebAuthnAuthenticationResult(
            credentialId = credentialIdBytes,
            authenticatorData = authenticatorDataBytes,
            clientDataJSON = clientDataJSONBytes,
            signature = signatureBytes
        )
    }

    // ---------------------------------------------------------------------------
    // Public Key Extraction (3 strategies)
    // ---------------------------------------------------------------------------

    /**
     * Extracts the 65-byte uncompressed secp256r1 public key from a WebAuthn
     * registration response using three fallback strategies in order of preference.
     *
     * Strategy 1: `response.getPublicKey()` -- returns SubjectPublicKeyInfo (SPKI).
     *   The last 65 bytes are the uncompressed point (0x04 + X + Y).
     *
     * Strategy 2: Parse the CBOR-encoded authenticator data from the attestation object
     *   to extract the COSE key X/Y coordinates.
     *
     * Strategy 3: Pattern-match the raw attestation object bytes for the 10-byte COSE
     *   ES256 key prefix and extract X/Y coordinates.
     *
     * @param response The AuthenticatorAttestationResponse from the browser
     * @param attestationObjectBytes Raw attestation object bytes (used by strategies 2 and 3)
     * @return Uncompressed 65-byte secp256r1 public key (0x04 prefix + 32-byte X + 32-byte Y)
     * @throws WebAuthnException.RegistrationFailed if no strategy can extract the key
     */
    private fun extractPublicKey(response: dynamic, attestationObjectBytes: ByteArray): ByteArray {
        // Strategy 1: response.getPublicKey() (preferred, supported in modern browsers)
        val spkiKey = tryGetPublicKeyFromResponse(response)
        if (spkiKey != null) return spkiKey

        // Strategy 2: Parse authenticator data from CBOR attestation object
        val authDataKey = tryExtractFromAuthenticatorData(attestationObjectBytes)
        if (authDataKey != null) return authDataKey

        // Strategy 3: Pattern match for COSE key in attestation object
        val patternKey = tryExtractFromAttestationPattern(attestationObjectBytes)
        if (patternKey != null) return patternKey

        throw WebAuthnException.registrationFailed(
            "Could not extract secp256r1 public key from attestation response. " +
                "None of the three extraction strategies succeeded."
        )
    }

    /**
     * Strategy 1: Use `response.getPublicKey()` which returns SubjectPublicKeyInfo (SPKI).
     *
     * The SPKI for an EC P-256 key is typically 91 bytes. The last 65 bytes contain
     * the uncompressed public key (0x04 prefix + 32-byte X + 32-byte Y).
     *
     * This method is available in modern browsers (Chrome 67+, Firefox 60+, Safari 14+).
     *
     * @return The 65-byte uncompressed public key, or null if the method is not available
     *         or does not return a valid key
     */
    private fun tryGetPublicKeyFromResponse(response: dynamic): ByteArray? {
        return try {
            // Check if getPublicKey exists on the response object
            val hasGetPublicKey = js(
                "(function(r) { return typeof r.getPublicKey === 'function'; })"
            )(response).unsafeCast<Boolean>()

            if (!hasGetPublicKey) return null

            val spkiBuffer = js(
                "(function(r) { return r.getPublicKey(); })"
            )(response)

            if (spkiBuffer == null || spkiBuffer == undefined) return null

            val spkiBytes = spkiBuffer.unsafeCast<ArrayBuffer>().toByteArray()
            if (spkiBytes.isEmpty()) return null

            WebAuthnCborParser.extractPublicKeyFromSpki(spkiBytes)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Strategy 2: Parse the authenticator data embedded in the CBOR-encoded
     * attestation object to extract X/Y coordinates from the COSE key structure.
     *
     * Delegates to [WebAuthnCborParser.extractAuthenticatorDataFromAttestation] to locate
     * the raw authenticator data within the attestation object, then delegates to
     * [SmartAccountUtils.extractPublicKeyFromAuthenticatorData] to extract the COSE key.
     *
     * @return The 65-byte uncompressed public key, or null if the authenticator data
     *         cannot be located or does not contain a valid key
     */
    private fun tryExtractFromAuthenticatorData(attestationObjectBytes: ByteArray): ByteArray? {
        return try {
            val authData = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestationObjectBytes)
                ?: return null
            SmartAccountUtils.extractPublicKeyFromAuthenticatorData(authData)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Strategy 3: Pattern-match the raw attestation object for the COSE ES256 key
     * structure prefix and extract X/Y coordinates.
     *
     * Delegates to [SmartAccountUtils.extractPublicKeyFromAttestationObject], which searches
     * for the 10-byte COSE key prefix for ES256 (secp256r1) and validates both the
     * Y-coordinate marker and that the extracted point lies on the secp256r1 curve.
     *
     * This is the most resilient strategy because it does not depend on the surrounding
     * CBOR structure being well-formed.
     *
     * @return The 65-byte uncompressed public key, or null if the COSE prefix is not found
     *         or validation fails
     */
    private fun tryExtractFromAttestationPattern(attestationObjectBytes: ByteArray): ByteArray? {
        return try {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(attestationObjectBytes)
        } catch (_: Throwable) {
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Authenticator Flags Parsing
    // ---------------------------------------------------------------------------

    /**
     * Parses authenticator flags from the attestation object to determine device type
     * and backup status.
     *
     * Delegates to [WebAuthnCborParser.extractAuthenticatorDataFromAttestation] to locate
     * the raw authenticator data, then to [WebAuthnCborParser.parseAuthenticatorFlags] to
     * read the flags byte. The relevant flag bits are:
     *
     * - Bit 3 (BE -- Backup Eligibility): If set, the credential is eligible for
     *   multi-device sync (device type = "multiDevice"). If clear, the credential is
     *   bound to a single device (device type = "singleDevice").
     * - Bit 4 (BS -- Backup State): If set, the credential is currently backed up or
     *   synced to a cloud provider.
     *
     * @param attestationObjectBytes Raw CBOR-encoded attestation object
     * @return Parsed [WebAuthnCborParser.AuthenticatorFlags], with null field values if
     *         the authenticator data cannot be located
     */
    private fun parseAuthenticatorFlags(
        attestationObjectBytes: ByteArray
    ): WebAuthnCborParser.AuthenticatorFlags {
        val authData = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestationObjectBytes)
        return WebAuthnCborParser.parseAuthenticatorFlags(authData)
    }

    // ---------------------------------------------------------------------------
    // Transport Extraction
    // ---------------------------------------------------------------------------

    /**
     * Extracts authenticator transport hints from the registration response.
     *
     * Uses `response.getTransports()` which returns an array of transport strings
     * indicating how the browser can communicate with the authenticator. Common values
     * include "internal" (platform authenticator), "usb", "nfc", "ble", and "hybrid".
     *
     * These hints are used when constructing `allowCredentials` for future authentication
     * ceremonies to help the browser select the correct transport.
     *
     * @param response The AuthenticatorAttestationResponse from the browser
     * @return List of transport strings, or null if `getTransports()` is not available
     */
    private fun extractTransports(response: dynamic): List<String>? {
        return try {
            val hasGetTransports = js(
                "(function(r) { return typeof r.getTransports === 'function'; })"
            )(response).unsafeCast<Boolean>()

            if (!hasGetTransports) return null

            val transportsArray = js(
                "(function(r) { return r.getTransports(); })"
            )(response)

            if (transportsArray == null || transportsArray == undefined) return null

            val jsArray = transportsArray.unsafeCast<Array<String>>()
            if (jsArray.isEmpty()) return null

            jsArray.toList()
        } catch (_: Throwable) {
            null
        }
    }

    // ---------------------------------------------------------------------------
    // Error Mapping
    // ---------------------------------------------------------------------------

    /**
     * Maps a JavaScript DOMException or error thrown by the WebAuthn API to the
     * appropriate [WebAuthnException] subtype.
     *
     * Known error names from the Web Authentication specification:
     * - `NotAllowedError`: User cancelled the operation or it was not allowed by policy
     * - `SecurityError`: Origin/domain mismatch or insecure context
     * - `AbortError`: Operation was aborted (e.g. timeout or AbortController signal)
     * - `InvalidStateError`: Credential already exists (registration) or is not available
     * - `NotSupportedError`: Requested algorithm or feature not supported
     * - `ConstraintError`: Authenticator constraints not met
     *
     * @param error The JavaScript error to map
     * @param isRegistration True if the error came from a registration ceremony,
     *        false if from an authentication ceremony
     * @return An appropriate [WebAuthnException] subtype
     */
    private fun mapWebAuthnError(error: Throwable, isRegistration: Boolean): WebAuthnException {
        val errorName = try {
            error.asDynamic().name?.unsafeCast<String>()
        } catch (_: Throwable) {
            null
        }

        val errorMessage = error.message ?: error.toString()

        return when (errorName) {
            "NotAllowedError" -> WebAuthnException.cancelled(error)

            "SecurityError" -> {
                val detail = "Security error: The operation is insecure or the RP ID " +
                    "does not match the current origin. $errorMessage"
                if (isRegistration) {
                    WebAuthnException.registrationFailed(detail, error)
                } else {
                    WebAuthnException.authenticationFailed(detail, error)
                }
            }

            "AbortError" -> {
                val detail = "Operation was aborted or timed out. $errorMessage"
                if (isRegistration) {
                    WebAuthnException.registrationFailed(detail, error)
                } else {
                    WebAuthnException.authenticationFailed(detail, error)
                }
            }

            "InvalidStateError" -> {
                val detail = "Invalid state: $errorMessage"
                if (isRegistration) {
                    WebAuthnException.registrationFailed(detail, error)
                } else {
                    WebAuthnException.authenticationFailed(detail, error)
                }
            }

            "NotSupportedError" -> WebAuthnException.notSupported(
                "WebAuthn operation not supported: $errorMessage",
                error
            )

            "ConstraintError" -> {
                val detail = "Authenticator constraint error: $errorMessage"
                if (isRegistration) {
                    WebAuthnException.registrationFailed(detail, error)
                } else {
                    WebAuthnException.authenticationFailed(detail, error)
                }
            }

            else -> {
                if (isRegistration) {
                    WebAuthnException.registrationFailed(errorMessage, error)
                } else {
                    WebAuthnException.authenticationFailed(errorMessage, error)
                }
            }
        }
    }

}
