//
//  AppleWebAuthnProvider.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.SmartAccountUtils
import com.soneso.stellar.sdk.smartaccount.oz.OZConstants
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnRegistrationResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialAssertion
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialProvider
import platform.AuthenticationServices.ASAuthorizationPlatformPublicKeyCredentialRegistration
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Apple-native WebAuthn provider using AuthenticationServices framework.
 *
 * This provider implements WebAuthn passkey operations for iOS 16+ and macOS 13+
 * using Apple's ASAuthorizationPlatformPublicKeyCredentialProvider. It provides
 * biometric-secured credential creation and assertion for Stellar smart account
 * operations.
 *
 * The provider handles:
 * - Passkey creation with platform authenticator (Touch ID / Face ID)
 * - Passkey assertion for transaction signing
 * - Automatic public key extraction from attestation data
 * - Proper bridging of delegate callbacks to Kotlin coroutines
 *
 * Usage:
 * ```kotlin
 * val provider = AppleWebAuthnProvider(
 *     rpId = "example.com",
 *     rpName = "My Stellar Wallet"
 * )
 *
 * val config = OZSmartAccountConfig.builder(...)
 *     .webauthnProvider(provider)
 *     .build()
 * ```
 *
 * Note on macOS: ASAuthorizationController requires a presentation context provider
 * (ASAuthorizationControllerPresentationContextProviding) to specify the window in which
 * to present the authorization sheet. On iOS this is handled automatically. On macOS,
 * callers should set the presentationContextProvider on the controller via a platform-specific
 * integration point (e.g., a SwiftUI bridge or AppKit delegate). A future API revision may
 * accept an optional presentation context parameter.
 *
 * @property rpId The WebAuthn Relying Party identifier. Must match the domain where
 *           credentials are registered. For iOS apps, this must be an associated domain.
 * @property rpName Human-readable name displayed during authentication prompts.
 * @property timeout Operation timeout in milliseconds. Defaults to
 *           [OZConstants.WEBAUTHN_TIMEOUT_MS] (60 seconds).
 */
