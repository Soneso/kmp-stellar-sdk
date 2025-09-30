# Stellar KMP SDK - Android Sample App

This is a comprehensive Android integration test app for the Stellar KMP SDK. It demonstrates all major features of the SDK on Android and runs an extensive test suite.

## Features

### 🎯 Interactive UI (Jetpack Compose)
- **Generate Keypairs**: Create new Ed25519 keypairs with one tap
- **Copy Account IDs**: Easily copy generated account IDs to clipboard
- **Run Tests**: Execute comprehensive test suite from the app
- **View Results**: See detailed test results with timing information
- **Material Design 3**: Modern UI with light/dark theme support

### 🧪 Comprehensive Tests

The sample app includes 10 comprehensive tests:

1. **Random KeyPair Generation** - Verifies unique keypair generation
2. **KeyPair from Secret Seed** - Tests keypair derivation from known seed
3. **KeyPair from Account ID** - Tests public-only keypair creation
4. **Sign and Verify** - Tests digital signature creation and verification
5. **Cross-KeyPair Verification** - Tests verification with public-only keypairs
6. **Invalid Secret Seed** - Validates error handling for invalid seeds
7. **Invalid Account ID** - Validates error handling for invalid account IDs
8. **Memory Safety** - Stress tests with 100 keypairs
9. **Crypto Library Info** - Verifies crypto library detection
10. **Sign Without Private Key** - Tests error handling

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 24 or higher (minSdk 24)
- Gradle 8.0 or later
- Kotlin 1.9.22 or later

## Quick Start

### Option 1: Open in Android Studio (Recommended)

1. **Open Project:**
   - Open Android Studio
   - File → Open
   - Select the root project directory (`kmp-stellar-sdk`)
   - Wait for Gradle sync to complete

2. **Select Module:**
   - In the run configuration dropdown, select `androidSample`
   - Or create a new Android App configuration pointing to `androidSample`

3. **Run:**
   - Click the Run button (▶) or press Shift+F10
   - Select an emulator or connected device
   - The app will build and launch

### Option 2: Command Line Build

```bash
# From project root
./gradlew :androidSample:assembleDebug

# Install on connected device/emulator
./gradlew :androidSample:installDebug

# Or combined
./gradlew :androidSample:installDebug && adb shell am start -n com.stellar.androidsample/.MainActivity
```

## Project Structure

```
androidSample/
├── build.gradle.kts                    # Android app build configuration
├── src/
│   └── main/
│       ├── AndroidManifest.xml         # App manifest
│       ├── java/com/stellar/androidsample/
│       │   ├── MainActivity.kt         # Main activity with Compose UI
│       │   └── ui/theme/
│       │       └── Theme.kt            # Material Design 3 theme
│       └── res/
│           ├── values/
│           │   ├── strings.xml         # App strings
│           │   └── themes.xml          # XML theme
│           └── mipmap-*/               # App icons
└── README.md                            # This file
```

## Usage

### Generate a New Keypair

1. Tap "Generate New Keypair"
2. The account ID will be displayed in a card
3. Tap the account ID card to copy to clipboard

### Run Tests

1. Tap "Run Comprehensive Tests"
2. Wait for tests to complete (usually < 1 second)
3. View individual test results with pass/fail status
4. See detailed messages and timing for each test

## SDK Integration Examples

The app demonstrates how to:

### Import the SDK

```kotlin
import com.stellar.sdk.KeyPair
```

### Generate Keypairs

```kotlin
// Generate random keypair
val keypair = KeyPair.random()
val accountId = keypair.getAccountId()

// From secret seed
val keypair = KeyPair.fromSecretSeed(
    "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
)

// From account ID (public-only)
val keypair = KeyPair.fromAccountId(
    "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"
)
```

### Sign and Verify Messages

```kotlin
// Sign data
val message = "Hello Stellar".toByteArray()
val signature = keypair.sign(message)

// Verify
val isValid = keypair.verify(message, signature)
```

### Get Crypto Library Info

```kotlin
val cryptoLib = KeyPair.getCryptoLibraryName()
println("Using: $cryptoLib")  // Prints: "BouncyCastle"
```

## Architecture

### Cryptographic Implementation

The Android implementation uses **BouncyCastle** for cryptographic operations:

