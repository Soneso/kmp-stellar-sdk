# Release Notes - Version 1.11.0

## Overview

Version 1.11.0 adds SEP-51 (XDR-JSON) support across the entire XDR type system, updates the XDR definitions to CAP-0083 / CAP-0085, and carries correctness fixes across XDR decoding, transaction submission, SEP-6, SEP-10, and the smart-account kit. The SEP-51 surface is additive. One source-breaking change (new XDR union arms) and ten behavior changes are listed under Compatibility.

## Added

### SEP-51 (XDR-JSON)

- Every generated XDR type gains `toXdrJson(): String` and `Companion.fromXdrJson(String)` at the string boundary, and `toXdrJsonElement(): JsonElement` and `Companion.fromXdrJsonElement(JsonElement)` at the tree boundary, symmetric with the existing base64 family. Any XDR value converts to the canonical JSON rendering SEP-0051 defines and back without loss.
- Output is compact and in XDR declaration order, so equal values produce byte-identical documents. Stellar-specific types render in their specified string forms: strkeys, asset codes, and 128/256-bit integers as base-10 strings.
- Input is strict. Decoding raises `IllegalArgumentException` on malformed input, naming the type and the offending key. A struct accepts only the keys it declares, an object must name each key once, and supplying both `type` and its historical `type_` spelling is rejected as a duplicate. `$schema` is the one property accepted without being declared.
- Enum member, struct key, and union arm names are verified name-by-name against the XDR-JSON reference implementation, and the emitted Kotlin is checked back against that frozen table. A vendored corpus generated from the reference implementation pins cross-tooling compatibility.
- The methods are emitted by the XDR generator, so they track the XDR pin.
- User guide at [docs/sep/sep-51.md](../docs/sep/sep-51.md); every example in it is executed and asserted by a test.

### XDR definitions

- Updated to stellar-xdr `911c935` (CAP-0083 / CAP-0085): new `ContractExecutableXdr.ExternalRef` arm with `ContractExecutableExternalRefXdr`, new `SCValXdr.ExecutableTag` arm carrying an `SCString`, and new `StellarValueExtXdr.ProposedValue` arm. `Scv` gains `toExecutableTag` / `fromExecutableTag`.

## Changed

- `kotlinx-serialization-json` moves from `implementation` to `api`: `JsonElement` appears in the public signatures of `toXdrJsonElement` and `fromXdrJsonElement`. The library was already a transitive runtime dependency through Ktor, so this changes the consumer compile classpath only.

## Fixed

- **SEP-45 and SEP-10 token submissions are never auto-retried.** The default HTTP client's retry-on-server-error resubmitted the one-time challenge; SEP-45 servers consume the challenge nonce on the first attempt, so the retry could never succeed and replaced the original error with a misleading "invalid nonce" rejection. Both token POSTs now disable retries at the request level; challenge requests keep theirs. Retrying a failed submission means a fresh challenge round via `jwtToken()`.
- **`ContractClient.deploy` and `deployFromWasmId` no longer race the RPC after deployment.** The returned client's spec is loaded from the uploaded code before deploying, so a successful deployment cannot surface as "Contract spec not found" when the RPC's ledger-entry ingestion runs behind transaction status. Constructor arguments that cannot be converted — the spec is unreadable or declares no `__constructor` — now raise before anything deploys instead of being silently discarded.
- **`ContractClient.deploy`, `install`, and `deployFromWasmId` report refused submissions immediately.** When the network refuses a transaction at send time (for example an inclusion fee below the current minimum, or a full queue), these entry points raise `SendTransactionFailedException` carrying the status and the error result, instead of polling for three minutes and reporting a misleading NOT_FOUND.
- **Contract-deployment salts** come from the platform CSPRNG instead of `kotlin.random.Random`. The salt and the deployer address determine the deployed contract ID, so the address a deployment would claim was predictable.
- **XDR decoding rejects truncated and malformed input uniformly on every target.** `XdrReader` performed no bounds checking on JavaScript and Native; on JavaScript, malformed XDR decoded into a bogus object instead of raising. All targets now validate lengths before allocating and throw `IllegalArgumentException`.
- **The SEP-29 memo-required check now runs on transaction submission.** The previous envelope parser misread real envelopes and the check never fired. With the check active, submitting a memo-less transaction performs one Horizon account lookup per distinct destination unless `skipMemoRequiredCheck` is set.
- **SEP-10 `validateChallenge` verifies the challenge's source account is the server account**, as SEP-10 requires; a mismatch raises the new `InvalidTransactionSourceAccountException`. A client-domain `stellar.toml` lacking a `SIGNING_KEY` is also reported as such instead of as a load failure.
- **SEP-6 `patchTransaction` matches the specification**: it targets `PATCH TRANSFER_SERVER/transactions/:id` and nests updated fields under a `transaction` key. A spec-conforming anchor does not serve the previous request shape.
- **`Transaction.isSorobanTransaction` classifies by operation type only**, matching its documented behavior, so a classic operation with Soroban transaction data attached no longer passes the `assembleTransaction` guard.
- **`Price.fromString` reports values it cannot approximate** instead of surfacing a misleading constructor error.
- **Horizon SSE streams recover from disconnects** instead of retrying against a duplicated request path; building a request URL is now idempotent.
- **`AccountDataResponse.decodedString` raises on invalid UTF-8** and `decodedStringOrNull` returns null, as documented, instead of substituting replacement characters.
- **Soroban RPC responses that cannot be deserialized** raise `IllegalArgumentException` with the underlying cause, as documented.
- **Smart-account kit**: `multiSignerTransfer` validates input before the first network round-trip; a transaction is reported as signed only when a signature was produced, so passed-through auth entries no longer emit `TransactionSigned` events or bump credential timestamps; and relayer responses with unexpected JSON shapes return a failed `RelayerResponse` instead of raising out of the parser.

