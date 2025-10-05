# Auth Helper Implementation Plan for Kotlin Multiplatform Stellar SDK

**Date:** October 5, 2025
**Feature:** Soroban Authorization Entry Signing
**Priority:** CRITICAL
**Estimated Effort:** 3 working days (~22.5 hours)

---

## 1. Overview

### What is the Auth Helper?

The Auth helper class (`Auth`) provides utilities for signing Soroban contract authorization entries. It is a critical component for smart contract interactions that require user authentication and authorization.

### Why is it needed?

When interacting with Soroban smart contracts, operations may require authorization from specific accounts. The Auth helper:
- Signs authorization entries that grant permission to execute contract invocations
- Constructs the proper hash preimage for Soroban authorization signatures
- Validates signatures against authorization entries
- Supports both direct KeyPair signing and custom signer implementations
- Enables time-bound authorization through ledger sequence expiration

### Primary Use Cases

1. **Authorizing Existing Entries**: Sign simulation-returned authorization entries
2. **Building New Authorizations**: Create authorization entries from scratch for specific invocations
3. **Multi-signature Support**: Enable custom signing logic through the Signer interface
4. **Network-specific Signing**: Incorporate network passphrase into signatures for replay protection

---

## 2. API Design

### Public Class Structure

```kotlin
package com.stellar.sdk

/**
 * Helper class for signing Soroban authorization entries.
 *
 * This class provides methods to authorize smart contract invocations by:
 * - Signing existing authorization entries (typically from transaction simulation)
 * - Building new authorization entries from scratch
 * - Supporting custom signing logic through the [Signer] interface
 *
 * ## Usage Examples
 *
 * ### Authorize an existing entry with a KeyPair
 * ```kotlin
 * val entry = SorobanAuthorizationEntryXdr.decode(...)
 * val signer = KeyPair.fromSecretSeed("S...")
 * val network = Network.TESTNET
 * val validUntilLedgerSeq = 1000000L
 *
 * val signedEntry = Auth.authorizeEntry(
 *     entry = entry,
 *     signer = signer,
 *     validUntilLedgerSeq = validUntilLedgerSeq,
 *     network = network
 * )
 * ```
 *
 * ### Build a new authorization from scratch
 * ```kotlin
 * val invocation = SorobanAuthorizedInvocationXdr(...)
 * val signer = KeyPair.fromSecretSeed("S...")
 * val network = Network.TESTNET
 * val validUntilLedgerSeq = 1000000L
 *
 * val authEntry = Auth.authorizeInvocation(
 *     signer = signer,
 *     validUntilLedgerSeq = validUntilLedgerSeq,
 *     invocation = invocation,
 *     network = network
 * )
 * ```
 *
 * ### Use a custom signer
 * ```kotlin
 * val customSigner = object : Auth.Signer {
 *     override suspend fun sign(preimage: HashIDPreimageXdr): Auth.Signature {
 *         val payload = Util.hash(preimage.toXdrByteArray())
 *         // Custom signing logic (e.g., hardware wallet)
 *         val signature = myCustomSigningDevice.sign(payload)
 *         return Auth.Signature(publicKey = "G...", signature = signature)
 *     }
 * }
 *
 * val signedEntry = Auth.authorizeEntry(
 *     entry = entry,
 *     signer = customSigner,
 *     validUntilLedgerSeq = validUntilLedgerSeq,
 *     network = network
 * )
 * ```
 *
 * @see <a href="https://developers.stellar.org/docs/learn/encyclopedia/security/authorization/">Smart Contract Authorization</a>
 */
object Auth {

    /**
     * Authorizes an existing authorization entry using a KeyPair.
     *
     * @param entry The base64 encoded unsigned Soroban authorization entry
     * @param signer The KeyPair to sign with (must correspond to the address in the entry)
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if entry cannot be decoded or signature verification fails
     */
    suspend fun authorizeEntry(
        entry: String,
        signer: KeyPair,
        validUntilLedgerSeq: Long,
        network: Network
    ): SorobanAuthorizationEntryXdr

    /**
     * Authorizes an existing authorization entry using a KeyPair.
     *
     * @param entry The unsigned Soroban authorization entry
     * @param signer The KeyPair to sign with (must correspond to the address in the entry)
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if signature verification fails
     */
    suspend fun authorizeEntry(
        entry: SorobanAuthorizationEntryXdr,
        signer: KeyPair,
        validUntilLedgerSeq: Long,
        network: Network
    ): SorobanAuthorizationEntryXdr

    /**
     * Authorizes an existing authorization entry using a custom Signer.
     *
     * @param entry The base64 encoded unsigned Soroban authorization entry
     * @param signer A function that signs the hash of a HashIDPreimage
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if entry cannot be decoded or signature verification fails
     */
    suspend fun authorizeEntry(
        entry: String,
        signer: Signer,
        validUntilLedgerSeq: Long,
        network: Network
    ): SorobanAuthorizationEntryXdr

    /**
     * Authorizes an existing authorization entry using a custom Signer.
     *
     * @param entry The unsigned Soroban authorization entry
     * @param signer A function that signs the hash of a HashIDPreimage
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if signature verification fails
     */
    suspend fun authorizeEntry(
        entry: SorobanAuthorizationEntryXdr,
        signer: Signer,
        validUntilLedgerSeq: Long,
        network: Network
    ): SorobanAuthorizationEntryXdr

    /**
     * Builds and authorizes a new entry from scratch using a KeyPair.
     *
     * @param signer The KeyPair to sign with
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param invocation The invocation tree being authorized (typically from simulation)
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if authorization fails
     */
    suspend fun authorizeInvocation(
        signer: KeyPair,
        validUntilLedgerSeq: Long,
        invocation: SorobanAuthorizedInvocationXdr,
        network: Network
    ): SorobanAuthorizationEntryXdr

    /**
     * Builds and authorizes a new entry from scratch using a custom Signer.
     *
     * @param signer A function that signs the hash of a HashIDPreimage
     * @param publicKey The public identity of the signer (G... address)
     * @param validUntilLedgerSeq The exclusive future ledger sequence until which this is valid
     * @param invocation The invocation tree being authorized (typically from simulation)
     * @param network The network (incorporated into the signature for replay protection)
     * @return A signed Soroban authorization entry
     * @throws IllegalArgumentException if authorization fails
     */
    suspend fun authorizeInvocation(
        signer: Signer,
        publicKey: String,
        validUntilLedgerSeq: Long,
        invocation: SorobanAuthorizedInvocationXdr,
        network: Network
    ): SorobanAuthorizationEntryXdr

    /**
     * A signature consisting of a public key and a signature.
     *
     * @property publicKey The signer's account ID (G... address)
     * @property signature The 64-byte Ed25519 signature
     */
    data class Signature(
        val publicKey: String,
        val signature: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Signature
            if (publicKey != other.publicKey) return false
            if (!signature.contentEquals(other.signature)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = publicKey.hashCode()
            result = 31 * result + signature.contentHashCode()
            return result
        }
    }

    /**
     * An interface for signing a HashIDPreimage to produce a signature.
     *
     * This enables custom signing logic such as:
     * - Hardware wallet signing
     * - Multi-signature coordination
     * - Custom key management systems
     */
    fun interface Signer {
        /**
         * Signs a HashIDPreimage and returns the signature.
         *
         * The implementation should:
         * 1. Convert the preimage to XDR bytes
         * 2. Hash the bytes with SHA-256
         * 3. Sign the hash with Ed25519
         * 4. Return the public key and signature
         *
         * @param preimage The hash preimage to sign
         * @return The signature containing public key and signature bytes
         */
        suspend fun sign(preimage: HashIDPreimageXdr): Signature
    }
}
```

