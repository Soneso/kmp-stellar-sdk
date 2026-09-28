# SEP-23: Strkey Encoding

SEP-23 defines how Stellar writes keys and identifiers as strkeys: base32 strings whose first letter names their type. Account ids start with `G`, secret seeds with `S`, muxed accounts with `M`, contracts with `C`, and so on. The SDK codec is the `StrKey` object, and every SDK type that reads or writes a strkey (`KeyPair`, `Address`, `MuxedAccount`, `SignerKey`, `ClaimableBalanceId`) goes through it, so one string is accepted or rejected identically on JVM, Android, iOS, macOS and JavaScript.

**Use Cases**:
- Validate addresses entered by users or received from other services
- Convert between raw key bytes and their string form
- Create and parse muxed accounts for sub-account tracking
- Build signer keys for pre-authorized transactions, hash-locks and signed payloads
- Read contract, liquidity pool and claimable balance ids in any spelling they arrive in

Code examples assume a `suspend` calling context and these imports:

```kotlin
import com.soneso.stellar.sdk.*
```

The decode contract, check by check, is described in [Addresses and StrKey](../addresses.md). This page walks through each strkey type and the validation rules SEP-23 requires.

## Quick Start

```kotlin
suspend fun quickExample() {
    val keyPair = KeyPair.random()
    val accountId = keyPair.getAccountId() // G...

    if (StrKey.isValidEd25519PublicKey(accountId)) {
        println("Valid account ID")
    }

    val rawPublicKey = StrKey.decodeEd25519PublicKey(accountId)
    val encoded = StrKey.encodeEd25519PublicKey(rawPublicKey)
    println(encoded == accountId) // true
}
```

## Account IDs and Secret Seeds

Account ids (`G...`) are ed25519 public keys that identify accounts on the network. Secret seeds (`S...`) are the private keys that sign transactions; never share them.

Secret seeds move as `CharArray`, not `String`: `decodeEd25519SecretSeed` and `isValidEd25519SecretSeed` take one, and `encodeEd25519SecretSeed` and `KeyPair.getSecretSeed()` return one, so the caller can zero a seed after use.

```kotlin
suspend fun accountIdsAndSeeds() {
    val keyPair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV4C3U252E2B6P6F5T3U6MM63WBSBZATAQI3EBTQ4")
    val accountId = keyPair.getAccountId()
    val secretSeed = keyPair.getSecretSeed()!!

    println(StrKey.isValidEd25519PublicKey(accountId)) // true
    println(StrKey.isValidEd25519SecretSeed(secretSeed)) // true

    val rawPublicKey = StrKey.decodeEd25519PublicKey(accountId)
    val rawSeed = StrKey.decodeEd25519SecretSeed(secretSeed)

    val encodedAccountId = StrKey.encodeEd25519PublicKey(rawPublicKey)
    val encodedSeed = StrKey.encodeEd25519SecretSeed(rawSeed)

    // Derive the account id from the raw seed
    val derived = KeyPair.fromSecretSeed(rawSeed).getAccountId()
    println(derived == accountId) // true

    // Zero the secret material once it is no longer needed
    secretSeed.fill('\u0000')
    encodedSeed.fill('\u0000')
    rawSeed.fill(0)
}
```

`KeyPair.fromAccountId` returns a public-only keypair from a `G...` address and throws the decoder's `IllegalArgumentException` for anything else. `KeyPair.fromSecretSeed` with a `String` or `CharArray` seed throws `IllegalArgumentException`: `Secret seed cannot be empty` for an empty seed, otherwise `Invalid secret seed format: ` followed by the decoder's message.

## Muxed Accounts (M...)