class AppleWebAuthnProvider(
    private val rpId: String,
    private val rpName: String,
    private val timeout: Long = OZConstants.WEBAUTHN_TIMEOUT_MS
) : WebAuthnProvider {

    /**
     * Strong reference to the active authorization delegate.
     *
     * ASAuthorizationController holds a weak reference to its delegate, so the delegate
     * must be retained by the provider for the duration of the authorization operation.
     * A local variable with @Suppress("UNUSED_VARIABLE") is not safe because the compiler
     * may optimize it away. This property is set before performing requests and cleared
     * in the delegate callbacks to avoid leaks.
     */
    private var activeDelegate: AuthorizationDelegate? = null

    init {
        require(rpId.isNotBlank()) { "rpId must not be blank" }
        require(rpName.isNotBlank()) { "rpName must not be blank" }
        require(timeout > 0) { "timeout must be positive" }
    }

    /**
     * Registers a new WebAuthn credential (passkey creation) using Apple's
     * AuthenticationServices framework.
     *
     * Triggers the platform authenticator (Touch ID / Face ID) to create a new
     * secp256r1 passkey. The resulting credential is bound to the configured
     * Relying Party ID and can be used to deploy and operate a smart account.
     *
     * The challenge bytes are passed directly to the platform authenticator
     * without modification.
     *
     * @param challenge The challenge bytes to bind to the credential (typically 32 bytes)
     * @param userId User identifier bytes for the credential (used for discoverable credentials)
     * @param userName Human-readable name displayed in the passkey creation dialog
     * @return [WebAuthnRegistrationResult] with credential ID, public key, attestation object,
     *         transport hints, device type, and backup status
     * @throws WebAuthnException.Cancelled if the user dismissed the authentication dialog
     * @throws WebAuthnException.NotSupported if passkeys are not available on this device
     * @throws WebAuthnException.RegistrationFailed if credential creation fails for any other reason
     */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun register(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String
    ): WebAuthnRegistrationResult {
        val challengeData = challenge.toNSData()
        val userIdData = userId.toNSData()

        val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(rpId)
        val request = provider.createCredentialRegistrationRequestWithChallenge(
            challenge = challengeData,
            name = userName,
            userID = userIdData
        )

        val authorization = performAuthorizationRequest(request, isRegistration = true)
        val credential = authorization.credential

        if (credential !is ASAuthorizationPlatformPublicKeyCredentialRegistration) {
            throw WebAuthnException.registrationFailed(
                "Unexpected credential type: ${credential::class.simpleName}"
            )
        }

        val credentialId = credential.credentialID.toByteArray()
        val rawAttestationObject = credential.rawAttestationObject
            ?: throw WebAuthnException.registrationFailed(
                "Attestation object is null"
            )
        val attestationBytes = rawAttestationObject.toByteArray()
        val clientDataJSON = credential.rawClientDataJSON.toByteArray()

        // Extract public key from attestation object using SmartAccountUtils
        // which handles CBOR parsing and COSE key extraction.
        val publicKey = try {
            SmartAccountUtils.extractPublicKeyFromAttestationObject(attestationBytes)
        } catch (e: Exception) {
            throw WebAuthnException.registrationFailed(
                "Failed to extract public key from attestation: ${e.message}",
                e
            )
        }

        // Parse authenticator data from the attestation object to determine
        // device type and backup status from the flags byte.
        val authData = extractAuthenticatorDataFromAttestation(attestationBytes)
        val flags = if (authData != null && authData.size > 32) {
            authData[32].toInt() and 0xFF
        } else {
            null
        }

        // Bit 3 (0x08): BE (Backup Eligibility) -> multi-device
        // Bit 4 (0x10): BS (Backup State) -> actually backed up
        val backupEligible = flags != null && (flags and 0x08) != 0
        val backedUp = flags != null && (flags and 0x10) != 0
        val deviceType = if (backupEligible) "multiDevice" else "singleDevice"

        return WebAuthnRegistrationResult(
            credentialId = credentialId,
            publicKey = publicKey,
            attestationObject = attestationBytes,
            transports = listOf("internal"),
            deviceType = deviceType,
            backedUp = backedUp
        )
    }

    /**
     * Authenticates with an existing WebAuthn credential (passkey assertion) using
     * Apple's AuthenticationServices framework.
     *
     * Triggers the platform authenticator (Touch ID / Face ID) to sign the provided
     * challenge with an existing passkey. The resulting signature can be used to
     * authorize Stellar smart account transactions.
     *
     * The challenge bytes are passed directly to the platform authenticator
     * without modification.
     *
     * @param challenge The challenge bytes to sign (authorization payload hash, typically 32 bytes)
     * @return [WebAuthnAuthenticationResult] with credential ID, authenticator data,
     *         client data JSON, and DER-encoded signature
     * @throws WebAuthnException.Cancelled if the user dismissed the authentication dialog
     * @throws WebAuthnException.NotSupported if passkeys are not available on this device
     * @throws WebAuthnException.AuthenticationFailed if assertion fails for any other reason
     */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun authenticate(
        challenge: ByteArray,
        allowCredentialIds: List<ByteArray>?
    ): WebAuthnAuthenticationResult {
        val challengeData = challenge.toNSData()

        val provider = ASAuthorizationPlatformPublicKeyCredentialProvider(rpId)
        val request = provider.createCredentialAssertionRequestWithChallenge(challengeData)

        val authorization = performAuthorizationRequest(request, isRegistration = false)
        val credential = authorization.credential

        if (credential !is ASAuthorizationPlatformPublicKeyCredentialAssertion) {
            throw WebAuthnException.authenticationFailed(
                "Unexpected credential type: ${credential::class.simpleName}"
            )
        }

        val credentialId = credential.credentialID.toByteArray()
        val rawAuthenticatorData = credential.rawAuthenticatorData
            ?: throw WebAuthnException.authenticationFailed(
                "Authenticator data is null"
            )
        val authenticatorData = rawAuthenticatorData.toByteArray()
        val clientDataJSON = credential.rawClientDataJSON.toByteArray()
        val signatureData = credential.signature
            ?: throw WebAuthnException.authenticationFailed(
                "Signature is null"
            )
        val signature = signatureData.toByteArray()

        return WebAuthnAuthenticationResult(
            credentialId = credentialId,
            authenticatorData = authenticatorData,
            clientDataJSON = clientDataJSON,
            signature = signature
        )
    }

    // MARK: - Private Implementation

    /**
     * Executes an ASAuthorization request and suspends until the delegate receives
     * a result or an error.
     *
     * This bridges Apple's delegate-based ASAuthorizationController API to Kotlin
     * coroutines using [suspendCancellableCoroutine]. The coroutine is resumed
     * when the delegate receives either a successful authorization or an error.
     *
     * The operation is wrapped in [withTimeout] using the configured [timeout] value
     * for consistency with other platform providers. If the timeout expires before the
     * delegate receives a callback, a [WebAuthnException] is thrown.
     *
     * @param request The ASAuthorization request to perform
     * @param isRegistration True if this is a registration ceremony, false for authentication.
     *        Used to select the appropriate error type in [mapAuthorizationError].
     * @return The successful [ASAuthorization] result
     * @throws WebAuthnException.Cancelled if the user cancelled the operation
     * @throws WebAuthnException.NotSupported if passkeys are not supported
     * @throws WebAuthnException.RegistrationFailed or [WebAuthnException.AuthenticationFailed]
     *         for other failures
     */
    @OptIn(BetaInteropApi::class)
    private suspend fun performAuthorizationRequest(
        request: platform.AuthenticationServices.ASAuthorizationRequest,
        isRegistration: Boolean
    ): ASAuthorization = try {
        withTimeout(timeout) {
            suspendCancellableCoroutine { continuation ->
                val delegate = AuthorizationDelegate(
                    onSuccess = { authorization ->
                        activeDelegate = null
                        if (continuation.isActive) {
                            continuation.resume(authorization)
                        }
                    },
                    onError = { error ->
                        activeDelegate = null
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                mapAuthorizationError(error, isRegistration)
                            )
                        }
                    }
                )

                // Store delegate as a class property to prevent deallocation.
                // ASAuthorizationController holds a weak reference to its delegate,
                // so a local variable is not sufficient -- the compiler may optimize it away.
                activeDelegate = delegate

                val controller = ASAuthorizationController(
                    authorizationRequests = listOf(request)
                )
                controller.delegate = delegate
                controller.performRequests()

                continuation.invokeOnCancellation {
                    activeDelegate = null
                    // ASAuthorizationController does not expose a cancel API.
                    // The delegate will simply not be called if the coroutine is cancelled.
                }
            }
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        activeDelegate = null
        if (isRegistration) {
            throw WebAuthnException.registrationFailed(
                "WebAuthn operation timed out after ${timeout}ms"
            )
        } else {
            throw WebAuthnException.authenticationFailed(
                "WebAuthn operation timed out after ${timeout}ms"
            )
        }
    }

    /**
     * Maps ASAuthorization errors to the appropriate [WebAuthnException] subtype.
     *
     * Error code mapping (from ASAuthorizationError):
     * - 1000: Unknown error
     * - 1001: Cancelled by user
     * - 1002: Invalid response
     * - 1003: Not handled
     * - 1004: Failed
     * - 1005: Not interactive (macOS)
     *
     * @param error The NSError from the authorization controller
     * @param isRegistration True if this error occurred during registration, false for
     *        authentication. Determines whether [WebAuthnException.RegistrationFailed]
     *        or [WebAuthnException.AuthenticationFailed] is returned.
     * @return The corresponding [WebAuthnException]
     */
    private fun mapAuthorizationError(error: NSError, isRegistration: Boolean): WebAuthnException {
        // ASAuthorizationError codes (ASAuthorizationErrorDomain)
        return when (error.code) {
            1001L -> WebAuthnException.cancelled()
            1002L -> {
                val msg = "Invalid response from authenticator: ${error.localizedDescription}"
                if (isRegistration) WebAuthnException.registrationFailed(msg)
                else WebAuthnException.authenticationFailed(msg)
            }
            1003L -> WebAuthnException.notSupported(
                "Passkey operation not handled: ${error.localizedDescription}"
            )
            1004L -> {
                val msg = "Authenticator operation failed: ${error.localizedDescription}"
                if (isRegistration) WebAuthnException.registrationFailed(msg)
                else WebAuthnException.authenticationFailed(msg)
            }
            else -> {
                val msg = "Authorization error (code ${error.code}): ${error.localizedDescription}"
                if (isRegistration) WebAuthnException.registrationFailed(msg)
                else WebAuthnException.authenticationFailed(msg)
            }
        }
    }

    /**
     * Extracts the authenticator data from a raw CBOR-encoded attestation object.
     *
     * WebAuthn attestation objects are CBOR maps. This method searches for the
     * "authData" key in the CBOR structure using a pattern-matching approach
     * that avoids requiring a full CBOR parser.
     *
     * The attestation object has this CBOR structure:
     * ```
     * {
     *   "fmt": "none",
     *   "attStmt": {},
     *   "authData": <byte string of authenticator data>
     * }
     * ```
     *
     * The method searches for the "authData" key (CBOR text string:
     * 0x68 "authData") followed by a CBOR byte string header (0x58 or 0x59),
     * then extracts the authenticator data bytes.
     *
     * @param attestationObject Raw attestation object bytes
     * @return Authenticator data bytes, or null if parsing fails
     */
    private fun extractAuthenticatorDataFromAttestation(
        attestationObject: ByteArray
    ): ByteArray? {
        // Search for "authData" key in the CBOR map.
        // CBOR encoding of text string "authData" (8 chars):
        // 0x68 (text string of length 8) + "authData" = 68 61 75 74 68 44 61 74 61
        val authDataKey = byteArrayOf(
            0x68, 0x61, 0x75, 0x74, 0x68, 0x44, 0x61, 0x74, 0x61
        )

        val keyIndex = SmartAccountUtils.findSubarray(attestationObject, authDataKey)
        if (keyIndex < 0) return null

        val valueStart = keyIndex + authDataKey.size
        if (valueStart >= attestationObject.size) return null

        val majorType = attestationObject[valueStart].toInt() and 0xFF

        return when {
            // Byte string with 1-byte length (0x58 prefix, lengths 0-255)
            majorType == 0x58 -> {
                if (valueStart + 1 >= attestationObject.size) return null
                val length = attestationObject[valueStart + 1].toInt() and 0xFF
                val dataStart = valueStart + 2
                if (dataStart + length > attestationObject.size) return null
                attestationObject.copyOfRange(dataStart, dataStart + length)
            }
            // Byte string with 2-byte length (0x59 prefix, lengths 256-65535)
            majorType == 0x59 -> {
                if (valueStart + 2 >= attestationObject.size) return null
                val length = ((attestationObject[valueStart + 1].toInt() and 0xFF) shl 8) or
                    (attestationObject[valueStart + 2].toInt() and 0xFF)
                val dataStart = valueStart + 3
                if (dataStart + length > attestationObject.size) return null
                attestationObject.copyOfRange(dataStart, dataStart + length)
            }
            // Short byte string (major type 2, length embedded in first byte: 0x40-0x57)
            majorType in 0x40..0x57 -> {
                val length = majorType - 0x40
                val dataStart = valueStart + 1
                if (dataStart + length > attestationObject.size) return null
                attestationObject.copyOfRange(dataStart, dataStart + length)
            }
            else -> null
        }
    }

    companion object {
        /**
         * Creates an AppleWebAuthnProvider for the given relying party configuration.
         *
         * This is a convenience factory method. On iOS, the rpId must match an
         * associated domain configured in the app's entitlements. On macOS, the rpId
         * must match a domain associated with the app.
         *
         * @param rpId The WebAuthn Relying Party identifier (domain)
         * @param rpName Human-readable name for the relying party
         * @param timeout Operation timeout in milliseconds (default:
         *        [OZConstants.WEBAUTHN_TIMEOUT_MS])
         * @return A configured [AppleWebAuthnProvider] instance
         */
        fun create(
            rpId: String,
            rpName: String,
            timeout: Long = OZConstants.WEBAUTHN_TIMEOUT_MS
        ): AppleWebAuthnProvider = AppleWebAuthnProvider(rpId, rpName, timeout)
    }
}

