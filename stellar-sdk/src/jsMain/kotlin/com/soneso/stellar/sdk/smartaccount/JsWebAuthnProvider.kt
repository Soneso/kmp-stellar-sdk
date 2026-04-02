//
//  JsWebAuthnProvider.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2025 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountUtils
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
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
     * @return [WebAuthnAuthenticationResult] with credential ID, authenticator data,
     *         client data JSON, and DER-encoded signature
     * @throws WebAuthnException.NotSupported if WebAuthn is not available (e.g. Node.js)
     * @throws WebAuthnException.Cancelled if the user dismissed the authentication prompt
     * @throws WebAuthnException.AuthenticationFailed for any other authentication error
     */
    override suspend fun authenticate(
        challenge: ByteArray,
        allowCredentialIds: List<ByteArray>?
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
        if (allowCredentialIds != null && allowCredentialIds.isNotEmpty()) {
            val idBuffers = allowCredentialIds.map { it.toArrayBuffer() }.toTypedArray()
            val jsAllowCreds = js("(function(buffers) { return buffers.map(function(buf) { return { type: 'public-key', id: buf }; }); })")
            publicKey.allowCredentials = jsAllowCreds(idBuffers)
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
                """
                (function() {
                    return typeof response.getPublicKey === 'function';
                })()
                """
            ).unsafeCast<Boolean>()

            if (!hasGetPublicKey) return null

            val spkiBuffer = js(
                """
                (function() {
                    return response.getPublicKey();
                })()
                """
            )

            if (spkiBuffer == null || spkiBuffer == undefined) return null

            val spkiBytes = spkiBuffer.unsafeCast<ArrayBuffer>().toByteArray()
            if (spkiBytes.isEmpty()) return null

            // Extract the last 65 bytes from SubjectPublicKeyInfo
            if (spkiBytes.size >= SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
                val candidate = spkiBytes.copyOfRange(
                    spkiBytes.size - SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE,
                    spkiBytes.size
                )
                if (candidate[0] == SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX) {
                    return candidate
                }
            }

            // If the returned key is exactly 65 bytes and starts with 0x04
            if (spkiBytes.size == SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE &&
                spkiBytes[0] == SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
            ) {
                return spkiBytes
            }

            null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Strategy 2: Parse the authenticator data embedded in the CBOR-encoded
     * attestation object to extract X/Y coordinates from the COSE key structure.
     *
     * The authenticator data is located within the attestation object by searching for
     * the CBOR text key "authData" (encoded as `68 61 75 74 68 44 61 74 61`), followed
     * by a CBOR byte string containing the raw authenticator data.
     *
     * Authenticator data layout (when the AT flag is set):
     * ```
     * [0..31]         rpIdHash          (32 bytes)
     * [32]            flags             (1 byte)
     * [33..36]        signCount         (4 bytes, big-endian)
     * [37..52]        aaguid            (16 bytes)
     * [53..54]        credentialIdLen   (2 bytes, big-endian)
     * [55..55+N-1]    credentialId      (N bytes)
     * [55+N..]        COSE public key   (variable length)
     * ```
     *
     * The COSE key for ES256 (P-256) has this structure:
     * ```
     * 10-byte prefix:  a5 01 02 03 26 20 01 21 58 20
     * X coordinate:    32 bytes
     * 3-byte separator: 22 58 20
     * Y coordinate:    32 bytes
     * ```
     *
     * @return The 65-byte uncompressed public key, or null if the authenticator data
     *         cannot be located or does not contain a valid key
     */
    private fun tryExtractFromAuthenticatorData(attestationObjectBytes: ByteArray): ByteArray? {
        // CBOR text string key for "authData": 0x68 (text, length 8) + ASCII bytes
        val authDataCborKey = byteArrayOf(
            0x68, // CBOR text string, length 8
            0x61, 0x75, 0x74, 0x68, 0x44, 0x61, 0x74, 0x61 // "authData"
        )

        val keyIndex = SmartAccountUtils.findSubarray(attestationObjectBytes, authDataCborKey)
        if (keyIndex < 0) return null

        // After the key, the value is a CBOR byte string (major type 2).
        // Decode the CBOR byte string length:
        //   0x40-0x57 -> inline length (0-23 bytes)
        //   0x58      -> 1-byte length follows
        //   0x59      -> 2-byte big-endian length follows
        val dataStart = keyIndex + authDataCborKey.size
        if (dataStart >= attestationObjectBytes.size) return null

        val lengthByte = attestationObjectBytes[dataStart].toInt() and 0xFF
        val authDataLength: Int
        val authDataOffset: Int

        when {
            lengthByte in 0x40..0x57 -> {
                authDataLength = lengthByte - 0x40
                authDataOffset = dataStart + 1
            }
            lengthByte == 0x58 -> {
                if (dataStart + 1 >= attestationObjectBytes.size) return null
                authDataLength = attestationObjectBytes[dataStart + 1].toInt() and 0xFF
                authDataOffset = dataStart + 2
            }
            lengthByte == 0x59 -> {
                if (dataStart + 2 >= attestationObjectBytes.size) return null
                authDataLength =
                    ((attestationObjectBytes[dataStart + 1].toInt() and 0xFF) shl 8) or
                        (attestationObjectBytes[dataStart + 2].toInt() and 0xFF)
                authDataOffset = dataStart + 3
            }
            else -> return null
        }

        if (authDataOffset + authDataLength > attestationObjectBytes.size) return null

        val authenticatorData = attestationObjectBytes.copyOfRange(
            authDataOffset, authDataOffset + authDataLength
        )

        // Need at least 55 bytes to read up to the credential ID length field
        if (authenticatorData.size < 55) return null

        // Check the AT flag (bit 6 of flags byte at offset 32) to confirm attested
        // credential data is present
        val flags = authenticatorData[32].toInt() and 0xFF
        if (flags and 0x40 == 0) return null

        // Read credential ID length from bytes 53-54 (big-endian uint16)
        val credentialIdLength =
            ((authenticatorData[53].toInt() and 0xFF) shl 8) or
                (authenticatorData[54].toInt() and 0xFF)

        // COSE key starts after the credential ID
        val coseKeyStart = 55 + credentialIdLength

        // The COSE prefix for ES256 is 10 bytes, then X (32), separator (3), Y (32)
        val xStart = coseKeyStart + 10
        val yStart = xStart + 32 + 3
        val requiredLength = yStart + 32

        if (authenticatorData.size < requiredLength) return null

        val x = authenticatorData.copyOfRange(xStart, xStart + 32)
        val y = authenticatorData.copyOfRange(yStart, yStart + 32)

        val publicKey = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        x.copyInto(publicKey, 1)
        y.copyInto(publicKey, 33)

        return publicKey
    }

    /**
     * Strategy 3: Pattern-match the raw attestation object for the COSE ES256 key
     * structure prefix and extract X/Y coordinates.
     *
     * Searches for the 10-byte COSE key prefix for ES256 (secp256r1):
     * `a5 01 02 03 26 20 01 21 58 20`
     * followed by X (32 bytes), separator (`22 58 20`, 3 bytes), Y (32 bytes).
     *
     * This is the most resilient strategy because it does not depend on the surrounding
     * CBOR structure being well-formed.
     *
     * @return The 65-byte uncompressed public key, or null if the COSE prefix is not found
     */
    private fun tryExtractFromAttestationPattern(attestationObjectBytes: ByteArray): ByteArray? {
        // COSE key prefix for ES256 (secp256r1)
        val prefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
            0x01, 0x21, 0x58, 0x20.toByte()
        )

        val prefixIndex = SmartAccountUtils.findSubarray(attestationObjectBytes, prefix)
        if (prefixIndex < 0) return null

        val xStart = prefixIndex + prefix.size

        // Need X (32 bytes) + separator (3 bytes) + Y (32 bytes)
        if (attestationObjectBytes.size < xStart + 32 + 3 + 32) return null

        val x = attestationObjectBytes.copyOfRange(xStart, xStart + 32)
        val y = attestationObjectBytes.copyOfRange(xStart + 32 + 3, xStart + 32 + 3 + 32)

        val publicKey = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        x.copyInto(publicKey, 1)
        y.copyInto(publicKey, 33)

        return publicKey
    }

    // ---------------------------------------------------------------------------
    // Authenticator Flags Parsing
    // ---------------------------------------------------------------------------

    /**
     * Holds parsed authenticator flags for device type and backup status.
     */
    private data class AuthenticatorFlagsInfo(
        val deviceType: String?,
        val backedUp: Boolean?
    )

    /**
     * Parses authenticator flags from the attestation object to determine device type
     * and backup status.
     *
     * Locates the authenticator data within the CBOR attestation object and reads the
     * flags byte at offset 32. The relevant flag bits are:
     *
     * - Bit 3 (BE -- Backup Eligibility): If set, the credential is eligible for
     *   multi-device sync (device type = "multiDevice"). If clear, the credential is
     *   bound to a single device (device type = "singleDevice").
     * - Bit 4 (BS -- Backup State): If set, the credential is currently backed up or
     *   synced to a cloud provider.
     *
     * @param attestationObjectBytes Raw CBOR-encoded attestation object
     * @return Parsed flags info, or null values if the authenticator data cannot be located
     */
    private fun parseAuthenticatorFlags(attestationObjectBytes: ByteArray): AuthenticatorFlagsInfo {
        val authDataCborKey = byteArrayOf(
            0x68,
            0x61, 0x75, 0x74, 0x68, 0x44, 0x61, 0x74, 0x61
        )

        val keyIndex = SmartAccountUtils.findSubarray(attestationObjectBytes, authDataCborKey)
        if (keyIndex < 0) return AuthenticatorFlagsInfo(null, null)

        val dataStart = keyIndex + authDataCborKey.size
        if (dataStart >= attestationObjectBytes.size) return AuthenticatorFlagsInfo(null, null)

        val lengthByte = attestationObjectBytes[dataStart].toInt() and 0xFF
        val authDataOffset: Int = when {
            lengthByte in 0x40..0x57 -> dataStart + 1
            lengthByte == 0x58 -> dataStart + 2
            lengthByte == 0x59 -> dataStart + 3
            else -> return AuthenticatorFlagsInfo(null, null)
        }

        // The flags byte is at offset 32 within authenticator data
        val flagsByteIndex = authDataOffset + 32
        if (flagsByteIndex >= attestationObjectBytes.size) {
            return AuthenticatorFlagsInfo(null, null)
        }

        val flags = attestationObjectBytes[flagsByteIndex].toInt() and 0xFF

        // Bit 3 (BE): Backup Eligibility -> device type
        val backupEligible = (flags and 0x08) != 0
        val deviceType = if (backupEligible) "multiDevice" else "singleDevice"

        // Bit 4 (BS): Backup State -> backed up
        val backedUp = (flags and 0x10) != 0

        return AuthenticatorFlagsInfo(
            deviceType = deviceType,
            backedUp = backedUp
        )
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
                """
                (function() {
                    return typeof response.getTransports === 'function';
                })()
                """
            ).unsafeCast<Boolean>()

            if (!hasGetTransports) return null

            val transportsArray = js(
                """
                (function() {
                    return response.getTransports();
                })()
                """
            )

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
