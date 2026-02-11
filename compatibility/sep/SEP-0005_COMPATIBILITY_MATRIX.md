# SEP-0005 (Key Derivation Methods for Stellar Keys) Compatibility Matrix

**Generated:** 2026-02-11 15:11:14

**SEP Version:** 1.0.0<br>
**SEP Status:** Active<br>
**SDK Version:** 1.2.1<br>
**SEP URL:** https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0005.md

## SEP Summary

SEP-5 defines key derivation methods for Stellar keys using BIP-39 mnemonics and SLIP-0010 hierarchical deterministic key derivation. It specifies how to generate mnemonic phrases in multiple languages, derive BIP-39 seeds using PBKDF2-HMAC-SHA512, and derive Ed25519 keypairs using the Stellar-specific path m/44'/148'/x'. This enables deterministic wallet recovery and interoperability across Stellar wallet implementations.

## Overall Coverage

**Total Coverage:** 100.0% (31/31 fields)

- ✅ **Implemented:** 31/31
- ❌ **Not Implemented:** 0/31

## Implementation Status

✅ **Fully Implemented**

### Implementation Files

- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/Mnemonic.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/MnemonicUtils.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/WordList.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/MnemonicLanguage.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/MnemonicStrength.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/MnemonicConstants.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/HexCodec.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/crypto/PlatformCrypto.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/exceptions/Sep05Exception.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/exceptions/InvalidMnemonicException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/exceptions/InvalidEntropyException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/exceptions/InvalidChecksumException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/exceptions/InvalidWordException.kt`
- `stellar-sdk/src/commonMain/kotlin/com/soneso/stellar/sdk/sep/sep05/exceptions/InvalidPathException.kt`

### Key Classes

- **`Mnemonic`** - Methods: generate12WordsMnemonic, generate15WordsMnemonic, generate18WordsMnemonic, generate21WordsMnemonic, generate24WordsMnemonic, validate, detectLanguage, from, fromEntropy, fromBip39HexSeed, fromBip39Seed, getKeyPair, getAccountId, getPrivateKey, getPublicKey, getBip39Seed, getBip39SeedHex, close
- **`MnemonicUtils`** - Methods: generateMnemonic, entropyToMnemonic, mnemonicToEntropy, validateMnemonic, detectLanguage, mnemonicToSeed, mnemonicToSeedHex, getMnemonicStrength
- **`MnemonicLanguage`**
- **`MnemonicStrength`** - Methods: fromWordCount, fromEntropyBits
- **`WordList`** - Methods: getWordList, getWordIndex
- **`MnemonicConstants`**
- **`HexCodec`** - Methods: encode, decode

### Test Coverage

**Tests:** 167 test cases

**Test Files:**

- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep05/MnemonicTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep05/MnemonicUtilsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep05/Sep05TestVectorsTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep05/WordListTest.kt`
- `stellar-sdk/src/commonTest/kotlin/com/soneso/stellar/sdk/sep/sep05/HexCodecTest.kt`

## Coverage by Section

| Section | Coverage | Implemented | Total |
|---------|----------|-------------|-------|
| BIP-39 Seed Derivation | 100.0% | 2 | 2 |
| Key Export | 100.0% | 4 | 4 |
| Language Support | 100.0% | 9 | 9 |
| Mnemonic Generation | 100.0% | 5 | 5 |
| Mnemonic Validation | 100.0% | 2 | 2 |
| SLIP-0010 Key Derivation | 100.0% | 4 | 4 |
| Test Vectors | 100.0% | 5 | 5 |

## Detailed Field Comparison

### BIP-39 Seed Derivation

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `mnemonic_to_seed` | ✓ | ✅ | `MnemonicUtils.mnemonicToSeed()` | Convert mnemonic to 64-byte BIP-39 seed using PBKDF2-HMAC-SHA512 with 2048 iterations |
| `passphrase_support` | ✓ | ✅ | `Mnemonic.from(passphrase)` | Support optional passphrase for additional security in seed derivation |

### Key Export

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `get_keypair` | ✓ | ✅ | `Mnemonic.getKeyPair()` | Get full Stellar KeyPair (public and private key) at specified derivation index |
| `get_account_id` | ✓ | ✅ | `Mnemonic.getAccountId()` | Get Stellar account ID (G... address) at specified derivation index |
| `get_public_key` | ✓ | ✅ | `Mnemonic.getPublicKey()` | Get raw 32-byte Ed25519 public key at specified derivation index |
| `get_private_key` | ✓ | ✅ | `Mnemonic.getPrivateKey()` | Get raw 32-byte Ed25519 private key at specified derivation index |

