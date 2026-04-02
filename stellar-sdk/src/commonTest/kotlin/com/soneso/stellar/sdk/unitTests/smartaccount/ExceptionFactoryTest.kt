//
//  ExceptionFactoryTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.ConfigurationException
import com.soneso.stellar.sdk.smartaccount.core.CredentialException
import com.soneso.stellar.sdk.smartaccount.core.SessionException
import com.soneso.stellar.sdk.smartaccount.core.SignerException
import com.soneso.stellar.sdk.smartaccount.core.StorageException
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WalletException
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SmartAccountException] companion factory message strings and return types.
 *
 * Each factory must:
 * 1. Return the correct sealed subclass type
 * 2. Include the input string in the resulting message
 */
class ExceptionFactoryTest {

    // ========================================================================
    // ConfigurationException
    // ========================================================================

    @Test
    fun testConfigurationException_invalidConfig_returnsInvalidConfigType() {
        val ex = ConfigurationException.invalidConfig("some detail")
        assertIs<ConfigurationException.InvalidConfig>(ex)
    }

    @Test
    fun testConfigurationException_invalidConfig_messageContainsDetail() {
        val detail = "webauthnVerifierAddress is wrong"
        val ex = ConfigurationException.invalidConfig(detail)
        assertNotNull(ex.message)
        assertContains(ex.message!!, detail, message = "invalidConfig message must contain detail")
    }

    @Test
    fun testConfigurationException_missingConfig_returnsMissingConfigType() {
        val ex = ConfigurationException.missingConfig("rpcUrl")
        assertIs<ConfigurationException.MissingConfig>(ex)
    }

    @Test
    fun testConfigurationException_missingConfig_messageContainsField() {
        val field = "accountWasmHash"
        val ex = ConfigurationException.missingConfig(field)
        assertNotNull(ex.message)
        assertContains(ex.message!!, field, message = "missingConfig message must contain field name")
    }

    // ========================================================================
    // WalletException
    // ========================================================================

    @Test
    fun testWalletException_notFound_returnsNotFoundType() {
        val ex = WalletException.notFound("wallet-123")
        assertIs<WalletException.NotFound>(ex)
    }

    @Test
    fun testWalletException_notFound_messageContainsId() {
        val id = "wallet-abc-123"
        val ex = WalletException.notFound(id)
        assertNotNull(ex.message)
        assertContains(ex.message!!, id, message = "notFound message must contain wallet id")
    }

    @Test
    fun testWalletException_alreadyExists_returnsAlreadyExistsType() {
        val ex = WalletException.alreadyExists("my-wallet")
        assertIs<WalletException.AlreadyExists>(ex)
    }

    @Test
    fun testWalletException_alreadyExists_messageContainsIdentifier() {
        val identifier = "my-wallet-id"
        val ex = WalletException.alreadyExists(identifier)
        assertNotNull(ex.message)
        assertContains(ex.message!!, identifier)
    }

    @Test
    fun testWalletException_notConnected_returnsNotConnectedType() {
        val ex = WalletException.notConnected()
        assertIs<WalletException.NotConnected>(ex)
    }

    @Test
    fun testWalletException_notConnected_withDetails_messageContainsDetails() {
        val details = "no browser extension found"
        val ex = WalletException.notConnected(details)
        assertNotNull(ex.message)
        assertContains(ex.message!!, details)
    }

    // ========================================================================
    // ValidationException
    // ========================================================================

    @Test
    fun testValidationException_invalidInput_returnsInvalidInputType() {
        val ex = ValidationException.invalidInput("amount", "must be positive")
        assertIs<ValidationException.InvalidInput>(ex)
    }

    @Test
    fun testValidationException_invalidInput_messageContainsBothFieldAndReason() {
        val field = "threshold"
        val reason = "must be at least 1"
        val ex = ValidationException.invalidInput(field, reason)
        assertNotNull(ex.message)
        assertContains(ex.message!!, field, message = "invalidInput message must contain field name")
        assertContains(ex.message!!, reason, message = "invalidInput message must contain reason")
    }

    @Test
    fun testValidationException_invalidAddress_returnsInvalidAddressType() {
        val ex = ValidationException.invalidAddress("GABC123")
        assertIs<ValidationException.InvalidAddress>(ex)
    }

    @Test
    fun testValidationException_invalidAddress_messageContainsAddress() {
        val addr = "INVALID_ADDRESS_VALUE"
        val ex = ValidationException.invalidAddress(addr)
        assertNotNull(ex.message)
        assertContains(ex.message!!, addr, message = "invalidAddress message must contain address")
    }

    @Test
    fun testValidationException_invalidAmount_returnsInvalidAmountType() {
        val ex = ValidationException.invalidAmount("-5")
        assertIs<ValidationException.InvalidAmount>(ex)
    }

    @Test
    fun testValidationException_invalidAmount_messageContainsAmount() {
        val amount = "-99.99"
        val ex = ValidationException.invalidAmount(amount)
        assertNotNull(ex.message)
        assertContains(ex.message!!, amount)
    }

