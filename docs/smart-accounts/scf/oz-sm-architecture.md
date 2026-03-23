# Smart account support for the KMP Stellar SDK

## Technical architecture document

### 1. Introduction

The existing TypeScript Smart Account Kit provides smart account tooling for JavaScript environments. It uses the browser's `navigator.credentials` API for WebAuthn and browser-based storage (localStorage, IndexedDB). While this works well for web apps, it does not provide native platform integration for Android or Apple platforms. Developers building native Android apps (Kotlin/Java), native iOS apps (Swift), or native macOS apps have no SDK that uses the platform's own passkey APIs and secure storage.

This project adds smart account support to the Kotlin Multiplatform (KMP) Stellar SDK. It uses native platform APIs on each target: Android Credential Manager for passkeys and EncryptedSharedPreferences for storage, Apple AuthenticationServices for passkeys and Keychain for storage, and the browser WebAuthn API for web. All platform logic is compiled to native code (not JavaScript), with shared business logic across all targets. Furthermore, KMP developers targeting JVM or Native cannot use the TypeScript kit at all -- it is a JavaScript library with no Kotlin API.

The KMP Stellar SDK is maintained by Soneso and covers transaction building, the Horizon REST API, Soroban RPC, a high-level ContractClient for Soroban smart contracts, and support for multiple SEPs.

### 2. Background

A traditional Stellar account is controlled by Ed25519 secret keys. A smart account is a Soroban contract that acts as an account, with authorization logic on-chain inside the contract. The contract implements `__check_auth`, which the Soroban runtime calls to decide whether a transaction is authorized.

Smart accounts support three signer types. Passkey signers use WebAuthn (secp256r1) -- the user authenticates with a biometric (Face ID, fingerprint) and the private key never leaves secure hardware (Secure Enclave on Apple, TEE on Android, TPM on desktop). Delegated signers are Stellar addresses verified via Soroban's built-in `require_auth`. Ed25519 signers use a 32-byte public key. Because smart accounts are contracts, not native Stellar accounts, passkey and Ed25519 signature verification goes through a verifier contract deployed on-chain rather than through the network's native Ed25519 verification.

