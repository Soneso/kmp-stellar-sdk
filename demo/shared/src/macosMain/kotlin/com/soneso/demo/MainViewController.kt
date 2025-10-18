package com.soneso.demo

import com.soneso.demo.stellar.KeyPairGenerationResult
import com.soneso.demo.stellar.generateRandomKeyPair
import com.soneso.stellar.sdk.KeyPair

/**
 * Bridge between Swift UI and Kotlin business logic for native macOS app.
 *
 * Note: Native macOS Compose Multiplatform does not have window management APIs
 * like iOS (ComposeUIViewController) or JVM Desktop (Window/application).
 *
 * For a native macOS app, we provide business logic functions that SwiftUI can call.
 * For a full Compose UI experience on macOS, use the JVM desktop target instead
 * (see demo/desktopApp/).
 *
 * This approach allows:
 * - Native macOS app with SwiftUI
 * - Shared business logic from Kotlin
 * - Access to the Stellar SDK
 */
class MacOSBridge {

    /**
     * Generate a random Stellar keypair asynchronously.
     * Call this from Swift using async/await.
     *
     * Uses the centralized KeyPairGeneration business logic to maintain consistency
     * across all platform UIs (Compose, SwiftUI, Web).
     *
     * @return KeyPair instance from the Stellar SDK
     * @throws Exception if keypair generation fails
     */
    suspend fun generateKeypair(): KeyPair {
        return when (val result = generateRandomKeyPair()) {
            is KeyPairGenerationResult.Success -> result.keyPair
            is KeyPairGenerationResult.Error -> {
                throw Exception(result.message, result.exception)
            }
        }
    }
}

