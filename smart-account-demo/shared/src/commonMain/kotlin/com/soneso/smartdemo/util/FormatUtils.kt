package com.soneso.smartdemo.util

import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.smartaccount.core.DelegatedSigner
import com.soneso.stellar.sdk.smartaccount.core.ExternalSigner
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountBuilders
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner
import com.soneso.stellar.sdk.smartaccount.oz.ContextRuleType

// ============================================================================
// Input Validation
// ============================================================================

/**
 * Returns true if [input] is a non-blank, valid Stellar contract address (C...).
 */
fun isValidContractAddress(input: String): Boolean =
    input.isNotBlank() && StrKey.isValidContract(input)

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
    return when (signer) {
        is DelegatedSigner -> SignerDisplayInfo(
            type = "G-Address",
            display = truncateAddress(signer.address, 6)
        )
        is ExternalSigner -> {
            val credentialId = SmartAccountBuilders.getCredentialIdStringFromSigner(signer)
            when {
                credentialId != null -> SignerDisplayInfo(
                    type = "Passkey",
                    display = credentialId
                )
                signer.keyData.size == 32 -> SignerDisplayInfo(
                    type = "Ed25519",
                    display = "${signer.keyData.toHexString().take(8)}..."
                )
                else -> SignerDisplayInfo(
                    type = "External",
                    display = truncateAddress(signer.verifierAddress, 4)
                )
            }
        }
    }
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
            val hashHex = contextType.wasmHash.toHexString()
            "Create Contract: ${hashHex.take(8)}..."
        }
    }
}

/**
 * Returns the demo's display label for a signer type.
 *
 * Label mapping is app-owned (the SDK provides type checks and key-data accessors,
 * not UI strings). Possible values: "Stellar Account", "Passkey (WebAuthn)",
 * "Ed25519", "External Verifier".
 */
private const val ED25519_KEY_SIZE = 32

fun describeSignerType(signer: SmartAccountSigner): String {
    if (signer is DelegatedSigner) return "Stellar Account"
    val external = signer as ExternalSigner
    return when {
        // WebAuthn signers carry a 65-byte public key followed by the credential ID
        SmartAccountBuilders.getPublicKeyFromSigner(external) != null -> "Passkey (WebAuthn)"
        // Ed25519 signers carry a 32-byte public key
        external.keyData.size == ED25519_KEY_SIZE -> "Ed25519"
        else -> "External Verifier"
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
    if (stroops == Long.MIN_VALUE) return "-922337203685.4775808"
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

// ============================================================================
// Hex Encoding
// ============================================================================

/**
 * Converts a hex string to a [ByteArray].
 *
 * @param hex Even-length hex string (e.g., "deadbeef" or "DEADBEEF").
 * @return Decoded byte array.
 * @throws IllegalArgumentException if [hex] has odd length or invalid characters.
 */
fun hexToByteArray(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(hex.length / 2) { i ->
        val index = i * 2
        hex.substring(index, index + 2).toInt(16).toByte()
    }
}

/**
 * Converts a [ByteArray] to a lowercase hex string.
 *
 * @return Lowercase hex representation of each byte.
 */
fun ByteArray.toHexString(): String {
    return joinToString("") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
}
