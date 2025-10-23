# Stellar SDK Demo - Android App

Android application demonstrating the Stellar SDK with Jetpack Compose UI.

## Overview

This Android app showcases the Stellar SDK's capabilities on Android, featuring:
- **Key Generation**: Generate and manage Stellar keypairs
- **Account Management**: Fund testnet accounts and fetch account details
- **Payments**: Send XLM and custom assets
- **Trustlines**: Establish trust to hold issued assets
- **Transaction Details**: Fetch and view transaction operations and events
- **Smart Contracts**: Fetch and parse Soroban contract details
- **Contract Deployment**: Upload and deploy WASM contracts to testnet
- **Contract Invocation**: Invoke smart contracts with type conversion and authorization

The app uses 100% shared Compose UI from the `demo:shared` module, with only minimal Android-specific entry point code.

## Architecture

```
┌──────────────────────────────────────────────┐
│         demo:shared (Kotlin)                 │
│  • All UI screens (Compose)                  │
│  • Business logic (Stellar SDK)              │
│  • Navigation (Voyager)                      │
│  • Material 3 theme                          │
└──────────────────────────────────────────────┘
                    ▼
┌──────────────────────────────────────────────┐
│      demo:androidApp (20 lines)              │
│  • MainActivity.kt                           │
│  • Sets up Activity Compose                  │
│  • Initializes clipboard                     │
└──────────────────────────────────────────────┘
```

### Code Distribution
- **Shared code**: ~95% (all UI and business logic)
- **Android-specific**: ~5% (MainActivity, AndroidManifest, clipboard)

## Prerequisites

### Development Tools
- **Android Studio**: Arctic Fox (2020.3.1) or newer
- **JDK**: 11 or higher
- **Gradle**: 8.5+ (included via wrapper)

### SDK Requirements
- **Min SDK**: API 24 (Android 7.0 Nougat)
- **Target SDK**: API 35 (Android 15)
- **Compile SDK**: API 35

### Device Requirements
- **Android Version**: 7.0 (Nougat) or higher
- **Architecture**: ARM64, ARMv7, or x86_64
- **Internet**: Required (connects to Stellar testnet)

## Building and Running

### Option 1: Android Studio (Recommended)

1. **Open the project**:
   - Open Android Studio
   - File → Open → Select `/path/to/kmp-stellar-sdk`
   - Wait for Gradle sync to complete

2. **Select configuration**:
   - Choose `:demo:androidApp` from the run configuration dropdown
   - Select a connected device or emulator

3. **Run**:
   - Click the Run button (▶) or press ⇧⌘R (macOS) / Shift+F10 (Windows/Linux)

### Option 2: Command Line

```bash
# Navigate to project root
cd /Users/chris/projects/Stellar/kmp/kmp-stellar-sdk

# Build debug APK
./gradlew :demo:androidApp:assembleDebug

# Output: demo/androidApp/build/outputs/apk/debug/androidApp-debug.apk

# Install on connected device
./gradlew :demo:androidApp:installDebug

# Build and install in one command
./gradlew :demo:androidApp:installDebug
```

### Build Variants

```bash
# Debug build (default)
./gradlew :demo:androidApp:assembleDebug

# Release build (requires signing configuration)
./gradlew :demo:androidApp:assembleRelease

# Install release build
./gradlew :demo:androidApp:installRelease
```

## Project Structure

```
androidApp/
├── src/
│   └── main/
│       ├── AndroidManifest.xml           # App manifest
│       ├── java/com/soneso/demo/android/
│       │   └── MainActivity.kt           # Entry point (20 lines)
│       └── res/
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── mipmap-*/                 # App icons
└── build.gradle.kts                      # Android build configuration
```

### Key Files

**MainActivity.kt** (Entry Point):
```kotlin
package com.soneso.demo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.soneso.demo.App
import com.soneso.demo.platform.initAndroidClipboard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize clipboard with application context
        initAndroidClipboard(this)

        setContent {
            App()  // Shared Compose UI from demo:shared
        }
    }
}
```

