# macOS Native App - Implementation Notes

## Overview

This document explains the implementation decisions for the native macOS demo app.

## Key Decision: SwiftUI Instead of Compose

### Why Not Compose Multiplatform UI?

After investigation, I found that Compose Multiplatform's native macOS support is **limited compared to iOS**:

1. **No `ComposeUIViewController` for macOS**: The `ComposeUIViewController` API that works beautifully for iOS/UIKit is not available for macOS/AppKit.

2. **No `application()` function for native**: The `application { }` and `Window()` APIs are only available in the JVM desktop target, not the native macOS target.

3. **Limited AppKit integration**: Unlike iOS where Compose has good UIKit interop, macOS native + Compose integration is still experimental.

### Available Options for macOS + Compose

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **JVM Desktop** (Recommended) | Use `demo/desktopApp` | Full Compose support, cross-platform | Requires JVM, larger bundle |
| **Native SwiftUI** (Implemented) | This module | Native app, smaller bundle | No Compose UI |
| **Wait for future support** | - | - | Not available today |

### Decision

I implemented a **native SwiftUI app** that:
- Demonstrates the Stellar SDK working on native macOS
- Shows Swift/Kotlin interop
- Provides a working, production-ready solution today
- Has a simple, clean UI that's easy to extend

## Technical Implementation

### Framework Structure

The shared module now supports macOS native:

```kotlin
// demo/shared/build.gradle.kts
listOf(
    macosX64(),
    macosArm64()
).forEach { macosTarget ->
    macosTarget.binaries.framework {
        baseName = "shared"
        isStatic = true
    }
}
```

### macOS Source Set

```kotlin
// demo/shared/src/macosMain/kotlin/com/soneso/demo/MainViewController.kt
package com.soneso.demo

import com.soneso.demo.stellar.KeyPairGenerationResult
import com.soneso.demo.stellar.generateRandomKeyPair
import com.soneso.stellar.sdk.KeyPair

/**
 * Bridge between Swift UI and Kotlin business logic for native macOS app.
 *
 * This approach allows:
 * - Native macOS app with SwiftUI
 * - Shared business logic from Kotlin
 * - Access to the Stellar SDK
 */
class MacOSBridge {
    /**
     * Generate a random Stellar keypair asynchronously.
     * Call this from Swift using async/await.
     */
    suspend fun generateKeypair(): KeyPair {
        return when (val result = generateRandomKeyPair()) {
            is KeyPairGenerationResult.Success -> result.keyPair
            is KeyPairGenerationResult.Error -> {
                throw Exception(result.message, result.exception)
            }
        }
    }
}
```

