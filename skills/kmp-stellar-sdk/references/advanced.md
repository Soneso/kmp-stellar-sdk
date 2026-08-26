# Advanced Features

Less common but important patterns. All code runs inside a `suspend` context (coroutine) and assumes these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.*
import com.soneso.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse
```

## Table of Contents

- [Multi-Signature Accounts](#multi-signature-accounts)
- [Multi-Sig XDR Sharing (Remote Signers)](#multi-sig-xdr-sharing-remote-signers)
- [Fee-Bump Transactions](#fee-bump-transactions)
- [Sponsored Reserves](#sponsored-reserves)
- [Claimable Balances](#claimable-balances)
- [Liquidity Pools](#liquidity-pools)
- [Muxed Accounts](#muxed-accounts)
- [Async Transaction Submission](#async-transaction-submission)

## Multi-Signature Accounts

**IMPORTANT:** Always add signers and set thresholds in a SINGLE transaction. Setting thresholds first in a separate transaction may lock you out if the new thresholds require signatures you haven't added yet.

```kotlin
val network = Network.TESTNET
val primaryKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val server = HorizonServer("https://horizon-testnet.stellar.org")

val secondaryKeyPair = KeyPair.random()
val primaryId = primaryKeyPair.getAccountId()

val accountResponse = server.accounts().account(primaryId)

// Add signer + set thresholds in ONE transaction
// WRONG: SignerKey.Ed25519(publicKey) — not a constructor call
// CORRECT: SignerKey.ed25519PublicKey(accountId) — factory method
val signerKey = SignerKey.ed25519PublicKey(secondaryKeyPair.getAccountId())

val tx = TransactionBuilder(
    sourceAccount = Account(primaryId, accountResponse.sequenceNumber),
    network = network
)
    .addOperation(
        SetOptionsOperation(
            signer = signerKey,
            signerWeight = 1,
            masterKeyWeight = 1,
            lowThreshold = 1,
            mediumThreshold = 2,  // payments require 2 signers
            highThreshold = 2
        )
    )
    .setTimeout(180)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

tx.sign(primaryKeyPair)
val response = server.submitTransaction(tx.toEnvelopeXdrBase64())
println("Multi-sig configured: ${response.successful}")

// Verify signers
val updated = server.accounts().account(primaryId)
for (signer in updated.signers) {
    println("Signer: ${signer.key} weight: ${signer.weight}")
}

// Multi-sig payment (requires 2 signatures to meet medium threshold)
val refreshed = server.accounts().account(primaryId)
val paymentTx = TransactionBuilder(
    sourceAccount = Account(primaryId, refreshed.sequenceNumber),
    network = network
)
    .addOperation(
        PaymentOperation(
            destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
            asset = AssetTypeNative,
            amount = "50.0"
        )
    )
    .setTimeout(180)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

// Both signers sign the same transaction
paymentTx.sign(primaryKeyPair)
paymentTx.sign(secondaryKeyPair)

// WRONG: server.submitTransaction(paymentTx) — does NOT accept Transaction objects
// CORRECT: server.submitTransaction(paymentTx.toEnvelopeXdrBase64()) — pass the XDR string
val payResponse = server.submitTransaction(paymentTx.toEnvelopeXdrBase64())
println("Multi-sig tx hash: ${payResponse.hash}")
```

## Multi-Sig XDR Sharing (Remote Signers)

When co-signers are on different machines, use XDR serialization to pass the transaction:

```kotlin
// signerAKeyPair, signerBKeyPair: from the previous steps of this flow
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Signer A: Build and share unsigned XDR
val account = server.accounts().account(signerAId)
val tx = TransactionBuilder(
    sourceAccount = Account(signerAId, account.sequenceNumber),
    network = Network.TESTNET
)
    .addOperation(
        PaymentOperation(
            destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
            asset = AssetTypeNative,
            amount = "100.0"
        )
    )
    .setTimeout(300)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

val unsignedXdr = tx.toEnvelopeXdrBase64()
// Send unsignedXdr to Signer B

