package com.soneso.smartdemo.flows

/**
 * Business logic for the approval inbox (steps 4 + 5 of the agent-signer flow).
 *
 * [ApprovalInboxFlow] is the single entry point the approval-inbox screen uses to talk
 * to the coordination server and the smart-account SDK. Screens must not call into the
 * SDK or the HTTP client directly.
 *
 * - [loadPending] / [pendingCount] — read the pending escalations the agent posted,
 *   scoped client-side to the connected smart account.
 * - [decodeCall] — derive the authoritative consent data (recipient + on-chain amount)
 *   from the stored base64 `SCValXdr` arguments that are actually re-submitted, NOT from
 *   the server-supplied display-only `amount`.
 * - [approveRequest] — rebuild the agent's EXACT call from the stored arguments and
 *   re-submit it under the user's Default rule (single-signer passkey path, gasless via
 *   the relayer when configured), then report the resulting transaction hash back.
 * - [retryReport] — re-report a previously confirmed on-chain approval whose report-back
 *   failed, WITHOUT re-submitting on-chain.
 * - [rejectRequest] — decline the escalation with an optional note.
 *
 * The coordination client, the contract-call submitter, the connected-account resolver,
 * and the confirmed-hash store are all injected, so every path is unit-testable without a
 * network or a live testnet.
 */

import com.soneso.smartdemo.config.DemoConfig
import com.soneso.smartdemo.state.ActivityLogState
import com.soneso.smartdemo.state.DemoState
import com.soneso.smartdemo.util.CoordinationClient
import com.soneso.smartdemo.util.CoordinationException
import com.soneso.smartdemo.util.CoordinationRequest
import com.soneso.smartdemo.util.formatBaseUnitsAsDecimal
import com.soneso.smartdemo.util.isUserCancellation
import com.soneso.smartdemo.util.truncateAddress
import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.core.ContractErrorCodes
import com.soneso.stellar.sdk.xdr.SCValTypeXdr
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrReader
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Maps an on-chain contract error [reason] code to a human-readable name.
 *
 * The numbers come from the OpenZeppelin smart-account contract's `Error` enum, surfaced
 * by the SDK via [ContractErrorCodes]. Unknown codes fall back to a generic
 * `Contract error #<n>` label so the inbox never hides a rejection it cannot name.
 */
fun describeRejectionReason(reason: Int): String = when (reason) {
    ContractErrorCodes.MATH_OVERFLOW -> "Math overflow"
    ContractErrorCodes.KEY_DATA_TOO_LARGE -> "Key data too large"
    ContractErrorCodes.CONTEXT_RULE_IDS_LENGTH_MISMATCH -> "Context rule IDs length mismatch"
    ContractErrorCodes.NAME_TOO_LONG -> "Name too long"
    ContractErrorCodes.UNAUTHORIZED_SIGNER -> "Unauthorized signer"
    else -> "Contract error #$reason"
}

/** The call shape the inbox recognised in a request's decoded arguments. */
enum class DecodedCallKind {
    /** `transfer(from, to, amount)` — the recipient is the `to` argument. */
    TRANSFER,

    /** `approve(from, spender, amount, expiration)` — the recipient is the `spender` argument. */
    APPROVE,

    /** A shape the inbox does not special-case; the full argument list is shown. */
    UNKNOWN,

    /** The stored arguments could not be decoded at all. */
    UNDECODABLE,
}

/**
 * A single decoded argument rendered for an unrecognised call shape.
 *
 * @property label Position + type label, for example `Arg 1 (address)`.
 * @property value Human-readable value (a decoded address, integer, symbol, or — for
 *   exotic types — the verbatim base64 that re-submits).
 */
data class DecodedArgument(
    val label: String,
    val value: String,
)

