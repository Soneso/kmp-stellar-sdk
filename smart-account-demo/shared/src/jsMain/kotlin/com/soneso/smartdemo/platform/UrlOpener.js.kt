package com.soneso.smartdemo.platform

import kotlinx.browser.window

private class JSUrlOpener : UrlOpener {
    override suspend fun openUrl(url: String): Boolean {
        return try {
            val opened = window.open(url, "_blank", "noreferrer,noopener")
            opened != null
        } catch (e: Exception) {
            console.error("URL opening error:", e)
            false
        }
    }
}

actual fun getUrlOpener(): UrlOpener = JSUrlOpener()
