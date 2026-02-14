package com.soneso.stellar.sdk

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.js.Date

// Node.js and browser global setTimeout/clearTimeout
private external fun setTimeout(callback: () -> Unit, ms: Int): Int
private external fun clearTimeout(id: Int)

/**
 * JavaScript implementation of currentTimeMillis using Date.now().
 */
internal actual fun currentTimeMillis(): Long = Date.now().toLong()

/**
 * JavaScript implementation of platformDelay using setTimeout.
 *
 * Uses a raw JavaScript setTimeout with suspendCancellableCoroutine to guarantee
 * real wall-clock delay, bypassing kotlinx.coroutines.test.runTest's virtual time
 * scheduler which intercepts all coroutine dispatchers on JS (including Dispatchers.Default).
 */
internal actual suspend fun platformDelay(timeMillis: Long) {
    suspendCancellableCoroutine { cont ->
        val id = setTimeout({ cont.resume(Unit) }, timeMillis.toInt())
        cont.invokeOnCancellation { clearTimeout(id) }
    }
}
