# Stellar SDK Demo - iOS App

iOS application demonstrating the Stellar SDK with SwiftUI and Compose Multiplatform integration.

## Overview

This iOS app showcases the Stellar SDK's capabilities on iOS, featuring:
- **Key Generation**: Generate and manage Stellar keypairs
- **Account Management**: Fund testnet accounts and fetch account details
- **Payments**: Send XLM and custom assets
- **Trustlines**: Establish trust to hold issued assets
- **Smart Contracts**: Fetch and parse Soroban contract details
- **Contract Deployment**: Upload and deploy WASM contracts to testnet

The app uses 100% shared Compose UI from the `demo:shared` module, wrapped in a minimal SwiftUI container.

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
│      demo:iosApp (28 lines Swift)            │
│  • StellarDemoApp.swift                      │
│  • UIViewControllerRepresentable wrapper     │
│  • Launches Compose UI                       │
└──────────────────────────────────────────────┘
```

### Code Distribution
- **Shared code**: ~98% (all UI and business logic in Kotlin)
- **iOS-specific**: ~2% (SwiftUI wrapper only)

### How It Works

1. **SwiftUI Entry**: `StellarDemoApp` is the SwiftUI app entry point
2. **Compose Wrapper**: `ComposeView` wraps the Kotlin Compose UI using `UIViewControllerRepresentable`
3. **View Controller**: Calls `MainViewController()` from the shared Kotlin framework
4. **Compose UI**: Renders the full Compose Multiplatform UI in the iOS app

## Prerequisites

### Development Tools
- **macOS**: Required for iOS development
- **Xcode**: 15.0 or newer
- **xcodegen**: Project generator
  ```bash
  brew install xcodegen
  ```
- **CocoaPods** or **Swift Package Manager**: For libsodium dependency

### SDK Requirements
- **Deployment Target**: iOS 14.0 or higher
- **Swift**: 5.9+
- **Kotlin**: 2.0+ (for building the framework)

### Device/Simulator Requirements
- **iOS**: 14.0 or higher
- **Architectures**: arm64 (device), x86_64 or arm64 (simulator)
- **Internet**: Required (connects to Stellar testnet)

## Building and Running

### Step 1: Build the Kotlin Framework

From project root:

```bash
# For iOS Simulator (Apple Silicon Mac)
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64

# For iOS Simulator (Intel Mac)
./gradlew :demo:shared:linkDebugFrameworkIosX64

# For iOS Device
./gradlew :demo:shared:linkDebugFrameworkIosArm64
```

**Note**: The pre-build script in Xcode automatically builds the framework, but building it manually first can help troubleshoot issues.

### Step 2: Generate Xcode Project

```bash
cd demo/iosApp
xcodegen generate
```

This creates `StellarDemo.xcodeproj` from `project.yml`.

### Step 3: Open in Xcode

```bash
open StellarDemo.xcodeproj
```

Or: Double-click `StellarDemo.xcodeproj` in Finder

### Step 4: Run

In Xcode:
1. **Select scheme**: "StellarDemo" (should be selected by default)
2. **Select destination**:
   - iOS Simulator (e.g., iPhone 15 Pro)
   - Your connected iOS device
3. **Run**: Click the Play button (▶) or press ⌘R

### Automatic Framework Build

The Xcode project includes a pre-build script that automatically builds the Kotlin framework:

```bash
cd "$SRCROOT/../../.."
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64
```

This runs before every build, ensuring the framework is up-to-date.

## Project Structure

```
iosApp/
├── StellarDemo/
│   ├── StellarDemoApp.swift        # SwiftUI app entry (28 lines)
│   └── Info.plist                  # iOS app configuration
├── project.yml                      # xcodegen configuration
├── StellarDemo.xcodeproj/          # Generated Xcode project (gitignored)
└── build/                           # Build outputs (gitignored)
```

### Key Files

**StellarDemoApp.swift** (Entry Point):
```swift
import SwiftUI
import shared

@main
struct StellarDemoApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
```

**project.yml** (Xcodegen Configuration):
```yaml
name: StellarDemo
options:
  bundleIdPrefix: com.soneso.demo
  deploymentTarget:
    iOS: 14.0
targets:
  StellarDemo:
    type: application
    platform: iOS
    sources:
      - StellarDemo
    dependencies:
      - framework: ../shared/build/bin/iosSimulatorArm64/debugFramework/shared.framework
        embed: true
    preBuildScripts:
      - script: |
          cd "$SRCROOT/../../.."
          ./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64
        name: Build Kotlin Framework
