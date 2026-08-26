# WebAuthn Setup: Android

Platform-specific guide for configuring WebAuthn passkey authentication in Android applications using the KMP Stellar SDK Smart Account Kit.

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.smartaccount.oz.*
```

## Prerequisites

- Android API 28+ (Android 9.0 Pie) for `AndroidWebAuthnProvider` (passkey authentication)
- Android API 24+ for `AndroidStorageAdapter` (credential persistence)
- A domain you control for Digital Asset Links

## Gradle Dependencies

The SDK brings the Credential Manager (`androidx.credentials`, including the Play Services backend used on devices without a native FIDO2 provider) and encrypted-storage (`androidx.security:security-crypto`) dependencies transitively — no additions to your build are required. Add them explicitly only if your own code calls those APIs directly.

## Digital Asset Links

WebAuthn on Android requires Digital Asset Links (DAL) to associate your app with your domain. The `rpId` you pass to `AndroidWebAuthnProvider` must match the domain hosting the `assetlinks.json` file.

### 1. Get Your App's SHA-256 Fingerprint

```bash
# Debug keystore
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android

# Release keystore
keytool -list -v -keystore your-release.keystore -alias your-alias
```

Copy the SHA-256 fingerprint from the output (e.g., `14:6D:E9:...`) as-is — `assetlinks.json` uses the colon-separated form.

### 2. Host assetlinks.json

Create a file at `https://your-domain.com/.well-known/assetlinks.json`:

```json
[
  {
    "relation": ["delegate_permission/common.get_login_creds"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.example.yourapp",
      "sha256_cert_fingerprints": [
        "14:6D:E9:83:C5:73:06:50:D8:EE:B9:95:2F:34:FC:64:16:A0:83:42:E6:1D:BE:A8:8A:04:96:B2:3F:CF:44:E5"
      ]
    }
  }
]
```

Serve this file with `Content-Type: application/json` over HTTPS.

### 3. Verify

Open `https://your-domain.com/.well-known/assetlinks.json` in a browser to confirm the file is accessible. Google provides a verification tool at `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://your-domain.com&relation=delegate_permission/common.get_login_creds`.

## WebAuthn Provider

`AndroidWebAuthnProvider` wraps the AndroidX Credential Manager API for passkey creation and assertion.

**Constructor parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `context` | `Context` | Yes | - | Activity context (must be an Activity, not Application) |
| `rpId` | `String` | Yes | - | Relying party domain (must match your DAL domain) |
| `rpName` | `String` | Yes | - | Display name shown during passkey prompts |
| `timeout` | `Long` | No | 60000 | Timeout in milliseconds |
| `authenticatorAttachment` | `String?` | No | `null` | `"platform"`, `"cross-platform"`, or `null` (both) |

```kotlin
import com.soneso.stellar.sdk.smartaccount.AndroidWebAuthnProvider

// In an Activity
val webauthnProvider = AndroidWebAuthnProvider(
    context = this,
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)
```

> The `context` must be an Activity context. The Credential Manager displays authentication dialogs anchored to the Activity window.

## Storage Adapter

`AndroidStorageAdapter` persists credentials and sessions using EncryptedSharedPreferences (AES-256-GCM encryption backed by the Android Keystore).

**Constructor parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `context` | `Context` | Yes | Application context (use `applicationContext`) |

```kotlin
import com.soneso.stellar.sdk.smartaccount.AndroidStorageAdapter

val storage = AndroidStorageAdapter(applicationContext)
```

> Use `applicationContext` for the storage adapter to avoid lifecycle issues. Use the Activity context for the WebAuthn provider.

## Full Kit Initialization

```kotlin
import com.soneso.stellar.sdk.smartaccount.AndroidStorageAdapter
import com.soneso.stellar.sdk.smartaccount.AndroidWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit

// In an Activity or coroutine scope
val webauthnProvider = AndroidWebAuthnProvider(
    context = this,
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)

val storage = AndroidStorageAdapter(applicationContext)

val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "your-wasm-hash-hex",
    webauthnVerifierAddress = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5",
    webauthnProvider = webauthnProvider,
    storage = storage
)

val kit = OZSmartAccountKit.create(config)
```

## Troubleshooting

### Credential Manager throws "No provider found"

The device lacks a FIDO2 provider. The Play Services Credential Manager backend ships with the SDK; ensure Google Play Services is up to date on the device.

### SecurityException or domain mismatch

The `rpId` does not match the domain in your `assetlinks.json`, or the signing certificate fingerprint does not match. Verify:
- The `rpId` matches the domain hosting `assetlinks.json`
- The SHA-256 fingerprint in `assetlinks.json` matches your signing key
- The `assetlinks.json` is served over HTTPS with correct Content-Type

### Attestation failure on emulators

Android emulators may not have hardware-backed authenticators. Passkeys on emulators require:
- A "Google APIs" system image with Google Play Services
- A Google account signed in on the emulator
- Network access to reach the RP domain for `assetlinks.json` verification

Alternatively, test on a physical device with biometric hardware.

### WebAuthnException.NotSupported

Thrown when `Build.VERSION.SDK_INT < 28`. Credential Manager requires Android 9.0 Pie. Check the API level before constructing the provider, or catch the exception and show a fallback UI.

### EncryptedSharedPreferences initialization failure

`AndroidStorageAdapter` throws `StorageException.WriteFailed` if the Android Keystore is unavailable. This can occur on rooted devices or devices without hardware-backed keystore support. Handle the exception and consider falling back to `InMemoryStorageAdapter` for testing.
