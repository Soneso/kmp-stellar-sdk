// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep10.exceptions.ChallengeRequestException
import com.soneso.stellar.sdk.sep.sep10.exceptions.InvalidSignatureException
import com.soneso.stellar.sdk.sep.sep10.exceptions.TokenSubmissionException
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*
import kotlin.time.ExperimentalTime

/**
 * End-to-end tests for WebAuth.jwtToken() orchestration method.
 * Tests the complete SEP-10 authentication flow from challenge request to JWT token receipt.
 */
class WebAuthJwtTokenTest {

    // Server keypair with secret seed for signing transactions
    private val testServerSecretSeed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
    private val testHomeDomain = "testanchor.stellar.org"
    private val testAuthEndpoint = "https://testanchor.stellar.org/auth"
    private val network = Network.TESTNET

    // Sample JWT token with proper structure
    // Payload: {"sub":"GACLIENTACCOUNTID23VLHEZ34PAXPWLUSDGEJIKNGYHKSPAYJ4BYOEKZTAOAQYU","iss":"testanchor.stellar.org","iat":1709598600,"exp":1709602200}
    private val sampleJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJHQUNMSUVOVEFDQ09VTlRJRDIzVkxIRVozNFBBWFBXTFVTREdFSklLTkdZSEtTUEFZSjRCWU9FS1pUQU9BUVlVIiwiaXNzIjoidGVzdGFuY2hvci5zdGVsbGFyLm9yZyIsImlhdCI6MTcwOTU5ODYwMCwiZXhwIjoxNzA5NjAyMjAwfQ.test_signature"

