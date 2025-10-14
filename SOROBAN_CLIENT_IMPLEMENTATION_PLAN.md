# SorobanClient Enhancement Implementation Plan

## Executive Summary

This plan outlines the implementation of missing features from the Flutter SDK's SorobanClient to bring the KMP SDK to feature parity. The Flutter SDK has a powerful ContractSpec class that provides automatic type conversion from native types to XDR, dramatically simplifying contract interactions.

## Current State Analysis

### What the Flutter SDK Has That KMP SDK Lacks

#### 1. **ContractSpec Class** ⭐ **HIGHEST PRIORITY**
The Flutter SDK has a powerful `ContractSpec` class (893 lines) that provides automatic type conversion from native types to XDR.

**Key capabilities:**
- `funcArgsToXdrSCValues(name, args)` - Converts `Map<String, dynamic>` to `List<XdrSCVal>`
- `nativeToXdrSCVal(val, ty)` - Converts native values to XDR based on type specs
- Automatic type inference and conversion for:
  - Primitives (bool, int, string)
  - Addresses (auto-detects "G..." or "C..." strings)
  - Large numbers (BigInt → i128/u128/i256/u256)
  - Collections (List → Vec, Map → Map)
  - Complex types (structs, unions, enums, tuples)
- `funcs()`, `getFunc(name)`, `findEntry(name)` - Spec introspection

**Impact:** This dramatically simplifies contract interaction. Compare:

```kotlin
// WITHOUT ContractSpec (current KMP SDK)
val args = listOf(
    Address.forAccountId(aliceId).toSCVal(),
    Address.forAccountId(bobId).toSCVal(),
    Address.forContractId(tokenA).toSCVal(),
    Address.forContractId(tokenB).toSCVal(),
    Scv.toInt128Parts(0, 1000),
    Scv.toInt128Parts(0, 4500),
    Scv.toInt128Parts(0, 5000),
    Scv.toInt128Parts(0, 950)
)

// WITH ContractSpec (Flutter SDK approach)
val args = contractSpec.funcArgsToXdrSCValues("swap", mapOf(
    "a" to aliceId,              // String → Address (automatic)
    "b" to bobId,                // String → Address (automatic)
    "token_a" to tokenA,         // String → Address (automatic)
    "token_b" to tokenB,         // String → Address (automatic)
    "amount_a" to 1000,          // Int → i128 (automatic)
    "min_b_for_a" to 4500,      // Int → i128 (automatic)
    "amount_b" to 5000,          // Int → i128 (automatic)
    "min_a_for_b" to 950         // Int → i128 (automatic)
))
```

#### 2. **High-Level SorobanClient Class**
The Flutter SDK has a feature-rich `SorobanClient` class that wraps ContractSpec and AssembledTransaction.

**Missing methods in KMP:**
- `forContractId(contractId, rpcUrl)` - Load contract from RPC, extract specs
- `deploy(deployRequest)` - Deploy contract with constructor args, return client
- `install(installRequest)` - Upload WASM, return hash (with force option)
- `invokeMethod(name, args, force)` - High-level invoke (auto-detects read/write)
- `buildInvokeMethodTx(name, args)` - Build tx without executing
- `getMethodNames()` - Extract method names from specs
- `getContractSpec()` - Get ContractSpec utility
- **ContractSpec convenience methods on client:**
  - `funcArgsToXdrSCValues(functionName, args)` - Direct on client
  - `nativeToXdrSCVal(val, ty)` - Direct on client

**Options classes:**
- `ClientOptions(sourceAccountKeyPair, contractId, network, rpcUrl, enableServerLogging)`
- `MethodOptions(fee, timeoutInSeconds, simulate, restore)`
- `AssembledTransactionOptions` - Combines client + method options
- `InstallRequest(wasmBytes, sourceAccountKeyPair, network, rpcUrl)`
- `DeployRequest(sourceAccountKeyPair, network, rpcUrl, wasmHash, constructorArgs, salt)`

#### 3. **AssembledTransaction Enhancements**

