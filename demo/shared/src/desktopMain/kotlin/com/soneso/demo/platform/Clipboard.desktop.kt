package com.soneso.demo.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Desktop/JVM implementation of clipboard using AWT Toolkit.
 * Uses the system clipboard for copying text.
 */
private class DesktopClipboard : Clipboard {
    override suspend fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(text)
            clipboard.setContents(selection, selection)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

actual fun getClipboard(): Clipboard = DesktopClipboard()
