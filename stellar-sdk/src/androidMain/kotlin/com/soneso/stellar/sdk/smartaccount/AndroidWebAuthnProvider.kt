//
//  AndroidWebAuthnProvider.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Created by Soneso on 05.10.25.
//  Copyright (c) 2025 Soneso. All rights reserved.
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
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountUtils
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
 *   [SmartAccountConstants.WEBAUTHN_TIMEOUT_MS] (60 seconds).
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
    private val timeout: Long = SmartAccountConstants.WEBAUTHN_TIMEOUT_MS,
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
            response = credential as CreatePublicKeyCredentialResponse
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
        val requestJson = buildAuthenticationRequestJson(challenge)

        val getRequest = GetCredentialRequest(
            credentialOptions = listOf(
                GetPublicKeyCredentialOption(requestJson = requestJson)
            )
        )

        val credential: PublicKeyCredential
        try {
            val result = credentialManager.getCredential(context, getRequest)
            credential = result.credential as PublicKeyCredential
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
     * `PublicKeyCredentialRequestOptions` dictionary. Uses an empty `allowCredentials`
     * list to enable discoverable credential selection (the user picks which passkey
     * to use).
     *
     * @param challenge The challenge bytes (base64url encoded in the JSON)
     * @return JSON string for the authentication request
     */
    private fun buildAuthenticationRequestJson(challenge: ByteArray): String {
        val challengeB64 = base64UrlEncode(challenge)

        val jsonObject = JsonObject(
            mapOf(
                "challenge" to JsonPrimitive(challengeB64),
                "rpId" to JsonPrimitive(rpId),
                "timeout" to JsonPrimitive(timeout),
                "userVerification" to JsonPrimitive("required"),
                // Empty allowCredentials = discoverable credential (passkey) selection
                "allowCredentials" to JsonArray(emptyList())
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
            extractAuthenticatorDataFromAttestation(attestationObject)
        }

        val deviceType = extractDeviceType(authenticatorData)
        val backedUp = extractBackedUp(authenticatorData)

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
                val extracted = extractPublicKeyFromSpki(spkiKey)
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
     * Extracts the uncompressed public key from an SPKI (SubjectPublicKeyInfo) encoded key.
     *
     * The SPKI structure for an EC P-256 key is:
     * ```
     * SEQUENCE {
     *   SEQUENCE {
     *     OID 1.2.840.10045.2.1 (ecPublicKey)
     *     OID 1.2.840.10045.3.1.7 (prime256v1)
     *   }
     *   BIT STRING (65 bytes: 0x04 || X || Y)
     * }
     * ```
     *
     * The uncompressed public key is the last 65 bytes of the SPKI encoding.
     *
     * @param spkiBytes The SPKI/DER encoded public key bytes
     * @return 65-byte uncompressed public key, or null if extraction fails
     */
    private fun extractPublicKeyFromSpki(spkiBytes: ByteArray): ByteArray? {
        // SPKI for P-256 is typically 91 bytes. The uncompressed public key (65 bytes)
        // is at the end, starting with 0x04.
        if (spkiBytes.size < SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE) {
            return null
        }

        // Try to find 0x04 prefix followed by exactly 64 bytes of coordinate data
        // The uncompressed point is at the tail end of the SPKI structure
        val candidateStart = spkiBytes.size - SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE
        if (spkiBytes[candidateStart] == SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX) {
            return spkiBytes.copyOfRange(candidateStart, spkiBytes.size)
        }

        return null
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
        // Find authData in the CBOR attestation object
        val authData = extractAuthDataFromCbor(attestationObject)
            ?: throw WebAuthnException.registrationFailed(
                "Could not extract authenticator data from attestation object"
            )

        // Parse authenticator data to extract COSE public key
        // Layout: rpIdHash(32) + flags(1) + signCount(4) + [attestedCredData] + [extensions]
        if (authData.size < 37) {
            throw WebAuthnException.registrationFailed(
                "Authenticator data too short: ${authData.size} bytes (minimum 37)"
            )
        }

        val flags = authData[32].toInt() and 0xFF

        // Check AT (attested credential data) flag (bit 6)
        if (flags and 0x40 == 0) {
            throw WebAuthnException.registrationFailed(
                "Authenticator data does not contain attested credential data (AT flag not set)"
            )
        }

        // Attested credential data starts at offset 37:
        // aaguid(16) + credentialIdLength(2) + credentialId(N) + COSE_KEY(variable)
        if (authData.size < 55) {
            throw WebAuthnException.registrationFailed(
                "Authenticator data too short for attested credential data: ${authData.size} bytes"
            )
        }

        // Read credential ID length (big-endian uint16 at offset 53)
        val credIdLen = ((authData[53].toInt() and 0xFF) shl 8) or
            (authData[54].toInt() and 0xFF)

        val coseKeyStart = 55 + credIdLen

        if (coseKeyStart >= authData.size) {
            throw WebAuthnException.registrationFailed(
                "COSE key data not found in authenticator data (credentialId length: $credIdLen)"
            )
        }

        // Extract X and Y coordinates from the COSE key
        val coseKeyData = authData.copyOfRange(coseKeyStart, authData.size)
        return extractPublicKeyFromCoseKey(coseKeyData)
    }

    /**
     * Extracts the authenticator data (authData) byte string from a CBOR-encoded
     * attestation object.
     *
     * Performs a minimal CBOR parse to locate the "authData" key in the top-level
     * CBOR map and extract its corresponding byte string value.
     *
     * @param attestationObject The raw attestation object (CBOR-encoded)
     * @return The raw authenticator data bytes, or null if not found
     */
    private fun extractAuthDataFromCbor(attestationObject: ByteArray): ByteArray? {
        // Minimal CBOR parsing to find authData
        // The attestation object is a CBOR map. We need to find the "authData" key
        // and extract its byte string value.

        // The CBOR map header tells us how many items it contains
        if (attestationObject.isEmpty()) return null

        var offset = 0
        val firstByte = attestationObject[offset].toInt() and 0xFF

        // Check for CBOR map (major type 5)
        val majorType = firstByte shr 5
        if (majorType != 5) return null

        val mapSize: Int
        val additionalInfo = firstByte and 0x1F

        when {
            additionalInfo < 24 -> {
                mapSize = additionalInfo
                offset = 1
            }
            additionalInfo == 24 -> {
                if (offset + 1 >= attestationObject.size) return null
                mapSize = attestationObject[offset + 1].toInt() and 0xFF
                offset = 2
            }
            else -> return null // Maps with more than 255 entries not expected
        }

        // Iterate through map entries to find "authData"
        for (i in 0 until mapSize) {
            if (offset >= attestationObject.size) return null

            // Read key (expect text string)
            val keyResult = readCborTextString(attestationObject, offset) ?: return null
            val key = keyResult.first
            offset = keyResult.second

            if (key == "authData") {
                // Read value (expect byte string)
                val valueResult = readCborByteString(attestationObject, offset) ?: return null
                return valueResult.first
            } else {
                // Skip value
                offset = skipCborValue(attestationObject, offset) ?: return null
            }
        }

        return null
    }

    /**
     * Reads a CBOR text string at the given offset.
     *
     * @param data The CBOR-encoded data
     * @param offset The byte offset to start reading from
     * @return Pair of (decoded string, new offset after the string), or null on failure
     */
    private fun readCborTextString(data: ByteArray, offset: Int): Pair<String, Int>? {
        if (offset >= data.size) return null

        val firstByte = data[offset].toInt() and 0xFF
        val majorType = firstByte shr 5

        // Major type 3 = text string
        if (majorType != 3) return null

        val additionalInfo = firstByte and 0x1F
        val length: Int
        var dataStart: Int

        when {
            additionalInfo < 24 -> {
                length = additionalInfo
                dataStart = offset + 1
            }
            additionalInfo == 24 -> {
                if (offset + 1 >= data.size) return null
                length = data[offset + 1].toInt() and 0xFF
                dataStart = offset + 2
            }
            additionalInfo == 25 -> {
                if (offset + 2 >= data.size) return null
                length = ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    (data[offset + 2].toInt() and 0xFF)
                dataStart = offset + 3
            }
            else -> return null
        }

        if (dataStart + length > data.size) return null

        val text = data.copyOfRange(dataStart, dataStart + length).decodeToString()
        return Pair(text, dataStart + length)
    }

    /**
     * Reads a CBOR byte string at the given offset.
     *
     * @param data The CBOR-encoded data
     * @param offset The byte offset to start reading from
     * @return Pair of (decoded bytes, new offset after the byte string), or null on failure
     */
    private fun readCborByteString(data: ByteArray, offset: Int): Pair<ByteArray, Int>? {
        if (offset >= data.size) return null

        val firstByte = data[offset].toInt() and 0xFF
        val majorType = firstByte shr 5

        // Major type 2 = byte string
        if (majorType != 2) return null

        val additionalInfo = firstByte and 0x1F
        val length: Int
        var dataStart: Int

        when {
            additionalInfo < 24 -> {
                length = additionalInfo
                dataStart = offset + 1
            }
            additionalInfo == 24 -> {
                if (offset + 1 >= data.size) return null
                length = data[offset + 1].toInt() and 0xFF
                dataStart = offset + 2
            }
            additionalInfo == 25 -> {
                if (offset + 2 >= data.size) return null
                length = ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    (data[offset + 2].toInt() and 0xFF)
                dataStart = offset + 3
            }
            additionalInfo == 26 -> {
                if (offset + 4 >= data.size) return null
                length = ((data[offset + 1].toInt() and 0xFF) shl 24) or
                    ((data[offset + 2].toInt() and 0xFF) shl 16) or
                    ((data[offset + 3].toInt() and 0xFF) shl 8) or
                    (data[offset + 4].toInt() and 0xFF)
                dataStart = offset + 5
            }
            else -> return null
        }

        if (dataStart + length > data.size) return null

        val bytes = data.copyOfRange(dataStart, dataStart + length)
        return Pair(bytes, dataStart + length)
    }

    /**
     * Skips a single CBOR value at the given offset, returning the offset after the value.
     *
     * Handles all major CBOR types: unsigned integers, negative integers, byte strings,
     * text strings, arrays, maps, tags, and simple values.
     *
     * @param data The CBOR-encoded data
     * @param offset The byte offset to start reading from
     * @return The byte offset after the skipped value, or null on failure
     */
    private fun skipCborValue(data: ByteArray, offset: Int): Int? {
        if (offset >= data.size) return null

        val firstByte = data[offset].toInt() and 0xFF
        val majorType = firstByte shr 5
        val additionalInfo = firstByte and 0x1F

        return when (majorType) {
            0, 1 -> {
                // Unsigned integer (0) or negative integer (1)
                skipCborHead(data, offset)
            }
            2, 3 -> {
                // Byte string (2) or text string (3) - skip head + content
                val lengthResult = readCborLength(data, offset) ?: return null
                val length = lengthResult.first
                val contentStart = lengthResult.second
                if (contentStart + length > data.size) return null
                contentStart + length
            }
            4 -> {
                // Array - skip head + N items
                val lengthResult = readCborLength(data, offset) ?: return null
                val count = lengthResult.first
                var pos = lengthResult.second
                for (j in 0 until count) {
                    pos = skipCborValue(data, pos) ?: return null
                }
                pos
            }
            5 -> {
                // Map - skip head + N key-value pairs
                val lengthResult = readCborLength(data, offset) ?: return null
                val count = lengthResult.first
                var pos = lengthResult.second
                for (j in 0 until count) {
                    pos = skipCborValue(data, pos) ?: return null // key
                    pos = skipCborValue(data, pos) ?: return null // value
                }
                pos
            }
            6 -> {
                // Tag - skip tag number + tagged value
                val headEnd = skipCborHead(data, offset) ?: return null
                skipCborValue(data, headEnd)
            }
            7 -> {
                // Simple value / float
                when (additionalInfo) {
                    in 0..23 -> offset + 1
                    24 -> offset + 2
                    25 -> offset + 3
                    26 -> offset + 5
                    27 -> offset + 9
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * Reads the length from a CBOR item head (for major types 2, 3, 4, 5).
     *
     * @param data The CBOR-encoded data
     * @param offset The byte offset of the CBOR item head
     * @return Pair of (length, offset after head), or null on failure
     */
    private fun readCborLength(data: ByteArray, offset: Int): Pair<Int, Int>? {
        if (offset >= data.size) return null

        val firstByte = data[offset].toInt() and 0xFF
        val additionalInfo = firstByte and 0x1F

        return when {
            additionalInfo < 24 -> Pair(additionalInfo, offset + 1)
            additionalInfo == 24 -> {
                if (offset + 1 >= data.size) null
                else Pair(data[offset + 1].toInt() and 0xFF, offset + 2)
            }
            additionalInfo == 25 -> {
                if (offset + 2 >= data.size) null
                else Pair(
                    ((data[offset + 1].toInt() and 0xFF) shl 8) or
                        (data[offset + 2].toInt() and 0xFF),
                    offset + 3
                )
            }
            additionalInfo == 26 -> {
                if (offset + 4 >= data.size) null
                else Pair(
                    ((data[offset + 1].toInt() and 0xFF) shl 24) or
                        ((data[offset + 2].toInt() and 0xFF) shl 16) or
                        ((data[offset + 3].toInt() and 0xFF) shl 8) or
                        (data[offset + 4].toInt() and 0xFF),
                    offset + 5
                )
            }
            else -> null
        }
    }

    /**
     * Skips a CBOR head (initial byte + additional info bytes), used for integer types.
     *
     * @param data The CBOR-encoded data
     * @param offset The byte offset of the CBOR item head
     * @return The byte offset after the head, or null on failure
     */
    private fun skipCborHead(data: ByteArray, offset: Int): Int? {
        if (offset >= data.size) return null

        val firstByte = data[offset].toInt() and 0xFF
        val additionalInfo = firstByte and 0x1F

        return when {
            additionalInfo < 24 -> offset + 1
            additionalInfo == 24 -> if (offset + 1 < data.size) offset + 2 else null
            additionalInfo == 25 -> if (offset + 2 < data.size) offset + 3 else null
            additionalInfo == 26 -> if (offset + 4 < data.size) offset + 5 else null
            additionalInfo == 27 -> if (offset + 8 < data.size) offset + 9 else null
            else -> null
        }
    }

    /**
     * Extracts the uncompressed secp256r1 public key from CBOR-encoded COSE key data.
     *
     * The COSE key for ES256 is a CBOR map with labeled entries:
     * - Key label -2 (0x21 as negative integer): X coordinate (32 bytes)
     * - Key label -3 (0x22 as negative integer): Y coordinate (32 bytes)
     *
     * CBOR encoding for negative integers: -1-n, so -2 is encoded as 0x21, -3 as 0x22.
     *
     * @param coseKeyData The CBOR-encoded COSE key data
     * @return 65-byte uncompressed secp256r1 public key (0x04 || X || Y)
     * @throws WebAuthnException.RegistrationFailed if X or Y coordinates cannot be found
     */
    private fun extractPublicKeyFromCoseKey(coseKeyData: ByteArray): ByteArray {
        var x: ByteArray? = null
        var y: ByteArray? = null

        if (coseKeyData.isEmpty()) {
            throw WebAuthnException.registrationFailed(
                "Empty COSE key data in authenticator data"
            )
        }

        val firstByte = coseKeyData[0].toInt() and 0xFF
        val majorType = firstByte shr 5

        if (majorType != 5) {
            // Not a CBOR map; try pattern matching as fallback
            return extractPublicKeyByPattern(coseKeyData)
        }

        val additionalInfo = firstByte and 0x1F
        val mapSize: Int
        var offset: Int

        when {
            additionalInfo < 24 -> {
                mapSize = additionalInfo
                offset = 1
            }
            additionalInfo == 24 -> {
                if (coseKeyData.size < 2) {
                    throw WebAuthnException.registrationFailed(
                        "Truncated COSE key map header"
                    )
                }
                mapSize = coseKeyData[1].toInt() and 0xFF
                offset = 2
            }
            else -> {
                return extractPublicKeyByPattern(coseKeyData)
            }
        }

        // Iterate through map entries looking for -2 (X) and -3 (Y)
        for (i in 0 until mapSize) {
            if (offset >= coseKeyData.size) break

            // Read key
            val keyByte = coseKeyData[offset].toInt() and 0xFF
            val keyMajorType = keyByte shr 5
            val keyInfo = keyByte and 0x1F

            // CBOR negative integer label: major type 1
            // -2 is encoded as 1*32+1 = 0x21, -3 as 1*32+2 = 0x22
            if (keyMajorType == 1 && keyInfo == 1) {
                // Key is -2 (X coordinate)
                offset++
                val byteStringResult = readCborByteString(coseKeyData, offset)
                if (byteStringResult != null) {
                    x = byteStringResult.first
                    offset = byteStringResult.second
                } else {
                    offset = skipCborValue(coseKeyData, offset) ?: break
                }
            } else if (keyMajorType == 1 && keyInfo == 2) {
                // Key is -3 (Y coordinate)
                offset++
                val byteStringResult = readCborByteString(coseKeyData, offset)
                if (byteStringResult != null) {
                    y = byteStringResult.first
                    offset = byteStringResult.second
                } else {
                    offset = skipCborValue(coseKeyData, offset) ?: break
                }
            } else {
                // Skip this key
                offset = skipCborHead(coseKeyData, offset) ?: break
                // Skip value
                offset = skipCborValue(coseKeyData, offset) ?: break
            }

            // If we have both coordinates, no need to continue
            if (x != null && y != null) break
        }

        if (x == null || y == null) {
            // Fallback to pattern matching
            return extractPublicKeyByPattern(coseKeyData)
        }

        if (x.size != 32 || y.size != 32) {
            throw WebAuthnException.registrationFailed(
                "Invalid COSE key coordinate size: X=${x.size}, Y=${y.size} (expected 32 each)"
            )
        }

        // Construct uncompressed public key: 0x04 || X || Y
        val publicKey = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        x.copyInto(publicKey, 1)
        y.copyInto(publicKey, 33)
        return publicKey
    }

    /**
     * Extracts the public key using byte pattern matching as a fallback.
     *
     * Searches for the well-known COSE key prefix pattern for ES256 keys:
     * `a5 01 02 03 26 20 01 21 58 20 [32 bytes X] 22 58 20 [32 bytes Y]`
     *
     * @param data The byte data to search in
     * @return 65-byte uncompressed secp256r1 public key
     * @throws WebAuthnException.RegistrationFailed if the pattern is not found
     */
    private fun extractPublicKeyByPattern(data: ByteArray): ByteArray {
        // COSE key prefix for secp256r1 (P-256) public keys
        val prefix = byteArrayOf(
            0xa5.toByte(), 0x01, 0x02, 0x03, 0x26.toByte(), 0x20.toByte(),
            0x01, 0x21, 0x58, 0x20.toByte()
        )

        val prefixIndex = SmartAccountUtils.findSubarray(data, prefix)
        if (prefixIndex < 0) {
            throw WebAuthnException.registrationFailed(
                "Could not find COSE key structure in authenticator data"
            )
        }

        val xStart = prefixIndex + prefix.size
        // X (32 bytes) + separator (0x22, 0x58, 0x20 = 3 bytes) + Y (32 bytes)
        val requiredLength = xStart + 32 + 3 + 32
        if (data.size < requiredLength) {
            throw WebAuthnException.registrationFailed(
                "Insufficient data after COSE key prefix for public key extraction"
            )
        }

        val x = data.copyOfRange(xStart, xStart + 32)
        val yStart = xStart + 32 + 3
        val y = data.copyOfRange(yStart, yStart + 32)

        val publicKey = ByteArray(SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
        publicKey[0] = SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX
        x.copyInto(publicKey, 1)
        y.copyInto(publicKey, 33)
        return publicKey
    }

    // MARK: - Authenticator Data Helpers

    /**
     * Extracts the authenticator data from a CBOR-encoded attestation object.
     *
     * Used when the response does not include authenticator data directly. Falls back
     * to parsing the attestation object to retrieve the authData field.
     *
     * @param attestationObject The raw attestation object (CBOR-encoded)
     * @return The authenticator data bytes, or null if extraction fails
     */
    private fun extractAuthenticatorDataFromAttestation(attestationObject: ByteArray): ByteArray? {
        return extractAuthDataFromCbor(attestationObject)
    }

    /**
     * Determines the device type from authenticator data flags.
     *
     * The flags byte is at offset 32 of the authenticator data.
     * Bit 3 (0x08) indicates the backup eligibility (BE) flag:
     * - Set: "multiDevice" (synced/cloud-backed passkey, available across devices)
     * - Not set: "singleDevice" (hardware security key or device-bound credential)
     *
     * @param authenticatorData The raw authenticator data bytes
     * @return "multiDevice" or "singleDevice", or null if the data is too short
     */
    private fun extractDeviceType(authenticatorData: ByteArray?): String? {
        if (authenticatorData == null || authenticatorData.size < 33) return null

        val flags = authenticatorData[32].toInt() and 0xFF
        // Bit 3 (BE - Backup Eligibility): indicates multi-device credential
        return if (flags and 0x08 != 0) "multiDevice" else "singleDevice"
    }

    /**
     * Determines the backup status from authenticator data flags.
     *
     * The flags byte is at offset 32 of the authenticator data.
     * Bit 4 (0x10) indicates the backup state (BS) flag:
     * - Set: Credential is backed up / synced to cloud
     * - Not set: Credential is not backed up
     *
     * @param authenticatorData The raw authenticator data bytes
     * @return true if backed up, false if not, or null if the data is too short
     */
    private fun extractBackedUp(authenticatorData: ByteArray?): Boolean? {
        if (authenticatorData == null || authenticatorData.size < 33) return null

        val flags = authenticatorData[32].toInt() and 0xFF
        // Bit 4 (BS - Backup State): indicates credential is backed up
        return flags and 0x10 != 0
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
