# ContractClient and AssembledTransaction Implementation Plan

**Date:** October 5, 2025
**Status:** Ready for Implementation
**Priority:** High (Phase 2 Feature)
**Estimated Effort:** 4-5 days
**Complexity:** High

---

## 1. Overview

### What Are These Classes?

#### ContractClient
A high-level developer-friendly API for interacting with Soroban smart contracts. It abstracts away the complexity of:
- Building transactions with `InvokeHostFunctionOperation`
- Simulating transactions to get resource estimates
- Handling authorization entries
- Managing SorobanServer connections

**Purpose:** Simplify contract invocations from ~20 lines of boilerplate to 2-3 lines.

#### AssembledTransaction
Represents a prepared, ready-to-sign transaction that has been simulated and assembled with proper resource estimates. It manages the complete transaction lifecycle:
- Simulation → Preparation → Signing → Submission → Result retrieval
- Authorization entry management
- Automatic contract state restoration when needed
- Result parsing and type conversion

**Purpose:** Provide a fluent, type-safe API for the entire transaction lifecycle.

### Why They're Needed

**Before (manual approach):**
```kotlin
// Verbose, error-prone
val account = server.getAccount(accountId)
val operation = InvokeHostFunctionOperation(...)
val transaction = TransactionBuilder(account, network)
    .addOperation(operation)
    .setBaseFee(baseFee)
    .setTimeout(300)
    .build()
val simulation = server.simulateTransaction(transaction)
val prepared = server.prepareTransaction(transaction, simulation)
prepared.sign(keypair)
val response = server.sendTransaction(prepared)
val result = server.pollTransaction(response.hash!!)
// Parse result manually...
```

**After (ContractClient + AssembledTransaction):**
```kotlin
// Simple, type-safe
val client = ContractClient(contractId, rpcUrl, network)
val result = client.invoke<TransferResult>(
    functionName = "transfer",
    parameters = listOf(
        Scv.toAddress(fromAddress),
        Scv.toAddress(toAddress),
        Scv.toInt128(amount)
    ),
    source = accountId,
    signer = keypair,
    parseResultXdrFn = { TransferResult.fromXdr(it) }
).signAndSubmit(keypair)
```

---

## 2. Architecture

### Class Relationship

```
┌─────────────────┐
│ ContractClient  │────┐
└─────────────────┘    │
                       │ creates
                       ↓
              ┌──────────────────────┐
              │ AssembledTransaction │
              └──────────────────────┘
                       │
                       ├─→ simulate() → SimulateTransactionResponse
                       ├─→ sign() → Transaction with signatures
                       ├─→ signAuthEntries() → Auth entry signatures
                       └─→ submit() → GetTransactionResponse + parsed result
```

### Dependencies

Both classes depend on existing KMP SDK components:

**From com.stellar.sdk:**
- `Transaction` / `TransactionBuilder` - Transaction construction
- `KeyPair` - Signing operations
- `Network` - Network configuration
- `Account` - Source account management
- `Auth` - Authorization entry signing
- `Address` - Contract address handling
- `Scv` - SCVal construction and parsing

**From com.stellar.sdk.rpc:**
- `SorobanServer` - RPC communication
- `SimulateTransactionResponse` - Simulation results
- `SendTransactionResponse` - Transaction submission
- `GetTransactionResponse` - Transaction status
- `SorobanDataBuilder` - Soroban data construction

**From com.stellar.sdk.xdr:**
- `SCValXdr` - Smart contract values
- `SorobanAuthorizationEntryXdr` - Authorization entries
- `InvokeHostFunctionOpXdr` - Contract invocation
- `TransactionMetaXdr` - Transaction metadata
- `SorobanTransactionDataXdr` - Soroban transaction data
- `LedgerKeyXdr` - Ledger footprint entries

---

## 3. API Design

### ContractClient

```kotlin
package com.stellar.sdk.contract

import com.stellar.sdk.KeyPair
import com.stellar.sdk.Network
import com.stellar.sdk.rpc.SorobanServer
import com.stellar.sdk.xdr.SCValXdr

/**
 * A client to interact with Soroban smart contracts.
 *
 * This client wraps [SorobanServer] and [TransactionBuilder] to make it easier
 * to interact with Soroban smart contracts. For more fine-grained control, use
 * them directly.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * val client = ContractClient(
 *     contractId = "CABC...",
 *     rpcUrl = "https://soroban-testnet.stellar.org:443",
 *     network = Network.TESTNET
 * )
 *
 * // Read-only call (no signing needed)
 * val balance = client.invoke<Long>(
 *     functionName = "balance",
 *     parameters = listOf(Scv.toAddress(accountId)),
 *     source = accountId,
 *     signer = null,
 *     parseResultXdrFn = { Scv.fromInt128(it).toLong() }
 * ).result()
 *
 * // Write call (requires signing)
 * val result = client.invoke<Unit>(
 *     functionName = "transfer",
 *     parameters = listOf(
 *         Scv.toAddress(from),
 *         Scv.toAddress(to),
 *         Scv.toInt128(amount)
 *     ),
 *     source = accountId,
 *     signer = keypair,
 *     parseResultXdrFn = null // Void return
 * ).signAndSubmit(keypair)
 *
 * // Close when done
 * client.close()
 * ```
 *
 * @property contractId The contract ID to interact with (C... address)
 * @property network The network to interact with
 * @property server The SorobanServer instance for RPC calls
 */
class ContractClient(
    val contractId: String,
    rpcUrl: String,
    val network: Network
) {
    val server: SorobanServer = SorobanServer(rpcUrl)

    /**
     * Build an [AssembledTransaction] to invoke a function on the contract.
     *
     * This is a convenience method with default timeout values.
     *
     * @param functionName The name of the function to invoke
     * @param parameters The parameters to pass to the function (as SCValXdr)
     * @param source The source account for the transaction (G... or M... address)
     * @param signer The KeyPair to sign with (or null for read-only calls)
     * @param parseResultXdrFn Function to parse the result XDR (or null for raw SCValXdr)
     * @param baseFee The base fee for the transaction (default 100)
     * @return An AssembledTransaction ready for signing/submitting
     */
    suspend fun <T> invoke(
        functionName: String,
        parameters: List<SCValXdr>,
        source: String,
        signer: KeyPair?,
        parseResultXdrFn: ((SCValXdr) -> T)? = null,
        baseFee: Int = 100
    ): AssembledTransaction<T>

    /**
     * Build an [AssembledTransaction] to invoke a function on the contract.
     *
     * Full control version with all configuration options.
     *
     * @param functionName The name of the function to invoke
     * @param parameters The parameters to pass to the function (as SCValXdr)
     * @param source The source account for the transaction (G... or M... address)
     * @param signer The KeyPair to sign with (or null for read-only calls)
     * @param parseResultXdrFn Function to parse the result XDR (or null for raw SCValXdr)
     * @param baseFee The base fee for the transaction (default 100)
     * @param transactionTimeout Transaction validity timeout in seconds (default 300)
     * @param submitTimeout Polling timeout when submitting in seconds (default 30)
     * @param simulate Whether to simulate the transaction (default true)
     * @param restore Whether to auto-restore contract state if needed (default true)
     * @return An AssembledTransaction ready for signing/submitting
     */
    suspend fun <T> invoke(
        functionName: String,
        parameters: List<SCValXdr>,
        source: String,
        signer: KeyPair?,
        parseResultXdrFn: ((SCValXdr) -> T)?,
        baseFee: Int = 100,
        transactionTimeout: Int = 300,
        submitTimeout: Int = 30,
        simulate: Boolean = true,
        restore: Boolean = true
    ): AssembledTransaction<T>

    /**
     * Close the underlying SorobanServer connection.
     */
    fun close()
}
```

### AssembledTransaction

```kotlin
package com.stellar.sdk.contract

