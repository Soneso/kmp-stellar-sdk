# Soroban Smart Contracts Reference

All code uses the KMP Stellar SDK. Import the SDK package:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.*
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
```

## Table of Contents

- [High-Level: ContractClient](#high-level-contractclient)
  - [Install WASM Code](#install-wasm-code)
  - [Deploy Contract Instance](#deploy-contract-instance)
  - [Deploy with Constructor Arguments](#deploy-with-constructor-arguments)
  - [Invoke Contract Methods](#invoke-contract-methods)
  - [Result Parsing with funcResToNative](#result-parsing-with-funcrestonative)
  - [Multi-Auth Contract Invocation](#multi-auth-contract-invocation)
- [Low-Level: SorobanServer](#low-level-sorobanserver)
- [Argument Encoding with Scv](#argument-encoding-with-scv)
- [Automatic Type Conversion](#automatic-type-conversion)
- [Reading Contract Return Values](#reading-contract-return-values)
- [Reading Contract State](#reading-contract-state)
- [TTL Extension and Restore](#ttl-extension-and-restore)
- [Deploy Stellar Asset Contract (SAC)](#deploy-stellar-asset-contract-sac)
- [Contract Introspection](#contract-introspection)
- [Exception Handling](#exception-handling)

---

## High-Level: ContractClient

`ContractClient` handles simulation, signing, and submission automatically. Use this for most contract interactions.

### Install WASM Code

```kotlin
// WRONG: ContractClient.install() returns a hex string, NOT a ByteArray
// CORRECT: wasmId is a hex String ready to use with deployFromWasmId()
val wasmId: String = ContractClient.install(
    wasmBytes = File("contract.wasm").readBytes(),
    source = keyPair.getAccountId(),
    signer = keyPair,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org:443"
)
println("WASM hash: $wasmId")
```

### Deploy Contract Instance

One-step deploy (uploads WASM + deploys contract + loads spec):

```kotlin
val client: ContractClient = ContractClient.deploy(
    wasmBytes = File("contract.wasm").readBytes(),
    source = keyPair.getAccountId(),
    signer = keyPair,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org:443"
)
println("Contract ID: ${client.contractId}")
```

Two-step deploy (reuse WASM for multiple instances):

```kotlin
// Step 1: Install WASM once
val wasmId = ContractClient.install(
    wasmBytes = File("token.wasm").readBytes(),
    source = keyPair.getAccountId(),
    signer = keyPair,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org:443"
)

// Step 2: Deploy instances from WASM ID
// WRONG: deployFromWasmId takes List<SCValXdr> for constructorArgs, NOT Map
// CORRECT: use Scv helpers for manual XDR construction

// WRONG: Scv.toSymbol("MTK") for a token symbol arg -- crashes with UnreachableCodeReached
//        if the contract spec declares the param as SC_SPEC_TYPE_STRING, not SC_SPEC_TYPE_SYMBOL
// CORRECT: match the Scv method to the actual spec type:
//   SC_SPEC_TYPE_STRING -> Scv.toString()
//   SC_SPEC_TYPE_SYMBOL -> Scv.toSymbol()
// The Stellar token contract declares both name and symbol as SC_SPEC_TYPE_STRING.
val client = ContractClient.deployFromWasmId(
    wasmId = wasmId,
    constructorArgs = listOf(
        Scv.toString("MyToken"),  // name: SC_SPEC_TYPE_STRING
        Scv.toString("MTK"),      // symbol: SC_SPEC_TYPE_STRING (NOT toSymbol!)
        Scv.toUint32(7u)
    ),
    source = keyPair.getAccountId(),
    signer = keyPair,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org:443"
)
```

### Deploy from an External Reference (Protocol 28)

A CAP-85 external reference names an owner contract and a tag; the owner's persistent
entry under that tag holds the WASM hash the new instance runs. There is no install
step, and the one-step `deploy(wasmBytes)` has no external reference counterpart --
nothing is uploaded. `ContractClient.deployFromExternalRef` resolves the reference
before the transaction is built (`IllegalArgumentException` for a non-contract owner
before any request, `IllegalStateException` naming the owner and the tag for a missing
or malformed tag entry), loads the spec from the resolved WASM, and returns a ready
client:

```kotlin
val client = ContractClient.deployFromExternalRef(
    executableOwner = "COWNER...",  // "C..." contract id holding the tag entry
    tag = "token-v1",               // matched byte for byte, encoded as UTF-8
    constructorArgs = listOf(Scv.toUint32(7u)),  // List<SCValXdr>, as for deployFromWasmId
    source = keyPair.getAccountId(),
    signer = keyPair,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org:443"
)
```

The `ContractClient` deployment paths (`deploy`, `deployFromWasmId`,
`deployFromExternalRef`) always submit the `CREATE_CONTRACT_V2` host function with
the constructor vector, empty when no args are given.
`InvokeHostFunctionOperation.createContractFromExternalRef(executableOwner: Address,
tag: String, address: Address, constructorArgs: List<SCValXdr>? = null, salt:
ByteArray? = null)` builds the underlying create operation directly, next to
`createContract`; these low-level builders emit plain `CREATE_CONTRACT` when no
constructor args are given and `CREATE_CONTRACT_V2` otherwise, and both
`createContractFromExternalRef` overloads throw `IllegalArgumentException` before
anything is built when `executableOwner` is not a contract address (only a contract
can hold the executable tag entry). Both entry points also take the tag as
`ByteArray` for tags that are not text; the String overloads encode as UTF-8.

`Address.deriveContractId(deployer: Address, salt: ByteArray, network: Network)`
(suspend) returns the contract id ("C...") a deployment creates. The id derives from
deployer, salt and network only (the executable does not enter it), so the address is
known before deploying. The salt is 32 raw bytes, not hex.

### Deploy with Constructor Arguments

The one-step `deploy()` accepts constructor args as `Map<String, Any?>` with automatic type conversion based on the contract spec. The two-step `deployFromWasmId()` accepts `List<SCValXdr>`.

**Always match the exact type from contract introspection** -- do not guess based on convention:

```kotlin
// WRONG: using toSymbol because "token names are symbols" -- crashes
// Scv.toSymbol("MyToken")  // WRONG if spec says String
// CORRECT: spec says String -> use Scv.toString; spec says Symbol -> use Scv.toSymbol

