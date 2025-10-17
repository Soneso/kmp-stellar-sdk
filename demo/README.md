# Stellar SDK Demo

A Compose Multiplatform demo application showcasing the Stellar SDK across all supported platforms.

## Architecture

This demo app follows the **Compose Multiplatform** architecture pattern:

- **Shared UI Logic** (`demo/shared`): All Compose UI code, navigation, and business logic
- **Platform Entry Points**: Minimal platform-specific code to launch the shared UI
  - Android: Activity that sets up Compose
  - iOS: SwiftUI wrapper around Compose
  - Desktop: JVM window wrapper
  - macOS Native: SwiftUI with KMP SDK (no Compose UI)
  - Web: Stable JavaScript with Compose (production-ready)

## Project Structure

```
demo/
├── shared/                 # Shared Compose Multiplatform module
│   ├── src/
│   │   ├── commonMain/    # Shared UI and business logic
│   │   ├── androidMain/   # Android-specific code
│   │   ├── desktopMain/   # Desktop-specific code
│   │   ├── iosMain/       # iOS-specific code
│   │   ├── jsMain/        # JavaScript-specific code
│   │   └── macosMain/     # macOS native-specific code
│   └── build.gradle.kts
├── androidApp/            # Android entry point
├── desktopApp/            # Desktop JVM entry point (works on macOS!)
├── iosApp/                # iOS SwiftUI entry point
├── macosApp/              # macOS native SwiftUI entry point
└── webApp/                # Web JavaScript entry point (stable, production-ready)
```

## Building and Running

### Android ✅
```bash
./gradlew :demo:androidApp:assembleDebug
# Or install on connected device
./gradlew :demo:androidApp:installDebug
```

### Desktop ✅
**Recommended for macOS users wanting Compose UI**
```bash
./gradlew :demo:desktopApp:run
```

### iOS ✅
```bash
# Build the framework
./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64

# Generate and open Xcode project (requires xcodegen)
cd demo/iosApp
xcodegen generate
open StellarDemo.xcodeproj
```

### macOS Native ✅
**Alternative to Desktop - Native app with SwiftUI**
```bash
# Build the framework
./gradlew :demo:shared:linkDebugFrameworkMacosArm64

# Generate and open Xcode project (requires xcodegen and libsodium)
cd demo/macosApp
xcodegen generate
open StellarDemo.xcodeproj
```

**Note**: See `demo/macosApp/README.md` for details. The native macOS app uses SwiftUI (not Compose) due to Compose Multiplatform limitations. For Compose UI on macOS, use the Desktop app.

### Web ✅
**Production-ready JavaScript web application**
```bash
# Development server with hot reload
./gradlew :demo:webApp:jsBrowserDevelopmentRun
# Opens at http://localhost:8081

# Production build (optimized, ~955 KB)
./gradlew :demo:webApp:jsBrowserProductionWebpack
```

**Browser Support**: All modern browsers (Chrome 90+, Firefox 88+, Safari 15.4+, Edge 90+)

See `demo/webApp/README.md` for detailed documentation.

## Web Application

The web application uses stable Kotlin/JS with Compose Multiplatform, providing production-ready support for all modern browsers.

**Features**:
- ✅ Stable & Production-Ready
- ✅ All modern browsers (Chrome 90+, Firefox 88+, Safari 15.4+, Edge 90+)
- ✅ Bundle Size: ~955 KB (production)
- ✅ Fast startup time
- ✅ Full mobile browser support
- ✅ Enterprise-ready compatibility

## Features

### Current
- ✅ Main screen with demo topic list
- ✅ Navigation between screens
- ✅ Key Generation screen (placeholder)
- ✅ Material 3 design system
- ✅ Cross-platform architecture
- ✅ Web support (JavaScript)

### Planned
- Key generation and management UI
- Transaction signing
- Account operations
- Payment operations
- Asset management
- Smart contract interactions
- QR code generation/scanning

## Technology Stack

- **Kotlin Multiplatform**: Shared business logic
- **Compose Multiplatform**: Shared UI across most platforms
- **SwiftUI**: Native macOS UI (due to Compose limitations)
- **Voyager**: Navigation library
- **Material 3**: Design system
- **Stellar SDK**: Core SDK functionality
- **Skiko**: Canvas-based rendering for web (JavaScript)

