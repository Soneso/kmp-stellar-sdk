# Release Notes - Version 1.19.0

## Overview

Version 1.19.0 adds `SACClient`, a client for the Stellar Asset Contract of a classic asset, XLM included. `SACClient.forAsset` derives the contract id from the asset and opens the contract with the interface description shipped in the SDK, without an RPC request. `balance` reads a balance, and `decimals`, `name`, and `symbol` read the asset's details. `transfer` builds and simulates a transfer and returns it for you to sign and send. `transferAndSubmit` signs and sends one from the client's account and waits for the result. `toBaseUnits` converts a decimal amount such as `"12.5"` to base units.

A read-only client runs the reads without a keypair or any account on the network. Unit tests and a testnet integration test cover the client. The [usage examples](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.19.0/docs/sdk-usage-examples.md#stellar-asset-contracts-sac) and the Soroban smart contracts reference of agent skill 1.9.0 describe it.

The release also extends the exact XDR round trip of transactions to three more cases. A transaction read with `AbstractTransaction.fromEnvelopeXdr` keeps its `hash()`, and `toEnvelopeXdrBase64()` writes the same bytes back:

- with an `M...` address as its source account: the address stays muxed (#170)
- with a memo text or a Soroban string or symbol that is not valid UTF-8: its bytes stay as they are (#172)
- with extended preconditions that set nothing beyond time bounds: they stay in the extended form (#175)

## Breaking: XDR strings and memo text keep their bytes

`SCStringXdr`, `SCSymbolXdr`, and `MemoXdr.Text` now store the bytes of their text instead of a `String`. Valid UTF-8 reads as before. Two cases need action:

- `value` throws `CharacterCodingException` for bytes that are not valid UTF-8, also when it is read through `Scv.fromString` or `Scv.fromSymbol`. Catch it where such data arrives, or read `bytes`.
- `MemoXdr.Text` has no `copy` or `component1`. Use its constructor and `value`.

Code that builds these types from valid text and reads `value` needs no source change.

## Added

- `InvokeHostFunctionOperation.createStellarAssetContract` builds the operation that deploys the Stellar Asset Contract of an asset.
- `ContractClient.forContract` opens a Stellar Asset Contract with the spec shipped in the SDK. Use it for the functions `SACClient` has no typed call for, such as `approve` and `mint`.

## Fixed

- Reading a transaction from XDR now accepts values that applied transactions carry and 1.18.0 refused, such as time bounds up to 2^64-1 or a TTL extension of 0 ledgers (#171, #176, #177).
- Reading and writing XDR is now stricter. Binary XDR refuses values longer than their declared maximum, padding that is not zero (#168), and booleans or optional-value flags other than 0 or 1 (#197). Writing XDR-JSON refuses fixed-length values of the wrong length (#187) and values nested deeper than XDR-JSON reading accepts (#169). XDR strings that are not memo texts or Soroban strings and symbols, such as contract meta values and spec docs, must be valid UTF-8.
- Parsing contract code now throws `SorobanContractParserException` with the byte offset when it cannot decode an entry (#183), where 1.18.0 returned the entries before it without an error. Deploying with `loadSpec = true` fails before submission when the code the RPC returns has no readable spec.
- Creating a passkey on Android now also works in apps that resolve androidx.credentials 1.6.0 (#195).

## Security

- JVM and Android: Bouncy Castle bcprov-jdk18on 1.85.2 fixes five advisories listed in the CHANGELOG, in code that the SDK's own calls do not reach. Apps that resolve Bouncy Castle through the SDK get the fixed version.
- The API documentation build and the smart account demo use fixed versions of Jackson, jsoup, httpcore5, and httpclient5, which the published artifacts do not contain.

## Compatibility

- Kotlin 2.2+
- Maven: `com.soneso.stellar:stellar-sdk:1.19.0`
- Android: minimum SDK 24 (Android 7.0), compile SDK 34 or later
- Smart accounts on OpenZeppelin stellar-accounts 0.8.0 and 0.7
- Horizon v30.0.1 and stellar-rpc v30.0.1, the targets of the compatibility matrices

## Details

The [CHANGELOG](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.19.0/CHANGELOG.md) lists every change of this release.

Migrating to 1.19.0: [docs/migration/1.19.0.md](https://github.com/Soneso/kmp-stellar-sdk/blob/v1.19.0/docs/migration/1.19.0.md)
