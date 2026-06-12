# Smart Account Kit

The Smart Account Kit provides passkey-authenticated smart accounts on Stellar using OpenZeppelin's Soroban contracts. Users authenticate with biometrics (Face ID, fingerprint, security keys) instead of managing secret keys. The SDK handles wallet creation, contract deployment, transaction signing, signer management, and policy enforcement across all KMP targets.

New to smart accounts? Start with the [onboarding guide](onboarding.md) for background on how smart accounts, passkeys, and the on-chain contracts work.

## Overview

A smart account is a Soroban contract that replaces traditional Stellar key management with programmable authorization. Each smart account supports:

- **Passkey authentication**: Users sign transactions with WebAuthn (secp256r1) instead of Ed25519 secret keys
- **Multiple signers**: Combine passkeys, delegated Stellar addresses (accounts or contracts), and Ed25519 keys on a single account. All three signer types support full multi-signer signing through the `OZMultiSignerManager` pipeline.
- **Context rules**: Define different authorization requirements for different operation types
- **Policies**: Enforce authorization constraints such as spending limits and multi-signature thresholds, or add custom policy contracts
- **Fee sponsoring**: Submit transactions through a relayer so users never pay gas fees
- **Credential discovery**: Optional indexer lookup that maps a passkey credential to one or more deployed smart-account contracts
- **Session management**: Silent reconnection without re-authentication for 7 days (configurable)

The kit wraps the OpenZeppelin smart account contracts deployed on Soroban. The on-chain contract stores signers and policies; the SDK handles WebAuthn ceremonies, transaction assembly, authorization entry signing, and submission.

## Architecture

The kit is split into two layers: a protocol-agnostic `core/` layer (signer types, signature wrappers, the `WebAuthnProvider` interface, and crypto helpers usable by any Soroban `CustomAccountInterface` contract) and an OpenZeppelin-specific `oz/` layer (the kit, managers, relayer/indexer clients, and storage adapters).

```
+-----------------------------------------------------------------------+
|                         Your Application                              |
+-----------------------------------------------------------------------+
        |
        v
+-----------------------------------------------------------------------+
|                       OZSmartAccountKit                               |
|  Entry point. Created via OZSmartAccountKit.create(config).           |
|  Provides sub-managers as properties:                                 |
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
|  +--------------------------+                                         |
|  | externalSigners          |                                         |
|  | (OZExternalSignerManager)|                                         |
|  +--------------------------+                                         |
+-----------------------------------------------------------------------+
        |                    |                      |
        v                    v                      v
+------------------+  +------------------+  +-----------------------+
| WebAuthnProvider |  | StorageAdapter   |  | ExternalWalletAdapter |
| (platform impl)  |  | (platform impl)  |  | (optional)            |
+------------------+  +------------------+  +-----------------------+

        OZSmartAccountKit also uses:

+----------------+  +------------------+  +---------------------+
| SorobanServer  |  | OZRelayerClient  |  | OZIndexerClient     |
| (Soroban RPC)  |  | (fee sponsoring) |  | (credential lookup) |
+----------------+  +------------------+  +---------------------+
```

**OZSmartAccountKit** is the single entry point. It holds configuration, connection state (`isConnected`, `credentialId`, `contractId`), and exposes all operations through sub-managers. Each sub-manager receives a reference to the kit and uses its Soroban server, relayer, indexer, and storage internally.

**WebAuthnProvider** is a platform-specific interface you implement, or use the provided implementation. It triggers the OS-level biometric prompt and returns raw WebAuthn attestation/assertion data.

**StorageAdapter** persists credentials and sessions. The SDK includes an in-memory adapter for testing and platform adapters for production: `KeychainStorageAdapter` and `UserDefaultsStorageAdapter` (Apple), `AndroidStorageAdapter`, `IndexedDBStorageAdapter` and `LocalStorageAdapter` (web). See the platform guides.

**External signing** flows through one kit-owned `OZExternalSignerManager`, exposed as `kit.externalSigners` — the single front door for all external (non-passkey) signers. Supply adapters for external wallet signers (e.g. Freighter or WalletConnect) and for raw Ed25519 signers (e.g. an HSM or remote signing service) via configuration, or register in-memory keypairs at runtime. See the [demo app](../../smart-account-demo) for examples.

## Quick Start

