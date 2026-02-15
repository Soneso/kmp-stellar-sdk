# WebAuthn Setup: iOS

Platform-specific guide for configuring WebAuthn passkey authentication in iOS applications using the KMP Stellar SDK Smart Account Kit.

## Prerequisites

- iOS 16.0+ (passkeys require AuthenticationServices with platform public key credential support)
- Xcode 14+
- An Apple Developer account with associated domains capability
- A domain you control for apple-app-site-association

## SPM Dependencies

The SDK requires libsodium for cryptographic operations. Add Clibsodium via Swift Package Manager in Xcode:

1. File > Add Package Dependencies
2. Enter URL: `https://github.com/nicklama/Clibsodium`
3. Select "Up to Next Major Version"

No additional dependencies are needed for WebAuthn -- it uses the built-in AuthenticationServices framework.

## Associated Domains

Passkeys on iOS require the Associated Domains entitlement to link your app with a web domain. The `rpId` you pass to `AppleWebAuthnProvider` must match an associated domain.

### 1. Enable the Entitlement

In Xcode:
1. Select your app target
2. Go to "Signing & Capabilities"
3. Click "+ Capability" and add "Associated Domains"
4. Add an entry: `webcredentials:your-domain.com`

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

Replace `TEAM_ID` with your Apple Developer Team ID (found in the Apple Developer portal under Membership). Replace `com.example.yourapp` with your app's bundle identifier.

Serve this file:
- Over HTTPS (no redirects)
- With `Content-Type: application/json`
- Without a `.json` file extension in the URL

### 3. Verify

Apple validates associated domains when the app is installed. To test during development, add the `?mode=developer` query parameter to the domain entry: `webcredentials:your-domain.com?mode=developer`. This bypasses Apple's CDN cache and fetches the file directly.

## WebAuthn Provider

`AppleWebAuthnProvider` uses the AuthenticationServices framework (`ASAuthorizationPlatformPublicKeyCredentialProvider`) for passkey creation and assertion.

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

The provider can also be created via the companion factory method:

```kotlin
val webauthnProvider = AppleWebAuthnProvider.create(
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)
```

## Storage Adapters

iOS offers two storage options:

### UserDefaultsStorageAdapter

Stores data in an isolated NSUserDefaults suite. Adequate for most use cases since stored credentials contain public keys, not secret keys.

**Constructor parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `suiteName` | `String` | No | `"com.soneso.stellar.smartaccount"` | NSUserDefaults suite name |

```kotlin
import com.soneso.stellar.sdk.smartaccount.UserDefaultsStorageAdapter

val storage = UserDefaultsStorageAdapter()

// Or with a custom suite name
val storage = UserDefaultsStorageAdapter(suiteName = "com.yourapp.smartaccount")
```

### KeychainStorageAdapter

Stores data in the iOS Keychain using the Security framework. Provides encryption at rest, `kSecAttrAccessibleAfterFirstUnlock` access control, and optional iCloud Keychain sync. Use this when stronger data protection is required.

**Constructor parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `serviceName` | `String` | No | `"com.soneso.stellar.smartaccount"` | Keychain service name |

```kotlin
import com.soneso.stellar.sdk.smartaccount.KeychainStorageAdapter

val storage = KeychainStorageAdapter()

// Or with a custom service name
val storage = KeychainStorageAdapter(serviceName = "com.yourapp.smartaccount")
```

## Full Kit Initialization

```kotlin
import com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.KeychainStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit

val webauthnProvider = AppleWebAuthnProvider(
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)

val storage = KeychainStorageAdapter()

val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "your-wasm-hash-hex",
    webauthnVerifierAddress = "CBCD1234...",
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet",
    webauthnProvider = webauthnProvider
)

val kit = OZSmartAccountKit.create(config, storage)
```

## Troubleshooting

### Passkeys are not available on the iOS Simulator

The iOS Simulator does not support passkeys. Passkey operations will fail with `WebAuthnException.NotSupported` or an ASAuthorization error code 1003. Test passkey flows on a physical device with Face ID or Touch ID.

### ASAuthorizationError code 1001 (cancelled)

The user dismissed the passkey prompt. The SDK maps this to `WebAuthnException.Cancelled`. Handle this gracefully in your UI.

### ASAuthorizationError code 1004 (failed)

The authenticator operation failed. Common causes:
- The `rpId` does not match any associated domain in the app's entitlements
- The `apple-app-site-association` file is not accessible or has incorrect content
- The provisioning profile does not include the Associated Domains capability

### Provisioning profile requirements

Associated Domains requires a provisioning profile that includes the capability. Automatic signing in Xcode handles this, but manual signing requires regenerating profiles after adding the entitlement in the Apple Developer portal.

### Credential not found during authentication

If `authenticate()` throws `WebAuthnException.AuthenticationFailed`, the user may not have a passkey for this `rpId` on the device. Ensure the passkey was created with the same `rpId` and that iCloud Keychain sync is enabled if testing across devices.