---

## 3. Key Features

### 3.1 Authorization Entry Signing

- Accept authorization entries in both XDR object and base64 string formats
- Support signing with KeyPair or custom Signer interface
- Clone entries to avoid mutating input parameters
- Validate credential types (only sign SOROBAN_CREDENTIALS_ADDRESS)
- Set signature expiration ledger sequence
- Construct and sign the hash preimage
- Verify signatures after signing
- Build the signature SCVal structure per Stellar account contract spec

### 3.2 Hash Preimage Construction

- Create HashIDPreimage with ENVELOPE_TYPE_SOROBAN_AUTHORIZATION
- Include network ID (SHA-256 hash of network passphrase)
- Include nonce from address credentials
- Include root invocation tree
- Include signature expiration ledger sequence
- Convert to XDR bytes and hash with SHA-256

### 3.3 Signature Creation and Verification

- Support both direct KeyPair signing and custom Signer interface
- Create decorated signatures with public key and signature bytes
- Verify signatures match the payload before returning
- Build signature SCVal as a map with "public_key" and "signature" fields
- Wrap signature in a vector as required by Stellar account contract

### 3.4 Building New Authorization Entries

- Generate cryptographically secure random nonce
- Create SorobanAddressCredentials from public key
- Build SorobanAuthorizationEntry structure
- Sign the newly created entry
- Return fully authorized entry ready for transaction inclusion

