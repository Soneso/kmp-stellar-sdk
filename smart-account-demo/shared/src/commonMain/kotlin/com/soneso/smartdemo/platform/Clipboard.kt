package com.soneso.smartdemo.platform

/**
 * Platform-agnostic clipboard interface for copying text to the system clipboard.
 */
interface Clipboard {
    /**
     * Copy text to the system clipboard.
     *
     * @param text The text to copy
     * @return true if the operation succeeded, false otherwise
     */
    suspend fun copyToClipboard(text: String): Boolean
}

/**
 * Get the platform-specific clipboard implementation.
 */
expect fun getClipboard(): Clipboard
