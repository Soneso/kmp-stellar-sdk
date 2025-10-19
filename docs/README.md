# Stellar KMP SDK Documentation

Welcome to the comprehensive documentation for the Stellar SDK for Kotlin Multiplatform. This documentation covers everything from getting started to advanced implementation details.

**This SDK is currently experimental. Do not use in production!**

## Documentation Structure

### Quick Start
- **[Getting Started Guide](getting-started.md)** - Installation, setup, and your first transaction
- **[API Reference](api-reference.md)** - Complete API documentation with examples

### Architecture & Design
- **[Architecture Guide](architecture.md)** - System architecture, design decisions, and security principles
- **[Platform Guides](platforms/)** - Platform-specific setup and considerations
  - [JVM/Android](platforms/jvm.md)
  - [JavaScript/Web](platforms/javascript.md)
  - [iOS](platforms/ios.md)
  - [macOS](platforms/macos.md)

### Development
- **[Demo Apps Guide](sample-apps.md)** - Learn from complete example applications
- **[Soroban RPC Usage](SOROBAN_RPC_USAGE.md)** - Using Soroban smart contracts
- **[Migration Guide](migration.md)** - Migrating from Java Stellar SDK

### Advanced Topics
- **[Advanced Topics](advanced.md)** - Hardware wallets, multi-sig, performance optimization
- **[Troubleshooting](troubleshooting.md)** - Common issues and solutions

## Key Features

This SDK provides a complete implementation of the Stellar protocol with:

- ✅ **Cross-platform support** - JVM, JavaScript, iOS, macOS
- ✅ **Production-ready cryptography** - Audited libraries on all platforms
- ✅ **Comprehensive API coverage** - Horizon, Soroban RPC, and more
- ✅ **Type-safe APIs** - Leverage Kotlin's type system
- ✅ **Async/await support** - Modern coroutines-based API
- ✅ **Smart contract interaction** - Full Soroban support

## Quick Links

### By Use Case

**Building Transactions**
- [Transaction Building](api-reference.md#transaction-building)
- [Operation Types](api-reference.md#operations)
- [Signing Transactions](api-reference.md#signing-transactions)

**Account Management**
- [KeyPair Generation](api-reference.md#keypair)
- [Account Creation](getting-started.md#creating-accounts)
- [Multi-signature Accounts](advanced.md#multi-signature-accounts)

**Smart Contracts**
- [ContractClient](api-reference.md#contractclient)
- [AssembledTransaction](api-reference.md#assembledtransaction)
- [Soroban Authorization](api-reference.md#auth)

**Network Communication**
- [Horizon API](api-reference.md#horizon-server)
- [Soroban RPC](api-reference.md#soroban-server)
- [Server-Sent Events](api-reference.md#server-sent-events)

### By Platform

**Mobile Development**
- [Android Setup](platforms/jvm.md#android-setup)
- [iOS Setup](platforms/ios.md)
- [Demo Mobile Apps](sample-apps.md#android)

**Web Development**
- [Browser Setup](platforms/javascript.md#browser-setup)
- [Node.js Setup](platforms/javascript.md#nodejs-setup)
- [Demo Web App](sample-apps.md#web-javascript)

**Server Development**
- [JVM Server Setup](platforms/jvm.md#server-setup)
- [Transaction Submission](api-reference.md#submitting-transactions)

## Current Implementation Status

### Core Features ✅
- Ed25519 keypair generation and management
- StrKey encoding/decoding (G..., S..., C..., M... addresses)
- Transaction building and signing
- Fee bump transactions
- Multi-signature support
- All 27 Stellar operations
- Complete XDR serialization/deserialization

### Horizon API ✅
- Accounts, Assets, Claimable Balances
- Ledgers, Liquidity Pools, Offers
- Operations, Payments, Trades
- Order Books, Path Payments
- Fee Statistics, Health Monitoring
- Server-Sent Events (SSE) streaming

### Soroban (Smart Contracts) ✅
- ContractClient for easy interaction
- AssembledTransaction lifecycle management
- Authorization handling
- State restoration
- Transaction simulation
- Result parsing with type safety

### Security Features ✅
- Production-ready crypto libraries
- Constant-time operations
- Memory-safe implementations
- Comprehensive input validation
- Network replay protection

## Getting Help

- **[Troubleshooting Guide](troubleshooting.md)** - Common issues and solutions
- **[GitHub Issues](https://github.com/your-repo/issues)** - Bug reports and feature requests
- **[Stellar Developers Discord](https://discord.gg/stellardev)** - Community support

## Contributing

See the main [README.md](../README.md) for contribution guidelines.

## License

This SDK is licensed under the Apache License 2.0. See [LICENSE](../LICENSE) for details.

---

**Navigation**: [Getting Started →](getting-started.md)