# Transaction Building Implementation Plan

This plan outlines the implementation of transaction building capabilities for the KMP Stellar SDK, following the Java SDK reference implementation.

## Overview

Transaction building allows developers to create, sign, and submit Stellar transactions. This is the core functionality needed to interact with the Stellar network.

## Phase 6: Transaction Building Foundation

### 6.1 Asset Classes
Implement the asset type hierarchy:

1. **Asset (abstract base class)**
   - `type: String` - Asset type (native, credit_alphanum4, credit_alphanum12)
   - `equals()` / `hashCode()` - Asset comparison
   - `compareTo()` - Asset ordering

2. **AssetTypeNative**
   - Represents XLM (Stellar's native asset)
   - Singleton pattern

3. **AssetTypeCreditAlphaNum (abstract)**
   - `code: String` - Asset code
   - `issuer: String` - Issuer account ID
   - Validation for code and issuer

4. **AssetTypeCreditAlphaNum4**
   - 1-4 character asset codes
   - Extends AssetTypeCreditAlphaNum

5. **AssetTypeCreditAlphaNum12**
   - 5-12 character asset codes
   - Extends AssetTypeCreditAlphaNum

**Reference**: `/Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/Asset*.java`

### 6.2 Account Classes

1. **TransactionBuilderAccount (interface)**
   - `accountId: String` - G... address
   - `sequenceNumber: Long` - Current sequence number
   - `incrementSequenceNumber()` - For transaction building

2. **Account (class)**
   - Implements TransactionBuilderAccount
   - `keypair: KeyPair?` - Optional keypair for signing
   - Constructor from account ID and sequence number
   - Constructor from keypair and sequence number

3. **MuxedAccount**
   - Represents both regular (G...) and muxed (M...) accounts
   - `accountId: String` - Base account ID
   - `id: Long?` - Muxed account ID (null for regular accounts)
   - Conversion to/from XDR

**Reference**: `/Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/Account*.java`

### 6.3 Memo Classes

1. **Memo (sealed class)**
   - Base class for all memo types

2. **MemoNone** - No memo

3. **MemoText**
   - `text: String` - UTF-8 text (max 28 bytes)

4. **MemoId**
   - `id: ULong` - Unsigned 64-bit integer

5. **MemoHash**
   - `hash: ByteArray` - 32-byte hash

6. **MemoReturn**
   - `hash: ByteArray` - 32-byte hash

**Reference**: `/Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/Memo*.java`

### 6.4 TimeBounds and Preconditions

1. **TimeBounds**
   - `minTime: Long` - Minimum timestamp (0 = no minimum)
   - `maxTime: Long` - Maximum timestamp (0 = no maximum)
   - Validation

2. **LedgerBounds**
   - `minLedger: Int` - Minimum ledger number
   - `maxLedger: Int` - Maximum ledger number

3. **TransactionPreconditions**
   - `timeBounds: TimeBounds?`
   - `ledgerBounds: LedgerBounds?`
   - `minSequenceNumber: Long?`
   - `minSequenceAge: Long?`
   - `minSequenceGap: Int?`
   - `extraSigners: List<SignerKey>`

**Reference**: `/Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/{TimeBounds,LedgerBounds,TransactionPreconditions}.java`

## Phase 7: Operations

### 7.1 Operation Base Class

1. **Operation (abstract class)**
   - `sourceAccount: MuxedAccount?` - Optional operation source
   - `toXdr()` - Convert to XDR
   - `fromXdr()` - Parse from XDR

### 7.2 Core Operations (Priority 1)

Implement these operations first:

1. **CreateAccountOperation**
   - `destination: String` - New account address
   - `startingBalance: String` - Initial XLM balance

2. **PaymentOperation**
   - `destination: MuxedAccount` - Destination account
   - `asset: Asset` - Asset to send
   - `amount: String` - Amount to send

3. **PathPaymentStrictReceiveOperation**
   - `sendAsset: Asset`, `sendMax: String`
   - `destination: MuxedAccount`
   - `destAsset: Asset`, `destAmount: String`
   - `path: List<Asset>` - Payment path

4. **PathPaymentStrictSendOperation**
   - Similar to StrictReceive but with guaranteed send amount

5. **ChangeTrustOperation**
   - `asset: Asset` - Asset to trust
   - `limit: String` - Trust limit (default = max)

6. **AllowTrustOperation** (deprecated but still needed)
   - `trustor: String` - Account to authorize
   - `assetCode: String` - Asset code
   - `authorize: Boolean` - Authorize or deauthorize

7. **SetOptionsOperation**
   - `inflationDestination: String?`
   - `clearFlags: Int?`, `setFlags: Int?`
   - `masterWeight: Int?`
   - `lowThreshold: Int?`, `mediumThreshold: Int?`, `highThreshold: Int?`
   - `homeDomain: String?`
   - `signer: SignerKey?`, `signerWeight: Int?`

8. **ManageDataOperation**
   - `name: String` - Data entry name
   - `value: ByteArray?` - Data value (null to delete)

9. **BumpSequenceOperation**
   - `bumpTo: Long` - New sequence number

10. **AccountMergeOperation**
    - `destination: MuxedAccount` - Destination for merged funds

### 7.3 Offer Operations (Priority 2)

11. **ManageSellOfferOperation**
    - `selling: Asset`, `buying: Asset`
    - `amount: String`, `price: Price`
    - `offerId: Long` - 0 for new offer

12. **ManageBuyOfferOperation**
    - Similar to ManageSellOffer but with guaranteed buy amount

13. **CreatePassiveSellOfferOperation**
    - Same as ManageSellOffer but passive

### 7.4 Advanced Operations (Priority 3)

14. **ClaimClaimableBalanceOperation**
15. **CreateClaimableBalanceOperation**
16. **BeginSponsoringFutureReservesOperation**
17. **EndSponsoringFutureReservesOperation**
18. **RevokeSponsorship** (multiple types)
19. **ClawbackOperation**
20. **ClawbackClaimableBalanceOperation**
21. **SetTrustLineFlagsOperation**
22. **LiquidityPoolDepositOperation**
23. **LiquidityPoolWithdrawOperation**

### 7.5 Soroban Operations (Priority 4)

24. **InvokeHostFunctionOperation**
25. **ExtendFootprintTTLOperation**
26. **RestoreFootprintOperation**

**Reference**: `/Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/operations/`

## Phase 8: Transaction Building

### 8.1 Transaction Class

1. **Transaction**
   - `sourceAccount: MuxedAccount` - Transaction source
   - `fee: Long` - Transaction fee
   - `sequenceNumber: Long` - Sequence number
   - `operations: List<Operation>` - List of operations
   - `memo: Memo` - Transaction memo
   - `preconditions: TransactionPreconditions?` - Time/ledger bounds
   - `signatures: MutableList<DecoratedSignature>` - Transaction signatures
   - `sign(KeyPair)` - Sign transaction
   - `signHashX(ByteArray)` - Add hash(x) signature
   - `toEnvelopeXdr()` - Convert to XDR envelope
   - `toEnvelopeXdrBase64()` - Base64-encoded XDR
   - `hash(Network)` - Transaction hash
   - `fromEnvelopeXdr()` - Parse from XDR

### 8.2 TransactionBuilder

1. **TransactionBuilder**
   - `sourceAccount: TransactionBuilderAccount` - Source account
   - `baseFee: Long` - Base fee per operation
   - `addOperation(Operation)` - Add operation
   - `addMemo(Memo)` - Add memo
   - `addPreconditions(TransactionPreconditions)` - Add preconditions
   - `setTimeout(Duration)` - Set timeout (creates TimeBounds)
   - `build()` - Build transaction

2. **Builder Pattern**
   ```kotlin
   val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
       .setBaseFee(100)
       .addOperation(
           PaymentOperation.Builder(destination, asset, amount).build()
       )
       .addMemo(MemoText("Payment"))
       .setTimeout(30.seconds)
       .build()
   ```

**Reference**: `/Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/{Transaction,TransactionBuilder}.java`

### 8.3 FeeBumpTransaction

1. **FeeBumpTransaction**
   - `feeSource: MuxedAccount` - Fee source account
   - `baseFee: Long` - New base fee
   - `innerTransaction: Transaction` - Original transaction
   - Similar signing methods

2. **FeeBumpTransactionBuilder**
   - Builder pattern for fee bump transactions

**Reference**: `/Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/FeeBump*.java`

## Phase 9: Network and Signing

### 9.1 Network Class

1. **Network**
   - `networkPassphrase: String` - Network identifier
   - Companion objects for:
     - `PUBLIC` - Stellar public network
     - `TESTNET` - Stellar test network
   - `networkId()` - SHA256 hash of passphrase

**Reference**: `/Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/Network.java`

### 9.2 Signing Support

1. **DecoratedSignature**
   - `hint: ByteArray` - Signature hint (last 4 bytes of public key)
   - `signature: ByteArray` - Actual signature

2. **SignerKey** (sealed class)
   - `Ed25519PublicKey`
   - `PreAuthTx` - Pre-authorized transaction hash
   - `HashX` - Hash(x) signer

**Reference**: `/Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/{DecoratedSignature,SignerKey}.java`

## Phase 10: XDR Integration

### 10.1 XDR Conversion

All classes need bidirectional XDR conversion:
- `toXdr()` - Convert to XDR types
- `fromXdr(xdr)` - Static factory from XDR

### 10.2 Required XDR Types

Ensure these XDR types exist (from code generation):
- `TransactionEnvelope`, `TransactionV1Envelope`, `FeeBumpTransactionEnvelope`
- `Transaction`, `TransactionV0`, `TransactionV1`
- `Operation`, `OperationBody` (union type)
- `Asset`, `AssetType`
- `MuxedAccount`, `AccountID`
- `Memo`, `MemoType`
- `TimeBounds`, `LedgerBounds`, `Preconditions`
- All operation-specific XDR types

## Phase 11: Helper Methods

### 11.1 Horizon Integration

1. **HorizonServer.loadAccount()**
   - Fetch account from Horizon
   - Return TransactionBuilderAccount ready for use

2. **HorizonServer.submitTransaction()**
   - Accept Transaction object (in addition to XDR string)
   - Automatic XDR encoding

### 11.2 Convenience Methods

1. **Asset.create()**
   - Factory method: `Asset.create("native")` or `Asset.create("USD:ISSUER")`

2. **Amount Parsing**
   - Utility functions for stroops ↔ decimal conversion
   - `amountToStroops(String)` / `stroopsToAmount(Long)`

## Implementation Priority

**Phase 6** (Foundation) → **Phase 7.1-7.2** (Core Operations) → **Phase 8** (Transaction Building) → **Phase 9** (Network/Signing) → **Phase 10** (XDR) → **Phase 7.3-7.5** (Remaining Operations) → **Phase 11** (Helpers)

## Testing Strategy

1. **Unit Tests**
   - Each operation class
   - Transaction building
   - XDR round-trip (to/from XDR)
   - Signature verification

2. **Integration Tests**
   - Build transaction, submit to testnet
   - Verify transaction appears in Horizon
   - Test all operation types on testnet

3. **Reference Tests**
   - Use test vectors from Java SDK
   - Ensure XDR compatibility

## Key Differences from Java SDK

1. **Kotlin Features**
   - Use sealed classes for Memo, Asset hierarchy
   - Use data classes for immutability
   - Null safety by default

2. **Multiplatform**
   - All code in commonMain unless platform-specific
   - XDR generation must work on all platforms

3. **Coroutines**
   - Async operations use suspend functions
   - No callbacks or futures

## Success Criteria

- [ ] Can build a simple payment transaction
- [ ] Can sign transaction with KeyPair
- [ ] Can submit transaction to Horizon
- [ ] Can build all 27 operation types
- [ ] XDR encoding matches Java SDK
- [ ] All platforms compile and pass tests
- [ ] `loadAccount()` helper works end-to-end

## Estimated Scope

- **Classes to implement**: ~50-60 classes
- **Operations**: 27 operation classes
- **XDR types**: ~40 types (if not generated)
- **Lines of code**: ~5,000-7,000 LOC
- **Test cases**: ~200-300 tests

This is a substantial effort, estimated at 2-3 weeks for full implementation with proper testing.
