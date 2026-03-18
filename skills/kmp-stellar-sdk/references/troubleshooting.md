# Troubleshooting Guide

Error handling patterns, common failures, and debugging techniques for the KMP Stellar SDK.

All code assumes the standard SDK import and a suspend context:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.exceptions.*
```

## Table of Contents

- [Exception Hierarchy](#exception-hierarchy)
- [Horizon HTTP Error Handling](#horizon-http-error-handling)
- [Transaction Failure Debugging](#transaction-failure-debugging)
- [Common Error Patterns and Solutions](#common-error-patterns-and-solutions)
- [Soroban RPC Error Handling](#soroban-rpc-error-handling)
- [ContractClient / AssembledTransaction Exceptions](#contractclient--assembledtransaction-exceptions)
- [Debugging Techniques](#debugging-techniques)
- [Common Mistakes](#common-mistakes)

---

## Exception Hierarchy

### Horizon Exceptions

All Horizon HTTP exceptions extend `NetworkException`:

| Exception | Trigger | Key Properties |
|-----------|---------|----------------|
| `NetworkException` | Base class for all network errors | `code: Int?`, `body: String?` |
| `BadRequestException` | HTTP 4xx client errors (400, 404, etc.) | `code`, `body` (JSON with result codes) |
| `BadResponseException` | HTTP 5xx server errors | `code`, `body` |
| `TooManyRequestsException` | HTTP 429 rate limiting | `code`, `body` |
| `RequestTimeoutException` | HTTP 504 or connection timeout | `code`, `body` |
| `ConnectionErrorException` | DNS failure, connectivity loss | `cause` (original exception) |
| `UnknownResponseException` | Unrecognized status code | `code`, `body` |

Non-network SDK exceptions:

| Exception | Trigger | Key Properties |
|-----------|---------|----------------|
| `SdkException` | Base for SDK validation errors | `message` |
| `AccountRequiresMemoException` | SEP-0029 memo required | `accountId: String`, `operationIndex: Int` |

Input validation exceptions (standard Kotlin, not SDK-specific):

| Exception | Trigger | Key Properties |
|-----------|---------|----------------|
| `IllegalArgumentException` | Invalid account ID, secret seed, or amount format | `message` |

```kotlin
// WRONG: expecting BadRequestException for invalid addresses -- that's for HTTP errors
// CORRECT: invalid addresses throw IllegalArgumentException at construction time, before any network call
try {
    val kp = KeyPair.fromAccountId("NOT_A_VALID_ADDRESS")
} catch (e: IllegalArgumentException) {
    println("Invalid address: ${e.message}")
}
// Also thrown by: KeyPair.fromSecretSeed(), amount parsing (e.g. PaymentOperation with bad amount)
```

### Soroban RPC Exceptions

| Exception | Trigger | Key Properties |
|-----------|---------|----------------|
| `SorobanRpcException` | JSON-RPC error response | `errorCode: Int`, `data: String?` |
| `PrepareTransactionException` | Simulation failed during prepare | `simulationError: String?` |
| `AccountNotFoundException` | Account not found via RPC | `accountId: String` |

### Contract Exceptions (ContractClient / AssembledTransaction)

All extend `ContractException`:

| Exception | Trigger | Key Properties |
|-----------|---------|----------------|
| `ContractException` | Base class | `assembledTransaction: AssembledTransaction<*>?` |
| `SimulationFailedException` | Simulation error | `assembledTransaction` |
| `SendTransactionFailedException` | Network rejected tx | `assembledTransaction` |
| `TransactionFailedException` | Tx executed but failed | `assembledTransaction` |
| `TransactionStillPendingException` | Polling timed out | `assembledTransaction` |
| `ExpiredStateException` | Ledger entries archived | `assembledTransaction` |
| `RestorationFailureException` | Auto-restore failed | `assembledTransaction` |
| `NeedsMoreSignaturesException` | Auth entries need signing | `assembledTransaction` |
| `NoSignatureNeededException` | Read-only call, no sig needed | `assembledTransaction` |
| `NotYetSimulatedException` | Operation called before simulate | `assembledTransaction` |
| `ContractSpecException` | Spec parsing / type conversion | `functionName`, `argumentName`, `entryName` |

---

## Horizon HTTP Error Handling

```kotlin
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.exceptions.*

