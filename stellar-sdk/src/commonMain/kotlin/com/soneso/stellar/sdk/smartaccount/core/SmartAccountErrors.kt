//
//  SmartAccountErrors.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk.smartaccount.core

/**
 * Error codes for Smart Account operations.
 *
 * Error code ranges:
 * - 1xxx: Configuration errors
 * - 2xxx: Wallet state errors
 * - 3xxx: Credential errors
 * - 4xxx: WebAuthn errors
 * - 5xxx: Transaction errors
 * - 6xxx: Signer errors
 * - 7xxx: Validation errors
 * - 8xxx: Storage errors
 * - 9xxx: Session errors
 * - 10xxx: Indexer errors
 */
enum class SmartAccountErrorCode(val code: Int) {
    // 1xxx: Configuration errors
    INVALID_CONFIG(1001),
    MISSING_CONFIG(1002),

    // 2xxx: Wallet state errors
    WALLET_NOT_CONNECTED(2001),
    WALLET_ALREADY_EXISTS(2002),
    WALLET_NOT_FOUND(2003),

    // 3xxx: Credential errors
    CREDENTIAL_NOT_FOUND(3001),
    CREDENTIAL_ALREADY_EXISTS(3002),
    CREDENTIAL_INVALID(3003),
    CREDENTIAL_DEPLOYMENT_FAILED(3004),

    // 4xxx: WebAuthn errors
    WEBAUTHN_REGISTRATION_FAILED(4001),
    WEBAUTHN_AUTHENTICATION_FAILED(4002),
    WEBAUTHN_NOT_SUPPORTED(4003),
    WEBAUTHN_CANCELLED(4004),

    // 5xxx: Transaction errors
    TRANSACTION_SIMULATION_FAILED(5001),
    TRANSACTION_SIGNING_FAILED(5002),
    TRANSACTION_SUBMISSION_FAILED(5003),
    TRANSACTION_TIMEOUT(5004),

    // 6xxx: Signer errors
    SIGNER_NOT_FOUND(6001),
    SIGNER_INVALID(6002),

    // 7xxx: Validation errors
    INVALID_ADDRESS(7001),
    INVALID_AMOUNT(7002),
    INVALID_INPUT(7003),

    // 8xxx: Storage errors
    STORAGE_READ_FAILED(8001),
    STORAGE_WRITE_FAILED(8002),

    // 9xxx: Session errors
    SESSION_EXPIRED(9001),
    SESSION_INVALID(9002),

    // 10xxx: Indexer errors
    INDEXER_REQUEST_FAILED(10001),
    INDEXER_TIMEOUT(10002)
}

/**
 * Base sealed class for Smart Account exceptions.
 *
 * SmartAccountException provides detailed error information including error codes,
 * descriptive messages, and optional underlying causes.
 *
 * Example error handling:
 * ```kotlin
 * try {
 *     val wallet = smartAccountKit.createWallet(name = "My Wallet")
 *     println("Wallet created: ${wallet.address}")
 * } catch (e: SmartAccountException) {
 *     when (e) {
 *         is WebAuthnException.Cancelled ->
 *             println("User cancelled authentication")
 *         is CredentialException.DeploymentFailed ->
 *             println("Failed to deploy contract: ${e.message}")
 *         else ->
 *             println("Error ${e.code.code}: ${e.message}")
 *     }
 * }
 * ```
 */
