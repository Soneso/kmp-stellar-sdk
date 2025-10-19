package com.soneso.demo.platform

import platform.UIKit.UIPasteboard

/**
 * iOS implementation of clipboard using UIPasteboard.
 * Uses the general pasteboard for copying text.
 */
private class IOSClipboard : Clipboard {
    override suspend fun copyToClipboard(text: String): Boolean {
        return try {
            UIPasteboard.generalPasteboard.string = text
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

actual fun getClipboard(): Clipboard = IOSClipboard()