### Language Support

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `english` | ✓ | ✅ | `MnemonicLanguage.ENGLISH` | English BIP-39 word list (2048 words) |
| `japanese` |  | ✅ | `MnemonicLanguage.JAPANESE` | Japanese BIP-39 word list (2048 words) |
| `korean` |  | ✅ | `MnemonicLanguage.KOREAN` | Korean BIP-39 word list (2048 words) |
| `spanish` |  | ✅ | `MnemonicLanguage.SPANISH` | Spanish BIP-39 word list (2048 words) |
| `chinese_simplified` |  | ✅ | `MnemonicLanguage.CHINESE_SIMPLIFIED` | Simplified Chinese BIP-39 word list (2048 words) |
| `chinese_traditional` |  | ✅ | `MnemonicLanguage.CHINESE_TRADITIONAL` | Traditional Chinese BIP-39 word list (2048 words) |
| `french` |  | ✅ | `MnemonicLanguage.FRENCH` | French BIP-39 word list (2048 words) |
| `italian` |  | ✅ | `MnemonicLanguage.ITALIAN` | Italian BIP-39 word list (2048 words) |
| `malay` |  | ✅ | `MnemonicLanguage.MALAY` | Malay BIP-39 word list (2048 words) |

### Mnemonic Generation

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `generate_12_word_mnemonic` | ✓ | ✅ | `Mnemonic.generate12WordsMnemonic()` | Generate 12-word mnemonic from 128 bits of entropy |
| `generate_15_word_mnemonic` | ✓ | ✅ | `Mnemonic.generate15WordsMnemonic()` | Generate 15-word mnemonic from 160 bits of entropy |
| `generate_18_word_mnemonic` | ✓ | ✅ | `Mnemonic.generate18WordsMnemonic()` | Generate 18-word mnemonic from 192 bits of entropy |
| `generate_21_word_mnemonic` | ✓ | ✅ | `Mnemonic.generate21WordsMnemonic()` | Generate 21-word mnemonic from 224 bits of entropy |
| `generate_24_word_mnemonic` | ✓ | ✅ | `Mnemonic.generate24WordsMnemonic()` | Generate 24-word mnemonic from 256 bits of entropy |

### Mnemonic Validation

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `validate_mnemonic` | ✓ | ✅ | `Mnemonic.validate()` | Validate mnemonic phrase by checking word list membership and checksum |
| `detect_language` |  | ✅ | `Mnemonic.detectLanguage()` | Detect the language of a mnemonic phrase by matching words against word lists |

### SLIP-0010 Key Derivation

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `stellar_derivation_path` | ✓ | ✅ | `MnemonicConstants.STELLAR_COIN_TYPE + MnemonicConstants.BIP44_PURPOSE` | Stellar-specific derivation path m/44'/148'/x' where 44' is BIP-44 purpose and 148' is Stellar co... |
| `hardened_derivation` | ✓ | ✅ | `MnemonicConstants.BIP32_HARDENED_OFFSET` | All derivation indices must be hardened (index + 2^31) for Ed25519 |
| `ed25519_master_key_generation` | ✓ | ✅ | `MnemonicConstants.ED25519_SEED_KEY` | Generate master key using HMAC-SHA512(key='ed25519 seed', data=BIP39_seed) |
| `ed25519_child_key_derivation` | ✓ | ✅ | `Mnemonic.derivePath()` | Derive child keys using HMAC-SHA512(key=parent_chain_code, data=0x00||parent_key||index+2^31) |

### Test Vectors

| Field | Required | Status | SDK Property | Description |
|-------|----------|--------|--------------|-------------|
| `test_vector_1_12words` | ✓ | ✅ | `Sep05TestVectorsTest.testVector1_12Words_AllAccounts()` | 12-word mnemonic test vector: 'illness spike retreat truth genius clock brain pass fit cave barga... |
| `test_vector_2_15words` | ✓ | ✅ | `Sep05TestVectorsTest.testVector3_15Words_AllAccounts()` | 15-word mnemonic test vector: 'resource asthma orphan phone ice canvas fire useful arch jewel imp... |
| `test_vector_3_24words` | ✓ | ✅ | `Sep05TestVectorsTest.testVector4_24Words_AllAccounts()` | 24-word mnemonic test vector: 'bench hurt jump file august wise...' with expected accounts |
| `test_vector_4_24words_passphrase` | ✓ | ✅ | `Sep05TestVectorsTest.testVector5_24Words_WithPassphrase_AllAccounts()` | 24-word mnemonic with passphrase test vector: 'cable spray genius state float twenty...' with pas... |
| `test_vector_5_abandon_about` | ✓ | ✅ | `Sep05TestVectorsTest.testVector6_12Words_AbandonAbout_AllAccounts()` | Known test vector: 'abandon abandon abandon abandon abandon abandon abandon abandon abandon aband... |

## Legend

- ✅ **Implemented**: Field is fully supported in the SDK
- ❌ **Not Implemented**: Field is not currently supported
- ⚠️ **Partial**: Field is partially supported with limitations

## Additional Information

**Documentation:** See `docs/sep-implementations.md` for usage examples and API reference

**Specification:** [SEP-0005](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0005.md)

**Implementation Package:** `com.soneso.stellar.sdk.sep.sep0005`
