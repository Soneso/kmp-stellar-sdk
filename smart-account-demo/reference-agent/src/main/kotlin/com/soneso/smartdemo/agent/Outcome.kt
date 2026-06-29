package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.smartaccount.core.ContractErrorCodes
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountException
import com.soneso.stellar.sdk.smartaccount.oz.TransactionResult

/**
 * Classification of a single scoped contract-call attempt.
 *
 * A call either confirmed on-chain ([Succeeded]), was denied by an on-chain
 * policy or authorization rule ([Rejected]), or failed for any other reason
 * ([Failed]). Only a [Rejected] outcome is a policy decision the agent escalates
 * for human review; every other failure — including a contract error code that
 * is not a policy denial — is a [Failed] that the agent reports without
 * escalating.
 */
sealed class CallOutcome {
    /** The scoped call confirmed on-chain, carrying the transaction hash. */
    data class Succeeded(val hash: String) : CallOutcome()

    /**
     * An on-chain policy denied the call, and the agent escalates it.
     *
     * [errorCode] is the integer extracted from the contract error in the
     * failure message (e.g. `Error(Contract, #3221)` yields `3221`) and is one of
     * [ContractErrorClassifier.escalatableCodes]. [errorName] is the symbolic name
     * of that code. [rawMessage] is the failure text the code was parsed from.
     */
    data class Rejected(
        val errorCode: Int,
        val errorName: String?,
        val rawMessage: String,
    ) : CallOutcome()

    /**
     * The call failed for a reason that is not an escalatable policy denial: a
     * network or simulation error, or a contract error code that is a credential,
     * installation, or arithmetic fault rather than a policy decision. The agent
     * does not escalate these.
     */
    data class Failed(val message: String) : CallOutcome()
}

/** Maps and parses OpenZeppelin smart-account contract error codes. */
object ContractErrorClassifier {

    /**
     * Policy and authorization denial codes the OZ context-rule pipeline emits
     * when a correctly installed policy rejects an otherwise-valid call, mapped to
     * their symbolic names. These are the only codes the agent escalates: a human
     * reviewer can approve and re-submit the call under their own (Default-rule)
     * authority, which is not bound by the agent's scoped policy.
     *
     * Sourced from the OZ policy contracts (`OpenZeppelin/stellar-contracts`,
     * `packages/accounts`), each raised from a policy's `enforce` step: the
     * spending-limit "limit exceeded" code (the demo's canonical scenario) and the
     * "not allowed" denial of each threshold and spending-limit policy. Other
     * contract codes (credential faults such as `UNAUTHORIZED_SIGNER`, policy
     * installation or configuration faults, and arithmetic overflows) are not
     * policy decisions and are classified as [CallOutcome.Failed].
     */
    val escalatableCodes: Map<Int, String> = mapOf(
        SIMPLE_THRESHOLD_NOT_ALLOWED to "simpleThresholdNotAllowed",
        WEIGHTED_THRESHOLD_NOT_ALLOWED to "weightedThresholdNotAllowed",
        SPENDING_LIMIT_EXCEEDED to "spendingLimitExceeded",
        SPENDING_LIMIT_NOT_ALLOWED to "spendingLimitNotAllowed",
    )

    /** Symbolic names for the [ContractErrorCodes] credential constants the SDK documents. */
    val knownCodes: Map<Int, String> = mapOf(
        ContractErrorCodes.MATH_OVERFLOW to "mathOverflow",
        ContractErrorCodes.KEY_DATA_TOO_LARGE to "keyDataTooLarge",
        ContractErrorCodes.CONTEXT_RULE_IDS_LENGTH_MISMATCH to "contextRuleIdsLengthMismatch",
        ContractErrorCodes.NAME_TOO_LONG to "nameTooLong",
        ContractErrorCodes.UNAUTHORIZED_SIGNER to "unauthorizedSigner",
    )

    /** Matches `#<digits>` as it appears in `Error(Contract, #3221)`. */
    private val codePattern = Regex("#(\\d+)")

    /**
     * Returns the integer contract error code embedded in [message], or `null`
     * when no `#<digits>` token is present.
     */
    fun parseContractErrorCode(message: String): Int? =
        codePattern.find(message)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Whether [code] is a policy/authorization denial the agent escalates.
     */
    fun isEscalatable(code: Int): Boolean = escalatableCodes.containsKey(code)

    /**
     * Returns the symbolic name for [code] — a policy denial from
     * [escalatableCodes] or a documented [ContractErrorCodes] credential
     * constant — or `null` when [code] matches neither.
     */
    fun nameForCode(code: Int): String? = escalatableCodes[code] ?: knownCodes[code]

    /**
     * Returns the message text of [error], preferring the SDK exception's own
     * `message` over its string description.
     */
    fun messageOf(error: Throwable): String =
        if (error is SmartAccountException) error.message else error.toString()

    /**
     * Simple-threshold policy denial: the M-of-N signer threshold was not met.
     * From `SimpleThresholdError::NotAllowed` in the OZ policy contract.
     */
    private const val SIMPLE_THRESHOLD_NOT_ALLOWED = 3202

    /**
     * Weighted-threshold policy denial: the accumulated signer weight did not
     * reach the threshold. From `WeightedThresholdError::NotAllowed`.
     */
    private const val WEIGHTED_THRESHOLD_NOT_ALLOWED = 3213

    /**
     * Spending-limit policy denial: the transfer would exceed the configured cap
     * for the current period. From `SpendingLimitError::SpendingLimitExceeded`.
     * This is the rejection the demo's agent flow triggers.
     */
    private const val SPENDING_LIMIT_EXCEEDED = 3221

    /**
     * Spending-limit policy denial: the call is not permitted by the policy. From
     * `SpendingLimitError::NotAllowed`.
     */
    private const val SPENDING_LIMIT_NOT_ALLOWED = 3223
}

/** Classifies a [TransactionResult] returned by the multi-signer pipeline. */
fun classifyResult(result: TransactionResult): CallOutcome {
    if (result.success) {
        return CallOutcome.Succeeded(result.hash ?: "")
    }
    return classifyFailureMessage(result.error ?: "Unknown submission error")
}

/** Classifies an error thrown by the multi-signer pipeline. */
fun classifyError(error: Throwable): CallOutcome =
    classifyFailureMessage(ContractErrorClassifier.messageOf(error))

private fun classifyFailureMessage(message: String): CallOutcome {
    val code = ContractErrorClassifier.parseContractErrorCode(message)
    return if (code != null && ContractErrorClassifier.isEscalatable(code)) {
        CallOutcome.Rejected(
            errorCode = code,
            errorName = ContractErrorClassifier.nameForCode(code),
            rawMessage = message,
        )
    } else {
        // No code, or a contract code that is not a policy denial (a credential,
        // installation, or arithmetic fault): a failure the agent does not escalate.
        CallOutcome.Failed(message)
    }
}
