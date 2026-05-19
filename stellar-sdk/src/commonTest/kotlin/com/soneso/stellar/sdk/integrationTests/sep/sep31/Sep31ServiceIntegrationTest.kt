// Copyright 2026 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.integrationTests.sep.sep31

import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.integrationTests.realDelay
import com.soneso.stellar.sdk.sep.sep10.WebAuth
import com.soneso.stellar.sdk.sep.sep31.Sep31PostTransactionsRequest
import com.soneso.stellar.sdk.sep.sep31.Sep31Service
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31BadRequestException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31CustomerInfoNeededException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31ForbiddenException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionCallbackNotSupportedException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31TransactionNotFoundException
import com.soneso.stellar.sdk.sep.sep31.exceptions.Sep31UnauthorizedException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for [Sep31Service] against the live Stellar testnet.
 *
 * Anchor: testanchor.stellar.org
 * - DIRECT_PAYMENT_SERVER: https://testanchor.stellar.org/sep31
 * - NETWORK_PASSPHRASE: Test SDF Network ; September 2015
 *
 * The anchor's `GET /info` returns `{"receive":{}}` (no assets configured), so the
 * tests exercise discovery and error-path branches against the live wire. Full
 * lifecycle assertions (status transitions, fee_details, refunds, callback success)
 * are covered by the mock-engine unit tests in `Sep31ServiceTest.kt`.
 *
 * Authentication: SEP-10 via [WebAuth.fromDomain] + [WebAuth.jwtToken]. Each test
 * that needs a JWT calls [obtainJwtToken], which generates a random keypair, funds
 * it via Friendbot, and authenticates. Account funding takes about one ledger close
 * (~5 s); [realDelay] is used to wait for testnet settlement.
 *
 * Note: These tests are not marked @Ignore. They require connectivity to
 * https://testanchor.stellar.org and https://friendbot.stellar.org.
 */
class Sep31ServiceIntegrationTest {

    private val anchorDomain = "testanchor.stellar.org"
    private val directPaymentServer = "https://testanchor.stellar.org/sep31"
    private val network = Network.TESTNET

    /**
     * Generates a random keypair, funds it via Friendbot, and obtains a SEP-10 JWT.
     *
     * Friendbot funding is asynchronous; [realDelay] waits for testnet settlement before
     * the SEP-10 challenge is requested.
     *
     * @return A JWT token string valid for testanchor.stellar.org.
     */
    private suspend fun obtainJwtToken(): String {
        val keyPair = KeyPair.random()
        val accountId = keyPair.getAccountId()

        val funded = FriendBot.fundTestnetAccount(accountId)
        assertTrue(funded, "Friendbot must fund the account before SEP-10 auth can proceed")
        // Wait for the account to settle on the testnet ledger (~5 s is sufficient).
        realDelay(5_000L)

        val webAuth = WebAuth.fromDomain(
            domain = anchorDomain,
            network = network,
        )
        val authToken = webAuth.jwtToken(
            clientAccountId = accountId,
            signers = listOf(keyPair),
        )
        return authToken.token
    }

    /**
     * Validates that [Sep31Service.fromDomain] resolves the anchor's stellar.toml and
     * returns a service configured with the advertised DIRECT_PAYMENT_SERVER URL.
     */
    @Test
    fun fromDomain_validDomain_resolvesService() = runTest(timeout = 60.seconds) {
        val service = Sep31Service.fromDomain(domain = anchorDomain)

        assertNotNull(service, "fromDomain must return a non-null Sep31Service")
        assertTrue(
            service.serviceUrl.startsWith("https://"),
            "Resolved serviceUrl must use HTTPS"
        )
        assertTrue(
            service.serviceUrl.contains("testanchor.stellar.org"),
            "Resolved serviceUrl must reference the testanchor host"
        )
    }

    /**
     * Validates that [Sep31Service.info] with a valid SEP-10 JWT returns a parseable
     * response. The anchor returns `{"receive":{}}`, so the assertion is structural
     * (the response parses) rather than content-based.
     */
    @Test
    fun info_returnsOkResponse() = runTest(timeout = 90.seconds) {
        val jwtToken = obtainJwtToken()
        val service = Sep31Service.fromDomain(domain = anchorDomain)

        val response = service.info(jwt = jwtToken)

        assertIs<Map<*, *>>(response.receiveAssets)
    }

    /**
     * Validates that [Sep31Service.info] accepts the optional `lang` query parameter
     * without altering response parsing.
     */
    @Test
    fun info_withLang_returnsOkResponse() = runTest(timeout = 90.seconds) {
        val jwtToken = obtainJwtToken()
        val service = Sep31Service.fromDomain(domain = anchorDomain)

        val response = service.info(jwt = jwtToken, lang = "en")

        assertIs<Map<*, *>>(response.receiveAssets)
    }

