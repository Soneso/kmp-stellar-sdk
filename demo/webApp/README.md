# Stellar SDK Demo - JavaScript Web App

This is the **production-ready JavaScript** version of the Stellar SDK demo web app using Kotlin/JS with Compose Multiplatform.

## Overview

This web app demonstrates the Stellar SDK's capabilities running in the browser using stable Kotlin/JS with Skiko-based canvas rendering.

**Key Features**:
- ✅ Stable and production-ready
- ✅ All modern browsers (Chrome 90+, Firefox 88+, Safari 15.4+, Edge 90+)
- ✅ Bundle Size: ~955 KB (production)
- ✅ Fast startup time
- ✅ Full mobile browser support
- ✅ Enterprise-ready compatibility

## Architecture

- **Entry Point**: `Main.kt` - Uses `CanvasBasedWindow` to render Compose UI on HTML canvas
- **HTML**: `index.html` - Minimal HTML with canvas element and loading indicator
- **Shared Logic**: Reuses `demo:shared` module with all Stellar SDK functionality
- **Rendering**: Skiko-based canvas rendering (same as desktop/iOS/Android)
- **Bundle**: Single JavaScript file with all dependencies included

## Building

### Development Build
```bash
./gradlew :demo:webApp:jsBrowserDevelopmentWebpack
```
Output: `demo/webApp/build/kotlin-webpack/js/developmentExecutable/`

### Production Build
```bash
./gradlew :demo:webApp:jsBrowserProductionWebpack
```
Output: `demo/webApp/build/kotlin-webpack/js/productionExecutable/`

Production build is minified and optimized (~955 KB).

## Running

### Development Server (with hot reload)
```bash
./gradlew :demo:webApp:jsBrowserDevelopmentRun
```
Opens at: http://localhost:8081

### Production Server (for testing)
```bash
./gradlew :demo:webApp:jsBrowserProductionRun
```

## Browser Compatibility

### Supported Browsers
- ✅ Chrome/Edge 90+ (Chromium-based)
- ✅ Firefox 88+
- ✅ Safari 15.4+
- ✅ Opera 76+

### Requirements
- **JavaScript**: ES2015+ (ES6+)
- **WebGL**: 2.0 (for Skiko canvas rendering)
- **WebAssembly**: Not required (pure JS build)

## Features Demonstrated

All features from the shared module work identically on JS:

1. **Key Generation**: Generate random Stellar keypairs
2. **Signing**: Sign data with Ed25519
3. **Verification**: Verify signatures
4. **StrKey Encoding**: Convert keys to Stellar format (G... and S...)
5. **Cryptography**: Full libsodium.js integration

## Deployment

### Static Hosting

The production build can be deployed to any static hosting service:

1. Build production bundle:
   ```bash
   ./gradlew :demo:webApp:jsBrowserProductionWebpack
   ```

2. Copy output directory to hosting:
   ```bash
   cp -r demo/webApp/build/kotlin-webpack/js/productionExecutable/ /path/to/hosting/
   ```

3. Serve with any web server (nginx, Apache, Netlify, Vercel, GitHub Pages, etc.)

### Example: Simple HTTP Server

```bash
cd demo/webApp/build/kotlin-webpack/js/productionExecutable/
python3 -m http.server 8000
```
Open: http://localhost:8000

## Performance

### Bundle Size
- **Development**: ~33.6 MB (unminified, with source maps)
- **Production**: ~955 KB (minified, optimized)
- **Gzipped**: ~220 KB (typical)

### Load Time
- Initial load: 1-3 seconds (depends on network)
- Cached load: <500ms
- First paint: ~1 second

### Runtime Performance
- Frame rate: 60 FPS on modern hardware
- Memory usage: ~50-100 MB
- Startup time: ~1 second

## Development

### Project Structure
```
webApp/
├── build.gradle.kts          # Kotlin/JS configuration
├── src/
│   └── jsMain/
│       ├── kotlin/
│       │   └── Main.kt       # Entry point
│       └── resources/
│           └── index.html    # HTML template
└── build/                    # Build outputs
```

### Dependencies
- `demo:shared` - Shared Stellar SDK logic
- `compose.ui` - Compose Multiplatform UI
- `compose.runtime` - Compose runtime
- `compose.foundation` - Foundation components

### Hot Reload
The development server supports hot reload for Kotlin code changes. Just save and the browser will refresh automatically.

## Troubleshooting

### Port Already in Use
If port 8081 is in use, edit `build.gradle.kts` and change the port:
```kotlin
devServer = devServer?.copy(
    port = 8082, // Change to desired port
    open = false
)
```

### Canvas Not Rendering
1. Check browser console for errors
2. Ensure WebGL 2.0 is supported: Visit https://get.webgl.org/webgl2/
3. Try a different browser

### Large Bundle Size
The JS bundle includes the full Compose runtime and Skiko. This is expected.
- Use production build for deployment (much smaller)
- Enable gzip compression on your web server
- Consider code splitting for larger apps

### libsodium.js Errors
The Stellar SDK automatically initializes libsodium.js. If you see crypto errors:
1. Check network connectivity (libsodium.js is loaded from npm bundle)
2. Wait for initialization before calling crypto functions
3. All SDK functions are `suspend` functions - use in coroutine scope

## Why JavaScript?

This web app uses stable Kotlin/JS for maximum compatibility and production readiness:

- ✅ **Maximum browser compatibility** - Works on all modern browsers
- ✅ **Mobile browser support** - Full support on iOS and Android browsers
- ✅ **Production-ready** - Stable and battle-tested
- ✅ **Enterprise environments** - Works in corporate networks
- ✅ **Proven technology** - Kotlin/JS is mature and well-supported

## Resources

- [Kotlin/JS Documentation](https://kotlinlang.org/docs/js-overview.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Stellar SDK Documentation](https://github.com/Soneso/kmp-stellar-sdk)
- [Skiko Graphics](https://github.com/JetBrains/skiko)

## License

Same as the parent project.