This example creates a smart account, connects to it, and sends a token transfer. All class names, method names, and parameters shown here are from the actual SDK.

```kotlin
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.smartaccount.core.*

// Step 1: Configure the kit
//
// Required fields come from the OpenZeppelin deployment:
// - rpcUrl: Soroban RPC endpoint
// - networkPassphrase: Stellar network identifier
// - accountWasmHash: SHA-256 hash of the smart account WASM (64-char hex)
// - webauthnVerifierAddress: deployed WebAuthn verifier contract (C-address)
//
// Optional fields configure platform adapters and services.

val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = Network.TESTNET.networkPassphrase,
    accountWasmHash = "<64-char hex WASM hash>",
    webauthnVerifierAddress = "<C-address of the WebAuthn verifier>",
    relayerUrl = "https://relayer.example.com",    // optional: enables fee sponsoring
    indexerUrl = "https://indexer.example.com",     // optional: enables credential lookup
    webauthnProvider = MyWebAuthnProvider(),         // optional: platform-specific passkey implementation
    storage = MyStorageAdapter(),                    // optional: defaults to InMemoryStorageAdapter
    externalWallet = null                            // optional: external signer (e.g. Freighter)
)

// Step 2: Create the kit

val kit = OZSmartAccountKit.create(config)

// Step 3: Create a new wallet
//
// Triggers a WebAuthn registration ceremony (biometric prompt), derives a
// deterministic contract address, deploys the smart account contract, and
// funds it via Friendbot (testnet).

val wallet = kit.walletOperations.createWallet(
    userName = "Alice",
    autoSubmit = true,
    autoFund = true,
    nativeTokenContract = "<C-address of native XLM SAC>"
)

// wallet.credentialId         -- Base64URL-encoded credential ID
// wallet.contractId           -- Stellar C-address of the deployed contract
// wallet.publicKey            -- 65-byte uncompressed secp256r1 public key
// wallet.signedTransactionXdr -- signed deploy envelope (always populated)
// wallet.transactionHash      -- deployment tx hash (set when autoSubmit = true)

// Step 4: Transfer tokens
//
// Triggers a WebAuthn authentication ceremony (biometric prompt) to sign the
// authorization entry. If a relayer is configured, the transaction is fee-sponsored.

val result = kit.transactionOperations.transfer(
    tokenContract = "<C-address of token contract>",
    recipient = "<recipient G-address>",
    amount = "10"  // decimal amount (converted to the token's base units)
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

// Call close() when finished with the kit entirely: it releases the HTTP
// clients and in-memory keys. Call disconnect() first if you also want to
// end the session.
kit.close()
```

### Reconnecting to an Existing Wallet

On app relaunch, use a two-phase connect pattern. Phase 1 silently restores the session without prompting the user. If no session exists, show a connect button and let the user trigger Phase 2.

```kotlin
val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = Network.TESTNET.networkPassphrase,
    accountWasmHash = "<64-char hex WASM hash>",
    webauthnVerifierAddress = "<C-address of the WebAuthn verifier>",
    storage = myStorageAdapter  // platform-specific adapter for credential persistence
)
val kit = OZSmartAccountKit.create(config)

// Phase 1: Silent restore at app launch (no biometric prompt)
when (val result = kit.walletOperations.connectWallet()) {
    null -> {
        // No saved session -- show a "Connect" button in the UI
    }
    is ConnectWalletResult.Connected -> {
        println("Reconnected to ${result.contractId}")
    }
    is ConnectWalletResult.Ambiguous -> {
        // Unreachable for the silent restore path
    }
}

// Phase 2: User taps "Connect" -- triggers WebAuthn if no session
val result = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(prompt = true)
)
when (result) {
    null -> { /* unreachable when prompt = true */ }
    is ConnectWalletResult.Connected -> println("Connected to ${result.contractId}")
    is ConnectWalletResult.Ambiguous -> {
        // Indexer reported multiple contracts for this passkey. Show a picker
        // and reconnect with the chosen contract address (no second WebAuthn
        // ceremony required because we re-use result.credentialId).
        val chosen = showContractPicker(result.candidates)
        kit.walletOperations.connectWallet(
            OZWalletOperations.ConnectWalletOptions(
                credentialId = result.credentialId,
                contractId = chosen
            )
        )
    }
}
```

Force fresh authentication when needed (e.g., before sensitive operations):

