package com.soneso.stellar.sdk.integrationTests

import com.soneso.stellar.sdk.FriendBot
import com.soneso.stellar.sdk.horizon.HorizonServer
import com.soneso.stellar.sdk.platformDelay
import com.soneso.stellar.sdk.rpc.SorobanServer
import kotlin.time.TimeSource

/**
 * Delays for the specified time using real wall-clock time.
 *
 * Delegates to [platformDelay] which uses platform-native timing mechanisms
 * (Thread.sleep on JVM, setTimeout on JS, delay on Native) to bypass
 * runTest's virtual time scheduler.
 */
suspend fun realDelay(timeMillis: Long) {
    platformDelay(timeMillis)
}

/**
 * Funds [accountId] via Friendbot and waits until the account is visible to the
 * given endpoints.
 *
 * A Friendbot success does not imply the account can be served yet: Soroban RPC
 * and Horizon ingest ledgers independently, and on a busy network either can
 * trail by tens of seconds. A deploy or submission issued immediately after
 * funding then fails with "account not found". Both endpoints also answer from
 * load-balanced replicas that can disagree while ingestion converges, so a
 * single successful lookup is weak evidence; this helper requires
 * [requiredConsecutiveSuccesses] rounds in which every given endpoint serves
 * the account before it returns.
 *
 * Pass the server instance(s) the test uses; at least one is required. The
 * caller keeps ownership of the servers.
 *
 * @param accountId Account to fund and await
 * @param rpc Soroban RPC server that must serve the account, or null
 * @param horizon Horizon server that must serve the account, or null
 * @param useFuturenet Fund via the Futurenet Friendbot instead of testnet
 * @param pollIntervalMillis Delay before retrying after a failed round
 * @param confirmationIntervalMillis Delay between successful confirmation rounds
 * @param requiredConsecutiveSuccesses Rounds in which every endpoint must serve
 *        the account, consecutively, before the helper returns
 * @param timeoutMillis Maximum elapsed wall-clock time to wait for visibility,
 *        including the time the endpoint lookups themselves take
 * @throws IllegalStateException If visibility is not confirmed within
 *         [timeoutMillis]; the message names each endpoint's last failure and
 *         one of them is chained as the cause
 */
suspend fun fundTestAccountAndAwaitVisibility(
    accountId: String,
    rpc: SorobanServer? = null,
    horizon: HorizonServer? = null,
    useFuturenet: Boolean = false,
    pollIntervalMillis: Long = 3_000,
    confirmationIntervalMillis: Long = 1_000,
    requiredConsecutiveSuccesses: Int = 3,
    timeoutMillis: Long = 90_000
) {
    require(rpc != null || horizon != null) {
        "At least one of rpc or horizon must be provided to await visibility"
    }
    require(requiredConsecutiveSuccesses > 0) {
        "requiredConsecutiveSuccesses must be positive"
    }

    val funded = if (useFuturenet) {
        FriendBot.fundFuturenetAccount(accountId)
    } else {
        FriendBot.fundTestnetAccount(accountId)
    }

    var streak = 0
    var lastRpcFailure: Exception? = null
    var lastHorizonFailure: Exception? = null
    val start = TimeSource.Monotonic.markNow()

    while (true) {
        var roundOk = true
        if (rpc != null) {
            try {
                rpc.getAccount(accountId)
            } catch (e: Exception) {
                roundOk = false
                lastRpcFailure = e
            }
        }
        if (horizon != null) {
            try {
                horizon.accounts().account(accountId)
            } catch (e: Exception) {
                roundOk = false
                lastHorizonFailure = e
            }
        }

        streak = if (roundOk) streak + 1 else 0
        if (streak >= requiredConsecutiveSuccesses) {
            return
        }
        if (start.elapsedNow().inWholeMilliseconds >= timeoutMillis) {
            val message = buildString {
                append("Account $accountId was not served consistently within ")
                append("$timeoutMillis ms after Friendbot ")
                append(if (funded) "funded it" else "reported a funding failure")
                lastRpcFailure?.let { append("; last RPC failure: ${it.message}") }
                lastHorizonFailure?.let { append("; last Horizon failure: ${it.message}") }
            }
            throw IllegalStateException(message, lastRpcFailure ?: lastHorizonFailure)
        }
        realDelay(if (roundOk) confirmationIntervalMillis else pollIntervalMillis)
    }
}

/**
 * [fundTestAccountAndAwaitVisibility] variant for tests that hold no
 * [SorobanServer] instance: builds an RPC client for [rpcUrl], owns it for the
 * duration of the call, and closes it before returning.
 */
suspend fun fundTestAccountAndAwaitVisibility(
    accountId: String,
    rpcUrl: String,
    useFuturenet: Boolean = false,
    pollIntervalMillis: Long = 3_000,
    confirmationIntervalMillis: Long = 1_000,
    requiredConsecutiveSuccesses: Int = 3,
    timeoutMillis: Long = 90_000
) {
    SorobanServer(rpcUrl).use { rpc ->
        fundTestAccountAndAwaitVisibility(
            accountId = accountId,
            rpc = rpc,
            useFuturenet = useFuturenet,
            pollIntervalMillis = pollIntervalMillis,
            confirmationIntervalMillis = confirmationIntervalMillis,
            requiredConsecutiveSuccesses = requiredConsecutiveSuccesses,
            timeoutMillis = timeoutMillis
        )
    }
}
