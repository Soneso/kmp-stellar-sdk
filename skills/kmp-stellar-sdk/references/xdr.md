# XDR Encoding & Decoding Reference

All code assumes standard SDK imports and a `suspend` context:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.scval.Scv
import com.ionspin.kotlin.bignum.integer.BigInteger
```

## Table of Contents

- [Transaction Envelope Encoding](#transaction-envelope-encoding)
- [Transaction Envelope Decoding](#transaction-envelope-decoding)
- [Scv Factory Methods (SCValXdr)](#scv-factory-methods-scvalxdr)
- [Building Vec and Map Values](#building-vec-and-map-values)
- [Reading Values from SCValXdr](#reading-values-from-scvalxdr)
- [SCValXdr Base64 Serialization](#scvalxdr-base64-serialization)
- [Address Construction](#address-construction)
- [Ledger Key Construction](#ledger-key-construction)
- [Soroban Transaction Data Inspection](#soroban-transaction-data-inspection)
- [Transaction Inspection Before Signing](#transaction-inspection-before-signing)
- [SorobanAuthorizationEntry XDR](#sorobanauthorizationentry-xdr)

## Transaction Envelope Encoding

Convert a signed transaction to base64 XDR for storage, sharing, or submission:

```kotlin
// After building and signing a transaction (see operations.md)
val xdrBase64: String = transaction.toEnvelopeXdrBase64()
println("Signed XDR: $xdrBase64")
```

## Transaction Envelope Decoding

Parse a base64 XDR string back into a transaction object:

```kotlin
val decoded: AbstractTransaction = AbstractTransaction.fromEnvelopeXdr(xdrBase64, Network.TESTNET)

when (decoded) {
    is Transaction -> {
        println("Source: ${decoded.sourceAccount}")
        println("Fee: ${decoded.fee}") // fee: Long (stroops)
        println("Sequence: ${decoded.sequenceNumber}")
        println("Operations: ${decoded.operations.size}")
        println("Signatures: ${decoded.signatures.size}")
    }
    is FeeBumpTransaction -> {
        println("Fee source: ${decoded.feeSource}")
        println("Fee: ${decoded.fee}")
        val inner: Transaction = decoded.innerTransaction
        println("Inner tx ops: ${inner.operations.size}")
    }
}
```

```
// WRONG: AbstractTransaction.fromEnvelopeXdrString(xdr) — no such method
// CORRECT: AbstractTransaction.fromEnvelopeXdr(xdr, network) — requires Network parameter

