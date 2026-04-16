//
//  ReownConnectorBridge.kt
//  Smart Account Demo
//
//  Copyright (c) 2026 Soneso. All rights reserved.
//

package com.soneso.smartdemo.wallet

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Callback interface that Swift implements to provide Reown WalletConnect operations.
 *
 * All async methods use completion-handler callbacks so they can be called from Kotlin/Native
 * without requiring Swift async/await bridging. Kotlin wraps each callback via
 * [suspendCancellableCoroutine].
 *
 * The [result] parameter carries the successful value; [error] carries a human-readable
 * error message. Exactly one of the two will be non-null on every callback invocation.
 *
 * Swift conforms to this interface by subclassing or implementing via an ObjC class,
 * then passes the instance to [ReownConnectorBridge].
 */
interface ReownHandler {

    /**
     * Initiates a WalletConnect session and waits for the wallet to approve it.
     *
     * The implementation should:
     * 1. Create a WalletConnect URI via `Sign.instance.connect`.
     * 2. Open the wallet app via the URI deep link.
     * 3. Wait for `sessionSettlePublisher` to confirm the session.
     * 4. Extract the Stellar account address from the settled session namespace.
     *
     * @param completion Called with ([WalletConnectionBridge], null) on success, or
     *        (null, errorMessage) on failure or cancellation by the user.
     */
    fun connect(completion: (WalletConnectionBridge?, String?) -> Unit)

    /**
     * Terminates the active WalletConnect session for the given address.
     *
     * @param address The Stellar G-address whose session should be disconnected.
     * @param completion Called with (null) on success, or (errorMessage) on failure.
     */
    fun disconnect(address: String, completion: (String?) -> Unit)

    /**
     * Requests the wallet to sign a Soroban auth entry preimage.
     *
     * The wallet receives a `stellar_signAuthEntry` JSON-RPC request, hashes the
     * preimage with SHA-256, signs with Ed25519, and returns the base64-encoded
     * raw 64-byte signature.
     *
     * @param preimageXdr Base64-encoded HashIdPreimage XDR.
     * @param address The Stellar G-address of the signer.
     * @param completion Called with (base64Signature, null) on success, or
     *        (null, errorMessage) on rejection or timeout.
     */
    fun signAuthEntry(preimageXdr: String, address: String, completion: (String?, String?) -> Unit)

    /**
     * Returns true if an active WalletConnect session exists for the given address.
     *
     * Synchronous — checks in-memory session state only.
     */
    fun isConnected(address: String): Boolean

    /**
     * Returns the Stellar G-address of the currently connected wallet account, or null.
     *
     * Synchronous — returns cached state.
     */
    fun getConnectedAddress(): String?

    /**
     * Retrieves the network passphrase reported by the connected wallet's namespace.
     *
     * Freighter Mobile encodes the network in the Stellar namespace chain ID
     * (e.g., `stellar:testnet` or `stellar:pubnet`). The bridge maps these to the
     * corresponding Stellar network passphrases.
     *
     * @param completion Called with (passphrase) on success, or (null) if the wallet
     *        is not connected or the network is not available.
     */
    fun getNetworkPassphrase(completion: (String?) -> Unit)
}

/**
 * Swift-friendly representation of a successfully connected wallet account.
 *
 * Uses primitive types to avoid Kotlin class wrapping issues across the
 * Kotlin/Native to Swift boundary.
 *
 * @property address The Stellar G-address from the wallet namespace.
 * @property walletName Human-readable wallet name from the session peer metadata.
 */
data class WalletConnectionBridge(
    val address: String,
    val walletName: String
)

/**
 * iOS implementation of [WalletConnector] that delegates to a Swift-provided [ReownHandler].
 *
 * The Swift side creates a [ReownHandler] implementation using the Reown Swift SDK
 * (WalletConnectSign) and passes it to this bridge during app initialization.
 * This class converts the callback-based [ReownHandler] interface into suspend functions.
 *
 * All suspend functions use [suspendCancellableCoroutine] to wrap the Swift callbacks,
 * so coroutine cancellation propagates correctly.
 *
 * @param handler The Swift-provided Reown handler implementation.
 */