```

## Features

All features are implemented in the shared Kotlin module:

### 1. Key Generation
- Generate Ed25519 keypairs with iOS's SecureRandom (via libsodium)
- Copy keys to iOS clipboard
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

### 6. Smart Contract Details
- Parse WASM contracts
- View contract metadata
- Display function specifications

### 7. Deploy Smart Contract
- Upload and deploy WASM contracts from iOS
- Platform-specific resource loading from bundle
- One-step and two-step deployment support

## Technology Stack

### iOS Layer
- **SwiftUI**: Modern declarative UI for app wrapper
- **UIKit**: UIViewController for Compose integration
- **UIViewControllerRepresentable**: Bridge between SwiftUI and Compose

### Shared Layer (Kotlin)
- **Compose Multiplatform**: All UI screens and navigation
- **Material 3**: Material Design 3 components
- **Voyager**: Navigation library
- **Stellar SDK**: All Stellar functionality

### Cryptography
- **libsodium**: Ed25519 cryptography (via Swift Package Manager)
  - Package: `swift-sodium` (provides both `Clibsodium` and `Sodium` products)
  - Source: jedisct1/swift-sodium
  - **Clibsodium**: C library used by Kotlin/Native code
  - **Sodium**: Swift wrapper (optional, for Swift code)

## Dependencies

### Swift Package Manager

Add libsodium to your project:

1. **File → Add Packages...**
2. **Search**: `https://github.com/jedisct1/swift-sodium.git`
3. **Add Package**: "Up to Next Major Version" from 0.9.1
4. **Products**: Both `Clibsodium` and `Sodium` will be added automatically (you cannot choose individual products)

**Note**: The `swift-sodium` package provides two products:
- **Clibsodium**: Required by the Kotlin/Native framework
- **Sodium**: Swift wrapper (not used by this demo, but included automatically)

Or add to `Package.swift`:
```swift
dependencies: [
    .package(url: "https://github.com/jedisct1/swift-sodium.git", from: "0.9.1")
]
```

### Embedded Framework

The Kotlin framework is embedded in the app bundle:
- **Debug**: `shared.framework` from `debugFramework/`
- **Release**: `shared.framework` from `releaseFramework/`

Framework includes:
- All shared Compose UI code
- Stellar SDK functionality
- Navigation and theming

## Configuration

### Bundle Identifier
```yaml
bundleIdPrefix: com.soneso.demo
# Full ID: com.soneso.demo.StellarDemo
```

### Version Information
```yaml
CFBundleShortVersionString: 1.0  # User-facing version
CFBundleVersion: 1               # Build number
```

### Deployment Target
```yaml
deploymentTarget:
  iOS: 14.0
```

### Supported Orientations
```yaml
UISupportedInterfaceOrientations:
  - UIInterfaceOrientationPortrait
  - UIInterfaceOrientationLandscapeLeft
  - UIInterfaceOrientationLandscapeRight
```

## Network Configuration

The app connects to Stellar testnet by default:
- **Horizon**: `https://horizon-testnet.stellar.org`
- **Soroban RPC**: `https://soroban-testnet.stellar.org`

### App Transport Security

No special configuration needed. HTTPS is used by default.

If needed, add to `Info.plist`:
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <false/>
</dict>
```

## Testing

### Run on Simulator

1. **Select simulator**: Product → Destination → iOS Simulator → iPhone 15 Pro
2. **Run**: ⌘R
3. **Test features**: All 6 demo features should work on simulator

### Run on Device

1. **Connect device** via USB or WiFi
2. **Select team**: Signing & Capabilities → Team → Select your Apple ID
3. **Select device**: Product → Destination → Your Device Name
4. **Trust computer**: Unlock device and tap "Trust"
5. **Run**: ⌘R
6. **Trust developer**: Settings → General → VPN & Device Management → Trust

### Recommended Test Devices
- **Simulator**: iPhone 15 Pro (iOS 17.0+)
- **Physical**: iPhone 8 or newer (iOS 14.0+)

## Debugging

### Xcode Console

View logs in Xcode's console:
- **Show console**: View → Debug Area → Activate Console (⌘⇧Y)
- **Filter logs**: Type in the filter box (e.g., "Stellar", "KeyPair")

### LLDB Debugging

Set breakpoints in Swift code:
- Click line number in Swift files
- Run with debugger (⌘R)

**Note**: Cannot set breakpoints in Kotlin code from Xcode. Use print statements in Kotlin.

### Common Debug Commands

**Print view hierarchy**:
```lldb
po view.recursiveDescription()
```

**Print all logs**:
```bash
# In Terminal, while app is running
xcrun simctl spawn booted log stream --predicate 'processImagePath contains "StellarDemo"'
```

## Troubleshooting

### Build Issues

**Framework not found**:
```bash
# Rebuild the framework (from project root)
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64

# Regenerate Xcode project
cd demo/iosApp
xcodegen generate
```

**Pre-build script fails**:
- Check Gradle is accessible: `./gradlew --version`
- Ensure Java is installed: `java -version`
- Check script path in `project.yml` is correct

**Architecture mismatch**:
```bash
# Apple Silicon Simulator
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64

# Intel Simulator
./gradlew :demo:shared:linkDebugFrameworkIosX64

