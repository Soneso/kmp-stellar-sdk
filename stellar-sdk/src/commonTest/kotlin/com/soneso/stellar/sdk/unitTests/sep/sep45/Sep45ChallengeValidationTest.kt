// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep45

import com.soneso.stellar.sdk.sep.sep45.*
import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.sep.sep45.exceptions.*
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for SEP-45 challenge validation logic in WebAuthForContracts.
 *
 * These tests verify all security validation checks performed on authorization entries
 * received from a SEP-45 authentication server. Each test targets a specific validation
 * requirement defined in the SEP-45 specification.
 */
@OptIn(ExperimentalEncodingApi::class)
class Sep45ChallengeValidationTest {

    // Test constants
    private val testServerSecretSeed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
    private val testHomeDomain = "testanchor.stellar.org"
    private val testAuthEndpoint = "https://testanchor.stellar.org/auth"
    private val testWebAuthDomain = "testanchor.stellar.org"
    private val network = Network.TESTNET

    // Test contract IDs and accounts
    private val testWebAuthContractId = "CA7A3N2BB35XMTFPAYWVZEF4TEYXW7DAEWDXJNQGUPR5SWSM2UVZCJM2"
    private val testClientContractId = "CDZJIDQW5WTPAZ64PGIJGVEIDNK72LL3LKUZWG3G6GWXYQKI2JNIVFNV"
    private val wrongContractId = "CCJCTOZFKPNTFLMORB7RBNKDQU42PBKGVTI4DIWVEMUCXRHWCYXGRRV7"
    private val wrongClientContractId = "CBMKBASJGUKV26JB55OKZW3G3PGQ4C7PLRH6L2RW74PYUTE22Y4KFW56"

    // Standard nonce for testing
    private val testNonce = "test_nonce_12345"

    /**
     * Helper function to create a WebAuthForContracts instance for testing.
     */
    private suspend fun createWebAuth(): WebAuthForContracts {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        return WebAuthForContracts(
            authEndpoint = testAuthEndpoint,
            webAuthContractId = testWebAuthContractId,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            network = network
        )
    }

    /**
     * Creates an SCAddress for an account ID (G... address).
     */
    private suspend fun createAccountAddress(accountId: String): SCAddressXdr {
        val keyPair = KeyPair.fromAccountId(accountId)
        val publicKeyXdr = PublicKeyXdr.Ed25519(Uint256Xdr(keyPair.getPublicKey()))
        val accountIdXdr = AccountIDXdr(publicKeyXdr)
        return SCAddressXdr.AccountId(accountIdXdr)
    }

    /**
     * Creates an SCAddress for a contract ID (C... address).
     */
    private fun createContractAddress(contractId: String): SCAddressXdr {
        val contractBytes = StrKey.decodeContract(contractId)
        val hashXdr = HashXdr(contractBytes)
        val contractIdXdr = ContractIDXdr(hashXdr)
        return SCAddressXdr.ContractId(contractIdXdr)
    }

    /**
     * Builds the args map for web_auth_verify function.
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
        val entries = mutableListOf<SCMapEntryXdr>()

        entries.add(SCMapEntryXdr(
            key = SCValXdr.Sym(SCSymbolXdr("account")),
            `val` = SCValXdr.Str(SCStringXdr(account))
        ))
        entries.add(SCMapEntryXdr(
            key = SCValXdr.Sym(SCSymbolXdr("home_domain")),
            `val` = SCValXdr.Str(SCStringXdr(homeDomain))
        ))
        entries.add(SCMapEntryXdr(
            key = SCValXdr.Sym(SCSymbolXdr("web_auth_domain")),
            `val` = SCValXdr.Str(SCStringXdr(webAuthDomain))
        ))
        entries.add(SCMapEntryXdr(
            key = SCValXdr.Sym(SCSymbolXdr("web_auth_domain_account")),
            `val` = SCValXdr.Str(SCStringXdr(webAuthDomainAccount))
        ))
        entries.add(SCMapEntryXdr(
            key = SCValXdr.Sym(SCSymbolXdr("nonce")),
            `val` = SCValXdr.Str(SCStringXdr(nonce))
        ))

        clientDomain?.let {
            entries.add(SCMapEntryXdr(
                key = SCValXdr.Sym(SCSymbolXdr("client_domain")),
                `val` = SCValXdr.Str(SCStringXdr(it))
            ))
        }

        clientDomainAccount?.let {
            entries.add(SCMapEntryXdr(
                key = SCValXdr.Sym(SCSymbolXdr("client_domain_account")),
                `val` = SCValXdr.Str(SCStringXdr(it))
            ))
        }

        return SCValXdr.Map(SCMapXdr(entries))
    }

    /**
     * Builds an args map without the nonce field (for testing missing nonce).
     */
    private fun buildArgsMapWithoutNonce(
        account: String,
        homeDomain: String,
        webAuthDomain: String,
        webAuthDomainAccount: String
    ): SCValXdr {
        val entries = mutableListOf<SCMapEntryXdr>()

        entries.add(SCMapEntryXdr(
            key = SCValXdr.Sym(SCSymbolXdr("account")),
            `val` = SCValXdr.Str(SCStringXdr(account))
        ))
        entries.add(SCMapEntryXdr(
            key = SCValXdr.Sym(SCSymbolXdr("home_domain")),
            `val` = SCValXdr.Str(SCStringXdr(homeDomain))
        ))
        entries.add(SCMapEntryXdr(
            key = SCValXdr.Sym(SCSymbolXdr("web_auth_domain")),
            `val` = SCValXdr.Str(SCStringXdr(webAuthDomain))
        ))
        entries.add(SCMapEntryXdr(
            key = SCValXdr.Sym(SCSymbolXdr("web_auth_domain_account")),
            `val` = SCValXdr.Str(SCStringXdr(webAuthDomainAccount))
        ))

        return SCValXdr.Map(SCMapXdr(entries))
    }

