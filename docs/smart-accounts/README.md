# Smart Account Kit

The Smart Account Kit provides passkey-authenticated smart accounts on Stellar using OpenZeppelin's Soroban contracts. Users authenticate with biometrics (Face ID, fingerprint, security keys) instead of managing secret keys. The SDK handles wallet creation, contract deployment, transaction signing, signer management, and policy enforcement across all KMP targets.

New to smart accounts? Start with the [onboarding guide](onboarding.md) for background on how smart accounts, passkeys, and the on-chain contracts work.

## Overview

A smart account is a Soroban contract that replaces traditional Stellar key management with programmable authorization. Each smart account supports:

- **Passkey authentication**: Users sign transactions with WebAuthn (secp256r1) instead of Ed25519 secret keys
- **Multiple signers**: Combine passkeys, delegated Stellar accounts, and Ed25519 keys on a single account
- **Context rules**: Define different authorization requirements for different operation types
- **Policies**: Enforce authorization constraints such as spending limits and multi-signature thresholds, or add custom policy contracts
- **Fee sponsoring**: Submit transactions through a relayer so users never pay gas fees
- **Session management**: Silent reconnection without re-authentication for 7 days (configurable)

The kit wraps the OpenZeppelin smart account contracts deployed on Soroban. The on-chain contract stores signers and policies; the SDK handles WebAuthn ceremonies, transaction assembly, authorization entry signing, and submission.

## Architecture

```
+-----------------------------------------------------------------------+
|                         Your Application                              |
+-----------------------------------------------------------------------+
        |
        v
+-----------------------------------------------------------------------+
|                       OZSmartAccountKit                               |
|  Entry point. Created via OZSmartAccountKit.create(config).           |
|  Provides sub-managers as lazy properties:                            |
|                                                                       |
|  +-----------------------+  +----------------------------+            |
|  | walletOperations      |  | transactionOperations      |            |
|  | (OZWalletOperations)  |  | (OZTransactionOperations)  |            |
|  +-----------------------+  +----------------------------+            |
|  +-----------------------+  +----------------------------+            |
|  | signerManager         |  | contextRuleManager         |            |
|  | (OZSignerManager)     |  | (OZContextRuleManager)     |            |
|  +-----------------------+  +----------------------------+            |
|  +-----------------------+  +----------------------------+            |
|  | policyManager         |  | multiSignerManager         |            |
|  | (OZPolicyManager)     |  | (OZMultiSignerManager)     |            |
|  +-----------------------+  +----------------------------+            |
|  +-----------------------+  +----------------------------+            |
|  | credentialManager     |  | events                     |            |
|  | (OZCredentialManager) |  | (SmartAccountEventEmitter) |            |
|  +-----------------------+  +----------------------------+            |
+-----------------------------------------------------------------------+
        |                    |                      |
        v                    v                      v
+------------------+  +------------------+  +-----------------------+
| WebAuthnProvider |  | StorageAdapter   |  | ExternalWalletAdapter |
| (platform impl)  |  | (platform impl)  |  | (optional)            |
+------------------+  +------------------+  +-----------------------+
        |                    |
        v                    v
+----------------+  +------------------+
| Platform       |  | Credential &     |
| Biometric UI   |  | Session Store    |
| (OS-level)     |  | (Keychain, etc.) |
+----------------+  +------------------+

        OZSmartAccountKit also connects to:

+----------------+  +------------------+  +---------------------+
| SorobanServer  |  | OZRelayerClient  |  | OZIndexerClient     |
| (Soroban RPC)  |  | (fee sponsoring) |  | (credential lookup) |
+----------------+  +------------------+  +---------------------+
```

**OZSmartAccountKit** is the single entry point. It holds configuration, connection state (`isConnected`, `credentialId`, `contractId`), and exposes all operations through sub-managers. Each sub-manager receives a reference to the kit and uses its Soroban server, relayer, and storage internally.

**WebAuthnProvider** is a platform-specific interface you implement (or use the provided implementations for Android, iOS, and browser). It triggers the OS-level biometric prompt and returns raw WebAuthn attestation/assertion data.

**StorageAdapter** persists credentials and sessions. The SDK includes `InMemoryStorageAdapter` for testing. Production apps implement this interface using platform storage (Keychain, SharedPreferences, localStorage).