// Signer B: Decode, inspect (see xdr.md), sign, return
val received = AbstractTransaction.fromEnvelopeXdr(unsignedXdr, Network.TESTNET)
received.sign(signerBKeyPair)
val partiallySigned = received.toEnvelopeXdrBase64()

// Signer A: Add final signature and submit
val withBSig = AbstractTransaction.fromEnvelopeXdr(partiallySigned, Network.TESTNET)
withBSig.sign(signerAKeyPair)
val response = server.submitTransaction(withBSig.toEnvelopeXdrBase64())
println("Multi-sig submitted: ${response.successful}")
```

## Fee-Bump Transactions

Build the inner transaction with a low base fee, then wrap it in a fee bump with a higher fee:

```kotlin
// innerAccountId, innerKeyPair: from the previous steps of this flow
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Step 1: Build inner transaction with low fee
val innerAccount = server.accounts().account(innerAccountId)
val innerTx = TransactionBuilder(
    sourceAccount = Account(innerAccountId, innerAccount.sequenceNumber),
    network = network
)
    .addOperation(
        PaymentOperation(
            destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
            asset = AssetTypeNative,
            amount = "10.0"
        )
    )
    .setBaseFee(100) // low fee: 100 stroops per operation
    .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
    .build()

innerTx.sign(innerKeyPair)

// Step 2: Wrap in fee bump with higher fee
// WRONG: FeeBumpTransaction(innerTx) — constructor is private
// CORRECT: Use FeeBumpTransactionBuilder or FeeBumpTransaction.createWithBaseFee()
val feeBump = FeeBumpTransactionBuilder(innerTx)
    .setBaseFee(1000) // higher fee per operation
    .setFeeSource(feePayerKeyPair.getAccountId())
    .build()

feeBump.sign(feePayerKeyPair)

// WRONG: server.submitTransaction(feeBump) — does NOT accept FeeBumpTransaction objects
// CORRECT: server.submitTransaction(feeBump.toEnvelopeXdrBase64())
val response = server.submitTransaction(feeBump.toEnvelopeXdrBase64())
println("Fee bump success: ${response.successful}")
```

**Alternative:** Use the static factory method directly instead of the builder:

```kotlin
// innerTx: from the previous steps of this flow
val feePayerKeyPair = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
// createWithBaseFee calculates total fee as: baseFee * (numInnerOperations + 1)
val feeBump = FeeBumpTransaction.createWithBaseFee(
    feeSource = feePayerKeyPair.getAccountId(),
    baseFee = 1000,
    innerTransaction = innerTx
)
feeBump.sign(feePayerKeyPair)
```

**Fee builder rules:**
- `setBaseFee()` sets per-operation fee (recommended) — total = baseFee * (numOps + 1)
- `setFee()` sets exact total fee — must be >= inner transaction fee
- You must call EITHER `setBaseFee()` OR `setFee()`, not both
- `setFeeSource()` is required — the account paying the bumped fee

## Sponsored Reserves

Use the "sandwich" pattern: Begin → sponsored operations → End.

```kotlin
val network = Network.TESTNET
val account = Account("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", 1L)
val server = HorizonServer("https://horizon-testnet.stellar.org")

val sponsorKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val sponsoredKeyPair = KeyPair.random()
val sponsorId = sponsorKeyPair.getAccountId()
val sponsoredId = sponsoredKeyPair.getAccountId()

val sponsorAccount = server.accounts().account(sponsorId)

// Sponsor creation of a new account (sandwich pattern)
val tx = TransactionBuilder(
    sourceAccount = Account(sponsorId, sponsorAccount.sequenceNumber),
    network = network
)
    .addOperation(
        BeginSponsoringFutureReservesOperation(sponsoredId = sponsoredId)
    )
    .addOperation(
        CreateAccountOperation(destination = sponsoredId, startingBalance = "0")
    )
    .addOperation(
        // EndSponsoring must have the sponsored account as its source
        EndSponsoringFutureReservesOperation().also {
            it.sourceAccount = sponsoredId
        }
    )
    .setTimeout(180)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

// Both parties must sign
tx.sign(sponsorKeyPair)
tx.sign(sponsoredKeyPair)

