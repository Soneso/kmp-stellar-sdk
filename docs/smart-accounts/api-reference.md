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
    storage = storageAdapter              // defaults to InMemoryStorageAdapter (AndroidKeystoreStorageAdapter / UserDefaultsStorageAdapter / IndexedDBStorageAdapter)
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
    val rpId: String? = null,
    val rpName: String = "Smart Account",
    val sessionExpiryMs: Long = 604800000L,  // 7 days
    val signatureExpirationLedgers: Int = 720,  // ~1 hour
    val timeoutInSeconds: Int = 30,
    val relayerUrl: String? = null,
    val indexerUrl: String? = null,
    val webauthnProvider: WebAuthnProvider? = null,
    val storage: StorageAdapter = InMemoryStorageAdapter(),
    val externalWallet: ExternalWalletAdapter? = null,
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
- `rpId`: WebAuthn Relying Party ID (domain name)
- `rpName`: WebAuthn Relying Party name displayed to users
- `sessionExpiryMs`: Session validity duration in milliseconds
- `signatureExpirationLedgers`: Auth entry signature expiration in ledgers
- `timeoutInSeconds`: Operation timeout in seconds
- `relayerUrl`: Optional relayer endpoint for fee sponsoring
- `indexerUrl`: Optional indexer endpoint for credential-to-contract mapping
- `webauthnProvider`: Platform-specific WebAuthn provider
- `storage`: Storage adapter for credential persistence (defaults to `InMemoryStorageAdapter()`)
- `externalWallet`: Optional external wallet adapter for multi-signer support
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
| Android | `AndroidKeystoreStorageAdapter` | Encrypted storage via Android Keystore |
| iOS/macOS | `UserDefaultsStorageAdapter` | Persists to UserDefaults |
| JS/Web | `IndexedDBStorageAdapter` | Browser IndexedDB (recommended for web) |
| JS/Web | `LocalStorageAdapter` | Browser localStorage |

#### ExternalWalletAdapter

Interface for delegated (G-address) signers in multi-signer operations. Implement this to integrate external Stellar wallets (e.g., Freighter, Lobstr) that can sign auth entries on behalf of delegated signers. Key methods:

- `canSignFor(address: String): Boolean` — check if the adapter can sign for an address
- `signAuthEntry(preimageXdr: String, options: SignAuthEntryOptions?): SignAuthEntryResult` — sign an auth entry preimage
- `connect(): ConnectedWallet?` — connect to the external wallet
- `disconnect()` — disconnect all wallets
- `getConnectedWallets(): List<ConnectedWallet>` — list connected wallets

