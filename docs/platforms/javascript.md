# JavaScript Platform Guide

This guide covers JavaScript-specific setup and usage for both Browser and Node.js environments.

## Table of Contents

- [Platform Overview](#platform-overview)
- [Browser Setup](#browser-setup)
- [Node.js Setup](#nodejs-setup)
- [Cryptography Details](#cryptography-details)
- [Webpack Configuration](#webpack-configuration)
- [React Integration](#react-integration)
- [Vue Integration](#vue-integration)
- [Next.js Integration](#nextjs-integration)
- [Performance Optimization](#performance-optimization)
- [Troubleshooting](#troubleshooting)

## Platform Overview

The JavaScript implementation works in:
- **Modern Browsers** (Chrome, Firefox, Safari, Edge)
- **Node.js** (14+)
- **React Native** (with additional setup)
- **Electron** applications

Key characteristics:
- Uses libsodium.js (WebAssembly) for cryptography
- Automatic async initialization
- Ktor client for networking
- Full async/await support

## Browser Setup

### Kotlin/JS Project

```kotlin
// build.gradle.kts
kotlin {
    js {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            testTask {
                useKarma {
                    useChrome()
                    // useFirefox()
                    // useSafari()
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation("com.soneso.stellar:stellar-sdk:0.1.0-SNAPSHOT")

                // Optional: UI frameworks
                implementation("org.jetbrains.kotlinx:kotlinx-html:0.9.1")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react:18.2.0-pre.700")
            }
        }
    }
}
```

### HTML Setup

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Stellar KMP SDK Demo</title>
</head>
<body>
    <div id="root"></div>

    <!-- Your compiled JS will be included automatically -->
    <script src="stellar-app.js"></script>
</body>
</html>
```

### Basic Browser Usage

```kotlin
// src/jsMain/kotlin/Main.kt
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.horizon.HorizonServer
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement

fun main() {
    val scope = MainScope()

    window.onload = {
        setupUI(scope)
    }
}

fun setupUI(scope: CoroutineScope) {
    val generateBtn = document.getElementById("generateBtn") as? HTMLButtonElement
    val resultDiv = document.getElementById("result") as? HTMLDivElement

    generateBtn?.onclick = {
        scope.launch {
            try {
                val keypair = KeyPair.random()
                resultDiv?.innerHTML = """
                    <h3>Generated Keypair</h3>
                    <p><strong>Account ID:</strong> ${keypair.getAccountId()}</p>
                    <p><strong>Can Sign:</strong> ${keypair.canSign()}</p>
                    <p><strong>Crypto Library:</strong> ${KeyPair.getCryptoLibraryName()}</p>
                """.trimIndent()
            } catch (e: Exception) {
                resultDiv?.innerHTML = "<p>Error: ${e.message}</p>"
            }
        }
    }
}
```

## Node.js Setup

### Package Configuration

```json
{
  "name": "stellar-kmp-node",
  "version": "1.0.0",
  "main": "stellar-app.js",
  "scripts": {
    "start": "node stellar-app.js"
  },
  "dependencies": {
    "libsodium-wrappers": "0.7.13"
  }
}
```

### Kotlin/JS for Node

```kotlin
// build.gradle.kts
kotlin {
    js {
        nodejs {
            testTask {
                useMocha {
                    timeout = "30000"  // 30 seconds for async tests
                }
            }
        }
        binaries.executable()
    }
}
```

### Node.js Usage

```kotlin
// src/jsMain/kotlin/NodeApp.kt
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.HorizonServer
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("Stellar SDK on Node.js")
    println("Crypto Library: ${KeyPair.getCryptoLibraryName()}")

    // Generate keypair
    val keypair = KeyPair.random()
    println("Generated Account: ${keypair.getAccountId()}")

    // Connect to Horizon
    val server = HorizonServer("https://horizon-testnet.stellar.org")

    try {
        // Fund on testnet
        val funded = FriendBot.fundAccount(
            keypair.getAccountId(),
            Network.TESTNET
        )

        if (funded) {
            println("Account funded successfully!")

            // Load account
            val account = server.loadAccount(keypair.getAccountId())
            println("Balance: ${account.balances[0].balance} XLM")
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    } finally {
        server.close()
    }
}
```

## Cryptography Details

### libsodium.js Implementation

The JavaScript platform uses libsodium.js (WebAssembly):

```kotlin
// Internal implementation (in jsMain)
@JsModule("libsodium-wrappers")
@JsNonModule
external object sodium {
    fun ready(): Promise<Unit>
    fun crypto_sign_seed_keypair(seed: Uint8Array): KeyPair
    fun crypto_sign_keypair(): KeyPair
    fun crypto_sign_detached(
        message: Uint8Array,
        secretKey: Uint8Array
    ): Uint8Array
    fun crypto_sign_verify_detached(
        signature: Uint8Array,
        message: Uint8Array,
        publicKey: Uint8Array
    ): Boolean
}

actual object Ed25519 {
    private var initialized = false

    private suspend fun ensureInitialized() {
        if (!initialized) {
            sodium.ready().await()
            initialized = true
        }
    }

    actual suspend fun generatePrivateKey(): ByteArray {
        ensureInitialized()
        val keypair = sodium.crypto_sign_keypair()
        return keypair.privateKey.toByteArray()
    }

    actual suspend fun sign(
        message: ByteArray,
        privateKey: ByteArray
    ): ByteArray {
        ensureInitialized()
        val signature = sodium.crypto_sign_detached(
            message.toUint8Array(),
            privateKey.toUint8Array()
        )
        return signature.toByteArray()
    }
}
```

### Async Initialization

```kotlin
// The SDK handles initialization automatically
suspend fun example() {
    // No manual initialization needed
    val keypair = KeyPair.random()  // SDK initializes libsodium internally
}

// For manual control (advanced)
import kotlinx.coroutines.await

suspend fun manualInit() {
    // Wait for libsodium
    sodium.ready().await()

    // Now all crypto operations are ready
    val keypair = KeyPair.random()
}
```

## Webpack Configuration

### Custom Webpack Config

```javascript
// webpack.config.js
const path = require('path');

module.exports = {
    entry: './build/js/packages/stellar-app/kotlin/stellar-app.js',
    output: {
        path: path.resolve(__dirname, 'dist'),
        filename: 'stellar-bundle.js',
        library: 'StellarSDK',
        libraryTarget: 'umd'
    },
    resolve: {
        fallback: {
            "crypto": false,
            "stream": false,
            "buffer": false
        }
    },
    module: {
        rules: [
            {
                test: /\.wasm$/,
                type: 'webassembly/async'
            }
        ]
    },
    experiments: {
        asyncWebAssembly: true
    }
};
```

### Production Build

```kotlin
// build.gradle.kts
kotlin {
    js {
        browser {
            webpackTask {
                cssSupport {
                    enabled.set(true)
                }
                mode = org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode.PRODUCTION
            }
            distribution {
                directory = File("$projectDir/dist")
            }
        }
    }
}
```

## React Integration

### React Component

```typescript
// StellarWallet.tsx
import React, { useState, useEffect } from 'react';

// Import the compiled Kotlin/JS module
const StellarSDK = require('./stellar-sdk');

interface KeyPairInfo {
    accountId: string;
    canSign: boolean;
}

export const StellarWallet: React.FC = () => {
    const [keypair, setKeypair] = useState<KeyPairInfo | null>(null);
    const [loading, setLoading] = useState(false);
    const [balance, setBalance] = useState<string>('0');

    const generateKeypair = async () => {
        setLoading(true);
        try {
            // Call Kotlin SDK
            const kp = await StellarSDK.com.soneso.stellar.sdk.KeyPair.random();
            setKeypair({
                accountId: kp.getAccountId(),
                canSign: kp.canSign()
            });
        } catch (error) {
            console.error('Failed to generate keypair:', error);
        } finally {
            setLoading(false);
        }
    };

    const checkBalance = async () => {
        if (!keypair) return;

        try {
            const server = new StellarSDK.com.soneso.stellar.sdk.horizon.HorizonServer(
                'https://horizon-testnet.stellar.org'
            );

            const account = await server.loadAccount(keypair.accountId);
            setBalance(account.balances[0].balance);

            server.close();
        } catch (error) {
            console.error('Failed to load account:', error);
            setBalance('Not funded');
        }
    };

    return (
        <div>
            <h2>Stellar Wallet</h2>

            <button onClick={generateKeypair} disabled={loading}>
                {loading ? 'Generating...' : 'Generate Keypair'}
            </button>

            {keypair && (
                <div>
                    <p>Account ID: {keypair.accountId}</p>
                    <p>Can Sign: {keypair.canSign ? 'Yes' : 'No'}</p>
                    <p>Balance: {balance} XLM</p>
                    <button onClick={checkBalance}>Check Balance</button>
                </div>
            )}
        </div>
    );
};
```

### React Hook

```typescript
// useStellar.ts
import { useState, useCallback } from 'react';

const StellarSDK = require('./stellar-sdk');

export const useStellar = () => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const generateKeypair = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            return await StellarSDK.com.soneso.stellar.sdk.KeyPair.random();
        } catch (e) {
            setError(e.message);
            throw e;
        } finally {
            setLoading(false);
        }
    }, []);

    const signMessage = useCallback(async (keypair: any, message: string) => {
        setLoading(true);
        setError(null);
        try {
            const data = new TextEncoder().encode(message);
            return await keypair.sign(data);
        } catch (e) {
            setError(e.message);
            throw e;
        } finally {
            setLoading(false);
        }
    }, []);

    return {
        generateKeypair,
        signMessage,
        loading,
        error
    };
};
```

## Vue Integration

### Vue Component

```vue
<!-- StellarWallet.vue -->
<template>
  <div>
    <h2>Stellar Wallet</h2>

    <button @click="generateKeypair" :disabled="loading">
      {{ loading ? 'Generating...' : 'Generate Keypair' }}
    </button>

    <div v-if="keypair">
      <p>Account ID: {{ keypair.accountId }}</p>
      <p>Can Sign: {{ keypair.canSign ? 'Yes' : 'No' }}</p>
      <p>Balance: {{ balance }} XLM</p>
      <button @click="checkBalance">Check Balance</button>
    </div>

    <div v-if="error" class="error">
      Error: {{ error }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

// Import compiled Kotlin/JS
const StellarSDK = require('./stellar-sdk');

const keypair = ref(null);
const loading = ref(false);
const balance = ref('0');
const error = ref(null);

const generateKeypair = async () => {
  loading.value = true;
  error.value = null;

  try {
    const kp = await StellarSDK.com.soneso.stellar.sdk.KeyPair.random();
    keypair.value = {
      accountId: kp.getAccountId(),
      canSign: kp.canSign(),
      instance: kp  // Keep reference for signing
    };
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
};

const checkBalance = async () => {
  if (!keypair.value) return;

  try {
    const server = new StellarSDK.com.soneso.stellar.sdk.horizon.HorizonServer(
      'https://horizon-testnet.stellar.org'
    );

    const account = await server.loadAccount(keypair.value.accountId);
    balance.value = account.balances[0].balance;

    server.close();
  } catch (e) {
    balance.value = 'Not funded';
    error.value = e.message;
  }
};
</script>
```

## Next.js Integration

### API Route

```typescript
// pages/api/stellar/generate.ts
import type { NextApiRequest, NextApiResponse } from 'next';

// Import server-side SDK
const StellarSDK = require('../../../stellar-sdk');

export default async function handler(
  req: NextApiRequest,
  res: NextApiResponse
) {
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  try {
    // Generate keypair server-side
    const keypair = await StellarSDK.com.soneso.stellar.sdk.KeyPair.random();

    // Only return public info
    res.status(200).json({
      accountId: keypair.getAccountId(),
      // Never send private key to client!
    });
  } catch (error) {
    res.status(500).json({ error: 'Failed to generate keypair' });
  }
}
```

### Client Component

```typescript
// app/stellar-wallet.tsx
'use client';

import { useState } from 'react';

export default function StellarWallet() {
  const [accountId, setAccountId] = useState('');
  const [loading, setLoading] = useState(false);

  const generateAccount = async () => {
    setLoading(true);
    try {
      const response = await fetch('/api/stellar/generate', {
        method: 'POST'
      });
      const data = await response.json();
      setAccountId(data.accountId);
    } catch (error) {
      console.error('Failed to generate account:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <button onClick={generateAccount} disabled={loading}>
        Generate Account
      </button>
      {accountId && <p>Account: {accountId}</p>}
    </div>
  );
}
```

## Performance Optimization

### Web Worker Usage

```javascript
// stellar-worker.js
importScripts('./stellar-sdk.js');

self.onmessage = async function(e) {
  const { type, data } = e.data;

  switch (type) {
    case 'GENERATE_KEYPAIR':
      try {
        const keypair = await StellarSDK.KeyPair.random();
        self.postMessage({
          type: 'KEYPAIR_GENERATED',
          data: {
            accountId: keypair.getAccountId()
          }
        });
      } catch (error) {
        self.postMessage({
          type: 'ERROR',
          error: error.message
        });
      }
      break;

    case 'SIGN_TRANSACTION':
      // Sign transaction in worker
      break;
  }
};
```

### Bundle Size Optimization

```javascript
// webpack.config.js
module.exports = {
  optimization: {
    splitChunks: {
      chunks: 'all',
      cacheGroups: {
        stellar: {
          test: /[\\/]stellar-sdk[\\/]/,
          name: 'stellar-sdk',
          priority: 10
        },
        libsodium: {
          test: /[\\/]libsodium[\\/]/,
          name: 'libsodium',
          priority: 20
        }
      }
    },
    minimize: true,
    usedExports: true,
    sideEffects: false
  }
};
```

### Lazy Loading

```typescript
// Lazy load the SDK
const loadStellarSDK = async () => {
  const { KeyPair, HorizonServer } = await import('./stellar-sdk');
  return { KeyPair, HorizonServer };
};

// Use when needed
const handleGenerateClick = async () => {
  const { KeyPair } = await loadStellarSDK();
  const keypair = await KeyPair.random();
  // ...
};
```

## Troubleshooting

### Common Issues

#### libsodium Not Initialized

```kotlin
// The SDK handles this automatically, but if you see errors:
suspend fun ensureSodiumReady() {
    try {
        // Force initialization
        val keypair = KeyPair.random()
    } catch (e: Exception) {
        console.error("libsodium initialization failed:", e)
        // Retry or show error to user
    }
}
```

#### WASM Loading Issues

```html
<!-- Ensure proper MIME type -->
<script>
  // For older browsers
  if (!WebAssembly.instantiateStreaming) {
    WebAssembly.instantiateStreaming = async (resp, importObject) => {
      const source = await (await resp).arrayBuffer();
      return await WebAssembly.instantiate(source, importObject);
    };
  }
</script>
```

#### Content Security Policy

```html
<!-- Allow WASM execution -->
<meta http-equiv="Content-Security-Policy"
      content="script-src 'self' 'wasm-unsafe-eval';">
```

#### Webpack Polyfills

```javascript
// webpack.config.js
module.exports = {
  resolve: {
    fallback: {
      "crypto": require.resolve("crypto-browserify"),
      "stream": require.resolve("stream-browserify"),
      "buffer": require.resolve("buffer/")
    }
  },
  plugins: [
    new webpack.ProvidePlugin({
      Buffer: ['buffer', 'Buffer'],
      process: 'process/browser'
    })
  ]
};
```

### Debug Logging

```kotlin
// Enable debug logging
external val console: Console

actual object Logger {
    actual fun debug(message: String) {
        console.log("[DEBUG] $message")
    }

    actual fun error(message: String, error: Throwable?) {
        console.error("[ERROR] $message", error)
    }
}

// Use in your code
Logger.debug("Generating keypair...")
Logger.error("Failed to sign", exception)
```

### Browser Compatibility

```javascript
// Check for required features
function checkBrowserSupport() {
  const features = {
    webAssembly: typeof WebAssembly !== 'undefined',
    crypto: typeof crypto !== 'undefined',
    textEncoder: typeof TextEncoder !== 'undefined'
  };

  const unsupported = Object.entries(features)
    .filter(([_, supported]) => !supported)
    .map(([feature]) => feature);

  if (unsupported.length > 0) {
    console.error('Browser missing features:', unsupported);
    return false;
  }

  return true;
}
```

---

**Navigation**: [← JVM Platform](jvm.md) | [iOS Platform →](ios.md)