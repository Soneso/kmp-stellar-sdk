# Recorded reference renderings

Renderings the SEP-0051 text alone leaves ambiguous, taken from the pinned reference CLI so
the SDK's expected values have a recorded source. Reproduce any row with `probe.sh`.

Reference build: `stellar-xdr` 28.0.0 (`d0f1330e43c3a2c0c616da30698b24140d4eea72`), vendored
xdr `9c9c145953e80990d6ff1ae3a6a973a0ce6d0694`. See `oracle-pin.json`.

Two columns are needed because escaping applies twice. **Escaped text** is the string value
after JSON decoding, which is what the escape ladder produces and what the SDK builds. **Raw
output** is the bytes the CLI prints, where the JSON encoder has escaped every backslash a
second time. A single input backslash is therefore one `\` in the value, two in the escaped
text, and four in the raw output.

## String escaping

| Type | Input bytes | Escaped text | Raw output |
|---|---|---|---|
| `AssetCode4` | `41 c3 43 00` | `A\xc3C` | `"A\\xc3C"` |
| `AssetCode4` | `41 c3 42 00` | `A\xc3B` | `"A\\xc3B"` |
| `AssetCode4` | `c3 c4 c5 c6` | `\xc3\xc4\xc5\xc6` | `"\\xc3\\xc4\\xc5\\xc6"` |
| `String32` | `5c 00` | `\\\0` | `"\\\\\\0"` |
| `String32` | `61 5c 00 62 09 0a 0d` | `a\\\0b\t\n\r` | `"a\\\\\\0b\\t\\n\\r"` |
| `String32` | `7f 80` | `\x7f\x80` | `"\\x7f\\x80"` |

What these settle:

- A non-ASCII byte inside `AssetCode4` is escaped like any other non-printable byte, with
  lowercase hexadecimal, and trailing NUL bytes are trimmed before escaping.
- A backslash and a NUL adjacent to each other stay distinguishable: `\\` then `\0`, never a
  single ambiguous run.
- `0x7f` is escaped, so the printable range kept verbatim ends at `0x7e` inclusive.

## Asset codes and opaque data

| Type | Input bytes | Escaped text | Note |
|---|---|---|---|
| `AssetCode12` | `41 42 43` + nine NUL | `ABC\0\0` | trimmed, then padded back to five bytes |
| `AssetCode12` | twelve NUL | `\0\0\0\0\0` | accepted on input, round-trips to twelve NUL |
| `DataValue` | empty | (empty string) | `""`, never `"0"` |

## Wide integers

Rendered as one base-10 decimal string of the reassembled value; the most significant limb is
signed and the rest are unsigned.

| Type | Limbs | Rendering |
|---|---|---|
| `Int128Parts` | hi `-1`, lo `1` | `-18446744073709551615` |
| `Int128Parts` | minimum | `-170141183460469231731687303715884105728` |
| `Int128Parts` | maximum | `170141183460469231731687303715884105727` |
| `Int256Parts` | minimum | `-57896044618658097711785492504343953926634992332820282019728792003956564819968` |
| `Int256Parts` | maximum | `57896044618658097711785492504343953926634992332820282019728792003956564819967` |
| `UInt256Parts` | maximum | `115792089237316195423570985008687907853269984665640564039457584007913129639935` |

## Unions

| Type | Input | Rendering |
|---|---|---|
| `ExtensionPoint` | discriminant 0 | `"v0"` |

## Reproducing

```bash
# AssetCode4 from raw bytes
./probe.sh AssetCode4 "$(python3 -c 'import base64;print(base64.b64encode(bytes.fromhex("41c34300")).decode())')"

# String32 carries a four-byte length prefix and is padded to a four-byte boundary,
# so the two content bytes 5c 00 are encoded as 00000002 5c00 0000
./probe.sh String32 "$(python3 -c 'import base64;print(base64.b64encode(bytes.fromhex("000000025c000000")).decode())')"
```
