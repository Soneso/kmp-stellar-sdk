# Stellar KMP SDK Documentation

Welcome to the documentation for the Stellar SDK for Kotlin Multiplatform. This documentation covers everything from getting started to advanced implementation details.

## Documentation Structure

### Getting Started
- **[Quick Start Guide](quick-start.md)** - Get running in 30 minutes
- **[Getting Started Guide](getting-started.md)** - Guide to SDK fundamentals

### Learning & Examples
- **[SDK Usage Examples](sdk-usage-examples.md)** - Code examples and patterns
- **[Demo App Guide](demo-app.md)** - Learn from example application

### Architecture & Design
- **[Architecture Guide](architecture.md)** - System architecture, design decisions, and security principles
- **[Platform Guides](platforms/)** - Platform-specific setup and considerations

### Advanced Topics
- **[Advanced Topics](advanced.md)** - Complex patterns and optimizations
- **[Testing Guide](testing.md)** - SDK testing (for contributors)

### Smart Accounts
- **[Smart Accounts Guide](smart-accounts/README.md)** - OpenZeppelin smart account support (passkeys, multi-signer, policies)
- **[API Reference](smart-accounts/api-reference.md)** - Smart account API reference
- **[Onboarding Guide](smart-accounts/onboarding.md)** - Getting started with smart accounts

### Protocols & Standards
- **[SEP Implementations](sep/)** - Stellar Ecosystem Proposal support

## Key Features

This SDK provides an implementation of the Stellar protocol with:

- **Cross-platform support** - JVM (Android/Desktop), JavaScript (Browser/Node.js), iOS, macOS
- **Production-ready cryptography** - Audited libraries on all platforms
- **Full API coverage** - Horizon, Soroban RPC, and more
- **Type-safe APIs** - Leverage Kotlin's type system
- **Async/await support** - Modern coroutines-based API
- **Smart contract interaction** - Full Soroban support

## SDK Capabilities

### Core Features
- Ed25519 keypair generation and management
- StrKey encoding/decoding (G..., S..., C..., M... addresses)
- Transaction building and signing
- Fee bump transactions
- Multi-signature support
- All 27 Stellar operations
- Complete XDR serialization/deserialization

### Horizon API
- Accounts, Assets, Claimable Balances
- Ledgers, Liquidity Pools, Offers
- Operations, Payments, Trades
- Order Books, Path Payments
- Fee Statistics, Health Monitoring
- Server-Sent Events (SSE) streaming

### SEP Support
- SEP-1 (Stellar TOML) - Domain discovery and service configuration
- SEP-2 (Federation Protocol) - Address resolution across domains
- SEP-5 (Key Derivation) - HD wallet support with BIP-39 mnemonics
- SEP-6 (Deposit and Withdrawal API) - Programmatic anchor transfers
- SEP-8 (Regulated Assets) - Issuer-approved transactions
- SEP-9 (Standard KYC Fields) - Standardized customer data fields
- SEP-10 (Web Authentication) - Secure challenge-response authentication
- SEP-12 (KYC API) - Customer information management
- SEP-24 (Hosted Deposit/Withdrawal) - Interactive anchor transfers
- SEP-30 (Account Recovery) - Multi-party account recovery
- SEP-31 (Cross-Border Payments) - Sending Anchor side of cross-border payments
- SEP-38 (Anchor RFQ API) - Quote service for asset exchanges
- SEP-45 (Web Authentication for Contract Accounts) - Contract account authentication
- SEP-46 (Contract Meta) - Parse contract metadata from Wasm bytecode
- SEP-47 (Contract Interface Discovery) - Discover contract-declared SEP support
- SEP-48 (Contract Interface Specification) - Extract contract spec entries (functions, types, events)
- SEP-53 (Sign and Verify Messages) - Off-chain message signing with Ed25519

### Soroban RPC API
- Transaction simulation and resource estimation
- Contract invocation and deployment
- Event queries and filtering
- Ledger entries and contract data retrieval
- Network information and versioning
- Health monitoring and status checks

### Soroban (Smart Contracts)
- ContractClient for easy interaction
- AssembledTransaction lifecycle management
- Authorization handling
- State restoration
- Transaction simulation
- Result parsing with type safety

### Smart Accounts (OpenZeppelin)
- WebAuthn passkey authentication (Android, iOS, macOS, Web)
- Multi-signer authorization (passkey + delegated Ed25519 signers)
- Context rules (Default, CallContract, CreateContract)
- Policies (simple threshold, weighted threshold, spending limit)
- Relayer integration for fee-sponsored transactions
- Indexer integration for credential discovery
- Platform-specific storage adapters

### Security Features
- Production-ready crypto libraries
- Constant-time operations
- Memory-safe implementations
- Extensive input validation
- Network replay protection

## Getting Help

- **[GitHub Issues](https://github.com/Soneso/kmp-stellar-sdk/issues)** - Bug reports and feature requests
- **[Stellar Developers Discord](https://discord.gg/stellardev)** - Community support

## Contributing

See the main [README.md](../README.md) for contribution guidelines.

## License

This SDK is licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.

---

**Navigation**: [Quick Start →](quick-start.md)