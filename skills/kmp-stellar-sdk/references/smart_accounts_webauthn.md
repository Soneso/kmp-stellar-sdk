# Smart Accounts — WebAuthn Providers and Storage Adapters

Platform-specific classes that supply `WebAuthnProvider` and `StorageAdapter` to `OZSmartAccountConfig`. Core kit API lives in [smart_accounts.md](./smart_accounts.md); [api_reference.md](./api_reference.md) carries a flat signature index (platform classes tagged `androidMain` / `nativeMain` / `jsMain`).

Imports:

```kotlin
// Common interfaces and result types (all platforms)
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnRegistrationResult
import com.soneso.stellar.sdk.smartaccount.oz.WebAuthnAuthenticationResult
import com.soneso.stellar.sdk.smartaccount.oz.AllowCredential
import com.soneso.stellar.sdk.smartaccount.oz.StorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.InMemoryStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredential
import com.soneso.stellar.sdk.smartaccount.oz.StoredSession
import com.soneso.stellar.sdk.smartaccount.oz.StoredCredentialUpdate
import com.soneso.stellar.sdk.smartaccount.oz.CredentialDeploymentStatus
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException
import com.soneso.stellar.sdk.smartaccount.core.StorageException
import com.soneso.stellar.sdk.smartaccount.core.CredentialException

// Platform adapters live in com.soneso.stellar.sdk.smartaccount (NOT the .oz sub-package)
import com.soneso.stellar.sdk.smartaccount.AndroidWebAuthnProvider        // androidMain
import com.soneso.stellar.sdk.smartaccount.AndroidStorageAdapter          // androidMain
import com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider          // nativeMain (iOS + macOS)
import com.soneso.stellar.sdk.smartaccount.KeychainStorageAdapter         // nativeMain
import com.soneso.stellar.sdk.smartaccount.UserDefaultsStorageAdapter     // nativeMain
import com.soneso.stellar.sdk.smartaccount.JsWebAuthnProvider             // jsMain
import com.soneso.stellar.sdk.smartaccount.IndexedDBStorageAdapter        // jsMain
import com.soneso.stellar.sdk.smartaccount.LocalStorageAdapter            // jsMain
```

```kotlin
// WRONG: import com.soneso.stellar.sdk.smartaccount.oz.AndroidWebAuthnProvider
// CORRECT: import com.soneso.stellar.sdk.smartaccount.AndroidWebAuthnProvider
//   — platform adapters are NOT in the .oz sub-package; only the interfaces and
//     InMemoryStorageAdapter are.
```

## Overview

The SDK ships platform-specific WebAuthn providers and storage adapters under `expect`/`actual` source sets — your KMP target selects the pair at compile time.

| Platform | WebAuthn Provider | WebAuthn API | Min Version | Storage Options |
|----------|-------------------|--------------|-------------|-----------------|
| Android | `AndroidWebAuthnProvider` | Credential Manager (`androidx.credentials`) | API 28 (Android 9 Pie) | `AndroidStorageAdapter` (API 24+) |
| iOS | `AppleWebAuthnProvider` | AuthenticationServices | iOS 16 | `KeychainStorageAdapter`, `UserDefaultsStorageAdapter` |
| macOS | `AppleWebAuthnProvider` | AuthenticationServices | macOS 13 (Ventura) | `KeychainStorageAdapter`, `UserDefaultsStorageAdapter` |
| Web / Browser | `JsWebAuthnProvider` | `navigator.credentials` | Chrome 67+, Firefox 60+, Safari 14+, Edge 79+ | `IndexedDBStorageAdapter`, `LocalStorageAdapter` |
| JVM server / Node.js | — (no provider) | — | — | `InMemoryStorageAdapter` or custom |

The kit requires a `webauthnProvider` for every call that creates or signs with a passkey (`createWallet`, `transfer`, `contractCall`, `executeAndSubmit`, signer changes). Without one, the SDK throws `WebAuthnException.NotSupported`. Pure read-only flows (`getContract`, event streaming, credential inspection) do not need a provider.

```kotlin
// WRONG: building a kit without a provider and calling createWallet()
// Result: WebAuthnException.NotSupported at the first biometric prompt
// CORRECT: always configure config.webauthnProvider when state-changing operations are used
```

---

## Common Interfaces (commonMain)

The interfaces and data classes below live in `com.soneso.stellar.sdk.smartaccount.oz` and are shared across every platform. Custom providers and storage adapters implement these.

### `WebAuthnProvider` interface

```kotlin
interface WebAuthnProvider {
    suspend fun register(
        challenge: ByteArray,            // typically 32 bytes, passed as-is to the authenticator
        userId: ByteArray,               // discoverable-credential user handle
        userName: String                 // shown during the passkey prompt
    ): WebAuthnRegistrationResult

    suspend fun authenticate(
        challenge: ByteArray,            // auth payload hash, passed as-is
        allowCredentials: List<AllowCredential>? = null
    ): WebAuthnAuthenticationResult
}
```

```kotlin
// WRONG: provider.register("challenge-string", ...)  — challenge is ByteArray, not String
// CORRECT: provider.register(challengeBytes, userIdBytes, "Alice")

// WRONG: provider.authenticate(challenge, allowCredentialIds = listOf(idBytes))
//   — allowCredentialIds was removed in SDK 1.5.0
// CORRECT: provider.authenticate(challenge, allowCredentials = AllowCredential.fromIds(listOf(idBytes)))
```

### `WebAuthnRegistrationResult`