class ReownConnectorBridge(
    private val handler: ReownHandler
) : WalletConnector {

    /**
     * Connects to the external wallet via WalletConnect v2.
     *
     * Opens the wallet app via deep link and waits for the session to settle.
     * Returns null if the user cancels the connection.
     *
     * @return [WalletConnection] on success, or null if the user cancelled.
     * @throws [WalletConnectionException] on WalletConnect protocol errors.
     */
    override suspend fun connect(): WalletConnection? {
        val bridge: WalletConnectionBridge? = suspendCancellableCoroutine { cont ->
            handler.connect { result, error ->
                resumeWith(cont, result, error, "WalletConnect connection failed")
            }
        }
        return bridge?.let { WalletConnection(address = it.address, walletName = it.walletName) }
    }

    /**
     * Disconnects the wallet session for the given Stellar address.
     *
     * @param address The G-address whose WalletConnect session to terminate.
     * @throws [WalletConnectionException] if the disconnect call fails.
     */
    override suspend fun disconnect(address: String) {
        suspendCancellableCoroutine { cont ->
            handler.disconnect(address) { error ->
                if (error != null) {
                    cont.resumeWithException(WalletConnectionException(error))
                } else {
                    cont.resume(Unit)
                }
            }
        }
    }

    /**
     * Signs a Soroban auth entry using the connected wallet.
     *
     * Sends a `stellar_signAuthEntry` request to the wallet and waits for the response.
     *
     * @param authEntryPreimageXdr Base64-encoded HashIdPreimage XDR.
     * @param address The G-address of the signer.
     * @return Base64-encoded raw Ed25519 signature (64 bytes).
     * @throws [WalletSigningException] if the wallet rejects or times out.
     */
    override suspend fun signAuthEntry(authEntryPreimageXdr: String, address: String): String {
        return suspendCancellableCoroutine { cont ->
            handler.signAuthEntry(authEntryPreimageXdr, address) { result, error ->
                resumeWithString(cont, result, error, "Wallet signing failed")
            }
        }
    }

    /**
     * Returns true if a WalletConnect session is active for the given address.
     */
    override fun isConnected(address: String): Boolean = handler.isConnected(address)

    /**
     * Returns the G-address of the currently connected wallet, or null.
     */
    override fun getConnectedAddress(): String? = handler.getConnectedAddress()

    /**
     * Returns the network passphrase from the wallet's namespace, or null.
     */
    override suspend fun getNetworkPassphrase(): String? {
        return suspendCancellableCoroutine { cont ->
            handler.getNetworkPassphrase { passphrase ->
                cont.resume(passphrase)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Resumes a [CancellableContinuation] with either the successful [result] or an exception
 * derived from [error]. When both [result] and [error] are null, resumes with a null result
 * (signals user cancellation for nullable continuations).
 *
 * @param cont The continuation to resume.
 * @param result The successful value, or null if the operation failed or was cancelled.
 * @param error Human-readable error message, or null on success.
 * @param defaultErrorPrefix Prefix prepended to [error] when constructing the exception.
 */
private fun <T> resumeWith(
    cont: CancellableContinuation<T?>,
    result: T?,
    error: String?,
    defaultErrorPrefix: String
) {
    if (error != null) {
        cont.resumeWithException(WalletConnectionException("$defaultErrorPrefix: $error"))
    } else {
        cont.resume(result)
    }
}

/**
 * Variant for non-nullable [String] results used by [signAuthEntry].
 */
private fun resumeWithString(
    cont: CancellableContinuation<String>,
    result: String?,
    error: String?,
    defaultErrorPrefix: String
) {
    when {
        error != null -> cont.resumeWithException(WalletSigningException("$defaultErrorPrefix: $error"))
        result != null -> cont.resume(result)
        else -> cont.resumeWithException(WalletSigningException("$defaultErrorPrefix: no result returned"))
    }
}
