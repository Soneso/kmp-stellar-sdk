package com.soneso.smartdemo.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

private class IosUrlOpener : UrlOpener {
    override suspend fun openUrl(url: String): Boolean {
        return try {
            val nsUrl = NSURL.URLWithString(url) ?: return false
            val application = UIApplication.sharedApplication

            if (!application.canOpenURL(nsUrl)) {
                return false
            }

            suspendCancellableCoroutine { continuation ->
                application.openURL(
                    nsUrl,
                    options = emptyMap<Any?, Any>(),
                    completionHandler = { success ->
                        continuation.resume(success)
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

actual fun getUrlOpener(): UrlOpener = IosUrlOpener()
