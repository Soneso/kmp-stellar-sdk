package com.soneso.smartdemo.util

import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.ValidationException
import com.soneso.stellar.sdk.smartaccount.oz.OZTransactionOperations
import com.soneso.stellar.sdk.smartaccount.oz.PolicyInstallParams
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Builds the [SCValXdr] for a simple threshold policy.
 *
 * Delegates validation and encoding to [PolicyInstallParams.SimpleThreshold].
 *
 * On-chain map structure: `{ "threshold": U32(threshold) }`
 *
 * @param threshold Number of signatures required.
 * @return Encoded SCVal map for the threshold policy.
 * @throws ValidationException if threshold is less than 1.
 */
fun buildSimpleThresholdScVal(threshold: UInt): SCValXdr {
    return PolicyInstallParams.SimpleThreshold(threshold).toScVal()
}

/**
 * Builds the [SCValXdr] for a spending limit policy from a pre-converted base-units value.
 *
 * On-chain map structure: `{ "period_ledgers": U32(periodLedgers), "spending_limit": I128(baseUnits) }`
 *
 * @param baseUnits Maximum spending amount in the token's base units.
 * @param periodLedgers Reset period expressed as a number of ledgers.
 * @return Encoded SCVal map for the spending limit policy.
 * @throws ValidationException if [baseUnits] or [periodLedgers] is not positive.
 */
fun buildSpendingLimitScVal(baseUnits: BigInteger, periodLedgers: UInt): SCValXdr {
    return PolicyInstallParams.SpendingLimit(baseUnits, periodLedgers).toScVal()
}

/**
 * Builds the [SCValXdr] for a spending limit policy from a display amount string.
 *
 * The amount is converted with the demo token scale (7 decimals) via
 * [OZTransactionOperations.amountToBaseUnits]; validation and encoding are
 * delegated to [PolicyInstallParams.SpendingLimit].
 *
 * On-chain map structure: `{ "period_ledgers": U32(periodLedgers), "spending_limit": I128(baseUnits) }`
 *
 * @param amountStr Spending limit as a decimal string (e.g., "100.0").
 * @param periodLedgers Reset period expressed as a number of ledgers.
 * @return Encoded SCVal map for the spending limit policy.
 * @throws ValidationException if [amountStr] is not a valid positive amount with at
 *   most 7 fractional digits, or [periodLedgers] is less than 1.
 */
fun buildSpendingLimitScVal(amountStr: String, periodLedgers: UInt): SCValXdr {
    val baseUnits = OZTransactionOperations.amountToBaseUnits(amountStr, decimals = 7)
    return buildSpendingLimitScVal(baseUnits, periodLedgers)
}

/**
 * Returns true if [amountStr] is a valid spending limit amount (parseable by
 * [OZTransactionOperations.amountToBaseUnits] at the demo token scale without throwing).
 *
 * @param amountStr The amount string to validate.
 */
fun isValidSpendingAmount(amountStr: String): Boolean {
    return try {
        OZTransactionOperations.amountToBaseUnits(amountStr, decimals = 7)
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Builds the [SCValXdr] for a weighted threshold policy.
 *
 * Checks the demo-level constraints (all weights > 0, total weight >= threshold)
 * before delegating validation and encoding to [PolicyInstallParams.WeightedThreshold].
 *
 * On-chain map structure: `{ "signer_weights": Map[Signer => U32], "threshold": U32(threshold) }`
 *
 * The signer weights map is sorted by XDR key order as required by the contract.
 *
 * @param weights Map of each signer to its assigned voting weight.
 * @param threshold Minimum total weight required for authorization.
 * @return Encoded SCVal map for the weighted threshold policy.
 * @throws ValidationException if threshold is less than 1, the weights map is empty,
 *   any weight is less than 1, or the total weight is less than the threshold.
 */
fun buildWeightedThresholdScVal(
    weights: Map<SmartAccountSigner, UInt>,
    threshold: UInt
): SCValXdr {
    var totalWeight = 0uL
    for ((_, weight) in weights) {
        if (weight == 0u) {
            throw ValidationException.invalidInput(
                "signerWeights",
                "All weights must be positive integers, got: $weight"
            )
        }
        totalWeight += weight.toULong()
    }
    if (totalWeight < threshold.toULong()) {
        throw ValidationException.invalidInput(
            "signerWeights",
            "Sum of weights ($totalWeight) must be >= threshold ($threshold)"
        )
    }

    return PolicyInstallParams.WeightedThreshold(weights, threshold).toScVal()
}
