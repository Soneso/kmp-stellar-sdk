# Android Smart Account Demo Setup

This document describes the Android-specific setup for the Smart Account Demo.

## What Was Updated

### 1. build.gradle.kts
- **Changed**: `minSdk = 24` → `minSdk = 28`
- **Reason**: Passkey support (Android Credential Manager API) requires API 28+ (Android 9.0 Pie)

### 2. AndroidManifest.xml
- **Added**: `<meta-data>` tag for Digital Asset Links
- **Purpose**: Links the app to a domain for passkey authentication
- **Documentation**: Includes detailed comments on how to set up `assetlinks.json`

### 3. res/values/strings.xml
- **Created**: New resource file with `asset_statements` string
- **Content**: Points to the domain's `.well-known/assetlinks.json` file
- **Note**: Replace `your-domain.example.com` with your actual domain

### 4. MainActivity.kt
- **Added**: Android provider setup for the Smart Account Kit
- **Components**:
  - `AndroidStorageAdapter(context)` - Encrypted storage using EncryptedSharedPreferences
  - `AndroidWebAuthnProvider(context, rpId, rpName)` - Passkey authentication; `rpId` and `rpName` are read from `DemoConfig`
  - Registers the WebAuthn provider and storage adapter into `DemoState` (`setWebAuthnProvider`, `setStorage`); the shared code builds `OZSmartAccountConfig` and calls `OZSmartAccountKit.create()` once the WebAuthn provider is registered (storage is optional and falls back to an in-memory adapter)
  - Registers the Reown wallet connector when not running on an emulator and `REOWN_PROJECT_ID` is set
  - Error handling with `ActivityLogState`

## Production Setup

### Step 1: Configure Your Domain

The RP ID is read from `DemoConfig.DEFAULT_RP_ID` (default `soneso.com`). To use your own domain, set it there and update the hosted `assetlinks.json` URL:
- `DEFAULT_RP_ID` in `shared/src/commonMain/kotlin/com/soneso/smartdemo/config/DemoConfig.kt`
- the `your-domain.example.com` placeholder (the `assetlinks.json` include URL) in `res/values/strings.xml`

### Step 2: Generate SHA-256 Fingerprint

Run this command to get your app's signing certificate fingerprint:
```bash
./gradlew signingReport
```

You'll see output like:
```
Variant: debug
SHA-256: AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78
```

### Step 3: Create assetlinks.json

Host this file at `https://your-domain.example.com/.well-known/assetlinks.json`:

```json
[
  {
    "relation": ["delegate_permission/common.get_login_creds"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.soneso.stellar.smartdemo.android",
      "sha256_cert_fingerprints": ["AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78"]
    }
  }
]
```

### Step 4: Verify Setup

Test your Digital Asset Links configuration:
```
https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://your-domain.example.com&relation=delegate_permission/common.get_login_creds
```

## Local Testing

For local development without a configured domain:
- The app will compile and run normally
- Passkey creation/authentication will fail with domain verification errors
- Use the iOS/Desktop/Web versions for testing without domain setup
- Or use the Android emulator with a test domain setup

## Architecture

The Android initialization follows the KMP Smart Account Kit architecture:

```
MainActivity.onCreate()
  └─> lifecycleScope.launch
       ├─> AndroidStorageAdapter(context)
       │    └─> Uses EncryptedSharedPreferences with AES-256-GCM
       ├─> AndroidWebAuthnProvider(context, rpId, rpName)
       │    └─> Uses Credential Manager API for passkeys
       └─> DemoState.setStorage(...) / DemoState.setWebAuthnProvider(...)
            └─> Shared code builds the kit once the WebAuthn provider is registered (storage optional):
                 OZSmartAccountConfig(...) -> OZSmartAccountKit.create(config)
                  ├─> Creates operation managers (wallet, transaction, signer, etc.)
                  ├─> Initializes Soroban RPC server
                  └─> Sets up relayer/indexer clients (if configured)
```

## Dependencies

Already included in the SDK:
- `androidx.credentials:credentials` - Credential Manager API for passkeys
- `androidx.security:security-crypto` - Encrypted SharedPreferences
- `org.jetbrains.kotlinx:kotlinx-coroutines-android` - Coroutines for Android

## Security Notes

1. **Encrypted Storage**: All credentials are encrypted at rest using AES-256-GCM
2. **Hardware Keystore**: Encryption keys are stored in Android Keystore (hardware-backed when available)
3. **Passkey Security**: Private keys never leave the device/secure element
4. **Domain Binding**: Digital Asset Links prevent phishing by binding the app to a verified domain
5. **Replay Protection**: Auth entries expire after a configurable number of ledgers

## Troubleshooting

### Passkeys Not Working
- Verify `minSdk = 28` in `build.gradle.kts`
- Check that `assetlinks.json` is accessible at the correct URL
- Confirm SHA-256 fingerprint matches your signing certificate
- Ensure the RP ID (`DemoConfig.DEFAULT_RP_ID`) matches the domain in assetlinks.json

### Storage Initialization Fails
- Check that device supports Android Keystore (API 24+)
- Verify `androidx.security:security-crypto` dependency is present
- Check logcat for detailed error messages

### Kit Initialization Fails
- Verify all config parameters are correct (RPC URL, WASM hash, verifier address)
- Check network connectivity
- Review `ActivityLogState` for detailed error messages