---

## 4. Implementation Details

### 4.1 Core Authorization Logic

```kotlin
private suspend fun authorizeEntryInternal(
    entry: SorobanAuthorizationEntryXdr,
    signer: Signer,
    validUntilLedgerSeq: Long,
    network: Network
): SorobanAuthorizationEntryXdr {
    // 1. Clone the entry to avoid mutation
    val clone = cloneEntry(entry)

    // 2. Check if credentials are address-based (only type we can sign)
    if (clone.credentials !is SorobanCredentialsXdr.Address) {
        return clone
    }

    // 3. Update signature expiration in credentials
    val addressCredentials = (clone.credentials as SorobanCredentialsXdr.Address).value
    addressCredentials.signatureExpirationLedger = Uint32Xdr(validUntilLedgerSeq.toUInt())

    // 4. Build the hash preimage
    val preimage = buildHashIDPreimage(
        networkId = network.networkId(),
        nonce = addressCredentials.nonce,
        invocation = clone.rootInvocation,
        signatureExpirationLedger = addressCredentials.signatureExpirationLedger
    )

    // 5. Sign the preimage
    val signature = signer.sign(preimage)

    // 6. Verify the signature
    verifySignature(preimage, signature)

    // 7. Build the signature SCVal and update credentials
    val signatureScVal = buildSignatureScVal(signature)
    addressCredentials.signature = signatureScVal

    return clone
}
```

### 4.2 Hash Preimage Construction

```kotlin
private fun buildHashIDPreimage(
    networkId: ByteArray,
    nonce: Int64Xdr,
    invocation: SorobanAuthorizedInvocationXdr,
    signatureExpirationLedger: Uint32Xdr
): HashIDPreimageXdr {
    return HashIDPreimageXdr.SorobanAuthorization(
        HashIDPreimageSorobanAuthorizationXdr(
            networkID = HashXdr(networkId),
            nonce = nonce,
            signatureExpirationLedger = signatureExpirationLedger,
            invocation = invocation
        )
    )
}
```

### 4.3 Signature Verification

```kotlin
private suspend fun verifySignature(
    preimage: HashIDPreimageXdr,
    signature: Signature
) {
    val payload = Util.hash(preimage.toXdrByteArray())
    val keyPair = KeyPair.fromAccountId(signature.publicKey)

    if (!keyPair.verify(payload, signature.signature)) {
        throw IllegalArgumentException("Signature does not match payload")
    }
}
```

### 4.4 Signature SCVal Construction

Per Stellar account contract specification:
- https://developers.stellar.org/docs/learn/encyclopedia/contract-development/contract-interactions/stellar-transaction#stellar-account-signatures
- Structure: `Vec<Map<Symbol, Bytes>>`
- Map contains: `{ "public_key": Bytes, "signature": Bytes }`

```kotlin
private fun buildSignatureScVal(signature: Signature): SCValXdr {
    val publicKeyBytes = KeyPair.fromAccountId(signature.publicKey).getPublicKey()

    val sigMap = linkedMapOf(
        Scv.toSymbol("public_key") to Scv.toBytes(publicKeyBytes),
        Scv.toSymbol("signature") to Scv.toBytes(signature.signature)
    )

    return Scv.toVec(listOf(Scv.toMap(sigMap)))
}
```

### 4.5 Random Nonce Generation

```kotlin
private suspend fun generateNonce(): Long {
    // Use the existing crypto infrastructure for secure random generation
    val randomBytes = getEd25519Crypto().generatePrivateKey() // 32 bytes

    // Convert first 8 bytes to Long
    var nonce = 0L
    for (i in 0..7) {
        nonce = (nonce shl 8) or (randomBytes[i].toLong() and 0xFF)
    }

    return nonce
}
```

