package com.soneso.smartdemo.flows

import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountConstants
import com.soneso.smartdemo.util.hexToByteArray

/**
 * Result of verifying a user-supplied secret against an expected signer identity.
 */
sealed class VerificationResult {
    /**
     * Verification succeeded.
     *
     * @property keypair The derived [KeyPair] from the verified secret.
     * @property publicKey 32-byte Ed25519 public key derived from the secret.
     * @property seedBytes The raw 32-byte seed extracted during verification, populated only
     *   for Ed25519 hex seeds (null for S-strkey delegated secrets).
     */
    data class Success(
        val keypair: KeyPair,
        val publicKey: ByteArray,
        val seedBytes: ByteArray? = null,
    ) : VerificationResult()

    /** Verification failed with a user-visible error message. */
    data class Failure(val errorMessage: String) : VerificationResult()
}

/**
 * Verifies that [secret] is a valid S-strkey whose derived account ID matches [expectedAccountId].
 *
 * Returns [VerificationResult.Success] with the derived [KeyPair] on match, or
 * [VerificationResult.Failure] with an actionable message on any format or identity mismatch.
 */
suspend fun verifyDelegatedSecret(
    secret: String,
    expectedAccountId: String,
): VerificationResult {
    val trimmed = secret.trim()
    if (!trimmed.startsWith("S") || trimmed.length != SmartAccountConstants.ED25519_SECRET_KEY_STRKEY_LENGTH) {
        return VerificationResult.Failure(
            "Invalid secret key. Must start with S and be " +
                "${SmartAccountConstants.ED25519_SECRET_KEY_STRKEY_LENGTH} characters."
        )
    }
    return try {
        val keypair = KeyPair.fromSecretSeed(trimmed)
        val derivedAddress = keypair.getAccountId()
        if (derivedAddress != expectedAccountId) {
            VerificationResult.Failure("Secret key does not match this address.")
        } else {
            VerificationResult.Success(keypair = keypair, publicKey = keypair.getPublicKey())
        }
    } catch (e: Throwable) {
        VerificationResult.Failure("Invalid secret key: ${e.message}")
    }
}

/**
 * Verifies that [hex] is a valid 64-character hex string encoding a 32-byte Ed25519 seed
 * whose derived public key matches [expectedPublicKey] byte-for-byte.
 *
 * Returns [VerificationResult.Success] with the derived [KeyPair] on match, or
 * [VerificationResult.Failure] with an actionable message on any format or key mismatch.
 */
suspend fun verifyEd25519Seed(
    hex: String,
    expectedPublicKey: ByteArray,
): VerificationResult {
    val trimmed = hex.trim()
    val expectedHexLength = SmartAccountConstants.ED25519_SECRET_KEY_SIZE * 2
    if (trimmed.length != expectedHexLength) {
        return VerificationResult.Failure(
            "Must be exactly $expectedHexLength hex characters " +
                "(${SmartAccountConstants.ED25519_SECRET_KEY_SIZE}-byte seed)."
        )
    }
    if (!trimmed.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' }) {
        return VerificationResult.Failure(
            "Contains invalid characters. Hex digits (0-9, a-f) only."
        )
    }
    return try {
        val seedBytes = hexToByteArray(trimmed)
        val keypair = KeyPair.fromSecretSeed(seedBytes)
        val derivedPublicKey = keypair.getPublicKey()
        if (!derivedPublicKey.contentEquals(expectedPublicKey)) {
            VerificationResult.Failure("Secret key does not match this signer's public key.")
        } else {
            VerificationResult.Success(keypair = keypair, publicKey = derivedPublicKey, seedBytes = seedBytes)
        }
    } catch (e: Throwable) {
        VerificationResult.Failure("Verification failed: ${e.message}")
    }
}
