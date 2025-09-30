# Stellar KMP SDK - Web Sample

This is a comprehensive web integration demo for the Stellar KMP SDK. It demonstrates all major features of the SDK in a browser environment with a modern, responsive UI.

## Features

### 🎯 Interactive Web UI
- **Generate Keypairs**: Create new Ed25519 keypairs with one click
- **Copy to Clipboard**: Easily copy account IDs and secret seeds
- **Run Tests**: Execute comprehensive test suite in the browser
- **View Results**: See detailed test results with timing information
- **Responsive Design**: Works on desktop, tablet, and mobile

### 🧪 Comprehensive Tests

The sample app includes 8 comprehensive tests:

1. **Random KeyPair Generation** - Verifies unique keypair generation
2. **KeyPair from Secret Seed** - Tests keypair derivation from known seed
3. **KeyPair from Account ID** - Tests public-only keypair creation
4. **Sign and Verify** - Tests digital signature creation and verification
5. **Invalid Secret Seed** - Validates error handling for invalid seeds
6. **Invalid Account ID** - Validates error handling for invalid account IDs
7. **Memory Safety** - Stress tests with 100 keypairs
8. **Crypto Library Info** - Verifies crypto library detection

## Prerequisites

- **Node.js**: 18.0 or later
- **npm**: 9.0 or later
- Modern web browser (Chrome, Firefox, Safari, Edge)

## Quick Start

### Step 1: Install Dependencies

```bash
cd webSample
npm install
```

### Step 2: Build the Stellar SDK

```bash
# From project root
./gradlew :stellar-sdk:jsBrowserProductionLibraryDistribution
```

This will build the JavaScript version of the SDK to `stellar-sdk/build/js/`.

### Step 3: Run Development Server

```bash
# From webSample directory
npm run dev
```

This will:
- Build the web application
- Start a development server at http://localhost:8080
- Open the application in your default browser
- Enable hot reload for development

### Step 4: Use the Application