Each smart account contract also stores context rules (per-operation authorization requirements) and policies (spending limits, multi-signature thresholds, weighted voting). The smart account WASM, verifier contracts, and policy contracts must be deployed on-chain before the SDK can use them. OpenZeppelin (https://github.com/OpenZeppelin/stellar-contracts) provides the smart account trait, implementation library, and reference contracts (verifiers, policies). A client SDK with OpenZeppelin smart account support receives these addresses as configuration, deploys individual smart account instances per user from the pre-deployed WASM, assembles transactions, signs authorization entries, and submits them either directly via Soroban RPC or through an optional relayer. The relayer is a simple HTTP proxy that wraps the caller's transaction in a fee-bump, so end users never pay transaction fees.

The TypeScript Smart Account Kit (https://github.com/kalepail/smart-account-kit) is the existing JavaScript SDK for deploying and managing OpenZeppelin smart account contracts on Stellar. This project adds the same capability to the KMP Stellar SDK, targeting Android, iOS, macOS, and web with native platform integration.

### 3. Architecture

The implementation has two layers:

**Core layer** (contract-agnostic): Signer types, typed error hierarchy, authorization entry signing, secp256r1 signature normalization (DER to compact with low-S enforcement), public key extraction from WebAuthn COSE key structures, and contract address derivation. This layer has no OpenZeppelin-specific logic and works with any smart account contract implementation.

**OpenZeppelin layer** (contract-specific): High-level operations built on the core primitives, targeting the OpenZeppelin smart account contracts.

```
+----------------------------------------------------------------------+
|                           Application                                |
+----------------------------------------------------------------------+
       |
       v
+------------------------+
| OZSmartAccountConfig   |
+------------------------+
       |
       v
+----------------------------------------------------------------------+
|                        OZSmartAccountKit                             |
|  Created via OZSmartAccountKit.create(config)                        |
|                                                                      |
|  +---------------------+  +--------------------------+               |
|  | walletOperations    |  | transactionOperations    |               |
|  +---------------------+  +--------------------------+               |
|  +---------------------+  +--------------------------+               |
|  | signerManager       |  | contextRuleManager       |               |
|  +---------------------+  +--------------------------+               |
|  +---------------------+  +--------------------------+               |
|  | policyManager       |  | multiSignerManager       |               |
|  +---------------------+  +--------------------------+               |
|  +---------------------+  +--------------------------+               |
|  | credentialManager   |  | events                   |               |
|  +---------------------+  +--------------------------+               |
+----------------------------------------------------------------------+
       |                  |                    |
       v                  v                    v
+------------------+  +------------------+  +-----------------------+
| WebAuthnProvider |  | StorageAdapter   |  | ExternalWalletAdapter |
| (per platform)   |  | (per platform)   |  | (interface)           |
+------------------+  +------------------+  +-----------------------+
       |                  |
       v                  v
+------------------+  +------------------+
| OS Biometric     |  | Secure Storage   |
| Prompt           |  | (Keychain, etc.) |
+------------------+  +------------------+

Infrastructure clients:
+------------------+  +------------------+  +-----------------------+
| SorobanServer    |  | OZRelayerClient  |  | OZIndexerClient       |
| (Soroban RPC)    |  | (fee sponsoring) |  | (credential lookup)   |
+------------------+  +------------------+  +-----------------------+
```

`OZSmartAccountConfig` requires four parameters: the Soroban RPC URL, the network passphrase, the smart account WASM hash (hex), and the WebAuthn verifier contract address. Optional parameters include a relayer URL (for fee sponsoring), an indexer URL (for credential-to-contract lookup), a custom deployer keypair, and the platform adapters (WebAuthnProvider, StorageAdapter, ExternalWalletAdapter).

`OZSmartAccountKit` stores configuration and connection state, and routes operations to seven sub-managers. Each sub-manager references the kit's Soroban server, relayer, and storage.

`WebAuthnProvider` is a platform-specific interface. It triggers the OS biometric prompt and returns raw WebAuthn attestation/assertion data. The KMP SDK includes implementations for each target platform.

`StorageAdapter` persists credentials and sessions. The KMP SDK ships an in-memory adapter for testing and production adapters per platform.

`ExternalWalletAdapter` is an interface for connecting external Stellar wallets as delegated signers in multi-signer workflows if needed. The interface is platform-agnostic; the existing reference implementation in the TypeScript kit wraps the Stellar Wallets Kit for browser extension wallets. The KMP SDK defines the interface but leaves concrete implementations to the application developer.

The KMP SDK provides three infrastructure clients: `SorobanServer` for Soroban RPC calls, `OZRelayerClient` for fee-sponsored transaction submission, and `OZIndexerClient` for credential-to-contract address lookup. The relayer and indexer are optional.

### 4. Platform integration

The KMP SDK provides WebAuthn and storage implementations for each target platform:

| Platform | WebAuthn API | Secure Storage | Min Version |
|----------|-------------|----------------|-------------|
| Android | AndroidX Credential Manager | EncryptedSharedPreferences (AES-256-GCM, Android Keystore) | API 28 (Android 9) |
| iOS | AuthenticationServices (ASAuthorizationPlatformPublicKeyCredentialProvider) | Keychain (kSecAttrAccessibleAfterFirstUnlock) | iOS 16 |
| macOS | AuthenticationServices (shared with iOS via Kotlin Native) | Keychain | macOS 13 |
| Web | navigator.credentials API | IndexedDB | Chrome 67+, Firefox 60+, Safari 14+ |

The KMP SDK also provides non-encrypted storage adapters (UserDefaults on Apple, localStorage on web) for development and testing.

iOS and macOS share the WebAuthn provider through Kotlin's `nativeMain` source set, with platform-specific code in `iosMain` and `macosMain`. Android implementations go in `jvmMain`, web implementations in `jsMain`.

Both WebAuthnProvider and StorageAdapter are defined as interfaces, so developers can supply custom implementations if needed.

### 5. Feature scope

**Wallet lifecycle**: Create wallets with WebAuthn registration, deploy the smart account contract, fund via Friendbot (testnet). Connect to existing wallets via session restore or fresh WebAuthn authentication. When connecting, the SDK resolves the credential to a contract address. With the default deployer this is derived locally; with a custom deployer, an Indexer is needed for the lookup. Disconnect clears in-memory state but keeps stored credentials.

**Token transfers and contract calls**: Transfer tokens through the smart account contract. Submit arbitrary Soroban contract invocations. Route transactions through a relayer for fee sponsoring or directly via Soroban RPC.

**Signer management**: The on-chain contract has two signer types: `Delegated` (a Stellar account address, verified via `require_auth`) and `External` (a verifier contract address plus key data). The KMP SDK exposes these as passkey signers (External with WebAuthn verifier, secp256r1), Ed25519 signers (External with Ed25519 verifier), and delegated signers (Delegated with a G-address).

**Context rules**: Create, update, and remove context rules per operation type. Context types: Default (fallback), CallContract (specific contract address), CreateContract (specific WASM hash). The contract evaluates specific rules first, then falls back to Default.

**Policy enforcement**: Attach and remove policy contracts on context rules. Built-in policy types: simple threshold (M-of-N), weighted threshold (per-signer weights), spending limit (rolling window). Custom policy contracts supported via generic install parameters. The on-chain contract enforces limits: 15 context rules, 15 signers per rule, 5 policies per rule. The KMP SDK validates these before submission.

**Multi-signer workflows**: Discover available signers across passkey and external wallet sources. Coordinate multi-party token transfers that require multiple signatures.

**Credential management**: Store credential metadata locally. Sync with on-chain state to track deployment status.

**Event system**: Type-safe listeners for lifecycle events (wallet connected/disconnected, credential created/deleted, session expired, transaction signed/submitted).

### 6. Deployer and address derivation

Contract addresses are deterministic. The KMP SDK derives a C-address from `SHA256(credentialId)` as salt, combined with the deployer public key and network passphrase, via `HashIDPreimage::ContractID`. Given the same inputs, the KMP SDK produces addresses identical to the TypeScript Smart Account Kit.

The KMP SDK accepts a configurable deployer keypair. Production applications will typically use a custom deployer for attribution and traceability, since the deployer signs the deployment transaction and is visible on-chain. For testing and simple use cases, the SDK provides a default deployer derived from `SHA256("openzeppelin-smart-account-kit")` as the Ed25519 seed, shared with the TypeScript Smart Account Kit.

Signer encoding follows the standard Soroban enum serialization for the on-chain `Signer` type (`Delegated(Address)` or `External(Address, Bytes)`). For passkey signers, the key data is the 65-byte uncompressed secp256r1 public key concatenated with the credential ID.

### 7. Security design

**Passkey isolation**: Private keys stay in platform secure hardware (Secure Enclave, TEE, TPM). The KMP SDK never accesses or stores private key material.

**Authorization flow**: When a transaction needs smart account authorization, the Soroban runtime calls the contract's `__check_auth`. The contract delegates signature verification to the appropriate verifier contract (WebAuthn verifier for passkeys, Ed25519 verifier for Ed25519 keys) or uses `require_auth` for delegated signers. All policies on the matching context rule are then evaluated.

**Replay protection**: Authorization entries include an expiration ledger (default 720 ledgers / 1 hour). Expired entries are rejected by the Soroban runtime during execution. The expiration window is configurable.

**Signature normalization**: WebAuthn produces DER-encoded secp256r1 signatures. The KMP SDK converts these to compact 64-byte format with low-S enforcement, as required by the on-chain verifier.

**Auth entry signing**: The KMP SDK computes `SHA256(XDR(HashIDPreimage::SorobanAuthorization))` as the payload hash. Signature values use double XDR encoding (encode SCVal to bytes, wrap in SCVal::Bytes). SCVal map keys are sorted by XDR-encoded byte representation for deterministic ordering.

**Storage security**: Android uses AES-256-GCM encryption backed by the Android Keystore. Apple platforms use the system Keychain. Web uses IndexedDB. Stored data contains only public keys and session metadata, never secret keys.

**Deployer key transparency**: The default deployer key is publicly derivable. After deployment the deployer has no privileges over the contract; it only participates in address derivation and signing the deployment transaction. For direct RPC submission the deployer account needs XLM; when using a relayer, the relayer covers this.

### 8. Demo application

The KMP SDK includes a multiplatform demo app for Android, iOS, and web, built with Compose Multiplatform. macOS uses a native SwiftUI UI layer over the shared SDK.

Screens:
- Main screen with SDK configuration, wallet status, balance display, and navigation
- Wallet creation: passkey registration, contract deployment, testnet funding
- Wallet connection: session restore, fresh authentication, pending deployment recovery
- Transfer: token transfers with biometric authentication
- Context rules: list, inspect, create, edit, and remove context rules
- Context rule builder: configure context type, signers, and policies for a rule
- Known signers: view and manage locally stored credentials
- Policy contracts: reference of available policy contracts (threshold, spending limit, weighted threshold) with addresses and descriptions

The demo connects to Stellar testnet with pre-deployed OpenZeppelin contracts and a fee-sponsoring relayer.

### 9. Documentation

The KMP SDK ships with:
- A usage guide covering configuration, quick start, and code examples
- Per-platform WebAuthn setup guides (domain association, entitlements, dependencies)
- An onboarding guide on smart account concepts and the on-chain authorization model
- A full API reference for all public classes, methods, and configuration options

### 10. Testing strategy

**Unit tests**: Core logic (signer encoding, address derivation, signature normalization, error handling, SCVal construction) is covered by platform-independent tests in `commonTest`.

**Cross-platform manual testing**: Wallet creation, connection, transfer, and signer management are verified on physical Android devices, iOS devices, macOS, and web browsers with the demo app. Passkey interoperability is tested between platforms (e.g., create on iOS, connect on web).

**Cross-implementation verification**: Wallets created via the TypeScript kit are accessed from the KMP SDK and vice versa to verify address derivation and signer format compatibility.
