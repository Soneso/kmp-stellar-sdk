# SEP-0005 (Key Derivation Methods for Stellar Keys) Compatibility Matrix

**Generated:** 2026-06-13 07:59:01

**SEP Version:** N/A  
**SEP Status:** Final  
**SDK Version:** 1.7.1  
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0005.md

## SEP Summary

Defines methods for deriving Stellar keypairs from mnemonic phrases, making it easier to back up and move keys between wallets.

## Overall Coverage

**Total Coverage:** 100.0% (31/31 fields)

- ✅ **Implemented:** 31/31
- ❌ **Not Implemented:** 0/31

**Required Fields:** 100.0% (22/22)

**Optional Fields:** 100.0% (9/9)

## Implementation Status

✅ **Fully Implemented**

## Coverage by Section

| Section | Coverage | Required | Implemented | Total |
|---------|----------|----------|-------------|-------|
| BIP-39 Seed Derivation | 100.0% | 2/2 | 2 | 2 |
| Key Export | 100.0% | 4/4 | 4 | 4 |
| Language Support | 100.0% | 1/1 | 9 | 9 |
| Mnemonic Generation | 100.0% | 5/5 | 5 | 5 |
| Mnemonic Validation | 100.0% | 1/1 | 2 | 2 |
| SLIP-0010 Key Derivation | 100.0% | 4/4 | 4 | 4 |
| Test Vectors | 100.0% | 5/5 | 5 | 5 |

## Detailed Field Comparison

### BIP-39 Seed Derivation

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `mnemonic_to_seed` | ✓ | ✅ | `from` | Convert mnemonic to 64-byte BIP-39 seed using PBKDF2-HMAC-SHA512 with 2048 iterations |
| `passphrase_support` | ✓ | ✅ | `from` | Support optional passphrase for additional security in seed derivation |

### Key Export

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `get_keypair` | ✓ | ✅ | `getKeyPair` | Get full Stellar KeyPair (public and private key) at specified derivation index |
| `get_account_id` | ✓ | ✅ | `getKeyPair` | Get Stellar account ID (G... address) at specified derivation index |
| `get_public_key` | ✓ | ✅ | `getKeyPair` | Get raw 32-byte Ed25519 public key at specified derivation index |
| `get_private_key` | ✓ | ✅ | `getKeyPair` | Get raw 32-byte Ed25519 private key at specified derivation index |

### Language Support

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `english` | ✓ | ✅ | `ENGLISH` | English BIP-39 word list (2048 words) |
| `japanese` |  | ✅ | `JAPANESE` | Japanese BIP-39 word list (2048 words) |
| `korean` |  | ✅ | `KOREAN` | Korean BIP-39 word list (2048 words) |
| `spanish` |  | ✅ | `SPANISH` | Spanish BIP-39 word list (2048 words) |
| `chinese_simplified` |  | ✅ | `CHINESE_SIMPLIFIED` | Simplified Chinese BIP-39 word list (2048 words) |
| `chinese_traditional` |  | ✅ | `CHINESE_TRADITIONAL` | Traditional Chinese BIP-39 word list (2048 words) |
| `french` |  | ✅ | `FRENCH` | French BIP-39 word list (2048 words) |
| `italian` |  | ✅ | `ITALIAN` | Italian BIP-39 word list (2048 words) |
| `malay` |  | ✅ | `MALAY` | Malay BIP-39 word list (2048 words) |

### Mnemonic Generation

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `generate_12_word_mnemonic` | ✓ | ✅ | `generate12WordsMnemonic` | Generate 12-word mnemonic from 128 bits of entropy |
| `generate_15_word_mnemonic` | ✓ | ✅ | `generate15WordsMnemonic` | Generate 15-word mnemonic from 160 bits of entropy |
| `generate_18_word_mnemonic` | ✓ | ✅ | `generate18WordsMnemonic` | Generate 18-word mnemonic from 192 bits of entropy |
| `generate_21_word_mnemonic` | ✓ | ✅ | `generate21WordsMnemonic` | Generate 21-word mnemonic from 224 bits of entropy |
| `generate_24_word_mnemonic` | ✓ | ✅ | `generate24WordsMnemonic` | Generate 24-word mnemonic from 256 bits of entropy |

### Mnemonic Validation

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `validate_mnemonic` | ✓ | ✅ | `isValidMnemonic` | Validate mnemonic phrase by checking word list membership and checksum |
| `detect_language` |  | ✅ | `detectLanguage` | Detect the language of a mnemonic phrase by matching words against word lists |

### SLIP-0010 Key Derivation

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `stellar_derivation_path` | ✓ | ✅ | `getKeyPair` | Stellar-specific derivation path m/44'/148'/x' where 44' is BIP-44 purpose and 148' is Stellar co... |
| `hardened_derivation` | ✓ | ✅ | `getKeyPair` | All derivation indices must be hardened (index + 2^31) for Ed25519 |
| `ed25519_master_key_generation` | ✓ | ✅ | `getKeyPair` | Generate master key using HMAC-SHA512(key='ed25519 seed', data=BIP39_seed) |
| `ed25519_child_key_derivation` | ✓ | ✅ | `getKeyPair` | Derive child keys using HMAC-SHA512(key=parent_chain_code, data=0x00||parent_key||index+2^31) |

### Test Vectors

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `test_vector_1_12words` | ✓ | ✅ | `(verified in tests)` | 12-word mnemonic test vector: 'illness spike retreat truth genius clock brain pass fit cave barga... |
| `test_vector_2_15words` | ✓ | ✅ | `(verified in tests)` | 15-word mnemonic test vector: 'resource asthma orphan phone ice canvas fire useful arch jewel imp... |
| `test_vector_3_24words` | ✓ | ✅ | `(verified in tests)` | 24-word mnemonic test vector: 'bench hurt jump file august wise...' with expected accounts |
| `test_vector_4_24words_passphrase` | ✓ | ✅ | `(verified in tests)` | 24-word mnemonic with passphrase test vector: 'cable spray genius state float twenty...' with pas... |
| `test_vector_5_abandon_about` | ✓ | ✅ | `(verified in tests)` | Known test vector: 'abandon abandon abandon abandon abandon abandon abandon abandon abandon aband... |

## Implementation Gaps

No gaps found! All fields are implemented.

## Recommendations

The SDK has full compatibility with SEP-0005!

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations
- **Server**: Server-side only feature (not applicable to client SDKs)
- ✓ **Required**: Field is required by SEP specification

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0005](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0005.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0005`