**Missing features:**
- `raw` property - Mutable `TransactionBuilder` before simulation
- `signed` property - Signed transaction (separate from built)
- **`signAuthEntries()` implementation** ⚠️ **CRITICAL GAP** (currently throws `NotImplementedError`)
  - Required for multi-auth scenarios (atomic swaps, etc.)
  - Supports delegate pattern for remote signing
  - Auto-sets expiration ledger (current + 100)
- `needsNonInvokerSigningBy(includeAlreadySigned)` - Enhanced version
- Better simulation data access (`getSimulationData()` returns structured data)

**Critical multi-auth workflow from Flutter:**
```dart
// Multi-auth workflow (CRITICAL for atomic swaps)
final tx = await atomicSwapClient.buildInvokeMethodTx(name: "swap", args: args);

// Check who needs to sign
final whoElseNeedsToSign = tx.needsNonInvokerSigningBy();
assert(whoElseNeedsToSign.contains(aliceId));
assert(whoElseNeedsToSign.contains(bobId));

// Sign auth entries for each party
await tx.signAuthEntries(signerKeyPair: aliceKeyPair);
await tx.signAuthEntries(signerKeyPair: bobKeyPair);

// Or with delegate for remote signing
await tx.signAuthEntries(
    signerKeyPair: bobPublicKeyOnlyKeyPair,
    authorizeEntryDelegate: (entry, network) async {
        // Send to remote server for signing
        final base64Entry = entry.toBase64EncodedXdrString();
        // ... remote signing ...
        return SorobanAuthorizationEntry.fromBase64EncodedXdr(signedEntry);
    });

await tx.signAndSend();
```

#### 4. **Helper Classes for Complex Types**

**Missing:**
- `NativeUnionVal` class - Represents union values (void case vs tuple case)
  ```dart
  // Void case (just a tag)
  NativeUnionVal.voidCase("Success")

  // Tuple case (tag + values)
  NativeUnionVal.tupleCase("Data", ["field1", "field2"])
  ```

---

## Implementation Plan (Prioritized)

### **Phase 1: ContractSpec Foundation** 🎯 **START HERE**
**Priority:** CRITICAL
**Estimated Effort:** 3-5 days
**Dependencies:** None

#### Tasks:

1. **Create `ContractSpec` class** (`stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/ContractSpec.kt`)

   **Core methods to implement:**
   ```kotlin
   class ContractSpec(private val entries: List<SCSpecEntryXdr>) {
       // Primary conversion methods
       fun funcArgsToXdrSCValues(functionName: String, args: Map<String, Any?>): List<SCValXdr>
       fun nativeToXdrSCVal(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr

       // Introspection methods
       fun funcs(): List<SCSpecFunctionV0Xdr>
       fun getFunc(name: String): SCSpecFunctionV0Xdr?
       fun findEntry(name: String): SCSpecEntryXdr?
   }
   ```

   **Type handlers to implement:**
   - **Primitives:**
     - `SC_SPEC_TYPE_BOOL` → `SCValXdr.Bool`
     - `SC_SPEC_TYPE_VOID` → `SCValXdr.Void`
     - `SC_SPEC_TYPE_STRING` → `SCValXdr.String`
     - `SC_SPEC_TYPE_SYMBOL` → `SCValXdr.Symbol`

   - **Numbers:**
     - `SC_SPEC_TYPE_U32` → `SCValXdr.U32` (validate range 0..0xFFFFFFFF)
     - `SC_SPEC_TYPE_I32` → `SCValXdr.I32` (validate range -0x80000000..0x7FFFFFFF)
     - `SC_SPEC_TYPE_U64` → `SCValXdr.U64`
     - `SC_SPEC_TYPE_I64` → `SCValXdr.I64`
     - `SC_SPEC_TYPE_U128` → `SCValXdr.U128` (handle Int, Long, BigInteger)
     - `SC_SPEC_TYPE_I128` → `SCValXdr.I128` (handle Int, Long, BigInteger)
     - `SC_SPEC_TYPE_U256` → `SCValXdr.U256` (handle BigInteger)
     - `SC_SPEC_TYPE_I256` → `SCValXdr.I256` (handle BigInteger)
     - `SC_SPEC_TYPE_TIMEPOINT` → `SCValXdr.Timepoint`
     - `SC_SPEC_TYPE_DURATION` → `SCValXdr.Duration`

   - **Addresses (auto-detection):**
     - `SC_SPEC_TYPE_ADDRESS`:
       - If String starts with "G" or "M" → Account address
       - If String starts with "C" → Contract address
       - Throw exception for invalid format

   - **Collections:**
     - `SC_SPEC_TYPE_VEC` → Recursively convert List elements
     - `SC_SPEC_TYPE_MAP` → Convert Map entries (key + value pairs)
     - `SC_SPEC_TYPE_TUPLE` → Convert List to ordered values
     - `SC_SPEC_TYPE_BYTES` → Convert ByteArray/List<Byte>/hex String
     - `SC_SPEC_TYPE_BYTES_N` → Fixed-size ByteArray (validate length)

   - **Complex types:**
     - `SC_SPEC_TYPE_OPTION` → Handle nullable values
     - `SC_SPEC_TYPE_RESULT` → Result type (ok/error)
     - `SC_SPEC_TYPE_UDT` → User-defined types (struct/union/enum)

   **Implementation details:**
   - Create private handler functions for each type category:
     - `private fun handleValueType(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr`
     - `private fun handleU128Type(value: Any?): SCValXdr`
     - `private fun handleI128Type(value: Any?): SCValXdr`
     - `private fun handleU256Type(value: Any?): SCValXdr`
     - `private fun handleI256Type(value: Any?): SCValXdr`
     - `private fun handleBytesType(value: Any?): SCValXdr`
     - `private fun handleAddressType(value: Any?): SCValXdr`
     - `private fun handleOptionType(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr`
     - `private fun handleVecType(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr`
     - `private fun handleMapType(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr`
     - `private fun handleTupleType(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr`
     - `private fun handleBytesNType(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr`
     - `private fun handleUDTType(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr`
     - `private fun handleStructType(value: Any?, structDef: SCSpecUDTStructV0Xdr): SCValXdr`
     - `private fun handleUnionType(value: Any?, unionDef: SCSpecUDTUnionV0Xdr): SCValXdr`
     - `private fun handleEnumType(value: Any?, enumDef: SCSpecUDTEnumV0Xdr): SCValXdr`
   - Helper function for integer parsing:
     - `private fun parseInteger(value: Any?, typeName: String): Long`
   - Helper function for type inference when no spec available:
     - `private fun inferAndConvert(value: Any?): SCValXdr`

2. **Create `ContractSpecException` class** (`stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/exception/ContractSpecException.kt`)

   ```kotlin
   open class ContractSpecException(
       message: String,
       val functionName: String? = null,
       val argumentName: String? = null,
       val entryName: String? = null
   ) : ContractException(message) {

       companion object {
           fun functionNotFound(name: String): ContractSpecException
           fun argumentNotFound(name: String, functionName: String? = null): ContractSpecException
           fun entryNotFound(name: String): ContractSpecException
           fun invalidType(message: String): ContractSpecException
           fun conversionFailed(message: String): ContractSpecException
           fun invalidEnumValue(message: String): ContractSpecException
       }
   }
   ```

3. **Create `NativeUnionVal` sealed class** (`stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/NativeUnionVal.kt`)

   ```kotlin
   sealed class NativeUnionVal {
       abstract val tag: String

       data class VoidCase(override val tag: String) : NativeUnionVal()

       data class TupleCase(
           override val tag: String,
           val values: List<Any?>
       ) : NativeUnionVal()

       val isVoidCase: Boolean get() = this is VoidCase
       val isTupleCase: Boolean get() = this is TupleCase
   }
   ```

