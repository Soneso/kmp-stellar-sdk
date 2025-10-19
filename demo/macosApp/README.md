# Stellar SDK Demo - macOS Native App

Native macOS application demonstrating the Stellar SDK with **SwiftUI** and complete integration with all 6 demo features.

## Overview

This is a **native macOS app** built with SwiftUI (not Compose), showcasing the full Stellar SDK feature set:
- **Key Generation**: Generate and manage Stellar keypairs
- **Account Management**: Fund testnet accounts and fetch account details
- **Payments**: Send XLM and custom assets
- **Trustlines**: Establish trust to hold issued assets
- **Smart Contracts**: Fetch and parse Soroban contract details

The app demonstrates how to integrate the Kotlin Multiplatform Stellar SDK into a pure SwiftUI macOS application.

## Architecture

```
┌──────────────────────────────────────────────┐
│      demo:shared (Kotlin Framework)          │
│  • Stellar SDK business logic                │
│  • Exported from KMP shared module           │
│  • No Compose UI (macOS-specific)            │
└──────────────────────────────────────────────┘
                    ▼
┌──────────────────────────────────────────────┐
│    demo:macosApp (17 Swift files)            │
│  • Native SwiftUI UI (Material 3 design)     │
│  • 7 Views (one per screen)                  │
│  • 6 Components (reusable UI)                │
│  • 3 Utilities (helpers)                     │
└──────────────────────────────────────────────┘
```

### Why SwiftUI Instead of Compose?

**Short answer**: Compose Multiplatform's native macOS support is limited. The Desktop (JVM) version is recommended for Compose UI on macOS.

**Two options for macOS**:

| Feature | Desktop App (JVM) | macOS Native App |
|---------|-------------------|------------------|
| UI Framework | Compose Multiplatform | SwiftUI |
| Code Sharing | 100% shared UI | Business logic only |
| Bundle Size | ~35 MB (includes JVM) | ~8 MB (native) |
| Performance | Good | Excellent |
| Platform APIs | Limited | Full AppKit access |
| Cross-platform | macOS/Windows/Linux | macOS only |
| **Recommendation** | ✅ For Compose UI | For native experience |

### Recent Refactoring

The macOS app was recently refactored from a single file to a well-structured project:

**Before**: 1 monolithic Swift file
**After**: 17 organized Swift files:
- **7 Views**: Dedicated view for each demo feature
- **6 Components**: Reusable UI components
- **3 Utilities**: Helper classes
- **1 App**: Main entry point

## Project Structure

```
macosApp/
├── StellarDemo/
│   ├── StellarDemoApp.swift       # App entry point
│   ├── Views/                     # 7 SwiftUI views
│   │   ├── MainScreen.swift       # Main menu
│   │   ├── KeyGenerationView.swift
│   │   ├── FundAccountView.swift
│   │   ├── AccountDetailsView.swift
│   │   ├── TrustAssetView.swift
│   │   ├── SendPaymentView.swift
│   │   └── ContractDetailsView.swift
│   ├── Components/                # 6 reusable components
│   │   ├── DemoTopicCard.swift    # Card for main menu
│   │   ├── KeyPairComponents.swift
│   │   ├── AccountComponents.swift
│   │   ├── TrustAssetComponents.swift
│   │   ├── PaymentComponents.swift
│   │   └── ContractComponents.swift
│   └── Utilities/                 # 3 utility classes
│       ├── Material3Colors.swift  # Material 3 color scheme
│       ├── ToastManager.swift     # Toast notifications
│       └── KeyPairExtension.swift # KeyPair helpers
├── project.yml                    # xcodegen configuration
├── build.gradle.kts               # Gradle helper tasks
├── StellarDemo.xcodeproj/         # Generated (gitignored)
└── README.md                      # This file
```

## Prerequisites

### Development Tools
- **macOS**: 13.0 (Ventura) or newer
- **Xcode**: 15.0 or newer
- **xcodegen**: Project generator
  ```bash
  brew install xcodegen
  ```
- **libsodium**: Required by Stellar SDK
  ```bash
  brew install libsodium
  ```

### SDK Requirements
- **Deployment Target**: macOS 13.0 or higher
- **Swift**: 5.9+
- **Kotlin**: 2.0+ (for building the framework)

## Building and Running

### Step 1: Install Dependencies

```bash
# Install xcodegen (project generator)
brew install xcodegen

# Install libsodium (required by Stellar SDK)
brew install libsodium

# Verify installations
xcodegen --version
brew list libsodium
```

### Step 2: Build the Kotlin Framework

From project root:

```bash
cd /Users/chris/projects/Stellar/kmp/kmp-stellar-sdk

# For Apple Silicon (M1/M2/M3)
./gradlew :demo:shared:linkDebugFrameworkMacosArm64

# For Intel Macs
./gradlew :demo:shared:linkDebugFrameworkMacosX64
```

