# Smart Accounts Reference

Passkey-authenticated smart accounts on Stellar using OpenZeppelin Soroban contracts. Core production API: kit setup, wallet creation, connection, transactions, credentials, events, and error handling.

Standard imports:

```kotlin
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import kotlinx.coroutines.delay
```

All operations run in a `suspend` context (coroutine).

Related references:

- [WebAuthn Setup](./smart_accounts_webauthn.md) — platform adapters, storage, rpId configuration
- [Context Rules, Policies, and Multi-Signer](./smart_accounts_policies.md) — signer management, context rules, policies, multi-signer operations
- [API Reference](./api_reference.md) — flat signature index for all public classes (smart-account classes are under the "Smart Accounts" section; platform-specific classes are tagged e.g. `(androidMain)`)

## Overview

A smart account is a Soroban contract whose authorization logic lives on-chain. Instead of a classical Stellar account secured by an Ed25519 secret key, the smart account verifies signatures against configured signers and applies context rules and policies.

Supported signer types:

- **WebAuthn passkey** (secp256r1) via an on-chain verifier contract
- **Delegated** Stellar account (G-address) or contract (C-address) using native `require_auth`
- **Ed25519** external signer via a verifier contract

Architecture. `OZSmartAccountKit.create(config)` is the single entry point. The kit exposes seven sub-managers as lazy properties: `walletOperations`, `transactionOperations`, `signerManager`, `contextRuleManager`, `policyManager`, `multiSignerManager`, `credentialManager` (plus `events`). The config takes three platform adapters — `WebAuthnProvider`, `StorageAdapter`, and an optional `ExternalWalletAdapter`. Internally the kit owns a `SorobanServer` (RPC), `OZRelayerClient` (fee-bump, optional), and `OZIndexerClient` (credential lookup, optional).

`OZExternalSignerManager` is **not** owned by the kit — construct it directly, and call `restoreConnections()` at app launch so wallet signers added in a prior session are visible to the multi-signer pipeline.

```kotlin
// WRONG: kit.walletOperations() — it is a property, not a function
// CORRECT: kit.walletOperations  — property access (no parentheses)
```

---

## Installation

Smart accounts are in the same artifact as the rest of the KMP SDK:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.5.0")
}
```

Platform targets: JVM (Java 17+), Android (API 24+), iOS, macOS, JavaScript (Browser/Node.js). WebAuthn features require a platform-specific `WebAuthnProvider` implementation — see [smart_accounts_webauthn.md](./smart_accounts_webauthn.md).

Common types live in two packages:

- `com.soneso.stellar.sdk.smartaccount.core` — signer types, exceptions, utilities
- `com.soneso.stellar.sdk.smartaccount.oz` — kit, sub-managers, config, results

```kotlin
// WRONG: com.soneso.stellar.sdk.smartaccount.*  — does NOT cover sub-packages
// CORRECT: import both explicitly
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.smartaccount.core.*
```

---

## Configuration

`OZSmartAccountConfig` is a data class with four required fields and several optional ones. It validates inputs in its `init` block and throws `ConfigurationException` on invalid values.

### Required fields

| Field | Type | Description |
|-------|------|-------------|
| `rpcUrl` | `String` | Soroban RPC endpoint URL |
| `networkPassphrase` | `String` | Stellar network passphrase (testnet or mainnet) |
| `accountWasmHash` | `String` | SHA-256 hash (**hex**, 64 chars) of the smart account WASM |
| `webauthnVerifierAddress` | `String` | C-address of the deployed WebAuthn verifier contract |

```kotlin
// WRONG: accountWasmHash = "YWJjMTIzZGVm..."  — base64 is NOT accepted
// CORRECT: accountWasmHash must be a 64-character hex string
//   Regex is [0-9a-fA-F]{64}. Config init throws ConfigurationException.InvalidConfig otherwise.
// WRONG: webauthnVerifierAddress = "GA7Q..."  — must be C-address, not G-address
// CORRECT: webauthnVerifierAddress = "CBCD..."  — validated via StrKey.isValidContract
```

### Optional fields

| Field | Type | Default | Notes |
|-------|------|---------|-------|
| `deployerKeypair` | `KeyPair?` | `null` | Null means use the default deterministic deployer |
| `rpId` | `String?` | `null` | WebAuthn Relying Party ID (domain). Browser uses origin when null |
| `rpName` | `String` | `"Smart Account"` | Display name shown to users during WebAuthn prompts |
| `sessionExpiryMs` | `Long` | `604_800_000` (7 days) | Session duration for silent reconnect |
| `signatureExpirationLedgers` | `Int` | `720` (`Util.LEDGERS_PER_HOUR`, ~1 hour) | Auth entry expiration, in ledgers (not seconds). **Replay-protection window**: if an attacker intercepts a signed envelope (via a compromised relayer, MITM, or logged XDR) they can resubmit it until this ledger passes. Default ~1 h is long for interactive flows — consider 60–180 (5–15 min) for high-value transfers |
| `timeoutInSeconds` | `Int` | `30` | Network timeout for transaction ops |
| `relayerUrl` | `String?` | `null` | Enables fee-bump relayer. See [Trust model](#relayer-modes) for the security boundary |
| `indexerUrl` | `String?` | `null` | Enables credential-to-contract discovery |
| `webauthnProvider` | `WebAuthnProvider?` | `null` | Platform passkey implementation |
| `storage` | `StorageAdapter` | `InMemoryStorageAdapter()` | Credential/session persistence. **DANGER: the default `InMemoryStorageAdapter` is tests-only.** Omit `storage = ...` in production and credentials are lost when the process exits — the on-chain smart account becomes unreachable. Always pass a platform adapter (Android Keystore / iOS Keychain / IndexedDB). See [smart_accounts_webauthn.md](./smart_accounts_webauthn.md) |
| `externalWallet` | `ExternalWalletAdapter?` | `null` | Freighter/Lobstr-style signer |
| `maxContextRuleScanId` | `UInt` | `50u` | Highest context rule ID to scan when listing |

```kotlin
// WRONG: sessionExpiryMs = 7  — interpreted as 7 milliseconds, expires immediately
// CORRECT: sessionExpiryMs = 7 * 24 * 60 * 60 * 1000L  — milliseconds
// WRONG: signatureExpirationLedgers = 3600  — 3600 ledgers is ~5 hours, not 1 hour
// CORRECT: signatureExpirationLedgers = Util.LEDGERS_PER_HOUR  — 720 ledgers ~1 hour
```

### Data-class construction

```kotlin
val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "a1b2c3d4e5f6789012345678901234567890abcdef1234567890abcdef123456",
    webauthnVerifierAddress = "CBCD1234EFGH5678IJKL9012MNOP3456QRST7890UVWX1234ABCDEFGH",
    relayerUrl = "https://relayer.example.com",       // optional
    indexerUrl = "https://indexer.example.com",       // optional
    webauthnProvider = MyWebAuthnProvider(),          // required for createWallet / transfer
    storage = MyKeychainStorageAdapter()              // use platform storage in production
)
```

### Builder pattern

Equivalent builder for codebases that prefer a fluent API:

```kotlin
val config = OZSmartAccountConfig.builder(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "a1b2c3d4...",
    webauthnVerifierAddress = "CBCD1234..."
)
    .rpName("My Wallet")
    .sessionExpiryMs(86_400_000L)               // 1 day
    .signatureExpirationLedgers(1440)           // ~2 hours
    .relayerUrl("https://relayer.example.com")
    .indexerUrl("https://indexer.example.com")
    .webauthnProvider(platformProvider)
    .storage(persistentStorage)
    .externalWallet(freighterAdapter)
    .build()
