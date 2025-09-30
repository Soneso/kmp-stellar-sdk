# Tools

This directory contains tools for building and maintaining the Stellar KMP SDK.

## xdrgen-kt

Kotlin Multiplatform code generator for Stellar XDR types.

### Prerequisites

- Ruby 2.7+
- Bundler gem: `gem install bundler` (usually pre-installed with Ruby)

### First-Time Setup

```bash
cd tools/xdrgen-kt
bundle install --path vendor/bundle
```

This installs all Ruby dependencies locally in `vendor/bundle` (which is gitignored).

### Usage

Generate XDR types from Stellar protocol definitions:

```bash
cd tools/xdrgen-kt
bundle exec ruby generate.rb /Users/chris/projects/Stellar/stellar-xdr/Stellar-*.x
```

This will generate KMP-compatible XDR types directly into:
`stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/xdr/`

The XDR definition files (`.x` files) should be obtained from the [stellar-xdr](https://github.com/stellar/stellar-xdr) repository.

**Note:** The path to stellar-xdr is currently: `/Users/chris/projects/Stellar/stellar-xdr`

### Generated Code

The generator produces:
- **467 XDR type files** covering all Stellar protocol types (accounts, transactions, operations, ledger entries, etc.)
- Zero-cost inline value classes for type safety (using `@kotlin.jvm.JvmInline`)
- Platform-agnostic multiplatform code compatible with JVM, JS, and Native
- `XdrReader` and `XdrWriter` expect declarations (actual implementations are hand-written per platform in `jvmMain`, `jsMain`, and `nativeMain`)

### Regenerating After Protocol Updates

When Stellar protocol is updated:

1. Pull latest changes from the stellar-xdr repository
2. Run the generator with updated `.x` files:
   ```bash
   cd tools/xdrgen-kt
   bundle exec ruby generate.rb /Users/chris/projects/Stellar/stellar-xdr/Stellar-*.x
   ```
3. Test all platforms: `cd ../.. && ./gradlew check`
4. Review changes and commit the generated files

### Technical Details

- **Generator:** Modified version of [stellar/xdrgen](https://github.com/stellar/xdrgen) with KMP-specific enhancements
- **Output format:** Kotlin with inline value classes instead of typealiases
- **Union types:** Implemented as sealed classes with proper discriminant wrapping
- **Typedef handling:** Chain resolution for correct type literal suffixes (u/uL)
- **Platform compatibility:** All generated code is common (expect/actual only for XdrReader/XdrWriter)