    /**
     * Validates that [Sep31Service.getTransaction] throws [Sep31TransactionNotFoundException]
     * when the anchor cannot locate a transaction matching the supplied id.
     */
    @Test
    fun getTransaction_nonexistentId_throwsSep31TransactionNotFoundException() =
        runTest(timeout = 90.seconds) {
            val jwtToken = obtainJwtToken()
            val service = Sep31Service.fromDomain(domain = anchorDomain)

            val exception = assertFailsWith<Sep31TransactionNotFoundException> {
                service.getTransaction(
                    id = "82fhs729f63dh0v4-nonexistent",
                    jwt = jwtToken,
                )
            }

            assertTrue(
                exception.statusCode == 404,
                "Exception statusCode must be 404; got ${exception.statusCode}"
            )
        }

    /**
     * Validates that [Sep31Service.patchTransaction] throws
     * [Sep31TransactionNotFoundException] when the anchor cannot locate the supplied
     * transaction id. The endpoint is part of the deprecated info-update flow and
     * shares the SDK's 404 mapping with [Sep31Service.getTransaction].
     */
    @Test
    fun patchTransaction_nonexistentId_throwsSep31TransactionNotFoundException() =
        runTest(timeout = 90.seconds) {
            val jwtToken = obtainJwtToken()
            val service = Sep31Service.fromDomain(domain = anchorDomain)

            val exception = assertFailsWith<Sep31TransactionNotFoundException> {
                service.patchTransaction(
                    id = "82fhs729f63dh0v4-nonexistent",
                    fields = mapOf(
                        "transaction" to mapOf("receiver_bank_account" to "1234567890"),
                    ),
                    jwt = jwtToken,
                )
            }

            assertTrue(
                exception.statusCode == 404,
                "Exception statusCode must be 404; got ${exception.statusCode}"
            )
        }

    /**
     * Validates the PUT /transactions/:id/callback 404 path.
     *
     * Anchors may map "transaction not found" and "callbacks not supported" to the
     * same HTTP 404 response, so the test accepts either
     * [Sep31TransactionCallbackNotSupportedException] or
     * [Sep31TransactionNotFoundException].
     */
    @Test
    fun putTransactionCallback_unknownId_throwsAppropriateException() =
        runTest(timeout = 90.seconds) {
            val jwtToken = obtainJwtToken()
            val service = Sep31Service.fromDomain(domain = anchorDomain)

            var caughtStatusCode: Int? = null
            var caughtIsCallbackException = false
            var caughtIsNotFoundException = false

            try {
                service.putTransactionCallback(
                    id = "82fhs729f63dh0v4-nonexistent",
                    callbackUrl = "https://example.com/sep31-callback",
                    jwt = jwtToken,
                )
            } catch (e: Sep31TransactionCallbackNotSupportedException) {
                caughtIsCallbackException = true
                caughtStatusCode = e.statusCode
            } catch (e: Sep31TransactionNotFoundException) {
                caughtIsNotFoundException = true
                caughtStatusCode = e.statusCode
            }

            assertTrue(
                caughtIsCallbackException || caughtIsNotFoundException,
                "Must throw Sep31TransactionCallbackNotSupportedException or " +
                    "Sep31TransactionNotFoundException; anchor returned no exception"
            )
            assertTrue(
                caughtStatusCode == 404,
                "Exception statusCode must be 404; got $caughtStatusCode"
            )
        }