val server = HorizonServer("https://horizon-testnet.stellar.org")

try {
    val account = server.accounts().account(accountId)
    println("Balance: ${account.balances.first().balance}")
} catch (e: TooManyRequestsException) {
    // HTTP 429 -- rate limited
    println("Rate limited (${e.code}), retry later")
} catch (e: RequestTimeoutException) {
    // HTTP 504 or connection timeout
    println("Timeout: ${e.message}")
} catch (e: BadRequestException) {
    // HTTP 4xx -- includes 404 Not Found
    // WRONG: checking e.code == 404 for "not found" -- 404 throws BadRequestException, NOT a separate exception
    // CORRECT: parse e.code to distinguish 400 vs 404 vs other 4xx
    when (e.code) {
        404 -> println("Account not found: $accountId")
        400 -> println("Bad request: ${e.body}")
        else -> println("Client error ${e.code}: ${e.body}")
    }
} catch (e: BadResponseException) {
    // HTTP 5xx
    println("Server error ${e.code}: ${e.body}")
} catch (e: ConnectionErrorException) {
    // Network connectivity issues
    println("Connection error: ${e.message}")
} catch (e: NetworkException) {
    // Catch-all for any other network error
    println("Network error ${e.code}: ${e.body}")
}
```

### Common HTTP Status Codes

| Status | Meaning | Typical Cause |
|--------|---------|---------------|
| 400 | Bad Request | Malformed transaction, invalid parameters |
| 404 | Not Found | Account/transaction/resource does not exist |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Horizon server issue |
| 504 | Gateway Timeout | Horizon overloaded or transaction took too long |

---

## Transaction Failure Debugging

### Sync Submission (submitTransaction)

The KMP SDK's `HorizonServer.submitTransaction()` throws `BadRequestException` when a transaction fails. The `body` property contains Horizon's JSON error response with result codes.

```kotlin
import com.soneso.stellar.sdk.horizon.exceptions.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray

val server = HorizonServer("https://horizon-testnet.stellar.org")

try {
    // submitTransaction returns TransactionResponse on success, throws on failure
    val response = server.submitTransaction(transaction.toEnvelopeXdrBase64())
    println("Success! Hash: ${response.hash}, ledger: ${response.ledger}")
} catch (e: BadRequestException) {
    // Transaction failed -- parse result codes from the JSON error body
    println("Transaction failed (HTTP ${e.code})")
    parseHorizonError(e.body)
} catch (e: RequestTimeoutException) {
    // HTTP 504 -- transaction may still succeed; poll by hash
    println("Submission timed out: ${e.message}")
} catch (e: ConnectionErrorException) {
    println("Network error: ${e.message}")
}

// Requires kotlinx-serialization-json dependency:
// implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
fun parseHorizonError(body: String?) {
    if (body.isNullOrEmpty()) return
    try {
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(body).jsonObject
        val extras = root["extras"]?.jsonObject ?: return
        val resultCodes = extras["result_codes"]?.jsonObject ?: return

        val txCode = resultCodes["transaction"]?.jsonPrimitive?.content
        println("Transaction error: $txCode")

        val opCodes = resultCodes["operations"]?.jsonArray
        opCodes?.forEachIndexed { i, code ->
            println("  Operation $i: ${code.jsonPrimitive.content}")
        }

        val resultXdr = extras["result_xdr"]?.jsonPrimitive?.content
        println("Result XDR: $resultXdr")
    } catch (e: Exception) {
        println("Raw error body: $body")
    }
}

