# Advanced SDK Usage

> This guide covers complex scenarios and production patterns using the Stellar KMP SDK. It assumes you're familiar with [Getting Started](getting-started.md), [Demo App](demo-app.md), [Architecture](architecture.md), and [SDK Usage Examples](sdk-usage-examples.md).

## Table of Contents

1. [Advanced Contract Operations](#advanced-contract-operations)
   - [Custom Authorization with Auth.Signer](#custom-authorization-with-authsigner)
   - [Multi-Signature Contract Workflows with buildInvoke](#multi-signature-contract-workflows-with-buildinvoke)
   - [Custom Result Parsing with funcResToNative](#custom-result-parsing-with-funcrestonative)
   - [State Restoration Patterns](#state-restoration-patterns)
   - [Contract Parser](#contract-parser)
2. [Advanced Transaction Patterns](#advanced-transaction-patterns)
3. [Working with XDR Types](#working-with-xdr-types)
4. [Production Error Handling](#production-error-handling)
5. [Performance Optimization](#performance-optimization)
6. [Security Best Practices](#security-best-practices)
7. [Advanced Network Operations](#advanced-network-operations)
   - [SSE Streaming with Error Recovery](#sse-streaming-with-error-recovery)
   - [Multiple Server Failover](#multiple-server-failover)
   - [Custom HTTP Client Configuration](#custom-http-client-configuration)
   - [Advanced Transaction Polling Strategies](#advanced-transaction-polling-strategies)
   - [Advanced Event Querying](#advanced-event-querying)
   - [SorobanDataBuilder](#sorobandatabuilder)
   - [Platform-Specific Network Patterns](#platform-specific-network-patterns)
8. [Testing Advanced Features](#testing-advanced-features)

## Advanced Contract Operations

### Custom Authorization with Auth.Signer

The SDK provides the `Auth.Signer` interface for custom signing logic:

```kotlin
import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.xdr.HashIDPreimageXdr

// Implement custom signer for hardware wallet integration
class HardwareWalletSigner(
    private val publicKey: String,
    private val deviceInterface: MyHardwareWallet
) : Auth.Signer {
    override suspend fun sign(preimage: HashIDPreimageXdr): Auth.Signature {
        // Hash the preimage as required by protocol
        val payload = Util.hash(preimage.toXdrByteArray())

        // Request signature from hardware device
        val signature = deviceInterface.signPayload(payload)

        return Auth.Signature(publicKey, signature)
    }
}

// Use custom signer with authorization
val signer = HardwareWalletSigner("G...", hardwareDevice)
val signedEntry = Auth.authorizeEntry(
    entry = authEntryFromSimulation,
    signer = signer,
    validUntilLedgerSeq = currentLedger + 10000L,
    network = Network.PUBLIC
)
```

### Protocol 27 Authorization Arms (CAP-71)

A Soroban authorization entry carries one of three address credential arms:

- `Address` (the default, `SOROBAN_CREDENTIALS_ADDRESS`) — valid on every network.
- `AddressV2` (`SOROBAN_CREDENTIALS_ADDRESS_V2`) — the same fields, but the
  signature is bound to the credential address. Valid only on Protocol 27+.
- `AddressWithDelegates` (`SOROBAN_CREDENTIALS_ADDRESS_WITH_DELEGATES`) — adds a
  recursive tree of delegate signatures. Valid only on Protocol 27+.

Emitting a V2 or WITH_DELEGATES entry on a pre-27 network invalidates the
transaction, so the legacy `Address` arm stays the default. Opt into V2 only
when you target a Protocol 27+ network.

**Signing a legacy entry** is unchanged — `Auth.authorizeEntry` signs the
top-level address, whatever the arm:

```kotlin
val signed = Auth.authorizeEntry(
    entry = authEntryFromSimulation,
    signer = aliceKeyPair,
    validUntilLedgerSeq = currentLedger + 100L,
    network = Network.PUBLIC
)
```

**Opting into V2 via simulation** is a request flag. A Protocol 27+ RPC that
supports it returns `AddressV2` auth entries in recording mode; sign them the
same way as legacy entries:

```kotlin
import com.soneso.stellar.sdk.rpc.SorobanServer

val server = SorobanServer("https://soroban-testnet.stellar.org")
val simulation = server.simulateTransaction(transaction, useUpgradedAuth = true)
// Or, in one step:
val prepared = server.prepareTransaction(transaction, useUpgradedAuth = true)
```

`ContractClient` exposes the same opt-in through `ClientOptions(useUpgradedAuth = true)`.
An RPC that does not yet support the flag silently ignores it and returns legacy
entries; detect support by inspecting the returned credential arm, never by
catching an error.

**Opting into V2 client-side** works without the request flag (or against an
RPC that does not support it): simulation then returns legacy `Address` auth
entries; build the address-bound `AddressV2` arm with `Auth.authorizeInvocation`:

```kotlin
val signed = Auth.authorizeInvocation(
    signer = aliceKeyPair,
    validUntilLedgerSeq = currentLedger + 100L,
    invocation = invocationFromSimulation,
    network = Network.PUBLIC,
    authV2 = true
)
```

`AddressV2` is only valid on Protocol 27+ networks; emitting it on an older
network invalidates the transaction.

**Building a delegated entry.** Simulation never returns a delegate tree; you
assemble it from an `Address` or `AddressV2` entry with `attachDelegates` and
`DelegateDescriptor`, then sign each node with `AuthOptions(forAddress = ...)`:

```kotlin
import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.DelegateDescriptor

// Attach a sorted, validated delegate tree to a base entry (top-level signature
// stays void). Delegates may nest further via DelegateDescriptor.nestedDelegates.
val delegated = Auth.attachDelegates(
    entry = authEntryFromSimulation,
    validUntilLedgerSeq = currentLedger + 100L,
    delegates = listOf(
        DelegateDescriptor(address = delegateKeyPair.getAccountId())
    )
)

// Sign the delegate node: forAddress routes the signature to every matching node.
val withDelegateSig = Auth.authorizeEntry(
    entry = delegated,
    signer = delegateKeyPair,
    validUntilLedgerSeq = currentLedger + 100L,
    network = Network.PUBLIC,
    options = Auth.AuthOptions(forAddress = delegateKeyPair.getAccountId())
)

// Optionally also sign the top-level address (forAddress = null, the default).
// A delegates-only entry whose top-level signature stays void is legitimate.
```

**Submitting V2 or delegated entries.** Simulation and the high-level
`ContractClient` / `AssembledTransaction` only emit legacy `Address` entries, so
the V2 and delegate arms built above are also submitted at the `SorobanServer`
level. Attach the signed entries to the operation, then **re-simulate in
enforcing mode** before submitting: when the authorizing address is a contract
account, its `__check_auth` runs only in the enforcing pass, and the storage it
reads (plus any delegate account entries) must be in the footprint.

```kotlin
val signedOp = InvokeHostFunctionOperation(
    hostFunction = operation.hostFunction,
    auth = signedEntries
)
// Rebuild the transaction with the signed auth, then re-simulate. With auth
// present the RPC simulates in enforcing mode and returns the full footprint.
val rebuiltTransaction = TransactionBuilder(sourceAccount = account, network = Network.PUBLIC)
    .addOperation(signedOp)
    .setTimeout(TransactionPreconditions.TIMEOUT_INFINITE)
    .setBaseFee(AbstractTransaction.MIN_BASE_FEE)
    .build()
val enforcing = sorobanServer.simulateTransaction(rebuiltTransaction)
val prepared = sorobanServer.prepareTransaction(rebuiltTransaction, enforcing)
prepared.sign(sourceKeyPair)
sorobanServer.sendTransaction(prepared)
```

When converting a simulated `Address` entry to `AddressV2` in place, reuse its
nonce (rebuild the credentials on the `AddressV2` arm); a fresh nonce will not
match the original simulation, so it then relies on the enforcing re-simulation
above.

### Multi-Signature Contract Workflows with buildInvoke

Use `buildInvoke` for complex multi-signature scenarios:

```kotlin
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.xdr.*

// Step 1: Build transaction without signing (for multi-sig coordination)
val assembled = contractClient.buildInvoke<Map<String, Any>>(
    functionName = "transfer",
    arguments = mapOf(
        "from" to senderAddress,
        "to" to recipientAddress,
        "amount" to 1000L
    ),
    source = senderAddress,
    signer = null // Don't sign yet
)

// Step 2: Simulate to discover auth requirements
assembled.simulate()

// Step 3: Collect signatures from multiple parties
val authEntries = assembled.simulation?.results?.firstOrNull()?.auth ?: emptyList()
val signedEntries = mutableListOf<SorobanAuthorizationEntryXdr>()

for (entry in authEntries) {
    // Extract signer address from authorization entry
    val requiredAddress = when (val creds = entry.credentials) {
        is SorobanCredentialsXdr.Address -> Address.fromSCAddress(creds.value.address).toString()
        else -> null
    }
    // Application-specific: Collect signature from required signer (your coordination logic)
    // This function represents your application's logic for coordinating with multiple parties
    val signedEntry = coordinateSignature(entry, requiredAddress)
    signedEntries.add(signedEntry)
}

// Step 4: Sign authorization entries for each required party
for (entry in signedEntries) {
    val requiredAddress = when (val creds = entry.credentials) {
        is SorobanCredentialsXdr.Address -> Address.fromSCAddress(creds.value.address).toString()
        else -> null
    }
    if (requiredAddress != null) {
        // Application-specific: Retrieve keypair for the required address (your key management logic)
        val keypairForAddress = getKeypairForAddress(requiredAddress)
        assembled.signAuthEntries(keypairForAddress)
    }
}
val result = assembled.signAndSubmit(sourceKeypair)
```

For collecting the addresses that still need to sign, prefer
`needsNonInvokerSigningBy()` over inspecting credential arms by hand: it walks
all three arms (`Address`, `AddressV2`, `AddressWithDelegates`) and, for delegate
trees, reports every unsigned delegate node alongside the top-level address.
`signAuthEntries(keyPair)` then signs every entry whose top-level address or any
delegate node matches the signer:

```kotlin
val assembled = swapClient.buildInvoke<Unit>(
    functionName = "swap",
    arguments = swapParams,
    source = invokerAccount,
    signer = invokerKeyPair
)
assembled.simulate()

// Addresses still needing a signature — top-level and delegate nodes alike.
val pending: Set<String> = assembled.needsNonInvokerSigningBy()

// Each signer signs its own entries; a delegate signer is routed to its node.
if (aliceKeyPair.getAccountId() in pending) assembled.signAuthEntries(aliceKeyPair)
if (delegateKeyPair.getAccountId() in pending) assembled.signAuthEntries(delegateKeyPair)

val swapResult = assembled.signAndSubmit(invokerKeyPair)
```

### Custom Result Parsing with funcResToNative

Parse complex contract results using the SDK's type conversion system:

```kotlin
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.xdr.SCValXdr

// Example: Parse custom struct from contract
data class TokenMetadata(
    val name: String,
    val symbol: String,
    val decimals: Int,
    val totalSupply: Long
)

suspend fun getTokenMetadata(contractClient: ContractClient): TokenMetadata {
    // Call contract function that returns a struct
    val resultXdr = contractClient.invoke<SCValXdr>(
        functionName = "get_metadata",
        arguments = emptyMap(),
        source = "G...",
        signer = null
    )

    // Use funcResToNative for automatic type conversion
    val nativeResult = contractClient.funcResToNative("get_metadata", resultXdr)

    // Cast to expected type (Map for structs)
    @Suppress("UNCHECKED_CAST")
    val resultMap = nativeResult as Map<String, Any>

    return TokenMetadata(
        name = resultMap["name"] as String,
        symbol = resultMap["symbol"] as String,
        decimals = (resultMap["decimals"] as UInt).toInt(),
        totalSupply = resultMap["total_supply"] as Long
    )
}
```

### State Restoration Patterns

Handle expired contract state gracefully:

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.exception.ExpiredStateException
import kotlinx.coroutines.delay

suspend fun invokeWithAutoRestore(
    contractClient: ContractClient,
    functionName: String,
    arguments: Map<String, Any?>,
    source: String,
    signer: KeyPair
): Any? {
    return try {
        contractClient.invoke(
            functionName = functionName,
            arguments = arguments,
            source = source,
            signer = signer
        )
    } catch (e: ExpiredStateException) {
        // Automatically restore expired entries using buildInvoke
        val assembled = e.assembledTransaction ?: throw e
        assembled.restoreFootprint()

        // Wait for restoration to complete
        delay(5000)

        // Retry original invocation (need to rebuild transaction)
        contractClient.invoke(
            functionName = functionName,
            arguments = arguments,
            source = source,
            signer = signer
        )
    }
}
```

### Contract Parser

Parse contract Wasm bytecode to extract specifications, metadata, and environment information without deploying. The parser implements SEP-46 (Contract Meta), SEP-47 (Contract Interface Discovery), and SEP-48 (Contract Interface Specification).

#### Parse from Bytecode

```kotlin
import com.soneso.stellar.sdk.contract.SorobanContractParser
import com.soneso.stellar.sdk.contract.SorobanContractInfo
import com.soneso.stellar.sdk.contract.SorobanContractParserException

val bytecode: ByteArray = // load .wasm file bytes

try {
    val contractInfo: SorobanContractInfo = SorobanContractParser.parseContractByteCode(bytecode)

    // Environment interface protocol version
    println("Protocol version: ${contractInfo.envInterfaceVersion}")

    // All spec entries (functions, structs, unions, enums, error enums, events)
    for (entry in contractInfo.specEntries) {
        println(entry.discriminant)
    }

    // Contract metadata as key-value pairs (SEP-46)
    val meta: Map<String, String> = contractInfo.metaEntries

    // Supported SEPs declared in metadata (SEP-47 "sep" key)
    val seps: List<String> = contractInfo.supportedSeps
} catch (e: SorobanContractParserException) {
    println("Failed to parse contract: ${e.message}")
}
```

#### Typed Accessors

`SorobanContractInfo` provides typed accessors for each spec entry kind:

```kotlin
// Exported functions
val functions: List<SCSpecFunctionV0Xdr> = contractInfo.funcs
for (func in functions) {
    println("Function: ${func.name.value}")
}

// User-defined types
val structs: List<SCSpecUDTStructV0Xdr> = contractInfo.udtStructs
val unions: List<SCSpecUDTUnionV0Xdr> = contractInfo.udtUnions
val enums: List<SCSpecUDTEnumV0Xdr> = contractInfo.udtEnums
val errorEnums: List<SCSpecUDTErrorEnumV0Xdr> = contractInfo.udtErrorEnums

// Contract events
val events: List<SCSpecEventV0Xdr> = contractInfo.events
```

#### Parse from Network

`SorobanServer` can fetch and parse contract info directly from a deployed contract, by contract ID or WASM ID:

```kotlin
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.contract.SorobanContractInfo

// Both methods are suspend functions — call from a coroutine scope
suspend fun loadContractInfo() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    // By contract address (C... format)
    val contractInfo: SorobanContractInfo? = server.loadContractInfoForContractId(
        contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
    )

    // By WASM ID (hex-encoded hash)
    val infoByWasm: SorobanContractInfo? = server.loadContractInfoForWasmId(
        wasmId = "abc123..."
    )

    contractInfo?.let { info ->
        println("Protocol version: ${info.envInterfaceVersion}")
        info.funcs.forEach { func -> println("Function: ${func.name.value}") }
        println("Supported SEPs: ${info.supportedSeps}")
    }
}
```

#### External Reference Executables (CAP-85)

From Protocol 28 on, a contract can be created from an external reference: instead of
carrying its own WASM hash, the instance names an owner contract and a tag, and the owner
holds a persistent contract data entry under that tag whose value is the 32-byte hash of an
already uploaded WASM. `loadContractCodeForContractId` and `loadContractInfoForContractId`
resolve such instances without any extra step. An external reference that cannot be
resolved throws rather than returning null; a null from the loaders still means the
instance or its code was not found, or the contract is a Stellar Asset Contract, which has
no WASM on-chain. To resolve a reference directly, use `getExternalRefWasmHash`:

```kotlin
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.ContractExecutableXdr
import com.soneso.stellar.sdk.xdr.LedgerEntryDataXdr
import com.soneso.stellar.sdk.xdr.SCValXdr

suspend fun resolveExternalRef() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    // Read the contract instance to inspect its executable.
    val entry = server.getContractData(
        contractId = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5",
        key = Scv.toLedgerKeyContractInstance(),
        durability = SorobanServer.Durability.PERSISTENT
    )
    val contractData = entry?.parseXdr() as? LedgerEntryDataXdr.ContractData
    val instance = contractData?.value?.`val` as? SCValXdr.Instance
    val executable = instance?.value?.executable
    if (executable is ContractExecutableXdr.ExternalRef) {
        // The tag entry on the owner contract holds the WASM hash the instance runs.
        val wasmHash = server.getExternalRefWasmHash(executable.value)
        println("Resolved a ${wasmHash.size}-byte WASM hash")
    }
}
```

`getExternalRefWasmHash` returns the 32-byte WASM hash. It throws
`IllegalArgumentException` when the reference's owner is not a contract address, and
`IllegalStateException` when the owner has no entry under the tag, the entry is not a
contract data entry, or the value does not hold a 32-byte hash. The owner contract is
read, never invoked.

## Advanced Transaction Patterns

### Transaction Preconditions

Use `TransactionPreconditions` for advanced transaction control:

```kotlin
import com.soneso.stellar.sdk.TransactionPreconditions
import com.soneso.stellar.sdk.TimeBounds
import com.soneso.stellar.sdk.LedgerBounds
import com.soneso.stellar.sdk.SignerKey

// Complex preconditions for time-locked transactions
val preconditions = TransactionPreconditions(
    timeBounds = TimeBounds(
        minTime = Clock.System.now().epochSeconds,
        maxTime = Clock.System.now().epochSeconds + 3600
    ),
    ledgerBounds = LedgerBounds(
        minLedger = 1000000U,
        maxLedger = 1001000U
    ),
    minSequenceNumber = account.sequenceNumber + 1,
    minSequenceAge = 60, // 60 seconds minimum age
    minSequenceLedgerGap = 10, // 10 ledgers minimum gap
    extraSigners = listOf(
        SignerKey.ed25519PublicKey(backupSigner.getPublicKey())
    )
)

val transaction = TransactionBuilder(account, network)
    .addOperation(payment)
    .addPreconditions(preconditions)
    .build()
```

### Fee Bump Transactions

Increase fees for stuck transactions:

```kotlin
import com.soneso.stellar.sdk.FeeBumpTransactionBuilder
import com.soneso.stellar.sdk.FeeBumpTransaction

// Original transaction stuck with low fee
// Application-specific: Load transaction from your storage
// This function represents your database/storage retrieval logic
val txHash = "abc123..." // Transaction hash from submission
val originalTx = loadTransactionFromDb(txHash)

// Create fee bump with higher fee
val feeBumpTx = FeeBumpTransactionBuilder(originalTx)
    .setFeeSource(feePayerAccount.accountId)
    .setBaseFee(1000) // 10x normal fee
    .build()

// Sign with fee payer account
feeBumpTx.sign(feePayerKeypair)

// Submit fee bump transaction
val response = horizonServer.submitTransaction(feeBumpTx)
```

### Complex Multi-Operation Transactions

Build transactions with multiple interdependent operations:

```kotlin
import kotlinx.datetime.Clock

// Atomic swap with multiple operations
val atomicSwap = TransactionBuilder(account, network)
    // Create claimable balance for party A
    .addOperation(
        CreateClaimableBalanceOperation.Builder(
            asset = AssetTypeCreditAlphaNum4("USDC", issuer),
            amount = "100.00",
            claimants = listOf(
                Claimant(
                    destination = partyB,
                    predicate = ClaimPredicate.And(
                        left = ClaimPredicate.Not(
                            ClaimPredicate.BeforeAbsoluteTime(Clock.System.now().epochSeconds)
                        ),
                        right = ClaimPredicate.BeforeAbsoluteTime(Clock.System.now().epochSeconds + 86400)
                    )
                )
            )
        ).build()
    )
    // Path payment from party B to party A
    .addOperation(
        PathPaymentStrictReceiveOperation.Builder(
            sendAsset = AssetTypeNative(),
            sendMax = "50.00",
            destination = partyA,
            destAsset = AssetTypeCreditAlphaNum4("EUR", eurIssuer),
            destAmount = "45.00",
            path = listOf(AssetTypeCreditAlphaNum4("USD", usdIssuer))
        ).setSourceAccount(partyB).build()
    )
    // Manage data for swap metadata
    .addOperation(
        ManageDataOperation.Builder(
            name = "swap_id",
            // Application-specific: Use platform-specific UUID or timestamp
            value = "swap_id_${System.currentTimeMillis()}".toByteArray()
        ).build()
    )
    .setBaseFee(300) // Higher fee for complex transaction
    .setTimeout(300)
    .build()
```

## Working with XDR Types

### Custom XDR Encoding/Decoding

The SDK provides 424 XDR types for advanced protocol operations:

```kotlin
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.xdr.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Manually build XDR structures
fun buildCustomContractData(
    contractId: String,
    key: SCValXdr,
    value: SCValXdr
): LedgerEntryXdr {
    val contractData = ContractDataEntryXdr(
        ext = ExtensionPointXdr.Void,
        contract = SCAddressXdr.Contract(
            HashXdr(Address(contractId).contractIdBytes)
        ),
        key = key,
        durability = ContractDataDurabilityXdr.PERSISTENT,
        val_ = value
    )

    return LedgerEntryXdr.ContractData(contractData)
}

// Parse XDR from base64
@OptIn(ExperimentalEncodingApi::class)
fun parseTransactionMeta(metaXdr: String): TransactionMetaXdr {
    val bytes = Base64.decode(metaXdr)
    val reader = XdrReader(bytes)
    return TransactionMetaXdr.decode(reader)
}
```

### SCVal Type Conversions

Convert between native types and Soroban contract values:

```kotlin
import com.soneso.stellar.sdk.scval.Scv

// Convert native types to SCVal
val scMap = Scv.toMap(linkedMapOf(
    Scv.toSymbol("name") to Scv.toString("MyToken"),
    Scv.toSymbol("decimals") to Scv.toUint32(7U),
    Scv.toSymbol("admin") to Scv.toAddress("G...")
))

val scVec = Scv.toVec(listOf(
    Scv.toInt128(BigInteger.valueOf(1000000)),
    Scv.toBytes(byteArrayOf(1, 2, 3, 4))
))

// Convert SCVal to native types
fun parseContractResult(scVal: SCValXdr): Any? {
    return when (scVal) {
        is SCValXdr.Bool -> scVal.value
        is SCValXdr.I32 -> scVal.value
        is SCValXdr.I64 -> scVal.value
        is SCValXdr.U32 -> scVal.value.toInt()
        is SCValXdr.U64 -> scVal.value.toLong()
        is SCValXdr.Str -> Scv.fromString(scVal)
        is SCValXdr.Vec -> scVal.value?.value?.map { parseContractResult(it) }
        is SCValXdr.Map -> {
            val map = mutableMapOf<String, Any?>()
            scVal.value?.value?.forEach { entry ->
                val key = Scv.fromSymbol(entry.key)
                val value = parseContractResult(entry.`val`)
                map[key] = value
            }
            map
        }
        else -> null
    }
}
```

## Production Error Handling

### ContractClient Exception Handling

The SDK provides 11 specialized exception types for contract operations:

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.exception.*
import com.soneso.stellar.sdk.rpc.responses.GetTransactionStatus
import kotlinx.coroutines.delay

suspend fun robustContractInvocation(
    contractClient: ContractClient,
    maxRetries: Int = 3
): Any? {
    var lastException: Exception? = null

    repeat(maxRetries) { attempt ->
        try {
            return contractClient.invoke(
                functionName = "transfer",
                arguments = mapOf("to" to "G...", "amount" to 100L),
                source = "G...",
                signer = keypair
            )
        } catch (e: SimulationFailedException) {
            // Simulation failed - check error details
            // Application-specific: Use your logging framework (Logback, SLF4J, etc.)
            val errorDetail = e.assembledTransaction?.simulation?.error ?: "Unknown error"
            logger.error("Simulation failed: $errorDetail")
            throw e // Don't retry simulation failures

        } catch (e: ExpiredStateException) {
            // Contract state expired - restore using AssembledTransaction
            val assembled = e.assembledTransaction ?: throw e
            assembled.restoreFootprint()
            delay(5000)

        } catch (e: SendTransactionFailedException) {
            // Network error - retry with exponential backoff
            lastException = e
            delay(1000L * (attempt + 1))

        } catch (e: TransactionFailedException) {
            // Transaction failed on-chain - check getTransactionResponse for details
            val transactionResponse = e.assembledTransaction?.getTransactionResponse
            when {
                transactionResponse?.status == GetTransactionStatus.FAILED -> {
                    // Application-specific: Reload account sequence from Horizon
                    // Example: account = horizonServer.loadAccount(accountId)
                    refreshAccountSequence()
                }
                else -> throw e
            }

        } catch (e: TransactionStillPendingException) {
            // Transaction still pending - polling is handled internally by AssembledTransaction
            // This exception means timeout was reached, but transaction may still complete
            // For manual polling later, access the transaction hash from the exception
            throw e

        } catch (e: NeedsMoreSignaturesException) {
            // Application-specific: Collect signatures from multiple parties
            // Use assembledTransaction.needsNonInvokerSigningBy() to see which accounts need to sign
            // Then call signAuthEntries() for each required signer before submission
            // See "Multi-Signature Contract Workflows with buildInvoke" section for complete example
            val assembled = e.assembledTransaction ?: throw e
            val requiredSigners = assembled.needsNonInvokerSigningBy()
            // Example: Coordinate with other signers
            for (signerAddress in requiredSigners) {
                val keypair = getKeypairForAddress(signerAddress)
                assembled.signAuthEntries(keypair)
            }
            // After all signatures collected, retry submission
            assembled.signAndSubmit(signer)

        } catch (e: RestorationFailureException) {
            // Restoration failed - may need different approach
            // Application-specific: Use your logging framework (Logback, SLF4J, etc.)
            logger.error("Failed to restore: ${e.message}")
            throw e

        } catch (e: NotYetSimulatedException) {
            // Simulation happens on AssembledTransaction, not raw Transaction
            // Call assembled.simulate() before signing
            val assembled = e.assembledTransaction ?: throw e
            // This shouldn't happen if using invoke() or buildInvoke() correctly
            throw e

        } catch (e: NoSignatureNeededException) {
            // Read-only call doesn't need signatures - get result from assembledTransaction
            val assembled = e.assembledTransaction ?: throw e
            return assembled.result

        } catch (e: ContractSpecException) {
            // Contract spec parsing error
            // Application-specific: Use your logging framework (Logback, SLF4J, etc.)
            logger.error("Invalid contract spec: ${e.message}")
            throw e
        }
    }

    throw lastException ?: IllegalStateException("Failed after $maxRetries attempts")
}
```

### Transaction Retry with Coroutines

Implement sophisticated retry patterns using Kotlin coroutines:

```kotlin
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.responses.SubmitTransactionResponse
import com.soneso.stellar.sdk.requests.BadRequestException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TransactionRetryManager(
    private val horizonServer: HorizonServer,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    suspend fun submitWithRetry(
        transaction: Transaction,
        maxAttempts: Int = 3,
        backoffMultiplier: Double = 2.0
    ): SubmitTransactionResponse = coroutineScope {
        var delay = 1000L
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return@coroutineScope horizonServer.submitTransaction(transaction)
            } catch (e: BadRequestException) {
                when (e.problem?.extras?.resultCodes?.transaction) {
                    "tx_bad_seq" -> {
                        // Bad sequence error - transaction used wrong sequence number
                        // Cannot retry with same transaction - must rebuild from scratch
                        // with fresh account data from network
                        throw e
                    }
                    "tx_insufficient_fee" -> {
                        // Insufficient fee - cannot retry with same transaction
                        // Must rebuild with higher fee
                        throw e
                    }
                }
                lastException = e
                delay(delay)
                delay = (delay * backoffMultiplier).toLong()
            } catch (e: Exception) {
                lastException = e
                delay(delay)
                delay = (delay * backoffMultiplier).toLong()
            }
        }

        throw lastException ?: IllegalStateException("Retry failed")
    }
}
```

## Performance Optimization

### Parallel Operations with Coroutines

Execute multiple independent operations concurrently:

```kotlin
import kotlinx.coroutines.*

class ParallelOperationsExecutor(
    private val horizonServer: HorizonServer
) {
    suspend fun executeParallelPayments(
        sourceAccount: Account,
        payments: List<PaymentRequest>
    ): List<Result<SubmitTransactionResponse>> = coroutineScope {
        // Use semaphore to limit concurrent submissions
        val semaphore = Semaphore(10)

        payments.map { payment ->
            async {
                semaphore.withPermit {
                    runCatching {
                        val operation = PaymentOperation.Builder(
                            destination = payment.destination,
                            asset = payment.asset,
                            amount = payment.amount
                        ).build()

                        val tx = TransactionBuilder(sourceAccount, Network.PUBLIC)
                            .addOperation(operation)
                            .setTimeout(180)
                            .build()

                        tx.sign(payment.signer)
                        horizonServer.submitTransaction(tx)
                    }
                }
            }
        }.awaitAll()
    }
}
```

### Efficient SSE Streaming

Process server-sent events efficiently:

```kotlin
import kotlinx.coroutines.flow.*

class StreamProcessor(private val horizonServer: HorizonServer) {

    fun streamTransactions(account: String): Flow<TransactionResponse> = callbackFlow {
        val eventSource = horizonServer.transactions()
            .forAccount(account)
            .cursor("now")
            .stream(object : EventListener<TransactionResponse> {
                override fun onEvent(transaction: TransactionResponse) {
                    // Process in flow - use trySend() in callbackFlow
                    trySend(transaction)
                }

                override fun onFailure(error: Throwable?, responseCode: Int?) {
                    // Handle errors and close flow
                    close(error)
                }
            })

        awaitClose { eventSource.close() }
    }.buffer(100) // Buffer for performance
     .flowOn(Dispatchers.IO)
}
```

### Batch Transaction Submission

Submit multiple transactions efficiently:

```kotlin
class BatchSubmitter(
    private val horizonServer: HorizonServer,
    private val chunkSize: Int = 50
) {
    suspend fun submitBatch(
        transactions: List<Transaction>
    ): BatchResult = coroutineScope {
        val results = mutableListOf<SubmissionResult>()

        transactions.chunked(chunkSize).forEach { chunk ->
            val chunkResults = chunk.map { tx ->
                async {
                    try {
                        val response = horizonServer.submitTransaction(tx)
                        SubmissionResult.Success(tx.hash(), response)
                    } catch (e: Exception) {
                        SubmissionResult.Failure(tx.hash(), e)
                    }
                }
            }.awaitAll()

            results.addAll(chunkResults)

            // Rate limiting between chunks
            delay(1000)
        }

        BatchResult(
            successful = results.filterIsInstance<SubmissionResult.Success>().size,
            failed = results.filterIsInstance<SubmissionResult.Failure>().size,
            details = results
        )
    }
}
```

## Security Best Practices

### Secure Key Management

Implement secure patterns for key handling:

```kotlin
class SecureKeyManager {
    // Use CharArray for sensitive data (can be zeroed)
    private fun secureKeyHandling(secretSeed: CharArray) {
        try {
            val keypair = KeyPair.fromSecretSeed(secretSeed)
            // Use keypair
        } finally {
            // Clear sensitive data
            secretSeed.fill('\u0000')
        }
    }

    // Defensive copying for immutability
    suspend fun createSignerWithDefensiveCopy(privateKey: ByteArray): KeyPair {
        val keyCopy = privateKey.copyOf()
        return try {
            KeyPair.fromSecretSeed(keyCopy)
        } finally {
            keyCopy.fill(0)
        }
    }
}
```

### Multi-Signature Coordination

Coordinate multi-signature transactions securely:

```kotlin
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

class MultiSigCoordinator(
    private val horizonServer: HorizonServer,
    private val network: Network
) {
    // Application-specific: Define your own data classes for signing coordination
    suspend fun coordinateMultiSig(
        transaction: Transaction,
        requiredSigners: List<String>
    ): SignedTransaction {
        // Create signing request
        val signingRequest = SigningRequest(
            transactionXdr = transaction.toEnvelopeXdrBase64(),
            network = network.networkPassphrase,
            requiredSigners = requiredSigners,
            expiresAt = Clock.System.now() + 1.hours
        )

        // Collect signatures from each party
        val signers = mutableListOf<KeyPair>()
        for (signerAccount in requiredSigners) {
            val signer = requestSignature(signingRequest, signerAccount)
            signers.add(signer)
        }

        // Apply all signatures - use transaction.sign() method
        signers.forEach { signer ->
            transaction.sign(signer)
        }

        return SignedTransaction(transaction, transaction.signatures)
    }
}
```

### Authorization Patterns with Auth Class

Use the SDK's Auth class for secure authorization:

```kotlin
import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import com.soneso.stellar.sdk.rpc.SorobanServer

// Build authorization from scratch
suspend fun authorizeCustomInvocation(
    signer: KeyPair,
    contractId: String,
    functionName: String,
    args: SCValXdr
): SorobanAuthorizationEntryXdr {
    val invocation = SorobanAuthorizedInvocationXdr(
        function = SorobanAuthorizedFunctionXdr.ContractFn(
            SorobanAuthorizedContractFunctionXdr(
                contractAddress = SCAddressXdr.Contract(
                    HashXdr(Address(contractId).contractIdBytes)
                ),
                functionName = Scv.toSymbol(functionName),
                args = SCVecXdr(listOf(args))
            )
        ),
        subInvocations = SCVecXdr(emptyList())
    )

    // Get current ledger sequence from network
    val server = SorobanServer("https://soroban-rpc.stellar.org:443")
    val currentLedger = server.getLatestLedger().sequence

    return Auth.authorizeInvocation(
        signer = signer,
        validUntilLedgerSeq = currentLedger + 100000L,
        invocation = invocation,
        network = Network.PUBLIC
        // authV2 = true emits ADDRESS_V2 credentials (Protocol 27+ networks only).
    )
}
```

## Advanced Network Operations

### SSE Streaming with Error Recovery

Implement robust streaming with automatic reconnection:

```kotlin
class RobustStreamManager(
    private val horizonServer: HorizonServer,
    private val reconnectDelay: Duration = 5.seconds
) {
    fun streamWithReconnect(
        account: String,
        onTransaction: (TransactionResponse) -> Unit
    ): Job = CoroutineScope(Dispatchers.IO).launch {
        var cursor = "now"

        while (isActive) {
            try {
                horizonServer.transactions()
                    .forAccount(account)
                    .cursor(cursor)
                    .stream(object : EventListener<TransactionResponse> {
                        override fun onEvent(transaction: TransactionResponse) {
                            cursor = transaction.pagingToken
                            onTransaction(transaction)
                        }

                        override fun onFailure(
                            optional: Optional<Throwable>?,
                            response: Response?
                        ) {
                            throw optional?.get() ?: IOException("Stream failed")
                        }
                    })
            } catch (e: Exception) {
                // Application-specific: Use your logging framework (Logback, SLF4J, etc.)
                logger.error("Stream error, reconnecting in $reconnectDelay", e)
                delay(reconnectDelay)
            }
        }
    }
}
```

### Multiple Server Failover

Implement failover across multiple Horizon servers:

```kotlin
class HorizonFailoverClient(
    private val servers: List<String>,
    private val network: Network
) {
    private var currentIndex = 0

    suspend fun <T> executeWithFailover(
        operation: suspend (HorizonServer) -> T
    ): T {
        val exceptions = mutableListOf<Exception>()

        for (i in servers.indices) {
            val serverUrl = servers[(currentIndex + i) % servers.size]
            val server = HorizonServer(serverUrl)

            try {
                return operation(server)
            } catch (e: Exception) {
                exceptions.add(e)
                if (i == servers.size - 1) {
                    // Application-specific: Use custom AggregateException or standard exception
                    throw IllegalStateException("All servers failed: ${exceptions.joinToString()}")
                }
            }
        }

        throw IllegalStateException("Unreachable")
    }
}
```

### Custom HTTP Client Configuration

For specific requirements like custom timeouts, retry policies, or logging, configure a custom Ktor HTTP client:

```kotlin
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun createCustomClient(): HttpClient {
    return HttpClient {
        // JSON configuration for API responses
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
                encodeDefaults = true
            })
        }

        // Timeout configuration
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000L  // 2 minutes for long-running operations
            connectTimeoutMillis = 30_000L   // 30 seconds to establish connection
            socketTimeoutMillis = 120_000L   // 2 minutes for socket operations
        }

        // Retry configuration for transient failures
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }

        // Logging (useful for debugging production issues)
        install(Logging) {
            level = LogLevel.INFO
        }
    }
}

// Use custom client with Soroban or Horizon servers
val customSorobanServer = SorobanServer(
    serverUrl = "https://soroban-testnet.stellar.org",
    httpClient = createCustomClient()
)
```

**When to use custom HTTP clients:**
- Long-running contract operations requiring extended timeouts
- High-reliability applications needing automatic retry logic
- Production debugging requiring detailed HTTP logging
- Rate-limited environments needing custom backoff strategies

### Advanced Transaction Polling Strategies

Customize transaction polling behavior based on your application's latency and reliability requirements:

```kotlin
suspend fun customPolling() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")
    val txHash = "YOUR_TRANSACTION_HASH"

    // Strategy 1: Default polling (30 attempts, 1 second intervals)
    val result1 = server.pollTransaction(txHash)

    // Strategy 2: Patient polling (60 attempts for slow networks)
    val result2 = server.pollTransaction(
        hash = txHash,
        maxAttempts = 60,
        sleepStrategy = { attemptNumber -> 1000L } // 1 second between attempts
    )

    // Strategy 3: Exponential backoff (efficient for variable latency)
    val result3 = server.pollTransaction(
        hash = txHash,
        maxAttempts = 20,
        sleepStrategy = { attemptNumber ->
            // Start with 1 second, double each time, max 30 seconds
            minOf(1000L * (1 shl attemptNumber), 30_000L)
        }
    )

    // Strategy 4: Linear backoff with warmup
    val result4 = server.pollTransaction(
        hash = txHash,
        maxAttempts = 30,
        sleepStrategy = { attemptNumber ->
            // 500ms initial delay, increase by 500ms each attempt
            500L + (500L * attemptNumber)
        }
    )

    server.close()
}
```

**Polling Strategy Guidelines:**
- **Default**: Good for testnet and most production scenarios (30s total)
- **Patient**: Use for congested networks or low-priority operations (60s total)
- **Exponential backoff**: Best for production with variable network latency
- **Linear backoff**: Predictable timing for SLA-sensitive applications

### Advanced Event Querying

Query Soroban contract events with sophisticated filtering for monitoring and debugging:

```kotlin
import com.soneso.stellar.sdk.rpc.requests.GetEventsRequest
import com.soneso.stellar.sdk.scval.Scv

suspend fun advancedEventQuerying() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    // Basic query: all contract events from a ledger range
    val basicRequest = GetEventsRequest(
        startLedger = 1000000L,
        endLedger = 1001000L,  // Optional: specify end of range
        filters = listOf(
            GetEventsRequest.EventFilter(
                type = GetEventsRequest.EventFilterType.CONTRACT,  // Optional: SYSTEM, CONTRACT, or null for all
                contractIds = listOf("CDZJ..."),  // Optional: up to 5 contract IDs
                topics = null  // No topic filtering
            )
        ),
        pagination = GetEventsRequest.Pagination(limit = 100)
    )

    val events = server.getEvents(basicRequest)
    println("Found ${events.events.size} events")

    // Advanced query: filter by topics with wildcards
    // Topic filters require base64-encoded XDR SCVal values
    val incrementTopic = Scv.toSymbol("increment").toXdrBase64()
    val counterTopic = Scv.toSymbol("COUNTER").toXdrBase64()

    val advancedRequest = GetEventsRequest(
        startLedger = 1000000L,
        filters = listOf(
            GetEventsRequest.EventFilter(
                type = null,  // null matches all types (SYSTEM, CONTRACT, DIAGNOSTIC)
                contractIds = listOf("CDZJ..."),
                topics = listOf(
                    // Example 1: Match events with "COUNTER" as first topic and any remaining topics
                    listOf(counterTopic, "**"),  // "**" matches zero or more segments (end only)

                    // Example 2: Match events with any first topic and "increment" as second
                    listOf("*", incrementTopic)  // "*" matches exactly one segment
                )
            )
        ),
        pagination = GetEventsRequest.Pagination(limit = 100)
    )

    val filteredEvents = server.getEvents(advancedRequest)

    filteredEvents.events.forEach { event ->
        println("Event ID: ${event.id}")
        println("Contract: ${event.contractId}")
        println("Ledger: ${event.ledger}")

        // Parse topics from base64 XDR back to values
        val parsedTopics = event.parseTopic()
        parsedTopics.forEach { topic ->
            // Convert SCVal back to native types
            when {
                topic.sym != null -> println("Topic (symbol): ${Scv.fromSymbol(topic)}")
                topic.u32 != null -> println("Topic (u32): ${Scv.fromUint32(topic)}")
                // ... handle other types as needed
            }
        }

        println("Value: ${event.value}")
    }

    server.close()
}
```

**Event Filtering Rules:**
- Topics must be base64-encoded XDR SCVal values (use `Scv.toSymbol().toXdrBase64()`, etc.)
- Wildcards: `*` matches exactly one segment, `**` matches zero or more (only at end)
- Max 4 segments per topic filter (excluding trailing `**`)
- Max 5 topic filters per event filter
- Multiple topic filters are OR'd together
- Exact match example: `listOf(counterTopic)` matches events with exactly 1 topic
- Prefix match example: `listOf(counterTopic, "**")` matches events with 1+ topics where first is "COUNTER"

**Event Type Filtering:**
- Valid filter types: `SYSTEM`, `CONTRACT`, or `null`
- `null` (or omitted) matches all event types including DIAGNOSTIC events
- DIAGNOSTIC events cannot be filtered explicitly but are included when type is null

**Limits:**
- Max 5 filters per request
- Max 5 contract IDs per filter
- Max 5 topic filters per filter
- Ledger range: Use `startLedger`/`endLedger` OR cursor (mutually exclusive)

### SorobanDataBuilder

Build Soroban transaction data for advanced resource configuration:

```kotlin
import com.soneso.stellar.sdk.SorobanDataBuilder

// Use when you need manual control over transaction resources
// (most users should rely on automatic simulation)
val builder = SorobanDataBuilder()
    .setResourceFee(1000000L)  // Fee for resource consumption
    .setResources(
        SorobanDataBuilder.Resources(
            cpuInstructions = 10000000L,  // CPU instruction limit
            diskReadBytes = 1000L,        // Ledger read bytes
            writeBytes = 1000L            // Ledger write bytes
        )
    )
    .setReadOnly(listOf(ledgerKey1, ledgerKey2))  // Read-only ledger keys
    .setReadWrite(listOf(ledgerKey3))             // Read-write ledger keys

val sorobanData = builder.build()

// Apply to transaction
val transaction = TransactionBuilder(account, network)
    .addOperation(invokeOperation)
    .setSorobanData(sorobanData)
    .build()
```

**When to use SorobanDataBuilder:**
- Advanced users implementing custom simulation logic
- Testing specific resource limit scenarios
- Overriding simulation results for specialized operations
- Most applications should use automatic simulation via `ContractClient.invoke()` or `server.prepareTransaction()`

### Platform-Specific Network Patterns

Different platforms have different concurrency models. Here are platform-optimized patterns:

#### JVM (Android/Desktop)

```kotlin
// On JVM platforms, use runBlocking for top-level calls
fun jvmNetworkExample() {
    runBlocking {
        val server = SorobanServer("https://soroban-testnet.stellar.org")
        val health = server.getHealth()
        println(health.status)
        server.close()
    }
}
```

#### JavaScript (Browser/Node.js)

```kotlin
// In JavaScript, all operations are inherently async
suspend fun jsNetworkExample() {
    val server = SorobanServer("https://soroban-testnet.stellar.org")

    // All RPC operations are suspend functions
    val health = server.getHealth()
    console.log("Health: ${health.status}")

    server.close()
}
```

#### iOS/macOS

```kotlin
// On iOS/macOS, use coroutines from the main scope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class SorobanNetworkExample {
    private val scope = MainScope()

    fun checkHealth() {
        scope.launch {
            val server = SorobanServer("https://soroban-testnet.stellar.org")
            val health = server.getHealth()
            println("Health: ${health.status}")
            server.close()
        }
    }
}
```

## Testing Advanced Features

### Integration Test Patterns

Test complex contract interactions:

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.util.FriendBot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class ContractIntegrationTest {
    private val testnet = Network.TESTNET
    private val rpcUrl = "https://soroban-testnet.stellar.org:443"

    @Test
    fun testComplexContractWorkflow() = runTest {
        // Setup test accounts
        val source = KeyPair.random()
        val destination = KeyPair.random()

        // Fund accounts
        FriendBot.fundAccount(source.accountId)
        FriendBot.fundAccount(destination.accountId)

        // Deploy test contract
        val contractClient = ContractClient.forContract(
            contractId = "C...",
            rpcUrl = rpcUrl,
            network = testnet
        )

        // Test multi-step workflow
        val assembled = contractClient.buildInvoke<Unit>(
            functionName = "initialize",
            arguments = mapOf("admin" to source.accountId)
        )

        assembled.simulate()
        assertNotNull(assembled.simulation)

        assembled.signAndSubmit(source)

        // Verify state
        val state = contractClient.invoke<Map<String, Any>>(
            functionName = "get_state",
            arguments = emptyMap(),
            source = source.accountId,
            signer = null
        )

        assertEquals(source.accountId, state["admin"])
    }
}
```

### Testing Error Scenarios

Test error handling and recovery:

```kotlin
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.exception.ExpiredStateException
import io.mockk.*
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

@Test
fun testErrorRecovery() = runTest {
    val assembled = mockk<AssembledTransaction<Any>>()
    val contractClient = mockk<ContractClient>()

    // Simulate expired state with assembledTransaction
    coEvery {
        contractClient.invoke(any(), any(), any(), any())
    } throws ExpiredStateException("State expired", assembled)

    coEvery {
        assembled.restoreFootprint()
    } returns mockk()

    // Test recovery logic
    val recovered: Any? = invokeWithAutoRestore(
        contractClient,
        "test_function",
        emptyMap(),
        "G...",
        KeyPair.random()
    )

    // Verify restoration was called
    coVerify { assembled.restoreFootprint() }
}
```

---

**Navigation**: [← SDK Usage Examples](sdk-usage-examples.md) | [Home →](README.md)