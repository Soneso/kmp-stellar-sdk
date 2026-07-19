//
//  FatalError.kt
//  Stellar SDK Kotlin Multiplatform
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.stellar.sdk

import kotlin.coroutines.cancellation.CancellationException

/**
 * Returns true for failures that SDK error handling must never swallow or wrap:
 * coroutine cancellation and platform-fatal errors (see [isFatalPlatformError]).
 *
 * Network-boundary catch blocks catch [Throwable] rather than [Exception] because
 * on Kotlin/JS the HTTP engine reports connectivity failures as [kotlin.Error]
 * ("Fail to fetch"), which is a [Throwable] but not an [Exception]. Each such
 * block rethrows failures for which this function returns true and handles
 * everything else.
 *
 * The annotation exposes this helper to public and protected inline functions in
 * the module (for example the Horizon request builders) while keeping it out of
 * the public API surface.
 *
 * @param throwable The caught failure to classify
 * @return true if the failure must be rethrown instead of handled
 */
@PublishedApi
internal fun isFatal(throwable: Throwable): Boolean =
    throwable is CancellationException || isFatalPlatformError(throwable)

/**
 * Returns true for platform-specific errors that indicate an unrecoverable
 * runtime condition rather than a failed operation.
 *
 * On JVM this matches [VirtualMachineError] (e.g. OutOfMemoryError,
 * StackOverflowError). On JS and Native there is no equivalent error category,
 * so the implementations return false.
 *
 * @param throwable The caught failure to classify
 * @return true if the failure is a platform-fatal error
 */
internal expect fun isFatalPlatformError(throwable: Throwable): Boolean