val response = server.submitTransaction(tx.toEnvelopeXdrBase64())
println("Sponsorship success: ${response.successful}")

// Verify sponsorship counts (numSponsoring and numSponsored are Int? — nullable)
val sponsor = server.accounts().account(sponsorId)
if (sponsor.numSponsoring != null) println("Sponsoring: ${sponsor.numSponsoring}")
else println("Sponsoring: N/A")
if (sponsor.numSponsored != null) println("Sponsored: ${sponsor.numSponsored}")
else println("Sponsored: N/A")

// Sponsored account exists with 0 XLM (reserves are sponsored)
val sponsored = server.accounts().account(sponsoredId)
println("Sponsored account sponsor: ${sponsored.sponsor}")
```

### Revoking Sponsorship

Use `RevokeSponsorshipOperation` with the appropriate `Sponsorship` sealed class variant:

```kotlin
// issuerId: from the previous steps of this flow
val signerAccountId = "GBVPKXWMAB3FIUJB6T7LF66DABKKA2ZHRHDOQZ25GBAEFZVHTBPJNOJI"
val sponsoredId = "GDWUSKGGFDI4FRXK5EBTRECZSVQSSWJHHJOGH6JWG3AUMFFMQ435DIAG"
// Revoke sponsorship of an account
val revokeOp = RevokeSponsorshipOperation(
    sponsorship = Sponsorship.Account(accountId = sponsoredId)
)

// Revoke sponsorship of a trustline
val revokeTrustlineOp = RevokeSponsorshipOperation(
    sponsorship = Sponsorship.TrustLine(
        accountId = sponsoredId,
        asset = AssetTypeCreditAlphaNum4("USD", issuerId)
    )
)

// Revoke sponsorship of a data entry
val revokeDataOp = RevokeSponsorshipOperation(
    sponsorship = Sponsorship.Data(
        accountId = sponsoredId,
        dataName = "myKey"
    )
)

// Revoke sponsorship of a signer
val revokeSignerOp = RevokeSponsorshipOperation(
    sponsorship = Sponsorship.Signer(
        accountId = sponsoredId,
        signerKey = SignerKey.ed25519PublicKey(signerAccountId)
    )
)
```

## Claimable Balances

### Create a Claimable Balance

```kotlin
val claimantId = "GCUZ6YLL5RQBTYLTTQLPCM73C5XAIUGK2TIMWQH7HPSGWVS2KJ2F3CHS"
val network = Network.TESTNET
val senderKeyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val server = HorizonServer("https://horizon-testnet.stellar.org")

val senderId = senderKeyPair.getAccountId()

val account = server.accounts().account(senderId)

// Create claimable balance with predicates
val claimants = listOf(
    // Claimant can claim unconditionally
    Claimant(
        destination = claimantId,
        predicate = ClaimPredicate.Unconditional
    ),
    // Sender can reclaim after 7 days (604800 seconds)
    Claimant(
        destination = senderId,
        predicate = ClaimPredicate.Not(
            ClaimPredicate.BeforeRelativeTime(seconds = 604800)
        )
    )
)

val tx = TransactionBuilder(
    sourceAccount = Account(senderId, account.sequenceNumber),
    network = network
)
    .addOperation(
        CreateClaimableBalanceOperation(
            asset = AssetTypeNative,
            amount = "100.0",
            claimants = claimants
        )
    )
    .setTimeout(180)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

tx.sign(senderKeyPair)
val response = server.submitTransaction(tx.toEnvelopeXdrBase64())
println("Created claimable balance: ${response.successful}")
```

After submitting, extract the balance ID by querying effects for the transaction:

```kotlin
import com.soneso.stellar.sdk.horizon.responses.effects.ClaimableBalanceCreatedEffectResponse

val balanceId = "00000000929b20b72e5890ab51c24f1cc46fa01c4f318d8d33367d24dd614cfdf5491072" // 72-char hex id from Horizon
// response: from the previous steps of this flow
val server = HorizonServer("https://horizon-testnet.stellar.org")

