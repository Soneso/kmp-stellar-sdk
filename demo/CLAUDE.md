# CLAUDE.md - Demo App Guidance

This file provides guidance to Claude Code (claude.ai/code) when working with the Stellar SDK demo app.

## Purpose of the Demo App

The demo app's **primary purpose** is to showcase SDK functionality for developers learning how to use the SDK. Key principles:

1. **Showcase SDK Usage**: Demonstrate real-world SDK patterns, not simplified implementations
2. **Educational Value**: Clear, well-documented examples that developers can learn from
3. **Production Patterns**: Show best practices (error handling, async operations, UI patterns)
4. **Don't Reimplement SDK**: Use SDK functionality directly - don't duplicate or simplify SDK features

**Example (Correct)**:
```kotlin
// Good: Uses SDK directly
val keypair = KeyPair.random()
val signature = keypair.sign(data)
```

**Example (Incorrect)**:
```kotlin
// Bad: Reimplementing SDK cryptography
fun myOwnSign(data: ByteArray): ByteArray {
    // Custom signing logic - DON'T DO THIS
}
```

## Project Overview

The demo app is a **Kotlin Multiplatform** application showcasing the Stellar SDK across all platforms:

- **Shared Module** (`demo/shared`): 100% of UI (Compose) and business logic
- **Platform Apps**: Minimal entry points (5-28 lines) to launch shared UI
- **Exception**: macOS native app uses SwiftUI (17 files) instead of Compose

### Architecture

```
demo/shared (Compose Multiplatform)
├── UI Screens (7 screens, 1 per feature + main menu)
├── Stellar Integration (6 feature modules using SDK)
└── Platform APIs (clipboard only)

Platform Apps (minimal entry points)
├── androidApp (MainActivity.kt - 20 lines)
├── iosApp (StellarDemoApp.swift - 28 lines)
├── desktopApp (Main.kt - 15 lines)
├── webApp (Main.kt + HTML - 12 lines Kotlin)
└── macosApp (SwiftUI - 17 files, native alternative)
```

## Current Features

The demo includes **7 comprehensive features**:

### 1. Key Generation
- **Location**: `shared/src/commonMain/kotlin/com/soneso/demo/`
- **UI**: `ui/screens/KeyGenerationScreen.kt`
- **Logic**: `stellar/KeyPairGeneration.kt`
- **Demonstrates**: `KeyPair.random()`, `sign()`, `verify()`, `getAccountId()`, `getSecretSeed()`

### 2. Fund Testnet Account
- **Location**: `shared/src/commonMain/kotlin/com/soneso/demo/`
- **UI**: `ui/screens/FundAccountScreen.kt`
- **Logic**: `stellar/AccountFunding.kt`
- **Demonstrates**: `FriendBot.fundTestnetAccount()`

### 3. Fetch Account Details
- **Location**: `shared/src/commonMain/kotlin/com/soneso/demo/`
- **UI**: `ui/screens/AccountDetailsScreen.kt`
- **Logic**: `stellar/AccountDetails.kt`
- **Demonstrates**: `Server.accounts()`, account data parsing, balance display

### 4. Trust Asset
- **Location**: `shared/src/commonMain/kotlin/com/soneso/demo/`
- **UI**: `ui/screens/TrustAssetScreen.kt`
- **Logic**: `stellar/TrustAsset.kt`
- **Demonstrates**: `ChangeTrustOperation`, transaction building, signing, submission

### 5. Send Payment
- **Location**: `shared/src/commonMain/kotlin/com/soneso/demo/`
- **UI**: `ui/screens/SendPaymentScreen.kt`
- **Logic**: `stellar/SendPayment.kt`
- **Demonstrates**: `PaymentOperation`, transaction building, asset handling

### 6. Fetch Smart Contract Details
- **Location**: `shared/src/commonMain/kotlin/com/soneso/demo/`
- **UI**: `ui/screens/ContractDetailsScreen.kt`
- **Logic**: `stellar/ContractDetails.kt`
- **Demonstrates**: Soroban RPC, contract WASM parsing, metadata display