```kotlin
data class WebAuthnRegistrationResult(
    val credentialId: ByteArray,         // raw bytes (NOT Base64URL)
    val publicKey: ByteArray,            // 65 bytes uncompressed secp256r1 (0x04 + X + Y)
    val attestationObject: ByteArray,    // raw CBOR attestation object
    val transports: List<String>? = null, // e.g. ["internal"], ["hybrid", "usb"]
    val deviceType: String? = null,      // "singleDevice" or "multiDevice"
    val backedUp: Boolean? = null        // true if the passkey is cloud-synced
)
```

```kotlin
// WRONG: result.publicKey.size == 33  — that would be the compressed point form
// CORRECT: result.publicKey.size == 65 && result.publicKey[0] == 0x04.toByte()

// Providers must return the 65-byte uncompressed key. The SDK's COSE/SPKI
// extraction fallback applies only during createWallet; addNewPasskeySigner
// validates publicKey strictly (65 bytes, 0x04 prefix).
```

### `WebAuthnAuthenticationResult`

```kotlin
data class WebAuthnAuthenticationResult(
    val credentialId: ByteArray,
    val authenticatorData: ByteArray,    // >= 37 bytes: rpIdHash(32) + flags(1) + signCount(4) + ...
    val clientDataJSON: ByteArray,       // contains challenge as base64url, no padding
    val signature: ByteArray             // DER-encoded ECDSA — SDK normalizes to compact low-S
)
```

The kit calls `SmartAccountUtils.normalizeSignature(signature)` internally to convert the DER signature to the 64-byte compact `r || s` form required by Soroban. Providers return DER as delivered by the platform.

### `AllowCredential`

```kotlin
data class AllowCredential(
    val id: ByteArray,                   // raw credential ID bytes
    val transports: List<String>? = null // "internal" | "hybrid" | "usb" | "ble" | "nfc"
) {
    companion object {
        fun fromId(id: ByteArray): AllowCredential
        fun fromIds(ids: List<ByteArray>): List<AllowCredential>
    }
}
```

Include transport hints when you know them — e.g. `"hybrid"` enables the browser/OS cross-device QR flow, `"internal"` restricts to the current device's platform authenticator. When `transports` is null the authenticator picks defaults.

### `StorageAdapter` interface

All adapters implement the same contract. Method names are short (`save`/`get`/`delete`), not `saveCredential`/`getCredential`.

```kotlin
interface StorageAdapter {
    // Credentials
    suspend fun save(credential: StoredCredential)
    suspend fun get(credentialId: String): StoredCredential?
    suspend fun getByContract(contractId: String): List<StoredCredential>
    suspend fun getAll(): List<StoredCredential>
    suspend fun update(credentialId: String, updates: StoredCredentialUpdate)
    suspend fun delete(credentialId: String)
    suspend fun clear()
    // Sessions
    suspend fun saveSession(session: StoredSession)
    suspend fun getSession(): StoredSession?
    suspend fun clearSession()
}
```

```kotlin
// WRONG: storage.saveCredential(cred)  — method is named save(cred)
// CORRECT: storage.save(cred)
// WRONG: storage.getCredential("abc")  — method is named get(id)
// CORRECT: storage.get("abc")
// WRONG: storage.getAllCredentials()  — method is named getAll()
// CORRECT: storage.getAll()
// WRONG: storage.deleteCredential("abc")  — method is named delete(id)
// CORRECT: storage.delete("abc")
```

`update` applies a partial `StoredCredentialUpdate`: non-null fields overwrite, null fields are left unchanged. There is no way to set a previously set field back to null via `update` — call `save` with a full replacement credential if you need that.

`getSession()` returns null when no session exists **and also when the stored session is expired** (the adapter clears expired sessions on read). After app restart, always check the return value.

### `InMemoryStorageAdapter`

Tests and ephemeral flows only. Every instance compares equal to every other instance (so `OZSmartAccountConfig` copies with default storage behave correctly in unit tests).

```kotlin
val storage = InMemoryStorageAdapter()  // default in OZSmartAccountConfig

// WRONG: shipping production with InMemoryStorageAdapter  — credentials lost on restart
// CORRECT: production apps wire a platform adapter (see below)
```

---

## Android

`AndroidWebAuthnProvider` wraps `androidx.credentials.CredentialManager`. `AndroidStorageAdapter` wraps `EncryptedSharedPreferences` (AES-256-GCM values / AES-256-SIV keys) backed by the Android Keystore.

### Prerequisites

- Google Play Services on the device (for `credentials-play-services-auth` on devices without a native FIDO2 provider)
- A domain you control for Digital Asset Links

### Gradle dependencies

```kotlin
// app-level build.gradle.kts
dependencies {

    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("androidx.security:security-crypto:1.1.0")
}
```

### Digital Asset Links (assetlinks.json)

Passkeys on Android require the `rpId` you pass to `AndroidWebAuthnProvider` to match a domain that publishes a Digital Asset Links statement for your app.

1. Extract the SHA-256 fingerprint of your signing key:

```bash
# Debug keystore (default path on macOS/Linux)
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android

# Release keystore
keytool -list -v -keystore your-release.keystore -alias your-alias
```

2. Host the file at `https://your-domain.com/.well-known/assetlinks.json`:

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

Serve with HTTPS, `Content-Type: application/json`, and no redirects.

```kotlin
// WRONG: "sha256_cert_fingerprints": ["146DE983C573..."]  — no colons
// CORRECT: colon-separated uppercase hex as printed by keytool (preserve the colons)
// WRONG: rpId = "https://your-domain.com"
// CORRECT: rpId = "your-domain.com"  — no scheme, no trailing slash, no path
```

3. Verify the file resolves via Google's Asset Links API:

```
https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://your-domain.com&relation=delegate_permission/common.get_login_creds
```