/**
 * Authoritative consent data derived from a request's stored `args`.
 *
 * These values come from decoding the opaque base64 `SCValXdr` arguments that are
 * actually re-submitted on-chain, NOT from the server-supplied, display-only `amount`
 * string. The card the user approves renders THESE values so the displayed call matches
 * the executed call exactly. A buggy/malicious relay cannot show one amount while the
 * args drain a wallet.
 *
 * @property kind The recognised call shape.
 * @property recipient Decoded recipient address (`to`/`spender`), or null for unknown/undecodable.
 * @property recipientLabel Label for [recipient]: `Recipient` for transfer, `Spender` for approve.
 * @property amountBaseUnits Raw decoded on-chain amount in base units (the i128), or null.
 * @property amount [amountBaseUnits] formatted at the request token's resolved decimal scale,
 *   or the raw base units with a label when that scale could not be read; null for
 *   unknown/undecodable shapes.
 * @property arguments Full decoded argument list, populated when [kind] is [DecodedCallKind.UNKNOWN].
 * @property error User-facing message when [kind] is [DecodedCallKind.UNDECODABLE].
 */
data class DecodedCall(
    val kind: DecodedCallKind,
    val recipient: String? = null,
    val recipientLabel: String? = null,
    val amountBaseUnits: BigInteger? = null,
    val amount: String? = null,
    val arguments: List<DecodedArgument> = emptyList(),
    val error: String? = null,
)

/**
 * Outcome of an [ApprovalInboxFlow.approveRequest] or [ApprovalInboxFlow.retryReport] call.
 *
 * @property success True when the rebuilt call confirmed on-chain AND the result was
 *   reported back to the coordination server.
 * @property hash On-chain transaction hash when known; null otherwise.
 * @property error Sanitised user-facing message on failure; null on success.
 * @property confirmedOnChain True whenever the transaction confirmed on-chain — even when
 *   reporting it back failed — so the inbox can switch the card from a re-submittable
 *   "Approve" to an idempotent "Retry report" and NEVER submit the same call twice.
 */
data class ApprovalResult(
    val success: Boolean,
    val hash: String? = null,
    val error: String? = null,
    val confirmedOnChain: Boolean = false,
)

/** Outcome of an [ApprovalInboxFlow.rejectRequest] call. */
data class RejectionResult(
    val success: Boolean,
    val error: String? = null,
)

/**
 * A resolved approval retained on the inbox screen for the session.
 *
 * Recorded once an [ApprovalInboxFlow.approveRequest] / [ApprovalInboxFlow.retryReport]
 * confirms on-chain so the transaction hash stays visible and copyable after the
 * confirmation toast disappears. Survives only until the screen is left; it is screen
 * presentation state, not a durable store.
 *
 * @property requestId The escalation this approval resolved.
 * @property hash The FULL on-chain transaction hash, or null when the transaction
 *   confirmed but the SDK returned no hash (see [confirmedWithoutHash]).
 * @property contextLabel A short, cheap description of what was approved (decoded amount +
 *   recipient when available), or null when nothing meaningful could be derived.
 * @property confirmedWithoutHash True when the transaction confirmed on-chain but no hash
 *   was returned; the inbox shows a graceful note with no copy/explorer control.
 */
data class ApprovedEntry(
    val requestId: String,
    val hash: String?,
    val contextLabel: String?,
    val confirmedWithoutHash: Boolean = false,
) {
    /** Explorer URL for [hash], or null when there is no hash to link to. */
    val explorerUrl: String?
        get() = hash?.let { DemoConfig.STELLAR_EXPERT_TESTNET_TX_BASE + it }
}

/**
 * Builds a short context label for an approved escalation from its decoded call.
 *
 * Uses the same authoritative values already shown on the card (decoded amount and
 * recipient), so the approved-results list reads consistently with the pending card it
 * replaced. Falls back to the function name and target for shapes without a recipient.
 */
fun approvalContextLabel(request: CoordinationRequest, decoded: DecodedCall): String =
    when (decoded.kind) {
        DecodedCallKind.TRANSFER, DecodedCallKind.APPROVE -> {
            val who = truncateAddress(decoded.recipient ?: request.target)
            val amount = decoded.amount
            if (amount != null) "$amount to $who" else "${request.targetFn} to $who"
        }
        DecodedCallKind.UNKNOWN, DecodedCallKind.UNDECODABLE ->
            "${request.targetFn} on ${truncateAddress(request.target)}"
    }

