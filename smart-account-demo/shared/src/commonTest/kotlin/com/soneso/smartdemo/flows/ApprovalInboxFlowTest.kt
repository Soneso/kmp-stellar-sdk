package com.soneso.smartdemo.flows

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.util.CoordinationClient
import com.soneso.smartdemo.util.CoordinationException
import com.soneso.smartdemo.util.CoordinationRequest
import com.soneso.smartdemo.util.truncateAddress
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.StrKey
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ApprovalInboxFlow], the inbox-side business logic (steps 4 + 5).
 *
 * Covers the authoritative-consent decoding (decoded from the call args, never the
 * display amount), the account-scope and still-pending guards before submission, the
 * exact-call re-submission + report-back, the durable confirmed-hash guard with retry,
 * and rejection.
 */
class ApprovalInboxFlowTest {

    private companion object {
        // Valid G-addresses derived from fixed raw public keys (deterministic, checksummed).
        val ACCOUNT = StrKey.encodeEd25519PublicKey(ByteArray(32) { 1 })
        val OTHER_ACCOUNT = StrKey.encodeEd25519PublicKey(ByteArray(32) { 2 })
        val RECIPIENT = StrKey.encodeEd25519PublicKey(ByteArray(32) { 3 })
        val TOKEN = DemoConfig.NATIVE_TOKEN_CONTRACT
    }

    // -------------------------------------------------------------------------
    // SCVal encoding helpers (mirror the reference agent's transfer-arg encoding)
    // -------------------------------------------------------------------------

    @OptIn(ExperimentalEncodingApi::class)
    private fun encode(value: SCValXdr): String =
        Base64.encode(XdrWriter().also { value.encode(it) }.toByteArray())

    private fun addressArg(strKey: String): SCValXdr =
        Scv.toAddress(Address(strKey).toSCAddress())

    private fun i128Arg(value: Long): SCValXdr = Scv.toInt128(BigInteger.fromLong(value))

    private fun transferArgs(from: String, to: String, amount: Long): List<String> =
        listOf(encode(addressArg(from)), encode(addressArg(to)), encode(i128Arg(amount)))

    private fun approveArgs(from: String, spender: String, amount: Long, expiry: UInt): List<String> =
        listOf(
            encode(addressArg(from)),
            encode(addressArg(spender)),
            encode(i128Arg(amount)),
            encode(Scv.toUint32(expiry)),
        )

    private fun request(
        id: String = "req-1",
        smartAccount: String = ACCOUNT,
        target: String = TOKEN,
        targetFn: String = "transfer",
        args: List<String> = transferArgs(ACCOUNT, RECIPIENT, 105_000_000L),
        amount: String = "999",
        reason: Int = 3017,
        status: String = CoordinationRequest.STATUS_PENDING,
    ): CoordinationRequest = CoordinationRequest(
        id = id,
        smartAccount = smartAccount,
        target = target,
        targetFn = targetFn,
        args = args,
        amount = amount,
        reason = reason,
        status = status,
        createdAt = 1L,
    )

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    private class FakeCoordination(
        var pending: List<CoordinationRequest> = emptyList(),
    ) : CoordinationClient {
        var getResult: (String) -> CoordinationRequest = { error("getRequest not configured") }
        var approveBehavior: (String, String) -> CoordinationRequest =
            { id, hash -> CoordinationRequest(id, "C", "C", "transfer", emptyList(), "", 0, "approved", 0L, resultHash = hash) }
        val approveCalls = mutableListOf<Pair<String, String>>()
        val rejectCalls = mutableListOf<Pair<String, String?>>()

        override suspend fun listPending(): List<CoordinationRequest> = pending
        override suspend fun getRequest(id: String): CoordinationRequest = getResult(id)
        override suspend fun approve(id: String, resultHash: String): CoordinationRequest {
            approveCalls.add(id to resultHash)
            return approveBehavior(id, resultHash)
        }
        override suspend fun reject(id: String, note: String?): CoordinationRequest {
            rejectCalls.add(id to note)
            return CoordinationRequest(id, "C", "C", "transfer", emptyList(), "", 0, "rejected", 0L, note = note)
        }
        override fun close() {}
    }

