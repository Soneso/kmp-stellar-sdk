# API Reference

Complete API documentation for the Stellar KMP SDK with examples.

## Table of Contents

- [Core Classes](#core-classes)
  - [KeyPair](#keypair)
  - [Account](#account)
  - [Asset](#asset)
  - [Network](#network)
- [Transaction Building](#transaction-building)
  - [TransactionBuilder](#transactionbuilder)
  - [Transaction](#transaction)
  - [FeeBumpTransaction](#feebumptransaction)
- [Operations](#operations)
  - [Payment Operations](#payment-operations)
  - [Account Operations](#account-operations)
  - [Asset Operations](#asset-operations)
  - [Trading Operations](#trading-operations)
  - [Claimable Balance Operations](#claimable-balance-operations)
  - [Sponsorship Operations](#sponsorship-operations)
  - [Liquidity Pool Operations](#liquidity-pool-operations)
  - [Contract Operations](#contract-operations)
- [Signing & Authorization](#signing--authorization)
  - [Signing Transactions](#signing-transactions)
  - [Auth (Soroban Authorization)](#auth)
  - [Multi-signature](#multi-signature)
- [Horizon Server](#horizon-server)
  - [Account Queries](#account-queries)
  - [Transaction Queries](#transaction-queries)
  - [Asset Queries](#asset-queries)
  - [Order Book Queries](#order-book-queries)
  - [Path Finding](#path-finding)
  - [Server-Sent Events](#server-sent-events)
- [Soroban (Smart Contracts)](#soroban-smart-contracts)
  - [SorobanServer](#sorobanserver)
  - [ContractClient](#contractclient)
  - [AssembledTransaction](#assembledtransaction)
  - [Contract Values (Scv)](#contract-values-scv)
- [Utilities](#utilities)
  - [StrKey](#strkey)
  - [Memo](#memo)
  - [FriendBot](#friendbot)

## Core Classes

### KeyPair

Manages Ed25519 keypairs for signing and verification.

```kotlin
package com.soneso.stellar.sdk

class KeyPair {
    companion object {
        // Generate a random keypair
        suspend fun random(): KeyPair

        // Create from secret seed (S...)
        suspend fun fromSecretSeed(seed: String): KeyPair
        suspend fun fromSecretSeed(seed: CharArray): KeyPair  // More secure
        suspend fun fromSecretSeed(seed: ByteArray): KeyPair

        // Create from public key only (G...)
        fun fromAccountId(accountId: String): KeyPair
        fun fromPublicKey(publicKey: ByteArray): KeyPair

        // Get crypto library name
        fun getCryptoLibraryName(): String
    }

    // Check if can sign (has private key)
    fun canSign(): Boolean

    // Get account ID (G...)
    fun getAccountId(): String

    // Get secret seed (S...) - returns null if public-only
    fun getSecretSeed(): CharArray?

    // Get raw public key bytes
    fun getPublicKey(): ByteArray

    // Sign data
    suspend fun sign(data: ByteArray): ByteArray

    // Sign with decorated signature (includes hint)
    suspend fun signDecorated(data: ByteArray): DecoratedSignature

    // Verify signature
    suspend fun verify(data: ByteArray, signature: ByteArray): Boolean

    // Get XDR representation
    fun getXdrAccountId(): AccountIDXdr
}
```

**Examples:**

```kotlin
// Generate random keypair
val keypair = KeyPair.random()
println("Account: ${keypair.getAccountId()}")
println("Secret: ${keypair.getSecretSeed()?.concatToString()}")

// Import from seed
val imported = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")

// Create public-only keypair
val publicOnly = KeyPair.fromAccountId("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D")
assert(!publicOnly.canSign())

// Sign and verify
val message = "Hello Stellar".encodeToByteArray()
val signature = keypair.sign(message)
val isValid = keypair.verify(message, signature)
assert(isValid)
```

### Account

Represents a Stellar account with sequence number management.

```kotlin
package com.soneso.stellar.sdk

class Account(
    val accountId: String,
    private var sequenceNumber: Long
) : TransactionBuilderAccount {
    // Get current sequence number
    override fun getSequenceNumber(): Long

    // Increment sequence number
    override fun incrementSequenceNumber()

    // Get next sequence number without incrementing
    fun getIncrementedSequenceNumber(): Long

    // Set sequence number
    fun setSequenceNumber(sequenceNumber: Long)
}
```

**Examples:**

```kotlin
// Create account for transaction building
val account = Account("GABC...", 123456789L)

// Build transaction (auto-increments sequence)
val transaction = TransactionBuilder(account, Network.TESTNET)
    .addOperation(/* ... */)
    .build()

// Sequence is now 123456790
println(account.getSequenceNumber())
```

### Asset

Represents Stellar assets (native XLM or issued assets).

```kotlin
package com.soneso.stellar.sdk

sealed class Asset {
    companion object {
        // Native asset (XLM)
        val NATIVE: AssetTypeNative

        // Create non-native asset
        fun createNonNativeAsset(code: String, issuer: String): Asset

        // Parse from canonical string
        fun fromCanonical(canonical: String): Asset

        // Create from XDR
        fun fromXdr(xdr: AssetXdr): Asset
    }

    // Get asset type
    abstract fun getType(): String

    // Get canonical representation
    abstract fun getCanonical(): String

    // Check equality
    abstract fun equals(other: Any?): Boolean

    // Convert to XDR
    abstract fun toXdr(): AssetXdr

    // Get contract ID (for Stellar Asset Contracts)
    abstract fun getContractId(network: Network): String
}

// Specific asset types
class AssetTypeNative : Asset()
class AssetTypeCreditAlphaNum4(val code: String, val issuer: String) : Asset()
class AssetTypeCreditAlphaNum12(val code: String, val issuer: String) : Asset()
```

**Examples:**

```kotlin
// Native asset (XLM)
val xlm = Asset.NATIVE

// Create USDC asset
val usdc = Asset.createNonNativeAsset(
    "USDC",
    "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
)

// Parse from canonical string
val asset = Asset.fromCanonical("USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")

// Get canonical representation
println(usdc.getCanonical())  // "USDC:GA5Z..."

// Get contract ID for Stellar Asset Contract
val contractId = usdc.getContractId(Network.TESTNET)
```

### Network

Represents a Stellar network with its passphrase.

```kotlin
package com.soneso.stellar.sdk

class Network(val networkPassphrase: String) {
    companion object {
        // Predefined networks
        val PUBLIC: Network  // Mainnet
        val TESTNET: Network
        val FUTURENET: Network
        val STANDALONE: Network

        // Create custom network
        fun custom(passphrase: String): Network
    }

    // Get network ID (hash of passphrase)
    fun getNetworkId(): ByteArray
}
```

**Examples:**

```kotlin
// Use predefined networks
val mainnet = Network.PUBLIC
val testnet = Network.TESTNET

// Create custom network
val custom = Network.custom("My Private Network; June 2024")

// Get network ID for signing
val networkId = testnet.getNetworkId()
```

## Transaction Building

### TransactionBuilder

Builds Stellar transactions with a fluent API.

```kotlin
package com.soneso.stellar.sdk

class TransactionBuilder(
    val sourceAccount: TransactionBuilderAccount,
    val network: Network
) {
    // Add operation
    fun addOperation(operation: Operation): TransactionBuilder

    // Add multiple operations
    fun addOperations(operations: List<Operation>): TransactionBuilder

    // Set memo
    fun addMemo(memo: Memo): TransactionBuilder

    // Set base fee per operation (in stroops)
    fun setBaseFee(baseFee: Long): TransactionBuilder

    // Set timeout (in seconds)
    fun setTimeout(timeout: Long): TransactionBuilder

    // Set absolute time bounds
    fun setTimeBounds(timeBounds: TimeBounds): TransactionBuilder

    // Add preconditions
    fun addPreconditions(preconditions: TransactionPreconditions): TransactionBuilder

    // Build the transaction
    fun build(): Transaction
}
```

**Examples:**

```kotlin
// Simple payment transaction
val transaction = TransactionBuilder(account, Network.TESTNET)
    .addOperation(
        PaymentOperation(
            destination = "GDEF...",
            amount = "100.50",
            asset = Asset.NATIVE
        )
    )
    .addMemo(Memo.text("Payment for services"))
    .setBaseFee(100)  // 100 stroops
    .setTimeout(300)   // 5 minutes
    .build()

// Complex multi-operation transaction
val complexTx = TransactionBuilder(account, Network.TESTNET)
    .addOperations(listOf(
        CreateAccountOperation("GNEW...", "10"),
        PaymentOperation("GNEW...", "50", Asset.NATIVE),
        SetOptionsOperation()
            .setHomeDomain("example.com")
            .setMasterKeyWeight(10)
    ))
    .addPreconditions(
        TransactionPreconditions()
            .setMinSeqNum(123456)
            .setMinSeqAge(60)  // 60 seconds
    )
    .build()
```

### Transaction

Represents a built transaction ready for signing and submission.

```kotlin
package com.soneso.stellar.sdk

class Transaction : AbstractTransaction {
    // Get operations
    val operations: List<Operation>

    // Get memo
    val memo: Memo

    // Get time bounds
    val timeBounds: TimeBounds?

    // Get fee (total for all operations)
    val fee: Long

    // Sign the transaction
    suspend fun sign(signer: KeyPair)
    suspend fun sign(preimage: ByteArray)  // For hash(x) signers

    // Get transaction hash
    fun hash(): ByteArray

    // Get signing payload
    fun signatureBase(): ByteArray

    // Convert to envelope XDR
    fun toEnvelopeXdr(): TransactionEnvelopeXdr

    // Get envelope XDR string (base64)
    fun toEnvelopeXdrBase64(): String

    // Create from XDR
    companion object {
        fun fromEnvelopeXdr(envelope: TransactionEnvelopeXdr): Transaction
        fun fromEnvelopeXdr(base64: String): Transaction
    }
}
```

**Examples:**

```kotlin
// Sign transaction
transaction.sign(keypair)

// Sign with multiple signers
transaction.sign(signer1)
transaction.sign(signer2)

// Get transaction hash
val hash = transaction.hash()
println("TX Hash: ${hash.toHexString()}")

// Export as XDR for submission
val xdr = transaction.toEnvelopeXdrBase64()

// Import from XDR
val imported = Transaction.fromEnvelopeXdr(xdr)
```

### FeeBumpTransaction

Wraps a transaction with a higher fee for priority processing.

```kotlin
package com.soneso.stellar.sdk

class FeeBumpTransaction : AbstractTransaction {
    // Get inner transaction
    val innerTransaction: AbstractTransaction

    // Get fee account
    val feeAccount: String

    // Get total fee
    val fee: Long

    // Sign the fee bump
    suspend fun sign(signer: KeyPair)

    // Create using builder
    companion object {
        fun createWithBaseFee(
            feeAccount: String,
            baseFee: Long,
            innerTransaction: AbstractTransaction
        ): FeeBumpTransaction

        fun createWithFee(
            feeAccount: String,
            fee: Long,
            innerTransaction: AbstractTransaction
        ): FeeBumpTransaction
    }
}
```

**Examples:**

```kotlin
// Create fee bump transaction
val feeBump = FeeBumpTransaction.createWithBaseFee(
    feeAccount = "GFEE...",
    baseFee = 1000,  // Higher fee
    innerTransaction = originalTransaction
)

// Sign with fee account
feeBump.sign(feeAccountKeypair)

// Submit fee bump transaction
server.submitTransaction(feeBump)
```

## Operations

### Payment Operations

#### PaymentOperation

Send payments of any asset.

```kotlin
class PaymentOperation(
    val destination: String,      // Recipient account
    val amount: String,           // Amount to send
    val asset: Asset,            // Asset to send
    val sourceAccount: String? = null  // Optional source
)
```

**Example:**
```kotlin
// Send 100 XLM
PaymentOperation(
    destination = "GDEF...",
    amount = "100",
    asset = Asset.NATIVE
)

// Send 50 USDC
PaymentOperation(
    destination = "GDEF...",
    amount = "50",
    asset = Asset.createNonNativeAsset("USDC", "GISS...")
)
```

#### PathPaymentStrictSendOperation

Send exact amount, receive at least minimum.

```kotlin
class PathPaymentStrictSendOperation(
    val sendAsset: Asset,
    val sendAmount: String,
    val destination: String,
    val destAsset: Asset,
    val destMin: String,
    val path: List<Asset>,
    val sourceAccount: String? = null
)
```

#### PathPaymentStrictReceiveOperation

Receive exact amount, send at most maximum.

```kotlin
class PathPaymentStrictReceiveOperation(
    val sendAsset: Asset,
    val sendMax: String,
    val destination: String,
    val destAsset: Asset,
    val destAmount: String,
    val path: List<Asset>,
    val sourceAccount: String? = null
)
```

### Account Operations

#### CreateAccountOperation

Create and fund a new account.

```kotlin
class CreateAccountOperation(
    val destination: String,      // New account ID
    val startingBalance: String,  // Initial XLM balance
    val sourceAccount: String? = null
)
```

**Example:**
```kotlin
CreateAccountOperation(
    destination = "GNEW...",
    startingBalance = "10"  // 10 XLM minimum
)
```

#### AccountMergeOperation

Merge account into another, transferring all XLM.

```kotlin
class AccountMergeOperation(
    val destination: String,
    val sourceAccount: String? = null
)
```

#### SetOptionsOperation

Configure account settings.

```kotlin
class SetOptionsOperation(
    val sourceAccount: String? = null
) {
    fun setInflationDestination(inflationDestination: String)
    fun setClearFlags(clearFlags: Int)
    fun setSetFlags(setFlags: Int)
    fun setMasterKeyWeight(masterKeyWeight: Int)
    fun setLowThreshold(lowThreshold: Int)
    fun setMediumThreshold(mediumThreshold: Int)
    fun setHighThreshold(highThreshold: Int)
    fun setHomeDomain(homeDomain: String)
    fun setSigner(signer: SignerKey, weight: Int)
}
```

**Example:**
```kotlin
SetOptionsOperation()
    .setHomeDomain("stellar.example.com")
    .setMasterKeyWeight(20)
    .setLowThreshold(5)
    .setMediumThreshold(10)
    .setHighThreshold(15)
    .setSigner(
        SignerKey.ed25519PublicKey("GSIGNER..."),
        10  // Weight
    )
```

### Asset Operations

#### ChangeTrustOperation

Create or modify a trustline.

```kotlin
class ChangeTrustOperation(
    val asset: ChangeTrustAsset,
    val limit: String? = null,  // null = maximum
    val sourceAccount: String? = null
)
```

**Example:**
```kotlin
// Add USDC trustline
ChangeTrustOperation(
    asset = ChangeTrustAsset.create(
        Asset.createNonNativeAsset("USDC", "GISS...")
    ),
    limit = "10000"  // Maximum 10,000 USDC
)

// Remove trustline (set limit to 0)
ChangeTrustOperation(
    asset = ChangeTrustAsset.create(usdcAsset),
    limit = "0"
)
```

#### AllowTrustOperation (Deprecated)

Allow/revoke trustline authorization.

```kotlin
class AllowTrustOperation(
    val trustor: String,
    val assetCode: String,
    val authorize: Boolean,
    val sourceAccount: String? = null
)
```

#### SetTrustLineFlagsOperation

Set trustline authorization flags.

```kotlin
class SetTrustLineFlagsOperation(
    val trustor: String,
    val asset: Asset,
    val clearFlags: Set<TrustLineFlag>,
    val setFlags: Set<TrustLineFlag>,
    val sourceAccount: String? = null
)

enum class TrustLineFlag {
    AUTHORIZED,
    AUTHORIZED_TO_MAINTAIN_LIABILITIES,
    CLAWBACK_ENABLED
}
```

### Trading Operations

#### ManageSellOfferOperation

Create or update a sell offer.

```kotlin
class ManageSellOfferOperation(
    val selling: Asset,
    val buying: Asset,
    val amount: String,
    val price: Price,
    val offerId: Long = 0,  // 0 = create new
    val sourceAccount: String? = null
)
```

**Example:**
```kotlin
// Sell 100 XLM for USDC at 0.20 USDC/XLM
ManageSellOfferOperation(
    selling = Asset.NATIVE,
    buying = usdcAsset,
    amount = "100",
    price = Price.fromString("0.20")
)
```

#### ManageBuyOfferOperation

Create or update a buy offer.

```kotlin
class ManageBuyOfferOperation(
    val selling: Asset,
    val buying: Asset,
    val buyAmount: String,
    val price: Price,
    val offerId: Long = 0,
    val sourceAccount: String? = null
)
```

#### CreatePassiveSellOfferOperation

Create a passive sell offer.

```kotlin
class CreatePassiveSellOfferOperation(
    val selling: Asset,
    val buying: Asset,
    val amount: String,
    val price: Price,
    val sourceAccount: String? = null
)
```

### Claimable Balance Operations

#### CreateClaimableBalanceOperation

Create a claimable balance.

```kotlin
class CreateClaimableBalanceOperation(
    val amount: String,
    val asset: Asset,
    val claimants: List<Claimant>,
    val sourceAccount: String? = null
)
```

**Example:**
```kotlin
CreateClaimableBalanceOperation(
    amount = "100",
    asset = Asset.NATIVE,
    claimants = listOf(
        Claimant(
            destination = "GCLAIM...",
            predicate = Predicate.unconditional()
        ),
        Claimant(
            destination = "GCLAIM2...",
            predicate = Predicate.relativeTime(3600)  // 1 hour
        )
    )
)
```

#### ClaimClaimableBalanceOperation

Claim a claimable balance.

```kotlin
class ClaimClaimableBalanceOperation(
    val balanceId: String,
    val sourceAccount: String? = null
)
```

### Sponsorship Operations

#### BeginSponsoringFutureReservesOperation

Begin sponsoring reserves.

```kotlin
class BeginSponsoringFutureReservesOperation(
    val sponsoredId: String,
    val sourceAccount: String? = null
)
```

#### EndSponsoringFutureReservesOperation

End sponsoring reserves.

```kotlin
class EndSponsoringFutureReservesOperation(
    val sourceAccount: String? = null
)
```

#### RevokeAccountSponsorshipOperation

Revoke account sponsorship.

```kotlin
class RevokeAccountSponsorshipOperation(
    val accountId: String,
    val sourceAccount: String? = null
)
```

### Liquidity Pool Operations

#### LiquidityPoolDepositOperation

Add liquidity to a pool.

```kotlin
class LiquidityPoolDepositOperation(
    val liquidityPoolId: String,
    val maxAmountA: String,
    val maxAmountB: String,
    val minPrice: Price,
    val maxPrice: Price,
    val sourceAccount: String? = null
)
```

#### LiquidityPoolWithdrawOperation

Remove liquidity from a pool.

```kotlin
class LiquidityPoolWithdrawOperation(
    val liquidityPoolId: String,
    val amount: String,  // Pool shares
    val minAmountA: String,
    val minAmountB: String,
    val sourceAccount: String? = null
)
```

### Contract Operations

#### InvokeHostFunctionOperation

Invoke a Soroban smart contract function.

```kotlin
class InvokeHostFunctionOperation {
    companion object {
        // Invoke contract function
        fun invokeContractFunction(
            contractAddress: String,
            functionName: String,
            parameters: List<SCValXdr>
        ): InvokeHostFunctionOperation

        // Upload contract WASM
        fun uploadContractWasm(
            wasm: ByteArray
        ): InvokeHostFunctionOperation

        // Create contract from WASM
        fun createContract(
            wasmId: ByteArray,
            address: SCAddressXdr
        ): InvokeHostFunctionOperation
    }
}
```

**Example:**
```kotlin
// Call contract function
InvokeHostFunctionOperation.invokeContractFunction(
    contractAddress = "CCREATE...",
    functionName = "transfer",
    parameters = listOf(
        Scv.toAddress("GFROM..."),
        Scv.toAddress("GTO..."),
        Scv.toInt128(1000)
    )
)
```

#### ExtendFootprintTTLOperation

Extend contract storage lifetime.

```kotlin
class ExtendFootprintTTLOperation(
    val extendTo: Long,  // Ledgers to extend
    val sourceAccount: String? = null
)
```

#### RestoreFootprintOperation

Restore archived contract data.

```kotlin
class RestoreFootprintOperation(
    val sourceAccount: String? = null
)
```

## Signing & Authorization

### Signing Transactions

Multiple ways to sign transactions:

```kotlin
// Sign with KeyPair
suspend fun signWithKeypair() {
    val transaction = buildTransaction()

    // Single signer
    transaction.sign(keypair)

    // Multiple signers
    transaction.sign(signer1)
    transaction.sign(signer2)
    transaction.sign(signer3)
}

// Sign with preimage (hash(x) signer)
suspend fun signWithPreimage() {
    val preimage = "mySecret".encodeToByteArray()
    transaction.sign(preimage)
}

// Custom signer interface
interface Signer {
    suspend fun signDecorated(data: ByteArray): DecoratedSignature
}

// Hardware wallet example
class LedgerSigner : Signer {
    override suspend fun signDecorated(data: ByteArray): DecoratedSignature {
        // Communicate with Ledger device
        val signature = ledgerDevice.sign(data)
        return DecoratedSignature(hint, signature)
    }
}
```

### Auth

Soroban authorization for smart contract calls.

```kotlin
package com.soneso.stellar.sdk

object Auth {
    // Sign authorization entries
    suspend fun authorizeEntries(
        entries: List<SorobanAuthorizationEntryXdr>,
        signer: Keypair,
        validUntilLedgerSeq: Long,
        network: Network
    ): List<SorobanAuthorizationEntryXdr>

    // Sign with custom Signer
    suspend fun authorizeEntries(
        entries: List<SorobanAuthorizationEntryXdr>,
        signer: Signer,
        validUntilLedgerSeq: Long,
        network: Network
    ): List<SorobanAuthorizationEntryXdr>

    // Build authorization entry from scratch
    suspend fun authorizeEntry(
        entryPreimageXdr: SorobanAuthorizedInvocationXdr,
        signer: KeyPair,
        validUntilLedgerSeq: Long,
        network: Network
    ): SorobanAuthorizationEntryXdr

    // Verify authorization signature
    suspend fun verifyAuthorization(
        entry: SorobanAuthorizationEntryXdr,
        publicKey: ByteArray,
        network: Network
    ): Boolean
}
```

**Example:**
```kotlin
// Sign contract authorization
val signedEntries = Auth.authorizeEntries(
    entries = simulationResult.authEntries,
    signer = userKeypair,
    validUntilLedgerSeq = currentLedger + 100,
    network = Network.TESTNET
)

// Build custom authorization
val customAuth = Auth.authorizeEntry(
    entryPreimageXdr = invocation,
    signer = keypair,
    validUntilLedgerSeq = validUntil,
    network = Network.TESTNET
)
```

### Multi-signature

Configure and use multi-signature accounts:

```kotlin
// Setup 2-of-3 multisig
suspend fun setupMultisig() {
    val account = server.loadAccount(masterKey.getAccountId())

    val transaction = TransactionBuilder(account, Network.TESTNET)
        .addOperation(
            SetOptionsOperation()
                // Add signers
                .setSigner(SignerKey.ed25519PublicKey(signer1.getAccountId()), 1)
                .setSigner(SignerKey.ed25519PublicKey(signer2.getAccountId()), 1)
                .setSigner(SignerKey.ed25519PublicKey(signer3.getAccountId()), 1)
                // Set thresholds (2 signatures required)
                .setLowThreshold(2)
                .setMediumThreshold(2)
                .setHighThreshold(2)
                // Reduce master key weight
                .setMasterKeyWeight(1)
        )
        .build()

    transaction.sign(masterKey)
    server.submitTransaction(transaction)
}

// Sign transaction with multiple signers
suspend fun multisigTransaction() {
    val transaction = buildTransaction()

    // Need 2 of 3 signatures
    transaction.sign(signer1)
    transaction.sign(signer3)

    // Submit with 2 signatures
    server.submitTransaction(transaction)
}
```

## Horizon Server

### HorizonServer

Main client for Horizon API interaction.

```kotlin
package com.soneso.stellar.sdk.horizon

class HorizonServer(
    val serverUrl: String,
    val httpClient: HttpClient = HttpClient()
) : Closeable {
    // Account operations
    suspend fun loadAccount(accountId: String): AccountResponse
    fun getAccounts(): AccountsRequestBuilder

    // Transaction operations
    suspend fun submitTransaction(transaction: AbstractTransaction): SubmitTransactionResponse
    suspend fun submitTransactionAsync(transaction: AbstractTransaction): SubmitTransactionAsyncResponse
    fun getTransactions(): TransactionsRequestBuilder

    // Asset operations
    fun getAssets(): AssetsRequestBuilder

    // Ledger operations
    fun getLedgers(): LedgersRequestBuilder

    // Effects
    fun getEffects(): EffectsRequestBuilder

    // Operations
    fun getOperations(): OperationsRequestBuilder

    // Payments
    fun getPayments(): PaymentsRequestBuilder

    // Offers
    fun getOffers(): OffersRequestBuilder

    // Order book
    suspend fun getOrderBook(
        selling: Asset,
        buying: Asset,
        limit: Int? = null
    ): OrderBookResponse

    // Path finding
    suspend fun findStrictSendPaths(
        sourceAsset: Asset,
        sourceAmount: String,
        destination: String? = null
    ): PathsResponse

    suspend fun findStrictReceivePaths(
        destinationAsset: Asset,
        destinationAmount: String,
        source: String? = null
    ): PathsResponse

    // Fee stats
    suspend fun getFeeStats(): FeeStatsResponse

    // Server info
    suspend fun getRoot(): RootResponse

    // Close connection
    override fun close()
}
```

### Account Queries

```kotlin
// Load single account
val account = server.loadAccount("GABC...")
println("Sequence: ${account.sequence}")
println("Balances: ${account.balances}")

// Query multiple accounts
val accounts = server.getAccounts()
    .forSponsor("GSPONSOR...")
    .forAsset(Asset.createNonNativeAsset("USDC", "GISS..."))
    .cursor("12345")
    .limit(50)
    .order(Order.DESC)
    .execute()

accounts.records.forEach { account ->
    println("Account: ${account.accountId}")
}
```

### Transaction Queries

```kotlin
// Submit transaction
val response = server.submitTransaction(signedTransaction)
if (response.isSuccess) {
    println("TX Hash: ${response.hash}")
} else {
    println("Failed: ${response.extras?.resultCodes}")
}

// Query transactions
val transactions = server.getTransactions()
    .forAccount("GABC...")
    .includeFailed(true)
    .limit(20)
    .execute()

// Get specific transaction
val tx = server.getTransaction("abc123...")
```

### Asset Queries

```kotlin
// Find assets
val assets = server.getAssets()
    .forCode("USDC")
    .forIssuer("GISS...")
    .limit(10)
    .execute()

// Get asset stats
assets.records.forEach { asset ->
    println("Asset: ${asset.code}")
    println("Accounts: ${asset.numAccounts}")
    println("Amount: ${asset.amount}")
}
```

### Order Book Queries

```kotlin
// Get order book
val orderBook = server.getOrderBook(
    selling = Asset.NATIVE,
    buying = Asset.createNonNativeAsset("USDC", "GISS..."),
    limit = 20
)

println("Bids:")
orderBook.bids.forEach { bid ->
    println("  ${bid.amount} @ ${bid.price}")
}

println("Asks:")
orderBook.asks.forEach { ask ->
    println("  ${ask.amount} @ ${ask.price}")
}
```

### Path Finding

```kotlin
// Find payment paths (strict send)
val paths = server.findStrictSendPaths(
    sourceAsset = Asset.NATIVE,
    sourceAmount = "100",
    destination = "GDEST..."
)

paths.records.forEach { path ->
    println("Path: ${path.path.map { it.code }}")
    println("Destination amount: ${path.destinationAmount}")
}

// Find payment paths (strict receive)
val receivePaths = server.findStrictReceivePaths(
    destinationAsset = usdcAsset,
    destinationAmount = "50",
    source = "GSOURCE..."
)
```

### Server-Sent Events

Stream real-time updates:

```kotlin
// Stream transactions
server.getTransactions()
    .forAccount("GABC...")
    .stream(object : EventListener<TransactionResponse> {
        override fun onEvent(data: TransactionResponse) {
            println("New transaction: ${data.hash}")
        }

        override fun onError(error: Exception) {
            println("Stream error: $error")
        }
    })

// Stream payments
server.getPayments()
    .forAccount("GABC...")
    .stream(object : EventListener<OperationResponse> {
        override fun onEvent(data: OperationResponse) {
            when (data) {
                is PaymentOperationResponse -> {
                    println("Payment: ${data.amount} ${data.asset.code}")
                }
            }
        }
    })

// Stream order book
server.getOrderBookStream(
    selling = Asset.NATIVE,
    buying = usdcAsset,
    listener = object : EventListener<OrderBookResponse> {
        override fun onEvent(data: OrderBookResponse) {
            println("Order book updated")
        }
    }
)
```

## Soroban (Smart Contracts)

### SorobanServer

RPC client for Soroban smart contracts.

```kotlin
package com.soneso.stellar.sdk.rpc

class SorobanServer(
    val serverUrl: String,
    val httpClient: HttpClient = HttpClient()
) : Closeable {
    // Network info
    suspend fun getHealth(): GetHealthResponse
    suspend fun getNetwork(): GetNetworkResponse
    suspend fun getLatestLedger(): GetLatestLedgerResponse

    // Transaction operations
    suspend fun simulateTransaction(
        transaction: Transaction,
        addlResources: SimulateTransactionRequest.AddlResources? = null
    ): SimulateTransactionResponse

    suspend fun sendTransaction(
        transaction: AbstractTransaction
    ): SendTransactionResponse

    suspend fun getTransaction(
        hash: String
    ): GetTransactionResponse

    // Contract data
    suspend fun getContractData(
        contractId: String,
        key: SCValXdr,
        durability: ContractDataDurability
    ): GetLedgerEntriesResponse.LedgerEntryResult?

    // Events
    suspend fun getEvents(
        request: GetEventsRequest
    ): GetEventsResponse

    // Ledger entries
    suspend fun getLedgerEntries(
        keys: List<LedgerKeyXdr>
    ): GetLedgerEntriesResponse

    override fun close()
}
```

**Examples:**

```kotlin
// Check server health
val health = sorobanServer.getHealth()
println("Status: ${health.status}")

// Simulate transaction
val simulation = sorobanServer.simulateTransaction(transaction)
if (simulation.isSuccess) {
    // Apply simulation results
    transaction.setSorobanData(simulation.transactionData)
    transaction.addResourceFees(simulation.minResourceFee)
}

// Get contract data
val balanceKey = Scv.toSymbol("balance")
val data = sorobanServer.getContractData(
    contractId = "CCREATE...",
    key = balanceKey,
    durability = ContractDataDurability.PERSISTENT
)

// Query events
val events = sorobanServer.getEvents(
    GetEventsRequest(
        startLedger = 1000,
        filters = listOf(
            EventFilter(
                contractIds = setOf("CCREATE..."),
                topics = listOf(listOf(Scv.toSymbol("transfer")))
            )
        )
    )
)
```

### ContractClient

High-level client for smart contract interaction.

```kotlin
package com.soneso.stellar.sdk.contract

class ContractClient(
    val contractId: String,
    rpcUrl: String,
    val network: Network
) {
    val server: SorobanServer

    // Invoke contract function
    suspend fun <T> invoke(
        functionName: String,
        parameters: List<SCValXdr>,
        source: String,
        signer: KeyPair?,
        parseResultXdrFn: ((SCValXdr) -> T)? = null,
        baseFee: Int = 100,
        transactionTimeout: Long = 300,
        submitTimeout: Int = 30,
        simulate: Boolean = true,
        restore: Boolean = true
    ): AssembledTransaction<T>

    // Close connection
    fun close()
}
```

**Examples:**

```kotlin
val client = ContractClient(
    contractId = "CCREATE...",
    rpcUrl = "https://soroban-testnet.stellar.org",
    network = Network.TESTNET
)

// Read-only call (no signing)
val balance = client.invoke<Long>(
    functionName = "balance",
    parameters = listOf(Scv.toAddress("GABC...")),
    source = "GABC...",
    signer = null,
    parseResultXdrFn = { scval ->
        Scv.fromInt128(scval).toLong()
    }
).result()

println("Balance: $balance")

// Write operation (requires signing)
val transferResult = client.invoke<Unit>(
    functionName = "transfer",
    parameters = listOf(
        Scv.toAddress("GFROM..."),
        Scv.toAddress("GTO..."),
        Scv.toInt128(1000)
    ),
    source = sourceAccount.getAccountId(),
    signer = sourceAccount,
    parseResultXdrFn = null  // Void return
)
    .signAuthEntries(sourceAccount)
    .signAndSubmit(sourceAccount)

println("Transfer complete: ${transferResult.result()}")

client.close()
```

### AssembledTransaction

Manages smart contract transaction lifecycle.

```kotlin
package com.soneso.stellar.sdk.contract

class AssembledTransaction<T>(
    val server: SorobanServer,
    val submitTimeout: Int,
    val transactionSigner: KeyPair?,
    val parseResultXdrFn: ((SCValXdr) -> T)?,
    val transactionBuilder: TransactionBuilder
) {
    // Current transaction state
    var transaction: Transaction?
    var simulationResult: SimulateTransactionResponse?
    var submitResult: SendTransactionResponse?

    // Simulate the transaction
    suspend fun simulate(restore: Boolean = true): AssembledTransaction<T>

    // Check if read-only (no state changes)
    fun isReadOnly(): Boolean

    // Sign authorization entries
    suspend fun signAuthEntries(signer: KeyPair): AssembledTransaction<T>
    suspend fun signAuthEntries(signer: Signer): AssembledTransaction<T>

    // Build transaction (after simulation)
    fun build(): Transaction

    // Sign transaction
    suspend fun sign(signer: KeyPair): AssembledTransaction<T>

    // Submit to network
    suspend fun send(): SendTransactionResponse

    // Sign and submit in one call
    suspend fun signAndSubmit(signer: KeyPair): AssembledTransaction<T>

    // Get result (for read-only calls)
    fun result(): T?

    // Poll for transaction status
    suspend fun pollStatus(
        maxAttempts: Int = 10,
        delayMillis: Long = 1000
    ): GetTransactionResponse

    // Complete flow: sign, submit, and poll
    suspend fun signAndSubmitSync(signer: KeyPair): T?
}
```

**Lifecycle Example:**

```kotlin
// Full transaction lifecycle
val assembled = client.invoke<String>(
    functionName = "getName",
    parameters = listOf(Scv.toU32(123)),
    source = account.getAccountId(),
    signer = account,
    parseResultXdrFn = { Scv.fromString(it) }
)

// 1. Simulate
assembled.simulate(restore = true)

// 2. Check if needs auth
if (!assembled.isReadOnly()) {
    // 3. Sign auth entries if needed
    assembled.signAuthEntries(account)

    // 4. Build transaction
    val tx = assembled.build()

    // 5. Sign transaction
    assembled.sign(account)

    // 6. Submit
    val submitResponse = assembled.send()

    // 7. Poll for result
    val finalStatus = assembled.pollStatus()

    // 8. Get parsed result
    val result = assembled.result()
    println("Result: $result")
}

// Or use convenience method for complete flow
val quickResult = assembled.signAndSubmitSync(account)
```

### Contract Values (Scv)

Helper for creating Soroban contract values.

```kotlin
package com.soneso.stellar.sdk.scval

object Scv {
    // Primitive types
    fun toVoid(): SCValXdr
    fun toBool(value: Boolean): SCValXdr
    fun toU32(value: UInt): SCValXdr
    fun toI32(value: Int): SCValXdr
    fun toU64(value: ULong): SCValXdr
    fun toI64(value: Long): SCValXdr
    fun toU128(value: BigInteger): SCValXdr
    fun toI128(value: BigInteger): SCValXdr
    fun toU256(value: BigInteger): SCValXdr
    fun toI256(value: BigInteger): SCValXdr

    // Complex types
    fun toBytes(value: ByteArray): SCValXdr
    fun toString(value: String): SCValXdr
    fun toSymbol(value: String): SCValXdr
    fun toAddress(accountId: String): SCValXdr
    fun toContractAddress(contractId: String): SCValXdr

    // Collections
    fun toVec(values: List<SCValXdr>): SCValXdr
    fun toMap(map: Map<SCValXdr, SCValXdr>): SCValXdr

    // Decoding
    fun fromBool(scval: SCValXdr): Boolean
    fun fromU32(scval: SCValXdr): UInt
    fun fromI32(scval: SCValXdr): Int
    fun fromU64(scval: SCValXdr): ULong
    fun fromI64(scval: SCValXdr): Long
    fun fromU128(scval: SCValXdr): BigInteger
    fun fromI128(scval: SCValXdr): BigInteger
    fun fromBytes(scval: SCValXdr): ByteArray
    fun fromString(scval: SCValXdr): String
    fun fromSymbol(scval: SCValXdr): String
    fun fromAddress(scval: SCValXdr): String
    fun fromVec(scval: SCValXdr): List<SCValXdr>
    fun fromMap(scval: SCValXdr): Map<SCValXdr, SCValXdr>
}
```

**Examples:**

```kotlin
// Create contract parameters
val params = listOf(
    Scv.toAddress("GABC..."),           // Account address
    Scv.toU128(BigInteger("1000000")),  // Amount
    Scv.toSymbol("transfer"),           // Method name
    Scv.toBool(true),                   // Flag
    Scv.toVec(listOf(                   // Array
        Scv.toU32(1u),
        Scv.toU32(2u),
        Scv.toU32(3u)
    )),
    Scv.toMap(mapOf(                    // Map
        Scv.toSymbol("key") to Scv.toString("value"),
        Scv.toSymbol("count") to Scv.toI32(42)
    ))
)

// Parse contract results
val result = contractResponse.returnValue
val balance = Scv.fromU128(result)
println("Balance: $balance")

// Complex parsing
when (result.discriminant) {
    SCValType.U128 -> {
        val num = Scv.fromU128(result)
        println("Number: $num")
    }
    SCValType.STRING -> {
        val str = Scv.fromString(result)
        println("String: $str")
    }
    SCValType.VEC -> {
        val vec = Scv.fromVec(result)
        println("Array size: ${vec.size}")
    }
}
```

## Utilities

### StrKey

Encode/decode Stellar keys in strkey format.

```kotlin
package com.soneso.stellar.sdk

object StrKey {
    // Encode public key (G...)
    fun encodeEd25519PublicKey(key: ByteArray): String

    // Encode secret seed (S...)
    fun encodeEd25519SecretSeed(seed: ByteArray): CharArray

    // Decode public key
    fun decodeEd25519PublicKey(encoded: String): ByteArray

    // Decode secret seed
    fun decodeEd25519SecretSeed(encoded: CharArray): ByteArray
    fun decodeEd25519SecretSeed(encoded: String): ByteArray

    // Validation
    fun isValidEd25519PublicKey(encoded: String): Boolean
    fun isValidEd25519SecretSeed(encoded: String): Boolean

    // Contract addresses (C...)
    fun encodeContract(contractId: ByteArray): String
    fun decodeContract(encoded: String): ByteArray
    fun isValidContract(encoded: String): Boolean

    // Muxed accounts (M...)
    fun encodeMuxedAccount(muxed: MuxedAccountXdr): String
    fun decodeMuxedAccount(encoded: String): MuxedAccountXdr
    fun isValidMuxedAccount(encoded: String): Boolean
}
```

**Examples:**

```kotlin
// Encode keys
val publicKeyStr = StrKey.encodeEd25519PublicKey(publicKeyBytes)
println("Public: $publicKeyStr")  // G...

val secretSeed = StrKey.encodeEd25519SecretSeed(seedBytes)
println("Secret: ${secretSeed.concatToString()}")  // S...

// Decode keys
val decodedPublic = StrKey.decodeEd25519PublicKey("GABC...")
val decodedSeed = StrKey.decodeEd25519SecretSeed("SXYZ..."."toCharArray())

// Validation
val isValid = StrKey.isValidEd25519PublicKey("GABC...")
println("Valid: $isValid")

// Contract addresses
val contractAddr = StrKey.encodeContract(contractIdBytes)
println("Contract: $contractAddr")  // C...
```

### Memo

Transaction memos for attaching data.

```kotlin
package com.soneso.stellar.sdk

sealed class Memo {
    companion object {
        // No memo
        fun none(): MemoNone

        // Text memo (up to 28 bytes UTF-8)
        fun text(text: String): MemoText

        // ID memo (64-bit number)
        fun id(id: Long): MemoId

        // Hash memo (32 bytes)
        fun hash(hash: ByteArray): MemoHash
        fun hash(hexString: String): MemoHash

        // Return hash memo (32 bytes)
        fun returnHash(hash: ByteArray): MemoReturnHash
    }

    abstract fun toXdr(): MemoXdr
}
```

**Examples:**

```kotlin
// Different memo types
val noMemo = Memo.none()
val textMemo = Memo.text("Invoice #12345")
val idMemo = Memo.id(987654321L)
val hashMemo = Memo.hash("abc123...".decodeHex())
val returnMemo = Memo.returnHash(refundTxHash)

// Add to transaction
TransactionBuilder(account, network)
    .addMemo(textMemo)
    .addOperation(/* ... */)
    .build()
```

### FriendBot

Fund accounts on testnet.

```kotlin
package com.soneso.stellar.sdk

object FriendBot {
    // Fund account with test XLM
    suspend fun fundAccount(
        accountId: String,
        network: Network = Network.TESTNET
    ): Boolean

    // Get friendbot URL for network
    fun getFriendbotUrl(network: Network): String?
}
```

**Examples:**

```kotlin
// Create and fund test account
val keypair = KeyPair.random()
println("New account: ${keypair.getAccountId()}")

val funded = FriendBot.fundAccount(
    accountId = keypair.getAccountId(),
    network = Network.TESTNET
)

if (funded) {
    println("Account funded with 10,000 test XLM!")
    // Account is now active on testnet
} else {
    println("Failed to fund account")
}

// Custom network friendbot
val url = FriendBot.getFriendbotUrl(Network.STANDALONE)
```

---

**Navigation**: [← Architecture](architecture.md) | [Platform Guides →](platforms/)