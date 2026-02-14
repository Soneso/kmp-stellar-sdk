// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep45

import com.soneso.stellar.sdk.sep.sep45.*
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.sep.sep45.exceptions.*
import com.soneso.stellar.sdk.xdr.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*

/**
 * Unit tests for SEP-45 WebAuthForContracts service.
 *
 * Tests cover:
 * - Success cases (complete auth flow, client domain, multiple signers)
 * - Challenge request tests (success, errors, timeouts)
 * - Token submission tests (success, errors, timeouts)
 * - Factory tests (fromDomain success and failures)
 * - Constructor validation tests
 * - Input validation tests
 * - Challenge validation tests (all security checks)
 */
class WebAuthForContractsTest {
    private var nonceCounter = 0L

    companion object {
        private const val DOMAIN = "example.stellar.org"
        private const val AUTH_SERVER = "https://auth.example.stellar.org"
        private const val SERVER_ACCOUNT_ID = "GBWMCCC3NHSKLAOJDBKKYW7SSH2PFTTNVFKWSGLWGDLEBKLOVP5JLBBP"
        private const val SERVER_SECRET_SEED = "SAWDHXQG6ROJSU4QGCW7NSTYFHPTPIVC2NC7QKVTO7PZCSO2WEBGM54W"
        private const val CLIENT_CONTRACT_ID = "CDZJIDQW5WTPAZ64PGIJGVEIDNK72LL3LKUZWG3G6GWXYQKI2JNIVFNV"
        private const val WEB_AUTH_CONTRACT_ID = "CA7A3N2BB35XMTFPAYWVZEF4TEYXW7DAEWDXJNQGUPR5SWSM2UVZCJM2"
        // JWT token with sub=CDZJIDQW5WTPAZ64PGIJGVEIDNK72LL3LKUZWG3G6GWXYQKI2JNIVFNV, iss=example.stellar.org
        private const val SUCCESS_JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJDRFpKSURRVzVXVFBBWjY0UEdJSkdWRUlETks3MkxMM0xLVVpXRzNHNkdXWFlRS0kySk5JVkZOViIsImlzcyI6ImV4YW1wbGUuc3RlbGxhci5vcmciLCJpYXQiOjE3Mzc3NjAwMDAsImV4cCI6MTczNzc2MzYwMH0.test"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ============================================================================
    // Helper Functions - Build Authorization Entries
    // ============================================================================

    /**
     * Builds the args map as SCValXdr for the web_auth_verify function.
     */
    private fun buildArgsMap(
        account: String,
        homeDomain: String,
        webAuthDomain: String,
        webAuthDomainAccount: String,
        nonce: String,
        clientDomain: String? = null,
        clientDomainAccount: String? = null
    ): SCValXdr {
        val mapEntries = mutableListOf<SCMapEntryXdr>()

        mapEntries.add(SCMapEntryXdr(
            key = Scv.toSymbol("account"),
            `val` = Scv.toString(account)
        ))
        mapEntries.add(SCMapEntryXdr(
            key = Scv.toSymbol("home_domain"),
            `val` = Scv.toString(homeDomain)
        ))
        mapEntries.add(SCMapEntryXdr(
            key = Scv.toSymbol("web_auth_domain"),
            `val` = Scv.toString(webAuthDomain)
        ))
        mapEntries.add(SCMapEntryXdr(
            key = Scv.toSymbol("web_auth_domain_account"),
            `val` = Scv.toString(webAuthDomainAccount)
        ))
        mapEntries.add(SCMapEntryXdr(
            key = Scv.toSymbol("nonce"),
            `val` = Scv.toString(nonce)
        ))

        if (clientDomain != null) {
            mapEntries.add(SCMapEntryXdr(
                key = Scv.toSymbol("client_domain"),
                `val` = Scv.toString(clientDomain)
            ))
        }

        if (clientDomainAccount != null) {
            mapEntries.add(SCMapEntryXdr(
                key = Scv.toSymbol("client_domain_account"),
                `val` = Scv.toString(clientDomainAccount)
            ))
        }

        return SCValXdr.Map(SCMapXdr(mapEntries))
    }

    /**
     * Converts address string to SCAddressXdr.
     */
    private fun addressToScAddress(address: String): SCAddressXdr {
        return when {
            address.startsWith("C") -> {
                val contractBytes = StrKey.decodeContract(address)
                SCAddressXdr.ContractId(ContractIDXdr(HashXdr(contractBytes)))
            }
            address.startsWith("G") -> {
                val publicKey = StrKey.decodeEd25519PublicKey(address)
                val accountId = AccountIDXdr(PublicKeyXdr.Ed25519(Uint256Xdr(publicKey)))
                SCAddressXdr.AccountId(accountId)
            }
            else -> throw IllegalArgumentException("Invalid address: $address")
        }
    }

    /**
     * Builds a single authorization entry with empty signature.
     */
    private fun buildAuthEntry(
        credentialsAddress: String,
        contractId: String,
        functionName: String,
        argsMap: SCValXdr,
        nonce: Long,
        expirationLedger: Long,
        subInvocations: List<SorobanAuthorizedInvocationXdr> = emptyList()
    ): SorobanAuthorizationEntryXdr {
        val address = addressToScAddress(credentialsAddress)

        val credentials = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = address,
                nonce = Int64Xdr(nonce),
                signatureExpirationLedger = Uint32Xdr(expirationLedger.toUInt()),
                signature = SCValXdr.Vec(SCVecXdr(emptyList())) // Empty signature vector
            )
        )

