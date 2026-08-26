# SEP-05: Key Derivation Methods for Stellar Keys

**Purpose:** Generate BIP-39 mnemonic phrases and derive deterministic Stellar keypairs using the SEP-0005 hierarchical derivation path `m/44'/148'/x'`.
**Prerequisites:** None
**Package:** `com.soneso.stellar.sdk.sep.sep05`

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep05.*
import com.soneso.stellar.sdk.sep.sep05.exceptions.*
```

## Table of Contents

1. [Mnemonic Class Overview](#1-mnemonic-class-overview)
2. [Mnemonic Generation](#2-mnemonic-generation)
3. [Language Support](#3-language-support)
4. [Deriving Keypairs from a Mnemonic](#4-deriving-keypairs-from-a-mnemonic)
5. [Multiple Account Derivation](#5-multiple-account-derivation)
6. [BIP-39 Passphrase](#6-bip-39-passphrase)
7. [From a BIP-39 Seed Directly](#7-from-a-bip-39-seed-directly)
8. [From Raw Entropy](#8-from-raw-entropy)
9. [Mnemonic Validation and Language Detection](#9-mnemonic-validation-and-language-detection)
10. [Seed Access](#10-seed-access)
11. [Cleanup with close()](#11-cleanup-with-close)
12. [Error Handling](#12-error-handling)
13. [Common Pitfalls](#13-common-pitfalls)

---

## 1. Mnemonic Class Overview

`Mnemonic` is the main entry point for SEP-05 key derivation. It has a private constructor; all creation goes through companion object factory methods: `Mnemonic.from()`, `Mnemonic.fromBip39HexSeed()`, `Mnemonic.fromBip39Seed()`, or `Mnemonic.fromEntropy()`. It stores the 64-byte BIP-39 seed internally and derives keypairs on demand using the Stellar path `m/44'/148'/index'`.

**All factory methods and key derivation methods are `suspend` functions.** They must be called from a coroutine scope. On JVM/Native this has zero overhead; on JS it enables proper async crypto initialization.

`Mnemonic` implements `AutoCloseable`. Call `close()` when done to zero internal seed data.

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // 1. Generate a fresh mnemonic phrase
    val phrase = Mnemonic.generate24WordsMnemonic()

    // 2. Create Mnemonic instance from that phrase
    val mnemonic = Mnemonic.from(phrase)

    // 3. Derive keypair for account 0
    val keyPair = mnemonic.getKeyPair(index = 0)
    println(keyPair.getAccountId())               // G... public key
    println(keyPair.getSecretSeed()?.concatToString()) // S... secret key -- store securely, never log

    // 4. Clean up when done
    mnemonic.close()
}
```

> **Class name:** The class is `Mnemonic` (some other Stellar SDKs call it `Wallet`). `getSecretSeed()` returns `CharArray?` (not `String`), so use `concatToString()` to convert it.

---

## 2. Mnemonic Generation

Five convenience methods generate 12-, 15-, 18-, 21-, or 24-word phrases. All return `String` (suspend) with space-separated words.

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // 12 words -- 128 bits of entropy (adequate for most use cases)
    val mnemonic12 = Mnemonic.generate12WordsMnemonic()
    println(mnemonic12)
    // e.g. "twice news void fiction lamp chaos few code rate donkey supreme primary"

    // 15 words -- 160 bits of entropy
    val mnemonic15 = Mnemonic.generate15WordsMnemonic()

    // 18 words -- 192 bits of entropy
    val mnemonic18 = Mnemonic.generate18WordsMnemonic()

    // 21 words -- 224 bits of entropy
    val mnemonic21 = Mnemonic.generate21WordsMnemonic()

    // 24 words -- 256 bits of entropy (recommended for high-value accounts)
    val mnemonic24 = Mnemonic.generate24WordsMnemonic()
    println(mnemonic24)
}
```

All five default to English. Pass a `language` parameter to change the word list (see section 3).

---

## 3. Language Support

Nine languages are available via the `MnemonicLanguage` enum. Pass the enum value as the `language` parameter to any generation or parsing method.

