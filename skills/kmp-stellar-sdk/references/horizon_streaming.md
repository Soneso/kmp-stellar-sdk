# Horizon API - Streaming

Complete guide to Horizon streaming (Server-Sent Events) with the KMP Stellar SDK.

All code examples assume these imports and run inside a `suspend` context (coroutine):
```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.*
import com.soneso.stellar.sdk.horizon.requests.EventListener
import com.soneso.stellar.sdk.horizon.requests.SSEStream
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse
import com.soneso.stellar.sdk.horizon.responses.operations.*
```

## Table of Contents

- [Overview](#overview)
- [Basic Streaming Pattern](#basic-streaming-pattern)
- [Streamable Resources](#streamable-resources)
- [Cursor Management](#cursor-management)
- [Reconnection Behavior](#reconnection-behavior)
- [Error Handling](#error-handling)
- [Resource Cleanup](#resource-cleanup)

## Overview

Horizon supports real-time updates via Server-Sent Events (SSE). The SDK wraps SSE connections in the `SSEStream` class, which manages connection lifecycle, automatic reconnection with a configurable timeout (default 15 seconds), and cursor tracking. Events are delivered through the `EventListener<T>` callback interface.

## Basic Streaming Pattern

Every streaming-capable request builder inherits a `stream()` method from `RequestBuilder`. It requires a `serializer` and an `EventListener<T>`. Use `cursor("now")` to receive only new events. Always store the returned `SSEStream` to `close()` later.

```kotlin
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.PaymentOperationResponse
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

val server = HorizonServer("https://horizon-testnet.stellar.org")


// WRONG: server.payments().forAccount(accountId).cursor("now").stream().listen { ... }
//   -- Dart-style API. This SDK does NOT return a Dart Stream.
// CORRECT: .stream(serializer, listener) -- callback-based via EventListener interface

val stream: SSEStream<OperationResponse> = server.payments()
    .forAccount(accountId)
    .cursor("now")
    .stream(
        serializer = OperationResponse.serializer(),
        listener = object : EventListener<OperationResponse> {
            override fun onEvent(event: OperationResponse) {
                if (event is PaymentOperationResponse) {
                    println("Payment: ${event.amount} ${event.assetCode ?: "XLM"} from ${event.from}")
                }
            }
            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: ${error?.message}")
            }
        }
    )

// Close when done to release resources
// stream.close()
```

Key differences from other SDKs:
- `stream()` takes a **kotlinx.serialization** `KSerializer<T>` as first argument -- use `ResponseType.serializer()` (e.g., `TransactionResponse.serializer()`)
- Events are delivered via the `EventListener<T>` interface (`onEvent` + `onFailure`), not a Dart/Rx stream
- `stream()` returns an `SSEStream<T>` -- call `.close()` to stop it (not `.cancel()`)

## Streamable Resources

All resources follow the same pattern: `server.<resource>()[.forAccount(id)].cursor("now").stream(serializer, listener)`.

| Builder | Serializer | Response Type | Supports `forAccount()` |
|---------|-----------|--------------|------------------------|
| `server.ledgers()` | `LedgerResponse.serializer()` | `LedgerResponse` | No |
| `server.transactions()` | `TransactionResponse.serializer()` | `TransactionResponse` | Yes |
| `server.operations()` | `OperationResponse.serializer()` | `OperationResponse` | Yes |
| `server.payments()` | `OperationResponse.serializer()` | `OperationResponse` (type-check subclasses) | Yes |
| `server.effects()` | `EffectResponse.serializer()` | `EffectResponse` | Yes |
| `server.offers()` | `OfferResponse.serializer()` | `OfferResponse` | Yes (via `forSeller()` or `forAccount()`) |
| `server.trades()` | `TradeResponse.serializer()` | `TradeResponse` | Yes |

**Not streamable:** `server.orderBook()` -- the order book endpoint does not support cursor pagination or streaming. Calling `cursor()` on `OrderBookRequestBuilder` throws `UnsupportedOperationException`.

### Stream Transactions (network-wide and per-account)

```kotlin
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

val server = HorizonServer("https://horizon-testnet.stellar.org")

// All new transactions on the network
val networkStream: SSEStream<TransactionResponse> = server.transactions()
    .cursor("now")
    .stream(
        serializer = TransactionResponse.serializer(),
        listener = object : EventListener<TransactionResponse> {
            override fun onEvent(event: TransactionResponse) {
                println("TX ${event.hash}: ${event.operationCount} ops")
            }
            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: ${error?.message}")
            }
        }
    )

// Transactions for a specific account
val accountStream: SSEStream<TransactionResponse> = server.transactions()
    .forAccount(accountId)
    .cursor("now")
    .stream(
        serializer = TransactionResponse.serializer(),
        listener = object : EventListener<TransactionResponse> {
            override fun onEvent(event: TransactionResponse) {
                println("Account TX: ${event.hash}")
            }
            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: ${error?.message}")
            }
        }
    )
```

### Stream Ledgers

```kotlin
import com.soneso.stellar.sdk.horizon.responses.LedgerResponse

val server = HorizonServer("https://horizon-testnet.stellar.org")

val stream: SSEStream<LedgerResponse> = server.ledgers()
    .cursor("now")
    .stream(
        serializer = LedgerResponse.serializer(),
        listener = object : EventListener<LedgerResponse> {
            override fun onEvent(event: LedgerResponse) {
                println("Ledger ${event.sequence}: ${event.successfulTransactionCount} successful txs")
            }
            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: ${error?.message}")
            }
        }
    )
```

### Stream Effects

```kotlin
import com.soneso.stellar.sdk.horizon.responses.effects.EffectResponse
import com.soneso.stellar.sdk.horizon.responses.effects.AccountCreditedEffectResponse
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

val server = HorizonServer("https://horizon-testnet.stellar.org")

val stream: SSEStream<EffectResponse> = server.effects()
    .forAccount(accountId)
    .cursor("now")
    .stream(
        serializer = EffectResponse.serializer(),
        listener = object : EventListener<EffectResponse> {
            override fun onEvent(event: EffectResponse) {
                when (event) {
                    is AccountCreditedEffectResponse ->
                        println("Credited: ${event.amount} ${event.assetCode ?: "XLM"}")
                    else ->
                        println("Effect: ${event.type}")
                }
            }
            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: ${error?.message}")
            }
        }
    )
```

### Stream Trades

```kotlin
import com.soneso.stellar.sdk.horizon.responses.TradeResponse
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"

val server = HorizonServer("https://horizon-testnet.stellar.org")

val stream: SSEStream<TradeResponse> = server.trades()
    .forAccount(accountId)
    .cursor("now")
    .stream(
        serializer = TradeResponse.serializer(),
        listener = object : EventListener<TradeResponse> {
            override fun onEvent(event: TradeResponse) {
                println("Trade: ${event.baseAmount} -> ${event.counterAmount}")
            }
            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Stream error: ${error?.message}")
            }
        }
    )
```

## Cursor Management

Use `cursor("now")` for real-time events, or a saved paging token to resume from a known position:

```kotlin
// accountId: from the previous steps of this flow
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.PaymentOperationResponse

val server = HorizonServer("https://horizon-testnet.stellar.org")
var lastCursor = "now" // or load from persistent storage
var stream: SSEStream<OperationResponse>? = null

fun startPaymentStream() {
    stream = server.payments()
        .forAccount(accountId)
        .cursor(lastCursor)
        .stream(
            serializer = OperationResponse.serializer(),
            listener = object : EventListener<OperationResponse> {
                override fun onEvent(event: OperationResponse) {
                    lastCursor = event.pagingToken
                    // Persist lastCursor to storage for crash recovery
                    if (event is PaymentOperationResponse) {
                        println("Payment: ${event.amount}")
                    }
                }
                override fun onFailure(error: Throwable?, responseCode: Int?) {
                    println("Stream error: ${error?.message}")
                    // The SDK reconnects automatically -- no manual retry needed
                }
            }
        )
}

fun stopPaymentStream() {
    stream?.close()
    stream = null
}
```

The `SSEStream` tracks the cursor internally via `lastPagingToken`. When the stream auto-reconnects, it uses the latest cursor automatically -- you do not need to manually manage the cursor for reconnection purposes. However, persisting the cursor externally is still recommended for crash recovery across app restarts.

## Reconnection Behavior

The `SSEStream` handles reconnection automatically through a monitor coroutine:

1. A monitor job checks connection health every 200ms.
2. If no event is received within the `reconnectTimeout` (default: 15 seconds), the connection is considered stale and a new SSE request is created.
3. On network errors (IOException, SocketException), the stream marks itself as closed and the monitor triggers reconnection.
4. On reconnect, the `Last-Event-ID` header is sent for server-side cursor tracking.
5. System messages (`"hello"` and `"byebye"`) are filtered out automatically.

You do not need to implement manual reconnection. The SDK reconnects transparently.

### Custom Reconnect Timeout

```kotlin
import kotlin.time.Duration.Companion.seconds
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val server = HorizonServer("https://horizon-testnet.stellar.org")

val stream: SSEStream<TransactionResponse> = server.transactions()
    .forAccount(accountId)
    .cursor("now")
    .stream(
        serializer = TransactionResponse.serializer(),
        listener = object : EventListener<TransactionResponse> {
            override fun onEvent(event: TransactionResponse) {
                println("TX: ${event.hash}")
            }
            override fun onFailure(error: Throwable?, responseCode: Int?) {
                println("Error: ${error?.message}")
            }
        },
        reconnectTimeout = 30.seconds // default is 15.seconds (SSEStream.DEFAULT_RECONNECT_TIMEOUT)
    )
```

## Error Handling

Stream errors are delivered through the `onFailure` callback. Common errors:

- **HTTP errors** -- non-2xx status from Horizon (responseCode is set, error contains the HTTP body)
- **Parse errors** -- malformed event data (stream auto-reconnects)
- **Network errors** -- transient connection failures (auto-reconnected by the monitor)

```kotlin
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.PaymentOperationResponse
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
val server = HorizonServer("https://horizon-testnet.stellar.org")

val stream: SSEStream<OperationResponse> = server.payments()
    .forAccount(accountId)
    .cursor("now")
    .stream(
        serializer = OperationResponse.serializer(),
        listener = object : EventListener<OperationResponse> {
            override fun onEvent(event: OperationResponse) {
                if (event is PaymentOperationResponse) {
                    println("Payment: ${event.amount}")
                }
            }
            override fun onFailure(error: Throwable?, responseCode: Int?) {
                if (responseCode != null) {
                    println("HTTP error $responseCode: ${error?.message}")
                } else {
                    println("Stream error: ${error?.message}")
                }
                // Network errors trigger auto-reconnect -- no manual action needed
            }
        }
    )
```

## Resource Cleanup

Always close streams when no longer needed. The `SSEStream.close()` method cancels internal coroutines and releases all resources. After calling `close()`, the stream cannot be restarted -- create a new one instead.

```kotlin
// WRONG: stream.cancel() -- no cancel() method exists
// CORRECT: stream.close() -- stops the stream and releases resources

stream.close()
```

### Android ViewModel Pattern

```kotlin
import androidx.lifecycle.ViewModel
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.EventListener
import com.soneso.stellar.sdk.horizon.requests.SSEStream
import com.soneso.stellar.sdk.horizon.responses.operations.OperationResponse
import com.soneso.stellar.sdk.horizon.responses.operations.PaymentOperationResponse

class PaymentViewModel(private val accountId: String) : ViewModel() {
    private val server = HorizonServer("https://horizon-testnet.stellar.org")
    private var stream: SSEStream<OperationResponse>? = null

    fun startStreaming() {
        stream = server.payments()
            .forAccount(accountId)
            .cursor("now")
            .stream(
                serializer = OperationResponse.serializer(),
                listener = object : EventListener<OperationResponse> {
                    override fun onEvent(event: OperationResponse) {
                        if (event is PaymentOperationResponse) {
                            // Update UI state (post to main thread if needed)
                        }
                    }
                    override fun onFailure(error: Throwable?, responseCode: Int?) {
                        // Log error; stream auto-reconnects for network issues
                    }
                }
            )
    }

    override fun onCleared() {
        super.onCleared()
        stream?.close()
        server.close()
    }
}
```

### Coroutine Scope Pattern (KMP)

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.requests.EventListener
import com.soneso.stellar.sdk.horizon.requests.SSEStream
import com.soneso.stellar.sdk.horizon.responses.TransactionResponse

class TransactionMonitor(private val accountId: String) {
    private val server = HorizonServer("https://horizon-testnet.stellar.org")
    private var stream: SSEStream<TransactionResponse>? = null

    fun start() {
        stream = server.transactions()
            .forAccount(accountId)
            .cursor("now")
            .stream(
                serializer = TransactionResponse.serializer(),
                listener = object : EventListener<TransactionResponse> {
                    override fun onEvent(event: TransactionResponse) {
                        println("TX ${event.hash}: ${event.operationCount} ops")
                    }
                    override fun onFailure(error: Throwable?, responseCode: Int?) {
                        println("Stream error: ${error?.message}")
                    }
                }
            )
    }

    fun stop() {
        stream?.close()
        stream = null
        server.close()
    }
}
```
