package com.soneso.smartdemo.platform

import platform.UIKit.UIPasteboard

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
