package com.soneso.smartdemo.platform

import android.content.Context

/**
 * Shared Android context holder for platform-specific features.
 */
internal object AndroidContext {
    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
    }

    fun get(): Context {
        return context ?: throw IllegalStateException(
            "Android context not initialized. Call AndroidContext.init(context) first."
        )
    }
}
