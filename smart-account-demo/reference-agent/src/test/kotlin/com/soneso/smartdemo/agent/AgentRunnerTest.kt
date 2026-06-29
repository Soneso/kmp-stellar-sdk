package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.TransactionException
import com.soneso.stellar.sdk.smartaccount.oz.OZTransactionOperations
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.TransactionResult
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// MARK: - Mocks

private class FakeWalletSession(private val contractId: String) : WalletSession {
    var connectCount = 0
        private set

    override suspend fun connect(): String {
        connectCount += 1
        return contractId
    }
}

private class FakeContractCall private constructor(
    private val result: TransactionResult?,
    private val error: Throwable?,
    private val onCall: (() -> Unit)?,
) : MultiSignerContractCall {
    var callCount = 0
        private set
    var lastTarget: String? = null
        private set
    var lastTargetFn: String? = null
        private set
    var lastArgs: List<SCValXdr>? = null
        private set
    var lastSelectedSigners: List<SelectedSigner>? = null
        private set

    constructor(result: TransactionResult, onCall: (() -> Unit)? = null) : this(result, null, onCall)
    constructor(error: Throwable) : this(null, error, null)

    override suspend fun multiSignerContractCall(
        target: String,
        targetFn: String,
        targetArgs: List<SCValXdr>,
        selectedSigners: List<SelectedSigner>,
    ): TransactionResult {
        callCount += 1
        lastTarget = target
        lastTargetFn = targetFn
        lastArgs = targetArgs
        lastSelectedSigners = selectedSigners
        // Probe the adapter at the exact moment of the call so a test can assert
        // the agent seed was registered before the multi-signer pipeline ran.
        onCall?.invoke()
        error?.let { throw it }
        return result!!
    }
}

private class FakeCoordinationClient(private val pollResponses: List<CoordinationRequest>) : CoordinationClient {
    var getCount = 0
        private set
    var createArgs: List<String>? = null
        private set
    var createSmartAccount: String? = null
        private set
    var createTarget: String? = null
        private set
    var createTargetFn: String? = null
        private set
    var createAmount: String? = null
        private set
    var createReason: Int? = null
        private set

    override suspend fun createRequest(
        smartAccount: String,
        target: String,
        targetFn: String,
        args: List<String>,
        amount: String?,
        reason: Int,
    ): CoordinationRequest {
        createSmartAccount = smartAccount
        createTarget = target
        createTargetFn = targetFn
        createArgs = args
        createAmount = amount
        createReason = reason
        return CoordinationRequest(
            id = "req-1", smartAccount = smartAccount, target = target, targetFn = targetFn,
            args = args, amount = amount ?: "", reason = reason,
            status = CoordinationRequest.STATUS_PENDING, createdAt = 1,
        )
    }

    override suspend fun getRequest(id: String): CoordinationRequest {
        val index = if (getCount < pollResponses.size) getCount else pollResponses.size - 1
        getCount += 1
        return pollResponses[index]
    }
}

/** Throws on the configured 1-based poll attempts, returning [resolved] otherwise. */
private class FlakyCoordinationClient(
    private val throwOnAttempts: Set<Int>,
    private val resolved: CoordinationRequest,
) : CoordinationClient {
    var getCount = 0
        private set

    override suspend fun createRequest(
        smartAccount: String,
        target: String,
        targetFn: String,
        args: List<String>,
        amount: String?,
        reason: Int,
    ): CoordinationRequest = CoordinationRequest(
        id = "req-1", smartAccount = smartAccount, target = target, targetFn = targetFn,
        args = args, amount = amount ?: "", reason = reason,
        status = CoordinationRequest.STATUS_PENDING, createdAt = 1,
    )

    override suspend fun getRequest(id: String): CoordinationRequest {
        getCount += 1
        if (getCount in throwOnAttempts) {
            throw CoordinationException("transient network failure while polling")
        }
        return resolved
    }
}