Digital Asset Links are not needed for non-passkey builds, but `AndroidWebAuthnProvider.register()` and `.authenticate()` both fail with a `SecurityException` (mapped to `WebAuthnException.RegistrationFailed` / `AuthenticationFailed`) if the fingerprint or domain mismatch.

### `AndroidWebAuthnProvider`

```kotlin
class AndroidWebAuthnProvider(
    private val context: Context,                           // Activity context — see note
    private val rpId: String,
    private val rpName: String,
    private val timeout: Long = 60_000L,                    // OZConstants.WEBAUTHN_TIMEOUT_MS
    private val authenticatorAttachment: String? = null     // "platform" | "cross-platform" | null
) : WebAuthnProvider
```

`authenticatorAttachment = null` (default) allows both platform (biometric/screen-lock) and cross-platform (security key) authenticators, matching the JS provider's behaviour. Set to `"platform"` to restrict to built-in authenticators or `"cross-platform"` for security keys only.

```kotlin
// In an Activity (not an Application subclass)
class WalletActivity : AppCompatActivity() {
    private val webauthn by lazy {
        AndroidWebAuthnProvider(
            context = this,                       // Activity context: dialogs anchor to this window
            rpId = "your-domain.com",
            rpName = "My Stellar Wallet"
        )
    }
    // ... use lifecycleScope.launch { ... } to call suspend methods
}
```

```kotlin
// WRONG: AndroidWebAuthnProvider(context = applicationContext, rpId = ...)
//   Credential Manager needs an Activity context to anchor its bottom sheet. Using
//   Application context throws an IllegalArgumentException at request time.
// CORRECT: pass `this` (or `requireActivity()` in a Fragment)

// WRONG: AndroidWebAuthnProvider(rpId = "https://your-domain.com", ...)
// CORRECT: rpId = "your-domain.com"  — bare domain only
```

### `AndroidStorageAdapter`

```kotlin
class AndroidStorageAdapter(context: Context) : StorageAdapter
```

The adapter's constructor throws `StorageException.WriteFailed` if the Android Keystore is unavailable (rare — happens on some rooted devices or devices without hardware-backed keystore).

```kotlin
// In Application.onCreate() or a DI module
val storage: StorageAdapter = AndroidStorageAdapter(applicationContext)
```

```kotlin
// WRONG: AndroidStorageAdapter(this)  where `this` is the Activity
//   — ties storage to the Activity lifecycle; leaks on configuration changes
// CORRECT: AndroidStorageAdapter(applicationContext)  — singleton-scoped
```

### Context handling summary

| Class | Use |
|-------|-----|
| `AndroidWebAuthnProvider` | Activity context |
| `AndroidStorageAdapter` | Application context (`applicationContext`) |

### AndroidManifest.xml

`AndroidWebAuthnProvider` and `AndroidStorageAdapter` require no manifest entries. Credential Manager uses the system biometric APIs via Play Services without explicit permissions. The SDK's Soroban RPC / Horizon traffic needs the usual `<uses-permission android:name="android.permission.INTERNET" />`.

### Full kit initialization (Android)

```kotlin
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.soneso.stellar.sdk.smartaccount.AndroidStorageAdapter
import com.soneso.stellar.sdk.smartaccount.AndroidWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var kit: OZSmartAccountKit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webauthn = AndroidWebAuthnProvider(
            context = this,
            rpId = "your-domain.com",
            rpName = "My Stellar Wallet"
        )
        val storage = AndroidStorageAdapter(applicationContext)

        val config = OZSmartAccountConfig(
            rpcUrl = "https://soroban-testnet.stellar.org",
            networkPassphrase = "Test SDF Network ; September 2015",
            accountWasmHash = "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456",
            webauthnVerifierAddress = "<C-address of the WebAuthn verifier>",
            webauthnProvider = webauthn,
            storage = storage
        )
        kit = OZSmartAccountKit.create(config)

        lifecycleScope.launch {
            val restored = kit.walletOperations.connectWallet()
            // restored == null -> show Connect button; otherwise we're logged in
        }
    }

    override fun onDestroy() {
        kit.close()
        super.onDestroy()
    }
}
```

### Troubleshooting (Android)

- **"No credential provider found"** — Device lacks a FIDO2 provider. Ensure `credentials-play-services-auth` is in dependencies and Play Services is up to date. On emulators, use an image with Play Services.
- **`SecurityException` / domain mismatch** — `rpId` does not match `assetlinks.json`, or the SHA-256 fingerprint in the file does not match the app's signing key, or the file is not served over HTTPS with `application/json`. Recompute the fingerprint with `keytool` against the actual APK's signing keystore.
- **Attestation failure on emulators** — Many emulator images lack hardware-backed authenticators. Use API 33+ emulators with Play Services, or switch to a physical device. The kit's `attestation = "direct"` can fall back to software attestation but requires a FIDO2 provider.
- **`WebAuthnException.NotSupported`** — `AndroidWebAuthnProvider.init` throws this on `Build.VERSION.SDK_INT < 28`. Gate the provider behind a runtime check and show a graceful fallback:

```kotlin
val webauthn = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
    AndroidWebAuthnProvider(this, rpId, rpName)
} else {
    null  // Disable passkey features in the UI
}
```

- **`StorageException.WriteFailed` during `AndroidStorageAdapter` init** — Keystore unavailable (rooted / custom ROM). Fall back to `InMemoryStorageAdapter` for read-only or test builds; do not use it in production.
- **Credential Manager prompt dismisses without callback** — The calling Activity lost focus (background / rotation). `authenticate()` throws `WebAuthnException.Cancelled`. Use `LaunchedEffect` or `lifecycleScope` so the coroutine cancels cleanly on config changes.

