//
//  WrapErrorTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

/**
 * Unit tests for [wrapError].
 *
 * Verifies that each [SmartAccountErrorCode] maps to the expected exception subclass,
 * that existing [SmartAccountException] instances pass through unchanged, and that
 * the original throwable is preserved as the cause.
 */
class WrapErrorTest {

    // MARK: - Pass-through behavior

    @Test
    fun testWrapError_smartAccountExceptionPassesThrough() {
        val original = ConfigurationException.InvalidConfig("already typed")
        val result = wrapError(original)
        assertSame(original, result, "SmartAccountException should be returned as-is")
    }

    @Test
    fun testWrapError_smartAccountExceptionSubclassPassesThrough() {
        val original = TransactionException.Timeout("timed out")
        val result = wrapError(original)
        assertSame(original, result)
    }

    // MARK: - Default code (INVALID_INPUT)

    @Test
    fun testWrapError_defaultCodeProducesValidationInvalidInput() {
        val cause = RuntimeException("something went wrong")
        val result = wrapError(cause)
        assertIs<ValidationException.InvalidInput>(result)
        assertEquals(SmartAccountErrorCode.INVALID_INPUT, result.code)
        assertSame(cause, result.cause)
    }

    @Test
    fun testWrapError_preservesOriginalMessage() {
        val cause = RuntimeException("original message")
        val result = wrapError(cause)
        assertEquals("original message", result.message)
    }

    @Test
    fun testWrapError_throwableWithoutMessage_usesToString() {
        val cause = object : Throwable() {
            override val message: String? = null
            override fun toString() = "CustomThrowable"
        }
        val result = wrapError(cause)
        assertEquals("CustomThrowable", result.message)
    }

    // MARK: - Configuration exceptions (1xxx)

    @Test
    fun testWrapError_invalidConfig() {
        val result = wrapError(RuntimeException("bad config"), SmartAccountErrorCode.INVALID_CONFIG)
        assertIs<ConfigurationException.InvalidConfig>(result)
        assertEquals(SmartAccountErrorCode.INVALID_CONFIG, result.code)
    }

    @Test
    fun testWrapError_missingConfig() {
        val result = wrapError(RuntimeException("missing"), SmartAccountErrorCode.MISSING_CONFIG)
        assertIs<ConfigurationException.MissingConfig>(result)
        assertEquals(SmartAccountErrorCode.MISSING_CONFIG, result.code)
    }

    // MARK: - Wallet exceptions (2xxx)

    @Test
    fun testWrapError_walletNotConnected() {
        val result = wrapError(RuntimeException("not connected"), SmartAccountErrorCode.WALLET_NOT_CONNECTED)
        assertIs<WalletException.NotConnected>(result)
        assertEquals(SmartAccountErrorCode.WALLET_NOT_CONNECTED, result.code)
    }

    @Test
    fun testWrapError_walletAlreadyExists() {
        val result = wrapError(RuntimeException("exists"), SmartAccountErrorCode.WALLET_ALREADY_EXISTS)
        assertIs<WalletException.AlreadyExists>(result)
        assertEquals(SmartAccountErrorCode.WALLET_ALREADY_EXISTS, result.code)
    }

    @Test
    fun testWrapError_walletNotFound() {
        val result = wrapError(RuntimeException("not found"), SmartAccountErrorCode.WALLET_NOT_FOUND)
        assertIs<WalletException.NotFound>(result)
        assertEquals(SmartAccountErrorCode.WALLET_NOT_FOUND, result.code)
    }

    // MARK: - Credential exceptions (3xxx)

    @Test
    fun testWrapError_credentialNotFound() {
        val result = wrapError(RuntimeException("no cred"), SmartAccountErrorCode.CREDENTIAL_NOT_FOUND)
        assertIs<CredentialException.NotFound>(result)
        assertEquals(SmartAccountErrorCode.CREDENTIAL_NOT_FOUND, result.code)
    }

