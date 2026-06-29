# Smart Account Demo App

A Kotlin Multiplatform application for testing the Smart Account Kit SDK with WebAuthn passkey authentication on Stellar testnet. The app covers wallet creation, token transfers, multi-signer authorization, on-chain context rule management, and an agent-signer flow that delegates scoped, spend-capped authority to an autonomous agent.

The primary purpose of this app is to test and validate the SDK implementation across platforms. It is not intended as a production application template.

Supported platforms: Android, iOS, macOS, and Web. Android, iOS, and Web use Compose Multiplatform for the UI. macOS uses a native SwiftUI implementation with a Kotlin bridge to shared business logic. Tested on macOS 26 (Tahoe), iOS 18, and Chrome.

## Features

The demo includes 10 screens:

### 1. Main Dashboard
Wallet status display with XLM and DEMO token balances, navigation to all other screens, an approval-inbox bell that badges pending agent escalations, activity log showing SDK operations in real time, balance refresh, and wallet disconnect.

### 2. Wallet Creation
Collects a username, registers a passkey via the platform's WebAuthn provider, deploys a smart account contract to testnet, funds the wallet with XLM via Friendbot, and mints 10,000 DEMO tokens. Displays the credential ID, contract address, transaction hash, and initial balances on completion.

### 3. Wallet Connection
Four connection strategies:
- **Auto Connect** -- restores a saved session if one exists, otherwise authenticates with a passkey and tries to resolve the contract address automatically.
- **Connect via Indexer** -- authenticates with a passkey first, then looks up the associated contract address through the indexer service.
- **Connect with Address** -- recovery flow where the user provides a known contract address and authenticates with any registered passkey.
- **Retry Pending Deployment** -- retries contract deployment for credentials where the passkey was registered but the on-chain deployment did not complete.

### 4. Transfer
Send XLM or DEMO tokens from the connected smart account to any Stellar address. When the account has multiple signers (from context rules), a signer picker allows selecting which signers co-authorize the transaction. Supports both single-passkey and multi-signer transfer paths. Signing with a passkey signer triggers a WebAuthn authentication ceremony to sign the Soroban authorization entry.

### 5. Context Rules
Lists all on-chain authorization rules for the connected account. Each rule card shows its ID, name, context type (Default, CallContract, CreateContract), signers, policies, and expiry. Supports expanding rules for detail view, removing rules (with a safety check preventing removal of the last rule), and navigating to the rule builder for creating or editing rules.

### 6. Context Rule Builder
Form for creating or editing a context rule. Configure the context type, rule name, optional expiry (as a ledger offset converted to an absolute ledger number), signers (passkey, delegated G-address, raw Ed25519), and policy contracts (threshold, spending limit, weighted threshold) with their parameters. In edit mode, you can rename the rule, change its expiry, add or remove signers, and add, remove, or modify policies; each change is applied as a separate on-chain transaction.

### 7. Account Signers
Displays all unique signers registered across all context rules. Each signer entry shows its type (passkey, delegated G-address, raw Ed25519), identifier, and the list of context rules it belongs to. Signers are deduplicated across rules using stable signer keys.

### 8. Approve
Grants a SEP-41 token spending allowance that delegates spending authority over the smart account's tokens to another address. This screen demonstrates an arbitrary contract call: unlike Transfer (which uses the dedicated transfer helper), Approve invokes the token's `approve` function through the generic contract-call path, with both single-signer and multi-signer support.

### 9. Delegate to an Agent
Reached from the Context Rules screen. Creates an on-chain context rule that scopes a raw Ed25519 agent key to a single token contract with a spending cap and an expiry, so an autonomous agent can sign transfers within that scope without a passkey. The result card displays the agent key and exposes the scoped token contract and the transaction hash as copyable values for wiring the reference agent.

### 10. Approval Inbox
Reached from the bell on the Main Dashboard, which badges the count of pending escalations. When the agent attempts an over-cap call, the call is rejected on-chain and escalated to the coordination server; the inbox lists each pending request with its decoded call, and the user approves it (re-submitting it under the Default rule) or rejects it. An approved request shows its on-chain transaction hash as a copyable value.

## Architecture

