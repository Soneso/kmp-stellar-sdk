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

/**
 * Sets platform-specific providers for iOS before the Compose UI loads.
 *
 * Creates and stores an [AppleWebAuthnProvider] and [UserDefaultsStorageAdapter]
 * in [DemoState]. The shared [MainScreen] LaunchedEffect picks these up and
 * initializes the SDK kit asynchronously.
 *
 * Runs synchronously so providers are available on first Compose composition.
 */
fun initSmartAccountKit() {
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
        ActivityLogState.info("iOS providers initialized")
    } catch (e: Exception) {
        ActivityLogState.error("Failed to initialize iOS providers: ${e.message}")
    }
}