    /**
     * Creates a valid challenge transaction XDR for testing.
     * This mimics what a real SEP-10 server would return.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun createValidChallengeXdr(
        clientKeyPair: KeyPair,
        serverSecretSeed: String,
        homeDomain: String,
        memo: Long? = null,
        includeClientDomain: Boolean = false,
        includeWebAuthDomain: Boolean = false
    ): String {
        // Create server keypair from secret seed
        val serverKeyPair = KeyPair.fromSecretSeed(serverSecretSeed)
        // Create a SEP-10 challenge transaction
        val sourceAccount = Account(serverKeyPair.getAccountId(), -1L)

        val builder = TransactionBuilder(sourceAccount, network)
            .setBaseFee(100)
            .addTimeBounds(
                TimeBounds(
                    minTime = (kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000) - 300,
                    maxTime = (kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000) + 600
                )
            )

        // Add memo if provided
        if (memo != null) {
            builder.addMemo(MemoId(memo.toULong()))
        }

        // First operation: home domain auth (source: client account)
        val authOp = ManageDataOperation(
            name = "$homeDomain auth",
            value = "test_value".encodeToByteArray()
        )
        authOp.sourceAccount = clientKeyPair.getAccountId()
        builder.addOperation(authOp)

        // Optional: client_domain operation (source: client domain account)
        if (includeClientDomain) {
            val clientDomainOp = ManageDataOperation(
                name = "client_domain",
                value = "wallet.example.com".encodeToByteArray()
            )
            clientDomainOp.sourceAccount = serverKeyPair.getAccountId()
            builder.addOperation(clientDomainOp)
        }

        // Optional: web_auth_domain operation (source: server account)
        if (includeWebAuthDomain) {
            val webAuthDomainOp = ManageDataOperation(
                name = "web_auth_domain",
                value = "testanchor.stellar.org".encodeToByteArray()
            )
            webAuthDomainOp.sourceAccount = serverKeyPair.getAccountId()
            builder.addOperation(webAuthDomainOp)
        }

        val transaction = builder.build()

        // Sign with server keypair
        transaction.sign(serverKeyPair)

        return transaction.toEnvelopeXdrBase64()
    }

    /**
     * Creates a mock HTTP client that simulates a complete SEP-10 flow.
     */
    private fun createMockClientForJwtToken(
        challengeXdr: String,
        jwtToken: String = sampleJwt,
        getChallengeStatusCode: HttpStatusCode = HttpStatusCode.OK,
        postTokenStatusCode: HttpStatusCode = HttpStatusCode.OK,
        getChallengeErrorMessage: String? = null,
        postTokenErrorMessage: String? = null
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/auth" -> {
                    when (request.method) {
                        HttpMethod.Get -> {
                            if (getChallengeStatusCode == HttpStatusCode.OK && getChallengeErrorMessage == null) {
                                respond(
                                    content = """{"transaction": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
                                    status = getChallengeStatusCode,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            } else {
                                respond(
                                    content = getChallengeErrorMessage ?: """{"error": "Challenge request failed"}""",
                                    status = getChallengeStatusCode,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            }
                        }
                        HttpMethod.Post -> {
                            if (postTokenStatusCode == HttpStatusCode.OK && postTokenErrorMessage == null) {
                                respond(
                                    content = """{"token": "$jwtToken"}""",
                                    status = postTokenStatusCode,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            } else {
                                respond(
                                    content = postTokenErrorMessage ?: """{"error": "Token submission failed"}""",
                                    status = postTokenStatusCode,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            }
                        }
                        else -> error("Unhandled HTTP method: ${request.method}")
                    }
                }
                else -> error("Unhandled path: ${request.url.encodedPath}")
            }
        }

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    @Test
    fun testJwtTokenBasicSuccess() = runTest {
        // Create client keypair
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        // Create valid challenge XDR
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain
        )

        // Create mock client
        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr
        )

        // Create WebAuth instance
        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        // Call jwtToken() - the high-level API
        val authToken = webAuth.jwtToken(
            clientAccountId = clientKeyPair.getAccountId(),
            signers = listOf(clientKeyPair)
        )

        // Verify token was returned
        assertNotNull(authToken)
        assertEquals(sampleJwt, authToken.token)
        assertEquals("testanchor.stellar.org", authToken.iss)
        assertNotNull(authToken.exp)
    }

    @Test
    fun testJwtTokenWithMemo() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val memo = 12345L

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain,
            memo = memo
        )

        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val authToken = webAuth.jwtToken(
            clientAccountId = clientKeyPair.getAccountId(),
            signers = listOf(clientKeyPair),
            memo = memo
        )

        assertNotNull(authToken)
        assertEquals(sampleJwt, authToken.token)
    }

    @Test
    fun testJwtTokenWithMuxedAccount() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        // Create a muxed account from the client keypair
        val muxedAccount = MuxedAccount(clientKeyPair.getAccountId(), 9876543210UL)
        val muxedAccountId = muxedAccount.address  // Use .address property to get M... format

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,  // Use underlying G... account for operation source
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain
        )

        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val authToken = webAuth.jwtToken(
            clientAccountId = muxedAccountId,  // Use M... address
            signers = listOf(clientKeyPair)
        )

        assertNotNull(authToken)
        assertEquals(sampleJwt, authToken.token)
    }

    @Test
    fun testJwtTokenWithHomeDomain() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val customHomeDomain = "custom.stellar.org"

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain  // Server still uses its home domain
        )

        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val authToken = webAuth.jwtToken(
            clientAccountId = clientKeyPair.getAccountId(),
            signers = listOf(clientKeyPair),
            homeDomain = customHomeDomain
        )

        assertNotNull(authToken)
        assertEquals(sampleJwt, authToken.token)
    }

    @Test
    fun testJwtTokenMultiSignature() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val signer2 = KeyPair.random()
        val signer3 = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain
        )

        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        // Provide multiple signers
        val authToken = webAuth.jwtToken(
            clientAccountId = clientKeyPair.getAccountId(),
            signers = listOf(clientKeyPair, signer2, signer3)
        )

        assertNotNull(authToken)
        assertEquals(sampleJwt, authToken.token)
    }

    @Test
    fun testJwtTokenChallengeRequestFails() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain
        )

        // Create mock that returns 401 on GET
        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr,
            getChallengeStatusCode = HttpStatusCode.Unauthorized,
            getChallengeErrorMessage = """{"error": "Account not allowed"}"""
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        // Should throw ChallengeRequestException
        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.jwtToken(
                clientAccountId = clientKeyPair.getAccountId(),
                signers = listOf(clientKeyPair)
            )
        }

        assertTrue(exception.errorMessage?.contains("Account not allowed") == true || exception.statusCode == 401)
    }

    @Test
    fun testJwtTokenValidationFails() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val wrongServerKeyPair = KeyPair.random()  // Different server keypair

        // Create challenge signed by wrong server
        val wrongServerSeed = wrongServerKeyPair.getSecretSeed()?.concatToString() ?: ""
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = wrongServerSeed,  // Wrong signature
            homeDomain = testHomeDomain
        )

        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),  // Expected server key
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        // Should throw InvalidSignatureException during validation
        val exception = assertFailsWith<InvalidSignatureException> {
            webAuth.jwtToken(
                clientAccountId = clientKeyPair.getAccountId(),
                signers = listOf(clientKeyPair)
            )
        }

        assertTrue(exception.message?.contains("signature") == true || exception.message?.contains("invalid") == true)
    }

    @Test
    fun testJwtTokenSubmissionFails() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain
        )

        // Mock returns success for GET but fails for POST
        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr,
            postTokenStatusCode = HttpStatusCode.Unauthorized,
            postTokenErrorMessage = """{"error": "Invalid signatures"}"""
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        // Should throw TokenSubmissionException
        val exception = assertFailsWith<TokenSubmissionException> {
            webAuth.jwtToken(
                clientAccountId = clientKeyPair.getAccountId(),
                signers = listOf(clientKeyPair)
            )
        }

        assertTrue(exception.message?.contains("401") == true || exception.message?.contains("Unauthorized") == true)
    }

    @Test
    fun testJwtTokenEmptySigners() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain
        )

        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        // Should throw IllegalArgumentException for empty signers
        val exception = assertFailsWith<IllegalArgumentException> {
            webAuth.jwtToken(
                clientAccountId = clientKeyPair.getAccountId(),
                signers = emptyList()  // Empty signers list
            )
        }

        assertTrue(exception.message?.contains("empty") == true || exception.message?.contains("cannot be empty") == true)
    }

    @Test
    fun testJwtTokenWithInvalidAccountId() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain
        )

        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        // Should throw IllegalArgumentException for invalid account ID format
        val exception = assertFailsWith<IllegalArgumentException> {
            webAuth.jwtToken(
                clientAccountId = "INVALID_ACCOUNT_ID",
                signers = listOf(clientKeyPair)
            )
        }

        assertTrue(exception.message?.contains("Invalid") == true || exception.message?.contains("must be") == true)
    }

    @Test
    fun testJwtTokenNetworkError() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        // Create mock that throws network exception
        val mockEngine = MockEngine { request ->
            throw Exception("Network connection failed")
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        // Should throw ChallengeRequestException with network error
        val exception = assertFailsWith<ChallengeRequestException> {
            webAuth.jwtToken(
                clientAccountId = clientKeyPair.getAccountId(),
                signers = listOf(clientKeyPair)
            )
        }

        assertTrue(exception.errorMessage?.contains("Network error") == true || exception.statusCode == 0)
    }

    @Test
    fun testJwtTokenSuccessfulFlow() = runTest {
        // Integration-style test that verifies the complete flow
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain,
            includeWebAuthDomain = true
        )

        val mockClient = createMockClientForJwtToken(
            challengeXdr = challengeXdr
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        // Complete flow: request -> validate -> sign -> submit
        val authToken = webAuth.jwtToken(
            clientAccountId = clientKeyPair.getAccountId(),
            signers = listOf(clientKeyPair)
        )

        // Verify all token fields
        assertNotNull(authToken)
        assertEquals(sampleJwt, authToken.token)
        assertEquals("testanchor.stellar.org", authToken.iss)
        assertNotNull(authToken.exp)
        assertNotNull(authToken.iat)
        assertEquals(1709602200L, authToken.exp)
        assertEquals(1709598600L, authToken.iat)
    }
}