### 7. Deploy Smart Contract
- **Location**: `shared/src/commonMain/kotlin/com/soneso/demo/`
- **UI**: `ui/screens/DeployContractScreen.kt`
- **Logic**: `stellar/DeployContract.kt`
- **Resources**: `resources/wasm/` (5 example WASM files)
- **Demonstrates**: `ContractClient.deploy()`, `install()`, `deployFromWasmId()`, platform-specific resource loading

## Adding New Demo Features

When adding a new demo feature, follow this pattern:

### Step 1: Create Business Logic Module

Create `shared/src/commonMain/kotlin/com/soneso/demo/stellar/NewFeature.kt`:

```kotlin
package com.soneso.demo.stellar

import com.stellar.sdk.* // Import SDK classes

/**
 * Demonstrates [SDK Feature Name] functionality.
 *
 * This shows developers how to:
 * - Point 1
 * - Point 2
 * - Point 3
 */
suspend fun performNewFeature(params): Result<Data> {
    try {
        // Use SDK functionality directly
        val server = Server("https://horizon-testnet.stellar.org")

        // ... implementation using SDK

        return Result.success(data)
    } catch (e: Exception) {
        return Result.failure(e)
    }
}
```

**Important**:
- Use SDK classes and methods directly
- Don't reimplement SDK functionality
- Include comprehensive error handling
- Add KDoc comments explaining what SDK features are being demonstrated
- Use `suspend` functions for async operations

### Step 2: Create UI Screen

Create `shared/src/commonMain/kotlin/com/soneso/demo/ui/screens/NewFeatureScreen.kt`:

```kotlin
package com.soneso.demo.ui.screens

import androidx.compose.runtime.*
import androidx.compose.material3.*
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.soneso.demo.stellar.performNewFeature
import kotlinx.coroutines.launch

class NewFeatureScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()

        var isLoading by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<Result<Data>?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("New Feature") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
            ) {
                // UI implementation

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            result = performNewFeature(params)
                            isLoading = false
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text(if (isLoading) "Loading..." else "Execute")
                }

                // Display result
                result?.let { /* ... */ }
            }
        }
    }
}
```