| Enum Value                              | Language              |
|-----------------------------------------|-----------------------|
| `MnemonicLanguage.ENGLISH`              | English (default)     |
| `MnemonicLanguage.FRENCH`               | French                |
| `MnemonicLanguage.SPANISH`              | Spanish               |
| `MnemonicLanguage.ITALIAN`              | Italian               |
| `MnemonicLanguage.KOREAN`               | Korean                |
| `MnemonicLanguage.JAPANESE`             | Japanese              |
| `MnemonicLanguage.CHINESE_SIMPLIFIED`   | Chinese Simplified    |
| `MnemonicLanguage.CHINESE_TRADITIONAL`  | Chinese Traditional   |
| `MnemonicLanguage.MALAY`               | Malay                 |

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import com.soneso.stellar.sdk.sep.sep05.MnemonicLanguage
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Generate a French 12-word mnemonic
    val frenchPhrase = Mnemonic.generate12WordsMnemonic(
        language = MnemonicLanguage.FRENCH
    )
    println(frenchPhrase)

    // Generate a Korean 24-word mnemonic
    val koreanPhrase = Mnemonic.generate24WordsMnemonic(
        language = MnemonicLanguage.KOREAN
    )

    // When creating a Mnemonic from a non-English phrase, pass the SAME language.
    // Alternatively, pass language = null (the default) to auto-detect.
    val mnemonic = Mnemonic.from(koreanPhrase, language = MnemonicLanguage.KOREAN)
    val kp = mnemonic.getKeyPair(index = 0)
    println(kp.getAccountId())
    mnemonic.close()
}
```

> **Auto-detection:** `Mnemonic.from()` defaults to `language = null`, which auto-detects the language.

---

## 4. Deriving Keypairs from a Mnemonic

`Mnemonic.from()` validates the mnemonic (checksum + word list), then converts it to a 64-byte BIP-39 seed. It throws `InvalidMnemonicException` if validation fails.

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val mnemonic = Mnemonic.from(
        "shell green recycle learn purchase able oxygen right echo claim hill again " +
        "hidden evidence nice decade panic enemy cake version say furnace garment glue"
    )

    // getKeyPair returns a full KeyPair (public + private)
    val keyPair0 = mnemonic.getKeyPair(index = 0)
    println(keyPair0.getAccountId())                      // GCVSEBHB6CTMEHUHIUY4DDFMWQ7PJTHFZGOK2JUD5EG2ARNVS6S22E3K
    println(keyPair0.getSecretSeed()?.concatToString())   // SATLGMF3SP2V47SJLBFVKZZJQARDOBDQ7DNSSPUV7NLQNPN3QB7M74XH

    val keyPair1 = mnemonic.getKeyPair(index = 1)
    println(keyPair1.getAccountId())                      // GBPHPX7SZKYEDV5CVOA5JOJE2RHJJDCJMRWMV4KBOIE5VSDJ6VAESR2W
    println(keyPair1.getSecretSeed()?.concatToString())   // SCAYXPIDEUVDGDTKF4NGVMN7HCZOTZJ43E62EEYKVUYXEE7HMU4DFQA6

    // getAccountId is a convenience method -- returns only the G... public key
    // without exposing the private key
    val accountId = mnemonic.getAccountId(index = 0)
    println(accountId) // GCVSEBHB6CTMEHUHIUY4DDFMWQ7PJTHFZGOK2JUD5EG2ARNVS6S22E3K

    mnemonic.close()
}
```

The derivation path used is `m/44'/148'/index'` (all components hardened, following SLIP-0010 for Ed25519).

---

## 5. Multiple Account Derivation

