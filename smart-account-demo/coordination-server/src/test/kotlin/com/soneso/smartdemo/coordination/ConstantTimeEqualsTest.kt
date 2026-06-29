package com.soneso.smartdemo.coordination

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Direct unit tests for [constantTimeEquals], the length-aware constant-time byte comparison
 * that backs bearer-token verification. The HTTP layer exercises it end-to-end; these assert
 * the comparison's correctness in isolation, including the equal-length path that a mismatched
 * length would otherwise short-circuit.
 */
class ConstantTimeEqualsTest {

    private fun bytes(value: String): ByteArray = value.encodeToByteArray()

    @Test
    fun equalContentsOfEqualLengthMatch() {
        assertTrue(constantTimeEquals(bytes("dev-token-change-me"), bytes("dev-token-change-me")))
    }

    @Test
    fun sameLengthDifferingContentDoesNotMatch() {
        // Same length forces the full xor loop to run rather than the length short-circuit.
        assertFalse(constantTimeEquals(bytes("dev-token-change-me"), bytes("dev-token-change-mE")))
    }

    @Test
    fun aSingleByteDifferenceAtTheFirstPositionIsDetected() {
        assertFalse(constantTimeEquals(bytes("Xev-token"), bytes("dev-token")))
    }

    @Test
    fun aSingleByteDifferenceAtTheLastPositionIsDetected() {
        assertFalse(constantTimeEquals(bytes("dev-tokeX"), bytes("dev-token")))
    }

    @Test
    fun differingLengthsDoNotMatch() {
        assertFalse(constantTimeEquals(bytes("short"), bytes("a-longer-token")))
        assertFalse(constantTimeEquals(bytes("a-longer-token"), bytes("short")))
    }

    @Test
    fun aPrefixDoesNotMatchTheLongerWhole() {
        assertFalse(constantTimeEquals(bytes("dev-token"), bytes("dev-token-change-me")))
    }

    @Test
    fun twoEmptyArraysMatch() {
        assertTrue(constantTimeEquals(ByteArray(0), ByteArray(0)))
    }

    @Test
    fun emptyDoesNotMatchNonEmpty() {
        assertFalse(constantTimeEquals(ByteArray(0), bytes("x")))
        assertFalse(constantTimeEquals(bytes("x"), ByteArray(0)))
    }
}
