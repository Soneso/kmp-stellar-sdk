# Context Rules, Policies, and Multi-Signer Operations

Signers, context rules, policies, and multi-signer operations for an existing smart account — the dynamic authorization layer on top of the core API in [smart_accounts.md](./smart_accounts.md).

Standard imports:

```kotlin
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.smartaccount.oz.*
import com.soneso.stellar.sdk.smartaccount.core.*
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import com.ionspin.kotlin.bignum.integer.BigInteger
```

All operations run in a `suspend` context (coroutine). The examples assume `val kit: OZSmartAccountKit` is created and `kit.walletOperations.connectWallet(...)` has returned non-null — see [smart_accounts.md](./smart_accounts.md#connecting-to-a-wallet).

For a flat signature index of every public class referenced here, see [api_reference.md](./api_reference.md) — smart-account classes are under the "Smart Accounts" section.

## Overview

On-chain authorization for an OZ smart account is arranged in three layers:

```text
Smart Account (C-address)
  |
  +-- Context Rule #0 (Default, created at deploy)
  |     +-- Signers:  [Passkey (user's initial credential)]
  |     +-- Policies: []
  |
  +-- Context Rule #1 (CallContract("Cxxx...") e.g. a token)
  |     +-- Signers:  [Passkey A, Passkey B, Wallet G...]
  |     +-- Policies: [SpendingLimit("100 XLM / day")]
  |
  +-- Context Rule #2 (CallContract("Cyyy...") e.g. a DAO)
        +-- Signers:  [Wallet G..., Wallet G...]
        +-- Policies: [WeightedThreshold(weights, 80u)]
```

When a transaction runs, the contract picks rules whose `contextType` matches the invocation: specific-type rules (`CallContract`, `CreateContract`) are evaluated first, and the `Default` rule is the fallback. A rule passes when its signers have signed and every one of its policies returns `true`. See the [onboarding guide](https://github.com/Soneso/kmp-stellar-sdk/blob/main/docs/smart-accounts/onboarding.md) for the full evaluation algorithm.

Typical use cases:

- **Passkey rotation / backup** — add a second passkey, a backup Ed25519 key, or a delegated wallet as additional signers on the Default rule.
- **Spending limits** — scope a `SpendingLimit` policy to a specific token contract via a `CallContract` rule.
- **Multi-party approval** — install a `SimpleThreshold(2u)` or `WeightedThreshold` policy on a rule that protects a governance contract.
- **Per-contract permissions** — allow a dApp helper passkey to authorize only calls to one specific contract.

Kit sub-managers covered here:

| Manager | Purpose |
|---------|---------|
| `kit.signerManager` | Add/remove signers on a context rule |
| `kit.contextRuleManager` | Add/remove/query/update context rules |
| `kit.policyManager` | Install/remove policies on a context rule |
| `kit.multiSignerManager` | Multi-party transfers and arbitrary contract calls |

```kotlin
// WRONG: kit.signerManager()  — it is a property, not a function
// CORRECT: kit.signerManager  — property access (no parentheses)
```

Rule limits: 15 signers, 5 policies, 20-byte name. Full `OZConstants` table: [smart_accounts.md — Contract Limits](./smart_accounts.md#limits-and-defaults).

---

## Signer Management

`kit.signerManager` (type `OZSignerManager`) adds or removes signers on a specific context rule. Every state-changing method accepts an optional `selectedSigners: List<SelectedSigner>` parameter; when empty (the default), the operation is authorized by the connected passkey alone. When non-empty, the operation routes through the multi-signer pipeline — see [Multi-Signer Operations](#multi-signer-operations).

### addNewPasskeySigner — register and add in one step

Runs a WebAuthn registration ceremony, persists the credential locally, emits `CredentialCreated`, and then submits the on-chain `add_signer` call. Requires `webauthnProvider` in config.

```kotlin
suspend fun addNewPasskeySigner(
    contextRuleId: UInt,
    userName: String,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): AddPasskeySignerResult

data class AddPasskeySignerResult(
    val credentialId: String,          // Base64URL, no padding
    val publicKey: ByteArray,          // 65 bytes uncompressed secp256r1
    val transactionResult: TransactionResult
)
```

```kotlin
val result = kit.signerManager.addNewPasskeySigner(
    contextRuleId = 0u,                   // Default rule
    userName = "Alice backup device"
)
println("Credential:  ${result.credentialId}")
println("Submitted:   ${result.transactionResult.success}, hash=${result.transactionResult.hash}")
```

The user sees two biometric prompts: one for registering the new passkey, one for the currently-connected passkey to authorize the on-chain call.

```kotlin
// WRONG: contextRuleId = 0       — Int will not compile (parameter is UInt)
// CORRECT: contextRuleId = 0u    — UInt literal
// WRONG: calling addNewPasskeySigner without webauthnProvider in config
//   -> throws WebAuthnException.NotSupported
// CORRECT: configure webauthnProvider before calling this method
```

### addPasskey — add a pre-registered passkey

Use when you already hold the public key and raw credential ID (e.g., imported from another device). Performs the on-chain `add_signer` call only; no local credential is stored.

> **Transport authenticity — anyone who can inject bytes into your import channel can become a signer.**
>
> The `publicKey` and `credentialId` must arrive over a channel that is authenticated to the user. An attacker who controls the transport (unsigned QR code, clipboard, email, unauthenticated WebSocket, URL query parameter) can substitute their own public key — you would then add *their* passkey as a signer on your smart account and they can authorize any operation the rule permits.
>
> Safe transports: in-app pairing code verified on both devices, OS-level Handoff/AirDrop, NFC tap, out-of-band secure channel, signed QR from a server you control. Always show the user a short hex fingerprint of the credential on **both** the sending and receiving device and require explicit confirmation before calling `addPasskey`. The SDK does not provide a canonical helper — any stable hash-and-truncate both sides can reproduce works (e.g., the first 16 bytes of `SHA-256(publicKey)` hex-encoded; do not use the raw `publicKey[0..15]` because byte 0 is always the constant `0x04` SEC-1 prefix and contributes no entropy).

```kotlin
suspend fun addPasskey(
    contextRuleId: UInt,
    publicKey: ByteArray,                 // 65 bytes, 0x04 prefix
    credentialId: ByteArray,              // raw bytes, NOT Base64URL
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
// PRECONDITION: publicKey and credentialId were verified with the user on
// both devices via an authenticated channel — not just received from an
// unauthenticated source.
val result = kit.signerManager.addPasskey(
    contextRuleId = 0u,
    publicKey = otherDevicePublicKey65,       // ByteArray, 65 bytes
    credentialId = otherDeviceCredentialId    // ByteArray, raw
)
if (!result.success) println("Failed: ${result.error}")
```

```kotlin
// WRONG: publicKey.size == 33  — compressed format, rejected
// CORRECT: publicKey.size == 65 and publicKey[0] == 0x04.toByte()
// WRONG: credentialId = Util.base64urlEncode(credIdBytes)  — this is a String, not ByteArray
// CORRECT: credentialId is the raw ByteArray from the WebAuthn ceremony
```

### addDelegated — add a Stellar account or contract signer

```kotlin
suspend fun addDelegated(
    contextRuleId: UInt,
    address: String,                      // G-address (account) or C-address (contract)
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
// Add a Stellar account as a signer
val accountRes = kit.signerManager.addDelegated(
    contextRuleId = 0u,
    address = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
)

// Add another contract as a signer (custom account contract)
val contractRes = kit.signerManager.addDelegated(
    contextRuleId = 0u,
    address = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"
)
```

The `DelegatedSigner(address)` constructor throws `ValidationException.InvalidAddress` if the address is neither a valid Ed25519 public key (G...) nor a valid contract address (C...).

### addEd25519 — add an Ed25519 external signer

Requires a deployed Ed25519 verifier contract. `publicKey` is the raw 32-byte Ed25519 key.

```kotlin
suspend fun addEd25519(
    contextRuleId: UInt,
    verifierAddress: String,              // C-address of the Ed25519 verifier
    publicKey: ByteArray,                 // 32 bytes
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
val result = kit.signerManager.addEd25519(
    contextRuleId = 0u,
    verifierAddress = "CED255192XXXX...",   // deployed verifier
    publicKey = backupEd25519PublicKey      // 32 bytes
)
```

```kotlin
// WRONG: publicKey.size == 64  — that is a signature, not a key
// CORRECT: publicKey.size == 32  — raw Ed25519 public key
```

### removeSigner — by on-chain ID

Signer IDs are assigned by the contract on insertion. They live on the rule and are returned from `ParsedContextRule.signerIds`.

```kotlin
suspend fun removeSigner(
    contextRuleId: UInt,
    signerId: UInt,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
val rules = kit.contextRuleManager.listContextRules()
val rule  = rules.first { it.id == 0u }

// ParsedContextRule.signers and ParsedContextRule.signerIds are positionally aligned
val idx = rule.signers.indexOfFirst { signer ->
    signer is DelegatedSigner && signer.address == "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ"
}
if (idx >= 0) {
    kit.signerManager.removeSigner(
        contextRuleId = 0u,
        signerId = rule.signerIds[idx]
    )
}
```

```kotlin
// WRONG: signerId = 0u for the first signer   — IDs are contract-assigned, NOT positional
// CORRECT: read signerId from rule.signerIds at the matching position
```

### removeSigner — by signer value

Convenience overload that looks up the on-chain ID internally.

```kotlin
suspend fun removeSigner(
    contextRuleId: UInt,
    signer: SmartAccountSigner,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
// Remove a known delegated signer without fetching IDs manually
kit.signerManager.removeSigner(
    contextRuleId = 0u,
    signer = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")
)
```

Throws `ValidationException.InvalidInput` if the signer is not on the rule.

### Removing the last signer

The contract rejects removing the final signer when the rule has no policies — see error 3004 (`NoSignersAndPolicies`) in [Contract Error Codes](#contract-error-codes). Either add a policy first, or remove the entire rule (`removeContextRule`).

### Signer type recap

`SmartAccountSigner` is a sealed class with two variants (`DelegatedSigner`, `ExternalSigner`). `ExternalSigner.webAuthn(...)` and `ExternalSigner.ed25519(...)` are factories on the companion object, not subclasses. Full details in [smart_accounts.md — Signer Types](./smart_accounts.md#signer-types).

---

## Context Rules

`kit.contextRuleManager` (type `OZContextRuleManager`) creates, lists, updates, and removes context rules on the connected smart account.

### The Default rule

Every smart account is deployed with one rule at `id = 0u`: `contextType = Default`, `name = "multisig"` (assigned by the account contract's constructor), signers `[initial passkey]`, policies `[]`. A Default rule matches any operation context. You can add signers/policies to it; do not remove it (see [removeContextRule](#removecontextrule)).

The Default rule can also deploy with policies already installed, instead of adding them afterward: pass a `policies: Map<String, SCValXdr>` (policy contract address to install-param `SCValXdr`) to `kit.walletOperations.createWallet(...)` or `deployPendingCredential(...)`, or set `OZSmartAccountConfig.defaultPolicies` for a config-level default that a per-call argument overrides. The kit passes them through the contract constructor; they are validated (max 5, valid C-addresses) before the passkey ceremony. Constructor args are not part of the contract-address preimage, so the derived address is unchanged. The built-in policies' own install rules apply against this Default rule and its single initial signer: spending-limit installs only on CallContract rules (error 3227) and a threshold above the signer count is rejected (error 3201). A threshold of 1 installs and keeps the rule at 1-of-N as signers are added later; beyond that, constructor policies are primarily useful for custom policies.

### ContextRuleType

The type of rule determines which invocations it applies to.

```kotlin
sealed class ContextRuleType {
    object Default : ContextRuleType()
    data class CallContract(val contractAddress: String) : ContextRuleType()
    data class CreateContract(val wasmHash: ByteArray) : ContextRuleType()
}
```

On-chain SCVal encoding:

```text
Default         ->  Vec([Symbol("Default")])
CallContract    ->  Vec([Symbol("CallContract"), Address(contractAddress)])
CreateContract  ->  Vec([Symbol("CreateContract"), Bytes(wasmHash)])
```

```kotlin
// WRONG: ContextRuleType.CallContract.new("CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5")   — no such syntax
// CORRECT: ContextRuleType.CallContract("CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5")     — data-class constructor

// WRONG: ContextRuleType.CallContract(Address("CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5"))
//        — parameter is a String (C-address), NOT an Address object
// CORRECT: ContextRuleType.CallContract("CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5")

// WRONG: ContextRuleType.CreateContract("abcd...")     — parameter is ByteArray, not hex string
// CORRECT: ContextRuleType.CreateContract(wasmHashBytes)  — raw 32-byte ByteArray
//   Use OZBuilders.createCreateContractContextType("abcd...") to convert hex ->
//   ContextRuleType.CreateContract automatically.
```

The `OZBuilders` helpers wrap construction with validation:

```kotlin
val defaultCtx = OZBuilders.createDefaultContextType()                  // Default
val callCtx    = OZBuilders.createCallContractContextType("CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5")    // validates C-address
val createCtx1 = OZBuilders.createCreateContractContextType("abc123...") // hex String, 64 chars (0x-prefix ok)
val createCtx2 = OZBuilders.createCreateContractContextType(wasmHash32Bytes) // ByteArray, 32 bytes
```

### addContextRule

```kotlin
suspend fun addContextRule(
    contextType: ContextRuleType,
    name: String,                          // max 20 UTF-8 bytes
    validUntil: UInt? = null,              // Option<u32> ledger sequence
    signers: List<SmartAccountSigner>,
    policies: Map<String, SCValXdr> = emptyMap(),    // C-address -> install params
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Example — create a rule that applies to a specific token contract, signed by two delegated signers with a spending-limit policy:

```kotlin
val signerA = DelegatedSigner("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")
val signerB = DelegatedSigner("GDAT5HWTGIU4TSSZ4752OUC4SABDLTLZFRPZUJ3D6LKBNEPA7V2CIG54")

// Build the install params with the typed builder (validated, correct encoding)
val spendingLimitParams = PolicyInstallParams.SpendingLimit(
    spendingLimit = OZTransactionOperations.amountToBaseUnits("1000", decimals = 7),
    periodLedgers = Util.LEDGERS_PER_DAY.toUInt()
).toScVal()

val result = kit.contextRuleManager.addContextRule(
    contextType = ContextRuleType.CallContract("CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"),
    name = "XlmDailyLimit",
    signers = listOf(signerA, signerB),
    policies = mapOf("CAQCCIRDEQSSMJZIFEVCWLBNFYXTAMJSGM2DKNRXHA4TUOZ4HU7D7V6Z" to spendingLimitParams)
)
if (result.success) println("Rule added, tx ${result.hash}")
```

```kotlin
// WRONG: addContextRule(name = "ALongerNameThanTwentyBytes") — contract error 3015 NameTooLong
// CORRECT: name must be <= 20 UTF-8 bytes

// WRONG: addContextRule(signers = emptyList(), policies = emptyMap())
//        — ValidationException: a rule must have >= 1 signer OR >= 1 policy
// CORRECT: supply at least one of the two

// WRONG: addContextRule(validUntil = 123u) where ledger 123 is already past
//        — contract error 3005 PastValidUntil at submission time
// CORRECT: validUntil must be a future ledger (or null for no expiration)
```

The `policies` map key is the policy contract address (C-address). The value is the install-param `SCValXdr`. For the three built-in policy types, prefer the convenience methods on `kit.policyManager` ([Policies](#policies)) — they encode the install params for you.

### ParsedContextRule

```kotlin
data class ParsedContextRule(
    val id: UInt,
    val contextType: ContextRuleType,
    val name: String,
    val signers: List<SmartAccountSigner>,  // positionally aligned with signerIds
    val signerIds: List<UInt>,
    val policies: List<String>,              // C-addresses, aligned with policyIds
    val policyIds: List<UInt>,
    val validUntil: UInt?
)
```

### listContextRules / getAllContextRules / getContextRule

```kotlin
suspend fun listContextRules(maxScanId: UInt = kit.config.maxContextRuleScanId): List<ParsedContextRule>
suspend fun getAllContextRules(maxScanId: UInt = kit.config.maxContextRuleScanId): List<SCValXdr>
suspend fun getContextRule(id: UInt): SCValXdr
suspend fun getContextRulesCount(): UInt
```

```kotlin
val rules = kit.contextRuleManager.listContextRules()
for (rule in rules) {
    println("Rule #${rule.id}: ${rule.name} (${rule.contextType})")
    println("  signers: ${rule.signers.size}  policies: ${rule.policies.size}")
    rule.validUntil?.let { println("  expires at ledger $it") }
}
```

IDs are monotonically increasing and never reused, so removed rules leave gaps. `listContextRules` iterates from 0 up to `maxScanId` (default `50u`, configurable via `OZSmartAccountConfig.maxContextRuleScanId`) and skips gaps. Raise the cap if your account has more than 50 rules added over its lifetime.

### updateName

Change a rule's display name. Names do not affect matching or enforcement.

```kotlin
suspend fun updateName(
    id: UInt,
    name: String,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
kit.contextRuleManager.updateName(id = 1u, name = "TokenTransfers")
```

```kotlin
// WRONG: kit.contextRuleManager.updateContextRuleName(...)  — method is named updateName
// CORRECT: kit.contextRuleManager.updateName(id, name)
```

### updateValidUntil

Set or clear a rule's expiration ledger. Pass `null` to remove expiration.

```kotlin
suspend fun updateValidUntil(
    id: UInt,
    validUntil: UInt?,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
// Expire rule in roughly one week from now (ledger-based, ~5 s/ledger).
// The kit's internal SorobanServer is not exposed; construct your own
// from the config's rpcUrl to read the current ledger.
val soroban = SorobanServer(kit.config.rpcUrl)
val latest = soroban.getLatestLedger().sequence.toUInt()
val inAWeek = latest + 7u * Util.LEDGERS_PER_DAY.toUInt()
kit.contextRuleManager.updateValidUntil(id = 1u, validUntil = inAWeek)

// Remove expiration
kit.contextRuleManager.updateValidUntil(id = 1u, validUntil = null)
```

The contract skips a rule once its `validUntil` is past — evaluation falls back to matching non-expired rules (and Default). Expired rules still exist on-chain until `removeContextRule`.

### removeContextRule

```kotlin
suspend fun removeContextRule(
    id: UInt,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
kit.contextRuleManager.removeContextRule(id = 3u)
```

Do not remove rule `0u` (Default) unless you have added equivalent coverage — the smart account needs at least one rule that matches every operation it performs.

---

## Policies

`kit.policyManager` (type `OZPolicyManager`) installs and removes policies on a context rule. A policy is a separate, already-deployed Soroban contract that implements `enforce()`, `install()`, and `uninstall()`. Policy contracts are shared network-wide (one deployment serves all smart accounts on the network); you provide the C-address and per-account install parameters.

### Finding policy contract addresses

You need a deployed policy contract before you can install it. Sources, in order of preference:

1. **Published OpenZeppelin addresses** — check the release notes or `README.md` of the [OpenZeppelin Stellar contracts repo](https://github.com/OpenZeppelin/stellar-contracts) for canonical testnet / mainnet C-addresses of `SimpleThreshold`, `WeightedThreshold`, and `SpendingLimit`.
2. **KMP SDK demo config** — the demo app ships current testnet addresses at [`smart-account-demo/shared/src/commonMain/kotlin/com/soneso/smartdemo/config/DemoConfig.kt`](https://github.com/Soneso/kmp-stellar-sdk/blob/main/smart-account-demo/shared/src/commonMain/kotlin/com/soneso/smartdemo/config/DemoConfig.kt) in the KMP SDK repo. Values rotate when testnet resets or contracts upgrade.
3. **Deploy your own** — clone the [stellar-contracts](https://github.com/OpenZeppelin/stellar-contracts) repo, build with `stellar contract build --package simple-threshold-policy` (or the relevant package), upload and deploy with `stellar contract deploy` to get a fresh C-address. Required for custom policies.

You also need `OZSmartAccountConfig.accountWasmHash` (hex SHA-256 of the uploaded smart account WASM) and `webauthnVerifierAddress` (C-address of the deployed WebAuthn verifier contract) — same sources apply. Compute the WASM hash locally with:

```bash
sha256sum path/to/multisig_account_example.wasm
# or, from the Stellar CLI after upload:
stellar contract upload --wasm path/to/multisig_account_example.wasm \
    --network testnet --source <deployer-secret>
```

Cross-reference the current values against the network you target; using a testnet address on mainnet (or vice-versa) fails with contract-not-found during simulation.

### addSimpleThreshold — M-of-N

All signers on the rule carry equal weight; the threshold is the minimum count required.

```kotlin
suspend fun addSimpleThreshold(
    contextRuleId: UInt,
    policyAddress: String,                 // C-address of the SimpleThreshold policy contract
    threshold: UInt,                       // >= 1
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
// 2-of-3 multisig on the Default rule
val result = kit.policyManager.addSimpleThreshold(
    contextRuleId = 0u,
    policyAddress = "CSIMPLETHRESHOLD000...",
    threshold = 2u
)
```

```kotlin
// WRONG: threshold = 2           — Int does not compile (UInt required)
// CORRECT: threshold = 2u
// WRONG: threshold = 0u          — contract error 3201 InvalidThreshold
// CORRECT: 1 <= threshold <= rule.signers.size
```

### addWeightedThreshold — weighted voting

Each signer has a weight; the sum of approving weights must be `>= threshold`. Signer identity is compared by SCVal bytes — the `SmartAccountSigner` key must match exactly what is stored on the rule.

```kotlin
suspend fun addWeightedThreshold(
    contextRuleId: UInt,
    policyAddress: String,
    signerWeights: Map<SmartAccountSigner, UInt>,   // keys are SmartAccountSigner, not String
    threshold: UInt,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
val admin = DelegatedSigner("GAADMIN1234567890...")
val lead  = DelegatedSigner("GALEAD1234567890...")
val dev   = DelegatedSigner("GADEV1234567890...")

val result = kit.policyManager.addWeightedThreshold(
    contextRuleId = 1u,
    policyAddress = "CAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAIBAEAQCAKAL",
    signerWeights = mapOf(
        admin to 50u,
        lead  to 30u,
        dev   to 20u
    ),
    threshold = 80u    // admin+lead passes; admin+dev passes; lead+dev does NOT
)
```

```kotlin
// WRONG: signerWeights = mapOf("GAJZR5RMNUNEK7CRXJVEWXZ5XUXWT7FJGILCDDOITF7EC26RPWJ4UVOE" to 50u)  — keys must be SmartAccountSigner, not String
// CORRECT: signerWeights = mapOf(DelegatedSigner("GAJZR5RMNUNEK7CRXJVEWXZ5XUXWT7FJGILCDDOITF7EC26RPWJ4UVOE") to 50u)

// WRONG: a signer in signerWeights is not also on the context rule's signers list
//        — even if weights add up to threshold, the signer cannot sign, and the
//          policy cannot pass. Add the signer to the rule first (addDelegated,
//          addPasskey, etc.) or include it in the rule's signers at rule creation.
// CORRECT: every signer in the weight map must be on the rule

// WRONG: threshold > sum of weights  — rule is unsatisfiable
// CORRECT: threshold <= sum of all weights (and you typically want strictly less,
//   so that at least one signer-subset passes without requiring unanimous approval)
```

### addSpendingLimit — rolling rate limit

Caps the total amount transferred by the rule's context within a rolling window measured in ledgers. The policy intercepts any invocation of a function named `transfer` (argument 2 read as `i128` amount) — so it can apply to any SEP-41 token contract.

```kotlin
suspend fun addSpendingLimit(
    contextRuleId: UInt,
    policyAddress: String,
    spendingLimit: String,                 // positive decimal string, e.g. "1000" or "10.5"
    periodLedgers: UInt,                   // window in ledgers (~5 s each)
    decimals: Int = 7,                     // token scale for the conversion (no on-chain fetch here)
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

Common period constants from `Util`:

```kotlin
Util.LEDGERS_PER_HOUR    // 720
Util.LEDGERS_PER_DAY     // 17_280
// No LEDGERS_PER_WEEK constant — compute it:
val weekLedgers: UInt = 7u * Util.LEDGERS_PER_DAY.toUInt()
```

Example — limit the account to 1000 XLM per day when calling the native SAC contract:

```kotlin
// 1. Create a CallContract rule that scopes the policy to the native XLM SAC
val nativeSac = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
val createRule = kit.contextRuleManager.addContextRule(
    contextType = ContextRuleType.CallContract(nativeSac),
    name = "XlmDailyLimit",
    signers = listOf(/* carry-over signers */)
)

// 2. Install the spending-limit policy on that rule
val rules = kit.contextRuleManager.listContextRules()
val ruleId = rules.last { it.contextType == ContextRuleType.CallContract(nativeSac) }.id
kit.policyManager.addSpendingLimit(
    contextRuleId = ruleId,
    policyAddress = "CSPENDINGLIMIT000...",
    spendingLimit = "1000",
    periodLedgers = Util.LEDGERS_PER_DAY.toUInt()
)
```

```kotlin
// WRONG: spendingLimit = "10000000000"     — interpreted as 10 billion XLM (decimal string!)
// CORRECT: spendingLimit = "1000"          — SDK converts to base units (decimals param, default 7)
// WRONG: spendingLimit = 1000.0            — parameter is a String, not Double
// CORRECT: spendingLimit = "1000"
// WRONG: periodLedgers = 86400u            — 86,400 ledgers is ~5 days at 5 s/ledger
// CORRECT: periodLedgers = Util.LEDGERS_PER_DAY.toUInt()  // 17,280 for 1 day
// WRONG: installing SpendingLimit on a Default rule  — intercepts every `transfer`
//        in the account (including transfers from unrelated token contracts that
//        happen to expose a `transfer` function). The policy also rejects non-
//        CallContract contexts: error 3227 OnlyCallContractAllowed.
// CORRECT: install on a CallContract(target-token-SAC) rule
```

Counter semantics: at the start of each new period, usage resets. The policy records one entry per transfer with the ledger sequence; entries older than `periodLedgers` are ignored. The contract caps history to 1000 entries (error 3224 `HistoryCapacityExceeded` on overflow).

### addPolicy — generic

For any custom policy contract or for install parameters not covered by the three wrappers.

```kotlin
suspend fun addPolicy(
    contextRuleId: UInt,
    policyAddress: String,
    installParams: SCValXdr,              // SCVal map with policy-specific keys
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
// Custom "allowlist" policy that accepts a list of permitted contracts
val installParams = Scv.toMap(linkedMapOf(
    Scv.toSymbol("allowed_contracts") to Scv.toVec(listOf(
        Scv.toAddress(Address("CALLOWED1...").toSCAddress()),
        Scv.toAddress(Address("CAYDAMBQGAYDAMBQGAYDAMBQGAYDAMBQGAYDAMBQGAYDAMBQGAYDBWBA").toSCAddress())
    )),
    Scv.toSymbol("max_per_tx") to Scv.toUint32(10u)
))

kit.policyManager.addPolicy(
    contextRuleId = 0u,
    policyAddress = "CCUSTOMALLOWLIST000...",
    installParams = installParams
)
```

Map keys must be in the **host's ScMap key order** — for lowercase snake_case field names that is plain alphabetical (content) order, not the XDR-byte order, whose length prefix would sort shorter names first. The SDK sorts the top-level policies map internally, but the install-params map inside each entry is your responsibility.

### PolicyInstallParams (typed install parameters)

The sealed class models the three built-in policy types. Its `toScVal()` method is public and encodes the variant as the SCVal map the policy contract's install entry point expects. `addPolicy` accepts either a `PolicyInstallParams` (typed overload, encodes internally) or a raw `SCValXdr` (for custom policy contracts):

```kotlin
sealed class PolicyInstallParams {
    abstract fun toScVal(): SCValXdr
    data class SimpleThreshold(val threshold: UInt) : PolicyInstallParams()
    data class WeightedThreshold(
        val signerWeights: Map<SmartAccountSigner, UInt>,
        val threshold: UInt
    ) : PolicyInstallParams()
    data class SpendingLimit(
        val spendingLimit: BigInteger,    // token base units, NOT a decimal string
        val periodLedgers: UInt
    ) : PolicyInstallParams()
}
```

Install via the convenience methods (`addSimpleThreshold` / `addWeightedThreshold` / `addSpendingLimit`), the typed `addPolicy(installParams: PolicyInstallParams)` overload, or the generic `addPolicy(installParams: SCValXdr)` for custom policy contracts.

### removePolicy — by ID

Policy IDs are assigned by the contract on install and align positionally with `ParsedContextRule.policies`.

```kotlin
suspend fun removePolicy(
    contextRuleId: UInt,
    policyId: UInt,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
val rule = kit.contextRuleManager.listContextRules().first { it.id == 0u }
val policyId = rule.policyIds.firstOrNull() ?: return
kit.policyManager.removePolicy(
    contextRuleId = 0u,
    policyId = policyId
)
```

### removePolicy — by address

Convenience overload that resolves the ID internally by matching the policy contract address.

```kotlin
suspend fun removePolicy(
    contextRuleId: UInt,
    policyAddress: String,
    selectedSigners: List<SelectedSigner> = emptyList(),
    forceMethod: SubmissionMethod? = null
): TransactionResult
```

```kotlin
kit.policyManager.removePolicy(
    contextRuleId = 0u,
    policyAddress = "CSPENDINGLIMIT000..."
)
```

Throws `ValidationException.InvalidInput` if the policy is not on the rule.

---

## Multi-Signer Operations

`kit.multiSignerManager` (type `OZMultiSignerManager`) coordinates a transaction across more than one signer — either multiple passkeys, one or more external wallets, or a mix. Use when a rule requires a threshold `> 1`, or when you want to collect signatures from separate devices or users.

### When to use which entry point

- **Any state-changing kit method with `selectedSigners` set** — signer/policy/context-rule edits, single-rule token transfers.
- **`multiSignerTransfer`** — token transfer (SEP-41 `transfer` on a token contract) authorized by multiple signers.
- **`multiSignerContractCall`** — arbitrary contract call authorized directly under a `CallContract(target)` rule.
- **`multiSignerExecuteAndSubmit`** — arbitrary contract call routed through the smart account's own `execute(target, target_fn, target_args)` entry point; the target contract sees the smart account as the invoker.

### SelectedSigner

Explicitly list every signer that will sign. There is no implicit "connected passkey" — if the connected passkey should sign, include it.

```kotlin
sealed class SelectedSigner {
    data class Passkey(
        val credentialId: String? = null,            // Base64URL for tracking/logging
        val credentialIdBytes: ByteArray? = null,    // raw bytes -> allowCredentials hint
        val keyData: ByteArray? = null,              // 65-byte pubkey || credentialId
        val transports: List<String>? = null         // e.g. listOf("internal", "hybrid")
    ) : SelectedSigner()

    data class Wallet(val address: String) : SelectedSigner()    // G-address or C-address

    data class Ed25519(                                          // External Ed25519 signer
        val verifierAddress: String,                             // C-address of the Ed25519 verifier
        val publicKey: ByteArray                                 // 32-byte Ed25519 public key
    ) : SelectedSigner()
}
```

Each `Passkey` triggers one OS biometric prompt. Each `Wallet` and `Ed25519` entry is signed through the kit-owned `kit.externalSigners` manager (a wallet adapter may show its own UI).

```kotlin
// WRONG: SelectedSigner.Passkey(credentialId = "abc...")
//        — keyData is required for rule resolution. Without it, the SDK cannot
//          match the passkey to any stored signer and throws ValidationException.
// CORRECT: supply both credentialId (or credentialIdBytes) AND keyData from the
//          signer record already loaded on the client:
val passkeySigner = SelectedSigner.Passkey(
    credentialId = savedCredential.credentialId,      // Base64URL
    credentialIdBytes = Util.base64urlDecode(savedCredential.credentialId),
    keyData = onChainSigner.keyData,                  // pubkey || credId from ParsedContextRule
    transports = savedCredential.transports           // null is fine
)
```

```kotlin
// WRONG: SelectedSigner.Wallet(address = "CCJZ5DGASBWQXR5MPFCJXMBI333XE5U3FSJTNQU7RIKE3P5GN2K2WYD5")  — wallet signers must be G-addresses
// CORRECT: SelectedSigner.Wallet(address = "GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")  — delegated wallet G-address
```

### Building SelectedSigner lists from on-chain rules

Discover which signers are available on a rule by reading `ParsedContextRule.signers` and matching against local credentials/wallets:

```kotlin
// Resolve wallet signing capability through the kit-owned manager. It returns true
// for either an in-memory keypair (kit.externalSigners.addFromSecret) or a wallet
// adapter supplied via config.externalWallet.
val rule = kit.contextRuleManager.listContextRules().first { it.id == contextRuleId }

// Build the list the UI will present. For a passkey signer, look up the
// Base64URL credentialId from keyData and check whether it is stored locally.
val available = rule.signers.mapNotNull { signer ->
    when {
        // ExternalSigner has three keyData shapes (WebAuthn: 65-byte pubkey + credential ID; Ed25519: 32-byte key; other: verifier-specific):
        //   size == ED25519_PUBLIC_KEY_SIZE (32)    -> Ed25519
        //   size  > SECP256R1_PUBLIC_KEY_SIZE (65)  -> WebAuthn passkey (65-byte pubkey || credentialId)
        //   any other size                          -> generic external verifier (no local signer; skipped)
        signer is ExternalSigner && signer.keyData.size == SmartAccountConstants.ED25519_PUBLIC_KEY_SIZE -> {
            // Ed25519 external signer: signable if the kit-owned manager (adapter or
            // in-memory key) can sign for this verifier + public-key slot.
            if (kit.externalSigners.canSignEd25519For(signer.verifierAddress, signer.keyData)) {
                SelectedSigner.Ed25519(
                    verifierAddress = signer.verifierAddress,
                    publicKey       = signer.keyData
                )
            } else null
        }
        signer is ExternalSigner -> {
            // WebAuthn passkey (keyData = 65-byte pubkey || credentialId). A generic external
            // verifier has no credentialId suffix, so getCredentialIdFromSigner returns null
            // and it is skipped.
            val credIdBytes = SmartAccountBuilders.getCredentialIdFromSigner(signer) ?: return@mapNotNull null
            val credIdStr   = SmartAccountBuilders.getCredentialIdStringFromSigner(signer) ?: return@mapNotNull null
            val stored      = kit.credentialManager.getCredential(credIdStr)
            SelectedSigner.Passkey(
                credentialId      = credIdStr,
                credentialIdBytes = credIdBytes,
                keyData           = signer.keyData,
                transports        = stored?.transports
            )
        }
        signer is DelegatedSigner -> {
            if (kit.externalSigners.canSignFor(signer.address)) {
                SelectedSigner.Wallet(signer.address)
            } else null
        }
        else -> null
    }
}
```

### multiSignerTransfer

```kotlin
suspend fun multiSignerTransfer(
    tokenContract: String,
    recipient: String,
    amount: String,                       // decimal string, NOT base units
    decimals: Int? = null,                // token scale; null fetches decimals() on-chain
    selectedSigners: List<SelectedSigner>,
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

Example — a 2-of-2 transfer with the connected passkey plus an external Freighter wallet:

```kotlin
val amount = "100"
val passkey = SelectedSigner.Passkey(
    credentialId      = kit.credentialId,
    credentialIdBytes = kit.credentialId?.let { Util.base64urlDecode(it) },
    keyData           = onChainPasskeySigner.keyData
)
val wallet = SelectedSigner.Wallet("GA7QYNF7SOWQ3GLR2BGMZEHXAVIRZA4KVWLTJJFC7MGXUA74P7UJVSGZ")

val result = kit.multiSignerManager.multiSignerTransfer(
    tokenContract = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
    recipient     = "GCZJM35NKGVK47BB4SPBDV25477PZYIYPVVG453LPYFNXLS3FGHDXOCM",
    amount        = "100",
    selectedSigners = listOf(passkey, wallet)
)
if (result.success) println("Multi-sig transfer ok: ${result.hash}")
```

### multiSignerContractCall

Direct call to an external contract (authorized under a `CallContract` rule on the target).

```kotlin
suspend fun multiSignerContractCall(
    target: String,                       // C-address
    targetFn: String,
    targetArgs: List<SCValXdr> = emptyList(),
    selectedSigners: List<SelectedSigner>,
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

```kotlin
// approve() on a SEP-41 token: from=smart account, spender=dex, amount=100.
// expiration_ledger is an ABSOLUTE ledger sequence — add the offset to the
// current ledger.
val currentLedger = SorobanServer(kit.config.rpcUrl).use { it.getLatestLedger().sequence.toUInt() }
val args = listOf(
    Scv.toAddress(Address(kit.contractId!!).toSCAddress()),
    Scv.toAddress(Address("CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC").toSCAddress()), // the spender (dex) contract
    Scv.toInt128(OZTransactionOperations.amountToBaseUnits("100", decimals = 7)),
    Scv.toUint32(currentLedger + Util.LEDGERS_PER_HOUR.toUInt())
)
kit.multiSignerManager.multiSignerContractCall(
    target = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
    targetFn = "approve",
    targetArgs = args,
    selectedSigners = listOf(passkey, wallet)
)
```

### multiSignerExecuteAndSubmit

Routes the call through the smart account's `execute(target, target_fn, target_args)` entry point. The target contract sees `smart_account_contract_id` as `require_auth` caller, not the underlying signers. Use this for governance votes, multi-sig swaps, or any operation gated by a multi-signer rule on the smart account side.

```kotlin
suspend fun multiSignerExecuteAndSubmit(
    target: String,
    targetFn: String,
    targetArgs: List<SCValXdr> = emptyList(),
    selectedSigners: List<SelectedSigner>,
    forceMethod: SubmissionMethod? = null,
    resolveContextRuleIds: ResolveContextRuleIds? = null
): TransactionResult
```

```kotlin
// Governance vote authorized by two wallet signers
val result = kit.multiSignerManager.multiSignerExecuteAndSubmit(
    target     = "CDAO1234...",
    targetFn   = "vote",
    targetArgs = listOf(
        Scv.toUint32(proposalId),
        Scv.toBoolean(true)
    ),
    selectedSigners = listOf(
        SelectedSigner.Wallet("GAVOTER1..."),
        SelectedSigner.Wallet("GCUZ6YLL5RQBTYLTTQLPCM73C5XAIUGK2TIMWQH7HPSGWVS2KJ2F3CHS")
    )
)
```

### ResolveContextRuleIds (advanced)

```kotlin
typealias ResolveContextRuleIds = suspend (
    entry: SorobanAuthorizationEntryXdr,
    index: Int
) -> List<UInt>
```

The SDK automatically picks which rule IDs each auth entry should invoke, using a three-tier match against `selectedSigners` (exact match, rule-subset, selected-subset — see `OZContextRuleManager.resolveContextRuleIdsForEntry`). Provide this callback when auto-resolution is ambiguous, or to force a specific choice.

```kotlin
// Force all auth entries to use rule 2
val forceRule2: ResolveContextRuleIds = { _, _ -> listOf(2u) }
kit.multiSignerManager.multiSignerTransfer(
    tokenContract = tokenSac,
    recipient = recipient,
    amount = "10",
    selectedSigners = signers,
    resolveContextRuleIds = forceRule2
)
```

If the automatic resolver cannot find a unique rule, it throws `ValidationException.InvalidInput` with one of:

- `"No context rule matches <contextType>..."` — add a matching rule (or a Default).
- `"Selected signers match multiple context rules: 1, 3, ..."` — use `resolveContextRuleIds` to disambiguate.
- `"No context rule contains all selected signers."` — the selected signer set crosses rules; either restrict the selection to one rule's signers or pass `resolveContextRuleIds`.

Before matching, the automatic resolver also throws `ValidationException` for a create-contract auth entry whose executable is a CAP-85 external reference or a Stellar Asset Contract (`"CreateContract invocation references ..."`): a `CreateContract` rule is identified by a 32-byte WASM hash only, and neither executable carries one. Such deployments always need explicit rule IDs via `resolveContextRuleIds`.

### External wallet requirements

For `SelectedSigner.Ed25519` signers, register a signing source via `kit.externalSigners.addEd25519FromRawKey(...)` or `config.externalEd25519Adapter` — see [smart_accounts.md](./smart_accounts.md) External Signer Manager.

A `SelectedSigner.Wallet` (G-address) signer resolves through the kit-owned `kit.externalSigners` manager. Register a signing source by either custody model: register an in-memory keypair at runtime with `kit.externalSigners.addFromSecret("S...")`, or supply an `ExternalWalletAdapter` via `OZSmartAccountConfig.externalWallet` at kit construction. Resolution tries the in-memory keypair first, then the adapter. The adapter interface (minimal):

```kotlin
interface ExternalWalletAdapter {
    suspend fun connect(): ConnectedWallet?
    suspend fun disconnect()
    suspend fun signAuthEntry(
        preimageXdr: String,                     // Base64 HashIDPreimage XDR
        options: SignAuthEntryOptions? = null    // carries networkPassphrase and address
    ): SignAuthEntryResult
    fun getConnectedWallets(): List<ConnectedWallet>
    fun canSignFor(address: String): Boolean     // NOT suspend
}
```

The adapter receives the Base64-encoded `HashIDPreimage::SorobanAuthorization` XDR, hashes it with SHA-256, signs with Ed25519, and returns the 64-byte raw signature as Base64 in `SignAuthEntryResult.signedAuthEntry`. The SDK takes care of building the signed auth entry from the raw signature.

```kotlin
// WRONG: adapter.signAuthEntry(preimageXdr = hexString, ...)
//        — preimageXdr is Base64, NOT hex
// CORRECT: Base64-decode, SHA-256 hash, Ed25519-sign, Base64-encode the signature
```

### Signing order and prompts

Signatures are collected in the order `selectedSigners` is supplied. For each auth entry whose credentials match the smart account contract:

1. Every `SelectedSigner.Passkey` triggers one WebAuthn prompt (the SDK passes its `credentialIdBytes` + `transports` as `allowCredentials` so the browser/OS routes to the correct passkey).
2. Every `SelectedSigner.Wallet` produces a separate delegated auth entry (signed through `kit.externalSigners`) and a placeholder entry in the smart account's internal signature map.

If a passkey signer cancels the biometric prompt, the call fails fast with `WebAuthnException.AuthenticationFailed` — remaining signers are not prompted.

---

## Common Scenarios

Three worked end-to-end flows that combine the building blocks above. Each one assumes `val kit: OZSmartAccountKit` exists and `kit.walletOperations.connectWallet(...)` has returned the connection shown in the preconditions.

### Passkey recovery via backup signer (lost device)

**Preconditions.** A backup `DelegatedSigner(G-address)` was added earlier to the Default rule on a second device, and the user still controls the corresponding Stellar account (through an `ExternalWalletAdapter` configured on `OZSmartAccountConfig.externalWallet`). The original passkey is gone (lost device, reset browser profile, etc.). The smart account's contract ID is known (stored server-side, indexed, or backed up).

**Flow.** Register a fresh passkey on the new device, persist it locally with the known contract ID, connect directly to the contract using the new credential + contract pair, then add the new passkey on-chain authorized by the backup signer, and finally remove the old passkey signer.

```kotlin
val contractId = "CC4DZNN2TPLUOAIRBI3CY7TGRFFCCW6GNVVRRQ3QIIBY6TM6M2RVMBMC"
// The old credential ID must be available from out-of-band storage
// (server-side record, encrypted backup, etc.) so step 5 can locate the
// old passkey's signer entry on the Default rule.
val oldPasskeyCredentialIdBase64Url: String = /* fetched from your backup */ TODO()

// 1. Register a fresh passkey on the new device. Use a cryptographically
//    secure source for challenge and userId (kotlin.random.Random is NOT one);
//    getEd25519Crypto() is the SDK's own CSPRNG.
val crypto       = com.soneso.stellar.sdk.crypto.getEd25519Crypto()
val challenge    = crypto.generatePrivateKey()   // 32 secure random bytes
val userIdBytes  = crypto.generatePrivateKey()
val webauthn     = kit.config.webauthnProvider
    ?: error("webauthnProvider must be configured for recovery")
val reg          = webauthn.register(challenge, userIdBytes, userName = "Recovery Device")
val newCredBytes = reg.credentialId
val newCredB64   = Util.base64urlEncode(newCredBytes)

// 2. Direct connect using the known (credentialId, contractId) pair. When both
//    are supplied, the kit skips session restore, skips WebAuthn, and skips
//    storage lookup for the contract — it trusts the supplied contractId and
//    verifies only that the contract exists on-chain. The new passkey is not
//    yet on-chain, but that is fine: every subsequent call routes signing to
//    the backup via `selectedSigners`.
//
//    IMPORTANT: verify `knownContractId` against at least two independent
//    channels before this step — a deterministic address derivation check
//    against the backup G-address plus an indexer lookup. If an attacker can
//    inject a fake contractId, the user's fresh passkey is added to the
//    attacker's contract.
val connected = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(
        credentialId = newCredB64,
        contractId   = knownContractId
    )
) ?: error("Unable to reconnect to $knownContractId")

// 3. Identify the backup signer on the Default rule. For a delegated G-address
//    held by the user's external wallet:
val backup = SelectedSigner.Wallet("GBPHPX7SZKYEDV5CVOA5JOJE2RHJJDCJMRWMV4KBOIE5VSDJ6VAESR2W")

// 4. Add the new passkey on-chain, authorized by the backup signer. The
//    non-empty `selectedSigners` list is load-bearing: with an empty list the
//    kit routes through single-signer auth and tries to sign with the (non-
//    existent) new passkey, which fails simulation with 3016 UNAUTHORIZED_SIGNER.
val addResult = kit.signerManager.addPasskey(
    contextRuleId   = 0u,
    publicKey       = reg.publicKey,
    credentialId    = newCredBytes,
    selectedSigners = listOf(backup)
)
check(addResult.success) { "add_signer failed: ${addResult.error}" }

// 5. Remove the old passkey, also authorized by the backup signer. Look up
//    its signer ID by matching the old credential ID on the Default rule.
val rule = kit.contextRuleManager.listContextRules().first { it.id == 0u }
val oldIdx = rule.signers.indexOfFirst { signer ->
    val credStr = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
    credStr != null && credStr == oldPasskeyCredentialIdBase64Url
}
if (oldIdx >= 0) {
    kit.signerManager.removeSigner(
        contextRuleId   = 0u,
        signerId        = rule.signerIds[oldIdx],
        selectedSigners = listOf(backup)
    )
}
```

**Why this pattern.** A raw-Ed25519 backup added via `addEd25519` uses a different auth path (the kit's external-signer pipeline) and is not expressible as a `SelectedSigner.Wallet` — register the backup as a delegated G-address instead if you need the multi-signer flow shown here. Direct connect (both `credentialId` and `contractId` supplied) trusts the pair and only verifies contract presence on-chain, so the new credential does not need to be pre-saved locally.

```kotlin
// WRONG: calling webauthnProvider.authenticate on the new device to sign the
//        add_signer call — there is no stored credential for the lost passkey
//        to answer the biometric prompt.
// CORRECT: pass selectedSigners = listOf(backup) so the multi-signer pipeline
//          routes auth to the backup signer through kit.externalSigners.
```

### Signer rotation (add new, then remove old)

**Preconditions.** The user is connected with the current passkey and wants to move to a new hardware authenticator (replacement YubiKey, new device with a fresh platform passkey, etc.). The Default rule currently has one passkey signer and no policies.

**Flow.** Register the new passkey while the old passkey authorizes the add. Then re-connect using the new passkey, and remove the old one. Do the add first — never remove first.

```kotlin
// 1. On the old device, register the new passkey and add it on-chain.
//    addNewPasskeySigner runs register() + createPendingCredential() + add_signer
//    in one call; the old passkey authorizes because selectedSigners is empty.
val added = kit.signerManager.addNewPasskeySigner(
    contextRuleId = 0u,
    userName      = "User name on new device"
)
check(added.transactionResult.success) {
    "add_signer failed: ${added.transactionResult.error}"
}
val newCredentialId = added.credentialId   // Base64URL

// 2. Remember the old credential ID BEFORE reconnecting.
val oldCredentialId = kit.credentialId
    ?: error("No connected credential — old session already lost")

// 3. Re-connect using the new passkey. `addNewPasskeySigner` already persisted
//    the new credential in local storage with the correct contract ID, so
//    connectWallet(credentialId = ...) resolves the contract from storage and
//    sets the new credential as the active session — no WebAuthn prompt.
//    (The `fresh` option has no effect when a `credentialId` is supplied;
//    the direct-connect path skips both session and WebAuthn. If biometric
//    re-verification is required, call `authenticatePasskey` separately.)
val reconn = kit.walletOperations.connectWallet(
    OZWalletOperations.ConnectWalletOptions(
        credentialId = newCredentialId
    )
) ?: error("Failed to reconnect with new passkey")

// 4. Remove the old passkey. The new passkey authorizes (selectedSigners empty
//    -> connected signer signs).
val rule   = kit.contextRuleManager.listContextRules().first { it.id == 0u }
val oldIdx = rule.signers.indexOfFirst { signer ->
    SmartAccountBuilders.getCredentialIdStringFromSigner(signer) == oldCredentialId
}
require(oldIdx >= 0) { "Old passkey not found on Default rule" }
kit.signerManager.removeSigner(
    contextRuleId = 0u,
    signerId      = rule.signerIds[oldIdx]
)
```

**Why this pattern.** Even if the contract's 3004 guard blocks a remove-first attempt, a failure between two transactions leaves no authorized signer — the account becomes unusable until recovery. Add-then-remove keeps authorization coverage at every step.

```kotlin
// WRONG: removeSigner(oldId) before addPasskey(...)  — rule briefly has 0 signers
//        and 0 policies; contract rejects with 3004 NoSignersAndPolicies. Even
//        if it didn't, you could brick the account on failure between the two txs.
// CORRECT: add the new passkey first, reconnect, then remove the old one.
```

### Debugging failed `__check_auth` by reading contract error codes

**Preconditions.** A call like `kit.transactionOperations.transfer(...)`, `kit.signerManager.removeSigner(...)`, or any other kit method throws `TransactionException.SimulationFailed`. The contract rejected the auth check (`__check_auth`) or an enforcement hook on a policy.

**Flow.** The simulation error message contains the host error surfaced by RPC — typically of the form `...Error(Contract, #3004)...` for a smart-account error or `...Error(Contract, #3221)...` for a policy error. Pass the exception's message to `ContractErrorCodes.decodeFromMessage`, which extracts and decodes the first known marker in one step, and act on the result.

```kotlin
val amount = "100"
try {
    kit.transactionOperations.transfer(
        tokenContract = nativeSac,
        recipient     = recipient,
        amount        = "10"
    )
} catch (e: TransactionException.SimulationFailed) {
    // decodeFromMessage scans the message for "Error(Contract, #NNNN)" markers and
    // resolves the first known code to its contract + variant name (or null).
    val decoded = ContractErrorCodes.decodeFromMessage(e.message)
    // Representative cases and action hints:
    val hint = if (decoded == null) {
        "No known contract code in message: ${e.message}"
    } else when (decoded.code) {
        3004 -> "NoSignersAndPolicies — rule would have 0 signers and 0 policies; add one first"
        3016 -> "UnauthorizedSigner — signer not on resolved rule; pass resolveContextRuleIds or adjust selectedSigners"
        3221 -> "SpendingLimit exceeded for the current window; wait for reset or raise the limit"
        else -> "${decoded.contract}.${decoded.name} (#${decoded.code})"
    }
    println("transfer rejected: $hint")
    // Surface SDK-interpreted constants explicitly where they match.
    if (decoded?.code == ContractErrorCodes.UNAUTHORIZED_SIGNER) {
        // Recovery: re-resolve rule IDs or adjust the selected-signer set.
    }
}
```

**Why this pattern.** The `TransactionException.SimulationFailed` message wraps the RPC `simulation.error` string, which is where the host error code lives. There is no typed contract-error exception in the SDK; `ContractErrorCodes.decodeFromMessage(e.message)` extracts and decodes the code in one step — no hand-rolled message parsing is needed. It resolves to an `OZContractError(code, contract, name)` across the full on-chain surface — smart account (3000-3016), WebAuthn (3110-3119), and the policy enums (3200-3227) — returning null when the message is null, carries no marker, or carries only unknown codes; the smart-account codes are also exposed as named constants for direct branching (and `ContractErrorCodes.decode(code)` resolves a code you already hold). The full enum is in [`packages/accounts/src/smart_account/mod.rs`](https://github.com/OpenZeppelin/stellar-contracts/blob/main/packages/accounts/src/smart_account/mod.rs) and cross-referenced in [Contract Error Codes](#contract-error-codes) below.

```kotlin
// WRONG: catch (e: ContractException) { when (e.code) { ... } }  — no such class
// CORRECT: catch TransactionException.SimulationFailed and decode its message

// WRONG: matching on e.code (which is SmartAccountErrorCode.TRANSACTION_SIMULATION_FAILED)
//        — that is the SDK error kind, not the on-chain contract code
// CORRECT: pass e.message to ContractErrorCodes.decodeFromMessage
```

---

## Events

Signer, policy, and context-rule changes do not emit dedicated kit events; they surface as `SmartAccountEvent.TransactionSubmitted`. Kit-level event catalogue and subscription mechanics: [smart_accounts.md — Events](./smart_accounts.md#events). For on-chain events (contract-emitted `signer_added`, `policy_added`, etc.), query `SorobanServer(kit.config.rpcUrl).getEvents(...)` with the smart account's contract ID as the filter — see [rpc.md](./rpc.md).

---

## Contract Error Codes

When the smart account contract rejects a call, the on-chain error code is surfaced as `TransactionException.SimulationFailed` (simulation) or `TransactionException.SubmissionFailed` (submit/poll). Look for the numeric code in the message; it matches the contract's `SmartAccountError` enum.

### Smart account errors (3000 range)

| Code | Symbol | Meaning | Fix |
|------|--------|---------|-----|
| 3000 | ContextRuleNotFound | `contextRuleId` does not exist | Pass a valid ID from `listContextRules()`. IDs are never reused once removed. |
| 3002 | UnvalidatedContext | No rule matches this operation's context type | Add a `CallContract` / `CreateContract` rule, or a `Default` rule. |
| 3003 | ExternalVerificationFailed | Verifier contract rejected the signature | Signature or key data is wrong, or the verifier contract was upgraded. |
| 3004 | NoSignersAndPolicies | Tried to create or reduce a rule to 0 signers and 0 policies | Supply at least one signer or one policy. |
| 3005 | PastValidUntil | `validUntil` is <= current ledger | Compute `validUntil` from a future ledger sequence. |
| 3006 | SignerNotFound | `signerId` not present on the rule | Use `ParsedContextRule.signerIds` to pick a valid ID. |
| 3007 | DuplicateSigner | Signer already on the rule | Each signer (by `uniqueKey`) can appear at most once per rule. |
| 3008 | PolicyNotFound | `policyId` not present on the rule | Use `ParsedContextRule.policyIds`. |
| 3009 | DuplicatePolicy | Policy contract already installed on the rule | Remove the existing installation first, or target a different rule. |
| 3010 | TooManySigners | >15 signers on a rule | Limit to `OZConstants.MAX_SIGNERS = 15`. |
| 3011 | TooManyPolicies | >5 policies on a rule | Limit to `OZConstants.MAX_POLICIES = 5`. |
| 3012 | MathOverflow | Internal ID counter hit `u32::MAX` | Extremely rare; the account has exhausted IDs — create a new account. |
| 3013 | KeyDataTooLarge | External signer `keyData` > 256 bytes | secp256r1 pubkey (65) + credentialId must fit. |
| 3014 | ContextRuleIdsLengthMismatch | SDK-side assembly bug | Report with reproduction — normally resolved by the auto-resolver. |
| 3015 | NameTooLong | Rule `name` > 20 UTF-8 bytes | Shorten the name. |
| 3016 | UnauthorizedSigner | A signer in the auth payload is not on the selected rule | Either adjust `selectedSigners` to match the rule, or pass `resolveContextRuleIds` to select a different rule that includes those signers. |

### SimpleThreshold policy errors (3200 range)

| Code | Symbol | Meaning |
|------|--------|---------|
| 3200 | SmartAccountNotInstalled | Policy was uninstalled or never installed on this smart account |
| 3201 | InvalidThreshold | `threshold == 0` or `threshold > signer_count` |
| 3202 | NotAllowed | Signer count below threshold at enforcement time |
| 3203 | AlreadyInstalled | Policy already installed on this rule (remove first) |

### WeightedThreshold policy errors (3210 range)

| Code | Symbol | Meaning |
|------|--------|---------|
| 3210 | SmartAccountNotInstalled | Policy was uninstalled or never installed |
| 3211 | InvalidThreshold | Threshold is 0 or > sum of weights |
| 3212 | MathOverflow | Weight sum would overflow `u32` |
| 3213 | NotAllowed | Sum of signing signers' weights below threshold |
| 3214 | AlreadyInstalled | Policy already installed on this rule |

### SpendingLimit policy errors (3220 range)

| Code | Symbol | Meaning |
|------|--------|---------|
| 3220 | SmartAccountNotInstalled | Policy was uninstalled or never installed |
| 3221 | SpendingLimitExceeded | Transfer would exceed the limit for the current window |
| 3222 | InvalidLimitOrPeriod | `spendingLimit <= 0` or `periodLedgers == 0` |
| 3223 | NotAllowed | Generic policy rejection at enforcement time |
| 3224 | HistoryCapacityExceeded | Transfer history exceeds 1000 entries per account/rule |
| 3225 | AlreadyInstalled | Policy already installed on this rule |
| 3226 | LessThanZero | `transfer` amount argument is negative |
| 3227 | OnlyCallContractAllowed | Policy installed on a `Default` or `CreateContract` rule; only `CallContract` rules are supported |

### Handling pattern

```kotlin
try {
    val res = kit.policyManager.addSimpleThreshold(0u, policyAddress, threshold = 2u)
    if (!res.success) println("submit failed: ${res.error}")
} catch (e: TransactionException.SimulationFailed) {
    // The message contains the contract error code and name
    println("simulation failed: ${e.message}")
} catch (e: ValidationException.InvalidInput) {
    println("client-side validation: ${e.message}")
} catch (e: WalletException.NotConnected) {
    println("call connectWallet() first")
}
```

See also: SDK error hierarchy and exception types in [smart_accounts.md — Error Handling](./smart_accounts.md#error-handling).
