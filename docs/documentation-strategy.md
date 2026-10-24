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

## Release Policy for Docs

`main`'s user documentation in `docs/` and the skill in `skills/` describe the released SDK, because users read them on GitHub against the SDK they can install. Documentation and skill content for unreleased behavior goes to the `release-docs` branch, not to the feature or fix branch that implements the behavior. Before a release, `main` is merged into `release-docs`, and `release-docs` is merged into the release preparation branch; `release-docs` is never the head of a pull request and is never merged directly into `main`. After the release it is fast-forwarded to `main`. Feature and fix branches carry code, tests, the CHANGELOG entry under `[Unreleased]`, and the version-independent process documents (`AGENTS.md`, `CONTRIBUTING.md`, this strategy) when a fact in them changes. Skill archives and the skill's generated API reference change only during release preparation; a run that regenerates them to check a change restores the committed files.

## Migration Guides

A release gets a migration guide only when its CHANGELOG section carries a `Breaking:` entry. An entry is breaking when a call that compiled and returned a correct result on the previous release stops compiling, returns a different result, or needs a code change to keep working. Breaking kinds are removed or retyped API, a changed default, changed output spelling, a change to the wire form, and a removal. A generated XDR addition is breaking when an exhaustive match over the type stops compiling or skips the new value silently. A deprecation is breaking only when the API also changes behavior. A fix is not breaking, even when a caller worked around the old behavior. That covers a corrected result, a new error for an input that was never valid, a check on a request that could not succeed, and a timeout or limit the specification sets. Additions, refactors, performance work, documentation, and CI changes are not breaking. A `Breaking:` entry and its note describe a situation that real code is in; a case no reasonable caller produces gets neither. The CHANGELOG records every change, breaking or not.

The guide is `docs/migration/X.Y.Z.md`, titled `# Migrating to X.Y.Z`, and is written during release preparation from the final CHANGELOG section of the version. Its introduction reads (the clause on earlier guides is omitted when none exists):

```markdown
This guide covers the observable changes between P.Q.R and X.Y.Z. If you upgrade from an older version, also review the CHANGELOG between your version and P.Q.R and the earlier migration guides, newest first: [A.B.C](A.B.C.md). Each note describes one situation: whether you are affected, how it shows, and what to do.
```

When a guide covers more than one area, its notes are grouped under `## <area>` headings in the order of the CHANGELOG section; a guide with one area stays a flat list. Each note is one bullet for one situation:

1. "If you <what the affected code does>:", naming the API.
2. What happens in X.Y.Z, with the exact type, message, or value.
3. "In P.Q.R <what happened>.", naming the version.
4. The action in the imperative, with the opt-out if one exists, and what stays unaffected.

The previous-version sentence is the only history a note carries. A note stays under 120 words; a note over 150 words is split by situation.

A note carries a fenced before-and-after pair, labeled with both versions, when the action rewrites a call, a type, an override signature, or a multi-step recipe; the after block compiles against X.Y.Z. A one-token action (add `try`, catch a type, pass a flag) stays inline.

Every CHANGELOG `Breaking:` entry has a note, and every note traces to a `Breaking:` entry. The CHANGELOG states what the API does; the note says who is affected and what to do.

The CHANGELOG section of the version opens with `Migration guide: [docs/migration/X.Y.Z.md](docs/migration/X.Y.Z.md)`. The release notes end with a "Migrating to X.Y.Z" line that links the guide at the tag `vX.Y.Z`. `docs/README.md` lists all guides, newest first. A correction of a published guide also updates the release notes of the version.

Review: a reviewer who did not write the guide starts from code that worked on P.Q.R and, note by note, decides whether they are affected, recognizes the symptom, and applies the action without the CHANGELOG or the source. A step that needs either fails the note.

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

### 3. Examples over theory

Every section must have working code examples:
- Complete, runnable examples (no undefined variables)
- Real-world use cases (not abstract demonstrations)
- Comments explain WHY, not WHAT
- Show the happy path first, edge cases in Advanced docs

### 4. Accuracy is critical

All code must:
- Use actual SDK classes, methods, and properties
- Compile and run without modifications
- Be verified against SDK source code
- Use correct types, parameter names, and method signatures

**Verification process**: Cross-reference examples with actual SDK source files before publishing.

### 5. Conciseness over completeness

