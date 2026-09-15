# Addresses and StrKey

Stellar writes keys and identifiers as strkeys: base32 strings whose first letter names their type, defined in [SEP-0023](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0023.md). Account ids, secret seeds, contract ids, muxed accounts, claimable balance ids, liquidity pool ids and signer keys all travel in this form. This page states the rules the SDK codec applies: which strings it accepts, in what order it checks them, and how the higher-level address types build on it.

The codec is the `StrKey` object. It is the single gate: every SDK type that reads or writes a strkey — `KeyPair`, `Address`, `MuxedAccount`, `SignerKey`, `ClaimableBalanceId`, the XDR-JSON layer — goes through it, so one string is accepted or rejected identically everywhere, on every platform.

## The Nine Types

| Prefix | Names | Payload bytes | Characters | `StrKey` functions |
|---|---|---|---|---|
| `G` | ed25519 public key (account id) | 32 | 56 | `encodeEd25519PublicKey` / `decodeEd25519PublicKey` |
| `S` | ed25519 secret seed | 32 | 56 | `encodeEd25519SecretSeed` / `decodeEd25519SecretSeed` |
| `M` | muxed ed25519 public key | 40 | 69 | `encodeMed25519PublicKey` / `decodeMed25519PublicKey` |
| `C` | contract id | 32 | 56 | `encodeContract` / `decodeContract` |
| `L` | liquidity pool id | 32 | 56 | `encodeLiquidityPool` / `decodeLiquidityPool` |
| `B` | claimable balance id | 33 | 58 | `encodeClaimableBalance` / `decodeClaimableBalance` |
| `T` | pre-authorized transaction hash | 32 | 56 | `encodePreAuthTx` / `decodePreAuthTx` |
| `X` | SHA-256 hash (Hash-X) | 32 | 56 | `encodeSha256Hash` / `decodeSha256Hash` |
| `P` | ed25519 signed payload | 40 to 100 | 69 to 165 | `encodeSignedPayload` / `decodeSignedPayload` |

Every type but the signed payload has one character count; a signed payload's count follows from the payload length it declares. Each `decodeX` has an `isValidX` companion that reports whether the decode accepts the string — it returns `false` wherever the decode throws, and never throws itself.

## How a StrKey Is Built

A strkey encodes one version byte naming the type, the payload, and a two-byte CRC16-XModem checksum over both, in base32 — the alphabet `A`–`Z` and `2`–`7` — without padding. Every character is load-bearing:

- **Upper case only.** Lower-case letters are not in the alphabet, so a lower-cased strkey is rejected, as are the `=` pad character, whitespace and any character outside the ASCII range.
- **One spelling per key.** The unused trailing bits of the last character must be zero, so each key has exactly one strkey and two different strings never decode to the same key.
- **The type is part of the string.** The version byte fixes the first letter, and each decode function requires the type it is named for: an `S...` seed handed to `decodeEd25519PublicKey` is rejected, not read as an account id.

## The Decode Contract

Every `decodeX` runs the same checks in the same order and reports the first one the string fails as an `IllegalArgumentException`:

1. the character count is one the requested type has;
2. every character is inside the ASCII range;
3. the character count leaves no partially filled trailing character;
4. every character is one of the 32 in the base32 alphabet;
5. the unused trailing bits of the last character are zero;
6. the version byte names a strkey type;
7. the decoded payload has a size that type admits;
8. the framing that type defines inside its payload holds (see below);
9. the version byte names the type the caller asked for;
10. the checksum matches the payload it covers.

Checks 2 and 4 answer one question — whether the string is written in the characters a strkey is written in — and report it in the same words. Check 8 has nothing to read for a type whose payload is an opaque key of a fixed size; only the muxed key, the claimable balance id and the signed payload frame structure inside their payloads:

- **Muxed key (`M...`)** — 40 bytes: the 32-byte ed25519 public key followed by the multiplexing id as an eight-byte big-endian value.
- **Claimable balance id (`B...`)** — 33 bytes: a one-byte type discriminant followed by the 32-byte balance hash. The XDR union declares a single case, `V0` with discriminant `0`, so that is the one value the decode accepts. `encodeClaimableBalance` also takes the 32-byte hash alone or the 36-byte XDR wire form (the four-byte big-endian union discriminant followed by the hash) and writes all three as the same strkey.
- **Signed payload (`P...`)** — 40 to 100 bytes: the 32-byte ed25519 public key, the declared payload length as a four-byte big-endian value, and the payload padded with zeros to a four-byte boundary. The declared length must be 1 to 64, the total size must fit it exactly, and every padding byte must be zero. `encodeSignedPayload` checks the same framing, so every string it returns is one the decode accepts.

```kotlin
import com.soneso.stellar.sdk.StrKey

fun validation() {
    println(StrKey.isValidEd25519PublicKey("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D")) // true

    // A seed is not an account id: the version byte names another type
    println(StrKey.isValidEd25519PublicKey("SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE")) // false

    // Lower case is outside the alphabet
    println(StrKey.isValidContract("ca2lvqxqlgpwhv2qo5envagwm2tyicrmwxw4uxbpvkv26wlku2v3uth5")) // false
}
```