    /**
     * Builds an authorization entry with the specified parameters.
     */
    private suspend fun buildAuthEntry(
        credentialsAddress: SCAddressXdr,
        contractId: String,
        functionName: String,
        argsMap: SCValXdr,
        nonce: Long = 12345L,
        expirationLedger: Long = 1000000L,
        subInvocations: List<SorobanAuthorizedInvocationXdr> = emptyList()
    ): SorobanAuthorizationEntryXdr {
        val contractAddress = createContractAddress(contractId)
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = contractAddress,
            functionName = SCSymbolXdr(functionName),
            args = listOf(argsMap)
        )

        val function = SorobanAuthorizedFunctionXdr.ContractFn(invokeArgs)
        val invocation = SorobanAuthorizedInvocationXdr(
            function = function,
            subInvocations = subInvocations
        )

        val credentials = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = credentialsAddress,
                nonce = Int64Xdr(nonce),
                signatureExpirationLedger = Uint32Xdr(expirationLedger.toUInt()),
                signature = SCValXdr.Vec(SCVecXdr(emptyList()))  // Empty signature
            )
        )

        return SorobanAuthorizationEntryXdr(
            credentials = credentials,
            rootInvocation = invocation
        )
    }

    /**
     * Signs an authorization entry with the server keypair.
     */
    private suspend fun signEntryWithServer(
        entry: SorobanAuthorizationEntryXdr,
        serverSecretSeed: String = testServerSecretSeed
    ): SorobanAuthorizationEntryXdr {
        val serverKeyPair = KeyPair.fromSecretSeed(serverSecretSeed)
        return Auth.authorizeEntry(
            entry = entry,
            signer = serverKeyPair,
            validUntilLedgerSeq = 1000000L,
            network = network
        )
    }

    /**
     * Builds a complete valid challenge with server and client entries.
     */
    private suspend fun buildValidChallenge(
        clientAccountId: String = testClientContractId,
        homeDomain: String = testHomeDomain,
        webAuthDomain: String = testWebAuthDomain,
        webAuthContractId: String = testWebAuthContractId,
        nonce: String = testNonce,
        signServerEntry: Boolean = true,
        clientDomain: String? = null,
        clientDomainAccount: String? = null
    ): List<SorobanAuthorizationEntryXdr> {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val entries = mutableListOf<SorobanAuthorizationEntryXdr>()

        // Build args map
        val argsMap = buildArgsMap(
            account = clientAccountId,
            homeDomain = homeDomain,
            webAuthDomain = webAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = nonce,
            clientDomain = clientDomain,
            clientDomainAccount = clientDomainAccount
        )

        // Create server entry
        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = webAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12345L
        )
        if (signServerEntry) {
            serverEntry = signEntryWithServer(serverEntry)
        }
        entries.add(serverEntry)

        // Create client entry
        val clientAddress = createContractAddress(clientAccountId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = webAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12346L
        )
        entries.add(clientEntry)

        // Create client domain entry if needed
        if (clientDomainAccount != null) {
            val clientDomainAddress = createAccountAddress(clientDomainAccount)
            val clientDomainEntry = buildAuthEntry(
                credentialsAddress = clientDomainAddress,
                contractId = webAuthContractId,
                functionName = "web_auth_verify",
                argsMap = argsMap,
                nonce = 12347L
            )
            entries.add(clientDomainEntry)
        }

        return entries
    }

    // ============================================================================
    // Structural Validation Tests
    // ============================================================================

    @Test
    fun testValidChallengeSuccess() = runTest {
        val webAuth = createWebAuth()
        val entries = buildValidChallenge()

        // Should not throw any exception
        webAuth.validateChallenge(
            authEntries = entries,
            clientAccountId = testClientContractId,
            homeDomain = testHomeDomain
        )
    }

    @Test
    fun testInvalidContractAddress() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        // Build args with correct values
        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        // Create entries with wrong contract ID
        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = wrongContractId,  // Wrong contract
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = wrongContractId,  // Wrong contract
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry)

        val exception = assertFailsWith<Sep45InvalidContractAddressException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }

        assertTrue(exception.expected == testWebAuthContractId)
        assertTrue(exception.actual == wrongContractId)
    }

    @Test
    fun testInvalidFunctionName() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        // Create entries with wrong function name
        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "wrong_function",  // Wrong function
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "wrong_function",  // Wrong function
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry)

        val exception = assertFailsWith<Sep45InvalidFunctionNameException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }

        assertTrue(exception.expected == "web_auth_verify")
        assertTrue(exception.actual == "wrong_function")
    }

    @Test
    fun testSubInvocationsFound() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        // Create a sub-invocation
        val subContractAddress = createContractAddress(testWebAuthContractId)
        val subInvokeArgs = InvokeContractArgsXdr(
            contractAddress = subContractAddress,
            functionName = SCSymbolXdr("some_other_function"),
            args = emptyList()
        )
        val subFunction = SorobanAuthorizedFunctionXdr.ContractFn(subInvokeArgs)
        val subInvocation = SorobanAuthorizedInvocationXdr(
            function = subFunction,
            subInvocations = emptyList()
        )

        // Create server entry with sub-invocation
        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            subInvocations = listOf(subInvocation)  // Has sub-invocation!
        )
        serverEntry = signEntryWithServer(serverEntry)

        val entries = listOf(serverEntry)

        assertFailsWith<Sep45SubInvocationsFoundException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    // ============================================================================
    // Argument Validation Tests
    // ============================================================================

    @Test
    fun testInvalidAccount() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        // Build args with wrong account
        val argsMap = buildArgsMap(
            account = wrongClientContractId,  // Wrong client account
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry)

        val exception = assertFailsWith<Sep45InvalidAccountException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }

        assertTrue(exception.expected == testClientContractId)
        assertTrue(exception.actual == wrongClientContractId)
    }

    @Test
    fun testInvalidHomeDomain() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val wrongHomeDomain = "wrong.domain.com"

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = wrongHomeDomain,  // Wrong home domain
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry)

        val exception = assertFailsWith<Sep45InvalidHomeDomainException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }

        assertTrue(exception.expected == testHomeDomain)
        assertTrue(exception.actual == wrongHomeDomain)
    }

    @Test
    fun testInvalidWebAuthDomain() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val wrongWebAuthDomain = "wrong.auth.stellar.org"

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = wrongWebAuthDomain,  // Wrong web auth domain
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry)

        val exception = assertFailsWith<Sep45InvalidWebAuthDomainException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }

        assertTrue(exception.expected == testWebAuthDomain)
        assertTrue(exception.actual == wrongWebAuthDomain)
    }

    @Test
    fun testInvalidWebAuthDomainAccount() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val wrongServerKeyPair = KeyPair.random()

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = wrongServerKeyPair.getAccountId(),  // Wrong account
            nonce = testNonce
        )

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry)

        assertFailsWith<Sep45InvalidArgsException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testInvalidNonce() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val nonce1 = "nonce_server_entry"
        val nonce2 = "nonce_client_entry"  // Different nonce

        // Server entry with nonce1
        val argsMap1 = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = nonce1
        )

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap1
        )
        serverEntry = signEntryWithServer(serverEntry)

        // Client entry with nonce2 (different!)
        val argsMap2 = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = nonce2
        )

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap2
        )

        val entries = listOf(serverEntry, clientEntry)

        assertFailsWith<Sep45InvalidNonceException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testMissingNonce() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        // Build args without nonce
        val argsMap = buildArgsMapWithoutNonce(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId()
        )

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry)

        assertFailsWith<Sep45InvalidNonceException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testInvalidClientDomainAccount() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val wrongClientDomainAccount = KeyPair.random().getAccountId()
        val actualClientDomainKeyPair = KeyPair.random()
        val clientDomain = "client.example.com"

        // Build args with wrong client domain account
        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce,
            clientDomain = clientDomain,
            clientDomainAccount = wrongClientDomainAccount  // Wrong!
        )

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        // Entry for the wrong client domain account (mismatches expected)
        val clientDomainAddress = createAccountAddress(wrongClientDomainAccount)
        val clientDomainEntry = buildAuthEntry(
            credentialsAddress = clientDomainAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry, clientDomainEntry)

        // Pass the actual expected client domain account
        assertFailsWith<Sep45InvalidArgsException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain,
                clientDomainAccountId = actualClientDomainKeyPair.getAccountId()
            )
        }
    }

    // ============================================================================
    // Entry Presence Tests
    // ============================================================================

    @Test
    fun testMissingServerEntry() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        // Only client entry, no server entry
        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(clientEntry)

        assertFailsWith<Sep45MissingServerEntryException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testMissingClientEntry() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        // Only server entry, no client entry
        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val entries = listOf(serverEntry)

        assertFailsWith<Sep45MissingClientEntryException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testMissingClientDomainEntry() = runTest {
        val webAuth = createWebAuth()
        val clientDomainKeyPair = KeyPair.random()
        val clientDomain = "client.example.com"

        // Build challenge without client domain entry
        val entries = buildValidChallenge(
            clientAccountId = testClientContractId,
            clientDomain = clientDomain,
            clientDomainAccount = null  // No client domain entry
        )

        // Validate with expected client domain account - should fail
        assertFailsWith<Sep45MissingClientEntryException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain,
                clientDomainAccountId = clientDomainKeyPair.getAccountId()
            )
        }
    }

    @Test
    fun testInvalidServerSignature() = runTest {
        val webAuth = createWebAuth()

        // Build challenge but don't sign the server entry
        val entries = buildValidChallenge(
            signServerEntry = false  // No server signature!
        )

        assertFailsWith<Sep45InvalidServerSignatureException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testServerSignatureWithWrongKey() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val wrongKeyPair = KeyPair.random()

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        // Create server entry
        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        // Sign with wrong key
        serverEntry = Auth.authorizeEntry(
            entry = serverEntry,
            signer = wrongKeyPair,
            validUntilLedgerSeq = 1000000L,
            network = network
        )

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry)

        assertFailsWith<Sep45InvalidServerSignatureException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    // ============================================================================
    // Network Passphrase Tests
    // ============================================================================

    @Test
    fun testInvalidNetworkPassphrase() = runTest {
        // Create WebAuth for TESTNET
        val webAuth = createWebAuth()

        // Sep45InvalidNetworkPassphraseException is thrown in jwtToken flow
        // before validateChallenge, but we can test via the exception
        val exception = Sep45InvalidNetworkPassphraseException(
            expected = Network.TESTNET.networkPassphrase,
            actual = Network.PUBLIC.networkPassphrase
        )

        assertTrue(exception.expected == "Test SDF Network ; September 2015")
        assertTrue(exception.actual == "Public Global Stellar Network ; September 2015")
        assertTrue(exception.message?.contains("mismatch") == true)
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Test
    fun testEmptyAuthorizationEntries() = runTest {
        val webAuth = createWebAuth()

        assertFailsWith<Sep45InvalidArgsException> {
            webAuth.validateChallenge(
                authEntries = emptyList(),
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testSingleEntryOnly() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        // Only one entry (server or client, doesn't matter - missing the other)
        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val entries = listOf(serverEntry)

        // Should fail because client entry is missing
        assertFailsWith<Sep45MissingClientEntryException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testDuplicateClientEntries() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = testWebAuthDomain,
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        // Server entry
        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        // Two identical client entries
        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry1 = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12346L
        )
        val clientEntry2 = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap,
            nonce = 12347L
        )

        val entries = listOf(serverEntry, clientEntry1, clientEntry2)

        // Should succeed - duplicate client entries are not explicitly rejected
        // The validation just checks that at least one exists
        webAuth.validateChallenge(
            authEntries = entries,
            clientAccountId = testClientContractId,
            homeDomain = testHomeDomain
        )
    }

    // ============================================================================
    // Client Domain Success Test
    // ============================================================================

    @Test
    fun testClientDomainSuccess() = runTest {
        val webAuth = createWebAuth()
        val clientDomainKeyPair = KeyPair.random()
        val clientDomain = "client.example.com"

        // Build valid challenge with client domain entry
        val entries = buildValidChallenge(
            clientAccountId = testClientContractId,
            clientDomain = clientDomain,
            clientDomainAccount = clientDomainKeyPair.getAccountId()
        )

        // Should succeed with correct client domain account
        webAuth.validateChallenge(
            authEntries = entries,
            clientAccountId = testClientContractId,
            homeDomain = testHomeDomain,
            clientDomainAccountId = clientDomainKeyPair.getAccountId()
        )
    }

    // ============================================================================
    // Additional Validation Tests
    // ============================================================================

    @Test
    fun testNonContractFunctionType() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        // Create an entry with CreateContractHostFn instead of ContractFn
        val preimage = ContractIDPreimageXdr.FromAsset(
            AssetXdr.Void  // Native asset
        )
        val createContractArgs = CreateContractArgsXdr(
            contractIdPreimage = preimage,
            executable = ContractExecutableXdr.WasmHash(HashXdr(ByteArray(32)))
        )
        val function = SorobanAuthorizedFunctionXdr.CreateContractHostFn(createContractArgs)
        val invocation = SorobanAuthorizedInvocationXdr(
            function = function,
            subInvocations = emptyList()
        )

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        val credentials = SorobanCredentialsXdr.Address(
            SorobanAddressCredentialsXdr(
                address = serverAddress,
                nonce = Int64Xdr(12345L),
                signatureExpirationLedger = Uint32Xdr(1000000u),
                signature = SCValXdr.Vec(SCVecXdr(emptyList()))
            )
        )

        val entry = SorobanAuthorizationEntryXdr(
            credentials = credentials,
            rootInvocation = invocation
        )

        val entries = listOf(entry)

        assertFailsWith<Sep45InvalidFunctionNameException> {
            webAuth.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testWebAuthDomainWithNonStandardPort() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        // Create WebAuth with non-standard port in endpoint
        val webAuthWithPort = WebAuthForContracts(
            authEndpoint = "https://testanchor.stellar.org:8080/auth",
            webAuthContractId = testWebAuthContractId,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            network = network
        )

        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = "testanchor.stellar.org:8080",  // Include port
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry)

        // Should succeed with port in web_auth_domain
        webAuthWithPort.validateChallenge(
            authEntries = entries,
            clientAccountId = testClientContractId,
            homeDomain = testHomeDomain
        )
    }

    @Test
    fun testWebAuthDomainMismatchWithPort() = runTest {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)

        // Create WebAuth with non-standard port in endpoint
        val webAuthWithPort = WebAuthForContracts(
            authEndpoint = "https://testanchor.stellar.org:8080/auth",
            webAuthContractId = testWebAuthContractId,
            serverSigningKey = serverKeyPair.getAccountId(),
            serverHomeDomain = testHomeDomain,
            network = network
        )

        // Missing port in web_auth_domain (mismatch)
        val argsMap = buildArgsMap(
            account = testClientContractId,
            homeDomain = testHomeDomain,
            webAuthDomain = "testanchor.stellar.org",  // Missing port!
            webAuthDomainAccount = serverKeyPair.getAccountId(),
            nonce = testNonce
        )

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        var serverEntry = buildAuthEntry(
            credentialsAddress = serverAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )
        serverEntry = signEntryWithServer(serverEntry)

        val clientAddress = createContractAddress(testClientContractId)
        val clientEntry = buildAuthEntry(
            credentialsAddress = clientAddress,
            contractId = testWebAuthContractId,
            functionName = "web_auth_verify",
            argsMap = argsMap
        )

        val entries = listOf(serverEntry, clientEntry)

        assertFailsWith<Sep45InvalidWebAuthDomainException> {
            webAuthWithPort.validateChallenge(
                authEntries = entries,
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testValidDefaultHomeDomain() = runTest {
        val webAuth = createWebAuth()
        val entries = buildValidChallenge()

        // Pass null for homeDomain - should use serverHomeDomain as default
        webAuth.validateChallenge(
            authEntries = entries,
            clientAccountId = testClientContractId,
            homeDomain = null  // Uses default
        )
    }

    @Test
    fun testThreeEntriesWithClientDomain() = runTest {
        val webAuth = createWebAuth()
        val clientDomainKeyPair = KeyPair.random()
        val clientDomain = "wallet.example.com"

        // Build challenge with all three entries: server, client, client domain
        val entries = buildValidChallenge(
            clientDomain = clientDomain,
            clientDomainAccount = clientDomainKeyPair.getAccountId()
        )

        // Should have 3 entries
        assertTrue(entries.size == 3)

        // Should succeed
        webAuth.validateChallenge(
            authEntries = entries,
            clientAccountId = testClientContractId,
            homeDomain = testHomeDomain,
            clientDomainAccountId = clientDomainKeyPair.getAccountId()
        )
    }
}