**UI Guidelines**:
- Use Material 3 components
- Follow existing screen patterns
- Include loading states
- Show errors to users
- Make UI educational (show what's happening)

### Step 3: Add to Navigation

Edit `shared/src/commonMain/kotlin/com/soneso/demo/ui/screens/MainScreen.kt`:

```kotlin
val demoTopics = listOf(
    // ... existing topics
    DemoTopic(
        title = "New Feature",
        description = "Brief description of what this demonstrates",
        icon = Icons.Default.Star, // Choose appropriate icon
        screen = NewFeatureScreen()
    )
)
```

### Step 4: Test on All Platforms

```bash
# Android
./gradlew :demo:androidApp:installDebug

# Desktop (easiest for quick testing)
./gradlew :demo:desktopApp:run

# Web
./gradlew :demo:webApp:jsBrowserDevelopmentRun

# iOS (requires Xcode)
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64
cd demo/iosApp && xcodegen generate && open StellarDemo.xcodeproj

# macOS Native (requires Xcode + libsodium)
./gradlew :demo:shared:linkDebugFrameworkMacosArm64
cd demo/macosApp && xcodegen generate && open StellarDemo.xcodeproj
```

## Code Organization

### Shared Module Structure

```
shared/src/commonMain/kotlin/com/soneso/demo/
├── App.kt                          # Main app entry point
├── ui/
│   ├── screens/                    # One screen per feature
│   │   ├── MainScreen.kt           # Main menu (list of features)
│   │   ├── KeyGenerationScreen.kt
│   │   ├── FundAccountScreen.kt
│   │   ├── AccountDetailsScreen.kt
│   │   ├── TrustAssetScreen.kt
│   │   ├── SendPaymentScreen.kt
│   │   └── ContractDetailsScreen.kt
│   └── theme/
│       └── Theme.kt                # Material 3 theme
├── stellar/                        # SDK integration modules
│   ├── KeyPairGeneration.kt
│   ├── AccountFunding.kt
│   ├── AccountDetails.kt
│   ├── TrustAsset.kt
│   ├── SendPayment.kt
│   └── ContractDetails.kt
└── platform/
    └── Clipboard.kt                # Expect/actual for clipboard
```

### Platform-Specific Code

Minimize platform-specific code. Only use for:

1. **Clipboard access**: Each platform has `Clipboard.*.kt`
2. **Entry points**: Minimal code to launch Compose UI
3. **macOS native**: Full SwiftUI reimplementation (exception)

**Example of acceptable platform-specific code**:

```kotlin
// shared/src/commonMain/kotlin/com/soneso/demo/platform/Clipboard.kt
expect object Clipboard {
    fun copy(text: String)
}

// shared/src/androidMain/kotlin/com/soneso/demo/platform/Clipboard.android.kt
actual object Clipboard {
    private var clipboardManager: ClipboardManager? = null

    fun init(context: Context) {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    actual fun copy(text: String) {
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("", text))
    }
}
```

## Dependencies

The shared module uses:

```kotlin
dependencies {
    // Stellar SDK (includes ktor, serialization, coroutines)
    api(project(":stellar-sdk"))

    // Compose Multiplatform UI
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Navigation
    implementation("cafe.adriel.voyager:voyager-navigator:1.1.0-beta02")
    implementation("cafe.adriel.voyager:voyager-transitions:1.1.0-beta02")

    // Coroutines (for async operations)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}
```

**Key points**:
- Use `api(project(":stellar-sdk"))` to expose SDK to demo code
- Stellar SDK provides ktor, serialization, coroutines transitively
- Compose and Voyager for UI only

## Design System

The demo uses **Material 3** design system:

### Colors
- **Primary**: Teal/Blue theme (`MaterialTheme.colorScheme.primary`)
- **Surfaces**: Card backgrounds, containers
- **Error**: For error states

### Components
- **Card**: For list items, results display
- **TextField**: For user input
- **Button**: For actions
- **TopAppBar**: Screen titles and navigation
- **IconButton**: Back buttons, actions

### Spacing
- **Card padding**: 16.dp
- **Content padding**: 16.dp
- **Vertical spacing**: 8-12.dp between elements

### Typography
- **Titles**: `MaterialTheme.typography.titleLarge`
- **Subtitles**: `MaterialTheme.typography.titleMedium`
- **Body**: `MaterialTheme.typography.bodyMedium`
- **Labels**: `MaterialTheme.typography.labelMedium`

## Best Practices

### 1. Use SDK Functionality Directly

**Good**:
```kotlin
val server = Server("https://horizon-testnet.stellar.org")
val account = server.accounts().account(accountId)
```

**Bad**:
```kotlin
// Don't create simplified wrappers
fun getAccount(id: String): SimpleAccount {
    // Simplified implementation
}
```

### 2. Handle Errors Comprehensively

```kotlin
suspend fun fetchAccount(accountId: String): Result<AccountResponse> {
    return try {
        val server = Server("https://horizon-testnet.stellar.org")
        val account = server.accounts().account(accountId)
        Result.success(account)
    } catch (e: Exception) {
        Result.failure(e) // Preserve full exception for user
    }
}
```

### 3. Use Suspend Functions

```kotlin
// Correct: All SDK crypto operations are suspend functions
suspend fun generateKey(): KeyPair {
    return KeyPair.random() // This is a suspend function
}

// In UI, call with coroutineScope
val scope = rememberCoroutineScope()
Button(onClick = {
    scope.launch {
        val key = generateKey()
    }
})
```

### 4. Show SDK Patterns

```kotlin
// Good: Shows developers the actual SDK pattern
val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
    .addOperation(
        ChangeTrustOperation.Builder(
            ChangeTrustAsset.create(assetCode, issuer),
            limit
        ).build()
    )
    .build()

transaction.sign(keypair)
server.submitTransaction(transaction)
```

### 5. Educational Comments

Add comments explaining SDK concepts:

```kotlin
// Create a payment operation to transfer XLM or issued assets
// The asset can be native (XLM) or an issued asset (code + issuer)
val operation = PaymentOperation.Builder(
    destination,  // Recipient's public key
    asset,        // Asset to send (native or issued)
    amount        // Amount in stroops (1 XLM = 10,000,000 stroops)
).build()
```

## Common Patterns

### Pattern 1: Feature with Loading State

```kotlin
var isLoading by remember { mutableStateOf(false) }
var result by remember { mutableStateOf<Result<Data>?>(null) }
val scope = rememberCoroutineScope()

Button(
    onClick = {
        scope.launch {
            isLoading = true
            result = performOperation()
            isLoading = false
        }
    },
    enabled = !isLoading && inputValid
) {
    Text(if (isLoading) "Loading..." else "Execute")
}

if (isLoading) {
    CircularProgressIndicator()
}

result?.let { res ->
    res.onSuccess { data ->
        // Display success
    }.onFailure { error ->
        // Display error
    }
}
```

### Pattern 2: Form Input Validation

```kotlin
var input by remember { mutableStateOf("") }
val isValid = remember(input) {
    // Validation logic
    input.isNotBlank() && input.startsWith("G") && input.length == 56
}

OutlinedTextField(
    value = input,
    onValueChange = { input = it },
    label = { Text("Account ID") },
    isError = input.isNotEmpty() && !isValid,
    supportingText = {
        if (input.isNotEmpty() && !isValid) {
            Text("Invalid account ID format")
        }
    }
)
```

### Pattern 3: Result Display

```kotlin
result?.let { res ->
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (res.isSuccess)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (res.isSuccess) "Success" else "Error",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = res.getOrNull()?.toString() ?: res.exceptionOrNull()?.message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
```

## Testing Strategy

### Manual Testing
- Test each feature on Desktop (fastest iteration)
- Verify on Android and Web
- Test iOS and macOS native before releases

### Integration Points
- All features should work against Stellar testnet
- Use Friendbot for account funding
- Test with real network calls (not mocks)

## Platform Considerations

### Android
- Minimum SDK: API 24 (Android 7.0)
- Use Activity Compose
- Initialize clipboard in MainActivity

### iOS
- Deployment target: iOS 14.0
- Uses Compose via UIViewControllerRepresentable
- Requires Swift Package Manager for libsodium

### macOS Native
- Uses SwiftUI (not Compose)
- Requires libsodium via Homebrew
- 17 Swift files with Views/Components/Utilities structure

### Desktop (JVM)
- Runs on macOS, Windows, Linux
- Recommended for macOS users wanting Compose UI
- Simple window wrapper around shared Compose

### Web (JavaScript)
- Stable Kotlin/JS with Compose
- Bundle size: ~955 KB production (220 KB gzipped)
- Supports all modern browsers

## Build Commands

```bash
# Build all
./gradlew build

# Run desktop (fastest for development)
./gradlew :demo:desktopApp:run

# Run Android
./gradlew :demo:androidApp:installDebug

# Run web
./gradlew :demo:webApp:jsBrowserDevelopmentRun

# Build iOS framework
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64

# Build macOS framework
./gradlew :demo:shared:linkDebugFrameworkMacosArm64
```

## Don'ts

1. ❌ **Don't reimplement SDK functionality** in the demo
2. ❌ **Don't create simplified wrappers** around SDK classes
3. ❌ **Don't use mock data** when real network calls work
4. ❌ **Don't bypass error handling** to simplify code
5. ❌ **Don't ignore suspend functions** - they're required for JavaScript
6. ❌ **Don't add platform-specific code** unless absolutely necessary
7. ❌ **Don't create new dependencies** without considering all platforms

## Do's

1. ✅ **Use SDK classes and methods directly**
2. ✅ **Show real-world patterns** developers will use
3. ✅ **Handle errors comprehensively**
4. ✅ **Add educational comments** explaining SDK concepts
5. ✅ **Test on multiple platforms** before committing
6. ✅ **Follow Material 3 design** for consistency
7. ✅ **Keep platform-specific code minimal**
8. ✅ **Document new features** in README files

## Resources

- **Main SDK Documentation**: `../CLAUDE.md`
- **Demo App README**: `README.md`
- **Platform READMEs**: `{platform}App/README.md`
- **Stellar Docs**: https://developers.stellar.org/
- **Compose Multiplatform**: https://www.jetbrains.com/lp/compose-multiplatform/

## Questions?

When working on the demo app:
1. Check this file first
2. Look at existing features for patterns
3. Review platform-specific READMEs
4. Refer to main SDK documentation at `../CLAUDE.md`
