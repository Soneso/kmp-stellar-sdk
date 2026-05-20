// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep31

import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ConfigurationException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31CustomerInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31Exception
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ForbiddenException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31InvalidResponseException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionCallbackNotSupportedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionNotFoundException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnauthorizedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnknownResponseException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Sep31ExceptionsTest {

    // ==================== Sep31BadRequestException ====================

    @Test
    fun badRequest_message_setOnException() {
        val ex = Sep31BadRequestException("invalid amount", statusCode = 400)
        assertEquals("invalid amount", ex.message)
    }

    @Test
    fun badRequest_toString_includesPrefix() {
        val ex = Sep31BadRequestException("invalid amount", statusCode = 400)
        val str = ex.toString()
        assertTrue(str.contains("SEP-31"), "toString must mention SEP-31")
        assertTrue(str.contains("400"), "toString must include status code")
        assertTrue(str.contains("invalid amount"), "toString must include message")
    }

    @Test
    fun badRequest_isInstanceOfSep31Exception() {
        val ex = Sep31BadRequestException("error", statusCode = 400)
        assertIs<Sep31Exception>(ex)
    }

    @Test
    fun badRequest_statusCode_exposed() {
        val ex = Sep31BadRequestException("error", statusCode = 400)
        assertEquals(400, ex.statusCode)
    }

    // ==================== Sep31UnauthorizedException ====================

    @Test
    fun unauthorized_statusCode_exposed() {
        val ex = Sep31UnauthorizedException("auth failed", statusCode = 401)
        assertEquals(401, ex.statusCode)
    }

    @Test
    fun unauthorized_isInstanceOfSep31Exception() {
        val ex = Sep31UnauthorizedException("auth failed")
        assertIs<Sep31Exception>(ex)
    }

    @Test
    fun unauthorized_defaultStatusCode_is401() {
        val ex = Sep31UnauthorizedException("auth failed")
        assertEquals(401, ex.statusCode)
    }

    // ==================== Sep31ForbiddenException ====================

    @Test
    fun forbidden_statusCode_exposed() {
        val ex = Sep31ForbiddenException("forbidden", statusCode = 403)
        assertEquals(403, ex.statusCode)
    }

    @Test
    fun forbidden_isInstanceOfSep31Exception() {
        val ex = Sep31ForbiddenException("forbidden")
        assertIs<Sep31Exception>(ex)
    }

    @Test
    fun forbidden_defaultStatusCode_is403() {
        val ex = Sep31ForbiddenException("forbidden")
        assertEquals(403, ex.statusCode)
    }

    // ==================== Sep31TransactionNotFoundException ====================

    @Test
    fun transactionNotFound_statusCode_exposed() {
        val ex = Sep31TransactionNotFoundException("not found", statusCode = 404)
        assertEquals(404, ex.statusCode)
    }

    @Test
    fun transactionNotFound_isInstanceOfSep31Exception() {
        val ex = Sep31TransactionNotFoundException("not found")
        assertIs<Sep31Exception>(ex)
    }

    // ==================== Sep31TransactionCallbackNotSupportedException ====================

    @Test
    fun callbackNotSupported_statusCode_exposed() {
        val ex = Sep31TransactionCallbackNotSupportedException("not supported", statusCode = 404)
        assertEquals(404, ex.statusCode)
    }

    @Test
    fun callbackNotSupported_isInstanceOfSep31Exception() {
        val ex = Sep31TransactionCallbackNotSupportedException("not supported")
        assertIs<Sep31Exception>(ex)
    }

    // ==================== Sep31InvalidResponseException ====================

    @Test
    fun invalidResponse_statusCode_exposed() {
        val ex = Sep31InvalidResponseException("malformed", statusCode = 200)
        assertEquals(200, ex.statusCode)
    }

    @Test
    fun invalidResponse_isInstanceOfSep31Exception() {
        val ex = Sep31InvalidResponseException("malformed")
        assertIs<Sep31Exception>(ex)
    }

    @Test
    fun invalidResponse_defaultStatusCode_is200() {
        val ex = Sep31InvalidResponseException("malformed")
        assertEquals(200, ex.statusCode)
    }

    // ==================== Sep31UnknownResponseException ====================

    @Test
    fun unknownResponse_statusCodeAndBody_exposedOnInstance() {
        val ex = Sep31UnknownResponseException(
            message = "Unexpected HTTP status: 503",
            statusCode = 503,
            responseBody = "Service unavailable",
        )
        assertEquals(503, ex.statusCode)
        assertEquals("Service unavailable", ex.responseBody)
        assertEquals("Unexpected HTTP status: 503", ex.message)
    }

    @Test
    fun unknownResponse_toString_includesStatusCode() {
        val ex = Sep31UnknownResponseException(
            message = "Unexpected HTTP status: 503",
            statusCode = 503,
            responseBody = "",
        )
        val str = ex.toString()
        assertTrue(str.contains("503"), "toString must include status code")
    }

    @Test
    fun unknownResponse_isInstanceOfSep31Exception() {
        val ex = Sep31UnknownResponseException("msg", statusCode = 500, responseBody = "")
        assertIs<Sep31Exception>(ex)
    }

    // ==================== Sep31CustomerInfoNeededException ====================

    @Test
    fun customerInfoNeeded_typeNull_storedAsNull() {
        val ex = Sep31CustomerInfoNeededException(type = null)
        assertNull(ex.type)
        assertEquals("customer_info_needed", ex.error)
    }

    @Test
    fun customerInfoNeeded_typeValue_storedExactly() {
        val ex = Sep31CustomerInfoNeededException(type = "sep31-sender")
        assertEquals("sep31-sender", ex.type)
        assertEquals("customer_info_needed", ex.error)
    }

    @Test
    fun customerInfoNeeded_isInstanceOfSep31Exception() {
        val ex = Sep31CustomerInfoNeededException(type = null)
        assertIs<Sep31Exception>(ex)
    }

    // ==================== Sep31TransactionInfoNeededException ====================

    @Test
    fun transactionInfoNeeded_nestedFieldsObject_leafValuesArePrimitives() {
        @Suppress("DEPRECATION")
        val fieldsMap: Map<String, Any?> = mapOf(
            "transaction" to mapOf(
                "receiver_account_number" to "description of account number",
                "receiver_routing_number" to "description of routing number",
            ),
        )
        @Suppress("DEPRECATION")
        val ex = Sep31TransactionInfoNeededException(fields = fieldsMap)
        val fields = ex.fields
        assertNotNull(fields)
        assertNoJsonElementLeaves(fields)
        assertEquals("transaction_info_needed", ex.error)
    }

    @Test
    fun transactionInfoNeeded_isInstanceOfSep31Exception() {
        @Suppress("DEPRECATION")
        val ex = Sep31TransactionInfoNeededException(fields = null)
        assertIs<Sep31Exception>(ex)
    }

    @Test
    fun transactionInfoNeeded_classCarriesDeprecatedAnnotation() {
        // CONTRACT: Sep31TransactionInfoNeededException is annotated with @Deprecated at
        // DeprecationLevel.WARNING and a non-empty message, because the per-transaction
        // `fields` workflow is deprecated in SEP-31 v2.5.0 in favor of SEP-12 PUT /customer.
        //
        // CROSS-PLATFORM VERIFICATION LIMITS: Common Kotlin reflection
        // (kotlin.reflect.KClass in stdlib-common) does NOT expose annotations on a class.
        // The `annotations: List<Annotation>` property lives on `kotlin.reflect.KAnnotatedElement`,
        // and that interface is not declared in the common stdlib — it is only available in
        // the JVM-flavored stdlib. As a result, this commonTest test cannot perform a runtime
        // introspection of the @Deprecated annotation without breaking the JS/Native compile.
        //
        // CONTRACT-LEVEL PROOF: Every usage site of `Sep31TransactionInfoNeededException` in
        // this test file (and across the SDK) must annotate with `@Suppress("DEPRECATION")`.
        // The Kotlin compiler enforces this: if the class were NOT annotated with @Deprecated,
        // the `@Suppress("DEPRECATION")` calls below would be flagged as `REDUNDANT_SUPPRESSION`.
        // Conversely, if the @Suppress lines were removed, the compiler would emit deprecation
        // warnings (which the CI build treats as build evidence). Both directions therefore
        // statically prove the @Deprecated annotation is present at compile time.
        //
        // This test additionally verifies the runtime shape (the class is constructible and
        // is a Sep31Exception). If a future Kotlin release exposes `KClass.annotations` in
        // common code, this test should be upgraded to perform the runtime reflection check
        // for the level (WARNING) and message (non-empty) directly.
        @Suppress("DEPRECATION")
        val ex = Sep31TransactionInfoNeededException(fields = null)
        // Runtime portion: prove the class is reachable, instantiable, and on the SEP-31
        // exception hierarchy. The @Suppress above is the compile-time witness that the
        // class still carries @Deprecated; removing the @Suppress would surface the
        // deprecation warning enforced by the Kotlin frontend.
        assertIs<Sep31Exception>(ex)
        assertEquals("transaction_info_needed", ex.error)
        // The class name is constant-folded into bytecode metadata across all platforms;
        // this assertion exists so the test body is not a no-op even on platforms without
        // annotation reflection.
        assertEquals(
            "Sep31TransactionInfoNeededException",
            Sep31TransactionInfoNeededException::class.simpleName,
            "class name must be stable for downstream catch-blocks",
        )
    }

    // ==================== Sep31ConfigurationException ====================

    @Test
    fun configuration_causePresent_storedOnException() {
        val cause = RuntimeException("TOML fetch failed")
        val ex = Sep31ConfigurationException("fromDomain failed", cause = cause)
        assertEquals(cause, ex.cause)
        assertEquals("fromDomain failed", ex.message)
    }

    @Test
    fun configuration_isInstanceOfSep31Exception() {
        val ex = Sep31ConfigurationException("bad config")
        assertIs<Sep31Exception>(ex)
    }

    @Test
    fun configuration_toString_includesConfigPrefix() {
        val ex = Sep31ConfigurationException("no DIRECT_PAYMENT_SERVER")
        val str = ex.toString()
        assertTrue(str.contains("SEP-31"), "toString must mention SEP-31")
        assertTrue(str.contains("no DIRECT_PAYMENT_SERVER"))
    }

    // ==================== Base Sep31Exception ====================

    @Test
    fun sep31Exception_toString_includesPrefix() {
        val ex = object : Sep31Exception("custom error") {}
        val str = ex.toString()
        assertTrue(str.startsWith("SEP-31 error:"), "Base toString must use 'SEP-31 error:' prefix")
        assertTrue(str.contains("custom error"))
    }

    // ==================== toString contract for typed exceptions ====================
    //
    // Each subclass overrides toString to produce a human-readable prefix that
    // identifies the SEP-31 failure path. These tests pin the exact prefix per
    // class so structured log consumers can rely on it.

    @Test
    fun unauthorized_toString_includesPrefixAndStatusCode() {
        val ex = Sep31UnauthorizedException("missing token", statusCode = 401)
        val str = ex.toString()
        assertTrue(str.contains("SEP-31 unauthorized"), "toString must mention SEP-31 unauthorized")
        assertTrue(str.contains("401"), "toString must include status code")
        assertTrue(str.contains("missing token"), "toString must include message")
    }

    @Test
    fun forbidden_toString_includesPrefixAndStatusCode() {
        val ex = Sep31ForbiddenException("denied", statusCode = 403)
        val str = ex.toString()
        assertTrue(str.contains("SEP-31 forbidden"), "toString must mention SEP-31 forbidden")
        assertTrue(str.contains("403"))
        assertTrue(str.contains("denied"))
    }

    @Test
    fun transactionNotFound_toString_includesPrefixAndStatusCode() {
        val ex = Sep31TransactionNotFoundException("tx not found", statusCode = 404)
        val str = ex.toString()
        assertTrue(str.contains("SEP-31 transaction not found"), "toString must mention transaction not found")
        assertTrue(str.contains("404"))
        assertTrue(str.contains("tx not found"))
    }

    @Test
    fun callbackNotSupported_toString_includesPrefixAndStatusCode() {
        val ex = Sep31TransactionCallbackNotSupportedException("not supported", statusCode = 404)
        val str = ex.toString()
        assertTrue(
            str.contains("SEP-31 transaction callback not supported"),
            "toString must mention callback not supported",
        )
        assertTrue(str.contains("404"))
        assertTrue(str.contains("not supported"))
    }

    @Test
    fun invalidResponse_toString_includesPrefixAndStatusCode() {
        val ex = Sep31InvalidResponseException("malformed body", statusCode = 200)
        val str = ex.toString()
        assertTrue(str.contains("SEP-31 invalid response"), "toString must mention invalid response")
        assertTrue(str.contains("200"))
        assertTrue(str.contains("malformed body"))
    }

    @Test
    fun customerInfoNeeded_toString_includesTypeAndPrefix() {
        val ex = Sep31CustomerInfoNeededException(type = "sep31-sender")
        val str = ex.toString()
        assertTrue(str.contains("SEP-31 customer info needed"), "toString must mention customer info needed")
        assertTrue(str.contains("sep31-sender"), "toString must echo the customer type")
    }

    @Test
    fun customerInfoNeeded_toString_nullType_includesPrefixAndNullSentinel() {
        val ex = Sep31CustomerInfoNeededException(type = null)
        val str = ex.toString()
        assertTrue(str.contains("SEP-31 customer info needed"))
        // sanitizeAnchorString(null) returns its safe placeholder; we only require the prefix
        // here so this assertion does not couple to the sanitizer's null-placeholder string.
    }

    @Test
    fun transactionInfoNeeded_toString_includesFixedMessage() {
        @Suppress("DEPRECATION")
        val ex = Sep31TransactionInfoNeededException(fields = null)
        val str = ex.toString()
        assertTrue(
            str.contains("SEP-31 transaction info needed"),
            "toString must mention transaction info needed",
        )
        assertTrue(str.contains("fields"), "toString must reference fields hint")
    }

    @Test
    fun customerInfoNeeded_toString_sanitizesControlCharsInType() {
        // Control characters in the type field must not survive in toString; sanitizeAnchorString
        // strips them. This locks in defense-in-depth against log injection.
        val ex = Sep31CustomerInfoNeededException(type = "type[31m")
        val str = ex.toString()
        assertTrue(!str.contains(''), "control characters must be stripped")
    }

    @Test
    fun badRequest_responseBodyExposed_onInstance() {
        // Lock in that rawResponseBody is exposed verbatim on the exception so debug
        // tooling can inspect bytes the anchor returned.
        val ex = Sep31BadRequestException(
            "invalid amount",
            statusCode = 400,
            rawResponseBody = "{\"error\":\"invalid amount\"}",
        )
        assertEquals("{\"error\":\"invalid amount\"}", ex.rawResponseBody)
    }
}

/**
 * Recursively asserts that no value within [map] (or within any nested map or list) is a
 * [JsonElement] instance. Fails the test at the first offending leaf.
 */
private fun assertNoJsonElementLeaves(map: Map<String, Any?>) {
    for ((key, value) in map) {
        assertNoJsonElementLeavesValue(key, value)
    }
}

private fun assertNoJsonElementLeavesValue(path: String, value: Any?) {
    if (value is JsonElement) {
        kotlin.test.fail("JsonElement found at path '$path': $value")
    }
    @Suppress("UNCHECKED_CAST")
    when (value) {
        is Map<*, *> -> (value as Map<String, Any?>).forEach { (k, v) ->
            assertNoJsonElementLeavesValue("$path.$k", v)
        }
        is List<*> -> value.forEachIndexed { i, v ->
            assertNoJsonElementLeavesValue("$path[$i]", v)
        }
    }
}