## Platform Support

No platform support changes in this release. Continued support:

- **JVM** (Android API 24+, Server JDK 17+)
- **iOS** 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS** 11.0+ (macosX64, macosArm64)
- **JavaScript** (Browser via WebAssembly, Node.js 14+)

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.11.0`
- One source-breaking change: the XDR update adds arms to the sealed classes `SCValXdr`, `ContractExecutableXdr`, and `StellarValueExtXdr`. A `when` over these types without an `else` branch no longer compiles on upgrade; add branches for the new arms or an `else`.
- Ten behavior changes to be aware of:
  - `ContractClient.deploy`, `install`, and `deployFromWasmId` raise `SendTransactionFailedException` (a `ContractException`) when the network refuses a transaction at send time. This case previously surfaced as `IllegalStateException` after the polling window; code catching `IllegalStateException` around these calls no longer catches it.
  - `ContractClient.deploy` with constructor arguments raises before deploying when the arguments cannot be converted (the spec is unreadable or declares no `__constructor`), instead of deploying without them.
  - Malformed XDR now raises `IllegalArgumentException` on every target. On JavaScript, code that previously received a silently decoded object from malformed input now sees an exception; on the JVM, the exception type for malformed XDR changes from target-specific errors.
  - The SEP-29 memo-required check is active on submission. Memo-less payments to accounts that require one now raise `AccountRequiresMemoException` before submission, and the check performs Horizon account lookups unless `skipMemoRequiredCheck` is set.
  - SEP-6 `patchTransaction` sends the spec-conforming request shape. Anchors that served only the previous non-spec shape will reject it.
  - `AccountDataResponse.decodedString` raises `CharacterCodingException` on data entries that are not valid UTF-8 instead of returning mojibake.
  - SEP-10 `validateChallenge` rejects a challenge whose transaction source account is not the server account, raising `InvalidTransactionSourceAccountException`. Such challenges previously passed validation, so authentication against a non-conforming anchor can now fail.
  - `Transaction.isSorobanTransaction` classifies by operation type only: a classic operation with Soroban transaction data attached is no longer classified as Soroban and is rejected by `assembleTransaction`.
  - Soroban RPC responses that cannot be deserialized raise `IllegalArgumentException` instead of the HTTP client's content-conversion exception, and relayer responses with unexpected JSON shapes return a failed `RelayerResponse` instead of throwing. Both are catch-clause changes.
  - The smart-account `TransactionSigned` event carries a null credential when every auth entry was passed through unsigned, and the credential's last-used timestamp is not bumped in that case.
- `kotlinx-serialization-json` joins the consumer compile classpath through the `api` scope. No existing public API is renamed or removed.

## References

- Changelog: [CHANGELOG.md](../CHANGELOG.md)
- SEP-51 guide: [docs/sep/sep-51.md](../docs/sep/sep-51.md)
- SEP-51 compatibility matrix: [compatibility/sep/SEP-0051_COMPATIBILITY_MATRIX.md](../compatibility/sep/SEP-0051_COMPATIBILITY_MATRIX.md)
