package com.soneso.smartdemo.platform

import platform.AppKit.NSPasteboard
import platform.AppKit.NSPasteboardTypeString

private class MacOSClipboard : Clipboard {
    override suspend fun copyToClipboard(text: String): Boolean {
        return try {
            val pasteboard = NSPasteboard.generalPasteboard
            pasteboard.clearContents()
            pasteboard.setString(text, forType = NSPasteboardTypeString)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

actual fun getClipboard(): Clipboard = MacOSClipboard()
