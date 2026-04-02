//
//  StellarSmartDemoApp.swift
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

// Native macOS app using SwiftUI that mirrors the Compose Multiplatform smart account demo.
//
// Compose does not support native macOS window management, so the macOS app uses SwiftUI
// with the Kotlin shared module providing all business logic via MacOSBridge.
//
// Screens:
// - MainScreen: Dashboard with wallet status, activity log, navigation
// - WalletCreationScreen: Create smart account with passkey
// - WalletConnectionScreen: Connect via session restore, indexer, or address
// - TransferScreen: Send XLM or DEMO tokens (single or multi-signer)
// - ContextRulesScreen: View and manage on-chain authorization rules
// - ContextRuleBuilderScreen: Create or edit context rules
// - KnownSignersScreen: View all unique signers across rules

@main
struct StellarSmartDemoApp: App {
    @StateObject private var bridgeWrapper = MacOSBridgeWrapper()
    @StateObject private var appState = AppState()
    @StateObject private var toastManager = ToastManager()

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                MainScreen(toastManager: toastManager)
            }
            .environmentObject(bridgeWrapper)
            .environmentObject(appState)
            .accentColor(Material3Colors.primary)
            .preferredColorScheme(.light)
            .toast(toastManager)
        }
        .windowResizability(.contentSize)
    }
}

// WebAuthn / Passkey Requirements for macOS
//
// 1. Associated Domains capability configured in project.yml
// 2. Entitlements: webcredentials:soneso.com?mode=developer
// 3. apple-app-site-association hosted at https://soneso.com/.well-known/
// 4. macOS 13.0+ (Ventura) with Touch ID hardware
// 5. The app uses AppleWebAuthnProvider (shared nativeMain with iOS)