### 4.6 Entry Cloning

```kotlin
private fun cloneEntry(entry: SorobanAuthorizationEntryXdr): SorobanAuthorizationEntryXdr {
    return try {
        val bytes = entry.toXdrByteArray()
        SorobanAuthorizationEntryXdr.decode(XdrReader(bytes))
    } catch (e: Exception) {
        throw IllegalArgumentException("Unable to clone SorobanAuthorizationEntry", e)
    }
}
```

---

## 5. Dependencies

### 5.1 Existing SDK Classes

- **KeyPair**: For signing and signature verification
- **Network**: For network ID (passphrase hash)
- **Address**: For converting between address formats and XDR
- **Scv**: For building SCVal structures (map, vec, symbol, bytes)
- **Util**: For SHA-256 hashing

### 5.2 XDR Types (Already Available)

- `SorobanAuthorizationEntryXdr`
- `SorobanCredentialsXdr`
- `SorobanAddressCredentialsXdr`
- `SorobanAuthorizedInvocationXdr`
- `HashIDPreimageXdr`
- `HashIDPreimageSorobanAuthorizationXdr`
- `EnvelopeTypeXdr`
- `HashXdr`
- `Int64Xdr`
- `Uint32Xdr`
- `Uint64Xdr`
- `SCValXdr`
- `SCAddressXdr`

### 5.3 Crypto Infrastructure

- `Ed25519Crypto.generatePrivateKey()`: For secure random nonce generation
- `Util.hash()`: For SHA-256 hashing

---

## 6. File Structure

```
stellar-sdk/src/
├── commonMain/kotlin/com/stellar/sdk/
│   ├── Auth.kt                          # New file - Auth helper class
│   ├── KeyPair.kt                       # Existing - used for signing
│   ├── Network.kt                       # Existing - used for network ID
│   ├── Address.kt                       # Existing - used for address conversion
│   ├── Util.kt                          # Existing - used for hashing
│   └── scval/
│       └── Scv.kt                       # Existing - used for SCVal construction
└── commonTest/kotlin/com/stellar/sdk/
    └── AuthTest.kt                      # New file - comprehensive tests
```

---

## 7. Testing Strategy

### 7.1 Unit Tests

Create `AuthTest.kt` with the following test cases:

#### Basic Functionality Tests

```kotlin
class AuthTest {

    @Test
    fun `authorizeEntry with KeyPair signs correctly`() = runTest {
        // Test basic signing with KeyPair
    }

    @Test
    fun `authorizeEntry with base64 string decodes and signs`() = runTest {
        // Test base64 string input
    }

    @Test
    fun `authorizeEntry with custom Signer works`() = runTest {
        // Test custom Signer interface
    }

    @Test
    fun `authorizeInvocation creates and signs new entry`() = runTest {
        // Test building from scratch
    }

    @Test
    fun `authorizeInvocation with custom Signer creates entry`() = runTest {
        // Test building with custom signer
    }
}
```

#### Signature Verification Tests

```kotlin
@Test
fun `signature verification passes for valid signatures`() = runTest {
    // Test that valid signatures are accepted
}

@Test
fun `signature verification fails for invalid signatures`() = runTest {
    // Test that invalid signatures are rejected
}

@Test
fun `signature structure matches Stellar account contract spec`() = runTest {
    // Verify the signature SCVal structure is correct
}
```

#### Edge Cases Tests

```kotlin
@Test
fun `returns unchanged entry for non-address credentials`() = runTest {
    // Test that SOURCE_ACCOUNT credentials are returned unchanged
}

@Test
fun `handles entry cloning correctly`() = runTest {
    // Test that original entry is not mutated
}

@Test
fun `throws on invalid base64 entry`() = runTest {
    // Test error handling for malformed input
}

@Test
fun `throws on signature verification failure`() = runTest {
    // Test error handling when signature doesn't match
}

@Test
fun `generates unique nonces`() = runTest {
    // Test that nonce generation produces unique values
}
```

#### Network-Specific Tests

