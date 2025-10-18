package com.soneso.demo.stellar

import com.soneso.stellar.sdk.KeyPair

/**
 * Result type for keypair generation operations.
 */
sealed class KeyPairGenerationResult {
    /**
     * Successful keypair generation.
     */
    data class Success(val keyPair: KeyPair) : KeyPairGenerationResult()

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
        KeyPairGenerationResult.Success(keyPair)
    } catch (e: Exception) {
        KeyPairGenerationResult.Error(
            message = "Failed to generate keypair: ${e.message}",
            exception = e
        )
    }
}
