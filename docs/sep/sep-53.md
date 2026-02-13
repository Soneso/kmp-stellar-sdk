# SEP-53: Sign and Verify Messages

SEP-53 defines how to sign and verify arbitrary messages with Stellar Ed25519 keypairs. A domain separation prefix prevents message signatures from being confused with transaction signatures.

**Use Cases**:
- Prove ownership of a Stellar keypair without submitting a transaction
- Sign off-chain agreements, attestations, or terms of service
- Authenticate users in off-chain systems using their Stellar key
- Produce portable proofs that can be verified by any Stellar SDK

## Quick Start

```kotlin
import com.soneso.stellar.sdk.KeyPair

suspend fun quickExample() {
    val keypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")

    // Sign a message
    val signature = keypair.signMessage("Hello, Stellar!")

    // Verify the signature (works with a public-only keypair too)
    val isValid = keypair.verifyMessage("Hello, Stellar!", signature)
    println("Valid: $isValid") // true
}
```

## Signing Messages

### String Messages

Pass a UTF-8 string directly. The SDK handles encoding internally.

```kotlin
suspend fun signStringMessage() {
    val keypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")

    val signature = keypair.signMessage("I agree to the terms of service.")
    println("Signature: ${signature.size} bytes") // 64 bytes
}
```

### Binary Messages

Pass raw bytes for non-text payloads such as file hashes or protocol buffers.

```kotlin
suspend fun signBinaryMessage() {
    val keypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")

    val binaryData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    val signature = keypair.signMessage(binaryData)
    println("Signature: ${signature.size} bytes") // 64 bytes
}
```

Both overloads produce identical results when the string and byte array represent the same UTF-8 content:

```kotlin
suspend fun equivalentSignatures() {
    val keypair = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")

    val fromString = keypair.signMessage("Hello")
    val fromBytes = keypair.signMessage("Hello".encodeToByteArray())
    println("Equal: ${fromString.contentEquals(fromBytes)}") // true
}
```

## Verifying Messages

### String Messages

```kotlin
suspend fun verifyStringMessage() {
    val verifier = KeyPair.fromAccountId("GCFMEYRERP6OTOF6GI2GQ2QLHFHNE7ZEPITKFCJ7GNFPEV3YKK6RBOA")
    val signature: ByteArray = getSignatureFromSomewhere()

    val isValid = verifier.verifyMessage("I agree to the terms of service.", signature)
    println("Valid: $isValid")
}
```

### Binary Messages

```kotlin
suspend fun verifyBinaryMessage() {
    val verifier = KeyPair.fromAccountId("GCFMEYRERP6OTOF6GI2GQ2QLHFHNE7ZEPITKFCJ7GNFPEV3YKK6RBOA")
    val binaryData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
    val signature: ByteArray = getSignatureFromSomewhere()

    val isValid = verifier.verifyMessage(binaryData, signature)
    println("Valid: $isValid")
}
```

### Public-Only Keypair

Verification requires only the public key. A keypair created with `fromAccountId` (no secret seed) can verify signatures:

```kotlin
suspend fun verifyWithPublicOnlyKeypair() {
    // Signer has the secret seed
    val signer = KeyPair.fromSecretSeed("SCZANGBA5YHTNYVVV3C7CAZMTQDBJHJG6C34CBOEPVCBWVISXZ3DQHKP")
    val message = "Verify me"
    val signature = signer.signMessage(message)

    // Verifier only needs the account ID (public key)
    val verifier = KeyPair.fromAccountId(signer.getAccountId())
    val isValid = verifier.verifyMessage(message, signature)
    println("Valid: $isValid") // true
}
```

## Cross-SDK Interoperability

Signatures produced by this SDK are compatible with any implementation that follows SEP-53. The same test vectors pass across the Java, Python, and Flutter Stellar SDKs.

