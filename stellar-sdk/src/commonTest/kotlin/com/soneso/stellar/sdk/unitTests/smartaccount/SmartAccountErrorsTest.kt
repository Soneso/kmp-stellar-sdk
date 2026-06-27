//
//  SmartAccountErrorsTest.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.unitTests.smartaccount

import com.soneso.stellar.sdk.smartaccount.core.ConfigurationException
import com.soneso.stellar.sdk.smartaccount.core.ContractErrorCodes
import com.soneso.stellar.sdk.smartaccount.core.CredentialException
import com.soneso.stellar.sdk.smartaccount.core.IndexerException
import com.soneso.stellar.sdk.smartaccount.core.SessionException
import com.soneso.stellar.sdk.smartaccount.core.SignerException
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountErrorCode
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountException
import com.soneso.stellar.sdk.smartaccount.core.StorageException
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.core.WalletException
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests for [SmartAccountErrors] covering error code values, factory method error codes,
 * cause preservation, toString formatting, inheritance, IndexerException, and constants.
 *
 * Complements [ExceptionFactoryTest] (type + message content) and [WrapErrorTest] (wrapError).
 */
class SmartAccountErrorsTest {

    // ========================================================================
    // SmartAccountErrorCode enum - numeric values
    // ========================================================================

    @Test
    fun testErrorCode_invalidConfig_is1001() {
        assertEquals(1001, SmartAccountErrorCode.INVALID_CONFIG.code)
    }

    @Test
    fun testErrorCode_missingConfig_is1002() {
        assertEquals(1002, SmartAccountErrorCode.MISSING_CONFIG.code)
    }

    @Test
    fun testErrorCode_walletNotConnected_is2001() {
        assertEquals(2001, SmartAccountErrorCode.WALLET_NOT_CONNECTED.code)
    }

    @Test
    fun testErrorCode_walletAlreadyExists_is2002() {
        assertEquals(2002, SmartAccountErrorCode.WALLET_ALREADY_EXISTS.code)
    }

    @Test
    fun testErrorCode_walletNotFound_is2003() {
        assertEquals(2003, SmartAccountErrorCode.WALLET_NOT_FOUND.code)
    }

    @Test
    fun testErrorCode_credentialNotFound_is3001() {
        assertEquals(3001, SmartAccountErrorCode.CREDENTIAL_NOT_FOUND.code)
    }

    @Test
    fun testErrorCode_credentialAlreadyExists_is3002() {
        assertEquals(3002, SmartAccountErrorCode.CREDENTIAL_ALREADY_EXISTS.code)
    }

    @Test
    fun testErrorCode_credentialInvalid_is3003() {
        assertEquals(3003, SmartAccountErrorCode.CREDENTIAL_INVALID.code)
    }

    @Test
    fun testErrorCode_credentialDeploymentFailed_is3004() {
        assertEquals(3004, SmartAccountErrorCode.CREDENTIAL_DEPLOYMENT_FAILED.code)
    }

    @Test
    fun testErrorCode_webAuthnRegistrationFailed_is4001() {
        assertEquals(4001, SmartAccountErrorCode.WEBAUTHN_REGISTRATION_FAILED.code)
    }

    @Test
    fun testErrorCode_webAuthnAuthenticationFailed_is4002() {
        assertEquals(4002, SmartAccountErrorCode.WEBAUTHN_AUTHENTICATION_FAILED.code)
    }

    @Test
    fun testErrorCode_webAuthnNotSupported_is4003() {
        assertEquals(4003, SmartAccountErrorCode.WEBAUTHN_NOT_SUPPORTED.code)
    }

    @Test
    fun testErrorCode_webAuthnCancelled_is4004() {
        assertEquals(4004, SmartAccountErrorCode.WEBAUTHN_CANCELLED.code)
    }

    @Test
    fun testErrorCode_transactionSimulationFailed_is5001() {
        assertEquals(5001, SmartAccountErrorCode.TRANSACTION_SIMULATION_FAILED.code)
    }

    @Test
    fun testErrorCode_transactionSigningFailed_is5002() {
        assertEquals(5002, SmartAccountErrorCode.TRANSACTION_SIGNING_FAILED.code)
    }

    @Test
    fun testErrorCode_transactionSubmissionFailed_is5003() {
        assertEquals(5003, SmartAccountErrorCode.TRANSACTION_SUBMISSION_FAILED.code)
    }

    @Test
    fun testErrorCode_transactionTimeout_is5004() {
        assertEquals(5004, SmartAccountErrorCode.TRANSACTION_TIMEOUT.code)
    }

    @Test
    fun testErrorCode_signerNotFound_is6001() {
        assertEquals(6001, SmartAccountErrorCode.SIGNER_NOT_FOUND.code)
    }

