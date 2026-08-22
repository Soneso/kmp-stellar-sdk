# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `ClaimableBalanceId` reads a claimable balance id in whichever spelling it is
  written: the `B...` strkey, the bare 32-byte hash as 64 hexadecimal characters,
  and that hash behind a type discriminant carried either in the single byte the
  strkey body leads with (66 characters) or in the four big-endian bytes the XDR
  union writes (72 characters), the shape Horizon reports. Hexadecimal is read in
  upper or lower case. It reports the balance as the canonical lower case hash
  (`hashHex`), as the 72-character form Horizon takes (`toPaddedHex()`), as the
  strkey (`toStrKey()`) and as the XDR union (`toXdr()`). Every byte of a
  discriminant is checked, so an id that names another type is rejected rather
  than having its high bytes dropped.
- `StrKey.encodeClaimableBalance` accepts the 36-byte XDR wire form alongside the
  32-byte hash and the 33-byte strkey body, so
  `Address.fromClaimableBalance(ByteArray)` takes it as well.
- `TransactionResponse.getCreatedClaimableBalanceId(operationIndex)` reports the
  id of the claimable balance the operation at that position created, as a `B...`
  strkey. It answers null when the transaction did not succeed, when the result
  XDR is absent or unreadable, when no operation sits at the index, or when the
  operation there creates no balance. Fee bump transactions are read through to
  the inner transaction's results.
- `SorobanServer.getExternalRefWasmHash(ref)` resolves a CAP-85 external
  reference (Protocol 28) to the 32-byte WASM hash it names: the reference's
  owner contract holds a persistent contract data entry keyed by the tag whose
  value is the hash, and the entry is read without invoking the owner.
  `loadContractCodeForContractId` and `loadContractInfoForContractId` apply the
  resolution when an instance carries an external reference executable, and
  `ContractClient.forContract` builds clients for such contracts, where all
  three previously answered null or "Contract spec not found". An external
  reference that cannot be resolved now throws rather than returning null; a
  null from the loaders still means the instance or its code was not found, or
  the contract is a Stellar Asset Contract, which has no WASM on-chain. A
  missing, wrongly typed or wrong-length tag entry raises
  `IllegalStateException` naming the owner and tag, which
  `ContractClient.forContract` passes through unwrapped; a reference
  whose owner is not a contract address raises `IllegalArgumentException`,
  which `forContract` wraps as `IllegalStateException("Failed to load contract
  spec for ...")` with the cause preserved. The tag bytes the reference
  carries reach the ledger key byte for byte, whether or not they are valid
  UTF-8, so any tag the owner wrote resolves; error messages render the tag
  through the SEP-0051 escape ladder, so a printable ASCII tag reads verbatim
  and any other byte appears as its escape. `ContractSpec.scValToNative`
  converts an `SCV_EXECUTABLE_TAG` value to a `String` when its tag bytes
  decode as UTF-8 and to the raw `ByteArray` otherwise; it previously failed
  the conversion.
- `ContractClient.deployFromExternalRef` deploys a contract instance from a
  CAP-85 external reference; the parameters name the owner contract and the tag
  instead of a wasm id, next to `deployFromWasmId`. The reference is resolved
  through `SorobanServer.getExternalRefWasmHash` before the transaction is
  built, so its exceptions apply unchanged: `IllegalArgumentException` for a
  non-contract owner before any request, `IllegalStateException` naming the
  owner and the tag for a missing, wrongly typed or wrong-length tag entry. The
  contract spec is loaded from the resolved WASM before submission and the
  returned client is ready to invoke, the same flow `deployFromWasmId` uses.
  Nothing is installed as part of the deployment, and the one-step
  `deploy(wasmBytes)` has no external reference counterpart, because there is
  nothing to upload. Without constructor arguments the operation carries the
  `CREATE_CONTRACT` arm, with them `CREATE_CONTRACT_V2`. The tag parameter is
  a `ByteArray` carried byte for byte or a `String` encoded as UTF-8, and one
  tag value feeds both the resolution and the built operation, so the entry
  that resolved is the entry the deployment names on-chain.
- `InvokeHostFunctionOperation.createContractFromExternalRef` builds the
  underlying create operation directly, next to `createContract`, with the same
  argument shape and internal `CREATE_CONTRACT`/`CREATE_CONTRACT_V2` branch.
  The tag parameter takes the raw bytes or a `String` encoded as UTF-8.
- `Address.deriveContractId(deployer, salt, network)` returns the contract id
  ("C...") a deployment by the given deployer with the given salt creates on
  the given network. The id derives from the deployer, the salt and the network
  only; the executable does not enter the derivation. A salt that is not
  exactly 32 bytes raises `IllegalArgumentException`.

### Changed
- **Breaking change**: CAP-71 upgraded authorization is the default.
  `Auth.authorizeInvocation` builds `ADDRESS_V2` credentials unless
  `authV2 = false` is passed, which builds the legacy `ADDRESS` arm.
  `useUpgradedAuth` defaults to true on `SimulateTransactionRequest`,
  `SorobanServer.simulateTransaction`, `SorobanServer.prepareTransaction`,
  `ClientOptions`, and the `AssembledTransaction` simulation path, and the key
  is sent on every simulate request with the current value, so an explicit
  false reaches the server as the legacy opt-out; the key was previously
  omitted when unset. RPC servers without Protocol 27 support ignore the flag
  and return legacy entries, which stay valid. Set false on a network below
  Protocol 27, where `ADDRESS_V2` entries invalidate the transaction.
  `useUpgradedAuth` is now `Boolean` rather than `Boolean?`, which changes the
  JVM binary signatures of `SorobanServer.simulateTransaction`,
  `SorobanServer.prepareTransaction`, and the `SimulateTransactionRequest`
  constructor and its generated `copy()`. Precompiled JVM consumers and
  callers passing `null` fail loudly and need updating.
- `SCValXdr.ExecutableTag` and `ContractExecutableExternalRefXdr.tag` carry the
  CAP-85 executable tag as `ByteArray` rather than as `SCStringXdr`. The XDR
  type is `SCString`, which admits arbitrary bytes, and the ledger matches the
  tag byte for byte; decoding into a Kotlin `String` replaced bytes that are
  not valid UTF-8 with U+FFFD, so such a tag re-encoded to different bytes and
  could neither round-trip nor resolve. The bytes now survive the binary and
  the XDR-JSON codecs exactly. Text-based access stays available:
  `ContractExecutableExternalRefXdr(owner, tag: String)` and
  `Scv.toExecutableTag(String)` encode the tag as UTF-8, and the
  `ContractExecutableExternalRefXdr.tagString` view and
  `Scv.fromExecutableTag` decode the bytes as UTF-8 strictly, throwing on a
  tag that is not valid UTF-8 rather than answering lossily.
  `Scv.toExecutableTagBytes` / `Scv.fromExecutableTagBytes` carry the bytes
  unchanged. `SCStringXdr` itself and the `SCV_STRING` / `SCV_SYMBOL` arms do
  not change. Code written against the `SCStringXdr` shape of the two tag
  positions needs the `ByteArray` shape, and `==` on these two positions
  compares the tag by array identity, as on the other array-backed XDR types.
- `ClaimClaimableBalanceOperation`, `ClawbackClaimableBalanceOperation` and
  `Sponsorship.ClaimableBalance` take a balance id in any spelling
  `ClaimableBalanceId` accepts, judge it when the operation is constructed, and
  put the same bytes on the wire for equivalent spellings. The first two required
  the 72-character form before. `Sponsorship.ClaimableBalance` validated nothing:
  a 72-character id became a 36-byte hash that the ledger key rejected only once
  the transaction was built. Reading an operation back from XDR reports the
  72-character form throughout. `Sponsorship.ClaimableBalance` reported the bare
  64-character hash before.
- `ClaimableBalancesRequestBuilder.claimableBalance`,
  `OperationsRequestBuilder.forClaimableBalance` and
  `TransactionsRequestBuilder.forClaimableBalance` take a balance id in any of
  those spellings and put the 72-character form Horizon serves in the route. An
  id naming no balance raises `IllegalArgumentException` before a request is sent
  rather than reaching Horizon as a 400. Streams build their URL from the same
  path segments, so they carry the same id.
- The Android unit test source set builds on the JVM one, so it resolves the JVM
  `actual` declarations the common tests need. It did not compile before. The
  variant runs on JUnit 5, as the JVM target does, and
  `-PexcludeIntegrationTests` reaches it too.

### Removed
- `EffectsRequestBuilder.forClaimableBalance`. Horizon serves no
  `/claimable_balances/{id}/effects` route, so every call this method made
  answered with a route-not-found error. The effects of a claimable balance are
  reachable through the operations that touched it.

### Fixed
- Strkey decoding requires canonical encoding. Input that at least one target
  previously accepted now raises `IllegalArgumentException` on all of them: trailing
  `=` padding, any content after a `=`, whitespace anywhere in the string
  including mid-string, lowercase base32, and characters outside the ASCII range.
  Padding also defeated the unused-trailing-bits check, so appending `===` to a
  strkey whose last character carried non-zero leftover bits made it decode; the
  padding drove the leftover bit count to zero and the check was skipped. A
  non-ASCII character was narrowed to its low byte before decoding, so a `G...`
  key with one character replaced by U+0141 decoded to the same key as the
  canonical string. Two different strings named one account. Decoding now also
  checks the encoded string length against the requested type before running the
  codec, so an oversized input is rejected without it running at all. Consumers
  that passed padded or whitespace-bearing strkeys, for example values pasted
  from a user interface, will now see rejections and should trim input before
  validating it.
- Every target reaches the same verdict on the same strkey. JVM and Android
  accepted whitespace and lowercase base32 where JavaScript and Native rejected
  the identical string. The exception type is uniform as well: `"========"` raised
  `ArrayIndexOutOfBoundsException` on JVM and Native but `IllegalArgumentException`
  on JavaScript, and `IndexOutOfBoundsException` no longer escapes any decode
  entry point on any platform.
- Signed payload strkeys (`P...`) are checked against the framing the XDR wire
  form defines: a declared payload length of 1 to 64, a data size that fits that
  length exactly, and zero padding after the payload. `StrKey.isValidSignedPayload`
  now reflects what `SignerKey` and the XDR decoders accept. The three SEP-0023
  signed payload vectors with malformed framing all decoded before: two
  round-tripped through `SignerKey` to a different strkey with the surplus bytes
  silently dropped, and the third raised `IndexOutOfBoundsException` from
  `SignerKey.fromEncodedSignerKey`, which its documentation says raises
  `IllegalArgumentException`. `SignerKey.fromEncodedSignerKey` names the rule a
  malformed signed payload broke rather than answering with the generic
  invalid-signer-key message.