```

### createDefaultDeployer

The deterministic default deployer is available as a companion function:

```kotlin
// Suspend function — uses SHA-256 and Ed25519 seed derivation
val defaultDeployer: KeyPair = OZSmartAccountConfig.createDefaultDeployer()
println(defaultDeployer.getAccountId())  // always the same G-address
```

When `deployerKeypair` is null in the config, this default is used automatically. See [Deployer Details](#deployer-details).

---

## Kit Lifecycle

### Create the kit

`OZSmartAccountKit.create(config)` is synchronous — it does not load sessions or make network calls.

```kotlin
val kit: OZSmartAccountKit = OZSmartAccountKit.create(config)
```

### Connection state

Kits expose three volatile read-only properties reflecting in-memory state only:

```kotlin
val connected: Boolean = kit.isConnected
val credId: String?    = kit.credentialId  // Base64URL-encoded, no padding
val contractId: String? = kit.contractId   // C-address (56 chars)
```

```kotlin
// WRONG: kit.credentialId returns hex  — it does NOT; it is Base64URL without padding
// CORRECT: credentialId is Base64URL-encoded (WebAuthn specification)
```

After an app restart, `isConnected` is always false. Call `walletOperations.connectWallet()` to restore the session from storage.

### Disconnect

Clears in-memory state and the stored session. Stored credentials remain so the user can reconnect later.

```kotlin
kit.disconnect()
// Emits SmartAccountEvent.WalletDisconnected with the previously-connected contractId
```

### Close

Releases HTTP resources owned by the kit (Soroban RPC client, indexer client). Does **not** clear session state. Call `disconnect()` first if you want both.

```kotlin
try {
    // use kit
} finally {
    kit.close()
}
```

---

## Creating a Wallet

`walletOperations.createWallet()` runs a WebAuthn registration ceremony, derives a deterministic contract address, and optionally deploys the smart account contract.

> **Account-loss risk — add a backup signer before funding.**
>
> A freshly-created wallet has exactly one signer: the passkey on the device that ran `createWallet`. If that device is lost, wiped, or the OS resets the authenticator (and iCloud/Google passkey sync is disabled or unavailable), the account and any funds it holds are **permanently inaccessible** — no one, including Soneso or OpenZeppelin, can recover it.
>
> Before funding a production smart account, add at least one backup signer via `signerManager.addNewPasskeySigner` (second device), `addDelegated` (a recovery G-address held offline), or `addEd25519` — see [Signer Management in smart_accounts_policies.md](./smart_accounts_policies.md#signer-management). Treat "first, add a backup signer" as the very next step after `createWallet`.

### Signature

```kotlin
suspend fun createWallet(
    userName: String = "Smart Account User",
    autoSubmit: Boolean = false,
    autoFund: Boolean = false,
    nativeTokenContract: String? = null,
    forceMethod: SubmissionMethod? = null
): CreateWalletResult
```

### CreateWalletResult

```kotlin
data class CreateWalletResult(
    val credentialId: String,         // Base64URL, no padding
    val contractId: String,           // deterministic C-address
    val publicKey: ByteArray,         // 65 bytes uncompressed secp256r1
    val signedTransactionXdr: String, // always populated, even if autoSubmit = false
    val transactionHash: String? = null,  // null unless autoSubmit succeeded
    val nickname: String? = null
)
```

```kotlin
// WRONG: wallet.transactionHash is always set  — it is null when autoSubmit = false
// CORRECT: signedTransactionXdr is always set; transactionHash only after autoSubmit
// WRONG: wallet.publicKey.size == 32  — secp256r1, not Ed25519
// CORRECT: wallet.publicKey.size == 65 (0x04 prefix + 32-byte X + 32-byte Y)
```

### autoSubmit vs autoFund

These two flags are distinct and frequently confused:

| Flag | Meaning |
|------|---------|
| `autoSubmit` | Submit the deploy transaction to the network immediately. When `false`, the result contains `signedTransactionXdr` only — submit externally with `deployPendingCredential()` or your own code. |
| `autoFund` | After deploy, fund the new smart account using Friendbot (**testnet only**). Requires `autoSubmit = true` and a `nativeTokenContract` C-address. |

Flow when `autoFund = true`:

1. Friendbot funds a temporary G-account (testnet HTTP request).
2. SDK waits ~5 seconds for propagation.
3. Balance of the temp account minus a 5 XLM reserve is transferred to the smart account contract via the native SAC token contract.

### Basic example

```kotlin
// Create and deploy in one call
val wallet = kit.walletOperations.createWallet(
    userName = "Alice",
    autoSubmit = true
)
println("Contract:     ${wallet.contractId}")
println("Credential:   ${wallet.credentialId}")
println("Deploy hash:  ${wallet.transactionHash}")
```

### Create-then-deploy-later

```kotlin
// Step 1: create credential and build signed deploy transaction without submitting
val wallet = kit.walletOperations.createWallet(
    userName = "Alice",
    autoSubmit = false
)
// wallet.signedTransactionXdr is populated; wallet.transactionHash is null.
// The credential is stored with deploymentStatus = PENDING.

// Step 2: submit later via deployPendingCredential (uses the stored credential)
val deploy: DeployPendingResult = kit.walletOperations.deployPendingCredential(
    credentialId = wallet.credentialId,
    autoSubmit = true
)
println("Deployed: ${deploy.contractId}, tx: ${deploy.transactionHash}")
```

### Create, deploy, and fund on testnet

On a fresh testnet (or after a reset), the default deployer G-account does not
exist on-chain and the deploy transaction will fail. Fund it via Friendbot
before calling `createWallet`. Skip this step if you configured a relayer
(which pays deploy fees) or supplied your own funded `deployerKeypair`.

```kotlin
// Ensure the default deployer exists on testnet — required when no relayer is
// configured. The default deployer is deterministic (SHA-256 of a well-known
// seed), so this preflight is idempotent across processes.
val deployer = OZSmartAccountConfig.createDefaultDeployer()
try {
    SorobanServer(kit.config.rpcUrl).use { it.getAccount(deployer.getAccountId()) }
} catch (_: Exception) {
    FriendBot.fundTestnetAccount(deployer.getAccountId())
    delay(5000) // allow the new account to propagate
}

val wallet = kit.walletOperations.createWallet(
    userName = "Alice",
    autoSubmit = true,
    autoFund = true,
    nativeTokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
)
```

### DeployPendingResult

```kotlin
data class DeployPendingResult(
    val contractId: String,
    val signedTransactionXdr: String,
    val transactionHash: String? = null
)
```

`deployPendingCredential` parameters mirror `createWallet`:

```kotlin
suspend fun deployPendingCredential(
    credentialId: String,
    autoSubmit: Boolean = true,
    autoFund: Boolean = false,
    nativeTokenContract: String? = null,
    forceMethod: SubmissionMethod? = null
): DeployPendingResult
```

The credential must already exist in storage with a valid `publicKey` and `contractId` — created by a prior `createWallet(autoSubmit = false)` call. On successful deployment the credential is deleted from storage.

### Failures

Throws from `WebAuthnException`, `ValidationException`, `TransactionException`, `CredentialException`, or `StorageException`. See [Error Handling](#error-handling).

### WebAuthn provider required

```kotlin
// WRONG: calling createWallet() without setting webauthnProvider in config
// Result: throws WebAuthnException.NotSupported
// CORRECT: set config.webauthnProvider to a platform-specific implementation
```

See [smart_accounts_webauthn.md](./smart_accounts_webauthn.md) for platform adapters.

---

## Connecting to a Wallet

`walletOperations.connectWallet(options)` restores a session, prompts WebAuthn, or connects directly with known credentials. It is designed for the two-phase app-launch pattern.

### Signature

```kotlin
// Member of OZWalletOperations. ConnectWalletOptions is nested inside the same class.
suspend fun connectWallet(
    options: ConnectWalletOptions = ConnectWalletOptions()
): ConnectWalletResult?
```

### ConnectWalletOptions

```kotlin
// Nested inside OZWalletOperations. Call site: `OZWalletOperations.ConnectWalletOptions(...)`,
// or add an explicit import:
//   import com.soneso.stellar.sdk.smartaccount.oz.OZWalletOperations.ConnectWalletOptions
data class ConnectWalletOptions(
    val credentialId: String? = null,
    val contractId: String? = null,
    val fresh: Boolean = false,
    val prompt: Boolean = false
)
```

### Decision matrix

| Options | Behavior | Returns |
|---------|----------|---------|
| (default) | Silent session restore | Session or `null` |
| `prompt = true` | Restore session, else WebAuthn | Non-null on success |
| `fresh = true` | Skip session, always WebAuthn | Non-null on success |
| `credentialId` [+ `contractId`] | Direct connect, skip session and WebAuthn | Non-null on success; throws `WalletException.NotFound` if the contract does not exist on-chain |

On a successful direct connect, `connectWithCredentials` **removes** the matching `StoredCredential` from local storage right after verifying the contract exists on-chain, because the credential is now considered committed (see [Credential lifecycle](#credential-lifecycle)). Apps that cache credentials locally for display should not rely on storage lookups after a direct connect — use the indexer or re-read `ParsedContextRule.signers`.

### ConnectWalletResult

A sealed type with two arms. `Connected` means a single contract was resolved (the kit's connected state has been set, a session has been saved). `Ambiguous` means the indexer reported multiple contracts where the passkey is registered as a signer; the connected state has NOT been set, and the caller must let the user pick a contract and reconnect with the chosen `contractId`.

```kotlin
sealed class ConnectWalletResult {
    abstract val credentialId: String

