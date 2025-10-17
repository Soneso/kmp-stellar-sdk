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
  - Web (JS): Stable JavaScript with Compose (production-ready)
  - Web (WASM): Experimental WebAssembly with Compose (cutting-edge)

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
│   │   ├── macosMain/     # macOS native-specific code
│   │   └── wasmJsMain/    # WebAssembly-specific code
│   └── build.gradle.kts
├── androidApp/            # Android entry point
├── desktopApp/            # Desktop JVM entry point (works on macOS!)
├── iosApp/                # iOS SwiftUI entry point
├── macosApp/              # macOS native SwiftUI entry point
├── webJsApp/              # Web JavaScript entry point (stable, production-ready)
└── webApp/                # Web WASM entry point (experimental)
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

### Web (JavaScript - Stable) ✅
**Recommended for production web applications**
```bash
# Development server with hot reload
./gradlew :demo:webJsApp:jsBrowserDevelopmentRun
# Opens at http://localhost:8081

# Production build (optimized, ~955 KB)
./gradlew :demo:webJsApp:jsBrowserProductionWebpack
```

**Browser Support**: All modern browsers (Chrome 90+, Firefox 88+, Safari 15.4+, Edge 90+)

See `demo/webJsApp/README.md` for detailed documentation.

### Web (WASM - Experimental) 🚧
**For cutting-edge browsers only**
```bash
# Development server
./gradlew :demo:webApp:wasmJsBrowserDevelopmentRun
# Opens at http://localhost:8080

# Production build
./gradlew :demo:webApp:wasmJsBrowserProductionWebpack
```

**Browser Support**: Chrome 119+, Firefox 120+, Safari 17.4+ (limited compatibility)

## Web Targets: JS vs WASM

### Which Web Target Should I Use?

| Feature | **JavaScript (webJsApp)** | **WASM (webApp)** |
|---------|---------------------------|-------------------|
| **Status** | ✅ **Stable & Production-Ready** | ⚠️ Experimental |
| **Browser Support** | ✅ All modern browsers | ⚠️ Latest browsers only |
| **Bundle Size** | ~955 KB (production) | ~1.2 MB |
| **Startup Time** | Fast | Very Fast |
| **Performance** | Good (60 FPS) | Excellent (120 FPS) |
| **Compatibility** | Maximum | Limited |
| **Mobile Browsers** | ✅ Full support | ⚠️ Limited support |
| **Corporate/Enterprise** | ✅ Excellent | ⚠️ May not work |
| **Recommended For** | Production apps | Experimental/Future |

**Recommendation**:
- **Use webJsApp (JS)** for production applications that need to work everywhere
- **Use webApp (WASM)** for experimental projects or if you only target the latest browsers

## Features

### Current
- ✅ Main screen with demo topic list
- ✅ Navigation between screens
- ✅ Key Generation screen (placeholder)
- ✅ Material 3 design system
- ✅ Cross-platform architecture
- ✅ Web support (both JS and WASM)

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
- **Skiko**: Canvas-based rendering for web (JS + WASM)

## Platform Support Status

| Platform | UI Framework | Status | Notes |
|----------|--------------|--------|-------|
| Android  | Compose | ✅ Ready | Tested on API 24+ |
| Desktop (JVM) | Compose | ✅ Ready | Works on macOS, Windows, Linux |
| iOS | Compose | ✅ Ready | Requires Xcode |
| macOS (Native) | SwiftUI | ✅ Ready | Alternative to Desktop |
| **Web (JS)** | **Compose** | **✅ Ready** | **Production-ready, all browsers** |
| Web (WASM) | Compose | 🚧 Experimental | Latest browsers only |

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

## Web: Two Options

### Option 1: JavaScript (Stable) - **Recommended**
- ✅ **Production-ready** - Stable Kotlin/JS
- ✅ **Maximum compatibility** - All modern browsers
- ✅ **Mobile support** - Full mobile browser support
- ✅ **Enterprise-ready** - Works in corporate environments
- Bundle: ~955 KB (production)

### Option 2: WebAssembly (Experimental)
- ⚠️ **Experimental** - Cutting-edge technology
- ⚠️ **Limited browsers** - Latest browsers only
- ⚠️ **Future-focused** - Not yet stable
- ✅ **Best performance** - Faster than JS
- Bundle: ~1.2 MB

See `demo/webJsApp/README.md` for detailed web documentation.

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

- The web targets use Skiko-based canvas rendering for Compose UI
- JavaScript target is stable and production-ready
- WASM target is experimental and requires latest browsers
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

   # Web (JavaScript - Stable)
   ./gradlew :demo:webJsApp:jsBrowserDevelopmentRun

   # Web (WASM - Experimental)
   ./gradlew :demo:webApp:wasmJsBrowserDevelopmentRun
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