- Claimable balance strkeys (`B...`) that carry a discriminant other than
  `CLAIMABLE_BALANCE_ID_TYPE_V0` are rejected by `StrKey.isValidClaimableBalance`,
  `StrKey.decodeClaimableBalance`, the `Address` constructor and XDR-JSON
  decoding. Such a strkey previously validated, decoded, produced an `Address`
  that reported `AddressType.CLAIMABLE_BALANCE` and re-encoded to the same
  string; only `toSCAddress()` refused it.
- `StrKey.encodeSignedPayload` and `StrKey.encodeClaimableBalance` run the same
  checks their decoders run, so neither emits a string the SDK will not read
  back. `encodeSignedPayload` validated a 40 to 100 byte count only and now
  validates the framing. `encodeClaimableBalance` requires a 33-byte input to
  carry a zero discriminant; the 32-byte hash-only form is unchanged and still
  prepends the discriminant itself. `Address.fromClaimableBalance(ByteArray)`
  therefore rejects a non-V0 33-byte value at construction rather than at
  `toSCAddress()`.
- Hexadecimal id and hash parsing holds input to the ASCII hex alphabet. The
  per-pair radix parse accepted a leading sign and non-ASCII Unicode digits, so
  a string of signed pairs such as `-1` repeated built a pool id, wasm hash,
  memo hash or contract spec bytes argument of different bytes than its
  characters spell, through the liquidity pool operations,
  `InvokeHostFunctionOperation.createContract`, `MemoHash`, `MemoReturn`, the
  contract spec and the OpenZeppelin credential storage, whose rejection is now
  `IllegalArgumentException` with a message where it was `NumberFormatException`.
  Contract spec hex arguments no longer have interior spaces removed; the `0x`
  prefix is still accepted there.
  `SorobanServer.loadContractCodeForWasmId` requires a 64-character wasm id
  before building the ledger key it queries.
- SEP-8 service construction fails on JVM and Android when a `stellar.toml`
  `CURRENCIES` entry names an issuer that is not a canonical strkey, for example
  one with whitespace inside the quotes, which the TOML parser preserves. It
  already failed that way on JavaScript and Native: `Sep08Service.fromDomain`
  builds a `RegulatedAsset` per regulated entry, and each one passes its issuer
  to `Asset.createNonNativeAsset`.
- Mint, burn and clawback asset balance changes on invoke host function
  operations deserialize. Horizon omits `from` on a mint and `to` on a burn or
  clawback, and the response model required both, so an operations page carrying
  one failed to parse as a whole. Both fields are now optional.
- SEP-12 callback signature verification fails closed when the configured signing
  key is not a canonical strkey. Its verifier catches `Throwable`, so a key that
  JVM and Android previously decoded now yields a `false` verification result
  rather than an error.
- SEP-10 and SEP-45 challenge validation propagates coroutine cancellation. A
  cancellation arriving while the server signature was being verified was
  reported as an invalid server signature; it now surfaces as the original
  `CancellationException`.

## [1.11.0] - 2026-08-10

### Added
- SEP-51 (XDR-JSON) support on the whole XDR type system. Every generated XDR
  type gains `toXdrJson(): String`, `toXdrJsonElement(): JsonElement`,
  `Companion.fromXdrJson(String)` and `Companion.fromXdrJsonElement(JsonElement)`,
  so any XDR value converts to the canonical JSON rendering SEP-0051 defines and
  back without loss. Output is compact and in XDR declaration order, so equal
  values produce byte-identical documents. Decoding raises
  `IllegalArgumentException` on malformed input, naming the type and the
  offending key. A struct accepts only the keys it declares: an unrecognised key
  is rejected rather than ignored, so a misspelled field fails instead of quietly
  discarding its value. `$schema` is the one property accepted without being
  declared. An object must also name each key once. `fromXdrJson` rejects a
  repeated key rather than resolving it to one occurrence and discarding the
  other; the rule covers one object at a time, so separate objects may share a
  key name. The methods are emitted by the XDR generator, so they track the XDR
  pin; see `docs/sep/sep-51.md` for the mapping rules, the documented
  limitations and the input-strictness choices.
- `kotlinx-serialization-json` moved from `implementation` to `api` in
  `stellar-sdk/build.gradle.kts`. `JsonElement` appears in the public signature
  of `toXdrJsonElement` and `fromXdrJsonElement`, so the library is now part of
  the SDK's public ABI and is on the consumer compile classpath. It was already a
  transitive runtime dependency through Ktor, so this changes the compile
  classpath only.
- Updated XDR definitions to stellar-xdr `911c935` (CAP-0083 / CAP-0085): new
  `ContractExecutableXdr.ExternalRef` arm with `ContractExecutableExternalRefXdr`
  (`executableOwner`, `tag`), new `SCValXdr.ExecutableTag` arm carrying an
  `SCString` (`SCV_EXECUTABLE_TAG`), and new `StellarValueExtXdr.ProposedValue`
  arm with `StellarValueProposedValueXdr` (`STELLAR_VALUE_EMPTY_TX_SET`).
  `Scv` gains `toExecutableTag` / `fromExecutableTag`, and smart-account ScMap
  key ordering compares `SCV_EXECUTABLE_TAG` values by content like `String`
  and `Symbol`. The new arms extend sealed classes, so a `when` over
  `SCValXdr`, `ContractExecutableXdr`, or `StellarValueExtXdr` without an
  `else` branch needs branches for them.

### Fixed
- SEP-45 and SEP-10 token submissions are never auto-retried. The default HTTP
  client retries on server errors, which resubmitted the one-time challenge:
  SEP-45 servers consume the challenge nonce on the first attempt, so the
  retry could never succeed and replaced the original error with a misleading
  "invalid nonce" rejection. Both token POSTs now disable retries at the
  request level (also effective on caller-supplied clients that install
  `HttpRequestRetry`); retrying requires a fresh challenge round via
  `jwtToken()`. Challenge requests keep their retries.
- `ContractClient.deploy` and `deployFromWasmId` load the returned client's
  spec from the uploaded code before deploying instead of reading back the
  instance entry the deployment just wrote. On a busy RPC, whose ledger-entry
  ingestion runs behind transaction status, that read could miss and a
  successful deployment surfaced as "Contract spec not found". Code without
  spec entries keeps the current error semantics through the forContract
  fallback. Constructor arguments that cannot be converted — the spec is
  unreadable or declares no `__constructor` — now raise before anything
  deploys; the arguments were previously discarded and the contract deployed
  without running its constructor.
- `ContractClient.deploy`, `install`, and `deployFromWasmId` raise
  `SendTransactionFailedException` when the network refuses a transaction at
  submission, carrying the status, the error result XDR with its parsed form,
  and any diagnostic events. A refused submission previously went undetected
  and the polling window reported the misleading status NOT_FOUND after three
  minutes. A DUPLICATE response is polled like a pending one, since it names a
  transaction the network already knows, so a resubmitted deployment that
  already succeeded reports that success. The exception's
  `assembledTransaction` property is now nullable, since these entry points
  submit without one.
- Contract-deployment salts now come from the platform CSPRNG
  (`secureRandomBytes`) instead of `kotlin.random.Random`. This covers
  `InvokeHostFunctionOperation.createContract` and the `ContractClient.deploy`
  and `ContractClient.deployFromWasmId` entry points, whose `salt` parameters
  defaulted to a non-cryptographic generator. The salt and the deployer address
  determine the deployed contract ID, so the address a deployment would claim
  was predictable.
- `Price.fromString` now reports values it cannot approximate. A value whose
  integer part exceeds `Int.MAX_VALUE` produced no fraction at all and surfaced
  as "Price denominator cannot be zero" from the constructor; it now throws
  `IllegalArgumentException` naming the value and the 32-bit limit.
- `Transaction.isSorobanTransaction` classifies by operation type only, matching
  its documented behavior and the Java, Python and JavaScript SDKs. A single
  classic operation with Soroban transaction data attached was reported as a
  Soroban transaction, so it passed the `assembleTransaction` guard whose own
  error message requires one `InvokeHostFunction`, `ExtendFootprintTTL` or
  `RestoreFootprint` operation, and the result paired a classic operation with
  Soroban resource data.
- The SEP-29 memo-required check now runs on transaction submission.
  `HorizonServer.submitTransaction` and `submitTransactionAsync` decode
  envelopes with the SDK's XDR types; the previous parser misread real
  envelopes and the check never fired. Muxed destinations remain exempt and
  fee-bump envelopes are inspected through their inner transaction. With the
  check active, submitting a memo-less transaction performs one Horizon
  account lookup per distinct destination unless `skipMemoRequiredCheck` is
  set.
- XDR decoding now rejects truncated and malformed input uniformly on every
  target. `XdrReader` performed no bounds checking on JavaScript and Native;
  on JavaScript, reads past the end of the buffer yielded undefined values, so
  malformed XDR (RPC responses, transaction envelopes, SEP-10 challenges,
  Soroban authorization entries) decoded into a bogus object instead of
  raising an error. The JVM reader failed, but with target-specific
  exceptions: `EOFException` on a truncated buffer, `NegativeArraySizeException`
  on a negative length prefix and `OutOfMemoryError` on a hostile one, because
  the length was used to allocate before being validated. All three targets now
  validate the remaining byte count before allocating and throw
  `IllegalArgumentException`, and reject negative length prefixes. On
  JavaScript this is a behavior change: code that previously received a
  silently decoded object from malformed XDR now sees an exception. On the JVM
  the exception type for malformed XDR changes.
- `RequestBuilder.buildUrl()` is idempotent. Repeated calls previously
  appended the path segments again, so every Horizon SSE reconnect targeted a
  duplicated path (`/transactions` became `/transactions/transactions`) and
  streams never recovered from a disconnect.
- `OZMultiSignerManager.multiSignerTransfer` validates the token contract
  address and the signer list before resolving token decimals, so invalid
  input fails fast instead of after a network round-trip.
- Soroban RPC responses that cannot be deserialized now raise
  `IllegalArgumentException` with the underlying cause, as documented. Ktor
  reports these failures as `ContentConvertException`, which was not being
  caught.
- SEP-10 reports a client-domain `stellar.toml` that lacks a `SIGNING_KEY` as
  such, instead of re-wrapping it as a stellar.toml load failure. The
  exception type is unchanged.
- SEP-10 `validateChallenge` verifies that the challenge transaction's source
  account is the server account, as SEP-10 requires. The check was missing, so
  a challenge sourced by any other account passed validation. A mismatch now
  raises the new `InvalidTransactionSourceAccountException`. A muxed source is
  rejected by the same comparison, since the server account is an ed25519
  account id.
- SEP-6 `patchTransaction` matches the specification. It targets
  `PATCH TRANSFER_SERVER/transactions/:id` rather than the singular
  `/transaction/:id`, and sends the updated fields nested under a
  `transaction` key rather than at the top level. A spec-conforming anchor
  does not serve the previous request shape.
