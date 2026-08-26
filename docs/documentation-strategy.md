# Documentation Strategy

This document defines the core strategy and principles for the Stellar KMP SDK documentation.

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.horizon.*
```

## Target Audience

All documentation is written for **Group 1: SDK Users** - developers who want to integrate the SDK into their applications.

**Who are SDK users?**
- Mobile app developers (Android, iOS)
- Web developers (Browser, Node.js)
- Backend/server developers (JVM)
- Desktop app developers (macOS, Windows, Linux)
- Developers building wallets, DApps, or Stellar integrations
- AI agents assisting developers

**Contributor documentation** (testing guides, architecture internals, build instructions) exists separately and is not part of the main learning path.

## Core Principles

### 1. Progressive Learning

Documentation follows a structured learning path from novice to expert:

```text
Quick Start → Getting Started → Demo App → Architecture → SDK Usage Examples → Advanced → Soroban RPC
```

Each step assumes knowledge from the previous step. Complexity increases naturally.

### 2. SDK Code Only

Documentation focuses exclusively on SDK usage. Avoid:
- **App architecture code** (ViewModels, Repositories, ConnectivityManagers, custom helpers)
- **Internal implementation details** (Kotlin/Native cinterop, libsodium internals, BouncyCastle internals)
- **Hypothetical code** (classes, methods, or patterns not in the actual SDK)
- **Framework boilerplate** (Swift extensions, Kotlin extensions unless part of SDK)

**Test**: Can a developer copy-paste this code and have it work with just the SDK dependency?

### 3. Examples Over Theory

Every section must have working code examples:
- Complete, runnable examples (no undefined variables)
- Real-world use cases (not abstract demonstrations)
- Comments explain WHY, not WHAT
- Show the happy path first, edge cases in Advanced docs

### 4. Accuracy Is Critical

All code must:
- Use actual SDK classes, methods, and properties
- Compile and run without modifications
- Be verified against SDK source code
- Use correct types, parameter names, and method signatures

**Verification process**: Cross-reference examples with actual SDK source files before publishing.

### 5. Conciseness Over Completeness

- Remove filler words and marketing language
- One clear example beats three redundant ones
- Link to Dokka for complete API reference (don't duplicate method signatures)
- Professional technical tone without unnecessary superlatives

### 6. Platform Compatibility

Examples must work across all KMP targets:
- Avoid `File` I/O (not available on JavaScript/iOS)
- Use `suspend` functions appropriately
- Show coroutine usage patterns
- Note platform-specific requirements clearly

### 7. One Framework Per Platform

Platform guides show **one primary framework** to avoid decision paralysis:
- **Android**: Compose with Kotlin
- **iOS**: Compose Multiplatform (95% of use cases)
- **macOS**: JVM/Compose Desktop
- **JavaScript**: Kotlin/JS with React
- **JVM Server**: Ktor

**Rationale**: Multiple framework examples create confusion and maintenance burden. Choose the most common/recommended approach.

## Content Guidelines

### SDK Usage Documentation

**sdk-usage-examples.md structure:**
- Organized by **use case** (not by class structure)
- Complete working examples with all variables defined
- Links to Dokka for full API reference
- Focus on common patterns (80% use cases)

**What to include:**
- KeyPair generation, import, signing
- Transaction building and submission
- All 27 Stellar operations with real use cases
- Horizon queries (accounts, transactions, etc.)
- Soroban contract interaction
- Multi-signature workflows
- Error handling patterns

**What to exclude:**
- Method signature documentation (Dokka handles this)
- Hypothetical wrapper classes
- App architecture code
- Framework integration patterns (unless SDK-specific)

### Platform Documentation

Platform guides (`docs/platforms/*.md`) follow these rules:

**Structure:**
1. Platform Overview (supported OS versions, key features)
2. Installation (one primary method)
3. Project Setup (configuration, dependencies)
4. Basic Usage (2-3 concise SDK examples)
5. Troubleshooting (common issues only)

**Length**: 200-300 lines maximum per platform

**What to include:**
- Gradle/build configuration for SDK installation
- Platform-specific SDK initialization (if any)
- 2-3 SDK usage examples (KeyPair generation, transaction building)
- Links to demo app for complete examples

**What to exclude:**
- Multiple framework alternatives (pick one)
- App architecture patterns (MVVM, MVI, repositories)
- Custom manager/helper classes
- Platform features unrelated to SDK (biometric auth, keychain, networking layers)
- Code signing, deployment, distribution details
- Performance optimization unrelated to SDK

### Demo App Documentation

The demo app (`demo/` directory) demonstrates:
- Real SDK usage in production-like scenarios
- Compose Multiplatform architecture (95% code sharing)
- All major SDK features across 11 screens
- Platform-specific implementations where necessary

**Purpose**: Show developers a complete working application using the SDK, not teach app architecture.

## Quality Checklist

Before publishing or updating documentation, verify:

### Accuracy
- [ ] All class names match actual SDK
- [ ] All method names match actual SDK
- [ ] All property names match actual SDK
- [ ] All types are correct (e.g., `AssetTypeNative` not `Asset.native`)
- [ ] All examples verified against SDK source code

### Completeness
- [ ] No undefined variables in examples
- [ ] All necessary imports shown
- [ ] Initialization code included
- [ ] Error handling appropriate for context

### Clarity
- [ ] Comments explain WHY, not WHAT
- [ ] Real-world use cases shown
- [ ] Platform compatibility noted
- [ ] Links to related documentation

### Strategy Alignment
- [ ] Targets Group 1 (SDK users)
- [ ] No app architecture code
- [ ] No internal implementation details
- [ ] No hypothetical code
- [ ] One framework per platform (for platform guides)
- [ ] Concise and professional tone

## Common Mistakes to Avoid

### 1. Hypothetical Code

**Wrong:**
```kotlin
// DON'T: This class doesn't exist in the SDK
class StellarManager {
    suspend fun signMessage(message: String): ByteArray {
        // Custom wrapper logic
    }
}
```

**Right:**
```kotlin
// DO: Use actual SDK classes
val keypair = KeyPair.random()
val signature = keypair.sign(data)
```

### 2. App Architecture Code

**Wrong:**
```kotlin
// DON'T: This is app architecture, not SDK usage
class AccountRepository(private val server: HorizonServer) {
    private val cache = ConcurrentHashMap<String, Account>()

    suspend fun getAccount(id: String): Result<Account> {
        cache[id]?.let { return Result.Success(it) }
        // ... caching logic
    }
}
```

**Right:**
```kotlin
val accountId = "GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54"
// DO: Show direct SDK usage
val server = HorizonServer("https://horizon-testnet.stellar.org")
val account = server.loadAccount(accountId)
```

### 3. Multiple Framework Examples

**Wrong:**
```markdown
## iOS Development

### Option A: SwiftUI
[50 lines of SwiftUI example]

### Option B: UIKit
[50 lines of UIKit example]

### Option C: Compose Multiplatform
[50 lines of Compose example]
```

**Right:**
```markdown
## iOS Development

### Compose Multiplatform Setup
[One clear example with Compose - the recommended approach]
```

### 4. Internal Implementation Details

**Wrong:**
```kotlin
// accountId: from the previous steps of this flow
// DON'T: Explain internal implementation
// The SDK uses Kotlin/Native cinterop to call libsodium's
// crypto_sign_detached function which implements Ed25519
// signatures using the ref10 implementation...
```

**Right:**
```kotlin
// DO: Focus on SDK usage
// Sign data with the keypair
val signature = keypair.sign(data)
```

### 5. Over-Explanation

**Wrong:**
```kotlin
// DON'T: Repeat what the code obviously does
// This line calls the random() method on the KeyPair companion object
// which returns a newly generated KeyPair instance with a random seed
val keypair = KeyPair.random()
```

**Right:**
```kotlin
// DO: Explain the purpose or context
// Generate a new keypair for account creation
val keypair = KeyPair.random()
```

## Review Process

### For New Documentation

1. **Write** following this strategy
2. **Verify** against SDK source code
3. **Test** code examples compile and run
4. **Review** against quality checklist
5. **Cross-reference** links and navigation
6. **Publish** and update related docs

### For Existing Documentation

1. **Audit** against quality checklist
2. **Identify** violations (hypothetical code, app architecture, etc.)
3. **Fix** issues systematically
4. **Verify** improvements maintain value
5. **Update** cross-references
6. **Document** changes

### Quarterly Maintenance

Every 3 months:
- Verify SDK method/class names still accurate
- Test code examples against latest SDK version
- Check for broken links
- Review new SDK features for documentation needs
- Gather developer feedback

## Metrics for Success

Good documentation achieves:

**Quantitative:**
- 70%+ reduction from initial drafts (remove bloat)
- Zero undefined variables in examples
- 100% accuracy (method names, class names match SDK)
- All platform guides under 300 lines

**Qualitative:**
- Developers can copy-paste examples and they work
- AI agents can understand and use the SDK correctly
- No confusion about which framework to choose
- Clear learning path from beginner to advanced
- Fast time-to-first-transaction (Quick Start goal)

## Documentation Structure

```text
docs/
├── README.md                    # Documentation hub with learning paths
├── quick-start.md              # 30-minute fast path
├── getting-started.md          # Full fundamentals guide
├── demo-app.md                 # Demo app guide
├── architecture.md             # SDK design and internals
├── sdk-usage-examples.md       # Practical code patterns
├── advanced.md                 # Complex scenarios
├── soroban-rpc-usage.md       # Smart contract development
├── testing.md                  # SDK testing (contributor doc)
├── platforms/
│   ├── README.md              # Platform overview
│   ├── jvm.md                 # Android + Server JVM
│   ├── javascript.md          # Browser + Node.js
│   ├── ios.md                 # iOS + iPadOS
│   └── macos.md               # macOS Desktop
└── documentation-strategy.md   # This file
```

## Related Documents

- [SDK Usage Examples](sdk-usage-examples.md) - Practical code patterns
- [Quick Start Guide](quick-start.md) - 30-minute fast path to first transaction
- [Getting Started Guide](getting-started.md) - Full fundamentals guide
- [Platform Documentation](platforms/README.md) - Platform-specific guides

---

**Last Updated**: 2025-11-11

**Next Review**: 2026-02-11 (Quarterly maintenance)
