package com.soneso.stellar.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JVM implementation of currentTimeMillis using System.currentTimeMillis().
 */
internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

/**
 * JVM implementation of platformDelay using Thread.sleep via Dispatchers.IO.
 *
 * Uses a blocking Thread.sleep on the IO dispatcher to guarantee real wall-clock delay,
 * bypassing kotlinx.coroutines.test.runTest's virtual time scheduler.
 */
internal actual suspend fun platformDelay(timeMillis: Long) {
    withContext(Dispatchers.IO) {
        Thread.sleep(timeMillis)
    }
}

/**
 * JVM implementation of platformSynchronized using kotlin.synchronized.
 *
 * Delegates to the JVM's built-in monitor-based synchronization for thread safety.
 */
internal actual fun <T> platformSynchronized(lock: Any, block: () -> T): T {
    return kotlin.synchronized(lock, block)
}