---

## iOS

`AppleWebAuthnProvider` lives in `nativeMain` and is shared by iOS and macOS. It wraps `ASAuthorizationPlatformPublicKeyCredentialProvider` from AuthenticationServices.

### Prerequisites

- Apple Developer account with "Associated Domains" capability
- A domain you control for `apple-app-site-association`

### SPM dependencies

The SDK needs libsodium for crypto. Add Clibsodium via Swift Package Manager:

1. In Xcode: File > Add Package Dependencies
2. URL: `https://github.com/jedisct1/swift-sodium`
3. Select "Up to Next Major Version" and add the `Clibsodium` product to the app target

No extra dependency is needed for WebAuthn — `AppleWebAuthnProvider` uses the built-in AuthenticationServices framework.

### Associated Domains (apple-app-site-association)

1. In Xcode: Target > Signing & Capabilities > `+ Capability` > "Associated Domains". Add:

```
webcredentials:your-domain.com
```

```
// WRONG: webcredentials:https://your-domain.com
// CORRECT: webcredentials:your-domain.com        — bare domain, no scheme
// For development: webcredentials:your-domain.com?mode=developer   — bypasses Apple CDN cache
```

2. Host at `https://your-domain.com/.well-known/apple-app-site-association`:

```json
{
  "webcredentials": {
    "apps": [
      "TEAM_ID.com.example.yourapp"
    ]
  }
}
```

Replace `TEAM_ID` with your Apple Developer Team ID (Apple Developer portal > Membership) and the bundle identifier with your actual one.

