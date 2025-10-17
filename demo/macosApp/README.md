# Stellar Demo - macOS Native App

Native macOS application demonstrating the Stellar SDK with **native SwiftUI**.

## Important Note

This is a **native SwiftUI app**, not a Compose Multiplatform UI app.

### Why Not Compose UI?

Compose Multiplatform's native macOS support via AppKit is currently limited. The APIs that work for iOS (`ComposeUIViewController`) are not available for native macOS.

### macOS Options

If you want to use Compose UI on macOS, you have two choices:

1. **JVM Desktop App** (Recommended) - See `/demo/desktopApp/`
   - Full Compose Multiplatform support
   - Cross-platform (works on macOS, Windows, Linux)
   - Uses the same Compose UI code as Android/iOS/Web
   - This is the official recommended approach

2. **Native SwiftUI App** (This Module)
   - True native macOS app
   - Native SwiftUI interface
   - Uses Stellar SDK business logic from KMP
   - Demonstrates Swift/Kotlin interop

## Architecture

This native macOS app uses:
- **Kotlin Multiplatform**: Shared Stellar SDK business logic
- **SwiftUI**: Native macOS user interface
- **AppKit**: Native macOS frameworks
- **Libsodium**: Cryptographic operations (via Homebrew)

## Prerequisites

1. **Xcode 15.0+**: Required for macOS development
   ```bash
   xcode-select --install
   ```

2. **xcodegen**: Project generation tool
   ```bash
   brew install xcodegen
   ```

3. **Libsodium**: Required by the Stellar SDK
   ```bash
   brew install libsodium
   ```

## Building and Running

### Option 1: Command Line (Recommended)

1. **Build the Kotlin framework**:
   ```bash
   cd /Users/chris/projects/Stellar/kmp/kmp-stellar-sdk

   # For Apple Silicon Macs
   ./gradlew :demo:shared:linkDebugFrameworkMacosArm64

   # For Intel Macs
   ./gradlew :demo:shared:linkDebugFrameworkMacosX64
   ```

2. **Generate and open Xcode project**:
   ```bash
   cd demo/macosApp
   xcodegen generate
   open StellarDemo.xcodeproj
   ```

3. **Run from Xcode**:
   - Select the "StellarDemo" scheme
   - Choose "My Mac" as the destination
   - Click Run (⌘R)

### Option 2: Using Gradle Task

```bash
cd /Users/chris/projects/Stellar/kmp/kmp-stellar-sdk
./gradlew :demo:macosApp:openXcode
```

This will build the framework and open Xcode automatically.

## Project Structure

```
macosApp/
├── StellarDemo/
│   ├── StellarDemoApp.swift    # Native SwiftUI app
│   └── Info.plist              # macOS app configuration
├── project.yml                  # xcodegen configuration
├── build.gradle.kts            # Gradle helper tasks
└── README.md                   # This file
```

## Features

This simple demo app demonstrates:

- **Generate Random Keypairs**: Create Stellar Ed25519 keypairs
- **Display Keys**: Show account ID (public key) and secret seed
- **Sign Data**: Sign test data with the keypair
- **Native Performance**: Full native macOS app performance
- **Swift/Kotlin Interop**: Call KMP SDK from Swift code

## How It Works

1. **Shared Module**:
   - Stellar SDK business logic is in the KMP shared module
   - macOS framework is built from `demo/shared/src/macosMain/`

2. **Native UI**:
   - `StellarDemoApp.swift` creates a native SwiftUI interface
   - Calls the Stellar SDK directly from Swift
   - Demonstrates async/await integration with KMP

3. **Build Process**:
   - Pre-build script runs Gradle to build the Kotlin framework
   - Xcode links the framework and embeds it in the app bundle
   - The app runs as a native macOS application

## Key Differences from iOS

- **UI Framework**: SwiftUI (not Compose) - by choice, not limitation
- **View Controller**: Not used - pure SwiftUI App
- **Window Management**: Native macOS window APIs
- **Deployment Target**: macOS 11.0
- **Dependencies**: Homebrew libsodium (iOS uses Swift Package Manager)

## Differences from Desktop App

The JVM `desktopApp` module:
- ✅ Uses Compose UI (same as Android/iOS/Web)
- ✅ Cross-platform (macOS/Windows/Linux)
- ✅ Full Compose Multiplatform feature set
- ❌ Requires JVM (larger bundle size)

This `macosApp` module:
- ✅ True native macOS app
- ✅ Smaller bundle size (no JVM)
- ✅ Native macOS integration
- ❌ Uses SwiftUI instead of Compose
- ❌ macOS-only (not cross-platform)

## Troubleshooting

### Framework Not Found
- Ensure you've built the framework first: `./gradlew :demo:shared:linkDebugFrameworkMacosArm64`
- Check that the framework path in `project.yml` is correct
- Clean and rebuild: `xcodegen generate` then clean build in Xcode (⇧⌘K)

### Libsodium Missing
- Install via Homebrew: `brew install libsodium`
- Verify installation: `brew list libsodium`
- The Stellar SDK requires libsodium for Ed25519 cryptography

### Build Script Fails
- Ensure you're in the project root when running Gradle
- Check that Java/Kotlin toolchain is properly configured
- Try a clean build: `./gradlew clean :demo:shared:linkDebugFrameworkMacosArm64`

### Architecture Mismatch
- On Apple Silicon: Use `linkDebugFrameworkMacosArm64`
- On Intel: Use `linkDebugFrameworkMacosX64`
- The pre-build script automatically detects your architecture

## Development Tips

- **Hot Reload**: Not available - restart the app to see changes
- **Debugging**: Use Xcode's debugger for Swift, print statements for Kotlin
- **Testing**: Run unit tests with `./gradlew :demo:shared:macosArm64Test`
- **Profiling**: Use Xcode Instruments for performance profiling

## Comparing All macOS Options

| Feature | macosApp (This) | desktopApp | Web App |
|---------|----------------|------------|---------|
| UI Framework | SwiftUI | Compose | Compose |
| Runtime | Native | JVM | Browser |
| Bundle Size | Small | Large | N/A |
| Compose UI | ❌ | ✅ | ✅ |
| Native APIs | ✅ | Limited | ❌ |
| Cross-platform | ❌ | ✅ | ✅ |

## Recommendation

For most use cases, use the **JVM desktopApp** if you want Compose UI on macOS.

Use this **native macosApp** only if you specifically need:
- Native macOS integration
- Smaller bundle size
- Direct AppKit access
- And are willing to build a native SwiftUI UI

## Next Steps

- Extend the SwiftUI interface with more features
- Add macOS-specific features (menu bar, dock, notifications)
- Implement proper error handling and loading states
- Create release builds and notarize for App Store distribution
- Consider migrating to `desktopApp` if you want Compose UI