## Quick Start

This example creates a smart account, connects to it, and sends a token transfer. All class names, method names, and parameters shown here are from the actual SDK.

```kotlin
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.smartaccount.core.*

// Step 1: Configure the kit
//
// You need four values from the OpenZeppelin deployment:
// - rpcUrl: Soroban RPC endpoint
// - networkPassphrase: Stellar network identifier
// - accountWasmHash: SHA-256 hash of the smart account WASM (hex string)
// - webauthnVerifierAddress: Deployed WebAuthn verifier contract (C-address)

val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "a1b2c3d4e5f6...",  // hex string from contract deployment
    webauthnVerifierAddress = "CBCD1234EFGH5678IJKL9012MNOP3456QRST7890UVWX1234ABCDEFGH",
    relayerUrl = "https://relayer.example.com",    // optional: enables fee sponsoring
    indexerUrl = "https://indexer.example.com",     // optional: enables credential lookup
    webauthnProvider = MyWebAuthnProvider()          // your platform-specific implementation
)

// Step 2: Create the kit
//
// Storage defaults to InMemoryStorageAdapter (for testing). For production,
// set a platform-specific adapter (Keychain, SharedPreferences, etc.)
// via the `storage` field in OZSmartAccountConfig.

val kit = OZSmartAccountKit.create(config)

// Step 3: Create a new wallet
//
// This triggers a WebAuthn registration ceremony (biometric prompt).
// The SDK generates a passkey, derives a deterministic contract address,
// deploys the smart account contract, and funds it via Friendbot (testnet).

val wallet = kit.walletOperations.createWallet(
    userName = "Alice",
    autoSubmit = true,
    autoFund = true,
    nativeTokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
)

// wallet.credentialId  -- Base64URL-encoded credential ID
// wallet.contractId    -- Stellar C-address of the deployed contract
// wallet.transactionHash -- deployment transaction hash

// Step 4: Transfer tokens
//
// This triggers a WebAuthn authentication ceremony (biometric prompt)
// to sign the authorization entry. If a relayer is configured,
// the transaction is fee-sponsored.

val result = kit.transactionOperations.transfer(
    tokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
    recipient = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ",
    amount = 10.0  // 10 XLM (automatically converted to stroops)
)

if (result.success) {
    println("Transfer succeeded. Hash: ${result.hash}")
} else {
    println("Transfer failed: ${result.error}")
}

// Step 5: Disconnect when done
//
// Clears in-memory state and stored session.
// Credentials remain in storage for reconnection.

kit.disconnect()
```

### Reconnecting to an Existing Wallet

On app relaunch, call `connectWallet()` to restore the session or re-authenticate:

```kotlin
val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "a1b2c3d4e5f6...",
    webauthnVerifierAddress = "CBCD1234...",
    storage = myStorageAdapter  // platform-specific adapter for credential persistence
)
val kit = OZSmartAccountKit.create(config)

val connection = kit.walletOperations.connectWallet()

if (connection.restoredFromSession) {
    // Silently reconnected from saved session (no biometric prompt)
    println("Reconnected to ${connection.contractId}")
} else {
    // User authenticated with passkey
    println("Authenticated and connected to ${connection.contractId}")
}
```

Force fresh authentication when needed (e.g., before sensitive operations):

```kotlin
val connection = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(fresh = true)
)
```

Connect directly with known credentials (skips WebAuthn and session check):

```kotlin
val connection = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(
        credentialId = "abc123...",
        contractId = "CABC..."
    )
)
```

### Managing Signers

Add additional signers to a context rule so multiple parties can authorize transactions:

```kotlin
// Add a delegated Stellar account as a signer on the Default context rule (ID 0)
val addResult = kit.signerManager.addDelegated(
    contextRuleId = 0u,
    address = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
)

// Add another passkey signer
val passkeyResult = kit.signerManager.addPasskey(
    contextRuleId = 0u,
    publicKey = otherPublicKey,       // 65-byte uncompressed secp256r1 key
    credentialId = otherCredentialId  // raw credential ID bytes
)

// Remove a signer
val delegatedSigner = DelegatedSigner(
    address = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
)
val removeResult = kit.signerManager.removeSigner(
    contextRuleId = 0u,
    signer = delegatedSigner
)
```

