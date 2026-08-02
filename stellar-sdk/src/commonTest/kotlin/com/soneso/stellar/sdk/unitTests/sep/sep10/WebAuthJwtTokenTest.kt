// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep10

import com.soneso.stellar.sdk.sep.sep10.*
import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.sep.sep10.exceptions.ChallengeRequestException
import com.soneso.stellar.sdk.sep.sep10.exceptions.GenericChallengeValidationException
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
        includeWebAuthDomain: Boolean = false,
        clientDomainOpSource: String? = null,
        txSourceAccountId: String? = null
    ): String {
        // Create server keypair from secret seed
        val serverKeyPair = KeyPair.fromSecretSeed(serverSecretSeed)
        // Create a SEP-10 challenge transaction. The transaction source defaults to the signing
        // server; txSourceAccountId sets it independently so a test can vary source and
        // signature separately.
        val sourceAccount = Account(txSourceAccountId ?: serverKeyPair.getAccountId(), -1L)

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
            clientDomainOp.sourceAccount = clientDomainOpSource ?: serverKeyPair.getAccountId()
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

        // Challenge sourced by the expected server but signed by a different key, so the
        // signature check is what rejects it
        val wrongServerSeed = wrongServerKeyPair.getSecretSeed()?.concatToString() ?: ""
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = wrongServerSeed,  // Wrong signature
            homeDomain = testHomeDomain,
            txSourceAccountId = serverKeyPair.getAccountId()
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

    /**
     * Creates a mock HTTP client that additionally serves a client domain stellar.toml.
     */
    private fun createMockClientWithClientDomainToml(
        challengeXdr: String,
        clientDomainToml: String,
        clientDomainTomlStatus: HttpStatusCode = HttpStatusCode.OK
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath.contains(".well-known/stellar.toml") -> respond(
                    content = clientDomainToml,
                    status = clientDomainTomlStatus,
                    headers = headersOf(HttpHeaders.ContentType, "text/plain")
                )
                request.method == HttpMethod.Get -> respond(
                    content = """{"transaction": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> respond(
                    content = """{"token": "$sampleJwt"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
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
    fun testJwtTokenClientDomainSigningKeyResolvedFromToml() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientDomainKeyPair = KeyPair.random()

        // The client_domain operation is sourced by the account published as the
        // client domain's SIGNING_KEY, which the SDK fetches from its stellar.toml
        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain,
            includeClientDomain = true,
            clientDomainOpSource = clientDomainKeyPair.getAccountId()
        )

        var delegateReceivedXdr: String? = null
        val delegate = ClientDomainSigningDelegate { transactionXdr ->
            delegateReceivedXdr = transactionXdr
            val tx = AbstractTransaction.fromEnvelopeXdr(transactionXdr, network) as Transaction
            tx.sign(clientDomainKeyPair)
            tx.toEnvelopeXdrBase64()
        }

        val mockClient = createMockClientWithClientDomainToml(
            challengeXdr = challengeXdr,
            clientDomainToml = """SIGNING_KEY="${clientDomainKeyPair.getAccountId()}""""
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
            clientDomain = "wallet.example.com",
            clientDomainSigningDelegate = delegate
        )

        assertEquals(sampleJwt, authToken.token)
        assertNotNull(delegateReceivedXdr)
    }

    @Test
    fun testJwtTokenClientDomainTomlWithoutSigningKeyRejected() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientDomainKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain,
            includeClientDomain = true,
            clientDomainOpSource = clientDomainKeyPair.getAccountId()
        )

        // stellar.toml exists but publishes no SIGNING_KEY, so the client_domain
        // operation source cannot be verified
        val mockClient = createMockClientWithClientDomainToml(
            challengeXdr = challengeXdr,
            clientDomainToml = """WEB_AUTH_ENDPOINT="https://wallet.example.com/auth""""
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<GenericChallengeValidationException> {
            webAuth.jwtToken(
                clientAccountId = clientKeyPair.getAccountId(),
                signers = listOf(clientKeyPair),
                clientDomain = "wallet.example.com",
                clientDomainKeyPair = clientDomainKeyPair
            )
        }

        assertEquals(
            "SIGNING_KEY not found in stellar.toml for client domain: wallet.example.com",
            exception.message,
            "A readable stellar.toml without SIGNING_KEY must report the missing key, not a load failure"
        )
    }

    @Test
    fun testJwtTokenClientDomainTomlLoadFailureRejected() = runTest {
        val clientKeyPair = KeyPair.random()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val clientDomainKeyPair = KeyPair.random()

        val challengeXdr = createValidChallengeXdr(
            clientKeyPair = clientKeyPair,
            serverSecretSeed = testServerSecretSeed,
            homeDomain = testHomeDomain,
            includeClientDomain = true,
            clientDomainOpSource = clientDomainKeyPair.getAccountId()
        )

        // The client domain publishes no stellar.toml at all
        val mockClient = createMockClientWithClientDomainToml(
            challengeXdr = challengeXdr,
            clientDomainToml = "Not Found",
            clientDomainTomlStatus = HttpStatusCode.NotFound
        )

        val webAuth = WebAuth(
            authEndpoint = testAuthEndpoint,
            network = network,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            httpClient = mockClient
        )

        val exception = assertFailsWith<GenericChallengeValidationException> {
            webAuth.jwtToken(
                clientAccountId = clientKeyPair.getAccountId(),
                signers = listOf(clientKeyPair),
                clientDomain = "wallet.example.com",
                clientDomainKeyPair = clientDomainKeyPair
            )
        }

        assertTrue(
            exception.message?.startsWith("Failed to load stellar.toml for client domain wallet.example.com") == true,
            "An unreachable stellar.toml must report the load failure, got: ${exception.message}"
        )
        assertTrue(
            exception.message?.contains("SIGNING_KEY") != true,
            "A load failure must not be described as a missing SIGNING_KEY, got: ${exception.message}"
        )
    }
}