4. **Add comprehensive tests** (`stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/contract/ContractSpecTest.kt`)

   Port test cases from Flutter SDK:
   - Test all primitive types (bool, int, string, symbol)
   - Test all numeric types (u32, i32, u64, i64, u128, i128, u256, i256)
   - Test address auto-detection ("G..." → account, "C..." → contract)
   - Test collections (vec, map, tuple)
   - Test fixed-size bytes (bytesN)
   - Test complex types (struct, union, enum)
   - Test option types (nullable values)
   - Test error cases (invalid types, missing arguments, out of range)
   - Test type inference when no spec available
   - Integration test with hello contract (compare manual XDR vs ContractSpec)

**Deliverable:** Full-featured `ContractSpec` matching Flutter SDK capabilities

**Reference Files:**
- Flutter source: `/Users/chris/projects/Stellar/stellar_flutter_sdk/lib/src/soroban/contract_spec.dart`
- Flutter tests: `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/soroban_client_test.dart` (lines 102-214, 280-324, 435-543)

---

### **Phase 2: Enhanced SorobanClient** 🎯
**Priority:** HIGH
**Estimated Effort:** 2-3 days
**Dependencies:** Phase 1 (ContractSpec)

#### Tasks:

1. **Create new `SorobanClient` class** (`stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/contract/SorobanClient.kt`)

   Keep existing `ContractClient` for backward compatibility, or rename it.

   ```kotlin
   class SorobanClient private constructor(
       private val contractId: String,
       private val rpcUrl: String,
       private val network: Network,
       private val specEntries: List<SCSpecEntryXdr>,
       private val methodNames: List<String>
   ) {
       private val server: SorobanServer = SorobanServer(rpcUrl)
       private val contractSpec: ContractSpec = ContractSpec(specEntries)

       companion object {
           // Load contract from RPC
           suspend fun forContractId(
               contractId: String,
               rpcUrl: String,
               network: Network
           ): SorobanClient

           // Deploy contract with constructor
           suspend fun deploy(deployRequest: DeployRequest): SorobanClient

           // Install contract WASM
           suspend fun install(
               installRequest: InstallRequest,
               force: Boolean = false
           ): String
       }

       // High-level invocation
       suspend fun <T> invokeMethod(
           name: String,
           args: Map<String, Any?>,
           force: Boolean = false,
           methodOptions: MethodOptions? = null
       ): T

       // Build transaction without executing
       suspend fun <T> buildInvokeMethodTx(
           name: String,
           args: Map<String, Any?>,
           methodOptions: MethodOptions? = null
       ): AssembledTransaction<T>

       // Accessors
       fun getContractId(): String
       fun getMethodNames(): List<String>
       fun getContractSpec(): ContractSpec
       fun getSpecEntries(): List<SCSpecEntryXdr>

       // ContractSpec convenience methods
       fun funcArgsToXdrSCValues(
           functionName: String,
           args: Map<String, Any?>
       ): List<SCValXdr> = contractSpec.funcArgsToXdrSCValues(functionName, args)

       fun nativeToXdrSCVal(value: Any?, typeDef: SCSpecTypeDefXdr): SCValXdr =
           contractSpec.nativeToXdrSCVal(value, typeDef)
   }
   ```

2. **Create options classes**

   ```kotlin
   // ClientOptions.kt
   data class ClientOptions(
       val sourceAccountKeyPair: KeyPair,
       val contractId: String,
       val network: Network,
       val rpcUrl: String,
       val enableServerLogging: Boolean = false
   )

   // MethodOptions.kt
   data class MethodOptions(
       val fee: Long = 100,
       val timeoutInSeconds: Long = 300,
       val simulate: Boolean = true,
       val restore: Boolean = false
   )

   // AssembledTransactionOptions.kt
   data class AssembledTransactionOptions(
       val clientOptions: ClientOptions,
       val methodOptions: MethodOptions,
       val method: String,
       val arguments: List<SCValXdr>? = null,
       val enableSorobanServerLogging: Boolean = false
   )

   // InstallRequest.kt
   data class InstallRequest(
       val wasmBytes: ByteArray,
       val sourceAccountKeyPair: KeyPair,
       val network: Network,
       val rpcUrl: String,
       val enableSorobanServerLogging: Boolean = false
   )

   // DeployRequest.kt
   data class DeployRequest(
       val sourceAccountKeyPair: KeyPair,
       val network: Network,
       val rpcUrl: String,
       val wasmHash: String,
       val constructorArgs: List<SCValXdr>? = null,
       val salt: Uint256Xdr? = null,
       val methodOptions: MethodOptions = MethodOptions(),
       val enableSorobanServerLogging: Boolean = false
   )
   ```