/**
 * Builds the session-scoped [ApprovedEntry] for a resolved approval.
 *
 * An empty/missing hash is normalised to a [ApprovedEntry.confirmedWithoutHash] entry so
 * the inbox degrades gracefully instead of offering a copy of an empty string.
 */
fun approvedEntryFor(
    request: CoordinationRequest,
    decoded: DecodedCall,
    result: ApprovalResult,
): ApprovedEntry {
    val hash = result.hash?.takeIf { it.isNotEmpty() }
    return ApprovedEntry(
        requestId = request.id,
        hash = hash,
        contextLabel = approvalContextLabel(request, decoded),
        confirmedWithoutHash = hash == null,
    )
}

/** Result of a single-signer contract-call submission under the Default rule. */
data class ContractCallOutcome(
    val success: Boolean,
    val hash: String?,
    val error: String?,
)

/**
 * Submits a single-signer contract call under the connected account's Default rule.
 *
 * The production adapter wraps `OZSmartAccountKit.transactionOperations`; tests inject a
 * fake that returns canned [ContractCallOutcome]s or throws.
 */
interface ContractCallSubmitter {
    suspend fun contractCall(
        target: String,
        targetFn: String,
        targetArgs: List<SCValXdr>,
    ): ContractCallOutcome
}

/**
 * Durable store of confirmed approval hashes, keyed by request id.
 *
 * A request recorded here must NEVER be re-submitted on-chain; only its report-back may
 * be retried. The production store is backed by [DemoState] so the guard survives
 * navigation; tests inject an in-memory store.
 */
interface ConfirmedHashStore {
    fun get(requestId: String): String?
    fun put(requestId: String, hash: String)
    fun remove(requestId: String)
}

/** [ConfirmedHashStore] backed by the shared [DemoState]. */
object DemoStateConfirmedHashStore : ConfirmedHashStore {
    override fun get(requestId: String): String? = DemoState.confirmedApprovalHash(requestId)
    override fun put(requestId: String, hash: String) = DemoState.recordConfirmedApprovalHash(requestId, hash)
    override fun remove(requestId: String) = DemoState.clearConfirmedApprovalHash(requestId)
}

/**
 * Business logic for the approval inbox.
 *
 * @param coordination Client for the coordination server.
 * @param resolveContractCall Returns the single-signer submitter for the connected kit,
 *   or null when no wallet is connected. Resolved lazily on each call so a kit that
 *   becomes available after the flow is created is picked up without rebuilding it.
 * @param resolveConnectedAccount Returns the C-address of the connected smart account,
 *   or null when disconnected.
 * @param confirmedHashStore Durable store of confirmed-on-chain hashes (never re-submit guard).
 * @param resolveDecimals Seam that resolves the decimal scale of a request's target token so
 *   decoded amounts are formatted at the token's true precision rather than a hardcoded scale.
 *   Defaults to [resolveSpendingLimitDecimals], which resolves the native and demo tokens
 *   without a network call and fetches any other token's `decimals()` over RPC. Injected in
 *   tests to supply a deterministic scale.
 */