    @Test
    fun testErrorCode_signerInvalid_is6002() {
        assertEquals(6002, SmartAccountErrorCode.SIGNER_INVALID.code)
    }

    @Test
    fun testErrorCode_invalidAddress_is7001() {
        assertEquals(7001, SmartAccountErrorCode.INVALID_ADDRESS.code)
    }

    @Test
    fun testErrorCode_invalidAmount_is7002() {
        assertEquals(7002, SmartAccountErrorCode.INVALID_AMOUNT.code)
    }

    @Test
    fun testErrorCode_invalidInput_is7003() {
        assertEquals(7003, SmartAccountErrorCode.INVALID_INPUT.code)
    }

    @Test
    fun testErrorCode_storageReadFailed_is8001() {
        assertEquals(8001, SmartAccountErrorCode.STORAGE_READ_FAILED.code)
    }

    @Test
    fun testErrorCode_storageWriteFailed_is8002() {
        assertEquals(8002, SmartAccountErrorCode.STORAGE_WRITE_FAILED.code)
    }

    @Test
    fun testErrorCode_sessionExpired_is9001() {
        assertEquals(9001, SmartAccountErrorCode.SESSION_EXPIRED.code)
    }

    @Test
    fun testErrorCode_sessionInvalid_is9002() {
        assertEquals(9002, SmartAccountErrorCode.SESSION_INVALID.code)
    }

    @Test
    fun testErrorCode_indexerRequestFailed_is10001() {
        assertEquals(10001, SmartAccountErrorCode.INDEXER_REQUEST_FAILED.code)
    }

    @Test
    fun testErrorCode_indexerTimeout_is10002() {
        assertEquals(10002, SmartAccountErrorCode.INDEXER_TIMEOUT.code)
    }

    @Test
    fun testErrorCode_walletHeadlessConnection_is2004() {
        assertEquals(2004, SmartAccountErrorCode.WALLET_HEADLESS_CONNECTION.code)
    }

    @Test
    fun testErrorCode_totalEnumEntries_is29() {
        assertEquals(29, SmartAccountErrorCode.entries.size)
    }

