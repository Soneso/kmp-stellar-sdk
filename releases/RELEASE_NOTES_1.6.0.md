# Release Notes - Version 1.6.0

## Overview

Version 1.6.0 adds SEP-31 (Cross-Border Payments) support and extracts a shared callback-signature verifier covering both SEP-12 and SEP-31. SEP-1's local-development story is also improved: `StellarToml.fromDomain` now allows HTTP over loopback authorities so an Anchor Platform instance can be used without a TLS cert.

This release introduces one deprecation in the SEP-12 namespace; see [Migration Notes](#migration-notes) at the bottom of this document.

## Added

### SEP-31 Cross-Border Payments (Sending Anchor)

`Sep31Service` exposes the Sending Anchor side of [SEP-31](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0031.md). It talks to a Receiving Anchor's `DIRECT_PAYMENT_SERVER` to discover supported assets, initiate cross-border payments, track lifecycle status, and register signed status callbacks.

Public surface:

- `Sep31Service.fromDomain(domain)` — discovers `DIRECT_PAYMENT_SERVER` from `stellar.toml`.
- `service.info(jwt, lang?)` — supported assets, fee model, KYC requirements, funding methods.
- `service.postTransactions(request, jwt)` — initiates a payment, returns on-chain delivery instructions.
- `service.getTransaction(id, jwt)` — fetches the current transaction state.
- `service.putTransactionCallback(id, callbackUrl, jwt)` — registers a status notification URL.
- `service.patchTransaction(id, fields, jwt)` — legacy info-update endpoint (annotated `@Deprecated`).

Typed exception hierarchy rooted at `Sep31Exception` distinguishes the `customer_info_needed` / `transaction_info_needed` 400 variants, 401/403 auth failures, the two 404 paths (`Sep31TransactionNotFoundException`, `Sep31TransactionCallbackNotSupportedException`), unknown HTTP statuses, and malformed bodies. Every exception preserves a sanitized error message and (where applicable) the raw response body for debugging while redacting JWT-shaped tokens.

Integration with other SEPs:

- **SEP-10** — JWTs from `WebAuth.jwtToken` authenticate the protected endpoints.
- **SEP-12** — sender/receiver KYC registration produces the `senderId` / `receiverId` opaque strings passed to `postTransactions`.
- **SEP-38** — firm quotes from `QuoteService.postQuote` produce the optional `quoteId` for quoted transactions.

Documentation:

- User guide: [docs/sep/sep-31.md](../docs/sep/sep-31.md) — covers the full SEP-10 -> SEP-12 -> SEP-38 -> SEP-31 flow, lifecycle tracking, callbacks, error handling, and the deprecated PATCH endpoint. Code examples are compile-checked against the SDK.
- Agent skill reference: `skills/kmp-stellar-sdk/references/sep-31.md` — same scope, modelled after the other SEP skill references.

### Shared callback signature verifier

`com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier` is a single implementation of the SEP-12 / SEP-31 signed-callback verification protocol. Construct one instance per registered callback URL:

```kotlin
val verifier = CallbackSignatureVerifier(
    signingKey = anchorSigningKey,          // SIGNING_KEY from anchor's stellar.toml
    registeredCallbackUrl = "https://myapp.example/callbacks/sep31",
    // freshnessSeconds defaults to 120 (spec recommendation; max 600 for clock skew)
)

val result = verifier.verify(
    signatureHeader = request.header("Signature"),
    xStellarSignatureHeader = request.header("X-Stellar-Signature"),
    body = request.bodyAsText(),
)
```

`Result` is a sealed type with arms `Valid`, `MissingHeader`, `MalformedHeader`, `Stale(ageSeconds: Long)`, and `SignatureMismatch` so callers can distinguish replay from forgery in logs.

The verifier pins the canonical host from the registered URL (port stripped), so a forwarded `Host` header cannot redirect signature scope. HTTPS is enforced; HTTP is allowed only for loopback authorities. Freshness is two-sided (`|now - signedTimestamp| <= freshnessSeconds`) so future-dated forgery is rejected as well as replay.

## Changed

### SEP-1 `StellarToml.fromDomain` loopback HTTP allowance

`StellarToml.fromDomain` now accepts HTTP for loopback authorities (`localhost`, `127.0.0.1`, `[::1]`). All other hosts still require HTTPS. This enables local-development workflows against an Anchor Platform instance without provisioning a TLS cert. Production behaviour is unchanged.

### SEP-10 integration test signer migration

The integration test's client-domain signer was migrated from `server-signer.replit.app` to `testsigner.stellargate.com` (source: `Soneso/go-server-signer`). No production code changes; only the integration test fixture moved. The two local-signing variants (`testClientDomainAuthentication`, `testLocalClientDomainSigningDelegate`) were dropped in favour of the remote-delegate test that already exercises the full client-domain path end-to-end.

## Deprecated

### `sep12.CallbackSignatureVerifier`

`com.soneso.stellar.sdk.sep.sep12.CallbackSignatureVerifier` is deprecated. The class remains as a shim with bit-for-bit observable behaviour preserved via internal compatibility flags. New code should use `com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier`.

**Removal schedule:** version 1.8.0, or no earlier than 90 days after the 1.6.0 release date, whichever is later.

## Migration Notes

Two changes ship a small breaking signal — both are limited in scope.

### Callers of `sep12.CallbackSignatureVerifier`

The deprecated class is a drop-in shim. Existing call sites continue to compile and behave identically. To migrate, change the import path:

```kotlin
// Before
import com.soneso.stellar.sdk.sep.sep12.CallbackSignatureVerifier

// After
import com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier
```

The shared class's public constructor takes the same parameters (`signingKey`, `registeredCallbackUrl`, `freshnessSeconds`, `clock`). The `Result` sealed type lives at `CallbackSignatureVerifier.Result` and has the same arms.

The new `Result.Stale` arm exposes `ageSeconds: Long` (signed: positive = past, negative = future-dated). Old code that pattern-matched only on the type still compiles unchanged; code that needs the age value is the new feature, not a migration requirement.

### Callers of `StellarToml.fromDomain` for non-HTTPS hosts

If your code previously caught the exception raised when `stellar.toml` resolved over HTTP for a loopback host, that exception is no longer raised. Loopback HTTP is now accepted. All other hosts still raise as before.

## Platform Support

No platform support changes in this release. Continued support:

- **JVM** (Android API 24+, Server JDK 17+)
- **iOS** 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS** 11.0+ (macosX64, macosArm64)
- **JavaScript** (Browser via WebAssembly, Node.js 14+)

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.6.0`
- Stellar Protocol 23 compatible
- Horizon API: full REST coverage
- Soroban RPC: full method coverage
- SEPs implemented: SEP-1, SEP-2, SEP-5, SEP-6, SEP-8, SEP-9, SEP-10, SEP-12, SEP-24, SEP-30, **SEP-31 (new)**, SEP-38, SEP-45, SEP-46, SEP-47, SEP-48, SEP-53
