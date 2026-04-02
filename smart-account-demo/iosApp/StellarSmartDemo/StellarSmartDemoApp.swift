//
//  StellarSmartDemoApp.swift
//  Smart Account Demo
//
//  Created by Claude on 15.02.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

// MARK: - Passkey Configuration
//
// This app demonstrates Stellar Smart Accounts with passkey authentication.
// Passkeys (WebAuthn) require:
//
// 1. iOS 16.0+ deployment target (configured in project.yml)
// 2. Associated Domains entitlement (see project.yml for setup)
// 3. apple-app-site-association file hosted on your domain
//
// For development/testing:
// - Passkeys may not work on iOS Simulator (platform limitation)
// - Test on real devices for full passkey functionality
// - Use a test domain or skip domain validation during development
//
// The Smart Account Kit is initialized in PlatformInit.ios.kt with:
// - AppleWebAuthnProvider (Touch ID / Face ID integration)
// - UserDefaultsStorageAdapter (credential persistence)

@main
struct StellarSmartDemoApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