A single `Mnemonic` instance can derive an unlimited number of accounts. Index 0 is the primary account; subsequent indices are independent accounts under the same seed.

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val mnemonic = Mnemonic.from(
        "illness spike retreat truth genius clock brain pass fit cave bargain toe"
    )

    // Derive accounts 0-4
    for (i in 0 until 5) {
        val kp = mnemonic.getKeyPair(index = i)
        println("Account $i: ${kp.getAccountId()}")
    }
    // Account 0: GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6
    // Account 1: GBAW5XGWORWVFE2XTJYDTLDHXTY2Q2MO73HYCGB3XMFMQ562Q2W2GJQX
    // Account 2: GAY5PRAHJ2HIYBYCLZXTHID6SPVELOOYH2LBPH3LD4RUMXUW3DOYTLXW
    // Account 3: GAOD5NRAEORFE34G5D4EOSKIJB6V4Z2FGPBCJNQI6MNICVITE6CSYIAE
    // Account 4: GBCUXLFLSL2JE3NWLHAWXQZN6SQC6577YMAU3M3BEMWKYPFWXBSRCWV4

    // Collect keypairs into a list
    val keyPairs = (0 until 3).map { mnemonic.getKeyPair(index = it) }

    mnemonic.close()
}
```

---

## 6. BIP-39 Passphrase

An optional passphrase can be provided to `Mnemonic.from()`. A passphrase creates a completely different set of accounts from the same mnemonic -- it is NOT a password protecting the mnemonic. Without the exact passphrase, the accounts cannot be recovered even with the correct mnemonic.

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val phrase = "cable spray genius state float twenty onion head street palace net private " +
        "method loan turn phrase state blanket interest dry amazing dress blast tube"

    // Same mnemonic, different passphrase -> completely different keypairs
    val mnemonicNoPass = Mnemonic.from(phrase)
    val mnemonicWithPass = Mnemonic.from(phrase, passphrase = "p4ssphr4se")

    val kp0NoPass = mnemonicNoPass.getKeyPair(index = 0)
    val kp0WithPass = mnemonicWithPass.getKeyPair(index = 0)

    // These produce different public keys
    println(kp0NoPass.getAccountId())   // GA4ZRW4S5P6R5DAZBSMEL2QANTIJJ6WSQCEKZEMEP3EZIB44ZGIG2SOF
    println(kp0WithPass.getAccountId()) // GDAHPZ2NSYIIHZXM56Y36SBVTV5QKFIZGYMMBHOU53ETUSWTP62B63EQ
    println(kp0WithPass.getSecretSeed()?.concatToString()) // SAFWTGXVS7ELMNCXELFWCFZOPMHUZ5LXNBGUVRCY3FHLFPXK4QPXYP2X

    val kp1WithPass = mnemonicWithPass.getKeyPair(index = 1)
    println(kp1WithPass.getAccountId()) // GDY47CJARRHHL66JH3RJURDYXAMIQ5DMXZLP3TDAUJ6IN2GUOFX4OJOC

    mnemonicNoPass.close()
    mnemonicWithPass.close()
}
```

---

## 7. From a BIP-39 Seed Directly

When you have a pre-computed 64-byte BIP-39 seed (from hardware wallet export, another library, or stored externally), skip the mnemonic step.

### From hex string

`Mnemonic.fromBip39HexSeed()` accepts a 128-character hex string representing 64 bytes:

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val mnemonic = Mnemonic.fromBip39HexSeed(
        "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497e" +
        "e4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186"
    )

    val kp0 = mnemonic.getKeyPair(index = 0)
    println(kp0.getAccountId())                    // GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6
    println(kp0.getSecretSeed()?.concatToString()) // SBGWSG6BTNCKCOB3DIFBGCVMUPQFYPA2G4O34RMTB343OYPXU5DJDVMN

    val kp1 = mnemonic.getKeyPair(index = 1)
    println(kp1.getAccountId())                    // GBAW5XGWORWVFE2XTJYDTLDHXTY2Q2MO73HYCGB3XMFMQ562Q2W2GJQX

    mnemonic.close()
}
```

### From ByteArray

`Mnemonic.fromBip39Seed()` accepts a `ByteArray` of exactly 64 bytes:

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import com.soneso.stellar.sdk.sep.sep05.MnemonicUtils
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Compute the seed from a mnemonic phrase
    val seedBytes: ByteArray = MnemonicUtils.mnemonicToSeed(
        "illness spike retreat truth genius clock brain pass fit cave bargain toe"
    )
    // seedBytes is 64 bytes

    val mnemonic = Mnemonic.fromBip39Seed(seedBytes)
    val kp = mnemonic.getKeyPair(index = 0)
    println(kp.getAccountId()) // GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6

    mnemonic.close()
}
```

---

## 8. From Raw Entropy

`Mnemonic.fromEntropy()` creates a Mnemonic instance from raw entropy bytes (16, 20, 24, 28, or 32 bytes). The entropy is converted internally to a mnemonic phrase and then to a BIP-39 seed.

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import com.soneso.stellar.sdk.sep.sep05.MnemonicLanguage
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // 32 bytes of entropy -> 24-word mnemonic internally
    val entropy = ByteArray(32) // replace with your secure entropy source
    val mnemonic = Mnemonic.fromEntropy(entropy)

    val kp = mnemonic.getKeyPair(index = 0)
    println(kp.getAccountId())

    // With a specific language and passphrase
    val frenchMnemonic = Mnemonic.fromEntropy(
        entropy = entropy,
        language = MnemonicLanguage.FRENCH,
        passphrase = "my-secret"
    )

    frenchMnemonic.close()
    mnemonic.close()
}
```

---

## 9. Mnemonic Validation and Language Detection

### Validation

`Mnemonic.validate()` checks that all words are in the word list and the BIP-39 checksum is correct. Returns `Boolean` (suspend).

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import com.soneso.stellar.sdk.sep.sep05.MnemonicLanguage
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Valid mnemonic
    val valid = Mnemonic.validate(
        "illness spike retreat truth genius clock brain pass fit cave bargain toe"
    )
    println(valid) // true

    // Invalid -- bad checksum or unknown words
    val invalid = Mnemonic.validate(
        "witch witch witch witch witch witch witch witch witch witch witch witch"
    )
    println(invalid) // false

    // Validate a non-English mnemonic -- pass the matching language
    val validKorean = Mnemonic.validate(
        "절차 튀김 건강 평가 테스트 민족 몹시 어른 주민 형제 발레 만점 " +
        "산길 물고기 방면 여학생 결국 수명 애정 정치 관심 상자 축하 고무신",
        language = MnemonicLanguage.KOREAN
    )
    println(validKorean) // true

    // Mnemonic.from() validates internally and throws InvalidMnemonicException on failure
    try {
        Mnemonic.from("bad mnemonic words here")
    } catch (e: com.soneso.stellar.sdk.sep.sep05.exceptions.InvalidMnemonicException) {
        println(e.message) // "Invalid mnemonic phrase" or "Cannot detect mnemonic language"
    }
}
```

