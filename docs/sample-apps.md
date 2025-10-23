# Demo Applications Guide

This guide explores the demo applications included with the Stellar KMP SDK, showcasing real-world SDK usage and best practices for Kotlin Multiplatform development.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Demo Features](#demo-features)
- [Code Examples](#code-examples)
- [Running the Demos](#running-the-demos)
- [Best Practices Demonstrated](#best-practices-demonstrated)
- [Extending the Demos](#extending-the-demos)

## Overview

The `demo` directory contains comprehensive Kotlin Multiplatform applications demonstrating:

- **Complete SDK feature coverage** across 10 comprehensive use cases
- **Compose Multiplatform UI** with 95% code sharing
- **Real Stellar network integration** with testnet
- **Production-ready patterns** for async operations and error handling
- **Platform-specific optimizations** where needed

### Demo App vs Sample Code

Unlike simple samples, these are full-featured applications that:
- Connect to real Stellar networks (testnet)
- Handle edge cases and errors comprehensively
- Demonstrate production patterns (not simplified examples)
- Use SDK features directly (not reimplementations)

### Code Sharing Efficiency

```
Traditional Development:
- Android: 2000 lines (logic + UI)
- iOS: 2000 lines (logic + UI)
- Desktop: 2000 lines (logic + UI)
- Web: 2000 lines (logic + UI)
- Total: 8000 lines with duplicated logic

Compose Multiplatform:
- Shared UI + Logic: 1500 lines
- Android entry: 20 lines
- iOS entry: 28 lines
- Desktop entry: 15 lines
- Web entry: 12 lines
- Total: 1575 lines, ~95% code reuse
```

## Architecture

The demo app uses **Compose Multiplatform** for maximum code sharing:

```
┌─────────────────────────────────────────────────────────────┐
│         Shared Module (demo/shared)                         │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Common UI (Compose Multiplatform)                    │  │
│  │  • All screens and navigation                         │  │
│  │  • Material 3 design system                           │  │
│  │  • Stellar SDK integration                            │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  Platform-specific: Entry points and platform APIs only     │
└─────────────────────────────────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┬──────────────┬──────────┐
        ▼                   ▼                   ▼              ▼          ▼
   ┌─────────┐        ┌──────────┐       ┌──────────┐   ┌──────────┐  ┌─────┐
   │ Android │        │ Desktop  │       │   iOS    │   │  macOS   │  │ Web │
   │ Compose │        │ Compose  │       │ Compose  │   │ SwiftUI  │  │  JS │
   └─────────┘        └──────────┘       └──────────┘   └──────────┘  └─────┘
```

### Project Structure

```
demo/
├── shared/                          # Shared Compose Multiplatform module
│   ├── src/
│   │   ├── commonMain/kotlin/       # Shared code (UI + business logic)
│   │   │   ├── ui/screens/          # All 10 demo screens
│   │   │   │   ├── MainScreen.kt
│   │   │   │   ├── KeyGenerationScreen.kt
│   │   │   │   ├── FundAccountScreen.kt
│   │   │   │   ├── AccountDetailsScreen.kt
│   │   │   │   ├── TrustAssetScreen.kt
│   │   │   │   ├── SendPaymentScreen.kt
│   │   │   │   ├── FetchTransactionScreen.kt
│   │   │   │   ├── ContractDetailsScreen.kt
│   │   │   │   ├── DeployContractScreen.kt
│   │   │   │   ├── InvokeHelloWorldContractScreen.kt
│   │   │   │   └── InvokeAuthContractScreen.kt
│   │   │   ├── stellar/             # Stellar SDK integration
│   │   │   │   ├── KeyPairGeneration.kt
│   │   │   │   ├── AccountFunding.kt
│   │   │   │   ├── AccountDetails.kt
│   │   │   │   ├── TrustAsset.kt
│   │   │   │   ├── SendPayment.kt
│   │   │   │   ├── FetchTransaction.kt
│   │   │   │   ├── ContractDetails.kt
│   │   │   │   ├── DeployContract.kt
│   │   │   │   ├── InvokeHelloWorldContract.kt
│   │   │   │   └── InvokeAuthContract.kt
│   │   │   └── App.kt               # Main app entry
│   │   ├── androidMain/             # Android-specific (clipboard)
│   │   ├── desktopMain/             # Desktop-specific (clipboard)
│   │   ├── iosMain/                 # iOS-specific (UIViewController)
│   │   ├── macosMain/               # macOS-specific (clipboard)
│   │   ├── jsMain/                  # JS-specific (clipboard)
│   │   └── wasmJsMain/              # WASM-specific (clipboard)
│   └── build.gradle.kts
├── androidApp/                      # Android entry point (20 lines)
│   ├── src/main/java/.../MainActivity.kt
│   └── build.gradle.kts
├── iosApp/                          # iOS entry point (SwiftUI wrapper, 28 lines)
│   ├── StellarDemo/StellarDemoApp.swift
│   └── project.yml
├── macosApp/                        # macOS native (SwiftUI, 17 files)
│   ├── StellarDemo/                 # Alternative to Desktop
│   │   ├── Views/                   # 7 SwiftUI views
│   │   ├── Components/              # 6 reusable components
│   │   └── Utilities/               # 3 utility files
│   └── project.yml
├── desktopApp/                      # Desktop JVM entry point (15 lines)
│   ├── src/jvmMain/kotlin/.../Main.kt
│   └── build.gradle.kts
└── webApp/                          # Web JS entry point (12 lines)
    ├── src/jsMain/kotlin/Main.kt
    ├── src/jsMain/resources/index.html
    └── build.gradle.kts
```

### Dependency Flow

```
Stellar KMP SDK
    ↓
demo/shared (Compose + Business Logic)
    ↓
    ├─→ androidApp (Compose entry)
    ├─→ desktopApp (Compose entry)
    ├─→ iosApp (Compose via UIViewController)
    ├─→ macosApp (SwiftUI - native alternative)
    └─→ webApp (Compose via Canvas)
```

## Demo Features

The demo app showcases 10 comprehensive SDK features:

### 1. Key Generation
- Generate random Ed25519 keypairs
- Display account ID (G...) and secret seed (S...)
- Copy keys to clipboard
- Sign and verify test data
- **SDK Features**: `KeyPair.random()`, `sign()`, `verify()`

### 2. Fund Testnet Account
- Get free test XLM from Friendbot
- Fund accounts on Stellar testnet
- Real-time funding status
- Error handling for already-funded accounts
- **SDK Features**: `FriendBot.fundTestnetAccount()`

### 3. Fetch Account Details
- Retrieve account information from Horizon
- Display balances, sequence number, and flags
- Show all account balances (native and issued assets)
- Real-time account data fetching
- **SDK Features**: `HorizonServer.accounts()`, account data parsing

### 4. Trust Asset
- Establish trustlines to hold non-native assets
- Support for custom asset issuers
- Transaction building and signing
- Submit transactions to testnet
- **SDK Features**: `ChangeTrustOperation`, transaction building

### 5. Send Payment
- Transfer XLM or issued assets between accounts
- Support for both native (XLM) and custom assets
- Amount validation and transaction signing
- Real-time transaction submission
- **SDK Features**: `PaymentOperation`, transaction submission

### 6. Fetch Transaction Details
- Fetch and view transaction details from Horizon or Soroban RPC
- Display operations, events, and smart contract data
- Expandable operations and events with copy functionality
- Human-readable SCVal formatting for contract data
- Supports both Horizon and RPC APIs
- **SDK Features**: `HorizonServer.transactions()`, `SorobanServer.getTransaction()`

### 7. Fetch Smart Contract Details
- Parse WASM contracts to view metadata
- Display contract specification (functions, types)
- View contract code hash and metadata
- Soroban smart contract integration
- **SDK Features**: Contract WASM parsing, Soroban RPC

### 8. Deploy Smart Contract
- Upload and deploy WASM contracts to testnet
- One-step deployment: `ContractClient.deploy()` with constructor args
- Two-step deployment: `install()` + `deployFromWasmId()` for WASM reuse
- Platform-specific WASM file loading (8 platforms)
- Support for 5 demo contracts
- **SDK Features**: `ContractClient.deploy()`, `install()`, `deployFromWasmId()`

### 9. Invoke Hello World Contract
- Invoke deployed "Hello World" contract
- Map-based argument conversion with automatic type handling
- Beginner-friendly contract invocation API
- **SDK Features**: `ContractClient.invoke()`, `funcArgsToXdrSCValues()`, `funcResToNative()`

### 10. Invoke Auth Contract
- Dynamic authorization handling for Soroban contracts
- Same-invoker vs different-invoker scenario detection
- Conditional authorization signing
- Production-ready authorization patterns
- **SDK Features**: `ContractClient.invokeWithXdr()`, `needsNonInvokerSigningBy()`, `signAuthEntries()`, `funcResToNative()`

## Code Examples

The demo app source code is located in the `demo/` directory. Key areas to explore:

### UI Screens (`demo/shared/src/commonMain/kotlin/com/soneso/demo/ui/screens/`)
- `MainScreen.kt` - Navigation and feature list
- `KeyGenerationScreen.kt` - Key generation UI
- `FundAccountScreen.kt` - Friendbot integration
- `AccountDetailsScreen.kt` - Horizon account queries
- `TrustAssetScreen.kt` - Trustline management
- `SendPaymentScreen.kt` - Payment transactions
- `FetchTransactionScreen.kt` - Transaction details viewer
- `ContractDetailsScreen.kt` - Soroban contract viewing
- `DeployContractScreen.kt` - Contract deployment
- `InvokeHelloWorldContractScreen.kt` - Hello World contract invocation
- `InvokeAuthContractScreen.kt` - Authorization demo

### Business Logic (`demo/shared/src/commonMain/kotlin/com/soneso/demo/stellar/`)
- `KeyPairGeneration.kt` - Keypair operations
- `AccountFunding.kt` - Friendbot integration
- `AccountDetails.kt` - Horizon queries
- `TrustAsset.kt` - Trustline operations
- `SendPayment.kt` - Payment operations
- `FetchTransaction.kt` - Transaction fetching
- `ContractDetails.kt` - Contract parsing
- `DeployContract.kt` - Contract deployment
- `InvokeHelloWorldContract.kt` - Hello World invocation
- `InvokeAuthContract.kt` - Authorization handling

### Platform Entry Points
- `androidApp/src/main/java/.../MainActivity.kt` (20 lines)
- `iosApp/StellarDemo/StellarDemoApp.swift` (28 lines)
- `desktopApp/src/jvmMain/kotlin/.../Main.kt` (15 lines)
- `webApp/src/jsMain/kotlin/Main.kt` (12 lines)
- `macosApp/` - Native SwiftUI implementation (17 files)

## Running the Demos

### Android

```bash
# Build and install
./gradlew :demo:androidApp:installDebug

# Or open in Android Studio
# File -> Open -> demo/androidApp
```

**Requirements**:
- Android Studio Arctic Fox or newer
- Android SDK API 24+ (Android 7.0)

### iOS

```bash
# Build the Kotlin framework
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64

# Generate Xcode project and open
cd demo/iosApp
xcodegen generate
open StellarDemo.xcodeproj
```

**In Xcode**:
1. Select "StellarDemo" scheme
2. Choose iOS Simulator (iPhone 15 Pro recommended)
3. Click Run (⌘R)

**Requirements**:
- macOS
- Xcode 15.0+
- xcodegen (`brew install xcodegen`)
- libsodium via Swift Package Manager (Clibsodium)

### Desktop (JVM)

**Recommended for macOS users wanting Compose UI**

```bash
# Run the desktop app
./gradlew :demo:desktopApp:run

# Create distributable package
./gradlew :demo:desktopApp:packageDmg        # macOS
./gradlew :demo:desktopApp:packageMsi        # Windows
./gradlew :demo:desktopApp:packageDeb        # Linux
```

**Requirements**:
- JDK 11 or higher
- Works on macOS, Windows, and Linux

### macOS Native

```bash
# Install libsodium (required)
brew install libsodium

# Build the Kotlin framework
./gradlew :demo:shared:linkDebugFrameworkMacosArm64

# Generate Xcode project and open
cd demo/macosApp
xcodegen generate
open StellarDemo.xcodeproj
```

**In Xcode**:
1. Select "StellarDemo" scheme
2. Choose "My Mac" as destination
3. Click Run (⌘R)

**Note**: The macOS app uses native SwiftUI (not Compose). See `demo/macosApp/README.md` for details.

### Web (JavaScript)

> **Note**: Migrated to Vite for development server (October 23, 2025) for improved hot reload performance.

```bash
# Development server with Vite (hot reload)
./gradlew :demo:webApp:viteDev
# Opens at http://localhost:8081

# Production build (webpack bundling)
./gradlew :demo:webApp:productionDist
# Output: demo/webApp/dist/

# Preview production build
./gradlew :demo:webApp:vitePreview
# Opens at http://localhost:8082
```

**Production Deployment**:
```bash
# Build production bundle
./gradlew :demo:webApp:productionDist

# Output directory contains:
# - app-kotlin-stdlib.js (~18 MB, 2.4 MB gzipped)
# - app-vendors.js (~1 MB, 325 KB gzipped)
# - app.js (~8.5 KB, 2.4 KB gzipped)
# - skiko.wasm (8 MB)
# Total: 28 MB unminified (2.7 MB JS gzipped + 8 MB WASM)

# Deploy to any static hosting (Netlify, Vercel, GitHub Pages, etc.)
```

**Requirements**:
- Modern browser (Chrome 90+, Firefox 88+, Safari 15.4+, Edge 90+)
- Node.js (for development server, optional)

## Best Practices Demonstrated

### 1. Shared Business Logic with Compose UI

All UI and business logic are in the `shared` module using Compose Multiplatform:
- Platform-agnostic code
- Material 3 design system
- Testable independently
- Single source of truth

### 2. Direct SDK Usage

```kotlin
// Demo code uses SDK directly (no wrappers)
suspend fun generateKeypair(): KeyPairResult {
    val keypair = KeyPair.random()  // Direct SDK call
    return KeyPairResult(
        accountId = keypair.getAccountId(),
        secretSeed = keypair.getSecretSeed()?.concatToString()
    )
}
```

### 3. Proper Error Handling

```kotlin
// Comprehensive error handling
suspend fun fundAccount(accountId: String): Result<String> = withContext(Dispatchers.IO) {
    try {
        val success = FriendBot.fundAccount(accountId, Network.TESTNET)
        if (success) {
            Result.success("Account funded successfully")
        } else {
            Result.failure(Exception("Funding failed"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### 4. Async Operations with Coroutines

```kotlin
// UI code with proper coroutine scopes
@Composable
fun KeyGenerationScreen() {
    val scope = rememberCoroutineScope()
    var keypair by remember { mutableStateOf<KeyPairResult?>(null) }

    Button(onClick = {
        scope.launch {
            keypair = generateKeypair()
        }
    }) {
        Text("Generate KeyPair")
    }
}
```

### 5. Real Network Integration

- All operations connect to Stellar testnet
- Real transaction submission
- Actual Horizon and Soroban RPC calls
- Production-ready patterns

### 6. Cross-Platform Architecture

- Minimal platform-specific code (clipboard access only)
- Maximum code reuse (95%)
- Platform-native entry points
- Consistent behavior across platforms

## Extending the Demos

### Adding New Features

1. **Add Business Logic** in `demo/shared/src/commonMain/kotlin/com/soneso/demo/stellar/`:
```kotlin
// Example: NewFeature.kt
suspend fun performNewFeature(param: String): Result<Data> {
    // Use Stellar SDK here
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    // ... implementation
}
```

2. **Create UI Screen** in `demo/shared/src/commonMain/kotlin/com/soneso/demo/ui/screens/`:
```kotlin
// Example: NewFeatureScreen.kt
class NewFeatureScreen : Screen {
    @Composable
    override fun Content() {
        // Compose UI implementation
    }
}
```

3. **Add to Navigation** in `MainScreen.kt`:
```kotlin
DemoTopic(
    title = "New Feature",
    description = "Description of the feature",
    icon = Icons.Default.Star,
    screen = NewFeatureScreen()
)
```

4. **Test on All Platforms**:
```bash
./gradlew :demo:androidApp:installDebug
./gradlew :demo:desktopApp:run
./gradlew :demo:webApp:jsBrowserDevelopmentRun
# iOS/macOS: Build framework and run in Xcode
```

### Creating Your Own Demo

1. **Copy Structure**:
```bash
cp -r demo myApp
```

2. **Update Gradle**:
```kotlin
// settings.gradle.kts
include(":myApp:shared")
include(":myApp:androidApp")
// ...
```

3. **Modify Shared Logic**:
- Add your business logic
- Keep it platform-agnostic
- Use SDK features directly

4. **Create Platform UIs**:
- Use Compose Multiplatform for maximum sharing
- Or create platform-specific UIs like macOS app

## Resources

- **Demo App Source**: `demo/` directory
- **Demo README**: `demo/README.md` for detailed instructions
- **macOS Native README**: `demo/macosApp/README.md` for SwiftUI version
- **Platform READMEs**: `demo/{platform}App/README.md` for each platform
- **SDK Documentation**: `docs/` directory

---

**Navigation**: [← API Reference](api-reference.md) | [Advanced Topics →](advanced.md)
