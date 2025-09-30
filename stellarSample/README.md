# Stellar KMP Sample

A single Kotlin Multiplatform sample app demonstrating proper KMP architecture with **shared business logic** and platform-specific UIs.

## Structure

```
stellarSample/
├── shared/                          # Shared Kotlin business logic
│   ├── src/commonMain/kotlin/
│   │   └── StellarDemo.kt          # Core SDK functionality
│   └── src/commonTest/kotlin/
│       └── StellarDemoTest.kt      # Shared tests
│
├── androidApp/                      # Android app with Compose UI
│   ├── src/main/kotlin/
│   │   ├── MainActivity.kt
│   │   └── StellarViewModel.kt
│   └── AndroidManifest.xml
│
├── iosApp/                         # iOS app with SwiftUI
│   ├── ContentView.swift
│   └── StellarSampleApp.swift
│
└── webApp/                         # Web app with Kotlin/JS
    ├── src/jsMain/kotlin/
    │   └── Main.kt
    └── src/jsMain/resources/
        └── index.html
```

## Key Concept

All three platforms share **the same business logic** written in Kotlin (`shared` module), but have platform-specific UIs:
- **Android**: Jetpack Compose
- **iOS**: SwiftUI
- **Web**: Kotlin/JS with HTML

## What's Shared

The `StellarDemo` class in `shared/src/commonMain/kotlin/` provides:

1. **KeyPair Generation**
   - Random keypair generation
   - Create from secret seed
   - Create from account ID

2. **Cryptographic Operations**
   - Sign messages
   - Verify signatures

3. **Test Suite**
   - 8 comprehensive tests
   - Works identically on all platforms

## Running the Sample

### Android

```bash
./gradlew :stellarSample:androidApp:installDebug
```

Or open in Android Studio and run.

### iOS

1. Build the shared framework:
```bash
./gradlew :stellarSample:shared:linkDebugFrameworkIosSimulatorArm64
```

2. Open `stellarSample/iosApp` in Xcode
3. Build and run

### Web

```bash
./gradlew :stellarSample:webApp:jsBrowserDevelopmentRun
```

Or build production:
```bash
./gradlew :stellarSample:webApp:jsBrowserProductionWebpack
```

Then open `stellarSample/webApp/build/distributions/index.html`

## Benefits of This Approach

### For Developers
✅ **Write business logic once** - Fix bugs in one place
✅ **Platform-native UIs** - Each platform feels native
✅ **Clear separation** - Business logic vs presentation
✅ **Type safety** - Kotlin everywhere
✅ **Easier testing** - Test shared code once

### Why KMP?

**Benefits**:
- `stellarSample/shared/` - 500 lines of shared logic (written once)
- `stellarSample/androidApp/` - 200 lines of UI only
- `stellarSample/iosApp/` - 200 lines of UI only
- `stellarSample/webApp/` - 200 lines of UI only

**Result**: Business logic written once, runs everywhere

## Architecture

### Shared Module Dependencies

```kotlin
commonMain {
    dependencies {
        implementation(project(":stellar-sdk"))
    }
}
```

The shared module depends on the Stellar SDK, which is also multiplatform.

### Platform Apps Dependencies

```kotlin
// Android
implementation(project(":stellarSample:shared"))

// iOS
framework {
    baseName = "shared"
}

// Web
implementation(project(":stellarSample:shared"))
```

Each platform app depends on the shared module.

## Code Example

### Shared Business Logic (Kotlin)

```kotlin
// stellarSample/shared/src/commonMain/kotlin/StellarDemo.kt
class StellarDemo {
    fun generateRandomKeyPair(): KeyPairInfo {
        val keypair = KeyPair.random()
        return KeyPairInfo(
            accountId = keypair.getAccountId(),
            secretSeed = keypair.getSecretSeed()?.concatToString(),
            canSign = keypair.canSign(),
            cryptoLibrary = KeyPair.getCryptoLibraryName()
        )
    }
}
```

### Android UI (Kotlin + Compose)