import com.stellar.sdk.KeyPair
import com.stellar.sdk.Network
import com.stellar.sdk.Transaction
import com.stellar.sdk.TransactionBuilder
import com.stellar.sdk.rpc.SorobanServer
import com.stellar.sdk.rpc.responses.GetTransactionResponse
import com.stellar.sdk.rpc.responses.SendTransactionResponse
import com.stellar.sdk.rpc.responses.SimulateTransactionResponse
import com.stellar.sdk.xdr.SCValXdr

/**
 * Represents a prepared transaction ready for signing and submission.
 *
 * AssembledTransaction manages the complete lifecycle:
 * 1. **Simulate** - Get resource estimates from the network
 * 2. **Sign** - Add transaction and/or authorization signatures
 * 3. **Submit** - Send to network and poll for completion
 * 4. **Parse** - Convert result to desired type
 *
 * ## Usage Patterns
 *
 * ### Read-Only Call (No Signing)
 * ```kotlin
 * val assembled = client.invoke<Long>(
 *     functionName = "balance",
 *     parameters = listOf(Scv.toAddress(account)),
 *     source = account,
 *     signer = null,
 *     parseResultXdrFn = { Scv.fromInt128(it).toLong() }
 * )
 * val balance = assembled.result() // Returns simulated result
 * ```
 *
 * ### Write Call (Standard Flow)
 * ```kotlin
 * val assembled = client.invoke<Unit>(
 *     functionName = "transfer",
 *     parameters = listOf(from, to, amount),
 *     source = account,
 *     signer = keypair,
 *     parseResultXdrFn = null
 * )
 * val result = assembled.signAndSubmit(keypair) // One-step
 * ```
 *
 * ### Advanced Flow (Separate Steps)
 * ```kotlin
 * val assembled = client.invoke<TransferResult>(...)
 * assembled.sign(keypair)            // Sign transaction
 * assembled.signAuthEntries(keypair) // Sign auth entries if needed
 * val result = assembled.submit()    // Submit and wait
 * ```
 *
 * ### Check Authorization Requirements
 * ```kotlin
 * val assembled = client.invoke<Unit>(...)
 * val needsSigning = assembled.needsNonInvokerSigningBy(includeAlreadySigned = false)
 * if (needsSigning.isNotEmpty()) {
 *     println("Needs signatures from: $needsSigning")
 *     // Collect signatures...
 * }
 * assembled.sign(keypair)
 * ```
 *
 * @param T The type of the parsed result
 * @property server The SorobanServer for RPC calls
 * @property submitTimeout Polling timeout in seconds
 * @property transactionSigner Optional KeyPair for signing
 * @property parseResultXdrFn Optional function to parse result
 * @property transactionBuilder The transaction builder
 */
class AssembledTransaction<T>(
    private val server: SorobanServer,
    private val submitTimeout: Int,
    private val transactionSigner: KeyPair?,
    private val parseResultXdrFn: ((SCValXdr) -> T)?,
    private val transactionBuilder: TransactionBuilder
) {
    /**
     * The built transaction after simulation (null before simulation).
     */
    var builtTransaction: Transaction? = null
        private set

    /**
     * The simulation response (null before simulation).
     */
    var simulation: SimulateTransactionResponse? = null
        private set

    /**
     * The send transaction response (null before submission).
     */
    var sendTransactionResponse: SendTransactionResponse? = null
        private set

    /**
     * The get transaction response (null before completion).
     */
    var getTransactionResponse: GetTransactionResponse? = null
        private set

    /**
     * Simulates the transaction on the network.
     *
     * Must be called before signing or submitting. Will automatically restore
     * required contract state if [restore] is true and this is not a read call.
     *
     * @param restore Whether to automatically restore contract state if needed
     * @return This AssembledTransaction for chaining
     * @throws SimulationFailedException if the simulation failed
     * @throws RestorationFailureException if contract state could not be restored
     */
    suspend fun simulate(restore: Boolean = true): AssembledTransaction<T>

    /**
     * Signs the transaction.
     *
     * @param transactionSigner The KeyPair to sign with (or null to use constructor signer)
     * @param force Whether to sign even if this is a read call (default false)
     * @return This AssembledTransaction for chaining
     * @throws NotYetSimulatedException if not yet simulated
     * @throws NoSignatureNeededException if read call and force=false
     * @throws ExpiredStateException if contract state needs restoration
     * @throws NeedsMoreSignaturesException if more auth signatures required
     */
    suspend fun sign(transactionSigner: KeyPair? = null, force: Boolean = false): AssembledTransaction<T>

    /**
     * Signs the transaction and submits it in one step.
     *
     * Convenience method combining [sign] and [submit].
     *
     * @param transactionSigner The KeyPair to sign with (or null to use constructor signer)
     * @param force Whether to sign even if this is a read call (default false)
     * @return The parsed result (or raw SCValXdr if parseResultXdrFn is null)
     * @throws NotYetSimulatedException if not yet simulated
     * @throws NoSignatureNeededException if read call and force=false
     * @throws NeedsMoreSignaturesException if more auth signatures required
     * @throws SendTransactionFailedException if submission failed
     * @throws TransactionStillPendingException if still pending after timeout
     * @throws ExpiredStateException if contract state needs restoration
     * @throws TransactionFailedException if transaction failed
     */
    suspend fun signAndSubmit(transactionSigner: KeyPair? = null, force: Boolean = false): T

    /**
     * Signs the authorization entries in the transaction.
     *
     * Used when the contract invocation requires authorization from specific accounts.
     * The validUntilLedgerSequence defaults to current ledger + 100.
     *
     * @param authEntriesSigner The KeyPair to sign auth entries with
     * @param validUntilLedgerSequence Ledger sequence until which signatures are valid (or null for default)
     * @return This AssembledTransaction for chaining
     * @throws NotYetSimulatedException if not yet simulated
     */
    suspend fun signAuthEntries(
        authEntriesSigner: KeyPair,
        validUntilLedgerSequence: Long? = null
    ): AssembledTransaction<T>

    /**
     * Get the addresses that need to sign authorization entries.
     *
     * Returns account or contract addresses (G.., C..) that need to sign auth entries.
     * Useful for multi-sig scenarios where you need to collect signatures.
     *
     * @param includeAlreadySigned Whether to include already-signed entries (default false)
     * @return Set of addresses that need to sign
     * @throws NotYetSimulatedException if not yet simulated
     */
    fun needsNonInvokerSigningBy(includeAlreadySigned: Boolean = false): Set<String>

    /**
     * Get the result from simulation.
     *
     * For read-only calls, this returns the simulated result without submitting.
     * For write calls, use [submit] or [signAndSubmit] instead.
     *
     * @return The parsed result (or raw SCValXdr if parseResultXdrFn is null)
     * @throws NotYetSimulatedException if not yet simulated
     */
    fun result(): T

    /**
     * Check if this is a read-only call.
     *
     * A call is read-only if:
     * - It has no authorization entries
     * - It has no read-write footprint entries
     *
     * Read-only calls don't need signing or submitting - just call [result].
     *
     * @return true if read-only, false otherwise
     * @throws NotYetSimulatedException if not yet simulated
     */
    fun isReadCall(): Boolean

    /**
     * Submits the transaction to the network.
     *
     * Sends the transaction and polls for completion. Parses the result using
     * parseResultXdrFn if provided.
     *
     * @return The parsed result (or raw SCValXdr if parseResultXdrFn is null)
     * @throws NotYetSimulatedException if not yet simulated
     * @throws SendTransactionFailedException if submission failed
     * @throws TransactionStillPendingException if still pending after timeout
     * @throws TransactionFailedException if transaction failed
     */
    suspend fun submit(): T

    /**
     * Get the transaction envelope as base64-encoded XDR.
     *
     * @return The transaction envelope XDR string
     */
    fun toEnvelopeXdrBase64(): String

    /**
     * Restores the contract footprint.
     *
     * Called automatically by [simulate] when restore=true and footprint needs restoration.
     * Can also be called manually if you want to restore before simulation.
     *
     * @throws TransactionFailedException if restoration transaction failed
     * @throws TransactionStillPendingException if restoration still pending
     * @throws SendTransactionFailedException if sending restoration failed
     */
    suspend fun restoreFootprint()
}
```

---

## 4. Key Features

### ContractClient Features

1. **Simple Initialization**
   - Takes contract ID, RPC URL, and network
   - Creates and manages SorobanServer internally
   - Implements Closeable for resource cleanup

2. **Invoke Method**
   - Two overloads: simple (defaults) and full (all options)
   - Type-safe generic return type `<T>`
   - Accepts nullable signer (for read-only calls)
   - Optional result parser function
   - Configurable timeouts and behavior

3. **Contract Function Invocation**
   - Builds `InvokeHostFunctionOperation` automatically
   - Creates `TransactionBuilder` with correct parameters
   - Returns `AssembledTransaction` for lifecycle management

### AssembledTransaction Features

1. **Simulation**
   - Fetches latest account sequence number
   - Calls `SorobanServer.simulateTransaction()`
   - Automatically restores footprint if needed
   - Detects and handles restoration preambles
   - Re-simulates after restoration
   - Assembles transaction with resource estimates

2. **Signing**
   - Transaction signature (envelope)
   - Authorization entry signatures (contract auth)
   - Support for custom signers
   - Validation: checks if already simulated, if signatures needed, if state expired
   - Prevents signing read-only calls (unless forced)

3. **Authorization Management**
   - Identifies addresses that need to sign auth entries
   - Filters out contract addresses (C...)
   - Supports multi-signature workflows
   - Integrates with `Auth.authorizeEntry()`

4. **Submission**
   - Sends transaction via `SorobanServer.sendTransaction()`
   - Polls for completion using exponential backoff
   - Parses result from `TransactionMetaXdr`
   - Handles all transaction statuses (SUCCESS, FAILED, NOT_FOUND)

5. **Result Parsing**
   - Simulation results (read-only calls)
   - Transaction execution results (write calls)
   - Custom parser function support
   - Raw `SCValXdr` fallback

6. **State Management**
   - Tracks simulation, send, and get responses
   - Immutable transaction after building
   - Proper state validation throughout lifecycle

---

## 5. Implementation Details

### ContractClient Core Logic

```kotlin
// File: stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/contract/ContractClient.kt

