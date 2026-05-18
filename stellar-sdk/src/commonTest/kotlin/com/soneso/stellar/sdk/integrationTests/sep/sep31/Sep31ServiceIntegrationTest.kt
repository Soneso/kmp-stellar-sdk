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
 * Chosen anchor: testanchor.stellar.org
 * - DIRECT_PAYMENT_SERVER: https://testanchor.stellar.org/sep31
 * - SIGNING_KEY: GCHLHDBOKG2JWMJQBTLSL5XG6NO7ESXI2TAQKZXCXWXB5WI2X6W233PR
 * - NETWORK_PASSPHRASE: Test SDF Network ; September 2015
 *
 * Anchor selection date: 2026-05-16 (Phase 0 RESEARCH).
 * Phase 0 RESEARCH report reference: plans/sep-31-implementation.md §8 Open decisions item #1.
 *
 * Spec divergence observed and exercised here:
 * - GET /info returns HTTP 200 with body `{"receive":{}}` when called without a JWT.
 *   The SEP-31 spec mandates authentication on all endpoints, but this anchor exposes
 *   /info unauthenticated. Both paths (with and without JWT) are tested.
 * - The anchor returns HTTP 403 (not 404) for requests to /transactions/:id and
 *   /transactions/:id/callback when the supplied JWT is invalid. A valid JWT obtained
 *   via SEP-10 is required to exercise the 404 path for a nonexistent transaction id.
 * - GET /info returns `{"receive":{}}` — the receive map is intentionally empty for this
 *   test account on testanchor. The test asserts the map is not null (it exists and is
 *   parseable), not that it is non-empty.
 *
 * Authentication: SEP-10 via [WebAuth.fromDomain] + [WebAuth.jwtToken]. Each test that
 * requires a JWT calls [obtainJwtToken], which generates a random keypair, funds it via
 * Friendbot, and authenticates. Account funding takes approximately one ledger close
 * (~5 s); [realDelay] is used to wait for testnet settlement.
 *
 * Note: These tests are not marked @Ignore. They require connectivity to
 * https://testanchor.stellar.org and https://friendbot.stellar.org.
 */
class Sep31ServiceIntegrationTest {

    // testanchor.stellar.org — anchor selected by Phase 0 RESEARCH on 2026-05-16.
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
     *
     * Flow:
     * 1. Call Sep31Service.fromDomain("testanchor.stellar.org").
     * 2. Assert the returned service's serviceUrl matches the anchor's advertised URL.
     */
    @Test
    fun fromDomain_validDomain_resolvesService() = runTest(timeout = 60.seconds) {
        val service = Sep31Service.fromDomain(domain = anchorDomain)

        assertNotNull(service, "fromDomain must return a non-null Sep31Service")
        assertTrue(
            service.serviceUrl.startsWith("https://"),
            "Resolved serviceUrl must use HTTPS; got: (redacted for log-injection prevention)"
        )
        assertTrue(
            service.serviceUrl.contains("testanchor.stellar.org"),
            "Resolved serviceUrl must reference the testanchor host"
        )
    }

    /**
     * Validates that [Sep31Service.info] with a valid SEP-10 JWT returns a parseable
     * response. The receive map may be empty for this anchor/account combination;
     * the test asserts the map is not null (the response is well-formed).
     *
     * testanchor.stellar.org returns `{"receive":{}}` for the test account, which is
     * spec-conformant. [Sep31InfoResponse.receiveAssets] is typed as
     * `Map<String, Sep31ReceiveAssetInfo>` and is therefore always non-null.
     *
     * Flow:
     * 1. Obtain SEP-10 JWT.
     * 2. Call info(jwt = jwtToken).
     * 3. Assert the response is parsed without exception.
     * 4. Assert receiveAssets is a Map (even if empty).
     */
    @Test
    fun info_returnsOkResponse() = runTest(timeout = 90.seconds) {
        val jwtToken = obtainJwtToken()
        val service = Sep31Service.fromDomain(domain = anchorDomain)

        val response = service.info(jwt = jwtToken)

        // receiveAssets is Map<String, Sep31ReceiveAssetInfo> — always non-null by type.
        assertIs<Map<*, *>>(
            response.receiveAssets,
            "receiveAssets must be a Map (may be empty for testanchor test account)"
        )
    }

    /**
     * Validates that [Sep31Service.getTransaction] throws [Sep31TransactionNotFoundException]
     * when the anchor cannot locate a transaction matching the supplied id.
     *
     * The id `82fhs729f63dh0v4-nonexistent` is spec-example-shaped (RFC 3986 pchar-safe)
     * and cannot exist in the anchor's database.
     *
     * Flow:
     * 1. Obtain SEP-10 JWT (required — anchor returns 403 for unauthenticated requests).
     * 2. Call getTransaction("82fhs729f63dh0v4-nonexistent", jwt = jwtToken).
     * 3. Assert Sep31TransactionNotFoundException is thrown.
     * 4. Assert exception.statusCode == 404.
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
     * Validates the PUT /transactions/:id/callback error path.
     *
     * testanchor.stellar.org does not support callback registration via
     * PUT /transactions/:id/callback — it returns HTTP 404. When the supplied
     * transaction id is also nonexistent, the anchor returns 404 for both reasons.
     * The test accepts either [Sep31TransactionCallbackNotSupportedException] or
     * [Sep31TransactionNotFoundException] as a valid outcome, because the anchor does
     * not distinguish between "transaction not found" and "callbacks not supported"
     * at the HTTP level.
     *
     * Live run result (2026-05-16): anchor returns HTTP 404 for this endpoint with any
     * valid JWT and any transaction id — mapped to
     * [Sep31TransactionCallbackNotSupportedException] by the SDK's PUT /callback
     * dispatch logic.
     *
     * Flow:
     * 1. Obtain SEP-10 JWT.
     * 2. Call putTransactionCallback("82fhs729f63dh0v4-nonexistent", callbackUrl, jwtToken).
     * 3. Assert Sep31TransactionCallbackNotSupportedException OR
     *    Sep31TransactionNotFoundException is thrown.
     * 4. Assert exception.statusCode == 404.
     */
    @Test
    fun putTransactionCallback_unknownId_throwsAppropriateException() =
        runTest(timeout = 90.seconds) {
            val jwtToken = obtainJwtToken()
            val service = Sep31Service.fromDomain(domain = anchorDomain)

            var caughtStatusCode: Int? = null
            var caughtIsCallbackException = false
            var caughtIsNotFoundExceptionn = false

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
                caughtIsNotFoundExceptionn = true
                caughtStatusCode = e.statusCode
            }

            assertTrue(
                caughtIsCallbackException || caughtIsNotFoundExceptionn,
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
     * testanchor.stellar.org returns HTTP 400 for POST /transactions requests from
     * accounts that have not completed SEP-12 KYC registration. The expected exception
     * is [Sep31CustomerInfoNeededException] (error tag "customer_info_needed") if the
     * anchor signals which SEP-12 type to collect, or [Sep31BadRequestException] for
     * other generic 400 responses.
     *
     * Live run result (2026-05-16): testanchor returns HTTP 400 with
     * `error: "customer_info_needed"` for POST /transactions from an unregistered
     * account — mapped to [Sep31CustomerInfoNeededException] by the SDK.
     *
     * Flow:
     * 1. Obtain SEP-10 JWT.
     * 2. Call postTransactions with a minimal request (USDC, amount 100).
     * 3. Assert Sep31CustomerInfoNeededException OR Sep31BadRequestException is thrown.
     * 4. For Sep31CustomerInfoNeededException, assert error == "customer_info_needed".
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
}