Serving requirements:
- HTTPS only
- `Content-Type: application/json`
- No `.json` extension in the URL path
- No redirects (Apple's CDN follows them but validation fails)

3. Verify after install by tailing `Console.app` on the device and filtering for `swcd` (the Shared Web Credentials Daemon). Warnings there explain why association failed.

### `AppleWebAuthnProvider`

```kotlin
class AppleWebAuthnProvider(
    private val rpId: String,
    private val rpName: String,
    private val timeout: Long = 60_000L
) : WebAuthnProvider {
    // presentationContextProvider is optional on iOS, REQUIRED on macOS — see macOS section
    var presentationContextProvider: ASAuthorizationControllerPresentationContextProvidingProtocol? = null

    companion object {
        fun create(rpId: String, rpName: String, timeout: Long = 60_000L): AppleWebAuthnProvider
    }
}
```

```kotlin
// Constructor or factory both work
val webauthn = AppleWebAuthnProvider(
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)
// or: AppleWebAuthnProvider.create(rpId = "your-domain.com", rpName = "My Stellar Wallet")
```

```kotlin
// WRONG: rpId = "https://your-domain.com"
// CORRECT: rpId = "your-domain.com"   — bare domain; the "webcredentials:" entitlement
//   value uses the same bare form
```

Transports returned by `register()` are hard-coded to `listOf("internal")` on Apple platforms — the platform authenticator is always the Secure Enclave / iCloud Keychain. Apple's API has no transport parameter on its credential descriptor, so transport hints on `AllowCredential` are accepted by `authenticate()` but ignored at the OS boundary; hybrid/QR flows are handled by Apple's own UI.

### Storage adapters (iOS)

```kotlin
// Simpler, less strict data protection. Adequate for stored public keys.
class UserDefaultsStorageAdapter(
    suiteName: String = "com.soneso.stellar.smartaccount"
) : StorageAdapter

// Stronger: Keychain Services with kSecAttrAccessibleAfterFirstUnlock.
// Survives reinstall (unless explicitly deleted) and optionally syncs via iCloud Keychain.
class KeychainStorageAdapter(
    serviceName: String = "com.soneso.stellar.smartaccount"
) : StorageAdapter
```

Stored credentials contain **public** keys only (no secret material), so `UserDefaultsStorageAdapter` is sufficient for most apps. Use `KeychainStorageAdapter` when you need:

- Stronger protection class (Keychain encryption at rest)
- Survival across app uninstall/reinstall
- Cross-device sync via iCloud Keychain
- Shared access group with a widget or sibling app

```kotlin
val storage = KeychainStorageAdapter(serviceName = "com.yourapp.stellar")
```

### Full kit initialization (iOS, called from Kotlin)

Typical iOS apps drive the kit from Swift, but every method is callable from Kotlin/Native too:

```kotlin
import com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.KeychainStorageAdapter
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit

val webauthn = AppleWebAuthnProvider(
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)
val storage = KeychainStorageAdapter(serviceName = "com.yourapp.stellar")
// Config and kit creation are identical to the Android example above.
```

From Swift (passing the shared framework into SwiftUI or UIKit):

```swift
import SharedKmp  // your KMP module's Kotlin framework

let webauthn = AppleWebAuthnProvider(
    rpId: "your-domain.com",
    rpName: "My Stellar Wallet",
    timeout: 60_000
)
let storage = KeychainStorageAdapter(serviceName: "com.yourapp.stellar")

// Use OZSmartAccountConfig.Companion.builder(...) from Swift for a cleaner call
// site than the long Kotlin data-class constructor.
let config = OZSmartAccountConfig.Companion().builder(
    rpcUrl: "https://soroban-testnet.stellar.org",
    networkPassphrase: "Test SDF Network ; September 2015",
    accountWasmHash: "a1b2c3...",
    webauthnVerifierAddress: "CBCD..."
)
    .webauthnProvider(webauthn)
    .storage(storage)
    .build()

let kit = OZSmartAccountKit.Companion().create(config: config)
```

### Troubleshooting (iOS)

- **Passkeys on the iOS Simulator** — Supported since the iOS 16 Simulator (Xcode 14). Simulator passkeys are stored locally and do not sync via iCloud Keychain; associated-domain validation still applies (`?mode=developer` during development).
- **ASAuthorization error 1001 (cancelled)** — User dismissed the system sheet. Maps to `WebAuthnException.Cancelled`. Surface as a neutral UI state, not an error.
- **ASAuthorization error 1004 (failed)** — Usually an associated-domains problem. Verify:
  - `rpId` matches the `webcredentials:` entitlement value (bare domain both)
  - `apple-app-site-association` is reachable at the well-known path with HTTPS and `application/json`
  - Provisioning profile includes the Associated Domains capability (regenerate after adding)
  - Run a first launch with `?mode=developer` on the entitlement to bypass CDN caching
- **First-install delay** — After install, Apple's CDN may take up to a minute to fetch the association file. Retry `register()` or wait for `swcd` to log success.
- **Credential not found on `authenticate()`** — No passkey exists for this `rpId` on the device. Prompt `createWallet()` first, or enable iCloud Keychain and use a passkey synced from another device.
- **`WebAuthnException.RegistrationFailed: operation timed out`** — Default timeout is 60 s. User ignored the sheet. Increase via constructor if needed but surface the retry in the UI rather than silently re-prompting.

---

## macOS

`AppleWebAuthnProvider` is the same class as on iOS; differences are in entitlements, signing, and the **required** presentation context.

### Prerequisites

- Developer ID signing **or** the App Sandbox entitlement for associated domains (macOS ignores AASA without one)
- A domain you control for `apple-app-site-association`

### libsodium dependency

macOS links the system libsodium from Homebrew (`brew install libsodium`); add `/opt/homebrew/opt/libsodium/lib` to Library Search Paths and `-lsodium` to Other Linker Flags of a native Swift app target.

### Associated Domains on macOS

macOS adds signing constraints over iOS:

| Distribution | Requirement |
|--------------|-------------|
| Mac App Store / sandboxed | App Sandbox capability enabled |
| Outside the App Store | Developer ID signing |

Without one of these, macOS will not fetch or validate `apple-app-site-association` at all.

In Xcode:

1. Target > Signing & Capabilities > `+ Capability` > "Associated Domains"
2. Add `webcredentials:your-domain.com` (append `?mode=developer` for local testing to bypass CDN caching)
3. Also enable `+ Capability` > "App Sandbox" (for sandboxed builds)

Host the same `apple-app-site-association` JSON as on iOS; one file covers both platforms when bundle IDs and Team ID match.

```kotlin
// WRONG: testing against `localhost` on macOS
//   — macOS requires a verified DAL domain; there's no localhost exemption
// CORRECT: use a real domain (mkcert + hosts file for development, or a staging domain)
```

### `presentationContextProvider` is required on macOS

Unlike iOS, `ASAuthorizationController` on macOS requires a presentation context provider to anchor the passkey sheet to an `NSWindow`. Without it the system fails with error code 1004 ("no host window provided").

```kotlin
// The provider is declared as a property on AppleWebAuthnProvider:
var presentationContextProvider: ASAuthorizationControllerPresentationContextProvidingProtocol? = null
```

Assign from Swift before you trigger any kit operation that signs:

```swift
class WindowProvider: NSObject, ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        return NSApplication.shared.keyWindow ?? NSWindow()
    }
}

let webauthn = AppleWebAuthnProvider(rpId: "your-domain.com", rpName: "My Wallet")
webauthn.presentationContextProvider = WindowProvider()
```

The `presentationContextProvider` property retains the `WindowProvider`; keep the `AppleWebAuthnProvider` itself alive across the ceremony.

```kotlin
// WRONG (macOS): creating AppleWebAuthnProvider without setting presentationContextProvider
//   Result: register/authenticate throws WebAuthnException.RegistrationFailed /
//           AuthenticationFailed wrapping "Authenticator operation failed" (code 1004)
// CORRECT: set provider.presentationContextProvider from Swift before any sign operation
```

### Entitlements for macOS

```xml
<!-- YourApp.entitlements -->
<key>com.apple.developer.associated-domains</key>
<array>
    <string>webcredentials:your-domain.com</string>
</array>

<!-- For sandboxed builds: -->
<key>com.apple.security.app-sandbox</key>
<true/>

<!-- Network access for Soroban RPC and Horizon: -->
<key>com.apple.security.network.client</key>
<true/>

<!-- Optional: keychain access group if KeychainStorageAdapter shares with sibling apps -->
<key>keychain-access-groups</key>
<array>
    <string>TEAMID.com.yourapp.shared</string>
</array>
```

### Storage adapters (macOS)

`UserDefaultsStorageAdapter` and `KeychainStorageAdapter` are the same classes as iOS. Consider giving macOS builds a different `suiteName` or `serviceName` to keep stores separate from an iOS companion app that ships the same Bundle ID family.

```kotlin
val storage = UserDefaultsStorageAdapter(
    suiteName = "com.yourapp.stellar.macos"
)
// or: KeychainStorageAdapter(serviceName = "com.yourapp.stellar.macos")
```

### Full kit initialization (macOS)

```kotlin
// In a Kotlin/Native source set shared with iosMain via nativeMain, or called from Swift.
val webauthn = AppleWebAuthnProvider(
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)
// IMPORTANT on macOS: set presentationContextProvider from the Swift side

val storage = UserDefaultsStorageAdapter(
    suiteName = "com.yourapp.stellar.macos"
)
// Config and kit creation are identical to the Android example above.
```

### Troubleshooting (macOS)

- **AASA file not fetched / validated** — Use `?mode=developer` on the domain entry during development (bypasses Apple's CDN cache) and confirm the entitlement is present in the built product (`codesign -d --entitlements - YourApp.app`). No sandbox or special signing setup is required.
- **ASAuthorization error 1004 ("no host window")** — `presentationContextProvider` unset or the anchor's `NSWindow` is nil (app minimized, main window closed). Hold a strong reference to the provider and ensure a visible key window before calling kit sign operations.
- **Passkeys synced from iOS missing** — Verify iCloud Keychain is enabled on both devices, both are signed into the same Apple ID, and `rpId` matches exactly.
- **Sandbox keychain restrictions** — Sandboxed apps have restricted Keychain access. If `KeychainStorageAdapter` throws unexpected `OSStatus` codes, add `keychain-access-groups` to the entitlements (matching the service name) or fall back to `UserDefaultsStorageAdapter`.
- **Local testing against `localhost`** — Not supported. macOS validates associated domains against a real HTTPS domain with a reachable AASA file. Use a real staging domain or mkcert with a `/etc/hosts` mapping.
- **Hardened runtime / codesigning** — Hardened runtime is required for distribution but does not affect WebAuthn. Codesign warnings during `archive` usually point at an un-notarized helper; fix by ensuring every bundled binary has a valid signature.

---

## Web / JavaScript

`JsWebAuthnProvider` calls `navigator.credentials.create()` and `navigator.credentials.get()`. It is browser-only — in Node.js it throws `WebAuthnException.NotSupported` because `navigator.credentials` is not defined.

### Prerequisites

- HTTPS in production; `localhost` is the only plaintext exception allowed by browsers
- No npm/gradle dependency is required for WebAuthn — it is a browser-native API

### Relying Party rules (browser)

The `rpId` must be either the current origin's hostname or a registrable parent domain of it.

| Page origin | Valid `rpId` | Invalid `rpId` |
|-------------|--------------|----------------|
| `https://app.example.com` | `"app.example.com"`, `"example.com"` | `"other.com"` (SecurityError), `"com"` (public suffix) |
| `http://localhost:8080` | `"localhost"` | `"127.0.0.1"` (WebAuthn treats this as a separate origin) |

```kotlin
// WRONG: rpId on a public suffix
// val provider = JsWebAuthnProvider(rpId = "com", rpName = ...)  — SecurityError at ceremony

// WRONG: page at https://app.example.com trying rpId = "other.com"
//   — cannot set an rpId that isn't a registrable suffix of the current origin
// CORRECT: page at https://app.example.com -> rpId = "app.example.com" OR "example.com"

// WRONG: rpId = "https://example.com"
// CORRECT: rpId = "example.com"
```

### `JsWebAuthnProvider`

```kotlin
class JsWebAuthnProvider(
    private val rpId: String,
    private val rpName: String,
    private val timeout: Long = 60_000L
) : WebAuthnProvider
```

```kotlin
val webauthn = JsWebAuthnProvider(
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)
```

Unlike the other platform providers, `JsWebAuthnProvider` does not silently degrade in Node.js — tests that exercise it must run in a browser-like environment (Karma, Playwright, Puppeteer, jsdom with a polyfill) or mock the provider entirely.

### Storage adapters (Web)

```kotlin
// Recommended for production: structured, async, multi-megabyte storage
class IndexedDBStorageAdapter(
    dbName: String = "stellar_smart_account"
) : StorageAdapter {
    suspend fun close()    // optional: release the connection, reopens lazily on next op
}

// Simpler key/value; ~5 MB per origin; synchronous under the hood
class LocalStorageAdapter(
    keyPrefix: String = "stellar_sa_"
) : StorageAdapter
```

```kotlin
// Sensible fallback pattern (private browsing sometimes breaks IndexedDB)
val storage: StorageAdapter = try {
    IndexedDBStorageAdapter()
} catch (e: StorageException.ReadFailed) {
    LocalStorageAdapter()
}
```

`IndexedDBStorageAdapter` creates an object store `credentials` (keyPath `credentialId`, indexes incl. `contractId`) and `sessions` (keyPath `key`). The version-1 schema is managed by the adapter — do not open the database yourself with a different version.

### Localhost development

Browsers treat `http://localhost` as a secure context for WebAuthn. Use `rpId = "localhost"` during development:

```kotlin
val webauthn = JsWebAuthnProvider(
    rpId = "localhost",           // works for http://localhost:8080
    rpName = "Dev Stellar Wallet"
)
```

```kotlin
// WRONG: using 127.0.0.1 with rpId = "localhost"
// CORRECT: visit http://localhost:PORT in the browser — the hostname must match rpId
//   (browsers consider 127.0.0.1 a different origin for WebAuthn purposes)
```

Passkeys created with `rpId = "localhost"` do not work on your production domain — they are separate credentials. Create per-environment passkeys or use a real HTTPS domain with mkcert locally.

### HTTPS requirement in production

Browsers reject `navigator.credentials.create/get` on plain HTTP origins (except `localhost`). The request fails with `SecurityError`, mapped to `WebAuthnException.RegistrationFailed` / `AuthenticationFailed`. Deploy behind TLS.

### Full kit initialization (Web)

```kotlin
// commonMain or jsMain
import com.soneso.stellar.sdk.smartaccount.IndexedDBStorageAdapter
import com.soneso.stellar.sdk.smartaccount.JsWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

val scope = MainScope()

fun bootstrap() {
    val webauthn = JsWebAuthnProvider(
        rpId = "your-domain.com",
        rpName = "My Stellar Wallet"
    )
    val storage = IndexedDBStorageAdapter(dbName = "my_app_smart_account")

    // Config and kit creation are identical to the Android example above.
    val kit = OZSmartAccountKit.create(config)

    scope.launch {
        when (val restored = kit.walletOperations.connectWallet()) {
            null -> { /* no saved session */ }
            is ConnectWalletResult.Connected -> {
                console.log("Reconnected: ${restored.contractId}")
            }
            is ConnectWalletResult.Ambiguous -> {
                // Unreachable for the silent restore path
            }
        }
    }
}
```

### Troubleshooting (Web)

- **`SecurityError: rpId does not match the current origin`** — Page domain and `rpId` are misaligned. The `rpId` must be the exact hostname or a registrable parent.
- **`NotAllowedError`** — Mapped to `WebAuthnException.Cancelled`. User dismissed the prompt, the page lost focus mid-ceremony, or an extension intercepted the request. Re-prompt on user action.
- **Insecure-origin error on HTTP** — Browser rejects the ceremony. Serve over HTTPS (production) or use `http://localhost:PORT` with `rpId = "localhost"` for dev.
- **`WebAuthnException.NotSupported` in Node.js** — `JsWebAuthnProvider` cannot run outside a browser. For SSR or server tests, mock the `WebAuthnProvider` interface.
- **Cross-origin iframe blocked** — Add `allow="publickey-credentials-create; publickey-credentials-get"` on the iframe element and serve a matching `Permissions-Policy` header from both the parent and the iframe.
- **Safari private mode / IndexedDB unavailable** — IndexedDB may throw on open. Catch and fall back to `LocalStorageAdapter`:

```kotlin
val storage = try {
    IndexedDBStorageAdapter()
} catch (e: Exception) {
    LocalStorageAdapter()
}
```

- **Passkeys not syncing** — Sync depends on the browser: Chrome (Google Password Manager when signed in), Safari (iCloud Keychain), Firefox (local only at time of writing). Identical `rpId` required across environments.

---

## Choosing a StorageAdapter

| Platform | Recommended (production) | Fallback | Never in production |
|----------|--------------------------|----------|---------------------|
| Android  | `AndroidStorageAdapter` (EncryptedSharedPreferences + Keystore) | `InMemoryStorageAdapter` for unit tests | `InMemoryStorageAdapter` in release builds |
| iOS      | `KeychainStorageAdapter` (Keychain, optional iCloud sync) | `UserDefaultsStorageAdapter` (public data only) | `InMemoryStorageAdapter` |
| macOS    | `KeychainStorageAdapter` or `UserDefaultsStorageAdapter` (per distribution) | `UserDefaultsStorageAdapter` with dedicated suite | `InMemoryStorageAdapter` |
| Web      | `IndexedDBStorageAdapter` | `LocalStorageAdapter` (small data, private-mode fallback) | `InMemoryStorageAdapter` |
| JVM (server) | Custom `StorageAdapter` (JDBC/Redis/etc.) | — | `InMemoryStorageAdapter` unless stateless |

Because `StoredCredential` contains **public keys only** (no secret material), the security bar is lower than for private key storage — but session tokens and contract IDs are still privacy-sensitive. Use platform encryption where it exists.

### Implementing a custom `StorageAdapter`

For JVM servers or unusual platforms, implement the interface directly. Minimal skeleton:

```kotlin
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.smartaccount.core.CredentialException
import com.soneso.stellar.sdk.smartaccount.core.StorageException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JdbcStorageAdapter(private val db: MyDatabase) : StorageAdapter {
    private val mutex = Mutex()

    override suspend fun save(credential: StoredCredential) = mutex.withLock {
        try { db.upsertCredential(credential) }
        catch (e: Exception) { throw StorageException.WriteFailed("save failed", e) }
    }

    override suspend fun get(credentialId: String) = mutex.withLock {
        db.loadCredential(credentialId)
    }

    override suspend fun update(credentialId: String, updates: StoredCredentialUpdate) = mutex.withLock {
        val existing = db.loadCredential(credentialId)
            ?: throw CredentialException.notFound(credentialId)
        db.upsertCredential(existing.applyUpdate(updates))
    }

    override suspend fun getSession(): StoredSession? = mutex.withLock {
        val s = db.loadSession() ?: return@withLock null
        if (s.isExpired) { db.deleteSession(); null } else s
    }

    // ... getByContract, getAll, delete, clear, saveSession, clearSession follow the same pattern
}
```

Key contracts to satisfy:

- **Thread safety** — multiple coroutines may hit the adapter concurrently. Use `Mutex`, per-row locking, or an ACID store.
- **Expired-session read** — `getSession()` must return null when `StoredSession.isExpired` is true, and should clear the stored row.
- **`update` partial semantics** — apply non-null fields only via `StoredCredential.applyUpdate(updates)`; do not overwrite existing values with null.
- **Exceptions** — wrap underlying errors in `StorageException.ReadFailed` / `WriteFailed` and throw `CredentialException.NotFound` for unknown IDs in `update`.

---

## Implementing a Custom WebAuthnProvider

Most developers use the platform adapters above. Reasons to implement your own:

- Unusual platforms (JVM desktop with an external FIDO2 middleware, game consoles, etc.)
- Custom hardware tokens outside the platform authenticator
- Deterministic test doubles for CI (the SDK's own test suite ships a `MockWebAuthnProvider` for this)
- Bridging to a native WebAuthn library not yet wrapped by the SDK

### Contract

`register()` and `authenticate()` must produce output that the OZ on-chain WebAuthn verifier contract can validate. That imposes strict format requirements — see also the public-key details in [smart_accounts.md](./smart_accounts.md#signer-types).

| Field | Requirement |
|-------|-------------|
| `WebAuthnRegistrationResult.publicKey` | 65 bytes, uncompressed secp256r1 (`0x04 \|\| X (32) \|\| Y (32)`). The COSE/SPKI extraction fallback (`SmartAccountUtils.extractPublicKeyFromRegistration`) applies only during `createWallet`; `addNewPasskeySigner` requires the 65-byte form. |
| `WebAuthnRegistrationResult.credentialId` | Raw bytes. The SDK Base64URL-encodes on its own for storage. |
| `WebAuthnRegistrationResult.attestationObject` | Raw CBOR object as delivered by the authenticator. Used by SDK fallback extraction strategies. |
| `WebAuthnAuthenticationResult.signature` | DER-encoded ECDSA P-256 signature. The SDK normalizes via `SmartAccountUtils.normalizeSignature` to 64-byte compact `r \|\| s` with low-S. Do **not** pre-normalize. |
| `WebAuthnAuthenticationResult.authenticatorData` | ≥ 37 bytes (`rpIdHash(32) + flags(1) + signCount(4) + ...`). The UV flag (bit 2) must be set — the verifier rejects with contract error #3117 otherwise. |
| `WebAuthnAuthenticationResult.clientDataJSON` | Must embed the SDK-provided `challenge` parameter as base64url-encoded **without** padding, per WebAuthn spec. |

```kotlin
// WRONG: returning compressed secp256r1 (33 bytes) or only raw X||Y (64 bytes)
// CORRECT: always 65 bytes starting with 0x04 — or let the SDK extract from attestationObject

// WRONG: normalizing the DER signature to compact form in the provider
//   — the SDK calls SmartAccountUtils.normalizeSignature itself; double-normalization breaks
// CORRECT: return DER as produced by the authenticator

// WRONG: clientDataJSON with base64-standard (+ /) encoding of the challenge
// CORRECT: base64url (- _) without padding — this is the WebAuthn spec
```

### Skeleton

```kotlin
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.smartaccount.core.WebAuthnException

class MyCustomWebAuthnProvider(
    private val rpId: String,
    private val rpName: String
) : WebAuthnProvider {

    override suspend fun register(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String
    ): WebAuthnRegistrationResult {
        // 1. Call your native WebAuthn stack, passing challenge as-is.
        // 2. Extract credentialId, 65-byte uncompressed pubkey, full attestation object.
        // 3. Optionally parse authenticator data flags for deviceType and backedUp.
        return WebAuthnRegistrationResult(
            credentialId = /* raw bytes */ ByteArray(0),
            publicKey = /* 65 bytes: 0x04 + X + Y */ ByteArray(65),
            attestationObject = /* CBOR */ ByteArray(0),
            transports = listOf("internal"),
            deviceType = "singleDevice",
            backedUp = false
        )
    }

    override suspend fun authenticate(
        challenge: ByteArray,
        allowCredentials: List<AllowCredential>?
    ): WebAuthnAuthenticationResult {
        // 1. Call your native assertion API, passing challenge as-is.
        // 2. If allowCredentials is non-null, constrain the picker to those IDs.
        // 3. Return raw DER signature (no normalization here).
        return WebAuthnAuthenticationResult(
            credentialId = ByteArray(0),
            authenticatorData = ByteArray(37),
            clientDataJSON = ByteArray(0),
            signature = ByteArray(0)     // DER
        )
    }
}
```

Always wrap native errors into `WebAuthnException.registrationFailed` / `authenticationFailed` / `cancelled` / `notSupported` so the kit's error-handling paths work.

---

## Cross-Platform Checklist

When bringing up a new app on an additional platform, walk this table end-to-end.

| Step | Android | iOS | macOS | Web |
|------|---------|-----|-------|-----|
| Choose `rpId` (bare domain, no scheme) | `example.com` | `example.com` | `example.com` | `example.com` (or `"localhost"` for dev) |
| Publish domain-association file | `.well-known/assetlinks.json` | `.well-known/apple-app-site-association` | `.well-known/apple-app-site-association` | — |
| Configure app capability / manifest | `keytool` SHA-256 in `assetlinks.json` | Xcode Associated Domains `webcredentials:...` | Xcode Associated Domains + App Sandbox / Developer ID | — |
| Add dependency | `androidx.credentials:credentials:1.3.0`, `credentials-play-services-auth:1.3.0`, `androidx.security:security-crypto:1.1.0` | Clibsodium via SPM | Clibsodium via SPM | — |
| Import WebAuthn provider | `com.soneso.stellar.sdk.smartaccount.AndroidWebAuthnProvider` | `com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider` | `com.soneso.stellar.sdk.smartaccount.AppleWebAuthnProvider` | `com.soneso.stellar.sdk.smartaccount.JsWebAuthnProvider` |
| Context requirement | Activity context for provider, Application context for storage | None | **Set `presentationContextProvider` to an `NSWindow` anchor** | None |
| Import storage adapter | `AndroidStorageAdapter` | `KeychainStorageAdapter` or `UserDefaultsStorageAdapter` | `KeychainStorageAdapter` or `UserDefaultsStorageAdapter` | `IndexedDBStorageAdapter` or `LocalStorageAdapter` |
| Minimum runtime version | API 28 for provider, API 24 for storage | iOS 16 | macOS 13 | Chrome 67+, Firefox 60+, Safari 14+, Edge 79+ |
| Localhost development | Not supported | Not supported | Not supported | Supported (`rpId = "localhost"` over `http://localhost`) |

After the per-platform setup, every call is identical — the same `OZSmartAccountKit` API works across platforms. See [smart_accounts.md](./smart_accounts.md) for kit operations and [smart_accounts_policies.md](./smart_accounts_policies.md) for signer, context-rule, policy, and multi-signer flows.