### Adding Policies

Policies enforce constraints on context rules. Each context rule supports up to 5 policies.

```kotlin
// Require 2-of-3 signers to authorize
val thresholdResult = kit.policyManager.addSimpleThreshold(
    contextRuleId = 0u,
    policyAddress = "CPOLICY1234...",
    threshold = 2u
)

// Limit spending to 1000 XLM per day
val limitResult = kit.policyManager.addSpendingLimit(
    contextRuleId = 0u,
    policyAddress = "CPOLICY5678...",
    spendingLimit = 1000.0,
    periodLedgers = SmartAccountConstants.LEDGERS_PER_DAY.toUInt()
)
```

For custom policy contracts beyond the built-in types, use `addPolicy()` with policy-specific installation parameters:

```kotlin
val result = kit.policyManager.addPolicy(
    contextRuleId = 0u,
    policyAddress = "CCUSTOMPOLICY...",
    installParams = Scv.toMap(linkedMapOf(
        Scv.toSymbol("my_param") to Scv.toUint32(42u)
    ))
)
```

### Error Handling

All operations throw typed exceptions from the `SmartAccountException` hierarchy:

```kotlin
try {
    val wallet = kit.walletOperations.createWallet(userName = "Alice", autoSubmit = true)
} catch (e: SmartAccountException) {
    when (e) {
        is WebAuthnException.Cancelled ->
            println("User cancelled biometric prompt")
        is WebAuthnException.NotSupported ->
            println("WebAuthn not configured: ${e.message}")
        is TransactionException.SimulationFailed ->
            println("Contract simulation failed: ${e.message}")
        is TransactionException.SubmissionFailed ->
            println("Transaction submission failed: ${e.message}")
        is WalletException.NotFound ->
            println("Wallet not found on-chain")
        else ->
            println("Error [${e.code.code}]: ${e.message}")
    }
}
```

## Configuration Reference

`OZSmartAccountConfig` holds all parameters. Four fields are required; the rest have defaults.

### Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `rpcUrl` | `String` | Soroban RPC endpoint URL (e.g., `"https://soroban-testnet.stellar.org"`) |
| `networkPassphrase` | `String` | Stellar network passphrase. Use `"Test SDF Network ; September 2015"` for testnet or `"Public Global Stellar Network ; September 2015"` for mainnet. |
| `accountWasmHash` | `String` | SHA-256 hash (hex) of the smart account contract WASM binary. Obtained after uploading the contract to the network. |
| `webauthnVerifierAddress` | `String` | Contract address (C-address, 56 chars) of the deployed WebAuthn signature verifier. Must start with `C`. |

### Optional Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `deployerKeypair` | `KeyPair?` | `null` (uses default) | Keypair used for contract deployment. If null, derived from `SHA256("openzeppelin-smart-account-kit")`. See [How Wallet Deployment Works](#how-wallet-deployment-works). |
| `rpId` | `String?` | `null` | WebAuthn Relying Party ID. Should match your domain (e.g., `"example.com"`). If null, the browser uses the current origin. |
| `rpName` | `String` | `"Smart Account"` | Display name shown to users during WebAuthn ceremonies. |
| `sessionExpiryMs` | `Long` | `604800000` (7 days) | Session duration in milliseconds. Sessions enable reconnection without re-authentication. |
| `signatureExpirationLedgers` | `Int` | `720` (~1 hour) | Auth entry expiration in ledgers (~5 seconds per ledger). Prevents replay attacks. |
| `timeoutInSeconds` | `Int` | `30` | Default timeout for network operations. |
| `relayerUrl` | `String?` | `null` | Relayer endpoint for fee-sponsored transactions. When set, users do not pay gas fees. |
| `indexerUrl` | `String?` | `null` | Indexer endpoint for credential-to-contract discovery. Enables `connectWallet()` to find contracts by credential ID. |
| `webauthnProvider` | `WebAuthnProvider?` | `null` | Platform-specific WebAuthn implementation. Required for `createWallet()`, `connectWallet()`, and `transfer()`. |
| `storage` | `StorageAdapter` | `InMemoryStorageAdapter()` | Credential and session persistence. Use a platform-specific adapter (Keychain, SharedPreferences, localStorage) in production. |
| `externalWallet` | `ExternalWalletAdapter?` | `null` | Adapter for external wallet signing (e.g., Freighter, Lobstr). When set, enables delegated signing workflows. |