1. The SDK will initialize automatically (you'll see a loading screen)
2. Once loaded, you'll see the SDK information
3. Click "Generate New Keypair" to create a keypair
4. Click "Run All Tests" to execute the test suite

## Project Structure

```
webSample/
├── package.json                # npm dependencies
├── webpack.config.js           # Webpack configuration
├── src/
│   ├── index.html              # Main HTML file
│   ├── css/
│   │   └── styles.css          # Application styles
│   └── js/
│       └── main.js             # Application logic
├── dist/                       # Built files (generated)
└── README.md                   # This file
```

## Building for Production

```bash
# Build production bundle
npm run build

# Output will be in dist/ directory
```

The production build:
- Minifies JavaScript and CSS
- Optimizes bundle size
- Includes source maps
- Ready for deployment to any static hosting

## SDK Integration Examples

### Import the SDK

```javascript
import { KeyPair } from '@stellar/kmp-sdk';
// Or with require:
const stellarSdk = require('path/to/stellar-sdk.js');
const KeyPair = stellarSdk.com.stellar.sdk.KeyPair;
```

### Generate Keypairs

```javascript
// Generate random keypair
const keypair = KeyPair.Companion.random();
const accountId = keypair.getAccountId();

// From secret seed
const keypair = KeyPair.Companion.fromSecretSeed(
    'SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE'
);

// From account ID (public-only)
const keypair = KeyPair.Companion.fromAccountId(
    'GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D'
);
```

### Sign and Verify Messages

```javascript
// Sign data
const message = new TextEncoder().encode('Hello Stellar');
const messageArray = Array.from(message);
const signature = keypair.sign(messageArray);

// Verify
const isValid = keypair.verify(messageArray, signature);
```

### Get Crypto Library Info

```javascript
const cryptoLib = KeyPair.Companion.getCryptoLibraryName();
console.log(`Using: ${cryptoLib}`);  // Prints: "libsodium.js / Web Crypto API"
```

## Architecture

### Cryptographic Implementation

The browser implementation uses **libsodium.js** for cryptographic operations:

- **Algorithm**: Ed25519 (RFC 8032)
- **Library**: libsodium.js (WebAssembly + Web Crypto API fallback)
- **Bundle Size**: ~200 KB (gzipped)
- **Browser Support**: All modern browsers

### Why libsodium.js?

- ✅ **Production-proven**: Same algorithm as native implementations
- ✅ **Cross-platform**: Consistent Ed25519 across all platforms
- ✅ **WebAssembly**: Near-native performance
- ✅ **Fallback Support**: Web Crypto API when WASM unavailable
- ✅ **Well-maintained**: Active development and security updates

### Technology Stack

| Component | Technology |
|-----------|-----------|
| **Build Tool** | Webpack 5 |
| **Module System** | ES Modules / CommonJS |
| **Styling** | Vanilla CSS (CSS Variables) |
| **Dev Server** | webpack-dev-server |
| **Crypto** | libsodium.js (WASM) |

## Browser Compatibility

| Browser | Minimum Version | Notes |
|---------|----------------|-------|
| Chrome | 90+ | Full support |
| Firefox | 88+ | Full support |
| Safari | 14+ | Full support |
| Edge | 90+ | Full support |

## Development

### Start Development Server

```bash
npm run dev
```

Features:
- Hot module replacement
- Source maps
- Error overlay
- Auto-refresh on changes

### Build for Production

```bash
npm run build
```

Optimizations:
- Code minification
- Tree shaking
- Asset optimization
- Cache busting

### Clean Build Artifacts

```bash
npm run clean
```

## Deployment

The built application in `dist/` can be deployed to:

- **Static Hosting**: Netlify, Vercel, GitHub Pages
- **CDN**: CloudFlare, AWS S3 + CloudFront
- **Web Servers**: Nginx, Apache

Example nginx configuration:

```nginx
server {
    listen 80;
    server_name stellar-demo.example.com;
    root /var/www/stellar-web-sample/dist;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

## Troubleshooting

### SDK Not Loading

If you see "SDK not ready yet":

1. Ensure the Stellar SDK is built: `./gradlew :stellar-sdk:jsBrowserProductionLibraryDistribution`
2. Check that the path in `main.js` points to the correct SDK location
3. Check browser console for errors

### Build Errors

If `npm install` fails:

1. Ensure Node.js 18+ is installed: `node --version`
2. Clear npm cache: `npm cache clean --force`
3. Delete `node_modules` and `package-lock.json`, then retry

### Development Server Issues

If the dev server won't start:

1. Check if port 8080 is already in use
2. Try a different port: `npx webpack serve --port 8081`
3. Check firewall settings

### Bundle Size Too Large

If the bundle is too large:

1. Enable production mode: `npm run build`
2. Check webpack bundle analyzer
3. Consider code splitting for large applications

## Performance

Expected performance in modern browsers:

- **Keypair Generation**: ~10-20ms
- **Sign Operation**: ~10-20ms
- **Verify Operation**: ~10-20ms
- **Full Test Suite**: < 1 second
- **Page Load**: < 2 seconds (with caching)

## Security Notes

- Private keys are handled in memory only
- Never log or transmit secret seeds
- Use HTTPS in production
- Implement proper key management
- This demo includes visible secret seeds for educational purposes only

## Distribution

This sample demonstrates the **recommended integration approach** for web:

1. **NPM Package**: Install via npm (when published)
   ```bash
   npm install @stellar/kmp-sdk
   ```

2. **Import in Your App**: Use modern JavaScript imports
   ```javascript
   import { KeyPair } from '@stellar/kmp-sdk';
   ```

3. **Bundle with Your App**: Use Webpack, Rollup, or Vite

## Related Documentation

- [Main Project README](../README.md)
- [Crypto Implementation](../CRYPTO_IMPLEMENTATIONS.md)
- [iOS Sample App](../iosSample/README.md)
- [Android Sample App](../androidSample/README.md)

## Comparison with Native Apps

| Feature | Web | iOS | Android |
|---------|-----|-----|---------|
| **Crypto** | libsodium.js | libsodium | BouncyCastle |
| **UI** | HTML/CSS/JS | SwiftUI | Compose |
| **Distribution** | npm / CDN | XCFramework | Gradle |
| **Bundle Size** | ~200 KB | ~7 MB | ~3 MB |
| **Platform** | Any browser | iOS 13+ | Android 7.0+ |

All implementations provide identical Ed25519 functionality with platform-optimized performance.

## License

Same as parent project (Stellar KMP SDK) - Apache 2.0

## Support

- **GitHub Issues**: https://github.com/stellar/kmp-stellar-sdk/issues
- **Stellar Development**: https://developers.stellar.org
- **Stellar Discord**: https://discord.gg/stellar