```kotlin
@Test
fun `signature includes correct network ID for TESTNET`() = runTest {
    // Test TESTNET signatures
}

@Test
fun `signature includes correct network ID for PUBLIC`() = runTest {
    // Test PUBLIC network signatures
}

@Test
fun `signatures differ across networks`() = runTest {
    // Test that same entry produces different signatures on different networks
}
```

#### Integration Tests

```kotlin
@Test
fun `round-trip authorization with simulation response`() = runTest {
    // Test authorizing an entry from a simulated transaction response
}

@Test
fun `multiple signers can authorize same entry`() = runTest {
    // Test multi-signature scenarios
}
```

### 7.2 Test Data

Use known test vectors from Java SDK tests for compatibility verification:

```kotlin
companion object {
    // Test vectors from Java SDK
    val TEST_KEYPAIR = KeyPair.fromSecretSeed("SXXXXXXXXXX...")
    val TEST_NETWORK = Network.TESTNET
    val TEST_VALID_UNTIL = 1000000L

    // Pre-constructed authorization entries for testing
    val UNSIGNED_AUTH_ENTRY_BASE64 = "AAAABgAAAAE..."

    // Expected signatures for verification
    val EXPECTED_SIGNATURE = "..."
}
```

### 7.3 Cross-Platform Tests

Run all tests on:
- JVM (BouncyCastle crypto)
- JS (libsodium-wrappers)
- Native (libsodium)

Verify that signatures are identical across platforms for the same inputs.

---

## 8. Edge Cases

### 8.1 Credential Type Handling

- **Non-Address Credentials**: Return entry unchanged (SOURCE_ACCOUNT type cannot be signed by SDK)
- **Already Signed Entries**: Override existing signature (re-signing is allowed)

### 8.2 Input Validation

- **Invalid Base64**: Throw IllegalArgumentException with clear message
- **Null/Empty Inputs**: Validate and throw with descriptive errors
- **Invalid Public Key**: Throw when creating Address or KeyPair fails
- **Invalid Ledger Sequence**: Accept any Long value (let protocol handle validation)

### 8.3 Signature Verification

- **Mismatched Signature**: Throw IllegalArgumentException after signing (safety check)
- **Wrong Public Key**: Detected during verification, throw with clear message
- **Corrupted Preimage**: Throw during XDR encoding/decoding

### 8.4 Nonce Generation

- **Collision Risk**: Extremely low with cryptographically secure random (2^64 space)
- **Platform Differences**: Use existing crypto infrastructure to ensure CSPRNG on all platforms

### 8.5 XDR Encoding/Decoding

- **Encoding Errors**: Wrap in IllegalArgumentException with context
- **Decoding Errors**: Same error handling strategy
- **Version Compatibility**: XDR types are stable across SDK versions

---

## 9. Platform Considerations

### 9.1 Common Code (All Platforms)

All core logic is platform-independent:
- Authorization logic
- Hash preimage construction
- Signature verification
- SCVal building
- Entry cloning

### 9.2 Platform-Specific Code

**None required** - All dependencies are already abstracted:

- **Random Generation**: Use `Ed25519Crypto.generatePrivateKey()` (already implemented per platform)
- **Hashing**: Use `Util.hash()` (already platform-independent)
- **Signing**: Use `KeyPair.sign()` (already platform-independent with suspend)
- **XDR Encoding**: Already platform-independent

### 9.3 Async API

All public methods are `suspend` functions because:
- KeyPair signing is async (required for JS libsodium initialization)
- Consistent API across all platforms
- Future-proof for async operations

---

## 10. Estimated Effort

### 10.1 Implementation Tasks

| Task | Estimated Time | Complexity |
|------|---------------|------------|
| Create Auth.kt skeleton | 30 min | Low |
| Implement authorizeEntry (KeyPair) | 1 hour | Medium |
| Implement authorizeEntry (Signer) | 1 hour | Medium |
| Implement authorizeInvocation (KeyPair) | 45 min | Medium |
| Implement authorizeInvocation (Signer) | 45 min | Medium |
| Implement hash preimage construction | 30 min | Low |
| Implement signature verification | 30 min | Low |
| Implement signature SCVal building | 1 hour | Medium |
| Implement nonce generation | 30 min | Low |
| Implement entry cloning | 30 min | Low |
| Add KDoc documentation | 1 hour | Low |
| **Total Implementation** | **~8 hours** | **Medium** |