// WRONG: decoded.sourceAccount.accountId — sourceAccount is a String, not an object
// CORRECT: decoded.sourceAccount — already a String (G... or M... address)
```

## Scv Factory Methods (SCValXdr)

The `Scv` object provides factory methods for creating and reading Soroban contract values. All methods use **full type names** (not abbreviations).

```
// WRONG: Scv.toU32(42u) — abbreviated names do NOT exist
// CORRECT: Scv.toUint32(42u) — use full type name
// WRONG: Scv.toI64(100L) — abbreviated names do NOT exist
// CORRECT: Scv.toInt64(100L) — use full type name
```

| Category | To SCValXdr | From SCValXdr | Kotlin Type |
|----------|-------------|---------------|-------------|
| **Primitives** | | | |
| Bool | `Scv.toBoolean(value)` | `Scv.fromBoolean(scVal)` | `Boolean` |
| Void | `Scv.toVoid()` | `Scv.fromVoid(scVal)` | `Unit` |
| u32 | `Scv.toUint32(value)` | `Scv.fromUint32(scVal)` | `UInt` |
| i32 | `Scv.toInt32(value)` | `Scv.fromInt32(scVal)` | `Int` |
| u64 | `Scv.toUint64(value)` | `Scv.fromUint64(scVal)` | `ULong` |
| i64 | `Scv.toInt64(value)` | `Scv.fromInt64(scVal)` | `Long` |
| Timepoint | `Scv.toTimePoint(value)` | `Scv.fromTimePoint(scVal)` | `ULong` |
| Duration | `Scv.toDuration(value)` | `Scv.fromDuration(scVal)` | `ULong` |
| **128-bit** | | | |
| i128 | `Scv.toInt128(value)` | `Scv.fromInt128(scVal)` | `BigInteger` |
| u128 | `Scv.toUint128(value)` | `Scv.fromUint128(scVal)` | `BigInteger` |
| **256-bit** | | | |
| i256 | `Scv.toInt256(value)` | `Scv.fromInt256(scVal)` | `BigInteger` |
| u256 | `Scv.toUint256(value)` | `Scv.fromUint256(scVal)` | `BigInteger` |
| **Data** | | | |
| Symbol | `Scv.toSymbol(str)` | `Scv.fromSymbol(scVal)` | `String` |
| String | `Scv.toString(str)` | `Scv.fromString(scVal)` | `String` |
| Bytes | `Scv.toBytes(bytes)` | `Scv.fromBytes(scVal)` | `ByteArray` |
| **Address** | | | |
| Address (XDR) | `Scv.toAddress(scAddress)` | `Scv.fromAddress(scVal)` | `SCAddressXdr` |
| **Collections** | | | |
| Vec | `Scv.toVec(list)` | `Scv.fromVec(scVal)` | `List<SCValXdr>` |
| Map | `Scv.toMap(linkedMap)` | `Scv.fromMap(scVal)` | `LinkedHashMap<SCValXdr, SCValXdr>` |
| **Special** | | | |
| Error | `Scv.toError(error)` | `Scv.fromError(scVal)` | `SCErrorXdr` |
| Instance | `Scv.toContractInstance(inst)` | `Scv.fromContractInstance(scVal)` | `SCContractInstanceXdr` |
| LedgerKey Instance | `Scv.toLedgerKeyContractInstance()` | `Scv.fromLedgerKeyContractInstance(scVal)` | `Unit` |

**BigInteger note:** 128-bit and 256-bit types use `com.ionspin.kotlin.bignum.integer.BigInteger` (KMP library), not `java.math.BigInteger`.

```kotlin
// WRONG: Scv.toInt128(java.math.BigInteger.valueOf(1000000))
// CORRECT: Scv.toInt128(BigInteger.fromLong(1000000))
// BigInteger is com.ionspin.kotlin.bignum.integer.BigInteger
```

```
// WRONG: Scv.toUint32(42) — UInt required, not Int
// CORRECT: Scv.toUint32(42u) — use unsigned literal
// WRONG: Scv.toUint64(100L) — ULong required, not Long
// CORRECT: Scv.toUint64(100uL) — use unsigned literal
```

## Building Vec and Map Values

```kotlin
// Vec: ordered list of values
val vec: SCValXdr = Scv.toVec(listOf(
    Scv.toSymbol("hello"),
    Scv.toUint32(42u),
    Scv.toBoolean(true),
))

// Map: key-value pairs using LinkedHashMap (order preserved)
val map: SCValXdr = Scv.toMap(linkedMapOf(
    Scv.toSymbol("name") to Scv.toString("Alice"),
    Scv.toSymbol("balance") to Scv.toInt128(BigInteger.fromLong(1000000)),
))
```

```
// WRONG: Scv.toMap(mapOf(...)) — requires LinkedHashMap, not Map
// CORRECT: Scv.toMap(linkedMapOf(...)) — use linkedMapOf for deterministic ordering
```

## Reading Values from SCValXdr

SCValXdr is a sealed class. Pattern-match on its subclasses to extract values, or use `Scv.from*()` methods for type-safe extraction:

```kotlin
val resultVal: SCValXdr = // from contract invocation result

// Using Scv convenience methods (recommended — throws on type mismatch)
val boolVal: Boolean = Scv.fromBoolean(resultVal)
val symbolVal: String = Scv.fromSymbol(resultVal)
val stringVal: String = Scv.fromString(resultVal)
val u32Val: UInt = Scv.fromUint32(resultVal)
val i64Val: Long = Scv.fromInt64(resultVal)
val i128Val: BigInteger = Scv.fromInt128(resultVal)

// Using pattern matching (for mixed-type handling)
when (resultVal) {
    is SCValXdr.B -> println("Bool: ${resultVal.value}")
    is SCValXdr.Sym -> println("Symbol: ${resultVal.value.value}")
    is SCValXdr.Str -> println("String: ${resultVal.value.value}")
    is SCValXdr.U32 -> println("u32: ${resultVal.value.value}")
    is SCValXdr.I32 -> println("i32: ${resultVal.value.value}")
    is SCValXdr.U64 -> println("u64: ${resultVal.value.value}")
    is SCValXdr.I64 -> println("i64: ${resultVal.value.value}")
    is SCValXdr.Vec -> {
        val items: List<SCValXdr> = resultVal.value?.value ?: emptyList()
        println("Vec with ${items.size} items")
    }
    is SCValXdr.Map -> {
        val entries = resultVal.value?.value ?: emptyList()
        entries.forEach { entry ->
            println("${entry.key} -> ${entry.`val`}")
        }
    }
    is SCValXdr.Address -> {
        val address = Address.fromSCAddress(resultVal.value)
        println("Address: ${address.getEncodedAddress()}")
    }
    else -> println("Other type: ${resultVal.discriminant}")
}
```

```
// WRONG: resultVal.sym — SCValXdr does NOT have property accessors
// CORRECT: (resultVal as SCValXdr.Sym).value.value — pattern match first
// CORRECT: Scv.fromSymbol(resultVal) — use Scv convenience method

