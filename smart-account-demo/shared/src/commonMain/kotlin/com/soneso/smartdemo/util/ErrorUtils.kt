package com.soneso.smartdemo.util

/**
 * Returns true if [message] indicates a user-initiated cancellation of a passkey operation.
 *
 * Checks for common patterns emitted by WebAuthn implementations across platforms:
 * - The word "cancelled" alone (covers iOS/macOS "The operation was cancelled")
 * - "user" combined with "cancel" or "abort" (covers Android and browser error messages)
 */
fun isUserCancellation(message: String): Boolean {
    return message.contains("cancelled", ignoreCase = true) ||
        (message.contains("user", ignoreCase = true) &&
            (message.contains("cancel", ignoreCase = true) ||
                message.contains("abort", ignoreCase = true)))
}
