//
//  AndroidWebAuthnProvider.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import android.content.Context
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountUtils
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnCborParser
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnRegistrationResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Android implementation of [WebAuthnProvider] using the Credential Manager API.
 *
 * Uses `androidx.credentials.CredentialManager` to interact with platform authenticators
 * (biometric, screen lock, security keys) for passkey registration and authentication.
 * The Credential Manager API is available on Android API 28+ (Android 9.0 Pie).
 *
 * This provider creates FIDO2/WebAuthn credentials using the secp256r1 (P-256) algorithm
 * with the ES256 signature scheme, as required by Stellar smart account contracts.
 *
 * Usage:
 * ```kotlin
 * // In an Activity or Fragment
 * val provider = AndroidWebAuthnProvider(
 *     context = this,  // Activity context required
 *     rpId = "example.com",
 *     rpName = "My Stellar App"
 * )
 *
 * // Register a new passkey
 * val registration = provider.register(
 *     challenge = challengeBytes,
 *     userId = userIdBytes,
 *     userName = "user@example.com"
 * )
 *
 * // Authenticate with existing passkey
 * val authentication = provider.authenticate(
 *     challenge = authChallengeBytes
 * )
 * ```
 *
 * @property context Android Activity context. Must be an Activity context for the
 *   Credential Manager to display authentication dialogs.
 * @property rpId Relying party identifier (domain name). This must match the domain
 *   associated with the Android app via Digital Asset Links.
 * @property rpName Human-readable relying party name displayed to the user during
 *   registration.
 * @property timeout Timeout in milliseconds for WebAuthn operations. Defaults to
 *   [OZConstants.WEBAUTHN_TIMEOUT_MS] (60 seconds).
 * @property authenticatorAttachment Optional authenticator attachment preference.
 *   When null (default), both platform and cross-platform authenticators are allowed,
 *   matching the JS provider behavior. Set to "platform" to restrict to built-in
 *   authenticators (biometric/screen lock), or "cross-platform" for security keys only.
 * @throws WebAuthnException.NotSupported if the device runs Android API level < 28
 */