### Language Detection

`Mnemonic.detectLanguage()` iterates all supported languages and returns the first one where all words exist in the word list. Returns `MnemonicLanguage?` (null if no match). This is a regular (non-suspend) function.

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic

fun main() {
    val language = Mnemonic.detectLanguage(
        "illness spike retreat truth genius clock brain pass fit cave bargain toe"
    )
    println(language) // ENGLISH

    val unknown = Mnemonic.detectLanguage("foo bar baz")
    println(unknown) // null
}
```

> **Note:** `Mnemonic.from()` with `language = null` (the default) calls `detectLanguage()` internally. If detection fails, it throws `InvalidMnemonicException("Cannot detect mnemonic language")`.

---

## 10. Seed Access

After creating a `Mnemonic` instance, you can access the underlying BIP-39 seed for export or interoperability.

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val mnemonic = Mnemonic.from(
        "illness spike retreat truth genius clock brain pass fit cave bargain toe"
    )

    // Get seed as ByteArray (defensive copy -- modifying it does not affect the Mnemonic)
    val seedBytes: ByteArray = mnemonic.getBip39Seed()
    println(seedBytes.size) // 64

    // Get seed as hex string
    val seedHex: String = mnemonic.getBip39SeedHex()
    println(seedHex)
    // e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497e...

    mnemonic.close()
}
```

`getBip39Seed()` and `getBip39SeedHex()` are regular (non-suspend) functions.

---

## 11. Cleanup with close()

`Mnemonic` implements `AutoCloseable`. Calling `close()` zeros out the internal seed for security. After `close()`, the instance is unusable -- any key derivation will produce invalid results.

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val mnemonic = Mnemonic.from(
        "illness spike retreat truth genius clock brain pass fit cave bargain toe"
    )

    // Use the mnemonic...
    val kp = mnemonic.getKeyPair(index = 0)
    println(kp.getAccountId())

    // Clean up -- zeros the internal seed
    mnemonic.close()

    // After close(), getBip39Seed() returns an empty array
    println(mnemonic.getBip39Seed().size) // 0
}
```

You can also use Kotlin's `use` extension for automatic cleanup:

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val phrase = Mnemonic.generate24WordsMnemonic()

    Mnemonic.from(phrase).use { mnemonic ->
        val kp = mnemonic.getKeyPair(index = 0)
        println(kp.getAccountId())
    } // close() is called automatically here
}
```

---

## 12. Error Handling

All SEP-05 exceptions extend `Sep05Exception`. The exception hierarchy allows broad or specific error handling.

| Exception                  | Thrown When                                              |
|----------------------------|----------------------------------------------------------|
| `Sep05Exception`           | Base class for all SEP-05 errors                         |
| `InvalidMnemonicException` | Bad word count, language detection failure, general validation failure |
| `InvalidWordException`     | A word is not in the specified word list (has `word` and `language` properties) |
| `InvalidChecksumException` | Words are valid but the BIP-39 checksum does not match   |
| `InvalidEntropyException`  | Entropy size is not 16, 20, 24, 28, or 32 bytes         |
| `InvalidPathException`     | Invalid BIP-32 derivation path format (has `path` property) |

