# Security Best Practices

Security patterns and guidelines for production Stellar KMP SDK applications. All code assumes `import com.soneso.stellar.sdk.*` and runs inside a `suspend` context (coroutine).

## Secret Key Management

Secret keys (S... seeds) give full control over an account. Compromised keys lead to irreversible fund loss.

### Never Hardcode Keys

```kotlin
// WRONG -- secret key exposed in source code
// val kp = KeyPair.fromSecretSeed("SDJHRQF4GCMIIKAAAQ6GR...")

// CORRECT -- load from platform-specific secure storage
suspend fun loadKeyPair(secureStore: SecureKeyStore): KeyPair {
    val seedChars: CharArray = secureStore.readSecret("stellar_secret_seed")
    try {
        return KeyPair.fromSecretSeed(seedChars)
    } finally {
        seedChars.fill('\u0000') // zero the CharArray after use
    }
}
```

### CharArray vs String for Secrets

`KeyPair.getSecretSeed()` returns `CharArray?` (not `String`) by design. `CharArray` can be explicitly zeroed; `String` is immutable on JVM and may linger in memory, heap dumps, or swap files.

```kotlin
// WRONG -- converting secret seed to String defeats security design
// val seedString: String = String(keyPair.getSecretSeed()!!)

// CORRECT -- use CharArray, zero when done
val seedChars: CharArray? = keyPair.getSecretSeed()
seedChars?.let { chars ->
    // ... use chars (e.g., display to user, write to secure storage)
    chars.fill('\u0000') // zero after use
}
```

```kotlin
// WRONG -- fromSecretSeed(String) is marked "Insecure" in the SDK
// Only use for prototyping/testing, NEVER in production with real secrets
// val kp = KeyPair.fromSecretSeed("SCZANGBA5YH...")

// CORRECT -- fromSecretSeed(CharArray) allows secure cleanup
val seedChars = secureStore.readSecretAsCharArray("stellar_seed")
try {
    val kp = KeyPair.fromSecretSeed(seedChars)
    // ... use kp
} finally {
    seedChars.fill('\u0000')
}
```

### Platform-Specific Secure Storage

The KMP SDK targets multiple platforms. Each has its own secure storage mechanism:

**Android -- Android Keystore + EncryptedSharedPreferences:**
```kotlin
// Android secure storage using Jetpack Security
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidSecureKeyStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "stellar_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun storeSecretSeed(alias: String, seed: CharArray) {
        // Store as String in encrypted prefs (encryption protects at rest)
        prefs.edit().putString(alias, String(seed)).apply()
    }

    fun readSecretSeed(alias: String): CharArray? {
        return prefs.getString(alias, null)?.toCharArray()
    }

    fun deleteSecretSeed(alias: String) {
        prefs.edit().remove(alias).apply()
    }
}
```

**iOS/macOS -- Keychain Services:**
```kotlin
// In iosMain/macosMain -- use platform-specific Keychain wrapper
// Access via expect/actual pattern from commonMain

// commonMain:
expect class SecureKeyStore {
    fun storeSecret(key: String, value: CharArray)
    fun readSecret(key: String): CharArray?
    fun deleteSecret(key: String)
}

// iosMain (implement using Security framework via Kotlin/Native interop):
// Use SecItemAdd, SecItemCopyMatching, SecItemDelete
// Set kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly
```

**JVM Server -- Java KeyStore or environment variables:**
```kotlin
// JVM server: load from environment, never from source files
suspend fun loadFromEnvironment(): KeyPair {
    val seedString = System.getenv("STELLAR_SECRET_SEED")
        ?: throw IllegalStateException("STELLAR_SECRET_SEED not set")
    val seedChars = seedString.toCharArray()
    try {
        return KeyPair.fromSecretSeed(seedChars)
    } finally {
        seedChars.fill('\u0000')
    }
}
```

