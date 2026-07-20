package com.soneso.stellar.sdk

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM-specific tests for [isFatal] / [isFatalPlatformError].
 *
 * VirtualMachineError subtypes exist only on the JVM, so this classification
 * cannot be asserted from common tests.
 */
class FatalErrorJvmTest {

    @Test
    fun testIsFatal_outOfMemoryError_isFatal() {
        assertTrue(isFatal(OutOfMemoryError("boom")))
    }

    @Test
    fun testIsFatal_stackOverflowError_isFatal() {
        assertTrue(isFatal(StackOverflowError()))
    }

    @Test
    fun testIsFatal_plainError_isNotFatal() {
        assertFalse(isFatal(Error("boom")))
    }

    @Test
    fun testReadErrorBodyOrFallback_readThrowsVirtualMachineError_propagates() = runTest {
        // A platform-fatal error (VirtualMachineError) is rethrown rather than masked by
        // the fallback; this branch exists only on the JVM.
        assertFailsWith<OutOfMemoryError> {
            readErrorBodyOrFallback("fallback") { throw OutOfMemoryError("boom") }
        }
    }

    @Test
    fun testReadErrorBodyOrFallback_readThrowsPlainError_returnsFallback() = runTest {
        assertEquals("fallback", readErrorBodyOrFallback("fallback") { throw Error("boom") })
    }
}
