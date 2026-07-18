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
import kotlin.concurrent.AtomicReference

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

/**
 * Native implementation of platformSynchronized using a spinlock based on AtomicReference.
 *
 * Under the new Kotlin/Native memory model, objects can be shared across threads.
 * This implementation uses a simple spinlock via AtomicReference<Boolean> to provide
 * mutual exclusion for short critical sections (listener list management).
 *
 * The lock is process-global (the `lock` argument is ignored) and NON-REENTRANT: a
 * nested platformSynchronized call on the same thread spins forever. The contract on
 * the expect declaration forbids nesting; keep it that way.
 */
private val nativeLock = AtomicReference(false)

internal actual fun <T> platformSynchronized(lock: Any, block: () -> T): T {
    // Spin until we acquire the lock
    while (!nativeLock.compareAndSet(false, true)) {
        // Busy-wait (acceptable for very short critical sections like list snapshots)
    }
    try {
        return block()
    } finally {
        nativeLock.compareAndSet(true, false)
    }
}

/**
 * Native implementation of isFatalPlatformError.
 *
 * Kotlin/Native has no error category that reliably indicates an unrecoverable
 * runtime condition, so no throwable is classified as fatal here.
 */
internal actual fun isFatalPlatformError(throwable: Throwable): Boolean = false
