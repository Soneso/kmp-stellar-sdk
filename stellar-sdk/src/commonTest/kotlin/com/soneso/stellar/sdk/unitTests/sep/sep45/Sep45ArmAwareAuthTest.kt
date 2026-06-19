// Copyright 2025 Soneso. All rights reserved.
// Use of this source code is governed by a license that can be
// found in the LICENSE file.

package com.soneso.stellar.sdk.unitTests.sep.sep45

import com.soneso.stellar.sdk.Auth
import com.soneso.stellar.sdk.KeyPair
import com.soneso.stellar.sdk.Network
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.Util
import com.soneso.stellar.sdk.sep.sep45.WebAuthForContracts
import com.soneso.stellar.sdk.sep.sep45.exceptions.Sep45InvalidServerSignatureException
import com.soneso.stellar.sdk.xdr.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests that SEP-45 server-signature verification and client signing operate on every
 * address-bearing credential arm (ADDRESS, ADDRESS_V2, ADDRESS_WITH_DELEGATES), not only
 * the legacy ADDRESS arm.
 *
 * The hash preimage type is bound to the credential arm: ADDRESS uses the legacy
 * ENVELOPE_TYPE_SOROBAN_AUTHORIZATION; ADDRESS_V2 and ADDRESS_WITH_DELEGATES use the
 * address-bound ENVELOPE_TYPE_SOROBAN_AUTHORIZATION_WITH_ADDRESS. A signature produced
 * against the wrong preimage arm must not verify, and signing must preserve the arm.
 */
class Sep45ArmAwareAuthTest {

    private val testServerSecretSeed = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
    private val testHomeDomain = "testanchor.stellar.org"
    private val testAuthEndpoint = "https://testanchor.stellar.org/auth"
    private val testWebAuthDomain = "testanchor.stellar.org"
    private val network = Network.TESTNET

    private val testWebAuthContractId = "CA7A3N2BB35XMTFPAYWVZEF4TEYXW7DAEWDXJNQGUPR5SWSM2UVZCJM2"
    private val testClientContractId = "CDZJIDQW5WTPAZ64PGIJGVEIDNK72LL3LKUZWG3G6GWXYQKI2JNIVFNV"

    private val testNonce = "test_nonce_12345"

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

    private suspend fun createAccountAddress(accountId: String): SCAddressXdr {
        val keyPair = KeyPair.fromAccountId(accountId)
        val publicKeyXdr = PublicKeyXdr.Ed25519(Uint256Xdr(keyPair.getPublicKey()))
        return SCAddressXdr.AccountId(AccountIDXdr(publicKeyXdr))
    }

    private fun createContractAddress(contractId: String): SCAddressXdr {
        val contractBytes = StrKey.decodeContract(contractId)
        return SCAddressXdr.ContractId(ContractIDXdr(HashXdr(contractBytes)))
    }

    private fun buildArgsMap(
        account: String,
        webAuthDomainAccount: String,
        nonce: String = testNonce
    ): SCValXdr {
        val entries = mutableListOf(
            SCMapEntryXdr(SCValXdr.Sym(SCSymbolXdr("account")), SCValXdr.Str(SCStringXdr(account))),
            SCMapEntryXdr(SCValXdr.Sym(SCSymbolXdr("home_domain")), SCValXdr.Str(SCStringXdr(testHomeDomain))),
            SCMapEntryXdr(SCValXdr.Sym(SCSymbolXdr("web_auth_domain")), SCValXdr.Str(SCStringXdr(testWebAuthDomain))),
            SCMapEntryXdr(
                SCValXdr.Sym(SCSymbolXdr("web_auth_domain_account")),
                SCValXdr.Str(SCStringXdr(webAuthDomainAccount))
            ),
            SCMapEntryXdr(SCValXdr.Sym(SCSymbolXdr("nonce")), SCValXdr.Str(SCStringXdr(nonce)))
        )
        return SCValXdr.Map(SCMapXdr(entries))
    }

