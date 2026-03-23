//
//  MacOSBridge.kt
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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Bridge between native macOS SwiftUI and Kotlin Smart Account Kit.
 *
 * The macOS app uses native SwiftUI (not Compose Multiplatform) due to limitations
 * in Compose for macOS. This bridge provides SDK access from Swift, allowing the
 * macOS app to demonstrate Smart Account Kit functionality.
 *
 * The bridge initializes the Smart Account Kit with:
 * - AppleWebAuthnProvider for passkey operations (Touch ID)
 * - UserDefaultsStorageAdapter for credential persistence
 * - Testnet contract addresses from DemoConfig
 *
 * WebAuthn on macOS requires:
 * - macOS 13.0+ (Ventura, passkey support)
 * - Associated Domains entitlement configured in project.yml
 * - apple-app-site-association file hosted on rpId domain
 *
 * Note: The macOS app is primarily for showcasing that the SDK works on macOS.
 * Full UI functionality is demonstrated in other platform targets.
 */
class MacOSBridge {

    /**
     * Returns the demo app version.
     */
    fun getVersion(): String = "0.1.0"

    /**
     * Initializes the Smart Account Kit with macOS-specific providers.
     *
     * This method creates and configures an OZSmartAccountKit instance using:
     * - AppleWebAuthnProvider (same implementation as iOS, shared in nativeMain)
     * - UserDefaultsStorageAdapter (same implementation as iOS, shared in nativeMain)
     *
     * The WebAuthn provider triggers Touch ID prompts for biometric authentication.
     * Credentials are persisted in NSUserDefaults with suite name isolation.
     *
     * Call this method from SwiftUI's onAppear or init to ensure the kit is ready
     * before any user interactions.
     *
     * Example from Swift:
     * ```swift
     * let bridge = MacOSBridge()
     * bridge.initializeKit()
     * ```
     */
    fun initializeKit() {
        MainScope().launch {
            try {
                ActivityLogState.info("Initializing Smart Account Kit for macOS...")

                // Create WebAuthn provider for passkey authentication with Touch ID
                // rpId must match the domain in Associated Domains entitlement
                val webauthnProvider = AppleWebAuthnProvider(
                    rpId = DemoConfig.DEFAULT_RP_ID,
                    rpName = DemoConfig.RP_NAME
                )

                // Create storage adapter using NSUserDefaults with macOS suite name
                val storage = UserDefaultsStorageAdapter(
                    suiteName = "com.soneso.stellar.smartdemo.macos"
                )

                // Store platform providers in DemoState so shared code can use them
                DemoState.webauthnProvider = webauthnProvider
                DemoState.storage = storage
                ActivityLogState.info("Smart Account Kit initialized (macOS)")
                ActivityLogState.info("WebAuthn provider: AppleWebAuthnProvider")
                ActivityLogState.info("Storage: UserDefaultsStorageAdapter")
            } catch (e: Exception) {
                ActivityLogState.error("Failed to initialize Smart Account Kit: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
