# SDK Usage Examples

> **Complete API Documentation**: For detailed method signatures, parameters, and return types, see the [auto-generated API reference](https://soneso.github.io/kmp-stellar-sdk/api/latest/).

This guide provides practical code examples for common Stellar SDK operations. Each section demonstrates real-world usage patterns to help you integrate the SDK into your application.

## Table of Contents

- [Keypairs & Accounts](#keypairs--accounts)
  - [Creating Keypairs](#creating-keypairs)
  - [Loading an Account](#loading-an-account)
  - [Funding Testnet Accounts](#funding-testnet-accounts)
  - [HD Wallets (SEP-5)](#hd-wallets-sep-5)
- [Building Classic Transactions](#building-classic-transactions)
  - [Simple Payments](#simple-payments)
  - [Multi-Operation Transactions](#multi-operation-transactions)
- [Operations](#operations)
  - [Payment Operations](#payment-operations)
  - [Account Operations](#account-operations)
  - [Asset Operations](#asset-operations)
  - [Trading Operations](#trading-operations)
  - [Claimable Balance Operations](#claimable-balance-operations)
  - [Liquidity Pool Operations](#liquidity-pool-operations)
  - [Liquidity Pool Trustlines](#liquidity-pool-trustlines)
  - [Sponsorship Operations](#sponsorship-operations)
  - [Soroban Operations](#soroban-operations)
- [Querying Horizon Data](#querying-horizon-data)
  - [Account Queries](#account-queries)
  - [Transaction Queries](#transaction-queries)
  - [Operation Queries](#operation-queries)
  - [Effect Queries](#effect-queries)
  - [Ledger Queries](#ledger-queries)
  - [Payment Queries](#payment-queries)
  - [Trade Queries](#trade-queries)
  - [Asset Queries](#asset-queries)
  - [Order Book Queries](#order-book-queries)
  - [Payment Path Queries](#payment-path-queries)
  - [Claimable Balance Queries](#claimable-balance-queries)
  - [Liquidity Pool Queries](#liquidity-pool-queries)
- [Smart Contracts](#smart-contracts)
  - [Invoking Contracts (Beginner API)](#invoking-contracts-beginner-api)
  - [Advanced Contract Control (buildInvoke)](#advanced-contract-control-buildinvoke)
  - [Deploying Contracts](#deploying-contracts)
  - [Type Conversions (XDR ↔ Native)](#type-conversions-xdr--native)
  - [Authorization](#authorization)
- [Network Communication](#network-communication)
  - [Streaming Events with SSE](#streaming-events-with-sse)
  - [Soroban RPC Operations](#soroban-rpc-operations)
  - [Transaction Submission](#transaction-submission)
- [Assets](#assets)
  - [Creating and Using Assets](#creating-and-using-assets)
  - [Stellar Asset Contracts (SAC)](#stellar-asset-contracts-sac)

## Keypairs & Accounts

### Creating Keypairs

```kotlin
// Generate random keypair
val keypair = KeyPair.random()
println("Account: ${keypair.getAccountId()}")
println("Secret: ${keypair.getSecretSeed()?.concatToString()}")

// Initialize from secret seed
val imported = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")

// Create public-only keypair
val publicOnly = KeyPair.fromAccountId("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D")
assert(!publicOnly.canSign())
```

### Loading an Account

```kotlin
// Initialize Horizon server
val server = HorizonServer("https://horizon-testnet.stellar.org")

// Load account from Horizon
try {
    val account = server.loadAccount("GABC...")
    println("Sequence: ${account.sequence}")
    println("Balances: ${account.balances.map { "${it.balance} ${it.assetCode}" }}")
} catch (e: BadRequestException) {
    // Account doesn't exist yet - fund it first using Friendbot or CreateAccountOperation
    println("Account not found")
}
```

### Funding Testnet Accounts

```kotlin
// Fund account using Friendbot (testnet only)
val keypair = KeyPair.random()
val accountId = keypair.getAccountId()

val success = FriendBot.fundTestnetAccount(accountId)
if (success) {
    println("Account funded with 10,000 XLM")

    // Now the account exists and can be loaded
    val account = server.loadAccount(accountId)
    println("Balance: ${account.balances.first().balance}")
} else {
    println("Funding failed")
}

// For mainnet, use CreateAccountOperation instead (see Operations section)
```

### HD Wallets (SEP-5)

Generate multiple Stellar accounts from a single mnemonic phrase using BIP-39/SLIP-0010 key derivation.

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic

// Generate a 24-word mnemonic (recommended for maximum security)
val phrase = Mnemonic.generate24WordsMnemonic()

// Create Mnemonic instance and derive accounts
val mnemonic = Mnemonic.from(phrase)
val account0 = mnemonic.getKeyPair(index = 0)  // Path: m/44'/148'/0'
val account1 = mnemonic.getKeyPair(index = 1)  // Path: m/44'/148'/1'

println("Account 0: ${account0.getAccountId()}")
println("Account 1: ${account1.getAccountId()}")

// Clean up when done (zeros internal seed)
mnemonic.close()
```

For passphrase support, validation, multi-language support, and security best practices, see the [SEP-5 Documentation](sep/sep-05.md).

## Building Classic Transactions

This section covers building traditional Stellar transactions for payments, account operations, and DEX trading. For Soroban smart contract transactions, see the [Smart Contracts](#smart-contracts) section.

### Simple Payments

```kotlin
// Initialize Horizon server
val server = HorizonServer("https://horizon-testnet.stellar.org")

// Initialize keypair from secret seed (needed for signing the transaction)
val keypair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")

// Load account from network to get current sequence number (required for TransactionBuilder)
val account = server.loadAccount(keypair.getAccountId())

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
    .build()

// Sign and submit
transaction.sign(keypair)
val response = server.submitTransaction(transaction)
```

### Multi-Operation Transactions

```kotlin
// Multi-operation transaction for complete account onboarding
// All operations succeed together or fail together (atomic)
val newAccountKeypair = KeyPair.random()
val usdcAsset = Asset.createNonNativeAsset("USDC", "GISSUER...")

val transaction = TransactionBuilder(account, Network.TESTNET)
    .addOperations(listOf(
        // 1. Create the account with sufficient XLM balance
        CreateAccountOperation(newAccountKeypair.getAccountId(), "5"),
        // 2. Establish trustline for USDC (requires the account to exist)
        ChangeTrustOperation(
            asset = ChangeTrustAsset.create(usdcAsset),
            sourceAccount = newAccountKeypair.getAccountId()
        ),
        // 3. Send initial USDC to the new account (requires trustline)
        PaymentOperation(
            destination = newAccountKeypair.getAccountId(),
            amount = "100",
            asset = usdcAsset
        )
    ))
    .build()

// Both accounts must sign (source account pays fees and sends USDC, new account authorizes trustline)
transaction.sign(keypair)
transaction.sign(newAccountKeypair)

// Submit transaction to Horizon
val response = server.submitTransaction(transaction)
if (response.isSuccess) {
    println("Success! Hash: ${response.hash}")
} else {
    println("Failed: ${response.extras?.resultCodes}")
}
```

## Operations

### Payment Operations

Payment operations transfer assets between accounts. Add these to a TransactionBuilder to execute them.

```kotlin
// Define assets used in examples
val usdcAsset = Asset.createNonNativeAsset("USDC", "GISSUER...")
val eurocAsset = Asset.createNonNativeAsset("EUROC", "GISSUER...")

// Simple XLM payment
PaymentOperation(
    destination = "GDEF...",
    amount = "100",
    asset = Asset.NATIVE
)

// Custom asset payment
PaymentOperation(
    destination = "GDEF...",
    amount = "50",
    asset = usdcAsset
)

// Path payment: send exactly 100 XLM, receive at least 95 USDC (converted via EUROC)
PathPaymentStrictSendOperation(
    sendAsset = Asset.NATIVE,
    sendAmount = "100",
    destination = "GDEST...",
    destAsset = usdcAsset,
    destMin = "95",  // Accept minimum 95 USDC
    path = listOf(eurocAsset)  // Through EUROC
)

// Path payment: receive exactly 100 USDC, send at most 105 XLM
PathPaymentStrictReceiveOperation(
    sendAsset = Asset.NATIVE,
    sendMax = "105",  // Send maximum 105 XLM
    destination = "GDEST...",
    destAsset = usdcAsset,
    destAmount = "100",  // Receive exactly 100 USDC
    path = listOf()
)
```

### Account Operations

```kotlin
// Create new account (funds transferred from source account)
CreateAccountOperation(
    destination = "GNEW...",
    startingBalance = "10"  // 10 XLM minimum
)

// Manually increment sequence number (useful for transaction coordination)
BumpSequenceOperation(
    bumpTo = 12345678
)

// Configure account settings (multi-signature and security)
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

// Store data on-chain (key-value storage)
ManageDataOperation(
    name = "config",
    value = "production".encodeToByteArray()
)

// Remove data entry
ManageDataOperation(
    name = "temp_data",
    value = null  // null removes the entry
)

// Merge account (transfer all XLM and close)
AccountMergeOperation(
    destination = "GDEST..."
)
```

### Asset Operations

```kotlin
// Define asset used in examples
val usdcAsset = Asset.createNonNativeAsset("USDC", "GISSUER...")

// Establish trustline to receive custom asset (required before receiving USDC)
ChangeTrustOperation(
    asset = ChangeTrustAsset.create(usdcAsset),
    limit = "10000"  // Maximum 10,000 USDC
)

// Remove trustline (set limit to 0, account must have zero balance)
ChangeTrustOperation(
    asset = ChangeTrustAsset.create(usdcAsset),
    limit = "0"
)

// Issuer authorizes trustline (issuer can control who holds their asset)
SetTrustLineFlagsOperation(
    trustor = "GTRUSTOR...",
    asset = usdcAsset,
    setFlags = setOf(TrustLineFlag.AUTHORIZED),
    clearFlags = setOf(TrustLineFlag.AUTHORIZED_TO_MAINTAIN_LIABILITIES)
)
```

### Trading Operations

```kotlin
// Create sell offer on DEX (sell 100 XLM for USDC at 0.20 USDC per XLM)
ManageSellOfferOperation(
    selling = Asset.NATIVE,
    buying = usdcAsset,
    amount = "100",
    price = Price.fromString("0.20")
)

// Create buy offer (receive exactly 50 USDC, price is maximum willing to pay)
ManageBuyOfferOperation(
    selling = Asset.NATIVE,
    buying = usdcAsset,
    buyAmount = "50",
    price = Price.fromString("0.20")
)

// Create passive offer (won't immediately match existing offers, useful for market making)
CreatePassiveSellOfferOperation(
    selling = Asset.NATIVE,
    buying = usdcAsset,
    amount = "100",
    price = Price.fromString("0.20")
)

// Update existing offer (change price or amount)
ManageSellOfferOperation(
    selling = Asset.NATIVE,
    buying = usdcAsset,
    amount = "150",  // New amount
    price = Price.fromString("0.25"),  // New price
    offerId = 12345  // ID of offer to update
)

// Cancel offer (set amount to 0)
ManageSellOfferOperation(
    selling = Asset.NATIVE,
    buying = usdcAsset,
    amount = "0",  // Setting to 0 cancels the offer
    price = Price.fromString("0.20"),
    offerId = 12345  // ID of offer to cancel
)
```

### Claimable Balance Operations

```kotlin
// Send funds with claim conditions (useful for escrow, scheduled payments, or pre-authorized transactions)
CreateClaimableBalanceOperation(
    amount = "100",
    asset = Asset.NATIVE,
    claimants = listOf(
        // Immediate claim allowed
        Claimant(
            destination = "GCLAIM...",
            predicate = Predicate.unconditional()
        ),
        // Can claim within 1 hour from balance creation (expires after)
        Claimant(
            destination = "GCLAIM2...",
            predicate = Predicate.relativeTime(3600)
        )
    )
)

// Recipient claims the balance (if predicate conditions are met)
ClaimClaimableBalanceOperation(
    balanceId = "00000000abc123..."
)

// Issuer reclaims unclaimed balance (requires CLAWBACK_ENABLED flag on asset)
ClawbackClaimableBalanceOperation(
    balanceId = "00000000abc123..."
)
```

### Liquidity Pool Operations

```kotlin
// Provide liquidity to AMM pool (earn trading fees, price bounds protect against slippage)
LiquidityPoolDepositOperation(
    liquidityPoolId = "abcd1234...",
    maxAmountA = "100",  // Max 100 of asset A
    maxAmountB = "50",   // Max 50 of asset B
    minPrice = Price.fromString("1.9"),  // Minimum acceptable pool price ratio (slippage protection)
    maxPrice = Price.fromString("2.1")   // Maximum acceptable pool price ratio (slippage protection)
)

// Remove liquidity from pool (burn pool shares to reclaim underlying assets)
LiquidityPoolWithdrawOperation(
    liquidityPoolId = "abcd1234...",
    amount = "10",  // Burn 10 pool shares
    minAmountA = "18",  // Minimum asset A to receive (slippage protection)
    minAmountB = "9"    // Minimum asset B to receive (slippage protection)
)
```

### Liquidity Pool Trustlines

Before participating in an AMM liquidity pool, you must first establish a trustline for the pool shares. This is similar to establishing trustlines for regular assets, but uses a LiquidityPool object instead of an Asset.

```kotlin
// Initialize Horizon server
val server = HorizonServer("https://horizon-testnet.stellar.org")

// Define the assets that make up the liquidity pool
val usdcAsset = Asset.createNonNativeAsset("USDC", "GISSUER...")
val eurocAsset = Asset.createNonNativeAsset("EUROC", "GISSUER2...")

// Create liquidity pool object (assets must be in lexicographic order)
val liquidityPool = LiquidityPool(
    assetA = usdcAsset,  // Assets are automatically validated for correct order
    assetB = eurocAsset,
    fee = LiquidityPool.FEE  // 30 basis points (0.3%)
)

// Establish trustline for liquidity pool shares (required before depositing)
// This allows your account to receive pool shares when you deposit liquidity
val poolTrustlineOp = ChangeTrustOperation(
    liquidityPool = liquidityPool,
    limit = "1000"  // Maximum pool shares you're willing to hold
)

// Complete workflow: trustline, then deposit to earn trading fees
val userKeypair = KeyPair.fromSecretSeed("SUSER...")
val userAccount = server.loadAccount(userKeypair.getAccountId())

// Step 1: Create trustline for pool shares
val trustlineTx = TransactionBuilder(userAccount, Network.TESTNET)
    .addOperation(poolTrustlineOp)
    .build()
trustlineTx.sign(userKeypair)
server.submitTransaction(trustlineTx)

// Step 2: Deposit liquidity to the pool (requires trustline from Step 1)
// Get the pool ID (suspend function, so we need to use it in a coroutine context)
val poolId = liquidityPool.getLiquidityPoolId()

val depositTx = TransactionBuilder(userAccount, Network.TESTNET)
    .addOperation(
        LiquidityPoolDepositOperation(
            liquidityPoolId = poolId,
            maxAmountA = "100",  // Max USDC to deposit
            maxAmountB = "100",  // Max EUROC to deposit
            minPrice = Price.fromString("0.95"),  // Slippage protection
            maxPrice = Price.fromString("1.05")
        )
    )
    .build()
depositTx.sign(userKeypair)
server.submitTransaction(depositTx)

// For regular asset trustlines, use ChangeTrustAsset with the asset
// (See Asset Operations section for more examples)
val regularAssetTrustline = ChangeTrustOperation(
    asset = usdcAsset,
    limit = "10000"  // Maximum USDC you're willing to hold
)
```

### Sponsorship Operations

Sponsorship allows one account to pay base reserves for another account's ledger entries, enabling user onboarding without requiring them to hold XLM.

```kotlin
// Example 1: Create a sponsored account (0 XLM required)
// Sponsor pays reserve costs, enabling zero-balance account creation
val sponsorKeypair = KeyPair.random()
val sponsorId = sponsorKeypair.getAccountId()
val newAccountKeypair = KeyPair.random()
val newAccountId = newAccountKeypair.getAccountId()

// Build transaction with sponsorship block
val tx = TransactionBuilder(
    sourceAccount = Account(sponsorId, sponsorSequence),
    network = Network.TESTNET
)
    // Begin sponsoring future reserves for new account
    .addOperation(BeginSponsoringFutureReservesOperation(
        sponsoredId = newAccountId
    ))
    // Create account with 0 XLM (sponsor pays base reserve)
    .addOperation(CreateAccountOperation(
        destination = newAccountId,
        startingBalance = "0"
    ))
    // End sponsorship block (must be signed by sponsored account)
    .addOperation(EndSponsoringFutureReservesOperation().apply {
        sourceAccount = newAccountId
    })
    .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

// Both accounts must sign: sponsor and sponsored
tx.sign(sponsorKeypair)
tx.sign(newAccountKeypair)
horizonServer.submitTransaction(tx.toEnvelopeXdrBase64())

// Example 2: Sponsor a trustline for an existing account
// Enables user to add trustline without holding reserve XLM
val asset = AssetTypeCreditAlphaNum4("USD", issuerId)

val trustlineTx = TransactionBuilder(
    sourceAccount = Account(sponsorId, sponsorSequence),
    network = Network.TESTNET
)
    .addOperation(BeginSponsoringFutureReservesOperation(
        sponsoredId = userId
    ))
    // Trustline created with reserves paid by sponsor
    .addOperation(ChangeTrustOperation(
        asset = asset,
        limit = "1000"
    ).apply {
        sourceAccount = userId
    })
    .addOperation(EndSponsoringFutureReservesOperation().apply {
        sourceAccount = userId
    })
    .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

trustlineTx.sign(sponsorKeypair)
trustlineTx.sign(userKeypair)
horizonServer.submitTransaction(trustlineTx.toEnvelopeXdrBase64())

// Example 3: Revoke sponsorship
// Transfer reserve responsibility back to the account owner
val revokeAccountTx = TransactionBuilder(
    sourceAccount = Account(sponsorId, sponsorSequence),
    network = Network.TESTNET
)
    // Revoke account sponsorship (account pays its own reserves)
    .addOperation(RevokeSponsorshipOperation(
        sponsorship = Sponsorship.Account(accountId = userId)
    ))
    // Revoke trustline sponsorship
    .addOperation(RevokeSponsorshipOperation(
        sponsorship = Sponsorship.TrustLine(accountId = userId, asset = asset)
    ))
    // Revoke data entry sponsorship
    .addOperation(RevokeSponsorshipOperation(
        sponsorship = Sponsorship.Data(accountId = userId, dataName = "user_metadata")
    ))
    .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

revokeAccountTx.sign(sponsorKeypair)
horizonServer.submitTransaction(revokeAccountTx.toEnvelopeXdrBase64())

// Other sponsorship types available:
// - Sponsorship.Offer(sellerId, offerId) - Sponsor trading offers
// - Sponsorship.ClaimableBalance(balanceId) - Sponsor claimable balances
// - Sponsorship.Signer(accountId, signerKey) - Sponsor additional signers
```

### Soroban Operations

Soroban operations differ fundamentally from Classic Stellar operations. They require a simulation step to determine resource requirements, authorization entries, and transaction data before submission. For most use cases, the `ContractClient` API (see [Smart Contracts](#smart-contracts) section) handles this workflow automatically.

```kotlin
// Example: Invoke contract function (low-level approach)
// For simpler workflows, use ContractClient API (see Smart Contracts section)

val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")

// 1. Create operation
val invokeOp = InvokeHostFunctionOperation.invokeContractFunction(
    contractAddress = "CCREATE...",
    functionName = "transfer",
    parameters = listOf(
        Scv.toAddress("GFROM..."),
        Scv.toAddress("GTO..."),
        Scv.toInt128(BigInteger.valueOf(1000))
    )
)

// 2. Build transaction (SorobanServer has getAccount method)
val account = sorobanServer.getAccount(sourceAccountId)
val transaction = TransactionBuilder(account, Network.TESTNET)
    .addOperation(invokeOp)
    .build()

// 3. Simulate to get resource requirements (REQUIRED for Soroban)
val simulationResult = sorobanServer.simulateTransaction(transaction)
if (!simulationResult.isSuccess) {
    throw Exception("Simulation failed: ${simulationResult.error}")
}

// 4. Apply simulation results (resource fees, footprint, auth)
transaction.setSorobanData(simulationResult.transactionData)
transaction.addResourceFees(simulationResult.minResourceFee)

// 5. Sign authorization entries if required
if (simulationResult.authEntries.isNotEmpty()) {
    val signedAuth = Auth.authorizeEntries(
        entries = simulationResult.authEntries,
        signer = sourceKeypair,
        validUntilLedgerSeq = simulationResult.latestLedger + 100,
        network = Network.TESTNET
    )
    transaction.setSorobanAuth(signedAuth)
}

// 6. Sign transaction
transaction.sign(sourceKeypair)

// 7. Submit
val response = sorobanServer.sendTransaction(transaction)
println("Transaction hash: ${response.hash}")
```

Other Soroban operations:

```kotlin
// Upload contract WASM (use ByteArray for cross-platform compatibility)
val wasmBytes: ByteArray = // ... load WASM file
InvokeHostFunctionOperation.uploadContractWasm(wasmBytes)

// Extend storage lifetime (prevent contract data expiration)
ExtendFootprintTTLOperation(
    extendTo = 535680  // Extend to ~3 months (assuming 5 sec/ledger)
)

// Restore archived data (required before accessing expired state)
RestoreFootprintOperation()
```

**Note**: For most use cases, prefer the `ContractClient` API (see [Smart Contracts](#smart-contracts) section) which handles simulation, authorization, and resource management automatically.

## Querying Horizon Data

Horizon provides comprehensive APIs for querying blockchain data. This section demonstrates common query patterns for retrieving accounts, transactions, operations, and more.

```kotlin
// Initialize Horizon server (reuse this instance across queries)
val server = HorizonServer("https://horizon-testnet.stellar.org")
```

### Account Queries

```kotlin
// Load specific account with full details (balances, signers, thresholds)
val account = server.accounts().account("GABC...")
println("Sequence: ${account.sequenceNumber}")
println("Balances: ${account.balances.map { "${it.balance} ${it.assetCode}" }}")

// Query multiple accounts with filters (useful for discovering sponsored accounts or asset holders)
val accounts = server.accounts()
    .forSponsor("GSPONSOR...")  // Find accounts sponsored by this address
    .forAsset("USDC", "GISSUER...")  // Find USDC holders (separate parameters: code, issuer)
    .cursor("12345")  // Pagination cursor from previous response
    .limit(50)  // Max 200, default 10
    .order(RequestBuilder.Order.DESC)  // DESC for newest first, ASC for oldest first
    .execute()

accounts.records.forEach { account ->
    println("Account: ${account.accountId}")
    println("Balances: ${account.balances.size}")
}
```

### Transaction Queries

```kotlin
// Get specific transaction by hash (useful for checking transaction status after submission)
val transaction = server.transactions().transaction("abc123...")
println("Result: ${transaction.successful}")
println("Fee charged: ${transaction.feeCharged}")

// Query transactions with filters (useful for transaction history, auditing, monitoring)
val transactions = server.transactions()
    .forAccount("GABC...")  // All transactions involving this account
    .includeFailed(true)  // Include failed transactions (default: false, only successful)
    .limit(20)
    .order(RequestBuilder.Order.DESC)  // Newest first
    .execute()

transactions.records.forEach { tx ->
    println("Hash: ${tx.hash}, Ledger: ${tx.ledger}, Ops: ${tx.operationCount}")
}

// Get transactions for a specific ledger (useful for analyzing block contents)
val ledgerTxs = server.transactions()
    .forLedger(12345678)
    .execute()

// Get transactions for a specific claimable balance (track who claimed it)
val claimableTxs = server.transactions()
    .forClaimableBalance("00000000abc...")
    .execute()

// Get transactions for a liquidity pool (track deposits/withdrawals)
val poolTxs = server.transactions()
    .forLiquidityPool("abc123...")
    .execute()
```

### Operation Queries

```kotlin
// Get specific operation by ID
val operation = server.operations().operation(12345678)
println("Type: ${operation.type}")

// Query operations with filters (useful for tracking specific actions, building activity feeds)
val operations = server.operations()
    .forAccount("GABC...")  // All operations involving this account
    .limit(50)
    .order(RequestBuilder.Order.DESC)
    .execute()

operations.records.forEach { op ->
    when (op) {
        is PaymentOperationResponse -> {
            println("Payment: ${op.amount} ${op.asset.code} to ${op.to}")
        }
        is CreateAccountOperationResponse -> {
            println("Account created: ${op.account} with ${op.startingBalance} XLM")
        }
        // Handle other operation types as needed
    }
}

// Get operations for a specific transaction (useful for analyzing transaction details)
val txOps = server.operations()
    .forTransaction("abc123...")
    .execute()

// Get operations in a specific ledger (analyze ledger contents)
val ledgerOps = server.operations()
    .forLedger(12345678)
    .execute()

// Get operations for a liquidity pool (track pool activity)
val poolOps = server.operations()
    .forLiquidityPool("abc123...")
    .execute()
```

### Effect Queries

```kotlin
// Query effects for an account (useful for detailed activity tracking, notifications)
// Effects show the specific changes that occurred (e.g., balance changes, trustline changes)
val effects = server.effects()
    .forAccount("GABC...")
    .limit(50)
    .order(RequestBuilder.Order.DESC)
    .execute()

effects.records.forEach { effect ->
    println("Type: ${effect.type}")
}

// Get effects for a specific transaction (detailed impact analysis)
val txEffects = server.effects()
    .forTransaction("abc123...")
    .execute()

// Get effects for a specific operation (granular change tracking)
val opEffects = server.effects()
    .forOperation(12345678)
    .execute()

// Get effects in a ledger (ledger-level impact analysis)
val ledgerEffects = server.effects()
    .forLedger(12345678)
    .execute()

// Get effects for a liquidity pool (track pool state changes)
val poolEffects = server.effects()
    .forLiquidityPool("abc123...")
    .execute()
```

### Ledger Queries

```kotlin
// Get specific ledger by sequence (useful for analyzing specific blocks)
val ledger = server.ledgers().ledger(12345678)
println("Closed at: ${ledger.closedAt}")
println("Transaction count: ${ledger.transactionCount}")
println("Operation count: ${ledger.operationCount}")

// Query recent ledgers (useful for monitoring network activity)
val ledgers = server.ledgers()
    .limit(10)
    .order(RequestBuilder.Order.DESC)  // Newest first
    .execute()

ledgers.records.forEach { ledger ->
    println("Ledger ${ledger.sequence}: ${ledger.transactionCount} transactions")
}
```

### Payment Queries

```kotlin
// Query payments for an account (useful for payment history, accounting)
// Payment queries return only payment-related operations (Payment, PathPayment, CreateAccount, AccountMerge)
val payments = server.payments()
    .forAccount("GABC...")
    .limit(50)
    .order(RequestBuilder.Order.DESC)
    .execute()

payments.records.forEach { payment ->
    when (payment) {
        is PaymentOperationResponse -> {
            println("Payment: ${payment.amount} ${payment.asset.code}")
            println("From: ${payment.from}, To: ${payment.to}")
        }
        is CreateAccountOperationResponse -> {
            println("Account funded: ${payment.startingBalance} XLM")
        }
    }
}

// Get payments for a transaction (filter transaction operations to payments only)
val txPayments = server.payments()
    .forTransaction("abc123...")
    .execute()

// Get payments in a ledger (analyze payment activity in a block)
val ledgerPayments = server.payments()
    .forLedger(12345678)
    .execute()
```

### Trade Queries

```kotlin
// Query trades for an account (useful for trading history, PnL calculation)
val trades = server.trades()
    .forAccount("GABC...")
    .limit(50)
    .order(RequestBuilder.Order.DESC)
    .execute()

trades.records.forEach { trade ->
    println("Trade: ${trade.baseAmount} ${trade.baseAssetCode} for ${trade.counterAmount} ${trade.counterAssetCode}")
    println("Price: ${trade.price}, Timestamp: ${trade.ledgerCloseTime}")
}

// Query trades for specific asset pair (useful for price discovery, charting)
val pairTrades = server.trades()
    .forBaseAsset("native")  // XLM as base
    .forCounterAsset("credit_alphanum4", "USDC", "GISSUER...")  // USDC as counter
    .limit(100)
    .execute()

// Query trades for a specific offer (track offer execution history)
val offerTrades = server.trades()
    .forOfferId(12345)
    .execute()

// Query trades by type (filter orderbook vs liquidity pool trades)
val orderbookTrades = server.trades()
    .forTradeType("orderbook")  // Options: "orderbook", "liquidity_pool", "all"
    .limit(50)
    .execute()

// Query trades for a liquidity pool (track AMM trading activity)
val poolTrades = server.trades()
    .forLiquidityPool("abc123...")
    .limit(50)
    .execute()
```

### Asset Queries

```kotlin
// Query assets with filters (useful for discovering tradeable assets)
val assets = server.assets()
    .forAssetCode("USDC")  // All USDC assets from different issuers
    .limit(20)
    .execute()

assets.records.forEach { asset ->
    println("Asset: ${asset.assetCode} issued by ${asset.assetIssuer}")
    println("Accounts: ${asset.numAccounts}, Supply: ${asset.amount}")
}

// Query assets by issuer (find all assets from a specific issuer)
val issuerAssets = server.assets()
    .forAssetIssuer("GISSUER...")
    .execute()

// Query assets by code and issuer (specific asset lookup)
val specificAsset = server.assets()
    .forAssetCode("USDC")
    .forAssetIssuer("GISSUER...")
    .execute()
```

### Order Book Queries

```kotlin
// Get order book for asset pair (useful for DEX trading, price discovery)
val usdcAsset = Asset.createNonNativeAsset("USDC", "GISSUER...")
val orderBook = server.orderBook()
    .sellingAsset(Asset.NATIVE)  // Selling XLM
    .buyingAsset(usdcAsset)  // Buying USDC
    .execute()

println("Best bid: ${orderBook.bids.firstOrNull()?.price}")
println("Best ask: ${orderBook.asks.firstOrNull()?.price}")

// Iterate through order book depth
orderBook.bids.forEach { bid ->
    println("Bid: ${bid.amount} at ${bid.price}")
}
orderBook.asks.forEach { ask ->
    println("Ask: ${ask.amount} at ${ask.price}")
}
```

### Payment Path Queries

```kotlin
// Find payment paths (strict send) - know how much you're sending, discover destinations
val sendPaths = server.strictSendPaths()
    .sourceAsset("native")  // Sending XLM
    .sourceAmount("100")  // Sending exactly 100 XLM
    .destinationAccount("GDEST...")  // To this account
    .execute()

sendPaths.records.forEach { path ->
    println("Destination asset: ${path.destinationAssetCode}")
    println("Destination amount: ${path.destinationAmount}")
    println("Path: ${path.path.map { it.code }}")
}

// Find payment paths with specific destination assets (useful for multi-currency scenarios)
val multiAssetPaths = server.strictSendPaths()
    .sourceAsset("credit_alphanum4", "USDC", "GISSUER...")
    .sourceAmount("100")
    .destinationAssets(listOf(
        Triple("credit_alphanum4", "EUROC", "GISSUER2..."),
        Triple("credit_alphanum4", "GBPT", "GISSUER3...")
    ))
    .execute()

// Find payment paths (strict receive) - know how much you want to receive, discover costs
val receivePaths = server.strictReceivePaths()
    .sourceAccount("GABC...")  // From this account
    .destinationAsset("credit_alphanum4", "EUROC", "GISSUER...")  // Receiving EUROC
    .destinationAmount("50")  // Receiving exactly 50 EUROC
    .execute()

receivePaths.records.forEach { path ->
    println("Source asset: ${path.sourceAssetCode}")
    println("Source amount: ${path.sourceAmount}")
    println("Path: ${path.path.map { it.code }}")
}

// Find paths with specific source assets (useful for multi-currency wallets)
val multiSourcePaths = server.strictReceivePaths()
    .sourceAssets(listOf(
        Triple("native", null, null),
        Triple("credit_alphanum4", "USDC", "GISSUER...")
    ))
    .destinationAsset("credit_alphanum4", "EUROC", "GISSUER2...")
    .destinationAmount("100")
    .execute()
```

### Claimable Balance Queries

```kotlin
// Query claimable balances with filters (useful for finding pending payments, airdrops)
val claimableBalances = server.claimableBalances()
    .forClaimant("GABC...")  // Balances this account can claim
    .limit(20)
    .execute()

claimableBalances.records.forEach { balance ->
    println("Balance ID: ${balance.id}")
    println("Asset: ${balance.asset}, Amount: ${balance.amount}")
    println("Sponsor: ${balance.sponsor}")
}

// Query by sponsor (find balances sponsored by this account)
val sponsoredBalances = server.claimableBalances()
    .forSponsor("GSPONSOR...")
    .execute()

// Query by asset (find all claimable balances for a specific asset)
val assetBalances = server.claimableBalances()
    .forAsset("credit_alphanum4", "USDC", "GISSUER...")  // Asset type, code, issuer
    .execute()

// Get specific claimable balance by ID
val balance = server.claimableBalances().claimableBalance("00000000abc...")
println("Amount: ${balance.amount}, Claimants: ${balance.claimants.size}")
```

### Liquidity Pool Queries

```kotlin
// Query liquidity pools (useful for discovering AMM pools)
val pools = server.liquidityPools()
    .limit(20)
    .execute()

pools.records.forEach { pool ->
    println("Pool ID: ${pool.id}")
    println("Reserves: ${pool.reserves}")
    println("Total shares: ${pool.totalShares}")
}

// Query pools by reserves (find pools containing specific assets)
val usdcAsset = Asset.createNonNativeAsset("USDC", "GISSUER...")
val usdcPools = server.liquidityPools()
    .forReserves(listOf(Asset.NATIVE, usdcAsset))  // XLM/USDC pools
    .execute()

// Query pools by account (find pools an account participates in)
val accountPools = server.liquidityPools()
    .forAccount("GABC...")
    .execute()

// Get specific pool by ID
val pool = server.liquidityPools().liquidityPool("abc123...")
println("Fee BP: ${pool.feeBp}")  // Fee in basis points (30 = 0.3%)
println("Type: ${pool.type}")  // constant_product
```

## Smart Contracts

### Invoking Contracts (Beginner API)

The `invoke()` method provides the simplest API for contract interaction with automatic execution. Use `invoke()` for simple use cases with a single signer where auto-execution is desired. For multi-signature workflows or transaction customization (memos, preconditions), use `buildInvoke()` instead (covered in the next section).

```kotlin
// Loads contract spec from network for automatic type conversion and validation
val client = ContractClient.forContract(
    contractId = "CCREATE...",
    rpcUrl = "https://soroban-testnet.stellar.org",
    network = Network.TESTNET
)

// Read-only call - Option 1: Custom result parser
// Use when you need specific type conversion or custom parsing logic
val balance = client.invoke<BigInteger>(
    functionName = "balance",
    arguments = mapOf("account" to "GABC..."),  // SDK auto-converts native types to XDR
    source = "GABC...",
    signer = null,  // No signer needed for read-only calls
    parseResultXdrFn = { scval ->
        Scv.fromInt128(scval)  // Returns BigInteger per SDK type mapping
    }
)
println("Balance: $balance")

// Read-only call - Option 2: Using funcResToNative for automatic parsing
// Use when contract spec provides complete type information
val balanceXdr = client.invoke<SCValXdr>(
    functionName = "balance",
    arguments = mapOf("account" to "GABC..."),
    source = "GABC...",
    signer = null
)
val parsedBalance = client.funcResToNative("balance", balanceXdr) as BigInteger
println("Balance: $parsedBalance")

// Write operation with native types (auto-signs and submits)
val sourceAccount = KeyPair.fromSecretSeed("SXXX...")  // Source account keypair
client.invoke<Unit>(
    functionName = "transfer",
    arguments = mapOf(
        "from" to "GFROM...",
        "to" to "GTO...",
        "amount" to 1000L
    ),
    source = sourceAccount.getAccountId(),
    signer = sourceAccount,  // Required for write
    parseResultXdrFn = null  // Void return
)
println("Transfer complete!")
```

### Advanced Contract Control (buildInvoke)

The `buildInvoke()` method provides full control over the transaction lifecycle when you need to manage authorization workflows for atomic swaps, escrow, multi-party transfers, or other scenarios requiring signatures from multiple accounts:

```kotlin
// Initialize accounts and keypairs
val sourceKeypair = KeyPair.fromSecretSeed("SXXX...")
val fromAddress = "GFROM..."
val toAddress = "GTO..."
val account1Keypair = KeyPair.fromSecretSeed("SXXX...")
val account1Id = account1Keypair.getAccountId()
val account2Keypair = KeyPair.fromSecretSeed("SXXX...")
val account2Id = account2Keypair.getAccountId()

// Multi-signature workflow: Transfer requires authorization from multiple parties
// 1. Build transaction without executing (gives control over signing flow)
// 2. Check which addresses need to authorize (needsNonInvokerSigningBy)
// 3. Collect authorization signatures from each required party
// 4. Submit once all signatures are collected

// Build transaction without auto-execution
val assembled = client.buildInvoke<String>(
    functionName = "transfer",
    arguments = mapOf(
        "from" to fromAddress,
        "to" to toAddress,
        "amount" to 1000
    ),
    source = sourceKeypair.getAccountId(),
    signer = sourceKeypair,
    parseResultXdrFn = { Scv.fromString(it) }
)

// Check which addresses need to sign authorization entries
// Essential for atomic swaps, escrow, multi-party operations
// Returns Set<String> of account IDs that must authorize this transaction
val whoNeedsToSign = assembled.needsNonInvokerSigningBy()

// Sign authorization entries for each required party
if (whoNeedsToSign.contains(account1Id)) {
    assembled.signAuthEntries(account1Keypair)
}
if (whoNeedsToSign.contains(account2Id)) {
    assembled.signAuthEntries(account2Keypair)
}

// Submit transaction with all required signatures
val result = assembled.signAndSubmit(sourceKeypair)
```

### Deploying Contracts

```kotlin
// Initialize deployer keypair
val deployer = KeyPair.fromSecretSeed("SXXX...")  // Deployer account keypair

// Load WASM bytes (platform-compatible approach)
// On JVM: File("token.wasm").readBytes()
// On JS: fetch() the WASM file and convert to ByteArray
// On iOS/macOS: Bundle.main.path() or NSFileManager
val wasmBytes: ByteArray = // ... load WASM file from platform-specific storage

// One-step deployment: Simple and convenient for single-contract deployments
// Use when: Deploying a single contract instance with no intention to reuse WASM
// Benefit: Handles both WASM upload and contract creation in one call with Map-based args for developer convenience
val newClient = ContractClient.deploy(
    wasmBytes = wasmBytes,
    constructorArgs = mapOf(
        "name" to "MyToken",
        "symbol" to "MTK",
        "decimals" to 7
    ),
    source = deployer.getAccountId(),
    signer = deployer,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org"
)
println("Contract deployed at: ${newClient.contractId}")

// Two-step deployment: Cost-efficient for deploying multiple contract instances
// Use when: You need to deploy multiple contracts from the same WASM code
// Benefit: Upload WASM once, then deploy many instances - saves transaction fees (no repeated WASM uploads)
//          and deployment time (WASM already on-chain)
// Step 1: Install WASM once
val wasmId = ContractClient.install(
    wasmBytes = wasmBytes,
    source = deployer.getAccountId(),
    signer = deployer,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org"
)

// Step 2: Deploy multiple contracts from same WASM
// Uses XDR values (List<SCValXdr>) instead of Map for type precision during deployment
// Each deployment is faster and cheaper than one-step because WASM is already on-chain
val contract1 = ContractClient.deployFromWasmId(
    wasmId = wasmId,
    constructorArgs = listOf(
        Scv.toString("Token1"),
        Scv.toString("TK1")
    ),
    source = deployer.getAccountId(),
    signer = deployer,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org"
)

val contract2 = ContractClient.deployFromWasmId(
    wasmId = wasmId,
    constructorArgs = listOf(
        Scv.toString("Token2"),
        Scv.toString("TK2")
    ),
    source = deployer.getAccountId(),
    signer = deployer,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org"
)
```

### Type Conversions (XDR ↔ Native)

The SDK automatically converts between XDR and native Kotlin types:

| XDR Type | Native Kotlin Type | Example Value |
|----------|-------------------|---------------|
| `SCV_BOOL` | `Boolean` | `true` |
| `SCV_U32` | `UInt` | `42u` |
| `SCV_I32` | `Int` | `42` |
| `SCV_U64` | `ULong` | `1000000UL` |
| `SCV_I64` | `Long` | `1000000L` |
| `SCV_U128` | `BigInteger` | `BigInteger("123456789")` |
| `SCV_I128` | `BigInteger` | `BigInteger("123456789")` |
| `SCV_U256` | `BigInteger` | `BigInteger("999...")` |
| `SCV_I256` | `BigInteger` | `BigInteger("999...")` |
| `SCV_BYTES` | `ByteArray` | `byteArrayOf(1, 2, 3)` |
| `SCV_STRING` | `String` | `"hello"` |
| `SCV_SYMBOL` | `String` | `"symbol"` |
| `SCV_VEC` | `List<Any?>` | `listOf(1, 2, 3)` |
| `SCV_MAP` | `Map<*, *>` | `mapOf("key" to "value")` |
| `SCV_ADDRESS` | `String` | `"GABC..."` |
| `SCV_VOID` | `null` | `null` |

**Manual conversion examples:**

```kotlin
// Convert native arguments to XDR (useful for low-level operations or when bypassing convenience layer)
val xdrArgs = client.funcArgsToXdrSCValues(
    functionName = "transfer",
    arguments = mapOf(
        "from" to "GABC...",
        "to" to "GDEF...",
        "amount" to 1000L
    )
)

// Parse XDR results to native types (inverse of funcArgsToXdrSCValues for bidirectional conversion)
val resultXdr = client.invoke<SCValXdr>(
    functionName = "balance",
    arguments = mapOf("account" to "GABC..."),
    source = sourceAccount,
    signer = null
)
val balance = client.funcResToNative("balance", resultXdr) as BigInteger

// Create contract values manually using low-level Scv API
// This is useful for custom types, low-level control, or performance-critical code where
// you need direct XDR manipulation without automatic conversion
val addressScVal = Address("GABC...").toSCVal()  // Convert string address to SCValXdr
val params = listOf(
    addressScVal,                            // Account address
    Scv.toUint128(BigInteger("1000000")),   // Amount
    Scv.toSymbol("transfer"),               // Method name
    Scv.toBoolean(true),                    // Flag
    Scv.toVec(listOf(                       // Array
        Scv.toUint32(1u),
        Scv.toUint32(2u)
    )),
    Scv.toMap(linkedMapOf(                  // Map (use LinkedHashMap for insertion order)
        Scv.toSymbol("key") to Scv.toString("value")
    ))
)

// Parse complex results with type-safe discriminated unions
// This pattern prevents runtime errors by ensuring you handle the correct SCValXdr type
val resultXdr = client.invoke<SCValXdr>(
    functionName = "get_data",
    arguments = mapOf("key" to "user_123"),
    source = sourceAccount,
    signer = null
)
when (resultXdr.discriminant) {
    SCValTypeXdr.SCV_U128 -> {
        val num = Scv.fromUint128(resultXdr)
        println("Number: $num")
    }
    SCValTypeXdr.SCV_STRING -> {
        val str = Scv.fromString(resultXdr)
        println("String: $str")
    }
    SCValTypeXdr.SCV_VEC -> {
        val vec = Scv.fromVec(resultXdr)
        println("Array size: ${vec.size}")
    }
    else -> println("Unexpected type: ${resultXdr.discriminant}")
}
```

### Authorization

Authorization entries provide cryptographic proof of consent for contract invocations. The SDK handles authorization automatically through `ContractClient.invoke()` and `AssembledTransaction.signAuthEntries()`. Use the `Auth` class directly only for advanced scenarios requiring manual control.

For complete authorization workflows (multi-signature, atomic swaps), see the [Advanced Contract Control (buildInvoke)](#advanced-contract-control-buildinvoke) section which demonstrates `AssembledTransaction.signAuthEntries()` usage.

```kotlin
// Example 1: Sign a single authorization entry
// Use case: You have an auth entry from simulation and need to sign it manually
// (Typically you'd use AssembledTransaction.signAuthEntries() instead - see Advanced Contract Control section)
val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")
val currentLedger = sorobanServer.getLatestLedger().sequence

// Assume you have an auth entry from contract simulation
val entry: SorobanAuthorizationEntryXdr = // ... from simulation

val userKeypair = KeyPair.fromSecretSeed("SXXX...")

val signedEntry = Auth.authorizeEntry(
    entry = entry,
    signer = userKeypair,
    validUntilLedgerSeq = currentLedger + 100,  // ~8.3 minutes (5s/ledger)
    network = Network.TESTNET
)

// Note: You cannot reassign operation.auth (it's immutable)
// Use AssembledTransaction.signAuthEntries() which properly rebuilds the transaction
// See "Advanced Contract Control" section for complete workflow

// Example 2: Build custom authorization from scratch
// Use case: Complex permission models, custom invocation trees, nested contract calls
val contractId = "CCXXX..."
val contractAddress = Address(contractId).toSCAddress()
val invocation = SorobanAuthorizedInvocationXdr(
    function = SorobanAuthorizedFunctionXdr.ContractFn(
        InvokeContractArgsXdr(
            contractAddress = contractAddress,
            functionName = SCSymbolXdr("transfer"),
            args = listOf(
                Scv.toAddress("GFROM..."),
                Scv.toAddress("GTO..."),
                Scv.toInt128(1000L)
            )
        )
    ),
    subInvocations = emptyList()  // Nested contract calls if needed
)

val keypair = KeyPair.fromSecretSeed("SXXX...")
val validUntil = currentLedger + 100

val customAuth = Auth.authorizeInvocation(
    signer = keypair,
    validUntilLedgerSeq = validUntil,
    invocation = invocation,
    network = Network.TESTNET
)

// Example 3: Custom signer for hardware wallets
// Use case: Ledger, Trezor, HSM integration for cold storage security
class LedgerSigner : Auth.Signer {
    override suspend fun sign(preimage: HashIDPreimageXdr): Auth.Signature {
        // 1. Convert preimage to payload
        val payload = Util.hash(preimage.toXdrByteArray())

        // 2. Send payload to your signing device (e.g., Ledger hardware wallet)
        // val signature = ledgerDevice.sign(payload)  // Your device-specific code
        val signature: ByteArray = TODO("Implement device signing")

        // 3. Get public key from your device
        // val publicKey = ledgerDevice.getPublicKey()  // Your device-specific code
        val publicKey: String = TODO("Get public key from device")  // G... address

        // 4. Return signature with public key
        return Auth.Signature(publicKey, signature)
    }
}

// Use custom signer with AssembledTransaction (recommended):
val client = ContractClient.forContract(
    server = SorobanServer("https://soroban-testnet.stellar.org"),
    network = Network.TESTNET,
    contractId = "CCXXX...",
    sourceAccount = "GSOURCE..."
)

val assembled = client.buildInvoke<Unit>(
    functionName = "transfer",
    parameters = mapOf("from" to "GFROM...", "to" to "GTO...", "amount" to 1000L),
    source = "GSOURCE..."
)

// Sign with custom hardware wallet signer
assembled.signAuthEntries(
    authEntriesSigner = KeyPair.fromAccountId("GHARDWARE..."),  // Public key only
    authorizeEntryDelegate = { entry, network ->
        Auth.authorizeEntry(entry, LedgerSigner(), currentLedger + 100, network)
    }
)

// Complete the transaction signing and submission
val sourceKeypair = KeyPair.fromSecretSeed("SSOURCE...")
assembled.signAndSubmit(sourceKeypair)
```

## Network Communication

This section covers communication patterns and protocols for interacting with the Stellar network, including real-time event streaming, RPC operations, and transaction submission.

### Streaming Events with SSE

Server-Sent Events (SSE) allow real-time monitoring of blockchain activity. Use streaming for building notification systems, live dashboards, or reactive applications.

```kotlin
// Initialize Horizon server
val server = HorizonServer("https://horizon-testnet.stellar.org")

// Stream transactions for an account
// Eliminates polling overhead - events arrive instantly as they happen on-chain
server.transactions()
    .forAccount("GABC...")
    .stream(
        serializer = TransactionResponse.serializer(),
        listener = object : EventListener<TransactionResponse> {
            override fun onEvent(data: TransactionResponse) {
                println("New transaction: ${data.hash}")
                println("Operations: ${data.operationCount}")
            }

            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: $error (HTTP $responseCode)")
            }
        }
    )

// Stream payments for an account
// Critical for payment processors requiring sub-second notification latency
server.payments()
    .forAccount("GABC...")
    .stream(
        serializer = OperationResponse.serializer(),
        listener = object : EventListener<OperationResponse> {
            override fun onEvent(data: OperationResponse) {
                when (data) {
                    is PaymentOperationResponse -> {
                        // Native XLM payments have null assetCode
                        val asset = data.assetCode ?: "XLM"
                        println("Payment received: ${data.amount} $asset")
                        println("From: ${data.from}")
                    }
                    is CreateAccountOperationResponse -> {
                        println("Account funded: ${data.startingBalance} XLM")
                    }
                }
            }

            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: $error (HTTP $responseCode)")
            }
        }
    )

// Stream operations for an account
// Captures all account activity - trustlines, offers, payments, contract calls
server.operations()
    .forAccount("GABC...")
    .stream(
        serializer = OperationResponse.serializer(),
        listener = object : EventListener<OperationResponse> {
            override fun onEvent(data: OperationResponse) {
                println("New operation: ${data.type}")
            }

            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: $error (HTTP $responseCode)")
            }
        }
    )

// Stream effects for an account
// Tracks granular state changes (balance updates, signer changes, trust authorized)
server.effects()
    .forAccount("GABC...")
    .stream(
        serializer = EffectResponse.serializer(),
        listener = object : EventListener<EffectResponse> {
            override fun onEvent(data: EffectResponse) {
                println("Effect: ${data.type}")
            }

            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: $error (HTTP $responseCode)")
            }
        }
    )

// Stream ledger closes
// Essential for network monitoring, validator tracking, and protocol upgrade detection
server.ledgers()
    .stream(
        serializer = LedgerResponse.serializer(),
        listener = object : EventListener<LedgerResponse> {
            override fun onEvent(data: LedgerResponse) {
                println("New ledger: ${data.sequence}")
                println("Successful transactions: ${data.successfulTransactionCount}")
            }

            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: $error (HTTP $responseCode)")
            }
        }
    )
```

### Soroban RPC Operations

```kotlin
val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")

// Check server health before making requests to verify connectivity and sync status
val health = sorobanServer.getHealth()
println("Status: ${health.status}")

// Build a Soroban transaction (e.g., invoke contract)
val sourceKeypair = KeyPair.fromSecretSeed("SXXX...")
val sourceAccount = sorobanServer.getAccount(sourceKeypair.getAccountId())
val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
    .addOperation(
        InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = "CCREATE...",
            functionName = "transfer",
            parameters = listOf(
                Scv.toAddress("GFROM..."),
                Scv.toAddress("GTO..."),
                Scv.toInt128(BigInteger.valueOf(1000))
            )
        )
    )
    .build()

// Simulate transaction to calculate resource requirements and preview results
// Simulation is required for ALL Soroban transactions to determine:
// - CPU/memory/storage footprint (prevents out-of-resources failures)
// - Authorization entries needed (for multi-party contracts)
// - Estimated resource fees (critical for budgeting)
val simulation = sorobanServer.simulateTransaction(transaction)

if (simulation.error == null) {
    // Prepare transaction by applying simulation results (footprint, auth, fees)
    // This updates the transaction to include all resource requirements
    val preparedTx = sorobanServer.prepareTransaction(transaction, simulation)

    // Sign and submit
    preparedTx.sign(sourceKeypair)
    val response = sorobanServer.sendTransaction(preparedTx)
} else {
    println("Simulation failed: ${simulation.error}")
}

// Get contract data (useful for reading contract state without invoking functions)
// Prefer PERSISTENT durability for critical data (tokens, ownership)
// Use TEMPORARY for cache-like data (can be archived if not accessed)
val balanceKey = Scv.toSymbol("balance")
val data = sorobanServer.getContractData(
    contractId = "CCREATE...",
    key = balanceKey,
    durability = SorobanServer.Durability.PERSISTENT
)

// Query events (essential for monitoring contract activity and debugging)
// Filter by contract IDs to reduce noise and improve query performance
// Use topics to match specific event types (e.g., "transfer", "mint")
val events = sorobanServer.getEvents(
    GetEventsRequest(
        startLedger = 1000,
        filters = listOf(
            EventFilter(
                contractIds = listOf("CCREATE..."),  // List, not Set
                topics = listOf(listOf(Scv.toSymbol("transfer").toXdrBase64()))
            )
        )
    )
)

events.events.forEach { event ->
    println("Event: ${event.topic}")
    println("Data: ${event.value}")
}
```

### Transaction Submission

```kotlin
// Build and sign a transaction first
val sourceAccount = server.loadAccount(sourceKeypair.getAccountId())
val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
    .addOperation(
        PaymentOperation(
            destination = "GDEST...",
            asset = Asset.NATIVE,
            amount = "10.0"
        )
    )
    .setBaseFee(100)
    .build()

// Sign the transaction
transaction.sign(sourceKeypair)

// Submit synchronously (waits for transaction to be included in ledger)
// Use this when you need immediate confirmation that the transaction was applied
val response = server.submitTransaction(transaction.toEnvelopeXdrBase64())
if (response.successful) {
    println("TX Hash: ${response.hash}")
    println("Ledger: ${response.ledger}")
} else {
    // Transaction failed validation or execution
    println("Failed: ${response.resultXdr}")
}

// Submit asynchronously (returns immediately after Stellar Core acceptance)
// Use this for fire-and-forget scenarios or when you want to poll separately
// Faster response time but requires polling to confirm ledger inclusion
val asyncResponse = server.submitTransactionAsync(transaction.toEnvelopeXdrBase64())
println("TX Hash: ${asyncResponse.hash}")
println("Status: ${asyncResponse.txStatus}")

// Poll for transaction completion when using async submission
// Continue polling until transaction is included in a ledger
var status = asyncResponse.txStatus
while (status == SubmitTransactionAsyncResponse.TransactionStatus.PENDING) {
    delay(1000)  // Wait 1 second between polls
    val result = server.transactions().transaction(asyncResponse.hash)
    if (result.successful) {
        println("Transaction applied in ledger ${result.ledger}")
        break
    } else {
        println("Transaction failed: ${result.resultXdr}")
        break
    }
}

// Fee bump for stuck transactions due to insufficient fees
// Use this when network fees spike or you need to prioritize a pending transaction
// The fee source account pays the additional fee (can be different from original source)
val feeSourceKeypair = KeyPair.random()  // Account that will pay the fee increase
val feeBump = FeeBumpTransaction.createWithBaseFee(
    feeSource = feeSourceKeypair.getAccountId(),
    baseFee = 1000,  // Must be higher than original transaction's base fee
    innerTransaction = transaction
)
// Fee bump requires signature from the fee source account
feeBump.sign(feeSourceKeypair)
server.submitTransaction(feeBump.toEnvelopeXdrBase64())
```

## Assets

### Creating and Using Assets

```kotlin
// Native asset (XLM)
val xlm = AssetTypeNative

// Create custom asset
val usdc = Asset.createNonNativeAsset(
    "USDC",
    "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
)

// Parse from canonical string
val asset = Asset.create("USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")

// Get canonical representation
println(usdc.toString())  // "USDC:GA5Z..."
// Asset codes: 1-12 uppercase A-Z characters or digits 0-9

// Check asset type
when (asset) {
    is AssetTypeNative -> println("Native XLM")
    is AssetTypeCreditAlphaNum4 -> println("Short code: ${asset.code}")
    is AssetTypeCreditAlphaNum12 -> println("Long code: ${asset.code}")
}
```

### Stellar Asset Contracts (SAC)

Stellar Asset Contracts (SAC) wrap classic Stellar assets (XLM, issued assets) as Soroban smart contracts, enabling them to be used in smart contract interactions.

**Benefits:**
- Use classic assets in smart contracts without manual bridging
- Access to standard token interface (SEP-41) for consistent operations
- Interoperability between classic Stellar operations and Soroban contracts
- Built-in authorization and compliance features

**When to use:**
- Integrating classic assets with Soroban contracts (DeFi, AMMs, etc.)
- Building dApps that need both classic and smart contract token operations
- Leveraging the standardized token interface for multi-asset support

```kotlin
// Get contract ID for Stellar Asset Contract
// This derives a deterministic contract address for the asset on the specified network
val contractId = usdc.getContractId(Network.TESTNET)
println("SAC Contract ID: $contractId")

// Create a ContractClient to interact with the SAC
// The client automatically loads the contract spec for type-safe operations
val sacClient = ContractClient.forContract(
    contractId = contractId,
    rpcUrl = "https://soroban-testnet.stellar.org",
    network = Network.TESTNET
)

// Define the account address to query
// This is the Stellar account (G... address) to check balance for
val accountAddress = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

// Call standard token interface methods (SEP-41)
// The SAC implements the standard token interface, providing consistent methods
// across all Stellar Asset Contracts (balance, transfer, approve, etc.)

// Query the token balance for an account
// Uses funcResToNative for automatic type conversion from XDR to native types
val balance = sacClient.invoke<BigInteger>(
    functionName = "balance",
    arguments = mapOf("id" to accountAddress),
    source = accountAddress,
    signer = null,  // Read-only operation, no signature needed
    parseResultXdrFn = { resultXdr ->
        // Parse the i128 result to BigInteger using SDK helper
        sacClient.funcResToNative("balance", resultXdr) as BigInteger
    }
)

// Get the token's name
val name = sacClient.invoke<String>(
    functionName = "name",
    arguments = emptyMap(),  // name() takes no arguments
    source = accountAddress,
    signer = null,  // Read-only operation
    parseResultXdrFn = { resultXdr ->
        // Parse string result using SDK helper
        sacClient.funcResToNative("name", resultXdr) as String
    }
)

println("Token: $name, Balance: $balance")

// Note: For write operations (transfer, mint, burn), provide a signer:
// val transferResult = sacClient.invoke<Unit>(
//     functionName = "transfer",
//     arguments = mapOf(
//         "from" to sourceAddress,
//         "to" to destinationAddress,
//         "amount" to BigInteger.valueOf(1000000)
//     ),
//     source = sourceAddress,
//     signer = sourceKeypair,  // Required for write operations
//     parseResultXdrFn = { /* parse result */ }
// )
```

---

**Navigation**: [← Architecture](architecture.md) | [Advanced Topics →](advanced.md)