    data class Connected(
        override val credentialId: String,
        val contractId: String,
        val restoredFromSession: Boolean
    ) : ConnectWalletResult()

    data class Ambiguous(
        override val credentialId: String,
        val candidates: List<String>   // contract addresses
    ) : ConnectWalletResult()
}
```

`Ambiguous` is by-construction unreachable when `contractId` is supplied to `ConnectWalletOptions` — the cascade is bypassed in that case and the result is always `Connected`.

### Phase 1: silent restore at app launch

```kotlin
val kit = OZSmartAccountKit.create(config)

when (val restored = kit.walletOperations.connectWallet()) {
    null -> {
        // No saved session — show Connect button in UI.
    }
    is ConnectWalletResult.Connected -> {
        println("Reconnected to ${restored.contractId}")
    }
    is ConnectWalletResult.Ambiguous -> {
        // Unreachable for the silent restore path: the saved session
        // supplies an explicit contractId, which bypasses the cascade.
    }
}
```

### Phase 2: user taps Connect

```kotlin
val result = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(prompt = true)
)
when (result) {
    null -> { /* unreachable when prompt = true */ }
    is ConnectWalletResult.Connected -> {
        println("Connected: ${result.contractId}")
    }
    is ConnectWalletResult.Ambiguous -> {
        // Show a picker to the user, then call connectWallet again with
        // both credentialId (= result.credentialId) and the chosen contractId.
        showPicker(result.candidates) { chosen ->
            kit.walletOperations.connectWallet(
                OZWalletOperations.ConnectWalletOptions(
                    credentialId = result.credentialId,
                    contractId = chosen
                )
            )
        }
    }
}
```

### Force fresh authentication

Required for sensitive operations (e.g., changing signers):

```kotlin
val fresh = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(fresh = true)
)
```

### Direct connect with known credentials

No WebAuthn ceremony, no session check. Useful after a user picks a wallet from a list in the indexer:

```kotlin
val direct = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(
        credentialId = "abc123_...",   // Base64URL, from indexer
        contractId   = "CABC..."
    )
)
// Always returns Connected on success (Ambiguous is by-construction unreachable
// when contractId is supplied); throws WalletException.NotFound if the
// contract does not exist on-chain.
```

```kotlin
// WRONG: OZWalletOperations.ConnectWalletOptions(contractId = "CABC...")  — contractId alone throws
// CORRECT: contractId must be paired with credentialId
```

### Contract lookup order for connectWallet

When `credentialId` is provided (or after WebAuthn), the SDK resolves the contract address in this order:

1. **Local storage**. A storage hit means deployment is `PENDING` or `FAILED`
   (successful deploy deletes the credential). `FAILED` entries throw
   `WalletException.NotFound` with a message pointing to
   `deployPendingCredential()` for retry. `PENDING` entries are trusted —
   the stored `contractId` is used directly.
2. **Deterministic address derivation** from the configured deployer. The
   derived address is verified on-chain via `getContractData`. If no
   contract exists at the derived address, the cascade falls through to
   the indexer (the passkey was added as a signer to an existing wallet
   rather than deploying its own under our deployer). RPC / network errors
   during verification propagate as their original types.
3. **Indexer fallback** (if configured). Looks up contracts where the
   passkey is registered as a signer (sourced from on-chain
   `signer_registered` events, emitted both at deploy time and on
   `add_signer`).
   - 0 contracts → throw `WalletException.NotFound`.
   - 1 contract → verify on-chain and return `Connected`.
   - N > 1 contracts → return `ConnectWalletResult.Ambiguous(credentialId, candidates)`. Connection state is NOT set; the caller must let the user pick.

When the explicit `contractId` is supplied (direct connect or session restore), the cascade is bypassed and only the on-chain verification runs.

---

## Standalone Passkey Authentication

`authenticatePasskey` runs a WebAuthn ceremony without connecting the kit. Use it when you need a signature first and want to discover contracts later (e.g., via an indexer), or for multi-signer authorization.

```kotlin
// Member of OZWalletOperations. Call via `kit.walletOperations.authenticatePasskey(...)`.
suspend fun authenticatePasskey(
    challenge: ByteArray? = null,
    credentialIds: List<String>? = null
): AuthenticatePasskeyResult
```

```kotlin
data class AuthenticatePasskeyResult(
    val credentialId: String,         // Base64URL, no padding
    val signature: WebAuthnSignature, // normalized (DER decoded to compact r||s, low-S)
    val publicKey: ByteArray          // 65 bytes if credential is in local storage; empty otherwise
)
```

Typical flow:

```kotlin
// 1. Authenticate
val auth = kit.walletOperations.authenticatePasskey()

// 2. Look up contracts via indexer
val response = kit.indexerClient?.lookupByCredentialId(auth.credentialId)
val first = response?.contracts?.firstOrNull()

// 3. Connect to the chosen contract
if (first != null) {
    val result = kit.walletOperations.connectWallet(
        OZWalletOperations.ConnectWalletOptions(
            credentialId = auth.credentialId,
            contractId   = first.contractId
        )
    )
}
```

---

## Signer Types

Smart-account signers are a sealed hierarchy in `com.soneso.stellar.sdk.smartaccount.core`:

```kotlin
sealed class SmartAccountSigner {
    abstract fun toScVal(): SCValXdr
    abstract val uniqueKey: String
}
```

### DelegatedSigner

A Stellar address (G or C) that authorizes via Soroban's native `require_auth`. No verifier contract.

```kotlin
data class DelegatedSigner(val address: String) : SmartAccountSigner()
```

```kotlin
val accountSigner  = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")
val contractSigner = DelegatedSigner("CBCD1234EFGH5678IJKL9012MNOP3456QRST7890UVWX1234ABCDEFGH")
```

On-chain SCVal representation:

```
Vec([Symbol("Delegated"), Address(address)])
```

Validation: constructor throws `ValidationException.InvalidAddress` if the address is neither a valid Ed25519 public key nor a valid contract address.

### ExternalSigner

A verifier contract + key-data bytes. Use this for passkeys and Ed25519 keys.

```kotlin
data class ExternalSigner(
    val verifierAddress: String,    // must be a C-address
    val keyData: ByteArray
) : SmartAccountSigner()
```

On-chain SCVal representation:

```
Vec([Symbol("External"), Address(verifierAddress), Bytes(keyData)])
```

Do not construct `ExternalSigner` directly for passkeys — use the `webAuthn` factory, which assembles `keyData` correctly.

### ExternalSigner.webAuthn (factory)

```kotlin
// Factory on the companion object — NOT a subclass and NOT a constructor
val signer: ExternalSigner = ExternalSigner.webAuthn(
    verifierAddress = "CBCD1234...",           // WebAuthn verifier contract
    publicKey = secp256r1PublicKey,            // 65 bytes, uncompressed (0x04 prefix + X + Y)
    credentialId = credentialIdBytes           // raw bytes (NOT Base64URL-encoded here)
)
```

```kotlin
// WRONG: ExternalSigner.WebAuthn(...)  — no such PascalCase variant (not a sealed subclass)
// CORRECT: ExternalSigner.webAuthn(...) — camelCase factory on companion object