**build.gradle.kts** (Configuration):
```kotlin
android {
    namespace = "com.soneso.demo.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.soneso.demo.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation(project(":demo:shared"))  // All UI and logic
    implementation("androidx.activity:activity-compose:1.8.2")
}
```

## Features

All features are implemented in the shared module. The Android app provides:

### 1. Key Generation
- Generate Ed25519 keypairs with Android's SecureRandom
- Copy keys to system clipboard
- Sign and verify data

### 2. Fund Testnet Account
- Request XLM from Friendbot
- Display funding status
- Error handling

### 3. Fetch Account Details
- View account balances
- Display sequence number
- Show account flags

### 4. Trust Asset
- Establish trustlines
- Build and sign transactions
- Submit to Horizon

### 5. Send Payment
- Transfer XLM or custom assets
- Amount validation
- Transaction signing

### 6. Fetch Transaction Details
- Retrieve transaction information from Horizon or Soroban RPC
- Display operations and events
- View smart contract call data
- Human-readable SCVal formatting

### 7. Smart Contract Details
- Parse WASM contracts
- View contract metadata
- Display function specifications

### 8. Deploy Smart Contract
- Upload WASM contracts
- Deploy with constructor arguments
- Platform-specific resource loading

### 9. Invoke Hello World Contract
- Invoke deployed "Hello World" contract
- Map-based argument conversion
- Automatic type handling with funcResToNative()
- Demonstrates beginner-friendly contract invocation

### 10. Invoke Auth Contract
- Dynamic authorization handling
- Same-invoker vs different-invoker scenarios
- Conditional signing with needsNonInvokerSigningBy()
- Production-ready authorization patterns

## Technology Stack

### UI Layer
- **Jetpack Compose**: Modern declarative UI (from shared module)
- **Material 3**: Material Design 3 components
- **Voyager**: Navigation library
- **Activity Compose**: Compose integration with Activities

### Business Logic
- **Stellar SDK**: All Stellar functionality
- **Ktor Client**: HTTP networking
- **kotlinx.coroutines**: Async operations
- **kotlinx.serialization**: JSON parsing

### Android Components
- **AndroidX**: Core Android libraries
- **Activity**: ComponentActivity for Compose
- **AppCompat**: Compatibility library

## Permissions

The app requires the following permissions (declared in AndroidManifest.xml):

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

- **INTERNET**: Required to connect to Stellar Horizon and Soroban RPC

No other permissions are needed. The app does NOT request:
- Camera (no QR scanning)
- Storage (no file operations)
- Location
- Contacts

## Configuration

### Application ID
```kotlin
applicationId = "com.soneso.demo.android"
```

### Version Information
```kotlin
versionCode = 1        // Increment for each release
versionName = "1.0"    // Semantic version
```

### Network Configuration

The app connects to Stellar testnet by default:
- **Horizon**: `https://horizon-testnet.stellar.org`
- **Soroban RPC**: `https://soroban-testnet.stellar.org`

To change networks, modify the code in `demo/shared/src/commonMain/kotlin/com/soneso/demo/stellar/`.

## Testing

### Run on Device

1. **Enable Developer Mode**:
   - Settings → About Phone → Tap "Build Number" 7 times
   - Settings → Developer Options → Enable "USB Debugging"

2. **Connect device** via USB

3. **Install and run**:
   ```bash
   ./gradlew :demo:androidApp:installDebug
   ```

### Run on Emulator

1. **Create AVD** (Android Virtual Device):
   - Tools → Device Manager → Create Device
   - Choose device (e.g., Pixel 6)
   - Choose system image (API 24+)
   - Click Finish

2. **Run**:
   - Select emulator from device dropdown
   - Click Run (▶)