3. **Implementation details:**

   - `forContractId()`: Load contract info from RPC, parse WASM using `SorobanContractParser`
   - `deploy()`: Create contract with constructor, build transaction, submit, return client for deployed contract
   - `install()`: Upload WASM, simulate to get hash (or force submit), return hash
   - `invokeMethod()`: Use ContractSpec to convert args, detect read/write, auto-handle submission
   - `buildInvokeMethodTx()`: Build AssembledTransaction with ContractSpec-converted args

4. **Add tests** (`stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/contract/SorobanClientTest.kt`)

   Port Flutter tests:
   - Test forContractId loading
   - Test contract deployment with constructor
   - Test contract installation
   - Test invokeMethod with ContractSpec args
   - Test buildInvokeMethodTx

**Deliverable:** Feature-complete `SorobanClient` matching Flutter SDK

**Reference Files:**
- Flutter source: `/Users/chris/projects/Stellar/stellar_flutter_sdk/lib/src/soroban/soroban_client.dart` (lines 1-248)

---

### **Phase 3: AssembledTransaction Auth Support** 🎯 **CRITICAL**
**Priority:** CRITICAL (blocks multi-auth scenarios)
**Estimated Effort:** 2-3 days
**Dependencies:** None (can be done in parallel with Phase 1-2)

#### Tasks:

1. **Implement `signAuthEntries()`** in `AssembledTransaction.kt`

   Remove the `NotImplementedError` stub and implement full functionality:

   ```kotlin
   suspend fun signAuthEntries(
       authEntriesSigner: KeyPair,
       validUntilLedgerSequence: Long? = null,
       authorizeEntryDelegate: (suspend (SorobanAuthorizationEntryXdr, Network) -> SorobanAuthorizationEntryXdr)? = null
   ): AssembledTransaction<T> {
       if (builtTransaction == null) {
           throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
       }

       val signerAddress = authEntriesSigner.accountId

       // Validate signer has entries to sign
       val neededSigning = needsNonInvokerSigningBy(includeAlreadySigned = false)
       if (authorizeEntryDelegate == null) {
           if (neededSigning.isEmpty()) {
               throw IllegalStateException("No unsigned non-invoker auth entries; maybe you already signed?")
           }
           if (!neededSigning.contains(signerAddress)) {
               throw IllegalStateException("No auth entries for public key $signerAddress")
           }
           if (!authEntriesSigner.canSign()) {
               throw IllegalArgumentException("You must provide a signer keypair containing the private key.")
           }
       }

       // Get or calculate expiration ledger
       val expirationLedger = validUntilLedgerSequence ?: run {
           val latestLedger = server.getLatestLedger()
           latestLedger.sequence + 100
       }

       // Get operation and auth entries
       val operation = builtTransaction!!.operations.first() as InvokeHostFunctionOperation
       val authEntries = operation.auth.toMutableList()

       // Sign matching entries
       for (i in authEntries.indices) {
           val entry = authEntries[i]

           // Check if this entry needs signing by this signer
           if (entry.credentials.discriminant != SorobanCredentialsTypeXdr.SOROBAN_CREDENTIALS_ADDRESS) {
               continue
           }

           val addressCreds = entry.credentials as? SorobanCredentialsXdr.Address ?: continue
           val entryAddress = Address.fromSCAddress(addressCreds.value.address).toString()

           if (entryAddress != signerAddress) {
               continue
           }

           // Set expiration
           addressCreds.value.signatureExpirationLedger = expirationLedger

           // Sign the entry
           val signedEntry = if (authorizeEntryDelegate != null) {
               authorizeEntryDelegate(entry, network)
           } else {
               Auth.authorizeEntry(entry, authEntriesSigner, network)
               entry
           }

           authEntries[i] = signedEntry
       }

       // Update transaction with signed auth entries
       // Need to rebuild transaction with updated auth
       val updatedOperation = InvokeHostFunctionOperation(
           hostFunction = operation.hostFunction,
           auth = authEntries
       )

       // Rebuild transaction with updated operation
       val updatedTxBuilder = TransactionBuilder(
           sourceAccount = builtTransaction!!.sourceAccount,
           network = network
       )
           .addOperation(updatedOperation)
           .setBaseFee(builtTransaction!!.fee)
           .addPreconditions(builtTransaction!!.preconditions)
           .setSorobanData(builtTransaction!!.sorobanData!!)

       builtTransaction = updatedTxBuilder.build()

       return this
   }
   ```

