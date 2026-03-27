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
8. [Multi-Signer Operations](#multi-signer-operations)
9. [External Signers](#external-signers)
10. [Events](#events)
11. [Exceptions](#exceptions)
12. [Types](#types)

---

## Quick Start

```kotlin
// Initialize the kit
val config = OZSmartAccountConfig(
    rpcUrl = "https://soroban-testnet.stellar.org",
    networkPassphrase = "Test SDF Network ; September 2015",
    accountWasmHash = "abc123...",
    webauthnVerifierAddress = "CBCD1234...",
    webauthnProvider = platformWebAuthnProvider()  // Platform-specific
)
val kit = OZSmartAccountKit.create(config)

// Create a wallet
val wallet = kit.walletOperations.createWallet(
    userName = "Alice",
    autoSubmit = true,
    autoFund = true,
    nativeTokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
)
println("Wallet: ${wallet.contractId}")

// Transfer tokens
val result = kit.transactionOperations.transfer(
    tokenContract = "CBCD1234...",
    recipient = "GA7Q...",
    amount = 10.0
)
if (result.success) {
    println("Transfer succeeded: ${result.hash}")
}

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

### Lifecycle Methods

#### disconnect
```kotlin
suspend fun disconnect()
```

Disconnects the currently connected wallet, clearing the in-memory connection state and removing the stored session. Stored credentials remain and can be reconnected later.

**Throws**: None (safe to call when not connected)

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
    nativeTokenContract: String? = null
): CreateWalletResult
```

Creates a new smart account wallet with WebAuthn passkey authentication.

**Parameters**:
- `userName`: Display name for the user
- `autoSubmit`: Whether to automatically submit the deploy transaction. When a relayer is configured, the transaction is submitted via the relayer which sponsors fees on behalf of the deployer. Without a relayer, the deployer account must be funded to pay fees directly.
- `autoFund`: Whether to automatically fund the wallet after deployment (testnet only)
- `nativeTokenContract`: Required if `autoFund` is true; the native token contract address

**Returns**: `CreateWalletResult` containing credential ID, contract address, optional transaction hash, and nickname

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
- `credentialIds`: Optional list of allowed credential IDs (currently not filtered)

**Returns**: `AuthenticatePasskeyResult` with credential ID, signature, and public key

**Throws**:
- `WebAuthnException`: Authentication failed or no provider configured
- `ValidationException`: Signature normalization failed

---

### Result Types

#### CreateWalletResult
```kotlin
data class CreateWalletResult(
    val credentialId: String,
    val contractId: String,
    val publicKey: ByteArray,
    val transactionHash: String? = null,
    val nickname: String? = null
)
```

**Fields**:
- `credentialId`: Base64URL-encoded credential ID
- `contractId`: Smart account contract address (C-address)
- `publicKey`: Uncompressed secp256r1 public key (65 bytes)
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

Transfers tokens from the smart account to a recipient. The amount is a decimal string (e.g., "100" or "10.5") converted to stroops internally using BigInteger arithmetic.

**Parameters**:
- `tokenContract`: Token contract address (C-address)
- `recipient`: Recipient address (G-address for accounts, C-address for contracts)
- `amount`: Amount in XLM (converted to stroops automatically)
- `forceMethod`: Optional override to force RELAYER or RPC submission

**Returns**: `TransactionResult` with success status, hash, ledger, and optional error

**Throws**:
- `ValidationException`: Invalid addresses or amount
- `TransactionException`: Simulation, signing, or submission failed
- `WebAuthnException`: Biometric authentication failed

**Example**:

```kotlin
val result = kit.transactionOperations.transfer(
    tokenContract = "CBCD1234...",
    recipient = "GA7QYNF7SOWQ...",
    amount = 100.5
)

if (result.success) {
    println("Hash: ${result.hash}")
    println("Ledger: ${result.ledger}")
} else {
    println("Error: ${result.error}")
}
```

---

#### submit

```kotlin
suspend fun submit(
    hostFunction: HostFunctionXdr,
    auth: List<SorobanAuthorizationEntryXdr>,
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Submits a custom host function with full authorization flow.

Handles simulation, auth entry extraction, WebAuthn signing, re-simulation, and submission.

**Parameters**:
- `hostFunction`: The Soroban host function to execute
- `auth`: Initial authorization entries (typically empty; simulation provides them)
- `forceMethod`: Optional submission method override

**Returns**: `TransactionResult` with submission outcome

**Throws**: Multiple exception types (see transaction operations exceptions)

---

#### fundWallet

```kotlin
suspend fun fundWallet(
    nativeTokenContract: String,
    forceMethod: SubmissionMethod? = null
): Double
```

Funds the smart account wallet using Friendbot (testnet only).

Creates a temporary keypair, funds it via Friendbot, then transfers to the smart account.

**Parameters**:
- `nativeTokenContract`: Native token contract address (C-address)
- `forceMethod`: Optional submission method override

**Returns**: Amount funded in XLM

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
    val deploymentStatus: CredentialDeploymentStatus,
    val isPrimary: Boolean = false,
    val createdAt: Long,
    val lastUsedAt: Long? = null,
    val nickname: String? = null,
    val transports: List<String>? = null,
    val deviceType: String? = null,
    val backedUp: Boolean? = null,
    val deploymentError: String? = null
)

enum class CredentialDeploymentStatus {
    PENDING,
    FAILED
}
```

---

## Signers and Policies

### OZSignerManager

Manages signers for context rules.

```kotlin
val signerMgr = kit.signerManager
```

---

#### addNewPasskeySigner

```kotlin
suspend fun addNewPasskeySigner(
    contextRuleId: UInt,
    userName: String
): AddPasskeySignerResult
```

Registers a new passkey and adds it as a signer to a context rule in one step. Handles the full lifecycle: WebAuthn registration, credential storage, event emission, and on-chain signer addition.

Internally calls the WebAuthn provider's `register()` method, stores the credential, emits a `CredentialCreated` event, and delegates to `addPasskey()` for the on-chain transaction.

**Parameters**:
- `contextRuleId`: Context rule ID (e.g., 0 for Default)
- `userName`: Display name shown during the WebAuthn registration ceremony

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
    credentialId: ByteArray
): TransactionResult
```

Low-level method that adds a pre-registered WebAuthn passkey signer to a context rule. Use this when you handle WebAuthn registration yourself and have the raw cryptographic materials. For most use cases, prefer `addNewPasskeySigner()`.

**Parameters**:
- `contextRuleId`: Context rule ID (e.g., 0 for Default)
- `publicKey`: Uncompressed secp256r1 public key (65 bytes starting with 0x04)
- `credentialId`: WebAuthn credential ID

**Returns**: `TransactionResult` indicating success or failure

**Throws**: `SmartAccountException` if validation or submission fails

---

#### addDelegated

```kotlin
suspend fun addDelegated(
    contextRuleId: UInt,
    address: String
): TransactionResult
```

Adds a delegated signer (account or contract) to a context rule.

**Parameters**:
- `contextRuleId`: Context rule ID
- `address`: Stellar address (G for accounts, C for contracts)

**Returns**: `TransactionResult`

---

#### addEd25519

```kotlin
suspend fun addEd25519(
    contextRuleId: UInt,
    verifierAddress: String,
    publicKey: ByteArray
): TransactionResult
```

Adds an Ed25519 signer to a context rule.

**Parameters**:
- `contextRuleId`: Context rule ID
- `verifierAddress`: Ed25519 verifier contract address (C-address)
- `publicKey`: Ed25519 public key (32 bytes)

**Returns**: `TransactionResult`

---

#### removeSigner

```kotlin
suspend fun removeSigner(
    contextRuleId: UInt,
    signer: SmartAccountSigner
): TransactionResult
```

Removes a signer from a context rule.

**Note**: Cannot remove the last signer unless policies exist.

**Parameters**:
- `contextRuleId`: Context rule ID
- `signer`: The signer to remove

**Returns**: `TransactionResult`

**Throws**: Various SmartAccount exceptions

---

### OZPolicyManager

Manages policies for context rules. Provides both a generic `addPolicy` method for arbitrary policy contracts and convenience methods (`addSimpleThreshold`, `addWeightedThreshold`, `addSpendingLimit`) for common policy types.

```kotlin
val policyMgr = kit.policyManager
```

---

#### addPolicy

```kotlin
suspend fun addPolicy(
    contextRuleId: UInt,
    policyAddress: String,
    installParams: SCValXdr
): TransactionResult
```

Generic method for adding any policy contract to a context rule. The convenience methods (`addSimpleThreshold`, `addWeightedThreshold`, `addSpendingLimit`) delegate to this method.

**Parameters**:

| Parameter | Type | Description |
|---|---|---|
| `contextRuleId` | `UInt` | Context rule ID (e.g., 0 for Default) |
| `policyAddress` | `String` | C-address of the policy contract |
| `installParams` | `SCValXdr` | Policy-specific installation parameters |

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
    threshold: UInt
): TransactionResult
```

Adds a simple threshold policy (M-of-N signers).

**Parameters**:
- `contextRuleId`: Context rule ID
- `policyAddress`: Policy contract address (C-address)
- `threshold`: Number of signers required

**Returns**: `TransactionResult`

---

#### addWeightedThreshold

```kotlin
suspend fun addWeightedThreshold(
    contextRuleId: UInt,
    policyAddress: String,
    signerWeights: Map<SmartAccountSigner, UInt>,
    threshold: UInt
): TransactionResult
```

Adds a weighted threshold policy with configurable signer weights.

**Parameters**:
- `contextRuleId`: Context rule ID
- `policyAddress`: Policy contract address
- `signerWeights`: Map of signers to their weights (vote power)
- `threshold`: Minimum total weight required

**Returns**: `TransactionResult`

---

#### addSpendingLimit

```kotlin
suspend fun addSpendingLimit(
    contextRuleId: UInt,
    policyAddress: String,
    spendingLimit: Double,
    periodLedgers: UInt
): TransactionResult
```

Adds a spending limit policy.

**Parameters**:
- `contextRuleId`: Context rule ID
- `policyAddress`: Policy contract address
- `spendingLimit`: Maximum amount per period in XLM
- `periodLedgers`: Period duration in ledgers (17,280 ≈ 1 day)

**Returns**: `TransactionResult`

**Example**:

```kotlin
// Add 1000 XLM per day limit
val result = kit.policyManager.addSpendingLimit(
    contextRuleId = 0u,
    policyAddress = "CBCD1234...",
    spendingLimit = 1000.0,
    periodLedgers = 17280u
)
```

---

#### removePolicy

```kotlin
suspend fun removePolicy(
    contextRuleId: UInt,
    policyAddress: String
): TransactionResult
```

Removes a policy from a context rule.

**Parameters**:
- `contextRuleId`: Context rule ID
- `policyAddress`: Policy contract address to remove

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

Manages authorization rules that determine which signers and policies apply.

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
    policies: Map<String, SCValXdr> = emptyMap()
): TransactionResult
```

Adds a new context rule to the smart account.

**Parameters**:
- `contextType`: Type of operations this rule applies to
- `name`: Human-readable rule name
- `validUntil`: Optional expiration ledger (null = no expiration)
- `signers`: List of signers (1-15, required)
- `policies`: Map of policy address to installation parameters

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
suspend fun updateName(id: UInt, name: String): TransactionResult
```

Updates the name of a context rule.

---

#### updateValidUntil

```kotlin
suspend fun updateValidUntil(id: UInt, validUntil: UInt?): TransactionResult
```

Updates the expiration ledger of a context rule.

---

#### removeContextRule

```kotlin
suspend fun removeContextRule(id: UInt): TransactionResult
```

Removes a context rule.

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

## Multi-Signer Operations

### OZMultiSignerManager

Manages multi-signature token transfers. The caller is responsible for discovering signers from context rules and passing complete signer data via `SelectedSigner`.

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
    selectedSigners: List<SelectedSigner>
): TransactionResult
```

Executes a multi-signature token transfer. The amount is a decimal string (e.g., "100" or "10.5").

The caller explicitly lists every signer. There is no implicit connected passkey — include `SelectedSigner.Passkey()` if the connected passkey should sign. Signatures are collected in list order: each `Passkey` entry triggers one OS WebAuthn prompt; each `Wallet` entry requests a delegated auth entry from the external wallet.

**Parameters**:
- `tokenContract`: Token contract address (C-address)
- `recipient`: Recipient address (G-address or C-address)
- `amount`: Amount in XLM
- `selectedSigners`: All signers that must sign, in collection order

**Returns**: `TransactionResult`

**Example**:

```kotlin
// Signers are obtained from context rule discovery (client-side)
val result = kit.multiSignerManager.multiSignerTransfer(
    tokenContract = "CBCD...",
    recipient = "GBXYZ...",
    amount = 50.0,
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

    suspend fun authenticate(challenge: ByteArray): WebAuthnAuthenticationResult
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

```kotlin
object SmartAccountConstants {
    const val SECP256R1_PUBLIC_KEY_SIZE = 65
    const val UNCOMPRESSED_PUBKEY_PREFIX: Byte = 0x04
    const val STROOPS_PER_XLM = 10_000_000L
    const val BASE_FEE = 100L
    const val MAX_SIGNERS = 15
    const val MAX_POLICIES = 5
    const val MAX_CONTEXT_RULES = 15
    const val LEDGERS_PER_HOUR = 720
    const val LEDGERS_PER_DAY = 17_280
    const val DEFAULT_SESSION_EXPIRY_MS = 604_800_000L  // 7 days
    const val DEFAULT_TIMEOUT_SECONDS = 30
    const val DEFAULT_INDEXER_TIMEOUT_MS = 10_000L  // 10 seconds
    const val DEFAULT_RELAYER_TIMEOUT_MS = 360_000L  // 6 minutes
    const val WEBAUTHN_TIMEOUT_MS = 60_000L  // 60 seconds
    const val AUTH_ENTRY_EXPIRATION_BUFFER = 100  // ledgers
    const val FRIENDBOT_RESERVE_XLM = 5
    const val FRIENDBOT_URL = "https://friendbot.stellar.org"
    const val MAX_HISTORY_ENTRIES = 1000
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

**Last Updated**: February 2026