private class RecordingLogger : AgentLogger {
    val messages = mutableListOf<String>()
    override fun info(message: String) { messages.add("INFO:$message") }
    override fun success(message: String) { messages.add("OK:$message") }
    override fun error(message: String) { messages.add("ERROR:$message") }
}

// MARK: - Tests

class AgentRunnerTest {

    private val smartAccount = "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC"
    private val ed25519Verifier = AgentDefaults.ED25519_VERIFIER_ADDRESS
    private val seed = ByteArray(32) { 1 }

    private fun publicKey(): ByteArray = publicKeyFor(seed)

    private fun buildConfig(destination: String): AgentConfig = AgentConfig(
        tokenContractId = AgentDefaults.NATIVE_TOKEN_CONTRACT,
        amount = "5",
        smartAccountContractId = smartAccount,
        agentSecretSeed = "01".repeat(32),
        destinationAddress = destination,
        pollIntervalSeconds = 0,
        pollMaxAttempts = 5,
    )

    private fun buildRunner(
        config: AgentConfig,
        contractCall: MultiSignerContractCall,
        coordination: CoordinationClient,
        session: WalletSession,
        logger: AgentLogger = RecordingLogger(),
        adapter: AgentEd25519Adapter = AgentEd25519Adapter(),
    ): AgentRunner = AgentRunner(
        config = config,
        session = session,
        contractCall = contractCall,
        coordination = coordination,
        signerAdapter = adapter,
        agentPublicKey = publicKey(),
        agentSeed = seed,
        logger = logger,
        sleep = { },
    )

    @OptIn(ExperimentalEncodingApi::class)
    private fun encode(arg: SCValXdr): String =
        Base64.encode(XdrWriter().also { arg.encode(it) }.toByteArray())

    @Test
    fun successfulScopedCallReturnsCallSucceededWithTheHash() = runBlocking {
        val destination = randomGAddress()
        val adapter = AgentEd25519Adapter()
        val pk = publicKey()
        // Snapshot the adapter's signing capability at the instant the scoped call
        // is submitted. This proves the runner registered the agent seed (via
        // signerAdapter.add) before the multi-signer pipeline ran; if that
        // registration were dropped, the snapshot would be false and this test
        // would fail rather than passing on the after-the-fact clear assertion.
        var canSignAtCallTime: Boolean? = null
        val contractCall = FakeContractCall(TransactionResult(success = true, hash = "TXHASH123")) {
            canSignAtCallTime = adapter.canSignFor(ed25519Verifier, pk)
        }
        val coordination = FakeCoordinationClient(emptyList())
        val session = FakeWalletSession(smartAccount)

        val result = buildRunner(buildConfig(destination), contractCall, coordination, session, adapter = adapter).run()

        assertEquals(AgentResult.CallSucceeded("TXHASH123"), result)
        assertEquals(1, session.connectCount)
        assertEquals(1, contractCall.callCount)

        // The seed was registered on the adapter before the call was submitted.
        assertEquals(true, canSignAtCallTime)

        val signers = assertNotNull(contractCall.lastSelectedSigners)
        assertEquals(1, signers.size)
        val signer = signers.first()
        assertTrue(signer is SelectedSigner.Ed25519)
        assertEquals(ed25519Verifier, signer.verifierAddress)
        assertTrue(signer.publicKey.contentEquals(publicKey()))
        assertEquals("transfer", contractCall.lastTargetFn)
        assertEquals(AgentDefaults.NATIVE_TOKEN_CONTRACT, contractCall.lastTarget)

        // The call carried the exact transfer(from, to, amount) vector: source is
        // the connected smart account, destination is the configured recipient, and
        // the amount is "5" scaled by the default 7 decimals -> 50_000_000 base units.
        val expectedArgs = listOf(
            Scv.toAddress(Address(smartAccount).toSCAddress()),
            Scv.toAddress(Address(destination).toSCAddress()),
            Scv.toInt128(OZTransactionOperations.amountToBaseUnits("5", 7)),
        ).map { encode(it) }
        assertEquals(expectedArgs, assertNotNull(contractCall.lastArgs).map { encode(it) })

        // No escalation occurred.
        assertNull(coordination.createArgs)
        assertEquals(0, coordination.getCount)

        // The adapter copy is cleared after the attempt.
        assertTrue(!adapter.canSignFor(ed25519Verifier, publicKey()))
    }

