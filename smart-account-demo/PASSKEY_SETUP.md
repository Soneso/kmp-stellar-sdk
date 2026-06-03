# Passkey (WebAuthn) Domain Setup

This document describes the Relying Party (RP) domain configuration required for WebAuthn passkeys to function on each platform. Passkeys are bound to a specific RP ID (typically a domain name), and the authenticator will only allow credential use when the requesting origin matches that RP ID.

## Overview

WebAuthn passkeys require a trust relationship between the app and a domain. The RP ID identifies which domain "owns" the passkey credentials. On the web, the browser enforces this automatically. On mobile and desktop, each platform has its own mechanism for associating an app with a domain.

| Platform | RP ID Default | Association Mechanism |
|----------|---------------|----------------------|
| Web | Current hostname | Origin-based (automatic) |
| Android | Must be set explicitly | Digital Asset Links (`assetlinks.json`) |
| iOS | Must be set explicitly | Associated Domains entitlement + `apple-app-site-association` |
| macOS | Must be set explicitly | Associated Domains entitlement + `apple-app-site-association` |

---

## Web (Kotlin/JS)

### RP ID

On the web, the RP ID defaults to the current page's hostname if not explicitly set. For example, if the demo runs at `http://localhost:8081`, the RP ID is `localhost`.

The RP ID is set on `JsWebAuthnProvider`, not on `OZSmartAccountConfig`. For web apps, the `rpId` parameter of `JsWebAuthnProvider` can be omitted — the browser's WebAuthn API uses the current origin's effective domain.

```kotlin
val webauthnProvider = JsWebAuthnProvider(
    rpId = "your-domain.com", // or omit for localhost development
    rpName = "My Stellar App"
)
val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "...",
    webauthnVerifierAddress = "C...",
    webauthnProvider = webauthnProvider
)
```

### HTTPS Requirement

WebAuthn requires a secure context:
- `localhost` and `127.0.0.1` work without HTTPS (browser exception for development).
- All other origins require HTTPS with a valid TLS certificate.

### `.well-known/webauthn`

The `.well-known/webauthn` endpoint is only relevant when using a registrable domain RP ID that differs from the page origin (cross-origin passkey requests). For standard same-origin usage, this file is not required.

If you need cross-origin support, serve a JSON file at `https://<rp-id>/.well-known/webauthn` listing the allowed origins:

```json
{
  "origins": [
    "https://app.example.com",
    "https://staging.example.com"
  ]
}
```

---

## Android

Android uses the Credential Manager API (API level 28+) for passkey operations. The app must be associated with the RP domain via Digital Asset Links.

### Step 1: Set the RP ID

Pass the RP ID explicitly on `AndroidWebAuthnProvider`:

```kotlin
val webauthnProvider = AndroidWebAuthnProvider(
    context = applicationContext,
    rpId = "example.com",
    rpName = "My Stellar App"
)
val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "...",
    webauthnVerifierAddress = "C...",
    webauthnProvider = webauthnProvider
)
```

### Step 2: Host `assetlinks.json`

Create a Digital Asset Links file and serve it at:

```
https://<rp-id>/.well-known/assetlinks.json
```

Contents:

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls", "delegate_permission/common.get_login_creds"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.example.mystellarapp",
      "sha256_cert_fingerprints": [
        "AB:CD:EF:12:34:56:..."
      ]
    }
  }
]
```

Replace:
- `com.example.mystellarapp` with your app's package name.
- The `sha256_cert_fingerprints` value with your signing certificate's SHA-256 fingerprint.

To obtain the fingerprint for a debug build:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
```

For a release build, use your release keystore.

### Step 3: AndroidManifest.xml

Ensure the app has internet permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

No additional manifest configuration is needed for passkeys via Credential Manager. The association is verified at runtime by Google Play Services using the `assetlinks.json` file.

### Android Emulator

Passkeys on Android emulators require:
- Google Play Services (use a "Google APIs" system image, not "Google Play" which may be locked down).
- A Google account signed in on the emulator.
- The emulator must have network access to reach the RP domain for `assetlinks.json` verification.

For local development with `localhost` as the RP ID, the emulator cannot verify `assetlinks.json` against `localhost` in production mode. Options:
- Use a real domain with a hosted `assetlinks.json` file, even for development.
- Use Android's `adb` port forwarding and a test domain with modified `/etc/hosts`.

---

## iOS

iOS uses the AuthenticationServices framework (`ASAuthorizationPlatformPublicKeyCredentialProvider`) for passkey operations. The app must be associated with the RP domain via Associated Domains.

### Step 1: Set the RP ID

Pass the RP ID explicitly on `AppleWebAuthnProvider`:

```kotlin
val webauthnProvider = AppleWebAuthnProvider(
    rpId = "example.com",
    rpName = "My Stellar App"
)
val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "...",
    webauthnVerifierAddress = "C...",
    webauthnProvider = webauthnProvider
)
```

### Step 2: Add Associated Domains Entitlement

In your Xcode project:

1. Select the app target.
2. Go to **Signing & Capabilities**.
3. Click **+ Capability** and add **Associated Domains**.
4. Add the entry: `webcredentials:example.com`

Replace `example.com` with your RP ID domain.

The entitlement in the `.entitlements` file will look like:

```xml
<key>com.apple.developer.associated-domains</key>
<array>
    <string>webcredentials:example.com</string>
</array>
```

### Step 3: Host `apple-app-site-association`

Serve a JSON file (no `.json` extension) at:

```
https://<rp-id>/.well-known/apple-app-site-association
```

Contents:

```json
{
  "webcredentials": {
    "apps": [
      "ABCDE12345.com.example.mystellarapp"
    ]
  }
}
```