class ApprovalInboxFlow(
    private val coordination: CoordinationClient,
    private val resolveContractCall: () -> ContractCallSubmitter?,
    private val resolveConnectedAccount: () -> String?,
    private val confirmedHashStore: ConfirmedHashStore = DemoStateConfirmedHashStore,
    private val resolveDecimals: suspend (tokenContract: String) -> Int = { resolveSpendingLimitDecimals(it) },
) {
    /** True while an approve call is executing, guarding against concurrent in-flight submissions. */
    private var isApproving = false

    /**
     * Decimal scales resolved during this inbox session, keyed by token contract. Successful
     * resolutions are cached so a refresh does not re-issue a `decimals()` RPC for the same
     * token; failures are not cached so a later attempt can retry. Decoding is sequential, so
     * no synchronisation is needed.
     */
    private val decimalsCache = mutableMapOf<String, Int>()

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    /**
     * Loads the pending escalations for the connected smart account, newest first.
     *
     * The coordination server filters only by status, so the listing is scoped
     * client-side to `request.smartAccount == ` the connected account. Returns an empty
     * list when no wallet is connected. Propagates [CoordinationException] so the screen
     * can render an error state when the server is unreachable.
     */
    suspend fun loadPending(): List<CoordinationRequest> {
        val pending = coordination.listPending()
        return scopeToConnectedAccount(pending)
    }

    /**
     * Returns the number of pending escalations for the connected account.
     *
     * Used by the bell badge on the main screen. Scoped exactly like [loadPending]; zero
     * when no wallet is connected.
     */
    suspend fun pendingCount(): Int {
        val pending = coordination.listPending()
        return scopeToConnectedAccount(pending).size
    }

    private fun scopeToConnectedAccount(all: List<CoordinationRequest>): List<CoordinationRequest> {
        val account = resolveConnectedAccount() ?: return emptyList()
        return all.filter { it.smartAccount == account }
    }

    // -------------------------------------------------------------------------
    // Decode (authoritative consent data)
    // -------------------------------------------------------------------------

    /**
     * Decodes the authoritative consent data the user is actually authorising.
     *
     * Decodes each base64 `SCValXdr` entry in [request].args and, for the known
     * `transfer(from, to, amount)` and `approve(from, spender, amount, expiry)` shapes,
     * derives the on-chain recipient and amount. The amount is formatted at the request
     * token's resolved decimal scale (so a non-7-decimal token is not misrepresented), or
     * shown as raw base units with a label when that scale cannot be read. For any other
     * shape the full decoded argument list is returned. The server-supplied
     * [CoordinationRequest.amount] is deliberately ignored: it is display-only and untrusted.
     */
    suspend fun decodeCall(request: CoordinationRequest): DecodedCall {
        val args: List<SCValXdr> = try {
            decodeArgs(request.args)
        } catch (_: Exception) {
            return DecodedCall(
                kind = DecodedCallKind.UNDECODABLE,
                error = "Cannot decode the stored call arguments. Do not approve.",
            )
        }

        when (request.targetFn) {
            "transfer" -> if (args.size == 3) {
                decodeTransferLike(
                    args, resolveDecimalsOrNull(request.target),
                    recipientIndex = 1, amountIndex = 2, recipientLabel = "Recipient",
                )?.let { return it }
            }
            "approve" -> if (args.size == 4) {
                decodeTransferLike(
                    args, resolveDecimalsOrNull(request.target),
                    recipientIndex = 1, amountIndex = 2, recipientLabel = "Spender",
                )?.let { return it }
            }
        }

        // Unknown shape (or a known function with an unexpected argument count): surface
        // the full decoded argument list so nothing is hidden.
        return DecodedCall(
            kind = DecodedCallKind.UNKNOWN,
            arguments = summariseArgs(args, request.args),
        )
    }

    /**
     * Resolves the decimal scale of [tokenContract], caching successes, or null when it
     * cannot be read. A null scale formats the amount in raw base units with a label rather
     * than at a guessed scale, so a non-7-decimal token is never silently misrepresented.
     */
    private suspend fun resolveDecimalsOrNull(tokenContract: String): Int? {
        decimalsCache[tokenContract]?.let { return it }
        return try {
            resolveDecimals(tokenContract).also { decimalsCache[tokenContract] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeTransferLike(
        args: List<SCValXdr>,
        decimals: Int?,
        recipientIndex: Int,
        amountIndex: Int,
        recipientLabel: String,
    ): DecodedCall? {
        val recipient = decodeAddress(args[recipientIndex]) ?: return null
        val amountBaseUnits = decodeInteger(args[amountIndex]) ?: return null
        val amount = if (decimals != null) {
            formatBaseUnitsAsDecimal(amountBaseUnits, decimals)
        } else {
            "$amountBaseUnits base units"
        }
        return DecodedCall(
            kind = if (recipientLabel == "Spender") DecodedCallKind.APPROVE else DecodedCallKind.TRANSFER,
            recipient = recipient,
            recipientLabel = recipientLabel,
            amountBaseUnits = amountBaseUnits,
            amount = amount,
        )
    }

    // -------------------------------------------------------------------------
    // Approve (steps 4 + 5)
    // -------------------------------------------------------------------------

    /**
     * Rebuilds and re-submits the agent's exact call, then reports the outcome.
     *
     * Before submitting, guards in order: a wallet must be connected; the escalation must
     * target the connected smart account (BEFORE any passkey ceremony); the stored
     * arguments must decode; and a fresh `GET /requests/{id}` must still report `pending`
     * (a stale inbox / cross-device resolution aborts the submission). Once a hash is
     * confirmed, that request is recorded and can never be re-submitted — a failed
     * report-back returns [ApprovalResult.confirmedOnChain] true so the inbox offers
     * [retryReport] instead of a second submit.
     */
    suspend fun approveRequest(request: CoordinationRequest): ApprovalResult {
        if (isApproving) {
            return ApprovalResult(success = false, error = "An approval is already in progress.")
        }
        isApproving = true
        try {
            // If this request already confirmed on-chain, never re-submit: route to the
            // idempotent report-back path instead. The dedup store survives navigation.
            if (confirmedHashStore.get(request.id) != null) {
                return retryReport(request)
            }

            val contractCall = resolveContractCall()
                ?: return ApprovalResult(success = false, error = NO_WALLET_ERROR)

            // Account-scope guard: refuse before any ceremony when the escalation targets
            // a different smart account than the connected one.
            val connectedAccount = resolveConnectedAccount()
                ?: return ApprovalResult(success = false, error = NO_WALLET_ERROR)
            if (connectedAccount != request.smartAccount) {
                ActivityLogState.error(ACCOUNT_MISMATCH_ERROR)
                return ApprovalResult(success = false, error = ACCOUNT_MISMATCH_ERROR)
            }

            val targetArgs: List<SCValXdr> = try {
                decodeArgs(request.args)
            } catch (_: Exception) {
                val message = "Cannot decode the stored call arguments. Do not approve."
                ActivityLogState.error(message)
                return ApprovalResult(success = false, error = message)
            }

            // Re-check the request is still pending immediately before submitting so a
            // stale inbox / cross-device resolution does not trigger a duplicate transfer.
            try {
                val latest = coordination.getRequest(request.id)
                if (latest.status != CoordinationRequest.STATUS_PENDING) {
                    val message = "This escalation is no longer pending (status: ${latest.status}); " +
                        "it was resolved elsewhere. Refresh the inbox."
                    ActivityLogState.info(message)
                    return ApprovalResult(success = false, error = message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = "Could not re-check the escalation before submitting: ${e.message ?: "Unknown error"}"
                ActivityLogState.error(message)
                return ApprovalResult(success = false, error = message)
            }

            ActivityLogState.info(
                "Approving agent call ${request.targetFn} on ${truncateAddress(request.target)}"
            )

            val result: ContractCallOutcome = try {
                contractCall.contractCall(
                    target = request.target,
                    targetFn = request.targetFn,
                    targetArgs = targetArgs,
                )
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val msg = e.message ?: "Unknown error"
                if (isUserCancellation(msg)) {
                    ActivityLogState.info("Passkey authentication cancelled")
                    return ApprovalResult(success = false, error = "Passkey authentication cancelled")
                }
                ActivityLogState.error("Approval failed: $msg")
                return ApprovalResult(success = false, error = msg)
            }

            if (!result.success) {
                val message = "Approval failed: ${result.error ?: "Unknown error"}"
                ActivityLogState.error(message)
                return ApprovalResult(success = false, error = message)
            }

            val hash = result.hash ?: ""
            // The transaction confirmed on-chain. From here on this request must NEVER be
            // re-submitted, even when the hash is empty or the report-back fails. Record it
            // before attempting the report so the guard survives navigation.
            if (hash.isEmpty()) {
                // Confirmed on-chain but the SDK returned no hash: record the no-hash sentinel
                // (blocks any re-submit) and report a marker so the escalation leaves the
                // pending set instead of re-surfacing forever.
                confirmedHashStore.put(request.id, CONFIRMED_NO_HASH_SENTINEL)
                return reportConfirmedWithoutHash(request.id)
            }
            confirmedHashStore.put(request.id, hash)

            // Step 5: report the confirmed hash back so the agent learns the outcome.
            return postApproval(request.id, hash)
        } finally {
            isApproving = false
        }
    }

    /**
     * Re-reports a previously confirmed on-chain approval WITHOUT re-submitting.
     *
     * Used by the inbox's "Retry report" affordance after [approveRequest] confirmed the
     * transaction on-chain but the report-back POST failed. Looks up the recorded hash and
     * POSTs only `/requests/{id}/approve`; never calls [ContractCallSubmitter.contractCall].
     * A confirmed-without-hash request reports its marker via [reportConfirmedWithoutHash].
     */
    suspend fun retryReport(request: CoordinationRequest): ApprovalResult {
        val recordedHash = confirmedHashStore.get(request.id)
        if (recordedHash == null) {
            val message = "No confirmed transaction hash is available to report for this escalation."
            ActivityLogState.error(message)
            return ApprovalResult(success = false, error = message)
        }
        if (recordedHash == CONFIRMED_NO_HASH_SENTINEL) {
            return reportConfirmedWithoutHash(request.id)
        }
        return postApproval(request.id, recordedHash)
    }

    /**
     * Reports a confirmed on-chain [hash] for [id] to the coordination server, shared by the
     * first report after [approveRequest] and the [retryReport] retry.
     *
     * On success the never-re-submit guard for [id] is cleared and the result reported as
     * approved. A `409` (already resolved server-side) is idempotent success: the report is
     * effectively complete, so the guard is cleared too. Any other failure leaves the guard in
     * place and returns [ApprovalResult.confirmedOnChain] true so the caller can offer a retry
     * without re-submitting on-chain.
     */
    private suspend fun postApproval(id: String, hash: String): ApprovalResult {
        try {
            coordination.approve(id, hash)
        } catch (e: CoordinationException) {
            if (e.statusCode == 409) {
                confirmedHashStore.remove(id)
                ActivityLogState.info("Escalation already resolved on the coordination server. Hash: $hash")
                return ApprovalResult(success = true, hash = hash)
            }
            val message = "Reporting the approval failed: ${e.message} " +
                "(transaction confirmed on-chain: $hash)"
            ActivityLogState.error(message)
            return ApprovalResult(success = false, hash = hash, error = message, confirmedOnChain = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "Reporting the approval failed: ${e.message ?: "Unknown error"} " +
                "(transaction confirmed on-chain: $hash)"
            ActivityLogState.error(message)
            return ApprovalResult(success = false, hash = hash, error = message, confirmedOnChain = true)
        }
        confirmedHashStore.remove(id)
        ActivityLogState.success("Agent call approved. Hash: $hash")
        return ApprovalResult(success = true, hash = hash)
    }

    /**
     * Reports a confirmed-on-chain approval whose SDK call returned no transaction hash.
     *
     * The transaction executed but its hash was not captured, so there is nothing to copy or
     * link. A non-hash marker ([CONFIRMED_NO_HASH_REPORT]) is reported to the coordination
     * server so the escalation leaves the pending set instead of re-surfacing forever; the
     * agent learns it was approved even though the exact hash is unavailable. On success (or a
     * `409` already-resolved) the never-re-submit guard is cleared; if the report fails the
     * guard is kept so a later approval retries the report — the on-chain call is never repeated.
     */
    private suspend fun reportConfirmedWithoutHash(id: String): ApprovalResult {
        val base = "Approval confirmed on-chain but no transaction hash was returned."
        try {
            coordination.approve(id, CONFIRMED_NO_HASH_REPORT)
        } catch (e: CoordinationException) {
            if (e.statusCode == 409) {
                confirmedHashStore.remove(id)
                ActivityLogState.info("$base The escalation was already resolved on the coordination server.")
                return ApprovalResult(success = true, confirmedOnChain = true)
            }
            val message = "$base Reporting it to the agent failed: ${e.message}. A later approval retries the report."
            ActivityLogState.error(message)
            return ApprovalResult(success = false, error = message, confirmedOnChain = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "$base Reporting it to the agent failed: ${e.message ?: "Unknown error"}. " +
                "A later approval retries the report."
            ActivityLogState.error(message)
            return ApprovalResult(success = false, error = message, confirmedOnChain = true)
        }
        confirmedHashStore.remove(id)
        ActivityLogState.success("$base The agent was notified that it executed on-chain.")
        return ApprovalResult(success = true, confirmedOnChain = true)
    }

    /**
     * Whether [id] has a confirmed on-chain approval whose report-back to the coordination
     * server is still outstanding — a real transaction hash, or the no-hash sentinel that is
     * reported as a marker. Cleared once the report (or its retry) resolves the escalation.
     */
    fun isAwaitingReport(id: String): Boolean = confirmedHashStore.get(id) != null

    // -------------------------------------------------------------------------
    // Reject
    // -------------------------------------------------------------------------

    /**
     * Declines [request], recording an optional [note] on the coordination server.
     *
     * An empty or whitespace-only note is sent as no note. Returns a [RejectionResult]; on
     * failure [RejectionResult.error] is sanitised and safe to display verbatim.
     */
    suspend fun rejectRequest(request: CoordinationRequest, note: String? = null): RejectionResult {
        val trimmed = note?.trim()
        val effectiveNote = if (trimmed.isNullOrEmpty()) null else trimmed
        return try {
            coordination.reject(request.id, effectiveNote)
            ActivityLogState.info(
                "Rejected agent call ${request.targetFn} on ${truncateAddress(request.target)}"
            )
            RejectionResult(success = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "Rejection failed: ${e.message ?: "Unknown error"}"
            ActivityLogState.error(message)
            RejectionResult(success = false, error = message)
        }
    }

    // -------------------------------------------------------------------------
    // Private: SCVal decoding
    // -------------------------------------------------------------------------

    /**
     * Decodes the base64-encoded `SCValXdr` argument list verbatim.
     *
     * @throws Exception when an entry is not valid base64 or not a valid `SCValXdr`,
     *   so the caller can surface a graceful error rather than crash.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeArgs(encoded: List<String>): List<SCValXdr> =
        encoded.map { entry ->
            val bytes = Base64.decode(entry)
            SCValXdr.decode(XdrReader(bytes))
        }

    /** Decodes an address `SCValXdr` to its StrKey form, or null when not an address. */
    private fun decodeAddress(value: SCValXdr): String? {
        if (value.discriminant != SCValTypeXdr.SCV_ADDRESS) return null
        return try {
            Address.fromSCVal(value).toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes a signed-integer `SCValXdr` (i128/u128/i64/u64/i32/u32) into a [BigInteger],
     * preserving the full range. Returns null for non-integer types.
     */
    private fun decodeInteger(value: SCValXdr): BigInteger? = try {
        when (value.discriminant) {
            SCValTypeXdr.SCV_I128 -> Scv.fromInt128(value)
            SCValTypeXdr.SCV_U128 -> Scv.fromUint128(value)
            SCValTypeXdr.SCV_I64 -> BigInteger.fromLong(Scv.fromInt64(value))
            SCValTypeXdr.SCV_U64 -> BigInteger.fromULong(Scv.fromUint64(value))
            SCValTypeXdr.SCV_I32 -> BigInteger.fromInt(Scv.fromInt32(value))
            SCValTypeXdr.SCV_U32 -> BigInteger.fromLong(Scv.fromUint32(value).toLong())
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun summariseArgs(args: List<SCValXdr>, encoded: List<String>): List<DecodedArgument> =
        args.mapIndexed { index, value ->
            val fallback = encoded.getOrNull(index) ?: ""
            DecodedArgument(
                label = "Arg $index (${scValTypeName(value)})",
                value = describeScVal(value, fallback),
            )
        }

    private fun describeScVal(value: SCValXdr, fallbackBase64: String): String {
        decodeAddress(value)?.let { return it }
        decodeInteger(value)?.let { return it.toString() }
        when (value.discriminant) {
            SCValTypeXdr.SCV_BOOL -> return try { Scv.fromBoolean(value).toString() } catch (_: Exception) { fallbackBase64 }
            SCValTypeXdr.SCV_SYMBOL -> return try { Scv.fromSymbol(value) } catch (_: Exception) { fallbackBase64 }
            SCValTypeXdr.SCV_STRING -> return try { Scv.fromString(value) } catch (_: Exception) { fallbackBase64 }
            else -> {}
        }
        // Exotic types: show the verbatim base64 that re-submits rather than hide it.
        return fallbackBase64
    }

    private fun scValTypeName(value: SCValXdr): String = when (value.discriminant) {
        SCValTypeXdr.SCV_ADDRESS -> "address"
        SCValTypeXdr.SCV_I128 -> "i128"
        SCValTypeXdr.SCV_U128 -> "u128"
        SCValTypeXdr.SCV_I256 -> "i256"
        SCValTypeXdr.SCV_U256 -> "u256"
        SCValTypeXdr.SCV_U32 -> "u32"
        SCValTypeXdr.SCV_I32 -> "i32"
        SCValTypeXdr.SCV_U64 -> "u64"
        SCValTypeXdr.SCV_I64 -> "i64"
        SCValTypeXdr.SCV_BOOL -> "bool"
        SCValTypeXdr.SCV_SYMBOL -> "symbol"
        SCValTypeXdr.SCV_STRING -> "string"
        SCValTypeXdr.SCV_BYTES -> "bytes"
        SCValTypeXdr.SCV_VEC -> "vec"
        SCValTypeXdr.SCV_MAP -> "map"
        else -> "value"
    }

    companion object {
        /** Marks (in the local store) a request that confirmed on-chain but produced no reportable hash. */
        const val CONFIRMED_NO_HASH_SENTINEL = "<confirmed-without-hash>"

        /**
         * Marker reported to the coordination server in the `resultHash` field for a
         * confirmed-without-hash approval, so the escalation leaves the pending set. The server
         * requires a non-empty value; this clearly-non-hash string signals the missing hash.
         */
        const val CONFIRMED_NO_HASH_REPORT = "confirmed-on-chain-without-hash"

        const val NO_WALLET_ERROR = "No wallet connected. Connect a wallet to approve."
        const val ACCOUNT_MISMATCH_ERROR =
            "This escalation targets a different smart account than the one you are connected to."
    }
}

/**
 * Builds a [ContractCallSubmitter] over the connected kit's single-signer contract-call
 * path under the Default rule, or null when no wallet is connected.
 *
 * The empty-signer single-signer path signs with the connected passkey and submits
 * through the relayer (gasless) when one is configured on the kit.
 */
fun kitContractCallSubmitter(): ContractCallSubmitter? {
    val kit = DemoState.kit ?: return null
    return object : ContractCallSubmitter {
        override suspend fun contractCall(
            target: String,
            targetFn: String,
            targetArgs: List<SCValXdr>,
        ): ContractCallOutcome {
            val result = kit.transactionOperations.contractCall(
                target = target,
                targetFn = targetFn,
                targetArgs = targetArgs,
            )
            return ContractCallOutcome(
                success = result.success,
                hash = result.hash,
                error = result.error,
            )
        }
    }
}

/**
 * Builds an [ApprovalInboxFlow] wired to the shared [DemoState] and coordination client.
 *
 * The contract-call submitter and the connected-account resolver are resolved lazily so a
 * kit that becomes available after the flow is created is picked up without rebuilding it.
 */
fun createApprovalInboxFlow(): ApprovalInboxFlow = ApprovalInboxFlow(
    coordination = DemoState.coordinationClient,
    resolveContractCall = { kitContractCallSubmitter() },
    resolveConnectedAccount = { DemoState.contractId },
    confirmedHashStore = DemoStateConfirmedHashStore,
)
