# Getting Started Guide

**Looking for a quick start? See [Quick Start](quick-start.md) to get running in 30 minutes.**

This comprehensive guide covers platform-specific details, best practices, and advanced usage patterns for the Stellar KMP SDK.

## Table of Contents

- [Installation](#installation)
  - [Gradle Setup](#gradle-setup)
  - [Platform-Specific Requirements](#platform-specific-requirements)
- [Basic Concepts](#basic-concepts)
- [Demo Applications](#demo-applications)
- [Advanced KeyPair Usage](#advanced-keypair-usage)
- [Account Management](#account-management)
- [Transaction Building](#transaction-building)
- [Connecting to Stellar Networks](#connecting-to-stellar-networks)
- [Platform-Specific Examples](#platform-specific-examples)
- [Error Handling](#error-handling)
- [Best Practices](#best-practices)
- [Next Steps](#next-steps)

## Installation

### Gradle Setup

Add the SDK as a Maven dependency (recommended for most projects):

```kotlin
// In your module's build.gradle.kts
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.8.1")
}
```

**Alternative: For advanced native Swift interop** (only needed for native iOS/macOS apps where Swift directly uses SDK types):

```kotlin
// settings.gradle.kts
includeBuild("/path/to/kmp-stellar-sdk")

// In your module's build.gradle.kts
dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.8.1")
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
    implementation("com.soneso.stellar:stellar-sdk:1.8.1")
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

No additional setup required. The SDK bundles libsodium-wrappers-sumo (0.7.13) automatically (sumo build includes SHA-256 support required for transaction hashing).

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

## Demo Applications

The SDK includes comprehensive demo applications showcasing real-world usage patterns across all platforms. These are excellent learning resources:

- **Android**: Jetpack Compose UI with 11 feature demonstrations
- **iOS**: SwiftUI wrapper around shared Compose UI
- **macOS**: Native SwiftUI implementation
- **Desktop**: JVM Compose application
- **Web**: Kotlin/JS browser application

See the [Demo App Guide](demo-app.md) for setup instructions and detailed walkthroughs.

## Advanced KeyPair Usage

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

## Account Management

### Account Creation on Mainnet

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

## Transaction Building

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
        )
        .setBaseFee(100)
        .setTimeout(300)
        .build()

    // Sign and submit...
}
```

### Deploying Smart Contracts

```kotlin
import com.soneso.stellar.sdk.contract.ContractClient
import java.io.File

suspend fun deployContract() {
    // Read WASM file
    val wasmBytes = File("path/to/contract.wasm").readBytes()

    // Deploy contract with constructor arguments; returns a client for the new contract
    val client = ContractClient.deploy(
        wasmBytes = wasmBytes,
        constructorArgs = mapOf("admin" to "GABC..."),
        source = sourceKeypair.getAccountId(),
        signer = sourceKeypair,
        network = Network.TESTNET,
        rpcUrl = "https://soroban-testnet.stellar.org"
    )

    println("Contract deployed: ${client.contractId}")
}
```

**Authorization**: Write operations on Soroban contracts require authorization. The SDK handles auto-authorization for the invoker. For complex authorization scenarios, see the [Advanced Topics Guide](advanced.md#custom-authorization-with-authsigner).

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

### Soroban RPC Server

```kotlin
import com.soneso.stellar.sdk.rpc.SorobanServer

// Connect to testnet
val sorobanTestnet = SorobanServer("https://soroban-testnet.stellar.org")

// Connect to mainnet
val sorobanMainnet = SorobanServer("https://soroban.stellar.org")

// Get network health
val health = sorobanTestnet.getHealth()
println("Status: ${health.status}")

// Get latest ledger info
val latestLedger = sorobanTestnet.getLatestLedger()
println("Latest ledger: ${latestLedger.sequence}")

// Get network information
val network = sorobanTestnet.getNetwork()
println("Network passphrase: ${network.passphrase}")

// Don't forget to close when done
sorobanTestnet.close()
```

For advanced operations like querying ledger entries, contract data, or custom event filtering, see the [Advanced Topics Guide](advanced.md#advanced-network-operations).

### Smart Contract Interaction

Interact with Soroban smart contracts using the high-level ContractClient API:

```kotlin
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.contract.ContractClient

// Create contract client (automatically loads contract spec)
val client = ContractClient.forContract(
    contractId = "CCXX...",
    rpcUrl = "https://soroban-testnet.stellar.org",
    network = Network.TESTNET
)

// Invoke read-only function (auto-executes and returns result)
val balance = client.invoke<Long>(
    functionName = "balance",
    arguments = mapOf("account" to "GABC..."),
    source = "GABC...",
    signer = null,  // Read-only, no signing needed
    parseResultXdrFn = { Scv.fromInt128(it).longValue() }
)
println("Balance: $balance")

// Invoke write function (auto-signs and submits, returns the parsed result)
client.invoke<Unit>(
    functionName = "transfer",
    arguments = mapOf(
        "from" to "GABC...",
        "to" to "GXYZ...",
        "amount" to 1000L
    ),
    source = sourceKeypair.getAccountId(),
    signer = sourceKeypair,  // Signs and submits transaction
    parseResultXdrFn = { }  // transfer returns void
)

// Alternative: Use automatic type conversion
val balanceXdr = client.invoke<SCValXdr>(
    functionName = "balance",
    arguments = mapOf("account" to "GABC..."),
    source = "GABC...",
    signer = null
)
// Convert XDR result to native type using contract spec
val balanceNative = client.funcResToNative("balance", balanceXdr) as BigInteger
```

**Helper Methods:**
- `funcArgsToXdrSCValues()` - Convert native arguments to XDR
- `funcResToNative()` - Convert XDR results back to native types (inverse of nativeToXdrSCVal)

#### Low-Level RPC Server

For advanced use cases requiring direct RPC server access, custom HTTP clients, or advanced polling strategies, see the [Advanced Topics Guide](advanced.md#advanced-network-operations).

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

- **[Demo App](demo-app.md)** - See complete example application
- **[Architecture Guide](architecture.md)** - Learn about the SDK's design and security
- **[SDK Usage Examples](sdk-usage-examples.md)** - Explore all available APIs
- **[Platform Guides](platforms/)** - Platform-specific details and optimizations
- **[Advanced Topics](advanced.md)** - Multi-sig, hardware wallets, and more

---

**Navigation**: [← Quick Start](quick-start.md) | [Demo App →](demo-app.md)