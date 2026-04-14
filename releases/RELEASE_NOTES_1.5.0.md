# Release Notes - Version 1.5.0

## Overview

Version 1.5.0 adds cross-device passkey authentication support to the smart account SDK layer. The `WebAuthnProvider.authenticate()` interface now accepts transport hints via the new `AllowCredential` type, enabling QR code scanning for cross-device authentication flows.

This is a **breaking change** to the `WebAuthnProvider` interface introduced in 1.4.0.

## Changed

### WebAuthn Hybrid Transport Support

The `WebAuthnProvider.authenticate()` parameter `allowCredentialIds: List<ByteArray>?` is replaced by `allowCredentials: List<AllowCredential>?`. The new `AllowCredential` data class pairs credential IDs with optional transport hints (e.g., "internal", "hybrid", "usb", "ble", "nfc").

Transport hints enable browsers and OS credential managers to offer cross-device authentication options such as QR code scanning. When a passkey is registered with transport hints, the SDK stores them and pipes them through to the WebAuthn provider during authentication.

**Migration**: Replace `allowCredentialIds = listOf(bytes)` with `allowCredentials = listOf(AllowCredential(id = bytes))` or use the convenience factory `AllowCredential.fromIds(listOf(bytes))`.

### Platform Provider Updates

- **JS (Browser)**: Includes `transports` in the `allowCredentials` JS credential descriptors when available
- **Android**: Conditionally adds `transports` JSON field to credential descriptors
- **Apple (iOS/macOS)**: Accepts the parameter for interface compliance; transport hints are ignored because Apple manages cross-device flows at the OS level

### Call Site Updates

All SDK call sites that invoke `WebAuthnProvider.authenticate()` now pipe stored transport hints from `StoredCredential` through to the provider:
- `OZWalletOperations.authenticatePasskey()`
- `OZTransactionOperations` (single-signer passkey auth)
- `OZMultiSignerManager` (multi-signer passkey auth)

`SelectedSigner.Passkey` gains a `transports` field for multi-signer cross-device flows.

## Fixed

- **JS Node CI**: Exclude `org.nodejs` and `com.yarnpkg` groups from JetBrains Compose Maven repository to prevent build failures when the repository returns 503
- **iOS demo app**: Pre-build script conditionally builds for device or simulator based on target platform

## Platform Support

All platforms fully supported:
- JVM (Android API 24+, Server Java 17+)
- iOS (iOS 14.0+)
- macOS (macOS 11.0+)
- JavaScript (Browser and Node.js 14+)

## Installation

```kotlin
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.5.0")
}
```

---

**Full Changelog**: https://github.com/Soneso/kmp-stellar-sdk/compare/v1.4.0...v1.5.0