    @Test
    fun testErrorCode_allCodesAreUnique() {
        val codes = SmartAccountErrorCode.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "All error codes must be unique")
    }

    // ========================================================================
    // Factory methods - error code property verification
    // ========================================================================

    @Test
    fun testConfigurationException_invalidConfig_setsCorrectCode() {
        val ex = ConfigurationException.invalidConfig("bad url")
        assertEquals(SmartAccountErrorCode.INVALID_CONFIG, ex.code)
    }

    @Test
    fun testConfigurationException_missingConfig_setsCorrectCode() {
        val ex = ConfigurationException.missingConfig("rpcUrl")
        assertEquals(SmartAccountErrorCode.MISSING_CONFIG, ex.code)
    }

    @Test
    fun testWalletException_notConnected_setsCorrectCode() {
        val ex = WalletException.notConnected()
        assertEquals(SmartAccountErrorCode.WALLET_NOT_CONNECTED, ex.code)
    }

    @Test
    fun testWalletException_alreadyExists_setsCorrectCode() {
        val ex = WalletException.alreadyExists("wallet-1")
        assertEquals(SmartAccountErrorCode.WALLET_ALREADY_EXISTS, ex.code)
    }

    @Test
    fun testWalletException_notFound_setsCorrectCode() {
        val ex = WalletException.notFound("wallet-1")
        assertEquals(SmartAccountErrorCode.WALLET_NOT_FOUND, ex.code)
    }

    @Test
    fun testCredentialException_notFound_setsCorrectCode() {
        val ex = CredentialException.notFound("cred-1")
        assertEquals(SmartAccountErrorCode.CREDENTIAL_NOT_FOUND, ex.code)
    }

    @Test
    fun testCredentialException_alreadyExists_setsCorrectCode() {
        val ex = CredentialException.alreadyExists("cred-1")
        assertEquals(SmartAccountErrorCode.CREDENTIAL_ALREADY_EXISTS, ex.code)
    }

    @Test
    fun testCredentialException_invalid_setsCorrectCode() {
        val ex = CredentialException.invalid("bad format")
        assertEquals(SmartAccountErrorCode.CREDENTIAL_INVALID, ex.code)
    }

    @Test
    fun testCredentialException_deploymentFailed_setsCorrectCode() {
        val ex = CredentialException.deploymentFailed("tx rejected")
        assertEquals(SmartAccountErrorCode.CREDENTIAL_DEPLOYMENT_FAILED, ex.code)
    }

    @Test
    fun testWebAuthnException_registrationFailed_setsCorrectCode() {
        val ex = WebAuthnException.registrationFailed("device error")
        assertEquals(SmartAccountErrorCode.WEBAUTHN_REGISTRATION_FAILED, ex.code)
    }

    @Test
    fun testWebAuthnException_authenticationFailed_setsCorrectCode() {
        val ex = WebAuthnException.authenticationFailed("bad pin")
        assertEquals(SmartAccountErrorCode.WEBAUTHN_AUTHENTICATION_FAILED, ex.code)
    }

    @Test
    fun testWebAuthnException_notSupported_setsCorrectCode() {
        val ex = WebAuthnException.notSupported()
        assertEquals(SmartAccountErrorCode.WEBAUTHN_NOT_SUPPORTED, ex.code)
    }

    @Test
    fun testWebAuthnException_cancelled_setsCorrectCode() {
        val ex = WebAuthnException.cancelled()
        assertEquals(SmartAccountErrorCode.WEBAUTHN_CANCELLED, ex.code)
    }

    @Test
    fun testTransactionException_simulationFailed_setsCorrectCode() {
        val ex = TransactionException.simulationFailed("out of gas")
        assertEquals(SmartAccountErrorCode.TRANSACTION_SIMULATION_FAILED, ex.code)
    }

    @Test
    fun testTransactionException_signingFailed_setsCorrectCode() {
        val ex = TransactionException.signingFailed("keypair invalid")
        assertEquals(SmartAccountErrorCode.TRANSACTION_SIGNING_FAILED, ex.code)
    }

    @Test
    fun testTransactionException_submissionFailed_setsCorrectCode() {
        val ex = TransactionException.submissionFailed("network error")
        assertEquals(SmartAccountErrorCode.TRANSACTION_SUBMISSION_FAILED, ex.code)
    }

    @Test
    fun testTransactionException_timeout_setsCorrectCode() {
        val ex = TransactionException.timeout()
        assertEquals(SmartAccountErrorCode.TRANSACTION_TIMEOUT, ex.code)
    }

    @Test
    fun testSignerException_notFound_setsCorrectCode() {
        val ex = SignerException.notFound("signer-1")
        assertEquals(SmartAccountErrorCode.SIGNER_NOT_FOUND, ex.code)
    }

    @Test
    fun testSignerException_invalid_setsCorrectCode() {
        val ex = SignerException.invalid("bad key")
        assertEquals(SmartAccountErrorCode.SIGNER_INVALID, ex.code)
    }

    @Test
    fun testValidationException_invalidAddress_setsCorrectCode() {
        val ex = ValidationException.invalidAddress("GXYZ")
        assertEquals(SmartAccountErrorCode.INVALID_ADDRESS, ex.code)
    }

    @Test
    fun testValidationException_invalidAmount_setsCorrectCode() {
        val ex = ValidationException.invalidAmount("-5")
        assertEquals(SmartAccountErrorCode.INVALID_AMOUNT, ex.code)
    }

    @Test
    fun testValidationException_invalidInput_setsCorrectCode() {
        val ex = ValidationException.invalidInput("field", "reason")
        assertEquals(SmartAccountErrorCode.INVALID_INPUT, ex.code)
    }

    @Test
    fun testStorageException_readFailed_setsCorrectCode() {
        val ex = StorageException.readFailed("key1")
        assertEquals(SmartAccountErrorCode.STORAGE_READ_FAILED, ex.code)
    }

    @Test
    fun testStorageException_writeFailed_setsCorrectCode() {
        val ex = StorageException.writeFailed("key1")
        assertEquals(SmartAccountErrorCode.STORAGE_WRITE_FAILED, ex.code)
    }

    @Test
    fun testSessionException_expired_setsCorrectCode() {
        val ex = SessionException.expired()
        assertEquals(SmartAccountErrorCode.SESSION_EXPIRED, ex.code)
    }

    @Test
    fun testSessionException_invalid_setsCorrectCode() {
        val ex = SessionException.invalid("token mismatch")
        assertEquals(SmartAccountErrorCode.SESSION_INVALID, ex.code)
    }

    @Test
    fun testIndexerException_requestFailed_setsCorrectCode() {
        val ex = IndexerException.requestFailed("HTTP 500")
        assertEquals(SmartAccountErrorCode.INDEXER_REQUEST_FAILED, ex.code)
    }

    @Test
    fun testIndexerException_timeout_setsCorrectCode() {
        val ex = IndexerException.timeout("https://indexer.example.com/api")
        assertEquals(SmartAccountErrorCode.INDEXER_TIMEOUT, ex.code)
    }

    // ========================================================================
    // Factory methods - cause preservation
    // ========================================================================

    @Test
    fun testConfigurationException_invalidConfig_preservesCause() {
        val cause = RuntimeException("underlying")
        val ex = ConfigurationException.invalidConfig("detail", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testConfigurationException_invalidConfig_causeNullByDefault() {
        val ex = ConfigurationException.invalidConfig("detail")
        assertNull(ex.cause)
    }

    @Test
    fun testConfigurationException_missingConfig_preservesCause() {
        val cause = IllegalStateException("missing")
        val ex = ConfigurationException.missingConfig("param", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testWalletException_notConnected_preservesCause() {
        val cause = RuntimeException("network down")
        val ex = WalletException.notConnected("detail", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testWalletException_alreadyExists_preservesCause() {
        val cause = RuntimeException("dup")
        val ex = WalletException.alreadyExists("id", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testWalletException_notFound_preservesCause() {
        val cause = RuntimeException("missing")
        val ex = WalletException.notFound("id", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testCredentialException_notFound_preservesCause() {
        val cause = RuntimeException("storage error")
        val ex = CredentialException.notFound("cred-1", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testCredentialException_alreadyExists_preservesCause() {
        val cause = RuntimeException("constraint violation")
        val ex = CredentialException.alreadyExists("cred-1", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testCredentialException_invalid_preservesCause() {
        val cause = RuntimeException("parse error")
        val ex = CredentialException.invalid("bad format", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testCredentialException_deploymentFailed_preservesCause() {
        val cause = RuntimeException("tx failed")
        val ex = CredentialException.deploymentFailed("out of funds", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testWebAuthnException_registrationFailed_preservesCause() {
        val cause = RuntimeException("hardware error")
        val ex = WebAuthnException.registrationFailed("sensor timeout", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testWebAuthnException_authenticationFailed_preservesCause() {
        val cause = RuntimeException("sig verify failed")
        val ex = WebAuthnException.authenticationFailed("bad assertion", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testWebAuthnException_notSupported_preservesCause() {
        val cause = RuntimeException("no platform authenticator")
        val ex = WebAuthnException.notSupported("old browser", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testWebAuthnException_cancelled_preservesCause() {
        val cause = RuntimeException("user abort")
        val ex = WebAuthnException.cancelled(cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testTransactionException_simulationFailed_preservesCause() {
        val cause = RuntimeException("rpc error")
        val ex = TransactionException.simulationFailed("host function failed", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testTransactionException_signingFailed_preservesCause() {
        val cause = RuntimeException("key error")
        val ex = TransactionException.signingFailed("missing key", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testTransactionException_submissionFailed_preservesCause() {
        val cause = RuntimeException("http 503")
        val ex = TransactionException.submissionFailed("server unavailable", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testTransactionException_timeout_preservesCause() {
        val cause = RuntimeException("poll exceeded")
        val ex = TransactionException.timeout("waited 60s", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testSignerException_notFound_preservesCause() {
        val cause = RuntimeException("lookup failed")
        val ex = SignerException.notFound("signer-abc", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testSignerException_invalid_preservesCause() {
        val cause = RuntimeException("decode error")
        val ex = SignerException.invalid("wrong length", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testValidationException_invalidAddress_preservesCause() {
        val cause = RuntimeException("checksum failed")
        val ex = ValidationException.invalidAddress("GABC", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testValidationException_invalidAmount_preservesCause() {
        val cause = RuntimeException("parse error")
        val ex = ValidationException.invalidAmount("abc", null, cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testValidationException_invalidInput_preservesCause() {
        val cause = RuntimeException("format error")
        val ex = ValidationException.invalidInput("memo", "too long", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testStorageException_readFailed_preservesCause() {
        val cause = RuntimeException("disk error")
        val ex = StorageException.readFailed("credentials", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testStorageException_writeFailed_preservesCause() {
        val cause = RuntimeException("disk full")
        val ex = StorageException.writeFailed("session", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testSessionException_expired_preservesCause() {
        val cause = RuntimeException("clock skew")
        val ex = SessionException.expired("session-1", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testSessionException_invalid_preservesCause() {
        val cause = RuntimeException("tampered")
        val ex = SessionException.invalid("bad token", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testIndexerException_requestFailed_preservesCause() {
        val cause = RuntimeException("connection refused")
        val ex = IndexerException.requestFailed("HTTP 500", cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testIndexerException_timeout_preservesCause() {
        val cause = RuntimeException("socket timeout")
        val ex = IndexerException.timeout("https://indexer.example.com", cause)
        assertSame(cause, ex.cause)
    }

    // ========================================================================
    // Factory methods - exact message format
    // ========================================================================

    @Test
    fun testConfigurationException_invalidConfig_exactMessageFormat() {
        val ex = ConfigurationException.invalidConfig("bad rpc url")
        assertEquals("Invalid configuration: bad rpc url", ex.message)
    }

    @Test
    fun testConfigurationException_missingConfig_exactMessageFormat() {
        val ex = ConfigurationException.missingConfig("accountWasmHash")
        assertEquals("Missing required configuration: accountWasmHash", ex.message)
    }

    @Test
    fun testWalletException_notConnected_defaultMessage() {
        val ex = WalletException.notConnected()
        assertEquals("Wallet is not connected", ex.message)
    }

    @Test
    fun testWalletException_notConnected_customMessage() {
        val ex = WalletException.notConnected("no extension found")
        assertEquals("no extension found", ex.message)
    }

    @Test
    fun testWalletException_alreadyExists_exactMessageFormat() {
        val ex = WalletException.alreadyExists("wallet-42")
        assertEquals("Wallet already exists: wallet-42", ex.message)
    }

    @Test
    fun testWalletException_notFound_exactMessageFormat() {
        val ex = WalletException.notFound("wallet-42")
        assertEquals("Wallet not found: wallet-42", ex.message)
    }

    @Test
    fun testCredentialException_notFound_exactMessageFormat() {
        val ex = CredentialException.notFound("cred-abc")
        assertEquals("Credential not found: cred-abc", ex.message)
    }

    @Test
    fun testCredentialException_alreadyExists_exactMessageFormat() {
        val ex = CredentialException.alreadyExists("cred-abc")
        assertEquals("Credential already exists: cred-abc", ex.message)
    }

    @Test
    fun testCredentialException_invalid_exactMessageFormat() {
        val ex = CredentialException.invalid("expired certificate")
        assertEquals("Invalid credential: expired certificate", ex.message)
    }

    @Test
    fun testCredentialException_deploymentFailed_exactMessageFormat() {
        val ex = CredentialException.deploymentFailed("insufficient funds")
        assertEquals("Credential deployment failed: insufficient funds", ex.message)
    }

    @Test
    fun testWebAuthnException_registrationFailed_exactMessageFormat() {
        val ex = WebAuthnException.registrationFailed("device not ready")
        assertEquals("WebAuthn registration failed: device not ready", ex.message)
    }

    @Test
    fun testWebAuthnException_authenticationFailed_exactMessageFormat() {
        val ex = WebAuthnException.authenticationFailed("invalid assertion")
        assertEquals("WebAuthn authentication failed: invalid assertion", ex.message)
    }

    @Test
    fun testWebAuthnException_notSupported_defaultMessage() {
        val ex = WebAuthnException.notSupported()
        assertEquals("WebAuthn is not supported on this platform", ex.message)
    }

    @Test
    fun testWebAuthnException_notSupported_customMessage() {
        val ex = WebAuthnException.notSupported("no secure enclave")
        assertEquals("no secure enclave", ex.message)
    }

    @Test
    fun testWebAuthnException_cancelled_defaultMessage() {
        val ex = WebAuthnException.cancelled()
        assertEquals("User cancelled WebAuthn operation", ex.message)
    }

    @Test
    fun testTransactionException_simulationFailed_exactMessageFormat() {
        val ex = TransactionException.simulationFailed("host function error")
        assertEquals("Transaction simulation failed: host function error", ex.message)
    }

    @Test
    fun testTransactionException_signingFailed_exactMessageFormat() {
        val ex = TransactionException.signingFailed("no private key")
        assertEquals("Transaction signing failed: no private key", ex.message)
    }

    @Test
    fun testTransactionException_submissionFailed_exactMessageFormat() {
        val ex = TransactionException.submissionFailed("fee too low")
        assertEquals("Transaction submission failed: fee too low", ex.message)
    }

    @Test
    fun testTransactionException_timeout_defaultMessage() {
        val ex = TransactionException.timeout()
        assertEquals("Transaction timed out", ex.message)
    }

    @Test
    fun testTransactionException_timeout_customMessage() {
        val ex = TransactionException.timeout("exceeded 120s")
        assertEquals("exceeded 120s", ex.message)
    }

    @Test
    fun testSignerException_notFound_exactMessageFormat() {
        val ex = SignerException.notFound("GA7QYN...")
        assertEquals("Signer not found: GA7QYN...", ex.message)
    }

    @Test
    fun testSignerException_invalid_exactMessageFormat() {
        val ex = SignerException.invalid("key too short")
        assertEquals("Invalid signer: key too short", ex.message)
    }

    @Test
    fun testValidationException_invalidAddress_exactMessageFormat() {
        val ex = ValidationException.invalidAddress("GXYZ123")
        assertEquals("Invalid address: GXYZ123", ex.message)
    }

    @Test
    fun testValidationException_invalidAmount_exactMessageFormat_withoutReason() {
        val ex = ValidationException.invalidAmount("-10")
        assertEquals("Invalid amount: -10", ex.message)
    }

    @Test
    fun testValidationException_invalidAmount_exactMessageFormat_withReason() {
        val ex = ValidationException.invalidAmount("-10", "must be positive")
        assertEquals("Invalid amount: -10 - must be positive", ex.message)
    }

    @Test
    fun testValidationException_invalidInput_exactMessageFormat() {
        val ex = ValidationException.invalidInput("memo", "exceeds 28 bytes")
        assertEquals("Invalid input for memo: exceeds 28 bytes", ex.message)
    }

    @Test
    fun testStorageException_readFailed_exactMessageFormat() {
        val ex = StorageException.readFailed("credentials")
        assertEquals("Storage read failed for key: credentials", ex.message)
    }

    @Test
    fun testStorageException_writeFailed_exactMessageFormat() {
        val ex = StorageException.writeFailed("session-data")
        assertEquals("Storage write failed for key: session-data", ex.message)
    }

    @Test
    fun testSessionException_expired_defaultMessage() {
        val ex = SessionException.expired()
        assertEquals("Session has expired", ex.message)
    }

    @Test
    fun testSessionException_expired_withSessionId() {
        val ex = SessionException.expired("sess-abc-123")
        assertEquals("Session expired: sess-abc-123", ex.message)
    }

    @Test
    fun testSessionException_invalid_exactMessageFormat() {
        val ex = SessionException.invalid("token corrupted")
        assertEquals("Invalid session: token corrupted", ex.message)
    }

    @Test
    fun testIndexerException_requestFailed_exactMessageFormat() {
        val ex = IndexerException.requestFailed("HTTP 503")
        assertEquals("Indexer request failed: HTTP 503", ex.message)
    }

    @Test
    fun testIndexerException_timeout_exactMessageFormat() {
        val ex = IndexerException.timeout("https://indexer.stellar.org/v1")
        assertEquals("Indexer request timed out: https://indexer.stellar.org/v1", ex.message)
    }

    // ========================================================================
    // IndexerException - return types
    // ========================================================================

    @Test
    fun testIndexerException_requestFailed_returnsRequestFailedType() {
        val ex = IndexerException.requestFailed("error")
        assertIs<IndexerException.RequestFailed>(ex)
    }

    @Test
    fun testIndexerException_timeout_returnsTimeoutType() {
        val ex = IndexerException.timeout("https://example.com")
        assertIs<IndexerException.Timeout>(ex)
    }

    @Test
    fun testIndexerException_requestFailed_causeNullByDefault() {
        val ex = IndexerException.requestFailed("error")
        assertNull(ex.cause)
    }

    @Test
    fun testIndexerException_timeout_causeNullByDefault() {
        val ex = IndexerException.timeout("https://example.com")
        assertNull(ex.cause)
    }

    // ========================================================================
    // CredentialException - alreadyExists and invalid return types
    // (not tested in ExceptionFactoryTest)
    // ========================================================================

    @Test
    fun testCredentialException_alreadyExists_returnsAlreadyExistsType() {
        val ex = CredentialException.alreadyExists("cred-dup")
        assertIs<CredentialException.AlreadyExists>(ex)
    }

    @Test
    fun testCredentialException_invalid_returnsInvalidType() {
        val ex = CredentialException.invalid("expired")
        assertIs<CredentialException.Invalid>(ex)
    }

    // ========================================================================
    // Inheritance - all exceptions are SmartAccountException
    // ========================================================================

    @Test
    fun testConfigurationException_isSmartAccountException() {
        val ex = ConfigurationException.invalidConfig("test")
        assertIs<SmartAccountException>(ex)
    }

    @Test
    fun testWalletException_isSmartAccountException() {
        val ex = WalletException.notFound("test")
        assertIs<SmartAccountException>(ex)
    }

    @Test
    fun testCredentialException_isSmartAccountException() {
        val ex = CredentialException.notFound("test")
        assertIs<SmartAccountException>(ex)
    }

    @Test
    fun testWebAuthnException_isSmartAccountException() {
        val ex = WebAuthnException.cancelled()
        assertIs<SmartAccountException>(ex)
    }

    @Test
    fun testTransactionException_isSmartAccountException() {
        val ex = TransactionException.timeout()
        assertIs<SmartAccountException>(ex)
    }

    @Test
    fun testSignerException_isSmartAccountException() {
        val ex = SignerException.notFound("test")
        assertIs<SmartAccountException>(ex)
    }

    @Test
    fun testValidationException_isSmartAccountException() {
        val ex = ValidationException.invalidAddress("test")
        assertIs<SmartAccountException>(ex)
    }

    @Test
    fun testStorageException_isSmartAccountException() {
        val ex = StorageException.readFailed("test")
        assertIs<SmartAccountException>(ex)
    }

    @Test
    fun testSessionException_isSmartAccountException() {
        val ex = SessionException.expired()
        assertIs<SmartAccountException>(ex)
    }

    @Test
    fun testIndexerException_isSmartAccountException() {
        val ex = IndexerException.requestFailed("test")
        assertIs<SmartAccountException>(ex)
    }

    @Test
    fun testAllExceptions_areAlsoJavaExceptions() {
        val ex = ConfigurationException.invalidConfig("test")
        assertIs<Exception>(ex)
    }

    // ========================================================================
    // Intermediate sealed class hierarchy
    // ========================================================================

    @Test
    fun testConfigurationException_invalidConfig_isConfigurationException() {
        val ex = ConfigurationException.invalidConfig("test")
        assertIs<ConfigurationException>(ex)
    }

    @Test
    fun testWalletException_notConnected_isWalletException() {
        val ex = WalletException.notConnected()
        assertIs<WalletException>(ex)
    }

    @Test
    fun testCredentialException_deploymentFailed_isCredentialException() {
        val ex = CredentialException.deploymentFailed("test")
        assertIs<CredentialException>(ex)
    }

    @Test
    fun testWebAuthnException_registrationFailed_isWebAuthnException() {
        val ex = WebAuthnException.registrationFailed("test")
        assertIs<WebAuthnException>(ex)
    }

    @Test
    fun testTransactionException_simulationFailed_isTransactionException() {
        val ex = TransactionException.simulationFailed("test")
        assertIs<TransactionException>(ex)
    }

    @Test
    fun testSignerException_invalid_isSignerException() {
        val ex = SignerException.invalid("test")
        assertIs<SignerException>(ex)
    }

    @Test
    fun testValidationException_invalidAmount_isValidationException() {
        val ex = ValidationException.invalidAmount("test")
        assertIs<ValidationException>(ex)
    }

    @Test
    fun testStorageException_writeFailed_isStorageException() {
        val ex = StorageException.writeFailed("test")
        assertIs<StorageException>(ex)
    }

    @Test
    fun testSessionException_invalid_isSessionException() {
        val ex = SessionException.invalid("test")
        assertIs<SessionException>(ex)
    }

    @Test
    fun testIndexerException_timeout_isIndexerException() {
        val ex = IndexerException.timeout("test")
        assertIs<IndexerException>(ex)
    }

    // ========================================================================
    // toString() format across exception categories
    // ========================================================================

    @Test
    fun testToString_walletException_containsCodeAndMessage() {
        val ex = WalletException.notFound("wallet-99")
        val str = ex.toString()
        assertContains(str, "SmartAccountException")
        assertContains(str, "2003")
        assertContains(str, "Wallet not found: wallet-99")
    }

    @Test
    fun testToString_credentialException_withCause() {
        val cause = RuntimeException("disk I/O error")
        val ex = CredentialException.notFound("cred-1", cause)
        val str = ex.toString()
        assertContains(str, "3001")
        assertContains(str, "caused by: disk I/O error")
    }

    @Test
    fun testToString_webAuthnException_withoutCause() {
        val ex = WebAuthnException.cancelled()
        val str = ex.toString()
        assertContains(str, "4004")
        assertContains(str, "User cancelled WebAuthn operation")
        assertTrue(!str.contains("caused by"))
    }

    @Test
    fun testToString_transactionException_format() {
        val ex = TransactionException.simulationFailed("out of gas")
        val str = ex.toString()
        assertContains(str, "5001")
        assertContains(str, "Transaction simulation failed: out of gas")
    }

    @Test
    fun testToString_signerException_format() {
        val ex = SignerException.invalid("wrong length")
        val str = ex.toString()
        assertContains(str, "6002")
    }

    @Test
    fun testToString_validationException_format() {
        val ex = ValidationException.invalidAddress("bad")
        val str = ex.toString()
        assertContains(str, "7001")
    }

    @Test
    fun testToString_storageException_format() {
        val ex = StorageException.readFailed("key")
        val str = ex.toString()
        assertContains(str, "8001")
    }

    @Test
    fun testToString_sessionException_format() {
        val ex = SessionException.expired()
        val str = ex.toString()
        assertContains(str, "9001")
    }

    @Test
    fun testToString_indexerException_format() {
        val ex = IndexerException.requestFailed("500")
        val str = ex.toString()
        assertContains(str, "10001")
    }

    @Test
    fun testToString_indexerTimeout_withCause() {
        val cause = RuntimeException("socket closed")
        val ex = IndexerException.timeout("https://example.com", cause)
        val str = ex.toString()
        assertContains(str, "10002")
        assertContains(str, "caused by: socket closed")
    }

    @Test
    fun testToString_causeWithNullMessage_noCausedByAppended() {
        val cause = object : Throwable() {
            override val message: String? = null
        }
        val ex = ConfigurationException.invalidConfig("detail", cause)
        val str = ex.toString()
        // cause.message is null, so "(caused by: ...)" should not appear
        assertTrue(!str.contains("caused by"), "cause with null message should not produce 'caused by'")
    }

    // ========================================================================
    // Base class properties via direct constructors
    // ========================================================================

    @Test
    fun testDirectConstructor_configurationExceptionInvalidConfig_properties() {
        val cause = RuntimeException("root")
        val ex = ConfigurationException.InvalidConfig("direct message", cause)
        assertEquals(SmartAccountErrorCode.INVALID_CONFIG, ex.code)
        assertEquals("direct message", ex.message)
        assertSame(cause, ex.cause)
    }

    @Test
    fun testDirectConstructor_walletExceptionNotConnected_defaultMessage() {
        val ex = WalletException.NotConnected()
        assertEquals("Wallet is not connected", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun testDirectConstructor_webAuthnExceptionNotSupported_defaultMessage() {
        val ex = WebAuthnException.NotSupported()
        assertEquals("WebAuthn is not supported on this platform", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun testDirectConstructor_webAuthnExceptionCancelled_defaultMessage() {
        val ex = WebAuthnException.Cancelled()
        assertEquals("User cancelled WebAuthn operation", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun testDirectConstructor_transactionExceptionTimeout_defaultMessage() {
        val ex = TransactionException.Timeout()
        assertEquals("Transaction timed out", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun testDirectConstructor_sessionExceptionExpired_defaultMessage() {
        val ex = SessionException.Expired()
        assertEquals("Session has expired", ex.message)
        assertNull(ex.cause)
    }

    // ========================================================================
    // SmartAccountConstants
    // ========================================================================

    @Test
    fun testSmartAccountConstants_ed25519PublicKeySize() {
        assertEquals(32, SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE)
    }

    @Test
    fun testSmartAccountConstants_secp256r1PublicKeySize() {
        assertEquals(65, SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE)
    }

    @Test
    fun testSmartAccountConstants_uncompressedPubkeyPrefix() {
        assertEquals(0x04.toByte(), SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX)
    }

    // ========================================================================
    // ContractErrorCodes
    // ========================================================================

    @Test
    fun testContractErrorCodes_mathOverflow() {
        assertEquals(3012, ContractErrorCodes.MATH_OVERFLOW)
    }

    @Test
    fun testContractErrorCodes_keyDataTooLarge() {
        assertEquals(3013, ContractErrorCodes.KEY_DATA_TOO_LARGE)
    }

    @Test
    fun testContractErrorCodes_contextRuleIdsLengthMismatch() {
        assertEquals(3014, ContractErrorCodes.CONTEXT_RULE_IDS_LENGTH_MISMATCH)
    }

    @Test
    fun testContractErrorCodes_nameTooLong() {
        assertEquals(3015, ContractErrorCodes.NAME_TOO_LONG)
    }

    @Test
    fun testContractErrorCodes_unauthorizedSigner() {
        assertEquals(3016, ContractErrorCodes.UNAUTHORIZED_SIGNER)
    }

    // ========================================================================
    // wrapError - additional edge cases not in WrapErrorTest
    // ========================================================================

    @Test
    fun testWrapError_indexerRequestFailed() {
        val cause = RuntimeException("connection refused")
        val result = SmartAccountException.wrapError(cause, SmartAccountErrorCode.INDEXER_REQUEST_FAILED)
        assertIs<IndexerException.RequestFailed>(result)
        assertEquals(SmartAccountErrorCode.INDEXER_REQUEST_FAILED, result.code)
        assertEquals("connection refused", result.message)
        assertSame(cause, result.cause)
    }

    @Test
    fun testWrapError_indexerTimeout() {
        val cause = RuntimeException("read timeout")
        val result = SmartAccountException.wrapError(cause, SmartAccountErrorCode.INDEXER_TIMEOUT)
        assertIs<IndexerException.Timeout>(result)
        assertEquals(SmartAccountErrorCode.INDEXER_TIMEOUT, result.code)
        assertEquals("read timeout", result.message)
        assertSame(cause, result.cause)
    }

    @Test
    fun testWrapError_nestedSmartAccountException_returnsOutermost() {
        val inner = ConfigurationException.invalidConfig("inner")
        val outer = WalletException.NotFound("outer", inner)
        val result = SmartAccountException.wrapError(outer)
        assertSame(outer, result, "Should return outer SmartAccountException as-is")
        assertIs<WalletException.NotFound>(result)
    }
}