// WRONG: publicKey.size == 33  — that is the compressed format, not accepted
// CORRECT: publicKey.size == 65 and publicKey[0] == 0x04.toByte()

// WRONG: credentialId = Util.base64urlDecode(...).let { String(it) }  — NOT a string
// CORRECT: credentialId is the raw ByteArray as returned by the WebAuthn ceremony
```

The factory validates the key size (`SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE = 65`) and the uncompressed prefix (`0x04`). The stored `keyData` is `publicKey || credentialId`.

### ExternalSigner.ed25519 (factory)

```kotlin
val signer: ExternalSigner = ExternalSigner.ed25519(
    verifierAddress = "CDEF5678...",  // Ed25519 verifier contract
    publicKey = ed25519PublicKey      // 32 bytes
)
```

The factory validates `publicKey.size == 32` (`SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE`). No credential ID suffix — `keyData` is the 32-byte public key.

### SmartAccountBuilders (factory helpers)

`SmartAccountBuilders` offers the same factories with type-safe names plus inspection helpers:

```kotlin
val delegated = SmartAccountBuilders.createDelegatedSigner("GA7Q...")
val passkey   = SmartAccountBuilders.createWebAuthnSigner(
    webauthnVerifierAddress = "CBCD...",
    publicKey = publicKey65,
    credentialId = credentialIdBytes
)
val ed25519Signer = SmartAccountBuilders.createEd25519Signer("CDEF...", publicKey32)

// Inspection
val isPasskey: Boolean = SmartAccountBuilders.isExternalSigner(passkey)
val credId: ByteArray? = SmartAccountBuilders.getCredentialIdFromSigner(passkey)
val credIdStr: String? = SmartAccountBuilders.getCredentialIdStringFromSigner(passkey) // Base64URL
val typeLabel: String  = SmartAccountBuilders.describeSignerType(passkey)              // "Passkey (WebAuthn)"