// One-step deploy with Map args (auto-converts based on spec):
val client = ContractClient.deploy(
    wasmBytes = File("token.wasm").readBytes(),
    constructorArgs = mapOf(
        "admin" to keyPair.getAccountId(),  // String -> Address (automatic)
        "decimal" to 7,                      // Int -> U32 (automatic)
        "name" to "MyToken",                // String -> String (automatic)
        "symbol" to "MTK"                  // String -> Symbol (automatic)
    ),
    source = keyPair.getAccountId(),
    signer = keyPair,
    network = Network.TESTNET,
    rpcUrl = "https://soroban-testnet.stellar.org:443"
)
```

### Invoke Contract Methods

Create a client for an existing contract, then use `invoke()`:

```kotlin
val client = ContractClient.forContract(
    contractId = "CABC...",
    rpcUrl = "https://soroban-testnet.stellar.org:443",
    network = Network.TESTNET
)

// Read call -- auto-detected, simulation only, no signing needed
// WRONG: invoke() requires a source account even for reads
// CORRECT: pass source but signer = null for read-only calls

// WRONG: client.invoke(...) without a type parameter when parseResultXdrFn is null
// CORRECT: client.invoke<SCValXdr>(...) -- Kotlin cannot infer T when parseResultXdrFn is null
val balanceXdr: SCValXdr = client.invoke<SCValXdr>(
    functionName = "balance",
    arguments = mapOf("id" to keyPair.getAccountId()),
    source = keyPair.getAccountId(),
    signer = null  // null = read-only
)

// Write call -- auto-detected, simulates + signs + sends
val result: SCValXdr = client.invoke<SCValXdr>(
    functionName = "transfer",
    arguments = mapOf(
        "from" to keyPair.getAccountId(),
        "to" to "GDEST...",
        "amount" to 1000000
    ),
    source = keyPair.getAccountId(),
    signer = keyPair
)

// With custom result parser
val balance: Long = client.invoke(
    functionName = "balance",
    arguments = mapOf("id" to keyPair.getAccountId()),
    source = keyPair.getAccountId(),
    signer = null,
    parseResultXdrFn = { xdr -> Scv.fromInt128(xdr).toLong() }
)

// With custom options (higher fee, shorter timeout)
val result2: SCValXdr = client.invoke<SCValXdr>(
    functionName = "expensive_op",
    arguments = mapOf("data" to "value"),
    source = keyPair.getAccountId(),
    signer = keyPair,
    options = ClientOptions(
        sourceAccountKeyPair = keyPair,
        contractId = "CABC...",
        network = Network.TESTNET,
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        baseFee = 10000,
        transactionTimeout = 60
    )
)

// Discover available methods
val methods: Set<String> = client.getMethodNames() // {"transfer", "balance", ...}
```

### Result Parsing with funcResToNative

`funcResToNative` converts XDR results back to native Kotlin types using the contract spec:

```kotlin
val client = ContractClient.forContract(contractId, rpcUrl, Network.TESTNET)

// Invoke and get raw XDR
val resultXdr: SCValXdr = client.invoke<SCValXdr>(
    functionName = "balance",
    arguments = mapOf("id" to accountId),
    source = accountId,
    signer = null
)

