// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.integrationTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.AbstractTransaction
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.Transaction
import io.ktor.client.*
import io.ktor.client.call.*
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
 * Integration tests for SEP-10 Web Authentication against live testnet anchor.
 *
 * These tests validate the complete SEP-10 authentication flow against
 * testanchor.stellar.org, a real Stellar test anchor maintained by the
 * Stellar Development Foundation.
 *
 * Test Coverage:
 * - Basic authentication flow (challenge request -> validate -> sign -> submit -> JWT)
 * - Client domain verification with local keypair signing
 * - Client domain verification with local signing delegate (HSM simulation)
 * - Client domain verification with remote signing service
 *
 * Network Requirements:
 * - These tests require connectivity to https://testanchor.stellar.org
 * - Tests use randomly generated keypairs (no account funding required)
 * - Remote signing test requires connectivity to https://server-signer.replit.app
 * - Average test duration: 5-10 seconds per test (depends on network latency)
 *
 * Security Note:
 * These integration tests demonstrate real-world SEP-10 usage patterns and
 * validate that the SDK correctly implements the full authentication protocol
 * against a production-grade authentication server.
 *
 * Reference:
 * - SEP-10 Specification: https://github.com/stellar/stellar-protocol/blob/master/ecosystem/sep-0010.md
 * - Test Anchor: https://testanchor.stellar.org
 * - Test Anchor stellar.toml: https://testanchor.stellar.org/.well-known/stellar.toml
 * - Test Anchor SIGNING_KEY: GCHLHDBOKG2JWMJQBTLSL5XG6NO7ESXI2TAQKZXCXWXB5WI2X6W233PR (from stellar.toml)
 * - Test Anchor WEB_AUTH_ENDPOINT: https://testanchor.stellar.org/auth (from stellar.toml)
 * - Remote Signer Service: https://server-signer.replit.app (test signing service)
 *
 * Note: These tests are NOT marked with @Ignore as they always have testnet
 * connectivity and should run as part of the standard test suite.
 */
class WebAuthIntegrationTest {

    // Test anchor configuration (from stellar.toml)
    private val testAnchorDomain = "testanchor.stellar.org"
    private val network = Network.TESTNET

    // Client domain test configuration
    // This is a test domain with a known signing key for testing client domain verification
    // The signing key matches the SIGNING_KEY in server-signer.replit.app's stellar.toml
    private val testClientDomain = "server-signer.replit.app"
    private val testClientDomainSignerSeed = "SBRSOOURG2E24VGDR6NKZJMBOSOHVT6GV7EECUR3ZBE7LGSSVYN5VMOG"

    // Remote signing service configuration
    private val remoteSigningServiceUrl = "https://server-signer.replit.app/sign"
    private val remoteSigningServiceToken = "Bearer 987654321"

