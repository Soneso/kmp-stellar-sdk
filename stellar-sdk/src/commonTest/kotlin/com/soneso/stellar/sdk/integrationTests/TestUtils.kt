package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.platformDelay

/**
 * Delays for the specified time using real wall-clock time.
 *
 * Delegates to [platformDelay] which uses platform-native timing mechanisms
 * (Thread.sleep on JVM, setTimeout on JS, delay on Native) to bypass
 * runTest's virtual time scheduler.
 */
suspend fun realDelay(timeMillis: Long) {
    platformDelay(timeMillis)
}
