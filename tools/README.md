# Tools

This directory contains tools for building and maintaining the Stellar KMP SDK.

## xdrgen-kt

Kotlin Multiplatform code generator for Stellar XDR types.

### Prerequisites

- Ruby 2.7+
- Bundler gem: `gem install bundler`

### Setup

```bash
cd xdrgen-kt
bundle install
```

### Usage

Generate XDR types from Stellar protocol definitions:

```bash
cd xdrgen-kt
./generate.rb /path/to/stellar-xdr/Stellar-*.x
```

This will generate KMP-compatible XDR types directly into:
`stellar-sdk/src/commonMain/kotlin/com/stellar/sdk/xdr/`

The XDR definition files (`.x` files) should be obtained from the [stellar-xdr](https://github.com/stellar/stellar-xdr) repository.

### Generated Code

The generator produces:
- All Stellar protocol XDR types (accounts, transactions, operations, etc.)
- Zero-cost inline value classes for type safety
- Platform-agnostic multiplatform code
- `XdrReader` and `XdrWriter` expect declarations (actual implementations are hand-written per platform)

### Regenerating After Protocol Updates

When Stellar protocol is updated:

1. Get latest XDR definitions from stellar-xdr repository
2. Run the generator with updated `.x` files
3. Test all platforms: `./gradlew check`
4. Commit the generated changes
