# SEP-51: XDR-JSON

**Purpose:** Render any XDR value as canonical JSON and read that JSON back into the same value.
**Prerequisites:** None (no network, no authentication, no anchor)
**SDK Class:** every generated XDR type in `com.soneso.stellar.sdk.xdr`
**Specification:** SEP-0051 v2.0.1, Draft status

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [JSON Forms by XDR Category](#json-forms-by-xdr-category)
- [Worked Example: a Transaction Envelope](#worked-example-a-transaction-envelope)
- [Reading XDR Returned by the Network](#reading-xdr-returned-by-the-network)
- [Working with JsonElement](#working-with-jsonelement)
- [Canonical Output](#canonical-output)
- [Error Handling](#error-handling)
- [Common Pitfalls](#common-pitfalls)

## Overview

SEP-0051 defines a canonical JSON rendering of XDR. The SDK implements it as four members on every generated XDR type — there is no `sep/sep51` package and no service class to construct:

| Member | Kind | Returns | Throws |
|--------|------|---------|--------|
| `toXdrJson()` | instance | `String` — compact JSON, never pretty-printed | `IllegalArgumentException` for the rare value with no JSON form |
| `toXdrJsonElement()` | instance | `kotlinx.serialization.json.JsonElement` | same |
| `Companion.fromXdrJson(json: String)` | companion | an instance of the type | `IllegalArgumentException` on any malformed input |
| `Companion.fromXdrJsonElement(element: JsonElement)` | companion | an instance of the type | same |

None of the four are `suspend` functions. They pair with the existing `toXdrBase64()` / `Companion.fromXdrBase64()`, but unlike those — which are extension functions declared for a subset of types and need an import — the JSON members are declared on the type itself and need no import beyond the type.

`kotlinx-serialization-json` is an `api` dependency of the SDK, so `JsonElement` is on the consumer compile classpath without adding it to your build file.

These members are not listed in `api_reference.md`: its generator skips the `xdr` package. They exist on every generated type regardless.

## Quick Start

```kotlin
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import com.soneso.stellar.sdk.xdr.toXdrBase64

fun inspect(envelopeBase64: String) {
    // base64 XDR -> typed value -> canonical JSON
    val envelope = TransactionEnvelopeXdr.fromXdrBase64(envelopeBase64)
    val json: String = envelope.toXdrJson()
    println(json)

    // canonical JSON -> typed value -> base64 XDR
    val restored = TransactionEnvelopeXdr.fromXdrJson(json)
    check(restored.toXdrBase64() == envelopeBase64) // round-trips exactly
}
```

Any XDR type works the same way — `SCValXdr`, `LedgerKeyXdr`, `TransactionResultXdr`, `AssetXdr`, `MemoXdr`, `SorobanAuthorizationEntryXdr`, `DiagnosticEventXdr`, and every other generated type:

```kotlin
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCValXdr

fun values() {
    val value: SCValXdr = Scv.toSymbol("transfer")
    println(value.toXdrJson())                    // {"symbol":"transfer"}

    val back: SCValXdr = SCValXdr.fromXdrJson("""{"u64":"18446744073709551615"}""")
}
```

## JSON Forms by XDR Category

| XDR category | JSON form |
|--------------|-----------|
| `int`, `unsigned int` (32-bit) | JSON number. A string is rejected on input |
| `hyper`, `unsigned hyper` (64-bit) | Base-10 JSON **string**. A JSON number is also accepted on input; output is always the string |
| `bool` | JSON boolean |
| `opaque` (fixed and variable) | Lowercase hexadecimal string; empty opaque is `""` |
| `string` | JSON string put through the escape ladder (see below) |
| array (fixed and variable) | JSON array, always present; empty as `[]` |
| optional (`*`) | `null` or the value; the key stays present in the parent object |
| `enum` | Bare JSON string: the member name in snake_case with the enum's shared prefix removed |
| `struct` | JSON object keyed by the snake_case member names, in `.x` declaration order |
| `union`, void arm | Bare JSON string naming the arm |
| `union`, value arm | Object with exactly one key: `{"arm": value}` |
| `union` cased on an integer | `"v0"` for a void arm, `{"v1": value}` for a value arm |

Verified renderings, one per rule:

```jsonc
// 32-bit as a number, 64-bit as a base-10 string
{"u32":4294967295}
{"i32":-2147483648}
{"u64":"18446744073709551615"}
{"i64":"-9223372036854775808"}
{"timepoint":"1735689600"}

// 128-bit and 256-bit as one base-10 string, not a pair of limbs
{"u128":"340282366920938463463374607431768211455"}
{"i128":"-1"}
{"i256":"-57896044618658097711785492504343953926634992332820282019728792003956564819968"}

// bool, opaque, string, symbol
{"bool":true}
{"bytes":"00ff"}
{"string":""}
{"symbol":"transfer"}

// arrays: an unset optional array is null, an empty one is []
{"vec":null}
{"vec":[]}
{"vec":[{"u32":1},"void"]}
{"map":[{"key":{"symbol":"k"},"val":{"u32":1}}]}

// enum member (SCValType), void union arm (Asset), value union arm (Asset)
"u32"
"native"
{"credit_alphanum4":{"asset_code":"ABCD","issuer":"GAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAWHF"}}

// struct: declaration order, snake_case keys
{"min_time":"1","max_time":"18446744073709551615"}
{"key_hash":"0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20","live_until_ledger_seq":1}

// integer-cased union: void arm, then a value arm
"v0"
{"v3":{"ext":"v0","seq_ledger":7,"seq_time":"9"}}
```

### The String Escape Ladder

A `string` member is escaped byte by byte, first match winning:

| Byte | Rendered as |
|------|-------------|
| `0x00` | `\0` |
| `0x09` | `\t` |
| `0x0A` | `\n` |
| `0x0D` | `\r` |
| `0x5C` (backslash) | `\\` |
| `0x20`–`0x7E` | the character itself |
| anything else | `\xNN`, two lowercase hexadecimal digits |

The JSON encoder then escapes the resulting backslashes a second time, so a single tab byte reaches the wire as `\\t` inside the document and a single backslash byte as four characters:

```jsonc
// A memo holding the bytes "tab", 0x09, "here"
{"text":"tab\\there"}
```

Decoding reverses exactly this ladder. A JSON-style `\u0041`, an uppercase `\xC3`, a trailing backslash and any escape outside the table are rejected.

### Stellar-Specific Types

These types render as strkeys rather than as their structural XDR shape:

| XDR type | JSON form | Verified example |
|----------|-----------|------------------|
| `AccountID`, `PublicKey`, `NodeID` | `G...` | `"GAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQTCQKRMFYYDENBWHA5DYPSABOV"` |
| `MuxedAccount` | `G...` on the ed25519 arm, `M...` on the med25519 arm | `"MAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQTCQKRMFYYDENBWHA5DYPSAAAAAAAAAAAE2LAOE"` |
| `MuxedEd25519Account`, `MuxedAccountMed25519` | `M...` | as above |
| `ContractID` | `C...` | `"CAQCCIRDEQSSMJZIFEVCWLBNFYXTAMJSGM2DKNRXHA4TUOZ4HU7D7V6Z"` |
| `PoolID` | `L...` | `"LCAIDAUDQSCYNB4IRGFIXDENR2HZBEMSSOKJLFUXTCMZVG44TWPJ744M"` |
| `ClaimableBalanceID` | `B...` | `"BAAACAQDAQCQMBYIBEFAWDANBYHRAEISCMKBKFQXDAMRUGY4DUPB6IHRSE"` |
| `SignerKey` | `G...` / `T...` / `X...` / `P...` by arm | `"TBAECQSDIRCUMR2IJFFEWTCNJZHVAUKSKNKFKVSXLBMVUW24LVPF7EOJ"` |
| `SCAddress` | `G...`, `C...`, `M...`, `B...` or `L...` by arm | `"CAQCCIRDEQSSMJZIFEVCWLBNFYXTAMJSGM2DKNRXHA4TUOZ4HU7D7V6Z"` |
| `Int128Parts`, `UInt128Parts`, `Int256Parts`, `UInt256Parts` | one base-10 decimal string | `"340282366920938463463374607431768211455"` |
| `AssetCode4`, `AssetCode12` | the code with its trailing NUL padding trimmed | `"ABC"` |

An `AssetCode12` is never trimmed below five bytes, which is what keeps it distinguishable from an `AssetCode4`: the code `ABC` padded to twelve bytes renders as `"ABC\\0\\0"` in the document, and an all-NUL code renders as five escaped NUL bytes.

A strkey that fails its checksum, or one whose prefix does not match the arm being read, is rejected — a `C...` where an account key is expected raises rather than silently decoding.

## Worked Example: a Transaction Envelope

The value below is pinned in both wire forms by `Sep51RollbackRehearsalTest`. It exercises the rules that matter in practice: a `G` and an `M` strkey, a 32-bit fee as a number next to a 64-bit sequence number as a string, a value union arm (`cond`, `memo`, `asset`), a void union arm (`ext`), an unset optional (`source_account`), a trimmed asset code, and opaque data as hexadecimal.

Base64 XDR, wrapped here for reading — it is one unbroken string:

```
AAAAAgAAAAAREREREREREREREREREREREREREREREREREREREREREQAAAMgAAAADAAAAAQAAAAEA
AAAAAAAAAAAAAABkAAAAAAAAAQAAAAVoZWxsbwAAAAAAAAIAAAAAAAAAAQAAAAAiIiIiIiIiIiIi
IiIiIiIiIiIiIiIiIiIiIiIiIiIiIgAAAAFVU0QAAAAAADMzMzMzMzMzMzMzMzMzMzMzMzMzMzMz
MzMzMzMzMzMzAAAAAACYln8AAAABAAABAAAAAAAAAAAHRERERERERERERERERERERERERERERERE
REREREREREQAAAAKAAAABG5hbWUAAAABAAAAAwECAwAAAAAAAAAAAaq7zN0AAAAEAQIDBA==
```

`toXdrJson()` emits this on a single line; it is indented here for reading only:

```json
{
  "tx": {
    "tx": {
      "source_account": "GAIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCF6M",
      "fee": 200,
      "seq_num": "12884901889",
      "cond": {
        "time": {
          "min_time": "0",
          "max_time": "1677721600"
        }
      },
      "memo": {
        "text": "hello"
      },
      "operations": [
        {
          "source_account": null,
          "body": {
            "payment": {
              "destination": "GARCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCFRVX",
              "asset": {
                "credit_alphanum4": {
                  "asset_code": "USD",
                  "issuer": "GAZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTGMZTHCM6"
                }
              },
              "amount": "9999999"
            }
          }
        },
        {
          "source_account": "MBCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIRCEIAAAAAAAAAAAA4WDM",
          "body": {
            "manage_data": {
              "data_name": "name",
              "data_value": "010203"
            }
          }
        }
      ],
      "ext": "v0"
    },
    "signatures": [
      {
        "hint": "aabbccdd",
        "signature": "01020304"
      }
    ]
  }
}
```

`"amount": "9999999"` is the raw XDR `int64` — stroops, not the decimal string Horizon reports. `"data_value": "010203"` is variable-length opaque as hexadecimal, while `"data_name": "name"` is a `string` and goes through the escape ladder.

Get here from a built transaction without a base64 detour:

```kotlin
// transaction is a Transaction or FeeBumpTransaction built and signed as usual
val json: String = transaction.toEnvelopeXdr().toXdrJson()
```

## Reading XDR Returned by the Network

A rejected submission does not come back as a response with `successful == false`:
`submitTransaction` raises `BadRequestException`, and the base64 result sits in the
Horizon error body under `extras.result_xdr`.

```kotlin
import com.soneso.stellar.sdk.Transaction
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.exceptions.BadRequestException
import com.soneso.stellar.sdk.xdr.TransactionResultXdr
import com.soneso.stellar.sdk.xdr.fromXdrBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

suspend fun submitAndExplain(server: HorizonServer, transaction: Transaction) {
    try {
        server.submitTransaction(transaction.toEnvelopeXdrBase64())
    } catch (e: BadRequestException) {
        val body = e.body ?: throw e
        val resultXdr = Json.parseToJsonElement(body)
            .jsonObject["extras"]?.jsonObject?.get("result_xdr")?.jsonPrimitive?.content
            ?: throw e

        println(TransactionResultXdr.fromXdrBase64(resultXdr).toXdrJson())
        // {"fee_charged":"100","result":{"tx_failed":[{"op_inner":{"payment":"malformed"}}]},"ext":"v0"}
    }
}
```

`submitTransactionAsync` is the other route: it returns a `SubmitTransactionAsyncResponse`
carrying the submission status without raising, and the transaction is polled for
separately.

Shapes worth recognising when reading result and diagnostic XDR:

```jsonc
// A successful payment
{"fee_charged":"100","result":{"tx_success":[{"op_inner":{"payment":"success"}}]},"ext":"v0"}

// A transaction-level failure carries no operation results
{"fee_charged":"0","result":{"tx_failed":[]},"ext":"v0"}

// Operation results: an operation-level code is a bare string, an inner result a single-key object
"op_bad_auth"
{"op_inner":{"create_account":"success"}}
{"op_inner":{"manage_sell_offer":{"success":{"offers_claimed":[],"offer":"deleted"}}}}

// Soroban events
{"ext":"v0","contract_id":"CAQCCIRDEQSSMJZIFEVCWLBNFYXTAMJSGM2DKNRXHA4TUOZ4HU7D7V6Z","type":"contract","body":{"v0":{"topics":[{"symbol":"transfer"}],"data":{"u32":1}}}}
{"in_successful_contract_call":true,"event":{"ext":"v0","contract_id":null,"type":"diagnostic","body":{"v0":{"topics":[],"data":"void"}}}}

// A Soroban authorization entry, source-account credentials and a nested invocation
{"credentials":"source_account","root_invocation":{"function":{"contract_fn":{"contract_address":"CAQCCIRDEQSSMJZIFEVCWLBNFYXTAMJSGM2DKNRXHA4TUOZ4HU7D7V6Z","function_name":"outer","args":[]}},"sub_invocations":[{"function":{"contract_fn":{"contract_address":"CAQCCIRDEQSSMJZIFEVCWLBNFYXTAMJSGM2DKNRXHA4TUOZ4HU7D7V6Z","function_name":"inner","args":[]}},"sub_invocations":[]}]}}

// Ledger keys
{"account":{"account_id":"GAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQTCQKRMFYYDENBWHA5DYPSABOV"}}
{"contract_data":{"contract":"CAQCCIRDEQSSMJZIFEVCWLBNFYXTAMJSGM2DKNRXHA4TUOZ4HU7D7V6Z","key":"void","durability":"persistent"}}
{"ttl":{"key_hash":"0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"}}
```

## Working with JsonElement

`toXdrJsonElement()` and `fromXdrJsonElement()` avoid a string round trip when the XDR value is one part of a larger document, or when you want a different serialization setting than the canonical compact one.

### Pretty-print for a log or a diff

```kotlin
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

fun logEnvelope(envelope: TransactionEnvelopeXdr) {
    val pretty = Json { prettyPrint = true }
    println(pretty.encodeToString(JsonElement.serializer(), envelope.toXdrJsonElement()))
}
```

### Embed in a larger document

```kotlin
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import com.soneso.stellar.sdk.xdr.TransactionResultXdr
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

fun record(
    transactionHash: String,
    envelope: TransactionEnvelopeXdr,
    result: TransactionResultXdr
): JsonObject = buildJsonObject {
    put("hash", JsonPrimitive(transactionHash))
    put("envelope", envelope.toXdrJsonElement())
    put("result", result.toXdrJsonElement())
}
```

### Read back out of a larger document

```kotlin
import com.soneso.stellar.sdk.xdr.TransactionEnvelopeXdr
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

fun envelopeOf(recordText: String): TransactionEnvelopeXdr {
    val parsed = Json.parseToJsonElement(recordText).jsonObject
    return TransactionEnvelopeXdr.fromXdrJsonElement(parsed["envelope"]!!)
}
```

## Canonical Output

`toXdrJson()` is deterministic: compact (no insignificant whitespace), object keys in XDR declaration order, hexadecimal lowercase, 64-bit integers always in their string form. The same value always produces byte-identical text, so the output can be hashed, diffed or used as a cache key.

Decoding is deliberately stricter than encoding is lax: the SDK accepts the spelling it emits, plus the compatibility spellings noted below (a 64-bit integer as a JSON number, a `$schema` property, and the `type_` key). Everything else is rejected:

- uppercase hexadecimal, and hexadecimal of odd length
- uppercase `\xNN` escapes, unrecognised escapes such as a JSON-style `\u0041`, a trailing backslash, and any unescaped byte outside `0x20`–`0x7E`
- integer literals with a leading `+`, a leading zero, a negative zero, a decimal point, an exponent or a hexadecimal prefix
- a 32-bit integer given as a string
- a value outside the range of its declared bit width
- a struct missing any declared key, or holding `null` where the member is not optional
- a union object carrying zero or more than one arm, an arm name the union does not declare, or an enum member name the enum does not declare (including the right name in the wrong case)
- an array longer than its declared maximum, or opaque data longer than its declared maximum
- a document nesting more than 128 containers deep

A `$schema` property is accepted anywhere an object is read, ignored, and never emitted, so a document annotated with its schema URL decodes unchanged. A `$schema` property on its own does not identify a value: a union still needs an arm.

Where a struct member is spelled `type` in XDR, the key is `type`; the historical spelling `type_` is still accepted on input and never emitted.

## Error Handling

Every failure is an `IllegalArgumentException` whose message names the type and, where the value sits under a key, that key.

```kotlin
import com.soneso.stellar.sdk.xdr.AssetTypeXdr
import com.soneso.stellar.sdk.xdr.AssetXdr
import com.soneso.stellar.sdk.xdr.TTLEntryXdr

fun errors() {
    try {
        TTLEntryXdr.fromXdrJson(
            """{"key_hash":"0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"}"""
        )
    } catch (e: IllegalArgumentException) {
        println(e.message)
        // TTLEntryXdr: is missing the required key "live_until_ledger_seq"
    }

    try {
        AssetXdr.fromXdrJson("""{"gold":{}}""")
    } catch (e: IllegalArgumentException) {
        println(e.message)
        // AssetXdr: has no arm named "gold"
    }

    try {
        AssetTypeXdr.fromXdrJson("\"gold\"")
    } catch (e: IllegalArgumentException) {
        println(e.message)
        // AssetTypeXdr: has no member named "gold"
    }
}
```

Text that is not JSON at all fails the same way — `fromXdrJson()` never throws a serialization exception, so one `catch (e: IllegalArgumentException)` covers parsing and mapping alike. Untrusted values quoted back in a message are truncated and have their control bytes escaped, so a hostile document cannot inject line breaks into a log.

Encoding raises only where XDR admits a value that has no SEP-0051 form: a `SignerKey` ed25519 signed payload with an empty payload has no P-strkey, so `toXdrJsonElement()` raises rather than emitting a strkey no decoder would read back.

## Common Pitfalls

```kotlin
// WRONG: expecting an unset optional to disappear, or to be a bare string
// {"body":{"payment":{...}}}
// CORRECT: the key stays present with a null value
// {"source_account":null,"body":{"payment":{...}}}
//
// A bare string in that position means something else entirely — a void union arm:
// {"source_account":null,"body":"inflation"}
```

```kotlin
// WRONG: expecting a 64-bit integer to come back as a JSON number
// The number form is accepted on input, but the string form is what is always emitted:
TimeBoundsXdr.fromXdrJson("""{"min_time":0,"max_time":0}""").toXdrJson()
// {"min_time":"0","max_time":"0"}
// So a consumer matching on the SDK's output must expect the string form.

// And the converse for 32 bits: a number is required and a string raises.
// {"fee":200} decodes; {"fee":"200"} raises
```

```kotlin
// WRONG: uppercase hexadecimal
HashXdr.fromXdrJson("\"0102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F20\"")
// CORRECT: lowercase only
HashXdr.fromXdrJson("\"0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20\"")
```

```kotlin
// WRONG: expecting the full XDR enum identifier
SCValTypeXdr.fromXdrJson("\"SCV_U32\"")   // raises
// CORRECT: snake_case with the enum's shared prefix removed
SCValTypeXdr.fromXdrJson("\"u32\"")
// Exceptions to the stripping: an enum with a single member keeps its whole name
// ("public_key_type_ed25519"), and a name that would start with a digit keeps enough
// of the prefix to avoid one ("b8_bit", not "8_bit").
```

```kotlin
// WRONG: assuming toXdrJson() indents, or that the SDK exposes a pretty-printer
val pretty = value.toXdrJson() // always compact, single line
// CORRECT: use your own Json instance on the element form
val indented = Json { prettyPrint = true }
    .encodeToString(JsonElement.serializer(), value.toXdrJsonElement())
```

```kotlin
// WRONG: reaching for the shared runtime — XdrJson is internal to the SDK
XdrJson.encodeToString(element) // not visible to consumers
// CORRECT: the four members on the type are the whole public surface
value.toXdrJson()
```

```kotlin
// WRONG: submitting the JSON form to the network
server.submitTransaction(transaction.toEnvelopeXdr().toXdrJson())
// CORRECT: the wire format stays base64 XDR; JSON is for inspection, storage and interchange
server.submitTransaction(transaction.toEnvelopeXdrBase64())
```

```kotlin
// WRONG: handing a type the document of a different type. The object below is how a field
// of type Asset renders inside its parent, not how Asset itself renders.
val wrong = AssetXdr.fromXdrJson("""{"asset":"native"}""")   // raises: no arm named "asset"
// CORRECT: fromXdrJson takes the document of that type alone
val asset = AssetXdr.fromXdrJson("\"native\"")
```