**Or use the Gradle helper task**:
```bash
./gradlew :demo:macosApp:buildFramework
```

This automatically detects your architecture and builds the correct framework.

### Step 3: Generate Xcode Project

```bash
cd demo/macosApp
xcodegen generate
```

This creates `StellarDemo.xcodeproj` from `project.yml`.

### Step 4: Open in Xcode

```bash
open StellarDemo.xcodeproj
```

**Or use the all-in-one Gradle task**:
```bash
cd /Users/chris/projects/Stellar/kmp/kmp-stellar-sdk
./gradlew :demo:macosApp:openXcode
```

This builds the framework, generates the project, and opens Xcode.

### Step 5: Run

In Xcode:
1. **Select scheme**: "StellarDemo" (should be selected by default)
2. **Select destination**: "My Mac"
3. **Run**: Click the Play button (▶) or press ⌘R

## Features

All 6 demo features are fully implemented in SwiftUI:

### 1. Key Generation
- **View**: `KeyGenerationView.swift`
- **Component**: `KeyPairComponents.swift`
- Generate random Stellar keypairs
- Display account ID (G...) and secret seed (S...)
- Copy to clipboard (macOS pasteboard)
- Sign and verify test data
- Uses: `KeyPair.random()` from Stellar SDK

### 2. Fund Testnet Account
- **View**: `FundAccountView.swift`
- Request XLM from Friendbot
- Real-time funding status with toast notifications
- Error handling for invalid accounts
- Uses: `FriendBot.fundTestnetAccount()` from Stellar SDK

### 3. Fetch Account Details
- **View**: `AccountDetailsView.swift`
- **Component**: `AccountComponents.swift`
- Retrieve account information from Horizon
- Display all balances (XLM and issued assets)
- Show sequence number and flags
- Uses: `Server.accounts()` from Stellar SDK

### 4. Trust Asset
- **View**: `TrustAssetView.swift`
- **Component**: `TrustAssetComponents.swift`
- Establish trustlines for issued assets
- Support for custom asset codes and issuers
- Build, sign, and submit transactions
- Uses: `ChangeTrustOperation` from Stellar SDK

### 5. Send Payment
- **View**: `SendPaymentView.swift`
- **Component**: `PaymentComponents.swift`
- Transfer XLM or issued assets
- Support for native and custom assets
- Amount validation and transaction signing
- Uses: `PaymentOperation` from Stellar SDK

### 6. Fetch Smart Contract Details
- **View**: `ContractDetailsView.swift`
- **Component**: `ContractComponents.swift`
- Parse WASM contracts to view metadata
- Display contract specification (functions, types)
- View contract code hash
- Uses: Soroban RPC integration from Stellar SDK

## Design System

The app uses a **Material 3-inspired color scheme** adapted for macOS:

### Material3Colors.swift

```swift
// Primary colors matching Material 3
static let primary = Color(red: 0.38, green: 0.49, blue: 0.54)
static let onPrimary = Color.white
static let primaryContainer = Color(red: 0.78, green: 0.85, blue: 0.88)
static let onPrimaryContainer = Color(red: 0.05, green: 0.17, blue: 0.21)

// Surface colors
static let surface = Color(NSColor.controlBackgroundColor)
static let onSurface = Color(NSColor.labelColor)
```

This provides:
- Consistent design across demo apps
- Proper dark mode support
- Native macOS integration
- Accessible color contrasts

## Components

### Reusable UI Components

**DemoTopicCard**: Card component for main menu
```swift
DemoTopicCard(
    title: "Key Generation",
    description: "Generate and manage Stellar keypairs",
    icon: "key.fill"
) {
    // Navigation action
}
```

**ToastManager**: macOS-native toast notifications
```swift
ToastManager.shared.show(message: "Account funded successfully!", isError: false)
```

**KeyPairExtension**: Helper methods for KeyPair
```swift
extension KeyPair {
    func formattedAccountId() -> String
    func formattedSecretSeed() -> String
}
```

## Technology Stack

### macOS Layer
- **SwiftUI**: Declarative UI framework
- **AppKit**: Native macOS frameworks
- **Combine**: Reactive programming (for async calls)
- **Foundation**: Core utilities

### Kotlin Framework
- **Stellar SDK**: All Stellar functionality
- **Ktor Client**: HTTP networking
- **kotlinx.serialization**: JSON parsing
- **kotlinx.coroutines**: Async operations

### Cryptography
- **libsodium**: Ed25519 cryptography (via Homebrew)
  - Installed system-wide: `/opt/homebrew/opt/libsodium/`
  - Linked in Xcode project settings

## Configuration

