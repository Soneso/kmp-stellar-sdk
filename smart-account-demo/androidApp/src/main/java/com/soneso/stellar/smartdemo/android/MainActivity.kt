package com.soneso.stellar.smartdemo.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.soneso.smartdemo.App
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.platform.initAndroidClipboard
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.wallet.ReownConnector
import com.soneso.stellar.sdk.smartaccount.AndroidStorageAdapter
import com.soneso.stellar.sdk.smartaccount.AndroidWebAuthnProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initAndroidClipboard(this)

        // Register the Reown wallet connector so shared code can trigger wallet flows.
        // Skipped on emulators (WalletConnect requires Freighter Mobile on a real device)
        // and when REOWN_PROJECT_ID is blank. This heuristic covers the standard Android
        // emulator (ranchu/goldfish) and SDK-based images. Some third-party emulators
        // (e.g., Genymotion) may not be detected — wallet connection will timeout on those.
        val isEmulator = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" ||
            Build.PRODUCT.contains("sdk") || Build.FINGERPRINT.contains("generic")
        if (!isEmulator && DemoConfig.REOWN_PROJECT_ID.isNotBlank()) {
            DemoState.setWalletConnector(ReownConnector(applicationContext))
        }

        // Initialize Smart Account Kit with Android-specific providers.
        lifecycleScope.launch {
            try {
                // Create storage adapter using encrypted SharedPreferences.
                val storage = AndroidStorageAdapter(applicationContext)

                // Create WebAuthn provider for passkey authentication.
                // rpId must match the domain configured in assetlinks.json.
                val webauthnProvider = AndroidWebAuthnProvider(
                    context = this@MainActivity,
                    rpId = DemoConfig.DEFAULT_RP_ID,
                    rpName = DemoConfig.RP_NAME
                )

                // Store platform providers in DemoState so shared code can use them.
                DemoState.setWebAuthnProvider(webauthnProvider)
                DemoState.setStorage(storage)

                ActivityLogState.info("Smart Account Kit initialized (Android)")
            } catch (e: Exception) {
                ActivityLogState.error("Failed to initialize kit: ${e.message}")
            }
        }

        setContent {
            App()
        }
    }

    /**
     * Called when the activity receives a new intent after creation (e.g. the deep link
     * redirect URI `stellar-smartdemo://request` sent by Freighter Mobile on return).
     *
     * The Reown SDK handles the relay connection internally via its WebSocket; this callback
     * exists to satisfy the `singleTop` launch mode contract and ensure the activity is
     * brought to the foreground correctly after the wallet redirects back.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
