# WebAuthn Setup: Web (Browser)

Platform-specific guide for configuring WebAuthn passkey authentication in browser applications using the KMP Stellar SDK Smart Account Kit.

## Prerequisites

- A modern browser with WebAuthn support (Chrome 67+, Firefox 60+, Safari 14+, Edge 79+)
- HTTPS (required for WebAuthn; `localhost` is the only exception)

No additional library dependencies are needed. WebAuthn is a built-in browser API (`navigator.credentials`).

## Relying Party Configuration

The `rpId` parameter determines which domain the passkey is bound to. It must match the current page's domain or a registrable suffix of it.

**Rules:**
- If your app is hosted at `app.example.com`, valid `rpId` values are `"app.example.com"` or `"example.com"`
- Setting `rpId` to `"example.com"` allows passkeys to work across all subdomains (`app.example.com`, `www.example.com`, etc.)
- The `rpId` cannot be a public suffix (e.g., `"com"`, `"co.uk"`)
- For `localhost` development, set `rpId` to `"localhost"`

## WebAuthn Provider

`JsWebAuthnProvider` calls the browser's `navigator.credentials.create()` and `navigator.credentials.get()` APIs for passkey creation and assertion.

**Constructor parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `rpId` | `String` | Yes | - | Relying party domain (must match current origin or registrable suffix) |
| `rpName` | `String` | Yes | - | Display name shown during passkey prompts |
| `timeout` | `Long` | No | 60000 | Timeout in milliseconds |

```kotlin
import com.soneso.stellar.sdk.smartaccount.JsWebAuthnProvider

val webauthnProvider = JsWebAuthnProvider(
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)
```

> `JsWebAuthnProvider` is browser-only. In Node.js environments where `navigator.credentials` is not available, it throws `WebAuthnException.NotSupported`.

## Storage Adapters

### IndexedDBStorageAdapter (Recommended)

Structured browser storage with indexing, large storage limits, and async operations. Recommended for production.

**Constructor parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `dbName` | `String` | No | `"stellar_smart_account"` | IndexedDB database name |

```kotlin
import com.soneso.stellar.sdk.smartaccount.IndexedDBStorageAdapter

val storage = IndexedDBStorageAdapter()

// Or with a custom database name
val storage = IndexedDBStorageAdapter(dbName = "my_app_smart_account")
```

Call `storage.close()` when the adapter is no longer needed to release the database connection. The adapter reopens the connection automatically on the next operation.

### LocalStorageAdapter (Fallback)

Synchronous key-value storage using `window.localStorage`. Simpler but limited to approximately 5 MB per origin. Data is not encrypted.

**Constructor parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `keyPrefix` | `String` | No | `"stellar_sa_"` | Prefix for all localStorage keys |

```kotlin
import com.soneso.stellar.sdk.smartaccount.LocalStorageAdapter

val storage = LocalStorageAdapter()

// Or with a custom prefix
val storage = LocalStorageAdapter(keyPrefix = "my_app_sa_")
```

Use `IndexedDBStorageAdapter` for production. `LocalStorageAdapter` is appropriate for prototyping or environments where IndexedDB is unavailable.

## Full Kit Initialization

```kotlin
import com.soneso.stellar.sdk.smartaccount.IndexedDBStorageAdapter
import com.soneso.stellar.sdk.smartaccount.JsWebAuthnProvider
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountConfig
import com.soneso.stellar.sdk.smartaccount.oz.OZSmartAccountKit

val webauthnProvider = JsWebAuthnProvider(
    rpId = "your-domain.com",
    rpName = "My Stellar Wallet"
)

val storage = IndexedDBStorageAdapter()

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

## HTTPS Requirement

WebAuthn requires a secure context. The browser will reject passkey operations on HTTP origins (except `localhost`).

For local development:
- `http://localhost:8080` works with `rpId = "localhost"`
- `http://127.0.0.1:8080` does **not** work (WebAuthn treats `127.0.0.1` differently from `localhost`)
- Use a local HTTPS proxy (e.g., mkcert) for testing with custom domains

## Localhost Development

```kotlin
// Development configuration
val webauthnProvider = JsWebAuthnProvider(
    rpId = "localhost",
    rpName = "Dev Stellar Wallet"
)

val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "your-wasm-hash-hex",
    webauthnVerifierAddress = "CBCD1234...",
    rpId = "localhost",
    rpName = "Dev Stellar Wallet",
    webauthnProvider = webauthnProvider
)
```

Passkeys created with `rpId = "localhost"` are only usable on localhost. Create separate passkeys for each environment.

## Troubleshooting

### SecurityError: rpId does not match the current origin

The `rpId` must match the current page's domain or a registrable suffix. If your page is at `https://app.example.com`:
- `rpId = "app.example.com"` -- valid
- `rpId = "example.com"` -- valid (registrable suffix)
- `rpId = "other.com"` -- SecurityError

### NotAllowedError (user cancellation)

The user dismissed the passkey dialog or the browser blocked the request. The SDK maps this to `WebAuthnException.Cancelled`. Common causes:
- User clicked "Cancel" on the browser's passkey prompt
- The page lost focus during the ceremony
- A browser extension blocked the request

### WebAuthnException.NotSupported in Node.js

`JsWebAuthnProvider` requires `navigator.credentials`, which is not available in Node.js. WebAuthn operations are browser-only. For server-side testing, use mock implementations of the `WebAuthnProvider` interface.

### Cross-origin iframe restrictions

WebAuthn does not work in cross-origin iframes by default. If your app runs inside an iframe:
- The parent frame must include `allow="publickey-credentials-create; publickey-credentials-get"` on the iframe element
- Both the parent and iframe origins must serve appropriate `Permissions-Policy` headers

### IndexedDB not available

`IndexedDBStorageAdapter` throws `StorageException.ReadFailed` if IndexedDB is unavailable (e.g., in some private browsing modes or web workers). Fall back to `LocalStorageAdapter`:

```kotlin
val storage = try {
    IndexedDBStorageAdapter()
} catch (e: Exception) {
    LocalStorageAdapter()
}
```

### Passkeys not syncing across devices

Passkey sync depends on the browser and platform:
- **Chrome**: Syncs via Google Password Manager when signed in
- **Safari**: Syncs via iCloud Keychain
- **Firefox**: Currently stores passkeys locally (no sync)

Ensure the `rpId` is identical across all environments where passkeys should be shared.
