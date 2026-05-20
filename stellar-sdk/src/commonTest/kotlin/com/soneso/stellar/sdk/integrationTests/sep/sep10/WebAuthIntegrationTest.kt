// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.integrationTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for SEP-10 Web Authentication against the live Stellar testnet.
 *
 * Tests validate the SEP-10 authentication flow against testanchor.stellar.org,
 * a Stellar test anchor maintained by the Stellar Development Foundation.
 *
 * Test coverage:
 * - Basic authentication flow (challenge request -> validate -> sign -> submit -> JWT)
 * - Client domain verification via remote signing service
 *
 * Network requirements:
 * - Connectivity to https://testanchor.stellar.org
 * - The client domain test additionally requires connectivity to https://testsigner.stellargate.com
 * - Tests use randomly generated keypairs (no account funding required)
 *
 * Reference:
 * - SEP-10 Specification: https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md
 * - Test Anchor: https://testanchor.stellar.org
 * - Test Anchor stellar.toml: https://testanchor.stellar.org/.well-known/stellar.toml
 * - Test Anchor SIGNING_KEY: GCHLHDBOKG2JWMJQBTLSL5XG6NO7ESXI2TAQKZXCXWXB5WI2X6W233PR
 * - Test Anchor WEB_AUTH_ENDPOINT: https://testanchor.stellar.org/auth
 * - Remote signer service: https://testsigner.stellargate.com (source: https://github.com/Soneso/go-server-signer)
 *
 * Note: these tests are not marked @Ignore; they always have testnet connectivity
 * and run as part of the standard test suite.
 */
class WebAuthIntegrationTest {

    // Test anchor configuration (from stellar.toml).
    private val testAnchorDomain = "testanchor.stellar.org"
    private val network = Network.TESTNET

    // Client domain signing service. The signing key is held by the remote
    // service and matches SIGNING_KEY in https://testsigner.stellargate.com/.well-known/stellar.toml.
    private val testClientDomain = "testsigner.stellargate.com"
    private val remoteSigningServiceUrl = "https://testsigner.stellargate.com/sign-sep-10"
    private val remoteSigningServiceToken =
        "Bearer 7b23fe8428e7fb9b3335ed36c39fb5649d3cd7361af8bf88c2554d62e8ca3017"

    /**
     * Tests the basic SEP-10 authentication flow without client domain verification.
     *
     * The most common pattern: a wallet generates a keypair, requests a challenge,
     * signs it, and exchanges the signed challenge for a JWT.
     *
     * Validates that the returned token carries the expected `sub`, `iss`, `iat`,
     * and `exp` claims and that `sub` matches the user account ID.
     */
    @Test
    fun testBasicAuthentication() = runTest {
        // Initialize WebAuth from domain (discovers configuration from stellar.toml)
        val webAuth = WebAuth.fromDomain(
            domain = testAnchorDomain,
            network = network
        )

        // Generate random user keypair (no account funding required)
        val userKeyPair = KeyPair.random()
        val userAccountId = userKeyPair.getAccountId()

        // Execute complete authentication flow
        val authToken = webAuth.jwtToken(
            clientAccountId = userAccountId,
            signers = listOf(userKeyPair)
        )

        // Validate token was received
        assertNotNull(authToken.token, "JWT token should be present")
        assertTrue(authToken.token.isNotEmpty(), "JWT token should not be empty")

        // Validate token structure
        assertNotNull(authToken.account, "Token should contain account claim")
        assertTrue(authToken.account == userAccountId, "Token account should match user account")

        // Validate token expiration
        assertNotNull(authToken.exp, "Token should have expiration")
        assertTrue(authToken.exp > 0, "Token expiration should be valid timestamp")

        // Validate token issuer
        assertNotNull(authToken.iss, "Token should have issuer")

        // Validate token issued-at
        assertNotNull(authToken.iat, "Token should have issued-at timestamp")
        assertTrue(authToken.iat > 0, "Token issued-at should be valid timestamp")

        println("Basic authentication successful:")
        println("  Account: ${authToken.account}")
        println("  Issuer: ${authToken.iss}")
        println("  Issued At: ${authToken.iat}")
        println("  Expires: ${authToken.exp}")
    }

