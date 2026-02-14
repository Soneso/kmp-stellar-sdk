package com.soneso.smartdemo.platform

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.HTMLTextAreaElement

private class JSClipboard : Clipboard {
    override suspend fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboard = window.navigator.asDynamic().clipboard
            if (clipboard != null) {
                val promise: kotlin.js.Promise<*> = clipboard.writeText(text) as kotlin.js.Promise<*>
                promise.await()
                true
            } else {
                copyToClipboardFallback(text)
            }
        } catch (e: Exception) {
            console.error("Clipboard error:", e)
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
            val textarea = document.createElement("textarea") as HTMLTextAreaElement
            textarea.value = text
            textarea.style.position = "fixed"
            textarea.style.left = "-9999px"
            document.body?.appendChild(textarea)

            textarea.select()
            val success = document.asDynamic().execCommand("copy") as Boolean

            document.body?.removeChild(textarea)
            success
        } catch (e: Exception) {
            console.error("Fallback clipboard error:", e)
            false
        }
    }
}

actual fun getClipboard(): Clipboard = JSClipboard()