- `AccountDataResponse.decodedString` raises `CharacterCodingException` on a
  data entry that is not valid UTF-8, and `decodedStringOrNull` returns null
  for one, as both were documented to do. Decoding accepted invalid sequences
  and substituted the Unicode replacement character, so binary data surfaced
  as mojibake instead of a detectable failure.
- The OpenZeppelin smart account kit reports a transaction as signed only when
  it produced a signature. Auth entries that are passed through unchanged
  (source-account credentials, or entries belonging to another contract) no
  longer cause the `TransactionSigned` event to name a credential, and no
  longer bump that credential's last-used timestamp.
- `OZRelayerClient` tolerates relayer responses whose fields carry an
  unexpected JSON shape. A non-primitive `success`, `error`, `hash`,
  `transactionId` or `status` raised `IllegalArgumentException` out of the
  response parser; such fields are now treated as absent and the call returns
  a failed `RelayerResponse` like every other malformed-response case.

## [1.10.0] - 2026-07-20

### Added
- **Constructor-time policies for smart accounts**: policies can now be
  installed on the default context rule at wallet creation.
  `OZSmartAccountConfig` gains `defaultPolicies`, and `createWallet` and
  `deployPendingCredential` gain an optional `policies` parameter that
  overrides the config default. The policy map is validated before the passkey
  ceremony starts, so an invalid configuration fails without leaving an
  orphaned credential. Constructor arguments are not part of the contract
  address preimage, so the derived wallet address is unchanged by the
  policies. Contract constraints are documented: constructor policies land on
  the default rule, so a spending-limit policy — which installs only on
  call-contract rules — cannot be installed at deploy time; a threshold must
  not exceed the signer count; and threshold 1 keeps a rule at 1-of-N as
  signers are added.
- **Contract error-code catalog**: `ContractErrorCodes` gains the full set of
  named constants for the smart-account contract's own error enum
  (`SmartAccountError`, codes 3000-3016; previously only five were exposed).
  `decode` resolves any known raw code — smart account, WebAuthn verifier,
  simple threshold, weighted threshold, or spending limit — into the new
  `OZContractError` (defining contract enum plus variant name), and
  `decodeFromMessage` resolves the first known error code inside a transaction
  failure message.
- **Client-side contract limits**: context-rule names (20 UTF-8 bytes) and
  external-signer key data (256 bytes) are validated before submission, and
  the per-rule policy limit (5) is enforced for constructor policies as well,
  so violations fail fast instead of on-chain. The limits are exposed as
  `OZConstants.MAX_NAME_SIZE`, `MAX_EXTERNAL_KEY_SIZE`, and `MAX_POLICIES`.

### Changed
- The default smart-account indexer endpoints for testnet and mainnet now
  point at the Mercury smart-account indexer (previously the SDF ecosystem
  workers endpoints). Consumers that set a custom indexer URL are unaffected.
- Smart-account indexer requests no longer send client-identification headers.
  Custom headers force a CORS preflight in browsers, and indexer providers
  only allowlist standard headers, which blocked every request from the web
  target.
- Coroutine cancellation now propagates out of SDK network calls instead of
  being swallowed or converted into error results.
- The new optional parameters change the JVM binary signatures of
  `createWallet`, `deployPendingCredential`, and the `OZSmartAccountConfig`
  constructor and its generated `copy()`.
- `OZPolicyManager.sortMapByKeyXdr` returns entries in the host's ScMap key
  order instead of XDR-byte order; consumer-built install-param maps sorted
  with it pick up the corrected ordering automatically.
- Updated XDR definitions to stellar-xdr `df0c200` (declaration reordering
  only; generated types unchanged apart from doc comments).
- Migrated the demo web apps to Vite 8 (Rolldown bundler) and removed the
  unused terser dependency.

### Fixed
- **Smart-account map-key ordering**: map keys in auth payloads and policy
  install parameters are now sorted in the Soroban host's content-wise key
  order. The previous length-major sort over XDR-encoded bytes produced
  orderings the host rejects, which failed authentication and constructor
  materialization for affected key sets.
- Smart-account, RPC, and FriendBot network boundaries no longer leak
  Kotlin/JS connectivity errors: relayer calls broke their documented no-throw
  contract, indexer and RPC failures escaped unwrapped, and a transient glitch
  aborted transaction polling. RPC connectivity errors now surface as
  `ConnectionErrorException`, indexer errors as `IndexerException`, relayer
  errors in the returned `RelayerResponse`, FriendBot funding failures as the
  documented `Exception`, and polling retries them.
- `SorobanServer.pollTransaction` now throws the last polling failure when
  every attempt fails; previously it crashed with a null-pointer error. A
  received response is still returned even if later attempts fail.
- Smart-account demo: the approval-inbox bell is disabled while the
  coordination server is unreachable and recovers automatically, an outage no
  longer freezes the app, and rapid repeated taps no longer corrupt the
  navigation stack. Editing a context rule offers signer selection based on
  the wallet's signer set, since a rule edit is authorized by the default rule
  rather than the edited rule's signers. During submission the add-signer and
  add-policy forms stay visible but disabled, and newly staged entries,
  validation errors, and results scroll into view.
- Horizon and SEP network boundaries no longer leak Kotlin/JS connectivity
  errors. On Kotlin/JS the HTTP engine reports a failed connection as a
  `kotlin.Error` ("Fail to fetch"), which is a `Throwable` but not an
  `Exception`, so it escaped the `catch (Exception)` blocks in the Horizon
  request builders, `HorizonServer` submit/POST paths, `Page.getNextPage`, the
  SSE stream loop, the SEP-10 (`WebAuth`) and SEP-45 (`WebAuthForContracts`)
  challenge and token calls, and `Sep31Service.fromDomain`. These boundaries now
  catch `Throwable` and surface the connectivity failure as the documented
  exception type (`ConnectionErrorException`, `ChallengeRequestException`,
  `TokenSubmissionException`, `Sep45ChallengeRequestException`,
  `Sep45TokenSubmissionException`, `Sep31ConfigurationException`, ...) on every
  platform. Coroutine cancellation and platform-fatal errors now propagate
  instead of being wrapped or swallowed. Behavior for `Exception`-typed failures
  on JVM and native is unchanged.

### Security
- XDR generator: xdrgen is consumed from the Soneso fork so concurrent-ruby
  resolves to >= 1.3.7 (GHSA-h8w8-99g7-qmvj and two further advisories);
  temporary until stellar/xdrgen#231 is merged.
- Demo web apps: updated vite past GHSA-fx2h-pf6j-xcff and
  GHSA-v6wh-96g9-6wx3; esbuild is no longer in the dependency tree
  (GHSA-g7r4-m6w7-qqqr). Development-only dependencies.

## [1.9.0] - 2026-07-14

### Added
- `GetHealthResponse` gains `latestLedgerCloseTime` and `oldestLedgerCloseTime` (`Long?`),
  the unix timestamps (seconds) at which the latest and oldest ledgers closed, returned by
  stellar-rpc v27.1.0+. On older servers the fields are `null`.
- **Spec-free contract invocation** (preparation for the upcoming community bindings
  generator, which is not yet released): `ContractClient` gains positional
  `invoke(functionName, parameters: List<SCValXdr>, ...)` and
  `buildInvoke(functionName, parameters: List<SCValXdr>, ...)` overloads that take
  pre-encoded XDR arguments and require no loaded `ContractSpec`. They replicate the full
  behavior of the Map-based overloads (build, simulate, read/write auto-detection,
  signer-required-for-write check, auto submit) minus the spec-driven argument conversion
  and method-name validation (an unknown function fails at simulation time instead of
  before the request). These are the entry points for generated contract clients, which
  encode and decode all values themselves.
- `ContractClient.forContractWithoutSpec(contractId, rpcUrl, network)`: a non-suspend
  factory that constructs a client without the network spec-load round-trip. The Map-based
  overloads and spec-backed helpers remain unavailable on such a client (they throw
  `IllegalStateException`); use the positional overloads instead.