// MARK: - ASAuthorizationController Delegate

/**
 * Internal delegate implementation for ASAuthorizationController that bridges
 * Apple's callback-based API to Kotlin lambda callbacks.
 *
 * This class implements [ASAuthorizationControllerDelegateProtocol] and forwards
 * authorization results and errors to the provided callback functions.
 *
 * The delegate must be kept alive (held by a strong reference) for the duration
 * of the authorization operation, as ASAuthorizationController holds a weak
 * reference to its delegate.
 */
@Suppress("CONFLICTING_OVERLOADS")
private class AuthorizationDelegate(
    private val onSuccess: (ASAuthorization) -> Unit,
    private val onError: (NSError) -> Unit
) : NSObject(), ASAuthorizationControllerDelegateProtocol {

    /**
     * Called when authorization completes successfully.
     */
    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization
    ) {
        onSuccess(didCompleteWithAuthorization)
    }

    /**
     * Called when authorization fails with an error.
     */
    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError
    ) {
        onError(didCompleteWithError)
    }
}

// MARK: - NSData / ByteArray Extensions

/**
 * Converts a [ByteArray] to [NSData].
 *
 * Creates a new NSData instance containing a copy of the byte array contents.
 * The data is copied, so modifications to the original byte array do not affect
 * the returned NSData.
 *
 * @return NSData containing the byte array contents
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(
            bytes = pinned.addressOf(0),
            length = size.toULong()
        )
    }
}

/**
 * Converts an [NSData] to [ByteArray].
 *
 * Creates a new ByteArray containing a copy of the NSData contents.
 * The data is copied, so modifications to the returned byte array do not affect
 * the original NSData.
 *
 * @return ByteArray containing the NSData contents
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}