// Convert to native type using spec
// WRONG: casting SCValXdr directly -- it is a sealed class, not a primitive
// CORRECT: use funcResToNative for spec-aware conversion
val balance = client.funcResToNative("balance", resultXdr) as com.ionspin.kotlin.bignum.integer.BigInteger
println("Balance: $balance")

// Also works with base64-encoded XDR strings
val value = client.funcResToNative("get_value", "AAAAAwAAAAQ=") as UInt
```

Type mapping (Soroban -> Kotlin via funcResToNative):

| Soroban Type | Kotlin Type |
|---|---|
| u32 | UInt |
| i32 | Int |
| u64 | ULong |
| i64 | Long |
| u128, i128, u256, i256 | BigInteger (com.ionspin.kotlin.bignum.integer) |
| bool | Boolean |
| symbol, string | String |
| address | String (G.../C.../M...) |
| bytes | ByteArray |
| vec\<T\> | List\<T\> |
| map\<K, V\> | List\<Pair\<K, V\>\> |
| option\<T\> | T? |
| void | null |
| struct | Map\<String, Any?\> |
| union | NativeUnionVal |
| enum | UInt |

### Multi-Auth Contract Invocation

When a contract call requires authorization from multiple parties (e.g., a swap), use `buildInvoke()` and `signAuthEntries()`:

```kotlin
val client = ContractClient.forContract(swapContractId, rpcUrl, Network.TESTNET)

val swapTx: AssembledTransaction<SCValXdr> = client.buildInvoke(
    functionName = "swap",
    arguments = mapOf(
        "a" to aliceKeyPair.getAccountId(),
        "b" to bobKeyPair.getAccountId(),
        "token_a" to tokenAContractId,
        "token_b" to tokenBContractId,
        "amount_a" to 100,
        "min_b_for_a" to 45,
        "amount_b" to 50,
        "min_a_for_b" to 9
    ),
    source = aliceKeyPair.getAccountId(),
    signer = aliceKeyPair
)

// Check which non-invoker accounts must sign auth entries
val needsSigning: Set<String> = swapTx.needsNonInvokerSigningBy()

// Sign Bob's auth entries
swapTx.signAuthEntries(bobKeyPair)

// Sign and submit (Alice signs the transaction itself)
val response: SCValXdr = swapTx.signAndSubmit(aliceKeyPair)
```

**Remote signing with delegate:**

```kotlin
val bobPublicOnly = KeyPair.fromAccountId(bobAccountId)
swapTx.signAuthEntries(
    authEntriesSigner = bobPublicOnly,
    authorizeEntryDelegate = { entry, network ->
        // Send to remote server for signing
        val entryXdr = entry.toXdrBase64()
        val signedXdr = remoteSignService.sign(entryXdr)
        SorobanAuthorizationEntryXdr.fromXdrBase64(signedXdr)
    }
)
```

**Protocol 27 auth arms (CAP-71).** Address credentials use one of three arms:
`AddressV2` (the default), legacy `Address` (valid on every network), or
`AddressWithDelegates` (V2/delegates require Protocol 27+; emitting them pre-27
invalidates the tx). Simulation and the high-level
`ContractClient`/`AssembledTransaction` request V2 by default: the
`useUpgradedAuth` key is sent on every simulate request (default `true`), and a
Protocol 27+ RPC (stellar-rpc v27.1.0+) returns `AddressV2` entries in recording
modes; pre-27 RPCs silently ignore the flag and return legacy entries.
`Auth.authorizeInvocation` builds `AddressV2` by default. On a network below
Protocol 27 opt out to the legacy arm: `simulateTransaction(tx, useUpgradedAuth = false)` /
`ClientOptions(useUpgradedAuth = false)` / `Auth.authorizeInvocation(..., authV2 = false)`.
Delegate entries are always built client-side with the low-level `Auth` helpers
and submitted via `SorobanServer`. Build a delegate tree with
`Auth.attachDelegates(entry, validUntilLedgerSeq, listOf(DelegateDescriptor(addr)))`,
then sign each node via `Auth.authorizeEntry(..., options = Auth.AuthOptions(forAddress = nodeAddress))`
(a delegates-only entry keeps a void top-level signature). `needsNonInvokerSigningBy()`
and `signAuthEntries(keyPair)` walk and sign an existing delegate tree by address
but do not create one. When the authorizing address is a contract account (its
`__check_auth` reads storage or consumes delegates), re-simulate in enforcing mode
after signing to capture the footprint before submitting.

**Adding memos or custom preconditions via buildInvoke:**

```kotlin
val tx = client.buildInvoke<SCValXdr>(
    functionName = "transfer",
    arguments = mapOf(
        "from" to fromAccount,
        "to" to toAccount,
        "amount" to 1000
    ),
    source = account,
    signer = keyPair
)

