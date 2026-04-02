package com.soneso.smartdemo.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

private class AndroidClipboard(private val context: Context) : Clipboard {
    override suspend fun copyToClipboard(text: String): Boolean {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboardManager != null) {
                val clip = ClipData.newPlainText("text", text)
                clipboardManager.setPrimaryClip(clip)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

fun initAndroidClipboard(context: Context) {
    AndroidContext.init(context)
}

actual fun getClipboard(): Clipboard {
    return AndroidClipboard(AndroidContext.get())
}