    private class FakeSubmitter(
        var outcome: ContractCallOutcome = ContractCallOutcome(true, "TXHASH", null),
        var throwable: Throwable? = null,
        private val reachedSubmit: CompletableDeferred<Unit>? = null,
        private val release: CompletableDeferred<Unit>? = null,
    ) : ContractCallSubmitter {
        var calls = 0
        var lastArgs: List<SCValXdr>? = null
        override suspend fun contractCall(
            target: String,
            targetFn: String,
            targetArgs: List<SCValXdr>,
        ): ContractCallOutcome {
            calls++
            lastArgs = targetArgs
            // Park here (when wired) so a concurrent second submission can be observed.
            reachedSubmit?.complete(Unit)
            release?.await()
            throwable?.let { throw it }
            return outcome
        }
    }

    private class InMemoryHashStore : ConfirmedHashStore {
        private val map = mutableMapOf<String, String>()
        override fun get(requestId: String): String? = map[requestId]
        override fun put(requestId: String, hash: String) { map[requestId] = hash }
        override fun remove(requestId: String) { map.remove(requestId) }
    }

    private fun flow(
        coordination: FakeCoordination,
        submitter: ContractCallSubmitter? = FakeSubmitter(),
        connectedAccount: String? = ACCOUNT,
        store: ConfirmedHashStore = InMemoryHashStore(),
        resolveDecimals: suspend (String) -> Int = { 7 },
    ): ApprovalInboxFlow = ApprovalInboxFlow(
        coordination = coordination,
        resolveContractCall = { submitter },
        resolveConnectedAccount = { connectedAccount },
        confirmedHashStore = store,
        resolveDecimals = resolveDecimals,
    )

    // -------------------------------------------------------------------------
    // Decode
    // -------------------------------------------------------------------------

    @Test
    fun decodeTransferDerivesRecipientAndAmountFromArgsNotDisplayAmount() = runTest {
        val f = flow(FakeCoordination())
        val decoded = f.decodeCall(request(amount = "1.0"))
        assertEquals(DecodedCallKind.TRANSFER, decoded.kind)
        assertEquals(RECIPIENT, decoded.recipient)
        assertEquals("Recipient", decoded.recipientLabel)
        // 105_000_000 base units at 7 decimals = 10.5, NOT the display amount "1.0".
        assertEquals("10.5", decoded.amount)
        assertEquals(BigInteger.fromLong(105_000_000L), decoded.amountBaseUnits)
    }

    @Test
    fun decodeFormatsAmountAtTheResolvedTokenDecimalsNotAFixedScale() = runTest {
        // A 6-decimal token: 105_000_000 base units = 105.0, not the 10.5 a fixed 7-decimal
        // scale would show. The displayed amount must follow the request token's true scale.
        val f = flow(FakeCoordination(), resolveDecimals = { 6 })
        val decoded = f.decodeCall(request())
        assertEquals(DecodedCallKind.TRANSFER, decoded.kind)
        assertEquals("105", decoded.amount)
    }

    @Test
    fun decodeShowsRawBaseUnitsWhenTokenDecimalsCannotBeResolved() = runTest {
        // When the token's decimals() cannot be read the amount is labelled raw base units
        // rather than formatted at a guessed scale, so it cannot silently misrepresent.
        val f = flow(FakeCoordination(), resolveDecimals = { error("rpc down") })
        val decoded = f.decodeCall(request())
        assertEquals(DecodedCallKind.TRANSFER, decoded.kind)
        assertEquals("105000000 base units", decoded.amount)
        assertEquals(BigInteger.fromLong(105_000_000L), decoded.amountBaseUnits)
    }

    @Test
    fun decodeApproveUsesSpenderLabel() = runTest {
        val f = flow(FakeCoordination())
        val req = request(
            targetFn = "approve",
            args = approveArgs(ACCOUNT, RECIPIENT, 70_000_000L, 1000u),
        )
        val decoded = f.decodeCall(req)
        assertEquals(DecodedCallKind.APPROVE, decoded.kind)
        assertEquals("Spender", decoded.recipientLabel)
        assertEquals(RECIPIENT, decoded.recipient)
        assertEquals("7", decoded.amount)
    }

