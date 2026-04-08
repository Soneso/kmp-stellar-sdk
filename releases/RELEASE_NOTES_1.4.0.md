# Release Notes - Version 1.4.0

## Overview

Version 1.4.0 adds support for [OpenZeppelin smart account contracts](https://github.com/OpenZeppelin/stellar-contracts) on Soroban. Smart accounts enable passkey-based wallet authentication, multi-signer authorization, and policy-based access control on the Stellar network.

## Added

### OpenZeppelin Smart Accounts

Full SDK support for the OpenZeppelin smart account contracts (v0.7.0), providing:

- **Wallet lifecycle**: Create wallets with WebAuthn passkey registration, deploy smart account contracts, connect to existing wallets via session restore or credential lookup
- **Multi-signer authorization**: Combine passkey signers with delegated Ed25519 signers for threshold and weighted signing schemes
- **Context rules**: Define authorization rules per context type (Default, CallContract, CreateContract) with configurable signers and policies
- **Policies**: Simple threshold (M-of-N), weighted threshold (weighted voting), and spending limit (amount per time period)
- **Token operations**: Transfers and contract calls with automatic auth entry signing
- **Fee sponsoring**: Relayer proxy integration for gasless transactions
- **Credential discovery**: Indexer integration with auto-configured defaults for testnet and mainnet

### Platform Support

Smart account features work across all supported platforms:

| Platform | WebAuthn Provider | Storage Adapter |
|----------|------------------|-----------------|
| Android (API 28+) | CredentialManager | EncryptedSharedPreferences |
| iOS (14.0+) | ASAuthorization | UserDefaults, Keychain |
| macOS (11.0+) | ASAuthorization | UserDefaults, Keychain |
| Web (Browser) | navigator.credentials | IndexedDB, LocalStorage |

### Smart Account Demo App

A Compose Multiplatform demo application showcasing all smart account features:
- Wallet creation and connection with passkey authentication
- Token transfers with single and multi-signer flows
- Context rule management with policy configuration
- Runs on Android, iOS, macOS, desktop, and web

### Documentation

- API reference covering all public classes and methods (`docs/smart-accounts/api-reference.md`)
- Onboarding guide (`docs/smart-accounts/onboarding.md`)
- Platform-specific WebAuthn guides for Android, macOS, and web

## Platform Support

All platforms fully supported:
- JVM (Android API 24+, Server Java 17+)
- iOS (iOS 14.0+)
- macOS (macOS 11.0+)
- JavaScript (Browser and Node.js 14+)

Note: Smart account WebAuthn features require Android API 28+ (CredentialManager).

## Installation

```kotlin
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.4.0")
}
```

---

**Full Changelog**: https://github.com/Soneso/kmp-stellar-sdk/compare/v1.3.1...v1.4.0
