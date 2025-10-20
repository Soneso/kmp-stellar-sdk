# Stellar SDK Demo - Web App (JavaScript)

Production-ready web application demonstrating the Stellar SDK with **Compose Multiplatform** running in the browser.

## Overview

This is a **JavaScript web app** built with Kotlin/JS and Compose Multiplatform, showcasing:
- **Key Generation**: Generate and manage Stellar keypairs in the browser
- **Account Management**: Fund testnet accounts and fetch account details
- **Payments**: Send XLM and custom assets
- **Trustlines**: Establish trust to hold issued assets
- **Smart Contracts**: Fetch and parse Soroban contract details

The app uses 100% shared Compose UI code, running identically on the web as on Android, iOS, and Desktop.

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
│     demo:webApp (12 lines Kotlin + HTML)     │
│  • Main.kt: ComposeViewport setup            │
│  • index.html: Canvas container              │
│  • Webpack bundling                          │
└──────────────────────────────────────────────┘
                    ▼
┌──────────────────────────────────────────────┐
│           Browser Runtime                    │
│  • Skiko canvas rendering (WebGL 2.0)       │
│  • libsodium.js for cryptography             │
│  • Single-page web application               │
└──────────────────────────────────────────────┘
```

### Code Distribution
- **Shared code**: ~99% (all UI and business logic)
- **Web-specific**: ~1% (Canvas setup and HTML template)

### Why JavaScript (not WASM)?

This uses **stable Kotlin/JS** for maximum compatibility:
- ✅ All modern browsers (Chrome 90+, Firefox 88+, Safari 15.4+, Edge 90+)
- ✅ Mobile browsers (iOS Safari, Chrome Mobile)
- ✅ Enterprise environments (corporate networks, proxies)
- ✅ Production-ready (stable, battle-tested)

## Prerequisites

### Development
- **Gradle**: 8.5+ (included via wrapper)
- **JDK**: 11 or higher
- **Modern Browser**: Chrome 90+, Firefox 88+, Safari 15.4+, or Edge 90+

### Browser Requirements
- **JavaScript**: ES2015+ (ES6+)
- **WebGL**: 2.0 (for Skiko canvas rendering)
- **WebAssembly**: Not required (pure JS build)
- **Cookies/Storage**: Not required

### Production Deployment
- **Static Hosting**: Any web server (nginx, Apache, Netlify, Vercel, GitHub Pages, etc.)
- **HTTPS**: Recommended for production
- **CDN**: Optional but recommended

## Building and Running

### Development Mode (Hot Reload)

```bash
# From project root
cd /Users/chris/projects/Stellar/kmp/kmp-stellar-sdk

# Start development server
./gradlew :demo:webApp:jsBrowserDevelopmentRun

# Opens at: http://localhost:8081
```

Features:
- **Hot reload**: Code changes refresh automatically
- **Source maps**: Debug Kotlin code in browser DevTools
- **Fast iteration**: See changes in seconds

### Development Build (Manual)

```bash
# Build development bundle (unminified, with source maps)
./gradlew :demo:webApp:jsBrowserDevelopmentWebpack

# Output: demo/webApp/build/kotlin-webpack/js/developmentExecutable/
# Size: ~33.6 MB (unminified)
```

### Production Build

```bash
# Build production bundle (minified, optimized)
./gradlew :demo:webApp:jsBrowserProductionWebpack

# Output: demo/webApp/build/kotlin-webpack/js/productionExecutable/
# Size: ~955 KB (minified, ~220 KB gzipped)
```

### Production Server (Testing)

```bash
# Run production build locally
./gradlew :demo:webApp:jsBrowserProductionRun

# Opens at: http://localhost:8081
```

## Project Structure

```
webApp/
├── src/
│   └── jsMain/
│       ├── kotlin/
│       │   └── Main.kt              # Entry point (12 lines)
│       └── resources/
│           └── index.html           # HTML template
├── build.gradle.kts                 # Kotlin/JS configuration
└── build/
    └── kotlin-webpack/js/
        ├── developmentExecutable/   # Dev build
        └── productionExecutable/    # Prod build
```

### Main.kt (Entry Point)

```kotlin
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.soneso.demo.App
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.getElementById("ComposeTarget")!!) {
        App()  // Shared Compose UI from demo:shared
    }
}
```

### index.html (Template)

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Stellar SDK Demo</title>
    <style>
        body {
            margin: 0;
            padding: 0;
            overflow: hidden;
        }
        #ComposeTarget {
            width: 100vw;
            height: 100vh;
        }
    </style>
</head>
<body>
    <div id="ComposeTarget"></div>
    <script src="stellarDemoJs.js"></script>
</body>
</html>
```