        val contractAddress = Address(contractId).toSCAddress()
        val contractFn = InvokeContractArgsXdr(
            contractAddress = contractAddress,
            functionName = SCSymbolXdr(functionName),
            args = listOf(argsMap)
        )

        val function = SorobanAuthorizedFunctionXdr.ContractFn(contractFn)
        val invocation = SorobanAuthorizedInvocationXdr(
            function = function,
            subInvocations = subInvocations
        )

        return SorobanAuthorizationEntryXdr(
            credentials = credentials,
            rootInvocation = invocation
        )
    }

    /**
     * Signs an authorization entry with a keypair.
     */
    private suspend fun signEntry(
        entry: SorobanAuthorizationEntryXdr,
        keyPair: KeyPair,
        network: Network = Network.TESTNET
    ): SorobanAuthorizationEntryXdr {
        val credentials = entry.credentials
        if (credentials !is SorobanCredentialsXdr.Address) {
            return entry
        }

        return Auth.authorizeEntry(
            entry = entry,
            signer = keyPair,
            validUntilLedgerSeq = credentials.value.signatureExpirationLedger.value.toLong(),
            network = network
        )
    }

    /**
     * Encodes authorization entries to base64 XDR.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeAuthEntries(entries: List<SorobanAuthorizationEntryXdr>): String {
        val writer = XdrWriter()
        writer.writeInt(entries.size)
        entries.forEach { it.encode(writer) }
        return Base64.encode(writer.toByteArray())
    }

    /**
     * Builds a valid challenge with server and client entries.
     */
    private suspend fun buildValidChallenge(
        clientAccountId: String,
        homeDomain: String,
        webAuthDomain: String,
        webAuthDomainAccount: String,
        nonce: String,
        clientDomain: String? = null,
        clientDomainAccount: String? = null,
        signServerEntry: Boolean = true
    ): String {
        val serverKeyPair = KeyPair.fromSecretSeed(SERVER_SECRET_SEED)
        val entries = mutableListOf<SorobanAuthorizationEntryXdr>()

        val argsMap = buildArgsMap(
            account = clientAccountId,
            homeDomain = homeDomain,
            webAuthDomain = webAuthDomain,
            webAuthDomainAccount = webAuthDomainAccount,
            nonce = nonce,
            clientDomain = clientDomain,
            clientDomainAccount = clientDomainAccount
        )

        // Server entry
        var serverEntry = buildAuthEntry(
            credentialsAddress = SERVER_ACCOUNT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12345L,
            expirationLedger = 1000000L
        )
        if (signServerEntry) {
            serverEntry = signEntry(serverEntry, serverKeyPair)
        }
        entries.add(serverEntry)

        // Client entry
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAccountId,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12346L,
            expirationLedger = 1000000L
        )
        entries.add(clientEntry)

        // Client domain entry (if provided)
        if (clientDomainAccount != null) {
            val clientDomainEntry = buildAuthEntry(
                credentialsAddress = clientDomainAccount,
                contractId = WEB_AUTH_CONTRACT_ID,
                functionName = "web_auth_verify",
                argsMap = argsMap,
                nonce = 12347L,
                expirationLedger = 1000000L
            )
            entries.add(clientDomainEntry)
        }

        return encodeAuthEntries(entries)
    }

    /**
     * Creates a mock HTTP client with specified responses.
     */
    private fun createMockClient(
        challengeResponse: String? = null,
        challengeStatusCode: HttpStatusCode = HttpStatusCode.OK,
        tokenResponse: String? = null,
        tokenStatusCode: HttpStatusCode = HttpStatusCode.OK,
        stellarTomlResponse: String? = null
    ): HttpClient {
        var requestCount = 0

        val mockEngine = MockEngine { request ->
            requestCount++

            when {
                // stellar.toml request
                request.url.encodedPath.contains(".well-known/stellar.toml") -> {
                    if (stellarTomlResponse != null) {
                        respond(
                            content = stellarTomlResponse,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "text/plain")
                        )
                    } else {
                        respond(
                            content = "",
                            status = HttpStatusCode.NotFound,
                            headers = headersOf(HttpHeaders.ContentType, "text/plain")
                        )
                    }
                }
                // Challenge request (GET)
                request.method == HttpMethod.Get -> {
                    respond(
                        content = challengeResponse ?: "",
                        status = challengeStatusCode,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                // Token request (POST)
                request.method == HttpMethod.Post -> {
                    respond(
                        content = tokenResponse ?: "",
                        status = tokenStatusCode,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> {
                    respond(
                        content = """{"error": "Not found"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(this@WebAuthForContractsTest.json)
            }
        }
    }

    // ============================================================================
    // Success Cases
    // ============================================================================

    @Test
    fun testDefaultSuccess() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
            tokenResponse = """{"token": "$SUCCESS_JWT_TOKEN"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val clientSigner = KeyPair.random()
        val token = webAuth.jwtToken(
            clientAccountId = CLIENT_CONTRACT_ID,
            signers = listOf(clientSigner),
            homeDomain = DOMAIN,
            signatureExpirationLedger = 1000000L
        )

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
    }

    @Test
    fun testDefaultHomeDomainSuccess() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
            tokenResponse = """{"token": "$SUCCESS_JWT_TOKEN"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val clientSigner = KeyPair.random()
        // Not passing homeDomain - should default to server home domain
        val token = webAuth.jwtToken(
            clientAccountId = CLIENT_CONTRACT_ID,
            signers = listOf(clientSigner),
            signatureExpirationLedger = 1000000L
        )

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
    }

    @Test
    fun testMultipleSignersSuccess() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
            tokenResponse = """{"token": "$SUCCESS_JWT_TOKEN"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val signer1 = KeyPair.random()
        val signer2 = KeyPair.random()
        val signer3 = KeyPair.random()
        val token = webAuth.jwtToken(
            clientAccountId = CLIENT_CONTRACT_ID,
            signers = listOf(signer1, signer2, signer3),
            homeDomain = DOMAIN,
            signatureExpirationLedger = 1000000L
        )

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
    }

    @Test
    fun testEmptySignersSuccess() = runTest {
        // For contracts that don't require signatures in __check_auth
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
            tokenResponse = """{"token": "$SUCCESS_JWT_TOKEN"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val token = webAuth.jwtToken(
            clientAccountId = CLIENT_CONTRACT_ID,
            signers = emptyList(),
            homeDomain = DOMAIN
        )

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
    }

    @Test
    fun testClientDomainWithKeyPairSuccess() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val clientDomainKeyPair = KeyPair.random()
        val clientDomainAccount = clientDomainKeyPair.getAccountId()
        val clientDomain = "client.example.com"

        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce,
            clientDomain = clientDomain,
            clientDomainAccount = clientDomainAccount
        )

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
            tokenResponse = """{"token": "$SUCCESS_JWT_TOKEN"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val clientSigner = KeyPair.random()
        val token = webAuth.jwtToken(
            clientAccountId = CLIENT_CONTRACT_ID,
            signers = listOf(clientSigner),
            homeDomain = DOMAIN,
            clientDomain = clientDomain,
            clientDomainAccountKeyPair = clientDomainKeyPair,
            signatureExpirationLedger = 1000000L
        )

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
    }

    @Test
    fun testClientDomainWithDelegateSuccess() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val clientDomainKeyPair = KeyPair.random()
        val clientDomainAccount = clientDomainKeyPair.getAccountId()
        val clientDomain = "client.example.com"

        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce,
            clientDomain = clientDomain,
            clientDomainAccount = clientDomainAccount
        )

        var delegateInvoked = false
        var requestCount = 0

        val mockEngine = MockEngine { request ->
            requestCount++

            when {
                request.url.encodedPath.contains(".well-known/stellar.toml") -> {
                    respond(
                        content = """SIGNING_KEY="$clientDomainAccount"""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/plain")
                    )
                }
                request.method == HttpMethod.Get -> {
                    respond(
                        content = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.method == HttpMethod.Post -> {
                    respond(
                        content = """{"token": "$SUCCESS_JWT_TOKEN"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> {
                    respond(
                        content = """{"error": "Not found"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(this@WebAuthForContractsTest.json)
            }
        }

        val delegate = Sep45ClientDomainSigningDelegate { entryXdr ->
            delegateInvoked = true
            // Sign the entry locally
            val entry = SorobanAuthorizationEntryXdr.fromXdrBase64(entryXdr)
            val signedEntry = Auth.authorizeEntry(
                entry = entry,
                signer = clientDomainKeyPair,
                validUntilLedgerSeq = 1000000L,
                network = Network.TESTNET
            )
            signedEntry.toXdrBase64()
        }

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val clientSigner = KeyPair.random()
        val token = webAuth.jwtToken(
            clientAccountId = CLIENT_CONTRACT_ID,
            signers = listOf(clientSigner),
            homeDomain = DOMAIN,
            clientDomain = clientDomain,
            clientDomainSigningDelegate = delegate,
            signatureExpirationLedger = 1000000L
        )

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
        assertTrue(delegateInvoked)
    }

    @Test
    fun testFormUrlEncodedSuccess() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        var contentTypeVerified = false
        val mockEngine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get -> {
                    respond(
                        content = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.method == HttpMethod.Post -> {
                    val contentType = request.body.contentType?.toString() ?: ""
                    contentTypeVerified = contentType.contains("application/x-www-form-urlencoded")
                    respond(
                        content = """{"token": "$SUCCESS_JWT_TOKEN"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> {
                    respond(
                        content = """{"error": "Not found"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(this@WebAuthForContractsTest.json)
            }
        }

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )
        webAuth.useFormUrlEncoded = true

        val clientSigner = KeyPair.random()
        val token = webAuth.jwtToken(
            clientAccountId = CLIENT_CONTRACT_ID,
            signers = listOf(clientSigner),
            homeDomain = DOMAIN,
            signatureExpirationLedger = 1000000L
        )

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
        assertTrue(contentTypeVerified, "Content-Type should be application/x-www-form-urlencoded")
    }

    @Test
    fun testJsonContentTypeSuccess() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        var contentTypeVerified = false
        val mockEngine = MockEngine { request ->
            when {
                request.method == HttpMethod.Get -> {
                    respond(
                        content = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.method == HttpMethod.Post -> {
                    val contentType = request.body.contentType?.toString() ?: ""
                    contentTypeVerified = contentType.contains("application/json")
                    respond(
                        content = """{"token": "$SUCCESS_JWT_TOKEN"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> {
                    respond(
                        content = """{"error": "Not found"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(this@WebAuthForContractsTest.json)
            }
        }

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )
        webAuth.useFormUrlEncoded = false

        val clientSigner = KeyPair.random()
        val token = webAuth.jwtToken(
            clientAccountId = CLIENT_CONTRACT_ID,
            signers = listOf(clientSigner),
            homeDomain = DOMAIN,
            signatureExpirationLedger = 1000000L
        )

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
        assertTrue(contentTypeVerified, "Content-Type should be application/json")
    }

    // ============================================================================
    // Challenge Request Tests
    // ============================================================================

    @Test
    fun testGetChallengeSuccess() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val response = webAuth.getChallenge(CLIENT_CONTRACT_ID)

        assertNotNull(response.authorizationEntries)
        assertEquals("Test SDF Network ; September 2015", response.networkPassphrase)
    }

    @Test
    fun testGetChallengeError() = runTest {
        val mockClient = createMockClient(
            challengeResponse = """{"error": "Invalid account"}""",
            challengeStatusCode = HttpStatusCode.BadRequest
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val exception = assertFailsWith<Sep45ChallengeRequestException> {
            webAuth.getChallenge(CLIENT_CONTRACT_ID)
        }

        assertEquals(400, exception.statusCode)
    }

    @Test
    fun testGetChallengeTimeout() = runTest {
        val mockClient = createMockClient(
            challengeResponse = "Gateway Timeout",
            challengeStatusCode = HttpStatusCode.GatewayTimeout
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45TimeoutException> {
            webAuth.getChallenge(CLIENT_CONTRACT_ID)
        }
    }

    @Test
    fun testChallengeWithClientDomain() = runTest {
        val clientDomain = "wallet.example.com"
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce,
            clientDomain = clientDomain
        )

        var clientDomainParam: String? = null
        val mockEngine = MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                clientDomainParam = request.url.parameters["client_domain"]
                respond(
                    content = """{"authorization_entries": "$challengeXdr"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"error": "Not found"}""",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(this@WebAuthForContractsTest.json)
            }
        }

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        webAuth.getChallenge(CLIENT_CONTRACT_ID, clientDomain = clientDomain)

        assertEquals(clientDomain, clientDomainParam)
    }

    // ============================================================================
    // Token Submission Tests
    // ============================================================================

    @Test
    fun testSendSignedChallengeSuccess() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val mockClient = createMockClient(
            tokenResponse = """{"token": "$SUCCESS_JWT_TOKEN"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val entries = webAuth.decodeAuthorizationEntries(challengeXdr)
        val token = webAuth.sendSignedChallenge(entries)

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
    }

    @Test
    fun testSendSignedChallengeError() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val mockClient = createMockClient(
            tokenResponse = """{"error": "Invalid signature"}""",
            tokenStatusCode = HttpStatusCode.BadRequest
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val entries = webAuth.decodeAuthorizationEntries(challengeXdr)

        val exception = assertFailsWith<Sep45TokenSubmissionException> {
            webAuth.sendSignedChallenge(entries)
        }

        assertEquals(400, exception.statusCode)
    }

    @Test
    fun testSendSignedChallengeTimeout() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val mockClient = createMockClient(
            tokenResponse = "Gateway Timeout",
            tokenStatusCode = HttpStatusCode.GatewayTimeout
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val entries = webAuth.decodeAuthorizationEntries(challengeXdr)

        assertFailsWith<Sep45TimeoutException> {
            webAuth.sendSignedChallenge(entries)
        }
    }

    // ============================================================================
    // Factory Tests
    // ============================================================================

    @Test
    fun testFromDomainSuccess() = runTest {
        val stellarToml = """
            WEB_AUTH_FOR_CONTRACTS_ENDPOINT="https://auth.example.com/auth"
            WEB_AUTH_CONTRACT_ID="$WEB_AUTH_CONTRACT_ID"
            SIGNING_KEY="$SERVER_ACCOUNT_ID"
        """.trimIndent()

        val mockClient = createMockClient(stellarTomlResponse = stellarToml)

        val webAuth = WebAuthForContracts.fromDomain(
            domain = "example.com",
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertNotNull(webAuth)
        assertEquals("https://auth.example.com/auth", webAuth.authEndpoint)
        assertEquals(WEB_AUTH_CONTRACT_ID, webAuth.webAuthContractId)
        assertEquals(SERVER_ACCOUNT_ID, webAuth.serverSigningKey)
    }

    @Test
    fun testFromDomainNoEndpoint() = runTest {
        val stellarToml = """
            WEB_AUTH_CONTRACT_ID="$WEB_AUTH_CONTRACT_ID"
            SIGNING_KEY="$SERVER_ACCOUNT_ID"
        """.trimIndent()

        val mockClient = createMockClient(stellarTomlResponse = stellarToml)

        assertFailsWith<Sep45NoEndpointException> {
            WebAuthForContracts.fromDomain(
                domain = "example.com",
                network = Network.TESTNET,
                httpClient = mockClient
            )
        }
    }

    @Test
    fun testFromDomainNoContractId() = runTest {
        val stellarToml = """
            WEB_AUTH_FOR_CONTRACTS_ENDPOINT="https://auth.example.com/auth"
            SIGNING_KEY="$SERVER_ACCOUNT_ID"
        """.trimIndent()

        val mockClient = createMockClient(stellarTomlResponse = stellarToml)

        assertFailsWith<Sep45NoContractIdException> {
            WebAuthForContracts.fromDomain(
                domain = "example.com",
                network = Network.TESTNET,
                httpClient = mockClient
            )
        }
    }

    @Test
    fun testFromDomainNoSigningKey() = runTest {
        val stellarToml = """
            WEB_AUTH_FOR_CONTRACTS_ENDPOINT="https://auth.example.com/auth"
            WEB_AUTH_CONTRACT_ID="$WEB_AUTH_CONTRACT_ID"
        """.trimIndent()

        val mockClient = createMockClient(stellarTomlResponse = stellarToml)

        assertFailsWith<Sep45NoSigningKeyException> {
            WebAuthForContracts.fromDomain(
                domain = "example.com",
                network = Network.TESTNET,
                httpClient = mockClient
            )
        }
    }

    // ============================================================================
    // Constructor Validation Tests
    // ============================================================================

    @Test
    fun testInvalidContractIdFormat() {
        assertFailsWith<IllegalArgumentException> {
            WebAuthForContracts(
                authEndpoint = AUTH_SERVER,
                webAuthContractId = SERVER_ACCOUNT_ID, // G... instead of C...
                serverSigningKey = SERVER_ACCOUNT_ID,
                serverHomeDomain = DOMAIN,
                network = Network.TESTNET
            )
        }
    }

    @Test
    fun testInvalidSigningKeyFormat() {
        assertFailsWith<IllegalArgumentException> {
            WebAuthForContracts(
                authEndpoint = AUTH_SERVER,
                webAuthContractId = WEB_AUTH_CONTRACT_ID,
                serverSigningKey = WEB_AUTH_CONTRACT_ID, // C... instead of G...
                serverHomeDomain = DOMAIN,
                network = Network.TESTNET
            )
        }
    }

    @Test
    fun testInvalidAuthEndpoint() {
        // "not-a-url" may be accepted by Url parser as a relative URL,
        // so use a URL without a host to trigger validation failure
        assertFailsWith<IllegalArgumentException> {
            WebAuthForContracts(
                authEndpoint = "://invalid",
                webAuthContractId = WEB_AUTH_CONTRACT_ID,
                serverSigningKey = SERVER_ACCOUNT_ID,
                serverHomeDomain = DOMAIN,
                network = Network.TESTNET
            )
        }
    }

    @Test
    fun testEmptyHomeDomain() {
        assertFailsWith<IllegalArgumentException> {
            WebAuthForContracts(
                authEndpoint = AUTH_SERVER,
                webAuthContractId = WEB_AUTH_CONTRACT_ID,
                serverSigningKey = SERVER_ACCOUNT_ID,
                serverHomeDomain = "",
                network = Network.TESTNET
            )
        }
    }

    // ============================================================================
    // Input Validation Tests
    // ============================================================================

    @Test
    fun testInvalidClientAccountFormat() = runTest {
        val mockClient = createMockClient()

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<IllegalArgumentException> {
            webAuth.jwtToken(
                clientAccountId = SERVER_ACCOUNT_ID, // G... instead of C...
                signers = listOf(KeyPair.random()),
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testClientDomainWithoutSigningMechanism() = runTest {
        val mockClient = createMockClient()

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45MissingClientDomainException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                clientDomain = "wallet.example.com",
                // Missing both clientDomainAccountKeyPair and clientDomainSigningDelegate
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testBothKeyPairAndDelegateProvided() = runTest {
        val mockClient = createMockClient()

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val delegate = Sep45ClientDomainSigningDelegate { it }

        assertFailsWith<Sep45MissingClientDomainException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                clientDomain = "wallet.example.com",
                clientDomainAccountKeyPair = KeyPair.random(),
                clientDomainSigningDelegate = delegate,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    // ============================================================================
    // Challenge Validation Tests
    // ============================================================================

    @Test
    fun testInvalidContractAddress() = runTest {
        val wrongContractId = "CCJCTOZFKPNTFLMORB7RBNKDQU42PBKGVTI4DIWVEMUCXRHWCYXGRRV7"
        val nonce = "test_nonce_${nonceCounter++}"

        val serverKeyPair = KeyPair.fromSecretSeed(SERVER_SECRET_SEED)
        val argsMap = buildArgsMap(
            account = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        var serverEntry = buildAuthEntry(
            credentialsAddress = SERVER_ACCOUNT_ID,
            contractId = wrongContractId, // Wrong contract!
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12345L,
            expirationLedger = 1000000L
        )
        serverEntry = signEntry(serverEntry, serverKeyPair)

        val clientEntry = buildAuthEntry(
            credentialsAddress = CLIENT_CONTRACT_ID,
            contractId = wrongContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12346L,
            expirationLedger = 1000000L
        )

        val challengeXdr = encodeAuthEntries(listOf(serverEntry, clientEntry))

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45InvalidContractAddressException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testInvalidFunctionName() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"

        val serverKeyPair = KeyPair.fromSecretSeed(SERVER_SECRET_SEED)
        val argsMap = buildArgsMap(
            account = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        var serverEntry = buildAuthEntry(
            credentialsAddress = SERVER_ACCOUNT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "wrong_function", // Wrong function!
            argsMap = argsMap,
            nonce = 12345L,
            expirationLedger = 1000000L
        )
        serverEntry = signEntry(serverEntry, serverKeyPair)

        val clientEntry = buildAuthEntry(
            credentialsAddress = CLIENT_CONTRACT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "wrong_function",
            argsMap = argsMap,
            nonce = 12346L,
            expirationLedger = 1000000L
        )

        val challengeXdr = encodeAuthEntries(listOf(serverEntry, clientEntry))

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45InvalidFunctionNameException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testMissingServerEntry() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"

        val argsMap = buildArgsMap(
            account = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        // Only client entry, no server entry
        val clientEntry = buildAuthEntry(
            credentialsAddress = CLIENT_CONTRACT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12346L,
            expirationLedger = 1000000L
        )

        val challengeXdr = encodeAuthEntries(listOf(clientEntry))

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45MissingServerEntryException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testMissingClientEntry() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"

        val serverKeyPair = KeyPair.fromSecretSeed(SERVER_SECRET_SEED)
        val argsMap = buildArgsMap(
            account = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        // Only server entry, no client entry
        var serverEntry = buildAuthEntry(
            credentialsAddress = SERVER_ACCOUNT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12345L,
            expirationLedger = 1000000L
        )
        serverEntry = signEntry(serverEntry, serverKeyPair)

        val challengeXdr = encodeAuthEntries(listOf(serverEntry))

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45MissingClientEntryException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testInvalidServerSignature() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"

        // Build challenge without signing server entry
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce,
            signServerEntry = false // No server signature
        )

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45InvalidServerSignatureException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testInvalidNonce() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(SERVER_SECRET_SEED)

        val argsMap1 = buildArgsMap(
            account = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = "nonce_1"
        )

        val argsMap2 = buildArgsMap(
            account = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = "nonce_2" // Different nonce!
        )

        // Server entry with nonce_1
        var serverEntry = buildAuthEntry(
            credentialsAddress = SERVER_ACCOUNT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap1,
            nonce = 12345L,
            expirationLedger = 1000000L
        )
        serverEntry = signEntry(serverEntry, serverKeyPair)

        // Client entry with nonce_2 (different!)
        val clientEntry = buildAuthEntry(
            credentialsAddress = CLIENT_CONTRACT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap2,
            nonce = 12346L,
            expirationLedger = 1000000L
        )

        val challengeXdr = encodeAuthEntries(listOf(serverEntry, clientEntry))

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45InvalidNonceException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testInvalidHomeDomain() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val serverKeyPair = KeyPair.fromSecretSeed(SERVER_SECRET_SEED)

        val argsMap = buildArgsMap(
            account = CLIENT_CONTRACT_ID,
            homeDomain = "wrong.domain.com", // Wrong domain!
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        var serverEntry = buildAuthEntry(
            credentialsAddress = SERVER_ACCOUNT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12345L,
            expirationLedger = 1000000L
        )
        serverEntry = signEntry(serverEntry, serverKeyPair)

        val clientEntry = buildAuthEntry(
            credentialsAddress = CLIENT_CONTRACT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12346L,
            expirationLedger = 1000000L
        )

        val challengeXdr = encodeAuthEntries(listOf(serverEntry, clientEntry))

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45InvalidHomeDomainException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testInvalidWebAuthDomain() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val serverKeyPair = KeyPair.fromSecretSeed(SERVER_SECRET_SEED)

        val argsMap = buildArgsMap(
            account = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "wrong.auth.stellar.org", // Wrong web auth domain!
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        var serverEntry = buildAuthEntry(
            credentialsAddress = SERVER_ACCOUNT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12345L,
            expirationLedger = 1000000L
        )
        serverEntry = signEntry(serverEntry, serverKeyPair)

        val clientEntry = buildAuthEntry(
            credentialsAddress = CLIENT_CONTRACT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12346L,
            expirationLedger = 1000000L
        )

        val challengeXdr = encodeAuthEntries(listOf(serverEntry, clientEntry))

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45InvalidWebAuthDomainException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testInvalidAccount() = runTest {
        val wrongClientAccount = "CBMKBASJGUKV26JB55OKZW3G3PGQ4C7PLRH6L2RW74PYUTE22Y4KFW56"
        val nonce = "test_nonce_${nonceCounter++}"
        val serverKeyPair = KeyPair.fromSecretSeed(SERVER_SECRET_SEED)

        val argsMap = buildArgsMap(
            account = wrongClientAccount, // Wrong client account in args!
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        var serverEntry = buildAuthEntry(
            credentialsAddress = SERVER_ACCOUNT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12345L,
            expirationLedger = 1000000L
        )
        serverEntry = signEntry(serverEntry, serverKeyPair)

        val clientEntry = buildAuthEntry(
            credentialsAddress = CLIENT_CONTRACT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12346L,
            expirationLedger = 1000000L
        )

        val challengeXdr = encodeAuthEntries(listOf(serverEntry, clientEntry))

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45InvalidAccountException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testSubInvocationsFound() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val serverKeyPair = KeyPair.fromSecretSeed(SERVER_SECRET_SEED)

        val argsMap = buildArgsMap(
            account = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        // Create a sub-invocation
        val subContractFn = InvokeContractArgsXdr(
            contractAddress = Address(WEB_AUTH_CONTRACT_ID).toSCAddress(),
            functionName = SCSymbolXdr("some_other_function"),
            args = emptyList()
        )
        val subFunction = SorobanAuthorizedFunctionXdr.ContractFn(subContractFn)
        val subInvocation = SorobanAuthorizedInvocationXdr(
            function = subFunction,
            subInvocations = emptyList()
        )

        // Server entry with sub-invocations (invalid)
        var serverEntry = buildAuthEntry(
            credentialsAddress = SERVER_ACCOUNT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12345L,
            expirationLedger = 1000000L,
            subInvocations = listOf(subInvocation)
        )
        serverEntry = signEntry(serverEntry, serverKeyPair)

        val challengeXdr = encodeAuthEntries(listOf(serverEntry))

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45SubInvocationsFoundException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testInvalidClientDomainAccount() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val clientDomain = "client.example.com"
        val wrongClientDomainAccount = KeyPair.random().getAccountId()
        val actualClientDomainKeyPair = KeyPair.random()

        // Challenge has wrong client domain account
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce,
            clientDomain = clientDomain,
            clientDomainAccount = wrongClientDomainAccount
        )

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45InvalidArgsException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                clientDomain = clientDomain,
                clientDomainAccountKeyPair = actualClientDomainKeyPair,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testInvalidNetworkPassphrase() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Wrong Network Passphrase"}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45InvalidNetworkPassphraseException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    // ============================================================================
    // Helper Method Tests
    // ============================================================================

    @Test
    fun testEncodeDecodeAuthorizationEntries() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val argsMap = buildArgsMap(
            account = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val entry = buildAuthEntry(
            credentialsAddress = SERVER_ACCOUNT_ID,
            contractId = WEB_AUTH_CONTRACT_ID,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12345L,
            expirationLedger = 1000000L
        )

        val mockClient = createMockClient()
        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        val encoded = webAuth.encodeAuthorizationEntries(listOf(entry))
        val decoded = webAuth.decodeAuthorizationEntries(encoded)

        assertEquals(1, decoded.size)
    }

    @Test
    fun testAuthTokenParsing() {
        val token = Sep45AuthToken.parse(SUCCESS_JWT_TOKEN)

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
        assertEquals(CLIENT_CONTRACT_ID, token.account)
        assertEquals(DOMAIN, token.issuer)
        assertTrue(token.issuedAt > 0)
        assertTrue(token.expiresAt > token.issuedAt)
    }

    @Test
    fun testChallengeResponseParsing() {
        val jsonString = """{"authorization_entries": "AAAA", "network_passphrase": "Test SDF Network ; September 2015"}"""
        val response = Sep45ChallengeResponse.fromJson(jsonString)

        assertEquals("AAAA", response.authorizationEntries)
        assertEquals("Test SDF Network ; September 2015", response.networkPassphrase)
    }

    @Test
    fun testChallengeResponseParsingCamelCase() {
        // Test camelCase field names
        val jsonString = """{"authorizationEntries": "BBBB", "networkPassphrase": "Public Global Stellar Network ; September 2015"}"""
        val response = Sep45ChallengeResponse.fromJson(jsonString)

        assertEquals("BBBB", response.authorizationEntries)
        assertEquals("Public Global Stellar Network ; September 2015", response.networkPassphrase)
    }

    // ============================================================================
    // Additional Edge Case Tests
    // ============================================================================

    @Test
    fun testUnknownResponseCode() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": "$challengeXdr"}""",
            tokenResponse = "Internal Server Error",
            tokenStatusCode = HttpStatusCode.InternalServerError
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        assertFailsWith<Sep45UnknownResponseException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testEmptyAuthorizationEntries() = runTest {
        val mockClient = createMockClient(
            challengeResponse = """{"authorization_entries": null}"""
        )

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient
        )

        // When authorization_entries is null, the SDK throws Sep45InvalidArgsException
        // because it cannot decode empty/null entries
        assertFailsWith<Sep45InvalidArgsException> {
            webAuth.jwtToken(
                clientAccountId = CLIENT_CONTRACT_ID,
                signers = listOf(KeyPair.random()),
                homeDomain = DOMAIN,
                signatureExpirationLedger = 1000000L
            )
        }
    }

    @Test
    fun testPublicNetworkConstruction() {
        // Test that WebAuthForContracts can be constructed with PUBLIC network
        // The full flow is tested in integration tests
        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.PUBLIC
        )

        assertNotNull(webAuth)
    }

    @Test
    fun testCustomHttpHeaders() = runTest {
        val nonce = "test_nonce_${nonceCounter++}"
        val challengeXdr = buildValidChallenge(
            clientAccountId = CLIENT_CONTRACT_ID,
            homeDomain = DOMAIN,
            webAuthDomain = "auth.example.stellar.org",
            webAuthDomainAccount = SERVER_ACCOUNT_ID,
            nonce = nonce
        )

        var customHeaderReceived = false
        val mockEngine = MockEngine { request ->
            if (request.headers["X-Custom-Header"] == "test-value") {
                customHeaderReceived = true
            }

            when {
                request.method == HttpMethod.Get -> {
                    respond(
                        content = """{"authorization_entries": "$challengeXdr", "network_passphrase": "Test SDF Network ; September 2015"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                request.method == HttpMethod.Post -> {
                    respond(
                        content = """{"token": "$SUCCESS_JWT_TOKEN"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> {
                    respond(
                        content = """{"error": "Not found"}""",
                        status = HttpStatusCode.NotFound,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(this@WebAuthForContractsTest.json)
            }
        }

        val webAuth = WebAuthForContracts(
            authEndpoint = AUTH_SERVER,
            webAuthContractId = WEB_AUTH_CONTRACT_ID,
            serverSigningKey = SERVER_ACCOUNT_ID,
            serverHomeDomain = DOMAIN,
            network = Network.TESTNET,
            httpClient = mockClient,
            httpRequestHeaders = mapOf("X-Custom-Header" to "test-value")
        )

        val token = webAuth.jwtToken(
            clientAccountId = CLIENT_CONTRACT_ID,
            signers = listOf(KeyPair.random()),
            homeDomain = DOMAIN,
            signatureExpirationLedger = 1000000L
        )

        assertEquals(SUCCESS_JWT_TOKEN, token.token)
        assertTrue(customHeaderReceived, "Custom header should have been sent")
    }
}
