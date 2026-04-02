package com.soneso.smartdemo.platform

import android.content.Intent
import android.net.Uri

private class AndroidUrlOpener(private val context: android.content.Context) : UrlOpener {
    override suspend fun openUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

actual fun getUrlOpener(): UrlOpener {
    return AndroidUrlOpener(AndroidContext.get())
}