class AndroidWebAuthnProvider(
    private val context: Context,
    private val rpId: String,
    private val rpName: String,
    private val timeout: Long = OZConstants.WEBAUTHN_TIMEOUT_MS,
    private val authenticatorAttachment: String? = null
) : WebAuthnProvider {

    private val credentialManager: CredentialManager
    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw WebAuthnException.notSupported(
                "Credential Manager requires Android API 28 (Pie) or higher. " +
                    "Current API level: ${Build.VERSION.SDK_INT}"
            )
        }
        credentialManager = CredentialManager.create(context)
    }

    // MARK: - Registration

    /**
     * Registers a new WebAuthn credential (passkey) using the Android Credential Manager.
     *
     * Triggers the platform credential creation flow, prompting the user to create a new
     * passkey using biometric authentication, screen lock, or a security key. The created
     * credential uses the ES256 algorithm (ECDSA with P-256/secp256r1 curve).
     *
     * @param challenge The challenge bytes to bind to the registration (typically 32 bytes).
     *   Used as-is in the WebAuthn ceremony.
     * @param userId User identifier bytes for discoverable credentials (typically random).
     * @param userName User-friendly display name for the credential.
     * @return [WebAuthnRegistrationResult] containing the credential ID, public key,
     *   attestation object, transport hints, device type, and backup status.
     * @throws WebAuthnException.Cancelled if the user cancels the registration dialog.
     * @throws WebAuthnException.RegistrationFailed if credential creation fails for any
     *   other reason (network error, authenticator error, etc.).
     */
    override suspend fun register(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String
    ): WebAuthnRegistrationResult {
        val requestJson = buildRegistrationRequestJson(challenge, userId, userName)

        val createRequest = CreatePublicKeyCredentialRequest(
            requestJson = requestJson
        )

        val response: CreatePublicKeyCredentialResponse
        try {
            val credential = credentialManager.createCredential(context, createRequest)
            response = credential as? CreatePublicKeyCredentialResponse
                ?: throw WebAuthnException.registrationFailed(
                    "Unexpected credential type: ${credential::class.simpleName}"
                )
        } catch (e: CreateCredentialCancellationException) {
            throw WebAuthnException.cancelled(e)
        } catch (e: CreateCredentialException) {
            throw WebAuthnException.registrationFailed(
                "Credential creation failed: ${e.type} - ${e.message}",
                e
            )
        } catch (e: Exception) {
            throw WebAuthnException.registrationFailed(
                "Unexpected error during credential creation: ${e.message}",
                e
            )
        }

        return parseRegistrationResponse(response)
    }

    // MARK: - Authentication

    /**
     * Authenticates with an existing WebAuthn credential (passkey) using the Android
     * Credential Manager.
     *
     * Triggers the platform credential assertion flow, prompting the user to authenticate
     * with their passkey using biometric authentication, screen lock, or a security key.
     * The authenticator signs the challenge with the credential's private key.
     *
     * @param challenge The challenge bytes to sign (authorization payload hash, typically
     *   32 bytes). Used as-is in the WebAuthn ceremony.
     * @return [WebAuthnAuthenticationResult] containing the credential ID, authenticator
     *   data, client data JSON, and DER-encoded ECDSA signature.
     * @throws WebAuthnException.Cancelled if the user cancels the authentication dialog.
     * @throws WebAuthnException.AuthenticationFailed with specific details if no matching
     *   credential is found or if the assertion fails.
     */
    override suspend fun authenticate(
        challenge: ByteArray,
        allowCredentialIds: List<ByteArray>?
    ): WebAuthnAuthenticationResult {
        val requestJson = buildAuthenticationRequestJson(challenge, allowCredentialIds)

        val getRequest = GetCredentialRequest(
            credentialOptions = listOf(
                GetPublicKeyCredentialOption(requestJson = requestJson)
            )
        )

        val credential: PublicKeyCredential
        try {
            val result = credentialManager.getCredential(context, getRequest)
            credential = result.credential as? PublicKeyCredential
                ?: throw WebAuthnException.authenticationFailed(
                    "Unexpected credential type: ${result.credential::class.simpleName}"
                )
        } catch (e: NoCredentialException) {
            throw WebAuthnException.authenticationFailed(
                "No matching credential found for this relying party ($rpId)",
                e
            )
        } catch (e: GetCredentialCancellationException) {
            throw WebAuthnException.cancelled(e)
        } catch (e: GetCredentialException) {
            throw WebAuthnException.authenticationFailed(
                "Credential assertion failed: ${e.type} - ${e.message}",
                e
            )
        } catch (e: Exception) {
            throw WebAuthnException.authenticationFailed(
                "Unexpected error during credential assertion: ${e.message}",
                e
            )
        }

        return parseAuthenticationResponse(credential)
    }

    // MARK: - JSON Request Building

    /**
     * Builds the PublicKeyCredentialCreationOptions JSON for a registration ceremony.
     *
     * Constructs a JSON string conforming to the WebAuthn specification's
     * `PublicKeyCredentialCreationOptions` dictionary, using base64url encoding for
     * binary fields as required by the Android Credential Manager API.
     *
     * @param challenge The challenge bytes (base64url encoded in the JSON)
     * @param userId User identifier bytes (base64url encoded in the JSON)
     * @param userName Display name for the user
     * @return JSON string for the registration request
     */
    private fun buildRegistrationRequestJson(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String
    ): String {
        val challengeB64 = base64UrlEncode(challenge)
        val userIdB64 = base64UrlEncode(userId)

        val jsonObject = JsonObject(
            mapOf(
                "challenge" to JsonPrimitive(challengeB64),
                "rp" to JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(rpId),
                        "name" to JsonPrimitive(rpName)
                    )
                ),
                "user" to JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(userIdB64),
                        "name" to JsonPrimitive(userName),
                        "displayName" to JsonPrimitive(userName)
                    )
                ),
                "pubKeyCredParams" to JsonArray(
                    listOf(
                        // ES256 (ECDSA with P-256) - required for Stellar smart accounts
                        JsonObject(
                            mapOf(
                                "type" to JsonPrimitive("public-key"),
                                "alg" to JsonPrimitive(ES256_ALGORITHM_ID)
                            )
                        )
                    )
                ),
                "timeout" to JsonPrimitive(timeout),
                "attestation" to JsonPrimitive("direct"),
                "authenticatorSelection" to JsonObject(
                    buildMap {
                        // Only set authenticatorAttachment if explicitly configured.
                        // When null, both platform and cross-platform authenticators
                        // are allowed, matching the JS provider behavior.
                        if (authenticatorAttachment != null) {
                            put("authenticatorAttachment", JsonPrimitive(authenticatorAttachment))
                        }
                        // Prefer resident keys for discoverable credentials
                        put("residentKey", JsonPrimitive("preferred"))
                        put("requireResidentKey", JsonPrimitive(false))
                        // Require user verification — the OZ WebAuthn verifier contract
                        // checks the UV flag in authenticator data and rejects if not set.
                        put("userVerification", JsonPrimitive("required"))
                    }
                )
            )
        )

        return jsonObject.toString()
    }

    /**
     * Builds the PublicKeyCredentialRequestOptions JSON for an authentication ceremony.
     *
     * Constructs a JSON string conforming to the WebAuthn specification's
     * `PublicKeyCredentialRequestOptions` dictionary. When [allowCredentialIds] is
     * provided, the `allowCredentials` array constrains the authenticator to only
     * the specified credentials. When null or empty, discoverable credential selection
     * is used (the user picks which passkey to use).
     *
     * @param challenge The challenge bytes (base64url encoded in the JSON)
     * @param allowCredentialIds Optional list of credential ID bytes to constrain selection
     * @return JSON string for the authentication request
     */
    private fun buildAuthenticationRequestJson(
        challenge: ByteArray,
        allowCredentialIds: List<ByteArray>? = null
    ): String {
        val challengeB64 = base64UrlEncode(challenge)

        val allowCredentials = if (!allowCredentialIds.isNullOrEmpty()) {
            JsonArray(allowCredentialIds.map { credId ->
                JsonObject(mapOf(
                    "type" to JsonPrimitive("public-key"),
                    "id" to JsonPrimitive(base64UrlEncode(credId))
                ))
            })
        } else {
            JsonArray(emptyList())
        }

        val jsonObject = JsonObject(
            mapOf(
                "challenge" to JsonPrimitive(challengeB64),
                "rpId" to JsonPrimitive(rpId),
                "timeout" to JsonPrimitive(timeout),
                "userVerification" to JsonPrimitive("required"),
                "allowCredentials" to allowCredentials
            )
        )

        return jsonObject.toString()
    }

    // MARK: - Response Parsing

    /**
     * Parses the registration response from the Credential Manager API.
     *
     * Extracts credential ID, public key, attestation object, transport hints, device
     * type, and backup status from the JSON response. The public key is extracted from
     * the attestation object using CBOR parsing of the authenticator data.
     *
     * @param response The Credential Manager registration response
     * @return [WebAuthnRegistrationResult] with all extracted fields
     * @throws WebAuthnException.RegistrationFailed if the response cannot be parsed
     */
    private fun parseRegistrationResponse(
        response: CreatePublicKeyCredentialResponse
    ): WebAuthnRegistrationResult {
        val responseJson: JsonObject
        try {
            responseJson = json.parseToJsonElement(response.registrationResponseJson).jsonObject
        } catch (e: Exception) {
            throw WebAuthnException.registrationFailed(
                "Failed to parse registration response JSON: ${e.message}",
                e
            )
        }

        // Extract credential ID (rawId is base64url encoded)
        val rawIdB64 = responseJson["rawId"]?.jsonPrimitive?.contentOrNull
            ?: throw WebAuthnException.registrationFailed(
                "Missing rawId in registration response"
            )
        val credentialId = base64UrlDecode(rawIdB64)

        // Parse the response object
        val responseObj = responseJson["response"]?.jsonObject
            ?: throw WebAuthnException.registrationFailed(
                "Missing response object in registration response"
            )

        // Extract attestation object (base64url encoded)
        val attestationObjectB64 = responseObj["attestationObject"]?.jsonPrimitive?.contentOrNull
            ?: throw WebAuthnException.registrationFailed(
                "Missing attestationObject in registration response"
            )
        val attestationObject = base64UrlDecode(attestationObjectB64)

        // Extract public key from the response if available, otherwise from attestation object
        val publicKey = extractPublicKey(responseObj, attestationObject)

        // Extract transport hints from the response
        val transports = extractTransports(responseObj)

        // Extract authenticator data flags for device type and backup status
        val authenticatorDataB64 = responseObj["authenticatorData"]?.jsonPrimitive?.contentOrNull
        val authenticatorData = if (authenticatorDataB64 != null) {
            base64UrlDecode(authenticatorDataB64)
        } else {
            // Extract authenticator data from attestation object
            WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestationObject)
        }

        val flags = WebAuthnCborParser.parseAuthenticatorFlags(authenticatorData)
        val deviceType = flags.deviceType
        val backedUp = flags.backedUp

        return WebAuthnRegistrationResult(
            credentialId = credentialId,
            publicKey = publicKey,
            attestationObject = attestationObject,
            transports = transports,
            deviceType = deviceType,
            backedUp = backedUp
        )
    }

    /**
     * Parses the authentication response from the Credential Manager API.
     *
     * Extracts credential ID, authenticator data, client data JSON, and signature
     * from the JSON response.
     *
     * @param credential The PublicKeyCredential from the Credential Manager
     * @return [WebAuthnAuthenticationResult] with all extracted fields
     * @throws WebAuthnException.AuthenticationFailed if the response cannot be parsed
     */
    private fun parseAuthenticationResponse(
        credential: PublicKeyCredential
    ): WebAuthnAuthenticationResult {
        val responseJson: JsonObject
        try {
            responseJson = json.parseToJsonElement(credential.authenticationResponseJson).jsonObject
        } catch (e: Exception) {
            throw WebAuthnException.authenticationFailed(
                "Failed to parse authentication response JSON: ${e.message}",
                e
            )
        }

        // Extract credential ID (rawId is base64url encoded)
        val rawIdB64 = responseJson["rawId"]?.jsonPrimitive?.contentOrNull
            ?: throw WebAuthnException.authenticationFailed(
                "Missing rawId in authentication response"
            )
        val credentialId = base64UrlDecode(rawIdB64)

        // Parse the response object
        val responseObj = responseJson["response"]?.jsonObject
            ?: throw WebAuthnException.authenticationFailed(
                "Missing response object in authentication response"
            )

        // Extract authenticator data (base64url encoded)
        val authenticatorDataB64 = responseObj["authenticatorData"]?.jsonPrimitive?.contentOrNull
            ?: throw WebAuthnException.authenticationFailed(
                "Missing authenticatorData in authentication response"
            )
        val authenticatorData = base64UrlDecode(authenticatorDataB64)

        // Extract client data JSON (base64url encoded)
        val clientDataJsonB64 = responseObj["clientDataJSON"]?.jsonPrimitive?.contentOrNull
            ?: throw WebAuthnException.authenticationFailed(
                "Missing clientDataJSON in authentication response"
            )
        val clientDataJson = base64UrlDecode(clientDataJsonB64)

        // Extract signature (DER-encoded, base64url encoded)
        val signatureB64 = responseObj["signature"]?.jsonPrimitive?.contentOrNull
            ?: throw WebAuthnException.authenticationFailed(
                "Missing signature in authentication response"
            )
        val signature = base64UrlDecode(signatureB64)

        return WebAuthnAuthenticationResult(
            credentialId = credentialId,
            authenticatorData = authenticatorData,
            clientDataJSON = clientDataJson,
            signature = signature
        )
    }

    // MARK: - Public Key Extraction

    /**
     * Extracts the uncompressed secp256r1 public key from the registration response.
     *
     * Tries two strategies:
     * 1. Direct public key from the response JSON (`publicKey` field, SPKI/DER encoded)
     * 2. CBOR parsing of the attestation object to find the COSE key
     *
     * @param responseObj The response JSON object from the registration
     * @param attestationObject The raw attestation object bytes
     * @return 65-byte uncompressed secp256r1 public key (0x04 || X || Y)
     * @throws WebAuthnException.RegistrationFailed if the public key cannot be extracted
     */
    private fun extractPublicKey(
        responseObj: JsonObject,
        attestationObject: ByteArray
    ): ByteArray {
        // Strategy 1: Try getPublicKey() from response (SPKI/DER encoded)
        val publicKeyB64 = responseObj["publicKey"]?.jsonPrimitive?.contentOrNull
        if (publicKeyB64 != null) {
            try {
                val spkiKey = base64UrlDecode(publicKeyB64)
                val extracted = WebAuthnCborParser.extractPublicKeyFromSpki(spkiKey)
                if (extracted != null) {
                    return extracted
                }
            } catch (_: Exception) {
                // Fall through to attestation object parsing
            }
        }

        // Strategy 2: Parse attestation object for COSE key
        return extractPublicKeyFromAttestationCbor(attestationObject)
    }

    /**
     * Extracts the uncompressed secp256r1 public key from a raw attestation object
     * using CBOR parsing.
     *
     * The attestation object is a CBOR map containing:
     * - `fmt`: attestation format string
     * - `attStmt`: attestation statement (map)
     * - `authData`: authenticator data (byte string)
     *
     * The authenticator data contains the COSE public key when the AT (attested
     * credential data) flag is set. The COSE key structure for ES256 (P-256):
     * ```
     * Map {
     *   1 (kty): 2 (EC2)
     *   3 (alg): -7 (ES256)
     *   -1 (crv): 1 (P-256)
     *   -2 (x): bstr (32 bytes)
     *   -3 (y): bstr (32 bytes)
     * }
     * ```
     *
     * @param attestationObject The raw attestation object bytes
     * @return 65-byte uncompressed secp256r1 public key (0x04 || X || Y)
     * @throws WebAuthnException.RegistrationFailed if the public key cannot be extracted
     */
    private fun extractPublicKeyFromAttestationCbor(attestationObject: ByteArray): ByteArray {
        // Strategy 2a: Use WebAuthnCborParser to get authData, then extract COSE key
        val authData = WebAuthnCborParser.extractAuthenticatorDataFromAttestation(attestationObject)
            ?: throw WebAuthnException.registrationFailed(
                "Could not extract authenticator data from attestation object"
            )

        // Parse authenticator data to extract COSE public key
        // Layout: rpIdHash(32) + flags(1) + signCount(4) + [attestedCredData] + [extensions]
        if (authData.size < WebAuthnCborParser.AUTH_DATA_MIN_LENGTH) {
            throw WebAuthnException.registrationFailed(
                "Authenticator data too short: ${authData.size} bytes (minimum ${WebAuthnCborParser.AUTH_DATA_MIN_LENGTH})"
            )
        }

        val authFlags = authData[WebAuthnCborParser.FLAGS_OFFSET].toInt() and 0xFF

        // Check AT (attested credential data) flag (bit 6)
        if (authFlags and 0x40 == 0) {
            throw WebAuthnException.registrationFailed(
                "Authenticator data does not contain attested credential data (AT flag not set)"
            )
        }

        // Attested credential data starts at offset 37:
        // aaguid(16) + credentialIdLength(2) + credentialId(N) + COSE_KEY(variable)
        if (authData.size < WebAuthnCborParser.ATTESTED_CRED_DATA_HEADER_SIZE) {
            throw WebAuthnException.registrationFailed(
                "Authenticator data too short for attested credential data: ${authData.size} bytes"
            )
        }

        // Read credential ID length (big-endian uint16 at offset 53)
        val credIdLen = ((authData[53].toInt() and 0xFF) shl 8) or
            (authData[54].toInt() and 0xFF)

        val coseKeyStart = WebAuthnCborParser.ATTESTED_CRED_DATA_HEADER_SIZE + credIdLen

        if (coseKeyStart >= authData.size) {
            throw WebAuthnException.registrationFailed(
                "COSE key data not found in authenticator data (credentialId length: $credIdLen)"
            )
        }

        val coseKeyData = authData.copyOfRange(coseKeyStart, authData.size)

        // Strategy 2b: Use WebAuthnCborParser for COSE key extraction
        val publicKey = WebAuthnCborParser.extractPublicKeyFromCoseKey(coseKeyData)
        if (publicKey != null) return publicKey

        // Strategy 3: Pattern-based fallback using SmartAccountUtils (throws on failure)
        return try {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(attestationObject)
        } catch (e: Exception) {
            throw WebAuthnException.registrationFailed(
                "Could not find COSE key structure in authenticator data",
                e
            )
        }
    }

    /**
     * Extracts transport hints from the registration response.
     *
     * Transport hints indicate how the client can communicate with the authenticator
     * (e.g., "usb", "nfc", "ble", "internal"). These are used when constructing
     * `allowCredentials` for future authentication ceremonies.
     *
     * @param responseObj The response JSON object from registration
     * @return List of transport strings, or null if not present in the response
     */
    private fun extractTransports(responseObj: JsonObject): List<String>? {
        val transportsArray = responseObj["transports"]?.jsonArray ?: return null
        return transportsArray.map { it.jsonPrimitive.content }
    }

    // MARK: - Base64url Encoding/Decoding

    /**
     * Encodes bytes to base64url format (RFC 4648 section 5, no padding).
     *
     * Base64url uses '-' instead of '+' and '_' instead of '/', with no trailing '='
     * padding characters. This is the encoding required by the WebAuthn specification
     * for binary fields in JSON.
     *
     * @param data The bytes to encode
     * @return Base64url-encoded string without padding
     */
    private fun base64UrlEncode(data: ByteArray): String {
        return android.util.Base64.encodeToString(
            data,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    /**
     * Decodes a base64url-encoded string to bytes.
     *
     * Handles both padded and unpadded base64url strings, automatically converting
     * from URL-safe alphabet to standard base64 as needed.
     *
     * @param encoded The base64url-encoded string
     * @return Decoded bytes
     * @throws IllegalArgumentException if the string is not valid base64url
     */
    private fun base64UrlDecode(encoded: String): ByteArray {
        return android.util.Base64.decode(
            encoded,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    companion object {
        /** COSE algorithm identifier for ES256 (ECDSA with SHA-256 on P-256 curve). */
        private const val ES256_ALGORITHM_ID = -7
    }
}