// Customize before signing (raw is the TransactionBuilder before simulation)
// tx.raw?.addMemo(Memo.text("Invoice #12345"))

// Sign and submit
tx.signAndSubmit(keyPair)
```

---

## Low-Level: SorobanServer

Full control over simulation, signing, and submission. Use when you need custom transaction construction.

```kotlin
val server = SorobanServer("https://soroban-testnet.stellar.org:443")
val sender = KeyPair.fromSecretSeed("SXXXXX...")
val account = server.getAccount(sender.getAccountId())

// 1. Build the invocation operation
val operation = InvokeHostFunctionOperation.invokeContractFunction(
    contractAddress = contractId,
    functionName = "transfer",
    parameters = listOf(
        Address(sender.getAccountId()).toSCVal(),
        Address("GDEST...").toSCVal(),
        Scv.toInt128(com.ionspin.kotlin.bignum.integer.BigInteger.fromLong(1000000))
    )
)
val tx = TransactionBuilder(
    sourceAccount = account,
    network = Network.TESTNET
)
    .addOperation(operation)
    .setTimeout(300)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

// 2. Simulate
val sim = server.simulateTransaction(tx)
if (sim.error != null) {
    println("Simulation failed: ${sim.error}")
    return
}

// 3. Apply simulation results, sign, send
val preparedTx = server.prepareTransaction(tx, sim)
preparedTx.sign(sender)
val sendResp = server.sendTransaction(preparedTx)

// 4. Poll for result
if (sendResp.status == SendTransactionStatus.PENDING) {
    val txResp = server.pollTransaction(
        hash = sendResp.hash!!,
        maxAttempts = 30,
        sleepStrategy = { 3000L }
    )

    if (txResp.status == GetTransactionStatus.SUCCESS) {
        val result: SCValXdr? = txResp.getResultValue()
        println("Result: $result")
    }
}
```

**Low-level deployment** follows the same simulate -> sign -> poll pattern:

```kotlin
// Upload WASM
val uploadOp = InvokeHostFunctionOperation.uploadContractWasm(wasmBytes)
// Build tx, simulate, sign, poll
// val wasmId: String? = txResponse.getWasmId()

// Create contract instance
val createOp = InvokeHostFunctionOperation.createContract(
    wasmId = wasmId!!,
    address = Address(deployer.getAccountId())
)
// Build tx, simulate, sign, poll
// val contractId: String? = txResponse.getCreatedContractId()

// With constructor args
val createWithArgsOp = InvokeHostFunctionOperation.createContract(
    wasmId = wasmId!!,
    address = Address(deployer.getAccountId()),
    constructorArgs = listOf(
        Address(adminId).toSCVal(),
        Scv.toUint32(8u),
        Scv.toString("TokenName"),
        Scv.toSymbol("TOKEN")
    )
)
```

---

## Argument Encoding with Scv

Use the `Scv` utility object for manual XDR construction. All methods are on the `Scv` object:

```kotlin
import com.soneso.stellar.sdk.scval.Scv
```

<!-- WRONG: Scv.toU32() or Scv.toI64() -- these methods do NOT exist -->
<!-- CORRECT: Scv uses full type names: toUint32(), toInt64(), toInt128() etc. -->

| Type | Scv Factory | Example |
|------|-------------|---------|
| Boolean | `Scv.toBoolean(value)` | `Scv.toBoolean(true)` |
| Void | `Scv.toVoid()` | `Scv.toVoid()` |
| u32 | `Scv.toUint32(value)` | `Scv.toUint32(42u)` |
| i32 | `Scv.toInt32(value)` | `Scv.toInt32(-1)` |
| u64 | `Scv.toUint64(value)` | `Scv.toUint64(1000uL)` |
| i64 | `Scv.toInt64(value)` | `Scv.toInt64(-500L)` |
| u128 | `Scv.toUint128(value)` | `Scv.toUint128(BigInteger.fromLong(1000000))` |
| i128 | `Scv.toInt128(value)` | `Scv.toInt128(BigInteger.fromLong(500))` |
| u256 | `Scv.toUint256(value)` | `Scv.toUint256(BigInteger.fromLong(1000000))` |
| i256 | `Scv.toInt256(value)` | `Scv.toInt256(BigInteger.fromLong(500))` |
| Symbol | `Scv.toSymbol(str)` | `Scv.toSymbol("transfer")` |
| String | `Scv.toString(str)` | `Scv.toString("hello")` |
| Bytes | `Scv.toBytes(bytes)` | `Scv.toBytes(keyPair.getPublicKey())` — raw 32-byte Ed25519 key |
| Vec | `Scv.toVec(list)` | `Scv.toVec(listOf(val1, val2))` |
| Map | `Scv.toMap(map)` | `Scv.toMap(linkedMapOf(key to val1))` |
| Address | `Scv.toAddress(scAddr)` | See below |
| Error | `Scv.toError(err)` | `Scv.toError(scErrorXdr)` |
| TimePoint | `Scv.toTimePoint(value)` | `Scv.toTimePoint(1234567890uL)` |
| Duration | `Scv.toDuration(value)` | `Scv.toDuration(3600uL)` |

**WRONG/CORRECT naming:**

```kotlin
// WRONG: Scv.toU32(42u) -- method does NOT exist
// CORRECT: Scv.toUint32(42u)