## Features

All 7 features work identically in the browser:

### 1. Key Generation
- Generate random Stellar keypairs using browser's crypto API
- Display and copy keys to clipboard
- Sign and verify data using libsodium.js
- Uses: `KeyPair.random()` from Stellar SDK

### 2. Fund Testnet Account
- Request XLM from Friendbot via HTTP
- Real-time funding status
- Error handling
- Uses: `FriendBot.fundTestnetAccount()`

### 3. Fetch Account Details
- Retrieve account information from Horizon
- Display balances, sequence number, flags
- Real-time data fetching
- Uses: `Server.accounts()`

### 4. Trust Asset
- Establish trustlines for issued assets
- Build, sign, and submit transactions
- Uses: `ChangeTrustOperation`

### 5. Send Payment
- Transfer XLM or custom assets
- Amount validation and signing
- Uses: `PaymentOperation`

### 6. Smart Contract Details
- Parse WASM contracts
- View contract metadata and specification
- Uses: Soroban RPC integration

### 7. Deploy Smart Contract
- Upload and deploy WASM contracts in the browser
- One-step deployment with constructor arguments
- Two-step deployment for WASM reuse
- Browser-based WASM file loading
- Uses: `ContractClient.deploy()`, `install()`, `deployFromWasmId()`

## Technology Stack

### Web Layer
- **Kotlin/JS**: Compiles Kotlin to JavaScript
- **Compose Multiplatform**: Declarative UI framework
- **Skiko**: Canvas-based rendering (WebGL 2.0)
- **Webpack**: Module bundling

### Shared Layer
- **Compose Multiplatform**: All UI (100% shared)
- **Material 3**: Design system
- **Voyager**: Navigation library
- **Stellar SDK**: All Stellar functionality

### Browser APIs
- **Canvas API**: For Compose rendering
- **Fetch API**: HTTP networking (from Ktor)
- **Crypto API**: `crypto.getRandomValues()` for randomness
- **Clipboard API**: Copy to clipboard
- **libsodium.js**: Ed25519 cryptography (WASM)

## Browser Compatibility

### Supported Browsers

| Browser | Min Version | Status | Notes |
|---------|-------------|--------|-------|
| Chrome | 90+ | ✅ Fully tested | Recommended |
| Edge | 90+ | ✅ Fully tested | Chromium-based |
| Firefox | 88+ | ✅ Fully tested | Excellent support |
| Safari | 15.4+ | ✅ Tested | Requires newer macOS |
| Safari iOS | 15.4+ | ✅ Mobile tested | Works on iPhone/iPad |
| Chrome Mobile | 90+ | ✅ Mobile tested | Android & iOS |
| Opera | 76+ | ✅ Compatible | Chromium-based |

