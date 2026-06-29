package com.soneso.smartdemo.flows

/**
 * Business logic for the "Delegate to agent" screen (step 2 of the agent-signer flow).
 *
 * [delegateToAgent] composes a single `addContextRule` call that grants an autonomous
 * agent a scoped, spend-capped, time-bounded authority on the connected smart account:
 *
 * - context type `CallContract(token)` — the rule only matches calls to the one token
 *   contract the agent may touch.
 * - signers `[Ed25519 external signer]` — the agent's Ed25519 public key, verified
 *   through the Ed25519 verifier contract. The agent owns the matching secret; only its
 *   public key is pasted into this screen.
 * - policies `{ spending-limit: cap per period }` — a maximum spend over a rolling ledger
 *   window.
 * - `validUntil` — an absolute ledger past which the rule stops applying.
 *
 * The rule is built through the SAME path the context-rule builder uses
 * ([addContextRule], [resolveAbsoluteLedger], [buildEd25519Signer]) so the composition
 * lives in one place. The cap is FAIL-CLOSED: the guarded token's decimal scale is resolved
 * at submit time and the spending-limit policy SCVal is pre-encoded and validated at that
 * scale before any passkey ceremony, so the delegation can never reach the chain with the
 * agent signer and token scope but the spend cap silently dropped or encoded at the wrong
 * precision. A token whose `decimals()` cannot be read aborts the delegation.
 */

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.config.KNOWN_POLICIES
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.token.DemoTokenService
import com.soneso.smartdemo.util.buildSpendingLimitScVal
import com.soneso.smartdemo.util.hexToByteArray
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.smartdemo.util.truncateAddress
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType
import com.soneso.stellar.sdk.smartaccount.oz.OZTransactionOperations
import com.soneso.stellar.sdk.xdr.SCValXdr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/** Default human-readable name for the delegation context rule (within the 20-byte limit). */
const val DEFAULT_DELEGATION_RULE_NAME = "Agent"

/**
 * Strict decimal grammar for the spending-limit cap: an optional leading minus, digits, and
 * at most one fractional dot. It mirrors the amount grammar
 * [OZTransactionOperations.amountToBaseUnits] enforces, and rejects the `Double`-parseable
 * tokens that are not finite decimals — `NaN`, `Infinity`, and hex-float literals. A leading
 * minus is admitted so a negative cap reaches the positivity check and gets the specific
 * "greater than zero" message rather than a generic parse error.
 */
private val DECIMAL_AMOUNT_REGEX = Regex("^-?[0-9]+(\\.[0-9]+)?$")

/**
 * Guards against a concurrent second [delegateToAgent] submission. A single delegate screen
 * drives the flow, so an in-flight delegation rejects a re-entrant call rather than composing
 * and submitting a second context rule while the first is still settling.
 */
private val delegationInFlight = Mutex()

/**
 * Structured description of an authorised delegation, shown on the confirmation card
 * after a successful submission.
 *
 * @property agentPublicKey The agent's Ed25519 public key (64-char hex) that was authorised.
 * @property tokenContract The token contract the agent is scoped to (C-address).
 * @property amount The human-readable spending cap (decimal string).
 * @property periodLedgers The spending-limit rolling window in ledgers.
 * @property validUntilLedger Absolute ledger past which the rule expires, or null for never.
 * @property ruleName The context-rule name written on-chain.
 * @property spendingLimitPolicyAddress The spending-limit policy contract address (C-address).
 * @property verifierAddress The Ed25519 verifier contract address (C-address).
 */
data class DelegationSummary(
    val agentPublicKey: String,
    val tokenContract: String,
    val amount: String,
    val periodLedgers: UInt,
    val validUntilLedger: UInt?,
    val ruleName: String,
    val spendingLimitPolicyAddress: String,
    val verifierAddress: String,
)

/**
 * Outcome of a [delegateToAgent] call.
 *
 * @property success True when the `addContextRule` transaction confirmed on-chain.
 * @property hash On-chain transaction hash on success.
 * @property error Sanitised user-facing message on failure.
 * @property summary Structured rule summary on success.
 */
data class DelegationResult(
    val success: Boolean,
    val hash: String? = null,
    val error: String? = null,
    val summary: DelegationSummary? = null,
)