// Alternative: simple string extraction without kotlinx-serialization-json
fun parseHorizonErrorSimple(body: String?) {
    if (body.isNullOrEmpty()) return
    // Extract transaction result code with a basic regex
    val txCode = Regex(""""transaction"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
    if (txCode != null) println("Transaction error: $txCode")
    else println("Raw error body: $body")
}
```

### Async Submission (submitTransactionAsync)

The async endpoint returns `SubmitTransactionAsyncResponse` with status codes instead of throwing:

```kotlin
import com.soneso.stellar.sdk.horizon.responses.SubmitTransactionAsyncResponse.TransactionStatus

val response = server.submitTransactionAsync(transaction.toEnvelopeXdrBase64())

when (response.txStatus) {
    TransactionStatus.PENDING -> {
        println("Accepted, hash: ${response.hash}")
        // Poll Horizon for final status
    }
    TransactionStatus.DUPLICATE -> {
        println("Already submitted, hash: ${response.hash}")
    }
    TransactionStatus.TRY_AGAIN_LATER -> {
        println("Server busy, retry later")
    }
    TransactionStatus.ERROR -> {
        println("Failed: ${response.errorResultXdr}")
    }
}
```

### Transaction Result Codes Reference

| Code | Cause | Solution |
|------|-------|----------|
| `tx_failed` | One or more operations failed | Check operation result codes |
| `tx_bad_seq` | Sequence number mismatch | Reload account, rebuild transaction |
| `tx_insufficient_fee` | Fee below network minimum | Increase base fee or check fee stats |
| `tx_bad_auth` | Invalid signature or insufficient weight | Verify signer key and threshold weights |
| `tx_no_source_account` | Source account does not exist | Create/fund the account first |
| `tx_too_early` | Current time before minTime bound | Adjust TimeBounds or wait |
| `tx_too_late` | Current time past maxTime bound | Rebuild with new TimeBounds |
| `tx_insufficient_balance` | Account lacks XLM for fee + reserves | Fund the source account |

### Operation Result Codes Reference

| Code | Cause | Solution |
|------|-------|----------|
| `op_underfunded` | Insufficient balance for payment | Check available balance minus reserves |
| `op_no_trust` | Destination has no trustline for asset | Destination must `ChangeTrustOperation` first |
| `op_not_authorized` | Asset requires authorization | Issuer must authorize the trustline |
| `op_line_full` | Destination trustline at limit | Destination must increase trust limit |
| `op_no_destination` | Destination account does not exist | Create account first or verify address |
| `op_low_reserve` | Below minimum XLM reserve | Add XLM to cover base reserve (0.5 XLM per entry) |
| `op_already_exists` | Offer/entry already exists | Use update instead of create |
| `op_no_issuer` | Asset issuer account not found | Verify issuer account ID |
| `op_bad_auth` | Insufficient signature weight for multi-sig | Check signer weights vs operation thresholds |

---

## Common Error Patterns and Solutions

### Insufficient Balance (op_underfunded)

**Cause:** Source account does not have enough XLM (or asset) to cover the payment amount plus reserves.

**Solution:** Check the source balance before building the transaction:

```kotlin
val account = server.accounts().account(accountId)
for (balance in account.balances) {
    if (balance.assetType == "native") {
        println("XLM balance: ${balance.balance}")
    }
}
```

Account minimum balance = `(2 + subentryCount) * 0.5 XLM`. Funds below this minimum cannot be spent.

### Missing Trustline (op_no_trust)

**Cause:** The destination account has no trustline for the asset being sent.

**Solution:** The recipient must establish a trustline before receiving the asset:

```kotlin
val recipientKeyPair = KeyPair.fromSecretSeed("SRECIPIENT...")
val recipientAccount = server.loadAccount(recipientKeyPair.getAccountId())
val asset = AssetTypeCreditAlphaNum4("USD", "GISSUER...")

val tx = TransactionBuilder(recipientAccount, Network.TESTNET)
    .setBaseFee(100)
    .addOperation(ChangeTrustOperation(asset = asset))
    .setTimeout(300)
    .build()

tx.sign(recipientKeyPair)
server.submitTransaction(tx.toEnvelopeXdrBase64())
```

### Account Not Found (404 BadRequestException)

**Cause:** The account does not exist on the network (never funded or merged).

**Solution:** Create the account with `CreateAccountOperation` or fund via Friendbot on testnet:

```kotlin
val newKeyPair = KeyPair.random()
val funded = FriendBot.fundTestnetAccount(newKeyPair.getAccountId())
if (funded) {
    println("Account created and funded with 10,000 XLM")
}
```

### Bad Sequence Number (tx_bad_seq)

**Cause:** The transaction's sequence number does not match the account's current sequence number + 1. Common scenarios:
- Loading an account, submitting a transaction, then building another from the same account object loaded separately
- Two code paths loading the same account and building transactions concurrently

**Key behavior:** `TransactionBuilder.build()` calls `sourceAccount.getIncrementedSequenceNumber()` and then sets the account's sequence to that value. After `build()`, the account object's sequence is incremented internally. This means:

```kotlin
// CORRECT: load account, build() increments sequence internally
val account = server.loadAccount(accountId) // on-chain seq N
val tx = TransactionBuilder(account, Network.TESTNET)
    .setBaseFee(100)
    .addOperation(op)
    .setTimeout(300)
    .build() // tx uses seq N+1, account object now at N+1

// WRONG: manually incrementing -- build() already does this
// account.incrementSequenceNumber() // now N+1
// val tx = TransactionBuilder(account, Network.TESTNET)...build() // uses N+2 -- tx_bad_seq!

// SAFE: building multiple transactions from the SAME account object
// build() auto-advances, so sequential builds work:
val tx1 = TransactionBuilder(account, Network.TESTNET)
    .setBaseFee(100).addOperation(op1).setTimeout(300).build() // uses N+1
val tx2 = TransactionBuilder(account, Network.TESTNET)
    .setBaseFee(100).addOperation(op2).setTimeout(300).build() // uses N+2
// Submit tx1 first, then tx2 -- both succeed in order.
```

### Rate Limiting (429 TooManyRequestsException)

**Solution:** Implement exponential backoff:

```kotlin
import com.soneso.stellar.sdk.horizon.exceptions.TooManyRequestsException
import com.soneso.stellar.sdk.horizon.responses.AccountResponse
import kotlinx.coroutines.delay

suspend fun fetchWithRetry(
    server: HorizonServer,
    accountId: String,
    maxRetries: Int = 3
): AccountResponse {
    for (attempt in 0 until maxRetries) {
        try {
            return server.accounts().account(accountId)
        } catch (e: TooManyRequestsException) {
            if (attempt == maxRetries - 1) throw e
            val waitMs = 2000L * (attempt + 1)
            delay(waitMs)
        }
    }
    throw Exception("Max retries exceeded")
}
```

### SEP-0029 Memo Required (AccountRequiresMemoException)

**Cause:** The destination account has `data_attr "config.memo_required" = "MQ=="` set, meaning it requires a memo on incoming transactions (e.g., exchanges).

**Solution:** Add a memo to the transaction:

```kotlin
try {
    server.submitTransaction(tx.toEnvelopeXdrBase64())
} catch (e: AccountRequiresMemoException) {
    println("Account ${e.accountId} (operation ${e.operationIndex}) requires a memo")
    // Rebuild the transaction with a memo
    val txWithMemo = TransactionBuilder(account, Network.TESTNET)
        .setBaseFee(100)
        .addOperation(op)
        .addMemo(MemoText("exchange-deposit-id"))
        .setTimeout(300)
        .build()
    txWithMemo.sign(keyPair)
    server.submitTransaction(txWithMemo.toEnvelopeXdrBase64())
}
```

To skip this check (e.g., for known contracts):

```kotlin
// WRONG: server.submitTransaction(xdr) -- always checks memo
// CORRECT: pass skipMemoRequiredCheck = true
server.submitTransaction(tx.toEnvelopeXdrBase64(), skipMemoRequiredCheck = true)
```

---

## Soroban RPC Error Handling

Soroban uses `SorobanServer` with JSON-RPC 2.0. Errors appear at multiple levels: RPC-level exceptions (`SorobanRpcException`), simulation errors (`SimulateTransactionResponse.error`), and send/poll status codes.

### Simulation Errors

```kotlin
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.rpc.exception.*
import com.soneso.stellar.sdk.rpc.responses.*

val rpcServer = SorobanServer("https://soroban-testnet.stellar.org:443")

try {
    val simResponse = rpcServer.simulateTransaction(transaction)

    // Check simulation-level error
    if (simResponse.error != null) {
        println("Simulation failed: ${simResponse.error}")
        return
    }

    // Check if entries need restoration before invocation
    if (simResponse.restorePreamble != null) {
        println("Expired entries detected -- restore footprint first")
        // Build and submit a RestoreFootprint transaction before retrying
        // See soroban_contracts.md > Restore Expired Data
        return
    }

    // Simulation succeeded -- use prepareTransaction to apply results
    val prepared = rpcServer.prepareTransaction(transaction, simResponse)
    prepared.sign(keyPair)
    val sendResponse = rpcServer.sendTransaction(prepared)

} catch (e: SorobanRpcException) {
    // JSON-RPC level error from the server
    println("RPC error (${e.errorCode}): ${e.message}")
    e.data?.let { println("Error data: $it") }
}
```

### prepareTransaction Shortcut

`prepareTransaction(transaction)` simulates and applies results in one step. It throws `PrepareTransactionException` on simulation failure:

```kotlin
try {
    val prepared = rpcServer.prepareTransaction(transaction)
    prepared.sign(keyPair)
    val sendResponse = rpcServer.sendTransaction(prepared)
} catch (e: PrepareTransactionException) {
    println("Prepare failed: ${e.message}")
    e.simulationError?.let { println("Simulation error: $it") }
}
```

### SendTransaction Status Codes

```kotlin
import com.soneso.stellar.sdk.rpc.responses.SendTransactionStatus

val sendResponse = rpcServer.sendTransaction(prepared)

when (sendResponse.status) {
    SendTransactionStatus.PENDING -> {
        // Accepted -- poll for final result
        val result = rpcServer.pollTransaction(sendResponse.hash!!)
        when (result.status) {
            GetTransactionStatus.SUCCESS -> println("Success!")
            GetTransactionStatus.FAILED -> println("Failed: ${result.resultXdr}")
            GetTransactionStatus.NOT_FOUND -> println("Still pending after timeout")
        }
    }
    SendTransactionStatus.DUPLICATE -> {
        // Already submitted -- poll with the hash
        println("Duplicate, polling: ${sendResponse.hash}")
    }
    SendTransactionStatus.TRY_AGAIN_LATER -> {
        // Network congestion -- wait and retry
        println("Busy, retry later")
    }
    SendTransactionStatus.ERROR -> {
        // Submission failed
        println("Send error: ${sendResponse.errorResultXdr}")
    }
}
```

### pollTransaction

`pollTransaction` polls `getTransaction` with configurable attempts and sleep:

```kotlin
// Default: 30 attempts, 1 second apart
val result = rpcServer.pollTransaction(txHash)

// Custom: 60 attempts with exponential backoff
val result = rpcServer.pollTransaction(
    hash = txHash,
    maxAttempts = 60,
    sleepStrategy = { attempt -> minOf(1000L * attempt, 10000L) }
)

when (result.status) {
    GetTransactionStatus.SUCCESS -> {
        println("Confirmed in ledger: ${result.ledger}")
        val returnValue = result.getResultValue() // SCValXdr? from contract call
    }
    GetTransactionStatus.FAILED -> {
        println("Transaction failed")
        // Parse result XDR for details
        val txResult = result.parseResultXdr()
    }
    GetTransactionStatus.NOT_FOUND -> {
        println("Transaction not found after all attempts")
    }
}
```

### Soroban-Specific Errors

**Simulation failure** (`SimulateTransactionResponse.error` non-null):
- Contract function does not exist
- Wrong number or type of arguments
- Contract logic reverted (e.g., assertion failed)
- Insufficient authorization

**Expired ledger entries** (`SimulateTransactionResponse.restorePreamble` non-null): Archived state must be restored before invoking. See [Soroban Contracts](./soroban_contracts.md) > Restore Expired Data.

**Resource limits exceeded:** If simulation succeeds but `sendTransaction` returns `ERROR`, add a buffer to `minResourceFee`:

```kotlin
val simResponse = rpcServer.simulateTransaction(transaction)
// Add 15% buffer to resource fee
val bufferedFee = ((simResponse.minResourceFee ?: 0L) * 1.15).toLong()
// Use prepareTransaction with simulation to apply standard fees,
// or manually set sorobanData and fee on the transaction
```

### JSON-RPC Error Codes

| Code | Meaning |
|------|---------|
| `-32700` | Parse error: invalid JSON |
| `-32600` | Invalid request |
| `-32601` | Method not found |
| `-32602` | Invalid params |
| `-32603` | Internal error |
| `-32000` to `-32099` | Server-defined errors |

---

## ContractClient / AssembledTransaction Exceptions

The high-level `ContractClient` and `AssembledTransaction` API throws specific contract exceptions:

```kotlin
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.contract.exception.*

try {
    val client = ContractClient.forContract(contractId, rpcUrl, network)
    val result = client.invoke<Unit>(
        functionName = "transfer",
        arguments = mapOf("from" to sourceAddress, "to" to destAddress, "amount" to 100L),
        source = keyPair.getAccountId(),
        signer = keyPair
    )
    println("Result: $result")
} catch (e: SimulationFailedException) {
    // Contract simulation failed (wrong args, assertion error, etc.)
    println("Simulation failed: ${e.message}")
    val sim = e.assembledTransaction?.simulation
    println("Simulation error: ${sim?.error}")
} catch (e: ExpiredStateException) {
    // Ledger entries expired -- set restore=true on simulate
    println("State expired: ${e.message}")
} catch (e: NeedsMoreSignaturesException) {
    // Auth entries need additional signatures
    println("Needs more signatures: ${e.message}")
    val addresses = e.assembledTransaction?.needsNonInvokerSigningBy()
    println("Addresses that need to sign: $addresses")
} catch (e: SendTransactionFailedException) {
    // Transaction rejected by network
    println("Send failed: ${e.message}")
} catch (e: TransactionFailedException) {
    // Transaction executed but failed
    println("Execution failed: ${e.message}")
} catch (e: TransactionStillPendingException) {
    // Polling timed out -- may still complete
    val hash = e.assembledTransaction?.sendTransactionResponse?.hash
    println("Still pending, poll manually: $hash")
} catch (e: NoSignatureNeededException) {
    // Read-only call, use result directly
    println("Read-only call: ${e.message}")
} catch (e: NotYetSimulatedException) {
    // Called sign/submit before simulate
    println("Must simulate first: ${e.message}")
} catch (e: ContractException) {
    // Catch-all for contract errors
    println("Contract error: ${e.message}")
}
```

---

## Debugging Techniques

1. **Inspect transaction XDR before submission:**
   ```kotlin
   val envelopeXdr = transaction.toEnvelopeXdrBase64()
   println("Envelope XDR: $envelopeXdr")
   // Paste into Stellar Laboratory XDR viewer to inspect contents
   ```

2. **Decode failed transaction results from Horizon error body:**
   ```kotlin
   import com.soneso.stellar.sdk.xdr.TransactionResultXdr
   import com.soneso.stellar.sdk.xdr.fromXdrBase64

   // resultXdrString from Horizon error body extras.result_xdr
   val result = TransactionResultXdr.fromXdrBase64(resultXdrString)
   println("Result code: ${result.result}")
   ```

3. **Extract return value from Soroban transaction:**
   ```kotlin
   val txResponse = rpcServer.pollTransaction(txHash)
   if (txResponse.status == GetTransactionStatus.SUCCESS) {
       val returnValue = txResponse.getResultValue() // SCValXdr?
       val wasmId = txResponse.getWasmId() // String? for upload WASM
       val contractId = txResponse.getCreatedContractId() // String? for deploy
   }
   ```

4. **Check Horizon health:**
   ```kotlin
   val health = server.health().execute()
   if (health.isHealthy) {
       println("Horizon OK: db=${health.databaseConnected}, core=${health.coreUp}, synced=${health.coreSynced}")
   } else {
       println("Horizon unhealthy!")
   }
   ```

5. **Check Soroban RPC health:**
   ```kotlin
   val health = rpcServer.getHealth()
   println("Status: ${health.status}, latest ledger: ${health.latestLedger}")
   ```

6. **Verify network passphrase:** Signing with the wrong network passphrase produces invalid signatures. Confirm `Network.TESTNET` vs `Network.PUBLIC` matches your Horizon/RPC endpoint.

7. **Check transaction status on Horizon after uncertain submission:**
   ```kotlin
   try {
       val txResponse = server.transactions().transaction(txHash)
       println("Confirmed in ledger: ${txResponse.ledger}, successful: ${txResponse.successful}")
   } catch (e: BadRequestException) {
       if (e.code == 404) println("Transaction not found -- may still be pending")
   }
   ```

---

## Common Mistakes

**Wrong network passphrase:** Signing with `Network.TESTNET` for a mainnet transaction produces invalid signatures. Always match the Network to your Horizon/RPC endpoint.

**Stale sequence numbers:** Building multiple transactions for the same account without submitting them sequentially causes `tx_bad_seq`. Always reload the account via `server.loadAccount()` or build from the same account object (which auto-increments).

**Insufficient fee for Soroban:** Soroban transactions require a resource fee from simulation on top of the base fee. Always call `simulateTransaction()` or `prepareTransaction()` first.

**Missing trustline:** Sending non-native assets to an account without a trustline fails with `op_no_trust`. The destination must execute `ChangeTrustOperation` before receiving the asset.

**XLM reserve requirements:** Every subentry (trustline, offer, data entry, signer) requires 0.5 XLM base reserve. Creating entries without sufficient XLM fails with `op_low_reserve`.

**Forgetting to prepare Soroban transactions:** After simulating a Soroban transaction, you must apply the simulation results before signing. Use `rpcServer.prepareTransaction(tx)` which handles this automatically.

**Confusing sync vs async submit:** `submitTransaction()` throws on failure (4xx becomes `BadRequestException`). `submitTransactionAsync()` returns a response object with a `txStatus` field -- check the status instead of catching exceptions.

**Missing `suspend` on crypto calls:** `KeyPair.random()`, `KeyPair.fromSecretSeed()`, `transaction.sign()` are all `suspend` functions. Calling them outside a coroutine context causes a compile error:

```kotlin
// WRONG: calling suspend function outside coroutine
// val kp = KeyPair.random() // compile error in non-suspend context

// CORRECT: inside a coroutine
suspend fun createAccount() {
    val kp = KeyPair.random()
    // ...
}

// CORRECT: in a test
@Test
fun testSomething() = runTest {
    val kp = KeyPair.random()
    // ...
}
```

**Horizon 4xx errors contain useful JSON:** When `BadRequestException` is thrown for a failed transaction, the `body` property contains Horizon's JSON error response with `extras.result_codes`. Always parse it for diagnostic information rather than just printing the exception message.
