# SEP-51: XDR-JSON

SEP-51 defines a canonical JSON rendering for XDR values. Every generated XDR type in the SDK converts to that rendering and back, so binary Stellar data — transaction envelopes, results, meta, ledger entries, contract values — can be read, logged and moved through JSON systems without leaving the SDK.

A value rendered to XDR-JSON and read back encodes to the byte sequence it started from, and the rendering is canonical, so the same value always produces the same document. One category of member is not byte-exact, for a reason that predates the JSON layer; see [Limitations](#limitations).

**Use Cases**:
- Inspect a transaction envelope, result or meta while debugging, instead of reading base64 by hand
- Log or persist XDR in a form that stays readable and diffable
- Move XDR through systems that speak JSON: message queues, HTTP APIs, test fixtures
- Build developer tooling such as explorers, command-line utilities and fixture generators
- Exchange documents with any other tool that implements SEP-0051

## Quick Start

```kotlin
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import com.soneso.stellar.sdk.xdr.toXdrBase64

fun quickExample() {
    // A contract value as it arrives from the network
    val value = SCValXdr.fromXdrBase64("AAAADwAAAAh0cmFuc2Zlcg==")
    println(value.toXdrJson()) // {"symbol":"transfer"}

    // And back: the JSON re-encodes to the bytes it came from
    val restored = SCValXdr.fromXdrJson("{\"symbol\":\"transfer\"}")
    println(restored.toXdrBase64()) // AAAADwAAAAh0cmFuc2Zlcg==
}
```

## The Four Members

Every generated XDR type carries the same four members. They are ordinary functions, not `suspend` functions, and need no network access.

```kotlin
import com.soneso.stellar.sdk.xdr.TimeBoundsXdr
import kotlinx.serialization.json.JsonElement

fun theFourMembers() {
    val bounds = TimeBoundsXdr.fromXdrJson("{\"min_time\":\"0\",\"max_time\":\"1700000000\"}")

    // String boundary
    val text: String = bounds.toXdrJson()
    val fromText: TimeBoundsXdr = TimeBoundsXdr.fromXdrJson(text)

    // Tree boundary, for inspecting or building a document without re-parsing it
    val tree: JsonElement = bounds.toXdrJsonElement()
    val fromTree: TimeBoundsXdr = TimeBoundsXdr.fromXdrJsonElement(tree)

    println(fromText == fromTree) // true
}
```

`JsonElement` comes from `kotlinx.serialization.json`, which the SDK exposes as an `api` dependency, so it is on the compile classpath of any project that depends on the SDK.

## Reading a Transaction Envelope

```kotlin
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64

fun readEnvelope() {
    val base64 = "AAAAAgAAAADmmSZkwY3163TMouB2TY8MljqXw2IxVYTGyvDrR6YtAAAqmmQAABpuAAAAAQAAAAAA" +
        "AAAAAAAAAQAAAAAAAAAYAAAAAQAAAAEAAAAAAAAAAQAAAAAAAAABAAAAAAAAAAAAAAABAAAABgAA" +
        "AAHXkotywnA8z+r365/0701QSlWouXn8m0UOoshCtNHOYQAAABQAAAABAAI9fQAAAAAAAAD4AAAA" +
        "AAAqmgAAAAABR6YtAAAAAEArDtxbqUI+CsdkRmV0lFhVt0wyB7fyrmmkM6Fr35wpPcK8WKcXeKTl" +
        "4BQ+akE14MZtpaea9LMdhXopaW3pJA0E"

    println(TransactionEnvelopeXdr.fromXdrBase64(base64).toXdrJson())
}
```

The real output is a single compact line. Indented and abridged here for reading, it shows most of the mapping rules at once — an account as a strkey, a sequence number as a base-10 string, a void union arm as a bare string, an unset optional as `null`, an empty array as `[]`, and binary members as hexadecimal:

```json
{
  "tx": {
    "tx": {
      "source_account": "GDTJSJTEYGG7L23UZSROA5SNR4GJMOUXYNRDCVMEY3FPB22HUYWQBZIA",
      "fee": 2792036,
      "seq_num": "29059748724737",
      "cond": "none",
      "memo": "none",
      "operations": [
        {
          "source_account": null,
          "body": {
            "invoke_host_function": {
              "host_function": {
                "create_contract": {
                  "contract_id_preimage": { "asset": "native" },
                  "executable": "stellar_asset"
                }
              },
              "auth": []
            }
          }
        }
      ],
      "ext": { "v1": { "ext": "v0", "resources": { "...": "..." }, "resource_fee": "2791936" } }
    },
    "signatures": [
      {
        "hint": "47a62d00",
        "signature": "2b0edc5ba9423e0ac764466574945855b74c3207b7f2ae69a433a16bdf9c293d..."
      }
    ]
  }
}
```

## Reading a Transaction Result

Horizon and the RPC server return results as base64 XDR. Rendering one as JSON names the failure instead of leaving a code to look up.

```kotlin
import com.soneso.stellar.sdk.xdr.TransactionResultXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64

fun readResult() {
    val resultXdr = "AAAAAAAAAGT/////AAAAAQAAAAAAAAAB/////wAAAAA="
    println(TransactionResultXdr.fromXdrBase64(resultXdr).toXdrJson())
    // {"fee_charged":"100","result":{"tx_failed":[{"op_inner":{"payment":"malformed"}}]},"ext":"v0"}
}
```

## Working With the JSON Tree

`toXdrJsonElement()` returns the document as a `JsonElement`, so a field can be read without a second parse.

```kotlin
import com.soneso.stellar.sdk.xdr.TransactionResultXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun inspectTree() {
    val result = TransactionResultXdr.fromXdrBase64("AAAAAAAAAGT/////AAAAAQAAAAAAAAAB/////wAAAAA=")
    val tree = result.toXdrJsonElement().jsonObject

    // 64-bit integers are strings, so read the content rather than a numeric field
    println(tree["fee_charged"]!!.jsonPrimitive.content) // 100

    // A union renders as a single-key object; its key names the arm
    println(tree["result"]!!.jsonObject.keys.single()) // tx_failed
}
```

`fromXdrJsonElement` accepts a tree built by hand, which is the shorter path when a document is assembled rather than parsed.

```kotlin
import com.soneso.stellar.sdk.xdr.TimeBoundsXdr
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun buildTree() {
    val bounds = TimeBoundsXdr.fromXdrJsonElement(
        buildJsonObject {
            put("min_time", "0")
            put("max_time", "1700000000")
        }
    )
    println(bounds.maxTime.value.value) // 1700000000
}
```

## JSON Forms by XDR Type

### Integers

32-bit integers are JSON numbers. 64-bit integers are base-10 JSON **strings**, because a JSON number cannot carry 64 bits of precision on every platform that reads one. The string form applies wherever a 64-bit value appears, including a type that is nothing but a 64-bit integer.

```kotlin
import com.soneso.stellar.sdk.xdr.Int32Xdr
import com.soneso.stellar.sdk.xdr.Int64Xdr
import com.soneso.stellar.sdk.xdr.Uint64Xdr

fun integers() {
    println(Int32Xdr.fromXdrJson("2147483647").toXdrJson())  // 2147483647
    println(Int64Xdr.fromXdrJson("\"-9223372036854775808\"").toXdrJson())
    // "-9223372036854775808"
    println(Uint64Xdr.fromXdrJson("\"18446744073709551615\"").toXdrJson())
    // "18446744073709551615"

    // A JSON number is also accepted for a 64-bit value, for documents written
    // against version 1 of the specification. Output is always the string form.
    println(Int64Xdr.fromXdrJson("1").toXdrJson()) // "1"
}
```

A 32-bit integer accepts only a JSON number: the string form is rejected.

### Booleans

A boolean is a JSON boolean, as a struct member and as a union arm alike.

```kotlin
import com.soneso.stellar.sdk.xdr.EvictionIteratorXdr
import com.soneso.stellar.sdk.xdr.SCValXdr

fun booleans() {
    val json = "{\"bucket_list_level\":3,\"is_curr_bucket\":true,\"bucket_file_offset\":\"64\"}"
    println(EvictionIteratorXdr.fromXdrJson(json).toXdrJson() == json) // true

    // The same value inside a contract value, where the arm key names the type
    println(SCValXdr.fromXdrJson("{\"bool\":true}").toXdrJson()) // {"bool":true}
}
```

Only `true` and `false` are accepted. The strings `"true"` and `"false"`, and the numbers `1` and `0`, are rejected.

### Opaque Data

Opaque data is a lowercase hexadecimal string, whether the XDR declares a fixed or a variable length. Empty variable-length data is `""`.

```kotlin
import com.soneso.stellar.sdk.xdr.SCBytesXdr
import com.soneso.stellar.sdk.xdr.ThresholdsXdr

fun opaque() {
    println(ThresholdsXdr.fromXdrJson("\"61626364\"").toXdrJson()) // "61626364"
    println(SCBytesXdr.fromXdrJson("\"\"").toXdrJson())            // ""
}
```

A fixed-length field validates its byte count on input, so a `Thresholds` of three or five bytes is rejected rather than silently truncated or padded.

### Strings

A string member is escaped byte by byte, with the first matching rule winning:

| Byte | Escape |
|---|---|
| `0x00` | `\0` |
| `0x09` | `\t` |
| `0x0A` | `\n` |
| `0x0D` | `\r` |
| `0x5C` | `\\` |
| `0x20`-`0x7E` | the character itself |
| anything else | `\xNN`, two lowercase hexadecimal digits |

The result is then a JSON string, so the JSON encoder escapes each backslash a second time. A single backslash byte therefore reaches the document as four characters.

```kotlin
import com.soneso.stellar.sdk.xdr.SCStringXdr

fun strings() {
    // One backslash byte: "\\" after the ladder, "\\\\" after JSON encoding
    println(SCStringXdr("\\").toXdrJson()) // "\\\\"

    // A byte outside the printable range becomes \xNN
    println(SCStringXdr("café").toXdrJson()) // "caf\\xc3\\xa9"
}
```

### Arrays

An array is a JSON array, always present. An empty one is `[]` and is never omitted.

```kotlin
import com.soneso.stellar.sdk.xdr.SorobanResourcesExtV0Xdr

fun arrays() {
    println(SorobanResourcesExtV0Xdr.fromXdrJson("{\"archived_soroban_entries\":[1,2,3,4]}").toXdrJson())
    // {"archived_soroban_entries":[1,2,3,4]}
    println(SorobanResourcesExtV0Xdr.fromXdrJson("{\"archived_soroban_entries\":[]}").toXdrJson())
    // {"archived_soroban_entries":[]}
}
```

### Enums

An enum is a string: the member identifier in snake_case, with the prefix its members share stripped when the enum has more than one member.

```kotlin
import com.soneso.stellar.sdk.xdr.SCValTypeXdr

fun enums() {
    println(SCValTypeXdr.SCV_U32.toXdrJson())  // "u32"
    println(SCValTypeXdr.SCV_BOOL.toXdrJson()) // "bool"
}
```

Three details decide the name where the rule is not obvious from an example:

- **The shared prefix ends at its last underscore.** If the members share no complete underscore-delimited token, nothing is stripped, which is why `OperationResultCode` keeps its `op`: `opINNER` renders as `"op_inner"`.
- **A single-member enum is never stripped.** `PublicKeyType` has one member, so `PUBLIC_KEY_TYPE_ED25519` renders in full as `"public_key_type_ed25519"`.
- **A remainder that would start with a digit keeps the first character of the stripped prefix**, since a name may not begin with one. `BinaryFuseFilterType` strips `BINARY_FUSE_FILTER_` from `BINARY_FUSE_FILTER_8_BIT`, leaving `8_BIT`, and prepends the prefix's `B` to give `"b8_bit"`.

Word boundaries are taken at underscores and at case changes, so `ContractCostType.WasmInsnExec` renders as `"wasm_insn_exec"`. The same splitting applies to struct field names: `signerSponsoringIDs` becomes the key `signer_sponsoring_i_ds`.

### Structs

A struct is an object whose keys are the XDR field names in snake_case, emitted in declaration order.

```kotlin
import com.soneso.stellar.sdk.xdr.TTLEntryXdr

fun structs() {
    val json = "{\"key_hash\":\"0102030405060708091011121314151617181920212223242526272829303132\"," +
        "\"live_until_ledger_seq\":1}"
    println(TTLEntryXdr.fromXdrJson(json).toXdrJson() == json) // true
}
```

Every key a struct declares must be present on input. An optional field may be `null`, but its key still has to appear.

### Unions

A union arm that carries no value is a bare string naming the arm. An arm that carries a value is an object with exactly one key.

```kotlin
import com.soneso.stellar.sdk.xdr.AssetXdr

fun unions() {
    println(AssetXdr.Void.toXdrJson()) // "native"

    val credit = "{\"credit_alphanum4\":{\"asset_code\":\"ABCD\"," +
        "\"issuer\":\"GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF\"}}"
    println(AssetXdr.fromXdrJson(credit).toXdrJson() == credit) // true
}
```

A union that switches on an integer rather than an enum keys its arms `v0`, `v1` and so on.

```kotlin
import com.soneso.stellar.sdk.xdr.SorobanTransactionMetaExtXdr

fun intCasedUnion() {
    println(SorobanTransactionMetaExtXdr.Void.toXdrJson()) // "v0"
}
```

### Optionals

An optional is `null` or the value itself.

```kotlin
import com.soneso.stellar.sdk.xdr.SponsorshipDescriptorXdr

fun optionals() {
    println(SponsorshipDescriptorXdr(null).toXdrJson()) // null
}
```

**The pitfall worth knowing.** An absent optional and a void union arm look similar in the XDR but not in the JSON: an absent optional is the JSON literal `null`, while a void union arm is a string naming the arm. In the envelope above, `"source_account": null` is an operation without a source account, and `"cond": "none"` is the `PRECOND_NONE` arm of a union. Spelling either as the other is rejected.

### Stellar-Specific Types

Types that already have a text form use it, rather than rendering their internal structure.

| Type | JSON form |
|---|---|
| `AccountID`, `PublicKey`, `NodeID` | `G…` strkey |
| `MuxedAccount` | `G…` or `M…` strkey, by arm |
| `MuxedAccountMed25519`, `MuxedEd25519Account` | `M…` strkey |
| `ContractID` | `C…` strkey |
| `PoolID` | `L…` strkey |
| `ClaimableBalanceID` | `B…` strkey |
| `SCAddress` | `G…`, `C…`, `M…`, `B…` or `L…`, by arm |
| `SignerKey` | `G…`, `T…`, `X…` or `P…`, by arm |
| `SignerKeyEd25519SignedPayload` | `P…` strkey |
| `AssetCode4` | trailing NUL bytes trimmed, then escaped |
| `AssetCode12` | trailing NUL bytes trimmed to no fewer than five, then escaped |
| `AssetCode` | rendered as its `AssetCode4` or `AssetCode12` arm |
| `Int128Parts`, `UInt128Parts`, `Int256Parts`, `UInt256Parts` | one base-10 decimal string |

```kotlin
import com.soneso.stellar.sdk.xdr.AssetCode12Xdr
import com.soneso.stellar.sdk.xdr.AssetCode4Xdr
import com.soneso.stellar.sdk.xdr.SCValXdr

fun stellarTypes() {
    // Padding is trimmed, so the code reads as the code
    println(AssetCode4Xdr.fromXdrJson("\"ABC\"").toXdrJson()) // "ABC"

    // An asset code of twelve keeps five characters, which is what distinguishes
    // it on the wire from an asset code of four
    println(AssetCode12Xdr.fromXdrJson("\"ABCDE\"").toXdrJson()) // "ABCDE"

    // A 128-bit integer is one decimal string, not a pair of limbs
    println(SCValXdr.fromXdrJson("{\"i128\":\"-42\"}").toXdrJson()) // {"i128":"-42"}
}
```

## Canonical Output

`toXdrJson()` emits one form and only that form: compact, with no inserted whitespace, and with object keys in XDR declaration order rather than sorted. Two equal values always produce byte-identical documents, which is what makes the output safe to hash, diff or use as a test fixture. There is no pretty-printing option; format the tree from `toXdrJsonElement()` if a document needs to be displayed.

That guarantee covers documents this SDK produced. SEP-0051 does not fix key order or whitespace, so another producer may render the same value with the keys in a different order, or with whitespace, and the two documents will differ byte for byte while meaning the same thing. The SDK ships no normaliser for foreign documents.

Compare across producers by decoding both and comparing the values, not the text:

```kotlin
import com.soneso.stellar.sdk.xdr.TimeBoundsXdr

fun compareAcrossProducers(ours: String, theirs: String) {
    // Structural comparison: whitespace and key order stop mattering
    val equal = TimeBoundsXdr.fromXdrJson(ours).toXdrJsonElement() ==
        TimeBoundsXdr.fromXdrJson(theirs).toXdrJsonElement()
    println(equal)
}
```

Comparing the decoded values themselves works too, since the generated types are data classes.

## When Not to Use XDR-JSON

XDR-JSON is for reading, logging and interchange. Keep the binary base64 form where size or verification matters: it is several times smaller on the wire, it is what Horizon and the Soroban RPC server accept for submission, and it is the form signatures and hashes are computed over, so it is what has to be stored when a value must stay cryptographically verifiable.

## Input Strictness

Decoding accepts only the spelling encoding produces. These rules are narrower than SEP-0051 requires, and they exist so that two different documents cannot decode to the same value:

- Hexadecimal must be lowercase and of even length.
- An `\xNN` escape must use lowercase hexadecimal digits.
- Only the escapes in the ladder above are recognised; any other escape, and a trailing backslash, are rejected.
- An integer must be a plain base-10 literal. `1.0`, `1e10`, `0x10` and a leading `+` are rejected, as is a value outside the range of its bit size.
- A struct object must carry only the keys the type declares. An unrecognised key is rejected, and the error quotes it alongside the type, so a misspelled field fails instead of quietly discarding the value it carried.
- An object must name each key once. A repeated key is rejected rather than resolved to one of its occurrences, since resolving it would discard the other silently. The rule covers one object at a time, so separate objects may share a key name, as every element of an array normally does. It applies to `$schema` as it does to any other key.

The repeated-key rule holds for `fromXdrJson`, which reads a document as text. `fromXdrJsonElement` takes a `JsonElement` you have already built, and a `JsonObject` is a map, so a repetition was resolved before the decoder saw it.

A document written by other SEP-0051 tooling that uses uppercase hexadecimal will therefore need normalising before it decodes here. Output is unaffected: this SDK emits the lowercase form the specification shows.

Two spellings are accepted for compatibility and never emitted: a 64-bit integer as a JSON number, and the key `type_` where a struct field named `type` is expected. The two spellings name one field: either one alone decodes, and a document carrying both is rejected.

A `$schema` property is accepted anywhere an object is read, ignored, and never emitted. No other undeclared property is accepted.

## Limitations

### Text Fidelity on String-Typed Members

An XDR `string` member is text: the SDK holds it as a Kotlin `String`, so the wire bytes are decoded as UTF-8 when the value is built. Wire bytes that are not valid UTF-8 have already become the Unicode replacement character by the time the JSON is produced, and the escape ladder is applied to the replacement rather than to the original byte. Round-tripping such a value through JSON therefore does not restore the original bytes — and neither does round-tripping it through binary XDR, since the loss happens at the type boundary rather than in the JSON layer.

Members that must preserve arbitrary bytes exactly are typed `ByteArray`, not `String`. Asset codes are the case that matters in practice: `AssetCode4` and `AssetCode12` hold bytes, so a code that is not valid UTF-8 renders and restores exactly.

### Zero-Length Signed Payloads

`SignerKeyEd25519SignedPayload` with an empty payload is valid XDR, and it is the one value of this type with no `P…` strkey. A strkey packs the 32-byte signer key, a four-byte length and the payload padded to a four-byte boundary, and the codec requires the result to be 40 to 100 bytes. Padding carries every payload length XDR admits over that floor — even a single byte pads to four, reaching exactly 40 — but an empty payload pads to nothing and leaves 36.

SEP-0051 renders this type as a strkey and specifies no alternative, so `toXdrJsonElement()` and `toXdrJson()` raise `IllegalArgumentException` for that one value rather than emitting a string no other tool would accept. Payloads of 1 to 64 bytes all render.

## Specification Conformance

Two renderings are worth stating explicitly, because tooling in the ecosystem is not uniform on them and SEP-0051 is.

**A standalone 64-bit integer is a string.** SEP-0051 "Hyper Integer (64-bit)" makes 64-bit integers JSON strings without qualification, so `Int64` and `Uint64` render as `"1"` whether they appear as a struct field or as the whole document. Some tooling emits a bare JSON number for the standalone case. Documents in either form decode here, since the number form is accepted on input.

**An inline fixed-length opaque field is a hexadecimal string.** SEP-0051 "Opaque Data (Fixed Length)" makes fixed-length opaque data a hexadecimal string, and the declaration it shows as its example is the inline form. Where the XDR declares such a field inline rather than through a named typedef, some tooling emits an array of byte numbers instead. This SDK emits the hexadecimal string in both cases, and the fields affected are `Curve25519Secret.key`, `Curve25519Public.key`, `HmacSha256Key.key`, `HmacSha256Mac.mac`, `ShortHashSeed.seed`, and `SerializedBinaryFuseFilter`, which embeds two of them. The array form is not accepted on input.

## Error Handling

Every malformed input raises `IllegalArgumentException`, on every platform. The message names the type and, where the offending value sits under a key, that key. Values quoted back are truncated and control bytes escaped, so an error message cannot be used to smuggle terminal control sequences into a log.

```kotlin
import com.soneso.stellar.sdk.xdr.AssetXdr
import com.soneso.stellar.sdk.xdr.TTLEntryXdr

fun handleErrors() {
    try {
        TTLEntryXdr.fromXdrJson("{\"key_hash\":\"" + "00".repeat(32) + "\"}")
    } catch (e: IllegalArgumentException) {
        println(e.message)
        // TTLEntryXdr: is missing the required key "live_until_ledger_seq"
    }

    try {
        AssetXdr.fromXdrJson("{\"gold\":{}}")
    } catch (e: IllegalArgumentException) {
        println(e.message)
        // AssetXdr: has no arm named "gold"
    }
}
```

The decoder rejects rather than repairs. A struct missing a key or carrying one that names no field, an object repeating a key, a union carrying two arms or none, an unknown enum member, a fixed-length field of the wrong size, an integer out of range and a document nested more than 128 levels deep all raise, and none of them produce a partially built value.

## API Reference

| Member | Parameters | Return | Throws | Description |
|--------|-----------|--------|--------|-------------|
| `toXdrJson()` | -- | `String` | `IllegalArgumentException` for a value with no rendering | Canonical XDR-JSON document for this value |
| `toXdrJsonElement()` | -- | `JsonElement` | `IllegalArgumentException` for a value with no rendering | The same document as a tree |
| `Companion.fromXdrJson(json: String)` | XDR-JSON document | the type | `IllegalArgumentException` on malformed input | Parse and decode a document |
| `Companion.fromXdrJsonElement(element: JsonElement)` | XDR-JSON tree | the type | `IllegalArgumentException` on malformed input | Decode an already-parsed tree |

All four members exist on every generated XDR type in `com.soneso.stellar.sdk.xdr`. None is a `suspend` function. `toXdrJson()` and `fromXdrJson(String)` are the string boundary and are named after the `toXdrBase64()` and `Companion.fromXdrBase64(String)` pair, which reads the same way but is an extension on fifteen commonly exchanged types rather than a member on all of them. `toXdrJsonElement()` and `fromXdrJsonElement(JsonElement)` are the tree boundary.

`toXdrJson` and `toXdrJsonElement` raise only for `SignerKeyEd25519SignedPayload` with an empty payload, and for any value containing one, such as the `SignerKey` signed-payload arm. Every other value has a rendering.

**Specification**: [SEP-51: XDR-JSON](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0051.md)

**Implementation**: `com.soneso.stellar.sdk.xdr`

**Coverage**: [SEP-0051 Compatibility Matrix](../../compatibility/sep/SEP-0051_COMPATIBILITY_MATRIX.md)

**Last Updated**: 2026-08-06