### Recommended Test Devices
- **Physical**: Any Android 7.0+ device
- **Emulator**: Pixel 6 with API 34 (Android 14)

## Debugging

### Logcat Viewing

```bash
# View all logs
adb logcat

# Filter by tag
adb logcat -s "StellarDemo"

# Clear logs
adb logcat -c
```

### In Android Studio
- View → Tool Windows → Logcat
- Filter by package name: `com.soneso.demo.android`

### Common Debug Tasks

**Check package installation**:
```bash
adb shell pm list packages | grep soneso
```

**Uninstall app**:
```bash
adb uninstall com.soneso.demo.android
```

**View app info**:
```bash
adb shell dumpsys package com.soneso.demo.android
```

## Troubleshooting

### Build Issues

**Gradle sync fails**:
```bash
./gradlew clean
./gradlew --refresh-dependencies
```

**Kotlin version mismatch**:
- Ensure all modules use the same Kotlin version (2.0+)
- Check `build.gradle.kts` in project root

**Out of memory during build**:
Add to `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m
```

### Installation Issues

**INSTALL_FAILED_INSUFFICIENT_STORAGE**:
- Free up space on device
- Uninstall old versions: `adb uninstall com.soneso.demo.android`

**INSTALL_FAILED_UPDATE_INCOMPATIBLE**:
- Uninstall existing app first
- Or change the `applicationId` in `build.gradle.kts`

**Device not detected**:
```bash
# Check connected devices
adb devices

# Restart ADB server
adb kill-server
adb start-server
```

### Runtime Issues

**App crashes on launch**:
- Check Logcat for stack traces
- Verify minimum SDK version (API 24+)
- Ensure internet connection is available

**Compose UI not rendering**:
- Update to latest Android Studio
- Update Compose version in shared module
- Clear build cache: `./gradlew clean`

**Network errors**:
- Check internet connection
- Verify Horizon URL is correct
- Check Android's Network Security Config

## Performance

### APK Size
- **Debug**: ~12 MB
- **Release** (with R8): ~8 MB
- **Release** (with ProGuard): ~6 MB

### Build Time (M1 Mac)
- **First build**: 2-3 minutes
- **Incremental build**: 10-30 seconds
- **Clean build**: 1-2 minutes

### Runtime Performance
- **Startup time**: 1-2 seconds (cold start)
- **Frame rate**: 60 FPS
- **Memory usage**: 80-120 MB

## Release Build

### Configure Signing

Create `keystore.properties` in project root:
```properties
storeFile=/path/to/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Add to `build.gradle.kts`:
```kotlin
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### Build Release APK

```bash
./gradlew :demo:androidApp:assembleRelease

# Output: demo/androidApp/build/outputs/apk/release/androidApp-release.apk
```

## Distribution

### Google Play Store

1. Create signed AAB (Android App Bundle):
   ```bash
   ./gradlew :demo:androidApp:bundleRelease
   ```

2. Upload to Google Play Console
3. Fill in store listing details
4. Submit for review

### Direct Distribution

1. Build signed APK:
   ```bash
   ./gradlew :demo:androidApp:assembleRelease
   ```

2. Distribute APK file directly
3. Users must enable "Install from Unknown Sources"

## Resources

### Android Documentation
- [Android Developers](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3](https://m3.material.io/)

### Stellar Resources
- [Stellar Documentation](https://developers.stellar.org/)
- [Horizon API](https://developers.stellar.org/api/horizon)
- [Soroban Documentation](https://soroban.stellar.org/)

### Project Documentation
- [Main Demo README](../README.md)
- [Shared Module](../shared/)
- [SDK Documentation](../../README.md)

## Support

For issues:
- **Android-specific**: Check this README and Android Studio logs
- **SDK functionality**: See main SDK documentation
- **Stellar protocol**: Visit [developers.stellar.org](https://developers.stellar.org/)

## License

Part of the Stellar KMP SDK project. See main repository for license details.
