# SEP-5: Key Derivation Methods for Stellar Keys

**[SEP-0005 Compatibility Matrix](../../compatibility/sep/SEP-0005_COMPATIBILITY_MATRIX.md)** - Full implementation coverage details

SEP-5 defines how to derive multiple Stellar accounts from a single mnemonic phrase using BIP-39 and SLIP-0010 key derivation. This enables HD (Hierarchical Deterministic) wallets where users back up all accounts with a single recovery phrase.

**Use Cases**:
- Generate multiple Stellar accounts from a single backup phrase
- Restore wallets from mnemonic phrases
- Hardware wallet compatible key derivation

**Note**: Most SEP-5 methods are `suspend` functions for JavaScript compatibility. Call them from a coroutine context (e.g., `launch { }`, `runBlocking { }`, or another suspend function).

## Generating a Mnemonic Phrase

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import com.soneso.stellar.sdk.sep.sep05.MnemonicLanguage

// Generate a 24-word mnemonic (256 bits entropy - recommended)
val phrase = Mnemonic.generate24WordsMnemonic()
// Example: "bench hurt jump file august wise shallow faculty impulse spring exact slush thunder author capable act festival slice deposit sauce coconut afford frown better"

// Other word lengths
val phrase12 = Mnemonic.generate12WordsMnemonic()  // 128 bits
val phrase15 = Mnemonic.generate15WordsMnemonic()  // 160 bits
val phrase18 = Mnemonic.generate18WordsMnemonic()  // 192 bits
val phrase21 = Mnemonic.generate21WordsMnemonic()  // 224 bits

// Generate in different languages (9 supported)
val japanese = Mnemonic.generate24WordsMnemonic(MnemonicLanguage.JAPANESE)
val spanish = Mnemonic.generate24WordsMnemonic(MnemonicLanguage.SPANISH)
```

## Deriving Stellar Accounts

```kotlin
// Create Mnemonic instance from phrase
val phrase = "illness spike retreat truth genius clock brain pass fit cave bargain toe"
val mnemonic = Mnemonic.from(phrase)

// Derive accounts using Stellar path: m/44'/148'/index'
val account0 = mnemonic.getKeyPair(index = 0)  // Primary account
val account1 = mnemonic.getKeyPair(index = 1)
val account2 = mnemonic.getKeyPair(index = 2)

println(account0.getAccountId())
// Output: GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6

// Get account ID without full keypair
val accountId = mnemonic.getAccountId(index = 0)

// Clean up when done (zeros internal seed)
mnemonic.close()
```

## Using a Passphrase

A passphrase creates a different wallet from the same mnemonic. Lost passphrases cannot be recovered.

```kotlin
val phrase = "cable spray genius state float twenty onion head street palace net private method loan turn phrase state blanket interest dry amazing dress blast tube"

// With passphrase - produces completely different wallet
val mnemonic = Mnemonic.from(phrase, passphrase = "p4ssphr4se")

println(mnemonic.getAccountId(0))
// Output: GDAHPZ2NSYIIHZXM56Y36SBVTV5QKFIZGYMMBHOU53ETUSWTP62B63EQ

mnemonic.close()
```

## Validating Mnemonics

```kotlin
// Validate a mnemonic phrase (checks words and checksum)
val isValid = Mnemonic.validate(
    "illness spike retreat truth genius clock brain pass fit cave bargain toe"
)
// isValid: true

// Detect language
val language = Mnemonic.detectLanguage(
    "illness spike retreat truth genius clock brain pass fit cave bargain toe"
)
// language: MnemonicLanguage.ENGLISH

// Validate with explicit language (useful when language is known)
val isValidInLanguage = Mnemonic.validate(phrase, MnemonicLanguage.ENGLISH)
```

## Supported Languages

| Language | Enum Value |
|----------|------------|
| English | `MnemonicLanguage.ENGLISH` |
| Japanese | `MnemonicLanguage.JAPANESE` |
| Korean | `MnemonicLanguage.KOREAN` |
| Spanish | `MnemonicLanguage.SPANISH` |
| Chinese (Simplified) | `MnemonicLanguage.CHINESE_SIMPLIFIED` |
| Chinese (Traditional) | `MnemonicLanguage.CHINESE_TRADITIONAL` |
| French | `MnemonicLanguage.FRENCH` |
| Italian | `MnemonicLanguage.ITALIAN` |
| Malay | `MnemonicLanguage.MALAY` |

**Note**: Japanese mnemonics use ideographic space (U+3000) as word separator instead of regular space.

## Security Notes

- Store mnemonics securely; never expose in logs or insecure storage
- Use 24 words for maximum security
- Lost passphrases cannot be recovered
- Call `close()` when done to zero internal seed data

**Specification**: [SEP-5: Key Derivation Methods for Stellar Keys](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0005.md)

**Implementation**: `com.soneso.stellar.sdk.sep.sep05`

**Last Updated**: 2026-02-04