// WRONG: Scv.toI64(-500L) -- method does NOT exist
// CORRECT: Scv.toInt64(-500L)

// WRONG: Scv.toI128(BigInteger.fromLong(500)) -- method does NOT exist
// CORRECT: Scv.toInt128(BigInteger.fromLong(500))

// WRONG: Scv.fromU32(scVal) -- method does NOT exist
// CORRECT: Scv.fromUint32(scVal)
```

**Address construction** (use the `Address` class, not Scv):

```kotlin
// Account address
val accountAddr: SCValXdr = Address("GABC...").toSCVal()

// Contract address
val contractAddr: SCValXdr = Address("CABC...").toSCVal()

// From SCAddress XDR
val addr: SCValXdr = Scv.toAddress(scAddressXdr)
```

**BigInteger for 128/256-bit types** (uses com.ionspin.kotlin.bignum):

```kotlin
import com.ionspin.kotlin.bignum.integer.BigInteger

val amount128 = Scv.toInt128(BigInteger.fromLong(1000000))
val amount256 = Scv.toUint256(BigInteger.fromLong(999999))

// Reading back
val value = Scv.fromInt128(resultScVal)  // returns BigInteger
```

**Map uses LinkedHashMap** (preserves order for deterministic XDR):

```kotlin
val mapVal = Scv.toMap(linkedMapOf(
    Scv.toSymbol("key1") to Scv.toInt64(100L),
    Scv.toSymbol("key2") to Scv.toString("value")
))
```

---

## Automatic Type Conversion

When using `ContractClient.invoke()` or `ContractClient.deploy()`, arguments are passed as `Map<String, Any?>` and auto-converted based on the contract spec:

```kotlin
// Native Kotlin types -> XDR (automatic via ContractSpec)
val args = mapOf(
    "admin" to "GABC...",     // String -> Address (auto-detected by G prefix)
    "token" to "CABC...",     // String -> Address (auto-detected by C prefix)
    "amount" to 1000,         // Int -> i128 (or u32, etc. based on spec)
    "name" to "MyToken",      // String -> String or Symbol (based on spec)
    "enabled" to true,        // Boolean -> Bool
    "data" to byteArrayOf(1, 2, 3)  // ByteArray -> Bytes
)

// WRONG: BigInteger in auto-conversion maps for i128/u128 -- throws ContractSpecException at runtime
//   "amount" to BigInteger.fromLong(1000000)  // WRONG: BigInteger not accepted
// CORRECT: use Long or Int for i128/u128 in map args (auto-conversion handles range internally)
//   "amount" to 1000000L   // CORRECT: Long -> i128
//   "amount" to 1000000    // CORRECT: Int -> i128
// BigInteger is ONLY for manual Scv.toInt128(BigInteger.fromLong(...)) calls (low-level API)
```

Address auto-detection rules:
- Strings starting with `G` or `M` -> account address
- Strings starting with `C` -> contract address

You can also use `funcArgsToXdrSCValues` directly for manual conversion:

```kotlin
val spec: ContractSpec? = client.getContractSpec()
val xdrArgs: List<SCValXdr> = client.funcArgsToXdrSCValues("transfer", mapOf(
    "from" to "GABC...",
    "to" to "GDEST...",
    "amount" to 1000
))
```

---

## Reading Contract Return Values

When using the low-level API, extract typed results from `SCValXdr` sealed class variants:

```kotlin
val result: SCValXdr? = txResponse.getResultValue()

// Use Scv.from* helpers to extract values
val strVal: String = Scv.fromString(result!!)      // SCV_STRING
val symVal: String = Scv.fromSymbol(result!!)      // SCV_SYMBOL
val boolVal: Boolean = Scv.fromBoolean(result!!)   // SCV_BOOL
val u32Val: UInt = Scv.fromUint32(result!!)        // SCV_U32
val i32Val: Int = Scv.fromInt32(result!!)          // SCV_I32
val u64Val: ULong = Scv.fromUint64(result!!)       // SCV_U64
val i64Val: Long = Scv.fromInt64(result!!)         // SCV_I64

