package com.soneso.smartdemo.util

import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSharedUtils
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Builds the [SCValXdr] for a simple threshold policy.
 *
 * Delegates to [SmartAccountBuilders.createThresholdParams] for validation (threshold >= 1),
 * then encodes the result as an SCVal map.
 *
 * On-chain map structure: `{ "threshold": U32(threshold) }`
 *
 * @param threshold Number of signatures required.
 * @return Encoded SCVal map for the threshold policy.
 * @throws ValidationException if threshold is less than 1.
 */
fun buildSimpleThresholdScVal(threshold: UInt): SCValXdr {
    SmartAccountBuilders.createThresholdParams(threshold.toInt())
    val map = linkedMapOf(
        Scv.toSymbol("threshold") to Scv.toUint32(threshold)
    )
    return Scv.toMap(map)
}

/**
 * Builds the [SCValXdr] for a spending limit policy from a pre-converted stroops value.
 *
 * On-chain map structure: `{ "period_ledgers": U32(periodLedgers), "spending_limit": I128(stroops) }`
 *
 * @param stroops Maximum spending amount in stroops.
 * @param periodLedgers Reset period expressed as a number of ledgers.
 * @return Encoded SCVal map for the spending limit policy.
 */
fun buildSpendingLimitScVal(stroops: BigInteger, periodLedgers: UInt): SCValXdr {
    val limitI128 = SmartAccountSharedUtils.stroopsToI128ScVal(stroops)
    val map = linkedMapOf(
        Scv.toSymbol("period_ledgers") to Scv.toUint32(periodLedgers),
        Scv.toSymbol("spending_limit") to limitI128
    )
    return Scv.toMap(map)
}

/**
 * Builds the [SCValXdr] for a spending limit policy from a display amount string.
 *
 * Delegates to [SmartAccountBuilders.createSpendingLimitParams] for validation (positive amount,
 * period >= 1), then encodes the result as an SCVal map.
 *
 * On-chain map structure: `{ "period_ledgers": U32(periodLedgers), "spending_limit": I128(stroops) }`
 *
 * @param amountStr Spending limit as a decimal string (e.g., "100.0").
 * @param periodLedgers Reset period expressed as a number of ledgers.
 * @return Encoded SCVal map for the spending limit policy.
 * @throws ValidationException if [amountStr] cannot be parsed as a valid positive amount
 *   or [periodLedgers] is less than 1.
 */
fun buildSpendingLimitScVal(amountStr: String, periodLedgers: UInt): SCValXdr {
    val params = SmartAccountBuilders.createSpendingLimitParams(amountStr, periodLedgers.toInt())
    return buildSpendingLimitScVal(params.spendingLimit, periodLedgers)
}

/**
 * Returns true if [amountStr] is a valid spending limit amount (parseable by
 * [SmartAccountSharedUtils.amountToStroops] without throwing).
 *
 * @param amountStr The amount string to validate.
 */
fun isValidSpendingAmount(amountStr: String): Boolean {
    return try {
        SmartAccountSharedUtils.amountToStroops(amountStr)
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Builds the [SCValXdr] for a weighted threshold policy.
 *
 * Delegates to [SmartAccountBuilders.createWeightedThresholdParams] for validation
 * (threshold >= 1, all weights > 0, total weight >= threshold), then encodes the
 * result as an SCVal map.
 *
 * On-chain map structure: `{ "signer_weights": Map[Signer => U32], "threshold": U32(threshold) }`
 *
 * The signer weights map is sorted by XDR key order as required by the contract.
 *
 * @param weights Map of each signer to its assigned voting weight.
 * @param threshold Minimum total weight required for authorization.
 * @return Encoded SCVal map for the weighted threshold policy.
 * @throws ValidationException if threshold is less than 1, any weight is less than 1,
 *   or the total weight is less than the threshold.
 */
fun buildWeightedThresholdScVal(
    weights: Map<SmartAccountSigner, UInt>,
    threshold: UInt
): SCValXdr {
    SmartAccountBuilders.createWeightedThresholdParams(
        threshold = threshold.toInt(),
        signerWeights = weights.mapValues { (_, w) -> w.toInt() }
    )

    val weightsMap = linkedMapOf<SCValXdr, SCValXdr>()
    for ((signer, weight) in weights) {
        weightsMap[signer.toScVal()] = Scv.toUint32(weight)
    }
    val sortedWeightsMap = SmartAccountSharedUtils.sortMapByKeyXdr(weightsMap)

    val map = linkedMapOf(
        Scv.toSymbol("signer_weights") to Scv.toMap(sortedWeightsMap),
        Scv.toSymbol("threshold") to Scv.toUint32(threshold)
    )
    return Scv.toMap(map)
}
