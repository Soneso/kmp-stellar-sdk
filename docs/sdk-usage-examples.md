# SDK Usage Examples

> **Complete API Documentation**: For detailed method signatures, parameters, and return types, see the [auto-generated API reference](https://soneso.github.io/kmp-stellar-sdk/api/latest/).

This guide provides practical code examples for common Stellar SDK operations. Each section demonstrates real-world usage patterns to help you integrate the SDK into your application.

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.*
import com.soneso.stellar.sdk.horizon.requests.*
import com.soneso.stellar.sdk.horizon.responses.*
import com.soneso.stellar.sdk.horizon.responses.operations.*
import com.soneso.stellar.sdk.horizon.responses.effects.*
import com.soneso.stellar.sdk.horizon.exceptions.*
import com.soneso.stellar.sdk.contract.*
import com.soneso.stellar.sdk.rpc.*
import com.soneso.stellar.sdk.rpc.requests.*
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.Asset
import com.soneso.stellar.sdk.Price
import com.soneso.stellar.sdk.LiquidityPool
import com.soneso.stellar.sdk.Claimant
```

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
  - [Spec-less conversion with toNative](#spec-less-conversion-with-tonative)
  - [Authorization](#authorization)
  - [Contract Bindings](#contract-bindings)
- [Network Communication](#network-communication)
  - [Streaming Events with SSE](#streaming-events-with-sse)
  - [Soroban RPC Operations](#soroban-rpc-operations)
  - [Transaction Submission](#transaction-submission)
- [Assets](#assets)
  - [Creating and Using Assets](#creating-and-using-assets)
  - [Stellar Asset Contracts (SAC)](#stellar-asset-contracts-sac)
    - [Opening a SAC Client](#opening-a-sac-client)
    - [Read-Only Client](#read-only-client)
    - [Reading a Balance](#reading-a-balance)
    - [Sending a Transfer](#sending-a-transfer)
    - [Transfers Authorized by Another Account](#transfers-authorized-by-another-account)
    - [Deploying a SAC](#deploying-a-sac)

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
    val account = server.accounts().account("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")
    println("Sequence: ${account.sequenceNumber}")
    println("Balances: ${account.balances.map { "${it.balance} ${it.assetCode}" }}")
} catch (e: BadRequestException) {
    // Account doesn't exist yet - fund it first using Friendbot or CreateAccountOperation
    println("Account not found")
}
```

### Funding Testnet Accounts

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Fund account using Friendbot (testnet only)
val keypair = KeyPair.random()
val accountId = keypair.getAccountId()

val success = FriendBot.fundTestnetAccount(accountId)
if (success) {
    println("Account funded with 10,000 XLM")

    // Now the account exists and its details can be read
    val details = server.accounts().account(accountId)
    println("Balance: ${details.balances.first().balance}")
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
            destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
            amount = "100.50",
            asset = AssetTypeNative
        )
    )
    .addMemo(MemoText("Payment for services"))
    .build()

// Sign and submit
transaction.sign(keypair)
val response = server.submitTransaction(transaction.toEnvelopeXdrBase64())
```

### Multi-Operation Transactions

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
val keypair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
val account = server.loadAccount(keypair.getAccountId())
// Multi-operation transaction for complete account onboarding
// All operations succeed together or fail together (atomic)
val newAccountKeypair = KeyPair.random()
val usdcAsset = Asset.createNonNativeAsset("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")

val transaction = TransactionBuilder(account, Network.TESTNET)
    .addOperations(listOf(
        // 1. Create the account with sufficient XLM balance
        CreateAccountOperation(newAccountKeypair.getAccountId(), "5"),
        // 2. Establish trustline for USDC (requires the account to exist)
        ChangeTrustOperation(asset = usdcAsset).apply {
            sourceAccount = newAccountKeypair.getAccountId()
        },
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

// Submit transaction to Horizon; a rejected transaction raises BadRequestException
try {
    val response = server.submitTransaction(transaction.toEnvelopeXdrBase64())
    println("Success! Hash: ${response.hash}")
} catch (e: BadRequestException) {
    println("Failed: ${e.message}")
}
```

## Operations

### Payment Operations

Payment operations transfer assets between accounts. Add these to a TransactionBuilder to execute them.

```kotlin
// Define assets used in examples
val usdcAsset = Asset.createNonNativeAsset("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
val eurocAsset = Asset.createNonNativeAsset("EUROC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")

// Simple XLM payment
PaymentOperation(
    destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
    amount = "100",
    asset = AssetTypeNative
)

// Custom asset payment
PaymentOperation(
    destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
    amount = "50",
    asset = usdcAsset
)

// Path payment: send exactly 100 XLM, receive at least 95 USDC (converted via EUROC)
PathPaymentStrictSendOperation(
    sendAsset = AssetTypeNative,
    sendAmount = "100",
    destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
    destAsset = usdcAsset,
    destMin = "95",  // Accept minimum 95 USDC
    path = listOf(eurocAsset)  // Through EUROC
)

// Path payment: receive exactly 100 USDC, send at most 105 XLM
PathPaymentStrictReceiveOperation(
    sendAsset = AssetTypeNative,
    sendMax = "105",  // Send maximum 105 XLM
    destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
    destAsset = usdcAsset,
    destAmount = "100",  // Receive exactly 100 USDC
    path = listOf()
)
```

### Account Operations

```kotlin
// Create new account (funds transferred from source account)
CreateAccountOperation(
    destination = "GDWUSKGGFDI4FRXK5EBTRECZSVQSSWJHHJOGH6JWG3AUMFFMQ435DIAG",
    startingBalance = "10"  // 10 XLM minimum
)

// Manually increment sequence number (useful for transaction coordination)
BumpSequenceOperation(
    bumpTo = 12345678
)

// Configure account settings (multi-signature and security)
SetOptionsOperation(
    homeDomain = "stellar.example.com",
    masterKeyWeight = 20,
    lowThreshold = 5,
    mediumThreshold = 10,
    highThreshold = 15,
    signer = SignerKey.ed25519PublicKey("GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI"),
    signerWeight = 10
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
    destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
)
```

### Asset Operations

```kotlin
// Define asset used in examples
val usdcAsset = Asset.createNonNativeAsset("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")

// Establish trustline to receive custom asset (required before receiving USDC)
ChangeTrustOperation(
    asset = usdcAsset,
    limit = "10000"  // Maximum 10,000 USDC
)

// Remove trustline (set limit to 0, account must have zero balance)
ChangeTrustOperation(
    asset = usdcAsset,
    limit = "0"
)

// Issuer authorizes trustline (issuer can control who holds their asset)
SetTrustLineFlagsOperation(
    trustor = "GCFIOX77D2ZYIUKXPLGVV7XEAVCWK2G5PSE6BEEGHICVPPD26SPRPPVB",
    asset = usdcAsset,
    setFlags = TrustLineFlagsXdr.AUTHORIZED_FLAG.value,
    clearFlags = TrustLineFlagsXdr.AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG.value
)
```

