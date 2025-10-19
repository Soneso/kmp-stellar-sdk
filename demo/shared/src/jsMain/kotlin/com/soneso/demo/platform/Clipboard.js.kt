package com.soneso.demo.platform

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.HTMLTextAreaElement

/**
 * JavaScript implementation of clipboard using the browser Clipboard API.
 * Requires HTTPS or localhost for security reasons (browser restriction).
 * Falls back to document.execCommand('copy') for older browsers.
 */
private class JSClipboard : Clipboard {
    override suspend fun copyToClipboard(text: String): Boolean {
        return try {
            // Modern Clipboard API (requires HTTPS or localhost)
            val clipboard = window.navigator.asDynamic().clipboard
            if (clipboard != null) {
                // Cast the writeText result to Promise<Unit> before awaiting
                val promise: kotlin.js.Promise<*> = clipboard.writeText(text) as kotlin.js.Promise<*>
                promise.await()
                true
            } else {
                // Fallback for older browsers using execCommand
                copyToClipboardFallback(text)
            }
        } catch (e: Exception) {
            console.error("Clipboard error:", e)
            // Try fallback if modern API fails
            try {
                copyToClipboardFallback(text)
            } catch (e2: Exception) {
                console.error("Fallback clipboard error:", e2)
                false
            }
        }
    }

    private fun copyToClipboardFallback(text: String): Boolean {
        return try {
            // Create a temporary textarea element
            val textarea = document.createElement("textarea") as HTMLTextAreaElement
            textarea.value = text
            textarea.style.position = "fixed"
            textarea.style.left = "-9999px"
            document.body?.appendChild(textarea)

            // Select and copy the text
            textarea.select()
            val success = document.asDynamic().execCommand("copy") as Boolean

            // Clean up
            document.body?.removeChild(textarea)

            success
        } catch (e: Exception) {
            console.error("Fallback clipboard error:", e)
            false
        }
    }
}

actual fun getClipboard(): Clipboard = JSClipboard()
