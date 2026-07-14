# JavaScript Platform Guide

This guide covers JavaScript-specific setup and usage for both Browser and Node.js environments.

## Table of Contents

- [Platform Overview](#platform-overview)
- [Browser Setup](#browser-setup)
- [Node.js Setup](#nodejs-setup)
- [Webpack Configuration](#webpack-configuration)
- [Troubleshooting](#troubleshooting)

## Platform Overview

The JavaScript implementation works in:
- **Modern Browsers** (Chrome, Firefox, Safari, Edge)
- **Node.js** (14+)
- **React Native** (with additional setup)
- **Electron** applications

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
                implementation("com.soneso.stellar:stellar-sdk:1.9.0")

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
    "libsodium-wrappers-sumo": "0.7.13"
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

## Webpack Configuration

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

## Troubleshooting

### Common Issues

#### libsodium Not Initialized

The SDK handles initialization automatically. If you encounter errors, check browser console for initialization messages.

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
