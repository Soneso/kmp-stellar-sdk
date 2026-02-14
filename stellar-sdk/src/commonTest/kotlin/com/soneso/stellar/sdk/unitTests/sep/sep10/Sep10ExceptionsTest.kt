package com.soneso.stellar.sdk.unitTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.exceptions.*
import kotlin.test.*

/**
 * Unit tests for SEP-10 exception classes.
 *
 * Tests cover all exception types:
 * - WebAuthException (sealed base)
 * - ChallengeRequestException
 * - ChallengeValidationException (sealed base) and all subclasses
 * - TokenSubmissionException
 * - NoMemoForMuxedAccountsException
 */
class Sep10ExceptionsTest {

    // ========== ChallengeRequestException ==========

    @Test
    fun testChallengeRequestException_withStatusCodeAndErrorMessage() {
        val exception = ChallengeRequestException(
            statusCode = 403,
            errorMessage = "Account not allowed"
        )

        assertEquals(403, exception.statusCode)
        assertEquals("Account not allowed", exception.errorMessage)
        assertTrue(exception.message!!.contains("HTTP 403"))
        assertTrue(exception.message!!.contains("Account not allowed"))
    }

    @Test
    fun testChallengeRequestException_withStatusCodeOnly() {
        val exception = ChallengeRequestException(
            statusCode = 500
        )

        assertEquals(500, exception.statusCode)
        assertNull(exception.errorMessage)
        assertTrue(exception.message!!.contains("HTTP 500"))
    }

    @Test
    fun testChallengeRequestException_simpleConstructor() {
        val exception = ChallengeRequestException("Network error")

        assertEquals(0, exception.statusCode)
        assertNull(exception.errorMessage)
        assertEquals("Network error", exception.message)
    }