**JavaScript/Web -- no truly secure local storage exists.** Browser `localStorage` and `sessionStorage` are accessible to any JavaScript on the page. Never store secret seeds in the browser. For web wallets, consider hardware wallet integrations or server-side key custody with SEP-10 authentication.

### Key Management Rules

1. Generate keys on-device, never on a server (unless server is the intended custodian)
2. Never log, print, or transmit secret seeds
3. Zero secret seeds (`CharArray.fill('\u0000')`) when no longer needed
4. Use `KeyPair.fromAccountId()` (public key only) for read-only operations -- it cannot sign
5. For HD wallets, store only the mnemonic and derive keys on demand via `Mnemonic` (SEP-0005)
6. Always call `Mnemonic.close()` to zero internal seed data when done

## Mnemonic / HD Wallet Security (SEP-0005)

`Mnemonic` implements `AutoCloseable`. Call `close()` to zero the internal BIP-39 seed when finished.

```kotlin
// WRONG -- mnemonic seed stays in memory indefinitely
// val m = Mnemonic.from(phrase)
// val kp = m.getKeyPair(0)
// (never calling close)

// CORRECT -- use close() to zero seed, or use() block
val mnemonic = Mnemonic.from(phrase)
try {
    val account0 = mnemonic.getKeyPair(index = 0)
    val account1 = mnemonic.getKeyPair(index = 1)
    // ... use keypairs
} finally {
    mnemonic.close() // zeros internal seed ByteArray
}
```

### Mnemonic Generation and Storage

```kotlin
// Generate a 24-word mnemonic (256 bits -- maximum security, recommended)
val phrase: String = Mnemonic.generate24WordsMnemonic()

// CRITICAL: store the phrase in platform-specific secure storage immediately
secureStore.storeMnemonic(phrase)

// Validate before restoring
val isValid = Mnemonic.validate(phrase) // suspend function
if (!isValid) {
    throw IllegalArgumentException("Invalid mnemonic phrase")
}

// Restore and derive keys
val mnemonic = Mnemonic.from(phrase) // validates and creates instance
try {
    val keyPair = mnemonic.getKeyPair(index = 0) // m/44'/148'/0'
    // ... use keyPair
} finally {
    mnemonic.close()
}
```

### Passphrase Security

```kotlin
// Passphrase creates entirely different keys -- lost passphrase = lost funds
val secureMnemonic = Mnemonic.from(phrase, passphrase = "my secret passphrase")
try {
    // These keys are DIFFERENT from Mnemonic.from(phrase) without passphrase
    val keyPair = secureMnemonic.getKeyPair(index = 0)
    // ...
} finally {
    secureMnemonic.close()
}
```

## Input Validation

Validate all user-provided data before constructing Stellar transactions.

### Address Validation

```kotlin
fun isValidStellarAddress(address: String): Boolean {
    if (StrKey.isValidEd25519PublicKey(address)) return true   // G...
    if (StrKey.isValidMed25519PublicKey(address)) return true   // M...
    if (StrKey.isValidContract(address)) return true            // C...
    return false
}

// WRONG: StrKey.isValidEd25519SecretSeed("S...") -- takes CharArray, NOT String
// CORRECT: StrKey.isValidEd25519SecretSeed("S...".toCharArray())
fun isValidSeed(seed: String): Boolean {
    val chars = seed.toCharArray()
    try {
        return StrKey.isValidEd25519SecretSeed(chars)
    } finally {
        chars.fill('\u0000')
    }
}
```

`StrKey` validation requires canonical encoding, and JVM, Android, JS and native
apply the same rule. All of the following are rejected:

- trailing `=` padding, and any content following a `=`
- whitespace anywhere in the string, including whitespace inserted mid-string
- lowercase base32 characters
- any character outside the base32 alphabet, including a non-ASCII character
  whose low byte would alias onto a legal one

Trim untrusted input before validating it. A strkey pasted from a user interface
frequently carries surrounding whitespace, and such a value is rejected rather
than silently normalized.