```kotlin
import com.soneso.stellar.sdk.sep.sep05.Mnemonic
import com.soneso.stellar.sdk.sep.sep05.exceptions.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    try {
        val mnemonic = Mnemonic.from("abandonn abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about")
        mnemonic.close()
    } catch (e: InvalidWordException) {
        // Specific: a word was not found in the word list
        println("Unknown word '${e.word}' in ${e.language.name} word list")
    } catch (e: InvalidChecksumException) {
        // Specific: words are valid but checksum failed
        println("Checksum failed -- check for typos")
    } catch (e: InvalidMnemonicException) {
        // General: bad word count, language detection failure, etc.
        println("Invalid mnemonic: ${e.message}")
    } catch (e: Sep05Exception) {
        // Catch-all for any SEP-05 error
        println("SEP-05 error: ${e.message}")
    }
}
```

> **Note:** `Mnemonic.validate()` catches all `Sep05Exception` subclasses internally and returns `false` instead of throwing. Use `validate()` for boolean checks; use `Mnemonic.from()` or `MnemonicUtils.mnemonicToEntropy()` when you need the specific exception.

---

## 13. Common Pitfalls

**Not calling suspend functions from a coroutine scope:**

```kotlin
// WRONG: factory methods and key derivation are suspend functions
fun main() {
    val phrase = Mnemonic.generate24WordsMnemonic()  // compile error: suspend function
    val mnemonic = Mnemonic.from(phrase)              // compile error: suspend function
}

// CORRECT: wrap in runBlocking (scripts/tests) or a coroutine scope (apps)
fun main() = runBlocking {
    val phrase = Mnemonic.generate24WordsMnemonic()
    val mnemonic = Mnemonic.from(phrase)
    val kp = mnemonic.getKeyPair(index = 0)
    mnemonic.close()
}
```

**Using the wrong class name:**

```kotlin
// WRONG: the KMP SDK does not have a Wallet class
val wallet = Wallet.from(mnemonic)

// CORRECT: use Mnemonic
val mnemonic = Mnemonic.from(phrase)
```

**Treating getSecretSeed() as String:**

```kotlin
val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
// WRONG: getSecretSeed() returns CharArray?, not String
val seed: String = keyPair.getSecretSeed()

// CORRECT: convert CharArray to String with concatToString()
val seed: String? = keyPair.getSecretSeed()?.concatToString()
```

**Forgetting to close() the Mnemonic instance:**

```kotlin
// WRONG: seed data stays in memory indefinitely
val mnemonic = Mnemonic.from(phrase)
val kp = mnemonic.getKeyPair(index = 0)
// mnemonic is never closed -- sensitive data remains in memory

// CORRECT: always close when done, or use .use {}
try {
    val kp = mnemonic.getKeyPair(index = 0)
    // ... use kp ...
} finally {
    mnemonic.close()
}

// Or use Kotlin's .use {} for automatic cleanup
Mnemonic.from(phrase).use { m ->
    val kp = m.getKeyPair(index = 0)
}
```

**Misunderstanding the BIP-39 passphrase -- it changes the wallet entirely:**

```kotlin
// WRONG assumption: passphrase "protects" the same accounts
// In BIP-39, the passphrase is mixed into the seed derivation and produces
// completely different accounts. Without the exact passphrase you cannot
// recover the same accounts, even with the correct mnemonic.

// CORRECT: treat mnemonic + passphrase as an inseparable unit; store both
val mnemonic = Mnemonic.from(phrase, passphrase = "my-extra-secret")
// Losing the passphrase means losing access -- there is no recovery
mnemonic.close()
```

**Using string literals instead of MnemonicLanguage enum:**

```kotlin
// WRONG: language parameter takes MnemonicLanguage enum, not a String
val mnemonic = Mnemonic.from(phrase, language = "Korean")  // compile error

// CORRECT: use MnemonicLanguage enum values
val mnemonic = Mnemonic.from(phrase, language = MnemonicLanguage.KOREAN)
```

**Relying on language auto-detection when you know the language:**

```kotlin
// FRAGILE: auto-detection may pick the wrong language for ambiguous words
val autoDetected = Mnemonic.from(frenchPhrase)  // might misdetect if words overlap with another language

// BETTER: pass the known language explicitly for non-English mnemonics
val mnemonic = Mnemonic.from(frenchPhrase, language = MnemonicLanguage.FRENCH)
```

**Catching the wrong exception type:**

```kotlin
// WRONG: Mnemonic.from() throws InvalidMnemonicException, NOT ArgumentError or IllegalArgumentException
try {
    val mnemonic = Mnemonic.from("bad mnemonic")
} catch (e: IllegalArgumentException) {  // will NOT catch the exception
    println(e.message)
}

// CORRECT: catch InvalidMnemonicException (or Sep05Exception for broad handling)
try {
    val mnemonic = Mnemonic.from("bad mnemonic")
} catch (e: InvalidMnemonicException) {
    println(e.message)
}
```