/** The spending-limit policy contract address from [KNOWN_POLICIES], or empty if none. */
val spendingLimitPolicyAddress: String
    get() = KNOWN_POLICIES.firstOrNull { it.type == "spending_limit" }?.address ?: ""

/**
 * Validates [value] as the agent's raw 32-byte Ed25519 public key in hex.
 *
 * Returns null when [value] is empty (so the form is not flagged on initial render) or
 * when it is exactly 64 hex characters. Returns an error string otherwise.
 */
fun validateAgentPublicKey(value: String): String? {
    val raw = value.trim().lowercase()
    if (raw.isEmpty()) return null
    if (raw.length != 64) {
        return "Must be 64 hex characters (32 bytes), got ${raw.length}"
    }
    if (!isHexString(raw)) {
        return "Invalid hex characters"
    }
    return null
}

/**
 * Validates [value] against the spending-limit amount rules.
 *
 * Returns null when [value] is empty. Otherwise returns one of the validation error
 * strings when the value uses scientific notation, a comma decimal separator, is not a
 * finite decimal (`NaN`, `Infinity`, or a hex float), fails parsing, or is not positive. A
 * comma is rejected rather than normalised: the cap is encoded by
 * [OZTransactionOperations.amountToBaseUnits], whose strict grammar accepts only a dot, so
 * normalising here could let a bad input pass the form and then drop the cap at encoding time.
 */
fun validateDelegationAmount(value: String): String? {
    if (value.isEmpty()) return null
    val trimmed = value.trim()
    if (trimmed.lowercase().contains("e")) {
        return "Scientific notation is not supported"
    }
    if (trimmed.contains(",")) {
        return "Use a dot for the decimal separator"
    }
    if (trimmed.count { it == '.' } > 1) {
        return "Must be a valid number"
    }
    // Restrict to a finite decimal grammar before parsing. toDoubleOrNull would otherwise
    // accept NaN, +/-Infinity, and hex-float literals as "valid" numbers.
    if (!DECIMAL_AMOUNT_REGEX.matches(trimmed)) {
        return "Must be a valid number"
    }
    val parsed = trimmed.toDoubleOrNull() ?: return "Must be a valid number"
    // A grammatically valid but astronomically large value overflows to Infinity on parse;
    // reject it rather than let the non-finite slip past the positivity check.
    if (!parsed.isFinite()) {
        return "Must be a valid number"
    }
    if (parsed <= 0.0) {
        return "Must be greater than zero"
    }
    return null
}