Replace:
- `ABCDE12345` with your Apple Developer Team ID.
- `com.example.mystellarapp` with your app's bundle identifier.

Requirements for this file:
- Must be served over HTTPS with a valid TLS certificate.
- Content-Type must be `application/json`.
- Must be accessible without redirects (or with at most one redirect).
- Must not require authentication.

### Step 4: Team ID

Your Apple Developer Team ID is required for the `apple-app-site-association` file. Find it at:
- [Apple Developer Portal](https://developer.apple.com/account) under Membership Details.
- Or in Xcode: select the project, go to Signing & Capabilities, and note the Team field.

### iOS Simulator

- The iOS Simulator supports passkeys starting with Xcode 14 / iOS 16 Simulator.
- Simulator passkeys are stored locally and do not sync via iCloud Keychain.
- Associated Domains verification can be bypassed for development by adding the `?mode=developer` suffix:
  ```
  webcredentials:example.com?mode=developer
  ```
  This tells iOS to use an alternate validation path that does not require the `apple-app-site-association` file to be publicly hosted. The alternate path checks a special Apple CDN used during development. See Apple documentation for [supporting associated domains](https://developer.apple.com/documentation/xcode/supporting-associated-domains).
- Even with developer mode, the Simulator must have network access.

---

## macOS

macOS uses the same AuthenticationServices framework as iOS. The configuration is identical to iOS.

### Differences from iOS

- macOS passkeys are available on macOS 13.0 (Ventura) and later.
- The same Associated Domains entitlement and `apple-app-site-association` file are required.
- macOS Compose apps and native SwiftUI apps follow the same steps.
- Touch ID or Apple Watch can serve as the biometric authenticator.
- If the Mac lacks biometrics, macOS falls back to the system password or a security key.

---

## Demo Defaults

### Web Demo

The web demo runs on `localhost` during development (e.g., `http://localhost:8081` via Vite dev server). No RP ID configuration or domain association is needed because:
- Browsers treat `localhost` as a secure context.
- The browser automatically uses `localhost` as the RP ID when none is specified.

The TypeScript reference demo uses the same approach: it passes no explicit `rpId` to its WebAuthn provider and relies on `localhost` for development.

### Android Demo

The Android demo requires:
1. An RP ID set to a domain you control (or a test domain).
2. A hosted `assetlinks.json` file at that domain.
3. A Google account signed in on the device or emulator.

There is no zero-configuration localhost equivalent on Android. For initial development and testing, consider using a staging domain with a hosted `assetlinks.json`.

### iOS/macOS Demo

The iOS and macOS demos require:
1. An RP ID set to a domain you control (or a test domain).
2. The `webcredentials:` Associated Domains entitlement added to the Xcode project.
3. An `apple-app-site-association` file hosted at that domain (or use `?mode=developer` for simulator testing).

For simulator testing during development, the `?mode=developer` suffix on the associated domain entry avoids the need for a publicly hosted association file.

---

## Custom Domain Setup (Production)

When deploying to production, replace `localhost` or test domains with your production domain.

### What Changes Per Platform

| Item | Web | Android | iOS/macOS |
|------|-----|---------|-----------|
| `rpId` on WebAuthn provider | Set to your domain | Set to your domain | Set to your domain |
| `rpName` on WebAuthn provider | Set display name | Set display name | Set display name |
| Domain file | `.well-known/webauthn` (only if cross-origin) | `.well-known/assetlinks.json` | `.well-known/apple-app-site-association` |
| App config | None | Package name + signing fingerprint in `assetlinks.json` | Team ID + bundle ID in association file |
| Entitlement | None | None | `webcredentials:<domain>` |
| TLS | Required (except localhost) | Required | Required |

### Steps

1. **Register a domain** and configure DNS to point to your server.

2. **Obtain a TLS certificate** (e.g., via Let's Encrypt) for the domain.

3. **Set the RP ID** on your platform `WebAuthnProvider` (not on `OZSmartAccountConfig`):
   ```kotlin
   // Web
   val webauthnProvider = JsWebAuthnProvider(rpId = "myapp.example.com", rpName = "My Stellar Wallet")
   // Android
   val webauthnProvider = AndroidWebAuthnProvider(context, rpId = "myapp.example.com", rpName = "My Stellar Wallet")
   // iOS / macOS
   val webauthnProvider = AppleWebAuthnProvider(rpId = "myapp.example.com", rpName = "My Stellar Wallet")
   ```

4. **Host the domain association files** as described in each platform section above. All files must be served over HTTPS.

5. **Update app identifiers**:
   - Android: Update `package_name` and `sha256_cert_fingerprints` in `assetlinks.json` for your release signing key.
   - iOS/macOS: Update the Team ID and bundle identifier in `apple-app-site-association`. Update the Associated Domains entitlement.

6. **Test on real devices**. Simulators and emulators may behave differently from physical hardware for biometric prompts and iCloud Keychain sync.

### RP ID Scope

The RP ID must be a registrable domain or a subdomain of the page origin (on web). For example:
- Page at `https://app.example.com` can use RP ID `example.com` or `app.example.com`.
- Page at `https://app.example.com` cannot use RP ID `other.com`.

Choose the RP ID carefully. Passkey credentials are permanently bound to the RP ID used at creation time. Changing the RP ID later will invalidate all existing passkeys.

### Cross-Platform Passkey Sharing

Passkeys created on one platform can be used on another if:
- The same RP ID is used on all platforms.
- The credentials are synced (e.g., via iCloud Keychain for Apple platforms, or Google Password Manager for Android and Chrome).
- The domain association files are correctly configured for each platform.

This enables a user to create a passkey on the web and later use it on iOS, or vice versa.