    /**
     * Tests basic SEP-10 authentication flow without client domain verification.
     *
     * This is the most common authentication pattern used by wallets and
     * applications integrating with Stellar anchors.
     *
     * Flow:
     * 1. Initialize WebAuth from testanchor.stellar.org domain (loads stellar.toml)
     * 2. Generate random user keypair
     * 3. Request challenge from server
     * 4. Validate challenge (13 security checks)
     * 5. Sign challenge with user keypair
     * 6. Submit signed challenge to server
     * 7. Receive and validate JWT token
     *
     * Expected Result:
     * - JWT token is returned with valid structure
     * - Token contains 'sub' claim matching user account ID
     * - Token contains 'iss' claim identifying the authentication server
     * - Token contains 'exp' claim indicating expiration time
     * - Token contains 'iat' claim indicating issued-at time
     *
     * Security Validations Performed:
     * - Challenge transaction sequence number is 0 (cannot be submitted to network)
     * - Challenge transaction has valid time bounds
     * - Challenge transaction contains home domain auth operation
     * - Challenge transaction has exactly 1 signature (server's)
     * - Server signature is valid and matches SIGNING_KEY from stellar.toml
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
     * Tests client domain verification with local keypair signing.
     *
     * Client domain verification allows wallets and applications to prove
     * ownership of a domain to the authentication server. This is useful for:
     * - Regulated services requiring domain verification
     * - Multi-tenant authentication servers
     * - Premium service tiers based on verified domains
     * - Compliance with regional regulations requiring domain identification
     *
     * Flow:
     * 1. Initialize WebAuth from testanchor.stellar.org
     * 2. Generate random user keypair
     * 3. Create client domain keypair from test seed
     * 4. Request challenge with client_domain parameter
     * 5. Validate challenge (includes client_domain operation)
     * 6. Sign challenge with both user keypair AND client domain keypair
     * 7. Submit signed challenge
     * 8. Receive JWT token with client domain verification
     *
     * Challenge Structure with Client Domain:
     * - Operation 0: ManageData("{serverHomeDomain} auth", random_value)
     *   - Source: client account
     * - Operation 1: ManageData("web_auth_domain", "{authEndpointHost}")
     *   - Source: server account
     * - Operation 2: ManageData("client_domain", "{clientDomain}")
     *   - Source: client domain signing account (from client domain's stellar.toml SIGNING_KEY)
     *
     * Security Requirements:
     * - The client domain keypair must match the SIGNING_KEY in the
     *   client domain's stellar.toml file
     * - The server fetches the client domain's stellar.toml to verify the signing key
     * - This proves the client application has access to the private key
     *   corresponding to the public key published in their stellar.toml
     *
     * Test Configuration:
     * - Client Domain: server-signer.replit.app
     * - Client Domain SIGNING_KEY: GBUTDNISXHXBMZE5I4U5INJTY376S5EW2AF4SQA2SWBXUXJY3OIZQHMV
     * - Client Domain Secret Seed: SBRSOOURG2E24VGDR6NKZJMBOSOHVT6GV7EECUR3ZBE7LGSSVYN5VMOG
     */
    @Test
    fun testClientDomainAuthentication() = runTest {
        // Initialize WebAuth from domain
        val webAuth = WebAuth.fromDomain(
            domain = testAnchorDomain,
            network = network
        )

        // Generate random user keypair
        val userKeyPair = KeyPair.random()
        val userAccountId = userKeyPair.getAccountId()

        // Create client domain signing keypair
        val clientDomainKeyPair = KeyPair.fromSecretSeed(testClientDomainSignerSeed)

        // Execute authentication with client domain verification
        val authToken = webAuth.jwtToken(
            clientAccountId = userAccountId,
            signers = listOf(userKeyPair),
            clientDomain = testClientDomain,
            clientDomainKeyPair = clientDomainKeyPair
        )

        // Validate token was received
        assertNotNull(authToken.token, "JWT token should be present")
        assertTrue(authToken.token.isNotEmpty(), "JWT token should not be empty")

        // Validate token claims
        assertNotNull(authToken.account, "Token should contain account claim")
        assertTrue(authToken.account == userAccountId, "Token account should match user account")

        // Validate token expiration
        assertNotNull(authToken.exp, "Token should have expiration")
        assertTrue(authToken.exp > 0, "Token expiration should be valid timestamp")

        // Validate token issuer
        assertNotNull(authToken.iss, "Token should have issuer")

        println("Client domain authentication successful:")
        println("  Account: ${authToken.account}")
        println("  Client Domain: $testClientDomain")
        println("  Client Domain Signer: ${clientDomainKeyPair.getAccountId()}")
        println("  Issuer: ${authToken.iss}")
        println("  Expires: ${authToken.exp}")
    }

