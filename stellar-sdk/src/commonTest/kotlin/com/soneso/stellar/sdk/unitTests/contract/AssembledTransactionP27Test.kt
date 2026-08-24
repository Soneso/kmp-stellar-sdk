package com.soneso.stellar.sdk.unitTests.contract

import com.soneso.stellar.sdk.*
import com.soneso.stellar.sdk.contract.AssembledTransaction
import com.soneso.stellar.sdk.contract.ClientOptions
import com.soneso.stellar.sdk.contract.ContractClient
import com.soneso.stellar.sdk.rpc.SorobanServer
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

/**
 * Unit tests for Protocol 27 / CAP-71 behavior on the contract client surface:
 * the simulateTransaction useUpgradedAuth flag, and the three-arm auth handling in
 * AssembledTransaction.needsNonInvokerSigningBy and signAuthEntries.
 *
 * A method-dispatching Ktor MockEngine drives a real AssembledTransaction.simulate()
 * so that builtTransaction is populated with crafted auth entries (legacy ADDRESS,
 * ADDRESS_V2, and ADDRESS_WITH_DELEGATES). No live server is contacted.
 */
class AssembledTransactionP27Test {

    companion object {
        private const val SERVER_URL = "https://soroban-testnet.stellar.org:443"
        private val NETWORK = Network.TESTNET

        // Top-level signer of the auth entries under test.
        private const val SIGNER_SEED = "SDJHRQF4GCMIIKAAAQ6IHY42X73FQFLHUULAPSKKD4DFDM7UXWWCRHBE"
        private const val SIGNER_ACCOUNT = "GCZHXL5HXQX5ABDM26LHYRCQZ5OJFHLOPLZX47WEBP3V2PF5AVFK2A5D"

        // A distinct delegate signer; the account is the public key of DELEGATE_SEED so a
        // KeyPair built from the seed matches the delegate node address in the tree.
        private const val DELEGATE_SEED = "SAEZSI6DY7AXJFIYA4PM6SIBNEYYXIEM2MSOTHFGKHDW32MBQ7KVO6EN"
        private const val DELEGATE_ACCOUNT = "GBMLPRFCZDZJPKUPHUSHCKA737GOZL7ERZLGGMJ6YGHBFJZ6ZKMKCZTM"

        private const val CONTRACT_ID = "CA3D5KRYM6CB7OWQ6TWYRR3Z4T7GNZLKERYNZGGA5SOAOPIFY6YQGAXE"
        private const val NONCE = 123456789101112L
        private const val EXPIRATION = 4242L

        // Source account of the transaction (any valid account).
        private const val SOURCE_ACCOUNT = "GBRPYHIL2CI3FNQ4BXLFMNDLFJUNPU2HY3ZMFSHONUCEOASW7QC7OX2H"
        private const val SOURCE_SEQ = 100L
    }

    // ========================================================================
    // XDR fixtures
    // ========================================================================

    private fun invocation(): SorobanAuthorizedInvocationXdr =
        SorobanAuthorizedInvocationXdr(
            function = SorobanAuthorizedFunctionXdr.ContractFn(
                InvokeContractArgsXdr(
                    contractAddress = Address(CONTRACT_ID).toSCAddress(),
                    functionName = SCSymbolXdr("hello"),
                    args = listOf(Scv.toUint64(1234UL))
                )
            ),
            subInvocations = emptyList()
        )

    private fun baseCredentials(): SorobanAddressCredentialsXdr =
        SorobanAddressCredentialsXdr(
            address = Address(SIGNER_ACCOUNT).toSCAddress(),
            nonce = Int64Xdr(NONCE),
            signatureExpirationLedger = Uint32Xdr(EXPIRATION.toUInt()),
            signature = Scv.toVoid()
        )

