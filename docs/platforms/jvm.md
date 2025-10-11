# JVM Platform Guide

This guide covers JVM-specific setup and usage, including Android and server applications.

## Table of Contents

- [Platform Overview](#platform-overview)
- [Android Setup](#android-setup)
- [Server Setup](#server-setup)
- [Cryptography Details](#cryptography-details)
- [Networking Configuration](#networking-configuration)
- [Android-Specific Considerations](#android-specific-considerations)
- [Server-Specific Considerations](#server-specific-considerations)
- [Performance Optimization](#performance-optimization)
- [Troubleshooting](#troubleshooting)

## Platform Overview

The JVM implementation of the Stellar SDK works on:
- **Android** (API 24+)
- **Server JVM** (Java 11+)
- **Desktop JVM** applications

Key characteristics:
- Uses BouncyCastle for cryptography
- Apache Commons Codec for Base32
- Ktor CIO for networking
- Zero-overhead suspend functions

## Android Setup

### Gradle Configuration

```kotlin
// app/build.gradle.kts
android {
    compileSdk = 34

    defaultConfig {
        minSdk = 24  // Minimum API 24 (Android 7.0)
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // For Jetpack Compose projects
    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("com.soneso.stellar:stellar-sdk:0.1.0-SNAPSHOT")

    // Android-specific
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Compose (optional)
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
}
```

### ProGuard/R8 Rules

```proguard
# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**

# Stellar SDK
-keep class com.soneso.stellar.sdk.** { *; }
-keep class com.soneso.stellar.sdk.xdr.** { *; }
```

### Android Manifest

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Network permission -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Optional: For network state checking -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:usesCleartextTraffic="false"
        ...>
    </application>
</manifest>
```

## Server Setup

### Gradle Configuration

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.0.21"
    application
}

kotlin {
    jvmToolchain(11)  // or higher
}

dependencies {
    implementation("com.soneso.stellar:stellar-sdk:0.1.0-SNAPSHOT")

    // Server frameworks (optional)
    implementation("io.ktor:ktor-server-netty:2.3.8")
    implementation("org.springframework.boot:spring-boot-starter:3.2.0")
}

application {
    mainClass.set("com.example.MainKt")
}
```

### Docker Configuration

```dockerfile
FROM openjdk:11-jre-slim

WORKDIR /app

COPY build/libs/app.jar app.jar

# BouncyCastle requires unlimited crypto
RUN apt-get update && apt-get install -y \
    && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Cryptography Details

### BouncyCastle Implementation

The JVM platform uses BouncyCastle 1.78 for Ed25519 operations:

```kotlin
// Internal implementation (in jvmMain)
actual object Ed25519 {
    init {
        // Register BouncyCastle provider
        Security.addProvider(BouncyCastleProvider())
    }

    actual suspend fun generatePrivateKey(): ByteArray {
        val keyPairGenerator = Ed25519KeyPairGenerator()
        keyPairGenerator.init(
            Ed25519KeyGenerationParameters(SecureRandom())
        )
        val keyPair = keyPairGenerator.generateKeyPair()
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        return privateKey.encoded
    }

    actual suspend fun sign(
        message: ByteArray,
        privateKey: ByteArray
    ): ByteArray {
        val signer = Ed25519Signer()
        val privateKeyParams = Ed25519PrivateKeyParameters(privateKey, 0)
        signer.init(true, privateKeyParams)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }
}
```

### Security Configuration

```kotlin
// Check available crypto providers
Security.getProviders().forEach { provider ->
    println("Provider: ${provider.name}")
}

// Ensure BouncyCastle is available
if (Security.getProvider("BC") == null) {
    Security.addProvider(BouncyCastleProvider())
}

// For strong random generation
val secureRandom = SecureRandom.getInstanceStrong()
```

## Networking Configuration

### HTTP Client Setup

```kotlin
// Default configuration (built-in)
val server = HorizonServer("https://horizon-testnet.stellar.org")

// Custom HTTP client configuration
val customClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
    }

    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        exponentialDelay()
    }

    engine {
        threadsCount = 4
        pipelining = true
    }
}

val customServer = HorizonServer(
    serverUrl = "https://horizon.stellar.org",
    httpClient = customClient
)
```

### Connection Pooling

```kotlin
// Configure connection pool
val engine = CIO.create {
    maxConnectionsCount = 100
    endpoint {
        maxConnectionsPerRoute = 10
        keepAliveTime = 5000
        connectTimeout = 5000
        socketTimeout = 30000
    }
}

val httpClient = HttpClient(engine)
```

## Android-Specific Considerations

### Lifecycle Management

```kotlin
class StellarViewModel : ViewModel() {
    private val _keypair = MutableStateFlow<KeyPair?>(null)
    val keypair: StateFlow<KeyPair?> = _keypair.asStateFlow()

    private val horizonServer = HorizonServer(
        "https://horizon-testnet.stellar.org"
    )

    fun generateKeypair() {
        viewModelScope.launch {
            try {
                _keypair.value = KeyPair.random()
            } catch (e: Exception) {
                Log.e("StellarVM", "Failed to generate keypair", e)
            }
        }
    }

    fun loadAccount(accountId: String) {
        viewModelScope.launch {
            try {
                val account = horizonServer.loadAccount(accountId)
                // Update UI state
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        horizonServer.close()
    }
}
```

### Secure Key Storage

```kotlin
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecureKeyStorage(private val context: Context) {
    private val keyAlias = "StellarSecretSeed"
    private val androidKeyStore = "AndroidKeyStore"

    fun storeSecretSeed(seed: CharArray) {
        val encrypted = encrypt(seed)
        // Store encrypted in SharedPreferences
        context.getSharedPreferences("stellar", Context.MODE_PRIVATE)
            .edit()
            .putString("encrypted_seed", encrypted.toBase64())
            .apply()
    }

    fun retrieveSecretSeed(): CharArray? {
        val encrypted = context.getSharedPreferences("stellar", Context.MODE_PRIVATE)
            .getString("encrypted_seed", null) ?: return null

        return decrypt(encrypted.fromBase64())
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore)
        keyStore.load(null)

        return if (keyStore.containsAlias(keyAlias)) {
            keyStore.getKey(keyAlias, null) as SecretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                androidKeyStore
            )
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()

            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }

    private fun encrypt(data: CharArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        return cipher.doFinal(data.toByteArray())
    }

    private fun decrypt(encrypted: ByteArray): CharArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey())
        return cipher.doFinal(encrypted).toCharArray()
    }
}
```

### Network State Handling

```kotlin
@Composable
fun NetworkAwareStellarScreen() {
    val context = LocalContext.current
    var isOnline by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }

            override fun onLost(network: Network) {
                isOnline = false
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    if (!isOnline) {
        Text("No network connection")
    } else {
        // Your Stellar UI
    }
}
```

## Server-Specific Considerations

### Spring Boot Integration

```kotlin
@RestController
@RequestMapping("/api/stellar")
class StellarController {
    private val horizonServer = HorizonServer(
        "https://horizon.stellar.org"
    )

    @GetMapping("/account/{id}")
    suspend fun getAccount(@PathVariable id: String): AccountResponse {
        return horizonServer.loadAccount(id)
    }

    @PostMapping("/payment")
    suspend fun sendPayment(@RequestBody request: PaymentRequest): String {
        val sourceKeypair = KeyPair.fromSecretSeed(request.sourceSeed)
        val account = horizonServer.loadAccount(sourceKeypair.getAccountId())

        val transaction = TransactionBuilder(account, Network.PUBLIC)
            .addOperation(
                PaymentOperation(
                    destination = request.destination,
                    amount = request.amount,
                    asset = Asset.NATIVE
                )
            )
            .setBaseFee(100)
            .setTimeout(180)
            .build()

        transaction.sign(sourceKeypair)

        val response = horizonServer.submitTransaction(transaction)
        return response.hash ?: throw Exception("Transaction failed")
    }

    @PreDestroy
    fun cleanup() {
        horizonServer.close()
    }
}

data class PaymentRequest(
    val sourceSeed: String,
    val destination: String,
    val amount: String
)
```

### Ktor Integration

```kotlin
fun Application.stellarModule() {
    val horizonServer = HorizonServer("https://horizon.stellar.org")

    routing {
        route("/api/stellar") {
            get("/account/{id}") {
                val accountId = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                try {
                    val account = horizonServer.loadAccount(accountId)
                    call.respond(account)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to e.message)
                    )
                }
            }

            post("/transaction/submit") {
                val xdr = call.receive<String>()
                val transaction = Transaction.fromEnvelopeXdr(xdr)

                val response = horizonServer.submitTransaction(transaction)
                call.respond(response)
            }
        }
    }

    // Cleanup on shutdown
    environment.monitor.subscribe(ApplicationStopped) {
        horizonServer.close()
    }
}
```

## Performance Optimization

### Connection Reuse

```kotlin
// Singleton pattern for server instances
object StellarService {
    private val horizonServer by lazy {
        HorizonServer("https://horizon.stellar.org")
    }

    private val sorobanServer by lazy {
        SorobanServer("https://soroban.stellar.org")
    }

    suspend fun getAccount(id: String) = horizonServer.loadAccount(id)

    suspend fun simulateContract(tx: Transaction) =
        sorobanServer.simulateTransaction(tx)

    fun cleanup() {
        horizonServer.close()
        sorobanServer.close()
    }
}
```

### Batch Operations

```kotlin
// Process multiple accounts efficiently
suspend fun batchLoadAccounts(accountIds: List<String>): List<AccountResponse> {
    return coroutineScope {
        accountIds.map { accountId ->
            async {
                try {
                    horizonServer.loadAccount(accountId)
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }
}
```

### Caching

```kotlin
class CachedHorizonService(
    private val horizonServer: HorizonServer
) {
    private val accountCache = ConcurrentHashMap<String, AccountResponse>()
    private val cacheExpiry = ConcurrentHashMap<String, Long>()
    private val cacheDuration = 60_000L // 1 minute

    suspend fun getAccount(accountId: String): AccountResponse {
        val cached = accountCache[accountId]
        val expiry = cacheExpiry[accountId] ?: 0

        return if (cached != null && System.currentTimeMillis() < expiry) {
            cached
        } else {
            val account = horizonServer.loadAccount(accountId)
            accountCache[accountId] = account
            cacheExpiry[accountId] = System.currentTimeMillis() + cacheDuration
            account
        }
    }

    fun clearCache() {
        accountCache.clear()
        cacheExpiry.clear()
    }
}
```

## Troubleshooting

### Common Issues

#### BouncyCastle Not Found

```kotlin
// Ensure BouncyCastle is initialized
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize BouncyCastle if needed
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }
}
```

#### Network on Main Thread (Android)

```kotlin
// Always use coroutines
// WRONG:
fun loadAccount() {
    val account = runBlocking {  // Don't do this on Android!
        horizonServer.loadAccount("GABC...")
    }
}

// CORRECT:
fun loadAccount() {
    lifecycleScope.launch {
        val account = horizonServer.loadAccount("GABC...")
        updateUI(account)
    }
}
```

#### Memory Leaks

```kotlin
// Always close servers when done
class MyActivity : AppCompatActivity() {
    private lateinit var horizonServer: HorizonServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        horizonServer = HorizonServer("https://horizon-testnet.stellar.org")
    }

    override fun onDestroy() {
        super.onDestroy()
        horizonServer.close()  // Prevent leaks
    }
}
```

### Debug Logging

```kotlin
// Enable detailed logging
System.setProperty("io.ktor.development", "true")

// Custom logger
class StellarLogger {
    fun logTransaction(tx: Transaction) {
        println("Transaction Details:")
        println("  Hash: ${tx.hash().toHexString()}")
        println("  Fee: ${tx.fee}")
        println("  Operations: ${tx.operations.size}")
        tx.operations.forEach { op ->
            println("    - ${op::class.simpleName}")
        }
    }
}
```

---

**Navigation**: [← API Reference](../api-reference.md) | [JavaScript Platform →](javascript.md)