// ClaimableBalanceCreatedEffectResponse is in the effects sub-package (not covered by sdk.* wildcard)
val effects = server.effects().forTransaction(response.hash).execute()
var balanceId: String? = null
for (effect in effects.records) {
    if (effect is ClaimableBalanceCreatedEffectResponse) {
        balanceId = effect.balanceId  // hex-encoded 72-character balance ID
        break
    }
}
println("Balance ID: $balanceId")
```

### Claim a Claimable Balance

```kotlin
// claimantKeyPair: from the previous steps of this flow
val server = HorizonServer("https://horizon-testnet.stellar.org")
// Query claimable balances for the claimant
val balances = server.claimableBalances()
    .forClaimant(claimantId)
    .execute()

if (balances.records.isNotEmpty()) {
    val balanceId = balances.records.first().id
    println("Claiming balance: $balanceId")

    val claimantAccount = server.accounts().account(claimantId)
    val claimTx = TransactionBuilder(
        sourceAccount = Account(claimantId, claimantAccount.sequenceNumber),
        network = network
    )
        .addOperation(
            // balanceId is the full 72-character hex-encoded ID from the query
            ClaimClaimableBalanceOperation(balanceId = balanceId)
        )
        .setTimeout(180)
        .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
        .build()

    claimTx.sign(claimantKeyPair)
    val claimResponse = server.submitTransaction(claimTx.toEnvelopeXdrBase64())
    println("Claimed: ${claimResponse.successful}")
}
```

### Predicate Types

```kotlin
// Unconditional — can claim at any time
ClaimPredicate.Unconditional

// Before absolute time — claimable only before a Unix timestamp
ClaimPredicate.BeforeAbsoluteTime(timestamp = 1700000000L)

// Before relative time — claimable within N seconds of creation
ClaimPredicate.BeforeRelativeTime(seconds = 86400L) // 24 hours

// NOT — negate a predicate
ClaimPredicate.Not(ClaimPredicate.BeforeRelativeTime(seconds = 3600L))

// AND — both must be true
ClaimPredicate.And(
    left = ClaimPredicate.BeforeAbsoluteTime(timestamp = 1700000000L),
    right = ClaimPredicate.Not(ClaimPredicate.BeforeRelativeTime(seconds = 3600L))
)

// OR — either must be true
ClaimPredicate.Or(
    left = ClaimPredicate.Unconditional,
    right = ClaimPredicate.BeforeRelativeTime(seconds = 86400L)
)
```

## Liquidity Pools

### Deposit into a Liquidity Pool

```kotlin
val issuerAccountId = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
val network = Network.TESTNET
val server = HorizonServer("https://horizon-testnet.stellar.org")

val accountId = keyPair.getAccountId()

val assetA: Asset = AssetTypeNative
val assetB: Asset = Asset.createNonNativeAsset("USDC", issuerAccountId)

// Assets must be in lexicographic order (assetA < assetB)
// AssetTypeNative is always first in sort order
val pool = LiquidityPool(assetA = assetA, assetB = assetB)

// Step 1: Establish trustline to the pool share asset
val account = server.accounts().account(accountId)

val trustTx = TransactionBuilder(
    sourceAccount = Account(accountId, account.sequenceNumber),
    network = network
)
    .addOperation(
        ChangeTrustOperation(
            liquidityPool = pool,
            limit = ChangeTrustOperation.MAX_LIMIT
        )
    )
    .setTimeout(180)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

trustTx.sign(keyPair)
server.submitTransaction(trustTx.toEnvelopeXdrBase64())

// Step 2: Deposit into the pool
val refreshedAccount = server.accounts().account(accountId) // refresh sequence number

// WRONG: pool.poolId — no such property
// CORRECT: pool.getLiquidityPoolId() — suspend function that computes the SHA-256 pool ID
val poolId = pool.getLiquidityPoolId()

val depositTx = TransactionBuilder(
    sourceAccount = Account(accountId, refreshedAccount.sequenceNumber),
    network = network
)
    .addOperation(
        LiquidityPoolDepositOperation(
            liquidityPoolId = poolId,
            maxAmountA = "100.0",
            maxAmountB = "100.0",
            minPrice = Price.fromString("0.5"),
            maxPrice = Price.fromString("2.0")
        )
    )
    .setTimeout(180)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

