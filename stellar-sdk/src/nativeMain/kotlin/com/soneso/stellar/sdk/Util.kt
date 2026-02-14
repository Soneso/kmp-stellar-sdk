package com.soneso.stellar.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

/**
 * Native implementation of platformDelay using Dispatchers.Default.
 *
 * On Native targets, Dispatchers.Default runs on a separate worker thread pool,
 * so withContext(Dispatchers.Default) properly escapes runTest's virtual time.
 */
internal actual suspend fun platformDelay(timeMillis: Long) {
    withContext(Dispatchers.Default) {
        delay(timeMillis)
    }
}
