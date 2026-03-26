package com.soneso.smartdemo.util

import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType

// ============================================================================
// Address and Signer Display Formatting
// ============================================================================

/**
 * Truncates an address for display purposes.
 *
 * @param address Full address string
 * @param chars Number of characters to show on each end (default: 4)
 * @return Truncated address like "GABC...WXYZ"
 */
fun truncateAddress(address: String, chars: Int = 4): String {
    if (address.length <= chars * 2 + 3) {
        return address
    }
    return "${address.take(chars)}...${address.takeLast(chars)}"
}

/**
 * Display info for a signer: type label and formatted identifier.
 */
data class SignerDisplayInfo(
    val type: String,
    val display: String
)

/**
 * Formats a signer for display, returning both the type label and a display identifier.
 */
fun formatSignerForDisplay(signer: SmartAccountSigner): SignerDisplayInfo {
    if (signer is DelegatedSigner) {
        return SignerDisplayInfo(
            type = "G-Address",
            display = truncateAddress(signer.address, 6)
        )
    }

    val external = signer as ExternalSigner
    val credentialId = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
    if (credentialId != null) {
        return SignerDisplayInfo(
            type = "Passkey",
            display = credentialId
        )
    }

    if (external.keyData.size == 32) {
        return SignerDisplayInfo(
            type = "Ed25519",
            display = "key:${external.keyData.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }.take(8)}..."
        )
    }

    return SignerDisplayInfo(
        type = "External",
        display = truncateAddress(external.verifierAddress, 4)
    )
}

/**
 * Formats a context rule type for human-readable display.
 */
fun formatContextType(contextType: ContextRuleType): String {
    return when (contextType) {
        is ContextRuleType.Default -> "Default (Any Operation)"
        is ContextRuleType.CallContract ->
            "Call Contract: ${truncateAddress(contextType.contractAddress)}"
        is ContextRuleType.CreateContract -> {
            val hashHex = contextType.wasmHash.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            "Create Contract: ${hashHex.take(8)}..."
        }
    }
}

// ============================================================================
// Numeric Formatting
// ============================================================================

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