/** Returns true if [value] is a non-empty, even-length string of hex digits. */
private fun isHexString(value: String): Boolean {
    if (value.isEmpty() || value.length % 2 != 0) return false
    return value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

/**
 * Resolves the decimal scale of the spending-limit guarded token.
 *
 * The native token and the demo token resolve without a network call (both use
 * [DemoConfig.DEMO_TOKEN_DECIMALS]); any other token contract's own `decimals()` is
 * fetched on-chain. The amount-to-base-units conversion in [delegateToAgent] must use
 * the value returned here so the cap is scaled with the correct precision.
 *
 * @param tokenContract The guarded token contract (C-address), or empty for no specific token.
 * @return The number of decimal places to use for the cap conversion.
 * @throws Exception if the token's `decimals()` cannot be read.
 */
suspend fun resolveSpendingLimitDecimals(tokenContract: String): Int {
    val token = tokenContract.trim()
    if (token.isEmpty() ||
        token == DemoConfig.NATIVE_TOKEN_CONTRACT ||
        token == DemoState.demoTokenContractId
    ) {
        return DemoConfig.DEMO_TOKEN_DECIMALS
    }
    val client = ContractClient.forContract(
        contractId = token,
        rpcUrl = DemoConfig.RPC_URL,
        network = Network(DemoConfig.NETWORK_PASSPHRASE),
    )
    return try {
        val sourceAccountId = DemoTokenService.getAdminAccountId()
        val result = client.invoke<SCValXdr>(
            functionName = "decimals",
            arguments = emptyMap(),
            source = sourceAccountId,
            signer = null,
        )
        Scv.fromUint32(result).toInt()
    } finally {
        client.close()
    }
}

/**
 * Composes and submits ONE `addContextRule` call delegating scoped, spend-capped,
 * time-bounded authority to the agent.
 *
 * @param agentPublicKey The agent's raw 32-byte Ed25519 public key as 64-char hex.
 * @param tokenContract The single token the rule scopes to via `CallContract`.
 * @param amount The spending cap as a human decimal string, converted at the guarded token's
 *   resolved decimal scale.
 * @param periodLedgers The spending-limit rolling window in ledgers.
 * @param validUntilOffsetLedgers Ledgers from now at which the rule expires; resolved to an
 *   absolute ledger. A value of 0 produces no expiry.
 * @param ruleName Human-readable rule name written on-chain.
 * @param submitContextRule Seam that submits the composed rule. Defaults to the top-level
 *   [addContextRule], which authorises and submits against `DemoState.kit`. Injected in tests
 *   to capture the composed context type, signers, policies, and `validUntil` without a chain.
 * @param resolveValidUntilLedger Seam that maps the expiry offset to an absolute ledger.
 *   Defaults to [resolveAbsoluteLedger], which reads the current ledger over RPC. Injected in
 *   tests to supply a deterministic current ledger and to assert it is not read when the offset
 *   is zero.
 * @param resolveTokenDecimals Seam that resolves the guarded token's decimal scale at submit
 *   time. Defaults to [resolveSpendingLimitDecimals], which reads the token's `decimals()` over
 *   RPC for non-native custom tokens. Injected in tests to supply a deterministic scale and to
 *   exercise the fail-closed path when the scale cannot be read.
 * @return A [DelegationResult]; on failure [DelegationResult.error] is safe to display.
 */
suspend fun delegateToAgent(
    agentPublicKey: String,
    tokenContract: String,
    amount: String,
    periodLedgers: UInt,
    validUntilOffsetLedgers: UInt,
    ruleName: String = DEFAULT_DELEGATION_RULE_NAME,
    submitContextRule: suspend (
        contextType: ContextRuleType,
        name: String,
        validUntil: UInt?,
        signers: List<SmartAccountSigner>,
        policies: List<FlowPolicyEntry>,
    ) -> ContextRuleResult = { contextType, name, validUntil, signers, policies ->
        addContextRule(contextType, name, validUntil, signers, policies)
    },
    resolveValidUntilLedger: suspend (offset: UInt) -> UInt = { offset ->
        resolveAbsoluteLedger(offset)
    },
    resolveTokenDecimals: suspend (tokenContract: String) -> Int = { token ->
        resolveSpendingLimitDecimals(token)
    },
): DelegationResult {
    // Reject a re-entrant submission while a delegation is already running so a second
    // context rule cannot be composed and submitted before the first one settles.
    if (!delegationInFlight.tryLock()) {
        return DelegationResult(
            success = false,
            error = "A delegation is already in progress.",
        )
    }
    return try {
        delegateToAgentGuarded(
            agentPublicKey = agentPublicKey,
            tokenContract = tokenContract,
            amount = amount,
            periodLedgers = periodLedgers,
            validUntilOffsetLedgers = validUntilOffsetLedgers,
            ruleName = ruleName,
            submitContextRule = submitContextRule,
            resolveValidUntilLedger = resolveValidUntilLedger,
            resolveTokenDecimals = resolveTokenDecimals,
        )
    } finally {
        delegationInFlight.unlock()
    }
}

/**
 * Body of [delegateToAgent], run while the [delegationInFlight] guard is held. Split out so the
 * guard's acquire/release brackets the whole composition with a single `try`/`finally`.
 */
private suspend fun delegateToAgentGuarded(
    agentPublicKey: String,
    tokenContract: String,
    amount: String,
    periodLedgers: UInt,
    validUntilOffsetLedgers: UInt,
    ruleName: String,
    submitContextRule: suspend (
        contextType: ContextRuleType,
        name: String,
        validUntil: UInt?,
        signers: List<SmartAccountSigner>,
        policies: List<FlowPolicyEntry>,
    ) -> ContextRuleResult,
    resolveValidUntilLedger: suspend (offset: UInt) -> UInt,
    resolveTokenDecimals: suspend (tokenContract: String) -> Int,
): DelegationResult {
    val trimmedKey = agentPublicKey.trim().lowercase()
    if (trimmedKey.length != 64 || !isHexString(trimmedKey)) {
        return DelegationResult(
            success = false,
            error = "Enter the agent Ed25519 public key as 64 hex characters.",
        )
    }

    val agentKeyBytes = try {
        hexToByteArray(trimmedKey)
    } catch (_: Exception) {
        return DelegationResult(
            success = false,
            error = "Enter the agent Ed25519 public key as 64 hex characters.",
        )
    }

    val trimmedToken = tokenContract.trim()
    val trimmedAmount = amount.trim()

    // Resolve the guarded token's decimal scale at submit time and FAIL CLOSED. The cap is
    // encoded at this precision, so trusting a scale captured from transient keystroke-driven
    // UI state could scale the cap wrongly. A non-native custom token whose decimals() cannot
    // be read aborts the delegation rather than encoding the cap at a guessed scale.
    val tokenDecimals: Int = try {
        resolveTokenDecimals(trimmedToken)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        val message = "Could not read the token's decimal precision, so the spending cap " +
            "cannot be scaled safely. Delegation aborted."
        ActivityLogState.error(message)
        return DelegationResult(success = false, error = message)
    }

    // Pre-encode the spending-limit policy on the EXACT amount string that will be
    // submitted, through the IDENTICAL conversion path addContextRule uses. This is the
    // fail-closed contract: every encoding failure (a comma the strict grammar rejects,
    // a precision the token scale cannot represent, a cap outside the i128 range) is a
    // hard validation error here, before any passkey ceremony. Because the encoding is
    // deterministic, a spec that encodes here also encodes inside addContextRule.
    val policyScVal: SCValXdr = try {
        val baseUnits = OZTransactionOperations.amountToBaseUnits(trimmedAmount, tokenDecimals)
        buildSpendingLimitScVal(baseUnits, periodLedgers)
    } catch (_: Exception) {
        val message = "Enter a spending limit greater than zero, using a dot for decimals, " +
            "with at most $tokenDecimals decimal places and within the supported range."
        ActivityLogState.error(message)
        return DelegationResult(success = false, error = message)
    }

    val policyAddress = spendingLimitPolicyAddress
    if (policyAddress.isEmpty()) {
        val message = "Spending-limit policy is not configured."
        ActivityLogState.error(message)
        return DelegationResult(success = false, error = message)
    }

    // buildEd25519Signer validates the 32-byte key length and stamps the configured
    // verifier address; CallContract scopes the rule to the token contract.
    val agentSigner: ExternalSigner = try {
        buildEd25519Signer(agentKeyBytes)
    } catch (e: Exception) {
        val message = e.message ?: "Invalid agent public key."
        ActivityLogState.error(message)
        return DelegationResult(success = false, error = message)
    }
    val contextType: ContextRuleType = ContextRuleType.CallContract(trimmedToken)

    // Resolve the expiry offset to an absolute ledger from the current one.
    val validUntil: UInt? = if (validUntilOffsetLedgers > 0u) {
        try {
            resolveValidUntilLedger(validUntilOffsetLedgers)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "Failed to resolve the expiry ledger: ${e.message ?: "Unknown error"}"
            ActivityLogState.error(message)
            return DelegationResult(success = false, error = message)
        }
    } else {
        null
    }

    val policies = listOf(FlowPolicyEntry(address = policyAddress, scVal = policyScVal))

    ActivityLogState.info(
        "Delegating to agent ${truncateAddress(trimmedKey)} " +
            "scoped to ${truncateAddress(trimmedToken)}..."
    )

    val result: ContextRuleResult = try {
        submitContextRule(
            contextType,
            ruleName,
            validUntil,
            listOf<SmartAccountSigner>(agentSigner),
            policies,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val message = e.message ?: "Delegation failed."
        if (isUserCancellation(message)) {
            ActivityLogState.info("Passkey authentication cancelled")
            return DelegationResult(success = false, error = "Passkey authentication cancelled")
        }
        ActivityLogState.error("Delegation failed: $message")
        return DelegationResult(success = false, error = message)
    }

    if (!result.success) {
        return DelegationResult(success = false, error = result.error ?: "Delegation failed.")
    }

    return DelegationResult(
        success = true,
        hash = result.hash,
        summary = DelegationSummary(
            agentPublicKey = trimmedKey,
            tokenContract = trimmedToken,
            amount = trimmedAmount,
            periodLedgers = periodLedgers,
            validUntilLedger = validUntil,
            ruleName = ruleName,
            spendingLimitPolicyAddress = policyAddress,
            verifierAddress = DemoConfig.ED25519_VERIFIER_ADDRESS,
        ),
    )
}