The SDK includes `OZExternalSignerManager` which implements this interface and manages keypair-based signers. See [External Signers](#external-signers) for details.

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
    .rpName("My Wallet")
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

Returns the deployer keypair that will be used for contract deployment and transaction submission. If `deployerKeypair` is explicitly set in the config, that value is returned. Otherwise, a deterministic keypair is derived from `SHA256("openzeppelin-smart-account-kit")`. The derivation is deterministic and reproducible, always producing the same deployer address. The deployer only pays fees; it does not control user wallets.

**Returns**: The configured deployer or the default deterministic deployer

**Throws**: `ConfigurationException` if default deployer creation fails

#### effectiveIndexerUrl

```kotlin
fun effectiveIndexerUrl(): String?
```

Returns the indexer URL that will be used after applying fallback logic. If `indexerUrl` is explicitly set in the config, that value is returned. Otherwise, the SDK falls back to the built-in default URL for the network. Testnet has a built-in default URL; mainnet does not (returns null unless explicitly configured).

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
```

Connects to an existing smart account wallet. Returns `null` when no session exists and no WebAuthn prompt is requested, enabling a two-phase connect pattern.

**Options Decision Matrix**:

| Options | Behavior |
|---------|----------|
| (default) | Session restore; return `null` if no session |
| `credentialId` and/or `contractId` | Direct connect, skip session check; always returns non-null |
| `fresh = true` | Skip session, always trigger WebAuthn |
| `prompt = true` | Session restore; trigger WebAuthn if no session |
| `fresh = true, prompt = true` | `fresh` takes priority, always trigger WebAuthn |

**Returns**: `ConnectWalletResult?` -- non-null on successful connection, `null` when no session exists and `prompt` is `false`

When `credentialId` and/or `contractId` are provided, the direct connect path always returns non-null.

**Throws**:
- `WebAuthnException`: Authentication failed (only when WebAuthn is triggered)
- `WalletException.NotFound`: Wallet not found for credential
- `ValidationException`: Invalid options

**Example**:

```kotlin
// Phase 1: Silent restore at app launch (returns null if no session)
val result = walletOps.connectWallet()
if (result != null) {
    println("Silently reconnected to ${result.contractId}")
} else {
    // Show a "Connect" button in the UI
}

// Phase 2: User taps "Connect" -- triggers WebAuthn if no session
val connected = walletOps.connectWallet(
    ConnectWalletOptions(prompt = true)
)

// Force fresh authentication
val fresh = walletOps.connectWallet(
    ConnectWalletOptions(fresh = true)
)

// Direct connection (always returns non-null)
val direct = walletOps.connectWallet(
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
data class ConnectWalletResult(
    val credentialId: String,
    val contractId: String,
    val restoredFromSession: Boolean
)
```

**Fields**:
- `credentialId`: Base64URL-encoded credential ID
- `contractId`: Smart account contract address
- `restoredFromSession`: True if reconnected from saved session, false if new authentication

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
- `WebAuthnException.NotSupported`: No WebAuthn provider configured
- `WalletException.NotConnected`: Wallet is not connected
- `WebAuthnException.RegistrationFailed`: Passkey registration failed
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

**TS SDK divergence**: The TypeScript SDK's `addPasskey()` returns an unsigned transaction for the caller to submit. The KMP SDK's `addNewPasskeySigner()` returns a `TransactionResult` because the transaction is assembled and submitted internally (via relayer when configured).

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

**Throws**: `SmartAccountException` if validation or submission fails

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

**Throws**: Various SmartAccount exceptions

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
- `ValidationException`: Invalid address or wallet not connected
- `TransactionException`: Transaction simulation or submission failed

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














**Contract limits**:
- Max 15 signers per rule
- Max 5 policies per rule

**Returns**: `TransactionResult`

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

#### getContextRulesCount

```kotlin
suspend fun getContextRulesCount(): UInt
```

Retrieves the total number of active context rules.

**Returns**: Count of active rules

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

The caller explicitly lists every signer. There is no implicit connected passkey -- include `SelectedSigner.Passkey()` if the connected passkey should sign. Signatures are collected in list order: each `Passkey` entry triggers one OS WebAuthn prompt; each `Wallet` entry requests a delegated auth entry from the external wallet.

**Parameters**:
- `tokenContract`: Token contract address (C-address)
- `recipient`: Recipient address (G-address or C-address)
- `amount`: Amount in XLM
- `selectedSigners`: All signers that must sign, in collection order
- `forceMethod`: Optional override for the submission method. When null (default), the SDK auto-detects whether to use the relayer or direct submission.
- `resolveContextRuleIds`: Optional callback that returns context rule IDs for each authorization entry. See [ResolveContextRuleIds](#resolvecontextruleids).

**Returns**: `TransactionResult`

**Example**:

```kotlin
// Signers are obtained from context rule discovery (client-side)
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
        SelectedSigner.Wallet("GA7Q...")
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
- `targetFn`: Function name to invoke on the target contract
- `targetArgs`: Arguments for the target function as XDR values (use `Scv` helpers)
- `selectedSigners`: All signers that must sign, in collection order
- `forceMethod`: Optional submission method override
- `resolveContextRuleIds`: Optional callback that returns context rule IDs for each authorization entry. See [ResolveContextRuleIds](#resolvecontextruleids).

**Returns**: `TransactionResult`

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

Low-level multi-signer submission pipeline. Accepts a pre-built `HostFunctionXdr` and handles the full lifecycle: simulation, auth entry extraction, multi-signer signing (WebAuthn + delegated), re-simulation, and submission.

This is the building block used internally by `multiSignerTransfer`, `multiSignerContractCall`, and `multiSignerExecuteAndSubmit`. Use it directly when you need full control over the host function construction.

**Parameters**:
- `hostFunction`: Pre-built host function to invoke
- `selectedSigners`: All signers that must sign, in collection order
- `forceMethod`: Optional submission method override
- `resolveContextRuleIds`: Optional callback that returns context rule IDs for each authorization entry

**Returns**: `TransactionResult`

---
## External Signers

### OZExternalSignerManager

Manages external (non-passkey) signers: Ed25519 keypairs and external wallet connections.

```kotlin
val externalMgr = OZExternalSignerManager(
    networkPassphrase = "Test SDF Network ; September 2015",
    walletAdapter = myWalletAdapter,
    walletConnectionStorage = myStorage
)
```

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

**Throws**: `ConfigurationException.MissingConfig` if no adapter configured

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

Removes all managed signers and disconnects external wallets.

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

## Indexer Client

The SDK includes an indexer client for reverse lookups from signer credentials to smart account contracts. The indexer is auto-configured for testnet when no explicit URL is provided.

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
// Create client for a specific network
val indexer = OZIndexerClient.forNetwork("Test SDF Network ; September 2015")

// Or with a custom URL
val indexer = OZIndexerClient(
    baseUrl = "https://smart-account-indexer.sdf-ecosystem.workers.dev",
    timeoutMs = 10000
)
```

### Methods

#### lookupByCredentialId

```kotlin
suspend fun lookupByCredentialId(credentialId: String): CredentialLookupResponse
```

Finds all smart account contracts where the given credential is registered as a signer. The credential ID is the Base64URL-encoded WebAuthn credential identifier.

**Returns**: `CredentialLookupResponse` with `credentialId`, `contracts: List<IndexedContractSummary>`, `count`

#### lookupByAddress

```kotlin
suspend fun lookupByAddress(address: String): AddressLookupResponse
```

Finds all smart account contracts where the given address is registered as a signer. Accepts both G-addresses (Stellar accounts) and C-addresses (contracts).

**Returns**: `AddressLookupResponse` with `signerAddress`, `contracts: List<IndexedContractSummary>`, `count`

#### getContract

```kotlin
suspend fun getContract(contractId: String): ContractDetailsResponse
```

Retrieves full details for a smart account contract including all context rules, signers, and policies.

**Returns**: `ContractDetailsResponse` with `contractId`, `summary: IndexedContractSummary`, `contextRules: List<IndexedContextRule>`

#### getStats

```kotlin
suspend fun getStats(): IndexerStatsResponse
```

Returns indexer service statistics (total contracts indexed, etc.).

#### isHealthy

```kotlin
suspend fun isHealthy(): Boolean
```

Returns true if the indexer service is reachable and healthy.

#### close

```kotlin
fun close()
```

Closes the HTTP client. The client must not be used after calling this. When using via `kit.indexerClient`, the kit's `close()` handles this automatically.

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
kit.relayerClient?.sendXdr(signedTransactionXdr)
```

### Methods

#### send

```kotlin
suspend fun send(
    hostFunctionXdr: String,
    authXdrs: List<String>,
    timeoutMs: Long? = null
): RelayerResponse
```

Submits a host function with signed authorization entries for fee sponsoring. The relayer wraps the operation in a fee-bump transaction using its own channel account.

**Parameters**:
- `hostFunctionXdr`: Base64-encoded host function XDR
- `authXdrs`: Base64-encoded signed authorization entries
- `timeoutMs`: Optional request timeout override

**Returns**: `RelayerResponse` with `success`, `hash`, `error`, `errorCode`

#### sendXdr

```kotlin
suspend fun sendXdr(
    transactionXdr: String,
    timeoutMs: Long? = null
): RelayerResponse
```

Submits a fully signed transaction XDR for fee-bumping. Used when the transaction contains source_account auth entries that require the deployer signature.

**Parameters**:
- `transactionXdr`: Base64-encoded signed transaction envelope XDR
- `timeoutMs`: Optional request timeout override

**Returns**: `RelayerResponse` with `success`, `hash`, `error`, `errorCode`

---

## Events

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

**Listening to Events**:

```kotlin
kit.events.on<SmartAccountEvent.WalletConnected> { event ->
    println("Connected to ${event.contractId}")
}

kit.events.on<SmartAccountEvent.TransactionSubmitted> { event ->
    println("Transaction ${event.hash}: ${if (event.success) "success" else "failed"}")
}
```

**Cross-Language Listener (Java/Swift)**:

The `on<T>` method uses Kotlin reified generics and is not callable from Java or Swift. Use `addListener` for cross-language event subscription:

```kotlin
fun addListener(listener: SmartAccountEventListener): () -> Unit
```

Subscribes a listener that receives all event types. The listener must dispatch internally using `when` (Kotlin) or `instanceof` (Java) / pattern matching (Swift). Returns an unsubscribe function.

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

**Error Code Ranges**:
- 1xxx: Configuration errors
- 2xxx: Wallet state errors
- 3xxx: Credential errors
- 4xxx: WebAuthn errors
- 5xxx: Transaction errors
- 6xxx: Signer errors
- 7xxx: Validation errors
- 8xxx: Storage errors
- 9xxx: Session errors

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

Sealed class that specifies which signers should participate in a multi-signature operation. The caller lists every signer explicitly — there is no implicit connected passkey.

```kotlin
sealed class SelectedSigner {
    /** Passkey (WebAuthn) signer. Each instance triggers one OS authentication prompt. */
    data class Passkey(
        val credentialId: String? = null,
        val credentialIdBytes: ByteArray? = null,
        val keyData: ByteArray? = null
    ) : SelectedSigner()

    /** Delegated wallet signer identified by its Stellar G-address. */
    data class Wallet(val address: String) : SelectedSigner()
}
```

- `credentialId`: Base64URL-encoded credential ID for display/logging.
- `credentialIdBytes`: Raw credential ID bytes for the WebAuthn allowCredentials constraint.
- `keyData`: Full key data (secp256r1 public key + credentialId bytes). Required for multi-signer transfers. Populated from the signer data obtained during context rule discovery.

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
        allowCredentialIds: List<ByteArray>? = null
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
    val signature: ByteArray,
    val authenticatorData: ByteArray,
    val clientDataJSON: ByteArray
)
```

---

### ExternalWalletAdapter

Interface for integrating external wallets (Freighter, LOBSTR, etc). Configured via the `externalWallet` field on `OZSmartAccountConfig` (defaults to `null`).

```kotlin
interface ExternalWalletAdapter {
    suspend fun connect(): ConnectedWallet?
    suspend fun disconnect()
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

### WebAuthnSignature

```kotlin
data class WebAuthnSignature(
    val authenticatorData: ByteArray,
    val clientData: ByteArray,
    val signature: ByteArray
)
```

Represents a WebAuthn signature with authenticator and client data.

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
    const val ED25519_PUBLIC_KEY_SIZE = 32         // Size in bytes of an Ed25519 public key (RFC 8032)
    const val SECP256R1_PUBLIC_KEY_SIZE = 65       // Size in bytes of an uncompressed secp256r1 public key
    const val UNCOMPRESSED_PUBKEY_PREFIX: Byte = 0x04  // Uncompressed point prefix byte as defined in SEC 1
}
```

### ContractErrorCodes

Defined in `smartaccount/core/SmartAccountErrors.kt`. Contract-level error codes from the OZ smart account contract. These codes are returned in contract error responses and can be mapped to exceptions when interpreting failed transaction results. Error code range: 3xxx.

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
    const val AUTH_ENTRY_EXPIRATION_BUFFER = 100     // ledgers
    const val DEFAULT_SESSION_EXPIRY_MS = 604_800_000L  // 7 days
    const val DEFAULT_INDEXER_TIMEOUT_MS = 10_000L   // 10 seconds
    const val DEFAULT_RELAYER_TIMEOUT_MS = 360_000L  // 6 minutes
    const val WEBAUTHN_TIMEOUT_MS = 60_000L          // 60 seconds
    const val FRIENDBOT_RESERVE_XLM = 5
    const val DEFAULT_TIMEOUT_SECONDS = 30
    const val MAX_SIGNERS = 15
    const val MAX_POLICIES = 5
    const val MAX_CONTEXT_RULES = 15
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

### Android WebAuthn Provider

```kotlin
// Implemented in androidMain
// Use platform's BiometricPrompt and WebAuthn APIs
```

### iOS/macOS WebAuthn Provider

```kotlin
// Implemented in nativeMain
// Uses Security.framework and local WebAuthn APIs
```

### JavaScript WebAuthn Provider

```kotlin
// Implemented in jsMain
// Uses WebAuthn API (credentials.create, credentials.get)
```

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

---

**Last Updated**: April 2026