// WRONG: entry.val — 'val' is a Kotlin keyword
// CORRECT: entry.`val` — use backtick-escaped name for the value field
```

## SCValXdr Base64 Serialization

Serialize individual values for storage, caching, or RPC calls. Requires importing the extension functions:

```kotlin
import com.soneso.stellar.sdk.xdr.toXdrBase64
import com.soneso.stellar.sdk.xdr.fromXdrBase64

// Encode to base64
val original: SCValXdr = Scv.toSymbol("hello")
val base64: String = original.toXdrBase64()

// Decode from base64
val restored: SCValXdr = SCValXdr.fromXdrBase64(base64)
println(Scv.fromSymbol(restored)) // "hello"
```

```
// WRONG: original.toBase64EncodedXdrString() — no such method
// CORRECT: original.toXdrBase64() — extension function from XdrExtensions.kt

// WRONG: SCValXdr.fromBase64EncodedXdrString(base64) — no such method
// CORRECT: SCValXdr.fromXdrBase64(base64) — extension function from XdrExtensions.kt
```

## Address Construction

The `Address` class provides a high-level way to work with Stellar addresses and convert to XDR:

```kotlin
// Parse from string (auto-detects type)
val accountAddr = Address("GABC...")     // G... account
val contractAddr = Address("CABC...")    // C... contract
val muxedAddr = Address("MABC...")       // M... muxed account

// Convert to SCAddressXdr (for low-level XDR construction)
val scAddress: SCAddressXdr = accountAddr.toSCAddress()

// Convert directly to SCValXdr (for contract arguments)
val addressArg: SCValXdr = contractAddr.toSCVal()

// Convert back from SCAddressXdr
val restored = Address.fromSCAddress(scAddress)
val strKey: String = restored.getEncodedAddress() // G... or C... format

// Convert back from SCValXdr
val fromVal = Address.fromSCVal(addressArg)
```

```
// WRONG: XdrSCAddress.forAccountId("GABC...") — no such class
// CORRECT: Address("GABC...").toSCAddress() — use Address class

// WRONG: scAddress.toStrKey() — SCAddressXdr has no toStrKey()
// CORRECT: Address.fromSCAddress(scAddress).getEncodedAddress() — convert via Address
```

## Ledger Key Construction

Build XDR ledger keys for querying specific ledger entries. These are low-level XDR types constructed directly:

```kotlin
import com.soneso.stellar.sdk.xdr.toXdrBase64

// Account
val accountKey = LedgerKeyXdr.Account(
    LedgerKeyAccountXdr(
        accountId = KeyPair.fromAccountId("GABC...").getXdrAccountId()
    )
)

// Trustline — must convert AssetXdr to TrustLineAssetXdr manually
val assetXdr = Asset.create("USDC:GABC...").toXdr() // returns AssetXdr
val trustLineAsset = when (assetXdr) {
    is AssetXdr.AlphaNum4 -> TrustLineAssetXdr.AlphaNum4(assetXdr.value)
    is AssetXdr.AlphaNum12 -> TrustLineAssetXdr.AlphaNum12(assetXdr.value)
    is AssetXdr.Void -> TrustLineAssetXdr.Void
}
val trustKey = LedgerKeyXdr.TrustLine(
    LedgerKeyTrustLineXdr(
        accountId = KeyPair.fromAccountId("GABC...").getXdrAccountId(),
        asset = trustLineAsset
    )
)

// Contract data
val contractDataKey = LedgerKeyXdr.ContractData(
    LedgerKeyContractDataXdr(
        contract = Address("CABC...").toSCAddress(),
        key = Scv.toSymbol("counter"),
        durability = ContractDataDurabilityXdr.PERSISTENT
    )
)