```
smart-account-demo/
├── shared/                              # Shared KMP module
│   └── src/
│       ├── commonMain/kotlin/.../
│       │   ├── App.kt                   # Compose app entry point
│       │   ├── config/                  # DemoConfig, PolicyInfo, KNOWN_POLICIES
│       │   ├── flows/                   # Business logic
│       │   ├── state/                   # DemoState, ActivityLogState
│       │   ├── token/                   # DemoTokenService
│       │   ├── util/                    # Helpers, context rule parsing, policy builders
│       │   ├── ui/screens/              # Compose screens
│       │   └── platform/               # Expect/actual (clipboard)
│       ├── androidMain/                 # Android clipboard, platform init
│       ├── iosMain/                     # iOS UIViewController, platform init
│       ├── macosMain/                   # MacOSBridge (thick bridge to Swift)
│       └── jsMain/                      # JS clipboard, platform init
├── androidApp/                          # Android entry point (Compose)
├── iosApp/                              # iOS entry point (SwiftUI wrapping Compose)
├── macosApp/                            # macOS native SwiftUI app
│   └── StellarSmartDemo/
│       ├── Views/                       # Screens, sections, view model
│       ├── Components/                  # Reusable UI components
│       └── Utilities/                   # Helper files
├── webApp/                              # Web entry point (Compose via Kotlin/JS)
├── coordination-server/                 # Ktor JVM relay service (agent-signer flow)
├── reference-agent/                     # Kotlin JVM reference agent (agent-signer flow)
└── documentation/                       # agent-flow.md end-to-end runbook
```

**Shared module**: All business logic lives in `flows/`. State management uses `DemoState` (wallet connection, balances, kit instance) and `ActivityLogState` (operation log). Platform-specific code is limited to clipboard access, WebAuthn providers, and storage adapters.

**macOS app**: Uses native SwiftUI instead of Compose. A Kotlin bridge in `macosMain` exposes the shared flow functions to Swift; the SwiftUI layer handles UI rendering while all SDK interaction goes through the bridge.

## Agent-signer flow

The demo includes an end-to-end agent-signer flow: a user delegates a scoped, spend-capped Ed25519 authority to an autonomous agent (Delegate to an Agent), the agent signs calls within that scope, and an over-cap call is rejected on-chain and escalated to the user for approval (Approval Inbox). The subsystem is all-Kotlin, built from two Gradle modules:

- `:smart-account-demo:coordination-server` -- a Ktor (CIO) JVM service that relays escalated calls between the agent and the app. Pure relay, no SDK dependency. Run with `./gradlew :smart-account-demo:coordination-server:run`.
- `:smart-account-demo:reference-agent` -- a Kotlin JVM executable that connects to the smart account headlessly (`connectToContract`), attempts a token transfer, and escalates over-cap calls. Run with `./gradlew :smart-account-demo:reference-agent:run`.

The agent-flow UI (Delegate to an Agent, Approval Inbox, and the badged bell) lives in the shared Compose module and is surfaced by the web, Android, and iOS shells; the native SwiftUI macOS app does not host these Compose screens. The web app is the recommended manual-test target -- passkeys work on `localhost` with no entitlement setup.

See [documentation/agent-flow.md](documentation/agent-flow.md) for the end-to-end runbook.

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
1. Resolve packages: File -> Packages -> Resolve Package Versions (downloads swift-sodium and reown-swift dependencies).
2. Set your signing team: select the StellarSmartDemo target -> Signing & Capabilities -> Team. This is required for both simulator and device builds and must be set after every `xcodegen generate`.
3. Select the StellarSmartDemo scheme and an iOS 16.0+ simulator.
4. Run (Cmd+R).

**Switching between simulator and physical device:**

The framework dependency in `project.yml` must match the target platform. After changing it, run `xcodegen generate` to regenerate the Xcode project, then set your signing team again.

| Target | Framework path in `project.yml` |
|--------|---------------------------------------|
| Simulator | `../shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework` |
| Physical device | `../shared/build/bin/iosArm64/debugFramework/shared.framework` |