```kotlin
val connection = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(fresh = true)
)
```

Connect directly with known credentials (skips WebAuthn and session check; the cascade is bypassed so the result is always `Connected` on success):

```kotlin
val connection = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(
        credentialId = "abc123...",
        contractId = "CABC..."
    )
)
```

### Retrying Failed Deployments

When `createWallet(autoSubmit = false)` is used, or if a deployment fails after the credential is created, use `deployPendingCredential()` to submit the deploy transaction later. The credential must exist in local storage. The `signedTransactionXdr` field on `CreateWalletResult` is always populated regardless of `autoSubmit`, so it can also be submitted externally.

```kotlin
val result = kit.walletOperations.deployPendingCredential(
    credentialId = wallet.credentialId,
    autoSubmit = true
)
println("Deployed: ${result.contractId}, tx: ${result.transactionHash}")
```

### Managing Signers

Add additional signers to a context rule so multiple parties can authorize transactions:

```kotlin
// Add a delegated Stellar account as a signer on the Default context rule (ID 0)
val addResult = kit.signerManager.addDelegated(
    contextRuleId = 0u,
    address = "<delegated G-address>"
)

// Add a new passkey signer (handles WebAuthn registration, credential storage,
// and on-chain signer addition in one step)
val passkeyResult = kit.signerManager.addNewPasskeySigner(
    contextRuleId = 0u,
    userName = "Alice backup device"
)
// passkeyResult.credentialId  -- Base64URL-encoded credential ID (no padding)
// passkeyResult.publicKey     -- 65-byte uncompressed secp256r1 public key
// passkeyResult.transactionResult -- on-chain submission result

// Low-level alternative: add a passkey signer with pre-extracted cryptographic materials
val lowLevelResult = kit.signerManager.addPasskey(
    contextRuleId = 0u,
    publicKey = otherPublicKey,       // 65-byte uncompressed secp256r1 key
    credentialId = otherCredentialId  // raw credential ID bytes
)

// Remove a signer by its on-chain signer ID
val removeResult = kit.signerManager.removeSigner(
    contextRuleId = 0u,
    signerId = 1u
)
```

### Adding Policies

Policies enforce constraints on context rules. Each context rule supports up to 5 policies. Built-in helpers cover the three OpenZeppelin policy contracts: `addSimpleThreshold`, `addWeightedThreshold`, and `addSpendingLimit`.

```kotlin
// Require 2-of-3 signers to authorize
val thresholdResult = kit.policyManager.addSimpleThreshold(
    contextRuleId = 0u,
    policyAddress = "<C-address of the simple-threshold policy>",
    threshold = 2u
)

// Limit spending to 1000 XLM per day
val limitResult = kit.policyManager.addSpendingLimit(
    contextRuleId = 0u,
    policyAddress = "<C-address of the spending-limit policy>",
    spendingLimit = "1000",
    periodLedgers = Util.LEDGERS_PER_DAY.toUInt()
)
```

For custom policy contracts beyond the built-in types, use `addPolicy()` with policy-specific install parameters:

```kotlin
val result = kit.policyManager.addPolicy(
    contextRuleId = 0u,
    policyAddress = "<C-address of the custom policy>",
    installParams = Scv.toMap(linkedMapOf(
        Scv.toSymbol("my_param") to Scv.toUint32(42u)
    ))
)
```

### Multi-Signer Operations

When a context rule requires multiple signers, use `kit.multiSignerManager` to coordinate signatures. `multiSignerTransfer()` handles token transfers; `multiSignerContractCall()` handles arbitrary external contract calls (e.g., governance votes, multisig swaps), authorized through the matching context rule; and `multiSignerExecuteAndSubmit()` routes a call through the smart account's `execute` entry point.

All three signer kinds — passkey (`SelectedSigner.Passkey`), delegated wallet (`SelectedSigner.Wallet`), and Ed25519 external (`SelectedSigner.Ed25519`) — may be mixed in the same `selectedSigners` list. Wallet and Ed25519 signers resolve through the kit-owned `kit.externalSigners` manager: register an in-memory key at runtime (`kit.externalSigners.addFromSecret(...)` / `kit.externalSigners.addEd25519FromRawKey(...)`) or supply an adapter at kit construction (`externalWallet` / `externalEd25519Adapter`).