### Bundle Identifier
```yaml
bundleIdPrefix: com.soneso.demo
# Full ID: com.soneso.demo.StellarDemo
```

### Version Information
```yaml
MARKETING_VERSION: "1.0"          # User-facing version
CURRENT_PROJECT_VERSION: "1"      # Build number
```

### Deployment Target
```yaml
deploymentTarget:
  macOS: 13.0
```

### Framework Search Paths
The Xcode project is configured to find the Kotlin framework:
```yaml
FRAMEWORK_SEARCH_PATHS:
  - $(SRCROOT)/../../shared/build/bin/macosArm64/debugFramework
  - $(SRCROOT)/../../shared/build/bin/macosX64/debugFramework
```

### libsodium Configuration
```yaml
LIBRARY_SEARCH_PATHS:
  - /opt/homebrew/opt/libsodium/lib
HEADER_SEARCH_PATHS:
  - /opt/homebrew/opt/libsodium/include
OTHER_LDFLAGS:
  - -lsodium
```

## Network Configuration

The app connects to Stellar testnet:
- **Horizon**: `https://horizon-testnet.stellar.org`
- **Soroban RPC**: `https://soroban-testnet.stellar.org`

To change networks, modify the SDK initialization in the Kotlin framework.

## Testing

### Run in Xcode

1. **Build**: ⌘B
2. **Run**: ⌘R
3. **Test all features**: Navigate through all 6 demo screens

### Manual Testing Checklist

- [ ] Key Generation: Generate keypair, copy to clipboard
- [ ] Fund Account: Fund a testnet account
- [ ] Account Details: Fetch and display account info
- [ ] Trust Asset: Establish a trustline
- [ ] Send Payment: Send XLM to another account
- [ ] Contract Details: Fetch contract metadata

### Network Testing

Test with different network conditions:
- Normal network
- Slow network (Network Link Conditioner)
- No network (verify error handling)

## Debugging

### Xcode Console

View logs in Xcode's console:
- **Show console**: View → Debug Area → Activate Console (⌘⇧Y)
- **Filter logs**: Type in filter box

### Print Debugging

Add print statements in Swift:
```swift
print("Debug: \(variableName)")
```

In Kotlin (shared framework):
```kotlin
println("Debug: $variableName")
```

### Breakpoints

Set breakpoints in Swift code:
- Click line number to add breakpoint
- Run with debugger (⌘R)
- Inspect variables in Debug Navigator

**Note**: Cannot debug Kotlin code from Xcode. Use print statements.

### LLDB Commands

Useful LLDB commands in console:
```lldb
po variableName              # Print object
frame variable               # Show all variables
bt                          # Backtrace
continue                    # Continue execution
```

## Troubleshooting

### Build Issues

**Framework not found**:
```bash
# Rebuild the framework
cd /Users/chris/projects/Stellar/kmp/kmp-stellar-sdk
./gradlew :demo:shared:linkDebugFrameworkMacosArm64

# Regenerate Xcode project
cd demo/macosApp
xcodegen generate
```

**libsodium not found**:
```bash
# Install libsodium
brew install libsodium

# Verify installation
brew list libsodium

# Check library exists
ls /opt/homebrew/opt/libsodium/lib/libsodium.dylib
```

**Architecture mismatch**:
```bash
# Check your Mac's architecture
uname -m
# arm64 = Apple Silicon
# x86_64 = Intel

# Build for Apple Silicon
./gradlew :demo:shared:linkDebugFrameworkMacosArm64

# Build for Intel
./gradlew :demo:shared:linkDebugFrameworkMacosX64
```

### Xcode Issues

**"No such module 'shared'"**:
- Clean build folder: Product → Clean Build Folder (⇧⌘K)
- Build framework first: `./gradlew :demo:shared:linkDebugFrameworkMacosArm64`
- Check Framework Search Paths in Build Settings

**Code signing error**:
- Signing & Capabilities → Select your team
- Or disable signing for development: Build Settings → Code Signing → Sign to Run Locally

**xcodegen not found**:
```bash
brew install xcodegen
# Add to PATH if needed
echo 'export PATH="/opt/homebrew/bin:$PATH"' >> ~/.zshrc
```

### Runtime Issues

**App crashes on launch**:
- Check Xcode console for stack traces
- Verify macOS version is 13.0+
- Ensure libsodium is installed

**SwiftUI previews fail**:
- SwiftUI previews don't work with KMP frameworks
- Use the full app for testing instead
- Or comment out framework imports for preview-only code

**Network errors**:
- Check internet connection
- Verify Horizon/Soroban URLs are correct
- Check macOS firewall settings

**Toast notifications not showing**:
- Toast uses NSUserNotification (legacy) or modern APIs
- Check notification permissions in System Settings

