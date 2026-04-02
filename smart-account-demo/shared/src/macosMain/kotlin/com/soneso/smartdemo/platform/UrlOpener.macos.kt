package com.soneso.smartdemo.platform

import platform.Foundation.NSURL
import platform.AppKit.NSWorkspace

private class MacosUrlOpener : UrlOpener {
    override suspend fun openUrl(url: String): Boolean {
        return try {
            val nsUrl = NSURL.URLWithString(url) ?: return false
            val workspace = NSWorkspace.sharedWorkspace
            workspace.openURL(nsUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

actual fun getUrlOpener(): UrlOpener = MacosUrlOpener()
