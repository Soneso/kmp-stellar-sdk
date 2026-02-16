//
//  PlatformInit.ios.kt
//  Smart Account Demo
//
//  Created by Claude on 15.02.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

package com.soneso.smartdemo

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.UserDefaultsStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Initializes the Smart Account Kit for iOS with Apple-specific providers.
 *
 * This function creates and configures an OZSmartAccountKit instance with:
 * - AppleWebAuthnProvider for passkey operations (Touch ID / Face ID)
 * - UserDefaultsStorageAdapter for credential persistence
 * - Testnet contract addresses from DemoConfig
 *
 * The WebAuthn provider requires:
 * - iOS 16.0+ (passkey support)
 * - Associated Domains entitlement configured in project.yml
 * - apple-app-site-association file hosted on rpId domain
 *
 * Note: For simulator testing, passkeys may not work due to platform limitations.
 * Test on real devices for full passkey functionality.
 *
 * This function is called automatically from MainViewController before the Compose UI loads.
 */
fun initSmartAccountKit() {
    MainScope().launch {
        try {
            ActivityLogState.info("Initializing Smart Account Kit for iOS...")

            // Create WebAuthn provider for passkey authentication
            // rpId must match the domain in Associated Domains entitlement
            val webauthnProvider = AppleWebAuthnProvider(
                rpId = DemoConfig.DEFAULT_RP_ID,
                rpName = DemoConfig.RP_NAME
            )

            // Create storage adapter using NSUserDefaults
            val storage = UserDefaultsStorageAdapter(
                suiteName = "com.soneso.stellar.smartdemo"
            )

            // Create configuration with the WebAuthn provider and storage
            val config = OZSmartAccountConfig(
                rpcUrl = DemoConfig.RPC_URL,
                networkPassphrase = DemoConfig.NETWORK_PASSPHRASE,
                accountWasmHash = DemoConfig.ACCOUNT_WASM_HASH,
                webauthnVerifierAddress = DemoConfig.WEBAUTHN_VERIFIER_ADDRESS,
                rpName = DemoConfig.RP_NAME,
                relayerUrl = DemoConfig.DEFAULT_RELAYER_URL,
                indexerUrl = DemoConfig.DEFAULT_INDEXER_URL,
                webauthnProvider = webauthnProvider,
                storage = storage
            )

            // Create the kit
            val kit = OZSmartAccountKit.create(config)

            DemoState.setKitInstance(kit)
            ActivityLogState.success("Smart Account Kit initialized successfully (iOS)")
            ActivityLogState.info("WebAuthn provider: AppleWebAuthnProvider")
            ActivityLogState.info("Storage: UserDefaultsStorageAdapter")
        } catch (e: Exception) {
            ActivityLogState.error("Failed to initialize Smart Account Kit: ${e.message}")
            e.printStackTrace()
        }
    }
}