### Trading Operations

```kotlin
// usdcAsset: from the previous steps of this flow
// Create sell offer on DEX (sell 100 XLM for USDC at 0.20 USDC per XLM)
ManageSellOfferOperation(
    selling = AssetTypeNative,
    buying = usdcAsset,
    amount = "100",
    price = Price.fromString("0.20")
)

// Create buy offer (receive exactly 50 USDC, price is maximum willing to pay)
ManageBuyOfferOperation(
    selling = AssetTypeNative,
    buying = usdcAsset,
    buyAmount = "50",
    price = Price.fromString("0.20")
)

// Create passive offer (won't immediately match existing offers, useful for market making)
CreatePassiveSellOfferOperation(
    selling = AssetTypeNative,
    buying = usdcAsset,
    amount = "100",
    price = Price.fromString("0.20")
)

// Update existing offer (change price or amount)
ManageSellOfferOperation(
    selling = AssetTypeNative,
    buying = usdcAsset,
    amount = "150",  // New amount
    price = Price.fromString("0.25"),  // New price
    offerId = 12345  // ID of offer to update
)

// Cancel offer (set amount to 0)
ManageSellOfferOperation(
    selling = AssetTypeNative,
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
    asset = AssetTypeNative,
    claimants = listOf(
        // Immediate claim allowed
        Claimant(
            destination = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP",
            predicate = ClaimPredicate.Unconditional
        ),
        // Can claim within 1 hour from balance creation (expires after)
        Claimant(
            destination = "GCUZ6YLL5RQBTYLTTQLPCM73C5XAIUGK2TIMWQH7HPSGWVS2KJ2F3CHS",
            predicate = ClaimPredicate.BeforeRelativeTime(3600)
        )
    )
)

// Recipient claims the balance (if predicate conditions are met)
ClaimClaimableBalanceOperation(
    balanceId = "000000003f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a"
)

// Issuer reclaims unclaimed balance (requires CLAWBACK_ENABLED flag on asset)
ClawbackClaimableBalanceOperation(
    balanceId = "000000003f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a"
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
val usdcAsset = Asset.createNonNativeAsset("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
val eurocAsset = Asset.createNonNativeAsset("EUROC", "GBAW5XGWORWVFE2XTJYDTLDHXTY2Q2MO73HYCGB3XMFMQ562Q2W2GJQX")

// Create the pool object. assetA < assetB in stellar-core's order: native first, then
// type (alphanum4 before alphanum12), then code bytes, then the issuer's raw key bytes
val liquidityPool = LiquidityPool(
    assetA = usdcAsset,  // LiquidityPool rejects a reversed pair
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
val userKeypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val userAccount = server.loadAccount(userKeypair.getAccountId())

// Step 1: Create trustline for pool shares
val trustlineTx = TransactionBuilder(userAccount, Network.TESTNET)
    .addOperation(poolTrustlineOp)
    .build()
trustlineTx.sign(userKeypair)
server.submitTransaction(trustlineTx.toEnvelopeXdrBase64())

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
server.submitTransaction(depositTx.toEnvelopeXdrBase64())

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
val horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
val sponsorSequence = 1L // current sequence of the sponsor account
val issuerId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val userKeypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val userId = userKeypair.getAccountId()

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
import com.ionspin.kotlin.bignum.integer.BigInteger
val sourceKeypair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")

// Example: Invoke contract function (low-level approach)
// For simpler workflows, use ContractClient API (see Smart Contracts section)


val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")

// 1. Create operation
val invokeOp = InvokeHostFunctionOperation.invokeContractFunction(
    contractAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
    functionName = "transfer",
    parameters = listOf(
        Scv.toAddress(Address("GCFIRY65OQE7DFP5KLNS2PF2LVZMUZYJX4OZIEQ36N2IQANUB5XVYOJR").toSCAddress()),
        Scv.toAddress(Address("GCATS5YOVB6ROX2WUNKGNQ2MP3GMXDMKSG2O4N5CLX3A6W4PZGZZI55U").toSCAddress()),
        Scv.toInt128(BigInteger.fromLong(1000))
    )
)

// 2. Build transaction (SorobanServer has getAccount method)
val sourceAccountId = sourceKeypair.getAccountId()
val account = sorobanServer.getAccount(sourceAccountId)
val transaction = TransactionBuilder(account, Network.TESTNET)
    .addOperation(invokeOp)
    .build()

// 3. Simulate to check the invocation succeeds (REQUIRED for Soroban)
val simulationResult = sorobanServer.simulateTransaction(transaction)
if (simulationResult.error != null) {
    throw Exception("Simulation failed: ${simulationResult.error}")
}

// 4. Prepare: applies footprint, resource fees, and auth from simulation
val prepared = sorobanServer.prepareTransaction(transaction)

// 5. Sign and submit
prepared.sign(sourceKeypair)
val response = sorobanServer.sendTransaction(prepared)
println("Transaction hash: ${response.hash}")
```

Other Soroban operations:

```kotlin
// Upload contract WASM (use ByteArray for cross-platform compatibility)
val wasmBytes: ByteArray = java.io.File("contract.wasm").readBytes() // JVM; use your platform's file APIs elsewhere
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

Horizon provides APIs for querying blockchain data. This section demonstrates common query patterns for retrieving accounts, transactions, operations, and more.

```kotlin
// Initialize Horizon server (reuse this instance across queries)
```

### Account Queries

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Load specific account with full details (balances, signers, thresholds)
val account = server.accounts().account("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")
println("Sequence: ${account.sequenceNumber}")
println("Balances: ${account.balances.map { "${it.balance} ${it.assetCode}" }}")

// Query multiple accounts with filters (useful for discovering sponsored accounts or asset holders)
val accounts = server.accounts()
    .forSponsor("GBUCAAMD7DYS7226CWUUOZ5Y2QF4JBJWIYU3UWJAFDGJVCR6EU5NJM5H")  // Find accounts sponsored by this address
    .forAsset("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")  // Find USDC holders (separate parameters: code, issuer)
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
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Get specific transaction by hash (useful for checking transaction status after submission)
val transaction = server.transactions().transaction("abc123...")
println("Result: ${transaction.successful}")
println("Fee charged: ${transaction.feeCharged}")

// Query transactions with filters (useful for transaction history, auditing, monitoring)
val transactions = server.transactions()
    .forAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")  // All transactions involving this account
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
    .forClaimableBalance("000000003f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a")
    .execute()

// Get transactions for a liquidity pool (track deposits/withdrawals)
val poolTxs = server.transactions()
    .forLiquidityPool("abc123...")
    .execute()
```

### Operation Queries

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Get specific operation by ID
val operation = server.operations().operation(12345678)
println("Type: ${operation.type}")

