package com.soneso.smartdemo.util

import androidx.compose.ui.graphics.Color

/**
 * Returns a display color for a signer based on its type description string.
 *
 * The [signerType] is the value returned by the demo's [describeSignerType] helper:
 * - "Passkey (WebAuthn)" -> purple
 * - "Stellar Account"    -> blue
 * - "Ed25519"            -> teal
 * - anything else        -> grey
 */
fun signerTypeColor(signerType: String): Color {
    return when (signerType) {
        "Passkey (WebAuthn)" -> Color(0xFF9C27B0)
        "Stellar Account" -> Color(0xFF2196F3)
        "Ed25519" -> Color(0xFF009688)
        else -> Color(0xFF607D8B)
    }
}
