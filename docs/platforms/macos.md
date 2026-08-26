# macOS Platform Guide

This guide covers macOS-specific setup and usage for the Stellar KMP SDK.

## Table of Contents

- [Platform Overview](#platform-overview)
- [Installation](#installation)
- [Project Setup](#project-setup)
- [Basic Usage](#basic-usage)
- [Troubleshooting](#troubleshooting)

## Platform Overview

The macOS implementation supports:
- **macOS 11.0+** (Big Sur and later)
- **Mac Catalyst** apps from iOS
- **Command-line tools**
- Both **Apple Silicon (M1/M2/M3)** and **Intel** Macs

## Installation

### JVM/Compose Desktop Setup

Use Maven artifact - no additional setup required:

```kotlin
// build.gradle.kts
kotlin {
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation("com.soneso.stellar:stellar-sdk:1.12.0")
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
```

JVM apps (including Compose Desktop) require no native dependencies.

## Project Setup

### Xcode Configuration

```xml
<!-- Info.plist -->
<key>LSMinimumSystemVersion</key>
<string>12.0</string>

<!-- Network access for sandboxed apps -->
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <false/>
</dict>

<!-- For Menu Bar apps -->
<key>LSUIElement</key>
<true/>
```

### Entitlements

```xml
<!-- YourApp.entitlements -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- Network access -->
    <key>com.apple.security.network.client</key>
    <true/>

    <!-- Keychain access -->
    <key>com.apple.security.keychain-access-groups</key>
    <array>
        <string>$(AppIdentifierPrefix)com.yourcompany.stellar</string>
    </array>

    <!-- For non-sandboxed apps (development) -->
    <key>com.apple.security.app-sandbox</key>
    <false/>
</dict>
</plist>
```

## Basic Usage

The following examples show Kotlin/Swift interop patterns that work with the JVM/Compose Desktop approach.

### Generating KeyPairs

```swift
import StellarSDK

Task {
    // Generate random keypair
    let keypair = try await KeyPair.companion.random()
    print("Account ID: \(keypair.getAccountId())")
    print("Secret Seed: \(keypair.getSecretSeed()?.toString() ?? "")")

    // Or import from secret seed
    let imported = try await KeyPair.companion.fromSecretSeed(seed: "SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
}
```

### Building and Submitting Transactions

```swift
import StellarSDK

Task {
    let keypair = try await KeyPair.companion.random()
    let server = HorizonServer(serverUrl: "https://horizon-testnet.stellar.org")

    // Fund account on testnet
    try await FriendBot.companion.fundAccount(
        accountId: keypair.getAccountId(),
        network: Network.testnet
    )

    // Load source account
    let sourceAccount = try await server.loadAccount(
        accountId: keypair.getAccountId()
    )

    // Build transaction
    let transaction = TransactionBuilder(
        sourceAccount: sourceAccount,
        network: Network.testnet
    )
    .addOperation(
        PaymentOperation(
            destination: "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
            amount: "10.00",
            asset: AssetTypeNative()
        )
    )
    .setBaseFee(100)
    .setTimeout(180)
    .build()

    // Sign transaction
    try await transaction.sign(signer: keypair)

    // Submit transaction
    let response = try await server.submitTransaction(transaction: transaction)

    if response.successful {
        print("Transaction hash: \(response.hash ?? "")")
    }
}
```

### Working with Accounts

```swift
import StellarSDK

Task {
    let server = HorizonServer(serverUrl: "https://horizon-testnet.stellar.org")

    // Load account details
    let account = try await server.loadAccount(accountId: "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")

    // Get XLM balance
    if let xlmBalance = account.balances.first(where: { $0.assetType == "native" }) {
        print("XLM Balance: \(xlmBalance.balance)")
    }

    // Get sequence number
    print("Sequence: \(account.sequence)")
}
```

### SwiftUI Integration

```swift
import SwiftUI
import StellarSDK

struct KeyPairGeneratorView: View {
    @State private var accountId = ""
    @State private var isGenerating = false

    var body: some View {
        VStack {
            Text("Account: \(accountId)")

            Button("Generate") {
                Task {
                    isGenerating = true
                    defer { isGenerating = false }

                    let keypair = try await KeyPair.companion.random()
                    accountId = keypair.getAccountId()
                }
            }
            .disabled(isGenerating)
        }
    }
}
```

## Troubleshooting

### Framework Architecture Issues

```bash
# Check framework architectures
lipo -info stellar_sdk.framework/stellar_sdk

# Build for specific architecture
# Apple Silicon
./gradlew :stellar-sdk:linkDebugFrameworkMacosArm64

# Intel
./gradlew :stellar-sdk:linkDebugFrameworkMacosX64

# Universal XCFramework
./build-xcframework.sh
```

---

**Navigation**: [← iOS Platform](ios.md) | [Demo App Guide →](../demo-app.md)