### 10.2 Testing Tasks

| Task | Estimated Time | Complexity |
|------|---------------|------------|
| Create AuthTest.kt skeleton | 30 min | Low |
| Write basic functionality tests | 2 hours | Medium |
| Write signature verification tests | 1 hour | Medium |
| Write edge case tests | 1.5 hours | Medium |
| Write network-specific tests | 1 hour | Low |
| Write integration tests | 1.5 hours | Medium |
| Create test data and vectors | 1 hour | Low |
| Run cross-platform tests | 1 hour | Low |
| Debug and fix issues | 2 hours | Medium |
| **Total Testing** | **~11.5 hours** | **Medium** |

### 10.3 Documentation Tasks

| Task | Estimated Time | Complexity |
|------|---------------|------------|
| Write usage examples | 1 hour | Low |
| Document Signer interface pattern | 30 min | Low |
| Add integration examples | 1 hour | Low |
| Update CLAUDE.md | 30 min | Low |
| **Total Documentation** | **~3 hours** | **Low** |

### 10.4 Total Effort

- **Implementation**: ~8 hours
- **Testing**: ~11.5 hours
- **Documentation**: ~3 hours
- **Total**: **~22.5 hours** (approximately 3 working days)

### 10.5 Complexity Assessment

**Overall Complexity: Medium**

**Rationale**:
- Most logic is straightforward data manipulation
- XDR types and crypto infrastructure already exist
- Well-defined specification from Java SDK
- Main complexity is in signature SCVal structure and verification
- No platform-specific code required
- Comprehensive testing needed due to cryptographic operations

### 10.6 Risk Factors

| Risk | Mitigation | Impact |
|------|-----------|--------|
| Signature structure mismatch | Use Java SDK test vectors for verification | High |
| XDR encoding inconsistencies | Thorough cross-platform testing | Medium |
| Nonce collision (extremely rare) | Use cryptographically secure random | Low |
| Async API complexity | Already established pattern in KeyPair | Low |

---

## 11. Implementation Checklist

### Pre-implementation
- [ ] Review Stellar account contract signature specification
- [ ] Examine Java SDK test cases for test vectors
- [ ] Verify all required XDR types are available
- [ ] Confirm crypto infrastructure supports needed operations

### Implementation Phase
- [ ] Create `Auth.kt` in `commonMain/kotlin/com/stellar/sdk/`
- [ ] Implement `Signature` data class
- [ ] Implement `Signer` interface
- [ ] Implement `authorizeEntry` (all overloads)
- [ ] Implement `authorizeInvocation` (all overloads)
- [ ] Implement internal helper methods
- [ ] Add comprehensive KDoc comments

### Testing Phase
- [ ] Create `AuthTest.kt` in `commonTest/kotlin/com/stellar/sdk/`
- [ ] Write and run JVM tests
- [ ] Write and run JS tests (Node.js and Browser)
- [ ] Write and run Native tests (macOS)
- [ ] Verify test vectors match Java SDK
- [ ] Test cross-platform signature compatibility
- [ ] Test edge cases and error conditions

### Documentation Phase
- [ ] Add usage examples to KDoc
- [ ] Create integration examples
- [ ] Update CLAUDE.md with Auth helper information
- [ ] Document Signer interface pattern

### Finalization
- [ ] Code review
- [ ] Performance testing
- [ ] Security review of signature construction
- [ ] Commit changes with descriptive message
- [ ] Update SDK version if applicable

---

## References

1. [Stellar Smart Contract Authorization](https://developers.stellar.org/docs/learn/encyclopedia/security/authorization/)
2. [Stellar Account Contract Signatures](https://developers.stellar.org/docs/learn/encyclopedia/contract-development/contract-interactions/stellar-transaction#stellar-account-signatures)
3. [Java Stellar SDK Auth.java](file:///Users/chris/projects/Stellar/java-stellar-sdk/src/main/java/org/stellar/sdk/Auth.java)
4. [Soroban Authorization XDR](https://github.com/stellar/stellar-xdr)
5. [Ed25519 Signature Scheme (RFC 8032)](https://tools.ietf.org/html/rfc8032)

---

*Plan created: October 5, 2025*
*Status: Ready for implementation*
*Next Steps: Review plan, then implement Auth.kt following this specification*
