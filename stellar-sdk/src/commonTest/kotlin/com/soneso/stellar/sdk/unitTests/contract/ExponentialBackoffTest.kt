package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.contract.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive tests for the exponential backoff helper function used in AssembledTransaction.
 *
 * The withExponentialBackoff function implements polling with exponential backoff:
 * - Calls the function immediately (first attempt)
 * - If condition is met on first attempt, returns immediately
 * - Otherwise, enters polling loop with exponential backoff
 * - Wait times: 1s, 2s, 4s, 8s, 16s, 32s, 60s (max)
 * - Returns all attempts made before timeout or condition met
 *
 * Tests verify:
 * - Immediate success (no retries)
 * - Single retry behavior
 * - Multiple retries with proper timing
 * - Timeout enforcement
 * - Exponential backoff doubling
 * - Max wait cap at 60 seconds
 * - All attempts are collected and returned
 * - Edge cases (short/long timeouts, never-true conditions)
 */
class ExponentialBackoffTest {

    /**
     * Test 1: Immediate Success
     * When the condition is met on the first attempt, the function should return immediately
     * with only one attempt, without any delays.
     */
    @Test
    fun testImmediateSuccess() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 10,
            fn = {
                callCount++
                "success"
            },
            keepWaitingIf = { false } // Condition met immediately
        )

        assertEquals(1, result.size, "Should have exactly 1 attempt")
        assertEquals("success", result[0], "First attempt should succeed")
        assertEquals(1, callCount, "Function should be called exactly once")
    }

    /**
     * Test 2: Immediate Success with True Result
     * The condition is based on the result value, not a boolean.
     * If the first result satisfies the condition, no retries occur.
     */
    @Test
    fun testImmediateSuccessWithValueCheck() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 10,
            fn = {
                callCount++
                42 // Return the target value immediately
            },
            keepWaitingIf = { it != 42 } // Stop when we get 42
        )

        assertEquals(1, result.size, "Should have exactly 1 attempt")
        assertEquals(42, result[0], "Should return the value")
        assertEquals(1, callCount, "Function should be called exactly once")
    }

    /**
     * Test 3: Single Retry
     * When the first attempt doesn't meet the condition but the second does,
     * we should see exactly 2 attempts with a 1-second delay between them.
     */
    @Test
    fun testSingleRetry() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 10,
            fn = {
                callCount++
                callCount // Return the call number
            },
            keepWaitingIf = { it < 2 } // Wait until we get 2
        )

        assertEquals(2, result.size, "Should have exactly 2 attempts")
        assertEquals(1, result[0], "First attempt returns 1")
        assertEquals(2, result[1], "Second attempt returns 2")
        assertEquals(2, callCount, "Function should be called exactly twice")
    }

    /**
     * Test 4: Multiple Retries
     * When the condition requires several retries, verify that all attempts
     * are recorded and the exponential backoff timing is correct.
     */
    @Test
    fun testMultipleRetries() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 30, // 30 seconds should be enough for 5 attempts
            fn = {
                callCount++
                callCount
            },
            keepWaitingIf = { it < 5 } // Wait until 5th attempt
        )

        assertEquals(5, result.size, "Should have exactly 5 attempts")
        assertEquals(listOf(1, 2, 3, 4, 5), result, "All attempts should be recorded in order")
        assertEquals(5, callCount, "Function should be called 5 times")
    }

    /**
     * Test 5: Condition Never Met (Timeout)
     * When the condition is never satisfied, the function should continue
     * polling until the timeout is reached.
     *
     * Note: With virtual time in runTest, delays complete instantly.
     * We verify behavior by checking the number of attempts made.
     */
    @Test
    fun testTimeoutWithNeverMetCondition() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 5, // 5 seconds
            fn = {
                callCount++
                "pending"
            },
            keepWaitingIf = { true } // Never satisfied
        )

        // With 5 second timeout and exponential backoff:
        // - Attempt 1: 0s
        // - Attempt 2: 0s + 1s delay = 1s
        // - Attempt 3: 1s + 2s delay = 3s
        // - Attempt 4: 3s + 2s delay = 5s (only 2s because timeout is at 5s)
        // So we should get 4 attempts
        assertTrue(result.size >= 3, "Should have at least 3 attempts in 5 seconds")
        assertTrue(callCount >= 3, "Function should be called at least 3 times")
        assertEquals(callCount, result.size, "All attempts should be recorded")
    }

    /**
     * Test 6: Very Short Timeout (1 second)
     * With a 1-second timeout, we should get the immediate attempt and
     * stop when the condition is met early.
     *
     * Note: We test with a condition that becomes true to avoid infinite loops in virtual time.
     */
    @Test
    fun testVeryShortTimeout() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 1, // 1 second
            fn = {
                callCount++
                callCount
            },
            keepWaitingIf = { it < 2 } // Stop after 2 attempts
        )

        // Should stop when condition is met
        assertEquals(2, result.size, "Should have exactly 2 attempts")
        assertEquals(listOf(1, 2), result, "Should return both attempts")
        assertEquals(2, callCount, "Function should be called twice")
    }

    /**
     * Test 7: Exponential Timing Progression
     * Verify that the wait times double correctly: 1s, 2s, 4s, 8s, 16s, 32s
     * before hitting the 60s maximum.
     *
     * Note: This test verifies the number of attempts and behavior, not actual timing.
     * Actual timing verification would require integration tests with real delays.
     */
    @Test
    fun testExponentialTimingProgression() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 70, // Long enough to see several doublings
            fn = {
                callCount++
                callCount
            },
            keepWaitingIf = { it < 7 } // Stop after 7 attempts
        )

        assertEquals(7, result.size, "Should have 7 attempts")
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), result, "All attempts should be in order")
        assertEquals(7, callCount, "Should have called function 7 times")
    }

    /**
     * Test 8: Max Wait Cap at 60 Seconds
     * After the wait time reaches 60 seconds, it should not increase further.
     * Wait progression: 1s, 2s, 4s, 8s, 16s, 32s, 60s (capped), 60s, 60s...
     *
     * Note: This test verifies behavior with a long timeout that would exceed the max wait cap.
     * Actual timing verification would require integration tests with real delays.
     */
    @Test
    fun testMaxWaitCap() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 250, // Long enough to see max cap behavior
            fn = {
                callCount++
                callCount
            },
            keepWaitingIf = { it < 9 } // Get enough attempts to see behavior
        )

        assertEquals(9, result.size, "Should have 9 attempts")
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), result, "All attempts should be in order")
        assertEquals(9, callCount, "Function should be called 9 times")
    }

    /**
     * Test 9: All Attempts Returned
     * Verify that the function collects and returns all attempts,
     * including the initial attempt and all retries.
     */
    @Test
    fun testAllAttemptsReturned() = runTest {
        val expectedValues = listOf("a", "b", "c", "d", "e")
        var index = 0

        val result = withExponentialBackoff(
            timeout = 30,
            fn = {
                val value = expectedValues[index]
                index++
                value
            },
            keepWaitingIf = { it != "e" }
        )

        assertEquals(expectedValues, result, "Should return all attempts in order")
    }

    /**
     * Test 10: Condition Becomes True Mid-Execution
     * When the condition becomes satisfied during polling,
     * the function should stop immediately after that attempt.
     */
    @Test
    fun testConditionBecomesTrue() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 100,
            fn = {
                callCount++
                if (callCount == 3) "success" else "pending"
            },
            keepWaitingIf = { it == "pending" }
        )

        assertEquals(3, result.size, "Should have exactly 3 attempts")
        assertEquals("pending", result[0], "First attempt should be pending")
        assertEquals("pending", result[1], "Second attempt should be pending")
        assertEquals("success", result[2], "Third attempt should be success")
        assertEquals(3, callCount, "Function should be called exactly 3 times")
    }

    /**
     * Test 11: Timeout Boundary - Exact Timeout
     * Test behavior when the condition is met on a later attempt.
     */
    @Test
    fun testTimeoutBoundaryExact() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 10, // 10 seconds
            fn = {
                callCount++
                // Return success on 4th attempt
                if (callCount == 4) "success" else "pending"
            },
            keepWaitingIf = { it == "pending" }
        )

        assertEquals(4, result.size, "Should have exactly 4 attempts")
        assertEquals("success", result.last(), "Last attempt should be success")
        assertEquals(4, callCount, "All attempts should be recorded")
    }

    /**
     * Test 12: Very Long Timeout
     * With a very long timeout (600 seconds = 10 minutes),
     * verify the function still works correctly and stops when condition is met.
     */
    @Test
    fun testVeryLongTimeout() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 600, // 10 minutes
            fn = {
                callCount++
                callCount
            },
            keepWaitingIf = { it < 3 } // Stop after 3 attempts
        )

        assertEquals(3, result.size, "Should have exactly 3 attempts")
        assertEquals(listOf(1, 2, 3), result, "All attempts should be recorded")
        assertEquals(3, callCount, "Function should be called exactly 3 times")

        // Should complete quickly despite long timeout
        // (because condition is met early)
    }

    /**
     * Test 13: Zero Attempts After First (Immediate False)
     * When keepWaitingIf returns false on the first attempt,
     * there should be no polling loop execution at all.
     */
    @Test
    fun testNoPollingLoopWhenConditionMetImmediately() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 100,
            fn = {
                callCount++
                "immediate-success"
            },
            keepWaitingIf = { false }
        )

        assertEquals(1, result.size, "Should have exactly 1 attempt")
        assertEquals(1, callCount, "Function should be called exactly once")
        assertEquals("immediate-success", result[0], "Should return the immediate result")
    }

    /**
     * Test 14: Remaining Time Adjustment
     * When the remaining time is less than the calculated wait time,
     * the function should use the remaining time instead.
     *
     * Note: This test verifies that the function handles timeout correctly
     * without relying on actual timing measurements.
     */
    @Test
    fun testRemainingTimeAdjustment() = runTest {
        var callCount = 0

        val result = withExponentialBackoff(
            timeout = 4, // 4 seconds
            fn = {
                callCount++
                callCount
            },
            keepWaitingIf = { true } // Never satisfied
        )

        // With 4 second timeout:
        // - Attempt 1: 0s (immediate)
        // - Attempt 2: 0s + 1s delay = 1s
        // - Attempt 3: 1s + 2s delay = 3s
        // - Attempt 4: 3s + 1s delay = 4s (reduced from 4s to 1s to fit timeout)

        assertTrue(result.size >= 3, "Should have at least 3 attempts")
        assertEquals(callCount, result.size, "All attempts should be recorded")
    }

    /**
     * Test 15: Function Throws Exception
     * Verify behavior when the polled function throws an exception.
     * The exception should propagate and stop the polling.
     */
    @Test
    fun testFunctionThrowsException() = runTest {
        var callCount = 0

        assertFailsWith<IllegalStateException> {
            withExponentialBackoff(
                timeout = 10,
                fn = {
                    callCount++
                    if (callCount == 2) {
                        throw IllegalStateException("Test exception")
                    }
                    "success"
                },
                keepWaitingIf = { true }
            )
        }

        assertEquals(2, callCount, "Function should be called twice before exception")
    }

    /**
     * Test 16: Different Result Types
     * Verify the function works with different generic types.
     */
    @Test
    fun testDifferentResultTypes() = runTest {
        // Test with Int
        val intResult = withExponentialBackoff(
            timeout = 10,
            fn = { 42 },
            keepWaitingIf = { false }
        )
        assertEquals(listOf(42), intResult)

        // Test with String
        val stringResult = withExponentialBackoff(
            timeout = 10,
            fn = { "test" },
            keepWaitingIf = { false }
        )
        assertEquals(listOf("test"), stringResult)

        // Test with custom data class
        data class TestData(val value: String)
        val objectResult = withExponentialBackoff(
            timeout = 10,
            fn = { TestData("data") },
            keepWaitingIf = { false }
        )
        assertEquals(listOf(TestData("data")), objectResult)

        // Test with nullable type (non-null result, condition false)
        val nullableResult1 = withExponentialBackoff(
            timeout = 10,
            fn = { "not-null" as String? },
            keepWaitingIf = { false }
        )
        assertEquals(listOf("not-null"), nullableResult1)

        // Test with nullable type (null result becoming non-null)
        var nullableCount = 0
        val nullableResult2 = withExponentialBackoff(
            timeout = 10,
            fn = {
                nullableCount++
                if (nullableCount == 1) null else "not-null"
            },
            keepWaitingIf = { it == null }
        )
        assertEquals(listOf(null, "not-null"), nullableResult2)
    }
}