// Query operations with filters (useful for tracking specific actions, building activity feeds)
val operations = server.operations()
    .forAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")  // All operations involving this account
    .limit(50)
    .order(RequestBuilder.Order.DESC)
    .execute()

operations.records.forEach { op ->
    when (op) {
        is PaymentOperationResponse -> {
            println("Payment: ${op.amount} ${op.assetCode ?: "XLM"} to ${op.to}")
        }
        is CreateAccountOperationResponse -> {
            println("Account created: ${op.account} with ${op.startingBalance} XLM")
        }
        else -> {}
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
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Query effects for an account (useful for detailed activity tracking, notifications)
// Effects show the specific changes that occurred (e.g., balance changes, trustline changes)
val effects = server.effects()
    .forAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")
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
// transactionCount: from the previous steps of this flow
val server = HorizonServer("https://horizon-testnet.stellar.org")
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
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Query payments for an account (useful for payment history, accounting)
// Payment queries return only payment-related operations (Payment, PathPayment, CreateAccount, AccountMerge)
val payments = server.payments()
    .forAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")
    .limit(50)
    .order(RequestBuilder.Order.DESC)
    .execute()

payments.records.forEach { payment ->
    when (payment) {
        is PaymentOperationResponse -> {
            println("Payment: ${payment.amount} ${payment.assetCode ?: "XLM"}")
            println("From: ${payment.from}, To: ${payment.to}")
        }
        is CreateAccountOperationResponse -> {
            println("Account funded: ${payment.startingBalance} XLM")
        }
        else -> { /* other operation kinds */ }
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
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Query trades for an account (useful for trading history, PnL calculation)
val trades = server.trades()
    .forAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")
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
    .forCounterAsset("credit_alphanum4", "USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")  // USDC as counter
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
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Query assets with filters (useful for discovering tradeable assets)
val assets = server.assets()
    .forAssetCode("USDC")  // All USDC assets from different issuers
    .limit(20)
    .execute()

assets.records.forEach { asset ->
    println("Asset: ${asset.assetCode} issued by ${asset.assetIssuer}")
    println("Authorized holders: ${asset.accounts.authorized}")
}

// Query assets by issuer (find all assets from a specific issuer)
val issuerAssets = server.assets()
    .forAssetIssuer("GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
    .execute()

// Query assets by code and issuer (specific asset lookup)
val specificAsset = server.assets()
    .forAssetCode("USDC")
    .forAssetIssuer("GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
    .execute()
```

### Order Book Queries

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Get order book for asset pair (useful for DEX trading, price discovery)
val usdcAsset = Asset.createNonNativeAsset("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
val orderBook = server.orderBook()
    .sellingAsset(AssetTypeNative)  // Selling XLM
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
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Find payment paths (strict send) - know how much you're sending, discover destinations
val sendPaths = server.strictSendPaths()
    .sourceAsset("native")  // Sending XLM
    .sourceAmount("100")  // Sending exactly 100 XLM
    .destinationAccount("GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM")  // To this account
    .execute()

sendPaths.records.forEach { path ->
    println("Destination asset: ${path.destinationAssetCode}")
    println("Destination amount: ${path.destinationAmount}")
    println("Path: ${path.path.joinToString()}")
}

// Find payment paths with specific destination assets (useful for multi-currency scenarios)
val multiAssetPaths = server.strictSendPaths()
    .sourceAsset("credit_alphanum4", "USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
    .sourceAmount("100")
    .destinationAssets(listOf(
        Triple("credit_alphanum4", "EUROC", "GBAW5XGWORWVFE2XTJYDTLDHXTY2Q2MO73HYCGB3XMFMQ562Q2W2GJQX"),
        Triple("credit_alphanum4", "GBPT", "GBBHQ7H4V6RRORKYLHTCAWP6MOHNORRFJSDPXDFYDGJB2LPZUFPXUEW3")
    ))
    .execute()

// Find payment paths (strict receive) - know how much you want to receive, discover costs
val receivePaths = server.strictReceivePaths()
    .sourceAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")  // From this account
    .destinationAsset("credit_alphanum4", "EUROC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")  // Receiving EUROC
    .destinationAmount("50")  // Receiving exactly 50 EUROC
    .execute()

receivePaths.records.forEach { path ->
    println("Source asset: ${path.sourceAssetCode}")
    println("Source amount: ${path.sourceAmount}")
    println("Path: ${path.path.joinToString()}")
}

// Find paths with specific source assets (useful for multi-currency wallets)
val multiSourcePaths = server.strictReceivePaths()
    .sourceAssets(listOf(
        Triple("native", null, null),
        Triple("credit_alphanum4", "USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
    ))
    .destinationAsset("credit_alphanum4", "EUROC", "GBAW5XGWORWVFE2XTJYDTLDHXTY2Q2MO73HYCGB3XMFMQ562Q2W2GJQX")
    .destinationAmount("100")
    .execute()
```

### Claimable Balance Queries

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Query claimable balances with filters (useful for finding pending payments, airdrops)
val claimableBalances = server.claimableBalances()
    .forClaimant("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")  // Balances this account can claim
    .limit(20)
    .execute()

claimableBalances.records.forEach { balance ->
    println("Balance ID: ${balance.id}")
    println("Asset: ${balance.assetString}, Amount: ${balance.amount}")
    println("Sponsor: ${balance.sponsor}")
}

// Query by sponsor (find balances sponsored by this account)
val sponsoredBalances = server.claimableBalances()
    .forSponsor("GBUCAAMD7DYS7226CWUUOZ5Y2QF4JBJWIYU3UWJAFDGJVCR6EU5NJM5H")
    .execute()

// Query by asset (find all claimable balances for a specific asset)
val assetBalances = server.claimableBalances()
    .forAsset("credit_alphanum4", "USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")  // Asset type, code, issuer
    .execute()

// Get specific claimable balance by ID
val balance = server.claimableBalances().claimableBalance("000000003f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a")
println("Amount: ${balance.amount}, Claimants: ${balance.claimants.size}")
```

### Liquidity Pool Queries

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
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
val usdcAsset = Asset.createNonNativeAsset("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
val usdcPools = server.liquidityPools()
    .forReserves("native", "USDC:GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")  // XLM/USDC pools
    .execute()

// Query pools by account (find pools an account participates in)
val accountPools = server.liquidityPools()
    .forAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")
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
import com.ionspin.kotlin.bignum.integer.BigInteger

// Loads contract spec from network for automatic type conversion and validation
val client = ContractClient.forContract(
    contractId = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
    rpcUrl = "https://soroban-testnet.stellar.org",
    network = Network.TESTNET
)

// Read-only call - Option 1: Custom result parser
// Use when you need specific type conversion or custom parsing logic
val balance = client.invoke<BigInteger>(
    functionName = "balance",
    arguments = mapOf("id" to "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"),  // SDK auto-converts native types to XDR
    source = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54",
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
    arguments = mapOf("id" to "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"),
    source = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54",
    signer = null
)
val parsedBalance = client.funcResToNative("balance", balanceXdr) as BigInteger
println("Balance: $parsedBalance")

// Write operation with native types (auto-signs and submits)
val sourceAccount = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")  // Source account keypair
client.invoke<Unit>(
    functionName = "transfer",
    arguments = mapOf(
        "from" to "GCFIRY65OQE7DFP5KLNS2PF2LVZMUZYJX4OZIEQ36N2IQANUB5XVYOJR",
        "to" to "GCATS5YOVB6ROX2WUNKGNQ2MP3GMXDMKSG2O4N5CLX3A6W4PZGZZI55U",
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
// client: from the previous steps of this flow
// Initialize accounts and keypairs
val sourceKeypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val fromAddress = "GCFIRY65OQE7DFP5KLNS2PF2LVZMUZYJX4OZIEQ36N2IQANUB5XVYOJR"
val toAddress = "GCATS5YOVB6ROX2WUNKGNQ2MP3GMXDMKSG2O4N5CLX3A6W4PZGZZI55U"
val account1Keypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val account1Id = account1Keypair.getAccountId()
val account2Keypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
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
val deployer = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")  // Deployer account keypair

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
| `SCV_TIMEPOINT` | `ULong` | `1700000000UL` |
| `SCV_DURATION` | `ULong` | `3600UL` |
| `SCV_U128` | `BigInteger` | `BigInteger.parseString("123456789")` |
| `SCV_I128` | `BigInteger` | `BigInteger.parseString("123456789")` |
| `SCV_U256` | `BigInteger` | `BigInteger.parseString("340282366920938463463374607431768211456")` |
| `SCV_I256` | `BigInteger` | `BigInteger.parseString("340282366920938463463374607431768211456")` |
| `SCV_BYTES` | `ByteArray` | `byteArrayOf(1, 2, 3)` |
| `SCV_STRING` | `String` | `"hello"` |
| `SCV_SYMBOL` | `String` | `"symbol"` |
| `SCV_VEC` | `List<Any?>` | `listOf(1, 2, 3)` |
| `SCV_MAP` | `Map<*, *>` | `mapOf("key" to "value")` |
| `SCV_ADDRESS` | `String` (`G...`, `C...`) or `Address`; a `MuxedAddress` parameter also takes `M...` and `W...` | `"GABC..."` |
| `SCV_VOID` | `null` | `null` |

As arguments, the 64-bit unsigned, timepoint, duration, 128-bit, and 256-bit types also take an `Int`, a `Long`, a `BigInteger`, a `ULong`, a decimal `String`, or a whole `Double` or `Float`. A fractional, NaN, infinite, or out-of-range value throws `ContractSpecException`, which `invoke` wraps in `IllegalArgumentException`.

**Manual conversion examples:**

```kotlin
import com.ionspin.kotlin.bignum.integer.BigInteger

// Convert native arguments to XDR (useful for low-level operations or when bypassing convenience layer)
// Address parameters take G... or C... strkeys or an Address; MuxedAddress parameters also M... and W...
val xdrArgs = client.funcArgsToXdrSCValues(
    functionName = "transfer",
    arguments = mapOf(
        "from" to "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54",
        "to" to "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
        "amount" to 1000L
    )
)

// Parse XDR results to native types (inverse of funcArgsToXdrSCValues for bidirectional conversion)
val resultXdr = client.invoke<SCValXdr>(
    functionName = "balance",
    arguments = mapOf("id" to "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"),
    source = sourceAccount.getAccountId(),
    signer = null
)
val balance = client.funcResToNative("balance", resultXdr) as BigInteger

// Create contract values manually using low-level Scv API
// This is useful for custom types, low-level control, or performance-critical code where
// you need direct XDR manipulation without automatic conversion
val addressScVal = Address("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54").toSCVal()  // Convert string address to SCValXdr
val params = listOf(
    addressScVal,                            // Account address
    Scv.toUint128(BigInteger.parseString("1000000")),   // Amount
    Scv.toSymbol("transfer"),               // Method name
    Scv.toBoolean(true),                    // Flag
    Scv.toVec(listOf(                       // Array
        Scv.toUint32(1u),
        Scv.toUint32(2u)
    )),
    Scv.toMap(linkedMapOf(                  // Map, emitted in the Soroban host's key order
        Scv.toSymbol("key") to Scv.toString("value")
    ))
)

// Parse complex results with type-safe discriminated unions
// This pattern prevents runtime errors by ensuring you handle the correct SCValXdr type
val dataXdr = client.invoke<SCValXdr>(
    functionName = "get_data",
    arguments = mapOf("key" to "user_123"),
    source = sourceAccount.getAccountId(),
    signer = null
)
when (dataXdr.discriminant) {
    SCValTypeXdr.SCV_U128 -> {
        val num = Scv.fromUint128(dataXdr)
        println("Number: $num")
    }
    SCValTypeXdr.SCV_STRING -> {
        val str = Scv.fromString(dataXdr)
        println("String: $str")
    }
    SCValTypeXdr.SCV_VEC -> {
        val vec = Scv.fromVec(dataXdr)
        println("Array size: ${vec.size}")
    }
    else -> println("Unexpected type: ${dataXdr.discriminant}")
}
```

### Spec-less conversion with toNative

`SCValXdr.toNative()` converts a value tree to native Kotlin values without a contract spec. It is an opt-in extension function in `com.soneso.stellar.sdk.scval`. The conversion never throws: every value has a defined result, and a value with no native representation comes back as the `SCValXdr` itself (the same instance), so a caller detects that case with `is SCValXdr`.

Reach for `toNative()` when no spec is at hand: contract event values, ledger entries, simulation results, or a quick look at a value during development. `funcResToNative` is its companion for results whose contract spec is available, since the spec lets it reconstruct structs, unions and enums. The two produce different shapes: a map becomes a Kotlin `Map` here and a list of pairs on the spec path, and error, contract-instance and executable-tag values come back as the `SCValXdr` itself here, where the spec path unwraps their payloads.

**Conversion outcomes:**

| XDR Type | Result |
|----------|--------|
| `SCV_BOOL` | `Boolean` |
| `SCV_VOID` | `null` |
| `SCV_U32` / `SCV_I32` | `UInt` / `Int` |
| `SCV_U64`, `SCV_TIMEPOINT`, `SCV_DURATION` | `ULong` |
| `SCV_I64` | `Long` |
| `SCV_U128`, `SCV_I128`, `SCV_U256`, `SCV_I256` | `BigInteger` (`com.ionspin.kotlin.bignum.integer`) |
| `SCV_BYTES` | `ByteArray` (the stored payload array, so writes to it go through to the value) |
| `SCV_STRING`, `SCV_SYMBOL` | `String`; the `SCValXdr` itself when the bytes are not valid UTF-8 |
| `SCV_ADDRESS` | strkey `String` (`G...`, `C...`, `M...`, `W...`, `B...` or `L...`); the `SCValXdr` itself when the address has no strkey form |
| `SCV_VEC` | `List<Any?>`, each element converted in order; an absent payload gives an empty list |
| `SCV_MAP` | `Map<Any?, Any?>` under the key rules below, or the `SCValXdr` itself; an absent payload gives an empty map |
| `SCV_ERROR`, `SCV_CONTRACT_INSTANCE`, `SCV_LEDGER_KEY_CONTRACT_INSTANCE`, `SCV_LEDGER_KEY_NONCE`, `SCV_EXECUTABLE_TAG` | the `SCValXdr` itself; `Scv.fromError`, `Scv.fromExecutableTagBytes` and the other `Scv.from*` accessors read the payload on request |

**Map keys:**

Map keys need value-based equality, which `ByteArray` and the XDR types do not provide, so keys convert under a narrower table than values:

| Key Type | Map Key |
|----------|---------|
| `SCV_SYMBOL`, `SCV_STRING` | `String` when the bytes are valid UTF-8 |
| `SCV_U32` / `SCV_I32` | `UInt` / `Int` |
| `SCV_U64`, `SCV_TIMEPOINT`, `SCV_DURATION` | `ULong` |
| `SCV_I64` | `Long` |
| `SCV_U128`, `SCV_I128`, `SCV_U256`, `SCV_I256` | `BigInteger` |
| `SCV_BOOL` | `Boolean` |
| `SCV_VOID` | `null` |
| `SCV_BYTES` | lowercase hex `String` |
| `SCV_ADDRESS` | strkey `String` |

Bytes are the one asymmetry between the two tables: a bytes value stays a `ByteArray`, while a bytes key becomes its lowercase hex `String` (`byteArrayOf(1, 2)` becomes the key `"0102"`). Addresses are strkey strings on both sides, so an address key is looked up with the same `G...`/`C...` string that an address value converts to.

Key equality is plain Kotlin `equals`/`hashCode` on the converted keys, and a key is looked up with the exact type the table gives: a u32 key with a `UInt`, a u64 key with a `ULong`, a 128-bit or 256-bit key with a `BigInteger`. The primitive integer types are never normalized against each other, so primitive keys of equal value from different arms are distinct: a u32 key `5` and a u64 key `5` are two entries, found with `5u` and `5uL`. Three kinds of key collide, and a map holding a collision falls back as described below:

- A wide-integer key and a primitive integer key of equal value: an i128 key `5` and a u64 key `5` are the same key, in either entry order. Keys of different value coexist, so an i128 key `-1` and a u64 key `18446744073709551615` are two entries.
- Wide-integer keys of equal value across arms: a u128 key `5` and an i256 key `5` are the same key.
- A bytes key and a symbol or string key spelling its hex: the bytes `[0x30, 0x31]` and the symbol `"3031"` are the same key.

A map converts to the `SCValXdr` itself as a whole when either

- any key is unrepresentable: a vec, map, error, contract instance, nonce key, executable tag or ledger-key-contract-instance key, a symbol or string key whose bytes are not valid UTF-8, or an address key with no strkey form; or
- two entries collide on equal converted keys.

A nested map that falls back is contained: the enclosing vec or map still converts, with that map left as an `SCValXdr` element. A converted map preserves the entry order of the XDR map.

**Examples:**

```kotlin
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.stellar.sdk.scval.toNative

// A u64 above Long.MAX_VALUE converts to a ULong and keeps its exact value
val count = Scv.toUint64(18446744073709551615uL).toNative()  // ULong 18446744073709551615

// A map with symbol keys converts to a Map keyed by String, in the host's key order
val owner = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
val record = Scv.toMap(
    linkedMapOf(
        Scv.toSymbol("name") to Scv.toString("Alice"),
        Scv.toSymbol("age") to Scv.toUint32(30u),
        Scv.toSymbol("balance") to Scv.toInt128(BigInteger.parseString("1000000000")),
        Scv.toSymbol("owner") to Scv.toAddress(Address(owner).toSCAddress())
    )
)
val fields = record.toNative() as Map<*, *>
val name = fields["name"] as String  // "Alice"
val age = fields["age"] as UInt  // 30u
val balance = fields["balance"] as BigInteger  // 1000000000
val ownerAddress = fields["owner"] as String  // the strkey held in owner
// fields.keys.toList() is ["age", "balance", "name", "owner"]

// A vec converts to a List with each element converted in turn
val items = Scv.toVec(
    listOf(Scv.toUint32(1u), Scv.toSymbol("a"), Scv.toVec(listOf(Scv.toBoolean(true))))
).toNative()  // [1u, "a", [true]]

// A map whose key has no native representation comes back as the SCValXdr itself
val keyedByVec = Scv.toMap(
    linkedMapOf(Scv.toVec(listOf(Scv.toUint32(1u))) to Scv.toUint32(2u))
)
val result = keyedByVec.toNative()
// result is SCValXdr, and it is the same instance as keyedByVec
```

### Authorization

Authorization entries provide cryptographic proof of consent for contract invocations. The SDK handles authorization automatically through `ContractClient.invoke()` and `AssembledTransaction.signAuthEntries()`. Use the `Auth` class directly only for advanced scenarios requiring manual control.

For complete authorization workflows (multi-signature, atomic swaps), see the [Advanced Contract Control (buildInvoke)](#advanced-contract-control-buildinvoke) section which demonstrates `AssembledTransaction.signAuthEntries()` usage.

> Authorization credentials have three address arms: `ADDRESS_V2` (the default, Protocol 27+, CAP-71), the legacy `ADDRESS` (valid on every network), and `ADDRESS_WITH_DELEGATES` (Protocol 27+, CAP-71). `Auth.authorizeInvocation` builds `ADDRESS_V2` by default and simulation requests it. On a network below Protocol 27, V2 entries invalidate the transaction, so request the legacy arm with `simulateTransaction(tx, useUpgradedAuth = false)` or `ClientOptions(useUpgradedAuth = false)` and build it with `Auth.authorizeInvocation(..., authV2 = false)`. Signing is the same whatever the arm; build delegate trees with `Auth.attachDelegates` + `DelegateDescriptor`. See [Advanced SDK Usage](advanced.md) for the delegate and multi-party signing flows.

The credential address, the `forAddress` target, the `authorizeInvocation` public key and every delegate are account (`G...`) or contract (`C...`) addresses. A muxed address throws `IllegalArgumentException` with the message `Muxed account (M...) and muxed contract (W...) addresses are not valid Soroban auth addresses: ` followed by the address, and `attachDelegates` names `delegate addresses` in place of `auth addresses`.

```kotlin
// Example 1: Sign a single authorization entry
// Use case: You have an auth entry from simulation and need to sign it manually
// (Typically you'd use AssembledTransaction.signAuthEntries() instead - see Advanced Contract Control section)
val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")
val currentLedger = sorobanServer.getLatestLedger().sequence

// entry: the SorobanAuthorizationEntryXdr returned by contract simulation
val entry: SorobanAuthorizationEntryXdr = TODO("take this from your simulation result")

val userKeypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")

// The first signature fixes the entry's expiration: a later signer passes the stored
// signatureExpirationLedger, and a different value throws IllegalArgumentException
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
val contractAddress = Address(contractId).toSCAddress()
val invocation = SorobanAuthorizedInvocationXdr(
    function = SorobanAuthorizedFunctionXdr.ContractFn(
        InvokeContractArgsXdr(
            contractAddress = contractAddress,
            functionName = SCSymbolXdr("transfer"),
            args = listOf(
                Scv.toAddress(Address("GCFIRY65OQE7DFP5KLNS2PF2LVZMUZYJX4OZIEQ36N2IQANUB5XVYOJR").toSCAddress()),
                Scv.toAddress(Address("GCATS5YOVB6ROX2WUNKGNQ2MP3GMXDMKSG2O4N5CLX3A6W4PZGZZI55U").toSCAddress()),
                Scv.toInt128(com.ionspin.kotlin.bignum.integer.BigInteger.fromLong(1000))
            )
        )
    ),
    subInvocations = emptyList()  // Nested contract calls if needed
)

val keypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
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
        // 1. Hash the XDR-encoded preimage
        val writer = XdrWriter()
        preimage.encode(writer)
        val payload = com.soneso.stellar.sdk.crypto.getSha256Crypto().hash(writer.toByteArray())

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
    contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC",
    rpcUrl = "https://soroban-testnet.stellar.org",
    network = Network.TESTNET
)

val assembled = client.buildInvoke<Unit>(
    functionName = "transfer",
    arguments = mapOf("from" to "GCFIRY65OQE7DFP5KLNS2PF2LVZMUZYJX4OZIEQ36N2IQANUB5XVYOJR", "to" to "GCATS5YOVB6ROX2WUNKGNQ2MP3GMXDMKSG2O4N5CLX3A6W4PZGZZI55U", "amount" to 1000L),
    source = "GDFJHLAXAUMHA4OWPOB4P7YO72AQR2HMIUYFOXLXE2DZGM633K7HZDQP",
    signer = null
)

// Sign with custom hardware wallet signer
assembled.signAuthEntries(
    authEntriesSigner = KeyPair.fromAccountId("GBXHUHG5FGYLPD6RHL2MKWMP572O6KUXCZXDZJXS4T57ZTMAKBN7DWXN"),  // Public key only
    authorizeEntryDelegate = { entry, network ->
        // signAuthEntries stamped the entry's expiration before calling this delegate
        val expiration = when (val credentials = entry.credentials) {
            is SorobanCredentialsXdr.Address -> credentials.value.signatureExpirationLedger
            is SorobanCredentialsXdr.AddressV2 -> credentials.value.signatureExpirationLedger
            is SorobanCredentialsXdr.AddressWithDelegates -> credentials.value.addressCredentials.signatureExpirationLedger
            SorobanCredentialsXdr.Void -> return@signAuthEntries entry
        }
        Auth.authorizeEntry(entry, LedgerSigner(), expiration.value.toLong(), network)
    }
)

// Complete the transaction signing and submission
val sourceKeypair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
assembled.signAndSubmit(sourceKeypair)
```

### Contract Bindings

Generate a typed Kotlin class from a contract's spec with [stellar-contract-bindings](https://github.com/lightsail-network/stellar-contract-bindings). The generated client has one method per contract function with native parameter and return types, plus a `build...Tx` variant that returns an `AssembledTransaction` for the [multi-signature workflow](#advanced-contract-control-buildinvoke).

```bash
pip install stellar-contract-bindings

stellar-contract-bindings kmp \
  --contract-id YOUR_CONTRACT_ID \
  --rpc-url https://soroban-testnet.stellar.org \
  --output ./generated \
  --package com.example.bindings \
  --class-name TokenContract
```

Or use the [web interface](https://stellar-contract-bindings.fly.dev/).

The generated file needs kmp-stellar-sdk 1.9.0 or later. Function and parameter names are the contract spec's names in camelCase (`expiration_ledger` becomes `expirationLedger`); struct fields keep the spec's names. The example below uses the testnet XLM Stellar Asset Contract.

```kotlin
import com.example.bindings.TokenContract
import com.ionspin.kotlin.bignum.integer.BigInteger

val sourceKeypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val sourceId = sourceKeypair.getAccountId()

// The generated methods encode and decode all values themselves, so no spec is loaded
val token = TokenContract.forContract(
    contractId = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
    rpcUrl = "https://soroban-testnet.stellar.org",
    network = Network.TESTNET
)

// Read-only call: typed arguments, typed result
val balance: BigInteger = token.balance(Address(sourceId), source = sourceId, signer = null)
println("Balance: $balance")

// Write: signs with the source keypair and submits
token.transfer(
    from = Address(sourceId),
    to = Address("GCATS5YOVB6ROX2WUNKGNQ2MP3GMXDMKSG2O4N5CLX3A6W4PZGZZI55U"),
    amount = BigInteger.fromLong(1000L),
    source = sourceId,
    signer = sourceKeypair
)

token.client.close()
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
    .forAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")
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
    .forAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")
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
    .forAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")
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
    .forAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")
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
import com.ionspin.kotlin.bignum.integer.BigInteger

val sorobanServer = SorobanServer("https://soroban-testnet.stellar.org")

// Check server health before making requests to verify connectivity and sync status
val health = sorobanServer.getHealth()
println("Status: ${health.status}")

// Build a Soroban transaction (e.g., invoke contract)
val sourceKeypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val sourceAccount = sorobanServer.getAccount(sourceKeypair.getAccountId())
val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
    .addOperation(
        InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            functionName = "transfer",
            parameters = listOf(
                Scv.toAddress(Address("GCFIRY65OQE7DFP5KLNS2PF2LVZMUZYJX4OZIEQ36N2IQANUB5XVYOJR").toSCAddress()),
                Scv.toAddress(Address("GCATS5YOVB6ROX2WUNKGNQ2MP3GMXDMKSG2O4N5CLX3A6W4PZGZZI55U").toSCAddress()),
                Scv.toInt128(BigInteger.fromLong(1000))
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
    contractId = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
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
            GetEventsRequest.EventFilter(
                contractIds = listOf("CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"),  // List, not Set
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
import kotlinx.coroutines.delay

val server = HorizonServer("https://horizon-testnet.stellar.org")
// Build and sign a transaction first
val sourceAccount = server.loadAccount(sourceKeypair.getAccountId())
val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
    .addOperation(
        PaymentOperation(
            destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
            asset = AssetTypeNative,
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
// Asset codes: 1-12 alphanumeric characters (a-z, A-Z, 0-9)

// Check asset type
when (asset) {
    is AssetTypeNative -> println("Native XLM")
    is AssetTypeCreditAlphaNum4 -> println("Short code: ${asset.code}")
    is AssetTypeCreditAlphaNum12 -> println("Long code: ${asset.code}")
}
```

### Stellar Asset Contracts (SAC)

A Stellar Asset Contract (SAC) is the built-in token contract of a classic asset: through it, accounts and contracts hold and transfer the asset with the SEP-41 token interface. Every classic asset, XLM included, has one SAC per network, whose contract id derives from the asset and the network; the contract becomes usable once it is deployed (see [Deploying a SAC](#deploying-a-sac)). `SACClient` offers typed calls for a SAC: balances, transfers, and the asset's decimals, name, and symbol. `ContractClient.forContract` also opens a SAC, for its other functions such as `approve`, `mint`, or `clawback`.

Amounts are i128 base units, carried as `com.ionspin.kotlin.bignum.integer.BigInteger`. A SAC has 7 decimals, so one unit of the asset is 10,000,000 base units, and an XLM base unit is a stroop.

#### Opening a SAC Client

`SACClient.forAsset` opens the SAC of an asset, whose contract id it derives with `Asset.getContractId`, and `SACClient.forContractId` opens a SAC by its contract id. Opening sends no request, and the id is not checked against the network, so a call to a contract that lacks the function fails. The keypair is the source account of every call and signs the transfers.

```kotlin
val rpcUrl = "https://soroban-testnet.stellar.org"

// A client with a keypair loads its source account for every call, so the account must exist
val sourceKeyPair = KeyPair.random()
FriendBot.fundTestnetAccount(sourceKeyPair.getAccountId())

// The contract id derives from the asset and the network, without a request
val xlmContractId = AssetTypeNative.getContractId(Network.TESTNET)  // CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC
val usdc = Asset.createNonNativeAsset("USDC", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
val usdcContractId = usdc.getContractId(Network.PUBLIC)  // CCW67TSZV3SSS2HXMBQ5JFGCKJNXKZM7UQUWUZPUTHXSTZLEO7SJMI75

val fromAsset = SACClient.forAsset(AssetTypeNative, sourceKeyPair, Network.TESTNET, rpcUrl)
val fromId = SACClient.forContractId(xlmContractId, sourceKeyPair, Network.TESTNET, rpcUrl)
println(fromAsset.contractId == fromId.contractId)  // true

fromAsset.close()
fromId.close()
```

`forContractId` throws `IllegalArgumentException` for an id that is not a contract (`C...`) address; the message names the kind of address given when it recognizes one (a secret seed by its kind only), and for a muxed contract (`W...`) address it asks for its base contract address.

#### Read-Only Client

`SACClient.readOnlyForAsset` and `SACClient.readOnlyForContractId` open a client without a keypair. Its source is `SACClient.READ_ONLY_SOURCE_ACCOUNT_ID`, the all-zero public key that no secret key produces, and its reads need no account on the network.

```kotlin
val reader = SACClient.readOnlyForAsset(AssetTypeNative, Network.TESTNET, "https://soroban-testnet.stellar.org")

println(reader.decimals())  // 7
println(reader.name())      // native
println(reader.symbol())    // native

// The balance of any holder; Friendbot funds this account with 10,000 XLM
val holder = KeyPair.random()
FriendBot.fundTestnetAccount(holder.getAccountId())
println(reader.balance(Address(holder.getAccountId())))  // the balance of the Friendbot-funded account, in base units

reader.close()
```

For an issued asset, `name()` returns `CODE:ISSUER` and `symbol()` the asset code. A read-only client builds and simulates a `transfer`, but the transaction refuses to be signed or submitted: `sign`, `signAndSubmit`, and `submit` throw `IllegalStateException`, and `transferAndSubmit` throws `IllegalStateException` before any request. No read signs, sends, or restores, on any client: when ledger entries a read needs are archived, the read throws `IllegalStateException`. Restore them first, for example with a `RestoreFootprintOperation` (see [Soroban Operations](#soroban-operations)).

#### Reading a Balance

`balance` runs the contract's own `balance` function for an account (`G...`) or contract (`C...`) holder and returns base units; a contract holder without a balance reads 0. An account that does not exist fails the simulation with `Error(Contract, #6)`, and an account without a trustline to an issued asset with `Error(Contract, #13)`. Both throw `SimulationFailedException`, whose message carries the contract error. `reportedContractError` carries the contract error code, and is null when the error has none; the `else` branch of the example rethrows that case.

```kotlin
import com.soneso.stellar.sdk.contract.exception.SimulationFailedException

val xlm = SACClient.readOnlyForAsset(AssetTypeNative, Network.TESTNET, "https://soroban-testnet.stellar.org")

// Holder ids arrive at run time, for example from user input
val holderIds = listOf(
    "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54",  // an account
    "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",  // a contract
    "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUQ"  // a muxed account
)
for (holderId in holderIds) {
    try {
        println("$holderId: ${xlm.balance(Address(holderId))}")
    } catch (e: IllegalArgumentException) {
        // Thrown before any request: not an address, or a muxed address whose base the message names
        println("$holderId: ${e.message}")
    } catch (e: SimulationFailedException) {
        when (e.reportedContractError?.code) {
            6u -> println("$holderId: the account does not exist")
            13u -> println("$holderId: the account has no trustline to the asset")
            else -> throw e
        }
    }
}

xlm.close()
```

A muxed account (`M...`) or muxed contract (`W...`) holds its balance at its base address, so `balance` rejects it with `IllegalArgumentException` before any request and names the base address to query. For the muxed account above, the message is `The balance holder must be an account (G...) or contract (C...) address, got a muxed account (M...) address: MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUQ; query its base account GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ`.

`SorobanServer.getSACBalance(contractId, asset, network)` reads the balance entry of a contract (`C...`) holder with its `authorized` and `clawback` flags straight from the ledger, without a simulation; `SACClient.balance` runs the contract's own `balance` function for an account or contract holder.

#### Sending a Transfer

`SACClient.toBaseUnits` converts a decimal amount to base units: decimal digits, optionally followed by a point and 1 to 7 fractional digits, with no sign, exponent, grouping, or whitespace. It accepts zero, rounds nothing, and its largest amount is `17014118346046923173168730371588.4105727`, which is 2^127 - 1 base units; any other input throws `IllegalArgumentException`.

With the default options, `transfer` returns a simulated, unsigned `AssembledTransaction<SCValXdr>`. `transferAndSubmit` transfers from the source account, signs with the client's keypair, submits, and returns the successful `GetTransactionResponse`. With `options.simulate = false`, call `simulate()` on the transaction returned by `transfer` before signing. On a keyed client, `transferAndSubmit` with this option throws `NotYetSimulatedException`. A transfer to a muxed account (`M...`) or muxed contract (`W...`) credits its base address, and the data of the `transfer` event is then a map of `amount` and `to_muxed_id`.

```kotlin
val rpcUrl = "https://soroban-testnet.stellar.org"
val sender = KeyPair.random()
val recipient = KeyPair.random()
FriendBot.fundTestnetAccount(sender.getAccountId())
FriendBot.fundTestnetAccount(recipient.getAccountId())

val xlm = SACClient.forAsset(AssetTypeNative, sender, Network.TESTNET, rpcUrl)

val amount = SACClient.toBaseUnits("12.5")  // 125000000
val response = xlm.transferAndSubmit(Address(recipient.getAccountId()), amount)
println("Transferred in ledger ${response.ledger}")

// A muxed account shares the recipient's balance; the transfer event names its id
val muxedRecipient = MuxedAccount(recipient.getAccountId(), 42uL).address
val transfer = xlm.transfer(
    from = Address(sender.getAccountId()),
    to = Address(muxedRecipient),
    amount = SACClient.toBaseUnits("1")
)
transfer.signAndSubmit()

val events = transfer.getTransactionResponse!!.events?.parseContractEventsXdr().orEmpty().flatten()
val transferEvent = events
    .map { (it.body as ContractEventBodyXdr.V0).value }
    .first { Scv.fromSymbol(it.topics.first()) == "transfer" }
val eventData = Scv.fromMap(transferEvent.data)
println(Scv.fromUint64(eventData.getValue(Scv.toSymbol("to_muxed_id"))))  // 42

xlm.close()
```

Both writes take `options`, a `ClientOptions` that sets the fee and the timeouts and keeps the client's `sourceAccountKeyPair`, `contractId`, `network`, and `rpcUrl`, and the contract's other functions, such as `approve`, `mint`, or `clawback`, go through `getContractClient().buildInvoke` (see [Advanced Contract Control (buildInvoke)](#advanced-contract-control-buildinvoke)).

#### Transfers Authorized by Another Account

`transfer` takes an account (`G...`) or contract (`C...`) address as `from`, and a `from` other than the client's source account must authorize the transfer. For an account, `needsNonInvokerSigningBy` lists it, `signAuthEntries` signs its authorization entry with its keypair, and `signAndSubmit` then signs the transaction with the client's keypair and submits it.

```kotlin
val rpcUrl = "https://soroban-testnet.stellar.org"

// The submitter pays the fee; the owner authorizes the transfer of its XLM
val submitter = KeyPair.random()
val owner = KeyPair.random()
val recipient = KeyPair.random()
for (account in listOf(submitter, owner, recipient)) {
    FriendBot.fundTestnetAccount(account.getAccountId())
}

val xlm = SACClient.forAsset(AssetTypeNative, submitter, Network.TESTNET, rpcUrl)
val transfer = xlm.transfer(
    from = Address(owner.getAccountId()),
    to = Address(recipient.getAccountId()),
    amount = SACClient.toBaseUnits("5")
)

println(transfer.needsNonInvokerSigningBy())  // the owner's account id
transfer.signAuthEntries(owner)
transfer.signAndSubmit()

xlm.close()
```

When the owner signs on another device, `signAuthEntries` takes a `KeyPair` built from the owner's account id with `KeyPair.fromAccountId(...)`, which carries no secret key, together with an `authorizeEntryDelegate` (see [Authorization](#authorization)).

A contract `from`, such as a smart wallet, authorizes through its own `__check_auth`. `signAuthEntries` signs the entries of accounts only, and `sign` skips contract addresses, so complete the wallet's entry at the `SorobanServer` level before the transaction is submitted. The entry is in the `auth` list of the `InvokeHostFunctionOperation` that the simulated transaction's `builtTransaction` holds:

1. Set `signatureExpirationLedger` on the entry's address credentials before computing the payload, because the signature covers it. `Auth.authorizeEntry` sets it to `validUntilLedgerSeq`, and on an entry that already carries a signature it requires the stored value.
2. Obtain the signature value that the wallet's `__check_auth` verifies. `Auth.authorizeEntry` with an `Auth.Signer` writes the format of an account: a vector of maps with the `public_key` and `signature` bytes. For any other format, build the value and set it as the credentials' `signature`.
3. Attach the signed entries. `AssembledTransaction` has no setter for them, so build `InvokeHostFunctionOperation(hostFunction = operation.hostFunction, auth = signedEntries)` into a new `TransactionBuilder` transaction for the same source account. Its base fee is the inclusion fee only, and it carries no Soroban data.
4. Re-simulate it with `simulateTransaction(transaction, authMode = SimulateTransactionRequest.AuthMode.ENFORCE)` and check the response's `error`.
5. Apply the enforcing response with `prepareTransaction(transaction, simulation)`. The fee becomes the inclusion fee plus minResourceFee, and the signed entries are kept.
6. Sign the prepared transaction with the source account's keypair, send it with `sendTransaction`, and wait for the result with `pollTransaction`. The passage "Submitting delegated or hand-built entries" in [Protocol 27 Authorization Arms (CAP-71)](advanced.md#protocol-27-authorization-arms-cap-71) shows this route in code.

#### Deploying a SAC

A SAC is deployed once per asset and network; the XLM SAC exists already on testnet. `InvokeHostFunctionOperation.createStellarAssetContract(asset)` builds the deploy operation. It carries no authorization entry, so any funded account can prepare, sign, send, and poll a transaction with it through `SorobanServer`. The host derives the new contract id from the asset and the network, so `getCreatedContractId()` of the result equals `asset.getContractId(network)`, and the deployed SAC opens with `SACClient`.

```kotlin
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import com.soneso.stellar.sdk.rpc.responses.SendTransactionStatus

val server = SorobanServer("https://soroban-testnet.stellar.org")
val issuer = KeyPair.random()
FriendBot.fundTestnetAccount(issuer.getAccountId())

// A new asset has no SAC until someone deploys it
val asset = Asset.createNonNativeAsset("DEMO", issuer.getAccountId())
val transaction = TransactionBuilder(server.getAccount(issuer.getAccountId()), Network.TESTNET)
    .addOperation(InvokeHostFunctionOperation.createStellarAssetContract(asset))
    .setTimeout(300)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

// prepareTransaction simulates the transaction and applies its footprint and resource fee
val prepared = server.prepareTransaction(transaction)
prepared.sign(issuer)
val sent = server.sendTransaction(prepared)

if (sent.status == SendTransactionStatus.PENDING) {
    val result = server.pollTransaction(sent.hash!!)
    if (result.status == GetTransactionStatus.SUCCESS) {
        println(result.getCreatedContractId() == asset.getContractId(Network.TESTNET))  // true
    } else if (result.status == GetTransactionStatus.FAILED) {
        println("Deployment failed: ${result.resultXdr}")
    } else {
        println("Deployment not confirmed; poll ${sent.hash} again.")
    }
} else {
    println("Not accepted: ${sent.errorResultXdr}")
}

server.close()
```

---

**Navigation**: [← Architecture](architecture.md) | [Advanced Topics →](advanced.md)