# SEP-0023 (Strkeys) Compatibility Matrix

**Generated:** 2026-09-30 13:56:47

**SEP Version:** 1.3.0  
**SEP Status:** Active  
**SDK Version:** 1.14.0  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0023.md

## SEP Summary

Strkey is an ASCII format for representing Stellar account IDs and addresses.

## Overall Coverage

**Total Coverage:** 100.0% (32/32 fields)

- ✅ **Implemented:** 32/32
- ❌ **Not Implemented:** 0/32

**Required Fields:** 100.0% (32/32)

**Optional Fields:** 100% (0/0)

## Implementation Status

✅ **Fully Implemented**

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| Key types | 100.0% | 9/9 | 9 | 9 |
| Test vectors quoted in the StrKey unit test files | 100.0% | 23/23 | 23 | 23 |

## Detailed Field Comparison

### Key types

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `STRKEY_PUBKEY` | ✓ | ✅ | `StrKey.encodeEd25519PublicKey / decodeEd25519PublicKey` | Version byte base value 6 << 3 = 48, first character G |
| `STRKEY_MUXED` | ✓ | ✅ | `StrKey.encodeMed25519PublicKey / decodeMed25519PublicKey` | Version byte base value 12 << 3 = 96, first character M |
| `STRKEY_PRIVKEY` | ✓ | ✅ | `StrKey.encodeEd25519SecretSeed / decodeEd25519SecretSeed` | Version byte base value 18 << 3 = 144, first character S |
| `STRKEY_PRE_AUTH_TX` | ✓ | ✅ | `StrKey.encodePreAuthTx / decodePreAuthTx` | Version byte base value 19 << 3 = 152, first character T |
| `STRKEY_HASH_X` | ✓ | ✅ | `StrKey.encodeSha256Hash / decodeSha256Hash` | Version byte base value 23 << 3 = 184, first character X |
| `STRKEY_SIGNED_PAYLOAD` | ✓ | ✅ | `StrKey.encodeSignedPayload / decodeSignedPayload` | Version byte base value 15 << 3 = 120, first character P |
| `STRKEY_CONTRACT` | ✓ | ✅ | `StrKey.encodeContract / decodeContract` | Version byte base value 2 << 3 = 16, first character C |
| `STRKEY_LIQUIDITY_POOL` | ✓ | ✅ | `StrKey.encodeLiquidityPool / decodeLiquidityPool` | Version byte base value 11 << 3 = 88, first character L |
| `STRKEY_CLAIMABLE_BALANCE` | ✓ | ✅ | `StrKey.encodeClaimableBalance / decodeClaimableBalance` | Version byte base value 1 << 3 = 8, first character B |

### Test vectors quoted in the StrKey unit test files

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `valid_01` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Valid non-multiplexed account |
| `valid_02` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Valid multiplexed account |
| `valid_03` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Valid multiplexed account in which unsigned id exceeds maxim... |
| `valid_04` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Valid signed payload with an ed25519 public key and a 32-byt... |
| `valid_05` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Valid signed payload with an ed25519 public key and a 29-byt... |
| `valid_06` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Valid contract |
| `valid_07` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Valid liquidity pool address |
| `valid_08` | ✓ | ✅ | `unitTests/ClaimableBalanceVectors.kt` | quoted in `unitTests/ClaimableBalanceVectors.kt`: Valid claimable balance address |
| `invalid_01` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Invalid length (Ed25519 should be 32 bytes, not 5) |
| `invalid_02` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: The unused trailing bit must be zero in the encoding of the ... |
| `invalid_03` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Invalid length (congruent to 1 mod 8) |
| `invalid_04` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Invalid length (base-32 decoding should yield 35 bytes, not 36) |
| `invalid_05` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Invalid algorithm (low 3 bits of version byte are 7) |
| `invalid_06` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Invalid length (congruent to 6 mod 8) |
| `invalid_07` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Invalid length (base-32 decoding should yield 43 bytes, not 44) |
| `invalid_08` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Invalid algorithm (low 3 bits of version byte are 7) |
| `invalid_09` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Padding bytes are not allowed |
| `invalid_10` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Invalid checksum |
| `invalid_11` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Length prefix specifies length that is shorter than payload ... |
| `invalid_12` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Length prefix specifies length that is longer than payload i... |
| `invalid_13` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: No zero padding in signed payload |
| `invalid_14` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: The unused trailing 2-bits must be zero in the encoding of t... |
| `invalid_15` | ✓ | ✅ | `unitTests/StrKeyTest.kt` | quoted in `unitTests/StrKeyTest.kt`: Invalid claimable balance type (first byte of binary key is ... |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0023!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep/README.md` for usage examples and API reference

**Specification:** [SEP-0023](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0023.md)

**Implementation Package:** `com.soneso.stellar.sdk.StrKey`
