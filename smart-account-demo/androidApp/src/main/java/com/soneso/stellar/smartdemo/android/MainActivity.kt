package com.soneso.stellar.smartdemo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.soneso.smartdemo.App
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.platform.initAndroidClipboard
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.smartaccount.AndroidStorageAdapter
import com.soneso.stellar.sdk.smartaccount.AndroidWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initAndroidClipboard(this)

        // Initialize Smart Account Kit with Android-specific providers
        lifecycleScope.launch {
            try {
                // Create storage adapter using encrypted SharedPreferences
                val storage = AndroidStorageAdapter(applicationContext)

                // Create WebAuthn provider for passkey authentication
                // rpId must match the domain configured in assetlinks.json
                val webauthnProvider = AndroidWebAuthnProvider(
                    context = this@MainActivity,
                    rpId = DemoConfig.DEFAULT_RP_ID,
                    rpName = DemoConfig.RP_NAME
                )

                // Build config with Android providers (storage is part of config)
                val config = OZSmartAccountConfig(
                    rpcUrl = DemoConfig.RPC_URL,
                    networkPassphrase = DemoConfig.NETWORK_PASSPHRASE,
                    accountWasmHash = DemoState.accountWasmHash,
                    webauthnVerifierAddress = DemoState.webauthnVerifier,
                    rpName = DemoConfig.RP_NAME,
                    relayerUrl = DemoState.relayerUrl.takeIf { it.isNotBlank() },
                    indexerUrl = DemoState.indexerUrl.takeIf { it.isNotBlank() },
                    webauthnProvider = webauthnProvider,
                    storage = storage
                )

                val kit = OZSmartAccountKit.create(config)

                DemoState.setKitInstance(kit)
                ActivityLogState.info("Smart Account Kit initialized (Android)")
            } catch (e: Exception) {
                ActivityLogState.error("Failed to initialize kit: ${e.message}")
            }
        }

        setContent {
            App()
        }
    }
}