    private fun legacyEntry(): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(baseCredentials()),
            rootInvocation = invocation()
        )

    private fun v2Entry(): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.AddressV2(baseCredentials()),
            rootInvocation = invocation()
        )

    /** A WITH_DELEGATES entry: top-level = SIGNER_ACCOUNT, one delegate = DELEGATE_ACCOUNT. */
    private fun withDelegatesEntry(): SorobanAuthorizationEntryXdr =
        Auth.attachDelegates(
            legacyEntry(),
            EXPIRATION,
            listOf(DelegateDescriptor(address = DELEGATE_ACCOUNT))
        )

    /**
     * A WITH_DELEGATES entry whose single delegate node carries the same address as
     * the top-level credential (SIGNER_ACCOUNT). The same address at different nesting
     * levels is legal under CAP-71.
     */
    private fun sameAddressTopAndDelegateEntry(): SorobanAuthorizationEntryXdr =
        Auth.attachDelegates(
            legacyEntry(),
            EXPIRATION,
            listOf(DelegateDescriptor(address = SIGNER_ACCOUNT))
        )

    /** Minimal but well-formed Soroban transaction data for the prepare step. */
    private fun minimalSorobanData(): SorobanTransactionDataXdr =
        SorobanTransactionDataXdr(
            ext = SorobanTransactionDataExtXdr.Void,
            resources = SorobanResourcesXdr(
                footprint = LedgerFootprintXdr(readOnly = emptyList(), readWrite = emptyList()),
                instructions = Uint32Xdr(0u),
                diskReadBytes = Uint32Xdr(0u),
                writeBytes = Uint32Xdr(0u)
            ),
            resourceFee = Int64Xdr(0L)
        )

    /** Base64 account ledger entry so getAccount() resolves a sequence number. */
    private fun accountEntryBase64(): String {
        val accountId = KeyPair.fromAccountId(SOURCE_ACCOUNT).getXdrAccountId()
        val entry = LedgerEntryDataXdr.Account(
            AccountEntryXdr(
                accountId = accountId,
                balance = Int64Xdr(10_000_000L),
                seqNum = SequenceNumberXdr(Int64Xdr(SOURCE_SEQ)),
                numSubEntries = Uint32Xdr(0u),
                inflationDest = null,
                flags = Uint32Xdr(0u),
                homeDomain = String32Xdr(""),
                thresholds = ThresholdsXdr(ByteArray(4)),
                signers = emptyList(),
                ext = AccountEntryExtXdr.Void
            )
        )
        return entry.toXdrBase64()
    }

    private fun simulateResultJson(authEntries: List<SorobanAuthorizationEntryXdr>): String {
        val authArray = authEntries.joinToString(",") { "\"${it.toXdrBase64()}\"" }
        val txData = minimalSorobanData().toXdrBase64()
        val returnXdr = Scv.toVoid().toXdrBase64()
        return """
            {
              "transactionData": "$txData",
              "minResourceFee": "100",
              "results": [ { "auth": [ $authArray ], "xdr": "$returnXdr" } ],
              "latestLedger": 14245
            }
        """.trimIndent()
    }

    private fun ledgerEntriesResultJson(): String {
        val entryXdr = accountEntryBase64()
        return """
            {
              "entries": [ { "key": "AAAAAA==", "xdr": "$entryXdr", "lastModifiedLedgerSeq": 1 } ],
              "latestLedger": 14245
            }
        """.trimIndent()
    }

    /**
     * Builds a MockEngine routing on the JSON-RPC method name. The simulate response
     * carries [authEntries]; [onSimulate] (when set) receives the captured simulate
     * request body for assertions.
     */
    private fun mockServer(
        authEntries: List<SorobanAuthorizationEntryXdr>,
        latestLedgerSeq: Long = 20000L,
        onSimulate: ((String) -> Unit)? = null
    ): SorobanServer {
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            val resultJson = when {
                "\"getLedgerEntries\"" in body -> ledgerEntriesResultJson()
                "\"simulateTransaction\"" in body -> {
                    onSimulate?.invoke(body)
                    simulateResultJson(authEntries)
                }
                "\"getLatestLedger\"" in body ->
                    """{ "id": "abc", "protocolVersion": 22, "sequence": $latestLedgerSeq, "closeTime": 1700000000, "headerXdr": "AA==", "metadataXdr": "AA==" }"""
                else -> "{}"
            }
            respond(
                content = ByteReadChannel(
                    """{ "jsonrpc": "2.0", "id": "1", "result": $resultJson }"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false })
            }
        }
        return SorobanServer(SERVER_URL, client)
    }

    private fun invokeBuilder(): TransactionBuilder =
        TransactionBuilder(
            sourceAccount = Account(SOURCE_ACCOUNT, SOURCE_SEQ),
            network = NETWORK
        )
            .addOperation(
                InvokeHostFunctionOperation.invokeContractFunction(
                    contractAddress = CONTRACT_ID,
                    functionName = "hello",
                    parameters = emptyList()
                )
            )
            .setTimeout(300L)
            .setBaseFee(100L)

    private fun assembled(
        server: SorobanServer,
        signer: KeyPair?,
        useUpgradedAuth: Boolean = true
    ): AssembledTransaction<SCValXdr> {
        return AssembledTransaction(
            server = server,
            submitTimeout = 30,
            transactionSigner = signer,
            parseResultXdrFn = null,
            transactionBuilder = invokeBuilder(),
            useUpgradedAuth = useUpgradedAuth
        )
    }

    private fun firstEntry(tx: AssembledTransaction<SCValXdr>): SorobanAuthorizationEntryXdr {
        val op = tx.builtTransaction!!.operations.first() as InvokeHostFunctionOperation
        return op.auth.first()
    }

    // ========================================================================
    // useUpgradedAuth JSON wire contract: key always present with the current value
    // ========================================================================

    @Test
    fun testUseUpgradedAuthExplicitFalsePresentAsFalse() = runTest {
        var captured: String? = null
        mockServer(listOf(legacyEntry()), onSimulate = { captured = it }).use { server ->
            assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED), useUpgradedAuth = false).simulate(restore = false)
        }
        assertNotNull(captured)
        assertTrue(
            "\"useUpgradedAuth\":false" in captured!!.replace(" ", ""),
            "explicit false must reach the server as the legacy opt-out"
        )
    }

    @Test
    fun testUseUpgradedAuthPresentAsTrueWhenSet() = runTest {
        var captured: String? = null
        mockServer(listOf(v2Entry()), onSimulate = { captured = it }).use { server ->
            assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED), useUpgradedAuth = true).simulate(restore = false)
        }
        assertNotNull(captured)
        assertTrue("\"useUpgradedAuth\":true" in captured!!.replace(" ", ""), "useUpgradedAuth must serialize as true")
    }

    @Test
    fun testAssembledTransactionDefaultSendsUseUpgradedAuthTrue() = runTest {
        // No useUpgradedAuth argument anywhere: the constructor default applies.
        var captured: String? = null
        mockServer(listOf(v2Entry()), onSimulate = { captured = it }).use { server ->
            AssembledTransaction<SCValXdr>(
                server = server,
                submitTimeout = 30,
                transactionSigner = KeyPair.fromSecretSeed(SIGNER_SEED),
                parseResultXdrFn = null,
                transactionBuilder = invokeBuilder()
            ).simulate(restore = false)
        }
        assertNotNull(captured)
        assertTrue(
            "\"useUpgradedAuth\":true" in captured!!.replace(" ", ""),
            "the default simulate request must carry useUpgradedAuth true"
        )
    }

    @Test
    fun testClientOptionsDefaultSendsUseUpgradedAuthTrue() = runTest {
        // ClientOptions built without useUpgradedAuth: the default-path simulate
        // request must carry the key as true.
        var captured: String? = null
        mockServer(listOf(v2Entry()), onSimulate = { captured = it }).use { server ->
            val client = ContractClient.forServer(CONTRACT_ID, SERVER_URL, NETWORK, server, contractSpec = null)
            val options = ClientOptions(
                sourceAccountKeyPair = KeyPair.fromAccountId(SOURCE_ACCOUNT),
                contractId = CONTRACT_ID,
                network = NETWORK,
                rpcUrl = SERVER_URL
            )
            client.buildInvoke(
                functionName = "hello",
                parameters = emptyList(),
                source = SOURCE_ACCOUNT,
                signer = KeyPair.fromSecretSeed(SIGNER_SEED),
                parseResultXdrFn = { it },
                options = options
            )
        }
        assertNotNull(captured)
        assertTrue(
            "\"useUpgradedAuth\":true" in captured!!.replace(" ", ""),
            "a simulate request with nothing set must carry useUpgradedAuth true"
        )
    }

    @Test
    fun testClientOptionsUseUpgradedAuthThreadsThroughBuildInvoke() = runTest {
        var captured: String? = null
        mockServer(listOf(legacyEntry()), onSimulate = { captured = it }).use { server ->
            val client = ContractClient.forServer(CONTRACT_ID, SERVER_URL, NETWORK, server, contractSpec = null)
            val options = ClientOptions(
                sourceAccountKeyPair = KeyPair.fromAccountId(SOURCE_ACCOUNT),
                contractId = CONTRACT_ID,
                network = NETWORK,
                rpcUrl = SERVER_URL,
                useUpgradedAuth = false
            )
            client.buildInvoke(
                functionName = "hello",
                parameters = emptyList(),
                source = SOURCE_ACCOUNT,
                signer = KeyPair.fromSecretSeed(SIGNER_SEED),
                parseResultXdrFn = { it },
                options = options
            )
        }
        assertNotNull(captured)
        assertTrue(
            "\"useUpgradedAuth\":false" in captured!!.replace(" ", ""),
            "ClientOptions.useUpgradedAuth must reach the simulate request through ContractClient"
        )
    }

    // ========================================================================
    // needsNonInvokerSigningBy: three arms + delegate walk
    // ========================================================================

    @Test
    fun testNeedsSigningReportsTopLevelForLegacyArm() = runTest {
        mockServer(listOf(legacyEntry())).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            assertEquals(setOf(SIGNER_ACCOUNT), tx.needsNonInvokerSigningBy())
        }
    }

    @Test
    fun testNeedsSigningReportsTopLevelForV2Arm() = runTest {
        mockServer(listOf(v2Entry())).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            assertEquals(setOf(SIGNER_ACCOUNT), tx.needsNonInvokerSigningBy())
        }
    }

    @Test
    fun testNeedsSigningReportsTopLevelAndDelegate() = runTest {
        mockServer(listOf(withDelegatesEntry())).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            // Top-level and the void delegate node are both reported.
            assertEquals(setOf(SIGNER_ACCOUNT, DELEGATE_ACCOUNT), tx.needsNonInvokerSigningBy())
        }
    }

    @Test
    fun testNeedsSigningDelegatesOnlyPatternDoesNotReportSignedDelegate() = runTest {
        // Sign only the delegate node; the top-level stays void. The fully-signed
        // delegate must not be reported; the still-void top-level must be.
        val signed = Auth.authorizeEntry(
            withDelegatesEntry(),
            KeyPair.fromSecretSeed(DELEGATE_SEED),
            EXPIRATION,
            NETWORK,
            Auth.AuthOptions(forAddress = DELEGATE_ACCOUNT)
        )
        mockServer(listOf(signed)).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            assertEquals(setOf(SIGNER_ACCOUNT), tx.needsNonInvokerSigningBy())
        }
    }

    @Test
    fun testNeedsSigningFullySignedDelegatesOnlyEntryReportsNothing() = runTest {
        // A WITH_DELEGATES entry is fully satisfied only when both the delegate node
        // and the top-level credential carry a signature. Sign both, then expect
        // nothing needed (and both nodes reported when includeAlreadySigned is set).
        var entry = Auth.authorizeEntry(
            withDelegatesEntry(),
            KeyPair.fromSecretSeed(DELEGATE_SEED),
            EXPIRATION,
            NETWORK,
            Auth.AuthOptions(forAddress = DELEGATE_ACCOUNT)
        )
        entry = Auth.authorizeEntry(entry, KeyPair.fromSecretSeed(SIGNER_SEED), EXPIRATION, NETWORK)
        mockServer(listOf(entry)).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            assertTrue(tx.needsNonInvokerSigningBy().isEmpty())
            // includeAlreadySigned still reports both nodes.
            assertEquals(
                setOf(SIGNER_ACCOUNT, DELEGATE_ACCOUNT),
                tx.needsNonInvokerSigningBy(includeAlreadySigned = true)
            )
        }
    }

    @Test
    fun testNeedsSigningSourceAccountContributesNothing() = runTest {
        val sourceEntry = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Void,
            rootInvocation = invocation()
        )
        mockServer(listOf(sourceEntry)).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            assertTrue(tx.needsNonInvokerSigningBy().isEmpty())
        }
    }

    // ========================================================================
    // signAuthEntries: arm preservation
    // ========================================================================

    @Test
    fun testSignAuthEntriesPreservesV2Arm() = runTest {
        mockServer(listOf(v2Entry())).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            tx.signAuthEntries(KeyPair.fromSecretSeed(SIGNER_SEED), validUntilLedgerSequence = EXPIRATION)
            val creds = firstEntry(tx).credentials
            assertTrue(creds is SorobanCredentialsXdr.AddressV2, "re-signing a V2 entry must stay V2")
            assertTrue(creds.value.signature is SCValXdr.Vec)
        }
    }

    @Test
    fun testSignAuthEntriesPreservesWithDelegatesArm() = runTest {
        mockServer(listOf(withDelegatesEntry())).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            tx.signAuthEntries(KeyPair.fromSecretSeed(SIGNER_SEED), validUntilLedgerSequence = EXPIRATION)
            val creds = firstEntry(tx).credentials
            assertTrue(creds is SorobanCredentialsXdr.AddressWithDelegates, "must stay WITH_DELEGATES")
            // Signing the top-level signer fills the top-level signature.
            assertTrue(creds.value.addressCredentials.signature is SCValXdr.Vec)
        }
    }

    // ========================================================================
    // signAuthEntries: delegate-signer routing
    // ========================================================================

    @Test
    fun testSignAuthEntriesRoutesDelegateSignerIntoDelegateNode() = runTest {
        mockServer(listOf(withDelegatesEntry())).use { server ->
            // Signer is the delegate, NOT the top-level address.
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            tx.signAuthEntries(KeyPair.fromSecretSeed(DELEGATE_SEED), validUntilLedgerSequence = EXPIRATION)

            val creds = firstEntry(tx).credentials as SorobanCredentialsXdr.AddressWithDelegates
            // Top-level stays void; the delegate node carries the signature.
            assertTrue(creds.value.addressCredentials.signature is SCValXdr.Void)
            val delegate = creds.value.delegates.first()
            assertTrue(delegate.signature is SCValXdr.Vec)
        }
    }

    @Test
    fun testSignAuthEntriesTopLevelSignerLeavesDelegateNodeVoid() = runTest {
        mockServer(listOf(withDelegatesEntry())).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            tx.signAuthEntries(KeyPair.fromSecretSeed(SIGNER_SEED), validUntilLedgerSequence = EXPIRATION)

            val creds = firstEntry(tx).credentials as SorobanCredentialsXdr.AddressWithDelegates
            assertTrue(creds.value.addressCredentials.signature is SCValXdr.Vec)
            assertTrue(creds.value.delegates.first().signature is SCValXdr.Void)
        }
    }

    @Test
    fun testSignAuthEntriesSignsBothWhenSignerIsTopLevelAndDelegate() = runTest {
        // The signer address is both the top-level credential and a delegate node.
        // A single signing pass must fill both signatures, not just the top level.
        mockServer(listOf(sameAddressTopAndDelegateEntry())).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            // Both nodes share one address, so it is reported once.
            assertEquals(setOf(SIGNER_ACCOUNT), tx.needsNonInvokerSigningBy())

            tx.signAuthEntries(KeyPair.fromSecretSeed(SIGNER_SEED), validUntilLedgerSequence = EXPIRATION)

            val creds = firstEntry(tx).credentials as SorobanCredentialsXdr.AddressWithDelegates
            assertTrue(creds.value.addressCredentials.signature is SCValXdr.Vec, "top-level must be signed")
            assertTrue(creds.value.delegates.first().signature is SCValXdr.Vec, "matching delegate node must be signed")
            // Fully satisfied: nothing left to sign.
            assertTrue(tx.needsNonInvokerSigningBy().isEmpty())
        }
    }

    @Test
    fun testSignAuthEntriesUnrelatedSignerThrows() = runTest {
        // Signer matches neither the top-level nor any delegate; without a delegate
        // callback the validation rejects the signer up front.
        val unrelated = KeyPair.random()
        mockServer(listOf(legacyEntry())).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            assertFailsWith<IllegalStateException> {
                tx.signAuthEntries(unrelated, validUntilLedgerSequence = EXPIRATION)
            }
        }
    }

    @Test
    fun testSignAuthEntriesDefaultExpirationUsesLatestLedgerPlus100() = runTest {
        mockServer(listOf(v2Entry()), latestLedgerSeq = 20000L).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            tx.signAuthEntries(KeyPair.fromSecretSeed(SIGNER_SEED))
            val creds = firstEntry(tx).credentials as SorobanCredentialsXdr.AddressV2
            assertEquals(20100u, creds.value.signatureExpirationLedger.value)
        }
    }

    // ========================================================================
    // signAuthEntries: authorizeEntryDelegate callback contract
    // ========================================================================

    /** A legacy ADDRESS entry whose inner expiration starts at 0. */
    private fun legacyEntryWithZeroExpiration(): SorobanAuthorizationEntryXdr =
        SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(
                SorobanAddressCredentialsXdr(
                    address = Address(SIGNER_ACCOUNT).toSCAddress(),
                    nonce = Int64Xdr(NONCE),
                    signatureExpirationLedger = Uint32Xdr(0u),
                    signature = Scv.toVoid()
                )
            ),
            rootInvocation = invocation()
        )

    @Test
    fun testSignAuthEntriesCallbackReceivesStampedExpiration() = runTest {
        // The delegate must receive the entry with the requested expiration already
        // stamped onto its inner credentials. If the stamp happened after the callback,
        // the network would reconstruct a different preimage and reject the signature.
        var captured: SorobanAuthorizationEntryXdr? = null
        mockServer(listOf(legacyEntryWithZeroExpiration())).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            tx.signAuthEntries(
                authEntriesSigner = KeyPair.fromSecretSeed(SIGNER_SEED),
                validUntilLedgerSequence = 1_000_000L,
                authorizeEntryDelegate = { entry, _ ->
                    captured = entry
                    entry
                }
            )
        }
        assertNotNull(captured, "delegate must be invoked for the matching entry")
        val capturedCreds = (captured!!.credentials as SorobanCredentialsXdr.Address).value
        // The expiration the delegate sees must be the requested value, not the original 0.
        assertEquals(
            1_000_000u,
            capturedCreds.signatureExpirationLedger.value,
            "delegate must receive the entry with the expiration already stamped"
        )
    }

    @Test
    fun testSignAuthEntriesCallbackDoesNotModifySiblingSignedByAnotherParty() = runTest {
        // Two distinct entries: A is addressed to and already signed by signerA; B is
        // addressed to signerB and void. signAuthEntries(signerB, delegate) must hand
        // the delegate only B, leaving A's already-present signature byte-identical.
        val signerA = KeyPair.fromSecretSeed(SIGNER_SEED)
        val signerB = KeyPair.fromSecretSeed(DELEGATE_SEED)

        // Entry A: legacy ADDRESS for signerA, pre-signed so its signature is non-void.
        val entryAUnsigned = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(
                SorobanAddressCredentialsXdr(
                    address = Address(SIGNER_ACCOUNT).toSCAddress(),
                    nonce = Int64Xdr(NONCE),
                    signatureExpirationLedger = Uint32Xdr(EXPIRATION.toUInt()),
                    signature = Scv.toVoid()
                )
            ),
            rootInvocation = invocation()
        )
        val entryA = Auth.authorizeEntry(entryAUnsigned, signerA, EXPIRATION, NETWORK)
        // Sanity: A is genuinely signed (non-void) going in.
        assertTrue(
            (entryA.credentials as SorobanCredentialsXdr.Address).value.signature is SCValXdr.Vec,
            "precondition: entry A must already carry a signature"
        )
        val entryABefore = entryA.toXdrBase64()

        // Entry B: legacy ADDRESS for signerB, void.
        val entryB = SorobanAuthorizationEntryXdr(
            credentials = SorobanCredentialsXdr.Address(
                SorobanAddressCredentialsXdr(
                    address = Address(DELEGATE_ACCOUNT).toSCAddress(),
                    nonce = Int64Xdr(NONCE + 1),
                    signatureExpirationLedger = Uint32Xdr(EXPIRATION.toUInt()),
                    signature = Scv.toVoid()
                )
            ),
            rootInvocation = invocation()
        )

        var delegateCallCount = 0
        mockServer(listOf(entryA, entryB)).use { server ->
            val tx = assembled(server, KeyPair.fromSecretSeed(SIGNER_SEED)).simulate(restore = false)
            tx.signAuthEntries(
                authEntriesSigner = signerB,
                validUntilLedgerSequence = EXPIRATION,
                authorizeEntryDelegate = { e, _ ->
                    delegateCallCount++
                    Auth.authorizeEntry(e, signerB, EXPIRATION, NETWORK)
                }
            )

            val op = tx.builtTransaction!!.operations.first() as InvokeHostFunctionOperation
            val entryAAfter = op.auth.first().toXdrBase64()
            // The sibling signed by another party is untouched, byte for byte.
            assertEquals(
                entryABefore,
                entryAAfter,
                "entry A signed by another party must be byte-identical after signing B"
            )
            // The delegate fired exactly once: only the matching entry B was handed to it.
            assertEquals(1, delegateCallCount, "delegate must fire exactly once, for entry B only")
        }
    }
}
