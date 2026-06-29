package com.soneso.smartdemo.agent

import com.soneso.stellar.sdk.Address
import com.soneso.stellar.sdk.scval.Scv
import com.soneso.stellar.sdk.smartaccount.oz.OZTransactionOperations
import com.soneso.stellar.sdk.smartaccount.oz.SelectedSigner
import com.soneso.stellar.sdk.smartaccount.oz.TransactionResult
import com.soneso.stellar.sdk.xdr.SCValXdr
import com.soneso.stellar.sdk.xdr.XdrWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Establishes the kit's connected state for the configured smart account.
 *
 * Behind an interface so the runner can be unit-tested without a live account or
 * a network round-trip.
 */
interface WalletSession {
    /** Connects to the smart account headlessly and returns its contract id. */
    suspend fun connect(): String
}

/**
 * Submits a multi-signer scoped contract call.
 *
 * The production adapter wraps `OZMultiSignerManager`; tests inject a fake that
 * returns canned [TransactionResult]s or throws.
 */
interface MultiSignerContractCall {
    /**
     * Invokes [targetFn] on [target] with [targetArgs], authorised by the
     * explicit [selectedSigners] list.
     */
    suspend fun multiSignerContractCall(
        target: String,
        targetFn: String,
        targetArgs: List<SCValXdr>,
        selectedSigners: List<SelectedSigner>,
    ): TransactionResult
}

/** Sink for the agent's progress messages. */
interface AgentLogger {
    fun info(message: String)
    fun success(message: String)
    fun error(message: String)
}

/**
 * [AgentLogger] that writes to stdout. Stdout is the headless agent's console
 * output channel, so a direct write is intentional here.
 */
class StdoutAgentLogger : AgentLogger {
    override fun info(message: String) = write("INFO", message)
    override fun success(message: String) = write("OK", message)
    override fun error(message: String) = write("ERROR", message)
    private fun write(level: String, message: String) = println("[agent] [$level] $message")
}

/** Terminal result of an [AgentRunner.run] invocation. */
sealed class AgentResult {
    /** The scoped call confirmed on-chain; no escalation was needed. */
    data class CallSucceeded(val hash: String) : AgentResult()

    /** The scoped call failed for a non-policy reason; the agent did not escalate. */
    data class CallFailed(val message: String) : AgentResult()

    /**
     * The escalated policy rejection was approved by the user. The agent learns
     * the outcome by polling and does not re-submit — the mobile app re-submits
     * the call under the Default rule and reports `resultHash`.
     */
    data class EscalationApproved(
        val requestId: String,
        val resultHash: String,
        val errorCode: Int,
    ) : AgentResult()

    /** The escalated policy rejection was declined by the user. */
    data class EscalationRejected(
        val requestId: String,
        val errorCode: Int,
        val note: String?,
    ) : AgentResult()

    /**
     * The escalation was created but no resolution arrived within the poll
     * budget.
     */
    data class EscalationPending(
        val requestId: String,
        val errorCode: Int,
        val attempts: Int,
    ) : AgentResult()
}

/**
 * Orchestrates one autonomous agent cycle: connect, register, submit a scoped
 * call, classify the outcome, and (on a policy rejection) escalate and poll.
 *
 * All collaborators are injected so unit tests can drive the success, rejection,
 * escalate-and-approved, escalate-and-rejected, and pending paths without a
 * network or a live account.
 *
 * [signerAdapter] is the same adapter instance supplied to the kit's
 * `OZSmartAccountConfig.externalEd25519Adapter`; the runner registers the agent
 * seed on it before submission and clears it afterwards. [sleep] is injectable
 * so tests can run the poll loop without real delays.
 */
