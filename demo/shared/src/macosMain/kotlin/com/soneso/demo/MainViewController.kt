package com.soneso.demo

import androidx.compose.runtime.Composable
import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
     */
    suspend fun generateKeypair(): KeyPairData {
        val keypair = KeyPair.random()
        return KeyPairData(
            accountId = keypair.getAccountId(),
            secretSeed = keypair.getSecretSeed()?.concatToString() ?: "",
            canSign = keypair.canSign(),
            cryptoLibrary = KeyPair.getCryptoLibraryName()
        )
    }

    /**
     * Sign data with a keypair.
     */
    suspend fun signData(secretSeed: String, data: ByteArray): ByteArray {
        val keypair = KeyPair.fromSecretSeed(secretSeed)
        return keypair.sign(data)
    }

    /**
     * Verify a signature.
     */
    suspend fun verifySignature(accountId: String, data: ByteArray, signature: ByteArray): Boolean {
        val keypair = KeyPair.fromAccountId(accountId)
        return keypair.verify(data, signature)
    }
}

/**
 * Data class to pass keypair information to Swift.
 * Swift cannot directly access KeyPair due to memory management differences.
 */
data class KeyPairData(
    val accountId: String,
    val secretSeed: String,
    val canSign: Boolean,
    val cryptoLibrary: String
)

/**
 * Provides access to the App composable for potential future Compose integration.
 * Currently not used, but available if Compose Multiplatform adds native macOS support.
 */
@Suppress("unused")
fun getAppComposable(): @Composable () -> Unit = {
    App()
}