```kotlin
val address = userInput.trim()
if (!isValidStellarAddress(address)) {
    // reject the input -- do not attempt any further normalization
}
```

Validation agrees with decoding. `isValidSignedPayload` enforces the same `P...`
framing that `SignerKey` and the XDR decoders require -- declared payload length
in 1..64, an exact fit, zero padding -- and `isValidClaimableBalance` rejects a
`B...` strkey whose discriminant is not `CLAIMABLE_BALANCE_ID_TYPE_V0`, as does
the `Address` constructor. Every `StrKey.decode*` entry point signals a rejection
with `IllegalArgumentException`.

### Asset Code Validation

```kotlin
fun validateAssetCode(code: String): String? {
    if (code.isEmpty() || code.length > 12) {
        return "Asset code must be 1-12 characters"
    }
    if (!code.matches(Regex("^[a-zA-Z0-9]+$"))) {
        return "Asset code must be alphanumeric only"
    }
    return null // valid
}
```

### Amount Validation

```kotlin
fun validateAmount(input: String): String? {
    if (input.isEmpty()) return "Amount is required"

    val amount = input.toDoubleOrNull()
        ?: return "Amount must be a number"
    if (amount <= 0) return "Amount must be positive"

    // Stellar supports max 7 decimal places (stroops)
    val parts = input.split(".")
    if (parts.size == 2 && parts[1].length > 7) {
        return "Maximum 7 decimal places"
    }

    // Stellar maximum: 922,337,203,685.4775807
    if (amount > 922_337_203_685.4775807) {
        return "Amount exceeds Stellar maximum"
    }

    return null // valid
}
```

### Memo Validation

```kotlin
fun validateMemoText(value: String): String? {
    // MemoText max 28 bytes UTF-8
    if (value.encodeToByteArray().size > 28) {
        return "Memo text exceeds 28 bytes"
    }
    return null
}
```

## Transaction Verification Before Signing

Always inspect transaction contents before calling `sign()`, especially when receiving XDR from external sources (SEP-0007 URIs, SEP-10 challenges, multi-sig coordination). See [XDR Reference](./xdr.md) for the inspection pattern.

```kotlin
// Parse and inspect a transaction from an external source
val tx = AbstractTransaction.fromEnvelopeXdr(envelopeXdrBase64, network)
if (tx is Transaction) {
    // Check source account
    val sourceAccountId = tx.sourceAccount
    require(sourceAccountId == expectedAccountId) { "Unexpected source account" }

    // Check operations
    for (op in tx.operations) {
        // Verify operation types and parameters
        println("Operation: ${op::class.simpleName}")
    }

    // Check fee (e.g., reject if over 0.001 XLM per operation)
    val maxFeePerOp = 10_000L // 0.001 XLM in stroops
    require(tx.fee <= tx.operations.size * maxFeePerOp) { "Fee too high" }

    // Check signature count
    println("Existing signatures: ${tx.signatures.size}")

    // Only sign after verification
    tx.sign(signerKeyPair)
}
```

Key checks before signing:
- Source account matches expectations
- Operations are expected types with expected parameters
- Fee is reasonable (e.g., under 0.001 XLM per operation)
- No unexpected Soroban resource fees attached
- Signature count is as expected (no extra signatures)

## Network Selection and Validation

Mixing testnet and public network configurations leads to invalid signatures or unintentional real-fund transfers.

```kotlin
// WRONG -- constructing Network with raw passphrase strings
// val network = Network("some passphrase")

// CORRECT -- use the predefined constants
val network = Network.TESTNET   // or Network.PUBLIC
```

Centralize network configuration to prevent testnet/mainnet mismatches:

```kotlin
class StellarConfig private constructor(
    val horizon: HorizonServer,
    val network: Network,
    val rpcServer: SorobanServer?
) {
    companion object {
        fun testnet() = StellarConfig(
            horizon = HorizonServer("https://horizon-testnet.stellar.org"),
            network = Network.TESTNET,
            rpcServer = SorobanServer("https://soroban-testnet.stellar.org")
        )

        fun publicNet() = StellarConfig(
            horizon = HorizonServer("https://horizon.stellar.org"),
            network = Network.PUBLIC,
            rpcServer = SorobanServer("https://rpc.stellar.org")
        )
    }
}
```

Always pass the `Network` object from a single configuration source. Every `TransactionBuilder` requires a `Network`, and the network passphrase is hashed into every transaction ID to prevent cross-network replay.

## Safe Error Handling

Never expose secret keys in error messages, logs, or stack traces.

```kotlin
// WRONG -- seed appears in stack frame if later code throws
// fun unsafeSign(seed: String) {
//     val kp = KeyPair.fromSecretSeed(seed)
//     // if code throws here, seed is in the stack trace
// }

// CORRECT -- isolate key handling, catch and sanitize errors
suspend fun safeSign(
    secureStore: SecureKeyStore,
    transactionBuilder: TransactionBuilder
): Transaction {
    val seedChars: CharArray = secureStore.readSecret("stellar_seed")
        ?: throw IllegalStateException("Secret seed not found in secure storage")
    try {
        val kp = KeyPair.fromSecretSeed(seedChars)
        val tx = transactionBuilder.build()
        tx.sign(kp)
        return tx
    } catch (e: Exception) {
        // Log only safe information, never the seed
        println("Transaction signing failed: ${e::class.simpleName}")
        throw e
    } finally {
        seedChars.fill('\u0000') // zero seed from memory
    }
}
```

## Multi-Signature Security

Security rules for multi-sig accounts:
- Set appropriate thresholds via `SetOptionsOperation` (low, medium, high) -- see [Advanced Features](./advanced.md)
- Distribute signing across different devices or parties
- Never collect all signer keys in one place
- Use time bounds (`TimeBounds`) in `TransactionPreconditions` to limit signing windows
- Always inspect transaction contents before co-signing (see XDR sharing pattern in advanced.md)

```kotlin
// Configure multi-sig thresholds
// WRONG: SignerKey.Ed25519(...) -- no such constructor
// CORRECT: SignerKey.ed25519PublicKey(...) -- factory method
val setOptions = SetOptionsOperation(
    lowThreshold = 1,
    mediumThreshold = 2,
    highThreshold = 3,
    signer = SignerKey.ed25519PublicKey(cosignerAccountId), // accepts G... String
    signerWeight = 1
)
```

## SEP-10 Authentication Security

SEP-10 (Web Authentication) proves account ownership to anchor services.

```kotlin
suspend fun authenticateWithAnchor(
    domain: String,
    clientKeyPair: KeyPair,
    network: Network
): AuthToken {
    val webAuth = WebAuth.fromDomain(domain, network)
    val authToken: AuthToken = webAuth.jwtToken(
        clientAccountId = clientKeyPair.getAccountId(),
        signers = listOf(clientKeyPair)
    )
    return authToken
}
```

SEP-10 security considerations:
- `WebAuth` validates the challenge internally (13 security checks) before signing
- Challenge transactions must have a `ManageDataOperation` with the correct domain
- Never sign challenges that contain unexpected operations
- Token expiry is set by the server -- do not cache tokens beyond their lifetime
- Use `WebAuth.fromDomain()` to ensure the auth endpoint is resolved from the official stellar.toml
- The `authToken.token` field contains the JWT string for use in `Authorization: Bearer` headers

## Platform-Specific Cryptographic Libraries

The SDK uses platform-specific crypto implementations. No custom/experimental cryptography is used.

| Platform | Library | Notes |
|----------|---------|-------|
| JVM/Android | BouncyCastle (`bcprov-jdk18on`) | Ed25519 RFC 8032, SecureRandom |
| iOS/macOS | libsodium (C interop) | `crypto_sign_*`, constant-time, audited |
| JavaScript | libsodium-wrappers-sumo (WASM) | Same audited libsodium compiled to WebAssembly |