    /**
     * Tests client domain verification with a remote signing service.
     *
     * The client-domain signing key is held by an external service that signs
     * the SDK-built transaction over HTTP. Production use cases include wallet
     * backends that delegate signing to HSMs, cloud KMS, or MPC services.
     *
     * Flow:
     * 1. SDK builds the SEP-10 challenge with the `client_domain` operation.
     * 2. The signing delegate POSTs the transaction XDR plus network passphrase
     *    to the remote service with a bearer token.
     * 3. The service signs with the client-domain key and returns the signed XDR.
     * 4. The SDK co-signs with the user keypair and submits the signed challenge.
     * 5. The auth server returns a JWT.
     *
     * Request body: `{"transaction": "<xdr>", "network_passphrase": "<passphrase>"}`.
     * Response body: `{"transaction": "<signed-xdr>"}`.
     */
    @Test
    fun testRemoteClientDomainSigningCallback() = runTest {
        // Initialize WebAuth from domain
        val webAuth = WebAuth.fromDomain(
            domain = testAnchorDomain,
            network = network
        )

        // Generate random user keypair
        val userKeyPair = KeyPair.random()
        val userAccountId = userKeyPair.getAccountId()

        // Create JSON parser
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        // Create HTTP client for remote signing service
        val httpClient = HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000 // 30 second timeout
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
        }

        // Create remote signing delegate - simplified to return signed transaction directly
        val remoteSigningDelegate = ClientDomainSigningDelegate { transactionXdr ->
            try {
                // Make HTTP POST request to remote signing service
                val response = httpClient.post(remoteSigningServiceUrl) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", remoteSigningServiceToken)
                    setBody(RemoteSigningRequest(
                        transaction = transactionXdr,
                        network_passphrase = network.networkPassphrase
                    ))
                }

                // Check response status
                if (!response.status.isSuccess()) {
                    throw IllegalStateException(
                        "Remote signing service returned error: ${response.status.value} - ${response.status.description}"
                    )
                }

                // Parse response - service returns full signed transaction XDR
                val responseText = response.bodyAsText()
                val responseBody = json.decodeFromString<RemoteSigningResponse>(responseText)

                // Validate response has transaction field
                if (responseBody.transaction.isBlank()) {
                    throw IllegalStateException("Remote signing service response missing 'transaction' field")
                }

                // Return signed transaction directly
                responseBody.transaction
            } catch (e: Exception) {
                // Provide clear error message for network/service failures
                throw IllegalStateException(
                    "Failed to sign transaction with remote service at $remoteSigningServiceUrl: ${e.message}. " +
                    "Ensure the remote signing service is accessible and properly configured.",
                    e
                )
            }
        }

        // Execute authentication with remote signing delegate
        val authToken = webAuth.jwtToken(
            clientAccountId = userAccountId,
            signers = listOf(userKeyPair),
            clientDomain = testClientDomain,
            clientDomainSigningDelegate = remoteSigningDelegate
        )

        // Cleanup HTTP client
        httpClient.close()

        // Validate token was received
        assertNotNull(authToken.token, "JWT token should be present")
        assertTrue(authToken.token.isNotEmpty(), "JWT token should not be empty")

        // Validate token structure
        assertNotNull(authToken.account, "Token should contain account claim")
        assertTrue(authToken.account == userAccountId, "Token account should match user account")

        // Validate token expiration
        assertNotNull(authToken.exp, "Token should have expiration")
        assertTrue(authToken.exp > 0, "Token expiration should be valid timestamp")

        // Validate token issuer
        assertNotNull(authToken.iss, "Token should have issuer")

        println("Remote client domain signing callback authentication successful:")
        println("  Account: ${authToken.account}")
        println("  Client Domain: $testClientDomain")
        println("  Signing Method: Remote HTTP Signing Service")
        println("  Signing Service: $remoteSigningServiceUrl")
        println("  Issuer: ${authToken.iss}")
        println("  Expires: ${authToken.exp}")
    }

    /**
     * Request format for the remote signing service.
     */
    @Serializable
    private data class RemoteSigningRequest(
        val transaction: String,
        val network_passphrase: String
    )

    /**
     * Response format from the remote signing service.
     */
    @Serializable
    private data class RemoteSigningResponse(
        val transaction: String
    )
}
