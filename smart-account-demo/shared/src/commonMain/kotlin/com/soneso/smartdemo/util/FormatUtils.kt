package com.soneso.smartdemo.util

/**
 * Formats a stroops amount (Long or String) as an XLM display string.
 * Uses integer arithmetic to avoid floating-point formatting issues.
 *
 * @param stroops The amount in stroops (1 XLM = 10,000,000 stroops)
 * @return Formatted string like "100.0", "0.5", "10.1234567"
 */
fun formatStroopsAsXlm(stroops: Long): String {
    val negative = stroops < 0
    val absStroops = if (negative) -stroops else stroops
    val wholePart = absStroops / 10_000_000L
    val fractionalPart = absStroops % 10_000_000L
    val fractionalStr = fractionalPart.toString().padStart(7, '0').trimEnd('0').ifEmpty { "0" }
    val prefix = if (negative) "-" else ""
    return "$prefix$wholePart.$fractionalStr"
}

/**
 * Formats a stroops amount from a String (as returned by Soroban RPC) as XLM.
 *
 * @param stroopsStr The amount in stroops as a string
 * @return Formatted XLM string, or "0.0" if parsing fails
 */
fun formatStroopsAsXlm(stroopsStr: String): String {
    val stroops = stroopsStr.toLongOrNull() ?: return "0.0"
    return formatStroopsAsXlm(stroops)
}
