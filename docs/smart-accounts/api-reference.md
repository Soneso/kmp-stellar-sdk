# Smart Accounts API Reference

OpenZeppelin Smart Account Kit for Stellar/Soroban. This reference documents all public APIs for creating, managing, and operating smart accounts with WebAuthn/passkey authentication.

**Location**: `com.soneso.stellar.sdk.smartaccount`

**Platform Support**: JVM, iOS/macOS, JavaScript, Android

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [OZSmartAccountKit](#ozsmartaccountkit-main-entry-point)
3. [Wallet Operations](#wallet-operations)
4. [Transaction Operations](#transaction-operations)
5. [Credential Management](#credential-management)
6. [Signers and Policies](#signers-and-policies)
7. [Context Rules](#context-rules)
8. [Builders](#builders)
9. [Multi-Signer Operations](#multi-signer-operations)
10. [External Signers](#external-signers)
11. [Indexer Client](#indexer-client)
12. [Relayer Client](#relayer-client)
13. [Events](#events)
14. [Exceptions](#exceptions)
15. [Types](#types)

---

## Quick Start

```kotlin
// Initialize the kit
val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "YOUR_ACCOUNT_WASM_HASH",
    webauthnVerifierAddress = "CWEBAUTHN_VERIFIER_ADDRESS",
    webauthnProvider = webauthnProvider,  // required for passkey auth (AndroidWebAuthnProvider / AppleWebAuthnProvider / JsWebAuthnProvider)
    storage = storageAdapter              // defaults to InMemoryStorageAdapter (AndroidStorageAdapter / UserDefaultsStorageAdapter / IndexedDBStorageAdapter)
)
val kit = OZSmartAccountKit.create(config)

// On app start: silently restore from stored session
val session = kit.walletOperations.connectWallet()
if (session != null) {
    println("Restored wallet: ${session.contractId}")
}

// User creates a new wallet (registers a passkey, deploys the contract)
val wallet = kit.walletOperations.createWallet(
    userName = "Alice",
    autoSubmit = true,
    autoFund = true,
    nativeTokenContract = "CDLZFC3..."
)
println("Created wallet: ${wallet.contractId}")

// User connects to an existing wallet (prompts for passkey selection)
val connected = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(prompt = true)
)

// Transfer tokens
val result = kit.transactionOperations.transfer(
    tokenContract = "CDLZFC3...",
    recipient = "GA7QYNF7...",
    amount = "10"
)

// Disconnect
kit.disconnect()
```

---

## OZSmartAccountKit (Main Entry Point)

### Factory Method

```kotlin
fun create(
    config: OZSmartAccountConfig
): OZSmartAccountKit
```

Creates a new OZSmartAccountKit instance. Storage and external wallet adapters are configured via `OZSmartAccountConfig`.

**Parameters**:
- `config`: Configuration defining network endpoints, contract addresses, storage, and optional external wallet adapter

**Returns**: Initialized OZSmartAccountKit instance

**Throws**: `ConfigurationException.InvalidConfig` if configuration is invalid

---

### Properties

#### config
```kotlin
val config: OZSmartAccountConfig
```

The configuration object containing network settings, contract addresses, and operational parameters.

#### events
```kotlin
val events: SmartAccountEventEmitter
```

Event emitter for wallet lifecycle events. Subscribe to receive notifications about wallet operations.

#### isConnected
```kotlin
val isConnected: Boolean
```

True if a wallet is currently connected (both credential ID and contract ID are set).

#### credentialId
```kotlin
val credentialId: String?
```

Base64URL-encoded credential ID of the currently connected wallet, or null if not connected.

#### contractId
```kotlin
val contractId: String?
```

Contract address (C-address, 56 characters starting with 'C') of the currently connected wallet, or null if not connected.

---

### Manager Properties

#### walletOperations
```kotlin
val walletOperations: OZWalletOperations
```

Access wallet creation, connection, and disconnection operations.

#### transactionOperations
```kotlin
val transactionOperations: OZTransactionOperations
```

Access transaction building, signing, and submission operations.

#### signerManager
```kotlin
val signerManager: OZSignerManager
```

Access signer management operations (add/remove signers to context rules).

#### contextRuleManager
```kotlin
val contextRuleManager: OZContextRuleManager
```

Access context rule operations (add/remove/query rules).

#### policyManager
```kotlin
val policyManager: OZPolicyManager
```

Access policy management operations (add/remove policies).

#### credentialManager
```kotlin
val credentialManager: OZCredentialManager
```

Access credential storage and lifecycle operations.

#### multiSignerManager
```kotlin
val multiSignerManager: OZMultiSignerManager
```

Access multi-signature operations and available signer queries.

#### externalSigners
```kotlin
val externalSigners: OZExternalSignerManager
```

Read-only property exposing the kit-owned [OZExternalSignerManager] — the single front door for all external (non-passkey) signers. Always non-null: the kit constructs the manager from its configuration, injecting the wallet adapter (`OZSmartAccountConfig.externalWallet`) and the Ed25519 adapter (`OZSmartAccountConfig.externalEd25519Adapter`). The multi-signer pipeline routes all wallet (G-address) and Ed25519 signing through this manager.

Use this property to register in-memory keypairs at runtime and to query signing capability before a multi-signer operation that includes `SelectedSigner.Wallet` or `SelectedSigner.Ed25519` selectors.

```kotlin
val config = OZSmartAccountConfig.builder(rpcUrl, networkPassphrase, wasmHash, verifier)
    .externalEd25519Adapter(myHardwareAdapter)
    .build()
val kit = OZSmartAccountKit.create(config)

// Register an in-memory Ed25519 signer at runtime
kit.externalSigners.addEd25519FromRawKey(secretKeyBytes, verifierAddress)

// Register an in-memory wallet (G-address) signer at runtime
kit.externalSigners.addFromSecret("S...")
```

---

### Client Properties

#### indexerClient
```kotlin
val indexerClient: OZIndexerClient?
```

Indexer client for credential-to-contract discovery. Null when no indexer URL is configured. Use for looking up contracts by credential ID or signer address, and for retrieving contract details (rules, signers, policies).

```kotlin
// Discover contracts associated with a credential
val contracts = kit.indexerClient?.lookupByCredentialId(credentialId)

// Get full contract details
val details = kit.indexerClient?.getContract(contractId)
```

#### relayerClient
```kotlin
val relayerClient: OZRelayerClient?
```

Relayer client for fee-sponsored transaction submission. Null when no relayer URL is configured. The SDK uses this internally for transaction submission when a relayer is configured. Direct access is available for advanced use cases.

---

### Lifecycle Methods

#### disconnect
```kotlin
suspend fun disconnect()
```

Disconnects the currently connected wallet, clearing the in-memory connection state and removing the stored session. Stored credentials remain and can be reconnected later.

**Throws**: None (safe to call when not connected)

---

#### close
```kotlin
fun close()
```

Closes the kit and releases all held HTTP client resources. Closes the Soroban RPC server connection and the indexer HTTP client if present. The relayer client manages its own per-request connections and requires no explicit cleanup.

The kit must not be used after calling this method. To log out without releasing resources, call `disconnect()` instead.

**Throws**: None

---

## OZSmartAccountConfig

Configuration data class for smart account operations.

```kotlin
data class OZSmartAccountConfig(
    // Required
    val rpcUrl: String,
    val networkPassphrase: String,
    val accountWasmHash: String,
    val webauthnVerifierAddress: String,

    // Optional
    val deployerKeypair: KeyPair? = null,
    val sessionExpiryMs: Long = 604800000L,  // 7 days
    val signatureExpirationLedgers: Int = 720,  // ~1 hour
    val timeoutInSeconds: Int = 30,
    val relayerUrl: String? = null,
    val indexerUrl: String? = null,
    val webauthnProvider: WebAuthnProvider? = null,
    val storage: StorageAdapter = InMemoryStorageAdapter(),
    val externalWallet: ExternalWalletAdapter? = null,
    val externalEd25519Adapter: OZExternalEd25519SignerAdapter? = null,
    val maxContextRuleScanId: UInt = 50u
)
```

**Required Fields**:
- `rpcUrl`: Soroban RPC endpoint (e.g., "https://soroban-testnet.stellar.org")
- `networkPassphrase`: Stellar network passphrase
- `accountWasmHash`: SHA-256 hash of smart account contract WASM (hex string)
- `webauthnVerifierAddress`: Contract address (C-address) for WebAuthn signature verification

**Optional Fields**:
- `deployerKeypair`: Keypair for contract deployment (uses deterministic default if null)
- `sessionExpiryMs`: Session validity duration in milliseconds
- `signatureExpirationLedgers`: Auth entry signature expiration in ledgers
- `timeoutInSeconds`: Operation timeout in seconds
- `relayerUrl`: Optional relayer endpoint for fee sponsoring
- `indexerUrl`: Optional indexer endpoint for credential-to-contract mapping
- `webauthnProvider`: Platform-specific WebAuthn provider
- `storage`: Storage adapter for credential persistence (defaults to `InMemoryStorageAdapter()`)
- `externalWallet`: Optional wallet adapter ([ExternalWalletAdapter]) backing the adapter custody model for `SelectedSigner.Wallet` (G-address) signers. The kit injects it into [OZSmartAccountKit.externalSigners].
- `externalEd25519Adapter`: Optional Ed25519 adapter ([OZExternalEd25519SignerAdapter]) backing the adapter custody model for `SelectedSigner.Ed25519` signers (hardware wallet, HSM, remote signing service). The kit injects it into [OZSmartAccountKit.externalSigners]. See [External Signers](#external-signers).
- `maxContextRuleScanId`: Upper bound on rule IDs to scan when iterating context rules (defaults to 50). Increase if the account has had many add/remove cycles.

### Platform-Specific Providers

#### WebAuthnProvider implementations

The SDK provides ready-to-use WebAuthn providers for each platform:

| Platform | Class | Constructor |
|----------|-------|-------------|
| Android | `AndroidWebAuthnProvider` | `AndroidWebAuthnProvider(activity)` |
| iOS/macOS | `AppleWebAuthnProvider` | `AppleWebAuthnProvider(window)` |
| JS/Web | `JsWebAuthnProvider` | `JsWebAuthnProvider(rpId)` |

#### StorageAdapter implementations

Storage adapters persist credentials and sessions across app restarts:

| Platform | Class | Description |
|----------|-------|-------------|
| All | `InMemoryStorageAdapter` | Non-persistent, for testing only (default) |
| Android | `AndroidStorageAdapter` | Encrypted storage (EncryptedSharedPreferences) backed by Android Keystore |
| iOS/macOS | `UserDefaultsStorageAdapter` | Persists to UserDefaults |
| JS/Web | `IndexedDBStorageAdapter` | Browser IndexedDB (recommended for web) |
| JS/Web | `LocalStorageAdapter` | Browser localStorage |

#### ExternalWalletAdapter

Interface for delegated (G-address) signers in multi-signer operations. Implement this to integrate external Stellar wallets (e.g., Freighter, Lobstr) that can sign auth entries on behalf of delegated signers. Key methods:

- `canSignFor(address: String): Boolean` — check if the adapter can sign for an address
- `signAuthEntry(preimageXdr: String, options: SignAuthEntryOptions?): SignAuthEntryResult` — sign an auth entry preimage
- `connect(): ConnectedWallet?` — connect to the external wallet
- `disconnect()` — disconnect all wallets
- `disconnectByAddress(address: String)` — disconnect a specific wallet by address (default no-op)
- `getConnectedWallets(): List<ConnectedWallet>` — list connected wallets

The kit-owned `OZExternalSignerManager` ([OZSmartAccountKit.externalSigners]) composes an `ExternalWalletAdapter` supplied via `OZSmartAccountConfig.externalWallet` and routes `SelectedSigner.Wallet` signing through it. See [External Signers](#external-signers) for details.

**Factory Methods**:

```kotlin
companion object {
    suspend fun createDefaultDeployer(): KeyPair
    fun builder(
        rpcUrl: String,
        networkPassphrase: String,
        accountWasmHash: String,
        webauthnVerifierAddress: String
    ): Builder
}
```

**Builder**:

```kotlin
val config = OZSmartAccountConfig.builder(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "abc123...",
    webauthnVerifierAddress = "CBCD1234..."
)
    .sessionExpiryMs(86400000L)  // 1 day
    .relayerUrl("https://relayer.example.com")
    .storage(myStorageAdapter)
    .externalWallet(myWalletAdapter)
    .webauthnProvider(platformWebAuthnProvider())
    .build()
```

**Instance Methods**:

#### effectiveDeployer

```kotlin
suspend fun effectiveDeployer(): KeyPair
```

Returns the deployer keypair that will be used for contract deployment and transaction submission. If `deployerKeypair` is explicitly set in the config, that value is returned. Otherwise, a deterministic keypair is derived from `SHA-256("openzeppelin-smart-account-kit")`. The derivation is deterministic and reproducible, always producing the same deployer address. The deployer only pays fees; it does not control user wallets.

**Returns**: The configured deployer or the default deterministic deployer

**Throws**: `ConfigurationException` if default deployer creation fails

#### effectiveIndexerUrl

```kotlin
fun effectiveIndexerUrl(): String?
```

Returns the indexer URL that will be used after applying fallback logic. If `indexerUrl` is explicitly set in the config, that value is returned. Otherwise, the SDK falls back to the built-in default URL for the network (testnet and mainnet have built-in defaults).

**Returns**: The resolved indexer URL, or null if no URL is configured and no default exists for the network

---

## Wallet Operations

### OZWalletOperations

Manages wallet creation and connection.

```kotlin
val kit = OZSmartAccountKit.create(config)
val walletOps = kit.walletOperations
```

---

#### createWallet

```kotlin
suspend fun createWallet(
    userName: String = "Smart Account User",
    autoSubmit: Boolean = false,
    autoFund: Boolean = false,
    nativeTokenContract: String? = null,
    forceMethod: SubmissionMethod? = null
): CreateWalletResult
```

Creates a new smart account wallet with WebAuthn passkey authentication.

**Parameters**:
- `userName`: Display name for the user
- `autoSubmit`: Whether to automatically submit the deploy transaction. When a relayer is configured, the transaction is submitted via the relayer which sponsors fees on behalf of the deployer. Without a relayer, the deployer account must be funded to pay fees directly.
- `autoFund`: Whether to automatically fund the wallet after deployment (testnet only)
- `nativeTokenContract`: Required if `autoFund` is true; the native token contract address
- `forceMethod`: Optional override to force relayer or RPC submission (default: auto-detect based on config)

**Returns**: `CreateWalletResult` containing credential ID, contract address, signed transaction XDR, optional transaction hash, and nickname

**Throws**:
- `WebAuthnException.NotSupported`: No WebAuthn provider configured
- `ValidationException`: Invalid inputs or missing required parameters
- `TransactionException`: Deployment or funding failed

**Example**:

```kotlin
// Create without deploying
val wallet1 = walletOps.createWallet(userName = "Alice", autoSubmit = false)
println("Created: ${wallet1.contractId}")

// Create, deploy, and fund
val wallet2 = walletOps.createWallet(
    userName = "Bob",
    autoSubmit = true,
    autoFund = true,
    nativeTokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
)
println("Funded: ${wallet2.contractId}")
```

---

#### connectWallet

```kotlin
suspend fun connectWallet(
    options: ConnectWalletOptions = ConnectWalletOptions()
): ConnectWalletResult?

data class ConnectWalletOptions(
    val credentialId: String? = null,
    val contractId: String? = null,
    val fresh: Boolean = false,
    val prompt: Boolean = false
)

sealed class ConnectWalletResult {
    abstract val credentialId: String

    data class Connected(
        override val credentialId: String,
        val contractId: String,
        val restoredFromSession: Boolean
    ) : ConnectWalletResult()

    data class Ambiguous(
        override val credentialId: String,
        val candidates: List<String>
    ) : ConnectWalletResult()
}
```

Connects to an existing smart account wallet. Returns `null` when no session exists and no WebAuthn prompt is requested, enabling a two-phase connect pattern.

The non-null result is one of two arms:
- `Connected`: a single contract was resolved; kit state is set and a session is saved.
- `Ambiguous`: the indexer reported multiple contracts where the passkey is registered as a signer. The kit state is NOT set; the caller must let the user pick a contract from `candidates` and reconnect with the chosen `contractId` (and the `credentialId` from the result, to skip a second WebAuthn ceremony).

`Ambiguous` is by-construction unreachable when `contractId` is supplied (the cascade is bypassed).

**Options Decision Matrix**:

| Options | Behavior |
|---------|----------|
| (default) | Session restore; return `null` if no session |
| `credentialId` and/or `contractId` | Direct connect, skip session check |
| `fresh = true` | Skip session, always trigger WebAuthn |
| `prompt = true` | Session restore; trigger WebAuthn if no session |
| `fresh = true, prompt = true` | `fresh` takes priority, always trigger WebAuthn |

**Contract lookup order** (when `credentialId` is provided or WebAuthn was triggered, and `contractId` is not supplied): storage → derivation → indexer.

1. Storage hit: `FAILED` deployment status throws with a recovery hint pointing to `deployPendingCredential()`. `PENDING` entries are trusted.
2. Derivation: derive the deterministic address under the configured deployer and verify on-chain. If no contract exists, fall through to the indexer.
3. Indexer: 0 results → `WalletException.NotFound`. 1 result → verify on-chain and return `Connected`. N > 1 → return `Ambiguous(candidates)`.

**Throws**:
- `WebAuthnException`: Authentication failed (only when WebAuthn is triggered)
- `WalletException.NotFound`: Contract not found at any cascade stage (no on-chain contract at the derived address and either no indexer or zero/missing indexer results), or `FAILED` storage entry detected
- `ValidationException`: Invalid options, or malformed deployer config (from `deriveContractAddress`)
- `TransactionException`: Internal XDR encoding failure (from `deriveContractAddress`)

**Example**:

```kotlin
// Phase 1: Silent restore at app launch (returns null if no session)
when (val result = walletOps.connectWallet()) {
    null -> {
        // No saved session — show a "Connect" button in the UI
    }
    is ConnectWalletResult.Connected -> {
        println("Silently reconnected to ${result.contractId}")
    }
    is ConnectWalletResult.Ambiguous -> {
        // Unreachable for the silent restore path
    }
}

// Phase 2: User taps "Connect" — triggers WebAuthn if no session
val result = walletOps.connectWallet(ConnectWalletOptions(prompt = true))
when (result) {
    null -> { /* unreachable when prompt = true */ }
    is ConnectWalletResult.Connected -> println("Connected: ${result.contractId}")
    is ConnectWalletResult.Ambiguous -> {
        // Let the user pick, then re-connect with credentialId + chosen contractId
        // (no second WebAuthn ceremony required).
        val chosen = userPicker(result.candidates)
        walletOps.connectWallet(
            ConnectWalletOptions(
                credentialId = result.credentialId,
                contractId = chosen
            )
        )
    }
}

// Force fresh authentication
walletOps.connectWallet(ConnectWalletOptions(fresh = true))

// Direct connection (Ambiguous is by-construction unreachable)
walletOps.connectWallet(
    ConnectWalletOptions(
        credentialId = "abc123...",
        contractId = "CBCD..."
    )
)
```

---

#### authenticatePasskey

```kotlin
suspend fun authenticatePasskey(
    challenge: ByteArray? = null,
    credentialIds: List<String>? = null
): AuthenticatePasskeyResult

data class AuthenticatePasskeyResult(
    val credentialId: String,
    val signature: WebAuthnSignature,
    val publicKey: ByteArray
)
```

Authenticates with a passkey without connecting to a wallet.

Use this for credential discovery via indexer or pre-authentication before contract selection.

**Parameters**:
- `challenge`: Optional challenge bytes (generates random 32 bytes if null)
- `credentialIds`: Optional list of Base64URL-encoded credential IDs. When provided, the OS/browser only offers these specific passkeys during authentication. When null, all passkeys for this Relying Party are offered.

**Returns**: `AuthenticatePasskeyResult` with credential ID, signature, and public key

**Throws**:
- `WebAuthnException`: Authentication failed or no provider configured
- `ValidationException`: Signature normalization failed

---

#### deployPendingCredential

```kotlin
suspend fun deployPendingCredential(
    credentialId: String,
    autoSubmit: Boolean = true,
    autoFund: Boolean = false,
    nativeTokenContract: String? = null,
    forceMethod: SubmissionMethod? = null
): DeployPendingResult
```

Deploys a wallet from a previously created pending credential. Use this to retry a failed deployment or to submit a wallet that was created with `createWallet(autoSubmit = false)`. The credential must exist in local storage with a valid public key and contract ID.

After successful deployment, the kit is set to the connected state and ready for use.

**Parameters**:
- `credentialId`: Base64URL-encoded credential ID of the pending credential
- `autoSubmit`: Whether to submit the deploy transaction (default: true)
- `autoFund`: Whether to fund the wallet after deployment via Friendbot (default: false, testnet only)
- `nativeTokenContract`: Required if `autoFund` is true; the native token contract address
- `forceMethod`: Optional override to force relayer or RPC submission (default: auto-detect based on config)

**Returns**: `DeployPendingResult` containing contract address, signed transaction XDR, and optional transaction hash

**Throws**:
- `CredentialException`: Credential not found in storage or missing required fields
- `ValidationException`: `autoFund` is true but `nativeTokenContract` is null
- `TransactionException`: Building, simulating, or submitting the deploy transaction failed

**Example**:

```kotlin
// Retry a failed deployment
val result = walletOps.deployPendingCredential(
    credentialId = "abc123...",
    autoSubmit = true
)
println("Deployed: ${result.contractId}, tx: ${result.transactionHash}")

// Build signed XDR without submitting (for external submission)
val deferred = walletOps.deployPendingCredential(
    credentialId = "abc123...",
    autoSubmit = false
)
println("Signed XDR: ${deferred.signedTransactionXdr}")
```

---

### Result Types

#### CreateWalletResult
```kotlin
data class CreateWalletResult(
    val credentialId: String,
    val contractId: String,
    val publicKey: ByteArray,
    val signedTransactionXdr: String,
    val transactionHash: String? = null,
    val nickname: String? = null
)
```

**Fields**:
- `credentialId`: Base64URL-encoded credential ID
- `contractId`: Smart account contract address (C-address)
- `publicKey`: Uncompressed secp256r1 public key (65 bytes)
- `signedTransactionXdr`: Base64-encoded signed deploy transaction envelope (always present, regardless of `autoSubmit`)
- `transactionHash`: Transaction hash if auto-submitted, null otherwise
- `nickname`: Display name from the `userName` parameter, stored with the credential

#### ConnectWalletResult
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
        val candidates: List<String>
    ) : ConnectWalletResult()
}
```

**Connected fields**:
- `credentialId`: Base64URL-encoded credential ID
- `contractId`: Smart account contract address
- `restoredFromSession`: True if reconnected from saved session, false if new authentication

**Ambiguous fields**:
- `credentialId`: Base64URL-encoded credential ID; reuse this for the disambiguation reconnect to skip a second WebAuthn ceremony
- `candidates`: Contract addresses (C-addresses) where the passkey is registered as a signer. The caller should let the user pick one and reconnect with `OZWalletOperations.ConnectWalletOptions(credentialId, contractId = chosen)`.

#### DeployPendingResult
```kotlin
data class DeployPendingResult(
    val contractId: String,
    val signedTransactionXdr: String,
    val transactionHash: String? = null
)
```

**Fields**:
- `contractId`: Smart account contract address (C-address)
- `signedTransactionXdr`: Base64-encoded signed deploy transaction envelope
- `transactionHash`: Transaction hash if auto-submitted, null when `autoSubmit` is false

#### AuthenticatePasskeyResult
```kotlin
data class AuthenticatePasskeyResult(
    val credentialId: String,
    val signature: WebAuthnSignature,
    val publicKey: ByteArray
)
```

**Fields**:
- `credentialId`: Base64URL-encoded credential ID of the authenticated passkey
- `signature`: Normalized WebAuthn signature (compact format, low-S)
- `publicKey`: Uncompressed secp256r1 public key (65 bytes)

#### AddPasskeySignerResult
```kotlin
data class AddPasskeySignerResult(
    val credentialId: String,
    val publicKey: ByteArray,
    val transactionResult: TransactionResult
)
```

**Fields**:
- `credentialId`: Base64URL-encoded credential ID of the newly registered passkey
- `publicKey`: 65-byte uncompressed secp256r1 public key (starts with 0x04)
- `transactionResult`: Result of the on-chain signer addition transaction

---

## Transaction Operations

### OZTransactionOperations

Handles transaction building, signing, and submission.

```kotlin
val txOps = kit.transactionOperations
```

---

#### transfer

```kotlin
suspend fun transfer(
    tokenContract: String,
    recipient: String,
    amount: String,
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Transfers tokens from the smart account to a recipient. The amount is a decimal string (e.g., "100" or "10.5") converted to stroops internally using BigInteger arithmetic. Works with any SEP-41 compatible token (XLM SAC, custom Soroban tokens).

**Parameters**:
- `tokenContract`: Token contract address (C-address). Use the SAC address for XLM or the token's contract address for custom tokens.
- `recipient`: Recipient address (G-address for accounts, C-address for contracts)
- `amount`: Decimal amount string (e.g., "10", "100.5"). Converted to stroops automatically.
- `forceMethod`: Optional override to force RELAYER or RPC submission

**Returns**: `TransactionResult` with success status, hash, ledger, and optional error

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid addresses or amount
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed
- `CredentialException`: Credential lookup failed during signing

**Example**:

```kotlin
val result = kit.transactionOperations.transfer(
    tokenContract = "CBCD1234...",
    recipient = "GA7QYNF7SOWQ...",
    amount = "100.5"
)

if (result.success) {
    println("Hash: ${result.hash}")
    println("Ledger: ${result.ledger}")
} else {
    println("Error: ${result.error}")
}
```

---

#### contractCall

```kotlin
suspend fun contractCall(
    target: String,
    targetFn: String,
    targetArgs: List<SCValXdr> = emptyList(),
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

Calls an arbitrary function on an external contract directly from the smart account. The host function invokes `target.targetFn(targetArgs)` without going through the smart account's `execute()` entry point. Context rules of type `CallContract(target)` are matched for authorization.

Use this for external contract interactions (e.g., token approve, DeFi protocol calls) where the smart account is the authorized party. For multi-signer authorization, see [multiSignerContractCall](#multisignercontractcall).

**Parameters**:
- `target`: Contract address to call (C-address)
- `targetFn`: Function name to invoke on the target contract
- `targetArgs`: Arguments for the target function as XDR values. Use `Scv` helpers for encoding (e.g., `Scv.toUint32()`, `Scv.toAddress(Address(...).toSCAddress())`)
- `forceMethod`: Optional submission method override
- `resolveContextRuleIds`: Optional callback that returns context rule IDs for each authorization entry. See [ResolveContextRuleIds](#resolvecontextruleids).

**Returns**: `TransactionResult` with submission outcome

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid addresses or arguments
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed
- `CredentialException`: Credential lookup failed during signing

**Example**:

```kotlin
// Approve a token allowance directly on the token contract
val smartAccountAddress = kit.contractId!!
val spenderAddress = "GSPENDER..."
val tokenContractId = "CTOKEN..."
val expirationLedger = 2_000_000u  // absolute ledger number

val result = kit.transactionOperations.contractCall(
    target = tokenContractId,
    targetFn = "approve",
    targetArgs = listOf(
        Scv.toAddress(Address(smartAccountAddress).toSCAddress()),
        Scv.toAddress(Address(spenderAddress).toSCAddress()),
        Util.stroopsToI128ScVal(Util.amountToStroops("100")),
        Scv.toUint32(expirationLedger)
    )
)

if (result.success) {
    println("Approved: ${result.hash}")
}
```

---

#### executeAndSubmit

```kotlin
suspend fun executeAndSubmit(
    target: String,
    targetFn: String,
    targetArgs: List<SCValXdr> = emptyList(),
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

Executes a contract call through the smart account's `execute()` entry point. The smart account invokes the target contract on behalf of itself, making it the direct caller. This is required for contracts that check their invoker (e.g., policy contracts that verify the smart account is the caller).

The auth context for `execute()` is `CallContract(smartAccountAddress)`, which means only Default rules (or rules targeting the smart account address) match. For external contract calls with contract-specific rules (e.g., `CallContract(tokenContract)`), use [contractCall](#contractcall) instead.

For the multi-signer equivalent, see [multiSignerExecuteAndSubmit](#multisignerexecuteandsubmit).

**Parameters**:
- `target`: Contract address to call (C-address)
- `targetFn`: Function name to invoke on the target contract
- `targetArgs`: Arguments for the target function as XDR values (use `Scv` helpers)
- `forceMethod`: Optional submission method override
- `resolveContextRuleIds`: Optional callback that returns context rule IDs for each authorization entry. See [ResolveContextRuleIds](#resolvecontextruleids).

**Returns**: `TransactionResult` with submission outcome

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid addresses or arguments
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed
- `CredentialException`: Credential lookup failed during signing

**Example**:

```kotlin
// Update a threshold policy via the smart account's execute() entry point
val ruleId = 1u
val thresholdPolicyAddress = "CPOLICY..."
val newThreshold = 3u

val contextRuleScVal = kit.contextRuleManager.getContextRule(ruleId)
val result = kit.transactionOperations.executeAndSubmit(
    target = thresholdPolicyAddress,
    targetFn = "set_threshold",
    targetArgs = listOf(
        Scv.toUint32(newThreshold),
        contextRuleScVal,
        Address(kit.contractId!!).toSCVal()
    )
)

if (result.success) {
    println("Threshold updated: ${result.hash}")
}
```

---

#### submit

```kotlin
suspend fun submit(
    hostFunction: HostFunctionXdr,
    auth: List<SorobanAuthorizationEntryXdr>,
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

Low-level submission method. Accepts a pre-built host function and handles the full authorization lifecycle: simulation, auth entry extraction, WebAuthn signing, re-simulation, and submission.

This is the building block used internally by `transfer`, `contractCall`, and `executeAndSubmit`. Use it directly when you need full control over the host function construction.

**Parameters**:
- `hostFunction`: The Soroban host function to execute
- `auth`: Initial authorization entries (typically empty; simulation provides them)
- `forceMethod`: Optional submission method override
- `resolveContextRuleIds`: Optional callback that returns context rule IDs for each authorization entry. See [ResolveContextRuleIds](#resolvecontextruleids).

**Returns**: `TransactionResult` with submission outcome

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid configuration
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed
- `CredentialException`: Credential lookup failed during signing

---

#### fundWallet

```kotlin
suspend fun fundWallet(
    nativeTokenContract: String,
    forceMethod: SubmissionMethod? = null
): String
```

Funds the smart account wallet using Friendbot (testnet only).

Creates a temporary keypair, funds it via Friendbot, then transfers to the smart account.

**Parameters**:
- `nativeTokenContract`: Native token contract address (C-address)
- `forceMethod`: Optional submission method override

**Returns**: Amount funded in XLM as a decimal string

**Throws**:
- `ValidationException`: Invalid contract address
- `TransactionException`: Funding failed at any step

**Example**:

```kotlin
val fundedAmount = kit.transactionOperations.fundWallet(
    nativeTokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
)
println("Funded: $fundedAmount XLM")
```

---

### Result Types

#### TransactionResult
```kotlin
data class TransactionResult(
    val success: Boolean,
    val hash: String? = null,
    val ledger: UInt? = null,
    val error: String? = null
)
```

**Fields**:
- `success`: Whether the transaction succeeded
- `hash`: Transaction hash if submitted successfully
- `ledger`: Ledger number where confirmed
- `error`: Error message if failed

---

## Credential Management

### OZCredentialManager

Manages the lifecycle of stored credentials (WebAuthn passkeys).

```kotlin
val credMgr = kit.credentialManager
```

---

#### createPendingCredential

```kotlin
suspend fun createPendingCredential(
    credentialId: String,
    publicKey: ByteArray,
    contractId: String,
    nickname: String? = null,
    transports: List<String>? = null,
    deviceType: String? = null,
    backedUp: Boolean? = null
): StoredCredential
```

Creates a new pending credential in storage.

**Parameters**:
- `credentialId`: Base64URL-encoded credential ID (must be unique)
- `publicKey`: Uncompressed secp256r1 public key (65 bytes)
- `contractId`: Smart account contract address
- `nickname`: Display name for the credential (e.g., from the userName passed to createWallet)
- `transports`: Authenticator transport hints ("usb", "nfc", "ble", "internal")
- `deviceType`: "singleDevice" or "multiDevice"
- `backedUp`: Whether the passkey is backed up/synced

**Returns**: `StoredCredential` with PENDING deployment status

**Throws**:
- `ValidationException`: Invalid inputs
- `CredentialException.AlreadyExists`: Duplicate credential ID
- `StorageException`: Storage write failed

---

#### getAllCredentials

```kotlin
suspend fun getAllCredentials(): List<StoredCredential>
```

Retrieves all stored credentials regardless of deployment status.

**Returns**: List of all credentials (empty if none exist)

**Throws**: `StorageException.ReadFailed` if reading fails

---

#### getPendingCredentials

```kotlin
suspend fun getPendingCredentials(): List<StoredCredential>
```

Retrieves credentials with PENDING or FAILED deployment status.

**Returns**: List of credentials awaiting or failed deployment

**Throws**: `StorageException.ReadFailed` if reading fails

---

#### sync

```kotlin
suspend fun sync(credentialId: String): Boolean
```

Checks whether a pending credential's contract has been deployed on-chain. If the contract exists, the credential is removed from storage (it is no longer needed as a pending record). Used to resolve credentials left in "pending" state when the app closed before deployment confirmation.

**Parameters**: `credentialId` to check

**Returns**: True if the contract exists on-chain (credential removed from storage), false if not yet deployed

**Throws**:
- `CredentialException.NotFound`: Credential does not exist
- `StorageException.ReadFailed`: Storage read failed

---

#### syncAll

```kotlin
suspend fun syncAll(): SyncResult

data class SyncResult(
    val deployed: Int,
    val pending: Int,
    val failed: Int
)
```

Syncs all credentials with on-chain state.

**Returns**: Summary of credential states

**Throws**: `StorageException.ReadFailed` if reading fails

---

#### getCredential

```kotlin
suspend fun getCredential(credentialId: String): StoredCredential?
```

Retrieves a single credential by its ID.

**Parameters**: `credentialId` — Base64URL-encoded credential ID

**Returns**: The stored credential, or null if not found

**Throws**: `StorageException.ReadFailed` if reading fails

---

#### getCredentialsByContract

```kotlin
suspend fun getCredentialsByContract(contractId: String): List<StoredCredential>
```

Retrieves all credentials associated with a specific contract address.

**Parameters**: `contractId` — Smart account contract address (C-address)

**Returns**: List of credentials for the contract (empty if none)

**Throws**: `StorageException.ReadFailed` if reading fails

---

#### getForConnectedWallet

```kotlin
suspend fun getForConnectedWallet(): List<StoredCredential>
```

Retrieves credentials associated with the currently connected wallet's contract address. Returns an empty list if no wallet is connected or no credentials are found.

**Returns**: List of credentials for the connected wallet (empty if none or not connected)

**Throws**: `StorageException.ReadFailed` if reading fails

---

#### saveCredential

```kotlin
suspend fun saveCredential(
    credentialId: String,
    publicKey: ByteArray,
    nickname: String? = null,
    contractId: String? = null
): StoredCredential
```

Saves a credential directly to storage. Unlike [createPendingCredential], this does not set deployment metadata (transports, deviceType, backedUp). Use this for restoring credentials or manual credential management.

**Parameters**:
- `credentialId`: Base64URL-encoded credential ID
- `publicKey`: Uncompressed secp256r1 public key (65 bytes)
- `nickname`: Optional display name
- `contractId`: Optional contract address to associate with

**Returns**: The saved `StoredCredential`

**Throws**:
- `ValidationException.InvalidInput`: Empty credential ID or wrong public key size
- `StorageException.WriteFailed`: Storage write failed

---

#### clearAll

```kotlin
suspend fun clearAll()
```

Clears all credentials from storage. This operation is irreversible.

**Throws**: `StorageException.WriteFailed` if clearing fails

---

#### updateNickname

```kotlin
suspend fun updateNickname(credentialId: String, nickname: String?)
```

Updates the user-friendly nickname for a credential.

**Throws**: `CredentialException.NotFound`, `StorageException.WriteFailed`

---

#### deleteCredential

```kotlin
suspend fun deleteCredential(credentialId: String)
```

Deletes a pending credential from storage (syncs before deleting to ensure not deployed).

**Throws**:
- `CredentialException.NotFound`: Credential does not exist
- `CredentialException.Invalid`: Credential already deployed on-chain
- `StorageException.WriteFailed`: Deletion failed

---

### StoredCredential Type

```kotlin
data class StoredCredential(
    val credentialId: String,
    val publicKey: ByteArray,
    val contractId: String? = null,
    val deploymentStatus: CredentialDeploymentStatus = CredentialDeploymentStatus.PENDING,
    val deploymentError: String? = null,
    val createdAt: Long = currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val nickname: String? = null,
    val isPrimary: Boolean = false,
    val transports: List<String>? = null,
    val deviceType: String? = null,
    val backedUp: Boolean? = null
)

enum class CredentialDeploymentStatus {
    PENDING,
    FAILED
}
```

---


## Signers and Policies

### OZSignerManager

Manages signers for context rules. All methods accept an optional `selectedSigners` parameter for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey. When non-empty, routes through the multi-signer pipeline.

```kotlin
val signerMgr = kit.signerManager
```

---

#### addNewPasskeySigner

```kotlin
suspend fun addNewPasskeySigner(
    contextRuleId: UInt,
    userName: String,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): AddPasskeySignerResult
```

Registers a new passkey and adds it as a signer to a context rule in one step. Handles the full lifecycle: WebAuthn registration, credential storage, event emission, and on-chain signer addition.

Internally calls the WebAuthn provider's `register()` method, stores the credential, emits a `CredentialCreated` event, and delegates to `addPasskey()` for the on-chain transaction.

**Parameters**:
- `contextRuleId`: Context rule ID (e.g., 0 for Default)
- `userName`: Display name shown during the WebAuthn registration ceremony
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `AddPasskeySignerResult` with the new credential ID, public key, and transaction result

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `WebAuthnException.NotSupported`: No WebAuthn provider configured
- `WebAuthnException.RegistrationFailed`: Passkey registration failed
- `ValidationException`: Invalid public key or credential ID
- `StorageException`: Credential storage failed
- `TransactionException`: On-chain signer addition failed

**Example**:

```kotlin
val result = kit.signerManager.addNewPasskeySigner(
    contextRuleId = 0u,
    userName = "Alice backup device"
)
println("New passkey credential: ${result.credentialId}")
println("Transaction hash: ${result.transactionResult.hash}")
```

---

#### addPasskey

```kotlin
suspend fun addPasskey(
    contextRuleId: UInt,
    publicKey: ByteArray,
    credentialId: ByteArray,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Low-level method that adds a pre-registered WebAuthn passkey signer to a context rule. Use this when you handle WebAuthn registration yourself and have the raw cryptographic materials. For most use cases, prefer `addNewPasskeySigner()`.

**Parameters**:
- `contextRuleId`: Context rule ID (e.g., 0 for Default)
- `publicKey`: Uncompressed secp256r1 public key (65 bytes starting with 0x04)
- `credentialId`: WebAuthn credential ID
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult` indicating success or failure

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid public key size or credential ID
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
val result = kit.signerManager.addPasskey(
    contextRuleId = 0u,
    publicKey = secp256r1PublicKey,   // 65 bytes, uncompressed
    credentialId = credentialIdBytes  // raw WebAuthn credential ID
)
```

---

#### addDelegated

```kotlin
suspend fun addDelegated(
    contextRuleId: UInt,
    address: String,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Adds a delegated signer (account or contract) to a context rule.

**Parameters**:
- `contextRuleId`: Context rule ID
- `address`: Stellar address (G for accounts, C for contracts)
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult`

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid address format
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
// Single-signer (default)
val result = kit.signerManager.addDelegated(
    contextRuleId = 0u,
    address = "GA7Q..."
)

// Multi-signer
val result = kit.signerManager.addDelegated(
    contextRuleId = 0u,
    address = "GA7Q...",
    selectedSigners = listOf(
        SelectedSigner.Passkey(credentialId = credIdStr, credentialIdBytes = credIdBytes, keyData = keyData),
        SelectedSigner.Wallet("GA7Q...")
    )
)
```

---

#### addEd25519

```kotlin
suspend fun addEd25519(
    contextRuleId: UInt,
    verifierAddress: String,
    publicKey: ByteArray,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Adds an Ed25519 signer to a context rule.

**Parameters**:
- `contextRuleId`: Context rule ID
- `verifierAddress`: Ed25519 verifier contract address (C-address)
- `publicKey`: Ed25519 public key (32 bytes)
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult`

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid verifier address or public key size
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
val result = kit.signerManager.addEd25519(
    contextRuleId = 0u,
    verifierAddress = "CED25519VERIFIER...",
    publicKey = ed25519PublicKey  // 32 bytes
)
```

---

#### removeSigner

```kotlin
suspend fun removeSigner(
    contextRuleId: UInt,
    signerId: UInt,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Removes a signer from a context rule by its on-chain signer ID.

**Note**: Cannot remove the last signer unless policies exist.

**Parameters**:
- `contextRuleId`: Context rule ID
- `signerId`: The on-chain ID of the signer to remove
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult`

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `TransactionException`: Simulation, signing, or submission failed (including invalid signer ID rejected by contract)
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
// Get signer IDs from the parsed context rule
val rules = kit.contextRuleManager.listContextRules()
val rule = rules.first { it.id == 1u }
val signerId = rule.signerIds.first()

val result = kit.signerManager.removeSigner(
    contextRuleId = 1u,
    signerId = signerId
)
```

---

#### removeSigner (by signer value)

```kotlin
suspend fun removeSigner(
    contextRuleId: UInt,
    signer: SmartAccountSigner,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Convenience overload that resolves the on-chain signer ID internally. Fetches the context rule, finds the matching signer, and delegates to the ID-based `removeSigner`.

**Parameters**:
- `contextRuleId`: Context rule ID
- `signer`: The signer to remove (matched by equality against the rule's signers)
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization
- `forceMethod`: Optional submission method override

**Returns**: `TransactionResult`

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Signer not found on the context rule
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
val result = kit.signerManager.removeSigner(
    contextRuleId = 1u,
    signer = DelegatedSigner(address = "GA7Q...")
)
```

---

### OZPolicyManager

Manages policies for context rules. Provides both a generic `addPolicy` method for arbitrary policy contracts and convenience methods (`addSimpleThreshold`, `addWeightedThreshold`, `addSpendingLimit`) for common policy types. All methods accept an optional `selectedSigners` parameter for multi-signer authorization.

```kotlin
val policyMgr = kit.policyManager
```

---

#### addPolicy

```kotlin
suspend fun addPolicy(
    contextRuleId: UInt,
    policyAddress: String,
    installParams: SCValXdr,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Generic method for adding any policy contract to a context rule. The convenience methods (`addSimpleThreshold`, `addWeightedThreshold`, `addSpendingLimit`) delegate to this method.

**Parameters**:

| Parameter | Type | Description |
|---|---|---|
| `contextRuleId` | `UInt` | Context rule ID (e.g., 0 for Default) |
| `policyAddress` | `String` | C-address of the policy contract |
| `installParams` | `SCValXdr` | Policy-specific installation parameters |
| `selectedSigners` | `List<SelectedSigner>` | Optional signers for multi-signer auth (default: empty) |
| `forceMethod` | `SubmissionMethod?` | Optional submission method override (default: null, auto-detect) |

**Returns**: `TransactionResult`

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid policy address
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
// Add a custom policy with manually constructed install parameters
val installParams = Scv.toVec(
    listOf(
        Scv.toUint32(3u),         // custom param 1
        Scv.toBoolean(true)       // custom param 2
    )
)

val result = kit.policyManager.addPolicy(
    contextRuleId = 0u,
    policyAddress = "CBCD1234...",
    installParams = installParams
)

if (result.success) {
    println("Policy added: ${result.hash}")
}
```

---

#### addSimpleThreshold

```kotlin
suspend fun addSimpleThreshold(
    contextRuleId: UInt,
    policyAddress: String,
    threshold: UInt,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Adds a simple threshold policy (M-of-N signers).

**Parameters**:
- `contextRuleId`: Context rule ID
- `policyAddress`: Policy contract address (C-address)
- `threshold`: Number of signers required
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult`

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid policy address
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
val result = kit.policyManager.addSimpleThreshold(
    contextRuleId = 1u,
    policyAddress = "CTHRESHOLD...",
    threshold = 2u
)
```

---

#### addWeightedThreshold

```kotlin
suspend fun addWeightedThreshold(
    contextRuleId: UInt,
    policyAddress: String,
    signerWeights: Map<SmartAccountSigner, UInt>,
    threshold: UInt,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Adds a weighted threshold policy with configurable signer weights.

**Parameters**:
- `contextRuleId`: Context rule ID
- `policyAddress`: Policy contract address
- `signerWeights`: Map of signers to their weights (vote power)
- `threshold`: Minimum total weight required
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult`

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid policy address or empty signer weights map
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
val signer1 = DelegatedSigner(address = "GA7Q...")
val signer2 = DelegatedSigner(address = "GBXYZ...")

val result = kit.policyManager.addWeightedThreshold(
    contextRuleId = 1u,
    policyAddress = "CWEIGHTED...",
    signerWeights = mapOf(signer1 to 3u, signer2 to 2u),
    threshold = 4u
)
```

---

#### addSpendingLimit

```kotlin
suspend fun addSpendingLimit(
    contextRuleId: UInt,
    policyAddress: String,
    spendingLimit: String,
    periodLedgers: UInt,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Adds a spending limit policy.

**Parameters**:
- `contextRuleId`: Context rule ID
- `policyAddress`: Policy contract address
- `spendingLimit`: Maximum amount per period as a decimal string (e.g., "1000")
- `periodLedgers`: Period duration in ledgers (17,280 ≈ 1 day)
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult`

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Invalid policy address
- `IllegalArgumentException`: Invalid spending limit amount
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
// Add 1000 XLM per day limit
val result = kit.policyManager.addSpendingLimit(
    contextRuleId = 0u,
    policyAddress = "CBCD1234...",
    spendingLimit = "1000",
    periodLedgers = 17280u
)
```

---

#### removePolicy

```kotlin
suspend fun removePolicy(
    contextRuleId: UInt,
    policyId: UInt,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Removes a policy from a context rule by its on-chain policy ID.

**Parameters**:
- `contextRuleId`: Context rule ID
- `policyId`: The on-chain ID of the policy to remove
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult`

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `TransactionException`: Simulation, signing, or submission failed (including invalid policy ID rejected by contract)
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
// Get policy IDs from the parsed context rule
val rules = kit.contextRuleManager.listContextRules()
val rule = rules.first { it.id == 1u }
val policyId = rule.policyIds.first()

val result = kit.policyManager.removePolicy(
    contextRuleId = 1u,
    policyId = policyId
)
```

---

#### removePolicy (by policy address)

```kotlin
suspend fun removePolicy(
    contextRuleId: UInt,
    policyAddress: String,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Convenience overload that resolves the on-chain policy ID internally. Fetches the specified context rule (single RPC call), finds the policy matching the given address, and delegates to the ID-based `removePolicy`.

**Parameters**:
- `contextRuleId`: Context rule ID
- `policyAddress`: The policy contract address (C-address) to remove
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization
- `forceMethod`: Optional submission method override

**Returns**: `TransactionResult`

**Throws**:
- `WalletException.NotConnected`: Wallet is not connected
- `ValidationException`: Policy address not found on the context rule
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
val result = kit.policyManager.removePolicy(
    contextRuleId = 1u,
    policyAddress = "CPOLICY..."
)
```

---

### Policy Types

#### PolicyInstallParams
```kotlin
sealed class PolicyInstallParams {
    data class SimpleThreshold(val threshold: UInt) : PolicyInstallParams()
    data class WeightedThreshold(
        val signerWeights: Map<SmartAccountSigner, UInt>,
        val threshold: UInt
    ) : PolicyInstallParams()
    data class SpendingLimit(
        val spendingLimit: BigInteger,  // in stroops (1 XLM = 10,000,000 stroops)
        val periodLedgers: UInt
    ) : PolicyInstallParams()
}
// Note: The addSpendingLimit() convenience method accepts amount as a String
// (e.g., "100" or "10.5") and converts to stroops internally. When using
// PolicyInstallParams.SpendingLimit directly with addPolicy(), provide stroops as BigInteger.
```

---

## Context Rules

### OZContextRuleManager

Manages authorization rules that determine which signers and policies apply. Methods that modify rules accept an optional `selectedSigners` parameter for multi-signer authorization.

```kotlin
val ruleMgr = kit.contextRuleManager
```

---

#### addContextRule

```kotlin
suspend fun addContextRule(
    contextType: ContextRuleType,
    name: String,
    validUntil: UInt? = null,
    signers: List<SmartAccountSigner>,
    policies: Map<String, SCValXdr> = emptyMap(),
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `contextType` | `ContextRuleType` | Rule scope: `Default`, `CallContract(address)`, or `CreateContract(wasmHash)` |
| `name` | `String` | Human-readable name stored on-chain |
| `validUntil` | `UInt?` | Optional expiry ledger number (null = no expiry) |
| `signers` | `List<SmartAccountSigner>` | Signers who can authorize operations matching this context |
| `policies` | `Map<String, SCValXdr>` | Policy contract addresses mapped to their installation parameters |
| `selectedSigners` | `List<SelectedSigner>` | Optional signers for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey. |
| `forceMethod` | `SubmissionMethod?` | Optional submission method override (default: null, auto-detect) |

**Validation**: At least one signer or one policy is required.











**Contract limits**:
- Max 15 signers per rule
- Max 5 policies per rule

**Returns**: `TransactionResult`

**Throws**: `ValidationException` if name is empty, signer/policy count exceeds limits, or policy address is invalid. `TransactionException` if submission fails.

**Example**:

```kotlin
val result = kit.contextRuleManager.addContextRule(
    contextType = ContextRuleType.CallContract("CBCD1234..."),
    name = "TokenTransfers",
    validUntil = null,
    signers = listOf(
        ExternalSigner.webAuthn(verifier, pubkey, credId),
        DelegatedSigner("GA7Q...")
    ),
    policies = mapOf(
        "CBCD5678..." to policyParams
    )
)
```

---

#### getContextRule

```kotlin
suspend fun getContextRule(id: UInt): SCValXdr
```

Retrieves a single context rule by ID as a raw ScVal. Query operation (read-only, no authorization required).

**Parameters**:
- `id`: Context rule ID

**Returns**: Raw SCValXdr containing the rule data.

**Throws**: `TransactionException` if simulation fails or the rule does not exist.

---

#### getContextRulesCount

```kotlin
suspend fun getContextRulesCount(): UInt
```

Retrieves the total number of active context rules. Query operation (read-only, no authorization required).

**Returns**: Count of active rules.

**Throws**: `TransactionException` if simulation fails.

---

#### getAllContextRules

```kotlin
suspend fun getAllContextRules(maxScanId: UInt = config.maxContextRuleScanId): List<SCValXdr>
```

Retrieves all active context rules as raw ScVal objects. Iterates rule IDs from 0 upward, skipping gaps from removed rules, until all active rules are found or `maxScanId` is reached.

**Parameters**:
- `maxScanId`: Upper bound on rule IDs to scan. Defaults to `OZSmartAccountConfig.maxContextRuleScanId`.

**Returns**: List of raw ScVal objects, one per active context rule.

---

#### listContextRules

```kotlin
suspend fun listContextRules(maxScanId: UInt = config.maxContextRuleScanId): List<ParsedContextRule>
```

Lists all active context rules as parsed objects. This is the primary method for rule discovery.

**Parameters**:
- `maxScanId`: Upper bound on rule IDs to scan. Defaults to `OZSmartAccountConfig.maxContextRuleScanId`.

**Returns**: List of `ParsedContextRule` objects.

**Throws**: `ValidationException` if a rule cannot be parsed. `TransactionException` if simulation fails.

**Example**:

```kotlin
val rules = kit.contextRuleManager.listContextRules()
for (rule in rules) {
    println("Rule ${rule.id}: ${rule.name} (${rule.signers.size} signers, ${rule.policies.size} policies)")
}
```

---

#### updateName

```kotlin
suspend fun updateName(
    id: UInt,
    name: String,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Updates the name of a context rule.

**Parameters**:
- `id`: Context rule ID
- `name`: New rule name (must not be empty)
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult`

---

#### updateValidUntil

```kotlin
suspend fun updateValidUntil(
    id: UInt,
    validUntil: UInt?,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Updates the expiration ledger of a context rule.

**Parameters**:
- `id`: Context rule ID
- `validUntil`: New expiration ledger, or null to remove expiration
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult`

---

#### removeContextRule

```kotlin
suspend fun removeContextRule(
    id: UInt,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Removes a context rule.

**Parameters**:
- `id`: Context rule ID
- `selectedSigners`: Optional list of `SelectedSigner` for multi-signer authorization. When empty (default), uses single-signer auth with the connected passkey.
- `forceMethod`: Optional submission method override. When null (default), uses the configured submission method (relayer if available, RPC otherwise).

**Returns**: `TransactionResult`

---

### ParsedContextRule

Parsed representation of a context rule from on-chain data. Returned by `listContextRules()`.

```kotlin
data class ParsedContextRule(
    val id: UInt,
    val contextType: ContextRuleType,
    val name: String,
    val signers: List<SmartAccountSigner>,
    val signerIds: List<UInt>,
    val policies: List<String>,
    val policyIds: List<UInt>,
    val validUntil: UInt?
)
```

| Property | Type | Description |
|----------|------|-------------|
| `id` | `UInt` | Unique identifier of this context rule |
| `contextType` | `ContextRuleType` | Operations this rule applies to |
| `name` | `String` | Human-readable name |
| `signers` | `List<SmartAccountSigner>` | Signers who can authorize matching operations |
| `signerIds` | `List<UInt>` | On-chain signer IDs, positionally aligned with `signers` |
| `policies` | `List<String>` | Policy contract addresses (C-addresses) |
| `policyIds` | `List<UInt>` | On-chain policy IDs, positionally aligned with `policies` |
| `validUntil` | `UInt?` | Expiry ledger number, or null if no expiration |

---

### ContextRuleType

```kotlin
sealed class ContextRuleType {
    object Default : ContextRuleType()
    data class CallContract(val contractAddress: String) : ContextRuleType()
    data class CreateContract(val wasmHash: ByteArray) : ContextRuleType()
}
```

**Usage**:

```kotlin
// Default: applies to all operations
ContextRuleType.Default

// Specific contract: applies to calls to this contract
ContextRuleType.CallContract("CBCD1234...")

// Deployment: applies to contract deployments with this WASM hash
ContextRuleType.CreateContract(wasmHashBytes)
```

---

## Builders

### OZBuilders

Type-safe constructors for context rule types and signer utilities. Use these instead of constructing `ContextRuleType` directly to get input validation.

```kotlin
val builders = OZBuilders
```

#### createDefaultContext

```kotlin
fun createDefaultContext(): ContextRuleType
```

Creates a Default context rule type that matches any operation.

#### createCallContractContext

```kotlin
fun createCallContractContext(contractAddress: String): ContextRuleType
```

Creates a CallContract context rule type for a specific contract.

**Parameters**:
- `contractAddress`: The contract address (C-address, validated)

**Throws**: `ValidationException` if the address is not a valid C-address

**Example**:

```kotlin
val contextType = OZBuilders.createCallContractContext("CTOKEN...")
val result = kit.contextRuleManager.addContextRule(
    contextType = contextType,
    name = "Token operations",
    signers = signerList,
    policies = policyMap
)
```

#### createCreateContractContext

```kotlin
fun createCreateContractContext(wasmHashHex: String): ContextRuleType
fun createCreateContractContext(wasmHash: ByteArray): ContextRuleType
```

Creates a CreateContract context rule type for a specific WASM hash.

**Parameters**:
- `wasmHashHex`: 64-character hex string (with optional "0x" prefix), or
- `wasmHash`: 32-byte array

**Throws**: `ValidationException` if the hash length is incorrect

#### collectUniqueSignersFromRules

```kotlin
fun collectUniqueSignersFromRules(rules: List<ParsedContextRule>): List<SmartAccountSigner>
```

Collects unique signers from all context rules, removing duplicates across rules.

---

## Multi-Signer Operations

### OZMultiSignerManager

Manages multi-signature operations including token transfers and arbitrary contract calls. The caller is responsible for discovering signers from context rules and passing complete signer data via `SelectedSigner`.

All three signer kinds — `SelectedSigner.Passkey`, `SelectedSigner.Wallet`, and `SelectedSigner.Ed25519` — may appear in the same `selectedSigners` list. The pipeline collects signatures for each signer in order: passkey entries trigger WebAuthn prompts, while wallet and Ed25519 entries are signed through the kit-owned [OZSmartAccountKit.externalSigners] manager.

Note: The `selectedSigners` parameter is also available on individual manager methods (`signerManager`, `policyManager`, `contextRuleManager`). Use those methods directly instead of `multiSignerExecuteAndSubmit` when performing standard signer, policy, or rule operations with multi-signer authorization. The SDK handles argument encoding and routing internally.

```kotlin
val multiMgr = kit.multiSignerManager
```

---

#### multiSignerTransfer

```kotlin
suspend fun multiSignerTransfer(
    tokenContract: String,
    recipient: String,
    amount: String,
    selectedSigners: List<SelectedSigner>,
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

Executes a multi-signature token transfer. The amount is a decimal string (e.g., "100" or "10.5").

The caller explicitly lists every signer. There is no implicit connected passkey — include `SelectedSigner.Passkey()` if the connected passkey should sign. Signatures are collected in list order: each `Passkey` entry triggers one OS WebAuthn prompt; each `Wallet` and `Ed25519` entry signs through the kit-owned [OZSmartAccountKit.externalSigners] manager.

**Parameters**:
- `tokenContract`: Token contract address (C-address)
- `recipient`: Recipient address (G-address or C-address)
- `amount`: Decimal amount to transfer (e.g., "100" or "10.5")
- `selectedSigners`: All signers that must sign, in collection order
- `forceMethod`: Optional override for the submission method. When null (default), the SDK auto-detects whether to use the relayer or direct submission.
- `resolveContextRuleIds`: Optional callback that returns context rule IDs for each authorization entry. See [ResolveContextRuleIds](#resolvecontextruleids).

**Returns**: `TransactionResult`

**Example**:

```kotlin
// Signers are obtained from context rule discovery (client-side).
// All three signer kinds may appear in the same list.
val result = kit.multiSignerManager.multiSignerTransfer(
    tokenContract = "CBCD...",
    recipient = "GBXYZ...",
    amount = "50",
    selectedSigners = listOf(
        SelectedSigner.Passkey(
            credentialId = credIdStr,
            credentialIdBytes = credIdBytes,
            keyData = signer.keyData
        ),
        SelectedSigner.Wallet("GA7Q..."),
        SelectedSigner.Ed25519(
            verifierAddress = "CED25519VERIFIER2AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            publicKey = ed25519PublicKeyBytes   // 32 bytes
        )
    )
)
```

---

#### multiSignerContractCall

```kotlin
suspend fun multiSignerContractCall(
    target: String,
    targetFn: String,
    targetArgs: List<SCValXdr> = emptyList(),
    selectedSigners: List<SelectedSigner>,
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

Calls an arbitrary function on an external contract directly with multi-signer authorization. The host function invokes `target.targetFn(targetArgs)` without going through the smart account's `execute()` entry point. Context rules of type `CallContract(target)` are matched, allowing contract-specific multi-signer rules to apply.

This is the multi-signer counterpart to [contractCall](#contractcall).

**Parameters**:
- `target`: Contract address to call (C-address)
- `targetFn`: Function name to invoke on the target contract (must not be blank)
- `targetArgs`: Arguments for the target function as XDR values (use `Scv` helpers)
- `selectedSigners`: All signers that must sign, in collection order (must not be empty)
- `forceMethod`: Optional submission method override
- `resolveContextRuleIds`: Optional callback that returns context rule IDs for each authorization entry. See [ResolveContextRuleIds](#resolvecontextruleids).

**Returns**: `TransactionResult`

**Throws**: `ValidationException` if `target` is not a valid C-address, `targetFn` is blank, or `selectedSigners` is empty. `SmartAccountException` if signing or submission fails.

**Example**:

```kotlin
// Multi-signer token approve using a CallContract rule with threshold 2-of-3
val result = kit.multiSignerManager.multiSignerContractCall(
    target = tokenContractId,
    targetFn = "approve",
    targetArgs = listOf(
        Scv.toAddress(Address(smartAccountAddress).toSCAddress()),
        Scv.toAddress(Address(spenderAddress).toSCAddress()),
        Util.stroopsToI128ScVal(Util.amountToStroops("100")),
        Scv.toUint32(expirationLedger)
    ),
    selectedSigners = listOf(
        SelectedSigner.Passkey(
            credentialId = credIdStr,
            credentialIdBytes = credIdBytes,
            keyData = signer.keyData
        ),
        SelectedSigner.Wallet("GA7Q...")
    )
)
```

---

#### multiSignerExecuteAndSubmit

```kotlin
suspend fun multiSignerExecuteAndSubmit(
    target: String,
    targetFn: String,
    targetArgs: List<SCValXdr> = emptyList(),
    selectedSigners: List<SelectedSigner>,
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

Executes an arbitrary contract function through the smart account's `execute` entry point with multi-signer authorization. This is the multi-signer counterpart to `executeAndSubmit()`. Use it when a contract call must be authorized by more than one signer -- for example, a governance vote, a multisig swap, or any operation gated by a multi-signer context rule.

For standard signer, policy, and context rule operations, prefer passing `selectedSigners` directly to the respective manager methods (`signerManager.addDelegated()`, `policyManager.addPolicy()`, `contextRuleManager.updateName()`, etc.) instead of manually encoding arguments for `multiSignerExecuteAndSubmit`.

The method routes the call through the smart account contract's `execute(target, target_fn, target_args)` entry point and collects signatures from all `selectedSigners` before submission.

**Parameters**:
- `target`: Target contract address (C-address)
- `targetFn`: Function name to invoke on the target contract
- `targetArgs`: Pre-encoded arguments as `SCValXdr` list. Use `Scv` helpers (e.g., `Scv.toUint32`, `Scv.toBoolean`, `Scv.toAddress`) to encode each argument. Defaults to empty.
- `selectedSigners`: All signers that must sign, in collection order
- `forceMethod`: Optional override for the submission method. When null (default), the SDK auto-detects whether to use the relayer or direct submission.
- `resolveContextRuleIds`: Optional callback that returns context rule IDs for each authorization entry. See [ResolveContextRuleIds](#resolvecontextruleids).

**Returns**: `TransactionResult`

**Example**:

```kotlin
val result = kit.multiSignerManager.multiSignerExecuteAndSubmit(
    target = "CDAO_CONTRACT...",
    targetFn = "vote",
    targetArgs = listOf(
        Scv.toUint32(proposalId),
        Scv.toBoolean(true)
    ),
    selectedSigners = listOf(
        SelectedSigner.Passkey(
            credentialId = credIdStr,
            credentialIdBytes = credIdBytes,
            keyData = signer.keyData
        ),
        SelectedSigner.Wallet("GA7Q...")
    )
)
```

---

#### submitWithMultipleSigners

```kotlin
suspend fun submitWithMultipleSigners(
    hostFunction: HostFunctionXdr,
    selectedSigners: List<SelectedSigner>,
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

Low-level multi-signer submission pipeline. Accepts a pre-built `HostFunctionXdr` and handles the full lifecycle: simulation, auth entry extraction, multi-signer signing (passkey, wallet, and Ed25519), re-simulation, and submission.

This is the building block used internally by `multiSignerTransfer`, `multiSignerContractCall`, and `multiSignerExecuteAndSubmit`. Use it directly when you need full control over the host function construction.

**Parameters**:
- `hostFunction`: Pre-built host function to invoke
- `selectedSigners`: All signers that must sign, in collection order
- `forceMethod`: Optional submission method override
- `resolveContextRuleIds`: Optional callback that returns context rule IDs for each authorization entry

**Returns**: `TransactionResult`

**Throws**: `ValidationException` if [OZSmartAccountKit.externalSigners] has no signing source for a given wallet or Ed25519 signer (no in-memory key registered and no configured adapter that can sign for it). `SmartAccountException` if signing or submission fails.

---
## External Signers

### OZExternalSignerManager

The kit-owned manager for external (non-passkey) signers, accessed as [OZSmartAccountKit.externalSigners]. It is the single front door through which the multi-signer pipeline resolves and signs `SelectedSigner.Wallet` (G-address) and `SelectedSigner.Ed25519` entries.

The manager handles two signer kinds, each with two custody models:

1. **Wallet (G-address) signers** — sign auth entries for `SelectedSigner.Wallet` entries.
   - In-memory: register a secret seed at runtime via `kit.externalSigners.addFromSecret("S...")`. The SDK holds the key in memory.
   - Adapter: supply an `ExternalWalletAdapter` via `OZSmartAccountConfig.externalWallet` at kit construction (Freighter, hardware, remote). The SDK never sees the key.
   - Resolution precedence: for a wallet address the manager tries the in-memory keypair first, then the adapter.
2. **Ed25519 external signers** — sign auth digests for `SelectedSigner.Ed25519` entries. The registry key is the tuple `(verifierAddress, publicKey)`, matching the on-chain `External(verifier, keyData)` signer slot.
   - In-memory: register a raw 32-byte seed at runtime via `kit.externalSigners.addEd25519FromRawKey(rawBytes, verifierAddress)`. The SDK holds the key in memory.
   - Adapter: supply an `OZExternalEd25519SignerAdapter` via `OZSmartAccountConfig.externalEd25519Adapter` at kit construction (hardware wallet, HSM, remote signing service). The SDK never sees the key.
   - Resolution precedence: for an Ed25519 slot the manager tries the adapter first, then the in-memory key.

A developer who registers a single slot under both custody models should keep this asymmetry in mind: a wallet slot resolves to the in-memory keypair first, an Ed25519 slot resolves to the adapter first.

The four registration paths:

```kotlin
val config = OZSmartAccountConfig.builder(rpcUrl, networkPassphrase, wasmHash, verifier)
    // Wallet adapter custody model (model 1)
    .externalWallet(myWalletAdapter)
    // Ed25519 adapter custody model (model 1)
    .externalEd25519Adapter(myHardwareAdapter)
    .build()
val kit = OZSmartAccountKit.create(config)

// Wallet in-memory custody model (model 2): register a secret seed at runtime
val walletAddress = kit.externalSigners.addFromSecret("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34REYB6WBMG7CKKFJHYAEGQ")

// Ed25519 in-memory custody model (model 2): register a raw 32-byte seed at runtime
val ed25519PublicKey = kit.externalSigners.addEd25519FromRawKey(
    secretKeyBytes = rawSeedBytes,   // exactly 32 bytes
    verifierAddress = "CED25519VERIFIER2AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
)
```

For a concrete `ExternalWalletAdapter` implementation, see the example implementation in the smart account demo. See also [SelectedSigner.Ed25519](#selectedsigner).

#### Standalone construction (advanced)

The multi-signer pipeline always uses the kit-owned `kit.externalSigners`. Constructing a manager directly is an advanced, optional path for using the signer registry outside a kit (for example, to supply a custom `WalletConnectionStorage` for cross-launch wallet-connection persistence).

```kotlin
val manager = OZExternalSignerManager(
    networkPassphrase = "Test SDF Network ; September 2015",
    walletAdapter = myWalletAdapter,
    walletConnectionStorage = myStorage,
    ed25519Adapter = myHardwareAdapter
)
```

**Constructor Parameters**:
- `networkPassphrase`: Stellar network passphrase (e.g., `"Test SDF Network ; September 2015"`)
- `walletAdapter`: Optional `ExternalWalletAdapter` for external wallet connections (e.g., Freighter, LOBSTR). Required for `addFromWallet` and `restoreConnections`.
- `walletConnectionStorage`: Optional `WalletConnectionStorage` for persisting wallet connections across app launches. When null, wallet connections are not persisted.
- `ed25519Adapter`: Optional `OZExternalEd25519SignerAdapter` backing the Ed25519 adapter custody model. Consulted before the in-memory Ed25519 registry (adapter-first precedence).

---

#### addFromSecret

```kotlin
suspend fun addFromSecret(secretKey: String): String
```

Adds an Ed25519 keypair from a secret key (memory-only, not persisted).

**Parameters**: `secretKey` - Stellar secret key (S...)

**Returns**: Derived G-address

**Throws**: `SignerException.Invalid` if key is invalid

---

#### addFromWallet

```kotlin
suspend fun addFromWallet(): ConnectedWallet?
```

Connects an external wallet (e.g., Freighter, LOBSTR).

Connection metadata is persisted if storage is configured.

**Returns**: Connected wallet info, or null if user cancelled

**Throws**: `ConfigurationException.MissingConfig` if no adapter configured. `SmartAccountException` if the wallet connection fails.

---

#### canSignFor

```kotlin
suspend fun canSignFor(address: String): Boolean
```

Checks if any managed signer can sign for the given address.

**Parameters**: `address` - G-address to check

**Returns**: True if a keypair or connected wallet can sign

---

#### getAll

```kotlin
suspend fun getAll(): List<ExternalSignerInfo>

data class ExternalSignerInfo(
    val address: String,
    val type: ExternalSignerType,
    val walletName: String? = null,
    val walletId: String? = null
)

enum class ExternalSignerType {
    KEYPAIR,
    WALLET
}
```

Lists all managed external signers (keypair and wallet).

**Returns**: List of signer information

---

#### signAuthEntry

```kotlin
suspend fun signAuthEntry(
    address: String,
    authEntry: String
): SignAuthEntryResult

data class SignAuthEntryResult(
    val signedAuthEntry: String,
    val signerAddress: String? = null
)
```

Signs an authorization entry preimage with the appropriate signer.

**Parameters**:
- `address`: G-address identifying the signer
- `authEntry`: Base64-encoded HashIdPreimage XDR

**Returns**: Signed entry and signer address

**Throws**: `SignerException.NotFound`, `TransactionException.SigningFailed`

---

#### restoreConnections

```kotlin
suspend fun restoreConnections(): List<ConnectedWallet>
```

Restores previously connected external wallets from storage.

Idempotent: subsequent calls return currently connected wallets.

**Returns**: List of restored wallet connections

---

#### remove

```kotlin
suspend fun remove(address: String)
```

Removes a signer by address.

**Parameters**: `address` - G-address of signer to remove

---

#### removeAll

```kotlin
suspend fun removeAll()
```

Removes all managed signers and disconnects external wallets. Also clears all Ed25519 registrations from the in-memory registry. The configured Ed25519 adapter is not affected.

---

#### addEd25519FromRawKey

```kotlin
suspend fun addEd25519FromRawKey(secretKeyBytes: ByteArray, verifierAddress: String): ByteArray
```

Registers an Ed25519 signing keypair derived from a raw 32-byte secret key seed. The keypair is held in memory only and is never persisted to storage. Registration is keyed by the tuple `(verifierAddress, publicKey)`, so the same 32-byte seed registered under two different verifier addresses is stored as two independent entries.

**Parameters**:
- `secretKeyBytes`: Raw 32-byte Ed25519 seed (not an S-strkey). For sources that emit raw bytes (hardware tokens, HSMs), pass the bytes directly without encoding.
- `verifierAddress`: C-strkey of the Ed25519 verifier contract under which the signer is registered on-chain.

**Returns**: The derived 32-byte Ed25519 public key.

**Throws**:
- `ValidationException.InvalidInput` when `secretKeyBytes` is not exactly 32 bytes.
- `SignerException.Invalid` when keypair construction fails.

```kotlin
// Decode a hex-encoded 32-byte seed and register it
val seedHex = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f9"
val seedBytes = seedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
val derivedPublicKey = kit.externalSigners.addEd25519FromRawKey(
    secretKeyBytes = seedBytes,
    verifierAddress = "CED25519VERIFIER2AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
)
// derivedPublicKey is 32 bytes — verify it matches the on-chain signer's publicKey
```

---

#### canSignEd25519For

```kotlin
fun canSignEd25519For(verifierAddress: String, publicKey: ByteArray): Boolean
```

Returns whether a signing source is available for the given `(verifierAddress, publicKey)` pair. Checks the adapter first (adapter-first precedence), then the in-memory keypair registry. This is a pure getter — no suspension, no I/O.

**Parameters**:
- `verifierAddress`: C-strkey of the Ed25519 verifier contract.
- `publicKey`: 32-byte Ed25519 public key identifying the signer slot.

**Returns**: `true` when a signing source is available.

```kotlin
val canSign = kit.externalSigners.canSignEd25519For(verifierAddress, publicKey)
if (canSign) {
    println("Ready to sign for this signer slot")
}
```

---

#### signEd25519AuthDigest

```kotlin
suspend fun signEd25519AuthDigest(
    verifierAddress: String,
    publicKey: ByteArray,
    authDigest: ByteArray
): ByteArray
```

Produces a 64-byte Ed25519 signature over `authDigest`. Resolves the signing source using adapter-first precedence: the adapter is checked first; if it declines, the in-memory registry is used. Throws when neither source is available.

The registry mutex is released before awaiting the adapter's `signAuthDigest` call. This allows hardware-wallet adapters that may take several seconds (e.g., user confirmation on a device) or that may need to call back into the manager without causing deadlock.

**Parameters**:
- `verifierAddress`: C-strkey of the Ed25519 verifier contract.
- `publicKey`: 32-byte Ed25519 public key identifying the signer slot.
- `authDigest`: 32-byte auth digest to sign.

**Returns**: 64-byte raw Ed25519 signature.

**Throws**:
- `ValidationException.InvalidInput` when no signing source is registered for the given tuple.
- `TransactionException.SigningFailed` when the adapter or keypair signing call fails.

---

#### removeEd25519

```kotlin
suspend fun removeEd25519(verifierAddress: String, publicKey: ByteArray)
```

Removes a registered Ed25519 signer from the in-memory registry. No-op when no keypair is registered for `(verifierAddress, publicKey)`. The configured Ed25519 adapter is not affected.

**Parameters**:
- `verifierAddress`: C-strkey of the Ed25519 verifier contract.
- `publicKey`: 32-byte Ed25519 public key identifying the signer slot to remove.

```kotlin
kit.externalSigners.removeEd25519(verifierAddress, publicKey)
```

---

### OZExternalEd25519SignerAdapter

Adapter interface for out-of-process Ed25519 signing sources. Implement this interface to route Ed25519 signing through a hardware wallet, HSM, or remote signing service, and supply it via `OZSmartAccountConfig.externalEd25519Adapter` at kit construction.

```kotlin
interface OZExternalEd25519SignerAdapter {
    fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean
    suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray
}
```

**`canSignFor`**: Called before the in-memory registry is consulted. When this returns `true`, the adapter must be able to fulfill a subsequent `signAuthDigest` call for the same key without error.

**`signAuthDigest`**: Produces a 64-byte Ed25519 signature over `authDigest`. The pipeline locally verifies the returned signature before incorporating it into the authorization payload.

**Parameters**:
- `verifierAddress`: C-strkey of the Ed25519 verifier contract identifying the on-chain signer slot.
- `publicKey`: 32-byte Ed25519 public key identifying the signer slot.
- `authDigest`: 32-byte digest to sign, computed as `SHA-256(signaturePayload || contextRuleIds.toXDR())`.

```kotlin
class MyHardwareAdapter : OZExternalEd25519SignerAdapter {
    override fun canSignFor(verifierAddress: String, publicKey: ByteArray): Boolean =
        hardwareDevice.hasSigner(publicKey)

    override suspend fun signAuthDigest(authDigest: ByteArray, publicKey: ByteArray): ByteArray =
        hardwareDevice.sign(authDigest, publicKey)  // blocks until user confirms on device
}

val config = OZSmartAccountConfig.builder(rpcUrl, networkPassphrase, wasmHash, verifier)
    .externalEd25519Adapter(MyHardwareAdapter())
    .build()
val kit = OZSmartAccountKit.create(config)
```

---

### Types

#### ConnectedWallet
```kotlin
data class ConnectedWallet(
    val address: String,
    val walletId: String,
    val walletName: String
)
```

Information about a connected external wallet.

---

#### WalletConnectionStorage

```kotlin
interface WalletConnectionStorage {
    suspend fun getItem(key: String): String?
    suspend fun setItem(key: String, value: String)
    suspend fun removeItem(key: String)
}
```

Key-value storage interface for persisting external wallet connections across app launches. Implementations must be thread-safe.

Platform-specific implementations should use SharedPreferences (Android), UserDefaults (iOS/macOS), localStorage (Web), or any other persistent key-value store.

| Method | Description |
|--------|-------------|
| `getItem(key)` | Retrieves a value by key, or null if not found |
| `setItem(key, value)` | Stores a value for a key (overwrites existing) |
| `removeItem(key)` | Removes a value by key (no-op if absent) |

---

## Indexer Client

The SDK includes an indexer client for reverse lookups from signer credentials to smart account contracts. The indexer is auto-configured for testnet and mainnet when no explicit URL is provided.

### Using via OZSmartAccountKit (Recommended)

```kotlin
val kit = OZSmartAccountKit.create(config)

// Discover contracts by credential ID
val contracts = kit.indexerClient?.lookupByCredentialId(credentialId)

// Discover contracts by signer address
val contracts = kit.indexerClient?.lookupByAddress("GABC...")

// Get full contract details (rules, signers, policies)
val details = kit.indexerClient?.getContract("CABC...")

// Health and stats
val healthy = kit.indexerClient?.isHealthy()
val stats = kit.indexerClient?.getStats()
```

### Using OZIndexerClient Directly

```kotlin
// Create client for a specific network (uses default indexer URL)
val indexer = OZIndexerClient.forNetwork("Test SDF Network ; September 2015")

// Or with a custom URL
val indexer = OZIndexerClient(
    indexerUrl = "https://smart-account-indexer.sdf-ecosystem.workers.dev",
    timeoutMs = 10000
)
```

### Constructor

```kotlin
class OZIndexerClient(
    indexerUrl: String,
    timeoutMs: Long = OZConstants.DEFAULT_INDEXER_TIMEOUT_MS
)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `indexerUrl` | `String` | Indexer service URL (must use HTTPS, or http://localhost for development) |
| `timeoutMs` | `Long` | Request timeout in milliseconds (default: `OZConstants.DEFAULT_INDEXER_TIMEOUT_MS`) |

### Factory Methods

#### forNetwork

```kotlin
companion object {
    fun forNetwork(
        networkPassphrase: String,
        timeoutMs: Long = OZConstants.DEFAULT_INDEXER_TIMEOUT_MS
    ): OZIndexerClient?
}
```

Creates an `OZIndexerClient` using the default indexer URL for a known network. Returns null if no default URL is configured for the network.

#### getDefaultUrl

```kotlin
fun getDefaultUrl(networkPassphrase: String): String?
```

Returns the default indexer URL for a given network passphrase, or null if unknown.

### Methods

#### lookupByCredentialId

```kotlin
suspend fun lookupByCredentialId(credentialId: String): CredentialLookupResponse
```

Finds all smart account contracts where the given credential is registered as a signer. The credential ID must be Base64URL-encoded (RFC 4648, no padding). The SDK converts it to hex internally before calling the indexer API.

**Returns**: `CredentialLookupResponse`

**Throws**: `ValidationException` if the credential ID is not valid base64url. `IndexerException` if the request fails or times out.

---

#### lookupByAddress

```kotlin
suspend fun lookupByAddress(address: String): AddressLookupResponse
```

Finds all smart account contracts where the given address is registered as a signer. Accepts both G-addresses (Stellar accounts) and C-addresses (contracts).

**Returns**: `AddressLookupResponse`

**Throws**: `ValidationException` if the address format is invalid. `IndexerException` if the request fails or times out.

---

#### getContract

```kotlin
suspend fun getContract(contractId: String): ContractDetailsResponse
```

Retrieves full details for a smart account contract including all context rules, signers, and policies.

**Returns**: `ContractDetailsResponse`

**Throws**: `ValidationException` if the contract ID format is invalid. `IndexerException` if the request fails or times out.

---

#### getStats

```kotlin
suspend fun getStats(): IndexerStatsResponse
```

Returns indexer service statistics (total contracts indexed, credentials, ledger range, event type breakdown).

**Throws**: `IndexerException` if the request fails or times out.

---

#### isHealthy

```kotlin
suspend fun isHealthy(): Boolean
```

Returns true if the indexer service is reachable and healthy. Does not throw — returns false for any error condition.

---

#### close

```kotlin
fun close()
```

Closes the HTTP client. The client must not be used after calling this. When using via `kit.indexerClient`, the kit's `close()` handles this automatically.

---

### Response Types

#### CredentialLookupResponse

```kotlin
data class CredentialLookupResponse(
    val credentialId: String,
    val contracts: List<IndexedContractSummary>,
    val count: Int
)
```

#### AddressLookupResponse

```kotlin
data class AddressLookupResponse(
    val signerAddress: String,
    val contracts: List<IndexedContractSummary>,
    val count: Int
)
```

#### ContractDetailsResponse

```kotlin
data class ContractDetailsResponse(
    val contractId: String,
    val summary: IndexedContractSummary,
    val contextRules: List<IndexedContextRule>
)
```

#### IndexedContractSummary

```kotlin
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
```

#### IndexedContextRule

```kotlin
data class IndexedContextRule(
    val contextRuleId: Int,
    val signers: List<IndexedSigner>,
    val policies: List<IndexedPolicy>
)
```

#### IndexedSigner

```kotlin
data class IndexedSigner(
    val signerType: String,       // "External", "Delegated", or "Native"
    val signerAddress: String?,   // G-address (Delegated/Native signers)
    val credentialId: String?     // Hex-encoded credential ID (External signers)
)
```

#### IndexedPolicy

```kotlin
data class IndexedPolicy(
    val policyAddress: String,
    val installParams: JsonElement?  // Policy-specific parameters
)
```

#### IndexerStatsResponse

```kotlin
data class IndexerStatsResponse(
    val stats: IndexerStats
)

data class IndexerStats(
    val totalEvents: Long,
    val uniqueContracts: Long,
    val uniqueCredentials: Long,
    val firstLedger: Long,
    val lastLedger: Long,
    val eventTypes: List<EventTypeCount>
)

data class EventTypeCount(
    val eventType: String,
    val count: Long
)
```

---

## Relayer Client

The SDK includes a relayer client for fee-sponsored transaction submission. When configured, the SDK automatically routes transactions through the relayer so users don't need XLM to pay fees.

### Using via OZSmartAccountKit (Recommended)

When the relayer is configured, all transaction submissions use it automatically:

```kotlin
val config = OZSmartAccountConfig(
    // ... other config
    relayerUrl = "https://my-relayer-proxy.example.com"
)
val kit = OZSmartAccountKit.create(config)

// Transactions automatically use the relayer
kit.transactionOperations.transfer(tokenContract, recipient, "10")

// Bypass the relayer for a specific operation
kit.transactionOperations.transfer(
    tokenContract, recipient, "10",
    forceMethod = SubmissionMethod.RPC
)

// Access the relayer client directly
kit.relayerClient?.sendXdr(transactionEnvelope)
```

### Constructor

```kotlin
class OZRelayerClient(
    relayerUrl: String,
    timeoutMs: Long = OZConstants.DEFAULT_RELAYER_TIMEOUT_MS,
    httpClient: HttpClient? = null
)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `relayerUrl` | `String` | Relayer endpoint URL (must use HTTPS, or http://localhost for development) |
| `timeoutMs` | `Long` | Default request timeout in milliseconds (default: 6 minutes for testnet retries) |
| `httpClient` | `HttpClient?` | Optional custom HTTP client for testing (default: null, creates per-request clients) |

**Throws**: `ConfigurationException.InvalidConfig` if `relayerUrl` is blank or not HTTPS.

### Properties

#### isConfigured

```kotlin
val isConfigured: Boolean
```

Whether the client is properly configured with a valid URL.

### Methods

#### send

```kotlin
suspend fun send(
    hostFunction: HostFunctionXdr,
    authEntries: List<SorobanAuthorizationEntryXdr>,
    perRequestTimeoutMs: Long? = null
): RelayerResponse
```

Submits a host function with signed authorization entries for fee sponsoring. The relayer wraps the operation in a fee-bump transaction using its own channel account. XDR encoding to base64 is handled internally.

This method does not throw. All error conditions are returned in the `RelayerResponse`.

**Parameters**:
- `hostFunction`: Host function XDR to execute
- `authEntries`: Signed authorization entries
- `perRequestTimeoutMs`: Optional per-request timeout override

**Returns**: `RelayerResponse`

---

#### sendXdr

```kotlin
suspend fun sendXdr(
    transactionEnvelope: TransactionEnvelopeXdr,
    perRequestTimeoutMs: Long? = null
): RelayerResponse
```

Submits a complete signed transaction envelope for fee-bumping. Used when the transaction contains source_account auth entries that require the deployer signature. XDR encoding to base64 is handled internally.

This method does not throw. All error conditions are returned in the `RelayerResponse`.

**Parameters**:
- `transactionEnvelope`: Signed transaction envelope XDR
- `perRequestTimeoutMs`: Optional per-request timeout override

**Returns**: `RelayerResponse`

---

### Response and Error Types

#### RelayerResponse

```kotlin
data class RelayerResponse(
    val success: Boolean,
    val transactionId: String? = null,
    val hash: String? = null,
    val status: String? = null,
    val error: String? = null,
    val errorCode: String? = null,
    val details: JsonElement? = null
)
```

| Property | Type | Description |
|----------|------|-------------|
| `success` | `Boolean` | Whether the transaction was successfully submitted |
| `transactionId` | `String?` | Transaction ID assigned by the relayer |
| `hash` | `String?` | Transaction hash on the Stellar network |
| `status` | `String?` | Transaction status (e.g., "PENDING", "SUCCESS", "ERROR") |
| `error` | `String?` | Error message if the request failed |
| `errorCode` | `String?` | Error code for programmatic handling (see `RelayerErrorCodes`) |
| `details` | `JsonElement?` | Additional error details from the relayer |

#### RelayerErrorCodes

```kotlin
object RelayerErrorCodes {
    const val INVALID_PARAMS = "INVALID_PARAMS"
    const val INVALID_XDR = "INVALID_XDR"
    const val POOL_CAPACITY = "POOL_CAPACITY"
    const val SIMULATION_FAILED = "SIMULATION_FAILED"
    const val ONCHAIN_FAILED = "ONCHAIN_FAILED"
    const val INVALID_TIME_BOUNDS = "INVALID_TIME_BOUNDS"
    const val FEE_LIMIT_EXCEEDED = "FEE_LIMIT_EXCEEDED"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val TIMEOUT = "TIMEOUT"
}
```

---

## Events

> **Scope — SDK lifecycle events only.** `kit.events` emits **kit-level** events (wallet connected/disconnected, credential created/deleted, session expired, transaction signed/submitted). It does **not** emit on-chain smart-account contract events such as `SignerAdded`, `SignerRemoved`, `PolicyInstalled`, `PolicyRemoved`, `ContextRuleAdded`, or `ContextRuleRemoved`. Those are emitted by the OpenZeppelin smart-account contract and must be queried via `SorobanServer.getEvents()` with the account's contract ID as a filter. Subscribing with `kit.events.on<SignerAdded>{}` will compile but never fire.
>
> To fetch on-chain contract events (after the wallet is connected):
>
> ```kotlin
> val session = kit.walletOperations.connectWallet() ?: error("not connected")
> val response = sorobanServer.getEvents(
>     GetEventsRequest(
>         startLedger = fromLedger,
>         filters = listOf(
>             GetEventsRequest.EventFilter(
>                 type = GetEventsRequest.EventFilterType.CONTRACT,
>                 contractIds = listOf(session.contractId)
>             )
>         )
>     )
> )
> ```
>
> Inspect `response.events`; each event's `topic` and `value` are base64-XDR-encoded `SCVal` entries that can be parsed with the SDK's XDR utilities.

### SmartAccountEvent

Events emitted by the Smart Account Kit during wallet operations.

```kotlin
sealed class SmartAccountEvent {
    data class WalletConnected(
        val contractId: String,
        val credentialId: String
    ) : SmartAccountEvent()

    data class WalletDisconnected(
        val contractId: String
    ) : SmartAccountEvent()

    data class CredentialCreated(
        val credential: StoredCredential
    ) : SmartAccountEvent()

    data class CredentialDeleted(
        val credentialId: String
    ) : SmartAccountEvent()

    data class SessionExpired(
        val contractId: String,
        val credentialId: String
    ) : SmartAccountEvent()

    data class TransactionSigned(
        val contractId: String,
        val credentialId: String?
    ) : SmartAccountEvent()

    data class TransactionSubmitted(
        val hash: String,
        val success: Boolean
    ) : SmartAccountEvent()
}
```

### SmartAccountEventEmitter

Accessed via `kit.events`. Manages event subscriptions with thread-safe listener management and error isolation (one failing listener does not affect others).

#### on

```kotlin
inline fun <reified T : SmartAccountEvent> on(listener: (T) -> Unit): () -> Unit
```

Subscribes to events of a specific type. Returns an unsubscribe function.

Note: Uses Kotlin reified generics — not callable from Java or Swift. Use `addListener` for cross-language compatibility.

```kotlin
val unsubscribe = kit.events.on<SmartAccountEvent.WalletConnected> { event ->
    println("Connected to ${event.contractId}")
}

kit.events.on<SmartAccountEvent.TransactionSubmitted> { event ->
    println("Transaction ${event.hash}: ${if (event.success) "success" else "failed"}")
}

// Later: unsubscribe()
```

---

#### once

```kotlin
inline fun <reified T : SmartAccountEvent> once(listener: (T) -> Unit): () -> Unit
```

Subscribes to a single occurrence of an event. The listener is automatically unsubscribed after the first matching event. Returns an unsubscribe function that can cancel the subscription before the event fires.

```kotlin
kit.events.once<SmartAccountEvent.TransactionSubmitted> { event ->
    println("First transaction: ${event.hash}")
}
```

---

#### addListener

```kotlin
fun addListener(listener: SmartAccountEventListener): () -> Unit
```

Subscribes a listener that receives all event types. The listener must dispatch internally using `when` (Kotlin) or `instanceof` (Java) / pattern matching (Swift). Returns an unsubscribe function.

This is the cross-language alternative to `on<T>` — callable from Java and Swift.

```kotlin
val unsubscribe = kit.events.addListener { event ->
    when (event) {
        is SmartAccountEvent.WalletConnected ->
            println("Connected to ${event.contractId}")
        is SmartAccountEvent.TransactionSubmitted ->
            println("Transaction ${event.hash}: success=${event.success}")
        else -> {}
    }
}
// Later: unsubscribe()
```

---

#### removeAllListeners

```kotlin
fun removeAllListeners(eventType: String? = null)
```

Removes all listeners for a specific event type, or all listeners if no type is specified.

```kotlin
// Remove all WalletConnected listeners
kit.events.removeAllListeners("WalletConnected")

// Remove all listeners for all event types
kit.events.removeAllListeners()
```

---

#### listenerCount

```kotlin
fun listenerCount(eventType: String): Int
```

Returns the number of listeners for a specific event type. Includes both type-specific listeners (via `on`) and global listeners (via `addListener`).

---

#### setErrorHandler

```kotlin
fun setErrorHandler(handler: ((event: SmartAccountEvent, error: Throwable) -> Unit)?)
```

Sets an error handler for listener failures. When a listener throws, the error is caught (other listeners still execute) and passed to this handler. Pass `null` to disable.

```kotlin
kit.events.setErrorHandler { event, error ->
    println("Listener error on $event: ${error.message}")
}
```

---

### SmartAccountEventListener

```kotlin
fun interface SmartAccountEventListener {
    fun onEvent(event: SmartAccountEvent)
}
```

Functional interface for event listeners. Used by `addListener` for cross-language compatibility (Java, Swift).

---

## Exceptions

All Smart Account exceptions extend `SmartAccountException` and include an error code and message.

### SmartAccountException

Base exception class with code and message.

```kotlin
sealed class SmartAccountException(
    val code: SmartAccountErrorCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)
```

### SmartAccountErrorCode

> **Two independent namespaces share the 3xxx range.** `SmartAccountErrorCode` is the **SDK** error enum, surfaced via `SmartAccountException.code` when the kit raises a credential/wallet/WebAuthn/etc. error locally. A separate set of error codes — also in the 3xxx range — is defined by the **on-chain** OpenZeppelin smart-account contract and surfaced in transaction simulation/result XDR (typically wrapped in `TransactionException.SimulationFailed`). The two overlap but do not collide at runtime because they arrive through different channels:
>
> | Numeric code | SDK meaning (`SmartAccountErrorCode`) | On-chain meaning (OZ contract) |
> |---|---|---|
> | 3002 | `CREDENTIAL_ALREADY_EXISTS` | `UnvalidatedContext` |
> | 3003 | `CREDENTIAL_INVALID` | `ExternalVerificationFailed` |
>
> When inspecting an error code, first check the exception type to determine which namespace it belongs to. SDK-defined contract codes that the SDK interprets directly are declared in [`ContractErrorCodes`](#contracterrorcodes); the full on-chain enum is defined by the smart-account contract source (see [`SmartAccountError`, `WebAuthnError`, and policy error enums in `OpenZeppelin/stellar-contracts`](https://github.com/OpenZeppelin/stellar-contracts)).

```kotlin
enum class SmartAccountErrorCode(val code: Int) {
    // 1xxx: Configuration
    INVALID_CONFIG(1001),
    MISSING_CONFIG(1002),

    // 2xxx: Wallet state
    WALLET_NOT_CONNECTED(2001),
    WALLET_ALREADY_EXISTS(2002),
    WALLET_NOT_FOUND(2003),

    // 3xxx: Credential
    CREDENTIAL_NOT_FOUND(3001),
    CREDENTIAL_ALREADY_EXISTS(3002),
    CREDENTIAL_INVALID(3003),
    CREDENTIAL_DEPLOYMENT_FAILED(3004),

    // 4xxx: WebAuthn
    WEBAUTHN_REGISTRATION_FAILED(4001),
    WEBAUTHN_AUTHENTICATION_FAILED(4002),
    WEBAUTHN_NOT_SUPPORTED(4003),
    WEBAUTHN_CANCELLED(4004),

    // 5xxx: Transaction
    TRANSACTION_SIMULATION_FAILED(5001),
    TRANSACTION_SIGNING_FAILED(5002),
    TRANSACTION_SUBMISSION_FAILED(5003),
    TRANSACTION_TIMEOUT(5004),

    // 6xxx: Signer
    SIGNER_NOT_FOUND(6001),
    SIGNER_INVALID(6002),

    // 7xxx: Validation
    INVALID_ADDRESS(7001),
    INVALID_AMOUNT(7002),
    INVALID_INPUT(7003),

    // 8xxx: Storage
    STORAGE_READ_FAILED(8001),
    STORAGE_WRITE_FAILED(8002),

    // 9xxx: Session
    SESSION_EXPIRED(9001),
    SESSION_INVALID(9002),

    // 10xxx: Indexer
    INDEXER_REQUEST_FAILED(10001),
    INDEXER_TIMEOUT(10002)
}
```

---

### ConfigurationException

```kotlin
sealed class ConfigurationException : SmartAccountException {
    class InvalidConfig(message: String, cause: Throwable? = null)
    class MissingConfig(message: String, cause: Throwable? = null)
}
```

**Error Codes**: 1001 (INVALID_CONFIG), 1002 (MISSING_CONFIG)

---

### WalletException

```kotlin
sealed class WalletException : SmartAccountException {
    class NotConnected(message: String = "Wallet is not connected", cause: Throwable? = null)
    class NotFound(message: String, cause: Throwable? = null)
    class AlreadyExists(message: String, cause: Throwable? = null)
}
```

**Error Codes**: 2001 (NOT_CONNECTED), 2002 (ALREADY_EXISTS), 2003 (NOT_FOUND)

---

### CredentialException

```kotlin
sealed class CredentialException : SmartAccountException {
    class NotFound(message: String, cause: Throwable? = null)
    class AlreadyExists(message: String, cause: Throwable? = null)
    class Invalid(message: String, cause: Throwable? = null)
    class DeploymentFailed(message: String, cause: Throwable? = null)
}
```

**Error Codes**: 3001-3004

---

### WebAuthnException

```kotlin
sealed class WebAuthnException : SmartAccountException {
    class RegistrationFailed(message: String, cause: Throwable? = null)
    class AuthenticationFailed(message: String, cause: Throwable? = null)
    class NotSupported(message: String, cause: Throwable? = null)
    class Cancelled(message: String, cause: Throwable? = null)
}
```

**Error Codes**: 4001-4004

---

### TransactionException

```kotlin
sealed class TransactionException : SmartAccountException {
    class SimulationFailed(message: String, cause: Throwable? = null)
    class SigningFailed(message: String, cause: Throwable? = null)
    class SubmissionFailed(message: String, cause: Throwable? = null)
    class Timeout(message: String, cause: Throwable? = null)
}
```

**Error Codes**: 5001-5004

---

### ValidationException

```kotlin
sealed class ValidationException : SmartAccountException {
    class InvalidAddress(message: String, cause: Throwable? = null)
    class InvalidAmount(message: String, cause: Throwable? = null)
    class InvalidInput(message: String, cause: Throwable? = null)
}
```

**Error Codes**: 7001 (INVALID_ADDRESS), 7002 (INVALID_AMOUNT), 7003 (INVALID_INPUT)

---

### StorageException

```kotlin
sealed class StorageException : SmartAccountException {
    class ReadFailed(message: String, cause: Throwable? = null)
    class WriteFailed(message: String, cause: Throwable? = null)
}
```

**Error Codes**: 8001-8002

---

### SignerException

```kotlin
sealed class SignerException : SmartAccountException {
    class NotFound(message: String, cause: Throwable? = null)
    class Invalid(message: String, cause: Throwable? = null)
}
```

**Error Codes**: 6001-6002

---

### SessionException

```kotlin
sealed class SessionException : SmartAccountException {
    class Expired(message: String, cause: Throwable? = null)
    class Invalid(message: String, cause: Throwable? = null)
}
```

**Error Codes**: 9001-9002

---

### IndexerException

```kotlin
sealed class IndexerException : SmartAccountException {
    class RequestFailed(message: String, cause: Throwable? = null)
    class Timeout(message: String, cause: Throwable? = null)
}
```

**Error Codes**: 10001-10002

---

## Types

### SelectedSigner

Sealed class that specifies which signers should participate in a multi-signature operation. The caller lists every signer explicitly — there is no implicit connected passkey. Three signer kinds are supported and may be mixed in a single list.

```kotlin
sealed class SelectedSigner {
    /** Passkey (WebAuthn) signer. Each instance triggers one OS authentication prompt. */
    data class Passkey(
        val credentialId: String? = null,
        val credentialIdBytes: ByteArray? = null,
        val keyData: ByteArray? = null,
        val transports: List<String>? = null
    ) : SelectedSigner()

    /** Delegated wallet signer identified by its Stellar G-address. */
    data class Wallet(val address: String) : SelectedSigner()

    /**
     * Ed25519 external signer identified by the verifier contract address and 32-byte public key.
     *
     * This case is a pure identifier — it carries no signing material. A signing source
     * must be registered on [OZSmartAccountKit.externalSigners] before including this
     * selector in a multi-signer operation: register an in-memory key at runtime via
     * [OZExternalSignerManager.addEd25519FromRawKey], or supply an
     * [OZExternalEd25519SignerAdapter] via [OZSmartAccountConfig.externalEd25519Adapter]
     * at kit construction.
     */
    data class Ed25519(
        val verifierAddress: String,
        val publicKey: ByteArray
    ) : SelectedSigner()
}
```

**`Passkey` fields**:
- `credentialId`: Base64URL-encoded credential ID for display/logging.
- `credentialIdBytes`: Raw credential ID bytes for the WebAuthn allowCredentials constraint.
- `keyData`: Full key data (secp256r1 public key + credentialId bytes). Required for multi-signer transfers. Populated from the signer data obtained during context rule discovery.
- `transports`: Optional transport hints (e.g., `"internal"`, `"hybrid"`). Enables cross-device authentication flows.

**`Ed25519` fields**:
- `verifierAddress`: C-strkey of the Ed25519 verifier contract that is stored as part of the on-chain `External(verifierAddress, publicKey)` signer entry.
- `publicKey`: 32-byte Ed25519 public key identifying the signer slot on the smart account.

**Equality**: `SelectedSigner.Ed25519` overrides `equals` and `hashCode` using content-based comparison for the `publicKey` byte array. Two instances with identical `verifierAddress` and `publicKey` bytes are equal regardless of object identity.

See also: [OZExternalSignerManager](#ozexternalsignermanager) for registering Ed25519 signing sources.

---

### SmartAccountSigner

Sealed class hierarchy for transaction signers.

```kotlin
sealed class SmartAccountSigner {
    abstract fun toScVal(): SCValXdr
    abstract val uniqueKey: String
}

data class DelegatedSigner(val address: String) : SmartAccountSigner()

data class ExternalSigner(
    val verifierAddress: String,
    val keyData: ByteArray
) : SmartAccountSigner() {
    companion object {
        fun webAuthn(
            verifierAddress: String,
            publicKey: ByteArray,
            credentialId: ByteArray
        ): ExternalSigner

        fun ed25519(
            verifierAddress: String,
            publicKey: ByteArray
        ): ExternalSigner
    }
}
```

---

### StorageAdapter

Interface for credential persistence. Configured via the `storage` field on `OZSmartAccountConfig` (defaults to `InMemoryStorageAdapter()`).

```kotlin
interface StorageAdapter {
    suspend fun save(credential: StoredCredential)
    suspend fun get(credentialId: String): StoredCredential?
    suspend fun getAll(): List<StoredCredential>
    suspend fun getByContract(contractId: String): List<StoredCredential>
    suspend fun update(credentialId: String, updates: StoredCredentialUpdate)
    suspend fun delete(credentialId: String)
    suspend fun clear()
    suspend fun saveSession(session: StoredSession)
    suspend fun getSession(): StoredSession?
    suspend fun clearSession()
}

class InMemoryStorageAdapter : StorageAdapter
```

---

### WebAuthnProvider

Interface for platform-specific WebAuthn operations.

```kotlin
interface WebAuthnProvider {
    suspend fun register(
        challenge: ByteArray,
        userId: ByteArray,
        userName: String
    ): WebAuthnRegistrationResult

    suspend fun authenticate(
        challenge: ByteArray,
        allowCredentials: List<AllowCredential>? = null
    ): WebAuthnAuthenticationResult
}

data class WebAuthnRegistrationResult(
    val credentialId: ByteArray,
    val publicKey: ByteArray,
    val attestationObject: ByteArray,
    val transports: List<String>? = null,
    val deviceType: String? = null,
    val backedUp: Boolean? = null
)

data class WebAuthnAuthenticationResult(
    val credentialId: ByteArray,
    val authenticatorData: ByteArray,
    val clientDataJSON: ByteArray,
    val signature: ByteArray
)
```

**`authenticate` parameters**:
- `challenge`: The challenge bytes to sign (authorization payload hash, 32 bytes).
- `allowCredentials`: Optional list of `AllowCredential` descriptors. Constrains which passkeys the authenticator offers and indicates how the client can reach the authenticator. When null, discoverable credential selection is used. Including transport hints (e.g., `"hybrid"`) enables cross-device authentication flows such as QR code scanning.

> **Breaking change (1.5.0)**: The `authenticate()` parameter was renamed from `allowCredentialIds: List<ByteArray>?` to `allowCredentials: List<AllowCredential>?`. Use `AllowCredential.fromIds()` to migrate existing `List<ByteArray>` values.

---

### AllowCredential

A credential descriptor pairing a credential ID with optional transport hints. Used in `WebAuthnProvider.authenticate()` to constrain which passkeys the authenticator offers and to indicate how the client can reach the authenticator.

```kotlin
data class AllowCredential(
    val id: ByteArray,
    val transports: List<String>? = null
) {
    companion object {
        fun fromId(id: ByteArray): AllowCredential
        fun fromIds(ids: List<ByteArray>): List<AllowCredential>
    }
}
```

**Properties**:
- `id`: The raw credential ID bytes.
- `transports`: Optional list of transport hints. Recognized values include `"internal"`, `"hybrid"`, `"usb"`, `"ble"`, and `"nfc"`. When null, the authenticator uses its default transport selection. Unknown transport strings are passed through without validation.

**Equality**: `equals()` and `hashCode()` use `contentEquals` / `contentHashCode` for the `id` byte array, ensuring correct comparison and collection behavior.

**Factory methods**:
- `AllowCredential.fromId(id)`: Creates an `AllowCredential` from a raw credential ID with no transport hints.
- `AllowCredential.fromIds(ids)`: Creates a list of `AllowCredential` from raw credential IDs with no transport hints. Useful for migrating code that previously used `List<ByteArray>`.

---

### ExternalWalletAdapter

Interface for integrating external wallets (Freighter, LOBSTR, etc). Configured via the `externalWallet` field on `OZSmartAccountConfig` (defaults to `null`).

```kotlin
interface ExternalWalletAdapter {
    suspend fun connect(): ConnectedWallet?
    suspend fun disconnect()
    suspend fun disconnectByAddress(address: String) { }  // default no-op
    fun canSignFor(address: String): Boolean
    fun getWalletForAddress(address: String): ConnectedWallet?
    fun getConnectedWallets(): List<ConnectedWallet>
    suspend fun signAuthEntry(
        authEntryXdr: String,
        options: SignAuthEntryOptions? = null
    ): SignAuthEntryResult
    suspend fun reconnect(walletId: String): ConnectedWallet?
}

data class SignAuthEntryOptions(
    val networkPassphrase: String? = null,
    val address: String? = null
)
```

---

### SmartAccountSignature

Sealed base class for all signature variants attached to a smart-account auth payload. Concrete subtypes: [WebAuthnSignature](#webauthnsignature), [Ed25519Signature](#ed25519signature), [PolicySignature](#policysignature).

```kotlin
sealed class SmartAccountSignature {
    abstract fun toScVal(): SCValXdr
    abstract fun toAuthPayloadBytes(): ByteArray
}
```

`toScVal()` returns the variant-specific Soroban value as it appears in the on-wire signature slot. `toAuthPayloadBytes()` returns the byte sequence that is inserted into the signer-payload Map under the signer's key — Map-shaped variants XDR-encode their `toScVal()`; the `Ed25519Signature` variant returns the raw 64-byte signature directly because the Ed25519 verifier contract expects `BytesN<64>` without an XDR envelope.

---

### WebAuthnSignature

```kotlin
data class WebAuthnSignature(
    val authenticatorData: ByteArray,
    val clientData: ByteArray,
    val signature: ByteArray
) : SmartAccountSignature()
```

Represents a WebAuthn signature with authenticator and client data. `toScVal()` returns an `SCValXdr.Map` with three keys in alphabetical order: `authenticator_data`, `client_data`, `signature`. The 64-byte `signature` field is the compact ECDSA signature with normalized S value.

---

### Ed25519Signature

```kotlin
data class Ed25519Signature(
    val publicKey: ByteArray,
    val signature: ByteArray
) : SmartAccountSignature()
```

Represents an Ed25519 signature produced by an Ed25519 external signer (see [SelectedSigner.Ed25519](#selectedsigner) and [OZExternalEd25519SignerAdapter](#ozexternaled25519signeradapter)).

- `publicKey` (32 bytes) is used for local verification before submission and for content-equality. It is NOT transmitted on the wire — the Ed25519 verifier contract reads the public key from the smart account's `External(verifier, key_data)` storage.
- `signature` (64 bytes) is the raw Ed25519 signature.
- `toScVal()` returns `SCValXdr.Bytes` containing the raw 64-byte signature. Not a Map.
- `toAuthPayloadBytes()` returns the raw 64-byte signature directly with no XDR envelope.

---

### PolicySignature

```kotlin
object PolicySignature : SmartAccountSignature()
```

Singleton signaling policy-based authorization. Used when a context rule is satisfied by policy evaluation (e.g. threshold, weighted threshold, spending limit) rather than by an explicit signer signature. `toScVal()` returns an empty `SCValXdr.Map`.

Obtain the canonical instance via `PolicySignature` (no constructor).

---

### TransactionResult

Returned by all state-changing operations (transfer, contractCall, addContextRule, etc.).

```kotlin
data class TransactionResult(
    val success: Boolean,
    val hash: String? = null,
    val ledger: UInt? = null,
    val error: String? = null
)
```

| Property | Type | Description |
|----------|------|-------------|
| `success` | `Boolean` | Whether the transaction was confirmed on-chain |
| `hash` | `String?` | Transaction hash (present on success) |
| `ledger` | `UInt?` | Ledger number where the transaction was included |
| `error` | `String?` | Error message if the transaction failed |

---

### PolicyInstallParams

Sealed class for policy installation parameters passed to `addContextRule` and `addPolicy`.

```kotlin
sealed class PolicyInstallParams {
    data class SimpleThreshold(
        val threshold: UInt
    ) : PolicyInstallParams()

    data class WeightedThreshold(
        val signerWeights: Map<SmartAccountSigner, UInt>,
        val threshold: UInt
    ) : PolicyInstallParams()

    data class SpendingLimit(
        val spendingLimit: BigInteger,
        val periodLedgers: UInt
    ) : PolicyInstallParams()
}
```

| Variant | Description |
|---------|-------------|
| `SimpleThreshold` | Requires at least M-of-N signers. All signers have equal weight. `threshold` must be > 0. |
| `WeightedThreshold` | Each signer has a configurable weight. Sum of approving weights must meet the threshold. |
| `SpendingLimit` | Limits spending per ledger period. `spendingLimit` is in stroops (as `BigInteger`), `periodLedgers` is the number of ledgers in the period. |

---

### StoredCredential

WebAuthn credential with deployment and usage metadata. Returned by credential operations and the `CredentialCreated` event.

```kotlin
data class StoredCredential(
    val credentialId: String,
    val publicKey: ByteArray,
    val contractId: String? = null,
    val deploymentStatus: CredentialDeploymentStatus = CredentialDeploymentStatus.PENDING,
    val deploymentError: String? = null,
    val createdAt: Long = currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val nickname: String? = null,
    val isPrimary: Boolean = false,
    val transports: List<String>? = null,
    val deviceType: String? = null,
    val backedUp: Boolean? = null
)
```

| Property | Type | Description |
|----------|------|-------------|
| `credentialId` | `String` | Base64URL-encoded WebAuthn credential ID |
| `publicKey` | `ByteArray` | Uncompressed secp256r1 public key (65 bytes, 0x04 prefix) |
| `contractId` | `String?` | Smart account contract address (C-address) |
| `deploymentStatus` | `CredentialDeploymentStatus` | Current deployment status |
| `deploymentError` | `String?` | Error message if deployment failed |
| `createdAt` | `Long` | Creation timestamp (milliseconds) |
| `lastUsedAt` | `Long?` | Last signing timestamp (milliseconds) |
| `nickname` | `String?` | User-friendly name (e.g., "MacBook Pro Touch ID") |
| `isPrimary` | `Boolean` | Whether this is the default signing credential |
| `transports` | `List<String>?` | Authenticator transport hints ("usb", "nfc", "ble", "internal") |
| `deviceType` | `String?` | "singleDevice" (hardware key) or "multiDevice" (synced passkey) |
| `backedUp` | `Boolean?` | Whether the credential is synced to a cloud provider |

---

### CredentialDeploymentStatus

```kotlin
enum class CredentialDeploymentStatus {
    PENDING,
    FAILED
}
```

| Value | Description |
|-------|-------------|
| `PENDING` | Credential created but smart account contract not yet deployed |
| `FAILED` | Deployment transaction failed |

Note: There is no `SUCCESS` status. On successful deployment the credential is removed from storage and the wallet connects using on-chain data.

---

### ConnectedWallet

Returned by `ExternalWalletAdapter.connect()` and `restoreConnections()`.

```kotlin
data class ConnectedWallet(
    val address: String,
    val walletId: String,
    val walletName: String
)
```

| Property | Type | Description |
|----------|------|-------------|
| `address` | `String` | Stellar G-address of the connected wallet |
| `walletId` | `String` | Wallet identifier for reconnection (e.g., "freighter", "lobstr") |
| `walletName` | `String` | Human-readable display name (e.g., "Freighter", "LOBSTR") |

---

### SignAuthEntryResult

Returned by `ExternalWalletAdapter.signAuthEntry()`.

```kotlin
data class SignAuthEntryResult(
    val signedAuthEntry: String,
    val signerAddress: String? = null
)
```

| Property | Type | Description |
|----------|------|-------------|
| `signedAuthEntry` | `String` | Base64-encoded raw Ed25519 signature (64 bytes) |
| `signerAddress` | `String?` | G-address that produced the signature (null if wallet does not report it) |

---

### ResolveContextRuleIds

```kotlin
typealias ResolveContextRuleIds = suspend (
    entry: SorobanAuthorizationEntryXdr,
    index: Int
) -> List<UInt>
```

Callback that resolves context rule IDs for a given authorization entry during transaction signing. Called once per entry. The `entry` is the authorization entry being signed, and `index` is its position in the authorization list. Return the list of context rule IDs the contract should evaluate for that entry.

**Usage**:

```kotlin
// Same rule for all entries
resolveContextRuleIds = { _, _ -> listOf(ruleId) }

// Different rules per entry
resolveContextRuleIds = { entry, index ->
    if (index == 0) listOf(1u) else listOf(2u)
}
```

---

### SubmissionMethod

```kotlin
enum class SubmissionMethod {
    RELAYER,
    RPC
}
```

Controls how transactions are submitted (fee-sponsored or direct).

---

## Constants

### SmartAccountConstants

Defined in `smartaccount/core/SmartAccountErrors.kt`. Contains crypto constants used across the core layer.

```kotlin
object SmartAccountConstants {
    const val ED25519_PUBLIC_KEY_SIZE = 32              // Size in bytes of an Ed25519 public key (RFC 8032)
    const val ED25519_SECRET_KEY_SIZE = 32              // Size in bytes of an Ed25519 secret key seed (RFC 8032)
    const val SECP256R1_PUBLIC_KEY_SIZE = 65            // Size in bytes of an uncompressed secp256r1 public key
    const val UNCOMPRESSED_PUBKEY_PREFIX: Byte = 0x04   // Uncompressed point prefix byte as defined in SEC 1
    const val ED25519_SIGNATURE_SIZE = 64               // Ed25519 signature length in bytes (RFC 8032)
    const val ED25519_SECRET_KEY_STRKEY_LENGTH = 56     // Length of a Stellar S-strkey secret seed
    const val ADDRESS_PREFIX_LENGTH = 8                 // Number of characters of an address used in error-message excerpts
}
```

### ContractErrorCodes

Defined in `smartaccount/core/SmartAccountErrors.kt`. A **curated subset** of on-chain error codes from the OpenZeppelin smart-account contract that the SDK interprets directly when decoding failed transaction results. Error code range: 3xxx.

This object does not mirror the full on-chain enum. The smart-account contract additionally defines codes for context-rule lookup, auth-payload validation, external verification, WebAuthn parsing (3110–3119), and policy enforcement (3200–3227 across the simple-threshold, weighted-threshold, and spending-limit policies). See the contract source for the full list: [OpenZeppelin/stellar-contracts — `packages/accounts`](https://github.com/OpenZeppelin/stellar-contracts/tree/main/packages/accounts). Note that several values in the 3xxx range also exist in the SDK-side [`SmartAccountErrorCode`](#smartaccounterrorcode) enum with different meanings — the two are distinguished by the exception type they arrive through.

```kotlin
object ContractErrorCodes {
    const val MATH_OVERFLOW = 3012                   // Integer arithmetic overflow occurred in the contract
    const val KEY_DATA_TOO_LARGE = 3013              // The key_data field on a signer exceeds the maximum allowed size
    const val CONTEXT_RULE_IDS_LENGTH_MISMATCH = 3014  // The number of context rule IDs does not match the expected count
    const val NAME_TOO_LONG = 3015                   // A name field (e.g. context rule name) exceeds the maximum allowed length
    const val UNAUTHORIZED_SIGNER = 3016             // The signer is not authorized to sign the given context rule
}
```

### OZConstants

Defined in `smartaccount/oz/OZConstants.kt`. Contains OZ-specific configuration defaults and contract limits.

```kotlin
object OZConstants {
    const val DEFAULT_SESSION_EXPIRY_MS = 604_800_000L  // 7 days
    const val DEFAULT_INDEXER_TIMEOUT_MS = 10_000L   // 10 seconds
    const val DEFAULT_RELAYER_TIMEOUT_MS = 360_000L  // 6 minutes
    const val WEBAUTHN_TIMEOUT_MS = 60_000L          // 60 seconds
    const val FRIENDBOT_RESERVE_XLM = 5
    const val DEFAULT_TIMEOUT_SECONDS = 30
    const val MAX_SIGNERS = 15
    const val MAX_POLICIES = 5
}
```

### Util

Defined in `Util.kt`. Contains general-purpose Stellar network constants available SDK-wide.

```kotlin
object Util {
    const val STROOPS_PER_XLM = 10_000_000L  // Number of stroops in one XLM
    const val LEDGERS_PER_HOUR = 720          // Average ledgers per hour (~5s per ledger)
    const val LEDGERS_PER_DAY = 17_280        // Average ledgers per day (~5s per ledger)
}
```

---

## Platform-Specific Implementations

Each platform requires a `WebAuthnProvider` (passkey authentication) and a `StorageAdapter` (credential persistence). The SDK provides production-ready implementations for all supported platforms.

### Android

**WebAuthn**: `AndroidWebAuthnProvider` — Uses Android Credential Manager API (requires API 28+).

```kotlin
val webauthnProvider = AndroidWebAuthnProvider(
    context = applicationContext,
    rpId = "example.com",
    rpName = "My App",
    timeout = OZConstants.WEBAUTHN_TIMEOUT_MS,          // optional, default 60s
    authenticatorAttachment = null                       // optional, null = both platform and cross-platform
)
```

**Storage**: `AndroidStorageAdapter` — Uses EncryptedSharedPreferences backed by Android Keystore.

```kotlin
val storage = AndroidStorageAdapter(context = applicationContext)
```

### iOS / macOS

**WebAuthn**: `AppleWebAuthnProvider` — Uses ASAuthorization framework.

```kotlin
val webauthnProvider = AppleWebAuthnProvider(
    rpId = "example.com",
    rpName = "My App",
    timeout = OZConstants.WEBAUTHN_TIMEOUT_MS            // optional, default 60s
)
```

**Storage** (two options):

`UserDefaultsStorageAdapter` — Uses NSUserDefaults. Suitable for non-sensitive credential metadata.

```kotlin
val storage = UserDefaultsStorageAdapter(
    suiteName = "com.soneso.stellar.smartaccount"        // optional, default shown
)
```

`KeychainStorageAdapter` — Uses Apple Keychain Services. Recommended for production (encrypted, hardware-backed).

```kotlin
val storage = KeychainStorageAdapter(
    serviceName = "com.soneso.stellar.smartaccount"      // optional, default shown
)
```

### JavaScript (Browser / Node.js)

**WebAuthn**: `JsWebAuthnProvider` — Uses the Web Authentication API (`navigator.credentials`).

```kotlin
val webauthnProvider = JsWebAuthnProvider(
    rpId = "example.com",
    rpName = "My App",
    timeout = OZConstants.WEBAUTHN_TIMEOUT_MS            // optional, default 60s
)
```

**Storage** (two options):

`IndexedDBStorageAdapter` — Uses IndexedDB. Recommended for browser apps (larger storage, async).

```kotlin
val storage = IndexedDBStorageAdapter(
    dbName = "stellar_smart_account"                     // optional, default shown
)
```

`LocalStorageAdapter` — Uses `window.localStorage`. Simpler alternative for small datasets.

```kotlin
val storage = LocalStorageAdapter(
    keyPrefix = "stellar_sa_"                            // optional, default shown
)
```

### Common (All Platforms)

`InMemoryStorageAdapter` — Non-persistent, used as the default when no storage is configured. Data is lost on app restart.

---

## Error Handling Example

```kotlin
try {
    val wallet = kit.walletOperations.createWallet(
        userName = "Alice",
        autoSubmit = true
    )
    println("Created: ${wallet.contractId}")
} catch (e: WebAuthnException.Cancelled) {
    println("User cancelled authentication")
} catch (e: WebAuthnException.NotSupported) {
    println("WebAuthn not supported on this platform")
} catch (e: TransactionException.SimulationFailed) {
    println("Transaction simulation failed: ${e.message}")
} catch (e: SmartAccountException) {
    println("Error [${e.code.code}]: ${e.message}")
    e.cause?.let { println("Caused by: ${it.message}") }
}
```

---

## License

Stellar SDK Kotlin Multiplatform - Apache License 2.0