Muxed accounts (defined in [CAP-27](https://github.com/stellar/stellar-protocol/blob/master/core/cap-0027.md)) multiplex many virtual accounts onto a single Stellar account. Exchanges, payment processors and custodial services use them to track funds for many users without creating an on-chain account for each.

A muxed account combines:
- an ed25519 account id (`G...`), the underlying Stellar account
- a 64-bit unsigned integer id, which names the virtual sub-account

Written as one string, the pair starts with `M`.

### Creating Muxed Accounts

`MuxedAccount` takes a `G...` address with an optional `ULong` id, or parses a `G...` or `M...` address:

```kotlin
fun createMuxedAccounts() {
    val accountId = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"

    // From a G... address and an id
    val muxed = MuxedAccount(accountId, 1234567890UL)
    println(muxed.address) // MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAETFQC2K6JE

    // From an M... address
    val parsed = MuxedAccount("MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAETFQC2K6JE")
    println(parsed == muxed) // true
}
```

### Extracting Muxed Account Components

```kotlin
fun extractComponents() {
    val muxed = MuxedAccount("MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVAAAAAAAAAAAAAJLK")

    // The underlying G... address, the account on the ledger
    println(muxed.accountId) // GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ
    println(muxed.ed25519AccountId) // the same value

    // The 64-bit id, as an unsigned value
    println(muxed.id) // 9223372036854775808

    // The M... address, or the G... address when no id is set
    println(muxed.address)
}
```

The id is a `ULong`, so ids above the largest signed 64-bit value read back unchanged. Zero is an id like any other: an account carrying the id `0` is written as an `M...` address, and only an account with no id (`id == null`) is written as its `G...` address.

### Using Muxed Accounts in Transactions

Operations accept `M...` addresses as destinations and as operation source accounts. The network applies the operation to the underlying `G...` account and keeps the id for tracking. The transaction is still signed by the key behind the `G...` address.

```kotlin
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.horizon.exceptions.NetworkException

// senderSecretSeed: the secret seed of your funded testnet account, loaded from secure storage
// destinationId: the G... address of an existing testnet account
suspend fun payToMuxedAccount(senderSecretSeed: String, destinationId: String) {
    val server = HorizonServer("https://horizon-testnet.stellar.org")
    try {
        val senderKeyPair = KeyPair.fromSecretSeed(senderSecretSeed)

        // Sender with user id 100, recipient with user id 200
        val muxedSource = MuxedAccount(senderKeyPair.getAccountId(), 100UL)
        val muxedDestination = MuxedAccount(destinationId, 200UL)

        val payment = PaymentOperation(
            destination = muxedDestination.address, // M... address
            asset = AssetTypeNative,
            amount = "10"
        )
        payment.sourceAccount = muxedSource.address

        // The transaction source is loaded by its G... address
        val sourceAccount = server.loadAccount(senderKeyPair.getAccountId())
        val transaction = TransactionBuilder(sourceAccount, Network.TESTNET)
            .addOperation(payment)
            .setBaseFee(100)
            .setTimeout(180)
            .build()

        transaction.sign(senderKeyPair)
        val response = server.submitTransaction(transaction.toEnvelopeXdrBase64())
        println("Transaction hash: ${response.hash}")
    } catch (e: NetworkException) {
        println("Horizon request failed: ${e.message}")
    } catch (e: IllegalArgumentException) {
        println("Invalid secret seed or account id: ${e.message}")
    }
}
```

### Low-Level Muxed Account Encoding

The raw form of a muxed account is 40 bytes: the 32-byte ed25519 public key followed by the id as an eight-byte big-endian value.

```kotlin
fun lowLevelMuxed() {
    val muxedAccountId = "MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUQ"

    println(StrKey.isValidMed25519PublicKey(muxedAccountId)) // true

    val raw = StrKey.decodeMed25519PublicKey(muxedAccountId)
    println(raw.size) // 40

    val publicKey = raw.copyOfRange(0, 32)
    println(StrKey.encodeEd25519PublicKey(publicKey)) // GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ

    println(StrKey.encodeMed25519PublicKey(raw) == muxedAccountId) // true
}
```

## Pre-Auth TX and SHA-256 Hashes (T..., X...)

Pre-authorized transaction hashes (`T...`) authorize one specific transaction in advance. SHA-256 hashes (`X...`) are hash-locks: the signer is satisfied by revealing the preimage. Both carry 32 bytes.

```kotlin
fun hashSigners() {
    val hash = ByteArray(32) { it.toByte() } // in practice a transaction hash or SHA-256 digest

    // Pre-authorized transaction (T...)
    val preAuthTx = StrKey.encodePreAuthTx(hash)
    println(StrKey.isValidPreAuthTx(preAuthTx)) // true
    val decodedPreAuth = StrKey.decodePreAuthTx(preAuthTx)

    // SHA-256 hash-lock (X...)
    val hashX = StrKey.encodeSha256Hash(hash)
    println(StrKey.isValidSha256Hash(hashX)) // true
    val decodedHash = StrKey.decodeSha256Hash(hashX)

    // As account signers
    val preAuthSigner = SignerKey.preAuthTx(preAuthTx)
    val hashXSigner = SignerKey.hashX(hashX)
    println(preAuthSigner.encodeSignerKey() == preAuthTx) // true
}
```

## Contract IDs (C...)

Soroban contracts are identified by `C...` addresses, which carry the 32-byte contract id.

```kotlin
fun contractIds() {
    val contractId = "CA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUWDA"

    println(StrKey.isValidContract(contractId)) // true

    val raw = StrKey.decodeContract(contractId) // 32 bytes
    println(StrKey.encodeContract(raw) == contractId) // true

    // The same id as an Address, for Soroban calls
    val address = Address(contractId)
    println(address.addressType) // CONTRACT
}
```

## Signed Payloads (P...)

Signed payloads (defined in [CAP-40](https://github.com/stellar/stellar-protocol/blob/master/core/cap-0040.md)) combine an ed25519 public key with 1 to 64 bytes of payload. As a signer, a signed payload is satisfied by a signature over the payload made with that key.

`SignerKey.Ed25519SignedPayload` builds and reads them:

```kotlin
fun signedPayloads() {
    val signer = SignerKey.ed25519SignedPayload(
        "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ",
        byteArrayOf(1, 2, 3, 4, 5)
    )
    val encoded = signer.encodeSignerKey()
    println(encoded) // PA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAACQCAQDAQCQAAAAFIZQ
    println(StrKey.isValidSignedPayload(encoded)) // true

    val decoded = SignerKey.fromEncodedSignerKey(encoded) as SignerKey.Ed25519SignedPayload
    println(decoded.encodedEd25519PublicKey()) // GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ
    println(decoded.payload.size) // 5
}
```

Behind a `P...` address sit three fields: the 32-byte public key, the payload length as a four-byte big-endian value, and the payload padded with zero bytes to a multiple of four. `StrKey.decodeSignedPayload` returns those bytes as they are, and it and `StrKey.encodeSignedPayload` hold the framing to three rules:

- The declared length is 1 to 64. XDR declares the payload as `opaque payload<64>`, and an empty payload has no strkey form.
- The data is exactly 36 bytes plus the padded payload, with nothing after it.
- Every padding byte is zero, so each signed payload has exactly one strkey.

A string that breaks any of them is rejected with `IllegalArgumentException`, and `SignerKey.fromEncodedSignerKey` passes the rule that was broken through in the message. `SignerKey.Ed25519SignedPayload` applies the same length bound at construction, so an out-of-range payload fails before it can be encoded:

```kotlin
fun emptyPayload() {
    try {
        SignerKey.ed25519SignedPayload(
            "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ",
            ByteArray(0)
        )
    } catch (e: IllegalArgumentException) {
        println(e.message) // Payload must be between 1 and 64 bytes, got 0
    }
}
```

## Liquidity Pool and Claimable Balance IDs (L..., B...)

Liquidity pool ids (`L...`) carry the 32-byte pool id. Claimable balance ids (`B...`) carry 33 bytes: a one-byte type discriminant followed by the 32-byte balance hash. `CLAIMABLE_BALANCE_ID_TYPE_V0`, discriminant `0`, is the only type the XDR union declares, and both directions accept only that value.

```kotlin
fun poolsAndBalances() {
    // Liquidity pool id (L...)
    val poolId = "LA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUPJN"
    println(StrKey.isValidLiquidityPool(poolId)) // true
    val rawPool = StrKey.decodeLiquidityPool(poolId) // 32 bytes
    println(StrKey.encodeLiquidityPool(rawPool) == poolId) // true

    // Claimable balance id (B...)
    val balanceId = "BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU"
    println(StrKey.isValidClaimableBalance(balanceId)) // true
    val body = StrKey.decodeClaimableBalance(balanceId) // 33 bytes, discriminant first
    val hash = body.copyOfRange(1, body.size)

    // encodeClaimableBalance takes the 32-byte hash, the 33-byte body,
    // or the 36-byte XDR form, and writes all three as the same strkey
    println(StrKey.encodeClaimableBalance(hash) == balanceId) // true
    println(StrKey.encodeClaimableBalance(body) == balanceId) // true
}
```

Horizon reports claimable balance ids as 72 hexadecimal characters: the discriminant as four bytes, then the hash. `ClaimableBalanceId.forId` reads the `B...` strkey and every hexadecimal spelling (64, 66 or 72 characters, in either case) and names the one balance behind them:

```kotlin
fun claimableBalanceSpellings() {
    val fromStrKey = ClaimableBalanceId.forId("BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU")
    val fromHorizon = ClaimableBalanceId.forId(
        "000000003f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a"
    )
    println(fromStrKey == fromHorizon) // true

    println(fromStrKey.hashHex) // 3f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a
    println(fromStrKey.toPaddedHex()) // 000000003f0c34bf93ad0d9971d04ccc90f705511c838aad9734a4a2fb0d7a03fc7fe89a
    println(fromStrKey.toStrKey()) // BAAD6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGR4TU
}
```

A discriminant other than `0` is rejected in every width, on decode and on encode:

```kotlin
fun unknownDiscriminant() {
    val tagged = ByteArray(33)
    tagged[0] = 1 // names no claimable balance id type

    try {
        StrKey.encodeClaimableBalance(tagged)
    } catch (e: IllegalArgumentException) {
        println(e.message) // Invalid claimable balance discriminant, expected 0, got 1
    }

    // A B... strkey written with the discriminant 1
    println(StrKey.isValidClaimableBalance("BAAT6DBUX6J22DMZOHIEZTEQ64CVCHEDRKWZONFEUL5Q26QD7R76RGXACA")) // false
}
```

## Parsing Any Address

`Address` reads the five strkey kinds a Soroban `SCAddress` can name (`G`, `M`, `C`, `B`, `L`) and reports which one it got. Anything else, including a valid `S`, `T`, `X` or `P` strkey, throws `IllegalArgumentException` with the message `Unsupported address type`.

```kotlin
fun classify(input: String) {
    val address = try {
        Address(input)
    } catch (e: IllegalArgumentException) {
        println("Not an address: ${e.message}")
        return
    }

    when (address.addressType) {
        Address.AddressType.ACCOUNT -> println("Account")
        Address.AddressType.MUXED_ACCOUNT -> println("Muxed account over ${MuxedAccount(input).accountId}")
        Address.AddressType.CONTRACT -> println("Contract")
        Address.AddressType.CLAIMABLE_BALANCE -> println("Claimable balance")
        Address.AddressType.LIQUIDITY_POOL -> println("Liquidity pool")
    }
}
```

## Version Bytes Reference

The version byte is the first decoded byte. Its top five bits select the first letter, and the low three bits are zero.

| Prefix | Type | Version byte | Payload bytes | Characters | `StrKey` functions |
|--------|------|--------------|---------------|------------|--------------------|
| G | Account id (ed25519 public key) | `6 << 3` (48) | 32 | 56 | `encodeEd25519PublicKey`, `decodeEd25519PublicKey`, `isValidEd25519PublicKey` |
| S | Secret seed (ed25519) | `18 << 3` (144) | 32 | 56 | `encodeEd25519SecretSeed`, `decodeEd25519SecretSeed`, `isValidEd25519SecretSeed` |
| M | Muxed account | `12 << 3` (96) | 40 | 69 | `encodeMed25519PublicKey`, `decodeMed25519PublicKey`, `isValidMed25519PublicKey` |
| T | Pre-authorized transaction hash | `19 << 3` (152) | 32 | 56 | `encodePreAuthTx`, `decodePreAuthTx`, `isValidPreAuthTx` |
| X | SHA-256 hash | `23 << 3` (184) | 32 | 56 | `encodeSha256Hash`, `decodeSha256Hash`, `isValidSha256Hash` |
| P | Signed payload | `15 << 3` (120) | 40 to 100 | 69 to 165 | `encodeSignedPayload`, `decodeSignedPayload`, `isValidSignedPayload` |
| C | Contract id | `2 << 3` (16) | 32 | 56 | `encodeContract`, `decodeContract`, `isValidContract` |
| L | Liquidity pool id | `11 << 3` (88) | 32 | 56 | `encodeLiquidityPool`, `decodeLiquidityPool`, `isValidLiquidityPool` |
| B | Claimable balance id | `1 << 3` (8) | 33 | 58 | `encodeClaimableBalance`, `decodeClaimableBalance`, `isValidClaimableBalance` |

A strkey is the version byte, the payload and a two-byte CRC16-XModem checksum over both, written in base32 without padding. The signed payload is the only type with a range: its payload is the 36-byte header plus 1 to 64 payload bytes padded to a multiple of four.

## Error Handling

Every `decode*` function throws `IllegalArgumentException` for a string it rejects, and every `encode*` function throws `IllegalArgumentException` for bytes of a width its type does not admit (`Public key must be 32 bytes, got 20`). Every `isValid*` function returns `false` for exactly the strings its matching `decode*` rejects, and never throws.

```kotlin
fun decodeErrors() {
    try {
        StrKey.decodeEd25519PublicKey("GINVALIDADDRESS")
    } catch (e: IllegalArgumentException) {
        println(e.message) // Invalid encoded length, expected 56 characters, got 15
    }

    // One changed character breaks the checksum
    try {
        StrKey.decodeMed25519PublicKey("MA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJUAAAAAAAAAAAACJUO")
    } catch (e: IllegalArgumentException) {
        println(e.message) // Checksum invalid
    }
}
```

Use the `isValid*` functions when classifying input rather than handling one expected type. Trim user input first: surrounding whitespace is rejected, not stripped.

```kotlin
fun classifyAccount(userInput: String) {
    val input = userInput.trim()
    when {
        StrKey.isValidEd25519PublicKey(input) -> println("Account id")
        StrKey.isValidMed25519PublicKey(input) -> {
            val muxed = MuxedAccount(input)
            println("Muxed account over ${muxed.accountId} with id ${muxed.id}")
        }
        else -> println("Not an account address")
    }
}
```

The higher-level types run the same decoder. `MuxedAccount(address)` throws `Invalid address: ` followed by the input for a string that is neither a valid `G...` nor a valid `M...` address, and `MuxedAccount(accountId, id)` throws `Invalid account ID: ` followed by the input for an invalid `G...` address. `SignerKey.fromEncodedSignerKey` throws `Invalid encoded signer key: ` followed by the input for anything but a valid `G`, `T` or `X` strkey or a `P...` string of a signed payload's length, which it decodes and rejects with the decoder's message.

### Common Validation Errors

The decoder runs its checks in a fixed order and reports the first one the string fails. The table lists each rejection with an example message; together they cover every invalid case SEP-23 lists.

| Rejection | Message |
|-----------|---------|
| Wrong character count for the requested type, including `=` padding that lengthens the string | `Invalid encoded length, expected 56 characters, got 15` |
| A character outside the base32 alphabet (`A` to `Z`, `2` to `7`): lower case, `=`, whitespace, non-ASCII | `Invalid base32 encoded string` |
| A character count that leaves a partially filled trailing character | `Encoded char array has leftover character` |
| Unused trailing bits of the last character not zero | `Unused bits should be set to 0` |
| Version byte that names no strkey type | `Version byte is invalid` |
| Decoded payload size wrong for the type the version byte names | `Invalid data length, expected 33 bytes, got 32` |
| Signed payload declaring a length outside 1 to 64 | `Invalid signed payload declared length, expected between 1 and 64 bytes, got 0` |
| Signed payload whose size does not fit its declared length | `Invalid signed payload size, a declared length of 29 requires 68 bytes, got 65` |
| Signed payload with a non-zero padding byte | `Invalid signed payload padding, expected zero at index 65 after a declared length of 29, got 1` |
| Claimable balance id with a discriminant other than `0` | `Invalid claimable balance discriminant, expected 0, got 1` |
| Valid strkey of another type than the one requested | `Version byte mismatch` |
| CRC16 checksum does not match | `Checksum invalid` |

Because the character count is checked first, an empty string or a single character is a length failure like any other. See [The Decode Contract](../addresses.md#the-decode-contract) for the reasoning behind each check.

## Related Specifications

- [SEP-05: Key Derivation](sep-05.md) - Deriving keypairs from mnemonic phrases
- [SEP-10: Web Authentication](sep-10.md) - Uses account ids for authentication challenges
- [SEP-45: Web Authentication for Contract Accounts](sep-45.md) - Authentication for Soroban contract accounts (`C...` addresses)
- [SEP-51: XDR-JSON](sep-51.md) - Renders keys, addresses and ids in the JSON form of XDR values as strkeys

**Specification**: [SEP-23: Strkeys](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0023.md)

**Implementation**: `com.soneso.stellar.sdk.StrKey`, `com.soneso.stellar.sdk.MuxedAccount`, `com.soneso.stellar.sdk.SignerKey`, `com.soneso.stellar.sdk.Address`, `com.soneso.stellar.sdk.ClaimableBalanceId`

**Last Updated**: 2026-09-28
