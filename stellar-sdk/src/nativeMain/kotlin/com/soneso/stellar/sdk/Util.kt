package com.soneso.stellar.sdk

import platform.posix.gettimeofday
import platform.posix.timeval
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Native implementation of currentTimeMillis using gettimeofday().
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun currentTimeMillis(): Long = memScoped {
    val tv = alloc<timeval>()
    gettimeofday(tv.ptr, null)
    tv.tv_sec * 1000L + tv.tv_usec / 1000L
}