```kotlin
// stellarSample/androidApp/src/main/kotlin/MainActivity.kt
@Composable
fun KeyPairCard(viewModel: StellarViewModel) {
    val keypair by viewModel.keypair.collectAsState()

    Button(onClick = { viewModel.generateRandom() }) {
        Text("Generate Random")
    }

    keypair?.let {
        Text("Account: ${it.accountId}")
    }
}
```

### iOS UI (Swift + SwiftUI)

```swift
// stellarSample/iosApp/ContentView.swift
struct ContentView: View {
    @StateObject private var viewModel = StellarViewModel()

    var body: some View {
        Button("Generate Random") {
            viewModel.generateRandom()
        }

        if let keypair = viewModel.keypair {
            Text("Account: \\(keypair.accountId)")
        }
    }
}

class StellarViewModel: ObservableObject {
    private let demo = StellarDemo()
    @Published var keypair: KeyPairInfo?

    func generateRandom() {
        keypair = demo.generateRandomKeyPair()
    }
}
```

### Web UI (Kotlin/JS + HTML)

```kotlin
// stellarSample/webApp/src/jsMain/kotlin/Main.kt
private val demo = StellarDemo()

fun generateRandom() {
    val keypair = demo.generateRandomKeyPair()
    displayKeypair(keypair)
}
```

## Testing

Run tests on all platforms:

```bash
# Shared tests (run on all platforms)
./gradlew :stellarSample:shared:allTests

# Android tests
./gradlew :stellarSample:shared:testDebugUnitTest

# iOS tests
./gradlew :stellarSample:shared:iosSimulatorArm64Test

# JS tests
./gradlew :stellarSample:shared:jsTest
```

## Features Demonstrated

### 1. Random KeyPair Generation
```kotlin
val keypair = demo.generateRandomKeyPair()
// Returns: KeyPairInfo with account ID, secret seed, etc.
```

### 2. KeyPair from Secret Seed
```kotlin
val result = demo.createFromSeed("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")
val keypair = result.getOrNull()
```

### 3. KeyPair from Account ID (Public Key Only)
```kotlin
val result = demo.createFromAccountId("GDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCFAC6")
// This keypair can verify but cannot sign
```

### 4. Sign and Verify
```kotlin
val signResult = demo.signMessage("Hello Stellar!")
val signature = signResult.getOrNull()

val isValid = demo.verifySignature(signature!!)
// Returns: true
```

### 5. Test Suite
```kotlin
val results = demo.runTestSuite()
// Returns: List of TestResult with pass/fail status
```

## Crypto Library Detection

The sample displays which crypto library is being used on each platform:
- **JVM**: BouncyCastle
- **iOS/Native**: libsodium (native)
- **JavaScript**: libsodium.js (WebAssembly)

This demonstrates that the same Kotlin code works with different underlying crypto implementations.

## Learning Resources

This sample demonstrates:
1. ✅ **Proper KMP architecture** - Shared code + platform UIs
2. ✅ **expect/actual pattern** - Platform-specific implementations
3. ✅ **KMP best practices** - Clear module boundaries
4. ✅ **Testing strategy** - Test shared code once
5. ✅ **Real-world SDK usage** - Stellar SDK in production

## Architecture Benefits

### stellarSample (KMP)
- 500 lines of shared Kotlin business logic
- 600 lines of platform-specific UI code
- **Works on all platforms** with the same code
- Business logic tested once, runs everywhere
- Platform-native UIs for best user experience

## Next Steps

To use this as a template for your own KMP app:

1. Copy `shared/src/commonMain/kotlin/StellarDemo.kt` structure
2. Add your business logic in the shared module
3. Keep platform apps thin (UI only)
4. Test shared code once
5. Enjoy writing multiplatform apps!

## Requirements

- **Kotlin**: 1.9.20+
- **Gradle**: 8.0+
- **Android**: API 24+
- **iOS**: iOS 13+
- **Web**: Modern browsers with WebAssembly support

## License

Same as the Stellar SDK.
