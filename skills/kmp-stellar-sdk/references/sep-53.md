# SEP-53: Sign/Verify Messages

**Purpose:** Sign and verify arbitrary messages using Stellar Ed25519 keypairs without on-chain transactions.
**Prerequisites:** None
**SDK Class:** `KeyPair`

## Overview

SEP-53 enables proof-of-ownership and off-chain authentication by defining a standard signing procedure for arbitrary messages. The four methods are instance methods on `KeyPair`:

| Method | Input | Returns | Throws |
|--------|-------|---------|--------|
| `signMessage(ByteArray)` | Raw bytes | `ByteArray` (64-byte signature) | `IllegalStateException` if no private key |
| `signMessage(String)` | UTF-8 string | `ByteArray` (64-byte signature) | `IllegalStateException` if no private key |
| `verifyMessage(ByteArray, ByteArray)` | Message bytes + signature | `Boolean` | Never throws |
| `verifyMessage(String, ByteArray)` | String + signature | `Boolean` | Never throws |

All four methods are `suspend` functions. `canSign()` is not a suspend function.

**Signing always requires a private key.** Verification only requires the public key — `KeyPair.fromAccountId()` is sufficient.

## Quick Start

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
suspend fun main() {
    // Sign with a full keypair (has private key)
    val signer = KeyPair.fromSecretSeed("SAKICEVQLYWGSOJS4WW7HZJWAHZVEEBS527LHK5V4MLJALYKICQCJXMW")
    val signature: ByteArray = signer.signMessage("Hello, World!")

    // Transmit the signature as base64 or hex
    val base64Sig = Base64.encode(signature)
    val hexSig = bytesToHex(signature) // see Signature Serialization section

    // Verify with public key only (no private key needed)
    val verifier = KeyPair.fromAccountId(signer.getAccountId())
    val valid: Boolean = verifier.verifyMessage("Hello, World!", signature)
    println("Valid: $valid") // true
}
```

## Signing Messages

### Sign a String

```kotlin
import com.soneso.stellar.sdk.KeyPair

suspend fun signString() {
    val keyPair = KeyPair.fromSecretSeed("SAKICEVQLYWGSOJS4WW7HZJWAHZVEEBS527LHK5V4MLJALYKICQCJXMW")

    // Sign a UTF-8 string — SDK handles UTF-8 encoding internally
    val signature: ByteArray = keyPair.signMessage("Hello, World!")

    // Encode for transmission (SEP-53 does not mandate a specific encoding)
    // See Signature Serialization section for encoding helpers
}
```

### Sign Binary Data

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalEncodingApi::class)
suspend fun signBinary() {
    val keyPair = KeyPair.fromSecretSeed("SAKICEVQLYWGSOJS4WW7HZJWAHZVEEBS527LHK5V4MLJALYKICQCJXMW")

    // Sign raw bytes directly
    val messageBytes = byteArrayOf(0xDB.toByte(), 0x36, 0x43, 0x3F)
    val signature: ByteArray = keyPair.signMessage(messageBytes)

    // Sign a JSON payload as bytes
    val json = """{"timestamp":1234567890,"action":"login"}"""
    val jsonSig: ByteArray = keyPair.signMessage(json.encodeToByteArray())
}
```

### Check Before Signing

```kotlin
import com.soneso.stellar.sdk.KeyPair

suspend fun checkBeforeSigning() {
    val keyPair = KeyPair.fromSecretSeed("SAKICEVQLYWGSOJS4WW7HZJWAHZVEEBS527LHK5V4MLJALYKICQCJXMW")

    // canSign() is NOT a suspend function — returns true only if keypair has a private key
    if (keyPair.canSign()) {
        val signature = keyPair.signMessage("my message")
    }

    // KeyPair.fromAccountId() creates a public-key-only keypair — canSign() returns false
    val publicOnly = KeyPair.fromAccountId("GBXFXNDLV4LSWA4VB7YIL5GBD7BVNR22SGBTDKMO2SBZZHDXSKZYCP7L")
    println(publicOnly.canSign()) // false — signMessage() would throw IllegalStateException
}
```

## Verifying Messages

Verification only requires the public key. Use `KeyPair.fromAccountId()` to create a verify-only keypair:

### Verify a String Signature

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
suspend fun verifyString() {
    // Receiver side: only needs the signer's account ID
    val verifier = KeyPair.fromAccountId("GBXFXNDLV4LSWA4VB7YIL5GBD7BVNR22SGBTDKMO2SBZZHDXSKZYCP7L")

    // Decode signature from the transport encoding used by the sender
    val receivedBase64 = "fO5dbYhXUhBMhe6kId/cuVq/AfEnHRHEvsP8vXh03M1uLpi5e46yO2Q8rEBzu3feXQewcQE5GArp88u6ePK6BA=="
    val signature: ByteArray = Base64.decode(receivedBase64)
    // or from hex: val signature = hexToBytes(receivedHex)

    val valid = verifier.verifyMessage("Hello, World!", signature)
    if (valid) {
        println("Authenticated: message is from the expected signer")
    } else {
        println("Verification failed")
    }
}
```

### Verify Binary Data

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
suspend fun verifyBinary() {
    val verifier = KeyPair.fromAccountId("GBXFXNDLV4LSWA4VB7YIL5GBD7BVNR22SGBTDKMO2SBZZHDXSKZYCP7L")

    val message = Base64.decode("2zZDP1sa1BVBfLP7TeeMk3sUbaxAkUhBhDiNdrksaFo=")
    val signature = hexToBytes(
        "540d7eee179f370bf634a49c1fa9fe4a58e3d7990b0207be336c04edfcc539ff" +
        "8bd0c31bb2c0359b07c9651cb2ae104e4504657b5d17d43c69c7e50e23811b0d"
    )

    val valid = verifier.verifyMessage(message, signature)
}
```

## Signature Serialization

Signatures are 64-byte `ByteArray`. SEP-53 does not mandate a specific string encoding — use whatever the application requires.

Kotlin has no built-in hex utility on all platforms. Use `@OptIn(ExperimentalStdlibApi::class)` with `toHexString()` / `hexToByteArray()` (Kotlin 1.9+), or the manual helpers shown in the SDK tests:

```kotlin
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Manual hex helpers — safe on all KMP targets
fun bytesToHex(bytes: ByteArray): String {
    val chars = "0123456789abcdef".toCharArray()
    val result = StringBuilder(bytes.size * 2)
    for (byte in bytes) {
        val v = byte.toInt() and 0xFF
        result.append(chars[v shr 4])
        result.append(chars[v and 0x0F])
    }
    return result.toString()
}

fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(hex.length / 2) { i ->
        val high = hex[i * 2].digitToInt(16)
        val low  = hex[i * 2 + 1].digitToInt(16)
        ((high shl 4) or low).toByte()
    }
}

@OptIn(ExperimentalEncodingApi::class)
suspend fun serializationExample() {
    val keyPair = KeyPair.fromSecretSeed("SAKICEVQLYWGSOJS4WW7HZJWAHZVEEBS527LHK5V4MLJALYKICQCJXMW")
    val signature = keyPair.signMessage("Hello, World!")

    // Encode for storage or transmission
    val base64Sig = Base64.encode(signature)   // base64 standard encoding
    val hexSig    = bytesToHex(signature)      // lowercase hex

    // Decode when verifying
    val fromBase64 = Base64.decode(base64Sig)
    val fromHex    = hexToBytes(hexSig)
}
```

## Cross-SDK Interoperability

Signatures produced by the KMP SDK are compatible with other Stellar SDKs (Java, Flutter, Python, etc.) implementing SEP-53. To verify a signature received from another SDK:

```kotlin
import com.soneso.stellar.sdk.KeyPair
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
suspend fun crossSdkVerify() {
    // Signature produced by a different Stellar SDK, received as base64
    val signatureFromOtherSdk = "CDU265Xs8y3OWbB/56H9jPgUss5G9A0qFuTqH2zs2YDgTm+++dIfmAEceFqB7bhfN3am59lCtDXrCtwH2k1GBA=="

    val verifier = KeyPair.fromAccountId("GBXFXNDLV4LSWA4VB7YIL5GBD7BVNR22SGBTDKMO2SBZZHDXSKZYCP7L")
    val signature = Base64.decode(signatureFromOtherSdk)

    val valid = verifier.verifyMessage("こんにちは、世界！", signature)
    println("Cross-SDK verification: $valid") // true
}
```

## Protocol Details

SEP-53 defines the signing procedure as:

