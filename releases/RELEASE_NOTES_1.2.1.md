# Release Notes - Version 1.2.1

## Overview

Version 1.2.1 adds SEP-8 support for regulated assets, enabling transactions involving issuer-controlled assets to be submitted to approval servers for regulatory authorization.

## What's New

### SEP-8: Regulated Assets

Full implementation of SEP-8 for regulated asset support:

**Service Discovery**
- Initialize from issuer's stellar.toml via `Sep08Service.fromDomain()`
- Discover regulated assets with approval server URLs and approval criteria
- Verify issuer authorization flags (auth_required, auth_revocable)

**Transaction Approval**
- Submit transactions to approval servers via `postTransaction()`
- Handle all 5 response types: Success, Revised, Pending, ActionRequired, Rejected
- Revised transactions include the modified envelope XDR for direct submission

**Action Flows**
- Complete required user actions (e.g., KYC) via `postAction()`
- Support for multi-step action flows with `NextUrl` chaining
- `Done` response includes the approved transaction

**Exception Handling**
- `Sep08Exception` base exception
- `Sep08IncompleteInitDataException` for missing network/Horizon configuration
- `Sep08InvalidTransactionResponseException` for malformed approval server responses
- `Sep08InvalidActionResponseException` for malformed action URL responses

### Usage Example

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep08.*
import com.soneso.stellar.sdk.horizon.HorizonServer

// Discover regulated assets
val sep08 = Sep08Service.fromDomain("issuer.example.com")
val asset = sep08.regulatedAssets.first()

// Build and sign a payment transaction
val server = HorizonServer("https://horizon-testnet.stellar.org")
val sender = KeyPair.fromSecretSeed("S...")
val sourceAccount = server.accounts().account(sender.getAccountId())

val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
    .addOperation(PaymentOperation("G...", asset.toAsset(), "100"))
    .build()
transaction.sign(sender)

// Submit for approval
val response = sep08.postTransaction(
    tx = transaction.toEnvelopeXdrBase64(),
    approvalServer = asset.approvalServer
)

when (response) {
    is Sep08PostTransactionResponse.Success -> {
        // Submit approved transaction to network
        server.submitTransaction(response.tx)
    }
    is Sep08PostTransactionResponse.Revised -> {
        // Sign and submit revised transaction
    }
    is Sep08PostTransactionResponse.Pending -> {
        // Retry after response.timeout seconds
    }
    is Sep08PostTransactionResponse.ActionRequired -> {
        // Direct user to response.actionUrl
    }
    is Sep08PostTransactionResponse.Rejected -> {
        // Handle rejection: response.error
    }
}
```

## Removed

- Removed `testDeploySACWithSourceAccount` integration test. This test deployed a Stellar Asset Contract using `CONTRACT_ID_PREIMAGE_FROM_ADDRESS` with `CONTRACT_EXECUTABLE_STELLAR_ASSET`, an XDR combination no longer accepted by the network (transactions go PENDING then NOT_FOUND). The correct approach for SAC deployment is `CONTRACT_ID_PREIMAGE_FROM_ASSET`, which remains tested in `testSACWithAsset`.

## Test Coverage

- 95 unit tests for SEP-8 functionality
- 13 integration tests against live Stellar testnet
- Tests passing on JVM, JS Node, JS Browser, macOS native, and iOS simulator

## Platform Support

All platforms fully supported:
- JVM (Android API 24+, Server Java 17+)
- iOS (iOS 14.0+)
- macOS (macOS 11.0+)
- JavaScript (Browser and Node.js 14+)

## Documentation

- SEP-8 guide: `docs/sep/sep-08.md`
- Compatibility matrix: `compatibility/sep/SEP-0008_COMPATIBILITY_MATRIX.md` (100% coverage, 22/22 features)

## Dependencies

No new external dependencies added. Uses existing Ktor HTTP client and kotlinx-serialization.

---

**Full Changelog**: https://github.com/Soneso/kmp-stellar-sdk/compare/v1.2.0...v1.2.1