2. **Add enhanced properties to AssembledTransaction:**

   ```kotlin
   class AssembledTransaction<T>(
       private val server: SorobanServer,
       private val submitTimeout: Int,
       private val transactionSigner: KeyPair?,
       private val parseResultXdrFn: ((SCValXdr) -> T)?,
       private var transactionBuilder: TransactionBuilder
   ) {
       // Add raw property for pre-simulation manipulation
       var raw: TransactionBuilder? = transactionBuilder
           private set

       // Add signed property (separate from builtTransaction)
       var signed: Transaction? = null
           private set

       // Existing properties...
       var builtTransaction: Transaction? = null
           private set

       var simulation: SimulateTransactionResponse? = null
           private set

       // ... rest of implementation
   }
   ```

3. **Create `SimulateHostFunctionResult` data class:**

   ```kotlin
   data class SimulateHostFunctionResult(
       val auth: List<SorobanAuthorizationEntryXdr>?,
       val transactionData: SorobanTransactionDataXdr,
       val returnedValue: SCValXdr
   )

   // Add to AssembledTransaction:
   fun getSimulationData(): SimulateHostFunctionResult {
       // Return structured simulation data
   }
   ```

4. **Update `sign()` method to set `signed` property:**

   ```kotlin
   suspend fun sign(transactionSigner: KeyPair? = null, force: Boolean = false): AssembledTransaction<T> {
       // ... existing validation ...

       builtTransaction!!.sign(signer)
       signed = builtTransaction  // Set signed property

       return this
   }
   ```

5. **Add comprehensive integration tests:**

   Port Flutter atomic swap test (lines 326-433 in soroban_client_test.dart):

   ```kotlin
   @Test
   fun testAtomicSwap() = runTest {
       // Deploy atomic swap contract and token contracts
       val atomicSwapClient = deployContract(swapContractWasmHash)
       val tokenAClient = deployContract(tokenContractWasmHash)
       val tokenBClient = deployContract(tokenContractWasmHash)

       // Create tokens and fund Alice and Bob
       createToken(tokenAClient, adminKeyPair, "TokenA", "TokenA")
       createToken(tokenBClient, adminKeyPair, "TokenB", "TokenB")
       mint(tokenAClient, adminKeyPair, aliceId, 10000000000000)
       mint(tokenBClient, adminKeyPair, bobId, 10000000000000)

       // Build swap transaction
       val args = listOf(
           Address.forAccountId(aliceId).toSCVal(),
           Address.forAccountId(bobId).toSCVal(),
           Address.forContractId(tokenAContractId).toSCVal(),
           Address.forContractId(tokenBContractId).toSCVal(),
           Scv.toInt128Parts(0, 1000),
           Scv.toInt128Parts(0, 4500),
           Scv.toInt128Parts(0, 5000),
           Scv.toInt128Parts(0, 950)
       )

       val tx = atomicSwapClient.invoke<SCValXdr>(
           functionName = "swap",
           parameters = args,
           source = sourceAccountKeyPair.accountId,
           signer = sourceAccountKeyPair,
           parseResultXdrFn = null
       )

       // Check who needs to sign
       val whoElseNeedsToSign = tx.needsNonInvokerSigningBy()
       assertEquals(2, whoElseNeedsToSign.size)
       assertTrue(whoElseNeedsToSign.contains(aliceId))
       assertTrue(whoElseNeedsToSign.contains(bobId))

       // Sign auth entries for Alice
       tx.signAuthEntries(aliceKeyPair)

       // Test delegate signing for Bob
       val bobPublicKeyOnly = KeyPair.fromAccountId(bobId)
       tx.signAuthEntries(
           authEntriesSigner = bobPublicKeyOnly,
           authorizeEntryDelegate = { entry, network ->
               // Simulate remote signing
               val base64Entry = entry.toXdrBase64()
               val entryToSign = SorobanAuthorizationEntryXdr.fromXdrBase64(base64Entry)
               Auth.authorizeEntry(entryToSign, bobKeyPair, network)
               entryToSign
           }
       )

       // Submit transaction
       val response = tx.signAndSubmit(sourceAccountKeyPair)
       // Verify success
   }
   ```