# Device (arm64 only)
./gradlew :demo:shared:linkDebugFrameworkIosArm64
```

### Xcode Issues

**"No such module 'shared'"**:
- Clean build folder: Product → Clean Build Folder (⇧⌘K)
- Build the framework first: `./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64`
- Check framework path in Build Settings → Framework Search Paths

**Code signing error**:
- Select your team in Signing & Capabilities
- Change bundle ID if needed to avoid conflicts
- Ensure device is registered in developer portal

**Simulator won't launch**:
```bash
# Reset simulator
xcrun simctl erase all

# Kill simulator processes
killall Simulator
```

### Runtime Issues

**App crashes on launch**:
- Check Xcode console for stack traces
- Verify iOS version is 14.0+
- Ensure framework architecture matches destination (simulator vs device)

**Compose UI not rendering**:
- Verify `MainViewController()` is called correctly
- Check framework is embedded properly
- Update Compose version in shared module

**Keyboard doesn't appear**:
- Hardware → Keyboard → Toggle Software Keyboard (⌘K)
- Or use physical keyboard: Hardware → Keyboard → Connect Hardware Keyboard

**Network errors**:
- Check internet connection
- Verify Horizon URL is correct
- Check App Transport Security settings

## Performance

### App Size
- **Debug**: ~15 MB
- **Release** (optimized): ~10 MB

### Build Time (M1 Mac)
- **First build**: 2-3 minutes (including framework)
- **Incremental build**: 10-30 seconds
- **Framework only**: 30-60 seconds

### Runtime Performance
- **Startup time**: 1-2 seconds (cold start)
- **Frame rate**: 60 FPS
- **Memory usage**: 60-100 MB

## Release Build

### Archive for Distribution

1. **Select device**: Generic iOS Device
2. **Archive**: Product → Archive
3. **Wait**: Xcode builds and archives
4. **Organizer**: Window → Organizer → Archives

### Export for App Store

1. **Select archive**: Choose latest archive
2. **Distribute App**: Click "Distribute App"
3. **Method**: App Store Connect
4. **Options**: Configure signing and options
5. **Upload**: Upload to App Store Connect

### Export for TestFlight

Same process as App Store, but available immediately in TestFlight after processing.

### Export for Ad Hoc

1. **Method**: Ad Hoc
2. **Export**: Save .ipa file
3. **Distribute**: Send to testers via email/link
4. **Install**: Use TestFlight, Xcode, or third-party tools

## Distribution

### App Store

1. **Create app** in App Store Connect
2. **Archive and upload** from Xcode
3. **Fill in metadata**: Screenshots, description, keywords
4. **Submit for review**
5. **Wait for approval** (1-3 days typically)

### TestFlight

1. **Upload build** to App Store Connect
2. **Add testers**: Internal or external
3. **Submit for beta review** (external testers only)
4. **Distribute**: Testers receive notification

### Enterprise Distribution

1. **Enroll in Apple Developer Enterprise Program**
2. **Archive with Enterprise certificate**
3. **Export for Enterprise Distribution**
4. **Host .ipa and manifest.plist**
5. **Distribute link**: `itms-services://?action=download-manifest&url=...`

## Xcodegen

This project uses **xcodegen** to generate the Xcode project from `project.yml`.

### Why xcodegen?

- **Version control friendly**: `.xcodeproj` is gitignored
- **No merge conflicts**: Xcode project is generated, not edited
- **Declarative configuration**: Simple YAML instead of complex XML
- **Reproducible builds**: Same project every time

### Regenerate Project

```bash
cd demo/iosApp
xcodegen generate
```

Run this whenever you:
- Pull changes from git
- Modify `project.yml`
- Add/remove files
- Change build settings

### Customize Project

Edit `project.yml` to change:
- **Targets**: Add new targets
- **Build settings**: Compiler flags, optimization
- **Dependencies**: Frameworks, libraries
- **Scripts**: Pre/post build scripts

See [xcodegen documentation](https://github.com/yonaskolb/XcodeGen/blob/master/Docs/ProjectSpec.md) for all options.

## Resources

### iOS Documentation
- [iOS Developer](https://developer.apple.com/ios/)
- [SwiftUI](https://developer.apple.com/xcode/swiftui/)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)

### Stellar Resources
- [Stellar Documentation](https://developers.stellar.org/)
- [Horizon API](https://developers.stellar.org/api/horizon)
- [Soroban Documentation](https://soroban.stellar.org/)

### Project Documentation
- [Main Demo README](../README.md)
- [Shared Module](../shared/)
- [SDK Documentation](../../README.md)

### Tools
- [xcodegen](https://github.com/yonaskolb/XcodeGen)
- [swift-sodium](https://github.com/jedisct1/swift-sodium)

## Support

For issues:
- **iOS-specific**: Check this README and Xcode console
- **Framework build**: Check Gradle output and shared module
- **SDK functionality**: See main SDK documentation
- **Stellar protocol**: Visit [developers.stellar.org](https://developers.stellar.org/)

## License

Part of the Stellar KMP SDK project. See main repository for license details.