```kotlin
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
suspend fun crossSdkVerification() {
    // Signature produced by any SEP-53-compliant SDK
    val accountId = "GBXFXNDLV4LSWA4VB7YIL5GBD7BVNR22SGBTDKMO2SBZZHDXSKZYCP7L"
    val message = "Hello, World!"
    val signatureBase64 = "fO5dbYhXUhBMhe6kId/cuVq/AfEnHRHEvsP8vXh03M1uLpi5e46yO2Q8rEBzu3feXQewcQE5GArp88u6ePK6BA=="
    val signature = kotlin.io.encoding.Base64.decode(signatureBase64)

    val verifier = KeyPair.fromAccountId(accountId)
    val isValid = verifier.verifyMessage(message, signature)
    println("Valid: $isValid") // true
}
```

## Error Handling

### Signing Without a Private Key

Calling `signMessage` on a public-only keypair throws `IllegalStateException`.

```kotlin
suspend fun handleSigningError() {
    val publicOnly = KeyPair.fromAccountId("GCFMEYRERP6OTOF6GI2GQ2QLHFHNE7ZEPITKFCJ7GNFPEV3YKK6RBOA")

    // Check before calling signMessage
    if (publicOnly.canSign()) {
        val signature = publicOnly.signMessage("test")
    } else {
        println("Cannot sign: keypair has no secret seed")
    }
}
```

### Invalid Signatures

`verifyMessage` returns `false` for invalid, malformed, or truncated signatures. It never throws for bad input.

```kotlin
suspend fun handleVerificationFailures() {
    val verifier = KeyPair.fromAccountId("GBXFXNDLV4LSWA4VB7YIL5GBD7BVNR22SGBTDKMO2SBZZHDXSKZYCP7L")
    val message = "Hello, World!".encodeToByteArray()

    // Wrong signature bytes
    val wrongSignature = ByteArray(64) { 0x00 }
    println(verifier.verifyMessage(message, wrongSignature)) // false

    // Truncated signature
    val truncated = ByteArray(32) { 0x42 }
    println(verifier.verifyMessage(message, truncated)) // false

    // Empty signature
    println(verifier.verifyMessage(message, ByteArray(0))) // false
}
```

## Security Considerations

### Domain Separation Prefix

Every message is prefixed with `"Stellar Signed Message:\n"` before hashing. This prevents a signed message from being replayed as a signed transaction or vice versa.

### Key Ownership vs Account Control

A valid message signature proves possession of the Ed25519 private key. It does not prove control of the Stellar account, because accounts can have multiple signers with weighted thresholds. Do not use message signatures as proof of account authority in multi-signature scenarios.

### User Confirmation Before Signing

Wallets and applications should display the message content to the user and obtain explicit confirmation before signing. This is especially important for binary or opaque payloads where the content is not human-readable.

## API Reference

| Method | Parameters | Return | Throws | Description |
|--------|-----------|--------|--------|-------------|
| `signMessage(message: ByteArray)` | Raw bytes to sign | `ByteArray` (64 bytes) | `IllegalStateException` if no private key | Sign binary message per SEP-53 |
| `signMessage(message: String)` | UTF-8 string to sign | `ByteArray` (64 bytes) | `IllegalStateException` if no private key | Sign string message per SEP-53 |
| `verifyMessage(message: ByteArray, signature: ByteArray)` | Raw bytes + signature | `Boolean` | -- | Verify binary message signature |
| `verifyMessage(message: String, signature: ByteArray)` | UTF-8 string + signature | `Boolean` | -- | Verify string message signature |

All four methods are `suspend` functions on the `KeyPair` class. Signing requires a keypair with a private key (created via `fromSecretSeed` or `random`). Verification works with any keypair, including public-only keypairs created via `fromAccountId`.

**Specification**: [SEP-53: Sign and Verify Messages](https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0053.md)

**Implementation**: `com.soneso.stellar.sdk.KeyPair`

**Last Updated**: 2026-02-13