- **Algorithm**: Ed25519 (RFC 8032)
- **Library**: org.bouncycastle:bcprov-jdk18on (1.78.1)
- **APK Size Impact**: ~2.8 MB (included in SDK)
- **Distribution**: Standard Gradle dependency

### Why BouncyCastle?

- ✅ **Production-proven**: Same library used by Java Stellar SDK
- ✅ **Cross-platform**: Consistent Ed25519 across JVM platforms
- ✅ **Audited**: Security-reviewed, RFC 8032 compliant
- ✅ **Performance**: Optimized for JVM
- ✅ **Ecosystem Standard**: Used throughout Stellar ecosystem

### UI Framework

The sample app uses **Jetpack Compose** with Material Design 3:

- Modern declarative UI
- Built-in theming support
- Smooth animations
- Efficient recomposition

## Troubleshooting

### Gradle Sync Failed

If Gradle sync fails:

1. Check that you're using Android Studio Hedgehog or later
2. Ensure Gradle wrapper is up to date
3. File → Invalidate Caches → Invalidate and Restart
4. Sync Project with Gradle Files

### SDK Not Found

If you see "Unresolved reference: KeyPair":

1. Ensure the stellar-sdk module is included in settings.gradle.kts
2. Check that androidSample depends on stellar-sdk in build.gradle.kts
3. File → Sync Project with Gradle Files

### Build Errors

If you encounter build errors:

1. Clean the project: Build → Clean Project
2. Rebuild: Build → Rebuild Project
3. Check minimum SDK version is set to 24
4. Verify Kotlin version matches the multiplatform module

### Runtime Errors

If the app crashes on launch:

1. Check Logcat for error messages
2. Ensure your device/emulator is running Android 7.0 (API 24) or higher
3. Verify the stellar-sdk module is properly built

## Performance

Expected performance on typical Android device:

- **Keypair Generation**: ~5-10ms
- **Sign Operation**: ~5-10ms
- **Verify Operation**: ~5-10ms
- **Full Test Suite**: < 1 second

## Security Notes

- Private keys are stored securely in memory
- Keys are defensively copied to prevent external modification
- BouncyCastle uses constant-time operations to prevent timing attacks
- Memory is properly managed by the JVM

## Distribution

This sample app demonstrates the **recommended integration approach** for Android:

1. **Gradle Dependency**: Add stellar-sdk to your build.gradle.kts
   ```kotlin
   dependencies {
       implementation(project(":stellar-sdk"))
       // Or when published:
       // implementation("com.stellar:kmp-sdk:0.1.0")
   }
   ```

2. **No Additional Configuration**: BouncyCastle is included automatically

3. **ProGuard/R8**: The SDK is minification-safe

## Building for Release

```bash
# Build release APK
./gradlew :androidSample:assembleRelease

# Build Android App Bundle (for Play Store)
./gradlew :androidSample:bundleRelease
```

### ProGuard Rules

If you encounter issues with ProGuard/R8, add these rules to `proguard-rules.pro`:

```proguard
# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Stellar SDK
-keep class com.stellar.sdk.** { *; }
```

## Testing

### Run Unit Tests

```bash
./gradlew :androidSample:testDebugUnitTest
```

### Run Instrumented Tests

```bash
./gradlew :androidSample:connectedAndroidTest
```

## Related Documentation

- [Main Project README](../README.md)
- [Crypto Implementation](../CRYPTO_IMPLEMENTATIONS.md)
- [iOS Sample App](../iosSample/README.md)

## Comparison with iOS

| Feature | Android | iOS |
|---------|---------|-----|
| **Crypto Library** | BouncyCastle | libsodium |
| **UI Framework** | Jetpack Compose | SwiftUI |
| **Distribution** | Gradle dependency | XCFramework + SPM |
| **APK/IPA Impact** | ~3 MB | ~7 MB |
| **Min Version** | Android 7.0 (API 24) | iOS 13.0 |

Both implementations provide the same Ed25519 functionality with platform-native optimizations.

## License

Same as parent project (Stellar KMP SDK) - Apache 2.0

## Support

- **GitHub Issues**: https://github.com/stellar/kmp-stellar-sdk/issues
- **Stellar Development**: https://developers.stellar.org
- **Stellar Discord**: https://discord.gg/stellar
