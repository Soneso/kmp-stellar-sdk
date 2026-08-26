# Stellar Operations Reference

All operations are data classes instantiated via constructors (NOT builders). Every operation inherits from the `Operation` sealed class and supports an optional `sourceAccount` property (G... or M... address) that overrides the transaction source. All code runs in a `suspend` context and assumes these imports: `com.soneso.stellar.sdk.*`, `com.soneso.stellar.sdk.scval.Scv`, and `com.soneso.stellar.sdk.xdr.*`.

<!-- WRONG/CORRECT: The KMP SDK does NOT use the builder pattern found in the Java SDK -->
<!-- WRONG: CreateAccountOperationBuilder("G...", "10.0").build() -->
<!-- CORRECT: CreateAccountOperation(destination = "G...", startingBalance = "10.0") -->

## Table of Contents

- [Account and Payment Operations](#account-and-payment-operations)
  - [CreateAccountOperation](#createaccountoperation)
  - [PaymentOperation](#paymentoperation)
  - [PathPaymentStrictReceiveOperation](#pathpaymentstrictreceiveoperation)
  - [PathPaymentStrictSendOperation](#pathpaymentstrictsendoperation)
  - [AccountMergeOperation](#accountmergeoperation)
- [DEX Trading Operations](#dex-trading-operations)
  - [ManageSellOfferOperation](#managesellofferoperation)
  - [ManageBuyOfferOperation](#managebuyofferoperation)
  - [CreatePassiveSellOfferOperation](#createpassivesellofferoperation)
- [Account Configuration Operations](#account-configuration-operations)
  - [SetOptionsOperation](#setoptionsoperation)
  - [ChangeTrustOperation](#changetrustoperation)
  - [AllowTrustOperation (Deprecated)](#allowtrustoperation-deprecated)
  - [SetTrustLineFlagsOperation](#settrustlineflagsoperation)
  - [ManageDataOperation](#managedataoperation)
  - [BumpSequenceOperation](#bumpsequenceoperation)
- [Claimable Balance Operations](#claimable-balance-operations)
  - [CreateClaimableBalanceOperation](#createclaimablebalanceoperation)
  - [ClaimClaimableBalanceOperation](#claimclaimablebalanceoperation)
  - [ClawbackClaimableBalanceOperation](#clawbackclaimablebalanceoperation)
- [Sponsorship Operations](#sponsorship-operations)
  - [BeginSponsoringFutureReservesOperation](#beginsponsoringfuturereservesoperation)
  - [EndSponsoringFutureReservesOperation](#endsponsoringfuturereservesoperation)
  - [RevokeSponsorshipOperation](#revokesponsorshipoperation)
- [Clawback Operations](#clawback-operations)
  - [ClawbackOperation](#clawbackoperation)
- [Liquidity Pool Operations](#liquidity-pool-operations)
  - [LiquidityPoolDepositOperation](#liquiditypooldepositoperation)
  - [LiquidityPoolWithdrawOperation](#liquiditypoolwithdrawoperation)
- [Soroban Operations](#soroban-operations)
  - [InvokeHostFunctionOperation](#invokehostfunctionoperation)
  - [ExtendFootprintTTLOperation](#extendfootprintttloperation)
  - [RestoreFootprintOperation](#restorefootprintoperation)
- [Common Result Codes](#common-result-codes)

## Account and Payment Operations

### CreateAccountOperation

Creates and funds a new account on the network.

```kotlin
CreateAccountOperation(
    destination: String,   // G... account ID to create
    startingBalance: String // Amount in XLM (minimum 1 XLM for base reserve)
)
```

```kotlin
val newKeyPair = KeyPair.random()
val createOp = CreateAccountOperation(
    destination = newKeyPair.getAccountId(),
    startingBalance = "10.0"
)
```

### PaymentOperation

Sends an asset to an existing account.

```kotlin
PaymentOperation(
    destination: String, // G... or M... recipient
    asset: Asset,        // AssetTypeNative or AssetTypeCreditAlphaNum4/12
    amount: String       // Decimal amount to send
)
```

```kotlin
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val recipientId = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
// Send XLM
val payXlm = PaymentOperation(
    destination = recipientId,
    asset = AssetTypeNative,
    amount = "100.0"
)

// Send custom asset
val usd = AssetTypeCreditAlphaNum4("USD", issuerAccountId)
val payUsd = PaymentOperation(
    destination = recipientId,
    asset = usd,
    amount = "50.0"
)
```

### PathPaymentStrictReceiveOperation

Send payment through a path, guaranteeing the destination receives an exact amount.

```kotlin
PathPaymentStrictReceiveOperation(
    sendAsset: Asset,       // Asset to send from source
    sendMax: String,        // Maximum to debit from source
    destination: String,    // Recipient account
    destAsset: Asset,       // Asset recipient receives
    destAmount: String,     // Exact amount recipient receives
    path: List<Asset> = emptyList() // Intermediate path assets (max 5)
)
```

```kotlin
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val recipientId = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
val usd = AssetTypeCreditAlphaNum4("USD", issuerAccountId)

// Direct path (no intermediaries)
val pathOp = PathPaymentStrictReceiveOperation(
    sendAsset = AssetTypeNative,
    sendMax = "20.0",
    destination = recipientId,
    destAsset = usd,
    destAmount = "10.0",
    path = emptyList() // direct path, or listOf(intermediateAsset) for multi-hop
)

// WRONG: omitting the path parameter — it defaults to emptyList() which is fine,
//        but the path payment will only work if a direct offer exists between the assets
// TIP: use sdk.strictReceivePaths to find valid paths before submitting
```

### PathPaymentStrictSendOperation

Send exact amount from source, recipient gets at least a minimum.

```kotlin
PathPaymentStrictSendOperation(
    sendAsset: Asset,       // Asset to send from source
    sendAmount: String,     // Exact amount to debit from source
    destination: String,    // Recipient account
    destAsset: Asset,       // Asset recipient receives
    destMin: String,        // Minimum amount recipient receives
    path: List<Asset> = emptyList()
)
```

```kotlin
val recipientId = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
val usd = AssetTypeCreditAlphaNum4("USD", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
val pathSendOp = PathPaymentStrictSendOperation(
    sendAsset = AssetTypeNative,
    sendAmount = "10.0",
    destination = recipientId,
    destAsset = usd,
    destMin = "4.5"
)
```

### AccountMergeOperation

Merges source account into destination, transferring all XLM.

```kotlin
val destinationAccountId = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
val mergeOp = AccountMergeOperation(destination = destinationAccountId)
```

## DEX Trading Operations

### ManageSellOfferOperation

Creates, updates, or deletes a sell offer on the DEX. Uses `Price` (numerator/denominator fraction), not a string.

```kotlin
ManageSellOfferOperation(
    selling: Asset,      // Asset to sell
    buying: Asset,       // Asset to buy
    amount: String,      // Amount to sell ("0" to delete)
    price: Price,        // Price per unit as fraction
    offerId: Long = 0    // 0 = new offer, existing ID to update/delete
)
```

```kotlin
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val xlm = AssetTypeNative
val usd = AssetTypeCreditAlphaNum4("USD", issuerAccountId)

// WRONG: ManageSellOfferOperation(usd, xlm, "100.0", "2.5") — price is NOT a String
// CORRECT: use Price(numerator, denominator) or Price.fromString("2.5")
val price = Price.fromString("2.5")

// Create new sell offer
val sellOp = ManageSellOfferOperation(
    selling = usd,
    buying = xlm,
    amount = "100.0",
    price = price
)

// Update existing offer
val updateOp = ManageSellOfferOperation(
    selling = usd,
    buying = xlm,
    amount = "150.0",
    price = Price.fromString("2.6"),
    offerId = 12345L
)

// Cancel offer (amount = "0")
val cancelOp = ManageSellOfferOperation(
    selling = usd,
    buying = xlm,
    amount = "0",
    price = Price.fromString("2.5"), // price still required, value doesn't matter
    offerId = 12345L
)

// After submitting, get the offer ID:
// val offers = sdk.offers.forAccount(accountId).execute()
// val offerId = offers.records.first().id.toLong()
```

### ManageBuyOfferOperation

Creates, updates, or deletes a buy offer. Same pattern as sell, but `buyAmount` is the buying amount.

```kotlin
ManageBuyOfferOperation(
    selling: Asset,
    buying: Asset,
    buyAmount: String,   // NOTE: parameter is "buyAmount", not "amount"
    price: Price,
    offerId: Long = 0
)
```

```kotlin
val usd = AssetTypeCreditAlphaNum4("USD", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
// WRONG: ManageBuyOfferOperation(xlm, usd, amount = "50.0", ...) — parameter is buyAmount
// CORRECT: ManageBuyOfferOperation(xlm, usd, buyAmount = "50.0", ...)
val buyOp = ManageBuyOfferOperation(
    selling = xlm,
    buying = usd,
    buyAmount = "50.0",
    price = Price.fromString("0.4")
)
```

### CreatePassiveSellOfferOperation

Creates a passive sell offer that does not take existing offers at the same price.

```kotlin
// xlm: from the previous steps of this flow
val usd = AssetTypeCreditAlphaNum4("USD", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
val passiveOp = CreatePassiveSellOfferOperation(
    selling = usd,
    buying = xlm,
    amount = "100.0",
    price = Price.fromString("2.5")
)
```

## Account Configuration Operations

### SetOptionsOperation

Configures account flags, thresholds, signers, and home domain. All parameters are optional (nullable). Uses `SignerKey` sealed class for signers.

```kotlin
SetOptionsOperation(
    inflationDestination: String? = null,
    clearFlags: Int? = null,
    setFlags: Int? = null,
    masterKeyWeight: Int? = null,   // 0-255
    lowThreshold: Int? = null,      // 0-255
    mediumThreshold: Int? = null,   // 0-255
    highThreshold: Int? = null,     // 0-255
    homeDomain: String? = null,     // max 32 bytes
    signer: SignerKey? = null,
    signerWeight: Int? = null       // 0-255, 0 to remove signer
)
```

```kotlin
val signerAccountId = "GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI"
// Set home domain
val domainOp = SetOptionsOperation(homeDomain = "example.com")

// Set auth flags (1=AUTH_REQUIRED, 2=AUTH_REVOCABLE, 4=AUTH_IMMUTABLE, 8=AUTH_CLAWBACK)
val flagsOp = SetOptionsOperation(setFlags = 1) // AUTH_REQUIRED

// Configure multi-sig thresholds
val thresholdOp = SetOptionsOperation(
    masterKeyWeight = 1,
    lowThreshold = 1,
    mediumThreshold = 2,
    highThreshold = 2
)

// WRONG: signer = KeyPair.fromAccountId(signerAccountId).xdrSignerKey — no such property
// CORRECT: signer = SignerKey.ed25519PublicKey(signerAccountId) — use SignerKey factory
val signerOp = SetOptionsOperation(
    signer = SignerKey.ed25519PublicKey(signerAccountId),
    signerWeight = 1
)

// Remove signer (set weight to 0)
val removeSignerOp = SetOptionsOperation(
    signer = SignerKey.ed25519PublicKey(signerAccountId),
    signerWeight = 0
)
```

**Multi-sig setup must be atomic:** Each `SetOptionsOperation` can set only ONE signer. To add multiple signers and configure thresholds, include all operations in a SINGLE transaction. If you raise thresholds in a separate transaction first, subsequent operations may require more signatures than available, locking you out.

```kotlin
val account = Account("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", 1L)
val primaryKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val signerAId = "GCFIRY65OQE7DFP5KLNS2PF2LVZMUZYJX4OZIEQ36N2IQANUB5XVYOJR"
val signerBId = "GCATS5YOVB6ROX2WUNKGNQ2MP3GMXDMKSG2O4N5CLX3A6W4PZGZZI55U"
// Correct: add 2 signers + set thresholds in ONE transaction
val addA = SetOptionsOperation(
    signer = SignerKey.ed25519PublicKey(signerAId),
    signerWeight = 1
)
val addB = SetOptionsOperation(
    signer = SignerKey.ed25519PublicKey(signerBId),
    signerWeight = 1
)
val thresholds = SetOptionsOperation(
    masterKeyWeight = 1,
    lowThreshold = 1,
    mediumThreshold = 2,
    highThreshold = 3
)

val tx = TransactionBuilder(account, Network.TESTNET)
    .setBaseFee(100)
    .addOperation(addA)
    .addOperation(addB)
    .addOperation(thresholds)
    .setTimeout(300)
    .build()
tx.sign(primaryKeyPair)
```

### ChangeTrustOperation

Creates, updates, or removes a trustline for non-native assets or liquidity pool shares. Has convenience constructors for both Asset and LiquidityPool.

```kotlin
// For regular assets
ChangeTrustOperation(
    asset: Asset,                  // The asset to trust (not native)
    limit: String = MAX_LIMIT      // Max amount to hold ("0" to remove)
)

// For liquidity pool shares
ChangeTrustOperation(
    liquidityPool: LiquidityPool,
    limit: String = MAX_LIMIT
)
```

```kotlin
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val usd = AssetTypeCreditAlphaNum4("USD", issuerAccountId)

// Create trustline with max limit
val trustOp = ChangeTrustOperation(usd)
// Equivalent to: ChangeTrustOperation(usd, ChangeTrustOperation.MAX_LIMIT)

// Create trustline with specific limit
val limitedTrustOp = ChangeTrustOperation(usd, "1000.0")

// Remove trustline (balance must be 0)
val removeTrustOp = ChangeTrustOperation(usd, "0")

// WRONG: ChangeTrustOperation(AssetTypeNative) — cannot trust the native asset
// CORRECT: ChangeTrustOperation(usd) — use a non-native asset

// Liquidity pool share trustline
val assetA = AssetTypeNative
val assetB = AssetTypeCreditAlphaNum4("USD", issuerAccountId)
// Assets must be in lexicographic order (assetA < assetB)
val pool = LiquidityPool(assetA, assetB)
val poolTrustOp = ChangeTrustOperation(pool)
```

### AllowTrustOperation (Deprecated)

Use `SetTrustLineFlagsOperation` instead.

```kotlin
AllowTrustOperation(
    trustor: String,     // Account that holds the trustline
    assetCode: String,   // Asset code (not the full Asset object)
    authorize: Int        // 0=unauthorized, 1=authorized, 2=authorized_to_maintain_liabilities
)
```

### SetTrustLineFlagsOperation

Sets or clears flags on a trustline. Source must be the asset issuer. Has companion constants for flag values.

```kotlin
SetTrustLineFlagsOperation(
    trustor: String,      // Account that owns the trustline
    asset: Asset,         // The asset (not native)
    clearFlags: Int? = null,
    setFlags: Int? = null
)

// Flag constants on SetTrustLineFlagsOperation companion:
// AUTHORIZED_FLAG = 1
// AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG = 2
// TRUSTLINE_CLAWBACK_ENABLED_FLAG = 4
```

```kotlin
// trustorAccountId: from the previous steps of this flow
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val usd = AssetTypeCreditAlphaNum4("USD", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
// Authorize a trustline
val authOp = SetTrustLineFlagsOperation(
    trustor = trustorAccountId,
    asset = usd,
    setFlags = SetTrustLineFlagsOperation.AUTHORIZED_FLAG
).also { it.sourceAccount = issuerAccountId }

// Freeze: allow maintaining liabilities only (clear AUTHORIZED, set AUTHORIZED_TO_MAINTAIN_LIABILITIES)
val freezeOp = SetTrustLineFlagsOperation(
    trustor = trustorAccountId,
    asset = usd,
    clearFlags = SetTrustLineFlagsOperation.AUTHORIZED_FLAG,
    setFlags = SetTrustLineFlagsOperation.AUTHORIZED_TO_MAINTAIN_LIABILITIES_FLAG
).also { it.sourceAccount = issuerAccountId }
```

### ManageDataOperation

Sets, updates, or deletes key-value data on an account.

```kotlin
ManageDataOperation(
    name: String,           // Key name (max 64 bytes)
    value: ByteArray? = null // Value bytes (null to delete, max 64 bytes)
)
```

```kotlin
// Set data using raw bytes
val setDataOp = ManageDataOperation(
    name = "my_key",
    value = "my_value".encodeToByteArray()
)

// Delete data
val deleteDataOp = ManageDataOperation(name = "my_key", value = null)

// Convenience factory for string values
val setDataOp2 = ManageDataOperation.forString("my_key", "my_value")
val deleteDataOp2 = ManageDataOperation.forString("my_key", null)
```

### BumpSequenceOperation

Bumps account sequence number to a higher value.

```kotlin
// WRONG: BumpSequenceOperation(BigInt.from(100000)) — parameter is Long, not BigInt
// CORRECT: BumpSequenceOperation(bumpTo = 100000L) — use Long
val bumpOp = BumpSequenceOperation(bumpTo = 100000L)
```

## Claimable Balance Operations

### CreateClaimableBalanceOperation

Creates a claimable balance with specified claimants and predicates. Uses `ClaimPredicate` sealed class hierarchy.

```kotlin
CreateClaimableBalanceOperation(
    asset: Asset,
    amount: String,
    claimants: List<Claimant>
)
```

```kotlin
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val recipientAccountId = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM"
@OptIn(ExperimentalTime::class)
val nowSeconds = Clock.System.now().epochSeconds

// Unconditional claimant
val claimant = Claimant(
    destination = recipientAccountId,
    predicate = ClaimPredicate.Unconditional
)

val createBalanceOp = CreateClaimableBalanceOperation(
    asset = AssetTypeNative,
    amount = "100.0",
    claimants = listOf(claimant)
)

// Time-locked claimant (claimable after a specific time)
val unlockTime = nowSeconds + 86400 // 24 hours from now

// WRONG: Claimant.predicateNot(...) — no static factory methods on Claimant
// CORRECT: use ClaimPredicate sealed class directly
val timedClaimant = Claimant(
    destination = recipientAccountId,
    predicate = ClaimPredicate.Not(
        ClaimPredicate.BeforeAbsoluteTime(unlockTime)
    )
)

// AND/OR predicates: claimable inside the window [startTime, endTime)
val startTime = nowSeconds
val endTime = startTime + 86400
val windowPredicate = ClaimPredicate.And(
    left = ClaimPredicate.Not(ClaimPredicate.BeforeAbsoluteTime(startTime)),
    right = ClaimPredicate.BeforeAbsoluteTime(endTime)
)

// Relative time predicate (claimable within 7 days of creation)
val relativePredicate = ClaimPredicate.BeforeRelativeTime(7 * 24 * 60 * 60L)
```

### ClaimClaimableBalanceOperation

Claims an existing claimable balance by its ID (72-character hex string).

```kotlin
// WRONG: ClaimableBalanceResponse has .id NOT .balanceId
// Horizon returns the ID as the "id" field: claimableBalanceResponse.id
val balanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072" // from Horizon's "id"

val claimOp = ClaimClaimableBalanceOperation(balanceId = balanceId)
```

### ClawbackClaimableBalanceOperation

Issuer claws back a claimable balance (requires clawback enabled on the asset).

```kotlin
val balanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072" // 72-char hex id from Horizon
val clawbackBalanceOp = ClawbackClaimableBalanceOperation(balanceId = balanceId)
```

## Sponsorship Operations

### BeginSponsoringFutureReservesOperation

Begins sponsoring reserves for another account.

```kotlin
val sponsoredAccountId = "GDWUSKGGFDI4FRXK5EBTRECZSVQSSWJHHJOGH6JWG3AUMFFMQ435DIAG"
val beginSponsorOp = BeginSponsoringFutureReservesOperation(
    sponsoredId = sponsoredAccountId
)
```

### EndSponsoringFutureReservesOperation

Ends the current sponsorship. Must be signed by the sponsored account.

```kotlin
val endSponsorOp = EndSponsoringFutureReservesOperation()
```

**Sponsorship sandwich pattern** (all operations in one transaction):

```kotlin
// sponsorAccount, sponsoredKeyPair: from the previous steps of this flow
val newAccountId = "GDWUSKGGFDI4FRXK5EBTRECZSVQSSWJHHJOGH6JWG3AUMFFMQ435DIAG"
val sponsorKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
val beginOp = BeginSponsoringFutureReservesOperation(sponsoredId = newAccountId)

val createOp = CreateAccountOperation(
    destination = newAccountId,
    startingBalance = "0"
)

val endOp = EndSponsoringFutureReservesOperation().also {
    it.sourceAccount = newAccountId // signed by sponsored account
}

val tx = TransactionBuilder(sponsorAccount, Network.TESTNET)
    .setBaseFee(100)
    .addOperation(beginOp)
    .addOperation(createOp)
    .addOperation(endOp)
    .setTimeout(300)
    .build()
tx.sign(sponsorKeyPair)
tx.sign(sponsoredKeyPair) // sponsored must also sign
```

### RevokeSponsorshipOperation

Revokes sponsorship of ledger entries or signers. Uses `Sponsorship` sealed class to specify what to revoke.

```kotlin
RevokeSponsorshipOperation(sponsorship: Sponsorship)
```

```kotlin
// ed25519AccountId, sponsorAccountId: from the previous steps of this flow
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val balanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072" // 72-char hex id from Horizon
val signerAccountId = "GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI"
val usd = AssetTypeCreditAlphaNum4("USD", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
// Revoke account sponsorship
val revokeAccountOp = RevokeSponsorshipOperation(
    Sponsorship.Account(accountId)
).also { it.sourceAccount = sponsorAccountId }

// Revoke trustline sponsorship
val revokeTrustlineOp = RevokeSponsorshipOperation(
    Sponsorship.TrustLine(accountId, usd)
)

// Revoke data entry sponsorship
val revokeDataOp = RevokeSponsorshipOperation(
    Sponsorship.Data(accountId, "my_key")
)

// Revoke offer sponsorship (offerId is Long)
val revokeOfferOp = RevokeSponsorshipOperation(
    Sponsorship.Offer(sellerId = accountId, offerId = 12345L)
)

// Revoke claimable balance sponsorship
val revokeBalanceOp = RevokeSponsorshipOperation(
    Sponsorship.ClaimableBalance(balanceId)
)

// Revoke signer sponsorship
val revokeSignerOp = RevokeSponsorshipOperation(
    Sponsorship.Signer(
        accountId = signerAccountId,
        signerKey = SignerKey.ed25519PublicKey(ed25519AccountId)
    )
)
```

## Clawback Operations

### ClawbackOperation

Issuer claws back (burns) assets from an account. Cannot be used with native asset.

```kotlin
ClawbackOperation(
    from: String,    // Account holding the asset
    asset: Asset,    // Asset to claw back (not native)
    amount: String
)
```

```kotlin
val holderAccountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val usd = AssetTypeCreditAlphaNum4("USD", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
val clawbackOp = ClawbackOperation(
    from = holderAccountId,
    asset = usd,
    amount = "100.0"
).also { it.sourceAccount = issuerAccountId }
```

## Liquidity Pool Operations

### LiquidityPoolDepositOperation

Deposits both assets into an AMM liquidity pool. Uses `Price` for min/max price bounds.

```kotlin
LiquidityPoolDepositOperation(
    liquidityPoolId: String,  // 64-character hex pool ID
    maxAmountA: String,
    maxAmountB: String,
    minPrice: Price,          // NOTE: Price, not String
    maxPrice: Price
)
```

```kotlin
val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7" // 64-char hex pool id
val depositOp = LiquidityPoolDepositOperation(
    liquidityPoolId = poolId,
    maxAmountA = "1000.0",
    maxAmountB = "500.0",
    minPrice = Price.fromString("0.49"),
    maxPrice = Price.fromString("0.51")
)
```

### LiquidityPoolWithdrawOperation

Withdraws assets from a liquidity pool by redeeming pool shares.

```kotlin
LiquidityPoolWithdrawOperation(
    liquidityPoolId: String,  // 64-character hex pool ID
    amount: String,           // Pool shares to redeem
    minAmountA: String,
    minAmountB: String
)
```

```kotlin
val poolId = "dd7b1ab831c273310ddbec6f97870aa83c2fbd78ce22aded37ecbf4f3380fac7" // 64-char hex pool id
val withdrawOp = LiquidityPoolWithdrawOperation(
    liquidityPoolId = poolId,
    amount = "100.0",
    minAmountA = "450.0",
    minAmountB = "225.0"
)
```

## Soroban Operations

### InvokeHostFunctionOperation

Invokes Soroban smart contract functions. Has companion factory methods for common use cases.

```kotlin
// Low-level constructor (takes raw XDR)
InvokeHostFunctionOperation(
    hostFunction: HostFunctionXdr,
    auth: List<SorobanAuthorizationEntryXdr> = emptyList()
)
```

**Upload WASM:**

```kotlin
import java.io.File

val wasmBytes = File("contract.wasm").readBytes() // or platform-specific file loading
val uploadOp = InvokeHostFunctionOperation.uploadContractWasm(wasmBytes)
```

**Create contract from WASM hash:**

```kotlin
// wasmIdHex: from the previous steps of this flow
val deployerAccountId = "GDFJHLAXAUMHA4OWPOB4P7YO72AQR2HMIUYFOXLXE2DZGM633K7HZDQP"
// WRONG: CreateContractHostFunction(address, wasmId) — no such class
// CORRECT: use the companion factory method
val createOp = InvokeHostFunctionOperation.createContract(
    wasmId = wasmIdHex,             // 64-character hex string
    address = Address(deployerAccountId)
)
```

**Create contract with constructor args:**

```kotlin
// wasmIdHex: from the previous steps of this flow
val adminId = "GAJZR5RMNUNEK7CRXJVEWXZ5XUXWT7FJGILCDDOITF7EC26RPWJ4UVOE"
val deployerAccountId = "GDFJHLAXAUMHA4OWPOB4P7YO72AQR2HMIUYFOXLXE2DZGM633K7HZDQP"
val createOp = InvokeHostFunctionOperation.createContract(
    wasmId = wasmIdHex,
    address = Address(deployerAccountId),
    constructorArgs = listOf(
        Address(adminId).toSCVal(),
        Scv.toUint32(8u),          // decimals
        Scv.toString("TokenName"),
        Scv.toString("TKN")
    )
)
```

**Invoke contract function:**

```kotlin
val contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
// WRONG: InvokeContractHostFunction(contractId, "transfer", arguments = [...])
// CORRECT: use the companion factory method
val invokeOp = InvokeHostFunctionOperation.invokeContractFunction(
    contractAddress = contractId,   // C... format
    functionName = "transfer",
    parameters = listOf(
        Address(fromId).toSCVal(),
        Address(toId).toSCVal(),
        Scv.toInt128(BigInteger(1000))
    )
)
```

**Deploy Stellar Asset Contract (SAC):**

For deploying a SAC, build the host function XDR manually:

```kotlin
val usd = AssetTypeCreditAlphaNum4("USD", "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN")
val assetXdr = usd.toXdr()
val preimage = ContractIDPreimageXdr.FromAsset(assetXdr)
// WRONG: ContractExecutableXdr.StellarAsset — no such variant
// CORRECT: ContractExecutableXdr.Void — the Stellar Asset type uses the Void variant
val executable = ContractExecutableXdr.Void
val createArgs = CreateContractArgsXdr(
    contractIdPreimage = preimage,
    executable = executable
)
val hostFunction = HostFunctionXdr.CreateContract(createArgs)
val sacOp = InvokeHostFunctionOperation(hostFunction = hostFunction)
```

### ExtendFootprintTTLOperation

Extends the time-to-live of Soroban contract state entries.

```kotlin
// WRONG: ExtendFootprintTTLOperationBuilder(100000).build()
// CORRECT: ExtendFootprintTTLOperation(extendTo = 100000)
val extendOp = ExtendFootprintTTLOperation(extendTo = 100000)
```

The `extendTo` parameter is the number of ledgers to extend the TTL. The transaction must include `sorobanTransactionData` (from simulation) specifying which entries to extend.

### RestoreFootprintOperation

Restores archived Soroban contract state entries.

```kotlin
val restoreOp = RestoreFootprintOperation()
```

## Common Result Codes

**Transaction-level:** `tx_success`, `tx_failed` (check op codes), `tx_bad_seq` (stale sequence), `tx_bad_auth` (missing/invalid signatures), `tx_insufficient_balance`, `tx_insufficient_fee`, `tx_too_early`/`tx_too_late` (time bounds).

**Operation-level:** `op_underfunded` (insufficient balance), `op_no_destination` (account doesn't exist), `op_no_trust` (missing trustline), `op_line_full` (trustline limit reached), `op_bad_auth` (insufficient signature weight for multi-sig).

For full error catalog and debugging: [Troubleshooting Guide](./troubleshooting.md)