- **Contract-binding fixtures and tests**: generated binding clients for the hello,
  auth, atomic-swap, and token demo contracts are checked in and exercised alongside their
  Map-based variants in the SorobanClient integration test, plus two purpose-built fixtures
  with unit and testnet integration tests — `BindingsSpecTestContract` (generated from the
  generator repository's reference contract, covering the entire contract-spec type surface)
  and `OptionShapesContract` (option values in nested positions and a Kotlin soft-keyword
  parameter name).
- `useUpgradedAuth` flag on `SorobanServer.simulateTransaction(...)`,
  `SorobanServer.prepareTransaction(...)`, `SimulateTransactionRequest`, and
  `ClientOptions`. When set, a supporting Protocol 27+ RPC returns `AddressV2`
  auth entries in recording modes; RPCs without support ignore the flag and
  return legacy entries. The flag is optional and defaults to omitted/absent, so
  the request wire shape is unchanged when it is not set.

### Changed
- The `com.ionspin.kotlin:bignum` dependency is now exposed via `api` (was
  `implementation`) in `commonMain`. The SDK's public surface and generated bindings return
  `com.ionspin.kotlin.bignum.integer.BigInteger`, so it must be visible transitively to
  consumers.
- The new optional parameters change the JVM binary signatures of
  `SorobanServer.simulateTransaction`, `SorobanServer.prepareTransaction`, and the
  `SimulateTransactionRequest` and `ClientOptions` constructors. Precompiled JVM
  consumers and positional calls that pass arguments after the new parameter fail
  loudly and need updating.
- Compatibility matrices regenerated; the Soroban RPC baseline moved to v27.1.1.
- Bumped pinned GitHub Actions via Dependabot: `actions/checkout` to v7.0.0,
  `actions/setup-java` to v5.4.0, and `codecov/codecov-action` to v7.0.0.

## [1.8.1] - 2026-06-29

### Added
- **Headless smart-account connect**: `connectToContract(contractId)` on the
  OpenZeppelin smart-account kit connects to a deployed account by contract
  address with no passkey. The connection is usable through the multi-signer /
  external-signer pipeline; single-passkey paths reject a headless connection.
  Adds the public `isHeadless` discriminator, the
  `SmartAccountEvent.HeadlessConnected` event, and
  `WalletException.HeadlessConnection` (error code 2004).
- Soroban-RPC-visibility polling for wallet auto-funding, replacing fixed delays.
- An agent-signer flow demo in the smart-account demo: delegate-to-agent plus an
  approval inbox, backed by a Ktor coordination server and a Kotlin reference
  agent.

### Changed
- The published `-javadoc.jar` is now empty (the Dokka HTML still deploys to
  GitHub Pages) to keep the Maven Central artifact size under the free threshold.

### Fixed
- The agent-skill API-reference generator now handles Kotlin nested block
  comments, so members declared after a KDoc containing an inner block comment
  are no longer dropped.

## [1.8.0] - 2026-06-19

### Added
- **Protocol 27 Soroban authorization arms (CAP-71)**: Soroban authorization
  credentials now have three address arms in `SorobanCredentialsXdr`: the legacy
  `Address` (`SOROBAN_CREDENTIALS_ADDRESS`), the address-bound `AddressV2`
  (`SOROBAN_CREDENTIALS_ADDRESS_V2`), and `AddressWithDelegates`
  (`SOROBAN_CREDENTIALS_ADDRESS_WITH_DELEGATES`), a recursive sorted
  delegate-signature tree. New XDR types: `SorobanAddressCredentialsWithDelegatesXdr`,
  `SorobanDelegateSignatureXdr`, and the `ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS`
  preimage `HashIDPreimageSorobanAuthorizationWithAddressXdr`. The V2 and
  WITH_DELEGATES arms are valid only on Protocol 27+ networks; legacy `Address`
  remains the default and is fully valid everywhere.
- `Auth.AuthOptions` with `forAddress`: passing `AuthOptions(forAddress = ...)`
  to `Auth.authorizeEntry(...)` routes the signature to every node in the
  credential tree (top-level address or any delegate, at any depth) whose
  address matches. The default (`null`) signs the top-level address.
- `Auth.attachDelegates(entry, validUntilLedgerSeq, delegates)` builds an
  `AddressWithDelegates` entry from an `Address` or `AddressV2` entry, sorting
  and validating the delegate tree. `DelegateDescriptor` describes a delegate
  node (address, optional signature, nested delegates).
- `Auth.authorizeInvocation(...)` gained an `authV2: Boolean = false` flag that
  emits `AddressV2` credentials instead of the legacy `Address` arm.

### Changed
- `AssembledTransaction.needsNonInvokerSigningBy()` and `signAuthEntries()` are
  arm-aware: they walk the delegate tree depth-first and report or sign matching
  delegate nodes as well as the top-level address. A delegates-only entry whose
  top-level signature stays void but whose delegate nodes are all signed is
  treated as fully signed.
- SEP-45 (`WebAuthForContracts`) verifies and signs challenge entries across all
  three credential arms, selecting the matching hash preimage automatically. No
  API change for callers.
- Smart-account and OpenZeppelin signing paths select the correct preimage per
  credential arm. No API change for callers.
- The new optional parameters change the JVM binary signatures of
  `Auth.authorizeEntry` and `Auth.authorizeInvocation`: callers using named
  arguments are source-compatible but need a recompile; precompiled JVM consumers
  and positional calls that pass arguments after the new parameter fail loudly
  and need updating.
- `SorobanCredentialsXdr` and `HashIDPreimageXdr` gain new sealed arms, and
  `SorobanCredentialsTypeXdr` and `EnvelopeTypeXdr` gain new entries. Code with
  an exhaustive `when` over any of these public types must add branches for the
  new Protocol 27 arms (or an `else`) to compile.
- Compatibility matrices regenerated against Horizon v27.0.0 and Soroban RPC
  v27.0.0. The RPC `simulateTransaction` `useUpgradedAuth` parameter is
  intentionally not yet implemented; server-side support has not shipped.

### Fixed
- `AccountResponse.lastModifiedTime` is now nullable, fixing Horizon account
  parsing when the field is absent (#39).

## [1.7.1] - 2026-06-13

### Added
- **Configurable token decimals for smart accounts**: `transfer`,
  `multiSignerTransfer`, and spending limit amounts accept a `decimals`
  parameter. Transfers fetch the token's on-chain `decimals()` when not
  specified (`fetchTokenDecimals` is public); spending limits default to 7.
  `amountToBaseUnits` is available for manual conversions.
- `OZExternalSignerManager` query surface: `get`, `hasSigners`, and
  `hasWalletAdapter`.
- `OZSmartAccountKit.getDeployer()` is now public;
  `SmartAccountBuilders.getPublicKeyFromSigner` was added.
- Typed context type builders (`createDefaultContextType`,
  `createCallContractContextType`, `createCreateContractContextType`) and
  typed policy install params (`PolicyInstallParams` with public `toScVal()`)
  with a matching `addPolicy` overload.

### Changed
- Amount validation is stricter: amounts with more fractional digits than the
  token's decimals are rejected instead of silently rounded, and invalid
  amounts throw `ValidationException.InvalidAmount` instead of
  `IllegalArgumentException`.
- The new optional `decimals` parameter changes the JVM binary signatures of
  `transfer`, `multiSignerTransfer`, and `addSpendingLimit`: callers using
  named arguments are source-compatible but need a recompile; precompiled JVM
  consumers and positional calls that pass arguments after the new parameter
  fail loudly and need updating.
- Smart account docs and skill references corrected: platform setup
  instructions (Android Digital Asset Links, macOS libsodium linking, iOS
  package source), configuration snippets, stale conceptual descriptions, and
  throws documentation; trimmed redundant content.
- Smart account demo: the macOS app is aligned with the Compose UI (in-place
  policy editing, cross-rule signer reuse, validation parity, shared view
  components).

### Deprecated
- `SmartAccountBuilders.describeSignerType`; map signer types to display
  labels in your app.

### Removed
- The non-functional external wallet connection persistence:
  `WalletConnectionStorage`, `ExternalWalletAdapter.reconnect`,
  `OZExternalSignerManager.addFromWallet` / `restoreConnections`, and the
  `walletConnectionStorage` constructor parameter. The kit never wired
  connection storage, so the reconnect path was unreachable.
- The orphan policy param builders from `SmartAccountBuilders`
  (`createThresholdParams`, `createWeightedThresholdParams`,
  `createSpendingLimitParams`); use `PolicyInstallParams` instead.

### Fixed
- `OZSmartAccountKit.close()` now clears in-memory external signer secrets,
  closes the relayer HTTP client, and removes event listeners.
- `InMemoryStorageAdapter.clear()` also clears the stored session.
- Smart account demo, all platforms: weighted-threshold weight prefill for
  account signers, pending-list deployments provision XLM and demo tokens,
  pending credential delete failures are surfaced, allowance fetch failures
  are logged. Context rule edits submit removals before additions, fixing
  contract errors when replacing signers or policies in one edit.

## [1.6.1] - 2026-05-29

### Added
- **Ed25519 external signers for OZ smart accounts**: `SelectedSigner.Ed25519`
  adds a third multi-signer kind alongside passkey and wallet (G-address)
  signers. An Ed25519 signer can sign two ways: an in-memory raw seed
  registered at runtime via `kit.externalSigners.addEd25519FromRawKey(...)`,
  or a pluggable `OZExternalEd25519SignerAdapter` (hardware wallet, HSM, or
  remote signing service) supplied at kit construction via
  `config.externalEd25519Adapter`. Usable wherever `selectedSigners` is
  accepted — `multiSignerTransfer`, `multiSignerContractCall`,
  `multiSignerExecuteAndSubmit`, and context-rule operations
  (`addContextRule` / `removeContextRule` / `updateName` / `updateValidUntil`).
- `OZSmartAccountKit.externalSigners` — the kit-owned `OZExternalSignerManager`
  fronting all external (non-passkey) signers. Exposes `addFromSecret` /
  `addEd25519FromRawKey` to register in-memory keys at runtime and `canSignFor` /
  `canSignEd25519For` to check signing capability before a multi-signer ceremony.
- `OZSmartAccountConfig.externalEd25519Adapter` — the Ed25519 adapter input,
  the symmetric sibling of `externalWallet`.

### Changed
- External (non-passkey) signing is unified behind the kit-owned
  `OZExternalSignerManager` (`kit.externalSigners`): the multi-signer pipeline
  resolves and signs both wallet (G-address) and Ed25519 signers through it.
  Each kind offers two custody models — a config-injected adapter
  (`config.externalWallet` / `config.externalEd25519Adapter`) or an in-memory
  key registered at runtime. Wallet signing behaviour is unchanged and
  `config.externalWallet` continues to work exactly as before; this release is
  additive and backwards compatible with 1.6.0.

## [1.6.0] - 2026-05-20

### Added
- **SEP-31 (Cross-Border Payments)**: Sending Anchor side. `Sep31Service`
  exposes service discovery via `stellar.toml`, payment initiation,
  lifecycle tracking, and signed status callback registration. Wraps the
  five HTTP endpoints (`GET /info`, `POST /transactions`,
  `GET /transactions/:id`, `PUT /transactions/:id/callback`,
  `PATCH /transactions/:id`) with typed request/response classes
  (`Sep31InfoResponse`, `Sep31ReceiveAssetInfo`, `Sep31Sep12TypesInfo`,
  `Sep31PostTransactionsRequest`, `Sep31PostTransactionsResponse`,
  `Sep31TransactionResponse`, `Sep31TransactionStatus`, `Sep31FeeDetails`,
  `Sep31FeeDetailsDetails`, `Sep31Refunds`, `Sep31RefundPayment`) and a
  typed exception hierarchy rooted at `Sep31Exception`. Integrates with
  SEP-10 (JWT auth), SEP-12 (KYC), and optional SEP-38 (firm quotes).
  User-facing guide at `docs/sep/sep-31.md`; agent skill reference at
  `skills/kmp-stellar-sdk/references/sep-31.md`.
- `com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier` — shared
  callback signature verifier covering SEP-12 and SEP-31. Construct one
  instance per registered callback URL. Returns a sealed `Result`
  (`Valid` / `Stale(ageSeconds)` / `SignatureMismatch` / `MalformedHeader`
  / `MissingHeader`) so callers can distinguish replay from forgery in
  logs. Enforces HTTPS (loopback-only HTTP exception), pins host from the
  registered URL with port stripped, and applies a two-sided freshness
  check (defends against future-dated forgery as well as replay).
- SEP-31 documentation references the shared verifier directly instead of
  inline verification snippets.

### Changed
- **SEP-1 `StellarToml.fromDomain`**: the HTTPS-only invariant is relaxed
  to allow HTTP for loopback authorities (`localhost`, `127.0.0.1`, `[::1]`).
  All other hosts still require HTTPS. This enables local-development
  workflows against an Anchor Platform instance without a TLS cert.
- **SEP-10 integration test**: migrated the client-domain signer used by
  the integration test from `server-signer.replit.app` to
  `testsigner.stellargate.com` (source: `Soneso/go-server-signer`). No
  production code changes; the two local-signing variants
  (`testClientDomainAuthentication`, `testLocalClientDomainSigningDelegate`)
  were dropped in favour of the remote-delegate test that already covers
  the full client-domain path.

### Deprecated
- `com.soneso.stellar.sdk.sep.sep12.CallbackSignatureVerifier` is
  deprecated. Use `com.soneso.stellar.sdk.sep.common.CallbackSignatureVerifier`
  instead. The shim is functionally equivalent (bit-for-bit observable
  behaviour preserved via internal compatibility flags) and is scheduled
  for removal in version 1.8.0, or no earlier than 90 days after the
  1.6.0 release date, whichever is later.

## [1.5.1] - 2026-04-28

### Changed
- **Smart-account connect: cascade order improved** (#24): The connection cascade now runs storage → derivation → indexer (was storage → indexer → derivation). When a passkey is registered as a signer on multiple smart accounts, multi-result indexer responses surface as the new `ConnectWalletResult.Ambiguous(candidates)` arm so the caller can let the user pick. Previously the indexer's first result (lex-first by contract address) was picked, with no signal that the choice was ambiguous.
- **Breaking change** (#24): `ConnectWalletResult` is now a sealed type with `Connected` and `Ambiguous` arms. Direct field access (`result.contractId`) becomes a `when` switch.
- **Breaking change** (#23): XDR constructor signatures changed for the five types affected by the discriminant fix — `TransactionResultResultXdr.Results`, `InnerResultPair`, `BucketEntryXdr.LiveEntry`, `ManageOfferSuccessResultOfferXdr.Offer`, `SCErrorXdr.Code` now take `discriminant` as the first constructor argument. High-level helper users (Horizon, RPC, `AssembledTransaction`, `ContractClient`) are unaffected.

### Fixed
- **XDR generator multi-case discriminant collapse** (#23): XDR unions where multiple discriminant cases shared one non-void payload were generated with the discriminant hardcoded to the first case. As a result `txFAILED` round-tripped as `txSUCCESS`, and all `SCError` types collapsed to `SCE_WASM_VM` (though the error code was correct). High-level helpers (Horizon `successful`, RPC `status`, `AssembledTransaction`, `ContractClient`) flow through JSON and were not affected. The bug surfaced for code that decoded `TransactionResultResultXdr.discriminant` directly or read Soroban contract errors via `Scv.fromError()` / `ContractSpec.scValToNative()`.
- **Smart-account connect: FAILED-status credentials** (#24): A credential whose deployment previously failed silently connected to a non-existent contract. It now throws with a message pointing the user at `deployPendingCredential()` for retry.
- **Smart-account connect: transport-error masking** (#24): RPC and indexer transport errors propagate as their original types instead of being laundered as "contract not found." Callers can now distinguish "contract is not on-chain" from "the lookup itself failed."
- **Smart-account indexer JSON parsing** (#24): `OZIndexerClient` data classes (`CredentialLookupResponse`, `AddressLookupResponse`, `ContractDetailsResponse`) expected snake_case top-level fields, but the hosted indexer returns camelCase top-level keys (with snake_case for inner fields). Removed the incorrect `@SerialName` annotations on top-level fields. The previous cascade ordering swallowed the deserialization failure silently; the new ordering surfaced it.
- **Test stability**: `testStoredCredential_equality` no longer flakes on slow JVM runners. Pinned `createdAt` explicitly in the test (the default `currentTimeMillis()` could produce different values for back-to-back constructions).

## [1.5.0] - 2026-04-14

### Changed
- **Smart Accounts: Cross-device passkey authentication**: Replace `allowCredentialIds: List<ByteArray>?` with `allowCredentials: List<AllowCredential>?` in `WebAuthnProvider.authenticate()`. The new `AllowCredential` data class pairs credential IDs with transport hints (e.g., "internal", "hybrid"), enabling browsers and OS credential managers to offer QR code scanning for cross-device authentication.
- **Breaking change**: `WebAuthnProvider.authenticate()` parameter renamed and retyped. Use `AllowCredential.fromIds()` to migrate existing `List<ByteArray>` values.

### Added
- `AllowCredential` data class with `id`, `transports`, and `fromId()`/`fromIds()` factory methods
- `SelectedSigner.Passkey.transports` field for multi-signer cross-device flows
- Transport hints propagated through all SDK call sites (`OZWalletOperations`, `OZTransactionOperations`, `OZMultiSignerManager`)
- 22 unit tests for `AllowCredential`

### Fixed
- JS Node CI: Exclude `org.nodejs` and `com.yarnpkg` groups from JetBrains Compose Maven repository to prevent build failures when the repository returns 503
- iOS demo app: Pre-build script conditionally builds for device or simulator based on target platform

## [1.4.0] - 2026-04-08

### Added
- **OpenZeppelin Smart Accounts**: SDK support for the [OpenZeppelin smart account contracts](https://github.com/OpenZeppelin/stellar-contracts) on Soroban
  - `OZSmartAccountKit` as the main entry point for wallet creation, connection, and management
  - WebAuthn passkey authentication across all platforms (Android, iOS, macOS, web)
  - Multi-signer support with delegated and external signers
  - Context rule management (Default, CallContract, CreateContract)
  - Policy management (simple threshold, weighted threshold, spending limit)
  - Token transfers and generic contract calls with single and multi-signer authorization
  - Relayer integration for fee-sponsored transactions
  - Indexer integration for credential-to-contract address lookup (testnet and mainnet defaults)
  - Platform-specific storage adapters (EncryptedSharedPreferences, Keychain, UserDefaults, IndexedDB, LocalStorage)
  - Platform-specific WebAuthn providers (CredentialManager, ASAuthorization, navigator.credentials)
  - Documentation in `docs/smart-accounts/`

- **Smart Account Demo App**: Compose Multiplatform demo with native macOS SwiftUI support
  - Wallet creation and connection with passkey registration
  - Token transfers (XLM and custom tokens) with single and multi-signer flows
  - Context rule creation, editing, and removal
  - Policy configuration (threshold, weighted threshold, spending limit)
  - Token approval (SEP-41 allowance)
  - Runs on Android, iOS, macOS, desktop (JVM), and web

## [1.3.1] - 2026-04-03

### Fixed
- Allow lowercase letters (a-z) in asset codes, matching JS and Python Stellar SDK validation (fixes #14)

## [1.3.0] - 2026-02-14

### Added
- **SEP-2 (Federation Protocol)**: Resolve human-readable Stellar addresses (e.g., `bob*stellar.org`) to account IDs and reverse-lookup accounts to addresses
  - `FederationService` with `fromDomain()` factory and 4 query methods: `resolveStellarAddress()`, `resolveAccountId()`, `resolveTransactionId()`, `resolveForward()`
  - `FederationResponse` data class with account ID, memo, memo type, and stellar address
  - 4 typed exceptions for invalid addresses, missing federation servers, and malformed responses
  - Unit tests with MockEngine and integration test against live federation server
  - Documentation in `docs/sep/sep-02.md`

- **SEP-30 (Account Recovery)**: Multi-party account recovery using alternative authentication methods (email, phone, Stellar address)
  - `Sep30Service` with all 6 spec endpoints: register, update identities, sign transaction, account details, delete, list accounts
  - 9 data model classes with JSON serialization/deserialization
  - 7 typed exceptions mapping HTTP status codes (400, 401, 404, 409, unknown, malformed 200)
  - 83 unit tests covering response parsing, exceptions, and service operations
  - Documentation in `docs/sep/sep-30.md`

- **SEP-53 (Sign and Verify Messages)**: Off-chain message signing and verification using Ed25519 keypairs
  - `signMessage()` and `verifySignedMessage()` methods on `KeyPair`
  - Domain-separated signing using SEP-53 payload format (36-byte prefix + SHA-256 hash)
  - Unit tests with known test vectors
  - Documentation in `docs/sep/sep-53.md`

- **SEP Compatibility Matrix Automation**: 3-stage Python pipeline for generating field-by-field coverage reports
  - `sep_parser.py` - Fetches and parses SEP specifications from GitHub
  - `sep_analyzer.py` - Scans SDK Kotlin source and maps spec fields to implementation
  - `generate_sep_comparison.py` - Compares definitions against implementation and generates markdown matrices
  - `run_sep_analysis.py` - Orchestrator for all implemented SEPs

## [1.2.1] - 2026-02-11

### Added
- **SEP-8 (Regulated Assets)**: Production-ready client for assets requiring issuer approval before transactions can be submitted
  - `Sep08Service` class with service discovery and approval server interaction:
    - `fromDomain()` - Initialize from issuer's stellar.toml configuration
    - `postTransaction()` - Submit transactions to the approval server for regulatory approval
    - `postAction()` - Complete required user actions (e.g., KYC verification)
    - `authorizationRequired()` - Check if issuer has authorization required/revocable flags set
    - `regulatedAssets` - Discover regulated assets and their approval server URLs
  - `Sep08PostTransactionResponse` sealed class with 5 response types:
    - `Success` - Transaction approved, ready for submission
    - `Revised` - Transaction modified by approval server (e.g., additional operations added)
    - `Pending` - Approval server needs more time; resubmit after timeout
    - `ActionRequired` - User must complete an action (e.g., KYC) before approval
    - `Rejected` - Transaction rejected with reason
  - `Sep08PostActionResponse` sealed class with 2 response types:
    - `Done` - Action completed, transaction approved
    - `NextUrl` - Additional action required at a new URL
  - `RegulatedAsset` data class with asset code, issuer, approval server URL, and approval criteria
  - 4 exception types:
    - `Sep08Exception` - Base exception
    - `Sep08IncompleteInitDataException` - Missing network/Horizon configuration
    - `Sep08InvalidTransactionResponseException` - Malformed approval server response
    - `Sep08InvalidActionResponseException` - Malformed action URL response
  - 95 unit tests + 13 integration tests against live testnet
  - Documentation in `docs/sep/sep-08.md`
  - SEP-8 compatibility matrix showing 100% feature coverage (22/22 features)

### Removed
- Removed `testDeploySACWithSourceAccount` integration test. The test used `CONTRACT_ID_PREIMAGE_FROM_ADDRESS` with `CONTRACT_EXECUTABLE_STELLAR_ASSET`, a combination no longer accepted by the network. SAC deployment via `CONTRACT_ID_PREIMAGE_FROM_ASSET` (tested in `testSACWithAsset`) remains the correct approach.

## [1.2.0] - 2026-02-04

### Added
- **SEP-5 (Key Derivation Methods for Stellar Keys)**: HD wallet support for deriving multiple Stellar accounts from a single mnemonic phrase
  - `Mnemonic` class with BIP-39 mnemonic generation and SLIP-0010 key derivation:
    - `generate12/15/18/21/24WordsMnemonic()` - Generate mnemonics with varying entropy
    - `from()` - Create Mnemonic instance from phrase with optional passphrase
    - `fromEntropy()` - Create from raw entropy bytes
    - `fromBip39Seed()` / `fromBip39HexSeed()` - Create from pre-computed seed
    - `getKeyPair()` / `getAccountId()` - Derive Stellar accounts at index
    - `getPrivateKey()` / `getPublicKey()` - Get raw key bytes
    - `validate()` - Validate mnemonic phrase and checksum
    - `detectLanguage()` - Auto-detect mnemonic language
    - `close()` - Zero internal seed data for security
  - `MnemonicLanguage` enum with 9 BIP-39 languages:
    - English, Japanese, Korean, Spanish, Chinese Simplified, Chinese Traditional, French, Italian, Malay
  - `MnemonicStrength` enum for word count selection (128-256 bits entropy)
  - `MnemonicUtils` low-level utilities for advanced use cases
  - Exception classes: `InvalidMnemonicException`, `InvalidChecksumException`, `InvalidWordException`, `InvalidEntropyException`, `InvalidPathException`
  - Platform-specific crypto implementations:
    - JVM: BouncyCastle for PBKDF2-HMAC-SHA512
    - JS: libsodium-wrappers-sumo
    - Native: libsodium via C interop
  - 182 unit tests including all 5 official SEP-5 test vectors
  - Documentation in `docs/sep/sep-05.md`
  - SEP-5 compatibility matrix showing 100% feature coverage (31/31 features)

## [1.1.0] - 2026-02-03

### Added
- **Test Infrastructure**: Code coverage tooling and CI workflow improvements
  - Kover plugin for JVM code coverage with HTML and XML reports
  - Codecov integration with coverage badge in README
  - CI workflow: JVM tests across JDK 17/21/25 (push + PR), JS Node tests (push + PR), macOS native tests (PR only)
  - Integration test exclusion via `-PexcludeIntegrationTests` flag
  - Real wall-clock delay utilities (`platformDelay()`, `realDelay()`) for integration tests
  - Test reorganization: Split `commonTest` into `unitTests/` and `integrationTests/` directories
- **Unit Tests**: 133 new unit test files (~3,984 tests across 5 platforms)
  - Coverage includes: crypto, StrKey, KeyPair, transactions, operations, assets, accounts, memos, Horizon request builders, Horizon response deserialization, all operation response types, all effect types, Soroban RPC, contract client, assembled transactions, SEP-1/6/9/10/12/24/38/45, XDR round-trips

### Fixed
- **BigInteger two's complement (JS & Native)**: `bigIntegerToBytesSigned()` used magnitude-only encoding instead of proper two's complement. Negative Int128/Int256 values were silently corrupted on JS and Native targets. JVM was unaffected.
- **Native empty data crypto**: SHA-256 of empty data crashed (invalid assertion); Ed25519 sign/verify of empty data crashed (`addressOf(0)` on empty ByteArray)
- **SorobanServer.pollTransaction()**: Added `withContext(Dispatchers.Default)` for real wall-clock delay during polling, fixing too-fast polling on JS and Native

### Changed
- **JVM Target**: Bumped from Java 11 to Java 17 (required by Android AGP 8.x and Gradle 8+)

## [1.0.0] - 2026-01-15

### Added
- **SEP-45 (Web Authentication for Contract Accounts)**: Production-ready client for authenticating Soroban smart contract accounts
  - `WebAuthForContracts` class with challenge-response authentication flow:
    - `fromDomain()` - Initialize from stellar.toml SEP-45 configuration
    - `jwtToken()` - Complete authentication flow and receive JWT token
    - `getChallenge()` - Request authorization entries from server
    - `validateChallenge()` - Validate server challenge (13 security checks)
    - `signAuthorizationEntries()` - Sign entries with keypair(s)
    - `sendSignedChallenge()` - Submit signed entries for JWT
    - `decodeAuthorizationEntries()` / `encodeAuthorizationEntries()` - XDR utilities
  - `Sep45AuthToken` class with JWT parsing and claim extraction:
    - Extracts `account`, `issuedAt`, `expiresAt`, `issuer`, `clientDomain`
    - `isExpired()` method for token validation
  - `Sep45ClientDomainSigningDelegate` interface for remote signing:
    - String-based API (base64 XDR) optimized for HTTP remote signing
  - `Sep45ChallengeResponse` and `Sep45TokenResponse` data classes
  - 22 exception types with sealed hierarchy:
    - `Sep45Exception` base class
    - `Sep45ChallengeValidationException` sealed class with 12 specific validation exceptions
    - Request/response exceptions for HTTP errors
  - 13 security validation checks matching SEP-45 specification
  - Server signature verification using authorization preimage hash
  - Multi-signature support for contract authentication
  - Client domain verification with remote signing delegate
  - 161 unit tests + 2 integration tests against live testnet
  - Documentation in `docs/sep/sep-45.md` with usage examples
  - SEP-45 compatibility matrix showing 100% feature coverage (35/35 features)

### Documentation
- Added SEP-45 Web Authentication for Contract Accounts guide
- Added SEP-0045 compatibility matrix
- Updated README and CLAUDE.md with SEP-45 support
- Updated SEP README with SEP-45 entry

## [0.9.0] - 2026-01-14

### Added
- **SEP-6 (Deposit and Withdrawal API)**: Production-ready client for programmatic anchor transfers
  - `Sep06Service` class with nine API endpoints:
    - `info()` - Discover anchor capabilities and supported assets
    - `deposit()` - Initiate programmatic deposit
    - `depositExchange()` - Deposit with SEP-38 asset conversion
    - `withdraw()` - Initiate programmatic withdrawal
    - `withdrawExchange()` - Withdrawal with SEP-38 asset conversion
    - `fee()` - Query deposit/withdrawal fees (deprecated endpoint)
    - `transactions()` - Retrieve transaction history with pagination
    - `transaction()` - Get single transaction by ID
    - `patchTransaction()` - Update transaction with additional info
  - 8 request data classes:
    - `Sep06DepositRequest`, `Sep06DepositExchangeRequest`
    - `Sep06WithdrawRequest`, `Sep06WithdrawExchangeRequest`
    - `Sep06TransactionsRequest`, `Sep06TransactionRequest`
    - `Sep06FeeRequest`, `Sep06PatchTransactionRequest`
  - 23 response/data classes including:
    - `Sep06InfoResponse`, `Sep06DepositResponse`, `Sep06WithdrawResponse`
    - `Sep06Transaction` with all 35 SEP-6 fields
    - `Sep06FeeDetails`, `Sep06Refunds`, `Sep06FeatureFlags`
  - `Sep06TransactionStatus` enum with 17 statuses and helper methods (`isTerminal()`, `isError()`, `isPending()`)
  - `Sep06TransactionKind` enum with 4 kinds and helper methods (`isDeposit()`, `isWithdrawal()`, `isExchange()`)
  - 7 exception types for error handling:
    - `Sep06AuthenticationRequiredException` - JWT token required (403)
    - `Sep06CustomerInformationNeededException` - SEP-12 KYC required (403)
    - `Sep06CustomerInformationStatusException` - KYC pending/denied (403)
    - `Sep06InvalidRequestException` - Invalid request parameters (400)
    - `Sep06TransactionNotFoundException` - Transaction not found (404)
    - `Sep06ServerErrorException` - Server-side errors (500+)
    - `Sep06Exception` - Base exception for general errors
  - SEP-10 JWT authentication for all endpoints
  - SEP-38 quote integration for exchange operations
  - SEP-12 KYC integration via customer info exceptions
  - Claimable balance support for deposits
  - Callback notification support via `onChangeCallback` parameter
  - Refund tracking with payment breakdowns
  - 93 unit tests + 12 integration tests against live testnet
  - Documentation in `docs/sep/sep-06.md` with usage examples
  - SEP-6 compatibility matrix showing 100% API coverage (95/95 fields)

### Documentation
- Added SEP-6 Deposit and Withdrawal API guide
- Added SEP-0006 compatibility matrix
- Updated README and CLAUDE.md with SEP-6 support

## [0.8.0] - 2026-01-14

### Added
- **SEP-24 (Hosted Deposit and Withdrawal)**: Production-ready client for interactive anchor transfers
  - `Sep24Service` class with seven API endpoints:
    - `info()` - Discover anchor capabilities and supported assets
    - `deposit()` - Initiate interactive deposit flow
    - `withdraw()` - Initiate interactive withdrawal flow
    - `fee()` - Query deposit/withdrawal fees (deprecated endpoint)
    - `transactions()` - Retrieve transaction history
    - `transaction()` - Get single transaction details
    - `pollTransaction()` - Poll transaction until terminal status with configurable intervals
  - 5 request data classes:
    - `Sep24DepositRequest`, `Sep24WithdrawRequest`
    - `Sep24FeeRequest`, `Sep24TransactionsRequest`, `Sep24TransactionRequest`
  - 13 response/data classes:
    - `Sep24InfoResponse`, `Sep24AssetInfo`, `Sep24FeeEndpointInfo`, `Sep24Features`
    - `Sep24InteractiveResponse`, `Sep24FeeResponse`
    - `Sep24TransactionResponse`, `Sep24TransactionsResponse`, `Sep24Transaction`
    - `Sep24FeeDetails`, `Sep24FeeDetail`, `Sep24Refunds`, `Sep24RefundPayment`
  - `Sep24TransactionStatus` enum with 16 statuses including terminal states
  - 5 exception types for error handling:
    - `Sep24AuthenticationRequiredException` - JWT token required (403)
    - `Sep24InvalidRequestException` - Invalid request parameters (400)
    - `Sep24ServerErrorException` - Server-side errors (500+)
    - `Sep24TransactionNotFoundException` - Transaction not found (404)
    - `Sep24Exception` - Base exception for general errors
  - SEP-10 JWT authentication for all endpoints
  - SEP-38 quote integration for cross-asset transfers
  - Support for KYC fields (SEP-9) in deposit/withdraw requests
  - Transaction polling with callbacks for status changes
  - Claimable balance support for deposits
  - Refund tracking with payment breakdowns
  - 38 unit tests + 9 integration tests against live testnet
  - Documentation in `docs/sep/sep-24.md` with usage examples
  - SEP-24 compatibility matrix showing 100% API coverage (128/128 fields)

### Documentation
- Added SEP-24 Hosted Deposit and Withdrawal guide
- Added SEP-0024 compatibility matrix
- Updated SEP README with SEP-24 entry

## [0.7.0] - 2025-12-17

### Added
- **SEP-38 (Anchor RFQ API)**: Production-ready client for anchor price quotes and exchange rate discovery
  - `QuoteService` class with five API endpoints:
    - `info()` - Discover supported assets and delivery methods
    - `prices()` - Get all available exchange prices for an asset
    - `price()` - Get indicative price for a specific asset pair
    - `postQuote()` - Request a firm quote with guaranteed rate
    - `getQuote()` - Retrieve an existing quote by ID
  - 12 data model classes for requests and responses:
    - `Sep38InfoResponse`, `Sep38PricesResponse`, `Sep38PriceResponse`
    - `Sep38QuoteRequest`, `Sep38QuoteResponse`
    - `Sep38Asset`, `Sep38BuyAsset`, `Sep38SellAsset`
    - `Sep38DeliveryMethod`, `Sep38Fee`, `Sep38FeeDetail`
  - 5 exception types for error handling:
    - `Sep38BadRequestException` - Invalid request parameters (400)
    - `Sep38PermissionDeniedException` - Authentication failure (403)
    - `Sep38NotFoundException` - Quote not found (404)
    - `Sep38UnknownResponseException` - Unexpected response codes
    - `Sep38Exception` - Base exception for general errors
  - SEP-10 JWT authentication for firm quotes
  - Service discovery via stellar.toml (ANCHOR_QUOTE_SERVER)
  - Support for multiple asset identification formats (stellar, iso4217, etc.)
  - Context-aware quoting for SEP-6, SEP-24, and SEP-31 integrations
  - Delivery method filtering for buy/sell operations
  - Detailed fee breakdowns with individual fee components
  - 48 unit tests + 9 integration tests against live testnet
  - Documentation in `docs/sep/sep-38.md` with usage examples
  - SEP-38 compatibility matrix showing 100% API coverage (63/63 fields)

### Documentation
- Added SEP-38 Anchor RFQ API guide with price discovery and firm quote examples
- Added SEP-0038 compatibility matrix

## [0.6.0] - 2025-12-09

### Added
- **SEP-09 (Standard KYC Fields)**: Type-safe data classes for standardized KYC information
  - `NaturalPersonKYCFields` - 34 fields for individual customer data (name, address, documents, etc.)
  - `OrganizationKYCFields` - 17 fields for business customer data with automatic field prefixing
  - `FinancialAccountKYCFields` - 14 fields for bank account information
  - `CardKYCFields` - 11 fields for payment card data with automatic field prefixing
  - `StandardKYCFields` - Composite class combining all field types
  - `LocalDate` for date fields, `ByteArray` for binary data (photos, documents)
  - 128 unit tests covering all field types and edge cases
  - Documentation in `docs/sep/sep-09.md` with usage examples
  - SEP-09 compatibility matrix showing 100% field coverage

- **SEP-12 (KYC API)**: Production-ready client for anchor KYC services
  - `KYCService` class with seven API endpoints:
    - `getCustomer()` - Retrieve customer status and required fields
    - `putCustomer()` - Submit or update customer information
    - `putCustomerVerification()` - Submit verification codes
    - `deleteCustomer()` - Request customer data deletion
    - `getCustomerFiles()` - List uploaded files
    - `putCustomerCallback()` - Register status change callbacks
  - SEP-10 JWT authentication integrated across all endpoints
  - Multipart/form-data support for file uploads (photos, documents)
  - Text fields sent before binary data per specification
  - `CallbackSignatureVerifier` for webhook signature validation
  - Six exception types for granular error handling:
    - `CustomerNotFoundException` - Customer record not found
    - `CustomerAlreadyExistsException` - Duplicate customer registration
    - `InvalidFieldException` - Field validation errors with details
    - `FileTooLargeException` - File size limit exceeded
    - `UnauthorizedException` - Authentication failures
    - `KYCException` - Base exception for other errors
  - Support for standard accounts (G...) and muxed accounts (M...)
  - 105 unit tests + 13 integration tests against live Stellar testnet
  - Documentation in `docs/sep/sep-12.md` with usage examples
  - SEP-12 compatibility matrix showing 100% API coverage

### Documentation
- Added SEP-09 Standard KYC Fields guide with data class examples
- Added SEP-12 KYC API guide with authentication and file upload examples
- Added SEP-0009 and SEP-0012 compatibility matrices
- Updated all compatibility matrices to version 0.6.0
- Updated README with SEP-09 and SEP-12 links

## [0.5.1] - 2025-11-25

### Added
- **SEP-10 (Stellar Web Authentication)**: Production-ready client-side implementation for secure authentication with Stellar anchors and services
  - `WebAuth` class with high-level `jwtToken()` API and low-level methods
  - `AuthToken` class for JWT parsing with property-style API (account, memo, jti, isExpired, etc.)
  - All 13 SEP-10 validation checks implemented correctly
  - Support for standard accounts (G...), muxed accounts (M...), and memo IDs
  - Client domain verification with `ClientDomainSigningDelegate` support
  - Multi-signature transaction signing with signature preservation
  - 115+ test cases (108 unit tests + 4 integration tests + 3 signature reordering tests)
  - 18 exception types with security warnings
  - Integration tests against live Stellar testnet anchor
  - Documentation in `docs/sep/sep-10.md` with usage examples
  - SEP-10 compatibility matrix showing 100% implementation coverage

### Changed
- **Dependencies**: Major version upgrades for improved performance and modern features
  - Ktor 2.3.8 → 3.3.2 (90%+ I/O performance improvement)
  - kotlinx-coroutines 1.8.0 → 1.10.2
  - kotlinx-serialization 1.6.3 → 1.9.0
  - bignum 0.3.9 → 0.3.10

### Fixed
- Ktor HttpTimeout deprecation warnings (INFINITE_TIMEOUT_MS in SSE streams)
- SEP compatibility matrix formatting (metadata fields now appear on separate lines on GitHub)

### Documentation
- Added SEP-10 Web Authentication guide with authentication examples
- Added SEP-0010 compatibility matrix
- Updated all compatibility matrices to version 0.5.1

## [0.4.0] - 2025-11-14

### Added
- **SEP-1 (Stellar TOML)**: Complete implementation with 71 fields across 5 data classes (GeneralInformation, Documentation, PointOfContact, Currency, Validator)
  - `StellarToml.fromDomain()` - Fetch stellar.toml from domains
  - `StellarToml.parse()` - Parse stellar.toml from string
  - `StellarToml.currencyFromUrl()` - Load external currency TOML files
  - Custom TOML parser with error correction
  - 33 tests including integration tests with real-world stellar.toml files (stellar.org, testanchor.stellar.org, circle.com, stellar.moneygram.com)
  - Documentation in `docs/sep/sep-01.md` with 7 usage examples
- **Infrastructure**: GitHub Pages deployment with automated Dokka API documentation generation

### Changed
- **Demo App**: Performance improvements with memoized form validation and smart auto-scroll (v1.3.0)
- **Documentation**: Major accuracy improvements across all platform guides and getting started materials
  - Fixed SDK class/property names and removed hypothetical code examples
  - Consolidated duplicate content and improved navigation structure
  - Added documentation strategy guide defining core principles and guidelines

### Fixed
- **GitHub Pages**: Fixed landing page redirect and Dokka V2 workflow configuration
- **Build**: Resolved Gradle wrapper jar tracking and gitignore rule ordering issues

### Documentation
- Complete documentation overhaul with enhanced accuracy and organization
- Added `docs/documentation-strategy.md` with core principles and quality guidelines
- Enhanced compatibility reports with conditional gaps section
- Updated demo app documentation with screenshots for all platforms

### Infrastructure
- Automated API documentation deployment via GitHub Pages
- Removed build artifacts from repository and updated gitignore rules

## [0.3.0] - 2025-11-08

### Added
- **Horizon API**: Added missing parameters to match official Horizon specification (improved API coverage)
- **Demo App**: Added Invoke Token Contract demo showcasing token interaction
- **Demo App**: Added Info screen with centralized version display and auto-scroll functionality
- **Type Conversion**: Complete scValToNative implementation with error and contract instance support
- **Contract Spec**: Complete SCSpecType support in ContractSpec for all Soroban types
- **Demo App**: Modernized UI with complete redesign and comprehensive app icons for all platforms

### Changed
- **Demo App**: Refactored macOS demo app with updated documentation for 11 demo features
- **Documentation**: Updated for accuracy and conciseness across all docs

### Fixed
- **Web App**: Fixed WASM loading for production deployments
- **RPC**: Fixed getEvents RPC implementation to match Stellar RPC specification

## [0.2.1] - 2025-10-25

### Fixed
- **Maven Publishing**: Repository URLs now correctly point to `https://github.com/Soneso/kmp-stellar-sdk` (previously pointed to incorrect repository `stellar-kotlin-multiplatform-sdk`)
- **Maven Publishing**: Package description updated to "Kotlin Multiplatform Stellar SDK"
- **Documentation**: Removed broken links to non-existent documentation files (docs/testing.md, docs/features.md, docs/platforms.md)
- **Documentation**: Updated Platform Guide link to point to correct docs/platforms/ directory
- **macOS Setup**: Corrected macOS demo app setup instructions to require `brew install libsodium`

### Changed
- **Documentation**: Removed beta status warnings from README to reflect production-ready status
- **Documentation**: Added standard Apache License warranty notice

## [0.2.0] - 2025-10-24

### Breaking Changes - ContractClient API Simplification

#### Removed
- `ContractClient.withoutSpec()` factory method - ContractClient now always requires a contract spec. For low-level contract interaction without a spec, use `SorobanServer` + `TransactionBuilder` directly.
- `ContractClient.invokeWithXdr()` method - Replaced by `buildInvoke()` with ergonomic Map-based arguments

#### Changed
- **Renamed**: `ContractClient.fromNetwork()` → `ContractClient.forContract()`
  - **Rationale**: "forContract" better emphasizes creating a client FOR a contract, not loading FROM network
  - **Migration**: Simple find-and-replace across your codebase

#### Added
- `ContractClient.buildInvoke()` - New method for manual transaction control
  - **Primary use case**: Multi-signature workflows where multiple parties need to sign authorization entries before submission
  - **Arguments**: Takes `Map<String, Any?>` (beginner-friendly) instead of `List<SCValXdr>` (XDR types)
  - **Returns**: `AssembledTransaction` for manual signing and submission
  - **Other use cases**: Adding memos, custom preconditions, simulation inspection, time bounds
  - **Example**:
    ```kotlin
    val assembled = client.buildInvoke<String>(
        functionName = "transfer",
        arguments = mapOf("from" to addr1, "to" to addr2, "amount" to 1000)
    )

    // Detect who needs to sign
    val whoNeedsToSign = assembled.needsNonInvokerSigningBy()
    if (whoNeedsToSign.contains(account1Id)) {
        assembled.signAuthEntries(account1Keypair)
    }

    val result = assembled.signAndSubmit(signer)
    ```

#### Migration Guide

**Factory Method Rename**:
```kotlin
// OLD (0.1.0-beta.1)
val client = ContractClient.fromNetwork(contractId, rpcUrl, Network.TESTNET)

// NEW (0.2.0+)
val client = ContractClient.forContract(contractId, rpcUrl, Network.TESTNET)
```

**Manual Transaction Control**:
```kotlin
// OLD (0.1.0-beta.1) - XDR-based
val xdrArgs = client.funcArgsToXdrSCValues("transfer", listOf(...))
val assembled = client.invokeWithXdr("transfer", xdrArgs)

// NEW (0.2.0+) - Map-based
val assembled = client.buildInvoke<T>(
    functionName = "transfer",
    arguments = mapOf("from" to addr1, "to" to addr2, "amount" to 1000)
)
```

**No-Spec Mode Removed**:
```kotlin
// OLD (0.1.0-beta.1)
val client = ContractClient.withoutSpec(contractId, rpcUrl, Network.TESTNET)

// NEW (0.2.0+) - Use low-level APIs instead
// ContractClient now always requires a spec
// For no-spec scenarios, use SorobanServer + TransactionBuilder directly
```

---

## [0.1.0-beta.1] - 2025-10-23

### Initial Beta Release

This is the first beta release of the Stellar SDK for Kotlin Multiplatform. The SDK provides comprehensive functionality for building Stellar applications across JVM, iOS, macOS, and JavaScript platforms.

**Status**: BETA - Not recommended for production use yet. API may change in subsequent beta releases.

### Platform Support

#### Supported Platforms
- **JVM**: Android API 24+ and Server applications (Java 11+)
- **iOS**: iOS 14.0+ (iosX64, iosArm64, iosSimulatorArm64)
- **macOS**: macOS 11.0+ (macosX64, macosArm64)
- **JavaScript**: Browser (WebAssembly) and Node.js 14+

### Features

#### Core Cryptography
- Ed25519 keypair generation, signing, and verification
- Production-ready crypto libraries on all platforms:
  - JVM: BouncyCastle (bcprov-jdk18on:1.78)
  - iOS/macOS: libsodium (native C interop)
  - JavaScript: libsodium-wrappers-sumo (WebAssembly)
- Constant-time operations for timing attack protection
- Comprehensive input validation and error handling
- Async API using Kotlin suspend functions for proper cross-platform support

#### StrKey Encoding
- Support for all Stellar address formats:
  - Ed25519 public keys (G... addresses)
  - Ed25519 secret seeds (S... seeds)
  - Muxed accounts (M... addresses)
  - Contract addresses (C... addresses)
- CRC16-XModem checksum validation
- Platform-optimized Base32 encoding

#### Transaction Building
- `TransactionBuilder` with fluent API
- `FeeBumpTransactionBuilder` for fee bump transactions
- All 27 Stellar operations implemented
- Memo support (none, text, ID, hash, return)
- Time bounds and ledger bounds
- Transaction preconditions (min sequence, sequence age/gap, extra signers)
- Multi-signature support
- Soroban transaction data (resource limits, footprint)
- XDR serialization/deserialization

#### Operations (All 27)
**Account Operations**:
- CreateAccount, AccountMerge, BumpSequence, SetOptions, ManageData

**Payment Operations**:
- Payment, PathPaymentStrictReceive, PathPaymentStrictSend

**Asset Operations**:
- ChangeTrust, AllowTrust, SetTrustLineFlags

**Trading Operations**:
- ManageSellOffer, ManageBuyOffer, CreatePassiveSellOffer

**Claimable Balance Operations**:
- CreateClaimableBalance, ClaimClaimableBalance, ClawbackClaimableBalance

**Liquidity Pool Operations**:
- LiquidityPoolDeposit, LiquidityPoolWithdraw

**Sponsorship Operations**:
- BeginSponsoringFutureReserves, EndSponsoringFutureReserves, RevokeSponsorship

**Clawback Operations**:
- Clawback

**Soroban Operations**:
- InvokeHostFunction, ExtendFootprintTTL, RestoreFootprint

**Deprecated Operations**:
- Inflation (protocol 12 deprecated, but supported for compatibility)

#### Assets & Accounts
- AssetTypeNative (XLM/Lumens)
- AssetTypeCreditAlphaNum4 (1-4 character asset codes)
- AssetTypeCreditAlphaNum12 (5-12 character asset codes)
- Asset parsing from canonical strings ("CODE:ISSUER")
- Contract ID derivation for Stellar Asset Contracts (SAC)
- Muxed accounts with ID support
- Account management with automatic sequence number handling

#### Horizon API Client
- Comprehensive REST API coverage
- Request builders for all endpoints:
  - Accounts (details, data entries, balances)
  - Assets (list, search, filter)
  - Claimable Balances (query, filter by sponsor/claimant/asset)
  - Effects (all 60+ effect types, filtering, streaming)
  - Ledgers (list, details, operations, transactions)
  - Liquidity Pools (list, details, operations, trades)
  - Offers (list by account, order books)
  - Operations (all 27 operation types, filtering, streaming)
  - Payments (payment filtering, streaming)
  - Trades (trade history, filtering, aggregations)
  - Transactions (submit, query, filter)
  - Paths (strict send, strict receive path finding)
  - Fee Stats (network fee statistics)
  - Health (server health monitoring)
  - Root (server information)
- Server-Sent Events (SSE) streaming support
- Automatic retries and error handling
- SEP-29 account memo validation (AccountRequiresMemoException)
- Cursor-based pagination
- Order (asc/desc) and limit parameter support

#### Soroban Smart Contracts

**High-Level API**:
- `ContractClient` with dual-mode interaction:
  - Beginner API: `invoke()` with Map<String, Any?> arguments
  - Power API: `invokeWithXdr()` with List<SCValXdr> for manual control (deprecated in 0.2.0, replaced by `buildInvoke()`)
- Factory methods: `fromNetwork()` loads contract spec (renamed to `forContract()` in 0.2.0), `withoutSpec()` for manual mode (removed in 0.2.0)
- Type conversion helpers:
  - `funcArgsToXdrSCValues()` - Convert native types to XDR
  - `funcResToNative()` - Convert XDR results to native types
- Smart contract deployment:
  - One-step: `deploy()` with Map-based constructor args
  - Two-step: `install()` + `deployFromWasmId()` for WASM reuse
- `AssembledTransaction` for full transaction lifecycle
- Type-safe generic results with custom parsers
- Automatic simulation and resource estimation
- Auto-execution: Read calls return results, write calls auto-sign and submit
- Read-only vs write call detection via auth entries

**Authorization**:
- Sign Soroban authorization entries (Auth class)
- Build authorization entries from scratch
- Custom Signer interface support
- Network replay protection
- Signature verification
- Auto-authorization for invoker
- Custom authorization handling for complex scenarios

**Contract Operations**:
- Contract invocation (InvokeHostFunctionOperation)
- WASM upload and contract deployment
- State restoration when expired
- Footprint TTL extension
- Transaction polling with exponential backoff

**RPC Client** (`SorobanServer`):
- Full Soroban RPC API coverage
- Transaction simulation
- Event queries and filtering
- Ledger and contract data retrieval
- Network information queries
- Health monitoring

**Contract Spec & Parsing**:
- ContractSpec parsing from XDR
- WASM analysis and metadata extraction
- Function signature detection
- Type parsing and validation

**Exception Handling** (10 types):
- ContractException (base)
- SimulationFailedException
- SendTransactionFailedException
- TransactionFailedException
- TransactionStillPendingException
- ExpiredStateException
- RestorationFailureException
- NotYetSimulatedException
- NeedsMoreSignaturesException
- NoSignatureNeededException

#### XDR System
- Complete XDR type system (470+ types)
- XDR serialization/deserialization
- Type-safe XDR unions and enums
- XDR validation

#### Scval (Smart Contract Values)
- Type conversions to/from SCValXdr
- Support for all Soroban types
- Address, symbol, bytes, numbers, vectors, maps
- Type validation and error handling

#### Utility Features
- Network support (TESTNET, PUBLIC, custom networks)
- FriendBot integration for testnet account funding
- Comprehensive error handling throughout

### Demo Application

Comprehensive multi-platform demo application showcasing SDK functionality:

**Platforms**:
- Android (Jetpack Compose)
- iOS (SwiftUI wrapper for Compose Multiplatform)
- macOS (Native SwiftUI)
- Desktop (JVM Compose)
- Web (Kotlin/JS with Compose, Vite dev server)

**Features** (10 comprehensive demos):
1. Key Generation - Generate and manage Ed25519 keypairs
2. Fund Testnet Account - Get free test XLM from Friendbot
3. Fetch Account Details - Retrieve account information from Horizon
4. Trust Asset - Establish trustlines for issued assets
5. Send Payment - Transfer XLM and issued assets
6. Fetch Transaction Details - View transaction operations and events
7. Fetch Smart Contract Details - Parse and inspect Soroban contracts
8. Deploy Smart Contract - Deploy Soroban WASM contracts to testnet
9. Invoke Hello World Contract - Simple contract invocation with result parsing
10. Invoke Auth Contract - Dynamic authorization handling

**Architecture**:
- 95% code sharing (Compose UI + business logic in commonMain)
- Real Stellar testnet integration
- Production-ready patterns and best practices

### Dependencies

**Common**:
- kotlinx-serialization: 1.6.3
- kotlinx-coroutines: 1.8.0
- kotlinx-datetime: 0.7.1
- ktor-client-core: 2.3.8
- bignum: 0.3.9

**JVM**:
- BouncyCastle: 1.78
- Apache Commons Codec: 1.16.1

**JavaScript**:
- libsodium-wrappers-sumo: 0.7.13 (via npm)

**Native (iOS/macOS)**:
- libsodium (via C interop)
- ktor-client-darwin: 2.3.8

### Testing

- Comprehensive test coverage across all platforms
- Unit tests for all core functionality
- Integration tests against live Stellar Testnet
- Contract client integration tests
- Platform-specific test suites (JVM, JS Node, JS Browser, Native)

### Documentation

- Comprehensive README with quick start guide
- Getting Started guide
- Features guide with examples
- Platform-specific setup guide
- Testing guide
- Architecture documentation (CLAUDE.md)
- Demo app documentation
- Horizon API compatibility matrix
- Soroban RPC compatibility matrix

### Known Limitations

1. **JavaScript Testing**: Running all test classes together currently hangs due to Kotlin/JS test bundling limitations. Individual test classes work perfectly. Workaround: Run tests with `--tests` filter or individually.

2. **iOS x86_64 Tests**: Disabled because libsodium.a only includes ARM64 architecture. x86_64 simulators are rare with Apple Silicon.

3. **Production Readiness**: This is a beta release. While comprehensive testing has been performed, we recommend additional testing before production use. API may change in subsequent beta releases.

### Breaking Changes

None (initial release).

### Migration Guide

None (initial release).

### Contributors

Built with Claude Code - AI-powered development assistant.

### Acknowledgments

- Inspired by the [Java Stellar SDK](https://github.com/stellar/java-stellar-sdk)
- Uses production cryptography from BouncyCastle and libsodium
- Built with Kotlin Multiplatform, Ktor, and Compose Multiplatform

---

[0.1.0-beta.1]: https://github.com/Soneso/kmp-stellar-sdk/releases/tag/v0.1.0-beta.1