// i128 extraction (common for token balances)
val bigIntVal: com.ionspin.kotlin.bignum.integer.BigInteger = Scv.fromInt128(result!!)

// Address extraction
val address = Address.fromSCVal(result!!)
val strKey: String = address.toString()  // G... or C... format

// Vec extraction
val vec: List<SCValXdr> = Scv.fromVec(result!!)
for (item in vec) {
    // each item is SCValXdr -- use Scv.from* to extract
}

// Map extraction
val map: LinkedHashMap<SCValXdr, SCValXdr> = Scv.fromMap(result!!)
for ((key, value) in map) {
    val keyStr = Scv.fromSymbol(key)
    val valStr = Scv.fromString(value)
}
```

**WRONG/CORRECT sealed class access:**

```kotlin
// WRONG: result.str, result.b, result.u32 -- SCValXdr is a sealed class, NOT a data class with properties
// CORRECT: use Scv.from* helpers or pattern match on sealed class variants

// Pattern matching approach:
when (result) {
    is SCValXdr.Str -> println(result.value.value)
    is SCValXdr.Sym -> println(result.value.value)
    is SCValXdr.B -> println(result.value)
    is SCValXdr.U32 -> println(result.value.value)
    is SCValXdr.I32 -> println(result.value.value)
    is SCValXdr.U64 -> println(result.value.value)
    is SCValXdr.I64 -> println(result.value.value)
    is SCValXdr.I128 -> println(Scv.fromInt128(result))
    is SCValXdr.Address -> println(Address.fromSCVal(result).toString())
    is SCValXdr.Vec -> println("Vec with ${result.value?.value?.size} items")
    is SCValXdr.Map -> println("Map with ${result.value?.value?.size} entries")
    else -> println("Other type: ${result.discriminant}")
}
```

---

## Reading Contract State

Read contract data directly from the ledger without invoking the contract:

```kotlin
val server = SorobanServer("https://soroban-testnet.stellar.org:443")

val key = Scv.toSymbol("counter")
val entry = server.getContractData(
    contractId = contractId,
    key = key,
    durability = SorobanServer.Durability.PERSISTENT
)

if (entry != null) {
    println("Live until ledger: ${entry.liveUntilLedger}")
    // Parse the ledger entry data from the base64-encoded XDR
    val ledgerEntryXdr = LedgerEntryXdr.fromXdrBase64(entry.xdr)
}
```

For querying multiple entries at once, see [RPC Reference](./rpc.md) (`getLedgerEntries`).

---

## TTL Extension and Restore

### Extend Footprint TTL

Prevent contract data from being archived:

```kotlin
val server = SorobanServer("https://soroban-testnet.stellar.org:443")
val account = server.getAccount(keyPair.getAccountId())

val extendOp = ExtendFootprintTTLOperation(extendTo = 100000)

val tx = TransactionBuilder(
    sourceAccount = account,
    network = Network.TESTNET
)
    .addOperation(extendOp)
    .setTimeout(300)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()

// Simulate to get footprint and fees
val preparedTx = server.prepareTransaction(tx)
preparedTx.sign(keyPair)
server.sendTransaction(preparedTx)
```

### Restore Expired Data

When simulation returns a `restorePreamble`, entries must be restored before invoking.

**With ContractClient (automatic):** Set `restore = true` in `ClientOptions` (the default). `AssembledTransaction.simulate()` handles restoration automatically.

**With low-level SorobanServer (manual):**

```kotlin
val sim = server.simulateTransaction(tx)

if (sim.restorePreamble != null) {
    val restoreAccount = server.getAccount(keyPair.getAccountId())
    val restoreTx = TransactionBuilder(
        sourceAccount = restoreAccount,
        network = Network.TESTNET
    )
        .addOperation(RestoreFootprintOperation())
        .setSorobanData(
            SorobanDataBuilder(sim.restorePreamble!!.transactionData).build()
        )
        .setTimeout(300)
        .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
        .build()

    val preparedRestore = server.prepareTransaction(restoreTx)
    preparedRestore.sign(keyPair)
    val restoreResp = server.sendTransaction(preparedRestore)

    // Wait for restore to complete, then re-simulate and submit the original
    val restoreResult = server.pollTransaction(
        hash = restoreResp.hash!!,
        maxAttempts = 30,
        sleepStrategy = { 3000L }
    )
}
```

---

## Deploy Stellar Asset Contract (SAC)

Wrap a classic Stellar asset for use in Soroban. The KMP SDK derives the SAC contract ID from the asset:

```kotlin
// Get the SAC contract ID for an asset (does NOT deploy -- just computes the ID)
val usdcAsset = AssetTypeCreditAlphaNum4("USDC", issuerAccountId)
val sacContractId: String = usdcAsset.getContractId(Network.TESTNET)  // suspend fun

