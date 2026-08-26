# iOS Platform Guide

This guide covers iOS-specific setup and usage for the Stellar KMP SDK.

## Table of Contents

- [Platform Overview](#platform-overview)
- [Installation](#installation)
- [Project Setup](#project-setup)
- [Swift Integration](#swift-integration)
- [SwiftUI Example](#swiftui-example)
- [Troubleshooting](#troubleshooting)

## Platform Overview

The iOS implementation supports:
- **iOS 14.0+**
- **iPadOS 14.0+**
- **Mac Catalyst** apps

Platform features:
- Zero-overhead interop with Swift
- Full async/await support in Swift
- Thread-safe operations

## Installation

### Compose Multiplatform iOS Setup

Add the SDK to your shared module:

```kotlin
// shared/build.gradle.kts
kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.soneso.stellar:stellar-sdk:1.12.0")
                implementation(compose.runtime)
                implementation(compose.material3)
            }
        }
    }
}
```

### Alternative: Direct Framework

```bash
# Build the framework
./gradlew :stellar-sdk:linkDebugFrameworkIosSimulatorArm64

# Or for device
./gradlew :stellar-sdk:linkDebugFrameworkIosArm64

# Build XCFramework for distribution
./build-xcframework.sh
```

Then add the framework to your Xcode project:
1. Drag the `.framework` or `.xcframework` to your project
2. Embed & Sign in your app target
3. Add libsodium via Swift Package Manager:
   - File → Add Package Dependencies
   - Search for: `https://github.com/jedisct1/swift-sodium`
   - Add the **Clibsodium** product to your target

## Project Setup

### Xcode Project Configuration

```xml
<!-- Info.plist -->
<key>NSAppTransportSecurity</key>
<dict>
    <!-- Only for development/testing -->
    <key>NSAllowsArbitraryLoads</key>
    <false/>
    <key>NSExceptionDomains</key>
    <dict>
        <key>horizon-testnet.stellar.org</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <false/>
            <key>NSIncludesSubdomains</key>
            <true/>
        </dict>
    </dict>
</dict>
```

### Build Settings

```text
// In Xcode Build Settings
ENABLE_BITCODE = NO
VALID_ARCHS = arm64
SWIFT_VERSION = 5.9
IPHONEOS_DEPLOYMENT_TARGET = 15.0
```

## Swift Integration

### Basic Usage

```swift
import StellarSDK

class StellarManager {

    func generateKeypair() async throws -> KeyPair {
        return try await KeyPair.companion.random()
    }

    func importFromSeed(_ seed: String) async throws -> KeyPair {
        return try await KeyPair.companion.fromSecretSeed(seed: seed)
    }

    func createPublicOnlyKeypair(_ accountId: String) -> KeyPair {
        return KeyPair.companion.fromAccountId(accountId: accountId)
    }
}
```

## SwiftUI Example

### Wallet View with Balance Checking

```swift
import SwiftUI
import StellarSDK

@MainActor
class WalletViewModel: ObservableObject {
    @Published var keypair: KeyPair?
    @Published var balance: String = "0"
    @Published var errorMessage = ""

    private let horizonServer = HorizonServer(
        serverUrl: "https://horizon-testnet.stellar.org"
    )

    func generateKeypair() async {
        do {
            keypair = try await KeyPair.companion.random()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func checkBalance() async {
        guard let keypair = keypair else { return }

        do {
            let account = try await horizonServer.loadAccount(
                accountId: keypair.getAccountId()
            )

            if let xlmBalance = account.balances.first(where: {
                $0.assetType == "native"
            }) {
                balance = xlmBalance.balance
            }
        } catch {
            errorMessage = "Account not found. Fund it first!"
            balance = "0"
        }
    }

    deinit {
        horizonServer.close()
    }
}

struct WalletView: View {
    @StateObject private var viewModel = WalletViewModel()

    var body: some View {
        VStack(spacing: 20) {
            if let keypair = viewModel.keypair {
                Text(keypair.getAccountId())
                    .font(.system(.caption, design: .monospaced))
                    .textSelection(.enabled)

                Text("\(viewModel.balance) XLM")
                    .font(.title2)

                Button("Check Balance") {
                    Task { await viewModel.checkBalance() }
                }
            } else {
                Button("Generate Keypair") {
                    Task { await viewModel.generateKeypair() }
                }
            }

            if !viewModel.errorMessage.isEmpty {
                Text(viewModel.errorMessage)
                    .foregroundColor(.red)
            }
        }
        .padding()
    }
}
```

## Troubleshooting

### Common Issues

#### Framework Not Found

```bash
# Ensure framework is built for correct architecture
lipo -info stellar_sdk.framework/stellar_sdk
# Should show: arm64 (for device) or x86_64 (for simulator)

# Build for specific architecture
./gradlew :stellar-sdk:linkDebugFrameworkIosArm64
```

#### libsodium Symbols Not Found

```swift
// Ensure Clibsodium is added via SPM
// In Package.swift or Xcode:
dependencies: [
    .product(name: "Clibsodium", package: "swift-sodium")
]
```

#### Async/Await Support

The SDK supports async/await on iOS 14.0+ with Swift 5.5+:

```swift
Task {
    do {
        let keypair = try await KeyPair.companion.random()
        // Use keypair
    } catch {
        // Handle error
    }
}
```

### Demo Application

For complete examples, see the iOS demo app:
- Location: `/demo/iosApp`
- Features: Key generation, account funding, balance checking, transactions
- Architecture: SwiftUI with Compose Multiplatform shared logic

---

**Navigation**: [← JavaScript Platform](javascript.md) | [macOS Platform →](macos.md)
