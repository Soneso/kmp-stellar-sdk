//
//  MockWebAuthnProvider.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnRegistrationResult

/**
 * Configurable mock implementation of [WebAuthnProvider] for unit testing.
 *
 * This mock allows tests to:
 * - Supply predetermined registration and authentication results
 * - Configure exceptions to simulate error conditions
 * - Track call counts and captured arguments for verification
 *
 * By default, the mock produces valid registration and authentication results
 * using synthetic test data. Override [registrationResult], [authenticationResult],
 * [registrationException], or [authenticationException] to change the behavior.
 *
 * Example:
 * ```kotlin
 * val mock = MockWebAuthnProvider()
 * mock.registrationException = WebAuthnException.cancelled()
 *
 * assertFailsWith<WebAuthnException.Cancelled> {
 *     mock.register(challenge, userId, userName)
 * }
 * assertEquals(1, mock.registerCallCount)
 * ```
 */
class MockWebAuthnProvider : WebAuthnProvider {

    // MARK: - Configuration

    /**
     * The result to return from [register]. If null and [registrationException] is also null,
     * a default synthetic result is generated.
     */
    var registrationResult: WebAuthnRegistrationResult? = null

    /**
     * The exception to throw from [register]. Takes precedence over [registrationResult].
     */
    var registrationException: WebAuthnException? = null

    /**
     * The result to return from [authenticate]. If null and [authenticationException] is also null,
     * a default synthetic result is generated.
     */
    var authenticationResult: WebAuthnAuthenticationResult? = null

    /**
     * The exception to throw from [authenticate]. Takes precedence over [authenticationResult].
     */
    var authenticationException: WebAuthnException? = null

    // MARK: - Call Tracking

    /** Number of times [register] has been called. */
    var registerCallCount: Int = 0
        private set

    /** Number of times [authenticate] has been called. */
    var authenticateCallCount: Int = 0
        private set

    /** The most recent challenge passed to [register], or null if never called. */
    var lastRegisterChallenge: ByteArray? = null
        private set

    /** The most recent userId passed to [register], or null if never called. */
    var lastRegisterUserId: ByteArray? = null
        private set

    /** The most recent userName passed to [register], or null if never called. */
    var lastRegisterUserName: String? = null
        private set

    /** The most recent challenge passed to [authenticate], or null if never called. */
    var lastAuthenticateChallenge: ByteArray? = null
        private set

    // MARK: - WebAuthnProvider Implementation

    override suspend fun register(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String
    ): WebAuthnRegistrationResult {
        registerCallCount++
        lastRegisterChallenge = challenge.copyOf()
        lastRegisterUserId = userId.copyOf()
        lastRegisterUserName = userName

        registrationException?.let { throw it }

        return registrationResult ?: defaultRegistrationResult()
    }

    override suspend fun authenticate(
        challenge: ByteArray,
        allowCredentialIds: List<ByteArray>?
    ): WebAuthnAuthenticationResult {
        authenticateCallCount++
        lastAuthenticateChallenge = challenge.copyOf()

        authenticationException?.let { throw it }

        return authenticationResult ?: defaultAuthenticationResult()
    }

    // MARK: - Reset

    /**
     * Resets all call tracking state and configured responses to defaults.
     */
    fun reset() {
        registrationResult = null
        registrationException = null
        authenticationResult = null
        authenticationException = null
        registerCallCount = 0
        authenticateCallCount = 0
        lastRegisterChallenge = null
        lastRegisterUserId = null
        lastRegisterUserName = null
        lastAuthenticateChallenge = null
    }

    // MARK: - Default Responses

    companion object {
        /**
         * Creates a test public key (65 bytes, uncompressed secp256r1 format starting with 0x04).
         */
        fun testPublicKey(seed: Int = 0): ByteArray {
            return ByteArray(65) { i ->
                if (i == 0) 0x04.toByte()
                else ((i + seed) % 256).toByte()
            }
        }

        /**
         * Creates a test credential ID (16 bytes).
         */
        fun testCredentialId(seed: Int = 0): ByteArray {
            return ByteArray(16) { ((it + seed) % 256).toByte() }
        }

        /**
         * Creates a test attestation object (synthetic, not a valid CBOR structure).
         */
        fun testAttestationObject(seed: Int = 0): ByteArray {
            return ByteArray(128) { ((it + seed + 0x10) % 256).toByte() }
        }
    }

    private fun defaultRegistrationResult(): WebAuthnRegistrationResult {
        return WebAuthnRegistrationResult(
            credentialId = testCredentialId(),
            publicKey = testPublicKey(),
            attestationObject = testAttestationObject(),
            transports = listOf("internal"),
            deviceType = "multiDevice",
            backedUp = true
        )
    }

    private fun defaultAuthenticationResult(): WebAuthnAuthenticationResult {
        return WebAuthnAuthenticationResult(
            credentialId = testCredentialId(),
            authenticatorData = ByteArray(37) { it.toByte() },
            clientDataJSON = """{"type":"webauthn.get","challenge":"test"}""".encodeToByteArray(),
            signature = ByteArray(64) { it.toByte() }
        )
    }
}
