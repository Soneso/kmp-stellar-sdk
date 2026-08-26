# Quick Start Guide

Get a Stellar wallet running in 30 minutes! This guide covers the essentials to start using the Stellar KMP SDK.

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.*
```

## What You'll Build

In this quick start, you'll:
- Generate a Stellar keypair (wallet)
- Fund an account on testnet
- Send your first payment transaction

## Installation

Add the SDK to your Gradle project:

```kotlin
// In your module's build.gradle.kts
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.12.0")
}
```

## Platform Requirements

### JVM/Android
- Android: API 24+ (Android 7.0)
- JVM: Java 17+

### iOS
Add libsodium via Swift Package Manager:
- URL: `https://github.com/jedisct1/swift-sodium`
- Product: `Clibsodium`

### JavaScript/Web
No additional setup required.

## Your First KeyPair

Generate a random Stellar wallet:

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Generate a random keypair
    val keypair = KeyPair.random()

    println("Account ID: ${keypair.getAccountId()}")
    println("Secret Seed: ${keypair.getSecretSeed()?.concatToString()}")

    // Example output:
    // Account ID: GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D
    // Secret Seed: SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE
}
```

**Important**: Keep the secret seed safe - it controls your account!

## Creating Accounts

Fund your new account on testnet with free test XLM:

```kotlin
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.Network

suspend fun createTestAccount() {
    // Generate a new keypair
    val keypair = KeyPair.random()

    // Fund it on testnet (10,000 test XLM)
    val success = FriendBot.fundTestnetAccount(keypair.getAccountId())

    if (success) {
        println("Account funded! Address: ${keypair.getAccountId()}")
    }
}
```

## Your First Transaction

Send a payment on the Stellar testnet:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer

suspend fun sendFirstPayment() {
    // Create connection to testnet
    val server = HorizonServer("https://horizon-testnet.stellar.org")

    // Your account (you need the secret seed)
    val senderKeypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")

    // Load current account state from network
    val senderAccount = server.loadAccount(senderKeypair.getAccountId())

    // Build payment transaction
    val transaction = TransactionBuilder(senderAccount, Network.TESTNET)
        .addOperation(
            PaymentOperation(
                destination = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",  // Recipient address
                amount = "10",             // Amount in XLM
                asset = AssetTypeNative       // XLM
            )
        )
        .addMemo(MemoText("My first payment"))
        .setBaseFee(100)
        .setTimeout(180)
        .build()

    // Sign the transaction
    transaction.sign(senderKeypair)

    // Submit to network; a rejected transaction raises BadRequestException
    try {
        val response = server.submitTransaction(transaction.toEnvelopeXdrBase64())
        println("Payment sent! Transaction: ${response.hash}")
    } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
        println("Payment failed: ${e.message}")
    }

    server.close()
}
```

## Complete Example

Here's everything together in a working example:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // 1. Generate two keypairs
    val alice = KeyPair.random()
    val bob = KeyPair.random()

    println("Alice: ${alice.getAccountId()}")
    println("Bob: ${bob.getAccountId()}")

    // 2. Fund both accounts on testnet
    FriendBot.fundTestnetAccount(alice.getAccountId())
    FriendBot.fundTestnetAccount(bob.getAccountId())

    // 3. Send payment from Alice to Bob
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    val aliceAccount = server.loadAccount(alice.getAccountId())

    val payment = TransactionBuilder(aliceAccount, Network.TESTNET)
        .addOperation(
            PaymentOperation(
                destination = bob.getAccountId(),
                amount = "100",
                asset = AssetTypeNative
            )
        )
        .setBaseFee(100)
        .setTimeout(180)
        .build()

    payment.sign(alice)

    // A rejected transaction raises BadRequestException, as above
    try {
        val result = server.submitTransaction(payment.toEnvelopeXdrBase64())
        println("Payment successful: ${result.hash}")
    } catch (e: com.soneso.stellar.sdk.horizon.exceptions.BadRequestException) {
        println("Payment failed: ${e.message}")
    }

    server.close()
}
```

## Next Steps

Congratulations! You've created wallets and sent your first Stellar transaction.

For deeper understanding:
- **[Getting Started Guide](getting-started.md)** - Platform details, error handling, best practices
- **[Demo App](demo-app.md)** - Complete application with 11 features
- **[SDK Usage Examples](sdk-usage-examples.md)** - Full SDK documentation
- **[Architecture](architecture.md)** - Security and design principles

---

**Navigation**: [← Home](README.md) | [Getting Started →](getting-started.md)