## Performance

### App Size
- **Debug**: ~8 MB
- **Release** (optimized): ~5 MB

Much smaller than JVM Desktop app (~35 MB).

### Build Time (M1 Mac)
- **First build**: 2-3 minutes (including framework)
- **Incremental build**: 10-30 seconds
- **Framework only**: 30-60 seconds

### Runtime Performance
- **Startup time**: <1 second (native app)
- **Frame rate**: 60-120 FPS (ProMotion displays)
- **Memory usage**: 40-80 MB

## Release Build

### Archive for Distribution

1. **Select scheme**: StellarDemo
2. **Select destination**: Any Mac
3. **Archive**: Product → Archive
4. **Organizer**: Window → Organizer

### Export Signed App

1. **Select archive**: Choose latest
2. **Distribute**: Direct Distribution
3. **Options**:
   - Developer ID signed
   - Include symbols
   - Notarize app
4. **Export**: Save .app or .pkg

### Notarization (Required for macOS 10.15+)

```bash
# Upload for notarization
xcrun notarytool submit StellarDemo.zip \
  --apple-id "your@email.com" \
  --team-id "TEAMID" \
  --password "app-specific-password"

# Check status
xcrun notarytool info <submission-id> \
  --apple-id "your@email.com" \
  --team-id "TEAMID" \
  --password "app-specific-password"

# Staple ticket to app
xcrun stapler staple StellarDemo.app
```

## Distribution

### Mac App Store

1. **Enroll** in Apple Developer Program ($99/year)
2. **Create app** in App Store Connect
3. **Archive and upload** from Xcode
4. **Fill metadata**: Screenshots, description
5. **Submit for review**

### Direct Distribution

1. **Sign with Developer ID**: For distribution outside Mac App Store
2. **Notarize**: Required for macOS 10.15+
3. **Distribute**: .dmg, .pkg, or .zip

### GitHub Releases

1. **Build release**: Archive and export
2. **Create .dmg**: Use create-dmg or similar tool
3. **Upload to GitHub**: Attach to release
4. **Provide installation instructions**

## Differences from Desktop App

The JVM `desktopApp` module:
- ✅ Uses Compose UI (100% shared with Android/iOS/Web)
- ✅ Cross-platform (macOS/Windows/Linux)
- ✅ Hot reload in development
- ❌ Requires JVM (larger bundle: ~35 MB)
- ❌ Slower startup time

This `macosApp` module:
- ✅ True native macOS app
- ✅ Smaller bundle size (~8 MB)
- ✅ Faster startup time
- ✅ Full AppKit/SwiftUI access
- ✅ Better macOS integration
- ❌ SwiftUI UI (not shared with other platforms)
- ❌ macOS-only (not cross-platform)

## Recommendation

**Use Desktop App** if you want:
- Compose UI shared with other platforms
- Cross-platform support (macOS/Windows/Linux)
- Faster development (less platform-specific code)

**Use macOS Native App** if you want:
- True native macOS experience
- Smaller bundle size
- Best performance
- Full macOS platform API access
- And you're willing to maintain SwiftUI code separately

## Xcodegen

This project uses **xcodegen** to generate the Xcode project from `project.yml`.

### Why xcodegen?

- **Version control friendly**: `.xcodeproj` is gitignored
- **No merge conflicts**: Project is generated, not edited
- **Declarative**: Simple YAML instead of complex XML
- **Reproducible**: Same project every time

### Regenerate Project

```bash
cd demo/macosApp
xcodegen generate
```

Run this whenever you:
- Pull changes from git
- Modify `project.yml`
- Add/remove Swift files
- Change build settings

## Resources

### macOS Documentation
- [macOS Developer](https://developer.apple.com/macos/)
- [SwiftUI](https://developer.apple.com/xcode/swiftui/)
- [AppKit](https://developer.apple.com/documentation/appkit)

### Stellar Resources
- [Stellar Documentation](https://developers.stellar.org/)
- [Horizon API](https://developers.stellar.org/api/horizon)
- [Soroban Documentation](https://soroban.stellar.org/)

### Project Documentation
- [Main Demo README](../README.md)
- [Shared Module](../shared/)
- [SDK Documentation](../../README.md)
- [Desktop App README](../desktopApp/README.md) - Compare with JVM version

### Tools
- [xcodegen](https://github.com/yonaskolb/XcodeGen)
- [libsodium](https://libsodium.gitbook.io/)

## Support

For issues:
- **macOS-specific**: Check this README and Xcode console
- **Framework build**: Check Gradle output
- **SDK functionality**: See main SDK documentation
- **Stellar protocol**: Visit [developers.stellar.org](https://developers.stellar.org/)

## License

Part of the Stellar KMP SDK project. See main repository for license details.
