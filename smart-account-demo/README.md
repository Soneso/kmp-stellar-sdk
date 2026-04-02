# Smart Account Demo App

A Kotlin Multiplatform application for testing the Smart Account Kit SDK with WebAuthn passkey authentication on Stellar testnet. The app covers wallet creation, token transfers, multi-signer authorization, and on-chain context rule management.

The primary purpose of this app is to test and validate the SDK implementation across platforms. It is not intended as a production application template.

Supported platforms: Android, iOS, macOS, and Web. Android, iOS, and Web use Compose Multiplatform for the UI. macOS uses a native SwiftUI implementation with a Kotlin bridge to shared business logic. Tested on macOS 26 (Tahoe), iOS 18, and Chrome.

## Features

The demo includes 7 screens:

### 1. Main Dashboard
Wallet status display with XLM and DEMO token balances, navigation to all other screens, activity log showing SDK operations in real time, balance refresh, and wallet disconnect.

### 2. Wallet Creation
Collects a username, registers a passkey via the platform's WebAuthn provider, deploys a smart account contract to testnet, funds the wallet with XLM via the relayer, and mints 10,000 DEMO tokens. Displays the credential ID, contract address, transaction hash, and initial balances on completion.

### 3. Wallet Connection
Four connection strategies:
- **Auto Connect** -- restores a saved session or triggers passkey authentication, then resolves the contract address via the indexer.
- **Connect via Indexer** -- authenticates with a passkey first, then looks up the associated contract address through the indexer service.
- **Connect with Address** -- recovery flow where the user provides a known contract address and authenticates with any registered passkey.
- **Retry Pending Deployment** -- retries contract deployment for credentials where the passkey was registered but the on-chain deployment did not complete.

### 4. Transfer
Send XLM or DEMO tokens from the connected smart account to any Stellar address. When the account has multiple signers (from context rules), a signer picker allows selecting which signers co-authorize the transaction. Supports both single-passkey and multi-signer transfer paths. Each transfer triggers a WebAuthn authentication ceremony to sign the Soroban authorization entry.

### 5. Context Rules
Lists all on-chain authorization rules for the connected account. Each rule card shows its ID, name, context type (Default, CallContract, CreateContract), signers, policies, and expiry. Supports expanding rules for detail view, removing rules (with a safety check preventing removal of the last rule), and navigating to the rule builder for creating or editing rules.

### 6. Context Rule Builder
Form for creating or editing a context rule. Configure the context type, rule name, optional expiry (as a ledger offset converted to an absolute ledger number), signers (passkey, Ed25519, or delegated), and policy contracts (threshold, spending limit, weighted threshold) with their parameters. In edit mode, updates the rule name and expiry via separate on-chain transactions.

### 7. Account Signers
Displays all unique signers registered across all context rules. Each signer entry shows its type (passkey, Ed25519, delegated), identifier, and the list of context rules it belongs to. Signers are deduplicated across rules using stable signer keys.

## Architecture

```
smart-account-demo/
├── shared/                              # Shared KMP module
│   └── src/
│       ├── commonMain/kotlin/.../
│       │   ├── App.kt                   # Compose app entry point
│       │   ├── config/                  # DemoConfig, PolicyInfo, KNOWN_POLICIES
│       │   ├── flows/                   # Business logic (6 flow files)
│       │   ├── state/                   # DemoState, ActivityLogState
│       │   ├── token/                   # DemoTokenService
│       │   ├── util/                    # Helpers, context rule parsing, policy builders
│       │   ├── ui/screens/              # 7 Compose screens
│       │   └── platform/               # Expect/actual (clipboard)
│       ├── androidMain/                 # Android clipboard, platform init
│       ├── iosMain/                     # iOS UIViewController, platform init
│       ├── macosMain/                   # MacOSBridge (thick bridge to Swift)
│       └── jsMain/                      # JS clipboard, platform init
├── androidApp/                          # Android entry point (Compose)
├── iosApp/                              # iOS entry point (SwiftUI wrapping Compose)
├── macosApp/                            # macOS native SwiftUI app
│   └── StellarSmartDemo/
│       ├── Views/                       # 7 screens, 3 sections, 1 view model
│       ├── Components/                  # 8 reusable UI components
│       └── Utilities/                   # 7 helper files
└── webApp/                              # Web entry point (Compose via Kotlin/JS)
```

**Shared module**: All business logic lives in `flows/` (wallet creation, connection, transfer, context rules, account signers, main screen initialization). State management uses `DemoState` (wallet connection, balances, kit instance) and `ActivityLogState` (operation log). Platform-specific code is limited to clipboard access, WebAuthn providers, and storage adapters.

**macOS app**: Uses native SwiftUI instead of Compose. The Kotlin `MacOSBridge.kt` in `macosMain` exposes all shared flow functions to Swift. The SwiftUI layer handles UI rendering while all SDK interaction goes through the bridge.

## Prerequisites

### All Platforms
- JDK 17+
- Kotlin 2.1+
- Gradle 8.5+

### Android
- Android SDK with API 28+ (Android 9.0 Pie, required for Credential Manager passkey API)
- Google Play Services on the device or emulator
- See [ANDROID_SETUP.md](ANDROID_SETUP.md) for Digital Asset Links configuration

### iOS
- Xcode 15.0+
- xcodegen: `brew install xcodegen`
- iOS 16.0+ deployment target (required for passkey support)
- swift-sodium package (Clibsodium product) for libsodium

### macOS
- Xcode 15.0+
- xcodegen: `brew install xcodegen`
- macOS 13.0+ (Ventura); Touch ID recommended (falls back to system password)
- libsodium via Homebrew: `brew install libsodium`