    /**
     * Validates that [Sep31Service.postTransactions] throws a typed SEP-31 exception
     * when the anchor rejects the request.
     *
     * The anchor returns HTTP 400 (`error: "customer_info_needed"`) for accounts that
     * have not completed SEP-12 KYC registration; this maps to
     * [Sep31CustomerInfoNeededException]. Other deployments may surface
     * [Sep31BadRequestException] or [Sep31ForbiddenException] depending on
     * configuration.
     */
    @Test
    fun postTransactions_throwsExpectedExceptionForTestAccount() =
        runTest(timeout = 90.seconds) {
            val jwtToken = obtainJwtToken()
            val service = Sep31Service.fromDomain(domain = anchorDomain)

            val request = Sep31PostTransactionsRequest(
                amount = 100.0,
                assetCode = "USDC",
                fundingMethod = "SWIFT",
            )

            var caughtCustomerInfoException: Sep31CustomerInfoNeededException? = null
            var caughtBadRequestException: Sep31BadRequestException? = null
            var caughtForbiddenException: Sep31ForbiddenException? = null

            try {
                service.postTransactions(request = request, jwt = jwtToken)
            } catch (e: Sep31CustomerInfoNeededException) {
                caughtCustomerInfoException = e
            } catch (e: Sep31BadRequestException) {
                caughtBadRequestException = e
            } catch (e: Sep31ForbiddenException) {
                // Some anchor configurations return 403 for unregistered accounts.
                caughtForbiddenException = e
            }

            val anyExpected = caughtCustomerInfoException != null ||
                caughtBadRequestException != null ||
                caughtForbiddenException != null

            assertTrue(
                anyExpected,
                "postTransactions must throw Sep31CustomerInfoNeededException, " +
                    "Sep31BadRequestException, or Sep31ForbiddenException for an " +
                    "unregistered test account"
            )

            // When the anchor signals customer_info_needed, assert the error tag is correct.
            caughtCustomerInfoException?.let { e ->
                assertTrue(
                    e.error == "customer_info_needed",
                    "Sep31CustomerInfoNeededException.error must be 'customer_info_needed'; got '${e.error}'"
                )
            }

            // Sep31BadRequestException carries statusCode 400.
            caughtBadRequestException?.let { e ->
                assertTrue(
                    e.statusCode == 400,
                    "Sep31BadRequestException.statusCode must be 400; got ${e.statusCode}"
                )
            }

            // Sep31ForbiddenException carries statusCode 403.
            caughtForbiddenException?.let { e ->
                assertTrue(
                    e.statusCode == 403,
                    "Sep31ForbiddenException.statusCode must be 403; got ${e.statusCode}"
                )
            }
        }

    /**
     * Validates that each protected SEP-31 endpoint rejects an invalid JWT with a typed
     * auth-failure exception.
     *
     * Anchors map invalid-JWT to either HTTP 401 or 403; some return 400 instead. The
     * test accepts [Sep31UnauthorizedException], [Sep31ForbiddenException], or
     * [Sep31BadRequestException] for every endpoint, and additionally accepts
     * [Sep31TransactionCallbackNotSupportedException] and
     * [Sep31TransactionNotFoundException] for the endpoints that share a 404 path with
     * those exception types.
     */
    @Test
    fun protectedEndpoints_invalidJwt_rejected() = runTest(timeout = 90.seconds) {
        val service = Sep31Service.fromDomain(domain = anchorDomain)
        val invalidJwt = "invalid_token_12345"
        val nonexistentId = "82fhs729f63dh0v4-nonexistent"

        // POST /transactions
        val postError = runCatching {
            service.postTransactions(
                request = Sep31PostTransactionsRequest(
                    amount = 100.0,
                    assetCode = "USDC",
                    fundingMethod = "SWIFT",
                ),
                jwt = invalidJwt,
            )
        }.exceptionOrNull()
        assertTrue(
            postError is Sep31UnauthorizedException ||
                postError is Sep31ForbiddenException ||
                postError is Sep31BadRequestException,
            "postTransactions with invalid JWT must throw 401/403/400; got: " +
                "${postError?.let { it::class.simpleName }}"
        )

        // GET /transactions/:id
        val getError = runCatching {
            service.getTransaction(id = nonexistentId, jwt = invalidJwt)
        }.exceptionOrNull()
        assertTrue(
            getError is Sep31UnauthorizedException ||
                getError is Sep31ForbiddenException ||
                getError is Sep31BadRequestException,
            "getTransaction with invalid JWT must throw 401/403/400; got: " +
                "${getError?.let { it::class.simpleName }}"
        )

        // PUT /transactions/:id/callback
        val callbackError = runCatching {
            service.putTransactionCallback(
                id = nonexistentId,
                callbackUrl = "https://example.com/sep31-callback",
                jwt = invalidJwt,
            )
        }.exceptionOrNull()
        assertTrue(
            callbackError is Sep31UnauthorizedException ||
                callbackError is Sep31ForbiddenException ||
                callbackError is Sep31BadRequestException ||
                callbackError is Sep31TransactionCallbackNotSupportedException,
            "putTransactionCallback with invalid JWT must throw 401/403/400/404; got: " +
                "${callbackError?.let { it::class.simpleName }}"
        )

        // PATCH /transactions/:id
        val patchError = runCatching {
            service.patchTransaction(
                id = nonexistentId,
                fields = mapOf(
                    "transaction" to mapOf("receiver_bank_account" to "1234567890"),
                ),
                jwt = invalidJwt,
            )
        }.exceptionOrNull()
        assertTrue(
            patchError is Sep31UnauthorizedException ||
                patchError is Sep31ForbiddenException ||
                patchError is Sep31BadRequestException ||
                patchError is Sep31TransactionNotFoundException,
            "patchTransaction with invalid JWT must throw 401/403/400/404; got: " +
                "${patchError?.let { it::class.simpleName }}"
        )
    }
}
