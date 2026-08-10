# Package com.soneso.stellar.sdk.xdr

The XDR type system: one Kotlin type per definition in the Stellar `.x` files, plus the readers,
writers and extensions that move values between Kotlin, the binary XDR wire format and JSON.

Types in this package are generated from the `.x` sources and are not edited by hand.

## Binary form

`encode(writer)` and `Companion.decode(reader)` exist on every type. The commonly exchanged
types additionally have `toXdrBase64()` and `Companion.fromXdrBase64(String)` extensions, which
are the pair to reach for when a value crosses a network or storage boundary.

## JSON form (SEP-0051)

Every type also carries four members that convert to and from XDR-JSON, the canonical JSON
rendering SEP-0051 defines:

- `toXdrJson(): String` — the canonical document for this value.
- `toXdrJsonElement(): JsonElement` — the same document as a tree, for inspecting or building
  one without a second parse.
- `Companion.fromXdrJson(json: String)` — parse and decode a document.
- `Companion.fromXdrJsonElement(element: JsonElement)` — decode an already-parsed tree.

The conversion is lossless in both directions and the output is canonical: compact, with object
keys in XDR declaration order, so equal values always produce byte-identical documents.

The mapping rules worth knowing before reading a document:

- 32-bit integers are JSON numbers; 64-bit integers are base-10 JSON **strings**, because a JSON
  number cannot carry 64 bits of precision on every platform that reads one. A 64-bit value is
  also accepted as a number on input.
- Opaque data is a lowercase hexadecimal string, empty opaque is `""`, and arrays are always
  present, empty ones as `[]`.
- A union arm carrying no value is a bare string naming the arm; an arm carrying a value is a
  single-key object. An unset optional is `null` with its key still present — which is not the
  same shape as a void arm.
- Types with a text form use it: accounts, contracts, pools, claimable balances and signer keys
  render as strkeys, and the 128-bit and 256-bit integer types render as one decimal string.

Decoding accepts only the spelling encoding produces — lowercase hexadecimal, lowercase `\xNN`
escapes, plain base-10 integer literals — and raises `IllegalArgumentException` for every
malformed input, naming the type and the offending key.

The full mapping table, the documented limitations and the input-strictness rules are in
`docs/sep/sep-51.md`.