**Deliverable:** Full multi-auth support matching Flutter SDK

**Reference Files:**
- Flutter source: `/Users/chris/projects/Stellar/stellar_flutter_sdk/lib/src/soroban/soroban_client.dart` (lines 723-800)
- Flutter test: `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/soroban_client_test.dart` (lines 326-433)
- Java SDK reference: `/Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/SorobanServer.java`

---

### **Phase 4: Contract Bindings Generator** 🎯 (Optional/Advanced)
**Priority:** NICE-TO-HAVE
**Estimated Effort:** 5-7 days
**Dependencies:** Phase 1, 2, 3

This phase is optional and can be deferred. It involves creating a code generation tool (Gradle plugin or CLI) that generates type-safe Kotlin client classes from contract WASM files.

**Example output:**
```kotlin
// Generated HelloContract.kt
class HelloContract private constructor(
    private val client: SorobanClient
) {
    companion object {
        suspend fun forContractId(
            sourceAccountKeyPair: KeyPair,
            contractId: String,
            network: Network,
            rpcUrl: String
        ): HelloContract {
            val client = SorobanClient.forContractId(contractId, rpcUrl, network)
            return HelloContract(client)
        }
    }

    // Type-safe method call
    suspend fun hello(to: String): List<String> {
        val result = client.invokeMethod<SCValXdr>(
            name = "hello",
            args = mapOf("to" to to)
        )
        // Parse result to List<String>
        return result.vec!!.map { it.sym!! }
    }

    // For auth scenarios
    suspend fun buildHelloTx(to: String): AssembledTransaction<List<String>> {
        return client.buildInvokeMethodTx(
            name = "hello",
            args = mapOf("to" to to)
        )
    }
}
```

---

## Testing Strategy

### Integration Tests to Port from Flutter SDK

All tests from `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/soroban_client_test.dart`:

1. ✅ Hello contract (basic invocation) - lines 141-161
2. ✅ Hello contract with ContractSpec - lines 163-214
3. ✅ Auth contract (single signer) - lines 216-278
4. ✅ Auth contract with ContractSpec - lines 280-324
5. ✅ **Atomic swap** (multi-auth) - lines 326-433 - **CRITICAL TEST**
6. ✅ Atomic swap with ContractSpec - lines 435-543
7. ✅ Hello contract with contract binding - lines 545-574
8. ✅ Auth contract with contract binding - lines 576-639
9. ✅ Atomic swap with contract binding - lines 641-788

### Test Data Required

- Hello contract WASM: `test/wasm/soroban_hello_world_contract.wasm`
- Auth contract WASM: `test/wasm/soroban_auth_contract.wasm`
- Atomic swap contract WASM: `test/wasm/soroban_atomic_swap_contract.wasm`
- Token contract WASM: `test/wasm/soroban_token_contract.wasm`