### Required Features
- **WebGL 2.0**: For Skiko canvas rendering (check at https://get.webgl.org/webgl2/)
- **ES6+ JavaScript**: All modern browsers support
- **Async/Await**: For coroutines support
- **Fetch API**: For HTTP networking

### Not Required
- WebAssembly (using pure JS build)
- Service Workers
- Local Storage
- IndexedDB

## Deployment

### Static Hosting

The production build can be deployed to any static hosting service.

#### 1. Build Production Bundle

```bash
./gradlew :demo:webApp:jsBrowserProductionWebpack
```

Output directory: `demo/webApp/build/kotlin-webpack/js/productionExecutable/`

Contains:
- `index.html` - HTML entry point
- `stellarDemoJs.js` - Bundled JavaScript (~955 KB)
- `stellarDemoJs.js.map` - Source map (optional, for debugging)

#### 2. Deploy to Hosting

Copy the output directory to your hosting service:

```bash
# Example: Deploy to /var/www/html
cp -r demo/webApp/build/kotlin-webpack/js/productionExecutable/* /var/www/html/

# Or create a zip for upload
cd demo/webApp/build/kotlin-webpack/js/productionExecutable/
zip -r stellar-demo.zip *
```

### Hosting Options

#### Netlify

1. **Build command**: `./gradlew :demo:webApp:jsBrowserProductionWebpack`
2. **Publish directory**: `demo/webApp/build/kotlin-webpack/js/productionExecutable`
3. **Deploy**: Drag & drop or connect Git repository

#### Vercel

1. **Build command**: `./gradlew :demo:webApp:jsBrowserProductionWebpack`
2. **Output directory**: `demo/webApp/build/kotlin-webpack/js/productionExecutable`
3. **Deploy**: `vercel deploy`

#### GitHub Pages

```bash
# Build production bundle
./gradlew :demo:webApp:jsBrowserProductionWebpack

# Copy to docs/ or gh-pages branch
cp -r demo/webApp/build/kotlin-webpack/js/productionExecutable/* docs/

# Commit and push
git add docs/
git commit -m "Deploy web app"
git push
```

Enable in: Settings → Pages → Source → Select branch

#### Custom Server (nginx)

```nginx
server {
    listen 80;
    server_name stellar-demo.example.com;

    root /var/www/stellar-demo;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # Enable gzip compression
    gzip on;
    gzip_types application/javascript text/css;
    gzip_min_length 1000;

    # Cache static assets
    location ~* \.(js|css|png|jpg|jpeg|gif|ico)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

#### Simple HTTP Server (Testing)

```bash
# Using Python
cd demo/webApp/build/kotlin-webpack/js/productionExecutable/
python3 -m http.server 8000

# Using Node.js (http-server)
npx http-server demo/webApp/build/kotlin-webpack/js/productionExecutable/ -p 8000

# Opens at: http://localhost:8000
```

## Performance

### Bundle Sizes

| Build Type | Size | Gzipped | Notes |
|------------|------|---------|-------|
| Development | ~33.6 MB | N/A | With source maps |
| Production | ~955 KB | ~220 KB | Minified |
| Production (no source map) | ~950 KB | ~215 KB | Smallest |

### Load Times (on 3G)

- **Initial load**: 2-4 seconds (first visit)
- **Cached load**: <500ms (subsequent visits)
- **First paint**: ~1 second
- **Interactive**: 2-3 seconds

### Runtime Performance

- **Frame rate**: 60 FPS on modern devices
- **Memory usage**: 50-100 MB
- **Startup time**: ~1 second
- **Responsiveness**: Excellent

### Optimization Tips

1. **Enable gzip**: Reduces bundle from 955 KB to ~220 KB
2. **Use CDN**: Faster delivery globally
3. **Cache headers**: Browser caching for static assets
4. **Code splitting**: Not needed for this small app
5. **Preload**: Add `<link rel="preload">` for critical resources

## Configuration

### Port Configuration

Edit `build.gradle.kts` to change the development server port:

```kotlin
kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                devServer = devServer?.copy(
                    port = 8082,  // Change port here
                    open = false  // Set to true to auto-open browser
                )
            }
        }
    }
}
```

### Output Filename

Change the JavaScript filename:

```kotlin
commonWebpackConfig {
    outputFileName = "app.js"  // Default: stellarDemoJs.js
}
```

### Production Optimizations

Customize webpack configuration:

```kotlin
commonWebpackConfig {
    cssSupport {
        enabled.set(true)
    }

    // Sourcemaps (disable for smallest build)
    devtool = "source-map"  // or false for production
}
```

## Development

### Hot Reload

While running the development server:
1. Edit code in `demo/shared/src/commonMain/`
2. Save file
3. Browser refreshes automatically
4. See changes immediately

### Debugging

#### Browser DevTools

1. **Open DevTools**: F12 or Cmd+Opt+I (Mac)
2. **Sources tab**: View Kotlin source code (source maps)
3. **Console**: View console.log() output
4. **Network**: Monitor HTTP requests
5. **Performance**: Profile app performance

#### Kotlin Source Debugging

Thanks to source maps, you can debug Kotlin code directly:
1. Open Sources tab in DevTools
2. Navigate to `webpack://` → `src/commonMain/kotlin/`
3. Set breakpoints in Kotlin code
4. Step through code, inspect variables

#### Console Logging

Add logging in Kotlin:
```kotlin
console.log("Debug message: $value")
console.error("Error: $error")
console.warn("Warning: $warning")
```

## Troubleshooting

### Build Issues

**Webpack build fails**:
```bash
# Clean build
./gradlew clean
./gradlew :demo:webApp:jsBrowserProductionWebpack
```

**Out of memory**:
Add to `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m
org.gradle.daemon=true
```

**Port already in use**:
- Change port in `build.gradle.kts`
- Or kill process: `lsof -ti:8081 | xargs kill`

### Runtime Issues

**Canvas not rendering**:
1. Check WebGL 2.0 support: https://get.webgl.org/webgl2/
2. Try different browser (Chrome recommended)
3. Update graphics drivers
4. Check browser console for errors