See the [API Reference](api-reference.md#multi-signer-operations) for `SelectedSigner` types, custody models, and registration examples.

### Error Handling

All operations throw typed exceptions from the `SmartAccountException` hierarchy:

```kotlin
try {
    val wallet = kit.walletOperations.createWallet(userName = "Alice", autoSubmit = true)
} catch (e: SmartAccountException) {
    when (e) {
        is WebAuthnException.Cancelled ->
            println("User cancelled the biometric prompt")
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

`OZSmartAccountConfig` holds all parameters. Four fields are required; the rest have defaults. The constructor validates inputs and throws `ConfigurationException` on invalid values.

### Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `rpcUrl` | `String` | Soroban RPC endpoint URL (for example `https://soroban-testnet.stellar.org`). |
| `networkPassphrase` | `String` | Stellar network passphrase. Use `Network.TESTNET.networkPassphrase` for testnet or `Network.PUBLIC.networkPassphrase` for mainnet. |
| `accountWasmHash` | `String` | SHA-256 hash (64 hex characters) of the smart account contract WASM binary. Obtained after uploading the contract to the network. |
| `webauthnVerifierAddress` | `String` | Contract address (C-address, 56 characters) of the deployed WebAuthn signature verifier. Must start with `C`. |

### Optional Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `deployerKeypair` | `KeyPair?` | `null` (uses default) | Keypair used for contract deployment. If `null`, derived from `SHA-256("openzeppelin-smart-account-kit")`. See [How Wallet Deployment Works](#how-wallet-deployment-works). |
| `sessionExpiryMs` | `Long` | `604800000` (7 days) | Session duration in milliseconds. Sessions enable reconnection without re-authentication. |
| `signatureExpirationLedgers` | `Int` | `720` (~1 hour) | Auth entry expiration in ledgers (~5 seconds per ledger). Prevents replay attacks. |
| `timeoutInSeconds` | `Int` | `30` | Sets each transaction's TimeBounds (`max_time = now + timeoutInSeconds`; `0` = no expiry), bounding how long a signed transaction stays valid for submission. Must be `>= 0`. |
| `relayerUrl` | `String?` | `null` | Relayer endpoint for fee-sponsored transactions. When set, users do not pay gas fees. |
| `indexerUrl` | `String?` | `null` | Indexer endpoint for credential-to-contract discovery. When `null`, falls back to the built-in per-network default (testnet/mainnet). |
| `webauthnProvider` | `WebAuthnProvider?` | `null` | Platform-specific WebAuthn implementation. Required for `createWallet`, `connectWallet(prompt = true)`, `authenticatePasskey`, and any passkey-signing flow. |
| `storage` | `StorageAdapter` | `InMemoryStorageAdapter()` | Credential and session persistence. Use a platform-specific adapter (Keychain, SharedPreferences, localStorage) in production. |
| `externalWallet` | `ExternalWalletAdapter?` | `null` | Wallet adapter (e.g., Freighter, Lobstr) backing the adapter custody model for `SelectedSigner.Wallet` signers. The kit injects it into `kit.externalSigners`. |
| `externalEd25519Adapter` | `OZExternalEd25519SignerAdapter?` | `null` | Ed25519 adapter (hardware wallet, HSM, remote signing service) backing the adapter custody model for `SelectedSigner.Ed25519` signers. The kit injects it into `kit.externalSigners`. |
| `maxContextRuleScanId` | `UInt` | `50` | Upper bound on the context-rule IDs scanned when listing rules without an explicit scan limit. |

### Builder Pattern

For configuration with many optional fields, use the builder:

```kotlin
val config = OZSmartAccountConfig.builder(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = Network.TESTNET.networkPassphrase,
    accountWasmHash = "<64-char hex WASM hash>",
    webauthnVerifierAddress = "<C-address of the WebAuthn verifier>"
)
    .sessionExpiryMs(86_400_000L)  // 1 day
    .relayerUrl("https://relayer.example.com")
    .indexerUrl("https://indexer.example.com")
    .signatureExpirationLedgers(1440)  // ~2 hours
    .storage(myStorageAdapter)
    .externalWallet(myExternalWallet)
    .build()
```

## Testnet Contract Addresses

The SDK needs two values that depend on the network: a WASM hash (`accountWasmHash`) for the uploaded smart account binary, and a verifier contract address (`webauthnVerifierAddress`). Both can change when contracts are upgraded, testnet is reset, or their TTL expires.

Current testnet values are in `DemoConfig.kt` in the demo app:

```
smart-account-demo/shared/src/commonMain/kotlin/com/soneso/smartdemo/config/DemoConfig.kt
```

### Uploading Your Own WASM

If the testnet hash has expired or you need a custom contract, clone the [OpenZeppelin stellar-contracts](https://github.com/OpenZeppelin/stellar-contracts) repository and build/upload:

```bash
# Build the smart account WASM
stellar contract build --package multisig-account-example

# Upload to testnet and capture the returned hash
stellar contract upload \
  --network testnet \
  --source <deployer-secret> \
  --wasm target/wasm32v1-none/release/multisig_account_example.wasm
```

The command prints a hex string. Use it as `accountWasmHash` in your `OZSmartAccountConfig`.

## How Wallet Deployment Works

When `createWallet()` is called, the SDK deploys a Soroban smart account contract. The deployment involves two roles:

**Deployer keypair**: The deployer is the source account of the deployment transaction. It serves two purposes:

1. **Address derivation**: The credential ID is hashed (SHA-256) to a salt, and the contract address derives from the deployer's public key, that salt, and the network passphrase. This makes the address deterministic — the same credential, deployer, and network always produce the same contract address.
2. **Transaction signing**: The deployer signs the deployment transaction as the source account.

After deployment, the deployer has no privileges over the contract. Only the configured signers (passkeys, delegated accounts, Ed25519 keys) can authorize operations on the smart account.

**Fee payment**: The deployer account pays the deployment transaction fee. When a relayer is configured, the relayer can sponsor the fee instead. If you use the default deployer (derived from a well-known seed — see below), you need either a relayer for fee sponsoring or to fund the deployer account before deployment. You can also provide your own funded keypair via `deployerKeypair` in the config.

## Deterministic Address Derivation

Contract address derivation is deterministic: given the same deployer keypair, credential ID, and network passphrase, `SmartAccountUtils.deriveContractAddress()` always produces the same contract address. This enables wallet discovery without an indexer (derive the address, check if it exists on-chain), consistent address display across applications, and cross-implementation verification.

### Default Deployer

The SDK provides a default deployer derived from `SHA-256("openzeppelin-smart-account-kit")`. This default is suitable for testing and simple deployments. Other OpenZeppelin Smart Account SDK implementations use the same derivation, so all SDKs produce identical results from the same inputs.

```kotlin
val deployer = OZSmartAccountConfig.createDefaultDeployer()
```

The default deployer's secret seed is publicly derivable. It is intended to be used with a relayer that sponsors transaction fees, or funded externally.

### Custom Deployers

Production wallet applications will typically use a custom deployer for attribution and traceability. The deployer signs the deployment transaction, so its public key is visible on-chain -- a custom deployer gives the wallet provider identity and allows distinguishing deployments by different providers.

Set `deployerKeypair` in the config to use your own deployer:

```kotlin
val config = OZSmartAccountConfig(
    // ...required fields...
    deployerKeypair = myFundedKeypair
)
```

When using a custom deployer, an indexer is recommended for wallet discovery, since clients that do not know the deployer keypair cannot derive the address independently.

## Contract Limits

The OpenZeppelin smart account contract enforces these limits:

| Limit | Value |
|-------|-------|
| Maximum signers per context rule | 15 |
| Maximum policies per context rule | 5 |

These limits are defined in `OZConstants` and validated client-side before submitting transactions.

## Sub-Pages

| Guide | Description |
|-------|-------------|
| [Onboarding Guide](onboarding.md) | Smart account concepts, passkeys, on-chain contract interface, end-to-end lifecycle |
| [WebAuthn Setup: Android](webauthn-android.md) | Android Credential Manager integration, Digital Asset Links setup |
| [WebAuthn Setup: iOS](webauthn-ios.md) | iOS AuthenticationServices integration, apple-app-site-association setup |
| [WebAuthn Setup: macOS](webauthn-macos.md) | macOS AuthenticationServices integration, associated domains setup |
| [WebAuthn Setup: Web](webauthn-web.md) | Browser WebAuthn API, localhost development setup |
| [API Reference](api-reference.md) | Full API reference for all public classes and methods |