Secret seeds move as `CharArray`, not `String`: `decodeEd25519SecretSeed` takes one and `encodeEd25519SecretSeed` returns one, so a seed can be zeroed after use instead of lingering in immutable strings. `KeyPair.getSecretSeed()` returns the same form.

## The Address Types

The codec hands back raw bytes. Five types sit on top of it and are the usual way to work with addresses:

| Type | Reads | Purpose |
|---|---|---|
| `KeyPair` | `G...`, `S...` | Signing and verification; `fromAccountId`, `fromSecretSeed`, `getAccountId` |
| `Address` | `G...`, `C...`, `M...`, `B...`, `L...` | The five kinds an `SCAddress` names, for Soroban |
| `MuxedAccount` | `G...`, `M...` | A payment destination with an optional multiplexing id |
| `SignerKey` | `G...`, `T...`, `X...`, `P...` | The four signer forms account options accept |
| `ClaimableBalanceId` | `B...` and hex spellings | One balance across every spelling it is written in |

### Address

`Address` accepts any of the five strkey kinds an `SCAddress` can name and reports which one it got. It converts to and from the XDR forms Soroban contracts consume:

```kotlin
import com.soneso.stellar.sdk.Address

fun addresses() {
    val account = Address("GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D")
    println(account.addressType) // ACCOUNT

    val contract = Address("CA2LVQXQLGPWHV2QO5ENVAGWM2TYICRMWXW4UXBPVKV26WLKU2V3UTH5")
    println(contract.addressType) // CONTRACT

    // To the XDR forms contract calls take, and back
    val scVal = contract.toSCVal()
    println(Address.fromSCVal(scVal)) // CA2LVQXQLGPWHV2QO5ENVAGWM2TYICRMWXW4UXBPVKV26WLKU2V3UTH5
}
```

`getBytes()` returns the decoded payload as the table above defines it — for a claimable balance address that is the 33-byte body, discriminant included. The byte factories `fromAccount`, `fromContract`, `fromMuxedAccount`, `fromClaimableBalance` and `fromLiquidityPool` take the same widths the codec's encode functions take, so `fromClaimableBalance` also reads the bare 32-byte hash and the 36-byte XDR wire form.

### MuxedAccount

A muxed account is an ed25519 account with an optional 64-bit multiplexing id. The `G...` address and the id are the two halves; the `M...` address is the pair written as one string:

```kotlin
import com.soneso.stellar.sdk.MuxedAccount

fun muxedAccounts() {
    val muxed = MuxedAccount("MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUQ")
    println(muxed.accountId) // GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ
    println(muxed.id)        // 0

    // The address writes the id back when one is set
    println(MuxedAccount(muxed.accountId, 123456789UL).address.first()) // M
    println(MuxedAccount(muxed.accountId).address.first())              // G
}
```

Zero is a multiplexing id like any other: an account carrying the id `0` is written as an `M...` address, and only an account with no id at all is written as the `G...` address.

### SignerKey

`SignerKey.fromEncodedSignerKey` reads any of the four signer forms and returns the matching subtype — `Ed25519PublicKey`, `PreAuthTx`, `HashX` or `Ed25519SignedPayload`. It is the inverse of `encodeSignerKey()` on each. A malformed string of any form is reported as an `IllegalArgumentException`; a signed payload is checked against the framing it declares before any byte that framing names is read.

### ClaimableBalanceId

The same balance reaches an application under four names: the `B...` strkey, the bare 64-character hash in hexadecimal, and that hash behind a type discriminant of one byte (66 characters) or four bytes (72 characters, the spelling Horizon reports). `ClaimableBalanceId.forId` reads all of them and reports the one balance they name:

```kotlin
import com.soneso.stellar.sdk.ClaimableBalanceId

fun claimableBalances() {
    val fromStrKey = ClaimableBalanceId.forId("BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU")
    val fromHorizon = ClaimableBalanceId.forId("000000003f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a")
    println(fromStrKey == fromHorizon) // true

    println(fromStrKey.hashHex)      // 3f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a
    println(fromStrKey.toPaddedHex().length) // 72, the spelling Horizon reports
    println(fromStrKey.toStrKey())   // BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU
}
```

Hexadecimal is case insensitive on the way in; `hashHex` is the canonical lower-case spelling, so one balance is reported under one string whichever case it arrived in. A discriminant other than the one the XDR union declares is rejected in every width.

## Validating Input

Constructors and factories that take encoded values verify their input and throw `IllegalArgumentException` for anything malformed. Call the API directly when the value is fixed and known good; where a value arrives at runtime — user input, a request parameter — either wrap the call in `try`/`catch` or ask first with the matching `isValidX`:

```kotlin
import com.soneso.stellar.sdk.StrKey

fun guarded(input: String) {
    if (StrKey.isValidEd25519PublicKey(input)) {
        // input is a strkey decodeEd25519PublicKey accepts
    }
}
```

Because `isValidX` is defined as "the matching decode accepts the string", the guard and the decode can never disagree.

## See Also

- [SEP-0023: Strkeys](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0023.md) — the format specification
- [SEP-51: XDR-JSON](sep/sep-51.md) — where strkeys appear in the JSON rendering of XDR values
- [Getting Started](getting-started.md) — key generation and account basics