**JavaScript errors**:
1. Open browser DevTools (F12)
2. Check Console tab for errors
3. Look for stack traces
4. Check Network tab for failed requests

**Performance issues**:
1. Use production build (not development)
2. Enable gzip compression on server
3. Use Chrome for best performance
4. Close other tabs/apps

**Clipboard not working**:
- Requires HTTPS in production (or localhost)
- Or use browser's clipboard permission API
- Check browser console for permission errors

### Network Issues

**CORS errors**:
- Expected for local development
- Horizon/Soroban servers have CORS enabled
- If custom server: Add CORS headers

**Slow loading**:
- Use production build (~955 KB vs 33.6 MB)
- Enable gzip compression
- Use CDN for faster delivery
- Check network throttling in DevTools

## Mobile Browser Support

### iOS Safari (15.4+)

✅ **Fully supported**
- All features work
- Touch interactions
- Clipboard access (with user gesture)
- Good performance

### Chrome Mobile (90+)

✅ **Fully supported**
- Android and iOS
- Excellent performance
- All features work

### Responsive Design

The Compose UI automatically adapts to different screen sizes:
- **Portrait**: Optimized for mobile phones
- **Landscape**: Better use of space
- **Tablet**: Larger touch targets
- **Desktop**: Full feature set

### Mobile-Specific Considerations

- **Virtual keyboard**: Automatically handled
- **Touch targets**: Compose ensures minimum 48dp
- **Scrolling**: Native smooth scrolling
- **Gestures**: Swipes and taps work natively

## Advanced Topics

### Progressive Web App (PWA)

To make this a PWA, add:

1. **Service Worker** (for offline support)
2. **Web App Manifest** (for install prompt)
3. **Icons** (for home screen)

Not included by default to keep the demo simple.

### Custom Domain

Deploy to a custom domain:

1. Build production bundle
2. Upload to hosting
3. Configure DNS records
4. Enable HTTPS (Let's Encrypt)
5. Test thoroughly

### Analytics

Add analytics (Google Analytics, Plausible, etc.):

```html
<!-- In index.html <head> -->
<script async src="https://www.googletagmanager.com/gtag/js?id=GA_MEASUREMENT_ID"></script>
<script>
    window.dataLayer = window.dataLayer || [];
    function gtag(){dataLayer.push(arguments);}
    gtag('js', new Date());
    gtag('config', 'GA_MEASUREMENT_ID');
</script>
```

### Security Headers

Add to server configuration:

```nginx
add_header X-Frame-Options "SAMEORIGIN";
add_header X-Content-Type-Options "nosniff";
add_header X-XSS-Protection "1; mode=block";
add_header Referrer-Policy "strict-origin-when-cross-origin";
```

## Comparison with Other Platforms

| Feature | Web (JS) | Desktop (JVM) | macOS Native | Android | iOS |
|---------|----------|---------------|--------------|---------|-----|
| Deployment | Static hosting | Installer | App Store | Play Store | App Store |
| Bundle Size | ~955 KB | ~35 MB | ~8 MB | ~12 MB | ~15 MB |
| Startup Time | 1-2s | 2-3s | <1s | 1-2s | 1-2s |
| Updates | Instant | Manual | Manual | Auto | Auto |
| Platform Access | Limited | Good | Excellent | Excellent | Excellent |
| Installation | None | Required | Required | Required | Required |
| **Best For** | ✅ Demos, quick access | Development, testing | Native feel | Mobile | Mobile |

## Resources

### Web Documentation
- [Kotlin/JS](https://kotlinlang.org/docs/js-overview.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Skiko Graphics](https://github.com/JetBrains/skiko)
- [Webpack](https://webpack.js.org/)

### Stellar Resources
- [Stellar Documentation](https://developers.stellar.org/)
- [Horizon API](https://developers.stellar.org/api/horizon)
- [Soroban Documentation](https://soroban.stellar.org/)

### Project Documentation
- [Main Demo README](../README.md)
- [Shared Module](../shared/)
- [SDK Documentation](../../README.md)
- [Desktop App README](../desktopApp/README.md) - Compare with JVM version

## Support

For issues:
- **Web-specific**: Check this README and browser console
- **Build issues**: Check Gradle output
- **UI issues**: Check shared module (`demo/shared`)
- **SDK functionality**: See main SDK documentation
- **Stellar protocol**: Visit [developers.stellar.org](https://developers.stellar.org/)

## License

Part of the Stellar KMP SDK project. See main repository for license details.