All implementations provide:
- Constant-time operations (timing attack protection)
- Cryptographically secure random number generation
- Audited, battle-tested Ed25519 signing and verification

### Suspend Functions for Crypto

Crypto operations (`KeyPair.random()`, `KeyPair.fromSecretSeed()`, `sign()`, `verify()`) are `suspend` functions because JavaScript requires async libsodium initialization. On JVM and Native, the suspend keyword has zero overhead.

```kotlin
// WRONG -- calling suspend crypto functions outside a coroutine
// val kp = KeyPair.random()  // compile error: suspend function

// CORRECT -- call from a coroutine scope
// In Android:
lifecycleScope.launch {
    val kp = KeyPair.random()
}

// In JVM server:
runBlocking {
    val kp = KeyPair.random()
}

// In tests:
@Test
fun testKeyGeneration() = runTest {
    val kp = KeyPair.random()
    assertNotNull(kp.getAccountId())
}
```

## HTTPS and Endpoint Security

- Always use HTTPS endpoints for Horizon and Soroban RPC
- The SDK's `HorizonServer` constructor accepts any URL -- verify the scheme is HTTPS for production
- When using custom Horizon instances, validate the URL scheme
- Pin or validate TLS certificates in high-security mobile apps (requires platform-specific code outside the SDK)

## Web Platform Considerations

Kotlin/JS in the browser has unique security constraints:

1. **No secure local storage.** Browser `localStorage` and `sessionStorage` are accessible to any JavaScript on the page. Never store secret seeds in the browser.

2. **HTTPS required.** All Horizon and Soroban RPC endpoints must be accessed over HTTPS.

3. **CORS restrictions.** Horizon servers must include appropriate CORS headers. Public Horizon and Soroban RPC endpoints support CORS. Custom instances may need CORS configuration.

## Dependency Security

The SDK uses audited cryptographic libraries with no custom crypto:

| Platform | Crypto Dependency | Other |
|----------|------------------|-------|
| JVM | `org.bouncycastle:bcprov-jdk18on` | `commons-codec` (Base32) |
| Native | libsodium (C interop) | -- |
| JS | `libsodium-wrappers-sumo` (npm) | -- |

1. Keep dependencies up to date -- check with `./gradlew dependencyUpdates` or similar
2. Review changelogs of cryptographic dependencies for security patches
3. Lock dependency versions in `gradle.lockfile` or `libs.versions.toml` and commit to source control

## Security Checklist

- [ ] Secret keys loaded from platform-specific secure storage, never hardcoded
- [ ] `KeyPair.fromSecretSeed(CharArray)` used instead of `fromSecretSeed(String)` in production
- [ ] Secret `CharArray` zeroed with `fill('\u0000')` after use
- [ ] `Mnemonic.close()` called when HD wallet instance is no longer needed
- [ ] All user-supplied addresses validated with `StrKey` methods
- [ ] Untrusted strkey input trimmed before validation -- `=` padding, whitespace
      and lowercase characters are rejected as non-canonical
- [ ] `StrKey.isValidEd25519SecretSeed()` called with `CharArray`, not `String`
- [ ] Asset codes validated (1-12 alphanumeric characters)
- [ ] Amounts validated as positive decimals with at most 7 decimal places
- [ ] All transactions inspected before signing (source, operations, fee)
- [ ] Error messages sanitized -- no secrets in logs or user-facing errors
- [ ] Network configuration sourced from a single place (testnet vs public)
- [ ] Web platform avoids storing secrets in browser storage
- [ ] All endpoints use HTTPS
- [ ] SEP-10 challenges verified before signing (automatic with `jwtToken()`)
- [ ] Multi-sig thresholds configured for high-value accounts
- [ ] Cryptographic dependencies audited and pinned
