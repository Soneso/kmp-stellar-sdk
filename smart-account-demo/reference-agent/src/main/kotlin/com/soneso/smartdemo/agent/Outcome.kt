package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.smartaccount.core.ContractErrorCodes
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountException
import com.soneso.stellar.sdk.smartaccount.oz.TransactionResult

/**
 * Classification of a single scoped contract-call attempt.
 *
 * A call either confirmed on-chain ([Succeeded]), was rejected by the
 * smart-account contract with a parseable error code ([Rejected]), or failed
 * for a non-contract reason such as a network error ([Failed]). Only a
 * [Rejected] outcome is a policy decision that the agent escalates.
 */
sealed class CallOutcome {
    /** The scoped call confirmed on-chain, carrying the transaction hash. */
    data class Succeeded(val hash: String) : CallOutcome()

    /**
     * The smart-account contract rejected the call with an on-chain error code.
     *
     * [errorCode] is the integer extracted from the contract error in the
     * failure message (e.g. `Error(Contract, #3016)` yields `3016`). [errorName]
     * is the symbolic name when [errorCode] matches a known [ContractErrorCodes]
     * constant, otherwise `null`. [rawMessage] is the failure text the code was
     * parsed from.
     */
    data class Rejected(
        val errorCode: Int,
        val errorName: String?,
        val rawMessage: String,
    ) : CallOutcome()

    /**
     * The call failed for a reason other than a contract rejection (for example
     * a network or simulation error). The agent does not escalate these.
     */
    data class Failed(val message: String) : CallOutcome()
}

/** Maps and parses OpenZeppelin smart-account contract error codes. */
object ContractErrorClassifier {

    /** Symbolic names for the [ContractErrorCodes] constants the SDK documents. */
    val knownCodes: Map<Int, String> = mapOf(
        ContractErrorCodes.MATH_OVERFLOW to "mathOverflow",
        ContractErrorCodes.KEY_DATA_TOO_LARGE to "keyDataTooLarge",
        ContractErrorCodes.CONTEXT_RULE_IDS_LENGTH_MISMATCH to "contextRuleIdsLengthMismatch",
        ContractErrorCodes.NAME_TOO_LONG to "nameTooLong",
        ContractErrorCodes.UNAUTHORIZED_SIGNER to "unauthorizedSigner",
    )

    /** Matches `#<digits>` as it appears in `Error(Contract, #3016)`. */
    private val codePattern = Regex("#(\\d+)")

    /**
     * Returns the integer contract error code embedded in [message], or `null`
     * when no `#<digits>` token is present.
     */
    fun parseContractErrorCode(message: String): Int? =
        codePattern.find(message)?.groupValues?.get(1)?.toIntOrNull()

    /**
     * Returns the symbolic name for [code], or `null` when it is not a known
     * [ContractErrorCodes] constant.
     */
    fun nameForCode(code: Int): String? = knownCodes[code]

    /**
     * Returns the message text of [error], preferring the SDK exception's own
     * `message` over its string description.
     */
    fun messageOf(error: Throwable): String =
        if (error is SmartAccountException) error.message else error.toString()
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
    return if (code != null) {
        CallOutcome.Rejected(
            errorCode = code,
            errorName = ContractErrorClassifier.nameForCode(code),
            rawMessage = message,
        )
    } else {
        CallOutcome.Failed(message)
    }
}