    @Test
    fun testWrapError_credentialAlreadyExists() {
        val result = wrapError(RuntimeException("dup"), SmartAccountErrorCode.CREDENTIAL_ALREADY_EXISTS)
        assertIs<CredentialException.AlreadyExists>(result)
        assertEquals(SmartAccountErrorCode.CREDENTIAL_ALREADY_EXISTS, result.code)
    }

    @Test
    fun testWrapError_credentialInvalid() {
        val result = wrapError(RuntimeException("invalid"), SmartAccountErrorCode.CREDENTIAL_INVALID)
        assertIs<CredentialException.Invalid>(result)
        assertEquals(SmartAccountErrorCode.CREDENTIAL_INVALID, result.code)
    }

    @Test
    fun testWrapError_credentialDeploymentFailed() {
        val result = wrapError(RuntimeException("deploy fail"), SmartAccountErrorCode.CREDENTIAL_DEPLOYMENT_FAILED)
        assertIs<CredentialException.DeploymentFailed>(result)
        assertEquals(SmartAccountErrorCode.CREDENTIAL_DEPLOYMENT_FAILED, result.code)
    }

    // MARK: - WebAuthn exceptions (4xxx)

    @Test
    fun testWrapError_webAuthnRegistrationFailed() {
        val result = wrapError(RuntimeException("reg failed"), SmartAccountErrorCode.WEBAUTHN_REGISTRATION_FAILED)
        assertIs<WebAuthnException.RegistrationFailed>(result)
        assertEquals(SmartAccountErrorCode.WEBAUTHN_REGISTRATION_FAILED, result.code)
    }

    @Test
    fun testWrapError_webAuthnAuthenticationFailed() {
        val result = wrapError(RuntimeException("auth failed"), SmartAccountErrorCode.WEBAUTHN_AUTHENTICATION_FAILED)
        assertIs<WebAuthnException.AuthenticationFailed>(result)
        assertEquals(SmartAccountErrorCode.WEBAUTHN_AUTHENTICATION_FAILED, result.code)
    }

    @Test
    fun testWrapError_webAuthnNotSupported() {
        val result = wrapError(RuntimeException("not supported"), SmartAccountErrorCode.WEBAUTHN_NOT_SUPPORTED)
        assertIs<WebAuthnException.NotSupported>(result)
        assertEquals(SmartAccountErrorCode.WEBAUTHN_NOT_SUPPORTED, result.code)
    }

    @Test
    fun testWrapError_webAuthnCancelled() {
        val result = wrapError(RuntimeException("cancelled"), SmartAccountErrorCode.WEBAUTHN_CANCELLED)
        assertIs<WebAuthnException.Cancelled>(result)
        assertEquals(SmartAccountErrorCode.WEBAUTHN_CANCELLED, result.code)
    }

    // MARK: - Transaction exceptions (5xxx)

    @Test
    fun testWrapError_transactionSimulationFailed() {
        val result = wrapError(RuntimeException("sim fail"), SmartAccountErrorCode.TRANSACTION_SIMULATION_FAILED)
        assertIs<TransactionException.SimulationFailed>(result)
        assertEquals(SmartAccountErrorCode.TRANSACTION_SIMULATION_FAILED, result.code)
    }

    @Test
    fun testWrapError_transactionSigningFailed() {
        val result = wrapError(RuntimeException("sign fail"), SmartAccountErrorCode.TRANSACTION_SIGNING_FAILED)
        assertIs<TransactionException.SigningFailed>(result)
        assertEquals(SmartAccountErrorCode.TRANSACTION_SIGNING_FAILED, result.code)
    }

    @Test
    fun testWrapError_transactionSubmissionFailed() {
        val result = wrapError(RuntimeException("submit fail"), SmartAccountErrorCode.TRANSACTION_SUBMISSION_FAILED)
        assertIs<TransactionException.SubmissionFailed>(result)
        assertEquals(SmartAccountErrorCode.TRANSACTION_SUBMISSION_FAILED, result.code)
    }

