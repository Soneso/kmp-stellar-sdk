# JVM Platform Guide

This guide covers JVM-specific setup and usage, including Android and server applications.

## Table of Contents

- [Platform Overview](#platform-overview)
- [Android Setup](#android-setup)
- [Server Setup](#server-setup)
- [Networking Configuration](#networking-configuration)
- [Android-Specific Considerations](#android-specific-considerations)
- [Server Integration Example](#server-integration-example)
- [Troubleshooting](#troubleshooting)

## Platform Overview

The JVM implementation of the Stellar SDK works on:
- **Android** (API 24+)
- **Server JVM** (Java 17+)
- **Desktop JVM** applications

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    implementation("com.soneso.stellar:stellar-sdk:1.6.0")

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
    jvmToolchain(17)  // or higher
}

dependencies {
    implementation("com.soneso.stellar:stellar-sdk:1.6.0")

    // Server frameworks (optional)
    implementation("io.ktor:ktor-server-netty:2.3.8")
    implementation("org.springframework.boot:spring-boot-starter:3.2.0")
}

application {
    mainClass.set("com.example.MainKt")
}
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

Ensure proper lifecycle management by closing resources in Android components (Activities, ViewModels) to prevent memory leaks. Always use coroutines (lifecycleScope, viewModelScope) for network operations.

## Server Integration Example

### Ktor REST API

```kotlin
fun Application.stellarModule() {
    val horizonServer = HorizonServer("https://horizon.stellar.org")

    routing {
        get("/account/{id}") {
            val accountId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            try {
                val account = horizonServer.loadAccount(accountId)
                call.respond(account)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to e.message))
            }
        }

        post("/payment") {
            val request = call.receive<PaymentRequest>()
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
            call.respond(response)
        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        horizonServer.close()
    }
}

data class PaymentRequest(
    val sourceSeed: String,
    val destination: String,
    val amount: String
)
```

## Troubleshooting

### Common Issues

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

#### Ktor Development Mode

```kotlin
// Enable detailed HTTP logging
System.setProperty("io.ktor.development", "true")
```

---

**Navigation**: [← SDK Usage Examples](../sdk-usage-examples.md) | [JavaScript Platform →](javascript.md)