package com.stellar.sdk.contract

import com.stellar.sdk.*
import com.stellar.sdk.rpc.SorobanServer
import com.stellar.sdk.xdr.*

class ContractClient(
    val contractId: String,
    rpcUrl: String,
    val network: Network
) {
    val server: SorobanServer = SorobanServer(rpcUrl)

    suspend fun <T> invoke(
        functionName: String,
        parameters: List<SCValXdr>,
        source: String,
        signer: KeyPair?,
        parseResultXdrFn: ((SCValXdr) -> T)? = null,
        baseFee: Int = 100
    ): AssembledTransaction<T> {
        return invoke(
            functionName = functionName,
            parameters = parameters,
            source = source,
            signer = signer,
            parseResultXdrFn = parseResultXdrFn,
            baseFee = baseFee,
            transactionTimeout = 300,
            submitTimeout = 30,
            simulate = true,
            restore = true
        )
    }

    suspend fun <T> invoke(
        functionName: String,
        parameters: List<SCValXdr>,
        source: String,
        signer: KeyPair?,
        parseResultXdrFn: ((SCValXdr) -> T)?,
        baseFee: Int = 100,
        transactionTimeout: Int = 300,
        submitTimeout: Int = 30,
        simulate: Boolean = true,
        restore: Boolean = true
    ): AssembledTransaction<T> {
        // Build InvokeHostFunctionOperation
        val operation = InvokeHostFunctionOperation.invokeContractFunction(
            contractAddress = contractId,
            functionName = functionName,
            parameters = parameters
        )

        // Create transaction builder with placeholder account (sequence will be updated in simulate)
        val builder = TransactionBuilder(
            sourceAccount = Account(source, 0L),
            network = network
        )
            .addOperation(operation)
            .setTimeout(transactionTimeout.toLong())
            .setBaseFee(baseFee)

        // Create assembled transaction
        val assembled = AssembledTransaction(
            server = server,
            submitTimeout = submitTimeout,
            transactionSigner = signer,
            parseResultXdrFn = parseResultXdrFn,
            transactionBuilder = builder
        )

        // Simulate if requested
        if (simulate) {
            assembled.simulate(restore)
        }

        return assembled
    }

    fun close() {
        server.close()
    }
}
```

### AssembledTransaction Lifecycle

```kotlin
// File: stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/contract/AssembledTransaction.kt

package com.stellar.sdk.contract

import com.stellar.sdk.*
import com.stellar.sdk.contract.exception.*
import com.stellar.sdk.rpc.SorobanServer
import com.stellar.sdk.rpc.responses.*
import com.stellar.sdk.xdr.*
import kotlinx.coroutines.delay