// Contract instance (metadata)
val instanceKey = LedgerKeyXdr.ContractData(
    LedgerKeyContractDataXdr(
        contract = Address("CABC...").toSCAddress(),
        key = Scv.toLedgerKeyContractInstance(),
        durability = ContractDataDurabilityXdr.PERSISTENT
    )
)

// Contract code (WASM)
val codeKey = LedgerKeyXdr.ContractCode(
    LedgerKeyContractCodeXdr(
        hash = HashXdr(wasmHashBytes) // 32-byte WASM hash
    )
)

// Use with sorobanServer.getLedgerEntries(listOf(contractDataKey))
// WRONG: sorobanServer.getLedgerEntries(listOf(contractDataKey.toXdrBase64()))
// -- getLedgerEntries takes Collection<LedgerKeyXdr>, NOT List<String>
```

Durability options for contract data:

| Durability | Usage |
|------------|-------|
| `ContractDataDurabilityXdr.PERSISTENT` | Long-lived data that survives TTL reset |
| `ContractDataDurabilityXdr.TEMPORARY` | Short-lived data cleared on TTL expiry |

## Soroban Transaction Data Inspection

Inspect Soroban resource allocation from simulation results or existing transactions:

```kotlin
import com.soneso.stellar.sdk.xdr.fromXdrBase64

// From simulation response
val sim = sorobanServer.simulateTransaction(transaction)
val txData: SorobanTransactionDataXdr? = sim.parseTransactionData()
if (txData != null) {
    val resources: SorobanResourcesXdr = txData.resources
    val footprint: LedgerFootprintXdr = resources.footprint

    println("Instructions: ${resources.instructions.value}")
    println("Disk read bytes: ${resources.diskReadBytes.value}")
    println("Write bytes: ${resources.writeBytes.value}")
    println("Resource fee: ${txData.resourceFee.value}")
    println("Read-only entries: ${footprint.readOnly.size}")
    println("Read-write entries: ${footprint.readWrite.size}")
}

// From base64 string
val parsedData = SorobanTransactionDataXdr.fromXdrBase64(base64SorobanData)
```

```
// WRONG: resources.instructions.uint32 — Uint32Xdr does NOT have .uint32
// CORRECT: resources.instructions.value — Uint32Xdr.value is UInt

// WRONG: txData.resourceFee.int64 — Int64Xdr does NOT have .int64
// CORRECT: txData.resourceFee.value — Int64Xdr.value is Long
```

## Transaction Inspection Before Signing

Always inspect transaction details before signing XDR from external sources:

```kotlin
val parsed: AbstractTransaction = AbstractTransaction.fromEnvelopeXdr(externalXdr, Network.TESTNET)

if (parsed is Transaction) {
    println("Source: ${parsed.sourceAccount}")
    println("Fee: ${parsed.fee} stroops")
    println("Signatures: ${parsed.signatures.size}")
    for (i in parsed.operations.indices) {
        val op = parsed.operations[i]
        println("  Op $i: ${op::class.simpleName}")
        when (op) {
            is PaymentOperation -> {
                println("    Destination: ${op.destination}")
                println("    Amount: ${op.amount}")
            }
            is InvokeHostFunctionOperation -> {
                println("    Soroban invocation")
            }
            else -> {}
        }
    }
    // Check for Soroban resource fees
    if (parsed.sorobanData != null) {
        println("Soroban fee: ${parsed.sorobanData!!.resourceFee.value}")
    }
}
```

## SorobanAuthorizationEntry XDR

Serialize and deserialize authorization entries for remote signing workflows. Use the `Auth` class for signing:

```kotlin
import com.soneso.stellar.sdk.xdr.toXdrBase64
import com.soneso.stellar.sdk.xdr.fromXdrBase64

// Get auth entries from simulation
val sim = sorobanServer.simulateTransaction(transaction)
val authEntries: List<SorobanAuthorizationEntryXdr>? = sim.results?.firstOrNull()?.parseAuth()

// Encode auth entry for transport to remote signer
val authEntry: SorobanAuthorizationEntryXdr = authEntries!!.first()
val base64: String = authEntry.toXdrBase64()
// Send base64 to remote signer...