depositTx.sign(keyPair)
val response = server.submitTransaction(depositTx.toEnvelopeXdrBase64())
println("Deposited to pool: $poolId")
```

### Withdraw from a Liquidity Pool

```kotlin
// currentSequence: from the previous steps of this flow
val server = HorizonServer("https://horizon-testnet.stellar.org")
val withdrawTx = TransactionBuilder(
    sourceAccount = Account(accountId, currentSequence),
    network = network
)
    .addOperation(
        LiquidityPoolWithdrawOperation(
            liquidityPoolId = poolId,
            amount = "50.0",        // pool shares to redeem
            minAmountA = "20.0",    // minimum asset A to receive
            minAmountB = "20.0"     // minimum asset B to receive
        )
    )
    .setTimeout(180)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

withdrawTx.sign(keyPair)
val response = server.submitTransaction(withdrawTx.toEnvelopeXdrBase64())
println("Withdrew from pool: ${response.successful}")
```

### Price Construction

```kotlin
// From numerator/denominator (exact)
val price = Price(numerator = 1, denominator = 2) // 0.5

// From decimal string (approximated via continued fractions)
val price2 = Price.fromString("1.5") // becomes 3/2
```

## Muxed Accounts

Muxed accounts are virtual sub-accounts sharing a single G... account, identified by a ULong ID and an M... address.

```kotlin
// innerTx: from the previous steps of this flow
// Create a muxed account (virtual sub-account under a G... account)
// WRONG: MuxedAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", BigInt.from(12345)) — no BigInt in KMP SDK
// CORRECT: MuxedAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", 12345UL) — use ULong
val muxed = MuxedAccount("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54", 12345UL)
println("Muxed address: ${muxed.address}")        // M... address
println("Base account: ${muxed.accountId}")        // G... address (alias: ed25519AccountId)
println("Sub ID: ${muxed.id}")                     // ULong?

// Parse from M... address
val fromAddress = MuxedAccount("MAAAAAAAAAAAAAB7BQ2L7E5NBWMXDUCMZSIPOBKRDSBYVLMXGSSKF6YNPIB7Y77ITLVL6")
println("Decoded base account: ${fromAddress.accountId}")
println("Decoded sub ID: ${fromAddress.id}")

// Use in payment (muxed as source account override)
val payment = PaymentOperation(
    destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
    asset = AssetTypeNative,
    amount = "10.0"
)
payment.sourceAccount = muxed.address // set M... address as operation source

// Muxed accounts as fee bump source
val feeBump = FeeBumpTransactionBuilder(innerTx)
    .setBaseFee(200)
    .setFeeSource(muxed.address) // M... address accepted
    .build()
```

## Async Transaction Submission

Submit without waiting for ingestion (returns immediately):

```kotlin
val server = HorizonServer("https://horizon-testnet.stellar.org")
// WRONG: server.submitAsyncTransaction(tx) — no such method name
// CORRECT: server.submitTransactionAsync(tx.toEnvelopeXdrBase64())
val asyncResponse = server.submitTransactionAsync(tx.toEnvelopeXdrBase64())

println("Hash: ${asyncResponse.hash}")
println("Status: ${asyncResponse.txStatus}")
// Possible statuses: PENDING, DUPLICATE, TRY_AGAIN_LATER, ERROR
// Poll horizon transactions endpoint later to check final result

when (asyncResponse.txStatus) {
    SubmitTransactionAsyncResponse.TransactionStatus.PENDING ->
        println("Transaction pending, will be included soon")
    SubmitTransactionAsyncResponse.TransactionStatus.DUPLICATE ->
        println("Transaction already submitted")
    SubmitTransactionAsyncResponse.TransactionStatus.TRY_AGAIN_LATER ->
        println("Network busy, retry later")
    SubmitTransactionAsyncResponse.TransactionStatus.ERROR ->
        println("Transaction failed: ${asyncResponse.errorResultXdr}")
}
```