class AgentRunner(
    val config: AgentConfig,
    private val session: WalletSession,
    private val contractCall: MultiSignerContractCall,
    private val coordination: CoordinationClient,
    private val signerAdapter: AgentEd25519Adapter,
    private val agentPublicKey: ByteArray,
    private val agentSeed: ByteArray,
    private val logger: AgentLogger = StdoutAgentLogger(),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    /** Runs one agent cycle and returns its terminal [AgentResult]. */
    suspend fun run(): AgentResult {
        logger.info(
            "Starting agent: account=${config.smartAccountContractId ?: "null"}, " +
                "token=${config.tokenContractId}, amount=${config.amount}"
        )
        // Print the agent's own public key as raw 64-character hex so an operator
        // can paste it into the demo's "Delegate to agent" screen, which registers
        // it as the Ed25519 external signer this agent then signs with.
        logger.info("Agent public key (paste into Delegate-to-agent): ${Hex.encode(agentPublicKey)}")

        val smartAccount = session.connect()
        logger.info("Connected to smart account $smartAccount")

        val args = buildTransferArgs(smartAccount)
        val selectedSigners = listOf<SelectedSigner>(
            SelectedSigner.Ed25519(config.ed25519VerifierAddress, agentPublicKey)
        )

        // The signing seed is only needed to authorise the scoped call below;
        // escalation and polling never sign. Register it immediately before the
        // call and drop the adapter's copy the moment the call returns, regardless
        // of outcome, so the key is not retained for the whole escalation/poll
        // window. clearAll runs in a finally so the seed is dropped on every exit
        // path, including a cooperative cancellation that attemptCall re-raises.
        signerAdapter.add(config.ed25519VerifierAddress, agentPublicKey, agentSeed)
        val outcome = try {
            attemptCall(args, selectedSigners)
        } finally {
            signerAdapter.clearAll()
        }

        return when (outcome) {
            is CallOutcome.Succeeded -> {
                logger.success("Scoped call confirmed. Hash: ${outcome.hash}")
                AgentResult.CallSucceeded(outcome.hash)
            }
            is CallOutcome.Failed -> {
                logger.error("Scoped call failed (not a policy rejection): ${outcome.message}")
                AgentResult.CallFailed(outcome.message)
            }
            is CallOutcome.Rejected -> escalateAndPoll(
                errorCode = outcome.errorCode,
                errorName = outcome.errorName,
                rawMessage = outcome.rawMessage,
                args = args,
                smartAccount = smartAccount,
            )
        }
    }

    private suspend fun attemptCall(
        args: List<SCValXdr>,
        selectedSigners: List<SelectedSigner>,
    ): CallOutcome = try {
        val result = contractCall.multiSignerContractCall(
            target = config.tokenContractId,
            targetFn = TARGET_FN,
            targetArgs = args,
            selectedSigners = selectedSigners,
        )
        classifyResult(result)
    } catch (e: CancellationException) {
        // Cooperative cancellation must propagate, never be misclassified as a
        // contract failure. The caller's finally still clears the registered seed.
        throw e
    } catch (e: Throwable) {
        classifyError(e)
    }

    private suspend fun escalateAndPoll(
        errorCode: Int,
        errorName: String?,
        rawMessage: String,
        args: List<SCValXdr>,
        smartAccount: String,
    ): AgentResult {
        logger.info(
            "Policy rejection (code $errorCode${errorName?.let { " / $it" } ?: ""}): $rawMessage. " +
                "Escalating to ${config.coordinationBaseUrl}."
        )

        val encodedArgs = args.map { encodeArg(it) }

        val created = coordination.createRequest(
            smartAccount = smartAccount,
            target = config.tokenContractId,
            targetFn = TARGET_FN,
            args = encodedArgs,
            amount = config.amount,
            reason = errorCode,
        )
        val requestId = created.id
        logger.info("Escalation request created: id=$requestId (pending).")

        // A live run validates pollMaxAttempts >= 1, but the runner is also driven
        // directly in tests. Clamp to a non-negative bound so a non-positive value
        // yields an empty loop (returns pending with attempts 0) instead of trapping
        // on an invalid range.
        val maxAttempts = config.pollMaxAttempts.coerceAtLeast(0)
        for (attempt in 0 until maxAttempts) {
            sleep(config.pollIntervalSeconds * 1000L)

            val current = try {
                coordination.getRequest(requestId)
            } catch (e: CancellationException) {
                // Cooperative cancellation must propagate, never be swallowed as a
                // transient poll failure.
                throw e
            } catch (e: Exception) {
                // A single failed poll (e.g. a transient network blip) must not
                // abort the escalation: the loop is bounded by maxAttempts. Log it
                // and try again on the next attempt; the request resolves
                // server-side independently of our polling.
                logger.info(
                    "Poll attempt ${attempt + 1}/$maxAttempts for $requestId failed: " +
                        "${e.message}. Retrying."
                )
                continue
            }
            when (current.status) {
                CoordinationRequest.STATUS_APPROVED -> {
                    val resultHash = current.resultHash ?: ""
                    logger.success(
                        "Escalation approved by user. resultHash=$resultHash. " +
                            "The mobile app re-submitted under the Default rule; the agent does not re-submit."
                    )
                    return AgentResult.EscalationApproved(requestId, resultHash, errorCode)
                }
                CoordinationRequest.STATUS_REJECTED -> {
                    logger.info(
                        "Escalation rejected by user${current.note?.let { ": $it" } ?: ""}."
                    )
                    return AgentResult.EscalationRejected(requestId, errorCode, current.note)
                }
                else -> {
                    // Still pending — keep polling.
                }
            }
        }

        logger.info("Escalation $requestId still pending after $maxAttempts polls; stopping.")
        return AgentResult.EscalationPending(requestId, errorCode, maxAttempts)
    }

    /**
     * Builds the `transfer(from, to, amount)` argument vector.
     *
     * The encoded form of this exact list is sent to the coordination server so
     * the mobile inbox can rebuild the call verbatim.
     */
    internal fun buildTransferArgs(smartAccount: String): List<SCValXdr> {
        val destination = config.destinationAddress
        if (destination.isNullOrEmpty()) {
            throw AgentConfigException("destinationAddress is required to build the transfer call.")
        }
        val baseUnits = OZTransactionOperations.amountToBaseUnits(config.amount, config.tokenDecimals)
        return listOf(
            Scv.toAddress(Address(smartAccount).toSCAddress()),
            Scv.toAddress(Address(destination).toSCAddress()),
            Scv.toInt128(baseUnits),
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeArg(arg: SCValXdr): String {
        val bytes = XdrWriter().also { arg.encode(it) }.toByteArray()
        return Base64.encode(bytes)
    }

    companion object {
        /** The function the agent calls on the target token. */
        const val TARGET_FN = "transfer"
    }
}