    @Test
    fun testWrapError_transactionTimeout() {
        val result = wrapError(RuntimeException("timeout"), SmartAccountErrorCode.TRANSACTION_TIMEOUT)
        assertIs<TransactionException.Timeout>(result)
        assertEquals(SmartAccountErrorCode.TRANSACTION_TIMEOUT, result.code)
    }

    // MARK: - Signer exceptions (6xxx)

    @Test
    fun testWrapError_signerNotFound() {
        val result = wrapError(RuntimeException("no signer"), SmartAccountErrorCode.SIGNER_NOT_FOUND)
        assertIs<SignerException.NotFound>(result)
        assertEquals(SmartAccountErrorCode.SIGNER_NOT_FOUND, result.code)
    }

    @Test
    fun testWrapError_signerInvalid() {
        val result = wrapError(RuntimeException("bad signer"), SmartAccountErrorCode.SIGNER_INVALID)
        assertIs<SignerException.Invalid>(result)
        assertEquals(SmartAccountErrorCode.SIGNER_INVALID, result.code)
    }

    // MARK: - Validation exceptions (7xxx)

    @Test
    fun testWrapError_invalidAddress() {
        val result = wrapError(RuntimeException("bad addr"), SmartAccountErrorCode.INVALID_ADDRESS)
        assertIs<ValidationException.InvalidAddress>(result)
        assertEquals(SmartAccountErrorCode.INVALID_ADDRESS, result.code)
    }

    @Test
    fun testWrapError_invalidAmount() {
        val result = wrapError(RuntimeException("bad amount"), SmartAccountErrorCode.INVALID_AMOUNT)
        assertIs<ValidationException.InvalidAmount>(result)
        assertEquals(SmartAccountErrorCode.INVALID_AMOUNT, result.code)
    }

    @Test
    fun testWrapError_invalidInput() {
        val result = wrapError(RuntimeException("bad input"), SmartAccountErrorCode.INVALID_INPUT)
        assertIs<ValidationException.InvalidInput>(result)
        assertEquals(SmartAccountErrorCode.INVALID_INPUT, result.code)
    }

    // MARK: - Storage exceptions (8xxx)

    @Test
    fun testWrapError_storageReadFailed() {
        val result = wrapError(RuntimeException("read fail"), SmartAccountErrorCode.STORAGE_READ_FAILED)
        assertIs<StorageException.ReadFailed>(result)
        assertEquals(SmartAccountErrorCode.STORAGE_READ_FAILED, result.code)
    }

    @Test
    fun testWrapError_storageWriteFailed() {
        val result = wrapError(RuntimeException("write fail"), SmartAccountErrorCode.STORAGE_WRITE_FAILED)
        assertIs<StorageException.WriteFailed>(result)
        assertEquals(SmartAccountErrorCode.STORAGE_WRITE_FAILED, result.code)
    }

    // MARK: - Session exceptions (9xxx)

    @Test
    fun testWrapError_sessionExpired() {
        val result = wrapError(RuntimeException("expired"), SmartAccountErrorCode.SESSION_EXPIRED)
        assertIs<SessionException.Expired>(result)
        assertEquals(SmartAccountErrorCode.SESSION_EXPIRED, result.code)
    }

    @Test
    fun testWrapError_sessionInvalid() {
        val result = wrapError(RuntimeException("invalid session"), SmartAccountErrorCode.SESSION_INVALID)
        assertIs<SessionException.Invalid>(result)
        assertEquals(SmartAccountErrorCode.SESSION_INVALID, result.code)
    }

    // MARK: - All 22 error codes covered

    @Test
    fun testAllErrorCodes_allCodesMapped() {
        // Verify every enum entry produces a non-null SmartAccountException
        for (code in SmartAccountErrorCode.entries) {
            val cause = RuntimeException("test for $code")
            val result = wrapError(cause, code)
            assertEquals(code, result.code, "Code $code should produce exception with same code")
            assertSame(cause, result.cause, "Cause should be preserved for code $code")
        }
    }
}