class AssembledTransaction<T>(
    private val server: SorobanServer,
    private val submitTimeout: Int,
    private val transactionSigner: KeyPair?,
    private val parseResultXdrFn: ((SCValXdr) -> T)?,
    private val transactionBuilder: TransactionBuilder
) {
    var builtTransaction: Transaction? = null
        private set

    var simulation: SimulateTransactionResponse? = null
        private set

    var sendTransactionResponse: SendTransactionResponse? = null
        private set

    var getTransactionResponse: GetTransactionResponse? = null
        private set

    // Cached simulation data
    private var simulationResult: SimulateTransactionResponse.SimulateHostFunctionResult? = null
    private var simulationTransactionData: SorobanTransactionDataXdr? = null

    suspend fun simulate(restore: Boolean = true): AssembledTransaction<T> {
        // Clear cached data
        simulationResult = null
        simulationTransactionData = null

        // Update source account sequence number
        val sourceAccount = server.getAccount(transactionBuilder.sourceAccount.accountId)
        transactionBuilder.sourceAccount.sequenceNumber = sourceAccount.sequenceNumber

        // Build and simulate
        val builtTx = transactionBuilder.build()
        simulation = server.simulateTransaction(builtTx)

        // Handle restoration if needed
        if (restore && simulation!!.restorePreamble != null && !isReadCall()) {
            try {
                restoreFootprint()
            } catch (e: Exception) {
                throw RestorationFailureException("Failed to restore contract data.", this)
            }
            // Re-simulate after restoration
            return simulate(restore = false)
        }

        // Check for simulation errors
        if (simulation!!.error != null) {
            throw SimulationFailedException("Transaction simulation failed: ${simulation!!.error}", this)
        }

        // Assemble transaction with simulation results
        builtTransaction = server.prepareTransaction(builtTx, simulation!!)

        return this
    }

    suspend fun sign(transactionSigner: KeyPair? = null, force: Boolean = false): AssembledTransaction<T> {
        if (builtTransaction == null) {
            throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
        }

        if (!force && isReadCall()) {
            throw NoSignatureNeededException(
                "This is a read call. It requires no signature or submitting. Set force=true to sign and submit anyway.",
                this
            )
        }

        if (simulation != null && simulation!!.restorePreamble != null) {
            throw ExpiredStateException(
                "You need to restore contract state before you can invoke this method. " +
                "You can set `restore` to true in order to automatically restore the contract state when needed.",
                this
            )
        }

        val signer = transactionSigner ?: this.transactionSigner
            ?: throw IllegalArgumentException(
                "You must provide a transactionSigner to sign the transaction, either here or in the constructor."
            )

        // Check for non-invoker signatures needed
        val sigsNeeded = needsNonInvokerSigningBy(includeAlreadySigned = false)
            .filter { !it.startsWith("C") } // Filter out contract addresses

        if (sigsNeeded.isNotEmpty()) {
            throw NeedsMoreSignaturesException(
                "Transaction requires signatures from $sigsNeeded. See `needsNonInvokerSigningBy` for details.",
                this
            )
        }

        builtTransaction!!.sign(signer)
        return this
    }

    suspend fun signAndSubmit(transactionSigner: KeyPair? = null, force: Boolean = false): T {
        sign(transactionSigner, force)
        return submit()
    }

    suspend fun signAuthEntries(
        authEntriesSigner: KeyPair,
        validUntilLedgerSequence: Long? = null
    ): AssembledTransaction<T> {
        if (builtTransaction == null) {
            throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
        }

        val validUntil = validUntilLedgerSequence ?: (server.getLatestLedger().sequence + 100L)

        val operation = builtTransaction!!.operations.first()
        if (operation !is InvokeHostFunctionOperation) {
            throw IllegalStateException("Expected InvokeHostFunction operation")
        }

        // Sign matching auth entries
        val updatedAuth = operation.auth.map { entry ->
            if (entry.credentials.discriminant != SorobanCredentialsTypeXdr.SOROBAN_CREDENTIALS_ADDRESS) {
                return@map entry
            }

            val address = entry.credentials.address?.address ?: return@map entry
            val addressStr = Address.fromSCAddress(address).toString()

            if (addressStr != authEntriesSigner.accountId) {
                return@map entry
            }

            // Sign this entry
            Auth.authorizeEntry(entry, authEntriesSigner, validUntil, builtTransaction!!.network)
        }

        // Update operation with signed auth entries
        val updatedOp = operation.copy(auth = updatedAuth)

        // Rebuild transaction with updated operation
        // (This requires cloning the transaction with updated operations)

        return this
    }

    fun needsNonInvokerSigningBy(includeAlreadySigned: Boolean = false): Set<String> {
        if (builtTransaction == null) {
            throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
        }

        val operation = builtTransaction!!.operations.firstOrNull()
        if (operation !is InvokeHostFunctionOperation) {
            return emptySet()
        }

        return operation.auth
            .filter { it.credentials.discriminant == SorobanCredentialsTypeXdr.SOROBAN_CREDENTIALS_ADDRESS }
            .filter { entry ->
                includeAlreadySigned ||
                entry.credentials.address?.signature?.discriminant == SCValTypeXdr.SCV_VOID
            }
            .mapNotNull { entry ->
                entry.credentials.address?.address?.let { Address.fromSCAddress(it).toString() }
            }
            .toSet()
    }

    fun result(): T {
        val simData = getSimulationData()
        val rawResult = SCValXdr.fromXdrBase64(simData.result.xdr!!)

        return if (parseResultXdrFn != null) {
            parseResultXdrFn.invoke(rawResult)
        } else {
            @Suppress("UNCHECKED_CAST")
            rawResult as T
        }
    }

    fun isReadCall(): Boolean {
        val simData = getSimulationData()
        val auths = simData.result.auth ?: emptyList()
        val writes = simData.transactionData.resources.footprint.readWrite
        return auths.isEmpty() && writes.isEmpty()
    }

    suspend fun submit(): T {
        val response = submitInternal()

        // Parse result from transaction meta
        val transactionMeta = TransactionMetaXdr.fromXdrBase64(response.resultMetaXdr!!)
        val resultVal = when {
            transactionMeta.v3 != null -> transactionMeta.v3.sorobanMeta?.returnValue
            transactionMeta.v4 != null -> transactionMeta.v4.sorobanMeta?.returnValue
            else -> throw IllegalStateException("Unexpected transaction meta version")
        } ?: throw IllegalStateException("No return value in transaction meta")

        return if (parseResultXdrFn != null) {
            parseResultXdrFn.invoke(resultVal)
        } else {
            @Suppress("UNCHECKED_CAST")
            resultVal as T
        }
    }

    fun toEnvelopeXdrBase64(): String {
        return builtTransaction?.toEnvelopeXdrBase64()
            ?: throw NotYetSimulatedException("Transaction has not yet been built.", this)
    }

    suspend fun restoreFootprint() {
        if (transactionSigner == null) {
            throw IllegalArgumentException(
                "For automatic restore to work you must provide a transactionSigner when initializing AssembledTransaction."
            )
        }

        val restoreOp = RestoreFootprintOperation()
        val restoreTxBuilder = TransactionBuilder(
            sourceAccount = transactionBuilder.sourceAccount,
            network = transactionBuilder.network
        )
            .setBaseFee(transactionBuilder.baseFee)
            .addOperation(restoreOp)
            .setSorobanData(
                SorobanDataBuilder(simulation!!.restorePreamble!!.transactionData!!).build()
            )
            .setPreconditions(
                TransactionPreconditions(
                    timeBounds = TimeBounds(minTime = 0, maxTime = 0)
                )
            )

        val restoreAssembled = AssembledTransaction<SCValXdr>(
            server = server,
            submitTimeout = submitTimeout,
            transactionSigner = transactionSigner,
            parseResultXdrFn = null,
            transactionBuilder = restoreTxBuilder
        )

        restoreAssembled.simulate(restore = false)
        restoreAssembled.sign(transactionSigner, force = true)
        restoreAssembled.submitInternal()
    }

    private suspend fun submitInternal(): GetTransactionResponse {
        if (builtTransaction == null) {
            throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
        }

        // Send transaction
        if (sendTransactionResponse == null) {
            sendTransactionResponse = server.sendTransaction(builtTransaction!!)
            if (sendTransactionResponse!!.status != SendTransactionResponse.SendTransactionStatus.PENDING) {
                throw SendTransactionFailedException("Sending the transaction to the network failed!", this)
            }
        }

        // Poll for result with exponential backoff
        val txHash = sendTransactionResponse!!.hash!!
        val attempts = withExponentialBackoff(
            timeout = submitTimeout,
            fn = { server.getTransaction(txHash) },
            keepWaitingIf = { it.status == GetTransactionResponse.GetTransactionStatus.NOT_FOUND }
        )

        getTransactionResponse = attempts.last()

        return when (getTransactionResponse!!.status) {
            GetTransactionResponse.GetTransactionStatus.SUCCESS -> getTransactionResponse!!
            GetTransactionResponse.GetTransactionStatus.NOT_FOUND -> throw TransactionStillPendingException(
                "Waited $submitTimeout seconds for transaction to complete, but it did not. " +
                "Returning anyway. You can call result() to await the result later " +
                "or check the status of the transaction manually.",
                this
            )
            GetTransactionResponse.GetTransactionStatus.FAILED -> throw TransactionFailedException(
                "Transaction failed.",
                this
            )
        }
    }

    private fun getSimulationData(): SimulationData {
        if (simulationResult != null && simulationTransactionData != null) {
            return SimulationData(simulationResult!!, simulationTransactionData!!)
        }

        if (simulation == null) {
            throw NotYetSimulatedException("Transaction has not yet been simulated.", this)
        }

        simulationResult = simulation!!.results!!.first()
        simulationTransactionData = SorobanTransactionDataXdr.fromXdrBase64(simulation!!.transactionData!!)

        return SimulationData(simulationResult!!, simulationTransactionData!!)
    }

    private data class SimulationData(
        val result: SimulateTransactionResponse.SimulateHostFunctionResult,
        val transactionData: SorobanTransactionDataXdr
    )
}