1. **Prefix:** Prepend `"Stellar Signed Message:\n"` (UTF-8 bytes) to the message
2. **Hash:** SHA-256 hash the concatenated payload
3. **Sign:** Ed25519 sign the hash with the private key

```
signature = Ed25519.sign(privateKey, SHA256("Stellar Signed Message:\n" + message))
```

The prefix provides domain separation — message signatures cannot be confused with transaction signatures even if the raw bytes happen to match a transaction hash.

### Spec Test Vectors

These vectors from the SEP-53 specification can be used to validate interoperability:

```
Secret seed: SAKICEVQLYWGSOJS4WW7HZJWAHZVEEBS527LHK5V4MLJALYKICQCJXMW
Account ID:  GBXFXNDLV4LSWA4VB7YIL5GBD7BVNR22SGBTDKMO2SBZZHDXSKZYCP7L

Message: "Hello, World!" (ASCII)
Signature (hex): 7cee5d6d885752104c85eea421dfdcb95abf01f1271d11c4bec3fcbd7874dccd
                 6e2e98b97b8eb23b643cac4073bb77de5d07b0710139180ae9f3cbba78f2ba04

Message: "こんにちは、世界！" (UTF-8)
Signature (hex): 083536eb95ecf32dce59b07fe7a1fd8cf814b2ce46f40d2a16e4ea1f6cecd980
                 e04e6fbef9d21f98011c785a81edb85f3776a6e7d942b435eb0adc07da4d4604
```

## Error Handling

```kotlin
import com.soneso.stellar.sdk.KeyPair

suspend fun errorHandling() {
    val publicOnly = KeyPair.fromAccountId("GBXFXNDLV4LSWA4VB7YIL5GBD7BVNR22SGBTDKMO2SBZZHDXSKZYCP7L")

    // Signing with a public-only keypair throws IllegalStateException (not Exception)
    try {
        val sig = publicOnly.signMessage("Hello")
    } catch (e: IllegalStateException) {
        println("Cannot sign: ${e.message}")
        // "KeyPair does not contain secret key. Use KeyPair.fromSecretSeed..."
    }

    // Verification never throws — returns false on failure instead
    val result = publicOnly.verifyMessage("Hello", ByteArray(64)) // false, not an exception
}
```

## Common Pitfalls

```kotlin
// WRONG: using signMessageString — this method does NOT exist in the KMP SDK
val sig = keyPair.signMessageString("Hello") // compilation error

// CORRECT: use the overloaded signMessage(String) suspend function
val sig = keyPair.signMessage("Hello")
```

```kotlin
// WRONG: using verifyMessageString — this method does NOT exist in the KMP SDK
val valid = verifier.verifyMessageString("Hello", signature) // compilation error

// CORRECT: use the overloaded verifyMessage(String, ByteArray) suspend function
val valid = verifier.verifyMessage("Hello", signature)
```

```kotlin
// WRONG: passing a base64 string directly as the signature bytes
val bad = verifier.verifyMessage("Hello", "abc123".encodeToByteArray())

// CORRECT: decode the base64 or hex string to ByteArray first
@OptIn(ExperimentalEncodingApi::class)
val sig = Base64.decode(base64SignatureString)
val good = verifier.verifyMessage("Hello", sig)
```

```kotlin
// WRONG: using sign() directly for message signing (bypasses the SEP-53 prefix/hash)
val sig = keyPair.sign("Hello".encodeToByteArray())

// CORRECT: use signMessage() which applies the SEP-53 prefix and SHA-256 hash
val sig = keyPair.signMessage("Hello")
```

```kotlin
// WRONG: assuming verifyMessage throws on invalid signature
try {
    keyPair.verifyMessage("msg", badSignature) // does NOT throw
} catch (e: Exception) { ... }

// CORRECT: check the return value
val valid = keyPair.verifyMessage("msg", badSignature)
if (!valid) { /* handle invalid signature */ }
```

```kotlin
// WRONG: calling signMessage or verifyMessage outside a coroutine/suspend context
// They are suspend functions and must be called from a coroutine or another suspend function
val sig = keyPair.signMessage("Hello") // compile error outside suspend context

// CORRECT: call from a suspend function or launch a coroutine
suspend fun doSign() {
    val sig = keyPair.signMessage("Hello")
}
// or in tests:
@Test
fun testSign() = runTest {
    val sig = keyPair.signMessage("Hello")
}
```