    @Test
    fun logsTheAgentPublicKeyAsRaw64CharHexOnStartup() = runBlocking {
        val logger = RecordingLogger()
        buildRunner(
            buildConfig(randomGAddress()),
            FakeContractCall(TransactionResult(success = true, hash = "TXHASH123")),
            FakeCoordinationClient(emptyList()),
            FakeWalletSession(smartAccount),
            logger = logger,
        ).run()

        val expectedHex = Hex.encode(publicKey())
        val startupLine = assertNotNull(logger.messages.firstOrNull { it.contains("Delegate-to-agent") })
        assertTrue(startupLine.contains(expectedHex))
        assertEquals(64, expectedHex.length)
    }

    @Test
    fun nonPolicyFailureReturnsCallFailedWithoutEscalating() = runBlocking {
        val contractCall = FakeContractCall(TransactionResult(success = false, error = "RPC endpoint unreachable"))
        val coordination = FakeCoordinationClient(emptyList())

        val result = buildRunner(buildConfig(randomGAddress()), contractCall, coordination, FakeWalletSession(smartAccount)).run()

        assertTrue(result is AgentResult.CallFailed)
        assertTrue(result.message.contains("unreachable"))
        assertNull(coordination.createArgs)
    }

    @Test
    fun aNonPolicyContractCodeFailsWithoutEscalating() = runBlocking {
        // 3016 (UNAUTHORIZED_SIGNER) is a contract error code but not a policy
        // denial, so the runner reports a failure and never escalates.
        val contractCall = FakeContractCall(TransactionResult(success = false, error = "HostError: Error(Contract, #3016)"))
        val coordination = FakeCoordinationClient(emptyList())

        val result = buildRunner(buildConfig(randomGAddress()), contractCall, coordination, FakeWalletSession(smartAccount)).run()

        assertTrue(result is AgentResult.CallFailed)
        assertTrue(result.message.contains("#3016"))
        assertNull(coordination.createArgs)
        assertEquals(0, coordination.getCount)
    }

    @Test
    fun policyRejectionEscalatesAndReturnsApprovedWithTheResultHash() = runBlocking {
        val destination = randomGAddress()
        val contractCall = FakeContractCall(TransactionResult(success = false, error = "HostError: Error(Contract, #3221)"))
        val approved = CoordinationRequest(
            id = "req-1", smartAccount = smartAccount, target = smartAccount, targetFn = "transfer",
            args = emptyList(), amount = "5", reason = 3221, status = CoordinationRequest.STATUS_APPROVED,
            createdAt = 1, resolvedAt = 2, resultHash = "RESOLVEDHASH",
        )
        val pending = CoordinationRequest(
            id = "req-1", smartAccount = smartAccount, target = smartAccount, targetFn = "transfer",
            args = emptyList(), amount = "5", reason = 3221, status = CoordinationRequest.STATUS_PENDING, createdAt = 1,
        )
        val coordination = FakeCoordinationClient(listOf(pending, approved))
        val config = buildConfig(destination)

        val result = buildRunner(config, contractCall, coordination, FakeWalletSession(smartAccount)).run()

        assertEquals(AgentResult.EscalationApproved("req-1", "RESOLVEDHASH", 3221), result)
        assertEquals(2, coordination.getCount)

        assertEquals(smartAccount, coordination.createSmartAccount)
        assertEquals(config.tokenContractId, coordination.createTarget)
        assertEquals("transfer", coordination.createTargetFn)
        assertEquals(3221, coordination.createReason)
        assertEquals("5", coordination.createAmount)

        // The escalated args are the exact base64 SCVal call args (from, to, amount):
        // source is the smart account, destination is the configured recipient, and the
        // amount is "5" scaled by 7 decimals -> 50_000_000 base units.
        val expectedArgs = listOf(
            Scv.toAddress(Address(smartAccount).toSCAddress()),
            Scv.toAddress(Address(destination).toSCAddress()),
            Scv.toInt128(OZTransactionOperations.amountToBaseUnits("5", 7)),
        ).map { encode(it) }
        assertEquals(expectedArgs, coordination.createArgs)
        // from and to are distinct addresses.
        assertTrue(coordination.createArgs!![0] != coordination.createArgs!![1])
    }

