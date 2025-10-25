# Getting Started Guide

This guide will help you get up and running with the Stellar KMP SDK on your platform of choice.

## Table of Contents

- [Installation](#installation)
  - [Gradle Setup](#gradle-setup)
  - [Platform-Specific Requirements](#platform-specific-requirements)
- [Basic Concepts](#basic-concepts)
- [Your First KeyPair](#your-first-keypair)
- [Creating Accounts](#creating-accounts)
- [Building Your First Transaction](#building-your-first-transaction)
- [Connecting to Stellar Networks](#connecting-to-stellar-networks)
- [Next Steps](#next-steps)

## Installation

### Gradle Setup

Add the SDK as a Maven dependency (recommended for most projects):

```kotlin
// In your module's build.gradle.kts
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:0.2.1")
}
```

**Alternative: For advanced native Swift interop** (only needed for native iOS/macOS apps where Swift directly uses SDK types):

```kotlin
// settings.gradle.kts
includeBuild("/path/to/kmp-stellar-sdk")

// In your module's build.gradle.kts
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:0.2.1")
}
```

See the [platform guides](platforms/) for when you need Git submodule setup vs Maven artifacts.

### Platform-Specific Requirements

#### JVM/Android

No additional setup required. BouncyCastle is included as a transitive dependency.

**Android Minimum SDK**: API 24 (Android 7.0)

```kotlin
android {
    defaultConfig {
        minSdk = 24
    }
}
```

#### iOS

**Two options available:**

**Option A: Compose Multiplatform iOS (Recommended - 95% of cases)**
- Maven artifact works perfectly
- UI in Kotlin (Compose)
- Swift code only launches the app
- Still requires libsodium via Swift Package Manager

**Option B: Native SwiftUI/UIKit (Advanced)**
- For apps where Swift directly accesses SDK types
- Requires Git submodule + project dependencies
- See [iOS Platform Guide](platforms/ios.md) for details

**Swift Package Manager Setup (required for both options):**

Add libsodium via Swift Package Manager in Xcode:

1. File → Add Package Dependencies
2. Search for: `https://github.com/jedisct1/swift-sodium`
3. Add the **swift-sodium** package
4. When prompted, select the **Clibsodium** product (not Sodium)

Or add to your `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/jedisct1/swift-sodium", from: "0.9.1")
],
targets: [
    .target(
        name: "YourApp",
        dependencies: [
            .product(name: "Clibsodium", package: "swift-sodium")
        ]
    )
]
```

#### macOS

**Two options available:**

**Option 1: Desktop App (Compose) - Recommended**
- No additional setup required
- Uses BouncyCastle (JVM) for cryptography
- Cross-platform (Windows/Linux/macOS)
- Same Compose UI as Android/iOS/Web

```kotlin
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:0.2.1")
}
```

**Option 2: Native SwiftUI App - Advanced**
- Requires Homebrew libsodium:
  ```bash
  brew install libsodium
  ```
- Only needed when Swift code directly uses SDK types
- Requires Git submodule + project dependencies
- See [macOS Platform Guide](platforms/macos.md) for details

#### JavaScript/Web

No additional setup required. The SDK bundles libsodium-wrappers-sumo automatically (sumo build includes SHA-256 support required for transaction hashing).

For Kotlin/JS projects:

```kotlin
kotlin {
    js {
        browser {
            // libsodium.js is bundled automatically
        }
        nodejs {
            // libsodium.js is included via npm
        }
    }
}
```

## Basic Concepts

### Networks

Stellar has two main networks:

- **Testnet**: For development and testing (free test XLM available)
- **Mainnet**: The production network with real assets

```kotlin
import com.soneso.stellar.sdk.Network

// Use testnet for development
val network = Network.TESTNET

// Use mainnet for production
val network = Network.PUBLIC
```

### Accounts and KeyPairs

Every Stellar account has:
- **Account ID** (public key): Starts with 'G', safe to share
- **Secret Seed** (private key): Starts with 'S', keep secret!

### Async Operations

All cryptographic operations in the SDK use Kotlin's `suspend` functions:

```kotlin
// Always use in a coroutine context
suspend fun example() {
    val keypair = KeyPair.random()
    val signature = keypair.sign(data)
}

// Or with runBlocking for simple scripts
fun main() = runBlocking {
    val keypair = KeyPair.random()
}
```

## Your First KeyPair

### Generate a Random KeyPair

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Generate a random keypair
    val keypair = KeyPair.random()

    println("Account ID: ${keypair.getAccountId()}")
    println("Secret Seed: ${keypair.getSecretSeed()?.concatToString()}")
    println("Can Sign: ${keypair.canSign()}")

    // Example output:
    // Account ID: GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D
    // Secret Seed: SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE
    // Can Sign: true
}
```

### Import Existing KeyPair

```kotlin
suspend fun importKeypair() {
    // From secret seed (can sign)
    val fullKeypair = KeyPair.fromSecretSeed(
        "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
    )

    // From account ID only (cannot sign, can verify)
    val publicKeypair = KeyPair.fromAccountId(
        "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
    )

    println("Full keypair can sign: ${fullKeypair.canSign()}")  // true
    println("Public keypair can sign: ${publicKeypair.canSign()}")  // false
}
```

### Secure Seed Handling

```kotlin
suspend fun secureHandling() {
    // Use CharArray for better security (can be zeroed)
    val seedChars = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE".toCharArray()

    try {
        val keypair = KeyPair.fromSecretSeed(seedChars)
        // Use the keypair...
    } finally {
        // Zero the seed from memory
        seedChars.fill('\u0000')
    }
}
```

## Creating Accounts

Stellar accounts must be created with a minimum balance (currently 1 XLM on mainnet).

### Testnet: Using Friendbot

```kotlin
import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.Network

suspend fun createTestAccount() {
    // Generate a new keypair
    val keypair = KeyPair.random()
    println("New account: ${keypair.getAccountId()}")

    // Fund it on testnet (10,000 test XLM)
    val success = FriendBot.fundAccount(
        accountId = keypair.getAccountId(),
        network = Network.TESTNET
    )

    if (success) {
        println("Account funded successfully!")
        // Account is now active on testnet
    } else {
        println("Failed to fund account")
    }
}
```

### Mainnet: Create Account Operation

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer

suspend fun createMainnetAccount(
    sourceKeypair: KeyPair,  // Existing funded account
    newKeypair: KeyPair      // New account to create
) {
    val server = HorizonServer("https://horizon.stellar.org")

    // Load source account
    val sourceAccount = server.loadAccount(sourceKeypair.getAccountId())

    // Build transaction
    val transaction = TransactionBuilder(sourceAccount, Network.PUBLIC)
        .addOperation(
            CreateAccountOperation(
                destination = newKeypair.getAccountId(),
                startingBalance = "1.5"  // 1.5 XLM (minimum is 1 XLM)
            )
        )
        .setBaseFee(100)  // 100 stroops
        .setTimeout(180)   // 3 minutes
        .build()

    // Sign with source account
    transaction.sign(sourceKeypair)

    // Submit to network
    val response = server.submitTransaction(transaction)
    println("Transaction hash: ${response.hash}")
}
```

## Building Your First Transaction

### Simple Payment

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer

suspend fun sendPayment() {
    val server = HorizonServer("https://horizon-testnet.stellar.org")

    // Your account (sender)
    val sourceKeypair = KeyPair.fromSecretSeed("SXXX...")

    // Load current account state
    val sourceAccount = server.loadAccount(sourceKeypair.getAccountId())

    // Build payment transaction
    val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
        .addOperation(
            PaymentOperation(
                destination = "GYYY...",  // Recipient
                amount = "10",             // Amount
                asset = Asset.NATIVE       // XLM
            )
        )
        .addMemo(Memo.text("Pizza payment"))
        .setBaseFee(100)
        .setTimeout(180)
        .build()

    // Sign the transaction
    transaction.sign(sourceKeypair)

    // Submit to network
    val response = server.submitTransaction(transaction)

    when {
        response.isSuccess -> {
            println("Payment successful!")
            println("Transaction: ${response.hash}")
        }
        else -> {
            println("Payment failed: ${response.extras?.resultCodes}")
        }
    }
}
```

### Multiple Operations

```kotlin
suspend fun complexTransaction() {
    val transaction = TransactionBuilder(account, Network.TESTNET)
        // Payment
        .addOperation(
            PaymentOperation(
                destination = "GABC...",
                amount = "50",
                asset = Asset.NATIVE
            )
        )
        // Create trustline for USDC
        .addOperation(
            ChangeTrustOperation(
                asset = Asset.createNonNativeAsset(
                    "USDC",
                    "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN"
                ),
                limit = "10000"
            )
        )
        // Set account options
        .addOperation(
            SetOptionsOperation()
                .setHomeDomain("example.com")
                .setInflationDestination("GINF...")
        )
        .setBaseFee(100)
        .setTimeout(300)
        .build()

    // Sign and submit...
}
```

## Connecting to Stellar Networks

### Horizon Server

```kotlin
import com.soneso.stellar.sdk.horizon.HorizonServer

// Connect to testnet
val testServer = HorizonServer("https://horizon-testnet.stellar.org")

// Connect to mainnet
val mainServer = HorizonServer("https://horizon.stellar.org")

// Load account information
val account = testServer.loadAccount("GABC...")
println("Balance: ${account.balances[0].balance}")

// Query transactions
val transactions = testServer.getTransactions()
    .forAccount("GABC...")
    .limit(10)
    .execute()

// Don't forget to close when done
testServer.close()
```

### Soroban RPC (Smart Contracts)

```kotlin
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.contract.ContractClient

// High-level contract interaction (recommended)
val client = ContractClient.forContract(
    contractId = "CCXX...",
    rpcUrl = "https://soroban-testnet.stellar.org",
    network = Network.TESTNET
)

// Invoke contract method with native types
val balance = client.invoke<Long>(
    functionName = "balance",
    arguments = mapOf("account" to "GABC..."),
    source = "GABC...",
    signer = null,
    parseResultXdrFn = { Scv.fromInt128(it).toLong() }
)

// Alternative: Using automatic type conversion (when spec is loaded)
val balanceXdr = client.invoke<SCValXdr>(
    functionName = "balance",
    arguments = mapOf("account" to "GABC..."),
    source = "GABC...",
    signer = null
)
val balanceNative = client.funcResToNative("balance", balanceXdr) as BigInteger

// For advanced usage, see the Soroban RPC Usage Guide
```

**Helper Methods:**
- `funcArgsToXdrSCValues()` - Convert native arguments to XDR
- `funcResToNative()` - Convert XDR results back to native types (requires loaded spec)

For detailed smart contract operations, deployment, and advanced patterns, see the [Soroban RPC Usage Guide](soroban-rpc-usage.md).

## Platform-Specific Examples

### Android with Jetpack Compose

```kotlin
@Composable
fun StellarWalletScreen() {
    val scope = rememberCoroutineScope()
    var keypair by remember { mutableStateOf<KeyPair?>(null) }
    var balance by remember { mutableStateOf("0") }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = {
                scope.launch {
                    keypair = KeyPair.random()
                    // In real app, save securely
                }
            }
        ) {
            Text("Generate Wallet")
        }

        keypair?.let {
            Text("Account: ${it.getAccountId()}")
            Text("Balance: $balance XLM")

            Button(
                onClick = {
                    scope.launch {
                        // Load balance
                        val server = HorizonServer("https://horizon-testnet.stellar.org")
                        try {
                            val account = server.loadAccount(it.getAccountId())
                            balance = account.balances[0].balance
                        } catch (e: Exception) {
                            balance = "Not funded"
                        }
                        server.close()
                    }
                }
            ) {
                Text("Check Balance")
            }
        }
    }
}
```

### iOS with SwiftUI

```swift
import SwiftUI
import stellar_sdk

class WalletViewModel: ObservableObject {
    @Published var accountId: String = ""
    @Published var balance: String = "0"

    func generateWallet() {
        Task {
            do {
                let keypair = try await KeyPair.companion.random()
                await MainActor.run {
                    self.accountId = keypair.getAccountId()
                }
            } catch {
                print("Error: \(error)")
            }
        }
    }

    func checkBalance() {
        Task {
            let server = HorizonServer(baseURL: "https://horizon-testnet.stellar.org")
            do {
                let account = try await server.loadAccount(accountId: accountId)
                await MainActor.run {
                    self.balance = account.balances[0].balance
                }
            } catch {
                await MainActor.run {
                    self.balance = "Not funded"
                }
            }
        }
    }
}

struct WalletView: View {
    @StateObject private var viewModel = WalletViewModel()

    var body: some View {
        VStack(spacing: 20) {
            Button("Generate Wallet") {
                viewModel.generateWallet()
            }

            if !viewModel.accountId.isEmpty {
                Text("Account: \(viewModel.accountId)")
                    .font(.system(.body, design: .monospaced))

                Text("Balance: \(viewModel.balance) XLM")

                Button("Check Balance") {
                    viewModel.checkBalance()
                }
            }
        }
        .padding()
    }
}
```

### Web with Kotlin/JS

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement

fun main() {
    val scope = MainScope()

    window.onload = {
        val generateBtn = document.getElementById("generateBtn") as HTMLButtonElement
        val accountDiv = document.getElementById("accountDiv") as HTMLDivElement

        generateBtn.onclick = {
            scope.launch {
                val keypair = KeyPair.random()
                accountDiv.innerHTML = """
                    <p>Account ID: ${keypair.getAccountId()}</p>
                    <p>Ready to receive payments!</p>
                """.trimIndent()
            }
        }
    }
}
```

## Error Handling

Always handle potential errors:

```kotlin
suspend fun robustExample() {
    try {
        val keypair = KeyPair.fromSecretSeed("INVALID")
    } catch (e: IllegalArgumentException) {
        println("Invalid seed format: ${e.message}")
    }

    try {
        val server = HorizonServer("https://horizon-testnet.stellar.org")
        val account = server.loadAccount("GINVALID...")
    } catch (e: AccountNotFoundException) {
        println("Account doesn't exist on network")
    } catch (e: NetworkException) {
        println("Network error: ${e.message}")
    }
}
```

## Best Practices

1. **Never expose secret seeds** - Store them securely using platform-specific secure storage
2. **Use testnet for development** - Always test thoroughly before using mainnet
3. **Set appropriate fees** - Use dynamic fees based on network congestion
4. **Handle all error cases** - Network operations can fail for many reasons
5. **Close resources** - Always close servers when done to free resources
6. **Use coroutines properly** - Don't block the main thread with crypto operations

## Next Steps

Now that you understand the basics:

- **[Architecture Guide](architecture.md)** - Learn about the SDK's design and security
- **[API Reference](api-reference.md)** - Explore all available APIs
- **[Sample Apps](sample-apps.md)** - See complete example applications
- **[Platform Guides](platforms/)** - Platform-specific details and optimizations
- **[Advanced Topics](advanced.md)** - Multi-sig, hardware wallets, and more

---

**Navigation**: [← Documentation Home](README.md) | [Architecture Guide →](architecture.md)