// Remote signer: decode, sign, return
val received: SorobanAuthorizationEntryXdr = SorobanAuthorizationEntryXdr.fromXdrBase64(base64)
val signerKeyPair = KeyPair.fromSecretSeed("S...")
val latestLedger = sorobanServer.getLatestLedger().sequence
val signedEntry: SorobanAuthorizationEntryXdr = Auth.authorizeEntry(
    entry = received,
    signer = signerKeyPair,
    validUntilLedgerSeq = latestLedger + 100L,
    network = Network.TESTNET
)
val signedBase64: String = signedEntry.toXdrBase64()
// Return signedBase64 to invoker...
```

```
// WRONG: authEntry.toBase64EncodedXdrString() — no such method
// CORRECT: authEntry.toXdrBase64() — extension function

// WRONG: SorobanAuthorizationEntry.fromBase64EncodedXdr(base64) — wrong class name
// CORRECT: SorobanAuthorizationEntryXdr.fromXdrBase64(base64) — XDR types use Xdr suffix
```

**Credential arms (`SorobanCredentialsXdr`).** The credentials are a sealed
union over four arms: `Void` (source account, no signature), `Address`
(legacy `SOROBAN_CREDENTIALS_ADDRESS`, the default), `AddressV2`
(`SOROBAN_CREDENTIALS_ADDRESS_V2`), and `AddressWithDelegates`
(`SOROBAN_CREDENTIALS_ADDRESS_WITH_DELEGATES`, wrapping
`SorobanAddressCredentialsWithDelegatesXdr` with a recursive
`SorobanDelegateSignatureXdr` tree). The V2 and WITH_DELEGATES arms (CAP-71) are
valid only on Protocol 27+. `Address` and `AddressV2` wrap
`SorobanAddressCredentialsXdr`; match exhaustively when reading:

```kotlin
import com.soneso.stellar.sdk.xdr.SorobanCredentialsXdr

val address = when (val creds = authEntry.credentials) {
    is SorobanCredentialsXdr.Void -> null
    is SorobanCredentialsXdr.Address -> creds.value.address
    is SorobanCredentialsXdr.AddressV2 -> creds.value.address
    is SorobanCredentialsXdr.AddressWithDelegates -> creds.value.addressCredentials.address
}
```

`Auth` selects the hash preimage per arm: `Address` uses the legacy
`ENVELOPE_TYPE_SOROBAN_AUTHORIZATION`; `AddressV2` and `AddressWithDelegates` use
the address-bound `ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS`
(`HashIDPreimageSorobanAuthorizationWithAddressXdr`).

## Generic XDR Base64 Encoding/Decoding

The SDK provides `toXdrBase64()` and `fromXdrBase64()` extension functions (in `com.soneso.stellar.sdk.xdr`) for these XDR types:

| Type | Encode | Decode |
|------|--------|--------|
| `LedgerKeyXdr` | `.toXdrBase64()` | `LedgerKeyXdr.fromXdrBase64(base64)` |
| `SCValXdr` | `.toXdrBase64()` | `SCValXdr.fromXdrBase64(base64)` |
| `SorobanTransactionDataXdr` | `.toXdrBase64()` | `SorobanTransactionDataXdr.fromXdrBase64(base64)` |
| `SorobanAuthorizationEntryXdr` | `.toXdrBase64()` | `SorobanAuthorizationEntryXdr.fromXdrBase64(base64)` |
| `TransactionEnvelopeXdr` | `.toXdrBase64()` | `TransactionEnvelopeXdr.fromXdrBase64(base64)` |
| `TransactionResultXdr` | `.toXdrBase64()` | `TransactionResultXdr.fromXdrBase64(base64)` |
| `TransactionMetaXdr` | `.toXdrBase64()` | `TransactionMetaXdr.fromXdrBase64(base64)` |
| `LedgerEntryDataXdr` | `.toXdrBase64()` | `LedgerEntryDataXdr.fromXdrBase64(base64)` |
| `LedgerEntryXdr` | `.toXdrBase64()` | `LedgerEntryXdr.fromXdrBase64(base64)` |
| `DiagnosticEventXdr` | `.toXdrBase64()` | `DiagnosticEventXdr.fromXdrBase64(base64)` |

For types **not** in this list, encode/decode manually:

```kotlin
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
fun encodeXdr(xdr: SCAddressXdr): String {
    val writer = XdrWriter()
    xdr.encode(writer)
    return Base64.encode(writer.toByteArray())
}

@OptIn(ExperimentalEncodingApi::class)
fun decodeSCAddress(base64: String): SCAddressXdr {
    val bytes = Base64.decode(base64)
    val reader = XdrReader(bytes)
    return SCAddressXdr.decode(reader)
}
```