### Builder Pattern

For configuration with many optional fields, use the builder:

```kotlin
val config = OZSmartAccountConfig.builder(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "a1b2c3d4e5f6...",
    webauthnVerifierAddress = "CBCD1234..."
)
    .rpName("My Wallet App")
    .sessionExpiryMs(86_400_000L)  // 1 day
    .relayerUrl("https://relayer.example.com")
    .indexerUrl("https://indexer.example.com")
    .signatureExpirationLedgers(1440)  // ~2 hours
    .storage(myStorageAdapter)
    .externalWallet(myExternalWallet)
    .build()
```

## How Wallet Deployment Works

When `createWallet()` is called, the SDK deploys a Soroban smart account contract. The deployment involves two roles:

**Deployer keypair**: The deployer is the source account of the deployment transaction. It serves two purposes:

1. **Address derivation**: The contract address is computed from `hash(deployer_public_key + credential_id)`. This makes the address deterministic — the same credential and deployer always produce the same contract address.
2. **Transaction signing**: The deployer signs the deployment transaction as the source account.

After deployment, the deployer has no privileges over the contract. Only the configured signers (passkeys, delegated accounts, Ed25519 keys) can authorize operations on the smart account.

**Fee payment**: The deployer account pays the deployment transaction fee. When a relayer is configured, the relayer can sponsor the fee instead. If you use the default deployer (derived from a well-known seed — see below), you need either a relayer for fee sponsoring or to fund the deployer account before deployment. You can also provide your own funded keypair via `deployerKeypair` in the config.

## Cross-SDK Interoperability

The KMP SDK produces identical on-chain results as the [TypeScript Smart Account Kit](https://github.com/kalepail/smart-account-kit). Wallets created by one SDK can be managed by the other.

### Shared Deployer Keypair

Both SDKs derive the same default deployer from `SHA256("openzeppelin-smart-account-kit")`. Since contract addresses are computed from the deployer's public key and the credential ID, using the same deployer ensures that the same passkey credential maps to the same contract address in both SDKs. This allows a wallet created through a web app (TypeScript SDK) to be accessed from a mobile app (KMP SDK) without an indexer lookup.

The default deployer's secret seed is publicly derivable. It is intended to be used with a relayer that sponsors transaction fees, or funded externally. To use a private deployer, set `deployerKeypair` in the config — but both SDKs must use the same deployer for cross-SDK wallet discovery to work.

```kotlin
// The default deployer is deterministic across SDKs
val deployer = OZSmartAccountConfig.createDefaultDeployer()
// Produces the same keypair as the TS SDK's default deployer
```

### Deterministic Contract Addresses

Given the same credential ID and deployer, `SmartAccountUtils.deriveContractAddress()` computes the same C-address. This enables:

- Wallet discovery without an indexer (derive the address, check if it exists on-chain)
- Cross-platform wallet access (create on mobile, use on web)
- Consistent address display across applications

### Signer Format Compatibility

Signer representations (`DelegatedSigner`, `ExternalSigner`) encode to the same Soroban SCVal structure. Signers added by the TypeScript SDK are recognized by the KMP SDK and vice versa. The `ExternalSigner.webAuthn()` factory produces the same `keyData` format (65-byte public key + credential ID bytes) used by the TypeScript SDK.

## Contract Limits

The OpenZeppelin smart account contract enforces these limits:

| Limit | Value |
|-------|-------|
| Maximum context rules per account | 15 |
| Maximum signers per context rule | 15 |
| Maximum policies per context rule | 5 |

These limits are defined in `SmartAccountConstants` and validated client-side before submitting transactions.

## Sub-Pages

| Guide | Description |
|-------|-------------|
| [Onboarding Guide](onboarding.md) | Smart account concepts, passkeys, on-chain contract interface, end-to-end lifecycle |
| [WebAuthn Setup: Android](webauthn-android.md) | Android Credential Manager integration, Digital Asset Links setup |
| [WebAuthn Setup: iOS](webauthn-ios.md) | iOS AuthenticationServices integration, apple-app-site-association setup |