    /**
     * Tests client domain verification with local signing delegate.
     *
     * This test demonstrates the HSM simulation pattern where the WALLET COMPANY'S
     * client domain signing key is not directly accessible (e.g., stored in the
     * wallet company's HSM or key management service), but signing can be performed
     * through a local callback/delegate function.
     *
     * Real-World Use Cases:
     * - Enterprise wallets with wallet company's HSM-protected domain signing keys
     * - Wallet backend services using hardware security modules
     * - Desktop wallet applications with HSM integration for domain key signing
     * - Development/testing environments simulating HSM behavior
     * - Airgapped signing setups where keys never leave secure hardware
     *
     * Flow:
     * 1. Initialize WebAuth from testanchor.stellar.org
     * 2. Generate random user keypair
     * 3. Create local signing delegate that simulates HSM signing:
     *    - Receives transaction XDR (not the private key)
     *    - Parses transaction and signs it
     *    - Returns signed transaction XDR
     * 4. Execute authentication with client domain delegate
     * 5. Validate JWT token is returned successfully
     *
     * Security Benefits:
     * - Private key never leaves secure storage (HSM/enclave)
     * - Delegate can inspect transaction before signing
     * - Can implement additional security policies in delegate
     * - Network replay protection via network passphrase
     * - Compatible with hardware security modules that can't export keys
     *
     * Delegate Behavior:
     * - Receives: Base64-encoded transaction envelope XDR
     * - Returns: Base64-encoded signed transaction envelope XDR
     * - The delegate signs the transaction and returns the complete signed transaction
     *
     * Test Configuration:
     * - Uses same client domain and signing key as testClientDomainAuthentication
     * - Demonstrates pattern applicable to HSMs, secure enclaves, and custody services
     */
    @Test
    fun testLocalClientDomainSigningDelegate() = runTest {
        // Initialize WebAuth from domain
        val webAuth = WebAuth.fromDomain(
            domain = testAnchorDomain,
            network = network
        )

        // Generate random user keypair
        val userKeyPair = KeyPair.random()
        val userAccountId = userKeyPair.getAccountId()

        // Create local signing delegate that simulates HSM behavior
        // In production, this would call HSM APIs, secure enclave, or key management service
        val localSigningDelegate = ClientDomainSigningDelegate { transactionXdr ->
            // Parse transaction
            val tx = AbstractTransaction.fromEnvelopeXdr(transactionXdr, network) as Transaction

            // Sign with client domain key (in HSM, this would be HSM-protected key)
            val clientDomainKeyPair = KeyPair.fromSecretSeed(testClientDomainSignerSeed)
            tx.sign(clientDomainKeyPair)

            // Return signed transaction
            tx.toEnvelopeXdrBase64()
        }

        // Execute authentication with local signing delegate
        val authToken = webAuth.jwtToken(
            clientAccountId = userAccountId,
            signers = listOf(userKeyPair),
            clientDomain = testClientDomain,
            clientDomainSigningDelegate = localSigningDelegate
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

        println("Local client domain signing delegate authentication successful:")
        println("  Account: ${authToken.account}")
        println("  Client Domain: $testClientDomain")
        println("  Signing Method: Local HSM Simulation Delegate")
        println("  Issuer: ${authToken.iss}")
        println("  Expires: ${authToken.exp}")
    }

    /**
     * Tests client domain verification with remote signing service.
     *
     * This test demonstrates integration with external signing services where
     * the client domain signing key is managed by a third-party service that
     * provides transaction signing via HTTP API.
     *
     * Real-World Use Cases:
     * - Wallet company's backend signing infrastructure (using HSMs, cloud KMS services)
     * - Cloud-based key management (AWS KMS, Google Cloud KMS, Azure Key Vault)
     * - Multi-Party Computation (MPC) signing services
     * - Enterprise signing services with compliance/audit requirements
     * - Distributed signing infrastructure for high availability
     * - Signing services with additional security policies/approvals
     *
     * Flow:
     * 1. Initialize WebAuth from testanchor.stellar.org
     * 2. Generate random user keypair
     * 3. Create remote signing delegate that:
     *    - Receives transaction XDR from SDK
     *    - Makes HTTP POST request to remote signing service
     *    - Passes transaction XDR and network passphrase
     *    - Includes authentication token in request headers
     *    - Receives signed transaction XDR from service
     *    - Returns signed transaction XDR to SDK
     * 4. Execute authentication with remote signing delegate
     * 5. Validate JWT token is returned successfully
     *
     * Remote Signing Service Details:
     * - Service URL: https://server-signer.replit.app/sign
     * - Service Source: https://replit.com/@crogobete/ServerSigner#main.py
     * - Authentication: Bearer token (987654321)
     * - Request Format: JSON with 'transaction' and 'network_passphrase' fields
     * - Response Format: JSON with 'transaction' field containing signed XDR
     * - Service maintains the client domain signing key securely
     * - Service signs the transaction and returns the complete signed envelope
     *
     * Security Considerations:
     * - Remote service must be trusted (manages signing keys)
     * - Communication should use HTTPS for transport security
     * - Authentication tokens should be securely stored
     * - Service should validate transaction contents before signing
     * - Implement timeout handling for network resilience
     * - Consider retry logic for production systems
     * - Monitor service availability and response times
     *
     * Error Handling:
     * - Network timeouts (30 second timeout configured)
     * - Service unavailability (connection refused, DNS failures)
     * - Invalid responses (malformed JSON, missing fields)
     * - Authentication failures (invalid tokens)
     * - Signing failures (service-side validation failures)
     *
     * Implementation Note:
     * The remote service returns a full signed transaction XDR which is returned
     * directly to the SDK. This is the ecosystem standard pattern used by all
     * Stellar SDKs (Flutter, PHP, iOS).
     *
     * Test Configuration:
     * - Uses live remote signing service at server-signer.replit.app
     * - Service signs with the same key as SIGNING_KEY in stellar.toml
     * - Demonstrates production-ready pattern for external signing integration
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
     * Request format for remote signing service.
     */
    @Serializable
    private data class RemoteSigningRequest(
        val transaction: String,
        val network_passphrase: String
    )

    /**
     * Response format from remote signing service.
     */
    @Serializable
    private data class RemoteSigningResponse(
        val transaction: String
    )
}
