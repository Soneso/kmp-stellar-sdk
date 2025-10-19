package com.soneso.demo.platform

/**
 * Platform-agnostic clipboard interface for copying text to the system clipboard.
 * Implementations use platform-specific clipboard APIs:
 * - Android: ClipboardManager
 * - iOS: UIPasteboard
 * - macOS: NSPasteboard
 * - Desktop/JVM: AWT Toolkit clipboard
 * - Web/JS: Browser Clipboard API
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