---

## Migration Path for Existing Users

### Option 1: Keep ContractClient, add SorobanClient (Recommended)

```kotlin
// Old API (still works)
val client = ContractClient(contractId, rpcUrl, network)
val assembled = client.invoke(...) // Manual XDR conversion required

// New API (much easier)
val client = SorobanClient.forContractId(contractId, rpcUrl, network)
val result = client.invokeMethod("hello", mapOf("to" to "World"))
```

### Option 2: Deprecate ContractClient, migrate to SorobanClient

- Mark `ContractClient` as `@Deprecated(message = "Use SorobanClient instead", ReplaceWith("SorobanClient"))`
- Provide migration guide in documentation
- Keep both for 1-2 major versions

---

## Key Advantages After Implementation

1. **90% less boilerplate** - No manual XDR conversion
2. **Type safety** - Compile-time checks with ContractSpec
3. **Multi-auth support** - Critical for DeFi/atomic swaps
4. **Better DX** - `Map<String, Any>` args instead of `List<SCValXdr>`
5. **Remote signing** - Delegate pattern for HSMs/multi-server
6. **Feature parity** - Match Java/Flutter/TypeScript SDKs
7. **Production-ready** - Battle-tested patterns from Flutter SDK

---

## Success Criteria

### Phase 1 Complete:
- [ ] ContractSpec class implemented with all type handlers
- [ ] All primitive types supported (bool, int, string, etc.)
- [ ] Address auto-detection working ("G..." → account, "C..." → contract)
- [ ] Complex types supported (struct, union, enum, vec, map)
- [ ] ContractSpecException hierarchy complete
- [ ] NativeUnionVal sealed class implemented
- [ ] 50+ unit tests passing (covering all type conversions)
- [ ] Integration test showing ContractSpec simplifies hello contract call

### Phase 2 Complete:
- [ ] SorobanClient class implemented
- [ ] forContractId() loads contract and extracts specs
- [ ] deploy() deploys contract with constructor
- [ ] install() uploads WASM and returns hash
- [ ] invokeMethod() uses ContractSpec for auto-conversion
- [ ] buildInvokeMethodTx() builds tx with ContractSpec args
- [ ] All options classes implemented
- [ ] Integration tests passing (hello, auth contracts)

### Phase 3 Complete:
- [ ] signAuthEntries() fully implemented (no more NotImplementedError)
- [ ] Delegate pattern working for remote signing
- [ ] ValidUntilLedgerSequence auto-set (current + 100)
- [ ] SimulateHostFunctionResult data class added
- [ ] Enhanced properties (raw, signed) added
- [ ] Atomic swap integration test passing
- [ ] Multi-auth workflows fully supported

---

## Recommendation: Start with Phase 1

The **ContractSpec** class is the foundation for everything else and provides immediate value. Once implemented, it can be used standalone even before the other phases.

**Quick Win Example:**
```kotlin
// After Phase 1 only
val spec = ContractSpec(specEntries)
val args = spec.funcArgsToXdrSCValues("swap", mapOf(
    "a" to aliceId,    // So much cleaner!
    "amount_a" to 1000
))
client.invoke("swap", args, ...) // Existing ContractClient still works
```

---

## Reference Documentation

- **Flutter SDK Source:** `/Users/chris/projects/Stellar/stellar_flutter_sdk/lib/src/soroban/`
  - `soroban_client.dart` - Main client implementation
  - `contract_spec.dart` - ContractSpec implementation
- **Flutter SDK Tests:** `/Users/chris/projects/Stellar/stellar_flutter_sdk/test/soroban_client_test.dart`
- **Java SDK Reference:** `/Users/chris/projects/Stellar/java-stellar-sdk/`
- **KMP SDK Current Implementation:**
  - `ContractClient.kt`
  - `AssembledTransaction.kt`
  - `SorobanContractParser.kt`