// For native XLM
val xlmAsset = AssetTypeNative
val xlmSacId: String = xlmAsset.getContractId(Network.TESTNET)
```

To deploy the SAC, use the low-level approach with `ContractIDPreimageXdr.FromAsset`:

```kotlin
val asset = AssetTypeCreditAlphaNum4("USDC", issuerAccountId)
val preimage = ContractIDPreimageXdr.FromAsset(asset.toXdr())
// WRONG: ContractExecutableXdr.StellarAsset -- no such variant name
// CORRECT: the Stellar Asset executable is the Void data object
val executable = ContractExecutableXdr.Void

val createContractArgs = CreateContractArgsXdr(
    contractIdPreimage = preimage,
    executable = executable
)
val hostFunction = HostFunctionXdr.CreateContract(createContractArgs)
val operation = InvokeHostFunctionOperation(hostFunction = hostFunction)

// Build transaction, simulate, sign, poll (same as other low-level patterns)
```

---

## Contract Introspection

Parse a contract's spec to discover its functions, types, and events programmatically.

### Loading Contract Info

```kotlin
// WRONG: import com.soneso.stellar.sdk.SorobanContractParser -- does NOT exist at sdk root
// WRONG: import com.soneso.stellar.sdk.SorobanContractInfo  -- does NOT exist at sdk root
// CORRECT: both are in com.soneso.stellar.sdk.contract (covered by the contract.* wildcard)
import com.soneso.stellar.sdk.contract.SorobanContractParser
import com.soneso.stellar.sdk.contract.SorobanContractInfo
// Note: com.soneso.stellar.sdk.* does NOT cover sub-packages like .contract

// From local WASM bytecode (offline)
val wasmBytes = File("contract.wasm").readBytes()
val contractInfo: SorobanContractInfo =
    SorobanContractParser.parseContractByteCode(wasmBytes)

// From network (by WASM hash or contract ID)
val server = SorobanServer("https://soroban-testnet.stellar.org:443")
val infoByWasm: SorobanContractInfo? = server.loadContractInfoForWasmId(wasmId)
val infoByContract: SorobanContractInfo? = server.loadContractInfoForContractId(contractId)
```

A contract created from a CAP-85 external reference (Protocol 28) resolves automatically.
See `rpc.md` > the contract loading section for `getExternalRefWasmHash()`.

### SorobanContractInfo Properties

| Property | Type | Description |
|----------|------|-------------|
| `specEntries` | `List<SCSpecEntryXdr>` | All spec entries (raw) |
| `funcs` | `List<SCSpecFunctionV0Xdr>` | Contract functions |
| `udtStructs` | `List<SCSpecUDTStructV0Xdr>` | Struct definitions |
| `udtUnions` | `List<SCSpecUDTUnionV0Xdr>` | Union definitions |
| `udtEnums` | `List<SCSpecUDTEnumV0Xdr>` | Enum definitions |
| `udtErrorEnums` | `List<SCSpecUDTErrorEnumV0Xdr>` | Error enum definitions |
| `events` | `List<SCSpecEventV0Xdr>` | Event definitions |
| `metaEntries` | `Map<String, String>` | Contract metadata |
| `envInterfaceVersion` | `ULong` | Protocol version |

### Using ContractSpec for Introspection

Build a `ContractSpec` for richer query methods:

```kotlin
val spec = ContractSpec(contractInfo.specEntries)

// List functions
val functions: List<SCSpecFunctionV0Xdr> = spec.funcs()
for (func in functions) {
    println("Function: ${func.name.value}")
    for (input in func.inputs) {
        // WRONG: when (input.type) { is SCSpecTypeDef.Address -> ... } -- no such class
        // CORRECT: input.type is SCSpecTypeDefXdr (Xdr suffix), but use .discriminant for printing
        println("  param: ${input.name} (type: ${input.type.discriminant})")
    }
    for (output in func.outputs) {
        println("  returns type: ${output.discriminant}")
    }
}

// Find a specific function
val helloFunc: SCSpecFunctionV0Xdr? = spec.getFunc("hello")

// Find any entry by name (function, struct, union, enum, event)
val entry: SCSpecEntryXdr? = spec.findEntry("MyStruct")

// List structs
for (struct in spec.udtStructs()) {
    println("Struct: ${struct.name}")
    for (field in struct.fields) {
        println("  ${field.name}: type ${field.type.discriminant}")
    }
}