The pre-build script automatically builds the correct Kotlin framework based on the selected target platform. You only need to change the framework dependency path and regenerate.

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
| `RPC_URL` | Soroban RPC endpoint |
| `NETWORK_PASSPHRASE` | Stellar testnet passphrase |
| `ACCOUNT_WASM_HASH` | Smart account contract WASM hash (OZ stellar-contracts v0.7.0) |
| `WEBAUTHN_VERIFIER_ADDRESS` | On-chain WebAuthn (secp256r1) signature verifier contract |
| `ED25519_VERIFIER_ADDRESS` | On-chain Ed25519 signature verifier contract |
| `NATIVE_TOKEN_CONTRACT` | XLM Stellar Asset Contract (SAC) address on testnet |
| `DEFAULT_RELAYER_URL` | Relayer proxy for fee-sponsored transaction submission |
| `DEFAULT_INDEXER_URL` | Credential-to-contract address lookup service |
| `DEFAULT_RP_ID` | WebAuthn Relying Party ID (`soneso.com`) |
| `RP_NAME` | Display name for passkey prompts |
| `REOWN_PROJECT_ID` | Reown (WalletConnect) project ID for external-wallet connect. Empty by default; register a free project ID at [cloud.reown.com](https://cloud.reown.com) and set it. External-wallet connect is disabled (and its UI hidden) when unset. |
| `MAX_CONTEXT_RULE_SCAN_ID` | Upper bound on rule-ID iteration when scanning the chain (default `25`) |

DEMO token settings (`DEMO_TOKEN_*`) control the deterministic deployment and minting of a custom Soroban token used for testing transfers.

Known policy contracts (threshold, spending limit, weighted threshold) are defined in `KNOWN_POLICIES`.

## External Wallet Connection

The demo supports connecting an external Stellar wallet (Freighter) as a delegated signer, as an alternative to entering a secret key manually.

| Platform | Method | Requirement |
|----------|--------|-------------|
| Web | Freighter browser extension | Install from [freighter.app](https://www.freighter.app/) |
| Android | WalletConnect v2 (Reown) | Freighter Mobile on the same device |
| iOS | WalletConnect v2 (Reown) | Freighter Mobile on the same device |
| macOS | Not available | Use secret key entry |

Wallet connection buttons are hidden on simulators and emulators since WalletConnect requires the wallet app on a real device.

### Reown Project ID

Android and iOS use the [Reown](https://cloud.reown.com/) (WalletConnect v2) SDK. A project ID is required. Set `REOWN_PROJECT_ID` in `DemoConfig.kt`. Register for a free project ID at [cloud.reown.com](https://cloud.reown.com/).

### iOS App Group

The Reown SDK requires an App Group for relay session storage. The entitlement `group.com.soneso.stellar.smartdemo` is configured in `iosApp/StellarSmartDemo/StellarSmartDemo.entitlements`.

To run on a physical iOS device, register this App Group in your Apple Developer account:

1. Go to [developer.apple.com](https://developer.apple.com/account/resources/identifiers/list/applicationGroup) -> Certificates, Identifiers & Profiles -> Identifiers -> App Groups
2. Click **+** and register `group.com.soneso.stellar.smartdemo`
3. In Xcode, refresh the App Group capability (Signing & Capabilities -> App Groups -> refresh button)

This is not needed for simulator builds (wallet connection is disabled on simulators).

## Quick Reference

| Task | Command |
|------|---------|
| Build Android APK | `./gradlew :smart-account-demo:androidApp:assembleDebug` |
| Install on Android device | `./gradlew :smart-account-demo:androidApp:installDebug` |
| Build iOS framework (simulator) | `./gradlew :smart-account-demo:shared:linkDebugFrameworkIosSimulatorArm64` |
| Build iOS framework (device) | `./gradlew :smart-account-demo:shared:linkDebugFrameworkIosArm64` |
| Build macOS framework | `./gradlew :smart-account-demo:shared:linkDebugFrameworkMacosArm64` |
| Generate iOS Xcode project | `cd smart-account-demo/iosApp && xcodegen generate` |
| Generate macOS Xcode project | `cd smart-account-demo/macosApp && xcodegen generate` |
| Web dev server (Vite) | `./gradlew :smart-account-demo:webApp:viteDev` |
| Web production build | `./gradlew :smart-account-demo:webApp:productionDist` |
| Web production preview | `./gradlew :smart-account-demo:webApp:vitePreview` |

## License

Copyright 2026 Soneso

Licensed under the Apache License, Version 2.0. See [LICENSE](../LICENSE).