    @Test
    fun testValidationException_invalidAmount_withReason_messageContainsReason() {
        val reason = "cannot be negative"
        val ex = ValidationException.invalidAmount("-5", reason)
        assertNotNull(ex.message)
        assertContains(ex.message!!, reason)
    }

    // ========================================================================
    // TransactionException
    // ========================================================================

    @Test
    fun testTransactionException_simulationFailed_returnsSimulationFailedType() {
        val ex = TransactionException.simulationFailed("out of gas")
        assertIs<TransactionException.SimulationFailed>(ex)
    }

    @Test
    fun testTransactionException_simulationFailed_messageContainsReason() {
        val reason = "contract call failed with code 3"
        val ex = TransactionException.simulationFailed(reason)
        assertNotNull(ex.message)
        assertContains(ex.message!!, reason)
    }

    @Test
    fun testTransactionException_submissionFailed_returnsSubmissionFailedType() {
        val ex = TransactionException.submissionFailed("network timeout")
        assertIs<TransactionException.SubmissionFailed>(ex)
    }

    @Test
    fun testTransactionException_submissionFailed_messageContainsReason() {
        val reason = "insufficient fee"
        val ex = TransactionException.submissionFailed(reason)
        assertNotNull(ex.message)
        assertContains(ex.message!!, reason)
    }

    @Test
    fun testTransactionException_timeout_returnsTimeoutType() {
        val ex = TransactionException.timeout("waited 30s")
        assertIs<TransactionException.Timeout>(ex)
    }

    @Test
    fun testTransactionException_timeout_withDetails_messageContainsDetails() {
        val details = "polling exceeded 60 seconds"
        val ex = TransactionException.timeout(details)
        assertNotNull(ex.message)
        assertContains(ex.message!!, details)
    }

    @Test
    fun testTransactionException_timeout_noDetails_hasNonBlankMessage() {
        val ex = TransactionException.timeout()
        assertNotNull(ex.message)
        assertTrue(ex.message!!.isNotBlank(), "timeout() with no details must produce non-blank message")
    }

    @Test
    fun testTransactionException_signingFailed_returnsSigningFailedType() {
        val ex = TransactionException.signingFailed("keypair invalid")
        assertIs<TransactionException.SigningFailed>(ex)
    }

    @Test
    fun testTransactionException_signingFailed_messageContainsReason() {
        val reason = "auth entry has void credentials"
        val ex = TransactionException.signingFailed(reason)
        assertNotNull(ex.message)
        assertContains(ex.message!!, reason)
    }

    // ========================================================================
    // WebAuthnException
    // ========================================================================

    @Test
    fun testWebAuthnException_registrationFailed_returnsRegistrationFailedType() {
        val ex = WebAuthnException.registrationFailed("device error")
        assertIs<WebAuthnException.RegistrationFailed>(ex)
    }

    @Test
    fun testWebAuthnException_registrationFailed_messageContainsReason() {
        val reason = "biometric sensor unavailable"
        val ex = WebAuthnException.registrationFailed(reason)
        assertNotNull(ex.message)
        assertContains(ex.message!!, reason)
    }

    @Test
    fun testWebAuthnException_authenticationFailed_returnsAuthenticationFailedType() {
        val ex = WebAuthnException.authenticationFailed("wrong pin")
        assertIs<WebAuthnException.AuthenticationFailed>(ex)
    }

    @Test
    fun testWebAuthnException_authenticationFailed_messageContainsReason() {
        val reason = "signature verification failed"
        val ex = WebAuthnException.authenticationFailed(reason)
        assertNotNull(ex.message)
        assertContains(ex.message!!, reason)
    }

    @Test
    fun testWebAuthnException_cancelled_returnsCancelledType() {
        val ex = WebAuthnException.cancelled()
        assertIs<WebAuthnException.Cancelled>(ex)
    }

    @Test
    fun testWebAuthnException_cancelled_hasNonBlankMessage() {
        val ex = WebAuthnException.cancelled()
        assertNotNull(ex.message)
        assertTrue(ex.message!!.isNotBlank(), "cancelled() must produce non-blank message")
    }

    @Test
    fun testWebAuthnException_notSupported_returnsNotSupportedType() {
        val ex = WebAuthnException.notSupported("no secure enclave")
        assertIs<WebAuthnException.NotSupported>(ex)
    }

    @Test
    fun testWebAuthnException_notSupported_withDetails_messageContainsDetails() {
        val details = "this browser does not support WebAuthn"
        val ex = WebAuthnException.notSupported(details)
        assertNotNull(ex.message)
        assertContains(ex.message!!, details)
    }

    @Test
    fun testWebAuthnException_notSupported_noDetails_hasNonBlankMessage() {
        val ex = WebAuthnException.notSupported()
        assertNotNull(ex.message)
        assertTrue(ex.message!!.isNotBlank())
    }

    // ========================================================================
    // CredentialException
    // ========================================================================

    @Test
    fun testCredentialException_notFound_returnsNotFoundType() {
        val ex = CredentialException.notFound("cred-123")
        assertIs<CredentialException.NotFound>(ex)
    }