// List enums
for (enumType in spec.udtEnums()) {
    println("Enum: ${enumType.name}")
    for (case in enumType.cases) {
        println("  ${case.name} = ${case.value.value}")
    }
}
```

### Listing Union Types

`SCSpecUDTUnionCaseV0Xdr` is a **sealed class** -- access the name through the specific case variant:

```kotlin
for (union in spec.udtUnions()) {
    println("Union: ${union.name}")
    for (case in union.cases) {
        // WRONG: case.name -- SCSpecUDTUnionCaseV0Xdr has no .name property
        // CORRECT: check the sealed class variant, then access voidCase or tupleCase
        when (case) {
            is SCSpecUDTUnionCaseV0Xdr.VoidCase -> {
                println("  ${case.value.name} (void)")
            }
            is SCSpecUDTUnionCaseV0Xdr.TupleCase -> {
                println("  ${case.value.name} (tuple with ${case.value.type.size} fields)")
            }
        }
    }
}
```

### Listing Error Enum Types

```kotlin
for (errorEnum in spec.udtErrorEnums()) {
    println("Error Enum: ${errorEnum.name}")
    for (case in errorEnum.cases) {
        println("  ${case.name} = ${case.value.value}")
    }
}
```

### Listing Events

```kotlin
for (event in spec.events()) {
    println("Event: ${event.name.value}")
    // WRONG: event.topics, event.body -- SCSpecEventV0Xdr has NO topics/body getters
    // CORRECT: use event.params (List of param entries)
    // Check the actual XDR fields available on SCSpecEventV0Xdr
}
```

### SCSpecEntryXdr Sealed Class Variants

| Kind | Variant | Type |
|------|---------|------|
| `SC_SPEC_ENTRY_FUNCTION_V0` | `SCSpecEntryXdr.FunctionV0` | `.value: SCSpecFunctionV0Xdr` |
| `SC_SPEC_ENTRY_UDT_STRUCT_V0` | `SCSpecEntryXdr.UdtStructV0` | `.value: SCSpecUDTStructV0Xdr` |
| `SC_SPEC_ENTRY_UDT_UNION_V0` | `SCSpecEntryXdr.UdtUnionV0` | `.value: SCSpecUDTUnionV0Xdr` |
| `SC_SPEC_ENTRY_UDT_ENUM_V0` | `SCSpecEntryXdr.UdtEnumV0` | `.value: SCSpecUDTEnumV0Xdr` |
| `SC_SPEC_ENTRY_UDT_ERROR_ENUM_V0` | `SCSpecEntryXdr.UdtErrorEnumV0` | `.value: SCSpecUDTErrorEnumV0Xdr` |
| `SC_SPEC_ENTRY_EVENT_V0` | `SCSpecEntryXdr.EventV0` | `.value: SCSpecEventV0Xdr` |

### NativeUnionVal for Union Values

When passing union-typed arguments to contracts or reading union results:

```kotlin
// Void case (no associated values)
val success = NativeUnionVal.VoidCase("Success")

// Tuple case (with associated values)
val data = NativeUnionVal.TupleCase("Data", listOf("field1", 42))

// Check type
if (result.isVoidCase) { /* ... */ }
if (result.isTupleCase) {
    val values = (result as NativeUnionVal.TupleCase).values
}
```

---

## Exception Handling

`ContractClient` and `AssembledTransaction` throw specific exceptions for different failure modes:

| Exception | When |
|-----------|------|
| `SimulationFailedException` | Transaction simulation failed |
| `SendTransactionFailedException` | Sending transaction to network failed |
| `TransactionFailedException` | Transaction included in ledger but failed |
| `TransactionStillPendingException` | Polling timeout reached |
| `ExpiredStateException` | Contract state needs restoration |
| `RestorationFailureException` | Auto-restore failed |
| `NotYetSimulatedException` | Calling result/sign before simulate |
| `NeedsMoreSignaturesException` | Auth entries need signing before submit |
| `NoSignatureNeededException` | Signing a read-only call (set force=true) |
| `ContractSpecException` | Spec parsing or type conversion error |

All contract exceptions extend `ContractException` and carry the `AssembledTransaction`:

```kotlin
import com.soneso.stellar.sdk.contract.exception.*

try {
    val result = client.invoke<SCValXdr>(
        functionName = "transfer",
        arguments = mapOf("from" to from, "to" to to, "amount" to amount),
        source = from,
        signer = keyPair
    )
} catch (e: SimulationFailedException) {
    println("Simulation failed: ${e.message}")
    // Access the assembled transaction for debugging
    println("Simulation response: ${e.assembledTransaction.simulation}")
} catch (e: NeedsMoreSignaturesException) {
    println("Need signatures from: ${e.assembledTransaction.needsNonInvokerSigningBy()}")
} catch (e: TransactionFailedException) {
    println("Transaction failed: ${e.message}")
} catch (e: ContractException) {
    println("Contract error: ${e.message}")
}
```
