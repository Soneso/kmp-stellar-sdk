# KMP Sample App Design

## Goal
Create a single Kotlin Multiplatform sample app that demonstrates using the Stellar SDK across all platforms with **shared business logic** and platform-specific UI.

## Project Structure

```
stellarSample/
├── shared/                          # Shared KMP module
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/
│       │   ├── StellarDemo.kt      # Shared business logic
│       │   ├── KeyPairManager.kt   # Shared SDK wrapper
│       │   └── TestSuite.kt        # Shared tests
│       ├── commonTest/kotlin/
│       ├── androidMain/kotlin/
│       ├── iosMain/kotlin/
│       └── jsMain/kotlin/
│
├── androidApp/                      # Android app
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/
│           └── MainActivity.kt     # Compose UI
│
├── iosApp/                         # iOS app
│   ├── iosApp.xcodeproj
│   └── iosApp/
│       ├── ContentView.swift       # SwiftUI UI
│       └── iosAppApp.swift
│
└── webApp/                         # Web app
    ├── build.gradle.kts
    └── src/
        └── jsMain/kotlin/
            └── Main.kt             # Compose for Web UI
```

## Shared Module (`shared/`)

### Features to Demonstrate

1. **KeyPair Generation**
   - Generate random keypairs
   - Create from secret seed
   - Create from account ID

2. **Signing & Verification**
   - Sign arbitrary data
   - Verify signatures
   - Show signature details

3. **Key Information**
   - Display account ID
   - Display secret seed (with security warning)
   - Show crypto library name

### Shared Business Logic

```kotlin
// shared/src/commonMain/kotlin/StellarDemo.kt
class StellarDemo {
    fun generateRandomKeyPair(): KeyPairInfo
    fun createFromSeed(seed: String): Result<KeyPairInfo>
    fun signMessage(keypair: KeyPairInfo, message: String): SignatureResult
    fun verifySignature(signature: SignatureResult, message: String): Boolean
    fun runTests(): List<TestResult>
}

data class KeyPairInfo(
    val accountId: String,
    val secretSeed: String?,
    val canSign: Boolean,
    val cryptoLibrary: String
)

data class SignatureResult(
    val signature: ByteArray,
    val publicKey: ByteArray
)

data class TestResult(
    val name: String,
    val passed: Boolean,
    val message: String,
    val duration: Long
)
```

## Platform Apps

### Android App
**UI Framework:** Jetpack Compose
**Features:**
- Material Design 3
- Same UI as current androidSample
- Uses `StellarDemo` from shared module
- No SDK code in Android app (all in shared)

### iOS App
**UI Framework:** SwiftUI
**Features:**
- iOS native design
- Same UI as current iosSample
- Uses `StellarDemo` from shared framework
- No SDK code in iOS app (all in shared)

### Web App
**Option 1: Compose for Web** (Recommended)
- Same Compose UI as Android
- Full type safety
- True multiplatform UI

**Option 2: Kotlin/JS + HTML**
- Kotlin code calling DOM APIs
- More lightweight
- Traditional web feel

**Option 3: React Kotlin**
- Kotlin bindings for React
- More familiar to web developers

**Recommendation:** Start with **Kotlin/JS + HTML** (simpler), can add Compose for Web later.

## Benefits of This Approach

### For Developers:
1. ✅ **See the real KMP benefit** - write business logic once
2. ✅ **Understand shared code patterns** - how to structure KMP apps
3. ✅ **Platform-specific UI** - each platform feels native
4. ✅ **Clear separation** - business logic vs UI
5. ✅ **Easier maintenance** - fix bugs in one place

### For the SDK:
1. ✅ **Demonstrates proper SDK usage** - in shared code
2. ✅ **Shows KMP best practices** - architecture patterns
3. ✅ **One sample to maintain** - not three separate ones
4. ✅ **True to KMP philosophy** - shared code, native UIs

## Migration Plan

### Phase 1: Create Shared Module
1. Create `stellarSample/shared/` module
2. Extract business logic from existing samples
3. Create shared `StellarDemo` class
4. Write shared tests

### Phase 2: Integrate Platform Apps
1. Update iOS app to use shared framework
2. Update Android app to use shared module
3. Create web app using shared module

### Phase 3: Cleanup
1. Remove old individual samples (or move to legacy/)
2. Update README with KMP sample instructions
3. Update documentation to focus on shared code

## What Gets Removed/Replaced

