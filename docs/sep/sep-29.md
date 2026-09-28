# SEP-29: Account Memo Requirements

SEP-29 lets an account require that incoming payments carry a memo. Exchanges and custodians receive deposits for many customers on one account and use the memo to tell them apart; a deposit without one cannot be credited to the right customer. The SDK runs the check inside `HorizonServer.submitTransaction` and `HorizonServer.submitTransactionAsync`, so a transaction that would pay such an account without a memo is refused before it reaches the network.

**Use Cases**:
- Send payments to exchanges or custodial services without losing funds to a missing memo
- Ask the user for a memo when the destination requires one
- Require memos on the incoming deposits of your own account

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.exceptions.*
```

## Quick Start

When a transaction has no memo and a destination requires one, `submitTransaction` throws `AccountRequiresMemoException` and nothing is submitted. Catch it and rebuild the transaction with a memo:

```kotlin
// senderSecretSeed: the secret seed of your funded testnet account, loaded from secure storage
// destinationId: an existing account with config.memo_required set to 1 (see Setting the Memo Requirement)
suspend fun quickExample(senderSecretSeed: String, destinationId: String) {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    try {
        val sender = KeyPair.fromSecretSeed(senderSecretSeed)
        val payment = PaymentOperation(
            destination = destinationId,
            asset = AssetTypeNative,
            amount = "100.0"
        )

        var senderAccount = server.loadAccount(sender.getAccountId())
        val transaction = TransactionBuilder(senderAccount, Network.TESTNET)
            .addOperation(payment)
            .setBaseFee(100)
            .setTimeout(300)
            .build()
        transaction.sign(sender)

        try {
            server.submitTransaction(transaction.toEnvelopeXdrBase64())
        } catch (e: AccountRequiresMemoException) {
            println("${e.accountId} requires a memo (operation ${e.operationIndex})")

            // build() advanced the local sequence number; reload the account
            senderAccount = server.loadAccount(sender.getAccountId())
            val withMemo = TransactionBuilder(senderAccount, Network.TESTNET)
                .addOperation(payment)
                .addMemo(MemoText("user-123"))
                .setBaseFee(100)
                .setTimeout(300)
                .build()
            withMemo.sign(sender)

            val response = server.submitTransaction(withMemo.toEnvelopeXdrBase64())
            println("Submitted: ${response.hash}")
        }
    } catch (e: NetworkException) {
        println("Horizon request failed: ${e.message}")
    } catch (e: IllegalArgumentException) {
        println("Invalid secret seed or account id: ${e.message}")
    }
}
```

In the catch block, `e.accountId` is `destinationId` and `e.operationIndex` is `0`, the index of the payment.

## How It Works

An account signals the requirement with a data entry named `config.memo_required` whose value is the single character `1` (following the [SEP-18](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0018.md) namespace convention). Horizon returns data entry values base64 encoded, so the check compares the entry with `MQ==`, the encoding of `1`. Any other value does not count.

Both submit methods run the check before they send anything, unless the caller passes `skipMemoRequiredCheck = true`. The check reads the base64 envelope passed to the method:

- A fee bump envelope carries no memo and no operations of its own, so the check reads the memo and the operations of its inner transaction.
- A transaction that carries a memo passes without any request to Horizon. `MemoText`, `MemoId` (including `MemoId(0UL)`), `MemoHash` and `MemoReturn` all count; `MemoNone` counts as no memo.
- The destinations of payment, path payment strict send, path payment strict receive and account merge operations are collected in operation order. No other operation type is checked.
- Multiplexed destinations (M-addresses) are skipped. The muxed id already identifies the customer.
- Each distinct remaining destination is loaded from Horizon once, one request at a time, in the order the operations first name them.
- A destination Horizon does not know (HTTP 404) is skipped, and the network decides the outcome when the transaction is submitted.
- The first destination that requires a memo ends the check with `AccountRequiresMemoException`. No further account is loaded and nothing is submitted.
- An envelope the SDK cannot decode is not checked. It is submitted as is, and Horizon reports the malformed envelope.

`AccountRequiresMemoException` extends `SdkException` and has the message `Destination account requires a memo in the transaction.` It carries two properties:

| Property | Type | Content |
|----------|------|---------|
| `accountId` | `String` | The `G...` account id that requires the memo |
| `operationIndex` | `Int` | Zero-based index, over all operations of the checked transaction, of the first operation that names the account as a non-multiplexed destination |

The checker class itself is internal to the SDK. The submit methods are the only entry point.

## Setting the Memo Requirement

An exchange or custodian sets the data entry on its own receiving account with a `ManageDataOperation`:

```kotlin
// exchangeSecretSeed: the secret seed of your funded receiving account, loaded from secure storage
suspend fun requireMemo(exchangeSecretSeed: String) {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    try {
        val exchange = KeyPair.fromSecretSeed(exchangeSecretSeed)
        val exchangeAccount = server.loadAccount(exchange.getAccountId())
        val transaction = TransactionBuilder(exchangeAccount, Network.TESTNET)
            .addOperation(
                ManageDataOperation(
                    name = "config.memo_required",
                    value = "1".encodeToByteArray()
                )
            )
            .setBaseFee(100)
            .setTimeout(300)
            .build()
        transaction.sign(exchange)

        server.submitTransaction(transaction.toEnvelopeXdrBase64())
    } catch (e: NetworkException) {
        println("Horizon request failed: ${e.message}")
    } catch (e: IllegalArgumentException) {
        println("Invalid secret seed: ${e.message}")
    }
}
```

To remove the requirement, submit the same operation without a value. A `ManageDataOperation` whose `value` is `null` deletes the entry:

```kotlin
ManageDataOperation(name = "config.memo_required")
```

## Multiple Destinations

When a transaction pays several accounts, the exception names the one that requires the memo. Here the first payment goes to a wallet without the entry and the second to an exchange that sets it:

```kotlin
// senderSecretSeed: the secret seed of your funded testnet account, loaded from secure storage
// walletAccountId: an existing account without the config.memo_required entry
// exchangeId: an existing account with config.memo_required set to 1
suspend fun multipleDestinations(senderSecretSeed: String, walletAccountId: String, exchangeId: String) {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    try {
        val sender = KeyPair.fromSecretSeed(senderSecretSeed)
        val senderAccount = server.loadAccount(sender.getAccountId())
        val transaction = TransactionBuilder(senderAccount, Network.TESTNET)
            .addOperation(PaymentOperation(destination = walletAccountId, asset = AssetTypeNative, amount = "10.0"))
            .addOperation(PaymentOperation(destination = exchangeId, asset = AssetTypeNative, amount = "25.0"))
            .setBaseFee(100)
            .setTimeout(300)
            .build()
        transaction.sign(sender)

        try {
            server.submitTransaction(transaction.toEnvelopeXdrBase64())
        } catch (e: AccountRequiresMemoException) {
            println(e.accountId)      // exchangeId
            println(e.operationIndex) // 1, the second payment
        }
    } catch (e: NetworkException) {
        println("Horizon request failed: ${e.message}")
    } catch (e: IllegalArgumentException) {
        println("Invalid secret seed or account id: ${e.message}")
    }
}
```

The index counts every operation of the transaction, including operations that have no destination. Account merge and path payment destinations are checked the same way: an `AccountMergeOperation` to `exchangeId` without a memo is refused with the index of the merge.

## Muxed Destinations

SEP-29 exempts multiplexed destinations. An M-address encodes the customer id, so no memo is needed, even when the base account sets `config.memo_required`:

```kotlin
// senderSecretSeed: the secret seed of your funded testnet account, loaded from secure storage
// exchangeId: an existing account with config.memo_required set to 1
suspend fun muxedDestination(senderSecretSeed: String, exchangeId: String) {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    try {
        val sender = KeyPair.fromSecretSeed(senderSecretSeed)
        val muxed = MuxedAccount(exchangeId, 12345UL)

        val senderAccount = server.loadAccount(sender.getAccountId())
        val transaction = TransactionBuilder(senderAccount, Network.TESTNET)
            .addOperation(PaymentOperation(destination = muxed.address, asset = AssetTypeNative, amount = "100.0"))
            .setBaseFee(100)
            .setTimeout(300)
            .build()
        transaction.sign(sender)

        // The M-address is not looked up; the payment is submitted
        val response = server.submitTransaction(transaction.toEnvelopeXdrBase64())
        println("Submitted: ${response.hash}")
    } catch (e: NetworkException) {
        println("Horizon request failed: ${e.message}")
    } catch (e: IllegalArgumentException) {
        println("Invalid secret seed or account id: ${e.message}")
    }
}
```

The same applies to muxed destinations of path payments and account merges. See [SEP-23](sep-23.md#muxed-accounts-m) for creating and parsing M-addresses.

## Fee Bump Transactions

A fee bump envelope goes through the same `submitTransaction` call, and the check reads its inner transaction. In this example the sender signs the inner payment, a separate fee payer signs the fee bump, and the destination requires a memo:

```kotlin
// senderSecretSeed: the secret seed of your funded testnet account, loaded from secure storage
// feePayerSecretSeed: the secret seed of a second funded testnet account that pays the fee
// destinationId: an existing account with config.memo_required set to 1
suspend fun feeBump(senderSecretSeed: String, feePayerSecretSeed: String, destinationId: String) {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    try {
        val sender = KeyPair.fromSecretSeed(senderSecretSeed)
        val feePayer = KeyPair.fromSecretSeed(feePayerSecretSeed)

        val senderAccount = server.loadAccount(sender.getAccountId())
        val inner = TransactionBuilder(senderAccount, Network.TESTNET)
            .addOperation(PaymentOperation(destination = destinationId, asset = AssetTypeNative, amount = "100.0"))
            .setBaseFee(100)
            .setTimeout(300)
            .build()
        inner.sign(sender)

        val feeBump = FeeBumpTransaction.createWithBaseFee(
            feeSource = feePayer.getAccountId(),
            baseFee = 200,
            innerTransaction = inner
        )
        feeBump.sign(feePayer)

        try {
            server.submitTransaction(feeBump.toEnvelopeXdrBase64())
        } catch (e: AccountRequiresMemoException) {
            // The inner payment has no memo; nothing was submitted
            println("${e.accountId} requires a memo (inner operation ${e.operationIndex})")
        }
    } catch (e: NetworkException) {
        println("Horizon request failed: ${e.message}")
    } catch (e: IllegalArgumentException) {
        println("Invalid secret seed or account id: ${e.message}")
    }
}
```

The memo belongs to the inner transaction, so the fee payer cannot add one. The sender has to rebuild and sign the inner transaction with a memo, and the fee payer then wraps and signs it again.

## Skipping the Check

Pass `skipMemoRequiredCheck = true` to submit without the check. No destination account is loaded. The network does not enforce SEP-29, so this payment to an account that requires a memo succeeds:

```kotlin
// senderSecretSeed: the secret seed of your funded testnet account, loaded from secure storage
// destinationId: an existing account with config.memo_required set to 1
suspend fun skipCheck(senderSecretSeed: String, destinationId: String) {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    try {
        val sender = KeyPair.fromSecretSeed(senderSecretSeed)
        val senderAccount = server.loadAccount(sender.getAccountId())
        val transaction = TransactionBuilder(senderAccount, Network.TESTNET)
            .addOperation(PaymentOperation(destination = destinationId, asset = AssetTypeNative, amount = "100.0"))
            .setBaseFee(100)
            .setTimeout(300)
            .build()
        transaction.sign(sender)

        server.submitTransaction(transaction.toEnvelopeXdrBase64(), skipMemoRequiredCheck = true)
    } catch (e: NetworkException) {
        println("Horizon request failed: ${e.message}")
    } catch (e: IllegalArgumentException) {
        println("Invalid secret seed or account id: ${e.message}")
    }
}
```

`submitTransactionAsync` takes the same parameter. Skip the check only when the destinations are accounts you control or when the memo requirement has been handled another way. A transaction that carries a memo causes no lookup, so there is nothing to save by skipping the check for it.

## Error Handling

A destination Horizon does not know passes the check. The submission then reaches Horizon, and the network rejects a payment to a missing account:

```kotlin
// senderSecretSeed: the secret seed of your funded testnet account, loaded from secure storage
suspend fun missingDestination(senderSecretSeed: String) {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    // A valid account id that does not exist on the network
    val destinationId = KeyPair.random().getAccountId()

    try {
        val sender = KeyPair.fromSecretSeed(senderSecretSeed)
        val senderAccount = server.loadAccount(sender.getAccountId())
        val transaction = TransactionBuilder(senderAccount, Network.TESTNET)
            .addOperation(PaymentOperation(destination = destinationId, asset = AssetTypeNative, amount = "10.0"))
            .setBaseFee(100)
            .setTimeout(300)
            .build()
        transaction.sign(sender)

        try {
            server.submitTransaction(transaction.toEnvelopeXdrBase64())
        } catch (e: BadRequestException) {
            // Horizon rejects the transaction; the body holds op_no_destination
            println("${e.code}: ${e.body}")
        }
    } catch (e: NetworkException) {
        println("Horizon request failed: ${e.message}")
    } catch (e: IllegalArgumentException) {
        println("Invalid secret seed: ${e.message}")
    }
}
```

A missing destination usually fails the operation (`op_no_destination` for a payment or path payment, `op_no_account` for an account merge). An earlier operation of the same transaction can create the account, though, so the check leaves that decision to the network.

A destination lookup that fails for any other reason stops the check, and the submit method throws that error without submitting. The error is the `NetworkException` subclass of the lookup: `TooManyRequestsException` when Horizon rate limits the request, `BadRequestException` for another 4xx status, `BadResponseException` for a 5xx status, `UnknownResponseException` for a status outside the 2xx, 4xx and 5xx ranges, or `ConnectionErrorException` for a transport failure.

The check validates memo presence, not memo type. SEP-29 leaves the memo type to the recipient, so any memo passes.

## API Reference

| Member | Description |
|--------|-------------|
| `HorizonServer.submitTransaction(transactionEnvelopeXdr: String)` | Runs the check, then submits and waits for the result |
| `HorizonServer.submitTransaction(transactionEnvelopeXdr: String, skipMemoRequiredCheck: Boolean)` | Runs the check unless `skipMemoRequiredCheck` is `true`, then submits |
| `HorizonServer.submitTransactionAsync(transactionEnvelopeXdr: String)` | Runs the check, then submits without waiting for ledger inclusion |
| `HorizonServer.submitTransactionAsync(transactionEnvelopeXdr: String, skipMemoRequiredCheck: Boolean)` | Runs the check unless `skipMemoRequiredCheck` is `true`, then submits without waiting |
| `AccountRequiresMemoException.accountId` | Account id that requires the memo |
| `AccountRequiresMemoException.operationIndex` | Zero-based index of the first operation naming that account |

All four submit methods are `suspend` functions and accept the base64 envelope of a `Transaction` or a `FeeBumpTransaction`, as returned by `toEnvelopeXdrBase64()`. See [Transaction Submission](../sdk-usage-examples.md#transaction-submission) for the difference between synchronous and asynchronous submission.

## Related Specifications

- [SEP-10: Web Authentication](sep-10.md) - Authentication with the services that often require memos
- [SEP-23: Strkey Encoding](sep-23.md) - Muxed accounts (M-addresses), which the check skips
- [SEP-24: Interactive Deposit and Withdrawal](sep-24.md) - Anchors that hand out deposit memos

**Specification**: [SEP-29: Account Memo Requirements](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0029.md)

**Implementation**: `com.soneso.stellar.sdk.horizon.HorizonServer`, `com.soneso.stellar.sdk.horizon.exceptions.AccountRequiresMemoException`

**Last Updated**: 2026-09-28