// Exponential backoff helper
private suspend fun <T> withExponentialBackoff(
    timeout: Int,
    fn: suspend () -> T,
    keepWaitingIf: (T) -> Boolean
): List<T> {
    val attempts = mutableListOf<T>()
    attempts.add(fn())

    if (!keepWaitingIf(attempts.first())) {
        return attempts
    }

    val startTime = currentTimeMillis()
    val waitUntil = startTime + (timeout * 1000L)
    var waitTime = 1000L
    val maxWaitTime = 60000L

    while (currentTimeMillis() < waitUntil && keepWaitingIf(attempts.last())) {
        delay(waitTime)
        attempts.add(fn())

        waitTime = (waitTime * 2).coerceAtMost(maxWaitTime)
        val remaining = waitUntil - currentTimeMillis()
        if (remaining < waitTime) {
            waitTime = remaining
        }
    }

    return attempts
}

// Platform-specific time function
expect fun currentTimeMillis(): Long
```

### Simulation and Preparation

Key logic in `simulate()`:
1. Fetch latest account sequence number
2. Build transaction
3. Simulate via `SorobanServer.simulateTransaction()`
4. Check for restoration preamble
5. If restore needed and not read-only:
   - Call `restoreFootprint()`
   - Re-simulate with restore=false
6. Check for simulation errors
7. Prepare transaction via `SorobanServer.prepareTransaction()`

### Signing Flow

Transaction signing (`sign()`):
1. Validate transaction is simulated
2. Check if read-only (skip if so, unless forced)
3. Check for expired state (needs restoration)
4. Validate no additional auth signatures needed
5. Sign transaction envelope

Auth entry signing (`signAuthEntries()`):
1. Validate transaction is simulated
2. Get valid-until ledger (default: current + 100)
3. Find matching auth entries for signer
4. Sign each matching entry via `Auth.authorizeEntry()`
5. Update operation with signed entries

### Result Parsing and Conversion

Simulation results (`result()`):
- Parse from `SimulateHostFunctionResult.xdr`
- Convert via `SCValXdr.fromXdrBase64()`
- Apply `parseResultXdrFn` if provided

Execution results (`submit()`):
- Parse from `GetTransactionResponse.resultMetaXdr`
- Extract `TransactionMeta.sorobanMeta.returnValue`
- Apply `parseResultXdrFn` if provided

### Error Handling

All exceptions extend `AssembledTransactionException`:
- Base class stores reference to `AssembledTransaction`
- Enables debugging by accessing transaction state
- Each exception type indicates specific failure mode

Exception hierarchy:
```
AssembledTransactionException (base)
├── NotYetSimulatedException
├── SimulationFailedException
├── RestorationFailureException
├── NoSignatureNeededException
├── NeedsMoreSignaturesException
├── ExpiredStateException
├── SendTransactionFailedException
├── TransactionStillPendingException
└── TransactionFailedException
```

---

## 6. Dependencies

### Existing KMP SDK Classes Required

**Core Classes:**
- ✅ `KeyPair` - Signing operations (already supports suspend)
- ✅ `Network` - Network configuration
- ✅ `Account` - Source account
- ✅ `Transaction` - Transaction representation
- ✅ `TransactionBuilder` - Transaction construction
- ✅ `TimeBounds` - Time preconditions
- ✅ `TransactionPreconditions` - Preconditions

**Operations:**
- ✅ `Operation` - Base operation class
- ✅ `InvokeHostFunctionOperation` - Contract invocation
- ✅ `RestoreFootprintOperation` - Footprint restoration
- ⚠️ Need to add: `InvokeHostFunctionOperation.invokeContractFunction()` builder

**Utilities:**
- ✅ `Address` - Account/contract addresses
- ✅ `Auth` - Authorization signing
- ✅ `Scv` - SCVal utilities
- ✅ `Util` - Hash functions

**RPC:**
- ✅ `SorobanServer` - RPC client
- ✅ `SorobanDataBuilder` - Soroban data builder
- ✅ `SimulateTransactionResponse` - Simulation results
- ✅ `SendTransactionResponse` - Send results
- ✅ `GetTransactionResponse` - Transaction status

**XDR Types:**
- ✅ `SCValXdr` - Smart contract values
- ✅ `SorobanAuthorizationEntryXdr` - Auth entries
- ✅ `SorobanTransactionDataXdr` - Transaction data
- ✅ `TransactionMetaXdr` - Transaction metadata
- ✅ `InvokeHostFunctionOpXdr` - Operation XDR
- ✅ `HostFunctionXdr` - Host function
- ✅ `LedgerKeyXdr` - Ledger keys

### New Dependencies Required

**Helper:**
- Need platform-specific `currentTimeMillis()` function for exponential backoff

---

## 7. File Structure

### Package Structure

```
stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/
├── contract/
│   ├── ContractClient.kt                    (NEW - ~150 lines)
│   ├── AssembledTransaction.kt              (NEW - ~400 lines)
│   └── exception/
│       ├── AssembledTransactionException.kt (NEW - ~20 lines)
│       ├── NotYetSimulatedException.kt      (NEW - ~10 lines)
│       ├── SimulationFailedException.kt     (NEW - ~10 lines)
│       ├── RestorationFailureException.kt   (NEW - ~10 lines)
│       ├── NoSignatureNeededException.kt    (NEW - ~10 lines)
│       ├── NeedsMoreSignaturesException.kt  (NEW - ~10 lines)
│       ├── ExpiredStateException.kt         (NEW - ~10 lines)
│       ├── SendTransactionFailedException.kt (NEW - ~10 lines)
│       ├── TransactionStillPendingException.kt (NEW - ~10 lines)
│       └── TransactionFailedException.kt    (NEW - ~10 lines)
└── Util.kt                                   (UPDATE - add currentTimeMillis)
```

### Test Files

```
stellar-sdk/src/commonTest/kotlin/com/stellar/sdk/
├── contract/
│   ├── ContractClientTest.kt                (NEW - ~200 lines)
│   ├── AssembledTransactionTest.kt          (NEW - ~400 lines)
│   └── ContractIntegrationTest.kt           (NEW - ~300 lines)
```

### Sample Application

```
stellarSample/shared/src/commonMain/kotlin/com/soneso/sample/
└── ContractDemo.kt                           (NEW - ~200 lines)
```

---

## 8. Testing Strategy

### Unit Tests

#### ContractClientTest.kt
- ✅ Constructor with valid parameters
- ✅ Constructor validation (invalid contract ID, URL)
- ✅ Simple invoke creates AssembledTransaction
- ✅ Full invoke with all parameters
- ✅ Default parameter values
- ✅ Close releases resources

#### AssembledTransactionTest.kt
- ✅ Simulate updates sequence number
- ✅ Simulate handles restoration preamble
- ✅ Simulate re-simulates after restore
- ✅ Simulate throws on error
- ✅ Sign requires simulation
- ✅ Sign throws NoSignatureNeededException for read calls
- ✅ Sign throws ExpiredStateException if state expired
- ✅ Sign throws NeedsMoreSignaturesException if auth needed
- ✅ Sign succeeds with valid signer
- ✅ SignAndSubmit combines sign and submit
- ✅ SignAuthEntries signs matching entries
- ✅ SignAuthEntries uses default valid-until
- ✅ NeedsNonInvokerSigningBy filters correctly
- ✅ NeedsNonInvokerSigningBy includes/excludes signed
- ✅ Result parses simulation result
- ✅ Result applies parser function
- ✅ Result returns raw SCVal if no parser
- ✅ IsReadCall detects read-only calls
- ✅ Submit sends and polls transaction
- ✅ Submit throws on failure
- ✅ Submit parses result correctly
- ✅ RestoreFootprint creates restore transaction
- ✅ RestoreFootprint requires signer
- ✅ ToEnvelopeXdrBase64 requires simulation

### Integration Tests

#### ContractIntegrationTest.kt (against testnet)
- ✅ Deploy test contract
- ✅ Invoke read-only function (no signing)
- ✅ Invoke write function (with signing)
- ✅ Multi-signature authorization flow
- ✅ Automatic state restoration
- ✅ Error handling (invalid params, wrong signer)
- ✅ Result parsing with custom types
- ✅ Poll timeout behavior

### Mock Server Tests

For tests that don't require live network:
- Mock `SorobanServer` responses
- Test exponential backoff logic
- Test error conditions
- Test edge cases

---

## 9. Edge Cases

### Simulation Edge Cases
1. **Restoration Loop** - If restoration fails repeatedly
   - Solution: Only re-simulate once after restore

2. **Expired Restoration Preamble** - Preamble becomes invalid before restore
   - Solution: Let restoration fail, user re-invokes

3. **Sequence Number Race** - Account sequence changes during simulation
   - Solution: Re-fetch account before building, transaction will fail with bad sequence

### Signing Edge Cases
1. **Read Call Forced Signing** - User wants to sign/submit read-only call
   - Solution: `force=true` parameter bypasses check

2. **Partial Multi-Sig** - Some auth entries signed, others not
   - Solution: `needsNonInvokerSigningBy()` shows what's missing

3. **Wrong Signer** - KeyPair doesn't match auth entry address
   - Solution: Auth.authorizeEntry() validates and throws

### Submission Edge Cases
1. **Network Timeout** - Transaction never appears
   - Solution: TransactionStillPendingException with transaction hash for manual checking

2. **Transaction Expires** - Transaction invalid by time it's submitted
   - Solution: Use reasonable default timeout (300s), let user configure

3. **Insufficient Fee** - Fee too low for network
   - Solution: Document fee requirements, let simulation estimate

### Result Parsing Edge Cases
1. **Null Return Value** - Contract returns void
   - Solution: Handle null SCVal, return Unit or null

2. **Parser Throws** - Custom parser function fails
   - Solution: Let exception propagate to caller

3. **Wrong Type** - Parser expects different type than returned
   - Solution: Parser validates and throws descriptive error

---

## 10. Platform Considerations

### Platform-Specific Code

#### currentTimeMillis()

**JVM:**
```kotlin
// stellar-sdk/src/jvmMain/kotlin/com/stellar/sdk/Util.kt
actual fun currentTimeMillis(): Long = System.currentTimeMillis()
```

**JS:**
```kotlin
// stellar-sdk/src/jsMain/kotlin/com/stellar/sdk/Util.kt
actual fun currentTimeMillis(): Long = Date.now().toLong()
```

**Native:**
```kotlin
// stellar-sdk/src/nativeMain/kotlin/com/stellar/sdk/Util.kt
import platform.posix.gettimeofday
import platform.posix.timeval
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr

actual fun currentTimeMillis(): Long = memScoped {
    val tv = alloc<timeval>()
    gettimeofday(tv.ptr, null)
    tv.tv_sec * 1000L + tv.tv_usec / 1000L
}
```

### Coroutines

All async methods use `suspend`:
- `ContractClient.invoke()`
- `AssembledTransaction.simulate()`
- `AssembledTransaction.sign()`
- `AssembledTransaction.signAndSubmit()`
- `AssembledTransaction.signAuthEntries()`
- `AssembledTransaction.submit()`
- `AssembledTransaction.restoreFootprint()`

This provides:
- Zero overhead on JVM/Native
- Proper async on JS
- Consistent API across platforms

### No Platform-Specific Logic in Core

All business logic is in `commonMain`:
- Simulation
- Signing
- Authorization
- Result parsing
- Error handling

Only `currentTimeMillis()` is platform-specific.

---

## 11. Estimated Effort

### Breakdown by Task

| Task | Effort | Lines | Priority |
|------|--------|-------|----------|
| **ContractClient** | 0.5 days | ~150 | High |
| ├─ Class structure | 2 hrs | 50 | - |
| ├─ Invoke methods | 2 hrs | 80 | - |
| └─ Tests | 2 hrs | 200 | - |
| **AssembledTransaction** | 2 days | ~400 | High |
| ├─ Class structure | 3 hrs | 80 | - |
| ├─ Simulate logic | 4 hrs | 100 | - |
| ├─ Signing logic | 4 hrs | 100 | - |
| ├─ Submit logic | 4 hrs | 80 | - |
| └─ Tests | 5 hrs | 400 | - |
| **Exception Classes** | 0.5 days | ~100 | Medium |
| ├─ Base exception | 1 hr | 20 | - |
| ├─ 9 specific exceptions | 2 hrs | 90 | - |
| └─ Tests | 1 hr | - | - |
| **Helper Functions** | 0.5 days | ~100 | Medium |
| ├─ Exponential backoff | 2 hrs | 40 | - |
| ├─ Platform time functions | 1 hr | 60 | - |
| └─ Tests | 1 hr | - | - |
| **Operation Builders** | 0.5 days | ~50 | Medium |
| └─ InvokeHostFunctionOperation.invokeContractFunction() | 4 hrs | 50 | - |
| **Integration Tests** | 1 day | ~300 | High |
| ├─ Contract deployment | 2 hrs | - | - |
| ├─ Test contract code | 2 hrs | - | - |
| ├─ Integration scenarios | 4 hrs | 300 | - |
| **Documentation** | 0.5 days | - | Medium |
| ├─ KDoc completion | 2 hrs | - | - |
| ├─ Sample app | 2 hrs | 200 | - |
| └─ Usage guide | 2 hrs | - | - |

**Total: 5.5 days** (with buffer for testing and refinement)

### Dependencies
- All SorobanServer methods complete ✅
- All Auth methods complete ✅
- All Address methods complete ✅
- All Scv methods complete ✅
- All XDR types available ✅

### Risks
- Low - All dependencies are already implemented
- Integration testing may require testnet contract deployment
- Some edge cases may require additional iteration

---

## 12. Usage Examples

### Example 1: Read-Only Query (Balance Check)

```kotlin
suspend fun checkBalance(accountId: String): Long {
    val client = ContractClient(
        contractId = "CABC123...", // Token contract
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    // Invoke balance function (read-only, no signing needed)
    val assembled = client.invoke<Long>(
        functionName = "balance",
        parameters = listOf(Scv.toAddress(accountId)),
        source = accountId,
        signer = null, // No signer needed for read calls
        parseResultXdrFn = { Scv.fromInt128(it).toLong() }
    )

    // Get result from simulation (no submission needed)
    val balance = assembled.result()

    client.close()
    return balance
}
```

### Example 2: Write Call (Token Transfer)

```kotlin
suspend fun transferTokens(
    from: String,
    to: String,
    amount: Long,
    signerKeypair: KeyPair
): Unit {
    val client = ContractClient(
        contractId = "CABC123...",
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    // Build and simulate transaction
    val assembled = client.invoke<Unit>(
        functionName = "transfer",
        parameters = listOf(
            Scv.toAddress(from),
            Scv.toAddress(to),
            Scv.toInt128(amount.toBigInteger())
        ),
        source = from,
        signer = signerKeypair,
        parseResultXdrFn = null // Void return
    )

    // Sign and submit in one step
    assembled.signAndSubmit(signerKeypair)

    client.close()
}
```

### Example 3: Multi-Step Flow with Authorization

```kotlin
suspend fun authorizedTransfer(
    from: String,
    fromKeypair: KeyPair,
    to: String,
    amount: Long,
    invokerKeypair: KeyPair
): TransferResult {
    val client = ContractClient(
        contractId = "CABC123...",
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    // Build transaction
    val assembled = client.invoke<TransferResult>(
        functionName = "transfer_from",
        parameters = listOf(
            Scv.toAddress(from),
            Scv.toAddress(to),
            Scv.toInt128(amount.toBigInteger())
        ),
        source = invokerKeypair.accountId,
        signer = invokerKeypair,
        parseResultXdrFn = { TransferResult.fromXdr(it) }
    )

    // Check who needs to sign authorization entries
    val needsSigning = assembled.needsNonInvokerSigningBy()
    println("Authorization needed from: $needsSigning")

    // Sign authorization entries (from account must authorize)
    assembled.signAuthEntries(
        authEntriesSigner = fromKeypair,
        validUntilLedgerSequence = null // Auto: current + 100
    )

    // Sign transaction envelope (invoker)
    assembled.sign(invokerKeypair)

    // Submit and get result
    val result = assembled.submit()

    client.close()
    return result
}
```

### Example 4: Custom Result Parser

```kotlin
data class TokenInfo(
    val name: String,
    val symbol: String,
    val decimals: Int
) {
    companion object {
        fun fromXdr(scVal: SCValXdr): TokenInfo {
            // Assuming contract returns a map
            val map = Scv.fromMap(scVal)
            return TokenInfo(
                name = Scv.fromString(map["name"]!!),
                symbol = Scv.fromString(map["symbol"]!!),
                decimals = Scv.fromUInt32(map["decimals"]!!).toInt()
            )
        }
    }
}

suspend fun getTokenInfo(contractId: String, accountId: String): TokenInfo {
    val client = ContractClient(
        contractId = contractId,
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    val assembled = client.invoke<TokenInfo>(
        functionName = "get_info",
        parameters = emptyList(),
        source = accountId,
        signer = null,
        parseResultXdrFn = { TokenInfo.fromXdr(it) }
    )

    val info = assembled.result()
    client.close()
    return info
}
```

### Example 5: Automatic State Restoration

```kotlin
suspend fun invokeWithAutoRestore(
    contractId: String,
    accountId: String,
    signerKeypair: KeyPair
): String {
    val client = ContractClient(
        contractId = contractId,
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    // invoke() with simulate=true and restore=true (defaults)
    // Will automatically restore contract state if needed
    val assembled = client.invoke<String>(
        functionName = "get_data",
        parameters = listOf(Scv.toSymbol("key")),
        source = accountId,
        signer = signerKeypair,
        parseResultXdrFn = { Scv.fromString(it) },
        simulate = true,
        restore = true // Automatic restoration
    )

    val result = assembled.signAndSubmit(signerKeypair)
    client.close()
    return result
}
```

### Example 6: Manual Restoration Control

```kotlin
suspend fun manualRestoreExample(
    contractId: String,
    accountId: String,
    signerKeypair: KeyPair
) {
    val client = ContractClient(
        contractId = contractId,
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    // Build without auto-restore
    val assembled = client.invoke<Unit>(
        functionName = "store_data",
        parameters = listOf(
            Scv.toSymbol("key"),
            Scv.toString("value")
        ),
        source = accountId,
        signer = signerKeypair,
        parseResultXdrFn = null,
        simulate = true,
        restore = false // Don't auto-restore
    )

    // Check if restoration is needed
    if (assembled.simulation?.restorePreamble != null) {
        println("Contract state needs restoration")

        // Manually restore
        assembled.restoreFootprint()

        // Re-simulate
        assembled.simulate(restore = false)
    }

    // Continue with normal flow
    assembled.signAndSubmit(signerKeypair)
    client.close()
}
```

### Example 7: Error Handling

```kotlin
suspend fun robustInvoke(
    contractId: String,
    accountId: String,
    signerKeypair: KeyPair
): Result<String> {
    val client = ContractClient(
        contractId = contractId,
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    return try {
        val assembled = client.invoke<String>(
            functionName = "get_value",
            parameters = emptyList(),
            source = accountId,
            signer = signerKeypair,
            parseResultXdrFn = { Scv.fromString(it) }
        )

        val result = assembled.signAndSubmit(signerKeypair)
        Result.success(result)

    } catch (e: SimulationFailedException) {
        println("Simulation failed: ${e.message}")
        println("Simulation error: ${e.assembledTransaction.simulation?.error}")
        Result.failure(e)

    } catch (e: NeedsMoreSignaturesException) {
        println("Missing signatures: ${e.message}")
        val needed = e.assembledTransaction.needsNonInvokerSigningBy()
        println("Need signatures from: $needed")
        Result.failure(e)

    } catch (e: TransactionFailedException) {
        println("Transaction failed: ${e.message}")
        println("Transaction hash: ${e.assembledTransaction.sendTransactionResponse?.hash}")
        Result.failure(e)

    } catch (e: TransactionStillPendingException) {
        println("Transaction still pending: ${e.message}")
        println("Check manually: ${e.assembledTransaction.sendTransactionResponse?.hash}")
        Result.failure(e)

    } catch (e: AssembledTransactionException) {
        println("Contract invocation error: ${e.message}")
        Result.failure(e)

    } finally {
        client.close()
    }
}
```

### Example 8: Read-Only vs Write Detection

```kotlin
suspend fun smartInvoke(
    contractId: String,
    functionName: String,
    parameters: List<SCValXdr>,
    accountId: String,
    signerKeypair: KeyPair?
): SCValXdr {
    val client = ContractClient(
        contractId = contractId,
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    val assembled = client.invoke<SCValXdr>(
        functionName = functionName,
        parameters = parameters,
        source = accountId,
        signer = signerKeypair,
        parseResultXdrFn = null // Raw SCVal
    )

    val result = if (assembled.isReadCall()) {
        println("Detected read-only call - using simulation result")
        assembled.result()
    } else {
        println("Detected write call - signing and submitting")
        assembled.signAndSubmit(signerKeypair!!)
    }

    client.close()
    return result
}
```

### Example 9: Separate Sign and Submit (Multi-Sig Workflow)

```kotlin
suspend fun multiSigWorkflow(
    contractId: String,
    sourceAccount: String,
    signer1: KeyPair,
    signer2: KeyPair
): String {
    val client = ContractClient(
        contractId = contractId,
        rpcUrl = "https://soroban-testnet.stellar.org:443",
        network = Network.TESTNET
    )

    // Build transaction
    val assembled = client.invoke<String>(
        functionName = "execute_multisig",
        parameters = listOf(Scv.toSymbol("action")),
        source = sourceAccount,
        signer = null, // Will sign separately
        parseResultXdrFn = { Scv.fromString(it) }
    )

    // Check authorization requirements
    val needsSigning = assembled.needsNonInvokerSigningBy()
    println("Need authorization from: $needsSigning")

    // Sign authorization entries with first signer
    assembled.signAuthEntries(signer1)

    // Sign authorization entries with second signer if needed
    if (needsSigning.contains(signer2.accountId)) {
        assembled.signAuthEntries(signer2)
    }

    // Sign transaction envelope
    assembled.sign(signer1)

    // Submit
    val result = assembled.submit()

    client.close()
    return result
}
```

### Example 10: Complete Sample App

```kotlin
// stellarSample/shared/src/commonMain/kotlin/com/soneso/sample/ContractDemo.kt

package com.soneso.sample

import com.stellar.sdk.KeyPair
import com.stellar.sdk.Network
import com.stellar.sdk.contract.ContractClient
import com.stellar.sdk.scval.Scv

class ContractDemo {
    suspend fun runDemo() {
        println("=== Soroban Contract Client Demo ===\n")

        // Setup
        val rpcUrl = "https://soroban-testnet.stellar.org:443"
        val network = Network.TESTNET
        val keypair = KeyPair.random()
        val accountId = keypair.accountId

        // Example token contract
        val tokenContractId = "CABC123..." // Replace with real contract

        val client = ContractClient(
            contractId = tokenContractId,
            rpcUrl = rpcUrl,
            network = network
        )

        try {
            // 1. Read-only: Get balance
            println("1. Checking balance...")
            val balanceResult = client.invoke<Long>(
                functionName = "balance",
                parameters = listOf(Scv.toAddress(accountId)),
                source = accountId,
                signer = null,
                parseResultXdrFn = { Scv.fromInt128(it).toLong() }
            )
            val balance = balanceResult.result()
            println("   Balance: $balance\n")

            // 2. Write: Transfer tokens
            println("2. Transferring tokens...")
            val transferResult = client.invoke<Unit>(
                functionName = "transfer",
                parameters = listOf(
                    Scv.toAddress(accountId),
                    Scv.toAddress("GDEF..."), // Recipient
                    Scv.toInt128(100.toBigInteger())
                ),
                source = accountId,
                signer = keypair,
                parseResultXdrFn = null
            )

            if (transferResult.isReadCall()) {
                println("   Read-only operation")
            } else {
                println("   Write operation - signing and submitting...")
                transferResult.signAndSubmit(keypair)
                println("   Transfer successful!\n")
            }

            // 3. Check authorization requirements
            println("3. Checking authorization requirements...")
            val authResult = client.invoke<Unit>(
                functionName = "transfer",
                parameters = listOf(
                    Scv.toAddress(accountId),
                    Scv.toAddress("GDEF..."),
                    Scv.toInt128(50.toBigInteger())
                ),
                source = accountId,
                signer = keypair,
                parseResultXdrFn = null
            )

            val needsSigning = authResult.needsNonInvokerSigningBy()
            if (needsSigning.isEmpty()) {
                println("   No additional authorization needed")
            } else {
                println("   Additional signatures needed from: $needsSigning")
            }

        } catch (e: Exception) {
            println("Error: ${e.message}")
            e.printStackTrace()
        } finally {
            client.close()
            println("\nDemo complete!")
        }
    }
}
```

---

## 13. Implementation Phases

### Phase 1: Exception Classes (Day 1 Morning)
1. Create base `AssembledTransactionException`
2. Create 9 specific exception classes
3. Add basic tests

### Phase 2: Helper Functions (Day 1 Afternoon)
1. Add `currentTimeMillis()` to Util with platform implementations
2. Implement `withExponentialBackoff()` function
3. Add tests for exponential backoff

### Phase 3: Operation Builders (Day 2 Morning)
1. Add `InvokeHostFunctionOperation.invokeContractFunction()` builder
2. Add tests

### Phase 4: AssembledTransaction (Day 2-3)
1. Create class structure and properties
2. Implement `simulate()` method
3. Implement `sign()` and `signAuthEntries()` methods
4. Implement `submit()` and `submitInternal()` methods
5. Implement `result()` and helper methods
6. Implement `restoreFootprint()` method
7. Add comprehensive unit tests

### Phase 5: ContractClient (Day 4 Morning)
1. Create class structure
2. Implement both `invoke()` overloads
3. Implement `close()` method
4. Add unit tests

### Phase 6: Integration Testing (Day 4 Afternoon - Day 5 Morning)
1. Deploy test contract to testnet
2. Write integration test scenarios
3. Test all workflows end-to-end
4. Fix any issues found

### Phase 7: Documentation (Day 5 Afternoon)
1. Complete KDoc for all public APIs
2. Create sample app (ContractDemo.kt)
3. Update README with usage examples
4. Final review and cleanup

---

## 14. Success Criteria

### Functionality
- ✅ ContractClient can invoke contract functions
- ✅ AssembledTransaction manages full lifecycle
- ✅ Simulation works correctly
- ✅ Signing works for both transaction and auth entries
- ✅ Submission polls and returns results
- ✅ Automatic restoration works
- ✅ Read-only calls skip signing/submission
- ✅ Error handling covers all failure modes

### Code Quality
- ✅ Production-ready (no simplified implementations)
- ✅ Comprehensive error handling
- ✅ Full KDoc documentation
- ✅ Matches Java SDK API where applicable
- ✅ Platform-agnostic (works on JVM, JS, Native)
- ✅ Proper use of suspend functions
- ✅ Immutability and thread-safety

### Testing
- ✅ Unit tests for all classes
- ✅ Integration tests against testnet
- ✅ Edge case coverage
- ✅ Error scenario tests
- ✅ 90%+ test coverage

### Documentation
- ✅ Complete KDoc for public APIs
- ✅ Usage examples for common scenarios
- ✅ Sample application demonstrating features
- ✅ Error handling guide

---

## 15. Future Enhancements (Post-MVP)

### Potential Additions
1. **Batch Operations** - Invoke multiple functions in one transaction
2. **Event Monitoring** - Listen for contract events after submission
3. **Result Caching** - Cache simulation results for identical calls
4. **Auto-Retry** - Retry failed transactions with updated fees
5. **Fee Estimation** - Provide detailed fee breakdown
6. **Simulation Preview** - Show resource consumption before signing
7. **Transaction Builder DSL** - Kotlin DSL for fluent transaction building
8. **Contract Bindings Generator** - Generate type-safe contract clients from specs

### Nice-to-Have Features
- Transaction history tracking
- Gas profiling and optimization suggestions
- Multi-network support (switch networks easily)
- Transaction templating (save/load configurations)

---

## Conclusion

This implementation plan provides a complete roadmap for adding ContractClient and AssembledTransaction to the Kotlin Multiplatform Stellar SDK. The design:

1. **Matches Java SDK API** - Ensures compatibility and familiarity
2. **Production-Ready** - No shortcuts or simplified implementations
3. **Platform-Agnostic** - Works on all KMP targets
4. **Developer-Friendly** - Simple API for common cases, powerful API for advanced cases
5. **Fully Tested** - Comprehensive unit and integration tests
6. **Well-Documented** - Complete KDoc and usage examples

**Estimated Timeline:** 5 days
**Complexity:** High
**Dependencies:** All satisfied ✅
**Risk Level:** Low

---

*Generated: October 5, 2025*
*Status: Ready for Implementation*
