# Demo Application

A comprehensive Kotlin Multiplatform demo showcasing the Stellar SDK across all supported platforms (Android, iOS, macOS, Desktop, Web). Learn SDK usage through production-ready patterns with 95% code sharing.

## Overview

The demo app demonstrates real-world SDK usage patterns through 11 interactive features, from basic cryptography to advanced smart contract operations. Unlike simplified samples, this is a full-featured application that:

- **Connects to real Stellar networks**: All operations run against testnet with actual transaction submission
- **Uses production-ready patterns**: Comprehensive error handling, async operations, proper state management
- **Shares 95% of code**: Compose Multiplatform UI and business logic work across Android, iOS, Desktop, and Web
- **Uses SDK directly**: Demonstrates actual SDK APIs, not simplified wrappers or reimplementations

### What You'll Learn

Explore SDK capabilities through 11 interactive demonstrations:

**Core Operations**:
- **Key Generation**: Ed25519 cryptography, signing, verification, StrKey encoding
- **Account Funding**: Friendbot integration for testnet accounts
- **Account Details**: Horizon queries, balance parsing, account data

**Transactions & Payments**:
- **Trust Asset**: Trustline operations, transaction building, signing, submission
- **Send Payment**: Payment operations, asset handling, native and issued assets
- **Fetch Transaction**: Transaction parsing, operation details, human-readable formatting

**Smart Contracts**:
- **Contract Details**: WASM parsing, contract specifications, metadata extraction
- **Deploy Contract**: One-step and two-step deployment, constructor arguments
- **Invoke Hello World**: Basic contract invocation, automatic type conversion
- **Invoke Auth**: Dynamic authorization, conditional signing patterns
- **Invoke Token**: SEP-41 token interface, multi-signature workflows

## Screenshots

The demo runs on all major platforms with a consistent celestial-themed UI:

<table>
  <tr>
    <td align="center">
      <img src="../demo/screenshots/android-demo.png" height="500" alt="Android Demo"><br>
      <b>Android</b>
    </td>
    <td width="50"></td>
    <td align="center">
      <img src="../demo/screenshots/ios-demo.png" height="500" alt="iOS Demo"><br>
      <b>iOS</b>
    </td>
  </tr>
</table>

<table>
  <tr>
    <td align="center">
      <img src="../demo/screenshots/web-demo.png" width="600" alt="Web Demo"><br>
      <b>Web (JavaScript)</b>
    </td>
  </tr>
</table>

## Quick Start

Get the demo running in under a minute:

```bash
# Desktop (recommended for fastest iteration)
./gradlew :demo:desktopApp:run

# Android
./gradlew :demo:androidApp:installDebug

# Web (Vite dev server with hot reload)
./gradlew :demo:webApp:viteDev
# Opens at http://localhost:8081
```

For iOS and macOS native setup, see the [comprehensive guide](../demo/README.md).

## Architecture

The demo uses **Compose Multiplatform** with maximum code sharing:

- **Shared Module** (`demo/shared`): All UI and business logic (95% of code)
- **Platform Apps**: Lightweight entry points (5-20 lines each)
- **Exception**: macOS native app uses SwiftUI (30 Swift files) as alternative to Compose

All features demonstrate SDK usage directly - no wrappers, no simplified implementations, just production-ready patterns you can use in your own apps.

## Platform Support

| Platform | UI Framework | Min Version | Notes |
|----------|--------------|-------------|-------|
| Android  | Compose | API 24 (Android 7.0) | Fully tested |
| iOS | Compose | iOS 14.0 | Requires Xcode 15+ |
| Desktop (JVM) | Compose | Java 17+ | Runs on macOS, Windows, Linux |
| Web | Compose | Modern browsers | Chrome 90+, Firefox 88+, Safari 15.4+ |
| macOS Native | SwiftUI | macOS 11.0 | Alternative native app |

## Code Organization

```
demo/shared/                    # 95% shared code
├── stellar/                    # SDK integration (business logic)
│   ├── KeyPairGeneration.kt
│   ├── AccountFunding.kt
│   ├── AccountDetails.kt
│   ├── TrustAsset.kt
│   ├── SendPayment.kt
│   ├── FetchTransaction.kt
│   ├── ContractDetails.kt
│   ├── DeployContract.kt
│   ├── InvokeHelloWorldContract.kt
│   ├── InvokeAuthContract.kt
│   └── InvokeTokenContract.kt
└── ui/screens/                 # 11 feature screens + main menu
    ├── MainScreen.kt
    ├── KeyGenerationScreen.kt
    ├── FundAccountScreen.kt
    ├── AccountDetailsScreen.kt
    ├── TrustAssetScreen.kt
    ├── SendPaymentScreen.kt
    ├── FetchTransactionScreen.kt
    ├── ContractDetailsScreen.kt
    ├── DeployContractScreen.kt
    ├── InvokeHelloWorldContractScreen.kt
    ├── InvokeAuthContractScreen.kt
    └── InvokeTokenContractScreen.kt

demo/{platform}App/             # Lightweight entry points
```

## Comprehensive Documentation

This overview provides a quick introduction. For detailed information:

### Building & Running
- **[Demo README](../demo/README.md)** - Complete build/run instructions, architecture details, troubleshooting
- **[Android Setup](../demo/androidApp/README.md)** - Android-specific prerequisites and configuration
- **[iOS Setup](../demo/iosApp/README.md)** - iOS framework setup, libsodium dependency, Xcode configuration
- **[macOS Native Setup](../demo/macosApp/README.md)** - Native SwiftUI app, Desktop vs Native comparison
- **[Desktop Setup](../demo/desktopApp/README.md)** - JVM configuration, packaging for distribution
- **[Web Setup](../demo/webApp/README.md)** - Vite dev server, production builds, deployment

### Development
- **[Demo CLAUDE.md](../demo/CLAUDE.md)** - Implementation patterns, adding new features, best practices
- **[Project Structure](../demo/README.md#project-structure)** - Detailed file organization
- **[Technology Stack](../demo/README.md#technology-stack)** - Dependencies and frameworks

### Platform Details
Each platform has specific setup requirements, build commands, and troubleshooting guides in its respective README.

## Why Explore This Demo?

1. **Learn by Example**: See production-ready SDK patterns, not toy examples
2. **Real Network Integration**: All features work against Stellar testnet
3. **Cross-Platform Patterns**: Understand how to build once, deploy everywhere
4. **Complete Coverage**: From basic keys to advanced smart contracts
5. **Copy-Paste Ready**: Code you can adapt for your own applications

## Next Steps

1. **Run the demo**: Start with Desktop app for fastest iteration
2. **Explore features**: Try all 11 demonstrations to understand SDK capabilities
3. **Read the code**: Business logic in `demo/shared/src/commonMain/kotlin/com/soneso/demo/stellar/`
4. **Build your app**: Use patterns from the demo in your own project

---

**Navigation**: [← Getting Started](getting-started.md) | [Architecture →](architecture.md)
