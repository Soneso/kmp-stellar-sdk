# WebAuthn Setup: macOS

Platform-specific guide for configuring WebAuthn passkey authentication in macOS applications using the KMP Stellar SDK Smart Account Kit.

## Prerequisites

- macOS 13.0+ (Ventura)
- Xcode 14+
- An Apple Developer account
- A domain you control for apple-app-site-association
- Developer ID signing or App Sandbox entitlement for associated domains

## SPM Dependencies

The SDK requires libsodium for cryptographic operations. Add Clibsodium via Swift Package Manager in Xcode:

1. File > Add Package Dependencies
2. Enter URL: `https://github.com/nicklama/Clibsodium`
3. Select "Up to Next Major Version"

No additional dependencies are needed for WebAuthn -- it uses the built-in AuthenticationServices framework.

## Associated Domains

macOS passkeys require Associated Domains, similar to iOS. However, macOS has additional signing requirements.

### Signing Requirements

Associated Domains on macOS requires one of:
- **Developer ID signing**: For apps distributed outside the Mac App Store
- **App Sandbox entitlement**: For sandboxed apps (Mac App Store or local development)

During development with Xcode, enable the App Sandbox capability to allow associated domains without Developer ID signing.

### 1. Enable the Entitlement

In Xcode:
1. Select your macOS app target
2. Go to "Signing & Capabilities"
3. Click "+ Capability" and add "Associated Domains"
4. Add an entry: `webcredentials:your-domain.com`

For local development, append the developer mode flag:
`webcredentials:your-domain.com?mode=developer`

### 2. Host apple-app-site-association

Create a file at `https://your-domain.com/.well-known/apple-app-site-association`:

```json
{
  "webcredentials": {
    "apps": [
      "TEAM_ID.com.example.yourapp"
    ]
  }
}
```

Replace `TEAM_ID` with your Apple Developer Team ID and `com.example.yourapp` with your app's bundle identifier. This is the same file format as iOS -- if your iOS and macOS apps share the same domain, a single file covers both.

Serve this file over HTTPS with `Content-Type: application/json`.

## WebAuthn Provider

`AppleWebAuthnProvider` is shared between iOS and macOS (nativeMain source set). It uses `ASAuthorizationPlatformPublicKeyCredentialProvider` from the AuthenticationServices framework.

**Constructor parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `rpId` | `String` | Yes | - | Relying party domain (must match associated domain) |
| `rpName` | `String` | Yes | - | Display name shown during passkey prompts |
| `timeout` | `Long` | No | 60000 | Timeout in milliseconds |

```kotlin
import com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider

val webauthnProvider = AppleWebAuthnProvider(
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)
```

### NSWindow Presentation Context

On macOS, `ASAuthorizationController` requires a presentation context provider to specify which window displays the authorization sheet. The current `AppleWebAuthnProvider` implementation performs requests without an explicit presentation context. For macOS apps, you may need to set the presentation context on the controller via a platform-specific integration point (e.g., a SwiftUI bridge or AppKit delegate that provides the `NSWindow`).

A future SDK revision may accept an optional presentation context parameter.

## Storage Adapter

`UserDefaultsStorageAdapter` stores data in an isolated NSUserDefaults suite. Use a different `suiteName` from your iOS app if you want separate credential stores per platform.

**Constructor parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `suiteName` | `String` | No | `"com.soneso.stellar.smartaccount"` | NSUserDefaults suite name |

```kotlin
import com.soneso.stellar.sdk.smartaccount.UserDefaultsStorageAdapter

// Default suite (shared with iOS if using iCloud)
val storage = UserDefaultsStorageAdapter()

// macOS-specific suite name
val storage = UserDefaultsStorageAdapter(
    suiteName = "com.soneso.stellar.smartaccount.macos"
)
```

For stronger data protection, use `KeychainStorageAdapter`:

```kotlin
import com.soneso.stellar.sdk.smartaccount.KeychainStorageAdapter

val storage = KeychainStorageAdapter(
    serviceName = "com.soneso.stellar.smartaccount.macos"
)
```

## Full Kit Initialization

```kotlin
import com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.UserDefaultsStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit

val webauthnProvider = AppleWebAuthnProvider(
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)

val storage = UserDefaultsStorageAdapter(
    suiteName = "com.soneso.stellar.smartaccount.macos"
)

val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "your-wasm-hash-hex",
    webauthnVerifierAddress = "CBCD1234...",
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet",
    webauthnProvider = webauthnProvider,
    storage = storage
)

val kit = OZSmartAccountKit.create(config)
```

## Troubleshooting

### Associated Domains not working without Developer ID

macOS requires Developer ID signing or the App Sandbox entitlement for associated domains. During development:
1. Enable "App Sandbox" in Signing & Capabilities
2. Use `?mode=developer` in the domain entry to bypass CDN caching

Without either of these, the system will not fetch or validate the `apple-app-site-association` file.

### ASAuthorizationError code 1005 (not interactive)

This error is specific to macOS and indicates the authorization controller could not present its UI. Ensure:
- The authorization request is performed on the main thread
- A valid `NSWindow` is available for the presentation context
- The app is in the foreground when the request is made

### Passkeys synced from iOS not appearing

Passkeys sync via iCloud Keychain. Ensure:
- iCloud Keychain is enabled on both devices
- Both devices are signed in to the same Apple ID
- The `rpId` matches between the iOS and macOS apps

### Sandbox restrictions

Sandboxed apps may have limited Keychain access. If `KeychainStorageAdapter` fails with unexpected `OSStatus` codes, verify your entitlements include `com.apple.security.keychain-access-groups` for the appropriate group, or use `UserDefaultsStorageAdapter` as an alternative.