## Platform Support Status

| Platform | UI Framework | Status | Notes |
|----------|--------------|--------|-------|
| Android  | Compose | ✅ Ready | Tested on API 24+ |
| Desktop (JVM) | Compose | ✅ Ready | Works on macOS, Windows, Linux |
| iOS | Compose | ✅ Ready | Requires Xcode |
| macOS (Native) | SwiftUI | ✅ Ready | Alternative to Desktop |
| Web | Compose | ✅ Ready | Production-ready, all modern browsers |

## macOS: Two Options

### Option 1: Desktop App (Recommended)
- ✅ **Full Compose UI** - Same UI as Android/iOS/Web
- ✅ **Cross-platform** - Also runs on Windows/Linux
- ✅ **Production-ready** - Official Compose Multiplatform target
- ❌ Requires JVM - Larger bundle size

### Option 2: Native macOS App
- ✅ **True native** - Native macOS app
- ✅ **Smaller bundle** - No JVM required
- ✅ **Native APIs** - Full AppKit/SwiftUI access
- ❌ **SwiftUI UI** - Can't use Compose UI (platform limitation)

See `demo/macosApp/README.md` for detailed comparison and rationale.

## Adding New Demo Topics

1. Create a new screen in `shared/src/commonMain/kotlin/com/stellar/demo/ui/screens/`
2. Add the topic to the list in `MainScreen.kt`
3. Implement the functionality in the new screen

Example:
```kotlin
DemoTopic(
    title = "Transaction Signing",
    description = "Sign and submit transactions",
    icon = Icons.Default.Edit,
    screen = TransactionSigningScreen()
)
```

## Development Notes

- The web target uses Skiko-based canvas rendering for Compose UI
- JavaScript target is stable and production-ready
- All platforms share the same UI code from `commonMain` (except macOS native)
- Platform-specific code is minimal and only used for entry points
- The app uses Material 3 design system for consistent UI across platforms
- macOS native uses SwiftUI due to limited Compose native macOS support

## Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│              Shared Module (demo/shared)            │
│                                                     │
│  ┌──────────────────────────────────────────────┐  │
│  │         Common UI (Compose)                  │  │
│  │  • Screens (MainScreen, KeyGenerationScreen) │  │
│  │  • Navigation (Voyager)                      │  │
│  │  • Theme (Material 3)                        │  │
│  │  • Business Logic                            │  │
│  └──────────────────────────────────────────────┘  │
│                                                     │
│  Platform-specific: Entry points only               │
└─────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┬─────────────┬──────────────┐
        │                 │                 │             │              │
        ▼                 ▼                 ▼             ▼              ▼
   ┌─────────┐      ┌──────────┐     ┌──────────┐  ┌──────────┐  ┌──────────┐
   │ Android │      │ Desktop  │     │   iOS    │  │  macOS   │  │   Web    │
   │ Compose │      │ Compose  │     │ Compose  │  │ SwiftUI* │  │ JS/WASM  │
   └─────────┘      └──────────┘     └──────────┘  └──────────┘  └──────────┘

   * macOS native uses SwiftUI instead of Compose
```

## Quick Start

1. **Clone and setup**:
   ```bash
   cd /path/to/kmp-stellar-sdk
   ```

2. **Run on your preferred platform**:
   ```bash
   # Android
   ./gradlew :demo:androidApp:installDebug

   # Desktop (macOS/Windows/Linux)
   ./gradlew :demo:desktopApp:run

   # iOS (requires Xcode)
   cd demo/iosApp && xcodegen generate && open StellarDemo.xcodeproj

   # macOS Native (requires Xcode + libsodium)
   cd demo/macosApp && xcodegen generate && open StellarDemo.xcodeproj

   # Web (JavaScript)
   ./gradlew :demo:webApp:jsBrowserDevelopmentRun
   ```

3. **Explore the code**:
   - Start with `shared/src/commonMain/kotlin/com/stellar/demo/App.kt`
   - Check out the screens in `shared/src/commonMain/kotlin/com/stellar/demo/ui/screens/`
   - See platform entry points in respective `*App` directories

## Contributing

When adding features:
1. Put shared UI in `commonMain`
2. Keep platform-specific code minimal
3. Test on multiple platforms
4. Update this README with any new features or requirements

## License

Part of the Stellar KMP SDK project.