### Current Structure:
```
iosSample/          # Standalone iOS app (3,000+ lines)
androidSample/      # Standalone Android app (2,000+ lines)
webSample/          # Vanilla JS app (doesn't use shared code!)
```

**Problem:** Each sample duplicates the business logic!

### New Structure:
```
stellarSample/
  shared/           # 500 lines of shared business logic
  androidApp/       # 200 lines of UI only
  iosApp/           # 200 lines of UI only
  webApp/           # 200 lines of UI only
```

**Result:** ~90% less code, one place to fix bugs, true KMP!

## Example: Shared Business Logic

```kotlin
// shared/src/commonMain/kotlin/StellarDemo.kt
class StellarDemo {
    private var currentKeyPair: KeyPair? = null

    fun generateRandomKeyPair(): KeyPairInfo {
        val keypair = KeyPair.random()
        currentKeyPair = keypair

        return KeyPairInfo(
            accountId = keypair.getAccountId(),
            secretSeed = keypair.getSecretSeed()?.let {
                String(it)
            },
            canSign = keypair.canSign(),
            cryptoLibrary = KeyPair.getCryptoLibraryName()
        )
    }

    fun signMessage(message: String): SignatureResult? {
        val kp = currentKeyPair ?: return null
        val data = message.encodeToByteArray()
        val signature = kp.sign(data)

        return SignatureResult(
            signature = signature,
            publicKey = kp.getPublicKey()
        )
    }

    // All 8 tests from current samples
    fun runTestSuite(): List<TestResult> {
        return listOf(
            testRandomGeneration(),
            testFromSeed(),
            testSigning(),
            testVerification(),
            // ... etc
        )
    }
}
```

### Android UI (uses shared):
```kotlin
// androidApp/src/main/kotlin/MainActivity.kt
@Composable
fun StellarScreen() {
    val demo = remember { StellarDemo() }
    var keypair by remember { mutableStateOf<KeyPairInfo?>(null) }

    Button(onClick = {
        keypair = demo.generateRandomKeyPair()
    }) {
        Text("Generate Keypair")
    }

    keypair?.let {
        Text("Account: ${it.accountId}")
        Text("Library: ${it.cryptoLibrary}")
    }
}
```

### iOS UI (uses shared):
```swift
// iosApp/ContentView.swift
struct ContentView: View {
    let demo = StellarDemo()
    @State var keypair: KeyPairInfo?

    var body: some View {
        Button("Generate Keypair") {
            keypair = demo.generateRandomKeyPair()
        }

        if let kp = keypair {
            Text("Account: \(kp.accountId)")
            Text("Library: \(kp.cryptoLibrary)")
        }
    }
}
```

### Web UI (uses shared):
```kotlin
// webApp/src/jsMain/kotlin/Main.kt
fun main() {
    val demo = StellarDemo()

    document.getElementById("generateBtn")?.addEventListener("click", {
        val keypair = demo.generateRandomKeyPair()
        document.getElementById("accountId")?.textContent = keypair.accountId
        document.getElementById("cryptoLib")?.textContent = keypair.cryptoLibrary
    })
}
```

## Implementation Order

1. ✅ **Create `stellarSample/` directory structure**
2. ✅ **Set up `shared/` module with gradle config**
3. ✅ **Extract shared business logic from existing samples**
4. ✅ **Create shared `StellarDemo` class**
5. ✅ **Write shared test suite**
6. ✅ **Create Android app using shared module**
7. ✅ **Create iOS app using shared framework**
8. ✅ **Create web app using shared module**
9. ✅ **Test all three platforms**
10. ✅ **Update documentation**
11. ✅ **Archive old samples**

## Timeline

- **Shared module**: 1-2 hours
- **Android app**: 30 minutes (reuse existing UI)
- **iOS app**: 30 minutes (reuse existing UI)
- **Web app**: 1 hour (new Kotlin/JS UI)
- **Testing & docs**: 1 hour

**Total:** ~4-5 hours for complete KMP sample

## Decision: Web UI Framework

For the web app, let's use **Kotlin/JS with kotlinx.html DSL**:
- ✅ Pure Kotlin (no JS framework)
- ✅ Type-safe HTML
- ✅ Lightweight
- ✅ Easy to understand
- ✅ Direct SDK usage (no wrappers needed)

Can add Compose for Web later if desired.

## Next Steps

Ready to start implementation?

1. Create directory structure
2. Set up gradle configuration
3. Extract and create shared business logic
4. Create platform apps
5. Test everything
6. Update documentation

Shall I proceed with creating the KMP sample app?