    @Test
    fun decodeUnknownFunctionListsArguments() = runTest {
        val f = flow(FakeCoordination())
        val req = request(targetFn = "custom_fn", args = listOf(encode(addressArg(RECIPIENT))))
        val decoded = f.decodeCall(req)
        assertEquals(DecodedCallKind.UNKNOWN, decoded.kind)
        assertEquals(1, decoded.arguments.size)
        assertTrue(decoded.arguments[0].label.contains("address"))
        assertEquals(RECIPIENT, decoded.arguments[0].value)
    }

    @Test
    fun decodeUndecodableArgsReturnsUndecodable() = runTest {
        val f = flow(FakeCoordination())
        val req = request(args = listOf("@@not-base64@@"))
        val decoded = f.decodeCall(req)
        assertEquals(DecodedCallKind.UNDECODABLE, decoded.kind)
        assertTrue(decoded.error != null)
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    @Test
    fun loadPendingScopesToConnectedAccount() = runTest {
        val mine = request(id = "mine", smartAccount = ACCOUNT)
        val theirs = request(id = "theirs", smartAccount = OTHER_ACCOUNT)
        val f = flow(FakeCoordination(pending = listOf(mine, theirs)))
        val result = f.loadPending()
        assertEquals(listOf("mine"), result.map { it.id })
    }

    @Test
    fun pendingCountIsZeroWhenDisconnected() = runTest {
        val f = flow(FakeCoordination(pending = listOf(request())), connectedAccount = null)
        assertEquals(0, f.pendingCount())
    }

    // -------------------------------------------------------------------------
    // Approve guards
    // -------------------------------------------------------------------------

    @Test
    fun approveRefusesAccountMismatchBeforeSubmitting() = runTest {
        val submitter = FakeSubmitter()
        val coord = FakeCoordination()
        val f = flow(coord, submitter = submitter, connectedAccount = ACCOUNT)
        val result = f.approveRequest(request(smartAccount = OTHER_ACCOUNT))
        assertFalse(result.success)
        assertEquals(ApprovalInboxFlow.ACCOUNT_MISMATCH_ERROR, result.error)
        assertEquals(0, submitter.calls)
    }

    @Test
    fun approveRefusesWhenNoWalletConnected() = runTest {
        val f = flow(FakeCoordination(), submitter = null, connectedAccount = ACCOUNT)
        val result = f.approveRequest(request())
        assertFalse(result.success)
        assertEquals(ApprovalInboxFlow.NO_WALLET_ERROR, result.error)
    }

    @Test
    fun approveRefusesWhenNoLongerPending() = runTest {
        val submitter = FakeSubmitter()
        val coord = FakeCoordination().apply {
            getResult = { id -> request(id = id, status = CoordinationRequest.STATUS_APPROVED) }
        }
        val f = flow(coord, submitter = submitter)
        val result = f.approveRequest(request())
        assertFalse(result.success)
        assertTrue(result.error!!.contains("no longer pending"))
        assertEquals(0, submitter.calls)
    }

    // -------------------------------------------------------------------------
    // Approve happy path + report
    // -------------------------------------------------------------------------

    @Test
    fun approveResubmitsExactArgsAndReportsHash() = runTest {
        val req = request()
        val submitter = FakeSubmitter(outcome = ContractCallOutcome(true, "TXHASH", null))
        val store = InMemoryHashStore()
        val coord = FakeCoordination().apply { getResult = { req } }
        val f = flow(coord, submitter = submitter, store = store)

        val result = f.approveRequest(req)

        assertTrue(result.success)
        assertEquals("TXHASH", result.hash)
        assertEquals(1, submitter.calls)
        // The re-submitted args, re-encoded, must equal the exact stored call arguments
        // (SCValXdr carries ByteArray fields, so compare the canonical base64 form).
        val resubmittedBase64 = submitter.lastArgs!!.map { encode(it) }
        assertEquals(req.args, resubmittedBase64)
        assertEquals(listOf(req.id to "TXHASH"), coord.approveCalls)
        // On a fully reported approval the confirmed-hash guard is cleared.
        assertNull(store.get(req.id))
        assertFalse(f.isAwaitingReport(req.id))
    }

    @Test
    fun approveRecordsConfirmedHashAndOffersRetryWhenReportFails() = runTest {
        val req = request()
        val submitter = FakeSubmitter(outcome = ContractCallOutcome(true, "TXHASH", null))
        val store = InMemoryHashStore()
        val coord = FakeCoordination().apply {
            getResult = { req }
            approveBehavior = { _, _ -> throw CoordinationException("server down") }
        }
        val f = flow(coord, submitter = submitter, store = store)

        val result = f.approveRequest(req)

        assertFalse(result.success)
        assertTrue(result.confirmedOnChain)
        assertEquals("TXHASH", store.get(req.id))
        assertTrue(f.isAwaitingReport(req.id))

        // A second approve must NOT re-submit on-chain: it routes to the report path.
        coord.approveBehavior = { id, hash ->
            CoordinationRequest(id, "C", "C", "transfer", emptyList(), "", 0, "approved", 0L, resultHash = hash)
        }
        val retry = f.approveRequest(req)
        assertTrue(retry.success)
        assertEquals("TXHASH", retry.hash)
        assertEquals(1, submitter.calls) // still only one on-chain submission
        assertNull(store.get(req.id))
    }

    @Test
    fun retryReportTreats409AsSuccess() = runTest {
        val req = request()
        val store = InMemoryHashStore().apply { put(req.id, "TXHASH") }
        val coord = FakeCoordination().apply {
            approveBehavior = { _, _ -> throw CoordinationException("already resolved", statusCode = 409) }
        }
        val f = flow(coord, store = store)

        val result = f.retryReport(req)

        assertTrue(result.success)
        assertEquals("TXHASH", result.hash)
        assertNull(store.get(req.id))
    }

    @Test
    fun concurrentSecondApproveIsRejectedAndDoesNotSubmitTwice() = runTest {
        val req = request()
        val reachedSubmit = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val submitter = FakeSubmitter(
            outcome = ContractCallOutcome(true, "TXHASH", null),
            reachedSubmit = reachedSubmit,
            release = release,
        )
        val coord = FakeCoordination().apply { getResult = { req } }
        val f = flow(coord, submitter = submitter, store = InMemoryHashStore())

        // The first approval parks inside the submitter while holding the in-flight guard.
        val first = async { f.approveRequest(req) }
        reachedSubmit.await()

        // A second approval issued meanwhile must be rejected without a second on-chain submit.
        val second = f.approveRequest(req)
        assertFalse(second.success)
        assertTrue(second.error!!.contains("already in progress"))
        assertEquals(1, submitter.calls)

        // Release the first call; it completes successfully and the submit count stays at one.
        release.complete(Unit)
        val firstResult = first.await()
        assertTrue(firstResult.success)
        assertEquals("TXHASH", firstResult.hash)
        assertEquals(1, submitter.calls)
    }

    @Test
    fun approveConfirmedWithoutHashReportsMarkerAndLeavesPending() = runTest {
        val req = request()
        val submitter = FakeSubmitter(outcome = ContractCallOutcome(true, null, null))
        val store = InMemoryHashStore()
        val coord = FakeCoordination().apply { getResult = { req } }
        val f = flow(coord, submitter = submitter, store = store)

        val result = f.approveRequest(req)

        // The call executed on-chain; with no hash the flow reports a non-hash marker so the
        // escalation leaves the pending set instead of re-surfacing forever.
        assertTrue(result.confirmedOnChain)
        assertNull(result.hash)
        assertEquals(listOf(req.id to ApprovalInboxFlow.CONFIRMED_NO_HASH_REPORT), coord.approveCalls)
        assertEquals(1, submitter.calls)
        // Reported successfully, so the never-re-submit guard is cleared and nothing is outstanding.
        assertNull(store.get(req.id))
        assertFalse(f.isAwaitingReport(req.id))
    }

    @Test
    fun confirmedWithoutHashKeepsGuardAndStaysReportableWhenReportFails() = runTest {
        val req = request()
        val submitter = FakeSubmitter(outcome = ContractCallOutcome(true, null, null))
        val store = InMemoryHashStore()
        val coord = FakeCoordination().apply {
            getResult = { req }
            approveBehavior = { _, _ -> throw CoordinationException("server down") }
        }
        val f = flow(coord, submitter = submitter, store = store)

        val result = f.approveRequest(req)

        // The report failed, but the call must never be re-submitted: the no-hash guard stays
        // and the escalation remains reportable (a later approval retries the report only).
        assertFalse(result.success)
        assertTrue(result.confirmedOnChain)
        assertEquals(ApprovalInboxFlow.CONFIRMED_NO_HASH_SENTINEL, store.get(req.id))
        assertTrue(f.isAwaitingReport(req.id))

        // A second approval re-reports the marker without a second on-chain submit.
        coord.approveBehavior = { id, hash ->
            CoordinationRequest(id, "C", "C", "transfer", emptyList(), "", 0, "approved", 0L, resultHash = hash)
        }
        val retry = f.approveRequest(req)
        assertTrue(retry.confirmedOnChain)
        assertEquals(1, submitter.calls)
        assertEquals(2, coord.approveCalls.size)
        assertEquals(ApprovalInboxFlow.CONFIRMED_NO_HASH_REPORT, coord.approveCalls.last().second)
        assertNull(store.get(req.id))
    }

    // -------------------------------------------------------------------------
    // Approved-results presentation state
    // -------------------------------------------------------------------------

    @Test
    fun approvedEntryForSuccessCarriesFullHashContextLabelAndExplorerUrl() = runTest {
        val f = flow(FakeCoordination())
        val req = request()
        val decoded = f.decodeCall(req)

        val entry = approvedEntryFor(req, decoded, ApprovalResult(success = true, hash = "ABC123"))

        assertEquals(req.id, entry.requestId)
        assertEquals("ABC123", entry.hash)
        assertFalse(entry.confirmedWithoutHash)
        assertEquals("https://stellar.expert/explorer/testnet/tx/ABC123", entry.explorerUrl)
        // 105_000_000 base units at 7 decimals = 10.5 to the decoded recipient.
        assertEquals("10.5 to ${truncateAddress(RECIPIENT)}", entry.contextLabel)
    }

    @Test
    fun approvedEntryForConfirmedWithoutHashDegradesGracefully() = runTest {
        val f = flow(FakeCoordination())
        val req = request()
        val decoded = f.decodeCall(req)

        val entry = approvedEntryFor(
            req,
            decoded,
            ApprovalResult(success = false, hash = "", confirmedOnChain = true),
        )

        assertNull(entry.hash)
        assertTrue(entry.confirmedWithoutHash)
        assertNull(entry.explorerUrl)
        assertEquals("10.5 to ${truncateAddress(RECIPIENT)}", entry.contextLabel)
    }

    @Test
    fun approvalContextLabelFallsBackToFunctionAndTargetForUnknownShape() = runTest {
        val f = flow(FakeCoordination())
        val req = request(targetFn = "custom_fn", args = listOf(encode(addressArg(RECIPIENT))))
        val decoded = f.decodeCall(req)

        assertEquals("custom_fn on ${truncateAddress(req.target)}", approvalContextLabel(req, decoded))
    }

    // -------------------------------------------------------------------------
    // Reject
    // -------------------------------------------------------------------------

    @Test
    fun rejectSendsTrimmedNoteAndDropsBlank() = runTest {
        val coord = FakeCoordination()
        val f = flow(coord)

        f.rejectRequest(request(id = "a"), note = "  spam  ")
        f.rejectRequest(request(id = "b"), note = "   ")

        assertEquals("spam", coord.rejectCalls[0].second)
        assertNull(coord.rejectCalls[1].second)
    }
}