    @Test
    fun escalationWithPollMaxAttemptsZeroReturnsPendingWithoutPollingOrTrapping() = runBlocking {
        val contractCall = FakeContractCall(TransactionResult(success = false, error = "Error(Contract, #3221)"))
        val coordination = FakeCoordinationClient(emptyList())
        val config = buildConfig(randomGAddress()).copy(pollMaxAttempts = 0)

        val result = buildRunner(config, contractCall, coordination, FakeWalletSession(smartAccount)).run()

        assertEquals(AgentResult.EscalationPending("req-1", 3221, 0), result)
        assertNotNull(coordination.createArgs)
        assertEquals(0, coordination.getCount)
    }

    @Test
    fun policyRejectionEscalatesAndReturnsRejectedWithTheNote() = runBlocking {
        // Exercise the thrown-error classification path.
        val contractCall = FakeContractCall(TransactionException.simulationFailed("Error(Contract, #3221)"))
        val rejected = CoordinationRequest(
            id = "req-1", smartAccount = smartAccount, target = smartAccount, targetFn = "transfer",
            args = emptyList(), amount = "5", reason = 3221, status = CoordinationRequest.STATUS_REJECTED,
            createdAt = 1, resolvedAt = 2, note = "looks malicious",
        )
        val coordination = FakeCoordinationClient(listOf(rejected))

        val result = buildRunner(buildConfig(randomGAddress()), contractCall, coordination, FakeWalletSession(smartAccount)).run()

        assertEquals(AgentResult.EscalationRejected("req-1", 3221, "looks malicious"), result)
    }

    @Test
    fun aTransientPollErrorIsToleratedAndPollingContinuesUntilResolution() = runBlocking {
        val contractCall = FakeContractCall(TransactionResult(success = false, error = "Error(Contract, #3221)"))
        val approved = CoordinationRequest(
            id = "req-1", smartAccount = smartAccount, target = smartAccount, targetFn = "transfer",
            args = emptyList(), amount = "5", reason = 3221, status = CoordinationRequest.STATUS_APPROVED,
            createdAt = 1, resolvedAt = 2, resultHash = "RESOLVEDHASH",
        )
        // First poll attempt throws a transient error; the second resolves.
        val coordination = FlakyCoordinationClient(throwOnAttempts = setOf(1), resolved = approved)

        val result = buildRunner(buildConfig(randomGAddress()), contractCall, coordination, FakeWalletSession(smartAccount)).run()

        assertEquals(AgentResult.EscalationApproved("req-1", "RESOLVEDHASH", 3221), result)
        assertEquals(2, coordination.getCount)
    }

    @Test
    fun escalationThatNeverResolvesReturnsEscalationPending() = runBlocking {
        val contractCall = FakeContractCall(TransactionResult(success = false, error = "Error(Contract, #3221)"))
        val pending = CoordinationRequest(
            id = "req-1", smartAccount = smartAccount, target = smartAccount, targetFn = "transfer",
            args = emptyList(), amount = "5", reason = 3221, status = CoordinationRequest.STATUS_PENDING, createdAt = 1,
        )
        val coordination = FakeCoordinationClient(listOf(pending))

        val result = buildRunner(buildConfig(randomGAddress()), contractCall, coordination, FakeWalletSession(smartAccount)).run()

        assertEquals(AgentResult.EscalationPending("req-1", 3221, 5), result)
        assertEquals(5, coordination.getCount)
    }
}