    private fun buildInvocation(argsMap: SCValXdr): SorobanAuthorizedInvocationXdr {
        val invokeArgs = InvokeContractArgsXdr(
            contractAddress = createContractAddress(testWebAuthContractId),
            functionName = SCSymbolXdr("web_auth_verify"),
            args = listOf(argsMap)
        )
        return SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(invokeArgs),
            subInvocations = emptyList()
        )
    }

    private fun buildAddressCredentials(
        address: SCAddressXdr,
        nonce: Long,
        expirationLedger: Long = 1_000_000L
    ): SorobanAddressCredentialsXdr = SorobanAddressCredentialsXdr(
        address = address,
        nonce = Int64Xdr(nonce),
        signatureExpirationLedger = Uint32Xdr(expirationLedger.toUInt()),
        signature = SCValXdr.Vec(SCVecXdr(emptyList()))
    )

    /**
     * Builds a complete challenge (server entry + client entry) whose credentials use the
     * arm produced by [makeCredentials]. The server entry is signed by [Auth.authorizeEntry],
     * which selects the preimage arm from the credentials.
     */
    private suspend fun buildSignedChallenge(
        makeCredentials: (SorobanAddressCredentialsXdr) -> SorobanCredentialsXdr
    ): List<SorobanAuthorizationEntryXdr> {
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val argsMap = buildArgsMap(testClientContractId, serverKeyPair.getAccountId())

        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        val serverEntry = SorobanAuthorizationEntryXdr(
            credentials = makeCredentials(buildAddressCredentials(serverAddress, 12345L)),
            rootInvocation = buildInvocation(argsMap)
        )
        val signedServerEntry = Auth.authorizeEntry(
            entry = serverEntry,
            signer = serverKeyPair,
            validUntilLedgerSeq = 1_000_000L,
            network = network
        )

        val clientEntry = SorobanAuthorizationEntryXdr(
            credentials = makeCredentials(buildAddressCredentials(createContractAddress(testClientContractId), 12346L)),
            rootInvocation = buildInvocation(argsMap)
        )

        return listOf(signedServerEntry, clientEntry)
    }

    // ============================================================================
    // (a) Legacy ADDRESS still verifies and signs as before
    // ============================================================================

    @Test
    fun testLegacyServerSignatureVerifies() = runTest {
        val webAuth = createWebAuth()
        val entries = buildSignedChallenge { SorobanCredentialsXdr.Address(it) }

        // The server entry uses ADDRESS credentials; verification must succeed.
        webAuth.validateChallenge(
            authEntries = entries,
            clientAccountId = testClientContractId,
            homeDomain = testHomeDomain
        )
    }

    @Test
    fun testLegacyClientSigningPreservesArmAndVerifies() = runTest {
        val webAuth = createWebAuth()
        val clientKeyPair = KeyPair.random()
        val clientAccountAsAddress = createAccountAddress(clientKeyPair.getAccountId())

        val argsMap = buildArgsMap(clientKeyPair.getAccountId(), KeyPair.fromSecretSeed(testServerSecretSeed).getAccountId())
        val clientEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(buildAddressCredentials(clientAccountAsAddress, 999L)),
            rootInvocation = buildInvocation(argsMap)
        )

        val signed = webAuth.signAuthorizationEntries(
            authEntries = listOf(clientEntry),
            clientAccountId = clientKeyPair.getAccountId(),
            signers = listOf(clientKeyPair),
            signatureExpirationLedger = 1_000_000L
        )

        val signedCredentials = signed[0].credentials
        assertTrue(signedCredentials is SorobanCredentialsXdr.Address, "Legacy arm must be preserved")

        // The legacy preimage (ENVELOPE_TYPE_SOROBAN_AUTHORIZATION) must validate the signature.
        val preimage = Auth.buildHashIDPreimage(
            credentials = signedCredentials,
            networkId = network.networkId(),
            invocation = signed[0].rootInvocation
        )
        assertTrue(preimage is HashIDPreimageXdr.SorobanAuthorization, "Legacy arm yields legacy preimage")
        assertSignatureVerifiesAgainst(signedCredentials, preimage, clientKeyPair)
    }

    // ============================================================================
    // (b) V2 entries verify against the WITH_ADDRESS preimage; arm preserved on signing
    // ============================================================================

    @Test
    fun testV2ServerSignatureVerifiesAgainstWithAddressPreimage() = runTest {
        val webAuth = createWebAuth()
        val entries = buildSignedChallenge { SorobanCredentialsXdr.AddressV2(it) }

        // The server entry uses ADDRESS_V2 credentials signed under the WITH_ADDRESS
        // preimage. verifyServerSignature must select the same arm and succeed.
        webAuth.validateChallenge(
            authEntries = entries,
            clientAccountId = testClientContractId,
            homeDomain = testHomeDomain
        )
    }

    @Test
    fun testV2ServerSignatureFailsWhenSignedWithLegacyPreimage() = runTest {
        val webAuth = createWebAuth()
        val serverKeyPair = KeyPair.fromSecretSeed(testServerSecretSeed)
        val argsMap = buildArgsMap(testClientContractId, serverKeyPair.getAccountId())
        val invocation = buildInvocation(argsMap)
        val serverAddress = createAccountAddress(serverKeyPair.getAccountId())
        val baseCredentials = buildAddressCredentials(serverAddress, 12345L)

        // Sign the LEGACY preimage but place the signature on ADDRESS_V2 credentials.
        // The verifier reconstructs the WITH_ADDRESS preimage for the V2 arm, so the
        // signature must not verify.
        val legacyPreimage = HashIDPreimageXdr.SorobanAuthorization(
            HashIDPreimageSorobanAuthorizationXdr(
                networkId = HashXdr(network.networkId()),
                nonce = baseCredentials.nonce,
                signatureExpirationLedger = baseCredentials.signatureExpirationLedger,
                invocation = invocation
            )
        )
        val writer = XdrWriter()
        legacyPreimage.encode(writer)
        val payload = Util.hash(writer.toByteArray())
        val wrongSignature = serverKeyPair.sign(payload)

        val signatureScVal = SCValXdr.Vec(
            SCVecXdr(
                listOf(
                    SCValXdr.Map(
                        SCMapXdr(
                            listOf(
                                SCMapEntryXdr(
                                    SCValXdr.Sym(SCSymbolXdr("public_key")),
                                    SCValXdr.Bytes(SCBytesXdr(serverKeyPair.getPublicKey()))
                                ),
                                SCMapEntryXdr(
                                    SCValXdr.Sym(SCSymbolXdr("signature")),
                                    SCValXdr.Bytes(SCBytesXdr(wrongSignature))
                                )
                            )
                        )
                    )
                )
            )
        )
        val serverEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.AddressV2(baseCredentials.copy(signature = signatureScVal)),
            rootInvocation = invocation
        )

        val clientEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.AddressV2(
                buildAddressCredentials(createContractAddress(testClientContractId), 12346L)
            ),
            rootInvocation = invocation
        )

        assertFailsWith<Sep45InvalidServerSignatureException> {
            webAuth.validateChallenge(
                authEntries = listOf(serverEntry, clientEntry),
                clientAccountId = testClientContractId,
                homeDomain = testHomeDomain
            )
        }
    }

    @Test
    fun testV2ClientSigningPreservesArmAndVerifies() = runTest {
        val webAuth = createWebAuth()
        val clientKeyPair = KeyPair.random()
        val clientAccountAsAddress = createAccountAddress(clientKeyPair.getAccountId())

        val argsMap = buildArgsMap(clientKeyPair.getAccountId(), KeyPair.fromSecretSeed(testServerSecretSeed).getAccountId())
        val clientEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.AddressV2(buildAddressCredentials(clientAccountAsAddress, 999L)),
            rootInvocation = buildInvocation(argsMap)
        )

        val signed = webAuth.signAuthorizationEntries(
            authEntries = listOf(clientEntry),
            clientAccountId = clientKeyPair.getAccountId(),
            signers = listOf(clientKeyPair),
            signatureExpirationLedger = 1_000_000L
        )

        val signedCredentials = signed[0].credentials
        assertTrue(signedCredentials is SorobanCredentialsXdr.AddressV2, "V2 arm must be preserved after signing")

        val preimage = Auth.buildHashIDPreimage(
            credentials = signedCredentials,
            networkId = network.networkId(),
            invocation = signed[0].rootInvocation
        )
        assertTrue(
            preimage is HashIDPreimageXdr.SorobanAuthorizationWithAddress,
            "V2 arm yields the address-bound preimage"
        )
        assertSignatureVerifiesAgainst(signedCredentials, preimage, clientKeyPair)
    }

    @Test
    fun testWithDelegatesClientDomainExpirationPreservesArm() = runTest {
        val webAuth = createWebAuth()
        val clientDomainKeyPair = KeyPair.random()
        val clientDomainAddress = createAccountAddress(clientDomainKeyPair.getAccountId())

        val argsMap = buildArgsMap(testClientContractId, KeyPair.fromSecretSeed(testServerSecretSeed).getAccountId())
        val baseCredentials = buildAddressCredentials(clientDomainAddress, 555L, expirationLedger = 1L)
        val withDelegates = SorobanCredentialsXdr.AddressWithDelegates(
            SorobanAddressCredentialsWithDelegatesXdr(
                addressCredentials = baseCredentials,
                delegates = emptyList()
            )
        )
        val clientDomainEntry = SorobanAuthorizationEntryXdr(
            credentials = withDelegates,
            rootInvocation = buildInvocation(argsMap)
        )

        // Remote signing via delegate: the expiration is updated before handing off; the
        // ADDRESS_WITH_DELEGATES arm and its (empty) delegate array must be preserved.
        var capturedArm: SorobanCredentialsXdr? = null
        val delegate = com.soneso.stellar.sdk.sep.sep45.Sep45ClientDomainSigningDelegate { entryXdr ->
            val reader = XdrReader(kotlin.io.encoding.Base64.decode(entryXdr))
            capturedArm = SorobanAuthorizationEntryXdr.decode(reader).credentials
            entryXdr
        }

        webAuth.signAuthorizationEntries(
            authEntries = listOf(clientDomainEntry),
            clientAccountId = testClientContractId,
            signers = emptyList(),
            signatureExpirationLedger = 2_000_000L,
            clientDomainAccountId = clientDomainKeyPair.getAccountId(),
            clientDomainSigningDelegate = delegate
        )

        val arm = capturedArm
        assertTrue(arm is SorobanCredentialsXdr.AddressWithDelegates, "Delegate arm must be preserved")
        assertEquals(
            2_000_000L.toUInt(),
            arm.value.addressCredentials.signatureExpirationLedger.value,
            "Expiration must be updated before delegate signing"
        )
        assertTrue(arm.value.delegates.isEmpty(), "Delegate array must be preserved")
    }

    // ============================================================================
    // (c) Void (source-account) credentials are passed through / skipped
    // ============================================================================

    @Test
    fun testVoidCredentialsPassedThroughOnSigning() = runTest {
        val webAuth = createWebAuth()
        val argsMap = buildArgsMap(testClientContractId, KeyPair.fromSecretSeed(testServerSecretSeed).getAccountId())
        val voidEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = buildInvocation(argsMap)
        )

        val signed = webAuth.signAuthorizationEntries(
            authEntries = listOf(voidEntry),
            clientAccountId = testClientContractId,
            signers = listOf(KeyPair.random()),
            signatureExpirationLedger = 1_000_000L
        )

        assertEquals(1, signed.size)
        assertTrue(signed[0].credentials is SorobanCredentialsXdr.Void, "Void credentials pass through unchanged")
        assertEquals(voidEntry, signed[0])
    }

    /**
     * Verifies that the first signature element in [credentials] is a valid signature by
     * [keyPair] over the hash of [preimage].
     */
    private suspend fun assertSignatureVerifiesAgainst(
        credentials: SorobanCredentialsXdr,
        preimage: HashIDPreimageXdr,
        keyPair: KeyPair
    ) {
        val addressCreds = when (credentials) {
            is SorobanCredentialsXdr.Address -> credentials.value
            is SorobanCredentialsXdr.AddressV2 -> credentials.value
            is SorobanCredentialsXdr.AddressWithDelegates -> credentials.value.addressCredentials
            is SorobanCredentialsXdr.Void -> throw IllegalStateException("Void has no signature")
        }
        val sigVec = addressCreds.signature as SCValXdr.Vec
        val firstSig = sigVec.value!!.value[0] as SCValXdr.Map
        var signatureBytes: ByteArray? = null
        for (mapEntry in firstSig.value!!.value) {
            val key = mapEntry.key
            if (key is SCValXdr.Sym && key.value.value == "signature") {
                signatureBytes = (mapEntry.`val` as SCValXdr.Bytes).value.value
            }
        }
        val writer = XdrWriter()
        preimage.encode(writer)
        val payload = Util.hash(writer.toByteArray())
        assertTrue(keyPair.verify(payload, signatureBytes!!), "Signature must verify against the arm-correct preimage")
    }
}
