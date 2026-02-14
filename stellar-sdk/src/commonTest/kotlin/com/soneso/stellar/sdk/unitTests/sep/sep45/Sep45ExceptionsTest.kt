// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep45

import com.soneso.stellar.sdk.sep.sep45.*
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45ChallengeRequestException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45ChallengeValidationException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45Exception
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45InvalidAccountException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45InvalidArgsException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45InvalidContractAddressException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45InvalidFunctionNameException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45InvalidHomeDomainException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45InvalidNetworkPassphraseException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45InvalidNonceException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45InvalidServerSignatureException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45InvalidWebAuthDomainException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45MissingClientDomainException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45MissingClientEntryException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45MissingServerEntryException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45NoContractIdException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45NoEndpointException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45NoSigningKeyException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45SubInvocationsFoundException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45TimeoutException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45TokenSubmissionException
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45UnknownResponseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Sep45ExceptionsTest {

    // ========== Base Exception Tests ==========

    @Test
    fun testSep45ExceptionMessage() {
        val exception = Sep45Exception("Test error message")

        assertEquals("Test error message", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun testSep45ExceptionWithCause() {
        val cause = RuntimeException("Underlying cause")
        val exception = Sep45Exception("Wrapper message", cause)

        assertEquals("Wrapper message", exception.message)
        assertNotNull(exception.cause)
        assertEquals("Underlying cause", exception.cause?.message)
        assertIs<RuntimeException>(exception.cause)
    }

    @Test
    fun testSep45ExceptionInheritance() {
        val exception = Sep45Exception("Test")

        assertIs<Exception>(exception)
    }

    // ========== Validation Exception Tests ==========

    @Test
    fun testInvalidContractAddressExceptionMessage() {
        val expected = "CDYF2TQHBFNWLNTQXUABKHWBJ3MSXCQOFVX6XCXSCUBH3XHZLXSCQAB"
        val actual = "CBWRONGCONTRACTADDRESSXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
        val exception = Sep45InvalidContractAddressException(expected, actual)

        assertEquals(expected, exception.expected)
        assertEquals(actual, exception.actual)
        assertTrue(exception.message!!.contains("Expected: $expected"))
        assertTrue(exception.message!!.contains("but found: $actual"))
        assertIs<Sep45ChallengeValidationException>(exception)
        assertIs<Sep45Exception>(exception)
    }

    @Test
    fun testInvalidFunctionNameExceptionMessage() {
        val expected = "web_auth_verify"
        val actual = "transfer"
        val exception = Sep45InvalidFunctionNameException(expected, actual)

        assertEquals(expected, exception.expected)
        assertEquals(actual, exception.actual)
        assertTrue(exception.message!!.contains("Expected: $expected"))
        assertTrue(exception.message!!.contains("but found: $actual"))
        assertTrue(exception.message!!.contains("wrong function"))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    @Test
    fun testInvalidHomeDomainExceptionMessage() {
        val expected = "example.com"
        val actual = "malicious.com"
        val exception = Sep45InvalidHomeDomainException(expected, actual)

        assertEquals(expected, exception.expected)
        assertEquals(actual, exception.actual)
        assertTrue(exception.message!!.contains("Expected: $expected"))
        assertTrue(exception.message!!.contains("but found: $actual"))
        assertTrue(exception.message!!.contains("home_domain"))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    @Test
    fun testInvalidNetworkPassphraseExceptionMessage() {
        val expected = "Public Global Stellar Network ; September 2015"
        val actual = "Test SDF Network ; September 2015"
        val exception = Sep45InvalidNetworkPassphraseException(expected, actual)

        assertEquals(expected, exception.expected)
        assertEquals(actual, exception.actual)
        assertTrue(exception.message!!.contains("Expected: '$expected'"))
        assertTrue(exception.message!!.contains("server returned: '$actual'"))
        assertTrue(exception.message!!.contains("passphrase"))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    @Test
    fun testInvalidAccountExceptionMessage() {
        val expected = "CDYF2TQHBFNWLNTQXUABKHWBJ3MSXCQOFVX6XCXSCUBH3XHZLXSCQAB"
        val actual = "CBWRONGACCOUNTADDRESSXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
        val exception = Sep45InvalidAccountException(expected, actual)

        assertEquals(expected, exception.expected)
        assertEquals(actual, exception.actual)
        assertTrue(exception.message!!.contains("Expected: $expected"))
        assertTrue(exception.message!!.contains("but found: $actual"))
        assertTrue(exception.message!!.contains("account argument"))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    @Test
    fun testInvalidWebAuthDomainExceptionMessage() {
        val expected = "api.example.com"
        val actual = "malicious.com:8080"
        val exception = Sep45InvalidWebAuthDomainException(expected, actual)

        assertEquals(expected, exception.expected)
        assertEquals(actual, exception.actual)
        assertTrue(exception.message!!.contains("Expected: $expected"))
        assertTrue(exception.message!!.contains("but found: $actual"))
        assertTrue(exception.message!!.contains("web_auth_domain"))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    @Test
    fun testInvalidNonceExceptionMessage() {
        val exception = Sep45InvalidNonceException("Nonce is missing from entry")

        assertTrue(exception.message!!.contains("nonce validation failed"))
        assertTrue(exception.message!!.contains("Nonce is missing from entry"))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    @Test
    fun testInvalidServerSignatureExceptionMessage() {
        val exception = Sep45InvalidServerSignatureException("Signature does not match expected signer")

        assertTrue(exception.message!!.contains("Server signature verification failed"))
        assertTrue(exception.message!!.contains("Signature does not match expected signer"))
        assertTrue(exception.message!!.contains("DO NOT sign this challenge"))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    @Test
    fun testInvalidArgsExceptionMessage() {
        val exception = Sep45InvalidArgsException("Arguments not in expected Map format")

        assertTrue(exception.message!!.contains("arguments validation failed"))
        assertTrue(exception.message!!.contains("Arguments not in expected Map format"))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    @Test
    fun testSubInvocationsFoundExceptionMessage() {
        val exception = Sep45SubInvocationsFoundException("Found 2 sub-invocations")

        assertTrue(exception.message!!.contains("sub-invocations"))
        assertTrue(exception.message!!.contains("not allowed"))
        assertTrue(exception.message!!.contains("Found 2 sub-invocations"))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    @Test
    fun testMissingClientEntryExceptionMessage() {
        val exception = Sep45MissingClientEntryException("Expected entry for contract CDYF...")

        assertTrue(exception.message!!.contains("Client authorization entry missing"))
        assertTrue(exception.message!!.contains("Expected entry for contract CDYF..."))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    @Test
    fun testMissingServerEntryExceptionMessage() {
        val exception = Sep45MissingServerEntryException("Expected entry for server GDXE...")

        assertTrue(exception.message!!.contains("Server authorization entry missing"))
        assertTrue(exception.message!!.contains("Expected entry for server GDXE..."))
        assertIs<Sep45ChallengeValidationException>(exception)
    }

    // ========== Request/Submission Exception Tests ==========

    @Test
    fun testChallengeRequestExceptionWithStatusCode() {
        val exception = Sep45ChallengeRequestException(
            message = "Challenge request failed",
            statusCode = 403
        )

        assertEquals(403, exception.statusCode)
        assertNull(exception.errorMessage)
        assertTrue(exception.message!!.contains("Challenge request failed"))
        assertTrue(exception.message!!.contains("HTTP 403"))
        assertIs<Sep45Exception>(exception)
    }

    @Test
    fun testChallengeRequestExceptionWithErrorMessage() {
        val exception = Sep45ChallengeRequestException(
            message = "Challenge request failed",
            errorMessage = "Contract not allowed"
        )

        assertNull(exception.statusCode)
        assertEquals("Contract not allowed", exception.errorMessage)
        assertTrue(exception.message!!.contains("Challenge request failed"))
        assertTrue(exception.message!!.contains(": Contract not allowed"))
    }

    @Test
    fun testChallengeRequestExceptionWithBoth() {
        val exception = Sep45ChallengeRequestException(
            message = "Challenge request failed",
            statusCode = 400,
            errorMessage = "Invalid contract ID format"
        )

        assertEquals(400, exception.statusCode)
        assertEquals("Invalid contract ID format", exception.errorMessage)
        assertTrue(exception.message!!.contains("Challenge request failed"))
        assertTrue(exception.message!!.contains("(HTTP 400)"))
        assertTrue(exception.message!!.contains(": Invalid contract ID format"))
    }

    @Test
    fun testChallengeRequestExceptionWithNeither() {
        val exception = Sep45ChallengeRequestException(
            message = "Network error"
        )

        assertNull(exception.statusCode)
        assertNull(exception.errorMessage)
        assertEquals("Network error", exception.message)
    }

    @Test
    fun testTokenSubmissionExceptionWithStatusCode() {
        val exception = Sep45TokenSubmissionException(
            message = "Token submission failed",
            statusCode = 401
        )

        assertEquals(401, exception.statusCode)
        assertNull(exception.errorMessage)
        assertTrue(exception.message!!.contains("Token submission failed"))
        assertTrue(exception.message!!.contains("HTTP 401"))
        assertIs<Sep45Exception>(exception)
    }

    @Test
    fun testTokenSubmissionExceptionWithErrorMessage() {
        val exception = Sep45TokenSubmissionException(
            message = "Token submission failed",
            errorMessage = "Signature verification failed"
        )

        assertNull(exception.statusCode)
        assertEquals("Signature verification failed", exception.errorMessage)
        assertTrue(exception.message!!.contains("Token submission failed"))
        assertTrue(exception.message!!.contains(": Signature verification failed"))
    }

    @Test
    fun testTokenSubmissionExceptionWithBoth() {
        val exception = Sep45TokenSubmissionException(
            message = "Token submission failed",
            statusCode = 401,
            errorMessage = "Contract __check_auth rejected authentication"
        )

        assertEquals(401, exception.statusCode)
        assertEquals("Contract __check_auth rejected authentication", exception.errorMessage)
        assertTrue(exception.message!!.contains("Token submission failed"))
        assertTrue(exception.message!!.contains("(HTTP 401)"))
        assertTrue(exception.message!!.contains(": Contract __check_auth rejected authentication"))
    }

    // ========== Configuration Exception Tests ==========

    @Test
    fun testNoEndpointExceptionMessage() {
        val exception = Sep45NoEndpointException("example.com")

        assertEquals("example.com", exception.domain)
        assertTrue(exception.message!!.contains("stellar.toml"))
        assertTrue(exception.message!!.contains("example.com"))
        assertTrue(exception.message!!.contains("WEB_AUTH_FOR_CONTRACTS_ENDPOINT"))
        assertIs<Sep45Exception>(exception)
    }

    @Test
    fun testNoContractIdExceptionMessage() {
        val exception = Sep45NoContractIdException("example.com")

        assertEquals("example.com", exception.domain)
        assertTrue(exception.message!!.contains("stellar.toml"))
        assertTrue(exception.message!!.contains("example.com"))
        assertTrue(exception.message!!.contains("WEB_AUTH_CONTRACT_ID"))
        assertIs<Sep45Exception>(exception)
    }

    @Test
    fun testNoSigningKeyExceptionMessage() {
        val exception = Sep45NoSigningKeyException("example.com")

        assertEquals("example.com", exception.domain)
        assertTrue(exception.message!!.contains("stellar.toml"))
        assertTrue(exception.message!!.contains("example.com"))
        assertTrue(exception.message!!.contains("SIGNING_KEY"))
        assertIs<Sep45Exception>(exception)
    }

    // ========== Other Exception Tests ==========

    @Test
    fun testTimeoutExceptionMessage() {
        val exception = Sep45TimeoutException("Request timed out after 30 seconds")

        assertEquals("Request timed out after 30 seconds", exception.message)
        assertIs<Sep45Exception>(exception)
    }

    @Test
    fun testMissingClientDomainExceptionMessage() {
        val exception = Sep45MissingClientDomainException("clientDomain provided without signing mechanism")

        assertEquals("clientDomain provided without signing mechanism", exception.message)
        assertIs<Sep45Exception>(exception)
    }

    @Test
    fun testUnknownResponseExceptionMessage() {
        val exception = Sep45UnknownResponseException(
            message = "Unexpected response format",
            code = 502,
            body = "<html>Bad Gateway</html>"
        )

        assertEquals(502, exception.code)
        assertEquals("<html>Bad Gateway</html>", exception.body)
        assertTrue(exception.message!!.contains("Unexpected response format"))
        assertTrue(exception.message!!.contains("HTTP 502"))
        assertTrue(exception.message!!.contains("<html>Bad Gateway</html>"))
        assertIs<Sep45Exception>(exception)
    }

    @Test
    fun testUnknownResponseExceptionWithEmptyBody() {
        val exception = Sep45UnknownResponseException(
            message = "Empty response",
            code = 204,
            body = ""
        )

        assertEquals(204, exception.code)
        assertEquals("", exception.body)
        assertTrue(exception.message!!.contains("Empty response"))
        assertTrue(exception.message!!.contains("HTTP 204"))
    }

    // ========== Sealed Class Tests ==========

    @Test
    fun testValidationExceptionIsSealed() {
        // Create instances of all validation exception types
        val exceptions: List<Sep45ChallengeValidationException> = listOf(
            Sep45InvalidContractAddressException("expected", "actual"),
            Sep45InvalidFunctionNameException("expected", "actual"),
            Sep45InvalidHomeDomainException("expected", "actual"),
            Sep45InvalidNetworkPassphraseException("expected", "actual"),
            Sep45InvalidAccountException("expected", "actual"),
            Sep45InvalidWebAuthDomainException("expected", "actual"),
            Sep45InvalidNonceException("message"),
            Sep45InvalidServerSignatureException("message"),
            Sep45InvalidArgsException("message"),
            Sep45SubInvocationsFoundException("message"),
            Sep45MissingClientEntryException("message"),
            Sep45MissingServerEntryException("message")
        )

        // Verify all instances are Sep45ChallengeValidationException
        for (exception in exceptions) {
            assertIs<Sep45ChallengeValidationException>(exception)
            assertIs<Sep45Exception>(exception)
        }

        // Verify exhaustive when handling compiles correctly
        for (exception in exceptions) {
            val result = when (exception) {
                is Sep45InvalidContractAddressException -> "contract"
                is Sep45InvalidFunctionNameException -> "function"
                is Sep45InvalidHomeDomainException -> "home_domain"
                is Sep45InvalidNetworkPassphraseException -> "network"
                is Sep45InvalidAccountException -> "account"
                is Sep45InvalidWebAuthDomainException -> "web_auth_domain"
                is Sep45InvalidNonceException -> "nonce"
                is Sep45InvalidServerSignatureException -> "signature"
                is Sep45InvalidArgsException -> "args"
                is Sep45SubInvocationsFoundException -> "sub_invocations"
                is Sep45MissingClientEntryException -> "client_entry"
                is Sep45MissingServerEntryException -> "server_entry"
            }
            assertNotNull(result)
        }
    }

    @Test
    fun testAllExceptionsAreInstancesOfSep45Exception() {
        val exceptions: List<Sep45Exception> = listOf(
            Sep45Exception("base"),
            Sep45ChallengeRequestException("request"),
            Sep45TokenSubmissionException("submission"),
            Sep45NoEndpointException("domain"),
            Sep45NoContractIdException("domain"),
            Sep45NoSigningKeyException("domain"),
            Sep45TimeoutException("timeout"),
            Sep45MissingClientDomainException("config"),
            Sep45UnknownResponseException("unknown", 500, "body"),
            Sep45InvalidContractAddressException("expected", "actual"),
            Sep45InvalidFunctionNameException("expected", "actual"),
            Sep45InvalidHomeDomainException("expected", "actual"),
            Sep45InvalidNetworkPassphraseException("expected", "actual"),
            Sep45InvalidAccountException("expected", "actual"),
            Sep45InvalidWebAuthDomainException("expected", "actual"),
            Sep45InvalidNonceException("message"),
            Sep45InvalidServerSignatureException("message"),
            Sep45InvalidArgsException("message"),
            Sep45SubInvocationsFoundException("message"),
            Sep45MissingClientEntryException("message"),
            Sep45MissingServerEntryException("message")
        )

        assertEquals(21, exceptions.size)

        for (exception in exceptions) {
            assertIs<Sep45Exception>(exception)
            assertIs<Exception>(exception)
            assertNotNull(exception.message)
        }
    }

    // ========== Property Preservation Tests ==========

    @Test
    fun testExpectedActualPropertiesPreserved() {
        // Test that expected/actual properties are correctly stored and accessible
        val contractEx = Sep45InvalidContractAddressException("C1", "C2")
        assertEquals("C1", contractEx.expected)
        assertEquals("C2", contractEx.actual)

        val functionEx = Sep45InvalidFunctionNameException("web_auth_verify", "transfer")
        assertEquals("web_auth_verify", functionEx.expected)
        assertEquals("transfer", functionEx.actual)

        val homeEx = Sep45InvalidHomeDomainException("good.com", "bad.com")
        assertEquals("good.com", homeEx.expected)
        assertEquals("bad.com", homeEx.actual)

        val networkEx = Sep45InvalidNetworkPassphraseException("Public", "Test")
        assertEquals("Public", networkEx.expected)
        assertEquals("Test", networkEx.actual)

        val accountEx = Sep45InvalidAccountException("CDYF...", "CWRONG...")
        assertEquals("CDYF...", accountEx.expected)
        assertEquals("CWRONG...", accountEx.actual)

        val webAuthEx = Sep45InvalidWebAuthDomainException("api.example.com", "other.com")
        assertEquals("api.example.com", webAuthEx.expected)
        assertEquals("other.com", webAuthEx.actual)
    }

    @Test
    fun testConfigurationExceptionDomainPropertyPreserved() {
        val endpointEx = Sep45NoEndpointException("domain1.com")
        assertEquals("domain1.com", endpointEx.domain)

        val contractIdEx = Sep45NoContractIdException("domain2.com")
        assertEquals("domain2.com", contractIdEx.domain)

        val signingKeyEx = Sep45NoSigningKeyException("domain3.com")
        assertEquals("domain3.com", signingKeyEx.domain)
    }

    @Test
    fun testUnknownResponsePropertiesPreserved() {
        val exception = Sep45UnknownResponseException(
            message = "Failed",
            code = 418,
            body = "I'm a teapot"
        )

        assertEquals(418, exception.code)
        assertEquals("I'm a teapot", exception.body)
    }
}
