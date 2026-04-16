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
import com.soneso.smartdemo.wallet.ReownConnectorBridge
import com.soneso.smartdemo.wallet.ReownHandler
import com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.UserDefaultsStorageAdapter

/**
 * Sets platform-specific providers for iOS before the Compose UI loads.
 *
 * Creates and stores an [AppleWebAuthnProvider] and [UserDefaultsStorageAdapter]
 * in [DemoState]. The shared [MainScreen] LaunchedEffect picks these up and
 * initializes the SDK kit asynchronously.
 *
 * If [DemoConfig.REOWN_PROJECT_ID] is non-blank, a [ReownConnectorBridge] backed by the
 * provided [reownHandler] is installed as the wallet connector. The Swift app delegate
 * creates the [ReownHandler] (using the Reown WalletConnectSign SDK) and passes it here.
 *
 * Runs synchronously so providers are available on first Compose composition.
 *
 * @param reownHandler Swift-implemented Reown handler. Null if WalletConnect is not configured.
 */
fun initSmartAccountKit(reownHandler: ReownHandler? = null) {
    try {
        val webauthnProvider = AppleWebAuthnProvider(
            rpId = DemoConfig.DEFAULT_RP_ID,
            rpName = DemoConfig.RP_NAME
        )

        val storage = UserDefaultsStorageAdapter(
            suiteName = "com.soneso.stellar.smartdemo"
        )

        DemoState.setWebAuthnProvider(webauthnProvider)
        DemoState.setStorage(storage)

        if (reownHandler != null && DemoConfig.REOWN_PROJECT_ID.isNotBlank()) {
            val connector = ReownConnectorBridge(reownHandler)
            DemoState.setWalletConnector(connector)
            ActivityLogState.info("iOS providers initialized (wallet connector enabled)")
        } else {
            ActivityLogState.info("iOS providers initialized")
        }
    } catch (e: Exception) {
        ActivityLogState.error("Failed to initialize iOS providers: ${e.message}")
    }
}