// Matching
val matches = SmartAccountBuilders.signerMatchesCredentialId(passkey, "base64url-id")
val same    = SmartAccountBuilders.signersEqual(passkey, otherSigner)
val unique  = SmartAccountBuilders.collectUniqueSigners(listOfSigners)
```

### Signer constants

```kotlin
SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE     // 32
SmartAccountConstants.SECP256R1_PUBLIC_KEY_SIZE   // 65
SmartAccountConstants.UNCOMPRESSED_PUBKEY_PREFIX  // 0x04.toByte()
```

---

## Transactions

`kit.transactionOperations` handles token transfers and arbitrary contract calls for the connected smart account. Every state-changing operation runs a WebAuthn ceremony to sign authorization entries.

### TransactionResult

```kotlin
data class TransactionResult(
    val success: Boolean,
    val hash: String? = null,
    val ledger: UInt? = null,
    val error: String? = null
)
```

### transfer

SEP-41 compatible token transfer (XLM via SAC, any Soroban token).

```kotlin
suspend fun transfer(
    tokenContract: String,       // C-address of the token contract
    recipient: String,           // G-address or C-address
    amount: String,              // decimal string — converted to stroops internally
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
val result = kit.transactionOperations.transfer(
    tokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC", // native SAC
    recipient     = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ",
    amount        = "10.5"
)

if (result.success) {
    println("Hash: ${result.hash}, ledger: ${result.ledger}")
} else {
    println("Failed: ${result.error}")
}
```

```kotlin
// WRONG: amount = 10  — must be a String
// CORRECT: amount = "10"  — decimal string with up to 7 places
// WRONG: amount = "10500000"  — that would be 10.5 million XLM, not 10.5 XLM
// CORRECT: amount = "10.5"  — SDK converts to stroops automatically
// WRONG: transfer to self  — throws ValidationException
// CORRECT: recipient must differ from the smart account's contractId
```

`transfer` throws `WalletException.NotConnected` when no wallet is connected. `ValidationException.InvalidAddress` for bad recipient, `IllegalArgumentException` for invalid amount, `TransactionException.*` for simulation/submission failures, `WebAuthnException.*` for biometric cancellation.

### contractCall

Calls an arbitrary function on an external contract, authorized by the smart account (context rule type `CallContract(target)`).

```kotlin
suspend fun contractCall(
    target: String,                    // C-address of target contract
    targetFn: String,                  // function name
    targetArgs: List<SCValXdr> = emptyList(),
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

Example — approve a token spender:

```kotlin
import com.soneso.stellar.sdk.Address

val args = listOf(
    Scv.toAddress(Address(smartAccountId).toSCAddress()),    // from
    Scv.toAddress(Address(spenderContract).toSCAddress()),   // spender
    Util.stroopsToI128ScVal(Util.amountToStroops("100")),    // amount as i128
    Scv.toUint32(720u)                                       // expiration ledger
)

val result = kit.transactionOperations.contractCall(
    target = tokenContract,
    targetFn = "approve",
    targetArgs = args
)
```

`ResolveContextRuleIds` is a typealias for `suspend (entry, index) -> List<UInt>`. Used to disambiguate which context rule authorizes an auth entry when multiple match — see [smart_accounts_policies.md](./smart_accounts_policies.md).

### executeAndSubmit

Like `contractCall`, but routes through the smart account contract's `execute(target, target_fn, target_args)` entry point. Use this when the target contract should see the smart account as the invoker via `execute`, not via `require_auth`.

```kotlin
suspend fun executeAndSubmit(
    target: String,
    targetFn: String,
    targetArgs: List<SCValXdr> = emptyList(),
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

### submit

Escape hatch for arbitrary host functions. `transfer`, `contractCall`, and `executeAndSubmit` all funnel into this method after building an `InvokeContract` host function. Use `submit` directly when the host function is not `InvokeContract` (e.g., `CreateContract`, `UploadContractWasm`) or when you need to hand-craft auth entries.

```kotlin
suspend fun submit(
    hostFunction: HostFunctionXdr,
    auth: List<SorobanAuthorizationEntryXdr>,
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

The SDK simulates the host function, signs auth entries whose address matches the connected smart account, re-simulates, and submits. Pass an empty `auth` list in most cases — simulation produces the entries. Pre-supplied entries are forwarded unchanged.

```kotlin
// WRONG: kit.transactionOperations.submit(hostFunction)  — auth is required (pass emptyList())
// CORRECT:
val result = kit.transactionOperations.submit(
    hostFunction = HostFunctionXdr.CreateContract(createContractArgs),
    auth = emptyList()
)
```

Example — shape of the call for a non-`InvokeContract` host function (CreateContract, UploadWasm):

```kotlin
// submit() accepts any HostFunctionXdr — useful for CreateContract, UploadWasm,
// or hand-crafted InvokeHostFunction that transfer/contractCall don't cover.
val result = kit.transactionOperations.submit(
    hostFunction = myHostFunctionXdr,   // build via InvokeHostFunctionOperation helpers — see xdr.md
    auth = emptyList()                  // or pre-built SorobanAuthorizationEntry list
)
```

See [xdr.md](./xdr.md) for constructing `HostFunctionXdr` values.

### fundWallet

Post-deploy testnet top-up helper. Generates a throw-away keypair, funds it via Friendbot, and transfers the balance (minus `OZConstants.FRIENDBOT_RESERVE_XLM`, currently 5 XLM) to the connected smart account via the native SAC contract. Works only on testnet — mainnet has no Friendbot.

```kotlin
suspend fun fundWallet(
    nativeTokenContract: String,              // XLM SAC C-address
    forceMethod: SubmissionMethod? = null
): String                                     // returned amount as decimal XLM string
```

Use this after `createWallet(autoSubmit = true, autoFund = false)` when you want to defer funding, or to top up an existing wallet during development. `createWallet(autoFund = true, nativeTokenContract = ...)` internally calls this.

```kotlin
// WRONG: kit.transactionOperations.fundWallet()  — native SAC is required
// CORRECT:
val amount = kit.transactionOperations.fundWallet(
    nativeTokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
)
println("Funded $amount XLM")
```

Throws `WalletException.NotConnected` if no wallet is connected, `ValidationException.InvalidAddress` for an invalid SAC address, and `TransactionException.*` for Friendbot or submission failures.

### SubmissionMethod

The kit auto-selects the submission method: relayer if `relayerUrl` is configured, otherwise direct Soroban RPC. Override with `forceMethod`:

```kotlin
enum class SubmissionMethod { RELAYER, RPC }

// Force direct RPC even if relayer is configured
val result = kit.transactionOperations.transfer(
    tokenContract = tokenId,
    recipient = to,
    amount = "10",
    forceMethod = SubmissionMethod.RPC
)

// Force RELAYER when no relayer is configured → throws TransactionException.SubmissionFailed
```

### Relayer modes

When a relayer is configured, the SDK picks between two submission modes based on the auth entries returned by simulation:

- **Mode 1** (host function + auth): used when all auth entries are `Address` credentials. Relayer builds the envelope and fee-bumps.
- **Mode 2** (signed XDR): used when any auth entry is `source_account` (Void) credentials. SDK signs the envelope with the deployer keypair, relayer fee-bumps.

The SDK handles this automatically — no caller intervention needed.

**Trust model.** The relayer receives the signed envelope (or host function + auth entries) and submits it on the user's behalf. It **cannot steal funds** because signatures are bound to the auth payload. It **can** see every transaction in plaintext, censor/drop/delay submissions, and reorder relative to other clients. For mainnet:

- Use a relayer you operate or whose operator you trust contractually.
- Require HTTPS with certificate validation; enable pinning where the platform allows.
- Never log or expose the relayer URL in public client code bundled with write access tokens.
- Prefer submission via direct RPC (`forceMethod = SubmissionMethod.RPC`) for high-value transfers when a delegated signer can pay the fee directly.

### Transaction lifecycle

Each `transfer` / `contractCall` / `executeAndSubmit` call simulates, prompts WebAuthn once per matching auth entry (usually one per transaction), re-simulates, submits, then polls (30 × 3 s for transaction methods; 10 × 2 s for deploy). Relayer vs RPC is auto-selected; override with `forceMethod`.

---

## Credential Management

`kit.credentialManager` manages local credential storage. Credentials are WebAuthn passkeys with metadata about deployment state and usage.

### StoredCredential

```kotlin
data class StoredCredential(
    val credentialId: String,                // Base64URL, no padding
    val publicKey: ByteArray,                // 65 bytes (uncompressed secp256r1)
    val contractId: String? = null,
    val deploymentStatus: CredentialDeploymentStatus = CredentialDeploymentStatus.PENDING,
    val deploymentError: String? = null,
    val createdAt: Long = currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val nickname: String? = null,
    val isPrimary: Boolean = false,
    val transports: List<String>? = null,    // "usb" | "nfc" | "ble" | "internal" | "hybrid"
    val deviceType: String? = null,          // "singleDevice" | "multiDevice"
    val backedUp: Boolean? = null
)
```

```kotlin
enum class CredentialDeploymentStatus { PENDING, FAILED }
// Note: no SUCCESS status. Credentials are deleted from storage after successful deployment.
```

### Credential lifecycle

```
pending --[deploy success]--> deleted from storage
pending --[deploy failure]--> failed (deploymentError set)
pending --[sync discovers contract on-chain]--> deleted from storage
failed  --[deleteCredential]--> deleted from storage
```

After deployment succeeds, the credential is removed from storage. Reconnection is via sessions (short-term) or the indexer (long-term). The public key stays on-chain as part of the context rule signers.

### Common operations

```kotlin
// Save or upsert (no deployment metadata; overwrites existing by ID)
val cred: StoredCredential = kit.credentialManager.saveCredential(
    credentialId = "abc123_...",
    publicKey    = publicKey65,
    nickname     = "MacBook Touch ID",
    contractId   = "CABC..."
)

// Lookup
val found: StoredCredential? = kit.credentialManager.getCredential("abc123_...")
val all:   List<StoredCredential> = kit.credentialManager.getAllCredentials()
val byContract: List<StoredCredential> =
    kit.credentialManager.getCredentialsByContract("CABC...")
val forCurrent: List<StoredCredential> = kit.credentialManager.getForConnectedWallet()
val pending:    List<StoredCredential> = kit.credentialManager.getPendingCredentials()

// Update
kit.credentialManager.updateNickname("abc123_...", "MacBook Pro Touch ID")

// Delete (refuses if contract is already deployed on-chain)
kit.credentialManager.deleteCredential("abc123_...")

// Bulk clear (irreversible)
kit.credentialManager.clearAll()
```

### Syncing with on-chain state

`sync` and `syncAll` reconcile local storage against the chain. Essential for apps that may be killed mid-deployment:

```kotlin
val deployed: Boolean = kit.credentialManager.sync("abc123_...")
// true  -> contract exists on-chain; credential deleted from storage
// false -> contract not yet on-chain; credential remains

val summary: SyncResult = kit.credentialManager.syncAll()
println("Deployed: ${summary.deployed}, pending: ${summary.pending}, failed: ${summary.failed}")
```

```kotlin
data class SyncResult(val deployed: Int, val pending: Int, val failed: Int)
```

### Storage adapter

`config.storage` defaults to `InMemoryStorageAdapter` (non-persistent). Production apps implement `StorageAdapter` using platform storage — see [smart_accounts_webauthn.md](./smart_accounts_webauthn.md) for Keychain, SharedPreferences, localStorage patterns.

---

## External Signer Manager

`OZExternalSignerManager` tracks delegated (non-passkey) signers — raw Ed25519 secret keys and connected external wallets (Freighter, LOBSTR, etc.). It is the runtime holder for signers supplied as `SelectedSigner.Wallet` in multi-signer operations.

```kotlin
// WRONG: kit.externalSignerManager  — it is NOT a property of OZSmartAccountKit
// CORRECT: construct the manager directly; it is an independent class
val externalMgr = OZExternalSignerManager(
    networkPassphrase = "Test SDF Network ; September 2015",
    walletAdapter = myWalletAdapter,                  // optional
    walletConnectionStorage = myConnectionStorage     // optional; platform KV store
)
```

Relationship to `ExternalWalletAdapter`: the adapter is the *interface* a wallet provider implements (connect / signAuthEntry / canSignFor), passed to both `OZSmartAccountConfig.externalWallet` and `OZExternalSignerManager`. The adapter discussion under [Multi-Signer Operations in smart_accounts_policies.md](./smart_accounts_policies.md#multi-signer-operations) covers the interface itself; this section covers the manager that owns adapter lifecycle and persists wallet connections.

### Constructor

```kotlin
class OZExternalSignerManager(
    private val networkPassphrase: String,
    private val walletAdapter: ExternalWalletAdapter? = null,
    private val walletConnectionStorage: WalletConnectionStorage? = null
)
```

- `networkPassphrase` — forwarded to the adapter's `signAuthEntry` via `SignAuthEntryOptions.networkPassphrase`.
- `walletAdapter` — required for `addFromWallet` and `restoreConnections`. When null, only keypair signers are supported.
- `walletConnectionStorage` — required for `restoreConnections` to reconnect across app launches. When null, wallet connections are in-memory only.

### WalletConnectionStorage

A minimal key-value interface for persisting wallet connection metadata (address + walletId + walletName). Separate from `StorageAdapter` — it holds only wallet connections, not credentials or sessions.

```kotlin
interface WalletConnectionStorage {
    suspend fun getItem(key: String): String?
    suspend fun setItem(key: String, value: String)
    suspend fun removeItem(key: String)
}
```

Implement this with SharedPreferences (Android), Keychain/UserDefaults (iOS), or `localStorage` (Web). The default if you pass `null` is an in-memory implementation that loses data on process exit — safe for development only.

### ExternalSignerInfo and ExternalSignerType

```kotlin
data class ExternalSignerInfo(
    val address: String,                     // G-address
    val type: ExternalSignerType,
    val walletName: String? = null,          // only for WALLET
    val walletId: String? = null             // only for WALLET
)

enum class ExternalSignerType { KEYPAIR, WALLET }
```

### addFromSecret

Adds an Ed25519 secret key as an in-memory signer. The `KeyPair` is never persisted — it lives only for the life of the manager.

```kotlin
suspend fun addFromSecret(secretKey: String): String    // returns derived G-address
```

```kotlin
val address = externalMgr.addFromSecret("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34REYB6WBMG7CKKFJHYAEGQ")
```

```kotlin
// WRONG: addFromSecret("GA7Q...")  — secret keys are S-addresses, not G-addresses
// CORRECT: addFromSecret("S...")  — Stellar secret seed
```

Keypair signers take precedence over wallet signers with the same G-address; any persisted wallet entry for that address is removed from `WalletConnectionStorage`. Throws `SignerException.Invalid` on an invalid seed.

### addFromWallet

Prompts the user through the configured `ExternalWalletAdapter`. On success, the connection is persisted to `WalletConnectionStorage` (if configured) so `restoreConnections` can reconnect later.

```kotlin
suspend fun addFromWallet(): ConnectedWallet?           // null if the user cancelled
```

```kotlin
val wallet = externalMgr.addFromWallet()
if (wallet != null) {
    println("Connected ${wallet.walletName} (${wallet.address})")
}
```

Throws `ConfigurationException.MissingConfig` if `walletAdapter` is null.

### canSignFor / get / getAll

Query methods. `canSignFor` checks keypair signers first (O(1)), then delegates to `walletAdapter.canSignFor`. `getAll` returns keypair signers followed by wallet signers, deduplicated by address (keypair wins).

```kotlin
suspend fun canSignFor(address: String): Boolean
suspend fun getAll(): List<ExternalSignerInfo>
```

```kotlin
if (externalMgr.canSignFor("GA7Q...")) {
    val info = externalMgr.getAll().first { it.address == "GA7Q..." }
    println("Type: ${info.type}, wallet: ${info.walletName ?: "n/a"}")
}
```

### signAuthEntry

Signs a Base64-encoded `HashIDPreimage::SorobanAuthorization` XDR with the signer for `address`. Keypair signers sign locally (SHA-256 hash the preimage, Ed25519-sign); wallet signers delegate to `ExternalWalletAdapter.signAuthEntry`.

```kotlin
suspend fun signAuthEntry(
    address: String,
    authEntry: String                                    // Base64 HashIDPreimage XDR
): SignAuthEntryResult

data class SignAuthEntryResult(
    val signedAuthEntry: String,                         // Base64 raw 64-byte Ed25519 signature
    val signerAddress: String? = null
)
```

```kotlin
// WRONG: authEntry = hexPreimage  — must be Base64, not hex
// CORRECT: authEntry = base64 of the HashIDPreimage::SorobanAuthorization XDR
// WRONG: signedAuthEntry is DER  — it is a raw 64-byte Ed25519 signature, Base64-encoded
// CORRECT: decode with Base64 and you get the 64-byte r||s signature
```

Throws `SignerException.NotFound` if no signer matches the address, or `TransactionException.SigningFailed` on a signing error.

### remove / removeAll

```kotlin
suspend fun remove(address: String)      // clears keypair + disconnects wallet + removes from storage
suspend fun removeAll()                  // clears every signer, disconnects all wallets, wipes storage
```

`remove` is safe to call for an unknown address — it is a no-op. `removeAll` calls `walletAdapter.disconnect()` and deletes the manager's storage key.

### restoreConnections

Critical on app restart. Reads persisted wallet-connection metadata from `WalletConnectionStorage` and calls `walletAdapter.reconnect(walletId)` for each entry. Failed reconnects are silently removed from storage (the wallet may have been revoked).

```kotlin
suspend fun restoreConnections(): List<ConnectedWallet>
```

Idempotent — subsequent calls return `walletAdapter.getConnectedWallets()` without re-reading storage. Returns an empty list if either `walletAdapter` or `walletConnectionStorage` is null.

```kotlin
// Typical app launch sequence
val kit = OZSmartAccountKit.create(config)
val externalMgr = OZExternalSignerManager(
    networkPassphrase = config.networkPassphrase,
    walletAdapter = myWalletAdapter,
    walletConnectionStorage = myConnectionStorage
)

// Restore previously connected wallets BEFORE the user triggers a multi-signer action.
// Without this call, adapter.canSignFor() returns false for wallets the user connected
// in a previous session, and multi-signer operations will fail.
externalMgr.restoreConnections()

// Now safe to build SelectedSigner lists with Wallet entries
val rule = kit.contextRuleManager.listContextRules().first { it.id == ruleId }
val selectedSigners = rule.signers.mapNotNull { /* as in smart_accounts_policies.md */ }
```

```kotlin
// WRONG: call restoreConnections() lazily, on first multi-signer op
// CORRECT: call it once at app launch after constructing the manager — wallet
//          signers are invisible to canSignFor until restore has run.
```

---

## Events

`kit.events` is a `SmartAccountEventEmitter`. Subscribe to typed events via `on<T>` (Kotlin-only, reified) or to all events via `addListener` (cross-language).

### Event types

```kotlin
sealed class SmartAccountEvent {
    data class WalletConnected(val contractId: String, val credentialId: String) : SmartAccountEvent()
    data class WalletDisconnected(val contractId: String) : SmartAccountEvent()
    data class CredentialCreated(val credential: StoredCredential) : SmartAccountEvent()
    data class CredentialDeleted(val credentialId: String) : SmartAccountEvent()
    data class SessionExpired(val contractId: String, val credentialId: String) : SmartAccountEvent()
    data class TransactionSigned(val contractId: String, val credentialId: String?) : SmartAccountEvent()
    data class TransactionSubmitted(val hash: String, val success: Boolean) : SmartAccountEvent()
}
```

### Type-safe subscription

```kotlin
val unsub: () -> Unit = kit.events.on<SmartAccountEvent.WalletConnected> { event ->
    println("Connected to ${event.contractId}")
}
// Later:
unsub()
```

### One-shot subscription

```kotlin
kit.events.once<SmartAccountEvent.TransactionSubmitted> { event ->
    println("First tx: ${event.hash}, ok=${event.success}")
}
```

### Cross-language subscription

```kotlin
val unsub = kit.events.addListener { event ->
    when (event) {
        is SmartAccountEvent.WalletConnected ->
            println("Connected: ${event.contractId}")
        is SmartAccountEvent.TransactionSubmitted ->
            println("Tx ${event.hash}: success=${event.success}")
        else -> { }
    }
}
```

### Error handler

Listener exceptions are swallowed by default to protect other listeners. Install an error handler for debugging:

```kotlin
kit.events.setErrorHandler { event, err ->
    println("Listener failed on ${event::class.simpleName}: ${err.message}")
}
```

### Other API

```kotlin
kit.events.removeAllListeners("WalletConnected")  // by event type name
kit.events.removeAllListeners()                   // everything
val n = kit.events.listenerCount("WalletConnected")
```

### TransactionSubmitted semantics

`success = true` means the network accepted the transaction for inclusion — **not** that it was confirmed in a ledger. Use `TransactionResult.success` (from `transfer`/`contractCall`) for confirmed state.

---

## Indexer

`OZIndexerClient` queries an off-chain index of smart-account contracts keyed by credential ID and signer address. Use it for "Connect Wallet" discovery (find a user's contracts by passkey) and for fetching on-chain state without iterating context rules by hand.

`kit.indexerClient` is populated when `config.indexerUrl` is set, **or** when `OZIndexerClient.DEFAULT_INDEXER_URLS` has a default URL for `config.networkPassphrase` (testnet and mainnet are covered). It is `null` only for custom networks with no explicit `indexerUrl`.

```kotlin
// WRONG: kit.indexerClient!!.lookupByCredentialId(id)  — null-unsafe; guard instead
// CORRECT: kit.indexerClient?.lookupByCredentialId(id)  — null when no indexer is configured
```

### Construction

```kotlin
class OZIndexerClient(
    indexerUrl: String,                                                     // https:// or http://localhost
    timeoutMs: Long = OZConstants.DEFAULT_INDEXER_TIMEOUT_MS                // 10_000
)

companion object {
    val DEFAULT_INDEXER_URLS: Map<String, String>                           // testnet + mainnet
    fun getDefaultUrl(networkPassphrase: String): String?
    fun forNetwork(networkPassphrase: String, timeoutMs: Long = ...): OZIndexerClient?
}
```

Direct construction (standalone use, outside the kit):

```kotlin
val indexer = OZIndexerClient.forNetwork("Test SDF Network ; September 2015")
    ?: error("No default indexer URL for this network")
```

Constructor throws `ConfigurationException.InvalidConfig` for a blank URL or a non-HTTPS URL (except `http://localhost`). `close()` releases the underlying HTTP client — the kit handles this automatically when used via `kit.indexerClient`.

### lookupByCredentialId

Finds contracts where a WebAuthn credential is registered as a signer. Accepts the Base64URL-encoded credential ID (the SDK's internal format); the client converts it to hex for the HTTP call.

```kotlin
suspend fun lookupByCredentialId(credentialId: String): CredentialLookupResponse
```

```kotlin
val response = kit.indexerClient?.lookupByCredentialId(auth.credentialId)
response?.contracts?.forEach { println("${it.contractId} (${it.contextRuleCount} rules)") }
```

Throws `ValidationException.InvalidInput` if `credentialId` is not valid Base64URL, `IndexerException.RequestFailed` on HTTP errors, `IndexerException.Timeout` on timeout.

### lookupByAddress

Finds contracts where an address is a delegated or native signer. Accepts both G-addresses and C-addresses.

```kotlin
suspend fun lookupByAddress(address: String): AddressLookupResponse
```

```kotlin
val contracts = kit.indexerClient?.lookupByAddress("GA7Q...")?.contracts ?: emptyList()
```

Throws `ValidationException.InvalidAddress` on bad format, `IndexerException.*` on request failure.

### getContract

Full details for a specific smart account contract — summary counts plus every context rule with its signers and policies.

```kotlin
suspend fun getContract(contractId: String): ContractDetailsResponse
```

```kotlin
val details = kit.indexerClient?.getContract("CABC...")
details?.contextRules?.forEach { rule ->
    println("Rule ${rule.contextRuleId}: ${rule.signers.size} signers, ${rule.policies.size} policies")
}
```

Throws `ValidationException.InvalidAddress` for a non-contract address, `IndexerException.*` on request failure.

### getStats

Aggregate indexer statistics. Useful for health dashboards.

```kotlin
suspend fun getStats(): IndexerStatsResponse
```

```kotlin
val stats = kit.indexerClient?.getStats()?.stats
println("Indexed ${stats?.uniqueContracts} contracts, ${stats?.uniqueCredentials} credentials")
```

### isHealthy

Lightweight reachability check. Never throws — returns `false` on any error (network failure, timeout, non-"ok" status).

```kotlin
suspend fun isHealthy(): Boolean
```

```kotlin
if (kit.indexerClient?.isHealthy() != true) {
    // Fall back to deterministic derivation + on-chain verification
}
```

### Response types

```kotlin
@Serializable
data class CredentialLookupResponse(
    val credentialId: String,                           // Base64URL
    val contracts: List<IndexedContractSummary>,
    val count: Int
)

@Serializable
data class AddressLookupResponse(
    val signerAddress: String,
    val contracts: List<IndexedContractSummary>,
    val count: Int
)

@Serializable
data class ContractDetailsResponse(
    val contractId: String,
    val summary: IndexedContractSummary,
    val contextRules: List<IndexedContextRule>
)

@Serializable
data class IndexedContractSummary(
    val contractId: String,
    val contextRuleCount: Int,
    val externalSignerCount: Int,
    val delegatedSignerCount: Int,
    val nativeSignerCount: Int,
    val firstSeenLedger: Int,
    val lastSeenLedger: Int,
    val contextRuleIds: List<Int>
)

@Serializable
data class IndexedContextRule(
    val contextRuleId: Int,
    val signers: List<IndexedSigner>,
    val policies: List<IndexedPolicy>
)

@Serializable
data class IndexedSigner(
    val signerType: String,                             // "External" | "Delegated" | "Native"
    val signerAddress: String? = null,                  // populated for Delegated/Native
    val credentialId: String? = null                    // hex, populated for External
)

@Serializable
data class IndexedPolicy(
    val policyAddress: String,
    val installParams: JsonElement? = null              // policy-specific JSON
)

@Serializable
data class IndexerStatsResponse(val stats: IndexerStats)

@Serializable
data class IndexerStats(
    val totalEvents: Long,
    val uniqueContracts: Long,
    val uniqueCredentials: Long,
    val firstLedger: Long,
    val lastLedger: Long,
    val eventTypes: List<EventTypeCount>
)

@Serializable
data class EventTypeCount(val eventType: String, val count: Long)

@Serializable
data class HealthCheckResponse(val status: String)      // internal to isHealthy()
```

```kotlin
// WRONG: IndexedSigner.credentialId is Base64URL  — indexer returns HEX here
// CORRECT: hex-encoded (no 0x prefix). Convert to Base64URL before matching against the
//          SDK's internal credential IDs. Util.hexToBytes is internal; use Kotlin's
//          stdlib String.hexToByteArray() plus the public Util.base64urlEncode:
//            val base64url = Util.base64urlEncode(indexedSigner.credentialId.hexToByteArray())
```

---

## Deterministic Address Derivation

The contract address for a smart account is deterministic given the same credential ID, deployer, and network passphrase. This property comes directly from how Soroban computes contract IDs.

```kotlin
suspend fun SmartAccountUtils.deriveContractAddress(
    credentialId: ByteArray,        // raw bytes (NOT Base64URL)
    deployerPublicKey: String,      // G-address of deployer
    networkPassphrase: String
): String                           // C-address
```

Algorithm:

```
salt         = SHA-256(credentialId)
deployerAddr = SCAddress::Account(deployerPublicKey)
networkId    = SHA-256(networkPassphrase as UTF-8)

preimage     = HashIdPreimage::ContractID {
                 networkId,
                 contractIdPreimage: ContractIdPreimage::FromAddress {
                   address: deployerAddr,
                   salt:    Uint256(salt)
                 }
               }

contractBytes = SHA-256(XDR_encode(preimage))
contractId    = StrKey.encodeContract(contractBytes)
```

Example:

```kotlin
val derived: String = SmartAccountUtils.deriveContractAddress(
    credentialId     = Util.base64urlDecode(walletResult.credentialId),
    deployerPublicKey = deployer.getAccountId(),
    networkPassphrase = "Test SDF Network ; September 2015"
)
```

Use this for wallet discovery without an indexer: derive the address, then verify it exists via `SorobanServer.getContractData` with the contract instance ledger key.

### Also exposed

```kotlin
suspend fun SmartAccountUtils.getContractSalt(credentialId: ByteArray): ByteArray
fun SmartAccountUtils.normalizeSignature(derSignature: ByteArray): ByteArray
fun SmartAccountUtils.extractPublicKeyFromRegistration(
    publicKey: ByteArray? = null,
    authenticatorData: ByteArray? = null,
    attestationObject: ByteArray? = null
): ByteArray
```

`normalizeSignature` converts a DER-encoded secp256r1 signature to 64-byte compact `r || s` format with low-S normalization — required for Soroban signature verification.

---

## Deployer Details

The deployer is the Stellar keypair whose G-address signs the deploy transaction. Its public key participates in contract address derivation, so the contract address is deterministic per deployer + credential.

### Default deployer

```kotlin
// Internally: KeyPair.fromSecretSeed(SHA256("openzeppelin-smart-account-kit"))
val default: KeyPair = OZSmartAccountConfig.createDefaultDeployer()
```

All compatible OZ Smart Account SDKs use the same derivation, so addresses derived from the same credential match across SDK implementations.

The default deployer's secret seed is **publicly derivable** — anyone who knows the SDK can reconstruct the keypair. This is safe by design because the deployer has **no post-deploy authority**: after the smart account is deployed, only the configured signers (passkeys, delegated, Ed25519) can authorize operations. The deployer is not a signer, not an admin, and cannot move funds or change policies.

What the publicly-derivable default does mean:

- **Shared G-address on-chain.** Every app using the default deployer shows the same deployer public key on each deploy transaction — no attribution, no way to distinguish contracts deployed by different wallet providers.
- **Anyone can submit deploys from it.** If the shared G-address is funded on mainnet, anyone who knows the derivation can spend its XLM on deploys — treat the default deployer as a testnet convenience, not a production account.
- **Safe when funded only for deploy.** With a relayer the deployer never holds funds; this is the recommended production setup for the default deployer.

Set `deployerKeypair` to a keypair you control for mainnet attribution (wallet-provider identity on-chain) and to avoid the shared-address concerns above.

### Custom deployer

Production wallet providers typically use a custom deployer for attribution (their public key appears on-chain on deploys):

```kotlin
val myDeployer = KeyPair.fromSecretSeed(secretSeedCharArray)

val config = OZSmartAccountConfig(
    rpcUrl = rpcUrl,
    networkPassphrase = passphrase,
    accountWasmHash = wasmHash,
    webauthnVerifierAddress = verifier,
    deployerKeypair = myDeployer       // <<<
)
```

Tradeoff: clients that do not know the deployer keypair cannot derive addresses locally. Run an indexer for discovery.

### Fee payment summary

| Setup | Who pays deploy fee |
|-------|---------------------|
| Relayer configured | Relayer (via fee-bump) |
| No relayer, default deployer | Default deployer G-address (must be funded) |
| No relayer, custom deployer | Your custom deployer G-address (must be funded) |

### Going to mainnet

Testnet examples throughout this file assume `Network.TESTNET.networkPassphrase` and FriendBot. Before switching to mainnet:

- Set `networkPassphrase = Network.PUBLIC.networkPassphrase` in `OZSmartAccountConfig`.
- Point `rpcUrl` at a mainnet Soroban RPC (not `https://soroban-testnet.stellar.org`).
- Stop using FriendBot. `FriendBot.fundTestnetAccount(...)` does NOT throw on mainnet — it hard-codes the testnet friendbot URL and silently sends the request against testnet regardless of your network passphrase, so mainnet accounts never get funded and the call appears to succeed or fail against the wrong network. Fund mainnet accounts out-of-band with real XLM instead.
- Set `autoFund = false` on `createWallet(...)`. On testnet, `autoFund` transfers XLM from the deployer into the freshly deployed wallet; on mainnet that is a real XLM transfer from whatever mainnet source pays for the call (via the relayer if configured, else the deployer directly). The SDK does not treat `autoFund` specially for relayer routing — whether a relayer sponsors it is a relayer-operator policy question, not an SDK guarantee. Unless you have explicit mainnet funding plumbed through, leave `autoFund = false` and fund wallets out-of-band.
- Replace the default deployer with a custom `deployerKeypair` for attribution and to avoid the shared-address concerns described above. If you keep the default deployer, fund its G-address with real XLM or configure a relayer.
- Use a mainnet relayer you operate or contractually trust. Every fee-bump costs real XLM — account for the operational budget.
- Evaluate the default mainnet indexer. `OZIndexerClient.DEFAULT_INDEXER_URLS` ships with a mainnet entry (an SDF-operated `sdf-ecosystem.workers.dev` endpoint). The default works, but it is a third-party dependency in your data path — for production wallets with privacy or availability requirements, set `indexerUrl` to your own deployment or leave `indexerUrl = null` and rely on deterministic address derivation.
- Replace any testnet-only contract addresses (WASM hash, WebAuthn verifier, policy contracts) with the corresponding mainnet values. Cross-check against the network passphrase before deploying.
- Shorten `signatureExpirationLedgers` from the default 720 (~1 h) for high-value flows — see the Configuration table.
- Audit the `storage` adapter — `InMemoryStorageAdapter` will silently lose credentials on process exit, permanently locking users out of mainnet funds.

---

## Error Handling

All SDK errors are subclasses of `SmartAccountException` and carry a `code: SmartAccountErrorCode` plus a `message: String`.

```kotlin
sealed class SmartAccountException(
    val code: SmartAccountErrorCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)
```

### Exception hierarchy

| Base | Codes | Variants |
|------|-------|----------|
| `ConfigurationException` | 1001-1002 | `InvalidConfig`, `MissingConfig` |
| `WalletException` | 2001-2003 | `NotConnected`, `AlreadyExists`, `NotFound` |
| `CredentialException` | 3001-3004 | `NotFound`, `AlreadyExists`, `Invalid`, `DeploymentFailed` |
| `WebAuthnException` | 4001-4004 | `RegistrationFailed`, `AuthenticationFailed`, `NotSupported`, `Cancelled` |
| `TransactionException` | 5001-5004 | `SimulationFailed`, `SigningFailed`, `SubmissionFailed`, `Timeout` |
| `SignerException` | 6001-6002 | `NotFound`, `Invalid` |
| `ValidationException` | 7001-7003 | `InvalidAddress`, `InvalidAmount`, `InvalidInput` |
| `StorageException` | 8001-8002 | `ReadFailed`, `WriteFailed` |
| `SessionException` | 9001-9002 | `Expired`, `Invalid` |
| `IndexerException` | 10001-10002 | `RequestFailed`, `Timeout` |

### Handling pattern

```kotlin
try {
    val wallet = kit.walletOperations.createWallet(userName = "Alice", autoSubmit = true)
    println("Created: ${wallet.contractId}")
} catch (e: SmartAccountException) {
    when (e) {
        is WebAuthnException.Cancelled ->
            // All three platform adapters (Android / Apple / JS) map user
            // cancellation cleanly to Cancelled — this is the sole branch for
            // a neutral "user dismissed the prompt" UI state.
            println("User cancelled biometric prompt")
        is WebAuthnException.AuthenticationFailed ->
            // Non-cancellation WebAuthn failures: no matching credential for
            // the allow-list, origin / rpId mismatch, user-verification failed,
            // authenticator timeout, or platform-specific errors.
            println("WebAuthn authentication failed: ${e.message}")
        is WebAuthnException.NotSupported ->
            println("WebAuthn not configured: ${e.message}")
        is ConfigurationException.MissingConfig ->
            println("Missing configuration: ${e.message}")
        is TransactionException.SimulationFailed ->
            println("Simulation failed: ${e.message}")
        is TransactionException.SubmissionFailed ->
            println("Submission failed: ${e.message}")
        is WalletException.NotFound ->
            println("Wallet not found on-chain")
        is CredentialException.DeploymentFailed ->
            println("Deployment failed: ${e.message}")
        else ->
            println("Error [${e.code.code}]: ${e.message}")
    }
}
```

### wrapError (internal utility)

```kotlin
val wrapped = SmartAccountException.wrapError(
    someThrowable,
    defaultCode = SmartAccountErrorCode.INVALID_INPUT
)
```

Wraps a non-SDK throwable into the appropriate `SmartAccountException` subclass. Pass-through for existing `SmartAccountException`s.

### Contract error codes

Contract error codes and their meanings live in [smart_accounts_policies.md — Contract Error Codes](./smart_accounts_policies.md#contract-error-codes). The SDK's `ContractErrorCodes` object (`com.soneso.stellar.sdk.smartaccount.core`) exposes five of them as constants (`MATH_OVERFLOW`, `KEY_DATA_TOO_LARGE`, `CONTEXT_RULE_IDS_LENGTH_MISMATCH`, `NAME_TOO_LONG`, `UNAUTHORIZED_SIGNER`).

---

## Contract Limits

OZ smart account contract limits (enforced client-side in `OZConstants` and on-chain):

| Limit | Value |
|-------|-------|
| `OZConstants.MAX_SIGNERS` (per context rule) | 15 |
| `OZConstants.MAX_POLICIES` (per context rule) | 5 |
| `OZConstants.DEFAULT_SESSION_EXPIRY_MS` | 604_800_000 (7 days) |
| `OZConstants.DEFAULT_TIMEOUT_SECONDS` | 30 |
| `OZConstants.DEFAULT_RELAYER_TIMEOUT_MS` | 360_000 (6 min) |
| `OZConstants.DEFAULT_INDEXER_TIMEOUT_MS` | 10_000 |
| `OZConstants.WEBAUTHN_TIMEOUT_MS` | 60_000 |
| `OZConstants.FRIENDBOT_RESERVE_XLM` | 5 |

See [smart_accounts_policies.md](./smart_accounts_policies.md) for adding signers and policies under these limits.
