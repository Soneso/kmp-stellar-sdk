# Using Stellar KMP SDK in Web Applications

## Quick Start

### 1. Install Dependencies

```bash
npm install @stellar/kmp-sdk libsodium-wrappers
```

**Note**: `libsodium-wrappers` is a peer dependency required for cryptographic operations.

### 2. Basic Usage

```javascript
import * as StellarSDK from '@stellar/kmp-sdk';
import sodium from 'libsodium-wrappers';

// Initialize libsodium (do this once at app startup)
await sodium.ready;
window._sodium = sodium;

// Use the SDK
const { KeyPair } = StellarSDK.com.stellar.sdk;
const keypair = KeyPair.Companion.random();
console.log(keypair.getAccountId());
```

### 3. With Webpack

If using webpack 5, add this to your `webpack.config.js`:

```javascript
module.exports = {
  resolve: {
    fallback: {
      "crypto": false,
      "path": false,
      "fs": false
    }
  }
};
```

## Complete Example

```javascript
import * as StellarSDK from '@stellar/kmp-sdk';
import sodium from 'libsodium-wrappers';

async function main() {
  // Initialize
  await sodium.ready;
  window._sodium = sodium;

  const { KeyPair } = StellarSDK.com.stellar.sdk;

  // Generate a new keypair
  const keypair = KeyPair.Companion.random();
  console.log('Account ID:', keypair.getAccountId());

  // Create from secret seed
  const seed = 'SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE';
  const kp2 = KeyPair.Companion.fromSecretSeedString(seed);

  // Sign and verify
  const message = new TextEncoder().encode('Hello Stellar');
  const messageArray = Array.from(message);
  const signature = keypair.sign(messageArray);
  const isValid = keypair.verify(messageArray, signature);
  console.log('Signature valid:', isValid);
}

main();
```

## API Differences from JVM/Kotlin

### Function Names

JavaScript doesn't support function overloading, so we use specific names:

| Kotlin | JavaScript |
|--------|------------|
| `fromSecretSeed(String)` | `fromSecretSeedString(seed)` |
| `fromSecretSeed(ByteArray)` | `fromSecretSeedBytes(bytes)` |
| `fromSecretSeed(CharArray)` | `fromSecretSeedCharArray(chars)` |

### Type Conversions

**ByteArray ↔ JavaScript Array:**
```javascript
// Kotlin ByteArray is exposed as Int8Array
const publicKey = keypair.getPublicKey(); // Returns Int8Array
const array = Array.from(publicKey); // Convert to regular array

// To pass ByteArray to Kotlin functions
const message = new TextEncoder().encode('Hello');
const messageArray = Array.from(message);
keypair.sign(messageArray); // Pass as regular array
```

**CharArray ↔ String:**
```javascript
// Get secret seed (returns Int32Array of char codes)
const secretSeed = keypair.getSecretSeed();
const seedString = new TextDecoder().decode(new Uint8Array(secretSeed));
```

## Why libsodium-wrappers?

The Stellar SDK uses Ed25519 cryptography for signing. In browsers, we use libsodium (a WebAssembly port of the NaCl library) for these operations.

While we'd love to bundle everything together, Kotlin Multiplatform generates a library that imports libsodium as a peer dependency. This is similar to how other JS libraries work (e.g., React requires react-dom).

## Comparison with stellar-sdk (JavaScript)

| Feature | stellar-sdk | kmp-stellar-sdk |
|---------|-------------|-----------------|
| Installation | `npm install stellar-sdk` | `npm install @stellar/kmp-sdk libsodium-wrappers` |
| Initialization | None | 2 lines (sodium init) |
| API | Native JS | Kotlin/JS bindings |
| Bundle size | ~150 KB | ~135 KB + 100 KB libsodium |
| Platforms | JS only | JS, JVM, iOS, Android, Native |

## Browser Support

- ✅ Chrome 90+
- ✅ Firefox 88+
- ✅ Safari 14+
- ✅ Edge 90+
- ✅ All modern browsers with WebAssembly support

## Complete Working Example

See `webSample/` directory for a complete working application with:
- Webpack 5 configuration
- libsodium initialization
- Full SDK integration
- 8 comprehensive tests
- Beautiful UI

To run:
```bash
cd webSample
npm install
npm run dev
```

## Troubleshooting

### "\_sodium is not defined"

Make sure you initialized libsodium:
```javascript
await sodium.ready;
window._sodium = sodium;
```

### Webpack warnings about 'crypto' module

Add fallbacks to your webpack config (see section 3 above).

### "KeyPair.Companion.fromSecretSeed is not a function"

Use the JavaScript-specific function names:
- `fromSecretSeedString()` for strings
- `fromSecretSeedBytes()` for byte arrays
