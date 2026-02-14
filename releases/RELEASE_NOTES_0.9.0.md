# Release Notes - Version 0.9.0

## Overview

Version 0.9.0 adds SEP-6 (Deposit and Withdrawal API) support, enabling programmatic anchor transfers for fiat on-ramps and off-ramps in the Stellar ecosystem.

## What's New

### SEP-6 Deposit and Withdrawal API

Production-ready client for programmatic anchor transfers without interactive web flows.

**Sep06Service** provides nine API endpoints:
- `info()` - Discover anchor capabilities and supported assets
- `deposit()` - Initiate programmatic deposit
- `depositExchange()` - Deposit with SEP-38 asset conversion
- `withdraw()` - Initiate programmatic withdrawal
- `withdrawExchange()` - Withdrawal with SEP-38 asset conversion
- `fee()` - Query fees (deprecated endpoint)
- `transactions()` - Retrieve transaction history
- `transaction()` - Get single transaction by ID
- `patchTransaction()` - Update transaction with additional info

**Data Classes:**
- 8 request classes for all endpoint parameters
- 23 response classes including `Sep06Transaction` with all 35 SEP-6 fields
- `Sep06TransactionStatus` enum with 17 statuses and helper methods
- `Sep06TransactionKind` enum with 4 kinds and helper methods

**Error Handling:**
- 7 exception types for precise error recovery
- SEP-12 KYC integration via `Sep06CustomerInformationNeededException`
- Customer status tracking via `Sep06CustomerInformationStatusException`

**Integration:**
- SEP-10 JWT authentication for all endpoints
- SEP-38 quote integration for exchange operations
- Claimable balance support for deposits
- Callback notification support via `onChangeCallback`

## Testing

- 93 unit tests with MockEngine
- 12 integration tests against live testnet
- 100% API coverage (95/95 fields)

## Documentation

- User guide: `docs/sep/sep-06.md`
- Compatibility matrix: `compatibility/sep/SEP-0006_COMPATIBILITY_MATRIX.md`
- Updated SEP README with SEP-6 entry

## Platform Support

All platforms continue to be fully supported:
- JVM (Android API 24+, Server Java 11+)
- iOS (iOS 14.0+)
- macOS (macOS 11.0+)
- JavaScript (Browser and Node.js 14+)

## Migration

No breaking changes. SEP-6 is an additive feature.

## Dependencies

No dependency changes from 0.8.0.

---

**Full Changelog**: https://github.com/Soneso/kmp-stellar-sdk/compare/v0.8.0...v0.9.0
