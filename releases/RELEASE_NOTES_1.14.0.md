# Release Notes - Version 1.14.0

## Overview

Version 1.14.0 validates every variable-length XDR array count against the remaining bytes before a decoder allocates the list, so a hostile count fails with `IllegalArgumentException` and no count-sized allocation takes place. Horizon SSE streams carry the same client identification as every other Horizon request. The WebAuthn CBOR parser's length checks cannot overflow. Only the latest API docs are hosted. No public signature was removed or changed; the SSE query-parameter change is a wire change and is listed under Migration.

## Added

- `XdrReader.readArrayLength()` reads the element count of a variable-length XDR array and validates it against the remaining bytes. A negative count, or one above a quarter of the remaining bytes, raises `IllegalArgumentException`. Every XDR array element occupies at least 4 bytes, so a count the buffer can hold is never rejected (#73).

## Changed

### XDR array counts

Every generated XDR decoder reads variable-length array counts through `readArrayLength()`, 93 sites in 71 generated types, and so does SEP-45 `WebAuthForContracts.decodeAuthorizationEntries`. A hostile count fails at the count with one of two messages:

- `XDR array count cannot be negative, got <n>`
- `XDR array count <n> exceeds the maximum of <m> for the <r> byte(s) remaining at offset <o>`

At the binary decoder level, rejected counts raise `IllegalArgumentException`. The check applies to `decode(XdrReader)`, `fromXdrBase64`, and every SDK path that decodes XDR received from a server or a caller, such as transaction envelopes and results, contract specs, and SEP-10 and SEP-45 challenges. Higher-level APIs keep their own exception wrapping or fallback: SEP-10 `WebAuth.validateChallenge` reports `GenericChallengeValidationException`, `SorobanContractParser` stops at the first spec entry it cannot decode, and `TransactionResponse.getCreatedClaimableBalanceId` returns `null`. `decodeAuthorizationEntries`, and `jwtToken` through it, report the failure as `Sep45InvalidArgsException("Failed to decode authorization entries: XDR array count ...")`. XDR-JSON decoding reads no counts from bytes and is unaffected (#73).

### Client identification on Horizon SSE streams

Stream requests on JVM/Android and native (iOS/macOS) carry exactly the identification the HTTP client configures, as every other Horizon request does. With the default `HorizonServer` client that is the headers `X-Client-Name: kmp-stellar-sdk` and `X-Client-Version: 1.14.0`. The SDK adds no `X-Client-Name` or `X-Client-Version` query parameters to stream URLs; caller-configured parameters are preserved. An injected `HttpClient` that sets its own identification headers has exactly those values on streams, and one that sets none sends none.

Browser (JS) streams use the `EventSource` API, which cannot set request headers, and send the identification as the query parameters `X-Client-Name=kmp-stellar-sdk` and `X-Client-Version=1.14.0`.

REST, Soroban RPC, SEP-10, SEP-45 and OpenZeppelin relayer requests are unchanged. The client name is one internal constant, which the public `OZConstants.CLIENT_NAME` reads; its value `kmp-stellar-sdk` is unchanged (#74).

### API documentation hosting

The Pages workflow deploys the API docs of the tip of `main` to https://soneso.github.io/kmp-stellar-sdk/api/latest/ on every push. Versioned copies are not hosted: `api/0.3.0/`, `api/1.11.0/`, `api/1.12.0/` and `api/1.13.0/` answer 404. IDEs show the KDoc from the published sources jars (#72).

## Fixed

- The WebAuthn CBOR parser compares a byte-string length against the bytes that remain, so the check cannot overflow for a 4-byte CBOR length near `Int.MAX_VALUE`. The parser returns `null` for a malformed attestation object carrying such a length. On Android, key extraction from the attestation object, used when the response carries no usable `publicKey`, raises `WebAuthnException.RegistrationFailed`; the authenticator flags path on Android, Apple and JS returns `AuthenticatorFlags(deviceType = null, backedUp = null)`; `SmartAccountUtils.extractPublicKeyFromRegistration` falls back to pattern matching or raises `ValidationException` (#74).

## Migration

No public signature changed. Four situations call for a check against 1.13.0:

- Operators or proxies keyed on the SSE stream query parameters. At 1.13.0 every JVM/Android and native stream request carried the query parameters `X-Client-Name=kotlin-stellar-sdk` and `X-Client-Version=dev` next to the headers the default client sets. Streams from 1.14.0 do not append these query parameters. Key on the headers `X-Client-Name: kmp-stellar-sdk` and `X-Client-Version` instead. At 1.13.0 browser streams sent `X-Client-Name=kotlin-stellar-sdk` and `X-Client-Version=dev`; from 1.14.0 they send `X-Client-Name=kmp-stellar-sdk` and `X-Client-Version=1.14.0`. Update filters that depend on those values. Browser streams use `EventSource` and do not use the injected `HttpClient`.
- JVM/Android and native apps that inject their own `HttpClient` into `HorizonServer`. At 1.13.0 the SDK appended the two query parameters to stream URLs regardless of the injected client. Streams now carry exactly what that client configures. To identify the app, add the `X-Client-Name` and `X-Client-Version` headers through Ktor's `DefaultRequest` plugin, or any query parameters the receiving integration requires.
- Code that handles malformed XDR array counts. At 1.13.0 a negative count raised the platform's list-capacity `IllegalArgumentException`; an excessive positive count raised `IllegalArgumentException` during the element reads with `XDR decode requires <n> byte(s) at offset <o> but only <r> remain in a <size>-byte buffer`, or, on the JVM, exhausted memory during the list allocation with an `OutOfMemoryError` that a `catch (e: Exception)` did not see, including the one in SEP-45. From 1.14.0 both cases raise `IllegalArgumentException` at the count with the two `XDR array count ...` messages quoted above, and SEP-45 wraps the rejection as `Sep45InvalidArgsException`. Update message matching to the new texts and handle the exception the API you call exposes.
- Bookmarks and external links to versioned API docs. Point them to https://soneso.github.io/kmp-stellar-sdk/api/latest/ or to the KDoc in the published sources jars.

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.14.0`

No public signature was removed or changed; `XdrReader.readArrayLength()` is the only public addition. The SSE identification change is a wire change: JVM/Android and native streams stop appending the SDK's identification query parameters, and browser streams change their values. Integrations that depend on the 1.13.0 query parameters or values must follow Migration.

## References

Full change list: [CHANGELOG](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.14.0/CHANGELOG.md)
