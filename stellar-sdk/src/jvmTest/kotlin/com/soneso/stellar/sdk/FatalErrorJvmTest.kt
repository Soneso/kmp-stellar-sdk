package com.soneso.stellar.sdk

import kotlin.test.Test
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
}
