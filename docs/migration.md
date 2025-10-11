# Migration Guide: Java Stellar SDK to KMP Stellar SDK

This comprehensive guide helps developers migrate from the Java Stellar SDK to the Kotlin Multiplatform (KMP) Stellar SDK. The KMP SDK maintains API compatibility where possible while embracing Kotlin idioms and supporting multiple platforms.

## Table of Contents

1. [Overview](#overview)
2. [Key Architectural Differences](#key-architectural-differences)
3. [Platform Support](#platform-support)
4. [API Comparison](#api-comparison)
5. [Migration Examples](#migration-examples)
6. [Breaking Changes](#breaking-changes)
7. [Platform-Specific Considerations](#platform-specific-considerations)
8. [Compatibility Matrix](#compatibility-matrix)
9. [Migration Timeline](#migration-timeline)
10. [Common Patterns and Gotchas](#common-patterns-and-gotchas)
11. [Rollback Strategy](#rollback-strategy)

## Overview

The KMP Stellar SDK is a complete reimplementation of the Java Stellar SDK in Kotlin Multiplatform, offering:

- **Cross-platform support**: JVM, Android, iOS, macOS, JavaScript (Browser & Node.js)
- **Async-first design**: Suspend functions for all I/O and crypto operations
- **Type safety**: Leveraging Kotlin's null safety and sealed classes
- **Modern architecture**: Coroutines instead of callbacks/CompletableFuture
- **Consistent API**: Same core functionality across all platforms

### Migration Effort Estimation

| Application Type | Estimated Effort | Complexity |
|-----------------|------------------|------------|
| Simple wallet (keypair operations) | 1-2 days | Low |
| Transaction builder applications | 3-5 days | Medium |
| Soroban smart contract clients | 5-7 days | Medium-High |
| Full Horizon integration | 1-2 weeks | High |
| Multi-signature services | 1-2 weeks | High |

## Key Architectural Differences

### 1. Synchronous vs Asynchronous

**Java SDK (Synchronous)**:
```java
// Java - Blocking operation
KeyPair keypair = KeyPair.random();
byte[] signature = keypair.sign(data);
```

**KMP SDK (Asynchronous)**:
```kotlin
// Kotlin - Suspend function (non-blocking)
val keypair = KeyPair.random()  // suspend
val signature = keypair.sign(data)  // suspend
```

### 2. Null Safety

**Java SDK**:
```java
// Java - Nullable without compile-time guarantees
KeyPair keypair = KeyPair.fromAccountId(accountId);
char[] seed = keypair.getSecretSeed(); // May return null
if (seed != null) {
    // Use seed
}
```

**KMP SDK**:
```kotlin
// Kotlin - Explicit nullability in type system
val keypair = KeyPair.fromAccountId(accountId)
val seed: CharArray? = keypair.getSecretSeed() // Type shows it's nullable
seed?.let {
    // Use seed - compiler ensures null safety
}
```

### 3. Exception Handling

**Java SDK**:
```java
// Java - Checked exceptions
try {
    Transaction transaction = new Transaction(...);
    transaction.sign(keypair);
} catch (IOException e) {
    // Handle network error
} catch (FormatException e) {
    // Handle format error
}
```

**KMP SDK**:
```kotlin
// Kotlin - No checked exceptions, use Result or try-catch
try {
    val transaction = Transaction(...)
    transaction.sign(keypair) // suspend
} catch (e: Exception) {
    when (e) {
        is NetworkException -> // Handle network error
        is FormatException -> // Handle format error
        else -> throw e
    }
}
```

### 4. Builder Pattern vs Named Parameters

**Java SDK**:
```java
// Java - Builder pattern
Transaction transaction = new TransactionBuilder(account, network)
    .addOperation(
        new PaymentOperation.Builder(
            destination,
            asset,
            amount
        ).setSourceAccount(source)
         .build()
    )
    .addMemo(Memo.text("Hello"))
    .setTimeout(300)
    .setBaseFee(100)
    .build();
```

**KMP SDK**:
```kotlin
// Kotlin - Named parameters and default values
val transaction = Transaction(
    sourceAccount = account,
    network = network,
    operations = listOf(
        PaymentOperation(
            destination = destination,
            asset = asset,
            amount = amount,
            sourceAccount = source // optional with default
        )
    ),
    memo = Memo.text("Hello"),
    timeBounds = TimeBounds.withTimeout(300),
    baseFee = 100
)
```

## Platform Support

| Platform | Java SDK | KMP SDK | Notes |
|----------|----------|---------|-------|
| JVM 8+ | ✅ | ✅ | Full compatibility |
| Android | ✅ | ✅ | Min SDK 21 (KMP) vs 19 (Java) |
| iOS | ❌ | ✅ | Via Kotlin/Native |
| macOS | ❌ | ✅ | Via Kotlin/Native |
| Browser JS | ❌ | ✅ | Via Kotlin/JS |
| Node.js | ❌ | ✅ | Via Kotlin/JS |
| Linux Native | ❌ | 🚧 | Planned |
| Windows Native | ❌ | 🚧 | Planned |

## API Comparison

### KeyPair Operations

| Operation | Java SDK | KMP SDK | Notes |
|-----------|----------|---------|-------|
| Generate random | `KeyPair.random()` | `suspend KeyPair.random()` | Now async |
| From secret seed (String) | `KeyPair.fromSecretSeed(String)` | `suspend KeyPair.fromSecretSeed(String)` | Now async |
| From secret seed (char[]) | `KeyPair.fromSecretSeed(char[])` | `suspend KeyPair.fromSecretSeed(CharArray)` | Now async |
| From secret seed (byte[]) | `KeyPair.fromSecretSeed(byte[])` | `suspend KeyPair.fromSecretSeed(ByteArray)` | Now async |
| From account ID | `KeyPair.fromAccountId(String)` | `KeyPair.fromAccountId(String)` | Sync |
| From public key | `KeyPair.fromPublicKey(byte[])` | `KeyPair.fromPublicKey(ByteArray)` | Sync |
| From BIP39 seed | `KeyPair.fromBip39Seed(byte[], int)` | Not yet implemented | Planned |
| Sign data | `keypair.sign(byte[])` | `suspend keypair.sign(ByteArray)` | Now async |
| Sign decorated | `keypair.signDecorated(byte[])` | `suspend keypair.signDecorated(ByteArray)` | Now async |
| Verify signature | `keypair.verify(byte[], byte[])` | `suspend keypair.verify(ByteArray, ByteArray)` | Now async |
| Get account ID | `keypair.getAccountId()` | `keypair.getAccountId()` | Sync |
| Get secret seed | `keypair.getSecretSeed()` | `keypair.getSecretSeed()` | Returns CharArray? |
| Get public key | `keypair.getPublicKey()` | `keypair.getPublicKey()` | Returns ByteArray |
| Can sign | `keypair.canSign()` | `keypair.canSign()` | Sync |

### Transaction Building

| Component | Java SDK | KMP SDK | Notes |
|-----------|----------|---------|-------|
| Builder class | `TransactionBuilder` | `Transaction` constructor | Direct construction |
| Add operation | `.addOperation(op)` | Pass list to constructor | Immutable |
| Set memo | `.addMemo(memo)` | `memo` parameter | Named param |
| Set timeout | `.setTimeout(seconds)` | `timeBounds` parameter | More flexible |
| Build | `.build()` | Direct instantiation | No build step |
| Sign | `transaction.sign(keypair)` | `suspend transaction.sign(keypair)` | Now async |
| To XDR | `transaction.toEnvelopeXdr()` | `transaction.toEnvelopeXdr()` | Same |
| From XDR | `Transaction.fromEnvelopeXdr(...)` | `Transaction.fromEnvelopeXdr(...)` | Same |

### Soroban Contract Client

| Feature | Java SDK | KMP SDK | Notes |
|---------|----------|---------|-------|
| ContractClient | `ContractClient` | `ContractClient` | Same class name |
| AssembledTransaction | `AssembledTransaction<T>` | `AssembledTransaction<T>` | Same generics |
| Simulate | `assembledTx.simulate()` | `suspend assembledTx.simulate()` | Now async |
| Sign | `assembledTx.sign(signer)` | `suspend assembledTx.sign(signer)` | Now async |
| Submit | `assembledTx.submit()` | `suspend assembledTx.submit()` | Now async |
| Auth handling | `Auth.signAuthEntries(...)` | `suspend Auth.signAuthEntries(...)` | Now async |
| Result parsing | Custom parsers | Custom parsers | Same pattern |

### Horizon Client

| Operation | Java SDK | KMP SDK | Notes |
|-----------|----------|---------|-------|
| Create server | `new Server(url)` | `HorizonServer(url)` | Different name |
| Submit transaction | `server.submitTransaction(tx)` | `suspend server.submitTransaction(tx)` | Now async |
| Account info | `server.accounts().account(id)` | `suspend server.accounts().account(id)` | Now async |
| Stream payments | `server.payments().stream(...)` | Flow-based streaming | Coroutines |
| Error handling | `HorizonException` | `HorizonException` | Same hierarchy |

## Migration Examples

### Example 1: Basic KeyPair Operations

**Java SDK**:
```java
// Java implementation
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    public KeyPair createWallet() {
        try {
            KeyPair keypair = KeyPair.random();
            log.info("Created wallet: " + keypair.getAccountId());
            return keypair;
        } catch (Exception e) {
            log.error("Failed to create wallet", e);
            throw new RuntimeException(e);
        }
    }

    public String signMessage(KeyPair keypair, String message) {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] signature = keypair.sign(messageBytes);
        return Base64.getEncoder().encodeToString(signature);
    }

    public boolean verifySignature(KeyPair keypair, String message, String signature) {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] signatureBytes = Base64.getDecoder().decode(signature);
        return keypair.verify(messageBytes, signatureBytes);
    }
}
```

**KMP SDK**:
```kotlin
// Kotlin implementation
class WalletService {
    private val logger = LoggerFactory.getLogger(WalletService::class.java)

    suspend fun createWallet(): KeyPair {
        return try {
            val keypair = KeyPair.random()
            logger.info("Created wallet: ${keypair.getAccountId()}")
            keypair
        } catch (e: Exception) {
            logger.error("Failed to create wallet", e)
            throw e
        }
    }

    suspend fun signMessage(keypair: KeyPair, message: String): String {
        val messageBytes = message.encodeToByteArray()
        val signature = keypair.sign(messageBytes)
        return Base64.encode(signature)
    }

    suspend fun verifySignature(keypair: KeyPair, message: String, signature: String): Boolean {
        val messageBytes = message.encodeToByteArray()
        val signatureBytes = Base64.decode(signature)
        return keypair.verify(messageBytes, signatureBytes)
    }
}
```

### Example 2: Building and Submitting Transactions

**Java SDK**:
```java
// Java - Building a payment transaction
public class PaymentService {
    private final Server server;
    private final Network network;

    public PaymentService(String horizonUrl, Network network) {
        this.server = new Server(horizonUrl);
        this.network = network;
    }

    public SubmitTransactionResponse sendPayment(
        KeyPair source,
        String destination,
        String amount,
        Asset asset
    ) throws IOException, AccountRequiresMemoException {

        // Load source account
        AccountResponse sourceAccount = server.accounts().account(source.getAccountId());

        // Build transaction
        Transaction transaction = new TransactionBuilder(sourceAccount, network)
            .addOperation(
                new PaymentOperation.Builder(destination, asset, amount)
                    .setSourceAccount(source.getAccountId())
                    .build()
            )
            .addMemo(Memo.text("Payment"))
            .setTimeout(180)
            .setBaseFee(100)
            .build();

        // Sign transaction
        transaction.sign(source);

        // Submit to network
        try {
            return server.submitTransaction(transaction);
        } catch (SubmitTransactionException e) {
            log.error("Transaction failed: " + e.getMessage());
            throw e;
        }
    }
}
```

**KMP SDK**:
```kotlin
// Kotlin - Building a payment transaction
class PaymentService(
    private val horizonUrl: String,
    private val network: Network
) {
    private val server = HorizonServer(horizonUrl)

    suspend fun sendPayment(
        source: KeyPair,
        destination: String,
        amount: String,
        asset: Asset
    ): SubmitTransactionResponse {

        // Load source account
        val sourceAccount = server.accounts().account(source.getAccountId())

        // Build transaction
        val transaction = Transaction(
            sourceAccount = sourceAccount,
            network = network,
            operations = listOf(
                PaymentOperation(
                    destination = destination,
                    asset = asset,
                    amount = amount,
                    sourceAccount = source.getAccountId()
                )
            ),
            memo = Memo.text("Payment"),
            timeBounds = TimeBounds.withTimeout(180),
            baseFee = 100
        )

        // Sign transaction
        transaction.sign(source)

        // Submit to network
        return try {
            server.submitTransaction(transaction)
        } catch (e: SubmitTransactionException) {
            logger.error("Transaction failed: ${e.message}")
            throw e
        }
    }
}
```

### Example 3: Soroban Smart Contract Interaction

**Java SDK**:
```java
// Java - Soroban contract client
public class TokenContract {
    private final ContractClient client;
    private final String contractId;

    public TokenContract(SorobanServer server, String contractId) {
        this.client = new ContractClient(server);
        this.contractId = contractId;
    }

    public CompletableFuture<Long> getBalance(String account) {
        List<ScVal> params = Arrays.asList(
            Address.fromString(account).toScVal()
        );

        return client.invoke(
            contractId,
            "balance",
            params,
            null,  // source account (for read-only)
            result -> ScInt.fromScVal(result).getValue()
        );
    }

    public CompletableFuture<TransactionResult> transfer(
        KeyPair source,
        String to,
        Long amount
    ) {
        List<ScVal> params = Arrays.asList(
            Address.fromString(source.getAccountId()).toScVal(),
            Address.fromString(to).toScVal(),
            new ScInt(amount).toScVal()
        );

        AssembledTransaction<Integer> assembled = client.invoke(
            contractId,
            "transfer",
            params,
            source.getAccountId(),
            result -> 1  // Simple parser
        );

        // Simulate, sign, and submit
        return assembled
            .simulate()
            .thenCompose(sim -> assembled.sign(source))
            .thenCompose(signed -> assembled.submit());
    }
}
```

**KMP SDK**:
```kotlin
// Kotlin - Soroban contract client
class TokenContract(
    private val server: SorobanServer,
    private val contractId: String
) {
    private val client = ContractClient(server)

    suspend fun getBalance(account: String): Long {
        val params = listOf(
            Address.fromString(account).toScVal()
        )

        val assembled = client.invoke(
            contractAddress = contractId,
            method = "balance",
            parameters = params,
            sourceAccount = null,  // read-only
            resultParser = { result ->
                ScInt.fromScVal(result).value
            }
        )

        return assembled.simulateAndParse()
    }

    suspend fun transfer(
        source: KeyPair,
        to: String,
        amount: Long
    ): TransactionResult {
        val params = listOf(
            Address.fromString(source.getAccountId()).toScVal(),
            Address.fromString(to).toScVal(),
            ScInt(amount).toScVal()
        )

        val assembled = client.invoke(
            contractAddress = contractId,
            method = "transfer",
            parameters = params,
            sourceAccount = source.getAccountId(),
            resultParser = { _ -> 1 }  // Simple parser
        )

        // Simulate, sign, and submit
        assembled.simulate()
        assembled.sign(source)
        return assembled.submit()
    }
}
```

### Example 4: Multi-Signature Account

**Java SDK**:
```java
// Java - Multi-signature setup
public class MultiSigService {

    public Transaction createMultiSigTransaction(
        AccountResponse account,
        List<KeyPair> signers,
        Operation operation,
        Network network
    ) {
        Transaction transaction = new TransactionBuilder(account, network)
            .addOperation(operation)
            .setTimeout(300)
            .setBaseFee(100)
            .build();

        // Sign with multiple signers
        for (KeyPair signer : signers) {
            transaction.sign(signer);
        }

        return transaction;
    }

    public boolean hasEnoughSignatures(
        Transaction transaction,
        int threshold
    ) {
        DecoratedSignature[] signatures = transaction
            .getEnvelope()
            .getSignatures();
        return signatures.length >= threshold;
    }
}
```

**KMP SDK**:
```kotlin
// Kotlin - Multi-signature setup
class MultiSigService {

    suspend fun createMultiSigTransaction(
        account: AccountResponse,
        signers: List<KeyPair>,
        operation: Operation,
        network: Network
    ): Transaction {
        val transaction = Transaction(
            sourceAccount = account,
            network = network,
            operations = listOf(operation),
            timeBounds = TimeBounds.withTimeout(300),
            baseFee = 100
        )

        // Sign with multiple signers
        signers.forEach { signer ->
            transaction.sign(signer)
        }

        return transaction
    }

    fun hasEnoughSignatures(
        transaction: Transaction,
        threshold: Int
    ): Boolean {
        val signatures = transaction
            .envelope
            .signatures
        return signatures.size >= threshold
    }
}
```

## Breaking Changes

### 1. All Crypto Operations Are Now Async

**Impact**: High
**Migration Required**: Yes

All cryptographic operations that were synchronous in Java SDK are now suspend functions:

```kotlin
// Before (Java)
KeyPair keypair = KeyPair.random();
byte[] signature = keypair.sign(data);

// After (Kotlin)
val keypair = KeyPair.random()  // suspend
val signature = keypair.sign(data)  // suspend
```

**Migration Strategy**:
- Wrap calls in coroutine scope: `runBlocking { }` for blocking contexts
- Use `lifecycleScope.launch { }` for Android
- Use `GlobalScope.launch { }` for simple cases

### 2. Package Names Changed

**Impact**: Medium
**Migration Required**: Yes

| Java Package | KMP Package |
|--------------|-------------|
| `org.stellar.sdk` | `com.stellar.sdk` or `com.soneso.stellar.sdk` |
| `org.stellar.sdk.xdr` | `com.stellar.sdk.xdr` or `com.soneso.stellar.sdk.xdr` |
| `org.stellar.sdk.responses` | `com.stellar.sdk.responses` |

**Migration Strategy**:
- Global find/replace in imports
- Update ProGuard/R8 rules if applicable

### 3. Exception Hierarchy

**Impact**: Medium
**Migration Required**: Partial

Kotlin doesn't have checked exceptions. All exceptions are unchecked:

```kotlin
// Java - Must handle or declare
try {
    transaction.sign(keypair);
} catch (IOException e) {
    // Required
}

// Kotlin - Optional handling
transaction.sign(keypair)  // No forced try-catch
```

### 4. Builder Pattern Removed

**Impact**: Low
**Migration Required**: Yes

Builders are replaced with constructors and named parameters:

```kotlin
// Java
Transaction tx = new TransactionBuilder(account, network)
    .addOperation(op)
    .setTimeout(100)
    .build();

// Kotlin
val tx = Transaction(
    sourceAccount = account,
    network = network,
    operations = listOf(op),
    timeBounds = TimeBounds.withTimeout(100)
)
```

### 5. Stream/Observable APIs

**Impact**: High for streaming apps
**Migration Required**: Yes

Java SDK uses SSE/callbacks, KMP uses Kotlin Flow:

```kotlin
// Java
EventSource eventSource = server.payments()
    .forAccount(account)
    .stream(new EventListener() {
        @Override
        public void onEvent(PaymentResponse payment) {
            // Handle payment
        }
    });

// Kotlin
server.payments()
    .forAccount(account)
    .stream()
    .collect { payment ->
        // Handle payment
    }
```

## Platform-Specific Considerations

### JVM/Android

**Crypto Provider**:
- Java SDK: BouncyCastle (automatic registration)
- KMP SDK: BouncyCastle (automatic registration)
- **Migration**: No changes needed

**Threading**:
- Use `Dispatchers.IO` for I/O operations
- Use `Dispatchers.Default` for CPU-intensive work
- Main thread safety with `Dispatchers.Main`

**Dependencies**:
```gradle
dependencies {
    implementation("com.stellar:stellar-sdk-kmp:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Android only
}
```

### iOS

**Crypto Provider**:
- Requires libsodium via Swift Package Manager
- Add `Clibsodium` package: `https://github.com/jedisct1/swift-sodium`

**Integration**:
```swift
// Swift - Using KMP SDK from iOS
import StellarSDK

class WalletManager {
    func createWallet() async throws -> KeyPair {
        return try await KeyPairKt.random()
    }

    func signData(keypair: KeyPair, data: Data) async throws -> Data {
        let bytes = KotlinByteArray(size: Int32(data.count))
        data.copyBytes(to: bytes.baseAddress!, count: data.count)
        let signature = try await keypair.sign(data: bytes)
        return Data(bytes: signature.toByteArray(), count: Int(signature.size))
    }
}
```

**Build Configuration**:
```gradle
kotlin {
    ios {
        binaries {
            framework {
                baseName = "StellarSDK"
                isStatic = false
            }
        }
    }
}
```

### JavaScript/Browser

**Crypto Provider**:
- libsodium-wrappers (WebAssembly)
- Automatic async initialization

**Bundling**:
```javascript
// webpack.config.js
module.exports = {
    resolve: {
        fallback: {
            "crypto": require.resolve("crypto-browserify"),
            "stream": require.resolve("stream-browserify"),
            "buffer": require.resolve("buffer/")
        }
    }
};
```

**Usage from JavaScript**:
```javascript
// JavaScript - Using KMP SDK
import * as stellar from 'stellar-sdk-kmp';

async function createWallet() {
    const keypair = await stellar.KeyPair.random();
    console.log('Account:', keypair.getAccountId());
    return keypair;
}

async function signMessage(keypair, message) {
    const encoder = new TextEncoder();
    const data = encoder.encode(message);
    const signature = await keypair.sign(data);
    return signature;
}
```

### Node.js

**Installation**:
```json
{
  "dependencies": {
    "stellar-sdk-kmp": "^1.0.0",
    "libsodium-wrappers": "^0.7.13"
  }
}
```

**Usage**:
```javascript
// Node.js
const stellar = require('stellar-sdk-kmp');

async function main() {
    const keypair = await stellar.KeyPair.random();
    console.log('Created account:', keypair.getAccountId());
}

main().catch(console.error);
```

## Compatibility Matrix

### Network Protocol Compatibility

| Feature | Java SDK | KMP SDK | Compatible |
|---------|----------|---------|------------|
| XDR Encoding | ✅ | ✅ | ✅ Full |
| Transaction Format | v1 | v1 | ✅ Full |
| Signature Format | Ed25519 | Ed25519 | ✅ Full |
| Memo Types | All | All | ✅ Full |
| Operation Types | All | All | ✅ Full |
| Network Passphrase | Same | Same | ✅ Full |
| Soroban XDR | ✅ | ✅ | ✅ Full |

### Horizon API Compatibility

| Endpoint | Java SDK | KMP SDK | Notes |
|----------|----------|---------|-------|
| Accounts | ✅ | ✅ | Same REST API |
| Transactions | ✅ | ✅ | Same submission |
| Operations | ✅ | ✅ | Same queries |
| Effects | ✅ | ✅ | Same streaming |
| Payments | ✅ | ✅ | Same history |
| Trades | ✅ | ✅ | Same orderbook |
| Assets | ✅ | ✅ | Same search |

### Soroban RPC Compatibility

| Feature | Java SDK | KMP SDK | Notes |
|---------|----------|---------|-------|
| simulateTransaction | ✅ | ✅ | Same RPC |
| getTransaction | ✅ | ✅ | Same status |
| getHealth | ✅ | ✅ | Same monitoring |
| getLatestLedger | ✅ | ✅ | Same sync |
| sendTransaction | ✅ | ✅ | Same submission |
| getContractData | ✅ | ✅ | Same queries |

## Migration Timeline

### Phase 1: Assessment (Week 1)
- Inventory current Java SDK usage
- Identify platform targets
- Review breaking changes impact
- Estimate migration effort

### Phase 2: Development Environment (Week 2)
- Set up Kotlin Multiplatform project
- Configure platform targets
- Add KMP SDK dependency
- Set up testing infrastructure

### Phase 3: Core Migration (Weeks 3-4)
- Migrate KeyPair operations
- Migrate transaction building
- Update signing workflows
- Port utility functions

### Phase 4: Platform Integration (Weeks 5-6)
- Platform-specific implementations
- Native library setup (iOS libsodium)
- JavaScript bundling configuration
- Android migration

### Phase 5: Testing (Week 7)
- Unit test migration
- Integration testing
- Cross-platform testing
- Performance validation

### Phase 6: Deployment (Week 8)
- Staging deployment
- Production rollout
- Monitoring setup
- Rollback preparation

## Common Patterns and Gotchas

### 1. Coroutine Scope Management

**Gotcha**: Launching coroutines without proper scope leads to leaks.

**Best Practice**:
```kotlin
// Android - Use lifecycle-aware scope
class MyActivity : AppCompatActivity() {
    fun loadAccount() {
        lifecycleScope.launch {
            val keypair = KeyPair.random()
            // Automatically cancelled if activity destroyed
        }
    }
}

// JVM - Use structured concurrency
class MyService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun processPayment() = scope.launch {
        val keypair = KeyPair.random()
    }

    fun cleanup() {
        scope.cancel()
    }
}
```

### 2. Error Handling in Suspending Functions

**Gotcha**: Exceptions in coroutines need proper handling.

**Best Practice**:
```kotlin
// Use try-catch
suspend fun safeSign(keypair: KeyPair, data: ByteArray): ByteArray? {
    return try {
        keypair.sign(data)
    } catch (e: Exception) {
        logger.error("Signing failed", e)
        null
    }
}

// Or use Result
suspend fun signWithResult(keypair: KeyPair, data: ByteArray): Result<ByteArray> {
    return runCatching {
        keypair.sign(data)
    }
}
```

### 3. Platform-Specific Implementations

**Gotcha**: Assuming all platforms behave identically.

**Best Practice**:
```kotlin
// Use expect/actual for platform differences
// commonMain
expect fun getPlatformName(): String

// jvmMain
actual fun getPlatformName(): String = "JVM"

// jsMain
actual fun getPlatformName(): String = "JavaScript"

// iosMain
actual fun getPlatformName(): String = "iOS"
```

### 4. Blocking Bridge for Legacy Code

**Gotcha**: Mixing blocking and non-blocking code.

**Best Practice**:
```kotlin
// Bridge for Java interop
class KeyPairBridge {
    @JvmStatic
    fun randomBlocking(): KeyPair = runBlocking {
        KeyPair.random()
    }

    @JvmStatic
    fun signBlocking(keypair: KeyPair, data: ByteArray): ByteArray = runBlocking {
        keypair.sign(data)
    }
}
```

### 5. Memory Management

**Gotcha**: Secret keys lingering in memory.

**Best Practice**:
```kotlin
// Clear sensitive data
fun processSecret(secret: CharArray) {
    try {
        val keypair = runBlocking {
            KeyPair.fromSecretSeed(secret)
        }
        // Use keypair
    } finally {
        secret.fill('\u0000')  // Clear the secret
    }
}
```

### 6. Testing Async Code

**Gotcha**: Tests completing before async operations.

**Best Practice**:
```kotlin
// Use runTest for testing
@Test
fun testKeyPairGeneration() = runTest {
    val keypair = KeyPair.random()
    assertNotNull(keypair)
    assertTrue(keypair.canSign())
}

// Configure test dispatcher
@Test
fun testWithDispatcher() = runTest {
    withContext(Dispatchers.IO) {
        val keypair = KeyPair.random()
        val signature = keypair.sign("test".encodeToByteArray())
        assertEquals(64, signature.size)
    }
}
```

## Rollback Strategy

### Maintaining Dual Support

During migration, maintain both SDKs:

```kotlin
// Abstraction layer
interface WalletOperations {
    suspend fun generateKeypair(): KeyPair
    suspend fun sign(keypair: KeyPair, data: ByteArray): ByteArray
}

// KMP implementation
class KmpWalletOperations : WalletOperations {
    override suspend fun generateKeypair() = KeyPair.random()
    override suspend fun sign(keypair: KeyPair, data: ByteArray) = keypair.sign(data)
}

// Java SDK wrapper (for rollback)
class JavaSdkWalletOperations : WalletOperations {
    override suspend fun generateKeypair() = withContext(Dispatchers.IO) {
        org.stellar.sdk.KeyPair.random().toKmpKeyPair()
    }

    override suspend fun sign(keypair: KeyPair, data: ByteArray) = withContext(Dispatchers.IO) {
        keypair.toJavaKeyPair().sign(data)
    }
}
```

### Feature Flags

Use feature flags for gradual rollout:

```kotlin
class SdkSelector(private val featureFlags: FeatureFlags) {
    fun getWalletOperations(): WalletOperations {
        return if (featureFlags.useKmpSdk) {
            KmpWalletOperations()
        } else {
            JavaSdkWalletOperations()
        }
    }
}
```

### Rollback Checklist

1. **Before Migration**:
   - Tag current version in git
   - Document current SDK version
   - Backup configuration
   - Create rollback branch

2. **During Migration**:
   - Keep old code in separate package
   - Use feature flags
   - Maintain compatibility layer
   - Test both paths

3. **If Rollback Needed**:
   - Disable feature flag
   - Revert dependency changes
   - Restore old imports
   - Re-run test suite
   - Monitor for issues

## Support and Resources

### Documentation
- [KMP SDK API Reference](./api-reference.md)
- [Getting Started Guide](./getting-started.md)
- [Platform Guides](./platforms/)
- [Sample Applications](./sample-apps.md)

### Community
- GitHub Issues: [Report bugs and request features]
- Discord: [Join the Stellar developers community]
- Stack Overflow: Tag questions with `stellar` and `kotlin-multiplatform`

### Migration Tools
- [Code converter](https://github.com/stellar/java-to-kmp-converter) (planned)
- [Compatibility checker](https://github.com/stellar/sdk-compatibility) (planned)
- [Test suite validator](https://github.com/stellar/test-migration) (planned)

## Conclusion

Migrating from the Java Stellar SDK to the KMP Stellar SDK requires careful planning but offers significant benefits:

- **Multi-platform support**: Write once, run everywhere
- **Modern architecture**: Coroutines, null safety, and type inference
- **Better performance**: Native performance on each platform
- **Future-proof**: Active development and community support

The migration can be done gradually using compatibility layers and feature flags, minimizing risk while maximizing the benefits of the modern KMP ecosystem.

---

*Last updated: October 2025*
*KMP SDK Version: 1.0.0*
*Java SDK Version: 0.44.0*