### Web
- Modern browser with WebAuthn support (Chrome 67+, Firefox 60+, Safari 14+)
- Node.js (for Vite dev server)

## Building and Running

### Android

```bash
# Build and install on connected device
./gradlew :smart-account-demo:androidApp:installDebug
```

Passkeys require a configured domain with a hosted `assetlinks.json` file. See [ANDROID_SETUP.md](ANDROID_SETUP.md) for setup steps.

### iOS

```bash
# Build the Kotlin framework
./gradlew :smart-account-demo:shared:linkDebugFrameworkIosSimulatorArm64

# Generate Xcode project and open
cd smart-account-demo/iosApp
xcodegen generate
open StellarSmartDemo.xcodeproj
```

In Xcode:
1. Add the swift-sodium package if not already added (File > Add Packages > `https://github.com/jedisct1/swift-sodium`). Select the Clibsodium product.
2. Select the StellarSmartDemo scheme and an iOS 16.0+ simulator.
3. Run (Cmd+R).

### macOS

```bash
# Install libsodium
brew install libsodium

# Build the Kotlin framework
./gradlew :smart-account-demo:shared:linkDebugFrameworkMacosArm64

# Generate Xcode project and open
cd smart-account-demo/macosApp
xcodegen generate
open StellarSmartDemo.xcodeproj
```

In Xcode, select the StellarSmartDemo scheme, set destination to "My Mac", and run (Cmd+R).

**Associated Domains developer mode** (required on macOS): macOS does not automatically bypass Associated Domains validation for debug builds. Without this, passkey operations fail with "Application is not associated with domain". Run once:

```bash
sudo swcutil developer-mode -e true
```

The app must be launched from Xcode with the debugger attached for the `?mode=developer` entitlement to take effect. Running the built `.app` directly will not work. This step is not needed on iOS (simulators enable developer mode automatically).

### Web

```bash
# Development server with Vite (hot reload)
./gradlew :smart-account-demo:webApp:viteDev

# Production build
./gradlew :smart-account-demo:webApp:productionDist
# Output: smart-account-demo/webApp/dist/

# Preview production build
./gradlew :smart-account-demo:webApp:vitePreview
```

The web app uses `localhost` as the RP ID during development. No domain association is needed -- browsers treat `localhost` as a secure context for WebAuthn.

## Passkey / WebAuthn Configuration

Passkeys are bound to a Relying Party (RP) ID. Each platform requires a domain association to link the app to the RP domain.

| Platform | Association Mechanism | Dev Configuration |
|----------|----------------------|-------------------|
| Web | Automatic (current hostname) | Works on `localhost` out of the box |
| Android | `assetlinks.json` at `/.well-known/` | Requires a hosted domain |
| iOS | Associated Domains entitlement + `apple-app-site-association` | `?mode=developer` suffix for simulators |
| macOS | Associated Domains entitlement + `apple-app-site-association` | `?mode=developer` + `swcutil developer-mode -e true` |

The demo defaults:
- **RP ID**: `soneso.com` (configured in `DemoConfig.kt`)
- **Associated Domains entitlement**: `webcredentials:soneso.com?mode=developer`
- **apple-app-site-association**: hosted at `https://soneso.com/.well-known/apple-app-site-association`

See [PASSKEY_SETUP.md](PASSKEY_SETUP.md) for full configuration details, including custom domain setup for production.

## Configuration

All testnet configuration is centralized in `shared/src/commonMain/kotlin/com/soneso/smartdemo/config/DemoConfig.kt`:

| Setting | Description |
|---------|-------------|
| `RPC_URL` | Soroban RPC endpoint (`soroban-testnet.stellar.org`) |
| `NETWORK_PASSPHRASE` | Stellar testnet passphrase |
| `ACCOUNT_WASM_HASH` | Smart account contract WASM hash (OZ stellar-contracts v0.6.0) |
| `WEBAUTHN_VERIFIER_ADDRESS` | On-chain WebAuthn (secp256r1) signature verifier contract |
| `ED25519_VERIFIER_ADDRESS` | On-chain Ed25519 signature verifier contract |
| `NATIVE_TOKEN_CONTRACT` | XLM Stellar Asset Contract (SAC) address on testnet |
| `DEFAULT_RELAYER_URL` | Relayer proxy for fee-sponsored transaction submission |
| `DEFAULT_INDEXER_URL` | Credential-to-contract address lookup service |
| `DEFAULT_RP_ID` | WebAuthn Relying Party ID (`soneso.com`) |
| `RP_NAME` | Display name for passkey prompts |

DEMO token settings (`DEMO_TOKEN_*`) control the deterministic deployment and minting of a custom Soroban token used for testing transfers.

Known policy contracts (threshold, spending limit, weighted threshold) are defined in `KNOWN_POLICIES`.

## Quick Reference

| Task | Command |
|------|---------|
| Build Android APK | `./gradlew :smart-account-demo:androidApp:assembleDebug` |
| Install on Android device | `./gradlew :smart-account-demo:androidApp:installDebug` |
| Build iOS framework | `./gradlew :smart-account-demo:shared:linkDebugFrameworkIosSimulatorArm64` |
| Build macOS framework | `./gradlew :smart-account-demo:shared:linkDebugFrameworkMacosArm64` |
| Generate iOS Xcode project | `cd smart-account-demo/iosApp && xcodegen generate` |
| Generate macOS Xcode project | `cd smart-account-demo/macosApp && xcodegen generate` |
| Web dev server (Vite) | `./gradlew :smart-account-demo:webApp:viteDev` |
| Web production build | `./gradlew :smart-account-demo:webApp:productionDist` |
| Web production preview | `./gradlew :smart-account-demo:webApp:vitePreview` |

## License

Copyright (c) 2026 Soneso. All rights reserved.