Note: Instead of exporting Compose components (which can't be used on native macOS), we provide a bridge class that exposes business logic methods that SwiftUI can call directly.

### Swift Implementation

The Swift app includes an extension to handle CharArray-to-String conversion for secret seeds:

```swift
// demo/macosApp/StellarDemo/StellarDemoApp.swift

// MARK: - Kotlin Interop Extensions
extension KeyPair {
    /// Convert Kotlin CharArray to Swift String
    /// The secret seed is kept as CharArray in the SDK for better security,
    /// so we only convert it to String in the UI layer when needed for display.
    func getSecretSeedAsString() -> String? {
        guard let charArray = getSecretSeed() else { return nil }
        var characters: [Character] = []
        for i in 0..<charArray.size {
            let unicodeValue = UInt16(charArray.get(index: i))
            if let scalar = UnicodeScalar(unicodeValue) {
                characters.append(Character(scalar))
            }
        }
        return String(characters)
    }
}
```

The app uses MacOSBridge to call Kotlin business logic:

```swift
struct KeyGenerationScreen: View {
    private let bridge = MacOSBridge()

    private func generateKeypair() {
        Task {
            do {
                let keypair = try await bridge.generateKeypair()
                // Use keypair.getAccountId() for public key
                // Use keypair.getSecretSeedAsString() for secret seed display
            } catch {
                // Handle error
            }
        }
    }
}
```

### Build Integration

The Xcode project is configured to automatically build the Kotlin framework:

```yaml
# demo/macosApp/project.yml
preBuildScripts:
  - script: |
      cd "$SRCROOT/../../.."
      if [ "$(uname -m)" = "arm64" ]; then
        ./gradlew :demo:shared:linkDebugFrameworkMacosArm64
      else
        ./gradlew :demo:shared:linkDebugFrameworkMacosX64
      fi
    name: Build Kotlin Framework
```

## What Was Built

### 1. Shared Module Updates

- ✅ Added `macosX64()` and `macosArm64()` targets
- ✅ Created `macosMain` source set
- ✅ Added framework configuration
- ✅ Created `MainViewController.kt` with `MacOSBridge` class for Swift interop

### 2. macOS App Module

- ✅ Created `demo/macosApp/` directory structure
- ✅ Created `project.yml` for xcodegen
- ✅ Created `StellarDemoApp.swift` with SwiftUI implementation
  - Material 3-inspired design matching other platform UIs
  - KeyPair extension for CharArray-to-String conversion
  - Toast notifications for user feedback
  - Full key generation screen with show/hide secret functionality
- ✅ Created `Info.plist` for macOS app configuration
- ✅ Created `build.gradle.kts` with helper tasks
- ✅ Created comprehensive `README.md`

### 3. Settings Updates

- ✅ Added `:demo:macosApp` to `settings.gradle.kts`

## Verification

The implementation was tested:

1. ✅ Kotlin framework builds successfully:
   ```bash
   ./gradlew :demo:shared:linkDebugFrameworkMacosArm64
   # BUILD SUCCESSFUL in 2m 31s
   ```

2. ✅ Xcode project generates successfully:
   ```bash
   cd demo/macosApp && xcodegen generate
   # Created project at .../StellarDemo.xcodeproj
   ```

## Comparing to iOS App

| Aspect | iOS App | macOS App |
|--------|---------|-----------|
| UI Framework | Compose (via ComposeUIViewController) | SwiftUI (native) |
| Wrapper | UIViewControllerRepresentable | Native SwiftUI App |
| Framework API | ComposeUIViewController | MacOSBridge class |
| CharArray handling | In Kotlin via getSecretSeedString() | Swift extension getSecretSeedAsString() |
| Deployment Target | iOS 14.0+ | macOS 11.0+ |
| Dependencies | Swift Package Manager (Clibsodium) | Homebrew (libsodium) |

## Future Improvements

### When Compose Native macOS Improves

If Compose Multiplatform adds better native macOS support in the future, we could:

1. Implement `ComposeNSViewController` (if it becomes available)
2. Use Skiko for direct rendering
3. Share the Compose UI code with iOS/Android/Web

### Current Workaround

For now, developers have two good options:
- Use the **JVM desktopApp** for Compose UI on macOS
- Use this **native macosApp** for true native integration

## Lessons Learned

1. **Compose Multiplatform maturity varies by platform**: iOS has excellent support, macOS native is still catching up

2. **JVM desktop is the official recommendation**: For Compose on macOS, the JVM desktop target is production-ready

3. **Native integration is valuable**: Even without Compose UI, having a native macOS app that uses the KMP SDK is useful

4. **SwiftUI is a good alternative**: For native macOS apps, SwiftUI provides a modern, declarative UI framework

## Recommendations

### For Production Apps

- **Need Compose UI on macOS?** → Use JVM `desktopApp`
- **Need native macOS with smaller bundle?** → Use this `macosApp` with SwiftUI
- **Want to share UI code across all platforms?** → Use JVM desktop for macOS, it works great

### For This Demo

The demo now showcases:
- ✅ Android: Jetpack Compose
- ✅ iOS: Compose Multiplatform
- ✅ Desktop (JVM): Compose Multiplatform (works on macOS/Windows/Linux)
- ✅ macOS (Native): SwiftUI + KMP SDK
- ✅ Web: Compose for Web

This provides a complete picture of KMP + Compose capabilities across all platforms.

## References

- [Compose Multiplatform Targets](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-target-platforms.html)
- [Compose for Desktop Documentation](https://github.com/JetBrains/compose-multiplatform#desktop)
- [KMP Native Development](https://kotlinlang.org/docs/native-get-started.html)
- [Xcodegen Documentation](https://github.com/yonaskolb/XcodeGen)

## Conclusion

While we couldn't use Compose UI for native macOS (due to platform limitations), we successfully created a working native macOS app that:
- Uses the Stellar KMP SDK
- Demonstrates Swift/Kotlin interop
- Provides a clean, native user experience
- Can be extended with full macOS capabilities

The JVM `desktopApp` remains the recommended way to use Compose on macOS.
