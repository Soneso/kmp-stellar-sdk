package com.soneso.demo.stellar

import com.soneso.stellar.sdk.KeyPair

/**
 * Data class representing a generated Stellar keypair with public and secret components.
 *
 * @property accountId The account ID (public key) starting with 'G'
 * @property secretSeed The secret seed starting with 'S' - keep this secure!
 */
data class GeneratedKeyPair(
    val accountId: String,
    val secretSeed: String
)

/**
 * Result type for keypair generation operations.
 */
sealed class KeyPairGenerationResult {
    /**
     * Successful keypair generation.
     */
    data class Success(val keyPair: GeneratedKeyPair) : KeyPairGenerationResult()

    /**
     * Failed keypair generation with error details.
     */
    data class Error(val message: String, val exception: Throwable? = null) : KeyPairGenerationResult()
}

/**
 * Generates a new cryptographically secure Ed25519 keypair for Stellar network operations.
 *
 * This function creates a random keypair using the platform's secure random number generator:
 * - JVM: Uses BouncyCastle's Ed25519 implementation
 * - JavaScript: Uses libsodium-wrappers with Web Crypto API
 * - Native (iOS/macOS): Uses libsodium via C interop
 *
 * @return KeyPairGenerationResult.Success with the generated keypair, or
 *         KeyPairGenerationResult.Error if generation fails
 */
suspend fun generateRandomKeyPair(): KeyPairGenerationResult {
    return try {
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()
        val secretSeed = keyPair.getSecretSeed()?.concatToString()
            ?: return KeyPairGenerationResult.Error("Failed to extract secret seed")

        KeyPairGenerationResult.Success(
            GeneratedKeyPair(
                accountId = accountId,
                secretSeed = secretSeed
            )
        )
    } catch (e: Exception) {
        KeyPairGenerationResult.Error(
            message = "Failed to generate keypair: ${e.message}",
            exception = e
        )
    }
}