    @Test
    fun testCredentialException_notFound_messageContainsId() {
        val id = "my-credential-id"
        val ex = CredentialException.notFound(id)
        assertNotNull(ex.message)
        assertContains(ex.message!!, id)
    }

    @Test
    fun testCredentialException_deploymentFailed_returnsDeploymentFailedType() {
        val ex = CredentialException.deploymentFailed("tx rejected")
        assertIs<CredentialException.DeploymentFailed>(ex)
    }

    @Test
    fun testCredentialException_deploymentFailed_messageContainsReason() {
        val reason = "insufficient balance in deployer"
        val ex = CredentialException.deploymentFailed(reason)
        assertNotNull(ex.message)
        assertContains(ex.message!!, reason)
    }

    // ========================================================================
    // StorageException
    // ========================================================================

    @Test
    fun testStorageException_readFailed_returnsReadFailedType() {
        val ex = StorageException.readFailed("credentials")
        assertIs<StorageException.ReadFailed>(ex)
    }

    @Test
    fun testStorageException_readFailed_messageContainsKey() {
        val key = "session-key-abc"
        val ex = StorageException.readFailed(key)
        assertNotNull(ex.message)
        assertContains(ex.message!!, key)
    }

    @Test
    fun testStorageException_writeFailed_returnsWriteFailedType() {
        val ex = StorageException.writeFailed("session")
        assertIs<StorageException.WriteFailed>(ex)
    }

    @Test
    fun testStorageException_writeFailed_messageContainsKey() {
        val key = "credential-key-xyz"
        val ex = StorageException.writeFailed(key)
        assertNotNull(ex.message)
        assertContains(ex.message!!, key)
    }

    // ========================================================================
    // SignerException
    // ========================================================================

    @Test
    fun testSignerException_notFound_returnsNotFoundType() {
        val ex = SignerException.notFound("signer-abc")
        assertIs<SignerException.NotFound>(ex)
    }

    @Test
    fun testSignerException_notFound_messageContainsId() {
        val id = "GA7QYNF7SOWQ3G"
        val ex = SignerException.notFound(id)
        assertNotNull(ex.message)
        assertContains(ex.message!!, id)
    }

    @Test
    fun testSignerException_invalid_returnsInvalidType() {
        val ex = SignerException.invalid("key format error")
        assertIs<SignerException.Invalid>(ex)
    }

    @Test
    fun testSignerException_invalid_messageContainsReason() {
        val reason = "public key has wrong length"
        val ex = SignerException.invalid(reason)
        assertNotNull(ex.message)
        assertContains(ex.message!!, reason)
    }

    // ========================================================================
    // SessionException
    // ========================================================================

    @Test
    fun testSessionException_expired_returnsExpiredType() {
        val ex = SessionException.expired()
        assertIs<SessionException.Expired>(ex)
    }

    @Test
    fun testSessionException_expired_hasNonBlankMessage() {
        val ex = SessionException.expired()
        assertNotNull(ex.message)
        assertTrue(ex.message!!.isNotBlank())
    }

    @Test
    fun testSessionException_expired_withSessionId_messageContainsId() {
        val sessionId = "session-abc-999"
        val ex = SessionException.expired(sessionId)
        assertNotNull(ex.message)
        assertContains(ex.message!!, sessionId)
    }

    @Test
    fun testSessionException_invalid_returnsInvalidType() {
        val ex = SessionException.invalid("token mismatch")
        assertIs<SessionException.Invalid>(ex)
    }

    @Test
    fun testSessionException_invalid_messageContainsReason() {
        val reason = "session token does not match stored value"
        val ex = SessionException.invalid(reason)
        assertNotNull(ex.message)
        assertContains(ex.message!!, reason)
    }

    // ========================================================================
    // SmartAccountException.toString()
    // ========================================================================

    @Test
    fun testSmartAccountException_toString_withoutCause_containsMessage() {
        val ex = ConfigurationException.InvalidConfig("bad rpc url")
        val str = ex.toString()
        assertContains(str, "bad rpc url", message = "toString() must include message")
    }

    @Test
    fun testSmartAccountException_toString_withCause_containsCauseMessage() {
        val cause = RuntimeException("underlying network error")
        val ex = ConfigurationException.InvalidConfig("config invalid", cause)
        val str = ex.toString()
        assertContains(str, "underlying network error", message = "toString() must include cause message")
    }

    @Test
    fun testSmartAccountException_toString_withoutCause_doesNotContainCausedBy() {
        val ex = ConfigurationException.InvalidConfig("only message, no cause")
        val str = ex.toString()
        // Without a cause, the "(caused by: ...)" portion should not appear
        assertTrue(
            !str.contains("caused by"),
            "toString() without cause must not contain 'caused by'"
        )
    }

    @Test
    fun testSmartAccountException_toString_containsErrorCode() {
        val ex = ConfigurationException.InvalidConfig("test")
        val str = ex.toString()
        // The error code numeric value is 1001
        assertContains(str, "1001", message = "toString() must contain error code number")
    }
}