    @Test
    fun testChallengeRequestException_simpleConstructorWithCause() {
        val cause = RuntimeException("timeout")
        val exception = ChallengeRequestException("Network error", cause)

        assertEquals("Network error", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun testChallengeRequestException_isWebAuthException() {
        val exception = ChallengeRequestException("Test")
        assertTrue(exception is WebAuthException)
        assertTrue(exception is Exception)
    }

    // ========== ChallengeValidationException Subclasses ==========

    @Test
    fun testInvalidSequenceNumberException() {
        val exception = InvalidSequenceNumberException(12345L)
        assertTrue(exception.message!!.contains("0"))
        assertTrue(exception.message!!.contains("12345"))
        assertTrue(exception is ChallengeValidationException)
        assertTrue(exception is WebAuthException)
    }

    @Test
    fun testInvalidTimeBoundsException_notYetValid() {
        val exception = InvalidTimeBoundsException(
            minTime = 2000000000L,
            maxTime = 2000000900L,
            currentTime = 1999999000L,
            gracePeriodSeconds = 300
        )
        assertTrue(exception.message!!.contains("not yet valid"))
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testInvalidTimeBoundsException_expired() {
        val exception = InvalidTimeBoundsException(
            minTime = 1000000000L,
            maxTime = 1000000900L,
            currentTime = 1100000000L,
            gracePeriodSeconds = 300
        )
        assertTrue(exception.message!!.contains("expired"))
    }

    @Test
    fun testInvalidTimeBoundsException_nullTimes() {
        val exception = InvalidTimeBoundsException(
            minTime = null,
            maxTime = null,
            currentTime = 1700000000L,
            gracePeriodSeconds = 300
        )
        assertTrue(exception.message!!.contains("must have time bounds set"))
    }

    @Test
    fun testInvalidOperationTypeException() {
        val exception = InvalidOperationTypeException("Payment", 2)
        assertTrue(exception.message!!.contains("Payment"))
        assertTrue(exception.message!!.contains("index 2"))
        assertTrue(exception.message!!.contains("ManageData"))
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testInvalidSourceAccountException() {
        val exception = InvalidSourceAccountException("GEXPECTED", "GACTUAL")
        assertTrue(exception.message!!.contains("GEXPECTED"))
        assertTrue(exception.message!!.contains("GACTUAL"))
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testInvalidHomeDomainException() {
        val exception = InvalidHomeDomainException("example.com", "evil.com auth")
        assertTrue(exception.message!!.contains("example.com auth"))
        assertTrue(exception.message!!.contains("evil.com auth"))
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testInvalidSignatureCountException() {
        val exception = InvalidSignatureCountException(0)
        assertTrue(exception.message!!.contains("exactly 1"))
        assertTrue(exception.message!!.contains("0"))
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testInvalidSignatureException() {
        val exception = InvalidSignatureException("GSERVERPUBKEY123")
        assertTrue(exception.message!!.contains("GSERVERPUBKEY123"))
        assertTrue(exception.message!!.contains("invalid"))
        assertTrue(exception.message!!.contains("DO NOT sign"))
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testInvalidMemoTypeException() {
        val exception = InvalidMemoTypeException("MEMO_TEXT")
        assertTrue(exception.message!!.contains("MEMO_TEXT"))
        assertTrue(exception.message!!.contains("MEMO_ID"))
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testInvalidMemoValueException() {
        val exception = InvalidMemoValueException(12345L, 99999L)
        assertTrue(exception.message!!.contains("12345"))
        assertTrue(exception.message!!.contains("99999"))
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testInvalidMemoValueException_nullActual() {
        val exception = InvalidMemoValueException(12345L, null)
        assertTrue(exception.message!!.contains("12345"))
        assertTrue(exception.message!!.contains("null"))
    }

    @Test
    fun testInvalidWebAuthDomainException() {
        val exception = InvalidWebAuthDomainException("api.example.com", "malicious.com")
        assertTrue(exception.message!!.contains("api.example.com"))
        assertTrue(exception.message!!.contains("malicious.com"))
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testInvalidClientDomainSourceException() {
        val exception = InvalidClientDomainSourceException("GEXPECTED", "GACTUAL")
        assertTrue(exception.message!!.contains("GEXPECTED"))
        assertTrue(exception.message!!.contains("GACTUAL"))
        assertTrue(exception.message!!.contains("client_domain"))
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testGenericChallengeValidationException() {
        val exception = GenericChallengeValidationException("Invalid XDR format")
        assertEquals("Invalid XDR format", exception.message)
        assertTrue(exception is ChallengeValidationException)
    }

    @Test
    fun testMemoWithMuxedAccountException() {
        val exception = MemoWithMuxedAccountException("MABC...XYZ", 12345L)
        assertTrue(exception.message!!.contains("MABC...XYZ"))
        assertTrue(exception.message!!.contains("12345"))
        assertTrue(exception.message!!.contains("cannot have both"))
        assertTrue(exception is ChallengeValidationException)
    }

    // ========== TokenSubmissionException ==========

    @Test
    fun testTokenSubmissionException_message() {
        val exception = TokenSubmissionException("Submission failed")
        assertEquals("Submission failed", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun testTokenSubmissionException_withCause() {
        val cause = RuntimeException("Network error")
        val exception = TokenSubmissionException("Submission failed", cause)
        assertSame(cause, exception.cause)
    }

    @Test
    fun testTokenSubmissionException_isWebAuthException() {
        val exception = TokenSubmissionException("Test")
        assertTrue(exception is WebAuthException)
    }

    // ========== NoMemoForMuxedAccountsException ==========

    @Test
    fun testNoMemoForMuxedAccountsException_defaultMessage() {
        val exception = NoMemoForMuxedAccountsException()
        assertTrue(exception.message!!.contains("Muxed accounts"))
        assertTrue(exception.message!!.contains("memo"))
    }

    @Test
    fun testNoMemoForMuxedAccountsException_customMessage() {
        val exception = NoMemoForMuxedAccountsException("Custom message")
        assertEquals("Custom message", exception.message)
    }

    @Test
    fun testNoMemoForMuxedAccountsException_isWebAuthException() {
        val exception = NoMemoForMuxedAccountsException()
        assertTrue(exception is WebAuthException)
    }

    // ========== Sealed Class Tests ==========

    @Test
    fun testAllValidationExceptionsAreChallengeValidationException() {
        val exceptions: List<ChallengeValidationException> = listOf(
            InvalidSequenceNumberException(0L),
            InvalidTimeBoundsException(1L, 2L, 3L, 300),
            InvalidOperationTypeException("Payment", 0),
            InvalidSourceAccountException("G1", "G2"),
            InvalidHomeDomainException("domain", "key"),
            InvalidSignatureCountException(2),
            InvalidSignatureException("GKEY"),
            InvalidMemoTypeException("MEMO_TEXT"),
            InvalidMemoValueException(1L, 2L),
            InvalidWebAuthDomainException("d1", "d2"),
            InvalidClientDomainSourceException("G1", "G2"),
            GenericChallengeValidationException("generic"),
            MemoWithMuxedAccountException("M123", 456L)
        )

        exceptions.forEach { exception ->
            assertTrue(exception is ChallengeValidationException,
                "${exception::class.simpleName} should be ChallengeValidationException")
            assertTrue(exception is WebAuthException,
                "${exception::class.simpleName} should be WebAuthException")
            assertTrue(exception is Exception,
                "${exception::class.simpleName} should be Exception")
            assertNotNull(exception.message)
        }
    }

    @Test
    fun testAllWebAuthExceptions() {
        val exceptions: List<WebAuthException> = listOf(
            ChallengeRequestException("req"),
            TokenSubmissionException("submit"),
            NoMemoForMuxedAccountsException(),
            InvalidSequenceNumberException(0L),
            GenericChallengeValidationException("generic")
        )

        exceptions.forEach { exception ->
            assertTrue(exception is WebAuthException)
            assertTrue(exception is Exception)
        }
    }

    @Test
    fun testCatchingAsChallengeValidationException() {
        var caught = false
        try {
            throw InvalidSignatureException("GKEY")
        } catch (e: ChallengeValidationException) {
            caught = true
            assertTrue(e is InvalidSignatureException)
        }
        assertTrue(caught)
    }

    @Test
    fun testCatchingAsWebAuthException() {
        var caught = false
        try {
            throw ChallengeRequestException("error")
        } catch (e: WebAuthException) {
            caught = true
            assertTrue(e is ChallengeRequestException)
        }
        assertTrue(caught)
    }
}