- Remove filler words and marketing language
- One clear example beats three redundant ones
- Link to Dokka for complete API reference (don't duplicate method signatures)
- Professional technical tone without unnecessary superlatives
- Don't use em dashes; use a comma, a period, or a colon

### 6. Platform Compatibility

Examples must work across all KMP targets:
- Avoid `File` I/O (not available on JavaScript/iOS)
- Use `suspend` functions appropriately
- Show coroutine usage patterns
- Note platform-specific requirements

### 7. One framework per platform

Platform guides show **one primary framework** to avoid decision paralysis:
- **Android**: Compose with Kotlin
- **iOS**: Compose Multiplatform (95% of use cases)
- **macOS**: JVM/Compose Desktop
- **JavaScript**: Kotlin/JS with React
- **JVM Server**: Ktor

**Rationale**: Multiple framework examples create confusion and maintenance burden. Choose the most common/recommended approach.

### 8. No Changelog Residue

Documentation states the current contract. It never tells the story of how the code got there, and never the counterfactual of what the code would do without one of its checks. Sentences shaped like "X happens rather than Y" or "X instead of Y", where Y is a former defect or a hypothetical failure, leak the author's editing context into the reader's head. The reader never imagined Y, learns nothing about the API from it, and is left wondering why it is mentioned. Migration guides are the one exception: each note names the previous version once (see Migration Guides).

Keep a "rather than"/"instead of" contrast only when both branches are real choices or plausible expectations for the reader ("returns null rather than throwing"). Delete it when the second branch exists only in the author's memory of the code.

```
DON'T: The checksum is verified, so a corrupted seed is rejected rather than
       imported as the signing key of a different account.

DO:    The checksum is verified, so a corrupted seed is rejected.
```

The justification for a check ("this guards against X") belongs in the commit message or release notes, not in user documentation.

### 9. Error Handling in Examples

Call the API directly when the input is fixed and known good (hardcoded literals, freshly generated values). A failure there is a programming error. Wrap the call in `try`/`catch` where a value arrives at runtime (user input, network data). Never show runtime input handled without a catch. Explain the convention to readers once in the entry-level guide; other pages follow it without restating it.

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
- Horizon queries, such as accounts and transactions
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
- [ ] All types are correct (for example, `AssetTypeNative`, not `Asset.native`)
- [ ] All examples verified against SDK source code

### Completeness
- [ ] No undefined variables in examples
- [ ] All necessary imports shown
- [ ] Initialization code included
- [ ] Error handling appropriate for context

### Clarity
- [ ] Comments explain WHY, not WHAT
- [ ] No changelog residue: no "rather than"/"instead of" clause whose second branch is former behavior or a hypothetical defect
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
2. **Identify** violations, such as hypothetical code and app architecture
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
├── README.md                      # Documentation hub with learning paths
├── quick-start.md                 # 30-minute fast path
├── getting-started.md             # Full fundamentals guide
├── demo-app.md                    # Demo app guide
├── architecture.md                # SDK design and internals
├── sdk-usage-examples.md          # Practical code patterns
├── addresses.md                   # Addresses and StrKey
├── advanced.md                    # Complex scenarios
├── sep/                           # SEP guides
│   ├── README.md                  # SEP overview
│   ├── sep-01.md                  # Stellar TOML
│   ├── sep-02.md                  # Federation protocol
│   ├── sep-05.md                  # Key derivation methods for Stellar keys
│   ├── sep-06.md                  # Programmatic deposit and withdrawal
│   ├── sep-08.md                  # Regulated assets
│   ├── sep-09.md                  # Standard KYC fields
│   ├── sep-10.md                  # Stellar web authentication
│   ├── sep-12.md                  # KYC API
│   ├── sep-23.md                  # StrKey encoding
│   ├── sep-24.md                  # Interactive anchor/wallet asset transfer
│   ├── sep-29.md                  # Account memo requirements
│   ├── sep-30.md                  # Account recovery
│   ├── sep-31.md                  # Cross-border payments
│   ├── sep-38.md                  # Anchor RFQ API
│   ├── sep-45.md                  # Web authentication for contract accounts
│   ├── sep-51.md                  # XDR-JSON
│   └── sep-53.md                  # Sign and verify messages
├── smart-accounts/                # Smart account kit
│   ├── README.md                  # Smart account overview
│   ├── api-reference.md           # Smart accounts API reference
│   ├── onboarding.md              # Smart accounts: developer onboarding
│   ├── scf/                       # Smart account architecture
│   │   └── oz-sm-architecture.md  # Smart account support for the KMP Stellar SDK
│   ├── webauthn-android.md        # WebAuthn setup: Android
│   ├── webauthn-ios.md            # WebAuthn setup: iOS
│   ├── webauthn-macos.md          # WebAuthn setup: macOS
│   └── webauthn-web.md            # WebAuthn setup: web (browser)
├── platforms/                     # Platform guides
│   ├── README.md                  # Platform overview
│   ├── ios.md                     # iOS + iPadOS
│   ├── javascript.md              # Browser + Node.js
│   ├── jvm.md                     # Android + server JVM
│   └── macos.md                   # macOS desktop
├── migration/                     # Migration guides, one per release
│   ├── 1.12.0.md                  # Migrating to 1.12.0
│   └── 1.15.0.md                  # Migrating to 1.15.0
├── testing.md                     # SDK testing (contributor doc)
└── documentation-strategy.md      # This file
```

`index.html` redirects to the generated API reference under `api/latest/`. `api/index.html` redirects to the same reference. `.nojekyll` is the GitHub Pages marker.

## Related Documents

- [SDK Usage Examples](sdk-usage-examples.md) - Practical code patterns
- [Quick Start Guide](quick-start.md) - 30-minute fast path to first transaction
- [Getting Started Guide](getting-started.md) - Full fundamentals guide
- [Platform Documentation](platforms/README.md) - Platform-specific guides

---

**Last Updated**: 2026-10-13

**Next Review**: 2027-01-13 (Quarterly maintenance)
