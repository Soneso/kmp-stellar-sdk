//
//  StellarSmartDemoApp.swift
//  Smart Account Demo
//
//  Created by Claude on 15.02.26.
//  Copyright © 2026 Soneso. All rights reserved.
//

import SwiftUI
import shared

@main
struct StellarSmartDemoApp: App {
    // Create bridge to Kotlin SDK
    private let bridge = MacOSBridge()

    init() {
        // Initialize Smart Account Kit on app launch
        // This sets up the WebAuthn provider and storage adapter
        bridge.initializeKit()
    }

    var body: some Scene {
        WindowGroup {
            NavigationStack {
                VStack(spacing: 20) {
                    Text("Smart Account Kit")
                        .font(.largeTitle)
                        .fontWeight(.bold)

                    Text("Passkey-based Stellar smart accounts with policy-driven signing")
                        .font(.body)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)

                    Spacer().frame(height: 20)

                    // Display initialization status
                    VStack(alignment: .leading, spacing: 10) {
                        Label("Platform: macOS", systemImage: "laptopcomputer")
                        Label("WebAuthn: AppleWebAuthnProvider", systemImage: "touchid")
                        Label("Storage: UserDefaultsStorageAdapter", systemImage: "externaldrive")
                        Label("Network: Stellar Testnet", systemImage: "network")
                    }
                    .font(.callout)
                    .foregroundColor(.secondary)
                    .padding()
                    .background(Color(nsColor: .controlBackgroundColor))
                    .cornerRadius(8)

                    Spacer().frame(height: 20)

                    Text("Demo features coming soon.")
                        .font(.callout)
                        .foregroundColor(.secondary)

                    Text("The macOS app demonstrates SDK compatibility on macOS.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(nsColor: .windowBackgroundColor))
                .navigationTitle("Stellar Smart Account Demo")
            }
        }
        .windowResizability(.contentSize)
    }
}

// MARK: - WebAuthn / Passkey Requirements for macOS

/*
 To enable passkey functionality on macOS, you must configure Associated Domains:

 1. Add the Associated Domains capability in project.yml (already configured)
 2. Configure the domains in your app's entitlements:
    - Format: `webcredentials:demo.stellar.org`
    - Replace `demo.stellar.org` with your actual domain

 3. Host an apple-app-site-association file at:
    https://demo.stellar.org/.well-known/apple-app-site-association

    Example content:
    {
      "webcredentials": {
        "apps": [ "TEAMID.com.soneso.stellar.smartdemo.macos" ]
      }
    }

    Replace TEAMID with your Apple Developer Team ID

 4. Requirements:
    - macOS 13.0+ (Ventura or later)
    - Touch ID hardware (MacBook Pro with Touch Bar, or Magic Keyboard with Touch ID)
    - Valid code signing with proper entitlements

 5. Testing:
    - Passkeys work on real Macs with Touch ID
    - Simulator does not support Touch ID biometric prompts
    - For development, use a test device with Touch ID

 Note: The macOS app uses the same AppleWebAuthnProvider as iOS (shared in nativeMain).
 The provider handles ASAuthorizationController presentation automatically.
 */