sealed class SmartAccountException(
    val code: SmartAccountErrorCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {
    override fun toString(): String = buildString {
        append("SmartAccountException [${code.code}]: $message")
        cause?.message?.let { append(" (caused by: $it)") }
    }

    companion object {
        /**
         * Wraps an arbitrary [Throwable] into a [SmartAccountException].
         *
         * If the throwable is already a [SmartAccountException], it is returned as-is.
         * Otherwise, a new exception subclass matching [defaultCode] is created,
         * preserving the original throwable as [cause].
         *
         * Ensures all errors surfaced from the Smart Account Kit are consistently typed.
         *
         * @param err The throwable to wrap
         * @param defaultCode The error code to use when wrapping non-SmartAccountException errors.
         *   Defaults to [SmartAccountErrorCode.INVALID_INPUT].
         * @return A [SmartAccountException] wrapping the original error
         */
        fun wrapError(
            err: Throwable,
            defaultCode: SmartAccountErrorCode = SmartAccountErrorCode.INVALID_INPUT
        ): SmartAccountException {
            if (err is SmartAccountException) {
                return err
            }
            val message = err.message ?: err.toString()
            return when (defaultCode) {
                SmartAccountErrorCode.INVALID_CONFIG ->
                    ConfigurationException.InvalidConfig(message, err)
                SmartAccountErrorCode.MISSING_CONFIG ->
                    ConfigurationException.MissingConfig(message, err)
                SmartAccountErrorCode.WALLET_NOT_CONNECTED ->
                    WalletException.NotConnected(message, err)
                SmartAccountErrorCode.WALLET_ALREADY_EXISTS ->
                    WalletException.AlreadyExists(message, err)
                SmartAccountErrorCode.WALLET_NOT_FOUND ->
                    WalletException.NotFound(message, err)
                SmartAccountErrorCode.CREDENTIAL_NOT_FOUND ->
                    CredentialException.NotFound(message, err)
                SmartAccountErrorCode.CREDENTIAL_ALREADY_EXISTS ->
                    CredentialException.AlreadyExists(message, err)
                SmartAccountErrorCode.CREDENTIAL_INVALID ->
                    CredentialException.Invalid(message, err)
                SmartAccountErrorCode.CREDENTIAL_DEPLOYMENT_FAILED ->
                    CredentialException.DeploymentFailed(message, err)
                SmartAccountErrorCode.WEBAUTHN_REGISTRATION_FAILED ->
                    WebAuthnException.RegistrationFailed(message, err)
                SmartAccountErrorCode.WEBAUTHN_AUTHENTICATION_FAILED ->
                    WebAuthnException.AuthenticationFailed(message, err)
                SmartAccountErrorCode.WEBAUTHN_NOT_SUPPORTED ->
                    WebAuthnException.NotSupported(message, err)
                SmartAccountErrorCode.WEBAUTHN_CANCELLED ->
                    WebAuthnException.Cancelled(message, err)
                SmartAccountErrorCode.TRANSACTION_SIMULATION_FAILED ->
                    TransactionException.SimulationFailed(message, err)
                SmartAccountErrorCode.TRANSACTION_SIGNING_FAILED ->
                    TransactionException.SigningFailed(message, err)
                SmartAccountErrorCode.TRANSACTION_SUBMISSION_FAILED ->
                    TransactionException.SubmissionFailed(message, err)
                SmartAccountErrorCode.TRANSACTION_TIMEOUT ->
                    TransactionException.Timeout(message, err)
                SmartAccountErrorCode.SIGNER_NOT_FOUND ->
                    SignerException.NotFound(message, err)
                SmartAccountErrorCode.SIGNER_INVALID ->
                    SignerException.Invalid(message, err)
                SmartAccountErrorCode.INVALID_ADDRESS ->
                    ValidationException.InvalidAddress(message, err)
                SmartAccountErrorCode.INVALID_AMOUNT ->
                    ValidationException.InvalidAmount(message, err)
                SmartAccountErrorCode.INVALID_INPUT ->
                    ValidationException.InvalidInput(message, err)
                SmartAccountErrorCode.STORAGE_READ_FAILED ->
                    StorageException.ReadFailed(message, err)
                SmartAccountErrorCode.STORAGE_WRITE_FAILED ->
                    StorageException.WriteFailed(message, err)
                SmartAccountErrorCode.SESSION_EXPIRED ->
                    SessionException.Expired(message, err)
                SmartAccountErrorCode.SESSION_INVALID ->
                    SessionException.Invalid(message, err)
                SmartAccountErrorCode.INDEXER_REQUEST_FAILED ->
                    IndexerException.RequestFailed(message, err)
                SmartAccountErrorCode.INDEXER_TIMEOUT ->
                    IndexerException.Timeout(message, err)
            }
        }
    }
}

// ============================================================================
// Configuration Exceptions
// ============================================================================

/**
 * Configuration-related errors (1xxx range).
 */
sealed class ConfigurationException(
    code: SmartAccountErrorCode,
    message: String,
    cause: Throwable? = null
) : SmartAccountException(code, message, cause) {

    /**
     * Invalid configuration error.
     */
    class InvalidConfig(message: String, cause: Throwable? = null) :
        ConfigurationException(SmartAccountErrorCode.INVALID_CONFIG, message, cause)

    /**
     * Missing required configuration error.
     */
    class MissingConfig(message: String, cause: Throwable? = null) :
        ConfigurationException(SmartAccountErrorCode.MISSING_CONFIG, message, cause)

    companion object {
        /**
         * Creates an invalid configuration error.
         *
         * @param details Description of what is invalid in the configuration
         * @param cause Optional underlying cause
         * @return InvalidConfig exception instance
         */
        fun invalidConfig(details: String, cause: Throwable? = null) =
            InvalidConfig("Invalid configuration: $details", cause)

        /**
         * Creates a missing configuration error.
         *
         * @param param Name of the missing configuration parameter
         * @param cause Optional underlying cause
         * @return MissingConfig exception instance
         */
        fun missingConfig(param: String, cause: Throwable? = null) =
            MissingConfig("Missing required configuration: $param", cause)
    }
}

// ============================================================================
// Wallet State Exceptions
// ============================================================================

/**
 * Wallet state-related errors (2xxx range).
 */
sealed class WalletException(
    code: SmartAccountErrorCode,
    message: String,
    cause: Throwable? = null
) : SmartAccountException(code, message, cause) {

    /**
     * Wallet not connected error.
     */
    class NotConnected(message: String = "Wallet is not connected", cause: Throwable? = null) :
        WalletException(SmartAccountErrorCode.WALLET_NOT_CONNECTED, message, cause)

    /**
     * Wallet already exists error.
     */
    class AlreadyExists(message: String, cause: Throwable? = null) :
        WalletException(SmartAccountErrorCode.WALLET_ALREADY_EXISTS, message, cause)

    /**
     * Wallet not found error.
     */
    class NotFound(message: String, cause: Throwable? = null) :
        WalletException(SmartAccountErrorCode.WALLET_NOT_FOUND, message, cause)

    companion object {
        /**
         * Creates a wallet not connected error.
         *
         * @param details Optional detailed description
         * @param cause Optional underlying cause
         * @return NotConnected exception instance
         */
        fun notConnected(details: String? = null, cause: Throwable? = null) =
            NotConnected(details ?: "Wallet is not connected", cause)

        /**
         * Creates a wallet already exists error.
         *
         * @param identifier The identifier of the wallet that already exists
         * @param cause Optional underlying cause
         * @return AlreadyExists exception instance
         */
        fun alreadyExists(identifier: String, cause: Throwable? = null) =
            AlreadyExists("Wallet already exists: $identifier", cause)

        /**
         * Creates a wallet not found error.
         *
         * @param identifier The identifier of the wallet that was not found
         * @param cause Optional underlying cause
         * @return NotFound exception instance
         */
        fun notFound(identifier: String, cause: Throwable? = null) =
            NotFound("Wallet not found: $identifier", cause)
    }
}

// ============================================================================
// Credential Exceptions
// ============================================================================

/**
 * Credential-related errors (3xxx range).
 */
sealed class CredentialException(
    code: SmartAccountErrorCode,
    message: String,
    cause: Throwable? = null
) : SmartAccountException(code, message, cause) {

    /**
     * Credential not found error.
     */
    class NotFound(message: String, cause: Throwable? = null) :
        CredentialException(SmartAccountErrorCode.CREDENTIAL_NOT_FOUND, message, cause)

    /**
     * Credential already exists error.
     */
    class AlreadyExists(message: String, cause: Throwable? = null) :
        CredentialException(SmartAccountErrorCode.CREDENTIAL_ALREADY_EXISTS, message, cause)

    /**
     * Invalid credential error.
     */
    class Invalid(message: String, cause: Throwable? = null) :
        CredentialException(SmartAccountErrorCode.CREDENTIAL_INVALID, message, cause)

    /**
     * Credential deployment failed error.
     */
    class DeploymentFailed(message: String, cause: Throwable? = null) :
        CredentialException(SmartAccountErrorCode.CREDENTIAL_DEPLOYMENT_FAILED, message, cause)

    companion object {
        /**
         * Creates a credential not found error.
         *
         * @param credentialId The ID of the credential that was not found
         * @param cause Optional underlying cause
         * @return NotFound exception instance
         */
        fun notFound(credentialId: String, cause: Throwable? = null) =
            NotFound("Credential not found: $credentialId", cause)

        /**
         * Creates a credential already exists error.
         *
         * @param credentialId The ID of the credential that already exists
         * @param cause Optional underlying cause
         * @return AlreadyExists exception instance
         */
        fun alreadyExists(credentialId: String, cause: Throwable? = null) =
            AlreadyExists("Credential already exists: $credentialId", cause)

        /**
         * Creates an invalid credential error.
         *
         * @param reason Description of why the credential is invalid
         * @param cause Optional underlying cause
         * @return Invalid exception instance
         */
        fun invalid(reason: String, cause: Throwable? = null) =
            Invalid("Invalid credential: $reason", cause)

        /**
         * Creates a credential deployment failed error.
         *
         * @param reason Description of why deployment failed
         * @param cause Optional underlying cause
         * @return DeploymentFailed exception instance
         */
        fun deploymentFailed(reason: String, cause: Throwable? = null) =
            DeploymentFailed("Credential deployment failed: $reason", cause)
    }
}

// ============================================================================
// WebAuthn Exceptions
// ============================================================================

/**
 * WebAuthn-related errors (4xxx range).
 */
sealed class WebAuthnException(
    code: SmartAccountErrorCode,
    message: String,
    cause: Throwable? = null
) : SmartAccountException(code, message, cause) {

    /**
     * WebAuthn registration failed error.
     */
    class RegistrationFailed(message: String, cause: Throwable? = null) :
        WebAuthnException(SmartAccountErrorCode.WEBAUTHN_REGISTRATION_FAILED, message, cause)

    /**
     * WebAuthn authentication failed error.
     */
    class AuthenticationFailed(message: String, cause: Throwable? = null) :
        WebAuthnException(SmartAccountErrorCode.WEBAUTHN_AUTHENTICATION_FAILED, message, cause)

    /**
     * WebAuthn not supported error.
     */
    class NotSupported(message: String = "WebAuthn is not supported on this platform", cause: Throwable? = null) :
        WebAuthnException(SmartAccountErrorCode.WEBAUTHN_NOT_SUPPORTED, message, cause)

    /**
     * User cancelled WebAuthn operation error.
     */
    class Cancelled(message: String = "User cancelled WebAuthn operation", cause: Throwable? = null) :
        WebAuthnException(SmartAccountErrorCode.WEBAUTHN_CANCELLED, message, cause)

    companion object {
        /**
         * Creates a WebAuthn registration failed error.
         *
         * @param reason Description of why registration failed
         * @param cause Optional underlying cause
         * @return RegistrationFailed exception instance
         */
        fun registrationFailed(reason: String, cause: Throwable? = null) =
            RegistrationFailed("WebAuthn registration failed: $reason", cause)

        /**
         * Creates a WebAuthn authentication failed error.
         *
         * @param reason Description of why authentication failed
         * @param cause Optional underlying cause
         * @return AuthenticationFailed exception instance
         */
        fun authenticationFailed(reason: String, cause: Throwable? = null) =
            AuthenticationFailed("WebAuthn authentication failed: $reason", cause)

        /**
         * Creates a WebAuthn not supported error.
         *
         * @param details Optional additional details about platform limitations
         * @param cause Optional underlying cause
         * @return NotSupported exception instance
         */
        fun notSupported(details: String? = null, cause: Throwable? = null) =
            NotSupported(details ?: "WebAuthn is not supported on this platform", cause)

        /**
         * Creates a user cancelled WebAuthn operation error.
         *
         * @param cause Optional underlying cause
         * @return Cancelled exception instance
         */
        fun cancelled(cause: Throwable? = null) =
            Cancelled(cause = cause)
    }
}

// ============================================================================
// Transaction Exceptions
// ============================================================================

/**
 * Transaction-related errors (5xxx range).
 */
sealed class TransactionException(
    code: SmartAccountErrorCode,
    message: String,
    cause: Throwable? = null
) : SmartAccountException(code, message, cause) {

    /**
     * Transaction simulation failed error.
     */
    class SimulationFailed(message: String, cause: Throwable? = null) :
        TransactionException(SmartAccountErrorCode.TRANSACTION_SIMULATION_FAILED, message, cause)

    /**
     * Transaction signing failed error.
     */
    class SigningFailed(message: String, cause: Throwable? = null) :
        TransactionException(SmartAccountErrorCode.TRANSACTION_SIGNING_FAILED, message, cause)

    /**
     * Transaction submission failed error.
     */
    class SubmissionFailed(message: String, cause: Throwable? = null) :
        TransactionException(SmartAccountErrorCode.TRANSACTION_SUBMISSION_FAILED, message, cause)

    /**
     * Transaction timeout error.
     */
    class Timeout(message: String = "Transaction timed out", cause: Throwable? = null) :
        TransactionException(SmartAccountErrorCode.TRANSACTION_TIMEOUT, message, cause)

    companion object {
        /**
         * Creates a transaction simulation failed error.
         *
         * @param reason Description of why simulation failed
         * @param cause Optional underlying cause
         * @return SimulationFailed exception instance
         */
        fun simulationFailed(reason: String, cause: Throwable? = null) =
            SimulationFailed("Transaction simulation failed: $reason", cause)

        /**
         * Creates a transaction signing failed error.
         *
         * @param reason Description of why signing failed
         * @param cause Optional underlying cause
         * @return SigningFailed exception instance
         */
        fun signingFailed(reason: String, cause: Throwable? = null) =
            SigningFailed("Transaction signing failed: $reason", cause)

        /**
         * Creates a transaction submission failed error.
         *
         * @param reason Description of why submission failed
         * @param cause Optional underlying cause
         * @return SubmissionFailed exception instance
         */
        fun submissionFailed(reason: String, cause: Throwable? = null) =
            SubmissionFailed("Transaction submission failed: $reason", cause)

        /**
         * Creates a transaction timeout error.
         *
         * @param details Optional additional timeout details
         * @param cause Optional underlying cause
         * @return Timeout exception instance
         */
        fun timeout(details: String? = null, cause: Throwable? = null) =
            Timeout(details ?: "Transaction timed out", cause)
    }
}

// ============================================================================
// Signer Exceptions
// ============================================================================

/**
 * Signer-related errors (6xxx range).
 */
sealed class SignerException(
    code: SmartAccountErrorCode,
    message: String,
    cause: Throwable? = null
) : SmartAccountException(code, message, cause) {

    /**
     * Signer not found error.
     */
    class NotFound(message: String, cause: Throwable? = null) :
        SignerException(SmartAccountErrorCode.SIGNER_NOT_FOUND, message, cause)

    /**
     * Invalid signer error.
     */
    class Invalid(message: String, cause: Throwable? = null) :
        SignerException(SmartAccountErrorCode.SIGNER_INVALID, message, cause)

    companion object {
        /**
         * Creates a signer not found error.
         *
         * @param signerId The ID or identifier of the signer that was not found
         * @param cause Optional underlying cause
         * @return NotFound exception instance
         */
        fun notFound(signerId: String, cause: Throwable? = null) =
            NotFound("Signer not found: $signerId", cause)

        /**
         * Creates an invalid signer error.
         *
         * @param reason Description of why the signer is invalid
         * @param cause Optional underlying cause
         * @return Invalid exception instance
         */
        fun invalid(reason: String, cause: Throwable? = null) =
            Invalid("Invalid signer: $reason", cause)
    }
}

// ============================================================================
// Validation Exceptions
// ============================================================================

/**
 * Validation-related errors (7xxx range).
 */
sealed class ValidationException(
    code: SmartAccountErrorCode,
    message: String,
    cause: Throwable? = null
) : SmartAccountException(code, message, cause) {

    /**
     * Invalid address error.
     */
    class InvalidAddress(message: String, cause: Throwable? = null) :
        ValidationException(SmartAccountErrorCode.INVALID_ADDRESS, message, cause)

    /**
     * Invalid amount error.
     */
    class InvalidAmount(message: String, cause: Throwable? = null) :
        ValidationException(SmartAccountErrorCode.INVALID_AMOUNT, message, cause)

    /**
     * Invalid input error.
     */
    class InvalidInput(message: String, cause: Throwable? = null) :
        ValidationException(SmartAccountErrorCode.INVALID_INPUT, message, cause)

    companion object {
        /**
         * Creates an invalid address error.
         *
         * @param address The invalid address string
         * @param cause Optional underlying cause
         * @return InvalidAddress exception instance
         */
        fun invalidAddress(address: String, cause: Throwable? = null) =
            InvalidAddress("Invalid address: $address", cause)

        /**
         * Creates an invalid amount error.
         *
         * @param amount The invalid amount string
         * @param reason Optional reason describing why the amount is invalid
         * @param cause Optional underlying cause
         * @return InvalidAmount exception instance
         */
        fun invalidAmount(amount: String, reason: String? = null, cause: Throwable? = null) =
            InvalidAmount("Invalid amount: $amount${reason?.let { " - $it" } ?: ""}", cause)

        /**
         * Creates an invalid input error.
         *
         * @param field The name of the invalid field
         * @param reason Description of why the input is invalid
         * @param cause Optional underlying cause
         * @return InvalidInput exception instance
         */
        fun invalidInput(field: String, reason: String, cause: Throwable? = null) =
            InvalidInput("Invalid input for $field: $reason", cause)
    }
}

// ============================================================================
// Storage Exceptions
// ============================================================================

/**
 * Storage-related errors (8xxx range).
 */
sealed class StorageException(
    code: SmartAccountErrorCode,
    message: String,
    cause: Throwable? = null
) : SmartAccountException(code, message, cause) {

    /**
     * Storage read operation failed error.
     */
    class ReadFailed(message: String, cause: Throwable? = null) :
        StorageException(SmartAccountErrorCode.STORAGE_READ_FAILED, message, cause)

    /**
     * Storage write operation failed error.
     */
    class WriteFailed(message: String, cause: Throwable? = null) :
        StorageException(SmartAccountErrorCode.STORAGE_WRITE_FAILED, message, cause)

    companion object {
        /**
         * Creates a storage read failed error.
         *
         * @param key The storage key that failed to read
         * @param cause Optional underlying cause
         * @return ReadFailed exception instance
         */
        fun readFailed(key: String, cause: Throwable? = null) =
            ReadFailed("Storage read failed for key: $key", cause)

        /**
         * Creates a storage write failed error.
         *
         * @param key The storage key that failed to write
         * @param cause Optional underlying cause
         * @return WriteFailed exception instance
         */
        fun writeFailed(key: String, cause: Throwable? = null) =
            WriteFailed("Storage write failed for key: $key", cause)
    }
}

// ============================================================================
// Session Exceptions
// ============================================================================

/**
 * Session-related errors (9xxx range).
 */
sealed class SessionException(
    code: SmartAccountErrorCode,
    message: String,
    cause: Throwable? = null
) : SmartAccountException(code, message, cause) {

    /**
     * Session expired error.
     */
    class Expired(message: String = "Session has expired", cause: Throwable? = null) :
        SessionException(SmartAccountErrorCode.SESSION_EXPIRED, message, cause)

    /**
     * Invalid session error.
     */
    class Invalid(message: String, cause: Throwable? = null) :
        SessionException(SmartAccountErrorCode.SESSION_INVALID, message, cause)

    companion object {
        /**
         * Creates a session expired error.
         *
         * @param sessionId Optional session identifier that expired
         * @param cause Optional underlying cause
         * @return Expired exception instance
         */
        fun expired(sessionId: String? = null, cause: Throwable? = null) =
            Expired(sessionId?.let { "Session expired: $it" } ?: "Session has expired", cause)

        /**
         * Creates an invalid session error.
         *
         * @param reason Description of why the session is invalid
         * @param cause Optional underlying cause
         * @return Invalid exception instance
         */
        fun invalid(reason: String, cause: Throwable? = null) =
            Invalid("Invalid session: $reason", cause)
    }
}

/**
 * Indexer-related errors (10xxx range).
 */
sealed class IndexerException(
    code: SmartAccountErrorCode,
    message: String,
    cause: Throwable? = null
) : SmartAccountException(code, message, cause) {

    /**
     * Indexer request failed (network error or non-success HTTP status).
     */
    class RequestFailed(message: String, cause: Throwable? = null) :
        IndexerException(SmartAccountErrorCode.INDEXER_REQUEST_FAILED, message, cause)

    /**
     * Indexer request timed out.
     */
    class Timeout(message: String, cause: Throwable? = null) :
        IndexerException(SmartAccountErrorCode.INDEXER_TIMEOUT, message, cause)

    companion object {
        /**
         * Creates a [RequestFailed] exception for indexer request failures.
         *
         * @param reason Description of why the request failed
         * @param cause Optional underlying exception
         * @return An [IndexerException.RequestFailed] instance
         */
        fun requestFailed(reason: String, cause: Throwable? = null) =
            RequestFailed("Indexer request failed: $reason", cause)

        /**
         * Creates a [Timeout] exception for indexer request timeouts.
         *
         * @param url The URL that timed out
         * @param cause Optional underlying exception
         * @return An [IndexerException.Timeout] instance
         */
        fun timeout(url: String, cause: Throwable? = null) =
            Timeout("Indexer request timed out: $url", cause)
    }
}

// ============================================================================
// Smart Account Constants
// ============================================================================

/**
 * Cryptographic and protocol-level constants for Smart Account operations.
 */
object SmartAccountConstants {
    /**
     * Size in bytes of an Ed25519 public key (RFC 8032).
     */
    const val ED25519_PUBLIC_KEY_SIZE = 32

    /**
     * Size in bytes of an Ed25519 secret key seed (RFC 8032). Equal to [ED25519_PUBLIC_KEY_SIZE]
     * for Ed25519 — kept as a distinct constant so validations remain semantically accurate.
     */
    const val ED25519_SECRET_KEY_SIZE = 32

    /**
     * Size in bytes of an uncompressed secp256r1 public key (1 prefix byte + 32 x-coordinate + 32 y-coordinate).
     */
    const val SECP256R1_PUBLIC_KEY_SIZE = 65

    /**
     * Uncompressed point prefix byte (0x04) as defined in SEC 1 for secp256r1 public keys.
     */
    const val UNCOMPRESSED_PUBKEY_PREFIX: Byte = 0x04

    /**
     * Ed25519 signature length in bytes (RFC 8032).
     */
    const val ED25519_SIGNATURE_SIZE = 64

    /**
     * Length of a Stellar S-strkey secret seed (Base32 encoding of version + 32-byte seed + 2-byte checksum).
     */
    const val ED25519_SECRET_KEY_STRKEY_LENGTH = 56

    /**
     * Number of characters of an address used in error-message excerpts.
     */
    const val ADDRESS_PREFIX_LENGTH = 8
}

// ============================================================================
// Contract Error Codes
// ============================================================================

/**
 * Contract-level error codes from the OZ smart account contract.
 *
 * A reference catalog for consumers. The SDK does not parse or map these codes —
 * a failed transaction surfaces the raw `Error(Contract, #NNNN)` message inside the
 * thrown exception, and the consumer extracts the code and matches it against these
 * constants.
 *
 * Error code range: 3xxx (credential errors, aligned with the contract's Error enum).
 */
object ContractErrorCodes {
    /**
     * Integer arithmetic overflow occurred in the contract.
     */
    const val MATH_OVERFLOW = 3012

    /**
     * The key_data field on a signer exceeds the maximum allowed size.
     */
    const val KEY_DATA_TOO_LARGE = 3013

    /**
     * The number of context rule IDs in the auth payload does not match the expected count.
     */
    const val CONTEXT_RULE_IDS_LENGTH_MISMATCH = 3014

    /**
     * A name field (e.g. context rule name) exceeds the maximum allowed length.
     */
    const val NAME_TOO_LONG = 3015

    /**
     * The signer is not authorized to sign the given context rule.
     */
    const val UNAUTHORIZED_SIGNER = 3016
}
