# WebAuthn Setup: macOS

Platform-specific guide for configuring WebAuthn passkey authentication in macOS applications using the KMP Stellar SDK Smart Account Kit.

## Prerequisites

- macOS 13.0+ (Ventura)
- Xcode 14+
- An Apple Developer account
- A domain you control for apple-app-site-association

## libsodium Dependency

On macOS the SDK links the system libsodium installed via Homebrew:

```bash
brew install libsodium
```

For a native Swift app embedding the shared framework, add the library to the app target's build settings:
- Header Search Paths: `/opt/homebrew/opt/libsodium/include`
- Library Search Paths: `/opt/homebrew/opt/libsodium/lib`
- Other Linker Flags: `-lsodium`

No additional dependencies are needed for WebAuthn -- it uses the built-in AuthenticationServices framework.

## Associated Domains

macOS passkeys require the same Associated Domains entitlement as iOS. A development-signed build with the `?mode=developer` flag on the domain entry is sufficient during development; no sandbox or special signing setup is required.

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

### NSWindow Presentation Context (Required on macOS)

On macOS, `ASAuthorizationController` requires a presentation context provider to specify which window displays the authorization sheet. Set the `presentationContextProvider` property before calling any registration or authentication methods. Without this, macOS will fail with error code 1004.

On iOS, this is not required — the system presents the sheet automatically.

```kotlin
// Set from Kotlin (if you have a reference to the provider)
webauthnProvider.presentationContextProvider = windowProvider
```

From Swift (typical macOS integration):

```swift
class WindowProvider: NSObject, ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        // Fall back to the main window; a freshly constructed NSWindow would
        // present the sheet off-screen.
        return NSApplication.shared.keyWindow ?? NSApplication.shared.mainWindow!
    }
}
provider.presentationContextProvider = WindowProvider()
```

## Storage Adapter

`UserDefaultsStorageAdapter` stores data in an isolated NSUserDefaults suite.

**Constructor parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `suiteName` | `String` | No | `"com.soneso.stellar.smartaccount"` | NSUserDefaults suite name |

```kotlin
import com.soneso.stellar.sdk.smartaccount.UserDefaultsStorageAdapter

// Default suite (local to this device, not synced via iCloud)
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
    webauthnVerifierAddress = "<C-address of the WebAuthn verifier>",
    webauthnProvider = webauthnProvider,
    storage = storage
)

val kit = OZSmartAccountKit.create(config)
```

## Troubleshooting

### Associated Domains validation fails

If the system does not fetch or validate the `apple-app-site-association` file:
1. Use `?mode=developer` in the domain entry during development to bypass Apple's CDN cache
2. Confirm the entitlement is present in the built product (`codesign -d --entitlements - YourApp.app`)
3. Verify the file is served over HTTPS with `Content-Type: application/json` and no redirects

### ASAuthorizationError code 1004 (no host window)

This error is specific to macOS and indicates the authorization controller has no window to present its UI. Ensure:
- `presentationContextProvider` is set on the `AppleWebAuthnProvider` before calling register/authenticate
- The provider returns a valid `NSWindow` from `presentationAnchor(for:)`
- The app is in the foreground when the request is made

### Passkeys synced from iOS not appearing

Passkeys sync via iCloud Keychain. Ensure:
- iCloud Keychain is enabled on both devices
- Both devices are signed in to the same Apple ID
- The `rpId` matches between the iOS and macOS apps

### Sandbox restrictions

Sandboxed apps may have limited Keychain access. If `KeychainStorageAdapter` fails with unexpected `OSStatus` codes, verify your entitlements include `com.apple.security.keychain-access-groups` for the appropriate group, or use `UserDefaultsStorageAdapter` as an alternative.
